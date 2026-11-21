package io.github.ra1nify.realos.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * ColorOS 17 press-scale modifier.
 *
 * Uses `animateFloatAsState` plus spring specs; parameter semantics match rmuix, with separate
 * press and release springs:
 * - press: default damping 0.82 / stiffness 1600, scale target [pressedScale];
 * - release or cancel: default damping 0.65 / stiffness 380, back to 1f.
 *
 * The modifier draws no ripple. [onClick] handles clicks and [haptic] handles haptics.
 *
 * @param enabled false leaves the Modifier unchanged (no scale, no click handling).
 * @param pressedScale Scale while pressed; 1f disables visual feedback.
 * @param pressDampingRatio Press animation damping ratio.
 * @param pressStiffness Press animation stiffness.
 * @param releaseDampingRatio Release animation damping ratio (lower values bounce more).
 * @param releaseStiffness Release animation stiffness.
 * @param haptic Haptic type on click; null disables haptics.
 * @param onClick Click callback; null keeps press feedback only.
 */
@Suppress("ktlint:compose:modifier-composed-check")
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    pressDampingRatio: Float = 0.82f,
    pressStiffness: Float = 1600f,
    releaseDampingRatio: Float = 0.65f,
    releaseStiffness: Float = 380f,
    haptic: HapticFeedbackType? = null,
    onClick: (() -> Unit)? = null,
): Modifier =
    composed {
        if (!enabled) return@composed this

        var pressed by remember { mutableStateOf(false) }
        val scale by
            animateFloatAsState(
                targetValue = if (pressed) pressedScale else 1f,
                animationSpec =
                    spring(
                        dampingRatio = if (pressed) pressDampingRatio else releaseDampingRatio,
                        stiffness = if (pressed) pressStiffness else releaseStiffness,
                    ),
                label = "realosPressableScale",
            )
        val hapticFeedback = LocalHapticFeedback.current
        val currentHaptic by rememberUpdatedState(haptic)
        val currentOnClick by rememberUpdatedState(onClick)

        val clickModifier =
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    currentHaptic?.let { hapticFeedback.performHapticFeedback(it) }
                    currentOnClick?.invoke()
                }
            } else {
                Modifier
            }

        this
            .scale(scale)
            .pointerInput(enabled) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    try {
                        // Keeps the pressed scale while any pointer is down; release, drag-out and cancel reset it.
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                    } finally {
                        pressed = false
                    }
                }
            }.then(clickModifier)
    }
