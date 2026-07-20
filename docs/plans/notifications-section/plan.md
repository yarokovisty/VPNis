---
type: plan
slug: notifications-section
date: 2026-07-21
status: approved
spec: none
risk_areas:
  - dependency-direction (:feature:home must not touch :data:vpn)
  - proactive-visibility vs first-run noise (binary gate cannot distinguish "not asked" from "blocked")
  - two-banner conflict with #114 session-scoped denial nudge
  - Connected-centric copy misleading on the Disconnected placement
review_verdict: conditional-resolved
review_panel: [architecture-expert, ux-expert]
review_notes: >
  Cycle 1 CONDITIONAL (0 critical, 2 major, 9 minor). Both majors + high-value minors folded into
  plan/tasks: (arch) HomeModuleCheckTest is not a dependency-direction guard — real guard = grep on
  build.gradle.kts + imports; (ux) copy is Connected-centric ("shade/disconnect") and must be reworded
  for both states. Also: thread params into both Disconnected sub-branches, empty-branch placement
  anchor, stale NotificationSettingsIntents KDoc, dismiss cross-state behavior (DM-4), liveRegion
  re-announce on resume (DM-6, → QA #133).
---

# Plan — feat(home): проактивная секция «Уведомления» + deep-link (#131)

## Context & Decision

GitHub issue **#131**, epic **#126**. Add a discoverable "Notifications" section to `:feature:home`
that surfaces when the OS will not display the tunnel notification, with a deep-link into system
settings. `:feature:settings` is intentionally **not** created (YAGNI) — the surface lives in Home.

**Crucial scoping fact:** most of the underlying infrastructure already shipped with **#114**
(`docs/plans/notifications-permission/`). This plan covers **only the delta**. Already present in
`:feature:home`:

- `HomeViewModel.notificationsGranted: StateFlow<Boolean>`, `notificationChannelId: String`,
  `refreshNotificationPermission()` — read the domain gate `NotificationPermissionState`
  (`:core:domain`). Single source of truth for "granted".
- `NotificationSettingsIntents` (`internal object`) — `channelNotificationSettings()`
  (`ACTION_CHANNEL_NOTIFICATION_SETTINGS` + `EXTRA_CHANNEL_ID`) and `appNotificationSettings()`
  (`ACTION_APP_NOTIFICATION_SETTINGS`). The deep-link route helper is **done** — reuse, do not
  duplicate.
- `HomeRoute` — `ON_RESUME` pull-refresh of the gate; `onOpenNotificationSettings` already tries
  channel-level then app-level fallback; a `VPNisBanner` is already rendered — **but only in
  `HomeConnectedContent`, and only after the system dialog was shown once** (route-local
  `hasRequestedBefore`, `rememberSaveable`) and not dismissed this session.

The **#114 banner is a session-scoped denial nudge tied to a live Connected session**. #131's delta
is to make recovery **proactive and discoverable**: show the same banner on the resting Home screen
(both `Disconnected` and `Connected`) whenever notifications are blocked — so a user who denied (or
silenced the channel) can find and fix it without needing to be mid-session.

### Design decisions taken with the owner (2026-07-21)

- **Proactivity model (Q1 → "as #114 + wider"):** keep the existing route-local `hasRequestedBefore`
  gate; **broaden placement** to `Disconnected` **and** `Connected`. Do **not** add a persistence
  layer. Rationale: the binary domain gate cannot distinguish "permission not yet requested" (fresh
  install, API 33+) from "really blocked". Until the app has asked once, the correct recovery is the
  in-app system dialog (owned by #114's Connected flow), **not** a settings deep-link — so gating on
  `hasRequestedBefore` avoids nagging a fresh install with a wrong-CTA banner. Once asked, the
  proactive banner now also appears on the Disconnected screen and survives leaving a Connected
  session. Trade-off accepted: `hasRequestedBefore` is `rememberSaveable` (Activity saved-state
  lifetime), so proactivity resets on process death; combined with `ON_RESUME` refresh this is
  acceptable for a solo-YAGNI scope.
- **Copy granularity (Q2 → "generic text"):** a single generic message. The domain gate is
  intentionally binary and epic #126 **forbids** adding channel `importance` to the `:core:domain`
  contract, so "channel silenced" vs "app-level off" cannot be distinguished at the gate. The
  app-vs-channel distinction is handled where it belongs — in the **deep-link** target (channel-first
  → app-fallback, already implemented), which satisfies review item **I6**.
  - ⚠️ **Copy must be rewritten (review finding).** The existing `home_notification_banner_text` is
    NOT state-agnostic: it promises "…disconnect from the shade without opening the app" — a benefit
    that only exists in a live Connected session. Shown proactively on the **Disconnected** screen it
    describes a non-existent action. The copy (EN + RU) must be reworded to a benefit valid in BOTH
    states, e.g. "Turn on notifications to see VPN status and control the connection without opening
    the app" (drop the "shade / disconnect" wording). The `strings.xml:51-52` comment that ties the
    banner to "#114 / Connected" is updated with it.

## Technical Approach

1. **Extract one stateless `NotificationsSection` composable** (`internal`, in `HomeScreen.kt` or a
   new `components/NotificationsSection.kt`) that wraps the `VPNisBanner` + copy + a11y
   (`liveRegion = Polite`) + CTA/dismiss slots. Single source of the banner's UI/copy so both states
   render identically. Replace the inline banner block currently in `HomeConnectedContent`.

2. **Broaden placement.** Render `NotificationsSection` in **both** `HomeDisconnectedContent` and
   `HomeConnectedContent`, gated by the same visibility flag. `HomeDisconnectedContent` fans out into
   two private sub-composables (`HomeEmptyContent` for `server == null`, `HomeDisconnectedWithServerContent`
   for `server != null`) — the params must be threaded into **both**, and `NotificationsSection` is the
   single insertion point in each so the render is identical. Placement:
   - `Disconnected`, server present (`HomeDisconnectedWithServerContent`): after `StatusIndicator`,
     before the `ServerCard` — a proactive recovery prompt on the home screen.
   - `Disconnected`, no server (`HomeEmptyContent`): place the section **below** the primary onboarding
     (after the "add a server" prompt card / primary CTA), NOT above it — on a fresh no-server screen
     the user's primary task is adding a server, not notifications (ux review Issue 2). There is no
     `ServerCard` in this branch, so "before ServerCard" is undefined here — this explicit anchor
     resolves it.
   - `Connected`: unchanged from #114 (after `SessionTimer`, before `ServerCard`).

3. **Extract the visibility predicate to a pure function** for testability and to avoid divergence
   between the two placements:
   `internal fun shouldShowNotificationsSection(hasRequestedBefore, granted, requestInFlight, dismissed): Boolean`
   = `hasRequestedBefore && !granted && !requestInFlight && !dismissed`. `HomeRoute` computes it once
   and threads the boolean + `onOpenNotificationSettings` + `onDismiss` into both content branches.

4. **Reconcile with #114's session-scoped state.** `dismissedThisSession` stays a route-local
   `remember` (not saveable): dismissing hides the section for the current app run; it re-surfaces on
   the next launch or after returning from settings still-blocked (via `ON_RESUME` refresh). The
   existing reset keyed on a new Connected `since` is kept; the Disconnected placement simply reads
   the same flag (no new reset trigger). One flag, one predicate — no two conflicting banners.

5. **Deep-link:** unchanged. `onOpenNotificationSettings` (channel-first → app-fallback) already
   satisfies I6. No new intents.

6. **Dependency direction (guard 🟡):** all signals come from `:core:domain` via the ViewModel gate;
   `NotificationSettingsIntents` builds plain `android.content.Intent`s in the route layer (allowed).
   No `project(":data:vpn")`, no `:data:vpn` types. **Verification correction (arch review Issue 1):**
   `HomeModuleCheckTest` is a Koin `checkModules()` graph-resolution test — it does NOT catch a
   `:data:vpn` dependency and must not be cited as the dependency-direction guard. The real guards are
   (a) `grep` for `data:vpn` in `feature/home/build.gradle.kts` → no match, and (b) `grep` for a
   `org.yarokovisty.vpnis.data.vpn` import across `feature/home/src` → no match.

7. **Follow-up trigger** (record, do not implement): extracting a real `:feature:settings` module —
   and moving `NotificationSettingsIntents` to a shared module — is triggered by the **second**
   settings surface. #131 is NOT that second consumer, so the stale KDoc in
   `NotificationSettingsIntents.kt` (which currently says extraction is "deferred to #131") is
   corrected to point at "the second settings surface". Documented in Out of Scope + a note on
   #131 / epic #126.

## Affected Modules & Files

| Path | Change | Note |
|---|---|---|
| `feature/home/.../HomeScreen.kt` | modify | Extract `NotificationsSection`; render in Disconnected + Connected; thread params into `HomeDisconnectedContent`. |
| `feature/home/.../components/NotificationsSection.kt` | add (optional) | Stateless section composable if extracted out of `HomeScreen.kt`. |
| `feature/home/.../HomeRoute.kt` | modify | Compute pure predicate; pass banner boolean + callbacks to both branches. |
| `feature/home/.../NotificationsVisibility.kt` | add | `internal` pure `shouldShowNotificationsSection(...)` (testable seam). |
| `feature/home/.../HomeScreenPreviews.kt` | modify | Add Disconnected-with-section preview (light/dark). |
| `feature/home/src/main/res/values/strings.xml` | modify | Update the `#114`-specific comment to state-agnostic; keys unchanged (already generic). |
| `feature/home/src/main/res/values-ru/strings.xml` | verify | RU keys already present; keep parity (no `MissingTranslation`). |

## Decisions Made

- **DM-1** Proactive but gated on `hasRequestedBefore` — see "Design decisions" Q1. No persistence layer.
- **DM-2** Generic copy; app-vs-channel distinction lives in the deep-link target, not the text — Q2.
- **DM-3** Single stateless `NotificationsSection` + single pure visibility predicate reused by both
  placements — no duplicated banner logic, no two-banner conflict.
- **DM-4** `dismissedThisSession` stays a non-saveable `remember`; re-surfaces on relaunch/resume
  while blocked. **Cross-state behavior (ux review Issue 4):** the flag is app-session-scoped and
  reset only on a new Connected `since`, so dismissing in one state hides the section in the other
  within the same run (dismiss in Disconnected also suppresses it in Connected until a new session,
  and vice-versa). This "one app-session = one dismiss" behavior is intended; it is made explicit
  here and must be covered by the #132 truth-table/UI test rather than left emergent.
- **DM-6** `liveRegion = Polite` is kept, but because the proactive predicate is re-evaluated on every
  `ON_RESUME`, a TalkBack user returning to Home while still blocked would hear the banner re-announced
  each time. Acceptable for now; flagged as an explicit TalkBack check in QA #133 (re-announce on
  repeated resume). If it proves naggy on-device, drop `Polite` for the proactive path (the banner
  stays reachable in normal focus order).
- **DM-5** No new domain contract surface — the gate stays binary `isGranted` + opaque `channelId`
  (epic #126 hard constraint).

## Risks & Mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| Fresh-install noise / wrong CTA before first ask (API 33+) | major | Gate on `hasRequestedBefore` (DM-1); in-app dialog path stays #114-owned. |
| Two conflicting banners (Disconnected + Connected) | major | One predicate, one `NotificationsSection`, one dismiss flag (DM-3/DM-4). |
| `:feature:home` leaking into `:data:vpn` | major | Gate via `:core:domain` only; `HomeModuleCheckTest` + grep guard (approach §6). |
| Proactive banner permanently dismissed hides a real misconfig | minor | Dismiss is session-scoped `remember`, re-surfaces on relaunch/resume while blocked (DM-4). |
| `MissingTranslation` breaks lint (RU parity) | minor | Keys already exist in `values-ru`; task check runs `:feature:home:lintDebug`. |
| Connected-centric copy misleads on Disconnected | major | Reword copy to a both-states benefit; drop "shade/disconnect" wording; update EN+RU + comment (see Copy note; T-4). |
| `default = false` param hides a lost Disconnected placement | minor | Mandatory Disconnected-with-section previews for BOTH sub-branches, light+dark (T-4). |

## Verification & Sources

- **Source of truth:** GitHub issue #131 (acceptance criteria) + epic #126 dependency-direction
  guards + the multiexpert-review cycle-1 comment on #131 (I6, placement/discoverability). No spec
  doc exists (`docs/specs/` empty) — the issue IS the contract; this plan references its acceptance,
  it does not restate a spec.
- **Baseline (behavior-preserving refactor part):** the #114 Connected-state banner behavior is the
  before-state — extracting `NotificationsSection` must not change how it renders in Connected.
  Capture by keeping the existing `HomeScreen` Connected preview green.
- **Sufficiency:** issue #131 acceptance is checkable (section reflects gate; deep-link opens correct
  screen; dependency direction; follow-up recorded). The one criterion not verifiable in JVM/unit —
  the deep-link actually landing on the channel/app settings page and the gate reflecting app +
  channel importance on-device — is owned by QA **#133** (Android 13/14/15 run), not this task.
- **Testing strategy (pyramid):**
  - **L0 build/lint:** `:feature:home:assembleDebug` + `:feature:home:lintDebug` (no `MissingTranslation`), `ktlintCheck`.
  - **L1 unit (this plan provides the seam; #132 writes the cases):** pure
    `shouldShowNotificationsSection(...)` truth-table (granted → hidden; **not-asked → hidden
    regardless of `granted`** — the key fresh-install/cold-start-Disconnected guard, Risk #1;
    blocked+asked → shown; in-flight → hidden; dismissed → hidden). Include the explicit
    cold-start-Disconnected case since #131 shows the section on Disconnected for the first time.
  - **L2 Compose UI (optional, #132):** section visible in Disconnected when blocked; CTA invokes
    `onOpenNotificationSettings`; dismiss invokes `onDismiss`; a11y `liveRegion`.
  - **L5 manual/on-device:** deferred to QA #133 (deep-link target correctness; gate reflects app +
    channel importance on A13/A14/A15).

## Out of Scope

- Runtime `POST_NOTIFICATIONS` request flow and the system dialog — owned by #114 (this plan only
  broadens where the resulting denial banner is shown).
- Real `:feature:settings` module extraction and moving `NotificationSettingsIntents` to a shared
  module — deferred until a second settings surface exists (follow-up recorded).
- Unit/UI test authoring — owned by #132 (this plan defines the testable seam and the cases to cover).
- Any change to the `:core:domain` `NotificationPermissionState` contract (epic #126 forbids it).

## Open Questions

_None blocking._ Both design forks (proactivity model, copy granularity) were resolved with the owner
on 2026-07-21 (see "Design decisions"). Non-blocking: whether the Disconnected section should sit
above or below the `ServerCard` is a pure visual nicety — default is above (proactive), adjustable in
review of the rendered preview.
