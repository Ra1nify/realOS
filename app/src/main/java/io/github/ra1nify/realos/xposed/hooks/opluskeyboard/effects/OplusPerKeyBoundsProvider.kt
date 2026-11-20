package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects

import android.graphics.RectF
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.OplusInputSymbols
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-key key-face provider built from resolved per-key symbols.
 *
 * Data flow:
 *   keyboardView local touch coordinates (localX, localY)
 *     -> model getter returns the model instance
 *     -> hit-test (int, int) returns the entity (read-only, no side effects)
 *     -> four int rect fields read from the entity (in the locked field order)
 *     -> assembled into a RectF in keyboardView local coordinates
 *
 * Constraints:
 * - All reflection objects are restored once at construction; callbacks only read fields,
 *   with no scanning, lookups, or IO.
 * - Field order: `rectFields` follows the symbol declaration order `[left, right, top, bottom]`
 *   (see `OplusInputSymbols.perkey{Left,Right,Top,Bottom}Field`). Values are read in that order;
 *   a runtime self-check detects a mismatch and switches to the healed order below.
 * - The first hit runs a runtime self-check: the hit point lies inside the rect (with tolerance)
 *   and the rect size matches one key rather than the whole screen. A pass locks the order;
 *   a fail returns null for that call and the next key is tried again.
 * - Any exception returns null (fail-closed) and leaves the IME's original handling intact.
 *
 * ## Self-check and healed order
 *
 * "Rect contains the touch point" alone matches several of the 6 C(4,2) X/Y splits at once.
 * Measured samples (fields `f11233b`=left, `f11234c`=right, `f11235d`=top, `e`=bottom) give the
 * declared order `(v0,v1,v2,v3)` = (left, right, top, bottom): v0<v1 and v2<v3 always hold,
 * width≈106 and height≈149 stay stable. The class tries the declared order first, then
 * enumerates the remaining orders and accepts only the unique solution passing both tests.
 */
class OplusPerKeyBoundsProvider private constructor(
    private val modelGetter: Method,
    private val hitTestMethod: Method,
    private val rectFields: Array<Field>,
    private val redrawMethod: Method?
) : OplusKeyboardEffectController.PerKeyBoundsProvider {

    /**
     * Locked field order (`intArrayOf(0,1,2,3)` means declaration order).
     * null means not yet determined; once set it does not change within this process.
     */
    @Volatile
    private var lockedOrder: IntArray? = null

    private val redrawFailureLogged = AtomicBoolean(false)
    private val boundsFailureLogged = AtomicBoolean(false)

    override fun boundsFor(keyboardView: View, localX: Float, localY: Float): RectF? {
        return targetFor(keyboardView, localX, localY)?.localBounds
    }

    override fun matchesBounds(entity: Any, bounds: RectF): Boolean {
        return try {
            val order = lockedOrder ?: return false
            val value0 = rectFields[0].getInt(entity)
            val value1 = rectFields[1].getInt(entity)
            val value2 = rectFields[2].getInt(entity)
            val value3 = rectFields[3].getInt(entity)
            val left = when (order[0]) {
                0 -> value0
                1 -> value1
                2 -> value2
                else -> value3
            }
            val right = when (order[1]) {
                0 -> value0
                1 -> value1
                2 -> value2
                else -> value3
            }
            val top = when (order[2]) {
                0 -> value0
                1 -> value1
                2 -> value2
                else -> value3
            }
            val bottom = when (order[3]) {
                0 -> value0
                1 -> value1
                2 -> value2
                else -> value3
            }
            right > left && bottom > top &&
                left.toFloat() == bounds.left &&
                right.toFloat() == bounds.right &&
                top.toFloat() == bounds.top &&
                bottom.toFloat() == bounds.bottom
        } catch (t: Throwable) {
            if (boundsFailureLogged.compareAndSet(false, true)) {
                Log.w(TAG, "Per-key entity geometry matching failed; identity matching remains active.", t)
            }
            false
        }
    }

    override fun targetFor(keyboardView: View, localX: Float, localY: Float): OplusPerKeyTarget? {
        return try {
            val model = modelGetter.invoke(keyboardView)
            if (model == null) return null
            val entity = hitTestMethod.invoke(model, localX.toInt(), localY.toInt())
            if (entity == null) return null

            val raw = intArrayOf(
                rectFields[0].getInt(entity),
                rectFields[1].getInt(entity),
                rectFields[2].getInt(entity),
                rectFields[3].getInt(entity)
            )

            val order = lockedOrder ?: resolveOrder(keyboardView, localX, localY, raw)?.also {
                lockedOrder = it
            } ?: return null

            val l = raw[order[0]]
            val r = raw[order[1]]
            val t = raw[order[2]]
            val b = raw[order[3]]
            if (r <= l || b <= t) return null

            OplusPerKeyTarget(entity, RectF(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat()))
        } catch (t: Throwable) {
            if (boundsFailureLogged.compareAndSet(false, true)) {
                Log.w(TAG, "Per-key bounds lookup failed; failing closed.", t)
            }
            null
        }
    }

    override fun supportsMotion(): Boolean = redrawMethod != null

    /**
     * The live key entity at this position; used to detect entities the host rebuilt.
     *
     * Reuses the [targetFor] hit path (model getter -> hit test -> entity) with no extra
     * reflection handles and no scanning. Returns null when the hit path fails; callers
     * treat that as "unknown" and keep the motion.
     */
    override fun entityAt(keyboardView: View, localX: Float, localY: Float): Any? =
        runCatching { targetFor(keyboardView, localX, localY)?.entity }.getOrNull()

    override fun requestRedraw(keyboardView: View): Boolean = requestRedraw(keyboardView, null)

    override fun requestRedraw(keyboardView: View, entity: Any?): Boolean {
        val method = redrawMethod ?: return false
        return runCatching {
            if (method.parameterTypes.size == 1 && entity != null) {
                method.invoke(keyboardView, entity)
            } else if (method.parameterTypes.isEmpty()) {
                method.invoke(keyboardView)
            } else {
                keyboardView.invalidate()
            }
            (keyboardView.parent as? View)?.invalidate()
            true
        }.getOrElse { t ->
            if (redrawFailureLogged.compareAndSet(false, true)) {
                Log.w(TAG, "Per-key redraw request failed; falling back to View.invalidate().", t)
            }
            keyboardView.invalidate()
            (keyboardView.parent as? View)?.invalidate()
            true
        }
    }

    /**
     * Determines the field order.
     *
     * 1. Tries the declaration order `[0,1,2,3]` first; a pass returns it.
     * 2. Otherwise enumerates the remaining permutations and takes the **unique** one that
     *    passes both the hit-point-in-rect test (with tolerance) and the single-key size
     *    test; zero or multiple solutions return null (fail-closed, retry on the next key).
     *
     * Declaration order runs first; other orders are enumerated only when it fails.
     * A unique non-declaration solution means the device/version field layout differs.
     */
    private fun resolveOrder(
        keyboardView: View,
        localX: Float,
        localY: Float,
        raw: IntArray
    ): IntArray? {
        val declared = intArrayOf(0, 1, 2, 3)
        if (validate(keyboardView, localX, localY, raw, declared)) return declared

        var found: IntArray? = null
        var count = 0
        for (order in PERMUTATIONS) {
            if (order.contentEquals(declared)) continue
            if (!validate(keyboardView, localX, localY, raw, order)) continue
            count++
            if (found == null) found = order
            if (count > 1) break
        }
        if (count != 1) {
            return null
        }
        return found
    }

    /**
     * Self-check for one permutation:
     * 1. `l<r && t<b` (rect not degenerate);
     * 2. hit point inside the rect, ~12% side tolerance (touch overshoots; sample y=156 vs bottom=148);
     * 3. size matches one key: width <= 40% of viewport width, height <= 60% of viewport height;
     * 4. coordinates within -20% .. +120% of the viewport (host shadow/spread).
     */
    private fun validate(
        keyboardView: View,
        localX: Float,
        localY: Float,
        raw: IntArray,
        order: IntArray
    ): Boolean {
        val vw = keyboardView.width.toFloat()
        val vh = keyboardView.height.toFloat()
        if (vw <= 0f || vh <= 0f) return false

        val l = raw[order[0]].toFloat()
        val r = raw[order[1]].toFloat()
        val t = raw[order[2]].toFloat()
        val b = raw[order[3]].toFloat()
        if (r <= l || b <= t) return false

        val w = r - l
        val h = b - t

        val tolX = w * 0.12f
        val tolY = h * 0.12f
        val contains = localX >= l - tolX && localX <= r + tolX &&
            localY >= t - tolY && localY <= b + tolY

        val smallEnough = w <= vw * 0.40f && h <= vh * 0.60f
        val inRange = l >= -vw * 0.20f && r <= vw * 1.20f &&
            t >= -vh * 0.20f && b <= vh * 1.20f

        return contains && smallEnough && inRange
    }

    companion object {
        private const val TAG = "OplusPerKeyBounds"
        /** All 24 permutations of the 4 fields; index 0 is the declaration order, which resolveOrder skips. */
        private val PERMUTATIONS: List<IntArray> = buildList {
            val idx = intArrayOf(0, 1, 2, 3)
            fun permute(start: Int) {
                if (start == idx.size) {
                    add(idx.copyOf())
                    return
                }
                for (i in start until idx.size) {
                    val tmp = idx[start]; idx[start] = idx[i]; idx[i] = tmp
                    permute(start + 1)
                    val tmp2 = idx[start]; idx[start] = idx[i]; idx[i] = tmp2
                }
            }
            permute(0)
        }

        /**
         * Builds the provider from resolved symbols; returns null when a required symbol is
         * missing or fails to restore. Callers then disable key blending (fail closed) while
         * background blending stays active.
         */
        fun create(classLoader: ClassLoader, symbols: OplusInputSymbols): OplusPerKeyBoundsProvider? {
            if (!symbols.isReadyForPerKey()) {
                Log.w(TAG, "Per-key symbols not ready (status=${symbols.statusMap["perkey_bounds"]}); provider disabled.")
                return null
            }

            val modelGetter = symbols.perkeyModelGetter?.restore(classLoader) ?: run {
                Log.w(TAG, "perkeyModelGetter restore failed.")
                return null
            }
            val hitTest = symbols.perkeyHitTestMethod?.restore(classLoader) ?: run {
                Log.w(TAG, "perkeyHitTestMethod restore failed.")
                return null
            }
            val fieldDescriptors = listOfNotNull(
                symbols.perkeyLeftField,
                symbols.perkeyRightField,
                symbols.perkeyTopField,
                symbols.perkeyBottomField
            )
            val rectFields = fieldDescriptors.mapNotNull { it.restore(classLoader) }.toTypedArray()
            if (rectFields.size != 4) {
                Log.w(TAG, "Per-key bounds fields restore failed (${rectFields.size}/4).")
                return null
            }

            val provider = OplusPerKeyBoundsProvider(
                modelGetter = modelGetter,
                hitTestMethod = hitTest,
                rectFields = rectFields,
                redrawMethod = symbols.perkeyRedrawMethod?.restore(classLoader)
            )
            Log.i(TAG, "Per-key bounds provider created on ${modelGetter.declaringClass?.name}")
            return provider
        }
    }
}
