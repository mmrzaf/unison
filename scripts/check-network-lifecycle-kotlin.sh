#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v kotlinc >/dev/null 2>&1; then
  echo "NETWORK_LIFECYCLE_CHECK_SKIPPED: kotlinc is not installed"
  exit 0
fi
KOTLIN_HOME="$(dirname "$(dirname "$(command -v kotlinc)")")"
COROUTINES_JAR="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
OUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/unison-network-lifecycle.XXXXXX")"
trap 'rm -rf "$OUT_DIR"' EXIT
kotlinc \
  scripts/network-lifecycle-kotlin-check/stubs/android/content/Context.kt \
  scripts/network-lifecycle-kotlin-check/stubs/android/net/nsd/Nsd.kt \
  scripts/network-lifecycle-kotlin-check/stubs/android/net/wifi/Wifi.kt \
  scripts/network-lifecycle-kotlin-check/stubs/android/os/Os.kt \
  scripts/network-lifecycle-kotlin-check/stubs/com/darius/unison/model/Models.kt \
  scripts/network-lifecycle-kotlin-check/stubs/com/darius/unison/protocol/Protocol.kt \
  scripts/network-lifecycle-kotlin-check/stubs/com/darius/unison/util/DiagnosticLog.kt \
  scripts/network-lifecycle-kotlin-check/stubs/com/darius/unison/network/Stubs.kt \
  app/src/main/java/com/darius/unison/network/NsdRoomDiscovery.kt \
  app/src/main/java/com/darius/unison/network/LocalHotspotController.kt \
  scripts/network-lifecycle-kotlin-check/NetworkLifecycleCheck.kt \
  -classpath "$COROUTINES_JAR" \
  -d "$OUT_DIR/network-lifecycle.jar"
echo NETWORK_LIFECYCLE_KOTLIN_COMPILE_OK
kotlin -classpath "$OUT_DIR/network-lifecycle.jar:$COROUTINES_JAR" \
  com.darius.unison.network.NetworkLifecycleCheckKt
