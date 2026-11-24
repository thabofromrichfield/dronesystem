package drone.test.core

import dji.common.battery.BatteryState
import dji.common.camera.SystemState
import dji.common.flightcontroller.FlightControllerState
import dji.common.gimbal.GimbalState
import dji.common.remotecontroller.HardwareState
import dji.sdk.products.Aircraft
import kotlin.math.sqrt

/**
 * Subscribes to every DJI state push the HUD needs and folds them into
 * [TelemetryStore]. Started when the control screen opens, stopped when it closes,
 * so no DJI callback ever outlives the UI.
 *
 * Only methods confirmed present in DJI Mobile SDK 4.18 are used here.
 */
object TelemetrySource {

    @Volatile
    var running = false
        private set

    private val flightCallback = object : FlightControllerState.Callback {
        override fun onUpdate(state: FlightControllerState) {
            val loc = state.aircraftLocation
            val home = state.homeLocation
            val att = state.attitude

            val vx = state.velocityX.toDouble()
            val vy = state.velocityY.toDouble()
            val ground = sqrt(vx * vx + vy * vy).toFloat()

            val heading = runCatching {
                ProductBus.aircraft()?.flightController?.compass?.heading?.toFloat()
            }.getOrNull() ?: Float.NaN

            val lat = loc?.latitude ?: Double.NaN
            val lon = loc?.longitude ?: Double.NaN
            val hLat = home?.latitude ?: Double.NaN
            val hLon = home?.longitude ?: Double.NaN

            TelemetryStore.mutate {
                copy(
                    isFlying = state.isFlying,
                    isGoingHome = state.isGoingHome,
                    flightMode = state.flightMode?.name ?: "--",
                    flightTimeSec = state.flightTimeInSeconds,
                    altitudeM = loc?.altitude?.toFloat() ?: 0f,
                    groundSpeedMs = ground,
                    verticalSpeedMs = state.velocityZ,
                    distanceFromHomeM = distanceMeters(lat, lon, hLat, hLon),
                    latitude = lat,
                    longitude = lon,
                    homeLatitude = hLat,
                    homeLongitude = hLon,
                    pitchDeg = att?.pitch?.toFloat() ?: 0f,
                    rollDeg = att?.roll?.toFloat() ?: 0f,
                    headingDeg = (att?.yaw?.toFloat() ?: 0f).let { if (it < 0f) it + 360f else it },
                    satellites = state.satelliteCount,
                    compassHeadingDeg = heading,
                    warning = firstWarning(state)
                )
            }
        }
    }

    private val batteryCallback = object : BatteryState.Callback {
        override fun onUpdate(state: BatteryState) {
            TelemetryStore.mutate {
                copy(
                    batteryPercent = state.chargeRemainingInPercent,
                    batteryVolts = state.voltage / 1000f,
                    batteryTempC = state.temperature / 10f
                )
            }
        }
    }

    private val gimbalCallback = object : GimbalState.Callback {
        override fun onUpdate(state: GimbalState) {
            TelemetryStore.mutate {
                copy(gimbalPitchDeg = state.attitudeInDegrees.pitch.toFloat())
            }
        }
    }

    private val cameraCallback = object : SystemState.Callback {
        // Non-null: SystemState.Callback.onUpdate declares a non-null parameter,
        // so Kotlin will not accept a nullable override. DJI's Java sample
        // null-checks anyway, which Java permits and Kotlin does not need.
        override fun onUpdate(state: SystemState) {
            TelemetryStore.mutate {
                copy(
                    recording = state.isRecording,
                    cameraHot = state.isOverheating,
                    cameraMode = state.mode?.name ?: "--"
                )
            }
        }
    }

    private val rcCallback = object : HardwareState.HardwareStateCallback {
        override fun onUpdate(state: HardwareState) {
            TelemetryStore.mutate {
                copy(rcModeSwitch = state.flightModeSwitch?.name ?: "--")
            }
        }
    }

    fun start() {
        if (running) return
        val aircraft: Aircraft = ProductBus.aircraft() ?: run {
            LogBus.warn("telemetry: no aircraft linked, nothing to subscribe to")
            return
        }
        running = true
        LogBus.info("telemetry: subscribing")

        runCatching { aircraft.flightController?.setStateCallback(flightCallback) }
            .onFailure { LogBus.err("flight state subscribe: ${it.message}") }
        runCatching { aircraft.battery?.setStateCallback(batteryCallback) }
            .onFailure { LogBus.err("battery state subscribe: ${it.message}") }
        runCatching { aircraft.gimbal?.setStateCallback(gimbalCallback) }
            .onFailure { LogBus.err("gimbal state subscribe: ${it.message}") }
        runCatching { aircraft.camera?.setSystemStateCallback(cameraCallback) }
            .onFailure { LogBus.err("camera state subscribe: ${it.message}") }
        runCatching { aircraft.remoteController?.setHardwareStateCallback(rcCallback) }
            .onFailure { LogBus.err("rc state subscribe: ${it.message}") }
    }

    fun stop() {
        if (!running) return
        running = false
        val aircraft = ProductBus.aircraft()
        runCatching { aircraft?.flightController?.setStateCallback(null) }
        runCatching { aircraft?.battery?.setStateCallback(null) }
        runCatching { aircraft?.gimbal?.setStateCallback(null) }
        runCatching { aircraft?.camera?.setSystemStateCallback(null) }
        runCatching { aircraft?.remoteController?.setHardwareStateCallback(null) }
        LogBus.info("telemetry: unsubscribed")
    }

    /** Pre-flight and in-flight sanity checks for the HUD warning strip. */
    private fun firstWarning(state: FlightControllerState): String? {
        if (state.isFailsafeEnabled) return "FAILSAFE ARMED"
        if (state.isLowerThanSeriousBatteryWarningThreshold) return "BATTERY SERIOUS"
        if (state.isLowerThanBatteryWarningThreshold) return "BATTERY LOW"
        if (!state.isHomeLocationSet) return "HOME POINT NOT SET"
        if (state.satelliteCount < 6) return "GPS WEAK — ${state.satelliteCount} SATS"
        val alt = state.aircraftLocation?.altitude?.toFloat() ?: 0f
        if (alt > 400f) return "ALTITUDE ABOVE 400 M"
        return null
    }
}
