package io.github.ra1nify.realos.xposed.hooks.intentroute

import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.xposed.loader.SpecLoader
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * SpecLoader entry point for the system_server link resolution alignment.
 */
object IntentRouteAlignmentSpec : SpecLoader {

    private const val TAG = "RealOS-IntentRouteSpec"

    override val packageNames: Set<String> = SpecPackages.ANDROID_VARIANTS

    override fun onSystemServerStarting(module: XposedModule, classLoader: ClassLoader) {
        module.log(Log.INFO, TAG, "onSystemServerStarting")
        IntentRouteAlignmentHook.install(module, classLoader)
    }
}
