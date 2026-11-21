package io.github.ra1nify.realos.navigation

import android.content.Context
import android.content.SharedPreferences
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.navigation.GestureAppearance
import io.github.ra1nify.realos.navigation.GestureVibrationConfig
import io.github.ra1nify.realos.navigation.SystemUiConfig
import io.github.ra1nify.realos.navigation.GestureAppearanceCodec
import io.github.ra1nify.realos.navigation.GestureVibrationCodec

/**
 * SystemUI-side configuration reads.
 */
object SystemUiConfig {

    private const val TAG = "RealOS-SysUiConfig"

    private const val PREFS_NAME = "app_config"

    const val KEY_GESTURE_BACK_ENABLED = "systemui_gesture_back_enabled"
    const val KEY_GESTURE_BACK_ICON_ALPHA = "systemui_gesture_back_icon_alpha"
    const val KEY_NAV_BUTTON_CUSTOM_ENABLED = "systemui_nav_button_custom_enabled"
    const val KEY_NAV_BUTTON_PRESET_INDEX = "systemui_nav_button_preset_index"
    const val KEY_NAV_BUTTON_TINT_MODE = "systemui_nav_button_tint_mode"
    const val KEY_NAV_BUTTON_TINT_COLOR = "systemui_nav_button_tint_color"
    const val KEY_NAV_BUTTON_TINT_LIGHT_COLOR = "systemui_nav_button_tint_light_color"
    const val KEY_NAV_BUTTON_TINT_DARK_COLOR = "systemui_nav_button_tint_dark_color"

    const val KEY_NAV_BUTTON_CUSTOM_BACK_BASE64 = "systemui_nav_button_custom_back"
    const val KEY_NAV_BUTTON_CUSTOM_HOME_BASE64 = "systemui_nav_button_custom_home"
    const val KEY_NAV_BUTTON_CUSTOM_RECENT_BASE64 = "systemui_nav_button_custom_recent"

    const val KEY_NAV_BUTTON_GLASS_ENABLED = "systemui_nav_button_glass_enabled"
    const val KEY_NAV_BUTTON_GLASS_OPACITY = "systemui_nav_button_glass_opacity"
    const val KEY_NAV_BUTTON_GLASS_STROKE = "systemui_nav_button_glass_stroke"
    const val KEY_NAV_BUTTON_GLASS_STROKE_FOLLOW_SYSTEM = "systemui_nav_button_glass_stroke_follow_system"
    const val KEY_NAV_BUTTON_GLASS_SPOTLIGHT_ENABLED = "systemui_nav_button_glass_spotlight_enabled"
    const val KEY_NAV_BUTTON_GLASS_LIGHT_COLOR = "systemui_nav_button_glass_light_color"
    const val KEY_NAV_BUTTON_GLASS_LIGHT_ALPHA = "systemui_nav_button_glass_light_alpha"
    const val KEY_NAV_BUTTON_GLASS_DARK_COLOR = "systemui_nav_button_glass_dark_color"
    const val KEY_NAV_BUTTON_GLASS_DARK_ALPHA = "systemui_nav_button_glass_dark_alpha"
    const val KEY_NAV_BUTTON_GLASS_CAPSULE_MODE = "systemui_nav_button_glass_capsule_mode"
    const val KEY_NAV_BUTTON_GLASS_BLUR_RADIUS = "systemui_nav_button_glass_blur_radius"

    const val CAPSULE_MODE_NONE = 0
    const val CAPSULE_MODE_SINGLE = 1
    const val CAPSULE_MODE_TRIPLE = 2
    const val CAPSULE_MODE_PAIR_BACK_HOME = 3
    const val CAPSULE_MODE_PAIR_HOME_RECENT = 4

    const val DEFAULT_GLASS_CAPSULE_MODE = CAPSULE_MODE_NONE
    const val PRESET_INDEX_CUSTOM = 11

    const val ACTION_NAV_BUTTON_CHANGED = "io.github.ra1nify.realos.action.NAV_BUTTON_CHANGED"
    const val LEGACY_ACTION_NAV_BUTTON_CHANGED = "com.luckyfuse.action.NAV_BUTTON_CHANGED"

    const val EXTRA_ENABLED = "extra_enabled"
    const val EXTRA_PRESET_INDEX = "extra_preset_index"
    const val EXTRA_TINT_MODE = "extra_tint_mode"
    const val EXTRA_TINT_COLOR = "extra_tint_color"
    const val EXTRA_TINT_LIGHT_COLOR = "extra_tint_light_color"
    const val EXTRA_TINT_DARK_COLOR = "extra_tint_dark_color"
    const val EXTRA_CUSTOM_BACK = "extra_custom_back"
    const val EXTRA_CUSTOM_HOME = "extra_custom_home"
    const val EXTRA_CUSTOM_RECENT = "extra_custom_recent"
    const val EXTRA_GLASS_ENABLED = "extra_glass_enabled"
    const val EXTRA_GLASS_OPACITY = "extra_glass_opacity"
    const val EXTRA_GLASS_SPOTLIGHT_ENABLED = "extra_glass_spotlight_enabled"
    const val EXTRA_GLASS_LIGHT_COLOR = "extra_glass_light_color"
    const val EXTRA_GLASS_LIGHT_ALPHA = "extra_glass_light_alpha"
    const val EXTRA_GLASS_DARK_COLOR = "extra_glass_dark_color"
    const val EXTRA_GLASS_DARK_ALPHA = "extra_glass_dark_alpha"
    const val EXTRA_GLASS_CAPSULE_MODE = "extra_glass_capsule_mode"

    const val DEFAULT_ENABLED = false
    const val DEFAULT_ALPHA = 255
    const val DEFAULT_PRESET_INDEX = 1
    const val DEFAULT_TINT_MODE = 1
    const val DEFAULT_TINT_COLOR = 0xFFFFFFFF.toInt()
    const val DEFAULT_TINT_LIGHT_COLOR = 0xFF1D1D1F.toInt()
    const val DEFAULT_TINT_DARK_COLOR = 0xFFFFFFFF.toInt()
    const val DEFAULT_GLASS_ENABLED = false
    const val DEFAULT_GLASS_OPACITY = 60
    const val DEFAULT_GLASS_SPOTLIGHT_ENABLED = true
    const val DEFAULT_GLASS_LIGHT_COLOR = "#E1E1E1"
    const val DEFAULT_GLASS_LIGHT_ALPHA = 80
    const val DEFAULT_GLASS_DARK_COLOR = "#0D0D0D"
    const val DEFAULT_GLASS_DARK_ALPHA = 60

    const val KEY_NAV_BUTTON_GLASS_MASK_ENABLED = "systemui_nav_button_glass_mask_enabled"
    const val KEY_GESTURE_TOP_EXCLUSION_RATIO = "systemui_gesture_top_exclusion_ratio"
    const val KEY_GESTURE_BOTTOM_EXCLUSION_RATIO = "systemui_gesture_bottom_exclusion_ratio"
    const val KEY_GESTURE_ACTIVE_MIN_RATIO = "systemui_gesture_active_min_ratio"
    const val KEY_GESTURE_ACTIVE_MAX_RATIO = "systemui_gesture_active_max_ratio"
    const val DEFAULT_TOP_EXCLUSION_RATIO = 0
    const val DEFAULT_BOTTOM_EXCLUSION_RATIO = 0

    const val ACTION_GESTURE_GLASS_CHANGED = "io.github.ra1nify.realos.action.GESTURE_GLASS_CHANGED"
    const val LEGACY_ACTION_GESTURE_GLASS_CHANGED = "com.luckyfuse.action.GESTURE_GLASS_CHANGED"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun prime(module: XposedModule) {
        val p = runCatching { module.getRemotePreferences(PREFS_NAME) }.getOrElse {
            module.log(Log.ERROR, TAG, "getRemotePreferences($PREFS_NAME) threw", it)
            null
        }
        prefs = p
        if (p == null) {
            module.log(
                Log.WARN, TAG,
                "remote preferences UNAVAILABLE -> using all defaults (enabled=$DEFAULT_ENABLED, alpha=$DEFAULT_ALPHA)"
            )
        } else {
            val keys = runCatching { p.all.keys.toList() }.getOrNull()
            module.log(Log.INFO, TAG, "remote preferences OK; allKeys=$keys")
        }
    }

    fun prefsAvailable(): Boolean = prefs != null

    fun gestureBackEnabled(): Boolean {
        val p = prefs ?: return DEFAULT_ENABLED
        return try {
            p.getBoolean(KEY_GESTURE_BACK_ENABLED, DEFAULT_ENABLED)
        } catch (_: Throwable) {
            DEFAULT_ENABLED
        }
    }

    fun gestureBackIconAlpha(): Int {
        val p = prefs ?: return DEFAULT_ALPHA
        return try {
            p.getInt(KEY_GESTURE_BACK_ICON_ALPHA, DEFAULT_ALPHA).coerceIn(0, 255)
        } catch (_: Throwable) {
            DEFAULT_ALPHA
        }
    }

    fun gestureActiveMinRatio(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_GESTURE_ACTIVE_MIN_RATIO)
            }.getOrNull()
            if (v != null) return v.coerceIn(0, 100)
        }
        val p = prefs ?: return gestureBottomExclusionRatio(context)
        return try {
            p.getInt(KEY_GESTURE_ACTIVE_MIN_RATIO, gestureBottomExclusionRatio(context)).coerceIn(0, 100)
        } catch (_: Throwable) {
            gestureBottomExclusionRatio(context)
        }
    }

    fun gestureActiveMaxRatio(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_GESTURE_ACTIVE_MAX_RATIO)
            }.getOrNull()
            if (v != null) return v.coerceIn(0, 100)
        }
        val p = prefs ?: return (100 - gestureTopExclusionRatio(context)).coerceIn(0, 100)
        return try {
            p.getInt(KEY_GESTURE_ACTIVE_MAX_RATIO, 100 - gestureTopExclusionRatio(context)).coerceIn(0, 100)
        } catch (_: Throwable) {
            (100 - gestureTopExclusionRatio(context)).coerceIn(0, 100)
        }
    }

    fun gestureTopExclusionRatio(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_GESTURE_TOP_EXCLUSION_RATIO)
            }.getOrNull()
            if (v != null) return v.coerceIn(0, 50)
        }
        val p = prefs ?: return DEFAULT_TOP_EXCLUSION_RATIO
        return try {
            p.getInt(KEY_GESTURE_TOP_EXCLUSION_RATIO, DEFAULT_TOP_EXCLUSION_RATIO).coerceIn(0, 50)
        } catch (_: Throwable) {
            DEFAULT_TOP_EXCLUSION_RATIO
        }
    }

    fun gestureBottomExclusionRatio(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_GESTURE_BOTTOM_EXCLUSION_RATIO)
            }.getOrNull()
            if (v != null) return v.coerceIn(0, 50)
        }
        val p = prefs ?: return DEFAULT_BOTTOM_EXCLUSION_RATIO
        return try {
            p.getInt(KEY_GESTURE_BOTTOM_EXCLUSION_RATIO, DEFAULT_BOTTOM_EXCLUSION_RATIO).coerceIn(0, 50)
        } catch (_: Throwable) {
            DEFAULT_BOTTOM_EXCLUSION_RATIO
        }
    }

    fun navButtonCustomEnabled(context: Context? = null): Boolean {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_CUSTOM_ENABLED)
            }.getOrNull()
            if (v != null) return v != 0
        }
        val p = prefs ?: return DEFAULT_ENABLED
        return try {
            p.getBoolean(KEY_NAV_BUTTON_CUSTOM_ENABLED, DEFAULT_ENABLED)
        } catch (_: Throwable) {
            DEFAULT_ENABLED
        }
    }

    fun navButtonPresetIndex(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_PRESET_INDEX)
            }.getOrNull()
            if (v != null && v in 1..PRESET_INDEX_CUSTOM) return v
        }
        val p = prefs ?: return DEFAULT_PRESET_INDEX
        return try {
            p.getInt(KEY_NAV_BUTTON_PRESET_INDEX, DEFAULT_PRESET_INDEX).coerceIn(1, PRESET_INDEX_CUSTOM)
        } catch (_: Throwable) {
            DEFAULT_PRESET_INDEX
        }
    }

    fun navButtonTintMode(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_TINT_MODE)
            }.getOrNull()
            if (v != null) return v
        }
        val p = prefs ?: return DEFAULT_TINT_MODE
        return try {
            p.getInt(KEY_NAV_BUTTON_TINT_MODE, DEFAULT_TINT_MODE)
        } catch (_: Throwable) {
            DEFAULT_TINT_MODE
        }
    }

    fun navButtonTintColor(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_TINT_COLOR)
            }.getOrNull()
            if (v != null) return v
        }
        val p = prefs ?: return DEFAULT_TINT_COLOR
        return try {
            p.getInt(KEY_NAV_BUTTON_TINT_COLOR, DEFAULT_TINT_COLOR)
        } catch (_: Throwable) {
            DEFAULT_TINT_COLOR
        }
    }

    fun navButtonTintLightColor(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_TINT_LIGHT_COLOR)
            }.getOrNull()
            if (v != null) return v
        }
        val p = prefs ?: return DEFAULT_TINT_LIGHT_COLOR
        return try {
            p.getInt(KEY_NAV_BUTTON_TINT_LIGHT_COLOR, DEFAULT_TINT_LIGHT_COLOR)
        } catch (_: Throwable) {
            DEFAULT_TINT_LIGHT_COLOR
        }
    }

    fun navButtonTintDarkColor(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_TINT_DARK_COLOR)
            }.getOrNull()
            if (v != null) return v
        }
        val p = prefs ?: return DEFAULT_TINT_DARK_COLOR
        return try {
            p.getInt(KEY_NAV_BUTTON_TINT_DARK_COLOR, DEFAULT_TINT_DARK_COLOR)
        } catch (_: Throwable) {
            DEFAULT_TINT_DARK_COLOR
        }
    }

    fun navButtonGlassMaskEnabled(context: Context? = null): Boolean {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_GLASS_MASK_ENABLED)
            }.getOrNull()
            if (v != null) return v != 0
        }
        val p = prefs ?: return false
        return try {
            p.getBoolean(KEY_NAV_BUTTON_GLASS_MASK_ENABLED, false)
        } catch (_: Throwable) {
            false
        }
    }

    fun navButtonGlassEnabled(context: Context? = null): Boolean {
        val hasCapsule = navButtonGlassCapsuleMode(context) != CAPSULE_MODE_NONE
        val hasMask = navButtonGlassMaskEnabled(context)
        return hasCapsule || hasMask
    }

    fun navButtonGlassOpacity(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_GLASS_OPACITY)
            }.getOrNull()
            if (v != null) return v.coerceIn(0, 100)
        }
        val p = prefs ?: return DEFAULT_GLASS_OPACITY
        return try {
            p.getInt(KEY_NAV_BUTTON_GLASS_OPACITY, DEFAULT_GLASS_OPACITY).coerceIn(0, 100)
        } catch (_: Throwable) {
            DEFAULT_GLASS_OPACITY
        }
    }

    fun navButtonGlassCapsuleMode(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_GLASS_CAPSULE_MODE)
            }.getOrNull()
            if (v != null) return v.coerceIn(0, 4)
        }
        val p = prefs ?: return DEFAULT_GLASS_CAPSULE_MODE
        return try {
            p.getInt(KEY_NAV_BUTTON_GLASS_CAPSULE_MODE, DEFAULT_GLASS_CAPSULE_MODE).coerceIn(0, 4)
        } catch (_: Throwable) {
            DEFAULT_GLASS_CAPSULE_MODE
        }
    }

    fun navButtonGlassSpotlightEnabled(context: Context? = null): Boolean {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_GLASS_SPOTLIGHT_ENABLED)
            }.getOrNull()
            if (v != null) return v != 0
        }
        val p = prefs ?: return DEFAULT_GLASS_SPOTLIGHT_ENABLED
        return try {
            p.getBoolean(KEY_NAV_BUTTON_GLASS_SPOTLIGHT_ENABLED, DEFAULT_GLASS_SPOTLIGHT_ENABLED)
        } catch (_: Throwable) {
            DEFAULT_GLASS_SPOTLIGHT_ENABLED
        }
    }

    fun navButtonGlassLightColor(context: Context? = null): String {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getString(context.contentResolver, KEY_NAV_BUTTON_GLASS_LIGHT_COLOR)
            }.getOrNull()
            if (!v.isNullOrEmpty()) return v
        }
        val p = prefs ?: return DEFAULT_GLASS_LIGHT_COLOR
        return try {
            p.getString(KEY_NAV_BUTTON_GLASS_LIGHT_COLOR, DEFAULT_GLASS_LIGHT_COLOR) ?: DEFAULT_GLASS_LIGHT_COLOR
        } catch (_: Throwable) {
            DEFAULT_GLASS_LIGHT_COLOR
        }
    }

    fun navButtonGlassLightAlpha(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_GLASS_LIGHT_ALPHA)
            }.getOrNull()
            if (v != null) return v.coerceIn(10, 100)
        }
        val p = prefs ?: return DEFAULT_GLASS_LIGHT_ALPHA
        return try {
            p.getInt(KEY_NAV_BUTTON_GLASS_LIGHT_ALPHA, DEFAULT_GLASS_LIGHT_ALPHA).coerceIn(10, 100)
        } catch (_: Throwable) {
            DEFAULT_GLASS_LIGHT_ALPHA
        }
    }

    fun navButtonGlassDarkColor(context: Context? = null): String {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getString(context.contentResolver, KEY_NAV_BUTTON_GLASS_DARK_COLOR)
            }.getOrNull()
            if (!v.isNullOrEmpty()) return v
        }
        val p = prefs ?: return DEFAULT_GLASS_DARK_COLOR
        return try {
            p.getString(KEY_NAV_BUTTON_GLASS_DARK_COLOR, DEFAULT_GLASS_DARK_COLOR) ?: DEFAULT_GLASS_DARK_COLOR
        } catch (_: Throwable) {
            DEFAULT_GLASS_DARK_COLOR
        }
    }

    fun navButtonGlassDarkAlpha(context: Context? = null): Int {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(context.contentResolver, KEY_NAV_BUTTON_GLASS_DARK_ALPHA)
            }.getOrNull()
            if (v != null) return v.coerceIn(10, 100)
        }
        val p = prefs ?: return DEFAULT_GLASS_DARK_ALPHA
        return try {
            p.getInt(KEY_NAV_BUTTON_GLASS_DARK_ALPHA, DEFAULT_GLASS_DARK_ALPHA).coerceIn(10, 100)
        } catch (_: Throwable) {
            DEFAULT_GLASS_DARK_ALPHA
        }
    }

    const val KEY_GESTURE_BACK_VIBRATE_ENABLED = "systemui_gesture_back_vibrate_enabled"
    const val DEFAULT_VIBRATE_ENABLED = true

    fun gestureBackVibrateEnabled(context: Context? = null): Boolean {
        if (context != null) {
            val v = runCatching {
                android.provider.Settings.System.getInt(
                    context.contentResolver, KEY_GESTURE_BACK_VIBRATE_ENABLED
                )
            }.getOrNull()
            if (v != null) return v != 0
        }
        val p = prefs ?: return DEFAULT_VIBRATE_ENABLED
        return try {
            p.getBoolean(KEY_GESTURE_BACK_VIBRATE_ENABLED, DEFAULT_VIBRATE_ENABLED)
        } catch (_: Throwable) {
            DEFAULT_VIBRATE_ENABLED
        }
    }

    const val KEY_GESTURE_BACK_CUSTOM_ICON = "systemui_gesture_back_custom_icon"

    fun gestureAppearance(context: Context?): GestureAppearance {
        if (context == null) return GestureAppearance()
        val s = runCatching {
            android.provider.Settings.System.getString(
                context.contentResolver, GestureAppearance.KEY_APPEARANCE
            )
        }.getOrNull()
        return GestureAppearanceCodec.fromJson(s)
    }

    fun gestureBackCustomIconBase64(context: Context?): String {
        if (context == null) return ""
        return runCatching {
            android.provider.Settings.System.getString(
                context.contentResolver, KEY_GESTURE_BACK_CUSTOM_ICON
            )
        }.getOrNull().orEmpty()
    }

    fun gestureVibration(context: Context?): GestureVibrationConfig {
        if (context == null) return GestureVibrationConfig()
        val s = runCatching {
            android.provider.Settings.System.getString(
                context.contentResolver, GestureVibrationConfig.KEY_VIBRATION
            )
        }.getOrNull()
        return GestureVibrationCodec.fromJson(s)
    }
}
