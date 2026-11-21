package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowInsetsControllerCompat
import java.lang.ref.WeakReference
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText

/**
 * Native Liquid Glass settings view for Xiaobu Input Method (com.oplus.keyboard).
 *
 * Implemented using 3-card structure and genuine ColorOS COUI widgets and styles. Zero custom canvas drawing.
 *
 * Structure:
 * - Preview Section (interactive soft keyboard wake-up)
 * - Card 1: Core switches & sliders (Force Blur, Dark Mode Bypass, Radius, Key Opacity)
 * - Card 2: Light mode customization (Light Mask Color, Mask Alpha, Custom Bg Overlay Switch & sub-items)
 * - Card 3: Dark mode customization (Dark Mask Color, Mask Alpha, Custom Bg Overlay Switch & sub-items)
 * - Bottom Android Bot: Vector comparison background for translucent keyboard
 */
class OplusWorkshopView(context: Context) : FrameLayout(context) {

    private val scrollContent: LinearLayout
    private val bannerContainer: FrameLayout
    private val scrollView: ScrollView

    private var lightBgImageRow: CouiComponentFactory.ClickableRowHandle? = null
    private var darkBgImageRow: CouiComponentFactory.ClickableRowHandle? = null
    private var lightOverlaySwitchRow: View? = null
    private var darkOverlaySwitchRow: View? = null

    init {
        activeRef = WeakReference(this)
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
        scrollView = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setBackgroundColor(CouiComponentFactory.getBackgroundColor(context))
        }

        scrollContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setBackgroundColor(CouiComponentFactory.getBackgroundColor(context))
        }
        scrollView.addView(scrollContent)

        // Banner Slot (Warning if non-default theme)
        bannerContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        scrollContent.addView(bannerContainer)

        // ── Section 1: Card 1 Core switches & sliders ──
        // The former "effect preview input box" moved to the top bar icon (tap to open a live
        // IME preview); nothing is embedded here.
        val card1Title = CouiComponentFactory.createCategoryTitle(context, OplusWorkshopUiText.getSectionCoreMaterial(context))
        scrollContent.addView(card1Title)

        val card1 = CouiComponentFactory.createCardContainer(context)

        // 1. Bypass liquid glass limits (merged: scene-wide blur + dark-follows-system release)
        //    One switch drives both (a) the whitelist bypass and (b) the dark-mode bypass;
        //    default off.
        //    Both hot-reload: the whitelist swaps a static field at runtime, the dark bypass
        //    reads the switch on each check.
        card1.addView(
            CouiComponentFactory.createSwitchRow(
                context = context,
                title = OplusWorkshopUiText.getSwitchBypassAllLimitsTitle(context),
                summary = OplusWorkshopUiText.getSwitchBypassAllLimitsSummary(context),
                initialChecked = OplusInputPrefs.isBypassAllLimits(context),
                onCheckedChange = { enabled ->
                    OplusInputPrefs.setBypassAllLimits(context, enabled)
                    // (a) Whitelist bypass: rewrites the host static whitelist field at runtime, no restart.
                    OplusInputWhitelistBypass.refresh(context)
                    // (b) Dark bypass reads the volatile cache on each check; no live refresh needed.
                    OplusLiquidGlassHook.updateBypassAllLimits(enabled)
                }
            )
        )
        card1.addView(CouiComponentFactory.createDivider(context))

        // 2. Blur spread radius (30 ~ 300 px)
        val blurRadius = OplusInputPrefs.getBlurRadius(context)
        card1.addView(
            CouiComponentFactory.createContinuousSeekBarRow(
                context = context,
                title = OplusWorkshopUiText.getSliderBlurRadius(context),
                min = 0,
                max = 500,
                initialProgress = blurRadius,
                startLabel = "0px",
                endLabel = "500px",
                formatValue = { "${it}px" },
                onProgressChange = { value ->
                    OplusInputPrefs.setBlurRadius(context, value)
                    OplusLiquidGlassHook.updateLiveBlurRadius(value, context)
                }
            )
        )
        card1.addView(CouiComponentFactory.createDivider(context))

        // 3. Key frosted opacity (0% ~ 100%, step 10%)
        val keyAlpha = OplusInputPrefs.getKeyAlpha(context)
        card1.addView(
            CouiComponentFactory.createSectionSeekBarRow(
                context = context,
                title = OplusWorkshopUiText.getSliderKeyOpacity(context),
                min = 0,
                max = 100,
                initialProgress = (keyAlpha * 100).toInt(),
                startLabel = "0%",
                endLabel = "100%",
                step = 10,
                formatValue = { "$it%" },
                onProgressChange = { value ->
                    val alpha = value / 100f
                    OplusInputPrefs.setKeyAlpha(context, alpha)
                    OplusLiquidGlassHook.updateLiveKeyAlpha(alpha)
                }
            )
        )
        card1.addView(CouiComponentFactory.createDivider(context))

        // 4. Keyboard corner radius (bottom bar top corners, 0 ~ 40dp)
        //    Applies to docked (bottom bar) mode only; rewritten when the host passes top
        //    corners > 0, while handwriting mode (host passes 0) stays square.
        //    No light/dark split needed, so it lives in Card 1.
        val cornerRadius = OplusInputPrefs.getCornerRadius(context)
        card1.addView(
            CouiComponentFactory.createContinuousSeekBarRow(
                context = context,
                title = OplusWorkshopUiText.getSliderCornerRadius(context),
                min = 0,
                max = 40,
                initialProgress = cornerRadius,
                startLabel = "0dp",
                endLabel = "40dp",
                formatValue = { "$it dp" },
                onProgressChange = { value ->
                    OplusInputPrefs.setCornerRadius(context, value)
                    OplusLiquidGlassHook.updateLiveCornerRadius(value, context)
                }
            )
        )
        scrollContent.addView(card1)

        // ── Section 3: Card 2 Light mode customization ──
        val card2Title = CouiComponentFactory.createCategoryTitle(context, OplusWorkshopUiText.getSectionLightCustom(context))
        scrollContent.addView(card2Title)

        val card2 = CouiComponentFactory.createCardContainer(context)

        // 1. Light mask color
        val lightMaskColor = OplusInputPrefs.getLightMaskColor(context)
        lateinit var lightColorRow: CouiComponentFactory.ColorPickerRowHandle
        lightColorRow = CouiComponentFactory.createColorPickerRow(
            context = context,
            title = OplusWorkshopUiText.getLightMaskColorTitle(context),
            initialColorHex = lightMaskColor,
            onClick = {
                CouiComponentFactory.showColorPickerDialog(
                    context = context,
                    title = OplusWorkshopUiText.getColorPickerTitle(context),
                    initialHex = OplusInputPrefs.getLightMaskColor(context)
                ) { newHex ->
                    OplusInputPrefs.setLightMaskColor(context, newHex)
                    lightColorRow.setColor(newHex)
                    OplusLiquidGlassHook.updateLiveMaskAlpha(context)
                }
            }
        )
        card2.addView(lightColorRow.row)
        card2.addView(CouiComponentFactory.createDivider(context))

        // 2. Light mask alpha (0% ~ 100%, step 10%)
        val lightAlpha = OplusInputPrefs.getLightMaskAlpha(context)
        card2.addView(
            CouiComponentFactory.createSectionSeekBarRow(
                context = context,
                title = OplusWorkshopUiText.getSliderLightAlpha(context),
                min = 0,
                max = 100,
                initialProgress = (lightAlpha * 100).toInt(),
                startLabel = "0%",
                endLabel = "100%",
                step = 10,
                formatValue = { "$it%" },
                onProgressChange = { value ->
                    val alpha = value / 100f
                    OplusInputPrefs.setLightMaskAlpha(context, alpha)
                    OplusLiquidGlassHook.updateLiveMaskAlpha(context, explicitLightAlpha = alpha)
                }
            )
        )
        card2.addView(CouiComponentFactory.createDivider(context))

        // ── [Background image feature disabled] Custom background overlay (light) ──
        // The whole block below (switch / image pick / opacity) is disabled; uncomment to restore.
        // // 3. Custom background overlay (Switch) & switch-driven visibility
        // val lightBgOverlayEnabled = OplusInputPrefs.isLightBgOverlayEnabled(context)
        // val lightBgImageDivider = CouiComponentFactory.createDivider(context)
        // val lightBgImageRowHandle = CouiComponentFactory.createClickableRow(
        //     context = context,
        //     title = OplusWorkshopUiText.getBgImageTitle(context),
        //     summary = if (OplusInputPrefs.hasBgImage(context, false)) {
        //         OplusWorkshopUiText.getBgImageIsSet(context)
        //     } else {
        //         OplusWorkshopUiText.getBgImageNotSet(context)
        //     },
        //     onClick = { handleImageRowClick(false) }
        // )
        // lightBgImageRow = lightBgImageRowHandle
        //
        // val lightBgAlphaDivider = CouiComponentFactory.createDivider(context)
        // val lightBgAlpha = OplusInputPrefs.getLightBgOverlayAlpha(context)
        // val lightBgAlphaRow = CouiComponentFactory.createSectionSeekBarRow(
        //     context = context,
        //     title = OplusWorkshopUiText.getBgOverlayAlphaTitle(context),
        //     min = 0,
        //     max = 100,
        //     initialProgress = (lightBgAlpha * 100).toInt(),
        //     startLabel = "0%",
        //     endLabel = "100%",
        //     step = 10,
        //     formatValue = { "$it%" },
        //     onProgressChange = { value ->
        //         val alpha = value / 100f
        //         OplusInputPrefs.setLightBgOverlayAlpha(context, alpha)
        //         OplusLiquidGlassHook.triggerLiveRefresh(context)
        //     }
        // )
        //
        // fun updateLightBgOverlayChildrenVisibility(visible: Boolean) {
        //     val v = if (visible) View.VISIBLE else View.GONE
        //     lightBgImageDivider.visibility = v
        //     lightBgImageRowHandle.row.visibility = v
        //     lightBgAlphaDivider.visibility = v
        //     lightBgAlphaRow.visibility = v
        // }
        //
        // updateLightBgOverlayChildrenVisibility(lightBgOverlayEnabled)
        //
        // val lightOverlaySwitchView = CouiComponentFactory.createSwitchRow(
        //     context = context,
        //     title = OplusWorkshopUiText.getBgOverlaySwitchTitle(context),
        //     summary = OplusWorkshopUiText.getBgOverlaySwitchSummary(context),
        //     initialChecked = lightBgOverlayEnabled,
        //     onCheckedChange = { enabled ->
        //         OplusInputPrefs.setLightBgOverlayEnabled(context, enabled)
        //         updateLightBgOverlayChildrenVisibility(enabled)
        //         OplusLiquidGlassHook.triggerLiveRefresh(context)
        //     }
        // )
        // lightOverlaySwitchRow = lightOverlaySwitchView
        // card2.addView(lightOverlaySwitchView)
        // card2.addView(lightBgImageDivider)
        // card2.addView(lightBgImageRowHandle.row)
        // card2.addView(lightBgAlphaDivider)
        // card2.addView(lightBgAlphaRow)

        scrollContent.addView(card2)

        // ── Section 4: Card 3 Dark mode customization ──
        val card3Title = CouiComponentFactory.createCategoryTitle(context, OplusWorkshopUiText.getSectionDarkCustom(context))
        scrollContent.addView(card3Title)

        val card3 = CouiComponentFactory.createCardContainer(context)

        // 1. Dark mask color
        val darkMaskColor = OplusInputPrefs.getDarkMaskColor(context)
        lateinit var darkColorRow: CouiComponentFactory.ColorPickerRowHandle
        darkColorRow = CouiComponentFactory.createColorPickerRow(
            context = context,
            title = OplusWorkshopUiText.getDarkMaskColorTitle(context),
            initialColorHex = darkMaskColor,
            onClick = {
                CouiComponentFactory.showColorPickerDialog(
                    context = context,
                    title = OplusWorkshopUiText.getColorPickerTitle(context),
                    initialHex = OplusInputPrefs.getDarkMaskColor(context)
                ) { newHex ->
                    OplusInputPrefs.setDarkMaskColor(context, newHex)
                    darkColorRow.setColor(newHex)
                    OplusLiquidGlassHook.updateLiveMaskAlpha(context)
                }
            }
        )
        card3.addView(darkColorRow.row)
        card3.addView(CouiComponentFactory.createDivider(context))

        // 2. Dark mask alpha (0% ~ 100%, step 10%)
        val darkAlpha = OplusInputPrefs.getDarkMaskAlpha(context)
        card3.addView(
            CouiComponentFactory.createSectionSeekBarRow(
                context = context,
                title = OplusWorkshopUiText.getSliderDarkAlpha(context),
                min = 0,
                max = 100,
                initialProgress = (darkAlpha * 100).toInt(),
                startLabel = "0%",
                endLabel = "100%",
                step = 10,
                formatValue = { "$it%" },
                onProgressChange = { value ->
                    val alpha = value / 100f
                    OplusInputPrefs.setDarkMaskAlpha(context, alpha)
                    OplusLiquidGlassHook.updateLiveMaskAlpha(context, explicitDarkAlpha = alpha)
                }
            )
        )
        card3.addView(CouiComponentFactory.createDivider(context))

        // ── [Background image feature disabled] Custom background overlay (dark) ──
        // The whole block below (switch / image pick / opacity) is disabled; uncomment to restore.
        // // 3. Custom background overlay (Switch) & switch-driven visibility
        // val darkBgOverlayEnabled = OplusInputPrefs.isDarkBgOverlayEnabled(context)
        // val darkBgImageDivider = CouiComponentFactory.createDivider(context)
        // val darkBgImageRowHandle = CouiComponentFactory.createClickableRow(
        //     context = context,
        //     title = OplusWorkshopUiText.getBgImageTitle(context),
        //     summary = if (OplusInputPrefs.hasBgImage(context, true)) {
        //         OplusWorkshopUiText.getBgImageIsSet(context)
        //     } else {
        //         OplusWorkshopUiText.getBgImageNotSet(context)
        //     },
        //     onClick = { handleImageRowClick(true) }
        // )
        // darkBgImageRow = darkBgImageRowHandle
        //
        // val darkBgAlphaDivider = CouiComponentFactory.createDivider(context)
        // val darkBgAlpha = OplusInputPrefs.getDarkBgOverlayAlpha(context)
        // val darkBgAlphaRow = CouiComponentFactory.createSectionSeekBarRow(
        //     context = context,
        //     title = OplusWorkshopUiText.getBgOverlayAlphaTitle(context),
        //     min = 0,
        //     max = 100,
        //     initialProgress = (darkBgAlpha * 100).toInt(),
        //     startLabel = "0%",
        //     endLabel = "100%",
        //     step = 10,
        //     formatValue = { "$it%" },
        //     onProgressChange = { value ->
        //         val alpha = value / 100f
        //         OplusInputPrefs.setDarkBgOverlayAlpha(context, alpha)
        //         OplusLiquidGlassHook.triggerLiveRefresh(context)
        //     }
        // )
        //
        // fun updateDarkBgOverlayChildrenVisibility(visible: Boolean) {
        //     val v = if (visible) View.VISIBLE else View.GONE
        //     darkBgImageDivider.visibility = v
        //     darkBgImageRowHandle.row.visibility = v
        //     darkBgAlphaDivider.visibility = v
        //     darkBgAlphaRow.visibility = v
        // }
        //
        // updateDarkBgOverlayChildrenVisibility(darkBgOverlayEnabled)
        //
        // val darkOverlaySwitchView = CouiComponentFactory.createSwitchRow(
        //     context = context,
        //     title = OplusWorkshopUiText.getBgOverlaySwitchTitle(context),
        //     summary = OplusWorkshopUiText.getBgOverlaySwitchSummary(context),
        //     initialChecked = darkBgOverlayEnabled,
        //     onCheckedChange = { enabled ->
        //         OplusInputPrefs.setDarkBgOverlayEnabled(context, enabled)
        //         updateDarkBgOverlayChildrenVisibility(enabled)
        //         OplusLiquidGlassHook.triggerLiveRefresh(context)
        //     }
        // )
        // darkOverlaySwitchRow = darkOverlaySwitchView
        // card3.addView(darkOverlaySwitchView)
        // card3.addView(darkBgImageDivider)
        // card3.addView(darkBgImageRowHandle.row)
        // card3.addView(darkBgAlphaDivider)
        // card3.addView(darkBgAlphaRow)

        scrollContent.addView(card3)

        // ── Bottom Android Bot: Vector comparison background for translucent keyboard ──
        val botContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = CouiComponentFactory.dp(16, context)
            val bottomScrollPadding = CouiComponentFactory.dp(180, context)
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

        refreshBanner()

        val pageRoot = CouiComponentFactory.createOfficialPageRoot(
            context = context,
            title = OplusWorkshopUiText.getLiquidGlassTitle(context),
            contentScrollView = scrollView,
            onBackClick = { (context as? Activity)?.finish() },
            showKeyboardPreview = true
        )
        addView(pageRoot, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Disables the host Force Dark pass on this page: it carries its own light/dark colors,
        // otherwise the framework inverts the night values again (all black).
        CouiComponentFactory.disableForceDarkRecursively(this)
    }

    private fun handleImageRowClick(isDark: Boolean) {
        val activity = context as? Activity ?: return
        val hasImg = OplusInputPrefs.hasBgImage(context, isDark)
        if (hasImg) {
            CouiComponentFactory.showCustomActionDialog(
                context = context,
                title = OplusWorkshopUiText.getClearBgImageDialogTitle(context),
                message = OplusWorkshopUiText.getClearBgImageDialogMsg(context),
                positiveText = OplusWorkshopUiText.getDialogReplace(context),
                onPositive = {
                    launchSafImagePicker(activity, isDark)
                },
                neutralText = OplusWorkshopUiText.getDialogClear(context),
                onNeutral = {
                    OplusInputPrefs.deleteBgImage(context, isDark)
                    updateImageSummary(isDark)
                    OplusLiquidGlassHook.triggerLiveRefresh(context)
                },
                negativeText = OplusWorkshopUiText.getDialogCancel(context),
                onNegative = null
            )
        } else {
            launchSafImagePicker(activity, isDark)
        }
    }

    private fun launchSafImagePicker(activity: Activity, isDark: Boolean) {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            val reqCode = if (isDark) REQUEST_CODE_PICK_BG_DARK else REQUEST_CODE_PICK_BG_LIGHT
            activity.startActivityForResult(intent, reqCode)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch SAF image picker: ${t.message}", t)
        }
    }

    fun syncBgOverlaySwitch(isDark: Boolean, enabled: Boolean) {
        post {
            val targetSwitchRow = if (isDark) darkOverlaySwitchRow else lightOverlaySwitchRow
            targetSwitchRow?.let {
                CouiComponentFactory.setSwitchRowChecked(it, enabled)
            }
        }
    }

    fun updateImageSummary(isDark: Boolean) {
        post {
            val summary = if (OplusInputPrefs.hasBgImage(context, isDark)) {
                OplusWorkshopUiText.getBgImageIsSet(context)
            } else {
                OplusWorkshopUiText.getBgImageNotSet(context)
            }
            if (isDark) {
                darkBgImageRow?.setSummary(summary)
            } else {
                lightBgImageRow?.setSummary(summary)
            }
        }
    }

    private fun refreshBanner() {
        bannerContainer.removeAllViews()
        val isInner = ThemeManagerHelper.isInnerTheme(context)
        val isFollowSys = ThemeManagerHelper.isFollowSystem(context)

        if (!isInner || !isFollowSys) {
            val banner = CouiComponentFactory.createBannerCard(
                context = context,
                iconEmoji = "⚠️",
                message = OplusWorkshopUiText.getBannerRestrictionWarn(context),
                buttonText = OplusWorkshopUiText.getBannerResetButton(context),
                onButtonClick = {
                    val ok = ThemeManagerHelper.applyDefaultTheme(context)
                    if (ok) {
                        refreshBanner()
                    }
                }
            )
            bannerContainer.addView(banner)
            // This method runs again after init (via the "reset theme" button); newly added
            // views default to forceDarkAllowed=true, so the host Force Dark pass is disabled
            // again — otherwise the warning card inverts a second time in dark mode.
            CouiComponentFactory.disableForceDarkRecursively(bannerContainer)
        }
    }

    companion object {
        private const val TAG = "OplusWorkshopView"
        const val REQUEST_CODE_PICK_BG_LIGHT = 9101
        const val REQUEST_CODE_PICK_BG_DARK = 9102

        @Volatile
        private var activeRef: WeakReference<OplusWorkshopView>? = null

        fun onBgImageSaved(isDark: Boolean) {
            activeRef?.get()?.let { view ->
                view.updateImageSummary(isDark)
                view.syncBgOverlaySwitch(isDark, true)
            }
        }
    }
}

typealias OplusLiquidGlassView = OplusWorkshopView
