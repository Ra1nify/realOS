package io.github.ra1nify.realos.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.restart.RestartPolicies
import io.github.ra1nify.realos.ui.components.SpecPageScaffold
import io.github.ra1nify.realos.opluskeyboard.KeyboardPagesContract
import io.github.ra1nify.realos.opluskeyboard.OplusKeyboardManager
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.Icon
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.ui.components.OverlayDialog

@Composable
fun KeyboardDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardPkg = remember {
        OplusKeyboardManager.getInstalledKeyboardPackage(context) ?: "com.oplus.keyboard"
    }

    var liquidGlassEnabled by remember {
        mutableStateOf(OplusKeyboardManager.isLiquidGlassEnabled(context, keyboardPkg))
    }
    var keyboardEffectsEnabled by remember {
        mutableStateOf(OplusKeyboardManager.isKeyboardEffectsEnabled(context, keyboardPkg))
    }
    var keyboardSoundEnabled by remember {
        mutableStateOf(OplusKeyboardManager.isKeyboardSoundEnabled(context, keyboardPkg))
    }
    var enhancementsEnabled by remember {
        mutableStateOf(OplusKeyboardManager.isEnhancementsEnabled(context, keyboardPkg))
    }

    var errorDialogMessage by remember { mutableStateOf<String?>(null) }

    fun handleLaunch(pageType: String) {
        when (val result = OplusKeyboardManager.launchParasiticPage(context, pageType)) {
            is OplusKeyboardManager.LaunchResult.Success -> {
                // Success
            }
            is OplusKeyboardManager.LaunchResult.Failed -> {
                errorDialogMessage = result.message
            }
        }
    }

    // Shortcut switch semantics: a row tap enables when off and opens the subpage when on.
    // The switch itself toggles independently.
    val setLiquidGlass: (Boolean) -> Unit = { enabled ->
        liquidGlassEnabled = enabled
        OplusKeyboardManager.setLiquidGlassEnabled(context, keyboardPkg, enabled)
    }
    val setKeyboardEffects: (Boolean) -> Unit = { enabled ->
        keyboardEffectsEnabled = enabled
        OplusKeyboardManager.setKeyboardEffectsEnabled(context, keyboardPkg, enabled)
    }
    val setKeyboardSound: (Boolean) -> Unit = { enabled ->
        keyboardSoundEnabled = enabled
        OplusKeyboardManager.setKeyboardSoundEnabled(context, keyboardPkg, enabled)
    }
    val setEnhancements: (Boolean) -> Unit = { enabled ->
        enhancementsEnabled = enabled
        OplusKeyboardManager.setEnhancementsEnabled(context, keyboardPkg, enabled)
    }

    SpecPageScaffold(
        title = rememberHostAppName(keyboardPkg, stringResource(R.string.oplus_input_app_name)),
        onNavigateBack = onNavigateBack,
        restartPolicy = RestartPolicies.keyboard(keyboardPkg),
        modifier = modifier,
        restartContentDescription = stringResource(R.string.oplus_input_restart_action),
        tipsTitle = stringResource(R.string.oplus_input_tips_title),
        tipsMessage = stringResource(R.string.oplus_input_tips_message)
    ) {
        // Core feature list card
        BasicComponentGroup(
            title = stringResource(R.string.oplus_input_section_core_features),
            footer = stringResource(R.string.oplus_input_host_app_footer)
        ) {
            // 1. Liquid glass
            SwitchPreference(
                title = stringResource(R.string.oplus_input_liquid_glass_title),
                summary = stringResource(R.string.oplus_input_liquid_glass_summary),
                shortcutHint = stringResource(R.string.oplus_input_liquid_glass_hint),
                startAction = { Icon(imageVector = Icons.Outlined.ColorLens, contentDescription = null) },
                checked = liquidGlassEnabled,
                onCheckedChange = setLiquidGlass,
                onClick = {
                    if (liquidGlassEnabled) {
                        handleLaunch(KeyboardPagesContract.PAGE_LIQUID_GLASS)
                    } else {
                        setLiquidGlass(true)
                    }
                }
            )

            HorizontalDivider()

            // 2. Key light effects
            SwitchPreference(
                title = stringResource(R.string.oplus_input_keyboard_effects_title),
                summary = stringResource(R.string.oplus_input_keyboard_effects_summary),
                shortcutHint = stringResource(R.string.oplus_input_keyboard_effects_hint),
                startAction = { Icon(imageVector = Icons.Outlined.AutoAwesome, contentDescription = null) },
                checked = keyboardEffectsEnabled,
                onCheckedChange = setKeyboardEffects,
                onClick = {
                    if (keyboardEffectsEnabled) {
                        handleLaunch(KeyboardPagesContract.PAGE_KEYBOARD_EFFECTS)
                    } else {
                        setKeyboardEffects(true)
                    }
                }
            )

            HorizontalDivider()

            // 3. Keyboard sounds
            SwitchPreference(
                title = stringResource(R.string.oplus_input_keyboard_sounds_title),
                summary = stringResource(R.string.oplus_input_keyboard_sounds_summary),
                shortcutHint = stringResource(R.string.oplus_input_keyboard_sounds_hint),
                startAction = { Icon(imageVector = Icons.Outlined.VolumeUp, contentDescription = null) },
                checked = keyboardSoundEnabled,
                onCheckedChange = setKeyboardSound,
                onClick = {
                    if (keyboardSoundEnabled) {
                        handleLaunch(KeyboardPagesContract.PAGE_KEYBOARD_SOUNDS)
                    } else {
                        setKeyboardSound(true)
                    }
                }
            )

            HorizontalDivider()

            // 4. Enhancements
            SwitchPreference(
                title = stringResource(R.string.oplus_input_enhancements_title),
                summary = stringResource(R.string.oplus_input_enhancements_summary),
                shortcutHint = stringResource(R.string.oplus_input_enhancements_hint),
                startAction = { Icon(imageVector = Icons.Outlined.Widgets, contentDescription = null) },
                checked = enhancementsEnabled,
                onCheckedChange = setEnhancements,
                onClick = {
                    if (enhancementsEnabled) {
                        handleLaunch(KeyboardPagesContract.PAGE_ENHANCEMENTS)
                    } else {
                        setEnhancements(true)
                    }
                }
            )
        }
    }

    // Error Dialog
    if (errorDialogMessage != null) {
        // rmuix OverlayDialog: the body parameter is summary; content is a required trailing lambda.
        OverlayDialog(
            show = true,
            onDismissRequest = { errorDialogMessage = null },
            title = stringResource(R.string.oplus_input_launch_failed_title),
            summary = errorDialogMessage,
            confirmButtonText = stringResource(R.string.dialog_got_it),
            onConfirm = { errorDialogMessage = null }
        ) {
            // Title and body only; the content slot is empty.
        }
    }
}
