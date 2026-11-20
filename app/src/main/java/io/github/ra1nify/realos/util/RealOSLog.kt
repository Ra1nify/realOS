package io.github.ra1nify.realos.util

import android.content.Context
import io.github.ra1nify.realos.BuildConfig
import android.os.SystemClock
import android.util.Log as AndroidLog
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterfaceWrapper
import io.github.libxposed.api.XposedModule
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Global log gate for both logging paths:
 *
 * 1. `module.log(...)` — libxposed's method is final. [attach] replaces the
 *    module's internal `XposedInterface` with a counting-free proxy that drops
 *    calls below the current level.
 * 2. direct `Log.*` calls — source imports use `RealOSLog as Log`, so every call
 *    reaches the same [isEnabled] check.
 *
 * Default is [LogLevel.NONE]. A host process refreshes the remote level at most
 * once per second per log burst; an app process reads its local preference.
 */
object RealOSLog {

    private const val TAG = "RealOS-Log"
    private const val REFRESH_MS = 1000L

    const val VERBOSE = AndroidLog.VERBOSE
    const val DEBUG = AndroidLog.DEBUG
    const val INFO = AndroidLog.INFO
    const val WARN = AndroidLog.WARN
    const val ERROR = AndroidLog.ERROR
    const val ASSERT = AndroidLog.ASSERT

    @Volatile
    private var level = LogLevel.NONE

    @Volatile
    private var lastRefreshAt = 0L

    @Volatile
    private var moduleRef: XposedModule? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var proxyInstalled = false

    /** realOS app process entry; logs before this point stay suppressed. */
    fun initApp(context: Context) {
        appContext = context.applicationContext ?: context
        refresh(force = true)
    }

    /** Host process entry; installs the `module.log` proxy and primes the level. */
    fun attach(module: XposedModule) {
        moduleRef = module
        refresh(force = true)
        installLogProxy(module)
    }

    fun isEnabled(priority: Int): Boolean {
        // Lite builds ship as a single-host patch with no settings surface, so nothing can
        // ever turn logging on. Suppress everything up front instead of refreshing a level.
        if (BuildConfig.MODE_LITE) return false
        refresh(force = false)
        return priority >= level.minPriority
    }

    private fun refresh(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastRefreshAt < REFRESH_MS) return
        lastRefreshAt = now

        val next = when {
            moduleRef != null -> runCatching { RealOSLogConfig.readRemote(moduleRef!!) }.getOrNull()
            appContext != null -> runCatching { RealOSLogConfig.getLevel(appContext!!) }.getOrNull()
            else -> null
        } ?: return

        level = next
    }

    /**
     * Replaces the wrapper's framework interface with a proxy that forwards all
     * methods and drops `log(priority, ...)` calls below the current level.
     *
     * Reflection matches [io.github.libxposed.api.XposedInterfaceWrapper]'s single
     * `XposedInterface` field; the field name is not part of the public API.
     */
    private fun installLogProxy(module: XposedModule) {
        if (proxyInstalled) return
        proxyInstalled = true

        runCatching {
            val field = XposedInterfaceWrapper::class.java.declaredFields.firstOrNull {
                XposedInterface::class.java.isAssignableFrom(it.type)
            } ?: return@runCatching
            field.isAccessible = true
            val base = field.get(module) as? XposedInterface ?: return@runCatching

            val proxy = Proxy.newProxyInstance(
                XposedInterface::class.java.classLoader,
                arrayOf(XposedInterface::class.java)
            ) { _, method, args ->
                if (method.name == "log") {
                    val priority = args?.getOrNull(0) as? Int
                    if (priority != null && !isEnabled(priority)) {
                        return@newProxyInstance null
                    }
                }
                try {
                    method.invoke(base, *(args ?: emptyArray()))
                } catch (t: InvocationTargetException) {
                    throw t.targetException
                }
            } as XposedInterface

            field.set(module, proxy)
        }.onFailure {
            AndroidLog.w(TAG, "log-level proxy install failed; module.log stays ungated", it)
        }
    }

    // ── android.util.Log-compatible surface ──

    fun v(tag: String?, msg: String): Int =
        if (isEnabled(VERBOSE)) AndroidLog.v(tag, msg) else 0

    fun v(tag: String?, msg: String, tr: Throwable?): Int =
        if (isEnabled(VERBOSE)) AndroidLog.v(tag, msg, tr) else 0

    fun d(tag: String?, msg: String): Int =
        if (isEnabled(DEBUG)) AndroidLog.d(tag, msg) else 0

    fun d(tag: String?, msg: String, tr: Throwable?): Int =
        if (isEnabled(DEBUG)) AndroidLog.d(tag, msg, tr) else 0

    fun i(tag: String?, msg: String): Int =
        if (isEnabled(INFO)) AndroidLog.i(tag, msg) else 0

    fun i(tag: String?, msg: String, tr: Throwable?): Int =
        if (isEnabled(INFO)) AndroidLog.i(tag, msg, tr) else 0

    fun w(tag: String?, msg: String): Int =
        if (isEnabled(WARN)) AndroidLog.w(tag, msg) else 0

    fun w(tag: String?, msg: String, tr: Throwable?): Int =
        if (isEnabled(WARN)) AndroidLog.w(tag, msg, tr) else 0

    fun e(tag: String?, msg: String): Int =
        if (isEnabled(ERROR)) AndroidLog.e(tag, msg) else 0

    fun e(tag: String?, msg: String, tr: Throwable?): Int =
        if (isEnabled(ERROR)) AndroidLog.e(tag, msg, tr) else 0

    fun wtf(tag: String?, msg: String): Int =
        if (isEnabled(ASSERT)) AndroidLog.wtf(tag, msg) else 0

    fun wtf(tag: String?, msg: String, tr: Throwable?): Int =
        if (isEnabled(ASSERT)) AndroidLog.wtf(tag, msg, tr) else 0

    fun println(priority: Int, tag: String?, msg: String): Int =
        if (isEnabled(priority)) AndroidLog.println(priority, tag, msg) else 0

    fun isLoggable(tag: String?, level: Int): Boolean =
        isEnabled(level) && AndroidLog.isLoggable(tag, level)

    fun getStackTraceString(t: Throwable): String = AndroidLog.getStackTraceString(t)
}
