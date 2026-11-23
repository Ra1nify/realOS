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
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Injects the realOS font library into the Settings font page.
 *
 * Imported fonts are appended after the stock cards, followed by a dedicated
 * realOS font-manager card; the stock theme-store "More fonts" card keeps its
 * own behavior and can be hidden by a separate switch. Tapping a font card only
 * stages the selection and updates the preview; the page's own Apply button
 * commits it through `FontDisplayModel.commit`, which is where the realOS hook
 * writes the file with `FontWriter` and refreshes `FontManager`.
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
    private const val FONT_DISPLAY_MODEL =
        "com.oplus.settings.feature.display.font.FontDisplayModel"

    private const val MORE_FONT_TYPE = 4
    private const val CUSTOM_TYPE_BASE = 1000
    private const val MANAGER_FONT_TYPE = 999

    private val installed = AtomicBoolean(false)
    private val listErrorLogged = AtomicBoolean(false)
    private val queryErrorLogged = AtomicBoolean(false)
    private val cardErrorLogged = AtomicBoolean(false)

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var settingsClassLoader: ClassLoader? = null

    private val fontEntries = ConcurrentHashMap<Int, HookFont>()
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    @Volatile
    private var pendingFont: HookFont? = null

    @Volatile
    private var pendingFragment: WeakReference<Any>? = null

    /** True while a stock card click synchronously rebuilds the card list. */
    @Volatile
    private var builtInStaged = false

    @Volatile
    private var lastRevision = 0

    private data class HookFont(val id: String, val name: String)

    fun install(module: XposedModule, classLoader: ClassLoader) {
        this.module = module
        this.settingsClassLoader = classLoader
        if (!installed.compareAndSet(false, true)) return
        lastRevision = SystemFontConfig.revision(module)

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
        val onResumeMethod = methodOrNull(fragmentClass, "onResume")
        val modelClass = runCatching { classLoader.loadClass(FONT_DISPLAY_MODEL) }.getOrNull()
        val commitMethod = modelClass?.let { clazz ->
            runCatching {
                clazz.getDeclaredMethod(
                    "commit",
                    Context::class.java,
                    Int::class.javaPrimitiveType!!
                )
            }.getOrNull()
        }
        val modelFieldsPresent = modelClass?.let { clazz ->
            runCatching {
                clazz.getDeclaredField("mFontDisplaySettingsUserSet").type
                    .getDeclaredField("mSelectFontTypeface")
                true
            }.getOrDefault(false)
        } ?: false
        val fragmentFieldsPresent = runCatching {
            fragmentClass.getDeclaredField("mFontDisplayModel")
            fragmentClass.getDeclaredField("mFontTypeList")
            true
        }.getOrDefault(false)
        val entryFieldPresent = runCatching {
            entryClass.getDeclaredField("mFontType")
            true
        }.getOrDefault(false)
        val themeStoreMethod = runCatching {
            classLoader.loadClass(PERSONALIZATION_FONT_UTILS)
                .getMethod("isThemeStoreExist", Context::class.java)
        }.getOrNull()

        // Cards are only injected when the whole select -> apply chain exists.
        // Otherwise a card would be visible but the stock Apply button could
        // never commit it.
        val canInjectCards = buildMethod != null && clickMethod != null &&
            updateSelectionMethod != null && commitMethod != null &&
            modelFieldsPresent && fragmentFieldsPresent && entryFieldPresent
        if (!canInjectCards) {
            module.log(
                Log.WARN,
                TAG,
                "HookPoint[system_font_list] state=MISSING reason=incomplete apply chain " +
                    "(build=${buildMethod != null}, click=${clickMethod != null}, " +
                    "selection=${updateSelectionMethod != null}, commit=${commitMethod != null}, " +
                    "model=$modelFieldsPresent, fragment=$fragmentFieldsPresent, " +
                    "entry=$entryFieldPresent)"
            )
        }
        if (commitMethod == null) {
            module.log(Log.WARN, TAG, "HookPoint[system_font_commit] state=MISSING")
        }

        themeStoreMethod?.let { method ->
            hook(module, "system_font_entry_gate", method) { chain ->
                if (SystemFontConfig.isHideThemeStore(module)) false else chain.proceed()
            }
        }

        if (canInjectCards) {
            buildMethod.let { method ->
                hook(module, "system_font_list", method) { chain ->
                    val fragment = chain.thisObject
                    if (fragment != null) {
                        // A rebuilt list always reflects the committed system
                        // font, not an uncommitted card selection.
                        clearPending()
                        if (isFeatureEnabled()) {
                            runCatching { ensureFontEntries(fragment) }
                                .onFailure { if (listErrorLogged.compareAndSet(false, true)) {
                                    module.log(Log.ERROR, TAG, "font list injection failed", it)
                                } }
                            if (!builtInStaged) {
                                val applied = runCatching { adjustSelection(fragment) }.getOrNull()
                                if (applied != null) {
                                    runCatching {
                                        fontEntries[applied]?.let { applyPreviewTypeface(fragment, it) }
                                    }
                                }
                            }
                        } else {
                            // Feature off only removes realOS entries; the
                            // active system font must not change implicitly.
                            runCatching { removeCustomEntries(fragment) }
                            fontEntries.clear()
                            typefaceCache.clear()
                        }
                        lastRevision = SystemFontConfig.revision(module)
                    }
                    chain.proceed()
                }
            }
        }

        onResumeMethod?.let { method ->
            hook(module, "system_font_resume", method) { chain ->
                val result = chain.proceed()
                val fragment = chain.thisObject
                if (fragment != null) {
                    runCatching {
                        val revision = SystemFontConfig.revision(module)
                        if (revision != lastRevision) {
                            lastRevision = revision
                            // The switch and library revision changed; a fresh
                            // page rebuilds the stock list so the theme-store
                            // card can also appear/disappear.
                            fragmentActivity(fragment)?.recreate()
                        }
                    }
                }
                result
            }
        }

        nameMethod?.let { method ->
            hook(module, "system_font_name", method) { chain ->
                val result = chain.proceed()
                val entry = chain.args.getOrNull(1)
                val textView = chain.args.getOrNull(0) as? TextView
                if (textView != null && entry != null && isFeatureEnabled()) {
                    val type = fontType(entry)
                    if (type == MANAGER_FONT_TYPE) {
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
                if (textView != null && isFeatureEnabled()) {
                    when {
                        type == MANAGER_FONT_TYPE -> {
                            runCatching { applyManagerPreview(textView) }
                        }
                        type >= CUSTOM_TYPE_BASE -> {
                            fontEntries[type]?.let { font ->
                                runCatching { applyCardPreview(textView, font) }
                            }
                        }
                    }
                }
                result
            }
        }

        clickMethod?.let { method ->
            hook(module, "system_font_click", method) { chain ->
                if (!isFeatureEnabled()) {
                    clearPending()
                    return@hook chain.proceed()
                }
                val entry = chain.args.getOrNull(1) ?: return@hook chain.proceed()
                val type = fontType(entry)
                when {
                    type == MANAGER_FONT_TYPE -> {
                        val context = (chain.args.getOrNull(0) as? android.view.View)?.context
                        if (context != null) startFontManager(context)
                        return@hook null
                    }

                    type >= CUSTOM_TYPE_BASE -> {
                        val font = fontEntries[type]
                        val fragment = chain.thisObject
                        if (font != null && fragment != null) {
                            // Stock behavior: selecting a card only stages it.
                            // The bottom Apply button does the actual commit.
                            builtInStaged = false
                            pendingFont = font
                            pendingFragment = WeakReference(fragment)
                            runCatching { updateSelectionMethod?.invoke(fragment, entry) }
                            runCatching { applyPreviewTypeface(fragment, font) }
                        } else if (cardErrorLogged.compareAndSet(false, true)) {
                            module.log(Log.WARN, TAG, "custom font card has no resolved font")
                        }
                        return@hook null
                    }
                }

                // A stock card was selected, so drop any staged custom font and
                // let the synchronous list rebuild keep the stock selection.
                clearPending()
                builtInStaged = true
                try {
                    return@hook chain.proceed()
                } finally {
                    builtInStaged = false
                }
            }
        }

        commitMethod?.let { method ->
            hook(module, "system_font_commit", method) { chain ->
                val pending = pendingFont ?: return@hook chain.proceed()
                if (!isFeatureEnabled()) {
                    clearPending()
                    return@hook chain.proceed()
                }
                val fragment = pendingFragment?.get()
                val model = chain.thisObject
                val fragmentModel = fragment?.let { fragmentField(it, "mFontDisplayModel") }
                val flags = chain.args.getOrNull(1) as? Int ?: 0
                val context = chain.args.getOrNull(0) as? Context
                if (fragment == null || fragmentModel == null || fragmentModel !== model ||
                    (flags and 1) == 0 || context == null
                ) {
                    module.log(
                        Log.WARN,
                        TAG,
                        "staged font dropped: fragment/model/flags mismatch " +
                            "(model=${fragmentModel != null}, same=${fragmentModel === model}, " +
                            "flags=$flags, context=${context != null})"
                    )
                    clearPending()
                    return@hook chain.proceed()
                }

                // Stock commit rewrites current_typeface/_name for its own
                // built-in typeface index. Stage the model as "no typeface
                // change" so it only commits font scale, then write the custom
                // font as the last step and let notifySystem refresh the UI.
                val savedTypeface = userSetTypeface(model)
                setUserSetTypeface(model, -1)
                var result: Any? = null
                var applied = false
                try {
                    result = chain.proceed()
                    applied = applyFont(context, pending)
                } finally {
                    if (savedTypeface != null) setUserSetTypeface(model, savedTypeface)
                    clearPending()
                }
                module.log(
                    if (applied) Log.INFO else Log.WARN,
                    TAG,
                    (if (applied) "system font applied: " else "system font apply failed: ") +
                        pending.name
                )
                runCatching {
                    Toast.makeText(
                        context,
                        ModuleResources.string(
                            context,
                            if (applied) R.string.system_font_applied
                            else R.string.system_font_apply_failed
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                result
            }
        }

        module.log(
            Log.INFO,
            TAG,
            "system font page installed: cards=$canInjectCards, " +
                "click=${clickMethod != null}, name=${nameMethod != null}, " +
                "preview=${previewMethod != null}, commit=${commitMethod != null}, " +
                "resume=${onResumeMethod != null}, themeStore=${themeStoreMethod != null}"
        )
    }

    fun reset() {
        installed.set(false)
        settingsClassLoader = null
        fontEntries.clear()
        typefaceCache.clear()
        builtInStaged = false
        lastRevision = 0
        listErrorLogged.set(false)
        queryErrorLogged.set(false)
        cardErrorLogged.set(false)
        clearPending()
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
        clazz.getDeclaredMethod(name, *params).apply { isAccessible = true }
    }.getOrNull()

    private fun isFeatureEnabled(): Boolean {
        val m = module ?: return SystemFontConfig.DEFAULT_ENABLED
        return SystemFontConfig.isEnabled(m)
    }

    // ── List injection ──

    private fun ensureFontEntries(fragment: Any) {
        val context = fragmentContext(fragment) ?: return
        val list = mutableFontTypeList(fragment) ?: return
        list.removeAll { isRealOsType(fontType(it)) }

        val fonts = queryFonts(context)
        fontEntries.clear()
        typefaceCache.keys.retainAll(fonts.map { it.id }.toSet())

        // Order: stock cards -> realOS fonts -> realOS manager -> stock
        // theme-store "More fonts" card (when present).
        val moreIndex = list.indexOfFirst { fontType(it) == MORE_FONT_TYPE }
            .let { if (it < 0) list.size else it }
        val entries = ArrayList<Any>(fonts.size + 1)
        fonts.forEachIndexed { index, font ->
            val type = CUSTOM_TYPE_BASE + index
            fontEntries[type] = font
            val entry = newFontTypeEntry(type) ?: return@forEachIndexed
            entries.add(entry)
        }
        newFontTypeEntry(MANAGER_FONT_TYPE)?.let { entries.add(it) }
        if (entries.isNotEmpty()) list.addAll(moreIndex, entries)
    }

    private fun removeCustomEntries(fragment: Any) {
        val list = mutableFontTypeList(fragment) ?: return
        list.removeAll { isRealOsType(fontType(it)) }
    }

    private fun isRealOsType(type: Int): Boolean =
        type == MANAGER_FONT_TYPE || type >= CUSTOM_TYPE_BASE

    private fun adjustSelection(fragment: Any): Int? {
        val context = fragmentContext(fragment) ?: return null
        val list = mutableFontTypeList(fragment) ?: return null
        val currentTypeface = readSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE)
        val currentId = readSetting(context, SystemFontContract.SETTING_FONT_ID)
        val currentName = readSetting(context, SystemFontContract.SETTING_CURRENT_TYPEFACE_NAME)
        val appliedType = if (currentTypeface == SystemFontContract.CUSTOM_TYPEFACE_VALUE) {
            findAppliedType(currentId, currentName)
        } else {
            null
        }

        for (entry in list) {
            val type = fontType(entry)
            when {
                type == MANAGER_FONT_TYPE -> setSelected(entry, false)
                type >= CUSTOM_TYPE_BASE -> setSelected(entry, type == appliedType)
            }
        }
        val more = list.firstOrNull { fontType(it) == MORE_FONT_TYPE }
        if (more != null && currentTypeface == SystemFontContract.CUSTOM_TYPEFACE_VALUE) {
            setSelected(more, false)
        }
        return appliedType
    }

    // Legacy applies have no id; fall back to the display name until reapplied.
    private fun findAppliedType(currentId: String?, currentName: String?): Int? {
        if (!currentId.isNullOrBlank()) {
            return fontEntries.entries.firstOrNull { it.value.id == currentId }?.key
        }
        if (currentName.isNullOrBlank()) return null
        val matches = fontEntries.entries.filter { it.value.name == currentName }
        return if (matches.size == 1) matches.first().key else null
    }

    private fun clearPending() {
        pendingFont = null
        pendingFragment = null
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
        if (queryErrorLogged.compareAndSet(false, true)) {
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

    private fun fragmentActivity(fragment: Any): android.app.Activity? = runCatching {
        fragment.javaClass.getMethod("getActivity").invoke(fragment) as? android.app.Activity
    }.getOrNull()

    private fun fragmentField(fragment: Any, name: String): Any? = runCatching {
        fragment.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(fragment)
    }.getOrNull()

    private fun userSetTypeface(model: Any): Int? = runCatching {
        val settings = model.javaClass.getDeclaredField("mFontDisplaySettingsUserSet")
            .apply { isAccessible = true }.get(model) ?: return null
        settings.javaClass.getDeclaredField("mSelectFontTypeface")
            .apply { isAccessible = true }.getInt(settings)
    }.getOrNull()

    private fun setUserSetTypeface(model: Any, value: Int) {
        runCatching {
            val settings = model.javaClass.getDeclaredField("mFontDisplaySettingsUserSet")
                .apply { isAccessible = true }.get(model) ?: return
            settings.javaClass.getDeclaredField("mSelectFontTypeface")
                .apply { isAccessible = true }.setInt(settings, value)
        }
    }

    // ── Preview ──

    private fun applyCardPreview(textView: TextView, font: HookFont) {
        textView.setBackgroundColor(0)
        textView.text = "Aa"
        cachedTypeface(textView.context, font.id)?.let { textView.typeface = it }
    }

    private fun applyManagerPreview(textView: TextView) {
        textView.setBackgroundColor(0)
        textView.text = "Aa"
        textView.typeface = Typeface.DEFAULT
    }

    /** Previews a staged custom font in the large sample above the cards. */
    private fun applyPreviewTypeface(fragment: Any, font: HookFont) {
        val primary = fragmentField(fragment, "mFontPreviewSummary") as? TextView
        val shadow = fragmentField(fragment, "mFontPreviewSummaryShadow") as? TextView
        val context = primary?.context ?: shadow?.context ?: return
        val typeface = cachedTypeface(context, font.id) ?: return
        primary?.typeface = typeface
        shadow?.typeface = typeface
    }

    private fun cachedTypeface(context: Context, id: String): Typeface? =
        typefaceCache[id] ?: loadTypeface(context, id)?.also { typefaceCache[id] = it }

    private fun loadTypeface(context: Context, id: String): Typeface? = runCatching {
        val uri = Uri.parse(
            "content://${SystemFontContract.AUTHORITY}/${SystemFontContract.PATH_FONTS}/$id"
        )
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            Typeface.Builder(pfd.fileDescriptor).build()
        }
    }.getOrNull()

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

            val oldTypeface = readSetting(
                context,
                SystemFontContract.SETTING_CURRENT_TYPEFACE
            )
            val oldName = readSetting(
                context,
                SystemFontContract.SETTING_CURRENT_TYPEFACE_NAME
            )
            val oldId = readSetting(
                context,
                SystemFontContract.SETTING_FONT_ID
            )

            val uri = Uri.parse(
                "content://${SystemFontContract.AUTHORITY}/" +
                    "${SystemFontContract.PATH_FONTS}/${font.id}"
            )
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                val dir = createDir.invoke(writer) as? java.io.File
                    ?: return@runCatching false
                copyFont.invoke(writer, dir, input, "Customized-Regular.ttf")
                val target = java.io.File(dir, "Customized-Regular.ttf")
                target.isFile && target.length() > 0L
            } ?: false

            if (!copied) return@runCatching false
            val idSet = setSetting(
                context,
                SystemFontContract.SETTING_FONT_ID,
                font.id
            )
            val nameSet = setSetting(
                context,
                SystemFontContract.SETTING_CURRENT_TYPEFACE_NAME,
                font.name
            )
            val typefaceSet = setSetting(
                context,
                SystemFontContract.SETTING_CURRENT_TYPEFACE,
                SystemFontContract.CUSTOM_TYPEFACE_VALUE
            )
            if (!idSet || !nameSet || !typefaceSet) {
                setSetting(
                    context,
                    SystemFontContract.SETTING_FONT_ID,
                    oldId
                )
                setSetting(
                    context,
                    SystemFontContract.SETTING_CURRENT_TYPEFACE_NAME,
                    oldName
                )
                setSetting(
                    context,
                    SystemFontContract.SETTING_CURRENT_TYPEFACE,
                    oldTypeface
                )
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

    private fun setSetting(context: Context, key: String, value: String?): Boolean {
        val target = value ?: ""
        runCatching {
            Settings.System.putString(context.contentResolver, key, target)
        }
        return readSetting(context, key) == target
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
}
