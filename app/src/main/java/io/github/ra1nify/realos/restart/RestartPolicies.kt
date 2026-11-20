package io.github.ra1nify.realos.restart

import android.content.Context
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.opluskeyboard.OplusKeyboardManager
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * Per-page restart policies.
 *
 * Single source of truth for the restart button, mirroring `restart.mode`:
 * - camera / wallet / MediaProvider: force-stop one package;
 * - launcher: force-stop plus HOME;
 * - settings: force-stop Settings and Wallpapers;
 * - SystemUI: pkill (persistent process, no stopped flag);
 * - keyboard: force-stop the installed keyboard package;
 * - system framework: no restart action.
 */
object RestartPolicies {

    val camera = RestartPolicy(
        mode = RestartMode.FORCE_STOP,
        packages = listOf(SpecPackages.OPLUS_CAMERA),
        successMessageRes = R.string.restart_success_camera
    )

    val wallet = RestartPolicy(
        mode = RestartMode.FORCE_STOP,
        packages = listOf(SpecPackages.WALLET),
        successMessageRes = R.string.restart_success_wallet
    )

    val launcher = RestartPolicy(
        mode = RestartMode.FORCE_STOP,
        packages = listOf(SpecPackages.LAUNCHER),
        relaunchHome = true,
        successMessageRes = R.string.restart_success_launcher
    )

    val settings = RestartPolicy(
        mode = RestartMode.FORCE_STOP,
        packages = listOf(SpecPackages.SETTINGS, SpecPackages.WALLPAPERS),
        successMessageRes = R.string.restart_success_settings
    )

    val systemUi = RestartPolicy(
        mode = RestartMode.PKILL,
        packages = listOf(SpecPackages.SYSTEMUI),
        successMessageRes = R.string.restart_success_systemui
    )

    val mediaProvider = RestartPolicy(
        mode = RestartMode.FORCE_STOP,
        packages = listOf(SpecPackages.MEDIA_PROVIDER),
        successMessageRes = R.string.restart_success_media_provider
    )

    /**
     * Clock-font page: Font list lives in Wallpapers; the widget host is Launcher;
     * AOD has its own process. SystemUI is restarted separately by the page with
     * [systemUi] because it is persistent and needs pkill rather than force-stop.
     */
    val clockFont = RestartPolicy(
        mode = RestartMode.FORCE_STOP,
        packages = listOf(
            SpecPackages.WALLPAPERS,
            SpecPackages.LAUNCHER,
            SpecPackages.AOD
        ),
        relaunchHome = true,
        successMessageRes = R.string.restart_success_clock_font
    )

    val systemFramework = RestartPolicy(mode = RestartMode.NONE)

    /** The keyboard host package is resolved at runtime; the fallback is the standard package. */
    fun keyboard(context: Context): RestartPolicy =
        keyboard(OplusKeyboardManager.getInstalledKeyboardPackage(context))

    fun keyboard(packageName: String): RestartPolicy = RestartPolicy(
        mode = RestartMode.FORCE_STOP,
        packages = listOf(packageName),
        successMessageRes = R.string.oplus_input_restart_success
    )
}
