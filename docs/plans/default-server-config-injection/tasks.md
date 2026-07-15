# Tasks: Build-time injection of the default-server config into `:data:server`

> Plan: ./plan.md · No spec — acceptance derives from plan Verification & Sources + issue #56.
> Hard rule: NO task, test, or CI step may reference the real injected value. Use `vless://sentinel`
> only in local runs where no secret is set. Verification that runs in CI checks structure, not value.

## T-0 — Capture before-state baseline
- after: none
- files: (none — read-only)
- acceptance: THE SYSTEM SHALL record that, with no property/env/local.properties value set, the packaged config resolves to the placeholder URI, before any edit.
- check: run `./gradlew :data:server:compileTestKotlin` and record the current `DefaultServer.kt:18` placeholder URI in `progress.md` Learnings as the baseline.

## T-1 — Gradle codegen task + ValueSource + resource wiring
- after: T-0
- files: `data/server/build.gradle.kts`
- acceptance: GIVEN a value from any of (`-Pvpnis.defaultServerConfig`, env `VPNIS_DEFAULT_SERVER_CONFIG`, `local.properties` key `vpnis.defaultServerConfig`) in that precedence WHEN `:data:server:processResources` runs THEN `build/generated/vpnis/resources/org/yarokovisty/vpnis/data/server/default_server.config` contains that value verbatim (empty when none set — the `@OutputDirectory` + file are produced unconditionally, even blank, so lint/detekt compilation passes never fail on a missing resource). The task is a typed `DefaultTask` with an `@TaskAction` method (not `doLast {}`), declares `@Input`(resolved value) + `@OutputDirectory`, sets `outputs.cacheIf { false }`; `local.properties` is read via a `ValueSource` (no direct File I/O; `// TODO(project-isolation)` at the `rootProject` callsite); values read via `providers.*` only.
- check: `./gradlew :data:server:processResources -Pvpnis.defaultServerConfig=vless://sentinel` → the namespaced file exists and its content equals `vless://sentinel`; with no property the file is empty. THE SYSTEM SHALL NOT surface the value — after the run, grep the entire `build/` tree for `sentinel`: it appears ONLY in the generated `default_server.config`, nowhere in logs/reports/config-cache. Absent `local.properties` does not fail the build.

## T-2 — DefaultServer fallback + leak-free test
- after: T-1
- files: `data/server/src/main/kotlin/org/yarokovisty/vpnis/data/server/DefaultServer.kt`, `data/server/src/test/kotlin/org/yarokovisty/vpnis/data/server/DefaultServerTest.kt`
- acceptance: GIVEN the injected resource is blank/absent WHEN `DEFAULT_SERVER` initializes THEN `DEFAULT_SERVER.config == PLACEHOLDER_CONFIG`; GIVEN the resource carries a value THEN `DEFAULT_SERVER.config` equals it. `readInjectedConfig()` is `private`; it anchors the relative load on an **in-package anonymous object** (`object {}.javaClass.getResource("default_server.config")`) — NOT `DefaultServer::class` (no such class exists; the file defines a top-level `val`). Blank/missing → fallback. `PLACEHOLDER_CONFIG` is a `private const val`. New members carry explicit visibility.
- check: `./gradlew :data:server:test` green. New `DefaultServerTest` asserts ONLY `DEFAULT_SERVER.config.isNotBlank()` and `DEFAULT_SERVER.config.startsWith("vless://")` (holds for placeholder AND real — safe under a secret-bearing env, writes no credential to `build/test-results`). It MUST NOT assert equality to any real value. ktlint/detekt clean.

## T-3 — CI secret wiring (step-level, build job only) + JVM-test coverage
- after: T-2
- files: `.github/workflows/ci.yml`
- acceptance: THE SYSTEM SHALL expose `VPNIS_DEFAULT_SERVER_CONFIG` from `secrets` as a **step-level** `env:` on the `Assemble debug APK` step of the `build` job only; a run without the secret (fork PR / unset) SHALL still build and fall back to the placeholder. The workflow SHALL remain on `pull_request` (a comment forbids `pull_request_target`). The `test` job SHALL run `./gradlew testDebugUnitTest :data:server:test` so the pure-JVM `DefaultServerTest` (and `ServerRepositoryImplTest`) actually execute — plain `testDebugUnitTest` only compiles `:data:server`.
- check: yaml parses; the `env:` sits under the `Assemble debug APK` step (not job-level); grep confirms no other job/step references the secret; comment present forbidding `pull_request_target`; `test` step command includes `:data:server:test`; `build` job has no upload-artifact step. CI run green with `DefaultServerTest` shown as executed in the test report.

## T-4 — Full verification pass
- after: T-3
- files: (none — verification)
- acceptance: THE SYSTEM SHALL satisfy all L5 manual checks (no-secret only) and keep the repo clean.
- check: (a) no property → `DEFAULT_SERVER.config == PLACEHOLDER_CONFIG`; (b) `-Pvpnis.defaultServerConfig=vless://sentinel` → config carries sentinel; (c) whole-`build/`-tree grep: sentinel appears only where the resource is legitimately copied (generated file, `build/resources/main/`, processResources output, module jar) and NOT in any logs / test-reports / lint/detekt reports / `build/reports/configuration-cache/`; (d) VCS guard — `git check-ignore -v local.properties` confirms it is ignored, the generated resource is under gitignored `build/`, and `git grep -n 'vless://' -- ':!*/DefaultServer.kt'` across TRACKED files finds no real/sentinel value; `./gradlew build` + `:data:server:test` green; CI green (debugging-expert on any failure).
