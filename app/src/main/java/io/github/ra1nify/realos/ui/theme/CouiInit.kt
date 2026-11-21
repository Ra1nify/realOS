package io.github.ra1nify.realos.ui.theme

import io.github.ra1nify.realos.util.RealOSLog as Log
import com.itos.coui.material3.CouiMaterial3Style
import com.itos.coui.theme.Coui

/**
 * Process-level coui initialization.
 *
 * `Coui.initialize()` runs before any Compose UI creation and only once;
 * the internal `check(!initialized)` throws on a second call. Both realOS
 * forms enter Compose through [RealOSTheme]:
 *
 * 1. the app process (MainActivity);
 * 2. injected host processes (keyboard settings, system navigation, wallet card).
 *
 * `CouiMaterial3Style` takes coui component colors and fonts from the host
 * `MaterialTheme`.
 *
 * A failed call leaves coui's default `NativeCouiStyle` in place.
 */
internal object CouiInit {

    private const val TAG = "RealOS-CouiInit"

    @Volatile
    private var done = false

    fun ensure() {
        if (done) return
        synchronized(this) {
            if (done) return
            done = true
            runCatching { Coui.initialize(CouiMaterial3Style()) }
                .onFailure { t -> Log.w(TAG, "coui initialization failed; falling back to the default style: ${t.message}") }
        }
    }
}
