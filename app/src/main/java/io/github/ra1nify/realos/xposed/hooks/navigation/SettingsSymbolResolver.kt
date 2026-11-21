package io.github.ra1nify.realos.xposed.hooks.navigation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.symbol.AntiTearingCache
import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor
import java.io.File
import java.lang.reflect.Method

/**
 * System Settings symbol resolver (Layer 1).
 *
 * Scans and caches class and method descriptors for navigation preference injection
 * and Compose sub-page host hooking. Uses [AntiTearingCache] for the disk cache.
 */
object SettingsSymbolResolver {

    private const val TAG = "RealOS-SettingsResolver"
    const val RULE_VERSION = 3
    private const val CACHE_PREFS = "realos_settings_symbols"

    private const val TARGET_FRAGMENT =
        "com.oplus.settings.feature.navbar.NavigationBarSettingsFragment"

    private const val SHELL_ACTIVITY =
        "com.oplus.settings.OplusSettingsActivity\$BackupRestoreEmptyActivity"

    private const val SETTINGS_ACTIVITY =
        "com.android.settings.SettingsActivity"

    fun resolve(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        apkPath: String?
    ): SettingsSymbols {
        val fingerprint = buildFingerprint(apkPath)
        val cache = AntiTearingCache(context, CACHE_PREFS, RULE_VERSION, tag = TAG)

        if (fingerprint.isNotBlank()) {
            val cachedPayload = cache.read(fingerprint)
            if (cachedPayload != null) {
                val cachedSymbols = runCatching {
                    SettingsSymbols.fromCachePayload(cachedPayload)
                }.getOrNull()

                if (cachedSymbols != null && verifyCached(cachedSymbols, classLoader)) {
                    module.log(Log.INFO, TAG, "Symbol cache hit (Settings, fingerprint=$fingerprint)")
                    return cachedSymbols
                } else {
                    module.log(Log.WARN, TAG, "Symbol cache verification failed, falling back to full scan")
                }
            }
        }

        module.log(Log.INFO, TAG, "Cache miss; performing structural symbol scan for Settings")
        val symbols = scan(module, classLoader, fingerprint)

        if (fingerprint.isNotBlank() && symbols.canInjectNav) {
            val written = cache.write(symbols.toCachePayload(fingerprint))
            if (written) {
                module.log(Log.INFO, TAG, "Persisted Settings symbols to cache (ruleVersion=$RULE_VERSION)")
            }
        }

        return symbols
    }

    private fun verifyCached(symbols: SettingsSymbols, classLoader: ClassLoader): Boolean {
        if (!symbols.canInjectNav) return false
        val fragmentClass = symbols.fragmentClass?.restore(classLoader) ?: return false
        val updateMethod = symbols.updatePreferenceViewMethod?.restore(classLoader) ?: return false
        return updateMethod.declaringClass.isAssignableFrom(fragmentClass)
    }

    private fun scan(module: XposedModule, classLoader: ClassLoader, fingerprint: String): SettingsSymbols {
        val states = mutableMapOf<String, HookPointState>()
        val scanErrors = mutableListOf<String>()

        fun logState(key: String, state: HookPointState, detail: String = "") {
            states[key] = state
            val msg = "HookPoint[$key] state=$state${if (detail.isNotEmpty()) " detail=$detail" else ""}"
            if (state == HookPointState.FOUND) {
                module.log(Log.INFO, TAG, msg)
            } else {
                module.log(Log.WARN, TAG, msg)
            }
        }

        fun safeLoadClass(name: String): Class<*>? = runCatching { classLoader.loadClass(name) }.getOrNull()

        // ── 1. Target Navigation Bar Settings Fragment ──
        val fragmentClass = safeLoadClass(TARGET_FRAGMENT)
        val fragmentDesc = fragmentClass?.let { ClassDescriptor(it.name) }
        logState(
            SettingsSymbols.KEY_NAV_FRAGMENT,
            if (fragmentClass != null) HookPointState.FOUND else HookPointState.MISSING
        )

        var updatePrefViewMethod: MethodDescriptor? = null
        var initPrefViewMethod: MethodDescriptor? = null
        var onCreatePrefsMethod: MethodDescriptor? = null
        var onNavTypeChangedMethod: MethodDescriptor? = null

        if (fragmentClass != null) {
            val updatePref = fragmentClass.declaredMethods.firstOrNull {
                it.name == "updatePreferenceView" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Integer.TYPE
            }
            updatePrefViewMethod = updatePref?.let { MethodDescriptor.of(it) }
            logState(
                SettingsSymbols.KEY_UPDATE_PREF_VIEW,
                if (updatePrefViewMethod != null) HookPointState.FOUND else HookPointState.MISSING
            )

            val initPref = fragmentClass.declaredMethods.firstOrNull {
                it.name == "initPreferenceView" && it.parameterTypes.isEmpty()
            }
            initPrefViewMethod = initPref?.let { MethodDescriptor.of(it) }
            logState(
                SettingsSymbols.KEY_INIT_PREF_VIEW,
                if (initPrefViewMethod != null) HookPointState.FOUND else HookPointState.MISSING
            )

            val onCreatePrefs = fragmentClass.declaredMethods.firstOrNull {
                it.name == "onCreatePreferences" && it.parameterTypes.size == 2
            }
            onCreatePrefsMethod = onCreatePrefs?.let { MethodDescriptor.of(it) }
            logState(
                SettingsSymbols.KEY_ON_CREATE_PREFS,
                if (onCreatePrefsMethod != null) HookPointState.FOUND else HookPointState.MISSING
            )

            val onNavChanged = fragmentClass.declaredMethods.firstOrNull {
                it.name == "onNavigationTypeChanged" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Integer.TYPE
            }
            onNavTypeChangedMethod = onNavChanged?.let { MethodDescriptor.of(it) }
            logState(
                SettingsSymbols.KEY_ON_NAV_TYPE_CHANGED,
                if (onNavTypeChangedMethod != null) HookPointState.FOUND else HookPointState.MISSING
            )
        } else {
            logState(SettingsSymbols.KEY_UPDATE_PREF_VIEW, HookPointState.MISSING)
            logState(SettingsSymbols.KEY_INIT_PREF_VIEW, HookPointState.MISSING)
            logState(SettingsSymbols.KEY_ON_CREATE_PREFS, HookPointState.MISSING)
            logState(SettingsSymbols.KEY_ON_NAV_TYPE_CHANGED, HookPointState.MISSING)
            scanErrors += "Failed to load fragment: $TARGET_FRAGMENT"
        }

        // ── 2. Settings Activity & Shell Host Activity ──
        val settingsClass = safeLoadClass(SETTINGS_ACTIVITY)
        val settingsDesc = settingsClass?.let { ClassDescriptor(it.name) }
        logState(
            SettingsSymbols.KEY_SETTINGS_ACTIVITY,
            if (settingsClass != null) HookPointState.FOUND else HookPointState.MISSING
        )

        val shellClass = safeLoadClass(SHELL_ACTIVITY)
        val isShellValid = shellClass != null && (settingsClass == null || settingsClass.isAssignableFrom(shellClass))
        val shellDesc = if (isShellValid) shellClass?.let { ClassDescriptor(it.name) } else null
        logState(
            SettingsSymbols.KEY_SHELL_ACTIVITY,
            if (shellDesc != null) HookPointState.FOUND else HookPointState.MISSING
        )

        var settingsOnCreateMethod: MethodDescriptor? = null
        var settingsOnActivityResultMethod: MethodDescriptor? = null

        if (settingsClass != null) {
            val onCreateMethod = runCatching {
                settingsClass.getDeclaredMethod("onCreate", Bundle::class.java)
            }.getOrNull()
            settingsOnCreateMethod = onCreateMethod?.let { MethodDescriptor.of(it) }
            logState(
                SettingsSymbols.KEY_SETTINGS_ON_CREATE,
                if (settingsOnCreateMethod != null) HookPointState.FOUND else HookPointState.MISSING
            )
        } else {
            logState(SettingsSymbols.KEY_SETTINGS_ON_CREATE, HookPointState.MISSING)
            scanErrors += "Failed to load activity: $SETTINGS_ACTIVITY"
        }

        // Search onActivityResult by traversing hierarchy from shellClass upwards
        var currCls: Class<*>? = shellClass ?: settingsClass
        var actResultMethod: Method? = null
        while (currCls != null && currCls != Any::class.java) {
            try {
                actResultMethod = currCls.getDeclaredMethod(
                    "onActivityResult",
                    Integer.TYPE,
                    Integer.TYPE,
                    Intent::class.java
                )
                break
            } catch (_: NoSuchMethodException) {
                currCls = currCls.superclass
            }
        }
        settingsOnActivityResultMethod = actResultMethod?.let { MethodDescriptor.of(it) }
        logState(
            SettingsSymbols.KEY_SETTINGS_ON_ACTIVITY_RESULT,
            if (settingsOnActivityResultMethod != null) HookPointState.FOUND else HookPointState.MISSING
        )

        // onNewIntent: shell Activities are mostly singleTop; reuse runs only this method.
        // getDeclaredMethod first, then getMethod for the inherited Activity implementation.
        var settingsOnNewIntentMethod: MethodDescriptor? = null
        val onNewIntentCls = settingsClass ?: shellClass
        if (onNewIntentCls != null) {
            val onNewIntentMethod = runCatching {
                onNewIntentCls.getDeclaredMethod("onNewIntent", Intent::class.java)
            }.getOrNull() ?: runCatching {
                onNewIntentCls.getMethod("onNewIntent", Intent::class.java)
            }.getOrNull()
            settingsOnNewIntentMethod = onNewIntentMethod?.let { MethodDescriptor.of(it) }
        }
        logState(
            SettingsSymbols.KEY_SETTINGS_ON_NEW_INTENT,
            if (settingsOnNewIntentMethod != null) HookPointState.FOUND else HookPointState.MISSING
        )

        return SettingsSymbols(
            ruleVersion = RULE_VERSION,
            hostFingerprint = fingerprint,
            fragmentClass = fragmentDesc,
            updatePreferenceViewMethod = updatePrefViewMethod,
            initPreferenceViewMethod = initPrefViewMethod,
            onCreatePreferencesMethod = onCreatePrefsMethod,
            onNavigationTypeChangedMethod = onNavTypeChangedMethod,
            shellActivityClass = shellDesc,
            settingsActivityClass = settingsDesc,
            settingsActivityOnCreateMethod = settingsOnCreateMethod,
            settingsActivityOnActivityResultMethod = settingsOnActivityResultMethod,
            settingsActivityOnNewIntentMethod = settingsOnNewIntentMethod,
            statusMap = states,
            scanErrors = scanErrors
        )
    }

    private fun buildFingerprint(apkPath: String?): String {
        if (apkPath.isNullOrBlank()) return ""
        val file = File(apkPath)
        return if (file.exists()) {
            "${file.absolutePath}|${file.lastModified()}|${file.length()}"
        } else {
            apkPath
        }
    }
}
