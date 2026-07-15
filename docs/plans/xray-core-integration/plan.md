---
type: plan
slug: xray-core-integration
date: 2026-07-15
status: approved
spec: none
risk_areas: []
review_verdict: pass
review_blockers: []
---

# Plan: Real libXray-backed XrayCore integration (epic #44)

## Context & Decision
Epic #44 needs a real Xray-core proxy behind the already-built `:data:vpn` tunnel. The controller
(`ConnectionControllerImpl`), `VpnTunnelService`, hev bridge, notifications and state machine exist
and are wired against a `NoOpXrayCore` stub (`XrayCore.kt`). The user picked **"Real XrayCore
integration"** as the next epic task. This plan delivers the real `LibXrayCoreImpl`, consumes the
CI-built `XrayCore.aar` without vendoring, adds the source-set seam that lets `vpnModule` pick the
real core only on native builds, and closes the config-flow gap — turning `#66` (swap
`fakeVpnModule`→`vpnModule` in `:app`) into a one-line change and unblocking `#67` on-device QA.
This is the HOW; the WHAT is fixed by epic #44, issue #66/#72 and ADR 0001. Revised after
multiexpert-review (build-engineer, architecture-expert, security-expert) — cycle 1.

## Technical Approach

Five seams. Four live in `:data:vpn/src/main` (compile with no AAR/NDK, JVM-unit-testable); one thin
binding wrapper lives in a `buildNative`-only source set and is the *only* code that touches the AAR.

### 1. Config translation — `XrayConfigBuilder` (`main`, pure Kotlin, kotlinx.serialization)
`Server.config` is a VLESS/Reality URI (`DefaultServer.kt:21`); `runXrayFromJSON` needs Xray-core
JSON. `XrayConfigBuilder.build(config: String): String?` parses a VLESS URI and emits Xray JSON with:
- a **SOCKS5 inbound** on `127.0.0.1:` + `TunConfig.localSocksPort` (10808) — literally sourced from
  the constant so hev and Xray never drift (`TunConfig.kt:37`, `Tun2SocksConfig.kt`);
- a **VLESS outbound** to `host:port` with the parsed `id` (UUID) and, when `security=reality`, a
  `realitySettings{publicKey=pbk, shortId=sid, serverName=sni, fingerprint=fp}` block plus optional
  `flow`; `type=tcp` → `"network":"tcp"`.

**Serialization safety (security-review Issue 4):** the JSON is assembled with **kotlinx.serialization
element APIs** (`buildJsonObject` / `JsonObject` / `parseToJsonElement`), never string interpolation —
every URI-derived value is escaped by construction, closing a JSON-injection vector that `#74`'s
user-imported URIs would otherwise open. `libs.kotlinx.serialization.json` (1.9.0) is already in the
version catalog — just add `implementation(libs.kotlinx.serialization.json)` to `:data:vpn`. **Use the
element/DSL APIs, not `@Serializable` classes** (Phase 3.5): the serialization *compiler plugin* is not
applied by any convention plugin, and `buildJsonObject`/`parseToJsonElement` need only the runtime — so
no shared build-logic change. Do **not** use `org.json` here: this module sets
`testOptions.unitTests.isReturnDefaultValues = true`, so Android's stubbed `org.json` returns defaults
and the unit test would be meaningless.

**Failure mode (architecture-review Issue 4):** `build` returns `null` on a malformed/unsupported URI
(no throwing across the seam). Milestone-1 scope = the default server's shape (VLESS + Reality/TCP);
broader protocol/transport coverage + validation is `#74`.

### 2. Config flow via the controller (`main`) — build in the controller, transport via intent
Build the config where the `Server` already lives — `ConnectionControllerImpl.connect(server)` — not in
the Android-typed launcher (architecture-review Issue 2, option b):
```
connect(server):
  currentTarget = server
  transition(Connecting(server))                 // existing edge
  val json = XrayConfigBuilder.build(server.config)
  if (json == null) { transition(Error(TunnelSetupFailed)); return }   // Connecting→Error, no stuck state
  launcher.launch(server, json)                  // widened signature
```
`TunnelLauncher.launch(server: Server, configJson: String)` gains the config param.
`AndroidTunnelLauncher` attaches it as `EXTRA_CONFIG_JSON` on the explicit, non-exported connect intent;
`VpnTunnelService.startTunnel()` reads it and passes it to `xrayCore.start(...)`. This keeps
`AndroidTunnelLauncher` a pure dispatcher, keeps parsing out of the service (no `ServerRepository` dep),
and makes the parse-failure path unit-testable in `ConnectionControllerImplTest` (the builder is pure
Kotlin, so the controller stays JVM-testable). **No `EXTRA_CONFIG_JSON` value is ever logged**
(security Issue 1) — keep the existing `configJson.length`-only logging discipline (`XrayCore.kt:74`).

### 3. Protector-at-start seam (`main`)
`VpnTunnelService` *is* the `VpnSocketProtector` but is framework-created (not Koin), and libXray's
`registerDialerController` is a global registration that must run before start. Change the seam to
`XrayCore.start(configJson: String, protector: VpnSocketProtector): Boolean`. `NoOpXrayCore` ignores the
protector (opens no sockets); `startTunnel()` passes `this`. **Atomic change set (build-review Issue
5):** the `XrayCore` interface, `NoOpXrayCore`, the `VpnTunnelService.startTunnel` call site (drop
`configJson = ""`), the seam KDoc (remove the stale *constructor-protector* language), and any seam
tests all change together in one task.

### 4. `LibXrayCoreImpl` + `LibxrayApi` seam — ordering testable in `main` (security-review Issue 2)
Split the real core so the security-critical logic is JVM-testable and only a thin binding is native:
- `main`: `internal interface LibxrayApi { fun registerDialerController(onProtect: (Int) -> Boolean); fun runFromJson(datDir: String, configJson: String): String; fun stop() }` — returns the raw base64
  string from libXray.
- `main`: `internal class LibXrayCoreImpl(private val api: LibxrayApi, private val datDir: String) : XrayCore`.
  `start(configJson, protector)` → `api.registerDialerController { fd -> protector.protect(fd) }`
  **before** `api.runFromJson(datDir, configJson)`, then base64-decode → parse the `CallResponse`
  (`{success, data, error}`, kotlinx.serialization) → return `success`. `stop()` → `api.stop()`.
- `buildNative`: `internal class RealLibxrayApi : LibxrayApi` wrapping the gomobile `Libxray` class —
  `registerDialerController(DialerController{ protectFd })`, `newXrayRunFromJSONRequest` →
  `runXrayFromJSON`, `stopXray`. This is the **only** class that references the AAR.

`LibXrayCoreImpl` is unit-tested with a fake `LibxrayApi`: assert `registerDialerController` is invoked
strictly before `runFromJson` (order verification), that the registered lambda delegates to
`protector.protect(fd)` and returns its boolean, and that a `success=false` CallResponse → `start`
returns `false`.

### 5. Source-set seam + AAR consumption (`build.gradle.kts`)
- `XrayCoreProvider` — a stateless factory (`object`) with two variants sharing package+name:
  `src/default/kotlin` → `create(ctx) = NoOpXrayCore()` (active when `buildNative=false`);
  `src/buildNative/kotlin` → `create(ctx) = LibXrayCoreImpl(RealLibxrayApi(), ctx.filesDir.path)`
  (active when `buildNative=true`). `vpnModule` calls `XrayCoreProvider.create(androidContext())` and
  binds the result as `single<XrayCore>` — the *only* call site (architecture-review Issue 3). `main`
  never references `LibXrayCoreImpl`/`RealLibxrayApi` directly.
- **Source-set injection (build-review Issues 1, 6):** register the variant source dir with the
  provider-safe AGP API, not a config-time `if`:
  `androidComponents.onVariants { v -> v.sources.kotlin?.addSrcDir(if (buildNative) "src/buildNative/kotlin" else "src/default/kotlin") }`.
  This keeps the property lazy (configuration-cache-safe) and guarantees exactly one variant is active
  → no duplicate-class clash.
- **AAR consumption (build-review Issue 2):** consume via a `flatDir` repository + a coordinate
  (`implementation(":XrayCore@aar")`), **not** `files(...)`, so AGP runs the full AAR transform and
  actually extracts/packages the gomobile `.so`. **The `flatDir` repository MUST be declared in
  `settings.gradle.kts` under `dependencyResolutionManagement.repositories`, gated on the property**
  (cycle-2 build-review): the project sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS`
  (`settings.gradle.kts:19`), which rejects any project-level `repositories { }` block in a module
  build file **even inside an `if`** — so a `flatDir` in `data/vpn/build.gradle.kts` breaks the default
  build. Declaration in settings:
  ```kotlin
  // settings.gradle.kts, inside dependencyResolutionManagement.repositories
  if (providers.gradleProperty("vpnis.buildNative").map { it.toBoolean() }.getOrElse(false)) {
      flatDir { dirs(rootDir.resolve("data/vpn/libs").path) }
  }
  ```
  The `implementation(":XrayCore@aar")` dependency stays in `data/vpn/build.gradle.kts` under
  `if (buildNative)`. Default builds get neither the repo nor the dependency.

### 6. CI verification (`ci.yml`)
`build` deliberately does not `needs: native-build`, so the AAR only exists inside `native-build`.
- Extend the `changes` paths-gate regex to also fire `native=true` on `data/vpn/src/buildNative/` and
  `data/vpn/src/default/` (build-review Issue 4) — otherwise a PR adding only the native source is
  never compiled in CI.
- In `native-build`, after `buildXrayCore.sh` produces the AAR (build-and-consume in the **same** job —
  integrity guarantee, security-review Issue 5), assert the submodule is at ADR-0001's pinned SHA
  (`git submodule status` check), then run `./gradlew :data:vpn:assembleDebug -Pvpnis.buildNative=true`
  (build-review Issue 3 — not `assembleRelease`: `assembleDebug` compiles `LibXrayCoreImpl`/`RealLibxrayApi`
  against the AAR *and* verifies `.so` packaging, with no signing/R8). Default `build`/`test`/`lint`
  jobs stay `buildNative=false`, unchanged.

## Affected Modules & Files
| Path | Change | Note |
|---|---|---|
| `data/vpn/src/main/kotlin/.../XrayConfigBuilder.kt` | New | VLESS/Reality URI → Xray JSON via kotlinx.serialization; `build(config): String?` |
| `data/vpn/src/main/kotlin/.../XrayCore.kt` | Modified | `start(configJson, protector)`; NoOpXrayCore ignores protector; KDoc rewrite |
| `data/vpn/src/main/kotlin/.../LibXrayCoreImpl.kt` | New | ordering + CallResponse decode; takes `LibxrayApi` + datDir (JVM-testable) |
| `data/vpn/src/main/kotlin/.../LibxrayApi.kt` | New | seam interface over libXray (impl lives in buildNative) |
| `data/vpn/src/main/kotlin/.../ConnectionControllerImpl.kt` | Modified | build config in `connect`; null→`Connecting→Error`; call widened `launch` |
| `data/vpn/src/main/kotlin/.../TunnelLauncher.kt` | Modified | `launch(server, configJson)`; `EXTRA_CONFIG_JSON` (not logged) |
| `data/vpn/src/main/kotlin/.../VpnTunnelService.kt` | Modified | read `EXTRA_CONFIG_JSON`, pass it + `this`; reconsider `START_STICKY` (see decisions) |
| `data/vpn/src/main/kotlin/.../VpnModule.kt` | Modified | bind `XrayCoreProvider.create(androidContext())` (public surface unchanged) |
| `data/vpn/src/default/kotlin/.../XrayCoreProvider.kt` | New | → `NoOpXrayCore` |
| `data/vpn/src/buildNative/kotlin/.../XrayCoreProvider.kt` | New | → `LibXrayCoreImpl(RealLibxrayApi(), …)` |
| `data/vpn/src/buildNative/kotlin/.../RealLibxrayApi.kt` | New | thin gomobile `Libxray` wrapper — only AAR consumer |
| `data/vpn/build.gradle.kts` | Modified | `onVariants` source-dir injection + `implementation(":XrayCore@aar")`, both `buildNative`-gated |
| `settings.gradle.kts` | Modified | gated `flatDir { dirs(".../data/vpn/libs") }` in `dependencyResolutionManagement.repositories` (avoids `FAIL_ON_PROJECT_REPOS`) |
| `gradle/libs.versions.toml` | No change | `kotlinx-serialization-json` 1.9.0 already present; just consume via `libs.kotlinx.serialization.json` (element API only — no compiler plugin) |
| `data/vpn/src/test/kotlin/.../XrayConfigBuilderTest.kt` | New | URI→JSON mapping + malformed URI + injection-safety cases |
| `data/vpn/src/test/kotlin/.../LibXrayCoreImplTest.kt` | New | register-before-run order, protect delegation, success/failure decode |
| `data/vpn/src/test/kotlin/.../ConnectionControllerImplTest.kt` | Modified | widened launch; parse-failure → Error path |
| `.github/workflows/ci.yml` | Modified | paths-gate regex + submodule-SHA assert + `assembleDebug -Pvpnis.buildNative=true` |

## Decisions Made
| Decision | Rationale | Alternatives rejected |
|---|---|---|
| Build config in `ConnectionControllerImpl.connect`, transport via `EXTRA_CONFIG_JSON`; widen `TunnelLauncher.launch(server, configJson)` | Keeps parsing in the JVM-testable controller (failure path unit-testable), launcher stays a pure dispatcher, service needs no `ServerRepository` dep | Build in `AndroidTunnelLauncher` (launcher owns protocol logic, wiring untested); service looks up `ServerRepository` (cross-module dep) |
| `XrayConfigBuilder.build(config): String?` via kotlinx.serialization | Escapes URI values by construction (JSON-injection-safe for `#74`); nullable result → `Connecting→Error`, no stuck state; `org.json` returns stubs under `isReturnDefaultValues=true` | String interpolation (injection); throwing (stuck `Connecting`); `org.json` (untestable here) |
| `LibxrayApi` seam in `main`; `LibXrayCoreImpl` in `main`; only `RealLibxrayApi` in `buildNative` | Makes register-before-start ordering + CallResponse decode JVM-unit-testable; minimal native surface | Whole impl in `buildNative` (ordering guarded only by review, never CI-compiled in default) |
| `XrayCore.start(configJson, protector)` | Protector is the framework-created service, not a Koin singleton; libXray registration must precede start | Protector via Koin ctor (impossible); service registers dialer directly (leaks libXray into `main`) |
| `androidComponents.onVariants { addSrcDir(…) }` for source-set selection | Provider-lazy → configuration-cache-safe; exactly one variant active → no duplicate class | Config-time `if(buildNative)` add (cache-unsafe, stale after flag flip); `main` refs native class (won't compile); product flavors (heavier) |
| `flatDir` (declared in `settings.gradle.kts`, gated) + `:XrayCore@aar` coordinate in the module | Runs AGP's AAR transform → gomobile `.so` extracted/packaged; settings-level repo avoids `FAIL_ON_PROJECT_REPOS` rejection | `files("libs/XrayCore.aar")` (skips transform → `.so` unpackaged → runtime UnsatisfiedLinkError); `flatDir` in the module `build.gradle.kts` (rejected by `FAIL_ON_PROJECT_REPOS` even inside `if`) |
| CI verifies via `:data:vpn:assembleDebug -Pvpnis.buildNative=true` in `native-build` (same job as AAR build) | Compiles native seam + verifies `.so` packaging, no signing/R8; build-and-consume in one job = integrity | `assembleRelease` (R8/signing risk, slower); separate job (artifact plumbing, weaker integrity); `build needs: native-build` (doubles critical path — rejected in #72 cycle-2) |
| Connect intent stickiness: switch `ACTION_CONNECT` to `START_NOT_STICKY` (controller drives reconnect) | Avoids persisting the credential-bearing `EXTRA_CONFIG_JSON` in the OS sticky-intent store and re-establishing with stale creds after a kill | Keep `START_STICKY` (persists secret, silent stale reconnect). *Confirm at checkpoint — see Open Questions.* |

## Risks & Mitigations
| Risk | Severity | Mitigation |
|---|---|---|
| gomobile Java names differ from assumed (`Libxray`, method casing) | major | `RealLibxrayApi` is the only affected class (`buildNative`-only); first CI bind is ground truth — adjust names against the generated AAR |
| `XrayConfigBuilder` emits JSON Xray rejects at runtime | major | Unit-test URI→JSON against a fixture; runtime correctness is `#67`; SOCKS inbound port/address tied to `TunConfig` constants |
| Native source-set/AAR wiring breaks default `./gradlew build` (no NDK) | critical | Everything native behind `-Pvpnis.buildNative=true`; default build adds no repo/dep and no `src/buildNative`; `onVariants` keeps it lazy; default CI stays `buildNative=false` |
| gomobile `.so` not packaged (wrong AAR consumption) | critical | `flatDir` + coordinate (AGP transform), not `files()`; CI `assembleDebug -Pvpnis.buildNative=true` verifies packaging |
| register-before-start regresses → silent routing loop | major | Ordering lives in `main` `LibXrayCoreImpl`, asserted by a JVM order-verification unit test + KDoc |
| Server credentials in an intent extra / logs | minor | Explicit intent to a non-exported same-process `<service>`; `EXTRA_CONFIG_JSON` never logged; `START_NOT_STICKY` avoids sticky-store persistence |
| Loopback SOCKS `127.0.0.1:10808` reachable by co-resident apps | minor (accepted) | Inherent to the tun2socks architecture (v2rayNG parity); accepted for Milestone-1; SOCKS `accounts` auth is a possible `#74`+ hardening |
| AAR supply-chain (any binary at `libs/XrayCore.aar` is trusted) | minor | Build-and-consume in one CI job; assert submodule at ADR-0001 pinned SHA; toolchain pinned (Go/gomobile/NDK r29); AAR never vendored |

## Verification & Sources
| Source of truth | Type | Status | Sufficient for verification? |
|---|---|---|---|
| Epic #44 + issue #66/#72 + ADR 0001 (`docs/adr/0001-…md`) | requirements | present | yes — define the seam, pins, F-Droid/AAR constraints, swap invariant |
| Default `./gradlew build` green (buildNative=false) | before-state baseline | present (current CI) | yes — proves native wiring does not regress non-native builds |
| `:data:vpn` unit tests: `XrayConfigBuilderTest`, `LibXrayCoreImplTest`, updated `ConnectionControllerImplTest` | unit (L2) | to-capture (this plan adds them) | yes — cover URI→JSON, injection-safety, parse-failure→Error, register-before-run ordering, CallResponse decode |
| `native-build` job compiling `:data:vpn` with `-Pvpnis.buildNative=true` (`assembleDebug`) against the real AAR | build proof (L0) | to-capture (this plan adds it) | yes — the only mechanical proof `RealLibxrayApi`/`LibXrayCoreImpl` link against libXray and the `.so` packages |
| On-device real connection (VLESS/Reality) | manual (L5) | absent — owned by `#67` | no for this plan — L5 deferred to `#67`; needs a device + operator-provisioned real server creds |

**Testing strategy (pyramid levels):** L0 build (default jobs at `buildNative=false`; native-build at
`buildNative=true`), L1 detekt, L2 unit (`XrayConfigBuilder` incl. malformed + injection fixtures,
`LibXrayCoreImpl` order/decode, `ConnectionControllerImpl` parse-failure path), L5 manual **deferred to
`#67`** (device + real creds; tracked, not silently skipped). L3/L4 N/A (no new UI / cross-module
integration surface).

## Out of Scope
- `#66` app-level Koin swap (`fakeVpnModule` → `vpnModule` in `VpnisApplication`) — deferred until the
  real core is CI-proven; this plan makes it a one-liner (T-3 asserts `vpnModule`'s public surface is
  unchanged).
- `#72` signing / `sign-and-publish` job — needs secrets.
- `#67` on-device QA — needs a device + operator-provisioned server credentials.
- Full VLESS/Reality/other-protocol import + validation, and SOCKS-inbound auth hardening (`#74`).
- Traffic counters (`#69`), ping (`#71`).

## Open Questions
- [non-blocking] Connect-intent stickiness: switch `ACTION_CONNECT` from `START_STICKY` to
  `START_NOT_STICKY` (recommended — avoids persisting `EXTRA_CONFIG_JSON` in the OS sticky store and a
  silent stale-cred reconnect; controller/#64 reconnect logic drives re-establishment) vs. keep
  `START_STICKY`. Decide during T-2; confirm at the interactive checkpoint.
- [non-blocking] `flatDir` coordinate string exact form (`:XrayCore@aar` vs `name:` map) — pick whatever
  the first CI bind + configuration cache accept during T-5.
