package io.github.ra1nify.realos.xposed.hooks.launcher

import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.FieldDescriptor
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor

/**
 * Resolved symbol data model for the launcher (descriptor layer).
 *
 * The class stores serializable descriptors only, with no `Class` / `Method` / `Field` handles:
 * - descriptors serialize to the disk cache; the next start restores them by reflection without a DexKit scan;
 * - [LauncherHandles] carries handles after restore, separate from the cache lifecycle;
 * - every hook point has a state in [statusMap]; a missing entry is [HookPointState.MISSING] and fails closed.
 */
data class LauncherSymbols(

    // ── Recents memory panel ──

    /** `com.oplus.quickstep.views.OplusClearAllPanelView` */
    val clearAllPanel: ClassDescriptor? = null,

    /** Memory panel refresh entry: `(boolean) -> void`, calls the memory manager inside */
    val refreshMemory: MethodDescriptor? = null,

    /** Memory text TextView field, located by unique type */
    val memoryTextView: FieldDescriptor? = null,

    /** Frame animation ValueAnimator field, located by exact type */
    val memoryAnimator: FieldDescriptor? = null,

    /**
     * Descriptors of all no-arg boolean methods in the memory manager.
     *
     * Both methods are released:
     * - show-allowed gate: the host `setOverviewStateEnabled` uses it to decide whether to
     *   call the refresh entry; while locked, the entry is never called;
     * - detail-needed gate: used only by the refresh entry, which this module already takes
     *   over; releasing it is a fallback only.
     */
    val memoryGates: List<MethodDescriptor> = emptyList(),

    /** Memory manager class, used to confirm gate candidates come from the same class */
    val memoryManager: ClassDescriptor? = null,

    /** Memory manager update method: `() -> void` (e.g. updateMemoDisplaySwitch) */
    val memoryManagerUpdate: MethodDescriptor? = null,

    // ── Launcher settings page ──

    /** `com.oplus.quickstep.locksetting.ui.LockSettingFragment` */
    val lockSettingFragment: ClassDescriptor? = null,

    /** `LockSettingFragment#onCreateView(LayoutInflater, ViewGroup, Bundle)` */
    val lockSettingOnCreateView: MethodDescriptor? = null,

    /** `LockSettingFragment#onResume()`: resets checked state and visibility */
    val lockSettingOnResume: MethodDescriptor? = null,

    /** `LockSettingFragment#onPreferenceChange(Preference, Object)`: switch persistence fallback */
    val lockSettingOnPreferenceChange: MethodDescriptor? = null,

    /**
     * `LockSettingFragment#updateMemoryInfoSwitch(boolean)`.
     *
     * The host refreshes the checked state with a constant false field in onCreateView and
     * onResume, so the argument is replaced with the persisted value.
     */
    val lockSettingUpdateMemorySwitch: MethodDescriptor? = null,

    // ── androidx.preference public types ──

    /** `androidx.preference.Preference` */
    val preference: ClassDescriptor? = null,

    /** `Preference#setVisible(boolean)` */
    val preferenceSetVisible: MethodDescriptor? = null,

    /**
     * `PreferenceGroup#addPreference(Preference)`.
     *
     * The host obfuscates the method name, so the lookup matches argument and return types in
     * both directions: a one-way `isAssignableFrom` also selects `setDefaultValue(Object)` and
     * `onSetInitialValue(Object)`, leaving the candidate set non-unique.
     */
    val preferenceGroupAdd: MethodDescriptor? = null,

    /** `PreferenceGroup#findPreference(CharSequence)` */
    val preferenceGroupFind: MethodDescriptor? = null,

    /** `androidx.preference.TwoStatePreference#setChecked(boolean)` */
    val twoStateSetChecked: MethodDescriptor? = null,

    /** `com.coui.appcompat.preference.COUISwitchPreference` */
    val couiSwitchPreference: ClassDescriptor? = null,

    // ── Recent task style ──

    /** `RecentStyleLayoutPreference#onBindViewHolder(PreferenceViewHolder)` */
    val recentStyleOnBindViewHolder: MethodDescriptor? = null,

    /**
     * Recent task card style read entry: `static (Context) -> String`.
     *
     * The host treats `Settings.Secure[key_recent_style]` as the single source of truth; this
     * static method reads and backfills that key, and its return value selects the card layout.
     */
    val recentStyleReader: MethodDescriptor? = null,

    /**
     * `OplusRecentsViewImpl#currentDelegate()`.
     *
     * The host's single delegate dispatch point and the layout plugin entry: the return type
     * is the obfuscated delegate interface, and no interface name is hardcoded.
     */
    val recentsCurrentDelegate: MethodDescriptor? = null,

    /** Readiness state of each hook point */
    val statusMap: Map<String, HookPointState> = emptyMap()
) {

    /** Whether the memory panel hooks have installation conditions */
    val canHookMemoryPanel: Boolean
        get() = clearAllPanel != null &&
            refreshMemory != null &&
            memoryTextView != null &&
            statusMap[KEY_PANEL_CLASS] == HookPointState.FOUND &&
            statusMap[KEY_REFRESH_ENTRY] == HookPointState.FOUND

    /** Whether the settings page injection has installation conditions */
    val canInjectSettings: Boolean
        get() = lockSettingFragment != null &&
            lockSettingOnCreateView != null &&
            preference != null &&
            preferenceSetVisible != null &&
            couiSwitchPreference != null &&
            preferenceGroupAdd != null &&
            statusMap[KEY_LOCK_SETTING_FRAGMENT] == HookPointState.FOUND
}

// ── Hook point keys ──
// Declared in one place so parser, business layer, and logs share the strings.

const val KEY_PANEL_CLASS = "panel_class"
const val KEY_REFRESH_ENTRY = "memory_refresh_entry"
const val KEY_MEMORY_TEXT_VIEW = "memory_text_view"
const val KEY_MEMORY_ANIMATOR = "memory_animator"
const val KEY_MEMORY_MANAGER = "memory_manager"
const val KEY_MEMORY_MANAGER_UPDATE = "memory_manager_update"
const val KEY_MEMORY_GATES = "memory_gates"
const val KEY_LOCK_SETTING_FRAGMENT = "lock_setting_fragment"
const val KEY_LOCK_SETTING_ON_CREATE_VIEW = "lock_setting_on_create_view"
const val KEY_LOCK_SETTING_ON_RESUME = "lock_setting_on_resume"
const val KEY_LOCK_SETTING_ON_PREF_CHANGE = "lock_setting_on_pref_change"
const val KEY_LOCK_SETTING_UPDATE_MEMORY_SWITCH = "lock_setting_update_memory_switch"
const val KEY_PREFERENCE_SET_VISIBLE = "preference_set_visible"
const val KEY_PREFERENCE_GROUP_ADD = "preference_group_add"
const val KEY_PREFERENCE_GROUP_FIND = "preference_group_find"
const val KEY_TWO_STATE_SET_CHECKED = "two_state_set_checked"
const val KEY_COUI_SWITCH_PREFERENCE = "coui_switch_preference"
const val KEY_RECENT_STYLE_PREFERENCE = "recent_style_preference"
const val KEY_RECENT_STYLE_READER = "recent_style_reader"
const val KEY_RECENTS_CURRENT_DELEGATE = "recents_current_delegate"

/** All hook point keys, iterated by log and status summaries */
val ALL_LAUNCHER_HOOK_KEYS: List<String> = listOf(
    KEY_PANEL_CLASS,
    KEY_REFRESH_ENTRY,
    KEY_MEMORY_TEXT_VIEW,
    KEY_MEMORY_ANIMATOR,
    KEY_MEMORY_MANAGER,
    KEY_MEMORY_MANAGER_UPDATE,
    KEY_MEMORY_GATES,
    KEY_LOCK_SETTING_FRAGMENT,
    KEY_LOCK_SETTING_ON_CREATE_VIEW,
    KEY_LOCK_SETTING_ON_RESUME,
    KEY_LOCK_SETTING_ON_PREF_CHANGE,
    KEY_LOCK_SETTING_UPDATE_MEMORY_SWITCH,
    KEY_PREFERENCE_SET_VISIBLE,
    KEY_PREFERENCE_GROUP_ADD,
    KEY_PREFERENCE_GROUP_FIND,
    KEY_TWO_STATE_SET_CHECKED,
    KEY_COUI_SWITCH_PREFERENCE,
    KEY_RECENT_STYLE_PREFERENCE,
    KEY_RECENT_STYLE_READER,
    KEY_RECENTS_CURRENT_DELEGATE
)
