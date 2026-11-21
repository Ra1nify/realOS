package io.github.ra1nify.realos.util

import android.content.Context
import android.util.Log as AndroidLog
import io.github.libxposed.api.XposedModule

/**
 * Cross-process storage for the global log level.
 *
 * This object is intentionally free of app-service dependencies: host processes
 * load it from `RealOSLog`, and must not initialize the app-side Xposed service.
 */
object RealOSLogConfig {

    private const val TAG = "RealOS-LogConfig"

    const val PREFS_NAME = "app_config"
    const val KEY_LOG_LEVEL = "realos_log_level"

    /** App-process read. */
    fun getLevel(context: Context): LogLevel = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LOG_LEVEL, LogLevel.NONE.value)
            .let(LogLevel::fromValue)
    }.getOrDefault(LogLevel.NONE)

    /** App-process local write; the remote push lives in [RealOSLogSettings]. */
    fun writeLocal(context: Context, level: LogLevel) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LOG_LEVEL, level.value)
                .apply()
        }.onFailure { AndroidLog.w(TAG, "write local log level failed", it) }
    }

    /** Host-process read; the caller applies its own refresh policy. */
    fun readRemote(module: XposedModule): LogLevel = runCatching {
        module.getRemotePreferences(PREFS_NAME)
            .getInt(KEY_LOG_LEVEL, LogLevel.NONE.value)
            .let(LogLevel::fromValue)
    }.getOrDefault(LogLevel.NONE)
}
