---
type: plan
slug: notification-traffic
date: 2026-07-20
status: approved
spec: none
issues: [69, 130]
epic: 126
risk_areas:
  - xray-metrics-config   # exposing /debug/vars expvar so libXray QueryStats returns real counters
  - native-only-verification  # polling path only runs under -Pvpnis.buildNative=true / CI
  - new-module-graph      # :core:format added; dependency direction must stay inward
  - notify-throttle       # measurable ≤1 notify()/sec; sampler must not delay state transitions
  - doze-clock            # rate math must use elapsedRealtimeNanos, not nanoTime (Doze pauses it)
  - screen-off-poll-cost  # 2s loopback GET runs screen-off for the whole session (battery)
review_verdict: conditional
review_blockers: []   # cycle-1 CONDITIONAL findings folded into the plan below (no residual blockers)
---

# Plan: Live traffic in the tunnel notification (#69 + #130)

## Context & Decision

Combine two issues into one deliverable (owner decision — traffic treated end-to-end):

- **#69** — poll real down/up traffic from Xray-core and feed it into
  `VpnConnectionState.Connected.traffic: TrafficStats?` (today always `null`, an MVP stub).
- **#130** — display that traffic in the ongoing FGS notification and **throttle** re-`notify()`
  so the low-importance `vpnis_tunnel` channel never floods the shade. Epic #126 DoD is
  **measurable**: ≤1 `notify()`/sec over a stable 60-second session.

Both the domain model (`TrafficStats`, `Connected.traffic`) and the notification content model
(`NotificationContent.Connected.traffic`) already carry the field — the wiring is what is missing.

The change spans the native/gomobile seam, the Xray JSON config, the data-layer controller, the
service lifecycle, a new shared formatting module, the notification presenter/render, the fake, and
tests. It is planned as **one** deliverable with tasks phased so the #69 data path lands before the
#130 presenter/render consumes it.

## Technical Approach

Five vertical slices, in dependency order:

1. **Stats config (`XrayConfigBuilder`)** — emit the Xray `metrics` object + a dokodemo inbound +
   a routing rule so Go's expvar `/debug/vars` endpoint exposes per-outbound traffic counters, plus
   `stats {}` and `policy.system.statsOutboundUplink/Downlink`. libXray's `QueryStats` reads
   `http://[::1]:<metricsPort>/debug/vars`, so this is the **expvar** path (not the gRPC
   `StatsService`). The metrics port is sourced from `TunConfig` so both sides agree (mirrors how
   `localSocksPort`/`dnsServers` are already shared).

2. **Stats read seam (`XrayCore` → `LibxrayApi` → `RealLibxrayApi`, parse in `LibXrayCoreImpl`)** —
   add `queryStats(): TrafficCounters?` returning cumulative `(uplinkBytes, downlinkBytes)` or
   `null` on failure. Only `RealLibxrayApi` (buildNative) calls the gomobile `LibXray.queryStats`;
   `NoOpXrayCore` returns `null`. The base64/JSON envelope decode + expvar key extraction live in
   `LibXrayCoreImpl` (JVM-testable, mirroring the existing `parseCallResponse`), **not** behind the AAR.

3. **Rate math + poller (`TrafficRateCalculator` pure; `TrafficStatsPoller` thin)** — the calculator
   turns successive cumulative `(rx, tx, elapsedNanos)` samples into `TrafficStats(rxBytes,
   txBytes, rxBps, txBps)` (first sample = 0 rate; guard clock-backwards; detect counter reset →
   treat as fresh baseline, no negative rate). It takes an **injected `() -> Long` elapsed-real-time
   clock** so tests are deterministic; the production impl is `SystemClock.elapsedRealtimeNanos()`,
   **not** `System.nanoTime()` — the latter pauses in deep sleep, so after a screen-off Doze window
   the first post-wake poll would divide real bytes by a stalled interval and show a spurious rate
   spike (review: perf Issue 1). `TrafficStatsPoller` is a Koin `single` owning `XrayCore` + the
   calculator; `start(scope)` runs `while(isActive){ delay(pollMs); xrayCore.queryStats() → calc →
   trafficSink.onTrafficSample() }` as a **single sequential loop** (no concurrent samples — the
   invariant the controller's check-then-act relies on), `stop()` cancels. Owned by
   `VpnTunnelService` exactly like `TunnelNotificationPresenter`: started after `onTunnelEstablished()`,
   stopped in `finishTeardown()` **before `xrayCore.stop()` and before the presenter's `stop()`**
   (so no in-flight `queryStats()` races the Xray shutdown and no late sample drives a `notify()`
   after the presenter gate flips) and in `onDestroy()`.

4. **Sink → controller self-transition (via a narrow `TrafficSink`, not `TunnelStateSink`)** —
   `TunnelStateSink` is documented as the **lifecycle-event** write-back surface (established/stopped/
   error — discrete edges). A ~0.5 Hz telemetry feed does not belong there (ISP — review: arch
   Issue 2). Add a **separate** `interface TrafficSink { fun onTrafficSample(stats: TrafficStats) }`
   in `:data:vpn`; `ConnectionControllerImpl` implements both interfaces; the poller depends only on
   `TrafficSink`. The implementation reads `_state.value` and emits `transition(s.copy(traffic =
   stats))` **only** in the `is Connected` branch, else drops. ⚠️ The safety here is the **explicit
   `is Connected` read**, NOT `isLegalTransition` — `Connecting→Connected` and `Loading→Connected`
   ARE legal transitions, so relying on the guard as a backstop would let a stray sample synthesise a
   bogus `Connected` (review: arch Issue 1). The "every write goes through `transition`" invariant is
   preserved; a straggler sample after teardown is a no-op because `_state.value` is no longer
   `Connected`.

5. **Notification display + throttle + PendingIntent cache (`TunnelNotificationPresenter`,
   `TunnelNotifications`) + shared formatter (`:core:format`)** —
   - New pure-JVM module **`:core:format`** (single purpose: presentation-neutral rate/number
     formatting) holding `formatBitrate(bps): FormattedBitrate`, `FormattedBitrate`, `BitrateUnit`
     (moved from `:feature:home`, made `public` with explicit return types — the `vpnis.jvm.library`
     convention plugin enforces `explicitApi()`). Package `org.yarokovisty.vpnis.core.format`. Both
     `:feature:home` and `:data:vpn` depend on it; each resolves the `BitrateUnit → localized label`
     from its own string resources.
   - `TunnelNotifications.render()` Connected branch renders down/up using `formatBitrate` +
     new `:data:vpn` unit-label strings (RU/EN), keeping `contentFor` pure and i18n at build-time.
   - **Throttle — sample only the `Connected` sub-stream.** `TunnelNotificationPresenter` applies
     `.sample(1000.milliseconds)` to the traffic-refresh path **only**, and lets non-`Connected`
     content (`Connecting`, and the `Error`→alert branch) pass through **un-throttled** so lifecycle
     transitions and the #129 drop-alert are never delayed by up to a window (review: arch Issue 3).
     Concretely: split by content type after `distinctUntilChanged()` — merge `(non-Connected passed
     immediately)` with `(Connected sampled)`. `sample` stays **upstream of `flowOn(Default)`** so its
     ticker runs off the collector/Main (review: perf Issue 5, confirmed correct). `setOnlyAlertOnce(
     true)` suppresses re-alert but not rebuild+`notify()`, so the sampler is what bounds `notify()`
     frequency. (`sample` is chosen over `conflate` to keep a hard ≤1/sec cap independent of poll
     cadence — #130's "decouple poll from notify" intent; the ~1 Hz idle ticker `sample` spawns is a
     documented minor trade, cancelled with the collector on `stop()`.)
   - **Cache the disconnect `PendingIntent` once** (flags constant, `FLAG_IMMUTABLE`) — now
     **non-optional**: `build()` moved from once-per-state-change to up to once-per-second, and
     `PendingIntent.getService` is a binder round-trip, not just an allocation (review: perf Issue 6).
     `NotificationCompat.Builder` reuse stays deferred (a Builder is not safely reused across mutating
     content).

## Affected Modules & Files

| Path | Change | Note |
|---|---|---|
| `settings.gradle.kts` | edit | `include(":core:format")` |
| `.github/workflows/ci.yml` | edit | add `core/format/` to the `changes` path filter; note `:core:format` compiles transitively via `assembleDebug` |
| `core/format/build.gradle.kts` | **new** | `id("vpnis.jvm.library")` (enforces `explicitApi()`), no Android/domain deps, no new catalog entries |
| `core/format/src/main/kotlin/org/yarokovisty/vpnis/core/format/BitrateFormatter.kt` | **new** | moved `formatBitrate`/`FormattedBitrate`/`BitrateUnit`, now `public` w/ explicit signatures |
| `core/format/src/test/kotlin/org/yarokovisty/vpnis/core/format/BitrateFormatterTest.kt` | **new** | moved from HomeFormattersTest |
| `feature/home/.../HomeFormatters.kt` | edit/remove | delete moved symbols; re-point `HomeScreen` imports |
| `feature/home/build.gradle.kts` | edit | `implementation(project(":core:format"))` |
| `feature/home/.../HomeFormattersTest.kt` | edit/remove | superseded by core:format test |
| `data/vpn/build.gradle.kts` | edit | `implementation(project(":core:format"))` |
| `data/vpn/.../XrayConfigBuilder.kt` | edit | emit `metrics` inbound + `stats` + `policy.system.stats*` + routing |
| `data/vpn/.../TunConfig.kt` | edit | add `metricsPort` (shared source of truth) |
| `data/vpn/.../XrayCore.kt` | edit | add `queryStats()`; `NoOpXrayCore` returns null |
| `data/vpn/.../LibxrayApi.kt` | edit | add `queryStats(server): String` |
| `data/vpn/.../LibXrayCoreImpl.kt` | edit | build metrics URL, call api, parse expvar counters |
| `data/vpn/src/buildNative/.../RealLibxrayApi.kt` | edit | call gomobile `LibXray.queryStats` (AAR-only) |
| `data/vpn/.../TrafficRateCalculator.kt` | **new** | pure delta→bps, injected clock |
| `data/vpn/.../TrafficStatsPoller.kt` | **new** | `single`, `start(scope)`/`stop()`, single loop, depends on `TrafficSink` |
| `data/vpn/.../TrafficSink.kt` | **new** | narrow `onTrafficSample(stats)` interface (separate from lifecycle sink) |
| `data/vpn/.../ConnectionControllerImpl.kt` | edit | implement `TrafficSink.onTrafficSample` via explicit `is Connected` read |
| `data/vpn/.../VpnTunnelService.kt` | edit | inject + start poller (success path) / stop **before** `xrayCore.stop()` in teardown |
| `data/vpn/.../VpnModule.kt` | edit | provide `TrafficStatsPoller` + calculator + `elapsedRealtimeNanos` clock |
| `data/vpn/.../TunnelNotifications.kt` | edit | render traffic (`:core:format`); cache PendingIntent |
| `data/vpn/.../TunnelNotificationPresenter.kt` | edit | sample **only** the `Connected` sub-stream after distinct; `Connecting`/`Error` immediate |
| `data/vpn/src/main/res/values/strings_vpn.xml` (+ values-ru) | edit | notification traffic + unit-label strings |
| `data/fake/.../FakeConnectionController.kt` | verify/edit | already ticks fake traffic — confirm cadence/format parity |
| `data/vpn/src/test/.../*` | **new/edit** | calculator, poller, config, presenter-throttle, render tests |

## Decisions Made

- **D1 — Poller owned by the service (not the controller or XrayCore).** The service already owns
  the tunnel lifecycle and `serviceScope`; the controller is deliberately Context-/scope-free (its
  testability rests on owning no coroutines) and `XrayCore` is a narrow stateless seam whose
  narrowness is load-bearing for AAR isolation. Mirrors the existing `TunnelNotificationPresenter`
  wiring — an already-reviewed pattern.
- **D2 — Push via a narrow `TrafficSink`, guarded by an explicit `is Connected` read.** A separate
  `TrafficSink` interface (not the lifecycle `TunnelStateSink`) keeps the sink cohesive (ISP). The
  controller emits `copy(traffic=…)` **only** when `_state.value is Connected`; this explicit read —
  **not** `isLegalTransition` (which permits `Connecting/Loading → Connected`) — is what makes a
  stray/late sample a no-op. Correctness rests on the poller being a **single sequential loop** (no
  concurrent `onTrafficSample` callers).
- **D3 — New `:core:format` module, not `:core:domain`.** `formatBitrate` is a *presentation* concern
  (rounding, `"%.1f"`, a display bucket). `:core:domain` is the dependency sink modelling *what the
  app is*; putting UI-shaped formatting there is the "shared module becomes a dumping ground"
  anti-pattern. Two concrete consumers exist **today** (Home tiles + notification), so a shared
  module is justified, not premature. Cost is one build file + one source + one test.
- **D4 — Rate math in a pure `TrafficRateCalculator` in `main`, clocked by `elapsedRealtimeNanos`.**
  AAR-free arithmetic ⇒ JVM-unit-testable without Robolectric, like `contentFor`/`XrayConfigBuilder`.
  Injected `() -> Long` clock (tests control it); production = `SystemClock.elapsedRealtimeNanos()`,
  which counts real time **including** deep sleep — `System.nanoTime()` stalls in Doze and would
  inflate the first post-wake rate.
- **D5 — Expvar (`/debug/vars`) config, not gRPC `StatsService`.** libXray's `QueryStats` HTTP-GETs
  the expvar endpoint, so the config uses the `metrics` object + dokodemo inbound + routing. Exact
  JSON verified against the pinned Xray-core version (see Verification).
- **D6 — `.sample(1000)` on the `Connected` sub-stream only, after `distinctUntilChanged`,
  presenter-only.** `distinct` operates on the full stream (correct "collapse consecutive duplicates"
  semantics); the sampler applies only to traffic-refresh (`Connected`) content, while `Connecting`
  and `Error` pass through immediately so state transitions and the #129 drop-alert are never delayed.
  `sample` stays upstream of `flowOn(Default)`. `MutableStateFlow` already conflates at the source,
  and the other consumer (`HomeScreen`) must NOT inherit the notification's ≤1/sec budget — cadence is
  a per-consumer policy. `sample` (hard cap, independent of poll cadence) is preferred over `conflate`
  (which would couple notify-rate to poll-rate); its ~1 Hz idle ticker is an accepted minor cost.
- **D7 — Cache disconnect `PendingIntent` (non-optional).** Flags are constant (`FLAG_IMMUTABLE`);
  `build()` now runs up to once/second, and `PendingIntent.getService` is a binder round-trip, so
  building it once is a real hot-path win, not a nicety. `NotificationCompat.Builder` reuse stays
  deferred (not safely reusable across mutating content).
- **D8 — Poll cadence ~2s (constant on the poller); cumulative counters + local delta.** Decoupled
  from the ≤1s notify budget; presenter throttle makes the exact value non-critical for display. Use
  cumulative counters + local delta (no `counter.Set(0)` reset call) so a missed poll never loses
  bytes. ⚠️ The poll does real work every 2s **screen-off for the whole session** (loopback GET +
  base64 + full JSON parse); this is budgeted, not free — see R6 and the on-device battery check.

## Risks & Mitigations

- **R1 — Metrics/expvar config wrong ⇒ poller silently reads zeros.** Highest risk. A JSON-shape
  unit test passes on a well-formed-but-semantically-wrong config, so it cannot prove real counters.
  Mitigate: (1) unit-test the emitted JSON shape in `XrayConfigBuilderTest` (keys present, port ==
  `TunConfig().metricsPort`); (2) verify the exact `metrics`+routing JSON against Xray-core docs
  (context7) for the pinned version; (3) a `LibXrayCoreImpl` parse test over a realistic expvar
  fixture asserting **non-zero** extraction; (4) **on-device** confirmation of a non-zero live read
  is the authoritative gate — `assembleDebug -Pvpnis.buildNative=true` only *compiles* the native
  path, it does not run the app, so the non-zero check is a **blocking on-device acceptance** for the
  #69 slice, not a CI assertion.
- **R2 — Polling path only runs under `-Pvpnis.buildNative=true`.** `NoOpXrayCore.queryStats()`
  returns null in default/CI-unit builds, so JVM tests cover calculator/parse/presenter but NOT the
  live gomobile call. Real verification needs the native build (CI Linux, or local Windows with the
  31 hev-symlink stubs materialized — see CLAUDE.local.md). Mitigate: keep all testable logic
  (parse, delta, throttle) off the AAR; treat on-device as acceptance, documented in Verification.
- **R3 — Sampler delays state transitions.** RESOLVED in design (D6): only the `Connected`
  sub-stream is sampled; `Connecting`/`Error` pass through un-throttled, so the #129 drop-alert and
  lifecycle transitions are never delayed. Verified by a presenter test asserting the `Error` alert
  posts promptly even mid-traffic-stream.
- **R4 — New module churn / `explicitApi()`.** Moving `formatBitrate` risks breaking `:feature:home`
  imports/tests, and `vpnis.jvm.library` enforces `explicitApi()` (every public decl needs explicit
  visibility + return type). Mitigate: move symbols with explicit signatures; `./gradlew
  :core:format:compileKotlin :core:format:test :feature:home:test`; no new version-catalog entries
  (`testImplementation(libs.junit)` already cataloged; no coroutines/serialization/Android).
- **R5 — Thread visibility of samples.** Poller runs on IO; controller `_state` is a
  `MutableStateFlow` (thread-safe). No extra sync needed; correctness of the check-then-act in
  `onTrafficSample` rests on the **single sequential poll loop** (one in-flight sample at a time) —
  documented so a future parallel poller triggers a re-review.
- **R6 — Screen-off poll cost (battery).** The 2s loopback GET + base64 + `Json.parseToJsonElement`
  (allocates an element tree) runs for the whole session including screen-off — a steady background
  wakeup/allocation cost the notify()-frequency DoD does NOT measure. Mitigate: keep poll ≥2s; add an
  **on-device battery-historian sanity check** (wakeups + CPU-time attributed to `:data:vpn` over a
  30–60 min screen-off session) to acceptance; treat adaptive slow-down when backgrounded as a
  fast-follow if drain is observed. Confirm the expvar payload is bounded (not the full Go memstats
  dump) during T-1 native verification.

## Verification & Sources

**Sources of truth for "done":**
- GitHub issues **#69** and **#130** acceptance criteria (the contract — no separate spec doc). #130:
  "Connected notification shows traffic and updates it without jank/sound/reordering" + measurable
  "≤1 notify()/sec over a 60s session". #69: real polling parsing
  `outbound>>>TAG>>>traffic>>>uplink/downlink`.
- Epic **#126** DoD (live content ≤1 notify()/sec, 0 heads-up/sound on `vpnis_tunnel`, single slot).
- **Xray-core docs** (via context7) for the exact `metrics`/expvar config JSON on the pinned version —
  collected during T-1 before finalizing the config; sufficient because the JSON shape is verifiable
  and unit-testable.
These are collected and sufficient to define done for the testable slices; the live-counter reading
is verified on-device/CI (native build), which is the only place the gomobile call executes.

**Testing strategy (pyramid):**
- **L0/L1 pure unit (JVM, `./gradlew test`)** — `TrafficRateCalculator` (first sample, steady rate,
  counter reset, clock-backwards, zero, **and a large elapsed-gap "Doze wake" case** asserting the
  rate uses the true interval, not a stalled clock); `LibXrayCoreImpl.queryStats` parse against a
  **realistic expvar fixture** (mimicking the gomobile `/debug/vars` body) asserting **non-zero**
  counter extraction, plus missing-keys / malformed → null; `XrayConfigBuilder` metrics/stats JSON
  assertions (keys present, port == `TunConfig().metricsPort`); `formatBitrate` moved tests;
  `contentFor`/render traffic mapping.
- **L2 presenter (Robolectric + `StandardTestDispatcher`, dispatcher injected via the existing
  `mapDispatcher` ctor param; `advanceTimeBy(60_000)` to drive `sample`'s ticker)** — **measurable
  throttle as a band, not a ceiling**: over a virtual 60s stable session with traffic changing every
  ~2s, assert `notify()` count on `NOTIFICATION_ID` is **both `≤ 60` AND `≥ ~25`** (proves it
  throttles *and* still updates — a bare `≤60` passes even if the pipeline emits 0). Plus: a
  transition-within-window test (`Connecting` then `Connected` <1s apart → terminal notify carries
  `Connected`, ≤1 in that window); an identical-`Connected`-content test (`distinctUntilChanged`
  collapses to one notify); an `Error`-mid-traffic test (alert on slot 1002 posts **promptly**, not
  delayed by the sampler); PendingIntent constructed once.
- **L2 controller** — `onTrafficSample` copies traffic only in `Connected`; a sample during
  `Connecting` does **not** transition to `Connected`; dropped in `Disconnected`.
- **L2 poller (`StandardTestDispatcher`)** — samples ~once/`pollMs`; no `onTrafficSample` after
  `stop()`; teardown stops the poller before `xrayCore.stop()`.
- **L5 on-device acceptance (native build / QA #133 scope) — BLOCKING for the #69 slice** —
  `queryStats()` returns **non-zero** counters against a live tunnel (JVM tests cannot prove this;
  `assembleDebug` does not run the app); Connected notification shows non-zero down/up updating ~1/s,
  no heads-up/sound, single slot; battery-historian sanity check per R6.

**Guards to assert in review:** no `:data:vpn → :feature:home` edge; `:core:format` imports nothing
app-specific (takes `Long`, returns value+enum); AAR referenced only in `src/buildNative`
(`RealLibxrayApi`); every controller write still routes through `transition`; ktlint clean;
pure-JVM module tests invoked via `./gradlew test`.

## Out of Scope

- Auto-reconnect / `Reconnecting` domain state (#64).
- Home-screen traffic-tile behavior changes beyond consuming the relocated formatter (the tiles
  already render `TrafficStats`; only the formatter's module moves).
- Total-session byte totals / historical graphs; per-app traffic; precision beyond single-decimal
  MB/s (#69 scoped to rate + cumulative bytes).
- Broader Xray transport/security coverage (#74); settings section (#131).
- Removing the MVP `traffic=null` nullability contract (kept so fakes/stubs share the signature).

## Open Questions

- **(non-blocking)** Exact `metrics` listener bind address/port for the pinned Xray-core: libXray's
  `QueryStats` targets `http://[::1]:PORT/debug/vars`. Resolve in T-1 by checking the Xray-core
  version's metrics docs (context7) + a native smoke; default assumption: dokodemo `listen` on
  `127.0.0.1`/`[::1]` at `TunConfig.metricsPort`, routed to the `metrics` outbound tag. Does not
  block writing the surrounding code — only the final JSON literal.
- **(non-blocking)** Whether to also reuse the `NotificationCompat.Builder` instance (D7 secondary).
  Default: cache only the PendingIntent; revisit if profiling shows build() cost.
