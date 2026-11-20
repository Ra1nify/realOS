package io.github.ra1nify.realos.opluskeyboard

/**
 * Keyboard settings page IDs and Intent protocol.
 *
 * Shared by the app side (launching host pages) and the host-side hook
 * (parsing page arguments).
 */
object KeyboardPagesContract {

    const val ACTION_LIQUID_GLASS = "io.github.ra1nify.realos.oplus.action.LIQUID_GLASS"
    const val ACTION_WORKSHOP = "io.github.ra1nify.realos.oplus.action.WORKSHOP"
    const val ACTION_KEYBOARD_EFFECTS = "io.github.ra1nify.realos.oplus.action.KEYBOARD_EFFECTS"
    const val ACTION_KEYBOARD_SOUNDS = "io.github.ra1nify.realos.oplus.action.KEYBOARD_SOUNDS"
    const val ACTION_ENHANCEMENTS = "io.github.ra1nify.realos.oplus.action.ENHANCEMENTS"

    const val EXTRA_PAGE_TYPE = "extra_page_type"

    const val PAGE_LIQUID_GLASS = "liquid_glass"
    const val PAGE_WORKSHOP = "workshop"
    const val PAGE_KEYBOARD_EFFECTS = "keyboard_effects"
    const val PAGE_KEYBOARD_SOUNDS = "keyboard_sounds"
    const val PAGE_ENHANCEMENTS = "enhancements"
}
