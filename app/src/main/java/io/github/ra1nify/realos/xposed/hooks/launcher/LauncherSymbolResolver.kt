package io.github.ra1nify.realos.xposed.hooks.launcher

import android.animation.ValueAnimator
import android.app.Application
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.symbol.AntiTearingCache
import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.ClassSymbol
import io.github.ra1nify.realos.xposed.symbol.DexKitRunner
import io.github.ra1nify.realos.xposed.symbol.FieldDescriptor
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor
import io.github.ra1nify.realos.xposed.symbol.MethodSymbol
import io.github.ra1nify.realos.xposed.symbol.SymbolDescriptor
import java.lang.reflect.Modifier

/**
 * ColorOS launcher symbol resolver (Layer 1: the only layer that uses DexKit and structure scans).
 *
 * Rules:
 * - unobfuscated public host classes load by full name (AGENTS.md §3.1) with structure checks;
 * - obfuscated methods and fields resolve by structure (method signature, caller relations,
 *   unique field type); single-letter names from decompiled samples stay out of business logic;
 * - output is serializable descriptors persisted through [AntiTearingCache] (fingerprint + ruleVersion);
 * - a non-unique or missing critical symbol only marks its state; the business layer fails closed.
 */
object LauncherSymbolResolver {

    private const val TAG = "RealOS-LauncherResolver"

    /**
     * Resolution rule version.
     *
     * Any change that affects resolution output (anchors, structure criteria, feature priority)
     * increments this value, so stale caches do not mask new rules.
     */
    const val RULE_VERSION = 2

    private const val CACHE_PREFS = "realos_launcher_symbols"

    // ── Unobfuscated public host types (AGENTS.md §3.1) ──
    private const val PANEL_CLASS = "com.oplus.quickstep.views.OplusClearAllPanelView"
    private const val LOCK_SETTING_FRAGMENT = "com.oplus.quickstep.locksetting.ui.LockSettingFragment"
    private const val COUI_SWITCH_PREFERENCE = "com.coui.appcompat.preference.COUISwitchPreference"
    private const val PREFERENCE_CLASS = "androidx.preference.Preference"
    private const val PREFERENCE_GROUP_CLASS = "androidx.preference.PreferenceGroup"
    private const val PREFERENCE_VIEW_HOLDER = "androidx.preference.PreferenceViewHolder"
    private const val TWO_STATE_PREFERENCE_CLASS = "androidx.preference.TwoStatePreference"
    private const val RECENT_STYLE_PREFERENCE =
        "com.oplus.quickstep.locksetting.ui.RecentStyleLayoutPreference"
    private const val RECENTS_VIEW_CLASS = "com.android.quickstep.views.OplusRecentsViewImpl"

    /**
     * Unique anchor of the memory manager: a Settings.Secure key read and written only by the
     * memory manager. A caller relation from the memory panel narrows it to the refresh entry.
     */
    private const val MEMORY_MANAGER_ANCHOR = "allow_memory_info_display"

    /**
     * Unique anchor of the recent task card style: a Settings.Secure key that selects the card layout.
     */
    private const val RECENT_STYLE_ANCHOR = "key_recent_style"

    fun resolve(module: XposedModule, app: Application, classLoader: ClassLoader): LauncherSymbols {
        val states = mutableMapOf<String, HookPointState>()
        val apkPath = app.applicationInfo?.sourceDir

        // ── Public types: direct load and verification ──
        val panelClass = classOrNull(classLoader, PANEL_CLASS)
        states[KEY_PANEL_CLASS] = panelClass.state()

        val lockSettingClass = classOrNull(classLoader, LOCK_SETTING_FRAGMENT)
        states[KEY_LOCK_SETTING_FRAGMENT] = lockSettingClass.state()

        val preferenceClass = classOrNull(classLoader, PREFERENCE_CLASS)
        val twoStateClass = classOrNull(classLoader, TWO_STATE_PREFERENCE_CLASS)
        val groupClass = classOrNull(classLoader, PREFERENCE_GROUP_CLASS)
        val preferenceViewHolder = classOrNull(classLoader, PREFERENCE_VIEW_HOLDER)

        // COUI switch: usable only when the class exists and has a (Context) constructor
        val couiSwitchClass = classOrNull(classLoader, COUI_SWITCH_PREFERENCE)
        states[KEY_COUI_SWITCH_PREFERENCE] = when {
            couiSwitchClass == null -> HookPointState.MISSING
            runCatching { couiSwitchClass.getConstructor(android.content.Context::class.java) }.isSuccess ->
                HookPointState.FOUND
            else -> HookPointState.ERROR
        }

        // ── Memory panel fields: located by unique type ──
        val textViewField = singleFieldOfType(panelClass, TextView::class.java)
        states[KEY_MEMORY_TEXT_VIEW] = textViewField.state()

        // ObjectAnimator extends ValueAnimator, so == keeps the match unique
        val animatorField = singleFieldOfType(panelClass, ValueAnimator::class.java)
        // The animation is optional; a missing field degrades to DISABLED instead of MISSING
        states[KEY_MEMORY_ANIMATOR] = when {
            animatorField != null -> HookPointState.FOUND
            panelClass == null -> HookPointState.MISSING
            else -> HookPointState.DISABLED
        }

        // ── Public settings page methods ──
        val onCreateView = lockSettingClass?.methodOrNull(
            "onCreateView",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            android.os.Bundle::class.java
        )
        states[KEY_LOCK_SETTING_ON_CREATE_VIEW] = onCreateView.state()

        val onPreferenceChange = if (lockSettingClass != null && preferenceClass != null) {
            lockSettingClass.methodOrNull("onPreferenceChange", preferenceClass, Any::class.java)
        } else null
        states[KEY_LOCK_SETTING_ON_PREF_CHANGE] = onPreferenceChange.state()

        val updateMemorySwitch = lockSettingClass?.methodOrNull(
            "updateMemoryInfoSwitch",
            java.lang.Boolean.TYPE
        )
        states[KEY_LOCK_SETTING_UPDATE_MEMORY_SWITCH] = updateMemorySwitch.state()

        val setVisible = preferenceClass?.methodOrNull("setVisible", java.lang.Boolean.TYPE)
        states[KEY_PREFERENCE_SET_VISIBLE] = setVisible.state()

        val twoStateSetChecked = twoStateClass?.methodOrNull("setChecked", java.lang.Boolean.TYPE)
        states[KEY_TWO_STATE_SET_CHECKED] = twoStateSetChecked.state()

        // ── PreferenceGroup add / find: obfuscated names, resolved by structure ──
        val groupAdd = resolveGroupAdd(groupClass, preferenceClass)
        states[KEY_PREFERENCE_GROUP_ADD] = when {
            groupAdd != null -> HookPointState.FOUND
            groupClass == null || preferenceClass == null -> HookPointState.MISSING
            else -> HookPointState.ERROR
        }
        val groupFind = resolveGroupFind(groupClass, preferenceClass)
        states[KEY_PREFERENCE_GROUP_FIND] = when {
            groupFind != null -> HookPointState.FOUND
            groupClass == null || preferenceClass == null -> HookPointState.MISSING
            else -> HookPointState.ERROR
        }

        // ── Recent task style onBindViewHolder ──
        val recentStyleClass = classOrNull(classLoader, RECENT_STYLE_PREFERENCE)
        val recentStyleOnBind = if (recentStyleClass != null && preferenceViewHolder != null) {
            recentStyleClass.methodOrNull("onBindViewHolder", preferenceViewHolder)
        } else null
        states[KEY_RECENT_STYLE_PREFERENCE] = when {
            recentStyleOnBind != null -> HookPointState.FOUND
            recentStyleClass == null -> HookPointState.MISSING
            else -> HookPointState.ERROR
        }

        // ── Recents delegate entry: method only; the plugin resolves delegate symbols on activation ──
        val recentsClass = classOrNull(classLoader, RECENTS_VIEW_CLASS)
        val currentDelegate = recentsClass?.let { clazz ->
            runCatching { clazz.getMethod("currentDelegate") }.getOrNull()
        }
        states[KEY_RECENTS_CURRENT_DELEGATE] = when {
            currentDelegate == null -> HookPointState.MISSING
            currentDelegate.returnType.isInterface -> HookPointState.FOUND
            else -> HookPointState.ERROR
        }

        // ── Obfuscated symbols: cache restore or DexKit scan ──
        val cache = AntiTearingCache(
            context = app,
            prefsName = CACHE_PREFS,
            ruleVersion = RULE_VERSION,
            tag = TAG
        )
        val fingerprint = fingerprintOf(apkPath)
        val structural = resolveStructural(module, cache, fingerprint, apkPath, classLoader, panelClass, states)

        // ── Memory gates: all no-arg boolean methods of the manager, exactly two ──
        val gates = if (structural.memoryManager != null) {
            val managerClass = structural.memoryManager.restore(classLoader)
            val candidates = managerClass?.declaredMethods?.filter {
                it.parameterCount == 0 &&
                    it.returnType == java.lang.Boolean.TYPE &&
                    Modifier.isPublic(it.modifiers)
            }.orEmpty()
            if (candidates.size == GATE_COUNT) {
                states[KEY_MEMORY_GATES] = HookPointState.FOUND
                candidates.map { MethodDescriptor.of(it) }
            } else {
                module.log(
                    Log.WARN, TAG,
                    "HookPoint[$KEY_MEMORY_GATES] state=ERROR candidates=${candidates.size} expected=$GATE_COUNT"
                )
                states[KEY_MEMORY_GATES] = HookPointState.ERROR
                emptyList()
            }
        } else {
            states[KEY_MEMORY_GATES] = HookPointState.MISSING
            emptyList()
        }

        logStates(module, states)

        return LauncherSymbols(
            clearAllPanel = panelClass?.let { ClassDescriptor(it.name) },
            refreshMemory = structural.refreshEntry,
            memoryTextView = textViewField?.let { FieldDescriptor.of(it) },
            memoryAnimator = animatorField?.let { FieldDescriptor.of(it) },
            memoryGates = gates,
            memoryManager = structural.memoryManager,
            lockSettingFragment = lockSettingClass?.let { ClassDescriptor(it.name) },
            lockSettingOnCreateView = onCreateView?.let { MethodDescriptor.of(it) },
            lockSettingOnPreferenceChange = onPreferenceChange?.let { MethodDescriptor.of(it) },
            lockSettingUpdateMemorySwitch = updateMemorySwitch?.let { MethodDescriptor.of(it) },
            preference = preferenceClass?.let { ClassDescriptor(it.name) },
            preferenceSetVisible = setVisible?.let { MethodDescriptor.of(it) },
            preferenceGroupAdd = groupAdd?.let { MethodDescriptor.of(it) },
            preferenceGroupFind = groupFind?.let { MethodDescriptor.of(it) },
            twoStateSetChecked = twoStateSetChecked?.let { MethodDescriptor.of(it) },
            couiSwitchPreference = couiSwitchClass?.let { ClassDescriptor(it.name) },
            recentStyleOnBindViewHolder = recentStyleOnBind?.let { MethodDescriptor.of(it) },
            recentStyleReader = structural.recentStyleReader,
            recentsCurrentDelegate = currentDelegate?.let { MethodDescriptor.of(it) },
            statusMap = states.toMap()
        )
    }

    /** Expected memory gate method count (show allowed / detail needed) */
    private const val GATE_COUNT = 2

    // ── Structural symbols (DexKit required) ───────────────────────────────────────────

    private data class Structural(
        val memoryManager: ClassDescriptor? = null,
        val refreshEntry: MethodDescriptor? = null,
        val recentStyleReader: MethodDescriptor? = null
    )

    /**
     * Resolves obfuscated symbols that need DexKit, preferring the cache.
     *
     * A cache hit performs reflection restore validation without touching DexKit; a miss scans and writes back.
     */
    private fun resolveStructural(
        module: XposedModule,
        cache: AntiTearingCache,
        fingerprint: String,
        apkPath: String?,
        classLoader: ClassLoader,
        panelClass: Class<*>?,
        states: MutableMap<String, HookPointState>
    ): Structural {
        // ── Cache restore ──
        cache.read(fingerprint)?.let { payload ->
            val manager = payload.symbols[CK_MEMORY_MANAGER]
            val refresh = payload.symbols[CK_REFRESH_ENTRY]
            val styleReader = payload.symbols[CK_RECENT_STYLE_READER]

            val managerDescriptor = (manager as? ClassSymbol)?.descriptor
            val refreshDescriptor = (refresh as? MethodSymbol)?.descriptor
            val styleDescriptor = (styleReader as? MethodSymbol)?.descriptor

            // Restore validation: each descriptor must resolve to a real handle under the current classLoader.
            // A null entry is not a validation failure; a symbol unresolved last time does not block others.
            val managerOk = managerDescriptor == null || managerDescriptor.restore(classLoader) != null
            val refreshOk = refreshDescriptor == null || refreshDescriptor.restore(classLoader) != null
            val styleOk = styleDescriptor == null || styleDescriptor.restore(classLoader) != null

            if (managerOk && refreshOk && styleOk) {
                states[KEY_MEMORY_MANAGER] = managerDescriptor.state()
                states[KEY_REFRESH_ENTRY] = refreshDescriptor.state()
                states[KEY_RECENT_STYLE_READER] = styleDescriptor.state()
                module.log(
                    Log.INFO, TAG,
                    "Symbol cache hit (ruleVersion=$RULE_VERSION): " +
                        "manager=${managerDescriptor?.className ?: "null"} " +
                        "refresh=${refreshDescriptor?.methodName ?: "null"} " +
                        "styleReader=${styleDescriptor?.methodName ?: "null"}"
                )
                return Structural(managerDescriptor, refreshDescriptor, styleDescriptor)
            }
            module.log(Log.WARN, TAG, "Symbol cache restore verification failed; rescanning")
        }

        if (apkPath.isNullOrBlank() || panelClass == null) {
            states[KEY_MEMORY_MANAGER] = HookPointState.MISSING
            states[KEY_REFRESH_ENTRY] = HookPointState.MISSING
            states[KEY_RECENT_STYLE_READER] = HookPointState.MISSING
            if (apkPath.isNullOrBlank()) {
                module.log(Log.WARN, TAG, "Host APK path is empty; skipping the DexKit structural scan")
            }
            return Structural()
        }

        // ── Fresh scan: DexKit unavailable (native load failure / scan exception) marks everything ERROR ──
        val scanned = DexKitRunner.withBridge(module, apkPath) { bridge ->
            scanWithDexKit(module, bridge, panelClass, classLoader)
        }
        if (scanned == null) {
            states[KEY_MEMORY_MANAGER] = HookPointState.ERROR
            states[KEY_REFRESH_ENTRY] = HookPointState.ERROR
            states[KEY_RECENT_STYLE_READER] = HookPointState.ERROR
            return Structural()
        }

        // Partial success is marked per symbol: resolved is FOUND, unresolved is MISSING
        states[KEY_MEMORY_MANAGER] = scanned.memoryManager.state()
        states[KEY_REFRESH_ENTRY] = scanned.refreshEntry.state()
        states[KEY_RECENT_STYLE_READER] = scanned.recentStyleReader.state()

        // Caches resolved symbols only; partial results are written so the next start scans less
        val toCache = buildMap<String, SymbolDescriptor> {
            scanned.memoryManager?.let { put(CK_MEMORY_MANAGER, ClassSymbol(it)) }
            scanned.refreshEntry?.let { put(CK_REFRESH_ENTRY, MethodSymbol(it)) }
            scanned.recentStyleReader?.let { put(CK_RECENT_STYLE_READER, MethodSymbol(it)) }
        }
        if (toCache.isNotEmpty()) {
            cache.write(AntiTearingCache.CachePayload(fingerprint, RULE_VERSION, toCache))
        }

        return scanned
    }

    /** Cache key names */
    private const val CK_MEMORY_MANAGER = "memory_manager"
    private const val CK_REFRESH_ENTRY = "refresh_entry"
    private const val CK_RECENT_STYLE_READER = "recent_style_reader"

    /**
     * One DexKit scan resolves the three structural symbols with a single bridge.
     *
     * The three sub-chains are independent: a memory manager failure affects only the memory
     * panel hook points, and the recent task style reader still resolves on its own.
     */
    private fun scanWithDexKit(
        module: XposedModule,
        bridge: org.luckypray.dexkit.DexKitBridge,
        panelClass: Class<*>,
        classLoader: ClassLoader
    ): Structural {
        var memoryManager: ClassDescriptor? = null
        var refreshEntry: MethodDescriptor? = null

        // ── Sub-chain A: recent task style reader `static (Context) -> String` ──
        val styleReader = resolveStyleReader(bridge, classLoader)

        // ── Sub-chain B: memory manager + refresh entry ──
        val managerClasses = bridge.findClass {
            matcher { usingStrings(MEMORY_MANAGER_ANCHOR) }
        }
        if (managerClasses.size != 1) {
            module.log(
                Log.WARN, TAG,
                "HookPoint[$KEY_MEMORY_MANAGER] state=ERROR anchor candidates=${managerClasses.size}; cannot converge on the refresh entry"
            )
        } else {
            val managerName = managerClasses.first().name
            memoryManager = ClassDescriptor(managerName)

            val refreshCandidate = bridge.findMethod {
                matcher {
                    declaredClass(panelClass.name)
                    addInvoke { declaredClass(managerName) }
                }
            }.filter { m ->
                m.paramCount == 1 &&
                    (m.paramTypeNames.getOrNull(0) == "boolean" || m.paramTypeNames.getOrNull(0) == "Z") &&
                    (m.returnTypeName == "void" || m.returnTypeName == "V")
            }.singleOrNull()

            if (refreshCandidate == null) {
                module.log(Log.WARN, TAG, "HookPoint[$KEY_REFRESH_ENTRY] state=ERROR candidates are not unique")
            } else {
                val refreshMethod = panelClass.methodOrNull(
                    refreshCandidate.name,
                    java.lang.Boolean.TYPE
                )
                if (refreshMethod == null) {
                    module.log(
                        Log.WARN, TAG,
                        "HookPoint[$KEY_REFRESH_ENTRY] state=ERROR reflective restore failed: ${refreshCandidate.name}"
                    )
                } else {
                    refreshEntry = MethodDescriptor.of(refreshMethod)
                }
            }
        }

        module.log(
            Log.INFO, TAG,
            "Structural symbols: manager=${memoryManager?.className ?: "null"} " +
                "refresh=${refreshEntry?.methodName ?: "null"} " +
                "styleReader=${styleReader?.methodName ?: "null"}"
        )

        return Structural(
            memoryManager = memoryManager,
            refreshEntry = refreshEntry,
            recentStyleReader = styleReader
        )
    }

    /**
     * Locates the recent task card style read entry.
     *
     * Two-stage narrowing: the `key_recent_style` anchor plus signature narrows to one method,
     * then `declaredClassName` reflection restores it and a static + (Context) → String check
     * rejects overloads under the same anchor.
     */
    private fun resolveStyleReader(
        bridge: org.luckypray.dexkit.DexKitBridge,
        classLoader: ClassLoader
    ): MethodDescriptor? {
        val candidates = bridge.findMethod {
            matcher {
                usingStrings(RECENT_STYLE_ANCHOR)
                paramTypes("android.content.Context")
                returnType("java.lang.String")
            }
        }
        val target = candidates.singleOrNull() ?: return null

        val clazz = runCatching { classLoader.loadClass(target.declaredClassName) }.getOrNull()
            ?: return null

        val method = runCatching {
            clazz.methods.firstOrNull {
                it.name == target.name &&
                    Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.contentEquals(arrayOf(android.content.Context::class.java)) &&
                    it.returnType == String::class.java
            }?.apply { isAccessible = true }
        }.getOrNull() ?: return null

        return MethodDescriptor.of(method)
    }

    // ── Structure resolution helpers ─────────────────────────────────────────────────────

    /**
     * `PreferenceGroup#addPreference(Preference)`.
     *
     * The type test assigns in both directions; a one-way `isAssignableFrom` also selects
     * `setDefaultValue(Object)` and `onSetInitialValue(Object)`.
     */
    private fun resolveGroupAdd(
        groupClass: Class<*>?,
        preferenceClass: Class<*>?
    ): java.lang.reflect.Method? {
        if (groupClass == null || preferenceClass == null) return null
        return groupClass.methods.filter {
            it.parameterCount == 1 &&
                it.returnType == Void.TYPE &&
                it.parameterTypes[0].isAssignableFrom(preferenceClass) &&
                preferenceClass.isAssignableFrom(it.parameterTypes[0])
        }.singleOrNull()?.apply { isAccessible = true }
    }

    /** `PreferenceGroup#findPreference(CharSequence) : Preference` */
    private fun resolveGroupFind(
        groupClass: Class<*>?,
        preferenceClass: Class<*>?
    ): java.lang.reflect.Method? {
        if (groupClass == null || preferenceClass == null) return null
        return groupClass.methods.filter {
            it.parameterCount == 1 &&
                it.parameterTypes[0].isAssignableFrom(CharSequence::class.java) &&
                preferenceClass.isAssignableFrom(it.returnType)
        }.singleOrNull()?.apply { isAccessible = true }
    }

    private fun singleFieldOfType(clazz: Class<*>?, type: Class<*>): java.lang.reflect.Field? {
        if (clazz == null) return null
        return clazz.declaredFields.filter { it.type == type }
            .singleOrNull()?.apply { isAccessible = true }
    }

    private fun classOrNull(classLoader: ClassLoader, name: String): Class<*>? =
        runCatching { classLoader.loadClass(name) }.getOrNull()


    private fun Class<*>?.methodOrNull(name: String): java.lang.reflect.Method? {
        if (this == null) return null
        return runCatching { getDeclaredMethod(name).apply { isAccessible = true } }.getOrNull()
    }

    private fun Class<*>?.methodOrNull(name: String, vararg params: Class<*>): java.lang.reflect.Method? {
        if (this == null) return null
        return runCatching { getDeclaredMethod(name, *params).apply { isAccessible = true } }.getOrNull()
    }

    /** A non-null value means the hook point is resolved; applies to restored Class / Method / Field handles */
    private fun Any?.state(): HookPointState =
        if (this != null) HookPointState.FOUND else HookPointState.MISSING

    /** Host fingerprint: APK path + modification time + size, covering an in-place reinstall */
    private fun fingerprintOf(apkPath: String?): String {
        val file = apkPath?.let { runCatching { java.io.File(it) }.getOrNull() }
        return AntiTearingCache.fingerprintOf(
            apkPath = apkPath,
            lastModified = file?.lastModified() ?: 0L,
            length = file?.length() ?: 0L
        )
    }

    private fun logStates(module: XposedModule, states: Map<String, HookPointState>) {
        val summary = ALL_LAUNCHER_HOOK_KEYS.joinToString(", ") { key ->
            "$key=${states[key] ?: HookPointState.MISSING}"
        }
        module.log(Log.INFO, TAG, "Launcher symbol resolution finished (ruleVersion=$RULE_VERSION): $summary")
    }

    /** Diagnostic one-line descriptor summary */
    fun describe(symbols: LauncherSymbols): String = buildString {
        append("panel=")
        append(symbols.clearAllPanel?.className?.substringAfterLast('.') ?: "null")
        append(", refresh=")
        append(symbols.refreshMemory?.methodName ?: "null")
        append(", tv=")
        append(symbols.memoryTextView?.fieldName ?: "null")
        append(", gates=")
        append(symbols.memoryGates.size)
        append(", styleReader=")
        append(symbols.recentStyleReader?.methodName ?: "null")
        append(", states=")
        append(symbols.statusMap.count { it.value == HookPointState.FOUND })
        append("/")
        append(symbols.statusMap.size)
    }
}
