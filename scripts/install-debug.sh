#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

command -v adb >/dev/null 2>&1 || { echo "adb is required." >&2; exit 1; }
APK="$PWD/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || ./scripts/build-debug.sh
adb install -r "$APK"
