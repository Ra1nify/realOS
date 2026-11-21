package io.github.ra1nify.realos.xposed.hooks.launcher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.xposed.hooks.launcher.ui.LauncherComposeHost
import io.github.ra1nify.realos.xposed.hooks.launcher.recents.RecentsStyleRegistry
import io.github.ra1nify.realos.xposed.hooks.launcher.recents.RecentsStyleText
import io.github.ra1nify.realos.xposed.hooks.launcher.ui.PickerState
import io.github.ra1nify.realos.xposed.hooks.launcher.ui.RecentsStyleOption
import io.github.ra1nify.realos.xposed.hooks.launcher.ui.RecentsStylePicker
import java.util.WeakHashMap

/**
 * Recent task style picker replacement (PoC).
 *
 * The host binds `RecentStyleLayoutPreference`: a horizontal LinearLayout with two equal-width
 * children (Lottie preview + text + RadioButton), which fits two items and cannot scroll.
 *
 * After `onBindViewHolder` this hides the host layout and adds a Compose HorizontalPager;
 * previews reuse the host Lottie views.
 */
object LauncherRecentsStyleHook {

    private const val TAG = "RealOS-RecentsStyle"

    private const val HOST_PACKAGE = "com.android.launcher"
    private const val HOST_LAYOUT_ID_NAME = "recent_style_layout"
    private const val SECURE_KEY_RECENT_STYLE = "key_recent_style"
    private const val COMPOSE_TAG = "realos_recents_style_pager"

    /** Pager height in dp; the host item is about 324dp, this leaves vertical breathing room */
    private const val PAGER_HEIGHT_DP = 300

    /**
     * The two host layouts. Previews reference the view id names from the host layout and
     * reuse the Lottie views inflated by the host, which already carry the lottie_autoPlay /
     * lottie_loop attributes; playback runs from those attributes, without reflection.
     */
    private val HOST_OPTIONS = listOf(
        RecentsStyleOption(
            id = "stacked",
            label = RecentsStyleText::stacked,
            animBrightId = "img_stacked_bright",
            animDarkId = "img_stacked_dark"
        ),
        RecentsStyleOption(
            id = "tiled",
            label = RecentsStyleText::tiled,
            animBrightId = "img_tiled_bright",
            animDarkId = "img_tiled_dark"
        )
    )

    /**
     * Options shown by the picker: host styles plus registered plugin styles.
     *
     * Plugins contribute metadata only (id / label / preview) and stay uninstantiated here;
     * instantiation happens when the user selects one and the gate first matches it.
     */
    private fun buildOptions(): List<RecentsStyleOption> =
        HOST_OPTIONS + RecentsStyleRegistry.all().map { descriptor ->
            RecentsStyleOption(
                id = descriptor.id,
                label = descriptor.label,
                customPreview = descriptor.preview
            )
        }

    /** itemView to picker state (RecyclerView reuse hits the same instance) */
    private val stateMap = WeakHashMap<View, PickerState>()

    fun install(module: XposedModule, symbols: LauncherHandles) {
        val method = symbols.recentStyleOnBindViewHolderMethod ?: run {
            module.log(
                Log.WARN, TAG,
                "fail closed: RecentStyleLayoutPreference#onBindViewHolder not resolved; skipping the style selector rework"
            )
            return
        }

        runCatching {
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                runCatching {
                    inject(module, chain.thisObject, chain.args.getOrNull(0))
                }.onFailure { module.log(Log.WARN, TAG, "Failed to inject the style selector", it) }
                result
            }
            module.log(Log.INFO, TAG, "Installed RecentStyleLayoutPreference#onBindViewHolder")
        }.onFailure {
            module.log(Log.ERROR, TAG, "fail closed: style selector hook install failed", it)
        }
    }

    private fun inject(module: XposedModule, preference: Any?, holder: Any?) {
        if (preference == null || holder == null) return

        val itemView = readField(holder, "itemView") as? ViewGroup ?: return
        val context = itemView.context ?: return
        val hostLayoutId = context.resources.getIdentifier(HOST_LAYOUT_ID_NAME, "id", HOST_PACKAGE)

        val activity = context.findActivity() ?: run {
            module.log(Log.WARN, TAG, "No Activity available; skipping the injection")
            return
        }

        // 1. Sync the selected state
        val options = buildOptions()
        val current = runCatching {
            Settings.Secure.getString(context.contentResolver, SECURE_KEY_RECENT_STYLE)
        }.getOrNull() ?: HOST_OPTIONS.first().id

        val state = stateMap.getOrPut(itemView) { PickerState(current) }
        state.selectedId = current

        // 2. Shows the existing view without creating another composition
        val existing = itemView.findViewWithTag<View>(COMPOSE_TAG)
        if (existing != null) {
            existing.visibility = View.VISIBLE
            if (hostLayoutId != 0) {
                itemView.findViewById<View>(hostLayoutId)?.visibility = View.GONE
            }
            return
        }

        // 3. Creates the Compose pager, then hides the host layout;
        //    a failed Compose initialization keeps the host layout visible
        val heightPx = (PAGER_HEIGHT_DP * context.resources.displayMetrics.density).toInt()
        val composeView = LauncherComposeHost.create(activity, module) {
            RecentsStylePicker(
                options = options,
                state = state,
                onSelect = { id -> writeStyle(context, id) }
            )
        }
        composeView.tag = COMPOSE_TAG
        itemView.addView(
            composeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
        )

        if (hostLayoutId != 0) {
            itemView.findViewById<View>(hostLayoutId)?.visibility = View.GONE
        }

        module.log(Log.INFO, TAG, "Injected the Compose style selector (${options.size} pages, current=$current)")
    }

    private fun writeStyle(context: Context, id: String) {
        runCatching {
            Settings.Secure.putString(context.contentResolver, SECURE_KEY_RECENT_STYLE, id)
            Log.i(TAG, "Switching the recents style -> $id")
        }.onFailure { Log.w(TAG, "Failed to write key_recent_style", it) }
    }

    private fun readField(target: Any, name: String): Any? = runCatching {
        target.javaClass.getField(name).get(target)
    }.getOrNull()

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
