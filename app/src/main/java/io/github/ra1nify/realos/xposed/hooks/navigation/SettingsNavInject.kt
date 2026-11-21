package io.github.ra1nify.realos.xposed.hooks.navigation

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import java.lang.reflect.Proxy
import io.github.ra1nify.realos.navigation.SettingsConfig
import io.github.ra1nify.realos.navigation.SettingsNavContract

/**
 * Injects RealOS navigation custom preference entries into the official system navigation settings fragment.
 */
object SettingsNavInject {

    private const val TAG = "RealOS-SettingsNav"

    private const val KEY_CATEGORY = "realos_nav_category"

    private const val KEY_ITEM_VIBRATION = "realos_nav_item_vibration"
    private const val KEY_ITEM_APPEARANCE = "realos_nav_item_appearance"
    private const val KEY_ITEM_ADVANCED = "realos_nav_item_advanced"

    private const val KEY_ENTRY_BUTTON_ICON = "realos_nav_button_icon"
    private const val KEY_ENTRY_BUTTON_GLASS = "realos_nav_button_glass"

    private data class SubItem(
        val key: String,
        val title: String,
        val action: String,
        val pageType: String
    )

    private val CATEGORY_TEMPLATES = listOf(
        "gesture_side_component_pref_key",
        "gesture_side_predict_back_pref_key",
        "gesture_up_component_pref_key",
        "virtual_key_combination_pref_key",
        "virtual_key_operation_pref_key",
        "navigation_buttons_position_container"
    )

    private val JUMP_TEMPLATES = listOf(
        "gesture_side_guide",
        "navigation_buttons_position",
        "gesture_side_mistouch_prevention_key"
    )

    private val ANCHOR_GESTURE = listOf(
        "gesture_side_predict_back_tips_key",
        "gesture_side_predict_back_pref_key",
        "gesture_side_component_pref_key"
    )

    private const val NAV_TYPE_SWIPE_SIDE = 2
    private const val NAV_TYPE_VIRTUAL_KEY = 0

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var shellActivityName: String? = null

    /**
     * "Navigation mode + entry switches" signature from the last successful injection
     * of each Fragment. A matching signature short-circuits the rebuild that
     * `updatePreferenceView` performs on every host pass.
     */
    private val lastInjectedSignature = java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<Any, String>()
    )

    fun reset() {
        shellActivityName = null
        module = null
        // Injection signatures of old Fragments are invalid after hot reload.
        lastInjectedSignature.clear()
    }

    private fun log(level: Int, msg: String) {
        module?.log(level, TAG, msg)
    }

    private fun log(level: Int, msg: String, t: Throwable) {
        module?.log(level, TAG, msg, t)
    }

    fun install(module: XposedModule, classLoader: ClassLoader, handles: SettingsHandles) {
        this.module = module
        module.log(Log.INFO, TAG, "install() enter (Settings)")

        SettingsConfig.prime(module)
        if (SettingsConfig.allEntriesDisabled()) {
            module.log(
                Log.INFO, TAG,
                "Both entry switches are disabled -> skip hook installation (expected behavior)"
            )
            return
        }

        if (!handles.canInjectNav) {
            module.log(Log.WARN, TAG, "fail closed: canInjectNav is false -> skip hook installation")
            return
        }

        val updatePrefMethod = handles.updatePreferenceViewMethod ?: run {
            module.log(Log.WARN, TAG, "fail closed: updatePreferenceView method is null")
            return
        }
        shellActivityName = handles.shellActivityName

        runCatching {
            module.hook(updatePrefMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    val fragment = chain.thisObject
                    val navType = readNavType(contextOf(fragment))
                    logDiagnostics(fragment, navType)
                    injectEntry(fragment, navType)
                } catch (t: Throwable) {
                    module.log(Log.WARN, TAG, "Exception inside preference injector (isolated from host)", t)
                }
                result
            }
            module.log(
                Log.INFO, TAG,
                "Hook installed: ${handles.fragmentClass?.name}#updatePreferenceView"
            )
        }.onFailure {
            module.log(Log.ERROR, TAG, "fail closed: failed to hook updatePreferenceView", it)
        }

        SettingsNavHostHook.install(module, classLoader, handles)
    }

    private fun injectEntry(fragment: Any?, navType: Int) {
        val screen = screenOf(fragment) ?: run {
            log(Log.WARN, "  Injection skipped: unable to obtain PreferenceScreen")
            return
        }
        val ctx = contextOf(fragment) ?: run {
            log(Log.WARN, "  Injection skipped: unable to obtain Context")
            return
        }
        val prefCls = preferenceClass(fragment) ?: run {
            log(Log.WARN, "  Injection skipped: unable to obtain host Preference class")
            return
        }

        // Idempotent short-circuit: skipped only when the signature matches **and the
        // group is still present**.
        //
        // The check runs before removePref; injection deletes the old group first, and
        // a check after deletion would find the group already removed.
        val signature = buildString {
            append(navType).append('|')
            append(SettingsConfig.visualEntryEnabled()).append('|')
            append(SettingsConfig.hapticEntryEnabled()).append('|')
            append(SettingsConfig.logicEntryEnabled()).append('|')
            append(SettingsConfig.advancedEntryEnabled())
        }
        if (lastInjectedSignature[fragment] == signature && findPref(screen, KEY_CATEGORY) != null) {
            return
        }

        findPref(screen, KEY_CATEGORY)?.let {
            removePref(screen, prefCls, it)
            log(Log.INFO, "  Removed existing $KEY_CATEGORY (ready to re-insert for navType=$navType)")
        }

        val categoryTpl = CATEGORY_TEMPLATES.firstNotNullOfOrNull { findPref(screen, it) }
        val jumpTpl = JUMP_TEMPLATES.firstNotNullOfOrNull { findPref(screen, it) }
        log(
            Log.INFO,
            "  Templates: category=${categoryTpl?.let { keyOf(it) }} jump=${jumpTpl?.let { keyOf(it) }}"
        )
        if (categoryTpl == null || jumpTpl == null) {
            log(
                Log.WARN,
                "  Injection skipped: template missing (all candidates evaluated) " +
                    "category candidates=$CATEGORY_TEMPLATES jump candidates=$JUMP_TEMPLATES"
            )
            return
        }

        val shell = shellActivityName ?: run {
            log(Log.WARN, "  Injection skipped: shell host Activity not resolved")
            return
        }

        if (navType != NAV_TYPE_SWIPE_SIDE && navType != NAV_TYPE_VIRTUAL_KEY) {
            log(Log.INFO, "  Injection skipped: navType=$navType is not a target mode (existing group removed)")
            return
        }
        val isGesture = navType == NAV_TYPE_SWIPE_SIDE

        val items = buildList {
            // 1. Visual tuning
            if (SettingsConfig.visualEntryEnabled()) {
                if (isGesture) {
                    add(
                        SubItem(
                            KEY_ITEM_APPEARANCE,
                            OplusWorkshopUiText.getNavSectionVisualTuning(ctx),
                            SettingsNavContract.ACTION_APPEARANCE,
                            SettingsNavContract.PAGE_APPEARANCE
                        )
                    )
                } else {
                    add(
                        SubItem(
                            KEY_ENTRY_BUTTON_ICON,
                            OplusWorkshopUiText.getNavSectionVisualTuning(ctx),
                            SettingsNavContract.ACTION_BUTTON,
                            SettingsNavContract.PAGE_BUTTON
                        )
                    )
                }
            }

            // 2. Haptic tuning
            if (SettingsConfig.hapticEntryEnabled()) {
                if (isGesture) {
                    add(
                        SubItem(
                            KEY_ITEM_VIBRATION,
                            OplusWorkshopUiText.getNavSectionHapticTuning(ctx),
                            SettingsNavContract.ACTION_VIBRATION,
                            SettingsNavContract.PAGE_VIBRATION
                        )
                    )
                } else {
                    add(
                        SubItem(
                            KEY_ITEM_VIBRATION,
                            OplusWorkshopUiText.getNavSectionHapticTuning(ctx),
                            SettingsNavContract.ACTION_BUTTON_VIBRATION,
                            SettingsNavContract.PAGE_BUTTON_VIBRATION
                        )
                    )
                }
            }

            // 3. Logic tuning
            if (SettingsConfig.logicEntryEnabled()) {
                if (isGesture) {
                    add(
                        SubItem(
                            KEY_ITEM_ADVANCED,
                            OplusWorkshopUiText.getNavSectionLogicTuning(ctx),
                            SettingsNavContract.ACTION_ADVANCED,
                            SettingsNavContract.PAGE_ADVANCED
                        )
                    )
                } else {
                    add(
                        SubItem(
                            KEY_ENTRY_BUTTON_GLASS,
                            OplusWorkshopUiText.getNavSectionLogicTuning(ctx),
                            SettingsNavContract.ACTION_BUTTON_GLASS,
                            SettingsNavContract.PAGE_BUTTON_GLASS
                        )
                    )
                }
            }

            // 4. Advanced tuning
            if (SettingsConfig.advancedEntryEnabled()) {
                add(
                    SubItem(
                        "realos_nav_item_advanced_misc",
                        OplusWorkshopUiText.getNavSectionAdvancedTuning(ctx),
                        SettingsNavContract.ACTION_BUTTON_GLASS,
                        SettingsNavContract.PAGE_BUTTON_GLASS
                    )
                )
            }
        }

        if (items.isEmpty()) {
            log(Log.INFO, "  Injection skipped: all tuning entry switches are disabled")
            return
        }

        val category = newLike(categoryTpl, ctx) ?: return
        copyLayout(categoryTpl, category)
        invoke1(category, "setKey", String::class.java, KEY_CATEGORY)
        val groupTitle = OplusWorkshopUiText.getNavUiSectionTitle(ctx)
        invoke1(category, "setTitle", CharSequence::class.java, groupTitle)
        addPref(screen, prefCls, category, "screen.addPreference(group)")

        for (item in items) {
            val entry = newLike(jumpTpl, ctx) ?: continue
            copyLayout(jumpTpl, entry)
            setKeyTitle(entry, item.key, item.title, "sub_item")
            setSummary(entry, OplusWorkshopUiText.getNavPoweredBy(ctx))

            // The target Intent goes into both the Preference intent field and the
            // click listener.
            //
            // Some host JumpPreference subclasses override onClick() and call
            // startActivity(getIntent()) directly; both paths carry the same Intent.
            val targetIntent = buildSubPageIntent(ctx, shell, item.action, item.pageType)
            if (targetIntent != null) {
                invoke1(entry, "setIntent", Intent::class.java, targetIntent)
                val readBack = invoke0(entry, "getIntent")
                log(
                    Log.INFO,
                    "  Entry ${item.key}: intent=${if (readBack is Intent) "set" else "not-set"}"
                )
            } else {
                log(Log.WARN, "  Entry ${item.key}: failed to build sub-page intent")
            }

            makeClickListener(entry, fragment?.javaClass?.classLoader) {
                log(Log.INFO, "  Entry click fired: page=${item.pageType}")
                openPage(ctx, shell, item.action, item.pageType)
            }
            addPref(category, prefCls, entry, "group.addPreference(${item.key})")
        }

        val anchor = if (isGesture) {
            ANCHOR_GESTURE.firstOrNull { findPref(screen, it) != null }
        } else {
            null
        }
        log(Log.INFO, "  Placement: navType=$navType anchor candidates=$ANCHOR_GESTURE selected=$anchor")
        if (anchor != null) {
            placeAfter(screen, anchor, category, prefCls)
        } else {
            log(Log.INFO, "  Placement: no anchor found -> appending to bottom")
        }

        val g = findPref(screen, KEY_CATEGORY)
        log(
            Log.INFO,
            "  Injection ${if (g != null) "successful" else "verification failed"}: navType=$navType " +
                "title='${titleOf(g)}' order=${orderOf(g)} subItems=${listPrefs(g).size}"
        )
        for (item in items) {
            log(
                Log.INFO,
                "    SubItem ${item.key}: title='${titleOf(findPref(g, item.key))}' " +
                    "summary='${summaryOf(findPref(g, item.key))}'"
            )
        }
        logScreenOrder(screen)

        // Records the signature only after a successful injection, with the group present.
        if (findPref(screen, KEY_CATEGORY) != null) {
            lastInjectedSignature[fragment] = signature
        }
    }

    private fun placeAfter(screen: Any, anchorKey: String, group: Any, prefCls: Class<*>) {
        val anchor = findPref(screen, anchorKey) ?: run {
            log(Log.WARN, "  placeAfter: anchor not found -> appending to bottom")
            return
        }
        val anchorOrder = orderOf(anchor)
        log(Log.INFO, "  placeAfter: anchor=$anchorKey order=$anchorOrder")
        if (anchorOrder == Int.MAX_VALUE) {
            log(Log.WARN, "  placeAfter: anchor order is MAX_VALUE (unable to bump) -> appending to bottom")
            return
        }

        var bumped = 0
        for (p in listPrefs(screen)) {
            if (p === group) continue
            val o = orderOf(p)
            if (o > anchorOrder && o != Int.MAX_VALUE) {
                setOrder(p, o + 1)
                bumped++
            }
        }
        setOrder(group, anchorOrder + 1)
        log(Log.INFO, "  placeAfter: bumped $bumped items; group order=${orderOf(group)}")

        removePref(screen, prefCls, group)
        addPref(screen, prefCls, group, "re-insert after anchor (order=${anchorOrder + 1})")
    }

    private fun makeClickListener(entry: Any, classLoader: ClassLoader?, onClick: () -> Unit) {
        val iface = runCatching {
            entry.javaClass.methods
                .firstOrNull { it.name == "setOnPreferenceClickListener" && it.parameterTypes.size == 1 }
                ?.parameterTypes?.get(0)
        }.getOrNull() ?: return

        val proxy = runCatching {
            Proxy.newProxyInstance(iface.classLoader ?: classLoader, arrayOf(iface)) { p, m, args ->
                when (m.name) {
                    "equals" -> p === args?.getOrNull(0)
                    "hashCode" -> System.identityHashCode(p)
                    "toString" ->
                        "RealOSNavEntry@${Integer.toHexString(System.identityHashCode(p))}"
                    else -> {
                        runCatching { onClick() }
                            .onFailure { log(Log.WARN, "Preference entry click handling failed", it) }
                        true
                    }
                }
            }
        }.getOrNull() ?: return
        invoke1(entry, "setOnPreferenceClickListener", iface, proxy)
    }

    /**
     * Builds the subpage Intent targeting the host shell Activity.
     *
     * Shared with [openPage]; click listener and `Preference.intent` carry one target.
     */
    private fun buildSubPageIntent(
        ctx: Context,
        shellName: String,
        action: String,
        pageType: String
    ): Intent? = runCatching {
        val shellClass = ctx.classLoader.loadClass(shellName)
        Intent(ctx, shellClass).apply {
            this.action = action
            putExtra(SettingsNavContract.EXTRA_PAGE_TYPE, pageType)
            shellFragmentName(ctx, shellName)?.let {
                putExtra(SettingsNavContract.EXTRA_SHOW_FRAGMENT, it)
            }
            putExtra(SettingsNavContract.EXTRA_SHOW_FRAGMENT_AS_SUBSETTING, true)
            if (ctx !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }.getOrNull()

    private fun openPage(ctx: Context, shellName: String, action: String, pageType: String) {
        runCatching {
            val intent = buildSubPageIntent(ctx, shellName, action, pageType)
                ?: error("buildSubPageIntent returned null")
            ctx.startActivity(intent)
            log(Log.INFO, "  Sub-page launched: page=$pageType shell=$shellName")
        }.onFailure { log(Log.ERROR, "Failed to launch sub-page", it) }
    }

    private fun shellFragmentName(ctx: Context, shellName: String): String? = runCatching {
        val info = ctx.packageManager.getActivityInfo(
            ComponentName(ctx.packageName, shellName),
            PackageManager.GET_META_DATA
        )
        info.metaData?.getString("com.android.settings.FRAGMENT_CLASS")
    }.getOrNull()

    private fun logDiagnostics(fragment: Any?, navType: Int) {
        val screen = screenOf(fragment) ?: return
        val groups = SettingsSymbols.GROUP_KEYS.map { k ->
            "$k=${if (findPref(screen, k) != null) "present" else "absent"}"
        }
        log(Log.INFO, "  Diagnostics: navType=$navType groups=$groups")
    }

    private fun logScreenOrder(screen: Any?) {
        val items = listPrefs(screen)
        val desc = items.mapIndexed { i, p ->
            val k = keyOf(p) ?: "null"
            val t = titleOf(p)
            if (t.isNullOrEmpty()) "$i:$k" else "$i:$k[$t]"
        }
        log(Log.INFO, "  Final order (${items.size})=$desc")
    }

    private fun readNavType(ctx: Context?): Int = ctx?.let {
        runCatching { Settings.Secure.getInt(it.contentResolver, "navigation_mode", -1) }
            .getOrDefault(-1)
    } ?: -1

    private fun preferenceClass(anchor: Any?): Class<*>? =
        anchor?.javaClass?.classLoader?.let { cl ->
            runCatching { cl.loadClass("androidx.preference.Preference") }.getOrNull()
        }

    private fun screenOf(fragment: Any?): Any? = fragment?.let {
        runCatching { invoke0(it, "getPreferenceScreen") }.getOrNull()
    }

    private fun contextOf(fragment: Any?): Context? = fragment?.let {
        runCatching { invoke0(it, "getContext") as? Context }.getOrNull()
    }

    private fun listPrefs(screen: Any?): List<Any> = runCatching {
        if (screen == null) return emptyList()
        val cls = screen.javaClass
        val count = cls.getMethod("getPreferenceCount").invoke(screen) as? Int ?: 0
        val get = cls.getMethod("getPreference", Int::class.javaPrimitiveType)
        (0 until count).mapNotNull { i -> runCatching { get.invoke(screen, i) }.getOrNull() }
    }.getOrElse {
        log(Log.WARN, "listPrefs failed", it)
        emptyList()
    }

    private fun findPref(screen: Any?, key: String): Any? = screen?.let {
        runCatching {
            it.javaClass.methods
                .firstOrNull { m -> m.name == "findPreference" && m.parameterTypes.size == 1 }
                ?.invoke(it, key)
        }.getOrNull()
    }

    private fun keyOf(p: Any?): String? = p?.let {
        runCatching { invoke0(it, "getKey") as? String }.getOrNull()
    }

    private fun titleOf(p: Any?): String? = p?.let {
        runCatching { invoke0(it, "getTitle")?.toString() }.getOrNull()
    }

    private fun summaryOf(p: Any?): String? = p?.let {
        runCatching { invoke0(it, "getSummary")?.toString() }.getOrNull()
    }

    private fun orderOf(p: Any?): Int {
        if (p == null) return Int.MAX_VALUE
        return runCatching { invoke0(p, "getOrder") as? Int }.getOrNull() ?: Int.MAX_VALUE
    }

    private fun setOrder(p: Any, value: Int) {
        invoke1(p, "setOrder", Int::class.javaPrimitiveType, value)
    }

    private fun invoke0(target: Any, name: String): Any? = runCatching {
        target.javaClass.methods
            .firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
            ?.invoke(target)
    }.getOrElse {
        log(Log.WARN, "invoke0($name) failed", it)
        null
    }

    private fun invoke1(target: Any, name: String, paramType: Class<*>?, arg: Any?) {
        runCatching {
            val candidates = target.javaClass.methods
                .filter { it.name == name && it.parameterTypes.size == 1 }
            val m = paramType?.let { pt -> candidates.firstOrNull { it.parameterTypes[0] == pt } }
                ?: candidates.firstOrNull()
            if (m == null) {
                log(
                    Log.WARN,
                    "invoke1($name): method not found; candidates=${candidates.map { it.parameterTypes[0].name }}"
                )
                return
            }
            m.invoke(target, arg)
        }.onFailure { log(Log.WARN, "invoke1($name) failed", it) }
    }

    private fun addPref(target: Any, prefCls: Class<*>, pref: Any, what: String) {
        invoke1(target, "addPreference", prefCls, pref)
        log(Log.INFO, "  addPref[$what]: current total=${listPrefs(target).size}")
    }

    private fun removePref(target: Any, prefCls: Class<*>, pref: Any) {
        invoke1(target, "removePreference", prefCls, pref)
    }

    private fun newLike(template: Any, ctx: Context): Any? = runCatching {
        template.javaClass.getConstructor(Context::class.java).newInstance(ctx)
    }.getOrElse {
        log(Log.WARN, "newLike(${template.javaClass.simpleName}) failed", it)
        null
    }

    private fun copyLayout(template: Any, target: Any) {
        runCatching {
            val layoutRes = invoke0(template, "getLayoutResource") as? Int
            if (layoutRes != null && layoutRes != 0) {
                invoke1(target, "setLayoutResource", Int::class.javaPrimitiveType, layoutRes)
            }
            val widgetRes = invoke0(template, "getWidgetLayoutResource") as? Int
            if (widgetRes != null && widgetRes != 0) {
                invoke1(target, "setWidgetLayoutResource", Int::class.javaPrimitiveType, widgetRes)
            }
            log(
                Log.INFO,
                "  copyLayout(${template.javaClass.simpleName}): layout=$layoutRes widget=$widgetRes"
            )
        }.onFailure { log(Log.WARN, "copyLayout failed", it) }
    }

    private fun setKeyTitle(target: Any, key: String, title: String, what: String) {
        if (title.isEmpty()) {
            log(
                Log.WARN,
                "  Empty title for $what key=$key"
            )
        }
        invoke1(target, "setKey", String::class.java, key)
        invoke1(target, "setTitle", CharSequence::class.java, title)
        log(Log.INFO, "  setKeyTitle($what): key=$key title='$title' readBack='${titleOf(target)}'")
    }

    private fun setSummary(target: Any, summary: String) {
        invoke1(target, "setSummary", CharSequence::class.java, summary)
    }
}
