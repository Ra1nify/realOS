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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.layout.Item
import com.itos.coui.slider.CouiSliderColors
import com.itos.coui.slider.Slider
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Slider preference with a title, summary and value text.
 *
 * rmuix `SliderPreference` facade over coui: the title row uses coui [Item], the slider uses
 * coui [Slider] in its own area below the title row, at the rmuix `bottomAction` position.
 *
 * Signature differences from rmuix (no call site uses these parameters):
 * - `titleColor` / `summaryColor`: rmuix `BasicComponentColors` has no coui equivalent;
 *   now nullable [Color], `null` resolves the realOS theme color by enabled state;
 * - `sliderColors`: now coui [CouiSliderColors] (coui `Slider` accepts nullable colors);
 * - `onClick`: kept; non-null adds a trailing chevron and makes the row clickable;
 * - `showKeyPoints`: mapped to coui `Slider` `showTickMarks` (evenly spaced ticks);
 * - `startLabel` / `endLabel` / `labelColor`: kept; the slider draws 14sp labels at both ends;
 * - `holdDownState`: coui `Item` has built-in press feedback; the parameter is kept and unused.
 *
 * Parameters removed from the signature, with no coui equivalent:
 * - `reverseDirection`: coui `Slider` only mirrors with the layout direction (RTL), with no arbitrary reverse switch;
 * - `sliderHeight`: coui `Slider` has a fixed 48dp minimum height, not settable by the caller;
 * - `hapticEffect`: coui `Slider` has built-in segment haptics, with no replaceable haptic parameter;
 * - `keyPoints` / `magnetThreshold`: coui only has evenly spaced ticks (`steps` + `showTickMarks`),
 *   no custom key point list or magnet threshold.
 *
 * @param value Current value; values outside [valueRange] are clamped by coui `Slider`.
 * @param onValueChange Value change callback.
 * @param modifier Modifier applied to the whole row.
 * @param title Title text; `null` draws no title.
 * @param titleColor Title color; `null` uses the theme primary text color.
 * @param summary Summary text.
 * @param summaryColor Summary color; `null` uses the theme secondary text color.
 * @param startAction Leading row content.
 * @param valueText Trailing current value text; `null` hides it.
 * @param endActions Trailing content after the value text.
 * @param bottomAction Extra content above the slider.
 * @param onClick Whole-row click callback; non-null adds a trailing chevron.
 * @param holdDownState rmuix press-state hint; unused, coui `Item` manages its own press state.
 * @param enabled Whether the row is interactive.
 * @param valueRange Slider value range.
 * @param steps Discrete step count in the value range; 0 means continuous values.
 * @param onValueChangeFinished Drag/click end callback.
 * @param sliderColors Slider colors; `null` uses the coui theme defaults.
 * @param showKeyPoints Whether to show evenly spaced tick marks (maps to coui `showTickMarks`; use with `steps`).
 * @param startLabel Slider start label.
 * @param endLabel Slider end label.
 * @param labelColor Both label colors; `null` uses the theme secondary text color.
 * @param insideMargin Row padding; defaults to the coui row metrics (16dp horizontal, 10dp vertical).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SliderPreference(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color? = null,
    summary: String? = null,
    summaryColor: Color? = null,
    startAction: @Composable (() -> Unit)? = null,
    valueText: String? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    sliderColors: CouiSliderColors? = null,
    showKeyPoints: Boolean = false,
    startLabel: String? = null,
    endLabel: String? = null,
    labelColor: Color? = null,
    insideMargin: PaddingValues = PreferenceInsideMargin,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = insideMargin.calculateStartPadding(layoutDirection)
    val endPadding = insideMargin.calculateEndPadding(layoutDirection)
    val bottomPadding = insideMargin.calculateBottomPadding()
    val resolvedTitleColor = titleColor ?: preferenceTitleColor(enabled)
    val resolvedSummaryColor = summaryColor ?: preferenceSummaryColor(enabled)
    val resolvedLabelColor = labelColor ?: preferenceSummaryColor(enabled)
    val summaryText = summary
    val summarySlot: (@Composable () -> Unit)? =
        if (summaryText == null) {
            null
        } else {
            { PreferenceSummaryText(summaryText, resolvedSummaryColor) }
        }

    Column(modifier = modifier.fillMaxWidth()) {
        // Title row: coui Item row height and padding; bottom padding is 0, the slider area below supplies it.
        Item(
            modifier = Modifier.fillMaxWidth(),
            title = {
                title?.let { PreferenceTitleText(it, resolvedTitleColor) }
            },
            summary = summarySlot,
            icon = startAction,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    valueText?.let { PreferenceValueText(it, resolvedSummaryColor) }
                    endActions?.invoke(this)
                    if (onClick != null) {
                        PreferenceChevron(
                            enabled = enabled,
                            modifier = Modifier.padding(start = JumpPreferenceDefaults.ArrowMarginStart),
                        )
                    }
                }
            },
            onClick = if (enabled) onClick else null,
            enabled = enabled,
            contentPadding =
                PaddingValues(
                    start = startPadding,
                    top = insideMargin.calculateTopPadding(),
                    end = endPadding,
                    bottom = 0.dp,
                ),
        )

        // Slider area: below the title row, aligned to the horizontal row padding; the 8dp top spacer matches the rmuix BasicComponent bottom slot spacing.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = startPadding, end = endPadding),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            bottomAction?.invoke()
            SliderWithLabels(
                startLabel = startLabel,
                endLabel = endLabel,
                labelColor = resolvedLabelColor,
            ) {
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    value = value,
                    onValueChange = { currentOnValueChange(it) },
                    colors = sliderColors,
                    valueRange = valueRange,
                    steps = steps,
                    showTickMarks = showKeyPoints,
                    enabled = enabled,
                    onValueChangeFinished = { currentOnValueChangeFinished?.invoke() },
                )
            }
            Spacer(modifier = Modifier.height(bottomPadding))
        }
    }
}

/**
 * Draws optional labels at both slider ends, matching rmuix `SliderWithLabels`:
 * labels are vertically centered, the slider takes the remaining width; with no labels the slider fills the row.
 */
@Composable
private fun SliderWithLabels(
    startLabel: String?,
    endLabel: String?,
    labelColor: Color,
    slider: @Composable () -> Unit,
) {
    if (startLabel == null && endLabel == null) {
        slider()
        return
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        startLabel?.let {
            Text(
                modifier = Modifier.padding(end = SliderPreferenceDefaults.LabelPadding),
                text = it,
                style =
                    AppTheme.textStyles.subtitle.copy(
                        fontSize = SliderPreferenceDefaults.LabelTextSize,
                        fontWeight = FontWeight.Normal,
                    ),
                color = labelColor,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            slider()
        }
        endLabel?.let {
            Text(
                modifier = Modifier.padding(start = SliderPreferenceDefaults.LabelPadding),
                text = it,
                style =
                    AppTheme.textStyles.subtitle.copy(
                        fontSize = SliderPreferenceDefaults.LabelTextSize,
                        fontWeight = FontWeight.Normal,
                    ),
                color = labelColor,
            )
        }
    }
}

/** [SliderPreference] end label default sizes. */
object SliderPreferenceDefaults {
    /** Label text size; matches rmuix `SliderPreferenceDefaults.LabelTextSize`. */
    val LabelTextSize = 14.sp

    /** Gap between a label and the slider track; matches rmuix `SliderPreferenceDefaults.LabelPadding`. */
    val LabelPadding = 10.dp

    /** Default label color: theme secondary text color. */
    @Composable
    fun labelColor(): Color = AppTheme.colorScheme.onSurfaceSecondary
}
