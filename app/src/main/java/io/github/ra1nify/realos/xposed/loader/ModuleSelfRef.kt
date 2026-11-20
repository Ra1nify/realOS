package io.github.ra1nify.realos.xposed.loader

import io.github.libxposed.api.XposedModule

/**
 * Process-wide reference to the loaded module instance.
 *
 * Published by `RealOSModule.onModuleLoaded` before any hook installs, so shared helpers can read
 * the module APK (resources, path) from every injected process, including hosts that never touch
 * the keyboard-specific [io.github.ra1nify.realos.opluskeyboard.OplusModuleRef].
 */
object ModuleSelfRef {

    @Volatile
    var module: XposedModule? = null
        private set

    /** Returns the module APK application info, or null before the module is loaded. */
    fun applicationInfo() = module?.getModuleApplicationInfo()

    fun set(m: XposedModule) {
        module = m
    }
}
