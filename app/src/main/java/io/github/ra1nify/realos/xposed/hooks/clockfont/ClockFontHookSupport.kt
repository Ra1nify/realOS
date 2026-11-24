package io.github.ra1nify.realos.xposed.hooks.clockfont

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Shared hook installation helpers. */
internal object ClockFontHookSupport {

    private const val TAG = "RealOS-ClockFontPlugin"

    private val installedKeys = ConcurrentHashMap.newKeySet<String>()

    fun safeHook(
        module: XposedModule,
        method: Method,
        key: String,
        handler: (XposedInterface.Chain) -> Any?
    ) {
        if (!installedKeys.add(key)) return
        runCatching {
            method.isAccessible = true
            module.hook(method).intercept(handler)
            Log.i(TAG, "hook installed: $key")
            module.log(Log.INFO, TAG, "hook installed: $key")
        }.onFailure {
            installedKeys.remove(key)
            Log.w(TAG, "failed to hook $key: ${it.message}")
            module.log(Log.WARN, TAG, "failed to hook $key: ${it.message}")
        }
    }

    fun logMissing(module: XposedModule, key: String) {
        val message = "resolved symbol missing; hook disabled: $key"
        Log.w(TAG, message)
        module.log(Log.WARN, TAG, message)
    }

    fun reset() {
        installedKeys.clear()
    }
}
