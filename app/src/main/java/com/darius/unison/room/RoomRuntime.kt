package com.darius.unison.room

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.darius.unison.BuildConfig
import com.darius.unison.app.AppContainer
import com.darius.unison.model.AppCommand
import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.HotspotInfo
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.UserCommand
import com.darius.unison.model.UserFacingStatus
import com.darius.unison.network.ControlClient
import com.darius.unison.network.ControlConnection
import com.darius.unison.network.DiscoveredRoomRegistry
import com.darius.unison.network.LocalHotspotController
import com.darius.unison.network.NetworkAddressPolicy
import com.darius.unison.network.NsdDiscoveryEvent
import com.darius.unison.network.NsdDiscoveryException
import com.darius.unison.network.NsdRoomDiscovery
import com.darius.unison.network.PeerServer
import com.darius.unison.network.WifiLocks
import com.darius.unison.playback.AudioOutputRoute
import com.darius.unison.playback.LocalPlayableItem
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlayerState
import com.darius.unison.playback.ScheduledPlaybackController
import com.darius.unison.protocol.Crypto
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.EnvelopeAcceptance
import com.darius.unison.protocol.EnvelopeReplayProtector
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.protocol.ProtocolJson
import com.darius.unison.protocol.RoomSnapshotValidator
import com.darius.unison.protocol.SnapshotValidationContext
import com.darius.unison.protocol.SnapshotValidationResult
import com.darius.unison.storage.ManagedFileLease
import com.darius.unison.storage.ManagedFileLeaseReason
import com.darius.unison.sync.ClockEstimate
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.sync.ClockSyncState
import com.darius.unison.sync.PlaybackSyncController
import com.darius.unison.sync.PlaybackSyncDecision
import com.darius.unison.sync.PlaybackSyncInput
import com.darius.unison.sync.SyncAction
import com.darius.unison.sync.SynchronizationDiagnostics
import com.darius.unison.sync.SynchronizationEvent
import com.darius.unison.transfer.TransferManager
import com.darius.unison.util.AndroidMonotonicClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Owns one room session. UI permissions are peer-equal; the coordinator only serializes commands,
 * supplies the shared monotonic clock, and assigns file sources.
 */
class RoomRuntime(
    context: Context,
    private val container: AppContainer,
    private val player: PlayerPort,
    private val scope: CoroutineScope,
) : PeerServer.Handler, AutoCloseable {
    private val appContext = context.applicationContext
    private val log = container.diagnostics
    private val clock = AndroidMonotonicClock
    private val clockSync = ClockSyncEngine(clock)
    private val playbackSync = PlaybackSyncController()
    private val syncDiagnostics = SynchronizationDiagnostics(scope, log)
    private val wifiLocks = WifiLocks(appContext)
    private val discovery = NsdRoomDiscovery(appContext, wifiLocks, log)
    private val discoveredRoomRegistry = DiscoveredRoomRegistry()
    private val hotspot = LocalHotspotController(appContext, log)
    private val controlClient = ControlClient(scope, log)
    private val server = PeerServer(scope, log, this)
    private val scheduler = ScheduledPlaybackController(
        player = player,
        clock = clock,
        clockSync = clockSync,
        scope = scope,
        log = log,
        onError = { message -> setError(message) },
        usesLocalCoordinatorClock = { isCoordinator() },
    )

    private lateinit var identity: LocalIdentity
    private var engine: RoomEngine? = null
    private var roomSecret: ByteArray? = null
    private var roomPin: String? = null
    private var coordinatorPeerId: PeerId? = null
    private var coordinatorConnection: ControlConnection? = null
    private val peers = PeerRegistry<ControlConnection>()
    private val connections get() = peers.connections
    private val peerDirectory get() = peers.endpoints
    private val availability get() = peers.availability
    private val waitingForSource get() = peers.waitingForSource
    private val recentCommandIds = LinkedHashSet<String>()
    private val lastSeenElapsedMs get() = peers.lastSeenElapsedMs
    private val announcedTrackIds get() = peers.announcedTrackIds
    private val clockReadyPeers get() = peers.clockReadyPeers
    private val transferFailureCounts get() = peers.transferFailureCounts
    private val pendingTransferAssignments get() = peers.pendingTransferAssignments
    private val initializationMutex = Mutex()
    private val canonicalSideEffects = Channel<Pair<ProtocolBody, RoomSnapshot>>(capacity = 128)
    private val snapshotValidator = RoomSnapshotValidator(maxMembers = MAX_ROOM_MEMBERS)
    private val envelopeReplayProtector = EnvelopeReplayProtector()
    private val persistence = RoomPersistenceManager(container.database.roomSnapshotDao(), log)
    private val admission = ControlAdmissionController(
        snapshot = { engine?.snapshot() },
        isCoordinator = ::isCoordinator,
        localIdentity = { identity },
        roomPin = { roomPin },
        roomSecret = { roomSecret },
        onWarning = { message -> log.w("ControlAdmission", message) },
        onEnvelope = ::enqueueEnvelope,
        onClosed = ::enqueueControlClosed,
    )
    private val messageRouter = RoomMessageRouter(
        localPeerId = { identity.peerId },
        isCoordinator = ::isCoordinator,
        coordinatorTarget = { coordinatorConnection?.asSendTarget() },
        peerTargets = {
            connections.mapValues { (_, connection) -> connection.asSendTarget() }
        },
        createEnvelope = ::envelope,
        handleCoordinatorLocal = ::processCoordinatorLocalBody,
        handleLocalEnvelope = { value -> processEnvelope(identity.peerId, value) },
        onCoordinatorUnavailable = ::setError,
    )
    private val roomEvents = SerializedEventLoop<RoomEvent>(
        scope = scope,
        capacity = ROOM_EVENT_CAPACITY,
        handler = ::processRoomEvent,
        onFailure = { event, error ->
            log.e(TAG, "Room event failed type=${event::class.simpleName}", error)
            event.completionOrNull()?.completeExceptionally(error)
        },
    )

    private var transferManager: TransferManager? = null
    private var canonicalSideEffectJob: Job? = null
    private var hotspotStateJob: Job? = null
    private var playerStateJob: Job? = null
    private var discoveryJob: Job? = null
    private var discoveryGeneration = 0L
    private var heartbeatJob: Job? = null
    private var clockSyncJob: Job? = null
    private var syncJob: Job? = null
    private var retentionRefreshJob: Job? = null
    private var addressMonitorJob: Job? = null
    private var artworkRetryJob: Job? = null
    private var artworkRetryTrackId: TrackId? = null
    private var queueRefreshJob: Job? = null
    private var timelineRefreshJob: Job? = null
    private var recoveryJob: Job? = null
    private var joinTimeoutJob: Job? = null
    private var lastObservedPlayerItem: QueueItemId? = null
    private var lastHandledEndedItem: QueueItemId? = null
    private var lastObservedSeekRevision = 0L
    private var lastObservedRepeatTransitionRevision = 0L
    @Volatile private var latestPlaybackReference: CanonicalPlaybackState? = null
    private var lastPlaybackReferenceBroadcastNs = 0L
    private var lastPlaybackStatusReportNs = 0L
    private var lastPlaybackSyncTickLocalNs = 0L
    private var lastObservedOutputRoute: AudioOutputRoute? = null
    private val outputLatencyOffsetsMs = ConcurrentHashMap<AudioOutputRoute, Long>()
    private val roomQueueLeases = mutableMapOf<TrackId, ManagedFileLease>()
    private var pendingAutoResumeQueueItemId: QueueItemId? = null
    private var pendingPlayRequestedBy: PeerId? = null
    private var closed = false

    private suspend fun processRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.AppCommandReceived -> completeEvent(event.completion) {
                handleAppCommand(event.command)
            }

            is RoomEvent.NetworkEnvelopeReceived -> completeEvent(event.completion) {
                processEnvelope(event.peerId, event.envelope)
            }

            is RoomEvent.ControlConnected -> completeEvent(event.completion) {
                processControlConnected(event.connection)
            }

            is RoomEvent.ControlClosed -> completeEvent(event.completion) {
                processControlClosed(event.connection, event.cause)
            }

            is RoomEvent.CoordinatorCommandReceived -> completeEvent(event.completion) {
                applyCoordinatorCommandInActor(event.command)
            }

            is RoomEvent.CanonicalMutationRequested -> completeEvent(event.completion) {
                applyCanonicalMutation(event.body)
            }

            is RoomEvent.TrackAvailabilityObserved -> completeEvent(event.completion) {
                if (event.available) onTrackHaveInActor(event.peerId, event.trackId)
                else onTrackNeedInActor(event.peerId, event.trackId)
            }

            is RoomEvent.LocalAddressChanged -> processLocalAddressChanged(event.address)
            is RoomEvent.HotspotChanged -> processHotspotChanged(event.value, event.address)
            is RoomEvent.PlayerStateChanged -> processPlayerStateChanged(event.state)
            RoomEvent.HeartbeatTick -> processHeartbeatTick()
            RoomEvent.ClockSyncTick -> processClockSyncTick()
            RoomEvent.PlaybackSyncTick -> processPlaybackSyncTick()
            is RoomEvent.TransferCompleted -> onLocalTrackReady(event.descriptor)
            is RoomEvent.TransferFailed -> sendToCoordinator(
                ProtocolBody.TrackFailed(event.trackId, event.reason, event.sourcePeerId)
            )
        }
    }

    private suspend fun completeEvent(
        completion: CompletableDeferred<Unit>,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            completion.complete(Unit)
        } catch (cancelled: CancellationException) {
            completion.completeExceptionally(cancelled)
            throw cancelled
        } catch (error: Throwable) {
            completion.completeExceptionally(error)
            throw error
        }
    }

    private suspend fun enqueueEnvelope(peerId: PeerId, envelope: Envelope) {
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(RoomEvent.NetworkEnvelopeReceived(peerId, envelope, completion))
        completion.await()
    }

    private suspend fun enqueueControlClosed(connection: ControlConnection, cause: Throwable?) {
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(RoomEvent.ControlClosed(connection, cause, completion))
        completion.await()
    }

    private suspend fun processLocalAddressChanged(address: String?) {
        container.roomStore.update { it.copy(roomAddress = address) }
        if (address != null && ::identity.isInitialized && engine != null) {
            if (isCoordinator()) refreshLocalCoordinatorEndpoint()
            else sendToCoordinator(ProtocolBody.EndpointAnnouncement(localEndpoint()))
        }
    }

    private suspend fun processHotspotChanged(value: HotspotInfo?, address: String?) {
        container.roomStore.update { it.copy(hotspot = value, roomAddress = address) }
        if (value != null && ::identity.isInitialized && isCoordinator()) {
            refreshLocalCoordinatorEndpoint()
        }
    }

    private suspend fun processPlayerStateChanged(value: PlayerState) {
        container.roomStore.updatePlayback {
            it.copy(
                localPositionMs = value.positionMs,
                localQueueItemId = value.queueItemId,
                // UI and room transport follow intent. ExoPlayer's isPlaying becomes false
                // while buffering or settling a seek, which must not look like a user pause.
                localIsPlaying = value.playWhenReady,
                localSeekRevision = value.seekRevision,
            )
        }
        value.error?.let { error ->
            container.roomStore.updateStructure { it.copy(errorMessage = error) }
        }
        if (value.seekRevision > lastObservedSeekRevision) {
            lastObservedSeekRevision = value.seekRevision
        }
        val previous = lastObservedPlayerItem
        lastObservedPlayerItem = value.queueItemId
        val repeatedCurrentItem = value.repeatTransitionRevision > lastObservedRepeatTransitionRevision
        lastObservedRepeatTransitionRevision = value.repeatTransitionRevision
        if (previous != value.queueItemId || !value.ended) lastHandledEndedItem = null
        if (repeatedCurrentItem && value.queueItemId != null && isCoordinator()) {
            recordNaturalRepeatTransition(value.queueItemId, value.positionMs)
        } else if (previous != null && value.queueItemId != null &&
            previous != value.queueItemId && value.playWhenReady && isCoordinator()
        ) {
            pendingAutoResumeQueueItemId = null
            recordNaturalTrackTransition(value.queueItemId, value.positionMs)
        } else if (value.ended && value.queueItemId != null &&
            lastHandledEndedItem != value.queueItemId && isCoordinator()
        ) {
            lastHandledEndedItem = value.queueItemId
            recordNaturalPlaybackEnded(value.queueItemId, value.positionMs, value.durationMs)
        }
    }

    init {
        scope.launch(Dispatchers.IO) {
            persistence.discardLegacySnapshots()
        }
        canonicalSideEffectJob = scope.launch {
            for ((body, snapshot) in canonicalSideEffects) {
                try {
                    applyCanonicalSideEffects(body, snapshot)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    log.e(TAG, "Canonical side effect failed for ${body::class.simpleName}", error)
                    setError("Unison could not prepare this song")
                }
            }
        }
        addressMonitorJob = scope.launch {
            var previousAddress: String? = null
            while (isActive) {
                val address = selectedLocalAddress()
                if (address != previousAddress) {
                    previousAddress = address
                    roomEvents.submit(RoomEvent.LocalAddressChanged(address))
                }
                delay(LOCAL_ADDRESS_POLL_INTERVAL_MS)
            }
        }
        hotspotStateJob = scope.launch {
            hotspot.state.collectLatest { value ->
                // LocalOnlyHotspot reports success before the network interface is always visible.
                // Give Android a brief moment to publish the address, then update and re-advertise
                // an active room without making the user recreate it.
                if (value != null) delay(HOTSPOT_INTERFACE_SETTLE_MS)
                val address = selectedLocalAddress()
                roomEvents.submit(RoomEvent.HotspotChanged(value, address))
            }
        }
        playerStateJob = scope.launch {
            player.state.collect { value ->
                roomEvents.submit(RoomEvent.PlayerStateChanged(value))
            }
        }
    }

    suspend fun handle(command: AppCommand) {
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(RoomEvent.AppCommandReceived(command, completion))
        completion.await()
    }

    private suspend fun handleAppCommand(command: AppCommand) {
        ensureInitialized()
        log.i(TAG, "App command ${command::class.simpleName}")
        when (command) {
            is AppCommand.CreateRoom -> createRoom(command.roomName)
            is AppCommand.JoinRoom -> joinRoom(command.room, command.pin)
            AppCommand.StartDiscovery -> startDiscovery()
            AppCommand.StopDiscovery -> stopDiscovery()
            AppCommand.LeaveRoom -> leaveRoom()
            AppCommand.CreateOfflineNetwork -> hotspot.start { message -> setError(message) }
            AppCommand.StopOfflineNetwork -> hotspot.stop()
            is AppCommand.AddTracks -> addExistingTracks(command.trackIds, command.insertAfterCurrent)
            is AppCommand.SaveDisplayName -> {
                container.settings.saveDisplayName(command.name)
                identity =
                    container.settings.ensureIdentity().copy(displayName = command.name.trim().ifBlank { "Friend" })
                container.roomStore.update { it.copy(localIdentity = identity) }
                if (isCoordinator()) {
                    refreshLocalCoordinatorEndpoint()
                } else if (engine != null) {
                    sendToCoordinator(ProtocolBody.EndpointAnnouncement(localEndpoint()))
                }
            }

            is AppCommand.KeepTrack -> container.trackRepository.keep(command.trackId)
            is AppCommand.RemoveTemporaryTrack -> container.trackRepository.deleteTemporary(command.trackId)
            AppCommand.Play -> {
                if (prepareCurrentTrackForPlay()) {
                    submitUserCommand(UserCommand.Play(requestedBy = identity.peerId))
                }
            }

            AppCommand.Pause -> {
                pendingPlayRequestedBy = null
                submitUserCommand(UserCommand.Pause(requestedBy = identity.peerId))
            }

            is AppCommand.Seek -> {
                pendingPlayRequestedBy = null
                submitUserCommand(UserCommand.Seek(requestedBy = identity.peerId, positionMs = command.positionMs))
            }

            AppCommand.SkipNext -> {
                pendingPlayRequestedBy = null
                submitUserCommand(UserCommand.SkipNext(requestedBy = identity.peerId))
            }

            AppCommand.SkipPrevious -> {
                pendingPlayRequestedBy = null
                submitUserCommand(UserCommand.SkipPrevious(requestedBy = identity.peerId))
            }

            is AppCommand.PlayQueueItem -> {
                pendingPlayRequestedBy = null
                submitUserCommand(
                    UserCommand.PlayQueueItem(
                        requestedBy = identity.peerId,
                        queueItemId = command.queueItemId,
                    )
                )
            }

            AppCommand.ShuffleQueue -> submitUserCommand(
                UserCommand.QueueShuffle(
                    requestedBy = identity.peerId,
                    shuffleSeed = clock.nowNs() xor identity.peerId.value.hashCode().toLong(),
                )
            )

            is AppCommand.SetRepeat -> submitUserCommand(
                UserCommand.PlaybackModeChange(
                    requestedBy = identity.peerId,
                    shuffleEnabled = engine?.snapshot()?.shuffleEnabled == true,
                    repeatMode = command.mode,
                    shuffleSeed = clock.nowNs() xor identity.peerId.value.hashCode().toLong(),
                )
            )

            is AppCommand.RemoveQueueItem -> submitUserCommand(
                UserCommand.QueueRemove(
                    requestedBy = identity.peerId,
                    queueItemId = command.queueItemId
                )
            )

            is AppCommand.MoveQueueItem -> submitUserCommand(
                UserCommand.QueueMove(
                    requestedBy = identity.peerId,
                    queueItemId = command.queueItemId,
                    newIndex = command.newIndex
                )
            )

            AppCommand.ClearPlayed -> submitUserCommand(
                UserCommand.QueueClearPlayed(requestedBy = identity.peerId)
            )

            is AppCommand.UpdateRoomOptions -> submitUserCommand(
                UserCommand.OptionsChange(
                    requestedBy = identity.peerId,
                    options = command.options
                )
            )
        }
    }

    private suspend fun ensureInitialized() {
        initializationMutex.withLock {
            if (!::identity.isInitialized) {
                identity = container.settings.ensureIdentity()
                container.roomStore.update { it.copy(localIdentity = identity) }
            }
            ensureServerAndTransfers()
        }
    }

    private fun ensureServerAndTransfers() {
        val port = server.start()
        if (transferManager == null && ::identity.isInitialized) {
            transferManager = TransferManager(
                localIdentity = identity,
                listeningPort = { server.port },
                appVersion = BuildConfig.VERSION_NAME,
                trackRepository = container.trackRepository,
                fileStore = container.fileStore,
                scope = scope,
                log = log,
                retentionPolicyProvider = { container.settings.retentionPolicy.first() },
                onProgress = { progress ->
                    container.roomStore.updateTransfers { state ->
                        state.copy(transfers = state.transfers + (progress.trackId to progress))
                    }
                },
                onCompleted = { descriptor -> roomEvents.submit(RoomEvent.TransferCompleted(descriptor)) },
                onFailed = { trackId, sourcePeerId, reason ->
                    roomEvents.submit(RoomEvent.TransferFailed(trackId, sourcePeerId, reason))
                },
            )
        }
        container.roomStore.update { it.copy(roomPort = port) }
    }

    private suspend fun createRoom(requestedName: String?) {
        resetSession(keepDiscovery = false)
        player.setRepeatCurrentItem(false)
        container.roomStore.reset()
        val id = UUID.randomUUID().toString()
        val name = requestedName?.trim()?.take(60)?.ifBlank { null } ?: "${identity.displayName}'s room"
        val pin = Crypto.randomSixDigitPin()
        roomPin = pin
        roomSecret = Crypto.randomBytes(32)
        coordinatorPeerId = identity.peerId
        clockSync.reset()
        clockReadyPeers.clear()
        clockReadyPeers.add(identity.peerId)
        val endpoint = localEndpoint()
        peerDirectory[identity.peerId] = endpoint
        val initial = RoomSnapshot(
            roomId = id,
            roomName = name,
            term = CoordinatorTerm(1, identity.peerId),
            sequence = 0,
            members = listOf(MemberSnapshot(identity.peerId, identity.displayName, endpoint)),
        )
        check(snapshotFitsProtocol(initial)) { "Locally-created room snapshot is invalid" }
        engine = RoomEngine(initial)
        discovery.advertise(id, name, server.port, 1, onError = ::setError)
        container.roomStore.update { it.copy(localRoomPin = pin) }
        updateSnapshot(initial, RoomLifecycleState.CONNECTED, "Room ready")
        startSessionJobs()
        log.i(TAG, "Created room id=${id.take(8)} port=${server.port}")
    }

    private suspend fun joinRoom(room: DiscoveredRoom, pin: String) {
        resetSession(keepDiscovery = false)
        container.roomStore.reset()
        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.CONNECTING,
                status = UserFacingStatus.PREPARING,
                statusMessage = "Connecting…",
                errorMessage = null
            )
        }
        var identityCollisionRetried = false
        while (true) {
            try {
                log.i(
                    TAG,
                    "Joining room id=${room.roomId.take(8)} peer=${identity.peerId.value.take(8)} " +
                        "target=${room.hostAddress}:${room.port}",
                )
                val connected = controlClient.connectWithPin(
                    identity = identity,
                    roomId = room.roomId,
                    host = room.hostAddress,
                    port = room.port,
                    listeningPort = server.port,
                    pin = pin,
                    appVersion = BuildConfig.VERSION_NAME,
                    onEnvelope = ::enqueueEnvelope,
                    onClosed = ::enqueueControlClosed,
                )
                roomSecret = connected.roomSecret
                roomPin = null
                coordinatorPeerId = connected.coordinatorPeerId
                coordinatorConnection = connected.connection
                connections[connected.coordinatorPeerId] = connected.connection
                connected.connection.start()
                container.roomStore.update {
                    it.copy(lifecycle = RoomLifecycleState.JOINING, statusMessage = "Joining room…")
                }
                // The encrypted handshake only authenticates the socket. Do not start heartbeat or
                // clock-sync traffic until JoinAccepted installs the canonical room context; otherwise
                // envelopes have no authoritative room ID and must be rejected by FrameCodec.
                joinTimeoutJob?.cancel()
                joinTimeoutJob = scope.launch {
                    delay(INITIAL_JOIN_TIMEOUT_MS)
                    if (engine == null && coordinatorConnection === connected.connection) {
                        connected.connection.close(IllegalStateException("Join acceptance timed out"))
                    }
                }
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!identityCollisionRetried && isIdentityCollision(error)) {
                    identityCollisionRetried = true
                    refreshDuplicatedIdentity()
                    continue
                }
                log.w(TAG, "Could not join room", error)
                setFailure(userFacingJoinFailure(error))
                return
            }
        }
    }

    private suspend fun refreshDuplicatedIdentity() {
        val previousPeerId = identity.peerId
        transferManager?.cancelAll()
        transferManager = null
        identity = container.settings.rotateIdentity()
        container.roomStore.update {
            it.copy(
                localIdentity = identity,
                lifecycle = RoomLifecycleState.CONNECTING,
                status = UserFacingStatus.PREPARING,
                statusMessage = "Refreshing device identity…",
                errorMessage = null,
            )
        }
        ensureServerAndTransfers()
        log.w(
            TAG,
            "Refreshed duplicated identity old=${previousPeerId.value.take(8)} " +
                "new=${identity.peerId.value.take(8)}; retrying join once",
        )
    }

    private fun isIdentityCollision(error: Throwable): Boolean =
        error.message.orEmpty().contains(IDENTITY_COLLISION_REASON, ignoreCase = true)

    private fun startDiscovery() {
        if (discoveryJob?.isActive == true) {
            log.i(TAG, "Nearby-room discovery is already active")
            return
        }
        discoveryJob?.cancel()
        val scanGeneration = ++discoveryGeneration
        discoveredRoomRegistry.clear()
        container.roomStore.update {
            it.copy(
                lifecycle = if (it.snapshot == null) RoomLifecycleState.DISCOVERING else it.lifecycle,
                discoveredRooms = emptyList(),
                discoveryCompleted = false,
                statusMessage = if (it.snapshot == null) "Looking for nearby rooms…" else it.statusMessage,
                errorMessage = null,
            )
        }
        val scanJob = scope.launch(start = CoroutineStart.LAZY) {
            fun publishRooms() {
                val rooms = discoveredRoomRegistry.rooms()
                container.roomStore.update { state ->
                    if (state.discoveredRooms == rooms) state else state.copy(discoveredRooms = rooms)
                }
            }
            try {
                val completedNormally = withTimeoutOrNull(MANUAL_DISCOVERY_WINDOW_MS) {
                    discovery.discover().collect { event ->
                        when (event) {
                            is NsdDiscoveryEvent.Found -> {
                                if (discoveredRoomRegistry.found(event.room)) {
                                    publishRooms()
                                }
                            }

                            // Keep a room found during this short scan visible after the browse
                            // window closes. The next button press clears the list and performs a
                            // fresh scan, avoiding mDNS loss flicker without background discovery.
                            is NsdDiscoveryEvent.Lost -> Unit
                        }
                    }
                    true
                }
                if (completedNormally == true) {
                    log.w(TAG, "NSD discovery flow completed before the manual scan window ended")
                } else {
                    log.i(TAG, "Manual nearby-room search finished")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val recoverable = (error as? NsdDiscoveryException)?.recoverable != false
                log.w(TAG, "Manual nearby-room search failed recoverable=$recoverable", error)
                container.roomStore.update { state ->
                    state.copy(
                        errorMessage = if (recoverable) {
                            "Room search stopped. Tap Find rooms to try again"
                        } else {
                            "Nearby room access is unavailable"
                        },
                    )
                }
            } finally {
                // callbackFlow owns its exact listener and closes it in awaitClose. Do not call the
                // class-level stop method here: an older cancelled job could otherwise stop a newer
                // scan that has already installed its listener.
                if (discoveryGeneration == scanGeneration) {
                    discoveryJob = null
                    container.roomStore.update { state ->
                        state.copy(
                            lifecycle = if (state.snapshot == null) RoomLifecycleState.IDLE else state.lifecycle,
                            discoveryCompleted = state.snapshot == null,
                            statusMessage = if (state.snapshot == null) null else state.statusMessage,
                        )
                    }
                }
            }
        }
        discoveryJob = scanJob
        scanJob.start()
    }

    private fun stopDiscovery() {
        discoveryGeneration++
        discoveryJob?.cancel()
        discoveryJob = null
        discovery.stopDiscovery()
        discoveredRoomRegistry.clear()
        container.roomStore.update { state ->
            state.copy(
                lifecycle = if (state.snapshot == null) RoomLifecycleState.IDLE else state.lifecycle,
                discoveredRooms = emptyList(),
                discoveryCompleted = false,
                statusMessage = if (state.snapshot == null) null else state.statusMessage,
            )
        }
    }

    private suspend fun addExistingTracks(trackIds: List<TrackId>, insertAfterCurrent: Boolean) {
        if (trackIds.isEmpty()) return
        val remainingCapacity = (
            RoomReducer.MAX_QUEUE_ITEMS - (engine?.snapshot()?.queue?.size ?: 0)
            ).coerceAtLeast(0)
        if (remainingCapacity == 0) {
            setError("The room queue is full")
            return
        }
        container.roomStore.update {
            it.copy(status = UserFacingStatus.PREPARING, statusMessage = "Adding music…", errorMessage = null)
        }
        val requestedTrackIds = trackIds.take(remainingCapacity)
        val descriptors = container.trackRepository.getMany(requestedTrackIds)
        val available = withContext(Dispatchers.IO) {
            descriptors.filter { descriptor ->
                suspendResult { container.trackRepository.requireReadableFile(descriptor.trackId) != null }
                    .onFailure { error -> log.w(TAG, "Could not prepare ${descriptor.trackId.value.take(8)}", error) }
                    .getOrDefault(false)
            }
        }
        if (available.isEmpty()) {
            setError("Unison could not open this music")
            return
        }
        available.chunked(RoomReducer.MAX_TRACKS_PER_COMMAND).forEach { tracks ->
            submitUserCommand(
                UserCommand.QueueAdd(
                    requestedBy = identity.peerId,
                    tracks = tracks,
                    insertAfterCurrent = insertAfterCurrent,
                )
            )
        }
        if (available.size < requestedTrackIds.size) {
            setError("Some songs could not be opened")
        } else if (requestedTrackIds.size < trackIds.size) {
            setError("The room queue holds up to ${RoomReducer.MAX_QUEUE_ITEMS} songs")
        }
    }

    /**
     * Removes the first-track race: the room snapshot becomes visible before its asynchronous
     * queue side effects necessarily finish. Warm the local Media3 queue and refresh this peer's
     * availability before evaluating Play. Duplicate TrackHave messages are harmless and make a
     * solo room deterministic.
     */
    private suspend fun prepareCurrentTrackForPlay(): Boolean {
        val snapshot = engine?.snapshot() ?: return true
        val item = PlaybackRequestPolicy.currentItem(snapshot) ?: return true
        container.roomStore.update {
            it.copy(status = UserFacingStatus.PREPARING, statusMessage = "Preparing music…", errorMessage = null)
        }
        refreshPlayerQueue(snapshot)
        val hasFile = withContext(Dispatchers.IO) {
            suspendResult { container.trackRepository.requireReadableFile(item.track.trackId) != null }
                .onFailure { error -> log.w(TAG, "Could not prepare ${item.track.trackId.value.take(8)}", error) }
                .getOrDefault(false)
        }
        log.i(
            TAG,
            "Play preflight item=${item.queueItemId.value.take(8)} localFile=$hasFile " +
                "playerItem=${player.state.value.queueItemId?.value?.take(8)} prepared=${player.state.value.prepared}",
        )
        announcedTrackIds.add(item.track.trackId)
        if (isCoordinator()) {
            if (hasFile) {
                onTrackHave(identity.peerId, item.track.trackId)
            } else {
                pendingPlayRequestedBy = identity.peerId
                onTrackNeed(identity.peerId, item.track.trackId)
                if (snapshot.members.count { it.connected } <= 1) {
                    pendingPlayRequestedBy = null
                    setError("This song is no longer available. Add it again.")
                    return false
                }
                container.roomStore.update {
                    it.copy(status = UserFacingStatus.RECEIVING, statusMessage = "Getting the song ready…")
                }
                return false
            }
        } else {
            sendToCoordinator(if (hasFile) ProtocolBody.TrackHave(item.track.trackId) else ProtocolBody.TrackNeed(item.track.trackId))
        }
        return true
    }

    private suspend fun submitUserCommand(command: UserCommand) {
        val snapshot = engine?.snapshot() ?: run {
            setError("Join or create a room first")
            return
        }
        if (snapshot.term.coordinatorPeerId == identity.peerId) {
            applyCoordinatorCommand(command)
        } else {
            sendToCoordinator(ProtocolBody.UserCommandRequest(command))
        }
    }

    private suspend fun applyCoordinatorCommand(command: UserCommand) {
        if (roomEvents.isCurrentContext()) {
            applyCoordinatorCommandInActor(command)
            return
        }
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(RoomEvent.CoordinatorCommandReceived(command, completion))
        completion.await()
    }

    private suspend fun applyCoordinatorCommandInActor(command: UserCommand) {
        if (!recentCommandIds.add(command.commandId)) return
        while (recentCommandIds.size > 256) recentCommandIds.remove(recentCommandIds.first())

        val roomEngine = engine ?: return
        val current = roomEngine.snapshot()
        val currentItem = PlaybackRequestPolicy.currentItem(current)
        if (command is UserCommand.Play && currentItem != null && PlaybackRequestPolicy.shouldDeferPlay(current)) {
            pendingPlayRequestedBy = command.requestedBy
            log.i(
                TAG,
                "Play deferred item=${currentItem.queueItemId.value.take(8)} " +
                    "prepared=false connected=${current.members.count { it.connected }}",
            )
            if (command.requestedBy == identity.peerId) {
                container.roomStore.update {
                    it.copy(
                        status = UserFacingStatus.PREPARING,
                        statusMessage = "Preparing music… Play will start automatically.",
                        errorMessage = null,
                    )
                }
            }
            prepareWindow(current)
            val currentTrackId = current.playback.queueItemId
                ?.let { id -> current.queue.firstOrNull { it.queueItemId == id } }
                ?.track?.trackId
                ?: current.queue.firstOrNull()?.track?.trackId
            if (currentTrackId != null) reevaluatePreparation(currentTrackId)
            return
        }

        when (val decision = roomEngine.decide(command, clock.nowNs(), ::snapshotFitsProtocol)) {
            is RoomReducer.Decision.Rejected -> {
                log.w(TAG, "Command ${command::class.simpleName} rejected: ${decision.reason}")
                if (command.requestedBy == identity.peerId) setError(decision.reason)
                else send(command.requestedBy, ProtocolBody.CommandRejected(command.commandId, decision.reason))
            }

            is RoomReducer.Decision.Accepted -> {
                log.i(TAG, "Command ${command::class.simpleName} accepted mutations=${decision.mutations.size}")
                if (command is UserCommand.Play || command is UserCommand.Pause || command is UserCommand.Seek ||
                    command is UserCommand.SkipNext || command is UserCommand.SkipPrevious ||
                    command is UserCommand.PlayQueueItem
                ) {
                    pendingAutoResumeQueueItemId = null
                    pendingPlayRequestedBy = null
                }
                if (command is UserCommand.PlayQueueItem && current.options.waitAtTrackBoundary &&
                    command.queueItemId !in current.preparedQueueItemIds
                ) {
                    pendingAutoResumeQueueItemId = command.queueItemId
                }
                decision.mutations.forEach { mutation ->
                    updateSnapshot(mutation.snapshot)
                    broadcastCanonical(mutation.sequence, mutation.body)
                    if (!canonicalSideEffects.trySend(mutation.body to mutation.snapshot).isSuccess) {
                        throw IllegalStateException("Canonical side-effect queue is full")
                    }
                }
            }
        }
    }

    private suspend fun applyCanonicalSideEffects(body: ProtocolBody, snapshot: RoomSnapshot) {
        log.i(TAG, "Apply ${body::class.simpleName} sequence=${snapshot.sequence}")
        when (body) {
            is ProtocolBody.QueueItemAdded -> requestTimelineRefresh(snapshot)

            is ProtocolBody.QueueItemRemoved -> {
                // Removing the audible item is followed by a scheduled CurrentItemChanged
                // mutation. Keep the existing ExoPlayer timeline until that timestamp; otherwise
                // setQueue() would select the replacement immediately and create an early skip.
                if (player.state.value.queueItemId != body.queueItemId) {
                    requestTimelineRefresh(snapshot)
                } else {
                    prepareWindow(snapshot)
                }
            }

            is ProtocolBody.QueueItemMoved,
            is ProtocolBody.QueueItemPreparation,
            is ProtocolBody.RoomOptionsChanged,
            is ProtocolBody.PlaybackModeChanged -> {
                player.setRepeatCurrentItem(snapshot.repeatMode == RepeatMode.ONE)
                requestTimelineRefresh(snapshot)
            }

            is ProtocolBody.PlayScheduled -> if (canApplyScheduledCommand()) {
                markTrackPlayed(snapshot, body.queueItemId)
                scheduler.schedulePlay(body.queueItemId, body.positionMs, body.executeAtCoordinatorNs)
            }

            is ProtocolBody.PauseScheduled -> if (canApplyScheduledCommand()) {
                scheduler.schedulePause(body.positionMs, body.executeAtCoordinatorNs)
            }

            is ProtocolBody.SeekScheduled -> if (canApplyScheduledCommand()) {
                scheduler.scheduleSeek(
                    body.queueItemId,
                    body.positionMs,
                    body.resumePlayback,
                    body.executeAtCoordinatorNs
                )
            }

            is ProtocolBody.CurrentItemChanged -> {
                body.queueItemId?.let { markTrackPlayed(snapshot, it) }
                // A canonical transition is published before its execution timestamp. Updating the
                // player's current item immediately would make this device skip early. Keep the
                // currently audible item selected, preload the target, execute at room time, then
                // reconcile the timeline after the transition.
                val localBefore = player.state.value
                val currentStillInQueue = localBefore.queueItemId?.let { currentId ->
                    snapshot.queue.any { it.queueItemId == currentId }
                } == true
                if (currentStillInQueue) {
                    refreshPlayerQueue(snapshot, localBefore.queueItemId, localBefore.positionMs)
                } else if (localBefore.queueItemId == null) {
                    refreshPlayerQueue(snapshot)
                }
                prepareWindow(snapshot)
                if (!canApplyScheduledCommand()) return
                body.queueItemId?.let { target ->
                    val expectedNow = body.positionMs + if (body.resumePlayback) {
                        ((clockSync.coordinatorNowNs() - body.executeAtCoordinatorNs).coerceAtLeast(0) / 1_000_000L)
                    } else 0L
                    val local = player.state.value
                    if (local.queueItemId != target || abs(local.positionMs - expectedNow) > 180 ||
                        body.executeAtCoordinatorNs > clockSync.coordinatorNowNs()
                    ) {
                        scheduler.scheduleSeek(
                            target,
                            body.positionMs,
                            body.resumePlayback,
                            body.executeAtCoordinatorNs
                        )
                    }
                } ?: scheduler.schedulePause(0, body.executeAtCoordinatorNs)
                scheduleQueueRefresh(body.executeAtCoordinatorNs)
            }

            else -> Unit
        }
    }

    private fun encodedSnapshotSizeBytes(snapshot: RoomSnapshot): Int =
        ProtocolJson.encodeToString(snapshot).encodeToByteArray().size

    private fun snapshotFitsProtocol(snapshot: RoomSnapshot): Boolean =
        snapshotValidator.validate(
            snapshot,
            SnapshotValidationContext(encodedSizeBytes = encodedSnapshotSizeBytes(snapshot)),
        ) is SnapshotValidationResult.Valid

    private fun validateIncomingSnapshot(
        snapshot: RoomSnapshot,
        expectedRoomId: String,
        expectedCoordinatorPeerId: PeerId,
        minimumTerm: Long? = null,
        minimumSequence: Long? = null,
    ): Boolean {
        val result = snapshotValidator.validate(
            snapshot,
            SnapshotValidationContext(
                expectedRoomId = expectedRoomId,
                expectedCoordinatorPeerId = expectedCoordinatorPeerId,
                minimumTerm = minimumTerm,
                minimumSequence = minimumSequence,
                encodedSizeBytes = encodedSnapshotSizeBytes(snapshot),
            ),
        )
        if (result is SnapshotValidationResult.Invalid) {
            log.w(TAG, "Rejected snapshot: ${result.summary}")
            return false
        }
        return true
    }

    private fun validatePeerDirectory(peers: List<PeerEndpoint>, snapshot: RoomSnapshot): Boolean {
        if (peers.size > MAX_ROOM_MEMBERS || peers.map { it.peerId }.distinct().size != peers.size) return false
        val memberIds = snapshot.members.map { it.peerId }.toSet()
        return peers.all { endpoint ->
            endpoint.peerId in memberIds &&
                endpoint.displayName.length in 1..80 && endpoint.displayName.none { it.isISOControl() } &&
                endpoint.hostAddress.length in 1..255 && endpoint.hostAddress.none { it.isISOControl() } &&
                endpoint.port in 1..65535 &&
                endpoint.appVersion.length in 1..64 && endpoint.appVersion.none { it.isISOControl() }
        }
    }

    private suspend fun processEnvelope(peerId: PeerId, envelope: Envelope) {
        lastSeenElapsedMs[peerId] = SystemClock.elapsedRealtime()
        val current = engine?.snapshot()
        if (current != null && envelope.roomId != current.roomId) return
        val coordinatorNow = when {
            isCoordinator() -> clock.nowNs()
            clockSync.synchronized -> clockSync.coordinatorNowNs()
            else -> null
        }
        when (val acceptance = envelopeReplayProtector.evaluate(
            socketPeerId = peerId,
            envelope = envelope,
            acceptedTerm = current?.term?.number,
            lastAppliedSequence = current?.sequence,
            coordinatorNowNs = coordinatorNow,
        )) {
            EnvelopeAcceptance.Accepted -> Unit
            EnvelopeAcceptance.Duplicate -> return
            is EnvelopeAcceptance.SequenceGap -> {
                log.w(TAG, "Rejected sequence gap expected=${acceptance.expected} actual=${acceptance.actual}")
                if (!isCoordinator() && current != null) {
                    sendToCoordinator(ProtocolBody.SnapshotRequest(current.sequence))
                }
                return
            }
            is EnvelopeAcceptance.Rejected -> {
                log.w(TAG, "Rejected envelope peer=${peerId.value.take(8)} reason=${acceptance.reason}")
                return
            }
        }
        when (val body = envelope.body) {
            is ProtocolBody.JoinAccepted -> {
                if (isCoordinator() || peerId != coordinatorPeerId) return
                if (body.snapshot.term.number != envelope.term ||
                    !validateIncomingSnapshot(
                        snapshot = body.snapshot,
                        expectedRoomId = envelope.roomId,
                        expectedCoordinatorPeerId = peerId,
                        minimumTerm = current?.term?.number,
                        minimumSequence = current?.sequence,
                    ) || !validatePeerDirectory(body.peerDirectory, body.snapshot)
                ) {
                    throw IllegalStateException("Invalid join acceptance")
                }
                joinTimeoutJob?.cancel(); joinTimeoutJob = null
                clockSync.reset()
                clockReadyPeers.clear()
                engine = RoomEngine(body.snapshot)
                coordinatorPeerId = body.snapshot.term.coordinatorPeerId
                body.peerDirectory.forEach { peerDirectory[it.peerId] = it }
                lastSeenElapsedMs[body.snapshot.term.coordinatorPeerId] = SystemClock.elapsedRealtime()
                announcedTrackIds.clear()
                recoveryJob?.cancel(); recoveryJob = null

                // Commit the room to UI only after local playback state can represent the accepted
                // snapshot. A local preparation failure is recoverable and must not tear down an
                // otherwise valid authenticated control connection.
                try {
                    reconcileSnapshotQueue(body.snapshot)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    log.e(TAG, "Initial playback reconciliation failed", error)
                    setError("Connected, but playback could not be prepared")
                }
                updateSnapshot(body.snapshot, RoomLifecycleState.CONNECTED, "Connected")
                startSessionJobs()
                try {
                    prepareWindow(body.snapshot)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    log.w(TAG, "Initial track preparation failed", error)
                    setError("Connected; some music may need to be prepared again")
                }
            }

            is ProtocolBody.UserCommandRequest -> if (isCoordinator()) {
                if (body.command.requestedBy != peerId) {
                    send(peerId, ProtocolBody.CommandRejected(body.command.commandId, "Invalid command identity"))
                } else {
                    applyCoordinatorCommand(body.command)
                }
            }

            is ProtocolBody.CommandRejected -> if (peerId == coordinatorPeerId && body.commandId.isNotBlank()) setError(
                body.reason
            )

            is ProtocolBody.PeerJoined -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.PeerUpdated -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.PeerLeft -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.QueueItemAdded -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.QueueItemRemoved -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.QueueItemMoved -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.QueueItemPreparation -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.RoomOptionsChanged -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.PlaybackModeChanged -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.PlayScheduled -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.PauseScheduled -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.SeekScheduled -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.CurrentItemChanged -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.Snapshot -> {
                val before = engine?.snapshot()
                val expectedCoordinator = before?.term?.coordinatorPeerId ?: coordinatorPeerId ?: return
                if (isCoordinator() || peerId != expectedCoordinator) return
                if (!validateIncomingSnapshot(
                        snapshot = body.snapshot,
                        expectedRoomId = envelope.roomId,
                        expectedCoordinatorPeerId = peerId,
                        minimumTerm = before?.term?.number,
                        minimumSequence = before?.sequence,
                    )
                ) return
                if (before != null && body.snapshot.term.number > before.term.number) {
                    clockSync.reset()
                    playbackSync.reset(preserveLearnedBaseline = true)
                    latestPlaybackReference = null
                }
                val replaced = engine?.replace(body.snapshot) ?: body.snapshot.also { engine = RoomEngine(it) }
                updateSnapshot(replaced)
                reconcileSnapshotQueue(replaced)
                prepareWindow(replaced)
            }

            is ProtocolBody.SnapshotRequest -> if (isCoordinator()) send(
                peerId,
                ProtocolBody.Snapshot(engine?.snapshot() ?: return)
            )

            is ProtocolBody.PeerDirectory -> if (!isCoordinator() && peerId == coordinatorPeerId) {
                val snapshot = engine?.snapshot() ?: return
                if (!validatePeerDirectory(body.peers, snapshot)) {
                    log.w(TAG, "Rejected invalid peer directory")
                    return
                }
                peerDirectory.keys.retainAll(body.peers.map { it.peerId }.toSet())
                body.peers.forEach { peerDirectory[it.peerId] = it }
            }

            is ProtocolBody.EndpointAnnouncement -> if (isCoordinator()) {
                updatePeerEndpoint(peerId, body.endpoint)
            }

            is ProtocolBody.Heartbeat -> {
                val snapshot = engine?.snapshot()
                if (isCoordinator()) {
                    send(peerId, ProtocolBody.AckSequence(snapshot?.sequence ?: 0))
                } else if (peerId == snapshot?.term?.coordinatorPeerId && body.lastAppliedSequence > snapshot.sequence) {
                    sendToCoordinator(ProtocolBody.SnapshotRequest(snapshot.sequence))
                }
            }

            is ProtocolBody.AckSequence -> {
                val snapshot = engine?.snapshot()
                if (!isCoordinator() && peerId == snapshot?.term?.coordinatorPeerId && body.sequence > snapshot.sequence) {
                    sendToCoordinator(ProtocolBody.SnapshotRequest(snapshot.sequence))
                }
            }

            is ProtocolBody.ClockPing -> if (isCoordinator()) {
                val receive = clock.nowNs()
                send(peerId, ProtocolBody.ClockPong(body.pingId, body.guestSendNs, receive, clock.nowNs()))
            }

            is ProtocolBody.ClockPong -> {
                if (isCoordinator() || peerId != coordinatorPeerId) return
                val wasSynchronized = clockSync.synchronized
                clockSync.recordPong(
                    body.pingId, body.guestSendNs, body.coordinatorReceiveNs, body.coordinatorSendNs, clock.nowNs()
                )
                if (!wasSynchronized && clockSync.synchronized) {
                    sendToCoordinator(ProtocolBody.ClockReady())
                    container.roomStore.update { it.copy(status = UserFacingStatus.READY, statusMessage = "Ready") }
                }
            }

            is ProtocolBody.ClockReady -> if (isCoordinator() && body.synchronized) {
                clockReadyPeers.add(peerId)
                reevaluateAllPreparation()
                val snapshot = engine?.snapshot()
                if (snapshot != null) {
                    val now = clock.nowNs()
                    send(peerId, ProtocolBody.PlaybackStateSync(snapshot.playback.forStateSync(now)))
                }
            }

            is ProtocolBody.PlaybackStateSync -> if (peerId == coordinatorPeerId) applyPlaybackSync(body.playback)
            is ProtocolBody.PlaybackStatusReport -> if (isCoordinator()) updateMemberPlayback(peerId, body)
            is ProtocolBody.MemberPlaybackStatus -> if (!isCoordinator() && peerId == coordinatorPeerId) {
                applyEphemeralMemberPlayback(body)
            }

            is ProtocolBody.TrackHave -> if (isCoordinator()) onTrackHave(peerId, body.trackId)
            is ProtocolBody.TrackNeed -> if (isCoordinator()) onTrackNeed(peerId, body.trackId)
            is ProtocolBody.TrackSourceAssigned -> if (!isCoordinator() && peerId == coordinatorPeerId) onTrackSourceAssigned(
                body
            )

            is ProtocolBody.TrackSourceAuthorized -> if (isCoordinator()) onTrackSourceAuthorized(peerId, body)
            is ProtocolBody.TrackReady -> if (isCoordinator()) onTrackHave(peerId, body.trackId)
            is ProtocolBody.TrackFailed -> if (isCoordinator()) onTrackFailed(peerId, body)
            is ProtocolBody.LeaveRoom -> connections[peerId]?.close()
            is ProtocolBody.RejoinRequest -> if (isCoordinator()) {
                send(peerId, ProtocolBody.Snapshot(engine?.snapshot() ?: return))
                body.cachedTrackIds.take(MAX_REJOIN_CACHE_IDS).forEach { onTrackHave(peerId, it) }
            }

            is ProtocolBody.TrackDescriptorMessage -> if (!isCoordinator() && peerId == coordinatorPeerId) {
                announceLocalAvailability(body.descriptor)
            }

            is ProtocolBody.TransferCancelled -> Unit
        }
    }

    private suspend fun applyCanonicalEnvelope(envelope: Envelope, body: ProtocolBody) {
        val snapshot = engine?.snapshot() ?: return
        if (envelope.senderPeerId != snapshot.term.coordinatorPeerId || envelope.term != snapshot.term.number) return
        val sequence = envelope.sequence ?: return
        if (sequence <= snapshot.sequence) return
        if (sequence != snapshot.sequence + 1) {
            // TCP preserves order within one connection, so a gap means this peer reconnected or
            // replaced state without receiving every mutation. Never build on a partial history.
            sendToCoordinator(ProtocolBody.SnapshotRequest(snapshot.sequence))
            return
        }
        val updated = engine?.applyValidated(sequence, body, ::snapshotFitsProtocol) ?: run {
            log.w(TAG, "Rejected canonical mutation ${body::class.simpleName}: invalid resulting snapshot")
            sendToCoordinator(ProtocolBody.SnapshotRequest(snapshot.sequence))
            return
        }
        updateSnapshot(updated)
        if (!canonicalSideEffects.trySend(body to updated).isSuccess) {
            throw IllegalStateException("Canonical side-effect queue is full")
        }
    }

    private suspend fun announceLocalAvailability(track: TrackDescriptor) {
        if (!announcedTrackIds.add(track.trackId)) return
        val hasFile = suspendResult { container.trackRepository.requireReadableFile(track.trackId) != null }
            .onFailure { error -> log.w(TAG, "Could not read ${track.trackId.value.take(8)}", error) }
            .getOrDefault(false)
        if (isCoordinator()) {
            if (hasFile) onTrackHave(identity.peerId, track.trackId) else onTrackNeed(identity.peerId, track.trackId)
        } else {
            sendToCoordinator(if (hasFile) ProtocolBody.TrackHave(track.trackId) else ProtocolBody.TrackNeed(track.trackId))
        }
    }

    private suspend fun onTrackHave(peerId: PeerId, trackId: TrackId) {
        if (roomEvents.isCurrentContext()) {
            onTrackHaveInActor(peerId, trackId)
            return
        }
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(RoomEvent.TrackAvailabilityObserved(peerId, trackId, true, completion))
        completion.await()
    }

    private suspend fun onTrackHaveInActor(peerId: PeerId, trackId: TrackId) {
        availability.computeIfAbsent(trackId) { ConcurrentHashMap.newKeySet() }.add(peerId)
        transferFailureCounts.keys.removeAll { it.startsWith("${trackId.value}:$peerId:") || it.endsWith(":$peerId") }
        waitingForSource[trackId]?.remove(peerId)
        assignWaiting(trackId)
        reevaluatePreparation(trackId)
    }

    private suspend fun onTrackNeed(peerId: PeerId, trackId: TrackId) {
        if (roomEvents.isCurrentContext()) {
            onTrackNeedInActor(peerId, trackId)
            return
        }
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(RoomEvent.TrackAvailabilityObserved(peerId, trackId, false, completion))
        completion.await()
    }

    private suspend fun onTrackNeedInActor(peerId: PeerId, trackId: TrackId) {
        availability[trackId]?.remove(peerId)
        waitingForSource.computeIfAbsent(trackId) { ConcurrentHashMap.newKeySet() }.add(peerId)
        reevaluatePreparation(trackId)
        assignWaiting(trackId)
    }

    private suspend fun assignWaiting(trackId: TrackId) {
        if (!isCoordinator()) return
        val snapshot = engine?.snapshot() ?: return
        val descriptor = snapshot.queue.firstOrNull { it.track.trackId == trackId }?.track
            ?: container.trackRepository.get(trackId)
            ?: return
        val sources = availability[trackId].orEmpty()
        val destinations = waitingForSource[trackId]?.toList().orEmpty()
        destinations.forEach { destination ->
            if (pendingTransferAssignments.values.any {
                    it.track.trackId == trackId && it.destinationPeerId == destination
                }) return@forEach
            val sourceId =
                sources.firstOrNull { it != destination && (it == identity.peerId || connections.containsKey(it)) }
                    ?: return@forEach
            val source = if (sourceId == identity.peerId) localEndpoint()
            else peerDirectory[sourceId] ?: return@forEach
            val token = Crypto.randomBase64(24)
            val assignment = ProtocolBody.TrackSourceAssigned(descriptor, source, destination, token)
            val expiresAt = SystemClock.elapsedRealtime() + TRANSFER_TOKEN_LIFETIME_MS

            if (sourceId == identity.peerId) {
                transferManager?.authorize(trackId, destination, token, expiresAt)
                deliverTransferAssignment(assignment)
            } else {
                pendingTransferAssignments[token] = assignment
                send(sourceId, assignment)
                scope.launch {
                    delay(SOURCE_AUTHORIZATION_TIMEOUT_MS)
                    val expired = pendingTransferAssignments.remove(token) ?: return@launch
                    waitingForSource.computeIfAbsent(expired.track.trackId) { ConcurrentHashMap.newKeySet() }
                        .add(expired.destinationPeerId)
                    assignWaiting(expired.track.trackId)
                }
            }
        }
    }

    private suspend fun deliverTransferAssignment(assignment: ProtocolBody.TrackSourceAssigned) {
        waitingForSource[assignment.track.trackId]?.remove(assignment.destinationPeerId)
        if (assignment.destinationPeerId == identity.peerId) {
            onTrackSourceAssigned(assignment)
        } else {
            send(assignment.destinationPeerId, assignment)
        }
    }

    private suspend fun onTrackSourceAuthorized(peerId: PeerId, authorized: ProtocolBody.TrackSourceAuthorized) {
        val assignment = pendingTransferAssignments[authorized.authorizationToken] ?: return
        if (assignment.source.peerId != peerId ||
            assignment.track.trackId != authorized.trackId ||
            assignment.destinationPeerId != authorized.destinationPeerId
        ) return
        if (!pendingTransferAssignments.remove(authorized.authorizationToken, assignment)) return
        deliverTransferAssignment(assignment)
    }

    private fun onTrackSourceAssigned(assignment: ProtocolBody.TrackSourceAssigned) {
        when (identity.peerId) {
            assignment.source.peerId -> {
                transferManager?.authorize(
                    assignment.track.trackId,
                    assignment.destinationPeerId,
                    assignment.authorizationToken,
                    SystemClock.elapsedRealtime() + TRANSFER_TOKEN_LIFETIME_MS,
                )
                scope.launch {
                    sendToCoordinator(
                        ProtocolBody.TrackSourceAuthorized(
                            assignment.track.trackId,
                            assignment.destinationPeerId,
                            assignment.authorizationToken,
                        )
                    )
                }
            }

            assignment.destinationPeerId -> transferManager?.download(
                roomId = container.roomStore.state.value.snapshot?.roomId ?: return,
                track = assignment.track,
                source = assignment.source,
                authorizationToken = assignment.authorizationToken,
            )
        }
    }

    private suspend fun onTrackFailed(peerId: PeerId, failure: ProtocolBody.TrackFailed) {
        availability[failure.trackId]?.remove(peerId)
        val sourcePeerId = failure.sourcePeerId
        if (sourcePeerId != null) {
            val key = "${failure.trackId.value}:${sourcePeerId.value}:$peerId"
            val attempts = transferFailureCounts.merge(key, 1, Int::plus) ?: 1
            // Retry a transient socket failure once. Only stop advertising a source after repeated
            // failure for the same source/destination pair, otherwise one Wi-Fi hiccup can stall
            // the entire room permanently.
            if (attempts >= MAX_SOURCE_FAILURES) availability[failure.trackId]?.remove(sourcePeerId)
        }
        waitingForSource.computeIfAbsent(failure.trackId) { ConcurrentHashMap.newKeySet() }.add(peerId)
        reevaluatePreparation(failure.trackId)
        scope.launch {
            delay(TRANSFER_RETRY_DELAY_MS)
            assignWaiting(failure.trackId)
        }
    }

    private fun canApplyScheduledCommand(): Boolean =
        roleEngine().canApplyScheduledCommand(clockSync.estimate(clock.nowNs()))

    private suspend fun onLocalTrackReady(descriptor: TrackDescriptor) {
        announcedTrackIds.add(descriptor.trackId)
        // Transfer completion is the authoritative point at which the immutable audio file is ready.
        // Remove any transient/negative artwork result created while the file was unavailable.
        container.artworkStore.invalidate(descriptor.trackId)
        refreshPlayerQueue(engine?.snapshot() ?: return)
        if (isCoordinator()) onTrackHave(identity.peerId, descriptor.trackId)
        else sendToCoordinator(ProtocolBody.TrackReady(descriptor.trackId))
    }

    private suspend fun reconcileSnapshotQueue(snapshot: RoomSnapshot) {
        player.setRepeatCurrentItem(snapshot.repeatMode == RepeatMode.ONE)
        val local = player.state.value
        val canonicalItem = snapshot.playback.queueItemId
        // A full snapshot is commonly received after reconnect. If this phone is still playing a
        // different item, keep that timeline until clock synchronization supplies an authoritative
        // PlaybackStateSync. This avoids both early future skips and abrupt unsynchronized jumps.
        if (local.queueItemId != null && canonicalItem != null && local.queueItemId != canonicalItem) return
        refreshPlayerQueue(
            snapshot = snapshot,
            preferredCurrentQueueItemId = local.queueItemId,
            preferredPositionMs = local.positionMs,
        )
    }

    private suspend fun refreshPlayerQueue(
        snapshot: RoomSnapshot,
        preferredCurrentQueueItemId: QueueItemId? = null,
        preferredPositionMs: Long? = null,
    ) {
        val windowQueue = PlaybackQueuePolicy.playerWindow(
            snapshot = snapshot,
            historyCount = PLAYER_HISTORY_ITEMS,
            upcomingCount = maxOf(snapshot.options.preloadCount + 2, PLAYER_UPCOMING_ITEMS),
        )
        val readable = withContext(Dispatchers.IO) {
            windowQueue.associate { item ->
                val file = suspendResult { container.trackRepository.requireReadableFile(item.track.trackId) }
                    .onFailure { error -> log.w(TAG, "Could not load ${item.track.trackId.value.take(8)}", error) }
                    .getOrNull()
                item.track.trackId to file
            }
        }
        val allowedByRoom = PlaybackQueuePolicy.playableItems(
            snapshot = snapshot.copy(queue = windowQueue),
            readableTrackIds = readable.filterValues { it != null }.keys,
        )
        val artworkQueueItemId = preferredCurrentQueueItemId
            ?: snapshot.playback.queueItemId
            ?: allowedByRoom.firstOrNull()?.queueItemId
        val artworkItem = allowedByRoom.firstOrNull { it.queueItemId == artworkQueueItemId }
        var artworkRetryDelayMs: Long? = null
        val artworkFile = artworkItem?.let { item ->
            readable[item.track.trackId]?.let { audioFile ->
                val file = suspendResult { container.artworkStore.fileFor(item.track.trackId, audioFile) }
                    .onFailure { error -> log.w(TAG, "Could not load artwork", error) }
                    .getOrNull()
                if (file == null) {
                    artworkRetryDelayMs = container.artworkStore.transientRetryDelayMs(item.track.trackId)
                }
                file
            }
        }
        val playable = allowedByRoom.mapNotNull { item ->
            readable[item.track.trackId]?.let { audioFile ->
                LocalPlayableItem(
                    queueItemId = item.queueItemId,
                    track = item.track,
                    file = audioFile,
                    artworkFile = artworkFile.takeIf { item.queueItemId == artworkQueueItemId },
                )
            }
        }
        val current = preferredCurrentQueueItemId?.takeIf { id -> playable.any { it.queueItemId == id } }
            ?: snapshot.playback.queueItemId?.takeIf { id -> playable.any { it.queueItemId == id } }
            ?: playable.firstOrNull()?.queueItemId
        val currentPosition = when {
            current == preferredCurrentQueueItemId && preferredPositionMs != null -> preferredPositionMs
            player.state.value.queueItemId == current -> player.state.value.positionMs
            else -> snapshot.playback.projectedPositionMs(clockSync.coordinatorNowNs())
        }
        player.setQueue(playable, current, currentPosition)
        val artworkTrackId = artworkItem?.track?.trackId
        val retryDelayMs = artworkRetryDelayMs
        if (artworkTrackId != null && retryDelayMs != null) {
            scheduleArtworkRetry(artworkTrackId, snapshot.roomId, retryDelayMs)
        } else {
            cancelArtworkRetry()
        }
    }

    private fun scheduleArtworkRetry(trackId: TrackId, roomId: String, delayMs: Long) {
        if (artworkRetryTrackId == trackId && artworkRetryJob?.isActive == true) return
        cancelArtworkRetry()
        artworkRetryTrackId = trackId
        artworkRetryJob = scope.launch {
            delay(delayMs.coerceAtLeast(0L) + ARTWORK_RETRY_SETTLE_MS)
            artworkRetryJob = null
            artworkRetryTrackId = null
            val snapshot = engine?.snapshot()?.takeIf { it.roomId == roomId } ?: return@launch
            val local = player.state.value
            val currentTrackId = snapshot.queue
                .firstOrNull { it.queueItemId == local.queueItemId }
                ?.track
                ?.trackId
            if (currentTrackId == trackId) {
                refreshPlayerQueue(snapshot, local.queueItemId, local.positionMs)
            }
        }
    }

    private fun cancelArtworkRetry() {
        artworkRetryJob?.cancel()
        artworkRetryJob = null
        artworkRetryTrackId = null
    }

    private suspend fun markTrackPlayed(snapshot: RoomSnapshot, queueItemId: QueueItemId) {
        snapshot.queue.firstOrNull { it.queueItemId == queueItemId }?.track?.trackId?.let { trackId ->
            suspendResult { container.trackRepository.markPlayed(trackId) }
                .onFailure { error -> log.w(TAG, "Could not update recent music", error) }
        }
    }

    /** Coalesces queue/preparation bursts so adding a large playlist does one Media3 rebuild, not
     * one database/file pass per canonical mutation. Transport-timestamp reconciliation uses the
     * separate [scheduleQueueRefresh] path and is never delayed by this debounce. */
    private fun requestTimelineRefresh(fallbackSnapshot: RoomSnapshot) {
        timelineRefreshJob?.cancel()
        timelineRefreshJob = scope.launch {
            delay(TIMELINE_REFRESH_DEBOUNCE_MS)
            val snapshot = engine?.snapshot()
                ?.takeIf { it.roomId == fallbackSnapshot.roomId }
                ?: fallbackSnapshot
            refreshPlayerQueue(snapshot)
            prepareWindow(snapshot)
        }
    }

    private fun scheduleQueueRefresh(executeAtCoordinatorNs: Long) {
        queueRefreshJob?.cancel()
        queueRefreshJob = scope.launch {
            while (isActive) {
                if (!isCoordinator() && !clockSync.synchronized) {
                    delay(CLOCK_MAPPING_RECHECK_INTERVAL_MS)
                    continue
                }
                val targetLocalNs = if (isCoordinator()) {
                    executeAtCoordinatorNs
                } else {
                    clockSync.toLocalTime(executeAtCoordinatorNs)
                }
                val remainingNs = targetLocalNs - clock.nowNs()
                if (remainingNs <= 0L) break
                delay(
                    minOf(
                        CLOCK_MAPPING_RECHECK_INTERVAL_MS,
                        ((remainingNs + 999_999L) / 1_000_000L).coerceAtLeast(1L),
                    )
                )
            }
            delay(TRANSITION_RECONCILE_DELAY_MS)
            engine?.snapshot()?.let { refreshPlayerQueue(it) }
        }
    }

    private suspend fun prepareWindow(snapshot: RoomSnapshot) {
        if (snapshot.queue.isEmpty()) return
        val currentIndex = snapshot.queue.indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
            .let { if (it < 0) 0 else it }
        snapshot.queue.drop(currentIndex).take(snapshot.options.preloadCount + 1).forEach { item ->
            container.trackRepository.touchTemporary(item.track.trackId)
            announceLocalAvailability(item.track)
        }
    }

    private suspend fun reevaluatePreparation(trackId: TrackId) {
        if (!isCoordinator()) return
        val snapshot = engine?.snapshot() ?: return
        val activePeers = snapshot.members.filter { it.connected }.mapTo(mutableSetOf()) { it.peerId }
        val readyPeers = availability[trackId].orEmpty()
        val shouldPrepare = activePeers.isNotEmpty() &&
            readyPeers.containsAll(activePeers) &&
            clockReadyPeers.containsAll(activePeers)
        log.i(
            TAG,
            "Preparation track=${trackId.value.take(8)} ready=${readyPeers.size}/${activePeers.size} " +
                "clocks=${clockReadyPeers.count { it in activePeers }}/${activePeers.size} prepared=$shouldPrepare",
        )
        snapshot.queue.filter { it.track.trackId == trackId }.forEach { item ->
            val prepared = item.queueItemId in (engine?.snapshot()?.preparedQueueItemIds ?: emptySet())
            if (prepared != shouldPrepare) emitCanonical(
                ProtocolBody.QueueItemPreparation(
                    item.queueItemId,
                    shouldPrepare
                )
            )
            val coordinatorCanContinue = !snapshot.options.waitAtTrackBoundary && identity.peerId in readyPeers
            if ((shouldPrepare || coordinatorCanContinue) && pendingAutoResumeQueueItemId == item.queueItemId) {
                val latest = engine?.snapshot() ?: return@forEach
                if (latest.playback.queueItemId == item.queueItemId && !latest.playback.isPlaying) {
                    pendingAutoResumeQueueItemId = null
                    emitCanonical(
                        ProtocolBody.CurrentItemChanged(
                            queueItemId = item.queueItemId,
                            positionMs = 0,
                            executeAtCoordinatorNs = clock.nowNs() + RoomReducer.DEFAULT_COMMAND_LEAD_NS,
                            resumePlayback = true,
                        )
                    )
                }
            }
        }
        resumePendingPlayIfReady()
    }

    private suspend fun resumePendingPlayIfReady() {
        val requester = pendingPlayRequestedBy ?: return
        val snapshot = engine?.snapshot() ?: return
        if (requester != identity.peerId && snapshot.members.none { it.peerId == requester && it.connected }) {
            pendingPlayRequestedBy = null
            return
        }
        val current = snapshot.playback.queueItemId
            ?.let { id -> snapshot.queue.firstOrNull { it.queueItemId == id } }
            ?: snapshot.queue.firstOrNull()
            ?: return
        if (snapshot.options.waitAtTrackBoundary && current.queueItemId !in snapshot.preparedQueueItemIds) return
        pendingPlayRequestedBy = null
        log.i(TAG, "Preparation complete; retrying deferred Play item=${current.queueItemId.value.take(8)}")
        applyCoordinatorCommand(UserCommand.Play(requestedBy = requester))
    }

    private suspend fun reevaluateAllPreparation() {
        val snapshot = engine?.snapshot() ?: return
        snapshot.queue.map { it.track.trackId }.distinct().forEach { reevaluatePreparation(it) }
    }

    private suspend fun emitCanonical(body: ProtocolBody) {
        if (roomEvents.isCurrentContext()) {
            applyCanonicalMutation(body)
            return
        }
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(RoomEvent.CanonicalMutationRequested(body, completion))
        completion.await()
    }

    private suspend fun applyCanonicalMutation(body: ProtocolBody) {
        if (!isCoordinator()) return
        val snapshot = engine?.snapshot() ?: return
        val sequence = snapshot.sequence + 1
        val updated = engine?.applyValidated(sequence, body, ::snapshotFitsProtocol) ?: run {
            log.w(TAG, "Rejected local mutation ${body::class.simpleName}: invalid resulting snapshot")
            return
        }
        updateSnapshot(updated)
        broadcastCanonical(sequence, body)
        if (!canonicalSideEffects.trySend(body to updated).isSuccess) {
            throw IllegalStateException("Canonical side-effect queue is full")
        }
    }

    private suspend fun recordNaturalTrackTransition(queueItemId: QueueItemId, positionMs: Long) {
        val snapshot = engine?.snapshot() ?: return
        if (!snapshot.playback.isPlaying || snapshot.playback.queueItemId == queueItemId) return
        if (snapshot.queue.none { it.queueItemId == queueItemId }) return
        val startTime = clock.nowNs() - positionMs.coerceAtLeast(0) * 1_000_000L
        emitCanonical(ProtocolBody.CurrentItemChanged(queueItemId, 0, startTime, true))
    }

    private suspend fun recordNaturalRepeatTransition(queueItemId: QueueItemId, positionMs: Long) {
        val mutation = PlaybackQueuePolicy.planRepeatTransition(
            snapshot = engine?.snapshot() ?: return,
            repeatedQueueItemId = queueItemId,
            positionMs = positionMs,
            coordinatorNowNs = clock.nowNs(),
        ) ?: return
        emitCanonical(mutation)
    }

    private suspend fun recordNaturalPlaybackEnded(queueItemId: QueueItemId, positionMs: Long, durationMs: Long) {
        val plan = PlaybackQueuePolicy.planNaturalEnd(
            snapshot = engine?.snapshot() ?: return,
            endedQueueItemId = queueItemId,
            positionMs = positionMs,
            durationMs = durationMs,
            coordinatorNowNs = clock.nowNs(),
        ) ?: return
        pendingAutoResumeQueueItemId = plan.waitForQueueItemId
        emitCanonical(plan.mutation)
    }

    private suspend fun applyPlaybackSync(canonical: CanonicalPlaybackState) {
        if (isCoordinator()) return
        latestPlaybackReference = canonical
        if (!clockSync.synchronized) return
        val queueItem = canonical.queueItemId ?: return
        val coordinatorNow = clockSync.coordinatorNowNs()
        val scheduledForFuture = canonical.coordinatorTimestampNs > coordinatorNow + FUTURE_COMMAND_TOLERANCE_NS
        if (scheduledForFuture) {
            val local = player.state.value
            if (local.queueItemId == null) refreshPlayerQueue(engine?.snapshot() ?: return)
            scheduler.scheduleSeek(
                queueItemId = queueItem,
                positionMs = canonical.positionAtTimestampMs,
                resume = canonical.isPlaying,
                executeAtCoordinatorNs = canonical.coordinatorTimestampNs,
            )
            scheduleQueueRefresh(canonical.coordinatorTimestampNs)
            return
        }
        if (player.state.value.queueItemId != queueItem) {
            refreshPlayerQueue(engine?.snapshot() ?: return)
            if (player.state.value.queueItemId != queueItem) return
        }

        // State-sync transport reconciliation is a recovery path. Fine-grained drift correction is
        // owned exclusively by PlaybackSyncController in the 500 ms local loop.
        val localState = player.state.value
        if (canonical.isPlaying && !localState.playWhenReady) {
            player.play()
        } else if (!canonical.isPlaying && localState.playWhenReady) {
            player.pause()
        }
    }

    private suspend fun runPlaybackSynchronizationTick() {
        val snapshot = engine?.snapshot() ?: return
        val coordinator = isCoordinator()
        val canonical = if (coordinator) {
            snapshot.playback
        } else {
            latestPlaybackReference ?: snapshot.playback
        }
        val sample = player.samplePlayback()
        val previousRoute = lastObservedOutputRoute
        lastObservedOutputRoute = sample.outputRoute
        if (previousRoute != null && previousRoute != sample.outputRoute) {
            resetSynchronizationAfterDiscontinuity("audio_route_change")
            return
        }
        val clockEstimate: ClockEstimate
        val sampleCoordinatorNs: Long
        if (coordinator) {
            sampleCoordinatorNs = sample.sampledAtLocalNs
            clockEstimate = ClockEstimate(
                offsetNs = 0L,
                rate = 1.0,
                rttNs = 0L,
                rttVariationNs = 0L,
                uncertaintyNs = 0L,
                sampledAtLocalNs = sample.sampledAtLocalNs,
                lastGoodSampleLocalNs = sample.sampledAtLocalNs,
                sampleAgeNs = 0L,
                acceptedSampleCount = Int.MAX_VALUE,
                rejectedSampleCount = 0,
                state = ClockSyncState.LOCKED,
            )
        } else {
            val conversion = clockSync.toCoordinatorTimeWithUncertainty(sample.sampledAtLocalNs)
            sampleCoordinatorNs = conversion.timeNs
            clockEstimate = clockSync.estimate(sample.sampledAtLocalNs)
        }

        val futureCommand = canonical.coordinatorTimestampNs >
            sampleCoordinatorNs + FUTURE_COMMAND_TOLERANCE_NS
        val decision = if (futureCommand) {
            playbackSync.holdForFutureCommand()
        } else {
            playbackSync.evaluate(
                PlaybackSyncInput(
                    canonicalQueueItemId = canonical.queueItemId,
                    expectedPositionMs = canonical.projectedPositionMs(sampleCoordinatorNs),
                    sample = sample,
                    connected = coordinator || coordinatorConnection != null,
                    clockState = clockEstimate.state,
                    clockUncertaintyNs = clockEstimate.uncertaintyNs,
                    coordinatorUsesLocalClock = coordinator,
                    outputLatencyOffsetMs = outputLatencyOffsetsMs[sample.outputRoute] ?: 0L,
                )
            )
        }
        applyPlaybackSyncDecision(decision, sample.playbackSpeed)

        container.roomStore.updatePlayback { state ->
            state.copy(localDriftMs = decision.rawDriftMs)
        }
        recordSynchronizationEvent(
            snapshot = snapshot,
            sampleCoordinatorNs = sampleCoordinatorNs,
            samplePositionMs = sample.positionMs,
            sampleAtLocalNs = sample.sampledAtLocalNs,
            outputRoute = sample.outputRoute,
            buffering = sample.activityState == PlaybackActivityState.BUFFERING,
            canonicalPositionMs = if (futureCommand) null else canonical.projectedPositionMs(sampleCoordinatorNs),
            clockEstimate = clockEstimate,
            decision = decision,
        )

        if (sample.sampledAtLocalNs - lastPlaybackStatusReportNs >= PLAYBACK_STATUS_REPORT_INTERVAL_NS) {
            lastPlaybackStatusReportNs = sample.sampledAtLocalNs
            val report = ProtocolBody.PlaybackStatusReport(
                queueItemId = sample.queueItemId,
                positionMs = sample.positionMs,
                isPlaying = sample.playWhenReady,
                driftMs = decision.rawDriftMs,
            )
            if (coordinator) {
                updateMemberPlayback(identity.peerId, report)
            } else if (coordinatorConnection != null) {
                sendToCoordinator(report)
            }
        }
    }

    private suspend fun resetSynchronizationAfterDiscontinuity(reason: String) {
        if (!isCoordinator()) clockSync.reset()
        playbackSync.reset(preserveLearnedBaseline = false)
        latestPlaybackReference = if (isCoordinator()) engine?.snapshot()?.playback else null
        lastPlaybackReferenceBroadcastNs = 0L
        lastPlaybackStatusReportNs = 0L
        container.roomStore.updatePlayback { it.copy(localDriftMs = null) }
        val actualSpeed = player.state.value.playbackSpeed
        if (abs(actualSpeed - 1f) > PLAYBACK_SPEED_EPSILON) player.setPlaybackSpeed(1f)
        log.i(TAG, "Synchronization reacquisition required reason=$reason")
    }

    private suspend fun applyPlaybackSyncDecision(decision: PlaybackSyncDecision, actualSpeed: Float) {
        when (val action = decision.action) {
            is SyncAction.SetSpeed -> {
                if (abs(actualSpeed - action.speed) > PLAYBACK_SPEED_EPSILON) {
                    player.setPlaybackSpeed(action.speed)
                }
            }
            is SyncAction.Seek -> {
                if (abs(actualSpeed - decision.baselineSpeed) > PLAYBACK_SPEED_EPSILON) {
                    player.setPlaybackSpeed(decision.baselineSpeed)
                }
                player.seekTo(action.positionMs)
            }
            is SyncAction.Hold -> {
                if (abs(actualSpeed - action.baselineSpeed) > PLAYBACK_SPEED_EPSILON) {
                    player.setPlaybackSpeed(action.baselineSpeed)
                }
            }
        }
    }

    private fun recordSynchronizationEvent(
        snapshot: RoomSnapshot,
        sampleCoordinatorNs: Long,
        samplePositionMs: Long,
        sampleAtLocalNs: Long,
        outputRoute: AudioOutputRoute,
        buffering: Boolean,
        canonicalPositionMs: Long?,
        clockEstimate: ClockEstimate,
        decision: PlaybackSyncDecision,
    ) {
        val actionName = when (decision.action) {
            is SyncAction.SetSpeed -> "SET_SPEED"
            is SyncAction.Seek -> "SEEK"
            is SyncAction.Hold -> "HOLD"
        }
        syncDiagnostics.record(
            SynchronizationEvent(
                timestampLocalNs = sampleAtLocalNs,
                timestampCoordinatorNs = sampleCoordinatorNs,
                deviceId = identity.peerId.value.take(12),
                deviceModel = Build.MODEL.orEmpty().take(80),
                androidVersion = Build.VERSION.SDK_INT,
                outputRoute = outputRoute.name,
                roomIdHash = snapshot.roomId.hashCode().toUInt().toString(16),
                coordinatorTerm = snapshot.term.number,
                queueItemId = snapshot.playback.queueItemId?.value,
                canonicalPositionMs = canonicalPositionMs,
                sampledPlayerPositionMs = samplePositionMs,
                sampleAgeMs = ((clock.nowNs() - sampleAtLocalNs).coerceAtLeast(0L) / 1_000_000L),
                rawDriftMs = decision.rawDriftMs,
                filteredDriftMs = decision.filteredDriftMs,
                selectedSpeed = decision.selectedSpeed,
                learnedBaselineSpeed = decision.baselineSpeed,
                clockOffsetNs = clockEstimate.offsetNs,
                clockRate = clockEstimate.rate,
                clockRttMs = clockEstimate.rttNs.takeIf { it != Long.MAX_VALUE }?.div(1_000_000.0),
                clockUncertaintyMs = clockEstimate.uncertaintyNs
                    .takeIf { it != Long.MAX_VALUE }
                    ?.div(1_000_000.0),
                clockState = clockEstimate.state.name,
                playbackSyncState = decision.state.name,
                action = actionName,
                actionReason = decision.reason,
                hardSeekCount = decision.hardSeekCount,
                buffering = buffering,
            )
        )
    }

    private suspend fun updateMemberPlayback(peerId: PeerId, report: ProtocolBody.PlaybackStatusReport) {
        val snapshot = engine?.snapshot() ?: return
        if (snapshot.members.none { it.peerId == peerId }) return
        val status = ProtocolBody.MemberPlaybackStatus(
            peerId = peerId,
            queueItemId = report.queueItemId,
            positionMs = report.positionMs,
            isPlaying = report.isPlaying,
            driftMs = report.driftMs,
        )
        applyEphemeralMemberPlayback(status)
        broadcast(status, except = peerId)
    }

    private fun applyEphemeralMemberPlayback(status: ProtocolBody.MemberPlaybackStatus) {
        container.roomStore.updatePlayback { state ->
            state.copy(
                memberPlayback = state.memberPlayback + (
                    status.peerId to com.darius.unison.model.MemberPlaybackTelemetry(
                        positionMs = status.positionMs,
                        driftMs = status.driftMs,
                    )
                ),
            )
        }
    }

    private fun startSessionJobs() {
        wifiLocks.acquireWifi()
        heartbeatJob?.cancel()
        clockSyncJob?.cancel()
        syncJob?.cancel()
        retentionRefreshJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                roomEvents.submit(RoomEvent.HeartbeatTick)
            }
        }
        // Converge the guest clock before the first user command, then keep it fresh.
        clockSyncJob = scope.launch {
            while (isActive) {
                roomEvents.submit(RoomEvent.ClockSyncTick)
                delay(if (clockSync.synchronized) CLOCK_SYNC_STEADY_INTERVAL_MS else CLOCK_SYNC_WARMUP_INTERVAL_MS)
            }
        }
        syncJob = scope.launch {
            while (isActive) {
                delay(playbackSync.config.tickIntervalMs)
                roomEvents.submit(RoomEvent.PlaybackSyncTick)
            }
        }
        retentionRefreshJob = scope.launch {
            while (isActive) {
                delay(TEMPORARY_RETENTION_REFRESH_INTERVAL_MS)
                refreshTemporaryRetention()
            }
        }
    }

    private suspend fun processHeartbeatTick() {
        val snapshot = engine?.snapshot() ?: return
        if (isCoordinator()) {
            broadcast(ProtocolBody.Heartbeat(snapshot.sequence))
            val cutoff = SystemClock.elapsedRealtime() - PEER_TIMEOUT_MS
            connections.keys.filter { (lastSeenElapsedMs[it] ?: 0L) < cutoff }
                .forEach { connections[it]?.close() }
        } else {
            sendToCoordinator(ProtocolBody.Heartbeat(snapshot.sequence))
            val coordinator = coordinatorPeerId
            if (coordinator != null && (lastSeenElapsedMs[coordinator]
                    ?: SystemClock.elapsedRealtime()) < SystemClock.elapsedRealtime() - PEER_TIMEOUT_MS
            ) {
                coordinatorConnection?.close(IllegalStateException("Coordinator heartbeat timed out"))
            }
        }
    }

    private suspend fun processClockSyncTick() {
        if (!isCoordinator() && coordinatorConnection != null) {
            val ping = clockSync.createPing()
            sendToCoordinator(ProtocolBody.ClockPing(ping.pingId, ping.localSendNs))
        }
    }

    private suspend fun processPlaybackSyncTick() {
        val now = clock.nowNs()
        val previousTick = lastPlaybackSyncTickLocalNs
        lastPlaybackSyncTickLocalNs = now
        if (previousTick > 0L && now - previousTick > LIFECYCLE_DISCONTINUITY_NS) {
            resetSynchronizationAfterDiscontinuity("scheduler_delay")
            return
        }
        if (isCoordinator() && now - lastPlaybackReferenceBroadcastNs >=
            playbackSync.config.referenceIntervalMs * 1_000_000L
        ) {
            val snapshot = engine?.snapshot()
            if (snapshot != null) {
                lastPlaybackReferenceBroadcastNs = now
                broadcast(ProtocolBody.PlaybackStateSync(snapshot.playback.forStateSync(now)))
            }
        }
        runPlaybackSynchronizationTick()
    }

    private suspend fun refreshTemporaryRetention() {
        val snapshot = engine?.snapshot() ?: return
        val currentIndex = snapshot.queue.indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
            .let { if (it < 0) 0 else it }
        snapshot.queue.drop(currentIndex).take(snapshot.options.preloadCount + 1).forEach { item ->
            container.trackRepository.touchTemporary(item.track.trackId)
        }
    }

    override suspend fun admitControl(
        hello: HandshakeMessage.ClientHello,
        remoteAddress: String,
    ): PeerServer.ControlAdmission = admission.admit(hello, remoteAddress)

    override suspend fun onControlConnected(connection: ControlConnection) {
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(RoomEvent.ControlConnected(connection, completion))
        completion.await()
    }

    private suspend fun processControlConnected(connection: ControlConnection) {
        connections.put(connection.peerId, connection)?.close()
        lastSeenElapsedMs[connection.peerId] = SystemClock.elapsedRealtime()
        if (!isCoordinator()) return
        peerDirectory[connection.peerId] = connection.endpoint
        val member = MemberSnapshot(
            connection.peerId,
            connection.endpoint.displayName,
            connection.endpoint,
        )
        val snapshot = engine?.snapshot() ?: return
        val sequence = snapshot.sequence + 1
        val joined = ProtocolBody.PeerJoined(member)
        val updated = engine?.applyValidated(sequence, joined, ::snapshotFitsProtocol) ?: run {
            peerDirectory.remove(connection.peerId)
            connection.close(IllegalStateException("Room state capacity exceeded"))
            return
        }
        updateSnapshot(updated)
        val joinAccepted = envelope(ProtocolBody.JoinAccepted(updated, peerDirectory.values.toList()), sequence = null)
        if (!connection.trySend(joinAccepted)) {
            connection.close(IllegalStateException("Guaranteed control queue is full"))
            return
        }
        broadcastCanonical(sequence, joined, except = connection.peerId)
        reevaluateAllPreparation()
        broadcast(ProtocolBody.PeerDirectory(peerDirectory.values.toList()))
        updated.queue.take(updated.options.preloadCount + 1).forEach { track ->
            send(connection.peerId, ProtocolBody.TrackDescriptorMessage(track.track))
        }
    }

    override suspend fun onFileConnection(socket: Socket, hello: HandshakeMessage.ClientHello) {
        transferManager?.handleIncomingFileSocket(socket, hello) ?: socket.close()
    }

    private suspend fun processControlClosed(connection: ControlConnection, cause: Throwable?) {
        val peerId = connection.peerId
        // A reconnect may replace an older socket for the same peer. The older socket's delayed
        // close callback must never remove or mark the new connection disconnected.
        val wasCurrent = connections.remove(peerId, connection)
        if (!wasCurrent && coordinatorConnection !== connection) return
        if (coordinatorPeerId == peerId && !isCoordinator()) {
            if (coordinatorConnection === connection) coordinatorConnection = null
            envelopeReplayProtector.resetPeer(peerId)
            // Stop all guest correction and scheduled execution immediately. The previous affine
            // clock mapping and playback-reference stream are no longer trustworthy after a
            // coordinator socket closes; both will reacquire after reconnect or election.
            clockSync.reset()
            playbackSync.reset(preserveLearnedBaseline = true)
            latestPlaybackReference = null
            if (engine == null) {
                // There is no canonical state to recover from until JoinAccepted arrives. Treat an
                // initial socket failure as a failed join instead of entering a dead RECONNECTING state.
                joinTimeoutJob?.cancel(); joinTimeoutJob = null
                connections.remove(peerId, connection)
                coordinatorPeerId = null
                roomSecret = null
                roomPin = null
                setFailure(userFacingJoinFailure(cause ?: IllegalStateException("Connection closed before joining")))
                return
            }
            container.roomStore.update {
                it.copy(
                    lifecycle = RoomLifecycleState.RECONNECTING,
                    status = UserFacingStatus.RECONNECTING,
                    statusMessage = "Reconnecting…"
                )
            }
            recoveryJob?.cancel()
            recoveryJob = scope.launch { attemptReconnectThenRecover(peerId) }
            return
        }
        if (isCoordinator()) {
            clockReadyPeers.remove(peerId)
            envelopeReplayProtector.resetPeer(peerId)
            val snapshot = engine?.snapshot()
            val member = snapshot?.members?.firstOrNull { it.peerId == peerId }
            if (snapshot != null && member != null) {
                val sequence = snapshot.sequence + 1
                val body = ProtocolBody.PeerUpdated(member.copy(connected = false))
                val updated = engine?.applyValidated(sequence, body, ::snapshotFitsProtocol)
                if (updated != null) {
                    updateSnapshot(updated)
                    broadcastCanonical(sequence, body)
                }
            }
            reevaluateAllPreparation()
        }
        log.i(TAG, "Peer disconnected ${peerId.value.take(8)} ${cause?.message.orEmpty()}")
    }

    private suspend fun attemptReconnectThenRecover(lostCoordinator: PeerId) {
        val snapshot = engine?.snapshot() ?: return
        val endpoint = peerDirectory[lostCoordinator]
        val secret = roomSecret
        if (endpoint != null && secret != null) {
            repeat(3) { attempt ->
                delay(1_200L + attempt * 600L)
                val result = suspendResult {
                    controlClient.reconnectWithRoomSecret(
                        identity = identity,
                        roomId = snapshot.roomId,
                        host = endpoint.hostAddress,
                        port = endpoint.port,
                        listeningPort = server.port,
                        roomSecret = secret,
                        appVersion = BuildConfig.VERSION_NAME,
                        onEnvelope = ::enqueueEnvelope,
                        onClosed = ::enqueueControlClosed,
                    )
                }.getOrNull()
                if (result != null) {
                    coordinatorPeerId = result.coordinatorPeerId
                    coordinatorConnection = result.connection
                    roomSecret = result.roomSecret
                    connections[result.coordinatorPeerId] = result.connection
                    result.connection.start()
                    lastSeenElapsedMs[result.coordinatorPeerId] = SystemClock.elapsedRealtime()
                    val cached = snapshot.queue.map { it.track.trackId }
                        .filter { container.trackRepository.hasVerifiedSource(it) }
                    val request = envelope(ProtocolBody.RejoinRequest(snapshot.sequence, cached, server.port))
                    if (!result.connection.trySend(request)) {
                        result.connection.close(IllegalStateException("Guaranteed control queue is full"))
                    } else {
                        return
                    }
                }
            }
        }
        beginCoordinatorRecovery()
    }

    /** Best-effort deterministic election. Everyone has equal rights; lowest peer ID is only a tie-breaker. */
    private suspend fun beginCoordinatorRecovery() {
        delay(ELECTION_DELAY_MS)
        val snapshot = engine?.snapshot() ?: return
        val lostCoordinator = coordinatorPeerId
        val connectedCandidates =
            snapshot.members.filter { (it.connected || it.peerId == identity.peerId) && it.peerId != lostCoordinator }
                .map { it.peerId }
        val winner = (connectedCandidates + identity.peerId).distinct().minByOrNull { it.value } ?: identity.peerId
        if (winner == identity.peerId) {
            val local = localEndpoint()
            val promoted = snapshot.copy(
                term = CoordinatorTerm(snapshot.term.number + 1, identity.peerId),
                // Old guest-to-coordinator sockets are gone. Mark every remote peer disconnected
                // until it actually reconnects to the elected coordinator; otherwise preparation
                // waits forever for peers that are not connected to this coordinator yet.
                members = snapshot.members.map {
                    if (it.peerId == identity.peerId) it.copy(connected = true, endpoint = local)
                    else it.copy(connected = false)
                },
            )
            if (!snapshotFitsProtocol(promoted)) {
                setFailure("Recovered room state was invalid")
                return
            }
            val replacementPin = Crypto.randomSixDigitPin()
            roomPin = replacementPin
            container.roomStore.update { it.copy(localRoomPin = replacementPin) }
            coordinatorPeerId = identity.peerId
            clockReadyPeers.clear()
            clockReadyPeers.add(identity.peerId)
            engine?.replace(promoted)
            peerDirectory[identity.peerId] = local
            discovery.advertise(
                promoted.roomId,
                promoted.roomName,
                server.port,
                promoted.term.number,
                onError = ::setError
            )
            updateSnapshot(promoted, RoomLifecycleState.CONNECTED, "Connection restored")
            // Existing guest-to-old-coordinator sockets are gone. NSD/direct reconnect brings peers back.
        } else {
            val endpoint = peerDirectory[winner]
            val secret = roomSecret
            if (endpoint == null || secret == null) {
                setFailure("Room connection was lost")
                return
            }
            var restored = false
            repeat(5) { attempt ->
                if (restored) return@repeat
                delay(350L + attempt * 450L)
                val connected = suspendResult {
                    controlClient.reconnectWithRoomSecret(
                        identity, snapshot.roomId, endpoint.hostAddress, endpoint.port, server.port,
                        secret, BuildConfig.VERSION_NAME, ::enqueueEnvelope, ::enqueueControlClosed,
                    )
                }.getOrNull()
                if (connected != null) {
                    coordinatorPeerId = winner
                    coordinatorConnection = connected.connection
                    roomSecret = connected.roomSecret
                    connections[winner] = connected.connection
                    connected.connection.start()
                    lastSeenElapsedMs[winner] = SystemClock.elapsedRealtime()
                    restored = true
                }
            }
            if (!restored) setFailure("Could not restore the room")
        }
    }

    private suspend fun leaveRoom() {
        suspendResult { sendToCoordinator(ProtocolBody.LeaveRoom("left")) }
        resetSession(keepDiscovery = false)
        player.pause()
        player.setRepeatCurrentItem(false)
        player.setQueue(emptyList(), null, 0)
        container.roomStore.reset()
    }

    private fun resetSession(keepDiscovery: Boolean) {
        scheduler.cancel()
        heartbeatJob?.cancel(); heartbeatJob = null
        clockSyncJob?.cancel(); clockSyncJob = null
        syncJob?.cancel(); syncJob = null
        retentionRefreshJob?.cancel(); retentionRefreshJob = null
        cancelArtworkRetry()
        queueRefreshJob?.cancel(); queueRefreshJob = null
        timelineRefreshJob?.cancel(); timelineRefreshJob = null
        recoveryJob?.cancel(); recoveryJob = null
        joinTimeoutJob?.cancel(); joinTimeoutJob = null
        transferManager?.cancelAll()
        peers.clearSession { connection -> connection.close() }
        coordinatorConnection = null
        coordinatorPeerId = null
        roomQueueLeases.values.forEach(ManagedFileLease::close)
        roomQueueLeases.clear()
        admission.reset()
        recentCommandIds.clear()
        envelopeReplayProtector.reset()
        lastObservedPlayerItem = null
        lastHandledEndedItem = null
        lastObservedSeekRevision = player.state.value.seekRevision
        lastObservedRepeatTransitionRevision = player.state.value.repeatTransitionRevision
        latestPlaybackReference = null
        lastPlaybackReferenceBroadcastNs = 0L
        lastPlaybackStatusReportNs = 0L
        lastPlaybackSyncTickLocalNs = 0L
        lastObservedOutputRoute = null
        playbackSync.reset(preserveLearnedBaseline = false)
        pendingAutoResumeQueueItemId = null
        pendingPlayRequestedBy = null
        engine = null
        roomSecret = null
        roomPin = null
        container.roomStore.update { it.copy(localRoomPin = null) }
        clockSync.reset()
        discovery.stopAdvertising()
        wifiLocks.releaseWifi()
        if (!keepDiscovery) stopDiscovery()
    }

    private fun isCoordinator(): Boolean =
        ::identity.isInitialized && coordinatorPeerId == identity.peerId && engine != null

    private fun roleEngine(): RoomRoleEngine = if (isCoordinator()) CoordinatorEngine else ParticipantEngine

    private fun ControlConnection.asSendTarget(): RoomSendTarget = RoomSendTarget(
        send = ::trySend,
        close = ::close,
    )

    private suspend fun processCoordinatorLocalBody(body: ProtocolBody) {
        when (body) {
            is ProtocolBody.TrackHave -> onTrackHave(identity.peerId, body.trackId)
            is ProtocolBody.TrackNeed -> onTrackNeed(identity.peerId, body.trackId)
            is ProtocolBody.TrackFailed -> onTrackFailed(identity.peerId, body)
            is ProtocolBody.UserCommandRequest -> applyCoordinatorCommand(body.command)
            else -> Unit
        }
    }

    private suspend fun sendToCoordinator(body: ProtocolBody) = messageRouter.sendToCoordinator(body)

    private suspend fun send(peerId: PeerId, body: ProtocolBody) = messageRouter.send(peerId, body)

    private suspend fun broadcast(body: ProtocolBody, except: PeerId? = null) =
        messageRouter.broadcast(body, except)

    private suspend fun broadcastCanonical(sequence: Long, body: ProtocolBody, except: PeerId? = null) =
        messageRouter.broadcastCanonical(sequence, body, except)

    private suspend fun envelope(body: ProtocolBody, sequence: Long? = null): Envelope {
        val snapshot = engine?.snapshot()
            ?: throw IllegalStateException("Room session is not established")
        return Envelope(
            roomId = snapshot.roomId,
            term = snapshot.term.number,
            coordinatorPeerId = snapshot.term.coordinatorPeerId,
            senderPeerId = identity.peerId,
            sequence = sequence,
            messageId = UUID.randomUUID().toString(),
            sentAtElapsedNs = clock.nowNs(),
            body = body,
        )
    }

    private suspend fun updatePeerEndpoint(peerId: PeerId, announced: PeerEndpoint) {
        if (announced.peerId != peerId || announced.port !in 1..65535) return
        val allowedAddress = NetworkAddressPolicy.parseAllowedIpv4(announced.hostAddress) ?: return
        val normalized = announced.copy(
            displayName = announced.displayName.trim().take(40).ifBlank { "Friend" },
            hostAddress = allowedAddress.hostAddress ?: return,
            lastSeenElapsedMs = SystemClock.elapsedRealtime(),
        )
        peerDirectory[peerId] = normalized
        val member = engine?.snapshot()?.members?.firstOrNull { it.peerId == peerId } ?: return
        if (member.displayName != normalized.displayName || member.endpoint != normalized || !member.connected) {
            emitCanonical(
                ProtocolBody.PeerUpdated(
                    member.copy(
                        displayName = normalized.displayName,
                        endpoint = normalized,
                        connected = true,
                    )
                )
            )
            broadcast(ProtocolBody.PeerDirectory(peerDirectory.values.toList()))
        }
    }

    private suspend fun refreshLocalCoordinatorEndpoint() {
        if (!isCoordinator()) return
        val snapshot = engine?.snapshot() ?: return
        val endpoint = localEndpoint()
        peerDirectory[identity.peerId] = endpoint
        val member = snapshot.members.firstOrNull { it.peerId == identity.peerId }
        if (member != null && (member.displayName != identity.displayName || member.endpoint != endpoint)) {
            emitCanonical(
                ProtocolBody.PeerUpdated(
                    member.copy(
                        displayName = identity.displayName,
                        endpoint = endpoint,
                        connected = true,
                    )
                )
            )
        }
        val current = engine?.snapshot() ?: snapshot
        discovery.advertise(
            current.roomId,
            current.roomName,
            server.port,
            current.term.number,
            onError = ::setError,
        )
        broadcast(ProtocolBody.PeerDirectory(peerDirectory.values.toList()))
    }

    private fun localEndpoint(): PeerEndpoint = PeerEndpoint(
        peerId = identity.peerId,
        displayName = identity.displayName,
        hostAddress = selectedLocalAddress() ?: "127.0.0.1",
        port = server.port,
        appVersion = BuildConfig.VERSION_NAME,
        lastSeenElapsedMs = SystemClock.elapsedRealtime(),
    )

    private fun selectedLocalAddress(): String? = NetworkAddressPolicy
        .bestLocalIpv4(preferHotspot = hotspot.state.value != null)
        ?.hostAddress

    private fun updateSnapshot(
        snapshot: RoomSnapshot,
        lifecycle: RoomLifecycleState = RoomLifecycleState.CONNECTED,
        message: String? = null,
    ) {
        refreshRoomQueueLeases(snapshot)
        latestPlaybackReference = snapshot.playback
        container.roomStore.update {
            it.copy(
                lifecycle = lifecycle,
                snapshot = snapshot,
                isCoordinator = snapshot.term.coordinatorPeerId == identity.peerId,
                status = UserFacingStatus.READY,
                statusMessage = message,
                errorMessage = null,
                roomAddress = selectedLocalAddress(),
                roomPort = server.port,
            )
        }
    }

    private fun refreshRoomQueueLeases(snapshot: RoomSnapshot) {
        val required = snapshot.queue.mapTo(linkedSetOf()) { it.track.trackId }
        roomQueueLeases.keys.filter { it !in required }.forEach { trackId ->
            roomQueueLeases.remove(trackId)?.close()
        }
        required.filterNot(roomQueueLeases::containsKey).forEach { trackId ->
            roomQueueLeases[trackId] = container.fileStore.acquireLease(
                trackId,
                ManagedFileLeaseReason.ROOM_QUEUE,
            )
        }
    }

    private suspend fun <T> suspendResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun userFacingJoinFailure(error: Throwable): String {
        val detail = error.message.orEmpty()
        return when {
            detail.contains("Room authentication failed", ignoreCase = true) ||
                detail.contains("Incorrect room PIN", ignoreCase = true) -> "The room PIN is incorrect"
            detail.contains("too many authentication", ignoreCase = true) ||
                detail.contains("too many PIN", ignoreCase = true) -> "Too many attempts. Try again shortly"
            detail.contains("incompatible", ignoreCase = true) ||
                detail.contains("protocol", ignoreCase = true) -> "This room uses a different Unison version"

            detail.contains("room is full", ignoreCase = true) -> "This room is full"
            detail.contains(IDENTITY_COLLISION_REASON, ignoreCase = true) -> "This phone is already in the room"
            else -> "Could not connect to this room"
        }
    }

    private fun setError(message: String) {
        container.roomStore.update { it.copy(errorMessage = message, statusMessage = null) }
    }

    private suspend fun setFailure(message: String) {
        // A terminal failure must not leave a stale snapshot, sockets, or audio running behind the
        // lobby. Avoid cancelling the coroutine currently reporting recovery failure, then perform
        // the same deterministic teardown as Leave room before publishing the error.
        if (recoveryJob === currentCoroutineContext()[Job]) recoveryJob = null
        resetSession(keepDiscovery = false)
        suspendResult { player.pause() }
        suspendResult { player.setRepeatCurrentItem(false) }
        suspendResult { player.setQueue(emptyList(), null, 0) }
        container.roomStore.reset()
        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.FAILED,
                status = UserFacingStatus.UNAVAILABLE,
                errorMessage = message,
                statusMessage = null,
            )
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        addressMonitorJob?.cancel()
        addressMonitorJob = null
        hotspotStateJob?.cancel(); hotspotStateJob = null
        playerStateJob?.cancel(); playerStateJob = null
        canonicalSideEffectJob?.cancel(); canonicalSideEffectJob = null
        resetSession(keepDiscovery = false)
        canonicalSideEffects.close()
        roomEvents.close()
        syncDiagnostics.close()
        hotspot.stop()
        server.stop()
        wifiLocks.close()
    }

    companion object {
        private const val TAG = "RoomRuntime"
        private const val IDENTITY_COLLISION_REASON = "Cannot join yourself"
        private const val MAX_ROOM_MEMBERS = 8
        private const val ROOM_EVENT_CAPACITY = 256
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val CLOCK_SYNC_WARMUP_INTERVAL_MS = 250L
        private const val CLOCK_SYNC_STEADY_INTERVAL_MS = 1_000L

        private const val PLAYBACK_SPEED_EPSILON = 0.00005f
        private const val PLAYBACK_STATUS_REPORT_INTERVAL_NS = 1_000_000_000L
        private const val LIFECYCLE_DISCONTINUITY_NS = 3_000_000_000L
        private const val ARTWORK_RETRY_SETTLE_MS = 500L
        private const val MANUAL_DISCOVERY_WINDOW_MS = 8_000L
        private const val TEMPORARY_RETENTION_REFRESH_INTERVAL_MS = 30_000L
        private const val TRANSFER_TOKEN_LIFETIME_MS = 60_000L
        private const val TRANSFER_RETRY_DELAY_MS = 1_500L
        private const val SOURCE_AUTHORIZATION_TIMEOUT_MS = 2_500L
        private const val MAX_SOURCE_FAILURES = 2
        private const val MAX_REJOIN_CACHE_IDS = 1_000
        private const val HOTSPOT_INTERFACE_SETTLE_MS = 800L
        private const val LOCAL_ADDRESS_POLL_INTERVAL_MS = 2_000L
        private const val TRANSITION_RECONCILE_DELAY_MS = 80L
        private const val CLOCK_MAPPING_RECHECK_INTERVAL_MS = 25L
        private const val TIMELINE_REFRESH_DEBOUNCE_MS = 60L
        private const val PLAYER_HISTORY_ITEMS = 2
        private const val PLAYER_UPCOMING_ITEMS = 12
        private const val FUTURE_COMMAND_TOLERANCE_NS = 5_000_000L
        private const val ELECTION_DELAY_MS = 3_000L
        private const val PEER_TIMEOUT_MS = 18_000L
        private const val INITIAL_JOIN_TIMEOUT_MS = 12_000L
    }
}
