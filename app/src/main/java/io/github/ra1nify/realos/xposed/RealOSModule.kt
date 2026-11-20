package io.github.ra1nify.realos.xposed

import android.app.Application
import io.github.ra1nify.realos.util.ModuleResources
import io.github.ra1nify.realos.util.RealOSLog as Log
import androidx.annotation.Keep
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.ra1nify.realos.xposed.loader.HookLoader
import io.github.ra1nify.realos.xposed.loader.ModuleSelfRef
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import io.github.ra1nify.realos.xposed.loader.SpecRegistry
import io.github.ra1nify.realos.xposed.lite.LiteHookGate
import io.github.ra1nify.realos.util.RealOSLog

@Keep
class RealOSModule : XposedModule() {

    private val tag = "RealOS-Module"

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        RealOSLog.attach(this)
        // Published before any hook runs so shared helpers can reach the module APK
        // resources from every injected process.
        ModuleSelfRef.set(this)
        log(Log.INFO, tag, "realOS module loaded for process: ${param.processName}")
    }

    /**
     * Pre-reload: clears old-generation views, delegates, weak references, and registration flags.
     * Returns true to allow the framework to replace the module.
     */
    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        log(Log.INFO, tag, "Hot reloading requested. Cleaning old generation resources.")
        // The module APK may have been replaced; drop the cached Resources so the next
        // lookup rebuilds from the new APK instead of serving stale strings.
        runCatching { ModuleResources.invalidate() }
        for (spec in SpecRegistry.specs) {
            runCatching { spec.onHotReloadCleanup() }
        }
        return true
    }

    /**
     * Post-reload: unhooks old handles, resolves the live Application, reinstalls specs,
     * and refreshes IME rendering.
     */
    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        log(Log.INFO, tag, "Hot reloaded (${param.oldHookHandles.size} old handles). Unhooking old handles and re-installing...")
        param.oldHookHandles.forEach { handle ->
            runCatching { handle.unhook() }
        }

        val app = try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentAppMethod = activityThreadClass.getMethod("currentApplication")
            currentAppMethod.invoke(null) as? Application
        } catch (t: Throwable) {
            log(Log.ERROR, tag, "Failed to get currentApplication during hot reload", t)
            null
        }

        if (app != null) {
            val pkg = app.packageName
            log(Log.INFO, tag, "Hot reloaded in host package: $pkg")
            val matchingSpecs = SpecRegistry.findSpecs(pkg)
            for (spec in matchingSpecs) {
                try {
                    spec.onHotReload(this, app)
                    log(Log.INFO, tag, "Spec ${spec::class.simpleName} re-installed on hot reload for $pkg")
                } catch (t: Throwable) {
                    log(Log.ERROR, tag, "Failed to re-install spec for $pkg on hot reload (${spec::class.simpleName})", t)
                }
            }
        } else {
            log(Log.WARN, tag, "currentApplication was null during hot reload; awaiting future process start")
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        log(Log.INFO, tag, "SystemServer starting, installing system hooks")
        for (spec in SpecRegistry.specs) {
            try {
                spec.onSystemServerStarting(this, param.classLoader)
            } catch (t: Throwable) {
                log(Log.ERROR, tag, "Failed to install system hooks for ${spec::class.simpleName}", t)
            }
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        // Re-publish before any spec runs: shared helpers read module resources from the
        // host process, and onModuleLoaded is the only other place that sets this.
        ModuleSelfRef.set(this)
        log(Log.INFO, tag, "realOS package ready: ${param.packageName}")

        // Lite: run the install gate first. Spec dispatch uses findSpecs.
        if (!LiteHookGate.shouldProceed(this, param)) return

        for (spec in SpecRegistry.findSpecs(param.packageName)) {
            try {
                spec.onPackageReady(
                    this,
                    param.packageName,
                    param.classLoader,
                    param.applicationInfo.sourceDir
                )
            } catch (t: Throwable) {
                log(Log.ERROR, tag, "Spec onPackageReady failed: ${spec::class.simpleName}", t)
            }
        }

        if (param.packageName in SpecPackages.ANDROID_VARIANTS) {
            return
        }

        try {
            val appClassName = param.applicationInfo.className ?: "android.app.Application"
            val appClass = try {
                param.classLoader.loadClass(appClassName)
            } catch (_: Throwable) {
                Application::class.java
            }
            val method = try {
                appClass.getMethod("onCreate")
            } catch (_: Throwable) {
                Application::class.java.getDeclaredMethod("onCreate")
            }
            hook(method).intercept { chain ->
                val app = chain.thisObject as? Application
                if (app != null) {
                    HookLoader.load(this, app, chain)
                    // Lite: second attempt after Application.onCreate.
                    LiteHookGate.onAppStarted(this, app)
                } else {
                    chain.proceed()
                }
            }
            log(Log.INFO, tag, "HookLoader installed for ${param.packageName} on ${appClass.name}")

            // Fast-starting processes (IME services) can create their Application before the
            // hook above lands, so onCreate never fires for them. Detect that case and run the
            // app-start path directly; specs guard their own install flags.
            val liveApp = currentApplicationOrNull()
            if (liveApp != null && liveApp.packageName == param.packageName) {
                log(
                    Log.INFO, tag,
                    "Application already created for ${param.packageName}; dispatching app start now"
                )
                runCatching { HookLoader.dispatchAppStart(this, liveApp) }
                    .onFailure { log(Log.ERROR, tag, "Fallback app-start dispatch failed", it) }
            }
        } catch (t: Throwable) {
            log(Log.ERROR, tag, "Failed to install HookLoader", t)
        }

    }

    /** Returns the live Application, or null when the process has not created one yet. */
    private fun currentApplicationOrNull(): Application? = try {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val currentAppMethod = activityThreadClass.getMethod("currentApplication")
        currentAppMethod.invoke(null) as? Application
    } catch (_: Throwable) {
        null
    }
}
