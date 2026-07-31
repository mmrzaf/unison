#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v kotlinc >/dev/null 2>&1; then
  echo "RISKY_KOTLIN_CHECK_SKIPPED: kotlinc is not installed"
  exit 0
fi

KOTLIN_HOME="$(dirname "$(dirname "$(command -v kotlinc)")")"
COROUTINES_JAR="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
if [[ ! -f "$COROUTINES_JAR" ]]; then
  echo "RISKY_KOTLIN_CHECK_SKIPPED: Kotlin coroutines JAR was not found"
  exit 0
fi

OUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/unison-risky-kotlin.XXXXXX")"
trap 'rm -rf "$OUT_DIR"' EXIT

kotlinc \
  scripts/core-check/stubs/android/content/Context.kt \
  scripts/core-check/stubs/android/net/Uri.kt \
  scripts/core-check/stubs/android/os/SystemClock.kt \
  scripts/core-check/stubs/kotlinx/serialization/Stubs.kt \
  scripts/risky-kotlin-check/stubs/android/util/Log.kt \
  scripts/risky-kotlin-check/stubs/com/darius/unison/library/TrackRepository.kt \
  scripts/risky-kotlin-check/stubs/com/darius/unison/protocol/ProtocolJson.kt \
  app/src/main/java/com/darius/unison/model/RoomIssue.kt \
  app/src/main/java/com/darius/unison/model/DomainModels.kt \
  app/src/main/java/com/darius/unison/model/RoomStateModels.kt \
  app/src/main/java/com/darius/unison/model/Commands.kt \
  app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt \
  app/src/main/java/com/darius/unison/protocol/ProtocolException.kt \
  app/src/main/java/com/darius/unison/protocol/Crypto.kt \
  app/src/main/java/com/darius/unison/protocol/PinPake.kt \
  app/src/main/java/com/darius/unison/protocol/AuthenticatedFileStreamCodec.kt \
  app/src/main/java/com/darius/unison/protocol/FileWireCodec.kt \
  app/src/main/java/com/darius/unison/protocol/FrameCodec.kt \
  app/src/main/java/com/darius/unison/protocol/HandshakeCodec.kt \
  app/src/main/java/com/darius/unison/network/NetworkAddressPolicy.kt \
  app/src/main/java/com/darius/unison/network/ControlTraffic.kt \
  app/src/main/java/com/darius/unison/network/ControlConnection.kt \
  app/src/main/java/com/darius/unison/network/ControlClient.kt \
  app/src/main/java/com/darius/unison/network/PeerServer.kt \
  app/src/main/java/com/darius/unison/playback/PlayerPort.kt \
  app/src/main/java/com/darius/unison/room/RoomEvent.kt \
  app/src/main/java/com/darius/unison/storage/ManagedFileStore.kt \
  app/src/main/java/com/darius/unison/transfer/TransferCancellationRegistry.kt \
  app/src/main/java/com/darius/unison/transfer/TransferAuthorizationRegistry.kt \
  app/src/main/java/com/darius/unison/transfer/TransferManager.kt \
  app/src/main/java/com/darius/unison/util/DiagnosticSanitizer.kt \
  app/src/main/java/com/darius/unison/util/DiagnosticLog.kt \
  app/src/main/java/com/darius/unison/sync/SynchronizationDiagnostics.kt \
  -classpath "$COROUTINES_JAR" \
  -d "$OUT_DIR/risky-kotlin.jar"

echo RISKY_KOTLIN_COMPILE_OK
