package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.content.Context
import android.graphics.drawable.Drawable
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import android.widget.AdapterView

/**
 * COUI single-choice popup, bound the same way as stock `COUIMenuPreference.onBindViewHolder`.
 *
 * Host widgets of the single-choice rows (system navigation -> full screen gestures ->
 * gesture mistouch prevention):
 * ```
 * com.coui.appcompat.poplist.COUIClickSelectMenu   // entry: binds a View and the popup
 * com.coui.appcompat.poplist.COUIPopupListWindow   // actual PopupWindow (anchor = our row)
 * com.coui.appcompat.poplist.PopupListItem$Builder // builds one item
 * ```
 *
 * ## `PopupListItem$Builder` is required for items
 * `com.coui.appcompat.poplist.PopupListItem` has different signatures in SystemUI and Settings:
 * | Build | 6-arg constructor |
 * |---|---|
 * | SystemUI | `(Drawable, String, boolean, `**`int`**`, boolean, int)` |
 * | Settings (the process of this page) | `(Drawable, String, boolean, `**`boolean`**`, int, boolean)` |
 *
 * The SystemUI reflection call throws `NoSuchMethodException` in the Settings process, and the
 * whole chain falls back. `PopupListItem$Builder` is `public static class` in both builds
 * (`setTitle`, `setId`, `setGroupId`, `setIsChecked`, `setIcon`, `setForceTint` and `build`
 * are public; verified on classes10.dex of Settings.apk, no `this$0`).
 *
 * ## Popup position and style
 * Stock `COUIMenuPreference.onBindViewHolder` configures the menu as follows:
 * ```java
 * mCouiClickSelectMenu = new COUIClickSelectMenu(getContext(), itemView);
 * mCouiClickSelectMenu.registerForClickSelectItems(itemView, items);
 * mCouiClickSelectMenu.setEnableAddExtraWidth(true);   // no-op in Settings
 * mCouiClickSelectMenu.setHelperEnabled(true);         // required
 * mCouiClickSelectMenu.setOnItemClickListener(listener);
 * mCouiClickSelectMenu.setMaxShowItemCount(-1);        // no-op in Settings
 * ```
 * `setHelperEnabled(true)` calls `PreciseClickHelper.setup()`, which replaces the anchor
 * `OnTouchListener` and `OnClickListener`; clicks then route through
 * `setPopupShow(view, touchX, touchY)` and the popup is anchored at the touch-down point.
 * `setPopupState(true)` instead calls the no-arg `show()` -> `show(mAnchorView)`, which places
 * the popup below the anchor. This class calls `setHelperEnabled(true)`; [showFallback] shows
 * the popup manually when the helper is absent.
 *
 * ## Item fields
 * Stock `setEntries` calls only `setTitle` and `setGroupId(-1)`.
 * `COUIPopupListWindow.setItemListInternal` inserts group/section rows when there are 3 or more
 * items with differing groupId, so all items use -1.
 * `itemType`: 0 = vertical item, 3 = section title, 4 = horizontal icon item, 2 = divider.
 *
 * ## Icons
 * `MixedPopupListAdapter.setIcon()` is enabled automatically when any item has
 * `getIcon() != null || getIconId() != 0`; the icon column is shown, otherwise `GONE`.
 * Tint: `(item.forceTint & 1) != 0` (default 7) colors the icon with the text color and the checked icon with the highlight color.
 * `Item.keepOriginalColor = true` calls `setForceTint(0)` and keeps the original icon colors.
 *
 * `setPopupState(true)` calls `mPopup.show(view, false)` in SystemUI and the no-arg `show()` (using `mAnchorView`) in Settings.
 *
 * All host access uses reflection inside `runCatching`; failures return without crashing the
 * settings page.
 */
class CouiSelectPopup(private val context: Context, private val anchor: View) {

    /** One candidate item. `icon == null` means no icon in this row; the icon column appears when any row has one. */
    data class Item(
        val title: String,
        val icon: Drawable? = null,
        /** true: calls `setForceTint(0)`; the icon keeps its original colors (multi-color images). */
        val keepOriginalColor: Boolean = false,
        /**
         * When non-null, the row uses a **custom view** (`PopupListItem.MENU_ITEM_TYPE_CUSTOM = 2`),
         * and [title], [icon] and [keepOriginalColor] are ignored.
         *
         * The adapter puts the view into a `FrameLayout` (`LayoutParams(MATCH_PARENT, WRAP_CONTENT)`)
         * and attaches `setOnClickListener` to it, so the selection callback works as usual.
         * Used for layouts the built-in items cannot express, such as several icons in one row.
         *
         * Each Item needs its own View instance; sharing one instance across items mixes up rows.
         * Reusing the same instance for the same Item across `bind()` calls is safe.
         */
        val customView: View? = null
    )

    private val boolType = Boolean::class.javaPrimitiveType!!
    private var menuCls: Class<*>? = null
    private var menu: Any? = null

    /** Whether a bind has succeeded; gates `showFallback`. */
    @Volatile
    private var bound = false

    /**
     * Binds the candidates. Stock binds once; later clicks on the row open the popup.
     * Safe to call again to refresh the checked state and icons.
     *
     * @return true when bound; false when the host control is unavailable.
     */
    fun bind(items: List<Item>, checkedIndex: Int, onPick: (Int) -> Unit): Boolean = runCatching {
        if (items.isEmpty()) return false

        val cls = menuCls ?: context.classLoader.loadClass(CLS_MENU).also { menuCls = it }
        val builderCls = context.classLoader.loadClass(CLS_BUILDER)

        val intType = Int::class.javaPrimitiveType!!
        val drawableType = Drawable::class.java

        val setTitle = builderCls.getMethod("setTitle", String::class.java)
        val setGroupId = builderCls.getMethod("setGroupId", intType)
        val setChecked = builderCls.getMethod("setIsChecked", boolType)
        val setIcon = builderCls.getMethod("setIcon", drawableType)
        val setForceTint = builderCls.getMethod("setForceTint", intType)
        val setItemType = builderCls.getMethod("setItemType", intType)
        val setCustomItemView = builderCls.getMethod("setCustomItemView", View::class.java)
        val build = builderCls.getMethod("build")
        val newBuilder = builderCls.getDeclaredConstructor()

        val list = ArrayList<Any>(items.size)
        for ((i, item) in items.withIndex()) {
            val b = newBuilder.newInstance()
            setGroupId.invoke(b, GROUP_ID_NONE)                 // -1 for every item: no group rows.
            setChecked.invoke(b, i == checkedIndex)
            if (item.customView != null) {
                // Custom view item; the adapter places it in FrameLayout(MATCH_PARENT, WRAP_CONTENT).
                setItemType.invoke(b, ITEM_TYPE_CUSTOM)
                setCustomItemView.invoke(b, item.customView)
                prepareCustomView(item.customView)
            } else {
                setTitle.invoke(b, item.title)
                if (item.icon != null) {
                    setIcon.invoke(b, item.icon)
                    if (item.keepOriginalColor) setForceTint.invoke(b, FORCE_TINT_NONE)
                }
            }
            list.add(build.invoke(b)!!)
        }

        val m = menu ?: newMenu(cls, context, anchor)?.also { menu = it } ?: return false

        // 1. Required part: items, selection callback and helper.
        cls.getMethod("registerForClickSelectItems", View::class.java, ArrayList::class.java)
            .invoke(m, anchor, list)

        val dismiss = cls.getMethod("dismiss")
        val listener = AdapterView.OnItemClickListener { _, _, position, _ ->
            runCatching { onPick(position) }
                .onFailure { Log.w(TAG, "onPick threw (swallowed)", it) }
            // Stock dismisses the popup after a selection.
            runCatching { dismiss.invoke(m) }
        }
        cls.getMethod("setOnItemClickListener", AdapterView.OnItemClickListener::class.java)
            .invoke(m, listener)

        // Stock step: PreciseClickHelper takes over touch and click; the popup anchors at touch-down.
        cls.getMethod("setHelperEnabled", boolType).invoke(m, true)

        bound = true

        // 2. Optional part: material. A failure here leaves the popup usable.
        applyMaterial(m, cls)

        true
    }.getOrElse {
        Log.w(TAG, "COUI single-choice popup binding failed: ${it.javaClass.simpleName}: ${it.message}")
        false
    }

    /**
     * Material: reads the system material blur switch and decides whether to arm blur.
     *
     * The method is not on `COUIClickSelectMenu`; it requires `getPopup()` to obtain the
     * `COUIPopupListWindow` and then `setUseBackgroundBlur(boolean)`.
     *
     * `COUIPopupListWindow` content root is a `RoundFrameLayout` (`mMainMenuWrapper`).
     * Without arm blur there is no material effect; outline and spotlight are then toggled by
     * `COUIMaterialStateManager` from `system_material_stroke_enable`
     * (`RoundFrameLayout.updateMaterialEffectsState()`).
     */
    private fun applyMaterial(menuObj: Any, cls: Class<*>) {
        if (!isSystemMaterialBlurEnabled()) return
        runCatching {
            val popup = cls.getMethod("getPopup").invoke(menuObj) ?: return
            popup.javaClass.getMethod("setUseBackgroundBlur", boolType).invoke(popup, true)
        }.onFailure {
            Log.w(TAG, "setUseBackgroundBlur unavailable (skipping the material): ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    /**
     * Prepares a custom item view.
     *
     * The adapter attaches the click listener to the CustomViewHolder container
     * (`viewHolder.itemView`), not to the custom view. A clickable custom view consumes the tap
     * in `ViewGroup.dispatchTouchEvent` and the single tap does nothing.
     *
     * Press and drag to the target still works: `COUITouchRecyclerView` shared-mask drag selection
     * calls `view.performClick()` on the container at touch-up, which skips touch dispatch.
     */
    private fun prepareCustomView(customView: View) {
        customView.isClickable = false
        customView.isFocusable = false
        // The adapter adds a press background (configStateEffectBackground) to built-in items
        // only; the ripple is added to the container after attach.
        customView.addOnAttachStateChangeListener(rippleToParent)
    }

    /** Adds the ripple to the adapter container once the custom item is attached. */
    private val rippleToParent = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            val parent = v.parent as? View ?: return
            if (parent.background == null) CouiComponentFactory.applyStockPressRipple(parent)
        }

        override fun onViewDetachedFromWindow(v: View) = Unit
    }

    /**
     * Fallback: shows the popup manually when `setHelperEnabled` did not replace the anchor
     * onClick. The normal path is handled by the helper.
     */
    fun showFallback(): Boolean {
        val m = menu ?: return false
        if (!bound) return false
        return runCatching {
            menuCls!!.getMethod("setPopupState", boolType).invoke(m, true)
            true
        }.getOrDefault(false)
    }

    fun dismiss() {
        val m = menu ?: return
        runCatching { menuCls!!.getMethod("dismiss").invoke(m) }
    }

    /**
     * System material blur switch, the same key read by stock `COUIMaterialStateManager`:
     * `Settings.System["system_material_blur_enable"]` (int 0/1).
     *
     * Only this key gates the blur arm.
     */
    private fun isSystemMaterialBlurEnabled(): Boolean = runCatching {
        android.provider.Settings.System.getInt(
            context.contentResolver, KEY_MATERIAL_BLUR, 0
        ) == 1
    }.getOrDefault(false)

    /** Version-tolerant constructor: `(Context, View)` first, then `(Context)`. */
    private fun newMenu(cls: Class<*>, context: Context, anchor: View): Any? {
        runCatching {
            cls.getConstructor(Context::class.java, View::class.java)
                .newInstance(context, anchor)
        }.getOrNull()?.let { return it }
        return runCatching {
            cls.getConstructor(Context::class.java).newInstance(context)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "RealOS-CouiSelectPopup"

        private const val CLS_MENU = "com.coui.appcompat.poplist.COUIClickSelectMenu"
        private const val CLS_BUILDER = "com.coui.appcompat.poplist.PopupListItem\$Builder"

        /** System material blur switch (`COUIMaterialStateManager.MATERIAL_BLUR`). */
        private const val KEY_MATERIAL_BLUR = "system_material_blur_enable"

        /** groupId used by stock `setEntries`; identical values produce no section rows. */
        private const val GROUP_ID_NONE = -1

        /** `PopupListItem.MENU_ITEM_TYPE_CUSTOM`; rows built from [Item.customView]. */
        private const val ITEM_TYPE_CUSTOM = 2

        /** `forceTint` = 0 disables icon tinting. */
        private const val FORCE_TINT_NONE = 0
    }
}
