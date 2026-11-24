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
        Log.i(TAG, "HookPoint[clockfont_spec_package_ready] state=FOUND pkg=$packageName")
        ClockFontPluginHook.install(module, packageName)
        // Resolve SystemUI symbols in onAppStart.
        if (Log.isEnabled(Log.DEBUG)) {
            ClockFontLoaderProbe.install(module)
        }
    }

    override fun onAppStart(module: XposedModule, app: Application) {
        Log.i(TAG, "HookPoint[clockfont_spec_app_start] state=FOUND pkg=${app.packageName}")
        // Seed remote metadata before the plugin loads.
        ClockFontHost.attach(module, app)
        ClockFontPluginHook.install(module, app.packageName)
        if (app.packageName == SpecPackages.SYSTEMUI) {
            SystemClockFontHook.install(
                module,
                app.classLoader,
                app,
                app.applicationInfo.sourceDir
            )
        }
        if (Log.isEnabled(Log.DEBUG)) {
            ClockFontLoaderProbe.install(module)
        }
    }

    override fun onHotReloadCleanup() {
        ClockFontLoaderProbe.reset()
        ClockFontPluginHook.reset()
        SystemClockFontHook.reset()
    }

    override fun onHotReload(module: XposedModule, app: Application) {
        Log.d(TAG, "onHotReload pkg=${app.packageName}")
        ClockFontLoaderProbe.reset()
        ClockFontPluginHook.reset()
        SystemClockFontHook.reset()
        ClockFontHost.attach(module, app)
        ClockFontPluginHook.install(module, app.packageName)
        if (app.packageName == SpecPackages.SYSTEMUI) {
            SystemClockFontHook.install(
                module,
                app.classLoader,
                app,
                app.applicationInfo.sourceDir
            )
        }
        if (Log.isEnabled(Log.DEBUG)) {
            ClockFontLoaderProbe.install(module)
        }
    }
}
