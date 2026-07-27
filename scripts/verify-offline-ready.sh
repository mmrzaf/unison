#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK_ROOT" && -d "$SDK_ROOT" ]] || { echo 'Android SDK is not configured.' >&2; exit 1; }
[[ -f gradle/wrapper/gradle-wrapper.jar ]] || { echo 'Gradle wrapper JAR is missing.' >&2; exit 1; }
[[ -d "$HOME/.gradle/caches" ]] || { echo 'Gradle dependency cache is missing.' >&2; exit 1; }
[[ -d "$HOME/.gradle/wrapper/dists" ]] || { echo 'Gradle distribution cache is missing.' >&2; exit 1; }
./gradlew --offline --no-daemon help >/dev/null
echo 'Offline Android build prerequisites are available.'
