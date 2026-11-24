package io.github.ra1nify.realos

import android.app.Application
import io.github.ra1nify.realos.clockfont.ClockFontPublisher
import io.github.ra1nify.realos.service.XposedServiceManager
import io.github.ra1nify.realos.util.RealOSLog

/**
 * Initializes the global log level before any app-side code logs.
 *
 * The host processes initialize through `RealOSModule.onModuleLoaded`; this
 * Application only covers the realOS app process.
 */
class RealOSApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        RealOSLog.initApp(this)
        // Publish existing clock fonts after an app update.
        XposedServiceManager.initialize(this)
        if (BuildConfig.SCOPE_CLOCKFONT) {
            ClockFontPublisher.start(this)
        }
    }
}
