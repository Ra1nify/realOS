package io.github.ra1nify.realos.xposed.hooks.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.camera.CameraIntentContract
import io.github.ra1nify.realos.camera.OplusCameraConfig
import java.lang.reflect.Modifier

/**
 * Injects a realOS tab into the camera settings top bar.
 *
 * The host's three tabs are driven by ViewPager2 + FragmentStateAdapter in the tab host Fragment:
 * page count comes from the host kind list, and `createFragment(position)` writes `arg_setting_page_tab`
 * into the Fragment. The host layout is not modified; four things happen:
 *
 * 1. after `ViewPager2.setAdapter`, the adapter is bound and [PAGE_ID] is appended to the host kind list;
 * 2. the adapter's count / itemId / containsItem / createFragment are rewritten so the extra page persists
 *    (the host clears the kind list in the photo/video availability callback; appending alone does not survive it);
 * 3. the `setSegmentButtons` label array is rewritten with the tab text;
 * 4. once our page's Fragment finishes `onViewCreated`, its content is replaced with realOS content.
 *
 * Note: **androidx application-layer types are not used for identity checks in the host process**.
 * `androidx.fragment.app.Fragment` in the module class loader differs from the host's same-named class;
 * `isAssignableFrom` and `as? Fragment` both fail, so Fragment operations go through reflection + Any.
 */
object OplusCameraTabHook {

    private const val TAG = "RealOS-CameraTab"

    /** realOS tab id, outside the host's 0/1/2. */
    const val PAGE_ID = 1000

    private const val ARG_PAGE_TAB = "arg_setting_page_tab"
    private const val ARG_HIDE_ACTION_BAR = "key_hide_actionbar"
    private const val TAG_CONTENT = "realos_camera_tab_content"

    private const val VIEW_PAGER_CLASS = "androidx.viewpager2.widget.ViewPager2"
    private const val RECYCLER_ADAPTER_CLASS = "androidx.recyclerview.widget.RecyclerView\$Adapter"
    private const val SEGMENT_LAYOUT_CLASS = "com.coui.appcompat.segmentbutton.COUISegmentButtonLayout"

    /** Host tab rebuilds reset the selected page; two idempotent corrections follow. */
    private const val SELECT_DELAY_MS = 120L
    private const val SELECT_RETRY_MS = 600L

    private var module: XposedModule? = null

    @Volatile
    private var enabled = false

    /** Host kind list of the current settings page (only one settings page at a time). */
    @Volatile
    private var tabList: ArrayList<Any?>? = null

    @Volatile
    private var adapterHooked = false

    @Volatile
    private var fragmentClassHooked = false

    /** Field where COUISegmentButtonLayout caches the previous label array */
    @Volatile
    private var labelCacheField: java.lang.reflect.Field? = null

    fun install(module: XposedModule, classLoader: ClassLoader) {
        this.module = module
        hookPagerAdapter(module, classLoader)
        hookSegmentLabels(module, classLoader)
    }

    // ── 1. Bind pager adapter ─────────────────────────────────────────

    private fun hookPagerAdapter(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val pagerClass = classLoader.loadClass(VIEW_PAGER_CLASS)
            val adapterClass = classLoader.loadClass(RECYCLER_ADAPTER_CLASS)
            val setAdapter = pagerClass.getMethod("setAdapter", adapterClass)
            module.hook(setAdapter).intercept { chain ->
                val result = chain.proceed()
                val pager = chain.thisObject
                val adapter = chain.args.getOrNull(0)
                if (pager != null && adapter != null) {
                    runCatching { onAdapterBound(module, pager, adapter) }
                        .onFailure { module.log(Log.WARN, TAG, "onAdapterBound failed", it) }
                }
                result
            }
            module.log(Log.INFO, TAG, "HookPoint[camera_tab_pager] installed")
        }.onFailure {
            module.log(Log.WARN, TAG, "HookPoint[camera_tab_pager] state=ERROR", it)
        }
    }

    private fun onAdapterBound(module: XposedModule, pager: Any, adapter: Any) {
        enabled = OplusCameraConfig.isRealosTabEnabled(module)
        if (!enabled) {
            tabList = null
            module.log(Log.INFO, TAG, "realOS tab disabled, host pager left untouched")
            return
        }

        val list = findTabList(adapter)
        if (list == null) {
            tabList = null
            module.log(Log.WARN, TAG, "HookPoint[camera_tab_list] state=MISSING")
            return
        }
        tabList = list
        if (!list.contains(PAGE_ID)) list.add(PAGE_ID)

        hookAdapterMembers(module, adapter.javaClass)
        if (launchedForRealosTab(pager)) scheduleSelect(pager)
        module.log(Log.INFO, TAG, "HookPoint[camera_tab_pager] bound, host tabs=$list")
    }

    /**
     * Locates the host kind list structurally: the adapter holds the tab host Fragment,
     * whose `ArrayList<Integer>` starts with 0 and contains only ints.
     */
    private fun findTabList(adapter: Any): ArrayList<Any?>? {
        var clazz: Class<*>? = adapter.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (field in clazz.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                if (!field.type.name.startsWith("com.oplus.")) continue
                field.isAccessible = true
                val host = runCatching { field.get(adapter) }.getOrNull() ?: continue
                var hostClass: Class<*>? = host.javaClass
                while (hostClass != null && hostClass != Any::class.java) {
                    for (hostField in hostClass.declaredFields) {
                        if (hostField.type != java.util.ArrayList::class.java) continue
                        hostField.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        val candidate = runCatching { hostField.get(host) }.getOrNull() as? ArrayList<Any?>
                            ?: continue
                        if (looksLikeTabList(candidate)) return candidate
                    }
                    hostClass = hostClass.superclass
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun looksLikeTabList(list: List<Any?>): Boolean {
        if (list.isEmpty() || list.size > 8) return false
        if (list.any { it !is Int }) return false
        return list[0] == 0
    }

    // ── 2. Rewrite adapter members ────────────────────────────────────

    private fun hookAdapterMembers(module: XposedModule, adapterClass: Class<*>) {
        if (adapterHooked) return
        synchronized(this) {
            if (adapterHooked) return

            val count = adapterClass.declaredMethods.firstOrNull {
                it.parameterCount == 0 && it.returnType == Int::class.javaPrimitiveType
            }
            val itemId = adapterClass.declaredMethods.firstOrNull {
                it.parameterCount == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    it.returnType == java.lang.Long.TYPE
            }
            val containsItem = adapterClass.declaredMethods.firstOrNull {
                it.parameterCount == 1 &&
                    it.parameterTypes[0] == java.lang.Long.TYPE &&
                    it.returnType == java.lang.Boolean.TYPE
            }
            // createFragment(int) -> Fragment. Fragment::class must not be used for identity here:
            // the module's and the host's androidx.fragment are not the same Class.
            val createFragment = adapterClass.declaredMethods.firstOrNull {
                it.parameterCount == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    !it.returnType.isPrimitive
            }

            count?.let { method ->
                module.hook(method).intercept { chain ->
                    val list = tabList
                    if (!enabled || list == null) {
                        chain.proceed()
                    } else {
                        if (list.contains(PAGE_ID)) list.size else list.size + 1
                    }
                }
            }

            itemId?.let { method ->
                module.hook(method).intercept { chain ->
                    val list = tabList
                    val position = chain.args.getOrNull(0) as? Int
                    if (!enabled || list == null || position == null || position < list.size) {
                        chain.proceed()
                    } else {
                        PAGE_ID.toLong()
                    }
                }
            }

            containsItem?.let { method ->
                module.hook(method).intercept { chain ->
                    val id = chain.args.getOrNull(0) as? Long
                    if (enabled && id == PAGE_ID.toLong()) true else chain.proceed()
                }
            }

            createFragment?.let { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (!enabled) return@intercept result
                    val fragment = result ?: return@intercept result
                    val list = tabList
                    val position = chain.args.getOrNull(0) as? Int
                    if (list != null && position != null && position >= list.size) {
                        retagAsRealosPage(fragment)
                    } else {
                        // The retag path is skipped when the host kind list already contains our id; the content Hook is installed here
                        hookFragmentClass(fragment.javaClass)
                    }
                    module.log(
                        Log.INFO, TAG,
                        "createFragment pos=$position tabs=$list class=${fragment.javaClass.name} " +
                            "arg=${fragmentArguments(fragment)?.getInt(ARG_PAGE_TAB, -1)}"
                    )
                    result
                }
            }

            adapterHooked = true
            module.log(
                Log.INFO, TAG,
                "Adapter members hooked: count=${count != null}, itemId=${itemId != null}, " +
                    "containsItem=${containsItem != null}, createFragment=${createFragment != null}" +
                    (createFragment?.let { " (${it.name} -> ${it.returnType.name})" } ?: "")
            )
            if (count == null || itemId == null || containsItem == null || createFragment == null) {
                module.log(Log.WARN, TAG, "HookPoint[camera_tab_adapter] state=PARTIAL")
            }
        }
    }

    // ── 3. Tab labels ─────────────────────────────────────────────────

    private fun hookSegmentLabels(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val segmentClass = classLoader.loadClass(SEGMENT_LAYOUT_CLASS)
            val setSegmentButtons = segmentClass.declaredMethods.firstOrNull {
                it.parameterCount == 1 && it.parameterTypes[0] == Array<String>::class.java
            }
            if (setSegmentButtons == null) {
                module.log(Log.WARN, TAG, "HookPoint[camera_tab_label] state=MISSING")
                return
            }
            module.hook(setSegmentButtons).intercept { chain ->
                val labels = chain.args.getOrNull(0) as? Array<*>
                val list = tabList
                if (!enabled || list == null || labels == null) {
                    chain.proceed()
                } else {
                    val extended = extendLabels(labels, list)
                    if (extended != null) {
                        invalidateLabelCache(chain.thisObject)
                        chain.proceed(arrayOf<Any?>(extended))
                    } else {
                        chain.proceed()
                    }
                }
            }
            module.log(Log.INFO, TAG, "HookPoint[camera_tab_label] installed")
        }.onFailure {
            module.log(Log.WARN, TAG, "HookPoint[camera_tab_label] state=ERROR", it)
        }
    }

    /** The host label array follows the kind list; our slot is replaced with realOS. */
    private fun extendLabels(labels: Array<*>, list: ArrayList<Any?>): Array<String>? {
        val containsOurs = list.contains(PAGE_ID)
        val pageCount = if (containsOurs) list.size else list.size + 1
        if (labels.size > pageCount) return null

        val slot = if (containsOurs) list.indexOf(PAGE_ID) else list.size
        if (slot < 0 || slot >= pageCount) return null

        val out = arrayOfNulls<String>(pageCount)
        for (i in labels.indices) out[i] = labels[i] as? String
        out[slot] = OplusCameraTabText.TAB_LABEL
        @Suppress("UNCHECKED_CAST")
        return out as Array<String>
    }

    /**
     * Clears the label array COUISegmentButtonLayout cached (its String[] field, located structurally).
     *
     * Host `k()` calls `removeAllViews()` before `setSegmentButtons`, and the latter returns early
     * on `Arrays.equals(cached, new)`; a stale cache yields
     * "buttons cleared and never rebuilt" — the tab title disappears and clicks stop working.
     */
    private fun invalidateLabelCache(target: Any?) {
        if (target == null) return
        val field = labelCacheField ?: runCatching {
            target.javaClass.declaredFields.firstOrNull { it.type == Array<String>::class.java }
        }.getOrNull()?.apply { isAccessible = true }?.also { labelCacheField = it }
        runCatching { field?.set(target, null) }
    }

    // ── 4. Tab content ─────────────────────────────────────────────────

    private fun retagAsRealosPage(fragment: Any) {
        val args = Bundle(fragmentArguments(fragment) ?: Bundle())
        args.putInt(ARG_PAGE_TAB, PAGE_ID)
        args.putBoolean(ARG_HIDE_ACTION_BAR, true)
        runCatching {
            fragment.javaClass.getMethod("setArguments", Bundle::class.java).invoke(fragment, args)
        }
        hookFragmentClass(fragment.javaClass)
    }

    private fun hookFragmentClass(fragmentClass: Class<*>) {
        if (fragmentClassHooked) return
        val host = module ?: return
        synchronized(this) {
            if (fragmentClassHooked) return
            val onViewCreated = runCatching {
                fragmentClass.getDeclaredMethod("onViewCreated", View::class.java, Bundle::class.java)
            }.getOrNull()
            if (onViewCreated == null) {
                host.log(Log.WARN, TAG, "HookPoint[camera_tab_content] state=MISSING on ${fragmentClass.name}")
                return
            }
            host.hook(onViewCreated).intercept { chain ->
                val result = chain.proceed()
                val fragment = chain.thisObject
                if (fragment != null && fragmentArguments(fragment)?.getInt(ARG_PAGE_TAB, -1) == PAGE_ID) {
                    runCatching { attachTabContent(fragment) }
                        .onFailure {
                            host.log(
                                Log.WARN, TAG,
                                "attachTabContent failed: ${it.javaClass.simpleName}: ${it.message}", it
                            )
                        }
                }
                result
            }
            fragmentClassHooked = true
            host.log(Log.INFO, TAG, "HookPoint[camera_tab_content] installed on ${fragmentClass.name}")
        }
    }

    /**
     * Top-bar placeholder height.
     *
     * Host `CameraSettingActivityFragment.M(paddingTop)` writes the toolbar + segment bar height into
     * **its own RecyclerView**'s paddingTop (a host-defined id, not `android:id/list`).
     * That padding needs no measurement and is preferred; otherwise the measured top-bar height is used.
     */
    private fun resolveTopInset(root: ViewGroup): Int {
        val listPadding = maxDescendantPaddingTop(root)
        if (listPadding > 0) return listPadding

        val activity = activityOf(root.context) ?: return 0
        val res = activity.resources
        val pkg = activity.packageName
        // blur_overlay covers the toolbar + segment bar + spacing; the appbar's own height is the fallback
        return listOf("blur_overlay", "setting_appbar").firstNotNullOfOrNull { name ->
            val id = runCatching { res.getIdentifier(name, "id", pkg) }.getOrDefault(0)
            if (id == 0) return@firstNotNullOfOrNull null
            val view = activity.findViewById<View>(id) ?: return@firstNotNullOfOrNull null
            (if (view.height > 0) view.height else view.measuredHeight).takeIf { it > 0 }
        } ?: 0
    }

    /** The host sets paddingTop only on its own list; the largest value in the subtree is taken here */
    private fun maxDescendantPaddingTop(group: ViewGroup): Int {
        var best = 0
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child.paddingTop > best) best = child.paddingTop
            if (child is ViewGroup) {
                val nested = maxDescendantPaddingTop(child)
                if (nested > best) best = nested
            }
        }
        return best
    }

    private fun attachTabContent(fragment: Any) {
        val root = fragmentView(fragment) as? ViewGroup
        if (root == null) {
            module?.log(Log.WARN, TAG, "attachTabContent skipped: view=${fragmentView(fragment)?.javaClass?.name}")
            return
        }
        if (root.findViewWithTag<View>(TAG_CONTENT) != null) return

        // ComposeView does not consume its own padding (children are always layout(0,0,w,h));
        // the top-bar placeholder enters Compose as content padding. A State holds the value:
        // the top bar may finish measuring after onViewCreated and is re-read after layout.
        val topInset = mutableStateOf(resolveTopInset(root))

        for (i in 0 until root.childCount) {
            root.getChildAt(i).visibility = View.GONE
        }
        val content = createCameraTabContent(root, topInset)
        content.tag = TAG_CONTENT
        root.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        content.post {
            val measured = resolveTopInset(root)
            if (measured > 0 && measured != topInset.value) topInset.value = measured
            module?.log(
                Log.INFO, TAG,
                "HookPoint[camera_tab_content] attached for page=$PAGE_ID inset=${topInset.value}px"
            )
        }
    }

    // ── Fragment reflection (host and module types differ; reflection only) ──────────

    private fun fragmentArguments(fragment: Any): Bundle? = runCatching {
        fragment.javaClass.getMethod("getArguments").invoke(fragment) as? Bundle
    }.getOrNull()

    private fun fragmentView(fragment: Any): View? = runCatching {
        fragment.javaClass.getMethod("getView").invoke(fragment) as? View
    }.getOrNull()

    // ── 5. Auto-switch page after launch ──────────────────────────────

    private fun launchedForRealosTab(pager: Any): Boolean = runCatching {
        val intent = activityOf(pager)?.intent ?: return false
        if (intent.getBooleanExtra(CameraIntentContract.EXTRA_OPEN_REALOS_TAB, false)) return true
        intent.getBundleExtra(CameraIntentContract.INTENT_DATA_KEY)
            ?.getBoolean(CameraIntentContract.EXTRA_OPEN_REALOS_TAB, false) == true
    }.getOrDefault(false)

    private fun activityOf(pager: Any): Activity? {
        var context: Context? = (pager as? View)?.context
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return context as? Activity
    }

    /**
     * The host rebuilds the kind list and resets selection to 0 in the photo/video availability
     * callback; two idempotent corrections follow (no loop, no long-running main-thread work).
     */
    private fun scheduleSelect(pager: Any) {
        val view = pager as? View ?: return
        view.postDelayed({ selectRealosPage(pager) }, SELECT_DELAY_MS)
        view.postDelayed({ selectRealosPage(pager) }, SELECT_RETRY_MS)
    }

    private fun selectRealosPage(pager: Any) {
        runCatching {
            val list = tabList ?: return
            if (!enabled) return
            val position = if (list.contains(PAGE_ID)) list.indexOf(PAGE_ID) else list.size
            if (position < 0) return
            val method = pager.javaClass.getMethod(
                "setCurrentItem",
                Int::class.javaPrimitiveType,
                java.lang.Boolean.TYPE
            )
            method.invoke(pager, position, false)
        }
    }
}
