package io.github.ra1nify.realos.ui.components.preference

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Item
import io.github.ra1nify.realos.ui.theme.AppTheme

/**
 * Preference row with a trailing chevron (jump semantics).
 *
 * rmuix `JumpPreference` facade over coui: the row uses coui [Item], the chevron uses the
 * ColorOS `coui_btn_next` stroke geometry (12dp × 24dp, 1.4dp round-cap stroke).
 *
 * Signature differences from rmuix (no call site uses these parameters):
 * - `titleColor` / `summaryColor` / `assignmentColor` / `statusTextColor`: rmuix
 *   `BasicComponentColors` has no coui equivalent; now nullable [Color];
 * - `iconBadge` / `iconBadgeCount` / `endBadge`: coui `Item` has no badge slot, removed
 *   (only this component used them in rmuix; no call site uses them);
 * - `holdDownState`: coui `Item` has built-in press feedback; the parameter is kept and unused.
 *
 * @param title Title text.
 * @param modifier Modifier applied to the whole row.
 * @param titleColor Title color; `null` uses the theme primary text color.
 * @param summary Summary text.
 * @param summaryColor Summary color; `null` uses the theme secondary text color.
 * @param startAction Leading row content.
 * @param endActions Trailing content before the chevron.
 * @param bottomAction Bottom row content rendered below the row with the row padding; coui `Item` has no bottom slot.
 * @param insideMargin Row padding; defaults to the coui row metrics (16dp horizontal, 10dp vertical).
 * @param onClick Whole-row click callback; `null` makes the row non-clickable.
 * @param holdDownState rmuix press-state hint; unused, coui `Item` manages its own press state.
 * @param enabled Whether the row is interactive.
 * @param assignment Third-line text below the summary.
 * @param assignmentColor Third-line text color; `null` uses the theme accent color (matches the ColorOS blue assignment line).
 * @param statusText Trailing status text, before the chevron and [endActions].
 * @param statusTextColor Status text color; `null` uses the summary color.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun JumpPreference(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    summary: String? = null,
    summaryColor: Color? = null,
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = PreferenceInsideMargin,
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    assignment: String? = null,
    assignmentColor: Color? = null,
    statusText: String? = null,
    statusTextColor: Color? = null,
) {
    val resolvedTitleColor = titleColor ?: preferenceTitleColor(enabled)
    val resolvedSummaryColor = summaryColor ?: preferenceSummaryColor(enabled)
    val resolvedAssignmentColor = assignmentColor ?: AppTheme.colorScheme.primary
    val resolvedStatusColor = statusTextColor ?: resolvedSummaryColor
    val layoutDirection = LocalLayoutDirection.current
    val summaryText = summary
    val assignmentText = assignment
    // Summary and assignment text share one column; assignment renders below summary.
    val summarySlot: (@Composable () -> Unit)? =
        if (summaryText == null && assignmentText == null) {
            null
        } else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    summaryText?.let { PreferenceSummaryText(it, resolvedSummaryColor) }
                    assignmentText?.let { PreferenceSummaryText(it, resolvedAssignmentColor) }
                }
            }
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Item(
            modifier = Modifier.fillMaxWidth(),
            title = { PreferenceTitleText(title, resolvedTitleColor) },
            summary = summarySlot,
            icon = startAction,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    statusText?.takeIf { it.isNotBlank() }?.let {
                        PreferenceValueText(it, resolvedStatusColor)
                    }
                    endActions.invoke(this)
                    PreferenceChevron(
                        enabled = enabled,
                        modifier = Modifier.padding(start = JumpPreferenceDefaults.ArrowMarginStart),
                    )
                }
            },
            onClick = onClick,
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

/**
 * Trailing chevron: ColorOS `coui_btn_next_normal.xml` stroke geometry
 * (12dp × 24dp viewport, 1.4dp round-cap stroke), mirrored horizontally in RTL.
 */
@Composable
internal fun PreferenceChevron(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val color =
        if (enabled) {
            AppTheme.colorScheme.onSurfaceVariantSummary
        } else {
            AppTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        }
    val layoutDirection = LocalLayoutDirection.current
    // COUI `coui_next_filled` geometry, redrawn locally: COUI icons resolve a drawable from
    // LocalContext resources, which a host process does not expose for the module APK.
    Canvas(modifier = modifier.size(width = 12.dp, height = 24.dp)) {
        val scaleX = size.width / 12f
        val scaleY = size.height / 24f
        val mirrored = layoutDirection == LayoutDirection.Rtl

        fun x(value: Float): Float = if (mirrored) size.width - value * scaleX else value * scaleX

        fun y(value: Float): Float = value * scaleY

        val path =
            Path().apply {
                moveTo(x(5f), y(6f))
                lineTo(x(10.646f), y(11.646f))
                cubicTo(
                    x(10.842f),
                    y(11.842f),
                    x(10.842f),
                    y(12.158f),
                    x(10.646f),
                    y(12.354f),
                )
                lineTo(x(5f), y(18f))
            }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Trailing chevron colors; `color(enabled)` resolves the color by enabled state. */
@Immutable
data class EndActionColors(
    private val color: Color,
    private val disabledColor: Color,
) {
    /** Returns the chevron color by enabled state. */
    @Stable
    internal fun color(enabled: Boolean): Color = if (enabled) color else disabledColor
}

/** [JumpPreference] default sizes and colors. */
object JumpPreferenceDefaults {
    /** Chevron start margin; matches rmuix `JumpPreferenceDefaults.ArrowMarginStart` (COUI 4dp). */
    val ArrowMarginStart = 4.dp

    /** Default chevron colors: neutral 26%/30% when enabled, neutral 8% when disabled. */
    @Composable
    fun endActionColors(): EndActionColors =
        EndActionColors(
            color = AppTheme.colorScheme.onSurfaceVariantSummary,
            disabledColor = AppTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        )
}
