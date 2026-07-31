#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v kotlinc >/dev/null 2>&1; then
  echo "PLAYER_KOTLIN_CHECK_SKIPPED: kotlinc is not installed"
  exit 0
fi
KOTLIN_HOME="$(dirname "$(dirname "$(command -v kotlinc)")")"
COROUTINES_JAR="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
[[ -f "$COROUTINES_JAR" ]] || { echo "PLAYER_KOTLIN_CHECK_SKIPPED: coroutines JAR missing"; exit 0; }
OUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/unison-player-kotlin.XXXXXX")"
trap 'rm -rf "$OUT_DIR"' EXIT

kotlinc \
  scripts/core-check/stubs/kotlinx/serialization/Stubs.kt \
  scripts/player-kotlin-check/stubs/android/content/Context.kt \
  scripts/player-kotlin-check/stubs/android/media/Audio.kt \
  scripts/player-kotlin-check/stubs/android/net/Uri.kt \
  scripts/player-kotlin-check/stubs/android/os/Os.kt \
  scripts/player-kotlin-check/stubs/androidx/annotation/OptIn.kt \
  scripts/player-kotlin-check/stubs/androidx/media3/common/util/UnstableApi.kt \
  scripts/player-kotlin-check/stubs/androidx/media3/common/Common.kt \
  scripts/player-kotlin-check/stubs/androidx/media3/exoplayer/ExoPlayer.kt \
  scripts/player-kotlin-check/stubs/com/darius/unison/util/DiagnosticLog.kt \
  app/src/main/java/com/darius/unison/model/RoomIssue.kt \
  app/src/main/java/com/darius/unison/model/DomainModels.kt \
  app/src/main/java/com/darius/unison/model/RoomStateModels.kt \
  app/src/main/java/com/darius/unison/model/Commands.kt \
  app/src/main/java/com/darius/unison/app/RoomCommandBus.kt \
  app/src/main/java/com/darius/unison/playback/PlayerPort.kt \
  app/src/main/java/com/darius/unison/playback/PlaybackQueueDiffPolicy.kt \
  app/src/main/java/com/darius/unison/playback/PlaybackTimelinePlan.kt \
  app/src/main/java/com/darius/unison/playback/SystemMediaCommandPolicy.kt \
  app/src/main/java/com/darius/unison/playback/Media3PlayerAdapter.kt \
  -classpath "$COROUTINES_JAR" \
  -d "$OUT_DIR/player-kotlin.jar"

echo PLAYER_KOTLIN_COMPILE_OK
