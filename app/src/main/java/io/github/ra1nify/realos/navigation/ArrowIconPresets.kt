package io.github.ra1nify.realos.navigation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import kotlin.math.roundToInt
import io.github.ra1nify.realos.navigation.ArrowIcon
import io.github.ra1nify.realos.navigation.drawArrowIcon

/**
 * Back gesture arrow icon presets. Hand-drawn Path and dot shapes.
 */
enum class ArrowIcon(val key: String) {
    /** Reads the system default dynamically */
    SYSTEM("system"),
    /** Rounded arrow, no stem */
    ROUND("round"),
    /** Rounded arrow with stem */
    ROUND_STEM("round_stem"),
    /** Dot arrow, no stem */
    DOT("dot"),
    /** Dot arrow with stem */
    DOT_STEM("dot_stem"),
    /** Isosceles triangle, 36 degree apex, filled */
    TRIANGLE("triangle"),
    /** Isosceles triangle, 36 degree apex, hollow */
    TRIANGLE_OUTLINE("triangle_outline"),
    /** Custom import (PNG) */
    CUSTOM("custom");

    companion object {
        fun fromKey(key: String?): ArrowIcon = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

private const val TRIANGLE_HEIGHT_RATIO = 1.53884f
private const val DOT_STEP_RATIO = 0.15f
private const val DOT_RADIUS_RATIO = 0.042f

internal fun drawArrowIcon(
    canvas: Canvas,
    size: Int,
    contentSize: Int,
    icon: ArrowIcon,
    color: Int,
    alpha: Int
) {
    val n = size.toFloat()
    val s = contentSize.coerceIn(4, size).toFloat()
    val cx = n / 2f
    val cy = n / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.alpha = alpha.coerceIn(0, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = s * 0.15f
    }

    when (icon) {
        ArrowIcon.TRIANGLE, ArrowIcon.TRIANGLE_OUTLINE -> {
            val h = s * 0.48f
            val w = h * TRIANGLE_HEIGHT_RATIO
            if (icon == ArrowIcon.TRIANGLE_OUTLINE) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.09f
            } else {
                paint.style = Paint.Style.FILL
            }
            val p = Path().apply {
                moveTo(cx - w / 2f, cy)
                lineTo(cx + w / 2f, cy - h / 2f)
                lineTo(cx + w / 2f, cy + h / 2f)
                close()
            }
            canvas.drawPath(p, paint)
        }

        ArrowIcon.DOT, ArrowIcon.DOT_STEM -> {
            paint.style = Paint.Style.FILL
            val path = arrowPath(cx, cy, s, withStem = icon == ArrowIcon.DOT_STEM)
            val measure = PathMeasure(path, false)
            val dotR = s * DOT_RADIUS_RATIO
            val step = s * DOT_STEP_RATIO
            val pos = FloatArray(2)
            do {
                val len = measure.length
                if (len <= 0f) continue
                var count = (len / step).roundToInt().coerceIn(2, 48)
                if (count % 2 != 0) count += 1
                val cell = len / count
                for (k in 0..count) {
                    if (measure.getPosTan(k * cell, pos, null)) {
                        canvas.drawCircle(pos[0], pos[1], dotR, paint)
                    }
                }
            } while (measure.nextContour())
        }

        else -> {
            val path = arrowPath(cx, cy, s, withStem = icon == ArrowIcon.ROUND_STEM)
            canvas.drawPath(path, paint)
        }
    }
}

private fun arrowPath(cx: Float, cy: Float, s: Float, withStem: Boolean): Path {
    val left = cx - s / 2f
    val top = cy - s / 2f
    fun x(f: Float) = left + s * f
    fun y(f: Float) = top + s * f
    return Path().apply {
        if (withStem) {
            moveTo(x(0.76f), y(0.50f))
            lineTo(x(0.26f), y(0.50f))
            moveTo(x(0.50f), y(0.26f))
            lineTo(x(0.26f), y(0.50f))
            lineTo(x(0.50f), y(0.74f))
        } else {
            moveTo(x(0.72f), y(0.26f))
            lineTo(x(0.30f), y(0.50f))
            lineTo(x(0.72f), y(0.74f))
        }
    }
}
