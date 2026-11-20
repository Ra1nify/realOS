package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.WindowInsetsControllerCompat
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText

/**
 * Native Feature Enhancements view for Xiaobu Input Method (com.oplus.keyboard).
 *
 * Implemented using genuine ColorOS COUI widgets and styles. Zero custom canvas drawing.
 */
class OplusEnhancementsView(context: Context) : FrameLayout(context) {

    init {
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

        // ── Section 1: Advanced ──
        val sectionTitle = CouiComponentFactory.createCategoryTitle(context, OplusWorkshopUiText.getSectionAdvanced(context))
        scrollContent.addView(sectionTitle)

        // ── Card 1: Autofill Switches ──
        val card1 = CouiComponentFactory.createCardContainer(context)
        val isNativelyDeclared = OplusInputPrefs.isInlineSuggestionsNativelyDeclared(context)
        val autofillSummary = if (isNativelyDeclared) {
            OplusWorkshopUiText.getAutofillSwitchNativeSummary(context)
        } else {
            OplusWorkshopUiText.getAutofillSwitchSummary(context)
        }
        val isAutofillActive = if (isNativelyDeclared) true else OplusInputPrefs.isAutofillEnabled(context)

        card1.addView(
            CouiComponentFactory.createSwitchRow(
                context = context,
                title = OplusWorkshopUiText.getAutofillSwitchTitle(context),
                summary = autofillSummary,
                initialChecked = isAutofillActive,
                enabled = !isNativelyDeclared,
                onCheckedChange = { enabled ->
                    OplusInputPrefs.setAutofillEnabled(context, enabled)
                }
            )
        )
        card1.addView(CouiComponentFactory.createDivider(context))
        card1.addView(
            CouiComponentFactory.createSwitchRow(
                context = context,
                title = OplusWorkshopUiText.getAutofillButtonSwitchTitle(context),
                summary = OplusWorkshopUiText.getAutofillButtonSwitchSummary(context),
                initialChecked = OplusInputPrefs.isAutofillButtonEnabled(context),
                onCheckedChange = { enabled ->
                    OplusInputPrefs.setAutofillButtonEnabled(context, enabled)
                }
            )
        )
        scrollContent.addView(card1)

        val pageRoot = CouiComponentFactory.createOfficialPageRoot(
            context = context,
            title = OplusWorkshopUiText.getEnhancementsTitle(context),
            contentScrollView = scrollView,
            onBackClick = { (context as? Activity)?.finish() }
        )
        addView(pageRoot, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Disables the host Force Dark pass on this page.
        CouiComponentFactory.disableForceDarkRecursively(this)
    }
}
