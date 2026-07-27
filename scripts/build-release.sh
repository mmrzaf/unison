#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

[[ -f keystore.properties ]] || {
  echo "Missing keystore.properties. Run ./scripts/create-release-key.sh first." >&2
  exit 1
}

./scripts/check-static.sh
./gradlew --no-daemon testDebugUnitTest lintRelease assembleRelease

APK="$PWD/app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || { echo "Signed release APK was not produced." >&2; exit 1; }

BUILD_TOOLS="$(find "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)"
if [[ -n "$BUILD_TOOLS" && -x "$BUILD_TOOLS/apksigner" ]]; then
  "$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK"
fi
sha256sum "$APK" > "$PWD/app/build/outputs/release-SHA256SUMS.txt"
printf 'APK: %s\nChecksums: %s\n' "$APK" "$PWD/app/build/outputs/release-SHA256SUMS.txt"
