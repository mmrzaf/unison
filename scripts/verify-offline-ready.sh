#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"
cd "$ROOT_DIR"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK_ROOT" && -d "$SDK_ROOT" ]] || { echo 'Android SDK is not configured.' >&2; exit 1; }
[[ -f gradle/wrapper/gradle-wrapper.jar ]] || { echo 'Gradle wrapper JAR is missing.' >&2; exit 1; }
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"
[[ -d "$GRADLE_HOME_DIR/caches" ]] || { echo "Gradle dependency cache is missing in $GRADLE_HOME_DIR." >&2; exit 1; }
[[ -d "$GRADLE_HOME_DIR/wrapper/dists" ]] || { echo "Gradle distribution cache is missing in $GRADLE_HOME_DIR." >&2; exit 1; }
./gradlew --offline --no-daemon help >/dev/null
echo 'Offline Android build prerequisites are available.'
