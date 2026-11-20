package io.github.ra1nify.realos.camera

/**
 * Camera settings page launch contract.
 *
 * The realOS app side (launching the page) and the host-side hook (reading launch parameters)
 * share these two keys; they live in a package that is not scope-trimmed — `xposed/hooks/camera`
 * is excluded from compilation when `realos.scope.camera=0` and the app side must not depend on it.
 */
object CameraIntentContract {

    /** Asks the host to open the settings page and switch directly to the realOS tab */
    const val EXTRA_OPEN_REALOS_TAB = "realos_open_realos_tab"

    /** The Bundle the host puts launch parameters into */
    const val INTENT_DATA_KEY = "camera_intent_data"
}
