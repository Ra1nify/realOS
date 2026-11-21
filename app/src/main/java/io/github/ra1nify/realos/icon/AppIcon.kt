package io.github.ra1nify.realos.icon

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.ra1nify.realos.R

/**
 * Selectable app icons.
 *
 * Add an icon:
 * 1. Add an `<activity-alias>` in `AndroidManifest.xml` with `icon` / `roundIcon`,
 *    `android:enabled="false"`, `MAIN/LAUNCHER`, and no `label`.
 * 2. Add an enum entry.
 *
 * Fields:
 * - [previewRes]: solid-geometry vector without background; used by the picker and in-app preview.
 * - [backgroundRes]: adaptive-icon background color, matching `background` in
 *   `mipmap-anydpi-v26/<name>.xml`.
 * - [labelRes]: display name, English source, marked `translatable="false"`.
 *
 * Monochrome layer: opaque solid geometry only. The system uses its alpha as a mask;
 * translucent fills, `fillAlpha`, and strokes render as gray blocks.
 */
enum class AppIcon(
    /** Full component name suffix appended to the package name. */
    val alias: String,
    @param:DrawableRes val previewRes: Int,
    @param:ColorRes val backgroundRes: Int,
    @param:StringRes val labelRes: Int,
) {
    STENCIL(
        alias = ".icon.LauncherStencil",
        previewRes = R.drawable.stencil,
        backgroundRes = R.color.stencil_background,
        labelRes = R.string.app_icon_stencil,
    ),
    DOT_MATRIX(
        alias = ".icon.LauncherDotmatrix",
        previewRes = R.drawable.dotmatrix,
        backgroundRes = R.color.dotmatrix_background,
        labelRes = R.string.app_icon_dotmatrix,
    ),
    ;

    companion object {
        /** Default icon; the only alias with `enabled="true"` in the manifest. */
        val DEFAULT: AppIcon = STENCIL
    }
}
