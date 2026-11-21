package io.github.ra1nify.realos.navigation

/**
 * Action and page identifiers for the "System navigation method" subpage.
 *
 * Shared by the app side (launching the Settings subpage) and the host-side hook
 * (parsing action and page extras). It sits outside the scope-trimmed packages:
 * `xposed/hooks/navigation` is excluded entirely when `realos.scope.navigation=0`.
 */
object SettingsNavContract {

    const val ACTION_BUTTON = "io.github.ra1nify.realos.settings.action.NAV_BUTTON"
    const val ACTION_BUTTON_GLASS = "io.github.ra1nify.realos.settings.action.NAV_BUTTON_GLASS"
    const val ACTION_VIBRATION = "io.github.ra1nify.realos.settings.action.NAV_VIBRATION"
    const val ACTION_BUTTON_VIBRATION = "io.github.ra1nify.realos.settings.action.NAV_BUTTON_VIBRATION"
    const val ACTION_APPEARANCE = "io.github.ra1nify.realos.settings.action.NAV_APPEARANCE"
    const val ACTION_ADVANCED = "io.github.ra1nify.realos.settings.action.NAV_ADVANCED"
    const val ACTION_ADVANCED_MISC = "io.github.ra1nify.realos.settings.action.NAV_ADVANCED_MISC"

    const val EXTRA_PAGE_TYPE = "extra_page_type"
    const val EXTRA_SHOW_FRAGMENT = ":settings:show_fragment"
    const val EXTRA_SHOW_FRAGMENT_AS_SUBSETTING = ":settings:show_fragment_as_subsetting"

    const val PAGE_BUTTON = "nav_button"
    const val PAGE_BUTTON_GLASS = "nav_button_glass"
    const val PAGE_VIBRATION = "nav_vibration"
    const val PAGE_BUTTON_VIBRATION = "nav_button_vibration"
    const val PAGE_APPEARANCE = "nav_appearance"
    const val PAGE_ADVANCED = "nav_advanced"
    const val PAGE_ADVANCED_MISC = "nav_advanced_misc"
}
