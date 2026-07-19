---
type: plan
slug: notifications-foundation
date: 2026-07-19
status: approved
spec: none
risk_areas: [perf-critical]
review_verdict: pass
review_blockers: []
---

# Plan: Notifications foundation — state-driven content + `NotificationPermissionState` gate (#127)

## Context & Decision
Task #127 is the **foundation** of the notifications subsystem epic #126 (triggered by bug #114:
on Android 13+ the persistent FGS notification is suppressed, so the "Disconnect" action is
unreachable). This plan does NOT request `POST_NOTIFICATIONS` and does NOT touch any Activity /
`ActivityResultLauncher` — those belong to #114. It seeds two decided pieces of infrastructure the
rest of the epic (#114, #128, #129, #130, #131, #132) builds on:

1. a read-only `NotificationPermissionState` gate in `:core:domain` (Android impl in `:data:vpn`,
   fake in `:data:fake`); and
2. a **state-driven notification pipeline**: a pure `VpnConnectionState → NotificationContent`
   mapper plus a `TunnelNotificationPresenter` that solely owns the notification slot.

The design was validated by an architecture-expert pass (see Decisions Made); this plan is the HOW.

## Technical Approach

### Part 1 — `NotificationPermissionState` gate

**Domain contract** (`:core:domain`, pure JVM, no `android.*`):
```kotlin
package org.yarokovisty.vpnis.core.domain.permission

public interface NotificationPermissionState {
    /** Backed by a StateFlow in impls; true when the OS will display the app's status notifications. */
    public val isGranted: Flow<Boolean>
    /** Re-reads the OS state (a PULL, not a push). Called from the route layer on ON_RESUME. */
    public suspend fun refresh()
}
```
The contract is **channel-agnostic**: its KDoc says "true when the OS will display the app's
foreground/status notifications" — it MUST NOT name `"vpnis_tunnel"` or mention channel importance.
That two-part check is an implementation detail of `:data:vpn`.

**Android impl** `AndroidNotificationPermissionState` (`:data:vpn`, injected `androidContext()`):
`refresh()` re-reads two different managers (the compat class does NOT expose `getNotificationChannel`):
`NotificationManagerCompat.from(context).areNotificationsEnabled()` **AND**
`context.getSystemService(NotificationManager::class.java).getNotificationChannel(TunnelNotifications.CHANNEL_ID)?.importance != IMPORTANCE_NONE`
(channel can be disabled manually after an app-level grant — I6; the framework `NotificationManager` is
the same one `TunnelNotifications.createChannel` already uses). Result is pushed into a private
`MutableStateFlow<Boolean>` exposed as `isGranted`. Initial value computed at construction (so the flow
always has a seed). The read is cheap/main-safe; hop to `Dispatchers.Default` inside `refresh()`
defensively. References the `internal const CHANNEL_ID` in the same module — no leak into domain. Bound
in `vpnModule`.

**Fake impl** `FakeNotificationPermissionState` (`:data:fake`): must **honestly model the pull
semantics** — a naïve `MutableStateFlow` where `setGranted()` writes the flow directly makes `refresh()`
a no-op (StateFlow already emitted on the `setGranted` write) and the acceptance test trivially green
regardless of `refresh()`. Instead: a plain backing field `private var backing = true` written by
`setGranted(Boolean)` (does NOT emit), and `isGranted` backed by a `private val _isGranted =
MutableStateFlow(backing)` that is updated **only** by `refresh()` (`_isGranted.value = backing`). So a
test that calls `setGranted(false)` then `refresh()` observes `false` *because of* `refresh()` — exactly
mirroring the Android pull where the OS state changes out-of-app and only the ON_RESUME `refresh()`
surfaces it. Default `backing = true`. Bound in `fakeVpnModule`. This binding is **mandatory even though
#127 adds no consumer yet** — it keeps the fake/real graphs symmetric so the swap invariant and
`checkModules` both hold, and unblocks #114/#131 wiring without re-touching the fake module.

### Part 2 — state-driven notification pipeline

**Pure mapper** (`TunnelNotifications`, `:data:vpn`, stays `internal`): change the seam from
`contentFor(NotificationContent): Pair<String,String>` to `contentFor(VpnConnectionState):
NotificationContent`. `NotificationContent` becomes a data-carrying sealed hierarchy (NO pre-formatted
strings):
```kotlin
internal sealed interface NotificationContent {
    data object Inactive : NotificationContent            // Loading/Disconnected/PermissionRequired/Error → no live tunnel
    data class Connecting(val serverName: String) : NotificationContent   // Server.name is non-null (Server.kt:10)
    data class Connected(val serverName: String, val since: Instant, val traffic: TrafficStats?) : NotificationContent
}
```
`contentFor` is a **total** `when` over the sealed `VpnConnectionState` (exhaustiveness catches new
states at compile time). **`build()` is refactored in the same edit as `contentFor`** (T-1): its body
currently does `val (title,text) = contentFor(content)` on a `Pair` — after the refactor it renders the
`NotificationContent` subtype to title/text and its default parameter becomes `NotificationContent.Inactive`
(there is no more `Default`). `Inactive` renders to the **current static copy** ("VPNis" / "Tunnel
active") so the service's initial `startForeground(build(this))` post is byte-for-byte unchanged;
`Connected`/`Connecting` render live-ish copy (server name; #128 adds the timer). Localization stays in
`build()` via `getString` (Context available there); `contentFor` stays Context-free and unit-testable
without Robolectric. Keep the subtypes minimal but data-carrying so #128/#130 extend without a signature
change.

**Presenter** `TunnelNotificationPresenter` (`:data:vpn`, Koin `single`, injected `androidContext()`
+ `ConnectionController`): solely owns `NOTIFICATION_ID = 1001`. Because it is a **process-lifetime
`single`**, it must be robust to repeated service start→kill→start cycles where a prior teardown may
not have run cleanly (low-memory kill). Lifecycle is driven by the service (architecture-expert
**option (i)** — no self-owned process-lifetime scope, which would leak between service starts), with
three defences layered so the design is correct even when coroutine cancellation ordering is
adversarial:
```kotlin
// mapDispatcher defaults to Dispatchers.Default; injected so tests can pass a StandardTestDispatcher
class TunnelNotificationPresenter(
    private val connectionController: ConnectionController,
    appContext: Context,
    private val mapDispatcher: CoroutineDispatcher = Dispatchers.Default,
) { /* notificationManager = appContext.getSystemService(NotificationManager::class.java) — a field, fetched once */

private var job: Job? = null
private val active = AtomicBoolean(false)     // gate checked immediately before notify()

/** Idempotent & re-entrant: cancels any stale job from a previous (possibly un-stopped) service. */
fun start(scope: CoroutineScope): Job {       // scope == VpnTunnelService.serviceScope (IO)
    job?.cancel()                             // defends against a stale collector after a dirty onDestroy
    active.set(true)
    val j = connectionController.state
        .map { contentFor(it) }               // pure, no Context
        .filter { it !is NotificationContent.Inactive }   // never post for non-active states
        .distinctUntilChanged()               // dedup rendered content (see note on traffic below)
        .flowOn(mapDispatcher)                // map/filter/distinct run on Default (test: StandardTestDispatcher) …
        .onEach { content ->                  // … onEach runs on the collector's dispatcher (serviceScope = IO)
            if (active.get()) {               // final guard: a late in-flight emission after stop() is dropped
                notificationManager.notify(NOTIFICATION_ID, TunnelNotifications.build(appContext, content))
            }
        }
        .launchIn(scope)
    job = j
    return j                                  // returned so tests can assert liveness (no-leak acceptance)
}

/** Idempotent. Flips the gate BEFORE cancelling so no emission can win the race. */
fun stop() { active.set(false); job?.cancel(); job = null }
```
Threading is explicit: `map/filter/distinctUntilChanged` run on `Dispatchers.Default` via `flowOn`;
`onEach { notify() }` runs on the collector's dispatcher, which is `serviceScope` = `Dispatchers.IO`
(VpnTunnelService.kt:186) — off Main. `start()` requires a **non-Main** scope (documented precondition;
a Main-dispatched scope would move `notify()` to Main). Implementation notes for the presenter:
`notificationManager` is derived once from `appContext.getSystemService(...)` (a field, not re-fetched
per emission); `active` is an `AtomicBoolean` so its happens-before edge crosses the Default→IO hop;
`job` is a plain `var` intentionally NOT `@Volatile` — carry a KDoc **invariant** that `start()`/`stop()`
are called only from `VpnTunnelService`'s (main-thread-serialized) lifecycle callbacks, never
concurrently off-thread. The final `cancel(NOTIFICATION_ID)` sweep makes the teardown **race-tolerant**
(not mathematically race-free): the only synchronous teardown emission is caught by the `filter`; the
sweep self-heals the sub-frame `flowOn` hand-off window on a multi-threaded IO pool.

> **`distinctUntilChanged` scope (honest trace):** it collapses only *non-traffic* churn. In #127
> `Connected.since` is set once and `traffic` is `null`, so churn is minimal. Once #130 emits
> `Connected` with changing `traffic`, each refresh is a distinct `NotificationContent` and passes
> through — `distinctUntilChanged` **cannot** satisfy the epic's ≤1 `notify()`/sec DoD for the traffic
> case. #130 owns adding a rate-limit (`sample(1.seconds)` / conflation) on the traffic-bearing stream.
> #127 seeds the dedup only for the state-change case.

**Service changes** (`VpnTunnelService`): the service keeps ONLY the initial `startForeground`
(with `TunnelNotifications.build(this)` — synchronous, service Context is fine) and its role as the
state writer via `TunnelStateSink`. It gains an injected `TunnelNotificationPresenter`.
- **Exact `start()` placement:** call `presenter.start(serviceScope)` at the **end of the success
  path** — right after `stateSink.onTunnelEstablished()` (VpnTunnelService.kt:515), NOT immediately
  after `startForeground`. Deliberate: the two early-abort paths (`xrayCore.start()` false at ~453;
  `establish()` null at ~473) call `stopForeground(REMOVE)` and return; a presenter started right after
  `startForeground` (~425) would survive those aborts and re-post a zombie. Starting only on the success
  path means no abort path can leave the presenter running.
- **Remove** the `updateNotification(content = NotificationContent.Default)` call (line ~516) and the
  whole `updateNotification(...)` method (lines ~627–632) — the presenter is now the only content source.
- **Zombie-notification defence (layered, primary = filter):** the **primary** guard is the presenter's
  active-state `filter`, because `ConnectionControllerImpl.disconnect()` synchronously emits
  `Disconnected` at the *start* of teardown (ConnectionControllerImpl.kt:~190), well before
  `finishTeardown` — an ordering-only fix cannot catch it; the `filter` maps it to `Inactive` and drops
  it. On top of that: (1) in `finishTeardown`, call `presenter.stop()` **before**
  `stateSink.onTunnelStopped()`; (2) the `active` AtomicBoolean gate drops any emission already past the
  `filter` and suspended in the `flowOn` hand-off when `stop()` fires; (3) as a final idempotent sweep,
  call `notificationManager.cancel(NOTIFICATION_ID)` immediately after
  `stopForeground(STOP_FOREGROUND_REMOVE)` in `finishTeardown` (cheap, always correct — removes any
  notification a lost race may have posted).
- **`onDestroy` must call `presenter.stop()`** (the low-memory-kill path that can fire without
  `finishTeardown`). Combined with the defensive `job?.cancel()` in `start()`, this releases the
  collector on every teardown path and prevents the singleton retaining a stale `job` / `serviceScope`
  / `Context`. `stop()` is idempotent, so calling it from both `finishTeardown` and `onDestroy` is safe.

**Koin wiring** (`vpnModule`): add `single { TunnelNotificationPresenter(get(), androidContext()) }`
(the `mapDispatcher` default is used in production) and
`single<NotificationPermissionState> { AndroidNotificationPermissionState(androidContext()) }`.
`VpnTunnelService` pulls the presenter with the **`org.koin.android.ext.android.inject` extension**
(`private val presenter: TunnelNotificationPresenter by inject()`) — the same `koin-android` Service
extension already used for `stateSink` and `xrayCore` (VpnTunnelService.kt:23); the service is NOT a
`KoinComponent`, it relies on the Android `inject()` extension. `fakeVpnModule` gains
`single<NotificationPermissionState> { FakeNotificationPermissionState() }`.

### Verification seam — `checkModules`
Add a Koin `verify()`/`checkModules()` test per graph. This needs `koin-test`, which is **not**
currently on either module's test classpath (both only pull `junit` + `kotlinx-coroutines-test`, and
`:data:fake` also `turbine` — verified in `data/vpn/build.gradle.kts:85-86` and
`data/fake/build.gradle.kts:9-11`; the `vpnis.koin` convention adds only `koin-bom`+`koin-core`).
`koin-test`, `koin-test-junit4` and `robolectric` are **already in the version catalog**
(`gradle/libs.versions.toml:45,46,59`) and `robolectric` is already proven in-repo (`:feature:home`
unit tests use it), so wiring them here is adding *cataloged, in-repo-vetted test infra* to two more
modules — not introducing a new external dependency. Because the checks run on **JUnit 4**, the runner
support lives in **`koin-test-junit4`** (the bare `koin-test` JVM artifact does not ship it), so T-8a
adds `testImplementation(libs.koin.test.junit4)` (which brings `koin-test` transitively) to both modules
and `testImplementation(libs.robolectric)` to `:data:vpn`.
- **Fake graph** (`:data:fake`, pure JVM, no `androidContext()`): `koin-test` `checkModules()` alone.
- **Real graph** (`:data:vpn`, needs a Context + channel APIs): run the check under **Robolectric**,
  providing the Robolectric application Context to the Koin check (`checkModules { withInstance<Context>(ctx) }`
  or `koinApplication { androidContext(ctx); modules(vpnModule) }.checkModules()`). The exact API form is
  a non-blocking open question resolved in T-8 — whichever runs green satisfies the acceptance.
This proves both graphs resolve every declared definition without error.

## Affected Modules & Files
| Path | Change | Note |
|---|---|---|
| `core/domain/src/main/kotlin/.../core/domain/permission/NotificationPermissionState.kt` | New | Channel-agnostic gate contract; `Flow<Boolean>` + `suspend refresh()`; no `android.*`. |
| `data/vpn/src/main/kotlin/.../data/vpn/AndroidNotificationPermissionState.kt` | New | `NotificationManagerCompat.areNotificationsEnabled()` && channel importance != NONE; MutableStateFlow. |
| `data/vpn/src/main/kotlin/.../data/vpn/TunnelNotificationPresenter.kt` | New | Koin `single`, owns `NOTIFICATION_ID`; `start(scope)`/`stop()`; map→filter→distinct→notify. |
| `data/vpn/src/main/kotlin/.../data/vpn/TunnelNotifications.kt` | Modified | `contentFor(VpnConnectionState): NotificationContent` (total `when`); data-carrying `NotificationContent`; `build()` renders + localizes. |
| `data/vpn/src/main/kotlin/.../data/vpn/VpnTunnelService.kt` | Modified | Inject presenter; `start` after `startForeground`; remove `updateNotification`; `presenter.stop()` before `onTunnelStopped()`. |
| `data/vpn/src/main/kotlin/.../data/vpn/VpnModule.kt` | Modified | Bind `TunnelNotificationPresenter` + `NotificationPermissionState`. |
| `data/fake/src/main/kotlin/.../data/fake/FakeNotificationPermissionState.kt` | New | Default `granted=true`; mutable setter; `refresh()` re-emits. |
| `data/fake/src/main/kotlin/.../data/fake/FakeVpnModule.kt` | Modified | Bind `NotificationPermissionState`. |
| `data/vpn/src/test/kotlin/.../data/vpn/VpnModuleCheckTest.kt` | New | Robolectric `checkModules` for real graph. |
| `data/fake/src/test/kotlin/.../data/fake/FakeVpnModuleCheckTest.kt` | New | `checkModules` for fake graph. |
| `data/vpn/src/test/kotlin/.../data/vpn/TunnelNotificationsTest.kt` | Modified | Update to new `contentFor(state)` signature; assert data-carrying mapping, no Context. |
| `data/vpn/src/test/kotlin/.../data/vpn/TunnelNotificationPresenterTest.kt` | New | No-leak (stop cancels collection), active-state filter, no notify after stop, distinct dedup. |
| `data/fake/src/test/kotlin/.../data/fake/FakeNotificationPermissionStateTest.kt` | New | Default true; setGranted+refresh reflects new value. |
| `data/vpn/src/test/kotlin/.../data/vpn/AndroidNotificationPermissionStateTest.kt` | New | Robolectric: two-part gate AND (app-level && channel importance != NONE). |
| `data/vpn/build.gradle.kts` | Modified | Add `testImplementation(libs.koin.test.junit4)` + `testImplementation(libs.robolectric)` (both cataloged, robolectric in-repo-proven). |
| `data/fake/build.gradle.kts` | Modified | Add `testImplementation(libs.koin.test.junit4)` (cataloged). |

## Decisions Made
| Decision | Rationale | Alternatives rejected |
|---|---|---|
| Contract = `val isGranted: Flow<Boolean>` + `suspend fun refresh()` | Matches the app's Flow-of-state model (HomeVM `combine`s Flows); `refresh()` is honest about the pull nature of `areNotificationsEnabled()` (no OS push). Mirrors `ConnectionController.state` (Flow backed by StateFlow). | `suspend fun isEnabled(): Boolean` only → forces the ViewModel to reinvent the StateFlow in the wrong layer. `callbackFlow` on `AppOpsManager` → heavier, still needs a seed; option A (refresh from ON_RESUME) is the epic's chosen approach. |
| Domain contract is channel-agnostic | `"vpnis_tunnel"` / channel-importance is an Android-notification-subsystem detail; leaking it into `:core:domain` makes the contract's meaning wrong the day a 2nd channel (alerts, #129) appears. | Passing channel id / importance through the domain type → leaky abstraction, blocked in review. |
| Presenter scope = **option (i)** `start(serviceScope)`/`stop()` | Coroutine lifetime is bounded by the FGS lifetime for free; `serviceScope.cancel()` in `onDestroy` guarantees teardown; testable with a `TestScope`. | (ii) presenter owns its own SupervisorJob → the exact "leaks a coroutine between service starts" hazard for a process-lifetime `single`. (iii) content filter alone → doesn't solve lifecycle/leak, kept only as complementary safety. |
| Zombie defence is **layered**, primary = active-state `filter` | `ConnectionControllerImpl.disconnect()` emits `Disconnected` synchronously at teardown *start* (~line 190) — an ordering-only fix (`stop()` before `onTunnelStopped()`) can't catch it. So the `filter` (drops `Inactive`) is primary; `stop()`-ordering + `active` AtomicBoolean gate + a final `cancel(NOTIFICATION_ID)` sweep are belt-and-suspenders for the narrow `flowOn` hand-off race. | Ordering alone → misses the early `Disconnected`. Filter alone → doesn't drop a stale *active* emission past the filter (hence the AtomicBoolean + final sweep). |
| `start()` placed at end of success path (after `onTunnelEstablished`, ~515), not after `startForeground` | The two abort paths (`xrayCore.start()` false ~453, `establish()` null ~473) call `stopForeground(REMOVE)`; a presenter started at ~425 would survive them and re-post a zombie. | Start right after `startForeground` → abort paths leak a live collector. |
| `start()` defensively `job?.cancel()`s + `onDestroy` calls `stop()` | A process-lifetime `single` can retain a stale `job`/`serviceScope`/`Context` if a low-memory-kill `onDestroy` skips `finishTeardown`; defensive cancel + `onDestroy` stop makes start re-entrant and leak-free across start→kill→start. | Rely on `serviceScope.cancel()` alone → singleton still holds a stale non-null `job` and a dead scope reference; next `start()` could double-collect. |
| Inject `androidContext()` (application Context) into presenter | Consistent with `AndroidTunnelLauncher`/`AndroidVpnConsentChecker`/`XrayCoreProvider`; app Context outlives any service instance; no Activity/leak. | Threading `Context` through `notify()` per call from the service → couples presenter to service instance lifetime, defeats the `single`. |
| `contentFor(VpnConnectionState): NotificationContent`, total `when`, data-carrying subtypes, localize in `build()` | Keeps mapper pure/Context-free (unit-testable, matches existing KDoc intent); total `when` gives exhaustiveness when states are added; data (not strings) lets #128/#130 extend without signature churn. | `NotificationContent → Pair<String,String>` (current) → mixes localization into the mapper. Formatted duration string in the model → re-leaks localization into the mapper. |
| `Connecting.serverName = state.server.name` (non-null) | `VpnConnectionState.Connecting(server: Server)` carries a non-null `Server` (VpnConnectionState.kt:60), so the value is always available — no reason for a nullable. | Leaving it nullable / "decide at impl" → arbitrary null where data is determinate. |
| Add `setOnlyAlertOnce(true)` in `build()` now | #127 is the first task where the presenter re-`notify()`s repeatedly; the epic DoD names `setOnlyAlertOnce` alongside `IMPORTANCE_LOW` for "0 heads-up/sound". It must land where re-notify begins, not be implicitly claimed. | Defer to #128 → #127 already re-notifies on every state change; a missing flag could surface heads-up before #128. |
| Wire cataloged `koin-test` (+`robolectric` for `:data:vpn`) test infra | `checkModules` lives in `koin-test`; the real graph needs a Context (Robolectric). Both are already in the version catalog and `robolectric` is proven in `:feature:home` tests — this is test-infra wiring of vetted libs, not a new external dependency (respects the no-new-deps rule). | Mock `Context` via mockk to avoid Robolectric → fragile constructor stubbing of `getSystemService`/`NotificationManagerCompat`; Robolectric is the cleaner, already-vetted path. |
| Bind fake gate now, even with no #127 consumer | Keeps fake/real graphs symmetric (swap invariant), makes `checkModules` meaningful, unblocks #114/#131 without re-touching the fake module. | Defer fake binding to #114 → asymmetric graph, and any Home VM dependency added later would crash the showcase build at Koin resolution. |

## Risks & Mitigations
| Risk | Severity | Mitigation |
|---|---|---|
| Zombie notification: `notify()` after `stopForeground(REMOVE)` re-creates a dismissable notification | critical | Layered defence (primary = active-state `filter`, catches the synchronous `Disconnected` at teardown start) + `stop()` before `onTunnelStopped()` + `active` AtomicBoolean gate before `notify()` + final `notificationManager.cancel(NOTIFICATION_ID)` sweep after `stopForeground`. Covered by `TunnelNotificationPresenterTest` ("no notify after stop", "drops Disconnected/active-past-filter"). |
| Coroutine leak / stale collector across start→kill→start (process-lifetime `single`) | major | `start()` defensively `job?.cancel()`s any stale job; `onDestroy` calls `stop()`; `serviceScope.cancel()` remains the outer guarantee. Test: `start(scopeA); start(scopeB)` without `stop()` asserts scopeA's collector no longer posts (no double-collection / orphan). |
| `checkModules` needs test infra not on these modules' classpath | major | T-8 adds cataloged `koin-test` (both) + `robolectric` (`:data:vpn`, already proven in `:feature:home`); real graph runs under Robolectric with an app Context. Not a new external dep — no approval gate needed. Fake graph stays pure JVM. |
| Channel-id leak into `:core:domain` | major | Contract + KDoc channel-agnostic; grep test / review guard: no `vpnis_tunnel`, no `importance` in `:core:domain`. |
| `notify()` storm | major | `distinctUntilChanged()` collapses **non-traffic** state-change churn (the #127 case: `since` set once, `traffic` null). It **cannot** collapse live-traffic churn — each changed `traffic` is a distinct data class; **#130 owns** the rate-limit (`sample(1.s)`/conflate) needed to hold the epic's ≤1 `notify()`/sec DoD once traffic is live. #127 seeds only the state-change dedup — no overclaim. |
| Per-emission `PendingIntent`/`Notification` allocation on the hot path (once #130 live) | minor | Static content in #127 (rare emissions), so acceptable now. Note for #130: cache the disconnect `PendingIntent` (never changes) and reuse a single `NotificationCompat.Builder`. |
| Removing `updateNotification` breaks the initial notification | minor | Service keeps `startForeground(build(this))` for the initial post; presenter takes over live updates. Verified by build + existing FGS start path unchanged. |
| `:feature:home` accidentally pulled toward `:data:vpn` | minor | #127 adds no Home consumer; guard test/grep: `feature/home/build.gradle.kts` has no `project(":data:vpn")`. |

## Verification & Sources
The finished change is verified against the falsifiable acceptance enumerated in **issue #127** and
the epic-wide guardrails in **issue #126** (no external spec doc exists; the GitHub issues are the
source of truth).

| Source of truth | Type | Status | Sufficient for verification? |
|---|---|---|---|
| GitHub issue #127 (acceptance bullets) | requirements | present | yes — each bullet maps to a concrete task check below (checkModules, fake-gate refresh test, `internal` visibility grep, no-`android.*` grep, presenter no-leak test, no `project(":data:vpn")` in `:feature:home`). |
| GitHub issue #126 (epic DoD / dependency-direction guardrails) | requirements | present | yes — the RED/YELLOW guards (no Activity/Launcher in `:data:vpn`; `:feature:home` doesn't import `:data:vpn` internals; single `NOTIFICATION_ID`; IMPORTANCE_LOW) are grep/structure-checkable. |
| Current `:data:vpn` notification code (`TunnelNotifications`, `VpnTunnelService`) | before-state baseline | present | yes — behavior-preserving refactor of the FGS notification: baseline is the current build + `TunnelNotificationsTest`; new tests assert the seam moved without changing the visible notification. |

**Testing strategy (pyramid levels):** L0 build always + L1 static (ktlint/detekt + no-`android.*`/
no-`vpnis_tunnel` in domain grep) + L2 unit (mapper without Context; fake-gate default+refresh;
Android gate two-part AND under Robolectric; presenter no-leak/no-notify-after-stop/distinct/
start→kill→start; `checkModules` for both graphs under JUnit4/Robolectric/Turbine/coroutines-test/
koin-test). L5 manual is **out of scope for #127** — on-device A13/14/15 verification is the dedicated
QA task #133; #127 changes no user-visible notification content, so the L2 seam tests + `checkModules`
are the appropriate gate. This is a DI/infra-layer change, but the L5 exception is tracked (deferred to
#133, per epic #126). Gate/mapper coverage is intentionally *seeded* here and *expanded* by the
dedicated test task **#132** — #127 owns the foundational tests its own acceptance names; #132 owns the
exhaustive matrix incl. the ViewModel effect (which #127 does not add).

## Out of Scope
- Requesting `POST_NOTIFICATIONS` runtime permission, permanently-denied UX, rationale copy, the
  "Disconnect" action visibility fix → **#114**.
- Consuming `NotificationPermissionState` in `HomeViewModel`/`HomeRoute` (the `combine` fold + the
  `DisposableEffect` ON_RESUME `refresh()` call) → **#114 / #131** (this task only seeds + fake-tests
  the contract; it deliberately adds no Home consumer to keep the RED/YELLOW guards trivially true).
- Live server-name + session timer rendering in `build()` → **#128**.
- Error/reconnect alert channel (`vpnis_alerts`) → **#129**.
- Traffic in the notification → **#130** (blocked by #69).
- The shared deep-link route helper into system settings → **#114**, reused by **#131**.
- On-device A13/14/15 / UiAutomator QA → **#133**.

## Open Questions
- [non-blocking] `checkModules` for the real `vpnModule`: confirm whether `checkKoinModules` +
  Robolectric application Context resolves `androidContext()`-based definitions cleanly, or whether the
  check must inject the Context via `checkModules { withInstance<Context>(...) }`. Either satisfies the
  acceptance; pick the one that runs green. Resolve during T-8. (No blocker: both forms use only the
  cataloged `koin-test` + `robolectric` T-8 wires in.)
