# Tasks: POST_NOTIFICATIONS runtime request + denial-UX + notification visibility (#114)

> Plan: ./plan.md · Epic #126 · Acceptance from issue #114 "Приёмка" · Review: CONDITIONAL (cycle 1, folded)

## T-1 — Contract: `channelId` + `refresh(): Boolean`
- after: none
- files: `core/domain/.../permission/NotificationPermissionState.kt`, `data/vpn/.../AndroidNotificationPermissionState.kt`, `data/fake/.../FakeNotificationPermissionState.kt`, `data/vpn/src/test/.../AndroidNotificationPermissionStateTest.kt`, `data/fake/src/test/.../FakeNotificationPermissionStateTest.kt`
- acceptance: THE SYSTEM SHALL (a) expose `val channelId: String` — Android impl → `TunnelNotifications.CHANNEL_ID` (staying `internal`); fake → its own `private const "vpnis_tunnel"` literal (`:data:fake` must NOT import `:data:vpn`); (b) change `refresh()` to `suspend fun refresh(): Boolean` that **computes** the gate value, stores it in the backing StateFlow, and **returns the computed value** (not a read-back of `_isGranted.value`); (c) reword the "channel-agnostic" KDoc to scope it to `isGranted` logic and mark `channelId` an opaque deep-link target. Domain contract gains NO `android.*` import. #127 impl tests updated for the new signature.
- check: `:core:domain:compileDebugKotlin`, `:data:vpn:compileDebugKotlin`, `:data:fake:compileDebugKotlin`, `:data:vpn:test`, `:data:fake:test` green; grep: `CHANNEL_ID` still `internal const`, no `import android.` in `NotificationPermissionState.kt`. (supports #114 deep-link + perf#1)

## T-2 — New effect + VM request derivation + gate exposure
- after: T-1
- files: `feature/home/.../HomeContract.kt`, `feature/home/.../HomeViewModel.kt`, `feature/home/.../HomeModule.kt`, `feature/home/src/test/.../HomeViewModelTest.kt`, `feature/home/src/test/.../HomeModuleCheckTest.kt` (new)
- acceptance: GIVEN the tunnel transitions INTO `Connected` and `refresh()` returns `false` WHEN the state settles THEN `HomeViewModel` emits `HomeEffect.RequestNotificationPermission` exactly once **per process** (`requestedThisProcess` guard); it does NOT emit on a `Connected→Connected` self-transition, when `refresh()` returns `true`, nor on a second `Connected` entry after a reconnect. VM exposes `notificationsGranted: StateFlow<Boolean>` (same `isGranted` gate), `notificationChannelId: String` (= `permissionState.channelId`), and `refreshNotificationPermission()`. Koin wires the 3-arg constructor. Tests use a **local** `FakeNotificationPermissionState` test double in `feature/home/src/test` (not `:data:fake` — not on the home test classpath).
- check: `HomeViewModelTest` cases (emit-once-when-denied; no-emit-on-self-transition; no-emit-when-granted; no-emit-on-reconnect via `Connected→Disconnected→Connected`) green; new `HomeModuleCheckTest` runs Koin `checkModules`/`verify` over `homeModule` + fake bindings (catches the 3rd `get()`); `:feature:home:testDebugUnitTest` + `:feature:home:test` green. (satisfies #114 "диалог после первого Connected"; guards "не перезапускается"; perf#2; red-team 4/7/10a)

## T-3 — Deep-link Intent factory
- after: none
- files: `feature/home/.../NotificationSettingsIntents.kt` (new), `feature/home/src/test/.../NotificationSettingsIntentsTest.kt`
- acceptance: THE SYSTEM SHALL provide `internal object NotificationSettingsIntents` building a channel-level intent (`ACTION_CHANNEL_NOTIFICATION_SETTINGS` + `EXTRA_APP_PACKAGE` + `EXTRA_CHANNEL_ID`) and an app-level fallback intent (`ACTION_APP_NOTIFICATION_SETTINGS` + `EXTRA_APP_PACKAGE`), both with `FLAG_ACTIVITY_NEW_TASK`, `channelId` as a parameter, with NO `:data:vpn` import.
- check: Robolectric unit asserts each intent's action/extras/flags; `:feature:home:testDebugUnitTest` green; grep: no `import org.yarokovisty.vpnis.data.vpn`. (satisfies #114 "переход в системные настройки")

## T-4 — Rationale copy (RU/EN, benefit-first)
- after: none
- files: `feature/home/src/main/res/values/strings.xml`, `feature/home/src/main/res/values-ru/strings.xml`
- acceptance: THE SYSTEM SHALL define, in BOTH `values/` and `values-ru/`, keys `home_notification_banner_text` and `home_notification_banner_button` (VPNisBanner has a single text slot — fold title into the body sentence). EN text "Turn on notifications to see VPN status and disconnect from the shade without opening the app." / button "Open settings"; RU «Включите уведомления, чтобы видеть статус VPN и отключаться из шторки, не открывая приложение.» / «Открыть настройки». Both keys present in both files; copy fits ≤2 lines at fontScale 1.0.
- check: `:feature:home:lint` (MissingTranslation) green; grep parity — identical key set in both strings.xml. (satisfies #114 rationale-copy deliverable; ux#4; red-team 10c)

## T-5 — Rationale banner in stateless HomeScreen (reuse VPNisBanner)
- after: T-4
- files: `feature/home/.../HomeScreen.kt`, `feature/home/.../HomeScreenPreviews.kt`, `feature/home/.../HomeScreenPreviewsConnectedError.kt`
- acceptance: GIVEN new **defaulted** public params `showNotificationBanner: Boolean = false`, `onOpenNotificationSettings: () -> Unit = {}`, `onDismissNotificationBanner: () -> Unit = {}` (module is `explicitApi()` — defaults avoid an API-breaking change) WHEN `showNotificationBanner` is true THEN `HomeConnectedContent` renders `VPNisBanner(text = stringResource(home_notification_banner_text), variant = VPNisBannerVariant.Info, icon = <info>, primaryAction = VPNisBannerAction(stringResource(home_notification_banner_button), onOpenNotificationSettings), onDismiss = onDismissNotificationBanner)` after the SessionTimer spacer and before `ServerCard`, full-width. Accessibility: wrap the banner call-site `modifier` in `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` (VPNisBanner has NO `liveRegionMode` param); reuse VPNisBanner built-in dismiss/icon descriptions (no re-label); banner precedes `ServerCard` in traversal order. Screen stays stateless. In-app Disconnect (`Connected` `ConnectionButton`) remains rendered.
- check: `:feature:home:compileDebugKotlin` green (BOTH preview files compile with new params; one banner-visible preview added); ui-accessibility-reviewer pass; `:feature:home:lint` green. (satisfies #114 in-app-fallback + a11y; ux#5/#6; red-team 2a/2b/10d/10e)

## T-6 — Wire denial handling in HomeRoute
- after: T-2, T-3, T-5
- files: `feature/home/.../HomeRoute.kt`
- acceptance: GIVEN `HomeEffect.RequestNotificationPermission` WHEN `SDK_INT>=33` and `!hasRequestedBefore` THEN the route sets `requestInFlight`, launches `RequestPermission(Manifest.permission.POST_NOTIFICATIONS)` once, sets `hasRequestedBefore`; a later effect with the flag set does NOT relaunch. Launcher result clears `requestInFlight` and calls `refreshNotificationPermission()`. `LifecycleEventEffect(ON_RESUME)` refreshes only when `!requestInFlight`. `dismissedThisSession` (reset via `LaunchedEffect` keyed on `Connected.since`) resets on each fresh `Connected` entry. Banner shown iff `hasRequestedBefore && !notificationsGranted && !requestInFlight && !dismissedThisSession`; the `HomeScreen(...)` call is updated to pass `showNotificationBanner` + the two callbacks; "Open settings" launches `NotificationSettingsIntents.channelNotificationSettings(context, viewModel.notificationChannelId)`. `SDK_INT<33`→no dialog. `:feature:home` gains NO `project(":data:vpn")` dependency.
- check: `:feature:home:compileDebugKotlin` green; `:feature:home:dependencies` shows no `:data:vpn`; emulator smoke (grant path hides banner; deny path shows banner; dialog not relaunched on reconnect). (satisfies #114 "диалог после Connected; не перезапускается; permanent-deny→settings"; ux#1/#2/#3)

## T-7 — Verification pass
- after: T-6
- files: (no source changes) build/lint/test targets
- acceptance: THE SYSTEM SHALL pass `check` (build + Detekt/lint + unit + `checkModules`) across affected modules with the dependency-direction invariants intact.
- check: `/check` green; then hand off device acceptance (grant/deny/permanent-deny + mid-session channel-silence re-surface + shade Disconnect + TalkBack on banner AND Disconnect action on A13/14/15) to #133. (covers epic #126 DoD; L5 owned by #133)
