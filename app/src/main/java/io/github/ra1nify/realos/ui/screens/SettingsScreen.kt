package io.github.ra1nify.realos.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.icon.AppIcon
import io.github.ra1nify.realos.icon.AppIconManager
import io.github.ra1nify.realos.util.LogLevel
import io.github.ra1nify.realos.util.RealOSLogConfig
import io.github.ra1nify.realos.util.RealOSLogSettings
import io.github.ra1nify.realos.ui.components.preference.JumpPreference
import io.github.ra1nify.realos.ui.components.preference.OverlaySpinnerPreference
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.DropdownItem
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.ui.components.SquircleShape
import androidx.compose.ui.graphics.Color
import io.github.ra1nify.realos.ui.components.Scaffold
import io.github.ra1nify.realos.ui.theme.AppScaffoldDefaults
import io.github.ra1nify.realos.ui.components.LocalScaffoldContentPadding

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = LocalScaffoldContentPadding.current,
) {
    val context = LocalContext.current
    // Toggles all activity-aliases.
    var hideIcon by remember { mutableStateOf(AppIconManager.isHidden(context)) }
    var logLevel by remember { mutableStateOf(RealOSLogConfig.getLevel(context)) }

    // The nested Scaffold only supplies the coui popup host for the dropdown dialogs.
    // Transparent container with zero insets.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = AppScaffoldDefaults.zeroWindowInsets
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            // ── Appearance: app icon ──
            item {
                var selectedIcon by remember { mutableStateOf(AppIconManager.current(context)) }

                BasicComponentGroup(title = stringResource(R.string.app_icon_section)) {
                    // A DropdownItem list plus selectedIndex describes the options:
                    // entryLabel maps to DropdownItem.text and itemLeadingContent to DropdownItem.icon.
                    OverlaySpinnerPreference(
                        title = stringResource(R.string.app_icon_title),
                        items = AppIcon.entries.map { icon ->
                            DropdownItem(
                                text = context.getString(icon.labelRes),
                                icon = { iconModifier ->
                                    Box(
                                        modifier = iconModifier
                                            .size(28.dp)
                                            .clip(SquircleShape(cornerRadius = 8.dp))
                                            .background(colorResource(icon.backgroundRes))
                                    ) {
                                        Image(
                                            painter = painterResource(icon.previewRes),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            )
                        },
                        selectedIndex = AppIcon.entries.indexOf(selectedIcon),
                        onSelectedIndexChange = { index ->
                            val icon = AppIcon.entries[index]
                            AppIconManager.apply(context, icon)
                            selectedIcon = icon
                        },
                        summary = stringResource(selectedIcon.labelRes)
                    )
                }
            }

            item {
                BasicComponentGroup(
                    title = stringResource(R.string.settings_section_behavior)
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_hide_launcher_icon_title),
                        summary = stringResource(R.string.settings_hide_launcher_icon_summary),
                        checked = hideIcon,
                        onCheckedChange = { enabled ->
                            AppIconManager.setHidden(context, enabled)
                            hideIcon = enabled
                        }
                    )
                }
            }

            item {
                BasicComponentGroup(
                    title = stringResource(R.string.settings_section_logging)
                ) {
                    OverlaySpinnerPreference(
                        title = stringResource(R.string.settings_log_level_title),
                        items = LogLevel.entries.map { level ->
                            DropdownItem(text = context.getString(level.labelRes()))
                        },
                        selectedIndex = LogLevel.entries.indexOf(logLevel),
                        onSelectedIndexChange = { index ->
                            val level = LogLevel.entries[index]
                            RealOSLogSettings.setLevel(context, level)
                            logLevel = level
                        },
                        summary = context.getString(logLevel.labelRes())
                    )
                }
            }

            item {
                BasicComponentGroup(
                    title = stringResource(R.string.settings_section_about)
                ) {
                    JumpPreference(
                        title = stringResource(R.string.settings_version_title),
                        statusText = "v" + BuildConfig.VERSION_NAME,
                        onClick = { }
                    )
                    HorizontalDivider()
                    JumpPreference(
                        title = stringResource(R.string.settings_ui_system_title),
                        statusText = stringResource(R.string.settings_ui_system_value),
                        onClick = { openControlLibraryPage(context) }
                    )
                }
            }
        }
    }
}

private fun LogLevel.labelRes(): Int = when (this) {
    LogLevel.NONE -> R.string.log_level_none
    LogLevel.ERROR -> R.string.log_level_error
    LogLevel.WARN -> R.string.log_level_warn
    LogLevel.INFO -> R.string.log_level_info
    LogLevel.DEBUG -> R.string.log_level_debug
    LogLevel.VERBOSE -> R.string.log_level_verbose
}

/** COUI-Kit control library page. */
private const val COUI_KIT_URL = "https://github.com/ItosEO/COUI-Kit"

/** Opens the control library page; failures are ignored. */
private fun openControlLibraryPage(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(COUI_KIT_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
