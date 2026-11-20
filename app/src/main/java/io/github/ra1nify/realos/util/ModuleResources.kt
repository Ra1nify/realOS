package io.github.ra1nify.realos.util

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import io.github.ra1nify.realos.BuildConfig
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.ra1nify.realos.xposed.loader.ModuleSelfRef

/**
 * Resolves this module's own string resources from any process.
 *
 * Two very different processes need this:
 *
 * - the realOS app process, where the app resource table already is the module table, so ids
 *   resolve directly;
 * - a host process (settings, launcher, camera, keyboard, wallet), whose own `Resources` table is a
 *   different one, so `context.getString(R.string.x)` would resolve the module id against the host
 *   table. There the module APK resources have to be loaded explicitly.
 *
 * Host-process resolution tries several sources, cheapest and most reliable first, because no single
 * one is guaranteed: package visibility rules can hide the module from a host, and the module may not
 * have been loaded yet when a UI helper is first reached.
 *
 * 1. the module's own process: the app table is used as-is;
 * 2. `createPackageContext` for the module package;
 * 3. the module APK reported by the framework, through `getResourcesForApplication`;
 * 4. `getResourcesForApplication` by module package name;
 * 5. `AssetManager.addAssetPath` on the module APK path.
 *
 * The result is cached per process and its configuration is kept in sync with the caller, so a
 * locale or UI-mode change is picked up on the next lookup. The first successful source and the
 * failure of each source are logged once, which is what makes a host-process miss diagnosable.
 */
object ModuleResources {

    private const val TAG = "ModuleResources"

    /** The module package; every realOS resource id belongs to this table. */
    private val MODULE_PACKAGE = BuildConfig.APPLICATION_ID

    @Volatile
    private var cached: Resources? = null

    /** Which source produced [cached]; logged once for diagnosis. */
    @Volatile
    private var cachedSource: String? = null

    /** Returns the module [Resources], or null when no source could load it. */
    fun get(context: Context): Resources? {
        val existing = cached
        if (existing != null) {
            syncConfiguration(existing, context)
            return existing
        }

        return synchronized(this) {
            cached?.let {
                syncConfiguration(it, context)
                return@synchronized it
            }

            val (resolved, source) = resolve(context)
            if (resolved != null) {
                syncConfiguration(resolved, context)
                cached = resolved
                cachedSource = source
                Log.i(TAG, "Module resources resolved via $source")
            } else {
                Log.e(TAG, "Module resources unavailable in ${context.packageName}")
            }
            resolved
        }
    }

    /** Returns a module string, or an empty string when the resource cannot be resolved. */
    fun string(context: Context, resId: Int): String {
        val resources = get(context) ?: return ""
        return runCatching { resources.getString(resId) }.getOrElse {
            Log.w(TAG, "Missing module string 0x${Integer.toHexString(resId)} via $cachedSource")
            ""
        }
    }

    /** Clears the cached table; used when the module APK is replaced. */
    fun invalidate() {
        cached = null
        cachedSource = null
    }

    private fun resolve(context: Context): Pair<Resources?, String> {
        // 1. The module's own process: the app table already is the module table, so the id is
        //    correct without any extra loading.
        if (context.packageName == MODULE_PACKAGE) {
            return ((context.applicationContext ?: context).resources) to "own process"
        }

        // 2. A dedicated Context for the module package: its Resources are the module APK table.
        runCatching { context.createPackageContext(MODULE_PACKAGE, 0).resources }
            .onSuccess { return it to "createPackageContext" }
            .onFailure { Log.w(TAG, "createPackageContext($MODULE_PACKAGE) failed: ${it.message}") }

        // 3. The module APK the framework reports for this module.
        val appInfo = ModuleSelfRef.applicationInfo()
        if (appInfo != null) {
            runCatching { context.packageManager.getResourcesForApplication(appInfo) }
                .onSuccess { return it to "getResourcesForApplication(module appInfo)" }
                .onFailure {
                    Log.w(TAG, "getResourcesForApplication(module appInfo) failed: ${it.message}")
                }
        } else {
            Log.w(TAG, "ModuleSelfRef has no module instance in ${context.packageName}")
        }

        // 4. Package-name lookup.
        runCatching { context.packageManager.getResourcesForApplication(MODULE_PACKAGE) }
            .onSuccess { return it to "getResourcesForApplication(package)" }
            .onFailure { Log.w(TAG, "getResourcesForApplication($MODULE_PACKAGE) failed: ${it.message}") }

        // 5. Last resort: mount the APK path reported by the framework.
        val sourceDir = appInfo?.sourceDir ?: return null to "none"
        val mounted =
            runCatching {
                val assetManager = AssetManager::class.java.newInstance()
                AssetManager::class.java
                    .getMethod("addAssetPath", String::class.java)
                    .invoke(assetManager, sourceDir)
                @Suppress("DEPRECATION")
                Resources(assetManager, context.resources.displayMetrics, context.resources.configuration)
            }.onFailure { Log.w(TAG, "addAssetPath fallback failed: ${it.message}") }.getOrNull()
        return mounted to "addAssetPath"
    }

    private fun syncConfiguration(resources: Resources, context: Context) {
        val hostConfig = context.resources.configuration
        if (resources.configuration.locales != hostConfig.locales) {
            @Suppress("DEPRECATION")
            resources.updateConfiguration(hostConfig, context.resources.displayMetrics)
        }
    }
}
