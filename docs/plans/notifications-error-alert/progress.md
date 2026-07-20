# Progress: Error notification alert (issue #129)

> Plan: ./plan.md · Tasks: ./tasks.md

## Status
- [x] T-1 — `NotificationContent.Error` + mapper + render guard
- [x] T-2 — Alert channel, copy resolver, `buildAlert` + strings (EN + RU)
- [x] T-3 — Presenter: route Error → alert (1002) with dedup gate
- [x] T-4 — Service: create the alert channel in `onCreate`
- [x] T-5 — Quality gate

## Learnings
<!-- Append one line per completed task: surprises, gotchas, decisions taken during implementation. -->
- Owner decisions (review cycle 1): D1 alert only on native mid-session drop (no Revoked/onRevoke
  change) → removes the state-machine change and the one raced delivery path; D2 informational-only
  (launch intent, no Reconnect action); D3 RU copy now (values-ru for alert keys).
- T-1..T-5 implemented. Gate green: `:data:vpn:testDebugUnitTest`+`test`+`ktlintCheck`+`assembleDebug`
  all pass; dependency-direction grep clean (no Activity/:app in main); RU key parity holds.
- ktlint required a trailing comma before `->` in the multi-condition `when` branch of `alertTextFor`.
- Dedup tests distinguish alerts by body copy (same slot id 1002 overwrites, so call-count can't be
  read from ShadowNotificationManager) — first-reason-wins proves the gate; second-session-reason
  proves reset-in-start().
- CI Android Lint failed: this project promotes `MissingTranslation` to an ERROR (my plan assumed
  it was a non-error warning). A partial `values-ru` makes all 7 existing translatable
  `vpn_notification_*` strings "missing" in ru. Fix: translated the ongoing strings to RU too, so
  the locale is complete. Verified with `:data:vpn:lintDebug` (the exact CI task).
