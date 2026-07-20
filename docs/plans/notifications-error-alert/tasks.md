# Tasks: Error notification alert (issue #129)

> Plan: ./plan.md · Acceptance references issue #129's three checks as AC-1 (Error reflects reason
> via mapper, no dupes), AC-2 (interrupting alert: separate channel, dismissible, cooldown),
> AC-3 (Reconnecting deferred — verified by *absence*). Owner decisions: D1 (native-drop only, no
> Revoked alert), D2 (informational-only), D3 (RU now).

## T-1 — `NotificationContent.Error` + mapper + render guard
- after: none
- files: `data/vpn/src/main/kotlin/org/yarokovisty/vpnis/data/vpn/TunnelNotifications.kt`
- acceptance: GIVEN `VpnConnectionState.Error(reason)` WHEN `contentFor(state)` is called THEN it
  returns `NotificationContent.Error(reason)` (no longer `Inactive`); the `when` over
  `VpnConnectionState` stays exhaustive; `NotificationContent`/`contentFor` remain `internal`; no
  `Reconnecting` variant is added (AC-3). AND `render(NotificationContent.Error)` throws (hard
  `error(...)`) so `Error` can never be rendered on slot 1001.
- check: `TunnelNotificationsTest` asserts `contentFor(Error(r)) == NotificationContent.Error(r)` for
  each `ConnectionError`, and that `render`/`build` with an `Error` throws; `./gradlew
  :data:vpn:testDebugUnitTest`. (satisfies AC-1 mapper, AC-3)

## T-2 — Alert channel, copy resolver, `buildAlert` + strings (EN + RU)
- after: T-1
- files: `data/vpn/src/main/kotlin/org/yarokovisty/vpnis/data/vpn/TunnelNotifications.kt`,
  `data/vpn/src/main/res/values/strings_vpn.xml`,
  `data/vpn/src/main/res/values-ru/strings_vpn.xml` (create the `values-ru/` directory — it does
  not exist yet)
- acceptance: THE SYSTEM SHALL expose `ALERT_CHANNEL_ID = "vpnis_alerts"`,
  `ALERT_NOTIFICATION_ID = 1002`, `createAlertChannel(context)` (IMPORTANCE_DEFAULT),
  `alertTextFor(context, reason)` (exhaustive over `ConnectionError`; `Unknown`/`Revoked`/
  `PermissionDenied` → generic; **never** renders `Unknown.message`), and `buildAlert(context, reason)`
  returning a notification that is `autoCancel`, **not** `ongoing`, `CATEGORY_ERROR`, on
  `ALERT_CHANNEL_ID`, title `vpn_alert_title`, body from `alertTextFor`, with a content intent from
  `packageManager.getLaunchIntentForPackage` (no `:app` class reference) OR no content intent when
  that returns null. All `vpn_alert_*` keys exist in EN (`values/`) and RU (`values-ru/`).
- check: `TunnelNotificationsTest` (Robolectric) builds `buildAlert(...)` and asserts channel id ==
  `vpnis_alerts`, flags have no `FLAG_ONGOING_EVENT`, `autoCancel` set; asserts `createAlertChannel`
  yields a channel with `IMPORTANCE_DEFAULT` (unit-locks the heads-up-vs-silent epic-DoD contract,
  not just device #133); `alertTextFor(Unknown("x"))` does not contain "x"; grep shows no
  `androidx.activity`/`MainActivity` import in `:data:vpn`. RU completeness is falsified by grep, not
  lint (`MissingTranslation` is a non-error warning by default): the set of `vpn_alert_*` keys in
  `values/strings_vpn.xml` must equal the set in `values-ru/strings_vpn.xml`. `./gradlew
  :data:vpn:lint` still runs for ref resolution. (satisfies AC-2 channel, D2, D3)

## T-3 — Presenter: route Error → alert (1002) with dedup gate
- after: T-2
- files: `data/vpn/src/main/kotlin/org/yarokovisty/vpnis/data/vpn/TunnelNotificationPresenter.kt`
- acceptance: GIVEN the running presenter WHEN it observes `Connected` THEN it posts the ongoing
  notification to 1001 AND resets `alertPosted = false`; WHEN it observes `Error(reason)` and
  `alertPosted == false` THEN it posts `buildAlert` to slot 1002 (never touching 1001) and sets
  `alertPosted = true`; WHEN it observes a second `Error` before any `Connected` THEN no second
  alert is posted. `start()` SHALL reset `alertPosted = false`. `Inactive` states remain dropped by
  the existing filter; the `active` gate still guards both branches; the `onEach` router is an
  exhaustive `when` over `Connecting`/`Connected`/`Error` (a new content type is a compile error).
- check: `TunnelNotificationPresenterTest` (add `TunnelNotifications.createAlertChannel(context)` to
  `setUp()` alongside the existing `createChannel`) — (a) `Connected→Error` posts exactly one
  notification on channel `vpnis_alerts`/id 1002 and leaves slot 1001 untouched — assert via
  `Shadows.shadowOf(notificationManager).getNotification(ALERT_NOTIFICATION_ID)` non-null and
  `getNotification(NOTIFICATION_ID)` null — using `StandardTestDispatcher` (emit → advanceUntilIdle →
  assert → stop, proving ordering); (b) two consecutive distinct Errors → one alert; (c)
  `start→Error→stop→start→Error` → two alerts (reset-in-`start`). `./gradlew
  :data:vpn:testDebugUnitTest`. (satisfies AC-1 no-dupes, AC-2 cooldown)

## T-4 — Service: create the alert channel in `onCreate`
- after: T-3
- files: `data/vpn/src/main/kotlin/org/yarokovisty/vpnis/data/vpn/VpnTunnelService.kt`
- acceptance: THE SYSTEM SHALL call `TunnelNotifications.createAlertChannel(this)` in `onCreate`
  (alongside `createChannel`). `onRevoke` and the state machine are **unchanged** (D1).
- check: grep confirms `createAlertChannel` in `onCreate` and that `onRevoke` has no
  `onTunnelError` call; `./gradlew :data:vpn:assembleDebug`. (satisfies AC-2 channel setup, D1)

## T-5 — Quality gate
- after: T-4
- files: (verification only)
- acceptance: THE SYSTEM SHALL build, lint, and pass unit tests for `:data:vpn`; dependency-direction
  guards hold (`NotificationContent`/`contentFor`/`buildAlert` `internal`; no
  Activity/`ActivityResultLauncher` or `:app` import in `:data:vpn`); no `notify()` regression on
  slot 1001 (≤1/sec unchanged).
- check: `./gradlew :data:vpn:testDebugUnitTest :data:vpn:test :data:vpn:ktlintCheck
  :data:vpn:assembleDebug` all green; grep shows no `androidx.activity`/`MainActivity` in
  `:data:vpn/src/main`. (satisfies AC-1, AC-2, dependency guards)
