package io.github.ra1nify.realos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.CouiTypography
import com.itos.coui.theme.LocalCouiDarkTheme
import io.github.ra1nify.realos.ui.theme.AppTheme

internal val LocalAppColors = staticCompositionLocalOf { RealOSLight }
internal val LocalAppTextStyles = staticCompositionLocalOf { DefaultAppTextStyles }

/**
 * realOS theme entry point.
 *
 * Composition order, outer to inner:
 *
 * 1. `Coui.initialize(CouiMaterial3Style())`, once per process, see [CouiInit];
 * 2. `MaterialTheme`, source of M3 Icon/IconButton/Button/Card/Progress and
 *    the color and typography source of `CouiMaterial3Style`;
 * 3. `CouiTheme`, the coui color scheme with realOS ColorOS semantic colors;
 * 4. `LocalCouiDarkTheme`, the dark flag coui reads;
 * 5. `ProvideCouiBackdrop`, the backdrop required by coui `TopAppBar`,
 *    `NavigationBar` and `Fab`.
 */
@Composable
fun RealOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CouiInit.ensure()

    val context = LocalContext.current
    val systemAccent = remember(darkTheme) { SystemThemeColor.get(context, darkTheme) }
    // Follows the ColorOS dark style tier; only the page background differs per tier.
    val darkTier = rememberDarkModeTier()

    val appColors = remember(darkTheme, systemAccent, darkTier) {
        val base = if (darkTheme) darkPalette(darkTier) else RealOSLight
        if (systemAccent != null) base.copy(primary = systemAccent) else base
    }
    val couiColors = remember(appColors) { appColors.toCouiColorScheme() }
    val couiTypography = remember { CouiTypography() }

    val materialColors = remember(appColors, darkTheme) {
        val scheme = if (darkTheme) darkColorScheme() else lightColorScheme()
        scheme.copy(
            primary = appColors.primary,
            onPrimary = appColors.onPrimary,
            background = appColors.background,
            onBackground = appColors.onBackground,
            surface = appColors.surface,
            onSurface = appColors.onSurface,
            onSurfaceVariant = appColors.onSurfaceSecondary,
            surfaceContainerHigh = appColors.surfaceContainerHigh,
            outlineVariant = appColors.dividerLine,
        )
    }

    MaterialTheme(colorScheme = materialColors) {
        CouiTheme(
            darkTheme = darkTheme,
            colorScheme = couiColors,
            typography = couiTypography,
            materialTint = Color.Transparent,
        ) {
            CompositionLocalProvider(
                LocalCouiDarkTheme provides darkTheme,
                LocalAppColors provides appColors,
                LocalAppTextStyles provides DefaultAppTextStyles,
            ) {
                // No backdrop is provided here on purpose. A coui Scaffold builds the sampling
                // layer for its own topBar / bottomBar / Fab slots, and a top bar placed outside
                // such a slot would sample an unfed backdrop and render solid. Leaving the local
                // unset makes that mistake fail loudly instead of degrading silently.
                content()
            }
        }
    }
}

/**
 * Reads realOS semantic colors and text styles.
 *
 * Call shape: `AppTheme.colorScheme.x` / `AppTheme.textStyles.x`.
 */
internal object AppTheme {
    val colorScheme: AppColors
        @Composable get() = LocalAppColors.current

    val textStyles: AppTextStyles
        @Composable get() = LocalAppTextStyles.current
}
