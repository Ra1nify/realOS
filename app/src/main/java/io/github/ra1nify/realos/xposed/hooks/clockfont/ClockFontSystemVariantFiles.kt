package io.github.ra1nify.realos.xposed.hooks.clockfont

import android.content.Context
import java.io.File

/** SystemUI variant file naming and atomic write. */
internal object ClockFontSystemVariantFiles {

    private const val PREFIX = "clockfont_sys_"
    private const val VERSION = 2

    const val MAX_BYTES = 4 * 1024 * 1024

    fun file(context: Context, profileTag: String, id: String): File = File(
        context.cacheDir,
        "${PREFIX}${VERSION}_${profileTag}_$id.ttf"
    )

    fun writeAtomically(file: File, bytes: ByteArray): Boolean {
        val dir = file.parentFile ?: return false
        if (!dir.exists() && !dir.mkdirs()) return false
        return runCatching {
            val temp = File(dir, file.name + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            true
        }.getOrDefault(false)
    }
}
