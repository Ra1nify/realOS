import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

// =================================================================
// realOS Build-time Scope Controller
//
// Only realos.scope.<key> single-line properties are read. Priority (high to low):
//   1. command line  -Prealos.scope.camera=1
//   2. environment   ORG_GRADLE_PROJECT_realos.scope.camera=1  (Gradle maps it to a project property)
//   3. local file    realos.scope.camera=1 in local.properties
//   4. built-in default: all off (a scope not written out is never packaged)
//
// All three sources share one validation path: the key must exist in SCOPE_KEYS or it is dropped with a warning;
// only 1/0/true/false are accepted, anything else is warned and ignored; an unset key counts as off and is
// listed at build time, so nothing disappears silently.
// The scope {} block syntax is unsupported and luckyfuse.scope.* is ignored.
// =================================================================
val SCOPE_PREFIX = "realos.scope."

/** Local property file shared by the scope and version controllers. */
val localPropsFile = rootProject.file("local.properties")

/**
 * SpecLoader implementation class kept for each scope (module-owned class, safe to hardcode).
 *
 * Also the single source of scope keys ([SCOPE_KEYS]) and the basis for boundary checks.
 */
val SCOPE_KEEP_CLASSES: Map<String, List<String>> = mapOf(
    "opluskeyboard" to listOf("io.github.ra1nify.realos.xposed.hooks.opluskeyboard.OplusInputSpec"),
    "navigation" to listOf("io.github.ra1nify.realos.xposed.hooks.navigation.SystemUISpec"),
    "font" to listOf("io.github.ra1nify.realos.xposed.hooks.font.SystemFontSpec"),
    "featureswitch" to listOf("io.github.ra1nify.realos.xposed.hooks.featureswitch.FeatureSwitchSpec"),
    "camera" to listOf("io.github.ra1nify.realos.xposed.hooks.camera.OplusCameraSpec"),
    "launcher" to listOf("io.github.ra1nify.realos.xposed.hooks.launcher.LauncherSpec"),
    "wallet" to listOf("io.github.ra1nify.realos.xposed.hooks.wallet.WalletSpec"),
    "mediaprovider" to listOf("io.github.ra1nify.realos.xposed.hooks.mediaprovider.MediaProviderSpec"),
    "fido" to listOf("io.github.ra1nify.realos.xposed.hooks.fido.FidoScanRedirectSpec"),
    "intentroute" to listOf("io.github.ra1nify.realos.xposed.hooks.intentroute.IntentRouteAlignmentSpec"),
    "clockfont" to listOf("io.github.ra1nify.realos.xposed.hooks.clockfont.ClockFontSpec"),
)

/** Supported scope keys, taken from [SCOPE_KEEP_CLASSES] so the two lists cannot drift. */
val SCOPE_KEYS: List<String> = SCOPE_KEEP_CLASSES.keys.sorted()

// ===============================================================================
// realOS Build Mode Controller
//
//   realos.mode = full | lite        (default full, backward compatible)
//
//   full : only realos.scope.<key> is read; realos.lite.target is ignored
//   lite : only realos.lite.target=<single key> is read; every realos.scope.* is ignored
//          one lite package = one host category. LSPatch decides the injection scope,
//          so package gating is dropped and the runtime uses the three-attempt lock logic.
//
//   Lookup priority matches scope: command line > environment > local.properties.
//   An invalid mode, or a missing/invalid lite target, fails the build with no output.
// ===============================================================================
val MODE_PROPERTY = "realos.mode"
val LITE_TARGET_PROPERTY = "realos.lite.target"
val BUILD_MODE_FULL = "full"
val BUILD_MODE_LITE = "lite"

/**
 * Staged gate for clockfont. Defaults to 0 (off) and must agree with realos.scope.clockfont:
 *   0 = off        inject no clock host
 *   1 = probe      observe createPackageContext / class resolution only, no behavior change
 *   2 = wallpapers enable the font list and preview in Wallpapers only
 *   3 = full       enable Wallpapers / SystemUI / Launcher / AOD
 */
val CLOCKFONT_STAGE_PROPERTY = "realos.clockfont.stage"

/** Same lookup chain as scope: -P / ORG_GRADLE_PROJECT_ env > local.properties. */
fun readRealosProperty(name: String): String? {
    (project.findProperty(name) as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    if (localPropsFile.exists()) {
        val props = Properties()
        runCatching { localPropsFile.inputStream().use { props.load(it) } }
        return props.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
    }
    return null
}

fun parseClockFontStage(raw: String?): Int {
    if (raw.isNullOrBlank()) return 0
    val value = raw.trim().toIntOrNull()
    if (value == null || value !in 0..3) {
        throw GradleException(
            "[realOS ClockFont] invalid $CLOCKFONT_STAGE_PROPERTY='$raw': " +
                "expected 0=off / 1=probe / 2=wallpapers / 3=full. Build aborted, nothing produced."
        )
    }
    return value
}

val clockFontStage: Int = parseClockFontStage(readRealosProperty(CLOCKFONT_STAGE_PROPERTY))

val buildModeRaw = readRealosProperty(MODE_PROPERTY) ?: BUILD_MODE_FULL
val buildMode = buildModeRaw.lowercase()
if (buildMode != BUILD_MODE_FULL && buildMode != BUILD_MODE_LITE) {
    throw GradleException(
        "[realOS Mode] invalid $MODE_PROPERTY='$buildModeRaw': expected '$BUILD_MODE_FULL' or '$BUILD_MODE_LITE'." +
            " Build aborted, nothing produced."
    )
}
val isLiteBuild = buildMode == BUILD_MODE_LITE

val liteTarget: String? = if (isLiteBuild) {
    val raw = readRealosProperty(LITE_TARGET_PROPERTY)
        ?: throw GradleException(
            "[realOS Mode] mode=lite requires a single-value $LITE_TARGET_PROPERTY=<key>." +
                " supported values: ${SCOPE_KEYS.joinToString()}"
        )
    val key = raw.trim().lowercase()
    if (key !in SCOPE_KEYS) {
        throw GradleException(
            "[realOS Mode] invalid $LITE_TARGET_PROPERTY='$raw': must be one of ${SCOPE_KEYS.joinToString()}," +
                " and exactly one value. Build aborted, nothing produced."
        )
    }
    key
} else {
    null
}


/** Application id of the variant being configured (full vs lite). */
val resolvedApplicationId: String =
    if (isLiteBuild) "io.github.ra1nify.realos.lite.$liteTarget" else "io.github.ra1nify.realos"

/**
 * Components that survived the lite whitelist manifest, as fully qualified class names.
 * Filled by the lite manifest generator and consumed by the mode-aware keep rules, so a lite
 * package keeps exactly the components it declares and nothing else.
 */
val liteKeptComponents = mutableListOf<String>()


/**
 * Strict flag parsing: only 1/0/true/false, case-insensitive.
 * Any other value returns null so the caller can warn; otherwise a typo like `camera=yes` would silently mean off.
 */
fun parseScopeFlag(raw: String?): Boolean? {
    return when (raw?.trim()?.lowercase()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }
}

/**
 * Single validation entry: unknown keys are dropped with a warning, invalid values are warned too.
 * Shared by the file source and the command line / environment sources so both behave identically.
 */
fun collectScopeFlags(source: String, entries: Map<String, String?>): Map<String, Boolean> = buildMap {
    entries.forEach { (rawKey, rawValue) ->
        val key = rawKey.trim().lowercase()
        if (rawValue.isNullOrBlank()) return@forEach
        if (key !in SCOPE_KEYS) {
            println(
                "[realOS Scope] WARN unknown scope key '$key' (from $source), ignored;" +
                    "supported keys: ${SCOPE_KEYS.joinToString()}"
            )
            return@forEach
        }
        val flag = parseScopeFlag(rawValue)
        if (flag == null) {
            println(
                "[realOS Scope] WARN scope '$key' value '$rawValue' is not recognized (from $source), ignored;" +
                    "use 1/true or 0/false"
            )
            return@forEach
        }
        put(key, flag)
    }
}

/** realos.scope.* from local.properties */
val fileScopeEntries: Map<String, String?> = buildMap {
    if (localPropsFile.exists()) {
        val props = Properties()
        runCatching { localPropsFile.inputStream().use { props.load(it) } }
            .onFailure { println("[realOS Scope] WARN failed to read local.properties: ${it.message}") }
        props.stringPropertyNames()
            .filter { it.startsWith(SCOPE_PREFIX) }
            .forEach { put(it.removePrefix(SCOPE_PREFIX), props.getProperty(it)) }
    }
}

/** Command line -P and ORG_GRADLE_PROJECT_ env (Gradle maps both to project properties). */
val gradleScopeEntries: Map<String, String?> =
    providers.gradlePropertiesPrefixedBy(SCOPE_PREFIX).orNull
        ?.mapKeys { (name, _) -> name.removePrefix(SCOPE_PREFIX) }
        ?: emptyMap()

/** File values first, command line / environment override. */
val scopeMap: Map<String, Boolean> = buildMap {
    putAll(collectScopeFlags("local.properties", fileScopeEntries))
    putAll(collectScopeFlags("command line / environment", gradleScopeEntries))
}


/**
 * Effective scope table.
 *
 * - full: keep the realos.scope.* result (unchanged behavior);
 * - lite: enable only lite.target, force the rest off, ignore every realos.scope.*.
 *
 * Downstream consumers (SpecRegistry / keep rules / scope.list) all read this table, so both modes share one trimming path.
 */
val effectiveScopeMap: Map<String, Boolean> = if (isLiteBuild) {
    if (scopeMap.isNotEmpty()) {
        println(
            "[realOS Mode] mode=lite ignores realos.scope.* (read ${scopeMap.keys.sorted().joinToString()})," +
                " keeping target='$liteTarget' only"
        )
    }
    SCOPE_KEYS.associateWith { it == liteTarget }
} else {
    if (readRealosProperty(LITE_TARGET_PROPERTY) != null) {
        println("[realOS Mode] mode=$BUILD_MODE_FULL ignores $LITE_TARGET_PROPERTY (only realos.scope.* is read)")
    }
    scopeMap
}

println("[realOS Mode] mode=$buildMode" + (liteTarget?.let { ", target=$it" } ?: ""))

val hasExplicitScopeConfig = effectiveScopeMap.isNotEmpty()
if (!isLiteBuild) {
    if (!hasExplicitScopeConfig) {
        println("[realOS Scope] WARN no realos.scope.* found, no scope will be packaged.")
        println(
            "[realOS Scope] WARN how to set: -Prealos.scope.<key>=1 / " +
                "ORG_GRADLE_PROJECT_realos.scope.<key>=1 / local.properties"
        )
    } else {
        val unspecified = SCOPE_KEYS.filterNot { effectiveScopeMap.containsKey(it) }
        if (unspecified.isNotEmpty()) {
            println(
                "[realOS Scope] WARN these scopes are not set and count as off: ${unspecified.joinToString()};" +
                    "write 1 to keep them"
            )
        }
    }
}

/** A scope not written out is off; it must be set to 1 to be packaged. */
fun isScopeIncluded(scopeKey: String): Boolean = effectiveScopeMap[scopeKey] == true

/**
 * Scope boundary check (replaces source exclusion).
 *
 * `sourceSets.exclude` is no longer used: it can break compilation for any scope combination, and excluded
 * code never gets type-checked. Instead we always compile everything and scan the import graph at build time:
 * if an enabled scope references a disabled one, fail with a readable dependency note.
 * Actual trimming is left to R8 (keep rules are generated only for enabled scopes).
 */
run {
    val hooksDir = file("src/main/java/io/github/ra1nify/realos/xposed/hooks")
    val importRegex = Regex("""^import io\.github\.ra1nify\.realos\.xposed\.hooks\.(\w+)[.\s]""")

    val deps = SCOPE_KEEP_CLASSES.keys.associateWith { scope ->
        val dir = hooksDir.resolve(scope)
        if (!dir.isDirectory) {
            emptySet()
        } else {
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { it.readLines().asSequence() }
                .mapNotNull { importRegex.find(it.trim())?.groupValues?.get(1) }
                .filter { it != scope }
                .filter { target -> target in SCOPE_KEEP_CLASSES.keys }
                .toSet()
        }
    }

    deps.forEach { (scope, targets) ->
        if (!isScopeIncluded(scope)) return@forEach
        val missing = targets.filterNot { isScopeIncluded(it) }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "[realOS Scope] scope '$scope' depends on ${missing.joinToString()}: " +
                    "enable it too (-Prealos.scope.${missing.first()}=1), or disable '$scope' as well."
            )
        }
    }

    val crossScopeDeps = deps.filterValues { it.isNotEmpty() }
        .map { (scope, targets) -> "$scope -> ${targets.joinToString("/")}" }
        .sorted()
    println("[realOS Scope] cross-scope deps: ${crossScopeDeps.ifEmpty { "none" }}")
}

val scopeOplusKeyboard = isScopeIncluded("opluskeyboard")
val scopeNavigation = isScopeIncluded("navigation")
val scopeFont = isScopeIncluded("font")
val scopeFeatureSwitch = isScopeIncluded("featureswitch")
val scopeCamera = isScopeIncluded("camera")
val scopeLauncher = isScopeIncluded("launcher")
val scopeWallet = isScopeIncluded("wallet")
val scopeMediaProvider = isScopeIncluded("mediaprovider")
val scopeFido = isScopeIncluded("fido")
val scopeIntentRoute = isScopeIncluded("intentroute")
val scopeClockFont = isScopeIncluded("clockfont")

// clockfont is a high-risk hook: scope and stage must both be set explicitly, otherwise the build fails.
if (scopeClockFont && clockFontStage == 0) {
    throw GradleException(
        "[realOS ClockFont] realos.scope.clockfont=1 but $CLOCKFONT_STAGE_PROPERTY=0." +
            " Set 1=probe / 2=wallpapers / 3=full, or disable the clockfont scope."
    )
}
if (!scopeClockFont && clockFontStage > 0) {
    throw GradleException(
        "[realOS ClockFont] $CLOCKFONT_STAGE_PROPERTY=$clockFontStage but realos.scope.clockfont=0." +
            " Set realos.scope.clockfont=1 as well, or set stage back to 0."
    )
}
// Phase 1 only ships the observe-only probe; stage 2/3 business hooks are not written yet, so reject them.
if (clockFontStage > 1) {
    throw GradleException(
        "[realOS ClockFont] stage=$clockFontStage is not implemented: only 0=off or 1=probe is allowed." +
            " Business hooks will land in a later phase."
    )
}
println("[realOS ClockFont] stage=$clockFontStage (0=off, 1=probe, 2=wallpapers, 3=full)")

println(
    "[realOS Scope] opluskeyboard=$scopeOplusKeyboard, navigation=$scopeNavigation, font=$scopeFont, featureswitch=$scopeFeatureSwitch, " +
        "camera=$scopeCamera, launcher=$scopeLauncher, wallet=$scopeWallet, mediaprovider=$scopeMediaProvider, fido=$scopeFido, intentroute=$scopeIntentRoute, " +
        "clockfont=$scopeClockFont"
)

// =================================================================
// realOS Scope Keep Rules Generator
//
// Generate per-spec keep rules from the scope config:
//   - enabled scope: -keep class <SpecClass> { *; }
//   - disabled scope: its spec class is unreachable in SpecRegistry, so R8 shrinks it
// Output: app/src/main/keepRules/realos-scope.keep (gitignored, auto-loaded from the AGP keepRules dir)
// =================================================================

fun generateScopeKeepRules(): String {
    val enabledScopes = SCOPE_KEEP_CLASSES.keys.filter { isScopeIncluded(it) }.sorted()
    return buildString {
        appendLine("# realOS scope keep rules - GENERATED at configuration time.")
        appendLine("# Source: realos.scope.* (local.properties / -P / ORG_GRADLE_PROJECT_) + SCOPE_KEEP_CLASSES in app/build.gradle.kts.")
        appendLine("# DO NOT EDIT - regenerated on every build after a scope config change.")
        appendLine("#")
        appendLine("# Only enabled scopes get a -keep for their SpecLoader implementation.")
        appendLine("# A disabled scope's spec class is unreachable in SpecRegistry and shrunk by R8.")
        appendLine()
        if (enabledScopes.isEmpty()) {
            appendLine("# (no scope enabled - nothing to keep)")
        } else {
            enabledScopes.forEach { scope ->
                appendLine("# [$scope]")
                SCOPE_KEEP_CLASSES.getValue(scope).forEach { cls ->
                    appendLine("-keep class $cls { *; }")
                }
                appendLine()
            }
        }
        appendLine("# 3. Android components")
        if (isLiteBuild) {
            appendLine("# Lite: keep only the components that survived the whitelist manifest.")
            if (liteKeptComponents.isEmpty()) {
                appendLine("# (the whitelist manifest declares no component)")
            } else {
                liteKeptComponents.sorted().forEach { fqcn ->
                    appendLine("-keep class $fqcn { public <init>(...); }")
                }
            }
        } else {
            appendLine("# Full: every Activity / Service is instantiated by the framework by name.")
            appendLine("-keep public class * extends android.app.Activity { public <init>(...); }")
            appendLine("-keep public class * extends android.app.Service { public <init>(...); }")
            appendLine("# Full: keep every class that declares a composable so the whole UI tree survives.")
            appendLine("-keepclasseswithmembers class ** {")
            appendLine("    @androidx.compose.runtime.Composable <methods>;")
            appendLine("}")
        }
        appendLine()
    }
}

val scopeKeepRulesFile = file("src/main/keepRules/realos-scope.keep")

// Written after the lite whitelist manifest is generated: lite keep rules depend on it.

// =================================================================
// realOS Scope List Generator
//
// src/main/resources/META-INF/xposed/scope.list is the full display definition (tracked by git, never overwritten);
// at packaging time the intermediate META-INF/xposed/scope.list is rewritten from the enabled scopes.
// =================================================================
// Full: ColorOS keyboard. Lite (target=opluskeyboard): the Realme sibling package;
// its internal class namespace stays com.oplus.keyboard.
val KEYBOARD_SCOPE_PACKAGE = if (isLiteBuild && liteTarget == "opluskeyboard") {
    "com.realme.keyboard"
} else {
    "com.oplus.keyboard"
}

val BASE_SCOPE_PACKAGES: Map<String, List<String>> = mapOf(
    "opluskeyboard" to listOf(KEYBOARD_SCOPE_PACKAGE),
    "navigation" to listOf("com.android.systemui", "com.android.settings"),
    "font" to listOf("com.android.settings"),
    "featureswitch" to listOf("com.android.settings", "com.oplus.wallpapers"),
    "camera" to listOf("com.oplus.camera"),
    "launcher" to listOf("com.android.launcher"),
    "wallet" to listOf("com.finshell.wallet"),
    "mediaprovider" to listOf("com.android.providers.media.module"),
    "fido" to listOf("system"),
    "intentroute" to listOf("system"),
    "clockfont" to listOf(
        "com.oplus.wallpapers",
        "com.android.systemui",
        "com.android.launcher",
        "com.oplus.aod"
    )
)

fun generateScopeList(): String {
    // A lite package targets LSPatch: the patch side decides the injection scope, so scope.list stays empty
    // to keep the package from injecting into any process if it is installed in LSPosed by mistake.
    if (isLiteBuild) return ""
    val lines = mutableListOf<String>()
    BASE_SCOPE_PACKAGES.forEach { (scopeKey, packages) ->
        if (isScopeIncluded(scopeKey)) {
            lines.addAll(packages)
        }
    }
    // Keep the low-level system scope when a system component is enabled or no filter list is set.
    if (isScopeIncluded("navigation") || isScopeIncluded("font") || isScopeIncluded("featureswitch") || !hasExplicitScopeConfig) {
        if (!lines.contains("system")) {
            lines.add("system")
        }
    }
    val distinct = lines.distinct()
    // With zero scopes return an empty string so scope.list is not written as a lone newline.
    return if (distinct.isEmpty()) "" else distinct.joinToString("\n") + "\n"
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JavaResource") }.configureEach {
    val dynamicScopeText = generateScopeList()
    // Make a scope change invalidate this task; otherwise an UP-TO-DATE task skips doLast and the
    // jar keeps the scope.list injected by the previous build.
    inputs.property("realosScopeList", dynamicScopeText)
    doLast {
        if (dynamicScopeText.isEmpty() && !isLiteBuild) {
            println("[realOS Scope] WARN no scope enabled: scope.list is empty, LSPosed will inject into nothing.")
        }
        val outputJars = outputs.files.asFileTree.files.filter { it.extension == "jar" }
        for (jarFile in outputJars) {
            runCatching {
                val uri = URI.create("jar:" + jarFile.toURI().toASCIIString())
                val env = mapOf("create" to "false")
                FileSystems.newFileSystem(uri, env).use { fs ->
                    val path = fs.getPath("META-INF/xposed/scope.list")
                    Files.write(path, dynamicScopeText.toByteArray(Charsets.UTF_8))
                }
                println("[realOS Scope] Injected dynamic scope.list into ${jarFile.name}")
            }
        }
    }
}

// =================================================================
// realOS Version Controller
//
// versionName = 17.<a>.<b>.<c>
//   a / b / c come from realos.ver.a / realos.ver.b / realos.ver.c, using the
//   same lookup chain as the scope flags (-P > env > local.properties).
//   Each part must be a single int digit 0..9; anything else falls back to 0.
//
// versionCode:
//   release = <yyMM><a><b><c>   (monotonic within a month)
//   debug   = epoch seconds
// =================================================================
val VERSION_A_PROPERTY = "realos.ver.a"
val VERSION_B_PROPERTY = "realos.ver.b"
val VERSION_C_PROPERTY = "realos.ver.c"

/** Parse one version part: a single digit 0..9, otherwise 0. */
fun parseVersionPart(name: String, raw: String?): Int {
    val trimmed = raw?.trim()
    val value = trimmed?.toIntOrNull()
    if (value == null || value !in 0..9) {
        if (!trimmed.isNullOrEmpty()) {
            println("[realOS Version] WARN $name='$trimmed' is not an int in 0..9, using 0")
        }
        return 0
    }
    return value
}

val verA = parseVersionPart(VERSION_A_PROPERTY, readRealosProperty(VERSION_A_PROPERTY))
val verB = parseVersionPart(VERSION_B_PROPERTY, readRealosProperty(VERSION_B_PROPERTY))
val verC = parseVersionPart(VERSION_C_PROPERTY, readRealosProperty(VERSION_C_PROPERTY))

val dateTag = SimpleDateFormat("yyMM").format(Date())

val releaseVersionCode = "${dateTag}${verA}${verB}${verC}".toInt()
val releaseVersionName = "17.${verA}.${verB}.${verC}"

val debugVersionCode = (System.currentTimeMillis() / 1000).toInt()
val debugVersionName = "${releaseVersionName}-debug"

println("[realOS Version] release=$releaseVersionName($releaseVersionCode) | debug=$debugVersionName($debugVersionCode)")

android {
    namespace = "io.github.ra1nify.realos"
    compileSdk = 37

    defaultConfig {
        // A lite package carries one host category; the applicationId suffix lets several lite packages coexist.
        applicationId = if (isLiteBuild) "io.github.ra1nify.realos.lite.$liteTarget" else "io.github.ra1nify.realos"
        minSdk = 36
        targetSdk = 37
        // Debug values (also the defaults). Release is overridden by androidComponents.onVariants below.
        versionCode = debugVersionCode
        versionName = debugVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }

        buildConfigField("boolean", "SCOPE_OPLUS_KEYBOARD", scopeOplusKeyboard.toString())
        buildConfigField("boolean", "SCOPE_NAVIGATION", scopeNavigation.toString())
        buildConfigField("boolean", "SCOPE_FONT", scopeFont.toString())
        buildConfigField("boolean", "SCOPE_FEATURESWITCH", scopeFeatureSwitch.toString())
        buildConfigField("boolean", "SCOPE_CAMERA", scopeCamera.toString())
        buildConfigField("boolean", "SCOPE_LAUNCHER", scopeLauncher.toString())
        buildConfigField("boolean", "SCOPE_WALLET", scopeWallet.toString())
        buildConfigField("boolean", "SCOPE_MEDIA_PROVIDER", scopeMediaProvider.toString())
        buildConfigField("boolean", "SCOPE_FIDO", scopeFido.toString())
        buildConfigField("boolean", "SCOPE_INTENTROUTE", scopeIntentRoute.toString())
        buildConfigField("boolean", "SCOPE_CLOCKFONT", scopeClockFont.toString())
        buildConfigField("int", "CLOCKFONT_STAGE", clockFontStage.toString())
        // Lite marker: the runtime uses it to enable the three-attempt lock logic (see LiteHookGate).
        buildConfigField("boolean", "MODE_LITE", isLiteBuild.toString())
        buildConfigField("String", "LITE_TARGET", "\"${liteTarget ?: ""}\"")

    }

    buildTypes {
        release {
            optimization {
                enable = true
                keepRules {
                    // Keep the default Android platform keep rules (proguard-android-optimize equivalent).
                    includeDefault = true
                }
            }
            ndk {
                debugSymbolLevel = "NONE"
            }
            isDebuggable = false
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable += "BlockedPrivateApi"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// =================================================================
// Apply the version per build type.
// AGP 9 removed versionCode / versionName from BuildType,
// so the Variant API is required.
// =================================================================

// ===============================================================================
// realOS Lite Manifest Generator
//
// A lite package is a module carrier for LSPatch and must not show up as a normal app.
// At configuration time this derives a whitelist manifest from src/main/AndroidManifest.xml:
// only components owned by the lite target scope survive, so the carrier never declares a
// component (or a provider authority) that another installed realOS package already owns.
// The source manifest stays the single source of truth, so the two cannot drift.
// A failed derivation fails the build instead of emitting a half-baked package.
// ===============================================================================

/**
 * Manifest components owned by each scope, expressed as prefixes of the component android:name.
 *
 * A scope with an empty list owns no manifest component (its UI lives inside the shared app shell).
 * The app shell itself (MainActivity, icon aliases) is never owned by a scope and is always dropped
 * from a lite package.
 */
val SCOPE_MANIFEST_PREFIXES: Map<String, List<String>> = mapOf(
    "opluskeyboard" to emptyList(),
    "navigation" to listOf(".navigation."),
    "featureswitch" to listOf(".featureswitch."),
    "font" to listOf(".font."),
    "camera" to listOf(".camera."),
    "launcher" to listOf(".launcher.", ".root.LauncherStatsProvider"),
    "wallet" to listOf(".wallet."),
    "mediaprovider" to listOf(".mediaprovider."),
    "fido" to listOf(".fido."),
    "intentroute" to listOf(".intentroute."),
    "clockfont" to listOf(".clockfont."),
)

if (isLiteBuild) {
    val target = liteTarget
        ?: throw GradleException("[realOS Lite] mode=lite requires a target scope. Build aborted.")
    val keptPrefixes = SCOPE_MANIFEST_PREFIXES[target]
        ?: throw GradleException(
            "[realOS Lite] no manifest prefix table entry for target='$target'. Build aborted."
        )

    val sourceManifest = file("src/main/AndroidManifest.xml")
    val liteManifest = File(
        layout.buildDirectory.get().asFile,
        "generated/liteManifest/AndroidManifest.xml"
    )

    run {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val doc = factory.newDocumentBuilder().parse(sourceManifest)
        val application = doc.getElementsByTagName("application").item(0) as? org.w3c.dom.Element
            ?: throw GradleException("[realOS Lite] AndroidManifest.xml has no <application>. Build aborted.")

        val androidNs = "http://schemas.android.com/apk/res/android"
        val removable = mutableListOf<org.w3c.dom.Node>()
        val removedAliasTargets = mutableSetOf<String>()

        fun readName(element: org.w3c.dom.Element): String =
            element.getAttributeNS(androidNs, "name")

        fun isLauncherComponent(element: org.w3c.dom.Element): Boolean {
            val actions = element.getElementsByTagName("action")
            val categories = element.getElementsByTagName("category")
            var hasMain = false
            var hasLauncher = false
            for (i in 0 until actions.length) {
                val node = actions.item(i)
                if ((node as? org.w3c.dom.Element)?.getAttributeNS(androidNs, "name") ==
                    "android.intent.action.MAIN"
                ) {
                    hasMain = true
                }
            }
            for (i in 0 until categories.length) {
                val node = categories.item(i)
                if ((node as? org.w3c.dom.Element)?.getAttributeNS(androidNs, "name") ==
                    "android.intent.category.LAUNCHER"
                ) {
                    hasLauncher = true
                }
            }
            return hasMain && hasLauncher
        }

        // Pass 1: remove activities / activity-aliases with MAIN/LAUNCHER and record their targetActivity.
        var child = application.firstChild
        while (child != null) {
            val next = child.nextSibling
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                val element = child as org.w3c.dom.Element
                if ((element.tagName == "activity" || element.tagName == "activity-alias") &&
                    isLauncherComponent(element)
                ) {
                    removable.add(element)
                    val target = element.getAttributeNS(androidNs, "targetActivity")
                    if (target.isNotEmpty()) removedAliasTargets.add(target)
                }
            }
            child = next
        }

        // Pass 2: whitelist. A component survives only when it is owned by the lite target scope.
        // This drops the app shell (MainActivity and the activity bodies behind the removed aliases)
        // as well as every component of the other scopes, including their providers, so a lite
        // carrier can never collide with a full install over a fixed provider authority.
        fun isOwnedByTarget(element: org.w3c.dom.Element): Boolean {
            val name = readName(element)
            if (name.isEmpty()) return false
            return keptPrefixes.any { prefix -> name.startsWith(prefix) }
        }

        child = application.firstChild
        while (child != null) {
            val next = child.nextSibling
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                val element = child as org.w3c.dom.Element
                val name = readName(element)
                val isComponent = element.tagName in setOf(
                    "activity", "activity-alias", "provider", "service", "receiver"
                )
                if (!isComponent) {
                    child = next
                    continue
                }
                val shouldRemove = when (element.tagName) {
                    "activity" -> name in removedAliasTargets || !isOwnedByTarget(element)
                    else -> !isOwnedByTarget(element)
                }
                if (shouldRemove) {
                    if (element !in removable) removable.add(element)
                } else {
                    val fqcn = if (name.startsWith(".")) resolvedApplicationId + name else name
                    liteKeptComponents.add(fqcn)
                }
            }
            child = next
        }

        if (removable.isEmpty()) {
            throw GradleException(
                "[realOS Lite] nothing to strip from AndroidManifest.xml for target='$target', " +
                    "cannot build a whitelist manifest. Build aborted; the manifest layout may have changed."
            )
        }
        removable.forEach { application.removeChild(it) }

        liteManifest.parentFile.mkdirs()
        val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
        }
        transformer.transform(
            javax.xml.transform.dom.DOMSource(doc),
            javax.xml.transform.stream.StreamResult(liteManifest)
        )
        println(
            "[realOS Lite] whitelist manifest for target='$target' " +
                "(${removable.size} components removed): $liteManifest"
        )
    }

    android.sourceSets.getByName("main").manifest.srcFile(liteManifest)
}

// Mode-aware keep rules are written here: the lite variant depends on the whitelist manifest above.
scopeKeepRulesFile.parentFile.mkdirs()
scopeKeepRulesFile.writeText(generateScopeKeepRules())
println(
    "[realOS Scope] keepRules scopes=${SCOPE_KEEP_CLASSES.keys.filter { isScopeIncluded(it) }.sorted()}" +
        " liteComponents=${liteKeptComponents.size} -> ${scopeKeepRulesFile.absolutePath}"
)

androidComponents {
    onVariants { variant ->
        val isRelease = variant.buildType == "release"
        val flavor = if (isLiteBuild) "lite" else "full"
        variant.outputs.forEach { output ->
            output.versionCode.set(if (isRelease) releaseVersionCode else debugVersionCode)
            val baseVersionName = if (isRelease) releaseVersionName else debugVersionName
            output.versionName.set(if (isLiteBuild) "$baseVersionName-lite-$liteTarget" else baseVersionName)
            // Artifact naming: app-full-release.apk / app-lite-release.apk (and the debug equivalents).
            output.outputFileName.set("app-$flavor-${variant.buildType}.apk")
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation(project(":libs:dexkit"))

    // coui (ColorOS 17 Compose design system): the only widget library realOS depends on.
    implementation("io.github.itoseo:coui:0.0.1-alpha11")
    implementation("io.github.itoseo:coui-icons:0.0.1-alpha11")
    implementation("io.github.itoseo:coui-material3:0.0.1-alpha11")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
