---
type: progress
slug: notification-traffic
issues: [69, 130]
---

# Progress — Live traffic in the tunnel notification (#69 + #130)

## Tasks
- [x] T-1 — Xray metrics/expvar config (`XrayConfigBuilder`, `TunConfig`)
- [x] T-2 — `queryStats()` read seam + parse (no AAR)
- [x] T-3 — `RealLibxrayApi.queryStats` (AAR-only, buildNative) — code-complete, CI-native verified
- [x] T-4 — `TrafficRateCalculator` (pure delta→bps)
- [x] T-5 — `TrafficSink` → controller self-transition (explicit `is Connected` guard)
- [x] T-6 — `TrafficStatsPoller` + service/DI wiring (stop before `xrayCore.stop()`)
- [x] T-7 — `:core:format` module + move formatter (`explicitApi`, ci path filter)
- [x] T-8 — Render traffic + strings + PendingIntent cache (non-optional)
- [x] T-9 — Presenter throttle, Connected-only (band test ≤60 ∧ ≥25; Error prompt)
- [x] T-10 — Fake parity + full green sweep

## Learnings
- T-7: `HomeFormatters.kt` held *only* the three moved symbols → deleted the whole file (+ its test);
  `HomeScreen.kt` needed the two new `core.format` imports (same-package usage previously). Skipped
  adding `core/format/` to the CI native-path filter: `:core:format` is pure-JVM and flavor-agnostic,
  so it has no native-specific compile path — adding it would trigger costly native builds on trivial
  formatter edits with no correctness gain (standard `build`/`test` jobs already cover its compile).
- T-5: `TrafficSink` kept separate from `TunnelStateSink` (ISP); guard is the explicit `is Connected`
  read — `Connecting/Loading → Connected` are *legal* edges so `isLegalTransition` is NOT the backstop.
- T-4: pure calculator with injected `() -> Long` clock; per-direction reset handling (negative delta
  → 0, not negative); `deltaNanos <= 0` guards divide-by-zero. Doze case validated by a 60s-gap test.
- Verified: `:core:format:test` + `:data:vpn:test` + `:feature:home:compileDebugKotlin` green; ktlint
  clean on all three modules.
- T-1: **context7 corrected the plan** — expvar is *nested* (`stats.outbound.<tag>.{uplink,downlink}`),
  NOT the gRPC `outbound>>>tag>>>traffic>>>uplink` form; and the `/debug/vars` URL is *caller-supplied*
  so we use `127.0.0.1` (not `[::1]`). Config = `stats {}` + `policy.system.statsOutbound*` + `metrics`
  handler + loopback dokodemo-door inbound on `TunConfig.metricsPort` (10809) + routing rule. Shared
  `XrayConfigBuilder.PROXY_OUTBOUND_TAG` const guards drift with the parser; a test asserts it equals
  the emitted outbound[0] tag.
- T-2: `CallResponse[string]` envelope confirmed via Go `nodep/model.go` — `data` carries the expvar
  body as a JSON-*string*, so parse is two-level (envelope → `data` → expvar → nested navigation).
  `queryStats()` returns `null` when counters absent (no traffic yet) so the calculator baselines on
  the first real reading instead of spiking. 5 parse tests (valid/absent/success=false/malformed/url).
- T-3: gomobile name `LibXray.queryStats(base64Url)`; Go `QueryStats` base64-*decodes* its arg, so we
  base64-*encode* the URL first. buildNative-only — not locally compilable (R2); CI-native verifies.
- ktlint `class-signature` rule reformatted the 2-arg fake ctor; used `ktlintFormat` to auto-fix.
- T-6: poller creates a **fresh** `TrafficRateCalculator` per `start()` so each tunnel session
  baselines cleanly (the reset guard would self-heal anyway, but avoids a wasted first tick). Loop is
  single sequential (`while(isActive){ delay; queryStats ?: continue; if(!isActive) break; sample }`);
  `?: continue` skips null ticks. Wired in `VpnModule` (new `TrafficSink` binding + poller `single`
  with `SystemClock.elapsedRealtimeNanos`), started after the presenter in `startTunnel`, stopped in
  `finishTeardown` **before** `xrayCore.stop()` and in `onDestroy`. `VpnModuleCheckTest` (checkModules)
  confirms the graph resolves. Poller test uses `runTest` + `testScheduler` (with `@OptIn(Experimental
  CoroutinesApi)`) — `advanceTimeBy`/`currentTime` are on `testScheduler`, not directly resolvable.
- T-8: `:data:vpn` already had its own `values-ru/strings_vpn.xml` (MissingTranslation is an error in
  this project), so added RU + EN unit labels (`vpn_traffic_unit_*`, mirroring `home_traffic_unit_*`)
  and a `vpn_notification_text_connected_traffic` = "%1$s · ↓ %2$s · ↑ %3$s". Render composes each
  rate via shared `formatBitrate` + module `unitLabel` (data-carrying pattern kept). Connected falls
  back to plain copy when `traffic == null`. PendingIntent cached (`@Volatile`, app context) — test
  asserts `actions[0].actionIntent` is the **same instance** across two `build()`s (Robolectric).
- T-9: split the deduped stream — `Connecting`/`Error` pass immediately, the `Connected` sub-stream
  is `.sample(1000)`d — then `merge`d (D6/R3). Extracted the merge/sample into an `internal
  contentPipeline(): Flow<NotificationContent>` seam: **required** because `Flow.sample`'s fixed
  ticker never idles (so `advanceUntilIdle` hangs on a live source) AND Robolectric can't count
  re-`notify()`s to one slot — the seam lets the band test **count emissions** directly (drove 120
  distinct traffic values over 60s virtual @2/s → asserted ≤60 ∧ ≥25). Existing presenter tests
  migrated to bounded `settle()` (advance 1 window) + `stop()`; `sample` is `@FlowPreview` (opt-in);
  the `distinct`-collapse test now uses `Connecting` (bypasses the sampler, completing source).
- T-10: `FakeConnectionController` already ticks `Connected.traffic` every 1s (rxBps 125_000 →
  "125.0 KB/s", txBps 25_000 → "25.0 KB/s") — valid shape through the pipeline; no fake change needed.
  Full-project `./gradlew test ktlintCheck` + `:data:vpn:lintDebug` (no MissingTranslation) green.
- code-reviewer gate: **WARN, 0 BLOCK**. In-scope fix applied — `TunnelNotifications` gained a
  `@VisibleForTesting resetCachesForTest()` (the process-lifetime disconnect-PendingIntent cache could
  leak across Robolectric test classes); called from the two build()-using test `@Before`s. Reviewer's
  `contentPipeline` double-subscription note: no bug — branches are disjoint (Connected vs not) and the
  source is a conflated StateFlow (documented). Two findings were in pre-existing #114/#131 code
  (`HomeViewModel.requestedThisProcess` grant-then-revoke edge; `HomeRoute` deep-link swallows failure)
  — OUT OF SCOPE for #69/#130; surfaced to the owner, not fixed here.
