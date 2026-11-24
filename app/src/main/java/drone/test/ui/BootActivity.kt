package drone.test.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import drone.test.R
import drone.test.core.Backend
import drone.test.core.DemoTelemetry
import drone.test.core.LogBus
import drone.test.core.SdkPermissions
import drone.test.core.TelemetryStore
import drone.test.core.UavLinkState
import drone.test.util.Fmt

/**
 * Link screen: grants runtime permissions, registers the App Key, and reports
 * handshake state. Nothing here commands the aircraft.
 *
 * In the `sim` flavour the backend reports LINKED immediately and needs neither
 * an App Key nor permissions, so this screen is a two-tap pass-through.
 */
class BootActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var enterButton: Button
    private var logListener: ((LogBus.Line) -> Unit)? = null
    private val logLines = StringBuilder()

    private val linkListener: (UavLinkState) -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_boot)

        statusView = findViewById(R.id.boot_status)
        enterButton = findViewById(R.id.boot_enter)

        findViewById<Button>(R.id.boot_init).setOnClickListener {
            if (SdkPermissions.allGranted(this) || !Backend.uav.canFly) {
                DemoTelemetry.stop()
                Backend.uav.register(this)
            } else {
                LogBus.warn("requesting: ${SdkPermissions.missingNames(this)}")
                SdkPermissions.request(this)
            }
        }

        enterButton.setOnClickListener {
            startActivity(Intent(this, ControlActivity::class.java))
        }

        findViewById<Button>(R.id.boot_sim).setOnClickListener {
            TelemetryStore.reset()
            DemoTelemetry.start()
            startActivity(Intent(this, ControlActivity::class.java))
        }

        logListener = { line ->
            logLines.append(line.time).append("  ").append(line.text).append('\n')
            render()
        }
    }

    override fun onStart() {
        super.onStart()
        Backend.uav.addLinkListener(linkListener)
        logListener?.let { LogBus.attach(it) }
        when {
            !Backend.uav.canFly -> Backend.uav.register(this)
            !SdkPermissions.allGranted(this) -> SdkPermissions.request(this)
            Backend.uav.link() == UavLinkState.IDLE -> Backend.uav.register(this)
        }
        render()
    }

    override fun onStop() {
        Backend.uav.removeLinkListener(linkListener)
        logListener?.let { LogBus.detach(it) }
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SdkPermissions.REQUEST_CODE && SdkPermissions.allGranted(this)) {
            LogBus.ok("permissions granted")
            Backend.uav.register(this)
        } else if (requestCode == SdkPermissions.REQUEST_CODE) {
            // Name what is still missing. "permissions denied" alone gives the
            // pilot nothing to act on.
            LogBus.err("still missing: ${SdkPermissions.missingNames(this)}")
            LogBus.err("grant them in Settings > Apps > GHOSTGRID > Permissions")
        }
        render()
    }

    private fun render() {
        val t = TelemetryStore.current
        val keySet = getString(R.string.dji_api_key) != "PASTE_YOUR_DJI_APP_KEY_HERE"
        val link = Backend.uav.link()

        val head = buildString {
            append("BACKEND   : ").append(Backend.uav.label).append('\n')
            append("CAN FLY   : ").append(if (Backend.uav.canFly) "YES" else "NO — interface only").append('\n')
            append("LINK      : ").append(link.name).append('\n')
            append("APP KEY   : ").append(if (keySet) "PRESENT" else "MISSING — see README").append('\n')
            append("AIRCRAFT  : ").append(t.aircraftModel).append('\n')
            append("BATTERY   : ").append(Fmt.battery(t.batteryPercent)).append('\n')
            append("SIM FEED  : ").append(if (DemoTelemetry.running) "ON" else "OFF").append('\n')
            Backend.uav.lastError()?.let { append("LAST ERR  : ").append(it).append('\n') }
            append("\n")
        }

        statusView.text = head + logLines.toString()
        enterButton.isEnabled = link == UavLinkState.LINKED
        enterButton.visibility = View.VISIBLE
    }
}
