package io.github.ra1nify.realos.xposed.hooks.launcher

import android.app.ActivityManager
import android.content.Context
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.io.File
import java.util.Locale
import io.github.ra1nify.realos.launcher.LauncherConfig

/**
 * Real memory data reader.
 *
 * ColorOS specifics measured on device:
 * - `/proc/meminfo` is readable (`proc_meminfo`) and provides MemTotal/MemAvailable/SwapTotal/SwapFree;
 * - `/sys/block/zram0/mm_stat` and `/proc/swaps` are blocked by SELinux (`sysfs_zram` / `proc_swaps`),
 *   denied even for shell and unreadable for the host (platform_app domain);
 * - the host does not declare `android.permission.DUMP`, so `dumpsys meminfo` is unavailable.
 *
 * zram compression data therefore comes from the realOS app reading it through root (see
 * [LauncherConfig.readRootStats]); missing or stale data falls back to the `/proc/meminfo` swap metric.
 */
object LauncherMemoryReader {

    private const val TAG = "RealOS-LauncherMemory"

    private const val PROC_MEMINFO = "/proc/meminfo"

    data class Snapshot(
        val totalKb: Long,
        val availKb: Long,
        val freeKb: Long,
        val buffersKb: Long,
        val cachedKb: Long,
        val swapTotalKb: Long,
        val swapFreeKb: Long,
        val swapCachedKb: Long
    ) {
        val usedKb: Long get() = (totalKb - availKb).coerceAtLeast(0L)
        val usedPercent: Int
            get() = if (totalKb <= 0L) 0 else ((usedKb * 100) / totalKb).toInt()
        val swapUsedKb: Long get() = (swapTotalKb - swapFreeKb).coerceAtLeast(0L)
    }

    fun read(): Snapshot? {
        val memInfo = runCatching { readMemInfo() }.getOrElse {
            Log.w(TAG, "Failed to read $PROC_MEMINFO; dropping the real memory display", it)
            null
        } ?: return null

        val totalKb = memInfo["MemTotal"] ?: return null
        if (totalKb <= 0L) return null

        return Snapshot(
            totalKb = totalKb,
            availKb = memInfo["MemAvailable"] ?: memInfo["MemFree"] ?: 0L,
            freeKb = memInfo["MemFree"] ?: 0L,
            buffersKb = memInfo["Buffers"] ?: 0L,
            cachedKb = memInfo["Cached"] ?: 0L,
            swapTotalKb = memInfo["SwapTotal"] ?: 0L,
            swapFreeKb = memInfo["SwapFree"] ?: 0L,
            swapCachedKb = memInfo["SwapCached"] ?: 0L
        )
    }

    /**
     * Single-line real data text.
     *
     * - with root data: `RAM 2.7G/15G (82%) · zRAM 4.2G/9.6G 43%`
     * - without root data (fallback): `RAM 2.7G/15G (82%) · Swap 10.3G`
     */
    fun formatReal(snapshot: Snapshot, rootStats: LauncherConfig.RootZramStats?): String {
        val availGb = snapshot.availKb.toDouble() / 1024.0 / 1024.0
        val totalGb = snapshot.totalKb.toDouble() / 1024.0 / 1024.0

        val sb = StringBuilder()
        sb.append("RAM ")
        sb.append(String.format(Locale.US, "%.1fG", availGb))
        sb.append('/')
        sb.append(String.format(Locale.US, "%.0fG", totalGb))
        sb.append(" (")
        sb.append(snapshot.usedPercent)
        sb.append("%)")

        if (rootStats != null && rootStats.isValid) {
            sb.append(" · zRAM ")
            sb.append(gb(rootStats.zramComprBytes))
            sb.append('/')
            sb.append(gb(rootStats.zramOrigBytes))
            sb.append(' ')
            sb.append((rootStats.zramComprBytes * 100 / rootStats.zramOrigBytes).toInt())
            sb.append('%')

            if (rootStats.externalSwapUsedKb > 0L) {
                appendSwap(sb, rootStats.externalSwapUsedKb)
            }
        } else {
            // Fallback: without root data the swap metric is used (swap is the zram pool on ColorOS)
            if (snapshot.swapUsedKb > 0L) {
                appendSwap(sb, snapshot.swapUsedKb)
            }
        }
        return sb.toString()
    }

    /**
     * Official text (available / total plus virtual extension, no inflated sum).
     * Used when the user turns off "real memory info".
     */
    fun formatOfficial(context: Context, snapshot: Snapshot?): String {
        val totalGb: Double
        val availGb: Double
        if (snapshot != null) {
            totalGb = snapshot.totalKb.toDouble() / 1024.0 / 1024.0
            availGb = snapshot.availKb.toDouble() / 1024.0 / 1024.0
        } else {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return ""
            val info = ActivityManager.MemoryInfo()
            runCatching { am.getMemoryInfo(info) }.onFailure { return "" }
            totalGb = info.totalMem.toDouble() / 1024.0 / 1024.0 / 1024.0
            availGb = info.availMem.toDouble() / 1024.0 / 1024.0 / 1024.0
        }

        val expandGb = readNandSwapGb()
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "%.1f GB", availGb))
        sb.append(LauncherUiText.availableSuffix(context))
        sb.append(" | ")
        sb.append(String.format(Locale.US, "%.0f GB", totalGb))
        if (expandGb > 0f) {
            sb.append(" + ")
            sb.append(String.format(Locale.US, "%.1f GB", expandGb))
        }
        return sb.toString()
    }

    private fun appendSwap(sb: StringBuilder, swapUsedKb: Long) {
        val swapUsedMb = swapUsedKb / 1024
        sb.append(" · Swap ")
        if (swapUsedMb >= 1024) {
            sb.append(String.format(Locale.US, "%.1fG", swapUsedMb / 1024.0))
        } else {
            sb.append(swapUsedMb).append('M')
        }
    }

    /** ColorOS NandSwap virtual memory extension size in GB; 0 when unreadable */
    private fun readNandSwapGb(): Float = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java, String::class.java)
        val raw = get.invoke(null, "persist.sys.oplus.nandswap.swapsize.curr", "0") as? String
        raw?.toFloatOrNull() ?: 0f
    }.getOrDefault(0f)

    private fun readMemInfo(): Map<String, Long> {
        val result = HashMap<String, Long>(64)
        val file = File(PROC_MEMINFO)
        if (!file.canRead()) return result
        file.useLines { lines ->
            lines.forEach { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) return@forEach
                val key = line.substring(0, idx).trim()
                val rest = line.substring(idx + 1).trim()
                val value = rest.substringBefore(' ').toLongOrNull() ?: return@forEach
                result[key] = value
            }
        }
        return result
    }

    private fun gb(bytes: Long): String =
        String.format(Locale.US, "%.1fG", bytes.toDouble() / 1024.0 / 1024.0 / 1024.0)
}
