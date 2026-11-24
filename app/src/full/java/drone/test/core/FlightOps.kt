package drone.test.core

import dji.common.camera.SettingsDefinitions
import dji.common.error.DJIError
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem
import dji.common.flightcontroller.virtualstick.RollPitchControlMode
import dji.common.flightcontroller.virtualstick.VerticalControlMode
import dji.common.flightcontroller.virtualstick.YawControlMode
import dji.common.flightcontroller.simulator.InitializationData
import dji.common.gimbal.Rotation
import dji.common.gimbal.RotationMode
import dji.common.model.LocationCoordinate2D
import dji.common.util.CommonCallbacks

/** Every command that leaves the phone. Nothing else in the app touches the aircraft. */
object FlightOps {

    private const val TAG = "FLIGHT"

    /** Completion callback that logs to the mission console instead of a toast. */
    private fun cb(what: String) = CommonCallbacks.CompletionCallback { error: DJIError? ->
        if (error == null) {
            LogBus.ok("$what: OK")
        } else {
            LogBus.err("$what: ${error.description}")
        }
    }

    private fun controller() = ProductBus.aircraft()?.flightController

    // ---------------------------------------------------------------- flight

    fun configureStick() {
        val fc = controller() ?: return logNoAircraft("configureStick")
        // American hand 2 (mode 2): left stick = throttle + yaw, right stick = pitch + roll.
        fc.setVerticalControlMode(VerticalControlMode.VELOCITY)
        fc.setRollPitchControlMode(RollPitchControlMode.VELOCITY)
        fc.setYawControlMode(YawControlMode.ANGULAR_VELOCITY)
        fc.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY)
        LogBus.ok("stick map: mode 2 / body frame / velocity")
    }

    fun setVirtualStick(enabled: Boolean) {
        val fc = controller() ?: return logNoAircraft("virtual stick")
        if (enabled) configureStick()
        fc.setVirtualStickModeEnabled(enabled) { error: DJIError? ->
            if (error == null) {
                TelemetryStore.mutate { copy(virtualStickOn = enabled) }
                LogBus.ok("virtual stick ${if (enabled) "ENGAGED" else "released"}")
                if (enabled) VirtualStickController.start() else VirtualStickController.stop()
            } else {
                LogBus.err("virtual stick: ${error.description}")
            }
        }
    }

    fun takeoff() {
        controller()?.startTakeoff(cb("takeoff")) ?: logNoAircraft("takeoff")
    }

    /**
     * Auto-land. DJI aborts the descent at ~0.3 m and waits for operator confirmation
     * on some airframes; call [confirmLanding] only when the ground is verified clear.
     */
    fun land() {
        controller()?.startLanding(cb("land")) ?: logNoAircraft("land")
    }

    fun confirmLanding() {
        controller()?.confirmLanding(cb("confirm landing")) ?: logNoAircraft("confirm landing")
    }

    fun cancelLanding() {
        controller()?.cancelLanding(cb("cancel landing")) ?: logNoAircraft("cancel landing")
    }

    fun goHome() {
        controller()?.startGoHome(cb("return to home")) ?: logNoAircraft("return to home")
    }

    /** Panic: neutralise the sticks and hold position. Does not disarm. */
    fun hover() {
        VirtualStickController.neutralise()
        LogBus.warn("$TAG: sticks neutralised, holding position")
    }

    fun setGoHomeHeight(meters: Int) {
        controller()?.setGoHomeHeightInMeters(meters, cb("RTH altitude ${meters}m"))
            ?: logNoAircraft("RTH altitude")
    }

    fun setMaxHeight(meters: Int) {
        controller()?.setMaxFlightHeight(meters, cb("max height ${meters}m"))
            ?: logNoAircraft("max height")
    }

    fun setMaxRadius(meters: Int) {
        controller()?.setMaxFlightRadius(meters, cb("max radius ${meters}m"))
            ?: logNoAircraft("max radius")
    }

    // -------------------------------------------------------------- simulator

    fun startSimulator() {
        val sim = controller()?.simulator ?: return logNoAircraft("simulator")
        val here = TelemetryStore.current
        val origin = LocationCoordinate2D(
            if (here.latitude.isNaN()) -26.2041 else here.latitude,
            if (here.longitude.isNaN()) 28.0473 else here.longitude
        )
        sim.start(InitializationData.createInstance(origin, 10, 10)) { error: DJIError? ->
            if (error == null) {
                TelemetryStore.mutate { copy(simulatorOn = true) }
                LogBus.ok("simulator running — props will not spin")
            } else {
                LogBus.err("simulator start: ${error.description}")
            }
        }
    }

    fun stopSimulator() {
        val sim = controller()?.simulator ?: return logNoAircraft("simulator")
        sim.stop { error: DJIError? ->
            if (error == null) {
                TelemetryStore.mutate { copy(simulatorOn = false) }
                LogBus.ok("simulator stopped")
            } else {
                LogBus.err("simulator stop: ${error.description}")
            }
        }
    }

    // ----------------------------------------------------------------- gimbal

    /** Continuous pitch at [speed] deg/s. Pass 0f to stop. */
    fun gimbalSpeed(speed: Float) {
        val g = ProductBus.product?.gimbal ?: return logNoAircraft("gimbal")
        // rotate() takes a @NonNull Rotation, so "stop" is a SPEED command of 0 deg/s.
        g.rotate(
            Rotation.Builder()
                .mode(RotationMode.SPEED)
                .pitch(speed)
                .yaw(Rotation.NO_ROTATION)
                .roll(Rotation.NO_ROTATION)
                .time(0.0)
                .build(),
            null
        )
    }

    fun gimbalTo(pitchDeg: Float) {
        val g = ProductBus.product?.gimbal ?: return logNoAircraft("gimbal")
        g.rotate(
            Rotation.Builder()
                .mode(RotationMode.ABSOLUTE_ANGLE)
                .pitch(pitchDeg)
                .yaw(Rotation.NO_ROTATION)
                .roll(Rotation.NO_ROTATION)
                .time(1.0)
                .build(),
            cb("gimbal to ${pitchDeg.toInt()} deg")
        )
    }

    // ----------------------------------------------------------------- camera

    fun shootPhoto() {
        val cam = ProductBus.product?.camera ?: return logNoAircraft("camera")
        cam.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO) { }
        cam.startShootPhoto(cb("capture"))
    }

    fun toggleRecording(start: Boolean) {
        val cam = ProductBus.product?.camera ?: return logNoAircraft("camera")
        cam.setMode(SettingsDefinitions.CameraMode.RECORD_VIDEO) { }
        if (start) cam.startRecordVideo(cb("record start")) else cam.stopRecordVideo(cb("record stop"))
    }

    private fun logNoAircraft(what: String) {
        LogBus.err("$TAG: $what rejected — no aircraft linked")
    }
}
