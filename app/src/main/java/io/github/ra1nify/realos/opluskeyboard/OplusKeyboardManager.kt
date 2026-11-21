package io.github.ra1nify.realos.opluskeyboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.root.RootManager
import io.github.ra1nify.realos.service.XposedServiceManager
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import io.github.ra1nify.realos.opluskeyboard.OplusKeyboardManager
import io.github.ra1nify.realos.opluskeyboard.KeyboardPagesContract

/**
 * Manager for ColorOS / Xiaobu Input Method settings, persistence and parasitic page launching.
 */
object OplusKeyboardManager {

    private const val TAG = "OplusKeyboardManager"
    private const val PREFS_NAME = "app_config"
    private const val DOCUMENT_ACTIVITY_CLASS = "com.oplus.keyboard.ai.activity.DocumentEmptyActivity"
    const val ACTION_KEYBOARD_CONFIG_CHANGED = "io.github.ra1nify.realos.ACTION_KEYBOARD_CONFIG_CHANGED"

    sealed class LaunchResult {
        object Success : LaunchResult()
        data class Failed(val message: String) : LaunchResult()
    }

    /**
     * Resolves the primary installed keyboard package matching ColorOS / Realme input method variants.
     * Fallbacks to [SpecPackages.KEYBOARD_HOST] if queries are restricted by OS.
     */
    fun getInstalledKeyboardPackage(context: Context): String {
        val pm = context.packageManager
        for (pkg in SpecPackages.OPLUS_KEYBOARD_VARIANTS) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Throwable) {
                // Continue searching
            }
        }
        return SpecPackages.KEYBOARD_HOST
    }

    // ── Preference Persistence ──

    private fun prefKey(key: String, pkg: String) = "${key}_$pkg"

    fun isLiquidGlassEnabled(context: Context, packageName: String): Boolean {
        val remotePrefs = XposedServiceManager.getRemotePreferences(PREFS_NAME)
        val key = prefKey("oplus_input_liquid_glass", packageName)
        val fallbackKey = prefKey("oplus_input_workshop", packageName)
        if (remotePrefs != null) {
            if (remotePrefs.contains(key)) return remotePrefs.getBoolean(key, true)
            if (remotePrefs.contains(fallbackKey)) return remotePrefs.getBoolean(fallbackKey, true)
        }
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (sp.contains(key)) return sp.getBoolean(key, true)
        return sp.getBoolean(fallbackKey, true)
    }

    fun setLiquidGlassEnabled(context: Context, packageName: String, enabled: Boolean) {
        val key = prefKey("oplus_input_liquid_glass", packageName)
        val fallbackKey = prefKey("oplus_input_workshop", packageName)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(key, enabled)
            .putBoolean(fallbackKey, enabled)
            .apply()
        XposedServiceManager.getRemotePreferences(PREFS_NAME)?.edit()
            ?.putBoolean(key, enabled)
            ?.putBoolean(fallbackKey, enabled)
            ?.apply()
        notifyKeyboardConfigChanged(context, packageName)
    }

    fun isKeyboardEffectsEnabled(context: Context, packageName: String): Boolean {
        val remotePrefs = XposedServiceManager.getRemotePreferences(PREFS_NAME)
        val key = prefKey("oplus_input_keyboard_effects", packageName)
        if (remotePrefs != null && remotePrefs.contains(key)) {
            return remotePrefs.getBoolean(key, true)
        }
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(key, true)
    }

    fun setKeyboardEffectsEnabled(context: Context, packageName: String, enabled: Boolean) {
        val key = prefKey("oplus_input_keyboard_effects", packageName)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(key, enabled)
            .apply()
        XposedServiceManager.getRemotePreferences(PREFS_NAME)?.edit()
            ?.putBoolean(key, enabled)
            ?.apply()
        notifyKeyboardConfigChanged(context, packageName)
    }

    fun isKeyboardSoundEnabled(context: Context, packageName: String): Boolean {
        val remotePrefs = XposedServiceManager.getRemotePreferences(PREFS_NAME)
        val key = prefKey("oplus_input_keyboard_sounds", packageName)
        if (remotePrefs != null && remotePrefs.contains(key)) {
            return remotePrefs.getBoolean(key, true)
        }
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(key, true)
    }

    fun setKeyboardSoundEnabled(context: Context, packageName: String, enabled: Boolean) {
        val key = prefKey("oplus_input_keyboard_sounds", packageName)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(key, enabled)
            .apply()
        XposedServiceManager.getRemotePreferences(PREFS_NAME)?.edit()
            ?.putBoolean(key, enabled)
            ?.apply()
        notifyKeyboardConfigChanged(context, packageName)
    }

    fun isEnhancementsEnabled(context: Context, packageName: String): Boolean {
        val remotePrefs = XposedServiceManager.getRemotePreferences(PREFS_NAME)
        val key = prefKey("oplus_input_enhancements", packageName)
        if (remotePrefs != null && remotePrefs.contains(key)) {
            return remotePrefs.getBoolean(key, true)
        }
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(key, true)
    }

    fun setEnhancementsEnabled(context: Context, packageName: String, enabled: Boolean) {
        val key = prefKey("oplus_input_enhancements", packageName)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(key, enabled)
            .apply()
        XposedServiceManager.getRemotePreferences(PREFS_NAME)?.edit()
            ?.putBoolean(key, enabled)
            ?.apply()
        notifyKeyboardConfigChanged(context, packageName)
    }

    fun notifyKeyboardConfigChanged(context: Context, packageName: String) {
        try {
            val intent = Intent(ACTION_KEYBOARD_CONFIG_CHANGED).apply {
                setPackage(packageName)
            }
            context.sendBroadcast(intent)
            Log.i(TAG, "Sent broadcast $ACTION_KEYBOARD_CONFIG_CHANGED to $packageName")
        } catch (t: Throwable) {
            Log.w(TAG, "sendBroadcast direct failed: ${t.message}; attempting root broadcast")
            if (RootManager.rootInfo.value.state == io.github.ra1nify.realos.root.RootState.GRANTED) {
                RootManager.suExecRaw("am broadcast -a $ACTION_KEYBOARD_CONFIG_CHANGED -p $packageName")
            }
        }
    }

    // ── Parasitic Page Launching (with 3-Tier Fallback Chain) ──

    /**
     * Launch a parasitic page inside the host keyboard application.
     *
     * @param context Android context
     * @param pageType One of [KeyboardPagesContract.PAGE_LIQUID_GLASS],
     *                 [KeyboardPagesContract.PAGE_KEYBOARD_EFFECTS],
     *                 or [KeyboardPagesContract.PAGE_ENHANCEMENTS]
     */
    fun launchParasiticPage(context: Context, pageType: String): LaunchResult {
        val targetPkg = getInstalledKeyboardPackage(context)

        val actionName = when (pageType) {
            KeyboardPagesContract.PAGE_LIQUID_GLASS, KeyboardPagesContract.PAGE_WORKSHOP ->
                KeyboardPagesContract.ACTION_LIQUID_GLASS
            KeyboardPagesContract.PAGE_KEYBOARD_EFFECTS ->
                KeyboardPagesContract.ACTION_KEYBOARD_EFFECTS
            KeyboardPagesContract.PAGE_KEYBOARD_SOUNDS ->
                KeyboardPagesContract.ACTION_KEYBOARD_SOUNDS
            KeyboardPagesContract.PAGE_ENHANCEMENTS ->
                KeyboardPagesContract.ACTION_ENHANCEMENTS
            else -> KeyboardPagesContract.ACTION_LIQUID_GLASS
        }

        // Tier 1: Direct Intent invocation
        try {
            val intent = Intent().apply {
                component = ComponentName(targetPkg, DOCUMENT_ACTIVITY_CLASS)
                action = actionName
                putExtra(KeyboardPagesContract.EXTRA_PAGE_TYPE, pageType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Successfully started parasitic activity directly: $targetPkg/$DOCUMENT_ACTIVITY_CLASS")
            return LaunchResult.Success
        } catch (secEx: SecurityException) {
            Log.w(TAG, "Direct launch failed with SecurityException (activity not exported): ${secEx.message}. Attempting Root fallback...")

            // Tier 2: Root transparent invocation
            val rootOk = RootManager.rootInfo.value.state == io.github.ra1nify.realos.root.RootState.GRANTED
            if (rootOk) {
                try {
                    val cmd = "am start -n \"$targetPkg/$DOCUMENT_ACTIVITY_CLASS\" -a \"$actionName\" --es extra_page_type \"$pageType\""
                    val result = RootManager.suExecRaw(cmd)
                    if (result.isSuccess) {
                        Log.i(TAG, "Root am start succeeded for $targetPkg: $cmd")
                        return LaunchResult.Success
                    } else {
                        Log.e(TAG, "Root am start failed: exit=${result.exitCode}, out=${result.output}")
                        val detail = result.output.ifBlank {
                            context.getString(R.string.oplus_input_unknown_error)
                        }
                        return LaunchResult.Failed(
                            context.getString(R.string.oplus_input_launch_root_failed, result.exitCode, detail)
                        )
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Exception during root am start", t)
                    val detail = t.message ?: context.getString(R.string.oplus_input_unknown_exception)
                    return LaunchResult.Failed(
                        context.getString(R.string.oplus_input_launch_root_error, detail)
                    )
                }
            } else {
                return LaunchResult.Failed(context.getString(R.string.oplus_input_launch_not_exported))
            }
        } catch (actNotFound: android.content.ActivityNotFoundException) {
            Log.e(TAG, "DocumentEmptyActivity not found in $targetPkg", actNotFound)
            return LaunchResult.Failed(
                context.getString(R.string.oplus_input_launch_activity_missing, DOCUMENT_ACTIVITY_CLASS)
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected error launching parasitic page", t)
            val detail = t.message ?: context.getString(R.string.oplus_input_unknown_exception)
            return LaunchResult.Failed(context.getString(R.string.oplus_input_launch_failed, detail))
        }
    }
}
