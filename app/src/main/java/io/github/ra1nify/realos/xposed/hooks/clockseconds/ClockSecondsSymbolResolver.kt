package io.github.ra1nify.realos.xposed.hooks.clockseconds

import android.content.Context
import java.lang.reflect.Modifier

/** Resolves and validates the Settings clock-seconds menu symbols. */
internal object ClockSecondsSymbolResolver {

    private const val CONTROLLER_CLASS =
        "com.oplus.settings.feature.notification.controller.ClockSecondsModePreferenceController"
    private const val STATUS_ICON_CLASS =
        "com.oplus.settings.feature.notification.controller.StatusIconBottomSheetDialog\$StatusIcon"

    fun resolve(loader: ClassLoader): ClockSecondsSymbols {
        val controller = Class.forName(CONTROLLER_CLASS, false, loader)

        val getItems = controller.getDeclaredMethod("getItems").apply { isAccessible = true }
        require(List::class.java.isAssignableFrom(getItems.returnType)) {
            "$CONTROLLER_CLASS#getItems must return List, was ${getItems.returnType.name}"
        }

        val applyMode = controller.getDeclaredMethod(
            "applyMode",
            Context::class.java,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        require(Modifier.isStatic(applyMode.modifiers)) {
            "$CONTROLLER_CLASS#applyMode must be static"
        }
        require(applyMode.returnType == Void.TYPE) {
            "$CONTROLLER_CLASS#applyMode must return void, was ${applyMode.returnType.name}"
        }

        val statusIcon = Class.forName(STATUS_ICON_CLASS, false, loader)
        val statusIconConstructor = statusIcon.declaredConstructors.firstOrNull {
            it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1].name.endsWith("StatusIconDialogItem")
        }?.apply { isAccessible = true } ?: error(
            "$STATUS_ICON_CLASS(String, StatusIconDialogItem) not found"
        )

        val titleField = statusIcon.getField("mTitle")
        require(titleField.type == String::class.java) {
            "$STATUS_ICON_CLASS#mTitle must be String, was ${titleField.type.name}"
        }

        val dialogItemClass = statusIconConstructor.parameterTypes[1]
        val dialogItemField = statusIcon.getField("mStatusIconDialogItem")
        require(dialogItemField.type == dialogItemClass) {
            "$STATUS_ICON_CLASS#mStatusIconDialogItem has type ${dialogItemField.type.name}, " +
                "expected ${dialogItemClass.name}"
        }

        val dialogItemSettingsValue = dialogItemClass.getMethod("getSettingsValue").apply {
            isAccessible = true
        }
        require(dialogItemSettingsValue.returnType == Int::class.javaPrimitiveType) {
            "${dialogItemClass.name}#getSettingsValue must return int, " +
                "was ${dialogItemSettingsValue.returnType.name}"
        }

        return ClockSecondsSymbols(
            getItems = getItems,
            applyMode = applyMode,
            statusIconClass = statusIcon,
            statusIconConstructor = statusIconConstructor,
            statusIconTitleField = titleField,
            statusIconDialogItemField = dialogItemField,
            dialogItemSettingsValue = dialogItemSettingsValue,
        )
    }
}
