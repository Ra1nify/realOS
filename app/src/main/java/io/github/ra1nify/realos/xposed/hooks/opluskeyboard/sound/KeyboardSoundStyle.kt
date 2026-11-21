package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.sound

import android.content.Context
import androidx.annotation.StringRes
import io.github.ra1nify.realos.R
import io.github.ra1nify.realos.opluskeyboard.OplusWorkshopUiText

/**
 * Key classes (Samsung Keys Cafe 3-track split).
 */
enum class SoundKeyType {
    STANDARD,  // letters, digits, plain punctuation
    DELETE,    // backspace key
    FUNCTION   // space, enter, shift, symbol keyboard keys
}

/**
 * Mechanical key sound preset styles (Samsung Keys Cafe stock sounds + Water Drop).
 */
enum class KeyboardSoundStyle(
    val key: String,
    @StringRes private val titleRes: Int,
    @StringRes private val summaryRes: Int,
    val standardFile: String,
    val deleteFile: String,
    val functionFile: String
) {
    RETRO(
        "RETRO",
        R.string.oplus_input_sound_retro_title,
        R.string.oplus_input_sound_retro_summary,
        "retro_keyboard.ogg",
        "retro_keyboard_back.ogg",
        "retro_keyboard_function.ogg"
    ),
    CALM(
        "CALM",
        R.string.oplus_input_sound_calm_title,
        R.string.oplus_input_sound_calm_summary,
        "calm_keyboard.ogg",
        "calm_keyboard_back.ogg",
        "calm_keyboard_function.ogg"
    ),
    FUN(
        "FUN",
        R.string.oplus_input_sound_fun_title,
        R.string.oplus_input_sound_fun_summary,
        "fun_keyboard.ogg",
        "fun_keyboard_back.ogg",
        "fun_keyboard_function.ogg"
    ),
    SPACE(
        "SPACE",
        R.string.oplus_input_sound_space_title,
        R.string.oplus_input_sound_space_summary,
        "space_keyboard.ogg",
        "space_keyboard_back.ogg",
        "space_keyboard.ogg"
    ),
    PINK(
        "PINK",
        R.string.oplus_input_sound_pink_title,
        R.string.oplus_input_sound_pink_summary,
        "pink_keyboard.ogg",
        "pink_keyboard_back.ogg",
        "pink_keyboard.ogg"
    ),
    WATER_DROP(
        "WATER_DROP",
        R.string.oplus_input_sound_water_drop_title,
        R.string.oplus_input_sound_water_drop_summary,
        "water_drop_sound.mp3",
        "water_drop_sound.mp3",
        "water_drop_sound.mp3"
    );

    fun getTitle(context: Context): String = OplusWorkshopUiText.getString(context, titleRes)
    fun getSummary(context: Context): String = OplusWorkshopUiText.getString(context, summaryRes)

    companion object {
        fun fromKey(key: String?): KeyboardSoundStyle =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: RETRO
    }
}
