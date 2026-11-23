package io.github.ra1nify.realos.font

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.service.XposedServiceManager

/**
 * Configuration for the realOS system-font business.
 *
 * The switches live in the realOS Settings spec page. The app owns the local
 * values and mirrors them to the LSPosed remote preferences; the Settings hook
 * only reads them.
 */
object SystemFontConfig {

    private const val PREFS_NAME = "systemfont_config"

    const val KEY_ENABLED = "systemfont_settings_entry_enabled"
    const val KEY_HIDE_THEME_STORE = "systemfont_hide_theme_store"

    private const val KEY_REVISION = "systemfont_revision"

    const val DEFAULT_ENABLED = true
    const val DEFAULT_HIDE_THEME_STORE = false

    fun isEnabled(module: XposedModule): Boolean = runCatching {
        module.getRemotePreferences(PREFS_NAME)
            .getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
    }.getOrDefault(DEFAULT_ENABLED)

    fun isEnabled(context: Context): Boolean = runCatching {
        local(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
    }.getOrDefault(DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        runCatching {
            local(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
            remote(context)?.edit()?.putBoolean(KEY_ENABLED, enabled)?.commit()
        }
    }

    fun isHideThemeStore(module: XposedModule): Boolean = runCatching {
        module.getRemotePreferences(PREFS_NAME)
            .getBoolean(KEY_HIDE_THEME_STORE, DEFAULT_HIDE_THEME_STORE)
    }.getOrDefault(DEFAULT_HIDE_THEME_STORE)

    fun isHideThemeStore(context: Context): Boolean = runCatching {
        local(context).getBoolean(KEY_HIDE_THEME_STORE, DEFAULT_HIDE_THEME_STORE)
    }.getOrDefault(DEFAULT_HIDE_THEME_STORE)

    fun setHideThemeStore(context: Context, hide: Boolean) {
        runCatching {
            local(context).edit().putBoolean(KEY_HIDE_THEME_STORE, hide).apply()
            remote(context)?.edit()?.putBoolean(KEY_HIDE_THEME_STORE, hide)?.commit()
        }
    }

    /** Mirrors both switches and the library revision to the Settings process. */
    fun syncToRemote(context: Context) {
        runCatching {
            remote(context)?.edit()
                ?.putBoolean(KEY_ENABLED, isEnabled(context))
                ?.putBoolean(KEY_HIDE_THEME_STORE, isHideThemeStore(context))
                ?.putInt(KEY_REVISION, local(context).getInt(KEY_REVISION, 0))
                // Remove reset keys left by older versions.
                ?.remove("systemfont_pending_reset")
                ?.remove("systemfont_pending_reset_at")
                ?.commit()
        }
    }

    /** Monotonic library/config version; the font page refreshes on change. */
    fun bumpRevision(context: Context) {
        runCatching {
            val next = local(context).getInt(KEY_REVISION, 0) + 1
            local(context).edit().putInt(KEY_REVISION, next).apply()
            remote(context)?.edit()?.putInt(KEY_REVISION, next)?.commit()
        }
    }

    fun revision(module: XposedModule): Int = runCatching {
        module.getRemotePreferences(PREFS_NAME).getInt(KEY_REVISION, 0)
    }.getOrDefault(0)

    private fun local(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun remote(context: Context) =
        XposedServiceManager.getRemotePreferences(PREFS_NAME)
}
