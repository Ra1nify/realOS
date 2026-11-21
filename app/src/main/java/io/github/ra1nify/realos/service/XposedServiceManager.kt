package io.github.ra1nify.realos.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.widget.Toast
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.libxposed.service.XposedServiceHelper.OnServiceListener
import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.data.AppStatusCache
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import java.util.zip.ZipFile
import io.github.ra1nify.realos.data.XposedCachedStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object XposedServiceManager {

    private const val TAG = "RealOS-XposedService"

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _frameworkName = MutableStateFlow<String?>(null)
    val frameworkName: StateFlow<String?> = _frameworkName.asStateFlow()

    private val _frameworkVersion = MutableStateFlow<String?>(null)
    val frameworkVersion: StateFlow<String?> = _frameworkVersion.asStateFlow()

    private val _runningTargets = MutableStateFlow<List<HookedTarget>>(emptyList())
    val runningTargets: StateFlow<List<HookedTarget>> = _runningTargets.asStateFlow()

    private val _isCheckingXposed = MutableStateFlow(false)
    val isCheckingXposed: StateFlow<Boolean> = _isCheckingXposed.asStateFlow()

    private val _scope = MutableStateFlow<List<String>>(emptyList())
    val scope: StateFlow<List<String>> = _scope.asStateFlow()

    private val _targetScopePackages = MutableStateFlow<List<String>>(emptyList())

    /**
     * Injection scope package names declared in `META-INF/xposed/scope.list`.
     * Reads the APK zip central directory and exposes the result as a StateFlow.
     */
    val targetScopePackages: StateFlow<List<String>> = _targetScopePackages.asStateFlow()

    private val _isRequestingScope = MutableStateFlow(false)
    val isRequestingScope: StateFlow<Boolean> = _isRequestingScope.asStateFlow()

    private var currentService: XposedService? = null
    private var initialized = false
    private var appContext: Context? = null

    @Volatile
    private var hasProbedOnAppLaunch = false

    /**
     * Initializes manager with persistent cache and registers system framework listener.
     * Default state is loaded instantly from local cache without waiting for IPC.
     */
    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app

        // Loads cached state.
        val cached = AppStatusCache.loadXposedStatus(app)
        _serviceConnected.value = cached.connected
        _frameworkName.value = cached.frameworkName
        _frameworkVersion.value = cached.frameworkVersion

        if (initialized) return
        initialized = true

        // Reads scope.list on the IO dispatcher; the UI only collects the state.
        if (targetScopePackages.value.isEmpty()) {
            serviceScope.launch {
                runCatching { _targetScopePackages.value = getTargetScopePackages(app) }
            }
        }

        Log.i(TAG, "Registering XposedService listener...")
        XposedServiceHelper.registerListener(object : OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                Log.i(TAG, "Xposed service connected")
                currentService = service
                _serviceConnected.value = true
                updateFrameworkDetails(service)
                updateRunningTargets(service)
                updateScope(service)
                persistCacheIfChanged()
            }

            override fun onServiceDied(service: XposedService) {
                Log.w(TAG, "Xposed service died")
                if (currentService == service) {
                    currentService = null
                    _serviceConnected.value = false
                    _frameworkName.value = null
                    _frameworkVersion.value = null
                    _runningTargets.value = emptyList()
                    _scope.value = emptyList()
                    persistCacheIfChanged()
                }
            }
        })
    }

    /**
     * Resolves framework name according to realOS specification:
     * Supports LSPosed, EdXposed, DreamLand, Vector (JingMatrix), or generic Xposed.
     */
    fun resolveFrameworkTitle(name: String?): String {
        if (name.isNullOrBlank()) return "Xposed"
        return when {
            name.contains("LSPosed", ignoreCase = true) -> "LSPosed"
            name.contains("EdXposed", ignoreCase = true) -> "EdXposed"
            name.contains("DreamLand", ignoreCase = true) -> "DreamLand"
            name.contains("Vector", ignoreCase = true) -> "Vector"
            else -> name
        }
    }

    private fun updateFrameworkDetails(service: XposedService) {
        try {
            _frameworkName.value = service.frameworkName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get framework name", e)
        }
        try {
            _frameworkVersion.value = service.frameworkVersion
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get framework version", e)
        }
    }

    private fun updateRunningTargets(service: XposedService) {
        serviceScope.launch {
            try {
                val targets = suspendCancellableCoroutine<List<HookedTarget>> { continuation ->
                    try {
                        continuation.resume(service.runningTargets)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to query running targets", e)
                        continuation.resume(emptyList())
                    }
                }
                _runningTargets.value = targets
            } catch (t: Throwable) {
                Log.e(TAG, "Error updating running targets", t)
            }
        }
    }

    private fun persistCacheIfChanged() {
        val app = appContext ?: return
        val currentStatus = XposedCachedStatus(
            connected = _serviceConnected.value,
            frameworkName = _frameworkName.value,
            frameworkVersion = _frameworkVersion.value
        )
        val overwritten = AppStatusCache.saveXposedStatus(app, currentStatus)
        if (overwritten) {
            Log.i(TAG, "Xposed status cache updated: $currentStatus")
        }
    }

    /**
     * Runs the launch probe once per process.
     * A changed result overwrites the cache.
     */
    fun probeOnAppLaunch(context: Context) {
        if (hasProbedOnAppLaunch) return
        hasProbedOnAppLaunch = true
        refreshExplicit(context, isUserClick = false)
    }

    /**
     * User-triggered probe.
     * Clears framework name and version before the new lookup.
     */
    fun refreshExplicit(context: Context? = null, isUserClick: Boolean = true) {
        context?.let { appContext = it.applicationContext }
        if (_isCheckingXposed.value) return
        _isCheckingXposed.value = true

        if (isUserClick) {
            // Clears framework details before the new lookup.
            _frameworkName.value = null
            _frameworkVersion.value = null
        }

        serviceScope.launch {
            try {
                if (isUserClick) {
                    // Visual pause so the fallback to generic Xposed is perceptible as a transition.
        delay(350)
                }
                val service = currentService
                if (service != null) {
                    _serviceConnected.value = true
                    updateFrameworkDetails(service)
                    updateRunningTargets(service)
                    updateScope(service)
                } else if (!initialized) {
                    appContext?.let { initialize(it) }
                }
                persistCacheIfChanged()
                if (!isUserClick) {
                    delay(260)
                }
            } finally {
                _isCheckingXposed.value = false
            }
        }
    }

    fun getFrameworkName(): String? = _frameworkName.value ?: currentService?.let {
        runCatching { it.frameworkName }.getOrNull()
    }

    fun getFrameworkVersion(): String? = _frameworkVersion.value ?: currentService?.let {
        runCatching { it.frameworkVersion }.getOrNull()
    }

    fun getRemotePreferences(group: String): SharedPreferences? {
        val service = currentService ?: return null
        return try {
            service.getRemotePreferences(group)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get remote preferences", e)
            null
        }
    }

    suspend fun getRunningTargets(): List<HookedTarget> = suspendCancellableCoroutine { continuation ->
        val service = currentService
        if (service == null) {
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        try {
            continuation.resume(service.runningTargets)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get running targets", e)
            continuation.resume(emptyList())
        }
    }

    suspend fun getScope(): List<String> = suspendCancellableCoroutine { continuation ->
        val service = currentService
        if (service == null) {
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        try {
            continuation.resume(service.scope)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get scope", e)
            continuation.resume(emptyList())
        }
    }

    fun getTargetState(packageName: String): HookedTarget.State? {
        val targets = _runningTargets.value
        val target = targets.firstOrNull {
            val basePkg = it.processName.substringBefore(":")
            basePkg == packageName
        }
        return target?.state
    }

    fun updateScope(service: XposedService? = currentService) {
        val s = service ?: return
        serviceScope.launch {
            try {
                val active = suspendCancellableCoroutine<List<String>> { cont ->
                    try {
                        cont.resume(s.scope)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to query scope", e)
                        cont.resume(emptyList())
                    }
                }
                _scope.value = active
            } catch (t: Throwable) {
                Log.e(TAG, "Error updating scope", t)
            }
        }
    }

    fun isPackageInScope(pkg: String, activeScope: Collection<String>): Boolean {
        if (pkg == "system" || pkg == "android") {
            return activeScope.contains("system") || activeScope.contains("android")
        }
        return activeScope.contains(pkg)
    }

    @Volatile
    private var cachedTargetPackages: List<String>? = null

    fun getTargetScopePackages(context: Context): List<String> {
        cachedTargetPackages?.let {
            if (_targetScopePackages.value !== it) _targetScopePackages.value = it
            return it
        }
        val packages = mutableListOf<String>()
        try {
            val zip = ZipFile(context.packageCodePath)
            zip.use { z ->
                val entry = z.getEntry("META-INF/xposed/scope.list")
                if (entry != null) {
                    z.getInputStream(entry).bufferedReader().useLines { lines ->
                        for (line in lines) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                packages.add(trimmed)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read scope.list from APK", e)
        }

        val result = if (packages.isNotEmpty()) {
            packages.distinct()
        } else {
            val fallback = mutableListOf<String>()
            if (BuildConfig.SCOPE_OPLUS_KEYBOARD) fallback.add(SpecPackages.KEYBOARD_HOST)
            if (BuildConfig.SCOPE_NAVIGATION) {
                fallback.add(SpecPackages.SYSTEMUI)
                fallback.add(SpecPackages.SETTINGS)
            }
            if (BuildConfig.SCOPE_FEATURESWITCH) {
                fallback.add(SpecPackages.SETTINGS)
                fallback.add(SpecPackages.WALLPAPERS)
            }
            if (BuildConfig.SCOPE_FONT) fallback.add(SpecPackages.SETTINGS)
            if (BuildConfig.SCOPE_CAMERA) fallback.add("com.oplus.camera")
            if (BuildConfig.SCOPE_LAUNCHER) fallback.add("com.android.launcher")
            if (BuildConfig.SCOPE_WALLET) fallback.add("com.finshell.wallet")
            if (BuildConfig.SCOPE_MEDIA_PROVIDER) fallback.add("com.android.providers.media.module")
            if (BuildConfig.SCOPE_CLOCKFONT) {
                fallback.add(SpecPackages.WALLPAPERS)
                fallback.add(SpecPackages.SYSTEMUI)
                fallback.add(SpecPackages.LAUNCHER)
                fallback.add(SpecPackages.AOD)
            }
            if (BuildConfig.SCOPE_NAVIGATION || BuildConfig.SCOPE_FEATURESWITCH || BuildConfig.SCOPE_FONT) {
                fallback.add("system")
            }
            fallback.distinct()
        }

        cachedTargetPackages = result
        return result
    }

    fun requestScopeForMissing(context: Context) {
        val app = context.applicationContext
        val service = currentService
        if (service == null) {
            Toast.makeText(app, app.getString(R.string.scope_service_not_connected), Toast.LENGTH_SHORT).show()
            return
        }

        val targetPackages = getTargetScopePackages(app)
        val active = _scope.value
        val missing = targetPackages.filterNot { isPackageInScope(it, active) }

        if (missing.isEmpty()) {
            updateScope(service)
            Toast.makeText(app, app.getString(R.string.scope_all_granted), Toast.LENGTH_SHORT).show()
            return
        }

        if (_isRequestingScope.value) return
        _isRequestingScope.value = true

        try {
            Log.i(TAG, "Requesting scope for packages: $missing")
            service.requestScope(missing, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    _isRequestingScope.value = false
                    Log.i(TAG, "Scope request approved: $approved")
                    updateScope(service)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(app, app.getString(R.string.scope_request_approved), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onScopeRequestFailed(message: String) {
                    _isRequestingScope.value = false
                    Log.w(TAG, "Scope request failed: $message")
                    updateScope(service)
                    Handler(Looper.getMainLooper()).post {
                        val errorText = app.getString(R.string.scope_request_failed, message)
                        Toast.makeText(app, errorText, Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } catch (e: Exception) {
            _isRequestingScope.value = false
            Log.e(TAG, "Failed to call requestScope", e)
            Handler(Looper.getMainLooper()).post {
                val errorText = app.getString(R.string.scope_request_failed, e.message ?: "Unknown error")
                Toast.makeText(app, errorText, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
