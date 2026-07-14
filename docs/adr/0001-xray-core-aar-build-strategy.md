# ADR 0001 — Xray-core Android AAR build & distribution strategy

- **Status:** Accepted
- **Date:** 2026-07-14
- **Deciders:** owner (solo)
- **Issue:** #59 (spike, epic #44 VPN core) — timebox 3 working days
- **Supersedes:** the `ndkVersion = "27.2.12479018"` pin written into #59/#72 cycle-2
- **Research:** `swarm-report/research/research-xray-aar-build-strategy.md`

## Context

VPNis needs an Xray-core Android binding to power the real `ConnectionController`
(`:data:vpn`, issues #60–#66). Xray-core is a Go project with **no Maven artifact**; the
Android binding (`libXray`) is produced with `gomobile bind`, which emits an `.aar`
(per-ABI `libgojni.so` + `classes.jar`). Two things make this the riskiest node of the
project and justified a dedicated spike:

1. **Reproducibility.** golang/go#40254 ("`gomobile bind` does not honor `-trimpath` /
   `-buildid=`") is **still OPEN** and confirmed to persist through Go 1.24.1 (issue #73097,
   2025). F-Droid's main repository requires bit-for-bit reproducible builds and **prohibits
   prebuilt `.aar`/`.jar` in `libs/`** — the recipe must run `gomobile bind` from source.
2. **API instability.** Upstream `XTLS/libXray` churns rapidly (e.g. the `Invoke` env field
   was removed and restored within one week; `SetTunFd` removed in favour of an
   `xray.tun.fd` config env). Pinning is mandatory.

The spike's binary decision rule (from #59): reproducible ourselves → build-in-CI; not within
3 days → adopt a proven F-Droid-compatible recipe (SaeedDev94/Xray); if that fails → fallback
channel (own signed APK via GitHub Releases + `AllowedAPKSigningKeys`).

## Decision

**Adopt the SaeedDev94/Xray recipe and build `XrayCore.aar` from source in CI**, pinned to a
specific commit, with the recipe's full toolchain pinned. Concretely:

| Item | Pinned value |
|---|---|
| Reference recipe | **SaeedDev94/Xray v12.3.0** — commit `7effa2b8bfac129141526e335775ede916f2c96e` (2026-05-23) |
| Binding | `SaeedDev94/libXray` fork (submodule at that commit) — frozen API `Test/Start/Stop/Version/Json` |
| Xray-core | v26.5.9 (wrapped by the fork) |
| hev-socks5-tunnel | v2.15.0 — commit `00c7eb9ad7ca381b0f1fee880abc1077fe9b93be` (ndk-build / Android.mk, **not** cmake) |
| Go toolchain | 1.26.3 |
| gomobile | `golang.org/x/mobile` pseudo-version `v0.0.0-20260520154334-0e4426e1883d` (pinned via go.sum) |
| **Android NDK** | **`29.0.14206865` (r29)** — set once in `AndroidCommon.kt` |
| `gomobile bind` invocation | `gomobile bind -o "$DEST/XrayCore.aar" -androidapi 26 -target "android/$TARGET" -ldflags="-buildid=" -trimpath` (one call per ABI ∈ {arm, arm64, 386, amd64}) |

**Reproducibility criterion outcome.** We did **not** attempt two independent bit-for-bit
builds locally (no Go/gomobile/NDK toolchain on the dev machine, and the sha256 dual-build is
a genuine multi-day effort that golang/go#40254 makes unreliable). Instead the spike resolves
by the decision rule using empirical third-party evidence: **SaeedDev94/Xray reproduces on
F-Droid's verification server for 48/58 versions (~83%), and every version from v11.7.0
(Oct 2025) through v12.3.0 passes.** Reproducibility is delivered by pinning the *entire*
toolchain and building from source — not by `-trimpath` alone. This satisfies the spike's
acceptance ("either sha256 of two builds matches, or fallback documented") via the documented,
independently-verified reproducibility of the adopted recipe, with the developer-signed
fallback below documented as the safety net.

**NDK supersession.** #59/#72 cycle-2 hardcoded `ndkVersion = "27.2.12479018"` (r27c). The
adopted recipe (and libXray upstream CI) use **r29 `29.0.14206865`**; hev's own CI uses r27d.
To keep the adopted recipe coherent, the project pins **r29**. This value is now the single
source of truth in `AndroidCommon.kt` and MUST be reused verbatim by #60 (hev NDK build) and
#72 (native CI job).

## Distribution channels

1. **Milestone-1 (now):** self-built `XrayCore.aar` from source in CI unblocks "first working
   connection". This does **not** depend on any F-Droid decision — epic #44 does not hang on
   this spike.
2. **F-Droid main (later):** achievable by mirroring SaeedDev94's from-source fdroiddata
   recipe (their recent versions reproduce 100%). Requires the recipe to build `gomobile bind`
   from source inside fdroiddata — **do not vendor the AAR in `libs/`** if this channel is
   wanted.
3. **Fallback (if F-Droid main reproducibility can't be met):** distribute our own
   developer-signed APK via **GitHub Releases**, referenced by F-Droid/IzzyOnDroid through the
   `Binaries:` + `AllowedAPKSigningKeys:` metadata pair (SaeedDev94 ships exactly this way).
   `AllowedAPKSigningKeys` is *mandatory alongside* `Binaries:` (fdroiddata MR #12911), not a
   reproducibility bypass. **Signing-key setup and the native CI job are deferred to #72.**

## Consequences

**Positive**
- Inherits a proven, F-Droid-reproducible toolchain and a stable binding API instead of
  re-deriving flags and chasing upstream churn.
- Single NDK literal (`AndroidCommon.kt`) prevents ABI mismatch across #60/#72; verified inert
  for non-native modules (`:app:assembleDebug` green with no NDK installed).
- Milestone-1 is decoupled from the F-Droid reproducibility question.

**Negative / follow-ups**
- Tracks an upstream community project's cadence; the SaeedDev94/libXray fork lags XTLS/libXray
  by ~7 weeks (Xray-core v26.5.9 vs v26.7.11). Revisit if we later track upstream directly.
- The from-source F-Droid recipe and signing keys are not yet built — **#72**.
- Verify at the first CI native build (#72): gomobile NDK side-by-side auto-detection for r29,
  and that Go 1.26.3 + current gomobile build cleanly (historical gomobile regressions on some
  Go versions).

## Alternatives considered

- **Follow XTLS/libXray upstream recipe directly** — freshest core (v26.7.11) and richer
  `Invoke(json)` API, but gomobile resolved to `@latest` (non-reproducible) and a volatile API.
  Rejected for M1; may revisit for core freshness later.
- **Vendor a prebuilt `XrayCore.aar` in `libs/`** — simplest for our own CI determinism but
  **rejected by F-Droid main-repo policy**; only viable for the developer-signed fallback.

## Acceptance (issue #59)

- [x] Decision (build/adopt/fallback) recorded in this ADR within the 3-day timebox → **adopt
      SaeedDev94/Xray recipe, build-in-CI, documented fallback**.
- [x] Reproducibility resolved: adopted recipe is independently reproducible on F-Droid
      (evidence cited); developer-signed fallback with `AllowedAPKSigningKeys` documented.
- [x] `ndkVersion` set in `AndroidCommon.kt` (`29.0.14206865`, single source of truth).
