# Tasks: Notifications foundation — state-driven content + `NotificationPermissionState` gate (#127)

> Plan: ./plan.md · Acceptance traces to GitHub issue #127 / epic #126 (no external spec).

## T-1 — Refactor `contentFor` to a pure state→content mapper (+ update `build()` in the same edit)
- after: none
- files: `data/vpn/src/main/kotlin/org/yarokovisty/vpnis/data/vpn/TunnelNotifications.kt`, `data/vpn/src/test/kotlin/org/yarokovisty/vpnis/data/vpn/TunnelNotificationsTest.kt`
- acceptance: GIVEN a `VpnConnectionState` WHEN `contentFor(state)` is called with NO Android Context THEN it returns a data-carrying `NotificationContent` (sealed: `Inactive` / `Connecting(serverName)` / `Connected(serverName, since, traffic)`), a **total** `when` over all six states. IN THE SAME EDIT `build(context, content = NotificationContent.Inactive)` is updated to render each subtype (its `Pair` destructuring is gone), where `Inactive` renders to today's static "VPNis"/"Tunnel active" copy so the initial `startForeground` post is unchanged. `NotificationContent` and `contentFor` remain `internal`.
- check: `:data:vpn:test` — updated `TunnelNotificationsTest` builds each `VpnConnectionState` (Server via `Server(ServerId("s"), "Srv", "cfg")`, pattern per `ConnectionControllerImplTest`) and asserts the expected subtype without instantiating a Context; `grep -n "internal" TunnelNotifications.kt` covers `contentFor` + `NotificationContent`; `:data:vpn:assembleDebug` compiles (proves `build()` consumes the new return type); compile fails if the `when` is non-exhaustive.

## T-2 — Introduce `NotificationContent` data subtypes (no formatted strings) + `setOnlyAlertOnce`
- after: T-1
- files: `data/vpn/src/main/kotlin/org/yarokovisty/vpnis/data/vpn/TunnelNotifications.kt`
- acceptance: THE SYSTEM SHALL define `NotificationContent` as a sealed hierarchy carrying DATA only (`Instant since`, `String serverName`, `TrafficStats? traffic`) with zero pre-formatted / localized strings; all localization lives in `build()`; `Connecting` carries `serverName = state.server.name` (non-null domain field); AND `build()` sets `setOnlyAlertOnce(true)` (epic DoD: 0 heads-up/sound once the presenter re-notifies).
- check: `grep -nE "getString|R\.string" TunnelNotifications.kt` shows string resolution only inside `build()`, never in `contentFor` or the `NotificationContent` declarations; `grep -n "setOnlyAlertOnce" TunnelNotifications.kt` present; `:data:vpn:test` green.

## T-3 — Add `NotificationPermissionState` domain contract
- after: none
- files: `core/domain/src/main/kotlin/org/yarokovisty/vpnis/core/domain/permission/NotificationPermissionState.kt`
- acceptance: THE SYSTEM SHALL expose `public interface NotificationPermissionState { public val isGranted: Flow<Boolean>; public suspend fun refresh() }` in `:core:domain` with NO `android.*` import and NO reference to `"vpnis_tunnel"` / channel importance in code or KDoc (channel-agnostic).
- check: `:core:domain:test` compiles; `grep -rnE "android\.|vpnis_tunnel|importance" core/domain/src/main/kotlin/.../permission/NotificationPermissionState.kt` returns nothing.

## T-4 — Android impl `AndroidNotificationPermissionState` + bind in `vpnModule` + gate test
- after: T-3, T-8a
- files: `data/vpn/src/main/kotlin/org/yarokovisty/vpnis/data/vpn/AndroidNotificationPermissionState.kt`, `data/vpn/src/main/kotlin/org/yarokovisty/vpnis/data/vpn/VpnModule.kt`, `data/vpn/src/test/kotlin/org/yarokovisty/vpnis/data/vpn/AndroidNotificationPermissionStateTest.kt`
- acceptance: GIVEN the Android impl WHEN `refresh()` runs THEN `isGranted` reflects `NotificationManagerCompat.areNotificationsEnabled()` **AND** `getNotificationChannel(TunnelNotifications.CHANNEL_ID)?.importance != IMPORTANCE_NONE`; the impl takes `androidContext()`, reads off the hot path (hop to `Dispatchers.Default` in `refresh()`); `vpnModule` binds `single<NotificationPermissionState> { AndroidNotificationPermissionState(androidContext()) }`.
- check: `:data:vpn:test` — `AndroidNotificationPermissionStateTest` (Robolectric) asserts the two-part AND: granted only when app-level enabled AND channel importance != NONE; false when the channel is disabled after an app-level grant (I6). `grep -n "IMPORTANCE_NONE" AndroidNotificationPermissionState.kt` present.

## T-5 — Fake impl `FakeNotificationPermissionState` + bind in `fakeVpnModule`
- after: T-3
- files: `data/fake/src/main/kotlin/org/yarokovisty/vpnis/data/fake/FakeNotificationPermissionState.kt`, `data/fake/src/main/kotlin/org/yarokovisty/vpnis/data/fake/FakeVpnModule.kt`, `data/fake/src/test/kotlin/org/yarokovisty/vpnis/data/fake/FakeNotificationPermissionStateTest.kt`
- acceptance: THE SYSTEM SHALL model pull semantics honestly: a `private var backing = true` written by `setGranted(Boolean)` (NO emit), and `isGranted` = a `MutableStateFlow` updated **only** by `refresh()` (`_isGranted.value = backing`). So `isGranted` emits `true` by default; `setGranted(false)` alone does NOT change the emitted value; only a subsequent `refresh()` surfaces it. `fakeVpnModule` binds `single<NotificationPermissionState> { FakeNotificationPermissionState() }`.
- check: `:data:fake:test` — `FakeNotificationPermissionStateTest` asserts (a) default `true`; (b) after `setGranted(false)` with NO `refresh()`, `isGranted.first()` is still `true` (proves `refresh()` is not a no-op); (c) after `refresh()`, `isGranted.first()` is `false` (Turbine). This falsifies a naïve StateFlow-write fake.

## T-6 — `TunnelNotificationPresenter` (single, owns the slot)
- after: T-1, T-2
- files: `data/vpn/src/main/kotlin/org/yarokovisty/vpnis/data/vpn/TunnelNotificationPresenter.kt`, `data/vpn/src/main/kotlin/org/yarokovisty/vpnis/data/vpn/VpnModule.kt`, `data/vpn/src/test/kotlin/org/yarokovisty/vpnis/data/vpn/TunnelNotificationPresenterTest.kt`
- acceptance: GIVEN a `TunnelNotificationPresenter(ConnectionController, appContext, mapDispatcher = Dispatchers.Default)` (dispatcher injected so tests pass a `StandardTestDispatcher`; `notificationManager` derived once from `appContext`) WHEN `start(scope): Job` is called THEN it (i) `job?.cancel()`s any stale job first, (ii) sets `active=true`, (iii) collects `controller.state.map { contentFor(it) }.filter { it !is NotificationContent.Inactive }.distinctUntilChanged().flowOn(Dispatchers.Default)` and posts to `NOTIFICATION_ID` only when `active` is true, (iv) returns the `Job` for test liveness; WHEN `stop()` is called THEN it flips `active=false` **before** cancelling the job and is idempotent. Bound as `single { TunnelNotificationPresenter(get(), androidContext()) }`. `start()` requires a non-Main scope.
- check: `:data:vpn:test` — `TunnelNotificationPresenterTest` (fake notifier + `TestScope`) asserts: (a) no post for `Inactive` states incl. a `Connected→Disconnected` emission (active-state filter is the primary teardown guard); (b) `distinctUntilChanged` collapses repeated identical content (no storm); (c) after `stop()` no further posts for later emissions (no-notify-after-stop, via the `active` gate) — use a `StandardTestDispatcher` and park a `Connected` emission in the `flowOn` hand-off, call `stop()` while parked, `advanceUntilIdle()`, assert zero posts (exercises the check-then-act window, not just the happy path); (d) **start→kill→start**: `start(scopeA); start(scopeB)` without an intervening `stop()`, then emit — asserts scopeA's collector no longer posts (no double-collection / orphaned collector).

## T-7 — Wire presenter into `VpnTunnelService`; remove `updateNotification`; fix teardown race
- after: T-6
- files: `data/vpn/src/main/kotlin/org/yarokovisty/vpnis/data/vpn/VpnTunnelService.kt`
- acceptance: THE SYSTEM SHALL inject the presenter via the `org.koin.android.ext.android.inject` extension (`private val presenter: TunnelNotificationPresenter by inject()`, same as `stateSink`/`xrayCore`; the service is NOT a `KoinComponent`). As ONE atomic edit at lines ~515–516, replace `updateNotification(content = NotificationContent.Default)` with `presenter.start(serviceScope)` immediately after `stateSink.onTunnelEstablished()` (NOT after `startForeground`, so the `xrayCore.start()`-false / `establish()`-null abort paths never leave a live collector, and there is no window where both fire). In `finishTeardown` call `presenter.stop()` **before** `stateSink.onTunnelStopped()`, and immediately after `ServiceCompat.stopForeground(STOP_FOREGROUND_REMOVE)` add `getSystemService(NotificationManager::class.java).cancel(TunnelNotifications.NOTIFICATION_ID)` (the service has no manager field — fetch inline, as the old `updateNotification` did) as the final sweep. Call `presenter.stop()` in `onDestroy`. Remove the `updateNotification(...)` method entirely. Retain the initial `startForeground(build(this))` post.
- check: `:data:vpn:test` green; `grep -n "updateNotification" VpnTunnelService.kt` returns nothing; `grep -n "presenter.start" VpnTunnelService.kt` is located after `onTunnelEstablished` (not after `startForeground`); `presenter.stop()` appears in both `finishTeardown` (before `onTunnelStopped`) and `onDestroy`; `grep -n "cancel(TunnelNotifications.NOTIFICATION_ID)\|cancel(NOTIFICATION_ID)" VpnTunnelService.kt` present after `stopForeground`; `:data:vpn:assembleDebug` builds.

## T-8a — Wire cataloged test infra (koin-test, robolectric)
- after: none
- files: `data/vpn/build.gradle.kts`, `data/fake/build.gradle.kts`
- acceptance: THE SYSTEM SHALL add `testImplementation(libs.koin.test.junit4)` to `:data:vpn` and `:data:fake` (JUnit-4 runner support; brings `koin-test` transitively — the bare `koin-test` artifact lacks the JUnit-4 runner), and `testImplementation(libs.robolectric)` to `:data:vpn`. All are already in `gradle/libs.versions.toml` (`koin-test-junit4:46`, `robolectric:59`) and `robolectric` is already used by `:feature:home` tests — no new external dependency, no approval gate.
- check: `grep -n "koin.test.junit4\|robolectric" data/vpn/build.gradle.kts` present; `grep -n "koin.test.junit4" data/fake/build.gradle.kts` present; `./gradlew :data:vpn:testClasses :data:fake:testClasses` compiles.

## T-8 — `checkModules` tests for real + fake Koin graphs
- after: T-4, T-5, T-6, T-7, T-8a
- files: `data/vpn/src/test/kotlin/org/yarokovisty/vpnis/data/vpn/VpnModuleCheckTest.kt`, `data/fake/src/test/kotlin/org/yarokovisty/vpnis/data/fake/FakeVpnModuleCheckTest.kt`
- acceptance: GIVEN the real `vpnModule` (under Robolectric with a Robolectric application Context supplied to the Koin check) and the fake `fakeVpnModule` (pure JVM) WHEN `checkModules`/`checkKoinModules` runs THEN both graphs resolve every declared definition without error. (Exact real-graph API form — `checkModules { withInstance<Context>(ctx) }` vs `koinApplication { androidContext(ctx); modules(vpnModule) }.checkModules()` — pick whichever runs green; both use only T-8a's cataloged libs.)
- check: `:data:vpn:test` runs `VpnModuleCheckTest` (Robolectric) green; `:data:fake:test` runs `FakeVpnModuleCheckTest` green.

## T-9 — Guardrails: dependency-direction + full check
- after: T-1..T-8
- files: (verification only) `feature/home/build.gradle.kts`, `data/vpn/build.gradle.kts`, `core/domain/src/main/kotlin/.../permission/NotificationPermissionState.kt`
- acceptance: THE SYSTEM SHALL keep the guardrails: `:feature:home` has NO `project(":data:vpn")`; `:data:vpn` pulls NO Activity/`ActivityResultLauncher`; `:core:domain` permission contract has NO `android.*`; `NotificationContent`/`contentFor` are `internal`.
- check: `grep -n "data:vpn" feature/home/build.gradle.kts` empty; `grep -rnE "ActivityResultLauncher|import android.app.Activity" data/vpn/src/main` empty; `grep -rn "android\." core/domain/src/main/kotlin/.../permission/` empty; full `./gradlew test ktlintCheck detekt` (and `:data:vpn:assembleDebug`) green.
