package drone.test.core

import android.os.Handler
import android.os.HandlerThread
import dji.common.flightcontroller.virtualstick.FlightControlData

/**
 * Pushes virtual-stick frames to the aircraft at [HZ]. The aircraft expects a
 * continuous stream; a gap makes it brake, so this runs on its own thread and
 * keeps sending the last commanded value until the sticks are released or the
 * mode is switched off.
 */
object VirtualStickController {

    private const val HZ = 10L
    private const val PERIOD_MS = 1000L / HZ

    /**
     * DJI 4.18 `FlightControlData(pitch, roll, yaw, verticalThrottle)` has its first
     * two arguments swapped relative to their names — documented in DJI's own sample
     * (`VirtualStickView.java`: "接口写反了"). Flip this to false if your airframe
     * rolls when you push forward.
     */
    private const val SWAP_PITCH_ROLL = true

    /** Horizontal speed ceiling in m/s at full stick deflection. */
    var maxHorizontalMs = 6f
    /** Vertical speed ceiling in m/s at full stick deflection. */
    var maxVerticalMs = 3f
    /** Yaw rate ceiling in deg/s at full stick deflection. */
    var maxYawDegs = 60f

    // Commanded stick values, normalised -1..1. Written by the UI thread,
    // read by the sender thread.
    @Volatile var pitch = 0f
    @Volatile var roll = 0f
    @Volatile var yaw = 0f
    @Volatile var throttle = 0f

    @Volatile var active = false
        private set

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val sender = object : Runnable {
        override fun run() {
            if (!active) return
            val fc = ProductBus.aircraft()?.flightController
            if (fc != null) {
                val p = pitch * maxHorizontalMs
                val r = roll * maxHorizontalMs
                val y = yaw * maxYawDegs
                val t = throttle * maxVerticalMs
                val data = if (SWAP_PITCH_ROLL) {
                    FlightControlData(r, p, y, t)
                } else {
                    FlightControlData(p, r, y, t)
                }
                runCatching { fc.sendVirtualStickFlightControlData(data, null) }
                    .onFailure { LogBus.err("stick frame: ${it.message}"); stop() }
            }
            handler?.postDelayed(this, PERIOD_MS)
        }
    }

    fun start() {
        if (active) return
        active = true
        thread = HandlerThread("ghost-stick").also { it.start() }
        handler = Handler(thread!!.looper)
        handler?.postDelayed(sender, PERIOD_MS)
        LogBus.ok("stick stream up @ ${1000 / PERIOD_MS} Hz")
    }

    fun stop() {
        if (!active) return
        active = false
        neutralise()
        handler?.removeCallbacksAndMessages(null)
        handler = null
        thread?.quitSafely()
        thread = null
        LogBus.info("stick stream down")
    }

    fun neutralise() {
        pitch = 0f
        roll = 0f
        yaw = 0f
        throttle = 0f
    }
}
