package io.github.ra1nify.realos.xposed.hooks.launcher

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Reflection handles restored from [LauncherSymbols].
 *
 * Kept apart from the descriptor layer: descriptors serialize to cache, handles do not and bind
 * to a specific `ClassLoader`. Each process start restores descriptors into handles and records
 * failed hook points in [failedKeys]; the business layer short-circuits per hook point.
 */
data class LauncherHandles(
    /** `com.oplus.quickstep.views.OplusClearAllPanelView` */
    val clearAllPanelClass: Class<*>? = null,

    /** Memory panel refresh entry `(boolean) -> void` */
    val refreshMemoryMethod: Method? = null,

    /** Memory text TextView field */
    val memoryTextViewField: Field? = null,

    /** Frame animation ValueAnimator field */
    val memoryAnimatorField: Field? = null,

    /** All no-arg boolean methods of the memory manager (show allowed / detail needed) */
    val memoryGateMethods: List<Method> = emptyList(),

    /** Memory manager update method `() -> void` */
    val memoryManagerUpdateMethod: Method? = null,

    /** `LockSettingFragment` */
    val lockSettingFragmentClass: Class<*>? = null,
    val lockSettingOnCreateViewMethod: Method? = null,
    val lockSettingOnResumeMethod: Method? = null,
    val lockSettingOnPreferenceChangeMethod: Method? = null,
    val lockSettingUpdateMemorySwitchMethod: Method? = null,

    /** `androidx.preference.Preference` */
    val preferenceClass: Class<*>? = null,
    val preferenceSetVisibleMethod: Method? = null,
    val preferenceGroupAddMethod: Method? = null,
    val preferenceGroupFindMethod: Method? = null,
    val twoStateSetCheckedMethod: Method? = null,

    /** `COUISwitchPreference` */
    val couiSwitchPreferenceClass: Class<*>? = null,

    /** Recent task style */
    val recentStyleOnBindViewHolderMethod: Method? = null,
    val recentStyleReaderMethod: Method? = null,

    /** `OplusRecentsViewImpl#currentDelegate()` */
    val recentsCurrentDelegateMethod: Method? = null,

    /** Hook point keys whose restore failed (state is ERROR in [LauncherSymbols.statusMap]) */
    val failedKeys: Set<String> = emptySet()
) {

    /** Whether the memory panel hooks can be installed */
    val canHookMemoryPanel: Boolean
        get() = clearAllPanelClass != null &&
            refreshMemoryMethod != null &&
            memoryTextViewField != null

    /** Whether the settings page injection can be installed */
    val canInjectSettings: Boolean
        get() = lockSettingFragmentClass != null &&
            lockSettingOnCreateViewMethod != null &&
            preferenceClass != null &&
            preferenceSetVisibleMethod != null &&
            couiSwitchPreferenceClass != null &&
            preferenceGroupAddMethod != null

    companion object {

        /**
         * Restores descriptors into handles.
         *
         * A failed descriptor affects only its own hook point: the key goes into [failedKeys]
         * and the other restored handles stay usable.
         */
        fun restore(symbols: LauncherSymbols, classLoader: ClassLoader): LauncherHandles {
            val failed = mutableSetOf<String>()

            /** Restores non-null descriptors; a null result adds [failed] */
            fun <D : Any, T : Any> track(key: String, descriptor: D?, restore: (D) -> T?): T? {
                if (descriptor == null) return null
                val value = runCatching { restore(descriptor) }.getOrNull()
                if (value == null) failed += key
                return value
            }

            val panelClass = track(KEY_PANEL_CLASS, symbols.clearAllPanel) {
                it.restore(classLoader)
            }
            val lockSettingClass = track(KEY_LOCK_SETTING_FRAGMENT, symbols.lockSettingFragment) {
                it.restore(classLoader)
            }
            val preferenceClass = track(KEY_PREFERENCE_SET_VISIBLE, symbols.preference) {
                it.restore(classLoader)
            }
            val couiSwitchClass = track(KEY_COUI_SWITCH_PREFERENCE, symbols.couiSwitchPreference) {
                it.restore(classLoader)
            }

            return LauncherHandles(
                clearAllPanelClass = panelClass,
                refreshMemoryMethod = track(KEY_REFRESH_ENTRY, symbols.refreshMemory) {
                    it.restore(classLoader)
                },
                memoryTextViewField = track(KEY_MEMORY_TEXT_VIEW, symbols.memoryTextView) {
                    it.restore(classLoader)
                },
                memoryAnimatorField = track(KEY_MEMORY_ANIMATOR, symbols.memoryAnimator) {
                    it.restore(classLoader)
                },
                memoryGateMethods = restoreGates(symbols, classLoader, failed),
                memoryManagerUpdateMethod =
                    track(KEY_MEMORY_MANAGER_UPDATE, symbols.memoryManagerUpdate) {
                        it.restore(classLoader)
                    },
                lockSettingFragmentClass = lockSettingClass,
                lockSettingOnCreateViewMethod =
                    track(KEY_LOCK_SETTING_ON_CREATE_VIEW, symbols.lockSettingOnCreateView) {
                        it.restore(classLoader)
                    },
                lockSettingOnResumeMethod =
                    track(KEY_LOCK_SETTING_ON_RESUME, symbols.lockSettingOnResume) {
                        it.restore(classLoader)
                    },
                lockSettingOnPreferenceChangeMethod =
                    track(KEY_LOCK_SETTING_ON_PREF_CHANGE, symbols.lockSettingOnPreferenceChange) {
                        it.restore(classLoader)
                    },
                lockSettingUpdateMemorySwitchMethod =
                    track(KEY_LOCK_SETTING_UPDATE_MEMORY_SWITCH, symbols.lockSettingUpdateMemorySwitch) {
                        it.restore(classLoader)
                    },
                preferenceClass = preferenceClass,
                preferenceSetVisibleMethod = track(KEY_PREFERENCE_SET_VISIBLE, symbols.preferenceSetVisible) {
                    it.restore(classLoader)
                },
                preferenceGroupAddMethod = track(KEY_PREFERENCE_GROUP_ADD, symbols.preferenceGroupAdd) {
                    it.restore(classLoader)
                },
                preferenceGroupFindMethod = track(KEY_PREFERENCE_GROUP_FIND, symbols.preferenceGroupFind) {
                    it.restore(classLoader)
                },
                twoStateSetCheckedMethod = track(KEY_TWO_STATE_SET_CHECKED, symbols.twoStateSetChecked) {
                    it.restore(classLoader)
                },
                couiSwitchPreferenceClass = couiSwitchClass,
                recentStyleOnBindViewHolderMethod =
                    track(KEY_RECENT_STYLE_PREFERENCE, symbols.recentStyleOnBindViewHolder) {
                        it.restore(classLoader)
                    },
                recentStyleReaderMethod = track(KEY_RECENT_STYLE_READER, symbols.recentStyleReader) {
                        it.restore(classLoader)
                    },
                recentsCurrentDelegateMethod =
                    track(KEY_RECENTS_CURRENT_DELEGATE, symbols.recentsCurrentDelegate) {
                        it.restore(classLoader)
                    },
                failedKeys = failed
            )
        }

        /**
         * Restores the memory gate method list.
         *
         * Only an all-or-nothing result is accepted: a partial list leaves the gate semantics
         * incomplete, so any failure clears the whole list.
         */
        private fun restoreGates(
            symbols: LauncherSymbols,
            classLoader: ClassLoader,
            failed: MutableSet<String>
        ): List<Method> {
            if (symbols.memoryGates.isEmpty()) return emptyList()
            val restored = symbols.memoryGates.map { it.restore(classLoader) }
            if (restored.any { it == null }) {
                failed += KEY_MEMORY_GATES
                return emptyList()
            }
            @Suppress("UNCHECKED_CAST")
            return restored as List<Method>
        }
    }
}
