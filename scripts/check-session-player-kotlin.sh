#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v kotlinc >/dev/null 2>&1 || ! command -v javac >/dev/null 2>&1; then
  echo "SESSION_PLAYER_KOTLIN_CHECK_SKIPPED: compiler missing"
  exit 0
fi
KOTLIN_HOME="$(dirname "$(dirname "$(command -v kotlinc)")")"
COROUTINES_JAR="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
[[ -f "$COROUTINES_JAR" ]] || { echo "SESSION_PLAYER_KOTLIN_CHECK_SKIPPED: coroutines JAR missing"; exit 0; }
OUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/unison-session-player.XXXXXX")"
trap 'rm -rf "$OUT_DIR"' EXIT
mkdir -p "$OUT_DIR/java"
javac -d "$OUT_DIR/java" scripts/session-player-kotlin-check/stubs/androidx/media3/common/*.java
kotlinc \
  scripts/core-check/stubs/kotlinx/serialization/Stubs.kt \
  scripts/player-kotlin-check/stubs/androidx/annotation/OptIn.kt \
  scripts/player-kotlin-check/stubs/androidx/media3/common/util/UnstableApi.kt \
  scripts/player-kotlin-check/stubs/com/darius/unison/util/DiagnosticLog.kt \
  app/src/main/java/com/darius/unison/model/RoomIssue.kt \
  app/src/main/java/com/darius/unison/model/DomainModels.kt \
  app/src/main/java/com/darius/unison/model/RoomStateModels.kt \
  app/src/main/java/com/darius/unison/model/Commands.kt \
  app/src/main/java/com/darius/unison/app/RoomCommandBus.kt \
  app/src/main/java/com/darius/unison/playback/SystemMediaCommandPolicy.kt \
  app/src/main/java/com/darius/unison/playback/RoomMediaSessionPlayer.kt \
  -classpath "$COROUTINES_JAR:$OUT_DIR/java" \
  -d "$OUT_DIR/session-player.jar"
echo SESSION_PLAYER_KOTLIN_COMPILE_OK
