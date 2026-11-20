package io.github.ra1nify.realos.xposed.hooks.navigation

import java.lang.reflect.Method

/**
 * Live reflection handles restored from [SettingsSymbols] (Layer 2).
 *
 * Decouples descriptors cached on disk from ClassLoader-bound reflection handles.
 */
data class SettingsHandles(
    val fragmentClass: Class<*>? = null,
    val updatePreferenceViewMethod: Method? = null,
    val initPreferenceViewMethod: Method? = null,
    val onCreatePreferencesMethod: Method? = null,
    val onNavigationTypeChangedMethod: Method? = null,

    val shellActivityClass: Class<*>? = null,
    val settingsActivityClass: Class<*>? = null,
    val settingsActivityOnCreateMethod: Method? = null,
    val settingsActivityOnActivityResultMethod: Method? = null,
    val settingsActivityOnNewIntentMethod: Method? = null,

    val failedKeys: Set<String> = emptySet()
) {

    val shellActivityName: String?
        get() = shellActivityClass?.name

    val canInjectNav: Boolean
        get() = fragmentClass != null &&
            updatePreferenceViewMethod != null &&
            !failedKeys.contains(SettingsSymbols.KEY_NAV_FRAGMENT) &&
            !failedKeys.contains(SettingsSymbols.KEY_UPDATE_PREF_VIEW)

    val canHostSubPage: Boolean
        get() = shellActivityClass != null &&
            settingsActivityClass != null &&
            settingsActivityOnCreateMethod != null &&
            !failedKeys.contains(SettingsSymbols.KEY_SHELL_ACTIVITY) &&
            !failedKeys.contains(SettingsSymbols.KEY_SETTINGS_ON_CREATE)

    companion object {
        fun restore(symbols: SettingsSymbols, classLoader: ClassLoader): SettingsHandles {
            val failed = mutableSetOf<String>()

            fun <D : Any, T : Any> track(key: String, descriptor: D?, restore: (D) -> T?): T? {
                if (descriptor == null) return null
                val value = runCatching { restore(descriptor) }.getOrNull()
                if (value == null) failed += key
                return value
            }

            val fragmentClass = track(SettingsSymbols.KEY_NAV_FRAGMENT, symbols.fragmentClass) {
                it.restore(classLoader)
            }

            val updatePreferenceViewMethod = track(SettingsSymbols.KEY_UPDATE_PREF_VIEW, symbols.updatePreferenceViewMethod) {
                it.restore(classLoader)
            }

            val initPreferenceViewMethod = symbols.initPreferenceViewMethod?.restore(classLoader)
            val onCreatePreferencesMethod = symbols.onCreatePreferencesMethod?.restore(classLoader)
            val onNavigationTypeChangedMethod = symbols.onNavigationTypeChangedMethod?.restore(classLoader)

            val shellActivityClass = track(SettingsSymbols.KEY_SHELL_ACTIVITY, symbols.shellActivityClass) {
                it.restore(classLoader)
            }

            val settingsActivityClass = track(SettingsSymbols.KEY_SETTINGS_ACTIVITY, symbols.settingsActivityClass) {
                it.restore(classLoader)
            }

            // Verify shell activity inheritance relationship
            if (shellActivityClass != null && settingsActivityClass != null) {
                if (!settingsActivityClass.isAssignableFrom(shellActivityClass)) {
                    failed += SettingsSymbols.KEY_SHELL_ACTIVITY
                }
            }

            val settingsActivityOnCreateMethod = track(SettingsSymbols.KEY_SETTINGS_ON_CREATE, symbols.settingsActivityOnCreateMethod) {
                it.restore(classLoader)
            }

            val settingsActivityOnActivityResultMethod = symbols.settingsActivityOnActivityResultMethod?.restore(classLoader)
        val settingsActivityOnNewIntentMethod = symbols.settingsActivityOnNewIntentMethod?.restore(classLoader)

            return SettingsHandles(
                fragmentClass = fragmentClass,
                updatePreferenceViewMethod = updatePreferenceViewMethod,
                initPreferenceViewMethod = initPreferenceViewMethod,
                onCreatePreferencesMethod = onCreatePreferencesMethod,
                onNavigationTypeChangedMethod = onNavigationTypeChangedMethod,
                shellActivityClass = shellActivityClass,
                settingsActivityClass = settingsActivityClass,
                settingsActivityOnCreateMethod = settingsActivityOnCreateMethod,
                settingsActivityOnActivityResultMethod = settingsActivityOnActivityResultMethod,
            settingsActivityOnNewIntentMethod = settingsActivityOnNewIntentMethod,
                failedKeys = failed
            )
        }
    }
}
