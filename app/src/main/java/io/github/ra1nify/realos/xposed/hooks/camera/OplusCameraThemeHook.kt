package io.github.ra1nify.realos.xposed.hooks.camera

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.provider.Settings
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.util.TypedValue
import android.view.View
import io.github.libxposed.api.XposedModule
import org.json.JSONObject
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * Camera settings page follows system light/dark mode and accent color (minimal verified build).
 *
 * Observed facts:
 * - COUI ships a full `Theme.COUI.{Light|Dark}.{7 colors}` set with matching attributes;
 * - 1100 of the camera's 1775 styles reference `couiColor*`; swapping the theme changes them broadly;
 * - the host never reads system uiMode; 12 settings Activities call `setTheme(Moon)` unconditionally.
 *
 * This hook only selects a theme; it defines no colors:
 * 1. after `setTheme`, overlays one COUI theme family by uiMode + system accent color;
 * 2. replaces hardcoded pure-black backgrounds on settings pages with the theme background;
 * 3. recreates the Activity on uiMode change (the host declares uiMode in configChanges; the system does not recreate them).
 *
 * Applies only to `com.oplus.camera.setting.*`; the viewfinder `com.oplus.camera.Camera` is untouched.
 */
object OplusCameraThemeHook {

    private const val TAG = "RealOS-CameraTheme"

    /** Settings pages only; the viewfinder (com.oplus.camera.Camera) is not included */
    private const val SETTINGS_PACKAGE_PREFIX = "com.oplus.camera.setting."

    /** Screen-off overlay stays pure black; the light theme does not rewrite it */
    private val EXCLUDED_ACTIVITY_SUFFIXES = listOf("ScreenOffActivity")

    /** COUI accent families shipped with the host */
    private val ACCENT_FAMILIES = listOf("Blue", "Green", "Orange", "Purple", "Red", "Skyblue", "Yellow")

    /** Default camera accent family; used when the accent color cannot be read */
    private const val DEFAULT_FAMILY = "Yellow"

    private const val SETTINGS_THEME_KEY = "theme_customization_overlay_packages"
    private const val KEY_ACCENT_COLOR = "android.theme.customization.accent_color"
    private const val KEY_SYSTEM_PALETTE = "android.theme.customization.system_palette"

    /** Static base-color entry for the COUI top-bar gradient mask */
    private const val APP_BAR_BLUR_HELPER_CLASS = "com.coui.appcompat.toolbar.AppBarBlurHelper"

    private const val BLACK = -0x1000000
    private const val FALLBACK_LIGHT_BACKGROUND = 0xFFF2F3F5.toInt()

    private data class ThemedState(val night: Boolean, val background: Int)

    private var module: XposedModule? = null

    @Volatile
    private var enabled = false

    /** Settings pages with the theme applied, plus their current night mode and background color */
    private val themedActivities = WeakHashMap<Activity, ThemedState>()

    /**
     * True system light/dark mode.
     *
     * Host `Camera.onConfigurationChanged` forces uiMode to NIGHT_YES, and Activities share one
     * ResourcesImpl, polluting the settings pages' `resources.configuration` as well. Only the
     * raw configuration captured at the Application layer is read.
     */
    @Volatile
    private var systemNight: Boolean? = null

    fun install(module: XposedModule) {
        this.module = module
        hookSetTheme(module)
        hookBackgroundColor(module)
        hookConfigurationChanged(module)
        hookApplicationConfigChange(module)
        module.log(Log.INFO, TAG, "HookPoint[camera_theme] installed")
    }

    // ── 1. Pick theme ───────────────────────────────────────────────

    private fun hookSetTheme(module: XposedModule) {
        runCatching {
            // Hooks go on Activity.setTheme, not ContextThemeWrapper.setTheme:
            // the latter is a super call inside Activity.setTheme, and the following initializeTheme()
            // re-applies the host's original theme and overwrites the overlay.
            val setTheme = Activity::class.java
                .getDeclaredMethod("setTheme", Int::class.javaPrimitiveType)
            module.hook(setTheme).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                if (activity != null) {
                    runCatching { applyIfEligible(activity) }
                        .onFailure { module.log(Log.WARN, TAG, "applyIfEligible failed", it) }
                }
                result
            }
            module.log(Log.INFO, TAG, "HookPoint[camera_theme_set_theme] installed")
        }.onFailure {
            module.log(Log.WARN, TAG, "HookPoint[camera_theme_set_theme] state=ERROR", it)
        }
    }

    private fun applyIfEligible(activity: Activity) {
        val host = module ?: return
        if (!activity.javaClass.name.startsWith(SETTINGS_PACKAGE_PREFIX)) return
        if (EXCLUDED_ACTIVITY_SUFFIXES.any { activity.javaClass.name.endsWith(it) }) return

        enabled = OplusCameraHostPrefs.isThemeFollowSystemEnabled(activity)
        if (!enabled) {
            themedActivities.remove(activity)
            return
        }

        val night = isNightNow(activity)
        val styleId = resolveOverlayStyle(activity, night)
        if (styleId == 0) {
            host.log(Log.WARN, TAG, "HookPoint[camera_theme_overlay] state=MISSING night=$night")
            return
        }

        val applied = runCatching { activity.theme.applyStyle(styleId, true) }.isSuccess
        if (!applied) {
            host.log(Log.WARN, TAG, "applyStyle failed for ${activity.javaClass.simpleName}")
            return
        }

        // The COUI theme ancestry contains ThemeOverrideBase → Theme.AppCompat.Light with
        // windowActionBar=true / windowNoTitle=false. Applying the whole chain overwrites the host
        // AppNoTitleTheme's false/true, AppCompat then builds a window with an ActionBar, and the host's
        // subsequent setSupportActionBar(toolbar) throws.
        // A NoActionBar theme carrying only these two attributes restores the window flags (no couiColor* touched).
        val noActionBar = resolveNoActionBarStyle(activity, night)
        if (noActionBar != 0) {
            runCatching { activity.theme.applyStyle(noActionBar, true) }
                .onFailure { host.log(Log.WARN, TAG, "applyStyle(NoActionBar) failed", it) }
        }

        val background = resolveBackgroundColor(activity, night)
        applyAppBarGradientBaseColor(activity, background)
        themedActivities[activity] = ThemedState(night, background)
        host.log(
            Log.INFO, TAG,
            "applied ${activity.resources.getResourceEntryName(styleId)} on " +
                "${activity.javaClass.simpleName} night=$night bg=${String.format("#%08X", background)}"
        )
    }

    /** Real light/dark value for the realOS tab to read (host uiMode is rewritten; isSystemInDarkTheme is unusable) */
    fun currentSystemNight(context: Context): Boolean =
        systemNight ?: isNight(context.resources.configuration)

    /**
     * Top-bar gradient mask base color.
     *
     * `createGradientBackground` reads only the static `gradientBlurBgColor` (then the appbar's own
     * background); the resolved theme background is fed in, producing a [background → transparent] gradient.
     */
    private fun applyAppBarGradientBaseColor(activity: Activity, color: Int) {
        val host = module
        runCatching {
            val clazz = activity.classLoader.loadClass(APP_BAR_BLUR_HELPER_CLASS)
            val setter = clazz.getDeclaredMethod("setGradientBlurBgColor", Int::class.javaPrimitiveType)
            setter.invoke(null, color)
            host?.log(
                Log.INFO, TAG,
                "appbar gradient baseColor=${String.format("#%08X", color)} on ${activity.javaClass.simpleName}"
            )
        }.onFailure {
            host?.log(Log.WARN, TAG, "HookPoint[camera_theme_appbar_color] state=ERROR", it)
        }
    }

    /** AppCompat theme containing only windowActionBar=false / windowNoTitle=true */
    private fun resolveNoActionBarStyle(context: Context, night: Boolean): Int {
        val name = if (night) "Theme.AppCompat.NoActionBar" else "Theme.AppCompat.Light.NoActionBar"
        return context.resources.getIdentifier(name, "style", context.packageName)
    }

    private fun isNightNow(activity: Activity): Boolean =
        systemNight ?: isNight(activity.resources.configuration)

    /** Application-layer onConfigurationChanged receives the configuration before the host rewrites it */
    private fun hookApplicationConfigChange(module: XposedModule) {
        runCatching {
            val method = Application::class.java
                .getDeclaredMethod("onConfigurationChanged", Configuration::class.java)
            module.hook(method).intercept { chain ->
                (chain.args.getOrNull(0) as? Configuration)?.let { systemNight = isNight(it) }
                chain.proceed()
            }
            module.log(Log.INFO, TAG, "HookPoint[camera_theme_system_night] installed")
        }.onFailure {
            module.log(Log.WARN, TAG, "HookPoint[camera_theme_system_night] state=ERROR", it)
        }
    }

    /** Records the true light/dark value at process start, before the host rewrites uiMode */
    fun primeSystemNight(app: Application) {
        systemNight = isNight(app.resources.configuration)
    }

    /** Accent family + night mode → the COUI theme shipped with the host */
    private fun resolveOverlayStyle(context: Context, night: Boolean): Int {
        val res = context.resources
        val pkg = context.packageName
        val mode = if (night) "Dark" else "Light"
        val family = pickAccentFamily(context, res, pkg) ?: DEFAULT_FAMILY

        val familyStyle = res.getIdentifier("Theme.COUI.$mode.$family", "style", pkg)
        if (familyStyle != 0) return familyStyle

        return res.getIdentifier("Theme.COUI.$mode", "style", pkg)
    }

    /** System accent color → nearest host accent family (compared by hue) */
    private fun pickAccentFamily(context: Context, res: Resources, pkg: String): String? {
        val accent = systemAccentColor(context) ?: return null
        val accentHue = hueOf(accent)
        var best: String? = null
        var bestDistance = Float.MAX_VALUE
        for (family in ACCENT_FAMILIES) {
            val colorId = res.getIdentifier("coui_color_primary_${family.lowercase()}", "color", pkg)
            if (colorId == 0) continue
            val familyHue = runCatching { hueOf(res.getColor(colorId)) }.getOrNull() ?: continue
            val delta = abs(familyHue - accentHue)
            val distance = min(delta, 360f - delta)
            if (distance < bestDistance) {
                bestDistance = distance
                best = family
            }
        }
        return best
    }

    /** Accent color from the ColorOS theme overlay package, falling back to Monet */
    private fun systemAccentColor(context: Context): Int? {
        val raw = runCatching {
            Settings.Secure.getString(context.contentResolver, SETTINGS_THEME_KEY)
        }.getOrNull()
        parseAccentFromOverlay(raw)?.let { return it }
        return runCatching {
            val id = context.resources.getIdentifier("system_accent1_500", "color", "android")
            if (id != 0) context.getColor(id) else null
        }.getOrNull()
    }

    private fun parseAccentFromOverlay(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            val hex = json.optString(KEY_ACCENT_COLOR).takeIf { it.isNotBlank() }
                ?: json.optString(KEY_SYSTEM_PALETTE).takeIf { it.isNotBlank() }
                ?: return null
            parseHexColor(hex)
        }.getOrNull()
    }

    private fun parseHexColor(raw: String): Int? {
        val clean = raw.trim().removePrefix("#")
        return runCatching {
            when (clean.length) {
                6 -> (0xFF000000L or clean.toLong(16)).toInt()
                8 -> clean.toLong(16).toInt()
                else -> null
            }
        }.getOrNull()
    }

    private fun hueOf(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0]
    }

    private fun isNight(configuration: Configuration): Boolean =
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // ── 2. Hardcoded black background ────────────────────────────────

    /**
     * Settings pages carry several `setBackgroundColor(-16777216)` calls (top bar, list root layout).
     * In light mode they are replaced with the theme background; otherwise the page stays black.
     */
    private fun hookBackgroundColor(module: XposedModule) {
        runCatching {
            val setBackgroundColor = View::class.java
                .getDeclaredMethod("setBackgroundColor", Int::class.javaPrimitiveType)
            module.hook(setBackgroundColor).intercept { chain ->
                if (!enabled) return@intercept chain.proceed()
                val color = chain.args.getOrNull(0) as? Int
                if (color != BLACK) return@intercept chain.proceed()

                val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                val state = activityOf(view)?.let { themedActivities[it] } ?: return@intercept chain.proceed()
                if (state.night) return@intercept chain.proceed()

                chain.proceed(arrayOf<Any?>(state.background))
            }
            module.log(Log.INFO, TAG, "HookPoint[camera_theme_background] installed")
        }.onFailure {
            module.log(Log.WARN, TAG, "HookPoint[camera_theme_background] state=ERROR", it)
        }
    }

    private fun activityOf(view: View): Activity? {
        var context: Context? = view.context
        while (context is android.content.ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

    /** Theme background color: COUI background attribute first, then android:colorBackground */
    private fun resolveBackgroundColor(context: Context, night: Boolean): Int {
        if (night) return BLACK
        val typedValue = TypedValue()
        val res = context.resources

        val couiAttr = res.getIdentifier("couiColorBackground", "attr", context.packageName)
        if (couiAttr != 0 && context.theme.resolveAttribute(couiAttr, typedValue, true)) {
            resolveColor(context, typedValue)?.let { return it }
        }
        if (context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
            resolveColor(context, typedValue)?.let { return it }
        }
        return FALLBACK_LIGHT_BACKGROUND
    }

    private fun resolveColor(context: Context, typedValue: TypedValue): Int? {
        val isColor = typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT
        if (isColor) return typedValue.data
        if (typedValue.resourceId != 0) {
            return runCatching { context.getColor(typedValue.resourceId) }.getOrNull()
        }
        return null
    }

    // ── 3. Recreate on mode change ───────────────────────────────────

    private fun hookConfigurationChanged(module: XposedModule) {
        runCatching {
            val onConfigurationChanged = Activity::class.java
                .getDeclaredMethod("onConfigurationChanged", Configuration::class.java)
            module.hook(onConfigurationChanged).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                val configuration = chain.args.getOrNull(0) as? Configuration
                if (activity != null && configuration != null) {
                    runCatching { recreateIfModeChanged(activity, configuration) }
                        .onFailure { module.log(Log.WARN, TAG, "recreateIfModeChanged failed", it) }
                }
                result
            }
            module.log(Log.INFO, TAG, "HookPoint[camera_theme_config_change] installed")
        }.onFailure {
            module.log(Log.WARN, TAG, "HookPoint[camera_theme_config_change] state=ERROR", it)
        }
    }

    /**
     * Settings Activities declare `uiMode` in configChanges; the system does not recreate them
     * and the theme would stay at the old light/dark mode. This triggers one recreate.
     */
    private fun recreateIfModeChanged(activity: Activity, configuration: Configuration) {
        val state = themedActivities[activity] ?: return
        val night = isNight(configuration)
        if (night == state.night) return

        themedActivities.remove(activity)
        module?.log(
            Log.INFO, TAG,
            "uiMode changed (night=$night), recreating ${activity.javaClass.simpleName}"
        )
        activity.window?.decorView?.post { runCatching { activity.recreate() } }
    }
}
