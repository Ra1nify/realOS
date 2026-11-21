package io.github.ra1nify.realos.xposed.hooks.launcher

import android.graphics.Bitmap
import android.graphics.Color
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.lang.reflect.Constructor

/**
 * Bucket rule override for drawer color classification.
 *
 * The host buckets pixels in `com.android.launcher.togglebar.arrange.color.n#c` with these thresholds:
 *
 * ```
 * V >= 0.9                          → WHITE
 * V <= 0.16 || S <= 0.22            → BLACK_GREY
 * H ∈ [0,32] ∪ [270,360]            → RED
 * H ∈ [32,65]                       → YELLOW
 * H ∈ [65,177]                      → GREEN
 * H ∈ [177,270]                     → BLUE
 * ```
 *
 * The RED range spans 270° to 32°, so orange (~30°) and purple (~300°) land in RED.
 *
 * ## Replacement thresholds
 *
 * The host fixes the slot count at 6 (`ArrangeColor` enum + `boolean[6]`):
 *
 * ```
 * white    V >= 0.9 && S <= 0.15
 * gray     V <= 0.16 || S <= 0.22
 * red      H ∈ [345,360] ∪ [0,15]
 * yellow   H ∈ [15,70]    orange and yellow merged
 * green    H ∈ [70,200]   cyan included
 * blue     H ∈ [200,345]  indigo, purple and magenta included
 * ```
 *
 * Two differences from the host rules:
 *
 * 1. WHITE adds the saturation limit `S <= 0.15`; very bright fully saturated colors
 *    (such as `#FFFF00`) land in their hue bucket.
 * 2. Purple moves from RED to BLUE.
 *
 * Orange and yellow share one slot; the host provides 6 slots.
 *
 * ## Implementation
 *
 * Replaces `n.c(ItemInfo, Bitmap)`: samples 60×60, buckets every pixel by HSV,
 * picks a slot with the host's dominant-ratio strategy, and constructs the host
 * `ColorInfo` object by reflection.
 *
 * Only this method is replaced; the `DrawerIconPalette` cache, `parseColorWithBitmap`
 * writes, and pager view construction stay on host logic.
 */
class ColorBucketClassifier private constructor(
    private val colorInfoCtor: Constructor<*>,
    /** `ArrangeColor` enum instances indexed by slot: 0=BLUE, 1=RED, 2=YELLOW, 3=GREEN, 4=WHITE, 5=BLACK_GREY */
    private val arrangeColors: Array<Any>
) {

    /**
     * Classifies one icon bitmap and returns the host `ColorInfo` object.
     *
     * Returns null on failure (recycled bitmap, reflection error); the caller runs the host fallback.
     */
    fun classify(bitmap: Bitmap): Any? {
        if (bitmap.isRecycled) return null

        // HARDWARE bitmaps reject getPixels; copy to a readable config first
        val source = if (bitmap.config == Bitmap.Config.HARDWARE) {
            runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return null
        } else {
            bitmap
        }

        val scaled = runCatching {
            Bitmap.createScaledBitmap(source, SAMPLE, SAMPLE, true)
        }.getOrNull() ?: run {
            if (source !== bitmap) source.recycle()
            return null
        }

        try {
            val pixels = IntArray(SAMPLE * SAMPLE)
            scaled.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)

            val counts = IntArray(SLOT_COUNT)
            val sumH = FloatArray(SLOT_COUNT)
            val sumS = FloatArray(SLOT_COUNT)
            val sumV = FloatArray(SLOT_COUNT)
            var valid = 0

            val hsv = FloatArray(3)
            for (px in pixels) {
                if (Color.alpha(px) < ALPHA_THRESHOLD) continue
                Color.colorToHSV(px, hsv)
                val slot = bucketOf(hsv[0], hsv[1], hsv[2])
                counts[slot]++
                sumH[slot] += hsv[0]
                sumS[slot] += hsv[1]
                sumV[slot] += hsv[2]
                valid++
            }

            if (valid == 0) return null

            val chosen = decide(counts, valid)
            val n = counts[chosen]
            val h = if (n > 0) sumH[chosen] / n else 0f
            val s = if (n > 0) sumS[chosen] / n else 0f
            val v = if (n > 0) sumV[chosen] / n else 0f

            return runCatching {
                // Two details match the host exactly:
                // - the second argument is `w.e(f13, f15, f14)`, i.e. [h, v, s], not [h, s, v]
                // - the third argument is the selected bucket's pixel count; `k.java:137`
                //   compares it across colors, and 0 makes the ordering arbitrary
                colorInfoCtor.newInstance(arrangeColors[chosen], arrayListOf(h, v, s), n)
            }.getOrNull()
        } finally {
            // createScaledBitmap returns the same object at identical size; skip recycling it
            if (scaled !== source) scaled.recycle()
            if (source !== bitmap && !source.isRecycled) source.recycle()
        }
    }

    /**
     * Slot selection, identical to the host:
     *
     * 1. Among the four color slots, the largest ratio above 25% wins;
     * 2. Otherwise the slot with the most pixels wins.
     */
    private fun decide(counts: IntArray, valid: Int): Int {
        var chosen = SLOT_GRAY
        var bestRatio = 0f
        var found = false

        for (slot in COLOR_SLOTS) {
            val ratio = counts[slot].toFloat() / valid
            if (ratio > DOMINANT_RATIO && ratio > bestRatio) {
                bestRatio = ratio
                chosen = slot
                found = true
            }
        }
        if (found) return chosen

        var max = -1
        for (slot in 0 until SLOT_COUNT) {
            if (counts[slot] > max) {
                max = counts[slot]
                chosen = slot
            }
        }
        return chosen
    }

    /** Buckets one pixel and returns the slot index */
    private fun bucketOf(h: Float, s: Float, v: Float): Int = when {
        v >= WHITE_V && s <= WHITE_S -> SLOT_WHITE
        v <= DARK_V || s <= NEUTRAL_S -> SLOT_GRAY
        h >= HUE_RED_HIGH || h < HUE_RED_LOW -> SLOT_RED
        h < HUE_YELLOW_END -> SLOT_YELLOW
        h < HUE_GREEN_END -> SLOT_GREEN
        else -> SLOT_BLUE
    }

    companion object {

        private const val TAG = "RealOS-ColorBucket"

        private const val SAMPLE = 60
        private const val ALPHA_THRESHOLD = 128
        private const val SLOT_COUNT = 6

        private const val SLOT_BLUE = 0
        private const val SLOT_RED = 1
        private const val SLOT_YELLOW = 2
        private const val SLOT_GREEN = 3
        private const val SLOT_WHITE = 4
        private const val SLOT_GRAY = 5

        /** Slot to `ArrangeColor` constant name, in enum ordinal order */
        private val SLOT_NAMES = arrayOf("BLUE", "RED", "YELLOW", "GREEN", "WHITE", "BLACK_GREY")

        /** Color slots used by the dominant-ratio check: red, yellow, green, blue */
        private val COLOR_SLOTS = intArrayOf(SLOT_RED, SLOT_YELLOW, SLOT_GREEN, SLOT_BLUE)

        // ── Thresholds ──
        /** White: bright with almost no saturation */
        private const val WHITE_V = 0.9f
        private const val WHITE_S = 0.15f
        /** Dark: very low value */
        private const val DARK_V = 0.16f
        /** Gray: low saturation at any brightness */
        private const val NEUTRAL_S = 0.22f
        /** Red: narrow range across 0° */
        private const val HUE_RED_HIGH = 345f
        private const val HUE_RED_LOW = 15f
        /** Orange-yellow upper bound */
        private const val HUE_YELLOW_END = 70f
        /** Green (cyan included) upper bound */
        private const val HUE_GREEN_END = 200f
        /** Dominant-ratio threshold for color slots, identical to the host */
        private const val DOMINANT_RATIO = 0.25f

        private const val ALGO_CLASS = "com.android.launcher.togglebar.arrange.color.n"
        private const val COLOR_INFO_CLASS = "com.android.launcher.togglebar.arrange.color.a"
        private const val ARRANGE_COLOR_CLASS = "com.android.launcher.togglebar.arrange.color.ArrangeColor"

        /**
         * Resolves the required symbols; any failure returns null and the caller keeps host behavior.
         *
         * All lookups use stable features, never obfuscated field names:
         * - `ArrangeColor.valueOf(String)` returns instances by constant name;
         * - the `ColorInfo` constructor is located by parameter types `(ArrangeColor, ArrayList, int)`.
         */
        fun create(classLoader: ClassLoader): ColorBucketClassifier? = runCatching {
            val arrangeColorClass = Class.forName(ARRANGE_COLOR_CLASS, false, classLoader)
            val colorInfoClass = Class.forName(COLOR_INFO_CLASS, false, classLoader)

            val valueOf = arrangeColorClass.getMethod("valueOf", String::class.java)
            val colors = SLOT_NAMES.map { name ->
                valueOf.invoke(null, name) ?: error("ArrangeColor.valueOf($name) returned null")
            }.toTypedArray()

            val ctor = colorInfoClass.getDeclaredConstructor(
                arrangeColorClass,
                java.util.ArrayList::class.java,
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }

            ColorBucketClassifier(ctor, colors)
        }.getOrNull()

        /** Class name of the bucketing algorithm, used by callers to locate the hook point */
        fun algorithmClassName(): String = ALGO_CLASS

        fun logInstalled(module: io.github.libxposed.api.XposedModule) {
            module.log(
                Log.INFO, TAG,
                "Bucket rule: red[345,360] and [0,15] orange-yellow[15,70] green[70,200] blue[200,345] white(V>=0.9,S<=0.15) black-grey(V<=0.16||S<=0.22)"
            )
        }
    }
}
