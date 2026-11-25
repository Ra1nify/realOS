package io.github.ra1nify.realos.xposed

import android.app.Application
import io.github.ra1nify.realos.util.ModuleResources
import io.github.ra1nify.realos.util.RealOSLog as Log
import androidx.annotation.Keep
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.ra1nify.realos.xposed.loader.HookCountProbe
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

    /** Cleans old-generation state before the framework replaces the module. */
    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        // Require an Application before allowing replacement.
        val app = currentApplicationOrNull()
        if (app == null) {
            log(
                Log.WARN,
                tag,
                "Refusing hot reload: currentApplication is null; old hooks stay active"
            )
            return false
        }
        // Carry the package name to the new generation.
        runCatching { param.setSavedInstanceState(app.packageName) }

        log(Log.INFO, tag, "Hot reloading requested. Cleaning old generation resources.")
        // The module APK may have been replaced; drop the cached Resources so the next
        // lookup rebuilds from the new APK instead of serving stale strings.
        runCatching { ModuleResources.invalidate() }
        for (spec in SpecRegistry.specs) {
            runCatching { spec.onHotReloadCleanup() }
        }
        return true
    }

    /** Reinstalls new-generation hooks and retires old handles. */
    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        // Re-publish module state for the new generation.
        ModuleSelfRef.set(this)
        RealOSLog.attach(this)
        log(
            Log.INFO,
            tag,
            "Hot reloaded (${param.oldHookHandles.size} old handles). Re-installing specs..."
        )

        val app = currentApplicationOrNull()
        if (app == null) {
            log(
                Log.WARN,
                tag,
                "currentApplication is null during hot reload; keeping old hooks"
            )
            return
        }

        val pkg = (param.savedInstanceState as? String)
            ?.takeIf { it.isNotBlank() }
            ?: app.packageName
        val matchingSpecs = SpecRegistry.findSpecs(pkg)
        log(
            Log.INFO,
            tag,
            "Hot reload host=$pkg moduleSpecs=${SpecRegistry.specs.size} " +
                "matchingSpecs=${matchingSpecs.size}"
        )

        if (matchingSpecs.isEmpty()) {
            if (SpecRegistry.specs.isEmpty()) {
                // Keep old hooks when no specs loaded.
                log(Log.WARN, tag, "No specs loaded in the new generation; keeping old hooks")
                return
            }
            unhookOld(param)
            log(Log.INFO, tag, "No business for $pkg in the new build; removed old hooks")
            return
        }

        HookCountProbe.begin(this)
        var asyncInstall = false
        for (spec in matchingSpecs) {
            if (spec.hotReloadInstallIsAsync) asyncInstall = true
            try {
                spec.onHotReload(this, app)
                log(
                    Log.INFO,
                    tag,
                    "Spec ${spec::class.simpleName} re-installed on hot reload for $pkg"
                )
            } catch (t: Throwable) {
                log(
                    Log.ERROR,
                    tag,
                    "Failed to re-install spec for $pkg on hot reload (${spec::class.simpleName})",
                    t
                )
            }
        }

        val newHooks = HookCountProbe.count()
        log(
            Log.INFO,
            tag,
            "Hot reload registered $newHooks hook(s), async=$asyncInstall, " +
                "oldHandles=${param.oldHookHandles.size}"
        )
        if (!asyncInstall && newHooks == 0) {
            // Keep old handles when no new hooks were registered.
            log(
                Log.ERROR,
                tag,
                "Hot reload registered no hooks; keeping old hooks for $pkg"
            )
            return
        }

        unhookOld(param)
        runCatching { hookApplicationOnCreate(app.javaClass) }
            .onFailure {
                log(Log.ERROR, tag, "Failed to re-install HookLoader on hot reload", it)
            }
        log(Log.INFO, tag, "Hot reload complete for $pkg")
    }

    private fun unhookOld(param: XposedModuleInterface.HotReloadedParam) {
        param.oldHookHandles.forEach { handle ->
            runCatching { handle.unhook() }
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
            hookApplicationOnCreate(appClass)
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

    /** Installs the Application.onCreate dispatch hook. */
    private fun hookApplicationOnCreate(appClass: Class<*>) {
        val method = try {
            appClass.getMethod("onCreate")
        } catch (_: Throwable) {
            Application::class.java.getDeclaredMethod("onCreate")
        }
        method.isAccessible = true
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
