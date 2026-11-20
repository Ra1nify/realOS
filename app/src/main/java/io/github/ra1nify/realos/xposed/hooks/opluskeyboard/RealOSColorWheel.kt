package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Native custom Color Wheel View for ColorOS injected Workshop dialog.
 * Reconstructed 1:1 with pure Canvas rendering.
 */
class RealOSColorWheel(
    context: Context,
    initialColor: Int = 0xFF6750A4.toInt()
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hueColors = IntArray(361) { i ->
        android.graphics.Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
    }

    private var hue = 0f
    private var sat = 1f
    private var bri = 1f
    private var alpha = 0xFF

    private var listener: ((Int) -> Unit)? = null
    fun setOnColorChanged(l: (Int) -> Unit) { listener = l }

    private var suppressCallback = false

    fun setSuppressCallback(suppress: Boolean) { suppressCallback = suppress }

    init {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor, hsv)
        hue = hsv[0]; sat = hsv[1]; bri = hsv[2]
        alpha = (initialColor ushr 24) and 0xFF
    }

    fun currentColor(): Int = (alpha shl 24) or (android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bri)) and 0xFFFFFF)

    fun setColor(argb: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        if (hsv[1] > 0.001f && hsv[2] > 0.001f) {
            hue = hsv[0]
        }
        sat = hsv[1]
        bri = hsv[2]
        alpha = (argb ushr 24) and 0xFF
        invalidate()
        if (!suppressCallback) {
            listener?.invoke(currentColor())
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wantSide = (240 * resources.displayMetrics.density).toInt()
        val w = resolveSize(wantSide, widthMeasureSpec)
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            else -> w
        }
        setMeasuredDimension(w, h)
    }

    private val side get() = minOf(width, height)
    private val sideF get() = side.toFloat()
    private val ringWidthF get() = sideF * 0.11f
    private val outerR get() = sideF / 2f
    private val innerR get() = outerR - ringWidthF
    private val squareSide get() = innerR * 1.41421356f * 0.9f
    private val sqLeft get() = (sideF - squareSide) / 2f
    private val sqTop get() = (sideF - squareSide) / 2f
    private val cX get() = sideF / 2f
    private val cY get() = sideF / 2f

    private fun inRing(x: Float, y: Float): Boolean {
        val d = sqrt(((x - cX).toDouble()).pow(2) + ((y - cY).toDouble()).pow(2))
        return d in (innerR * 0.75)..outerR.toDouble()
    }

    private fun inSquare(x: Float, y: Float): Boolean =
        x in sqLeft..(sqLeft + squareSide) && y in sqTop..(sqTop + squareSide)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val center = cX
        val midR = (outerR + innerR) / 2f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ringWidthF
        paint.shader = android.graphics.SweepGradient(center, cY, hueColors, null)
        canvas.drawCircle(center, cY, midR, paint)
        paint.shader = null

        val hRad = Math.toRadians(hue.toDouble())
        val ix = center + midR * cos(hRad).toFloat()
        val iy = cY + midR * sin(hRad).toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * resources.displayMetrics.density
        paint.color = Color.WHITE
        canvas.drawCircle(ix, iy, ringWidthF * 0.48f, paint)
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        canvas.drawCircle(ix, iy, ringWidthF * 0.38f, paint)

        paint.shader = android.graphics.LinearGradient(
            sqLeft, 0f, sqLeft + squareSide, 0f,
            Color.WHITE, android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(sqLeft, sqTop, sqLeft + squareSide, sqTop + squareSide, paint)

        paint.shader = android.graphics.LinearGradient(
            0f, sqTop, 0f, sqTop + squareSide,
            0x00000000, Color.BLACK,
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRect(sqLeft, sqTop, sqLeft + squareSide, sqTop + squareSide, paint)
        paint.shader = null

        val svX = sqLeft + sat * squareSide
        val svY = sqTop + (1f - bri) * squareSide
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * resources.displayMetrics.density
        canvas.drawCircle(svX, svY, 9f * resources.displayMetrics.density, paint)
        paint.color = Color.BLACK
        paint.strokeWidth = resources.displayMetrics.density
        canvas.drawCircle(svX, svY, 9f * resources.displayMetrics.density, paint)
        paint.style = Paint.Style.FILL
        paint.color = currentColor()
        canvas.drawCircle(svX, svY, 6f * resources.displayMetrics.density, paint)
    }

    private var dragZone = 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragZone = when {
                    inRing(event.x, event.y) -> 1
                    inSquare(event.x, event.y) -> 2
                    else -> 0
                }
                if (dragZone != 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateFromTouch(event.x, event.y)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragZone != 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateFromTouch(event.x, event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragZone != 0) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    dragZone = 0
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(x: Float, y: Float) {
        when (dragZone) {
            1 -> {
                hue = (Math.toDegrees(atan2(
                    (y - cY).toDouble(), (x - cX).toDouble()
                )).toFloat() + 360f) % 360f
            }
            2 -> {
                val cx = x.coerceIn(sqLeft, sqLeft + squareSide)
                val cy = y.coerceIn(sqTop, sqTop + squareSide)
                sat = ((cx - sqLeft) / squareSide).coerceIn(0f, 1f)
                bri = (1f - (cy - sqTop) / squareSide).coerceIn(0f, 1f)
            }
            else -> return
        }
        invalidate()
        listener?.invoke(currentColor())
    }
}
