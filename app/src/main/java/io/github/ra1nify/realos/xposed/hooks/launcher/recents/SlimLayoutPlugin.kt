package io.github.ra1nify.realos.xposed.hooks.launcher.recents

import android.graphics.Canvas
import android.graphics.Rect
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import java.lang.reflect.Method

/**
 * `realos.slim` — a vertical capsule list.
 *
 * Presentation and interaction:
 * - each row = the official inflated `oplus_task_header_override` (icon + name + overflow dots)
 * - container = an overlay on `recent_container` + the native `ScrollView`, vertical scrolling intact
 * - the native cards go fully `alpha = 0`; layout and state machine stay unchanged
 *
 * **Delegate geometry is not taken over**: the host falls back to the tile delegate for unknown styles,
 * the native cards are only hidden while this plugin draws, so `wrapDelegate` returns `base` unchanged.
 *
 * Launch animation alignment ([installBoundsHook]): the host's open animation starts from
 * `taskView.getThumbnail().getBoundsOnScreen(rect)`
 * (the `OVERVIEW` branch in `OplusLauncherAppTransitionHelper`);
 * the thumbnail reports "the capsule's position", and the animation expands from the capsule —
 * no TaskView layout change, no effect on other styles (the hook checks the active flag internally).
 */
object SlimLayoutPlugin : RecentsLayoutPlugin {

    const val ID = "realos.slim"

    private const val TAG = "RealOS-SlimPlugin"

    private const val THUMBNAIL_IMPL_CLASS =
        "com.android.quickstep.views.OplusTaskThumbnailViewImpl"

    private const val CONTAINER_CLASS = "com.android.launcher3.RecentContainerView"

    private const val RECENTS_VIEW_CLASS = "com.android.quickstep.views.OplusRecentsViewImpl"

    private const val TASK_VIEW_CLASS = "com.android.quickstep.views.TaskView"

    private const val HEADER_CLASS = "com.oplus.quickstep.views.OplusTaskHeaderView"

    override val id: String = ID

    private var overlay: SlimOverlay? = null

    /** libxposed cannot uninstall hooks; installed once, with [overlay] nullness as the active check */
    private var boundsHookInstalled = false

    private var containerHookInstalled = false

    private var childrenHookInstalled = false

    private var titleHookInstalled = false

    override fun wrapDelegate(base: Any, ctx: PluginContext): Any {
        // Geometry is not taken over; the native cards are hidden, so their geometry does not affect the visuals.
        // This also runs a rebind check: a launcher Activity rebuild swaps the recentsView instance.
        overlay?.rebind(ctx)
        return base
    }

    override fun onActivate(ctx: PluginContext) {
        overlay?.destroy()
        overlay = SlimOverlay(ctx).also { it.start() }
        installBoundsHook(ctx)
        installContainerDrawHook(ctx)
        installChildrenChangedHook(ctx)
        installTitleChangedHook(ctx)
        Log.i(TAG, "onActivate: $ID")
    }

    override fun onDeactivate() {
        overlay?.destroy()
        overlay = null
        Log.i(TAG, "onDeactivate: $ID")
    }

    // ─────────────────────────────────────────────────────────────
    // Launch animation start point
    // ─────────────────────────────────────────────────────────────

    private fun installBoundsHook(ctx: PluginContext) {
        if (boundsHookInstalled) return
        boundsHookInstalled = true

        runCatching {
            val clazz = Class.forName(THUMBNAIL_IMPL_CLASS, false, ctx.classLoader)
            val method = runCatching {
                clazz.getDeclaredMethod("getBoundsOnScreen", Rect::class.java)
            }.getOrElse {
                clazz.getMethod("getBoundsOnScreen", Rect::class.java)
            }.apply { isAccessible = true }

            ctx.module.hook(method).intercept { chain ->
                val active = this@SlimLayoutPlugin.overlay
                    ?: return@intercept chain.proceed()
                val thumbnail = chain.thisObject as? View ?: return@intercept chain.proceed()
                val out = chain.args.getOrNull(0) as? Rect ?: return@intercept chain.proceed()

                val taskView = ownerTaskView(thumbnail) ?: return@intercept chain.proceed()
                val capsule = active.capsuleRectOnScreen(taskView)
                    ?: return@intercept chain.proceed()

                out.set(capsule)
                null
            }
            ctx.module.log(Log.INFO, TAG, "Installed thumbnail getBoundsOnScreen (launch animation starts from the capsule)")
        }.onFailure {
            ctx.module.log(Log.WARN, TAG, "Thumbnail bounds hook install failed (the launch animation keeps the host behaviour)", it)
        }
    }

    /** TaskView owning the thumbnail: its own getTaskView() first, then walking up the parents */
    private fun ownerTaskView(thumbnail: View): View? {
        runCatching {
            thumbnail.javaClass.getMethod("getTaskView").invoke(thumbnail) as? View
        }.getOrNull()?.let { return it }

        var parent = thumbnail.parent
        while (parent is View) {
            if (parent.javaClass.name == "com.android.quickstep.views.TaskView") return parent
            parent = parent.parent
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────
    // Force-hide native cards every frame
    // ─────────────────────────────────────────────────────────────

    /**
     * Forces the native cards back to invisible before `RecentContainerView#dispatchDraw`.
     *
     * The host resets `RecentsView.alpha` to 1 on state changes, scrolling, and animations.
     * A 400ms poll leaves the native tiled cards visible for the gap (the observed
     * "tiled strips flash while scrolling").
     *
     * Placed before `dispatchDraw`'s `proceed()`, so it corrects once **before every frame draws**,
     * changing one property and only while this plugin is active.
     */
    private fun installContainerDrawHook(ctx: PluginContext) {
        if (containerHookInstalled) return
        containerHookInstalled = true

        runCatching {
            val clazz = Class.forName(CONTAINER_CLASS, false, ctx.classLoader)
            val method = runCatching {
                clazz.getDeclaredMethod("dispatchDraw", Canvas::class.java)
            }.getOrElse {
                clazz.getMethod("dispatchDraw", Canvas::class.java)
            }.apply { isAccessible = true }

            ctx.module.hook(method).intercept { chain ->
                this@SlimLayoutPlugin.overlay?.forceNativeHidden()
                chain.proceed()
            }
            ctx.module.log(Log.INFO, TAG, "Installed RecentContainerView#dispatchDraw (suppresses the native card every frame)")
        }.onFailure {
            ctx.module.log(Log.WARN, TAG, "dispatchDraw hook install failed (the native card may occasionally flash)", it)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Event-driven list sync
    // ─────────────────────────────────────────────────────────────

    /**
     * Replaces "polling" with "events".
     *
     * Three event points cover every list change:
     * - `attachAndBindChilds`: full task list rebuild (host `removeAllViews()` then adds one by one,
     *   see `RecentsView:1905`), the main entry;
     * - `onViewAdded` / `onViewRemoved`: single TaskView added/removed (task deletion goes this way);
     * - `TaskView#bind`: task rebind; title/icon are filled **asynchronously**, known only after bind completes.
     *
     * A hit only calls `requestSync()`; the actual sync runs once on the next frame (debounced).
     */
    private fun installChildrenChangedHook(ctx: PluginContext) {
        if (childrenHookInstalled) return
        childrenHookInstalled = true

        val recentsClass = runCatching {
            Class.forName(RECENTS_VIEW_CLASS, false, ctx.classLoader)
        }.getOrNull() ?: return

        val targets = buildList {
            findMethod(recentsClass, "attachAndBindChilds", ArrayList::class.java)?.let { add(it) }
            findMethod(recentsClass, "onViewAdded", View::class.java)?.let { add(it) }
            findMethod(recentsClass, "onViewRemoved", View::class.java)?.let { add(it) }
        }

        var installed = 0
        for (method in targets) {
            runCatching {
                method.isAccessible = true
                ctx.module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    this@SlimLayoutPlugin.overlay?.requestSync()
                    result
                }
                installed++
            }.onFailure { ctx.module.log(Log.WARN, TAG, "hook ${method.name} failed", it) }
        }

        // Task rebind → title/icon available at this point
        runCatching {
            val taskViewClass = Class.forName(TASK_VIEW_CLASS, false, ctx.classLoader)
            val bind = taskViewClass.methods.firstOrNull {
                it.name == "bind" && it.parameterCount == 2
            } ?: return@runCatching
            ctx.module.hook(bind).intercept { chain ->
                val result = chain.proceed()
                this@SlimLayoutPlugin.overlay?.requestSync()
                result
            }
            installed++
        }.onFailure { ctx.module.log(Log.WARN, TAG, "hook TaskView#bind failed", it) }

        ctx.module.log(Log.INFO, TAG, "Installed $installed list change events (event-driven sync)")
    }

    /**
     * Title change event.
     *
     * Host titles are filled **asynchronously**: `Task.title` may start null and be completed later
     * from the icon/title cache. Our row reads the native header's TextView, so one sync runs when
     * the host updates the title — no polling, no "icon first, name later".
     */
    private fun installTitleChangedHook(ctx: PluginContext) {
        if (titleHookInstalled) return
        titleHookInstalled = true

        runCatching {
            val headerClass = Class.forName(HEADER_CLASS, false, ctx.classLoader)
            val method = headerClass.methods.firstOrNull {
                it.name == "setTitle" && it.parameterCount == 1
            } ?: return@runCatching

            ctx.module.hook(method).intercept { chain ->
                val result = chain.proceed()
                this@SlimLayoutPlugin.overlay?.requestSync()
                result
            }
            ctx.module.log(Log.INFO, TAG, "Installed OplusTaskHeaderView#setTitle (asynchronous title update)")
        }.onFailure { ctx.module.log(Log.WARN, TAG, "hook setTitle failed", it) }
    }

    /** Finds a method up the inheritance chain: `onViewAdded`/`onViewRemoved` are protected and may be declared in a parent */
    private fun findMethod(clazz: Class<*>, name: String, vararg params: Class<*>): Method? {
        var c: Class<*>? = clazz
        while (c != null && c != Any::class.java) {
            runCatching {
                c!!.getDeclaredMethod(name, *params)
            }.getOrNull()?.let { return it }
            c = c!!.superclass
        }
        return null
    }
}
