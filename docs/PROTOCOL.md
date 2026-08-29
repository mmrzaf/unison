# Protocol 2

Protocol 2 is Unison 1.2.0's only wire contract. There is no negotiation or fallback to protocol 1,
no compatibility decoder, and no alternate message shape. Unknown fields, missing required fields,
invalid enum values, and a protocol value other than `2` are rejected.

Protocol 3 was deliberately **not** introduced for 1.2.0: the readiness, transfer-orchestration,
lifecycle, and diagnostics changes fit Protocol 2 without weakening its semantics. Persistent peer
data sessions remain a future experiment and are not part of this contract.

## Network boundary

Unison uses Android NSD to advertise rooms on the current private network. Advertisements include
room identity, display name, coordinator term, port, and protocol value. Discovery grants no access.
Public addresses and DNS hostnames are rejected.

Control and file transfer use separate TCP connections. Every accepted input is length-bounded and
time-bounded before expensive processing begins.

## Handshake messages

The first message has one explicit purpose:

- `pin_client_hello`: first admission with peer identity, room identity, endpoint, nonce, and SRP-6a
  public value;
- `reconnect_client_hello`: reconnect request using the active room secret;
- `file_client_hello`: one authorized transfer request.

A coordinator never infers connection purpose from nullable fields.

### First admission

1. Client sends `pin_client_hello` with a fresh nonce and SRP public value.
2. Coordinator validates metadata, protocol, room, identity, limits, and nonce reuse.
3. Coordinator returns a fresh SRP challenge.
4. Client returns its proof.
5. Coordinator verifies it and returns its proof plus the room secret encrypted under the negotiated
   SRP session key.

The four-digit code itself never crosses the network and is not exposed as an offline-testable hash.

### Reconnect

1. Client sends `reconnect_client_hello` with a fresh client nonce.
2. Coordinator returns a fresh server nonce.
3. Client proves possession of the active room secret over the complete transcript.
4. Only after proof verification does the coordinator accept or replace a control connection.

A captured reconnect request or proof cannot authenticate a later connection.

## Control framing

Each control frame contains a fixed authenticated header, protocol value, flags, ciphertext length,
message UUID, and fresh AES-GCM nonce. Direction-specific session keys prevent reflection between
client→coordinator and coordinator→client traffic.

The encrypted envelope contains protocol value, room ID, coordinator term, sender peer ID, sequence
context, monotonic timestamp, and one typed body.

Replay, duplicate message IDs, invalid sequence relationships, wrong room/sender/term, oversized
payload, malformed ciphertext, and expired messages fail closed.

## Canonical room state

The coordinator serializes accepted commands through one room actor. Queue and playback intent have
independent monotonic revisions. Transient transfer/readiness/endpoint/socket state is not canonical
history.

Peers report enough execution state for convergence repair: queue/playback revisions, queue item,
play/pause intent, local playback participation, local player state/position, and runtime readiness
signals.

Repair order is deterministic:

1. queue revision;
2. playback revision;
3. queue item identity;
4. play/pause intent;
5. position drift.

Older revisions, delayed callbacks, and stale preparation results cannot overwrite newer state.

## Prepare and playback

`QueueItemPreparationRequested` is ephemeral runtime intent, not a canonical mutation. Preparing a
song never secretly changes canonical playback.

Playback/select/Next/Previous target content only when the room readiness rules allow it. If a peer
cannot locally execute current canonical media, it waits for verified content rather than repeatedly
issuing impossible player mutations. Once media is locally available, it reconciles against the
latest canonical state.

Transport execution uses a bounded future coordinator timestamp. Play, pause, seek, previous, next,
and queue selection remain correlated with command identity through acceptance, scheduling,
application, settlement, supersession, or failure.

## Transfer coordination

Transfer priority, deadline, source assignment, retry/backoff, and readiness are runtime state. The
coordinator uses the shared transfer-capacity rules before granting work; transport-level gates remain
defensive checks, not a second scheduling policy.

An explicit local cancellation is semantically distinct from a network/route failure and must not
produce route penalty or retry feedback.

## File transfer

The coordinator grants a short-lived single-use authorization through the encrypted control channel.
The file connection uses a fresh nonce challenge whose proof binds room, track, request, source,
destination, resume offset, and both nonces.

After authorization is atomically consumed, both peers derive a transfer-session key. The response
header and every bounded 64 KiB record are independently AES-GCM authenticated. The destination
validates sequence, offset, size, and final SHA-256 before atomically committing the file.

The file request ID is also a diagnostic transfer operation identifier. The coordinator's existing
authorization ID provides a safe assignment-correlation identifier. Authorization tokens themselves
are never logged.

## Room end

`LeaveRoom` is an explicit session-ending signal when sent by the coordinator. Participants do not
elect a replacement coordinator in Protocol 2. Unexpected coordinator loss uses bounded reconnect;
exhausted recovery ends the room locally.

## Limits

Important protocol limits are constants in source and covered by tests: room membership, queue and
metadata size, frame length, audio size, inbound admissions, authentication concurrency, transfer
records, replay windows, and transfer capacity.
