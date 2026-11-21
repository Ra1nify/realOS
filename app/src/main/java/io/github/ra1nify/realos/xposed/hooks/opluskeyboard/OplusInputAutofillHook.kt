package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.app.Application
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.util.Size
import android.view.inputmethod.EditorInfo
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedInterface
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import java.util.concurrent.atomic.AtomicBoolean
import io.github.ra1nify.realos.opluskeyboard.KeyboardThemeColors
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusInputConfig

/**
 * Hook for OPPO Keyboard to participate in Android's Inline Autofill Framework.
 *
 * Hooks across class hierarchy:
 * 1. onCreateInlineSuggestionsRequest(Bundle) -> returns InlineSuggestionsRequest
 * 2. onInlineSuggestionsResponse(InlineSuggestionsResponse) -> displays suggestions
 * 3. onFinishInputView(boolean) -> dismisses suggestions strip
 * 4. onStartInput(EditorInfo, boolean) -> resets suggestions strip on new field
 */
object OplusInputAutofillHook {

    private const val TAG = "OplusAutofillHook"
    private const val IME_SERVICE_CLASS = "com.oplus.keyboard.service.ImeService"
    private val installed = AtomicBoolean(false)

    /**
     * Symbols resolved after Application startup.
     *
     * The early install from `onPackageReady` runs before the resolver can access the host
     * Application, so its response hook initially captures a null symbol set. A later install
     * with symbols updates this holder; the callback reads it at call time.
     */
    @Volatile
    private var runtimeSymbols: OplusInputSymbols? = null

    /**
     * Resets the install gate.
     *
     * [installed] is process-wide static state that outlives the framework's hook teardown;
     * clearing it lets a hot reload call [install] again.
     */
    fun reset() {
        installed.set(false)
        runtimeSymbols = null
    }

    fun install(module: XposedModule, app: Application, symbols: OplusInputSymbols? = null) {
        install(module, app.packageName, app.classLoader, symbols)
    }

    fun install(
        module: XposedModule,
        packageName: String,
        classLoader: ClassLoader,
        symbols: OplusInputSymbols? = null
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            module.log(Log.INFO, TAG, "Device API level (${Build.VERSION.SDK_INT}) < 30; Inline suggestions unsupported.")
            return
        }

        if (symbols != null) {
            runtimeSymbols = symbols
            try {
                OplusAutofillToolbarManager.install(module, symbols, classLoader)
            } catch (t: Throwable) {
                module.log(Log.WARN, TAG, "Failed to install toolbar autofill manager: ${t.message}")
            }
        }

        if (!installed.compareAndSet(false, true)) {
            module.log(Log.DEBUG, TAG, "OplusInputAutofillHook already installed; skipping.")
            return
        }

        if (!OplusInputConfig.isEnhancementsEnabled(module, packageName)) {
            module.log(Log.INFO, TAG, "Enhancements feature disabled for $packageName; skipping autofill hook installation.")
            return
        }

        module.log(Log.INFO, TAG, "Installing OplusInputAutofillHook across class hierarchy for $packageName...")

        try {
            // 1. Traverse class hierarchy starting from ImeService up to InputMethodService
            hookMethodInClassTree(
                module = module,
                classLoader = classLoader,
                startClassName = IME_SERVICE_CLASS,
                methodName = "onCreateInlineSuggestionsRequest",
                parameterTypes = arrayOf(Bundle::class.java)
            ) { chain ->
                val thisObj = chain.thisObject
                val className = thisObj?.javaClass?.name ?: ""
                val pkgName = (thisObj as? android.content.Context)?.packageName ?: ""
                val isTargetIme = pkgName in SpecPackages.OPLUS_KEYBOARD_VARIANTS ||
                    SpecPackages.OPLUS_KEYBOARD_VARIANTS.any { className.startsWith(it) }

                if (isTargetIme) {
                    val context = thisObj as android.content.Context
                    if (!OplusInputPrefs.isAutofillEnabled(context)) {
                        module.log(
                            Log.INFO, TAG,
                            "onCreateInlineSuggestionsRequest skipped: inline autofill disabled"
                        )
                        return@hookMethodInClassTree chain.proceed()
                    }
                    val imeService = thisObj as? InputMethodService
                    if (imeService == null || !OplusAutofillStripManager.canDisplayInline(
                            imeService,
                            runtimeSymbols ?: symbols
                        )
                    ) {
                        module.log(
                            Log.WARN, TAG,
                            "HookPoint[inline_autofill_display] state=MISSING; " +
                                "declining inline request so the provider can use its popup"
                        )
                        return@hookMethodInClassTree chain.proceed()
                    }
                    val uiExtras = chain.getArg(0) as? Bundle
                    module.log(Log.INFO, TAG, "onCreateInlineSuggestionsRequest called on $className (pkg=$pkgName), uiExtras=$uiExtras")
                    try {
                        val currentClassLoader = thisObj.javaClass.classLoader ?: context.classLoader
                        val density = context.resources.displayMetrics.density
                        val minWidth = (80 * density).toInt()
                        val maxWidth = (276 * density).toInt()
                        val minHeight = (36 * density).toInt()
                        val maxHeight = (40 * density).toInt()

                        val specBuilder = android.widget.inline.InlinePresentationSpec.Builder(
                            Size(minWidth, minHeight),
                            Size(maxWidth, maxHeight)
                        )
                        val stylesBundle = buildInlineUiStylesBundle(context, currentClassLoader)
                        if (!stylesBundle.isEmpty) {
                            specBuilder.setStyle(stylesBundle)
                        }
                        val spec = specBuilder.build()

                        val requestBuilder = android.view.inputmethod.InlineSuggestionsRequest.Builder(listOf(spec, spec, spec, spec, spec))
                            .setMaxSuggestionCount(5)

                        if (uiExtras != null) {
                            requestBuilder.setExtras(uiExtras)
                            val hostInputToken = runCatching {
                                uiExtras.getBinder("android:autofill:host_input_token")
                            }.getOrNull()
                            if (hostInputToken != null) {
                                runCatching {
                                    val setHostTokenMethod = requestBuilder.javaClass.getMethod(
                                        "setHostInputToken",
                                        android.os.IBinder::class.java
                                    )
                                    setHostTokenMethod.invoke(requestBuilder, hostInputToken)
                                    module.log(Log.INFO, TAG, "Attached hostInputToken to InlineSuggestionsRequest successfully")
                                }.onFailure {
                                    module.log(Log.WARN, TAG, "Failed setting hostInputToken on requestBuilder: ${it.message}")
                                }
                            }
                        }

                        val request = requestBuilder.build()
                        module.log(Log.INFO, TAG, "Successfully created and returned InlineSuggestionsRequest (max=5)")
                        return@hookMethodInClassTree request
                    } catch (t: Throwable) {
                        module.log(Log.ERROR, TAG, "Error building InlineSuggestionsRequest", t)
                    }
                }
                chain.proceed()
            }

            // 2. Hook onInlineSuggestionsResponse across class hierarchy
            hookMethodInClassTree(
                module = module,
                classLoader = classLoader,
                startClassName = IME_SERVICE_CLASS,
                methodName = "onInlineSuggestionsResponse",
                parameterTypes = arrayOf(android.view.inputmethod.InlineSuggestionsResponse::class.java)
            ) { chain ->
                val thisObj = chain.thisObject
                val className = thisObj?.javaClass?.name ?: ""
                val pkgName = (thisObj as? android.content.Context)?.packageName ?: ""
                val isTargetIme = pkgName in SpecPackages.OPLUS_KEYBOARD_VARIANTS ||
                    SpecPackages.OPLUS_KEYBOARD_VARIANTS.any { className.startsWith(it) }

                if (isTargetIme) {
                    val response = chain.getArg(0) as? android.view.inputmethod.InlineSuggestionsResponse
                    val suggestions = response?.inlineSuggestions ?: emptyList()
                    module.log(Log.INFO, TAG, "onInlineSuggestionsResponse received ${suggestions.size} suggestion(s) on $className")

                    val imeService = thisObj as? InputMethodService
                    val displayed = if (imeService != null) {
                        try {
                            OplusAutofillStripManager.onSuggestionsReceived(
                                module,
                                imeService,
                                suggestions,
                                runtimeSymbols ?: symbols
                            )
                        } catch (t: Throwable) {
                            module.log(Log.ERROR, TAG, "Failed to present inline suggestions", t)
                            false
                        }
                    } else {
                        false
                    }

                    if (suggestions.isNotEmpty()) {
                        if (displayed) {
                            return@hookMethodInClassTree true
                        }
                        module.log(
                            Log.WARN, TAG,
                            "Inline suggestions not displayed; reporting display failure"
                        )
                        return@hookMethodInClassTree false
                    }
                }
                chain.proceed()
            }

            // 3. Hook onFinishInputView(boolean)
            hookMethodInClassTree(
                module = module,
                classLoader = classLoader,
                startClassName = IME_SERVICE_CLASS,
                methodName = "onFinishInputView",
                parameterTypes = arrayOf(Boolean::class.javaPrimitiveType!!)
            ) { chain ->
                val result = chain.proceed()
                val thisObj = chain.thisObject
                val imeService = thisObj as? InputMethodService
                if (imeService != null) {
                    OplusAutofillStripManager.dismiss(module, imeService)
                }
                result
            }

            // 4. Hook onStartInput(EditorInfo, boolean)
            hookMethodInClassTree(
                module = module,
                classLoader = classLoader,
                startClassName = IME_SERVICE_CLASS,
                methodName = "onStartInput",
                parameterTypes = arrayOf(EditorInfo::class.java, Boolean::class.javaPrimitiveType!!)
            ) { chain ->
                val result = chain.proceed()
                val restarting = chain.getArg(1) as? Boolean ?: false
                val thisObj = chain.thisObject
                val imeService = thisObj as? InputMethodService
                if (imeService != null && !restarting) {
                    OplusAutofillStripManager.dismiss(module, imeService)
                }
                result
            }
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "Failed to install OplusInputAutofillHook", t)
        }
    }

    private fun hookMethodInClassTree(
        module: XposedModule,
        classLoader: ClassLoader,
        startClassName: String,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        block: (XposedInterface.Chain) -> Any?
    ) {
        // Hooks only the first class in the hierarchy that declares the method,
        // the layer closest to the runtime object.
        //
        // ImeService, base.service.b and InputMethodService all override these methods;
        // hooking all three fires the callback three times per call, for example three
        // dismisses in onStartInput and three request builds in onCreateInlineSuggestionsRequest.
        var clazz: Class<*>? = runCatching { classLoader.loadClass(startClassName) }.getOrNull()
            ?: runCatching { classLoader.loadClass("android.inputmethodservice.InputMethodService") }.getOrNull()

        while (clazz != null && clazz != Any::class.java) {
            val method = runCatching { clazz.getDeclaredMethod(methodName, *parameterTypes) }.getOrNull()
            if (method != null) {
                try {
                    module.hook(method).intercept { chain -> block(chain) }
                    module.log(Log.INFO, TAG, "Successfully hooked $methodName on ${clazz.name}")
                } catch (t: Throwable) {
                    module.log(Log.WARN, TAG, "Failed to hook $methodName on ${clazz.name}: ${t.message}")
                }
                return
            }
            clazz = clazz.superclass
        }

        module.log(Log.WARN, TAG, "HookPoint[$methodName] state=MISSING (no class in hierarchy declares it)")
    }

    private fun buildInlineUiStylesBundle(context: android.content.Context, classLoader: ClassLoader): Bundle {
        return runCatching {
            val density = context.resources.displayMetrics.density
            val (titleColor, subtitleColor, _) = resolveKeyboardThemeColors(context, classLoader)

            // The host slot paints the ColorOS superellipse surface. Keep the provider chip
            // background transparent so its default round-rect does not cover that shape.
            val capsuleBgColor = android.graphics.Color.TRANSPARENT

            val v1Style = Bundle().apply {
                putBoolean("style_v1", true)
                putInt("layout_truncation_preference", 0)

                val chipStyle = Bundle().apply {
                    putBoolean("view_style", true)
                    putInt("background_color", capsuleBgColor)
                    putIntArray("padding", intArrayOf(8, 4, 8, 4))
                    putIntArray("layout_margin", intArrayOf(0, 0, 0, 0))
                }
                putBundle("chip_style", chipStyle)

                val titleStyle = Bundle().apply {
                    putBoolean("view_style", true)
                    putBoolean("text_view_style", true)
                    putFloat("text_size", 11.5f)
                    putInt("text_size_unit", android.util.TypedValue.COMPLEX_UNIT_SP)
                    putInt("text_color", titleColor)
                    putInt("text_gravity", android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL)
                    putIntArray("layout_margin", intArrayOf(0, 0, 0, 0))
                }
                putBundle("title_style", titleStyle)

                val subtitleStyle = Bundle().apply {
                    putBoolean("view_style", true)
                    putBoolean("text_view_style", true)
                    putFloat("text_size", 10f)
                    putInt("text_size_unit", android.util.TypedValue.COMPLEX_UNIT_SP)
                    putInt("text_color", subtitleColor)
                    putInt("text_gravity", android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL)
                    putIntArray("layout_margin", intArrayOf(0, 0, 0, 0))
                }
                putBundle("subtitle_style", subtitleStyle)

                val iconSize = (18 * density).toInt()
                val startIconStyle = Bundle().apply {
                    putBoolean("view_style", true)
                    putBoolean("image_view_style", true)
                    putInt("image_max_width", iconSize)
                    putInt("image_max_height", iconSize)
                    putIntArray("layout_margin", intArrayOf(0, 0, 1, 0))
                }
                putBundle("start_icon_style", startIconStyle)

                val endIconStyle = Bundle().apply {
                    putBoolean("view_style", true)
                    putBoolean("image_view_style", true)
                    putInt("image_max_width", (16 * density).toInt())
                    putInt("image_max_height", (16 * density).toInt())
                    putIntArray("layout_margin", intArrayOf(1, 0, 0, 0))
                }
                putBundle("end_icon_style", endIconStyle)
            }

            Bundle().apply {
                putStringArrayList(
                    "androidx.autofill.inline.ui.version:key",
                    arrayListOf("androidx.autofill.inline.ui.version:v1")
                )
                putBundle("androidx.autofill.inline.ui.version:v1", v1Style)
            }
        }.getOrDefault(Bundle())
    }

    /**
     * Keyboard theme colors: accent, subtitle color, dark flag.
     *
     * Detection lives in [KeyboardThemeColors]; this falls back to uiMode when it fails.
     */
    private fun resolveKeyboardThemeColors(
        context: android.content.Context,
        classLoader: ClassLoader
    ): Triple<Int, Int, Boolean> {
        val accent = KeyboardThemeColors.resolveAccentColor(context, classLoader)
        if (accent != null) {
            return Triple(
                accent,
                KeyboardThemeColors.subtitleColorOf(accent),
                KeyboardThemeColors.isDarkTheme(context, classLoader)
            )
        }
        return if (KeyboardThemeColors.isSystemNight(context)) {
            Triple(0xEEFFFFFF.toInt(), 0x99FFFFFF.toInt(), true)
        } else {
            Triple(0xDE000000.toInt(), 0x8A000000.toInt(), false)
        }
    }

}
