#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew test lint assembleDebug
printf 'APK: %s\n' "$PWD/app/build/outputs/apk/debug/app-debug.apk"
