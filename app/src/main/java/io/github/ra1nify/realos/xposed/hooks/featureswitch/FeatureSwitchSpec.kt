package io.github.ra1nify.realos.xposed.hooks.featureswitch

import android.app.Application
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.featureswitch.FeatureSwitchConfig
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * SpecLoader implementation for Feature Switch (Staging).
 *
 * Targets com.android.settings and com.oplus.wallpapers to hook
 * feature discovery query methods without modifying target UI layouts.
 */
object FeatureSwitchSpec : SpecLoader {

    private const val TAG = "RealOS-FeatureSwitchSpec"

    override val packageNames: Set<String> = setOf(
        SpecPackages.SETTINGS,
        SpecPackages.WALLPAPERS
    )

    override fun onPackageReady(
        module: XposedModule,
        packageName: String,
        classLoader: ClassLoader,
        sourceDir: String?
    ) {
        module.log(Log.INFO, TAG, "onPackageReady: $packageName")
        FeatureSwitchConfig.prime(module)

        when (packageName) {
            SpecPackages.SETTINGS -> {
                FeatureSwitchHooks.installSettingsHooks(module, classLoader)
            }
            SpecPackages.WALLPAPERS -> {
                FeatureSwitchHooks.installWallpapersHooks(module, classLoader)
            }
        }
    }

    override fun onPackageReady(module: XposedModule, packageName: String, classLoader: ClassLoader) {
        onPackageReady(module, packageName, classLoader, null)
    }

    override fun onPackageReady(module: XposedModule, classLoader: ClassLoader) {
        onPackageReady(module, SpecPackages.SETTINGS, classLoader, null)
    }

    override fun onAppStart(module: XposedModule, app: Application) {
        module.log(Log.INFO, TAG, "onAppStart: ${app.packageName}")
    }

    override fun onHotReloadCleanup() {
        FeatureSwitchHooks.reset()
    }

    override fun onHotReload(module: XposedModule, app: Application) {
        onHotReloadCleanup()
        onPackageReady(module, app.packageName, app.classLoader, app.applicationInfo?.sourceDir)
        onAppStart(module, app)
    }
}
