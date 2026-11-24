package drone.test.core

import android.content.Context
import dji.common.error.DJIError
import dji.sdk.base.BaseComponent
import dji.sdk.base.BaseProduct
import dji.sdk.products.Aircraft
import dji.sdk.products.HandHeld
import dji.common.error.DJISDKError
import dji.sdk.sdkmanager.DJISDKInitEvent
import dji.sdk.sdkmanager.DJISDKManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns the DJI SDK lifecycle: app registration, then product connect/disconnect.
 * Lives for the whole process. UI code never talks to DJISDKManager directly.
 */
object ProductBus {

    enum class Link {
        IDLE,          // registerApp not called yet
        REGISTERING,   // waiting for DJI's servers to validate the App Key
        NO_KEY,        // App Key missing from the manifest
        KEY_REJECTED,  // App Key present but DJI rejected it
        LINKED,        // SDK registered, aircraft connected
        STANDBY        // SDK registered, nothing connected
    }

    var link: Link = Link.IDLE
        private set

    var product: BaseProduct? = null
        private set

    var lastError: String? = null
        private set

    private val listeners = CopyOnWriteArrayList<(Link) -> Unit>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private val sdkCallback = object : DJISDKManager.SDKManagerCallback {

        override fun onRegister(error: DJIError?) {
            if (error == DJISDKError.REGISTRATION_SUCCESS) {
                LogBus.ok("SDK registered — opening product channel")
                setLink(Link.STANDBY)
                try {
                    DJISDKManager.getInstance().startConnectionToProduct()
                } catch (t: Throwable) {
                    LogBus.err("startConnectionToProduct: ${t.message}")
                }
            } else {
                val msg = error?.description ?: "unknown"
                lastError = msg
                LogBus.err("SDK registration failed: $msg")
                hintFor(error)?.let { LogBus.err(it) }
                setLink(Link.KEY_REJECTED)
            }
        }

        // DJI 4.18 names these onProductConnect / onProductDisconnect, with no
        // "-ed". The past-tense names are what the older SDK docs show.
        override fun onProductConnect(p: BaseProduct?) {
            product = p
            if (p != null) {
                LogBus.ok("Aircraft linked: ${p.model}")
                TelemetryStore.mutate { copy(aircraftModel = "${p.model}", linked = true) }
                setLink(Link.LINKED)
            }
        }

        override fun onProductChanged(p: BaseProduct?) {
            product = p
            if (p != null) {
                TelemetryStore.mutate { copy(aircraftModel = "${p.model}", linked = true) }
                setLink(Link.LINKED)
            }
        }

        override fun onProductDisconnect() {
            LogBus.warn("Aircraft channel dropped")
            product = null
            TelemetryStore.mutate { copy(linked = false) }
            setLink(Link.STANDBY)
        }

        // One callback covers both directions; a null new component means the
        // component went down.
        override fun onComponentChange(
            key: BaseProduct.ComponentKey?,
            oldComponent: BaseComponent?,
            newComponent: BaseComponent?
        ) {
            if (newComponent == null) LogBus.warn("component down: $key")
            else LogBus.info("component up: $key")
        }

        /** The SDK's own initialisation progress, 0..100. */
        override fun onInitProcess(event: DJISDKInitEvent?, percent: Int) {
            if (percent % 25 == 0) LogBus.info("SDK init $percent%")
        }

        /** HMS databases downloading in the background. Nothing to show the pilot. */
        override fun onDatabaseDownloadProgress(current: Long, total: Long) { }
    }

    /** Call once from the Application. Safe to call again. */
    fun register(context: Context) {
        if (link != Link.IDLE) return
        setLink(Link.REGISTERING)
        TelemetrySource.stop()
        try {
            DJISDKManager.getInstance().registerApp(context.applicationContext, sdkCallback)
        } catch (t: Throwable) {
            lastError = t.message
            LogBus.err("registerApp threw: ${t.message}")
            setLink(Link.KEY_REJECTED)
        }
    }

    fun addLinkListener(l: (Link) -> Unit) {
        listeners.add(l)
        l(link)
    }

    fun removeLinkListener(l: (Link) -> Unit) {
        listeners.remove(l)
    }

    fun aircraft(): Aircraft? = product?.let { if (it is Aircraft) it else null }

    /**
     * DJI's registration failures all surface as one generic description on
     * screen, which leaves the pilot guessing. These are the ones worth
     * distinguishing, matched on DJI's own error constants the way their sample
     * matches REGISTRATION_SUCCESS.
     */
    private fun hintFor(error: DJIError?): String? = when (error) {
        DJISDKError.INVALID_METADATA ->
            "INVALID_METADATA usually means the App Key itself is malformed - a stray " +
                "space or newline, or a truncated paste. Re-copy it from " +
                "developer.dji.com with no surrounding whitespace."
        DJISDKError.BUNDLE_NOT_MATCH ->
            "BUNDLE_NOT_MATCH: this App Key is registered against a different package " +
                "name. It must be registered for 'drone.test'."
        DJISDKError.APP_KEY_NOT_EXIST,
        DJISDKError.INVALID_APP_KEY ->
            "DJI does not recognise this key at all. Check it was copied in full."
        DJISDKError.EMPTY_APP_KEY ->
            "The App Key reached the SDK empty."
        DJISDKError.COULD_NOT_CONNECT_TO_INTERNET,
        DJISDKError.HTTP_TIMEOUT ->
            "The phone could not reach DJI's servers. Registration needs internet."
        DJISDKError.MAX_ACTIVATION_COUNT_REACHED ->
            "This key has hit its activation limit on developer.dji.com."
        DJISDKError.APP_KEY_LEVEL_NOT_PERMITTED ->
            "This key's permission level does not allow the feature requested."
        else -> null
    }

    fun isHandHeld(): Boolean = product is HandHeld

    private fun setLink(next: Link) {
        link = next
        main.post {
            val it = listeners.iterator()
            while (it.hasNext()) {
                try {
                    it.next().invoke(next)
                } catch (ignored: Throwable) {
                }
            }
        }
    }
}
