package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.ra1nify.realos.util.RealOSLog as Log
import androidx.core.view.WindowInsetsControllerCompat
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.BoardEffectType
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.EffectColorPreset
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.FootPrintIcon
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.KeyEffectType
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.MotionEffectType
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText

/**
 * 1:1 Native ColorOS Settings View for Keys Cafe Keyboard Effects.
 *
 * Implemented using genuine ColorOS COUI styles and widgets.
 */
class OplusKeyboardEffectsView(context: Context) : FrameLayout(context) {

    init {
        activeInstance = java.lang.ref.WeakReference(this)
        setBackgroundColor(CouiComponentFactory.getBackgroundColor(context))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        // Adjust status bar icon brightness according to dark mode
        (context as? Activity)?.let { act ->
            val isNight = CouiComponentFactory.isNightMode(context)
            WindowInsetsControllerCompat(act.window, act.window.decorView).apply {
                isAppearanceLightStatusBars = !isNight
                isAppearanceLightNavigationBars = !isNight
            }
        }

        // ── 1. Scrollable Body ──
        val scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(CouiComponentFactory.getBackgroundColor(context))
        }

        val scrollContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setBackgroundColor(CouiComponentFactory.getBackgroundColor(context))
        }
        scrollView.addView(scrollContent)


        // ── Section 2: Board Blending (keyboard background light effect) ──
        scrollContent.addView(CouiComponentFactory.createCategoryTitle(context, OplusWorkshopUiText.getSectionEffectsBoard(context)))
        val boardCard = CouiComponentFactory.createCardContainer(context)
        val currentBoard = OplusInputPrefs.getEffectsBoard(context)
        val boardItems = BoardEffectType.entries.toList()
        val boardCheckViews = ArrayList<View>()

        boardItems.forEachIndexed { index, type ->
            if (index > 0) boardCard.addView(CouiComponentFactory.createDivider(context))
            val isSelected = type.key.equals(currentBoard, ignoreCase = true)
            val row = createSelectionRow(
                context = context,
                title = type.getTitle(context),
                summary = type.getSummary(context),
                selected = isSelected,
                onSelected = {
                    OplusInputPrefs.setEffectsBoard(context, type.key)
                    boardCheckViews.forEachIndexed { i, checkView ->
                        checkView.visibility = if (i == index) View.VISIBLE else View.GONE
                    }
                }
            )
            boardCheckViews.add(row.second)
            boardCard.addView(row.first)
        }
        scrollContent.addView(boardCard)

        // ── Section 3: Key Blending (key press light effect) ──
        scrollContent.addView(CouiComponentFactory.createCategoryTitle(context, OplusWorkshopUiText.getSectionEffectsKey(context)))
        val keyCard = CouiComponentFactory.createCardContainer(context)
        val currentKey = OplusInputPrefs.getEffectsKey(context)
        val keyItems = KeyEffectType.entries.toList()
        val keyCheckViews = ArrayList<View>()

        // ── Section 3b: Foot Print icon picker (footprint icon) ──
        // Samsung KeyFootPrint ships 10 icons (9 built-in vectors + 1 custom PNG).
        // **Appears only when Key Blending = FOOT_PRINT** (the key selection callback below
        // toggles visibility).
        val fpSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        fpSection.addView(CouiComponentFactory.createCategoryTitle(context, OplusWorkshopUiText.getSectionFootPrint(context)))
        val fpCard = CouiComponentFactory.createCardContainer(context)

        val currentFpIcon = OplusInputPrefs.getFootPrintIcon(context)
        val fpItems = FootPrintIcon.entries.toList()
        val fpCheckViews = ArrayList<View>()
        // The custom row preview refreshes separately after import or removal.
        var customPreviewRefresher: (() -> Unit)? = null

        fpItems.forEachIndexed { index, icon ->
            if (index > 0) fpCard.addView(CouiComponentFactory.createDivider(context))
            val isSelected = icon.key.equals(currentFpIcon, ignoreCase = true)
            val row = createFootPrintRow(
                context = context,
                icon = icon,
                selected = isSelected,
                isCustomRow = icon.isCustom,
                onSelected = {
                    OplusInputPrefs.setFootPrintIcon(context, icon.key)
                    fpCheckViews.forEachIndexed { i, checkView ->
                        checkView.visibility = if (i == index) View.VISIBLE else View.GONE
                    }
                    customPreviewRefresher?.invoke()
                }
            )
            fpCheckViews.add(row.second)
            if (icon.isCustom) customPreviewRefresher = row.third
            fpCard.addView(row.first)

            // The custom row lists the "Pick image / Remove" action buttons below it.
            if (icon.isCustom) {
                fpCard.addView(createFootPrintActionRow(context, OplusWorkshopUiText.getFootPrintCustomPick(context)) {
                    launchFootPrintPicker()
                })
                if (OplusInputPrefs.hasCustomFootPrint(context)) {
                    fpCard.addView(createFootPrintActionRow(context, OplusWorkshopUiText.getFootPrintCustomClear(context)) {
                        OplusInputPrefs.clearCustomFootPrint(context)
                        row.third.invoke()
                    })
                }
            }
        }
        fpSection.addView(fpCard)
        // Initial visibility: only when the current key blending = FOOT_PRINT.
        fpSection.visibility =
            if (KeyEffectType.fromKey(currentKey) == KeyEffectType.FOOT_PRINT) View.VISIBLE else View.GONE
        // Adds keyCard first; the foot print section follows right after key blending.
        scrollContent.addView(keyCard)

        keyItems.forEachIndexed { index, type ->
            if (index > 0) keyCard.addView(CouiComponentFactory.createDivider(context))
            val isSelected = type.key.equals(currentKey, ignoreCase = true)
            val row = createSelectionRow(
                context = context,
                title = type.getTitle(context),
                summary = type.getSummary(context),
                selected = isSelected,
                onSelected = {
                    OplusInputPrefs.setEffectsKey(context, type.key)
                    keyCheckViews.forEachIndexed { i, checkView ->
                        checkView.visibility = if (i == index) View.VISIBLE else View.GONE
                    }
                    // The foot print icon section appears only when FOOT_PRINT is selected.
                    fpSection.visibility =
                        if (type == KeyEffectType.FOOT_PRINT) View.VISIBLE else View.GONE
                }
            )
            keyCheckViews.add(row.second)
            keyCard.addView(row.first)
        }
        scrollContent.addView(fpSection)

        // ── Section 4: Key Motion (physical key deformation) ──
        scrollContent.addView(CouiComponentFactory.createCategoryTitle(context, OplusWorkshopUiText.getSectionEffectsMotion(context)))
        val motionCard = CouiComponentFactory.createCardContainer(context)
        val currentMotion = OplusInputPrefs.getEffectsMotion(context)
        val motionItems = MotionEffectType.entries.toList()
        val motionCheckViews = ArrayList<View>()

        motionItems.forEachIndexed { index, type ->
            if (index > 0) motionCard.addView(CouiComponentFactory.createDivider(context))
            val isSelected = type.key.equals(currentMotion, ignoreCase = true)
            val row = createSelectionRow(
                context = context,
                title = type.getTitle(context),
                summary = type.getSummary(context),
                selected = isSelected,
                onSelected = {
                    OplusInputPrefs.setEffectsMotion(context, type.key)
                    motionCheckViews.forEachIndexed { i, checkView ->
                        checkView.visibility = if (i == index) View.VISIBLE else View.GONE
                    }
                }
            )
            motionCheckViews.add(row.second)
            motionCard.addView(row.first)
        }
        scrollContent.addView(motionCard)

        // ── Section 5: Color Preset (color scheme palette) ──
        scrollContent.addView(CouiComponentFactory.createCategoryTitle(context, OplusWorkshopUiText.getSectionEffectsColor(context)))
        val colorCard = CouiComponentFactory.createCardContainer(context)
        val currentColor = OplusInputPrefs.getEffectsColorPreset(context)
        val colorItems = EffectColorPreset.entries.toList()
        val colorCheckViews = ArrayList<View>()

        colorItems.forEachIndexed { index, preset ->
            if (index > 0) colorCard.addView(CouiComponentFactory.createDivider(context))
            val isSelected = preset.key.equals(currentColor, ignoreCase = true)
            val row = createColorPresetRow(
                context = context,
                preset = preset,
                selected = isSelected,
                onSelected = {
                    OplusInputPrefs.setEffectsColorPreset(context, preset.key)
                    colorCheckViews.forEachIndexed { i, checkView ->
                        checkView.visibility = if (i == index) View.VISIBLE else View.GONE
                    }
                }
            )
            colorCheckViews.add(row.second)
            colorCard.addView(row.first)
        }
        scrollContent.addView(colorCard)

        // ── Section 6: Effect Parameters (effect parameter sliders) ──
        scrollContent.addView(CouiComponentFactory.createCategoryTitle(context, OplusWorkshopUiText.getSectionEffectsParams(context)))
        val paramsCard = CouiComponentFactory.createCardContainer(context)

        // 1. Light effect opacity (0% ~ 100%)
        val currentAlpha = (OplusInputPrefs.getEffectsAlphaRatio(context) * 100).toInt().coerceIn(0, 100)
        paramsCard.addView(
            CouiComponentFactory.createContinuousSeekBarRow(
                context = context,
                title = OplusWorkshopUiText.getSliderEffectsAlpha(context),
                min = 0,
                max = 100,
                initialProgress = currentAlpha,
                startLabel = "0%",
                endLabel = "100%",
                formatValue = { "$it%" },
                onProgressChange = { value ->
                    OplusInputPrefs.setEffectsAlphaRatio(context, value / 100f)
                }
            )
        )
        paramsCard.addView(CouiComponentFactory.createDivider(context))

        // 2. Spread range ratio (10% ~ 200%)
        val currentRange = (OplusInputPrefs.getEffectsRangeRatio(context) * 100).toInt().coerceIn(50, 200)
        paramsCard.addView(
            CouiComponentFactory.createContinuousSeekBarRow(
                context = context,
                title = OplusWorkshopUiText.getSliderEffectsRange(context),
                min = 10,
                max = 200,
                initialProgress = currentRange,
                startLabel = "10%",
                endLabel = "200%",
                formatValue = { "$it%" },
                onProgressChange = { value ->
                    OplusInputPrefs.setEffectsRangeRatio(context, value / 100f)
                }
            )
        )
        paramsCard.addView(CouiComponentFactory.createDivider(context))

        // 3. Spread speed ratio (0.1x ~ 2.0x)
        val currentSpeed = (OplusInputPrefs.getEffectsSpeedRatio(context) * 100).toInt().coerceIn(50, 200)
        paramsCard.addView(
            CouiComponentFactory.createContinuousSeekBarRow(
                context = context,
                title = OplusWorkshopUiText.getSliderEffectsSpeed(context),
                min = 10,
                max = 200,
                initialProgress = currentSpeed,
                startLabel = "0.1x",
                endLabel = "2.0x",
                formatValue = { String.format(java.util.Locale.US, "%.1fx", it / 100f) },
                onProgressChange = { value ->
                    OplusInputPrefs.setEffectsSpeedRatio(context, value / 100f)
                }
            )
        )
        scrollContent.addView(paramsCard)

        // ── Bottom Android Bot: Vector comparison background for translucent keyboard ──
        val botContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = CouiComponentFactory.dp(16, context)
            val bottomScrollPadding = CouiComponentFactory.dp(120, context)
            setPadding(p, p * 2, p, bottomScrollPadding)
        }
        val botView = CouiComponentFactory.createAndroidBotView(context, 160, animate = true)
        botContainer.addView(botView)

        val botHintTv = TextView(context).apply {
            text = OplusWorkshopUiText.getBotHint(context)
            textSize = 12f
            setTextColor(CouiComponentFactory.getSecondaryTextColor(context))
            gravity = Gravity.CENTER
            setPadding(0, CouiComponentFactory.dp(8, context), 0, 0)
        }
        botContainer.addView(botHintTv)
        scrollContent.addView(botContainer)

        val pageRoot = CouiComponentFactory.createOfficialPageRoot(
            context = context,
            title = OplusWorkshopUiText.getKeyboardEffectsTitle(context),
            contentScrollView = scrollView,
            onBackClick = { (context as? Activity)?.finish() },
            showKeyboardPreview = true
        )
        addView(pageRoot, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Disables the host Force Dark pass on this page: it carries its own light/dark colors,
        // otherwise the framework inverts the night values again (all black).
        CouiComponentFactory.disableForceDarkRecursively(this)
    }

    /**
     * Creates an authentic ColorOS styled single-choice item row with title, summary, and check indicator.
     */
    private fun createSelectionRow(
        context: Context,
        title: String,
        summary: String,
        selected: Boolean,
        onSelected: () -> Unit
    ): Pair<View, View> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = CouiComponentFactory.dp(16, context)
            val vPad = CouiComponentFactory.stockRowVerticalPadding(context)
            setPadding(hPad, vPad, hPad, vPad)
            CouiComponentFactory.applyStockPressRipple(this)
            isClickable = true
            isFocusable = true
        }

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val titleTv = TextView(context).apply {
            text = title
            CouiComponentFactory.applyStockTitleStyle(this, context)
        }
        textLayout.addView(titleTv)

        if (summary.isNotEmpty()) {
            val summaryTv = TextView(context).apply {
                text = summary
                CouiComponentFactory.applyStockSummaryStyle(this, context)
                setPadding(0, CouiComponentFactory.dp(3, context), 0, 0)
            }
            textLayout.addView(summaryTv)
        }
        container.addView(textLayout)

        // Selection indicator checkmark
        val checkView = createCheckIndicator(context).apply {
            visibility = if (selected) View.VISIBLE else View.GONE
        }
        container.addView(checkView)

        container.setOnClickListener {
            onSelected()
        }

        return Pair(container, checkView)
    }

    /**
     * Creates a color preset option row with colorful preview dots.
     */
    private fun createColorPresetRow(
        context: Context,
        preset: EffectColorPreset,
        selected: Boolean,
        onSelected: () -> Unit
    ): Pair<View, View> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = CouiComponentFactory.dp(16, context)
            val vPad = CouiComponentFactory.stockRowVerticalPadding(context)
            setPadding(hPad, vPad, hPad, vPad)
            CouiComponentFactory.applyStockPressRipple(this)
            isClickable = true
            isFocusable = true
        }

        val leftLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        // Color palette preview dots
        val dotsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, CouiComponentFactory.dp(12, context), 0)
        }

        if (preset.colors.isNotEmpty()) {
            for (color in preset.colors) {
                val dot = View(context).apply {
                    val size = CouiComponentFactory.dp(14, context)
                    layoutParams = LayoutParams(size, size).apply {
                        setMargins(0, 0, CouiComponentFactory.dp(4, context), 0)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
                }
                dotsContainer.addView(dot)
            }
        } else {
            // Rainbow / Random indicator
            val rainbowDot = View(context).apply {
                val size = CouiComponentFactory.dp(14, context)
                layoutParams = LayoutParams(size, size).apply {
                    setMargins(0, 0, CouiComponentFactory.dp(4, context), 0)
                }
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(0xFFFF4081.toInt(), 0xFF7C4DFF.toInt(), 0xFF00E676.toInt())
                ).apply {
                    shape = GradientDrawable.OVAL
                }
            }
            dotsContainer.addView(rainbowDot)
        }
        leftLayout.addView(dotsContainer)

        val titleTv = TextView(context).apply {
            text = preset.getTitle(context)
            CouiComponentFactory.applyStockTitleStyle(this, context)
        }
        leftLayout.addView(titleTv)

        container.addView(leftLayout)

        val checkView = createCheckIndicator(context).apply {
            visibility = if (selected) View.VISIBLE else View.GONE
        }
        container.addView(checkView)

        container.setOnClickListener {
            onSelected()
        }

        return Pair(container, checkView)
    }

    /**
     * Creates ColorOS checkmark icon view for single choice selection.
     */
    /**
     * Single-choice indicator: prefers the **stock** `com.coui.appcompat.checkbox.COUICheckBox`
     * (the widget behind the host's `COUICheckBoxPreference`); falls back to a checkmark
     * TextView when it is unavailable.
     *
     * The returned View stays in the checked state; the caller shows and hides it through
     * `visibility`.
     */
    private fun createCheckIndicator(context: Context): View {
        val stock = runCatching {
            val clazz = context.classLoader.loadClass("com.coui.appcompat.checkbox.COUICheckBox")
            clazz.getConstructor(Context::class.java).newInstance(context) as android.widget.CompoundButton
        }.getOrNull()
        if (stock != null) {
            stock.isChecked = true
            stock.isClickable = false
            stock.isFocusable = false
            return stock
        }
        return TextView(context).apply {
            text = "✓"
            textSize = 16f
            setTextColor(CouiComponentFactory.getAccentColor(context))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val size = CouiComponentFactory.dp(24, context)
            layoutParams = LayoutParams(size, size)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // FOOT_PRINT icon picker helpers
    // ──────────────────────────────────────────────────────────────────────

    /** Custom footprint image awaiting import (waiting for onActivityResult). */
    private var pendingFootPrintPick = false

    /**
     * Forwards onActivityResult from the host Activity (see [OplusInputSettingsHook]).
     * Returns true when the result was consumed.
     */
    fun onFootPrintPickResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean {
        if (requestCode != footPrintPickRequestCode || !pendingFootPrintPick) return false
        pendingFootPrintPick = false
        if (resultCode != Activity.RESULT_OK) return true
        val uri = data?.data ?: return true
        val ok = OplusInputPrefs.saveCustomFootPrint(context, uri)
        if (ok) {
            // Switches to the custom icon after a successful import.
            OplusInputPrefs.setFootPrintIcon(context, FootPrintIcon.CUSTOM.key)
            // Recreates the page to refresh the list (this page is lightweight).
            (context as? Activity)?.recreate()
        }
        return true
    }

    companion object {
        const val REQUEST_CODE_PICK_FOOT_PRINT = 0x4F50 // "OP"

        /** Currently mounted instance, used to forward Activity results (one effects page at a time). */
        @Volatile
        var activeInstance: java.lang.ref.WeakReference<OplusKeyboardEffectsView>? = null

        /** Called from hook side: forwards the SAF result to the current page. Returns whether it was consumed. */
        fun dispatchFootPrintResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean {
            return activeInstance?.get()?.onFootPrintPickResult(requestCode, resultCode, data) ?: false
        }
    }

    private val footPrintPickRequestCode = REQUEST_CODE_PICK_FOOT_PRINT

    /**
     * One footprint icon row: icon preview on the left (vector or custom PNG), title in the
     * middle, check mark on the right.
     *
     * @return (row View, check View, preview refresh lambda) — the third redraws the custom
     * row preview after import or removal.
     */
    private fun createFootPrintRow(
        context: Context,
        icon: FootPrintIcon,
        selected: Boolean,
        isCustomRow: Boolean,
        onSelected: () -> Unit
    ): Triple<View, View, () -> Unit> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = CouiComponentFactory.dp(16, context)
            val vPad = CouiComponentFactory.stockRowVerticalPadding(context)
            setPadding(hPad, vPad, hPad, vPad)
            CouiComponentFactory.applyStockPressRipple(this)
            isClickable = true
            isFocusable = true
        }

        // Icon preview (56dp square).
        val previewSize = CouiComponentFactory.dp(40, context)
        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(previewSize, previewSize).apply {
                setMargins(0, 0, CouiComponentFactory.dp(14, context), 0)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        container.addView(preview)

        val refreshPreview: () -> Unit = {
            val drawable = if (icon.isCustom) {
                // Custom: prefers the user's PNG; otherwise a faded placeholder (cat outline).
                val f = OplusInputPrefs.getCustomFootPrintFile(context)
                if (f.exists()) {
                    android.graphics.drawable.Drawable.createFromPath(f.absolutePath)
                        ?: loadModuleVectorDrawable(context, icon.drawableRes)
                } else {
                    loadModuleVectorDrawable(context, icon.drawableRes)?.mutate()?.apply { alpha = 60 }
                }
            } else {
                loadModuleVectorDrawable(context, icon.drawableRes)
            }
            preview.setImageDrawable(drawable)
        }
        refreshPreview()

        val titleTv = TextView(context).apply {
            text = icon.getTitle(context)
            CouiComponentFactory.applyStockTitleStyle(this, context)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        container.addView(titleTv)

        val checkView = createCheckIndicator(context).apply {
            visibility = if (selected) View.VISIBLE else View.GONE
        }
        container.addView(checkView)

        container.setOnClickListener { onSelected() }
        return Triple(container, checkView, refreshPreview)
    }

    /**
     * "Pick image / Remove" secondary action row (indented, secondary color, no check mark).
     */
    private fun createFootPrintActionRow(context: Context, label: String, onClick: () -> Unit): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = CouiComponentFactory.dp(16, context)
            val vPad = CouiComponentFactory.dp(10, context)
            // Left indent aligns past the icon preview.
            setPadding(hPad + CouiComponentFactory.dp(40, context) + CouiComponentFactory.dp(14, context), vPad, hPad, vPad)
            CouiComponentFactory.applyStockPressRipple(this)
            isClickable = true
            isFocusable = true
        }
        val tv = TextView(context).apply {
            text = label
            CouiComponentFactory.applyStockTitleStyle(this, context)
            // Action row keeps the accent color — the host's COUI blue instead of a hardcoded iOS blue.
            setTextColor(CouiComponentFactory.stockAccentColor(context))
        }
        container.addView(tv)
        container.setOnClickListener { onClick() }
        return container
    }

    /**
     * Loads a vector drawable from the **module APK** resources.
     * In the host process `context.resources` is the Xiaobu keyboard resource table;
     * `OplusWorkshopUiText.getModuleResources` is required.
     */
    private fun loadModuleVectorDrawable(context: Context, resId: Int): android.graphics.drawable.Drawable? {
        return try {
            OplusWorkshopUiText.getModuleResources(context)?.getDrawable(resId, null)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Starts the system image picker (SAF GetContent); the result arrives in the host Activity's
     * onActivityResult. This View is injected into DocumentEmptyActivity, so
     * registerForActivityResult is unavailable; the code uses startActivityForResult plus
     * forwarding on the Activity side (see [onFootPrintPickResult]).
     */
    private fun launchFootPrintPicker() {
        val act = context as? Activity ?: return
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
            }
            pendingFootPrintPick = true
            act.startActivityForResult(android.content.Intent.createChooser(intent, null), footPrintPickRequestCode)
        } catch (t: Throwable) {
            pendingFootPrintPick = false
            Log.w("OplusKeyboardEffects", "launchFootPrintPicker failed", t)
        }
    }
}
