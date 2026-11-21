package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects

import android.content.Context
import android.graphics.Color
import androidx.annotation.StringRes
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText
import kotlin.math.abs
import kotlin.random.Random

/**
 * Samsung Keys Cafe preset models, animation types, and authentic color palettes.
 * Extracted from Honeyboard & Keys Cafe resources (res/values/arrays.xml).
 * Resolves titles and summaries via OplusWorkshopUiText from module XML string resources.
 */

enum class BoardEffectType(
    val key: String,
    @StringRes private val titleRes: Int,
    @StringRes private val summaryRes: Int
) {
    RING_SPREAD(
        "RING_SPREAD",
        R.string.oplus_input_effect_board_ring_spread_title,
        R.string.oplus_input_effect_board_ring_spread_summary
    ),
    BLOB_SPREAD(
        "BLOB_SPREAD",
        R.string.oplus_input_effect_board_blob_spread_title,
        R.string.oplus_input_effect_board_blob_spread_summary
    ),
    SPARK(
        "SPARK",
        R.string.oplus_input_effect_board_spark_title,
        R.string.oplus_input_effect_board_spark_summary
    ),
    OUTLINE(
        "OUTLINE",
        R.string.oplus_input_effect_board_outline_title,
        R.string.oplus_input_effect_board_outline_summary
    ),
    BLOB(
        "BLOB",
        R.string.oplus_input_effect_board_blob_title,
        R.string.oplus_input_effect_board_blob_summary
    ),
    SPARK_SPIN(
        "SPARK_SPIN",
        R.string.oplus_input_effect_board_spark_spin_title,
        R.string.oplus_input_effect_board_spark_spin_summary
    ),
    PAINT_GUN(
        "PAINT_GUN",
        R.string.oplus_input_effect_board_paint_gun_title,
        R.string.oplus_input_effect_board_paint_gun_summary
    ),
    FIRE_CRACKER(
        "FIRE_CRACKER",
        R.string.oplus_input_effect_board_fire_cracker_title,
        R.string.oplus_input_effect_board_fire_cracker_summary
    ),
    NONE(
        "NONE",
        R.string.oplus_input_effect_board_none_title,
        R.string.oplus_input_effect_board_none_summary
    );

    fun getTitle(context: Context): String = OplusWorkshopUiText.getString(context, titleRes)
    fun getSummary(context: Context): String = OplusWorkshopUiText.getString(context, summaryRes)

    companion object {
        fun fromKey(key: String): BoardEffectType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: RING_SPREAD
    }
}

enum class KeyEffectType(
    val key: String,
    @StringRes private val titleRes: Int,
    @StringRes private val summaryRes: Int
) {
    COLOR_FLASH(
        "COLOR_FLASH",
        R.string.oplus_input_effect_key_color_flash_title,
        R.string.oplus_input_effect_key_color_flash_summary
    ),
    OUTLINE(
        "OUTLINE",
        R.string.oplus_input_effect_key_outline_title,
        R.string.oplus_input_effect_key_outline_summary
    ),
    BLOB(
        "BLOB",
        R.string.oplus_input_effect_key_blob_title,
        R.string.oplus_input_effect_key_blob_summary
    ),
    BLOB_SPREAD(
        "BLOB_SPREAD",
        R.string.oplus_input_effect_key_blob_spread_title,
        R.string.oplus_input_effect_key_blob_spread_summary
    ),
    SPARK_SPIN(
        "SPARK_SPIN",
        R.string.oplus_input_effect_key_spark_spin_title,
        R.string.oplus_input_effect_key_spark_spin_summary
    ),
    FOOT_PRINT(
        "FOOT_PRINT",
        R.string.oplus_input_effect_key_foot_print_title,
        R.string.oplus_input_effect_key_foot_print_summary
    ),
    NONE(
        "NONE",
        R.string.oplus_input_effect_key_none_title,
        R.string.oplus_input_effect_key_none_summary
    );

    fun getTitle(context: Context): String = OplusWorkshopUiText.getString(context, titleRes)
    fun getSummary(context: Context): String = OplusWorkshopUiText.getString(context, summaryRes)

    companion object {
        fun fromKey(key: String): KeyEffectType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: COLOR_FLASH
    }
}

/**
 * Icon options for the key FOOT_PRINT effect. Maps to Samsung `HoneyTeaSettingValue.KeyFootPrint.list`:
 * `{CAT, DOG, BEAR, DUCK, FROG, HORSE, PIG, MAN, WOMAN, CUSTOM}` (10 entries).
 *
 * - The first 9 entries are module-bundled **monochrome vectors** (`#010101`, 200dp, viewport 200);
 *   rendering tints them with `PorterDuffColorFilter(color, SRC_IN)` (same as the Samsung `isCustomIcon()==false` branch).
 * - `CUSTOM` is a user-imported PNG. Samsung rule: **custom icons are not tinted** (drawn as-is).
 *   The render side reads [OplusInputPrefs.getFootPrintIcon] for the CUSTOM check.
 */
enum class FootPrintIcon(
    val key: String,
    val drawableRes: Int,
    @StringRes private val titleRes: Int
) {
    CAT("CAT", R.drawable.foot_print_cat, R.string.oplus_input_foot_print_cat),
    DOG("DOG", R.drawable.foot_print_dog, R.string.oplus_input_foot_print_dog),
    BEAR("BEAR", R.drawable.foot_print_bear, R.string.oplus_input_foot_print_bear),
    DUCK("DUCK", R.drawable.foot_print_duck, R.string.oplus_input_foot_print_duck),
    FROG("FROG", R.drawable.foot_print_frog, R.string.oplus_input_foot_print_frog),
    HORSE("HORSE", R.drawable.foot_print_horse, R.string.oplus_input_foot_print_horse),
    PIG("PIG", R.drawable.foot_print_pig, R.string.oplus_input_foot_print_pig),
    MAN("MAN", R.drawable.foot_print_man, R.string.oplus_input_foot_print_man),
    WOMAN("WOMAN", R.drawable.foot_print_woman, R.string.oplus_input_foot_print_woman),
    CUSTOM("CUSTOM", R.drawable.foot_print_cat, R.string.oplus_input_foot_print_custom);

    /** CUSTOM is a user-imported bitmap (untinted); the rest are bundled vectors (tinted). */
    val isCustom: Boolean get() = this == CUSTOM

    fun getTitle(context: Context): String = OplusWorkshopUiText.getString(context, titleRes)

    companion object {
        fun fromKey(key: String): FootPrintIcon =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: CAT
    }
}

enum class MotionEffectType(
    val key: String,
    @StringRes private val titleRes: Int,
    @StringRes private val summaryRes: Int
) {
    SPRING_DOWN(
        "SPRING_DOWN",
        R.string.oplus_input_effect_motion_spring_down_title,
        R.string.oplus_input_effect_motion_spring_down_summary
    ),
    SPRING_UP(
        "SPRING_UP",
        R.string.oplus_input_effect_motion_spring_up_title,
        R.string.oplus_input_effect_motion_spring_up_summary
    ),
    BOUNCE_UP(
        "BOUNCE_UP",
        R.string.oplus_input_effect_motion_bounce_up_title,
        R.string.oplus_input_effect_motion_bounce_up_summary
    ),
    BOUNCE_TOUCH(
        "BOUNCE_TOUCH",
        R.string.oplus_input_effect_motion_bounce_touch_title,
        R.string.oplus_input_effect_motion_bounce_touch_summary
    ),
    TILT(
        "TILT",
        R.string.oplus_input_effect_motion_tilt_title,
        R.string.oplus_input_effect_motion_tilt_summary
    ),
    NONE(
        "NONE",
        R.string.oplus_input_effect_motion_none_title,
        R.string.oplus_input_effect_motion_none_summary
    );

    fun getTitle(context: Context): String = OplusWorkshopUiText.getString(context, titleRes)
    fun getSummary(context: Context): String = OplusWorkshopUiText.getString(context, summaryRes)

    companion object {
        fun fromKey(key: String): MotionEffectType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SPRING_DOWN
    }
}

enum class EffectColorPreset(
    val key: String,
    @StringRes private val titleRes: Int,
    val colors: IntArray
) {
    POPSICLE(
        "POPSICLE",
        R.string.oplus_input_effect_color_popsicle,
        intArrayOf(
            0xFFF3533A.toInt(),
            0xFFFA9F42.toInt(),
            0xFF8AD879.toInt(),
            0xFF5ACFC9.toInt()
        )
    ),
    KERMIT(
        "KERMIT",
        R.string.oplus_input_effect_color_kermit,
        intArrayOf(
            0xFF75BDE0.toInt(),
            0xFF78D1D2.toInt(),
            0xFF97DBAE.toInt(),
            0xFFCDE4AD.toInt()
        )
    ),
    PASTEL(
        "PASTEL",
        R.string.oplus_input_effect_color_pastel,
        intArrayOf(
            0xFF91D2DB.toInt(),
            0xFFE9A7C6.toInt(),
            0xFF8ED1B7.toInt(),
            0xFFFECB52.toInt()
        )
    ),
    SUNRISE(
        "SUNRISE",
        R.string.oplus_input_effect_color_sunrise,
        intArrayOf(
            0xFF5F236B.toInt(),
            0xFFBE375F.toInt(),
            0xFFED8554.toInt(),
            0xFFF5E86D.toInt()
        )
    ),
    FOUR_SEASONS(
        "FOUR_SEASONS",
        R.string.oplus_input_effect_color_four_seasons,
        intArrayOf(
            0xFF2E9599.toInt(),
            0xFFF7DC68.toInt(),
            0xFFF46C3F.toInt(),
            0xFFA7226F.toInt()
        )
    ),
    OCEAN(
        "OCEAN",
        R.string.oplus_input_effect_color_ocean,
        intArrayOf(
            0xFF345DA7.toInt(),
            0xFF3B8AC4.toInt(),
            0xFF4BB4DE.toInt(),
            0xFFEFDBCB.toInt()
        )
    ),
    EMBERS(
        "EMBERS",
        R.string.oplus_input_effect_color_embers,
        intArrayOf(
            0xFF41436A.toInt(),
            0xFF984063.toInt(),
            0xFFF64668.toInt(),
            0xFFFE9677.toInt()
        )
    ),
    RANDOM(
        "RANDOM",
        R.string.oplus_input_effect_color_random,
        intArrayOf()
    ),
    CUSTOM(
        "CUSTOM",
        R.string.oplus_input_effect_color_custom,
        intArrayOf()
    );

    fun getTitle(context: Context): String = OplusWorkshopUiText.getString(context, titleRes)

    fun pickColor(index: Int, customColors: IntArray? = null): Int {
        val palette = if (this == CUSTOM && customColors != null && customColors.isNotEmpty()) {
            customColors
        } else {
            colors
        }
        if (palette.isEmpty()) {
            val hue = (Random.nextFloat() * 360f)
            return Color.HSVToColor(floatArrayOf(hue, 0.72f, 0.96f))
        }
        return palette[abs(index) % palette.size]
    }

    fun pickColor(index: Int, context: Context): Int {
        return if (this == CUSTOM) {
            val list = OplusInputPrefs.getCustomPalette(context)
            if (list.isNotEmpty()) {
                list[abs(index) % list.size]
            } else {
                val hue = (Random.nextFloat() * 360f)
                Color.HSVToColor(floatArrayOf(hue, 0.72f, 0.96f))
            }
        } else {
            pickColor(index)
        }
    }

    companion object {
        fun fromKey(key: String): EffectColorPreset =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: POPSICLE
    }
}
