package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.clockfont.ClockFontContract
import io.github.ra1nify.realos.clockfont.ClockFontSystemVariant
import io.github.ra1nify.realos.clockfont.ClockFontType
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Host state required by the typeface factory. */
internal interface ClockFontTypefaceHost {
    fun hostContext(): Context?
    fun hostModule(): XposedModule?
    fun hasRemotePrefs(): Boolean
    fun hostEntries(): List<ClockFontEntry>
    fun warnOnce(key: String, message: String)
    fun refreshAsync(context: Context)
    fun prepareSystemVariants()
}

/** Builds clock-font typefaces. */
internal object ClockFontTypefaceFactory {

    private const val TAG = "RealOS-ClockFontHost"
    private const val MAX_TYPEFACE_CACHE = 64

    private val cache = ConcurrentHashMap<String, Typeface>()

    /** Stock SystemUI clock profile. */
    val stockProfile: ClockFontSystemVariant.Profile by lazy {
        runCatching {
            val paint = Paint().apply {
                typeface = Typeface.create("sans-serif-semibold", Typeface.NORMAL)
                textSize = 1000f
            }
            val metrics = paint.fontMetrics
            val widths = FloatArray(1)
            val advances = buildMap {
                for (ch in "0123456789:. ") {
                    if (paint.getTextWidths(ch.toString(), widths) > 0 && widths[0] > 0f) {
                        put(ch, widths[0].roundToInt())
                    }
                }
            }.toMutableMap()
            // Keep digits tabular.
            val digitAdvance = ('0'..'9').mapNotNull { advances[it] }.maxOrNull()
            if (digitAdvance != null) {
                for (digit in '0'..'9') advances[digit] = digitAdvance
            }
            ClockFontSystemVariant.Profile(
                ascentRatio = (-metrics.ascent / 1000f).coerceIn(0.5f, 1.5f),
                descentRatio = (metrics.descent / 1000f).coerceIn(0.0f, 0.6f),
                lineGapRatio = (metrics.leading / 1000f).coerceIn(0f, 0.5f),
                advanceAt1000 = advances
            )
        }.getOrDefault(
            ClockFontSystemVariant.Profile(0.841f, 0.277f, 0f, emptyMap())
        )
    }

    /** Variant cache tag for the current profile. */
    val profileTag: String by lazy {
        Integer.toHexString(stockProfile.hashCode())
    }

    fun build(
        host: ClockFontTypefaceHost,
        context: Context?,
        value: String?,
        weight: Int,
        height: Int,
        origin: String,
        normalizeMetrics: Boolean = false
    ): Typeface? {
        val id = ClockFontContract.idFromValue(value) ?: return null
        val entry = host.hostEntries().firstOrNull { it.id == id }
            ?: run {
                host.hostContext()?.let { host.refreshAsync(it) }
                host.warnOnce("missing:$id", "clock font $id is not in the host snapshot")
                return null
            }

        val safeWeight = if (entry.type == ClockFontType.FLEX) {
            weight.coerceIn(100, 1000)
        } else {
            weight.coerceIn(100, 700)
        }
        val fontHeight = effectiveHeight(entry.type, height)
        val metricsKey = if (normalizeMetrics) "sys" else "raw"
        val cacheKey =
            "${entry.id}|${entry.type.wireValue}|$safeWeight|$fontHeight|$metricsKey"
        cache[cacheKey]?.let { return it }

        val settings = if (entry.type == ClockFontType.FLEX) {
            "'wght' $safeWeight, 'opsz' 1010, 'HGHT' $fontHeight"
        } else {
            "'wght' $safeWeight, 'opsz' 1010"
        }

        if (normalizeMetrics) {
            val ctx = context ?: host.hostContext() ?: return null
            val file = ClockFontSystemVariantFiles.file(ctx, profileTag, entry.id)
            if (file.isFile && file.length() > 0) {
                val typeface = runCatching { buildFromFile(file, safeWeight, settings) }
                    .onFailure {
                        host.warnOnce(
                            "sys-file:${entry.id}",
                            "failed to build system variant for ${entry.id}: ${it.message}"
                        )
                    }
                    .getOrNull()
                if (typeface != null) {
                    cacheTypeface(cacheKey, typeface)
                    Log.d(
                        TAG,
                        "built ${entry.displayName} id=${entry.id} type=${entry.type} " +
                            "weight=$safeWeight height=$fontHeight origin=$origin " +
                            "source=sys-variant"
                    )
                    return typeface
                }
                ClockFontSystemVariantPreparer.invalidate(entry.id)
                // Remove a failed variant for regeneration.
                runCatching { file.delete() }
            }

            if (host.hasRemotePrefs()) {
                host.prepareSystemVariants()
                host.warnOnce(
                    "sys-pending:${entry.id}",
                    "system variant not prepared yet for ${entry.id}; using stock until ready"
                )
                return null
            }

            // Legacy provider path.
            val bytes = loadProviderBytes(host, ctx, entry)
            val typeface = bytes?.let {
                buildSystemVariant(host, ctx, entry, it, safeWeight, settings, "provider")
            }
            if (typeface != null) {
                cacheTypeface(cacheKey, typeface)
                Log.d(
                    TAG,
                    "built ${entry.displayName} id=${entry.id} weight=$safeWeight " +
                        "height=$fontHeight origin=$origin source=provider-sys"
                )
            } else {
                Log.w(
                    TAG,
                    "no system typeface for ${entry.displayName} id=${entry.id} origin=$origin"
                )
            }
            return typeface
        }

        val remote = loadRemote(host, entry, safeWeight, settings)
        val typeface = remote ?: if (!host.hasRemotePrefs()) {
            loadProvider(host, context ?: host.hostContext(), entry, safeWeight, settings)
        } else {
            // No provider fallback on the render path.
            host.hostContext()?.let { host.refreshAsync(it) }
            null
        }
        val source = when {
            remote != null -> "remote"
            typeface != null -> "provider"
            else -> "none"
        }

        if (typeface != null) {
            cacheTypeface(cacheKey, typeface)
            Log.d(
                TAG,
                "built ${entry.displayName} id=${entry.id} type=${entry.type} " +
                    "weight=$safeWeight height=$fontHeight origin=$origin source=$source"
            )
        } else {
            Log.w(
                TAG,
                "no typeface for ${entry.displayName} id=${entry.id} origin=$origin " +
                    "(source=$source)"
            )
        }
        return typeface
    }

    /** Clamps Flex height to [1, 1000]. */
    fun effectiveHeight(type: ClockFontType, height: Int): Int {
        val safe = height.coerceIn(0, 1000)
        return if (type == ClockFontType.FLEX) safe.coerceIn(1, 1000) else safe
    }

    fun readEntryBytes(host: ClockFontTypefaceHost, entry: ClockFontEntry): ByteArray? {
        val currentModule = host.hostModule()
        if (currentModule != null) {
            val remote = runCatching {
                currentModule.openRemoteFile(ClockFontContract.remoteFileName(entry.id))
                    .use { pfd -> readBytes(pfd) }
            }.onFailure {
                host.warnOnce(
                    "remote-bytes:${entry.id}",
                    "remote font bytes unavailable for ${entry.id}: ${it.message}"
                )
            }.getOrNull()
            if (remote != null) return remote
        }
        // Provider bytes are legacy-only.
        if (!host.hasRemotePrefs()) {
            return loadProviderBytes(host, host.hostContext(), entry)
        }
        return null
    }

    fun clearCache() {
        cache.clear()
    }

    private fun cacheTypeface(key: String, typeface: Typeface) {
        if (cache.size >= MAX_TYPEFACE_CACHE) cache.clear()
        cache[key] = typeface
    }

    private fun loadRemote(
        host: ClockFontTypefaceHost,
        entry: ClockFontEntry,
        weight: Int,
        settings: String
    ): Typeface? {
        val currentModule = host.hostModule() ?: return null
        return runCatching {
            currentModule.openRemoteFile(ClockFontContract.remoteFileName(entry.id))
                .use { pfd -> buildFromDescriptor(pfd, weight, settings) }
        }.onFailure {
            host.warnOnce(
                "remote:${entry.id}",
                "remote font unavailable for ${entry.id}: ${it.message}"
            )
        }.getOrNull()
    }

    private fun loadProvider(
        host: ClockFontTypefaceHost,
        context: Context?,
        entry: ClockFontEntry,
        weight: Int,
        settings: String
    ): Typeface? {
        val ctx = context ?: return null
        return runCatching {
            ctx.contentResolver.openFileDescriptor(ClockFontContract.fontUri(entry.id), "r")
                ?.use { pfd -> buildFromDescriptor(pfd, weight, settings) }
        }.onFailure {
            host.warnOnce(
                "provider:${entry.id}",
                "clock-font provider failed for ${entry.id}: ${it.message}"
            )
        }.getOrNull()
    }

    private fun loadProviderBytes(
        host: ClockFontTypefaceHost,
        context: Context?,
        entry: ClockFontEntry
    ): ByteArray? {
        val ctx = context ?: return null
        return runCatching {
            ctx.contentResolver.openFileDescriptor(ClockFontContract.fontUri(entry.id), "r")
                ?.use { pfd -> readBytes(pfd) }
        }.onFailure {
            host.warnOnce(
                "provider-bytes:${entry.id}",
                "provider font bytes unavailable for ${entry.id}: ${it.message}"
            )
        }.getOrNull()
    }

    private fun buildFromDescriptor(
        pfd: android.os.ParcelFileDescriptor,
        weight: Int,
        settings: String
    ): Typeface =
        Typeface.Builder(pfd.fileDescriptor)
            .setWeight(weight)
            .setItalic(false)
            .setFontVariationSettings(settings)
            .setFallback("sans-serif")
            .build()

    private fun buildFromFile(file: File, weight: Int, settings: String): Typeface =
        Typeface.Builder(file)
            .setWeight(weight)
            .setItalic(false)
            .setFontVariationSettings(settings)
            .setFallback("sans-serif")
            .build()

    private fun readBytes(pfd: android.os.ParcelFileDescriptor): ByteArray =
        FileInputStream(pfd.fileDescriptor).use { it.readBytes() }

    private fun buildSystemVariant(
        host: ClockFontTypefaceHost,
        context: Context,
        entry: ClockFontEntry,
        source: ByteArray,
        weight: Int,
        settings: String,
        sourceLabel: String
    ): Typeface? {
        if (source.size > ClockFontSystemVariantFiles.MAX_BYTES) {
            host.warnOnce(
                "sys-size:${entry.id}",
                "system variant skipped for ${entry.id}: size=${source.size}"
            )
            return null
        }
        val normalized = ClockFontSystemVariant.normalize(source, stockProfile)
        if (normalized == null) {
            host.warnOnce(
                "sys-normalize:${entry.id}",
                "cannot normalize system variant for ${entry.id}"
            )
            return null
        }
        val file = ClockFontSystemVariantFiles.file(context, profileTag, entry.id)
        if (!ClockFontSystemVariantFiles.writeAtomically(file, normalized)) {
            host.warnOnce("sys-write:${entry.id}", "cannot write system variant for ${entry.id}")
            return null
        }
        Log.i(TAG, "system variant ready for ${entry.id} source=$sourceLabel")
        return runCatching { buildFromFile(file, weight, settings) }.getOrNull()
    }
}
