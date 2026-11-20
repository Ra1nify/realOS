package io.github.ra1nify.realos.font

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ra1nify.realos.service.XposedServiceManager
import io.github.ra1nify.realos.ui.screens.SystemFontManagerScreen
import io.github.ra1nify.realos.ui.theme.RealOSTheme

/**
 * Standalone font library opened from Settings -> Personalization -> Fonts ->
 * realOS font. Kept as a separate exported Activity so the Settings font page
 * can start it without depending on the realOS MainActivity.
 */
class SystemFontManagerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        XposedServiceManager.initialize(this)
        SystemFontConfig.syncToRemote(this)
        setContent {
            RealOSTheme {
                SystemFontManagerScreen(onBack = { finish() })
            }
        }
    }
}
