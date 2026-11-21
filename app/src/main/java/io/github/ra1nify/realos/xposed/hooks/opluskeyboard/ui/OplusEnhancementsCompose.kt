package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.ui.components.HorizontalDivider
import io.github.ra1nify.realos.ui.components.BasicComponentGroup
import io.github.ra1nify.realos.ui.components.preference.SwitchPreference
import io.github.ra1nify.realos.ui.components.InjectPage

@Composable
fun OplusEnhancementsCompose(activity: Activity) {
    val context = activity
    val isNativelyDeclared = remember {
        OplusInputPrefs.isInlineSuggestionsNativelyDeclared(context)
    }

    var autofillEnabled by remember {
        mutableStateOf(if (isNativelyDeclared) true else OplusInputPrefs.isAutofillEnabled(context))
    }
    var autofillButtonEnabled by remember {
        mutableStateOf(OplusInputPrefs.isAutofillButtonEnabled(context))
    }
    var systemInlineAutofillEnabled by remember {
        mutableStateOf(OplusInputPrefs.isSystemInlineAutofillEnabled(context))
    }

    val autofillSummary = if (isNativelyDeclared) {
        OplusWorkshopUiText.getAutofillSwitchNativeSummary(context)
    } else {
        OplusWorkshopUiText.getAutofillSwitchSummary(context)
    }

    InjectPage(
        title = OplusWorkshopUiText.getEnhancementsTitle(context),
        onBack = { activity.finish() },
    ) {
        BasicComponentGroup(
            title = OplusWorkshopUiText.getSectionAdvanced(context)
        ) {
            SwitchPreference(
                title = OplusWorkshopUiText.getAutofillSwitchTitle(context),
                summary = autofillSummary,
                checked = autofillEnabled,
                enabled = !isNativelyDeclared,
                onCheckedChange = { enabled ->
                    autofillEnabled = enabled
                    OplusInputPrefs.setAutofillEnabled(context, enabled)
                }
            )

            HorizontalDivider()

            SwitchPreference(
                title = OplusWorkshopUiText.getAutofillButtonSwitchTitle(context),
                summary = OplusWorkshopUiText.getAutofillButtonSwitchSummary(context),
                checked = autofillButtonEnabled,
                onCheckedChange = { enabled ->
                    autofillButtonEnabled = enabled
                    OplusInputPrefs.setAutofillButtonEnabled(context, enabled)
                }
            )

            HorizontalDivider()

            SwitchPreference(
                title = OplusWorkshopUiText.getSystemInlineAutofillTitle(context),
                summary = OplusWorkshopUiText.getSystemInlineAutofillDesc(context),
                checked = systemInlineAutofillEnabled,
                onCheckedChange = { enabled ->
                    systemInlineAutofillEnabled = enabled
                    OplusInputPrefs.setSystemInlineAutofillEnabled(context, enabled)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
