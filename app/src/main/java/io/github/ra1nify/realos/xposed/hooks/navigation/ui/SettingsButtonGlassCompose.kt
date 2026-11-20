package io.github.ra1nify.realos.xposed.hooks.navigation.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import io.github.ra1nify.realos.ui.components.InjectPage
import io.github.ra1nify.realos.ui.components.TopTips

@Composable
fun SettingsButtonGlassCompose(activity: Activity) {
    val context = activity

    InjectPage(
        title = OplusWorkshopUiText.getNavSectionLogicTuning(context),
        onBack = { activity.finish() },
    ) {
        TopTips(
            title = OplusWorkshopUiText.getHelloWorldTitle(context),
            content = OplusWorkshopUiText.getHelloWorldDesc(context),
        )
    }
}
