package io.github.ra1nify.realos.camera

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.root.RootManager
import io.github.ra1nify.realos.root.RootState
import io.github.ra1nify.realos.xposed.loader.SpecPackages

/**
 * Launches the camera settings page from the realOS app side.
 *
 * `CameraSettingActivity` declares `oplus.permission.OPLUS_COMPONENT_SAFE`;
 * a direct launch from a normal app throws SecurityException. Root `am start` is the fallback.
 *
 * Lives in `realos.camera`, not `xposed.hooks.camera`: with `realos.scope.camera=0`
 * that directory is excluded from compilation, and the UI references this class directly.
 */
object OplusCameraLauncher {

    private const val TAG = "RealOS-CameraLauncher"

    private const val ACTION_SETTING_MENU = "com.oplus.camera.action.SETTING_MENU"
    private const val SETTING_ACTIVITY = "com.oplus.camera.setting.CameraSettingActivity"

    /**
     * Opens the camera settings page.
     *
     * @param openRealosTab whether to ask the host to switch straight to the realOS tab
     * @return whether the launch command was dispatched
     */
    fun openSettings(context: Context, openRealosTab: Boolean): Boolean {
        val intent = Intent(ACTION_SETTING_MENU).apply {
            setClassName(SpecPackages.OPLUS_CAMERA, SETTING_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (openRealosTab) {
                putExtra(CameraIntentContract.EXTRA_OPEN_REALOS_TAB, true)
                putExtra(
                    CameraIntentContract.INTENT_DATA_KEY,
                    Bundle().apply { putBoolean(CameraIntentContract.EXTRA_OPEN_REALOS_TAB, true) }
                )
            }
        }

        val direct = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (direct) return true

        if (RootManager.rootInfo.value.state != RootState.GRANTED) {
            Log.w(TAG, "Direct launch rejected and root is unavailable")
            return false
        }

        val command = buildString {
            append("am start -n \"${SpecPackages.OPLUS_CAMERA}/$SETTING_ACTIVITY\"")
            append(" -a \"$ACTION_SETTING_MENU\"")
            if (openRealosTab) {
                append(" --ez ${CameraIntentContract.EXTRA_OPEN_REALOS_TAB} true")
            }
        }
        val result = runCatching { RootManager.suExecRaw(command) }.getOrNull()
        if (result?.isSuccess == true) return true

        Log.e(TAG, "Root am start failed: exit=${result?.exitCode}, out=${result?.output}")
        return false
    }
}
