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
./scripts/capture-playback-log.sh unison-playback.ndjson
./scripts/analyze-playback-log.py unison-playback.ndjson --strict
```

Retain the trace, device details, source archive checksum, and APK checksum.

## Required scenarios

- one-hour single-device playback with screen and app lifecycle changes;
- 30-minute three-device room with controls issued from different phones;
- rapid play/pause/seek/next/previous and queue-item selection;
- uninterrupted natural song boundaries under active playback; verify no pause/restart hiccup is
  introduced by reconciliation or a stale transport watchdog;
- song change while another listener downloads or reconnects;
- queue reorder and clear during import/preparation;
- coordinator departure and recovery;
- Wi-Fi interruption and complete reconnect reconciliation;
- Bluetooth route changes;
- phone-call/audio-focus interruption on one listener while the room advances across at least two
  songs; verify the interrupted phone stays silent, never pauses the room, and explicit Rejoin lands
  on the current canonical song/position before the listener becomes READY again;
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
- app-owned Logcat records are valid structured JSON and the room log viewer remains responsive
  while DEBUG diagnostics are enabled;
- strict playback-log analysis passes for every soak trace.
