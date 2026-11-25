package io.github.ra1nify.realos.xposed.hooks.clockseconds

import android.app.Application
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/** Settings host spec for the clock-seconds conversion. */
object ClockSecondsSpec : SpecLoader {

    private const val TAG = "RealOS-ClockSecondsSpec"

    override val packageNames: Set<String> = SpecPackages.SETTINGS_VARIANTS

    override fun onPackageReady(
        module: XposedModule,
        packageName: String,
        classLoader: ClassLoader,
        sourceDir: String?
    ) {
        Log.i(TAG, "HookPoint[clock_seconds_spec_package_ready] state=FOUND pkg=$packageName")
    }

    override fun onAppStart(module: XposedModule, app: Application) {
        if (app.packageName != SpecPackages.SETTINGS) return
        Log.i(TAG, "HookPoint[clock_seconds_spec_app_start] state=FOUND pkg=${app.packageName}")
        ClockSecondsSettingsHook.install(module, app)
    }

    override fun onHotReloadCleanup() {
        ClockSecondsSettingsHook.reset()
    }

    override fun onHotReload(module: XposedModule, app: Application) {
        ClockSecondsSettingsHook.reset()
        onAppStart(module, app)
    }
}
