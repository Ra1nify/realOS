package io.github.ra1nify.realos.xposed.hooks.opluskeyboard

import android.app.Application
import android.content.pm.PackageInfo
import android.os.Build
import io.github.ra1nify.realos.util.RealOSLog as Log
import io.github.libxposed.api.XposedModule
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.UsingType as QueryUsingType
import org.luckypray.dexkit.query.matchers.FieldMatcher
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.result.MethodData
import org.json.JSONObject
import java.io.File
import io.github.ra1nify.realos.xposed.symbol.ClassDescriptor
import io.github.ra1nify.realos.xposed.symbol.FieldDescriptor
import io.github.ra1nify.realos.xposed.symbol.HookPointState
import io.github.ra1nify.realos.xposed.symbol.MethodDescriptor
import java.lang.reflect.Modifier

/**
 * Symbol resolver for OPPO Keyboard (Xiaobu Input Method).
 *
 * Responsibilities:
 * - Dynamic resolution of obfuscated symbols using cross-version invariant signatures via DexKit.
 * - Persistent caching in app-internal files directory with validation of host and module versions.
 * - Strict uniqueness check and fail-closed error handling (no speculation fallback).
 * - HookPoint state output for diagnostic observability.
 */
object OplusInputSymbolResolver {

    private const val TAG = "OplusInputResolver"
    private const val RULE_VERSION = 22
    private const val CACHE_FILE_NAME = "realos_oplusinput_symbols.json"

    /**
     * Intermediate result of a per-key scan. Every field is nullable; [statusMap] holds
     * state. A non-unique primary condition leaves its descriptor null.
     */
    private data class PerKeyResult(
        val modelGetter: MethodDescriptor? = null,
        val entityClass: ClassDescriptor? = null,
        val hitTestMethod: MethodDescriptor? = null,
        val leftField: FieldDescriptor? = null,
        val rightField: FieldDescriptor? = null,
        val topField: FieldDescriptor? = null,
        val bottomField: FieldDescriptor? = null,
        val drawMethod: MethodDescriptor? = null,
        val pressedField: FieldDescriptor? = null,
        val redrawMethod: MethodDescriptor? = null,
        val statusMap: MutableMap<String, HookPointState> = mutableMapOf()
    )

    fun resolve(module: XposedModule, app: Application): OplusInputSymbols {
        val hostInfo = getPackageInfo(app)
        val hostFingerprint = "${hostInfo?.let { getVersionCode(it) } ?: 0}_${hostInfo?.versionName ?: "unknown"}"
        val moduleVersionCode = try {
            val apkPath = module.getModuleApplicationInfo().sourceDir
            val archiveInfo = app.packageManager.getPackageArchiveInfo(apkPath, 0)
            archiveInfo?.let { getVersionCode(it).toInt() } ?: 0
        } catch (_: Throwable) {
            0
        }

        // 1. Attempt cache restoration
        val cached = loadFromCache(app, hostFingerprint, moduleVersionCode)
        if (cached != null) {
            Log.i(TAG, "Cache valid for host=$hostFingerprint. Verifying symbols via reflection...")
            if (verifyCachedSymbols(app.classLoader, cached)) {
                logHookPointStates(cached)
                return cached
            }
            Log.w(TAG, "Cached symbols failed reflection verification, discarding cache.")
        }

        // 2. Perform fresh DexKit scan
        Log.i(TAG, "Performing fresh DexKit scan for host=$hostFingerprint...")
        val freshSymbols = scanDex(module, app, hostFingerprint, moduleVersionCode)

        // 3. Persist fresh symbols to cache
        saveToCache(app, freshSymbols)
        logHookPointStates(freshSymbols)

        return freshSymbols
    }

    private fun loadFromCache(
        app: Application,
        hostFingerprint: String,
        moduleVersionCode: Int
    ): OplusInputSymbols? = runCatching {
        val file = File(app.filesDir, CACHE_FILE_NAME)
        if (!file.exists()) return null

        val jsonText = file.readText()
        val json = JSONObject(jsonText)
        val symbols = OplusInputSymbols.fromJson(json)

        if (symbols.ruleVersion != RULE_VERSION) {
            Log.i(TAG, "Cache ruleVersion mismatch (${symbols.ruleVersion} vs $RULE_VERSION)")
            return null
        }
        if (symbols.hostFingerprint != hostFingerprint) {
            Log.i(TAG, "Cache hostFingerprint mismatch (${symbols.hostFingerprint} vs $hostFingerprint)")
            return null
        }
        if (symbols.lastAdaptedVersion.isNotEmpty() && symbols.lastAdaptedVersion != hostFingerprint) {
            Log.i(TAG, "Cache lastAdaptedVersion mismatch (${symbols.lastAdaptedVersion} vs $hostFingerprint)")
            return null
        }
        if (symbols.moduleVersionCode != moduleVersionCode) {
            Log.i(TAG, "Cache moduleVersionCode changed (${symbols.moduleVersionCode} -> $moduleVersionCode), verifying symbols...")
        }
        if (symbols.statusMap["whitelist"] != HookPointState.FOUND ||
            symbols.statusMap["perkey_bounds"] != HookPointState.FOUND ||
            symbols.statusMap["perkey_hit_test"] != HookPointState.FOUND
        ) {
            Log.w(TAG, "Cached symbols missing critical hook points; discarding cache.")
            return null
        }
        symbols
    }.getOrNull()

    private fun verifyCachedSymbols(classLoader: ClassLoader, symbols: OplusInputSymbols): Boolean {
        if (symbols.blurDecisionMethod != null) {
            val method = symbols.blurDecisionMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for blurDecisionMethod: ${symbols.blurDecisionMethod.methodName}")
                return false
            }
        }
        if (symbols.whitelistField != null) {
            val field = symbols.whitelistField.restore(classLoader)
            if (field == null) {
                Log.w(TAG, "Verification failed for whitelistField: ${symbols.whitelistField.fieldName}")
                return false
            }
        }
        if (symbols.blurApplyMethod != null) {
            val method = symbols.blurApplyMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for blurApplyMethod: ${symbols.blurApplyMethod.methodName}")
                return false
            }
        }
        if (symbols.foldCheckMethod != null) {
            val method = symbols.foldCheckMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for foldCheckMethod: ${symbols.foldCheckMethod.methodName}")
                return false
            }
        }
        if (symbols.keyboardTouchMethod != null) {
            val method = symbols.keyboardTouchMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for keyboardTouchMethod: ${symbols.keyboardTouchMethod.methodName}")
                return false
            }
        }
        if (symbols.keyboardDrawMethod != null) {
            val method = symbols.keyboardDrawMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for keyboardDrawMethod: ${symbols.keyboardDrawMethod.methodName}")
                return false
            }
        }
        if (symbols.dayNightCheckMethod != null) {
            val method = symbols.dayNightCheckMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for dayNightCheckMethod: ${symbols.dayNightCheckMethod.methodName}")
                return false
            }
        }
        if (symbols.blurParamCreateMethod != null) {
            val method = symbols.blurParamCreateMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for blurParamCreateMethod: ${symbols.blurParamCreateMethod.methodName}")
                return false
            }
        }
        // The blur radius path is handled at runtime by reflection on
        // ViewRootManager.setBlurRadius; no host symbol is verified here.
        if (symbols.keyBackgroundMethod != null) {
            val method = symbols.keyBackgroundMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for keyBackgroundMethod: ${symbols.keyBackgroundMethod.methodName}")
                return false
            }
        }
        for (descriptor in symbols.keyAlphaMethods) {
            if (descriptor.restore(classLoader) == null) {
                Log.w(TAG, "Verification failed for keyAlphaMethod: ${descriptor.className}.${descriptor.methodName}")
                return false
            }
        }
        if (symbols.toolbarLayoutMethod != null) {
            val method = symbols.toolbarLayoutMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for toolbarLayoutMethod: ${symbols.toolbarLayoutMethod.methodName}")
                return false
            }
        }
        if (symbols.toolbarItemIconMethod != null) {
            val method = symbols.toolbarItemIconMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for toolbarItemIconMethod: ${symbols.toolbarItemIconMethod.methodName}")
                return false
            }
        }
        if (symbols.toolCatalogCreateMethod != null) {
            val method = symbols.toolCatalogCreateMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for toolCatalogCreateMethod: ${symbols.toolCatalogCreateMethod.methodName}")
                return false
            }
        }
        if (symbols.toolboxDataMethod != null) {
            val method = symbols.toolboxDataMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for toolboxDataMethod: ${symbols.toolboxDataMethod.methodName}")
                return false
            }
        }
        if (symbols.toolStateManagerTransformMethod != null) {
            val method = symbols.toolStateManagerTransformMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for toolStateManagerTransformMethod: ${symbols.toolStateManagerTransformMethod.methodName}")
                return false
            }
        }
        if (symbols.themeIconMethod != null) {
            val method = symbols.themeIconMethod.restore(classLoader)
            if (method == null) {
                Log.w(TAG, "Verification failed for themeIconMethod: ${symbols.themeIconMethod.methodName}")
                return false
            }
        }
        // per-key: verified only when per-key is usable; MISSING entries are skipped.
        if (symbols.isReadyForPerKey()) {
            if (symbols.perkeyModelGetter?.restore(classLoader) == null) {
                Log.w(TAG, "Verification failed for perkeyModelGetter")
                return false
            }
            if (symbols.perkeyEntityClass?.restore(classLoader) == null) {
                Log.w(TAG, "Verification failed for perkeyEntityClass: ${symbols.perkeyEntityClass?.className}")
                return false
            }
            if (symbols.perkeyHitTestMethod?.restore(classLoader) == null) {
                Log.w(TAG, "Verification failed for perkeyHitTestMethod")
                return false
            }
            val boundsFields = listOf(
                symbols.perkeyLeftField, symbols.perkeyRightField,
                symbols.perkeyTopField, symbols.perkeyBottomField
            )
            if (boundsFields.any { it?.restore(classLoader) == null }) {
                Log.w(TAG, "Verification failed for one of per-key bounds fields")
                return false
            }
            if (symbols.perkeyPressedField != null && symbols.perkeyPressedField.restore(classLoader) == null) {
                Log.w(TAG, "Verification failed for perkeyPressedField")
                return false
            }
            if (symbols.perkeyRedrawMethod != null && symbols.perkeyRedrawMethod.restore(classLoader) == null) {
                Log.w(TAG, "Verification failed for perkeyRedrawMethod")
                return false
            }
            if (symbols.perkeyDrawMethod != null && symbols.perkeyDrawMethod.restore(classLoader) == null) {
                Log.w(TAG, "Verification failed for perkeyDrawMethod")
                return false
            }
            if (symbols.perkeyShadowUnderlayMethod != null &&
                symbols.perkeyShadowUnderlayMethod.restore(classLoader) == null
            ) {
                Log.w(TAG, "Verification failed for perkeyShadowUnderlayMethod")
                return false
            }
            if (symbols.perkeyShadowOverlayMethod != null &&
                symbols.perkeyShadowOverlayMethod.restore(classLoader) == null
            ) {
                Log.w(TAG, "Verification failed for perkeyShadowOverlayMethod")
                return false
            }
        }
        return true
    }

    private fun saveToCache(app: Application, symbols: OplusInputSymbols) {
        try {
            val file = File(app.filesDir, CACHE_FILE_NAME)
            file.writeText(symbols.toJson().toString(2))
            Log.i(TAG, "Saved resolved symbols to cache file: ${file.absolutePath}")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to write symbols cache file", t)
        }
    }

    private fun loadDexKitNative(module: XposedModule) {
        try {
            System.loadLibrary("dexkit")
        } catch (t: Throwable) {
            val nativeDir = module.getModuleApplicationInfo().nativeLibraryDir
            val soFile = File(nativeDir, "libdexkit.so")
            if (soFile.exists()) {
                System.load(soFile.absolutePath)
            } else {
                throw t
            }
        }
    }

    private fun scanDex(
        module: XposedModule,
        app: Application,
        hostFingerprint: String,
        moduleVersionCode: Int
    ): OplusInputSymbols {
        val statusMap = mutableMapOf<String, HookPointState>()
        val scanErrors = mutableListOf<String>()
        var whitelistField: FieldDescriptor? = null
        var blurDecisionMethod: MethodDescriptor? = null
        var blurApplyMethod: MethodDescriptor? = null
        var foldCheckMethod: MethodDescriptor? = null
        var keyboardTouchMethod: MethodDescriptor? = null
        var keyboardDrawMethod: MethodDescriptor? = null
        var keyboardDrawMethodData: MethodData? = null
        var keyboardViewClassName: String? = null
        var dayNightCheckMethod: MethodDescriptor? = null
        var blurParamCreateMethod: MethodDescriptor? = null
        var keyBackgroundMethod: MethodDescriptor? = null
        val keyAlphaMethods = mutableListOf<MethodDescriptor>()
    var keyBgAlpha255Method: MethodDescriptor? = null
    val keyBufferRefreshMethods = mutableListOf<MethodDescriptor>()
        var toolbarLayoutMethod: MethodDescriptor? = null
        var toolbarItemIconMethod: MethodDescriptor? = null
        var toolCatalogCreateMethod: MethodDescriptor? = null
        var toolboxDataMethod: MethodDescriptor? = null
        var toolStateManagerTransformMethod: MethodDescriptor? = null
        var themeIconMethod: MethodDescriptor? = null
        var themeManagerClass: ClassDescriptor? = null
        var themeManagerLazyField: FieldDescriptor? = null
        var themeManagerInnerMethod: MethodDescriptor? = null
        var themeManagerApplyMethod: MethodDescriptor? = null
        // per-key symbols (key blending, per-key geometry)
        var perkeyModelGetter: MethodDescriptor? = null
        var perkeyEntityClass: ClassDescriptor? = null
        var perkeyHitTestMethod: MethodDescriptor? = null
        var perkeyLeftField: FieldDescriptor? = null
        var perkeyRightField: FieldDescriptor? = null
        var perkeyTopField: FieldDescriptor? = null
        var perkeyBottomField: FieldDescriptor? = null
        var perkeyDrawMethod: MethodDescriptor? = null
        var perkeyShadowUnderlayMethod: MethodDescriptor? = null
        var perkeyShadowOverlayMethod: MethodDescriptor? = null
        var perkeyPressedField: FieldDescriptor? = null
        var perkeyRedrawMethod: MethodDescriptor? = null

        val apkPath = app.applicationInfo.sourceDir

        try {
            loadDexKitNative(module)
        } catch (t: Throwable) {
            val err = "Failed to load libdexkit.so: ${t.message}"
            Log.e(TAG, err, t)
            scanErrors.add(err)
            statusMap["whitelist"] = HookPointState.ERROR
            statusMap["blur_apply"] = HookPointState.ERROR
            statusMap["fold_check"] = HookPointState.ERROR
            statusMap["effects_touch"] = HookPointState.ERROR
            statusMap["effects_draw"] = HookPointState.ERROR
            statusMap["day_night_check"] = HookPointState.ERROR
            statusMap["blur_param_create"] = HookPointState.ERROR
            statusMap["key_background"] = HookPointState.ERROR
            statusMap["key_alpha"] = HookPointState.ERROR
            statusMap["toolbar_layout"] = HookPointState.ERROR
            statusMap["toolbar_item_icon"] = HookPointState.ERROR
            statusMap["tool_catalog_create"] = HookPointState.ERROR
            statusMap["toolbox_data"] = HookPointState.ERROR
            statusMap["tool_state_transform"] = HookPointState.ERROR
            statusMap["theme_icon"] = HookPointState.ERROR
            statusMap["blur_radius_runtime"] = HookPointState.ERROR
            statusMap["perkey_model_getter"] = HookPointState.ERROR
            statusMap["perkey_entity"] = HookPointState.ERROR
            statusMap["perkey_hit_test"] = HookPointState.ERROR
            statusMap["perkey_bounds"] = HookPointState.ERROR
            statusMap["perkey_shadow_underlay"] = HookPointState.ERROR
            statusMap["perkey_shadow_overlay"] = HookPointState.ERROR
            return OplusInputSymbols(
                ruleVersion = RULE_VERSION,
                hostFingerprint = hostFingerprint,
                moduleVersionCode = moduleVersionCode,
                statusMap = statusMap,
                scanErrors = scanErrors
            )
        }

        try {
            DexKitBridge.create(apkPath).use { bridge ->
                // ── 1. Resolve Whitelist Field or Inline Blur Decision Method ──
                try {
                    // 1a. New version: blur decision method taking (EditorInfo) returning boolean using quicksearchbox + aimemory
                    val blurDecisionCandidates = bridge.findMethod {
                        matcher {
                            paramCount(1)
                            paramTypes("android.view.inputmethod.EditorInfo")
                            returnType("boolean")
                            usingStrings("com.heytap.quicksearchbox", "com.oplus.aimemory")
                        }
                    }
                    if (blurDecisionCandidates.size == 1) {
                        val method = blurDecisionCandidates.single()
                        blurDecisionMethod = MethodDescriptor(method.className, method.name, method.returnTypeName, method.paramTypeNames)
                        statusMap["whitelist"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique blurDecisionMethod: ${method.className}.${method.name}")
                    } else {
                        // 1b. Legacy version: static whitelist List field
                        val matchedClasses = bridge.findClass {
                            matcher {
                                usingStrings("com.heytap.quicksearchbox", "com.oplus.aimemory")
                            }
                        }
                        val candidates = mutableListOf<Pair<ClassData, FieldData>>()
                        for (clz in matchedClasses) {
                            for (field in clz.fields) {
                                if (Modifier.isStatic(field.modifiers)) {
                                    val tName = field.typeName
                                    if (tName == "java.util.List" ||
                                        tName == "java.util.ArrayList" ||
                                        tName.endsWith(".List") ||
                                        tName.endsWith(".ArrayList")
                                    ) {
                                        candidates.add(clz to field)
                                    }
                                }
                            }
                        }

                        if (candidates.size == 1) {
                            val (_, field) = candidates[0]
                            whitelistField = FieldDescriptor(field.className, field.name, field.typeName)
                            statusMap["whitelist"] = HookPointState.FOUND
                            Log.i(TAG, "Found unique whitelistField: ${field.className}.${field.name} (${field.typeName})")
                        } else if (candidates.isEmpty() && blurDecisionCandidates.isEmpty()) {
                            statusMap["whitelist"] = HookPointState.MISSING
                            scanErrors.add("Whitelist: 0 candidates found for blurDecisionMethod or whitelistField")
                        } else {
                            statusMap["whitelist"] = HookPointState.ERROR
                            scanErrors.add("Whitelist: ambiguous candidates blurDecision=${blurDecisionCandidates.size} field=${candidates.size}")
                        }
                    }
                } catch (t: Throwable) {
                    statusMap["whitelist"] = HookPointState.ERROR
                    scanErrors.add("Whitelist scan exception: ${t.message}")
                    Log.e(TAG, "Whitelist scan failed", t)
                }

                // ── 2. Resolve Blur Apply Method ──
                var blurMethodData: MethodData? = null
                try {
                    val matchedMethods = bridge.findMethod {
                        matcher {
                            usingStrings("applyBlurEnable pkg=")
                        }
                    }
                    val candidates = matchedMethods.filter {
                        it.paramCount == 2 &&
                        it.paramTypeNames.getOrNull(0) == "android.view.inputmethod.EditorInfo" &&
                        (it.paramTypeNames.getOrNull(1) == "boolean" || it.paramTypeNames.getOrNull(1) == "Z") &&
                        (it.returnTypeName == "void" || it.returnTypeName == "V")
                    }

                    if (candidates.size == 1) {
                        val m = candidates[0]
                        blurMethodData = m
                        blurApplyMethod = MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                        statusMap["blur_apply"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique blurApplyMethod: ${m.className}.${m.name}")
                    } else if (candidates.isEmpty()) {
                        statusMap["blur_apply"] = HookPointState.MISSING
                        scanErrors.add("Blur apply method: 0 candidates found for 'applyBlurEnable pkg='")
                    } else {
                        statusMap["blur_apply"] = HookPointState.ERROR
                        scanErrors.add("Blur apply method: ambiguous candidates count=${candidates.size}")
                    }
                } catch (t: Throwable) {
                    statusMap["blur_apply"] = HookPointState.ERROR
                    scanErrors.add("Blur apply scan exception: ${t.message}")
                    Log.e(TAG, "Blur apply scan failed", t)
                }

                // ── 3. Resolve Fold Check Method ──
                try {
                    val matchedMethods = bridge.findMethod {
                        matcher {
                            usingStrings("oplus_system_folding_mode")
                        }
                    }
                    val candidates = matchedMethods.filter {
                        it.className.startsWith("com.oplus.keyboard") &&
                        it.paramCount == 0 &&
                        (it.returnTypeName == "boolean" || it.returnTypeName == "Z") &&
                        Modifier.isStatic(it.modifiers)
                    }

                    var chosenMethod: MethodData? = null
                    if (candidates.size == 1) {
                        chosenMethod = candidates[0]
                    } else if (candidates.size > 1 && blurMethodData != null) {
                        // Disambiguate by checking which fold candidate is invoked by blurApplyMethod
                        val invokedDesc = runCatching {
                            blurMethodData.invokes.map { it.descriptor }.toSet()
                        }.getOrNull()
                        if (invokedDesc != null) {
                            val invokedCandidates = candidates.filter { it.descriptor in invokedDesc }
                            if (invokedCandidates.size == 1) {
                                chosenMethod = invokedCandidates[0]
                            }
                        }
                    }

                    if (chosenMethod != null) {
                        foldCheckMethod = MethodDescriptor(
                            chosenMethod.className,
                            chosenMethod.name,
                            chosenMethod.returnTypeName,
                            chosenMethod.paramTypeNames
                        )
                        statusMap["fold_check"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique foldCheckMethod: ${chosenMethod.className}.${chosenMethod.name}")
                    } else if (candidates.isEmpty()) {
                        statusMap["fold_check"] = HookPointState.MISSING
                        scanErrors.add("Fold check method: 0 candidates found for 'oplus_system_folding_mode'")
                    } else {
                        statusMap["fold_check"] = HookPointState.ERROR
                        scanErrors.add("Fold check method: ambiguous candidates count=${candidates.size}")
                    }
                } catch (t: Throwable) {
                    statusMap["fold_check"] = HookPointState.ERROR
                    scanErrors.add("Fold check scan exception: ${t.message}")
                    Log.e(TAG, "Fold check scan failed", t)
                }

                // ── 4. Resolve the keyboard View touch/draw methods on verified body View ──
                try {
                    val matchedBgMethods = bridge.findMethod {
                        matcher {
                            usingStrings("bgKeyNormalBlur", "bgKeyPressedBlur")
                        }
                    }
                    val kbCandidates = matchedBgMethods.filter {
                        it.paramCount == 2 &&
                        (it.paramTypeNames[1] == "boolean" || it.paramTypeNames[1] == "Z") &&
                        it.returnTypeName == "android.graphics.drawable.Drawable" &&
                        Modifier.isStatic(it.modifiers)
                    }

                    if (kbCandidates.size == 1) {
                        val bgMethod = kbCandidates[0]
                        keyBackgroundMethod = MethodDescriptor(
                            bgMethod.className,
                            bgMethod.name,
                            bgMethod.returnTypeName,
                            bgMethod.paramTypeNames
                        )
                        statusMap["key_background"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique keyBackgroundMethod: ${bgMethod.className}.${bgMethod.name}")

                        val kbClass = bgMethod.declaredClass ?: bridge.findClass {
                            matcher {
                                className = bgMethod.className
                            }
                        }.firstOrNull()

                        if (kbClass != null) {
                            val touchMethod = kbClass.methods.firstOrNull { method ->
                                method.name == "onTouchEvent" &&
                                    !Modifier.isStatic(method.modifiers) &&
                                    method.paramCount == 1 &&
                                    method.paramTypeNames[0] == "android.view.MotionEvent" &&
                                    (method.returnTypeName == "boolean" || method.returnTypeName == "Z")
                            }
                            val drawMethod = kbClass.methods.firstOrNull { method ->
                                method.name == "onDraw" &&
                                    !Modifier.isStatic(method.modifiers) &&
                                    method.paramCount == 1 &&
                                    method.paramTypeNames[0] == "android.graphics.Canvas" &&
                                    (method.returnTypeName == "void" || method.returnTypeName == "V")
                            }

                            if (touchMethod != null) {
                                keyboardTouchMethod = MethodDescriptor(
                                    touchMethod.className,
                                    touchMethod.name,
                                    touchMethod.returnTypeName,
                                    touchMethod.paramTypeNames
                                )
                                statusMap["effects_touch"] = HookPointState.FOUND
                                Log.i(TAG, "Found unique keyboard effects touch method on ${touchMethod.className}.${touchMethod.name}")
                            } else {
                                statusMap["effects_touch"] = HookPointState.MISSING
                                scanErrors.add("Effects touch method: not found on ${kbClass.name}")
                            }

                            if (drawMethod != null) {
                                keyboardDrawMethod = MethodDescriptor(
                                    drawMethod.className,
                                    drawMethod.name,
                                    drawMethod.returnTypeName,
                                    drawMethod.paramTypeNames
                                )
                                keyboardDrawMethodData = drawMethod
                                statusMap["effects_draw"] = HookPointState.FOUND
                                Log.i(TAG, "Found unique keyboard effects draw method on ${drawMethod.className}.${drawMethod.name}")
                            } else {
                                statusMap["effects_draw"] = HookPointState.MISSING
                                scanErrors.add("Effects draw method: not found on ${kbClass.name}")
                            }

                            // P0 structure check: the class is an android.view.View subclass and
                            // onDraw is declared on it. A failure leaves per-key unresolved.
                            val isViewSubclass = try {
                                val loaded = app.classLoader.loadClass(kbClass.name)
                                android.view.View::class.java.isAssignableFrom(loaded)
                            } catch (_: Throwable) {
                                false
                            }
                            if (isViewSubclass && drawMethod != null && drawMethod.className == kbClass.name) {
                                keyboardViewClassName = kbClass.name
                            } else {
                                keyboardViewClassName = null
                                scanErrors.add(
                                    "Per-key view start rejected: class=${kbClass.name} " +
                                        "isViewSubclass=$isViewSubclass " +
                                        "drawDeclaredOnClass=${drawMethod?.className ?: "null"}"
                                )
                            }
                        } else {
                            statusMap["effects_touch"] = HookPointState.ERROR
                            statusMap["effects_draw"] = HookPointState.ERROR
                            scanErrors.add("Keyboard body view: cannot resolve class for ${bgMethod.className}")
                        }
                    } else {
                        if (kbCandidates.isEmpty()) {
                            statusMap["key_background"] = HookPointState.MISSING
                            statusMap["effects_touch"] = HookPointState.MISSING
                            statusMap["effects_draw"] = HookPointState.MISSING
                            scanErrors.add("Keyboard body view: 0 candidates for bgKeyNormalBlur + bgKeyPressedBlur")
                        } else {
                            statusMap["key_background"] = HookPointState.ERROR
                            statusMap["effects_touch"] = HookPointState.ERROR
                            statusMap["effects_draw"] = HookPointState.ERROR
                            scanErrors.add("Keyboard body view: ambiguous candidates count=${kbCandidates.size}")
                        }
                    }
                } catch (t: Throwable) {
                    statusMap["effects_touch"] = HookPointState.ERROR
                    statusMap["effects_draw"] = HookPointState.ERROR
                    statusMap["key_background"] = HookPointState.ERROR
                    scanErrors.add("Effects keyboard view scan exception: ${t.message}")
                    Log.e(TAG, "Effects keyboard view scan failed", t)
                }
                // ── 5. Resolve DayNight Mode Check Method (AbstractC0864f.c) ──
                try {
                    val matchedMethods = bridge.findMethod {
                        matcher {
                            usingStrings("key_day_night_mode", "MODE_FOLLOW_SYS")
                        }
                    }
                    val candidates = matchedMethods.filter {
                        it.className.startsWith("com.oplus.keyboard") &&
                        it.paramCount == 0 &&
                        (it.returnTypeName == "boolean" || it.returnTypeName == "Z") &&
                        Modifier.isStatic(it.modifiers)
                    }

                    if (candidates.size == 1) {
                        val m = candidates[0]
                        dayNightCheckMethod = MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                        statusMap["day_night_check"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique dayNightCheckMethod: ${m.className}.${m.name}")
                    } else if (candidates.isEmpty()) {
                        statusMap["day_night_check"] = HookPointState.MISSING
                        scanErrors.add("DayNight check method: 0 candidates found for key_day_night_mode + MODE_FOLLOW_SYS")
                    } else {
                        statusMap["day_night_check"] = HookPointState.ERROR
                        scanErrors.add("DayNight check method: ambiguous candidates count=${candidates.size}")
                    }
                } catch (t: Throwable) {
                    statusMap["day_night_check"] = HookPointState.ERROR
                    scanErrors.add("DayNight check scan exception: ${t.message}")
                    Log.e(TAG, "DayNight check scan failed", t)
                }

                // ── 6. Resolve Blur Param Create Method (d.r) ──
                try {
                    val matchedMethods = bridge.findMethod {
                        matcher {
                            usingStrings("bgKeyboardBlur")
                        }
                    }
                    val candidates = matchedMethods.filter {
                        it.paramCount == 0 &&
                        it.returnTypeName.endsWith("OplusBlurParam") &&
                        Modifier.isStatic(it.modifiers)
                    }

                    if (candidates.size == 1) {
                        val m = candidates[0]
                        blurParamCreateMethod = MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                        statusMap["blur_param_create"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique blurParamCreateMethod: ${m.className}.${m.name}")
                    } else if (candidates.isEmpty()) {
                        statusMap["blur_param_create"] = HookPointState.MISSING
                        scanErrors.add("Blur param create method: 0 candidates found for 'bgKeyboardBlur' -> OplusBlurParam")
                    } else {
                        statusMap["blur_param_create"] = HookPointState.ERROR
                        scanErrors.add("Blur param create method: ambiguous candidates count=${candidates.size}")
                    }
                } catch (t: Throwable) {
                    statusMap["blur_param_create"] = HookPointState.ERROR
                    scanErrors.add("Blur param create scan exception: ${t.message}")
                    Log.e(TAG, "Blur param create scan failed", t)
                }

                // ── 6b/6c. blur radius symbols removed ──
                // The former implementation used DexKit to locate the host blur helper class
                // (Oplus blur.b); that class has no string constants, and ViewRootManager belongs
                // to the Oplus framework and is absent from the host APK dex.
                // The path is now a runtime reflection hook on
                // com.oplus.view.ViewRootManager.setBlurRadius(int) (see OplusLiquidGlassHook.install
                // 3.5). One constant FOUND state bit is kept for log output.
                statusMap["blur_radius_runtime"] = HookPointState.FOUND

                // ── 7. Resolve Key Background Method (C1116t.h) ──
                if (keyBackgroundMethod == null) {
                    try {
                        val matchedMethods = bridge.findMethod {
                            matcher {
                                usingStrings("bgKeyNormalBlur", "bgKeyPressedBlur")
                            }
                        }
                        val candidates = matchedMethods.filter {
                            it.paramCount == 2 &&
                            (it.paramTypeNames[1] == "boolean" || it.paramTypeNames[1] == "Z") &&
                            it.returnTypeName == "android.graphics.drawable.Drawable" &&
                            Modifier.isStatic(it.modifiers)
                        }

                        if (candidates.size == 1) {
                            val m = candidates[0]
                            keyBackgroundMethod = MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                            statusMap["key_background"] = HookPointState.FOUND
                            Log.i(TAG, "Found unique keyBackgroundMethod: ${m.className}.${m.name}")
                        } else if (candidates.isEmpty()) {
                            statusMap["key_background"] = HookPointState.MISSING
                            scanErrors.add("Key background method: 0 candidates found for 'bgKeyNormalBlur' + 'bgKeyPressedBlur'")
                        } else {
                            statusMap["key_background"] = HookPointState.ERROR
                            scanErrors.add("Key background method: ambiguous candidates count=${candidates.size}")
                        }
                    } catch (t: Throwable) {
                        statusMap["key_background"] = HookPointState.ERROR
                        scanErrors.add("Key background method scan exception: ${t.message}")
                        Log.e(TAG, "Key background scan failed", t)
                    }
                }
                // ── 8. Resolve Key Alpha Getters (key translucency, theme work objects) ──
                //
                // Old host draw path:
                //   Float fT = <work>.t();
                //   drawable.setAlpha(fT != null ? clamp((int)(fT * 255), 0, 255) : 255);
                // <work> is the theme work object (implementation of
                // com.oplus.keyboard.base.theme.work.a); the facade
                // com.oplus.keyboard.base.theme.b#t() forwards and is not on the draw path.
                // All work implementations are collected so every theme type is covered.
                //
                // New host draw path (1.9.18+):
                //   drawable.setAlpha(keySkinGroup.getKeyBGAlpha255());
                // The skin group precomputes the 0..255 alpha; the work getter is no longer used.
                try {
                    val candidates = bridge.findClass {
                        searchPackages("com.oplus.keyboard.base.theme.data")
                    }.asSequence()
                        .flatMap { it.methods.asSequence() }
                        .filter { m ->
                            m.name == "getKeyBGAlpha255" &&
                                m.paramCount == 0 &&
                                (m.returnTypeName == "int" || m.returnTypeName == "I")
                        }
                        .distinctBy { it.className + "#" + it.name }
                        .toList()
                    when (candidates.size) {
                        0 -> Unit
                        1 -> {
                            val method = candidates.single()
                            keyBgAlpha255Method = MethodDescriptor(
                                method.className,
                                method.name,
                                method.returnTypeName,
                                method.paramTypeNames
                            )
                            statusMap["key_alpha"] = HookPointState.FOUND
                            Log.i(TAG, "Found new key alpha getter: ${method.className}.${method.name}")
                        }
                        else -> scanErrors.add(
                            "Key alpha: ${candidates.size} getKeyBGAlpha255 candidates, cannot disambiguate; skipping the new path"
                        )
                    }
                } catch (t: Throwable) {
                    scanErrors.add("Key alpha new-path scan exception: ${t.message}")
                    Log.e(TAG, "Key alpha new-path scan failed", t)
                }
                try {
                    val themeClasses = buildList {
                        // searchPackages matches exactly or by prefix depending on the DexKit
                        // version; both parent and child packages are queried, then prefix-filtered.
                        addAll(bridge.findClass { searchPackages("com.oplus.keyboard.base.theme") })
                        addAll(bridge.findClass { searchPackages("com.oplus.keyboard.base.theme.work") })
                    }.filter { it.name.startsWith("com.oplus.keyboard.base.theme.work.") }
                        .distinctBy { it.name }

                    val collected = mutableListOf<MethodData>()
                    for (clz in themeClasses) {
                        val perClass = clz.methods.filter { m ->
                            !Modifier.isStatic(m.modifiers) &&
                                !Modifier.isAbstract(m.modifiers) &&
                                m.paramCount == 0 &&
                                (m.returnTypeName == "java.lang.Float" || m.returnTypeName == "Ljava/lang/Float;")
                        }
                        when (perClass.size) {
                            0 -> Unit
                            1 -> collected += perClass[0]
                            else -> scanErrors.add(
                                "Key alpha: ${clz.name} has ${perClass.size} no-arg Float methods, " +
                                    "cannot disambiguate; skipping this class"
                            )
                        }
                    }
                    val distinct = collected.distinctBy { it.className + "#" + it.name }

                    // Facade getter: the single class in the exact `base.theme` package whose
                    // only 0-arg Float method is the one the key draw path calls
                    // (body/<keyView>#drawKey -> <facade>#t()). Hooking the facade is more robust
                    // than the work implementations, whose class set changes across host versions.
                    val facadeGetter = runCatching {
                        val facadeClasses = bridge.findClass {
                            searchPackages("com.oplus.keyboard.base.theme")
                        }.filter { clz ->
                            // Exact package only: no further dots after `base.theme`.
                            clz.name.substringAfter("com.oplus.keyboard.base.theme.").let { rest ->
                                rest.isNotEmpty() && !rest.contains('.')
                            }
                        }
                        val perClass = facadeClasses.mapNotNull { clz ->
                            val getters = clz.methods.filter { m ->
                                !Modifier.isStatic(m.modifiers) &&
                                    !Modifier.isAbstract(m.modifiers) &&
                                    m.paramCount == 0 &&
                                    (m.returnTypeName == "java.lang.Float" ||
                                        m.returnTypeName == "Ljava/lang/Float;")
                            }
                            if (getters.size == 1) getters[0] else null
                        }.distinctBy { it.className + "#" + it.name }
                        perClass.singleOrNull()
                    }.getOrNull()

                    if (distinct.isEmpty()) {
                        if (statusMap["key_alpha"] != HookPointState.FOUND) {
                            statusMap["key_alpha"] = HookPointState.MISSING
                            scanErrors.add(
                                "Key alpha: neither the legacy work.t nor KeySkinGroup.getKeyBGAlpha255 was found"
                            )
                        }
                    } else {
                        keyAlphaMethods += distinct.map { m ->
                            MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                        }
                        statusMap["key_alpha"] = HookPointState.FOUND
                        Log.i(
                            TAG,
                            "Found ${distinct.size} keyAlphaMethod(s): " +
                                distinct.joinToString { it.className + "." + it.name }
                        )
                    }

                    // Buffered key renderers paint the keys into a cached Bitmap and only rebuild
                    // it when a refresh flag is set. Structural rule, so it survives host versions
                    // that rename the accessor:
                    //   1. class declares onDraw(Canvas) and owns an instance Bitmap + Canvas pair
                    //      (the off-screen key buffer), and
                    //   2. the refresh setter is setDrawRefresh(boolean) when present, otherwise the
                    //      class single void(boolean) method.
                    val rendererClasses = bridge.findClass {
                        searchPackages(
                            "com.oplus.keyboard.input.view.body",
                            "com.oplus.keyboard.lib_themes.kb"
                        )
                    }.filter { clz ->
                        val inTargetPackage =
                            clz.name.startsWith("com.oplus.keyboard.input.view.body.") ||
                                clz.name.startsWith("com.oplus.keyboard.lib_themes.kb.")
                        inTargetPackage &&
                            clz.fields.any {
                                !Modifier.isStatic(it.modifiers) && it.typeName == "android.graphics.Bitmap"
                            } &&
                            clz.fields.any {
                                !Modifier.isStatic(it.modifiers) && it.typeName == "android.graphics.Canvas"
                            } &&
                            clz.methods.any {
                                it.name == "onDraw" && it.paramCount == 1
                            }
                    }

                    val refreshMethods = rendererClasses.mapNotNull { clz ->
                        val booleanSetters = clz.methods.filter { m ->
                            !Modifier.isStatic(m.modifiers) &&
                                !Modifier.isAbstract(m.modifiers) &&
                                m.paramCount == 1 &&
                                m.returnTypeName == "void" &&
                                m.paramTypeNames.firstOrNull() == "boolean"
                        }
                        booleanSetters.firstOrNull { it.name == "setDrawRefresh" }
                            ?: booleanSetters.singleOrNull()
                    }.distinctBy { it.className + "#" + it.name }

                    if (refreshMethods.isNotEmpty()) {
                        keyBufferRefreshMethods += refreshMethods.map { m ->
                            MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                        }
                        Log.i(
                            TAG,
                            "Found ${refreshMethods.size} key buffer refresh setter(s): " +
                                refreshMethods.joinToString { it.className + "." + it.name }
                        )
                    } else {
                        Log.w(TAG, "No key buffer refresh setter; key alpha stays restart-only")
                    }

                    // Append the facade after the impls: same signature, same hook body, so the
                    // existing install loop covers it without extra symbols or state.
                    if (facadeGetter != null) {
                        val facadeDescriptor = MethodDescriptor(
                            facadeGetter.className,
                            facadeGetter.name,
                            facadeGetter.returnTypeName,
                            facadeGetter.paramTypeNames
                        )
                        if (keyAlphaMethods.none {
                                it.className == facadeDescriptor.className &&
                                    it.methodName == facadeDescriptor.methodName
                            }
                        ) {
                            keyAlphaMethods += facadeDescriptor
                            statusMap["key_alpha"] = HookPointState.FOUND
                            Log.i(
                                TAG,
                                "Found key alpha facade getter: " +
                                    "${facadeDescriptor.className}.${facadeDescriptor.methodName}"
                            )
                        }
                    }
                } catch (t: Throwable) {
                    statusMap["key_alpha"] = HookPointState.ERROR
                    scanErrors.add("Key alpha method scan exception: ${t.message}")
                    Log.e(TAG, "Key alpha scan failed", t)
                }
                // ── 9. Resolve Toolbar Layout Method (ToolBarView.j(List)) ──
                try {
                    val matchedMethods = bridge.findMethod {
                        searchPackages("com.oplus.keyboard.input.view")
                        matcher {
                            paramTypes("java.util.List")
                            usingStrings("key_toolbar_align_right")
                            returnType("void")
                        }
                    }
                    if (matchedMethods.size == 1) {
                        val m = matchedMethods[0]
                        toolbarLayoutMethod = MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                        statusMap["toolbar_layout"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique toolbarLayoutMethod: ${m.className}.${m.name}")
                    } else if (matchedMethods.isEmpty()) {
                        statusMap["toolbar_layout"] = HookPointState.MISSING
                        scanErrors.add("Toolbar layout method: 0 candidates found for List + 'key_toolbar_align_right'")
                    } else {
                        statusMap["toolbar_layout"] = HookPointState.ERROR
                        scanErrors.add("Toolbar layout method: ambiguous candidates count=${matchedMethods.size}")
                    }
                } catch (t: Throwable) {
                    statusMap["toolbar_layout"] = HookPointState.ERROR
                    scanErrors.add("Toolbar layout scan exception: ${t.message}")
                    Log.e(TAG, "Toolbar layout scan failed", t)
                }

                // ── 10. Resolve Toolbar Item Icon Method (e.b()) ──
                try {
                    val matchedMethods = bridge.findMethod {
                        searchPackages("com.oplus.keyboard.input.ui.data")
                        matcher {
                            paramCount(0)
                            returnType("android.graphics.drawable.Drawable")
                            usingStrings("colorToolbarIconNormal")
                        }
                    }
                    if (matchedMethods.size == 1) {
                        val m = matchedMethods[0]
                        toolbarItemIconMethod = MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                        statusMap["toolbar_item_icon"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique toolbarItemIconMethod: ${m.className}.${m.name}")
                    } else if (matchedMethods.isEmpty()) {
                        statusMap["toolbar_item_icon"] = HookPointState.MISSING
                        scanErrors.add("Toolbar item icon method: 0 candidates found for Drawable + 'colorToolbarIconNormal'")
                    } else {
                        statusMap["toolbar_item_icon"] = HookPointState.ERROR
                        scanErrors.add("Toolbar item icon method: ambiguous candidates count=${matchedMethods.size}")
                    }
                } catch (t: Throwable) {
                    statusMap["toolbar_item_icon"] = HookPointState.ERROR
                    scanErrors.add("Toolbar item icon scan exception: ${t.message}")
                    Log.e(TAG, "Toolbar item icon scan failed", t)
                }
                // ── 11. Resolve Tool Catalog Create Method (ToolStateManager.c(Context)) ──
                try {
                    val matchedClasses = bridge.findClass {
                        searchPackages("com.oplus.keyboard.input.manager")
                        matcher {
                            usingStrings("ToolStateManager")
                        }
                    }
                    val candidates = matchedClasses.flatMap { clz ->
                        clz.methods.filter { m ->
                            Modifier.isStatic(m.modifiers) &&
                            m.paramCount == 1 &&
                            (m.paramTypeNames[0] == "android.content.Context" || m.paramTypeNames[0] == "Landroid/content/Context;") &&
                            !m.returnTypeName.startsWith("void") &&
                            !m.returnTypeName.startsWith("boolean")
                        }
                    }
                    if (candidates.size == 1) {
                        val m = candidates[0]
                        toolCatalogCreateMethod = MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                        statusMap["tool_catalog_create"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique toolCatalogCreateMethod: ${m.className}.${m.name}")
                    } else if (candidates.isEmpty()) {
                        statusMap["tool_catalog_create"] = HookPointState.MISSING
                        scanErrors.add("Tool catalog create method: 0 candidates found for Context in ToolStateManager")
                    } else {
                        statusMap["tool_catalog_create"] = HookPointState.ERROR
                        scanErrors.add("Tool catalog create method: ambiguous candidates count=${candidates.size}")
                    }
                } catch (t: Throwable) {
                    statusMap["tool_catalog_create"] = HookPointState.ERROR
                    scanErrors.add("Tool catalog create method scan exception: ${t.message}")
                    Log.e(TAG, "Tool catalog create scan failed", t)
                }

                // ── 12. Resolve Toolbox Data Method (ToolBoxView.getBoxData()) ──
                try {
                    val matchedMethods = bridge.findMethod {
                        searchPackages("com.oplus.keyboard.input.toolbox")
                        matcher {
                            paramCount(0)
                            usingStrings("iconToolboxSwitchBoard")
                        }
                    }
                    val candidates = matchedMethods.filter {
                        it.returnTypeName == "java.util.List" || it.returnTypeName.endsWith(".List")
                    }
                    if (candidates.size == 1) {
                        val m = candidates[0]
                        toolboxDataMethod = MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                        statusMap["toolbox_data"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique toolboxDataMethod: ${m.className}.${m.name}")
                    } else if (candidates.isEmpty()) {
                        statusMap["toolbox_data"] = HookPointState.MISSING
                        scanErrors.add("Toolbox data method: 0 candidates found for 'iconToolboxSwitchBoard'")
                    } else {
                        statusMap["toolbox_data"] = HookPointState.ERROR
                        scanErrors.add("Toolbox data method: ambiguous candidates count=${candidates.size}")
                    }
                } catch (t: Throwable) {
                    statusMap["toolbox_data"] = HookPointState.ERROR
                    scanErrors.add("Toolbox data method scan exception: ${t.message}")
                    Log.e(TAG, "Toolbox data scan failed", t)
                }

                // ── 13. Resolve ToolStateManager Transform Method (ToolStateManager.j(List, List): Pair) ──
                try {
                    val matchedClasses = bridge.findClass {
                        searchPackages("com.oplus.keyboard.input.manager")
                        matcher {
                            usingStrings("ToolStateManager")
                        }
                    }
                    val candidates = matchedClasses.flatMap { clz ->
                        clz.methods.filter { m ->
                            Modifier.isStatic(m.modifiers) &&
                            m.paramCount == 2 &&
                            (m.paramTypeNames[0] == "java.util.List" || m.paramTypeNames[0] == "Ljava/util/List;") &&
                            (m.paramTypeNames[1] == "java.util.List" || m.paramTypeNames[1] == "Ljava/util/List;") &&
                            (m.returnTypeName == "kotlin.Pair" || m.returnTypeName == "Lkotlin/Pair;" || m.returnTypeName.endsWith("Pair"))
                        }
                    }
                    if (candidates.size == 1) {
                        val m = candidates[0]
                        toolStateManagerTransformMethod = MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                        statusMap["tool_state_transform"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique toolStateManagerTransformMethod: ${m.className}.${m.name}")
                    } else if (candidates.isEmpty()) {
                        statusMap["tool_state_transform"] = HookPointState.MISSING
                        scanErrors.add("ToolStateManager transform method: 0 candidates found for List, List -> Pair")
                    } else {
                        statusMap["tool_state_transform"] = HookPointState.ERROR
                        scanErrors.add("ToolStateManager transform method: ambiguous candidates count=${candidates.size}")
                    }
                } catch (t: Throwable) {
                    statusMap["tool_state_transform"] = HookPointState.ERROR
                    scanErrors.add("ToolStateManager transform method scan exception: ${t.message}")
                    Log.e(TAG, "ToolStateManager transform scan failed", t)
                }

                // ── 14. Resolve Theme Icon Method (Theme resource loader c.J(String, ThemeCompatMode): Drawable) ──
                try {
                    val matchedMethods = bridge.findMethod {
                        matcher {
                            paramCount(2)
                            paramTypes("java.lang.String", "com.oplus.keyboard.base.theme.enums.ThemeCompatMode")
                            returnType("android.graphics.drawable.Drawable")
                            modifiers(Modifier.STATIC)
                        }
                    }
                    if (matchedMethods.size == 1) {
                        val m = matchedMethods[0]
                        themeIconMethod = MethodDescriptor(m.className, m.name, m.returnTypeName, m.paramTypeNames)
                        statusMap["theme_icon"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique themeIconMethod: ${m.className}.${m.name}")
                    } else if (matchedMethods.isEmpty()) {
                        statusMap["theme_icon"] = HookPointState.MISSING
                        scanErrors.add("Theme icon method: 0 candidates found for (String, ThemeCompatMode) -> Drawable")
                    } else {
                        statusMap["theme_icon"] = HookPointState.ERROR
                        scanErrors.add("Theme icon method: ambiguous candidates count=${matchedMethods.size}")
                    }
                } catch (t: Throwable) {
                    statusMap["theme_icon"] = HookPointState.ERROR
                    scanErrors.add("Theme icon scan exception: ${t.message}")
                    Log.e(TAG, "Theme icon scan failed", t)
                }

                // ── 14b. Resolve ThemeManager (com.oplus.keyboard.base.theme.b) ──
                // ── 14b. Resolve ThemeManager (com.oplus.keyboard.base.theme.b) ──
                try {
                    val themeClasses = bridge.findClass {
                        searchPackages("com.oplus.keyboard.base.theme")
                        matcher {
                            usingStrings("Theme.Keyboard.Light")
                        }
                    }
                    if (themeClasses.isNotEmpty()) {
                        val clz = themeClasses[0]
                        themeManagerClass = ClassDescriptor(clz.name)
                        statusMap["theme_manager_class"] = HookPointState.FOUND
                        Log.i(TAG, "Found unique themeManagerClass: ${clz.name}")

                        val applyM = clz.methods.firstOrNull { m ->
                            m.paramTypeNames == listOf("java.lang.String") || m.paramTypeNames == listOf("Ljava/lang/String;")
                        }
                        if (applyM != null) {
                            themeManagerApplyMethod = MethodDescriptor(applyM.className, applyM.name, applyM.returnTypeName, applyM.paramTypeNames)
                            statusMap["theme_manager_apply"] = HookPointState.FOUND
                        }

                        val innerM = clz.methods.firstOrNull { m ->
                            m.paramCount == 0 && (m.returnTypeName == "boolean" || m.returnTypeName == "Z")
                        }
                        if (innerM != null) {
                            themeManagerInnerMethod = MethodDescriptor(innerM.className, innerM.name, innerM.returnTypeName, innerM.paramTypeNames)
                            statusMap["theme_manager_inner"] = HookPointState.FOUND
                        }

                        val f = clz.fields.firstOrNull { Modifier.isStatic(it.modifiers) }
                        if (f != null) {
                            themeManagerLazyField = FieldDescriptor(f.className, f.name, f.typeName)
                            statusMap["theme_manager_lazy"] = HookPointState.FOUND
                        }
                    } else {
                        statusMap["theme_manager"] = HookPointState.MISSING
                        scanErrors.add("ThemeManager: 0 classes found for 'Theme.Keyboard.Light'")
                    }
                } catch (t: Throwable) {
                    statusMap["theme_manager"] = HookPointState.ERROR
                    scanErrors.add("ThemeManager scan exception: ${t.message}")
                    Log.e(TAG, "ThemeManager scan failed", t)
                }

                // ── 15. Resolve per-key symbols (model getter / entity / hit-test / bounds) ──
                try {
                    val perkeyResult = resolvePerKeySymbols(
                        bridge = bridge,
                        keyboardViewClass = keyboardViewClassName,
                        keyboardDrawMethod = keyboardDrawMethod,
                        keyBackgroundMethod = keyBackgroundMethod,
                        scanErrors = scanErrors
                    )
                    perkeyModelGetter = perkeyResult.modelGetter
                    perkeyEntityClass = perkeyResult.entityClass
                    perkeyHitTestMethod = perkeyResult.hitTestMethod
                    perkeyLeftField = perkeyResult.leftField
                    perkeyRightField = perkeyResult.rightField
                    perkeyTopField = perkeyResult.topField
                    perkeyBottomField = perkeyResult.bottomField
                    perkeyDrawMethod = perkeyResult.drawMethod
                    perkeyPressedField = perkeyResult.pressedField
                    perkeyRedrawMethod = perkeyResult.redrawMethod
                    statusMap.putAll(perkeyResult.statusMap)
                } catch (t: Throwable) {
                    statusMap["perkey_model_getter"] = HookPointState.ERROR
                    statusMap["perkey_entity"] = HookPointState.ERROR
                    statusMap["perkey_hit_test"] = HookPointState.ERROR
                    statusMap["perkey_bounds"] = HookPointState.ERROR
                    statusMap["perkey_draw"] = HookPointState.ERROR
                    statusMap["perkey_redraw"] = HookPointState.ERROR
                    scanErrors.add("Per-key scan exception: ${t.message}")
                    Log.e(TAG, "Per-key scan failed", t)
                }

                // ── 16. Resolve the two native key-shadow passes called directly by keyboard onDraw ──
                try {
                    val onDrawInvokes = keyboardDrawMethodData?.invokes.orEmpty()
                    val shadowShapeCandidates = onDrawInvokes.filter { method ->
                        Modifier.isStatic(method.modifiers) &&
                            method.paramTypeNames == listOf(
                                "android.graphics.Canvas",
                                "java.util.List",
                                "boolean"
                            ) &&
                            (method.returnTypeName == "void" || method.returnTypeName == "V")
                    }.distinctBy { it.descriptor }

                    val underlayCandidates = shadowShapeCandidates.filter { method ->
                        method.invokes.any { invoked ->
                            invoked.className == "android.graphics.Canvas" && invoked.name == "drawBitmap"
                        }
                    }
                    val overlayCandidates = shadowShapeCandidates.filter { method ->
                        method.invokes.any { invoked ->
                            invoked.className == "android.graphics.Canvas" &&
                                invoked.name == "isHardwareAccelerated" &&
                                invoked.paramTypeNames.isEmpty()
                        }
                    }

                    val shadowPassesAreUnique =
                        underlayCandidates.size == 1 &&
                            overlayCandidates.size == 1 &&
                            underlayCandidates[0].descriptor != overlayCandidates[0].descriptor
                    if (shadowPassesAreUnique) {
                        val underlay = underlayCandidates.single()
                        val overlay = overlayCandidates.single()
                        perkeyShadowUnderlayMethod = MethodDescriptor(
                            underlay.className,
                            underlay.name,
                            underlay.returnTypeName,
                            underlay.paramTypeNames
                        )
                        perkeyShadowOverlayMethod = MethodDescriptor(
                            overlay.className,
                            overlay.name,
                            overlay.returnTypeName,
                            overlay.paramTypeNames
                        )
                        statusMap["perkey_shadow_underlay"] = HookPointState.FOUND
                        statusMap["perkey_shadow_overlay"] = HookPointState.FOUND
                        Log.i(TAG, "Found key shadow underlay method: ${underlay.className}.${underlay.name}")
                        Log.i(TAG, "Found key shadow overlay method: ${overlay.className}.${overlay.name}")
                    } else {
                        val state = if (
                            underlayCandidates.isEmpty() || overlayCandidates.isEmpty()
                        ) {
                            HookPointState.MISSING
                        } else {
                            HookPointState.ERROR
                        }
                        statusMap["perkey_shadow_underlay"] = state
                        statusMap["perkey_shadow_overlay"] = state
                        scanErrors.add(
                            "Per-key shadow passes not uniquely separated: " +
                                "underlay=${underlayCandidates.size} overlay=${overlayCandidates.size}"
                        )
                    }
                } catch (t: Throwable) {
                    statusMap["perkey_shadow_underlay"] = HookPointState.ERROR
                    statusMap["perkey_shadow_overlay"] = HookPointState.ERROR
                    scanErrors.add("Per-key shadow scan exception: ${t.message}")
                    Log.e(TAG, "Per-key shadow scan failed", t)
                }
            }
        } catch (t: Throwable) {
            val err = "DexKit scan failed: ${t.message}"
            Log.e(TAG, err, t)
            scanErrors.add(err)
        }

        return OplusInputSymbols(
            ruleVersion = RULE_VERSION,
            hostFingerprint = hostFingerprint,
            moduleVersionCode = moduleVersionCode,
            lastAdaptedVersion = hostFingerprint,
            whitelistField = whitelistField,
            blurDecisionMethod = blurDecisionMethod,
            blurApplyMethod = blurApplyMethod,
            foldCheckMethod = foldCheckMethod,
            keyboardTouchMethod = keyboardTouchMethod,
            keyboardDrawMethod = keyboardDrawMethod,
            dayNightCheckMethod = dayNightCheckMethod,
            blurParamCreateMethod = blurParamCreateMethod,
            keyBackgroundMethod = keyBackgroundMethod,
            keyAlphaMethods = keyAlphaMethods,
            keyBgAlpha255Method = keyBgAlpha255Method,
            keyBufferRefreshMethods = keyBufferRefreshMethods,
            toolbarLayoutMethod = toolbarLayoutMethod,
            toolbarItemIconMethod = toolbarItemIconMethod,
            toolCatalogCreateMethod = toolCatalogCreateMethod,
            toolboxDataMethod = toolboxDataMethod,
            toolStateManagerTransformMethod = toolStateManagerTransformMethod,
            themeIconMethod = themeIconMethod,
            themeManagerClass = themeManagerClass,
            themeManagerLazyField = themeManagerLazyField,
            themeManagerInnerMethod = themeManagerInnerMethod,
            themeManagerApplyMethod = themeManagerApplyMethod,
            perkeyModelGetter = perkeyModelGetter,
            perkeyEntityClass = perkeyEntityClass,
            perkeyHitTestMethod = perkeyHitTestMethod,
            perkeyLeftField = perkeyLeftField,
            perkeyRightField = perkeyRightField,
            perkeyTopField = perkeyTopField,
            perkeyBottomField = perkeyBottomField,
            perkeyDrawMethod = perkeyDrawMethod,
            perkeyShadowUnderlayMethod = perkeyShadowUnderlayMethod,
            perkeyShadowOverlayMethod = perkeyShadowOverlayMethod,
            perkeyPressedField = perkeyPressedField,
            perkeyRedrawMethod = perkeyRedrawMethod,
            statusMap = statusMap,
            scanErrors = scanErrors
        )
    }

    /**
     * Per-key symbol resolution (P0-P6).
     *
     * Starts from the resolved keyboard View and fails closed per stage:
     * P1 model getter, P2 entity, P3 hit-test, P4 bounds; P5/P6 are optional.
     */
    private fun resolvePerKeySymbols(
        bridge: DexKitBridge,
        keyboardViewClass: String?,
        keyboardDrawMethod: MethodDescriptor?,
        keyBackgroundMethod: MethodDescriptor?,
        scanErrors: MutableList<String>
    ): PerKeyResult {
        val statusMap = mutableMapOf<String, HookPointState>()
        var perkeyDrawMethod: MethodDescriptor? = null

        // ── P0. Start check: a structurally verified keyboard View and a resolved onDraw ──
        if (keyboardViewClass == null || keyboardDrawMethod == null) {
            statusMap["perkey_model_getter"] = HookPointState.ERROR
            statusMap["perkey_entity"] = HookPointState.MISSING
            statusMap["perkey_hit_test"] = HookPointState.MISSING
            statusMap["perkey_bounds"] = HookPointState.MISSING
            scanErrors.add("Per-key: keyboard View start unavailable (view=$keyboardViewClass draw=${keyboardDrawMethod != null})")
            return PerKeyResult(statusMap = statusMap)
        }

        // ── P1. model getter: no-arg, non-static View method returning a class with a List field ──
        val modelGetterCandidates = try {
            bridge.findMethod {
                matcher {
                    declaredClass(keyboardViewClass)
                    paramCount(0)
                }
            }.filter { m ->
                !Modifier.isStatic(m.modifiers) && !m.isConstructor &&
                    !isPrimitiveOrVoidName(m.returnTypeName) &&
                    m.returnTypeName != keyboardViewClass &&
                    hasDeclaredListField(loadClassData(bridge, m.returnTypeName))
            }
        } catch (t: Throwable) {
            statusMap["perkey_model_getter"] = HookPointState.ERROR
            statusMap["perkey_entity"] = HookPointState.MISSING
            statusMap["perkey_hit_test"] = HookPointState.MISSING
            statusMap["perkey_bounds"] = HookPointState.MISSING
            scanErrors.add("Per-key model getter scan exception: ${t.message}")
            return PerKeyResult(statusMap = statusMap)
        }

        if (modelGetterCandidates.isEmpty()) {
            statusMap["perkey_model_getter"] = HookPointState.MISSING
            statusMap["perkey_entity"] = HookPointState.MISSING
            statusMap["perkey_hit_test"] = HookPointState.MISSING
            statusMap["perkey_bounds"] = HookPointState.MISSING
            scanErrors.add("Per-key model getter: 0 shape candidates on $keyboardViewClass")
            return PerKeyResult(statusMap = statusMap)
        }

        // Filters candidates by onDraw invokes.
        val drawInvokeDescs = try {
            val drawData = if (keyboardDrawMethod.className == keyboardViewClass) {
                bridge.findMethod {
                    matcher {
                        declaredClass(keyboardViewClass)
                        paramCount(1)
                        paramTypes("android.graphics.Canvas")
                    }
                }.filter { it.name == keyboardDrawMethod.methodName }.firstOrNull()
            } else null
            drawData?.invokes?.map { it.descriptor }?.toSet()
        } catch (t: Throwable) {
            scanErrors.add("Per-key model getter: onDraw invokes unreadable: ${t.message}")
            null
        }

        val drawCallsGetter = if (drawInvokeDescs != null) {
            modelGetterCandidates.filter { it.descriptor in drawInvokeDescs }
        } else {
            emptyList()
        }

        if (drawCallsGetter.size != 1) {
            statusMap["perkey_model_getter"] =
                if (drawCallsGetter.isEmpty()) HookPointState.MISSING else HookPointState.ERROR
            statusMap["perkey_entity"] = HookPointState.MISSING
            statusMap["perkey_hit_test"] = HookPointState.MISSING
            statusMap["perkey_bounds"] = HookPointState.MISSING
            scanErrors.add(
                "Per-key model getter: onDraw-filtered candidates=${drawCallsGetter.size} " +
                    "(shape=${modelGetterCandidates.size}, drawInvokes=${drawInvokeDescs?.size ?: -1})"
            )
            Log.w(
                TAG,
                "Per-key P1 fail: shapeCandidates=${modelGetterCandidates.size} " +
                    "drawInvokes=${drawInvokeDescs?.size ?: -1} matched=${drawCallsGetter.size} " +
                    "view=$keyboardViewClass draw=${keyboardDrawMethod.className}.${keyboardDrawMethod.methodName}"
            )
            return PerKeyResult(statusMap = statusMap)
        }

        val getter = drawCallsGetter[0]
        statusMap["perkey_model_getter"] = HookPointState.FOUND
        val modelGetter = MethodDescriptor(getter.className, getter.name, getter.returnTypeName, getter.paramTypeNames)
        val modelClassName = getter.returnTypeName
        Log.i(TAG, "Found unique perkeyModelGetter: ${getter.className}.${getter.name} -> $modelClassName")

        val listFieldMatcher = FieldMatcher()
            .declaredClass(modelClassName)
            .type("java.util.List")

        // ── P2. entity class: non-static (int,int) method on the model returning a class with >=4 int fields ──
        val intIntMethods = try {
            bridge.findMethod {
                matcher {
                    declaredClass(modelClassName)
                    paramCount(2)
                    paramTypes("int", "int")
                }
            }.filter { m ->
                !Modifier.isStatic(m.modifiers) && !isPrimitiveOrVoidName(m.returnTypeName) &&
                    m.returnTypeName != "java.lang.Object"
            }
        } catch (t: Throwable) {
            statusMap["perkey_entity"] = HookPointState.ERROR
            statusMap["perkey_hit_test"] = HookPointState.MISSING
            statusMap["perkey_bounds"] = HookPointState.MISSING
            scanErrors.add("Per-key entity scan exception: ${t.message}")
            return PerKeyResult(modelGetter = modelGetter, statusMap = statusMap)
        }

        val entityCandidates = intIntMethods.mapNotNull { m ->
            runCatching {
                loadClassData(bridge, m.returnTypeName)?.takeIf { entityShape(it) }
            }.getOrNull()
        }.distinctBy { it.name }

        if (entityCandidates.size != 1) {
            statusMap["perkey_entity"] =
                if (entityCandidates.isEmpty()) HookPointState.MISSING else HookPointState.ERROR
            statusMap["perkey_hit_test"] = HookPointState.MISSING
            statusMap["perkey_bounds"] = HookPointState.MISSING
            scanErrors.add("Per-key entity: candidates=${entityCandidates.size} from intIntMethods=${intIntMethods.size}")
            Log.w(TAG, "Per-key P2 fail: intIntMethods=${intIntMethods.size} entityCandidates=${entityCandidates.size} on model=$modelClassName")
            return PerKeyResult(modelGetter = modelGetter, statusMap = statusMap)
        }

        val entityClassData = entityCandidates[0]
        val entityClassName = entityClassData.name
        statusMap["perkey_entity"] = HookPointState.FOUND
        Log.i(TAG, "Found unique perkeyEntity: $entityClassName")

        val entityIntFieldMatcher = FieldMatcher()
            .declaredClass(entityClassName)
            .type("int")

        // ── P3. hit-test: non-static (int,int)->entity on the model, reading the model List and entity int fields ──
        val hitTestCandidates = try {
            bridge.findMethod {
                matcher {
                    declaredClass(modelClassName)
                    paramCount(2)
                    paramTypes("int", "int")
                    returnType(entityClassName)
                    addUsingField(listFieldMatcher, QueryUsingType.Read)
                    addUsingField(entityIntFieldMatcher, QueryUsingType.Read)
                }
            }.filter { m -> !Modifier.isStatic(m.modifiers) }
        } catch (t: Throwable) {
            statusMap["perkey_hit_test"] = HookPointState.ERROR
            statusMap["perkey_bounds"] = HookPointState.MISSING
            scanErrors.add("Per-key hit-test scan exception: ${t.message}")
            return PerKeyResult(modelGetter = modelGetter, entityClass = ClassDescriptor(entityClassName), statusMap = statusMap)
        }

        val hitTestByUsage = hitTestCandidates.filter { m ->
            val used = runCatching { m.usingFields }.getOrNull().orEmpty()
            val readsModelList = used.any {
                it.field.className == modelClassName &&
                    it.field.typeName == "java.util.List" &&
                    it.usingType == FieldUsingType.Read
            }
            val readEntityInts = used.count {
                it.field.className == entityClassName &&
                    isIntName(it.field.typeName) &&
                    it.usingType == FieldUsingType.Read
            }
            readsModelList && readEntityInts >= 2
        }

        if (hitTestByUsage.size != 1) {
            statusMap["perkey_hit_test"] =
                if (hitTestByUsage.isEmpty()) HookPointState.MISSING else HookPointState.ERROR
            statusMap["perkey_bounds"] = HookPointState.MISSING
            scanErrors.add("Per-key hit-test: candidates=${hitTestByUsage.size} raw=${hitTestCandidates.size}")
            Log.w(TAG, "Per-key P3 fail: raw=${hitTestCandidates.size} byUsage=${hitTestByUsage.size} model=$modelClassName entity=$entityClassName")
            return PerKeyResult(modelGetter = modelGetter, entityClass = ClassDescriptor(entityClassName), statusMap = statusMap)
        }

        val hitTest = hitTestByUsage[0]
        statusMap["perkey_hit_test"] = HookPointState.FOUND
        val hitTestMethod = MethodDescriptor(hitTest.className, hitTest.name, hitTest.returnTypeName, hitTest.paramTypeNames)
        Log.i(TAG, "Found unique perkeyHitTest: ${hitTest.className}.${hitTest.name}")

        // ── P4. bounds: converge exactly 4 rectangle fields from the entity ints read by hit-test ──
        // The hit-test may read more than 4 ints (padding, state, margins), so the read set is
        // not required to equal 4. Name hints and layout writers narrow it to 4, else fail closed.
        val readIntDescriptors = runCatching {
            hitTest.usingFields
                .filter { it.field.className == entityClassName && isIntName(it.field.typeName) }
                .map { it.field.descriptor }
                .toSet()
        }.getOrElse {
            scanErrors.add("Per-key bounds: hit-test usingFields unreadable: ${it.message}")
            emptySet()
        }

        val hitReadIntFields = entityClassData.fields
            .filter { isIntName(it.typeName) && it.descriptor in readIntDescriptors }

        if (hitReadIntFields.size < 4) {
            statusMap["perkey_bounds"] = HookPointState.MISSING
            scanErrors.add("Per-key bounds: hit-read int fields=${hitReadIntFields.size} (<4)")
            Log.w(TAG, "Per-key P4 fail: hitReadIntFields=${hitReadIntFields.size} descriptors=$readIntDescriptors")
            return PerKeyResult(modelGetter = modelGetter, entityClass = ClassDescriptor(entityClassName), hitTestMethod = hitTestMethod, statusMap = statusMap)
        }

        // Converge 4 rectangle bounds fields from hit-test read int candidates.
        val boundsQuad = selectBoundsFields(hitReadIntFields, entityClassData)
        val leftCandidate = boundsQuad.left
        val rightCandidate = boundsQuad.right
        val topCandidate = boundsQuad.top
        val bottomCandidate = boundsQuad.bottom
        if (leftCandidate == null) {
            statusMap["perkey_bounds"] = HookPointState.ERROR
            scanErrors.add(
                "Per-key bounds: cannot converge 4 of ${hitReadIntFields.size} hit-read ints, " +
                    "fields=${hitReadIntFields.joinToString { it.name }}"
            )
            Log.w(TAG, "Per-key P4 converge fail: fields=${hitReadIntFields.joinToString { it.name }}")
            return PerKeyResult(modelGetter = modelGetter, entityClass = ClassDescriptor(entityClassName), hitTestMethod = hitTestMethod, statusMap = statusMap)
        }

        statusMap["perkey_bounds"] = HookPointState.FOUND
        Log.i(TAG, "Found perkey bounds: L=${leftCandidate.name} R=${rightCandidate!!.name} T=${topCandidate!!.name} B=${bottomCandidate!!.name}")

        // The body has more than one (Canvas, entity) method. The complete key renderer is the
        // unique candidate that invokes the already-resolved static key-background method.
        val keyDrawShapeCandidates = runCatching {
            bridge.findMethod {
                matcher {
                    declaredClass(keyboardViewClass)
                    paramCount(2)
                    paramTypes("android.graphics.Canvas", entityClassName)
                    returnType("void")
                }
            }.filter { !Modifier.isStatic(it.modifiers) }
        }.getOrElse {
            scanErrors.add("Per-key draw scan exception: ${it.message}")
            emptyList()
        }
        val keyDrawCandidates = if (keyBackgroundMethod != null) {
            keyDrawShapeCandidates.filter { candidate ->
                runCatching {
                    candidate.invokes.any { invoked ->
                        invoked.className == keyBackgroundMethod.className &&
                            invoked.name == keyBackgroundMethod.methodName &&
                            invoked.returnTypeName == keyBackgroundMethod.returnType &&
                            invoked.paramTypeNames == keyBackgroundMethod.parameterTypes
                    }
                }.getOrElse { t ->
                    scanErrors.add(
                        "Per-key draw invokes unreadable for ${candidate.className}.${candidate.name}: ${t.message}"
                    )
                    false
                }
            }
        } else {
            emptyList()
        }
        if (keyDrawCandidates.size == 1) {
            val draw = keyDrawCandidates[0]
            perkeyDrawMethod = MethodDescriptor(draw.className, draw.name, draw.returnTypeName, draw.paramTypeNames)
            statusMap["perkey_draw"] = HookPointState.FOUND
            Log.i(TAG, "Found perkey draw method by key-background invoke: ${draw.className}.${draw.name}")
        } else {
            statusMap["perkey_draw"] = if (keyDrawCandidates.isEmpty()) HookPointState.MISSING else HookPointState.ERROR
            scanErrors.add(
                "Per-key draw: background-filtered=${keyDrawCandidates.size} " +
                    "shape=${keyDrawShapeCandidates.size} keyBackground=${keyBackgroundMethod != null}"
            )
        }

        // ── P5. pressed (optional): entity boolean field read or written by touch and redraw paths ──
        val pressedField = runCatching {
            entityClassData.fields
                .filter { isBooleanName(it.typeName) }
                .singleOrNull { it.readers.any { r -> r.className == keyboardViewClass } || it.writers.isNotEmpty() }
        }.getOrNull()
        statusMap["perkey_pressed"] = when {
            pressedField != null -> HookPointState.FOUND
            entityClassData.fields.count { isBooleanName(it.typeName) } == 0 -> HookPointState.MISSING
            else -> HookPointState.DISABLED
        }

        // P6. Single-key redraw: void redrawKey(entity)
        // Uses per-key invalidation instead of rebuilding all keys in the offscreen buffer.
        val singleKeyCandidates = runCatching {
            bridge.findMethod {
                matcher {
                    declaredClass(keyboardViewClass)
                    paramCount(1)
                    paramTypes(entityClassName)
                    returnType("void")
                }
            }.filter { m ->
                !Modifier.isStatic(m.modifiers) && !m.isConstructor &&
                    m.invokes.any { invoked ->
                        invoked.name == "invalidate" && invoked.paramTypeNames.isEmpty()
                    } &&
                    (perkeyDrawMethod == null || m.invokes.any { invoked ->
                        invoked.name == perkeyDrawMethod.methodName &&
                            invoked.className == perkeyDrawMethod.className
                    })
            }
        }.getOrElse { t ->
            scanErrors.add("Single-key redraw scan exception: ${t.message}")
            emptyList()
        }

        val keyboardClassData = loadClassData(bridge, keyboardViewClass)
        val redrawShapeCandidates = runCatching {
            bridge.findMethod {
                matcher {
                    declaredClass(keyboardViewClass)
                    paramCount(0)
                    returnType("void")
                }
            }.filter { m -> !Modifier.isStatic(m.modifiers) && !m.isConstructor }
        }.getOrElse { t ->
            scanErrors.add("Per-key redraw scan exception: ${t.message}")
            emptyList()
        }
        val redrawCandidates = redrawShapeCandidates.filter { candidate ->
            runCatching {
                val invokesInvalidate = candidate.invokes.any { invoked ->
                    invoked.className == "android.view.View" &&
                        invoked.name == "invalidate" && invoked.paramTypeNames.isEmpty() &&
                        (invoked.returnTypeName == "void" || invoked.returnTypeName == "V")
                }
                val invokesRectUnion = candidate.invokes.any { invoked ->
                    invoked.className == "android.graphics.Rect" && invoked.name == "union" &&
                        invoked.paramTypeNames.size == 4 &&
                        invoked.paramTypeNames.all(::isIntName)
                }
                val writesOwnBoolean = keyboardClassData?.fields
                    ?.filter { isBooleanName(it.typeName) }
                    ?.any { field -> field.writers.any { it.descriptor == candidate.descriptor } } == true
                invokesInvalidate && invokesRectUnion && writesOwnBoolean
            }.getOrElse { t ->
                scanErrors.add(
                    "Per-key redraw structure unreadable for ${candidate.className}.${candidate.name}: ${t.message}"
                )
                false
            }
        }

        val redrawMethod = singleKeyCandidates.singleOrNull()?.let {
            MethodDescriptor(it.className, it.name, it.returnTypeName, it.paramTypeNames)
        } ?: redrawCandidates.singleOrNull()?.let {
            MethodDescriptor(it.className, it.name, it.returnTypeName, it.paramTypeNames)
        }

        statusMap["perkey_redraw"] = when {
            redrawMethod != null -> HookPointState.FOUND
            singleKeyCandidates.isEmpty() && redrawCandidates.isEmpty() -> HookPointState.MISSING
            else -> HookPointState.ERROR
        }
        if (redrawMethod == null) {
            scanErrors.add(
                "Per-key redraw: singleMatched=${singleKeyCandidates.size} " +
                    "fallbackMatched=${redrawCandidates.size}"
            )
        }

        return PerKeyResult(
            modelGetter = modelGetter,
            entityClass = ClassDescriptor(entityClassName),
            hitTestMethod = hitTestMethod,
            leftField = FieldDescriptor(leftCandidate.className, leftCandidate.name, leftCandidate.typeName),
            rightField = FieldDescriptor(rightCandidate.className, rightCandidate.name, rightCandidate.typeName),
            topField = FieldDescriptor(topCandidate.className, topCandidate.name, topCandidate.typeName),
            bottomField = FieldDescriptor(bottomCandidate.className, bottomCandidate.name, bottomCandidate.typeName),
            drawMethod = perkeyDrawMethod,
            pressedField = pressedField?.let { FieldDescriptor(it.className, it.name, it.typeName) },
            redrawMethod = redrawMethod,
            statusMap = statusMap
        )
    }

    private fun loadClassData(bridge: DexKitBridge, className: String): ClassData? =
        runCatching {
            bridge.findClass { matcher { this.className = className } }.singleOrNull()
        }.getOrNull()

    private fun hasDeclaredListField(clazz: ClassData?): Boolean =
        clazz != null && clazz.fields.count { it.typeName == "java.util.List" } >= 1

    private fun entityShape(clazz: ClassData): Boolean =
        clazz.fields.count { isIntName(it.typeName) } >= 4

    private fun isIntName(typeName: String): Boolean = typeName == "int" || typeName == "I"

    private fun isBooleanName(typeName: String): Boolean = typeName == "boolean" || typeName == "Z"

    private fun isPrimitiveOrVoidName(typeName: String): Boolean =
        typeName == "void" || typeName == "V" ||
            typeName == "int" || typeName == "I" ||
            typeName == "boolean" || typeName == "Z" ||
            typeName == "long" || typeName == "J" ||
            typeName == "float" || typeName == "F" ||
            typeName == "double" || typeName == "D" ||
            typeName == "byte" || typeName == "B" ||
            typeName == "short" || typeName == "S" ||
            typeName == "char" || typeName == "C"

    /**
     * Converges left/right/top/bottom from the entity int fields read by hit-test.
     *
     * Host entity field names are obfuscated (for example f11233b/f11234c); names are not used.
     *
     * 1. Writer grouping (primary): the 4 rectangle fields are written together by one layout
     *    method (`iput-*`). The writer covering >=4 candidates with the smallest count wins.
     * 2. Size getters: an int getter reads exactly two of the candidates (right-left, bottom-top).
     *
     * Name hints are a fast path only. All sources failing returns all null.
     */
    private data class BoundsQuad(
        val left: FieldData?,
        val right: FieldData?,
        val top: FieldData?,
        val bottom: FieldData?
    )

    private fun selectBoundsFields(
        fields: List<FieldData>,
        entityClassData: ClassData? = null
    ): BoundsQuad {
        // Fast path 1: Names hint if available (unobfuscated sample)
        pickByNames(fields)?.let { return it }

        // Fast path 2: Check methods declared in entity class.
        // In Oplus Keyboard entity (e.g. com.oplus.keyboard.base.entity.f), method e(IIII...) writes/uses
        // exactly the 4 bounds fields (b, c, d, e), while c() uses 2, f() uses 2, and others use >=5 or <=1.
        if (entityClassData != null) {
            val fieldDescSet = fields.map { it.descriptor }.toSet()
            val methodMatches = entityClassData.methods.mapNotNull { m ->
                val used = runCatching {
                    m.usingFields
                        .filter { it.field.descriptor in fieldDescSet }
                        .map { it.field }
                        .distinctBy { it.descriptor }
                }.getOrNull()
                if (used != null && used.size == 4) used else null
            }
            if (methodMatches.isNotEmpty()) {
                val best = methodMatches.first()
                Log.i(TAG, "Converged per-key bounds via entity method (${best.size} fields): ${best.joinToString { it.name }}")
                return orientBounds(best)
            }

            // Fallback for paired entity getters (e.g. c() for height=top/bottom, f() for width=left/right)
            val pairFields = entityClassData.methods.mapNotNull { m ->
                val used = runCatching {
                    m.usingFields
                        .filter { it.field.descriptor in fieldDescSet }
                        .map { it.field }
                        .distinctBy { it.descriptor }
                }.getOrNull()
                if (used != null && used.size == 2) used else null
            }.flatten().distinctBy { it.descriptor }
            if (pairFields.size == 4) {
                Log.i(TAG, "Converged per-key bounds via paired entity getters: ${pairFields.joinToString { it.name }}")
                return orientBounds(pairFields)
            }
        }

        // Primary path: By field writers (layout methods)
        val byWriter: List<FieldData>? = runCatching {
            val writerGroups = mutableMapOf<String, MutableList<FieldData>>()
            for (f in fields) {
                for (w in f.writers) {
                    writerGroups.getOrPut(w.descriptor) { mutableListOf() }.add(f)
                }
            }
            // Take the writer that touches >= 4 candidates with the smallest candidate count
            writerGroups.values
                .filter { it.size >= 4 }
                .minByOrNull { it.size }
                ?.distinctBy { it.descriptor }
        }.getOrNull()

        if (byWriter != null && byWriter.size == 4) {
            return orientBounds(byWriter)
        }

        val withWriters = fields.filter { it.writers.isNotEmpty() }
        if (withWriters.size == 4) {
            return orientBounds(withWriters)
        }

        return BoundsQuad(null, null, null, null)
    }

    private fun pickByNames(fields: List<FieldData>): BoundsQuad? {
        fun pick(vararg hints: String): FieldData? =
            fields.filter { f -> hints.any { f.name.contains(it, ignoreCase = true) } }.singleOrNull()
        val left = pick("left", "start", "x1", "xStart", "minX")
        val right = pick("right", "end", "x2", "xEnd", "maxX")
        val top = pick("top", "y1", "yStart", "minY")
        val bottom = pick("bottom", "y2", "yEnd", "maxY")
        return orientBounds(listOfNotNull(left, right, top, bottom))
            ?.takeIf { left != null && right != null && top != null && bottom != null }
    }

    /**
     * Assigns directions to the 4 fields of one rectangle by name when available.
     * Runtime validation confirms the final orientation (see OplusPerKeyBoundsProvider).
     */
    private fun orientBounds(four: List<FieldData>): BoundsQuad {
        if (four.size != 4) return BoundsQuad(null, null, null, null)
        val distinct = four.map { it.descriptor }.toSet().size == 4
        if (!distinct) return BoundsQuad(null, null, null, null)

        fun pick(vararg hints: String): FieldData? =
            four.filter { f -> hints.any { f.name.contains(it, ignoreCase = true) } }.singleOrNull()
        val left = pick("left", "start", "x1", "xStart", "minX")
        val right = pick("right", "end", "x2", "xEnd", "maxX")
        val top = pick("top", "y1", "yStart", "minY")
        val bottom = pick("bottom", "y2", "yEnd", "maxY")

        // All four names present and distinct: assign by name.
        if (left != null && right != null && top != null && bottom != null) {
            val used = setOf(left.descriptor, right.descriptor, top.descriptor, bottom.descriptor)
            if (used.size == 4) return BoundsQuad(left, right, top, bottom)
        }

        // Obfuscated names: assign by descriptor order; runtime validation selects the correct order.
        val sorted = four.sortedBy { it.descriptor }
        return BoundsQuad(sorted[0], sorted[1], sorted[2], sorted[3])
    }

    private fun logHookPointStates(symbols: OplusInputSymbols) {
        Log.i(TAG, "HookPoint[oplus_input_whitelist] state=${symbols.statusMap["whitelist"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_blur_apply] state=${symbols.statusMap["blur_apply"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_fold_check] state=${symbols.statusMap["fold_check"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_effects_touch] state=${symbols.statusMap["effects_touch"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_effects_draw] state=${symbols.statusMap["effects_draw"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_day_night_check] state=${symbols.statusMap["day_night_check"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_blur_param_create] state=${symbols.statusMap["blur_param_create"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_key_background] state=${symbols.statusMap["key_background"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_key_alpha] state=${symbols.statusMap["key_alpha"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_toolbar_layout] state=${symbols.statusMap["toolbar_layout"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_toolbar_item_icon] state=${symbols.statusMap["toolbar_item_icon"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_tool_catalog_create] state=${symbols.statusMap["tool_catalog_create"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_tool_state_transform] state=${symbols.statusMap["tool_state_transform"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_toolbox_data] state=${symbols.statusMap["toolbox_data"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_theme_icon] state=${symbols.statusMap["theme_icon"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_blur_radius_runtime] state=${symbols.statusMap["blur_radius_runtime"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_perkey_model_getter] state=${symbols.statusMap["perkey_model_getter"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_perkey_entity] state=${symbols.statusMap["perkey_entity"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_perkey_hit_test] state=${symbols.statusMap["perkey_hit_test"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_perkey_bounds] state=${symbols.statusMap["perkey_bounds"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_perkey_draw] state=${symbols.statusMap["perkey_draw"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_perkey_shadow_underlay] state=${symbols.statusMap["perkey_shadow_underlay"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_perkey_shadow_overlay] state=${symbols.statusMap["perkey_shadow_overlay"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_perkey_pressed] state=${symbols.statusMap["perkey_pressed"] ?: HookPointState.MISSING}")
        Log.i(TAG, "HookPoint[oplus_input_perkey_redraw] state=${symbols.statusMap["perkey_redraw"] ?: HookPointState.MISSING}")
        if (symbols.scanErrors.isNotEmpty()) {
            Log.w(TAG, "Scan errors encountered: ${symbols.scanErrors.joinToString("; ")}")
        }
    }

    private fun getPackageInfo(app: Application): PackageInfo? = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0)
    }.getOrNull()

    private fun getVersionCode(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }
}
