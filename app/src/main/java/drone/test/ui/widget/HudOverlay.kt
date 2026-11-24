package drone.test.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import drone.test.core.Telemetry
import drone.test.util.Fmt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Head-up glass drawn straight over the video feed: artificial horizon, heading
 * tape, speed and altitude tapes, reticle, warning strip, telemetry footer.
 * All geometry is derived from [data]; nothing here touches the SDK.
 */
class HudOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var data: Telemetry = Telemetry()
        set(value) {
            field = value
            invalidate()
        }

    /** Pixels of ladder travel per degree of pitch. Recomputed on size change. */
    private var pxPerDeg = 8f
    private var scanlines: Bitmap? = null

    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Palette.NEON
    }
    private val accentDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Palette.NEON_DIM
    }
    private val ladder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        color = 0xAAE6F7FF.toInt()
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.NEON
        textSize = 26f
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val labelRight = Paint(label).apply { textAlign = Paint.Align.RIGHT }
    private val labelCenter = Paint(label).apply { textAlign = Paint.Align.CENTER }
    private val valueBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.GREY
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.RED
        textSize = 32f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val warnBox = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Palette.RED
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.PANEL
    }
    private val greenFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.GREEN
    }
    private val redFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.RED
    }
    private val amberFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.AMBER
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val cornerPath = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pxPerDeg = h / 42f
        scanlines = buildScanlines(w, h)
        accent.strokeWidth = max(2f, w / 640f)
    }

    private fun buildScanlines(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.color = 0x1800E5FF
        p.strokeWidth = 1f
        var y = 0f
        while (y < h) {
            c.drawLine(0f, y, w.toFloat(), y, p)
            y += 4f
        }
        return bmp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        scanlines?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }

        drawCorners(canvas, w, h)

        if (!data.linked && !data.demo) {
            drawCenterNotice(canvas, w, h, "NO LINK", "AWAITING AIRCRAFT HANDSHAKE")
            return
        }

        drawHorizon(canvas, w, h)
        drawReticle(canvas, w, h)
        drawHeadingTape(canvas, w, h)
        drawSpeedTape(canvas, w, h)
        drawAltitudeTape(canvas, w, h)
        drawFooter(canvas, w, h)
        drawWarning(canvas, w, h)

        if (data.demo) drawSimBadge(canvas, w, h)
        if (data.recording) drawRecBadge(canvas, w, h)
    }

    // ------------------------------------------------------------------ frame

    private fun drawCorners(canvas: Canvas, w: Float, h: Float) {
        val arm = min(w, h) * 0.07f
        val pad = 14f
        cornerPath.reset()
        // top-left
        cornerPath.moveTo(pad, pad + arm); cornerPath.lineTo(pad, pad); cornerPath.lineTo(pad + arm, pad)
        // top-right
        cornerPath.moveTo(w - pad - arm, pad); cornerPath.lineTo(w - pad, pad); cornerPath.lineTo(w - pad, pad + arm)
        // bottom-right
        cornerPath.moveTo(w - pad, h - pad - arm); cornerPath.lineTo(w - pad, h - pad); cornerPath.lineTo(w - pad - arm, h - pad)
        // bottom-left
        cornerPath.moveTo(pad + arm, h - pad); cornerPath.lineTo(pad, h - pad); cornerPath.lineTo(pad, h - pad - arm)
        canvas.drawPath(cornerPath, accent)
    }

    // -------------------------------------------------------------- attitude

    private fun drawHorizon(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        canvas.save()
        canvas.clipRect(cx - w * 0.22f, cy - h * 0.26f, cx + w * 0.22f, cy + h * 0.26f)
        canvas.translate(cx, cy)
        canvas.rotate(-data.rollDeg)
        canvas.translate(0f, data.pitchDeg * pxPerDeg)

        val span = w * 0.30f
        canvas.drawLine(-span, 0f, -span * 0.18f, 0f, ladder)
        canvas.drawLine(span * 0.18f, 0f, span, 0f, ladder)

        var deg = -60
        while (deg <= 60) {
            if (deg != 0) {
                val y = -deg * pxPerDeg
                val major = deg % 20 == 0
                val half = if (major) span * 0.30f else span * 0.15f
                canvas.drawLine(-half, y, half, y, ladder)
                if (major) {
                    val txt = abs(deg).toString()
                    canvas.drawText(txt, half + 10f, y + 9f, caption)
                    canvas.drawText(txt, -half - 10f, y + 9f, Paint(caption).apply { textAlign = Paint.Align.RIGHT })
                }
            }
            deg += 10
        }
        canvas.restore()
    }

    private fun drawReticle(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val gap = w * 0.018f
        val arm = w * 0.055f
        canvas.drawLine(cx - gap - arm, cy, cx - gap, cy, accent)
        canvas.drawLine(cx + gap, cy, cx + gap + arm, cy, accent)
        canvas.drawLine(cx, cy - gap, cx, cy + gap, accent)
        canvas.drawCircle(cx, cy, 3.5f, Paint(accent).apply { style = Paint.Style.FILL })
    }

    // ------------------------------------------------------------ heading

    private fun drawHeadingTape(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val top = h * 0.055f
        val tapeW = w * 0.30f
        val pxPer = tapeW / 60f
        val heading = data.headingDeg
        if (heading.isNaN()) return

        canvas.drawRect(cx - tapeW / 2f, top - 6f, cx + tapeW / 2f, top + 52f, fill)
        canvas.drawLine(cx - tapeW / 2f, top + 52f, cx + tapeW / 2f, top + 52f, accentDim)

        canvas.save()
        canvas.clipRect(cx - tapeW / 2f, top - 6f, cx + tapeW / 2f, top + 52f)
        var d = (heading - 30f)
        while (d <= heading + 30f) {
            val norm = ((d % 360f) + 360f) % 360f
            val x = cx + (d - heading) * pxPer
            if (norm % 10f < 0.001f || norm % 10f > 9.999f) {
                val major = (norm.toInt() % 30) == 0
                val tickH = if (major) 16f else 9f
                canvas.drawLine(x, top + 52f, x, top + 52f - tickH, accent)
                if (major) canvas.drawText(Fmt.heading(norm), x, top + 24f, labelCenter)
            }
            d += 1f
        }
        canvas.restore()

        // caret
        canvas.drawLine(cx, top + 52f, cx, top + 62f, accent)
        canvas.drawText(
            "${Fmt.heading(heading)} ${Fmt.cardinal(heading)}",
            cx, top + 86f, valueBig
        )
    }

    // -------------------------------------------------------- speed / alt

    private fun drawSpeedTape(canvas: Canvas, w: Float, h: Float) {
        val x = w * 0.13f
        val cy = h / 2f
        drawVerticalTape(canvas, x, cy, data.groundSpeedMs, "GS", "M/S", 5f, w, h, false)
    }

    private fun drawAltitudeTape(canvas: Canvas, w: Float, h: Float) {
        val x = w * 0.87f
        val cy = h / 2f
        drawVerticalTape(canvas, x, cy, data.altitudeM, "ALT", "M", 5f, w, h, true)

        // vertical speed indicator
        val vs = data.verticalSpeedMs
        val maxVs = 6f
        val barH = h * 0.22f
        val bx = x + w * 0.045f
        val frac = (vs / maxVs).coerceIn(-1f, 1f)
        canvas.drawLine(bx, cy - barH / 2f, bx, cy + barH / 2f, accentDim)
        val paint = if (vs >= 0) greenFill else amberFill
        if (vs >= 0) {
            canvas.drawRect(bx - 4f, cy - barH / 2f * frac, bx + 4f, cy, paint)
        } else {
            canvas.drawRect(bx - 4f, cy, bx + 4f, cy + barH / 2f * (-frac), paint)
        }
        canvas.save()
        canvas.translate(bx + 22f, cy)
        canvas.rotate(90f)
        canvas.drawText("VS ${Fmt.num(vs, 1)} M/S", 0f, 0f, caption)
        canvas.restore()
    }

    private fun drawVerticalTape(
        canvas: Canvas,
        x: Float,
        cy: Float,
        value: Float,
        name: String,
        unit: String,
        step: Float,
        w: Float,
        h: Float,
        rightSide: Boolean
    ) {
        val pxPer = h / 60f
        val boxW = w * 0.085f
        val boxH = 56f
        val tick = if (rightSide) -18f else 18f

        canvas.save()
        canvas.clipRect(x - boxW, cy - h * 0.20f, x + boxW, cy + h * 0.20f)
        val first = ((value - 20f) / step).toInt() * step.toInt()
        var v = first.toFloat()
        while (v <= value + 20f) {
            val y = cy + (value - v) * pxPer
            canvas.drawLine(x, y, x + tick, y, accentDim)
            canvas.drawText(
                v.toInt().toString(),
                if (rightSide) x + tick + 8f else x - tick - 8f,
                y + 7f,
                if (rightSide) label else labelRight
            )
            v += step
        }
        canvas.restore()

        // readout box
        canvas.drawRect(x - boxW / 2f, cy - boxH / 2f, x + boxW / 2f, cy + boxH / 2f, fill)
        canvas.drawRect(x - boxW / 2f, cy - boxH / 2f, x + boxW / 2f, cy + boxH / 2f, accent)
        val shown = if (value.isNaN()) "--" else String.format(java.util.Locale.US, "%.0f", value)
        canvas.drawText(shown, x, cy + 14f, valueBig)

        val capY = if (rightSide) cy - boxH / 2f - 12f else cy + boxH / 2f + 26f
        canvas.drawText("$name $unit", x, capY, caption)
    }

    // -------------------------------------------------------------- footer

    private fun drawFooter(canvas: Canvas, w: Float, h: Float) {
        val y = h - 22f
        val left = 26f
        val t = data

        // battery
        val pct = t.batteryPercent
        val batPaint = when {
            pct < 0 -> Paint(greenFill)
            pct <= 15 -> redFill
            pct <= 30 -> amberFill
            else -> greenFill
        }
        val bw = w * 0.09f
        val bh = 18f
        val by = y - bh
        canvas.drawRect(left, by, left + bw, by + bh, Paint(accentDim).apply { style = Paint.Style.STROKE })
        if (pct >= 0) canvas.drawRect(left + 2f, by + 2f, left + 2f + (bw - 4f) * (pct / 100f), by + bh - 2f, batPaint)
        canvas.drawText(
            "${Fmt.battery(pct)}  ${Fmt.num(t.batteryVolts, 2)}V  ${Fmt.num(t.batteryTempC, 0)}C",
            left + bw + 14f, y, label
        )

        // centre: mode + timer
        canvas.drawText(
            "${t.flightMode}   T+${Fmt.mmss(t.flightTimeSec)}   GIM ${Fmt.num(t.gimbalPitchDeg, 0)}",
            w / 2f, y, labelCenter
        )

        // right: satellites + coords
        canvas.drawText(
            "SAT ${t.satellites}   ${Fmt.num(t.latitude, 5)} ${Fmt.num(t.longitude, 5)}",
            w - left, y, labelRight
        )

        // second line: distances
        canvas.drawText(
            "DIST ${Fmt.dist(t.distanceFromHomeM)}${Fmt.distUnit(t.distanceFromHomeM)}   " +
                "H ${Fmt.dist(t.altitudeM)} M   RC ${t.rcModeSwitch}",
            w / 2f, y - 26f, labelCenter
        )
    }

    // ------------------------------------------------------------- warnings

    private fun drawWarning(canvas: Canvas, w: Float, h: Float) {
        val msg = data.warning ?: return
        val blink = (System.currentTimeMillis() / 400) % 2 == 0L
        if (blink) {
            val tw = warnPaint.measureText(msg) + 40f
            val cx = w / 2f
            val top = h * 0.24f
            canvas.drawRect(cx - tw / 2f, top - 34f, cx + tw / 2f, top + 12f, fill)
            canvas.drawRect(cx - tw / 2f, top - 34f, cx + tw / 2f, top + 12f, warnBox)
            canvas.drawText(msg, cx, top, warnPaint)
        }
    }

    private fun drawRecBadge(canvas: Canvas, w: Float, h: Float) {
        val blink = (System.currentTimeMillis() / 500) % 2 == 0L
        if (!blink) return
        val p = Paint(redFill).apply { style = Paint.Style.FILL }
        canvas.drawCircle(w - 60f, 54f, 12f, p)
        canvas.drawText("REC", w - 84f, 62f, Paint(labelRight).apply { color = Palette.RED })
    }

    private fun drawSimBadge(canvas: Canvas, w: Float, h: Float) {
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.AMBER
            textSize = 28f
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
        }
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Palette.AMBER
        }
        val text = "SIM FEED"
        val tw = badge.measureText(text) + 36f
        canvas.drawRect(24f, 24f, 24f + tw, 72f, fill)
        canvas.drawRect(24f, 24f, 24f + tw, 72f, box)
        canvas.drawText(text, 24f + tw / 2f, 58f, Paint(badge).apply { textAlign = Paint.Align.CENTER })

        // diagonal watermark
        canvas.save()
        canvas.rotate(-18f, w / 2f, h / 2f)
        val wm = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFB300
            textSize = h * 0.16f
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SIMULATION", w / 2f, h / 2f + h * 0.05f, wm)
        canvas.restore()
    }

    private fun drawCenterNotice(canvas: Canvas, w: Float, h: Float, title: String, sub: String) {
        val cx = w / 2f
        val cy = h / 2f
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Palette.NEON
        }
        canvas.drawRect(cx - 220f, cy - 80f, cx + 220f, cy + 80f, fill)
        canvas.drawRect(cx - 220f, cy - 80f, cx + 220f, cy + 80f, box)
        canvas.drawText(title, cx, cy - 6f, Paint(valueBig).apply { textSize = 44f; color = Palette.NEON })
        canvas.drawText(sub, cx, cy + 34f, caption)

        // sweep bar
        val sweep = ((System.currentTimeMillis() / 12) % 440).toFloat()
        canvas.drawRect(cx - 220f + sweep, cy + 56f, cx - 220f + sweep + 60f, cy + 60f, greenFill)
    }
}
