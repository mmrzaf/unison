# Architecture

## Product model

Unison behaves as one shared music player across several phones. Each phone plays a verified local
copy. One temporary coordinator orders commands and publishes canonical queue and playback state; it
is not a privileged product role.

## Application surfaces

- **Home:** room creation, nearby rooms, library search, playlists, imports, and storage management in
  one scrolling surface.
- **Room:** room identity/code, canonical player, listeners, and queue in one scrolling surface.

Focused tasks use dialogs or sheets. There is no destination tab bar and no alternate player truth in
the UI.

## State ownership

`RoomRuntime` is the Android/session orchestrator and owns one bounded `SerializedEventLoop` for
canonical state. `RoomReducer` performs deterministic mutations. The actor is the only writer of room
state.

Focused components own policies and effects:

- `ControlAdmissionController`: first admission and reconnect authentication;
- `PlaybackSessionCoordinator`: playback revision, cadence, and repair throttling;
- `CanonicalPlaybackCoordinator`: exact local/peer convergence;
- `CanonicalPlaybackDispatcher`: ordered timestamped work and replaceable latest-state reconciliation;
- `PlayerMutationCoordinator`: the only Media3 mutation boundary;
- `TransferManager`: authorized upload/download lifecycle;
- `RoomStore`: structural, playback, and transfer flows for UI consumption.

High-frequency position telemetry does not enter the room actor. Only meaningful player transitions
do.

## Command flow

`RoomCommandBus` has bounded general and transport mailboxes owned by `UnisonRoomService`.

- General commands preserve FIFO ingress. Long repository work completes asynchronously with
  generation/revision fences.
- Play/pause and seek intent are coalesced before canonical mutation.
- Previous, next, and explicit queue selection remain discrete.
- Every asynchronous result carries enough identity to be rejected after clear, leave, reconnect,
  queue mutation, or newer playback intent.

## Canonical convergence

A peer is correct only when it has the latest queue revision, playback revision, queue item, and
play/pause intent. Position correction happens after those invariants match. Reconnect performs a
complete state application before the listener is considered converged.

## Storage

Room schema 1 contains only tracks, track sources, playlists, and playlist entries. Active room state
is memory only.

Managed files are content-addressed by SHA-256. Staging, complete verification, atomic commit,
operation locks, and reason-scoped leases protect imports, playback, cleanup, and transfer.

## Network lifecycle

Android NSD and LocalOnlyHotspot callbacks are generation-bound. Stale callbacks cannot clear newer
state. Discovery, registration, sockets, multicast locks, Wi-Fi locks, transfer jobs, and session jobs
have explicit owners and teardown paths.

## Scale boundaries

- room members: 8;
- room queue: 1,000 items;
- one audio file: 1 GiB;
- M3U: 4 MiB, 10,000 entries, 8,192 characters per line;
- concurrent inbound admissions: 24;
- concurrent uploads: 3, at most one per destination;
- library presentation: Room/Paging;
- player timeline: moving window around the current item.
