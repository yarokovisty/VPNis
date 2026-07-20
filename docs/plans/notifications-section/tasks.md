---
type: tasks
slug: notifications-section
---

# Tasks — notifications-section (#131)

Ordered. Each task is small enough to implement and verify in one focused pass. Acceptance is the
implementation-level check that the corresponding issue-#131 criterion is met.

---

## T-1 — Extract stateless `NotificationsSection` composable

- **after:** —
- **files:** `feature/home/.../HomeScreen.kt` (or new `components/NotificationsSection.kt`)
- **change:** Pull the inline `VPNisBanner` block currently in `HomeConnectedContent`
  (`HomeScreen.kt` ~L490–506) into a single `internal @Composable fun NotificationsSection(
  onOpenSettings: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier)` that owns the
  copy, `VPNisBannerVariant.Info`, `Icons.Filled.Info`, CTA + dismiss, and
  `semantics { liveRegion = Polite }`. Call it from `HomeConnectedContent` in the same spot. Keep it
  `internal` in `:feature:home` — a feature wrapper over the `:design:uikit` `VPNisBanner` primitive,
  intentionally NOT lifted into `:design:uikit` (arch review Issue 4).
- **acceptance:** GIVEN the Connected state with the banner visible, WHEN rendered, THEN the output
  is identical to the pre-refactor #114 banner (behavior-preserving).
  - check: `:feature:home:assembleDebug` green; existing Connected preview renders unchanged;
    `ktlintCheck` green.

## T-2 — Extract pure visibility predicate

- **after:** T-1
- **files:** `feature/home/.../NotificationsVisibility.kt` (add)
- **change:** `internal fun shouldShowNotificationsSection(hasRequestedBefore: Boolean, granted:
  Boolean, requestInFlight: Boolean, dismissed: Boolean): Boolean =
  hasRequestedBefore && !granted && !requestInFlight && !dismissed`. Replace the inline boolean in
  `HomeRoute` with a call to it.
- **acceptance:** THE SYSTEM SHALL return `true` only when asked-before ∧ not-granted ∧
  not-in-flight ∧ not-dismissed; `false` otherwise.
  - check: pure JVM truth-table unit test compiles & passes (authored here or handed to #132); grep
    confirms `HomeRoute` no longer inlines the 4-term expression.

## T-3 — Render section proactively in Disconnected + thread params

- **after:** T-2
- **files:** `feature/home/.../HomeScreen.kt`, `feature/home/.../HomeRoute.kt`
- **change:** Add `showNotificationBanner`, `onOpenNotificationSettings`, `onDismissNotificationBanner`
  params to `HomeDisconnectedContent` AND thread them into its two private sub-composables —
  `HomeDisconnectedWithServerContent` (`server != null`) and `HomeEmptyContent` (`server == null`).
  Insert `NotificationsSection` when the flag is true, with distinct anchors per sub-branch:
  - `HomeDisconnectedWithServerContent`: after `StatusIndicator`, before `ServerCard`.
  - `HomeEmptyContent`: **below** the primary onboarding (after the prompt card / Connect+Add CTAs),
    NOT above it — the primary task on a no-server screen is adding a server (ux review Issue 2).
  In `HomeRoute`, pass the same computed predicate + callbacks into the `Disconnected` branch of
  `HomeScreen` (currently only `Connected` receives them).
- **acceptance:** GIVEN gate `!granted` ∧ `hasRequestedBefore` ∧ not-dismissed, WHEN the screen is in
  `Disconnected`, THEN the notifications section is shown with a working "Open settings" CTA; WHEN
  `granted`, THEN nothing is shown (no permanent noise).
  - check: new Disconnected-with-section preview renders; `:feature:home:assembleDebug` green.
  - satisfies #131: "секция отражает актуальное состояние через gate" + proactive discoverability.

## T-4 — Reword copy (both-states) + previews + RU parity

- **after:** T-3
- **files:** `feature/home/.../HomeScreenPreviews.kt`, `feature/home/src/main/res/values/strings.xml`,
  `feature/home/src/main/res/values-ru/strings.xml`
- **change:**
  - **Reword `home_notification_banner_text` (EN + RU)** to a benefit valid in BOTH Disconnected and
    Connected — drop the Connected-only "disconnect from the shade" wording (ux review Issue 1).
    Suggested EN: "Turn on notifications to see VPN status and control the connection without opening
    the app." RU mirror. Update the `strings.xml:51-52` comment (currently ties the banner to
    "#114 / Connected") to state-agnostic wording.
  - Add light/dark `@Preview`s for Disconnected-with-section for **both** sub-branches
    (`server == null` and `server != null`) so the `default = false` param can't silently hide a lost
    placement (ux review Issue 5).
- **acceptance:** GIVEN the module builds, WHEN `:feature:home:lintDebug` runs, THEN no
  `MissingTranslation`; the reworded copy contains no "shade/disconnect" wording; previews compile.
  - check: `:feature:home:lintDebug` green; `ktlintCheck` green; grep for "shade"/"шторк" in the
    banner strings → no match.

## T-5 — Dependency-direction guard + follow-up record

- **after:** T-3
- **files:** `docs/plans/notifications-section/progress.md`;
  `feature/home/.../NotificationSettingsIntents.kt` (KDoc); verify `feature/home/build.gradle.kts`;
  optional note appended to issue #131 / epic #126.
- **change:** Confirm no `project(":data:vpn")` in `:feature:home` and no `:data:vpn` type imported
  by the new code. Fix the stale KDoc in `NotificationSettingsIntents.kt:22-26` — it says extraction
  is "deferred to #131 (the Settings 'Notifications' section)"; #131 explicitly does NOT create
  `:feature:settings`, so reword the trigger to "the second settings surface" (arch review Issue 5).
  Record the `:feature:settings` extraction trigger as a follow-up in `progress.md` learnings and
  (optionally) as an issue comment.
- **acceptance:** THE SYSTEM SHALL keep `:feature:home` talking to the notification subsystem only
  through `:core:domain` contracts.
  - check (dependency-direction guard — NOT `HomeModuleCheckTest`, which only resolves the Koin graph
    per arch review Issue 1): `grep -R "data:vpn" feature/home/build.gradle.kts` → no match AND
    `grep -R "org.yarokovisty.vpnis.data.vpn" feature/home/src` → no match; KDoc no longer references
    #131 as the extraction trigger; follow-up line present in `progress.md`.

---

### Handoff to #132 (tests — not authored here)

Provide the seam; #132 authors: (a) `shouldShowNotificationsSection` truth-table unit test;
(b) optional Compose UI test — section visible in Disconnected when blocked, CTA → `onOpenSettings`,
dismiss → `onDismiss`, a11y `liveRegion`. On-device deep-link + gate correctness = QA #133.
