# ADR-007: Playback effects are not a second state owner

**Status:** Accepted (amends ADR-001)

## Context

ADR-001 requires one canonical room actor. The playback path still broke that rule: a dedicated
worker read and wrote actor-owned state (media window, prepared set, transfer demands, timeline
jobs) and called back into the actor's dependencies, while several session jobs did the same. Locks
and generation checks grew around the leak instead of removing it, and the actor and the playback
worker could wait on each other.

## Decision

- The room actor computes **all** room state, including playback preparation, media readiness, the
  transfer demand window, and track-played bookkeeping.
- A separate ordered **player-effect queue** performs Media3 calls from an already-decided input. It
  never reads or writes room state and never calls synchronously into the actor.
- Timers only wait, then submit an event. They do not run room or playback work themselves.
- Values other threads need are published as immutable snapshots (the clock mapping, the room
  projections, the sync cadence) instead of being shared under a lock.
- Locks are removed from actor-owned state rather than added around it. A new `synchronized` or
  `ConcurrentHashMap` in room/session code is treated as a design smell in review.

## Consequences

- Actor turns may suspend on IO or player work; that is allowed. Edges **out** of the actor must not
  depend on actor-owned state changing underneath them.
- Actor turns may await the player. That wait is bounded: scheduled commands only reserve a ticket
  and run on their own coroutine, and the remaining player calls hold one Media3 operation. The
  harness proves it (scenario S15): with a player that takes 400 ms per call and 120 transport
  commands in flight, no member is dropped and the worst actor queue wait stays under 2.5 s, far
  inside the 5 s heartbeat and 30 s peer timeout.
- A non-suspending handler would additionally require moving rejoin, synchronization and queue refresh
  (read-await-recheck flows) into a separate playback director. That is a design for a later release,
  not a stability fix for this one.
- The only locks in `room/` are the genuine boundaries `AdmissionGuard` (network threads),
  `QueuePreparationFence` and `SessionJobRegistry`; `scripts/check-source-tree.py` rejects any other
  `synchronized`, concurrent map or mutex there.
- Shutdown must stop the actor before tearing down the state it owns; the runtime cancels the actor
  loop first and only then clears session fields.
