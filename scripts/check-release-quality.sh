#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

room_screen_lines="$(wc -l < app/src/main/java/com/darius/unison/ui/room/RoomScreens.kt)"
(( room_screen_lines <= 1300 )) || {
  echo "RoomScreens.kt grew past the 1,300-line decomposition gate: $room_screen_lines" >&2
  exit 1
}

required=(
  app/src/main/java/com/darius/unison/ui/room/RoomPlaybackComponents.kt
  app/src/main/java/com/darius/unison/ui/room/RoomStatusComponents.kt
  app/src/main/java/com/darius/unison/ui/RoomPlaybackUiPolicy.kt
  app/src/main/java/com/darius/unison/playback/MediaNotificationUpdatePolicy.kt
  app/src/main/java/com/darius/unison/playback/PlaybackTimelinePlan.kt
  scripts/analyze-playback-log.py
)
for path in "${required[@]}"; do
  [[ -f "$path" ]] || { echo "Missing release-quality component: $path" >&2; exit 1; }
done

grep -q 'PersistentRoomIssueCard' app/src/main/java/com/darius/unison/ui/UnisonApp.kt
grep -q 'ui.room.issue == null' app/src/main/java/com/darius/unison/ui/UnisonApp.kt
grep -q 'PlaybackTimelinePlan.decide' app/src/main/java/com/darius/unison/playback/Media3PlayerAdapter.kt
grep -q 'MediaNotificationUpdatePolicy.decide' app/src/main/java/com/darius/unison/playback/UnisonRoomService.kt
grep -q 'playbackReconcile=' app/src/main/java/com/darius/unison/room/RoomRuntime.kt
! grep -q '"Set queue items=' app/src/main/java/com/darius/unison/playback/Media3PlayerAdapter.kt

./scripts/check-network-lifecycle-kotlin.sh
./scripts/analyze-playback-log.py --self-test
./scripts/benchmark-library-search.py --sizes 100000 --iterations 8 --max-p95-ms 50

echo RELEASE_QUALITY_CHECK_OK
