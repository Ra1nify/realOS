package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.os.Build
import android.provider.Settings
import android.graphics.Outline
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.RealOSColorWheel
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui.AndroidBotSpec

/**
 * Factory for ColorOS native COUI components.
 *
 * Loads com.coui.appcompat.* classes via reflection. Falls back to
 * standard AndroidX widgets when those classes are absent.
 */
object CouiComponentFactory {

    /** Menu item id of the keyboard preview toolbar button. */
    private const val ID_PREVIEW_BUTTON = 0x4C46_5056 // "LFPV"

    fun dp(dp: Int, context: Context): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    fun dp(dp: Float, context: Context): Float =
        dp * context.resources.displayMetrics.density

    /**
     * Resolves a host resource ID, including repackaged variants such as `com.realme.keyboard`.
     *
     * Some repackaged APKs change the package in `AndroidManifest.xml` while `resources.arsc`
     * keeps the original package name such as `com.oplus.keyboard`. A lookup with
     * `context.packageName` then returns 0. This function falls back to [SpecPackages.OPLUS_KEYBOARD]
     * and its known variants when the current package has no match.
     */
    /**
     * Smooth-corner bridge for `com.oplus.graphics.OplusOutline`.
     *
     * Resolves the class and method once and caches the result, including a failed lookup.
     * `getOutline` runs on every layout and size change.
     */
    private class OplusOutlineBridge(
        private val ctor: java.lang.reflect.Constructor<*>,
        private val smoothMethod: java.lang.reflect.Method
    ) {
        fun apply(outline: Outline, view: View, cornerRadiusPx: Float): Boolean = runCatching {
            val inst = ctor.newInstance(outline)
            if (smoothMethod.parameterTypes.size == 6) {
                smoothMethod.invoke(inst, 0, 0, view.width, view.height, cornerRadiusPx, 0.6f)
            } else {
                smoothMethod.invoke(inst, 0, 0, view.width, view.height, cornerRadiusPx)
            }
            true
        }.getOrDefault(false)
    }

    @Volatile
    private var outlineBridgeResolved = false

    @Volatile
    private var outlineBridge: OplusOutlineBridge? = null

    /** Applies smooth corners; falls back to a standard round rect when the host class is absent. */
    private fun applySmoothOutline(view: View, outline: Outline, cornerRadiusPx: Float) {
        val bridge = if (outlineBridgeResolved) {
            outlineBridge
        } else {
            synchronized(this) {
                if (!outlineBridgeResolved) {
                    outlineBridge = runCatching {
                        val cls = Class.forName("com.oplus.graphics.OplusOutline")
                        val ctor = cls.getConstructor(Outline::class.java)
                        val m = cls.methods.firstOrNull {
                            it.name == "setSmoothRoundRect" && it.parameterTypes.size >= 5
                        }
                        if (m == null) null else OplusOutlineBridge(ctor, m)
                    }.getOrNull()
                    outlineBridgeResolved = true
                }
                outlineBridge
            }
        }
        if (bridge?.apply(outline, view, cornerRadiusPx) == true) return
        outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
    }

    /** Host resource ID cache: `defType|name|packageName -> id`. Caches 0 as well. */
    private val hostResIdCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun resolveHostResId(context: Context, name: String, defType: String): Int {
        val cacheKey = defType + "|" + name + "|" + context.packageName
        hostResIdCache[cacheKey]?.let { return it }
        val resolved = resolveHostResIdUncached(context, name, defType)
        hostResIdCache[cacheKey] = resolved
        return resolved
    }

    private fun resolveHostResIdUncached(context: Context, name: String, defType: String): Int {
        val id = context.resources.getIdentifier(name, defType, context.packageName)
        if (id != 0) return id
        if (context.packageName != SpecPackages.OPLUS_KEYBOARD) {
            val oplusId = context.resources.getIdentifier(name, defType, SpecPackages.OPLUS_KEYBOARD)
            if (oplusId != 0) return oplusId
        }
        for (pkg in SpecPackages.OPLUS_KEYBOARD_VARIANTS) {
            if (pkg != context.packageName && pkg != SpecPackages.OPLUS_KEYBOARD) {
                val fallbackId = context.resources.getIdentifier(name, defType, pkg)
                if (fallbackId != 0) return fallbackId
            }
        }
        return 0
    }

    /**
     * Reads a host dimen by name. Returns [fallbackDp] converted to px when the resource is missing.
     *
     * Does not throw.
     */
    fun resolveHostDimen(context: Context, name: String, fallbackDp: Int): Int = runCatching {
        val id = resolveHostResId(context, name, "dimen")
        if (id == 0) dp(fallbackDp, context) else context.resources.getDimensionPixelSize(id)
    }.getOrDefault(dp(fallbackDp, context))

    /**
     * Reads a host theme color attr by name, for example `couiColorSecondNeutral`.
     *
     * Returns [fallback] when the attr is missing. Does not throw.
     */
    fun resolveHostAttrColor(context: Context, attrName: String, fallback: Int): Int = runCatching {
        val attrId = resolveHostResId(context, attrName, "attr")
        if (attrId == 0) {
            fallback
        } else {
            val ta = context.theme.obtainStyledAttributes(intArrayOf(attrId))
            val c = ta.getColor(0, fallback)
            ta.recycle()
            c
        }
    }.getOrDefault(fallback)

    fun isNightMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // System dark-mode level, written to Settings.Global by system_server
    // (com.android.server.wm.OplusActivityTaskManagerService#updateExtraConfigurationForUser).
    private const val KEY_DARK_MODE_BACKGROUND_MAX_L = "DarkMode_BackgroundMaxL"
    private const val KEY_DARK_MODE_DIALOG_BG_MAX_L = "DarkMode_DialogBgMaxL"

    /**
     * Reads the system dark-mode level from `Settings.Global`.
     *
     * Value is the CIELAB L\* ceiling for dark backgrounds. Defaults to `0f` when the key is missing.
     */
    private fun readDarkModeLevel(context: Context, key: String): Float = runCatching {
        Settings.Global.getFloat(context.contentResolver, key, 0f)
    }.getOrDefault(0f)

    /**
     * Dark-background conversion of the host framework
     * (`com.android.server.wm.QuickStartUtils#makeColorBackground`, same math as
     * COUI `COUIDarkModeHelper#makeDark`).
     *
     * - Inverts only when L\* > 50; darker colors return unchanged (idempotent).
     * - `maxL` maps the inverted L\* into `maxL..50`; `maxL < 0` gives a plain inversion.
     * - Keeps the original alpha.
     */
    private fun makeColorBackground(color: Int, maxL: Float): Int {
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(color, lab)
        var invertedL = 100.0 - lab[0]
        if (invertedL >= lab[0]) return color
        if (maxL >= 0f) invertedL = maxL + (invertedL / 50.0) * (50.0 - maxL)
        lab[0] = invertedL
        val out = ColorUtils.LABToColor(lab[0], lab[1], lab[2])
        return Color.argb(Color.alpha(color), Color.red(out), Color.green(out), Color.blue(out))
    }

    fun getSurfaceColor(context: Context): Int =
        if (isNightMode(context)) Color.parseColor("#1C1C1E") else Color.parseColor("#FFFFFF")

    /**
     * Page background color. Light mode uses host `?attr/couiColorBackgroundWithCard` (`#f0f1f2`).
     *
     * In night mode the attr night value is light `#ffffff`; the host reaches dark through
     * Force Dark (light theme plus framework inversion), and this module disables Force Dark
     * on the page (see [disableForceDarkRecursively]).
     *
     * Night mode therefore runs the framework conversion ([makeColorBackground]) on the attr
     * night value.
     */
    fun getBackgroundColor(context: Context): Int {
        val base = resolveHostAttrColor(context, "couiColorBackgroundWithCard", Color.parseColor("#F0F1F2"))
        if (!isNightMode(context)) return base
        return makeColorBackground(base, readDarkModeLevel(context, KEY_DARK_MODE_BACKGROUND_MAX_L))
    }

    /**
     * Card dialog background color. Light mode is `#FFFFFF`.
     *
     * Night mode uses the same framework conversion with `DarkMode_DialogBgMaxL`, which yields
     * `#404040` on this device and matches the night value of `coui_alert_dialog_background_tint`.
     */
    fun getDialogSurfaceColor(context: Context): Int {
        if (!isNightMode(context)) return Color.WHITE
        return makeColorBackground(Color.WHITE, readDarkModeLevel(context, KEY_DARK_MODE_DIALOG_BG_MAX_L))
    }

    /**
     * Minimal fallback page shown when the embedded page fails to build.
     *
     * Uses no host layout, no reflection and no module drawable: only a `FrameLayout`
     * with two `TextView` children.
     */
    fun createPageLoadFailedView(context: Context): View {
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(getBackgroundColor(context))
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            val p = dp(32, context)
            setPadding(p, p, p, p)
        }
        column.addView(TextView(context).apply {
            text = OplusWorkshopUiText.getPageLoadFailedTitle(context)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getPrimaryTextColor(context))
            gravity = Gravity.CENTER
        })
        column.addView(TextView(context).apply {
            text = OplusWorkshopUiText.getPageLoadFailedHint(context)
            textSize = 13f
            setTextColor(getSecondaryTextColor(context))
            gravity = Gravity.CENTER
            setPadding(0, dp(8, context), 0, 0)
        })
        root.addView(column)
        return root
    }

    fun getPrimaryTextColor(context: Context): Int =
        if (isNightMode(context)) Color.parseColor("#F2F2F7") else Color.parseColor("#1C1C1E")

    fun getSecondaryTextColor(context: Context): Int =
        if (isNightMode(context)) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70")

    fun getAccentColor(context: Context): Int =
        Color.parseColor("#007AFF")

    /**
     * Host accent color `?attr/couiColorPrimary` (COUI blue). Falls back to [getAccentColor].
     *
     * [getAccentColor] is the hardcoded iOS blue `#007AFF`.
     */
    fun stockAccentColor(context: Context): Int =
        resolveHostAttrColor(context, "couiColorPrimary", getAccentColor(context))

    fun getWarningBgColor(context: Context): Int =
        if (isNightMode(context)) Color.parseColor("#332800") else Color.parseColor("#FFF8E1")

    fun getWarningTextColor(context: Context): Int =
        if (isNightMode(context)) Color.parseColor("#FFD54F") else Color.parseColor("#E65100")

    /**
     * ColorOS stock frosted-glass header.
     * Holds a COUIToolbar, a status bar inset spacer, a translucent background
     * and a divider whose alpha follows the scroll offset.
     */
    class OfficialHeaderView(context: Context) : LinearLayout(context) {
        val toolbar: View
        val dividerView: View
        private val statusBarSpacer: View
        private val isNight = isNightMode(context)

        init {
            orientation = VERTICAL

            // Stock semi-translucent frosted glass acrylic background
            val defaultBgColor = if (isNight) {
                Color.argb(215, 20, 20, 22)
            } else {
                Color.argb(215, 246, 246, 248)
            }
            setBackgroundColor(defaultBgColor)

            val initialStatusHeight = getStatusBarHeight(context)
            statusBarSpacer = View(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, initialStatusHeight)
            }
            addView(statusBarSpacer)

            toolbar = createToolbarWidget(context)
            addView(toolbar)

            dividerView = View(context).apply {
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    dp(0.5f, context).toInt().coerceAtLeast(1)
                )
                val divColor = if (isNight) Color.argb(40, 255, 255, 255) else Color.argb(25, 0, 0, 0)
                setBackgroundColor(divColor)
                alpha = 0f // Invisible when at top, fades in as content scrolls underneath
            }
            addView(dividerView)
        }

        fun setStatusBarHeight(statusBarHeight: Int) {
            val lp = statusBarSpacer.layoutParams
            if (lp.height != statusBarHeight) {
                lp.height = statusBarHeight
                statusBarSpacer.layoutParams = lp
            }
        }

        fun updateScroll(scrollY: Int) {
            val threshold = dp(16, context)
            val progress = (scrollY.toFloat() / threshold).coerceIn(0f, 1f)
            dividerView.alpha = progress

            val baseAlpha = if (isNight) 195 else 205
            val maxAlpha = 245
            val currentAlpha = (baseAlpha + (maxAlpha - baseAlpha) * progress).toInt()
            val baseColor = if (isNight) Color.rgb(20, 20, 22) else Color.rgb(246, 246, 248)
            val colorWithAlpha = Color.argb(
                currentAlpha,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
            )
            setBackgroundColor(colorWithAlpha)
        }
    }

    private fun createToolbarWidget(context: Context): View {
        val minHeightId = resolveHostResId(context, "toolbar_min_height", "dimen")
            .takeIf { it != 0 }
            ?: resolveHostResId(context, "coui_action_bar_default_height", "dimen")
        val toolbarHeight = if (minHeightId != 0) context.resources.getDimensionPixelSize(minHeightId) else dp(56, context)

        val couiToolbar = runCatching {
            val clazz = context.classLoader.loadClass("com.coui.appcompat.toolbar.COUIToolbar")
            clazz.getConstructor(Context::class.java).newInstance(context) as View
        }.getOrNull()

        val toolbar = couiToolbar ?: runCatching {
            val fallbackClass = context.classLoader.loadClass("androidx.appcompat.widget.Toolbar")
            fallbackClass.getConstructor(Context::class.java).newInstance(context) as View
        }.getOrElse { View(context) }

        toolbar.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            toolbarHeight
        )
        toolbar.setBackgroundColor(Color.TRANSPARENT)

        if (couiToolbar != null) {
            runCatching {
                couiToolbar.javaClass.getMethod("setIsTitleCenterStyle", Boolean::class.javaPrimitiveType)
                    .invoke(couiToolbar, false)
            }
        } else {
            runCatching {
                toolbar.javaClass.getMethod("setContentInsetStartWithNavigation", Int::class.javaPrimitiveType)
                    .invoke(toolbar, dp(16, context))
            }
        }

        val titleColor = getPrimaryTextColor(context)
        runCatching {
            toolbar.javaClass.getMethod("setTitleTextColor", Int::class.javaPrimitiveType)
                .invoke(toolbar, titleColor)
        }

        val titleStyleId = resolveHostResId(context, "textAppearanceSecondTitle", "style")
        if (titleStyleId != 0) {
            runCatching {
                toolbar.javaClass.getMethod("setTitleTextAppearance", Context::class.java, Int::class.javaPrimitiveType)
                    .invoke(toolbar, context, titleStyleId)
            }
        }

        return toolbar
    }

    /**
     * Reflectively configures a native COUIToolbar or Toolbar instance safely across ClassLoaders.
     */
    fun configureToolbar(
        toolbar: View,
        context: Context,
        title: String,
        onBackClick: () -> Unit
    ) {
        runCatching {
            toolbar.javaClass.getMethod("setTitle", CharSequence::class.java).invoke(toolbar, title)
        }

        val backArrowId = resolveHostResId(context, "coui_back_arrow", "drawable")
            .takeIf { it != 0 }
            ?: resolveHostResId(context, "coui_toolbar_back_arrow", "drawable")
            .takeIf { it != 0 }
            ?: resolveHostResId(context, "abc_ic_ab_back_material", "drawable")

        if (backArrowId != 0) {
            val navSet = runCatching {
                toolbar.javaClass.getMethod("setNavigationIcon", Int::class.javaPrimitiveType)
                    .invoke(toolbar, backArrowId)
                true
            }.getOrDefault(false)

            if (!navSet) {
                runCatching {
                    val drawable = context.getDrawable(backArrowId)
                    toolbar.javaClass.getMethod("setNavigationIcon", Drawable::class.java)
                        .invoke(toolbar, drawable)
                }
            }
        }

        runCatching {
            toolbar.javaClass.getMethod("setNavigationOnClickListener", View.OnClickListener::class.java)
                .invoke(toolbar, View.OnClickListener { onBackClick() })
        }

        runCatching {
            toolbar.javaClass.getMethod("setIsTitleCenterStyle", Boolean::class.javaPrimitiveType)
                .invoke(toolbar, false)
        }
        val titleColor = getPrimaryTextColor(context)
        runCatching {
            toolbar.javaClass.getMethod("setTitleTextColor", Int::class.javaPrimitiveType)
                .invoke(toolbar, titleColor)
        }
        val titleStyleId = resolveHostResId(context, "textAppearanceSecondTitle", "style")
        if (titleStyleId != 0) {
            runCatching {
                toolbar.javaClass.getMethod("setTitleTextAppearance", Context::class.java, Int::class.javaPrimitiveType)
                    .invoke(toolbar, context, titleStyleId)
            }
        }
    }

    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else dp(24, context)
    }

    /**
     * Installs the keyboard preview button on the right side of the toolbar.
     *
     * Behavior:
     * - Shows the **outline** icon when the IME is hidden.
     * - Click with the IME hidden: focuses the hidden input, calls `showSoftInput`, switches to **filled**.
     * - Click with the IME shown: calls `hideSoftInput`, switches back to outline.
     * - IME dismissal through system back or app switch is detected via **WindowInsets(ime)**.
     *   The host window uses `SOFT_INPUT_ADJUST_NOTHING`, where `onGlobalLayout` detection fails.
     *
     * Placement: the button is a toolbar ActionMenu item (`setIcon` + `SHOW_AS_ACTION_ALWAYS`).
     * Verified against the decompiled COUIToolbar:
     * - A custom child view with right gravity is not laid out by `COUIToolbar.onLayout`, which
     *   positions only `mTypeTextButton`, so the view stays at (0,0) over the back button.
     * - `setActionView` with `SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW` sets `mExpandedActionView` and
     *   `setChildVisibilityForExpandedActionView(true)` hides every child except the menu view.
     * - A plain icon menu item is right-aligned by `COUIActionMenuView` and does not collapse.
     *
     * The hidden input uses `maxLength = -1` with a `LengthFilter` whose `keep` is <= 0, so it
     * always returns an empty string. It serves only as the IME focus anchor and never shows text.
     *
     * @param anchorHost Container for the hidden input; null uses the toolbar.
     * @param onClick Optional click callback, invoked after the internal toggle.
     */
    fun installKeyboardPreviewButton(
        toolbar: View,
        context: Context,
        desc: String,
        anchorHost: ViewGroup? = null,
        onClick: (() -> Unit)? = null
    ) {
        // Loads icons from module resources; context.resources belongs to the host process.
        val moduleRes = OplusWorkshopUiText.getModuleResources(context)
        val outlineDrawable = moduleRes?.getDrawable(R.drawable.ic_toolbar_preview_keyboard_outline, null)
        val filledDrawable = moduleRes?.getDrawable(R.drawable.ic_toolbar_preview_keyboard_filled, null)
        val tint = getPrimaryTextColor(context)
        outlineDrawable?.setTint(tint)
        filledDrawable?.setTint(tint)

        // Hidden input as the IME anchor: 1px, alpha 0, attached to the page root container.
        val hiddenEdit = android.widget.EditText(context).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            alpha = 0f
            isFocusable = true
            isFocusableInTouchMode = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(-1))
            background = null
            setPadding(0, 0, 0, 0)
            setTextColor(Color.TRANSPARENT)
            isCursorVisible = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val anchor = anchorHost ?: (toolbar as? ViewGroup)
        runCatching { anchor?.addView(hiddenEdit) }

        // IME visibility state; the menu item carries the icon.
        var imeShown = false
        fun imeManager() = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager

        // Applies the icon to the menu item and the fallback ImageView.
        var fallbackIcon: android.widget.ImageView? = null
        fun applyPreviewIcon(d: Drawable?) {
            setPreviewMenuIcon(toolbar, d)
            fallbackIcon?.setImageDrawable(d)
        }

        // Installs through a plain menu icon item in the right-aligned slot.
        val menuInstalled = runCatching {
            // Skips installation when the icon is missing.
            if (outlineDrawable == null) return@runCatching false
            val toolbarCls = toolbar.javaClass
            val menu = toolbarCls.getMethod("getMenu").invoke(toolbar) as? android.view.Menu
                ?: return@runCatching false
            // Removes a previous item before adding.
            runCatching { menu.removeItem(ID_PREVIEW_BUTTON) }
            val item: android.view.MenuItem = menu.add(
                android.view.Menu.NONE, ID_PREVIEW_BUTTON, android.view.Menu.NONE, desc
            )
            item.setIcon(outlineDrawable)
            item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            item.isVisible = true
            item.setOnMenuItemClickListener {
                val imm = imeManager()
                if (!imeShown) {
                    hiddenEdit.setText("")
                    hiddenEdit.requestFocus()
                    imm?.showSoftInput(hiddenEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    imeShown = true
                    applyPreviewIcon(filledDrawable)
                } else {
                    imm?.hideSoftInputFromWindow(hiddenEdit.windowToken, 0)
                    hiddenEdit.clearFocus()
                    imeShown = false
                    applyPreviewIcon(outlineDrawable)
                }
                onClick?.invoke()
                true
            }
            true
        }.getOrDefault(false)

        if (!menuInstalled && outlineDrawable != null) {
            // Fallback for hosts without a menu: custom child view with right gravity.
            runCatching {
                val hitSize = dp(48, context)
                val iconSize = dp(24, context)
                val holder = FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(hitSize, hitSize)
                    isClickable = true
                    isFocusable = true
                    background = createSelectableItemBackground(context)
                    contentDescription = desc
                }
                val iv = android.widget.ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
                    setImageDrawable(outlineDrawable)
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
                holder.addView(iv)
                fallbackIcon = iv
                holder.setOnClickListener {
                    val imm = imeManager()
                    if (!imeShown) {
                        hiddenEdit.setText("")
                        hiddenEdit.requestFocus()
                        imm?.showSoftInput(hiddenEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                        imeShown = true
                        applyPreviewIcon(filledDrawable)
                    } else {
                        imm?.hideSoftInputFromWindow(hiddenEdit.windowToken, 0)
                        hiddenEdit.clearFocus()
                        imeShown = false
                        applyPreviewIcon(outlineDrawable)
                    }
                    onClick?.invoke()
                }
                (toolbar as? ViewGroup)?.addView(holder)
            }
        }

        // IME dismissal detection through WindowInsets.
        //
        // OnGlobalLayoutListener + getWindowVisibleDisplayFrame does not work here: the host
        // window uses SOFT_INPUT_ADJUST_NOTHING (see OplusInputSettingsHook.kt:211), so IME
        // show/hide does not change the window layout and onGlobalLayout never fires.
        // IME insets are dispatched regardless of softInputMode.
        //
        // The listener is attached to the page root container (anchorHost) instead of decorView,
        // which keeps the host insets listener intact.
        val insetsTarget: View? = anchorHost
            ?: runCatching { (context as android.app.Activity).window.decorView }.getOrNull()
        if (insetsTarget != null) {
            ViewCompat.setOnApplyWindowInsetsListener(insetsTarget) { _, insets ->
                val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                if (imeVisible && !imeShown) {
                    // IME shown, including system-initiated: updates the icon only.
                    imeShown = true
                    applyPreviewIcon(filledDrawable)
                } else if (!imeVisible && imeShown) {
                    // IME hidden by back, app switch or focus loss: switches back to outline.
                    imeShown = false
                    hiddenEdit.setText("")
                    applyPreviewIcon(outlineDrawable)
                }
                insets
            }
            ViewCompat.requestApplyInsets(insetsTarget)
        }
    }

    /** Updates the icon of the installed menu item. */
    private fun setPreviewMenuIcon(toolbar: View, drawable: Drawable?) {
        runCatching {
            val menu = toolbar.javaClass.getMethod("getMenu").invoke(toolbar) as? android.view.Menu
            menu?.findItem(ID_PREVIEW_BUTTON)?.setIcon(drawable)
        }
    }

    private fun createSelectableItemBackground(context: Context): Drawable? {
        val id = context.resources.getIdentifier(
            "selectableItemBackgroundBorderless", "attr", "android"
        )
        return if (id != 0) {
            val tv = TypedValue()
            if (context.theme.resolveAttribute(id, tv, true)) context.getDrawable(tv.resourceId) else null
        } else null
    }

    /**
     * Builds the ColorOS Appbar page layout: a wrapping CoordinatorLayout, COUIAppBarLayout
     * with gradient blur, COUIToolbar with coui_back_arrow and a FrameLayout list_container.
     *
     * Adds contentScrollView to the list_container. Falls back to a FrameLayout with
     * [createOfficialHeader] when the host layout is unavailable.
     */
    fun createOfficialPageRoot(
        context: Context,
        title: String,
        contentScrollView: View,
        onBackClick: () -> Unit,
        showKeyboardPreview: Boolean = false,
        onKeyboardPreviewClick: (() -> Unit)? = null
    ): View {
        val layoutId = resolveHostResId(
            context,
            "coui_preference_list_with_appbar_fragment",
            "layout"
        )

        if (layoutId != 0) {
            val root = runCatching {
                LayoutInflater.from(context).inflate(layoutId, null)
            }.getOrNull()

            if (root != null) {
                // 1. Configure COUIToolbar
                val toolbarId = resolveHostResId(context, "toolbar", "id")
                val toolbar = if (toolbarId != 0) root.findViewById<View>(toolbarId) else null
                if (toolbar != null) {
                    configureToolbar(toolbar, context, title, onBackClick)
                    if (showKeyboardPreview) {
                        installKeyboardPreviewButton(
                            toolbar, context,
                            OplusWorkshopUiText.getToolbarPreviewKeyboardDesc(context),
                            anchorHost = root as? ViewGroup,
                            onClick = onKeyboardPreviewClick
                        )
                    }
                }

                // 2. Configure COUIAppBarLayout & Insets
                val appbarId = resolveHostResId(context, "appbar_layout", "id")
                val appbar = if (appbarId != 0) root.findViewById<View>(appbarId) else null
                if (appbar != null) {
                    val statusBarHeight = getStatusBarHeight(context)
                    appbar.setPadding(appbar.paddingStart, statusBarHeight, appbar.paddingEnd, appbar.paddingBottom)
                    runCatching { (appbar as? ViewGroup)?.touchscreenBlocksFocus = false }
                    runCatching {
                        appbar.javaClass.getMethod("setGradientBlurEnabled", Boolean::class.javaPrimitiveType)
                            .invoke(appbar, true)
                    }
                    // The host appbar background is `@drawable/coui_window_bg_with_card`, a solid
                    // `?attr/couiColorBackgroundWithCard` whose night value is light `#ffffff`
                    // (the host reaches dark through Force Dark, disabled on this page; see
                    // [disableForceDarkRecursively]). Sets the page background color instead.
                    appbar.setBackgroundColor(getBackgroundColor(context))
                }

                // 3. Attach scroll content to list_container
                val listContainer = root.findViewById<ViewGroup>(android.R.id.list_container)
                if (listContainer != null) {
                    listContainer.addView(
                        contentScrollView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    return root
                }
            }
        }

        // Fallback: wrapped FrameLayout with official header
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(getBackgroundColor(context))
        }
        val header = createOfficialHeader(context, title, onBackClick)
        if (showKeyboardPreview && header is OfficialHeaderView) {
            installKeyboardPreviewButton(
                header.toolbar, context,
                OplusWorkshopUiText.getToolbarPreviewKeyboardDesc(context),
                anchorHost = container,
                onClick = onKeyboardPreviewClick
            )
        }
        val statusBarHeight = getStatusBarHeight(context)
        applyHeaderStatusBarInset(header, statusBarHeight)
        val headerHeight = getHeaderContentHeight(context)
        contentScrollView.setPadding(0, statusBarHeight + headerHeight, 0, dp(36, context))
        container.addView(contentScrollView)
        container.addView(header)
        return container
    }

    /**
     * Builds the ColorOS top bar used by InputSettingsFragment:
     * - com.coui.appcompat.toolbar.COUIToolbar
     * - coui_back_arrow navigation icon with touch feedback
     * - translucent background and a scroll-linked divider
     */
    fun createOfficialHeader(
        context: Context,
        title: String,
        onBackClick: () -> Unit
    ): View {
        val header = OfficialHeaderView(context)
        configureToolbar(header.toolbar, context, title, onBackClick)
        return header
    }

    fun getHeaderContentHeight(context: Context): Int {
        val minHeightId = resolveHostResId(context, "toolbar_min_height", "dimen")
            .takeIf { it != 0 }
            ?: resolveHostResId(context, "coui_action_bar_default_height", "dimen")
        return if (minHeightId != 0) context.resources.getDimensionPixelSize(minHeightId) else dp(56, context)
    }

    fun applyHeaderStatusBarInset(header: View, statusBarTop: Int) {
        if (header is OfficialHeaderView) {
            header.setStatusBarHeight(statusBarTop)
        } else {
            header.setPadding(0, statusBarTop, 0, 0)
        }
    }

    fun updateHeaderScrollState(header: View, scrollY: Int) {
        if (header is OfficialHeaderView) {
            header.updateScroll(scrollY)
        }
    }

    // Between cards no divider is drawn; grouping uses whitespace only.
    // Card vertical margins of 6dp plus category title margins of 8dp give 14dp of whitespace.

    /** Host `?attr/couiColorLabelPrimary`: 90% black in light mode, 90% white in night mode. */
    private fun stockLabelPrimaryColor(context: Context): Int = resolveHostAttrColor(
        context,
        "couiColorLabelPrimary",
        if (isNightMode(context)) Color.parseColor("#E6FFFFFF") else Color.parseColor("#E6000000")
    )

    /**
     * Stock row title style: 16sp, `sans-serif-medium`, `?attr/couiColorLabelPrimary`.
     *
     * Matches host `COUIPreferenceTitleStyle` → `TextAppearance.COUI.List.Title`
     * → `couiTextAppearanceHeadline6`.
     */
    fun applyStockTitleStyle(tv: TextView, context: Context) {
        tv.textSize = 16f
        tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        tv.setTextColor(stockLabelPrimaryColor(context))
    }

    /**
     * Stock description/label style: 12sp, `sans-serif-medium`, `?attr/couiColorLabelPrimary`.
     *
     * Matches host `couiTextDescription` (`tv_type_start` / `tv_type_end`).
     */
    fun applyStockDescriptionStyle(tv: TextView, context: Context) {
        tv.textSize = 12f
        tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        tv.setTextColor(stockLabelPrimaryColor(context))
    }

    /**
     * Stock row summary style: 13sp, `?attr/couiColorSecondNeutral` (54% black or white).
     *
     * Matches host `COUIPreferenceSummaryStyle` → `couiTextAppearanceArticleBody`.
     */
    fun applyStockSummaryStyle(tv: TextView, context: Context) {
        tv.textSize = 13f
        tv.setTextColor(
            resolveHostAttrColor(
                context,
                "couiColorSecondNeutral",
                if (isNightMode(context)) Color.parseColor("#8AFFFFFF") else Color.parseColor("#8A000000")
            )
        )
    }

    /** Stock row content vertical padding, from `support_preference_text_content_padding_top/bottom`. */
    fun stockRowVerticalPadding(context: Context): Int =
        resolveHostDimen(context, "support_preference_text_content_padding_top", 10)

    /** Stock row minimum height, from `support_preference_min_height`. */
    fun stockRowMinHeight(context: Context): Int =
        resolveHostDimen(context, "support_preference_min_height", 48)

    /**
     * Bottom spacer for the page content.
     *
     * Height is the system navigation bar height (`android:navigation_bar_height`) plus 16dp,
     * or 40dp when the bar height is unavailable. Placed at the end of the scroll content.
     */
    fun createBottomSpacer(context: Context): View {
        val navBarResId = runCatching {
            context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        }.getOrDefault(0)
        val navBarHeight = if (navBarResId != 0) {
            runCatching { context.resources.getDimensionPixelSize(navBarResId) }.getOrDefault(0)
        } else {
            0
        }
        val height = if (navBarHeight > 0) navBarHeight + dp(16, context) else dp(40, context)
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        }
    }

    /**
     * Section title that matches `Widget.COUI.List.Category.Title` in host
     * `coui_preference_category_layout.xml`.
     *
     * Does not inflate the host layout: its root `COUICustomLinearLayoutForPreference` reads
     * `getChildAt(0/1/2)` in onMeasure without null checks and is safe only with exactly
     * three children. This function builds a single TextView and reads all values and colors
     * from host resources by name.
     *
     * Stock spec:
     * - 12sp, weight `sans-serif-medium`, textStyle normal
     * - color `?attr/couiColorSecondNeutral`, 54% black or white
     * - indent 16dp start, 8dp end, 8dp top/bottom margins, minHeight 16dp
     * - gravity CENTER_VERTICAL, textAlignment VIEW_START
     */
    fun createCategoryTitle(context: Context, title: String): TextView {
        val textColor = resolveHostAttrColor(
            context,
            "couiColorSecondNeutral",
            if (isNightMode(context)) Color.parseColor("#8AFFFFFF") else Color.parseColor("#8A000000")
        )
        val titleMarginStart = resolveHostDimen(
            context, "support_preference_category_layout_title_margin_start_small", 16
        )
        val titleMarginEnd = resolveHostDimen(
            context, "coui_category_title_pading_end_with_reddot_default", 8
        )
        val titleMarginTop = resolveHostDimen(context, "coui_common_category_text_padding_top", 8)
        val titleMarginBottom = resolveHostDimen(context, "coui_common_category_text_padding_bottom", 8)
        val titleMinHeight = resolveHostDimen(context, "coui_preference_category_text_height", 16)

        return TextView(context).apply {
            text = title
            textSize = 12f
            // Stock couiTextAppearanceSmallButton: fontFamily=sans-serif-medium, textStyle=normal
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(textColor)
            gravity = Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            minimumHeight = titleMinHeight
            isAllCaps = false
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = titleMarginStart
                marginEnd = titleMarginEnd
                topMargin = titleMarginTop
                bottomMargin = titleMarginBottom
            }
            layoutParams = lp
        }
    }

    /**
     * Creates native COUIToolbar or fallback Toolbar safely across ClassLoaders.
     */
    fun createToolbar(context: Context): View {
        val toolbar = runCatching {
            val clazz = context.classLoader.loadClass("com.coui.appcompat.toolbar.COUIToolbar")
            clazz.getConstructor(Context::class.java).newInstance(context) as View
        }.recoverCatching {
            val fallbackClass = context.classLoader.loadClass("androidx.appcompat.widget.Toolbar")
            fallbackClass.getConstructor(Context::class.java).newInstance(context) as View
        }.getOrElse { View(context) }

        toolbar.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52, context)
        )
        toolbar.setBackgroundColor(getBackgroundColor(context))
        runCatching {
            toolbar.javaClass.getMethod("setTitleTextColor", Int::class.javaPrimitiveType)
                .invoke(toolbar, getPrimaryTextColor(context))
        }
        return toolbar
    }

    /**
     * Creates native COUISwitch or fallback SwitchCompat.
     */
    fun createSwitch(context: Context): CompoundButton {
        return runCatching {
            val clazz = context.classLoader.loadClass("com.coui.appcompat.couiswitch.COUISwitch")
            clazz.getConstructor(Context::class.java).newInstance(context) as CompoundButton
        }.getOrElse { SwitchCompat(context) }
    }

    /**
     * Creates a standard [SeekBar] tinted with the module accent color.
     *
     * `COUISeekBar` is not usable here. Its chain is
     * `COUISeekBar → COUISeekBarAdvanced → COUISeekBarCore → android.widget.AbsSeekBar`,
     * while `android.widget.SeekBar` is a subclass of `AbsSeekBar`, so `COUISeekBar` is not a
     * `SeekBar` and a cast throws `ClassCastException`.
     *
     * Native COUI sliders are provided by [createSectionSeekBarRow] and
     * [createContinuousSeekBarRow], which hold the bar as `View` and call
     * `setMax`/`setProgress`/`setOnSeekBarChangeListener` via reflection.
     */
    fun createSeekBar(context: Context): SeekBar {
        val seekBar = SeekBar(context)
        val accent = getAccentColor(context)
        seekBar.progressTintList = ColorStateList.valueOf(accent)
        seekBar.thumbTintList = ColorStateList.valueOf(accent)
        return seekBar
    }

    /**
     * Creates a ColorOS-style Segmented Pill Tab component.
     */
    fun createSegmentedTab(
        context: Context,
        titles: List<String>,
        initialIndex: Int = 0,
        onTabSelected: (Int) -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val hPad = dp(16, context)
            val vPad = dp(4, context)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40, context)
            ).apply {
                setMargins(hPad, vPad, hPad, vPad)
            }
            layoutParams = lp

            val pillBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20, context).toFloat()
                setColor(if (isNightMode(context)) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA"))
            }
            background = pillBg
            val p = dp(3, context)
            setPadding(p, p, p, p)
        }

        val tabViews = ArrayList<TextView>()

        titles.forEachIndexed { index, title ->
            val isSelected = index == initialIndex
            val tv = TextView(context).apply {
                text = title
                textSize = 14f
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                layoutParams = lp

                if (isSelected) {
                    val selBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(17, context).toFloat()
                        setColor(if (isNightMode(context)) Color.parseColor("#3A3A3C") else Color.parseColor("#FFFFFF"))
                    }
                    background = selBg
                    setTextColor(getPrimaryTextColor(context))
                    typeface = Typeface.DEFAULT_BOLD
                } else {
                    background = null
                    setTextColor(getSecondaryTextColor(context))
                    typeface = Typeface.DEFAULT
                }

                setOnClickListener {
                    tabViews.forEachIndexed { i, btn ->
                        if (i == index) {
                            val selBg = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(17, context).toFloat()
                                setColor(if (isNightMode(context)) Color.parseColor("#3A3A3C") else Color.parseColor("#FFFFFF"))
                            }
                            btn.background = selBg
                            btn.setTextColor(getPrimaryTextColor(context))
                            btn.typeface = Typeface.DEFAULT_BOLD
                        } else {
                            btn.background = null
                            btn.setTextColor(getSecondaryTextColor(context))
                            btn.typeface = Typeface.DEFAULT
                        }
                    }
                    onTabSelected(index)
                }
            }
            tabViews.add(tv)
            container.addView(tv)
        }

        return container
    }

    /**
     * Creates native COUIButton or fallback AppCompatButton.
     */
    fun createButton(context: Context): TextView {
        return runCatching {
            val clazz = context.classLoader.loadClass("com.coui.appcompat.button.COUIButton")
            clazz.getConstructor(Context::class.java).newInstance(context) as TextView
        }.getOrElse { AppCompatButton(context) }
    }

    /**
     * Creates a ColorOS rounded card container.
     * Sets ViewOutlineProvider.BACKGROUND and clipToOutline = true; child ripples and
     * contents clip to the rounded corners.
     */
    fun createCardContainer(context: Context): LinearLayout {
        // Corner radius from host ?attr/couiRoundCornerXL → @dimen/coui_round_corner_xl.
        val cardCornerRadius = resolveHostDimen(context, "coui_round_corner_xl", 20).toFloat()
        // Background from host ?attr/couiColorCardBackground: #ffffff light, #1affffff night.
        val cardColor = resolveHostAttrColor(context, "couiColorCardBackground", getSurfaceColor(context))
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cardCornerRadius
            setColor(cardColor)
            // No stroke: stock COUICardListSelectedItemLayout.init() only calls setBackground
            // with a self-drawn drawable.
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = drawable
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setPadding(0, 0, 0, 0)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(16, context), dp(6, context), dp(16, context), dp(6, context))
            }
            layoutParams = lp
        }
    }

    /**
     * Creates a Warning / Status Banner Card with action button.
     */
    fun createBannerCard(
        context: Context,
        iconEmoji: String,
        message: String,
        buttonText: String? = null,
        onButtonClick: (() -> Unit)? = null
    ): LinearLayout {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14, context).toFloat()
            setColor(getWarningBgColor(context))
            setStroke(dp(1, context), Color.parseColor("#FFE082"))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            val p = dp(14, context)
            setPadding(p, p, p, p)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(16, context), dp(8, context), dp(16, context), dp(6, context))
            }
            layoutParams = lp
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconTv = TextView(context).apply {
            text = iconEmoji
            textSize = 18f
            setPadding(0, 0, dp(8, context), 0)
        }
        row.addView(iconTv)

        val textTv = TextView(context).apply {
            text = message
            textSize = 13f
            setTextColor(getWarningTextColor(context))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(textTv)
        container.addView(row)

        if (buttonText != null && onButtonClick != null) {
            val btn = createButton(context).apply {
                text = buttonText
                textSize = 13f
                isAllCaps = false
                val btnBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(18, context).toFloat()
                    setColor(Color.parseColor("#FF9800"))
                }
                background = btnBg
                setTextColor(Color.WHITE)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(36, context)
                ).apply {
                    topMargin = dp(10, context)
                }
                layoutParams = lp
                gravity = Gravity.CENTER
                setOnClickListener { onButtonClick() }
            }
            container.addView(btn)
        }

        return container
    }

    /**
     * Creates a native ColorOS subtle horizontal divider between items inside a Card.
     * Matches preference_divider_margin_horizontal / preference_divider_height specs.
     */
    /**
     * In-card divider between rows.
     *
     * Color comes from host `?attr/couiColorDivider`: `#1f000000` in light mode,
     * `#33ffffff` in night mode.
     *
     * Requires `obtainStyledAttributes(...).getColor(...)`, i.e. [resolveHostAttrColor].
     * `theme.resolveAttribute(...).data` returns a resource ID (`coui_color_divider`) for
     * `TYPE_REFERENCE`, and reading it as ARGB yields a 50% transparent near-black color.
     */
    fun createDivider(context: Context): View {
        val dividerColor = resolveHostAttrColor(
            context,
            "couiColorDivider",
            if (isNightMode(context)) Color.argb(0x33, 0xFF, 0xFF, 0xFF) else Color.argb(0x1F, 0x00, 0x00, 0x00)
        )

        return View(context).apply {
            setBackgroundColor(dividerColor)
            val hMargin = dp(16, context)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(0.8f, context).toInt().coerceAtLeast(1)
            ).apply {
                setMargins(hMargin, 0, hMargin, 0)
            }
            layoutParams = lp
        }
    }

    /** Creates the selectable item ripple drawable. */
    fun getRippleDrawable(context: Context): Drawable {
        val isNight = isNightMode(context)
        val rippleColor = if (isNight) {
            Color.argb(0x20, 0xFF, 0xFF, 0xFF)
        } else {
            Color.argb(0x15, 0x00, 0x00, 0x00)
        }
        val mask = ColorDrawable(Color.WHITE)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }

    /**
     * Applies the stock press ripple to view, with [getRippleDrawable] as fallback.
     *
     * Calls host static method
     * `com.coui.appcompat.rippleutil.COUIRippleDrawableUtil.setPressRippleDrawable(view, radius, false)`,
     * the same path as host `COUIPreferenceCategory`. That method builds
     * `COUIPressRippleDrawable extends RippleDrawable` with:
     * - ripple color `?attr/couiColorPressBackground` (SDK >= 34) or `?attr/couiColorRipplePressBackground`
     * - transparent content
     * - mask `COUIPressMaskDrawable(radius)`
     *
     * Sets the fallback ripple when any step fails.
     *
     * @param radiusDp Mask corner radius; rows inside a card are clipped by the card outline.
     * @return true when the stock ripple was applied.
     */
    fun applyStockPressRipple(view: View, radiusDp: Int = 0): Boolean {
        val ok = runCatching {
            val util = view.context.classLoader
                .loadClass("com.coui.appcompat.rippleutil.COUIRippleDrawableUtil")
            util.getMethod(
                "setPressRippleDrawable",
                View::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ).invoke(null, view, radiusDp, false)
            true
        }.getOrDefault(false)
        if (!ok) {
            view.background = getRippleDrawable(view.context)
        }
        return ok
    }

    /**
     * Builds a switch setting row with a native COUISwitch.
     * - The row handles clicks and shows a clipped ripple.
     * - The switch is not clickable, so every touch reaches the row.
     * - Uses stock ColorOS dimensions and typography.
     */
    fun createSwitchRow(
        context: Context,
        title: String,
        summary: String? = null,
        initialChecked: Boolean,
        enabled: Boolean = true,
        onCheckedChange: (Boolean) -> Unit
    ): LinearLayout {
        val switchWidget = createSwitch(context).apply {
            isChecked = initialChecked
            isEnabled = enabled
            isClickable = false
            isFocusable = false
        }

        // Re-entry guard is object-level, keyed by weak CompoundButton reference, and shared
        // with setSwitchRowChecked.
        switchWidget.setOnCheckedChangeListener { _, isChecked ->
            if (internalChangeGuard[switchWidget] != true) {
                onCheckedChange(isChecked)
            }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val vPad = stockRowVerticalPadding(context)
            val hPad = dp(16, context)
            setPadding(hPad, vPad, hPad, vPad)
            minimumHeight = stockRowMinHeight(context)
            isEnabled = enabled
            isClickable = enabled
            isFocusable = enabled
            if (enabled) {
                applyStockPressRipple(this)
            } else {
                alpha = 0.5f
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (enabled) {
                setOnClickListener {
                    val newState = !switchWidget.isChecked
                    internalChangeGuard[switchWidget] = true
                    switchWidget.isChecked = newState
                    internalChangeGuard.remove(switchWidget)
                    onCheckedChange(newState)
                }
            }
        }

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, dp(12, context), 0)
        }

        val titleTv = TextView(context).apply {
            text = title
            applyStockTitleStyle(this, context)
        }
        textLayout.addView(titleTv)

        if (summary != null) {
            val summaryTv = TextView(context).apply {
                text = summary
                applyStockSummaryStyle(this, context)
                setPadding(0, dp(2, context), 0, 0)
            }
            textLayout.addView(summaryTv)
        }
        row.addView(textLayout)
        row.addView(switchWidget)
        row.tag = switchWidget

        return row
    }

    fun setSwitchRowChecked(row: View, checked: Boolean) {
        (row.tag as? CompoundButton)?.let { switch ->
            if (switch.isChecked != checked) {
                // Shares the guard with the row click path; programmatic changes skip onCheckedChange.
                internalChangeGuard[switch] = true
                try {
                    switch.isChecked = checked
                } finally {
                    internalChangeGuard.remove(switch)
                }
            }
        }
    }

    /** Re-entry guard for programmatic switch updates, keyed by weak view reference. */
    private val internalChangeGuard = java.util.Collections.synchronizedMap(
        java.util.WeakHashMap<android.widget.CompoundButton, Boolean>()
    )

    class ClickableRowHandle(
        val row: LinearLayout,
        val titleView: TextView,
        val summaryView: TextView?
    ) {
        fun setSummary(summary: String?) {
            if (summary != null) {
                summaryView?.text = summary
                summaryView?.visibility = View.VISIBLE
            } else {
                summaryView?.visibility = View.GONE
            }
        }
    }

    class ColorPickerRowHandle(
        val row: LinearLayout,
        val titleView: TextView,
        val previewView: View,
        val hexTextView: TextView
    ) {
        fun setColor(hexColor: String) {
            val parsed = runCatching { Color.parseColor(hexColor) }.getOrDefault(Color.TRANSPARENT)
            val gd = previewView.background as? GradientDrawable
            gd?.setColor(parsed)
            hexTextView.text = hexColor.uppercase()
        }
    }

    /**
     * Standard clickable row: `[title (+ optional summary)] …`
     *
     * @param summaryCustomView When non-null, replaces the [summary] text line with this view;
     *   the caller owns its content and refresh.
     */
    fun createClickableRow(
        context: Context,
        title: String,
        summary: String? = null,
        onClick: () -> Unit,
        onLongClick: (() -> Boolean)? = null,
        summaryCustomView: View? = null
    ): ClickableRowHandle {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val vPad = stockRowVerticalPadding(context)
            val hPad = dp(16, context)
            setPadding(hPad, vPad, hPad, vPad)
            minimumHeight = stockRowMinHeight(context)
            isClickable = true
            isFocusable = true
            applyStockPressRipple(this)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener { onLongClick() }
            }
        }

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setPadding(0, 0, dp(12, context), 0)
            }
        }

        val titleTv = TextView(context).apply {
            text = title
            applyStockTitleStyle(this, context)
        }
        textLayout.addView(titleTv)

        val summaryTv = if (summaryCustomView != null) {
            textLayout.addView(summaryCustomView)
            null
        } else if (summary != null) {
            TextView(context).apply {
                text = summary
                applyStockSummaryStyle(this, context)
                setPadding(0, dp(2, context), 0, 0)
            }.also { textLayout.addView(it) }
        } else null

        row.addView(textLayout)

        return ClickableRowHandle(row, titleTv, summaryTv)
    }

    fun createColorPickerRow(
        context: Context,
        title: String,
        initialColorHex: String,
        onClick: () -> Unit
    ): ColorPickerRowHandle {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val vPad = stockRowVerticalPadding(context)
            val hPad = dp(16, context)
            setPadding(hPad, vPad, hPad, vPad)
            minimumHeight = stockRowMinHeight(context)
            isClickable = true
            isFocusable = true
            applyStockPressRipple(this)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
        }

        val titleTv = TextView(context).apply {
            text = title
            applyStockTitleStyle(this, context)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setPadding(0, 0, dp(12, context), 0)
            }
        }
        row.addView(titleTv)

        val rightContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val parsed = runCatching { Color.parseColor(initialColorHex) }.getOrDefault(Color.TRANSPARENT)
        val circleSize = dp(22, context)
        val previewView = View(context).apply {
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(parsed)
                setStroke(dp(1, context), 0x33888888)
            }
            background = gd
            layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                marginEnd = dp(8, context)
            }
        }
        rightContainer.addView(previewView)

        val hexTv = TextView(context).apply {
            text = initialColorHex.uppercase()
            applyStockDescriptionStyle(this, context)
        }
        rightContainer.addView(hexTv)

        row.addView(rightContainer)

        return ColorPickerRowHandle(row, titleTv, previewView, hexTv)
    }

    /**
     * Disables Force Dark recursively on the view subtree.
     *
     * Host `com.oplus.keyboard` themes (`Theme.Empty` / `Theme.Settings` / `TextTheme`) are
     * light themes with `android:forceDarkAllowed="true"` and `android:isLightTheme="true"`.
     * Stock COUI components call `setForceDarkAllow(false)` on their own
     * (`COUIButton` / `COUICheckBox` / `COUICardListSelectedItemLayout`).
     *
     * This module supplies its own light and dark colors, so the framework inversion is
     * disabled; otherwise night values such as `#F2F2F7` text are inverted a second time.
     */
    fun disableForceDarkRecursively(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.isForceDarkAllowed = false
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                disableForceDarkRecursively(view.getChildAt(i))
            }
        }
    }

    fun showColorPickerDialog(
        context: Context,
        title: String,
        initialHex: String,
        onColorSelected: (String) -> Unit
    ) {
        val parsedInitial = runCatching { Color.parseColor(initialHex) }.getOrDefault(Color.LTGRAY)

        val dialog = android.app.Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val cornerRadiusPx = dp(24, context).toFloat()
        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusPx
                setColor(getDialogSurfaceColor(context))
            }
            background = bg
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(16, context).toFloat()
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        applySmoothOutline(view, outline, cornerRadiusPx)
                    }
                }
                clipToOutline = true
            }
            val pHoriz = dp(20, context)
            val pTop = dp(20, context)
            val pBottom = dp(16, context)
            setPadding(pHoriz, pTop, pHoriz, pBottom)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getPrimaryTextColor(context))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14, context)
            }
        }
        rootCard.addView(titleView)

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14, context)
            }
        }

        val circleSize = dp(32, context)
        val previewCircle = View(context).apply {
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(parsedInitial)
                setStroke(dp(1, context), 0x33888888)
            }
            background = gd
            layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                marginEnd = dp(12, context)
            }
        }
        topRow.addView(previewCircle)

        val editText = createEditText(context, "#RRGGBB").apply {
            setText(initialHex.uppercase())
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(editText)
        rootCard.addView(topRow)

        // 2D color wheel: outer hue ring plus inner saturation/value square.
        val wheelSize = dp(220, context)
        val colorWheel = RealOSColorWheel(context, initialColor = parsedInitial).apply {
            layoutParams = LinearLayout.LayoutParams(
                wheelSize,
                wheelSize
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(12, context)
            }
        }
        rootCard.addView(colorWheel)

        var isUpdatingFromWheel = false

        colorWheel.setOnColorChanged { argb ->
            val hex = String.format("#%06X", 0xFFFFFF and argb)
            if (editText.text.toString().trim() != hex) {
                isUpdatingFromWheel = true
                editText.setText(hex)
                isUpdatingFromWheel = false
            }
            (previewCircle.background as? GradientDrawable)?.setColor(argb)
        }

        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isUpdatingFromWheel) return
                val str = s?.toString()?.trim() ?: ""
                val formatted = if (str.startsWith("#")) str else "#$str"
                if (formatted.length == 7 || formatted.length == 9) {
                    val parsed = runCatching { Color.parseColor(formatted) }.getOrNull()
                    if (parsed != null) {
                        (previewCircle.background as? GradientDrawable)?.setColor(parsed)
                        colorWheel.setSuppressCallback(true)
                        colorWheel.setColor(parsed)
                        colorWheel.setSuppressCallback(false)
                    }
                }
            }
        })

        val buttonBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4, context)
            }
        }

        val cancelBtn = TextView(context).apply {
            text = OplusWorkshopUiText.getDialogCancel(context)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getSecondaryTextColor(context))
            val pV = dp(8, context)
            val pH = dp(14, context)
            setPadding(pH, pV, pH, pV)
            isClickable = true
            isFocusable = true
            val ripple = RippleDrawable(
                ColorStateList.valueOf(if (isNightMode(context)) 0x33FFFFFF else 0x1F000000),
                null,
                null
            )
            background = ripple
            setOnClickListener {
                dialog.dismiss()
            }
        }

        val okBtn = TextView(context).apply {
            text = OplusWorkshopUiText.getDialogOk(context)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            val pV = dp(8, context)
            val pH = dp(18, context)
            setPadding(pH, pV, pH, pV)
            isClickable = true
            isFocusable = true
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18, context).toFloat()
                setColor(getAccentColor(context))
            }
            val ripple = RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                btnBg,
                null
            )
            background = ripple
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(12, context)
            }
            setOnClickListener {
                val text = editText.text.toString().trim()
                val finalHex = if (text.startsWith("#")) text else "#$text"
                val valid = runCatching { Color.parseColor(finalHex) }.isSuccess
                if (valid && (finalHex.length == 7 || finalHex.length == 9)) {
                    onColorSelected(finalHex.uppercase())
                }
                dialog.dismiss()
            }
        }

        buttonBar.addView(cancelBtn)
        buttonBar.addView(okBtn)
        rootCard.addView(buttonBar)

        val shadowPadding = dp(20, context)
        val shadowWrapper = FrameLayout(context).apply {
            setPadding(shadowPadding, shadowPadding, shadowPadding, shadowPadding)
            clipChildren = false
            clipToPadding = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(rootCard)
            setOnClickListener {
                dialog.dismiss()
            }
        }
        rootCard.isClickable = true

        disableForceDarkRecursively(shadowWrapper)

        dialog.setContentView(shadowWrapper)
        dialog.setCanceledOnTouchOutside(true)

        dialog.window?.let { win ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                win.setElevation(0f)
                win.decorView.elevation = 0f
            }
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.decorView.setPadding(0, 0, 0, 0)
            win.decorView.background = ColorDrawable(Color.TRANSPARENT)
            (win.decorView as? ViewGroup)?.clipChildren = false
            (win.decorView as? ViewGroup)?.clipToPadding = false
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val cardWidth = (screenWidth * 0.88f).toInt().coerceAtMost(dp(340, context))
            val totalWidth = cardWidth + shadowPadding * 2
            win.setLayout(totalWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setGravity(Gravity.CENTER)
            disableForceDarkRecursively(win.decorView)
        }

        dialog.show()
    }

    /**
     * ColorOS card-style confirmation and action dialog.
     * Uses the ColorOS card surface color, OplusOutline smooth corners, a shadow padding
     * wrapper and a row of text buttons.
     */
    fun showCustomActionDialog(
        context: Context,
        title: String,
        message: String,
        positiveText: String,
        onPositive: () -> Unit,
        neutralText: String? = null,
        onNeutral: (() -> Unit)? = null,
        negativeText: String? = null,
        onNegative: (() -> Unit)? = null
    ) {
        val dialog = android.app.Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val cornerRadiusPx = dp(24, context).toFloat()
        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusPx
                setColor(getDialogSurfaceColor(context))
            }
            background = bg
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(16, context).toFloat()
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        applySmoothOutline(view, outline, cornerRadiusPx)
                    }
                }
                clipToOutline = true
            }
            val pHoriz = dp(22, context)
            val pTop = dp(22, context)
            val pBottom = dp(18, context)
            setPadding(pHoriz, pTop, pHoriz, pBottom)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        val titleView = TextView(context).apply {
            text = title
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getPrimaryTextColor(context))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10, context)
            }
        }
        rootCard.addView(titleView)

        // Message
        val messageView = TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(getSecondaryTextColor(context))
            setLineSpacing(dp(2, context).toFloat(), 1.0f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(20, context)
            }
        }
        rootCard.addView(messageView)

        // Button Bar
        val buttonBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Negative (Cancel) Button
        if (!negativeText.isNullOrEmpty()) {
            val cancelBtn = TextView(context).apply {
                text = negativeText
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(getSecondaryTextColor(context))
                val pV = dp(8, context)
                val pH = dp(12, context)
                setPadding(pH, pV, pH, pV)
                isClickable = true
                isFocusable = true
                val ripple = RippleDrawable(
                    ColorStateList.valueOf(if (isNightMode(context)) 0x33FFFFFF else 0x1F000000),
                    null,
                    null
                )
                background = ripple
                setOnClickListener {
                    dialog.dismiss()
                    onNegative?.invoke()
                }
            }
            buttonBar.addView(cancelBtn)
        }

        // Neutral (Clear / Destructive) Button
        if (!neutralText.isNullOrEmpty()) {
            val neutralBtn = TextView(context).apply {
                text = neutralText
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                // Soft alert red for the destructive action.
                setTextColor(Color.parseColor("#FF453A"))
                val pV = dp(8, context)
                val pH = dp(12, context)
                setPadding(pH, pV, pH, pV)
                isClickable = true
                isFocusable = true
                val ripple = RippleDrawable(
                    ColorStateList.valueOf(if (isNightMode(context)) 0x33FFFFFF else 0x1F000000),
                    null,
                    null
                )
                background = ripple
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(6, context)
                }
                setOnClickListener {
                    dialog.dismiss()
                    onNeutral?.invoke()
                }
            }
            buttonBar.addView(neutralBtn)
        }

        // Positive (Confirm / Replace) Pill Button
        val positiveBtn = TextView(context).apply {
            text = positiveText
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            val pV = dp(8, context)
            val pH = dp(18, context)
            setPadding(pH, pV, pH, pV)
            isClickable = true
            isFocusable = true
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18, context).toFloat()
                setColor(getAccentColor(context))
            }
            val ripple = RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                btnBg,
                null
            )
            background = ripple
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(10, context)
            }
            setOnClickListener {
                dialog.dismiss()
                onPositive.invoke()
            }
        }
        buttonBar.addView(positiveBtn)
        rootCard.addView(buttonBar)

        val shadowPadding = dp(20, context)
        val shadowWrapper = FrameLayout(context).apply {
            setPadding(shadowPadding, shadowPadding, shadowPadding, shadowPadding)
            clipChildren = false
            clipToPadding = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(rootCard)
            setOnClickListener {
                dialog.dismiss()
            }
        }
        rootCard.isClickable = true

        disableForceDarkRecursively(shadowWrapper)

        dialog.setContentView(shadowWrapper)
        dialog.setCanceledOnTouchOutside(true)

        dialog.window?.let { win ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                win.setElevation(0f)
                win.decorView.elevation = 0f
            }
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.decorView.setPadding(0, 0, 0, 0)
            win.decorView.background = ColorDrawable(Color.TRANSPARENT)
            (win.decorView as? ViewGroup)?.clipChildren = false
            (win.decorView as? ViewGroup)?.clipToPadding = false
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val cardWidth = (screenWidth * 0.88f).toInt().coerceAtMost(dp(340, context))
            val totalWidth = cardWidth + shadowPadding * 2
            win.setLayout(totalWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setGravity(Gravity.CENTER)
            disableForceDarkRecursively(win.decorView)
        }

        dialog.show()
    }

    /**
     * Builds a ColorOS HandWrite (HWSeekbarPreference) style section seek bar row.
     * Uses com.coui.appcompat.seekbar.COUISectionSeekBar with adaptive vibration.
     * Supports discrete step intervals, for example step = 10.
     */
    /**
     * Selects the listener interface overload for `setOnSeekBarChangeListener`.
     * Returns null when no candidate matches.
     *
     * The predicate: the first parameter of `onProgressChanged` accepts a barClass instance
     * (`paramType.isAssignableFrom(barClass)`). A check of `iface.isAssignableFrom(barClass)`
     * tests whether the bar implements the interface and always fails here, which skipped the
     * listener installation.
     *
     * Host candidates (`com/coui/appcompat/seekbar/`):
     * | Interface | `onProgressChanged` first parameter | Accepts `COUISectionSeekBar` |
     * |---|---|---|
     * | `COUISeekBar$OnSeekBarChangeListener` | `COUISeekBar` | yes |
     * | `COUISeekBarCore$OnSeekBarChangeListener` | `COUISeekBarCore` | yes |
     * | `SeekBar$OnSeekBarChangeListener` | `SeekBar` | no (`COUI` bars are `AbsSeekBar` subclasses) |
     *
     * Among several candidates the one earliest in the barClass superclass chain wins, the
     * outermost host entry point: `COUISeekBar` (depth 1) over `COUISeekBarCore` (depth 3).
     * `COUISeekBar.setOnSeekBarChangeListener` wraps the listener and forwards it to
     * `COUISeekBarCore$…`.
     */
    private fun findSeekBarListenerInterface(barClass: Class<*>): Class<*>? {
        val superChain = generateSequence(barClass as Class<*>?) { it.superclass }.toList()

        fun depth(iface: Class<*>): Int {
            val firstParam = iface.declaredMethods
                .firstOrNull { it.name == "onProgressChanged" && it.parameterTypes.isNotEmpty() }
                ?.parameterTypes?.get(0) ?: return Int.MAX_VALUE
            val idx = superChain.indexOfFirst { it == firstParam }
            return if (idx >= 0) idx else Int.MAX_VALUE
        }

        return barClass.methods.asSequence()
            .filter { it.name == "setOnSeekBarChangeListener" && it.parameterTypes.size == 1 }
            .mapNotNull { it.parameterTypes.getOrNull(0) }
            .filter { iface ->
                iface.declaredMethods.any { m ->
                    m.name == "onProgressChanged" && m.parameterTypes.isNotEmpty() &&
                        m.parameterTypes[0].isAssignableFrom(barClass)
                }
            }
            .sortedBy { depth(it) }
            .firstOrNull()
    }

    /** Shared scaffold for a seek bar row: container, title/value header and start/end labels. */
    private class SeekRowScaffold(
        val container: LinearLayout,
        val valueTv: TextView,
        val touchListener: View.OnTouchListener
    )

    /**
     * Builds the seek row scaffold.
     *
     * Handles the parts independent of the bar type: container padding, header row, value
     * view, start/end labels and the touch listener that blocks horizontal drag interception.
     */
    private fun buildSeekRowScaffold(
        context: Context,
        title: String,
        initialProgress: Int,
        startLabel: String,
        endLabel: String,
        formatValue: (Int) -> String
    ): SeekRowScaffold {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val vPad = stockRowVerticalPadding(context)
            val hPad = dp(16, context)
            setPadding(hPad, vPad, hPad, vPad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Blocks the parent ScrollView from intercepting the horizontal drag.
        val touchListener = View.OnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        // Title on the left, current value in accent color on the right.
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleTv = TextView(context).apply {
            text = title
            applyStockTitleStyle(this, context)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(titleTv)

        val valueTv = TextView(context).apply {
            text = formatValue(initialProgress)
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getAccentColor(context))
        }
        headerRow.addView(valueTv)
        container.addView(headerRow)

        // Start/end labels, matching host tv_type_start / tv_type_end.
        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2, context)
            }
        }

        val startLabelTv = TextView(context).apply {
            text = startLabel
            applyStockDescriptionStyle(this, context)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        labelRow.addView(startLabelTv)

        val endLabelTv = TextView(context).apply {
            text = endLabel
            applyStockDescriptionStyle(this, context)
            gravity = Gravity.END
        }
        labelRow.addView(endLabelTv)
        container.addView(labelRow)

        return SeekRowScaffold(container, valueTv, touchListener)
    }

    /**
     * Attaches the bar to the scaffold: host COUI bar first, plain SeekBar as fallback.
     *
     * [step] > 1 selects a section bar ([createSectionSeekBarRow]) with progress scaled by step;
     * otherwise the bar is continuous and progress is `value - min`.
     *
     * Both paths invoke the callback only for user drags.
     */
    private fun attachSeekBarToRow(
        context: Context,
        scaffold: SeekRowScaffold,
        couiClassName: String,
        step: Int,
        min: Int,
        max: Int,
        initialProgress: Int,
        topMarginDp: Int,
        enableAdaptiveVibrator: Boolean,
        callerName: String,
        formatValue: (Int) -> String,
        onProgressChange: (Int) -> Unit
    ) {
        val range = (max - min).coerceAtLeast(1)
        val totalSteps = if (step > 1) (range / step).coerceAtLeast(1) else range
        val currentStep = if (step > 1) {
            ((initialProgress - min) / step).coerceIn(0, totalSteps)
        } else {
            (initialProgress - min).coerceIn(0, range)
        }
        fun actualOf(progress: Int): Int = if (step > 1) min + progress * step else progress + min
        fun barLayoutParams() = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(topMarginDp, context)
            bottomMargin = dp(4, context)
        }

        val barView = runCatching {
            val barClass = context.classLoader.loadClass(couiClassName)
            val barInstance = barClass.getConstructor(Context::class.java).newInstance(context) as View

            // Blocks parent ScrollView interception of horizontal drags.
            barInstance.setOnTouchListener(scaffold.touchListener)

            if (enableAdaptiveVibrator) {
                runCatching {
                    barClass.getMethod("setEnableAdaptiveVibrator", Boolean::class.javaPrimitiveType)
                        .invoke(barInstance, true)
                }
            }

            // Sets max and progress.
            //
            // The onFailure lambda runs outside the outer runCatching, so it is wrapped in its
            // own runCatching to keep exceptions from reaching the caller.
            if (step > 1) {
                runCatching {
                    barClass.getMethod("setMax", Int::class.javaPrimitiveType).invoke(barInstance, totalSteps)
                    barClass.getMethod(
                        "setProgress",
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType
                    ).invoke(barInstance, currentStep, false, false)
                }.onFailure {
                    runCatching {
                        barClass.getMethod("setMax", Int::class.javaPrimitiveType).invoke(barInstance, totalSteps)
                        barClass.getMethod("setProgress", Int::class.javaPrimitiveType).invoke(barInstance, currentStep)
                    }
                }
            } else {
                barClass.getMethod("setMax", Int::class.javaPrimitiveType).invoke(barInstance, totalSteps)
                barClass.getMethod("setProgress", Int::class.javaPrimitiveType).invoke(barInstance, currentStep)
            }

            // Attaches the listener through a dynamic proxy.
            //
            // The host declares several `setOnSeekBarChangeListener` overloads with different
            // parameter types (`COUISeekBarCore.OnSeekBarChangeListener` and the `COUISeekBar`
            // overload). The proxy copies the chosen interface signature; a parameter type that
            // does not accept the bar instance makes ART fail the CHECKCAST at the proxy entry
            // with `ClassCastException`. Interface selection is handled by
            // findSeekBarListenerInterface().
            val listenerInterface = findSeekBarListenerInterface(barClass)
                ?: throw IllegalStateException(
                    "No usable OnSeekBarChangeListener interface found (candidates=" +
                        barClass.methods.filter { it.name == "setOnSeekBarChangeListener" }
                            .map { it.parameterTypes.getOrNull(0)?.name } + "）"
                )

            val proxy = Proxy.newProxyInstance(
                listenerInterface.classLoader ?: context.classLoader,
                arrayOf(listenerInterface)
            ) { _, method, args ->
                if (method.name == "onProgressChanged" && args != null && args.size >= 2) {
                    val actual = actualOf(args[1] as? Int ?: 0)
                    scaffold.valueTv.text = formatValue(actual)
                    // Programmatic setProgress, from init or external sync, skips the callback.
                    if (args.lastOrNull() as? Boolean != false) onProgressChange(actual)
                }
                null
            }
            barClass.getMethod("setOnSeekBarChangeListener", listenerInterface).invoke(barInstance, proxy)

            barInstance.apply { layoutParams = barLayoutParams() }
        }.getOrElse {
            // createSeekBar() returns a real android.widget.SeekBar (COUI bars are AbsSeekBar
            // subclasses, see createSeekBar), so SeekBar.OnSeekBarChangeListener is type-safe.
            Log.w(
                "RealOS-CouiFactory",
                "$callerName: COUI slider unavailable -> falling back to a plain SeekBar",
                it
            )
            createSeekBar(context).apply {
                this.max = totalSteps
                this.progress = currentStep
                setOnTouchListener(scaffold.touchListener)
                layoutParams = barLayoutParams()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        val actual = actualOf(progress)
                        scaffold.valueTv.text = formatValue(actual)
                        if (fromUser) {
                            onProgressChange(actual)
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
        }

        scaffold.container.addView(barView)
    }

    /** Section seek bar row (`COUISectionSeekBar`) with progress scaled by [step]. */
    fun createSectionSeekBarRow(
        context: Context,
        title: String,
        min: Int,
        max: Int,
        initialProgress: Int,
        startLabel: String,
        endLabel: String,
        step: Int = 1,
        formatValue: (Int) -> String,
        onProgressChange: (Int) -> Unit
    ): LinearLayout {
        val scaffold = buildSeekRowScaffold(context, title, initialProgress, startLabel, endLabel, formatValue)
        attachSeekBarToRow(
            context = context,
            scaffold = scaffold,
            couiClassName = "com.coui.appcompat.seekbar.COUISectionSeekBar",
            step = step,
            min = min,
            max = max,
            initialProgress = initialProgress,
            topMarginDp = 2,
            enableAdaptiveVibrator = true,
            callerName = "createSectionSeekBarRow",
            formatValue = formatValue,
            onProgressChange = onProgressChange
        )
        return scaffold.container
    }

    /** Continuous seek bar row (`COUISeekBar`), matching ColorOS settings_volume_slider. */
    fun createContinuousSeekBarRow(
        context: Context,
        title: String,
        min: Int,
        max: Int,
        initialProgress: Int,
        startLabel: String,
        endLabel: String,
        formatValue: (Int) -> String,
        onProgressChange: (Int) -> Unit
    ): LinearLayout {
        val scaffold = buildSeekRowScaffold(context, title, initialProgress, startLabel, endLabel, formatValue)
        attachSeekBarToRow(
            context = context,
            scaffold = scaffold,
            couiClassName = "com.coui.appcompat.seekbar.COUISeekBar",
            step = 1,
            min = min,
            max = max,
            initialProgress = initialProgress,
            topMarginDp = 4,
            enableAdaptiveVibrator = false,
            callerName = "createContinuousSeekBarRow",
            formatValue = formatValue,
            onProgressChange = onProgressChange
        )
        return scaffold.container
    }

    /** Creates an EditText with a ColorOS rounded search-style background. */
    fun createEditText(context: Context, hintText: String): android.widget.EditText {
        return android.widget.EditText(context).apply {
            hint = hintText
            textSize = 14f
            setTextColor(getPrimaryTextColor(context))
            setHintTextColor(getSecondaryTextColor(context))
            val padH = dp(16, context)
            val padV = dp(12, context)
            setPadding(padH, padV, padH, padV)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12, context).toFloat()
                setColor(if (isNightMode(context)) Color.parseColor("#2C2C2E") else Color.parseColor("#EBEBF0"))
            }
            background = bg
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener {
                requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    /**
     * Creates a vector Android Bot (Bugdroid) View for live keyboard blur background comparison.
     */
    /**
     * Creates a vector Android Bot (Bugdroid) View for keyboard blur background preview.
     *
     * [animate] = true rotates the body hue over [AndroidBotSpec.HUE_PERIOD_MS] per cycle.
     * The animation runs only while attached and stops on detach.
     *
     * Geometry must stay in sync with the Compose path (`AndroidBotPreview`).
     */
    fun createAndroidBotView(
        context: Context,
        sizeDp: Int = AndroidBotSpec.SIZE_DP,
        animate: Boolean = false
    ): View {
        return object : View(context) {
            private val botPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                // Eyes are cut out of the head layer, not painted over it.
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            private val headRect = android.graphics.RectF()
            private val bodyRect = android.graphics.RectF()
            private val armLRect = android.graphics.RectF()
            private val armRRect = android.graphics.RectF()
            private val legLRect = android.graphics.RectF()
            private val legRRect = android.graphics.RectF()

            /** Current hue; stays at the base green when [animate] is false. */
            private var currentHue = AndroidBotSpec.BASE_HUE
            private var hueAnimator: android.animation.ValueAnimator? = null

            init {
                applyColors()
            }

            private fun applyColors() {
                val body = Color.HSVToColor(
                    floatArrayOf(currentHue.mod(360f), AndroidBotSpec.SATURATION, AndroidBotSpec.VALUE)
                )
                botPaint.color = body
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                applyColors()
                if (!animate || hueAnimator != null) return
                hueAnimator = android.animation.ValueAnimator.ofFloat(
                    AndroidBotSpec.BASE_HUE,
                    AndroidBotSpec.BASE_HUE + 360f
                ).apply {
                    duration = AndroidBotSpec.HUE_PERIOD_MS
                    interpolator = android.view.animation.LinearInterpolator()
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    addUpdateListener { animator ->
                        currentHue = animator.animatedValue as Float
                        applyColors()
                        invalidate()
                    }
                    start()
                }
            }

            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                hueAnimator?.cancel()
                hueAnimator = null
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f) return

                val cx = w / 2f
                val cy = h / 2f
                val s = Math.min(w, h) * 0.72f // Scaling factor

                // Dimensions
                val headR = s * 0.28f
                val gap = s * 0.04f
                val bodyW = s * 0.56f
                val bodyH = s * 0.44f
                val armW = s * 0.11f
                val armH = s * 0.36f
                val legW = s * 0.11f
                val legH = s * 0.20f
                val antennaW = s * 0.026f

                val headCenterY = cy - s * 0.15f

                // 1. Antennas
                botPaint.strokeWidth = antennaW
                botPaint.strokeCap = android.graphics.Paint.Cap.ROUND
                val antLen = s * 0.12f
                val dx = (antLen * 0.5f)
                val dy = (antLen * 0.866f)
                canvas.drawLine(cx - headR * 0.5f, headCenterY - headR * 0.866f, cx - headR * 0.5f - dx, headCenterY - headR * 0.866f - dy, botPaint)
                canvas.drawLine(cx + headR * 0.5f, headCenterY - headR * 0.866f, cx + headR * 0.5f + dx, headCenterY - headR * 0.866f - dy, botPaint)

                // 2. Head (semi-circle); inked into an offscreen layer so the eyes can be cut out.
                headRect.set(cx - headR, headCenterY - headR, cx + headR, headCenterY + headR)
                val headLayer = canvas.saveLayer(headRect, null)
                canvas.drawArc(headRect, 180f, 180f, true, botPaint)

                // Eyes: CLEAR punches holes; the page background shows through.
                val eyeR = headR * AndroidBotSpec.EYE_RADIUS_RATIO
                val eyeOffset = headR * AndroidBotSpec.EYE_OFFSET_RATIO
                val eyeY = headCenterY + headR * AndroidBotSpec.EYE_VERTICAL_RATIO
                canvas.drawCircle(cx - eyeOffset, eyeY, eyeR, eyePaint)
                canvas.drawCircle(cx + eyeOffset, eyeY, eyeR, eyePaint)
                canvas.restoreToCount(headLayer)

                // 3. Body
                val bodyTop = headCenterY + gap
                bodyRect.set(cx - bodyW / 2f, bodyTop, cx + bodyW / 2f, bodyTop + bodyH)
                val corner = s * 0.08f
                canvas.drawRoundRect(bodyRect, corner, corner, botPaint)
                // Fill top square of body so only bottom corners are rounded
                canvas.drawRect(cx - bodyW / 2f, bodyTop, cx + bodyW / 2f, bodyTop + corner, botPaint)

                // 4. Arms
                val armTop = bodyTop
                val armOffset = bodyW / 2f + gap + armW / 2f
                armLRect.set(cx - armOffset - armW / 2f, armTop, cx - armOffset + armW / 2f, armTop + armH)
                armRRect.set(cx + armOffset - armW / 2f, armTop, cx + armOffset + armW / 2f, armTop + armH)
                canvas.drawRoundRect(armLRect, armW / 2f, armW / 2f, botPaint)
                canvas.drawRoundRect(armRRect, armW / 2f, armW / 2f, botPaint)

                // 5. Legs
                val legTop = bodyTop + bodyH - corner
                val legOffset = bodyW * 0.22f
                legLRect.set(cx - legOffset - legW / 2f, legTop, cx - legOffset + legW / 2f, legTop + legH + corner)
                legRRect.set(cx + legOffset - legW / 2f, legTop, cx + legOffset + legW / 2f, legTop + legH + corner)
                canvas.drawRoundRect(legLRect, legW / 2f, legW / 2f, botPaint)
                canvas.drawRoundRect(legRRect, legW / 2f, legW / 2f, botPaint)
            }
        }.apply {
            val px = dp(sizeDp, context)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
    }
}
