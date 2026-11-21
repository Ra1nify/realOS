package io.github.ra1nify.realos.font

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.service.XposedServiceManager

/**
 * Configuration for the realOS system font entry in Settings.
 *
 * The Settings-process hook reads `systemfont_config` through
 * [XposedModule.getRemotePreferences]; the app process reads its local
 * SharedPreferences. Writes are mirrored to remote preferences so the hook can
 * see them without a restart.
 */
object SystemFontConfig {

    private const val PREFS_NAME = "systemfont_config"

    const val KEY_ENABLED = "systemfont_settings_entry_enabled"

    const val DEFAULT_ENABLED = true

    fun isEnabled(module: XposedModule): Boolean = runCatching {
        module.getRemotePreferences(PREFS_NAME).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
    }.getOrDefault(DEFAULT_ENABLED)

    fun isEnabled(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
    }.getOrDefault(DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
        runCatching {
            XposedServiceManager.getRemotePreferences(PREFS_NAME)
                ?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        }
    }

    fun syncToRemote(context: Context) {
        val enabled = isEnabled(context)
        runCatching {
            XposedServiceManager.getRemotePreferences(PREFS_NAME)
                ?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        }
    }
}
