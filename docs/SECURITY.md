# Security model

## Protected boundary

Unison is designed for trusted participants on a private Wi-Fi or LocalOnlyHotspot network. It
protects against accidental cross-room traffic, malformed peers, casual unauthenticated command
injection, corrupted transfers, replay of consumed tokens/nonces, and unbounded common inputs. It
is not a replacement for a hostile-network VPN.

## Admission and session credentials

- The six-digit room PIN exists only on the active coordinator as invitation material.
- The PIN is not part of `RoomSnapshot`, canonical state, persisted snapshots, or network snapshot
  payloads.
- Initial admission uses a PBKDF2-HMAC-SHA-256 proof bound to the room ID and client nonce.
- Reconnect uses proof of the active room secret rather than resending or retaining the PIN.
- Authentication work is concurrency-bounded, timed out, failure-throttled by address, and protected
  by expiring one-time client nonces.
- A coordinator promoted after failure creates a new local invite PIN.

A six-digit PIN has limited entropy. The proof prevents sending the PIN directly, but the protocol
is still intended for private networks and trusted nearby users—not determined hostile observers.

## Control and transfer controls

- AES-GCM protects room-secret delivery during admission.
- Separate HMAC-SHA-256 keys authenticate client-to-coordinator and coordinator-to-client control
  frames.
- Protocol, room, sender, UUID, term, sequence, expiry, length, and metadata validation fail closed.
- Incoming socket and authentication concurrency are bounded and socket operations use timeouts.
- File authorization is short-lived, destination-bound, and single-use.
- Managed and transferred audio is accepted only after SHA-256 content verification.
- Transfer cancellation closes owned sockets before coroutine cancellation.
- App-private storage is used; broad storage permission and Android backup are disabled.
- Diagnostics redact recognizable PINs, tokens, passphrases, and secrets.

## Confidentiality limits

Control payloads and audio-transfer bytes are not end-to-end encrypted. A participant that has
joined the room is inside the room trust boundary and can access commands and audio legitimately
provided to it. Public or hostile Wi-Fi is not recommended.

## Local application boundary

The runtime contains no hosted service SDK, account system, analytics, advertising, remote endpoint,
or store API. Cleartext HTTP is disabled; local raw sockets remain necessary for LAN operation.
Media-session trust checks restrict external commands.

## Secret lifetime

Room secrets exist only for the active session and are not restored after process death. The
coordinator-local invite PIN and active reconnect material are cleared when the room runtime resets.
Transfer tokens expire and are consumed once.

## Remaining trust assumptions

A compromised joined device can misuse permissions available to a normal participant, share audio
it receives, or disrupt collaborative playback. Bluetooth latency remains hardware-dependent and
cannot be authenticated or corrected by the room protocol.
