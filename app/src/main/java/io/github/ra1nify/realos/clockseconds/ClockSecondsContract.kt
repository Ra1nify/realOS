package io.github.ra1nify.realos.clockseconds

/** Shared app/host contract for converting the native five-minute clock-seconds entry. */
object ClockSecondsContract {
    const val REMOTE_PREFS = "clock_seconds_config"
    const val REMOTE_KEY_ENABLED = "clock_seconds_persistent_enabled"

    /** Legacy key removed on sync. */
    const val LEGACY_REMOTE_KEY_ENABLED = "clock_seconds_always_enabled"

    /** Default switch state. */
    const val DEFAULT_ENABLED = true
}
