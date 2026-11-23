package drone.test

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import drone.test.core.LogBus

/**
 * Process entry point. Installs MultiDex and the DJI native library loader before
 * anything else runs, because the SDK's .so files must be present before
 * DJISDKManager is touched.
 */
class GhostGridApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
        // DJI ships its native libs packed; this unpacks them on first run.
        runCatching { com.cySdkyc.clx.Helper.install(this) }
            .onFailure { LogBus.err("native loader: ${it.message}") }
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        LogBus.info("GHOSTGRID process up")
    }

    companion object {
        lateinit var instance: GhostGridApp
            private set
    }
}
