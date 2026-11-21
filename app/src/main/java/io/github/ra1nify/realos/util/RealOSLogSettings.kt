package io.github.ra1nify.realos.util

import android.content.Context
import android.util.Log as AndroidLog
import io.github.ra1nify.realos.service.XposedServiceManager

/**
 * App-side writer for the global log level.
 *
 * Kept separate from [RealOSLogConfig] so host processes can read the level
 * without loading the app's Xposed service manager.
 */
object RealOSLogSettings {

    private const val TAG = "RealOS-LogSettings"

    fun setLevel(context: Context, level: LogLevel) {
        RealOSLogConfig.writeLocal(context, level)
        runCatching {
            XposedServiceManager.getRemotePreferences(RealOSLogConfig.PREFS_NAME)
                ?.edit()
                ?.putInt(RealOSLogConfig.KEY_LOG_LEVEL, level.value)
                ?.apply()
        }.onFailure { AndroidLog.w(TAG, "write remote log level failed", it) }
    }
}
