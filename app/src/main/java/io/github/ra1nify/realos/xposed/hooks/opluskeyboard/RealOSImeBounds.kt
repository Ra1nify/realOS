package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.graphics.Rect
import android.view.View
import java.lang.ref.WeakReference

/**
 * Runtime access point for the IME's actual visible area.
 *
 * The background overlay is confined to the IME, but not every container in the host
 * hierarchy sits inside the IME:
 *  - ImeContainer `q` (LifecycleConstraintLayout) is the return value of `onCreateInputView()`,
 *    **the IME view itself**;
 *  - the framework wraps it in `mInputFrame` → `mFrame` (window root container) → decorView.
 *    These window-level containers can be stretched to full-screen height (the host applies
 *    `max(parentHeight, decorView.getMeasuredHeight())`-style maximums), so **an overlay
 *    attached above the IME view draws outside the IME**.
 *
 * Conclusion: the overlay bounds come from the IME view's own visible rectangle; no
 * window-level or ancestor-level size is used.
 *
 * This object provides one thing: an authoritative IME visible rectangle in screen
 * coordinates.
 *  - Primary: the runtime-captured IME view (inputView, the root returned by onCreateInputView);
 *  - fallback: the runtime-captured keyboard draw View's visible rectangle (covers at least
 *    the keyboard body);
 *  - when neither is available it returns an empty rectangle; callers fail closed
 *    (no draw / no attach).
 *
 * Thread safety: addresses are read and written on the main thread only; the @Volatile weak
 * references are readable from any thread.
 */
internal object RealOSImeBounds {

    /** IME view (the value returned by onCreateInputView, i.e. ImeContainer). */
    @Volatile
    private var inputViewRef: WeakReference<View>? = null

    /** Keyboard draw View (the drawing view inside the body host), the fallback source. */
    @Volatile
    private var keyboardViewRef: WeakReference<View>? = null

    fun bindInputView(view: View) {
        inputViewRef = WeakReference(view)
    }

    fun bindKeyboardView(view: View) {
        keyboardViewRef = WeakReference(view)
    }

    fun clear() {
        inputViewRef = null
        keyboardViewRef = null
    }

    /** Currently captured IME view (the value returned by onCreateInputView). Null when unset. */
    fun inputViewOrNull(): View? = inputViewRef?.get()

    /** Currently captured keyboard draw View. Null when unset. */
    fun keyboardViewOrNull(): View? = keyboardViewRef?.get()

    /**
     * Returns the current IME visible rectangle in screen coordinates.
     * An empty rectangle means the area is undetermined; callers must fail closed.
     */
    fun currentVisibleRect(out: Rect): Boolean {
        val input = inputViewRef?.get()
        if (input != null && input.isAttachedToWindow && input.width > 0 && input.height > 0) {
            if (globalRect(input, out)) return true
        }
        val kb = keyboardViewRef?.get()
        if (kb != null && kb.isAttachedToWindow && kb.width > 0 && kb.height > 0) {
            if (globalRect(kb, out)) return true
        }
        return false
    }

    /**
     * Converts the IME rectangle in screen coordinates into a local rectangle relative to
     * [host]. An empty rectangle means no intersection or the position could not be located.
     *
     * The overlay clips to the local rectangle returned here, which pins its drawing inside
     * the IME area even when the overlay sits in a container larger than the IME.
     */
    fun imeRectInHost(host: View, out: Rect): Boolean {
        val imeRect = Rect()
        if (!currentVisibleRect(imeRect)) return false
        return intersectInHost(host, imeRect, out)
    }

    /** Intersects screen rect [screenRect] with the host bounds in [host] coordinates. */
    private fun intersectInHost(host: View, screenRect: Rect, out: Rect): Boolean {
        val hostRect = Rect()
        if (!globalRect(host, hostRect)) return false
        // Translates the IME screen rect into host local coordinates
        val local = Rect(screenRect)
        local.offset(-hostRect.left, -hostRect.top)
        // Intersects it with the host local bounds
        val bounded = Rect(0, 0, host.width, host.height)
        if (!local.intersect(bounded)) {
            out.setEmpty()
            return false
        }
        out.set(local)
        return !out.isEmpty
    }

    /** Reads the screen rect with getGlobalVisibleRect; falls back to getLocationOnScreen on failure. */
    private fun globalRect(v: View, out: Rect): Boolean {
        if (v.getGlobalVisibleRect(out)) return !out.isEmpty
        val loc = IntArray(2)
        runCatching { v.getLocationOnScreen(loc) }.getOrElse { return false }
        out.set(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
        return !out.isEmpty
    }
}
