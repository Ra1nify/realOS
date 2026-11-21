package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.sound.KeyboardSoundStyle
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.sound.OplusKeyboardSoundManager
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.preference.OverlayDropdownPreference
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.preference.SliderPreference
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.ui.components.InjectPage

/**
 * Second-level configuration page for mechanical keyboard sounds (ColorOS 17 / roui conventions).
 */
@Composable
fun OplusKeyboardSoundCompose(activity: Activity) {
    val context = activity

    var soundEnabled by remember {
        mutableStateOf(OplusInputPrefs.isKeyboardSoundEnabled(context))
    }
    var selectedStyle by remember {
        mutableStateOf(KeyboardSoundStyle.fromKey(OplusInputPrefs.getKeyboardSoundStyle(context)))
    }
    var soundVolume by remember {
        mutableFloatStateOf(OplusInputPrefs.getKeyboardSoundVolume(context))
    }

    // Same as the other pages: one write after the last change, flush on exit.
    val writer = rememberSliderPrefWriter()
    DisposableEffect(Unit) {
        onDispose {
            writer.cancelAll()
            runCatching { OplusInputPrefs.setKeyboardSoundVolume(context, soundVolume) }
        }
    }

    InjectPage(
        title = OplusWorkshopUiText.getKeyboardSoundsTitle(context),
        onBack = { activity.finish() },
        actions = { KeyboardPreviewActionButton() },
    ) {
        // ── Section 1: master switch ──
        BasicComponentGroup(
            title = OplusWorkshopUiText.getSectionFeatures(context)
        ) {
            SwitchPreference(
                title = OplusWorkshopUiText.getSoundSwitchTitle(context),
                summary = OplusWorkshopUiText.getSoundSwitchSummary(context),
                checked = soundEnabled,
                onCheckedChange = { enabled ->
                    soundEnabled = enabled
                    OplusInputPrefs.setKeyboardSoundEnabled(context, enabled)
                    if (enabled) {
                        OplusKeyboardSoundManager.ensureLoaded(context, selectedStyle)
                    }
                }
            )
        }

        // ── Section 2: style and volume (expanded when the switch is on) ──
        if (soundEnabled) {
            BasicComponentGroup(
                title = OplusWorkshopUiText.getSoundStyleTitle(context)
            ) {
                // 1. Sound style single-choice menu (rmuix's OverlayDropdownPreference expresses selection with items + selectedIndex)
                OverlayDropdownPreference(
                    title = OplusWorkshopUiText.getSoundStyleTitle(context),
                    summary = selectedStyle.getSummary(context),
                    items = KeyboardSoundStyle.entries.map { it.getTitle(context) },
                    selectedIndex = KeyboardSoundStyle.entries.indexOf(selectedStyle).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        val style = KeyboardSoundStyle.entries.getOrNull(index) ?: selectedStyle
                        selectedStyle = style
                        OplusInputPrefs.setKeyboardSoundStyle(context, style.key)
                        OplusKeyboardSoundManager.ensureLoaded(context, style)
                    }
                )

                HorizontalDivider()

                // 2. Key volume slider
                SliderPreference(
                    title = OplusWorkshopUiText.getSoundVolumeTitle(context),
                    value = soundVolume,
                    valueRange = 0f..1f,
                    valueText = "${(soundVolume * 100).toInt()}%",
                    onValueChange = { vol ->
                        soundVolume = vol
                        writer.submit("sound_volume") {
                            OplusInputPrefs.setKeyboardSoundVolume(context, vol)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
