package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui

/**
 * Shared spec for the keyboard live material reference (Bugdroid robot).
 *
 * Constants only, no Compose / View dependency. Used by both render paths:
 * - Compose main path: [AndroidBotPreview]
 * - View fallback path: `CouiComponentFactory.createAndroidBotView`
 *
 * Base color is Bugdroid green `#3DDC84`, HSV = (146.8°, 0.723, 0.863).
 * Hue rotation moves H only; S and V stay fixed; the robot reads as the same
 * solid block at every hue.
 */
internal object AndroidBotSpec {

    /** Robot height (dp). The original implementation used 160. */
    const val SIZE_DP = 160

    /** Time for one full hue rotation; 18s equals 20° per second. */
    const val HUE_PERIOD_MS = 18_000L

    /** Base color `#3DDC84` hue (degrees). */
    const val BASE_HUE = 146.8f

    /** Base color saturation; fixed, does not follow the hue. */
    const val SATURATION = 0.723f

    /** Base color value; fixed, does not follow the hue. */
    const val VALUE = 0.863f

    /**
     * Eyes are cut out of the head layer with `BlendMode.Clear` (Compose) /
     * `PorterDuff.Mode.CLEAR` (View); the page background shows through.
     */

    /** Eye radius relative to the head radius. */
    const val EYE_RADIUS_RATIO = 0.12f

    /** Eye horizontal offset relative to the head radius. */
    const val EYE_OFFSET_RATIO = 0.44f

    /** Eye vertical position relative to the head radius (negative = above the head center). */
    const val EYE_VERTICAL_RATIO = -0.42f
}
