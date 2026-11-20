package io.github.ra1nify.realos.xposed.hooks.mediaprovider

import android.net.Uri
import android.os.SystemClock
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.mediaprovider.MediaProviderConfig
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Restores the photo picker refresh path that ColorOS suppresses when Gallery
 * provides picker data.
 *
 * AOSP calls `PickerDataLayer.handleMediaEventNotification` from the MediaProvider
 * file change listener. ColorOS skips it behind `isAvailableGalleryData()` and
 * relies on Gallery's throttled `gallery_request_data_sync` request instead. This
 * hook triggers the same local picker sync directly after an external image or
 * video row changes, so `PickerSyncController` updates the picker database and
 * sends its own `notifyMediaChange` / `notifyMediaDeleteChange`.
 */
object MediaProviderPickerRefreshHook {

    private const val TAG = "RealOS-MediaProviderHook"
    private const val DEBOUNCE_MS = 250L
    private const val FAILURE_BACKOFF_MS = 60_000L
    private const val MAX_FAILURES_BEFORE_BACKOFF = 3

    @Volatile
    private var isInstalled = false

    private val executorLock = Any()
    private val executorRef = AtomicReference<ScheduledExecutorService?>(null)
    private val pendingRef = AtomicReference<ScheduledFuture<*>?>(null)
    private val failureCount = AtomicInteger(0)

    @Volatile
    private var blockedUntil = 0L

    private var getSyncController: Method? = null
    private var syncLocalMedia: Method? = null

    fun install(module: XposedModule, symbols: MediaProviderSymbols, classLoader: ClassLoader) {
        if (isInstalled) return
        if (!symbols.canTriggerSync) {
            module.log(Log.WARN, TAG, "Picker sync symbols missing; refresh hook not installed")
            return
        }

        val controller = symbols.getSyncControllerMethod?.restore(classLoader)
        val sync = symbols.syncAllMediaFromLocalProviderMethod?.restore(classLoader)
        if (controller == null || sync == null) {
            module.log(Log.WARN, TAG, "Failed to restore PickerSyncController handles; refresh hook not installed")
            return
        }

        val notifyMethods = listOfNotNull(
            symbols.notifyInsertMethod?.restore(classLoader),
            symbols.notifyUpdateMethod?.restore(classLoader),
            symbols.notifyDeleteMethod?.restore(classLoader)
        )
        if (notifyMethods.size != 3) {
            module.log(
                Log.WARN,
                TAG,
                "Failed to restore DatabaseHelper.notify* handles; refresh hook not installed (${notifyMethods.size}/3)"
            )
            return
        }

        getSyncController = controller
        syncLocalMedia = sync

        try {
            notifyMethods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    schedule(module, chain.args.getOrNull(0) as? Uri)
                    result
                }
            }
            isInstalled = true
            module.log(
                Log.INFO,
                TAG,
                "Picker refresh hook installed; enabled=${MediaProviderConfig.isPickerRefreshEnabled(module)}"
            )
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "Failed to install picker refresh hook", t)
            reset()
        }
    }

    fun reset() {
        isInstalled = false
        pendingRef.getAndSet(null)?.cancel(false)
        executorRef.getAndSet(null)?.shutdownNow()
        getSyncController = null
        syncLocalMedia = null
        failureCount.set(0)
        blockedUntil = 0L
    }

    private fun schedule(module: XposedModule, uri: Uri?) {
        if (!isMediaRowUri(uri)) return

        if (!MediaProviderConfig.isPickerRefreshEnabled(module)) {
            pendingRef.getAndSet(null)?.cancel(false)
            return
        }

        pendingRef.getAndSet(null)?.cancel(false)
        pendingRef.set(
            executor().schedule(
                { runSync(module) },
                DEBOUNCE_MS,
                TimeUnit.MILLISECONDS
            )
        )
    }

    private fun runSync(module: XposedModule) {
        pendingRef.set(null)
        if (!MediaProviderConfig.isPickerRefreshEnabled(module)) return

        val now = SystemClock.elapsedRealtime()
        if (now < blockedUntil) return

        try {
            val controller = getSyncController?.invoke(null)
            if (controller == null) {
                module.log(Log.WARN, TAG, "PickerSyncController.getInstanceOrThrow() returned null")
                return
            }
            syncLocalMedia?.invoke(controller, null)
            failureCount.set(0)
        } catch (t: Throwable) {
            val cause = (t as? InvocationTargetException)?.targetException ?: t
            val failures = failureCount.incrementAndGet()
            module.log(
                Log.WARN,
                TAG,
                "Picker local sync failed (count=$failures): ${cause.javaClass.simpleName}: ${cause.message}"
            )
            if (failures >= MAX_FAILURES_BEFORE_BACKOFF) {
                blockedUntil = now + FAILURE_BACKOFF_MS
                module.log(
                    Log.WARN,
                    TAG,
                    "Picker local sync backing off ${FAILURE_BACKOFF_MS}ms after $failures failures"
                )
            }
        }
    }

    private fun executor(): ScheduledExecutorService {
        executorRef.get()?.let { return it }
        synchronized(executorLock) {
            executorRef.get()?.let { return it }
            val created = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "RealOS-PickerRefresh").apply { isDaemon = true }
            }
            executorRef.set(created)
            return created
        }
    }

    /**
     * Accepts only per-row external image/video URIs:
     * `content://media/<volume>/images|video/media/<id>`.
     *
     * Expanded parent URIs, the generic `file/<id>` URI, audio, documents and
     * other volumes are ignored so unrelated media changes stay untouched.
     */
    private fun isMediaRowUri(uri: Uri?): Boolean {
        if (uri == null) return false
        if (uri.scheme != "content" || uri.authority != "media") return false
        val segments = uri.pathSegments
        if (segments.size != 4) return false
        if (!segments[0].startsWith("external")) return false
        val kind = segments[1]
        if (kind != "images" && kind != "video") return false
        if (segments[2] != "media") return false
        return segments[3].toLongOrNull() != null
    }
}
