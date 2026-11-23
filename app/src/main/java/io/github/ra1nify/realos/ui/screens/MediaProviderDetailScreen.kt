package io.github.ra1nify.realos.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.mediaprovider.MediaProviderConfig
import io.github.ra1nify.realos.restart.RestartPolicies
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.Icon
import io.github.ra1nify.realos.ui.components.SpecPageScaffold
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * MediaProvider detail page.
 *
 * The switch controls the picker refresh hook. The hook stays installed while
 * the scope is active and reads this value on every media change, so toggling
 * takes effect without restarting the provider process.
 */
@Composable
fun MediaProviderDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var pickerRefreshEnabled by remember {
        mutableStateOf(MediaProviderConfig.isPickerRefreshEnabled(context))
    }

    LaunchedEffect(Unit) {
        MediaProviderConfig.syncToRemote(context)
    }

    SpecPageScaffold(
        title = rememberHostAppName(
            SpecPackages.MEDIA_PROVIDER,
            stringResource(R.string.media_provider_app_name)
        ),
        onNavigateBack = onNavigateBack,
        restartPolicy = RestartPolicies.mediaProvider,
        modifier = modifier,
        tipsTitle = stringResource(R.string.media_provider_tips_title),
        tipsMessage = stringResource(R.string.media_provider_tips_message)
    ) {
        BasicComponentGroup(
            title = stringResource(R.string.media_provider_section_features_title)
        ) {
            SwitchPreference(
                title = stringResource(R.string.media_provider_picker_refresh_title),
                summary = stringResource(R.string.media_provider_picker_refresh_summary),
                startAction = { Icon(Icons.Outlined.PhotoLibrary, null) },
                checked = pickerRefreshEnabled,
                onCheckedChange = { enabled ->
                    pickerRefreshEnabled = enabled
                    MediaProviderConfig.setPickerRefreshEnabled(context, enabled)
                }
            )
        }
    }
}
