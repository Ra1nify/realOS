package io.github.ra1nify.realos.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ra1nify.realos.service.XposedServiceManager
import io.github.ra1nify.realos.ui.screens.LauncherDockPage
import io.github.ra1nify.realos.ui.theme.RealOSTheme

/**
 * Standalone dock background tuning page, started from the launcher settings entry.
 */
class LauncherDockPageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        XposedServiceManager.initialize(this)
        setContent {
            RealOSTheme {
                LauncherDockPage(onBack = { finish() })
            }
        }
    }
}
