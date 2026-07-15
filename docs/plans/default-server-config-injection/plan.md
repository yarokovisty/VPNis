---
type: plan
slug: default-server-config-injection
date: 2026-07-15
status: approved
spec: none
risk_areas: []
review_verdict: pass
review_blockers: []
---

# Plan: Build-time injection of the default-server config into `:data:server`

## Context & Decision
The operator default server (`data/server/.../DefaultServer.kt:18`) currently ships a **placeholder**
VLESS/Reality URI (zeroed UUID, `PLACEHOLDER_PUBLIC_KEY`, `PLACEHOLDER_SHORT_ID`). Xray starts on it
but the Reality handshake fails — no live tunnel. This is a deliberate placeholder (issue #56):
real credentials must never be committed (open repo + F-Droid channel must stay clean, ADR 0001).

Decision (already made, this plan is the HOW): let an **operator build** substitute the real
default-server config at build time via a secret, keeping the placeholder as fallback. Open /
F-Droid build (no secret) → placeholder; operator build (local or CI) → real credentials. Part of
epic #44 / issue #56; unblocks #67 (device QA).

## Technical Approach
`:data:server` is a **pure-JVM module** (`vpnis.jvm.library` → `org.jetbrains.kotlin.jvm`, under
`explicitApi()`, Java 11 target — see `build-logic/convention/src/main/kotlin/vpnis.jvm.library.gradle.kts`).
It has **no AGP `BuildConfig`** (that is an Android-library feature). So injection is done by a
build-time Gradle task that generates a **resource file** (not a Kotlin source), keeping the module
free of AGP and avoiding Kotlin-source escaping of the URI and ktlint/detekt runs over generated
`.kt`.

### Value resolution (config-cache-safe)
A single provider chain resolves the config value, evaluated lazily so it integrates with the Gradle
configuration cache (project is on Gradle 9; config cache is currently OFF but the design must be
cache-clean so enabling it later doesn't break):

```
val injected = providers.gradleProperty("vpnis.defaultServerConfig")
    .orElse(providers.environmentVariable("VPNIS_DEFAULT_SERVER_CONFIG"))
    .orElse(providers.of(LocalPropertiesValueSource::class) { parameters.rootDir.set(rootProject.layout.projectDirectory) })
    .orElse("")
```

- The `local.properties` read is done through a custom `ValueSource<String, Params>`
  (`LocalPropertiesValueSource`) — **not** direct `File` I/O in the task/config block. Arbitrary file
  reads in a config-cache world must go through a `ValueSource`, otherwise Gradle either serializes
  raw bytes into the cache entry or throws a violation. The value source reads
  `<rootDir>/local.properties` if present (absent → returns null; never fails the build) and returns
  the `vpnis.defaultServerConfig` key.

### Codegen task
**Class location:** both `LocalPropertiesValueSource` (an `abstract class : ValueSource<String, Params>`)
and `GenerateDefaultServerConfigTask` (an `abstract class : DefaultTask()` with an `@TaskAction`
method) are declared **inline in `data/server/build.gradle.kts`** as script-local abstract classes —
Gradle Kotlin DSL supports this for single-module `ValueSource`/task types, and they are used via
`providers.of(LocalPropertiesValueSource::class) { … }` and
`tasks.register<GenerateDefaultServerConfigTask>("generateDefaultServerConfig") { … }`. (They are
NOT importable from `build-logic/convention` — convention-module classes are not on a consumer
script's classpath — and buildSrc is unnecessary for one module.)

`generateDefaultServerConfig`, declared with **explicit inputs/outputs** so up-to-date checks, cache
keys, and cross-job cache safety are correct:

- `@Input val configValue: Property<String>` ← wired to the `injected` provider above. Because the
  resolved value is a declared task input, the task's (and every downstream task's) cache key varies
  with the value: a blank-value build and a real-value build get **distinct** cache keys and can
  never restore each other's output.
- `@OutputDirectory val outputDir: DirectoryProperty` ← `build/generated/vpnis/resources`. The
  `@TaskAction` resolves the target file at
  `outputDir/org/yarokovisty/vpnis/data/server/default_server.config`, **creates the parent directory
  tree (`parentFile.mkdirs()`)**, and **always writes the file** with the resolved value **verbatim**
  — even when blank it writes an (empty) file, so `processResources` always has content to copy and
  the lint/detekt compilation passes never see a missing resource. `readInjectedConfig()` treats an
  empty file the same as absent (→ fallback).
- `outputs.cacheIf { false }` — the task is sub-millisecond; belt-and-suspenders so the generated
  file itself is never stored in the shared build cache regardless of key correctness. (Downstream
  `processResources`/`jar` remain cacheable and correctly keyed on the file **content**.)
- The file-write lives in an `@TaskAction`-annotated method on a typed `DefaultTask` subclass (not a
  `doLast { }` closure) to keep the task configuration-cache serializable.
- The `LocalPropertiesValueSource` takes the root dir via its `Params` (`rootDir` `DirectoryProperty`).
  At the callsite `parameters.rootDir.set(rootProject.layout.projectDirectory)` is a cross-project
  reference — legal today (project isolation off) but a future project-isolation incompatibility; add
  a `// TODO(project-isolation): replace rootProject reference` marker at the callsite so it's traceable.

Wired via `sourceSets["main"].resources.srcDir(generateDefaultServerConfig.flatMap { it.outputDir })`
so `processResources` establishes the dependency automatically and adds the correct resource **root**
(a directory, not a single file). The namespaced file therefore lands on the classpath at
`org/yarokovisty/vpnis/data/server/default_server.config`.

Values are read only through `providers.*`/`ValueSource` and never printed. The CI path uses the
**env var** (not `-P`) precisely because `-Pvpnis.defaultServerConfig=…` would appear in the Gradle
command line captured by build scans and process listings; the env path does not.

### `DefaultServer.kt`
- Extract the current placeholder URI into a `private const val PLACEHOLDER_CONFIG: String`.
- Add `private fun readInjectedConfig(): String`. **Resource anchor caveat:** `DefaultServer.kt`
  defines only a top-level `val` — there is **no class named `DefaultServer`** (top-level members
  compile into the `DefaultServerKt` file facade), so `DefaultServer::class` does not exist. Anchor
  the relative load on an **anonymous object in this package** instead:
  `object {}.javaClass.getResource("default_server.config")` — the anonymous class lives in
  `org.yarokovisty.vpnis.data.server`, so the relative name resolves to
  `org/yarokovisty/vpnis/data/server/default_server.config` (the namespaced path the task writes).
  Read its text (UTF-8); return `""` when the resource is null or blank. Relative loading + the
  namespaced path make a cross-module classpath collision impossible.
- `config = readInjectedConfig().ifBlank { PLACEHOLDER_CONFIG }`.
- New members carry explicit visibility (`private`); `explicitApi()` requires it on public/internal
  API (private members are exempt, but keep the explicit `String` return type on `readInjectedConfig`
  for clarity).

### CI (`.github/workflows/ci.yml`)
- Add `env: VPNIS_DEFAULT_SERVER_CONFIG: ${{ secrets.VPNIS_DEFAULT_SERVER_CONFIG }}` at **step level**
  on the `Assemble debug APK` step of the `build` job only (narrower than job-level `env:`, which
  would expose the value to the checkout/JDK/Gradle-setup steps and any third-party action running
  there). Fork PRs get no secret → blank → placeholder.
- The `build` job runs `assembleDebug` and does **not** upload the APK — so no credential leaves CI
  via an artifact today. Recorded invariant (see Risks): **no secret-bearing job may upload its
  build artifact** unless that artifact is the intended signed operator release (out of scope here).
- The workflow **must remain on `pull_request`** (not `pull_request_target`) — the latter would run
  untrusted fork code with the secret in scope. Recorded as a prohibition.
- **CI `test` job invocation must be extended.** The job runs `./gradlew testDebugUnitTest`, which is
  an **AGP variant task**: it *compiles* `:data:server` (verified via `--dry-run`:
  `:data:server:compileKotlin/processResources/classes/jar` run) but does **not** execute the
  pure-JVM `:data:server:test` task — so neither the existing `ServerRepositoryImplTest` nor the new
  `DefaultServerTest` currently runs in CI. Change the step to
  `./gradlew testDebugUnitTest :data:server:test` so the no-secret fallback test actually runs and
  gates. (The same latent gap affects other pure-JVM modules, e.g. `:core:domain` — see Out of Scope.)
- `test` (no secret) stays green on the placeholder; `lint` / `static-analysis` / `detekt` are left
  untouched but **transitively invoke `generateDefaultServerConfig`** via their compilation passes
  with no secret → blank resource → fallback. The task must therefore register its `@OutputDirectory`
  and produce the (possibly-empty) file **unconditionally** on a clean environment, so those jobs
  never fail with a confusing "missing generated resource" error.

Integration points (from investigation):
- `DEFAULT_SERVER` is a top-level `val` (`DefaultServer.kt:18`), consumed by `ServerRepositoryImpl`
  (`ServerRepositoryImpl.kt:28,31`) and `ServerRepositoryImplTest`. The tests compare against
  `DEFAULT_SERVER` **itself**, so they are independent of the `config` value — injection cannot break
  them.
- New Gradle property name `vpnis.defaultServerConfig` follows the existing dot-style convention
  (`-Pvpnis.buildNative=true`, ci.yml:279).

## Affected Modules & Files
| Path | Change | Note |
|---|---|---|
| `data/server/build.gradle.kts` | Modified | Register `generateDefaultServerConfig` (typed `@Input`/`@OutputDirectory`, `cacheIf { false }`); add `LocalPropertiesValueSource`; wire generated dir into `main` resources. |
| `data/server/src/main/kotlin/org/yarokovisty/vpnis/data/server/DefaultServer.kt` | Modified | Extract `private const PLACEHOLDER_CONFIG`; add `private fun readInjectedConfig()` (relative resource load); `config = readInjectedConfig().ifBlank { PLACEHOLDER_CONFIG }`. |
| `data/server/src/test/kotlin/org/yarokovisty/vpnis/data/server/DefaultServerTest.kt` | New | Fallback assertions that leak nothing (no reference to the real value) — see Verification. |
| `.github/workflows/ci.yml` | Modified | Step-level `env: VPNIS_DEFAULT_SERVER_CONFIG` on `Assemble debug APK` (build job only). |
| `data/server/src/test/.../ServerRepositoryImplTest.kt` | Unchanged | Verified independent of `config` value; do not alter semantics. |

## Decisions Made
| Decision | Rationale | Alternatives rejected |
|---|---|---|
| Build-time codegen into a **resource file** | Keeps `:data:server` pure-JVM; no URI escaping into Kotlin source; ktlint/detekt don't lint resources; `processResources` auto-wires the task dependency. | **AGP `BuildConfig`** — requires converting the module to `android.library`, breaking the deliberate JVM purity. **Generated Kotlin `const` (Base64)** — bulletproof on escaping but adds a runtime decode and needs manual ktlint/detekt task-ordering for generated `.kt`. |
| Task declares `@Input`(value) + `@OutputDirectory`, and `cacheIf { false }` | Makes cache keys vary with the value (distinct blank vs real entries → no cross-job/fork restore of a cached credential); disabling task caching is extra insurance; a task with no declared I/O has no up-to-date/cache semantics at all. | Untyped `doLast { }` writing the file — breaks up-to-date checks and lets a stale/foreign cache entry be reused. |
| `local.properties` via a `ValueSource`, not direct `File` I/O | Only config-cache-safe way to feed a file read into a provider chain on Gradle 9. | `rootProject.file(...).readText()` in config/action — config-cache violation / serialized bytes. |
| Namespaced resource path + **relative** `getResource` (anchored on an in-package anonymous object, since there is no `DefaultServer` class) | `org/yarokovisty/vpnis/data/server/default_server.config` loaded relative to the package — collision with any other module's resource is impossible. | Flat `default_server.config` at classpath root — first-match-wins collision risk across JVM modules. |
| Value precedence: gradle prop → env → `local.properties` → blank; **env** used in CI | Covers CI (env from secret, not on the command line), local dev (`local.properties`, gitignored), clean open build (blank → placeholder). `-P` avoided in CI to keep the value out of build scans/process listings. | Env-only — no local override. `-P` in CI — value visible in scans/process listing. |
| Secret injected at **step level** on `Assemble debug APK`, `build` job only | Minimal exposure surface — only the Gradle step that consumes it, not setup steps/third-party actions; other jobs don't need it. | Job-level `env:` — exposes to every step. Injecting into all jobs — needless secret spread. |
| Property name `vpnis.defaultServerConfig` | Matches the existing `vpnis.buildNative` dot-style convention. | `VPNIS_DEFAULT_SERVER_CONFIG` as gradle prop — inconsistent (kept only as the env/secret name). |

## Risks & Mitigations
| Risk | Severity | Mitigation |
|---|---|---|
| Cached generated resource / jar carrying a real credential restored by a no-secret `test` or fork-PR job (cache = exfiltration vector) | critical | Resolved value is a declared `@Input` → distinct cache key for blank vs real, so a no-secret job never restores the secret-bearing entry; plus `cacheIf { false }` on the generate task. `test` job is `cache-read-only` on PRs. |
| Real credential leaked into uploaded CI test/report artifacts | critical | Forbid any test/assertion that references the real injected value; the fallback unit test asserts only structure (`isNotBlank()`, `startsWith("vless://")`) which holds for both placeholder and real and reveals nothing. Sentinel (`vless://sentinel`) used only in **local** runs where no secret is set. |
| Real credential leaks into build logs / build scan / config-cache report | major | Read only via `providers.*`/`ValueSource`; never `println`/`logger` the value; CI uses env (not `-P`); never run `--scan` on a secret-bearing build (documented). |
| Generated resource missing/stale at runtime | minor | `readInjectedConfig()` treats missing/blank as fallback → placeholder; `srcDir(taskProvider.flatMap { outputDir })` makes `processResources`/`test`/`jar` depend on generation. |
| Placeholder accidentally dropped, breaking open build | major | Keep `PLACEHOLDER_CONFIG` in-source + `ifBlank { … }`; automated no-secret unit test asserts `config` is a non-blank `vless://` URI (fallback path exercised in the `test` job). |
| Operator release silently ships the placeholder (secret unset/misspelled) | minor | **Deferred** to the release-build milestone (out of scope — this PR has no release job): an opt-in strict mode `-Pvpnis.requireDefaultServerConfig=true` that fails the build when the resolved value is blank. When implemented, its failure message must state only that the value is blank/missing — never echo the resolved config. Not implemented in this PR. |
| `local.properties` presence coupling | minor | `ValueSource` returns null when the file is absent → falls through to blank; never fails the build. |
| Workflow switched to `pull_request_target` in future → secret exposed to fork code | major | Recorded prohibition in the plan and a comment at the CI `env:` site: this secret requires `pull_request`; `pull_request_target` is forbidden. |

## Verification & Sources
The finished change is verified against the FR-50 default-server contract plus the two build-mode
invariants (open build → placeholder; operator build → injected value) — with the hard constraint
that **verification must never surface the real value in any CI-uploaded sink**.

| Source of truth | Type | Status | Sufficient for verification? |
|---|---|---|---|
| Task description / issue #56 acceptance (open → placeholder; operator → injected; no secret in VCS) | requirements | present | yes — concrete pass/fail per build mode. |
| `ServerRepositoryImplTest` (existing) | test-plan | present | yes — proves the `DEFAULT_SERVER` seam still resolves; repository behaviour unchanged. |
| `DefaultServerTest` (new, leak-free) | test-plan | to-author-in-T-2 | yes — automates the no-secret fallback path in the `test` job without referencing any real value. |
| Before-state: current build (no property) produces the placeholder URI | before-state baseline | to-capture-before-impl | yes — capture `:data:server:processResources` output + placeholder path before edits, so "no secret ⇒ placeholder" is proven not just asserted. |

**Automated no-secret assertion (replaces the leaky T-2 idea):** `DefaultServerTest` (runs in the
no-secret `test` job **via the extended `./gradlew testDebugUnitTest :data:server:test` invocation** —
see CI section; plain `testDebugUnitTest` would not execute it) asserts
`DEFAULT_SERVER.config.isNotBlank()` and
`DEFAULT_SERVER.config.startsWith("vless://")`. Both hold for placeholder **and** real, so the test
is safe in a secret-bearing environment too and never writes a credential into `build/test-results`.
The exact placeholder equality is checked only in the **local** L5 run below (no secret set).

**Test-report leak, second-order:** `ServerRepositoryImplTest` uses `assertEquals(DEFAULT_SERVER, …)`;
on failure JUnit prints `Server.toString()` (a data class → includes `config`) into
`build/test-results/*.xml`, which the `test` job uploads. This is **not** a leak because the `test`
job has no secret in scope, so `config` is always the placeholder there — the real value never exists
where tests run or reports are uploaded. (No change needed; recorded so the assumption is explicit.)

**Testing strategy (pyramid levels):** L0 build always + L1 static (ktlint/detekt stay green; no
generated `.kt`) + L2 unit (`:data:server:test` — existing tests unchanged + new leak-free
`DefaultServerTest`) + L5 manual, no-secret local runs only:
(a) no property → generated resource blank → `DEFAULT_SERVER.config == PLACEHOLDER_CONFIG`;
(b) `-Pvpnis.defaultServerConfig=vless://sentinel` → generated resource + `config` carry the sentinel;
(c) after (b), scan the whole `build/` tree — the sentinel appears **only** where the resource is
legitimately expected: the generated file plus its normal copies (`build/resources/main/`, the
`processResources` output, and the module `jar`). It must **NOT** appear in any
logs / test-reports / lint/detekt reports / `build/reports/configuration-cache/` — that is the leak
check (the earlier "only in one file" wording was wrong: `processResources`/`jar` copy the resource by design);
(d) `git check-ignore -v local.properties` confirms it is ignored, and `git status` is clean — no
real value committed (the generated resource lives under the gitignored `build/`).
L5 is mandatory here because this is an infra-layer (build/DI/config) change; these are the L5
coverage. The real credential is exercised only by the operator, never in CI.

## Out of Scope
- Remote-config delivery of the default server (future milestone — not now).
- Manual `vless://` import (issue #74) — a separate feature, not a replacement for the FR-50 default.
- Controller swap (#66).
- Providing / rotating the actual production credentials, and any **release** build/upload job
  (`assembleRelease`/signing) — operator concern; this plan wires only the seam and injects into the
  existing debug `build` job. The "no secret-bearing job uploads its artifact unless it's the signed
  release" invariant is recorded so the future release job honours it.
- The duplicate `DEFAULT_SERVER` placeholder in `:data:fake` `FakeServerRepository` — left as-is.
- **Broader CI JVM-test coverage.** This plan extends the `test` job only enough to run
  `:data:server:test`. The same latent gap (`testDebugUnitTest` not running pure-JVM module tests)
  affects `:core:domain` and any other JVM module; a follow-up should switch the job to aggregate all
  `test` tasks (or add them explicitly). Discovered here, tracked separately to keep this PR scoped to #56.

## Open Questions
- [non-blocking] Namespaced resource path is fixed at `org/yarokovisty/vpnis/data/server/default_server.config`; revisit only if a future module ships the same relative name (collision already prevented by package-relative loading).
