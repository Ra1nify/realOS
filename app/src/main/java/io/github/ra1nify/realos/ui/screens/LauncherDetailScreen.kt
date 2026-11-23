package io.github.ra1nify.realos.ui.screens

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.restart.RestartExecutor
import io.github.ra1nify.realos.restart.RestartPolicies
import io.github.ra1nify.realos.restart.toastMessageRes
import io.github.ra1nify.realos.ui.components.ModuleDetailScaffold
import io.github.ra1nify.realos.launcher.LauncherConfig
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.Icon
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference

/**
 * System launcher detail page.
 *
 * The three switches are install gates: they are read once when the launcher
 * process starts, and off means no hooks are installed for that feature.
 */
@Composable
fun LauncherDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var memoryHooksEnabled by remember {
        mutableStateOf(LauncherConfig.isMemoryHooksEnabledForUi(context))
    }
    var recentsExtraStylesEnabled by remember {
        mutableStateOf(LauncherConfig.isRecentsExtraStylesEnabledForUi(context))
    }
    var colorFilterEnabled by remember {
        mutableStateOf(LauncherConfig.isColorFilterEnabledForUi(context))
    }

    LaunchedEffect(Unit) {
        LauncherConfig.syncToRemote(context)
    }

    ModuleDetailScaffold(
        title = rememberHostAppName(SpecPackages.LAUNCHER, stringResource(R.string.launcher_app_name)),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        onRestart = {
            scope.launch {
                val policy = RestartPolicies.launcher
                val result = RestartExecutor.restart(context, policy)
                Toast.makeText(
                    context,
                    context.getString(result.toastMessageRes(policy)),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    ) {
        BasicComponentGroup(
            title = stringResource(R.string.launcher_section_features_title),
            footer = stringResource(R.string.launcher_features_footer)
        ) {
            SwitchPreference(
                title = stringResource(R.string.launcher_memory_hooks_title),
                summary = stringResource(R.string.launcher_memory_hooks_summary),
                startAction = { Icon(Icons.Outlined.Memory, null) },
                checked = memoryHooksEnabled,
                onCheckedChange = { enabled ->
                    memoryHooksEnabled = enabled
                    LauncherConfig.setMemoryHooksEnabled(context, enabled)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.launcher_recents_extra_styles_title),
                summary = stringResource(R.string.launcher_recents_extra_styles_summary),
                startAction = { Icon(Icons.Outlined.Dashboard, null) },
                checked = recentsExtraStylesEnabled,
                onCheckedChange = { enabled ->
                    recentsExtraStylesEnabled = enabled
                    LauncherConfig.setRecentsExtraStylesEnabled(context, enabled)
                }
            )
            HorizontalDivider()
            SwitchPreference(
                title = stringResource(R.string.launcher_color_filter_title),
                summary = stringResource(R.string.launcher_color_filter_summary),
                startAction = { Icon(Icons.Outlined.Palette, null) },
                checked = colorFilterEnabled,
                onCheckedChange = { enabled ->
                    colorFilterEnabled = enabled
                    LauncherConfig.setColorFilterEnabled(context, enabled)
                }
            )
        }
    }
}
