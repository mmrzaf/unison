#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib/standalone-kotlin.sh
prepare_standalone_kotlin NETWORK_LIFECYCLE_CHECK
OUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/unison-network-lifecycle.XXXXXX")"
trap 'rm -rf "$OUT_DIR"' EXIT
run_standalone_kotlinc \
  scripts/network-lifecycle-kotlin-check/stubs/android/annotation/TargetApi.kt \
  scripts/network-lifecycle-kotlin-check/stubs/android/content/Context.kt \
  scripts/network-lifecycle-kotlin-check/stubs/android/net/Network.kt \
  scripts/network-lifecycle-kotlin-check/stubs/android/net/nsd/Nsd.kt \
  scripts/network-lifecycle-kotlin-check/stubs/android/net/wifi/Wifi.kt \
  scripts/network-lifecycle-kotlin-check/stubs/android/os/Os.kt \
  scripts/network-lifecycle-kotlin-check/stubs/com/darius/unison/model/Models.kt \
  scripts/network-lifecycle-kotlin-check/stubs/com/darius/unison/protocol/Protocol.kt \
  scripts/network-lifecycle-kotlin-check/stubs/com/darius/unison/util/DiagnosticLog.kt \
  scripts/network-lifecycle-kotlin-check/stubs/com/darius/unison/network/Stubs.kt \
  app/src/main/java/com/darius/unison/network/NetworkAddressPolicy.kt \
  app/src/main/java/com/darius/unison/network/LocalNetworkSocketProvider.kt \
  app/src/main/java/com/darius/unison/network/AndroidLocalNetworkRouter.kt \
  app/src/main/java/com/darius/unison/network/NsdRoomDiscovery.kt \
  app/src/main/java/com/darius/unison/network/LocalHotspotController.kt \
  scripts/network-lifecycle-kotlin-check/NetworkLifecycleCheck.kt \
  -classpath "$STANDALONE_KOTLIN_RUNTIME_CLASSPATH" \
  -d "$OUT_DIR/network-lifecycle.jar"
echo NETWORK_LIFECYCLE_KOTLIN_COMPILE_OK
java -cp "$OUT_DIR/network-lifecycle.jar:$STANDALONE_KOTLIN_RUNTIME_CLASSPATH" \
  com.darius.unison.network.NetworkLifecycleCheckKt
