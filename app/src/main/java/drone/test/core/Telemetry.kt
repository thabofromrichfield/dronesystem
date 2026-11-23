package drone.test.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One immutable frame of everything the HUD draws. All units are SI unless the
 * name says otherwise. NaN means "no fix / unknown" and must render as "--".
 */
data class Telemetry(
    // link
    val aircraftModel: String = "--",
    val linked: Boolean = false,
    val demo: Boolean = false,
    // flight
    val isFlying: Boolean = false,
    val isGoingHome: Boolean = false,
    val isFailsafe: Boolean = false,
    val flightMode: String = "--",
    val flightTimeSec: Int = 0,
    // position
    val altitudeM: Float = 0f,
    val groundSpeedMs: Float = 0f,
    val verticalSpeedMs: Float = 0f,
    val distanceFromHomeM: Float = 0f,
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN,
    val homeLatitude: Double = Double.NaN,
    val homeLongitude: Double = Double.NaN,
    // attitude
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val headingDeg: Float = 0f,
    val gimbalPitchDeg: Float = 0f,
    // nav quality
    val satellites: Int = 0,
    val compassHeadingDeg: Float = Float.NaN,
    // power
    val batteryPercent: Int = -1,
    val batteryVolts: Float = 0f,
    val batteryTempC: Float = Float.NaN,
    // camera
    val recording: Boolean = false,
    val cameraMode: String = "--",
    val cameraHot: Boolean = false,
    // controller
    val virtualStickOn: Boolean = false,
    val simulatorOn: Boolean = false,
    val rcModeSwitch: String = "--",
    // warnings surfaced by the SDK
    val warning: String? = null
) {
    val batteryOk: Boolean get() = batteryPercent < 0 || batteryPercent >= 20
    val navOk: Boolean get() = satellites >= 8
}

/** Great-circle distance in metres. Returns NaN when either fix is unknown. */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    if (lat1.isNaN() || lon1.isNaN() || lat2.isNaN() || lon2.isNaN()) return Float.NaN
    val r = 6378137.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (r * c).toFloat()
}
