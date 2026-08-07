# Security model

## Boundary

Unison protects room traffic between nearby devices on a private Wi-Fi network or Android
LocalOnlyHotspot. It provides authentication, confidentiality, integrity, replay resistance, bounded
resource use, and verified file identity. It does not protect a room from a device that has already
joined and is itself compromised.

## Admission

The room credential is exactly four digits and remains on the coordinator device. First admission
uses SRP-6a so the code and a reusable offline-testable password proof are not transmitted.
Authentication attempts are rate-limited, concurrency-limited, and timed out.

Reconnect uses a fresh coordinator challenge. Proof and session-key derivation bind room ID, peer ID,
client nonce, and server nonce to the active random room secret. Connection replacement happens only
after successful proof verification.

## Control traffic

- Protocol 1 uses strict decoding and exact version equality.
- Direction-specific AES-GCM keys protect every control frame.
- Headers and envelope context are authenticated.
- Message UUIDs and sequence checks reject replay and reordering outside allowed semantics.
- Room, term, sender, peer endpoint, size, timestamp, and metadata constraints fail closed.
- The coordinator serializes canonical mutations; peers cannot directly mutate another peer's player.

## File transfer

- Transfer requests require a short-lived, destination-bound, single-use authorization.
- A fresh nonce challenge proves possession of the authorization secret.
- The transfer header and every chunk are AES-GCM authenticated.
- Resume offset, expected size, record sequence, and final SHA-256 are verified.
- Files remain staged and invisible until complete verification and atomic commit.
- Active playback, queue, import, cleanup, and transfer work use leases or operation locks.

## Storage

Managed audio is stored in application-private content-addressed paths. Track identity is the
lowercase SHA-256 digest of exact bytes. Imports and downloads write to staging files, verify size and
digest, then atomically commit. Android backup is disabled.

The Room database is schema 1. It contains library and playlist data only. Active room secrets,
control keys, transfer keys, peer sockets, reconnect state, and canonical room sessions are memory
only and are cleared when the session ends.

## Network and platform controls

- No remote hostname or HTTP endpoint exists in production source.
- Public IP addresses and DNS joins are rejected.
- Cleartext Android network traffic is disabled.
- Media-session controllers must be trusted by Android before receiving transport capability.
- Exported components are limited to the launcher/share activity and MediaSessionService contract.
- Diagnostics use one bounded structured NDJSON sink. Secret/token/PIN/password/proof/key-material
  attributes, content URIs, and private storage paths are redacted before persistence. Raw room IDs,
  file contents, reusable credentials, cryptographic keys, and stack traces are not logged.
- Diagnostic files rotate at about 6 MiB total, are app-private, and have no automatic network
  exporter. Room-log copy is an explicit user action.

## Out of scope

- malicious code running on an admitted phone;
- a user intentionally sharing the four-digit code;
- operating-system compromise;
- traffic analysis such as observing that local devices are communicating;
- availability against a local attacker able to jam or disconnect the network.
