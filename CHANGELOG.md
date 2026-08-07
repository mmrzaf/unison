# Changelog

## 1.0.0

Fresh production release.

- Two continuous application surfaces: Home and Room.
- Shared queue, room-wide transport controls, playlists, local imports, and nearby discovery.
- Shuffle is a one-shot canonical queue action; repeat is the only persistent playback mode.
- Canonical queue and playback revisions with stale-work rejection and automatic peer repair.
- READY playback-cohort leases isolate joining or degraded listeners from healthy-room timing.
- Local audio-focus/noisy-output interruptions become silent-follower state; explicit Rejoin jumps
  to the room's current song/position and only returns to READY after fresh synchronization.
- Media3 play/pause provenance prevents platform interruptions from being repaired into unwanted
  audio, and stale reconciliation cannot pause a healthy automatic track transition.
- Tight, Balanced, and Smooth local synchronization profiles share one bounded tuning source.
- Participant playback reports are coordinator-only repair traffic instead of room-wide telemetry
  fanout.
- Fresh reconnect challenge and transcript-bound authentication.
- Strict wire protocol 1 with no negotiation or fallback message shapes.
- Fresh Room schema 1 with no migrations or persisted room-session compatibility.
- SHA-256 content-addressed storage and authenticated resumable peer transfer.
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
