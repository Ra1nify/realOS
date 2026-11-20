package io.github.ra1nify.realos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * realOS text styles.
 *
 * coui `Text` accepts only a `CouiTextKind` enum and no free `TextStyle`
 * parameter. This file defines finer-grained `TextStyle` values consumed by
 * M3 `Text`.
 *
 * Sizes follow the coui type scale: ItemTitle 16sp, ItemSummary 13sp,
 * TopBar 18sp, GroupTitle 12sp, GroupFooter 12sp. `subtitle` (14sp) has no
 * coui role and is kept for the trailing value slot.
 */
@Immutable
internal data class AppTextStyles(
    /** Body text (coui ItemTitle metrics). */
    val body1: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
    ),
    /** Secondary title. */
    val subtitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    ),
    /** Tertiary title / top bar title (coui TopBar metrics). */
    val title3: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp,
    ),
    /** Caption text (coui ItemSummary metrics). */
    val footnote2: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    ),
    /** Smaller auxiliary text. */
    val footnote1: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
    ),
)

internal val DefaultAppTextStyles = AppTextStyles()
