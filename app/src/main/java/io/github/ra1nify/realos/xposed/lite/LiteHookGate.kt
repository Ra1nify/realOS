package io.github.ra1nify.realos.xposed.lite

import android.app.Application
import android.os.Handler
import android.os.Looper
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.xposed.loader.SpecRegistry
import org.json.JSONObject
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

/**
 * Install gate for the lite build (`BuildConfig.MODE_LITE`).
 *
 * Tries to install hooks at three points: `onPackageReady`, `Application.onCreate`,
 * and after the main-thread queue drains. If no hook is installed after three attempts,
 * writes a lock file and skips later processes. The lock clears when the host APK
 * fingerprint or module version changes.
 *
 * Hook count: libxposed `hook()` is final, so `onModuleLoaded` wraps the internal
 * `XposedInterface` with a counting proxy. If the proxy cannot be installed, only
 * exceptions are counted.
 */
internal object LiteHookGate {

    private const val TAG = "RealOS-LiteGate"
    private const val LOCK_FILE_NAME = "realos_lite_lock.json"

    /** Installed hook count. */
    private val hookCount = AtomicInteger(0)

    private var counterInstalled = false
    private var counterAvailable = false

    private var hostDataDir: File? = null
    private var fingerprint: String? = null
    private var lockedChecked = false
    private var locked = false
    private var evaluated = false
    private var failureCount = 0

    /**
     * Process entry point.
     *
     * @return true to continue; false when the gate is locked.
     */
    fun shouldProceed(module: XposedModule, param: XposedModuleInterface.PackageReadyParam): Boolean {
        if (!BuildConfig.MODE_LITE) return true
        installHookCounter(module)

        val dataDir = param.applicationInfo.dataDir
        if (dataDir.isNullOrEmpty()) {
            Log.w(TAG, "Host dataDir missing; deadlock detection is disabled for this run")
            return true
        }
        hostDataDir = File(dataDir)
        val currentFingerprint = buildFingerprint(param.applicationInfo.sourceDir)
        fingerprint = currentFingerprint

        if (lockedChecked) return !locked
        lockedChecked = true

        val state = readLock()
        if (state == null) {
            locked = false
            return true
        }
        val sameHost = state.fingerprint == currentFingerprint
        val sameModule = state.moduleVersion == BuildConfig.VERSION_CODE
        if (!sameHost || !sameModule) {
            Log.i(
                TAG,
                "Deadlock cleared: host fingerprint changed=$(!sameHost) module version changed=$(!sameModule)" +
                    "(old fingerprint=${state.fingerprint}, new fingerprint=$currentFingerprint)"
            )
            clearLock()
            locked = false
            return true
        }
        locked = true
        Log.w(TAG, "lite is deadlocked (reason: ${state.reason}); skipping install until the host or the module is updated")
        return false
    }

    /** Second attempt: after Application.onCreate. */
    fun onAppStarted(module: XposedModule, app: Application) {
        if (!BuildConfig.MODE_LITE || locked) return
        if (hookCountOf() > 0) {
            markEffective()
            return
        }
        // Third attempt: main-thread queue idle.
        Handler(Looper.getMainLooper()).post { thirdAttempt(module, app) }
    }

    private fun thirdAttempt(module: XposedModule, app: Application) {
        if (evaluated) return
        if (hookCountOf() > 0) {
            markEffective()
            return
        }
        // Reruns only when no hook is installed.
        for (spec in SpecRegistry.specs) {
            try {
                spec.onAppStart(module, app)
            } catch (t: Throwable) {
                failureCount++
                Log.w(TAG, "Third attempt threw: ${spec::class.simpleName}", t)
            }
        }
        evaluate()
    }

    private fun markEffective() {
        if (evaluated) return
        evaluated = true
        Log.i(TAG, "lite install effective (hook count=${hookCountOf()})")
    }

    private fun evaluate() {
        if (evaluated) return
        evaluated = true
        val installed = hookCountOf()
        if (installed > 0) {
            Log.i(TAG, "lite install effective (hook count=$installed)")
            return
        }
        if (installed == 0) {
            writeLock("No hook installed after three attempts")
            Log.e(TAG, "No hook installed after three attempts; deadlocked until the host or the module is updated")
            return
        }
        // Counter unavailable: locks only when all three attempts threw.
        if (failureCount >= 3) {
            writeLock("All three attempts threw; hook count unavailable")
            Log.e(TAG, "All three attempts threw; deadlocked")
        } else {
            Log.i(TAG, "Hook count unavailable and no total failure observed; staying retryable (not locked)")
        }
    }

    /** Returns the hook count, or -1 when the counter is unavailable. */
    private fun hookCountOf(): Int = if (counterAvailable) hookCount.get() else -1

    // Hook counting proxy.

    private fun installHookCounter(module: XposedModule) {
        if (counterInstalled) return
        counterInstalled = true
        counterAvailable = runCatching {
            // XposedInterfaceWrapper.mBase holds the framework interface.
            // Lookup by type; the field name may change.
            val wrapperClass = Class.forName("io.github.libxposed.api.XposedInterfaceWrapper")
            val field = wrapperClass.declaredFields.firstOrNull {
                XposedInterface::class.java.isAssignableFrom(it.type)
            } ?: return@runCatching false
            field.isAccessible = true
            val base = field.get(module) as? XposedInterface ?: return@runCatching false
            val proxy = Proxy.newProxyInstance(
                XposedInterface::class.java.classLoader,
                arrayOf(XposedInterface::class.java)
            ) { _, method, args ->
                if (method.name == "hook" || method.name == "hookClassInitializer") {
                    hookCount.incrementAndGet()
                }
                try {
                    method.invoke(base, *(args ?: emptyArray()))
                } catch (t: InvocationTargetException) {
                    throw t.targetException
                }
            } as XposedInterface
            field.set(module, proxy)
            true
        }.getOrElse { t ->
            Log.w(TAG, "Hook count proxy install failed; deadlock detection degrades to all-three-attempts-threw: ${t.message}")
            false
        }
        if (!counterAvailable) {
            Log.w(TAG, "Hook count unavailable: locking only happens when all three attempts throw")
        }
    }

    // State persistence.

    private data class LockState(
        val fingerprint: String,
        val moduleVersion: Int,
        val reason: String
    )

    private fun lockFile(): File? = hostDataDir?.let { File(it, LOCK_FILE_NAME) }

    private fun buildFingerprint(sourceDir: String?): String {
        val length = sourceDir?.let { runCatching { File(it).length() }.getOrDefault(0L) } ?: 0L
        return "$sourceDir|$length"
    }

    private fun readLock(): LockState? = runCatching {
        val file = lockFile() ?: return null
        if (!file.isFile) return null
        val json = JSONObject(file.readText())
        LockState(
            fingerprint = json.optString("fingerprint"),
            moduleVersion = json.optInt("moduleVersion", -1),
            reason = json.optString("reason")
        )
    }.getOrElse { t ->
        // Corrupt state is treated as unlocked.
        Log.w(TAG, "Failed to read the deadlock state; treating it as unlocked", t)
        null
    }

    private fun writeLock(reason: String) {
        runCatching {
            val target = lockFile() ?: return@runCatching
            val dir = target.parentFile ?: return@runCatching
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Host data directory unavailable; the deadlock state is not persisted this run (memory only)")
                return@runCatching
            }
            val json = JSONObject().apply {
                put("fingerprint", fingerprint ?: "")
                put("moduleVersion", BuildConfig.VERSION_CODE)
                put("reason", reason)
                put("lockedAt", System.currentTimeMillis())
            }
            // Writes to a temp file, then renames atomically.
            val tmp = File(dir, "$LOCK_FILE_NAME.tmp")
            tmp.writeText(json.toString())
            if (tmp.renameTo(target)) {
                Log.i(TAG, "Deadlock state persisted: $target")
            } else {
                tmp.delete()
                Log.w(TAG, "Deadlock state rename failed; not persisted this run (memory only)")
            }
        }.getOrElse { t ->
            Log.w(TAG, "Deadlock state write threw; degrading to memory only", t)
        }
    }

    private fun clearLock() = runCatching {
        lockFile()?.takeIf { it.isFile }?.delete()
    }.getOrElse { t ->
        Log.w(TAG, "Failed to clear the deadlock state", t)
    }
}
