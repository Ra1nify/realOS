package io.github.ra1nify.realos.xposed.hooks.intentroute

import android.app.SearchManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import io.github.libxposed.api.XposedModule
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.intentroute.IntentRouteConfig
import io.github.ra1nify.realos.xposed.loader.SpecPackages
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Aligns link intents with the user's resolver preferences instead of a
 * caller-supplied target package.
 *
 * Order applied by the system resolver once the explicit package is removed:
 *   1. the user's default app (preferred activity / selected app link / role)
 *   2. apps that declare support for the intent (single handler or chooser)
 *   3. the user's default browser (for http/https and web searches)
 *
 * ColorOS components bypass this by calling `Intent.setPackage()` (scan engine
 * `BrowserRouter`, Camera `QrCodeManager`, scanner `BrowserChooserHelper`, ...).
 * This hook strips that explicit target for link-like intents before the
 * package manager resolves them, and adds the browser fallback for
 * `ACTION_WEB_SEARCH`, which the default browser may not declare.
 *
 * Exclusions:
 *   - the caller is the target package (in-app deep links)
 *   - content/file/resource/intent schemes (URI grants and nested intents)
 *   - `fido` scheme (owned by the dedicated FIDO scan redirect feature)
 *   - the FIDO web-search fallback (query starts with `fido:`)
 */
object IntentRouteAlignmentHook {

    private const val TAG = "RealOS-IntentRoute"

    private const val ACTIVITY_TASK_SUPERVISOR = "com.android.server.wm.ActivityTaskSupervisor"
    private const val RESOLVE_INTENT = "resolveIntent"

    private const val SEARCH_URL = "https://www.google.com/search?q="
    private const val CONFIG_REFRESH_SECONDS = 10L

    private val EXCLUDED_SCHEMES = setOf(
        "content",
        "file",
        "android.resource",
        "android-app",
        "intent",
        "fido"
    )

    /**
     * Hosts whose hardcoded link targets are normalized.
     *
     * Keep this list explicit: a global redirect has too large a blast radius.
     * Add new hosts to [SpecPackages.INTENT_ROUTE_HOSTS] when another ColorOS
     * component is confirmed to hardcode a target package.
     */
    private val ALLOWED_CALLER_PACKAGES: Set<String> = SpecPackages.INTENT_ROUTE_HOSTS

    private val installed = AtomicBoolean(false)
    private val errorLogged = AtomicBoolean(false)
    private val browserRoleErrorLogged = AtomicBoolean(false)
    private val allowedUidsErrorLogged = AtomicBoolean(false)

    @Volatile
    private var allowedCallerUids: Set<Int> = emptySet()

    @Volatile
    private var alignEnabled = IntentRouteConfig.DEFAULT_ALIGN_LINKS_ENABLED

    @Volatile
    private var browserPackage: String? = null

    @Volatile
    private var cachedContext: Context? = null

    @Volatile
    private var cachedPackageManager: PackageManager? = null

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (!installed.compareAndSet(false, true)) {
            module.log(Log.DEBUG, TAG, "HookPoint[intent_route_align] already installed; skipping")
            return
        }

        val supervisorClass = runCatching { classLoader.loadClass(ACTIVITY_TASK_SUPERVISOR) }
            .getOrElse { t ->
                module.log(
                    Log.WARN,
                    TAG,
                    "HookPoint[intent_route_align] state=MISSING class=$ACTIVITY_TASK_SUPERVISOR",
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
                "HookPoint[intent_route_align] state=MISSING method=$RESOLVE_INTENT(Intent,String,int,int,int,int)",
                t
            )
            installed.set(false)
            return
        }

        alignEnabled = IntentRouteConfig.DEFAULT_ALIGN_LINKS_ENABLED
        refreshAllowedCallerUids(module)

        runCatching {
            module.hook(resolveIntent).intercept { chain ->
                val intent = chain.args.getOrNull(0) as? Intent
                val callingUid = (chain.args.getOrNull(4) as? Int) ?: -1
                if (intent != null) {
                    try {
                        maybeAlign(module, intent, callingUid)
                    } catch (t: Throwable) {
                        if (errorLogged.compareAndSet(false, true)) {
                            module.log(
                                Log.ERROR,
                                TAG,
                                "HookPoint[intent_route_align] state=ERROR; intent left untouched",
                                t
                            )
                        }
                    }
                }
                chain.proceed()
            }
            module.log(Log.INFO, TAG, "HookPoint[intent_route_align] state=FOUND method=$RESOLVE_INTENT")
        }.onFailure { t ->
            module.log(Log.WARN, TAG, "HookPoint[intent_route_align] state=ERROR", t)
            installed.set(false)
            return
        }

        startRefresh(module)
        module.log(
            Log.INFO,
            TAG,
            "Intent route alignment installed, hosts=$ALLOWED_CALLER_PACKAGES uids=$allowedCallerUids"
        )
    }

    private fun startRefresh(module: XposedModule) {
        runCatching {
            val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "realos-intentroute-config").apply { isDaemon = true }
            }
            executor.scheduleWithFixedDelay(
                {
                    runCatching {
                        alignEnabled = IntentRouteConfig.isAlignLinksEnabled(module)
                    }
                    refreshAllowedCallerUids(module)
                    refreshBrowserPackage(module)
                },
                0L,
                CONFIG_REFRESH_SECONDS,
                TimeUnit.SECONDS
            )
        }.onFailure {
            module.log(Log.WARN, TAG, "Failed to start config refresh", it)
        }
    }

    private fun maybeAlign(module: XposedModule, intent: Intent, callingUid: Int) {
        if (!alignEnabled) return
        // The chooser starts the selected activity from the system UID, which is
        // never in the host allowlist, so the user's selection is always honored.
        if (callingUid == Process.SYSTEM_UID) return
        if (callingUid !in allowedCallerUids) return

        val action = intent.action ?: return
        if (action == Intent.ACTION_WEB_SEARCH) {
            alignWebSearch(module, intent)
            return
        }
        if (action != Intent.ACTION_VIEW) return

        val data = intent.data ?: return
        val scheme = data.scheme?.lowercase() ?: return
        if (scheme in EXCLUDED_SCHEMES) return

        // Never re-target an intent that carries URI grants: the grant is
        // scoped to the original package.
        val grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        if ((intent.flags and grantFlags) != 0) return
        if (intent.clipData != null) return

        val explicitPackage = intent.component?.packageName ?: intent.getPackage() ?: return
        val callerPackage = packageForUid(callingUid)
        if (callerPackage != null && callerPackage == explicitPackage) return

        if (!intent.hasCategory(Intent.CATEGORY_DEFAULT)) {
            intent.addCategory(Intent.CATEGORY_DEFAULT)
        }
        if ((scheme == "http" || scheme == "https") &&
            !intent.hasCategory(Intent.CATEGORY_BROWSABLE)
        ) {
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val packageManager = systemPackageManager() ?: return
        val implicit = Intent(intent).apply {
            component = null
            setPackage(null)
        }
        if (packageManager.resolveActivity(implicit, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            return
        }

        intent.component = null
        intent.setPackage(null)
        module.log(
            Log.INFO,
            TAG,
            "Aligned $scheme VIEW: dropped hardcoded target=$explicitPackage"
        )
    }

    private fun alignWebSearch(module: XposedModule, intent: Intent) {
        val query = intent.getStringExtra(SearchManager.QUERY) ?: return
        if (query.startsWith("fido:", ignoreCase = true)) return
        if (intent.getPackage() == null && intent.component == null) return

        val browser = browserPackage ?: return
        if (intent.getPackage() == browser && intent.component == null) return

        val packageManager = systemPackageManager() ?: return

        val direct = Intent(Intent.ACTION_WEB_SEARCH).apply {
            setPackage(browser)
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(SearchManager.QUERY, query)
        }
        if (packageManager.resolveActivity(direct, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            intent.component = null
            intent.setPackage(browser)
            if (!intent.hasCategory(Intent.CATEGORY_DEFAULT)) {
                intent.addCategory(Intent.CATEGORY_DEFAULT)
            }
            module.log(Log.INFO, TAG, "Aligned WEB_SEARCH: dropped target, browser=$browser")
            return
        }

        intent.action = Intent.ACTION_VIEW
        intent.component = null
        intent.data = Uri.parse(SEARCH_URL + Uri.encode(query))
        intent.setPackage(browser)
        intent.removeExtra(SearchManager.QUERY)
        if (!intent.hasCategory(Intent.CATEGORY_DEFAULT)) {
            intent.addCategory(Intent.CATEGORY_DEFAULT)
        }
        if (!intent.hasCategory(Intent.CATEGORY_BROWSABLE)) {
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
        }
        module.log(Log.INFO, TAG, "Aligned WEB_SEARCH: converted to search URL, browser=$browser")
    }

    private fun packageForUid(uid: Int): String? {
        if (uid < 0) return null
        return runCatching { systemPackageManager()?.getNameForUid(uid) }.getOrNull()
    }

    private fun refreshAllowedCallerUids(module: XposedModule) {
        runCatching {
            val packageManager = systemPackageManager() ?: return
            val uids = buildSet {
                for (pkg in ALLOWED_CALLER_PACKAGES) {
                    val uid = runCatching { packageManager.getPackageUid(pkg, 0) }.getOrNull()
                    if (uid != null) add(uid)
                }
            }
            allowedCallerUids = uids
            if (uids.isEmpty() && allowedUidsErrorLogged.compareAndSet(false, true)) {
                module.log(
                    Log.WARN,
                    TAG,
                    "No allowlisted host package resolved: $ALLOWED_CALLER_PACKAGES"
                )
            }
        }.onFailure { t ->
            if (allowedUidsErrorLogged.compareAndSet(false, true)) {
                module.log(Log.WARN, TAG, "Failed to resolve host UIDs", t)
            }
        }
    }

    private fun refreshBrowserPackage(module: XposedModule) {
        val previous = browserPackage
        runCatching {
            val context = systemContext() ?: return
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return
            val getRoleHolders = roleManager.javaClass.getMethod(
                "getRoleHolders",
                String::class.java
            )
            val holders = getRoleHolders.invoke(roleManager, RoleManager.ROLE_BROWSER) as? List<*>
            browserPackage = holders?.firstOrNull() as? String
        }.onFailure { t ->
            if (browserRoleErrorLogged.compareAndSet(false, true)) {
                module.log(Log.WARN, TAG, "Failed to read BROWSER role holder", t)
            }
            return
        }
        val current = browserPackage
        if (current != previous && current != null) {
            module.log(Log.INFO, TAG, "BROWSER role holder=$current")
        }
    }

    private fun systemContext(): Context? {
        cachedContext?.let { return it }
        synchronized(this) {
            cachedContext?.let { return it }
            val context = runCatching {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val current = activityThreadClass
                    .getMethod("currentActivityThread")
                    .invoke(null) ?: return null
                activityThreadClass
                    .getMethod("getSystemContext")
                    .invoke(current) as? Context ?: return null
            }.getOrNull() ?: return null
            cachedContext = context
            return context
        }
    }

    private fun systemPackageManager(): PackageManager? {
        cachedPackageManager?.let { return it }
        synchronized(this) {
            cachedPackageManager?.let { return it }
            val packageManager = systemContext()?.packageManager ?: return null
            cachedPackageManager = packageManager
            return packageManager
        }
    }
}
