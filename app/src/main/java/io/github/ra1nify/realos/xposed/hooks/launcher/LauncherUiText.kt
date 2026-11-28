package io.github.ra1nify.realos.xposed.hooks.launcher

import android.content.Context
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.util.ModuleResources

/**
 * User-visible text injected into the host settings page.
 *
 * Resolved from the module APK resources through [ModuleResources], so the injected rows follow
 * the module translation set rather than the host table.
 */
object LauncherUiText {

    fun realMemoryTitle(context: Context): String =
        ModuleResources.string(context, R.string.launcher_real_memory_title)

    fun realMemorySummary(context: Context): String =
        ModuleResources.string(context, R.string.launcher_real_memory_summary)

    fun dockEntryTitle(context: Context): String =
        ModuleResources.string(context, R.string.launcher_dock_entry_title)

    fun dockEntrySummary(context: Context): String =
        ModuleResources.string(context, R.string.launcher_dock_entry_summary)

    /** "available" suffix of the official text (such as `12.3 GB available | 16 GB`) */
    fun availableSuffix(context: Context): String =
        ModuleResources.string(context, R.string.launcher_memory_available_suffix)
}
