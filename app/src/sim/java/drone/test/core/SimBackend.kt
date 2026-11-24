package drone.test.core

import android.app.Application
import android.content.Context

/**
 * `sim` flavour. There is no DJI Mobile SDK on this classpath at all — not
 * compile-time, not runtime — which is the whole point: the emulator has no
 * ARM translation for DJI's native libraries, so a build that linked them
 * could never load.
 *
 * Every telemetry value comes from [DemoTelemetry]. Every flight command is
 * refused and logged, so nothing here can be mistaken for flight capability.
 */
class SimBackend : UavBackend {

    override val label = "SIM BACKEND — NO SDK LINKED"
    override val canFly = false

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(UavLinkState) -> Unit>()

    override fun installNatives(app: Application) {
        LogBus.info("sim backend: no native libraries to unpack")
    }

    /**
     * Reports LINKED straight away and starts the synthetic feed, so the boot
     * screen's ENTER GRID button is usable without an App Key or an aircraft.
     */
    override fun register(context: Context) {
        LogBus.ok("sim backend online — synthetic telemetry only")
        notify(UavLinkState.LINKED)
    }

    override fun link(): UavLinkState = UavLinkState.LINKED

    override fun lastError(): String? = null

    override fun addLinkListener(l: (UavLinkState) -> Unit) {
        listeners.add(l)
        l(UavLinkState.LINKED)
    }

    override fun removeLinkListener(l: (UavLinkState) -> Unit) {
        listeners.remove(l)
    }

    private fun notify(state: UavLinkState) {
        val it = listeners.iterator()
        while (it.hasNext()) it.next().invoke(state)
    }

    override fun startTelemetry() = DemoTelemetry.start()

    override fun stopTelemetry() = DemoTelemetry.stop()

    override fun stickActive(): Boolean = false

    override fun setStick(pitch: Float, roll: Float, yaw: Float, throttle: Float) = Unit

    override fun maxHorizontalMs(): Float = 6f

    override fun setVirtualStick(enabled: Boolean) = refuse("virtual stick")

    override fun takeoff() = refuse("takeoff")
    override fun hover() = refuse("hover")
    override fun land() = refuse("land")
    override fun confirmLanding() = refuse("confirm landing")
    override fun cancelLanding() = refuse("abort landing")
    override fun goHome() = refuse("return to home")

    override fun setGoHomeHeight(meters: Int) = refuse("RTH altitude")
    override fun setMaxHeight(meters: Int) = refuse("max height")
    override fun setMaxRadius(meters: Int) = refuse("max radius")

    override fun gimbalSpeed(degPerSec: Float) = refuse("gimbal jog")
    override fun gimbalTo(pitchDeg: Float) = refuse("gimbal angle")

    override fun shootPhoto() = refuse("capture")
    override fun toggleRecording(start: Boolean) = refuse("record")

    override fun simulatorOn(): Boolean = false
    override fun startSimulator() = refuse("onboard simulator")
    override fun stopSimulator() = refuse("onboard simulator")

    private fun refuse(what: String) {
        LogBus.err("SIM BUILD: $what refused — this flavour links no DJI SDK")
    }
}

/** Supplied by the `sim` source set. */
object UavBackendFactory {
    fun create(): UavBackend = SimBackend()
}
