package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.OplusInputSymbols
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.sound.OplusKeyboardSoundManager
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs the isolated touch hook that attaches and drives KeyboardEffectsDrawable via ViewOverlay.
 *
 * - Transient effects draw on ViewOverlay; key motion redraws the host keyboard buffer.
 * - Board Blending triggers on every touch.
 * - Key Blending triggers only in character input mode (non-emoji, fixed container).
 * - Any exception leaves the original IME touch handling untouched (fail-closed).
 */
object OplusInputEffectsHook {

    private const val TAG = "OplusInputEffects"
    private val installed = AtomicBoolean(false)
    private val overlayMap = Collections.synchronizedMap(WeakHashMap<View, KeyboardEffectsDrawable>())
    private val drawingDrawable = ThreadLocal<KeyboardEffectsDrawable?>()
    private val shadowReentry = ThreadLocal<Boolean>()
    private val shadowBuffers = ThreadLocal<ShadowBuffers>()
    private val drawingActiveKeyOnScreen = ThreadLocal<Boolean>()
    private val currentKeyboardView = ThreadLocal<View>()
    private var perKeyDrawMethodRef: Method? = null
    private var motionShadowHooksInstalled = false
    private val originalClipChildren = Collections.synchronizedMap(WeakHashMap<ViewGroup, Boolean>())
    private val originalClipToPadding = Collections.synchronizedMap(WeakHashMap<ViewGroup, Boolean>())

    /**
     * Cached "should ancestor clip be released" flag.
     *
     * [syncHierarchyClipping] recomputes it only on gesture down (or reset); the other frames
     * read this value. A recompute reads one cross-process remote preference (inside
     * `isEffectsEnabled`) plus 4 SharedPreferences reads, which at 60fps would run per frame.
     */
    @Volatile
    private var clipUnclipDesired: Boolean? = null

    private fun shouldUnclip(context: Context, recompute: Boolean = false): Boolean {
        if (recompute) clipUnclipDesired = null
        clipUnclipDesired?.let { return it }
        val isEffectsOn = OplusInputPrefs.isEffectsEnabled(context)
        val motionType = if (isEffectsOn) {
            MotionEffectType.fromKey(OplusInputPrefs.getEffectsMotion(context))
        } else {
            MotionEffectType.NONE
        }
        val desired = isEffectsOn && motionType != MotionEffectType.NONE
        clipUnclipDesired = desired
        return desired
    }

    /**
     * Recursively adapts parent ViewGroup clipping boundaries for Key Motion.
     *
     * When Key Motion is active (motion != NONE), first-row keys and their native shadows/glows
     * translate upward into negative Y space (above keyboardView bounds). Standard Android
     * ViewGroups enforce clipChildren = true, which truncates any content at Y = 0.
     *
     * This method recursively disables clipChildren and clipToPadding up to the root window view
     * when Key Motion is active, and cleanly restores original properties when Key Motion is NONE.
     */
    private fun syncHierarchyClipping(keyboardView: View, recompute: Boolean = false) {
        val context = keyboardView.context ?: return
        val shouldUnclip = shouldUnclip(context, recompute)

        if (shouldUnclip) {
            var current: View? = keyboardView
            val rootView = keyboardView.rootView
            while (current != null) {
                val parent = current.parent as? ViewGroup ?: break
                if (!originalClipChildren.containsKey(parent)) {
                    originalClipChildren[parent] = parent.clipChildren
                }
                if (!originalClipToPadding.containsKey(parent)) {
                    originalClipToPadding[parent] = parent.clipToPadding
                }
                if (parent.clipChildren) {
                    parent.clipChildren = false
                }
                if (parent.clipToPadding) {
                    parent.clipToPadding = false
                }
                if (parent === rootView) break
                current = parent
            }
        } else if (originalClipChildren.isNotEmpty()) {
            synchronized(originalClipChildren) {
                for ((parent, orig) in originalClipChildren) {
                    if (parent.clipChildren != orig) {
                        parent.clipChildren = orig
                    }
                }
                originalClipChildren.clear()
            }
            synchronized(originalClipToPadding) {
                for ((parent, orig) in originalClipToPadding) {
                    if (parent.clipToPadding != orig) {
                        parent.clipToPadding = orig
                    }
                }
                originalClipToPadding.clear()
            }
        }
    }

    private class ShadowBuffers {
        val inactive = ArrayList<Any?>()
        val active = ArrayList<Any?>()
        val single = ArrayList<Any?>(1)
    }

    fun install(module: XposedModule, app: Application, symbols: OplusInputSymbols) {
        if (!symbols.isReadyForEffects()) {
            Log.w(TAG, "Effects requested but symbols are not ready; failing closed.")
            return
        }
        if (!installed.compareAndSet(false, true)) {
            return
        }

        val touchMethod = symbols.keyboardTouchMethod?.restore(app.classLoader)
        if (touchMethod == null) {
            installed.set(false)
            Log.w(TAG, "Effects touch method restoration failed; failing closed.")
            return
        }

        val touchFailureLogged = AtomicBoolean(false)
        val drawFailureLogged = AtomicBoolean(false)

        // Per-key key-face provider: built once at install time (reflection restore included);
        // a failure disables only key blending.
        val perKeyProvider = try {
            OplusPerKeyBoundsProvider.create(app.classLoader, symbols)
        } catch (t: Throwable) {
            Log.w(TAG, "Per-key bounds provider creation failed; key blending will fail closed.", t)
            null
        }

        // Preloads the key mechanical sound style with async decoding.
        runCatching {
            val styleKey = OplusInputPrefs.getKeyboardSoundStyle(app)
            val style = io.github.ra1nify.realos.xposed.hooks.opluskeyboard.sound.KeyboardSoundStyle.fromKey(styleKey)
            OplusKeyboardSoundManager.ensureLoaded(app, style)
        }

        try {
            module.hook(touchMethod).intercept { chain ->
                try {
                    val view = chain.thisObject as? View
                    val event = chain.args.getOrNull(0) as? MotionEvent
                    if (view != null && event != null) {
                        val action = event.actionMasked
                        // Short-circuits MOVE events, which repeat continuously during touch drift
                        if (action == MotionEvent.ACTION_MOVE) {
                            return@intercept chain.proceed()
                        }

                        // Recomputes "should ancestor clip be released" only on gesture down;
                        // the other events read the cached value instead of the settings.
                        syncHierarchyClipping(view, recompute = action == MotionEvent.ACTION_DOWN)

                        val context = view.context
                        val isEffectsOn = OplusInputPrefs.isEffectsEnabled(context)
                        val isSoundOn = OplusInputPrefs.isKeyboardSoundEnabled(context)

                        // Releases the SoundPool while sound is off. release() has no other call
                        // site in the repo; the decoded audio and SoundPool would otherwise stay
                        // resident. release() is idempotent and runs outside the sound branch.
                        if (!isSoundOn) {
                            OplusKeyboardSoundManager.release()
                        }

                        if (isEffectsOn || isSoundOn) {
                            val isCharMode = isCharacterInputMode(view, event)

                            // 1. Keyboard light effect dispatch
                            if (isEffectsOn) {
                                val targetView = view.rootView ?: view
                                val drawable = getOrAttachOverlayDrawable(targetView, perKeyProvider)
                                drawable.onTouch(view, event, isCharMode)
                            }

                            // 2. Keyboard mechanical sound dispatch (ACTION_DOWN / ACTION_POINTER_DOWN only)
                            if (isSoundOn && isCharMode) {
                                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                                    val index = event.actionIndex
                                    val localX = event.getX(index)
                                    val localY = event.getY(index)
                                    OplusKeyboardSoundManager.onKeyDown(view, localX, localY, perKeyProvider)
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    if (touchFailureLogged.compareAndSet(false, true)) {
                        Log.w(TAG, "Touch effect/sound update failed; preserving original input.", t)
                    }
                }
                chain.proceed()
            }

            Log.i(
                TAG,
                "Installed Overlay-based Effects Hook on ${symbols.keyboardTouchMethod.className}." +
                    "${symbols.keyboardTouchMethod.methodName}"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Effects hook installation failed; original keyboard remains untouched.", t)
        }

        val perKeyDrawMethod = if (symbols.isReadyForMotion()) {
            symbols.perkeyDrawMethod?.restore(app.classLoader)
        } else {
            null
        }
        if (perKeyDrawMethod == null) {
            Log.w(TAG, "Key motion unavailable: per-key draw/redraw symbols are not ready; other effects remain active.")
            return
        }
        perKeyDrawMethodRef = perKeyDrawMethod.also { runCatching { it.isAccessible = true } }

        var motionDrawHookInstalled = false
        try {
            module.hook(perKeyDrawMethod).intercept { chain ->
                if (drawingActiveKeyOnScreen.get() == true) {
                    return@intercept chain.proceed()
                }

                val canvas = chain.args.getOrNull(0) as? Canvas
                val entity = chain.args.getOrNull(1)
                val view = chain.thisObject as? View
                val targetView = view?.let { it.rootView ?: it }
                val drawable = targetView?.let { overlayMap[it] }

                if (motionShadowHooksInstalled && entity != null && drawable != null && drawable.isMotionActive(entity)) {
                    // Motion key transform is applied on the screen canvas during the overlay draw pass.
                    // Baking into the shared offscreen buffer bitmap is skipped; that pass would clip and corrupt neighbors.
                    return@intercept null
                }

                var saveCount: Int? = null
                if (!motionShadowHooksInstalled && canvas != null && entity != null && drawable != null) {
                    try {
                        saveCount = drawable.beginKeyDraw(canvas, entity)
                    } catch (t: Throwable) {
                        if (drawFailureLogged.compareAndSet(false, true)) {
                            Log.w(TAG, "Key motion draw transform failed; preserving original draw.", t)
                        }
                    }
                }
                try {
                    chain.proceed()
                } finally {
                    if (saveCount != null && canvas != null) {
                        drawable?.endKeyDraw(canvas, saveCount)
                    }
                }
            }
            Log.i(TAG, "Installed per-key motion draw hook on ${symbols.perkeyDrawMethod?.className}.${symbols.perkeyDrawMethod?.methodName}")
            motionDrawHookInstalled = true
        } catch (t: Throwable) {
            Log.e(TAG, "Per-key motion draw hook installation failed; other effects remain active.", t)
        }

        if (symbols.isReadyForMotionShadows()) {
            installMotionShadowHooks(module, app, symbols)
        } else {
            Log.w(TAG, "Key motion shadows unavailable; native shadows remain at their original positions.")
        }
    }

    private fun installMotionShadowHooks(
        module: XposedModule,
        app: Application,
        symbols: OplusInputSymbols
    ) {
        if (!symbols.isReadyForMotionShadows()) {
            Log.w(TAG, "Key motion shadows unavailable; native shadows remain at their original positions.")
            return
        }

        val keyboardDrawMethod = symbols.keyboardDrawMethod?.restore(app.classLoader) ?: return
        val underlayMethod = symbols.perkeyShadowUnderlayMethod?.restore(app.classLoader) ?: return
        val overlayMethod = symbols.perkeyShadowOverlayMethod?.restore(app.classLoader) ?: return
        val handles = ArrayList<XposedInterface.HookHandle>(3)
        val shadowFailureLogged = AtomicBoolean(false)

        try {
            handles += module.hook(keyboardDrawMethod).intercept { chain ->
                val view = chain.thisObject as? View
                if (view != null) {
                    syncHierarchyClipping(view)
                }
                val targetView = view?.let { it.rootView ?: it }
                val drawable = targetView?.let { overlayMap[it] }
                if (drawable == null) {
                    return@intercept chain.proceed()
                }

                drawingDrawable.set(drawable)
                currentKeyboardView.set(view)
                // Passes view so stale motions (key entity rebuilt by the host) drop before baking;
                // otherwise the full redraw skips those keys and leaves holes in the buffer.
                drawable.beginKeyboardDrawFrame(view)
                try {
                    chain.proceed()
                } finally {
                    drawable.endKeyboardDrawFrame()
                    currentKeyboardView.remove()
                    drawingDrawable.remove()
                }
            }

            handles += installShadowPassHook(module, underlayMethod, shadowFailureLogged, isOverlay = false)
            handles += installShadowPassHook(module, overlayMethod, shadowFailureLogged, isOverlay = true)
            Log.i(
                TAG,
                "Installed motion shadow hooks: " +
                    "underlay=${underlayMethod.declaringClass.name}.${underlayMethod.name} " +
                    "overlay=${overlayMethod.declaringClass.name}.${overlayMethod.name}"
            )
            motionShadowHooksInstalled = true
        } catch (t: Throwable) {
            handles.asReversed().forEach { runCatching { it.unhook() } }
            motionShadowHooksInstalled = false
            Log.e(TAG, "Motion shadow hook installation failed; native shadows remain unchanged.", t)
        }
    }

    private fun installShadowPassHook(
        module: XposedModule,
        method: Method,
        failureLogged: AtomicBoolean,
        isOverlay: Boolean
    ): XposedInterface.HookHandle {
        return module.hook(method).intercept { chain ->
            if (shadowReentry.get() == true) {
                return@intercept chain.proceed()
            }

            val drawable = drawingDrawable.get() ?: return@intercept chain.proceed()
            val canvas = chain.args.getOrNull(0) as? Canvas ?: return@intercept chain.proceed()
            val source = chain.args.getOrNull(1) as? List<*> ?: return@intercept chain.proceed()
            val enabled = chain.args.getOrNull(2) as? Boolean ?: return@intercept chain.proceed()
            val buffers = shadowBuffers.get() ?: ShadowBuffers().also(shadowBuffers::set)
            if (!drawable.partitionMotionEntities(source, buffers.inactive, buffers.active)) {
                return@intercept chain.proceed()
            }

            val result = chain.proceed(arrayOf(canvas, buffers.inactive, enabled))
            val view = currentKeyboardView.get()
            val keyDrawMethod = perKeyDrawMethodRef
            for (entity in buffers.active) {
                entity ?: continue
                var saveCount: Int? = null
                try {
                    saveCount = drawable.beginKeyDraw(canvas, entity)
                    if (isOverlay && view != null && keyDrawMethod != null) {
                        drawingActiveKeyOnScreen.set(true)
                        try {
                            keyDrawMethod.invoke(view, canvas, entity)
                        } catch (t: Throwable) {
                            if (failureLogged.compareAndSet(false, true)) {
                                Log.w(TAG, "Motion key content redraw on screen canvas failed.", t)
                            }
                        } finally {
                            drawingActiveKeyOnScreen.remove()
                        }
                    }
                    buffers.single.clear()
                    buffers.single.add(entity)
                    shadowReentry.set(true)
                    invokeStatic(method, canvas, buffers.single, enabled)
                } catch (t: Throwable) {
                    if (failureLogged.compareAndSet(false, true)) {
                        Log.w(TAG, "Motion shadow redraw failed for one frame; retrying on the next redraw.", t)
                    }
                } finally {
                    shadowReentry.remove()
                    drawable.endKeyDraw(canvas, saveCount)
                }
            }
            result
        }
    }

    private fun invokeStatic(method: Method, vararg args: Any?) {
        try {
            method.invoke(null, *args)
        } catch (t: InvocationTargetException) {
            throw t.targetException
        }
    }

    private fun getOrAttachOverlayDrawable(
        targetView: View,
        perKeyProvider: OplusPerKeyBoundsProvider?
    ): KeyboardEffectsDrawable {
        var drawable = overlayMap[targetView]
        if (drawable == null) {
            drawable = KeyboardEffectsDrawable(targetView)
            drawable.setPerKeyBoundsProvider(perKeyProvider)
            overlayMap[targetView] = drawable
            targetView.overlay.add(drawable)
        }
        val w = targetView.width
        val h = targetView.height
        if (w > 0 && h > 0) {
            drawable.setBounds(0, 0, w, h)
        }
        return drawable
    }

    /**
     * Reports whether the current mode is character input (the fixed non-emoji container).
     * The host body View is the persistent character container; the check tests that the
     * event coordinate falls inside the keyboard area.
     */
    private fun isCharacterInputMode(view: View, event: MotionEvent): Boolean {
        if (view.width <= 0 || view.height <= 0) return false
        val y = event.y
        return y >= 0f && y <= view.height.toFloat()
    }

    /**
     * Resets installation flag, clears overlays, and restores original ViewGroup clipping flags.
     */
    fun reset() {
        installed.set(false)
        overlayMap.clear()
        clipUnclipDesired = null
        synchronized(originalClipChildren) {
            for ((parent, orig) in originalClipChildren) {
                if (parent.clipChildren != orig) {
                    parent.clipChildren = orig
                }
            }
            originalClipChildren.clear()
        }
        synchronized(originalClipToPadding) {
            for ((parent, orig) in originalClipToPadding) {
                if (parent.clipToPadding != orig) {
                    parent.clipToPadding = orig
                }
            }
            originalClipToPadding.clear()
        }
    }
}

