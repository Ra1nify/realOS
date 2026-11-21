package io.github.ra1nify.realos.xposed.hooks.navigation.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import com.itos.coui.text.CouiTextKind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.navigation.SettingsConfig
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.InjectPage
import io.github.ra1nify.realos.ui.components.RangeSlider
import io.github.ra1nify.realos.ui.components.Text
import io.github.ra1nify.realos.ui.theme.AppTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun SettingsGestureAdvancedCompose(activity: Activity) {
    val context = activity

    val initialMin = remember(context) { SettingsConfig.gestureActiveMinRatio(context) / 100f }
    val initialMax = remember(context) { SettingsConfig.gestureActiveMaxRatio(context) / 100f }

    var activeRange by remember {
        val start = initialMin.coerceIn(0f, 0.9f)
        val end = initialMax.coerceIn(start + 0.1f, 1.0f)
        mutableStateOf<ClosedFloatingPointRange<Float>>(start..end)
    }

    var isInteracting by remember { mutableStateOf(false) }
    var interactionCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(interactionCount) {
        if (interactionCount > 0) {
            isInteracting = true
            delay(2000)
            isInteracting = false
        }
    }

    val startActivePct = (activeRange.start * 100f).roundToInt()
    val endActivePct = (activeRange.endInclusive * 100f).roundToInt()

    Box(modifier = Modifier.fillMaxSize()) {
        InjectPage(
            title = OplusWorkshopUiText.getNavSectionLogicTuning(context),
            onBack = { activity.finish() },
        ) {
            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavSectionLogicTuning(context),
                footer = OplusWorkshopUiText.getNavGestureRangeFooter(context)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = OplusWorkshopUiText.getNavGestureRangeItemTitle(context),
                            kind = CouiTextKind.ItemTitle,
                            color = AppTheme.colorScheme.primary
                        )
                        Text(
                            text = "$startActivePct% ~ $endActivePct%",
                            kind = CouiTextKind.GroupFooter,
                            color = AppTheme.colorScheme.onSurfaceSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    RangeSlider(
                        value = activeRange,
                        onValueChange = { newRange ->
                            interactionCount++
                            activeRange = newRange
                            val minVal = (newRange.start * 100f).roundToInt().coerceIn(0, 100)
                            val maxVal = (newRange.endInclusive * 100f).roundToInt().coerceIn(0, 100)
                            SettingsConfig.setGestureActiveRangeRatio(context, minVal, maxVal)
                        },
                        valueRange = 0f..1.0f,
                        minGap = 0.10f
                    )
                }
            }
        }

        // Preview overlay of the side gesture active hot zone (fades in while the slider is dragged)
        AnimatedVisibility(
            visible = isInteracting,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            GestureRangeMaskOverlay(
                activeMin = activeRange.start,
                activeMax = activeRange.endInclusive
            )
        }
    }
}

/**
 * Indicator overlay for the left/right side gesture active zones (stock ColorOS sensitivity mask style)
 */
@Composable
private fun GestureRangeMaskOverlay(
    activeMin: Float,
    activeMax: Float
) {
    val topDeadRatio = (1.0f - activeMax).coerceIn(0f, 1f)
    val bottomDeadRatio = activeMin.coerceIn(0f, 1f)
    val activeRatio = (activeMax - activeMin).coerceIn(0.001f, 1f)

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top dead zone (faint translucent black mask)
        if (topDeadRatio > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(topDeadRatio)
                    .background(Color(0x26000000))
            )
        }

        // Middle segment: active gesture zone (stock blue highlight capsules on both sides)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(activeRatio)
        ) {
            // Left side gesture active capsule
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(18.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                    .background(Color(0x664A86F7))
            )

            // Right side gesture active capsule
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(18.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(Color(0x664A86F7))
            )
        }

        // Bottom dead zone (faint translucent black mask)
        if (bottomDeadRatio > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(bottomDeadRatio)
                    .background(Color(0x26000000))
            )
        }
    }
}
