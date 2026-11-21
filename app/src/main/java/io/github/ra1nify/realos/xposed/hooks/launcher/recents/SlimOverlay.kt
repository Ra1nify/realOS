package io.github.ra1nify.realos.xposed.hooks.launcher.recents

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * View layer of `realos.slim`: a column of capsules, **all self-drawn**.
 *
 * **Data source: reads the task model directly, not through TaskView.**
 * `com.android.quickstep.db` static singleton `.get(ctx)` (RecentsModel) → its recent task list member
 * → the `List<Task>` in it (all tasks, newest first). Field names are found structurally; no R8 names are hardcoded.
 *
 * TaskView no longer supplies data: the host does **not refresh headers** of off-screen page cards,
 * so titles/icons stop at stale pool content and the list shows a repeating column.
 * The `Task` in the model is the copy fixed at `bind()`, unaffected by visibility.
 *
 * TaskView now serves one purpose: **the launch animation anchor** (the N5 `getBoundsOnScreen` hook).
 * Rendering does not depend on it — the native panel is fully `alpha = 0`.
 *
 * List order: **newest at the bottom**; entering lands at the bottom, scrolling up reveals older tasks.
 */
internal class SlimOverlay(ctx: PluginContext) {

    private class Row(
        val container: FrameLayout,
        val iconView: ImageView,
        val titleView: TextView,
        val taskId: Int,
        var boundTitle: String? = null,
        var boundIcon: Drawable? = null
    )

    private var ctx: PluginContext = ctx

    private val hostContext: Context get() = ctx.hostContext

    private var root: FrameLayout? = null
    private var column: LinearLayout? = null
    private var scroll: ScrollView? = null

    /** taskId → row */
    private val rows = HashMap<Int, Row>()

    /** taskIds being dismissed: the host model updates asynchronously; skipped meanwhile */
    private val dismissing = HashSet<Int>()

    private val savedVisibility = java.util.WeakHashMap<View, Int>()

    private var ticker: Runnable? = null
    private var taskViewClass: Class<*>? = null

    private var lastSizedHeight = -1
    private var lastBottomInset = -1

    @Volatile
    private var syncScheduled = false

    /** Whether the list already scrolled to the bottom after entering recents (newest at the bottom) */
    private var scrolledToBottom = false

    private var diagDone = false

    private var idDockPanel = 0
    private var idClearPanel = 0
    private var idDots = 0
    private var idsResolved = false

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    fun start() {
        if (ticker != null) return
        taskViewClass = runCatching {
            Class.forName("com.android.quickstep.views.TaskView", false, ctx.classLoader)
        }.getOrNull()
        resolveIds()
        SlimTaskSource.prime(hostContext, ctx.classLoader)

        val runnable = object : Runnable {
            override fun run() {
                runCatching { tick() }.onFailure { Log.w(TAG, "Sync failed", it) }
                ctx.recentsView.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
        ticker = runnable
        ctx.recentsView.postDelayed(runnable, REFRESH_INTERVAL_MS)
    }

    fun rebind(newCtx: PluginContext) {
        if (newCtx.recentsView === ctx.recentsView) return
        Log.i(TAG, "recentsView replaced; rebuilding the overlay")
        destroy()
        ctx = newCtx
        start()
    }

    fun destroy() {
        stopTicker()
        removeRoot()
        rows.clear()
        dismissing.clear()
        restoreHost()
        taskViewClass = null
        idsResolved = false
    }

    private fun stopTicker() {
        ticker?.let { ctx.recentsView.removeCallbacks(it) }
        ticker = null
    }

    fun requestSync() {
        val rootView = root ?: return
        if (syncScheduled) return
        syncScheduled = true
        rootView.post {
            syncScheduled = false
            runCatching { tick() }.onFailure { Log.w(TAG, "Event-driven sync failed", it) }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Each sync round
    // ─────────────────────────────────────────────────────────────

    private fun tick() {
        if (root?.isAttachedToWindow != true) {
            buildOverlay()
            if (root?.isAttachedToWindow != true) return
        }

        if (!ctx.recentsView.isShown) {
            setVisible(false)
            applyNativeHidden(false)
            scrolledToBottom = false
            return
        }

        setVisible(true)
        ensureSizing()
        ensureBottomInset()

        val rendered = sync()
        if (rendered == 0) {
            if (nativeHidden) Log.w(TAG, "No task available; falling back to host rendering")
            applyNativeHidden(false)
            return
        }
        applyNativeHidden(true)
        hideDockPanel()
        raiseClearPanel()
        scrollToBottomIfNeeded()
    }

    private fun buildOverlay() {
        val container = ctx.container ?: return
        if (root != null) removeRoot()
        resolveIds()

        val density = container.resources.displayMetrics.density.coerceAtLeast(1f)

        val rootView = FrameLayout(hostContext).apply { tag = TAG_ROOT }
        val sv = ScrollView(hostContext).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val list = LinearLayout(hostContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        sv.addView(list)
        rootView.addView(
            sv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val side = (SIDE_PADDING_DP * density).toInt()
        val top = (TOP_PADDING_DP * density).toInt()
        sv.setPadding(side, top, side, (96 * density).toInt())

        container.addView(
            rootView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root = rootView
        column = list
        scroll = sv
        lastSizedHeight = -1
        lastBottomInset = -1
        scrolledToBottom = false
        ensureSizing()
    }

    private fun removeRoot() {
        val rootView = root
        root = null
        column = null
        scroll = null
        lastSizedHeight = -1
        lastBottomInset = -1
        if (rootView == null) return

        runCatching {
            val parent = rootView.parent as? ViewGroup
            when {
                parent == null -> Unit
                parent.isInLayout -> parent.post { runCatching { parent.removeView(rootView) } }
                else -> parent.removeView(rootView)
            }
        }.onFailure { Log.w(TAG, "Failed to remove the overlay", it) }
    }

    private fun ensureSizing() {
        val container = ctx.container ?: return
        val rootView = root ?: return
        if (lastSizedHeight == container.height) return
        lastSizedHeight = container.height
        rootView.requestLayout()
    }

    private fun ensureBottomInset() {
        val container = ctx.container ?: return
        val sv = scroll ?: return
        val density = container.resources.displayMetrics.density.coerceAtLeast(1f)

        val clearPanel = findClearPanel(container)
        val bottomInset = if (clearPanel != null && clearPanel.top > 0) {
            (container.height - clearPanel.top).coerceAtLeast(0)
        } else {
            (96 * density).toInt()
        }
        if (bottomInset == lastBottomInset) return
        lastBottomInset = bottomInset

        val side = (SIDE_PADDING_DP * density).toInt()
        val top = (TOP_PADDING_DP * density).toInt()
        sv.setPadding(side, top, side, bottomInset + (12 * density).toInt())
    }

    private fun setVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        root?.let { if (it.visibility != v) it.visibility = v }
    }

    /** Newest at the bottom: entering lands at the bottom */
    private fun scrollToBottomIfNeeded() {
        if (scrolledToBottom) return
        val sv = scroll ?: return
        if (sv.height == 0) return
        scrolledToBottom = true
        sv.post { runCatching { sv.fullScroll(View.FOCUS_DOWN) } }
    }

    // ─────────────────────────────────────────────────────────────
    // Native panel: fully transparent (not part of the presentation)
    // ─────────────────────────────────────────────────────────────

    private var nativeHidden = false
    private var savedRecentsAlpha = 1f
    private var alphaCaptured = false
    private var dockRef: View? = null

    private fun applyNativeHidden(hidden: Boolean) {
        val view = ctx.recentsView
        if (hidden) {
            if (!alphaCaptured) {
                savedRecentsAlpha = view.alpha
                alphaCaptured = true
            }
            if (view.alpha != 0f) view.alpha = 0f
        } else if (alphaCaptured && view.alpha != savedRecentsAlpha) {
            view.alpha = savedRecentsAlpha
        }
        if (nativeHidden != hidden) {
            nativeHidden = hidden
            ctx.module.log(Log.INFO, TAG, "Native panel: ${if (hidden) "alpha=0" else "alpha=$savedRecentsAlpha"}")
        }
    }

    /** Forced once before each frame draws: the host restores alpha and the dock on state changes/scrolling */
    fun forceNativeHidden() {
        if (!nativeHidden) return
        val view = ctx.recentsView
        if (view.alpha != 0f) view.alpha = 0f

        val dock = dockRef?.takeIf { it.isAttachedToWindow } ?: findDock()
        if (dock != null && dock.visibility != View.GONE) dock.visibility = View.GONE
    }

    private fun findDock(): View? {
        val container = ctx.container ?: return null
        if (idDockPanel == 0) return null
        return container.findViewById<View>(idDockPanel)?.also { dockRef = it }
    }

    private fun hideDockPanel() {
        val dock = dockRef?.takeIf { it.isAttachedToWindow } ?: findDock() ?: return
        savedVisibility.getOrPut(dock) { dock.visibility }
        if (dock.visibility != View.GONE) dock.visibility = View.GONE
    }

    private fun raiseClearPanel() {
        val container = ctx.container ?: return
        if (idClearPanel == 0) return
        container.findViewById<View>(idClearPanel)?.let { clear ->
            savedVisibility.getOrPut(clear) { clear.visibility }
            if (clear.visibility != View.VISIBLE) clear.visibility = View.VISIBLE
            val parent = clear.parent as? ViewGroup
            if (parent != null && parent.getChildAt(parent.childCount - 1) !== clear) {
                clear.bringToFront()
            }
        }
    }

    private fun restoreHost() {
        applyNativeHidden(false)
        dockRef = null
        savedVisibility.forEach { (view, visibility) ->
            runCatching { view.visibility = visibility }
        }
        savedVisibility.clear()
    }

    // ─────────────────────────────────────────────────────────────
    // List sync (data from the task model)
    // ─────────────────────────────────────────────────────────────

    private fun sync(): Int {
        val list = column ?: return 0

        val tasks = SlimTaskSource.tasks(hostContext, ctx.classLoader)
        if (tasks.isEmpty()) return 0

        // tasks are newest-first; the list wants newest at the bottom, so iterate in reverse (oldest → newest)
        val wanted = ArrayList<FrameLayout>(tasks.size)
        for (i in tasks.indices.reversed()) {
            val task = tasks[i] ?: continue
            runCatching {
                val taskId = SlimTaskSource.taskIdOf(task)
                if (taskId <= 0 || taskId in dismissing) return@runCatching

                val title = SlimTaskSource.titleOf(task)
                val icon = SlimTaskSource.iconOf(task)

                val row = rows[taskId] ?: createRow(list, taskId, title, icon)
                if (row.boundTitle != title || row.boundIcon !== icon) {
                    (row.container as? SwipeDismissRow)?.reset()
                    row.titleView.text = title ?: ""
                    if (icon != null) row.iconView.setImageDrawable(icon)
                    row.boundTitle = title
                    row.boundIcon = icon
                }
                wanted.add(row.container)
            }.onFailure { Log.w(TAG, "Failed to sync a single row", it) }
        }

        // Clears the "dismissing" flag after the task leaves the model
        if (dismissing.isNotEmpty()) {
            val aliveIds = tasks.mapNotNull { SlimTaskSource.taskIdOf(it) }.toSet()
            dismissing.retainAll(aliveIds)
        }

        val stale = rows.entries.filter { it.value.container !in wanted }
        for (entry in stale) {
            rows.remove(entry.key)
            list.removeView(entry.value.container)
        }

        for ((index, row) in wanted.withIndex()) {
            if (list.indexOfChild(row) != index) {
                list.removeView(row)
                list.addView(row, index)
            }
        }

        return wanted.size
    }

    private fun createRow(list: LinearLayout, taskId: Int, title: String?, icon: Drawable?): Row {
        val density = hostContext.resources.displayMetrics.density.coerceAtLeast(1f)
        val rowHeight = (CAPSULE_HEIGHT_DP * density).toInt()
        val rowGap = (ROW_GAP_DP * density).toInt()

        val container = SwipeDismissRow(hostContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, rowHeight
            ).also { it.bottomMargin = rowGap }
            clipToOutline = true
            isClickable = true
            background = GradientDrawable().apply {
                cornerRadius = rowHeight / 2f
                setColor(if (isDark(hostContext)) 0x1FFFFFFF else 0x14000000)
            }
        }

        val content = LinearLayout(hostContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(
                (ICON_START_DP * density).toInt(), 0,
                (DOTS_END_DP * density).toInt(), 0
            )
        }

        val iconView = ImageView(hostContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                (ICON_SIZE_DP * density).toInt(), (ICON_SIZE_DP * density).toInt()
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(icon)
        }

        val titleView = TextView(hostContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginStart = (TITLE_MARGIN_DP * density).toInt() }
            text = title ?: ""
            textSize = TITLE_SIZE_SP
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(if (isDark(hostContext)) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val dotsView = ImageView(hostContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                (DOTS_SIZE_DP * density).toInt(), (DOTS_SIZE_DP * density).toInt()
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            runCatching { if (idDots != 0) setImageResource(idDots) }
            setOnClickListener {
                runCatching { showTaskMenuAt(taskId, this) }
                    .onFailure { Log.w(TAG, "Failed to show the task menu", it) }
            }
        }

        container.addView(content)
        content.addView(iconView)
        content.addView(titleView)
        content.addView(dotsView)

        container.setOnClickListener { runCatching { launchTask(taskId) } }
        list.addView(container)

        if (!diagDone) {
            diagDone = true
            ctx.module.log(Log.INFO, TAG, "First row drawn locally: id=$taskId title=${title?.take(12)} icon=${icon != null}")
        }

        val row = Row(container, iconView, titleView, taskId, title, icon)
        container.onDismiss = { dismissRow(row) }
        return row
    }

    // ─────────────────────────────────────────────────────────────
    // Launch / dismiss / menu (TaskView located by taskId for animations and server-side capabilities only)
    // ─────────────────────────────────────────────────────────────

    private fun taskViewOf(taskId: Int): View? {
        val group = ctx.recentsView as? ViewGroup ?: return null
        val tvClass = taskViewClass ?: return null
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i) ?: continue
            if (!tvClass.isInstance(child)) continue
            if (SlimTaskSource.taskIdOfTaskView(child) == taskId) return child
        }
        return null
    }

    /**
     * Launches a task.
     *
     * Prefers the matching TaskView's `performClick()`: that is the host's own launch path and
     * its animation lands on the capsule rectangle aligned in N5.
     * Falls back to `startActivityFromRecents` when no TaskView is found.
     */
    private fun launchTask(taskId: Int) {
        taskViewOf(taskId)?.let { taskView ->
            val ok = runCatching { taskView.performClick() }.getOrDefault(false)
            if (ok) return
        }
        runCatching {
            val am = Class.forName("android.app.ActivityManager")
                .getMethod("getService")
                .invoke(null)
            am.javaClass.getMethod(
                "startActivityFromRecents",
                Int::class.javaPrimitiveType,
                ActivityOptions::class.java
            ).invoke(am, taskId, null)
        }.onFailure { Log.w(TAG, "startActivityFromRecents($taskId) failed", it) }
    }

    /** Swipe-to-dismiss: goes straight to AMS, no TaskView dependency */
    private fun dismissRow(row: Row) {
        val taskId = row.taskId
        dismissing.add(taskId)
        rows.remove(taskId)
        column?.removeView(row.container)

        val ok = runCatching {
            val am = Class.forName("android.app.ActivityManager")
                .getMethod("getService")
                .invoke(null)
            am.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
                .invoke(am, taskId)
        }.onFailure { Log.w(TAG, "AMS.removeTask($taskId) failed", it) }.isSuccess

        if (ok) {
            // AMS does not notify the host; kick one model refresh so the row does not stay in "dismissing"
            SlimTaskSource.refresh(hostContext, ctx.classLoader)
        } else {
            dismissing.remove(taskId)
            Log.w(TAG, "Delete failed; restoring the row on the next pass")
        }
    }

    private fun showTaskMenuAt(taskId: Int, anchor: View) {
        val taskView = taskViewOf(taskId) ?: run {
            Log.w(TAG, "No TaskView found for taskId=$taskId; cannot show the menu")
            return
        }

        // Popup's internal check: only the current page's TaskView may open the menu (n4.showForTask)
        val recents = ctx.recentsView as? ViewGroup
        val index = recents?.indexOfChild(taskView) ?: -1
        if (recents != null && index >= 0) {
            runCatching {
                recents.javaClass
                    .getMethod("setCurrentPage", Int::class.javaPrimitiveType)
                    .invoke(recents, index)
            }.onFailure { Log.w(TAG, "Failed to switch to the current page", it) }
        }

        val icon = runCatching {
            taskView.javaClass.getMethod("getIconView").invoke(taskView) as? View
        }.getOrNull() ?: return

        val iconLoc = IntArray(2)
        val anchorLoc = IntArray(2)
        icon.getLocationOnScreen(iconLoc)
        anchor.getLocationOnScreen(anchorLoc)

        val oldX = icon.translationX
        val oldY = icon.translationY
        icon.translationX = oldX + (anchorLoc[0] - iconLoc[0])
        icon.translationY = oldY + (anchorLoc[1] - iconLoc[1])

        try {
            val shown = taskView.javaClass.methods
                .firstOrNull { it.name == "showTaskMenu" && it.parameterCount == 1 }
                ?.invoke(taskView, icon)
            ctx.module.log(Log.INFO, TAG, "showTaskMenu(id=$taskId) -> $shown")
        } finally {
            icon.post {
                icon.translationX = oldX
                icon.translationY = oldY
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Host resources
    // ─────────────────────────────────────────────────────────────

    private fun resolveIds() {
        if (idsResolved) return
        idDockPanel = hostId(ID_DOCK_PANEL, "id")
        idClearPanel = hostId(ID_CLEAR_PANEL, "id")
        idDots = hostId(ID_DOTS, "drawable")
        idsResolved = true
        ctx.module.log(
            Log.INFO, TAG,
            "Host ids: dock=$idDockPanel clear=$idClearPanel dots=$idDots"
        )
    }

    private fun hostId(name: String, type: String): Int = runCatching {
        hostContext.resources.getIdentifier(name, type, HOST_PACKAGE)
    }.getOrDefault(0)

    private fun findClearPanel(container: ViewGroup): View? =
        if (idClearPanel == 0) null else container.findViewById(idClearPanel)

    private fun isDark(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** Capsule rect on screen (for the N5 launch animation). The animation hook passes a TaskView. */
    fun capsuleRectOnScreen(taskView: View): android.graphics.Rect? {
        val taskId = SlimTaskSource.taskIdOfTaskView(taskView)
        val row = rows[taskId]?.container ?: return null
        if (row.width <= 0 || row.height <= 0 || !row.isAttachedToWindow) return null
        val location = IntArray(2)
        row.getLocationOnScreen(location)
        return android.graphics.Rect(
            location[0],
            location[1],
            location[0] + row.width,
            location[1] + row.height
        )
    }

    private companion object {
        const val TAG = "RealOS-SlimOverlay"
        const val HOST_PACKAGE = "com.android.launcher"
        const val TAG_ROOT = "realos_slim_overlay"
        const val ID_DOCK_PANEL = "dockview_panel"
        const val ID_CLEAR_PANEL = "oplus_clear_all_panel"
        const val ID_DOTS = "oplus_task_pop_menu_icon"

        const val REFRESH_INTERVAL_MS = 1200L

        const val CAPSULE_HEIGHT_DP = 56
        const val ROW_GAP_DP = 8
        const val SIDE_PADDING_DP = 16
        const val TOP_PADDING_DP = 12

        const val ICON_SIZE_DP = 30
        const val ICON_START_DP = 14
        const val TITLE_MARGIN_DP = 12
        const val TITLE_SIZE_SP = 15f
        const val DOTS_SIZE_DP = 24
        const val DOTS_END_DP = 10
    }
}

/**
 * Task data source: reads `RecentsModel` directly, not through TaskView.
 *
 * Chain (all reflected by field name; field names are not obfuscated):
 * ```
 * holder.<singleton>  : static singleton (found structurally: type carries get(Context))
 *   .get(context)     → RecentsModel
 *   .<taskList>       → RecentTasksList (found structurally: the only member with a List field)
 *   .<tasks>          → List<Task> (all tasks, newest first)
 * ```
 *
 * The task list has content only after one load completes, so an empty first read kicks one load with `m(0, null)`.
 */
internal object SlimTaskSource {

    private const val TAG = "RealOS-SlimTaskSource"

    /**
     * **Stable class name** of the host entry class (not an R8 field name).
     *
     * Only this one string is kept deliberately: R8 renames the `RecentsModel` static singleton field and
     * the `RecentTasksList` task list field to names like `f18184r` / `f17955a` / `f18008k`,
     * changing with every host upgrade; hardcoding them in business logic is forbidden by §3.1.
     * Fields are found structurally (see [resolveChain]); a failed discovery fails closed.
     */
    private const val MODEL_CLASS = "com.android.quickstep.db"

    /**
     * Reflection chain found structurally:
     * `holder.<singleton>.get(context)` → RecentsModel `<taskList>` → RecentTasksList `<tasks>: List`.
     */
    private class ModelChain(
        val singletonField: Field,
        val getMethod: Method,
        val taskListField: Field,
        val tasksField: Field
    )

    @Volatile
    private var chain: ModelChain? = null

    @Volatile
    private var chainFailureLogged = false

    @Volatile
    private var model: Any? = null

    @Volatile
    private var lastLoadKick = 0L

    /** All fields including superclasses (the host sometimes puts the singleton field in a parent). */
    private fun allFields(type: Class<*>): List<Field> {
        val out = ArrayList<Field>()
        var c: Class<*>? = type
        while (c != null && c != Any::class.java) {
            out += c.declaredFields
            c = c.superclass
        }
        return out
    }

    /**
     * Discovers the reflection chain structurally (no R8 field names).
     *
     * Criteria (all must hold, otherwise null → feature fails closed):
     *  1. holder has a **static** field whose type carries `get(Context)`, and its return type (RecentsModel)
     *     contains exactly **one** field that itself has a List field (RecentTasksList);
     *  2. that RecentTasksList type contains exactly **one** `List` field (the task array).
     * Any step with a non-unique candidate aborts; no guess-based fallback.
     */
    private fun resolveChain(classLoader: ClassLoader): ModelChain? {
        val holder = runCatching { Class.forName(MODEL_CLASS, false, classLoader) }.getOrNull() ?: return null

        val candidates = ArrayList<ModelChain>()
        for (field in allFields(holder)) {
            if (!Modifier.isStatic(field.modifiers)) continue
            if (field.type.isPrimitive || field.type == String::class.java) continue
            runCatching { field.isAccessible = true }

            val getMethod = runCatching {
                field.type.getMethod("get", Context::class.java)
            }.getOrNull() ?: continue
            val modelType = getMethod.returnType

            // Instance members of RecentsModel that are a List or contain a List field
            val listHolders = allFields(modelType).filter { f ->
                if (Modifier.isStatic(f.modifiers) || f.type.isPrimitive) {
                    false
                } else {
                    val direct = List::class.java.isAssignableFrom(f.type)
                    val nested = allFields(f.type).any { inner ->
                        !Modifier.isStatic(inner.modifiers) &&
                            List::class.java.isAssignableFrom(inner.type)
                    }
                    direct || nested
                }
            }
            if (listHolders.size != 1) continue
            val taskListField = listHolders.first()
            runCatching { taskListField.isAccessible = true }

            val tasksField = allFields(taskListField.type)
                .filter { !Modifier.isStatic(it.modifiers) && List::class.java.isAssignableFrom(it.type) }
                .singleOrNull() ?: continue
            runCatching { tasksField.isAccessible = true }

            candidates += ModelChain(field, getMethod, taskListField, tasksField)
        }

        return candidates.singleOrNull()
    }

    fun prime(context: Context, classLoader: ClassLoader) {
        if (model != null) return
        val resolved = chain ?: resolveChain(classLoader)?.also { chain = it }
        if (resolved == null) {
            if (!chainFailureLogged) {
                chainFailureLogged = true
                Log.w(
                    TAG,
                    "fail closed: RecentsModel discovery failed (the host version may have changed); the slim list task source is disabled"
                )
            }
            return
        }
        runCatching {
            val singleton = resolved.singletonField.get(null)
            model = resolved.getMethod.invoke(singleton, context.applicationContext)
        }.onFailure { Log.w(TAG, "Failed to obtain RecentsModel", it) }
    }

    /** All tasks, newest first; empty list when unreadable */
    fun tasks(context: Context, classLoader: ClassLoader): List<Any> {
        if (model == null) prime(context, classLoader)
        val m = model ?: return emptyList()
        val resolved = chain ?: return emptyList()

        val list = runCatching {
            val recentTasks = resolved.taskListField.get(m)
            val tasks = resolved.tasksField.get(recentTasks)
            @Suppress("UNCHECKED_CAST")
            (tasks as? List<Any>)?.toList().orEmpty()
        }.getOrDefault(emptyList())

        if (list.isEmpty()) kickLoad(m)
        return list
    }

    /** Explicit refresh (the host does not reload by itself after a task deletion) */
    fun refresh(context: Context, classLoader: ClassLoader) {
        if (model == null) prime(context, classLoader)
        val m = model ?: return
        lastLoadKick = 0L
        kickLoad(m)
    }

    /** Kicks one load when the first read is empty (`m(int, Consumer)` returns a changeId, ignored here) */
    private fun kickLoad(m: Any) {
        val now = System.currentTimeMillis()
        if (now - lastLoadKick < 1000) return
        lastLoadKick = now
        runCatching {
            m.javaClass
                .getMethod(
                    "m",
                    Int::class.javaPrimitiveType,
                    java.util.function.Consumer::class.java
                )
                .invoke(m, 0, java.util.function.Consumer<Any> { })
        }.onFailure { Log.w(TAG, "Failed to trigger the task load", it) }
    }

    fun taskIdOf(task: Any): Int = runCatching {
        val key = task.javaClass.getField("key").get(task) ?: return -1
        taskIdOfKey(key)
    }.getOrDefault(-1)

    fun taskIdOfTaskView(taskView: View): Int = runCatching {
        val task = taskView.javaClass.getMethod("getTask").invoke(taskView) ?: return -1
        taskIdOf(task)
    }.getOrDefault(-1)

    /** Uniquely reads the task id: only the host's stable field name `id`, else the single int field; no name guessing. */
    private fun taskIdOfKey(key: Any): Int {
        val intFields = allFields(key.javaClass).filter { it.type == Int::class.javaPrimitiveType }
        val field = intFields.firstOrNull { it.name == "id" }
            ?: intFields.singleOrNull()
            ?: return -1
        runCatching { field.isAccessible = true }
        return runCatching { field.getInt(key) }.getOrDefault(-1)
    }

    fun titleOf(task: Any): String? = runCatching {
        task.javaClass.getField("title").get(task) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun iconOf(task: Any): Drawable? = runCatching {
        task.javaClass.getField("icon").get(task) as? Drawable
    }.getOrNull()
}
