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
- **Storage:** Room database with an exported baseline schema, artwork cache, and content-addressed
  managed files.
- **Room:** pure reducer plus serialized actor orchestration, peer registry, role engines, message router,
  legacy-session cleanup, and control-admission controller.
- **Network:** Android NSD, LocalOnlyHotspot, private-address policy, control sockets, and file
  sockets.
- **Protocol:** authenticated frames, PIN proof, encrypted room-secret exchange, replay-resistant
  identifiers, and bounded payloads.
- **Transfer:** single-use authorization, resumable writes, final size and SHA-256 verification.
- **Playback:** Media3 player, media session, queue windowing, scheduled transport, and drift
  correction.

## State ownership

`RoomReducer` is the deterministic authority for canonical mutations. `RoomRuntime` serializes
accepted mutations and owns Android lifecycle orchestration while focused components handle peer bookkeeping,
routing, admission, legacy-session cleanup, and role policy. `RoomStore` publishes structural state separately from
playback and transfer telemetry. UI and players consume state; they do not mutate canonical state directly.

## Storage integrity

Track identity is the lowercase SHA-256 digest of exact file bytes. Imports write to a staging file,
flush, best-effort sync, verify identity, and then commit to the final content-addressed path.
Existing final files are reused only when size and digest match. Transfers use `.part` files and
become visible as final tracks only after complete digest verification.

## Scale controls

- Room queue: 1,000 items
- One queue-add command: 100 tracks
- One audio file: 1 GiB
- M3U file: 4 MiB, 10,000 entries, 8,192 characters per line
- Inbound sockets: 24 concurrent admissions
- Library UI: Room/Paging rather than full materialization
- Player queue: moving window around the active item

## Offline boundary

Runtime communication is limited to local/private addresses. The current discovery, invitation,
and direct-endpoint protocol advertises IPv4 addresses only; complete IPv6 endpoint exchange is not
implemented. Invitation links are accepted only for the `unison://join` scheme, current protocol,
six-digit PIN, valid local IPv4 address, and bounded metadata. No source code contains a remote HTTP endpoint.
