#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

[[ -f keystore.properties ]] || {
  echo "Missing keystore.properties. Run ./scripts/create-release-key.sh first." >&2
  exit 1
}

./scripts/check-release-quality.sh
./gradlew --offline --no-daemon --stacktrace \
  spotlessCheck testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease \
  :app:compileDebugAndroidTestKotlin

APK="$PWD/app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || { echo "Signed release APK was not produced." >&2; exit 1; }

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK_ROOT" ]] || { echo "ANDROID_HOME or ANDROID_SDK_ROOT is required." >&2; exit 1; }
BUILD_TOOLS="$(find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)"
[[ -n "$BUILD_TOOLS" && -x "$BUILD_TOOLS/apksigner" ]] || {
  echo "apksigner is required to verify the release artifact." >&2
  exit 1
}
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK"

MAX_RELEASE_APK_BYTES="${MAX_RELEASE_APK_BYTES:-47185920}"
python3 ./scripts/analyze-apk-size.py "$APK" --max-bytes "$MAX_RELEASE_APK_BYTES"
sha256sum "$APK" > "$PWD/app/build/outputs/release-SHA256SUMS.txt"
printf 'APK: %s\nChecksum: %s\n' "$APK" "$PWD/app/build/outputs/release-SHA256SUMS.txt"
