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

## Device/API matrix

The final signed candidate must be exercised on physical Android 11 (API 30), Android 13 (API 33),
and Android 16 (API 36) devices. For each API level, qualify both discovery/control and a complete
verified file transfer followed by playback. Include private Wi-Fi; also qualify LocalOnlyHotspot on
at least one supported device. The selected route should be `SYSTEM_DEFAULT` when the owning LAN is
Android's active network, `NETWORK_BOUND` for a non-default owning `Network`, and
`ENDPOINT_FALLBACK` only for genuine hotspot/downstream cases where Android exposes no `Network`.

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
  songs; verify the interrupted phone stays silent, never pauses the room, and explicit local Play
  resumes on the current canonical song/position without a persistent intermediate participation state;
- headphone/noisy-route interruption followed by Leave and Create/Join; verify the new room starts
  with fresh local participation and never requires a process restart to clear stale interruption state;
- Android 16 debug compatibility test with `RESTRICT_LOCAL_NETWORK`: revoke Nearby devices, verify
  Create/Join requests it, grant it, then complete discovery, control, transfer, and playback;
- Android 16 with cellular active plus private/no-Internet Wi-Fi; verify a non-default Wi-Fi LAN
  remains `NETWORK_BOUND` rather than leaking onto the cellular default route;
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
