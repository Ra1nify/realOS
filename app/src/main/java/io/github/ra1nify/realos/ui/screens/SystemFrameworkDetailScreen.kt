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
import io.github.ra1nify.realos.restart.RestartPolicies
import io.github.ra1nify.realos.ui.components.SpecPageScaffold
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.fido.FidoRedirectConfig
import io.github.ra1nify.realos.intentroute.IntentRouteConfig
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.xposed.loader.SpecPackages

@Composable
fun SystemFrameworkDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var systemInlineAutofillEnabled by remember {
        mutableStateOf(OplusInputPrefs.isSystemInlineAutofillEnabled(context))
    }

    var fidoScanRedirectEnabled by remember {
        mutableStateOf(FidoRedirectConfig.isScanRedirectEnabled(context))
    }

    var intentRouteAlignEnabled by remember {
        mutableStateOf(IntentRouteConfig.isAlignLinksEnabled(context))
    }

    LaunchedEffect(Unit) {
        FidoRedirectConfig.syncToRemote(context)
        IntentRouteConfig.syncToRemote(context)
    }

    SpecPageScaffold(
        title = rememberHostAppName(SpecPackages.ANDROID, stringResource(R.string.system_framework_app_name)),
        onNavigateBack = onNavigateBack,
        restartPolicy = RestartPolicies.systemFramework,
        modifier = modifier
    ) {
        BasicComponentGroup(
            title = stringResource(R.string.system_framework_autofill_section)
        ) {
            SwitchPreference(
                title = OplusWorkshopUiText.getSystemInlineAutofillTitle(context),
                summary = OplusWorkshopUiText.getSystemInlineAutofillDesc(context),
                checked = systemInlineAutofillEnabled,
                onCheckedChange = { checked ->
                    systemInlineAutofillEnabled = checked
                    OplusInputPrefs.setSystemInlineAutofillEnabled(context, checked)
                }
            )
        }

        if (BuildConfig.SCOPE_FIDO) {
            BasicComponentGroup(
                title = stringResource(R.string.system_framework_fido_section)
            ) {
                SwitchPreference(
                    title = stringResource(R.string.system_framework_fido_redirect_title),
                    summary = stringResource(R.string.system_framework_fido_redirect_summary),
                    checked = fidoScanRedirectEnabled,
                    onCheckedChange = { checked ->
                        fidoScanRedirectEnabled = checked
                        FidoRedirectConfig.setScanRedirectEnabled(context, checked)
                    }
                )
            }
        }

        if (BuildConfig.SCOPE_INTENTROUTE) {
            BasicComponentGroup(
                title = stringResource(R.string.system_framework_intent_section)
            ) {
                SwitchPreference(
                    title = stringResource(R.string.system_framework_intent_align_title),
                    summary = stringResource(R.string.system_framework_intent_align_summary),
                    checked = intentRouteAlignEnabled,
                    onCheckedChange = { checked ->
                        intentRouteAlignEnabled = checked
                        IntentRouteConfig.setAlignLinksEnabled(context, checked)
                    }
                )
            }
        }
    }
}
