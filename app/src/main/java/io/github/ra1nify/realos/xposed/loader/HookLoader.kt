package io.github.ra1nify.realos.xposed.loader

import android.app.Application
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedInterface

/**
 * Unified hook dispatch.
 * Called once per package start from the module's Application.onCreate hook chain:
 *  1. original Application.onCreate proceeds
 *  2. the per-package specs (if any)
 *
 * Registration happens in [SpecRegistry] — this class never changes when hooks are added.
 */
object HookLoader {

    private const val TAG = "HookLoader"

    @Throws(Throwable::class)
    fun load(
        module: XposedModule,
        app: Application,
        chain: XposedInterface.Chain
    ): Any? {
        // ── Proceed the original call ──
        val result = chain.proceed()

        dispatchAppStart(module, app)

        return result
    }

    /**
     * Runs every matching spec's app-start path.
     *
     * Split out of [load] so a caller that missed the `Application.onCreate` hook can still run
     * it exactly once. Fast-starting processes (an IME service, for example) can create their
     * Application before `onPackageReady` manages to install the hook, and the hook then never
     * fires for that process.
     */
    fun dispatchAppStart(module: XposedModule, app: Application) {
        val pkg = app.packageName
        val matchingSpecs = SpecRegistry.findSpecs(pkg)
        for (spec in matchingSpecs) {
            try {
                Log.i(TAG, "Running spec ${spec::class.simpleName} for $pkg")
                spec.onAppStart(module, app)
            } catch (t: Throwable) {
                Log.e(TAG, "Spec onAppStart failed for $pkg (${spec::class.simpleName})", t)
            }
        }
    }
}
