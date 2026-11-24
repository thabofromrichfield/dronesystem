package drone.test.core

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Rolling mission log. Everything the grid does is written here so the operator
 * can read the exact command, result and timestamp. Never throws.
 */
object LogBus {

    enum class Level { INFO, OK, WARN, ERR }

    class Line(val time: String, val level: Level, val text: String)

    private const val MAX_LINES = 500

    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(Line) -> Unit>()
    private val history = java.util.ArrayDeque<Line>()
    private val lock = Any()

    fun snapshot(): List<Line> = synchronized(lock) { history.toList() }

    fun attach(l: (Line) -> Unit) {
        listeners.add(l)
        val past = snapshot()
        main.post { past.forEach { l(it) } }
    }

    fun detach(l: (Line) -> Unit) {
        listeners.remove(l)
    }

    fun log(level: Level, text: String) {
        val line = Line(stamp.format(Date()), level, text)
        synchronized(lock) {
            history.addLast(line)
            while (history.size > MAX_LINES) history.removeFirst()
        }
        main.post {
            val it = listeners.iterator()
            while (it.hasNext()) {
                try {
                    it.next().invoke(line)
                } catch (ignored: Throwable) {
                }
            }
        }
    }

    fun info(text: String) = log(Level.INFO, text)
    fun ok(text: String) = log(Level.OK, text)
    fun warn(text: String) = log(Level.WARN, text)
    fun err(text: String) = log(Level.ERR, text)
}
