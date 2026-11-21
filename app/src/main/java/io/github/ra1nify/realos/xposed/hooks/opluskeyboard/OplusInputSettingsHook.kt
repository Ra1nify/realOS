package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.AttributeSet
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui.OplusComposeHost
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui.OplusEnhancementsCompose
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui.OplusKeyboardEffectsCompose
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui.OplusKeyboardSoundCompose
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui.OplusLiquidGlassCompose
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import io.github.ra1nify.realos.opluskeyboard.KeyboardPagesContract
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.opluskeyboard.OplusInputConfig

/**
 * Injects realOS settings category into Xiaobu Input Method's SettingsHomeFragment
 * and hosts custom views inside DocumentEmptyActivity.
 *
 * Requirements:
 * - Cancel redirect of official SkinActivity.
 * - In SettingsHomeFragment, add a new COUI category between "Help & Feedback" and "Restore Defaults".
 * - Category contains:
 *     1. "Skin Workshop" (summary: "Powered By realOS") -> opens OplusWorkshopView
 *     2. "Feature Enhancements" (summary: "Powered By realOS") -> opens OplusEnhancementsView
 * - Presence depends on realOS app switch.
 * - Resolves obfuscated Preference.OnPreferenceClickListener (e.g. androidx.preference.r)
 *   at runtime through reflection and Proxy, covering multiple host versions.
 */
object OplusInputSettingsHook {

    private const val TAG = "OplusSettingsHook"
    private const val SETTINGS_FRAGMENT_CLASS = "com.oplus.keyboard.settings.SettingsHomeFragment"
    private const val DOCUMENT_ACTIVITY_CLASS = "com.oplus.keyboard.ai.activity.DocumentEmptyActivity"

    // Page protocol constants live in realos.opluskeyboard.KeyboardPagesContract, shared by app and hook sides.
    private const val ACTION_LIQUID_GLASS = KeyboardPagesContract.ACTION_LIQUID_GLASS
    private const val ACTION_WORKSHOP = KeyboardPagesContract.ACTION_WORKSHOP
    private const val ACTION_KEYBOARD_EFFECTS = KeyboardPagesContract.ACTION_KEYBOARD_EFFECTS
    private const val ACTION_KEYBOARD_SOUNDS = KeyboardPagesContract.ACTION_KEYBOARD_SOUNDS
    private const val ACTION_ENHANCEMENTS = KeyboardPagesContract.ACTION_ENHANCEMENTS
    private const val EXTRA_PAGE_TYPE = KeyboardPagesContract.EXTRA_PAGE_TYPE
    private const val PAGE_LIQUID_GLASS = KeyboardPagesContract.PAGE_LIQUID_GLASS
    private const val PAGE_WORKSHOP = KeyboardPagesContract.PAGE_WORKSHOP
    private const val PAGE_KEYBOARD_EFFECTS = KeyboardPagesContract.PAGE_KEYBOARD_EFFECTS
    private const val PAGE_KEYBOARD_SOUNDS = KeyboardPagesContract.PAGE_KEYBOARD_SOUNDS
    private const val PAGE_ENHANCEMENTS = KeyboardPagesContract.PAGE_ENHANCEMENTS

    private const val KEY_REALOS_CATEGORY = "key_realos_category"
    private const val KEY_LIQUID_GLASS_PREF = "key_realos_liquid_glass"
    private const val KEY_KEYBOARD_EFFECTS_PREF = "key_realos_keyboard_effects"
    private const val KEY_KEYBOARD_SOUNDS_PREF = "key_realos_keyboard_sounds"
    private const val KEY_ENHANCEMENTS_PREF = "key_realos_enhancements"

    private val settingsHookInstalled = AtomicBoolean(false)
    private val documentHookInstalled = AtomicBoolean(false)

    fun reset() {
        settingsHookInstalled.set(false)
        documentHookInstalled.set(false)
    }

    fun install(module: XposedModule, classLoader: ClassLoader) {
        // ── 1. Hook SettingsHomeFragment to inject category ──
        if (!settingsHookInstalled.get()) {
            try {
                val fragmentClass = classLoader.loadClass(SETTINGS_FRAGMENT_CLASS)

                // Hook onCreatePreferences
                var hookCount = 0
                runCatching {
                    val onCreatePrefsMethod = fragmentClass.getDeclaredMethod(
                        "onCreatePreferences",
                        Bundle::class.java,
                        String::class.java
                    )
                    module.hook(onCreatePrefsMethod).intercept { chain ->
                        val result = chain.proceed()
                        val fragment = chain.thisObject
                        injectSettingsCategory(module, classLoader, fragment)
                        result
                    }
                    hookCount++
                    Log.i(TAG, "Hooked SettingsHomeFragment.onCreatePreferences")
                    module.log(Log.INFO, TAG, "Hooked SettingsHomeFragment.onCreatePreferences")
                }.onFailure {
                    Log.w(TAG, "Failed to hook onCreatePreferences", it)
                    module.log(Log.WARN, TAG, "Failed to hook onCreatePreferences: $it")
                }

                // Second hook: onViewCreated injects the category as well.
                runCatching {
                    val onViewCreatedMethod = fragmentClass.getDeclaredMethod(
                        "onViewCreated",
                        View::class.java,
                        Bundle::class.java
                    )
                    module.hook(onViewCreatedMethod).intercept { chain ->
                        val result = chain.proceed()
                        val fragment = chain.thisObject
                        injectSettingsCategory(module, classLoader, fragment)
                        result
                    }
                    hookCount++
                    Log.i(TAG, "Hooked SettingsHomeFragment.onViewCreated")
                    module.log(Log.INFO, TAG, "Hooked SettingsHomeFragment.onViewCreated")
                }.onFailure {
                    Log.w(TAG, "Failed to hook onViewCreated", it)
                    module.log(Log.WARN, TAG, "Failed to hook onViewCreated: $it")
                }

                if (hookCount > 0) {
                    settingsHookInstalled.set(true)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook SettingsHomeFragment; will retry on next entry point", t)
                module.log(Log.WARN, TAG, "Failed to hook SettingsHomeFragment: $t")
            }
        }

        // ── 2. Hook DocumentEmptyActivity to host Liquid Glass, Keyboard Effects & Enhancements views ──
        if (!documentHookInstalled.get()) {
            try {
                val activityClass = classLoader.loadClass(DOCUMENT_ACTIVITY_CLASS)
                val onCreateMethod = activityClass.getDeclaredMethod("onCreate", Bundle::class.java)

                module.hook(onCreateMethod).intercept { chain ->
                    val activity = chain.thisObject as? Activity ?: return@intercept chain.proceed()
                    val intent = activity.intent
                    val action = intent?.action
                    val pageType = intent?.getStringExtra(EXTRA_PAGE_TYPE)

                    val isLiquidGlass = action == ACTION_LIQUID_GLASS || action == ACTION_WORKSHOP ||
                            action == "com.luckyfuse.oplus.action.LIQUID_GLASS" || action == "com.luckyfuse.oplus.action.WORKSHOP" ||
                            pageType == PAGE_LIQUID_GLASS || pageType == PAGE_WORKSHOP
                    val isKeyboardEffects = action == ACTION_KEYBOARD_EFFECTS || action == "com.luckyfuse.oplus.action.KEYBOARD_EFFECTS" ||
                            pageType == PAGE_KEYBOARD_EFFECTS
                    val isKeyboardSounds = action == ACTION_KEYBOARD_SOUNDS || action == "com.luckyfuse.oplus.action.KEYBOARD_SOUNDS" ||
                            pageType == PAGE_KEYBOARD_SOUNDS
                    val isEnhancements = action == ACTION_ENHANCEMENTS || action == "com.luckyfuse.oplus.action.ENHANCEMENTS" ||
                            pageType == PAGE_ENHANCEMENTS

                    if (!isLiquidGlass && !isKeyboardEffects && !isKeyboardSounds && !isEnhancements) {
                        return@intercept chain.proceed()
                    }

                    // 1. Sets the Settings theme before super.onCreate, ahead of AppCompat theme initialization.
                    val settingsThemeId = CouiComponentFactory.resolveHostResId(activity, "Theme.Settings", "style")
                        .takeIf { it != 0 }
                        ?: CouiComponentFactory.resolveHostResId(activity, "Theme.COUI.Blue", "style")
                        .takeIf { it != 0 }
                        ?: CouiComponentFactory.resolveHostResId(activity, "Theme.COUI", "style")
                    if (settingsThemeId != 0) {
                        activity.setTheme(settingsThemeId)
                    }

                    // Requests FEATURE_NO_TITLE before the decor view exists.
                    runCatching {
                        activity.window.requestFeature(android.view.Window.FEATURE_NO_TITLE)
                    }

                    // Proceed base AppCompatActivity onCreate lifecycle
                    val result = chain.proceed()

                    try {
                        // 2. Apply COUI Theme Overlay (identical to EmptyActivity / SettingsActivity)
                        runCatching {
                            val overlayClass = classLoader.loadClass("com.coui.appcompat.theme.COUIThemeOverlay")
                            val getInstanceMethod = overlayClass.getMethod("getInstance")
                            val overlayInstance = getInstanceMethod.invoke(null)
                            val applyMethod = overlayClass.getMethod("applyThemeOverlays", Context::class.java)
                            applyMethod.invoke(overlayInstance, activity)
                        }

                        // 3. Apply SystemBarUtil (transparent status bar & stock contrast icon tinting)
                        runCatching {
                            val sysBarUtilClass = classLoader.loadClass("com.coui.appcompat.baseview.util.SystemBarUtil")
                            val setMethod = sysBarUtilClass.getMethod("setStatusBarTransparentAndBlackFont", Activity::class.java)
                            setMethod.invoke(null, activity)
                        }

                        // 4. Configure window flags & layout for full-screen display
                        val window = activity.window
                        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                        window.addFlags(
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                        )

                        val bgColor = CouiComponentFactory.getBackgroundColor(activity)
                        window.statusBarColor = Color.TRANSPARENT
                        window.navigationBarColor = bgColor
                        window.setBackgroundDrawable(ColorDrawable(bgColor))
                        window.decorView.setBackgroundColor(bgColor)
                        window.setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // 5. Hide default ActionBar if any
                        activity.title = ""
                        runCatching {
                            val supportActionBarMethod = activity.javaClass.getMethod("getSupportActionBar")
                            val actionBar = supportActionBarMethod.invoke(activity)
                            actionBar?.javaClass?.getMethod("hide")?.invoke(actionBar)
                        }
                        runCatching {
                            activity.actionBar?.hide()
                        }

                        // 6. Sets softInputMode to ADJUST_NOTHING; the window layout keeps its size.
                        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

                        // 7. Inject content view with MATCH_PARENT (prioritize roui Compose UI, fallback to legacy views)
                        val contentView: View = runCatching {
                            OplusComposeHost.create(activity, module) {
                                when {
                                    isLiquidGlass -> OplusLiquidGlassCompose(activity)
                                    isKeyboardEffects -> OplusKeyboardEffectsCompose(activity)
                                    isKeyboardSounds -> OplusKeyboardSoundCompose(activity)
                                    isEnhancements -> OplusEnhancementsCompose(activity)
                                }
                            }.also {
                                Log.i(TAG, "Loaded roui Compose UI into DocumentEmptyActivity")
                                module.log(Log.INFO, TAG, "Loaded roui Compose UI into DocumentEmptyActivity")
                            }
                        }.getOrElse { t ->
                            Log.w(TAG, "Compose host failed; falling back to legacy views", t)
                            when {
                                isLiquidGlass -> OplusWorkshopView(activity)
                                isKeyboardEffects -> OplusKeyboardEffectsView(activity)
                                isEnhancements -> OplusEnhancementsView(activity)
                                else -> return@intercept result
                            }
                        }

                        activity.setContentView(
                            contentView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )

                        // 8. Sets android.R.id.content to a solid background with zero padding and margins.
                        val content = activity.findViewById<ViewGroup>(android.R.id.content)
                        content?.setBackgroundColor(bgColor)
                        content?.setPadding(0, 0, 0, 0)
                        (content?.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(0, 0, 0, 0)

                        // 9. Hide only the sibling ActionBarContainer of android.R.id.content
                        (content?.parent as? ViewGroup)?.let { directParent ->
                            for (i in 0 until directParent.childCount) {
                                val child = directParent.getChildAt(i)
                                if (child != content) {
                                    val childName = child.javaClass.name
                                    if (childName.contains("ActionBarContainer") ||
                                        childName.contains("Toolbar") ||
                                        childName.contains("AppBarLayout")
                                    ) {
                                        child.visibility = View.GONE
                                    }
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        // Full stack trace goes to logcat; android.util.Log accepts multiple lines.
                        Log.e(TAG, "Failed to configure full page for DocumentEmptyActivity", t)
                        // LSPosed logs line by line and keeps only the first line of a multi-line string, so the trace is flattened to one line.
                        module.log(
                            Log.ERROR, TAG,
                            "Failed to configure full page: ${t.stackTraceToString().replace('\n', ' ')}"
                        )
                        // Fallback view: an init exception leaves setContentView unexecuted,
                        // showing only the window background and an empty status bar.
                        runCatching {
                            activity.setContentView(
                                CouiComponentFactory.createPageLoadFailedView(activity),
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    }

                    result
                }
                documentHookInstalled.set(true)
                Log.i(TAG, "Hooked DocumentEmptyActivity.onCreate")
                module.log(Log.INFO, TAG, "Hooked DocumentEmptyActivity.onCreate")

                // Hooks DocumentEmptyActivity.onActivityResult to intercept the SAF result ahead of the hardcoded finish()
                runCatching {
                    val docEmptyClass = classLoader.loadClass(DOCUMENT_ACTIVITY_CLASS)
                    val onActivityResultMethod = docEmptyClass.getDeclaredMethod(
                        "onActivityResult",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Intent::class.java
                    )
                    module.hook(onActivityResultMethod).intercept { chain ->
                        val activity = chain.thisObject as? Activity
                        val requestCode = chain.getArg(0) as? Int ?: 0
                        val resultCode = chain.getArg(1) as? Int ?: 0
                        val data = chain.getArg(2) as? Intent

                        if (requestCode == OplusWorkshopView.REQUEST_CODE_PICK_BG_LIGHT ||
                            requestCode == OplusWorkshopView.REQUEST_CODE_PICK_BG_DARK
                        ) {
                            if (resultCode == Activity.RESULT_OK && activity != null) {
                                val isDark = (requestCode == OplusWorkshopView.REQUEST_CODE_PICK_BG_DARK)
                                val uri = data?.data
                                if (uri != null) {
                                    try {
                                        activity.contentResolver.openInputStream(uri)?.use { input ->
                                            val bitmap = BitmapFactory.decodeStream(input)
                                            if (bitmap != null) {
                                                val outFile = OplusInputPrefs.getBgImageFile(activity, isDark)
                                                java.io.FileOutputStream(outFile).use { fos ->
                                                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
                                                }
                                                bitmap.recycle()
                                            }
                                        }
                                        if (isDark) {
                                            OplusInputPrefs.setDarkBgOverlayEnabled(activity, true)
                                        } else {
                                            OplusInputPrefs.setLightBgOverlayEnabled(activity, true)
                                        }
                                        // Invalidates the hasBgImage TTL cache after the new file is written.
                                        OplusInputPrefs.invalidateBgImageCache()
                                        OplusWorkshopView.onBgImageSaved(isDark)
                                        OplusLiquidGlassHook.triggerLiveRefresh(activity)
                                        module.log(Log.INFO, TAG, "Successfully saved SAF background image for isDark=$isDark")
                                    } catch (t: Throwable) {
                                        Log.e(TAG, "Failed to copy SAF image: ${t.message}", t)
                                        module.log(Log.ERROR, TAG, "Failed to copy SAF image: ${t.message}")
                                    }
                                }
                            }
                            // Returns null here to skip DocumentEmptyActivity's hardcoded finish().
                            return@intercept null
                        }

                        // FOOT_PRINT custom icon picker, started inside OplusKeyboardEffectsView.
                        if (requestCode == OplusKeyboardEffectsView.REQUEST_CODE_PICK_FOOT_PRINT) {
                            OplusKeyboardEffectsView.dispatchFootPrintResult(requestCode, resultCode, data)
                            // Returns null to skip the host finish() here too.
                            return@intercept null
                        }
                        chain.proceed()
                    }
                    module.log(Log.INFO, TAG, "Hooked DocumentEmptyActivity.onActivityResult directly for SAF picker")
                }.onFailure {
                    Log.w(TAG, "Failed to hook DocumentEmptyActivity.onActivityResult: ${it.message}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook DocumentEmptyActivity.onCreate; will retry on next entry point", t)
                module.log(Log.WARN, TAG, "Failed to hook DocumentEmptyActivity: $t")
            }
        }
    }

    private fun injectSettingsCategory(module: XposedModule, classLoader: ClassLoader, fragment: Any?) {
        if (fragment == null) return

        var hostScreen: Any? = null
        var injectedCategory: Any? = null
        var removeCategoryMethod: Method? = null

        try {
            val getContextMethod = fragment.javaClass.getMethod("getContext")
            val context = getContextMethod.invoke(fragment) as? Context ?: return

            val getScreenMethod = fragment.javaClass.getMethod("getPreferenceScreen")
            val screen = getScreenMethod.invoke(fragment) ?: run {
                Log.w(TAG, "PreferenceScreen is null on fragment $fragment")
                return
            }

            // Reads all 4 switches in one IPC call.
            val switches = OplusInputConfig.snapshot(module, context.packageName)
            val isLiquidGlass = switches.liquidGlass
            val isKeyboardEffects = switches.keyboardEffects
            val isKeyboardSounds = switches.keyboardSounds
            val isEnhancements = switches.enhancements

            if (!isLiquidGlass && !isKeyboardEffects && !isKeyboardSounds && !isEnhancements) {
                Log.i(TAG, "All realOS settings features disabled; skipping injection.")
                return
            }

            val prefClass = classLoader.loadClass("androidx.preference.Preference")
            val prefGroupClass = classLoader.loadClass("androidx.preference.PreferenceGroup")

            val findPrefMethod = screen.javaClass.getMethod("findPreference", CharSequence::class.java)
            val addPrefMethod = prefGroupClass.getMethod("addPreference", prefClass)
            val removePrefMethod = prefGroupClass.getMethod("removePreference", prefClass)
            val setKeyMethod = prefClass.getMethod("setKey", String::class.java)
            val setTitleMethod = prefClass.getMethod("setTitle", CharSequence::class.java)
            val setSummaryMethod = prefClass.getMethod("setSummary", CharSequence::class.java)
            val setOrderMethod = prefClass.getMethod("setOrder", Int::class.javaPrimitiveType)
            val getOrderMethod = prefClass.getMethod("getOrder")

            // Avoid duplicate category injection
            if (findPrefMethod.invoke(screen, KEY_REALOS_CATEGORY) != null) {
                return
            }

            // Dynamically discover OnPreferenceClickListener interface from setOnPreferenceClickListener method signature
            val setClickListenerMethod = prefClass.methods.firstOrNull {
                it.name == "setOnPreferenceClickListener" && it.parameterTypes.size == 1
            }
            val clickListenerInterface = setClickListenerMethod?.parameterTypes?.get(0)

            val couiCatClass = classLoader.loadClass("com.coui.appcompat.preference.COUIPreferenceCategory")
            val couiJumpClass = classLoader.loadClass("com.coui.appcompat.preference.COUIJumpPreference")
            val documentActClass = classLoader.loadClass(DOCUMENT_ACTIVITY_CLASS)

            // Locate feedback & restore default preferences
            val getPrefCountMethod = prefGroupClass.getMethod("getPreferenceCount")
            val getPrefMethod = prefGroupClass.getMethod("getPreference", Int::class.javaPrimitiveType)
            val totalTopGroups = (getPrefCountMethod.invoke(screen) as? Int) ?: 0

            var feedbackCategory: Any? = null
            var restoreCategory: Any? = null

            // Inspect screen direct child categories
            for (i in 0 until totalTopGroups) {
                val group = getPrefMethod.invoke(screen, i) ?: continue
                if (prefGroupClass.isInstance(group)) {
                    if (findPrefMethod.invoke(group, "key_feedback") != null) {
                        feedbackCategory = group
                    }
                    if (findPrefMethod.invoke(group, "key_restore_default_settings") != null) {
                        restoreCategory = group
                    }
                }
            }

            // Fallback via getParent if not found via direct iteration
            if (feedbackCategory == null) {
                runCatching {
                    val fbPref = findPrefMethod.invoke(screen, "key_feedback")
                    feedbackCategory = fbPref?.javaClass?.getMethod("getParent")?.invoke(fbPref)
                }
            }
            if (restoreCategory == null) {
                runCatching {
                    val resPref = findPrefMethod.invoke(screen, "key_restore_default_settings")
                    restoreCategory = resPref?.javaClass?.getMethod("getParent")?.invoke(resPref)
                }
            }

            val category = couiCatClass.getConstructor(Context::class.java, AttributeSet::class.java)
                .newInstance(context, null)
            setKeyMethod.invoke(category, KEY_REALOS_CATEGORY)

            // Order calculation with overflow protection
            if (feedbackCategory != null && restoreCategory != null) {
                val rawFbOrder = (getOrderMethod.invoke(feedbackCategory) as? Int) ?: 3
                val fbOrder = if (rawFbOrder >= Int.MAX_VALUE - 10 || rawFbOrder < 0) 30 else rawFbOrder
                setOrderMethod.invoke(category, fbOrder + 1)
                setOrderMethod.invoke(restoreCategory, fbOrder + 2)
            } else if (restoreCategory != null) {
                val rawRestoreOrder = (getOrderMethod.invoke(restoreCategory) as? Int) ?: 900
                val restoreOrder = if (rawRestoreOrder >= Int.MAX_VALUE - 10 || rawRestoreOrder < 0) 900 else rawRestoreOrder
                setOrderMethod.invoke(category, restoreOrder)
                setOrderMethod.invoke(restoreCategory, restoreOrder + 10)
            }

            // Attaches the category to the screen first, giving it the screen's PreferenceManager.
            // PreferenceGroup.addPreference on an unattached category reaches
            // synchronized(getPreferenceManager()) and throws NullPointerException.
            addPrefMethod.invoke(screen, category)
            hostScreen = screen
            injectedCategory = category
            removeCategoryMethod = removePrefMethod

            fun createClickListener(onClick: () -> Unit): Any? {
                if (clickListenerInterface == null) return null
                return Proxy.newProxyInstance(
                    clickListenerInterface.classLoader ?: classLoader,
                    arrayOf(clickListenerInterface)
                ) { proxy, method, args ->
                    when (method.name) {
                        "equals" -> proxy === args?.getOrNull(0)
                        "hashCode" -> System.identityHashCode(proxy)
                        "toString" -> "RealOSPreferenceClickListener@${Integer.toHexString(System.identityHashCode(proxy))}"
                        else -> {
                            try {
                                onClick()
                            } catch (t: Throwable) {
                                Log.e(TAG, "Preference click handler failed", t)
                            }
                            true
                        }
                    }
                }
            }

            var childAdded = 0

            // 1. Liquid glass settings
            if (isLiquidGlass) {
                runCatching {
                    val glassPref = couiJumpClass.getConstructor(Context::class.java)
                        .newInstance(context)
                    setKeyMethod.invoke(glassPref, KEY_LIQUID_GLASS_PREF)
                    setTitleMethod.invoke(glassPref, OplusWorkshopUiText.getLiquidGlassPrefTitle(context))
                    setSummaryMethod.invoke(glassPref, OplusWorkshopUiText.getPoweredByRealOS(context))
                    val listener = createClickListener {
                        val intent = Intent(context, documentActClass).apply {
                            action = ACTION_LIQUID_GLASS
                            putExtra(EXTRA_PAGE_TYPE, PAGE_LIQUID_GLASS)
                            if (context !is Activity) {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                        context.startActivity(intent)
                    }
                    if (listener != null) {
                        setClickListenerMethod?.invoke(glassPref, listener)
                    }
                    addPrefMethod.invoke(category, glassPref)
                    childAdded++
                }.onFailure { Log.e(TAG, "Failed to add liquid glass pref", it) }
            }

            // 2. Keyboard effects settings
            if (isKeyboardEffects) {
                runCatching {
                    val effectPref = couiJumpClass.getConstructor(Context::class.java)
                        .newInstance(context)
                    setKeyMethod.invoke(effectPref, KEY_KEYBOARD_EFFECTS_PREF)
                    setTitleMethod.invoke(effectPref, OplusWorkshopUiText.getKeyboardEffectsPrefTitle(context))
                    setSummaryMethod.invoke(effectPref, OplusWorkshopUiText.getPoweredByRealOS(context))
                    val listener = createClickListener {
                        val intent = Intent(context, documentActClass).apply {
                            action = ACTION_KEYBOARD_EFFECTS
                            putExtra(EXTRA_PAGE_TYPE, PAGE_KEYBOARD_EFFECTS)
                            if (context !is Activity) {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                        context.startActivity(intent)
                    }
                    if (listener != null) {
                        setClickListenerMethod?.invoke(effectPref, listener)
                    }
                    addPrefMethod.invoke(category, effectPref)
                    childAdded++
                }.onFailure { Log.e(TAG, "Failed to add keyboard effects pref", it) }
            }

            // 2b. Keyboard sound settings
            if (isKeyboardSounds) {
                runCatching {
                    val soundPref = couiJumpClass.getConstructor(Context::class.java)
                        .newInstance(context)
                    setKeyMethod.invoke(soundPref, KEY_KEYBOARD_SOUNDS_PREF)
                    setTitleMethod.invoke(soundPref, OplusWorkshopUiText.getKeyboardSoundsPrefTitle(context))
                    setSummaryMethod.invoke(soundPref, OplusWorkshopUiText.getPoweredByRealOS(context))
                    val listener = createClickListener {
                        val intent = Intent(context, documentActClass).apply {
                            action = ACTION_KEYBOARD_SOUNDS
                            putExtra(EXTRA_PAGE_TYPE, PAGE_KEYBOARD_SOUNDS)
                            if (context !is Activity) {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                        context.startActivity(intent)
                    }
                    if (listener != null) {
                        setClickListenerMethod?.invoke(soundPref, listener)
                    }
                    addPrefMethod.invoke(category, soundPref)
                    childAdded++
                }.onFailure { Log.e(TAG, "Failed to add keyboard sounds pref", it) }
            }

            // 3. Feature enhancements
            if (isEnhancements) {
                runCatching {
                    val enhPref = couiJumpClass.getConstructor(Context::class.java)
                        .newInstance(context)
                    setKeyMethod.invoke(enhPref, KEY_ENHANCEMENTS_PREF)
                    setTitleMethod.invoke(enhPref, OplusWorkshopUiText.getEnhancementsPrefTitle(context))
                    setSummaryMethod.invoke(enhPref, OplusWorkshopUiText.getPoweredByRealOS(context))
                    val listener = createClickListener {
                        val intent = Intent(context, documentActClass).apply {
                            action = ACTION_ENHANCEMENTS
                            putExtra(EXTRA_PAGE_TYPE, PAGE_ENHANCEMENTS)
                            if (context !is Activity) {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                        context.startActivity(intent)
                    }
                    if (listener != null) {
                        setClickListenerMethod?.invoke(enhPref, listener)
                    }
                    addPrefMethod.invoke(category, enhPref)
                    childAdded++
                }.onFailure { Log.e(TAG, "Failed to add enhancements pref", it) }
            }

            if (childAdded == 0) {
                runCatching {
                    removePrefMethod.invoke(screen, category)
                }
                Log.w(TAG, "No realOS preferences added, removed empty category")
                return
            }

            Log.i(TAG, "Successfully injected realOS category ($childAdded items) into SettingsHomeFragment via dynamic reflection")
            module.log(Log.INFO, TAG, "Successfully injected realOS category into SettingsHomeFragment")
        } catch (t: Throwable) {
            val sc = hostScreen
            val cat = injectedCategory
            val rm = removeCategoryMethod
            if (sc != null && cat != null && rm != null) {
                runCatching { rm.invoke(sc, cat) }
            }
            Log.e(TAG, "Failed to inject realOS category into SettingsHomeFragment", t)
            module.log(Log.ERROR, TAG, "Failed to inject realOS category: ${Log.getStackTraceString(t)}")
        }
    }
}
