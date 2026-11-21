package io.github.ra1nify.realos.root

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.launcher.LauncherConfig

/**
 * ContentProvider exposing zram statistics to the host launcher through
 * `ContentResolver.call()`. Serves read-only memory statistics from the internal cache.
 */
class LauncherStatsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != LauncherConfig.METHOD_GET_ZRAM_STATS) return Bundle.EMPTY

        val stats = runCatching { ZramRootFetcher.readStats() }.getOrElse {
            Log.w(TAG, "readStats failed", it)
            null
        } ?: return Bundle.EMPTY

        return Bundle().apply {
            putLong(LauncherConfig.KEY_STATS_ORIG, stats.origBytes)
            putLong(LauncherConfig.KEY_STATS_COMPR, stats.comprBytes)
            putLong(LauncherConfig.KEY_STATS_TOTAL, stats.totalBytes)
            putLong(LauncherConfig.KEY_STATS_SWAP_USED, stats.externalSwapUsedKb)
            putLong(LauncherConfig.KEY_STATS_AT, stats.at)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val TAG = "RealOS-LauncherStatsProvider"
    }
}
