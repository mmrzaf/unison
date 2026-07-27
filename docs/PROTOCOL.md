# Unison protocol v1

## Transport

- TCP only.
- One listening port handles control and file handshakes.
- Connections are accepted only from loopback, private, or link-local addresses.
- Control and file data use separate TCP connections so transfers cannot head-of-line block playback commands.

## Discovery record

DNS-SD service type: `_unison._tcp.`

TXT attributes:

| Key | Meaning |
|---|---|
| `rid` | random room ID |
| `v` | protocol version |
| `name` | display name, capped at 80 UTF-8 bytes by the app |
| `term` | coordinator term |

## Control handshake

1. Client sends `ClientHello` containing peer identity, room ID, supported protocol versions, listening port, nonce, and PIN proof.
2. Coordinator validates the room, protocol, port, and proof.
3. Coordinator encrypts the 256-bit room secret with a PBKDF2-derived PIN key using AES-GCM.
4. Both sides derive a per-connection session key from the room secret and both nonces using HKDF-SHA256.
5. Subsequent frames are HMAC-SHA256 authenticated.

The six-digit PIN is a friend-room admission mechanism, not protection against a determined offline brute-force attacker.

## Control frame

```text
4 bytes   magic "UNSN"
2 bytes   protocol version
1 byte    channel type
1 byte    flags
4 bytes   JSON length
16 bytes  message UUID
N bytes   UTF-8 JSON envelope
32 bytes  HMAC-SHA256(header + payload)
```

Control payloads are limited to 256 KiB. The decoder validates frame magic, version, channel, bounded length, HMAC, UUID consistency, envelope version, and room ID.

## Canonical envelope

```text
protocolVersion
roomId
term
coordinatorPeerId
senderPeerId
sequence?          canonical mutations only
messageId
sentAtElapsedNs
body
```

Canonical mutations include queue changes, room-option changes, scheduled playback actions, member updates, and preparation state. Only the current coordinator may issue them.

## Clock synchronization

Guest sends `ClockPing(pingId, guestSendNs)`. Coordinator replies with receive/send timestamps. Guest calculates network round trip and coordinator offset. Three samples make the clock usable; warm-up pings run every 250 ms, then every 5 seconds.

The guest sends `ClockReady` once synchronized. This prevents a scheduled play command from arriving before the phone can map coordinator time.

## Playback commands

- `PlayScheduled(queueItemId, positionMs, executeAtCoordinatorNs)`
- `PauseScheduled(positionMs, executeAtCoordinatorNs)`
- `SeekScheduled(queueItemId, positionMs, resumePlayback, executeAtCoordinatorNs)`
- `CurrentItemChanged(...)`
- `PlaybackStateSync(canonicalPlayback)` every two seconds

No message means “now.” The receiving phone converts coordinator time to local monotonic time and schedules the action.

## Track availability and transfer

1. Coordinator announces `TrackDescriptorMessage` for the preload window.
2. Peer answers `TrackHave` or `TrackNeed`.
3. Coordinator selects a source that is connected, holds the exact hash, and is not the destination.
4. Coordinator sends `TrackSourceAssigned` to the source.
5. Source installs the one-time token and answers `TrackSourceAuthorized`.
6. Coordinator sends the assignment to the destination.
7. Destination opens a dedicated file connection with the token and partial-file offset.
8. Source validates token, destination peer ID, track ID, expiry, and offset.
9. Destination resumes, fsyncs, hashes, and atomically finalizes the file.
10. Destination sends `TrackReady`.

A failed source/destination pair is retried once before that source is removed for the track.

## File response header

```text
4 bytes   magic "UNSF"
4 bytes   bounded JSON header length
N bytes   FileResponseHeader
raw bytes from acceptedOffset
```

Statuses: `OK`, `NOT_FOUND`, `UNAUTHORIZED`, `INVALID_OFFSET`, `BUSY`, `ERROR`.

## Compatibility

Protocol version is currently `1`. Unknown JSON fields are ignored. Unsupported protocol versions are rejected during handshake. Wire changes that alter meaning or framing require a new protocol version.
