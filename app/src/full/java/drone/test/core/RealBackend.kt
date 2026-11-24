package drone.test.core

import android.app.Application
import android.content.Context

/**
 * `full` flavour. Delegates to the real DJI Mobile SDK 4.18 objects, which all
 * live in this source set and nowhere else.
 *
 */
class RealBackend : UavBackend {

    override val label = "DJI MOBILE SDK 4.18"
    override val canFly = true

    override fun installNatives(app: Application) {
        runCatching { com.cySdkyc.clx.Helper.install(app) }
            .onFailure { LogBus.err("native loader: ${it.message}") }
    }

    override fun register(context: Context) = ProductBus.register(context)

    override fun link(): UavLinkState = when (ProductBus.link) {
        ProductBus.Link.IDLE -> UavLinkState.IDLE
        ProductBus.Link.REGISTERING -> UavLinkState.REGISTERING
        ProductBus.Link.NO_KEY -> UavLinkState.NO_KEY
        ProductBus.Link.KEY_REJECTED -> UavLinkState.KEY_REJECTED
        ProductBus.Link.LINKED -> UavLinkState.LINKED
        ProductBus.Link.STANDBY -> UavLinkState.STANDBY
    }

    override fun lastError(): String? = ProductBus.lastError

    // The wrapper has to be the same instance on add and remove, otherwise
    // ProductBus never finds it and every screen leaks a listener on stop.
    private val wrapped =
        java.util.concurrent.ConcurrentHashMap<(UavLinkState) -> Unit, (ProductBus.Link) -> Unit>()

    override fun addLinkListener(l: (UavLinkState) -> Unit) {
        val w: (ProductBus.Link) -> Unit = { l(translate(it)) }
        wrapped[l] = w
        ProductBus.addLinkListener(w)
    }

    override fun removeLinkListener(l: (UavLinkState) -> Unit) {
        wrapped.remove(l)?.let { ProductBus.removeLinkListener(it) }
    }

    private fun translate(s: ProductBus.Link): UavLinkState = when (s) {
        ProductBus.Link.IDLE -> UavLinkState.IDLE
        ProductBus.Link.REGISTERING -> UavLinkState.REGISTERING
        ProductBus.Link.NO_KEY -> UavLinkState.NO_KEY
        ProductBus.Link.KEY_REJECTED -> UavLinkState.KEY_REJECTED
        ProductBus.Link.LINKED -> UavLinkState.LINKED
        ProductBus.Link.STANDBY -> UavLinkState.STANDBY
    }

    override fun startTelemetry() = TelemetrySource.start()

    override fun stopTelemetry() = TelemetrySource.stop()

    override fun stickActive(): Boolean = VirtualStickController.active

    override fun setStick(pitch: Float, roll: Float, yaw: Float, throttle: Float) {
        if (!VirtualStickController.active) return
        VirtualStickController.pitch = pitch
        VirtualStickController.roll = roll
        VirtualStickController.yaw = yaw
        VirtualStickController.throttle = throttle
    }

    override fun maxHorizontalMs(): Float = VirtualStickController.maxHorizontalMs

    override fun setVirtualStick(enabled: Boolean) {
        FlightOps.setVirtualStick(enabled)
        if (!enabled) {
            // Kill the local stream immediately. Waiting for DJI's callback
            // would leave frames going out after the UI released the sticks.
            VirtualStickController.stop()
        }
    }

    override fun takeoff() = FlightOps.takeoff()
    override fun hover() = FlightOps.hover()
    override fun land() = FlightOps.land()
    override fun confirmLanding() = FlightOps.confirmLanding()
    override fun cancelLanding() = FlightOps.cancelLanding()
    override fun goHome() = FlightOps.goHome()

    override fun setGoHomeHeight(meters: Int) = FlightOps.setGoHomeHeight(meters)
    override fun setMaxHeight(meters: Int) = FlightOps.setMaxHeight(meters)
    override fun setMaxRadius(meters: Int) = FlightOps.setMaxRadius(meters)

    override fun gimbalSpeed(degPerSec: Float) = FlightOps.gimbalSpeed(degPerSec)
    override fun gimbalTo(pitchDeg: Float) = FlightOps.gimbalTo(pitchDeg)

    override fun shootPhoto() = FlightOps.shootPhoto()
    override fun toggleRecording(start: Boolean) = FlightOps.toggleRecording(start)

    override fun simulatorOn(): Boolean = TelemetryStore.current.simulatorOn

    override fun startSimulator() = FlightOps.startSimulator()
    override fun stopSimulator() = FlightOps.stopSimulator()
}

/** Supplied by the `full` source set. `Backend` resolves this at class load. */
object UavBackendFactory {
    fun create(): UavBackend = RealBackend()
}
