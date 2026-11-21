package io.github.ra1nify.realos.root

import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.launcher.LauncherConfig

/**
 * Reads zram / swap statistics through root.
 *
 * ColorOS labels `/sys/block/zram0/mm_stat` and `/proc/swaps` as `sysfs_zram` /
 * `proc_swaps`, and SELinux denies reads by normal apps; realOS reads them for the
 * host. Results are cached for CACHE_TTL_MS.
 */
object ZramRootFetcher {

    private const val TAG = "RealOS-ZramFetcher"

    private const val ZRAM_MM_STAT = "/sys/block/zram0/mm_stat"
    private const val PROC_SWAPS = "/proc/swaps"

    /** Cache time-to-live */
    private const val CACHE_TTL_MS = 2000L

    data class Stats(
        val origBytes: Long,
        val comprBytes: Long,
        val totalBytes: Long,
        val externalSwapUsedKb: Long,
        val at: Long
    ) {
        val isValid: Boolean get() = origBytes > 0L && comprBytes > 0L
    }

    @Volatile
    private var cache: Stats? = null

    @Volatile
    private var cacheAt = 0L

    @Volatile
    private var running = false

    /** Reads the statistics with caching; last cached value on failure, null when empty. */
    fun readStats(): Stats? {
        val cached = cache
        if (cached != null && System.currentTimeMillis() - cacheAt <= CACHE_TTL_MS) {
            return cached
        }
        if (running) return cached
        running = true
        try {
            val fresh = doFetch()
            if (fresh != null) {
                cache = fresh
                cacheAt = System.currentTimeMillis()
                return fresh
            }
            return cached
        } finally {
            running = false
        }
    }

    /**
     * Fetches and writes the module preferences for the host fallback read.
     *
     * @return true when zram data was obtained
     */
    fun fetchAndPublish(context: Context): Boolean {
        val stats = readStats() ?: return false
        runCatching {
            LauncherConfig.publishRootStats(
                context = context,
                zramOrigBytes = stats.origBytes,
                zramComprBytes = stats.comprBytes,
                zramTotalBytes = stats.totalBytes,
                externalSwapUsedKb = stats.externalSwapUsedKb,
                at = stats.at
            )
        }.onFailure { Log.w(TAG, "Failed to publish the zram stats", it) }
        return true
    }

    private fun doFetch(): Stats? {
        val mmStat = runCatching { RootManager.suExecRaw("cat $ZRAM_MM_STAT") }.getOrNull()
        if (mmStat == null || !mmStat.isSuccess) {
            Log.w(TAG, "Failed to read mm_stat: exit=${mmStat?.exitCode}")
            return null
        }

        val parts = mmStat.output.trim().split(Regex("\\s+"))
        val orig = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val compr = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        if (orig <= 0L || compr <= 0L) {
            Log.w(TAG, "mm_stat content looks wrong: ${mmStat.output}")
            return null
        }

        val swapUsedKb = runCatching {
            val r = RootManager.suExecRaw("cat $PROC_SWAPS")
            if (r.isSuccess) parseExternalSwapUsedKb(r.output) else 0L
        }.getOrDefault(0L)

        return Stats(
            origBytes = orig,
            comprBytes = compr,
            totalBytes = orig,
            externalSwapUsedKb = swapUsedKb,
            at = System.currentTimeMillis()
        )
    }

    /** Counts swap usage of non-zram backends only (zram is the swap device on ColorOS) */
    private fun parseExternalSwapUsedKb(raw: String): Long {
        var used = 0L
        raw.lineSequence().drop(1).forEach { line ->
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size < 4) return@forEach
            if (cols[0].contains("zram", ignoreCase = true)) return@forEach
            used += cols[3].toLongOrNull() ?: 0L
        }
        return used
    }
}
