package drone.test.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import drone.test.R
import drone.test.core.DemoTelemetry
import drone.test.core.LogBus
import drone.test.core.ProductBus
import drone.test.core.SdkPermissions
import drone.test.core.TelemetryStore
import drone.test.util.Fmt

/**
 * Link screen: grants runtime permissions, registers the App Key with DJI, and
 * reports handshake state. Nothing here commands the aircraft.
 */
class BootActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var enterButton: Button
    private var logListener: ((LogBus.Line) -> Unit)? = null
    private val logLines = StringBuilder()

    private val linkListener: (ProductBus.Link) -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_boot)

        statusView = findViewById(R.id.boot_status)
        enterButton = findViewById(R.id.boot_enter)

        findViewById<Button>(R.id.boot_init).setOnClickListener {
            if (SdkPermissions.allGranted(this)) {
                ProductBus.register(this)
            } else {
                LogBus.warn("permissions missing — requesting")
                SdkPermissions.request(this)
            }
        }

        enterButton.setOnClickListener {
            DemoTelemetry.stop()
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
        ProductBus.addLinkListener(linkListener)
        logListener?.let { LogBus.attach(it) }
        if (!SdkPermissions.allGranted(this)) {
            SdkPermissions.request(this)
        } else if (ProductBus.link == ProductBus.Link.IDLE) {
            ProductBus.register(this)
        }
        render()
    }

    override fun onStop() {
        ProductBus.removeLinkListener(linkListener)
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
            ProductBus.register(this)
        } else {
            LogBus.err("permissions denied — SDK cannot see the aircraft")
        }
        render()
    }

    private fun render() {
        val t = TelemetryStore.current
        val keySet = getString(R.string.dji_api_key) != "PASTE_YOUR_DJI_APP_KEY_HERE"

        val head = buildString {
            append("LINK      : ").append(ProductBus.link.name).append('\n')
            append("APP KEY   : ").append(if (keySet) "PRESENT" else "MISSING — see README").append('\n')
            append("AIRCRAFT  : ").append(t.aircraftModel).append('\n')
            append("BATTERY   : ").append(Fmt.battery(t.batteryPercent)).append('\n')
            append("SIM FEED  : ").append(if (DemoTelemetry.running) "ON" else "OFF").append('\n')
            ProductBus.lastError?.let { append("LAST ERR  : ").append(it).append('\n') }
            append("\n")
        }

        statusView.text = head + logLines.toString()
        enterButton.isEnabled = ProductBus.link == ProductBus.Link.LINKED
        enterButton.visibility = View.VISIBLE
    }
}
