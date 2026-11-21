package io.github.ra1nify.realos.opluskeyboard

import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.opluskeyboard.OplusModuleRef

/**
 * Global reference to XposedModule in Xiaobu Keyboard (com.oplus.keyboard) process.
 * Used by parasite UI views and text loaders to access module resources and preferences.
 */
object OplusModuleRef {

    @Volatile
    var module: XposedModule? = null
        private set

    fun set(m: XposedModule) {
        module = m
    }

    fun get(): XposedModule? = module
}
