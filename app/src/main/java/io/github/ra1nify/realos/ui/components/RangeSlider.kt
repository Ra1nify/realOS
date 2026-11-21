package io.github.ra1nify.realos.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.RangeSlider as Material3RangeSlider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * realOS [RangeSlider] facade.
 *
 * coui has no two-thumb range component; forwards Material3 [Material3RangeSlider] with the rmuix
 * parameter shape. `minGap` (minimum distance between thumbs) is enforced in `onValueChange`.
 *
 * rmuix `colors`, `height`, `trackHeight`, `showSpotLight`, `showKeyPoints`, `keyPoints` and
 * `magnetThreshold` appearance parameters are absent; realOS call sites use
 * `value`/`onValueChange`/`valueRange`/`minGap`.
 *
 * @param minGap Minimum difference between the two values, in `valueRange` units.
 */
@Composable
@Suppress("DEPRECATION")
fun RangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    minGap: Float = 0f,
) {
    require(minGap >= 0f) { "minGap must be non-negative" }
    require(minGap <= valueRange.endInclusive - valueRange.start) {
        "minGap must not exceed the valueRange span"
    }
    Material3RangeSlider(
        value = value,
        onValueChange = { newRange ->
            if (minGap == 0f) {
                onValueChange(newRange)
            } else {
                // Thumbs cannot cross or swap; each end clamps against the other.
                val start =
                    newRange.start.coerceIn(
                        valueRange.start,
                        (newRange.endInclusive - minGap).coerceAtLeast(valueRange.start),
                    )
                val end =
                    newRange.endInclusive.coerceIn(
                        (start + minGap).coerceAtMost(valueRange.endInclusive),
                        valueRange.endInclusive,
                    )
                onValueChange(start..end)
            }
        },
        modifier = modifier.defaultMinSize(minWidth = 0.dp),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
    )
}
