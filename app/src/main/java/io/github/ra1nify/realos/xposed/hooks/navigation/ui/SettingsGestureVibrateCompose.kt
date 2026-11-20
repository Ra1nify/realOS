package io.github.ra1nify.realos.xposed.hooks.navigation.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.navigation.SettingsConfig
import io.github.ra1nify.realos.navigation.GestureVibrationConfig
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.InjectPage
import io.github.ra1nify.realos.ui.components.preference.SliderPreference
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference

@Composable
fun SettingsGestureVibrateCompose(activity: Activity) {
    val context = activity

    var vibrateEnabled by remember {
        mutableStateOf(SettingsConfig.gestureBackVibrateEnabled(context))
    }
    var vibration by remember {
        mutableStateOf(SettingsConfig.gestureVibration(context))
    }

    InjectPage(
        title = OplusWorkshopUiText.getNavSectionHapticTuning(context),
        onBack = { activity.finish() },
    ) {
        BasicComponentGroup(
            title = OplusWorkshopUiText.getNavPageSectionFeatures(context)
        ) {
            SwitchPreference(
                title = OplusWorkshopUiText.getNavGestureVibrateTitle(context),
                summary = OplusWorkshopUiText.getNavGestureVibrateSummary(context),
                checked = vibrateEnabled,
                onCheckedChange = { checked ->
                    vibrateEnabled = checked
                    SettingsConfig.setGestureBackVibrateEnabled(context, checked)
                }
            )
        }

        if (vibrateEnabled) {
            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavVibState1(context)
            ) {
                SliderPreference(
                    title = OplusWorkshopUiText.getNavVibWeakStrength(context),
                    value = vibration.weakStrength.toFloat(),
                    valueRange = GestureVibrationConfig.STRENGTH_MIN.toFloat()..GestureVibrationConfig.STRENGTH_MAX.toFloat(),
                    valueText = "${vibration.weakStrength}",
                    onValueChange = { newVal ->
                        vibration = vibration.copy(weakStrength = newVal.toInt())
                    },
                    // Dragging only updates state; the Settings.System write is a synchronous
                    // binder call and would otherwise run once per frame.
                    onValueChangeFinished = {
                        SettingsConfig.setGestureVibration(context, vibration)
                    }
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavVibStep(context),
                    value = vibration.stepDp,
                    valueRange = GestureVibrationConfig.STEP_DP_MIN..GestureVibrationConfig.STEP_DP_MAX,
                    valueText = "${vibration.stepDp.toInt()} dp",
                    onValueChange = { newVal ->
                        vibration = vibration.copy(stepDp = newVal)
                    },
                    // Dragging only updates state; the Settings.System write is a synchronous
                    // binder call and would otherwise run once per frame.
                    onValueChangeFinished = {
                        SettingsConfig.setGestureVibration(context, vibration)
                    }
                )
                HorizontalDivider()
                SliderPreference(
                    title = OplusWorkshopUiText.getNavVibInterval(context),
                    value = vibration.intervalMs.toFloat(),
                    valueRange = GestureVibrationConfig.INTERVAL_MIN.toFloat()..GestureVibrationConfig.INTERVAL_MAX.toFloat(),
                    valueText = "${vibration.intervalMs} ms",
                    onValueChange = { newVal ->
                        vibration = vibration.copy(intervalMs = newVal.toLong())
                    },
                    // Dragging only updates state; the Settings.System write is a synchronous
                    // binder call and would otherwise run once per frame.
                    onValueChangeFinished = {
                        SettingsConfig.setGestureVibration(context, vibration)
                    }
                )
            }

            BasicComponentGroup(
                title = OplusWorkshopUiText.getNavVibState2(context)
            ) {
                SliderPreference(
                    title = OplusWorkshopUiText.getNavVibStrongStrength(context),
                    value = vibration.strongStrength.toFloat(),
                    valueRange = GestureVibrationConfig.STRENGTH_MIN.toFloat()..GestureVibrationConfig.STRENGTH_MAX.toFloat(),
                    valueText = "${vibration.strongStrength}",
                    onValueChange = { newVal ->
                        vibration = vibration.copy(strongStrength = newVal.toInt())
                    },
                    // Dragging only updates state; the Settings.System write is a synchronous
                    // binder call and would otherwise run once per frame.
                    onValueChangeFinished = {
                        SettingsConfig.setGestureVibration(context, vibration)
                    }
                )
                HorizontalDivider()
                SwitchPreference(
                    title = OplusWorkshopUiText.getNavVibTakeOver(context),
                    summary = OplusWorkshopUiText.getNavVibTakeOverSummary(context),
                    checked = vibration.takeOverStrong,
                    onCheckedChange = { checked ->
                        val updated = vibration.copy(takeOverStrong = checked)
                        vibration = updated
                        SettingsConfig.setGestureVibration(context, updated)
                    }
                )
            }
        }
    }
}
