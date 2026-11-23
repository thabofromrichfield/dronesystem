package drone.test.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import drone.test.R
import drone.test.core.DemoTelemetry
import drone.test.core.FlightOps
import drone.test.core.LogBus
import drone.test.core.ProductBus
import drone.test.core.Telemetry
import drone.test.core.TelemetrySource
import drone.test.core.TelemetryStore
import drone.test.core.VirtualStickController
import drone.test.ui.widget.ConsoleView
import drone.test.ui.widget.CyberJoystick
import drone.test.ui.widget.FpvSurface
import drone.test.ui.widget.HudOverlay
import drone.test.util.Fmt

/**
 * The control surface. Full-screen FPV with head-up glass, two virtual sticks and
 * a command rail. Landscape only, so the layout matches how a controller phone is
 * actually held.
 */
class ControlActivity : AppCompatActivity() {

    private lateinit var fpv: FpvSurface
    private lateinit var hud: HudOverlay
    private lateinit var chip: TextView
    private lateinit var stickButton: Button
    private lateinit var recordButton: Button
    private lateinit var simButton: Button
    private lateinit var consoleView: ConsoleView

    private var recording = false

    private val telemetryListener: (Telemetry) -> Unit = { t ->
        hud.data = t
        renderChip(t)
        if (t.recording != recording) {
            recording = t.recording
            recordButton.text =
                getString(if (recording) R.string.btn_record_on else R.string.btn_record)
        }
    }

    private val linkListener: (ProductBus.Link) -> Unit = { link ->
        chip.text = "LINK ${link.name}"
        chip.setTextColor(if (link == ProductBus.Link.LINKED) 0xFF00FF9C.toInt() else 0xFFFFB300.toInt())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        fpv = findViewById(R.id.fpv)
        hud = findViewById(R.id.hud)
        chip = findViewById(R.id.chip_link)
        stickButton = findViewById(R.id.btn_stick)
        recordButton = findViewById(R.id.btn_record)
        simButton = findViewById(R.id.btn_sim)
        consoleView = findViewById(R.id.console)

        wireSticks()
        wireCommands()
        enterImmersive()

        findViewById<CyberJoystick>(R.id.stick_left).label = getString(R.string.stick_left_label)
        findViewById<CyberJoystick>(R.id.stick_right).label = getString(R.string.stick_right_label)
    }

    // ------------------------------------------------------------- lifecycle

    override fun onStop() {
        // Order matters. A paused UI must never keep driving the aircraft, and
        // DJI must not be left in virtual-stick mode with no input stream behind
        // it — that state triggers the aircraft's own failsafe. So hand authority
        // back to the physical controller first, then tear the stream down.
        if (VirtualStickController.active) {
            FlightOps.setVirtualStick(false)
        }
        VirtualStickController.stop()
        TelemetryStore.mutate { copy(virtualStickOn = false) }
        fpv.stop()
        TelemetrySource.stop()
        TelemetryStore.detach(telemetryListener)
        ProductBus.removeLinkListener(linkListener)
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        TelemetryStore.attach(telemetryListener)
        ProductBus.addLinkListener(linkListener)
        stickButton.text = getString(R.string.btn_stick)
        if (!DemoTelemetry.running) {
            TelemetrySource.start()
            fpv.start()
        } else {
            LogBus.warn("SIM feed active — SDK telemetry and video are bypassed")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onBackPressed() {
        val flying = TelemetryStore.current.isFlying
        if (flying && !TelemetryStore.current.demo) {
            // Full sentences on purpose: this is a safety confirmation.
            AlertDialog.Builder(this)
                .setTitle("AIRCRAFT IS AIRBORNE")
                .setMessage(R.string.exit_confirm)
                .setNegativeButton(R.string.exit_no) { d, _ -> d.dismiss() }
                .setPositiveButton(R.string.exit_yes) { _, _ -> leaveGrid() }
                .show()
        } else {
            leaveGrid()
        }
    }

    /** Kotlin forbids `super` inside a lambda, so the exit path lives here. */
    private fun leaveGrid() {
        super.onBackPressed()
    }

    // ---------------------------------------------------------------- sticks

    private fun wireSticks() {
        val left = findViewById<CyberJoystick>(R.id.stick_left)
        val right = findViewById<CyberJoystick>(R.id.stick_right)

        // Mode 2 (American hand 2): left stick = throttle + yaw, right = pitch + roll.
        left.listener = object : CyberJoystick.StickListener {
            override fun onStick(joystick: CyberJoystick, x: Float, y: Float) {
                if (!VirtualStickController.active) return
                VirtualStickController.yaw = x
                VirtualStickController.throttle = y
            }
        }
        right.listener = object : CyberJoystick.StickListener {
            override fun onStick(joystick: CyberJoystick, x: Float, y: Float) {
                if (!VirtualStickController.active) return
                VirtualStickController.roll = x
                VirtualStickController.pitch = y
            }
        }
    }

    private fun toggleStick() {
        if (TelemetryStore.current.demo) {
            LogBus.err("SIM feed: virtual stick needs a real aircraft link")
            return
        }
        val turningOn = !VirtualStickController.active
        if (turningOn) {
            // Confirm in full sentences: engaging virtual stick hands flight
            // authority to this phone and disengages the physical controller sticks.
            AlertDialog.Builder(this)
                .setTitle("ENGAGE VIRTUAL STICK?")
                .setMessage(
                    "This phone takes flight authority. The controller sticks stop responding. " +
                        "Release both sticks to hover. Max horizontal speed is " +
                        "${Fmt.num(VirtualStickController.maxHorizontalMs, 1)} m/s."
                )
                .setNegativeButton("CANCEL") { d, _ -> d.dismiss() }
                .setPositiveButton("ENGAGE") { _, _ ->
                    FlightOps.setVirtualStick(true)
                    stickButton.text = "STICK ON"
                }
                .show()
        } else {
            FlightOps.setVirtualStick(false)
            stickButton.text = getString(R.string.btn_stick)
        }
    }

    // -------------------------------------------------------------- commands

    private fun wireCommands() {
        stickButton.setOnClickListener { toggleStick() }
        findViewById<Button>(R.id.btn_takeoff).setOnClickListener {
            confirm("AUTO TAKEOFF", "The aircraft will climb to about 1.2 m and hover. Area clear?") {
                FlightOps.takeoff()
            }
        }
        findViewById<Button>(R.id.btn_hover).setOnClickListener { FlightOps.hover() }
        findViewById<Button>(R.id.btn_land).setOnClickListener {
            confirm("AUTO LAND", "The aircraft descends and shuts down its motors. Ground clear?") {
                FlightOps.land()
            }
        }
        findViewById<Button>(R.id.btn_confirm_land).setOnClickListener {
            confirm("CONFIRM LANDING", "Motors will stop. Confirm only when the landing surface is verified clear.") {
                FlightOps.confirmLanding()
            }
        }
        findViewById<Button>(R.id.btn_cancel_land).setOnClickListener { FlightOps.cancelLanding() }
        findViewById<Button>(R.id.btn_rth).setOnClickListener {
            confirm("RETURN TO HOME", "The aircraft flies to the recorded home point and lands.") {
                FlightOps.goHome()
            }
        }

        findViewById<Button>(R.id.btn_photo).setOnClickListener { FlightOps.shootPhoto() }
        recordButton.setOnClickListener {
            val start = !TelemetryStore.current.recording
            FlightOps.toggleRecording(start)
        }

        findViewById<Button>(R.id.btn_gimbal_up).setOnClickListener { FlightOps.gimbalSpeed(15f) }
        findViewById<Button>(R.id.btn_gimbal_down).setOnClickListener { FlightOps.gimbalSpeed(-15f) }
        findViewById<Button>(R.id.btn_gimbal_stop).setOnClickListener { FlightOps.gimbalSpeed(0f) }
        findViewById<Button>(R.id.btn_gimbal_horizon).setOnClickListener { FlightOps.gimbalTo(0f) }
        findViewById<Button>(R.id.btn_gimbal_down45).setOnClickListener { FlightOps.gimbalTo(-45f) }

        simButton.setOnClickListener {
            if (TelemetryStore.current.simulatorOn) FlightOps.stopSimulator() else FlightOps.startSimulator()
        }

        findViewById<Button>(R.id.btn_console).setOnClickListener {
            consoleView.visibility = if (consoleView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<Button>(R.id.btn_limits).setOnClickListener { showLimits() }
    }

    private fun showLimits() {
        val labels = arrayOf(
            "RTH ALTITUDE 40 m",
            "RTH ALTITUDE 60 m",
            "MAX HEIGHT 120 m",
            "MAX HEIGHT 60 m",
            "MAX RADIUS 100 m",
            "MAX RADIUS 300 m"
        )
        AlertDialog.Builder(this)
            .setTitle("FLIGHT LIMITS")
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> FlightOps.setGoHomeHeight(40)
                    1 -> FlightOps.setGoHomeHeight(60)
                    2 -> FlightOps.setMaxHeight(120)
                    3 -> FlightOps.setMaxHeight(60)
                    4 -> FlightOps.setMaxRadius(100)
                    5 -> FlightOps.setMaxRadius(300)
                }
            }
            .show()
    }

    /**
     * Safety gate for anything that moves the aircraft. Uses full sentences by
     * design — a fragment here is exactly the wrong shape.
     */
    private fun confirm(title: String, message: String, action: () -> Unit) {
        if (TelemetryStore.current.demo) {
            LogBus.err("SIM feed: flight commands are disabled")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("CANCEL") { d, _ -> d.dismiss() }
            .setPositiveButton("CONFIRM") { _, _ -> action() }
            .show()
    }

    private fun renderChip(t: Telemetry) {
        val state = when {
            t.demo -> "SIM"
            !t.linked -> "NO LINK"
            t.virtualStickOn -> "STICK"
            t.isGoingHome -> "RTH"
            t.isFlying -> "AIR"
            else -> "GROUND"
        }
        chip.text = "$state  ${t.aircraftModel}  BAT ${Fmt.battery(t.batteryPercent)}  SAT ${t.satellites}"
        chip.setTextColor(
            when {
                t.demo -> 0xFFFFB300.toInt()
                !t.linked -> 0xFFFF1744.toInt()
                else -> 0xFF00FF9C.toInt()
            }
        )
    }
}
