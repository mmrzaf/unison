# Testing

Unison tests should be simpler than Unison itself. The goal is strong invariants and repeated abuse,
not a custom distributed-systems test platform.

## Fast repository checks

These checks do not require an Android device:

```bash
./scripts/check-static.sh
./scripts/check-data.sh
python3 ./scripts/analyze-playback-log.py --self-test
python3 ./scripts/analyze-stability-log.py --self-test
python3 ./scripts/check-log-analyzer-fixtures.py
```

With the repository-pinned Gradle/Kotlin dependency cache available:

```bash
./scripts/check-release-quality.sh
```

The release-quality gate includes focused Kotlin compilation, reducer/protocol/transfer/playback tests,
diagnostics checks, and the 100,000-track library benchmark.

## High-value stress coverage

Normal JVM tests deliberately repeat the dangerous state transitions rather than inventing a separate
simulation framework:

- `ReliabilityStressTest`: 20,000 deterministic transfer mutations while continuously checking every
  shared capacity invariant;
- readiness/convergence stress: 10,000 repeated Prepare/unavailable-media reconciliation cycles with
  zero futile playback repair until READY;
- `ManagedFileStoreTest`: repeated interrupted writes with monotonic partial offsets, resume, final
  SHA-256 commit, and byte equality;
- focused tests for duplicate assignments, cancellation ordering, retry/backoff, same-pair admission,
  room reconnect grace, and long scheduled-command clock remapping.

A small Android instrumentation test repeats interrupted/resume/verified-commit behavior on the real
Android filesystem. Contributors do not need a multi-device lab to run ordinary tests.

## Stability invariants

Keep [INVARIANTS.md](INVARIANTS.md) green. In particular:

- unavailable content cannot become executable playback;
- repeated Prepare/demand is idempotent;
- same source→destination transfer capacity cannot be exceeded;
- intentional cancellation is not network failure;
- completed media is hash-verified;
- a healthy steady room causes no player/transfer repair storm;
- exhausted room recovery ends rather than leaving stale synchronized state.

## Diagnostic trace analysis

Capture a real candidate trace when doing final device validation:

```bash
./scripts/capture-playback-log.sh unison-playback.ndjson
python3 ./scripts/analyze-playback-log.py unison-playback.ndjson --strict
python3 ./scripts/analyze-stability-log.py unison-playback.ndjson --strict
```

The playback analyzer checks transition/player/convergence failures plus the physical boundary,
pending-successor, content-readiness, local rejoin, and clock-domain invariants exercised by the
physical-device incidents. The stability analyzer checks unavailable-media rejection, duplicate
assignments, handshake timeouts, transfer reconnect/retry storms, malformed diagnostics, unclean room
teardown, and materially late scheduled playback. Playback arrival lateness is reported separately
from PlayerExecutor execution lateness so a slow network/room-actor delivery is not misdiagnosed as a
player scheduler failure.

`scripts/check-log-analyzer-fixtures.py` runs sanitized regression traces derived from real 1.2.0
qualification failures. A release-quality change must keep the healthy fixture green and every bad
fixture red for the intended reason.

## Android/build qualification

When Android SDK 36 and the pinned Gradle/dependency cache are available:

```bash
./scripts/verify-offline-ready.sh
./gradlew --offline --no-daemon --stacktrace \
  testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease \
  :app:compileDebugAndroidTestKotlin
```

Release candidates still get a final real-phone listening check because JVM/instrumentation tests do
not reproduce every OEM Wi-Fi, Media3, Bluetooth, foreground-service, or power-management behavior.
That real use is the last validation layer, not the first place basic state-machine regressions should
be discovered.
