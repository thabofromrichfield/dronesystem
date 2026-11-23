package drone.test.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Touch joystick for virtual-stick flight. Reports normalised -1..1 on both axes:
 * x = left/right, y = up/down where pushing UP is +1.
 * Auto-recentres on release so the aircraft can never be left with a stuck stick.
 */
class CyberJoystick @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    interface StickListener {
        /** Called on every move and on release (x = 0, y = 0). */
        fun onStick(joystick: CyberJoystick, x: Float, y: Float)
    }

    var listener: StickListener? = null

    /** Caption drawn under the pad, e.g. "THR / YAW". */
    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private var padX = 0f
    private var padY = 0f
    private var padRadius = 0f
    private var knobRadius = 0f
    private var knobX = 0f
    private var knobY = 0f
    private var trackingPointer = -1
    private var returnAnim: ValueAnimator? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Palette.NEON
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Palette.NEON_DIM
    }
    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.PANEL
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.NEON
    }
    private val knobCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.BLACK
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.GREY
        textSize = 26f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }

    init {
        // setShadowLayer needs a software layer to render the neon glow.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        padX = w / 2f
        padY = h / 2f
        padRadius = minOf(w, h) * 0.44f
        knobRadius = padRadius * 0.34f
        knobX = padX
        knobY = padY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawCircle(padX, padY, padRadius, padPaint)
        canvas.drawCircle(padX, padY, padRadius, ringPaint)
        canvas.drawCircle(padX, padY, padRadius * 0.62f, gridPaint)
        canvas.drawCircle(padX, padY, padRadius * 0.30f, gridPaint)

        // crosshair
        canvas.drawLine(padX - padRadius, padY, padX + padRadius, padY, gridPaint)
        canvas.drawLine(padX, padY - padRadius, padX, padY + padRadius, gridPaint)

        // 12 tick marks
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val inner = padRadius * 0.90f
            val outer = padRadius
            canvas.drawLine(
                (padX + inner * kotlin.math.cos(a)).toFloat(),
                (padY + inner * kotlin.math.sin(a)).toFloat(),
                (padX + outer * kotlin.math.cos(a)).toFloat(),
                (padY + outer * kotlin.math.sin(a)).toFloat(),
                gridPaint
            )
        }

        // knob
        knobPaint.setShadowLayer(18f, 0f, 0f, Palette.NEON)
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint)
        knobPaint.clearShadowLayer()
        canvas.drawCircle(knobX, knobY, knobRadius * 0.42f, knobCorePaint)

        if (label.isNotEmpty()) {
            canvas.drawText(label, padX, padY + padRadius + 34f, labelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                returnAnim?.cancel()
                trackingPointer = event.getPointerId(0)
                moveTo(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(trackingPointer)
                if (idx >= 0) moveTo(event.getX(idx), event.getY(idx))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                trackingPointer = -1
                release()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun moveTo(rawX: Float, rawY: Float) {
        var dx = rawX - padX
        var dy = rawY - padY
        val len = hypot(dx, dy)
        val limit = padRadius - knobRadius * 0.4f
        if (limit <= 0f) return
        if (len > limit) {
            dx = dx / len * limit
            dy = dy / len * limit
        }
        knobX = padX + dx
        knobY = padY + dy
        invalidate()
        listener?.onStick(this, dx / limit, -dy / limit)
    }

    private fun release() {
        val fromX = knobX
        val fromY = knobY
        returnAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 120
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                knobX = fromX + (padX - fromX) * (1f - f)
                knobY = fromY + (padY - fromY) * (1f - f)
                invalidate()
                listener?.onStick(this@CyberJoystick, (knobX - padX) / padRadius, -(knobY - padY) / padRadius)
            }
            start()
        }
    }
}
