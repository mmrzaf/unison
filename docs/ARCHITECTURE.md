# Unison architecture

## Product boundary

Unison is a synchronized playback layer, not a streaming service or a replacement music platform. Users bring local audio. A room owns one queue and one canonical timeline. Files are transferred before playback and decoded locally on every device.

## Runtime topology

```text
                         silent coordinator
                  canonical state + shared clock
                    /          |          \
              control TCP  control TCP  control TCP
                  /              |              \
               peer A          peer B          peer C
                  \____________ file TCP __________/
```

Every device runs the same server. The room creator begins as coordinator. The coordinator:

- accepts authenticated control connections;
- serializes friend commands into one sequence;
- broadcasts canonical mutations;
- performs clock ping/pong replies;
- tracks which peers hold each upcoming hash;
- assigns a source for each missing track.

It is not an administrator role. Room options default to collaborative queue and transport control. File bytes may flow directly between any two peers.

## Main components

### `UnisonRoomService`

A foreground `MediaSessionService` owns the room runtime and Media3 player. The Activity can be recreated without tearing down networking or playback. Commands enter through `RoomCommandBus`. The service exposes a standard Android media session, but only trusted system/media controllers are accepted and only play, pause, seek, previous, and next commands are granted. Notification, lock-screen, headset, and in-app controls therefore use the same synchronized room command path instead of mutating one phone directly.

### `RoomRuntime`

The orchestration boundary. It owns discovery, sockets, clock sync, transfer scheduling, coordinator recovery, room snapshots, and playback side effects. Canonical mutations are serialized with a mutex; ordered side effects use a channel.

### `RoomReducer` and `RoomEngine`

`RoomReducer` is pure. Given a snapshot, command, and coordinator time, it returns either a rejection or one or more canonical mutations. `RoomEngine` protects the current snapshot with a coroutine mutex. Networking never directly mutates canonical state.

### `NsdRoomDiscovery`

Advertises `_unison._tcp.` through Android NSD. Android 11–13 resolution is serialized because legacy resolver implementations are unreliable when several services resolve concurrently. QR invitations provide a direct-address fallback.

### `PeerServer` and `ControlConnection`

One server port accepts both control and file handshakes. Control connections use one reader and one ordered writer coroutine. A stale socket cannot remove a newer connection for the same persistent peer ID.

### `TransferManager`

Transfers one incoming and one outgoing track at a time per device. Transfers are resumable from a partial file, bounded to 1 GiB, checked against available space, and finalized only after exact SHA-256 verification. The source explicitly acknowledges installation of a one-time authorization before the destination connects.

### `ClockSyncEngine`

Maps local monotonic time to coordinator monotonic time using repeated four-timestamp ping/pong samples. It uses the median of low-round-trip samples and slowly adjusts established offsets.

### `PlaybackSyncEngine`

- drift under 35 ms: ignore;
- drift from 35 to 99 ms: temporary 0.99× or 1.01× correction;
- drift at least 100 ms: seek.

Scheduled commands use a 1.2-second lead. A joining guest must establish a clock mapping and prepare the current track before the room marks it ready.

## Data lifecycle

### File picker and share imports

All imported audio is copied once into Unison's content-addressed app-private store before it is promised to the library, a playlist, or a room. This deliberately trades additional device storage for reliable playback, hashing, and peer transfer that do not depend on document-provider lifetime or source-file movement. Importing identical bytes again reuses the existing SHA-256 file rather than creating another Unison copy.

### M3U interoperability

Direct `content://` and `file://` entries resolve immediately. Known library files are matched by filename. If relative entries remain, the UI asks for the containing music folder once through the Storage Access Framework, indexes that selected tree with bounded depth/count, and rebuilds the imported playlist in source order. M3U8 export never writes app-private paths; managed tracks use relative original filenames because exported playlists do not include audio files.

### Received tracks and retention

Tracks received from peers use the same content-addressed store and default to a 24-hour policy. Promoting a track to the library changes retention metadata only; the bytes are not copied again. The Library shows total, kept, and temporary storage. Clearing temporary music requires confirmation and protects tracks used by an active room.

### Content-addressed storage

```text
files/tracks/<first two hash chars>/<64-char SHA-256>
```

Partial transfers use the same path with `.part`. Filenames supplied by peers are never used for paths.

### Database

Room stores track metadata, source records, playlists, ordered playlist entries, and diagnostic room snapshots. The active room remains memory-authoritative.

## State and ordering

Each canonical mutation carries:

- room ID;
- coordinator term;
- coordinator peer ID;
- monotonically increasing sequence;
- unique message ID.

Guests reject wrong-room, wrong-term, non-coordinator, duplicate, stale, and gapped canonical mutations. A gap triggers a full snapshot request.

## Coordinator recovery

When the coordinator connection times out, a guest first attempts direct reconnection. If that fails, all peers deterministically choose the lowest persistent peer ID among the last known connected members. The winner increments the term, re-advertises, and marks remote members disconnected until they reconnect. Old-term canonical traffic is ignored.

This is intentionally best-effort rather than a full consensus implementation. It handles ordinary phone departure and transient Wi-Fi loss without adding Raft-level complexity.
