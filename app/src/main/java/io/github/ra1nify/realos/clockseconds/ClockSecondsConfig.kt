package io.github.ra1nify.realos.clockseconds

import android.content.Context
import io.github.ra1nify.realos.service.XposedServiceManager

/** App-side switch for the clock-seconds conversion. */
object ClockSecondsConfig {

    fun isEnabled(context: Context): Boolean = runCatching {
        local(context).getBoolean(
            ClockSecondsContract.REMOTE_KEY_ENABLED,
            ClockSecondsContract.DEFAULT_ENABLED
        )
    }.getOrDefault(ClockSecondsContract.DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        runCatching {
            local(context).edit()
                .putBoolean(ClockSecondsContract.REMOTE_KEY_ENABLED, enabled)
                .remove(ClockSecondsContract.LEGACY_REMOTE_KEY_ENABLED)
                .apply()
            remote(context)?.edit()
                ?.putBoolean(ClockSecondsContract.REMOTE_KEY_ENABLED, enabled)
                ?.remove(ClockSecondsContract.LEGACY_REMOTE_KEY_ENABLED)
                ?.commit()
        }
    }

    /** Mirrors the local switch value to the Settings process. */
    fun syncToRemote(context: Context) {
        runCatching {
            remote(context)?.edit()
                ?.putBoolean(ClockSecondsContract.REMOTE_KEY_ENABLED, isEnabled(context))
                ?.remove(ClockSecondsContract.LEGACY_REMOTE_KEY_ENABLED)
                ?.commit()
        }
    }

    private fun local(context: Context) =
        context.getSharedPreferences(ClockSecondsContract.REMOTE_PREFS, Context.MODE_PRIVATE)

    private fun remote(context: Context) =
        XposedServiceManager.getRemotePreferences(ClockSecondsContract.REMOTE_PREFS)
}
