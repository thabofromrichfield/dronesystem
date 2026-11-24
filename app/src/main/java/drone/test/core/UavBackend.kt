package drone.test.core

import android.app.Application
import android.content.Context

/**
 * The seam between the interface and whatever is behind it.
 *
 * Two implementations exist, one per build flavour, and they are the only
 * classes in the project allowed to know which one is running:
 *
 *  - `full` → `RealBackend`, the DJI Mobile SDK 4.18. Moves a real aircraft.
 *  - `sim`  → `SimBackend`, no DJI classes at all. Synthetic telemetry only,
 *             so the app links and runs on an x86_64 emulator.
 *
 * Video is deliberately not part of this interface. `FpvSurface` is declared
 * once per flavour under the same fully-qualified name, so the shared layout
 * inflates and the shared activity drives it directly in either build.
 *
 * Nothing in `ui/` imports `dji.*`. That rule is what makes the emulator build
 * possible, and it is checked by the verification script in the repo notes.
 */
enum class UavLinkState {
    IDLE,          // registerApp not called yet
    REGISTERING,   // waiting for DJI's servers to validate the App Key
    NO_KEY,        // App Key missing
    KEY_REJECTED,  // App Key present but rejected
    LINKED,        // SDK registered, aircraft connected
    STANDBY        // SDK registered, nothing connected
}

interface UavBackend {

    /** Human-readable flavour name, shown on the boot screen. */
    val label: String

    /** True when this backend can command a real aircraft. */
    val canFly: Boolean

    // ------------------------------------------------------------ lifecycle

    /**
     * Unpack native libraries. No-op where there are none.
     *
     * Takes an [Application], not a Context: DJI's native-loader helper is
     * declared against Application. GhostGridApp is one, so the caller already
     * passes the right thing.
     */
    fun installNatives(app: Application)

    /** Validate the App Key and open the product channel. */
    fun register(context: Context)

    fun link(): UavLinkState

    fun lastError(): String?

    fun addLinkListener(l: (UavLinkState) -> Unit)

    fun removeLinkListener(l: (UavLinkState) -> Unit)

    // ------------------------------------------------------------ telemetry

    fun startTelemetry()

    fun stopTelemetry()

    // -------------------------------------------------------- virtual stick

    fun stickActive(): Boolean

    /** Normalised -1..1. Ignored unless [stickActive]. */
    fun setStick(pitch: Float, roll: Float, yaw: Float, throttle: Float)

    /** Horizontal speed ceiling in m/s, quoted in the engage dialog. */
    fun maxHorizontalMs(): Float

    fun setVirtualStick(enabled: Boolean)

    // --------------------------------------------------------------- flight

    fun takeoff()

    fun hover()

    fun land()

    fun confirmLanding()

    fun cancelLanding()

    fun goHome()

    fun setGoHomeHeight(meters: Int)

    fun setMaxHeight(meters: Int)

    fun setMaxRadius(meters: Int)

    // --------------------------------------------------------------- gimbal

    fun gimbalSpeed(degPerSec: Float)

    fun gimbalTo(pitchDeg: Float)

    // --------------------------------------------------------------- camera

    fun shootPhoto()

    fun toggleRecording(start: Boolean)

    // ------------------------------------------------------------ simulator

    fun simulatorOn(): Boolean

    fun startSimulator()

    fun stopSimulator()
}
