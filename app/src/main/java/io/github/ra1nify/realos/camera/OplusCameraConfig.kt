package io.github.ra1nify.realos.camera

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.service.XposedServiceManager
import io.github.ra1nify.realos.opluskeyboard.OplusKeyboardManager

/**
 * ColorOS camera module configuration read/write (app side).
 *
 * Cross-process contract: the host process reads `camera_config` through [XposedModule.getRemotePreferences].
 * Remote preferences do not refresh from plain SharedPreferences writes;
 * a write through [XposedServiceManager.getRemotePreferences] is required for the host to see new values
 * (same approach as the keyboard module OplusKeyboardManager).
 *
 * Lives in `realos.camera`, not `xposed.hooks.camera`: with `realos.scope.camera=0`
 * that directory is excluded from compilation, and the UI references this class directly.
 */
object OplusCameraConfig {

    private const val PREFS_NAME = "camera_config"

    /** Whether the realOS tab is injected into the camera settings top bar. */
    const val KEY_REALOS_TAB_ENABLED = "camera_realos_tab_enabled"

    private const val DEFAULT_REALOS_TAB_ENABLED = false

    // ── Host process reads (latest value on each call) ──

    fun isRealosTabEnabled(module: XposedModule): Boolean =
        readRemote(module, KEY_REALOS_TAB_ENABLED, DEFAULT_REALOS_TAB_ENABLED)

    // ── realOS app-side read/write ──

    fun isRealosTabEnabled(context: Context): Boolean =
        readLocal(context, KEY_REALOS_TAB_ENABLED, DEFAULT_REALOS_TAB_ENABLED)

    fun setRealosTabEnabled(context: Context, enabled: Boolean) =
        writeBoth(context, KEY_REALOS_TAB_ENABLED, enabled)

    /**
     * Copies the local value into remote preferences.
     *
     * Runs once when the detail page opens; overwrites values left stale by versions that wrote only local SP.
     */
    fun syncToRemote(context: Context) {
        pushToRemote(KEY_REALOS_TAB_ENABLED, isRealosTabEnabled(context))
    }

    // ── Internals ──

    private fun readRemote(module: XposedModule, key: String, default: Boolean): Boolean = runCatching {
        module.getRemotePreferences(PREFS_NAME).getBoolean(key, default)
    }.getOrDefault(default)

    private fun readLocal(context: Context, key: String, default: Boolean): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(key, default)
    }.getOrDefault(default)

    private fun writeBoth(context: Context, key: String, enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, enabled)
                .apply()
        }
        pushToRemote(key, enabled)
    }

    private fun pushToRemote(key: String, enabled: Boolean) {
        runCatching {
            XposedServiceManager.getRemotePreferences(PREFS_NAME)
                ?.edit()
                ?.putBoolean(key, enabled)
                ?.apply()
        }
    }
}
