#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib/standalone-kotlin.sh
prepare_standalone_kotlin HARDENING_CHECK
OUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/unison-hardening.XXXXXX")"
trap 'rm -rf "$OUT_DIR"' EXIT

run_standalone_kotlinc \
  scripts/core-check/stubs/kotlinx/serialization/Stubs.kt \
  scripts/core-check/stubs/org/junit/JUnitStubs.kt \
  app/src/main/java/com/darius/unison/model/RoomIssue.kt \
  app/src/main/java/com/darius/unison/model/DomainModels.kt \
  app/src/main/java/com/darius/unison/model/Commands.kt \
  app/src/main/java/com/darius/unison/protocol/ProtocolModels.kt \
  app/src/main/java/com/darius/unison/protocol/Crypto.kt \
  app/src/main/java/com/darius/unison/protocol/Srp6aCore.kt \
  app/src/main/java/com/darius/unison/protocol/PinPake.kt \
  app/src/main/java/com/darius/unison/network/NetworkAddressPolicy.kt \
  app/src/main/java/com/darius/unison/room/RoomSessionProvenance.kt \
  app/src/main/java/com/darius/unison/room/RoomIngressAuthority.kt \
  app/src/main/java/com/darius/unison/room/PeerEndpointAuthority.kt \
  app/src/main/java/com/darius/unison/room/SessionJobRegistry.kt \
  scripts/hardening-kotlin-check/RoomPolicyStubs.kt \
  app/src/main/java/com/darius/unison/room/PlaybackQueuePolicy.kt \
  app/src/main/java/com/darius/unison/room/TerminalReplayPolicy.kt \
  app/src/test/java/com/darius/unison/protocol/Srp6aCoreRfc5054Test.kt \
  app/src/test/java/com/darius/unison/protocol/PinPakeTest.kt \
  app/src/test/java/com/darius/unison/room/RoomIngressAuthorityTest.kt \
  app/src/test/java/com/darius/unison/room/PeerEndpointAuthorityTest.kt \
  app/src/test/java/com/darius/unison/room/SessionJobRegistryTest.kt \
  app/src/test/java/com/darius/unison/room/RoomLifecycleSeamRegressionTest.kt \
  scripts/hardening-kotlin-check/HardeningTestRunner.kt \
  -classpath "$STANDALONE_KOTLIN_RUNTIME_CLASSPATH" \
  -d "$OUT_DIR/hardening-tests.jar"

echo HARDENING_KOTLIN_COMPILE_OK
java -cp "$OUT_DIR/hardening-tests.jar:$STANDALONE_KOTLIN_RUNTIME_CLASSPATH" HardeningTestRunnerKt
