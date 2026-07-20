---
type: tasks
slug: notification-traffic
issues: [69, 130]
---

# Tasks — Live traffic in the tunnel notification (#69 + #130)

Ordered. Each task is small enough to implement and verify in one pass. `after:` = hard dependency.

---

## T-1 — Xray metrics/expvar config
**Files:** `XrayConfigBuilder.kt`, `TunConfig.kt`, `XrayConfigBuilderTest.kt`
**Depends:** —
Add a `metricsPort` to `TunConfig`. Extend `XrayConfigBuilder.assembleJson` to emit: `stats {}`,
`policy.system { statsOutboundUplink=true; statsOutboundDownlink=true }`, a `metrics` object
(tag) with a dokodemo inbound bound at `metricsPort`, and a routing rule sending that inbound tag to
the metrics handler so Go expvar `/debug/vars` exposes `outbound>>>proxy-out>>>traffic>>>uplink|downlink`.
Verify the exact JSON against the pinned Xray-core version (context7) before finalizing.

**Acceptance (Given/When/Then):**
- GIVEN a valid VLESS/Reality URI, WHEN `build(uri)` runs, THEN the JSON contains `stats`,
  `policy.system.statsOutboundUplink/Downlink=true`, a metrics inbound listening on
  `TunConfig().metricsPort`, and a routing rule to the metrics tag.
- **Check:** new assertions in `XrayConfigBuilderTest` (parse emitted JSON with kotlinx element API;
  assert keys + port). `./gradlew :data:vpn:testDebugUnitTest` green.

---

## T-2 — `queryStats()` read seam (interfaces + parse, no AAR)
**Files:** `XrayCore.kt`, `LibxrayApi.kt`, `LibXrayCoreImpl.kt`, `LibXrayCoreImplTest.kt`
**Depends:** after: T-1
Add `queryStats(): TrafficCounters?` to `XrayCore` (`TrafficCounters` = cumulative rx/tx Longs, a
small `:data:vpn` internal type) with `NoOpXrayCore` returning `null`. Add `queryStats(server:
String): String` to `LibxrayApi`. `LibXrayCoreImpl` builds the metrics URL
(`http://[::1]:${TunConfig().metricsPort}/debug/vars`), calls `api.queryStats`, base64-decodes +
parses the expvar JSON to extract the two counters (mirror `parseCallResponse`; `java.util.Base64` +
kotlinx element API), returns `null` on any failure.

**Acceptance:**
- GIVEN an expvar JSON body with the uplink/downlink keys, WHEN `LibXrayCoreImpl.queryStats()` runs
  (fake `LibxrayApi`), THEN it returns the parsed cumulative counters; malformed/missing → `null`.
- **Check:** `LibXrayCoreImplTest` cases (valid, missing keys, malformed base64). `./gradlew :data:vpn:testDebugUnitTest`.
- **Guard:** grep confirms no AAR/`libXray` import outside `src/buildNative`.

---

## T-3 — `RealLibxrayApi.queryStats` (AAR-only)
**Files:** `data/vpn/src/buildNative/.../RealLibxrayApi.kt`
**Depends:** after: T-2
Implement `queryStats(server)` by base64-encoding the server URL and calling the gomobile
`LibXray.queryStats(...)`, returning the raw base64 `CallResponse` string. AAR call only; no parsing here.

**Acceptance:**
- GIVEN a native build, WHEN `queryStats(url)` is called, THEN it delegates to `LibXray.queryStats`
  and returns its result unmodified.
- **Check:** compiles under `-Pvpnis.buildNative=true` (CI native job, or local per CLAUDE.local.md
  symlink materialization). No JVM unit test (source set excluded from default).

---

## T-4 — `TrafficRateCalculator` (pure delta→bps)
**Files:** `TrafficRateCalculator.kt` (new), `TrafficRateCalculatorTest.kt` (new)
**Depends:** —  (can proceed in parallel with T-1..T-3)
Pure class: `fun sample(rxCumulative, txCumulative): TrafficStats` using an injected `() -> Long`
clock. First sample → rate 0, cumulative echoed. Steady samples → bytes/sec over the real elapsed
interval. Counter reset (new < previous) → rebaseline, rate 0, no negative. Clock non-advance → guard
against divide-by-zero. **Production clock = `SystemClock.elapsedRealtimeNanos()`** (counts deep
sleep), wired in `VpnModule` — **not** `System.nanoTime()` (pauses in Doze → spurious post-wake spike).

**Acceptance:**
- GIVEN two samples 1s apart with +125_000 rx, THEN `rxBps ≈ 125_000`; GIVEN first sample, THEN
  rates 0; GIVEN a decreasing counter, THEN rebaseline with 0 rate (never negative).
- GIVEN a large elapsed gap between samples (simulated Doze wake), THEN the rate divides bytes by the
  **true** elapsed interval (no spurious spike).
- **Check:** `TrafficRateCalculatorTest` (JVM, no Robolectric). `./gradlew test` runs it.

---

## T-5 — `TrafficSink` → controller self-transition
**Files:** `TrafficSink.kt` (new), `ConnectionControllerImpl.kt`, `ConnectionControllerImplTest.kt`
**Depends:** —
Add a **narrow** `interface TrafficSink { fun onTrafficSample(stats: TrafficStats) }` (separate from
the lifecycle `TunnelStateSink` — ISP). `ConnectionControllerImpl` implements it: read `_state.value`;
**only** in the `is Connected` branch, `transition(s.copy(traffic = stats))`; else drop. The explicit
`is Connected` read — NOT `isLegalTransition` (Connecting/Loading→Connected are legal) — is the guard.

**Acceptance:**
- GIVEN state `Connected`, WHEN `onTrafficSample(s)`, THEN state becomes `Connected(traffic=s)` (same
  server/since). GIVEN `Disconnected`, WHEN `onTrafficSample(s)`, THEN state unchanged.
- GIVEN state `Connecting`, WHEN `onTrafficSample(s)`, THEN state stays `Connecting` (does **not**
  synthesise a `Connected`).
- **Check:** new `ConnectionControllerImplTest` cases (incl. the Connecting case). `./gradlew test`.

---

## T-6 — `TrafficStatsPoller` + service/DI wiring
**Files:** `TrafficStatsPoller.kt` (new), `TrafficStatsPollerTest.kt` (new), `VpnTunnelService.kt`,
`VpnModule.kt`, `VpnModuleCheckTest.kt`
**Depends:** after: T-2, T-4, T-5
`single` holding `XrayCore` + `TrafficRateCalculator` + `TrafficSink`. `start(scope)` runs a **single
sequential loop** `delay(pollMs) → xrayCore.queryStats() → calc.sample() → sink.onTrafficSample()`;
null query skipped (debug-level log, not error). `stop()` cancels. Inject into `VpnTunnelService`;
`start(serviceScope)` right after `notificationPresenter.start(...)` (success path); in
`finishTeardown()` `stop()` the poller **before `xrayCore.stop()` and before `notificationPresenter.
stop()`**; also `stop()` in `onDestroy()` (idempotent). Provide in `VpnModule` with the
`elapsedRealtimeNanos` clock.

**Acceptance:**
- GIVEN a fake `XrayCore` emitting rising counters, WHEN the poller runs under a `StandardTestDispatcher`,
  THEN `sink.onTrafficSample` is called ~once per `pollMs` with computed rates; after `stop()` **no
  further** `onTrafficSample` fires.
- **Check:** `TrafficStatsPollerTest` (virtual time); `VpnModuleCheckTest`/`checkModules` green. `./gradlew test`.
- **Guard:** teardown order asserted — poller stops before `xrayCore.stop()`; lifecycle symmetric with
  presenter (start success path only; stop in teardown + onDestroy).

---

## T-7 — `:core:format` module + move formatter
**Files:** `settings.gradle.kts`, `.github/workflows/ci.yml`, `core/format/build.gradle.kts` (new),
`core/format/src/main/kotlin/org/yarokovisty/vpnis/core/format/BitrateFormatter.kt` (new),
`core/format/src/test/kotlin/org/yarokovisty/vpnis/core/format/BitrateFormatterTest.kt` (new),
`feature/home/HomeFormatters.kt`, `feature/home/build.gradle.kts`, `feature/home/HomeScreen.kt`,
`feature/home/HomeFormattersTest.kt`
**Depends:** —
Create pure-JVM `:core:format` (`id("vpnis.jvm.library")`, no Android/domain deps, **no new catalog
entries** — `testImplementation(libs.junit)` suffices). Move `formatBitrate`, `FormattedBitrate`,
`BitrateUnit` there as `public` **with explicit visibility + return types** (`vpnis.jvm.library`
enforces `explicitApi()`), package `org.yarokovisty.vpnis.core.format`. Add `implementation(project(
":core:format"))` to `:feature:home` and re-point imports; delete the moved symbols from
`HomeFormatters.kt` (keep any Home-only formatters). Move the formatter test to `:core:format`. Add
`core/format/` to the `changes` path filter in `ci.yml`.

**Acceptance:**
- GIVEN the move, WHEN `./gradlew :core:format:compileKotlin :core:format:test :feature:home:test
  ktlintCheck` runs, THEN green, and `HomeScreen` compiles against `org.yarokovisty.vpnis.core.format`.
- **Check:** builds + tests pass; grep confirms `formatBitrate` no longer defined in `:feature:home`.
- **Guard:** `:core:format` `build.gradle.kts` has no `project(":core:domain")` / no Android plugin;
  `explicitApi` compile clean.

---

## T-8 — Render traffic in the notification + strings + PendingIntent cache
**Files:** `TunnelNotifications.kt`, `data/vpn/build.gradle.kts`, `strings_vpn.xml` (+ `values-ru`),
`TunnelNotificationsTest.kt`
**Depends:** after: T-7
Add `implementation(project(":core:format"))` to `:data:vpn` (**unconditional** — used from `main`,
runs on both flavors). In `render()` Connected branch, format `content.traffic` (down/up) via
`formatBitrate` + new `:data:vpn` unit-label strings (RU/EN, mirroring Home's `home_traffic_unit_*`),
showing a graceful placeholder when `traffic == null`. Cache the disconnect `PendingIntent` once
(constant `FLAG_IMMUTABLE`) — **non-optional** (build() is now a per-second hot path; getService is a
binder round-trip).

**Acceptance:**
- GIVEN `Connected(traffic=TrafficStats(...))`, WHEN `build()` renders, THEN the text contains
  formatted down/up using the shared formatter; GIVEN `traffic=null`, THEN a clean fallback (no crash).
- **Check:** `TunnelNotificationsTest` render assertions (Robolectric for string res); PendingIntent
  constructed once (assert via a counting seam or single-construction refactor). `ktlintCheck` green.

---

## T-9 — Presenter throttle — measurable ≤1 notify()/sec (Connected sub-stream only)
**Files:** `TunnelNotificationPresenter.kt`, `TunnelNotificationPresenterTest.kt`
**Depends:** after: T-8
Apply `.sample(1000.milliseconds)` to the **`Connected` sub-stream only**, after `distinctUntilChanged()`
and upstream of `flowOn` — split by content type so `Connecting` and the `Error`→alert branch pass
through **un-throttled** (never delayed). Dispatcher = the existing injected `mapDispatcher` ctor param
(tests pass a `StandardTestDispatcher`). Update KDoc (remove the "#130 will add sample" deferral note;
document Connected-only sampling + the idle-ticker trade).

**Acceptance (measurable — epic #126 DoD):**
- GIVEN distinct `Connected` traffic samples every ~2s across a virtual 60s window, WHEN collected
  under `StandardTestDispatcher` with `advanceTimeBy(60_000)`, THEN `notify()` on `NOTIFICATION_ID` is
  called **both `≤ 60` AND `≥ ~25`** (throttles *and* keeps updating — a bare `≤60` passes on 0 emits).
- GIVEN `Connecting` then `Connected` <1s apart, THEN the terminal notify carries `Connected` and ≤1
  notify fires in that window (latest-wins, transition not lost).
- GIVEN repeated **identical** `Connected` content, THEN `distinctUntilChanged` collapses it to one notify.
- GIVEN an `Error` emission mid-traffic-stream, THEN the alert (slot 1002) posts **promptly**
  (un-sampled) and the dedup gate is unaffected.
- **Check:** new/updated `TunnelNotificationPresenterTest` counting notifications over advanced virtual
  time; existing presenter tests still green. `./gradlew test`.

---

## T-10 — Fake parity + full green sweep
**Files:** `data/fake/.../FakeConnectionController.kt` (verify), CI/test run
**Depends:** after: T-6, T-9
Confirm `FakeConnectionController` already emits periodic `Connected` traffic (it does — ~1Mbps down
tick) and that its cadence/shape renders correctly through the new pipeline in the default (non-native)
build. Adjust only if the display/throttle exposes a mismatch. Run the full quality loop.

**Acceptance:**
- GIVEN the default-flavor app (fake backend), WHEN connected, THEN the notification shows updating
  traffic throttled to ≤1/sec with no heads-up/sound, single slot.
- GIVEN a native build on device (BLOCKING for #69, QA #133 scope), THEN `queryStats()` returns
  non-zero counters, real down/up renders, and a battery-historian sanity check over a 30–60 min
  screen-off session shows acceptable wakeups/CPU attributed to `:data:vpn` (R6).
- **Check:** `./gradlew test ktlintCheck` green; `check` skill clean; manual/`run` smoke on default flavor.
