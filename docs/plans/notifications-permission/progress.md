# Progress: POST_NOTIFICATIONS runtime request + denial-UX + notification visibility (#114)

> Plan: ./plan.md · Tasks: ./tasks.md

## Status
- [x] T-1 — `channelId` + `refresh(): Boolean` on contract (+impls +#127 tests) — green
- [x] T-2 — effect + VM derivation (guard) + `notificationsGranted`/`notificationChannelId`/`refreshNotificationPermission()` + tests + HomeModuleCheckTest — green
- [x] T-3 — `NotificationSettingsIntents` (channel + app deep-link) + Robolectric test
- [x] T-4 — RU/EN banner strings (`home_notification_banner_text`/`_button`)
- [x] T-5 — VPNisBanner Info in HomeConnectedContent (semantics Polite) + previews — green
- [x] T-6 — HomeRoute denial FSM (launcher, hasRequestedBefore/requestInFlight/dismissedThisSession, gated ON_RESUME, gate-driven banner, channel-level deep-link) — green, no :data:vpn import
- [x] T-7 — Verification pass — `assembleDebug` + all module unit tests + `:feature:home:lint` GREEN; layer invariants confirmed (no `:data:vpn` in home, no `android.*` in domain, CHANNEL_ID internal). L5 device acceptance A13/14/15 → #133.

## Learnings
- T-1: `:data:fake` fake duplicates literal `"vpnis_tunnel"` (no `:data:vpn` dep); `refresh()` returns computed value, not StateFlow read-back.
- T-2: `:data:fake` not on `:feature:home` test classpath → local `FakeNotificationPermissionState` test double added; added `testImplementation(libs.koin.test.junit4)`; T-2 left a stub `RequestNotificationPermission -> Unit` branch in HomeRoute (T-6 replaces it).
- T-3: VPNisBanner (verified API) has single `text` slot + `VPNisBannerAction(label,onClick)` primaryAction + `onDismiss`; NO `liveRegionMode` param → wrap call-site in `Modifier.semantics`.
