package drone.test.core

import android.os.Handler
import android.os.Looper
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic telemetry so the interface can be driven and screenshotted with no
 * aircraft attached. Frames are flagged `demo = true` and the HUD stamps SIM
 * across the glass so this can never be mistaken for real flight data.
 *
 * It renders only. It never reaches the SDK.
 */
object DemoTelemetry {

    @Volatile var running = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var t = 0.0
    private val originLat = -26.2041
    private val originLon = 28.0473

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            t += 0.1

            val circling = (t * 0.25) % (Math.PI * 2)
            val radiusM = 120.0
            val dLat = (radiusM * cos(circling)) / 111320.0
            val dLon = (radiusM * sin(circling)) / (111320.0 * cos(Math.toRadians(originLat)))

            val alt = (60 + 25 * sin(t * 0.15)).toFloat()
            val headingRaw = (Math.toDegrees(circling) + 90).toFloat()
            val heading = ((headingRaw % 360f) + 360f) % 360f

            TelemetryStore.mutate {
                copy(
                    aircraftModel = "SIM-AIRFRAME",
                    linked = true,
                    demo = true,
                    isFlying = true,
                    isGoingHome = false,
                    flightMode = "GPS",
                    flightTimeSec = t.toInt(),
                    altitudeM = alt,
                    groundSpeedMs = 8.4f + (sin(t * 0.4) * 0.8).toFloat(),
                    verticalSpeedMs = (25 * 0.15 * cos(t * 0.15)).toFloat(),
                    distanceFromHomeM = radiusM.toFloat(),
                    latitude = originLat + dLat,
                    longitude = originLon + dLon,
                    homeLatitude = originLat,
                    homeLongitude = originLon,
                    pitchDeg = 4f * sin(t * 0.5).toFloat(),
                    rollDeg = 18f * sin(circling).toFloat(),
                    headingDeg = heading,
                    gimbalPitchDeg = -30f + 20f * sin(t * 0.2).toFloat(),
                    satellites = 16,
                    compassHeadingDeg = heading,
                    batteryPercent = (92 - (t / 6).toInt()).coerceAtLeast(3),
                    batteryVolts = 15.8f - (t / 600.0).toFloat(),
                    batteryTempC = 31f + 3f * sin(t * 0.1).toFloat(),
                    recording = (t.toInt() / 20) % 2 == 0,
                    cameraMode = if ((t.toInt() / 20) % 2 == 0) "RECORD_VIDEO" else "SHOOT_PHOTO",
                    virtualStickOn = Backend.uav.stickActive(),
                    simulatorOn = false,
                    rcModeSwitch = "P",
                    warning = if ((t.toInt() / 45) % 7 == 0) "PROXIMITY 3.2 M" else null
                )
            }
            handler.postDelayed(this, 100)
        }
    }

    fun start() {
        if (running) return
        running = true
        t = 0.0
        LogBus.warn("SIM FEED engaged — synthetic data, no aircraft commands possible")
        handler.post(tick)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        LogBus.info("SIM feed stopped")
    }
}
