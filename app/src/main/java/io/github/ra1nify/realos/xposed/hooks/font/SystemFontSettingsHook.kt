package io.github.ra1nify.realos.xposed.hooks.font

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.font.SystemFontConfig
import io.github.ra1nify.realos.font.SystemFontContract
import io.github.ra1nify.realos.util.ModuleResources
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Injects the realOS font library into the Settings font page.
 *
 * Each imported font becomes a card. Tapping a card applies the font directly in
 * the Settings process through the ROM's own `FontWriter` + `FontManager`, so no
 * broadcast/receiver round-trip is involved. The stock "More fonts" card is
 * renamed to the realOS font manager and opens the realOS Activity for
 * import/delete.
 */
object SystemFontSettingsHook {

    private const val TAG = "RealOS-SystemFont"

    private const val FONT_SETTINGS_FRAGMENT =
        "com.oplus.settings.feature.display.font.FontSettingsFragment"
    private const val FONT_TYPE_ENTRY =
        "com.oplus.settings.feature.display.font.FontSettingsFragment\$FontTypeEntry"
    private const val FONT_TYPE_CARD =
        "com.oplus.settings.feature.display.font.FontTypeSelectableView"
    private const val PERSONALIZATION_FONT_UTILS =
        "com.oplus.settings.feature.display.font.PersonalizationFontUtils"
    private const val FONT_MANAGER = "com.oplus.flipfont.FontManager"
    private const val FONT_WRITER = "com.oplus.flipfont.FontWriter"

    private const val MORE_FONT_TYPE = 4
    private const val CUSTOM_TYPE_BASE = 1000

    private val installed = AtomicBoolean(false)
    private val errorLogged = AtomicBoolean(false)

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var settingsClassLoader: ClassLoader? = null

    private val fontEntries = ConcurrentHashMap<Int, HookFont>()
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    private data class HookFont(val id: String, val name: String)

    fun install(module: XposedModule, classLoader: ClassLoader) {
        this.module = module
        this.settingsClassLoader = classLoader
        if (!installed.compareAndSet(false, true)) return

        val fragmentClass = runCatching { classLoader.loadClass(FONT_SETTINGS_FRAGMENT) }
            .getOrElse {
                module.log(Log.WARN, TAG, "HookPoint[system_font] state=MISSING fragment", it)
                installed.set(false)
                return
            }
        val entryClass = runCatching { classLoader.loadClass(FONT_TYPE_ENTRY) }
            .getOrElse {
                module.log(Log.WARN, TAG, "HookPoint[system_font] state=MISSING entry", it)
                installed.set(false)
                return
            }
        val cardClass = runCatching { classLoader.loadClass(FONT_TYPE_CARD) }
            .getOrElse {
                module.log(Log.WARN, TAG, "HookPoint[system_font] state=MISSING card", it)
                installed.set(false)
                return
            }

        val clickMethod = methodOrNull(fragmentClass, "handleFontTypeItemClick", cardClass, entryClass)
        val nameMethod = methodOrNull(fragmentClass, "setFontTypeName", TextView::class.java, entryClass)
        val previewMethod = methodOrNull(
            fragmentClass,
            "setFontTypePreviewSystemTypeface",
            TextView::class.java,
            Int::class.javaPrimitiveType!!
        )
        val buildMethod = methodOrNull(fragmentClass, "buildFontTypeViews")
        val updateSelectionMethod = methodOrNull(fragmentClass, "updateFontTypeSelection", entryClass)
        val themeStoreMethod = runCatching {
            classLoader.loadClass(PERSONALIZATION_FONT_UTILS)
                .getMethod("isThemeStoreExist", Context::class.java)
        }.getOrNull()

        themeStoreMethod?.let { method ->
            hook(module, "system_font_entry_gate", method) { chain ->
                if (isFeatureEnabled()) true else chain.proceed()
            }
        }

        buildMethod?.let { method ->
            hook(module, "system_font_list", method) { chain ->
                val fragment = chain.thisObject
                if (fragment != null && isFeatureEnabled()) {
                    runCatching { ensureFontEntries(fragment) }
                        .onFailure { if (errorLogged.compareAndSet(false, true)) {
                            module.log(Log.ERROR, TAG, "font list injection failed", it)
                        } }
                    runCatching { adjustSelection(fragment) }
                }
                chain.proceed()
            }
        }

        nameMethod?.let { method ->
            hook(module, "system_font_name", method) { chain ->
                val result = chain.proceed()
                val entry = chain.args.getOrNull(1)
                val textView = chain.args.getOrNull(0) as? TextView
                if (textView != null && entry != null && isFeatureEnabled()) {
                    val type = fontType(entry)
                    if (type == MORE_FONT_TYPE) {
                        textView.text = managerLabel(textView.context)
                    } else if (type >= CUSTOM_TYPE_BASE) {
                        textView.text = fontEntries[type]?.name ?: textView.text
                    }
                }
                result
            }
        }

        previewMethod?.let { method ->
            hook(module, "system_font_preview", method) { chain ->
                val result = chain.proceed()
                val type = (chain.args.getOrNull(1) as? Int) ?: -1
                val textView = chain.args.getOrNull(0) as? TextView
                if (textView != null && type >= CUSTOM_TYPE_BASE && isFeatureEnabled()) {
                    fontEntries[type]?.let { font ->
                        runCatching { applyCardPreview(textView, font) }
                    }
                }
                result
            }
        }

        clickMethod?.let { method ->
            hook(module, "system_font_click", method) { chain ->
                val entry = chain.args.getOrNull(1)
                if (entry != null && isFeatureEnabled()) {
                    val type = fontType(entry)
                    if (type == MORE_FONT_TYPE) {
                        val context = (chain.args.getOrNull(0) as? android.view.View)?.context
                        if (context != null) startFontManager(context)
                        return@hook null
                    }
                    if (type >= CUSTOM_TYPE_BASE) {
                        val font = fontEntries[type]
                        val context = (chain.args.getOrNull(0) as? android.view.View)?.context
                        if (font != null && context != null) {
                            val ok = applyFont(context, font)
                            if (ok) {
                                runCatching {
                                    val fragment = chain.thisObject
                                    updateSelectionMethod?.invoke(fragment, entry)
                                }
                                Toast.makeText(
                                    context,
                                    ModuleResources.string(context, R.string.system_font_applied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    ModuleResources.string(context, R.string.system_font_apply_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        return@hook null
                    }
                }
                chain.proceed()
            }
        }

        module.log(
            Log.INFO,
            TAG,
            "system font page installed: list=${buildMethod != null}, " +
                "click=${clickMethod != null}, name=${nameMethod != null}, preview=${previewMethod != null}"
        )
    }

    fun reset() {
        installed.set(false)
        settingsClassLoader = null
        fontEntries.clear()
        typefaceCache.clear()
    }

    private inline fun hook(
        module: XposedModule,
        key: String,
        method: java.lang.reflect.Method,
        crossinline block: (io.github.libxposed.api.XposedInterface.Chain) -> Any?
    ) {
        runCatching {
            module.hook(method).intercept { chain -> block(chain) }
            module.log(Log.INFO, TAG, "HookPoint[$key] state=FOUND")
        }.onFailure {
            module.log(Log.WARN, TAG, "HookPoint[$key] state=ERROR", it)
        }
    }

    private fun methodOrNull(
        clazz: Class<*>,
        name: String,
        vararg params: Class<*>
    ): java.lang.reflect.Method? = runCatching {
        clazz.getDeclaredMethod(name, *params)
    }.getOrNull()

    private fun isFeatureEnabled(): Boolean {
        val m = module ?: return SystemFontConfig.DEFAULT_ENABLED
        return SystemFontConfig.isEnabled(m)
    }

    // ── List injection ──

    private fun ensureFontEntries(fragment: Any) {
        val context = fragmentContext(fragment) ?: return
        val list = mutableFontTypeList(fragment) ?: return
        list.removeAll { fontType(it) >= CUSTOM_TYPE_BASE }

        val fonts = queryFonts(context)
        fontEntries.clear()
        typefaceCache.keys.retainAll(fonts.map { it.id }.toSet())

        val moreIndex = list.indexOfFirst { fontType(it) == MORE_FONT_TYPE }
            .let { if (it < 0) list.size else it }
        val entries = ArrayList<Any>(fonts.size)
        fonts.forEachIndexed { index, font ->
            val type = CUSTOM_TYPE_BASE + index
            fontEntries[type] = font
            val entry = newFontTypeEntry(type) ?: return@forEachIndexed
            entries.add(entry)
        }
        if (entries.isNotEmpty()) list.addAll(moreIndex, entries)
    }

    private fun adjustSelection(fragment: Any) {
        val context = fragmentContext(fragment) ?: return
        val list = mutableFontTypeList(fragment) ?: return
        val currentTypeface = readSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE)
        val currentName = readSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE_NAME)
        val appliedType = if (currentTypeface == SystemFontContract.CUSTOM_TYPEFACE_VALUE &&
            !currentName.isNullOrBlank()
        ) {
            fontEntries.entries.firstOrNull { it.value.name == currentName }?.key
        } else {
            null
        }

        for (entry in list) {
            val type = fontType(entry)
            if (type >= CUSTOM_TYPE_BASE) {
                setSelected(entry, type == appliedType)
            }
        }
        val more = list.firstOrNull { fontType(it) == MORE_FONT_TYPE }
        if (more != null && appliedType != null) setSelected(more, false)
    }

    private fun mutableFontTypeList(fragment: Any): MutableList<Any>? = runCatching {
        val field = fragment.javaClass.getDeclaredField("mFontTypeList")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(fragment) as? MutableList<Any>)
    }.getOrNull()

    private fun newFontTypeEntry(type: Int): Any? = runCatching {
        val clazz = settingsClassLoader?.loadClass(FONT_TYPE_ENTRY) ?: return null
        val constructor = clazz.getDeclaredConstructor(Int::class.javaPrimitiveType)
        constructor.isAccessible = true
        constructor.newInstance(type)
    }.getOrNull()

    private fun queryFonts(context: Context): List<HookFont> = runCatching {
        val uri = Uri.parse("content://${SystemFontContract.AUTHORITY}/${SystemFontContract.PATH_FONTS}")
        val result = ArrayList<HookFont>()
        context.contentResolver.query(
            uri,
            arrayOf(SystemFontContract.COLUMN_ID, SystemFontContract.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: id
                result.add(HookFont(id, name))
            }
        }
        result
    }.getOrElse {
        if (errorLogged.compareAndSet(false, true)) {
            module?.log(Log.WARN, TAG, "realOS font provider query failed", it)
        }
        emptyList()
    }

    private fun fontType(entry: Any): Int = runCatching {
        entry.javaClass.getDeclaredField("mFontType").apply { isAccessible = true }.getInt(entry)
    }.getOrDefault(-1)

    private fun setSelected(entry: Any, selected: Boolean) {
        runCatching {
            entry.javaClass.getMethod("setSelected", Boolean::class.javaPrimitiveType)
                .invoke(entry, selected)
        }
    }

    private fun fragmentContext(fragment: Any): Context? = runCatching {
        fragment.javaClass.getMethod("getContext").invoke(fragment) as? Context
    }.getOrNull()

    // ── Preview ──

    private fun applyCardPreview(textView: TextView, font: HookFont) {
        textView.setBackgroundColor(0)
        textView.text = "Aa"
        val typeface = typefaceCache.getOrPut(font.id) { loadTypeface(textView.context, font.id) }
        textView.typeface = typeface
    }

    private fun loadTypeface(context: Context, id: String): Typeface {
        val fallback = Typeface.DEFAULT
        return runCatching {
            val uri = Uri.parse(
                "content://${SystemFontContract.AUTHORITY}/${SystemFontContract.PATH_FONTS}/$id"
            )
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                Typeface.Builder(pfd.fileDescriptor).build()
            } ?: fallback
        }.getOrDefault(fallback)
    }

    private fun managerLabel(context: Context): String =
        ModuleResources.string(context, R.string.system_font_manager_label)

    // ── Apply ──

    private fun applyFont(context: Context, font: HookFont): Boolean {
        val classLoader = settingsClassLoader ?: return false
        return runCatching {
            val fmClass = classLoader.loadClass(FONT_MANAGER)
            val writerClass = classLoader.loadClass(FONT_WRITER)
            val writer = writerClass.getConstructor().newInstance()
            val createDir = writerClass.getMethod("createFontDirectory")
            val copyFont = writerClass.getMethod(
                "copyFontFileInternet",
                java.io.File::class.java,
                InputStream::class.java,
                String::class.java
            )
            val notify = fmClass.getMethod("notifySystem", Context::class.java)

            val oldTypeface = readSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE)
            val oldName = readSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE_NAME)
            setSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE, SystemFontContract.CUSTOM_TYPEFACE_VALUE)
            setSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE_NAME, font.name)

            val uri = Uri.parse(
                "content://${SystemFontContract.AUTHORITY}/${SystemFontContract.PATH_FONTS}/${font.id}"
            )
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                val dir = createDir.invoke(writer) as? java.io.File ?: return@runCatching false
                copyFont.invoke(writer, dir, input, "Customized-Regular.ttf")
                val target = java.io.File(dir, "Customized-Regular.ttf")
                target.isFile && target.length() > 0L
            } ?: false

            if (!copied) {
                setSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE, oldTypeface)
                setSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE_NAME, oldName)
                return@runCatching false
            }

            runCatching { notify.invoke(null, context) }
            true
        }.getOrElse {
            module?.log(Log.WARN, TAG, "apply font failed", it)
            false
        }
    }

    private fun readSetting(context: Context, key: String): String? = runCatching {
        Settings.System.getString(context.contentResolver, key)
    }.getOrNull()

    private fun setSetting(context: Context, key: String, value: String?) {
        runCatching {
            Settings.System.putString(context.contentResolver, key, value ?: "")
        }
    }

    private fun startFontManager(context: Context): Boolean {
        val intent = Intent()
            .setClassName(SystemFontContract.REALOS_PACKAGE, SystemFontContract.FONT_MANAGER_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    @Suppress("unused")
    private fun currentApplication(): android.app.Application? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        activityThread.getMethod("currentApplication").invoke(null) as? android.app.Application
    }.getOrNull()
}
