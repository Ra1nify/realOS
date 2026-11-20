package io.github.ra1nify.realos.opluskeyboard

import android.content.Context
import android.graphics.Color
import android.content.res.Configuration
import java.lang.reflect.Method

/**
 * Host keyboard theme color probe (single implementation).
 *
 * `com.oplus.omes.dmask.util.c` is the obfuscated name of a host built-in library class;
 * the class name is only a discovery entry, acceptance is structural:
 *  - a `static` method taking `(String, <any>)` and returning `int`;
 *  - a probe call with the confirmed keys (`colorHeaderClipText` /
 *    `colorHeaderCandidateWord`) returning a non-zero color.
 * Any unmet condition returns null or falls back to uiMode; no name-based guessing.
 */
object KeyboardThemeColors {

    /** Host theme utility class (obfuscated name, discovery entry only; validity decided structurally). */
    private const val PROVIDER_CLASS = "com.oplus.omes.dmask.util.c"

    /** Host ThemeCompatMode enum (follow system / light / dark). */
    private const val MODE_CLASS = "com.oplus.keyboard.base.theme.enums.ThemeCompatMode"

    private val COLOR_KEYS = arrayOf("colorHeaderClipText", "colorHeaderCandidateWord")

    @Volatile
    private var cachedMethod: Method? = null

    @Volatile
    private var cachedFollowMode: Any? = null

    @Volatile
    private var probeFailed = false

    @Volatile
    private var cachedColor: Int? = null

    @Volatile
    private var cachedAt: Long = 0L

    private const val CACHE_TTL_MS = 5000L

    /** Host theme accent color; null when undetermined. */
    fun resolveAccentColor(context: Context, classLoader: ClassLoader): Int? {
        val now = System.currentTimeMillis()
        if (now - cachedAt < CACHE_TTL_MS) return cachedColor
        val value = probe(classLoader)
        cachedColor = value
        cachedAt = now
        return value
    }

    /** Dark-theme flag; falls back to uiMode when the host probe is unavailable. */
    fun isDarkTheme(context: Context, classLoader: ClassLoader): Boolean {
        val color = resolveAccentColor(context, classLoader) ?: return isSystemNight(context)
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance > 0.45
    }

    /** Secondary text color (accent at reduced alpha) for the suggestion strip subtitle; null on probe failure. */
    fun subtitleColorOf(accent: Int): Int {
        val alpha = (Color.alpha(accent) * 0.65f).toInt().coerceIn(100, 255)
        return Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))
    }

    fun isSystemNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun clearCache() {
        cachedColor = null
        cachedAt = 0L
    }

    private fun probe(classLoader: ClassLoader): Int? {
        if (probeFailed) return null
        val method = cachedMethod ?: resolveMethod(classLoader)?.also { cachedMethod = it }
        if (method == null) {
            probeFailed = true
            return null
        }
        val followMode = cachedFollowMode
        for (key in COLOR_KEYS) {
            val color = runCatching { method.invoke(null, key, followMode) as? Int }.getOrNull()
            if (color != null && color != 0) return color
        }
        return null
    }

    /**
     * Structural check: accepts only the unique method that is static, takes (String, Any)
     * and returns int. Non-unique candidates are dropped.
     */
    private fun resolveMethod(classLoader: ClassLoader): Method? {
        val provider = runCatching { classLoader.loadClass(PROVIDER_CLASS) }.getOrNull() ?: return null
        val candidates = provider.declaredMethods.filter { m ->
            java.lang.reflect.Modifier.isStatic(m.modifiers) &&
                m.parameterTypes.size == 2 &&
                m.parameterTypes[0] == String::class.java &&
                m.returnType == Int::class.javaPrimitiveType
        }
        val method = candidates.singleOrNull() ?: return null
        runCatching { method.isAccessible = true }

        cachedFollowMode = runCatching {
            val modeClass = classLoader.loadClass(MODE_CLASS)
            modeClass.enumConstants?.firstOrNull { (it as Enum<*>).name == "FOLLOW" }
                ?: modeClass.enumConstants?.firstOrNull()
        }.getOrNull()

        return method
    }
}
