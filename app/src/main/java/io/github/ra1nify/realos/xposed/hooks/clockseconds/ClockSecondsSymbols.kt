package io.github.ra1nify.realos.xposed.hooks.clockseconds

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Validated Settings symbols for the clock-seconds menu. */
internal class ClockSecondsSymbols(
    val getItems: Method,
    val applyMode: Method,
    val statusIconClass: Class<*>,
    val statusIconConstructor: Constructor<*>,
    val statusIconTitleField: Field,
    val statusIconDialogItemField: Field,
    val dialogItemSettingsValue: Method,
)
