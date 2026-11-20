package io.github.ra1nify.realos.xposed.hooks.launcher.recents

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * One capsule row: swipe left/right to dismiss, without disturbing the parent `ScrollView`'s vertical scroll.
 *
 * **All gesture handling lives in [onTouchEvent]; [onInterceptTouchEvent] is not used.**
 * The row's header is not clickable, so the row consumes DOWN itself;
 * `mFirstTouchTarget == null` then, and `onInterceptTouchEvent` is **never called again**,
 * so drag detection placed there would never run (the earlier "swipe did nothing" bug).
 *
 * Split of work:
 * - vertical wins first → mark `abandoned`, drop this gesture, let the `ScrollView` scroll;
 * - horizontal clearly dominant → `requestDisallowInterceptTouchEvent(true)` takes over, `translationX` follows the finger;
 * - no drag started → hand back to `super`, the `OnClickListener` fires normally.
 */
internal class SwipeDismissRow(context: Context) : FrameLayout(context) {

    /** Dismiss callback; invoked once by this class after the animation ends */
    var onDismiss: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dismissing = false

    /** This gesture was judged vertical; the row stops competing for it */
    private var abandoned = false

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (dismissing) return true

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
                abandoned = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging && !abandoned) {
                    val dx = abs(ev.rawX - downX)
                    val dy = abs(ev.rawY - downY)
                    if (dy > touchSlop && dy >= dx) {
                        abandoned = true
                    } else if (dx > touchSlop * 2 && dx > dy * 2f) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (dragging) {
                    translationX = ev.rawX - downX
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (dragging) {
                    dragging = false
                    if (ev.actionMasked == MotionEvent.ACTION_UP &&
                        abs(translationX) > width * DISMISS_THRESHOLD
                    ) {
                        flyOut()
                    } else {
                        animate().translationX(0f).setDuration(SPRING_BACK_MS).start()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun flyOut() {
        dismissing = true
        val target = if (translationX >= 0f) width.toFloat() else -width.toFloat()
        animate()
            .translationX(target)
            .alpha(0f)
            .setDuration(FLY_OUT_MS)
            .withEndAction { onDismiss?.invoke() }
            .start()
    }

    /** Resets the row when reused (normally the row is removed after dismissal; this is the fallback) */
    fun reset() {
        animate().cancel()
        translationX = 0f
        alpha = 1f
        dragging = false
        dismissing = false
        abandoned = false
    }

    private companion object {
        const val DISMISS_THRESHOLD = 0.35f
        const val FLY_OUT_MS = 180L
        const val SPRING_BACK_MS = 150L
    }
}
