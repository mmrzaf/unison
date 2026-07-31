# Release qualification

This checklist is the final gate for a locally signed Unison release candidate. Repository checks
must pass before device testing. Device evidence must be retained with the diagnostic log and the
exact APK checksum.

## Repository gate

```bash
./scripts/check-static.sh
./scripts/check-core.sh
./scripts/check-data.sh
./scripts/check-risky-kotlin.sh
./scripts/check-player-kotlin.sh
./scripts/check-session-player-kotlin.sh
./scripts/check-network-lifecycle-kotlin.sh
./scripts/check-release-quality.sh
./scripts/verify-offline-ready.sh
./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --offline
```

`check-release-quality.sh` enforces the room-screen size boundary, playback reconciliation policy,
persistent typed issue handling, playback-dispatch metrics, executable Android network-lifecycle races,
log-analyzer self-test, and a bounded 100,000-track search benchmark.

## Playback-log gate

Capture Logcat from immediately before room creation until after leaving the room, then run:

```bash
./scripts/analyze-playback-log.py path/to/logcat.txt --strict
```

The strict gate fails when the trace contains any of the following:

- an automatic-transition circuit breaker;
- a reported playback-dispatch failure;
- an unavailable-song playback error;
- more than three canonical current-item changes in any two-second window;
- more than three local current-item switches in any two-second window.

For machine-readable CI or test-lab output:

```bash
./scripts/analyze-playback-log.py path/to/logcat.txt --strict --json
```

## Required device matrix

Qualify at least three physical devices:

1. Android 11 reference device;
2. Xiaomi/MIUI device representative of the original failure trace;
3. Android 13 reference device.

Exercise built-in speaker, wired audio when available, and Bluetooth. Record device model, Android
version, Media3 version, battery restrictions, route, router/hotspot topology, and APK checksum.

## Required scenarios

- one-hour single-device playback with screen-off/on and background/foreground cycles;
- 30-minute three-device synchronized playback with play, pause, seek, next, previous, and queue edits;
- next/previous while the target is preparing or unavailable;
- rapid repeated transport input from UI, notification, and Bluetooth controls;
- coordinator departure and recovery;
- transient Wi-Fi loss and peer reconnect;
- transfer interruption, resume, insufficient storage, and source loss;
- five create/join/leave cycles followed by idle-resource inspection;
- process recreation while connected to a room;
- signed upgrade over the previous locally signed build.

## Acceptance criteria

A candidate passes only when:

- one transport action creates at most one effective canonical transition;
- no programmatic seek or timeline update is classified as natural progression;
- no queue-size or current-item oscillation occurs;
- no stale scheduled command reaches Media3;
- no repeated unavailable-song issue is shown;
- pending navigation disables conflicting navigation and seeking while play/pause remains reversible;
- every visible failure has a stable presentation and a valid action or explicit automatic recovery;
- leaving the room releases player work, jobs, transfers, sockets, locks, and notifications;
- `analyze-playback-log.py --strict` passes for every soak trace;
- memory and log volume remain bounded throughout the longest run.

## Final transport and admission acceptance

- no unresolved transport command remains after ten seconds or at session shutdown;
- preparation timeout leaves the current playable song intact and exposes one retryable issue;
- a newer navigation command terminally supersedes the prior pending target;
- the device that created the credential can reveal and copy it through the explicit Room code menu
  item; the code is never shown automatically or redistributed to other admitted members;
- every admitted member has the same playback, queue, and room-setting controls; coordinator status
  remains an internal ordering detail;
- notification-manager shedding is zero during the rapid-input qualification run.
