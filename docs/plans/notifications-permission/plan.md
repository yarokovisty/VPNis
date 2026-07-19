---
type: plan
slug: notifications-permission
date: 2026-07-19
status: approved
spec: none
risk_areas: []
review_verdict: conditional
review_blockers: []
---

# Plan: POST_NOTIFICATIONS runtime request + denial-UX + notification visibility (#114)

## Context & Decision

Issue #114 (epic #126, notifications subsystem). On Android 13+ the app never requests the runtime
`POST_NOTIFICATIONS` permission, so the tunnel foreground-service notification is suppressed (system
app-importance = NONE) and the user loses the shade **Disconnect** action while the tunnel is live.
The manifest already declares the permission and the FGS notification (channel, `NOTIFICATION_ID`,
Disconnect action) is fully built by the #127/#128 foundation — the only gap is the **runtime
request** and the **denial UX**. The fix direction (request timing, three denial states, deep-link)
is already decided by the epic's multiexpert review (see #114 body); this plan is the HOW.

Foundation already merged (do not rebuild): `NotificationPermissionState` domain contract
(pull-semantics `isGranted: Flow<Boolean>` + `suspend refresh()`), `AndroidNotificationPermissionState`
(two-part gate), `FakeNotificationPermissionState`, `TunnelNotificationPresenter` (owns
`NOTIFICATION_ID`), `contentFor` mapper. The gate is bound in `vpnModule`/`fakeVpnModule` as a
`single` but **not yet consumed** — this plan wires the first consumer.

## Technical Approach

Cross-layer design validated by architecture-expert (all four forks). Dependency invariants held:
🔴 `:data:vpn` never pulls Activity/`ActivityResultLauncher`; `:core:domain` has no `android.*`.
🟡 `:feature:home` never depends on `:data:vpn` — communication only via `:core:domain` contracts.

**1. Request timing (fork 2) — derive effect from transition INTO `Connected`.**
`HomeViewModel` gains a third constructor param `NotificationPermissionState`. A new derivation block
in `init{}` mirrors the existing `PermissionRequired` pattern (`HomeViewModel.kt:95-103`):
```
controller.state
    .map { it is VpnConnectionState.Connected }
    .distinctUntilChangedBy { it }
    .onEach { isConnected ->
        if (isConnected && !requestedThisProcess) {
            val granted = permissionState.refresh()     // refresh() returns the freshly computed gate value (atomic)
            if (!granted) {
                requestedThisProcess = true             // VM-level guard: emit at most once per process
                _effects.send(HomeEffect.RequestNotificationPermission)
            }
        }
    }
    .launchIn(viewModelScope)
```
`distinctUntilChangedBy { isConnected }` collapses `Connected→Connected` self-transitions (traffic /
timer field churn) so the block runs once per *entry* into Connected — never on every tick.

Two review-driven refinements (perf#1, perf#2/ux#7):
- **`refresh(): Boolean`** — the contract's `refresh()` returns the freshly computed gate value, and
  the block gates on that return value directly. This is an atomic read (no separate collector, no
  interleaving race with the `ON_RESUME` refresh) — strictly better than `isGranted.first()`, which
  on a `Flow`-typed member is a subscribe/cancel, not a `.value` snapshot, and could observe an
  interleaved refresh.
- **`requestedThisProcess`** — a plain `var` confined to this single collector (no race) so the
  effect is emitted at most once per process. A denied-then-reconnect loop therefore never re-spams
  the effect channel; later re-surfacing is handled by the `ON_RESUME` refresh + the gate-driven
  banner, not by re-firing the dialog (matches "don't nag" / acceptance "не перезапускается").

API<33 is handled in the route (no-op launcher result), keeping the VM Android-version-agnostic.

**2. Gate exposure to the route (sibling flow, NOT in `HomeUiState`).**
`HomeViewModel` additionally exposes:
- `val notificationsGranted: StateFlow<Boolean>` — `permissionState.isGranted.stateIn(viewModelScope,
  WhileSubscribed(5000), initial = true)`. The **same** backing gate the `refresh(): Boolean` block
  reads, so there is exactly one source of truth for "granted" (perf#4). The route observes this to
  drive banner visibility off the **two-part** gate (app-level AND channel importance), so a
  mid-session app-level revocation **or** a channel silenced to `IMPORTANCE_NONE` correctly
  re-surfaces the banner (ux#3).
- `val notificationChannelId: String` = `permissionState.channelId` — so `HomeRoute` can pass it to the
  deep-link helper without touching the domain contract directly (red-team 10a).
- `fun refreshNotificationPermission()` — launches `permissionState.refresh()` in `viewModelScope`;
  called by the route on `ON_RESUME`.
These are **siblings** to `uiState`, not fields inside the `HomeUiState` sealed type — permission is a
route/presentation concern, not a projection of `ConnectionController.state` (architecture fork 1).
`controller.state` is a hot `MutableStateFlow.asStateFlow()` (verified), so the VM's three collectors
on it multicast — no cold-flow duplication (perf#6).

**3. Denial FSM (fork 1) — route-local, banner driven off the gate.**
`HomeRoute` owns a second launcher `rememberLauncherForActivityResult(RequestPermission())` for
`POST_NOTIFICATIONS`, plus route-local state:
- `hasRequestedBefore: Boolean` in `rememberSaveable` — survives recomposition/recreation.
- `requestInFlight: Boolean` (transient `remember`) — true only while the system dialog is up.
- `dismissedThisSession: Boolean` (transient `remember`) — reset to `false` on each fresh entry into
  `Connected` (keyed on a session token, e.g. `since`), so a dismiss is **session-scoped** (ux#1).

Flow:
- On `HomeEffect.RequestNotificationPermission`: if `Build.VERSION.SDK_INT < 33` → ignore. Else if
  `!hasRequestedBefore` → `requestInFlight=true`, launch dialog, `hasRequestedBefore=true`. Else
  (already asked earlier) → do nothing extra (banner is already gate-driven; **never** relaunch the
  dialog — Play guideline / acceptance "повторный Connect после отказа диалог НЕ перезапускается").
- Launcher result → `requestInFlight=false`, `viewModel.refreshNotificationPermission()` (updates the
  gate for both grant and deny; banner visibility follows the gate).
- `LifecycleEventEffect(ON_RESUME) { if (!requestInFlight) viewModel.refreshNotificationPermission() }`
  — gated so an in-flight dialog's pause/resume does not race the request (ux#2).
- **Banner visible** when `hasRequestedBefore && !notificationsGranted && !requestInFlight &&
  !dismissedThisSession`. Driving it off `notificationsGranted` (the two-part gate) — not the raw
  POST_NOTIFICATIONS result — means a later channel-silence / app-level revocation re-surfaces it, and
  a re-grant auto-hides it (ux#3). Suppressing while `requestInFlight` guarantees banner and system
  dialog are never shown together (ux#2).

The three denial states (never-asked / denied-once / permanently-denied) collapse into this model:
`shouldShowRequestPermissionRationale` is **not** needed as a separate branch — the gate-driven banner
(with a deep-link to settings) is the single denial surface regardless of once-vs-permanent. Known
limitation: a full process kill clears `rememberSaveable hasRequestedBefore`, so after a cold start the
app may re-launch the dialog once; the OS silently denies a permanently-denied permission, the result
fires, `refresh()` leaves the gate `false`, and the banner appears anyway — correct end state,
documented as an accepted limitation (no disk persistence — YAGNI).

**4. Rationale banner (Compose) — reuse `VPNisBanner`.**
`HomeScreen` (stateless) renders the existing `VPNisBanner` (`:design:uikit`). **Real API** (verified
`banner/VPNisBanner.kt:94`): `VPNisBanner(text: String, variant: VPNisBannerVariant, modifier, icon:
ImageVector?, primaryAction: VPNisBannerAction?, secondaryAction, onDismiss: (() -> Unit)?)` — a
**single `text`** block (no title/body split) + an action **data class** `VPNisBannerAction(label,
onClick)`, and **no `liveRegionMode` parameter**. Concretely:
- `text = stringResource(home_notification_banner_text)` (the single benefit sentence; the short
  "title" phrase is folded into this one string, since the banner has one text slot).
- `variant = VPNisBannerVariant.Info` (secondaryContainer — an enhancement, not an error; ux#5).
- `icon = ` an info icon; `primaryAction = VPNisBannerAction(stringResource(home_notification_banner_button),
  onOpenNotificationSettings)`; `onDismiss = onDismissNotificationBanner`.

Placement: inside `HomeConnectedContent` (`HomeScreen.kt:413`, `private`), after the `SessionTimer`
spacer and before the `ServerCard`, full-width — consistent with the Error banner. New params on the
**public** `HomeScreen` (module is `explicitApi()` — params MUST have defaults to avoid a source-
breaking change): `showNotificationBanner: Boolean = false`, `onOpenNotificationSettings: () -> Unit =
{}`, `onDismissNotificationBanner: () -> Unit = {}`; these thread down to `HomeConnectedContent`. The
screen stays stateless (no permission logic inside). Accessibility (ux#6): since `VPNisBanner` has no
`liveRegionMode`, wrap the banner call-site's `modifier` with `Modifier.semantics { liveRegion =
LiveRegionMode.Polite }` (don't interrupt the "Connected" announcement); reuse `VPNisBanner`'s built-in
dismiss/icon content descriptions (do **not** re-label — avoids double-announcement); the banner sits
**before** the `ServerCard` in traversal order. The shade **Disconnect** action (TalkBack-announced via
the notification `addAction`) is unchanged, and the in-app Disconnect (the `Connected`-state
`ConnectionButton`) stays available in every permission state (acceptance).

**5. Deep-link helper (fork 4 — local now).**
`internal object NotificationSettingsIntents` in `:feature:home`:
- `channelNotificationSettings(context, channelId): Intent` →
  `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS` + `EXTRA_APP_PACKAGE` + `EXTRA_CHANNEL_ID`.
- `appNotificationSettings(context): Intent` → `Settings.ACTION_APP_NOTIFICATION_SETTINGS` +
  `EXTRA_APP_PACKAGE` (fallback).
Both carry `FLAG_ACTIVITY_NEW_TASK`. The banner button uses the **channel-level** intent as primary
(we have `channelId`; it lands directly on the tunnel channel — the common "channel silenced" case,
ux#3). Acceptance ("app- or channel-level") is met either way. `channelId` comes from the new domain
`NotificationPermissionState.channelId`, passed as a parameter — the helper never imports `:data:vpn`.
Extraction to a shared module is #131's job (Out of Scope).

**6. Channel id exposure (fork 3).**
Add `val channelId: String` to `NotificationPermissionState` — it has a concrete consumer here (the
channel-level deep-link, §5). Reword the contract KDoc so the **"channel-agnostic" claim is scoped to
the permission-evaluation logic** (`isGranted` hides the two-part check); `channelId` is documented as
an **opaque deep-link target id, NOT a permission-logic signal** (guards against future creep like
`importance`/`isSilenced`; arch#1). `AndroidNotificationPermissionState.channelId` returns
`TunnelNotifications.CHANNEL_ID` (stays `internal` in `:data:vpn`). `:data:fake` does **not** depend on
`:data:vpn`, so `FakeNotificationPermissionState.channelId` **duplicates the literal** `"vpnis_tunnel"`
as its own `private const` (a shared reference would need a forbidden dependency or a domain constant
this plan rejects — red-team 10h). Also: `refresh(): Boolean` must **compute** the gate value, store it
in the backing StateFlow, and **return the computed value** — not read `_isGranted.value` back (avoids a
stale read under an interleaved refresh; red-team 6). The fake returns its `backing` value.

## Affected Modules & Files

| Path | Change | Note |
|---|---|---|
| `core/domain/.../permission/NotificationPermissionState.kt` | Modified | + `val channelId: String` (opaque deep-link target; KDoc boundary note); `refresh()` → `suspend fun refresh(): Boolean` (returns fresh gate value); reword channel-agnostic KDoc |
| `data/vpn/.../AndroidNotificationPermissionState.kt` | Modified | implement `channelId` → `TunnelNotifications.CHANNEL_ID` |
| `data/fake/.../FakeNotificationPermissionState.kt` | Modified | implement `channelId` → const string |
| `feature/home/.../HomeContract.kt` | Modified | + `HomeEffect.RequestNotificationPermission` |
| `feature/home/.../HomeViewModel.kt` | Modified | inject gate; effect derivation on `Connected` (`refresh():Boolean` + `requestedThisProcess`); `notificationsGranted` StateFlow; `notificationChannelId`; `refreshNotificationPermission()` |
| `feature/home/.../HomeModule.kt` | Modified | `viewModel { HomeViewModel(get(), get(), get()) }` |
| `feature/home/.../HomeRoute.kt` | Modified | `RequestPermission` launcher; `hasRequestedBefore`/`requestInFlight`/`dismissedThisSession`; gated `ON_RESUME` refresh; gate-driven banner wiring; pass 3 new args to `HomeScreen(...)` |
| `feature/home/.../HomeScreen.kt` | Modified | `VPNisBanner` (Info) in `HomeConnectedContent`; `showNotificationBanner=false`/`onOpenNotificationSettings={}`/`onDismissNotificationBanner={}` **defaulted** params (explicitApi); call-site `Modifier.semantics{liveRegion=Polite}` |
| `feature/home/.../NotificationSettingsIntents.kt` | New | `internal object` — channel/app deep-link Intent factory |
| `feature/home/src/main/res/values/strings.xml` | Modified | EN banner strings (`home_notification_banner_text`, `home_notification_banner_button`) |
| `feature/home/src/main/res/values-ru/strings.xml` | Modified | RU banner strings (same keys) |
| `feature/home/.../HomeScreenPreviews*.kt` | Modified | **required** — both preview files updated (new defaulted params compile; add one banner-visible preview) |
| `feature/home/src/test/.../HomeViewModelTest.kt` | Modified | + local `FakeNotificationPermissionState` test double (`:data:fake` not on home test classpath); 3-arg ctor; new effect cases |
| `feature/home/src/test/.../HomeModuleCheckTest.kt` | New | Koin `checkModules`/`verify` over `homeModule` + fake bindings — catches the 3rd `get()` |
| `feature/home/src/test/.../NotificationSettingsIntentsTest.kt` | New | Robolectric — intent action/extras/flags |

## Decisions Made

| Decision | Rationale | Alternatives rejected |
|---|---|---|
| Request after transition INTO `Connected`, gate via `refresh(): Boolean` return value | Value moment; atomic read at the transition, no second collector / interleaving race (perf#1) | `isGranted.first()` — subscribe/cancel on a `Flow`-typed member, races `ON_RESUME` refresh; `combine(state, gate)` — cartesian re-emission |
| `requestedThisProcess` VM guard | Effect emitted ≤1×/process; denied-then-reconnect never re-spams the channel (perf#2/ux#7) | rely solely on route FSM — VM would emit on every reconnect for process lifetime |
| `distinctUntilChangedBy { isConnected }` | Collapses `Connected→Connected` traffic/timer churn → block runs once per entry | raw `onEach` — runs on every tick |
| Denial handling route-local; banner driven off the gate | `RequestPermission` launcher / session flags are Activity/route-only; must not leak into VM/domain | FSM in VM — pulls Activity into ViewModel (🔴 violation) |
| Rationale = route-local state driven by `notificationsGranted` (not Effect, not `HomeUiState`) | Persistent banner must survive recomposition/rotation and re-surface on later revocation; Effect is one-shot; `HomeUiState` is a domain projection | `HomeEffect` — disappears on rotation; `HomeUiState` field — leaks permission concern into VM state |
| Banner visibility off two-part `isGranted` gate | Handles mid-session app-level revocation AND channel silenced to NONE; auto-hides on re-grant (ux#3) | keying off raw POST_NOTIFICATIONS result — misses channel-silence case |
| Dismiss is session-scoped (reset on fresh `Connected` entry) | Recoverable path without per-resume nagging (ux#1) | permanent dismiss — user loses only in-app path; per-resume — nagging |
| `channelId: String` added to domain contract | Concrete consumer (channel-level deep-link); keeps `CHANNEL_ID` `internal`; KDoc scopes channel-agnostic to `isGranted` logic (arch#1) | resource dup (2nd source of truth), separate contract (over-engineering), helper in `:app` (default flavor has no `:data:vpn`) |
| Deep-link helper local to `:feature:home` now | YAGNI — one consumer today; module for one function is premature; uikit pollution avoided | new `:core:ui` (build overhead now), `:design:uikit` (semantically UI-components only) |
| Channel-level deep-link as banner primary | We have `channelId`; lands on the tunnel channel (the common silenced case); acceptance met either way | app-level primary — extra hop for the channel-silenced case |

## Risks & Mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| Duplicate dialog on reconnect (`Connected→Disconnected→Connected`) | major | `requestedThisProcess` VM guard + gate `!granted` before send + route `hasRequestedBefore` suppresses relaunch |
| Banner + system dialog shown together | major | Banner suppressed while `requestInFlight`; `ON_RESUME` refresh gated on `!requestInFlight` (ux#2) |
| Banner never re-surfaces on mid-session channel-silence / revocation | major | Banner driven off two-part `notificationsGranted`; `ON_RESUME` refresh re-reads gate (ux#3) |
| Dismissed banner gone forever OR nags every resume | major | Session-scoped dismiss — hidden until next fresh `Connected` entry (ux#1) |
| Process kill loses `rememberSaveable` → can't tell first-ask vs permanent-deny | minor | Documented accepted limitation; worst case re-launch → silent OS deny → gate stays `false` → banner (correct end state) |
| Pull-semantics loop left open (banner never auto-hides) | major | `LifecycleEventEffect(ON_RESUME) { refreshNotificationPermission() }` + `notificationsGranted` collection closes it |
| Effect lost if `Connected` precedes UI subscription | minor | `_effects` is `Channel.BUFFERED` (64) — buffers until collector attaches |
| Layer-direction regression | major | Verified by `checkModules` test + code review; `channelId` opaque; helper takes `channelId` param, no `:data:vpn` import |

## Verification & Sources

| Source of truth | Type | Status | Sufficient for verification? |
|---|---|---|---|
| Issue #114 "Приёмка" (5 acceptance bullets) | requirements | present | yes — each bullet is a concrete observable check (dialog after Connected, no relaunch after refusal, permanent-deny→settings, in-app Disconnect always, TalkBack action) |
| Epic #126 DoD (notification visibility on A13/14/15) | requirements | present | yes — device-level DoD, exercised by #133 QA run |
| Foundation code (#127/#128) as integration baseline | before-state | present | yes — presenter/mapper/gate already unit-tested; this plan only adds a consumer |

**Testing strategy (pyramid levels):** L0 build always + L1 static (Detekt/lint, `checkModules` layer
guard) + L2 unit (VM effect derivation: fires once on Connected when `!granted`; suppressed on
self-transition, when granted, and on reconnect via `requestedThisProcess`; `NotificationSettingsIntents`
intent assembly via Robolectric) + L5
manual (device grant/deny/permanent-deny + shade Disconnect + TalkBack on A13/14/15 — owned by #133).
L3 Compose UI test for the banner is optional here (banner is simple, static copy). L5 is mandatory:
this is an infra-layer (permission/DI) change whose core acceptance is only observable on a real
device shade — inline unit cannot prove notification visibility.

## Out of Scope

- Extracting `NotificationSettingsIntents` into a shared module (`:core:ui`) — deferred to #131 (second
  consumer). Helper is designed self-contained so extraction is a mechanical `git mv` + import update.
- Comprehensive UiAutomator/device QA on A13/14/15 — task #133.
- Full unit/mapper/gate test matrix — task #132 (this plan adds only the VM-effect unit inline).
- Error/reconnect alert channel (`vpnis_alerts`) — task #129.
- Traffic in notification — task #130 (blocked by #69).
- Pre-permission priming / rationale-before-dialog screen — explicitly ruled out by epic decision.

## Open Questions

- [non-blocking] Should the banner button prefer channel-level deep-link when app-level is enabled but
  the channel is silenced? Deferred: contract omits importance signal by design; app-level satisfies
  acceptance. Revisit if #131 needs finer targeting.
