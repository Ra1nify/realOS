package io.github.ra1nify.realos.intentroute

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.service.XposedServiceManager

/**
 * Link resolution alignment configuration.
 *
 * Cross-process: system_server reads `intentroute_config` through
 * [XposedModule.getRemotePreferences]; the app process reads its local
 * SharedPreferences. A normal SharedPreferences write does not update remote
 * preferences, so [setAlignLinksEnabled] also writes through
 * [XposedServiceManager.getRemotePreferences].
 */
object IntentRouteConfig {

    private const val PREFS_NAME = "intentroute_config"

    const val KEY_ALIGN_LINKS_ENABLED = "intentroute_align_links_enabled"

    const val DEFAULT_ALIGN_LINKS_ENABLED = true

    /** system_server read; returns the latest value on every call. */
    fun isAlignLinksEnabled(module: XposedModule): Boolean = runCatching {
        module.getRemotePreferences(PREFS_NAME)
            .getBoolean(KEY_ALIGN_LINKS_ENABLED, DEFAULT_ALIGN_LINKS_ENABLED)
    }.getOrDefault(DEFAULT_ALIGN_LINKS_ENABLED)

    /** realOS app-side read. */
    fun isAlignLinksEnabled(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALIGN_LINKS_ENABLED, DEFAULT_ALIGN_LINKS_ENABLED)
    }.getOrDefault(DEFAULT_ALIGN_LINKS_ENABLED)

    /** realOS app-side write: local and remote preferences. */
    fun setAlignLinksEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ALIGN_LINKS_ENABLED, enabled)
                .apply()
        }
        pushToRemote(enabled)
    }

    /** Copies the local value into remote preferences. Runs on detail page entry. */
    fun syncToRemote(context: Context) {
        pushToRemote(isAlignLinksEnabled(context))
    }

    private fun pushToRemote(enabled: Boolean) {
        runCatching {
            XposedServiceManager.getRemotePreferences(PREFS_NAME)
                ?.edit()
                ?.putBoolean(KEY_ALIGN_LINKS_ENABLED, enabled)
                ?.apply()
        }
    }
}
