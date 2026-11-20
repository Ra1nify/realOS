package io.github.ra1nify.realos.xposed.common

import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.util.TypedValue

/**
 * Resource bridge for host injection.
 *
 * Module and host APKs both use resource package id `0x7f`; a module resource id resolves
 * to an unrelated host resource in the host process.
 *
 * Resolution:
 * - Text: host first, module fallback.
 * - Drawables and values: module first, host fallback.
 */
class SafeModuleResources(
    private val base: Resources,
    private val moduleResources: Resources?,
) : Resources(base.assets, base.displayMetrics, base.configuration) {

    /** Resolves from module resources first, then host resources. */
    private inline fun <T> moduleFirst(block: (Resources) -> T): T? {
        moduleResources?.let { module ->
            runCatching { block(module) }.getOrNull()?.let { return it }
        }
        return runCatching { block(base) }.getOrNull()
    }

    // Text: host first.

    override fun getText(id: Int): CharSequence =
        runCatching { base.getText(id) }
            .getOrElse { runCatching { moduleResources?.getText(id) }.getOrNull() ?: "" }

    override fun getText(id: Int, def: CharSequence?): CharSequence =
        runCatching { base.getText(id, def) }
            .getOrElse { runCatching { moduleResources?.getText(id, def) }.getOrNull() ?: (def ?: "") }

    override fun getString(id: Int): String =
        runCatching { base.getString(id) }
            .getOrElse { runCatching { moduleResources?.getString(id) }.getOrNull() ?: "" }

    override fun getString(id: Int, vararg formatArgs: Any?): String =
        runCatching { base.getString(id, *formatArgs) }
            .getOrElse { runCatching { moduleResources?.getString(id, *formatArgs) }.getOrNull() ?: "" }

    override fun getTextArray(id: Int): Array<CharSequence> =
        runCatching { moduleResources?.getTextArray(id) }.getOrNull() ?: emptyArray()

    override fun getStringArray(id: Int): Array<String> =
        runCatching { base.getStringArray(id) }
            .getOrElse { runCatching { moduleResources?.getStringArray(id) }.getOrNull() ?: emptyArray() }

    // Drawables and values: module first.

    override fun getValue(id: Int, outValue: TypedValue, resolveRefs: Boolean) {
        moduleResources?.let { module ->
            if (runCatching { module.getValue(id, outValue, resolveRefs) }.isSuccess) return
        }
        base.getValue(id, outValue, resolveRefs)
    }

    override fun getValueForDensity(id: Int, density: Int, outValue: TypedValue, resolveRefs: Boolean) {
        moduleResources?.let { module ->
            if (runCatching { module.getValueForDensity(id, density, outValue, resolveRefs) }.isSuccess) return
        }
        base.getValueForDensity(id, density, outValue, resolveRefs)
    }

    @Suppress("DEPRECATION")
    override fun getDrawable(id: Int): Drawable? = moduleFirst { it.getDrawable(id) }

    @Suppress("DEPRECATION")
    override fun getDrawable(id: Int, theme: Theme?): Drawable? =
        moduleFirst { it.getDrawable(id, theme) }

    @Suppress("DEPRECATION")
    override fun getDrawableForDensity(id: Int, density: Int): Drawable? =
        moduleFirst { it.getDrawableForDensity(id, density) }

    @Suppress("DEPRECATION")
    override fun getDrawableForDensity(id: Int, density: Int, theme: Theme?): Drawable? =
        moduleFirst { it.getDrawableForDensity(id, density, theme) }

    @Suppress("DEPRECATION")
    override fun getColor(id: Int): Int = moduleFirst { it.getColor(id) } ?: base.getColor(id)

    @Suppress("DEPRECATION")
    override fun getColor(id: Int, theme: Theme?): Int =
        moduleFirst { it.getColor(id, theme) } ?: base.getColor(id, theme)

    override fun getColorStateList(id: Int, theme: Theme?) =
        moduleFirst { it.getColorStateList(id, theme) } ?: base.getColorStateList(id, theme)

    override fun getDimension(id: Int): Float = moduleFirst { it.getDimension(id) } ?: base.getDimension(id)

    override fun getDimensionPixelSize(id: Int): Int =
        moduleFirst { it.getDimensionPixelSize(id) } ?: base.getDimensionPixelSize(id)

    override fun getDimensionPixelOffset(id: Int): Int =
        moduleFirst { it.getDimensionPixelOffset(id) } ?: base.getDimensionPixelOffset(id)

    override fun getInteger(id: Int): Int = moduleFirst { it.getInteger(id) } ?: base.getInteger(id)

    override fun getBoolean(id: Int): Boolean = moduleFirst { it.getBoolean(id) } ?: base.getBoolean(id)

    override fun getFraction(id: Int, baseValue: Int, pValue: Int): Float =
        moduleFirst { it.getFraction(id, baseValue, pValue) }
            ?: base.getFraction(id, baseValue, pValue)

    override fun obtainTypedArray(id: Int): TypedArray =
        moduleFirst { it.obtainTypedArray(id) } ?: base.obtainTypedArray(id)

    override fun openRawResource(id: Int) = moduleFirst { it.openRawResource(id) } ?: base.openRawResource(id)

    override fun openRawResource(id: Int, value: TypedValue) =
        moduleFirst { it.openRawResource(id, value) } ?: base.openRawResource(id, value)

    override fun openRawResourceFd(id: Int) =
        moduleFirst { it.openRawResourceFd(id) } ?: base.openRawResourceFd(id)
}
