package io.github.ra1nify.realos.restart

/**
 * Restart strategy for a scoped host.
 *
 * The numeric values mirror the `restart.mode` property:
 * 0 = no restart support, 1 = `am force-stop`, 2 = `pkill -f`.
 */
enum class RestartMode(val value: Int) {
    NONE(0),
    FORCE_STOP(1),
    PKILL(2);

    companion object {
        fun fromValue(value: Int): RestartMode? = entries.firstOrNull { it.value == value }
    }
}
