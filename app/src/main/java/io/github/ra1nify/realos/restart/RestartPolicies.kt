package io.github.ra1nify.realos.restart

import android.content.Context
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.opluskeyboard.OplusKeyboardManager
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * Per-page restart targets.
 *
 * The command per host is resolved from [RestartConfig]. The project default
 * and per-host overrides live in app/build.gradle.kts.
 * - SystemUI uses pkill by default;
 * - system framework has no restart target.
 */
object RestartPolicies {

    val camera = RestartPolicy(
        packages = listOf(SpecPackages.OPLUS_CAMERA),
        successMessageRes = R.string.restart_success_camera
    )

    val wallet = RestartPolicy(
        packages = listOf(SpecPackages.WALLET),
        successMessageRes = R.string.restart_success_wallet
    )

    val launcher = RestartPolicy(
        packages = listOf(SpecPackages.LAUNCHER),
        relaunchHome = true,
        successMessageRes = R.string.restart_success_launcher
    )

    val settings = RestartPolicy(
        packages = listOf(SpecPackages.SETTINGS, SpecPackages.WALLPAPERS),
        successMessageRes = R.string.restart_success_settings
    )

    /** Font-only Settings page; Wallpapers is not part of this business. */
    val settingsFont = RestartPolicy(
        packages = listOf(SpecPackages.SETTINGS),
        successMessageRes = R.string.restart_success_settings
    )

    val systemUi = RestartPolicy(
        packages = listOf(SpecPackages.SYSTEMUI),
        successMessageRes = R.string.restart_success_systemui
    )

    val mediaProvider = RestartPolicy(
        packages = listOf(SpecPackages.MEDIA_PROVIDER),
        successMessageRes = R.string.restart_success_media_provider
    )

    /**
     * Clock-font page: the font list lives in Wallpapers, the widget host is
     * Launcher, and AOD has its own process. SystemUI resolves to pkill by
     * default; the other targets use the configured default mode.
     */
    val clockFont = RestartPolicy(
        packages = listOf(
            SpecPackages.WALLPAPERS,
            SpecPackages.LAUNCHER,
            SpecPackages.AOD,
            SpecPackages.SYSTEMUI
        ),
        relaunchHome = true,
        successMessageRes = R.string.restart_success_clock_font
    )

    /** System framework is system_server; it has no restart target. */
    val systemFramework = RestartPolicy()

    /** The keyboard host package is resolved at runtime; the fallback is the standard package. */
    fun keyboard(context: Context): RestartPolicy =
        keyboard(OplusKeyboardManager.getInstalledKeyboardPackage(context))

    fun keyboard(packageName: String): RestartPolicy = RestartPolicy(
        packages = listOf(packageName),
        successMessageRes = R.string.oplus_input_restart_success
    )
}
