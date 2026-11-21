package io.github.ra1nify.realos.xposed.hooks.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.navigation.SettingsConfig
import io.github.ra1nify.realos.xposed.hooks.navigation.glass.NavigationBarCapsuleOverlayView
import java.util.Collections
import java.util.WeakHashMap
import io.github.ra1nify.realos.navigation.SystemUiConfig
import io.github.ra1nify.realos.xposed.hooks.navigation.SystemUiNavBarPresetLoader

/**
 * Core implementation of the SystemUI navigation button custom icon hook.
 */
object SystemUiNavBarButtonHook {

    private const val TAG = "RealOS-SysUiNavBarButton"

    private val activeNavBarViews: MutableSet<Any> = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )

    private val customDrawablesSet: MutableSet<Any> = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )

    private val originalTemplateColors = WeakHashMap<Any, Pair<Int, Int>>()

    @Volatile
    private var isNativeTinted = false

    @Volatile
    private var isReceiverRegistered = false

    /**
     * Reflection field cache: `Class -> (field name -> Field)`.
     *
     * Names such as `mDarkIntensity`, `mLightColor` and `mBarTransitions` come from
     * AOSP/ColorOS SystemUI implementation details and change with host upgrades.
     * [fieldOf] walks the superclass chain and resolves each name once.
     */
    private val fieldCache = java.util.concurrent.ConcurrentHashMap<
        Class<*>,
        java.util.concurrent.ConcurrentHashMap<String, java.lang.reflect.Field>>()
    private val missingFields = java.util.concurrent.ConcurrentHashMap<
        Class<*>,
        MutableSet<String>>()

    private fun fieldOf(cls: Class<*>?, name: String): java.lang.reflect.Field? {
        if (cls == null) return null
        val perClass = fieldCache.getOrPut(cls) { java.util.concurrent.ConcurrentHashMap() }
        val cached = perClass[name]
        if (cached != null) return cached
        val missing = missingFields.getOrPut(cls) { java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap()) }
        if (missing.contains(name)) return null

        var found: java.lang.reflect.Field? = null
        var c: Class<*>? = cls
        while (c != null && found == null) {
            found = runCatching { c.getDeclaredField(name) }.getOrNull()
            c = c.superclass
        }
        if (found != null) {
            runCatching { found.isAccessible = true }
            perClass[name] = found
            return found
        } else {
            missing.add(name)
            return null
        }
    }

    /** Registered hot-reload receiver and its context, unregistered by [reset]. */
    private var navbarReceiver: BroadcastReceiver? = null
    private var navbarReceiverContext: java.lang.ref.WeakReference<Context>? = null

    private val isApplying = ThreadLocal<Boolean>()

    @Volatile
    private var cachedEnabled: Boolean? = null

    @Volatile
    private var cachedPresetIndex: Int? = null

    @Volatile
    private var cachedTintMode: Int? = null

    @Volatile
    private var cachedTintColor: Int? = null

    @Volatile
    private var cachedTintLightColor: Int? = null

    @Volatile
    private var cachedTintDarkColor: Int? = null

    @Volatile
    private var cachedGlassEnabled: Boolean? = null

    @Volatile
    private var cachedGlassOpacity: Int? = null

    @Volatile
    private var cachedCapsuleMode: Int? = null

    @Volatile
    private var cachedGlassSpotlight: Boolean? = null

    @Volatile
    private var cachedGlassLightColor: String? = null

    @Volatile
    private var cachedGlassLightAlpha: Int? = null

    @Volatile
    private var cachedGlassDarkColor: String? = null

    @Volatile
    private var cachedGlassDarkAlpha: Int? = null

    @Volatile
    private var glassSwitchMigrated = false

    private val ourTransparentPlaceholders = WeakHashMap<View, ColorDrawable>()

    /**
     * Original background replaced by the glass overlay, recorded per View.
     */
    private val savedOriginalBackgrounds = WeakHashMap<View, Drawable?>()

    private val savedOverrideAlpha = WeakHashMap<View, Float>()

    private const val TAG_CAPSULE_OVERLAY = 0x524F_4350 // "ROCP"

    fun install(module: XposedModule, classLoader: ClassLoader, handles: SystemUiHandles) {
        module.log(Log.INFO, TAG, "install() enter (SystemUI NavBarButton)")

        SystemUiConfig.prime(module)

        if (!handles.canHookNavBarButtons) {
            module.log(Log.WARN, TAG, "fail closed: NavBarButton symbols missing, skipping hook")
            return
        }
        val handles = handles

        // Validates the host fields this hook reads once at install time and logs each
        // result; a missing field disables only the path that reads it.
        verifyRequiredFields(module, handles)

        handles.onAttachedToWindowMethod?.let { method ->
            runCatching {
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        val navBarView = chain.thisObject
                        activeNavBarViews.add(navBarView)
                        (navBarView as? View)?.let { view ->
                            ensureReceiverRegistered(module, view.context, handles)
                            view.post {
                                cachedEnabled = null
                                cachedPresetIndex = null
                                cachedTintMode = null
                                cachedTintColor = null
                                cachedTintLightColor = null
                                cachedTintDarkColor = null
                                cachedGlassEnabled = null
                                cachedGlassOpacity = null
                                cachedCapsuleMode = null
                                applyCustomIcons(module, navBarView, handles)
                            }
                        }
                    } catch (t: Throwable) {
                        module.log(Log.WARN, TAG, "Post-processing after onAttachedToWindow failed", t)
                    }
                    result
                }
                module.log(Log.INFO, TAG, "Hooked onAttachedToWindow")
            }.onFailure {
                module.log(Log.ERROR, TAG, "fail closed: onAttachedToWindow hook install failed", it)
            }
        }

        handles.onDetachedFromWindowMethod?.let { method ->
            runCatching {
                module.hook(method).intercept { chain ->
                    try {
                        activeNavBarViews.remove(chain.thisObject)
                    } catch (_: Throwable) {
                    }
                    chain.proceed()
                }
                module.log(Log.INFO, TAG, "Hooked onDetachedFromWindow")
            }.onFailure {
                module.log(Log.WARN, TAG, "onDetachedFromWindow hook install failed", it)
            }
        }

        handles.updateMainIconsMethod?.let { method ->
            runCatching {
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        activeNavBarViews.add(chain.thisObject)
                        if (isApplying.get() != true) {
                            applyCustomIcons(module, chain.thisObject, handles)
                        }
                    } catch (t: Throwable) {
                        module.log(Log.WARN, TAG, "updateMainIcons: applying the custom buttons failed", t)
                    }
                    result
                }
                module.log(Log.INFO, TAG, "Hooked updateMainIcons")
            }.onFailure {
                module.log(Log.ERROR, TAG, "fail closed: updateMainIcons hook install failed", it)
            }
        }

        handles.updateNavButtonIconsMethod?.let { method ->
            runCatching {
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        activeNavBarViews.add(chain.thisObject)
                        if (isApplying.get() != true) {
                            applyCustomIcons(module, chain.thisObject, handles)
                        }
                    } catch (t: Throwable) {
                        module.log(Log.WARN, TAG, "updateNavButtonIcons: applying the custom buttons failed", t)
                    }
                    result
                }
                module.log(Log.INFO, TAG, "Hooked updateNavButtonIcons")
            }.onFailure {
                module.log(Log.WARN, TAG, "updateNavButtonIcons hook install failed", it)
            }
        }

        handles.updateIconsMethod?.let { method ->
            runCatching {
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    try {
                        activeNavBarViews.add(chain.thisObject)
                        if (isApplying.get() != true) {
                            applyCustomIcons(module, chain.thisObject, handles)
                        }
                    } catch (t: Throwable) {
                        module.log(Log.WARN, TAG, "updateIcons: applying the custom buttons failed", t)
                    }
                    result
                }
                module.log(Log.INFO, TAG, "Hooked updateIcons(Configuration)")
            }.onFailure {
                module.log(Log.WARN, TAG, "updateIcons hook install failed", it)
            }
        }

        handles.setDarkIntensityMethod?.let { method ->
            runCatching {
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val darkIntensity = (chain.args.firstOrNull() as? Float) ?: 0f
                    synchronized(activeNavBarViews) {
                        for (nb in activeNavBarViews) {
                            (nb as? ViewGroup)?.let { findCapsuleOverlay(it)?.darkIntensity = darkIntensity }
                        }
                    }
                    result
                }
                module.log(Log.INFO, TAG, "Hooked KeyButtonDrawable.setDarkIntensity")
            }.onFailure {
                module.log(Log.WARN, TAG, "setDarkIntensity hook install failed", it)
            }
        }

        handles.navBarViewClass?.let { navBarCls ->
            runCatching {
                val dispatchTouchMethod = navBarCls.getMethod("dispatchTouchEvent", MotionEvent::class.java)
            module.hook(dispatchTouchMethod).intercept { chain ->
                val event = chain.args.firstOrNull() as? MotionEvent
                if (event != null) {
                    val navBar = chain.thisObject as? ViewGroup
                    if (navBar != null) {
                        findCapsuleOverlay(navBar)?.onPointerEvent(event)
                    }
                }
                chain.proceed()
            }
                module.log(Log.INFO, TAG, "Hook installed: NavigationBarView.dispatchTouchEvent")
            }.onFailure {
                module.log(Log.WARN, TAG, "Failed to hook dispatchTouchEvent", it)
            }
        }

        hookBarBackgroundDrawable(module, classLoader)
    }

    /**
     * Verifies the host fields this hook depends on at install time and logs a
     * locatable status for each.
     *
     * Names (`mDarkIntensity` / `mLightColor` / `mIconPaint` / `mBarTransitions` /
     * `mBarBackground`) are AOSP / ColorOS SystemUI implementation details, not R8
     * names. Missing entries short-circuit the matching `fieldOf(...)?` call sites.
     */
    private fun verifyRequiredFields(module: XposedModule, handles: SystemUiHandles) {
        data class Spec(val key: String, val cls: Class<*>?, val field: String)

        val specs = listOf(
            Spec("nav_bar_buttons_dark_intensity", handles.navBarViewClass, "mDarkIntensity"),
            Spec("nav_bar_buttons_shadow_light_color", handles.shadowDrawableStateClass, "mLightColor"),
            Spec("nav_bar_buttons_shadow_dark_color", handles.shadowDrawableStateClass, "mDarkColor"),
            Spec("nav_bar_buttons_icon_paint", handles.keyButtonDrawableClass, "mIconPaint"),
            Spec("nav_bar_buttons_last_drawn_icon", handles.shadowDrawableStateClass, "mLastDrawnIcon"),
            Spec("nav_bar_buttons_bar_transitions", handles.navBarViewClass, "mBarTransitions")
        )
        for (spec in specs) {
            val present = fieldOf(spec.cls, spec.field) != null
            val state = if (present) "FOUND" else "MISSING"
            val msg = "HookPoint[${spec.key}] state=$state field=${spec.cls?.simpleName ?: "null"}#${spec.field}"
            if (present) module.log(Log.INFO, TAG, msg) else module.log(Log.WARN, TAG, "$msg (dependency short-circuits)")
        }
    }

    private fun ensureReceiverRegistered(
        module: XposedModule,
        context: Context,
        handles: SystemUiHandles
    ) {
        if (isReceiverRegistered) return
        val appContext = context.applicationContext ?: context

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                val action = intent?.action
                if (action != SystemUiConfig.ACTION_NAV_BUTTON_CHANGED &&
                    action != SystemUiConfig.LEGACY_ACTION_NAV_BUTTON_CHANGED
                ) return

                val isEnabled = if (intent.hasExtra(SystemUiConfig.EXTRA_ENABLED)) {
                    intent.getBooleanExtra(SystemUiConfig.EXTRA_ENABLED, false)
                } else {
                    cachedEnabled ?: SystemUiConfig.navButtonCustomEnabled(recvContext)
                }
                val presetIndex = if (intent.hasExtra(SystemUiConfig.EXTRA_PRESET_INDEX)) {
                    intent.getIntExtra(SystemUiConfig.EXTRA_PRESET_INDEX, 1)
                } else {
                    cachedPresetIndex ?: SystemUiConfig.navButtonPresetIndex(recvContext)
                }
                val tintMode = if (intent.hasExtra(SystemUiConfig.EXTRA_TINT_MODE)) {
                    intent.getIntExtra(SystemUiConfig.EXTRA_TINT_MODE, 1)
                } else {
                    cachedTintMode ?: SystemUiConfig.navButtonTintMode(recvContext)
                }
                val tintLightColor = if (intent.hasExtra(SystemUiConfig.EXTRA_TINT_LIGHT_COLOR)) {
                    intent.getIntExtra(SystemUiConfig.EXTRA_TINT_LIGHT_COLOR, SystemUiConfig.DEFAULT_TINT_LIGHT_COLOR)
                } else {
                    cachedTintLightColor ?: SystemUiConfig.navButtonTintLightColor(recvContext)
                }
                val tintDarkColor = if (intent.hasExtra(SystemUiConfig.EXTRA_TINT_DARK_COLOR)) {
                    intent.getIntExtra(SystemUiConfig.EXTRA_TINT_DARK_COLOR, SystemUiConfig.DEFAULT_TINT_DARK_COLOR)
                } else {
                    cachedTintDarkColor ?: SystemUiConfig.navButtonTintDarkColor(recvContext)
                }
                val tintColor = if (intent.hasExtra(SystemUiConfig.EXTRA_TINT_COLOR)) {
                    intent.getIntExtra(SystemUiConfig.EXTRA_TINT_COLOR, tintLightColor)
                } else {
                    cachedTintColor ?: tintLightColor
                }
                val isGlassEnabled = if (intent.hasExtra(SystemUiConfig.EXTRA_GLASS_ENABLED)) {
                    intent.getBooleanExtra(SystemUiConfig.EXTRA_GLASS_ENABLED, false)
                } else {
                    cachedGlassEnabled ?: SystemUiConfig.navButtonGlassEnabled(recvContext)
                }
                val glassOpacity = if (intent.hasExtra(SystemUiConfig.EXTRA_GLASS_OPACITY)) {
                    intent.getIntExtra(SystemUiConfig.EXTRA_GLASS_OPACITY, SystemUiConfig.DEFAULT_GLASS_OPACITY)
                } else {
                    cachedGlassOpacity ?: SystemUiConfig.navButtonGlassOpacity(recvContext)
                }
                val capsuleMode = if (intent.hasExtra(SystemUiConfig.EXTRA_GLASS_CAPSULE_MODE)) {
                    intent.getIntExtra(SystemUiConfig.EXTRA_GLASS_CAPSULE_MODE, SystemUiConfig.DEFAULT_GLASS_CAPSULE_MODE)
                } else {
                    cachedCapsuleMode ?: SystemUiConfig.navButtonGlassCapsuleMode(recvContext)
                }
                val glassSpotlight = if (intent.hasExtra(SystemUiConfig.EXTRA_GLASS_SPOTLIGHT_ENABLED)) {
                    intent.getBooleanExtra(SystemUiConfig.EXTRA_GLASS_SPOTLIGHT_ENABLED, true)
                } else {
                    cachedGlassSpotlight ?: SystemUiConfig.navButtonGlassSpotlightEnabled(recvContext)
                }
                val glassLightColor = intent.getStringExtra(SystemUiConfig.EXTRA_GLASS_LIGHT_COLOR)
                    ?: cachedGlassLightColor ?: SystemUiConfig.navButtonGlassLightColor(recvContext)
                val glassLightAlpha = if (intent.hasExtra(SystemUiConfig.EXTRA_GLASS_LIGHT_ALPHA)) {
                    intent.getIntExtra(SystemUiConfig.EXTRA_GLASS_LIGHT_ALPHA, SystemUiConfig.DEFAULT_GLASS_LIGHT_ALPHA)
                } else {
                    cachedGlassLightAlpha ?: SystemUiConfig.navButtonGlassLightAlpha(recvContext)
                }
                val glassDarkColor = intent.getStringExtra(SystemUiConfig.EXTRA_GLASS_DARK_COLOR)
                    ?: cachedGlassDarkColor ?: SystemUiConfig.navButtonGlassDarkColor(recvContext)
                val glassDarkAlpha = if (intent.hasExtra(SystemUiConfig.EXTRA_GLASS_DARK_ALPHA)) {
                    intent.getIntExtra(SystemUiConfig.EXTRA_GLASS_DARK_ALPHA, SystemUiConfig.DEFAULT_GLASS_DARK_ALPHA)
                } else {
                    cachedGlassDarkAlpha ?: SystemUiConfig.navButtonGlassDarkAlpha(recvContext)
                }
                cachedEnabled = isEnabled
                cachedPresetIndex = presetIndex
                cachedTintMode = tintMode
                cachedTintColor = tintColor
                cachedTintLightColor = tintLightColor
                cachedTintDarkColor = tintDarkColor
                cachedGlassEnabled = isGlassEnabled
                cachedGlassOpacity = glassOpacity
                cachedCapsuleMode = capsuleMode
                cachedGlassSpotlight = glassSpotlight
                cachedGlassLightColor = glassLightColor
                cachedGlassLightAlpha = glassLightAlpha
                cachedGlassDarkColor = glassDarkColor
                cachedGlassDarkAlpha = glassDarkAlpha

                if (presetIndex == SystemUiConfig.PRESET_INDEX_CUSTOM) {
                    val b64Back = intent.getStringExtra(SystemUiConfig.EXTRA_CUSTOM_BACK)
                    val b64Home = intent.getStringExtra(SystemUiConfig.EXTRA_CUSTOM_HOME)
                    val b64Recent = intent.getStringExtra(SystemUiConfig.EXTRA_CUSTOM_RECENT)
                    if (b64Back != null) SystemUiNavBarPresetLoader.setCustomBitmapBase64("back", b64Back)
                    if (b64Home != null) SystemUiNavBarPresetLoader.setCustomBitmapBase64("home", b64Home)
                    if (b64Recent != null) SystemUiNavBarPresetLoader.setCustomBitmapBase64("recent", b64Recent)
                } else {
                    SystemUiNavBarPresetLoader.clearCustomBitmapCache()
                }

                val views = synchronized(activeNavBarViews) {
                    activeNavBarViews.filter { (it as? View)?.isAttachedToWindow == true }
                }.ifEmpty {
                    findNavBarViewsFromWindowManager(handles).filter { (it as? View)?.isAttachedToWindow == true }.also { found ->
                        synchronized(activeNavBarViews) {
                            activeNavBarViews.clear()
                            activeNavBarViews.addAll(found)
                        }
                    }
                }
                module.log(
                    Log.INFO,
                    TAG,
                    "Hot reload broadcast received: enabled=$isEnabled preset=$presetIndex tintMode=$tintMode tintLight=${String.format("#%08X", tintLightColor)} tintDark=${String.format("#%08X", tintDarkColor)} glassEnabled=$isGlassEnabled capsuleMode=$capsuleMode spotlight=$glassSpotlight light=($glassLightColor,$glassLightAlpha%) dark=($glassDarkColor,$glassDarkAlpha%) (activeViews=${views.size})"
                )

                val mainHandler = Handler(Looper.getMainLooper())
                mainHandler.post {
                    for (navBarView in views) {
                        runCatching {
                            applyCustomIcons(
                                module = module,
                                navBarView = navBarView,
                                handles = handles,
                                explicitEnabled = isEnabled,
                                explicitPresetIndex = presetIndex,
                                explicitTintMode = tintMode,
                                explicitTintColor = tintColor,
                                explicitTintLightColor = tintLightColor,
                                explicitTintDarkColor = tintDarkColor,
                                explicitGlassEnabled = isGlassEnabled,
                                explicitGlassOpacity = glassOpacity,
                                explicitCapsuleMode = capsuleMode,
                                explicitGlassSpotlight = glassSpotlight,
                                explicitGlassLightColor = glassLightColor,
                                explicitGlassLightAlpha = glassLightAlpha,
                                explicitGlassDarkColor = glassDarkColor,
                                explicitGlassDarkAlpha = glassDarkAlpha
                            )
                        }.onFailure {
                            module.log(Log.WARN, TAG, "Hot reload refresh failed", it)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(SystemUiConfig.ACTION_NAV_BUTTON_CHANGED)
            addAction(SystemUiConfig.LEGACY_ACTION_NAV_BUTTON_CHANGED)
        }
        try {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            navbarReceiver = receiver
            navbarReceiverContext = java.lang.ref.WeakReference(appContext)
            isReceiverRegistered = true
            module.log(Log.INFO, TAG, "Registered the hot reload broadcast receiver at the SystemUI Application level")
        } catch (t: Throwable) {
            module.log(Log.WARN, TAG, "Failed to register the hot reload broadcast receiver", t)
        }
    }

    /**
     * Hot-reload cleanup: unregisters the broadcast receiver and resets the flag.
     */
    fun reset() {
        val receiver = navbarReceiver
        val ctx = navbarReceiverContext?.get()
        if (receiver != null && ctx != null) {
            runCatching { ctx.unregisterReceiver(receiver) }
                .onFailure { Log.w(TAG, "Failed to unregister the hot reload broadcast receiver", it) }
        }
        navbarReceiver = null
        navbarReceiverContext = null
        isReceiverRegistered = false
        // Hot reload swaps the host Class view; the old field cache is dropped.
        fieldCache.clear()
        missingFields.clear()
    }

    private fun applyCustomIcons(
        module: XposedModule,
        navBarView: Any,
        handles: SystemUiHandles,
        explicitEnabled: Boolean? = null,
        explicitPresetIndex: Int? = null,
        explicitTintMode: Int? = null,
        explicitTintColor: Int? = null,
        explicitTintLightColor: Int? = null,
        explicitTintDarkColor: Int? = null,
        explicitGlassEnabled: Boolean? = null,
        explicitGlassOpacity: Int? = null,
        explicitCapsuleMode: Int? = null,
        explicitGlassSpotlight: Boolean? = null,
        explicitGlassLightColor: String? = null,
        explicitGlassLightAlpha: Int? = null,
        explicitGlassDarkColor: String? = null,
        explicitGlassDarkAlpha: Int? = null
    ) {
        if (isApplying.get() == true) return
        isApplying.set(true)
        try {
            val view = navBarView as? View ?: return
            val context = view.context

            if (!glassSwitchMigrated) {
                glassSwitchMigrated = true
                SettingsConfig.migrateNavButtonGlassSwitch(context)
            }

            val enabled = explicitEnabled ?: cachedEnabled ?: SystemUiConfig.navButtonCustomEnabled(context).also {
                cachedEnabled = it
            }
            val glassEnabled = explicitGlassEnabled ?: cachedGlassEnabled ?: SystemUiConfig.navButtonGlassEnabled(context).also {
                cachedGlassEnabled = it
            }
            val glassOpacity = explicitGlassOpacity ?: cachedGlassOpacity ?: SystemUiConfig.navButtonGlassOpacity(context).also {
                cachedGlassOpacity = it
            }
            val capsuleMode = if (glassEnabled) {
                explicitCapsuleMode ?: cachedCapsuleMode ?: SystemUiConfig.navButtonGlassCapsuleMode(context).also {
                    cachedCapsuleMode = it
                }
            } else {
                SystemUiConfig.CAPSULE_MODE_NONE
            }
            val glassSpotlight = explicitGlassSpotlight ?: cachedGlassSpotlight ?: SystemUiConfig.navButtonGlassSpotlightEnabled(context).also {
                cachedGlassSpotlight = it
            }
            val glassLightColor = explicitGlassLightColor ?: cachedGlassLightColor ?: SystemUiConfig.navButtonGlassLightColor(context).also {
                cachedGlassLightColor = it
            }
            val glassLightAlpha = explicitGlassLightAlpha ?: cachedGlassLightAlpha ?: SystemUiConfig.navButtonGlassLightAlpha(context).also {
                cachedGlassLightAlpha = it
            }
            val glassDarkColor = explicitGlassDarkColor ?: cachedGlassDarkColor ?: SystemUiConfig.navButtonGlassDarkColor(context).also {
                cachedGlassDarkColor = it
            }
            val glassDarkAlpha = explicitGlassDarkAlpha ?: cachedGlassDarkAlpha ?: SystemUiConfig.navButtonGlassDarkAlpha(context).also {
                cachedGlassDarkAlpha = it
            }

            applyNavBarBackgroundTransparency(view, glassEnabled)

            ensureCapsuleOverlay(
                navBarView = navBarView,
                handles = handles,
                capsuleMode = capsuleMode,
                glassOpacity = glassOpacity,
                glassSpotlight = glassSpotlight,
                glassLightColor = glassLightColor,
                glassLightAlpha = glassLightAlpha,
                glassDarkColor = glassDarkColor,
                glassDarkAlpha = glassDarkAlpha
            )

            val tintMode = explicitTintMode ?: cachedTintMode ?: SystemUiConfig.navButtonTintMode(context).also {
                cachedTintMode = it
            }
            val tintLightColor = explicitTintLightColor ?: cachedTintLightColor ?: SystemUiConfig.navButtonTintLightColor(context).also {
                cachedTintLightColor = it
            }
            val tintDarkColor = explicitTintDarkColor ?: cachedTintDarkColor ?: SystemUiConfig.navButtonTintDarkColor(context).also {
                cachedTintDarkColor = it
            }
            val tintColor = explicitTintColor ?: cachedTintColor ?: tintLightColor

            val needsIconOverride = enabled || tintMode == 1

            if (!needsIconOverride && !glassEnabled) {
                cachedEnabled = false
                cachedTintMode = tintMode
                cachedTintColor = tintColor
                cachedTintLightColor = tintLightColor
                cachedTintDarkColor = tintDarkColor
                cachedGlassEnabled = false
                cachedGlassOpacity = glassOpacity
                cachedCapsuleMode = SystemUiConfig.CAPSULE_MODE_NONE
                (navBarView as? ViewGroup)?.let { findCapsuleOverlay(it)?.capsuleMode = SystemUiConfig.CAPSULE_MODE_NONE }
                if (customDrawablesSet.isNotEmpty() || isNativeTinted) {
                    module.log(Log.INFO, TAG, "Custom key icons, tint mask and liquid glass are all off; restoring the system stock icons")
                    customDrawablesSet.clear()
                    isNativeTinted = false
                    restoreNativeIcons(navBarView, handles)
                }
                return
            }

            if (!needsIconOverride) {
                cachedEnabled = false
                cachedTintMode = tintMode
                cachedTintColor = tintColor
                cachedTintLightColor = tintLightColor
                cachedTintDarkColor = tintDarkColor
                cachedGlassEnabled = glassEnabled
                cachedGlassOpacity = glassOpacity
                cachedCapsuleMode = capsuleMode
                if (customDrawablesSet.isNotEmpty() || isNativeTinted) {
                    module.log(Log.INFO, TAG, "Custom icons and icon tinting are off; restoring the system stock icons (the nav bar capsule background is kept)")
                    customDrawablesSet.clear()
                    isNativeTinted = false
                    restoreNativeIcons(navBarView, handles)
                }
                return
            }

            if (!enabled && customDrawablesSet.isNotEmpty()) {
                module.log(Log.INFO, TAG, "Custom key icons are off; restoring the system stock icons before applying the tint mask")
                customDrawablesSet.clear()
                restoreNativeIcons(navBarView, handles)
            }

            var backTemplate = handles.backIconField?.get(navBarView)
            var homeTemplate = handles.homeDefaultIconField?.get(navBarView)
            var recentTemplate = handles.recentIconField?.get(navBarView)

            if (backTemplate == null || homeTemplate == null || recentTemplate == null) {
                module.log(Log.INFO, TAG, "Template icons null, calling updateMainIcons")
                handles.updateMainIconsMethod?.invoke(navBarView)
                backTemplate = handles.backIconField?.get(navBarView)
                homeTemplate = handles.homeDefaultIconField?.get(navBarView)
                recentTemplate = handles.recentIconField?.get(navBarView)
            }

            if (backTemplate == null || homeTemplate == null || recentTemplate == null) {
                module.log(
                    Log.WARN, TAG,
                    "fail closed: template icons still null back=${backTemplate != null} home=${homeTemplate != null} recent=${recentTemplate != null}"
                )
                return
            }

            if (!enabled && tintMode == 1) {
                isNativeTinted = true
                applyTintToNativeTemplate(backTemplate, tintLightColor, tintDarkColor, handles)
                applyTintToNativeTemplate(homeTemplate, tintLightColor, tintDarkColor, handles)
                applyTintToNativeTemplate(recentTemplate, tintLightColor, tintDarkColor, handles)

                val backDispatcher = handles.getBackButtonMethod?.invoke(navBarView)
                val homeDispatcher = handles.getHomeButtonMethod?.invoke(navBarView)
                val recentDispatcher = handles.getRecentsButtonMethod?.invoke(navBarView)

                handles.dispatcherSetImageDrawableMethod?.invoke(backDispatcher, backTemplate)
                handles.dispatcherSetImageDrawableMethod?.invoke(homeDispatcher, homeTemplate)
                handles.dispatcherSetImageDrawableMethod?.invoke(recentDispatcher, recentTemplate)

                handles.orientBackButtonMethod?.invoke(navBarView, backTemplate)
                val isVertical = handles.isVerticalField?.getBoolean(navBarView) ?: false
                handles.setRotationMethod?.invoke(homeTemplate, if (isVertical) 90.0f else 0.0f)

                cachedEnabled = false
                cachedTintMode = tintMode
                cachedTintColor = tintColor
                cachedTintLightColor = tintLightColor
                cachedTintDarkColor = tintDarkColor
                cachedGlassEnabled = glassEnabled
                cachedGlassOpacity = glassOpacity
                cachedCapsuleMode = capsuleMode

                view.postInvalidate()
                module.log(Log.INFO, TAG, "Applied the custom foreground tint mask to the stock icons and rebound to refresh")
                return
            }

            val presetIndex = if (enabled) {
                explicitPresetIndex ?: cachedPresetIndex ?: SystemUiConfig.navButtonPresetIndex(context).also {
                    cachedPresetIndex = it
                }
            } else {
                1
            }

            val isCustom = enabled && (presetIndex == SystemUiConfig.PRESET_INDEX_CUSTOM)
            val backBmp = if (isCustom) {
                SystemUiNavBarPresetLoader.getCustomBitmap(module, context, "back")
            } else {
                SystemUiNavBarPresetLoader.getPresetBitmap(module, "back", presetIndex)
            }
            val homeBmp = if (isCustom) {
                SystemUiNavBarPresetLoader.getCustomBitmap(module, context, "home")
            } else {
                SystemUiNavBarPresetLoader.getPresetBitmap(module, "home", presetIndex)
            }
            val recentBmp = if (isCustom) {
                SystemUiNavBarPresetLoader.getCustomBitmap(module, context, "recent")
            } else {
                SystemUiNavBarPresetLoader.getPresetBitmap(module, "recent", presetIndex)
            }

            if (backBmp == null || homeBmp == null || recentBmp == null) {
                module.log(
                    Log.WARN, TAG,
                    "fail closed: ${if (isCustom) "custom" else "preset"} image load failed back=${backBmp != null} home=${homeBmp != null} recent=${recentBmp != null}"
                )
                return
            }

            val customBack = SystemUiNavBarPresetLoader.createKeyButtonDrawable(
                module = module,
                context = context,
                template = backTemplate,
                bitmap = backBmp,
                tintMode = tintMode,
                tintLightColor = tintLightColor,
                tintDarkColor = tintDarkColor,
                handles = handles
            )
            val customHome = SystemUiNavBarPresetLoader.createKeyButtonDrawable(
                module = module,
                context = context,
                template = homeTemplate,
                bitmap = homeBmp,
                tintMode = tintMode,
                tintLightColor = tintLightColor,
                tintDarkColor = tintDarkColor,
                handles = handles
            )
            val customRecent = SystemUiNavBarPresetLoader.createKeyButtonDrawable(
                module = module,
                context = context,
                template = recentTemplate,
                bitmap = recentBmp,
                tintMode = tintMode,
                tintLightColor = tintLightColor,
                tintDarkColor = tintDarkColor,
                handles = handles
            )

            if (customBack == null || customHome == null || customRecent == null) {
                module.log(
                    Log.WARN, TAG,
                    "fail closed: custom KeyButtonDrawable build failed back=${customBack != null} home=${customHome != null} recent=${customRecent != null}"
                )
                return
            }

            customDrawablesSet.add(customBack)
            customDrawablesSet.add(customHome)
            customDrawablesSet.add(customRecent)

            handles.backIconField?.set(navBarView, customBack)
            handles.homeDefaultIconField?.set(navBarView, customHome)
            handles.recentIconField?.set(navBarView, customRecent)

            clearLastDrawnIcon(handles, customBack)
            clearLastDrawnIcon(handles, customHome)
            clearLastDrawnIcon(handles, customRecent)

            val backDispatcher = handles.getBackButtonMethod?.invoke(navBarView)
            val homeDispatcher = handles.getHomeButtonMethod?.invoke(navBarView)
            val recentDispatcher = handles.getRecentsButtonMethod?.invoke(navBarView)

            handles.dispatcherSetImageDrawableMethod?.invoke(backDispatcher, customBack)
            handles.dispatcherSetImageDrawableMethod?.invoke(homeDispatcher, customHome)
            handles.dispatcherSetImageDrawableMethod?.invoke(recentDispatcher, customRecent)

            applyDrawableToDispatcherViews(backDispatcher, customBack)
            applyDrawableToDispatcherViews(homeDispatcher, customHome)
            applyDrawableToDispatcherViews(recentDispatcher, customRecent)

            handles.orientBackButtonMethod?.invoke(navBarView, customBack)
            val isVertical = handles.isVerticalField?.getBoolean(navBarView) ?: false
            handles.setRotationMethod?.invoke(customHome, if (isVertical) 90.0f else 0.0f)

            val currentDarkIntensity: Float = runCatching {
                handles.getDarkIntensityMethod?.invoke(backTemplate) as? Float
            }.getOrNull() ?: runCatching {
                val state = (backTemplate as? Drawable)?.constantState ?: return@runCatching 0f
                val stateClass = handles.shadowDrawableStateClass ?: return@runCatching 0f
                fieldOf(stateClass, "mDarkIntensity")?.getFloat(state) ?: 0f
            }.getOrDefault(0f)

            val initialColor = if (tintMode == 1) {
                android.animation.ArgbEvaluator()
                    .evaluate(currentDarkIntensity, tintDarkColor, tintLightColor) as Int
            } else {
                val state = (backTemplate as? Drawable)?.constantState
                val nativeLightColor = runCatching {
                    fieldOf(handles.shadowDrawableStateClass, "mLightColor")?.getInt(state)
                }.getOrDefault(SystemUiConfig.DEFAULT_TINT_DARK_COLOR)
                val nativeDarkColor = runCatching {
                    fieldOf(handles.shadowDrawableStateClass, "mDarkColor")?.getInt(state)
                }.getOrDefault(SystemUiConfig.DEFAULT_TINT_LIGHT_COLOR)
                android.animation.ArgbEvaluator()
                    .evaluate(currentDarkIntensity, nativeLightColor, nativeDarkColor) as Int
            }

            listOf(customBack, customHome, customRecent).forEach { drawable ->
                runCatching {
                    val iconPaint = fieldOf(handles.keyButtonDrawableClass, "mIconPaint")?.get(drawable) as? Paint
                    iconPaint?.colorFilter = android.graphics.PorterDuffColorFilter(initialColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
                    (drawable as? Drawable)?.invalidateSelf()
                }
            }

            isNativeTinted = false
            cachedEnabled = enabled
            cachedPresetIndex = presetIndex
            cachedTintMode = tintMode
            cachedTintColor = tintColor
            cachedTintLightColor = tintLightColor
            cachedTintDarkColor = tintDarkColor
            cachedGlassEnabled = glassEnabled
            cachedGlassOpacity = glassOpacity
            cachedCapsuleMode = capsuleMode

            view.postInvalidate()
            module.log(
                Log.INFO,
                TAG,
                "Applied the custom navigation button icons: enabled=$enabled preset=$presetIndex tintMode=$tintMode tintLight=${String.format("#%08X", tintLightColor)} tintDark=${String.format("#%08X", tintDarkColor)} glassEnabled=$glassEnabled"
            )
        } finally {
            isApplying.set(false)
        }
    }

    private fun findNavBarViewsFromWindowManager(handles: SystemUiHandles): List<Any> {
        return runCatching {
            val wmgClass = Class.forName("android.view.WindowManagerGlobal")
            val getInstanceMethod = wmgClass.getMethod("getInstance")
            val wmg = getInstanceMethod.invoke(null) ?: return emptyList()
            val viewsField = wmgClass.getDeclaredField("mViews").apply { isAccessible = true }
            val rootViews = viewsField.get(wmg) as? List<*> ?: return emptyList()
            val found = mutableListOf<Any>()
            val navBarCls = handles.navBarViewClass
            if (navBarCls != null) {
                for (root in rootViews) {
                    val rv = root as? View ?: continue
                    findNavBarViewRecursively(rv, navBarCls, found)
                }
            }
            found
        }.getOrDefault(emptyList())
    }

    private fun findNavBarViewRecursively(v: View, targetClass: Class<*>, out: MutableList<Any>) {
        if (targetClass.isInstance(v)) {
            out.add(v)
            return
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val child = v.getChildAt(i) ?: continue
                findNavBarViewRecursively(child, targetClass, out)
            }
        }
    }

    private fun clearLastDrawnIcon(handles: SystemUiHandles, drawable: Any?) {
        runCatching {
            if (drawable == null) return
            val keyButtonCls = handles.keyButtonDrawableClass ?: return
            val shadowStateCls = handles.shadowDrawableStateClass ?: return
            val state = fieldOf(keyButtonCls, "mState")?.get(drawable) ?: return
            fieldOf(shadowStateCls, "mLastDrawnIcon")?.set(state, null)
        }
    }

    private fun applyDrawableToDispatcherViews(dispatcher: Any?, drawable: Any?) {
        if (dispatcher == null || drawable !is Drawable) return
        runCatching {
            // Applies the drawable to every View associated with the dispatcher; this
            // path skips the attached/visible filter and covers recycled copies.
            for (v in NavButtonDispatcherViews.allViews(dispatcher)) {
                (v as? android.widget.ImageView)?.apply {
                    setImageDrawable(drawable)
                    invalidate()
                    postInvalidate()
                }
            }
        }
    }

    private fun findCapsuleOverlay(navBar: ViewGroup): NavigationBarCapsuleOverlayView? {
        for (i in 0 until navBar.childCount) {
            val child = navBar.getChildAt(i)
            if (child is NavigationBarCapsuleOverlayView || child.tag == TAG_CAPSULE_OVERLAY) {
                return child as? NavigationBarCapsuleOverlayView
            }
        }
        return null
    }

    private fun applyNavBarBackgroundTransparency(navBar: View, isGlass: Boolean) {
        runCatching {
            val barBg = findBarBackground(navBar)
            if (isGlass) {
                if (barBg != null && !savedOverrideAlpha.containsKey(navBar)) {
                    readBarBackgroundOverrideAlpha(barBg)?.let { savedOverrideAlpha[navBar] = it }
                }
                var v: View? = navBar
                while (v != null) {
                    val existing = ourTransparentPlaceholders[v]
                    if (existing == null) {
                        if (!savedOriginalBackgrounds.containsKey(v)) {
                            savedOriginalBackgrounds[v] = v.background
                        }
                        val placeholder = ColorDrawable(Color.TRANSPARENT)
                        ourTransparentPlaceholders[v] = placeholder
                        v.background = placeholder
                    } else if (v.background !== existing) {
                        v.background = existing
                    }
                    v = v.parent as? View
                }
                setBarBackgroundOverrideAlpha(barBg, 0f)
            } else {
                var v: View? = navBar
                while (v != null) {
                    val placeholder = ourTransparentPlaceholders.remove(v)
                    val hadSaved = savedOriginalBackgrounds.containsKey(v)
                    val saved = if (hadSaved) savedOriginalBackgrounds.remove(v) else null
                    if (placeholder != null && v.background === placeholder) {
                        // Restores the saved original value, including a saved null.
                        v.background = when {
                            hadSaved -> saved
                            v === navBar -> barBg
                            else -> null
                        }
                    }
                    v = v.parent as? View
                }
                setBarBackgroundOverrideAlpha(barBg, savedOverrideAlpha.remove(navBar) ?: 1f)
            }
        }
    }

    private fun findBarBackground(navBar: View): Drawable? {
        // fieldOf walks the superclass chain and caches; no manual two-level fallback.
        val barTransitionsField = fieldOf(navBar.javaClass, "mBarTransitions") ?: return null
        val transitions = barTransitionsField.get(navBar) ?: return null
        val barBgField = fieldOf(transitions.javaClass, "mBarBackground") ?: return null
        return barBgField.get(transitions) as? Drawable
    }

    private fun setBarBackgroundOverrideAlpha(barBg: Drawable?, alpha: Float) {
        if (barBg == null) return
        runCatching {
            barBg.javaClass.getMethod("setOverrideAlpha", Float::class.javaPrimitiveType)
                .invoke(barBg, alpha)
        }.onFailure {
            runCatching {
                barBg.javaClass.superclass
                    ?.getMethod("setOverrideAlpha", Float::class.javaPrimitiveType)
                    ?.invoke(barBg, alpha)
            }
        }
    }

    private fun readBarBackgroundOverrideAlpha(barBg: Drawable): Float? = runCatching {
        barBg.javaClass.getMethod("getOverrideAlpha").invoke(barBg) as? Float
    }.getOrNull() ?: runCatching {
        barBg.javaClass.superclass?.getMethod("getOverrideAlpha")?.invoke(barBg) as? Float
    }.getOrNull()

    private fun ensureCapsuleOverlay(
        navBarView: Any,
        handles: SystemUiHandles,
        capsuleMode: Int,
        glassOpacity: Int,
        glassSpotlight: Boolean,
        glassLightColor: String,
        glassLightAlpha: Int,
        glassDarkColor: String,
        glassDarkAlpha: Int
    ): NavigationBarCapsuleOverlayView? {
        val navBar = navBarView as? ViewGroup ?: return null
        var overlay = findCapsuleOverlay(navBar)
        if (overlay == null) {
            overlay = NavigationBarCapsuleOverlayView(navBar.context).apply {
                tag = TAG_CAPSULE_OVERLAY
            }
            navBar.addView(
                overlay,
                0,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        val backDispatcher = runCatching { handles.getBackButtonMethod?.invoke(navBarView) }.getOrNull()
        val homeDispatcher = runCatching { handles.getHomeButtonMethod?.invoke(navBarView) }.getOrNull()
        val recentDispatcher = runCatching { handles.getRecentsButtonMethod?.invoke(navBarView) }.getOrNull()

        val backView = resolveDispatcherView(backDispatcher)
        val homeView = resolveDispatcherView(homeDispatcher)
        val recentView = resolveDispatcherView(recentDispatcher)

        overlay.capsuleMode = capsuleMode
        overlay.glassOpacity = glassOpacity
        overlay.spotlightEnabled = glassSpotlight
        overlay.lightColor = glassLightColor
        overlay.lightAlpha = glassLightAlpha
        overlay.darkColor = glassDarkColor
        overlay.darkAlpha = glassDarkAlpha
        overlay.setButtons(backView, homeView, recentView)
        overlay.setDispatchers(backDispatcher, homeDispatcher, recentDispatcher)
        overlay.postInvalidateOnAnimation()

        return overlay
    }

    /** Routes the three call sites through [NavButtonDispatcherViews]. */
    private fun resolveDispatcherView(dispatcher: Any?): View? =
        NavButtonDispatcherViews.resolve(null, dispatcher)

    private fun hookBarBackgroundDrawable(module: XposedModule, classLoader: ClassLoader) {
        val candidateClasses = listOf(
            "com.oplusos.systemui.navigationbar.OplusBarBackgroundDrawable",
            "com.android.systemui.statusbar.phone.BarTransitions\$BarBackgroundDrawable"
        )
        for (name in candidateClasses) {
            val clazz = runCatching { classLoader.loadClass(name) }.getOrNull() ?: continue
            val drawMethod = runCatching {
                clazz.getDeclaredMethod("draw", Canvas::class.java).apply { isAccessible = true }
            }.getOrNull() ?: continue

            runCatching {
                module.hook(drawMethod).intercept { chain ->
                    val isGlass = cachedGlassEnabled ?: SystemUiConfig.navButtonGlassEnabled()
                    if (isGlass) {
                        null
                    } else {
                        chain.proceed()
                    }
                }
                module.log(Log.INFO, TAG, "Hooked $name.draw(Canvas) (transparent background short circuit)")
            }.onFailure {
                module.log(Log.WARN, TAG, "$name.draw(Canvas) hook install failed", it)
            }
        }
    }

    private fun restoreNativeIcons(navBarView: Any, handles: SystemUiHandles) {
        runCatching {
            if (handles.updateMainIconsMethod != null) {
                handles.updateMainIconsMethod?.invoke(navBarView)
            } else {
                val nativeBack = handles.backIconField?.get(navBarView)
                val nativeHome = handles.homeDefaultIconField?.get(navBarView)
                val nativeRecent = handles.recentIconField?.get(navBarView)

                if (nativeBack != null && nativeHome != null && nativeRecent != null) {
                    val backDispatcher = handles.getBackButtonMethod?.invoke(navBarView)
                    val homeDispatcher = handles.getHomeButtonMethod?.invoke(navBarView)
                    val recentDispatcher = handles.getRecentsButtonMethod?.invoke(navBarView)

                    handles.dispatcherSetImageDrawableMethod?.invoke(backDispatcher, nativeBack)
                    handles.dispatcherSetImageDrawableMethod?.invoke(homeDispatcher, nativeHome)
                    handles.dispatcherSetImageDrawableMethod?.invoke(recentDispatcher, nativeRecent)

                    handles.orientBackButtonMethod?.invoke(navBarView, nativeBack)
                    val isVertical = handles.isVerticalField?.getBoolean(navBarView) ?: false
                    handles.setRotationMethod?.invoke(nativeHome, if (isVertical) 90.0f else 0.0f)
                }
            }

            listOfNotNull(
                handles.backIconField?.get(navBarView),
                handles.homeDefaultIconField?.get(navBarView),
                handles.recentIconField?.get(navBarView)
            ).forEach { icon ->
                val state = (icon as? Drawable)?.constantState ?: return@forEach
                val orig = originalTemplateColors[state] ?: return@forEach
                val stateClass = handles.shadowDrawableStateClass ?: return@forEach
                runCatching {
                    fieldOf(stateClass, "mLightColor")?.setInt(state, orig.first)
                    fieldOf(stateClass, "mDarkColor")?.setInt(state, orig.second)
                    fieldOf(stateClass, "mLastDrawnIcon")?.set(state, null)
                }
                val currentDark = runCatching {
                    handles.getDarkIntensityMethod?.invoke(icon) as? Float
                }.getOrNull() ?: 0f
                runCatching {
                    handles.setDarkIntensityMethod?.invoke(icon, currentDark)
                }
                (icon as? Drawable)?.invalidateSelf()
            }

            (navBarView as? View)?.postInvalidate()
        }
    }

    private fun applyTintToNativeTemplate(
        template: Any?,
        tintLightColor: Int,
        tintDarkColor: Int,
        handles: SystemUiHandles
    ) {
        template ?: return
        val state = (template as? Drawable)?.constantState ?: return
        val stateClass = handles.shadowDrawableStateClass ?: return
        runCatching {
            val lightField = fieldOf(stateClass, "mLightColor") ?: return@runCatching
            val darkField = fieldOf(stateClass, "mDarkColor") ?: return@runCatching
            if (!originalTemplateColors.containsKey(state)) {
                originalTemplateColors[state] = Pair(lightField.getInt(state), darkField.getInt(state))
            }
            lightField.setInt(state, tintDarkColor)
            darkField.setInt(state, tintLightColor)
            fieldOf(stateClass, "mLastDrawnIcon")?.set(state, null)
        }
        val currentDark = runCatching {
            handles.getDarkIntensityMethod?.invoke(template) as? Float
        }.getOrNull() ?: 0f
        runCatching {
            handles.setDarkIntensityMethod?.invoke(template, currentDark)
        }
        (template as? Drawable)?.invalidateSelf()
    }
}
