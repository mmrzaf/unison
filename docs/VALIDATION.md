# Validation status

Current source audit validated on 2026-07-28:

- 171 Android/JVM unit tests with zero failures;
- synchronization, affine-clock, two-hour multi-device, room reducer, event-loop, snapshot,
  replay/ordering, crypto, managed-file, cleanup-lease, playlist, and transfer-cancellation cases;
- repository, manifest, local-runtime, architecture-invariant, and development-marker checks;
- dependency-free Kotlin patch-regression checks for delimiter balance, duplicate named arguments,
  icon imports, and manual-discovery invariants;
- coordinator-local PIN and snapshot-exclusion source checks;
- SHA-256 verification and lease-protected deletion/partial-cleanup tests;
- worker dependency-injection and socket-ownership source checks;
- playlist path/ambiguity/order tests, SAF permission-ledger tests, and artwork-backoff tests;
- advisory host-side library-search benchmark through 100,000 tracks; physical-device results remain the FTS decision gate;
- split-state, nullable playback telemetry, peer-registry, message-router, role-engine, bounded admission-state, and control-admission tests;
- source-shape gates for the reduced application shell and ViewModel boundaries;
- removal of incomplete persisted room sessions and hard caps on transfer-authorization state;
- Android debug/release lint, debug assembly, and minified release assembly with zero failures.

Run these development checks on a provisioned Android machine:

```bash
./scripts/check-static.sh
./scripts/check-core.sh
./scripts/check-data.sh
./scripts/benchmark-library-search.py
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

## Required physical-device checks

Use at least three Android devices to verify:

1. screen-off and lock-screen playback for an extended session;
2. backgrounding, task removal, battery saver, sleep/wake, and Wi-Fi interruption recovery;
3. Android 13+ notification grant, denial, and later settings recovery;
4. built-in, wired, USB when available, and Bluetooth route changes;
5. cancellation during a blocked or slow transfer, followed by resume;
6. cleanup while queued, playing, uploading, downloading, and after lease release;
7. same-size managed-file corruption and automatic reacquisition;
8. initial PIN admission, reconnect without PIN, wrong-PIN throttling, nonce replay rejection, and
   coordinator promotion with a rotated invite PIN;
9. cancellation during a large M3U folder scan and confirmation that persisted tree grants are released;
10. duplicate playlist filename/title matches requiring an explicit choice while preserving source order;
11. QR dialog responsiveness and cache reuse;
12. artwork memory and disk behavior while rapidly scrolling a large library.

Record device model, Android version, battery restrictions, network topology, transfer outcome, and
observed synchronization recovery for each run.
