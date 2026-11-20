package io.github.ra1nify.realos.navigation

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.service.XposedServiceManager
import io.github.ra1nify.realos.navigation.GestureAppearance
import io.github.ra1nify.realos.navigation.GestureAppearanceCodec
import io.github.ra1nify.realos.navigation.GestureVibrationCodec
import io.github.ra1nify.realos.navigation.GestureVibrationConfig
import io.github.ra1nify.realos.navigation.SystemUiConfig
import io.github.ra1nify.realos.navigation.SettingsConfig

/**
 * Configuration reads and writes for Settings (`com.android.settings`).
 */
object SettingsConfig {

    private const val TAG = "RealOS-SettingsConfig"

    private const val PREFS_NAME = "app_config"
    private const val NAV_BUTTON_PREFS_NAME = "realos_nav_button_config"

    const val KEY_NAV_GESTURE_ENABLED = "settings_nav_gesture_enabled"
    const val KEY_NAV_BUTTON_ENABLED = "settings_nav_button_enabled"

    const val KEY_NAV_VISUAL_ENABLED = "settings_nav_visual_enabled"
    const val KEY_NAV_HAPTIC_ENABLED = "settings_nav_haptic_enabled"
    const val KEY_NAV_LOGIC_ENABLED = "settings_nav_logic_enabled"
    const val KEY_NAV_ADVANCED_ENABLED = "settings_nav_advanced_enabled"

    const val DEFAULT_ENTRY_ENABLED = true

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var module: XposedModule? = null

    /** App-side context: host process reads remote prefs, app process reads local SP. */
    @Volatile
    private var appContext: java.lang.ref.WeakReference<Context>? = null

    fun init(context: Context) {
        appContext = java.lang.ref.WeakReference(context.applicationContext ?: context)
    }

    private fun log(level: Int, msg: String) {
        module?.log(level, TAG, msg)
    }

    private fun log(level: Int, msg: String, t: Throwable) {
        module?.log(level, TAG, msg, t)
    }

    fun prime(module: XposedModule) {
        this.module = module
        val p = runCatching { module.getRemotePreferences(PREFS_NAME) }.getOrElse {
            module.log(Log.ERROR, TAG, "getRemotePreferences threw", it)
            null
        }
        prefs = p
        module.log(
            Log.INFO, TAG,
            "remote preferences ${if (p == null) "unavailable" else "OK"}; " +
                "allKeys=${runCatching { p?.all?.keys?.toList() }.getOrNull()}"
        )
    }

    fun visualEntryEnabled(): Boolean = readBoolean(KEY_NAV_VISUAL_ENABLED, DEFAULT_ENTRY_ENABLED)
    fun setVisualEntryEnabled(context: Context, enabled: Boolean) =
        writeBoth(context, KEY_NAV_VISUAL_ENABLED, enabled)

    fun hapticEntryEnabled(): Boolean = readBoolean(KEY_NAV_HAPTIC_ENABLED, DEFAULT_ENTRY_ENABLED)
    fun setHapticEntryEnabled(context: Context, enabled: Boolean) =
        writeBoth(context, KEY_NAV_HAPTIC_ENABLED, enabled)

    fun logicEntryEnabled(): Boolean = readBoolean(KEY_NAV_LOGIC_ENABLED, DEFAULT_ENTRY_ENABLED)
    fun setLogicEntryEnabled(context: Context, enabled: Boolean) =
        writeBoth(context, KEY_NAV_LOGIC_ENABLED, enabled)

    fun advancedEntryEnabled(): Boolean = readBoolean(KEY_NAV_ADVANCED_ENABLED, DEFAULT_ENTRY_ENABLED)
    fun setAdvancedEntryEnabled(context: Context, enabled: Boolean) =
        writeBoth(context, KEY_NAV_ADVANCED_ENABLED, enabled)

    fun gestureEntryEnabled(): Boolean =
        readBoolean(KEY_NAV_GESTURE_ENABLED, DEFAULT_ENTRY_ENABLED)

    fun setGestureEntryEnabled(context: Context, enabled: Boolean) =
        writeBoth(context, KEY_NAV_GESTURE_ENABLED, enabled)

    fun buttonEntryEnabled(): Boolean =
        readBoolean(KEY_NAV_BUTTON_ENABLED, DEFAULT_ENTRY_ENABLED)

    fun setButtonEntryEnabled(context: Context, enabled: Boolean) =
        writeBoth(context, KEY_NAV_BUTTON_ENABLED, enabled)

    fun allEntriesDisabled(): Boolean =
        !visualEntryEnabled() && !hapticEntryEnabled() && !logicEntryEnabled() && !advancedEntryEnabled() && !gestureEntryEnabled() && !buttonEntryEnabled()

    // ────────────────────────────────────────────────────────────────
    // Back gesture vibration
    // ────────────────────────────────────────────────────────────────

    fun gestureBackVibrateEnabled(ctx: Context?): Boolean {
        if (ctx != null) {
            val v = runCatching {
                Settings.System.getInt(ctx.contentResolver, SystemUiConfig.KEY_GESTURE_BACK_VIBRATE_ENABLED)
            }.getOrNull()
            if (v != null) return v != 0
        }
        return readBoolean(SystemUiConfig.KEY_GESTURE_BACK_VIBRATE_ENABLED, SystemUiConfig.DEFAULT_VIBRATE_ENABLED)
    }

    fun setGestureBackVibrateEnabled(ctx: Context, enabled: Boolean): Boolean {
        val ok = runCatching {
            Settings.System.putInt(
                ctx.contentResolver,
                SystemUiConfig.KEY_GESTURE_BACK_VIBRATE_ENABLED,
                if (enabled) 1 else 0
            )
        }.getOrElse {
            log(Log.ERROR, "setGestureBackVibrateEnabled: Settings.System.putInt failed", it)
            false
        }
        return ok
    }

    // ────────────────────────────────────────────────────────────────
    // Back gesture appearance (shadow / stroke / arrow)
    // ────────────────────────────────────────────────────────────────

    fun gestureAppearance(ctx: Context): GestureAppearance {
        val s = runCatching {
            Settings.System.getString(ctx.contentResolver, GestureAppearance.KEY_APPEARANCE)
        }.getOrNull()
        return GestureAppearanceCodec.fromJson(s)
    }

    /**
     * Queues a coalesced string write for a continuously updated value (slider drag).
     *
     * The application context is used because the write runs later on a background thread and must
     * not keep an Activity alive.
     */
    private fun putStringCoalesced(ctx: Context, key: String, value: String): Boolean {
        CoalescedSettingsWriter.putString((ctx.applicationContext ?: ctx).contentResolver, key, value)
        return true
    }

    /** Queues a coalesced int write for a continuously updated value (slider drag). */
    private fun putIntCoalesced(ctx: Context, key: String, value: Int): Boolean {
        CoalescedSettingsWriter.putInt((ctx.applicationContext ?: ctx).contentResolver, key, value)
        return true
    }

    fun setGestureAppearance(ctx: Context, appearance: GestureAppearance): Boolean {
        putStringCoalesced(ctx, GestureAppearance.KEY_APPEARANCE, GestureAppearanceCodec.toJson(appearance))
        // The broadcast is queued too, so a drag costs one write and one broadcast per drain
        // instead of one of each per frame.
        val appCtx = ctx.applicationContext ?: ctx
        CoalescedSettingsWriter.submitNotify("notify:gesture_appearance") {
            notifyGestureAppearanceChanged(appCtx)
        }
        return true
    }

    fun setGestureBackCustomIcon(ctx: Context, base64: String): Boolean {
        val ok = runCatching {
            Settings.System.putString(
                ctx.contentResolver, SystemUiConfig.KEY_GESTURE_BACK_CUSTOM_ICON, base64
            )
        }.getOrElse {
            log(Log.ERROR, "setGestureBackCustomIcon failed", it)
            false
        }
        return ok
    }

    fun gestureBackCustomIconBase64(ctx: Context): String = runCatching {
        Settings.System.getString(ctx.contentResolver, SystemUiConfig.KEY_GESTURE_BACK_CUSTOM_ICON)
    }.getOrNull().orEmpty()

    // ────────────────────────────────────────────────────────────────
    // Two-stage back gesture vibration tuning
    // ────────────────────────────────────────────────────────────────

    fun gestureVibration(ctx: Context): GestureVibrationConfig {
        val s = runCatching {
            Settings.System.getString(ctx.contentResolver, GestureVibrationConfig.KEY_VIBRATION)
        }.getOrNull()
        return GestureVibrationCodec.fromJson(s)
    }

    fun setGestureVibration(ctx: Context, cfg: GestureVibrationConfig): Boolean =
        putStringCoalesced(ctx, GestureVibrationConfig.KEY_VIBRATION, GestureVibrationCodec.toJson(cfg))

    // ────────────────────────────────────────────────────────────────
    // Navigation button liquid glass (shares Settings.System with the SystemUI side)
    // ────────────────────────────────────────────────────────────────

    const val KEY_NAV_BUTTON_GLASS_SWITCH_MIGRATED = "systemui_nav_button_glass_switch_migrated"

    /**
     * Liquid glass master switch check.
     *
     * **Delegates to SystemUiConfig**: `SystemUiConfig` treats capsule or mask as on,
     * and the broadcast `EXTRA_GLASS_ENABLED` carries the value the host reads.
     */
    fun navButtonGlassEnabled(ctx: Context): Boolean =
        SystemUiConfig.navButtonGlassEnabled(ctx)

    fun setNavButtonGlassEnabled(ctx: Context, enabled: Boolean): Boolean {
        val ok = if (enabled) {
            val cur = navButtonGlassCapsuleMode(ctx)
            if (cur != SystemUiConfig.CAPSULE_MODE_NONE) true
            else setNavButtonGlassCapsuleMode(ctx, SystemUiConfig.CAPSULE_MODE_SINGLE)
        } else {
            setNavButtonGlassCapsuleMode(ctx, SystemUiConfig.CAPSULE_MODE_NONE)
        }
        writeLegacyNavButtonGlassEnabled(ctx, enabled)
        notifyNavButtonChanged(ctx)
        return ok
    }

    fun writeLegacyNavButtonGlassEnabled(ctx: Context, enabled: Boolean): Boolean =
        runCatching {
            Settings.System.putInt(
                ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_ENABLED, if (enabled) 1 else 0
            )
        }.getOrDefault(false)

    fun isNavButtonGlassMaskEnabled(ctx: Context): Boolean {
        val v = runCatching {
            Settings.System.getInt(ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_MASK_ENABLED)
        }.getOrNull()
        return (v ?: 0) != 0
    }

    fun setNavButtonGlassMaskEnabled(ctx: Context, enabled: Boolean) {
        writeInt(ctx, SystemUiConfig.KEY_NAV_BUTTON_GLASS_MASK_ENABLED, if (enabled) 1 else 0)
        notifyNavButtonChanged(ctx)
    }

    fun migrateNavButtonGlassSwitch(ctx: Context) {
        runCatching {
            val done = runCatching {
                Settings.System.getInt(ctx.contentResolver, KEY_NAV_BUTTON_GLASS_SWITCH_MIGRATED)
            }.getOrNull()
            if (done == 1) return

            val legacy = runCatching {
                Settings.System.getInt(ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_ENABLED)
            }.getOrNull()
            if (legacy == null || legacy == 0) {
                runCatching {
                    Settings.System.putInt(
                        ctx.contentResolver,
                        SystemUiConfig.KEY_NAV_BUTTON_GLASS_CAPSULE_MODE,
                        SystemUiConfig.CAPSULE_MODE_NONE
                    )
                }
            }
            runCatching {
                Settings.System.putInt(ctx.contentResolver, KEY_NAV_BUTTON_GLASS_SWITCH_MIGRATED, 1)
            }
            log(Log.INFO, "migrateNavButtonGlassSwitch: legacyEnabled=$legacy")
        }.onFailure { log(Log.WARN, "migrateNavButtonGlassSwitch failed", it) }
    }

    fun navButtonGlassOpacity(ctx: Context): Int {
        val v = runCatching {
            Settings.System.getInt(ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_OPACITY)
        }.getOrNull()
        return (v ?: SystemUiConfig.DEFAULT_GLASS_OPACITY).coerceIn(0, 100)
    }

    fun setNavButtonGlassOpacity(ctx: Context, opacity: Int): Boolean =
        putIntCoalesced(ctx, SystemUiConfig.KEY_NAV_BUTTON_GLASS_OPACITY, opacity.coerceIn(0, 100))

    fun navButtonGlassSpotlightEnabled(ctx: Context): Boolean {
        val v = runCatching {
            Settings.System.getInt(ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_SPOTLIGHT_ENABLED)
        }.getOrNull()
        return if (v != null) v != 0 else SystemUiConfig.DEFAULT_GLASS_SPOTLIGHT_ENABLED
    }

    fun setNavButtonGlassSpotlightEnabled(ctx: Context, enabled: Boolean): Boolean {
        return runCatching {
            Settings.System.putInt(
                ctx.contentResolver,
                SystemUiConfig.KEY_NAV_BUTTON_GLASS_SPOTLIGHT_ENABLED,
                if (enabled) 1 else 0
            )
        }.getOrDefault(false)
    }

    fun navButtonGlassLightColor(ctx: Context): String {
        val v = runCatching {
            Settings.System.getString(ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_LIGHT_COLOR)
        }.getOrNull()
        return if (!v.isNullOrEmpty()) v else SystemUiConfig.DEFAULT_GLASS_LIGHT_COLOR
    }

    fun setNavButtonGlassLightColor(ctx: Context, color: String): Boolean {
        return runCatching {
            Settings.System.putString(ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_LIGHT_COLOR, color)
        }.getOrDefault(false)
    }

    fun navButtonGlassLightAlpha(ctx: Context): Int {
        val v = runCatching {
            Settings.System.getInt(ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_LIGHT_ALPHA)
        }.getOrNull()
        return (v ?: SystemUiConfig.DEFAULT_GLASS_LIGHT_ALPHA).coerceIn(10, 100)
    }

    fun setNavButtonGlassLightAlpha(ctx: Context, alpha: Int): Boolean =
        putIntCoalesced(ctx, SystemUiConfig.KEY_NAV_BUTTON_GLASS_LIGHT_ALPHA, alpha.coerceIn(10, 100))

    fun navButtonGlassDarkColor(ctx: Context): String {
        val v = runCatching {
            Settings.System.getString(ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_DARK_COLOR)
        }.getOrNull()
        return if (!v.isNullOrEmpty()) v else SystemUiConfig.DEFAULT_GLASS_DARK_COLOR
    }

    fun setNavButtonGlassDarkColor(ctx: Context, color: String): Boolean {
        return runCatching {
            Settings.System.putString(ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_DARK_COLOR, color)
        }.getOrDefault(false)
    }

    fun navButtonGlassDarkAlpha(ctx: Context): Int {
        val v = runCatching {
            Settings.System.getInt(ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_DARK_ALPHA)
        }.getOrNull()
        return (v ?: SystemUiConfig.DEFAULT_GLASS_DARK_ALPHA).coerceIn(10, 100)
    }

    fun setNavButtonGlassDarkAlpha(ctx: Context, alpha: Int): Boolean =
        putIntCoalesced(ctx, SystemUiConfig.KEY_NAV_BUTTON_GLASS_DARK_ALPHA, alpha.coerceIn(10, 100))

    fun navButtonGlassCapsuleMode(ctx: Context): Int {
        val v = runCatching {
            Settings.System.getInt(ctx.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_GLASS_CAPSULE_MODE)
        }.getOrNull()
        return (v ?: SystemUiConfig.DEFAULT_GLASS_CAPSULE_MODE).coerceIn(0, 4)
    }

    fun setNavButtonGlassCapsuleMode(ctx: Context, mode: Int): Boolean {
        return runCatching {
            Settings.System.putInt(
                ctx.contentResolver,
                SystemUiConfig.KEY_NAV_BUTTON_GLASS_CAPSULE_MODE,
                mode.coerceIn(0, 4)
            )
        }.getOrDefault(false)
    }

    // ────────────────────────────────────────────────────────────────
    // Navigation button custom icons and tint
    // ────────────────────────────────────────────────────────────────

    fun isNavButtonCustomEnabled(context: Context): Boolean {
        val sp = navButtonPrefs(context)
        if (sp.contains(SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_ENABLED)) {
            return sp.getBoolean(SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_ENABLED, false)
        }
        return runCatching {
            Settings.System.getInt(context.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_ENABLED) != 0
        }.getOrDefault(false)
    }

    fun setNavButtonCustomEnabled(context: Context, enabled: Boolean): Boolean {
        navButtonPrefs(context)
            .edit()
            .putBoolean(SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_ENABLED, enabled)
            .apply()
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_ENABLED,
                if (enabled) 1 else 0
            )
        }.getOrDefault(false)
    }

    fun getNavButtonPresetIndex(context: Context): Int {
        val sp = navButtonPrefs(context)
        if (sp.contains(SystemUiConfig.KEY_NAV_BUTTON_PRESET_INDEX)) {
            return sp.getInt(SystemUiConfig.KEY_NAV_BUTTON_PRESET_INDEX, 1).coerceIn(1, 11)
        }
        return runCatching {
            val v = Settings.System.getInt(context.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_PRESET_INDEX)
            if (v in 1..11) v else 1
        }.getOrDefault(1)
    }

    fun setNavButtonPresetIndex(context: Context, index: Int): Boolean {
        navButtonPrefs(context)
            .edit()
            .putInt(SystemUiConfig.KEY_NAV_BUTTON_PRESET_INDEX, index.coerceIn(1, 11))
            .apply()
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                SystemUiConfig.KEY_NAV_BUTTON_PRESET_INDEX,
                index.coerceIn(1, 11)
            )
        }.getOrDefault(false)
    }

    fun getNavButtonTintMode(context: Context): Int {
        val sp = navButtonPrefs(context)
        if (sp.contains(SystemUiConfig.KEY_NAV_BUTTON_TINT_MODE)) {
            return sp.getInt(SystemUiConfig.KEY_NAV_BUTTON_TINT_MODE, 1)
        }
        return runCatching {
            Settings.System.getInt(context.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_TINT_MODE)
        }.getOrDefault(1)
    }

    fun setNavButtonTintMode(context: Context, mode: Int): Boolean {
        navButtonPrefs(context)
            .edit()
            .putInt(SystemUiConfig.KEY_NAV_BUTTON_TINT_MODE, mode)
            .apply()
        return runCatching {
            Settings.System.putInt(context.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_TINT_MODE, mode)
        }.getOrDefault(false)
    }

    fun getNavButtonTintLightColor(context: Context): Int {
        val sp = navButtonPrefs(context)
        if (sp.contains(SystemUiConfig.KEY_NAV_BUTTON_TINT_LIGHT_COLOR)) {
            return sp.getInt(SystemUiConfig.KEY_NAV_BUTTON_TINT_LIGHT_COLOR, SystemUiConfig.DEFAULT_TINT_LIGHT_COLOR)
        }
        return runCatching {
            Settings.System.getInt(context.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_TINT_LIGHT_COLOR)
        }.getOrDefault(SystemUiConfig.DEFAULT_TINT_LIGHT_COLOR)
    }

    fun setNavButtonTintLightColor(context: Context, color: Int): Boolean {
        navButtonPrefs(context)
            .edit()
            .putInt(SystemUiConfig.KEY_NAV_BUTTON_TINT_LIGHT_COLOR, color)
            .apply()
        return runCatching {
            Settings.System.putInt(context.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_TINT_LIGHT_COLOR, color)
        }.getOrDefault(false)
    }

    fun getNavButtonTintDarkColor(context: Context): Int {
        val sp = navButtonPrefs(context)
        if (sp.contains(SystemUiConfig.KEY_NAV_BUTTON_TINT_DARK_COLOR)) {
            return sp.getInt(SystemUiConfig.KEY_NAV_BUTTON_TINT_DARK_COLOR, SystemUiConfig.DEFAULT_TINT_DARK_COLOR)
        }
        return runCatching {
            Settings.System.getInt(context.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_TINT_DARK_COLOR)
        }.getOrDefault(SystemUiConfig.DEFAULT_TINT_DARK_COLOR)
    }

    fun setNavButtonTintDarkColor(context: Context, color: Int): Boolean {
        navButtonPrefs(context)
            .edit()
            .putInt(SystemUiConfig.KEY_NAV_BUTTON_TINT_DARK_COLOR, color)
            .apply()
        return runCatching {
            Settings.System.putInt(context.contentResolver, SystemUiConfig.KEY_NAV_BUTTON_TINT_DARK_COLOR, color)
        }.getOrDefault(false)
    }

    fun getNavButtonCustomIconBase64(context: Context, type: String): String {
        val key = when (type) {
            "back" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_BACK_BASE64
            "home" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_HOME_BASE64
            "recent" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_RECENT_BASE64
            else -> return ""
        }
        val sp = navButtonPrefs(context)
        if (sp.contains(key)) return sp.getString(key, "") ?: ""
        return runCatching {
            Settings.System.getString(context.contentResolver, key)
        }.getOrNull().orEmpty()
    }

    fun setNavButtonCustomIconBase64(context: Context, type: String, base64: String): Boolean {
        val key = when (type) {
            "back" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_BACK_BASE64
            "home" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_HOME_BASE64
            "recent" -> SystemUiConfig.KEY_NAV_BUTTON_CUSTOM_RECENT_BASE64
            else -> return false
        }
        navButtonPrefs(context)
            .edit()
            .putString(key, base64)
            .apply()
        return runCatching {
            Settings.System.putString(context.contentResolver, key, base64)
        }.getOrDefault(false)
    }

    // ────────────────────────────────────────────────────────────────
    // Broadcast notifications
    // ────────────────────────────────────────────────────────────────

    fun notifyNavButtonChanged(ctx: Context) {
        // Every call reads about 17 Settings values and sends two broadcasts, so a slider drag
        // would run that once per frame. Coalescing keeps only the last request per drain.
        val appCtx = ctx.applicationContext ?: ctx
        CoalescedSettingsWriter.submitNotify("notify:nav_button") { sendNavButtonChanged(appCtx) }
    }

    private fun sendNavButtonChanged(ctx: Context) {
        val intent = Intent(SystemUiConfig.ACTION_NAV_BUTTON_CHANGED).apply {
            setPackage("com.android.systemui")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
            putExtra(SystemUiConfig.EXTRA_ENABLED, isNavButtonCustomEnabled(ctx))
            // Reads each value once; every getter is one Settings.System binder read
            val presetIndex = getNavButtonPresetIndex(ctx)
            putExtra(SystemUiConfig.EXTRA_PRESET_INDEX, presetIndex)
            putExtra(SystemUiConfig.EXTRA_TINT_MODE, getNavButtonTintMode(ctx))
            putExtra(SystemUiConfig.EXTRA_TINT_LIGHT_COLOR, getNavButtonTintLightColor(ctx))
            putExtra(SystemUiConfig.EXTRA_TINT_DARK_COLOR, getNavButtonTintDarkColor(ctx))
            putExtra(SystemUiConfig.EXTRA_GLASS_ENABLED, navButtonGlassEnabled(ctx))
            putExtra(SystemUiConfig.EXTRA_GLASS_OPACITY, navButtonGlassOpacity(ctx))
            putExtra(SystemUiConfig.EXTRA_GLASS_CAPSULE_MODE, navButtonGlassCapsuleMode(ctx))
            putExtra(SystemUiConfig.EXTRA_GLASS_SPOTLIGHT_ENABLED, navButtonGlassSpotlightEnabled(ctx))
            putExtra(SystemUiConfig.EXTRA_GLASS_LIGHT_COLOR, navButtonGlassLightColor(ctx))
            putExtra(SystemUiConfig.EXTRA_GLASS_LIGHT_ALPHA, navButtonGlassLightAlpha(ctx))
            putExtra(SystemUiConfig.EXTRA_GLASS_DARK_COLOR, navButtonGlassDarkColor(ctx))
            putExtra(SystemUiConfig.EXTRA_GLASS_DARK_ALPHA, navButtonGlassDarkAlpha(ctx))
            if (presetIndex == SystemUiConfig.PRESET_INDEX_CUSTOM) {
                putExtra(SystemUiConfig.EXTRA_CUSTOM_BACK, getNavButtonCustomIconBase64(ctx, "back"))
                putExtra(SystemUiConfig.EXTRA_CUSTOM_HOME, getNavButtonCustomIconBase64(ctx, "home"))
                putExtra(SystemUiConfig.EXTRA_CUSTOM_RECENT, getNavButtonCustomIconBase64(ctx, "recent"))
            }
        }
        val legacyIntent = Intent(intent).apply {
            action = SystemUiConfig.LEGACY_ACTION_NAV_BUTTON_CHANGED
        }
        runCatching {
            val appCtx = ctx.applicationContext ?: ctx
            appCtx.sendBroadcast(intent)
            appCtx.sendBroadcast(legacyIntent)
        }.onFailure {
            log(Log.WARN, "sendBroadcast NAV_BUTTON_CHANGED failed", it)
        }
    }

    fun notifyGestureGlassChanged(ctx: Context) {
        val intent = Intent(SystemUiConfig.ACTION_GESTURE_GLASS_CHANGED).apply {
            setPackage("com.android.systemui")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
        }
        val legacyIntent = Intent(intent).apply {
            action = SystemUiConfig.LEGACY_ACTION_GESTURE_GLASS_CHANGED
        }
        runCatching {
            val appCtx = ctx.applicationContext ?: ctx
            appCtx.sendBroadcast(intent)
            appCtx.sendBroadcast(legacyIntent)
        }.onFailure {
            log(Log.WARN, "sendBroadcast GESTURE_GLASS_CHANGED failed", it)
        }
    }

    private fun notifyGestureAppearanceChanged(ctx: Context) {
        notifyGestureGlassChanged(ctx)
    }

    fun gestureActiveMinRatio(ctx: Context): Int =
        readInt(ctx, SystemUiConfig.KEY_GESTURE_ACTIVE_MIN_RATIO, 0)

    fun gestureActiveMaxRatio(ctx: Context): Int =
        readInt(ctx, SystemUiConfig.KEY_GESTURE_ACTIVE_MAX_RATIO, 100)

    fun setGestureActiveRangeRatio(ctx: Context, minRatio: Int, maxRatio: Int) {
        val minClamped = minRatio.coerceIn(0, 100)
        val maxClamped = maxRatio.coerceIn(0, 100)
        putIntCoalesced(ctx, SystemUiConfig.KEY_GESTURE_ACTIVE_MIN_RATIO, minClamped)
        putIntCoalesced(ctx, SystemUiConfig.KEY_GESTURE_ACTIVE_MAX_RATIO, maxClamped)
        // Mapped to Top/Bottom Exclusion for compatibility
        putIntCoalesced(ctx, SystemUiConfig.KEY_GESTURE_BOTTOM_EXCLUSION_RATIO, minClamped)
        putIntCoalesced(ctx, SystemUiConfig.KEY_GESTURE_TOP_EXCLUSION_RATIO, (100 - maxClamped).coerceIn(0, 100))
        notifyGestureGlassChanged(ctx)
    }

    fun gestureTopExclusionRatio(ctx: Context): Int =
        readInt(ctx, SystemUiConfig.KEY_GESTURE_TOP_EXCLUSION_RATIO, SystemUiConfig.DEFAULT_TOP_EXCLUSION_RATIO)

    fun setGestureTopExclusionRatio(ctx: Context, ratio: Int) {
        putIntCoalesced(ctx, SystemUiConfig.KEY_GESTURE_TOP_EXCLUSION_RATIO, ratio.coerceIn(0, 50))
        notifyGestureGlassChanged(ctx)
    }

    fun gestureBottomExclusionRatio(ctx: Context): Int =
        readInt(ctx, SystemUiConfig.KEY_GESTURE_BOTTOM_EXCLUSION_RATIO, SystemUiConfig.DEFAULT_BOTTOM_EXCLUSION_RATIO)

    fun setGestureBottomExclusionRatio(ctx: Context, ratio: Int) {
        putIntCoalesced(ctx, SystemUiConfig.KEY_GESTURE_BOTTOM_EXCLUSION_RATIO, ratio.coerceIn(0, 50))
        notifyGestureGlassChanged(ctx)
    }

    private fun readInt(ctx: Context, key: String, def: Int): Int {
        val v = runCatching {
            Settings.System.getInt(ctx.contentResolver, key)
        }.getOrNull()
        if (v != null) return v
        val p = prefs ?: return def
        return try {
            p.getInt(key, def)
        } catch (_: Throwable) {
            def
        }
    }

    private fun writeInt(ctx: Context, key: String, value: Int) {
        runCatching {
            Settings.System.putInt(ctx.contentResolver, key, value)
        }
        localPrefs(ctx)
            .edit()
            .putInt(key, value)
            .apply()
    }

    /** `realos_nav_button_config` (local mirror). */
    private fun navButtonPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAV_BUTTON_PREFS_NAME, Context.MODE_PRIVATE)

    /** Local copy of `app_config`, read on the app side. */
    private fun localPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Reads a switch: the host process reads remote prefs written by [pushToRemote],
     * the app process falls back to the local SP.
     */
    private fun readBoolean(key: String, def: Boolean): Boolean {
        val p = prefs
        if (p != null) {
            return try {
                p.getBoolean(key, def)
            } catch (_: Throwable) {
                def
            }
        }
        val ctx = appContext?.get() ?: return def
        return runCatching {
            localPrefs(ctx).getBoolean(key, def)
        }.getOrDefault(def)
    }

    /**
     * Writes the local SP (app-side UI) and the remote preferences (host-side
     * `SettingsNavInject`). A plain SP write does not update remote preferences;
     * [XposedServiceManager.getRemotePreferences] is written once more for the host.
     */
    private fun writeBoth(context: Context, key: String, enabled: Boolean) {
        runCatching {
            localPrefs(context)
                .edit().putBoolean(key, enabled).apply()
        }.onFailure { Log.w(TAG, "write local prefs failed: $key", it) }
        pushToRemote(key, enabled)
    }

    private fun pushToRemote(key: String, enabled: Boolean) {
        runCatching {
            XposedServiceManager.getRemotePreferences(PREFS_NAME)
                ?.edit()?.putBoolean(key, enabled)?.apply()
        }.onFailure { Log.w(TAG, "push remote prefs failed: $key", it) }
    }

    /** Syncs app-side local values to remote preferences when the page is entered. */
    fun syncToRemote(context: Context) {
        init(context)
        for (key in listOf(
            KEY_NAV_VISUAL_ENABLED,
            KEY_NAV_HAPTIC_ENABLED,
            KEY_NAV_LOGIC_ENABLED,
            KEY_NAV_ADVANCED_ENABLED,
            KEY_NAV_GESTURE_ENABLED,
            KEY_NAV_BUTTON_ENABLED
        )) {
            pushToRemote(key, readLocalBoolean(context, key, DEFAULT_ENTRY_ENABLED))
        }
    }

    private fun readLocalBoolean(context: Context, key: String, def: Boolean): Boolean = runCatching {
        localPrefs(context).getBoolean(key, def)
    }.getOrDefault(def)
}
