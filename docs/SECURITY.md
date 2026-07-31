# Security model

## Protected boundary

Unison is designed for nearby participants on a private Wi-Fi or Android LocalOnlyHotspot network.
Protocol 1 provides confidentiality and integrity for control and file-transfer traffic, rejects
public endpoints, bounds attacker-controlled inputs, and protects against replay and unauthenticated
peer claims. It does not protect a room from a compromised device that has already joined.

## Four-digit admission

The room code is exactly four digits and exists only on the active coordinator. It is never placed in
canonical room state, persisted snapshots, logs, URLs, or control messages.

Initial joining uses a mutually authenticated SRP-6a exchange. A passively captured handshake does
not provide an offline code verifier. The resulting temporary session key encrypts delivery of the
random room secret, and both sides verify possession before accepting the connection. Because a
four-digit code still permits active online guesses, admission also enforces:

- one-time client nonces with expiry;
- per-address failure limits and temporary backoff;
- a global failed-attempt budget;
- bounded concurrent authentication work;
- handshake timeouts and bounded encoded values.

Reconnect proves possession of the active random room secret and does not reuse the four-digit code.
A promoted coordinator generates a new code and room secret.

## Control and transfer protection

- AES-GCM protects room-secret delivery and all protocol 1 control frames.
- Separate client-to-coordinator and coordinator-to-client keys prevent directional key reuse.
- Protocol, room, sender, UUID, term, sequence, expiry, length, and metadata validation fail closed.
- File-transfer authorizations are short-lived, destination-bound, offset-bound, and consumed only after
  file and offset validation immediately before an accepted transfer begins.
- File sockets use nonce-bound proofs; response headers and every bounded chunk use AES-GCM.
- Managed and transferred audio is registered only after final size and SHA-256 verification.
- Transfer cancellation closes owned sockets before cancelling coroutine work.
- App-private storage is used; broad storage permission and Android backup are disabled.
- Diagnostics sanitize recognizable codes, tokens, passphrases, secrets, and authorization headers.

## Local application boundary

The installed runtime contains no hosted service SDK, account system, analytics, advertising,
remote endpoint, or store API. Cleartext HTTP is disabled; Android's `INTERNET` permission remains
necessary for private raw TCP sockets. Media-session trust checks restrict external commands.

## Secret lifetime

Room secrets, SRP session keys, reconnect keys, transfer authorizations, directional frame keys, and
transfer-session keys are scoped to their owner and zeroed where the JVM representation allows it. Encoded
handshakes, decrypted control frames, transfer headers/chunks, HKDF inputs, proofs, nonces, and PAKE padding
buffers are also cleared after their final use; immutable JVM strings remain outside deterministic wiping.
Room state is not restored after process death. Transfer authorizations expire and are consumed once.

## Remaining trust assumptions

A joined device can disrupt collaborative playback or redistribute audio it legitimately receives.
Bluetooth latency and device codec behavior are hardware-dependent and cannot be cryptographically
corrected by the room protocol.
