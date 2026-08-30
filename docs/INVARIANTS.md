# Unison invariants

These are stronger than implementation details. A change to room, transfer, playback, storage, or
protocol code should preserve them or explicitly change the product contract.

## Canonical room

1. One serialized room actor is the only writer of canonical room state.
2. Queue and playback revisions never move backward.
3. Stale session/revision/callback results cannot overwrite newer state.
4. `ENDED` is terminal; a new room requires a new session.
5. A disconnected participant either reconnects within bounded grace or leaves canonical membership.

### Async provenance

- Any asynchronous work that may mutate current room state carries enough immutable provenance to
  prove at the authoritative mutation boundary that it still belongs to the current room/session.
- Session-bound results are validated when the room actor consumes them. A producer-side generation
  check may reduce unnecessary work, but is not the correctness boundary.
- Connection-bound work is valid only while the exact connection that produced it remains
  authoritative for that peer. Room/session and connection provenance are separate requirements.
- Device-global observations such as local address/hotspot state intentionally span room generations
  and are interpreted against current state when consumed; they are not historical session results.
- A stale result is discarded as ordinary superseded work. It must not cancel or terminate the
  persistent room actor.
- A successfully authenticated inbound control connection may enter canonical state only while the
  exact room ID and session generation captured at final admission are still current.
- A remote envelope may affect replay state, peer liveness, or room state only while the exact
  `ControlConnection` that decoded it remains authoritative for that peer.

## Media readiness

6. READY means verified media exists for every listener required for synchronized playback.
7. A phone may execute playback only when its own exact file is locally verified.
8. Prepare and Play are separate intents: preparing media does not secretly mutate canonical playback.
9. Repeating Prepare for the same queue item is idempotent.
10. Repeated desired state while media is unavailable causes no repeated Media3 mutation storm.

## Transfer

11. At most one active route exists for a track→destination demand.
12. All transfer admission uses one shared capacity policy.
13. A source→destination pair never exceeds its configured active-stream capacity.
14. Duplicate assignment/demand must not destroy healthy in-flight work.
15. Useful partial progress is retained across genuine interruption and resumes from the stored offset.
16. Intentional cancellation is never classified as route/network failure.
17. One genuine failure produces one bounded retry decision; retry timers do not multiply.
18. A file becomes complete/READY only after final SHA-256 verification and atomic commit.
19. Transfer progress, completion, and failure may affect current room state only while they belong to
    the active session generation at the actual mutation boundary.
20. An upload streams only from a managed file whose readability was resolved/repaired first and whose
    exact final path was then leased atomically against deletion.
21. Logical managed-file deletion either removes bytes immediately or creates a durable pending-delete
    obligation that completes when the final lease is released.
22. Publication of a legitimate managed reference blocks deletion without blocking corruption repair or
    verified replacement. Successful publication reconciles any pending deletion while that publication
    lease is held; a later logical delete creates a new deletion obligation.

## Playback and synchronization

23. `PlayerExecutor` is the only Media3 mutation authority.
24. Wrong item/play-state/revision is repaired before position drift.
25. Unexecutable canonical media is waited on, not repeatedly forced into Media3.
26. Long scheduled commands re-evaluate changing clock mapping before execution.
27. Once room and player state are correct, reconciliation becomes quiet.
28. Transfer load must not become the owner of control/playback timing.
29. A final item that paused because of a genuine natural terminal boundary has one canonical replay
    meaning: a subsequent Play restarts that item at position 0. A manual seek to the media duration is
    not equivalent to a terminal natural boundary.
30. One physical natural media boundary is attributed to the item that actually ended and produces at
    most one boundary revision, independent of Media3 callback ordering.
31. On Android API 30-32, inability to query the actual active media route is represented as `UNKNOWN`;
    connected-device inventory is never promoted into a confident active route.
32. MediaSession advertises only commands that Unison can translate faithfully into canonical room
    behavior.

## Lifecycle and resources

33. Explicit app/task leave ends that phone's room session and synchronized playback.
34. Unrecoverable coordinator/network loss ends the room truthfully; no zombie room remains.
35. Leaving/ending releases transfer work, sockets, scheduled commands, locks, hotspot ownership, and
    session jobs.
36. Queues, retries, diagnostics, sockets, and jobs remain bounded.

## Security and privacy

37. Protocol 2 is strict: no negotiation/fallback decoder or unknown-field compatibility path.
38. First admission, reconnect, and file transfer are authenticated for their explicit purpose.
39. Received content is never trusted by filename/metadata; SHA-256 identity is authoritative.
40. Diagnostics never persist raw room secrets, PINs, authorization tokens/proofs, content URIs, or
    private application paths.
41. Unison remains local-only: no hosted runtime, analytics, account, advertising, or cloud relay.
42. A participant endpoint may advertise a transfer port, but its canonical host address is derived
    from the authenticated control socket; an admitted peer cannot redirect transfers to another LAN host.
43. Under sustained lower-priority control traffic, guaranteed canonical messages and clock traffic are
    not starved. Priority is a bounded-latency/no-starvation contract; simultaneous-ready messages do not
    require a mathematically total arrival order.
44. The room-code SRP arithmetic remains covered by a published SRP-6a conformance vector. JVM
    modular exponentiation is not claimed to be constant-time; that residual limitation is explicit in the
    1.2 security review and must not be "fixed" with unaudited custom cryptography.

## Engineering rule

A subsystem may be sophisticated, but it must own the information required to make its decision.
State changes should trigger reconciliation; reconciliation should produce a physical effect only when
that effect differs from what is already correct or underway.
