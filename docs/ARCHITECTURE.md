# Architecture

## Product model

Unison is a temporary local listening room, not a streaming service or a general file-sync system.
Nearby phones deliberately reach the same verified playable state, then execute one canonical room
playback intent together.

The architecture is optimized for four outcomes:

1. **truthful state** — the UI never pretends a dead room or unavailable song is usable;
2. **deterministic control** — one serialized authority orders queue/playback mutations;
3. **monotonic preparation** — useful file progress converges toward verified READY content;
4. **quiet steady state** — once healthy, the system stops doing unnecessary repair work.

Complexity is accepted when it buys correctness, recovery, responsiveness, security, or measured
transfer efficiency. It is rejected when it creates split ownership or repeated physical work.

## State ownership

`RoomRuntime` is the Android/session composition root. It wires network, storage, playback, clock,
and room components together and owns one bounded `SerializedEventLoop<RoomEvent>`. The room actor
is the only writer of canonical room state.

Focused components own decisions that need independent reasoning:

- `RoomReducer` — deterministic canonical queue/member/playback mutations;
- `ControlAdmissionController` — first admission and reconnect authentication;
- `PlaybackSessionCoordinator` — playback revision/reference lifecycle;
- `CanonicalPlaybackCoordinator` — exact local/peer canonical convergence;
- `PeerPlaybackHealthRegistry` — coordinator-side playback/clock readiness leases;
- `RoomMediaReadinessPolicy` — derived `NEEDS_PREPARATION / PREPARING / READY` state;
- `TransferCoordinator` — transfer demand, admission, active routes, and route retry/backoff;
- `TransferCapacityPolicy` — one capacity model shared by coordinator policy and transport guards;
- `TransferManager` — authenticated resumable upload/download execution and verified commit;
- `PlayerExecutor` — the only Media3 mutation authority;
- `PlaybackSynchronizationRuntime` — local drift/correction policy outside the room actor;
- `RoomReconnectPolicy` — bounded coordinator/network/peer recovery windows;
- `DiagnosticLog` — one bounded structured observability sink.

The rule is simple: policy owners decide; effect executors execute; effects report typed outcomes back.
No subsystem should reach sideways and manipulate another subsystem's internal lifecycle.

## Canonical state vs runtime state

Canonical room history contains shared product truth:

- members;
- queue/order;
- canonical playback intent;
- monotonic queue/playback revisions;
- room options.

Transient runtime facts do **not** belong in canonical history:

- socket state;
- transfer progress;
- source assignment;
- retry timers;
- local file availability;
- preparation progress;
- clock acquisition details.

Those facts are derived locally/coordinator-side and are fenced by session/revision identity.

## Prepare and Play

Preparation and playback are separate operations.

A queue item becomes room-ready only when every listener required for synchronized playback has the
exact verified local content. The local device also requires its own verified file before it may
execute Media3 playback.

- `NEEDS_PREPARATION`: tap means Prepare.
- `PREPARING`: transfer is useful work in progress.
- `READY`: tap means Play/select canonically.

Background prefetch may prepare likely upcoming content automatically, but it never creates hidden
"prepare then replay an old command" state. Canonical playback repair is suppressed while a peer
cannot execute the target media; queue/revision repair remains independent.

## Transfer architecture

Transfer exists only to turn demanded content into verified READY content.

`TransferCoordinator` is the coordinator-side lifecycle owner. It tracks logical demand, admitted
routes, and genuine route backoff inside the serialized room actor.

The shared capacity policy currently allows:

- up to 2 inbound transfers per destination;
- up to 3 outbound transfers per source;
- at most 1 active transfer for a specific source→destination pair;
- at most 1 active route for a track→destination demand.

That permits useful multi-peer concurrency without opening a same-pair socket that can only wait
behind another upload.

Priorities primarily choose **what starts next**. An already admitted useful BODY transfer is not
blindly preempted because speculative playback demand changed.

`TransferManager` executes a granted assignment:

1. route/bind and connect;
2. authenticate the single-use authorization;
3. resume from the verified partial offset;
4. stream bounded authenticated records;
5. verify final SHA-256;
6. atomically commit the managed file;
7. report a typed terminal result.

Intentional cancellation is semantically different from network failure and must never penalize a
route or create a retry storm.

## Playback and synchronization

One canonical timeline defines which ready item should play, play/pause intent, and room position.
`PlayerExecutor` serializes every Media3 mutation behind one mutex. Media3 does not independently
author canonical song progression.

A peer may know canonical desired state while being temporarily unable to execute it. In that case it
waits for media or clock recovery rather than repeatedly issuing impossible player mutations.

Clock synchronization and drift correction are local. Long scheduled waits periodically re-evaluate
the coordinator→local mapping, so clock reacquisition cannot leave a stale sleep target several
seconds in the future.

Healthy steady state should be quiet: occasional clock/reference traffic and useful background
preparation, not constant seek/repair/reassignment churn.

## Room lifecycle

A room is a live network session, not a stale UI object.

```text
CREATING / JOINING
        ↓
      ACTIVE
        ↓
   RECONNECTING
      ↙    ↘
   ACTIVE   ENDED
```

Temporary connectivity loss pauses local synchronized output and gets bounded recovery. Explicit
leave/task exit, coordinator shutdown, hotspot loss that cannot recover, or exhausted coordinator
reconnect ends the session. `ENDED` is terminal.

Unexpected participant loss gets a short reconnect grace. If it does not return, canonical membership
and associated transfer/preparation state are removed. No ghost listener remains indefinitely.

Unison 1.2.0 deliberately does **not** elect a replacement coordinator. Surviving coordinator death
would require a different availability contract and substantially more distributed-state machinery.

## Control and network boundary

Control and file traffic use separate TCP connections. Control traffic has bounded priority classes so
large music movement cannot starve room commands or clock traffic.

Android NSD and LocalOnlyHotspot callbacks are generation-bound. The process-local route authority
preserves the owning Android `Network` where available and binds control/transfer sockets consistently.
Public addresses and DNS joins are rejected.

## Storage

Room database schema 1 stores tracks, track sources, playlists, and playlist entries only. Active
room state is memory-only.

Managed media is content-addressed by SHA-256. Staging, resumable partials, operation locks,
reason-scoped leases, complete verification, and atomic commit protect imports, playback, cleanup,
and transfer.

## Diagnostics

`DiagnosticLog` is the only application observability sink. Events are structured, bounded, sanitized,
and local-only. Transfer attempts carry operation/assignment correlation IDs so coordinator, source,
and destination lifecycle can be reconstructed without logging authorization secrets.

Release analysis checks both playback and stability invariants; diagnostics are evidence, not another
source of room truth.

## Scale boundaries

- room members: 8;
- room queue: 1,000 items;
- one audio file: 1 GiB;
- M3U: 4 MiB, 10,000 entries, 8,192 characters per line;
- concurrent inbound admissions: 24;
- transfer capacity: shared `TransferCapacityPolicy` values above;
- library presentation: Room/Paging;
- player timeline: bounded moving window around current playable content.
