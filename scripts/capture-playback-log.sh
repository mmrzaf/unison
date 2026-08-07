#!/usr/bin/env bash
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not installed or not on PATH" >&2
  exit 1
fi

output="${1:-unison-playback.ndjson}"
adb logcat -c
printf '%s\n' "Capturing structured Unison diagnostics to $output. Reproduce the issue, then press Ctrl+C."
# DiagnosticLog writes one compact JSON object per Logcat message. Raw mode preserves NDJSON so the
# same analyzer consumes live device captures and the app's rotated diagnostics files.
adb logcat -v raw Unison:D '*:S' | tee "$output"
