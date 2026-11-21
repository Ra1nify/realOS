package io.github.ra1nify.realos.xposed.hooks.launcher.recents

import android.content.Context
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.util.ModuleResources

/**
 * Recents style names: host-provided "Stacked / Tiled" plus this module's "List".
 *
 * The selector renders in the launcher process, so the names come from the module APK resources
 * through [ModuleResources].
 */
internal object RecentsStyleText {

    fun stacked(context: Context): String =
        ModuleResources.string(context, R.string.launcher_recents_style_stacked)

    fun tiled(context: Context): String =
        ModuleResources.string(context, R.string.launcher_recents_style_tiled)

    fun slimList(context: Context): String =
        ModuleResources.string(context, R.string.launcher_recents_style_list)
}
