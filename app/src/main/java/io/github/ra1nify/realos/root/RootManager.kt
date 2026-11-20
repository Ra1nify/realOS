package io.github.ra1nify.realos.root

import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.data.AppStatusCache
import io.github.ra1nify.realos.data.RootCachedStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class RootState {
    UNKNOWN,
    GRANTED,
    NOT_GRANTED
}

enum class RootFlavor(val title: String) {
    UNKNOWN("Root"),
    KERNEL_SU("KernelSU"),
    MAGISK("Magisk"),
    APATCH("APatch"),
    GENERIC("Root")
}

data class RootInfo(
    val state: RootState,
    val flavor: RootFlavor = RootFlavor.UNKNOWN,
    val version: String? = null
)

/**
 * Root capability wrapper using ProcessBuilder for su -c.
 * Reads exit codes and output streams directly.
 * Probes KernelSU, Magisk and APatch environments over multiple rounds.
 * The default state comes from the local cache; updates overwrite the cache.
 */
object RootManager {

    private const val TAG = "RealOS-Root"
    private const val SU_TIMEOUT_MILLIS = 15000L

    private val rootScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _rootInfo = MutableStateFlow(RootInfo(RootState.UNKNOWN))
    val rootInfo: StateFlow<RootInfo> = _rootInfo.asStateFlow()

    private val _isCheckingRoot = MutableStateFlow(false)
    val isCheckingRoot: StateFlow<Boolean> = _isCheckingRoot.asStateFlow()

    private var appContext: Context? = null
    private var initialized = false

    @Volatile
    private var hasProbedOnAppLaunch = false

    data class SuResult(val exitCode: Int, val output: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    /**
     * Initializes manager with persistent cache.
     * Default state is loaded instantly from local storage.
     */
    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app

        // Shows the cached status by default
        val cached = AppStatusCache.loadRootStatus(app)
        _rootInfo.value = cached.toRootInfo()
        initialized = true
    }

    fun suExecRaw(command: String, timeoutMillis: Long = SU_TIMEOUT_MILLIS): SuResult {
        return try {
            val process = ProcessBuilder("su", "-M", "-c", command)
                .redirectErrorStream(true)
                .start()

            val outputBuf = StringBuilder()
            val readerThread = Thread {
                try {
                    process.inputStream.bufferedReader().use { r ->
                        while (true) {
                            val line = r.readLine() ?: break
                            outputBuf.appendLine(line)
                        }
                    }
                } catch (_: Throwable) {
                }
            }.apply { isDaemon = true }
            readerThread.start()

            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                joinReaderQuietly(readerThread)
                Log.e(TAG, "su timed out after ${timeoutMillis}ms: $command")
                return SuResult(-1, outputBuf.toString())
            }
            joinReaderQuietly(readerThread)
            SuResult(process.exitValue(), outputBuf.toString().trim())
        } catch (t: Throwable) {
            Log.e(TAG, "su exec failed: $command", t)
            SuResult(-1, "")
        }
    }

    private fun joinReaderQuietly(thread: Thread, timeoutMillis: Long = 2000L) {
        try {
            thread.join(timeoutMillis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    fun checkRoot(): Boolean {
        val r = suExecRaw("id")
        Log.i(TAG, "su id -> rc=${r.exitCode} out=${r.output}")
        return r.isSuccess && r.output.contains("uid=0")
    }

    /**
     * Probes root status synchronously and identifies environment (KernelSU, Magisk, APatch, or Generic su).
     */
    fun probeRootInfoSync(): RootInfo {
        if (!checkRoot()) {
            val ungranted = RootInfo(RootState.NOT_GRANTED, RootFlavor.UNKNOWN, null)
            _rootInfo.value = ungranted
            persistCacheIfChanged(ungranted)
            Log.i(TAG, "Root not granted")
            return ungranted
        }

        // 1. Probe KernelSU
        val ksuResult = suExecRaw("ksud --version")
        if (ksuResult.isSuccess && ksuResult.output.isNotBlank()) {
            val ver = parseKernelSuVersion(ksuResult.output)
            val info = RootInfo(RootState.GRANTED, RootFlavor.KERNEL_SU, ver)
            _rootInfo.value = info
            persistCacheIfChanged(info)
            Log.i(TAG, "KernelSU detected: $ver")
            return info
        }
        val ksuV = suExecRaw("ksud -V")
        if (ksuV.isSuccess && ksuV.output.isNotBlank() && ksuV.output.all { it.isDigit() }) {
            val ver = "v" + ksuV.output.trim()
            val info = RootInfo(RootState.GRANTED, RootFlavor.KERNEL_SU, ver)
            _rootInfo.value = info
            persistCacheIfChanged(info)
            Log.i(TAG, "KernelSU detected via -V: $ver")
            return info
        }

        // 2. Probe Magisk
        val magiskResult = suExecRaw("magisk -v")
        if (magiskResult.isSuccess && magiskResult.output.isNotBlank()) {
            val ver = parseMagiskVersion(magiskResult.output)
            val info = RootInfo(RootState.GRANTED, RootFlavor.MAGISK, ver)
            _rootInfo.value = info
            persistCacheIfChanged(info)
            Log.i(TAG, "Magisk detected: $ver")
            return info
        }

        // 3. Probe APatch
        val apdResult = suExecRaw("apd -V")
        if (apdResult.isSuccess && apdResult.output.isNotBlank()) {
            val ver = parseAPatchVersion(apdResult.output)
            val info = RootInfo(RootState.GRANTED, RootFlavor.APATCH, ver)
            _rootInfo.value = info
            persistCacheIfChanged(info)
            Log.i(TAG, "APatch detected: $ver")
            return info
        }
        val apdVer = suExecRaw("apd --version")
        if (apdVer.isSuccess && apdVer.output.isNotBlank()) {
            val ver = parseAPatchVersion(apdVer.output)
            val info = RootInfo(RootState.GRANTED, RootFlavor.APATCH, ver)
            _rootInfo.value = info
            persistCacheIfChanged(info)
            Log.i(TAG, "APatch detected via --version: $ver")
            return info
        }

        // 4. Generic Root
        val generic = RootInfo(RootState.GRANTED, RootFlavor.GENERIC, null)
        _rootInfo.value = generic
        persistCacheIfChanged(generic)
        Log.i(TAG, "Generic Root detected")
        return generic
    }

    private fun persistCacheIfChanged(info: RootInfo) {
        val app = appContext ?: return
        val currentStatus = RootCachedStatus(
            state = info.state,
            flavor = info.flavor,
            version = info.version
        )
        val overwritten = AppStatusCache.saveRootStatus(app, currentStatus)
        if (overwritten) {
            Log.i(TAG, "Root status cache updated: $currentStatus")
        }
    }

    /**
     * Trigger 1: app launch. Probes once per cold start and overwrites the cache when
     * the value changes; never runs again on tab switches.
     */
    fun probeOnAppLaunch(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!initialized) initialize(app)
        if (hasProbedOnAppLaunch) return
        hasProbedOnAppLaunch = true
        refreshExplicit(app, isUserClick = false)
    }

    /**
     * Trigger 2: manual tap. Probes when the user taps the Root card on the home page
     * and overwrites the cache when the value changes; the tap first downgrades the
     * shown flavor and version to generic, then upgrades them after the version is found.
     */
    fun refreshExplicit(context: Context? = null, isUserClick: Boolean = true) {
        context?.let { appContext = it.applicationContext }
        if (_isCheckingRoot.value) return
        _isCheckingRoot.value = true

        if (isUserClick) {
            // On a refresh tap, downgrades to generic Root with no version
            _rootInfo.value = _rootInfo.value.copy(flavor = RootFlavor.GENERIC, version = null)
        }

        rootScope.launch {
            try {
                if (isUserClick) {
                    delay(350) // Visual pause while the generic Root downgrade is shown
                }
                probeRootInfoSync()
            } finally {
                _isCheckingRoot.value = false
            }
        }
    }

    private fun parseKernelSuVersion(raw: String): String {
        val trimmed = raw.trim()
        val regex = Regex("""v?[0-9]+(\.[0-9]+)*(-[a-zA-Z0-9.]+)?(\s*\([0-9]+\))?""")
        val match = regex.find(trimmed)
        val extracted = match?.value?.trim() ?: trimmed
        return if (extracted.startsWith("v")) extracted else "v$extracted"
    }

    private fun parseMagiskVersion(raw: String): String {
        val trimmed = raw.trim()
        val cleaned = trimmed.substringBefore(":").trim()
        return if (cleaned.startsWith("v")) cleaned else "v$cleaned"
    }

    private fun parseAPatchVersion(raw: String): String {
        val trimmed = raw.trim().removePrefix("APatch").removePrefix("apd").trim()
        return if (trimmed.startsWith("v")) trimmed else "v$trimmed"
    }

    fun rootInfo(): RootInfo = _rootInfo.value

    fun rootState(): RootState = _rootInfo.value.state

    fun suExec(command: String): Boolean = suExecRaw(command).isSuccess
}
