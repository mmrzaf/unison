#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

[[ -f keystore.properties ]] || {
  echo "Missing keystore.properties. Run ./scripts/create-release-key.sh first." >&2
  exit 1
}

./scripts/check-release-quality.sh
# The release build widens the seeded multi-peer soak beyond the per-commit default.
./gradlew --offline --no-daemon --stacktrace \
  -Dunison.soak.seeds=1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16 -Dunison.soak.minutes=20 \
  spotlessCheck testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease \
  :app:compileDebugAndroidTestKotlin

APK="$PWD/app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || { echo "Signed release APK was not produced." >&2; exit 1; }

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK_ROOT" ]] || { echo "ANDROID_HOME or ANDROID_SDK_ROOT is required." >&2; exit 1; }
BUILD_TOOLS="$SDK_ROOT/build-tools/$(scripts/build-tools-version.sh)"
[[ -n "$BUILD_TOOLS" && -x "$BUILD_TOOLS/apksigner" ]] || {
  echo "apksigner is required to verify the release artifact." >&2
  exit 1
}
./scripts/verify-release-apk.sh "$APK" "$BUILD_TOOLS"
sha256sum "$APK" > "$PWD/app/build/outputs/release-SHA256SUMS.txt"
printf 'APK: %s\nChecksum: %s\n' "$APK" "$PWD/app/build/outputs/release-SHA256SUMS.txt"
