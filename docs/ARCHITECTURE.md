# Architecture

## Goal

Unison coordinates synchronized playback without streaming audio from a server. Each peer stores and
plays the same SHA-256-identified bytes locally. One peer temporarily orders room commands and
defines the shared monotonic timeline; it is not an account owner or privileged UI role.

## Layers

- **UI:** Jetpack Compose feature screens and permission flow. `MainViewModel` composes immutable
  structural, playback, transfer, library, playlist, and import flows while focused action classes own workflows.
- **Application:** `AppContainer`, `RoomCommandBus`, settings, and shared state ownership.
- **Library:** bounded imports, content metadata, playlists, and M3U interoperability.
- **Storage:** Room database with an exported baseline schema and content-addressed managed files.
- **Room:** pure reducer plus serialized actor orchestration, peer registry, role engines, message router,
  memory-only session cleanup, and control-admission controller.
- **Network:** Android NSD, LocalOnlyHotspot, private-address policy, control sockets, and file
  sockets.
- **Protocol:** AES-GCM encrypted directional frames, four-digit SRP-6a admission, reconnect proof,
  encrypted room-secret exchange, replay-resistant identifiers, and bounded payloads.
- **Transfer:** nonce-bound single-use authorization, encrypted/authenticated chunk streaming,
  resumable writes, final size, and SHA-256 verification.
- **Playback:** Media3 player, a single transport-control media notification, queue windowing,
  scheduled transport, and one canonical monotonic timeline. Every local player—including the
  coordinator—uses the same bounded drift controller; only clock mapping differs by role.

## State ownership

`RoomReducer` is the deterministic authority for canonical mutations. `RoomRuntime` serializes
accepted mutations and owns Android lifecycle orchestration while focused components handle peer bookkeeping,
routing, admission, memory-only session cleanup, and role policy. Accepted event completions are tied to
the serialized loop lifecycle, so cancellation and shutdown cannot leave callers suspended indefinitely. `RoomStore` publishes structural state separately from
playback and transfer telemetry. UI and players consume state; they do not mutate canonical state directly.


## Command and Android callback ownership

`RoomCommandBus` exposes independent receiving lanes owned only by `UnisonRoomService`; it deliberately
has no merged receiving flow because multiple channel collectors would compete and could steal commands.
Fixed worker pools bound command concurrency while the room actor remains the sole canonical-state writer.

NSD registration, Android 11–13 discovery/resolution, and LocalOnlyHotspot callbacks use exact
listener/request generations. Stale callbacks cannot clear newer state, late hotspot reservations are closed,
and cancellation stops discovery and releases the multicast lock exactly once.

## Storage integrity

Track identity is the lowercase SHA-256 digest of exact file bytes. Imports write to a staging file,
flush, best-effort sync, verify identity, and then commit to the final content-addressed path.
Existing final files are reused only when size and digest match. Transfers use `.part` files and
become visible as final tracks only after complete digest verification. Per-track operation locks serialize
replacement downloads, while reason-scoped leases prevent cleanup or verified-file replacement during
playback, queue use, indexing, pending side effects, or upload.

## Scale controls

- Room queue: 1,000 items
- One queue-add command: up to the remaining 1,000-item capacity in one batch
- One audio file: 1 GiB
- M3U file: 4 MiB, 10,000 entries, 8,192 characters per line
- Inbound sockets: 24 concurrent admissions
- Library UI: Room/Paging rather than full materialization
- Player queue: moving window around the active item

## Offline boundary

Runtime communication is limited to loopback, link-local, and private site-local addresses. Android
NSD discovers rooms; users enter the room's four-digit code. There are no QR codes, invitation URLs,
deep links, DNS joins, or remote HTTP endpoints. private IPv4 and IPv6 endpoints, including scoped
link-local IPv6, are supported; public addresses and DNS hostnames are rejected.

## Playback isolation

Playback position is presentation telemetry and publishes directly to the playback state flow. Only
meaningful transitions—item changes, play intent, end, error, seek revision, and repeat transition—
enter the serialized room actor. This keeps joins, heartbeats, queue mutations, and recovery independent
from progress-indicator frequency. Media3 owns the only foreground notification path.

Transport input has a dedicated bounded mailbox and a correlated lifecycle. Play/Pause and Seek are
coalesced before canonical mutation, while Next/Previous remain intentional discrete navigation.
`PlayerMutationCoordinator` is the single Media3 write boundary: scheduled transport invalidates stale
operations, synchronization yields while transport is pending, and timeline maintenance defers until
the active transport generation settles.

## Canonical playback reconciliation

Canonical room mutations no longer enqueue one best-effort player side effect per event. The
`CanonicalPlaybackDispatcher` is the serialized boundary between room state and Media3:

- timestamped transport operations remain ordered and use backpressure;
- replaceable queue, preparation, options, and playback-mode changes collapse to one reconciliation
  token that reads the latest submitted `RoomSnapshot`;
- `DesiredPlaybackState` provides a deterministic content revision so unrelated member/telemetry
  updates do not rebuild the player timeline;
- failures are surfaced through typed `PlaybackFailure` and `RoomIssue` values rather than relying
  on message text as identity.

`PlayerEventInterpreter` separately owns Media3 callback revisions, end-event deduplication, natural
transition classification, and transition-loop detection. `RoomRuntime` receives explicit decisions
and does not infer canonical intent from raw player callback state.
