---
type: progress
slug: notifications-section
---

# Progress — notifications-section (#131)

## Tasks

- [x] T-1 — Extract stateless `NotificationsSection` composable
- [x] T-2 — Extract pure visibility predicate `shouldShowNotificationsSection`
- [x] T-3 — Render section proactively in Disconnected + thread params
- [x] T-4 — Reword copy (both-states) + previews + RU parity
- [x] T-5 — Dependency-direction guard + KDoc fix + follow-up record

## Learnings

- Plan reviewed by architecture-expert + ux-expert (multiexpert-review, cycle 1 → CONDITIONAL,
  0 critical / 2 major). Both majors resolved in-plan before implementation: (1) `HomeModuleCheckTest`
  is a Koin graph check, not a dependency-direction guard — use grep guards instead; (2) the #114
  copy is Connected-centric and must be reworded for both states (T-4).
- Follow-up: `:feature:settings` extraction (and moving `NotificationSettingsIntents` to a shared
  module) is triggered by the SECOND settings surface — #131 is not it. KDoc corrected in T-5.
- T-1: `NotificationsSection` extracted as a single `internal` composable in `HomeScreen.kt` — owns copy, icon, CTA, dismiss, and `liveRegion = Polite`; replaces the inline block in `HomeConnectedContent`.
- T-2: `NotificationsVisibility.kt` added with pure `shouldShowNotificationsSection`; inline 4-term expression removed from `HomeRoute` (replaced with a named call — grep confirms it's gone).
- T-3: Banner params threaded into `HomeDisconnectedContent` → `HomeEmptyContent` (section below CTAs) and `HomeDisconnectedWithServerContent` (section after StatusIndicator, before ServerCard); `HomeScreen` Disconnected branch now forwards all three banner params.
- T-4: EN copy reworded to "…control the connection without opening the app" (no "shade/disconnect"); RU mirror updated; strings.xml comment updated to state-agnostic wording tied to #131; 4 Disconnected-with-banner previews added (both sub-branches × light/dark).
- T-5: `NotificationSettingsIntents.kt` KDoc corrected — extraction trigger changed from "#131" to "the second settings surface"; dependency-direction grep guards confirmed clean (no `data:vpn` in build.gradle.kts or feature/home/src imports).
