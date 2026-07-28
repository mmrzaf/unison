# Local room protocol

## Transport

Unison uses separate TCP connections for control and file transfer. Android NSD advertises rooms on
the local network; QR/direct invitations provide a fallback. Public addresses are rejected.

## Handshake

A client sends its supported protocol versions, peer identity, local listening port, nonce, room ID,
and PIN proof. The coordinator applies metadata, room-capacity, PIN-attempt, and private-address
validation before accepting. The room secret is encrypted with a key derived from the room ID, PIN,
and client nonce. Both peers derive a session key from the room secret and nonces.

## Control frames

Each control frame contains:

- fixed magic and protocol version;
- channel and payload length;
- UUID message identifier;
- bounded serialized envelope;
- HMAC-SHA-256 over header and payload.

The envelope includes room ID, coordinator term, sequence context, sender, monotonic timestamp, and
a typed body. Invalid sizes, room IDs, versions, JSON, HMACs, or message IDs fail closed.

## Canonical commands

Peers submit user commands. The coordinator validates membership, permissions, command identifier
length, queue capacity, track descriptors, and scheduling overlap. Accepted commands become ordered
canonical mutations. Playback changes carry a future coordinator monotonic timestamp rather than
“play now.”

## File transfer

The coordinator assigns a source and creates a short-lived, destination-bound, single-use
authorization token. The destination opens a file channel with the track ID and resume offset. The
source validates authorization before consuming it, then returns a bounded header and bytes. The
destination validates request ID, status, descriptor, offset, size, and final SHA-256 before
registering the file.

## Compatibility

Application version is `1.0.0`. The first-release wire protocol version is `1`; it is an independent
compatibility contract and is not an alternate application release.
