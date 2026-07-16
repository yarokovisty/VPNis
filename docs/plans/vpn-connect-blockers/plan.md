---
type: plan
slug: vpn-connect-blockers
date: 2026-07-16
status: approved
spec: none
epic: "#44"
fixes: ["#106", "#107"]
found_by: "#67"
risk_areas:
  - "Android 14+ foreground-service type validation (FGS contract)"
  - "VpnService consent (prepare) ordering vs service start"
  - "IPv6 leak: traffic escaping the TUN with the device's real address"
  - "native JNI load failure (UnsatisfiedLinkError) on Dispatchers.IO"
  - "state-machine legality of the server-side PermissionRequired backstop"
review_verdict: PASS
review_cycle: 3
---

# План фикса: реальный VPN-туннель недостижим (#106 + #107)

> **История ревью.** Cycle 1 = FAIL. Правки по мультиэксперт-панели (architecture/security/build):
> IPv6-утечка внесена в скоуп; реордер `establish↔xray` **удалён** (текущий порядок безопаснее для
> protect-тайминга — подтверждено architecture-expert); ужесточён T-1 по `specialUse`
> (значение property, второе lint-правило); зафиксирована легальность серверного backstop
> `PermissionRequired`; «ложный Connected» задокументирован как security-оговорка.

## Context & Decision

Ручной QA #67 на Google Pixel 7 / Android 16 (API 36), подписанный **release-native** APK
(`-Pvpnis.buildNative=true`, реальный `ConnectionControllerImpl` через Koin) показал: первый же
`connect` крашит процесс, `Connected` недостижим. Найдены **два сцепленных блокера**, оба в `main`,
оба подтверждены независимо агентом `debugging-expert`:

- **#106** — `VpnTunnelService.startTunnel()` вызывает
  `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)`
  (`data/vpn/src/main/kotlin/.../VpnTunnelService.kt:332`). Тип `systemExempted` на API 34+
  требует, помимо `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, ещё одной из
  `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`/appop `android:activate_vpn`, либо system-exempted.
  Ни одной нет → `SecurityException`. Процесс умирает **не из-за пойманного исключения**, а из-за
  нарушения контракта `startForegroundService → startForeground`-за-5с: `catch`+`stopSelf()` не
  спасают, ActivityManager убивает процесс (краш-диалог).
- **#107** — `ConnectionControllerImpl.connect()`
  (`data/vpn/src/main/kotlin/.../ConnectionControllerImpl.kt:124-139`) не имеет consent-гейта:
  идёт сразу `transition(Connecting) → build → launcher.launch()`, никогда не эмитя
  `PermissionRequired`. Presentation-проводка consent **уже корректна и готова**
  (`feature/home/.../HomeRoute.kt:53-83` + `HomeViewModel.kt:87-104` деривят эффект
  `RequestVpnPermission` из доменного состояния `PermissionRequired`) — но это состояние драйвит
  **только** `FakeConnectionController` (`data/fake/.../FakeConnectionController.kt:117-128`).

**Сцепление:** нет consent → нет appop `activate_vpn` → `systemExempted` падает. Фиксим вместе.

Плюс QA-прогон и security-ревью выявили, что «первое рабочее подключение» обязано не течь мимо
туннеля: IPv6 сейчас уходит в обход TUN — это внесено в скоуп фикса (fail-closed), иначе приёмка A2
ложно зелёная.

## Technical Approach

### #106 — тип foreground-сервиса `systemExempted → specialUse`
- Манифест `data/vpn/src/main/AndroidManifest.xml`:
  - `uses-permission FOREGROUND_SERVICE_SYSTEM_EXEMPTED` → `FOREGROUND_SERVICE_SPECIAL_USE`;
  - `<service> android:foregroundServiceType="systemExempted"` → `"specialUse"`;
  - добавить дочерний `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"`
    `android:value="This VPN app tunnels user traffic through an encrypted proxy for privacy."/>`
    — **человекочитаемая строка** (Play-review инспектирует её вручную; значение должно совпасть с
    будущей Play declaration form; для self-distributed канала строка всё равно объявляется корректно);
  - переписать неверный комментарий (:32-37) про авто-квалификацию VpnService на system-exempted;
  - `tools:ignore="ForegroundServicePermission"` → пересмотреть. С `specialUse` старое требование
    exact-alarm снимается, но AGP-lint под Android 14 может поднять **отдельное** правило
    `SpecialPermission` (сервис `specialUse` без достаточного обоснования). Acceptance T-1 проверяет
    **оба** правила; при появлении `SpecialPermission` — заменить suppress на точечный
    `tools:ignore="SpecialPermission"` на `<service>`, а не оставлять без suppress.
- Код `VpnTunnelService.kt:336`: `FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` →
  `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`. `catch`-ветку оставить как backstop, лог-текст обновить.
- **minSdk 26:** атрибут `foregroundServiceType` и константа `TYPE_SPECIAL_USE` (добавлена в API 34)
  на API 26–33 ОС игнорирует — `SecurityException` не возникает, поведение корректно. Верификация на
  API 26–33 вне acceptance (скоуп — Pixel 7 / API 36).

### #107 — consent-гейт в реальном контроллере
- Новый интерфейс `VpnConsentChecker` в `data/vpn` (`fun isConsentRequired(): Boolean`).
- `AndroidVpnConsentChecker(context)` → `VpnService.prepare(context) != null`. **Intent
  отбрасывается** — берётся только boolean, поэтому I1 (Intent не покидает `feature/home`) не
  нарушается: `HomeRoute` независимо получает реальный Intent для диалога. `prepare()` идемпотентен —
  два независимых чтения (checker ради boolean vs HomeRoute ради Intent) не конфликтуют.
- Koin `vpnModule`: `single<VpnConsentChecker> { AndroidVpnConsentChecker(androidContext()) }`;
  `single { ConnectionControllerImpl(launcher = get(), consentChecker = get()) }`.
- `connect()` в самом начале: `if (consentChecker.isConsentRequired()) { transition(PermissionRequired); return }`
  — **до** `transition(Connecting)` и `launcher.launch()`. После grant `HomeRoute` шлёт
  `HomeIntent.Connect` повторно → `prepare()==null` → штатный путь.
- Переходы: `Disconnected/Error → PermissionRequired` и `PermissionRequired → Connecting` легальны
  (`isLegalTransition`), поэтому первый connect и Retry работают; отказ пользователя (`Cancel →
  disconnect()`) из `PermissionRequired` даёт `Disconnected` (легальный edge) — паритет с
  `FakeConnectionController`.

### Порядок в `startTunnel` — оставляем как есть (реордер отклонён)
Текущий порядок: `startForeground(1) → xrayCore.start(protector=this)(2) → buildTun/establish(3) →
networkMonitor(4) → hev(5) → onTunnelEstablished(6)` (`VpnTunnelService.kt:330-408`).
- **Решение: НЕ переупорядочивать** `xray↔establish`. Первоначальная идея (establish перед xray)
  отклонена по итогам architecture-ревью: она создаёт НОВОЕ окно утечки/петли — если `xray.start()`
  вернёт false/зависнет уже при поднятом TUN, сокеты старта Xray могут пойти через TUN до отработки
  protector. Текущий порядок безопаснее: на момент `xray.start()` TUN ещё не поднят, сокеты Xray
  идут напрямую, а `protect()`/`ProtectFd` регистрируется до старта прокси и действует, когда TUN
  поднимется. Менять рабочий порядок внутри blocker-фикса — лишний регрессионный риск.
- protect-тайминг (отсутствие петли/утечки) **проверяется на устройстве** в T-6; при обнаружении
  проблемы — отдельный issue с device-доказательством, а не спекулятивная перестановка сейчас.

### Серверный backstop `establish()==null` — легальность зафиксирована
Ветка `establish()==null` (`VpnTunnelService.kt:367-378`) сейчас зовёт `stateSink.onPermissionRequired()`.
Но при поднятом consent-гейте (T-2) сюда попадают только из состояния `Connecting`, а
`Connecting → PermissionRequired` **нелегален** (`isLegalTransition`) → транзишн молча дропнется, юзер
застрянет в `Connecting`. **Решение:** backstop должен вести в состояние с легальным ребром из
`Connecting` — `onTunnelError(TunnelSetupFailed)` (Connecting→Error легален), а НЕ `onPermissionRequired`.
Проактивный гейт в контроллере — основной рубеж; серверный backstop — восстановимая ошибка, не тупик.

### IPv6 fail-closed (внесено в скоуп по security-ревью)
`TunConfig` маршрутизирует только IPv4 `0.0.0.0/0` + IPv4 DNS (`TunConfig.kt:31-37`); IPv6 уходит мимо
туннеля с реальным адресом устройства. Цель — **anti-leak (fail-closed):** весь IPv6 заворачивается в
TUN (`::/0`); если Xray/hev IPv6-аплинк не несёт — IPv6 **падает закрыто** (таймаут/отказ), а не течёт.
Полный IPv6-transit — отдельный follow-up.

**Структурная правка (по architecture-ревью cycle-2):** текущая `TunConfig` несёт единственный
`prefixLength`, валидируемый `0..32` (`TunConfig.kt:40,64-65`), а `buildTun` фильтрует маршруты по той
же IPv4-константе `MAX_PREFIX_LENGTH=32` (`VpnTunnelService.kt:539`) — IPv6-адрес `/128` провалит
`init`, а prefix>32 у маршрута молча дропнется (= молчаливая утечка, не fail-closed). Поэтому T-3
обязан:
- ввести отдельные IPv6-поля (`ipv6ClientAddress`, `ipv6PrefixLength` c валидацией `0..128`) или
  типизированный список адресов; развести max-prefix для v4 (32) и v6 (128), НЕ ослабляя IPv4-валидацию;
- сделать фильтр маршрутов в `buildTun` **family-aware** (v6 → верхняя граница 128), чтобы валидный
  IPv6-маршрут не выбрасывался молча;
- IPv4-конфиг (адрес/маршрут/DNS) сохранить без изменений.
Юнит-тест (`TunConfigTest`) — smoke-инвариант структуры (адрес `/128` конструируется, IPv4-валидация не
ослаблена, `::/0` в списке маршрутов). Единственный источник истины «fail-closed» — device-проверка T-6
(активный `curl -6` реально блокируется).

### Хардненинг нативной загрузки (риск #3)
`Tun2SocksBridge.nativeStart` в `serviceScope.launch` (`VpnTunnelService.kt:398-402`) обернуть в
try/catch (`UnsatisfiedLinkError`/`Throwable`): при провале — `onTunnelError(TunnelSetupFailed)` +
teardown, вместо необработанного падения корутины уже после `Connected`.

### Инвариант обработки credential
`configJson` содержит ключи сервера. При правках `startTunnel`/`connect` **инвариант:** значения
`configJson`/`uuid`/`pbk`/`sid`/`sni`/`fp`/`host` не интерполируются в логи ни на одной ветке (scope
включает и `XrayConfigBuilder.kt`); `START_NOT_STICKY` сохраняется (не персистить credential-bearing
extra). Acceptance проверяет grep-ом отсутствие интерполяции этих значений в `Log.*`.

### Чистка контракта `TunnelStateSink` (по architecture-ревью)
После перевода серверного backstop на `onTunnelError` (T-4) метод `onPermissionRequired()`
(`TunnelStateSink.kt`) теряет единственного продюсера в сервисе — источником `PermissionRequired`
становится проактивный гейт в `connect()` через `transition` (T-2). Удалить `onPermissionRequired()` из
интерфейса `TunnelStateSink` (ISP: контракт не должен обещать неэмитируемое сервисом событие). Реализацию
в `ConnectionControllerImpl` (`:219`) — удалить/поглотить. Существующий тест
`onPermissionRequired from Disconnected` перенести на проверку проактивного гейта (T-5).

## Affected Modules & Files

| Path | Change type | Note |
|---|---|---|
| `data/vpn/src/main/AndroidManifest.xml` | edit | FGS тип/permission/property(значение); комментарий; оба lint-правила (#106) |
| `data/vpn/src/main/kotlin/.../VpnTunnelService.kt` | edit | FGS-тип; backstop→`onTunnelError`; try/catch nativeStart; лог-инвариант (#106,#3) |
| `data/vpn/src/main/kotlin/.../TunConfig.kt` | edit | отдельные IPv6-поля (v6-адрес, prefix 0..128) + маршрут `::/0`; family-aware (fail-closed) |
| `data/vpn/src/main/kotlin/.../TunnelStateSink.kt` | edit | удалить `onPermissionRequired()` (нет продюсера после T-4) |
| `data/vpn/src/main/kotlin/.../VpnConsentChecker.kt` | add | seam-интерфейс (#107) |
| `data/vpn/src/main/kotlin/.../AndroidVpnConsentChecker.kt` | add | `prepare()!=null`, Intent отбрасывается (#107) |
| `data/vpn/src/main/kotlin/.../ConnectionControllerImpl.kt` | edit | ctor `consentChecker`; гейт в начале `connect()` (#107) |
| `data/vpn/src/main/kotlin/.../VpnModule.kt` | edit | Koin-биндинг checker + прокид (#107) |
| `data/vpn/src/test/kotlin/.../ConnectionControllerImplTest.kt` | edit | fake checker + тесты гейта/отказа/backstop |
| `data/vpn/src/test/kotlin/.../TunConfigTest.kt` | edit | IPv6-маршрут/адрес присутствуют |

`feature/home` и `:app` **не меняются** — consent-проводка там уже корректна.

## Decisions Made

1. **`specialUse` вместо переупорядочивания под `systemExempted`.** Развязывает тип от appop
   `activate_vpn`; соответствует adopt-рецепту SaeedDev94/Xray (ADR-0001). `PROPERTY_..._SUBTYPE` —
   человекочитаемая строка, синхронная с Play declaration.
2. **Consent-гейт в контроллере, не в ViewModel/Route.** ViewModel деривит `RequestVpnPermission` из
   доменного `PermissionRequired`; `prepare()` заперт в `feature/home` (I1). Значит источник состояния
   обязан быть в домене. Seam `VpnConsentChecker` держит Context вне контроллера (как `TunnelLauncher`).
3. **Checker берёт только boolean, Intent отбрасывает** — I1 не нарушается, `prepare()` идемпотентен.
4. **Порядок `startTunnel` НЕ меняем.** Реордер `establish↔xray` создаёт новое окно утечки/петли
   (architecture-ревью); текущий порядок безопаснее для protect-тайминга. Проверяем на устройстве.
5. **Серверный backstop `establish()==null` ведёт в `Error`, не `PermissionRequired`** — иначе
   нелегальный `Connecting→PermissionRequired` дропнется и юзер застрянет в Connecting.
6. **IPv6 fail-closed — в скоуп, со структурной правкой `TunConfig`.** VPN, текущий по IPv6, не
   «рабочий». Требуется family-aware модель адресов/маршрутов (v6 prefix 0..128), не ослабляющая
   IPv4-валидацию; fail-closed доказывается на устройстве (`curl -6` блокируется). Полный
   IPv6-transit — follow-up.
7. **Удалить `onPermissionRequired()` из `TunnelStateSink`** (ISP) — после T-4 у него нет продюсера;
   `PermissionRequired` эмитит проактивный гейт в `connect()`.

## Risks & Mitigations

| Риск | Митигация |
|---|---|
| `specialUse` ведёт себя иначе для VpnService-нотификации | Device B1/B2: уведомление + кнопка «Отключить» работают |
| Новое lint-правило `SpecialPermission` валит CI | T-1 проверяет оба правила; при появлении — точечный `tools:ignore="SpecialPermission"` |
| Значение `PROPERTY_..._SUBTYPE` расходится с Play declaration | Risk-строка: строка = будущая Play-декларация; человекочитаемая |
| Consent-гейт ломает юнит-тесты | `makeController()` по умолчанию — fake-checker «согласие есть» |
| IPv6 fail-closed рвёт IPv6-связность | Приемлемо для anti-leak (fail-closed ≠ leak); полный transit — follow-up; device-проверка A2 по IPv4 И отсутствие IPv6-утечки |
| `<property>` не смёржится в `:app`-манифест | T-1: проверить `merged_manifests/debug/AndroidManifest.xml` содержит property |
| «Ложный Connected» вводит в заблуждение о защите | Задокументировано (Out of Scope); device A2 — проверка по фактическому трафику, не индикатору |

## Verification & Sources

**Источники истины «done» (собраны и достаточны):**
- **QA чек-лист #67** (бинарные A1–E3) — функциональная приёмка.
- **Debug-repro** — logcat `.qa-tmp/logcat_smoke.log`: `SecurityException` systemExempted + отсутствие
  consent-диалога. После фикса эти строки исчезают.
- **Before-state baseline** — egress-IP до VPN зафиксирован (`37.2…`); после `Connected` IPv4-IP →
  нидерландский, **и** IPv6 не выдаёт реальный адрес устройства.

**Стратегия тестирования (уровни пирамиды):**
- **L0 unit** — `ConnectionControllerImplTest`: гейт (required→PermissionRequired, launch не зван;
  not-required→Connecting+launch; PermissionRequired→connect→Connecting; **отказ**
  PermissionRequired→disconnect→Disconnected; **повторный** connect при `prepare()` снова non-null →
  снова PermissionRequired). `TunConfigTest`: IPv6-адрес и маршрут `::/0` присутствуют.
- **L1 build** — `:data:vpn:assembleDebug -Pvpnis.buildNative=true`, `lintDebug` (оба FGS-правила),
  `:data:vpn:test` зелёные; подписанный native-APK через CI `workflow_dispatch` (`sign-and-publish`),
  проверка подписи `apksigner verify`.
- **L5 manual (устройство)** — переустановка на Pixel 7, #67: A1 переходы; A2 **реальный трафик**
  (IPv4-IP сменился + отсутствие IPv6-утечки, `test-ipv6.com`/аналог), не только индикатор; A4/A5;
  B1–B4; C1–C3; D1–D3; E3. Подтвердить активный `LibXrayCoreImpl` (не `NoOpXrayCore`) в логах.
  Проверить отсутствие петли/утечки при старте (protect-тайминг). E1/E2 = N/A на Pixel.

## Out of Scope (follow-up issue'ы)

- **«Ложный `Connected`»** — `onTunnelEstablished()` зовётся сразу после запуска hev-петли
  (`VpnTunnelService.kt:407`), без сигнала реальной связности. До закрытия follow-up `Connected` **не
  является security-гарантией**; QA проверяет A2 по фактическому трафику. Отдельный issue (нужен
  probe/сигнал связности от hev/Xray; опционально — не показывать «защищён» до probe).
- **Полный IPv6-transit** — здесь только fail-closed (anti-leak); маршрутизация IPv6 через Xray-аплинк
  — отдельный issue.
- **Credential в Intent-extra** (known-limitation, не регрессия) — `EXTRA_CONFIG_JSON` проходит через
  ActivityManager и может осесть в системных Intent-дампах. Приемлемо для Milestone-1 (`START_NOT_STICKY`
  не персистит); follow-up — передавать config через in-process holder/Koin, раз сервис и контроллер в
  одном процессе.
- Живые traffic-счётчики (#69), Room-CRUD (#70), ping (#71) — вне Milestone-1.

## Open Questions

_Нет блокирующих. Дизайн-развилки (реордер, IPv6-скоуп, backstop-легальность) разрешены по итогам
мультиэксперт-ревью и зафиксированы в Decisions._
