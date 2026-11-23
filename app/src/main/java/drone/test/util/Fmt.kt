package drone.test.util

import java.util.Locale
import kotlin.math.abs

/** Fixed-width, unit-explicit formatting for the HUD. Never throws. */
object Fmt {

    private const val DASH = "--"

    fun num(v: Float, decimals: Int = 1): String =
        if (v.isNaN() || v.isInfinite()) DASH else String.format(Locale.US, "%.${decimals}f", v)

    fun num(v: Double, decimals: Int = 6): String =
        if (v.isNaN() || v.isInfinite()) DASH else String.format(Locale.US, "%.${decimals}f", v)

    fun int(v: Int, placeholder: Int = -1): String = if (v == placeholder) DASH else v.toString()

    /** Distance under 1000 m in metres, above that in km with 2 decimals. */
    fun dist(m: Float): String = when {
        m.isNaN() -> DASH
        abs(m) < 1000f -> String.format(Locale.US, "%.0f", m)
        else -> String.format(Locale.US, "%.2f", m / 1000f)
    }

    fun distUnit(m: Float): String = if (!m.isNaN() && abs(m) >= 1000f) "km" else "m"

    fun heading(deg: Float): String {
        if (deg.isNaN()) return DASH
        val d = ((deg % 360f) + 360f) % 360f
        return String.format(Locale.US, "%03d", d.toInt())
    }

    fun cardinal(deg: Float): String {
        if (deg.isNaN()) return "--"
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val d = ((deg % 360f) + 360f) % 360f
        return names[((d + 22.5f) / 45f).toInt() % 8]
    }

    fun mmss(totalSeconds: Int): String {
        if (totalSeconds < 0) return DASH
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun battery(pct: Int): String = if (pct < 0) DASH else "$pct%"

    /** 0..1 -> 0..n block characters, for the console and battery bars. */
    fun bar(fraction: Float, n: Int = 10): String {
        val filled = (fraction.coerceIn(0f, 1f) * n).toInt()
        return "[" + "#".repeat(filled) + "-".repeat(n - filled) + "]"
    }
}
