package io.github.ra1nify.realos.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ColorOS dark mode style tier.
 *
 * ColorOS exposes a three-step dark style under Settings -> Display & brightness -> Dark mode ->
 * Dark style. Only the page background differs between tiers; the card, text and divider tokens
 * are shared.
 *
 * Values are verified against the device:
 *
 * - `Settings.System` key `DarkMode_style_key`: 0 = enhanced, 1 = moderate, 2 = gentle.
 * - The same tiers set `Settings.Global` `DarkMode_BackgroundMaxL` to 0.0 / 8.0 / 20.0.
 * - Settings.apk ships the matching swatches:
 *   `darkmode_setting_background_color_heighten` `#FF000000`,
 *   `darkmode_setting_background_color_middle` `#FF191919`,
 *   `darkmode_setting_background_color_gentle` `#FF2E2E2E`.
 *
 * The host tier is read from the public `Settings.System` table, which needs no root and no
 * Xposed hook.
 */
internal enum class DarkModeTier(
    val settingValue: Int,
    /** Page background for this tier, matching the ColorOS dark style swatch. */
    val background: Color,
    /**
     * Overlay/menu surface for this tier.
     *
     * ColorOS varies only the page background, because its own menus are translucent over a blur.
     * realOS menus are opaque, so the two lighter tiers lift this token to stay above the page:
     * the darkest tier keeps the coui `coui_list_dialog_background_color_no_blur_night` value, and
     * the others keep the same page-to-menu step so a menu never renders darker than the page.
     */
    val menuSurface: Color,
) {
    /** Enhanced: pure black page background. */
    ENHANCED(
        settingValue = 0,
        background = Color(0xFF000000),
        menuSurface = Color(0xFF242426),
    ),

    /** Moderate: dark grey page background. */
    MODERATE(
        settingValue = 1,
        background = Color(0xFF191919),
        menuSurface = Color(0xFF242426),
    ),

    /** Gentle: light grey page background. */
    GENTLE(
        settingValue = 2,
        background = Color(0xFF2E2E2E),
        menuSurface = Color(0xFF3A3A3C),
    ),
    ;

    companion object {
        /** Resolves a tier from its stored setting value; unknown values fall back to [ENHANCED]. */
        fun fromSettingValue(value: Int): DarkModeTier =
            entries.firstOrNull { it.settingValue == value } ?: ENHANCED
    }
}

/**
 * Reads the ColorOS dark mode style tier.
 *
 * The key is absent on builds without the ColorOS dark style page, in which case
 * [DarkModeTier.ENHANCED] is used: that is the palette realOS shipped before this setting was
 * honored, so an unsupported host keeps its previous appearance.
 *
 * One `ContentObserver` is shared by the whole process: a host such as launcher, settings or camera
 * may compose several realOS ComposeViews at once, and registering one observer per composition
 * would multiply the callbacks for a single setting.
 */
internal object DarkModeStyle {

    /** `Settings.System` key backing the ColorOS dark style selector. */
    private const val KEY_DARK_MODE_STYLE = "DarkMode_style_key"

    private val listeners = CopyOnWriteArrayList<(DarkModeTier) -> Unit>()

    @Volatile
    private var observer: ContentObserver? = null

    /** Reads the current tier. Never throws. */
    fun read(context: Context): DarkModeTier =
        runCatching {
            // Public Settings.System.getInt reads the calling user, which is the foreground user
            // realOS renders for. The @hide getIntForUser(..., USER_CURRENT) is not in the SDK.
            val value =
                Settings.System.getInt(
                    context.contentResolver,
                    KEY_DARK_MODE_STYLE,
                    DarkModeTier.ENHANCED.settingValue,
                )
            DarkModeTier.fromSettingValue(value)
        }.getOrDefault(DarkModeTier.ENHANCED)

    /**
     * Subscribes to tier changes.
     *
     * @return a handle that unsubscribes when invoked; calling it twice is harmless.
     */
    fun subscribe(context: Context, onChange: (DarkModeTier) -> Unit): () -> Unit {
        ensureObserver(context.applicationContext ?: context)
        listeners.add(onChange)
        return {
            listeners.remove(onChange)
            Unit
        }
    }

    private fun ensureObserver(context: Context) {
        if (observer != null) return
        synchronized(this) {
            if (observer != null) return
            val created =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        val tier = read(context)
                        for (listener in listeners) {
                            runCatching { listener(tier) }
                        }
                    }
                }
            val registered =
                runCatching {
                    context.contentResolver.registerContentObserver(
                        Settings.System.getUriFor(KEY_DARK_MODE_STYLE),
                        false,
                        created,
                    )
                }.isSuccess
            if (registered) observer = created
        }
    }
}

/**
 * Remembers the host dark style tier and keeps it in sync with the system setting.
 *
 * Changing the style in Settings updates every composed realOS page without a restart.
 */
@Composable
internal fun rememberDarkModeTier(): DarkModeTier {
    val context = LocalContext.current
    var tier by remember(context) { mutableStateOf(DarkModeStyle.read(context)) }

    DisposableEffect(context) {
        val unsubscribe = DarkModeStyle.subscribe(context) { tier = it }
        onDispose { unsubscribe() }
    }

    return tier
}
