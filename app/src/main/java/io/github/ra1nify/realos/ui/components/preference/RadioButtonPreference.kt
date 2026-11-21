package io.github.ra1nify.realos.ui.components.preference

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Item
import com.itos.coui.radiobutton.CouiRadioButtonColors
import com.itos.coui.radiobutton.RadioButton
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Single-row radio preference.
 *
 * rmuix `RadioButtonPreference` facade over coui: the row uses coui [Item], the dot uses
 * coui [RadioButton] (coui RadioButton only draws; the whole row handles clicks).
 *
 * Signature differences from rmuix (no call site uses these parameters):
 * - `colors`: rmuix `RadioButtonPreferenceColors` depends on `BasicComponentColors`; this
 *   package rebuilds the type with [Color] fields;
 * - `radioButtonColors`: now coui [CouiRadioButtonColors];
 * - `holdDownState`: coui `Item` has built-in press feedback; the parameter is kept and unused.
 *
 * @param title Title text.
 * @param selected Whether the option is selected.
 * @param onClick Click callback; `null` makes the row non-clickable.
 * @param modifier Modifier applied to the whole row.
 * @param summary Summary text.
 * @param colors Title/summary colors in the selected and unselected states.
 * @param radioButtonColors Radio dot colors; `null` uses the coui theme defaults.
 * @param startAction Leading row content.
 * @param endActions Trailing content before the radio dot.
 * @param radioButtonLocation Radio dot position: row end or row start.
 * @param bottomAction Bottom row content rendered below the row with the row padding; coui `Item` has no bottom slot.
 * @param insideMargin Row padding; defaults to the coui row metrics (16dp horizontal, 10dp vertical).
 * @param holdDownState rmuix press-state hint; unused, coui `Item` manages its own press state.
 * @param enabled Whether the row is interactive.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun RadioButtonPreference(
    title: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    summary: String? = null,
    colors: RadioButtonPreferenceColors = RadioButtonPreferenceDefaults.radioButtonPreferenceColors(),
    radioButtonColors: CouiRadioButtonColors? = null,
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
    radioButtonLocation: RadioButtonLocation = RadioButtonLocation.End,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = PreferenceInsideMargin,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val resolvedTitleColor = if (selected) colors.selectedTitleColor else colors.titleColor
    val resolvedSummaryColor = if (selected) colors.selectedSummaryColor else colors.summaryColor
    val layoutDirection = LocalLayoutDirection.current
    val summaryText = summary
    val summarySlot: (@Composable () -> Unit)? =
        if (summaryText == null) {
            null
        } else {
            { PreferenceSummaryText(summaryText, resolvedSummaryColor) }
        }

    // rmuix behavior: commits the click only when enabled; the haptic matches the post-click state.
    val rowClick: (() -> Unit)? =
        onClick?.let {
            {
                if (enabled) {
                    currentOnClick?.invoke()
                    currentHapticFeedback.performHapticFeedback(
                        if (selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                    )
                }
            }
        }

    val radioButton: @Composable () -> Unit = {
        PreferenceRadioButton(
            selected = selected,
            enabled = enabled,
            colors = radioButtonColors,
        )
    }
    val hasStartRadioButton = radioButtonLocation == RadioButtonLocation.Start
    val itemIcon: (@Composable () -> Unit)? =
        when {
            hasStartRadioButton && startAction != null -> {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        radioButton()
                        Spacer(modifier = Modifier.width(5.dp))
                        startAction.invoke()
                    }
                }
            }

            hasStartRadioButton -> radioButton
            startAction != null -> startAction
            else -> null
        }
    // With both a start dot and custom content, the icon width is unconstrained; Item sizes it to content.
    val iconSize = if (hasStartRadioButton && startAction != null) Dp.Unspecified else 24.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Item(
            modifier = Modifier.fillMaxWidth(),
            title = { PreferenceTitleText(title, resolvedTitleColor) },
            summary = summarySlot,
            icon = itemIcon,
            iconSize = iconSize,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    endActions?.invoke(this)
                    if (radioButtonLocation == RadioButtonLocation.End) {
                        radioButton()
                    }
                }
            },
            onClick = rowClick,
            enabled = enabled,
            contentPadding = insideMargin,
        )
        bottomAction?.let { content ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = insideMargin.calculateStartPadding(layoutDirection),
                            end = insideMargin.calculateEndPadding(layoutDirection),
                            bottom = insideMargin.calculateBottomPadding(),
                        ),
            ) {
                content()
            }
        }
    }
}

/** coui radio dot: uses the passed colors when non-null; otherwise coui derives them from the theme. */
@Composable
private fun PreferenceRadioButton(
    selected: Boolean,
    enabled: Boolean,
    colors: CouiRadioButtonColors?,
) {
    if (colors == null) {
        RadioButton(selected = selected, enabled = enabled)
    } else {
        RadioButton(selected = selected, enabled = enabled, colors = colors)
    }
}

/** Title and summary colors in the selected/unselected states. */
@Immutable
data class RadioButtonPreferenceColors(
    val titleColor: Color,
    val selectedTitleColor: Color,
    val summaryColor: Color,
    val selectedSummaryColor: Color,
)

/**
 * [RadioButtonPreference] default colors.
 *
 * Unselected uses the theme primary/secondary text colors; selected uses the theme accent color (matches rmuix).
 */
object RadioButtonPreferenceDefaults {
    /** Builds a default color set; each color is overridable, `null` resolves the realOS theme color. */
    @Composable
    fun radioButtonPreferenceColors(
        titleColor: Color? = null,
        selectedTitleColor: Color? = null,
        summaryColor: Color? = null,
        selectedSummaryColor: Color? = null,
    ): RadioButtonPreferenceColors {
        val theme = AppTheme.colorScheme
        return RadioButtonPreferenceColors(
            titleColor = titleColor ?: theme.onSurface,
            selectedTitleColor = selectedTitleColor ?: theme.primary,
            summaryColor = summaryColor ?: theme.onSurfaceSecondary,
            selectedSummaryColor = selectedSummaryColor ?: theme.primary,
        )
    }
}

/** Radio dot position within the row. */
enum class RadioButtonLocation {
    /** Row start. */
    Start,

    /** Row end. */
    End,
}
