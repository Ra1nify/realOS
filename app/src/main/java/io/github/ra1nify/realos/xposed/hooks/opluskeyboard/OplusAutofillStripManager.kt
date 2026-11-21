package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.SurfaceControl
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.inputmethod.InlineSuggestion
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.inline.InlineContentView
import androidx.annotation.RequiresApi
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import java.lang.ref.WeakReference
import java.util.concurrent.Executor

/**
 * Manages presentation, layout inflation, and transition animations of Autofill InlineSuggestions
 * for OPPO / ColorOS Keyboard (com.oplus.keyboard).
 *
 * Mount path:
 * 1. Host top container:
 *    Adds the suggestion bubbles and close button as direct children of the keyboard's
 *    `container_toolbar` item container. The stock top-bar background, height, left/right
 *    tool icons, and HorizontalScrollView are preserved; no overlay row is created.
 * 2. No floating fallback:
 *    When the toolbar cannot be resolved, is hidden, or the keyboard search bar is active,
 *    the response is not displayed and [onSuggestionsReceived] returns false. The request
 *    preflight also declines the inline request, which lets the autofill provider use its
 *    external popup UI instead.
 * 3. Viewport clipping:
 *    The HorizontalScrollView viewport clips the host slots; each provider InlineContentView
 *    also crops its remote child surface to the local visible rectangle so scrolled-out text
 *    cannot draw outside the strip.
 *
 * The toolbar is only resolved through the module symbol resolver. Class-name prefix scans are
 * not used: the host has multiple `com.oplus.keyboard.input.view.head.*` views (toolbar and
 * search bar) and a prefix match can select the search bar, whose custom measurement leaves the
 * embedded row at 0x0.
 */
@RequiresApi(Build.VERSION_CODES.R)
object OplusAutofillStripManager {

    private const val TAG = "OplusAutofillStrip"

    /** Empty responses inside this window after a show are treated as another provider's empty result. */
    private const val SECONDARY_PROVIDER_SUPPRESS_MS = 4000L

    /**
     * Window after a user close during which the inline strip is not shown.
     *
     * The system keeps pushing responses in the same autofill round,
     * and without this window the strip is re-attached immediately.
     * The toolbar button clears it through [onUserRequestedShow]; onStartInput does not.
     */
    private const val USER_DISMISS_SUPPRESS_MS = 8000L
    private const val TAG_AUTOFILL_BUBBLE = "realos_autofill_bubble"

    private var activeKRef: WeakReference<ViewGroup>? = null
    private val hiddenKChildrenState: MutableList<Pair<WeakReference<View>, Int>> = mutableListOf()

    /** Stock toolbar buttons (`toolbar_iv_box` / `toolbar_iv_close`) hidden during autofill. */
    private val hiddenToolbarChrome: MutableList<Pair<WeakReference<View>, Int>> = mutableListOf()

    /** Host container gravity while the stock tools (right-aligned) are replaced. */
    private var savedContainerGravity: Int? = null

    /** Left inset of the scroll viewport while bubbles are shown; keeps content off the edge. */
    private var savedScrollPaddingLeft: Int? = null
    private var savedScrollClipToPadding: Boolean? = null
    private var savedScrollClipToOutline: Boolean? = null
    private var savedScrollOutlineProvider: ViewOutlineProvider? = null

    /** Fixed close button pinned over the stock end slot, and the scrolling strip. */
    private var activeCloseButton: WeakReference<View>? = null
    private var activeScrollView: WeakReference<View>? = null
    private val activeInlineViews: MutableList<WeakReference<InlineContentView>> = mutableListOf()
    private var scrollClipListener: ViewTreeObserver.OnScrollChangedListener? = null
    private var savedScrollStartToStart: Int? = null
    private var savedScrollStartToEnd: Int? = null
    private var savedScrollLeftToLeft: Int? = null
    private var savedScrollLeftToRight: Int? = null

    @Volatile
    private var cornerClipFailureLogged = false

    @Volatile
    private var childSurfaceCropFailureLogged = false

    @Volatile
    private var childSurfaceCornerFailureLogged = false

    /** Timestamp of the last non-empty show; filters multi-provider empty responses, kept across dismiss. */
    @Volatile
    private var lastShownTimestamp = 0L

    /** Suppression deadline after a user close (elapsedRealtime); no strip is mounted before it. */
    @Volatile
    private var suppressedUntil = 0L

    /** Set when a non-empty response could not be displayed; the next request then declines inline. */
    @Volatile
    private var inlineDisplayFailed = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    /**
     * Displays inline suggestions in the host toolbar.
     *
     * @return true when the suggestions were attached to a visible toolbar; false when no inline
     * container is available. The caller reports the false result back to the framework.
     */
    fun onSuggestionsReceived(
        module: XposedModule?,
        imeService: InputMethodService,
        suggestions: List<InlineSuggestion>,
        symbols: OplusInputSymbols? = null
    ): Boolean {
        if (!Looper.getMainLooper().isCurrentThread) {
            mainHandler.post { onSuggestionsReceived(module, imeService, suggestions, symbols) }
            return false
        }

        module?.log(Log.INFO, TAG, "=== onSuggestionsReceived called with ${suggestions.size} suggestion(s) ===")

        // Drops responses that arrive inside the user-close suppression window.
        if (suppressedUntil > 0L) {
            val now = SystemClock.elapsedRealtime()
            if (now < suppressedUntil) {
                module?.log(
                    Log.INFO, TAG,
                    "Ignoring ${suggestions.size} suggestion(s): user dismissed, ${suppressedUntil - now}ms of suppression left"
                )
                return false
            }
            suppressedUntil = 0L
        }

        if (suggestions.isEmpty()) {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastShownTimestamp
            // Android 14+ queries several providers (CredentialManager / AutofillService) at once,
            // and one of them returning empty is normal. An empty response shortly after a
            // successful show is dropped instead of cycling 3 -> mount -> empty -> 3.
            if (lastShownTimestamp > 0L && elapsed < SECONDARY_PROVIDER_SUPPRESS_MS) {
                module?.log(Log.INFO, TAG, "Suppressed secondary provider empty response (${elapsed}ms race detected)")
                return false
            }
            module?.log(Log.INFO, TAG, "Empty suggestions response received; dismissing.")
            dismiss(module, imeService)
            return false
        }

        lastShownTimestamp = SystemClock.elapsedRealtime()

        val window = imeService.window
        val decorView = window?.window?.decorView as? ViewGroup
        if (decorView == null) {
            inlineDisplayFailed = true
            module?.log(Log.ERROR, TAG, "Cannot obtain decorView from imeService.window")
            return false
        }

        cleanupActiveViewsImmediate(module)

        val density = imeService.resources.displayMetrics.density
        val itemHeight = (40 * density).toInt()

        val inputView = findInputView(decorView)
        val targetContainer = findToolbarContainer(inputView ?: decorView, symbols)
            ?: if (inputView != null && inputView !== decorView) {
                findToolbarContainer(decorView, symbols)
            } else {
                null
            }
        // Wide bubbles; the host HorizontalScrollView handles overflow. Two bubbles are visible
        // at a time on the stock top bar, the rest scroll under the fixed close button.
        val itemWidth = (168 * density).toInt()
        val itemSize = Size(itemWidth, itemHeight)
        module?.log(
            Log.INFO, TAG,
            "View search: inputView=${inputView?.javaClass?.name}, " +
                "toolbar=${targetContainer?.javaClass?.name}, " +
                "visible=${targetContainer?.isShown}, " +
                "size=${targetContainer?.width}x${targetContainer?.height}, " +
                "symbolClass=${symbols?.toolbarLayoutMethod?.className}"
        )

        if (targetContainer != null && targetContainer !== inputView) {
            try {
                if (embedPreemptiveStripInContainer(
                        module,
                        imeService,
                        targetContainer,
                        suggestions,
                        itemSize,
                        density
                    )
                ) {
                    inlineDisplayFailed = false
                    module?.log(Log.INFO, TAG, "Successfully embedded ${suggestions.size} suggestion(s) into preemptive strip")
                    return true
                }
            } catch (t: Throwable) {
                module?.log(Log.ERROR, TAG, "Failed to embed inline suggestions", t)
                cleanupActiveViewsImmediate(module)
            }
        }

        inlineDisplayFailed = true
        module?.log(
            Log.WARN, TAG,
            "Inline suggestions not displayed: no visible toolbar container; " +
                "reporting display failure"
        )
        return false
    }

    /**
     * Checks whether inline suggestions can be hosted before the request is sent.
     *
     * The request is declined when the host has no resolved toolbar or the keyboard search bar
     * currently replaces it. The framework then calls `onInlineSuggestionsUnsupported`, which is
     * the only supported way to make the autofill provider fall back to its external popup UI.
     *
     * A missing toolbar (hierarchy not built yet) is accepted once; a toolbar without the
     * `container_toolbar` item container is declined. Hidden top-bar content is restored by the
     * response path before the bubbles are attached.
     */
    fun canDisplayInline(imeService: InputMethodService, symbols: OplusInputSymbols?): Boolean {
        val className = symbols?.toolbarLayoutMethod?.className
        if (className.isNullOrEmpty()) return false

        // The request can arrive before the input view is attached or measured. The decorView
        // itself must exist to host anything; otherwise decline and let the provider use popup.
        val decorView = imeService.window?.window?.decorView as? ViewGroup ?: return false
        val inputView = findInputView(decorView)
        val root = inputView ?: decorView
        if (isSearchViewVisible(root)) return false

        val toolbar = findToolbarByClass(root, className)
            ?: if (root !== decorView) findToolbarByClass(decorView, className) else null
        // A missing toolbar is accepted once as "not built yet"; after a concrete display
        // failure, the next request declines inline and falls back to the provider popup.
        if (toolbar == null) return !inlineDisplayFailed
        val containerId = CouiComponentFactory.resolveHostResId(
            toolbar.context,
            "container_toolbar",
            "id"
        )
        if (containerId == 0) return false
        return toolbar.findViewById<ViewGroup>(containerId) != null
    }

    /**
     * Creates suggestion slots and inflates them into the host top container.
     *
     * Callers supply the close strategy through [onSlotChosen] and [onAllFailed]; slot creation,
     * inflation, content attachment, and click binding live here.
     *
     * @param rowRoot host container used for requestLayout / invalidate
     * @param label log tag
     */
    private fun populateSuggestionSlots(
        module: XposedModule?,
        imeService: InputMethodService,
        suggestionsContainer: ViewGroup,
        scrollView: View,
        rowRoot: View,
        suggestions: List<InlineSuggestion>,
        itemSize: Size,
        density: Float,
        bubbleColor: Int,
        label: String,
        onSlotChosen: (FrameLayout) -> Unit,
        onAllFailed: () -> Unit
    ) {
        val slots = ArrayList<FrameLayout>()
        val isDark = isDarkTheme(imeService)

        for (i in suggestions.indices) {
            val slot = FrameLayout(imeService).apply {
                tag = TAG_AUTOFILL_BUBBLE
                clipChildren = true
                clipToPadding = true
                clipToOutline = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16 * density
                    setColor(bubbleColor)
                    setStroke(
                        (1 * density).toInt().coerceAtLeast(1),
                        if (isDark) Color.argb(50, 255, 255, 255) else Color.argb(22, 0, 0, 0)
                    )
                }
            }
            val slotLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                itemSize.height
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = (2 * density).toInt()
                marginEnd = (2 * density).toInt()
            }
            suggestionsContainer.addView(slot, slotLp)
            slots.add(slot)
        }

        var inflatedCount = 0
        var completedCount = 0

        for ((index, suggestion) in suggestions.withIndex()) {
            try {
                suggestion.inflate(imeService, itemSize, mainExecutor) { inlineContentView ->
                    completedCount++
                    if (inlineContentView != null) {
                        inflatedCount++
                        val slot = slots.getOrNull(index)
                        if (slot != null) {
                            slot.removeAllViews()

                            // InlineContentView hosts a SurfaceControlViewHost and has no local child to measure;
                            // WRAP_CONTENT measures width 0 (observed size=0x118). The width is set explicitly
                            // from itemSize.width as submitted to inflate().
                            val chipWidth = itemSize.width
                            val lp = FrameLayout.LayoutParams(
                                chipWidth,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                Gravity.START or Gravity.CENTER_VERTICAL
                            )
                            slot.addView(inlineContentView, lp)
                            inlineContentView.minimumWidth = chipWidth
                            inlineContentView.minimumHeight = itemSize.height
                            // InlineContentView enables SurfaceView clipping; give it the same
                            // rounded rectangle as the slot so the remote surface follows the
                            // keyboard design where the platform supports it.
                            inlineContentView.outlineProvider = object : ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: Outline) {
                                    outline.setRoundRect(
                                        0,
                                        0,
                                        view.width,
                                        view.height,
                                        16 * density
                                    )
                                }
                            }
                            inlineContentView.clipToOutline = true
                            applySurfaceCornerRadius(inlineContentView, 16 * density)

                            activeInlineViews.add(WeakReference(inlineContentView))
                            inlineContentView.setOnClickListener { onSlotChosen(slot) }
                            slot.setOnClickListener { inlineContentView.performClick() }
                            inlineContentView.post { updateSurfaceClips() }

                            inlineContentView.requestLayout()
                            slot.requestLayout()
                            suggestionsContainer.requestLayout()
                            scrollView.requestLayout()
                            rowRoot.requestLayout()
                            rowRoot.invalidate()
                            module?.log(Log.INFO, TAG, "Attached InlineContentView at slot $index into $label")
                        }
                    } else {
                        module?.log(Log.WARN, TAG, "InlineSuggestion.inflate callback at slot $index returned null view for $label")
                    }

                    if (completedCount == suggestions.size && inflatedCount == 0) {
                        module?.log(Log.WARN, TAG, "All suggestions failed to inflate; dismissing $label")
                        onAllFailed()
                    }
                }
            } catch (t: Throwable) {
                completedCount++
                module?.log(Log.ERROR, TAG, "Failed to inflate InlineSuggestion for $label at $index", t)
                if (completedCount == suggestions.size && inflatedCount == 0) {
                    onAllFailed()
                }
            }
        }
    }

    private fun embedPreemptiveStripInContainer(
        module: XposedModule?,
        imeService: InputMethodService,
        containerView: ViewGroup,
        suggestions: List<InlineSuggestion>,
        itemSize: Size,
        density: Float
    ): Boolean {
        // containerView is the keyboard's own top-bar item container. The bubbles are direct
        // children so the stock top-bar background, height, and HorizontalScrollView apply.
        containerView.visibility = View.VISIBLE
        var ancestor: View? = containerView.parent as? View
        while (ancestor != null) {
            if (ancestor.visibility != View.VISIBLE) ancestor.visibility = View.VISIBLE
            ancestor = ancestor.parent as? View
        }
        containerView.requestLayout()

        cleanupActiveViewsImmediate(module)

        // The host HorizontalScrollView keeps its end constraint against the stock right
        // button, which is replaced by the fixed close button. Move its start to the parent so
        // the bubbles use the whole left area instead of leaving the old stock-button inset.
        val scrollView = containerView.parent as? View
        if (scrollView != null) {
            activeScrollView = WeakReference(scrollView)
            expandScrollStart(scrollView, module)
            // A fixed left viewport inset keeps scrolled bubbles from being clipped exactly at
            // the screen edge. Padding on the scroll view stays fixed while its content scrolls.
            savedScrollPaddingLeft = scrollView.paddingLeft
            // The stock scroll view explicitly sets clipToPadding=false. Enable it while the
            // bubbles are shown, otherwise the padding is transparent to content and the
            // remote surface is still clipped exactly at x=0.
            savedScrollClipToPadding = (scrollView as? ViewGroup)?.clipToPadding
            (scrollView as? ViewGroup)?.clipToPadding = true
            scrollView.setPadding(
                scrollView.paddingLeft + 28,
                scrollView.paddingTop,
                scrollView.paddingRight,
                scrollView.paddingBottom
            )
            // Clip the scrolling viewport with the same corner radius as the bubbles. The
            // outline covers only the bubble row, so a bubble sliding under the left/right edge
            // is cut with a rounded edge instead of a straight rectangle.
            val bubbleHeight = itemSize.height
            val bubbleRadius = 16 * density
            savedScrollClipToOutline = scrollView.clipToOutline
            savedScrollOutlineProvider = scrollView.outlineProvider
            scrollView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val available = view.height - view.paddingTop - view.paddingBottom
                    val top = (view.paddingTop + (available - bubbleHeight) / 2)
                        .coerceAtLeast(view.paddingTop)
                    val bottom = (top + bubbleHeight)
                        .coerceAtMost(view.height - view.paddingBottom)
                    if (view.width > view.paddingLeft + view.paddingRight && bottom > top) {
                        outline.setRoundRect(
                            view.paddingLeft,
                            top,
                            view.width - view.paddingRight,
                            bottom,
                            bubbleRadius.coerceAtMost((bottom - top) / 2f)
                        )
                    } else {
                        outline.setEmpty()
                    }
                }
            }
            scrollView.clipToOutline = true
            setupSurfaceClipTracking(scrollView)
        }
        val topBar = hideToolbarChrome(containerView)

        // The stock tool container is right-aligned. Bubbles must start at the left edge while
        // the stock left/right buttons are hidden, otherwise a large blank remains on the left.
        if (containerView is LinearLayout) {
            savedContainerGravity = containerView.gravity
            containerView.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        // Hide the host's current tool items; they are restored on dismiss.
        hiddenKChildrenState.clear()
        for (i in 0 until containerView.childCount) {
            val child = containerView.getChildAt(i)
            if (child.tag == TAG_AUTOFILL_BUBBLE) continue
            if (child.visibility != View.GONE) {
                hiddenKChildrenState.add(Pair(WeakReference(child), child.visibility))
                child.visibility = View.GONE
            }
        }

        val isDark = isDarkTheme(imeService)
        val stockColor = stockToolBackgroundColor(imeService, isDark)
        val stockBackground = resolveStockToolBackground(imeService)

        // Register the container before inflation so a throw inside populate restores the host
        // tools, chrome, and gravity through the normal cleanup path.
        activeKRef = WeakReference(containerView)

        populateSuggestionSlots(
            module = module,
            imeService = imeService,
            suggestionsContainer = containerView,
            scrollView = (containerView.parent as? View) ?: containerView,
            rowRoot = containerView,
            suggestions = suggestions,
            itemSize = itemSize,
            density = density,
            bubbleColor = stockColor,
            label = "stock toolbar",
            onSlotChosen = { slot ->
                runCatching { slot.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
                mainHandler.postDelayed({
                    dismissByUser(module, imeService)
                }, 250)
            },
            onAllFailed = { dismiss(module, imeService) }
        )

        // Resolve the stock end slot first: the X reuses its width, height and end margin so it
        // sits exactly where the stock close/hide control was.
        val rightId = CouiComponentFactory.resolveHostResId(imeService, "toolbar_iv_close", "id")
        val rightButton = if (rightId != 0) topBar?.findViewById<View>(rightId) else null
        val slotWidth = rightButton?.layoutParams?.width?.takeIf { it > 0 }
            ?: rightButton?.width?.takeIf { it > 0 }
            ?: (40 * density).toInt()
        val slotHeight = rightButton?.layoutParams?.height?.takeIf { it > 0 }
            ?: rightButton?.height?.takeIf { it > 0 }
            ?: (40 * density).toInt()
        val iconSize = (24 * density).toInt()
        val circleSize = (32 * density).toInt()
        val insetX = ((slotWidth - circleSize) / 2).coerceAtLeast(0)
        val insetY = ((slotHeight - circleSize) / 2).coerceAtLeast(0)

        val closeButton = ImageView(imeService).apply {
            tag = TAG_AUTOFILL_BUBBLE
            contentDescription = OplusWorkshopUiText.getString(
                imeService,
                R.string.oplus_input_close_content_desc
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            val iconDrawable = resolveOfficialCloseDrawable(imeService, isDark)
            if (iconDrawable != null) {
                setImageDrawable(iconDrawable)
            }
            // Reuse the stock ToolItemView background drawable, inset to the stock 32dp circle
            // inside the taller toolbar hit area.
            val stockCircle = stockBackground?.constantState?.newDrawable()?.mutate()
                ?: GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(stockColor)
                    setStroke(
                        (1 * density).toInt().coerceAtLeast(1),
                        if (isDark) Color.argb(50, 255, 255, 255) else Color.argb(22, 0, 0, 0)
                    )
                }
            background = InsetDrawable(stockCircle, insetX, insetY, insetX, insetY)
            setPadding(
                ((slotWidth - iconSize) / 2).coerceAtLeast(0),
                ((slotHeight - iconSize) / 2).coerceAtLeast(0),
                ((slotWidth - iconSize) / 2).coerceAtLeast(0),
                ((slotHeight - iconSize) / 2).coerceAtLeast(0)
            )
            setOnClickListener {
                runCatching { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
                module?.log(Log.INFO, TAG, "Close button clicked; dismissing stock toolbar bubbles")
                dismissByUser(module, imeService)
            }
        }
        // Place the close in the stock top bar, pinned to the end, outside the scrolling strip.
        val addedToTopBar = if (topBar != null) {
            addFixedCloseButton(topBar, closeButton, rightButton, density, module)
        } else {
            false
        }
        if (addedToTopBar) {
            // Draw the fixed close above the scrolling strip at the right edge.
            closeButton.bringToFront()
            activeCloseButton = WeakReference(closeButton)
        } else {
            // Never add the close into the scroll container. Fall back to the stock right
            // button kept visible by hideToolbarChrome; it is already pinned at the right.
            module?.log(Log.WARN, TAG, "Fixed close overlay failed; using stock right button")
            rightButton?.visibility = View.VISIBLE
            activeCloseButton = null
        }

        containerView.requestLayout()
        containerView.invalidate()
        // The remote child surface package is attached asynchronously; crop it once more after
        // the first layout/surface pass.
        mainHandler.postDelayed({ updateSurfaceClips() }, 120L)
        return true
    }

    private fun cleanupActiveViewsImmediate(module: XposedModule?) {
        val kView = activeKRef?.get()
        if (kView != null) {
            for (i in kView.childCount - 1 downTo 0) {
                val child = kView.getChildAt(i)
                if (child.tag == TAG_AUTOFILL_BUBBLE) {
                    kView.removeViewAt(i)
                }
            }

            activeCloseButton?.get()?.let { close ->
                (close.parent as? ViewGroup)?.removeView(close)
            }
            activeCloseButton = null

            activeScrollView?.get()?.let { scroll ->
                scrollClipListener?.let { listener ->
                    runCatching { scroll.viewTreeObserver.removeOnScrollChangedListener(listener) }
                }
                savedScrollPaddingLeft?.let { paddingLeft ->
                    scroll.setPadding(
                        paddingLeft,
                        scroll.paddingTop,
                        scroll.paddingRight,
                        scroll.paddingBottom
                    )
                    savedScrollPaddingLeft = null
                }
                savedScrollClipToPadding?.let { clip ->
                    (scroll as? ViewGroup)?.clipToPadding = clip
                    savedScrollClipToPadding = null
                }
                savedScrollClipToOutline?.let { clip ->
                    scroll.clipToOutline = clip
                    savedScrollClipToOutline = null
                }
                savedScrollOutlineProvider?.let { provider ->
                    scroll.outlineProvider = provider
                    savedScrollOutlineProvider = null
                }
                restoreScrollStart(scroll)
            }
            scrollClipListener = null
            activeInlineViews.clear()
            activeScrollView = null

            val childrenToRestore = hiddenKChildrenState.mapNotNull { (weakRef, originalVisibility) ->
                weakRef.get()?.let { it to originalVisibility }
            }
            for ((child, originalVisibility) in childrenToRestore) {
                child.visibility = originalVisibility
                child.alpha = 1f
                child.translationY = 0f
            }
            hiddenKChildrenState.clear()

            val chromeToRestore = hiddenToolbarChrome.mapNotNull { (weakRef, originalVisibility) ->
                weakRef.get()?.let { it to originalVisibility }
            }
            for ((child, originalVisibility) in chromeToRestore) {
                child.visibility = originalVisibility
                child.alpha = 1f
                child.translationY = 0f
            }
            hiddenToolbarChrome.clear()

            savedContainerGravity?.let { gravity ->
                (kView as? LinearLayout)?.gravity = gravity
                savedContainerGravity = null
            }

            kView.visibility = View.VISIBLE
            kView.requestLayout()
            kView.invalidate()
            for (i in 0 until kView.childCount) {
                val child = kView.getChildAt(i)
                module?.log(
                    Log.INFO, TAG,
                    "Post-dismiss child[$i] ${child.javaClass.simpleName} " +
                        "vis=${child.visibility} size=${child.width}x${child.height}"
                )
            }

            // The host may re-hide its top-bar content right after our bubbles are removed.
            // Re-apply the original visibility for a short window unless a new embed started.
            for (attempt in 1..3) {
                kView.postDelayed({
                    val hasBubbles = (0 until kView.childCount).any {
                        kView.getChildAt(it).tag == TAG_AUTOFILL_BUBBLE
                    }
                    if (kView.isAttachedToWindow && !hasBubbles) {
                        for ((child, originalVisibility) in childrenToRestore) {
                            child.visibility = originalVisibility
                            child.alpha = 1f
                            child.translationY = 0f
                        }
                        for ((child, originalVisibility) in chromeToRestore) {
                            child.visibility = originalVisibility
                            child.alpha = 1f
                            child.translationY = 0f
                        }
                        kView.visibility = View.VISIBLE
                        kView.requestLayout()
                        kView.invalidate()
                        module?.log(Log.INFO, TAG, "Delayed toolbar restore attempt $attempt")
                    }
                }, 80L * attempt)
            }

            activeKRef = null
        }
    }

    /**
     * Handles a user-initiated show request from the toolbar autofill button.
     *
     * Clears the suppression window set by [dismissByUser].
     */
    fun onUserRequestedShow(module: XposedModule?) {
        if (suppressedUntil != 0L) {
            suppressedUntil = 0L
            module?.log(Log.INFO, TAG, "User requested autofill; suppression cleared")
        }
    }

    fun dismiss(module: XposedModule?, imeService: InputMethodService) {
        // The suppression window filters empty responses from other providers in the same round.
        // It is zeroed on close; a stale window would drop the empty response of the next
        // open and the user tap would produce no visible change.
        lastShownTimestamp = 0L

        // The container is read before cleanup: cleanupActiveViewsImmediate clears activeKRef,
        // so a later read returns null and restoreToolbar never runs.
        val kView = activeKRef?.get()

        cleanupActiveViewsImmediate(module)

        if (kView != null) {
            restoreToolbar(module, kView)
        }

        module?.log(Log.INFO, TAG, "Dismissed autofill suggestions and restored toolbar")
    }

    /**
     * Handles a user close of the embedded strip (close button or a suggested item tap).
     *
     * Runs local cleanup and opens the suppression window. It does not dispatch
     * performContextMenuAction(R.id.autofill): the system maps that action to
     * AutofillManager.requestAutofill(), so the call issues a fresh request and the
     * system re-sends suggestions.
     *
     * suppressedUntil blocks one further response in the same round.
     */
    fun dismissByUser(module: XposedModule?, imeService: InputMethodService) {
        suppressedUntil = SystemClock.elapsedRealtime() + USER_DISMISS_SUPPRESS_MS
        module?.log(
            Log.INFO, TAG,
            "User dismissed autofill; suppressing re-show for ${USER_DISMISS_SUPPRESS_MS}ms"
        )
        dismiss(module, imeService)
    }

    private fun findInputView(decorView: ViewGroup): View? {
        val inputViewId = decorView.resources.getIdentifier("inputView", "id", "android")
        if (inputViewId != 0) {
            val view = decorView.findViewById<View>(inputViewId)
            if (view != null) return view
        }
        val inputViewId2 = decorView.resources.getIdentifier("input_view", "id", "android")
        if (inputViewId2 != 0) {
            val view = decorView.findViewById<View>(inputViewId2)
            if (view != null) return view
        }
        return null
    }

    /**
     * Resolves the host toolbar through the symbol resolver only.
     *
     * A class-name prefix scan is intentionally not used: `com.oplus.keyboard.input.view.head`
     * contains both the toolbar (`head.B0`) and the search bar (`head.s0`). The search bar has a
     * custom measurement pass that leaves an embedded row at 0x0.
     *
     * Returns the host's `container_toolbar` item container, or null when the symbol or the
     * container resource is unavailable. The caller then reports display failure instead of
     * mounting an overlay.
     */
    private fun findToolbarContainer(root: View?, symbols: OplusInputSymbols?): ViewGroup? {
        if (root == null || root !is ViewGroup) return null
        // The keyboard search bar replaces the candidate/toolbar area in search mode. It is not
        // a candidate bar and must not be covered by the inline row.
        if (isSearchViewVisible(root)) return null
        val targetClassName = symbols?.toolbarLayoutMethod?.className
        if (targetClassName.isNullOrEmpty()) return null
        val toolbar = findVisibleToolbar(root, targetClassName) ?: return null
        if (toolbar === root) return null
        // Depend on the keyboard's own top-bar item container. Bubbles become direct children,
        // so the stock top-bar background, height, and HorizontalScrollView are preserved.
        val containerId = CouiComponentFactory.resolveHostResId(
            toolbar.context,
            "container_toolbar",
            "id"
        )
        if (containerId == 0) return null
        return toolbar.findViewById<ViewGroup>(containerId)
    }

    private fun isSearchViewVisible(root: View): Boolean {
        val searchViewId = CouiComponentFactory.resolveHostResId(root.context, "searchView", "id")
        if (searchViewId == 0) return false
        val searchView = root.findViewById<View>(searchViewId) ?: return false
        return searchView.isShown && searchView.width > 0 && searchView.height > 0
    }

    /**
     * Restores the toolbar layout.
     *
     * Calls requestLayout() and invalidate() on the container.
     */
    private fun restoreToolbar(module: XposedModule?, kView: ViewGroup) {
        runCatching {
            kView.requestLayout()
            kView.invalidate()
        }.onFailure {
            module?.log(Log.WARN, TAG, "restoreToolbar: requestLayout/invalidate failed", it)
        }
    }

    private fun resolveOfficialCloseDrawable(context: Context, isDark: Boolean): Drawable? {
        val resNames = listOf(
            "coui_ic_close",
            "coui_toolbar_ic_close",
            "coui_close",
            "ic_close",
            "coui_btn_close",
            "coui_navigation_close"
        )
        for (name in resNames) {
            val resId = CouiComponentFactory.resolveHostResId(context, name, "drawable")
            if (resId != 0) {
                val d = runCatching { androidx.core.content.ContextCompat.getDrawable(context, resId) }.getOrNull()
                if (d != null) {
                    val tintColor = if (isDark) Color.argb(200, 255, 255, 255) else Color.argb(180, 0, 0, 0)
                    val mutated = d.mutate()
                    mutated.setTint(tintColor)
                    return mutated
                }
            }
        }
        val sysResId = android.R.drawable.ic_menu_close_clear_cancel
        val sysD = runCatching { androidx.core.content.ContextCompat.getDrawable(context, sysResId) }.getOrNull()
        if (sysD != null) {
            val tintColor = if (isDark) Color.argb(200, 255, 255, 255) else Color.argb(180, 0, 0, 0)
            val mutated = sysD.mutate()
            mutated.setTint(tintColor)
            return mutated
        }
        return null
    }

    /**
     * Finds a visible ViewGroup whose class is [className] or a subclass of it.
     *
     * The resolver records the class declaring the toolbar layout method; the live toolbar view
     * may be an obfuscated subclass of that class.
     */
    private fun findVisibleToolbar(root: View, className: String): ViewGroup? {
        if (root !is ViewGroup) return null
        if (root.isShown &&
            root.width > 0 &&
            root.height > 0 &&
            isClassOrSubclass(root, className)
        ) {
            return root
        }
        for (i in 0 until root.childCount) {
            val found = findVisibleToolbar(root.getChildAt(i), className)
            if (found != null) return found
        }
        return null
    }

    /** Finds the toolbar view regardless of attachment, visibility, or measured size. */
    private fun findToolbarByClass(root: View, className: String): ViewGroup? {
        if (root !is ViewGroup) return null
        if (isClassOrSubclass(root, className)) return root
        for (i in 0 until root.childCount) {
            val found = findToolbarByClass(root.getChildAt(i), className)
            if (found != null) return found
        }
        return null
    }

    private fun isClassOrSubclass(view: View, className: String): Boolean {
        var clazz: Class<*>? = view.javaClass
        while (clazz != null) {
            if (clazz.name == className) return true
            clazz = clazz.superclass
        }
        return false
    }

    /**
     * Keeps every remote InlineContentView surface clipped to the scroll viewport.
     *
     * SurfaceView content is composed above the host canvas, so the HorizontalScrollView's
     * normal child clipping does not hide the parts of a bubble that scroll out of view.
     * [View.getLocalVisibleRect] already accounts for ancestor scroll, padding and clipping, so
     * the same rectangle can be applied to the host SurfaceView and to the provider's child
     * surface.
     */
    private fun setupSurfaceClipTracking(scroll: View) {
        scrollClipListener?.let { listener ->
            runCatching { scroll.viewTreeObserver.removeOnScrollChangedListener(listener) }
        }
        val listener = ViewTreeObserver.OnScrollChangedListener { updateSurfaceClips() }
        runCatching { scroll.viewTreeObserver.addOnScrollChangedListener(listener) }
        scrollClipListener = listener
        scroll.post { updateSurfaceClips() }
    }

    private fun updateSurfaceClips() {
        val rect = Rect()
        for (ref in activeInlineViews) {
            val content = ref.get() ?: continue
            if (!content.isAttachedToWindow) continue
            rect.setEmpty()
            val visible = runCatching { content.getLocalVisibleRect(rect) }.getOrDefault(false)
            val clip = if (visible && rect.width() > 0 && rect.height() > 0) Rect(rect) else null
            content.setClipBounds(clip ?: Rect(0, 0, 0, 0))
            val cropped = cropChildSurface(content, clip)
            val parent = content.parent as? View
            if (cropped) {
                parent?.visibility = View.VISIBLE
                content.visibility = View.VISIBLE
            } else {
                // The remote child surface cannot be cropped on this build. Never let it draw
                // outside the viewport: hide partially visible bubbles until they are fully in.
                val fullyVisible = clip != null &&
                    clip.left <= 0 &&
                    clip.top <= 0 &&
                    clip.right >= content.width &&
                    clip.bottom >= content.height
                val visibility = if (fullyVisible) View.VISIBLE else View.INVISIBLE
                parent?.visibility = visibility
                content.visibility = visibility
            }
        }
    }

    /**
     * Crops the remote child surface (SurfaceControlViewHost package) to the visible part.
     *
     * Clipping only the InlineContentView/SurfaceView host does not crop the provider surface
     * layer; scrolled-out text otherwise keeps drawing above the toolbar and the fixed X.
     */
    private fun cropChildSurface(content: InlineContentView, clip: Rect?): Boolean {
        val surfaceView = content.getChildAt(0) as? SurfaceView ?: return true
        val packageObject = runCatching {
            SurfaceView::class.java.getMethod("getChildSurfacePackage").invoke(surfaceView)
        }.getOrNull()
        val childSurface = packageObject?.let { pkg ->
            runCatching {
                pkg.javaClass.getMethod("getSurfaceControl").invoke(pkg) as? SurfaceControl
            }.getOrNull()
        }
        val hostSurface = runCatching { content.getSurfaceControl() }.getOrNull()
        if (childSurface == null && hostSurface == null) return true
        val radius = 16 * content.resources.displayMetrics.density
        val applied = runCatching {
            SurfaceControl.Transaction().use { transaction ->
                if (clip != null) {
                    childSurface?.let { surface ->
                        transaction.setCrop(surface, clip)
                        transaction.setVisibility(surface, true)
                        setSurfaceCornerRadius(transaction, surface, radius)
                    }
                    // The host SurfaceView's crop is set by setClipBounds(); rounding its
                    // crop also rounds the child provider surface where the platform clips
                    // children to the parent surface.
                    hostSurface?.let { setSurfaceCornerRadius(transaction, it, radius) }
                } else {
                    childSurface?.let { transaction.setVisibility(it, false) }
                }
                transaction.apply()
            }
            true
        }.getOrDefault(false)
        if (!applied && !childSurfaceCropFailureLogged) {
            childSurfaceCropFailureLogged = true
            Log.w(TAG, "Child surface crop is unavailable on this build; hiding partial bubbles")
        }
        return applied
    }

    /**
     * Rounds a surface's visible crop to the bubble radius. The crop above defines the visible
     * slice, so a bubble clipped by the scroll viewport keeps a rounded cut edge.
     */
    private fun setSurfaceCornerRadius(
        transaction: SurfaceControl.Transaction,
        surfaceControl: SurfaceControl,
        radius: Float
    ) {
        val applied = runCatching {
            SurfaceControl.Transaction::class.java
                .getMethod(
                    "setCornerRadius",
                    SurfaceControl::class.java,
                    Float::class.javaPrimitiveType!!
                )
                .invoke(transaction, surfaceControl, radius)
            true
        }.getOrDefault(false)
        if (!applied && !childSurfaceCornerFailureLogged) {
            childSurfaceCornerFailureLogged = true
            Log.w(TAG, "Surface corner radius is unavailable on this build")
        }
    }

    /**
     * Moves the host scroll container's start constraint to the top-bar parent.
     *
     * The stock scroll view is constrained after the left suitcase button, so hiding that
     * button alone still leaves its inset. The end constraint is intentionally kept against the
     * right stock button (which is replaced by the fixed close), so the scroll ends at the X.
     */
    private fun expandScrollStart(scroll: View, module: XposedModule?) {
        val lp = scroll.layoutParams
        val cls = lp.javaClass
        runCatching {
            fun field(name: String) = runCatching { cls.getField(name) }.getOrNull()
            savedScrollStartToStart = field("startToStart")?.getInt(lp)
            savedScrollStartToEnd = field("startToEnd")?.getInt(lp)
            savedScrollLeftToLeft = field("leftToLeft")?.getInt(lp)
            savedScrollLeftToRight = field("leftToRight")?.getInt(lp)
            field("startToEnd")?.setInt(lp, -1)
            field("startToStart")?.setInt(lp, 0)
            field("leftToRight")?.setInt(lp, -1)
            field("leftToLeft")?.setInt(lp, 0)
            scroll.layoutParams = lp
            scroll.requestLayout()
        }.onFailure {
            module?.log(Log.WARN, TAG, "Failed to expand top scroll to parent start", it)
        }
    }

    private fun restoreScrollStart(scroll: View) {
        val lp = scroll.layoutParams
        val cls = lp.javaClass
        runCatching {
            fun field(name: String) = runCatching { cls.getField(name) }.getOrNull()
            savedScrollStartToStart?.let { field("startToStart")?.setInt(lp, it) }
            savedScrollStartToEnd?.let { field("startToEnd")?.setInt(lp, it) }
            savedScrollLeftToLeft?.let { field("leftToLeft")?.setInt(lp, it) }
            savedScrollLeftToRight?.let { field("leftToRight")?.setInt(lp, it) }
            scroll.layoutParams = lp
            scroll.requestLayout()
        }
        savedScrollStartToStart = null
        savedScrollStartToEnd = null
        savedScrollLeftToLeft = null
        savedScrollLeftToRight = null
    }

    /**
     * Adds the close as a fixed child of the stock ConstraintLayout top bar.
     *
     * Copying the right ToolItemView's LayoutParams directly is not reliable (the copy
     * constructor/visibility rules can drop the constraints and place the view at 0,0). Build a
     * fresh layout params from the stock end button's runtime params class instead. The nested
     * `ConstraintLayout.LayoutParams` class name is R8-renamed in the host APK, and
     * `generateLayoutParams(null)` throws because the constructor requires layout_width/height.
     */
    private fun addFixedCloseButton(
        topBar: ViewGroup,
        close: View,
        rightButton: View?,
        density: Float,
        module: XposedModule?
    ): Boolean {
        val rightLp = rightButton?.layoutParams
        val width = rightLp?.width?.takeIf { it > 0 }
            ?: rightButton?.width?.takeIf { it > 0 }
            ?: (40 * density).toInt()
        val height = rightLp?.height?.takeIf { it > 0 }
            ?: rightButton?.height?.takeIf { it > 0 }
            ?: (40 * density).toInt()
        val marginEnd = (rightLp as? ViewGroup.MarginLayoutParams)?.marginEnd ?: 0

        return runCatching {
            val lpClass = rightLp?.javaClass ?: return@runCatching false
            val lp = lpClass
                .getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .newInstance(width, height) as ViewGroup.LayoutParams
            lp.width = width
            lp.height = height
            (lp as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.marginEnd = marginEnd
                params.rightMargin = marginEnd
            }
            fun set(name: String, value: Int): Boolean =
                runCatching {
                    lpClass.getField(name).setInt(lp, value)
                    true
                }.getOrDefault(false)

            set("startToStart", -1)
            set("startToEnd", -1)
            set("endToStart", -1)
            val pinned = set("endToEnd", 0) &&
                set("topToTop", 0) &&
                set("bottomToBottom", 0)
            if (!pinned) return@runCatching false
            set("topToBottom", -1)
            set("bottomToTop", -1)
            topBar.addView(close, lp)
            true
        }.onFailure {
            module?.log(Log.WARN, TAG, "Failed to add fixed close to stock top bar", it)
        }.getOrDefault(false)
    }

    /** Hides the stock left/right toolbar buttons and returns their top-bar parent. */
    private fun hideToolbarChrome(container: ViewGroup): ViewGroup? {
        val context = container.context
        val leftId = CouiComponentFactory.resolveHostResId(context, "toolbar_iv_box", "id")
        val rightId = CouiComponentFactory.resolveHostResId(context, "toolbar_iv_close", "id")
        val ids = listOf(leftId, rightId).filter { it != 0 }
        if (ids.isEmpty()) return null
        var topBar: ViewGroup? = container.parent as? ViewGroup
        while (topBar != null) {
            val candidate = topBar
            if (ids.any { candidate.findViewById<View>(it) != null }) break
            topBar = candidate.parent as? ViewGroup
        }
        val bar = topBar ?: return null
        hiddenToolbarChrome.clear()

        fun hide(id: Int, targetVisibility: Int) {
            val view = bar.findViewById<View>(id) ?: return
            if (view.visibility != targetVisibility) {
                hiddenToolbarChrome.add(Pair(WeakReference(view), view.visibility))
                view.visibility = targetVisibility
            }
        }

        // Left button: remove its space; the scroll view is moved to the parent start.
        if (leftId != 0) hide(leftId, View.GONE)
        // Right button: keep its measured space (INVISIBLE, not GONE) so the scroll view ends
        // before the X. A fresh close view is pinned over the same end slot.
        if (rightId != 0) hide(rightId, View.INVISIBLE)
        return bar
    }

    /**
     * Resolves the stock ToolItemView background color from the keyboard theme.
     *
     * The stock controls use the `iconHeaderToolItemNormal` attr (a normal/pressed
     * StateListDrawable). Sampling the drawable center gives the same light/dark surface color
     * as the circular toolbar buttons.
     */
    fun stockToolBackgroundColor(context: Context, isDark: Boolean): Int {
        val sampled = runCatching { sampleStockToolColor(context) }.getOrNull()
        return sampled ?: if (isDark) Color.rgb(44, 44, 46) else Color.WHITE
    }

    private fun resolveStockToolBackground(context: Context): Drawable? {
        val attrId = CouiComponentFactory.resolveHostResId(context, "iconHeaderToolItemNormal", "attr")
        if (attrId == 0) return null
        val ta = context.theme.obtainStyledAttributes(intArrayOf(attrId))
        val drawable = runCatching { ta.getDrawable(0) }.getOrNull()
        ta.recycle()
        return drawable
    }

    private fun sampleStockToolColor(context: Context): Int? {
        val drawable = resolveStockToolBackground(context) ?: return null
        val size = 32
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        runCatching { drawable.setState(intArrayOf(android.R.attr.state_enabled)) }
        drawable.draw(canvas)
        val color = bitmap.getPixel(size / 2, size / 2)
        bitmap.recycle()
        return color.takeIf { Color.alpha(it) > 0 }
    }

    /**
     * Clips the remote inline surface to a rounded rectangle.
     *
     * InlineContentView is SurfaceView-backed; a View outline alone does not clip the surface.
     * SurfaceView#setCornerRadius (hidden but present since Android 12) rounds both the surface
     * and its hole. The SurfaceControl transaction is a fallback for builds where hidden-API
     * reflection is restricted.
     */
    private fun applySurfaceCornerRadius(content: InlineContentView, radius: Float) {
        val surfaceView = content.getChildAt(0) as? SurfaceView ?: return
        val applied = runCatching {
            SurfaceView::class.java
                .getMethod("setCornerRadius", Float::class.javaPrimitiveType!!)
                .invoke(surfaceView, radius)
        }.isSuccess
        if (applied) return

        val fallback = runCatching {
            val surfaceControl = content.getSurfaceControl() ?: return@runCatching false
            val transactionClass = Class.forName("android.view.SurfaceControl\$Transaction")
            val transaction = transactionClass.getDeclaredConstructor().newInstance()
            transactionClass.getMethod(
                "setCornerRadius",
                SurfaceControl::class.java,
                Float::class.javaPrimitiveType!!
            ).invoke(transaction, surfaceControl, radius)
            transactionClass.getMethod("apply").invoke(transaction)
            true
        }.getOrDefault(false)

        if (!fallback && !cornerClipFailureLogged) {
            cornerClipFailureLogged = true
            Log.w(TAG, "Inline surface corner-radius clipping is unavailable on this build")
        }
    }

    /**
     * Dark theme check, delegated to [KeyboardThemeColors].
     * Results come from the host probe with a uiMode fallback.
     */
    private fun isDarkTheme(context: Context): Boolean =
        io.github.ra1nify.realos.opluskeyboard.KeyboardThemeColors.isDarkTheme(context, context.classLoader)

}
