# Local room protocol

## Transport

Unison uses separate TCP connections for control and file transfer. Android NSD advertises rooms on
the local network; QR/direct invitations provide a fallback. Public addresses are rejected.

## Control handshake

A client sends its supported protocol versions, peer identity, local listening port, nonce, room ID,
and exactly one credential:

- an initial PIN proof; or
- a reconnect proof derived from the active room secret.

The coordinator validates protocol compatibility, room identity, metadata, room capacity, address,
credential shape, nonce replay, authentication concurrency, timeout, and per-address failure
backoff. The coordinator-local PIN is never included in canonical room state or snapshots.

On acceptance, the coordinator encrypts the active room secret with AES-GCM under the credential
unwrap key. Both peers then derive distinct client-to-coordinator and coordinator-to-client control
keys from the room secret and fresh nonces.

## Control frames

Each control frame contains:

- fixed magic and protocol version;
- channel and payload length;
- UUID message identifier;
- bounded serialized envelope;
- HMAC-SHA-256 over header and payload using the direction-specific key.

The envelope includes room ID, coordinator term, sequence context, sender, monotonic timestamp, and
a typed body. Invalid sizes, room IDs, versions, JSON, HMACs, senders, terms, sequences, expiries, or
message IDs fail closed.

## Canonical commands

Peers submit user commands. The coordinator validates membership, permissions, command identifier
length, queue capacity, track descriptors, and scheduling overlap. Accepted commands become ordered
canonical mutations. Playback changes carry a future coordinator monotonic timestamp rather than
"play now."

## File transfer

The coordinator assigns a source and creates a short-lived, destination-bound, single-use
authorization token. The destination opens a file channel with the track ID and resume offset. The
source validates and atomically consumes authorization before returning a bounded header and bytes.
The destination validates request ID, status, descriptor, offset, size, and final SHA-256 before
registering the file.

Transfer bytes are not encrypted. The file channel is intended for trusted participants on a private
local network.

## Compatibility

The wire protocol version is an independent compatibility contract. Credential or framing changes
must either remain backward-compatible or increment that protocol version deliberately.
