# Changelog

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
