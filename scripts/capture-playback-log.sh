#!/usr/bin/env bash
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not installed or not on PATH" >&2
  exit 1
fi

adb logcat -c
printf '%s\n' "Capturing Unison playback logs. Reproduce the issue, then press Ctrl+C."
adb logcat -v threadtime \
  RoomRuntime:I \
  UnisonPlayback:I \
  UnisonScheduler:I \
  ExoPlayerImpl:I \
  MediaCodecRenderer:W \
  AudioTrack:W \
  AndroidRuntime:E \
  '*:S'
