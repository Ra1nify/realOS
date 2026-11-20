package io.github.ra1nify.realos.xposed.hooks.camera

import android.content.Context
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.util.ModuleResources

/**
 * User-visible text on the realOS camera tab.
 *
 * The tab renders in the camera process, so strings are resolved from the module APK resources
 * through [ModuleResources] instead of the host `Resources`, which would resolve module ids
 * against the host table.
 */
internal object OplusCameraTabText {
    const val TAB_LABEL = "realOS"

    fun appearanceSection(context: Context): String =
        ModuleResources.string(context, R.string.camera_realos_tab_appearance)

    fun appearanceFooter(context: Context): String =
        ModuleResources.string(context, R.string.camera_realos_tab_appearance_footer)

    fun themeFollowTitle(context: Context): String =
        ModuleResources.string(context, R.string.camera_realos_tab_theme_follow_title)

    fun themeFollowSummary(context: Context): String =
        ModuleResources.string(context, R.string.camera_realos_tab_theme_follow_summary)
}
