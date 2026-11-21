package io.github.ra1nify.realos.xposed.symbol

import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import org.luckypray.dexkit.DexKitBridge
import java.io.File

/**
 * RAII executor for the DexKit bridge.
 *
 * 1. **Native library path**: `libdexkit.so` ships inside the module APK; the host
 *    process cannot find it through `System.loadLibrary` and loads it from the module
 *    `nativeLibraryDir` instead.
 * 2. **Native memory**: `DexKitBridge` holds a C++ mapping and is `close()`d on both
 *    the normal and the exception path.
 *
 * Covers load + run + release only, with no host-specific rules.
 */
object DexKitRunner {

    private const val TAG = "RealOS-DexKitRunner"
    private const val LIB_NAME = "dexkit"
    private const val LIB_FILE = "libdexkit.so"

    @Volatile
    private var nativeLoaded = false

    /**
     * Runs one scan on [apkPath] and releases the bridge.
     *
     * @param block Scan logic receiving the ready bridge; its return value passes through
     * @return Result, or null when the native load fails or the scan throws (caller marks ERROR)
     */
    fun <T> withBridge(module: XposedModule, apkPath: String, block: (DexKitBridge) -> T): T? {
        if (apkPath.isBlank() || !File(apkPath).exists()) {
            module.log(Log.WARN, TAG, "Host APK path is invalid; skipping the DexKit scan: $apkPath")
            return null
        }

        if (!ensureNativeLoaded(module)) return null

        return runCatching {
            // use {} closes the bridge whether block returns or throws
            DexKitBridge.create(apkPath).use { bridge -> block(bridge) }
        }.getOrElse { t ->
            module.log(Log.ERROR, TAG, "DexKit scan threw", t)
            null
        }
    }

    /**
     * Loads the native library once and marks the result.
     *
     * Tries regular `loadLibrary` first, then the absolute path in the module native directory.
     */
    private fun ensureNativeLoaded(module: XposedModule): Boolean {
        if (nativeLoaded) return true
        synchronized(this) {
            if (nativeLoaded) return true
            nativeLoaded = runCatching {
                System.loadLibrary(LIB_NAME)
                true
            }.getOrElse {
                // Host process cannot see the module lib dir; loads the absolute path from the module nativeLibraryDir
                val nativeDir = module.getModuleApplicationInfo().nativeLibraryDir
                val soFile = File(nativeDir, LIB_FILE)
                if (!soFile.exists()) {
                    module.log(Log.ERROR, TAG, "DexKit native library not found: ${soFile.absolutePath}")
                    return false
                }
                runCatching {
                    System.load(soFile.absolutePath)
                    true
                }.getOrElse { t ->
                    module.log(Log.ERROR, TAG, "Failed to load the DexKit native library", t)
                    false
                }
            }
            return nativeLoaded
        }
    }
}
