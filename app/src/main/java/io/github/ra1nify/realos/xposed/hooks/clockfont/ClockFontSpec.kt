package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.app.Application
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * ColorOS shared clock-font spec.
 *
 * Full multi-host business hooks (Wallpapers, SystemUI, Launcher, AOD).
 * Diagnostic probe installation and logs are unified under RealOSLog (DEBUG level).
 */
object ClockFontSpec : SpecLoader {

    private const val TAG = "RealOS-ClockFontSpec"

    override val packageNames: Set<String> = SpecPackages.CLOCK_FONT_HOSTS

    override fun onPackageReady(
        module: XposedModule,
        packageName: String,
        classLoader: ClassLoader,
        sourceDir: String?
    ) {
        Log.d(TAG, "onPackageReady pkg=$packageName")
        ClockFontPluginHook.install(module)
        if (Log.isEnabled(Log.DEBUG)) {
            ClockFontLoaderProbe.install(module)
        }
    }

    override fun onAppStart(module: XposedModule, app: Application) {
        Log.d(TAG, "onAppStart pkg=${app.packageName}")
        ClockFontPluginHook.install(module)
        if (Log.isEnabled(Log.DEBUG)) {
            ClockFontLoaderProbe.install(module)
        }
    }

    override fun onHotReloadCleanup() {
        ClockFontLoaderProbe.reset()
        ClockFontPluginHook.reset()
    }

    override fun onHotReload(module: XposedModule, app: Application) {
        Log.d(TAG, "onHotReload pkg=${app.packageName}")
        ClockFontLoaderProbe.reset()
        ClockFontPluginHook.reset()
        ClockFontPluginHook.install(module)
        if (Log.isEnabled(Log.DEBUG)) {
            ClockFontLoaderProbe.install(module)
        }
    }
}
