#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_OPTS="${JAVA_OPTS:--Xmx1g -XX:+UseG1GC}"
source scripts/lib/standalone-kotlin.sh
prepare_standalone_kotlin DIAGNOSTICS_CHECK

OUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/unison-diagnostics-check.XXXXXX")"
trap 'rm -rf "$OUT_DIR"' EXIT

SOURCES=(
  scripts/core-check/stubs/android/content/Context.kt
  scripts/core-check/stubs/android/os/SystemClock.kt
  scripts/risky-kotlin-check/stubs/android/util/Log.kt
  scripts/core-check/stubs/org/junit/JUnitStubs.kt
  scripts/core-check/stubs/org/junit/rules/TemporaryFolder.kt
  app/src/main/java/com/darius/unison/util/DiagnosticEvent.kt
  app/src/main/java/com/darius/unison/util/DiagnosticSanitizer.kt
  app/src/main/java/com/darius/unison/util/DiagnosticLog.kt
  app/src/test/java/com/darius/unison/util/DiagnosticEventTest.kt
  app/src/test/java/com/darius/unison/util/DiagnosticLogTest.kt
  scripts/diagnostics-kotlin-check/DiagnosticsTestRunner.kt
)

run_standalone_kotlinc "${SOURCES[@]}" \
  -classpath "$STANDALONE_KOTLIN_RUNTIME_CLASSPATH" \
  -d "$OUT_DIR/unison-diagnostics-tests.jar"

echo DIAGNOSTICS_COMPILE_OK
java -cp "$OUT_DIR/unison-diagnostics-tests.jar:$STANDALONE_KOTLIN_RUNTIME_CLASSPATH" DiagnosticsTestRunnerKt
