package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import io.github.libxposed.api.XposedModule
import java.util.concurrent.atomic.AtomicBoolean
import io.github.ra1nify.realos.opluskeyboard.OplusInputConfig

/**
 * System Server hook for inline autofill suggestions of OPPO Keyboard.
 *
 * In Android's Autofill Framework, InputMethodManagerService (IMMS) queries
 * InputMethodInfo.isInlineSuggestionsEnabled() to decide whether to dispatch
 * inline autofill requests to the current IME.
 *
 * For Xiaobu Input Method (com.oplus.keyboard and variants), the hook returns true from
 * isInlineSuggestionsEnabled() and writes mInlineSuggestionsEnabled = true.
 */
object OplusInputSystemServerHook {

    private const val TAG = "OplusSystemServerHook"
    private val installed = AtomicBoolean(false)

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (!installed.compareAndSet(false, true)) {
            module.log(Log.DEBUG, TAG, "OplusInputSystemServerHook already installed; skipping.")
            return
        }

        module.log(Log.INFO, TAG, "Installing OplusInputSystemServerHook in system_server (stub hook installed)...")

        try {
            val imiClass = classLoader.loadClass("android.view.inputmethod.InputMethodInfo")

            // 1. Hook public boolean isInlineSuggestionsEnabled()
            try {
                val isInlineMethod = imiClass.getDeclaredMethod("isInlineSuggestionsEnabled")
                module.hook(isInlineMethod).intercept { chain ->
                    val imi = chain.thisObject
                    val pkgName = runCatching {
                        val getPkg = imi.javaClass.getMethod("getPackageName")
                        getPkg.invoke(imi) as? String
                    }.getOrNull()

                    if (pkgName != null && pkgName in SpecPackages.OPLUS_KEYBOARD_VARIANTS) {
                        // Passes through when the native config already reports true.
                        val nativeResult = chain.proceed() as? Boolean ?: false
                        if (nativeResult) {
                            module.log(Log.DEBUG, TAG, "isInlineSuggestionsEnabled natively true for $pkgName; skipping override.")
                            return@intercept true
                        }

                        val isEnabled = OplusInputConfig.isSystemOplusInlineAutofillEnabled(module)
                        module.log(Log.INFO, TAG, "isInlineSuggestionsEnabled queried for $pkgName, isEnabled=$isEnabled")
                        if (isEnabled) {
                            // Also writes the field for direct field reads.
                            runCatching {
                                val field = imiClass.getDeclaredField("mInlineSuggestionsEnabled")
                                field.isAccessible = true
                                field.setBoolean(imi, true)
                            }
                            return@intercept true
                        }
                    }
                    chain.proceed()
                }
                module.log(Log.INFO, TAG, "Successfully hooked InputMethodInfo.isInlineSuggestionsEnabled()")
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "Failed to hook InputMethodInfo.isInlineSuggestionsEnabled()", t)
            }

            // 2. Hook InputMethodInfo constructors to set mInlineSuggestionsEnabled = true
            try {
                val field = runCatching {
                    val f = imiClass.getDeclaredField("mInlineSuggestionsEnabled")
                    f.isAccessible = true
                    f
                }.getOrNull()

                if (field != null) {
                    for (ctor in imiClass.declaredConstructors) {
                        module.hook(ctor).intercept { chain ->
                            val result = chain.proceed()
                            val imi = chain.thisObject
                            if (imi != null) {
                                val pkgName = runCatching {
                                    val getPkg = imi.javaClass.getMethod("getPackageName")
                                    getPkg.invoke(imi) as? String
                                }.getOrNull()

                                if (pkgName != null && pkgName in SpecPackages.OPLUS_KEYBOARD_VARIANTS) {
                                    val currentVal = runCatching { field.getBoolean(imi) }.getOrDefault(false)
                                    if (currentVal) {
                                        module.log(Log.DEBUG, TAG, "mInlineSuggestionsEnabled already true in constructor for $pkgName")
                                    } else if (OplusInputConfig.isSystemOplusInlineAutofillEnabled(module)) {
                                        runCatching { field.setBoolean(imi, true) }
                                        module.log(Log.DEBUG, TAG, "Forced mInlineSuggestionsEnabled=true in constructor for $pkgName")
                                    }
                                }
                            }
                            result
                        }
                    }
                    module.log(Log.INFO, TAG, "Successfully hooked InputMethodInfo constructors")
                }
            } catch (t: Throwable) {
                module.log(Log.ERROR, TAG, "Failed to hook InputMethodInfo constructors", t)
            }
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "Failed to load InputMethodInfo class in system_server", t)
        }
    }
}
