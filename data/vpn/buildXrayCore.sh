#!/usr/bin/env bash
#
# Build XrayCore.aar — the gomobile binding of the pinned SaeedDev94/libXray
# submodule (ADR 0001). Runs ONLY on the native-build CI agent (#72), which has
# Go 1.26.3, gomobile (pinned) and NDK r29 installed. Never runs in the normal
# Android/Gradle build — the AAR is not consumed as a dependency yet (#66).
#
# Why a throwaway wrapper module (mirrors SaeedDev94/Xray's buildXrayCore.sh):
# libXray's own go.mod does NOT require golang.org/x/mobile, so `gomobile bind`
# cannot run inside the submodule directly. We stand up a tiny module that
# replaces github.com/xtls/libxray with the local submodule, pulls a pinned
# golang.org/x/mobile, and binds the libXray package from there. GOFLAGS=-mod=mod
# lets `go`/gomobile populate go.sum from the module cache instead of failing on
# a missing checksum (the wrapper's go.sum is generated per run, never vendored).
#
# Reproducibility (ADR 0001) comes from pinning the whole toolchain — the libXray
# submodule commit (which pins Xray-core via its own go.sum), Go, gomobile, NDK —
# plus -ldflags=-buildid= -trimpath below, NOT from a committed wrapper go.sum.
#
# Usage: GOMOBILE_VERSION=<pseudo-version> ./buildXrayCore.sh [output-aar]
set -euo pipefail

: "${GOMOBILE_VERSION:?set GOMOBILE_VERSION (pinned golang.org/x/mobile pseudo-version)}"

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"   # data/vpn
LIBXRAY_DIR="$MODULE_DIR/libXray"
OUT_AAR="${1:-$MODULE_DIR/libs/XrayCore.aar}"
WORK_DIR="$MODULE_DIR/build/xraybind"

if [ ! -f "$LIBXRAY_DIR/go.mod" ]; then
  echo "ERROR: libXray submodule not initialized at $LIBXRAY_DIR (git submodule update --init?)" >&2
  exit 1
fi

# -mod=mod: let `go get` / `gomobile bind` update the throwaway go.mod/go.sum
# rather than erroring on missing entries (no committed go.sum for this module).
export GOFLAGS="-mod=mod"

# `go install` drops binaries in GOPATH/bin; put it on PATH so the freshly
# installed `gomobile` is invocable below.
export PATH="$(go env GOPATH)/bin:$PATH"

echo "==> Preparing wrapper module in $WORK_DIR"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR" "$(dirname "$OUT_AAR")"
cd "$WORK_DIR"

go mod init xraybind
go mod edit -require="github.com/xtls/libxray@v0.0.0-00010101000000-000000000000"
go mod edit -replace="github.com/xtls/libxray=$LIBXRAY_DIR"

# Blank import keeps libXray in the module graph so `go get` resolves its
# transitive deps (Xray-core et al.) from the submodule's pinned go.sum.
cat > wrapper.go <<'EOF'
// Package xraybind exists only to pull github.com/xtls/libxray into the module
// graph so `gomobile bind` can target it. It is never compiled into the AAR.
package xraybind

import _ "github.com/xtls/libxray"
EOF

echo "==> Fetching dependencies (libXray + pinned gomobile)"
go get "github.com/xtls/libxray"
go get "golang.org/x/mobile@$GOMOBILE_VERSION"
go install "golang.org/x/mobile/cmd/gomobile@$GOMOBILE_VERSION"

echo "==> gomobile init"
gomobile init

echo "==> gomobile bind -> $OUT_AAR"
# arm64/amd64 match the arm64-v8a/x86_64 abiFilters in build.gradle.kts.
gomobile bind \
  -o "$OUT_AAR" \
  -androidapi 26 \
  -target=android/arm64,android/amd64 \
  -ldflags="-buildid=" \
  -trimpath \
  github.com/xtls/libxray

echo "==> Built $OUT_AAR"
# Fail loudly if the AAR is missing an expected ABI (silent single-ABI bind is a
# known gomobile foot-gun with multi-target). Both must be present.
missing=0
for abi in arm64-v8a x86_64; do
  if unzip -l "$OUT_AAR" | grep -q "jni/$abi/.*\.so"; then
    echo "    ok: $abi native lib present"
  else
    echo "    ERROR: $abi native lib MISSING from AAR" >&2
    missing=1
  fi
done
exit "$missing"
