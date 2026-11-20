package io.github.ra1nify.realos.xposed.hooks.camera

import android.content.Context

/**
 * realOS camera configuration stored inside the host process.
 *
 * Holds host-side feature switches only: toggles on the realOS tab write directly into the host's
 * own SharedPreferences, **not through LSPosed remote preferences**.
 * Feature switches are read and written inside the host process only.
 *
 * `camera_realos_tab_enabled` is the exception; it goes through remote preferences
 * and decides whether the host process installs the tab Hook.
 */
object OplusCameraHostPrefs {

    private const val PREFS_NAME = "realos_camera_prefs"

    /** Whether the settings page follows system light/dark mode and accent color */
    const val KEY_THEME_FOLLOW_SYSTEM = "theme_follow_system"

    private const val DEFAULT_THEME_FOLLOW_SYSTEM = false

    fun isThemeFollowSystemEnabled(context: Context): Boolean = runCatching {
        prefs(context).getBoolean(KEY_THEME_FOLLOW_SYSTEM, DEFAULT_THEME_FOLLOW_SYSTEM)
    }.getOrDefault(DEFAULT_THEME_FOLLOW_SYSTEM)

    fun setThemeFollowSystemEnabled(context: Context, enabled: Boolean) {
        runCatching { prefs(context).edit().putBoolean(KEY_THEME_FOLLOW_SYSTEM, enabled).apply() }
    }

    private fun prefs(context: Context) =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
