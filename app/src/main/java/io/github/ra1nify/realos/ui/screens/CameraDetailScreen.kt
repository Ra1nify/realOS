package io.github.ra1nify.realos.ui.screens

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
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
import io.github.ra1nify.realos.camera.OplusCameraConfig
import io.github.ra1nify.realos.camera.OplusCameraLauncher
import io.github.ra1nify.realos.restart.RestartExecutor
import io.github.ra1nify.realos.restart.RestartPolicies
import io.github.ra1nify.realos.restart.toastMessageRes
import io.github.ra1nify.realos.ui.components.ModuleDetailScaffold
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.Icon
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * ColorOS camera assistant detail page.
 *
 * The right switch controls realOS tab injection into the camera settings top bar.
 * The left side opens camera settings, requesting the realOS tab when enabled.
 */
@Composable
fun CameraDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var realosTabEnabled by remember {
        mutableStateOf(OplusCameraConfig.isRealosTabEnabled(context))
    }

    // Copies local values to remote preferences on entry.
    LaunchedEffect(Unit) {
        OplusCameraConfig.syncToRemote(context)
    }

    fun openCameraSettings() {
        val ok = OplusCameraLauncher.openSettings(context, openRealosTab = realosTabEnabled)
        if (!ok) {
            Toast.makeText(
                context,
                context.getString(R.string.camera_open_settings_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    ModuleDetailScaffold(
        title = rememberHostAppName(SpecPackages.OPLUS_CAMERA, stringResource(R.string.camera_app_name)),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        tipsTitle = stringResource(R.string.camera_tips_title),
        tipsMessage = stringResource(R.string.camera_tips_message),
        onRestart = {
            scope.launch {
                val policy = RestartPolicies.camera
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
            title = stringResource(R.string.camera_section_injection_title)
        ) {
            SwitchPreference(
                title = stringResource(R.string.camera_shortcut_realos_tab_title),
                summary = stringResource(R.string.camera_shortcut_realos_tab_summary),
                shortcutHint = stringResource(R.string.camera_shortcut_realos_tab_hint),
                // The startAction slot holds the leading icon; the row click uses onClick.
                startAction = { Icon(Icons.Outlined.Camera, null) },
                checked = realosTabEnabled,
                onCheckedChange = { enabled ->
                    realosTabEnabled = enabled
                    OplusCameraConfig.setRealosTabEnabled(context, enabled)
                },
                shortcutOnlyWhenChecked = true,
                onClick = { openCameraSettings() }
            )
        }
    }
}
