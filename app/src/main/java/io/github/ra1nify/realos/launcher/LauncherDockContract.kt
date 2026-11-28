package io.github.ra1nify.realos.launcher

import io.github.ra1nify.realos.BuildConfig

/**
 * Shared contract between the launcher-settings preference entry and the standalone page.
 *
 * The hook injects a jump preference into the launcher settings page that starts
 * [DOCK_PAGE_ACTIVITY]; the page reads/writes [LauncherConfig] directly.
 */
object LauncherDockContract {
    val REALOS_PACKAGE = BuildConfig.APPLICATION_ID
    const val DOCK_PAGE_ACTIVITY = "io.github.ra1nify.realos.launcher.LauncherDockPageActivity"
}
