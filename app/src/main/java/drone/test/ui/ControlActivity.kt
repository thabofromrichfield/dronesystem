package drone.test.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import drone.test.R
import drone.test.core.Backend
import drone.test.core.DemoTelemetry
import drone.test.core.LogBus
import drone.test.core.Telemetry
import drone.test.core.TelemetryStore
import drone.test.core.UavLinkState
import drone.test.ui.widget.ConsoleView
import drone.test.ui.widget.CyberJoystick
import drone.test.ui.widget.FpvSurface
import drone.test.ui.widget.HudOverlay
import drone.test.util.Fmt

/**
 * The control surface. Full-screen FPV with head-up glass, two virtual sticks
 * and a command rail. Landscape only, so the layout matches how a controller
 * phone is actually held.
 *
 * Every command goes through [Backend.uav], so this class is identical in the
 * `full` build, where it flies an aircraft, and the `sim` build, where the same
 * buttons are refused and logged.
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

    // Mode 2 (American hand 2): left stick = throttle + yaw, right = pitch + roll.
    // Held here so one stick's move can be forwarded with the other's last value.
    private var stickPitch = 0f
    private var stickRoll = 0f
    private var stickYaw = 0f
    private var stickThrottle = 0f

    private val telemetryListener: (Telemetry) -> Unit = { t ->
        hud.data = t
        renderChip(t)
        if (t.recording != recording) {
            recording = t.recording
            recordButton.text =
                getString(if (recording) R.string.btn_record_on else R.string.btn_record)
        }
    }

    private val linkListener: (UavLinkState) -> Unit = { link ->
        chip.text = "LINK ${link.name}"
        chip.setTextColor(if (link == UavLinkState.LINKED) 0xFF00FF9C.toInt() else 0xFFFFB300.toInt())
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

        LogBus.info("control surface up — backend: ${Backend.uav.label}")
    }

    // ------------------------------------------------------------- lifecycle

    override fun onStart() {
        super.onStart()
        TelemetryStore.attach(telemetryListener)
        Backend.uav.addLinkListener(linkListener)
        stickButton.text = getString(R.string.btn_stick)
        if (DemoTelemetry.running && Backend.uav.canFly) {
            LogBus.warn("SIM feed active — SDK telemetry and video are bypassed")
        } else {
            Backend.uav.startTelemetry()
            fpv.start()
        }
    }

    override fun onStop() {
        // Order matters. A paused UI must never keep driving the aircraft, and
        // DJI must not be left in virtual-stick mode with no input stream behind
        // it — that state triggers the aircraft's own failsafe. So hand authority
        // back to the physical controller first, then tear the stream down.
        if (Backend.uav.stickActive()) {
            Backend.uav.setVirtualStick(false)
        }
        zeroSticks()
        TelemetryStore.mutate { copy(virtualStickOn = false) }
        fpv.stop()
        Backend.uav.stopTelemetry()
        TelemetryStore.detach(telemetryListener)
        Backend.uav.removeLinkListener(linkListener)
        super.onStop()
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
        val t = TelemetryStore.current
        if (t.isFlying && !t.demo && Backend.uav.canFly) {
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

        left.listener = object : CyberJoystick.StickListener {
            override fun onStick(joystick: CyberJoystick, x: Float, y: Float) {
                if (!Backend.uav.stickActive()) return
                stickYaw = x
                stickThrottle = y
                pushSticks()
            }
        }
        right.listener = object : CyberJoystick.StickListener {
            override fun onStick(joystick: CyberJoystick, x: Float, y: Float) {
                if (!Backend.uav.stickActive()) return
                stickRoll = x
                stickPitch = y
                pushSticks()
            }
        }
    }

    private fun pushSticks() {
        Backend.uav.setStick(stickPitch, stickRoll, stickYaw, stickThrottle)
    }

    private fun zeroSticks() {
        stickPitch = 0f
        stickRoll = 0f
        stickYaw = 0f
        stickThrottle = 0f
    }

    private fun toggleStick() {
        if (!Backend.uav.canFly || TelemetryStore.current.demo) {
            LogBus.err("virtual stick needs a real aircraft link — this build cannot provide one")
            return
        }
        if (!Backend.uav.stickActive()) {
            // Confirm in full sentences: engaging virtual stick hands flight
            // authority to this phone and disengages the physical controller sticks.
            AlertDialog.Builder(this)
                .setTitle("ENGAGE VIRTUAL STICK?")
                .setMessage(
                    "This phone takes flight authority. The controller sticks stop responding. " +
                        "Release both sticks to hover. Max horizontal speed is " +
                        "${Fmt.num(Backend.uav.maxHorizontalMs(), 1)} m/s."
                )
                .setNegativeButton("CANCEL") { d, _ -> d.dismiss() }
                .setPositiveButton("ENGAGE") { _, _ ->
                    Backend.uav.setVirtualStick(true)
                    stickButton.text = "STICK ON"
                }
                .show()
        } else {
            Backend.uav.setVirtualStick(false)
            zeroSticks()
            stickButton.text = getString(R.string.btn_stick)
        }
    }

    // -------------------------------------------------------------- commands

    private fun wireCommands() {
        stickButton.setOnClickListener { toggleStick() }
        findViewById<Button>(R.id.btn_takeoff).setOnClickListener {
            confirm("AUTO TAKEOFF", "The aircraft will climb to about 1.2 m and hover. Area clear?") {
                Backend.uav.takeoff()
            }
        }
        findViewById<Button>(R.id.btn_hover).setOnClickListener { Backend.uav.hover() }
        findViewById<Button>(R.id.btn_land).setOnClickListener {
            confirm("AUTO LAND", "The aircraft descends and shuts down its motors. Ground clear?") {
                Backend.uav.land()
            }
        }
        findViewById<Button>(R.id.btn_confirm_land).setOnClickListener {
            confirm("CONFIRM LANDING", "Motors will stop. Confirm only when the landing surface is verified clear.") {
                Backend.uav.confirmLanding()
            }
        }
        findViewById<Button>(R.id.btn_cancel_land).setOnClickListener { Backend.uav.cancelLanding() }
        findViewById<Button>(R.id.btn_rth).setOnClickListener {
            confirm("RETURN TO HOME", "The aircraft flies to the recorded home point and lands.") {
                Backend.uav.goHome()
            }
        }

        findViewById<Button>(R.id.btn_photo).setOnClickListener { Backend.uav.shootPhoto() }
        recordButton.setOnClickListener {
            Backend.uav.toggleRecording(!TelemetryStore.current.recording)
        }

        findViewById<Button>(R.id.btn_gimbal_up).setOnClickListener { Backend.uav.gimbalSpeed(15f) }
        findViewById<Button>(R.id.btn_gimbal_down).setOnClickListener { Backend.uav.gimbalSpeed(-15f) }
        findViewById<Button>(R.id.btn_gimbal_stop).setOnClickListener { Backend.uav.gimbalSpeed(0f) }
        findViewById<Button>(R.id.btn_gimbal_horizon).setOnClickListener { Backend.uav.gimbalTo(0f) }
        findViewById<Button>(R.id.btn_gimbal_down45).setOnClickListener { Backend.uav.gimbalTo(-45f) }

        simButton.setOnClickListener {
            if (Backend.uav.simulatorOn()) Backend.uav.stopSimulator() else Backend.uav.startSimulator()
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
                    0 -> Backend.uav.setGoHomeHeight(40)
                    1 -> Backend.uav.setGoHomeHeight(60)
                    2 -> Backend.uav.setMaxHeight(120)
                    3 -> Backend.uav.setMaxHeight(60)
                    4 -> Backend.uav.setMaxRadius(100)
                    5 -> Backend.uav.setMaxRadius(300)
                }
            }
            .show()
    }

    /**
     * Safety gate for anything that moves the aircraft. Uses full sentences by
     * design — a fragment here is exactly the wrong shape.
     */
    private fun confirm(title: String, message: String, action: () -> Unit) {
        if (!Backend.uav.canFly || TelemetryStore.current.demo) {
            LogBus.err("flight commands are disabled in this build")
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
