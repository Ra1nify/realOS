package io.github.ra1nify.realos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.itos.coui.theme.CouiColorScheme

/**
 * realOS semantic color palette.
 *
 * Holds the ColorOS 17 semantic tokens and maps them to coui slots through
 * [toCouiColorScheme].
 *
 * Mapping to coui `CouiColorScheme`:
 *
 * | realOS token              | coui slot            | Description |
 * | ------------------------- | -------------------- | ---- |
 * | `background`              | `surface` / `background` | Page background |
 * | `surface`                 | `container` / `card` | Card background |
 * | `surfaceContainerHigh`    | `menuSurface`        | Overlay/menu background |
 * | `onSurface`               | `onSurface`          | Primary text |
 * | `onSurfaceSecondary`      | `onSurfaceVariant`   | Secondary text |
 * | `dividerLine`             | `outlineVariant`     | Divider |
 * | `primary`                 | `accent`             | Brand accent, overridden by the host theme color |
 */
@Immutable
internal data class AppColors(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceSecondary: Color,
    val onSurfaceVariantSummary: Color,
    val dividerLine: Color,
    val surfaceContainerHigh: Color,
)

/** Light palette with ColorOS 17 role values. */
internal val RealOSLight = AppColors(
    primary = Color(0xFF007AFF),               // coui_color_primary_blue_light
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF0F1F2),            // coui_color_background_with_card_light
    onBackground = Color(0xE6000000),          // coui_color_primary_neutral_light (90%)
    surface = Color(0xFFFFFFFF),               // coui_color_card_background_light
    onSurface = Color(0xE6000000),
    onSurfaceSecondary = Color(0x8A000000),    // coui_color_secondary_neutral_light (54%)
    onSurfaceVariantSummary = Color(0x42000000), // coui_color_hint_neutral_light (26%)
    dividerLine = Color(0x1F000000),           // coui_color_divider_light (12%)
    surfaceContainerHigh = Color(0xFFFAFAFA),  // coui_alert_dialog_background_tint
)

/**
 * Dark palette, at the darkest ([DarkModeTier.ENHANCED]) tier.
 *
 * Only `background` varies with the host dark style tier; see [darkPalette] and [DarkModeTier].
 */
internal val RealOSDark = AppColors(
    primary = Color(0xFF3388FF),               // coui_color_primary_blue_dark
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF000000),            // darkmode_setting_background_color_heighten
    onBackground = Color(0xE6FFFFFF),          // (90%)
    surface = Color(0x1AFFFFFF),               // coui_color_card_background_dark (10% translucent white)
    onSurface = Color(0xE6FFFFFF),
    onSurfaceSecondary = Color(0x8AFFFFFF),    // (54%)
    onSurfaceVariantSummary = Color(0x4DFFFFFF), // (30%)
    dividerLine = Color(0x33FFFFFF),           // (20%)
    surfaceContainerHigh = Color(0xFF242426),  // coui_list_dialog_background_color_no_blur_night
)

/**
 * Dark palette for the host [tier].
 *
 * ColorOS varies only the page background between the three dark styles, so the card, text and
 * divider tokens are shared: the translucent card (`surface`, white 10%) keeps compositing
 * correctly over every tier background. The menu surface is the one extra adjustment, see
 * [DarkModeTier.menuSurface].
 */
internal fun darkPalette(tier: DarkModeTier): AppColors =
    RealOSDark.copy(
        background = tier.background,
        surfaceContainerHigh = tier.menuSurface,
    )

/**
 * Maps to the coui color scheme.
 *
 * coui `surface` is the page background and coui `container`/`card` is the
 * card background, the inverse of the realOS `background`/`surface` naming.
 */
internal fun AppColors.toCouiColorScheme(): CouiColorScheme = CouiColorScheme(
    accent = primary,
    onAccent = onPrimary,
    surface = background,
    // coui uses `container` only as the opaque menu fill, for the case where no menu backdrop is
    // provided (Menu.kt / CouiMenuSubMenu.kt). It must therefore be an opaque menu surface, not the
    // translucent card: `surface` is 10% white in dark mode and would show the page through a solid
    // menu.
    container = surfaceContainerHigh,
    card = surface,
    background = background,
    onSurface = onSurface,
    onSurfaceVariant = onSurfaceSecondary,
    outlineVariant = dividerLine,
    accentOnSurface = primary,
    menuSurface = surfaceContainerHigh,
    checkboxAccent = primary,
    menuCheckboxAccent = primary,
)
