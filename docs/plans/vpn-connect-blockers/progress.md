---
type: progress
slug: vpn-connect-blockers
date: 2026-07-16
review_cycle: 3
---

# Прогресс — фикс #106 + #107

## Задачи
- [x] T-1 — #106: тип FGS `systemExempted → specialUse` (property + оба lint-правила + merge-check)
- [x] T-2 — #107: seam `VpnConsentChecker` + гейт согласия в `connect()`
- [x] T-3 — IPv6 fail-closed: структурная правка `TunConfig` (v6-поля) + family-aware `buildTun`
- [x] T-4 — nativeStart try/catch + backstop→`Error` + удаление `onPermissionRequired` из sink
- [x] T-5 — юнит-тесты гейта/отказа/backstop + миграция старых permission-тестов
- [~] T-6 — device-верификация: #106/#107 подтверждены исправленными на Pixel 7; полный прогон #67 (Connected/IPv6/DNS) заблокирован #109 (дефолт-сервер = placeholder, секрет не установлен)

## Learnings
- T-1: `specialUse` прошёл `lintDebug` без `tools:ignore` — второе правило `SpecialPermission` не загорелось (AGP 9.2 + `<property>` присутствует).
- T-2: presentation-проводка consent (`HomeRoute`/`HomeViewModel`) не тронута — реактивна к доменному `PermissionRequired`, который теперь эмитит гейт в `connect()`.
- T-3: единый `prefixLength 0..32` не выражал IPv6 — введены отдельные v6-поля + `MAX_IPV6_PREFIX_LENGTH=128`; фильтр маршрутов в `buildTun` family-aware по наличию ':' в адресе.
- T-4: `Connecting→PermissionRequired` нелегален → backstop `establish()==null` переведён на `onTunnelError(TunnelSetupFailed)`; `onPermissionRequired` удалён из sink (ISP).
- T-5: `makeController` дефолтит `FakeVpnConsentChecker(consentRequired=false)` — happy-path тесты не тронуты; старые permission-тесты мигрированы на гейт.
- Локально: `:data:vpn:test`, `ktlintCheck`, `lintDebug`, полный `test` — зелёные. Нативная компиляция и пересборка APK — на CI.
