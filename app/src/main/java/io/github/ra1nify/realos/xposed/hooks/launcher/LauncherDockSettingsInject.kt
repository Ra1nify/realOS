package io.github.ra1nify.realos.xposed.hooks.launcher

import android.content.Context
import android.content.Intent
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.launcher.LauncherDockContract
import java.lang.reflect.Proxy

/**
 * Injects a jump preference into the launcher settings "effects" category.
 *
 * Hooks the already-resolved [LauncherHandles.preferenceGroupAddMethod] and detects the stable
 * anchor key (`launcher_widget_optimize_setting_entry`) as its preference is added during XML
 * inflation. No obfuscated host class name, fragment lookup or resource id is hardcoded.
 * Tapping the entry starts [LauncherDockContract.DOCK_PAGE_ACTIVITY].
 */
object LauncherDockSettingsInject {

    private const val TAG = "RealOS-LauncherDockEntry"

    private const val ANCHOR_KEY = "launcher_widget_optimize_setting_entry"
    private const val ENTRY_KEY = "realos_launcher_dock_entry"

    @Volatile
    private var installed = false

    @Volatile
    private var injecting = false

    private var groupFind: java.lang.reflect.Method? = null

    fun install(module: XposedModule, symbols: LauncherHandles) {
        if (installed) {
            module.log(Log.INFO, TAG, "already installed, skip")
            return
        }
        val adder = symbols.preferenceGroupAddMethod ?: run {
            module.log(
                Log.WARN, TAG,
                "fail closed: PreferenceGroup#addPreference not resolved; no dock entry"
            )
            return
        }
        val prefClass = symbols.preferenceClass ?: run {
            module.log(Log.WARN, TAG, "fail closed: Preference class not resolved; no dock entry")
            return
        }
        groupFind = symbols.preferenceGroupFindMethod
        installed = true

        module.hook(adder).intercept { chain ->
            val result = chain.proceed()
            if (injecting) return@intercept result
            val pref = chain.args.getOrNull(0) ?: return@intercept result
            val key = preferenceKey(pref)
            if (key != ANCHOR_KEY) return@intercept result
            val parent = chain.thisObject ?: return@intercept result
            if (findInGroup(parent, ENTRY_KEY) != null) return@intercept result

            injecting = true
            runCatching { inject(module, parent, pref, adder, prefClass) }
                .onFailure { module.log(Log.WARN, TAG, "Dock entry injection failed", it) }
            injecting = false
            result
        }
        module.log(Log.INFO, TAG, "Installed dock entry hook on PreferenceGroup#addPreference")
    }

    fun reset() {
        installed = false
        injecting = false
        groupFind = null
    }

    private fun inject(
        module: XposedModule,
        parent: Any,
        anchor: Any,
        adder: java.lang.reflect.Method,
        prefClass: Class<*>
    ) {
        val classLoader = anchor.javaClass.classLoader
        val context = runCatching {
            anchor.javaClass.getMethod("getContext").invoke(anchor) as? Context
        }.getOrNull() ?: run {
            module.log(Log.WARN, TAG, "Anchor has no context; skip")
            return
        }

        val jumpClass = runCatching {
            classLoader?.loadClass("com.coui.appcompat.preference.COUIJumpPreference")
        }.getOrNull() ?: prefClass
        val entry = runCatching {
            jumpClass.getConstructor(Context::class.java).newInstance(context)
        }.getOrNull() ?: run {
            module.log(Log.WARN, TAG, "Jump preference construction failed; skip")
            return
        }

        val order = runCatching {
            anchor.javaClass.getMethod("getOrder").invoke(anchor) as? Int
        }.getOrNull() ?: 0
        invoke1(entry, "setKey", String::class.java, ENTRY_KEY)
        invoke1(entry, "setTitle", CharSequence::class.java, LauncherUiText.dockEntryTitle(context))
        invoke1(entry, "setSummary", CharSequence::class.java, LauncherUiText.dockEntrySummary(context))
        invoke1(entry, "setOrder", Integer.TYPE, order + 1)

        val intent = Intent().apply {
            setClassName(
                LauncherDockContract.REALOS_PACKAGE,
                LauncherDockContract.DOCK_PAGE_ACTIVITY
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        invoke1(entry, "setIntent", Intent::class.java, intent)
        setClickListener(entry, classLoader) {
            runCatching { context.startActivity(intent) }
                .onFailure { module.log(Log.WARN, TAG, "Failed to open dock page", it) }
        }

        adder.invoke(parent, entry)
        module.log(Log.INFO, TAG, "Injected dock entry below $ANCHOR_KEY")
    }

    private fun preferenceKey(pref: Any): String? = runCatching {
        pref.javaClass.getMethod("getKey").invoke(pref) as? String
    }.getOrNull()

    private fun findInGroup(group: Any, key: String): Any? {
        groupFind?.let { m ->
            runCatching { m.invoke(group, key) }.getOrNull()?.let { return it }
        }
        return runCatching {
            group.javaClass.getMethod("findPreference", CharSequence::class.java)
                .invoke(group, key)
        }.getOrNull()
    }

    private fun invoke1(target: Any, name: String, param: Class<*>, value: Any): Any? = runCatching {
        target.javaClass.getMethod(name, param).invoke(target, value)
    }.getOrNull()

    private fun setClickListener(entry: Any, classLoader: ClassLoader?, onClick: () -> Unit) {
        val iface = runCatching {
            entry.javaClass.methods
                .firstOrNull {
                    it.name == "setOnPreferenceClickListener" && it.parameterTypes.size == 1
                }?.parameterTypes?.get(0)
        }.getOrNull() ?: return
        val proxy = runCatching {
            Proxy.newProxyInstance(iface.classLoader ?: classLoader, arrayOf(iface)) { p, m, _ ->
                when (m.name) {
                    "equals" -> p === m
                    "hashCode" -> System.identityHashCode(p)
                    "toString" -> "RealOSDockEntry"
                    else -> {
                        onClick()
                        true
                    }
                }
            }
        }.getOrNull() ?: return
        invoke1(entry, "setOnPreferenceClickListener", iface, proxy)
    }
}
