# Release qualification

A locally signed candidate is releasable only after repository, Android build, and physical-device
gates all pass against the exact APK checksum.

## Repository and Android gate

```bash
./scripts/verify-offline-ready.sh
./scripts/check-release-quality.sh
./gradlew --offline --no-daemon --stacktrace \
  clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
```

The generated Room schema must still contain exactly schema `1.json` and the four production tables.
Protocol constants, NSD advertisement, handshakes, frames, and envelopes must all use protocol 1.

## Playback-log gate

```bash
./scripts/analyze-playback-log.py path/to/logcat.txt --strict
```

Retain the trace, device details, source archive checksum, and APK checksum.

## Required scenarios

- one-hour single-device playback with screen and app lifecycle changes;
- 30-minute three-device room with controls issued from different phones;
- rapid play/pause/seek/next/previous and queue-item selection;
- song change while another listener downloads or reconnects;
- queue reorder and clear during import/preparation;
- coordinator departure and recovery;
- Wi-Fi interruption and complete reconnect reconciliation;
- Bluetooth route changes;
- transfer interruption/resume, insufficient storage, corruption, and source loss;
- repeated create/join/leave cycles followed by idle resource inspection;
- process recreation while connected.

## Acceptance

- no connected ready listener remains on a different song or play/pause intent;
- detected state mismatch repairs automatically;
- no stale scheduled command reaches Media3;
- no queue/current-item oscillation or transition storm occurs;
- unavailable content leaves the current playable song intact and exposes one actionable failure;
- leaving releases player work, transfers, sockets, locks, jobs, and notification ownership;
- memory, log volume, CPU, storage, and network activity remain bounded;
- strict playback-log analysis passes for every soak trace.
