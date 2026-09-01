# Changelog

## Unreleased

Changes after `1.2.0-beta.6` should be recorded here until the next prerelease or stable release is
cut.

## 1.2.0-beta.6

### Fixed

- Made Android LAN socket selection VPN-aware, preserved system policy instead of forcing an
  underlying-network bind, and added the normal network-state permission required by explicit
  non-default network selection.
- Split pre-connect socket-provision failures from ordinary TCP connection failures so policy/access
  denial is terminal until the environment changes while transient route failures remain retryable.
- Replaced per-song route retry storms with source/destination route health, bounded backoff, a
  five-failure circuit breaker, alternate-source failover, and explicit preparation retry/recovery.
- Propagated blocked preparation to both the affected listener and coordinator and prevented failed
  successors from remaining indefinitely presented as Preparing.
- Added pre-socket/transfer-attempt diagnostics and taught the stability analyzer to count failures
  that happen before TCP connect, including the Beta 5 bind-failure retry-storm shape.

### Changed

- Simplified empty-room and empty-queue UI, made queue search progressive, reduced healthy transfer
  status noise, tightened the playing/Up Next layout, and compacted the empty Nearby section.
- Made shared-music destination/playlist sheets content-sized and removed search controls when no
  playlists exist.
- Made Diagnostics responsive on narrow displays while retaining explicit Copy/Share actions and
  moving Clear view into overflow.
- New installs now require an explicit display name instead of proposing `Friend`; `Listener` is the
  centralized defensive fallback for malformed/legacy blank identity only.
- Kept the room PIN as the plain four-digit value with no extra Copy/Code chrome.

### Reliability and testing

- Added route-policy, route-failure classification, circuit-breaker, preparation/error propagation,
  room playback/queue presentation, UI invariant, and diagnostic analyzer regression coverage.
- Added explicit physical qualification for Android 16 VPN/LAN combinations, non-default local-only
  Wi-Fi, both transfer directions, route recovery, and retry-storm prevention.
- Kept Protocol 2, Room schema 1, `targetSdk 33`, and the existing storage/playback architecture
  unchanged.

## 1.2.0-beta.5

### Fixed

- Made out-of-room room failures visible on Home instead of silently retaining a structured issue
  that only the in-room screen could render.
- Added explicit local-network precondition failures for Create and Join, including a direct Home
  action to create Unison's local connection when no LAN is available.
- Extended coordinator local-network recovery to the same bounded 20-second grace used by
  participants so brief Android Wi-Fi reassociation does not immediately destroy the room.
- Added one bounded same-room NSD endpoint refresh during participant reconnect so recovery can
  follow a coordinator whose LAN address changed after Wi-Fi reassociation.
- Kept genuine hotspot shutdown and exhausted reconnect paths terminal and visible rather than
  allowing zombie room state.

## 1.2.0-beta.4

### Fixed

- Restored release-note version alignment so the Beta 4 tag has a curated changelog section for the
  release publication gate.

## 1.2.0-beta.1

### Fixed

- Prevented stale room/library preparation results from terminating the persistent serialized room
  actor through synthetic `CancellationException` paths.
- Rejected old-room accepted control connections and messages from superseded control sockets before
  they can mutate membership, liveness, peer directories, replay state, or canonical room state.
- Bound transfer endpoint host authority to the authenticated control socket rather than trusting an
  announced private-LAN host.
- Fenced transfer completion, failure, progress, heartbeat, and reconnect-state callbacks by the room
  session generation that created them.
- Made the upload readable-file-to-lease handoff atomic against managed deletion without blocking
  legitimate repair/replacement of corrupt managed content.
- Added durable pending deletion so logically deleted managed media is eventually removed after the
  final active lease, including across process restart and valid republication races.
- Made Play after genuine natural completion of the final queue item restart canonically from position
  zero while keeping manual seek-to-end semantics distinct.
- Stopped Android 11/12 route detection from treating merely connected Bluetooth/USB/wired devices as
  the active media route; those platforms now report `UNKNOWN` when active routing cannot be proven.
- Stopped advertising arbitrary MediaSession seek-to-media-item support when canonical playback only
  implements the supported navigation surface.

### Changed

- Added immutable room/session provenance and explicit ingress-authority policies for asynchronous room
  work.
- Hardened `SerializedEventLoop` so handler-thrown cancellation cannot silently kill a healthy owner
  coroutine while genuine owner cancellation still terminates normally.
- Kept Protocol 2 and Room schema 1 unchanged; no Protocol 3 or database migration was required.
- Retained `targetSdk 33` for the 1.2 release line while compiling against SDK 36 and qualifying API
  30/33/36 behavior explicitly.

### Security

- Added RFC 5054 Appendix B SRP-6a arithmetic conformance coverage and documented the JVM
  `BigInteger.modPow` timing limitation and 1.2 threat-model decision.
- Added endpoint-host spoof rejection, stale-session/socket diagnostics, and stronger lifecycle
  authority regression coverage.
- Added sustained control-lane priority/no-starvation stress coverage.

### Reliability and testing

- Added deterministic lifecycle seam regressions for stale admission, connection replacement,
  transfer/session callbacks, heartbeat, reconnect state, and terminal replay.
- Added real Media3 instrumentation scenarios for two-item natural transition, final-item completion,
  repeat-one, replay re-arm, and queue mutation near a boundary.
- Expanded storage stress/regression coverage for corruption repair, upload/delete races, pending
  deletion, and publication protection.
- Expanded structured diagnostics and strict release analyzers for unexpected actor-handler
  cancellation and stale-work rejection evidence.

## 1.1.0

- Player timelines stop at the first unavailable or unprepared canonical successor instead of exposing later ready songs.
- Media3 mutations are serialized behind one player authority; natural completion is reported to canonical room logic.
- Protocol 2 removes transient readiness, endpoint, connection, and transfer state from canonical snapshots.
- Coordinator loss uses bounded reconnect, then ends the room cleanly when recovery is impossible.
- Playback demand drives bounded, resumable peer transfers with priority, deadlines, preemption, and typed failures.
- Joining peers catch up only after current and successor content are ready, without stalling healthy listeners.
- Pending navigation stays reversible while playback preparation state is shown directly in the player and queue.
- Runtime readiness and preparation requests stay outside canonical history; hot paths avoid redundant work.
- UX1: audio shared into Unison and audio chosen from inside the app use one destination sheet for Library, playlists, inline playlist creation, and the current room.
- UX1: playlist curation uses reusable full-height music/playlist pickers, batch cross-playlist actions, and long-press drag reordering instead of move-up/move-down menus.
- UX2: the room surface is music-first: the player leads, healthy participants collapse to a tappable listener count, and background prefetch/transfer machinery disappears from the normal listening view.
- UX2: listeners move to a bottom sheet, queue actions are consolidated behind a compact toolbar overflow, and playback/preparation/failure copy describes user outcomes instead of internal transfer/synchronization mechanics.
- UX3: All Music and playlist detail are persistent navigation surfaces with real Back behavior; contextual add/import pickers remain bottom sheets instead of stacking long-lived modal screens.
- UX3: playlist browsing is decoupled from room-queue actions, and Back exits selection/reorder modes before leaving the playlist.
- UX4: persistent screens, contextual sheets, and queue/library search now share one quieter visual language with consistent top bars, spacing, selection surfaces, and empty states.
- UX4: the room player has stronger music-first hierarchy, current queue state uses subtle tonal emphasis, and playlist/library rows are denser without returning technical status noise.

## 1.0.1

- Avoid redundant Android network binding when the resolved LAN is already the system default, and
  safely fall back to that reachable active LAN if explicit binding fails.
- Reject loopback-only discovery endpoints.

## 1.0.0

Fresh production release.

- Two continuous application surfaces: Home and Room.
- Shared queue, room-wide transport controls, playlists, local imports, and nearby discovery.
- Shuffle is a one-shot canonical queue action; repeat is the only persistent playback mode.
- Canonical queue and playback revisions with stale-work rejection and automatic peer repair.
- READY playback-cohort leases isolate joining or degraded listeners from healthy-room timing.
- Local audio-focus/noisy-output interruptions become silent-follower state; explicit resume jumps
  to the room's current song/position atomically, while synchronization health reacquires separately.
- Media3 play/pause provenance prevents platform interruptions from being repaired into unwanted
  audio, and stale reconciliation cannot pause a healthy automatic track transition.
- Tight, Balanced, and Smooth local synchronization profiles share one bounded tuning source.
- Participant playback reports are coordinator-only repair traffic instead of room-wide telemetry
  fanout.
- Fresh reconnect challenge and transcript-bound authentication.
- Strict wire protocol 1 with no negotiation or fallback message shapes.
- Fresh Room schema 1 with no migrations or persisted room-session compatibility.
- SHA-256 content-addressed storage and authenticated resumable peer transfer.
- Android 11/13/16 LAN routing uses one process-local route authority: Android 14–16 NSD preserves
  all resolved addresses and the owning Android `Network`, Android 13 preserves the resolved owning
  network, Android 11–12 infer it from `LinkProperties`, and control/transfer sockets bind to that
  route; hotspot fallback is explicit and bounded.
- Nearby-network permission flow is explicit before room socket workflows on Android 13+ and
  LocalOnlyHotspot keeps the Android 11 location-permission path; Android 16 local-network opt-in can
  therefore be qualified without a discover-then-socket permission gap.
- Home and Room menus share a compact About surface with app version, protocol version, supported
  Android matrix, and the project source link.
- Audio MIME normalization rejects generic provider MIME values, prefers trustworthy metadata, and
  gives Media3 deterministic format hints for extensionless content-addressed files.
- Transfer diagnostics identify connect/handshake/body/verify/register failure phases and playback
  errors include normalized media/decoder/device context without private paths.
- Bounded command, queue, metadata, socket, import, and transfer inputs.
- Media3 system controls routed through synchronized room commands.
- Release builds use optimized resource shrinking, narrow R8 rules, and an APK-size gate.
- One bounded OpenTelemetry-shaped NDJSON diagnostic pipeline powers Logcat, room logs, and soak analysis.
- App-owned logs use the structured pipeline exclusively, with DEBUG command/timeline diagnostics,
  quiet normal sync/buffering levels, real observed timestamps, and omitted null fields.
- Room queue pending feedback is non-animated and room actions use a remembered immutable interaction
  bundle to keep normal scrolling/click feedback out of expensive recomposition paths.
- Room actions include a live searchable/filterable structured log console with explicit NDJSON copy.
- Local-only networking, no account, cloud service, telemetry, advertising, or billing.
