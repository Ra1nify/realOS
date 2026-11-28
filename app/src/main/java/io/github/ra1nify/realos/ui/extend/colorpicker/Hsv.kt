// Ported from rmuix-extend colorpicker/Hsv.kt (compose-rmuix-ui, Apache-2.0), realOS-maintained copy.
// Pure math and Compose Color operations; implementation matches apart from the package name.
// The hex helpers were reworked: realOS always shows and accepts `#AARRGGBB`.

package io.github.ra1nify.realos.ui.extend.colorpicker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Hue in degrees (0..360), saturation and value (0..1). */
internal data class Hsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

internal fun colorToHsv(color: Color): Hsv {
    val r = color.red
    val g = color.green
    val b = color.blue
    val highest = max(r, max(g, b))
    val lowest = min(r, min(g, b))
    val delta = highest - lowest
    val hue =
        when {
            delta == 0f -> 0f
            highest == r -> 60f * (((g - b) / delta) % 6f)
            highest == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
    val saturation = if (highest == 0f) 0f else delta / highest
    return Hsv(hue = hue, saturation = saturation, value = highest)
}

internal fun hsvToColor(
    hue: Float,
    saturation: Float,
    value: Float,
    alpha: Float,
): Color {
    val h = (hue % 360f + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val chroma = v * s
    val second = chroma * (1f - abs((h / 60f) % 2f - 1f))
    val offset = v - chroma
    val (r, g, b) =
        when {
            h < 60f -> Triple(chroma, second, 0f)
            h < 120f -> Triple(second, chroma, 0f)
            h < 180f -> Triple(0f, chroma, second)
            h < 240f -> Triple(0f, second, chroma)
            h < 300f -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }
    return Color(red = r + offset, green = g + offset, blue = b + offset, alpha = alpha.coerceIn(0f, 1f))
}

/** `#AARRGGBB`, always eight digits; the alpha byte is never dropped. */
internal fun Color.toHexArgb(): String =
    "#" + toArgb().toUInt().toString(radix = 16).padStart(8, '0').uppercase()

/** Parses `#AARRGGBB`, or `#RRGGBB` with an opaque alpha byte; null when the text is not a color. */
internal fun parseHexArgb(input: String): Color? {
    val clean = input.removePrefix("#").trim()
    if (clean.length != 6 && clean.length != 8) return null
    if (clean.any { it !in '0'..'9' && it !in 'A'..'F' && it !in 'a'..'f' }) return null
    val value = clean.toLongOrNull(radix = 16) ?: return null
    val argb = if (clean.length == 6) value or 0xFF000000L else value
    return Color(argb.toInt())
}
