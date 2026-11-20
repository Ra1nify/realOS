package io.github.ra1nify.realos.mediaprovider

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.service.XposedServiceManager

/**
 * MediaProvider (com.android.providers.media.module) configuration.
 *
 * Cross-process: the host process reads `mediaprovider_config` through
 * [XposedModule.getRemotePreferences]; the app process reads its local
 * SharedPreferences. A normal SharedPreferences write does not update remote
 * preferences, so [setPickerRefreshEnabled] also writes through
 * [XposedServiceManager.getRemotePreferences].
 */
object MediaProviderConfig {

    private const val PREFS_NAME = "mediaprovider_config"

    const val KEY_PICKER_REFRESH_ENABLED = "mediaprovider_picker_refresh_enabled"

    const val DEFAULT_PICKER_REFRESH_ENABLED = true

    /** Host process read; returns the latest value on every call. */
    fun isPickerRefreshEnabled(module: XposedModule): Boolean = runCatching {
        module.getRemotePreferences(PREFS_NAME)
            .getBoolean(KEY_PICKER_REFRESH_ENABLED, DEFAULT_PICKER_REFRESH_ENABLED)
    }.getOrDefault(DEFAULT_PICKER_REFRESH_ENABLED)

    /** realOS app-side read. */
    fun isPickerRefreshEnabled(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PICKER_REFRESH_ENABLED, DEFAULT_PICKER_REFRESH_ENABLED)
    }.getOrDefault(DEFAULT_PICKER_REFRESH_ENABLED)

    /** realOS app-side write: local and remote preferences. */
    fun setPickerRefreshEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PICKER_REFRESH_ENABLED, enabled)
                .apply()
        }
        pushToRemote(enabled)
    }

    /** Copies the local value into remote preferences. Runs on detail page entry. */
    fun syncToRemote(context: Context) {
        pushToRemote(isPickerRefreshEnabled(context))
    }

    private fun pushToRemote(enabled: Boolean) {
        runCatching {
            XposedServiceManager.getRemotePreferences(PREFS_NAME)
                ?.edit()
                ?.putBoolean(KEY_PICKER_REFRESH_ENABLED, enabled)
                ?.apply()
        }
    }
}
