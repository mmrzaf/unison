# Unison — Product and Technical Specification

**Application name:** Unison
**Android package:** `com.darius.unison`
**Document status:** Release-candidate implementation baseline
**Primary devices:** Android 11, 12, 12L, and 13
**Forward compatibility:** Android 14–16 compatibility target; Android 17 migration planned
**Distribution:** Signed APK through GitHub Releases
**Network model:** Offline local Wi-Fi only; no cloud, accounts, analytics, or internet services

---

## 1. Executive summary

Unison is a lightweight Android application for friends who want to listen to the same music at approximately the same time through their own headphones.

Unison does **not** stream one phone's live audio to the others. Every participant plays an identical local copy of the audio file. The network is used to:

1. discover and join a room;
2. distribute tracks that a participant does not already have;
3. maintain one shared queue and playback state;
4. schedule play, pause, seek, skip, and track transitions against a shared room clock;
5. detect and gently correct playback drift.

The product deliberately avoids becoming another full music platform. Users can maintain small personal playlists, import files and M3U playlists, receive music through the Android share sheet, and add any of those items to a room queue. File transfer, duplicate detection, caching, and preloading happen automatically.

All participants are equal in the user experience. Anyone may add music and use playback controls when the room options allow it. Internally, one device is temporarily selected as the **coordinator**. The coordinator is not an administrator. It only assigns a total order to commands, publishes canonical room state, and provides the reference clock. Audio files may transfer directly between any two peers.

### Locked product decisions

- Local playback on each phone; no live audio streaming.
- One persistent room session containing a continuous queue.
- Automatic transfer and preloading of upcoming tracks.
- Shared transport state; commands are serialized by an invisible coordinator.
- Primary input through the Android share sheet and system file picker.
- Personal playlists inside Unison.
- M3U and M3U8 import and export.
- No online audio URLs and no internet requirement.
- SHA-256 content hashes for duplicate detection and file identity.
- Temporary caching by default, with global and room-level retention options.
- No per-track retention prompt during playback.
- Rejoin synchronization after temporary disconnection.
- Clear user-facing states: Preparing, Receiving, Ready, Syncing, Reconnecting, and Unavailable.
- Android 11–13 are the main quality baseline, not the only supported releases.

---

## 2. Product definition

### 2.1 One-sentence definition

> Unison lets friends build a shared offline music queue and play identical local files together through their own headphones, with automatic transfer, preloading, and synchronization.

### 2.2 Product promise

A user should be able to:

1. open Unison;
2. create or join a nearby room;
3. add a playlist, one or more files, or music shared from another app;
4. press Play;
5. continue listening without repeating a file-transfer or setup process for every song.

The user should not need to understand IP addresses, ports, file hashes, codecs, storage paths, transfer sources, or clock offsets.

### 2.3 Primary use case

A small group of friends, usually two to eight people, is physically near each other. They are connected to the same local Wi-Fi network or to an offline local-only hotspot created by one phone. Each person listens through wired or Bluetooth headphones. They may sing along, so synchronization should be as tight as practical without turning the project into a laboratory-grade audio system.

### 2.4 Non-goals

Unison version 1 is not:

- a Spotify, YouTube Music, or Apple Music client;
- a cloud music locker;
- an internet radio service;
- a social network;
- a recommendation engine;
- a remote listening app across the internet;
- a multi-room speaker system;
- a sample-accurate professional audio synchronization system;
- a general-purpose file-sharing app;
- an account-based service;
- a DRM circumvention tool.

### 2.5 Experience principles

1. **One room setup, many songs.** The connection and permissions are handled once per session.
2. **Hide engineering.** Technical operations collapse into a few meaningful states.
3. **Prepare ahead.** The next tracks transfer while the current track plays.
4. **Together first.** A room is a shared experience, not several loosely linked players.
5. **No surprise storage.** Temporary files are the default; permanent retention is explicit but easy.
6. **Equal friends.** No visible administrator role is required.
7. **Simple failure recovery.** Rejoin from a fresh snapshot rather than replaying a fragile message history.
8. **Offline by design.** No production code contacts a public host.

---

## 3. User experience specification

## 3.1 Main navigation

Use three destinations only:

- **Home** — create room, join room, recent music, recent playlists.
- **Library** — tracks, playlists, received music.
- **Room** — current playback, members, queue, and Add Music.

Do not add artist, album, recommendation, social, or settings tabs unless later evidence justifies them.

## 3.2 First launch

First launch should ask only for a display name. Generate the persistent installation identity automatically.

Do not request network, nearby-device, notification, location, or file permissions on launch. Request them only when the user enters a flow that requires them.

Suggested screen:

```text
Welcome to Unison

Your name
[Darius                     ]

[Continue]
```

Persist:

- display name;
- random installation ID;
- default retention policy;
- last selected library view.

## 3.3 Home screen

```text
Unison

[Create room]   [Join room]

Recent playlists
• Road Trip
• Karaoke

Recent music
• Track A
• Track B
```

When an active room exists, replace the primary actions with:

```text
Current room
4 people • Playing
[Return to room]
```

## 3.4 Creating a room

### Same-Wi-Fi path

1. User taps **Create room**.
2. Unison verifies that Wi-Fi is enabled and connected. Internet capability is irrelevant.
3. The room service starts.
4. A TCP server opens on an ephemeral port.
5. The room is advertised over Android NSD/mDNS.
6. The room screen opens immediately.

Default room options:

```text
Room name: Darius's room
Queue: Everyone can add
Controls: Everyone can control
When someone is not ready: Wait at track boundary
Received music: Temporary for 24 hours
```

Do not force an options screen before room creation. Put options in a compact room settings sheet.

### No-network fallback

If the phone is not connected to a usable Wi-Fi LAN:

```text
No local Wi-Fi network

[Create offline network]
[Open Wi-Fi settings]
```

**Create offline network** starts Android's LocalOnlyHotspot API. The resulting Wi-Fi network has no internet access. Unison displays a standard Wi-Fi QR code containing the hotspot SSID and passphrase.

MVP guest flow:

1. Scan the Wi-Fi QR using the system camera or Wi-Fi settings.
2. Join the network once.
3. Return to Unison.
4. The room appears through NSD.

This context switch is acceptable for the fallback because it occurs once per session. An in-app QR scanner can be added later, but it should not block the first stable release.

## 3.5 Joining a room

1. User taps **Join room**.
2. Unison starts NSD discovery for `_unison._tcp` services.
3. Nearby rooms appear by friendly name.
4. User taps a room.
5. Unison connects to the room coordinator.
6. The coordinator sends a complete room snapshot and peer directory.
7. The guest enters the room.

Suggested states:

```text
Looking for rooms…
Connecting…
Joining room…
Synchronizing…
Connected
```

Do not expose IP addresses or service names in normal UI.

### Join authentication

The minimum-friction MVP uses a six-digit room PIN displayed in the room header. A discovered guest enters the PIN once. A QR-based join may carry a high-entropy room token and avoid PIN entry.

The PIN is not presented as an administrator approval mechanism. It is simply protection against accidental or unwanted joins from the same LAN.

## 3.6 Adding music

Use the same Add Music sheet everywhere:

```text
Add music

Recent
My playlists
Choose files
Import M3U playlist
Received music
```

When a room is active, the destination defaults to the room queue. Outside a room, the destination defaults to the personal library.

### Android share sheet

Unison accepts:

- `ACTION_SEND` with one audio URI;
- `ACTION_SEND_MULTIPLE` with multiple audio URIs;
- supported M3U/M3U8 documents.

When Unison receives music while a room is active:

```text
Add to
● Current room
○ My library
```

The current room is the default. The app must not force the user to navigate through the library first.

### System file picker

Use `ACTION_OPEN_DOCUMENT` with multiple selection for audio. Use the Storage Access Framework instead of broad media or storage permissions.

### Playlists

A user can add an entire personal playlist to the room queue in one action. The app resolves availability and begins preparing tracks in priority order.

## 3.7 Room screen

```text
Darius's room                     [⋮]
4 people • Together

Track title
Artist
──────────────
1:24 / 3:42

[Previous]   [Pause]   [Next]

Queue
1. Current track               Ready
2. Next track                  Ready
3. Another track               Preparing
4. Fourth track                Waiting

[+ Add music]
```

Member detail should remain secondary:

```text
Darius      Ready
Leyla       Ready
Sam         Receiving next track
Nihat       Reconnecting
```

## 3.8 Queue behavior

- Every queue entry has a stable UUID, independent of its array position.
- Everyone can add by default.
- Everyone can control playback by default.
- The coordinator serializes simultaneous actions.
- Queue edits appear optimistically only after the coordinator applies them, or use a clear pending state.
- The current track cannot be silently removed; removing it becomes a skip operation.
- Adding a playlist appends its tracks in playlist order.
- Shuffle and repeat are postponed until the base queue is stable.

## 3.9 Readiness behavior

A track is **Ready** for a participant only when:

1. the complete local file exists;
2. SHA-256 verification succeeds;
3. the player can open and prepare it;
4. the duration and codec are valid.

The room begins the first track only when all active participants are ready.

During playback, Unison preloads at least the next two tracks. If one participant is not ready when the current track reaches its end, the default behavior is:

1. finish the current track;
2. pause at the boundary;
3. show `Preparing for Sam…`;
4. continue automatically when ready.

After a configurable timeout, show:

```text
Sam is still preparing the next track.
[Keep waiting] [Continue without Sam]
```

This should be rare when preloading works correctly.

## 3.10 Ending a room and retention

Default global retention:

```text
Received music: Temporary — delete after 24 hours
```

Room override options:

- Temporary for 24 hours;
- Keep all received music;
- Ask once when the room ends.

A user may tap **Keep** on an individual received track at any time. This action does not ask a modal question.

If the room policy is **Ask once**, show one summary at room end:

```text
12 tracks were received
[Keep all] [Choose] [Leave temporary]
```

No per-track question appears during playback.

---

## 4. Quality and performance targets

These are engineering targets, not guarantees across every Android device and headset.

### 4.1 Supported group size

- Minimum: 2 devices.
- Normal target: 2–8 devices.
- Hard MVP limit: 8 active participants.

The limit may be raised only after device testing.

### 4.2 Playback synchronization

Measured at the application player position, excluding unknown headphone output latency:

- Initial start difference: target under 50 ms; acceptable under 100 ms.
- Steady-state drift: target under 40 ms; acceptable under 80 ms.
- Rejoin convergence: under 3 seconds after the track is locally available.
- Control action scheduling: normally 700–1,000 ms after the command is accepted.

Bluetooth headphones may add different device-specific output delays. Version 1 does not promise identical acoustic arrival time across arbitrary Bluetooth models.

### 4.3 Transfer behavior

- First track receives highest priority.
- Next two tracks should normally be verified before the current track ends.
- A disconnected transfer resumes from the existing partial-file length.
- One active outgoing transfer and one active incoming transfer per peer by default.
- File verification is mandatory before readiness.

### 4.4 Stability

The following must not stop playback on other devices:

- one guest temporarily disconnecting;
- one guest's app moving to the background;
- one file transfer failing;
- one guest lacking an upcoming track;
- duplicate or delayed control messages;
- an old coordinator message arriving after coordinator migration.

---

## 5. Android platform baseline

## 5.1 SDK configuration

```kotlin
android {
    namespace = "com.darius.unison"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.darius.unison"
        minSdk = 30
        targetSdk = 36
        versionCode = 10000
        versionName = "0.1.0"
    }
}
```

Rationale:

- `minSdk = 30` directly establishes Android 11 as the oldest supported release.
- Android 11, 12, 12L, and 13 are the primary device test matrix.
- `compileSdk = 36` and `targetSdk = 36` use the stable Android 16 platform behavior while retaining Android 11 compatibility.
- The publication build targets API 36 and must remain aligned with current store target-level requirements.
- Android 14–16 are compatibility targets and must receive physical-device smoke testing.
- Android 17 can run the target-36 build under compatibility behavior. Before moving `targetSdk` to 37, add and test the new `ACCESS_LOCAL_NETWORK` runtime permission path.

## 5.2 Technology stack

- Kotlin.
- Jetpack Compose.
- Kotlin coroutines and `StateFlow`.
- AndroidX Media3 ExoPlayer and `MediaSessionService`.
- A restricted MediaSession controller surface: trusted system/media controllers receive only synchronized transport and seek commands; playlist mutation, repeat, shuffle, and speed controls are not exposed.
- Room database.
- DataStore Preferences for small settings and installation identity.
- Kotlinx Serialization JSON for protocol version 1.
- Android Storage Access Framework.
- Android NSD (`NsdManager`) for room discovery.
- Java/Kotlin TCP sockets for control and file transfer.
- Android LocalOnlyHotspot as the no-router fallback.
- SHA-256 from the Java cryptography APIs.

Do not include:

- Google Play Services;
- Firebase;
- analytics or crash-reporting SDKs;
- cloud APIs;
- WebRTC;
- Bluetooth transport;
- Wi-Fi Direct in version 1;
- Hilt or a large dependency-injection framework initially.

## 5.3 Signing and public distribution

Build both a signed Android App Bundle for store publication and a signed APK for direct release testing. Keep one permanent signing identity; every future update must use the same application ID and compatible signing lineage.

Required release process:

1. create a release keystore and back it up in two secure locations;
2. never commit the keystore, passwords, `keystore.properties`, or local SDK paths;
3. increment `versionCode` for every uploaded update;
4. run unit tests, Android Lint, static checks, and signed release builds;
5. verify the APK signature and generate a SHA-256 checksum;
6. test the signed release on Android 11, 12/12L, 13, and at least one Android 14–16 device;
7. publish a privacy policy and complete store data-safety and foreground-service declarations from actual behavior;
8. use `com.darius.unison` for every production build.

Minification remains disabled until release-build tests cover serialization, Room, networking, Media3, and reflection-sensitive paths. Stability is more important than a smaller first release.

Public distribution does not change the offline product model: Unison still uses no cloud backend, analytics, ads, account system, or Google Play services at runtime.

## 5.4 Project structure

Start with one application module. Enforce boundaries through packages and interfaces rather than premature Gradle modularization.

```text
com.darius.unison/
  app/
    UnisonApplication
    AppContainer
  model/
  protocol/
  room/
    RoomEngine
    RoomReducer
    CoordinatorController
    ElectionController
  network/
    PeerServer
    PeerConnection
    NsdRoomDiscovery
    LocalHotspotController
    FrameCodec
  transfer/
    TransferManager
    FileSourceSelector
  sync/
    ClockSyncEngine
    PlaybackSyncEngine
  playback/
    UnisonRoomService
    UnisonPlayer
    ScheduledPlaybackController
  library/
    TrackRepository
    PlaylistRepository
    ImportManager
    M3uCodec
  storage/
    UnisonDatabase
    ManagedFileStore
    CacheCleanupWorker
  ui/
    home/
    library/
    room/
    components/
```

Use a manual composition root:

```kotlin
class AppContainer(context: Context) {
    val database = UnisonDatabase.create(context)
    val fileStore = ManagedFileStore(context)
    val trackRepository = TrackRepository(database, fileStore)
    val settings = UnisonSettings(context)
    val roomStore = RoomStore()
}
```

---

## 6. Runtime architecture

## 6.1 High-level components

```text
Compose UI
   │ commands / StateFlow
   ▼
RoomCommandBus ─────────────────────────────────┐
                                                │
UnisonRoomService : MediaSessionService         │
   ├── RoomRuntime                              │
   │    ├── RoomEngine                          │
   │    ├── CoordinatorController               │
   │    ├── PeerServer                          │
   │    ├── NsdRoomDiscovery                    │
   │    ├── TransferManager                     │
   │    ├── ClockSyncEngine                     │
   │    └── PlaybackSyncEngine                  │
   ├── ExoPlayer                                │
   └── MediaSession                             │
                                                │
Room database + managed files ◄─────────────────┘
```

The service owns the active room runtime so the connection, transfer, and playback lifecycle survive screen-off and Activity recreation.

The components remain separately testable. Putting them under one foreground service lifecycle is not permission to mix their logic.

## 6.2 Foreground service type

Declare the room service as:

```xml
android:foregroundServiceType="mediaPlayback|connectedDevice"
```

`mediaPlayback` covers background audio. `connectedDevice` covers continuous interaction with peer devices over a network and avoids treating the room as a generic cloud data-sync job.

The service starts only from direct user action such as Create room, Join room, or Play.

## 6.3 Single event processor

All state mutations pass through one serialized event loop. Network callbacks, UI commands, transfer completion, player events, and election messages do not mutate room state directly.

```kotlin
sealed interface RoomEvent {
    data class UserCommandReceived(
        val peerId: PeerId,
        val command: UserCommand
    ) : RoomEvent

    data class ProtocolReceived(
        val peerId: PeerId,
        val message: ProtocolMessage
    ) : RoomEvent

    data class PeerConnected(val peer: PeerDescriptor) : RoomEvent
    data class PeerDisconnected(val peerId: PeerId) : RoomEvent
    data class TransferUpdated(val update: TransferUpdate) : RoomEvent
    data class PlayerUpdated(val update: PlayerUpdate) : RoomEvent
    data class TimerFired(val timer: RoomTimer) : RoomEvent
}
```

```kotlin
class RoomEngine(
    private val reducer: RoomReducer,
    private val effects: RoomEffectHandler,
    scope: CoroutineScope,
) {
    private val events = Channel<RoomEvent>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(RoomState.empty())
    val state: StateFlow<RoomState> = _state.asStateFlow()

    init {
        scope.launch {
            for (event in events) {
                val result = reducer.reduce(_state.value, event)
                _state.value = result.state
                result.effects.forEach { effects.execute(it) }
            }
        }
    }

    suspend fun submit(event: RoomEvent) = events.send(event)
}
```

The reducer should be pure where practical. Side effects are explicit commands such as `Broadcast`, `SchedulePlayback`, `StartTransfer`, or `PersistSnapshot`.

---

## 7. Network architecture

## 7.1 Chosen transport

Use local Wi-Fi LAN with:

- NSD/mDNS for room discovery;
- persistent TCP control connections;
- separate on-demand TCP file connections;
- LocalOnlyHotspot when no router is available.

This is fully offline. Android's `INTERNET` manifest permission is required for sockets, including local sockets; it does not imply that Unison contacts the internet.

## 7.2 Topology

```text
                    Coordinator
                 control connections
              ┌─────────┼─────────┐
            Peer A     Peer B     Peer C
              ╲          │          ╱
                 direct file sockets
```

### Control plane

Every peer has one persistent control connection to the current coordinator.

The coordinator:

- assigns monotonically increasing room sequence numbers;
- applies queue and playback commands;
- broadcasts canonical events and periodic snapshots;
- provides the reference clock;
- maintains the peer directory;
- assigns file sources.

### File plane

Any peer with a verified track may serve that track directly to another peer. File traffic does not pass through the coordinator unless the coordinator is selected as the source.

### Equality model

The coordinator has no special user-facing permissions. Anyone may issue commands. The coordinator is a sequencer and clock authority, not an administrator.

## 7.3 One listening port per device

Every active participant opens one `ServerSocket` on an ephemeral port. The first bytes on each accepted socket identify its channel:

- `CONTROL` — persistent coordinator connection;
- `FILE` — one file request/response;
- `ELECTION` — short coordinator-election exchange if needed.

This avoids advertising multiple ports and keeps firewall/network behavior simple.

Recommended socket settings:

```kotlin
socket.tcpNoDelay = true
socket.keepAlive = true
socket.soTimeout = 0 // application heartbeat controls liveness
```

Operational defaults:

- connect timeout: 5 seconds;
- handshake timeout: 5 seconds;
- heartbeat interval: 5 seconds;
- peer considered stale: 12 seconds;
- peer considered disconnected: 15 seconds;
- maximum control frame: 256 KiB;
- maximum peers: 8.

## 7.4 Room discovery with NSD

Service type:

```text
_unison._tcp
```

Only the current coordinator advertises the room. Suggested service instance name:

```text
Unison-<8-character-room-id>
```

Use NSD TXT attributes where supported:

```text
rid   = short room ID
v     = protocol version
name  = UTF-8 room display name
term  = coordinator term
flags = compact capability flags
```

Do not place the room secret or PIN in NSD metadata.

### Multicast lock

For reliability on Android 11, Android 12, and Android 13 devices without newer SDK extensions, acquire `WifiManager.MulticastLock` while advertising or discovering. Release it immediately when discovery stops or room joining closes.

A simple implementation may acquire the lock on all supported versions during these short operations. This is easier to test and avoids extension-version branches.

## 7.5 Local-only hotspot

The coordinator may call `WifiManager.startLocalOnlyHotspot()` when no suitable Wi-Fi network exists.

Properties:

- no internet access;
- peers connected to the hotspot can communicate locally;
- Android may stop the hotspot, producing a callback;
- the user must approve required nearby/location permissions depending on OS version;
- the reservation object must remain alive until the room ends.

MVP joining uses a standard Wi-Fi QR code. After joining, normal NSD and TCP behavior applies.

Do not make LocalOnlyHotspot the only path. Same-router LAN is simpler and should remain the primary path.

## 7.6 Local address policy

Unison has no internet features. Enforce this in code:

- connect only to addresses returned by NSD or the authenticated peer directory;
- reject public IPv4 addresses;
- reject non-link-local/non-ULA IPv6 addresses in version 1;
- do not resolve arbitrary hostnames;
- do not accept online URLs as music sources;
- do not include HTTP clients in the production dependency graph.

IPv4 LAN addresses are the version-1 baseline. Add IPv6 LAN support after the IPv4 implementation is stable.

---

## 8. Identity, room terms, and command ordering

## 8.1 Persistent peer identity

Generate once and store in DataStore:

```kotlin
data class LocalIdentity(
    val peerId: String,       // random 128-bit value encoded as hex
    val displayName: String,
)
```

Never use:

- NSD service names;
- IP addresses;
- socket endpoint IDs;
- Android device names;

as persistent identity.

## 8.2 Room identity

```kotlin
data class RoomIdentity(
    val roomId: String,       // random UUID
    val roomSecret: ByteArray,// random 128 or 256 bits
)
```

A new room creates a new `roomId` and secret.

## 8.3 Coordinator term

```kotlin
data class CoordinatorTerm(
    val number: Long,
    val coordinatorPeerId: String,
)
```

Every canonical message contains:

- room ID;
- term number;
- coordinator peer ID;
- room sequence number.

Peers reject messages from:

- another room;
- a lower term;
- a conflicting coordinator in the same term that loses the deterministic tie-break;
- a room sequence already applied.

## 8.4 Command flow

```text
Peer sends UserCommandRequest(commandId)
        ↓
Coordinator validates request
        ↓
Coordinator increments room sequence
        ↓
Coordinator applies state transition
        ↓
Coordinator broadcasts AppliedRoomEvent(sequence)
        ↓
All peers apply the same event
```

Every request uses a UUID `commandId`. The coordinator remembers a bounded recent set so retries are idempotent.

## 8.5 Coordinator selection

Initial coordinator: room creator.

This is invisible in normal UI.

### Coordinator migration

When coordinator heartbeats disappear:

1. peers continue local playback of the current prepared track;
2. transport controls temporarily show `Reconnecting room…`;
3. connected peers wait a small deterministic delay based on sorted peer ID;
4. the lowest eligible peer ID announces a new term;
5. peers accept the highest term, then lowest coordinator ID as tie-break;
6. the new coordinator gathers the latest snapshots;
7. it selects the snapshot with the highest applied room sequence;
8. it broadcasts a new canonical snapshot;
9. clock synchronization restarts against the new coordinator;
10. scheduled control resumes.

This is deliberately not a full Raft implementation. The expected environment is one small LAN, not a hostile partitioned data center.

Partition behavior:

- two disconnected groups may temporarily elect separate coordinators;
- when groups reconnect, the higher term wins;
- equal terms use coordinator peer ID as tie-break;
- commands from the losing branch may be discarded;
- show a brief `Room resynchronized` message if user-visible queue changes were lost.

Do not attempt distributed conflict-free playback control in version 1.

---

## 9. Wire protocol

## 9.1 Protocol goals

- debuggable;
- versioned;
- bounded memory use;
- resistant to stale and duplicate messages;
- independent of Android classes;
- deterministic serialization where authentication requires it.

## 9.2 Frame format

Control sockets use length-prefixed frames.

```text
4 bytes   magic: "UNSN"
2 bytes   protocol version, unsigned big-endian
1 byte    channel type
1 byte    flags
4 bytes   payload length, unsigned big-endian
16 bytes  message UUID
N bytes   UTF-8 JSON payload
32 bytes  HMAC-SHA256 over header and payload
```

Reject before allocation when:

- magic is wrong;
- version is unsupported;
- payload length exceeds 256 KiB;
- HMAC fails;
- room ID is unexpected.

Version 1 may use JSON because room messages are small and human-readable logging materially speeds development. Migrate to protobuf only if profiling shows a real reason.

## 9.3 Envelope

```kotlin
@Serializable
data class Envelope(
    val protocolVersion: Int,
    val roomId: String,
    val term: Long,
    val coordinatorPeerId: String?,
    val senderPeerId: String,
    val sequence: Long?,
    val messageId: String,
    val sentAtElapsedNs: Long,
    val body: ProtocolBody,
)
```

Do not use wall-clock timestamps for synchronization. `sentAtElapsedNs` is meaningful only with the sender's current clock mapping.

## 9.4 Handshake

### Client hello

```kotlin
@Serializable
data class ClientHello(
    val peerId: String,
    val displayName: String,
    val appVersion: String,
    val protocolVersions: List<Int>,
    val listeningPort: Int,
    val roomId: String,
    val clientNonce: String,
    val pinProof: String?,
)
```

### Coordinator hello

```kotlin
@Serializable
data class CoordinatorHello(
    val acceptedVersion: Int,
    val term: Long,
    val coordinatorPeerId: String,
    val serverNonce: String,
    val roomTokenEnvelope: String,
    val snapshotSequence: Long,
)
```

MVP security is intended to prevent accidental joins and casual command injection, not sophisticated passive LAN attacks.

Recommended derivation:

```text
sessionKey = HKDF-SHA256(
    input = roomSecret,
    salt = clientNonce || serverNonce,
    info = "unison-protocol-v1"
)
```

When joining by PIN rather than QR, the PIN proves knowledge to the coordinator. The coordinator then sends the room secret over the accepted session. This does not provide strong protection against a determined local network attacker; stronger authenticated key exchange is a later security enhancement.

## 9.5 Core message families

### Membership

- `JOIN_ACCEPTED`
- `PEER_JOINED`
- `PEER_UPDATED`
- `PEER_LEFT`
- `PEER_DIRECTORY`
- `HEARTBEAT`
- `LEAVE_ROOM`

### State recovery

- `ROOM_SNAPSHOT_REQUEST`
- `ROOM_SNAPSHOT`
- `ACK_SEQUENCE`
- `REJOIN_REQUEST`

### User commands

- `PLAY_REQUEST`
- `PAUSE_REQUEST`
- `SEEK_REQUEST`
- `SKIP_NEXT_REQUEST`
- `SKIP_PREVIOUS_REQUEST`
- `QUEUE_ADD_REQUEST`
- `QUEUE_REMOVE_REQUEST`
- `QUEUE_MOVE_REQUEST`

### Canonical events

- `PLAY_SCHEDULED`
- `PAUSE_SCHEDULED`
- `SEEK_SCHEDULED`
- `CURRENT_ITEM_CHANGED`
- `QUEUE_ITEM_ADDED`
- `QUEUE_ITEM_REMOVED`
- `QUEUE_ITEM_MOVED`
- `ROOM_OPTIONS_CHANGED`

### Clock and playback sync

- `CLOCK_PING`
- `CLOCK_PONG`
- `PLAYBACK_STATE_SYNC`
- `PLAYBACK_STATUS_REPORT`

### Track availability and transfer

- `TRACK_DESCRIPTOR`
- `TRACK_HAVE`
- `TRACK_NEED`
- `TRACK_SOURCE_ASSIGNED`
- `TRACK_READY`
- `TRACK_FAILED`
- `TRANSFER_CANCELLED`

### Election

- `COORDINATOR_SUSPECTED`
- `COORDINATOR_CLAIM`
- `COORDINATOR_ACCEPTED`
- `STATE_CANDIDATE`
- `TERM_SNAPSHOT`

## 9.6 Snapshot

```kotlin
@Serializable
data class RoomSnapshot(
    val roomId: String,
    val term: Long,
    val coordinatorPeerId: String,
    val sequence: Long,
    val options: RoomOptions,
    val members: List<MemberSnapshot>,
    val queue: List<QueueItemSnapshot>,
    val playback: CanonicalPlaybackState,
)
```

Send a full snapshot:

- immediately after joining;
- immediately after rejoining;
- after coordinator migration;
- when a peer reports a sequence gap;
- periodically every 30 seconds as cheap defensive recovery.

---

## 10. Library, files, and playlists

## 10.1 Track identity

The track ID is the lowercase hexadecimal SHA-256 digest of the exact file bytes.

```text
trackId = SHA-256(file bytes)
```

Consequences:

- identical files deduplicate regardless of filename;
- different encodings or edits remain distinct;
- metadata changes inside the file change the hash;
- every received file can be verified before playback.

Hashing runs on `Dispatchers.IO` and streams the file; never load the full audio file into memory.

## 10.2 Track descriptor

```kotlin
@Serializable
data class TrackDescriptor(
    val trackId: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val durationMs: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val originalFileName: String?,
)
```

Display metadata is not trusted identity. After receiving a file, probe it locally and report unsupported or inconsistent media.

## 10.3 Managed file store

Use an app-private, content-addressed store:

```text
filesDir/
  tracks/
    ab/
      abcdef...         verified final file
      abcdef....part    interrupted partial file
```

The first two hash characters prevent excessively large directories.

Do not keep active room files only in `cacheDir`, because Android may reclaim cache files. Retention is an application database policy applied to managed files.

## 10.4 Source types

```kotlin
enum class TrackSourceType {
    PERSISTED_DOCUMENT_URI,
    APP_MANAGED_FILE,
}
```

```kotlin
enum class RetentionPolicy {
    EXTERNAL_REFERENCE,
    TEMPORARY_24_HOURS,
    KEEP_IN_LIBRARY,
}
```

A track may have more than one source. Prefer a verified managed file for room serving because its availability is deterministic.

## 10.5 Import from the system picker

Use `ACTION_OPEN_DOCUMENT` for user-controlled selection. Open the selected URI immediately, validate it as audio, extract metadata, hash the bytes while copying them into the content-addressed managed store, and insert or reuse the Track row.

The managed copy is deliberate. It prevents later playback and peer-transfer failures caused by revoked grants, moved source files, cloud-backed providers, or vendor document-provider behavior. Selecting identical bytes again reuses the existing SHA-256 file and does not create another Unison copy.

## 10.6 Import from the share sheet

Share-sheet URI permissions are normally temporary. Therefore:

1. open the URI immediately;
2. hash while copying into a temporary managed file;
3. atomically move to the content-addressed final path after verification;
4. add it to the room or library;
5. apply the selected retention policy later.

Do not keep only a shared `content://` URI and assume it remains readable after the sending application exits.

## 10.7 M3U and M3U8 support

Treat M3U as an import/export format, not the canonical database.

Supported import subset:

- plain `.m3u` and UTF-8 `.m3u8`;
- blank lines;
- comment lines beginning with `#`;
- `#EXTM3U`;
- `#EXTINF:<duration>,<display title>`;
- absolute document-like references when resolvable;
- relative paths resolved against a user-selected directory tree;
- file names matched against already indexed Unison tracks as a fallback.

Import algorithm:

1. detect BOM and decode UTF-8; optionally fall back to the system charset for `.m3u`;
2. normalize line endings;
3. parse metadata comments;
4. resolve each path;
5. create playlist entries for resolved tracks;
6. preserve unresolved entries in an import report;
7. ask once for a music directory if relative entries require it.

Unresolved UI:

```text
Imported 34 of 38 tracks
4 files could not be found
[Choose music folder] [View missing]
```

### Export

Use `ACTION_CREATE_DOCUMENT` and produce UTF-8 `.m3u8`.

Export rules:

- use an accessible source URI/path only when it is meaningful outside Unison;
- never write app-private paths;
- use the original filename as a relative entry for managed tracks;
- state clearly that the `.m3u8` file contains playlist references and metadata, not copies of the audio;
- a future **Export playlist with files** flow may copy both the playlist and audio into a user-selected folder.

## 10.8 Database schema

```text
TrackEntity
  trackId TEXT PRIMARY KEY
  sizeBytes INTEGER NOT NULL
  mimeType TEXT
  durationMs INTEGER NOT NULL
  title TEXT
  artist TEXT
  album TEXT
  originalFileName TEXT
  createdAt INTEGER NOT NULL
  lastPlayedAt INTEGER

TrackSourceEntity
  sourceId TEXT PRIMARY KEY
  trackId TEXT NOT NULL
  sourceType TEXT NOT NULL
  uri TEXT
  managedRelativePath TEXT
  retentionPolicy TEXT NOT NULL
  verified INTEGER NOT NULL
  lastVerifiedAt INTEGER
  expiresAt INTEGER

PlaylistEntity
  playlistId TEXT PRIMARY KEY
  name TEXT NOT NULL
  createdAt INTEGER NOT NULL
  updatedAt INTEGER NOT NULL

PlaylistEntryEntity
  playlistId TEXT NOT NULL
  entryId TEXT PRIMARY KEY
  trackId TEXT NOT NULL
  position INTEGER NOT NULL

RoomSnapshotEntity
  roomId TEXT PRIMARY KEY
  serializedSnapshot TEXT NOT NULL
  updatedAt INTEGER NOT NULL
```

Use foreign keys and indexes on `trackId`, `playlistId`, `position`, and `expiresAt`.

---

## 11. Room queue and track readiness

## 11.1 Queue model

```kotlin
@Serializable
data class QueueItem(
    val queueItemId: String,
    val track: TrackDescriptor,
    val addedByPeerId: String,
    val addedAtSequence: Long,
)
```

Do not identify queue entries only by track hash because the same track may intentionally appear more than once.

## 11.2 Member readiness

```kotlin
enum class MemberTrackState {
    UNKNOWN,
    CHECKING,
    NEEDS_FILE,
    RECEIVING,
    VERIFYING,
    PREPARING_PLAYER,
    READY,
    FAILED,
}
```

```kotlin
data class TrackReadiness(
    val queueItemId: String,
    val statesByPeer: Map<String, MemberTrackState>,
)
```

## 11.3 Preload window

Default target:

- current track;
- next two queue entries;
- a third upcoming track when storage and transfer bandwidth are healthy.

Priority score:

```text
current reconnect requirement  = 1000
next queue item                = 900
second next                    = 800
third next                     = 700
remaining queue                = 100 - queue distance
```

A newly added track should not interrupt transfer of the immediate next track unless it becomes the next track due to queue reordering.

---

## 12. Peer-to-peer file transfer

## 12.1 Availability exchange

When a queue item is applied:

1. every peer checks for a verified local source by hash;
2. peers send `TRACK_HAVE` or `TRACK_NEED`;
3. coordinator builds the source set;
4. coordinator assigns a source for each needing peer;
5. destination connects directly to source.

Source selection order:

1. verified source;
2. source not already serving a file;
3. direct endpoint recently reachable;
4. lowest recent transfer load;
5. coordinator as fallback;
6. deterministic peer-ID tie-break.

Do not implement multi-source chunking in version 1.

## 12.2 File request

Destination opens a `FILE` channel and sends:

```kotlin
@Serializable
data class FileRequest(
    val requestId: String,
    val roomId: String,
    val trackId: String,
    val offset: Long,
)
```

Source validates:

- authenticated room session;
- valid hash format;
- verified source exists;
- offset is between zero and file size;
- requested file is in the room queue or recent transfer authorization set.

## 12.3 File response

```kotlin
@Serializable
data class FileResponseHeader(
    val requestId: String,
    val status: FileResponseStatus,
    val trackId: String,
    val totalSize: Long,
    val acceptedOffset: Long,
)
```

After the header, stream exactly `totalSize - acceptedOffset` raw bytes.

Use a 128 KiB copy buffer. TCP already provides ordered reliable bytes; do not add per-chunk acknowledgements or CRC in version 1. Resume and final SHA-256 verification provide the needed recovery and integrity.

## 12.4 Partial files

- Store partial data as `<hash>.part`.
- Persist expected total size and current length.
- On retry, request `offset = currentLength`.
- If the source rejects the offset or descriptor changed, delete and restart.
- After complete transfer, calculate SHA-256.
- On success, atomically rename to the final content-addressed path.
- On failure, delete the corrupt final candidate and retain or restart the partial according to error type.

## 12.5 Transfer cancellation

Cancel a transfer when:

- queue item is removed and not otherwise needed;
- room ends;
- destination already receives a verified copy from another completed path;
- user clears temporary files;
- source leaves and no further bytes arrive.

Do not cancel preparation of the immediate next track merely because the app UI moves to the background.

---

## 13. Playback architecture

## 13.1 Player service

```kotlin
class UnisonRoomService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var roomRuntime: RoomRuntime
}
```

Use `MediaSessionService` so playback continues with the screen off and integrates with Android media controls.

The room UI must not directly own ExoPlayer.

## 13.2 Player configuration

Recommended baseline:

```kotlin
player = ExoPlayer.Builder(this)
    .setSeekParameters(SeekParameters.EXACT)
    .build()
    .apply {
        setHandleAudioBecomingNoisy(true)
        setWakeMode(C.WAKE_MODE_NETWORK)
        repeatMode = Player.REPEAT_MODE_OFF
        shuffleModeEnabled = false
    }
```

Notes:

- Exact seeking may cost more work but is preferred for synchronization corrections.
- The player should be prepared before scheduled start.
- Use local file URIs only.
- Disable audio offload initially if device testing shows offload causes inconsistent start or speed-correction behavior. Do not disable it without evidence.
- Keep all player calls on the player's application looper.

## 13.3 Media playlist mirroring

Mirror the current track and ready upcoming tracks into ExoPlayer's playlist.

Benefits:

- upcoming media is prepared;
- local automatic transition reduces gaps;
- every device uses the same exact file sequence;
- no last-millisecond network command is required for every normal track boundary.

Queue edits must preserve the currently playing MediaItem identity by `queueItemId`.

## 13.4 Audio focus and noisy output

- Request normal music audio focus through Media3.
- Pause locally on audio-focus loss and report the state to the coordinator.
- On headphone disconnect (`AUDIO_BECOMING_NOISY`), pause the affected peer and show a clear message.
- Do not automatically pause the entire room because one person's headphones disconnect. That participant may rejoin playback after resolving the route.

---

## 14. Clock synchronization

## 14.1 Clock source

Use:

```kotlin
SystemClock.elapsedRealtimeNanos()
```

Do not use:

- `System.currentTimeMillis()`;
- network time;
- Unix timestamps;
- calendar time.

The room needs a monotonic clock unaffected by user or network time adjustments.

## 14.2 Offset convention

Define offset as:

```text
coordinatorTime ≈ localTime + offset
```

Therefore:

```text
localExecutionTime = coordinatorExecutionTime - offset
```

Use one convention everywhere and test it explicitly.

## 14.3 Ping/pong sample

Guest records `t0` and sends a clock ping.

Coordinator records receipt at `t1`, records send at `t2`, and replies.

Guest records receipt at `t3`.

```text
roundTrip = (t3 - t0) - (t2 - t1)

offset = ((t1 - t0) + (t2 - t3)) / 2
```

All values are nanoseconds from each device's monotonic clock.

## 14.4 Sample filtering

Initial synchronization:

- send 12 samples over roughly 1.5 seconds;
- discard invalid or negative corrected RTT values;
- sort by RTT;
- retain the best 5 samples;
- use the median offset;
- compute clock quality from median RTT and offset spread.

Ongoing synchronization:

- one sample every 5 seconds;
- retain a rolling window of 20;
- prefer low-RTT samples;
- limit offset adjustment rate to avoid sudden scheduling jumps;
- reset the window after coordinator migration or network change.

Example quality states:

```text
GOOD       median RTT < 30 ms and offset spread < 10 ms
FAIR       median RTT < 80 ms and spread < 30 ms
POOR       otherwise
```

These thresholds are starting values and must be tuned from device logs.

---

## 15. Scheduled playback commands

## 15.1 Never send “play now”

A canonical command contains a future coordinator timestamp:

```kotlin
@Serializable
data class ScheduledPlaybackCommand(
    val sequence: Long,
    val queueItemId: String,
    val positionMs: Long,
    val executeAtCoordinatorNs: Long,
)
```

The local target is:

```text
executeAtLocalNs = executeAtCoordinatorNs - estimatedOffsetNs
```

## 15.2 Lead time

Initial defaults:

- normal play/pause/seek: 800 ms;
- skip to another prepared track: 1,000 ms;
- rejoin: 1,500 ms;
- poor clock quality: up to 2,000 ms.

Later make lead time adaptive:

```text
lead = clamp(
    basePrepareMargin + p95OneWayDelay + schedulingJitterMargin,
    600 ms,
    2000 ms
)
```

Users should see immediate visual feedback such as a button state change, even though acoustic execution is scheduled slightly ahead.

## 15.3 Scheduler implementation

- Prepare and seek before the deadline.
- Schedule on a dedicated monotonic scheduler.
- At deadline, post the final player call onto the player's looper.
- Record actual execution lateness for diagnostics.
- If a runnable fires early, reschedule the remaining duration.
- If late by under 100 ms, execute immediately and let drift correction converge.
- If late by more than 100 ms, execute and mark the peer `Syncing`.

Do not busy-spin for millisecond precision in version 1. It harms battery and does not solve downstream audio-output latency.

---

## 16. Playback state and drift correction

## 16.1 Canonical playback state

```kotlin
@Serializable
data class CanonicalPlaybackState(
    val queueItemId: String?,
    val positionAtTimestampMs: Long,
    val coordinatorTimestampNs: Long,
    val isPlaying: Boolean,
    val nominalSpeed: Float = 1.0f,
)
```

Expected position at coordinator time `now`:

```text
expectedPositionMs = positionAtTimestampMs
                   + elapsedSinceTimestampMs * nominalSpeed
```

For a paused state, expected position remains constant.

## 16.2 State sync frequency

Coordinator broadcasts `PLAYBACK_STATE_SYNC` every 1 second while playing and every 3 seconds while paused.

Each peer reports:

- local player position;
- player state;
- current queue item;
- buffered readiness;
- measured drift;
- output route category: wired, Bluetooth, speaker, other.

## 16.3 Correction thresholds

Starting policy:

```text
absolute drift < 35 ms
    no correction

35–100 ms
    temporary speed correction of approximately ±0.5% to ±1.0%

100–180 ms
    stronger temporary correction up to approximately ±1.5%,
    unless this remains audible or unstable in testing

> 180 ms
    exact seek to expected position
```

Correction rules:

- apply a deadband to prevent oscillation;
- require two consecutive measurements before correcting moderate drift;
- restore speed gradually to 1.0;
- cap one speed-correction episode at 5 seconds;
- do not seek repeatedly within a 3-second cooldown;
- after a seek, ignore measurements briefly while the player settles.

Configuration belongs in one `SyncTuning` data class so field testing can adjust it without rewriting logic.

## 16.4 Track transitions

For ordinary transitions:

- identical verified files are already loaded into each local ExoPlayer playlist;
- local automatic transition is allowed;
- the coordinator publishes the new current item immediately after its transition callback;
- peers that transition differently correct to the canonical item.

For manual Next/Previous:

- schedule a future item change and position zero;
- all peers pre-seek/prepare before the execution time.

## 16.5 Acoustic latency limitation

The app synchronizes software playback timelines. The sound may still reach listeners at different times because:

- Bluetooth codecs buffer differently;
- different earbuds have different output pipelines;
- device audio effects add latency;
- the Android audio clock and system monotonic clock are not guaranteed to run at exactly the same rate.

Version 1 response:

- do the best stable software synchronization;
- record output route in diagnostics;
- recommend wired or low-latency headphones for singing;
- do not add manual calibration until real testing shows a repeatable need.

Possible later feature:

```text
Headphone sync adjustment: -200 ms … +200 ms
```

Store adjustment by device and output route, not globally.

---

## 17. Rejoin and recovery

## 17.1 Rejoin request

```kotlin
@Serializable
data class RejoinRequest(
    val peerId: String,
    val roomId: String,
    val lastKnownTerm: Long,
    val lastAppliedSequence: Long,
    val currentQueueItemId: String?,
    val verifiedNeededTrackIds: List<String>,
    val listeningPort: Int,
)
```

## 17.2 Rejoin procedure

1. reconnect control socket;
2. authenticate session;
3. receive fresh room snapshot;
4. replace local canonical room state;
5. restart clock sampling;
6. report verified current/upcoming tracks;
7. request current track if missing;
8. prepare current item at expected position;
9. coordinator schedules a rejoin approximately 1.5 seconds ahead;
10. transition from `Reconnecting` to `Syncing`, then `Ready`.

Other participants do not pause for a reconnecting peer.

## 17.3 Process death

Persist a small room snapshot and temporary file state. On process restart:

- show `Previous room interrupted`;
- attempt rejoin only after direct user action unless playback service is legitimately restored;
- never silently recreate a hotspot or foreground service from boot;
- clear stale connection objects and recreate sockets.

---

## 18. Android permissions and manifest

## 18.1 Permissions

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Local TCP sockets and NSD. Does not mean Unison uses the internet. -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />

    <!-- Wi-Fi/CPU wake behavior while a visible room is active. -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- Android 13+ LocalOnlyHotspot and nearby Wi-Fi APIs. -->
    <uses-permission
        android:name="android.permission.NEARBY_WIFI_DEVICES"
        android:usesPermissionFlags="neverForLocation" />

    <!-- Android 11–12 compatibility for LocalOnlyHotspot. -->
    <uses-permission
        android:name="android.permission.ACCESS_FINE_LOCATION"
        android:maxSdkVersion="32" />

    <!-- Foreground room/playback service. -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

    <!-- Android 13+ notification visibility. -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <uses-feature
        android:name="android.hardware.wifi"
        android:required="true" />

</manifest>
```

Do not request:

- `READ_EXTERNAL_STORAGE`;
- `WRITE_EXTERNAL_STORAGE`;
- `MANAGE_EXTERNAL_STORAGE`;
- `READ_MEDIA_AUDIO` for normal imports;
- Bluetooth permissions;
- microphone permission.

The system file picker grants access to user-selected documents without broad storage permissions.

## 18.2 Service declaration

```xml
<service
    android:name=".playback.UnisonRoomService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback|connectedDevice">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

## 18.3 Share intent filters

The launcher Activity may accept audio sharing:

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="audio/*" />
</intent-filter>

<intent-filter>
    <action android:name="android.intent.action.SEND_MULTIPLE" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="audio/*" />
</intent-filter>
```

M3U MIME handling is inconsistent across file providers. Support known playlist MIME types and also expose Import M3U through the system picker.

## 18.4 Android 17 migration

Android 17 introduces enforced local-network protection for applications targeting API 37 or higher.

Before changing `targetSdk` to 37:

1. declare `android.permission.ACCESS_LOCAL_NETWORK`;
2. request it at room create/join time, not first launch;
3. test NSD, raw sockets, direct peer transfers, and LocalOnlyHotspot paths;
4. evaluate the Android 17 NSD system picker path for narrower service access;
5. retain target-36 behavior until the migration is complete.

This future permission should be isolated behind `LocalNetworkPermissionController`.

---

## 19. Background execution and power

## 19.1 Foreground lifecycle

The room service remains foreground while:

- playback is active;
- a room is active and peer connectivity must remain alive;
- critical preloading is in progress.

The notification should show:

- room name;
- current track;
- participant count;
- play/pause and next;
- Leave room action.

## 19.2 Wi-Fi lock

For active synchronized playback, acquire a non-reference-counted `WifiLock` using `WIFI_MODE_FULL_LOW_LATENCY` on supported devices. Release it when:

- the room ends;
- the service stops;
- playback is paused for a long idle interval and no transfer is active.

Use a fixed tag:

```text
com.darius.unison:room
```

Do not hold a Wi-Fi lock merely while browsing the library.

## 19.3 Multicast lock

Acquire only for NSD advertising/discovery. Do not keep multicast reception enabled for the entire playback session after membership is closed.

## 19.4 OEM battery behavior

Some manufacturers aggressively kill background applications despite foreground services. Do not request battery-optimization exemption on first run.

Add a troubleshooting item only after a detected repeated failure:

```text
Unison is being stopped in the background on this phone.
[Open battery settings]
```

Screen-off and battery-saver tests are mandatory on physical Android 11–13 devices.

---

## 20. User-facing state model

Map many internal conditions to a small vocabulary.

| User state | Meaning |
|---|---|
| Preparing | Hashing, reading metadata, or preparing the player |
| Receiving | Audio bytes are transferring |
| Verifying | Transfer completed; SHA-256 is being checked |
| Ready | Verified file and prepared player are available |
| Syncing | Playback exists but timing is converging |
| Reconnecting | Control connection is being restored |
| Waiting | Room is waiting at a track boundary |
| Unavailable | File cannot be accessed, decoded, or transferred |

Detailed diagnostics belong behind a developer/debug screen, not in the primary room UI.

---

## 21. Failure handling

## 21.1 Discovery failure

User message:

```text
No rooms found on this Wi-Fi network.
[Try again] [Create room]
```

Developer diagnostics:

- Wi-Fi network identity;
- NSD start result;
- multicast-lock state;
- number of discovered services;
- resolve failures.

## 21.2 Connection failure

- retry with exponential delays: 0.5 s, 1 s, 2 s, 4 s, then every 5 s;
- stop retrying when room explicitly ends or user leaves;
- after 15 seconds show Reconnecting;
- preserve local playback until canonical state becomes unsafe.

## 21.3 File unavailable

Possible causes:

- persisted URI permission lost;
- source file moved or deleted;
- all peers with the file left;
- unsupported file format;
- insufficient storage;
- hash mismatch.

User message should identify the track and useful action:

```text
“Track name” is unavailable.
[Choose replacement] [Remove from queue]
```

## 21.4 Storage full

- check free space before transfer;
- reserve expected file size plus a safety margin;
- offer cleanup of expired temporary tracks;
- never delete kept library tracks automatically.

## 21.5 Coordinator loss

- continue prepared local playback briefly;
- disable new controls during election;
- elect and resynchronize;
- if no peer can be reached, pause at the next safe point and show `Room connection lost`.

## 21.6 Codec failure

Track readiness becomes Failed for that peer. Since every participant must hear the track, default action is to mark the queue item unavailable for the room and ask to remove or replace it. Automatic transcoding is outside version 1.

---

## 22. Security and privacy

## 22.1 Privacy baseline

- no accounts;
- no analytics;
- no cloud logs;
- no remote crash reporting;
- no internet endpoints;
- no contact access;
- no microphone access;
- local metadata and music remain on participant devices;
- room data is ephemeral unless stored for recovery.

## 22.2 Network security scope

Version 1 protects against:

- accidental room collisions;
- unauthenticated command injection after the room key is established;
- file corruption;
- stale and replayed room commands.

Version 1 does not fully protect against:

- a sophisticated attacker passively sniffing the same LAN during a weak PIN join;
- a compromised participant intentionally redistributing received files;
- maliciously crafted media exploiting platform decoders.

Potential later hardening:

- ephemeral X25519 key exchange;
- PIN-authenticated PAKE;
- ChaCha20-Poly1305 encrypted frames and file streams;
- per-peer permission revocation;
- certificate fingerprint in QR joins.

Do not add this cryptographic complexity before the base connection and synchronization behavior is stable, but preserve protocol versioning for it.

---

## 23. Logging and diagnostics

Provide an optional local developer mode. Logs remain on device and can be exported manually.

Record:

- room term and sequence;
- peer connect/disconnect reasons;
- heartbeat RTT;
- clock offset samples;
- scheduled command target and actual execution lateness;
- measured playback drift;
- correction action;
- file transfer source, bytes, duration, and resume count;
- hash verification result;
- player errors and output route.

Never log:

- room secret;
- PIN;
- HMAC/session key;
- full private filesystem paths unless explicitly exporting diagnostics;
- audio bytes.

Use a bounded rolling log, for example 5–10 MB.

---

## 24. Testing strategy

## 24.1 Unit tests

### Room reducer

- command ordering;
- duplicate command IDs;
- stale sequences;
- queue add/remove/move;
- current-item removal;
- room option changes;
- peer readiness transitions.

### Protocol

- frame boundaries;
- partial socket reads;
- oversized payload rejection;
- invalid magic/version;
- HMAC failure;
- unknown message type;
- deterministic serialization.

### Clock sync

- known offset and RTT simulations;
- asymmetric delay;
- high-jitter outliers;
- offset smoothing;
- coordinator migration reset.

### Transfer

- resume from valid offset;
- invalid offset;
- source disappears;
- hash mismatch;
- destination storage full;
- duplicate completion race.

### M3U

- UTF-8;
- BOM;
- CRLF/LF;
- EXTINF;
- comments;
- relative paths;
- unresolved entries;
- duplicate tracks.

## 24.2 Integration tests with fakes

Create:

- `FakeMonotonicClock`;
- `FakeTransport`;
- `FakePlayer`;
- `FakeFileStore`;
- `DeterministicRoomHarness`.

Simulate eight peers in one JVM process and inject:

- delayed messages;
- duplicate messages;
- reordered connection events;
- dropped heartbeats;
- transfer interruption;
- coordinator loss;
- sequence gaps.

## 24.3 Physical device matrix

Primary mandatory matrix:

- Android 11 / API 30;
- Android 12 / API 31;
- Android 12L / API 32 when available;
- Android 13 / API 33.

Forward-compatibility smoke matrix:

- Android 14 / API 34;
- Android 15 / API 35;
- Android 16 / API 36.

Use multiple manufacturers where possible because Wi-Fi, background behavior, and audio latency vary by device firmware.

## 24.4 Core physical scenarios

1. Two devices, same Wi-Fi, same preinstalled file.
2. Five devices, one source, all receive a file.
3. Five devices, different peers already own different upcoming tracks.
4. Screen off on every guest.
5. Bluetooth headphones on all guests.
6. Mixed wired and Bluetooth routes.
7. One guest leaves and rejoins.
8. Coordinator loses Wi-Fi and migration occurs.
9. Next track transfer is interrupted and resumed.
10. One URI source is deleted after playlist creation.
11. Room runs through 20 short tracks without user setup between tracks.
12. No-router LocalOnlyHotspot room.
13. Battery saver enabled.
14. Rapid Play/Pause/Seek/Next commands from different peers.
15. App Activity is destroyed while service continues.

## 24.5 Acceptance criteria for first usable build

- Four physical Android 11–13 phones complete a 30-minute room.
- At least ten tracks transition without manual transfer steps.
- All controls converge to one canonical state.
- No duplicate file is transferred when the verified hash already exists.
- A guest can reconnect and rejoin current playback.
- Screen-off does not stop playback or the room on tested devices.
- Initial application-level start difference is under 100 ms on the tested set.
- Steady-state application-level drift remains under 80 ms for at least 20 minutes.
- No crash or room corruption occurs during coordinator migration.

---

## 25. Implementation roadmap

## Milestone 0 — repository and skeleton

- package `com.darius.unison`;
- Compose app shell;
- Room database;
- AppContainer;
- signed debug/release setup;
- local diagnostics framework.

## Milestone 1 — synchronization spike

- two phones;
- manually place the same file on both;
- TCP connection by hardcoded IP temporarily;
- clock ping/pong;
- scheduled play, pause, and seek;
- drift measurement logs;
- no library, NSD, or transfer UI.

**Exit:** synchronization behavior is measurable and repeatable.

## Milestone 2 — room control

- PeerServer and control frames;
- NSD create/join;
- room snapshot;
- shared queue model;
- anyone-can-control requests;
- sequence and term handling;
- heartbeat and rejoin.

**Exit:** four phones maintain one deterministic room state.

## Milestone 3 — file transfer

- hash-based inventory;
- peer source assignment;
- direct file socket;
- partial resume;
- SHA-256 verification;
- current/next-track priorities.

**Exit:** one playlist can play continuously after initial preparation.

## Milestone 4 — library and import UX

- file picker;
- share-sheet receiver;
- personal playlists;
- M3U/M3U8 import;
- M3U8 export;
- managed file retention.

**Exit:** adding music is easier than manually managing files.

## Milestone 5 — background stability

- MediaSessionService lifecycle;
- foreground notification;
- Wi-Fi and multicast locks;
- screen-off testing;
- Bluetooth route testing;
- battery and process-death behavior.

**Exit:** a normal room survives real phone usage.

## Milestone 6 — coordinator migration

- peer directory;
- election term;
- state candidate collection;
- NSD re-advertisement;
- partition merge rule.

**Exit:** room continues when the original creator leaves.

## Milestone 7 — LocalOnlyHotspot

- permission controller;
- hotspot reservation lifecycle;
- Wi-Fi QR display;
- no-router physical testing.

**Exit:** friends can create a fully offline room without a router.

---

## 26. Review findings

## Critical — must be correct before the product works

1. **Never stream live audio.** Every participant must play the same verified local file.
2. **Use a coordinator for total ordering.** Peer equality does not remove the need for one canonical command order and clock.
3. **Separate control and file sockets.** File transfer must never block Play/Pause/Seek messages.
4. **Verify every received file by SHA-256.** A transfer is not Ready merely because the socket reached EOF.
5. **Use a monotonic clock.** Wall-clock time invalidates synchronization.
6. **Schedule commands in the future.** `PLAY_NOW` guarantees device-dependent arrival differences.
7. **Centralize state mutation in RoomEngine.** Callback-driven direct mutation will create race conditions.
8. **Copy share-sheet URIs immediately.** Their access is temporary.
9. **Run the room under a visible foreground service.** Background execution cannot be left to Activity lifecycle.
10. **Test real Android 11–13 devices.** Emulator-only testing is insufficient for NSD, Wi-Fi, background behavior, and audio latency.

## Important — should be completed before calling the app stable

1. Coordinator migration.
2. LocalOnlyHotspot fallback.
3. Partial transfer resume.
4. Room snapshot recovery.
5. Next-two-track preload policy.
6. Expired temporary-file cleanup.
7. M3U unresolved-entry workflow.
8. Screen-off and battery-saver diagnostics.
9. Output-route logging for Bluetooth latency investigation.
10. Android 14–16 smoke testing.

## Cleanup — useful after the core experience is proven

1. Protobuf wire format.
2. In-app QR scanner.
3. Strong authenticated encryption.
4. Manual headphone latency calibration.
5. Shuffle and repeat.
6. Artwork extraction and caching.
7. Multi-source file chunking.
8. Gradle multi-module split.
9. Advanced queue voting.
10. Rich room history.

---

## 27. Final architectural decision record

| Area | Decision |
|---|---|
| Name | Unison |
| Package | `com.darius.unison` |
| Distribution | Signed APK through GitHub Releases |
| Primary OS | Android 11–13 |
| Additional OS | Android 14–16 supported; Android 17 migration planned |
| Internet | Not used |
| Google Play Services | Not used |
| Audio model | Identical local files played independently |
| Room network | Same Wi-Fi LAN |
| No-router fallback | Android LocalOnlyHotspot |
| Discovery | Android NSD/mDNS |
| Control transport | Persistent TCP to temporary coordinator |
| File transport | Direct peer-to-peer TCP |
| User authority | Equal by default |
| Technical authority | One invisible coordinator assigns order and clock |
| Duplicate detection | SHA-256 file hash |
| Playback | Media3 ExoPlayer in MediaSessionService |
| Synchronization clock | `SystemClock.elapsedRealtimeNanos()` |
| State architecture | Single serialized RoomEngine event loop |
| Persistence | Room database + content-addressed app-private file store |
| Primary imports | Share sheet and Storage Access Framework |
| Playlist interoperability | M3U/M3U8 import/export |
| Default retention | Temporary for 24 hours |
| Maximum initial room | 8 participants |

---

## 28. Official Android reference notes

The implementation should be checked against the current Android documentation for:

- Android Network Service Discovery (`NsdManager`) and its multicast-lock requirements.
- Local-only Wi-Fi hotspot permissions and lifecycle.
- Android nearby Wi-Fi permissions on Android 13+.
- Android 17 local-network permission enforcement for target SDK 37+.
- Media3 background playback with `MediaSessionService`.
- Foreground service types `mediaPlayback` and `connectedDevice`.
- Storage Access Framework and persistable URI permissions.
- Android Sharesheet temporary URI grants.
- Media3 exact seek parameters.
- Android audio-latency limitations and device variability.

Key official pages:

- <https://developer.android.com/develop/connectivity/wifi/use-nsd>
- <https://developer.android.com/reference/android/net/nsd/NsdManager>
- <https://developer.android.com/develop/connectivity/wifi/localonlyhotspot>
- <https://developer.android.com/develop/connectivity/wifi/wifi-permissions>
- <https://developer.android.com/privacy-and-security/local-network-permission>
- <https://developer.android.com/media/media3/session/background-playback>
- <https://developer.android.com/develop/background-work/services/fgs/service-types>
- <https://developer.android.com/training/data-storage/shared/documents-files>
- <https://developer.android.com/training/sharing/send>
- <https://developer.android.com/reference/androidx/media3/exoplayer/SeekParameters>
- <https://developer.android.com/ndk/guides/audio/audio-latency>

---

## 29. Immediate next implementation step

Build the synchronization spike before the library UI:

1. Create two Android projects/devices running the same Unison build.
2. Place the same local MP3 on both devices manually.
3. Connect with one TCP control socket.
4. Implement monotonic ping/pong offset estimation.
5. Prepare the same file in ExoPlayer on both devices.
6. Send `PLAY_SCHEDULED` one second in the future.
7. Log actual player start, position difference, and drift for ten minutes.
8. Add scheduled pause, seek, and resume.

This spike validates the only technically unusual part of Unison. Once it is stable, discovery, transfer, playlists, and UI are conventional Android engineering.
