package io.github.ra1nify.realos.xposed.hooks.navigation.pointer

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import io.github.ra1nify.realos.navigation.GestureAppearance
import kotlin.math.abs

/**
 * Multi-style renderer for the dynamic side-swipe back gesture pointer.
 */
object SideGestureDynamicPointerRenderer {

    private const val MAX_DOTS = 32

    /**
     * Per-thread drawing scratch.
     *
     * [render] runs inside the SystemUI side pointer `onDraw` and can nest across
     * roots and displays. Paint, Path and FloatArray state is stored per thread.
     */
    private class Scratch {
        val pointsBuffer = FloatArray(MAX_DOTS * 2)
        val matrixPts = FloatArray(4)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val vectorPath = Path()
    }

    private val scratchLocal = ThreadLocal.withInitial { Scratch() }

    private val pointsBuffer: FloatArray get() = scratchLocal.get().pointsBuffer
    private val matrixPts: FloatArray get() = scratchLocal.get().matrixPts
    private val dotPaint: Paint get() = scratchLocal.get().dotPaint
    private val vectorPaint: Paint get() = scratchLocal.get().vectorPaint
    private val vectorPath: Path get() = scratchLocal.get().vectorPath


    fun render(
        canvas: Canvas,
        backIconMatrix: Matrix?,
        pullDistancePx: Float,
        isLeft: Boolean,
        reverseLeft: Boolean,
        density: Float,
        color: Int,
        alpha: Int,
        sizeDp: Float,
        style: Int = GestureAppearance.DYNAMIC_STYLE_DOT_MATRIX_5
    ) {
        if (backIconMatrix == null) return

        val progress = computeStretchProgress(pullDistancePx, density)

        val iconSizePx = 18f * density
        val halfIcon = iconSizePx * 0.5f
        matrixPts[0] = halfIcon
        matrixPts[1] = halfIcon
        backIconMatrix.mapPoints(matrixPts)
        val centerX = matrixPts[0]
        val centerY = matrixPts[1]

        val heightPx = sizeDp * density
        val halfHeight = heightPx * 0.5f

        val pointingRight = isLeft && reverseLeft
        val directionSign = if (pointingRight) 1.0f else -1.0f

        when (style) {
            GestureAppearance.DYNAMIC_STYLE_DOT_MATRIX_5 -> {
                renderDotMatrix(canvas, centerX, centerY, heightPx, halfHeight, directionSign, progress, density, color, alpha, dotCount = 5)
            }
            GestureAppearance.DYNAMIC_STYLE_DOT_FLOW -> {
                renderDotFlow(canvas, centerX, centerY, heightPx, halfHeight, directionSign, progress, density, color, alpha)
            }
            GestureAppearance.DYNAMIC_STYLE_ELASTIC_CHEVRON -> {
                renderElasticChevron(canvas, centerX, centerY, heightPx, halfHeight, directionSign, progress, density, color, alpha, sizeDp)
            }
            GestureAppearance.DYNAMIC_STYLE_PULSE_BARS -> {
                renderPulseBars(canvas, centerX, centerY, heightPx, halfHeight, directionSign, progress, density, color, alpha)
            }
            GestureAppearance.DYNAMIC_STYLE_BOWSTRING -> {
                renderBowstring(canvas, centerX, centerY, heightPx, halfHeight, directionSign, progress, density, color, alpha, sizeDp)
            }
            else -> {
                renderDotMatrix(canvas, centerX, centerY, heightPx, halfHeight, directionSign, progress, density, color, alpha, dotCount = 5)
            }
        }
    }

    private fun renderDotMatrix(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        heightPx: Float,
        halfHeight: Float,
        directionSign: Float,
        progress: Float,
        density: Float,
        color: Int,
        alpha: Int,
        dotCount: Int
    ) {
        val safeCount = dotCount.coerceIn(2, MAX_DOTS)
        val stepY = heightPx / (safeCount - 1).toFloat()
        val dotRadius = (stepY * 0.35f).coerceIn(1.5f * density, 4.0f * density)
        val maxSpread = heightPx * 0.55f
        val centerIndex = (safeCount - 1) / 2.0f

        for (i in 0 until safeCount) {
            val u = (i - centerIndex) / centerIndex
            val kernel = (1.0f - abs(u)).coerceIn(0.0f, 1.0f)
            val yOffset = -halfHeight + i * stepY
            val xOffset = directionSign * (maxSpread * kernel * progress)
            pointsBuffer[i * 2] = centerX + xOffset
            pointsBuffer[i * 2 + 1] = centerY + yOffset
        }

        dotPaint.color = color
        dotPaint.alpha = alpha.coerceIn(0, 255)

        for (i in 0 until safeCount) {
            val px = pointsBuffer[i * 2]
            val py = pointsBuffer[i * 2 + 1]
            canvas.drawCircle(px, py, dotRadius, dotPaint)
        }
    }

    private fun renderDotFlow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        heightPx: Float,
        halfHeight: Float,
        directionSign: Float,
        progress: Float,
        density: Float,
        color: Int,
        alpha: Int
    ) {
        val dotCount = 7
        val stepY = heightPx / (dotCount - 1).toFloat()
        val baseRadius = (stepY * 0.38f).coerceIn(1.4f * density, 3.8f * density)
        val maxSpread = heightPx * 0.55f
        val centerIndex = 3.0f

        dotPaint.color = color

        for (i in 0 until dotCount) {
            val u = (i - centerIndex) / centerIndex
            val absU = abs(u)
            val kernel = (1.0f - u * u).coerceIn(0.0f, 1.0f)
            val yOffset = -halfHeight + i * stepY
            val xOffset = directionSign * (maxSpread * kernel * progress)

            val px = centerX + xOffset
            val py = centerY + yOffset

            val pointRadius = baseRadius * (0.60f + 0.55f * (1.0f - absU))
            val pointAlpha = (alpha * (0.35f + 0.65f * (1.0f - absU))).toInt().coerceIn(0, 255)

            dotPaint.alpha = pointAlpha
            canvas.drawCircle(px, py, pointRadius, dotPaint)
        }
    }

    private fun renderElasticChevron(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        heightPx: Float,
        halfHeight: Float,
        directionSign: Float,
        progress: Float,
        density: Float,
        color: Int,
        alpha: Int,
        sizeDp: Float
    ) {
        vectorPaint.color = color
        vectorPaint.alpha = alpha.coerceIn(0, 255)
        vectorPaint.strokeWidth = (sizeDp * 0.16f * density).coerceIn(2.2f * density, 5.5f * density)

        val maxSpread = heightPx * 0.50f
        val vertexX = centerX + directionSign * (maxSpread * progress)

        val topX = centerX
        val topY = centerY - halfHeight
        val bottomX = centerX
        val bottomY = centerY + halfHeight

        vectorPath.rewind()
        vectorPath.moveTo(topX, topY)
        vectorPath.lineTo(vertexX, centerY)
        vectorPath.lineTo(bottomX, bottomY)

        canvas.drawPath(vectorPath, vectorPaint)
    }

    private fun renderPulseBars(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        heightPx: Float,
        halfHeight: Float,
        directionSign: Float,
        progress: Float,
        density: Float,
        color: Int,
        alpha: Int
    ) {
        val barCount = 5
        val stepY = heightPx / (barCount - 1).toFloat()
        val centerIndex = 2.0f

        vectorPaint.color = color
        vectorPaint.alpha = alpha.coerceIn(0, 255)
        vectorPaint.strokeWidth = (stepY * 0.42f).coerceIn(1.8f * density, 4.5f * density)

        val baseLen = heightPx * 0.20f
        val maxExtend = heightPx * 0.50f

        for (i in 0 until barCount) {
            val u = (i - centerIndex) / centerIndex
            val kernel = (1.0f - abs(u)).coerceIn(0.0f, 1.0f)
            val y = centerY - halfHeight + i * stepY
            val len = baseLen + maxExtend * kernel * progress

            val startX = centerX
            val endX = centerX + directionSign * len
            canvas.drawLine(startX, y, endX, y, vectorPaint)
        }
    }

    private fun renderBowstring(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        heightPx: Float,
        halfHeight: Float,
        directionSign: Float,
        progress: Float,
        density: Float,
        color: Int,
        alpha: Int,
        sizeDp: Float
    ) {
        vectorPaint.color = color
        vectorPaint.alpha = alpha.coerceIn(0, 255)
        vectorPaint.strokeWidth = (sizeDp * 0.14f * density).coerceIn(2.0f * density, 5.0f * density)

        val maxSpread = heightPx * 0.70f
        val ctrlX = centerX + directionSign * (maxSpread * progress)

        val topX = centerX
        val topY = centerY - halfHeight
        val bottomX = centerX
        val bottomY = centerY + halfHeight

        vectorPath.rewind()
        vectorPath.moveTo(topX, topY)
        vectorPath.quadTo(ctrlX, centerY, bottomX, bottomY)

        canvas.drawPath(vectorPath, vectorPaint)
    }

    private fun computeStretchProgress(
        pullDistancePx: Float,
        density: Float
    ): Float {
        val targetDistancePx = (45f * density).coerceAtLeast(1f)
        val ratio = (pullDistancePx / targetDistancePx).coerceIn(0.0f, 1.0f)
        return ratio * ratio * (3.0f - 2.0f * ratio)
    }
}
