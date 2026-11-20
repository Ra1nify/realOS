package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.graphics.Rect
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import android.view.ViewGroup

/**
 * Runtime hierarchy diagnostics and shared host selection for the background overlay.
 *
 * Design rules (per gemini-oplus-blur-radius-and-expanded-background-fix.md §3.3/§3.4/§6):
 *  - no JADX-obfuscated class/field names and no fixed child indices;
 *  - decisions use only real runtime parent/ancestor relations, measured/layout bounds and
 *    visibility;
 *  - a shared host that cannot be uniquely determined is **fail-closed** (returns null; the
 *    caller falls back to the verified keyboard body overlay);
 *  - the overlay never attaches to decorView / Window / screen size.
 *
 * Diagnostics are gated by [io.github.ra1nify.realos.BuildConfig].DEBUG and printed once per
 * keyboard View, on first capture.
 */
internal object RealOSOverlayHostProbe {

    private const val TAG = "RealOSOverlayProbe"

    /** Hosts already diagnosed (weak-reference set, one diagnostic dump per host). */
    private val diagnosedHosts = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())
    )

    /**
     * Shared host selection result.
     *
     * @param host    the selected shared host (the overlay's container)
     * @param reason  selection or rejection reason, used for logging
     */
    data class HostSelection(
        val host: ViewGroup?,
        val reason: String
    )

    /**
     * Walks up from the resolved keyboard draw View and returns the first ancestor meeting all
     * shared host conditions:
     *  1. the host is an ancestor of keyboardView (or its direct parent);
     *  2. the host is measured (measuredWidth/Height > 0) and visible;
     *  3. the host bounds contain the keyboardView bounds (same coordinate system, not clipped away);
     *  4. the host carries at least one other non-decorative sibling subtree (content besides
     *     keyboardView), so it may cover a larger area such as toolbar/candidate/bottom.
     *
     * This method does not guess which child is the toolbar; it only checks that the host sits
     * higher than keyboardView's direct parent, covering area beyond the keyboard body.
     * Returns null when nothing qualifies; the caller keeps the existing keyboard body overlay.
     */
    fun selectCommonHost(keyboardView: View): HostSelection {
        val directParent = keyboardView.parent as? ViewGroup
            ?: return HostSelection(null, "keyboardView has no ViewGroup parent")

        if (!isMeasured(directParent)) {
            return HostSelection(null, "direct parent not measured")
        }

        // Upper bound: the IME view (the value returned by onCreateInputView). Never climb above
        // it — the framework containers above it (mInputFrame/mFrame/decorView) can be stretched
        // to full-screen height, where an attached overlay draws outside the IME. Without an
        // upper bound no extra limit applies (the depth limit still applies).
        val inputBound: View? = RealOSImeBounds.inputViewOrNull()

        // Walks up from the direct parent's parent looking for a higher shared content host.
        // Conditions (all must hold for a shared host):
        //   1. the ancestor's measured bounds are strictly larger than keyboardView's bounds
        //      (it carries area beyond the keyboard);
        //   2. it contains keyboardView's bounds (same coordinate system, not clipped away);
        //   3. at least 2 children (toolbar/candidate/bottom content besides the keyboard);
        //   4. the child count stays small (< 8), excluding root containers that hold
        //      popup/floating content.
        // Stops at the first match (the lowest qualifying ancestor), without climbing to the root.
        var candidate: ViewGroup? = directParent.parent as? ViewGroup
        var depth = 0
        val kbRect = screenRect(keyboardView)
        val kbArea = areaOfRect(kbRect)
        var best: ViewGroup? = null
        var bestReason = "no elevated ancestor qualified (fallback to keyboard body)"

        while (candidate != null && depth < 8) {
            depth++
            val host = candidate
            // Upper bound guard: once a candidate passes the IME view, stop climbing at once
            // (containers above inputBound are not considered).
            if (inputBound != null && isAbove(host, inputBound)) {
                break
            }
            if (!isMeasured(host) || host.visibility != View.VISIBLE) {
                candidate = host.parent as? ViewGroup
                continue
            }
            val qualifies = canContain(host, kbRect) &&
                area(host) > kbArea * 1.02f &&
                host.childCount in 2..7
            if (qualifies) {
                best = host
                bestReason = "elevated host at depth $depth (${host.javaClass.simpleName}, " +
                    "measured=${host.measuredWidth}x${host.measuredHeight}, children=${host.childCount})"
                break
            }
            candidate = host.parent as? ViewGroup
        }

        // With no higher host, a direct parent much larger than keyboardView carries the whole
        // content area itself (for example a FrameLayout host) and is used directly.
        if (best == null && area(directParent) > kbArea * 1.02f && directParent.childCount in 2..7) {
            best = directParent
            bestReason = "direct parent already large enough (${directParent.javaClass.simpleName}, " +
                "measured=${directParent.measuredWidth}x${directParent.measuredHeight}, children=${directParent.childCount})"
        }

        // Final fallback: with no host yet, use the IME view itself when it is available and an
        // ancestor of keyboardView — its bounds are the IME area (safest with the overlay's
        // clipToIme).
        if (best == null && inputBound is ViewGroup && inputBound !== keyboardView &&
            isAncestor(inputBound, keyboardView) && isMeasured(inputBound)
        ) {
            best = inputBound
            bestReason = "fallback to IME input view (${inputBound.javaClass.simpleName}, " +
                "measured=${inputBound.measuredWidth}x${inputBound.measuredHeight}, children=${inputBound.childCount})"
        }

        if (io.github.ra1nify.realos.BuildConfig.DEBUG) {
            dumpTreeOnce(keyboardView, best, bestReason)
        }

        return HostSelection(best, bestReason)
    }

    /** Whether the View has been measured to a non-zero size. */
    private fun isMeasured(v: View): Boolean =
        v.measuredWidth > 0 && v.measuredHeight > 0

    /** Whether [descendant] is a descendant of [ancestor], direct or indirect. */
    private fun isAncestor(ancestor: View, descendant: View): Boolean {
        var cur: View? = descendant
        var guard = 0
        while (cur != null && guard++ < 64) {
            if (cur === ancestor) return true
            cur = cur.parent as? View
        }
        return false
    }

    /**
     * Whether [maybeAbove] sits above [bound] (that is, [bound] is a descendant of
     * [maybeAbove]). Used while climbing for a host to stop at any ancestor past the IME view.
     */
    private fun isAbove(maybeAbove: View, bound: View): Boolean = isAncestor(maybeAbove, bound)

    /**
     * Computes the overlay's insert index inside the host: after the pure background nodes and
     * before any interactive foreground.
     *
     * Pure background node conditions (structure + geometry, all must hold):
     *  - an ImageView / AppCompatImageView subclass;
     *  - no child View (a leaf);
     *  - not clickable and not focusable (non-interactive widget);
     *  - size close to the whole host (>= 80%), marking a full-size background candidate
     *    (such as Oplus N);
     *  - visible (a GONE background node needs no clearance).
     *
     * Returns 0 (the host's bottom) when no such node exists.
     * Decisions never use a fixed index 0 or a JADX class name.
     */
    fun computeBackgroundInsertIndex(host: ViewGroup): Int {
        var index = 0
        for (i in 0 until host.childCount) {
            val child = host.getChildAt(i) ?: continue
            if (isPureBackgroundNode(host, child)) {
                index = i + 1
            }
        }
        return index.coerceIn(0, host.childCount)
    }

    private fun isPureBackgroundNode(host: ViewGroup, child: View): Boolean {
        if (child.visibility != View.VISIBLE) return false
        if (child.isClickable || child.isFocusable) return false
        if (child is ViewGroup && child.childCount > 0) return false
        if (child !is android.widget.ImageView) return false
        val hw = host.measuredWidth
        val hh = host.measuredHeight
        if (hw <= 0 || hh <= 0) return false
        val cw = child.measuredWidth
        val ch = child.measuredHeight
        if (cw <= 0 || ch <= 0) return false
        // Full-size background candidate: width and height each reach 80% of the host
        return cw >= hw * 0.8f && ch >= hh * 0.8f
    }

    /**
     * Returns a snapshot of every pure background node in the host for the caller to turn
     * transparent. Callers process only the Views in the returned list; they passed the
     * structure and geometry checks.
     */
    fun findPureBackgroundNodes(host: ViewGroup): List<View> {
        val result = ArrayList<View>(2)
        for (i in 0 until host.childCount) {
            val child = host.getChildAt(i) ?: continue
            if (isPureBackgroundNode(host, child)) result.add(child)
        }
        return result
    }

    private fun area(v: View): Float = v.measuredWidth.toFloat() * v.measuredHeight.toFloat()

    private fun areaOfRect(r: Rect): Float =
        r.width().toFloat().coerceAtLeast(0f) * r.height().toFloat().coerceAtLeast(0f)

    /** Computes the View's absolute rectangle in screen coordinates (clip-independent, used only for area comparison). */
    private fun screenRect(v: View): Rect {
        val r = Rect()
        v.getGlobalVisibleRect(r)
        if (r.isEmpty) {
            // Fallback: derive from local bounds without clipping
            r.set(0, 0, v.width, v.height)
        }
        return r
    }

    /** Whether the host contains the target rect in screen coordinates (a zero-size host counts as unavailable). */
    private fun canContain(host: ViewGroup, target: Rect): Boolean {
        if (target.isEmpty) return false
        val hr = screenRect(host)
        if (hr.isEmpty) return false
        return hr.width() >= target.width() && hr.height() >= target.height()
    }

    /**
     * Prints the ancestor chain and sibling geometry summary once. Output only in debug builds
     * and only on the first hit per host.
     * Prints a summary, not full object dumps.
     */
    private fun dumpTreeOnce(keyboardView: View, chosen: ViewGroup?, reason: String) {
        val marker = chosen ?: keyboardView
        if (!diagnosedHosts.add(marker)) return
        runCatching {
            val sb = StringBuilder()
            sb.append("[OverlayProbe] keyboardView=").append(keyboardView.javaClass.simpleName)
                .append(" id=").append(resName(keyboardView))
                .append(" bounds=").append(screenRect(keyboardView).toShortString())
                .append('\n')
            sb.append("  chosenHost=")
                .append(chosen?.javaClass?.simpleName ?: "null")
                .append(" reason=").append(reason)
                .append('\n')

            var cur: View? = keyboardView
            var depth = 0
            while (cur != null && depth < 8) {
                val p = cur.parent as? ViewGroup
                sb.append("  [").append(depth).append("] ")
                    .append(cur.javaClass.simpleName)
                    .append(" id=").append(resName(cur))
                    .append(" measured=").append(cur.measuredWidth).append('x').append(cur.measuredHeight)
                    .append(" vis=").append(cur.visibility)
                    .append(" clip=").append(p?.clipChildren)
                    .append("/").append(p?.clipToPadding)
                    .append(" parent=").append(p?.javaClass?.simpleName ?: "-")
                    .append('\n')
                cur = p
                depth++
            }

            val host = chosen ?: (keyboardView.parent as? ViewGroup)
            if (host != null) {
                sb.append("  siblings of host (").append(host.childCount).append("):\n")
                for (i in 0 until host.childCount) {
                    val c = host.getChildAt(i)
                    sb.append("    #").append(i).append(' ')
                        .append(c.javaClass.simpleName)
                        .append(" id=").append(resName(c))
                        .append(" measured=").append(c.measuredWidth).append('x').append(c.measuredHeight)
                        .append(" vis=").append(c.visibility)
                        .append('\n')
                }
            }
            Log.i(TAG, sb.toString())
        }
    }

    private fun resName(v: View): String = runCatching {
        if (v.id == View.NO_ID) "-" else v.resources.getResourceEntryName(v.id)
    }.getOrDefault("-")
}
