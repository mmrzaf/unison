# Protocol 1

Protocol 1 is Unison 1.0.0's only wire contract. There is no version list, negotiation, fallback
shape, or compatibility decoder. Unknown fields, missing required fields, invalid enum values, and a
protocol value other than `1` are rejected.

## Network boundary

Unison uses Android NSD to advertise rooms on the current private network. Discovered advertisements
include room identity, display name, coordinator term, port, and protocol value. Discovery grants no
access. Public addresses and DNS hostnames are rejected.

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

1. The client sends `pin_client_hello` with a fresh nonce and SRP public value.
2. The coordinator validates metadata, protocol, room, identity, limits, and nonce reuse.
3. The coordinator returns a fresh SRP challenge.
4. The client returns its proof.
5. The coordinator verifies the proof and returns its proof plus the room secret encrypted under the
   negotiated SRP session key.

The four-digit code itself never crosses the network and is not exposed as an offline-testable hash.

### Reconnect

1. The client sends `reconnect_client_hello` with a fresh client nonce.
2. The coordinator returns a fresh server nonce.
3. The client proves possession of the active room secret over the complete transcript.
4. Only after proof verification does the coordinator accept or replace a control connection.

A captured reconnect request or proof cannot authenticate a later connection.

## Control framing

Each control frame contains a fixed authenticated header, protocol value, flags, ciphertext length,
message UUID, and fresh AES-GCM nonce. Direction-specific session keys prevent reflection between
client-to-coordinator and coordinator-to-client traffic.

The encrypted envelope contains:

- protocol value;
- room ID;
- coordinator term;
- sender peer ID;
- sequence context;
- monotonic timestamp;
- typed body.

Replay, duplicate message IDs, invalid sequence relationships, wrong room, wrong sender, invalid
term, oversized payload, malformed ciphertext, and expired messages fail closed.

## Canonical room state

The coordinator serializes accepted commands through one room actor. Queue membership/order and
playback intent have independent monotonic revisions. Shuffle is a one-shot canonical reorder of
upcoming items; it is not a persistent playback mode. Repeat is the only persistent queue playback
mode.

Peers report:

- queue revision;
- playback revision;
- queue item ID;
- desired play/pause state;
- local playback participation (`ACTIVE` or `OUTPUT_INHIBITED`);
- local player state and position.

Audio-focus loss, becoming-noisy events, and unsuitable local output are device-local conditions,
not room transport commands. Inhibited peers continue receiving canonical state but are
excluded from play-state repair and the READY timing cohort. Explicit local rejoin positions the
device on the latest canonical item/current projected position before it can return to `ACTIVE`.

Repair order is deterministic:

1. queue revision;
2. playback revision;
3. queue item identity;
4. play/pause intent;
5. position drift.

Older revisions, delayed callbacks, and stale preparation results cannot overwrite newer state.

## Song transitions

Track selection is revision-bound. Each listener verifies or obtains the exact content-addressed
track before applying canonical playback. Pending work carries session, queue, playback, and command
identity; results that no longer match are discarded.

Transport execution uses a bounded future coordinator timestamp. Play, pause, seek, previous, next,
and queue selection remain correlated with their command ID through acceptance, scheduling,
application, settlement, supersession, or failure.

## File transfer

The coordinator grants a short-lived single-use authorization through the encrypted control channel.
The file connection uses a fresh nonce challenge whose proof binds:

- room;
- track;
- request;
- source and destination;
- resume offset;
- both nonces.

After authorization is atomically consumed, both peers derive a transfer-session key. The response
header and every bounded 64 KiB record are independently AES-GCM authenticated. The destination
validates sequence, offset, size, and final SHA-256 before committing the file.

## Limits

Important protocol limits are constants in source and are covered by tests. They include room
membership, queue length, metadata length, frame length, audio size, inbound admissions,
authentication concurrency, transfer records, and replay windows.
