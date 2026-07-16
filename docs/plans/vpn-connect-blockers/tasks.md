---
type: tasks
slug: vpn-connect-blockers
date: 2026-07-16
review_cycle: 3
---

# Задачи — фикс #106 + #107 (ревизия после cycle-2 ревью)

T-1→T-3→T-4 трогают `VpnTunnelService.kt`/манифест/`TunConfig` — цепочка `after` выражает
последовательность машиночитаемо (T-3 after T-1; T-4 after T-1,T-3) во избежание конфликтов правок.

## T-1 — #106: тип FGS `systemExempted → specialUse`
**after:** —
**Файлы:** `data/vpn/src/main/AndroidManifest.xml`, `data/vpn/src/main/kotlin/.../VpnTunnelService.kt` (строка типа в `startForeground`, ~:336; лог-текст catch)

**Acceptance (THE SYSTEM SHALL):**
- Манифест: `uses-permission FOREGROUND_SERVICE_SPECIAL_USE`, НЕ `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`.
- `<service> android:foregroundServiceType="specialUse"` + дочерний
  `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="<человекочитаемая строка про VPN-туннель>"/>`.
- `startForeground` вызывается с `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.
- Неверный комментарий про «system-exempted at runtime» удалён/переписан.
- `lintDebug` зелёный по **обоим** правилам: `ForegroundServicePermission` И `SpecialPermission`
  (при появлении второго — точечный `tools:ignore="SpecialPermission"` на `<service>`).
- `<property>` присутствует в мёрженном манифесте `:app`.

**Проверка:**
- `grep -rn 'SYSTEM_EXEMPTED' data/vpn/src/main/ --include='*.xml' --include='*.kt'` → no matches.
- `./gradlew :data:vpn:lintDebug -Pvpnis.buildNative=true` зелёный. (Манифестные FGS-правила
  анализируют только XML, поэтому CI-шаг `lintDebug` без `-Pvpnis.buildNative` эквивалентен для них —
  флаг не обязателен для этой проверки.)
- `./gradlew :app:assembleDebug -Pvpnis.buildNative=true`, затем
  `find app/build/intermediates/merged_manifest -name AndroidManifest.xml | xargs grep -c PROPERTY_SPECIAL_USE_FGS_SUBTYPE` → ≥1
  (путь `merged_manifest/.../processDebugManifest/`, устойчиво к смене task-имени AGP).

## T-2 — #107: seam `VpnConsentChecker` + гейт согласия в `connect()`
**after:** —
**Файлы:** `+ VpnConsentChecker.kt`, `+ AndroidVpnConsentChecker.kt`, `ConnectionControllerImpl.kt`, `VpnModule.kt`

**Acceptance (Given/When/Then):**
- *Given* `isConsentRequired()==true`, *When* `connect(server)`, *Then* → `PermissionRequired`, `launcher.launch` НЕ вызван.
- *Given* `isConsentRequired()==false`, *When* `connect(server)`, *Then* → `Connecting`, `launch` вызван once.
- *Given* `PermissionRequired`, *When* повторный `connect` при согласии, *Then* → `Connecting` + launch.
- `AndroidVpnConsentChecker` = `VpnService.prepare(context) != null`; Intent наружу не отдаётся.
- Koin `vpnModule` биндит `VpnConsentChecker` и прокидывает в `ConnectionControllerImpl`.

**Проверка:** тесты T-5 зелёные; `./gradlew :data:vpn:test`.

## T-3 — IPv6 fail-closed (anti-leak): структурная правка `TunConfig` + family-aware `buildTun`
**after:** T-1
**Файлы:** `data/vpn/src/main/kotlin/.../TunConfig.kt`, `VpnTunnelService.kt` (`buildTun` :523-550), `data/vpn/src/test/kotlin/.../TunConfigTest.kt`

**Acceptance (THE SYSTEM SHALL):**
- `TunConfig` получает **отдельные** IPv6-поля (напр. `ipv6ClientAddress`, `ipv6PrefixLength` с
  валидацией `0..128`) ИЛИ типизированный список адресов; IPv4-валидация (`0..32`) НЕ ослаблена
  (разведены константы max-prefix v4/v6).
- `buildTun` добавляет IPv6-адрес интерфейса (ULA, напр. `fd00::1/128`) и маршрут `::/0`; фильтр
  маршрутов **family-aware** (для v6 верхняя граница prefix = 128) — валидный IPv6-маршрут не
  выбрасывается молча.
- IPv4-конфиг (адрес/маршрут/DNS) сохранён без изменений.

**Проверка:**
- `TunConfigTest`: IPv6-адрес `/128` конструируется без провала `require`; IPv4-валидация по-прежнему
  отклоняет prefix>32; `::/0` присутствует в маршрутах. (Smoke-инвариант структуры — НЕ доказательство
  fail-closed; истина «fail-closed» — device T-6.)
- `./gradlew :data:vpn:test` зелёный.

## T-4 — хардненинг nativeStart + backstop→Error + чистка sink
**after:** T-1, T-3
**Со-изменение с T-2:** обязателен вместе с T-2. Без T-4 гейт согласия из T-2 стоит, но серверная
ветка `establish()==null` всё ещё зовёт нелегальный `Connecting→PermissionRequired` → тихий тупик в
`Connecting`. Поэтому T-4 — не просто «хардненинг», а требуемое со-изменение к #107.
**Файлы:** `data/vpn/src/main/kotlin/.../VpnTunnelService.kt`, `TunnelStateSink.kt`, `ConnectionControllerImpl.kt`

**Acceptance (Given/When/Then + THE SYSTEM SHALL):**
- *Given* `Tun2SocksBridge.nativeStart` кидает `UnsatisfiedLinkError`/`Throwable`, *When* корутина hev,
  *Then* поймано, `onTunnelError(TunnelSetupFailed)`, туннель снесён (нет необработанного падения).
- Ветка `establish()==null` вызывает `onTunnelError(TunnelSetupFailed)` (легальный `Connecting→Error`),
  а НЕ `onPermissionRequired()`. В `VpnTunnelService` не остаётся ни одного вызова `onPermissionRequired()`.
- `onPermissionRequired()` удалён из интерфейса `TunnelStateSink` и его реализации в контроллере (ISP).
- Инвариант логов: значения `configJson`/`uuid`/`pbk`/`sid`/`sni`/`fp`/`host` не интерполируются в
  `Log.*` ни на одной ветке (scope включает `XrayConfigBuilder.kt`); `START_NOT_STICKY` сохранён.

**Проверка:**
- `grep -rn 'onPermissionRequired' data/vpn/src/main` → нет вызовов в `VpnTunnelService`; метод удалён из sink.
- Лог-инвариант credential (два шага):
  - интерполяция **значения**: `grep -rniE 'Log\.[a-z]+\([^)]*\$\{?(configJson|uuid|pbk|sid|sni|fp|host)\b(\}|[^.\w])' data/vpn/src/main` → no matches.
    (Логирование `${configJson.length}` — намеренно разрешено: длина, не значение; паттерн его не ловит.)
  - конкатенация вне string-template: ручная инспекция `XrayConfigBuilder.kt` и `VpnTunnelService.kt` —
    значения `uuid/pbk/sid/sni/fp/host/configJson` не попадают в `Log.*` через `+`/`append`.
- Компиляция `:data:vpn` зелёная.

## T-5 — юнит-тесты consent-гейта, отказа, backstop
**after:** T-2, T-4
**Файлы:** `data/vpn/src/test/kotlin/.../ConnectionControllerImplTest.kt`

**Acceptance (THE SYSTEM SHALL):**
- Добавлен `FakeVpnConsentChecker(var consentRequired)`; `makeController()` по умолчанию `consentRequired=false` (happy-path тесты не трогаются).
- Новые тесты: три Given/When/Then из T-2; **отказ** `PermissionRequired → disconnect() → Disconnected`;
  **повторный** connect при `prepare()` снова non-null → снова PermissionRequired, launch не зван.
- Существующие тесты, дергавшие удалённый `onPermissionRequired()` (`onPermissionRequired from
  Disconnected`, `connect after onPermissionRequired`), **переписаны** на драйв через
  `FakeVpnConsentChecker(consentRequired=true)` — поведение `Disconnected/Error → PermissionRequired`
  сохранено, источник — гейт, а не sink-callback.

**Проверка:** `./gradlew :data:vpn:test` — все зелёные; `grep onPermissionRequired data/vpn/src/test` → no matches.

## T-6 — верификация на устройстве (приёмка плана)
**after:** T-1, T-2, T-3, T-4, T-5
**Файлы:** — (без правок кода)

**Acceptance:**
- Пересобран подписанный native-APK через CI `workflow_dispatch` (`sign-and-publish` зелёный);
  подпись подтверждена `apksigner verify --verbose app-release.apk` (`Verified using v2 scheme`).
- Переустановлен на Pixel 7; в logcat при connect НЕТ `SecurityException`/краш-диалога.
- Первый connect: системный VPN-consent диалог показан (#107).
- #67: A1 переходы `Disconnected→Connecting→Connected`; A4/A5; B1–B4; C1–C3; D1–D3; E3. E1/E2 = N/A на Pixel.
- **A2 (реальный трафик, не индикатор):**
  - IPv4 egress-IP сменился с `37.2…` на нидерландский;
  - **IPv6 fail-closed доказан активно:** `adb shell` браузер на `test-ipv6.com` / генерация IPv6-трафика —
    соединение по IPv6 таймаутит/отказывает (fail-closed), реальный IPv6-адрес устройства НЕ виден;
  - **нет DNS-утечки:** `browserleaks.com/dns` (или аналог) — резолверы не принадлежат провайдеру мимо туннеля.
- В логах подтверждён активный `LibXrayCoreImpl` (не `NoOpXrayCore`).
- Проверено отсутствие петли/утечки при старте (protect-тайминг) — трафик не течёт до protect.

**Проверка:** обновлённый чек-лист в #67 с pass-отметками; найденные баги → новые issue, линк к #67.
