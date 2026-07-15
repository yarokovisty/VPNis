# Progress: Build-time injection of the default-server config into `:data:server`

> Plan: ./plan.md · Tasks: ./tasks.md

## Status
- [x] T-0 — Capture before-state baseline
- [x] T-1 — Gradle codegen task + ValueSource + resource wiring
- [x] T-2 — DefaultServer fallback + leak-free test
- [x] T-3 — CI secret wiring (step-level, build job only)
- [x] T-4 — Full verification pass

## Learnings
- T-0 baseline: with no property/env/local.properties set, `DefaultServer.kt:18` ships the placeholder URI `vless://00000000-0000-0000-0000-000000000000@nl1.vpnis.net:443?type=tcp&security=reality&pbk=PLACEHOLDER_PUBLIC_KEY&fp=chrome&sni=www.google.com&sid=PLACEHOLDER_SHORT_ID#VPNis%20NL`. After the change, the no-secret build must still resolve to exactly this string.
- T-1: codegen task + `LocalPropertiesValueSource` inline in `data/server/build.gradle.kts`; verified empty file (no prop) and `vless://sentinel` (with `-P`), ktlint/detekt green, no log leak. Deviation: used `parameters.rootDir = …` (modern `=` assignment) per project Gradle-style memory instead of `.set(...)`.
- T-2: `DefaultServer.kt` reads resource via in-package `object {}.javaClass.getResource(...)` (NOT `DefaultServer::class` — no such class); `ifBlank { PLACEHOLDER_CONFIG }`; no trim (verbatim). New `DefaultServerTest` asserts structure only (`isNotBlank`, `startsWith("vless://")`) — leak-free. Both tests green.
- T-3: `ci.yml` — step-level secret env on `Assemble debug APK` (build job only) + `pull_request_target` prohibition comment; `test` job now `testDebugUnitTest :data:server:test`.
- T-4: sentinel only in generated + `resources/main` copies (not reports/test-results); resource resets to 0 bytes; `local.properties` gitignored; no real value in tracked files. Gate: assembleDebug + lintDebug + testDebugUnitTest + :data:server:test + ktlintCheck all GREEN.
- Fix during /check: `ktlintKotlinScriptCheck` (build-script lint, not run by the T-1 agent) flagged the wrapped supertype at build.gradle.kts:22 — collapsed the class signature to one line.
