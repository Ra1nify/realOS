package io.github.ra1nify.realos.xposed.hooks.fido

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.fido.FidoRedirectConfig
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Redirects FIDO2 QR scan results to the system credential authenticator.
 *
 * ColorOS scan engine (`com.coloros.ocrscanner`) and Camera classify
 * `FIDO:/<digits>` as a generic URI and fall back to
 * `ACTION_WEB_SEARCH` because `BrowserRouter` only accepts web URLs. The
 * account scanner works because it hands the same string to the system
 * resolver as `ACTION_VIEW`.
 *
 * This hook runs in system_server before activity resolution and rewrites the
 * scanner fallback into the equivalent `ACTION_VIEW`, preferring the OEM
 * credential provider. The payload is validated with the same digit/remainder
 * rules as the FIDO hybrid QR format so malformed URIs never reach the
 * authenticator (which throws on an invalid remainder length).
 *
 * Hook point: `com.android.server.wm.ActivityTaskSupervisor.resolveIntent`,
 * called from `ActivityStarter.execute` -> `Request.resolveActivity` before
 * the package manager resolution and before the activity is started.
 */
object FidoScanRedirectHook {

    private const val TAG = "RealOS-FidoRedirect"

    private const val ACTIVITY_TASK_SUPERVISOR = "com.android.server.wm.ActivityTaskSupervisor"
    private const val RESOLVE_INTENT = "resolveIntent"

    /** OEM system passkey provider on ColorOS. */
    private const val SYSTEM_CREDENTIAL_PACKAGE = "com.oplus.credential"

    private const val FIDO_SCHEME_PREFIX = "fido:"

    /** FIDO hybrid QR: 17 decimal digits per 7 bytes, these remainders are valid. */
    private val ALLOWED_REMAINDERS = intArrayOf(0, 3, 5, 8, 10, 13, 15)

    private const val CONFIG_REFRESH_SECONDS = 10L

    private val installed = AtomicBoolean(false)
    private val errorLogged = AtomicBoolean(false)
    private val noTargetLogged = AtomicBoolean(false)

    @Volatile
    private var redirectEnabled = FidoRedirectConfig.DEFAULT_SCAN_REDIRECT_ENABLED

    @Volatile
    private var cachedPackageManager: PackageManager? = null

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (!installed.compareAndSet(false, true)) {
            module.log(Log.DEBUG, TAG, "HookPoint[fido_scan_redirect] already installed; skipping")
            return
        }

        val supervisorClass = runCatching { classLoader.loadClass(ACTIVITY_TASK_SUPERVISOR) }
            .getOrElse { t ->
                module.log(
                    Log.WARN,
                    TAG,
                    "HookPoint[fido_scan_redirect] state=MISSING class=$ACTIVITY_TASK_SUPERVISOR",
                    t
                )
                installed.set(false)
                return
            }

        val resolveIntent = runCatching {
            supervisorClass.getDeclaredMethod(
                RESOLVE_INTENT,
                Intent::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
        }.getOrElse { t ->
            module.log(
                Log.WARN,
                TAG,
                "HookPoint[fido_scan_redirect] state=MISSING method=$RESOLVE_INTENT(Intent,String,int,int,int,int)",
                t
            )
            installed.set(false)
            return
        }

        redirectEnabled = FidoRedirectConfig.DEFAULT_SCAN_REDIRECT_ENABLED

        runCatching {
            module.hook(resolveIntent).intercept { chain ->
                val intent = chain.args.getOrNull(0) as? Intent
                if (intent != null) {
                    try {
                        maybeRewrite(module, intent)
                    } catch (t: Throwable) {
                        if (errorLogged.compareAndSet(false, true)) {
                            module.log(
                                Log.ERROR,
                                TAG,
                                "HookPoint[fido_scan_redirect] state=ERROR rewrite failed; intent left untouched",
                                t
                            )
                        }
                    }
                }
                chain.proceed()
            }
            module.log(Log.INFO, TAG, "HookPoint[fido_scan_redirect] state=FOUND method=$RESOLVE_INTENT")
        }.onFailure { t ->
            module.log(Log.WARN, TAG, "HookPoint[fido_scan_redirect] state=ERROR", t)
            installed.set(false)
            return
        }

        startConfigRefresh(module)
        module.log(
            Log.INFO,
            TAG,
            "FIDO scan redirect installed, target=$SYSTEM_CREDENTIAL_PACKAGE"
        )
    }

    private fun startConfigRefresh(module: XposedModule) {
        runCatching {
            val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "realos-fido-config").apply { isDaemon = true }
            }
            executor.scheduleWithFixedDelay(
                {
                    runCatching {
                        redirectEnabled = FidoRedirectConfig.isScanRedirectEnabled(module)
                    }
                },
                0L,
                CONFIG_REFRESH_SECONDS,
                TimeUnit.SECONDS
            )
        }.onFailure {
            module.log(Log.WARN, TAG, "Failed to start config refresh; using default value", it)
        }
    }

    private fun maybeRewrite(module: XposedModule, intent: Intent) {
        if (intent.action != Intent.ACTION_WEB_SEARCH) return
        val query = intent.getStringExtra(SearchManager.QUERY) ?: return
        if (query.length < 8) return
        if (!query.startsWith(FIDO_SCHEME_PREFIX, ignoreCase = true)) return

        val payload = parsePayload(query) ?: return
        if (!redirectEnabled) return

        val packageManager = systemPackageManager() ?: return

        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("FIDO:/$payload")).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pinnedIntent = Intent(viewIntent).apply { setPackage(SYSTEM_CREDENTIAL_PACKAGE) }
        val targetPackage = if (
            packageManager.resolveActivity(pinnedIntent, PackageManager.MATCH_DEFAULT_ONLY) != null
        ) {
            SYSTEM_CREDENTIAL_PACKAGE
        } else {
            if (
                packageManager.resolveActivity(viewIntent, PackageManager.MATCH_DEFAULT_ONLY) == null
            ) {
                if (noTargetLogged.compareAndSet(false, true)) {
                    module.log(Log.WARN, TAG, "No FIDO handler installed; leaving scan result untouched")
                }
                return
            }
            null
        }

        intent.action = Intent.ACTION_VIEW
        intent.data = viewIntent.data
        intent.component = null
        intent.setPackage(targetPackage)
        if (!intent.hasCategory(Intent.CATEGORY_DEFAULT)) {
            intent.addCategory(Intent.CATEGORY_DEFAULT)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.removeExtra(SearchManager.QUERY)

        module.log(
            Log.INFO,
            TAG,
            "FIDO scan redirect: ACTION_WEB_SEARCH -> ACTION_VIEW " +
                "target=${targetPackage ?: "system-resolver"} payloadLen=${payload.length}"
        )
    }

    /**
     * Mirrors the FIDO hybrid QR format: `FIDO:/` + decimal digits, 17 digits
     * per 7 bytes. Returns the first path segment when it is structurally valid.
     */
    private fun parsePayload(raw: String): String? {
        val uri = Uri.parse(raw.trim())
        if (!"fido".equals(uri.scheme, ignoreCase = true)) return null
        val payload = uri.pathSegments.firstOrNull() ?: return null
        if (payload.isEmpty()) return null
        if (payload.length % 17 !in ALLOWED_REMAINDERS) return null
        for (ch in payload) {
            if (ch !in '0'..'9') return null
        }
        return payload
    }

    private fun systemPackageManager(): PackageManager? {
        cachedPackageManager?.let { return it }
        synchronized(this) {
            cachedPackageManager?.let { return it }
            val packageManager = runCatching {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val current = activityThreadClass
                    .getMethod("currentActivityThread")
                    .invoke(null) ?: return null
                val systemContext = activityThreadClass
                    .getMethod("getSystemContext")
                    .invoke(current) as? Context ?: return null
                systemContext.packageManager
            }.getOrNull() ?: return null
            cachedPackageManager = packageManager
            return packageManager
        }
    }
}
