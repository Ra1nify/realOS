package io.github.ra1nify.realos.ui.theme

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import io.github.ra1nify.realos.root.RootManager
import io.github.ra1nify.realos.root.RootState
import org.json.JSONObject

/**
 * ColorOS system primary/accent color provider.
 *
 * Resolution order:
 * 1. `Settings.Secure` `theme_customization_overlay_packages` through `ContentResolver`.
 * 2. `RootManager` execution when `ContentResolver` is blocked.
 * 3. Android 12+ (API 31+) Monet resource `android.R.color.system_accent1_500`.
 * 4. ColorOS Tech Blue (light #007AFF, dark #3388FF).
 *
 * Results live in memory with a 5-second TTL.
 */
object SystemThemeColor {

    private const val SETTINGS_THEME_KEY = "theme_customization_overlay_packages"
    private const val KEY_ACCENT_COLOR = "android.theme.customization.accent_color"
    private const val KEY_SYSTEM_PALETTE = "android.theme.customization.system_palette"

    val DEFAULT_BLUE_LIGHT = Color(0xFF007AFF)
    val DEFAULT_BLUE_DARK = Color(0xFF3388FF)

    @Volatile
    private var cachedRawColor: Color? = null
    @Volatile
    private var lastQueryTime: Long = 0L

    /**
     * Whether a lookup has run, successful or not.
     *
     * Null results stay cached within the TTL.
     */
    @Volatile
    private var hasQueried: Boolean = false
    private const val CACHE_TTL_MS = 5000L

    /**
     * Returns the system accent color.
     *
     * @param context Host or app context.
     * @param isDark Whether dark mode is active.
     * @return Accent color, or null when no source resolves one.
     */
    fun get(context: Context, isDark: Boolean = false): Color? {
        val raw = getRawAccentColor(context) ?: return null
        return if (isDark) {
            adjustForDarkMode(raw)
        } else {
            raw
        }
    }

    /**
     * Returns the accent color, or the ColorOS blue default.
     */
    fun getOrDefault(context: Context, isDark: Boolean = false): Color {
        return get(context, isDark) ?: if (isDark) DEFAULT_BLUE_DARK else DEFAULT_BLUE_LIGHT
    }

    /**
     * Clears the cached color and lookup state.
     */
    fun invalidateCache() {
        cachedRawColor = null
        lastQueryTime = 0L
        hasQueried = false
    }

    private fun getRawAccentColor(context: Context): Color? {
        val now = System.currentTimeMillis()
        if (hasQueried && (now - lastQueryTime) < CACHE_TTL_MS) {
            // Reuses null results within the TTL.
            return cachedRawColor
        }

        val resolved = extractFromContentResolver(context)
            ?: extractFromRoot(context)
            ?: extractFromSystemResources(context)

        cachedRawColor = resolved
        lastQueryTime = now
        hasQueried = true
        return resolved
    }

    private fun extractFromContentResolver(context: Context): Color? {
        return runCatching {
            val jsonStr = Settings.Secure.getString(context.contentResolver, SETTINGS_THEME_KEY)
            parseColorFromJson(jsonStr)
        }.getOrNull()
    }

    private fun extractFromRoot(context: Context): Color? {
        return runCatching {
            if (RootManager.rootInfo.value.state != RootState.GRANTED) {
                return null
            }
            val res = RootManager.suExecRaw("settings get secure $SETTINGS_THEME_KEY", 1000L)
            if (res.isSuccess && res.output.isNotBlank()) {
                parseColorFromJson(res.output)
            } else null
        }.getOrNull()
    }

    private fun extractFromSystemResources(context: Context): Color? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return runCatching {
            val resId = context.resources.getIdentifier("system_accent1_500", "color", "android")
            if (resId != 0) {
                val colorInt = context.getColor(resId)
                if (colorInt != 0) Color(colorInt) else null
            } else null
        }.getOrNull()
    }

    private fun parseColorFromJson(jsonStr: String?): Color? {
        if (jsonStr.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(jsonStr)
            val accentHex = obj.optString(KEY_ACCENT_COLOR).takeIf { it.isNotBlank() }
                ?: obj.optString(KEY_SYSTEM_PALETTE).takeIf { it.isNotBlank() }
                ?: return null
            parseHexColor(accentHex)
        }.getOrNull()
    }

    private fun parseHexColor(raw: String): Color? {
        val clean = raw.trim().removePrefix("#").removePrefix("_")
        val argb = runCatching {
            when (clean.length) {
                6 -> (0xFF000000.toLong() or clean.toLong(16)).toInt()
                8 -> clean.toLong(16).toInt()
                else -> null
            }
        }.getOrNull() ?: return null
        return Color(argb)
    }

    /**
     * Raises the HSV value to at least 0.85.
     */
    private fun adjustForDarkMode(color: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        if (hsv[2] < 0.85f) {
            hsv[2] = 0.85f
            val alpha = android.graphics.Color.alpha(color.toArgb())
            return Color(android.graphics.Color.HSVToColor(alpha, hsv))
        }
        return color
    }
}

/**
 * Remembers the system accent color in composition.
 */
@Composable
fun rememberSystemThemeColor(isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme()): Color? {
    val context = LocalContext.current
    return remember(isDark) {
        SystemThemeColor.get(context, isDark)
    }
}
