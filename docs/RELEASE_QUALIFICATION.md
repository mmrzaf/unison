# Release qualification

A locally signed candidate is releasable only after repository, Android build, and physical-device
gates all pass against the exact APK checksum.

## Repository and Android gate

```bash
./scripts/verify-offline-ready.sh
./scripts/check-release-quality.sh
./gradlew --offline --no-daemon --stacktrace \
  clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease \
  :app:compileDebugAndroidTestKotlin
```

The generated Room schema must still contain exactly schema `1.json` and the four production tables.
Protocol constants, NSD advertisement, handshakes, frames, envelopes, and documentation must all use strict Protocol 2.
`check-release-quality.sh` must execute the focused Milestone-5 hardening suite: RFC SRP arithmetic
conformance plus production PIN-handshake tests, lifecycle seam regressions, session-generation fences,
endpoint authority checks, and the sustained control-priority/no-starvation stress check.

## Playback-log gate

```bash
./scripts/capture-playback-log.sh unison-playback.ndjson
./scripts/analyze-playback-log.py unison-playback.ndjson --strict
./scripts/analyze-stability-log.py unison-playback.ndjson --strict
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
- rapid play/pause/seek/next/previous and READY queue-item selection;
- Next to an unavailable immediate successor while playing: prepare that exact successor, never skip
  forward, wait at the current boundary if necessary, and advance automatically when READY; repeat
  while paused and preserve paused intent;
- explicitly Prepare arbitrary unavailable queue items and verify preparation never changes current canonical playback;
- uninterrupted natural song boundaries under active playback with both READY and unavailable
  successors; verify exactly one physical boundary handoff, no repair/restart of the finished item,
  and automatic continuation from position 0 after an unavailable successor becomes READY;
- natural completion of the final queue item followed by Play; verify the canonical replay starts the same
  item once from position 0 on every listener, while a manual seek to duration does not acquire terminal
  replay semantics;
- song change while another listener downloads or reconnects;
- queue reorder and clear during import/preparation;
- coordinator loss: bounded reconnection to the existing coordinator, followed by a clean room end
  if recovery fails; no replacement coordinator is elected;
- Wi-Fi interruption and complete reconnect reconciliation;
- Bluetooth route changes; on API 30/32, a merely connected but unselected Bluetooth/USB/wired output
  must leave Unison route state `UNKNOWN`; on API 33/36, actual selected-route changes must be detected;
- phone-call/audio-focus interruption on one listener while the room advances across at least two
  songs; after focus/suppression clears, issue no user playback command and verify the listener
  automatically rejoins the current canonical song/position once its clock and media prerequisites
  recover, without pausing the room or remaining in an intermediate participation state;
- headphone/noisy-route interruption: verify no automatic audio resume; after explicit local Rejoin
  or a new Leave/Create/Join cycle, verify participation is fresh and never requires a process restart
  to clear stale interruption state;
- Android 16 debug compatibility test with `RESTRICT_LOCAL_NETWORK`: revoke Nearby devices, verify
  Create/Join requests it, grant it, then complete discovery, control, transfer, and playback;
- Android 16 with cellular active plus private/no-Internet Wi-Fi; verify a non-default Wi-Fi LAN
  remains `NETWORK_BOUND` rather than leaking onto the cellular default route;
- transfer interruption/resume, insufficient storage, corruption, and source loss;
- repeated create/join/leave cycles followed by idle resource inspection;
- room-A admission in flight while A is left and room B is created immediately; delayed A admission
  must be rejected before membership, connection, directory, liveness, or canonical mutation;
- rapid same-peer control-socket replacement while commands are already decoded/queued; the old socket
  message and delayed close must not affect the replacement connection or refresh liveness;
- source/destination room changes while transfer completion/failure/progress callbacks are in flight;
  callbacks from the old generation must not repopulate progress, READY, or failure state;
- authenticated endpoint-host spoof attempt: a peer connected from host X may change its transfer port
  but must not cause another phone to open a transfer connection to announced private host Y;
- concurrent temporary deletion and upload resolution, including corrupt-file repair: repair must remain
  legal before the final upload lease, and a leased upload file must not disappear mid-stream;
- sustained transfer/telemetry/playback-reference control traffic with repeated guaranteed and clock
  messages; no control or clock starvation is permitted;
- process recreation while connected.

## Acceptance

- no connected ready listener remains on a different song or play/pause intent;
- detected state mismatch repairs automatically;
- no stale scheduled command reaches Media3;
- no queue/current-item oscillation or transition storm occurs;
- every observed `END_OF_MEDIA_ITEM` is paired with one physical boundary handoff (or an explicitly
  ignored duplicate), the boundary identifies the item that actually ended, and no `WRONG_PLAY_STATE`
  repair resurrects an item before canonical boundary ownership decides the successor;
- the final-item terminal replay and actual-Media3 boundary instrumentation are green on the required
  API/device matrix;
- unavailable content leaves current canonical playback intact, exposes Prepare/Preparing truthfully, and never produces an unavailable-media command/mutation storm;
- a connected room never loses its content-readiness cohort merely because audible participation is
  temporarily inhibited;
- transient audio-focus suppression that has cleared cannot leave an automatic rejoin pending
  indefinitely; becoming-noisy/unsuitable-output interruptions must remain silent;
- participants never project a canonical playback position while their room clock is unlocked;
- leaving releases player work, transfers, sockets, locks, jobs, and notification ownership;
- memory, log volume, CPU, storage, and network activity remain bounded;
- app-owned Logcat records are valid structured JSON and the room log viewer remains responsive
  while DEBUG diagnostics are enabled;
- strict playback and stability-log analysis pass for every retained candidate trace;
- no `room.event.unexpected_handler_cancellation` appears in a candidate trace;
- stale admission/envelope/transfer/session diagnostics may appear only as rejected-work evidence and
  never correspond to a current-session mutation;
- no spoofed endpoint host receives an outbound transfer connection;
- the RFC SRP conformance vector, production PIN handshake tests, admission throttling tests, and
  documented 1.2 SRP security review are green;
- guaranteed room commands and clock traffic remain bounded/no-starvation under sustained lower-priority
  control load.
