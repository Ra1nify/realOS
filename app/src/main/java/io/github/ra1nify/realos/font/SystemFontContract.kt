package io.github.ra1nify.realos.font

import android.net.Uri
import io.github.ra1nify.realos.BuildConfig

/**
 * Shared contract between the realOS font library, its read-only provider and
 * the Settings-process hook that applies the selected font.
 */
object SystemFontContract {

    val AUTHORITY = "${BuildConfig.APPLICATION_ID}.systemfonts"
    const val MIME_TYPE = "font/ttf"

    const val PATH_FONTS = "fonts"

    val REALOS_PACKAGE = BuildConfig.APPLICATION_ID
    const val FONT_MANAGER_ACTIVITY = "io.github.ra1nify.realos.font.SystemFontManagerActivity"

    /** Framework settings keys written by the Settings process. */
    const val SETTING_CURRENT_TYPEFACE = "current_typeface"
    const val SETTING_CURRENT_TYPEFACE_NAME = "current_typeface_name"
    const val SETTING_FONT_ID = "realos_font_id"

    /** Synthetic owner value for a realOS-imported font. */
    const val CUSTOM_TYPEFACE_VALUE = "realos.font"

    const val COLUMN_ID = "_id"
    const val COLUMN_DISPLAY_NAME = "display_name"
    const val COLUMN_SIZE_BYTES = "size_bytes"
    const val COLUMN_SHA256 = "sha256"

    fun fontUri(id: String): Uri = Uri.parse("content://$AUTHORITY/$PATH_FONTS/$id")
}
