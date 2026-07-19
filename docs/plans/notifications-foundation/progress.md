# Progress: Notifications foundation — state-driven content + `NotificationPermissionState` gate (#127)

> Plan: ./plan.md · Tasks: ./tasks.md

## Status
- [x] T-1 — Refactor `contentFor` to a pure state→content mapper
- [x] T-2 — Introduce `NotificationContent` data subtypes (no formatted strings) [done as part of T-1: data-only subtypes + setOnlyAlertOnce; verified getString absent from contentFor]
- [x] T-3 — Add `NotificationPermissionState` domain contract
- [x] T-4 — Android impl `AndroidNotificationPermissionState` + bind in `vpnModule`
- [x] T-5 — Fake impl `FakeNotificationPermissionState` + bind in `fakeVpnModule`
- [x] T-6 — `TunnelNotificationPresenter` (single, owns the slot)
- [x] T-7 — Wire presenter into `VpnTunnelService`; remove `updateNotification`; fix teardown race
- [x] T-8a — Wire cataloged test infra (koin-test-junit4, robolectric)
- [x] T-8 — `checkModules` tests for real + fake Koin graphs
- [x] T-9 — Guardrails: dependency-direction + full check

## Learnings
<!-- Append one line per completed task: surprises, gotchas, decisions taken during implementation. -->
- T-1: `contentFor` now maps `VpnConnectionState → NotificationContent` (total `when`); `build()` renders via new `render()` using string resources. Added 4 string resources (title/active/connecting/connected); `Inactive` keeps "VPNis"/"Tunnel active" unchanged. Folded T-2's `setOnlyAlertOnce(true)` into `build()` (same method) — T-2 now just verifies data-only + the flag. Bridged `VpnTunnelService`'s two `NotificationContent.Default` refs → `.Inactive` (T-7 removes `updateNotification` entirely). Module test task is `:data:vpn:test` (no product flavors on the lib; flavors are `:app`-only). detekt has a PRE-EXISTING `LongMethod` failure in `XrayConfigBuilder.kt:243` (unrelated, reporting-only in CI); my code is clean. `:data:vpn:test` + `ktlintCheck` green.
- T-3..T-6: production code delegated to kotlin-engineer per plan (domain contract + Android/Fake gate impls + presenter). All match plan snippets; compiled clean.
- T-7: presenter wired into VpnTunnelService — start() after onTunnelEstablished (not after startForeground, so abort paths can't leak a collector); stop() before onTunnelStopped in finishTeardown + final cancel(NOTIFICATION_ID) sweep; stop() in onDestroy; updateNotification removed.
- T-8/T-8a: tests via unit-test-engineer. Gotcha 1: androidx.test ApplicationProvider NOT on :data:vpn classpath (only present in :feature:home via compose-ui-test) → used org.robolectric.RuntimeEnvironment.getApplication() instead (no new dep). Gotcha 2: :data:vpn needed testOptions.unitTests.isIncludeAndroidResources=true for Robolectric getString (added). Fixed a flawed distinct test (Connected→Connecting→Connected are 3 distinct contents; rewrote to flowOf(connected,connected) consecutive-duplicate).
- T-9: all 5 dependency-direction guards pass; `./gradlew test :app:assembleDebug ktlintCheck` green project-wide. detekt unchanged pre-existing XrayConfigBuilder failure only.
- Quality gate (code-reviewer, gate 4): WARN → resolved. (major) presenter test (c) overclaimed testing the flowOn hand-off race — single test dispatcher makes flowOn a passthrough & job.cancel() dominates, so the `active` gate can't be deterministically isolated in a unit test (it guards a multi-threaded sub-frame race; guaranteed closer is finishTeardown's cancel(NOTIFICATION_ID) sweep, device-QA #133). Reworded test (c) + class KDoc to state the true contract rather than chase an impossible test. (minor) removed stale [updateNotification] KDoc link in VpnTunnelService. Re-ran: :data:vpn:test + ktlintCheck green.
