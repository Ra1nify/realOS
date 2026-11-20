package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserManager
import io.github.ra1nify.realos.util.ModuleResources
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.R
import java.util.concurrent.atomic.AtomicBoolean
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs

/**
 * Manages presentation and interaction of the native Autofill ToolItem in
 * Xiaobu Keyboard's top toolbar and expanded Toolbox (ToolBoxView / ToolCatalog).
 *
 * Implements Native Tool Registration:
 * - Registered into the full ToolCatalog and ToolStateManager transform method
 *   so it natively appears in the "Customize Toolbar" editor, supporting native
 *   drag-and-drop, sorting, and database persistence.
 * - Registered into ToolBoxView (ToolBoxView.getBoxData()) so users can find and tap it
 *   in the expanded Toolbox grid.
 * - Icon rendered via pure-code vector Outline SVG key drawable without external assets (Plan B).
 * - Smooth dismissal back to main keyboard on click so autofill popup bubble displays properly.
 * - Complete short-circuit when disabled via OplusInputPrefs.isAutofillButtonEnabled.
 */
@RequiresApi(Build.VERSION_CODES.R)
object OplusAutofillToolbarManager {

    private const val TAG = "OplusAutofillToolbar"
    const val AUTOFILL_TOOL_ID = 9999

    /**
     * Display title of the new tool item, resolved from the module resources.
     *
     * Falls back to the English label when the IME is not attached yet; the item is rebuilt
     * with the localized title once the IME is up.
     */
    private fun autofillToolTitle(): String {
        val context = OplusLiquidGlassHook.getActiveIms()?.applicationContext
        return if (context != null) {
            ModuleResources.string(context, R.string.oplus_input_autofill_tool_title)
        } else {
            "Autofill"
        }
    }

    /**
     * Titles matched for item identity. Both the Chinese and the English label stay in the
     * set because the host may still carry an item created before a language switch; these
     * are host-owned literals and intentionally not resource lookups.
     */
    private val AUTOFILL_TOOL_TITLES = setOf("自动填充", "Autofill")

    private fun isAutofillTitle(raw: Any?): Boolean =
        raw is String && raw in AUTOFILL_TOOL_TITLES

    private val installed = AtomicBoolean(false)

    /** Weak set of ToolBoxView instances that already carry the layout listener. */
    private val layoutListenerInstalled = java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<View, Boolean>()
    )

    /** Cache TTL for `key_display_default_tools`. */
    private const val DEFAULT_TOOLS_CACHE_TTL_MS = 2000L

    /** One-shot flag for the missing dismiss entry point log. */
    private val dismissFallbackLogged = AtomicBoolean(false)

    /**
     * Hot-reload reset.
     *
     * Clears the `installed` gate, the layout listener set and the default-tools cache.
     * Called by `OplusInputSpec.onHotReloadCleanup/onHotReload`.
     */
    fun reset() {
        installed.set(false)
        layoutListenerInstalled.clear()
        defaultToolsCacheAt = 0L
    }

    fun install(module: XposedModule, symbols: OplusInputSymbols, classLoader: ClassLoader) {
        if (!installed.compareAndSet(false, true)) {
            return
        }

        if (!symbols.isReadyForToolbarAutofill()) {
            module.log(Log.WARN, TAG, "Symbols not ready for Toolbar Autofill; failing closed.")
            return
        }

        val transformMethod = symbols.toolStateManagerTransformMethod?.restore(classLoader)
        val catalogMethod = symbols.toolCatalogCreateMethod?.restore(classLoader)
        val boxDataMethod = symbols.toolboxDataMethod?.restore(classLoader)
        val layoutMethod = symbols.toolbarLayoutMethod?.restore(classLoader)
        val iconMethod = symbols.toolbarItemIconMethod?.restore(classLoader)

        module.log(Log.INFO, TAG, "Installing native Toolbar Autofill hooks...")

        // 1. Hook ToolStateManager.j(List, List): Pair (Primary data transform hub)
        if (transformMethod != null) {
            try {
                module.hook(transformMethod).intercept { chain ->
                    val list1 = chain.getArg(0) as? List<*>
                    val list2 = chain.getArg(1) as? List<*>
                    val context = OplusLiquidGlassHook.getActiveIms()?.applicationContext
                    val isEnabled = context != null && OplusInputPrefs.isAutofillButtonEnabled(context)

                    if (list1 == null) {
                        return@intercept chain.proceed()
                    }

                    var effectiveList1 = list1
                    var effectiveList2 = list2

                    if (!isEnabled) {
                        // Switch OFF: clean out all autofill items completely
                        val filtered1 = list1.filterNot { isAutofillItem(it) }
                        if (filtered1.size != list1.size) effectiveList1 = filtered1
                        if (list2 != null) {
                            val filtered2 = list2.filterNot { isAutofillItem(it) }
                            if (filtered2.size != list2.size) effectiveList2 = filtered2
                        }
                    } else {
                        // Switch ON:
                        try {
                            val hasItemInList1 = list1.any { isAutofillItem(it) }
                            val eClassName = symbols.toolbarItemIconMethod?.className
                            val eClass = if (eClassName != null) classLoader.loadClass(eClassName) else null

                            if (eClass != null) {
                                val autofillItem = createAutofillToolItem(eClass, classLoader)
                                if (autofillItem != null) {
                                    // 1. Adds the item to the full ToolCatalog pool when missing
                                    if (!hasItemInList1) {
                                        val newList1 = ArrayList<Any?>(list1.size + 1)
                                        newList1.addAll(list1)
                                        newList1.add(autofillItem)
                                        effectiveList1 = newList1
                                    }

                                    // 2. Selected toolbar: injects only on the factory default state; customized lists stay untouched.
                                    if (list2 != null) {
                                        val hasItemInList2 = list2.any { isAutofillItem(it) }
                                        val isDefault = isDefaultToolsState(context)
                                        if (!hasItemInList2 && isDefault) {
                                            val newList2 = ArrayList<Any?>(list2.size + 1)
                                            newList2.addAll(list2)
                                            val insertIdx = if (newList2.isNotEmpty()) 1.coerceAtMost(newList2.size) else 0
                                            newList2.add(insertIdx, autofillItem)
                                            effectiveList2 = newList2
                                        }
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            module.log(Log.WARN, TAG, "Error injecting into ToolStateManager.j: ${t.message}")
                        }
                    }

                    if (effectiveList1 !== list1 || effectiveList2 !== list2) {
                        chain.proceed(arrayOf(effectiveList1, effectiveList2))
                    } else {
                        chain.proceed()
                    }
                }
                module.log(Log.INFO, TAG, "Hooked ToolStateManager.j (toolStateManagerTransformMethod) successfully")
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "Failed to hook toolStateManagerTransformMethod: ${t.message}")
            }
        }

        // 2. Hook ToolStateManager.c(Context) to register Autofill into initial ToolCatalog
        if (catalogMethod != null) {
            try {
                module.hook(catalogMethod).intercept { chain ->
                    val result = chain.proceed() ?: return@intercept null
                    val context = chain.getArg(0) as? Context
                    val isEnabled = context != null && OplusInputPrefs.isAutofillButtonEnabled(context)

                    try {
                        val toolsField = result.javaClass.declaredFields.firstOrNull {
                            List::class.java.isAssignableFrom(it.type)
                        }
                        toolsField?.isAccessible = true
                        val originalTools = toolsField?.get(result) as? List<*>

                        if (originalTools != null) {
                            var newTools: List<Any?>? = null
                            if (!isEnabled) {
                                val filtered = originalTools.filterNot { isAutofillItem(it) }
                                if (filtered.size != originalTools.size) {
                                    newTools = filtered
                                }
                            } else {
                                val hasItem = originalTools.any { isAutofillItem(it) }
                                if (!hasItem) {
                                    val eClassName = symbols.toolbarItemIconMethod?.className
                                    val eClass = if (eClassName != null) classLoader.loadClass(eClassName) else null
                                    if (eClass != null) {
                                        val autofillItem = createAutofillToolItem(eClass, classLoader)
                                        if (autofillItem != null) {
                                            val list = ArrayList<Any?>(originalTools.size + 1)
                                            list.addAll(originalTools)
                                            list.add(autofillItem)
                                            newTools = list
                                        }
                                    }
                                }
                            }

                            if (newTools != null) {
                                val ctor = result.javaClass.declaredConstructors.firstOrNull {
                                    it.parameterTypes.size == 4 && List::class.java.isAssignableFrom(it.parameterTypes[1])
                                }
                                if (ctor != null) {
                                    ctor.isAccessible = true
                                    val fields = result.javaClass.declaredFields
                                    fields.forEach { it.isAccessible = true }
                                    val f1 = fields[0].get(result)
                                    val f3 = fields[2].get(result)
                                    val f4 = fields[3].get(result)
                                    return@intercept ctor.newInstance(f1, newTools, f3, f4)
                                } else {
                                    toolsField.set(result, newTools)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        module.log(Log.WARN, TAG, "Error injecting into ToolCatalog: ${t.message}")
                    }

                    result
                }
                module.log(Log.INFO, TAG, "Hooked ToolStateManager toolCatalogCreateMethod successfully")
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "Failed to hook toolCatalogCreateMethod: ${t.message}")
            }
        }

        // 3. Hook ToolBoxView.getBoxData() to inject Autofill into the expanded Toolbox grid
        if (boxDataMethod != null) {
            try {
                module.hook(boxDataMethod).intercept { chain ->
                    // Reads the result before the type check.
                    val result = chain.proceed()
                    val resultList = result as? List<*> ?: return@intercept result
                    val toolBoxView = chain.thisObject as? ViewGroup
                    val context = toolBoxView?.context
                    val isEnabled = context != null && OplusInputPrefs.isAutofillButtonEnabled(context)

                    var effectiveList = resultList
                    if (!isEnabled) {
                        // Switch OFF: clean out autofill item from toolbox
                        val filtered = resultList.filterNot { item ->
                            if (item == null) false else runCatching {
                                val titleField = item.javaClass.declaredFields.firstOrNull { it.type == String::class.java }
                                titleField?.isAccessible = true
                                isAutofillTitle(titleField?.get(item))
                            }.getOrDefault(false)
                        }
                        if (filtered.size != resultList.size) {
                            effectiveList = filtered
                        }
                    } else {
                        // Switch ON: inject into toolbox grid if not present
                        try {
                            val hasItem = resultList.any { item ->
                                if (item == null) false else runCatching {
                                    val titleField = item.javaClass.declaredFields.firstOrNull { it.type == String::class.java }
                                    titleField?.isAccessible = true
                                    isAutofillTitle(titleField?.get(item))
                                }.getOrDefault(false)
                            }

                            if (!hasItem) {
                                val bClass = resultList.firstOrNull { it != null }?.javaClass
                                if (bClass != null) {
                                    val autofillBoxData = createAutofillBoxData(bClass, classLoader)
                                    if (autofillBoxData != null) {
                                        val newList = ArrayList<Any?>(resultList.size + 1)
                                        newList.addAll(resultList)
                                        newList.add(autofillBoxData)
                                        effectiveList = newList
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            module.log(Log.WARN, TAG, "Error injecting into ToolBoxView data: ${t.message}")
                        }

                        toolBoxView?.post {
                            bindToolboxAutofillItem(module, toolBoxView, classLoader, symbols)
                        }
                    }

                    effectiveList
                }
                module.log(Log.INFO, TAG, "Hooked ToolBoxView toolboxDataMethod successfully")
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "Failed to hook toolboxDataMethod: ${t.message}")
            }
        }

        // 4. Hook ToolBarView.j(List): injects the item and binds the Toolbar click listener
        if (layoutMethod != null) {
            try {
                module.hook(layoutMethod).intercept { chain ->
                    val originalList = chain.getArg(0) as? List<*>
                    val toolbarView = chain.thisObject as? View
                    val context = toolbarView?.context
                    val isEnabled = context != null && OplusInputPrefs.isAutofillButtonEnabled(context)

                    var effectiveList: List<*>? = null

                    if (originalList != null) {
                        if (!isEnabled) {
                            // Switch OFF: filters the autofill item out of the list
                            val filtered = originalList.filterNot { isAutofillItem(it) }
                            if (filtered.size != originalList.size) {
                                effectiveList = filtered
                            }
                        } else {
                            // Switch ON:
                            // Injects only on the factory default state; customized lists stay untouched.
                            val hasItem = originalList.any { isAutofillItem(it) }
                            val isDefault = isDefaultToolsState(context)
                            if (!hasItem && isDefault) {
                                try {
                                    val eClassName = symbols.toolbarItemIconMethod?.className
                                    val eClass = if (eClassName != null) classLoader.loadClass(eClassName) else null
                                    if (eClass != null) {
                                        val autofillItem = createAutofillToolItem(eClass, classLoader)
                                        if (autofillItem != null) {
                                            val newList = ArrayList<Any?>(originalList.size + 1)
                                            newList.addAll(originalList)
                                            val insertIdx = if (newList.isNotEmpty()) 1.coerceAtMost(newList.size) else 0
                                            newList.add(insertIdx, autofillItem)
                                            effectiveList = newList
                                        }
                                    }
                                } catch (t: Throwable) {
                                    module.log(Log.WARN, TAG, "Error injecting toolbar autofill item: ${t.message}")
                                }
                            }
                        }
                    }

                    val result = if (effectiveList != null) {
                        chain.proceed(arrayOf(effectiveList))
                    } else {
                        chain.proceed()
                    }

                    val parent = chain.thisObject as? ViewGroup
                    if (parent != null) {
                        parent.post {
                            bindAutofillClickListener(module, parent)
                        }
                    }

                    result
                }
                module.log(Log.INFO, TAG, "Hooked ToolBarView layout method successfully")
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "Failed to hook ToolBarView layout method: ${t.message}")
            }
        }

        // 5. Hook e.b() to return our custom adaptive vector drawable for AUTOFILL_TOOL_ID
        if (iconMethod != null) {
            try {
                module.hook(iconMethod).intercept { chain ->
                    val thisObj = chain.thisObject ?: return@intercept chain.proceed()
                    val id = runCatching {
                        val idField = thisObj.javaClass.declaredFields.firstOrNull { it.type == Int::class.javaPrimitiveType }
                        idField?.isAccessible = true
                        idField?.getInt(thisObj) ?: -1
                    }.getOrDefault(-1)

                    if (id == AUTOFILL_TOOL_ID) {
                        val context = OplusLiquidGlassHook.getActiveIms()?.applicationContext
                        return@intercept getAdaptiveKeyDrawable(context)
                    }

                    chain.proceed()
                }
                module.log(Log.INFO, TAG, "Hooked toolbar item icon method successfully")
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "Failed to hook toolbar item icon method: ${t.message}")
            }
        }

        // 6. Hook Theme icon resource loader c.J(String, ThemeCompatMode) for candidate pool & toolbox
        hookThemeIconMethod(module, classLoader, symbols)
    }

    /**
     * Short-TTL cache for `key_display_default_tools`.
     *
     * Read on every `ToolStateManager` and `ToolBarView` transform callback through
     * `getSharedPreferences` and `UserManager.isUserUnlocked`; cached for 2 seconds.
     */
    @Volatile
    private var defaultToolsCacheValue: Boolean = true

    @Volatile
    private var defaultToolsCacheAt: Long = 0L

    private fun isDefaultToolsState(context: Context?): Boolean {
        val ctx = context ?: OplusLiquidGlassHook.getActiveIms()?.applicationContext ?: return true
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - defaultToolsCacheAt < DEFAULT_TOOLS_CACHE_TTL_MS) return defaultToolsCacheValue
        val value = runCatching {
            val prefsName = "${ctx.packageName}_preferences"
            val prefs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !ctx.getSystemService(UserManager::class.java).isUserUnlocked
            ) {
                ctx.createDeviceProtectedStorageContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            } else {
                ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            }
            prefs.getBoolean("key_display_default_tools", true)
        }.getOrDefault(true)
        defaultToolsCacheValue = value
        defaultToolsCacheAt = now
        return value
    }

    private fun hookThemeIconMethod(
        module: XposedModule,
        classLoader: ClassLoader,
        symbols: OplusInputSymbols
    ) {
        val themeMethod = symbols.themeIconMethod?.restore(classLoader)
        if (themeMethod == null) {
            module.log(Log.WARN, TAG, "themeIconMethod not resolved; candidate/toolbox icons will rely on direct binding fallback.")
            return
        }
        runCatching {
            module.hook(themeMethod).intercept { chain ->
                val iconName = chain.getArg(0) as? String
                if (iconName == "iconToolboxAutofill" || iconName == "iconHeaderAutofill") {
                    val context = OplusLiquidGlassHook.getActiveIms()?.applicationContext
                    if (context != null && !OplusInputPrefs.isAutofillButtonEnabled(context)) {
                        return@intercept null
                    }
                    return@intercept getAdaptiveKeyDrawable(context)
                }
                chain.proceed()
            }
            module.log(Log.INFO, TAG, "Hooked theme icon method successfully")
        }.onFailure {
            module.log(Log.WARN, TAG, "Failed to hook theme icon method: ${it.message}")
        }
    }

    private fun isNightMode(context: Context?): Boolean {
        val ctx = context ?: OplusLiquidGlassHook.getActiveIms()?.applicationContext
        if (ctx != null) {
            val modeStr = runCatching {
                val sp = ctx.getSharedPreferences("${ctx.packageName}_preferences", Context.MODE_PRIVATE)
                sp.getString("key_day_night_mode", "MODE_FOLLOW_SYS")
            }.getOrNull()

            when (modeStr) {
                "MODE_NIGHT_YES" -> return true
                "MODE_NIGHT_NO" -> return false
            }

            val uiMode = ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) return true
            if (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_NO) return false
        }

        val systemUiMode = android.content.res.Resources.getSystem().configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return systemUiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun getAdaptiveKeyDrawable(context: Context?): Drawable {
        val isNight = isNightMode(context)
        // Dark mode draws a pure white icon; light mode draws 0xDE000000.
        val baseColor = if (isNight) Color.WHITE else Color.argb(222, 0, 0, 0)
        return AutofillKeySvgDrawable(baseColor)
    }

    private fun isAutofillItem(item: Any?): Boolean {
        if (item == null) return false
        return runCatching {
            val idField = item.javaClass.declaredFields.firstOrNull { it.type == Int::class.javaPrimitiveType }
            idField?.isAccessible = true
            idField?.getInt(item) == AUTOFILL_TOOL_ID
        }.getOrDefault(false)
    }

    private fun createAutofillToolItem(eClass: Class<*>, classLoader: ClassLoader): Any? {
        return runCatching {
            val boxEnumsClass = runCatching { classLoader.loadClass("com.oplus.keyboard.base.enums.BoxEnums") }.getOrNull()
            @Suppress("UNCHECKED_CAST")
            val boxSettingEnum = if (boxEnumsClass != null) {
                runCatching {
                    java.lang.Enum.valueOf(boxEnumsClass as Class<out Enum<*>>, "BOX_SETTING")
                }.getOrNull() ?: boxEnumsClass.enumConstants?.firstOrNull()
            } else null

            val dClass = eClass.declaredConstructors
                .firstOrNull { it.parameterTypes.size in 7..8 }
                ?.parameterTypes?.getOrNull(2)
            val dCtor = dClass?.declaredConstructors?.firstOrNull { it.parameterTypes.size == 2 }
            dCtor?.isAccessible = true
            val dInstance = dCtor?.newInstance("iconHeaderAutofill", null)

            val ctors = eClass.declaredConstructors
            for (ctor in ctors) {
                ctor.isAccessible = true
                val pTypes = ctor.parameterTypes
                if (pTypes.size == 8 && pTypes[0] == Int::class.javaPrimitiveType && pTypes[1] == String::class.java) {
                    return ctor.newInstance(
                        AUTOFILL_TOOL_ID,
                        autofillToolTitle(),
                        dInstance,
                        false,
                        "iconToolboxAutofill",
                        true,
                        boxSettingEnum,
                        true
                    )
                }
                if (pTypes.size == 7 && pTypes[0] == Int::class.javaPrimitiveType && pTypes[1] == String::class.java && pTypes[6] == Int::class.javaPrimitiveType) {
                    return ctor.newInstance(
                        AUTOFILL_TOOL_ID,
                        autofillToolTitle(),
                        dInstance,
                        "iconToolboxAutofill",
                        true,
                        boxSettingEnum,
                        0
                    )
                }
            }
            null
        }.getOrNull()
    }

    private fun createAutofillBoxData(bClass: Class<*>, classLoader: ClassLoader): Any? {
        return runCatching {
            val boxEnumsClass = runCatching { classLoader.loadClass("com.oplus.keyboard.base.enums.BoxEnums") }.getOrNull()
            @Suppress("UNCHECKED_CAST")
            val boxSettingEnum = if (boxEnumsClass != null) {
                runCatching {
                    java.lang.Enum.valueOf(boxEnumsClass as Class<out Enum<*>>, "BOX_SETTING")
                }.getOrNull() ?: boxEnumsClass.enumConstants?.firstOrNull()
            } else null

            val ctors = bClass.declaredConstructors
            for (ctor in ctors) {
                ctor.isAccessible = true
                val pTypes = ctor.parameterTypes
                if (pTypes.size == 7 && pTypes[0] == String::class.java) {
                    return ctor.newInstance(
                        autofillToolTitle(),
                        false,
                        boxSettingEnum,
                        null,
                        null,
                        null,
                        214
                    )
                }
                if (pTypes.size == 8 && pTypes[0] == String::class.java) {
                    return ctor.newInstance(
                        autofillToolTitle(),
                        null,
                        false,
                        boxSettingEnum,
                        null,
                        null,
                        null,
                        true
                    )
                }
            }
            null
        }.getOrNull()
    }

    private fun bindToolboxAutofillItem(
        module: XposedModule?,
        toolBoxView: ViewGroup,
        classLoader: ClassLoader,
        symbols: OplusInputSymbols
    ) {
        scanAndBindToolboxView(module, toolBoxView, classLoader, symbols)

        // Called repeatedly from the getBoxData() post{} callback; the weak set keeps
        // the layout listener attached once per ToolBoxView.
        if (layoutListenerInstalled.put(toolBoxView, true) == null) {
            toolBoxView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View?, left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                ) {
                    scanAndBindToolboxView(module, toolBoxView, classLoader, symbols)
                }
            })
        }
    }

    private fun scanAndBindToolboxView(
        module: XposedModule?,
        view: View,
        classLoader: ClassLoader,
        symbols: OplusInputSymbols
    ) {
        if (view is ViewGroup) {
            val desc = view.contentDescription?.toString()
            if (isAutofillTitle(desc)) {
                setupToolboxItemView(module, view, classLoader, symbols)
                return
            }

            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is TextView && isAutofillTitle(child.text?.toString())) {
                    setupToolboxItemView(module, view, classLoader, symbols)
                    return
                }
                if (child is ViewGroup) {
                    scanAndBindToolboxView(module, child, classLoader, symbols)
                }
            }
        }
    }

    private fun setupToolboxItemView(
        module: XposedModule?,
        container: ViewGroup,
        classLoader: ClassLoader,
        symbols: OplusInputSymbols
    ) {
        findImageView(container)?.let { iv ->
            iv.setImageDrawable(getAdaptiveKeyDrawable(container.context))
        }

        container.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val ims = OplusLiquidGlassHook.getActiveIms()
            val ic = ims?.currentInputConnection
            if (ic != null) {
                val dispatched = ic.performContextMenuAction(android.R.id.autofill)
                module?.log(Log.INFO, TAG, "Toolbox autofill clicked, dispatched result=$dispatched")
            }
            dismissToolboxPanel(v, classLoader)
        }
    }

    private fun findImageView(root: ViewGroup): ImageView? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is ImageView) return child
            if (child is ViewGroup) {
                val found = findImageView(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun dismissToolboxPanel(view: View?, classLoader: ClassLoader) {
        // 1. Primary: Search up the View tree for ToolBoxView's back button and performClick()
        var foundBack = false
        var cur: ViewParent? = view?.parent
        while (cur is ViewGroup) {
            val group = cur
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                val desc = child.contentDescription?.toString() ?: ""
                if (desc.contains("返回") || desc.contains("Back") || desc.contains("back")) {
                    child.performClick()
                    foundBack = true
                    break
                }
            }
            if (foundBack) break
            cur = group.parent
        }

        if (foundBack) return

        // 2. No secondary path: no fallback invocation over host ViewGroup methods.
        //    A missing back/close entry logs once and skips panel dismissal.
        if (dismissFallbackLogged.compareAndSet(false, true)) {
            Log.w(
                TAG,
                "fail closed: no explicit dismiss/back entry found; skipping the toolbox panel close (avoids blind calls into host methods)"
            )
        }
    }

    private fun bindAutofillClickListener(module: XposedModule?, toolbarView: ViewGroup) {
        val containerId = CouiComponentFactory.resolveHostResId(toolbarView.context, "container_toolbar", "id")
        val container = (if (containerId != 0) toolbarView.findViewById(containerId) else null)
            ?: findToolbarContainer(toolbarView) ?: return

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val desc = child.contentDescription?.toString() ?: ""
            val isTarget = isAutofillTitle(desc) || child.tag?.toString() == "BOX_SETTING" || child.tag?.toString() == "BOX_TOOLBAR"

            if (isTarget) {
                // Synchronize toolbar button icon with current day/night mode
                if (child is ViewGroup) {
                    findImageView(child)?.let { iv ->
                        iv.setImageDrawable(getAdaptiveKeyDrawable(toolbarView.context))
                    }
                }

                child.setOnClickListener { v ->
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    // Clears the suppressed-show state on user request.
                    OplusAutofillStripManager.onUserRequestedShow(module)
                    val ims = OplusLiquidGlassHook.getActiveIms()
                    val ic = ims?.currentInputConnection
                    if (ic != null) {
                        val dispatched = ic.performContextMenuAction(android.R.id.autofill)
                        module?.log(Log.INFO, TAG, "Dispatched performContextMenuAction(R.id.autofill), result=$dispatched")
                    } else {
                        module?.log(Log.WARN, TAG, "currentInputConnection is null; cannot dispatch autofill")
                    }
                }
                // Leaves setOnLongClickListener alone; native long-press opens the "Customize Toolbar" editor.
                break
            }
        }
    }

    private fun findToolbarContainer(root: ViewGroup): ViewGroup? {
        val count = root.childCount
        for (i in 0 until count) {
            val child = root.getChildAt(i)
            val idName = runCatching { root.resources.getResourceEntryName(child.id) }.getOrNull()
            if (idName == "container_toolbar") {
                return child as? ViewGroup
            }
            if (child is ViewGroup) {
                val found = findToolbarContainer(child)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Precision pure-code vector Outline SVG key drawable (Plan B).
     *
     * Conforms to ColorOS Header Tool Icon Specification:
     * - Viewport: 22 x 22 dp
     * - Outline style with 1.4dp stroke width
     * - Round cap and round joins
     * - Hollow key ring at left, horizontal shaft, dual teeth downwards
     */
    class AutofillKeySvgDrawable(private var tintColor: Int = Color.WHITE) : Drawable() {

        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = tintColor
        }

        private var colorFilterOverride: ColorFilter? = null

        init {
            buildKeyPath()
        }

        private fun buildKeyPath() {
            path.reset()

            // 1. Left circular key ring outline (Center: 7.2f, 11.0f, Radius: 4.2f)
            path.addCircle(7.2f, 11.0f, 4.2f, Path.Direction.CW)

            // 2. Hollow inner pinhole (Radius: 1.8f)
            path.addCircle(7.2f, 11.0f, 1.8f, Path.Direction.CW)

            // 3. Horizontal key shaft line extending to right
            path.moveTo(11.4f, 11.0f)
            path.lineTo(19.2f, 11.0f)

            // 4. First key tooth downwards
            path.moveTo(15.8f, 11.0f)
            path.lineTo(15.8f, 14.8f)

            // 5. Second key tooth downwards
            path.moveTo(18.8f, 11.0f)
            path.lineTo(18.8f, 13.8f)
        }

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (w <= 0f || h <= 0f) return

            val saveCount = canvas.save()

            // Scale 22x22 path to fit drawable bounds
            val scale = Math.min(w / 22.0f, h / 22.0f)
            val dx = bounds.left + (w - 22.0f * scale) / 2.0f
            val dy = bounds.top + (h - 22.0f * scale) / 2.0f

            canvas.translate(dx, dy)
            canvas.scale(scale, scale)

            paint.color = tintColor
            paint.colorFilter = colorFilterOverride
            canvas.drawPath(path, paint)
            canvas.restoreToCount(saveCount)
        }

        override fun getIntrinsicWidth(): Int = 22
        override fun getIntrinsicHeight(): Int = 22

        override fun mutate(): Drawable = this

        override fun setTint(tintColor: Int) {
            this.tintColor = tintColor
            paint.color = tintColor
            invalidateSelf()
        }

        override fun setTintList(tint: ColorStateList?) {
            if (tint != null) {
                this.tintColor = tint.defaultColor
                paint.color = this.tintColor
            }
            invalidateSelf()
        }

        override fun setTintMode(tintMode: PorterDuff.Mode?) {
            if (tintMode != null) {
                colorFilterOverride = PorterDuffColorFilter(paint.color, tintMode)
            }
            invalidateSelf()
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            colorFilterOverride = colorFilter
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
