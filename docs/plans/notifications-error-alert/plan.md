---
type: plan
slug: notifications-error-alert
date: 2026-07-20
status: approved
spec: none
risk_areas: []
review_verdict: pass
review_blockers: []
---

# Plan: Error notification alert (issue #129)

## Context & Decision

Part of the notifications subsystem epic **#126**. Decided by issue **#129** (body + review
cycle-1 comment), epic #126 decisions, and three owner decisions taken during multiexpert-review
cycle 1 (see Decisions D1–D3). Foundation task **#127** (state-driven content +
`TunnelNotificationPresenter`) is merged (#134); **#128** (live timer) merged (#135). This plan is
the HOW for surfacing an **unexpected drop of an active tunnel** as a dedicated, dismissible
heads-up alert — it does not re-argue the epic's scope.

Today `TunnelNotifications.contentFor(Error)` collapses to `Inactive`, which the presenter filters
out, so a tunnel error is **invisible** in the notification layer. This plan makes an unexpected
drop of an *active* tunnel produce a single dismissible heads-up alert on a dedicated channel,
without disturbing the ongoing foreground-service (FGS) notification.

> **Revised after cycle-1 review.** The interrupting alert now fires **only** for a native
> mid-session drop (`Connected → Error` from the hev loop), **not** for `onRevoke`/`Revoked`
> (owner decision D1). This removes the state-machine change entirely and eliminates the one
> non-deterministic delivery path the reviewers flagged (see Decisions & Risks).

## Technical Approach

### Sole alert trigger — `Connected → Error` from the hev native loop

`TunnelNotificationPresenter` is started only at the **end of the success path**, after
`stateSink.onTunnelEstablished()` (`VpnTunnelService.kt:536`), and stopped in `finishTeardown`
(`VpnTunnelService.kt:610`) / `onDestroy` (`VpnTunnelService.kt:331`). So the running presenter
observes only transitions of an *already-established* tunnel. The only such transition that emits
an error today is the hev native loop throwing mid-session:
`VpnTunnelService.kt:516-523` → `stateSink.onTunnelError(TunnelSetupFailed)` → `stopTunnel()`,
**both executed inside the `tunnelJob` coroutine**.

Setup-time failures (`Connecting → Error`: config `null`, `xrayCore.start()` false, `establish()`
null — `ConnectionControllerImpl.kt:168`, `VpnTunnelService.kt:412,468,488`) happen **before** the
presenter starts, so they never reach it — correct, because during a connect attempt the user is
in-app and Home already renders the error. And `onRevoke` is left unchanged (D1): a revoke is often
the user's own deliberate system-settings toggle (Android cannot distinguish self-revoke from
another-app takeover — `ConnectionError.Revoked` KDoc), so it produces no alert; the system shows
its own VPN-off affordance.

### Why delivery is deterministic (not a race)

The single trigger runs inside `tunnelJob`. `stopTunnel()` schedules teardown as
`serviceScope.launch { withTimeoutOrNull(2s){ tunnelJob.join() }; delay(100ms); finishTeardown() }`
(`VpnTunnelService.kt:583-587`), and `finishTeardown` calls `presenter.stop()` **before**
`onTunnelStopped()` (`VpnTunnelService.kt:610-613`). Therefore:

1. `onTunnelError(Error)` sets `state = Error` from **within** `tunnelJob`'s catch.
2. `finishTeardown` (hence `presenter.stop()`, which flips `active=false` and cancels the collector)
   runs only **after** `tunnelJob` completes (`join()`) **plus** a 100 ms grace — so the presenter
   collector is provably still alive, observing `state = Error`, for that whole window.
3. `Disconnected` is written **after** `presenter.stop()` has already cancelled the collector, so
   `StateFlow` conflation can never overwrite `Error` before the collector sees it — the collector's
   last observable value is `Error`.

Stated precisely (per cycle-2 review): the guarantee comes from *StateFlow always delivering its
latest value to a live collector* — `Error` sits in `_state` for the whole `join()`+100 ms window
while the collector is still active, and `Disconnected` is written only after `stop()` has cancelled
it. `join()` ensures `stop()` is strictly later; the load-bearing margin is `GRACE_STOP_DELAY_MS`
(100 ms), which must stay comfortably larger than a `Default → IO` dispatch (it is). The
`StandardTestDispatcher` presenter test proves the **logical** ordering deterministically (emit
`Connected → Error` → `advanceUntilIdle` → assert alert → `stop()`); the **physical** timing margin
(100 ms ≫ dispatch latency) is the real-dispatcher guarantee, spot-checked on-device in #133.

### Data flow (same mapper, no direct notify from catch blocks)

1. **Mapper (`contentFor`)** — add `NotificationContent.Error(reason: ConnectionError)` and map
   `VpnConnectionState.Error → NotificationContent.Error(state.reason)` (was `Inactive`). Pure,
   Context-free, `internal`. `Reconnecting` is **not** added (no domain state — issue defers it).
2. **Presenter pipeline** — unchanged shape
   `state.map { contentFor(it) }.filter { it !is Inactive }.distinctUntilChanged().flowOn(Default)
   .onEach { … }`. `Error` now passes the `!is Inactive` filter; `Inactive` (Disconnected / Loading
   / PermissionRequired) is still dropped exactly as before. `distinctUntilChanged` collapses an
   identical repeated `Error(reason)`.
3. **`onEach` routes by content type** (the only structural change), all under the existing
   `if (active.get())` gate:
   - `Connecting` / `Connected` → post the **ongoing** FGS notification to slot `NOTIFICATION_ID`
     (1001) exactly as today, via `TunnelNotifications.build`. On `Connected`, reset the alert
     dedup gate (defensive — see dedup).
   - `Error` → post a **separate, dismissible** alert to `ALERT_NOTIFICATION_ID` (1002) on the new
     `vpnis_alerts` channel via `TunnelNotifications.buildAlert`, subject to the dedup gate. The
     `onEach` router is a `when (content)` over **all four** `NotificationContent` subtypes —
     `Connecting`/`Connected` → ongoing (1001), `Error` → alert (1002), and `Inactive ->
     error("Inactive is filtered upstream")` (unreachable: the `filter` already dropped it). Covering
     all four subtypes (not relying on filter type-narrowing, which Kotlin does not do) makes the
     `when` genuinely compile-time exhaustive, so a future subtype is a compile error. The
     ongoing slot 1001 is **never** touched by an `Error` — the FGS notification is left untouched
     (non-interrupting) until `finishTeardown` removes it. This is the concrete meaning of #129's
     "MVP — reuse the ongoing FGS text (non-interrupting)": the error is surfaced out-of-band; the
     ongoing notification is not mutated. The alert lives in a different slot **and** channel, so
     `finishTeardown`'s slot-1001 `stopForeground(REMOVE)` + `cancel(NOTIFICATION_ID)` sweep
     (`VpnTunnelService.kt:616,620`) does not remove it — it stays until the user dismisses it
     (`setAutoCancel(true)`, not `ongoing`).

### Type-safe routing (Error never rendered as ongoing)

Adding `NotificationContent.Error` makes `TunnelNotifications.render`'s exhaustive `when`
non-exhaustive. `Error` must never flow through `build`/`render` (which produce the ongoing slot-1001
notification). Enforce the invariant in the type/`when`: `render`'s `is NotificationContent.Error`
branch is a hard `error("Error content must be routed to buildAlert, not rendered as ongoing")`
(exact message), so an accidental future call that puts `Error` on slot 1001 fails loudly rather than
silently violating the "1001 never touched by Error" invariant. The presenter's `onEach` is the
router that guarantees `build` only ever receives `Connecting`/`Connected`. `applyTimer` needs **no**
change — it uses `if (content is NotificationContent.Connected)` (a runtime check, not an exhaustive
`when`), so `Error` harmlessly falls to its `else` (no chronometer).

### Dedup / cooldown — "at most one alert per reconnect chain"

A "reconnect chain" today equals a single presenter session (`start()` → drop → `stop()`), because
there is no auto-reconnect yet (#64 out of scope) and the tunnel is torn down on the first drop.
Model dedup with one presenter-held gate (`@Volatile private var alertPosted = false`). `@Volatile`
(not `AtomicBoolean` — unlike `active`, which needs a cross-dispatcher happens-before with a
main-thread `stop()`) suffices: the collector `onEach` is the only in-flight writer/reader, and the
`start()` reset runs after `job?.cancel()` has cancelled any prior collector, so there is no
*concurrent* second writer — only a visibility barrier is needed:

- On `Error` → if `alertPosted` is `false`, post the alert and set it `true`; else skip.
- **Reset `alertPosted = false` in `start()`** (a fresh presenter session begins a fresh chain) —
  this is the primary reset and fixes the cycle-1 cross-session-suppression bug (a process-`single`
  presenter must not carry a stale `alertPosted=true` from a prior session into a new one and
  swallow the first genuine error). Also reset on `Connected` (defensive, harmless, future-proofs
  an in-session reconnect once #64/`Reconnecting` land).

Combined with `distinctUntilChanged` (collapses identical adjacent `Error(reason)`), this yields
exactly one alert per session/chain. Note explicitly: the alert-slot (1002) rate limit is provided
**solely** by `alertPosted` (for alternating reasons `distinctUntilChanged` does not collapse); the
ongoing-slot ≤1 notify()/sec guarantee is unchanged. **Dismiss-then-redrop** is a non-issue in the
current architecture: after the single drop the tunnel is torn down, so no second drop can occur
within the same session; a genuinely new drop requires a new connect (new `start()` → gate reset →
re-alert). This is revisited when `Reconnecting` (#64) lands (Out of Scope).

### Alert notification construction

New in `TunnelNotifications`:
- `ALERT_CHANNEL_ID = "vpnis_alerts"`, `ALERT_NOTIFICATION_ID = 1002`.
- `createAlertChannel(context)` — `IMPORTANCE_DEFAULT` (heads-up), localized name/description;
  called from `VpnTunnelService.onCreate` alongside the existing `createChannel`.
- `alertTextFor(context, reason)` — the localized copy resolver, exhaustive `when` over
  `ConnectionError`; **never** renders `ConnectionError.Unknown.message` (technical/credential leak
  risk — the raw message stays in logs only), mapping it and any non-specific reason to a generic
  "connection lost" body.
- `buildAlert(context, reason)` — `NotificationCompat.Builder(ALERT_CHANNEL_ID)`, small icon
  `ic_stat_vpn`, title = `vpn_alert_title` ("VPN disconnected" — carries the event so TalkBack
  reads "VPN disconnected, <reason>"), body = `alertTextFor`, `setAutoCancel(true)`, **not**
  `setOngoing`, `CATEGORY_ERROR`, `PRIORITY_DEFAULT`. Content intent (informational-only, D2):
  `context.packageManager.getLaunchIntentForPackage(context.packageName)` →
  `PendingIntent.getActivity(context, 0, intent, FLAG_IMMUTABLE)` (`requestCode = 0`, as the existing
  disconnect `getService` uses — the activity-vs-service target already differentiates them);
  if `getLaunchIntentForPackage` returns `null` (no launchable activity), build the alert **without**
  a content intent (still shown, just not tappable) rather than crashing. This keeps `:data:vpn`
  decoupled from `:app`'s `MainActivity` (no class reference → 🔴 dependency-direction guard holds).
  `buildAlert` receives, as a parameter, the presenter's `context` field (the **application** context,
  exactly as `build` is called), and is rebuilt per `notify()` (no cached `Notification`/`PendingIntent`)
  — no service leak.

### Localized copy (EN base + RU)

Owner decision D3: add `values-ru` for the alert strings now (a high-salience error alert on a
Russian-market VPN). Strings (keys — final copy in T-2):

| Key | EN | RU |
|---|---|---|
| `vpn_alert_channel_name` | VPN alerts | Оповещения VPN |
| `vpn_alert_channel_description` | Warns when the VPN connection drops unexpectedly | Предупреждает о неожиданном разрыве VPN-соединения |
| `vpn_alert_title` | VPN connection lost | VPN-соединение потеряно |
| `vpn_alert_text_server_unreachable` | Server unreachable. Tap to open VPNis. | Сервер недоступен. Нажмите, чтобы открыть VPNis. |
| `vpn_alert_text_tunnel_failed` | The connection dropped. Tap to open VPNis. | Соединение прервано. Нажмите, чтобы открыть VPNis. |
| `vpn_alert_text_generic` | Tap to open VPNis. | Нажмите, чтобы открыть VPNis. |

Copy notes (cycle-2 UX): the **title** carries the event and its *unexpectedness* ("VPN connection
lost", not "VPN disconnected" which reads like a user-initiated toggle) so TalkBack announces a
self-contained "VPN connection lost, <body>". Since the only reason that can reach the alert today
is `TunnelSetupFailed` (the hev mid-session drop, `VpnTunnelService.kt:521`), its body
(`tunnel_failed`) is written to be the clearest/most actionable string, naming the *outcome*
("connection dropped") + the **accurate** CTA for the informational-only alert ("Tap to open VPNis" —
not "reconnect", which would imply an action button that D2 defers).

`alertTextFor`: `ServerUnreachable → server_unreachable`; `TunnelSetupFailed → tunnel_failed`;
`Revoked` / `PermissionDenied` / `Unknown → generic` (Unknown.message never shown). EN lives in
`values/strings_vpn.xml`; RU in a new `values-ru/strings_vpn.xml` (only the alert keys — the ongoing
`vpn_notification_*` strings fall back to EN base, unchanged).

## Affected Modules & Files

| Path | Change | Note |
|---|---|---|
| `data/vpn/src/main/kotlin/.../TunnelNotifications.kt` | Modified | `NotificationContent.Error`; `contentFor(Error) → Error(reason)`; `render(Error)` hard-error guard; `ALERT_CHANNEL_ID`/`ALERT_NOTIFICATION_ID`; `createAlertChannel`; `alertTextFor`; `buildAlert` (null-intent fallback). All `internal`. |
| `data/vpn/src/main/kotlin/.../TunnelNotificationPresenter.kt` | Modified | `onEach` routes by content type: ongoing (1001) for Connecting/Connected, alert (1002) for Error; `alertPosted` gate reset in `start()` + on Connected. |
| `data/vpn/src/main/kotlin/.../VpnTunnelService.kt` | Modified | `onCreate` also calls `createAlertChannel`. **No** `onRevoke` / state-machine change (D1). |
| `data/vpn/src/main/res/values/strings_vpn.xml` | Modified | Add `vpn_alert_*` EN strings. |
| `data/vpn/src/main/res/values-ru/strings_vpn.xml` | New | RU translations of the `vpn_alert_*` strings (D3). |
| `data/vpn/src/test/kotlin/.../TunnelNotificationsTest.kt` | Modified | `contentFor(Error) → Error(reason)` cases; `alertTextFor` maps each reason (Unknown → generic, message not shown). |
| `data/vpn/src/test/kotlin/.../TunnelNotificationPresenterTest.kt` | Modified | Error → one post on 1002/`vpnis_alerts`, 1001 untouched; two consecutive Errors → one alert; `start→Error→stop→start→Error` → two alerts (reset-in-start); Connected still posts 1001. |

No Koin/module-graph change: the presenter constructor signature is unchanged (gate is internal
state; the alert channel is created by the service), so `FakeVpnModuleCheckTest` / `checkModules`
need no update.

## Decisions Made

| Decision | Rationale | Alternatives rejected |
|---|---|---|
| **D1** Alert fires only for native mid-session drop (`Connected→Error`); `onRevoke`/`Revoked` stays silent | Owner decision (review cycle 1). `onRevoke` cannot distinguish self-revoke (user toggled VPN off) from another-app takeover, so heads-up would often fight the user's own action; the system shows its own VPN-off UI. Also removes the state-machine change and the only non-`tunnelJob` (raced) emission path. | Heads-up on Revoked too (owner rejected: annoying on self-revoke); a second low-importance channel for Revoked (owner rejected: extra code for little value). |
| Alert = separate channel `vpnis_alerts` (IMPORTANCE_DEFAULT) + separate dismissible notification (slot 1002) | The FGS channel is `IMPORTANCE_LOW` + `ongoing` (cannot alert / cannot be dismissed); a heads-up needs a distinct channel and a distinct non-ongoing slot. Epic §4 & #129 body. | Raising FGS channel importance — breaks epic DoD "0 heads-up/sound on `vpnis_tunnel`". Reusing slot 1001 — removed at teardown, alert would vanish. |
| Alert sourced from the reactive `state` pipeline (mapper), not a direct `notify()` from the catch | #129 requires the trigger be the state machine via the mapper, not a parallel post path. Delivery is deterministic because the sole trigger runs inside `tunnelJob`, which `finishTeardown` joins before `presenter.stop()` (happens-before). | A dedicated non-conflating `SharedFlow` of errors — considered for the racy `onRevoke` path, but unnecessary once D1 removes that path; would add an internal seam + DI for no gain. |
| Dedup via `alertPosted` gate reset in `start()` (primary) + on `Connected` (defensive) | "One alert per reconnect chain" = one per presenter session today; resetting in `start()` fixes the cross-session suppression bug from a process-`single` presenter carrying stale state. Event-based, no clock. | Reset only on `Connected` (cycle-1 bug: swallows first error of a fresh manual session). Wall-clock cooldown (inject `Clock`) — needless given the session boundary. |
| `render(Error)` is a hard `error(...)`; `build` only ever gets non-error content | Type-system/loud-fail enforcement that `Error` reaches only `buildAlert`, protecting the "slot 1001 never shows Error" invariant. | Silently rendering `Error` as ongoing text — would corrupt slot 1001. |
| **D2** Informational-only alert (tap → launch intent, null-safe), no Reconnect action | Owner decision. A Reconnect action needs `ACTION_RECONNECT` + last-server/consent handling — a meaningfully larger change; deferred. Launch intent via `getLaunchIntentForPackage` keeps `:data:vpn` decoupled from `:app`. | Reconnect action now (owner deferred to a follow-up). Direct `MainActivity` reference — violates dependency direction. |
| **D3** English base + `values-ru` for the alert strings now | Owner decision: a high-salience error alert on a Russian-market VPN warrants RU immediately, even though the low-visibility ongoing FGS strings stay EN-base for now. | English-only now (owner rejected for the alert surface). Full module RU pass — out of scope beyond the alert keys. |
| `vpn_alert_title` = "VPN connection lost" (event + unexpectedness in the title) | So TalkBack reads "VPN connection lost, <reason>" meaningfully from title+body without relying on `CATEGORY_ERROR` (no a11y semantics), and it reads as *involuntary* (not a routine user-initiated "VPN disconnected"). | "VPNis" — unclear an error occurred; "VPN disconnected" — reads like a user-initiated toggle. |

## Risks & Mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| Alert emission not observed before `presenter.stop()` | major | Sole trigger runs inside `tunnelJob`; `finishTeardown` joins it (+100 ms) before `stop()`, and writes `Disconnected` only after the collector is cancelled — so `Error` cannot be conflated away. The `StandardTestDispatcher` presenter test proves **logical** ordering only (virtual time collapses the `Default → IO` hop); the **physical** guarantee is that `GRACE_STOP_DELAY_MS` (100 ms) ≫ a real dispatch, spot-checked on-device in #133. Do not shrink `GRACE_STOP_DELAY_MS` toward 0 without re-checking this margin. |
| Process-`single` presenter carries stale `alertPosted` into a new session, swallowing a real error | major | Reset `alertPosted` in `start()`, not only on `Connected`. Covered by the `start→Error→stop→start→Error` → two-alerts test. |
| `Error` accidentally rendered on the ongoing slot 1001 | major | `render(Error)` is a hard `error(...)`; the presenter routes `Error` exclusively to `buildAlert`. |
| `Unknown.message` leaks technical/credential detail into the shade | minor | `alertTextFor` maps `Unknown` to the generic body; the raw message is never put in the notification (logs only). |
| `getLaunchIntentForPackage` returns null → crash / dead tap | minor | Build the alert without a content intent when null. |
| Duplicate alerts on churny `Error → Error` | minor | `distinctUntilChanged` collapses identical reasons; `alertPosted` caps at one per chain. |
| Alert becomes sticky / not dismissible | minor | `setAutoCancel(true)`, not `ongoing`; separate slot. Confirmed on-device #133. |

## Verification & Sources

The finished change is verified against issue **#129**'s acceptance, epic **#126**'s decisions, and
owner decisions D1–D3 (no formal `docs/specs` file — the issue *is* the contract). Baseline: current
behaviour is "errors are invisible in the notification layer" (`contentFor(Error) → Inactive`,
filtered) — additive change, baseline is code-evident, no capture needed.

| Source of truth | Type | Status | Sufficient for verification? |
|---|---|---|---|
| GitHub issue #129 (body + review cycle-1 comment) | requirements | present | yes — the three falsifiable checks: Error reflects reason via mapper without dupes; interrupting alert on a separate channel, dismissible, cooldown; Reconnecting deferred. |
| Epic #126 body + `research-notifications-epic.md` §4 | requirements | present | yes — channel id/importance, dedup requirement, dependency-direction guards. |
| Owner decisions D1–D3 (this plan) | requirements | present | yes — scope the alert to native drops, informational-only, RU-now; each maps to a task acceptance. |

**Testing strategy (pyramid levels):** L0 build always + L1 static (ktlint blocking, detekt) + L2
unit (Robolectric `TunnelNotificationPresenterTest` for alert/dedup/slot behaviour incl. the
`Connected→Error` delivery ordering under `StandardTestDispatcher`, and the reset-in-`start()` case;
pure-JVM `TunnelNotificationsTest` for the mapper + `alertTextFor`) + L5 manual QA on Android
13/14/15 (deferred to sibling task **#133**: heads-up appears once, is dismissible, does not disturb
the ongoing FGS notification, and — since Revoked is silent by D1 — verify a system-revoke shows
*no* alert). L3 UI n/a (no Compose change). Because D1 removes the `VpnService`/state-machine change,
the previously device-only concern is now unit-covered at the presenter seam; the only L5-exclusive
item is the real OS heads-up rendering, tracked in #133 (qa-and-testing §4 tracked exception).

## Out of Scope

- `Revoked`/`onRevoke` interrupting alert — declined by owner (D1); `onRevoke` behaviour unchanged.
- `NotificationContent.Reconnecting` / auto-reconnect state and any pre-alert debounce — deferred
  until a domain `Reconnecting` state exists (#129 explicitly; owner: #64 / future epic work).
- A "Reconnect" alert action / reconnect deep-link — deferred (D2); can reuse the #114/#131 route
  helper later.
- Live traffic in the notification — task **#130** (blocked by #69).
- ~~RU translation of the ongoing `vpn_notification_*` strings~~ — done together with the alert
  strings: the project promotes lint `MissingTranslation` to an **error**, so once `values-ru` exists
  the locale must be complete. The whole `:data:vpn` notification surface is now RU (a superset of
  D3, strictly better for the RU market).
- Full mapper/gate/effect unit suite consolidation — task **#132** (this plan adds targeted
  regression tests inline).

## Open Questions

- [non-blocking] Confirm on-device (Android 14/15) that `IMPORTANCE_DEFAULT` yields a heads-up for
  the alert while the FGS `IMPORTANCE_LOW` notification stays silent — validated in #133.
