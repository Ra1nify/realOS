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
    const val COLUMN_HIDDEN = "hidden"
    const val COLUMN_SIZE_BYTES = "size_bytes"
    const val COLUMN_SHA256 = "sha256"

    /** LSPosed remote metadata group. */
    const val REMOTE_PREFS = "clockfont_config"
    const val REMOTE_KEY_VERSION = "version"
    const val REMOTE_KEY_REVISION = "revision"
    const val REMOTE_KEY_ENTRIES = "entries"
    const val REMOTE_KEY_STATUS_BAR_CLOCK = "status_bar_clock_enabled"
    const val REMOTE_CONFIG_VERSION = 2
    const val REMOTE_FILE_PREFIX = "clockfont_"

    fun remoteFileName(id: String): String = "$REMOTE_FILE_PREFIX$id.ttf"

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
        // The host may store the value with a variable:/fixed: path prefix.
        val normalized = raw.removePrefix("variable:").removePrefix("fixed:")
        val idx = normalized.indexOf(VALUE_PREFIX)
        if (idx >= 0) {
            val after = normalized.substring(idx + VALUE_PREFIX.length)
            return after.substringBefore('/').substringBefore('?').takeIf { it.isNotEmpty() }
        }
        // Legacy bare cf_ ids, including fixed:cf_xxx / variable:cf_xxx.
        if (normalized.startsWith("cf_")) {
            return normalized.substringBefore('/').substringBefore('?').takeIf { it.isNotEmpty() }
        }
        return null
    }
}
