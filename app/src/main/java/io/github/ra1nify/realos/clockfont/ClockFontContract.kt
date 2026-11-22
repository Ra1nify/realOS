package io.github.ra1nify.realos.clockfont

import android.net.Uri
import io.github.ra1nify.realos.BuildConfig

/**
 * Shared contract between the realOS app/provider and the clock-font hooks.
 *
 * Values stored in `timeFamilyName` use [VALUE_PREFIX] so they cannot be confused with
 * stock asset paths or `/system/fonts` paths.
 */
object ClockFontContract {
    val AUTHORITY = "${BuildConfig.APPLICATION_ID}.clockfonts"
    const val VALUE_PREFIX = "realos-clock://"
    const val MIME_TYPE = "font/ttf"

    const val PATH_FONTS = "fonts"

    const val COLUMN_ID = "_id"
    const val COLUMN_DISPLAY_NAME = "display_name"
    const val COLUMN_TYPE = "type"
    const val COLUMN_DYNAMIC_WEIGHT = "dynamic_weight"
    const val COLUMN_SIZE_BYTES = "size_bytes"
    const val COLUMN_SHA256 = "sha256"

    const val TYPE_FIXED = "fixed"
    const val TYPE_FLEX = "flex"

    const val REGISTRY_VERSION = 1
    const val MAX_FILE_BYTES = 16L * 1024L * 1024L
    const val MIN_FILE_BYTES = 1024L

    val FONTS_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_FONTS")

    fun fontUri(id: String): Uri = Uri.parse("content://$AUTHORITY/$PATH_FONTS/$id")

    fun valueFor(id: String): String = VALUE_PREFIX + id

    fun idFromValue(value: String?): String? {
        val raw = value ?: return null
        val idx = raw.indexOf(VALUE_PREFIX)
        if (idx >= 0) {
            val after = raw.substring(idx + VALUE_PREFIX.length)
            return after.substringBefore('/').substringBefore('?').takeIf { it.isNotEmpty() }
        }
        if (raw.startsWith("cf_")) {
            return raw.substringBefore('/').substringBefore('?').takeIf { it.isNotEmpty() }
        }
        return null
    }
}
