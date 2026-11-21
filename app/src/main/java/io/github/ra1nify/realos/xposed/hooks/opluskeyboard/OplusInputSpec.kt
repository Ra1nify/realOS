package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.app.Application
import io.github.ra1nify.realos.xposed.loader.HookLoader
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog
import io.github.ra1nify.realos.opluskeyboard.OplusModuleRef

/**
 * SpecLoader entry point for OPPO Keyboard (Xiaobu Input Method).
 */
object OplusInputSpec : SpecLoader {

    private const val TAG = "RealOS-OplusInputSpec"

    override val packageNames: Set<String> = SpecPackages.OPLUS_KEYBOARD_VARIANTS + SpecPackages.ANDROID_VARIANTS

    override fun onSystemServerStarting(module: XposedModule, classLoader: ClassLoader) {
        OplusInputSystemServerHook.install(module, classLoader)
    }

    override fun onPackageReady(module: XposedModule, packageName: String, classLoader: ClassLoader) {
        OplusModuleRef.set(module)
        OplusInputSystemServerHook.install(module, classLoader)
        if (packageName in SpecPackages.ANDROID_VARIANTS) {
            return
        }

        // Install autofill hooks as early as possible so onCreateInlineSuggestionsRequest is never missed
        OplusInputAutofillHook.install(module, packageName, classLoader)

        // Install settings hook into SettingsHomeFragment and DocumentEmptyActivity
        OplusInputSettingsHook.install(module, classLoader)

        // The Application.onCreate hook that normally drives onAppStart can be installed after
        // this process already created its Application (IME services start very fast), leaving
        // the whole liquid-glass / key-alpha path uninstalled. Post one deferred dispatch on the
        // main thread, where ActivityThread.currentApplication() is guaranteed to be set.
        RealOSLog.i(TAG, "onPackageReady: scheduling deferred app-start dispatch for $packageName")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                val activityThread = Class.forName("android.app.ActivityThread")
                val app = activityThread.getMethod("currentApplication").invoke(null)
                    as? android.app.Application
                if (app != null && app.packageName == packageName) {
                    RealOSLog.i(
                        TAG,
                        "deferred app-start dispatch running for ${app.packageName}"
                    )
                    HookLoader.dispatchAppStart(module, app)
                } else {
                    RealOSLog.w(TAG, "deferred app-start dispatch skipped: app=$app")
                }
            }.onFailure {
                RealOSLog.e(TAG, "deferred app-start dispatch failed", it)
            }
        }
    }

    override fun onPackageReady(module: XposedModule, classLoader: ClassLoader) {
        onPackageReady(module, SpecPackages.KEYBOARD_HOST, classLoader)
    }

    override fun onAppStart(module: XposedModule, app: Application) {
        if (app.packageName in SpecPackages.OPLUS_KEYBOARD_VARIANTS) {
            OplusInputHook.install(module, app)
        }
    }

    override fun onHotReloadCleanup() {
        OplusLiquidGlassHook.cleanup()
        io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.OplusInputEffectsHook.reset()
        OplusInputWhitelistBypass.reset()
        OplusInputSettingsHook.reset()
        // Reset installation flags so that hot-reload reinstall is not blocked by compareAndSet
        OplusInputAutofillHook.reset()
        OplusAutofillToolbarManager.reset()
        OplusInputHook.reset()
    }

    override fun onHotReload(module: XposedModule, app: Application) {
        OplusInputSettingsHook.reset()
        OplusInputAutofillHook.reset()
        OplusAutofillToolbarManager.reset()
        onPackageReady(module, app.packageName, app.classLoader)
        onAppStart(module, app)
        OplusLiquidGlassHook.recoverActiveViewsAndDelegates(app)
        OplusLiquidGlassHook.triggerLiveRefresh(app)
    }
}
