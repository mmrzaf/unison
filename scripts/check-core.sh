#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v kotlinc >/dev/null 2>&1; then
  echo "CORE_CHECK_SKIPPED: kotlinc is not installed"
  exit 0
fi

KOTLIN_HOME="$(dirname "$(dirname "$(command -v kotlinc)")")"
COROUTINES_JAR="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
if [[ ! -f "$COROUTINES_JAR" ]]; then
  echo "CORE_CHECK_SKIPPED: Kotlin coroutines JAR was not found"
  exit 0
fi

OUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/unison-core-check.XXXXXX")"
trap 'rm -rf "$OUT_DIR"' EXIT

SOURCES=(
  scripts/core-check/stubs/android/content/Context.kt
  scripts/core-check/stubs/android/net/Uri.kt
  scripts/core-check/stubs/android/os/SystemClock.kt
  scripts/core-check/stubs/kotlinx/serialization/Stubs.kt
  scripts/core-check/stubs/org/junit/JUnitStubs.kt
  app/src/main/java/com/darius/unison/model/DomainModels.kt
  app/src/main/java/com/darius/unison/storage/ManagedFileStore.kt
  app/src/main/java/com/darius/unison/model/Commands.kt
  app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt
  app/src/main/java/com/darius/unison/protocol/Crypto.kt
  app/src/main/java/com/darius/unison/room/RoomReducer.kt
  app/src/main/java/com/darius/unison/room/RoomEngine.kt
  app/src/main/java/com/darius/unison/room/PlaybackQueuePolicy.kt
  app/src/main/java/com/darius/unison/room/PlaybackRequestPolicy.kt
  app/src/main/java/com/darius/unison/playback/SystemMediaCommandPolicy.kt
  app/src/main/java/com/darius/unison/sync/ClockSyncEngine.kt
  app/src/main/java/com/darius/unison/sync/PlaybackSyncEngine.kt
  app/src/main/java/com/darius/unison/util/MonotonicClock.kt
  app/src/main/java/com/darius/unison/library/M3uCodec.kt
  app/src/main/java/com/darius/unison/network/NetworkAddressPolicy.kt
  app/src/main/java/com/darius/unison/network/DiscoveredRoomRegistry.kt
  app/src/test/java/com/darius/unison/library/M3uCodecTest.kt
  app/src/test/java/com/darius/unison/model/CanonicalPlaybackStateTest.kt
  app/src/test/java/com/darius/unison/model/RoomUiStateTest.kt
  app/src/test/java/com/darius/unison/network/NetworkAddressPolicyTest.kt
  app/src/test/java/com/darius/unison/network/DiscoveredRoomRegistryTest.kt
  app/src/test/java/com/darius/unison/protocol/CryptoTest.kt
  app/src/test/java/com/darius/unison/room/PlaybackQueuePolicyTest.kt
  app/src/test/java/com/darius/unison/room/PlaybackRequestPolicyTest.kt
  app/src/test/java/com/darius/unison/playback/SystemMediaCommandPolicyTest.kt
  app/src/test/java/com/darius/unison/room/RoomReducerTest.kt
  app/src/test/java/com/darius/unison/sync/ClockSyncEngineTest.kt
  app/src/test/java/com/darius/unison/sync/PlaybackSyncEngineTest.kt
  app/src/test/java/com/darius/unison/storage/ManagedFileStoreTest.kt
  scripts/core-check/CoreTestRunner.kt
)

kotlinc "${SOURCES[@]}" \
  -classpath "$COROUTINES_JAR" \
  -include-runtime \
  -d "$OUT_DIR/unison-core-tests.jar"

echo CORE_COMPILE_OK
java -cp "$OUT_DIR/unison-core-tests.jar:$COROUTINES_JAR" CoreTestRunnerKt
