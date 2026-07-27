# Architecture

## Goal

Unison coordinates synchronized playback without streaming audio from a server. Each peer stores and plays the same SHA-256-identified bytes locally. One peer temporarily orders room commands and defines the shared monotonic timeline; it is not an account owner or privileged UI role.

## Layers

- **UI:** Jetpack Compose screens and permission flow. UI state is exposed through `MainViewModel` and immutable flows.
- **Application:** `AppContainer`, `RoomCommandBus`, settings, and shared state ownership.
- **Library:** bounded imports, content metadata, playlists, and M3U interoperability.
- **Storage:** Room database with an exported baseline schema, artwork cache, and content-addressed managed files.
- **Room:** pure reducer plus serialized engine and runtime orchestration.
- **Network:** Android NSD, LocalOnlyHotspot, private-address policy, control sockets, and file sockets.
- **Protocol:** authenticated frames, PIN proof, encrypted room-secret exchange, replay-resistant identifiers, and bounded payloads.
- **Transfer:** single-use authorization, resumable writes, final size and SHA-256 verification.
- **Playback:** Media3 player, media session, queue windowing, scheduled transport, and drift correction.

## State ownership

`RoomReducer` is the deterministic authority for canonical mutations. `RoomRuntime` serializes accepted mutations, broadcasts their sequence, schedules playback, manages peers, and reconciles availability. UI and players consume canonical state; they do not mutate it directly.

## Storage integrity

Track identity is the lowercase SHA-256 digest of exact file bytes. Imports write to a staging file, flush, best-effort sync, verify identity, and then commit to the final content-addressed path. Existing final files are reused only when size and digest match. Transfers use `.part` files and become visible as final tracks only after complete digest verification.

## Scale controls

- Room queue: 1,000 items
- One queue-add command: 100 tracks
- One audio file: 1 GiB
- M3U file: 4 MiB, 10,000 entries, 8,192 characters per line
- Inbound sockets: 24 concurrent admissions
- Library UI: Room/Paging rather than full materialization
- Player queue: moving window around the active item

## Offline boundary

Runtime communication is limited to IPv4/IPv6 loopback, link-local, or private site-local addresses. Invitation links are accepted only for the `unison://join` scheme, current protocol, six-digit PIN, valid local IPv4 address, and bounded metadata. No source code contains a remote HTTP endpoint.
