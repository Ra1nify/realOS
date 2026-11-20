package io.github.ra1nify.realos.xposed.hooks.navigation

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.hooks.navigation.ui.SettingsButtonGlassCompose
import io.github.ra1nify.realos.xposed.hooks.navigation.ui.SettingsButtonNavCompose
import io.github.ra1nify.realos.xposed.hooks.navigation.ui.SettingsButtonNavHostCallbacks
import io.github.ra1nify.realos.xposed.hooks.navigation.ui.SettingsButtonVibrateCompose
import io.github.ra1nify.realos.xposed.hooks.navigation.ui.SettingsComposeHost
import io.github.ra1nify.realos.xposed.hooks.navigation.ui.SettingsGestureAdvancedCompose
import io.github.ra1nify.realos.xposed.hooks.navigation.ui.SettingsGestureAppearanceCallbacks
import io.github.ra1nify.realos.xposed.hooks.navigation.ui.SettingsGestureAppearanceCompose
import io.github.ra1nify.realos.xposed.hooks.navigation.ui.SettingsGestureMiscCompose
import io.github.ra1nify.realos.xposed.hooks.navigation.ui.SettingsGestureVibrateCompose
import io.github.ra1nify.realos.navigation.SettingsNavContract

/**
 * Host activity hook for RealOS navigation sub-pages.
 *
 * Intercepts the empty shell Activity to render Compose-based sub-settings pages.
 */
object SettingsNavHostHook {

    private const val TAG = "RealOS-SettingsNavHost"

    /** Re-attach attempt limit, counted in layout callbacks. */
    private const val MAX_ATTACH_ATTEMPTS = 6

    /** Fallback delay before showing content when no layout callback arrives. */
    private const val MAX_ATTACH_WAIT_MS = 400L

    /** Tag of the injected Compose root view, used to detect a mounted realOS subpage. */
    private const val TAG_COMPOSED_PAGE = "realos_composed_subpage"


    // Page actions & types shared between app and host
    private const val ACTION_BUTTON = SettingsNavContract.ACTION_BUTTON
    private const val ACTION_BUTTON_GLASS = SettingsNavContract.ACTION_BUTTON_GLASS
    private const val ACTION_VIBRATION = SettingsNavContract.ACTION_VIBRATION
    private const val ACTION_BUTTON_VIBRATION = SettingsNavContract.ACTION_BUTTON_VIBRATION
    private const val ACTION_APPEARANCE = SettingsNavContract.ACTION_APPEARANCE
    private const val ACTION_ADVANCED = SettingsNavContract.ACTION_ADVANCED
    private const val ACTION_ADVANCED_MISC = SettingsNavContract.ACTION_ADVANCED_MISC

    private const val LEGACY_ACTION_BUTTON = "com.luckyfuse.settings.action.NAV_BUTTON"
    private const val LEGACY_ACTION_BUTTON_GLASS = "com.luckyfuse.settings.action.NAV_BUTTON_GLASS"
    private const val LEGACY_ACTION_VIBRATION = "com.luckyfuse.settings.action.NAV_VIBRATION"
    private const val LEGACY_ACTION_APPEARANCE = "com.luckyfuse.settings.action.NAV_APPEARANCE"
    private const val LEGACY_ACTION_ADVANCED = "com.luckyfuse.settings.action.NAV_ADVANCED"

    private const val EXTRA_PAGE_TYPE = SettingsNavContract.EXTRA_PAGE_TYPE
    private const val PAGE_BUTTON = SettingsNavContract.PAGE_BUTTON
    private const val PAGE_BUTTON_GLASS = SettingsNavContract.PAGE_BUTTON_GLASS
    private const val PAGE_VIBRATION = SettingsNavContract.PAGE_VIBRATION
    private const val PAGE_BUTTON_VIBRATION = SettingsNavContract.PAGE_BUTTON_VIBRATION
    private const val PAGE_APPEARANCE = SettingsNavContract.PAGE_APPEARANCE
    private const val PAGE_ADVANCED = SettingsNavContract.PAGE_ADVANCED
    private const val PAGE_ADVANCED_MISC = SettingsNavContract.PAGE_ADVANCED_MISC

    private const val EXTRA_SHOW_FRAGMENT = SettingsNavContract.EXTRA_SHOW_FRAGMENT
    private const val EXTRA_SHOW_FRAGMENT_AS_SUBSETTING = SettingsNavContract.EXTRA_SHOW_FRAGMENT_AS_SUBSETTING

    @Volatile
    private var module: XposedModule? = null

    fun reset() {
        module = null
    }

    private fun log(level: Int, msg: String) {
        module?.log(level, TAG, msg)
    }

    private fun log(level: Int, msg: String, t: Throwable) {
        module?.log(level, TAG, msg, t)
    }

    fun install(module: XposedModule, classLoader: ClassLoader, handles: SettingsHandles) {
        this.module = module

        val shellName = handles.shellActivityName
        val onCreate = handles.settingsActivityOnCreateMethod

        // Framework method first: it always exists. Host Activity onCreate and
        // canHostSubPage stay as fallbacks only.
        val frameworkOnCreate = runCatching {
            Activity::class.java.getDeclaredMethod("onCreate", android.os.Bundle::class.java)
        }.getOrNull()
        val targetOnCreate = frameworkOnCreate ?: onCreate ?: run {
            module.log(Log.WARN, TAG, "fail closed: no onCreate to hook -> skipping the subpage host")
            return
        }

        // Hooks the framework Activity#onCreate instead of a host Activity onCreate.
        //
        // The app-launched entry is a sub-settings container class such as
        // com.oplus.settings.OplusSubSettings$CompatibleActivity, whose supertypes skip
        // com.android.settings.SettingsActivity. Every Activity creation in the process
        // passes through the framework method; the intent action / pageType filter
        // selects this module's pages, and other intents proceed immediately.
        val targetLabel = if (frameworkOnCreate != null) "Activity#onCreate" else "SettingsActivity#onCreate"

        runCatching {
            module.hook(targetOnCreate).intercept { chain ->
                val activity = chain.thisObject as? Activity ?: return@intercept chain.proceed()
                val page = pageTypeOf(activity.intent)
                val result = chain.proceed()
                if (page == null) {
                    // Plain host intent: rebuilds the instance when our subpage is mounted.
                    restoreHostContentIfNeeded(activity, "onCreate")
                    return@intercept result
                }
                log(Log.INFO, "Subpage requested: page=$page activity=${activity.javaClass.name}")
                if (activity.isFinishing) {
                    log(Log.WARN, "Host activity finishing, skip configuring subpage: $page")
                    return@intercept result
                }
                // Takes over in onCreate, right after the host onCreate returns.
                //
                // The intent action is sampled here; the host rewrites the intent after
                // onCreate and pageTypeOf would return null at onResume.
                try {
                    buildPage(activity, classLoader, page)
                } catch (t: Throwable) {
                    log(
                        Log.ERROR,
                        "Failed to configure subpage: ${t.stackTraceToString().replace('\n', ' ')}"
                    )
                }
                result
            }
            log(Log.INFO, "Hook installed: $targetLabel (shell=$shellName)")
        }.onFailure {
            log(Log.ERROR, "fail closed: failed to hook onCreate", it)
        }

        handles.settingsActivityOnActivityResultMethod?.let { method ->
            runCatching {
                module.hook(method).intercept { chain ->
                    val activity = chain.thisObject as? Activity
                    if (activity != null && activity.javaClass.name == shellName) {
                        val requestCode = chain.args.getOrNull(0) as? Int ?: 0
                        val resultCode = chain.args.getOrNull(1) as? Int ?: 0
                        val data = chain.args.getOrNull(2) as? Intent
                        log(Log.INFO, "SettingsActivity#onActivityResult: req=$requestCode res=$resultCode uri=${data?.data}")
                        if (SettingsButtonNavHostCallbacks.handleActivityResult(activity, requestCode, resultCode, data)) {
                            return@intercept null
                        }
                        if (SettingsGestureAppearanceCallbacks.handleActivityResult(activity, requestCode, resultCode, data)) {
                            return@intercept null
                        }
                    }
                    chain.proceed()
                }
                log(Log.INFO, "Hook installed: SettingsActivity#onActivityResult (for custom icon picker)")
            }.onFailure {
                log(Log.WARN, "Failed to hook onActivityResult: ${it.message}")
            }
        }

        // singleTop reuse: an Activity already on the stack runs onNewIntent only.
        //
        // Reuses pageTypeOf + buildPage; buildPage clears android.R.id.content first,
        // so repeat entries do not stack Compose views.
        //
        // The resolver usually returns Activity.onNewIntent itself; the intent
        // action / pageType filter applies this hook to every Activity in the process.
        val onNewIntentMethod = handles.settingsActivityOnNewIntentMethod ?: runCatching {
            Activity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
        }.getOrNull()
        onNewIntentMethod?.let { method ->
            runCatching {
                module.hook(method).intercept { chain ->
                    val activity = chain.thisObject as? Activity ?: return@intercept chain.proceed()
                    // The new Intent is in the argument; activity.intent may be the old one.
                    val newIntent = chain.args.getOrNull(0) as? Intent
                    val page = pageTypeOf(newIntent)
                    if (page == null) {
                        val r = chain.proceed()
                        // Plain host intent reusing the instance; rebuilds when our page is mounted.
                        restoreHostContentIfNeeded(activity, "onNewIntent", newIntent)
                        return@intercept r
                    }
                    log(Log.INFO, "Subpage requested (onNewIntent): page=$page activity=${activity.javaClass.name}")
                    val result = chain.proceed()
                    if (activity.isFinishing) {
                        log(Log.WARN, "onNewIntent: shell activity finishing, skip subpage: $page")
                        return@intercept result
                    }
                    try {
                        buildPage(activity, classLoader, page)
                    } catch (t: Throwable) {
                        log(Log.ERROR, "Failed to configure subpage (onNewIntent): ${t.message}")
                    }
                    result
                }
                log(Log.INFO, "Hook installed: SettingsActivity#onNewIntent (singleTop reuse)")
            }.onFailure {
                log(Log.WARN, "Failed to hook onNewIntent: ${it.message}")
            }
        }
    }

    /** Reports whether the injected Compose subpage is still mounted in the Activity content. */
    private fun hasComposedPage(activity: Activity): Boolean = runCatching {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return false
        for (i in 0 until content.childCount) {
            if (content.getChildAt(i).tag == TAG_COMPOSED_PAGE) return true
        }
        false
    }.getOrDefault(false)

    /**
     * Recreates the instance when the host starts or reuses it with a plain intent
     * while our subpage is still mounted.
     */
    private fun restoreHostContentIfNeeded(
        activity: Activity,
        why: String,
        newIntent: Intent? = null
    ) {
        if (!hasComposedPage(activity)) return
        log(
            Log.INFO,
            "Stale realOS subpage on $why, recreating ${activity.javaClass.name} to restore host content"
        )
        // recreate() reuses the current intent; the new plain intent is written back first.
        if (newIntent != null) runCatching { activity.intent = newIntent }
        activity.window?.decorView?.post { runCatching { activity.recreate() } }
    }

    private fun pageTypeOf(intent: Intent?): String? {
        if (intent == null) return null
        val action = intent.action
        val extra = intent.getStringExtra(EXTRA_PAGE_TYPE)
        return when {
            action == ACTION_BUTTON || action == LEGACY_ACTION_BUTTON || extra == PAGE_BUTTON -> PAGE_BUTTON
            action == ACTION_BUTTON_GLASS || action == LEGACY_ACTION_BUTTON_GLASS || extra == PAGE_BUTTON_GLASS -> PAGE_BUTTON_GLASS
            action == ACTION_VIBRATION || action == LEGACY_ACTION_VIBRATION || extra == PAGE_VIBRATION -> PAGE_VIBRATION
            action == ACTION_BUTTON_VIBRATION || extra == PAGE_BUTTON_VIBRATION -> PAGE_BUTTON_VIBRATION
            action == ACTION_APPEARANCE || action == LEGACY_ACTION_APPEARANCE || extra == PAGE_APPEARANCE -> PAGE_APPEARANCE
            action == ACTION_ADVANCED || action == LEGACY_ACTION_ADVANCED || extra == PAGE_ADVANCED -> PAGE_ADVANCED
            action == ACTION_ADVANCED_MISC || extra == PAGE_ADVANCED_MISC -> PAGE_ADVANCED_MISC
            else -> null
        }
    }

    fun startSubPage(activity: Activity, action: String, pageType: String) {
        runCatching {
            val intent = Intent(activity, activity.javaClass).apply {
                this.action = action
                putExtra(EXTRA_PAGE_TYPE, pageType)
                shellFragmentName(activity)?.let { putExtra(EXTRA_SHOW_FRAGMENT, it) }
                putExtra(EXTRA_SHOW_FRAGMENT_AS_SUBSETTING, true)
            }
            activity.startActivity(intent)
        }.onFailure { log(Log.ERROR, "Failed to launch subpage: $pageType", it) }
    }

    private fun shellFragmentName(ctx: Context): String? = runCatching {
        val info = ctx.packageManager.getActivityInfo(
            ComponentName(ctx.packageName, ctx.javaClass.name),
            PackageManager.GET_META_DATA
        )
        info.metaData?.getString("com.android.settings.FRAGMENT_CLASS")
    }.getOrNull()

    private fun buildPage(activity: Activity, classLoader: ClassLoader, page: String) {
        removeAllFragments(activity)

        val window = activity.window
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
        window.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        )

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Content runs at alpha=0 until stable while the window background is transparent;
        // the decor is filled with the theme background color first and cleared by the
        // layout callback below.
        val stabilityBackdrop = runCatching {
            val attrs = activity.obtainStyledAttributes(
                intArrayOf(android.R.attr.colorBackground)
            )
            val color = attrs.getColor(0, Color.WHITE)
            attrs.recycle()
            color
        }.getOrDefault(Color.WHITE)
        runCatching { window.decorView.setBackgroundColor(stabilityBackdrop) }

        activity.title = ""
        runCatching {
            val actionBar = activity.javaClass.getMethod("getSupportActionBar").invoke(activity)
            actionBar?.javaClass?.getMethod("hide")?.invoke(actionBar)
        }
        runCatching { activity.actionBar?.hide() }

        val composeView = SettingsComposeHost.create(activity, module) {
            when (page) {
                PAGE_BUTTON -> SettingsButtonNavCompose(activity)
                PAGE_BUTTON_GLASS -> SettingsButtonGlassCompose(activity)
                PAGE_VIBRATION -> SettingsGestureVibrateCompose(activity)
                PAGE_BUTTON_VIBRATION -> SettingsButtonVibrateCompose(activity)
                PAGE_APPEARANCE -> SettingsGestureAppearanceCompose(activity)
                PAGE_ADVANCED -> SettingsGestureAdvancedCompose(activity)
                PAGE_ADVANCED_MISC -> SettingsGestureMiscCompose(activity)
                else -> SettingsGestureMiscCompose(activity)
            }
        }

        // Tags the injected root; a later non-subpage host intent detects the tag and rebuilds.
        composeView.tag = TAG_COMPOSED_PAGE

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        if (content != null) {
            content.setPadding(0, 0, 0, 0)
            (content.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(0, 0, 0, 0)
            content.removeAllViews()
            content.addView(
                composeView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        } else {
            activity.window.setContentView(
                composeView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        (content?.parent as? ViewGroup)?.let { parent ->
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child !== content) {
                    val name = child.javaClass.name
                    if (name.contains("ActionBarContainer") ||
                        name.contains("Toolbar") ||
                        name.contains("AppBarLayout")
                    ) {
                        child.visibility = View.GONE
                    }
                }
            }
        }

        // Host fragment transactions often commit after onCreate and cover the content
        // for a frame. Content stays invisible while up to MAX_ATTACH_ATTEMPTS layout
        // callbacks re-attach our view; content is shown once the view stays on top.
        if (content != null) {
            content.alpha = 0f
            var attempts = 0
            val listener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val onTop = content.childCount > 0 &&
                        content.getChildAt(content.childCount - 1) === composeView
                    if (!onTop) {
                        log(Log.INFO, "Compose view covered by host, re-attaching (attempt=${attempts + 1}): page=$page")
                        runCatching {
                            if (composeView.parent !== content) {
                                (composeView.parent as? ViewGroup)?.removeView(composeView)
                                content.addView(
                                    composeView,
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            } else {
                                composeView.bringToFront()
                            }
                        }.onFailure { log(Log.WARN, "Re-attach Compose view failed: ${it.message}") }
                    }
                    attempts++
                    if (onTop || attempts >= MAX_ATTACH_ATTEMPTS) {
                        runCatching {
                            content.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        }
                        content.alpha = 1f
                        runCatching { activity.window?.decorView?.background = null }
                        log(
                            Log.INFO,
                            "Subpage stable after $attempts layout pass(es): page=$page"
                        )
                    }
                }
            }
            content.viewTreeObserver.addOnGlobalLayoutListener(listener)
            // Fallback: shows the content after MAX_ATTACH_WAIT_MS when no layout callback fires.
            content.postDelayed({
                content.alpha = 1f
                runCatching { activity.window?.decorView?.background = null }
            }, MAX_ATTACH_WAIT_MS)
        }

        log(Log.INFO, "Subpage successfully injected with Compose: page=$page")
    }

    private fun removeAllFragments(activity: Activity) {
        runCatching {
            val fm = activity.javaClass.getMethod("getSupportFragmentManager").invoke(activity) ?: return
            // Executes pending host transactions before fragments are removed.
            runCatching { fm.javaClass.getMethod("executePendingTransactions").invoke(fm) }
            val fragments = runCatching {
                fm.javaClass.getMethod("getFragments").invoke(fm) as? List<*>
            }.getOrNull() ?: return
            if (fragments.isEmpty()) return

            val fragmentCls = activity.javaClass.classLoader
                ?.loadClass("androidx.fragment.app.Fragment") ?: return
            val tx = fm.javaClass.getMethod("beginTransaction").invoke(fm) ?: return
            val remove = tx.javaClass.getMethod("remove", fragmentCls)
            var n = 0
            for (f in fragments) {
                if (f != null && runCatching { remove.invoke(tx, f) }.isSuccess) n++
            }
            runCatching { tx.javaClass.getMethod("commitNow").invoke(tx) }
                .onFailure {
                    runCatching { tx.javaClass.getMethod("commitAllowingStateLoss").invoke(tx) }
                }
            log(Log.INFO, "Removed host fragments: $n/${fragments.size}")
        }.onFailure { log(Log.WARN, "Failed to remove host fragments", it) }
    }
}
