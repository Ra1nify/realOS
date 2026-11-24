package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import io.github.ra1nify.realos.clockfont.ClockFontSystemVariant
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Prepares SystemUI clock-font variants. */
internal object ClockFontSystemVariantPreparer {

    private const val TAG = "RealOS-ClockFontHost"

    private val prepared = ConcurrentHashMap.newKeySet<String>()
    private val preparing = AtomicBoolean(false)
    private val dirty = AtomicBoolean(false)

    @Volatile
    private var retryRequest: (() -> Unit)? = null

    fun prepare(
        context: Context,
        entries: List<ClockFontEntry>,
        profile: ClockFontSystemVariant.Profile,
        profileTag: String,
        readBytes: (ClockFontEntry) -> ByteArray?,
        onPrepared: () -> Unit,
        warn: (String, String) -> Unit,
        retry: () -> Unit
    ) {
        if (entries.isEmpty()) return
        retryRequest = retry
        if (!preparing.compareAndSet(false, true)) {
            dirty.set(true)
            return
        }

        Thread({
            var preparedCount = 0
            var failed = 0
            try {
                for (entry in entries) {
                    val file = ClockFontSystemVariantFiles.file(context, profileTag, entry.id)
                    if (file.isFile && file.length() > 0) {
                        prepared.add(entry.id)
                        preparedCount++
                        continue
                    }
                    prepared.remove(entry.id)
                    val bytes = readBytes(entry)
                    if (bytes == null) {
                        failed++
                        continue
                    }
                    if (bytes.size > ClockFontSystemVariantFiles.MAX_BYTES) {
                        warn(
                            "sys-size:${entry.id}",
                            "system variant skipped for ${entry.id}: size=${bytes.size}"
                        )
                        failed++
                        continue
                    }
                    val normalized = ClockFontSystemVariant.normalize(bytes, profile)
                    if (normalized == null ||
                        !ClockFontSystemVariantFiles.writeAtomically(file, normalized)
                    ) {
                        failed++
                        continue
                    }
                    prepared.add(entry.id)
                    preparedCount++
                }
                if (preparedCount > 0) runCatching { onPrepared() }
                if (preparedCount > 0) {
                    Log.i(
                        TAG,
                        "HookPoint[clockfont_system_variant] state=FOUND " +
                            "prepared=$preparedCount failed=$failed total=${entries.size}"
                    )
                } else if (failed > 0) {
                    Log.w(
                        TAG,
                        "HookPoint[clockfont_system_variant] state=ERROR " +
                            "prepared=0 failed=$failed total=${entries.size}"
                    )
                }
            } finally {
                preparing.set(false)
                val next = retryRequest
                retryRequest = null
                if (dirty.getAndSet(false)) runCatching { next?.invoke() }
            }
        }, "RealOS-ClockFontSysVariant").apply { isDaemon = true }.start()
    }

    fun invalidate(id: String) {
        prepared.remove(id)
    }

    fun reset() {
        prepared.clear()
        preparing.set(false)
        dirty.set(false)
        retryRequest = null
    }
}
