package io.github.ra1nify.realos.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import org.json.JSONObject
import io.github.ra1nify.realos.navigation.GestureAppearance
import io.github.ra1nify.realos.navigation.ArrowIcon
import io.github.ra1nify.realos.navigation.GestureAppearanceCodec
import io.github.ra1nify.realos.navigation.drawArrowIcon

/**
 * Arrow DIY: color / opacity / size.
 */
data class ArrowDiy(
    val color: Int = Color.WHITE,
    val alpha: Int = 255,
    val sizeDp: Float = 18f
)

/**
 * Shadow DIY (bezier water-drop fill).
 */
data class ShadowDiy(
    val color: Int = GestureAppearance.DEFAULT_SHADOW_COLOR,
    val alpha: Int = GestureAppearance.DEFAULT_SHADOW_ALPHA
)

/**
 * Stroke DIY.
 */
data class StrokeDiy(
    val color: Int = Color.WHITE,
    val alpha: Int = GestureAppearance.DEFAULT_STROKE_ALPHA,
    val widthDp: Float = 0.5f
)

/**
 * Handle bar mask DIY: separate light/dark color and opacity values.
 */
data class HandleDiy(
    val color: Int = Color.WHITE,
    val alpha: Int = 255
)

/**
 * Complete appearance set.
 */
data class AppearanceDetail(
    val arrow: ArrowDiy = ArrowDiy(),
    val shadow: ShadowDiy = ShadowDiy(),
    val stroke: StrokeDiy = StrokeDiy(),
    val handle: HandleDiy = HandleDiy()
)

/**
 * Back gesture appearance configuration.
 */
data class GestureAppearance(
    val reverseLeft: Boolean = false,
    val arrowIconKey: String = ArrowIcon.SYSTEM.key,
    val arrowExperienceMode: Int = ARROW_EXPERIENCE_STOCK,
    val dynamicPointerStyle: Int = DYNAMIC_STYLE_DOT_MATRIX_5,
    val arrowSizeDp: Float = 18f,
    val arrowDiyEnabled: Boolean = false,
    val shadowDiyEnabled: Boolean = false,
    val strokeDiyEnabled: Boolean = false,
    val handleDiyEnabled: Boolean = false,
    val handleLengthScale: Int = 100,
    val day: AppearanceDetail = AppearanceDetail(),
    val night: AppearanceDetail = AppearanceDetail(
        arrow = ArrowDiy(color = Color.BLACK),
        shadow = ShadowDiy(color = DEFAULT_NIGHT_SHADOW_COLOR),
        stroke = StrokeDiy(color = Color.BLACK, alpha = 40),
        handle = HandleDiy(color = Color.BLACK, alpha = 180)
    )
) {
    val anyDiyEnabled: Boolean
        get() = reverseLeft || arrowDiyEnabled || shadowDiyEnabled || strokeDiyEnabled || handleDiyEnabled

    fun detailFor(isNight: Boolean): AppearanceDetail {
        val base = if (isNight) night else day
        return base.copy(arrow = base.arrow.copy(sizeDp = arrowSizeDp))
    }

    fun normalized(): GestureAppearance {
        val clampedSize = arrowSizeDp.coerceIn(8f, 48f)
        val dayDefault = AppearanceDetail(arrow = ArrowDiy(sizeDp = clampedSize))
        val nightDefault = GestureAppearance().night.let {
            it.copy(arrow = it.arrow.copy(sizeDp = clampedSize))
        }
        return copy(
            arrowSizeDp = clampedSize,
            handleLengthScale = if (handleDiyEnabled) handleLengthScale.coerceIn(50, 200) else 100,
            day = AppearanceDetail(
                arrow = if (arrowDiyEnabled) day.arrow.copy(sizeDp = clampedSize) else dayDefault.arrow,
                shadow = if (shadowDiyEnabled) day.shadow else dayDefault.shadow,
                stroke = if (strokeDiyEnabled) day.stroke else dayDefault.stroke,
                handle = if (handleDiyEnabled) day.handle else dayDefault.handle
            ),
            night = AppearanceDetail(
                arrow = if (arrowDiyEnabled) night.arrow.copy(sizeDp = clampedSize) else nightDefault.arrow,
                shadow = if (shadowDiyEnabled) night.shadow else nightDefault.shadow,
                stroke = if (strokeDiyEnabled) night.stroke else nightDefault.stroke,
                handle = if (handleDiyEnabled) night.handle else nightDefault.handle
            )
        )
    }

    companion object {
        const val KEY_APPEARANCE = "systemui_gesture_back_appearance"

        const val ARROW_EXPERIENCE_STOCK = 0
        const val ARROW_EXPERIENCE_DYNAMIC = 1

        const val DYNAMIC_STYLE_DOT_MATRIX_5 = 0
        const val DYNAMIC_STYLE_DOT_FLOW = 1
        const val DYNAMIC_STYLE_ELASTIC_CHEVRON = 2
        const val DYNAMIC_STYLE_PULSE_BARS = 3
        const val DYNAMIC_STYLE_BOWSTRING = 4

        const val DEFAULT_SHADOW_COLOR = 0xFF2F2F2F.toInt()
        const val DEFAULT_SHADOW_ALPHA = 191
        const val DEFAULT_STROKE_ALPHA = 77
        const val DEFAULT_NIGHT_SHADOW_COLOR = 0xFFE8E8E8.toInt()
    }
}

object GestureAppearanceCodec {
    private const val VERSION = 8

    fun toJson(a: GestureAppearance): String = runCatching {
        JSONObject().apply {
            put("v", VERSION)
            put("reverseLeft", a.reverseLeft)
            put("arrowIconKey", a.arrowIconKey)
            put("arrowExperienceMode", a.arrowExperienceMode)
            put("dynamicPointerStyle", a.dynamicPointerStyle)
            put("arrowSizeDp", a.arrowSizeDp.toDouble())
            put("arrowDiyEnabled", a.arrowDiyEnabled)
            put("shadowDiyEnabled", a.shadowDiyEnabled)
            put("strokeDiyEnabled", a.strokeDiyEnabled)
            put("handleDiyEnabled", a.handleDiyEnabled)
            put("handleLengthScale", a.handleLengthScale)
            put("day", detailToJson(a.day))
            put("night", detailToJson(a.night))
        }.toString()
    }.getOrDefault("")

    fun fromJson(s: String?): GestureAppearance {
        if (s.isNullOrBlank()) return GestureAppearance()
        return runCatching {
            val o = JSONObject(s)
            val legacyEnabled = o.optBoolean("enabled", false)
            val legacySize = o.optJSONObject("day")?.optJSONObject("arrow")?.optDouble("sizeDp", 18.0) ?: 18.0
            val sizeDp = o.optDouble("arrowSizeDp", legacySize).toFloat().coerceIn(8f, 48f)
            GestureAppearance(
                reverseLeft = o.optBoolean("reverseLeft", false),
                arrowIconKey = ArrowIcon.fromKey(o.optString("arrowIconKey", "")).key,
                arrowExperienceMode = o.optInt("arrowExperienceMode", GestureAppearance.ARROW_EXPERIENCE_STOCK),
                dynamicPointerStyle = o.optInt("dynamicPointerStyle", GestureAppearance.DYNAMIC_STYLE_DOT_MATRIX_5),
                arrowSizeDp = sizeDp,
                arrowDiyEnabled = o.optBoolean("arrowDiyEnabled", legacyEnabled),
                shadowDiyEnabled = o.optBoolean("shadowDiyEnabled", legacyEnabled),
                strokeDiyEnabled = o.optBoolean("strokeDiyEnabled", legacyEnabled),
                handleDiyEnabled = o.optBoolean("handleDiyEnabled", o.optBoolean("handleMaskDiyEnabled", false)),
                handleLengthScale = o.optInt("handleLengthScale", 100).coerceIn(50, 200),
                day = detailFromJson(o.optJSONObject("day"), AppearanceDetail()),
                night = detailFromJson(o.optJSONObject("night"), GestureAppearance().night)
            ).normalized()
        }.getOrDefault(GestureAppearance())
    }

    private fun detailToJson(d: AppearanceDetail): JSONObject = JSONObject().apply {
        put("arrow", JSONObject().apply {
            put("color", d.arrow.color)
            put("alpha", d.arrow.alpha)
            put("sizeDp", d.arrow.sizeDp.toDouble())
        })
        put("shadow", JSONObject().apply {
            put("color", d.shadow.color)
            put("alpha", d.shadow.alpha)
        })
        put("stroke", JSONObject().apply {
            put("color", d.stroke.color)
            put("alpha", d.stroke.alpha)
            put("widthDp", d.stroke.widthDp.toDouble())
        })
        put("handle", JSONObject().apply {
            put("color", d.handle.color)
            put("alpha", d.handle.alpha)
        })
    }

    private fun detailFromJson(o: JSONObject?, def: AppearanceDetail): AppearanceDetail {
        if (o == null) return def
        val h = o.optJSONObject("handle")
        val handleDiy = HandleDiy(
            color = h?.optInt("color", def.handle.color) ?: def.handle.color,
            alpha = (h?.optInt("alpha", def.handle.alpha) ?: def.handle.alpha).coerceIn(0, 255)
        )
        if (o.has("arrow") || o.has("shadow") || o.has("stroke") || o.has("handle")) {
            val a = o.optJSONObject("arrow")
            val s = o.optJSONObject("shadow")
            val t = o.optJSONObject("stroke")
            return AppearanceDetail(
                arrow = ArrowDiy(
                    color = a?.optInt("color", def.arrow.color) ?: def.arrow.color,
                    alpha = (a?.optInt("alpha", def.arrow.alpha) ?: def.arrow.alpha).coerceIn(0, 255),
                    sizeDp = (a?.optDouble("sizeDp", def.arrow.sizeDp.toDouble()) ?: def.arrow.sizeDp.toDouble())
                        .toFloat().coerceIn(8f, 48f)
                ),
                shadow = ShadowDiy(
                    color = s?.optInt("color", def.shadow.color) ?: def.shadow.color,
                    alpha = (s?.optInt("alpha", def.shadow.alpha) ?: def.shadow.alpha).coerceIn(0, 255)
                ),
                stroke = StrokeDiy(
                    color = t?.optInt("color", def.stroke.color) ?: def.stroke.color,
                    alpha = (t?.optInt("alpha", def.stroke.alpha) ?: def.stroke.alpha).coerceIn(0, 255),
                    widthDp = (t?.optDouble("widthDp", def.stroke.widthDp.toDouble()) ?: def.stroke.widthDp.toDouble())
                        .toFloat().coerceIn(0f, 4f)
                ),
                handle = handleDiy
            )
        }
        return AppearanceDetail(
            arrow = ArrowDiy(
                color = o.optInt("arrowColor", def.arrow.color),
                alpha = o.optInt("arrowAlpha", def.arrow.alpha).coerceIn(0, 255),
                sizeDp = o.optDouble("arrowSizeDp", def.arrow.sizeDp.toDouble()).toFloat().coerceIn(8f, 48f)
            ),
            shadow = ShadowDiy(
                color = o.optInt("shadowColor", def.shadow.color),
                alpha = o.optInt("shadowAlpha", def.shadow.alpha).coerceIn(0, 255)
            ),
            stroke = StrokeDiy(
                color = o.optInt("strokeColor", def.stroke.color),
                alpha = o.optInt("strokeAlpha", def.stroke.alpha).coerceIn(0, 255),
                widthDp = o.optDouble("strokeWidthDp", def.stroke.widthDp.toDouble()).toFloat().coerceIn(0f, 4f)
            )
        )
    }
}

fun buildArrowBitmap(
    sizePx: Int,
    color: Int,
    alpha: Int,
    mirrored: Boolean,
    contentSizePx: Int = sizePx,
    icon: ArrowIcon = ArrowIcon.ROUND
): Bitmap {
    val n = sizePx.coerceAtLeast(8)
    val base = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    drawArrowIcon(Canvas(base), n, contentSizePx, icon, color, alpha)
    return if (mirrored) mirrorHorizontally(base) else base
}

fun buildArrowBitmapFrom(
    src: Bitmap,
    sizePx: Int,
    contentSizePx: Int,
    mirrored: Boolean
): Bitmap {
    val n = sizePx.coerceAtLeast(8)
    val content = contentSizePx.coerceIn(4, n)
    val base = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(base)
    val scale = minOf(content.toFloat() / src.width, content.toFloat() / src.height)
    val w = src.width * scale
    val h = src.height * scale
    val left = (n - w) / 2f
    val top = (n - h) / 2f
    val dst = android.graphics.RectF(left, top, left + w, top + h)
    canvas.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    return if (mirrored) mirrorHorizontally(base) else base
}

internal fun mirrorHorizontally(src: Bitmap): Bitmap {
    val n = src.width
    val out = Bitmap.createBitmap(n, src.height, Bitmap.Config.ARGB_8888)
    val m = Matrix().apply { preScale(-1f, 1f, n / 2f, src.height / 2f) }
    Canvas(out).drawBitmap(src, m, null)
    src.recycle()
    return out
}
