#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v javac >/dev/null 2>&1 || {
  echo "SESSION_PLAYER_KOTLIN_CHECK failed: javac is not installed" >&2
  exit 1
}
source scripts/lib/standalone-kotlin.sh
prepare_standalone_kotlin SESSION_PLAYER_KOTLIN_CHECK
OUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/unison-session-player.XXXXXX")"
trap 'rm -rf "$OUT_DIR"' EXIT
mkdir -p "$OUT_DIR/java"
javac -d "$OUT_DIR/java" scripts/session-player-kotlin-check/stubs/androidx/media3/common/*.java
run_standalone_kotlinc \
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
  -classpath "$STANDALONE_KOTLIN_RUNTIME_CLASSPATH:$OUT_DIR/java" \
  -d "$OUT_DIR/session-player.jar"
echo SESSION_PLAYER_KOTLIN_COMPILE_OK
