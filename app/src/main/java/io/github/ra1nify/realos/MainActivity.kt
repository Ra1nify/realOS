package io.github.ra1nify.realos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ra1nify.realos.icon.AppIconManager
import io.github.ra1nify.realos.root.RootManager
import io.github.ra1nify.realos.root.ZramRootFetcher
import io.github.ra1nify.realos.service.XposedServiceManager
import io.github.ra1nify.realos.navigation.SettingsConfig
import io.github.ra1nify.realos.ui.screens.MainScreen
import io.github.ra1nify.realos.ui.theme.RealOSTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enables the default alias when an upgrade leaves all aliases disabled.
        AppIconManager.ensureDefault(this)

        // Loads cached state, then runs one background probe per app launch.
        XposedServiceManager.initialize(this)
        XposedServiceManager.probeOnAppLaunch(this)
        RootManager.initialize(this)
        RootManager.probeOnAppLaunch(this)
        // SettingsConfig writes both preference channels and needs the app context.
        SettingsConfig.init(this)

        // Fetches zram stats once with root and publishes them for the launcher.
        // Runs only on first creation.
        if (savedInstanceState == null) {
            Thread({
                runCatching { ZramRootFetcher.fetchAndPublish(applicationContext) }
            }, "realOS-zram-warmup").apply { isDaemon = true }.start()
        }

        setContent {
            RealOSTheme {
                MainScreen()
            }
        }
    }
}
