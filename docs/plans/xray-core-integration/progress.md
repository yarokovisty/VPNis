# Progress: Real libXray-backed XrayCore integration (epic #44)

> Plan: ./plan.md · Tasks: ./tasks.md

## Status
- [x] T-1 — XrayConfigBuilder: VLESS/Reality URI → Xray JSON (kotlinx.serialization)
- [x] T-2 — XrayCore protector seam + config flow through the controller
- [x] T-3 — LibxrayApi seam + LibXrayCoreImpl (main, JVM-testable)
- [x] T-4 — XrayCoreProvider source-set seam + onVariants injection
- [x] T-5 — Consume XrayCore.aar under buildNative (flatDir in settings + transform)
- [x] T-6 — CI: native-build compiles :data:vpn against the real AAR + gate/integrity

## Learnings
- AGP 9 source-dir injection: the correct API is `Sources.getKotlin().addStaticSourceDirectory(String)` (non-nullable `SourceDirectories.Flat`), NOT the plan's assumed `addSrcDir` (which does not exist).
- CI submodule-SHA assert: the `data/vpn/libXray` submodule is pinned at `f6ce612…` (SaeedDev94/libXray), a DIFFERENT repo/commit from the SaeedDev94/Xray recipe `7effa2b8…` in ADR-0001 line 37. ADR line 38's "submodule at that commit" wording conflates them — asserted the true submodule SHA.
- kotlinx-serialization-json 1.9.0 already in the catalog; used element API (`buildJsonObject`/`parseToJsonElement`) only — no `@Serializable`, so no serialization compiler plugin / build-logic change.
- Unit-test JVM gotchas honored: `java.net.URI` + `java.util.Base64` (android.net.Uri / android.util.Base64 are stubbed under `isReturnDefaultValues=true`).
- detekt is CI reporting-only (`continue-on-error`); `ktlintCheck` is the blocking style gate. Refactored `XrayConfigBuilder` into `buildInternal` + `parseReality` to cut cyclomatic complexity; `@Suppress` (with justification, per codebase idiom) for the guard-clause `ReturnCount` and heterogeneous-catch cases.
- Local verification: `ktlintCheck` + `assembleDebug` (all modules) + 127 `:data:vpn` unit tests all green. Native path (RealLibxrayApi vs real AAR) is CI-only — proven by the native-build job.
