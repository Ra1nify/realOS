package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Keyboard live material reference: Bugdroid robot (Compose main path).
 *
 * Placed at the bottom of the page content with blank space below; the keyboard
 * floats over it while blur / mask / corner sliders change.
 *
 * Color: Bugdroid green by default; with [animate] the body hue rotates at
 * constant speed ([AndroidBotSpec.HUE_PERIOD_MS] per turn).
 *
 * The hue is read only inside the [Canvas] draw lambda: each frame invalidates the
 * **draw phase** only, no recomposition. The robot is a 160dp solid block.
 *
 * View fallback path: `CouiComponentFactory.createAndroidBotView`; both share [AndroidBotSpec].
 */
@Composable
fun AndroidBotPreview(
    modifier: Modifier = Modifier,
    sizeDp: Int = AndroidBotSpec.SIZE_DP,
    animate: Boolean = true,
) {
    val hueState: State<Float>? = if (animate) {
        rememberInfiniteTransition(label = "androidBotHue").animateFloat(
            initialValue = AndroidBotSpec.BASE_HUE,
            // 360° and 0° are the same color; the Restart jump is invisible in the hue
            targetValue = AndroidBotSpec.BASE_HUE + 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(AndroidBotSpec.HUE_PERIOD_MS.toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "androidBotHueValue",
        )
    } else {
        null
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(sizeDp.dp)
    ) {
        drawBugdroid(hueState?.value ?: AndroidBotSpec.BASE_HUE)
    }
}

/**
 * Draws Bugdroid with the geometry of the original View implementation (both stay in sync).
 */
private fun DrawScope.drawBugdroid(hue: Float) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val cx = w / 2f
    val cy = h / 2f
    val s = min(w, h) * 0.72f

    val bodyColor = Color.hsv(
        hue = hue.mod(360f),
        saturation = AndroidBotSpec.SATURATION,
        value = AndroidBotSpec.VALUE,
    )

    val headR = s * 0.28f
    val gap = s * 0.04f
    val bodyW = s * 0.56f
    val bodyH = s * 0.44f
    val armW = s * 0.11f
    val armH = s * 0.36f
    val legW = s * 0.11f
    val legH = s * 0.20f
    val antennaW = s * 0.026f
    val headCenterY = cy - s * 0.15f

    // 1. Antennae (two, round caps, slanted out from the head arc ends)
    val antLen = s * 0.12f
    val antDx = antLen * 0.5f
    val antDy = antLen * 0.866f
    val antBaseY = headCenterY - headR * 0.866f
    drawLine(
        color = bodyColor,
        start = Offset(cx - headR * 0.5f, antBaseY),
        end = Offset(cx - headR * 0.5f - antDx, antBaseY - antDy),
        strokeWidth = antennaW,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = bodyColor,
        start = Offset(cx + headR * 0.5f, antBaseY),
        end = Offset(cx + headR * 0.5f + antDx, antBaseY - antDy),
        strokeWidth = antennaW,
        cap = StrokeCap.Round,
    )

    // 2. Head (upper half circle; useCenter makes the bottom edge straight).
    // The head is drawn into an offscreen layer so the eye discs can be cleared;
    // the page background then shows through as real cut-outs.
    val eyeR = headR * AndroidBotSpec.EYE_RADIUS_RATIO
    val eyeOffset = headR * AndroidBotSpec.EYE_OFFSET_RATIO
    val eyeY = headCenterY + headR * AndroidBotSpec.EYE_VERTICAL_RATIO
    val headBounds = Rect(
        offset = Offset(cx - headR, headCenterY - headR),
        size = Size(headR * 2f, headR * 2f),
    )
    drawContext.canvas.saveLayer(headBounds, Paint())
    drawArc(
        color = bodyColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = headBounds.topLeft,
        size = headBounds.size,
    )
    drawCircle(
        color = Color.Black,
        radius = eyeR,
        center = Offset(cx - eyeOffset, eyeY),
        blendMode = BlendMode.Clear,
    )
    drawCircle(
        color = Color.Black,
        radius = eyeR,
        center = Offset(cx + eyeOffset, eyeY),
        blendMode = BlendMode.Clear,
    )
    drawContext.canvas.restore()

    // 3. Body (rounded rect plus a square rect filling the top two corners)
    val bodyTop = headCenterY + gap
    val corner = s * 0.08f
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(cx - bodyW / 2f, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(corner, corner),
    )
    drawRect(
        color = bodyColor,
        topLeft = Offset(cx - bodyW / 2f, bodyTop),
        size = Size(bodyW, corner),
    )

    // 4. Arms
    val armOffset = bodyW / 2f + gap + armW / 2f
    val armRadius = CornerRadius(armW / 2f, armW / 2f)
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(cx - armOffset - armW / 2f, bodyTop),
        size = Size(armW, armH),
        cornerRadius = armRadius,
    )
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(cx + armOffset - armW / 2f, bodyTop),
        size = Size(armW, armH),
        cornerRadius = armRadius,
    )

    // 5. Legs
    val legTop = bodyTop + bodyH - corner
    val legOffset = bodyW * 0.22f
    val legRadius = CornerRadius(legW / 2f, legW / 2f)
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(cx - legOffset - legW / 2f, legTop),
        size = Size(legW, legH + corner),
        cornerRadius = legRadius,
    )
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(cx + legOffset - legW / 2f, legTop),
        size = Size(legW, legH + corner),
        cornerRadius = legRadius,
    )
}
