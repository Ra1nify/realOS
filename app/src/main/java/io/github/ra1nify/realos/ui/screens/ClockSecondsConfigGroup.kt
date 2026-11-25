package io.github.ra1nify.realos.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.clockseconds.ClockSecondsConfig
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference

/** Clock-seconds conversion switch. */
@Composable
fun ClockSecondsConfigGroup() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ClockSecondsConfig.isEnabled(context)) }

    LaunchedEffect(Unit) {
        ClockSecondsConfig.syncToRemote(context)
    }

    BasicComponentGroup(
        title = stringResource(R.string.clock_seconds_config_section),
        footer = stringResource(R.string.clock_seconds_config_footer)
    ) {
        SwitchPreference(
            title = stringResource(R.string.clock_seconds_config_title),
            summary = stringResource(R.string.clock_seconds_config_summary),
            checked = enabled,
            onCheckedChange = { checked ->
                enabled = checked
                ClockSecondsConfig.setEnabled(context, checked)
            }
        )
    }
}
