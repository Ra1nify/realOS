package io.github.ra1nify.realos.fido

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.service.XposedServiceManager

/**
 * FIDO2 QR scan redirect configuration.
 *
 * Cross-process: system_server reads `fido_config` through
 * [XposedModule.getRemotePreferences]; the app process reads its local
 * SharedPreferences. A normal SharedPreferences write does not update remote
 * preferences, so [setScanRedirectEnabled] also writes through
 * [XposedServiceManager.getRemotePreferences].
 */
object FidoRedirectConfig {

    private const val PREFS_NAME = "fido_config"

    const val KEY_SCAN_REDIRECT_ENABLED = "fido_scan_redirect_enabled"

    const val DEFAULT_SCAN_REDIRECT_ENABLED = true

    /** system_server read; returns the latest value on every call. */
    fun isScanRedirectEnabled(module: XposedModule): Boolean = runCatching {
        module.getRemotePreferences(PREFS_NAME)
            .getBoolean(KEY_SCAN_REDIRECT_ENABLED, DEFAULT_SCAN_REDIRECT_ENABLED)
    }.getOrDefault(DEFAULT_SCAN_REDIRECT_ENABLED)

    /** realOS app-side read. */
    fun isScanRedirectEnabled(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SCAN_REDIRECT_ENABLED, DEFAULT_SCAN_REDIRECT_ENABLED)
    }.getOrDefault(DEFAULT_SCAN_REDIRECT_ENABLED)

    /** realOS app-side write: local and remote preferences. */
    fun setScanRedirectEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SCAN_REDIRECT_ENABLED, enabled)
                .apply()
        }
        pushToRemote(enabled)
    }

    /** Copies the local value into remote preferences. Runs on detail page entry. */
    fun syncToRemote(context: Context) {
        pushToRemote(isScanRedirectEnabled(context))
    }

    private fun pushToRemote(enabled: Boolean) {
        runCatching {
            XposedServiceManager.getRemotePreferences(PREFS_NAME)
                ?.edit()
                ?.putBoolean(KEY_SCAN_REDIRECT_ENABLED, enabled)
                ?.apply()
        }
    }
}
