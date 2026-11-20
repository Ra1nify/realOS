package io.github.ra1nify.realos.util

import android.util.Log as AndroidLog

/**
 * Global realOS log level.
 *
 * The numeric value is persisted in `app_config`; [minPriority] maps it to
 * `android.util.Log` priorities. NONE suppresses every call.
 */
enum class LogLevel(val value: Int, val minPriority: Int) {
    NONE(0, Int.MAX_VALUE),
    ERROR(1, AndroidLog.ERROR),
    WARN(2, AndroidLog.WARN),
    INFO(3, AndroidLog.INFO),
    DEBUG(4, AndroidLog.DEBUG),
    VERBOSE(5, AndroidLog.VERBOSE);

    companion object {
        fun fromValue(value: Int): LogLevel = entries.firstOrNull { it.value == value } ?: NONE
    }
}
