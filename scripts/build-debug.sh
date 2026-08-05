#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/check-release-quality.sh
./gradlew --offline --no-daemon testDebugUnitTest lintDebug assembleDebug
printf 'APK: %s\n' "$PWD/app/build/outputs/apk/debug/app-debug.apk"
