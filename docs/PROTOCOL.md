# Local room protocol

## Transport

Unison uses separate TCP connections for control and file transfer. Android NSD advertises rooms on
the local network. Users join a discovered room with the four-digit code displayed on the coordinator. Discovery does not grant admission. QR codes, invitation URLs,
and deep-link credentials are not part of the product. Public addresses and DNS hostnames are
rejected.

## Control handshake

A new client sends its supported protocol versions, peer identity, listening port, room ID, a fresh
nonce, and an SRP-6a public value. The coordinator validates the request, reserves the nonce, applies
rate limits, and returns a fresh SRP salt/public value/server nonce. The client returns its proof; the
coordinator verifies it and returns a server proof plus the AES-GCM-wrapped random room secret.

Neither the four-digit code nor an offline-testable proof crosses the network. Reconnecting clients
instead send a nonce-bound proof derived from the active room secret. Authentication work is bounded
and timed out.

## Control frames

Protocol 1 frames contain a fixed authenticated header, bounded encrypted length, message UUID, and
a fresh AES-GCM nonce. The encrypted envelope contains room ID, coordinator term, sequence context,
sender, monotonic timestamp, and a typed body. Invalid versions, sizes, flags, ciphertexts, senders,
terms, sequences, expiries, room IDs, or message IDs fail closed.

## Canonical queue and transport

Peers submit commands; the coordinator serializes and validates them. Queue additions/removals are
batched, Clear Queue is one atomic mutation, and the canonical queue never exceeds 1,000 items.
Shuffle persists one deterministic shared upcoming order. Play Next is expressed relative to the
canonical current item.

Every transport command keeps its command ID through submission, coordinator acceptance, canonical
scheduling, local execution, settlement, supersession, or rejection. Play/Pause and Seek are
coalesced before canonical mutation so only the latest intent in each lane is ordered. Execution lead
is adaptive and bounded from 150 to 1,200 milliseconds using connected-peer readiness, measured RTT,
clock uncertainty, and reconnect state.

Pause never seeks. Play reuses an already aligned item without flushing the decoder. Next/Previous
resolve to an absolute queue item; when that item is not ready, the current item keeps playing while
the target receives priority preparation. A later navigation command replaces the prior target and
cancels obsolete prefetch work unless a transfer is already near completion.

## File transfer

The coordinator issues a short-lived authorization through the encrypted control channel. The file
socket sends only a derived authorization ID and completes a fresh client/server nonce challenge.
Proofs bind room, track, request, source, destination, resume offset, and both nonces.

After atomic authorization consumption, peers derive a transfer-session key. The response header and
every bounded 64 KiB chunk are independently AES-GCM protected with sequence and transfer context as
associated data. The destination validates offset, order, final size, and SHA-256 before commit.

## Version contract

Application 1.0.0 supports protocol 1 only. Incompatible framing or credential changes require an
explicit protocol-version increase and end-to-end tests; speculative compatibility branches are not
kept in the release tree.
