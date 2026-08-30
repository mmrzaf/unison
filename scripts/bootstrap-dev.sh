#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"
cd "$ROOT_DIR"

require_command java "Install JDK 21 and make it the Gradle runtime"
require_command python3 "Install Python 3"
require_command git "Install Git"
require_command adb "Install Android SDK Platform Tools"

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK_ROOT" && -d "$SDK_ROOT" ]] || die "ANDROID_HOME or ANDROID_SDK_ROOT must point to an Android SDK"

JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
[[ "$JAVA_MAJOR" == "21" ]] || die "Gradle development is qualified with JDK 21; found Java $JAVA_MAJOR"

info "Bootstrapping Gradle, Android dependencies, and standalone Kotlin check dependencies"
./gradlew --no-daemon help :app:prepareStandaloneKotlinChecks :app:compileDebugAndroidTestKotlin
info "Checking that the workstation can now build offline"
./scripts/verify-offline-ready.sh
info "Development bootstrap complete"
