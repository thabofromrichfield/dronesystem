package drone.test.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/** Holds the newest [Telemetry] frame and pushes it to the UI on the main thread. */
object TelemetryStore {

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(Telemetry) -> Unit>()
    private val lock = Any()

    @Volatile
    var current: Telemetry = Telemetry()
        private set

    fun mutate(block: Telemetry.() -> Telemetry) {
        val next = synchronized(lock) { current.block().also { current = it } }
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

    fun reset() {
        mutate { Telemetry() }
    }

    fun attach(l: (Telemetry) -> Unit) {
        listeners.add(l)
        l(current)
    }

    fun detach(l: (Telemetry) -> Unit) {
        listeners.remove(l)
    }
}
