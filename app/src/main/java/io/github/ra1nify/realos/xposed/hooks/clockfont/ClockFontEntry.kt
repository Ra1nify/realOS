package io.github.ra1nify.realos.xposed.hooks.clockfont

import io.github.ra1nify.realos.clockfont.ClockFontContract
import io.github.ra1nify.realos.clockfont.ClockFontType

/** Host clock-font entry. */
internal data class ClockFontEntry(
    val id: String,
    val displayName: String,
    val type: ClockFontType,
    val dynamicWeight: Boolean = true,
    val hidden: Boolean = false
) {
    val value: String get() = ClockFontContract.valueFor(id)
}
