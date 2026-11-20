package io.github.ra1nify.realos.xposed.hooks.camera

import android.app.Application
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * SpecLoader entry point for the ColorOS camera (com.oplus.camera).
 *
 * Status: all legacy feature hooks are removed; the module is rebuilt on the new architecture.
 * Installs two hooks: realOS tab injection ([OplusCameraTabHook]) and settings page system theme follow ([OplusCameraThemeHook]).
 * New hooks go through the symbol resolver and fail closed; no speculative hooks are installed here (see AGENTS.md §3.1 / §7).
 */
object OplusCameraSpec : SpecLoader {

    private const val TAG = "RealOS-CameraSpec"

    override val packageNames: Set<String> = SpecPackages.OPLUS_CAMERA_VARIANTS

    override fun onPackageReady(module: XposedModule, packageName: String, classLoader: ClassLoader) {
        if (packageName !in SpecPackages.OPLUS_CAMERA_VARIANTS) return
        module.log(Log.INFO, TAG, "onPackageReady: $packageName")
        try {
            OplusCameraTabHook.install(module, classLoader)
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "Failed to install OplusCameraTabHook", t)
        }
        try {
            OplusCameraThemeHook.install(module)
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "Failed to install OplusCameraThemeHook", t)
        }
    }

    override fun onAppStart(module: XposedModule, app: Application) {
        if (app.packageName !in SpecPackages.OPLUS_CAMERA_VARIANTS) return
        module.log(Log.INFO, TAG, "onAppStart: ${app.packageName}")
        try {
            OplusCameraThemeHook.primeSystemNight(app)
        } catch (t: Throwable) {
            module.log(Log.WARN, TAG, "Failed to prime system night mode", t)
        }
    }
}
