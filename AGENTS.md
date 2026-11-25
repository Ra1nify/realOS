# AI Code Direction Guide

This file guides AI changes and reviews for the Xposed, LSPosed and decompiled-host adapter projects in
this workspace. The goals are to keep multi-version compatibility, keep runtime hook cost low, remove
redundant fallbacks, and keep every change reviewable, verifiable and maintainable.

These are workspace-wide rules that apply to every module. Before starting a task you must identify
the current project, the target host, the symbol resolution architecture and the verification method,
then apply the constraints below. Do not treat one project's directory name, package name, resolver
name or UI text location as a workspace-wide fact.

## Core principles

These principles run through the whole document; later sections reference them instead of restating
them.

- **Reuse first**: when a problem is general or the domain is mature, search for and evaluate proven
  open-source implementations, libraries or practices on GitHub first, and prefer referencing or
  reusing them over writing your own. Before reusing, confirm that the licence, maintenance status,
  size impact, runtime cost and adaptation boundaries are compatible with the current project's
  constraints.
- **First-principles debugging**: reconstruct the system's real inputs, state, call chain and
  invariants first, then form falsifiable hypotheses. Never patch blindly from symptoms or past
  experience. Build a repeatable feedback loop first, confirm the root cause, then make the minimal
  fix and verify for regressions.
- **Reverse-engineering evidence chain first**: when host behaviour, obfuscated call chains or runtime
  hooks are involved, first establish the correspondence between JADX static evidence, runtime call
  evidence and the module's own state evidence. A single log line, a single decompiled sample or a
  single experiment is never enough to conclude behaviour.
- **Fail closed**: when symbol resolution fails, candidates are not unique, confidence is too low,
  resource or structure features are unstable, or a dependency is unmet, disable that feature and
  emit one locatable reason. Do not add speculative fallbacks and do not affect other features.
- **Multi-version compatibility**: every resolution rule and feature must target multiple versions.
  Extract stable features that hold across versions from decompiled code; never hardcode symbols or
  behaviour for the single APK sample in front of you.
- **Minimal change**: make only the changes directly required by the current task (see section 2).

## Code design rules

- Stay faithful to source and reverse-engineering facts; do not add requirements beyond the task.
- Use `adb shell -su` only for silent background information extraction. Do not control the screen.
  For screenshots or layout checks, ask the user to set up the foreground first.
- Do not build unless the user reports a build problem or provides a build error log; static work is
  the default.
- For long tasks, propose an assessment, test or implementation plan when needed.
- Comments: concise en-US. State only what the code does. No rationale, history or decorative prose.
- Review code and use the simpler, optimized form where behaviour stays the same.
- Hook code must follow JADX/dexsql results; no hardcoded symbols.
- Follow standard indentation and naming. Code and comments are en-US; conversation follows the
  user's language.

## 0. Workspace context

Common directory meanings:

- Module source: for example `tbhook_api101`, `BBZQ`, `qb-rewardskip-api101`, `bili-adskip-api101`.
- Decompiled host code: for example `tieba`, `bili`, `qb`, used to verify host behaviour, call chains,
  resources and hook points.
- Xposed/LSPosed API reference: for example `libxposed102`, `xposed api101`, or a similar docs
  directory.
- Local Android device: never assume a fixed port. Use `adb devices` to confirm an online device.
  Use `adb shell -su` only for silent background information extraction; do not control the screen.
  For screenshots or layout checks, ask the user to set up the foreground first.

Before starting a task you must pin down: the module source directory to modify, the matching host
decompiled directory, that module's symbol resolution entry point and its data model / cache / status
output, where UI text is centralized, which processes the feature runs in, and whether it should be
fully short-circuited once the user turns it off.

## 1. Confirm before working

Complete the minimum necessary confirmation before changing code:

- State the task goal, the success criteria and the impact scope; ask first when the requirement is
  unclear.
- Read the relevant existing code and plan to touch only the files the task requires.
- Decide whether the task involves hook points, obfuscated symbols, target app resource IDs, UI text,
  runtime logs, persistence, background tasks or caches.
- Describe the hook features that may be affected; a change to one feature must not affect others by
  default.
- Verify target app behaviour against the matching host decompiled code; for Xposed/LSPosed APIs, rely
  on the API docs or source the current project actually depends on.

### 1.1 Reverse-engineering research and the evidence chain

- First fix the target host version, process, APK/JADX directory, module hook entry point and a
  reproducible procedure; record the version fingerprint so one APK's obfuscated names are never
  mistaken for a cross-version rule.
- **JADX / static analysis** builds the class hierarchy, field semantics, callers and callees, draw
  order, caches and invalidation paths. Follow already-confirmed View, Model, Draw, Shadow and
  Invalidate chains; do not run unbounded full-text searches.
- **Frida** confirms real runtime objects, parameter types, return values, instance identity, field
  values and call order. Observe briefly, rate-limited and summarized; never print whole objects or
  stacks inside high-frequency draw callbacks.
- **LSPosed hooks** verify where the module actually installs, its parameter changes and its feature
  isolation. Business hooks may only consume symbols the resolver already validated; never copy
  Frida/JADX temporary names straight into business logic.
- **logcat** confirms hook install state, exceptions, threads and lifecycle boundaries. Clear old logs
  first and filter on an explicit tag; remove or disable high-frequency debug channels once diagnosis
  is done.
- The usual research order is: model in JADX -> verify hypotheses with Frida -> minimal LSPosed hook ->
  compare module and host state in logcat -> go back to JADX and correct the call chain. When no
  two-way correspondence can be established, stay fail closed and do not guess a patch.
- Every conclusion should record its evidence source, host version, reproducibility and remaining
  uncertainty. When static and runtime evidence conflict, narrow the experiment first and re-confirm
  the object, thread, coordinate space and cache boundary.

## 2. Change scope

- Make only the minimal change directly related to the current task; do not refactor, reformat, rename,
  clean imports or restyle code that is unrelated.
- Do not add features, config keys, compatibility paths or extensibility that the requirement does not
  justify. Do not change working logic just to make it "cleaner".
- Extract a shared utility only when several callers actually exist; do not turn single-use logic into
  a framework.
- When uncommitted changes already exist, never revert the user's or another tool's work. Confirm which
  files relate to the current task first, then continue on top of the existing changes.

## 3. Obfuscated symbols and the symbol resolution architecture

Obfuscated classes, methods and fields in the target app must all be resolved through the current
module's symbol resolution architecture. Common shapes:

- Single-file resolver: for example `HookSymbolResolver.kt`.
- Layered resolver: for example `BiliSymbolResolver.kt` scans, while `BiliHookSymbols.kt` owns symbol
  data, restore, cache version and the status model.
- DexKit or static scan helpers: for example `DexKitBridgeProvider`, `DexKitRuleVersions`,
  `MethodDescriptor`, `FieldDescriptor`, `HookPointStatus`.

Before adding or changing an obfuscated hook point, find the current module's symbol resolution entry
point and symbol data model. Business hooks only consume already-resolved, already-restored,
already-validated symbols or install plans.

### 3.1 Prohibitions for business hooks

- Never hardcode obfuscated class names, method names, field names, R8 sample names, anonymous class
  names or short package names. Never copy symbols such as `a`, `b`, `h`, `FJ0.c`, `BL:` or
  `r8-map-id` from a decompiled sample into business logic.
- A business hook must not scan classes, walk methods, guess fields, search the dex, parse decompiled
  strings or append its own fallback layer.

Direct hardcoding is allowed only for:

- the Android SDK, Java/Kotlin standard library, Xposed/LSPosed APIs;
- host classes, methods or protocol fields that are clearly unobfuscated and stable across versions;
- the current module's own classes, resources and config keys.

Even when a stable host class name may be hardcoded, if it takes part in a hook install plan, prefer
validating and caching it centrally in the symbol resolver.

### 3.2 Resolver responsibilities

- **Candidate discovery**: find candidates through DexKit, class-name keywords, interface implementors,
  inheritance, method signatures, field structure, call chains, key strings, resource context.
- **Structural validation**: cross-check with multiple dimensions such as parameter types, return type,
  field count and types, interface/superclass, constructors, call relationships and key-string
  combinations.
- **Uniqueness**: candidates must be unique or cleanly partitioned by a stable rule, otherwise fail
  closed.
- **Symbol table emission**: emit lightweight class/method/field/resource descriptors or install plans.
- **Restore and validation**: on later starts, restore `Class`, `Method`, `Field` from the cache and
  confirm the signature still matches.
- **Cache**: decide cache validity from stable conditions such as host fingerprint, module version,
  patch state and symbol rule version; skip the full scan when nothing changed.
- **Status output**: emit a locatable status per hook point, for example
  `HookPoint[...] state=FOUND/MISSING/ERROR/OPTIONAL` or the current module's equivalent.
- **Error isolation**: a scan error in one hook point must not interrupt the others. Never swallow
  exceptions with `getOrNull()`, an empty `catch` or a silent `null`; recoverable sub-scan errors must
  reach the scan log or `scanErrors`, and that feature must fail closed.

Decompiled code is only evidence for finding stable features. New rules target multiple versions; they
do not hardcode symbols for the single APK sample in front of you.

### 3.3 What must stay in sync when adding or changing an obfuscated hook point

Follow the current module's architecture and fill in:

- the symbol data class or install-plan fields;
- JSON / SharedPreferences / file cache serialization and deserialization;
- the restore/recover method and its signature validation;
- the cache schema, rule version or equivalent cache invalidation version;
- `HookPoint[...]` status output, sub hook point statuses, feature status map or equivalent;
- scan error capture plus `scanErrors`, logs and status reporting;
- consistency between user config and feature availability;
- short-circuiting of scanning, hook installation, callbacks, background tasks and persistence when the
  feature is off.

### 3.4 Verification requirements for reverse-engineered hook points

- A hook point must answer four questions at once: who calls it, what the parameters and return value
  are, what the host state is at call time, and how the host continues drawing or invalidating after a
  failure or skip. Confirming only the method signature without the call context is not enough for a
  usable hook point.
- For drawing hooks, record the Canvas state, host clip, save/restore responsibility, draw order and
  whether a cache exists. Every `save()` must be reliably restored along the same control path, and
  exception paths must not pollute later drawing.
- For animation hooks, verify the whole lifecycle: down, move, up, cancel, layout rebuild, View
  detaching from the window and process restart. Do not verify only the down frame or only a normal
  up.
- For redraw/invalidate hooks, confirm the relationship between the host's dirty flag, expanded
  invalidation region, `invalidate()`/`postInvalidate()` and the next full draw. A single-frame
  reflection failure must not unconditionally disable the feature forever, and animation state must
  not be discarded outright while a cache may still be dirty.
- Paths spanning several entity instances or several Views/roots must not rely on object identity
  alone; combine it with verified stable geometry or business keys, and re-confirm the binding when the
  View/root is rebuilt.
- Hook exceptions must be isolated to the current feature or frame; ThreadLocal re-entry flags, Canvas
  state and temporary collections must be cleared in `finally`. One exception must not disable every
  later hook.

## 4. Resource IDs and resource access

Target app resource IDs change with version, channel package, resource reordering or patch state, so
hardcoding breaks multi-version compatibility.

- Business code must not write numeric resource IDs of the target app (`0x7f...`, decimal IDs, or
  target `R.id`, `R.layout`, `R.string` values copied from logs or decompiled code).
- When target resources are needed, resolve them during install or scan through resource name, package
  name, type and verifiable context, and precompute the result as lightweight state. Fail closed when
  the resource name, layout structure, View hierarchy or text features are unstable. If a resource ID
  is part of an obfuscated hook install plan, keep it consistent with the resolver's scan results,
  cache and status output.
- Hardcoded resource IDs are allowed only for the Android SDK or this module's own resources. Referencing
  this module's resources must use the generated `R.xxx.xxx` constants, never integer literals.
- When looking up a View, prefer a stable structure, an explicit parent container and a type check.
  When it is missing, the type mismatches or candidates are not unique, exit on the short path and keep
  a locatable log.

## 5. Hook performance

Runtime hook callbacks must stay lightweight:

- A callback must not scan classes, walk methods, run complex reflection, look up resources, query
  DexKit, do file IO, touch a database, perform network requests or log heavily.
- Anything that can be resolved, validated or precomputed during the scan phase, the first main-process
  start or hook install must not happen in the callback.
- State a callback depends on is precomputed into booleans, collections, function references, restored
  descriptors or lightweight data structures; the callback itself only does simple checks, reads
  arguments, modifies a return value or exits on a short path.
- Debug-style logging of large objects is off by default, or guarded by an explicit switch, sampling and
  a length cap.
- Keep reverse-engineering observation code separate from production business hooks. Frida scripts,
  temporary dumps, detailed stacks and high-frequency logging must not enter the default runtime path.

## 6. Keep the module lightweight

A module should be small and focused, and must not take on long-term maintenance and size cost for a
single hook feature.

- Do not introduce dependencies, SDKs, frameworks, dynamic script engines or large tool libraries the
  requirement does not justify; anything Android/Xposed/Kotlin can do must not add a third-party
  dependency.
- Do not package the target app's decompiled code, resources, APKs, temporary scan artifacts, full logs
  or debug dumps into the module.
- Before adding a dependency, resource or native file, state its purpose, size impact, runtime cost and
  alternatives; do not add it when necessity cannot be proven.
- Debug tools, detailed logging, export capabilities and experimental code are off by default and only
  enabled behind an explicit switch, a debug build or a clear diagnostic path, with length, frequency
  and lifetime limits.
- When deleting a feature or migrating an implementation, also remove now-unused dependencies,
  resources, config keys and cache fields. Do not keep "might be useful later" baggage.

## 7. Feature decoupling, lifecycle and fallback

- Every feature has its own hook install entry point; one feature failing must not affect others.
- The module start path lazy-loads and installs hooks per feature switch; a disabled feature must not
  initialize scanners, background tasks, cache reads/writes or large object state.
- Every user-facing config has a clear effective boundary: enabling it installs only the logic that
  config needs; disabling it uninstalls or short-circuits the matching hook installs, scan triggers,
  background tasks, cache reads/writes, listeners and callback paths.
- The scan results a config depends on must agree with feature status; when the scan verdict is
  unavailable the feature must fail closed even if the user enabled the config, with a locatable reason.
- Exit early in non-target processes, for non-target package names or when the feature is off. Do not
  initialize global singletons, thread pools, timers or broadcast receivers in every process.
- When several features hook the same method, check execution order, parameter changes and state
  conflicts. Do not introduce implicit global state dependencies, and do not mix UI config, symbol
  resolution and hook callback logic.
- Do not add duplicate hooks, duplicate symbol resolution, multiple implementation paths or
  "just in case" reflective fallbacks. When an existing fallback turns up, judge whether it is
  necessary: move what belongs in the resolver there, and delete what can be safely deleted.

### 7.1 Hook state and host lifecycle

- A successful hook install does not mean the feature keeps working; consider symbol restore, View
  attach, layout size, entity instances, caches, redraw requests and animation end state separately.
- Never hold a detached View, Canvas, Drawable or host entity as long-lived state; use weak references
  or explicit lifecycle cleanup, and rebind when the root/View changes.
- Motion/animation state must not be cleared just because one redraw failed; request a normal host
  invalidation and do bounded retries/backoff first, clear only after a defined failure limit, and avoid
  an infinite redraw loop.
- Every modification of the Canvas or of mutable host parameters must define its enter, exit and
  exception-recovery invariants; a hook must restore temporary transforms, clip, re-entry flags and
  argument replacements before returning.

### 7.2 Build-time scope gating and code layering (required reading)

The project controls whether each **business** is packaged through `realos.scope.<business>=1|0` in
`local.properties` (`app/build.gradle.kts`).

**A scope is a business, not a host package**

- One scope maps to one business (`opluskeyboard` / `navigation` / `featureswitch` / `font` / `camera`
  / `launcher` / `wallet` / `mediaprovider` / `fido` / `intentroute` / `clockfont`) and to one
  SpecLoader implementation class.
- One business may span several hosts: `navigation` hooks `com.android.systemui` and also injects a
  config page into `com.android.settings`.
- One host may be injected by several businesses: `com.android.settings` is a target of `navigation`,
  `featureswitch` and `font` at once. `SpecRegistry.findSpecs()` returns a list per package name, which
  naturally supports several specs on one host.
- When adding a new business that hooks an existing host, add a scope key and a Spec class so it gates
  independently; do not stuff it into an existing scope.
- Every host that enters `scope.list` must be a **primary host** for at least one business; that
  business's user switch lives on this host's spec page.

**Values and precedence**

- Only the single-line `realos.scope.<business>` property is read. **The `scope { }` block is not
  supported**, and `luckyfuse.scope.*` is ignored.
- Precedence: command line `-Prealos.scope.<business>=1` > environment
  `ORG_GRADLE_PROJECT_realos.scope.<business>=1` > `local.properties` > default `0`.
- Only `1/0/true/false` are accepted; unknown keys and invalid values warn and are ignored.
- **A business that is not written out counts as disabled** (fail-closed); every business you want to
  keep must be listed explicitly.

**Gating has three parts, all required**

1. `BuildConfig.SCOPE_*`: `SpecRegistry` registers SpecLoaders only for enabled entries;
2. keep rules: `-keep` is generated only for enabled scopes, so a disabled scope's Spec classes are
   unreachable and R8 removes them;
3. dynamic `scope.list`: packages of disabled scopes never enter the LSPosed injection scope; a host
   with no enabled business injection stops appearing in `scope.list` at all.

**Source exclusion (`sourceSets.exclude`) is no longer used**

- Do not go back to "drop the directory from compilation when scope=0": it makes arbitrary scope
  combinations fail to compile and the excluded code never gets type-checked.
- Everything is **compiled in full**; the boundary is enforced by a build-time import scan. If hook code
  of an enabled scope references a package of a disabled scope, configuration fails immediately with a
  readable dependency explanation.
- Artifact trimming is left to R8; a debug build may carry dead code from disabled scopes.

**Code layering (mandatory)**

- `xposed/hooks/<host>/**`: host-side hooks, symbol resolution and injection logic only. **May be
  trimmed by scope.**
- `realos/<host>/**`: app-side code (config read/write, Preference management, launchers, UI text,
  state machines). **UI may depend only on this layer**; `ui/**` and app-side code must not
  `import io.github.ra1nify.realos.xposed.hooks.*`.
- Constants shared between app side and host side (Intent actions/extras, page IDs) live in
  `realos/<host>/XxxContract`, and the hook side references them through a `private const val` alias.
  **Do not define public constants in hook objects for UI to use.**
- Hooks may depend on the app side; the app side must not depend on hooks. Decide which layer a new file
  belongs to before adding it.

## 8. UI text

User-visible text must live in the project's existing central location (for example the Android
resource `src/main/res/values/strings.xml`, a Kotlin text object `UiText.kt`, or the project's existing
Compose/resource facade). Confirm the current project's pattern first; do not apply one project's text
location to another project.

- Activities, Views, Dialogs, Adapters, Compose UI and hook logic must not scatter user-visible text;
  logs, debug information and internal error descriptions are exempt.
- Before adding text, check whether reusable content already exists; do not adjust unrelated text while
  changing one string.
- **Emoji are forbidden**: user-visible text, card titles (Section Title), switch options, description
  text and resource definitions must not contain Emoji symbols (sun, moon, palette, vibration, gear and
  so on); always use clean plain text.

### 8.1 Mask and theme-mode config card rules

- **Light/dark card split rule**: any configuration that involves a mask (Mask/Overlay/Tint) or related
  appearance-rendering attributes (mask colour, mask alpha/concentration, blur amount and so on) **must
  be split into two independent config cards (Section/Card), one for Light Mode and one for Dark Mode**.
  Never merge them into a single switch or a single mixed slider.
- **Layout shape**:
  - `Light mode mask` (light-mode mask colour picker, mask concentration slider, and so on)
  - `Dark mode mask` (dark-mode mask colour picker, mask concentration slider, and so on)

## 9. Runtime logs and persistence

When optimizing from runtime logs, handle first whatever keeps consuming performance or flooding the
log.

- On persistence failures such as `EACCES`, `Permission denied`, a missing file or an unwritable
  directory, do not swallow the exception and keep writing at high frequency. Log one clear reason and
  then back off, disable persistence for this round or switch to memory-only state. Background task
  failures likewise need backoff, a failure count or a stop condition, so they do not retry forever and
  flood the log.
- Statistics, cache and debug-export files should prefer the app's own writable directory; confirm the
  parent directory exists before writing.
- Hooks over high-frequency information streams, lists, images or messages must not log whole objects,
  whole JSON, image URL lists or large fields by default; a structural summary is enough, and a full
  dump must be behind an explicit debug switch with a total length cap.
- After optimizing, when possible, re-check `runtime.log`, the module log or logcat to confirm the same
  warnings no longer repeat.

## 10. Verification requirements

- After a change, run static checks, diff checks, symbol/reference checks and any necessary
  decompiled-code comparison that match the change. Do not build unless the user reports a build
  problem or provides a build error log; static work is the default.
- Do not install, reboot or operate the device by default. Use `adb shell -su` only for silent
  background information extraction; do not control the screen. Ask the user to set up the foreground
  for screenshots or layout checks. Let the user decide the install and reboot scope. Never report a
  build, install or runtime verification as verified when it was not executed.
- Whether the project compiles, and whether the related hook features remain independent.
- Whether every obfuscated hook point goes through the current module's symbol resolver; whether no
  target app resource ID is hardcoded; whether a resource resolution failure fails closed without
  affecting other features.
- Whether new or changed obfuscated hook points appear in the symbol scan status, `FOUND` or equivalent
  on success, and `MISSING`, `ERROR` or a concrete missing field / exception reason on failure; whether
  scan errors are captured into the scan log, `Scan Errors` or the matching status without removing
  other hook points' statuses.
- Whether the scan results accurately reflect feature availability, and whether user config takes effect
  only when its dependencies are satisfied.
- Whether deobfuscation scanning runs only when the cache is missing, invalid or the target fingerprint
  changed; whether later starts and hook callbacks stay on the lightweight path; whether a disabled
  feature is fully uninstalled or short-circuited with no residual performance cost.
- Whether the module added no unnecessary dependencies, resources, background tasks, packaged artifacts
  or default-on debug capability; whether UI text is still centralized; whether duplicate fallbacks,
  redundant implementations or log floods were introduced.

When a build, install, device test or log review was not executed, say so explicitly and at least
complete the static checks that match the change. Do not build needlessly just to satisfy a fixed
process.

## 11. Output requirements

Report according to task size after making changes. When hooks, obfuscated symbols, resources, runtime
behaviour or config keys are involved, prefer including:

```text
1. Which files changed and why
2. Which module source and which host decompiled directory this targets
3. Steps to check the target feature and possibly affected features work
4. Whether the symbol resolver is involved, and what its entry point and data model are
5. Whether obfuscated hook points are involved
6. Whether target app resource IDs are involved, and whether any are hardcoded
7. Whether UI text is involved, and which central entry it lives in
8. Whether dependencies, resources, background tasks or other size/startup cost were added
9. Whether a fallback was added, removed or kept
10. Verification commands executed and their results
11. What could not be verified, and why
12. For runtime issues, whether logs were re-checked and what residual risk remains
13. For new or changed obfuscated hook points, the status output, exception capture path, log review
    result and multi-version applicability
14. Suggested next steps, prioritizing what is most worth verifying, observing or cleaning up
```

If the task only changes docs, comments or pure configuration, a change summary, verification commands
and unverified items are enough.

## 12. Recommended flow

```text
1. Read the requirement and the related code
2. Identify the current module source, host decompiled directory, symbol resolver, UI text entry and
   verification commands
3. Decide whether obfuscated hook points, target app resource IDs, Xposed/LSPosed APIs, UI text,
   runtime logs or persistence are involved
4. Put obfuscated symbols through the symbol resolver for resolution and caching, and extract stable
   cross-version features
5. Never hardcode target app resource IDs; resolve and precompute them at install or scan time
6. Confirm scan results, feature status and user config agree; a disabled feature installs no hook, runs
   no callback and leaves no background cost
7. Change code within the smallest scope, avoiding unnecessary dependencies, resources and startup cost
8. Complete static checks and the host decompiled-code comparison by default; only when the user
   explicitly asks, or a risk cannot be covered by static evidence, does the user decide whether to
   build, install and verify on device, then verify hook independence, callback performance, lifecycle
   and log behaviour
9. Report concisely following the output requirements
```

## Skills: load first (mandatory)

Before your first search, read, or edit, load the matching skill below with the `skill` tool. Skip when no listed skill applies, or for a trivial single-file edit.

- Load `smart-agent-orchestrator` for debugging, "where is X handled", refactoring, architecture, multi-file or multi-module changes, test generation, log triage, or any mention of subagents, parallel work, or model choice. Its default is "do not delegate".
- Load `opencode-config-maintainer` before editing or creating `opencode.json`, `opencode.jsonc`, `.opencode/agents/*.md`, or any model, agent, mode, steps, or permissions setting.
- After loading the orchestrator, state in one line whether you are delegating and why.

If a skill cannot be loaded, follow these minimum rules:

1. Resolve model availability and context size before dispatching. Pass an explicit `model` to every subagent call; omitting it inherits the parent model.
2. Do not delegate single-file edits, tightly coupled refactors, or work needing global consistency.
3. The parent owns git writes, builds, formatters, and tests. Parallel write tasks get disjoint file allowlists.
4. If a permission is denied, ask the user. Never work around it with shell redirection.
5. Do not edit OpenCode configuration without `opencode-config-maintainer`; if it cannot be loaded, ask the user.