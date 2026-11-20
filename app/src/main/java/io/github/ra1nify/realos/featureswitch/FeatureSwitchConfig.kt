package io.github.ra1nify.realos.featureswitch

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.root.RootManager
import io.github.ra1nify.realos.service.XposedServiceManager

/**
 * Pure software feature switch configuration (staging).
 *
 * Reads and writes SharedPreferences and XposedServiceManager RemotePreferences on the
 * app side; the host side (com.android.settings / com.oplus.wallpapers) reads the
 * latest switch state through LibXposed RemotePreferences.
 */
object FeatureSwitchConfig {

    private const val TAG = "RealOS-FeatureSwitchConfig"
    private const val PREFS_NAME = "app_config"

    // ── OnePlus features and branding ──
    const val KEY_ONEPLUS_BRAND = "feat_oneplus_brand"
    const val KEY_ONEPLUS_RED_ONE = "feat_oneplus_red_one"

    // ── System performance and scheduling ──
    const val KEY_GT_MODE = "feat_gt_mode"
    const val KEY_APP_FROZEN = "feat_app_frozen"
    const val KEY_PURE_MODE = "feat_pure_mode"
    const val KEY_FLUID_CLOUD = "feat_fluid_cloud"
    const val KEY_MINI_CAPSULE = "feat_mini_capsule"
    const val KEY_CUSTOM_HIDE_APP = "feat_custom_hide_app"
    const val KEY_TASKBAR = "feat_taskbar"

    // ── Display quality and eye care ──
    const val KEY_VIDEO_COLOR_ENHANCE = "feat_video_color_enhance"
    const val KEY_VIDEO_OSIE = "feat_video_osie"
    const val KEY_VIDEO_SR = "feat_video_sr"
    const val KEY_APP_HIGH_REFRESH = "feat_app_high_refresh"
    const val KEY_PWM_REBOOT_CLOSE = "feat_pwm_reboot_close"
    const val KEY_COMFORT_COLOR_TEMP = "feat_comfort_color_temp"
    const val KEY_SINGLE_PULSE_REFRESH = "feat_single_pulse_refresh"
    const val KEY_DC_MIN_2NIT = "feat_dc_min_2nit"

    // ── Audio routing and media enhancements ──
    const val KEY_DUAL_HEADPHONE = "feat_dual_headphone"
    const val KEY_MULTI_SOUNDS_PLAY = "feat_multi_sounds_play"
    const val KEY_SUPER_VOLUME = "feat_super_volume"
    const val KEY_KARAOKE = "feat_karaoke"
    const val KEY_DOLBY = "feat_dolby"
    const val KEY_DIRAC = "feat_dirac"

    // ── Permission and developer restriction bypasses ──
    const val KEY_DISABLE_OEM_UNLOCK_BYPASS = "feat_oem_unlock_bypass"
    const val KEY_APP_DISALLOW_DISABLE_BYPASS = "feat_disallow_disable_bypass"
    const val KEY_CLEAR_CACHE_DISABLED_BYPASS = "feat_clear_cache_bypass"
    const val KEY_VOLTE_UNAVAILABLE_BYPASS = "feat_volte_bypass"
    const val KEY_VOWIFI_UNAVAILABLE_BYPASS = "feat_vowifi_bypass"
    const val KEY_VIDEO_WALLPAPER = "feat_video_wallpaper"
    const val KEY_AI_EYE_PROTECT = "feat_ai_eye_protect"
    const val KEY_DISALLOW_SWITCH_PREV_APP_BYPASS = "feat_switch_prev_app_bypass"

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var module: XposedModule? = null

    fun prime(module: XposedModule) {
        this.module = module
        val p = runCatching { module.getRemotePreferences(PREFS_NAME) }.getOrElse {
            module.log(Log.ERROR, TAG, "getRemotePreferences failed", it)
            null
        }
        prefs = p
    }

    fun isEnabled(context: Context, key: String, def: Boolean = false): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(key, def)
    }

    fun setEnabled(context: Context, key: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, enabled)
            .apply()
        pushToRemote(key, enabled)

        if (key == KEY_ONEPLUS_RED_ONE) {
            syncSystemRedOneSetting(context, enabled)
        }
    }

    /**
     * Syncs every feat_* entry in local SharedPreferences to RemotePreferences.
     */
    fun syncToRemote(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val all = sp.all
        val remotePrefs = XposedServiceManager.getRemotePreferences(PREFS_NAME)
        val editor = remotePrefs?.edit()
        for ((k, v) in all) {
            if (k.startsWith("feat_") && v is Boolean) {
                editor?.putBoolean(k, v)
                if (k == KEY_ONEPLUS_RED_ONE) {
                    syncSystemRedOneSetting(context, v)
                }
            }
        }
        editor?.apply()
    }

    private fun pushToRemote(key: String, enabled: Boolean) {
        runCatching {
            XposedServiceManager.getRemotePreferences(PREFS_NAME)
                ?.edit()
                ?.putBoolean(key, enabled)
                ?.apply()
        }
    }

    private fun syncSystemRedOneSetting(context: Context, enabled: Boolean) {
        val value = if (enabled) 1 else 0
        val ok = runCatching {
            Settings.System.putInt(context.contentResolver, "oneplus_red_one_enable", value)
        }.getOrDefault(false)
        if (!ok) {
            RootManager.suExecRaw("settings put system oneplus_red_one_enable $value")
        }
    }

    fun isHookEnabled(key: String, def: Boolean = false): Boolean {
        val p = prefs ?: module?.let { runCatching { it.getRemotePreferences(PREFS_NAME) }.getOrNull() } ?: return def
        return try {
            p.getBoolean(key, def)
        } catch (_: Throwable) {
            def
        }
    }
}
