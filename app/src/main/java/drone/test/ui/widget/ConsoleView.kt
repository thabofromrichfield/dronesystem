package drone.test.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import drone.test.core.LogBus

/**
 * Scrolling mission console. Every SDK result lands here with a timestamp so the
 * operator can replay exactly what the grid was told and what it answered.
 */
class ConsoleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val scroll = ScrollView(context)
    private val text = TextView(context)
    private val buffer = SpannableStringBuilder()
    private var lineCount = 0
    private val maxLines = 200

    private val listener: (LogBus.Line) -> Unit = { line -> append(line) }

    init {
        orientation = VERTICAL
        setBackgroundColor(0xCC04070A.toInt())

        val header = TextView(context).apply {
            text = "// MISSION CONSOLE"
            setTextColor(Palette.NEON)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(12, 8, 12, 4)
            setBackgroundColor(0x3300E5FF)
        }

        text.apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Palette.WHITE)
            setPadding(12, 4, 12, 10)
            text = SpannableStringBuilder()
        }

        scroll.apply {
            isVerticalScrollBarEnabled = false
            addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        LogBus.snapshot().forEach { append(it) }
        LogBus.attach(listener)
    }

    private fun append(line: LogBus.Line) {
        val color = when (line.level) {
            LogBus.Level.OK -> Palette.GREEN
            LogBus.Level.WARN -> Palette.AMBER
            LogBus.Level.ERR -> Palette.RED
            LogBus.Level.INFO -> Palette.WHITE
        }
        val row = "${line.time} ${line.level.name.take(1)} ${line.text}\n"
        val start = buffer.length
        buffer.append(row)
        buffer.setSpan(ForegroundColorSpan(color), start, buffer.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        lineCount++
        if (lineCount > maxLines) {
            val firstBreak = buffer.toString().indexOf('\n')
            if (firstBreak in 0 until buffer.length - 1) {
                buffer.delete(0, firstBreak + 1)
                lineCount--
            }
        }

        text.text = buffer
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    fun clear() {
        buffer.clear()
        lineCount = 0
        text.text = buffer
    }

    override fun onDetachedFromWindow() {
        LogBus.detach(listener)
        super.onDetachedFromWindow()
    }
}
