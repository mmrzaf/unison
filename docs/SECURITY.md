# Security model

## Goals

Unison protects a private friend session on a local network from accidental cross-room traffic and casual command injection. It is not designed for hostile public networks or anonymous internet peers.

## Implemented controls

- No cloud backend, account, analytics, advertising, or internet API.
- Public internet addresses are rejected before outbound connection or transfer.
- Random room ID, random 256-bit room secret, random nonces, and random one-time transfer tokens.
- Six-digit PIN proof derived with PBKDF2-HMAC-SHA256.
- Room secret encrypted with AES-GCM during admission.
- Per-control-connection keys derived with HKDF-SHA256.
- Every control frame authenticated with HMAC-SHA256.
- Constant-time MAC comparison.
- Canonical sender, room, term, and sequence validation.
- File source authorization bound to track, destination peer, token, and expiry.
- SHA-256 verification before a received track becomes usable.
- Bounded handshake, control, file-header, audio-file sizes, M3U folder depth, and indexed document count.
- Per-address PIN failure backoff and strict peer identity/nonce metadata validation.
- Upload and download socket timeouts prevent abandoned transfers from blocking a device indefinitely.
- No peer-provided filename is used as a filesystem path.
- App backup is disabled; room secrets are not persisted.
- Diagnostic logging redacts obvious PIN, secret, token, and passphrase assignments.
- The exported MediaSessionService accepts only trusted Android media controllers; its command set excludes queue mutation, repeat, shuffle, and playback-speed changes.

## Explicit non-goals

- Audio bytes are not encrypted on the LAN. A network observer may read transferred music.
- A six-digit PIN can be brute-forced offline by a determined attacker who captures an admission exchange.
- A friend already admitted to the room can send valid requests using their own authenticated connection.
- Coordinator recovery is not Byzantine fault tolerant.
- The application does not verify an APK update or another friend's installation signature.

## Recommended use

Use Unison on a trusted home network or its local-only hotspot. Do not use it for sensitive audio or on an untrusted public Wi-Fi network.

## Release signing

Run `./scripts/create-release-key.sh` once, back up the generated private key, and never commit it. `./scripts/build-release.sh` runs tests/lint, produces and verifies the signed APK, and writes its SHA-256 checksum. Keep `keystore.properties`, `*.jks`, and `*.keystore` outside source control. Friends must install updates signed with the same key.
