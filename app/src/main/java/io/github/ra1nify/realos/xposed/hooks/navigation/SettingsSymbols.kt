package io.github.ra1nify.realos.xposed.hooks.navigation

import org.json.JSONArray
import org.json.JSONObject
import io.github.ra1nify.realos.xposed.symbol.AntiTearingCache
import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.ClassSymbol
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor
import io.github.ra1nify.realos.xposed.symbol.MethodSymbol
import io.github.ra1nify.realos.xposed.symbol.SymbolDescriptor

/**
 * Data model for resolved System Settings symbols (Layer 2).
 *
 * Holds serializable descriptors for persistent cache, decoupling symbol
 * resolution from reflection handles.
 */
data class SettingsSymbols(
    val ruleVersion: Int = 1,
    val hostFingerprint: String = "",

    // Target navigation settings fragment & its hook methods
    val fragmentClass: ClassDescriptor? = null,
    val updatePreferenceViewMethod: MethodDescriptor? = null,
    val initPreferenceViewMethod: MethodDescriptor? = null,
    val onCreatePreferencesMethod: MethodDescriptor? = null,
    val onNavigationTypeChangedMethod: MethodDescriptor? = null,

    // Sub-page host activity & lifecycle methods
    val shellActivityClass: ClassDescriptor? = null,
    val settingsActivityClass: ClassDescriptor? = null,
    val settingsActivityOnCreateMethod: MethodDescriptor? = null,
    val settingsActivityOnActivityResultMethod: MethodDescriptor? = null,
    val settingsActivityOnNewIntentMethod: MethodDescriptor? = null,

    // Status map & diagnostic logs
    val statusMap: Map<String, HookPointState> = emptyMap(),
    val scanErrors: List<String> = emptyList()
) {

    /**
     * Whether navigation preferences can be safely injected into the host fragment.
     */
    val canInjectNav: Boolean
        get() = fragmentClass != null &&
            updatePreferenceViewMethod != null &&
            statusMap[KEY_NAV_FRAGMENT] == HookPointState.FOUND &&
            statusMap[KEY_UPDATE_PREF_VIEW] == HookPointState.FOUND

    /**
     * Whether the shell Activity is available to host Compose preference sub-pages.
     */
    val canHostSubPage: Boolean
        get() = shellActivityClass != null &&
            settingsActivityClass != null &&
            settingsActivityOnCreateMethod != null &&
            statusMap[KEY_SHELL_ACTIVITY] == HookPointState.FOUND &&
            statusMap[KEY_SETTINGS_ON_CREATE] == HookPointState.FOUND

    fun toCachePayload(fingerprint: String): AntiTearingCache.CachePayload {
        val map = buildMap<String, SymbolDescriptor> {
            fragmentClass?.let { put(KEY_NAV_FRAGMENT, ClassSymbol(it)) }
            updatePreferenceViewMethod?.let { put(KEY_UPDATE_PREF_VIEW, MethodSymbol(it)) }
            initPreferenceViewMethod?.let { put(KEY_INIT_PREF_VIEW, MethodSymbol(it)) }
            onCreatePreferencesMethod?.let { put(KEY_ON_CREATE_PREFS, MethodSymbol(it)) }
            onNavigationTypeChangedMethod?.let { put(KEY_ON_NAV_TYPE_CHANGED, MethodSymbol(it)) }
            shellActivityClass?.let { put(KEY_SHELL_ACTIVITY, ClassSymbol(it)) }
            settingsActivityClass?.let { put(KEY_SETTINGS_ACTIVITY, ClassSymbol(it)) }
            settingsActivityOnCreateMethod?.let { put(KEY_SETTINGS_ON_CREATE, MethodSymbol(it)) }
            settingsActivityOnActivityResultMethod?.let { put(KEY_SETTINGS_ON_ACTIVITY_RESULT, MethodSymbol(it)) }
            settingsActivityOnNewIntentMethod?.let { put(KEY_SETTINGS_ON_NEW_INTENT, MethodSymbol(it)) }
        }
        return AntiTearingCache.CachePayload(fingerprint, ruleVersion, map)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("ruleVersion", ruleVersion)
        put("hostFingerprint", hostFingerprint)

        fragmentClass?.let { put("fragmentClass", it.toJson()) }
        updatePreferenceViewMethod?.let { put("updatePreferenceViewMethod", it.toJson()) }
        initPreferenceViewMethod?.let { put("initPreferenceViewMethod", it.toJson()) }
        onCreatePreferencesMethod?.let { put("onCreatePreferencesMethod", it.toJson()) }
        onNavigationTypeChangedMethod?.let { put("onNavigationTypeChangedMethod", it.toJson()) }

        shellActivityClass?.let { put("shellActivityClass", it.toJson()) }
        settingsActivityClass?.let { put("settingsActivityClass", it.toJson()) }
        settingsActivityOnCreateMethod?.let { put("settingsActivityOnCreateMethod", it.toJson()) }
        settingsActivityOnActivityResultMethod?.let { put("settingsActivityOnActivityResultMethod", it.toJson()) }
        settingsActivityOnNewIntentMethod?.let { put("settingsActivityOnNewIntentMethod", it.toJson()) }

        val statusObj = JSONObject()
        statusMap.forEach { (k, v) -> statusObj.put(k, v.name) }
        put("statusMap", statusObj)

        if (scanErrors.isNotEmpty()) {
            put("scanErrors", JSONArray().apply { scanErrors.forEach { put(it) } })
        }
    }

    companion object {
        const val KEY_NAV_FRAGMENT = "settings_nav_fragment"
        const val KEY_UPDATE_PREF_VIEW = "settings_nav_update_pref_view"
        const val KEY_INIT_PREF_VIEW = "settings_nav_init_pref_view"
        const val KEY_ON_CREATE_PREFS = "settings_nav_on_create_prefs"
        const val KEY_ON_NAV_TYPE_CHANGED = "settings_nav_on_nav_type_changed"

        const val KEY_SHELL_ACTIVITY = "settings_shell_activity"
        const val KEY_SETTINGS_ACTIVITY = "settings_activity"
        const val KEY_SETTINGS_ON_CREATE = "settings_activity_on_create"
        const val KEY_SETTINGS_ON_ACTIVITY_RESULT = "settings_activity_on_activity_result"
        const val KEY_SETTINGS_ON_NEW_INTENT = "settings_activity_on_new_intent"

        val ALL_SETTINGS_HOOK_KEYS: List<String> = listOf(
            KEY_NAV_FRAGMENT,
            KEY_UPDATE_PREF_VIEW,
            KEY_INIT_PREF_VIEW,
            KEY_ON_CREATE_PREFS,
            KEY_ON_NAV_TYPE_CHANGED,
            KEY_SHELL_ACTIVITY,
            KEY_SETTINGS_ACTIVITY,
            KEY_SETTINGS_ON_CREATE,
            KEY_SETTINGS_ON_ACTIVITY_RESULT,
            KEY_SETTINGS_ON_NEW_INTENT
        )

        val GROUP_KEYS: List<String> = listOf(
            "virtual_key_combination_pref_key",
            "gesture_up_component_pref_key",
            "gesture_side_component_pref_key"
        )

        fun fromCachePayload(payload: AntiTearingCache.CachePayload): SettingsSymbols {
            val symbols = payload.symbols
            val fragmentClass = (symbols[KEY_NAV_FRAGMENT] as? ClassSymbol)?.descriptor
            val updatePref = (symbols[KEY_UPDATE_PREF_VIEW] as? MethodSymbol)?.descriptor
            val initPref = (symbols[KEY_INIT_PREF_VIEW] as? MethodSymbol)?.descriptor
            val onCreatePrefs = (symbols[KEY_ON_CREATE_PREFS] as? MethodSymbol)?.descriptor
            val onNavChanged = (symbols[KEY_ON_NAV_TYPE_CHANGED] as? MethodSymbol)?.descriptor
            val shellClass = (symbols[KEY_SHELL_ACTIVITY] as? ClassSymbol)?.descriptor
            val settingsClass = (symbols[KEY_SETTINGS_ACTIVITY] as? ClassSymbol)?.descriptor
            val settingsOnCreate = (symbols[KEY_SETTINGS_ON_CREATE] as? MethodSymbol)?.descriptor
            val settingsOnActivityResult = (symbols[KEY_SETTINGS_ON_ACTIVITY_RESULT] as? MethodSymbol)?.descriptor
            val settingsOnNewIntent = (symbols[KEY_SETTINGS_ON_NEW_INTENT] as? MethodSymbol)?.descriptor

            val statusMap = mutableMapOf<String, HookPointState>()
            statusMap[KEY_NAV_FRAGMENT] = if (fragmentClass != null) HookPointState.FOUND else HookPointState.MISSING
            statusMap[KEY_UPDATE_PREF_VIEW] = if (updatePref != null) HookPointState.FOUND else HookPointState.MISSING
            statusMap[KEY_INIT_PREF_VIEW] = if (initPref != null) HookPointState.FOUND else HookPointState.MISSING
            statusMap[KEY_ON_CREATE_PREFS] = if (onCreatePrefs != null) HookPointState.FOUND else HookPointState.MISSING
            statusMap[KEY_ON_NAV_TYPE_CHANGED] = if (onNavChanged != null) HookPointState.FOUND else HookPointState.MISSING
            statusMap[KEY_SHELL_ACTIVITY] = if (shellClass != null) HookPointState.FOUND else HookPointState.MISSING
            statusMap[KEY_SETTINGS_ACTIVITY] = if (settingsClass != null) HookPointState.FOUND else HookPointState.MISSING
            statusMap[KEY_SETTINGS_ON_CREATE] = if (settingsOnCreate != null) HookPointState.FOUND else HookPointState.MISSING
            statusMap[KEY_SETTINGS_ON_ACTIVITY_RESULT] = if (settingsOnActivityResult != null) HookPointState.FOUND else HookPointState.MISSING
            statusMap[KEY_SETTINGS_ON_NEW_INTENT] = if (settingsOnNewIntent != null) HookPointState.FOUND else HookPointState.MISSING

            return SettingsSymbols(
                ruleVersion = payload.ruleVersion,
                hostFingerprint = payload.fingerprint,
                fragmentClass = fragmentClass,
                updatePreferenceViewMethod = updatePref,
                initPreferenceViewMethod = initPref,
                onCreatePreferencesMethod = onCreatePrefs,
                onNavigationTypeChangedMethod = onNavChanged,
                shellActivityClass = shellClass,
                settingsActivityClass = settingsClass,
                settingsActivityOnCreateMethod = settingsOnCreate,
                settingsActivityOnActivityResultMethod = settingsOnActivityResult,
                settingsActivityOnNewIntentMethod = settingsOnNewIntent,
                statusMap = statusMap
            )
        }

        fun fromJson(json: JSONObject): SettingsSymbols {
            val statusMap = mutableMapOf<String, HookPointState>()
            json.optJSONObject("statusMap")?.let { obj ->
                obj.keys().forEach { k ->
                    statusMap[k] = runCatching { HookPointState.valueOf(obj.getString(k)) }.getOrDefault(HookPointState.MISSING)
                }
            }

            val scanErrors = mutableListOf<String>()
            json.optJSONArray("scanErrors")?.let { arr ->
                for (i in 0 until arr.length()) scanErrors.add(arr.getString(i))
            }

            fun optClass(key: String) = json.optJSONObject(key)?.let { ClassDescriptor.fromJson(it) }
            fun optMethod(key: String) = json.optJSONObject(key)?.let { MethodDescriptor.fromJson(it) }

            return SettingsSymbols(
                ruleVersion = json.optInt("ruleVersion", 1),
                hostFingerprint = json.optString("hostFingerprint", ""),
                fragmentClass = optClass("fragmentClass"),
                updatePreferenceViewMethod = optMethod("updatePreferenceViewMethod"),
                initPreferenceViewMethod = optMethod("initPreferenceViewMethod"),
                onCreatePreferencesMethod = optMethod("onCreatePreferencesMethod"),
                onNavigationTypeChangedMethod = optMethod("onNavigationTypeChangedMethod"),
                shellActivityClass = optClass("shellActivityClass"),
                settingsActivityClass = optClass("settingsActivityClass"),
                settingsActivityOnCreateMethod = optMethod("settingsActivityOnCreateMethod"),
                settingsActivityOnActivityResultMethod = optMethod("settingsActivityOnActivityResultMethod"),
                settingsActivityOnNewIntentMethod = optMethod("settingsActivityOnNewIntentMethod"),
                statusMap = statusMap,
                scanErrors = scanErrors
            )
        }
    }
}
