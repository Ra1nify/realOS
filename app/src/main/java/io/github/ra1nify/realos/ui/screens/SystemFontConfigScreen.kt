package io.github.ra1nify.realos.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.font.SystemFontConfig
import io.github.ra1nify.realos.restart.RestartPolicies
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.SpecPageScaffold
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference

/**
 * Settings-host font switches. They live in the realOS Settings spec page,
 * not in the Settings font page itself.
 */
@Composable
fun SystemFontConfigGroup() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SystemFontConfig.isEnabled(context)) }
    var hideThemeStore by remember {
        mutableStateOf(SystemFontConfig.isHideThemeStore(context))
    }

    LaunchedEffect(Unit) {
        SystemFontConfig.syncToRemote(context)
    }

    BasicComponentGroup(title = stringResource(R.string.settings_font_section)) {
        SwitchPreference(
            title = stringResource(R.string.settings_font_config_title),
            summary = stringResource(R.string.settings_font_config_summary),
            checked = enabled,
            onCheckedChange = { checked ->
                enabled = checked
                SystemFontConfig.setEnabled(context, checked)
                SystemFontConfig.bumpRevision(context)
            }
        )
        HorizontalDivider()
        SwitchPreference(
            title = stringResource(R.string.settings_font_hide_theme_store_title),
            summary = stringResource(R.string.settings_font_hide_theme_store_summary),
            checked = hideThemeStore,
            onCheckedChange = { checked ->
                hideThemeStore = checked
                SystemFontConfig.setHideThemeStore(context, checked)
                SystemFontConfig.bumpRevision(context)
            }
        )
    }
}

/** Settings host page for the enabled font and clock-seconds groups. */
@Composable
fun SystemFontConfigScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SpecPageScaffold(
        title = stringResource(R.string.settings_detail_title),
        onNavigateBack = onBack,
        restartPolicy = RestartPolicies.settingsFont,
        modifier = modifier,
        tipsTitle = if (BuildConfig.SCOPE_FONT) {
            stringResource(R.string.settings_font_tips_title)
        } else {
            null
        },
        tipsMessage = if (BuildConfig.SCOPE_FONT) {
            stringResource(R.string.settings_font_tips_message)
        } else {
            null
        }
    ) {
        if (BuildConfig.SCOPE_FONT) {
            SystemFontConfigGroup()
        }
        if (BuildConfig.SCOPE_CLOCKSECONDS) {
            ClockSecondsConfigGroup()
        }
    }
}
