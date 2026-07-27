# Security model

## Protected boundary

Unison is designed for a trusted group on a private Wi-Fi or LocalOnlyHotspot network. It protects against accidental cross-room traffic, malformed peers, casual command injection, corrupted transfers, replay of consumed transfer tokens, and unbounded common inputs. It is not a replacement for a hostile-network VPN.

## Controls

- private/link-local address allow-list for every socket path;
- no hosted service SDK, remote endpoint, account, telemetry, advertising, or store API;
- six-digit room PIN with PBKDF2-HMAC-SHA-256 proof and throttled failures;
- AES-GCM protection for room-secret delivery;
- HMAC-SHA-256 authenticated control frames;
- protocol, room, UUID, length, and metadata validation;
- bounded incoming socket concurrency and timeouts;
- single-use, destination-bound, expiring file authorization;
- SHA-256 content identity and post-transfer verification;
- app-private storage, no broad storage permission, backup disabled;
- cleartext HTTP disabled and no HTTP endpoint in runtime source;
- media-session trust checks and restricted external commands;
- redacted, size-rotated local diagnostics.

## Secrets

Signing keys and `keystore.properties` are local-only and ignored by source control. Room secrets exist only for the active session and are not restored after process death. PINs, tokens, passphrases, and secrets are redacted from diagnostic messages where recognizable.

## Remaining trust assumptions

A device that has joined the room can submit allowed collaborative commands and receive tracks assigned to it. A compromised participant device can access audio it legitimately receives. Bluetooth output latency remains hardware-dependent and cannot be authenticated or corrected by the room protocol.
