# Unison invariants

These are stronger than implementation details. A change to room, transfer, playback, storage, or
protocol code should preserve them or explicitly change the product contract.

## Canonical room

1. One serialized room actor is the only writer of canonical room state.
2. Queue and playback revisions never move backward.
3. Stale session/revision/callback results cannot overwrite newer state.
4. `ENDED` is terminal; a new room requires a new session.
5. A disconnected participant either reconnects within bounded grace or leaves canonical membership.

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

## Playback and synchronization

19. `PlayerExecutor` is the only Media3 mutation authority.
20. Wrong item/play-state/revision is repaired before position drift.
21. Unexecutable canonical media is waited on, not repeatedly forced into Media3.
22. Long scheduled commands re-evaluate changing clock mapping before execution.
23. Once room and player state are correct, reconciliation becomes quiet.
24. Transfer load must not become the owner of control/playback timing.

## Lifecycle and resources

25. Explicit app/task leave ends that phone's room session and synchronized playback.
26. Unrecoverable coordinator/network loss ends the room truthfully; no zombie room remains.
27. Leaving/ending releases transfer work, sockets, scheduled commands, locks, hotspot ownership, and
    session jobs.
28. Queues, retries, diagnostics, sockets, and jobs remain bounded.

## Security and privacy

29. Protocol 2 is strict: no negotiation/fallback decoder or unknown-field compatibility path.
30. First admission, reconnect, and file transfer are authenticated for their explicit purpose.
31. Received content is never trusted by filename/metadata; SHA-256 identity is authoritative.
32. Diagnostics never persist raw room secrets, PINs, authorization tokens/proofs, content URIs, or
    private application paths.
33. Unison remains local-only: no hosted runtime, analytics, account, advertising, or cloud relay.

## Engineering rule

A subsystem may be sophisticated, but it must own the information required to make its decision.
State changes should trigger reconciliation; reconciliation should produce a physical effect only when
that effect differs from what is already correct or underway.
