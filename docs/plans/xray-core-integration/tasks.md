# Tasks: Real libXray-backed XrayCore integration (epic #44)

> Plan: ./plan.md · No spec (epic #44 / issue #66/#72 / ADR 0001 are the contract). Revised after
> multiexpert-review cycle 1.

## T-1 — XrayConfigBuilder: VLESS/Reality URI → Xray JSON (kotlinx.serialization)
- after: none
- files: `data/vpn/src/main/kotlin/.../XrayConfigBuilder.kt`, `data/vpn/src/test/kotlin/.../XrayConfigBuilderTest.kt`, `data/vpn/build.gradle.kts` (add `implementation(libs.kotlinx.serialization.json)` — catalog entry already exists)
- acceptance:
  - GIVEN the default-server URI (`vless://uuid@host:port?type=tcp&security=reality&pbk&fp&sni&sid`) WHEN `XrayConfigBuilder.build(config)` runs THEN it returns Xray JSON with a SOCKS5 inbound on `127.0.0.1` + `TunConfig.localSocksPort` (10808) and a VLESS outbound to `host:port` carrying `id` and `realitySettings{publicKey,shortId,serverName,fingerprint}`, `"network":"tcp"`.
  - THE SYSTEM SHALL assemble the JSON with kotlinx.serialization element APIs (`buildJsonObject`/`JsonObject`), NOT `@Serializable` classes (no serialization compiler plugin is applied), NOT string interpolation, and NOT `org.json` (stubbed under `isReturnDefaultValues=true`).
  - GIVEN a malformed/unsupported URI WHEN `build` runs THEN it returns `null` (no exception across the seam).
- check: `XrayConfigBuilderTest` asserts (a) parsed JSON fields vs a fixture URI, (b) SOCKS port == `TunConfig.localSocksPort`, (c) a field containing `"`/`}` yields still-well-formed JSON with the value contained (injection-safety), (d) malformed URI → `null`. `./gradlew :data:vpn:testDebugUnitTest`

## T-2 — XrayCore protector seam + config flow through the controller
- after: T-1
- files: `data/vpn/src/main/kotlin/.../XrayCore.kt`, `.../ConnectionControllerImpl.kt`, `.../TunnelLauncher.kt`, `.../VpnTunnelService.kt`, `data/vpn/src/test/kotlin/.../ConnectionControllerImplTest.kt` (+ any seam/launcher fakes/tests)
- acceptance:
  - THE SYSTEM SHALL define `XrayCore.start(configJson: String, protector: VpnSocketProtector): Boolean`; `NoOpXrayCore` SHALL ignore the protector; the `XrayCore.kt` KDoc SHALL drop the stale constructor-protector wording. All changes land atomically (no intermediate non-compiling state).
  - THE SYSTEM SHALL widen `TunnelLauncher.launch(server: Server, configJson: String)`; `AndroidTunnelLauncher` SHALL attach `EXTRA_CONFIG_JSON` and SHALL NOT log its value.
  - GIVEN `connect(server)` WHEN `XrayConfigBuilder.build(server.config)` returns non-null THEN state goes `Connecting` and `launcher.launch(server, json)` is called; WHEN it returns `null` THEN state goes `Connecting → Error(TunnelSetupFailed)` and `launch` is NEVER called.
  - `VpnTunnelService.startTunnel` SHALL read `EXTRA_CONFIG_JSON` and call `xrayCore.start(config, this)` (no more `configJson = ""`), and SHALL treat a null/blank extra as `onTunnelError(TunnelSetupFailed)` rather than starting with an empty config (guards the `START_STICKY` sticky-restart case where extras are dropped). Decide `START_STICKY` vs `START_NOT_STICKY`/`START_REDELIVER_INTENT` for `ACTION_CONNECT` per plan Open Questions (recommend `START_NOT_STICKY`).
  - `connect`'s KDoc SHALL note that on config-build failure `currentTarget` is intentionally left set (harmless: `Error` has no legal edge to `Connected`, so a stale `onTunnelEstablished` is dropped).
- check: `ConnectionControllerImplTest` covers both the happy path (launch called with json) and the null-config path (Connecting→Error, launch not called); `grep -R 'start(configJson = "")' data/vpn/src` returns nothing; existing `:data:vpn` unit tests updated and green. `./gradlew :data:vpn:testDebugUnitTest`

## T-3 — LibxrayApi seam + LibXrayCoreImpl (main, JVM-testable)
- after: T-2
- files: `data/vpn/src/main/kotlin/.../LibxrayApi.kt`, `.../LibXrayCoreImpl.kt`, `data/vpn/src/test/kotlin/.../LibXrayCoreImplTest.kt`
- acceptance: THE SYSTEM SHALL define `internal interface LibxrayApi { registerDialerController(onProtect:(Int)->Boolean); runFromJson(datDir,configJson):String; stop() }` and `LibXrayCoreImpl(api: LibxrayApi, datDir: String) : XrayCore` such that `start(configJson, protector)` calls `api.registerDialerController { fd -> protector.protect(fd) }` **strictly before** `api.runFromJson(datDir, configJson)`, base64-decodes + parses the `CallResponse` `{success,error}` (kotlinx.serialization element API — `parseToJsonElement`/`jsonObject`, no `@Serializable`), and returns `success`; `stop()` calls `api.stop()`. Lives in `main` (no AAR reference).
- check: `LibXrayCoreImplTest` with a fake `LibxrayApi` asserts (a) `registerDialerController` called before `runFromJson` (order verification), (b) the registered lambda delegates to `protector.protect(fd)` and returns its boolean, (c) `success=false` CallResponse → `start` returns `false`. `./gradlew :data:vpn:testDebugUnitTest`

## T-4 — XrayCoreProvider source-set seam + onVariants injection
- after: T-3
- files: `data/vpn/src/default/kotlin/.../XrayCoreProvider.kt`, `data/vpn/src/buildNative/kotlin/.../XrayCoreProvider.kt`, `data/vpn/src/buildNative/kotlin/.../RealLibxrayApi.kt`, `data/vpn/src/main/kotlin/.../VpnModule.kt`, `data/vpn/build.gradle.kts`
- acceptance:
  - THE SYSTEM SHALL provide `object XrayCoreProvider { fun create(context: Context): XrayCore }` in two same-package/same-name variants: `src/default` → `NoOpXrayCore()`; `src/buildNative` → `LibXrayCoreImpl(RealLibxrayApi(), context.filesDir.path)`. `RealLibxrayApi` (buildNative only) wraps the gomobile `Libxray` class.
  - `build.gradle.kts` SHALL inject the source dir via `androidComponents.onVariants { v -> v.sources.kotlin?.addSrcDir(if (buildNative) "src/buildNative/kotlin" else "src/default/kotlin") }` (NOT a config-time `if` add) so exactly one variant is active.
  - `vpnModule` SHALL bind `single<XrayCore> { XrayCoreProvider.create(androidContext()) }`; `XrayCoreProvider` SHALL be referenced ONLY from `vpnModule`; `vpnModule`'s public surface (`ConnectionController` + `TunnelStateSink` bindings) SHALL be unchanged so `#66` stays a one-liner.
- check: `./gradlew :data:vpn:assembleDebug` (no `-P`, no AAR) green; `grep -R 'LibXrayCoreImpl\|RealLibxrayApi' data/vpn/src/main` returns nothing; `grep -R 'XrayCoreProvider' data/vpn/src` shows references only in `VpnModule.kt` + the two provider files. `./gradlew :data:vpn:testDebugUnitTest` green (default variant → NoOp).

## T-5 — Consume XrayCore.aar under buildNative (flatDir in settings + transform)
- after: T-4
- files: `settings.gradle.kts`, `data/vpn/build.gradle.kts`
- acceptance:
  - THE SYSTEM SHALL declare the `flatDir` repository in `settings.gradle.kts` under `dependencyResolutionManagement.repositories`, gated on the property (`if (providers.gradleProperty("vpnis.buildNative").map{it.toBoolean()}.getOrElse(false)) { flatDir { dirs(rootDir.resolve("data/vpn/libs").path) } }`) — NOT in the module `build.gradle.kts` (the project sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS` at `settings.gradle.kts:19`, which rejects a project-level `repositories{}` block even inside an `if`).
  - The dependency coordinate `implementation(":XrayCore@aar")` (NOT `files(...)`) SHALL live in `data/vpn/build.gradle.kts` under `if (buildNative)` so AGP's AAR transform extracts the gomobile `.so`.
  - GIVEN `buildNative=false` THEN neither the settings repo nor the module dependency is added.
- check: `./gradlew :data:vpn:assembleDebug` (no flag, no AAR) still green (proves no `FAIL_ON_PROJECT_REPOS` breakage); configuration cache reused across two default runs. Full AAR-present compile + `.so` packaging is proven in T-6.

## T-6 — CI: native-build compiles :data:vpn against the real AAR + gate/integrity
- after: T-5
- files: `.github/workflows/ci.yml`
- acceptance:
  - The `changes` paths-gate regex SHALL also emit `native=true` for `data/vpn/src/buildNative/` and `data/vpn/src/default/`.
  - `native-build` SHALL, after `buildXrayCore.sh` produces the AAR (same job), assert the `data/vpn/libXray` submodule is at ADR-0001's pinned SHA (`git submodule status`), then run `./gradlew :data:vpn:assembleDebug -Pvpnis.buildNative=true` (NOT `assembleRelease`) so `LibXrayCoreImpl`/`RealLibxrayApi` compile against the AAR and the `.so` packages; the job is green.
  - Default `build`/`test`/`lint` jobs remain `buildNative=false` and unchanged.
- check: CI run — `native-build` green with the new compile + SHA-assert steps; default jobs green. `gh run watch` after push (per CLAUDE.local.md: if any check fails, launch `debugging-expert`).
