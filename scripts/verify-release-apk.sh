#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"
cd "$ROOT_DIR"

APK="${1:-}"
BUILD_TOOLS="${2:-}"
APKSIGNER_OUTPUT="${3:-}"
[[ -n "$APK" && -f "$APK" ]] || die "Usage: $0 <release.apk> <build-tools-dir> [apksigner-output]"
[[ -n "$BUILD_TOOLS" && -d "$BUILD_TOOLS" ]] || die "Android build-tools directory is required"

APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="$BUILD_TOOLS/zipalign"
AAPT2="$BUILD_TOOLS/aapt2"
[[ -x "$APKSIGNER" ]] || die "Missing apksigner: $APKSIGNER"
[[ -x "$ZIPALIGN" ]] || die "Missing zipalign: $ZIPALIGN"
[[ -x "$AAPT2" ]] || die "Missing aapt2: $AAPT2"
[[ -f keystore.properties ]] || die "keystore.properties is required to verify the expected signing identity"

EXPECTED_CERT_SHA256="$(sed -n 's/^certificateSha256=//p' keystore.properties | head -1)"
[[ -n "$EXPECTED_CERT_SHA256" ]] || die "keystore.properties is missing certificateSha256"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
APKSIGNER_OUTPUT="${APKSIGNER_OUTPUT:-$TMP_DIR/apksigner.txt}"
BADGING_OUTPUT="$TMP_DIR/badging.txt"

"$APKSIGNER" verify --verbose --print-certs "$APK" | tee "$APKSIGNER_OUTPUT"
python3 ./scripts/check-release-signing.py \
  --apksigner-output "$APKSIGNER_OUTPUT" \
  --expected "$EXPECTED_CERT_SHA256"
"$ZIPALIGN" -c -P 16 -v 4 "$APK"
"$AAPT2" dump badging "$APK" > "$BADGING_OUTPUT"
python3 ./scripts/check-release-apk-metadata.py \
  --badging-output "$BADGING_OUTPUT" \
  --versions gradle/libs.versions.toml \
  --application-id com.darius.unison

MAX_RELEASE_APK_BYTES="${MAX_RELEASE_APK_BYTES:-47185920}"
python3 ./scripts/analyze-apk-size.py "$APK" --max-bytes "$MAX_RELEASE_APK_BYTES"
printf 'Release APK signature, identity metadata, alignment, and size verified.\n'
