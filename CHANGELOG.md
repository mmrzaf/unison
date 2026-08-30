# Changelog

## 1.2.0

- Natural Media3 end-of-item signals now hand off exactly once to canonical room ownership; an unready successor is prepared urgently, waited for without skipping, and started automatically when ready. Explicit Next uses the same exact-successor convergence model.
- Transient audio-focus interruption now converges back into the live room automatically after platform suppression, room clock, and media readiness recover; becoming-noisy/unsuitable-output conditions remain intentionally silent until a user rejoins.
- Content readiness is independent from audible participation, so temporarily silencing a device cannot erase verified media readiness or deadlock playback.
- Room playback controls now expose Play, Pause, Prepare, Preparing, Recovering, and Preparing next song from actual runtime state, while a solo coordinator normalizes to 1x and stops pointless self-drift correction.
- Release diagnostics now reject the real 1.2.0 qualification failure shapes: missing natural-boundary handoff, post-end repair, empty readiness, generic SYSTEM_POLICY inhibition, stuck automatic rejoin, unlocked-clock projection, impossible unavailable-media commands, dirty teardown, and materially late playback. Arrival lateness and PlayerExecutor lateness are reported separately.
- Sanitized regression fixtures derived from the physical-device incidents are part of the release-quality gate.

- Media readiness is explicit: unavailable queue items are prepared first and only verified room-ready items can be played.
- Transfer orchestration has one capacity model and one coordinator-side lifecycle owner; same-peer admission, cancellation, retry/backoff, and duplicate demand are deterministic.
- Active useful transfers are no longer blindly preempted by changing speculative demand; partial verified progress is preserved and genuine failures resume with bounded retry.
- Playback execution gates unavailable media instead of repeatedly mutating Media3, eliminating the 1.1.0 unavailable-song repair storm.
- Room lifecycle is truthful: app/task exit leaves the room, temporary connectivity loss enters bounded recovery, unrecoverable coordinator/network loss ends the room, and stale participants are removed after reconnect grace.
- Scheduled playback rechecks changing coordinator clock mapping during long waits so clock reacquisition cannot leave stale multi-second timers.
- Room UX distinguishes Ready, Preparing, and Needs preparation; unavailable songs prepare on tap, ready songs play, and music can be inserted with Add next or appended with Add to queue.
- Reliability coverage adds deterministic high-count transfer/readiness stress, repeated interrupted-file resume checks, causal transfer diagnostics, and a strict stability-log analyzer.
- The public architecture is documented around explicit invariants and Protocol 2 remains the strict 1.2.0 wire contract; no incompatible Protocol 3 change was required.

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
