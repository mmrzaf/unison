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
import com.darius.unison.model.HotspotInfo
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RoomIssue
import com.darius.unison.model.RoomIssueCode
import com.darius.unison.model.RoomIssueSeverity
import com.darius.unison.model.RoomJoinCredential
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomRecoveryAction
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransportAction
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.model.TransportCommandStatus
import com.darius.unison.model.UserCommand
import com.darius.unison.model.UserFacingStatus
import com.darius.unison.model.transportAction
import com.darius.unison.model.transportActionOrNull
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
import com.darius.unison.playback.CanonicalPlaybackDispatcher
import com.darius.unison.playback.LocalPlayableItem
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackFailure
import com.darius.unison.playback.PlaybackIntentReconciliationPolicy
import com.darius.unison.playback.PlaybackSpeedCommandGate
import com.darius.unison.playback.PlayerEventInterpreter
import com.darius.unison.playback.PlayerMutationCoordinator
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlayerState
import com.darius.unison.playback.PlayerStateEventPolicy
import com.darius.unison.playback.ScheduledPlaybackController
import com.darius.unison.protocol.Crypto
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.EnvelopeAcceptance
import com.darius.unison.protocol.EnvelopeReplayProtector
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.HandshakeRejectionCode
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.protocol.ProtocolException
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
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private data class PendingJoin(
        val room: DiscoveredRoom,
        val credential: RoomJoinCredential,
        val generation: Long,
        val attemptsStarted: Int = 0,
        val identityCollisionRetried: Boolean = false,
    )

    private val appContext = context.applicationContext
    private val log = container.diagnostics
    private val clock = AndroidMonotonicClock
    private val clockSync = ClockSyncEngine(clock)
    private val playbackSync = PlaybackSyncController()
    private val playbackSpeedGate = PlaybackSpeedCommandGate()
    private val syncDiagnostics = SynchronizationDiagnostics(scope, log)
    private val wifiLocks = WifiLocks(appContext)
    private val discovery = NsdRoomDiscovery(appContext, wifiLocks, log)
    private val discoveredRoomRegistry = DiscoveredRoomRegistry()
    private val hotspot = LocalHotspotController(appContext, log)
    private val controlClient = ControlClient(scope, log)
    private val server = PeerServer(scope, log, this)
    private val playerMutations = PlayerMutationCoordinator(player)
    private val transportIntents = TransportIntentCoordinator()
    private val scheduler =
        ScheduledPlaybackController(
            player = player,
            mutations = playerMutations,
            clock = clock,
            clockSync = clockSync,
            scope = scope,
            log = log,
            onError = ::onPlaybackFailure,
            onCommandPhase = ::onScheduledCommandPhase,
            usesLocalCoordinatorClock = { isCoordinator() },
        )

    private lateinit var identity: LocalIdentity
    private var engine: RoomEngine? = null
    private var roomSecret: ByteArray? = null
    private var roomPin: String? = null
    private var coordinatorPeerId: PeerId? = null
    private var coordinatorConnection: ControlConnection? = null
    private val peers = PeerRegistry<ControlConnection>()
    private val connections
        get() = peers.connections

    private val peerDirectory
        get() = peers.endpoints

    private val availability
        get() = peers.availability

    private val waitingForSource
        get() = peers.waitingForSource

    private val recentCommandIds = LinkedHashSet<String>()
    private val lastSeenElapsedMs
        get() = peers.lastSeenElapsedMs

    private val announcedTrackIds
        get() = peers.announcedTrackIds

    private val clockReadyPeers
        get() = peers.clockReadyPeers

    private val clockRoundTripNs
        get() = peers.clockRoundTripNs

    private val clockUncertaintyNs
        get() = peers.clockUncertaintyNs

    private val transferFailureCounts
        get() = peers.transferFailureCounts

    private val pendingTransferAssignments
        get() = peers.pendingTransferAssignments

    private val initializationMutex = Mutex()
    private val snapshotValidator = RoomSnapshotValidator(maxMembers = MAX_ROOM_MEMBERS)
    private val envelopeReplayProtector = EnvelopeReplayProtector()
    private val persistence = RoomPersistenceManager(container.database.roomSnapshotDao(), log)
    private val admission =
        ControlAdmissionController(
            snapshot = { engine?.snapshot() },
            isCoordinator = ::isCoordinator,
            localIdentity = { identity },
            roomPin = { roomPin },
            roomSecret = { roomSecret },
            log = log,
            onEnvelope = ::enqueueEnvelope,
            onClosed = ::enqueueControlClosed,
        )
    private val messageRouter =
        RoomMessageRouter(
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
    private val roomEvents =
        SerializedEventLoop<RoomEvent>(
            scope = scope,
            capacity = ROOM_EVENT_CAPACITY,
            handler = ::processRoomEvent,
            onFailure = { event, error ->
                log.e(TAG, "Room event failed type=${event::class.simpleName}", error)
                event.completionOrNull()?.completeExceptionally(error)
            },
            onDropped = { event, cause ->
                event.completionOrNull()?.completeExceptionally(cause)
            },
        )
    private val playbackDispatcher =
        CanonicalPlaybackDispatcher(
            scope = scope,
            applyExact = ::applyExactCanonicalPlayback,
            reconcileLatest = ::reconcileCanonicalPlayback,
            onFailure = { body, error ->
                log.e(
                    TAG,
                    "Canonical playback work failed for ${body?.let { it::class.simpleName } ?: "reconciliation"}",
                    error,
                )
                setIssue(
                    RoomIssue(
                        code = RoomIssueCode.PLAYBACK_ACTION_FAILED,
                        message = "Unison could not prepare this song",
                        recoveryAction = RoomRecoveryAction.RETRY,
                        deduplicationKey = "canonical-playback-work",
                    )
                )
            },
        )

    private var transferManager: TransferManager? = null
    private var hotspotStateJob: Job? = null
    private var playerStateJob: Job? = null
    private var discoveryJob: Job? = null
    private var discoveryGeneration = 0L
    private var heartbeatJob: Job? = null
    private var clockSyncJob: Job? = null
    private var syncJob: Job? = null
    private var retentionRefreshJob: Job? = null
    private var addressMonitorJob: Job? = null
    private var queueRefreshJob: Job? = null
    private var timelineRefreshJob: Job? = null
    private var playerMaintenanceRetryJob: Job? = null
    private var recoveryJob: Job? = null
    private var joinTimeoutJob: Job? = null
    private var joinAttemptJob: Job? = null
    private var pendingJoin: PendingJoin? = null
    private val heartbeatLiveness = HeartbeatLivenessPolicy(HEARTBEAT_INTERVAL_MS, PEER_TIMEOUT_MS)
    private val playerEventInterpreter = PlayerEventInterpreter()
    @Volatile private var latestPlaybackStateSync: CanonicalPlaybackState? = null
    private var lastPlaybackReferenceBroadcastNs = 0L
    private var lastPlaybackStatusReportNs = 0L
    private var lastPlaybackSyncTickLocalNs = 0L
    private var lastClockQualityReportNs = 0L
    private var lastObservedOutputRoute: AudioOutputRoute? = null
    private val outputLatencyOffsetsMs = ConcurrentHashMap<AudioOutputRoute, Long>()
    private val roomQueueLeases = mutableMapOf<TrackId, ManagedFileLease>()
    private var desiredPrefetchTrackIds: Set<TrackId> = emptySet()
    private var pendingAutoResumeQueueItemId: QueueItemId? = null
    private var pendingPlayCommand: UserCommand.Play? = null
    private var pendingPlayQueueItemId: QueueItemId? = null
    private var pendingPlayTimeoutJob: Job? = null
    private val pendingTrackTransitions = PendingTrackTransitionRegistry()
    private var pendingTrackTransitionTimeoutJob: Job? = null
    private var pendingTrackAvailabilityProbeJob: Job? = null
    private val localTransportCommandIds = LinkedHashSet<String>()
    private val completedLocalTransportCommandIds = LinkedHashSet<String>()
    private val transportCommands = TransportCommandTracker()
    private val transportWatchdogJobs = mutableMapOf<String, Job>()
    private var transportStatusClearJob: Job? = null
    private val sessionJobs = SessionJobRegistry(scope)
    private val diagnosticDeviceModel = Build.MODEL.orEmpty().take(80)
    private val diagnosticAndroidVersion = Build.VERSION.SDK_INT
    private val closed = AtomicBoolean(false)

    private suspend fun processRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.AppCommandReceived ->
                when (event.command) {
                    is AppCommand.AddTracks ->
                        beginTrackPreparation(event.command, event.completion)
                    is AppCommand.KeepTrack,
                    is AppCommand.RemoveTemporaryTrack ->
                        beginRepositoryCommand(event.command, event.completion)

                    else -> completeEvent(event.completion) { handleAppCommand(event.command) }
                }

            is RoomEvent.LocalTransportSubmitted ->
                completeEvent(event.completion) {
                    rememberLocalTransport(event.command)
                }

            is RoomEvent.LocalTransportSuperseded ->
                completeEvent(event.completion) {
                    updateLocalTransportPhase(
                        event.command.commandId,
                        TransportCommandPhase.SUPERSEDED,
                        "Replaced by a newer action",
                    )
                }

            is RoomEvent.NetworkEnvelopeReceived ->
                completeEvent(event.completion) {
                    processEnvelope(event.peerId, event.envelope)
                }

            is RoomEvent.ControlConnected ->
                completeEvent(event.completion) {
                    processControlConnected(event.connection)
                }

            is RoomEvent.ControlClosed ->
                completeEvent(event.completion) {
                    processControlClosed(event.connection, event.cause)
                }

            is RoomEvent.InitialJoinConnected -> processInitialJoinConnected(event)
            is RoomEvent.InitialJoinFailed -> processInitialJoinFailed(event)
            is RoomEvent.InitialJoinRetry -> {
                joinAttemptJob = null
                if (sessionJobs.isCurrent(event.generation)) startPendingJoinAttempt()
            }

            is RoomEvent.ReconnectSucceeded -> processReconnectSucceeded(event)
            is RoomEvent.ReconnectExhausted -> {
                if (sessionJobs.isCurrent(event.generation) && engine != null) {
                    recoveryJob = launchSessionJob { beginCoordinatorRecovery() }
                }
            }

            is RoomEvent.CoordinatorCommandReceived ->
                completeEvent(event.completion) {
                    applyCoordinatorCommandInActor(event.command)
                }

            is RoomEvent.CoordinatorTransportSuperseded -> {
                if (sessionJobs.isCurrent(event.generation)) {
                    publishSupersededTransport(event.command)
                }
            }

            is RoomEvent.CanonicalMutationRequested ->
                completeEvent(event.completion) {
                    applyCanonicalMutation(event.body)
                }

            is RoomEvent.TrackAvailabilityObserved ->
                completeEvent(event.completion) {
                    if (event.available) onTrackHaveInActor(event.peerId, event.trackId)
                    else onTrackNeedInActor(event.peerId, event.trackId)
                }

            is RoomEvent.TracksPrepared ->
                completeEvent(event.completion) {
                    applyPreparedTracks(event)
                }

            is RoomEvent.RepositoryCommandCompleted ->
                completeEvent(event.completion) {
                    event.error?.let { throw it }
                    if (!sessionJobs.isCurrent(event.generation)) {
                        throw CancellationException("Room changed during library operation")
                    }
                }

            is RoomEvent.LocalAddressChanged -> processLocalAddressChanged(event.address)
            is RoomEvent.HotspotChanged -> processHotspotChanged(event.value, event.address)
            is RoomEvent.PlayerTransitionObserved -> processPlayerTransition(event.state)
            is RoomEvent.TransportCommandPhaseObserved ->
                processTransportCommandPhaseObserved(event)
            is RoomEvent.PendingTrackAvailabilityProbed ->
                processPendingTrackAvailabilityProbed(event)
            is RoomEvent.PendingTrackTransitionTimedOut ->
                processPendingTrackTransitionTimedOut(event)
            is RoomEvent.PendingPlayTimedOut -> processPendingPlayTimedOut(event)
            is RoomEvent.TransportWatchdogExpired -> processTransportWatchdogExpired(event)
            RoomEvent.HeartbeatTick -> processHeartbeatTick()
            RoomEvent.ClockSyncTick -> processClockSyncTick()
            RoomEvent.PlaybackSyncTick -> processPlaybackSyncTick()
            is RoomEvent.TransferCompleted -> onLocalTrackReady(event.descriptor)
            is RoomEvent.TransferFailed ->
                sendToCoordinator(
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

    private suspend fun processPlayerTransition(value: PlayerState) {
        value.error?.let { error ->
            container.roomStore.updateStructure { it.copy(errorMessage = error) }
        }
        when (val action = playerEventInterpreter.observe(value, isCoordinator(), clock.nowNs())) {
            PlayerEventInterpreter.Action.None -> Unit
            is PlayerEventInterpreter.Action.NaturalRepeat ->
                recordNaturalRepeatTransition(action.queueItemId, action.positionMs)

            is PlayerEventInterpreter.Action.NaturalAdvance -> {
                pendingAutoResumeQueueItemId = null
                recordNaturalTrackTransition(action.queueItemId, action.positionMs)
            }

            is PlayerEventInterpreter.Action.PlaybackEnded ->
                recordNaturalPlaybackEnded(action.queueItemId, action.positionMs, action.durationMs)

            PlayerEventInterpreter.Action.TransitionLoopDetected ->
                recoverFromPlayerTransitionLoop()
        }
    }

    init {
        scope.launch(Dispatchers.IO) {
            persistence.discardPersistedSnapshots()
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
            var previousEventKey: PlayerStateEventPolicy.Key? = null
            player.state.collect { value ->
                // Position is presentation telemetry, not a canonical room event. Publishing it
                // directly keeps the serialized actor available for joins, heartbeats, and user
                // commands even while the progress indicator advances several times per second.
                container.roomStore.updatePlayback { playback ->
                    playback.copy(
                        localPositionMs = value.positionMs,
                        localQueueItemId = value.queueItemId,
                        localIsPlaying = value.playWhenReady,
                        localSeekRevision = value.seekRevision,
                    )
                }
                val eventKey = PlayerStateEventPolicy.key(value)
                if (eventKey != previousEventKey) {
                    previousEventKey = eventKey
                    roomEvents.submit(RoomEvent.PlayerTransitionObserved(value))
                }
            }
        }
    }

    suspend fun handle(command: AppCommand) {
        if (command is AppCommand.Transport) {
            val submitted = CompletableDeferred<Unit>()
            roomEvents.submit(RoomEvent.LocalTransportSubmitted(command, submitted))
            submitted.await()
            if (!transportIntents.awaitLatest(command)) {
                val superseded = CompletableDeferred<Unit>()
                roomEvents.submit(RoomEvent.LocalTransportSuperseded(command, superseded))
                superseded.await()
                return
            }
        }
        if (command == AppCommand.ClearQueue || command == AppCommand.LeaveRoom) {
            // These coordinators own their own synchronization and can invalidate immediately.
            // Room-owned pending transition state is cancelled later inside the serialized actor.
            transportIntents.invalidateAll()
            playerMutations.invalidateTransport()
        }
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(RoomEvent.AppCommandReceived(command, completion))
        completion.await()
    }

    private suspend fun handleAppCommand(command: AppCommand) {
        ensureInitialized()
        log.i(TAG, "App command ${command::class.simpleName}")
        if (command == AppCommand.ClearQueue || command == AppCommand.LeaveRoom) {
            val reason =
                "Cancelled by ${if (command == AppCommand.ClearQueue) "Clear queue" else "Leave room"}"
            supersedePendingTrackTransition(reason)
            supersedePendingPlayCommand(reason)
        }
        when (command) {
            is AppCommand.CreateRoom -> createRoom(command.roomName)
            is AppCommand.JoinRoom -> joinRoom(command.room, command.credential)
            AppCommand.StartDiscovery -> startDiscovery()
            AppCommand.StopDiscovery -> stopDiscovery()
            AppCommand.LeaveRoom -> leaveRoom()
            AppCommand.CreateOfflineNetwork -> hotspot.start { message -> setError(message) }
            AppCommand.StopOfflineNetwork -> hotspot.stop()
            is AppCommand.AddTracks -> error("Track preparation must run outside the room actor")
            is AppCommand.SaveDisplayName -> {
                container.settings.saveDisplayName(command.name)
                identity =
                    container.settings
                        .ensureIdentity()
                        .copy(displayName = command.name.trim().ifBlank { "Friend" })
                container.roomStore.update { it.copy(localIdentity = identity) }
                if (isCoordinator()) {
                    refreshLocalCoordinatorEndpoint()
                } else if (engine != null) {
                    sendToCoordinator(ProtocolBody.EndpointAnnouncement(localEndpoint()))
                }
            }

            is AppCommand.KeepTrack,
            is AppCommand.RemoveTemporaryTrack ->
                error("Library file operations must run outside the room actor")
            is AppCommand.Play -> {
                // Play is an explicit replacement intent even when its target still needs media.
                // Clear an older pending navigation before preflight so the two commands cannot
                // remain active together if this Play request is deferred for preparation.
                supersedePendingTrackTransition("Cancelled by Play")
                if (pendingPlayCommand?.commandId != command.commandId) {
                    supersedePendingPlayCommand("Replaced by a newer Play request")
                }
                if (prepareCurrentTrackForPlay(command)) {
                    val snapshot = engine?.snapshot()
                    val local = player.state.value
                    when (
                        PlaybackIntentReconciliationPolicy.decidePlayRequest(
                            canonicalPlaying = snapshot?.playback?.isPlaying == true,
                            canonicalQueueItemId = snapshot?.playback?.queueItemId,
                            localQueueItemId = local.queueItemId,
                            locallySuppressed = local.locallySuppressed,
                        )
                    ) {
                        PlaybackIntentReconciliationPolicy.PlayRequestAction
                            .RESUME_LOCAL_OUTPUT -> {
                            executeImmediateLocalTransport(
                                commandId = command.commandId,
                                action = TransportAction.PLAY,
                            ) {
                                play()
                            }
                            log.i(TAG, "Resumed device-local output without rescheduling the room")
                        }
                        PlaybackIntentReconciliationPolicy.PlayRequestAction
                            .MUTATE_CANONICAL_ROOM ->
                            submitUserCommand(
                                UserCommand.Play(
                                    commandId = command.commandId,
                                    requestedBy = identity.peerId,
                                )
                            )
                    }
                }
            }

            is AppCommand.Pause -> {
                supersedePendingPlayCommand("Cancelled by Pause")
                submitUserCommand(
                    UserCommand.Pause(commandId = command.commandId, requestedBy = identity.peerId)
                )
            }

            is AppCommand.Seek -> {
                supersedePendingPlayCommand("Replaced by a seek")
                submitUserCommand(
                    UserCommand.Seek(
                        commandId = command.commandId,
                        requestedBy = identity.peerId,
                        positionMs = command.positionMs,
                    )
                )
            }

            is AppCommand.SkipNext -> {
                supersedePendingPlayCommand("Replaced by Next")
                submitUserCommand(
                    UserCommand.SkipNext(
                        commandId = command.commandId,
                        requestedBy = identity.peerId,
                    )
                )
            }

            is AppCommand.SkipPrevious -> {
                supersedePendingPlayCommand("Replaced by Previous")
                submitUserCommand(
                    UserCommand.SkipPrevious(
                        commandId = command.commandId,
                        requestedBy = identity.peerId,
                    )
                )
            }

            is AppCommand.PlayQueueItem -> {
                supersedePendingPlayCommand("Replaced by a selected song")
                submitUserCommand(
                    UserCommand.PlayQueueItem(
                        commandId = command.commandId,
                        requestedBy = identity.peerId,
                        queueItemId = command.queueItemId,
                    )
                )
            }

            AppCommand.ShuffleQueue ->
                submitUserCommand(
                    UserCommand.QueueShuffle(
                        requestedBy = identity.peerId,
                        shuffleSeed = clock.nowNs() xor identity.peerId.value.hashCode().toLong(),
                    )
                )

            is AppCommand.SetRepeat ->
                submitUserCommand(
                    UserCommand.PlaybackModeChange(
                        requestedBy = identity.peerId,
                        shuffleEnabled = engine?.snapshot()?.shuffleEnabled == true,
                        repeatMode = command.mode,
                        shuffleSeed = clock.nowNs() xor identity.peerId.value.hashCode().toLong(),
                    )
                )

            is AppCommand.RemoveQueueItem ->
                submitUserCommand(
                    UserCommand.QueueRemove(
                        requestedBy = identity.peerId,
                        queueItemId = command.queueItemId,
                    )
                )

            is AppCommand.MoveQueueItem ->
                submitUserCommand(
                    UserCommand.QueueMove(
                        requestedBy = identity.peerId,
                        queueItemId = command.queueItemId,
                        newIndex = command.newIndex,
                    )
                )

            is AppCommand.MoveQueueItemNext ->
                submitUserCommand(
                    UserCommand.QueueMoveAfterCurrent(
                        requestedBy = identity.peerId,
                        queueItemId = command.queueItemId,
                    )
                )

            AppCommand.ClearPlayed ->
                submitUserCommand(UserCommand.QueueClearPlayed(requestedBy = identity.peerId))

            AppCommand.ClearQueue ->
                submitUserCommand(UserCommand.QueueClear(requestedBy = identity.peerId))

            is AppCommand.UpdateRoomOptions ->
                submitUserCommand(
                    UserCommand.OptionsChange(
                        requestedBy = identity.peerId,
                        options = command.options,
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
            val managerGeneration = sessionJobs.generation
            transferManager =
                TransferManager(
                    localIdentity = identity,
                    listeningPort = { server.port },
                    appVersion = BuildConfig.VERSION_NAME,
                    trackRepository = container.trackRepository,
                    fileStore = container.fileStore,
                    scope = scope,
                    log = log,
                    retentionPolicyProvider = { container.settings.retentionPolicy.first() },
                    onProgress = { progress ->
                        if (managerGeneration == sessionJobs.generation) {
                            container.roomStore.updateTransfers { state ->
                                state.copy(
                                    transfers = state.transfers + (progress.trackId to progress)
                                )
                            }
                        }
                    },
                    onCompleted = { descriptor ->
                        if (managerGeneration == sessionJobs.generation) {
                            roomEvents.submit(RoomEvent.TransferCompleted(descriptor))
                        }
                    },
                    onFailed = { trackId, sourcePeerId, reason ->
                        if (managerGeneration == sessionJobs.generation) {
                            roomEvents.submit(
                                RoomEvent.TransferFailed(trackId, sourcePeerId, reason)
                            )
                        }
                    },
                )
        }
        container.roomStore.update { it.copy(roomPort = port) }
    }

    private suspend fun createRoom(requestedName: String?) {
        resetSession(keepDiscovery = false)
        ensureServerAndTransfers()
        playerMutations.maintenance { setRepeatCurrentItem(false) }
        container.roomStore.reset()
        val id = UUID.randomUUID().toString()
        val name =
            requestedName?.trim()?.take(60)?.ifBlank { null } ?: "${identity.displayName}'s room"
        val pin = Crypto.randomFourDigitPin()
        roomPin = pin
        roomSecret = Crypto.randomBytes(32)
        coordinatorPeerId = identity.peerId
        resetClockSynchronization()
        clockReadyPeers.clear()
        clockReadyPeers.add(identity.peerId)
        val endpoint = localEndpoint()
        peerDirectory[identity.peerId] = endpoint
        val initial =
            RoomSnapshot(
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

    private suspend fun joinRoom(room: DiscoveredRoom, credential: RoomJoinCredential) {
        resetSession(keepDiscovery = false)
        ensureServerAndTransfers()
        container.roomStore.reset()
        pendingJoin =
            PendingJoin(
                room = room,
                credential = credential,
                generation = sessionJobs.generation,
            )
        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.CONNECTING,
                status = UserFacingStatus.PREPARING,
                statusMessage = "Connecting…",
                errorMessage = null,
                issue = null,
            )
        }
        refreshPowerLocks()
        startPendingJoinAttempt()
    }

    /**
     * Starts blocking socket admission outside the serialized room actor so Cancel remains usable.
     */
    private fun startPendingJoinAttempt() {
        if (joinAttemptJob?.isActive == true) return
        val pending = pendingJoin ?: return
        if (!sessionJobs.isCurrent(pending.generation)) return
        val attempt = pending.attemptsStarted + 1
        pendingJoin = pending.copy(attemptsStarted = attempt)
        container.roomStore.update { state ->
            state.copy(
                lifecycle = RoomLifecycleState.CONNECTING,
                status = UserFacingStatus.PREPARING,
                statusMessage =
                    if (attempt == 1) {
                        "Connecting to ${pending.room.roomName}…"
                    } else {
                        "Retrying connection ($attempt/${JoinRetryPolicy.MAX_ATTEMPTS})…"
                    },
                errorMessage = null,
                issue = null,
            )
        }
        refreshPowerLocks()
        joinAttemptJob = launchSessionJob {
            val startedAtMs = SystemClock.elapsedRealtime()
            try {
                log.i(
                    TAG,
                    "Joining room id=${pending.room.roomId.take(8)} " +
                        "attempt=$attempt peer=${identity.peerId.value.take(8)} " +
                        "target=${pending.room.hostAddress}:${pending.room.port}",
                )
                val credential = pending.credential as RoomJoinCredential.Pin
                val connected =
                    controlClient.connectWithPin(
                        identity = identity,
                        roomId = pending.room.roomId,
                        host = pending.room.hostAddress,
                        port = pending.room.port,
                        listeningPort = server.port,
                        pin = credential.value,
                        appVersion = BuildConfig.VERSION_NAME,
                        onEnvelope = ::enqueueEnvelope,
                        onClosed = ::enqueueControlClosed,
                    )
                log.i(
                    TAG,
                    "Room admission connected attempt=$attempt " +
                        "durationMs=${SystemClock.elapsedRealtime() - startedAtMs}",
                )
                roomEvents.submit(
                    RoomEvent.InitialJoinConnected(pending.generation, attempt, connected)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                log.w(
                    TAG,
                    "Room admission failed attempt=$attempt " +
                        "durationMs=${SystemClock.elapsedRealtime() - startedAtMs} " +
                        "type=${error::class.simpleName}",
                    error,
                )
                roomEvents.submit(RoomEvent.InitialJoinFailed(pending.generation, attempt, error))
            }
        }
    }

    private suspend fun processInitialJoinConnected(event: RoomEvent.InitialJoinConnected) {
        joinAttemptJob = null
        val pending = pendingJoin
        if (
            pending == null ||
                pending.generation != event.generation ||
                pending.attemptsStarted != event.attempt ||
                !sessionJobs.isCurrent(event.generation)
        ) {
            event.connected.connection.closeSilently()
            event.connected.roomSecret.fill(0)
            return
        }
        coordinatorConnection?.closeSilently()
        roomSecret?.fill(0)
        roomSecret = event.connected.roomSecret
        roomPin = null
        coordinatorPeerId = event.connected.coordinatorPeerId
        coordinatorConnection = event.connected.connection
        connections[event.connected.coordinatorPeerId] = event.connected.connection
        event.connected.connection.start()
        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.JOINING,
                status = UserFacingStatus.PREPARING,
                statusMessage = "Connected. Loading room…",
                errorMessage = null,
                issue = null,
            )
        }
        refreshPowerLocks()
        joinTimeoutJob?.cancel()
        joinTimeoutJob = launchSessionJob {
            delay(INITIAL_JOIN_TIMEOUT_MS)
            if (engine == null && coordinatorConnection === event.connected.connection) {
                event.connected.connection.close(JoinAcceptanceTimeoutException())
            }
        }
    }

    private suspend fun processInitialJoinFailed(event: RoomEvent.InitialJoinFailed) {
        joinAttemptJob = null
        val pending = pendingJoin
        if (
            pending == null ||
                pending.generation != event.generation ||
                pending.attemptsStarted != event.attempt ||
                !sessionJobs.isCurrent(event.generation)
        )
            return

        if (!pending.identityCollisionRetried && isIdentityCollision(event.error)) {
            refreshDuplicatedIdentity()
            pendingJoin =
                pending.copy(
                    generation = sessionJobs.generation,
                    attemptsStarted = 0,
                    identityCollisionRetried = true,
                )
            refreshPowerLocks()
            startPendingJoinAttempt()
            return
        }

        val decision = JoinRetryPolicy.decide(event.error, event.attempt)
        if (!decision.retry) {
            log.w(TAG, "Could not join room after attempt=${event.attempt}", event.error)
            pendingJoin = null
            setFailure(userFacingJoinFailure(event.error))
            return
        }

        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.CONNECTING,
                status = UserFacingStatus.RECONNECTING,
                statusMessage = decision.message ?: "Retrying connection…",
                errorMessage = null,
                issue = null,
            )
        }
        refreshPowerLocks()
        val generation = pending.generation
        joinAttemptJob = launchSessionJob {
            delay(decision.delayMs)
            roomEvents.submit(RoomEvent.InitialJoinRetry(generation))
        }
    }

    private suspend fun refreshDuplicatedIdentity() {
        val previousPeerId = identity.peerId
        sessionJobs.advanceAndCancelNow()
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
                issue = null,
            )
        }
        ensureServerAndTransfers()
        log.w(
            TAG,
            "Refreshed duplicated identity old=${previousPeerId.value.take(8)} " +
                "new=${identity.peerId.value.take(8)}; retrying join once",
        )
    }

    private fun isIdentityCollision(error: Throwable): Boolean {
        var current: Throwable? = error
        repeat(8) {
            val protocol = current as? ProtocolException
            if (protocol?.rejectionCode == HandshakeRejectionCode.IDENTITY_COLLISION) return true
            current = current?.cause ?: return false
        }
        return false
    }

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
                lifecycle =
                    if (it.snapshot == null) RoomLifecycleState.DISCOVERING else it.lifecycle,
                discoveredRooms = emptyList(),
                discoveryCompleted = false,
                statusMessage =
                    if (it.snapshot == null) "Looking for nearby rooms…" else it.statusMessage,
                errorMessage = null,
                issue = null,
            )
        }
        val scanJob =
            scope.launch(start = CoroutineStart.LAZY) {
                fun publishRooms() {
                    val rooms = discoveredRoomRegistry.rooms()
                    container.roomStore.update { state ->
                        if (state.discoveredRooms == rooms) state
                        else state.copy(discoveredRooms = rooms)
                    }
                }
                try {
                    val completedNormally =
                        withTimeoutOrNull(MANUAL_DISCOVERY_WINDOW_MS) {
                            discovery.discover().collect { event ->
                                when (event) {
                                    is NsdDiscoveryEvent.Found -> {
                                        if (discoveredRoomRegistry.found(event.room)) {
                                            publishRooms()
                                        }
                                    }

                                    // Keep a room found during this short scan visible after the
                                    // browse
                                    // window closes. The next button press clears the list and
                                    // performs a
                                    // fresh scan, avoiding mDNS loss flicker without background
                                    // discovery.
                                    is NsdDiscoveryEvent.Lost -> Unit
                                }
                            }
                            true
                        }
                    if (completedNormally == true) {
                        log.w(
                            TAG,
                            "NSD discovery flow completed before the manual scan window ended",
                        )
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
                            errorMessage =
                                if (recoverable) {
                                    "Room search stopped. Tap Find rooms to try again"
                                } else {
                                    "Nearby room access is unavailable"
                                }
                        )
                    }
                } finally {
                    // callbackFlow owns its exact listener and closes it in awaitClose. Do not call
                    // the
                    // class-level stop method here: an older cancelled job could otherwise stop a
                    // newer
                    // scan that has already installed its listener.
                    if (discoveryGeneration == scanGeneration) {
                        discoveryJob = null
                        container.roomStore.update { state ->
                            state.copy(
                                lifecycle =
                                    if (state.snapshot == null) RoomLifecycleState.IDLE
                                    else state.lifecycle,
                                discoveryCompleted = state.snapshot == null,
                                statusMessage =
                                    if (state.snapshot == null) null else state.statusMessage,
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
                lifecycle =
                    if (state.snapshot == null) RoomLifecycleState.IDLE else state.lifecycle,
                discoveredRooms = emptyList(),
                discoveryCompleted = false,
                statusMessage = if (state.snapshot == null) null else state.statusMessage,
            )
        }
    }

    private fun beginRepositoryCommand(
        command: AppCommand,
        completion: CompletableDeferred<Unit>,
    ) {
        val generation = sessionJobs.generation
        val submitted = AtomicBoolean(false)
        val job = launchSessionJob {
            val error =
                try {
                    withContext(Dispatchers.IO) {
                        when (command) {
                            is AppCommand.KeepTrack ->
                                container.trackRepository.keep(command.trackId)
                            is AppCommand.RemoveTemporaryTrack ->
                                container.trackRepository.deleteTemporary(command.trackId)

                            else -> error("Unsupported repository command")
                        }
                    }
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    failure
                }
            val completed =
                RoomEvent.RepositoryCommandCompleted(
                    generation = generation,
                    command = command,
                    error = error,
                    completion = completion,
                )
            submitted.set(true)
            try {
                roomEvents.submit(completed)
            } catch (failure: Throwable) {
                submitted.set(false)
                throw failure
            }
        }
        job.invokeOnCompletion { cause ->
            if (!submitted.get() && !completion.isCompleted) {
                completion.completeExceptionally(
                    cause ?: CancellationException("Room changed during library operation")
                )
            }
        }
    }

    private suspend fun beginTrackPreparation(
        command: AppCommand.AddTracks,
        completion: CompletableDeferred<Unit>,
    ) {
        if (command.trackIds.isEmpty()) {
            completion.complete(Unit)
            return
        }
        val remainingCapacity =
            (RoomReducer.MAX_QUEUE_ITEMS - (engine?.snapshot()?.queue?.size ?: 0)).coerceAtLeast(0)
        if (remainingCapacity == 0) {
            setError("The room queue is full")
            completion.complete(Unit)
            return
        }
        container.roomStore.update {
            it.copy(
                status = UserFacingStatus.PREPARING,
                statusMessage = "Adding music…",
                errorMessage = null,
            )
        }
        val requestedTrackIds = command.trackIds.take(remainingCapacity)
        val generation = sessionJobs.generation
        val submitted = AtomicBoolean(false)
        val job = launchSessionJob {
            val result =
                try {
                    Result.success(
                        withContext(Dispatchers.IO) {
                            container.trackRepository.getMany(requestedTrackIds).filter { descriptor
                                ->
                                suspendResult {
                                        container.trackRepository.requireReadableFile(
                                            descriptor.trackId
                                        ) != null
                                    }
                                    .onFailure { error ->
                                        log.w(
                                            TAG,
                                            "Could not prepare ${descriptor.trackId.value.take(8)}",
                                            error,
                                        )
                                    }
                                    .getOrDefault(false)
                            }
                        }
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Result.failure(error)
                }
            val prepared =
                RoomEvent.TracksPrepared(
                    generation = generation,
                    requestedCount = command.trackIds.size,
                    selectedCount = requestedTrackIds.size,
                    available = result.getOrDefault(emptyList()),
                    insertAfterCurrent = command.insertAfterCurrent,
                    error = result.exceptionOrNull(),
                    completion = completion,
                )
            submitted.set(true)
            try {
                roomEvents.submit(prepared)
            } catch (error: Throwable) {
                submitted.set(false)
                throw error
            }
        }
        job.invokeOnCompletion { cause ->
            if (!submitted.get() && !completion.isCompleted) {
                completion.completeExceptionally(
                    cause ?: CancellationException("Room changed while preparing music")
                )
            }
        }
    }

    private suspend fun applyPreparedTracks(event: RoomEvent.TracksPrepared) {
        event.error?.let { throw it }
        if (!sessionJobs.isCurrent(event.generation) || engine == null) {
            throw CancellationException("Room changed while preparing music")
        }
        if (event.available.isEmpty()) {
            setError("Unison could not open this music")
            return
        }
        event.available.chunked(RoomReducer.MAX_TRACKS_PER_COMMAND).forEach { tracks ->
            submitUserCommand(
                UserCommand.QueueAdd(
                    requestedBy = identity.peerId,
                    tracks = tracks,
                    insertAfterCurrent = event.insertAfterCurrent,
                )
            )
        }
        when {
            event.available.size < event.selectedCount -> setError("Some songs could not be opened")
            event.selectedCount < event.requestedCount ->
                setError("The room queue holds up to ${RoomReducer.MAX_QUEUE_ITEMS} songs")
        }
    }

    /**
     * Removes the first-track race: the room snapshot becomes visible before its asynchronous queue
     * side effects necessarily finish. Warm the local Media3 queue and refresh this peer's
     * availability before evaluating Play. Duplicate TrackHave messages are harmless and make
     * first-track startup deterministic.
     */
    private suspend fun prepareCurrentTrackForPlay(command: AppCommand.Play): Boolean {
        val snapshot = engine?.snapshot() ?: return true
        val item = PlaybackRequestPolicy.currentItem(snapshot) ?: return true
        container.roomStore.update {
            it.copy(
                status = UserFacingStatus.PREPARING,
                statusMessage = "Preparing music…",
                errorMessage = null,
            )
        }
        refreshPlayerQueue(snapshot)
        val hasFile =
            withContext(Dispatchers.IO) {
                suspendResult {
                        container.trackRepository.requireReadableFile(item.track.trackId) != null
                    }
                    .onFailure { error ->
                        log.w(TAG, "Could not prepare ${item.track.trackId.value.take(8)}", error)
                    }
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
                setPendingPlayCommand(
                    UserCommand.Play(command.commandId, identity.peerId),
                    item.queueItemId,
                )
                onTrackNeed(identity.peerId, item.track.trackId)
                if (snapshot.members.count { it.connected } <= 1) {
                    clearPendingPlayCommand()
                    updateLocalTransportPhase(
                        command.commandId,
                        TransportCommandPhase.REJECTED,
                        "This song is no longer available. Add it again.",
                        queueItemId = item.queueItemId,
                        action = TransportAction.PLAY,
                    )
                    setError("This song is no longer available. Add it again.")
                    return false
                }
                updateLocalTransportPhase(
                    command.commandId,
                    TransportCommandPhase.ACCEPTED,
                    "Getting the song ready…",
                    queueItemId = item.queueItemId,
                    action = TransportAction.PLAY,
                )
                container.roomStore.update {
                    it.copy(
                        status = UserFacingStatus.RECEIVING,
                        statusMessage = "Getting the song ready…",
                    )
                }
                return false
            }
        } else {
            sendToCoordinator(
                if (hasFile) ProtocolBody.TrackHave(item.track.trackId)
                else ProtocolBody.TrackNeed(item.track.trackId)
            )
        }
        return true
    }

    private fun rememberLocalTransport(command: AppCommand.Transport) {
        completedLocalTransportCommandIds.remove(command.commandId)
        localTransportCommandIds.add(command.commandId)
        while (localTransportCommandIds.size > 256) localTransportCommandIds.remove(
            localTransportCommandIds.first()
        )
        val queueItemId = (command as? AppCommand.PlayQueueItem)?.queueItemId
        val positionMs = (command as? AppCommand.Seek)?.positionMs
        container.roomStore.updateStructure { state ->
            state.copy(
                transportStatus =
                    TransportCommandStatus(
                        commandId = command.commandId,
                        action = command.transportAction(),
                        phase = TransportCommandPhase.SUBMITTED,
                        queueItemId = queueItemId,
                        requestedPositionMs = positionMs,
                    ),
                errorMessage = null,
                issue = null,
            )
        }
    }

    private fun updateLocalTransportPhase(
        commandId: String,
        phase: TransportCommandPhase,
        message: String? = null,
        queueItemId: QueueItemId? = null,
        requestedPositionMs: Long? = null,
        action: TransportAction? = null,
    ) {
        if (commandId !in localTransportCommandIds) return
        if (commandId in completedLocalTransportCommandIds) return
        var applied = false
        container.roomStore.updateStructure { state ->
            val current = state.transportStatus
            if (current != null && current.commandId != commandId && current.active)
                return@updateStructure state
            if (
                current?.commandId == commandId && !canAdvanceTransportPhase(current.phase, phase)
            ) {
                return@updateStructure state
            }
            val resolved =
                if (current?.commandId == commandId) {
                    current.copy(
                        phase = phase,
                        queueItemId = queueItemId ?: current.queueItemId,
                        requestedPositionMs = requestedPositionMs ?: current.requestedPositionMs,
                        message = message,
                    )
                } else {
                    TransportCommandStatus(
                        commandId = commandId,
                        action = action ?: return@updateStructure state,
                        phase = phase,
                        queueItemId = queueItemId,
                        requestedPositionMs = requestedPositionMs,
                        message = message,
                    )
                }
            applied = true
            state.copy(transportStatus = resolved)
        }
        if (!applied) return
        if (!phase.isTerminal) {
            transportStatusClearJob?.cancel()
            transportStatusClearJob = null
        } else {
            completedLocalTransportCommandIds.add(commandId)
            while (completedLocalTransportCommandIds.size > RECENT_COMMAND_ID_LIMIT) {
                completedLocalTransportCommandIds.remove(completedLocalTransportCommandIds.first())
            }
            transportStatusClearJob?.cancel()
            transportStatusClearJob = scope.launch {
                delay(
                    if (phase == TransportCommandPhase.REJECTED) TRANSPORT_REJECTION_VISIBLE_MS
                    else TRANSPORT_RESULT_VISIBLE_MS
                )
                container.roomStore.updateStructure { state ->
                    val current = state.transportStatus
                    if (current?.commandId == commandId && current.phase == phase) {
                        state.copy(transportStatus = null)
                    } else {
                        state
                    }
                }
            }
        }
    }

    private suspend fun executeImmediateLocalTransport(
        commandId: String,
        action: TransportAction,
        block: suspend PlayerPort.() -> Boolean,
    ) {
        val (ticket, superseded) = playerMutations.beginTransport(commandId)
        if (superseded != null && superseded != commandId) {
            updateLocalTransportPhase(
                superseded,
                TransportCommandPhase.SUPERSEDED,
                "Replaced by a newer action",
            )
        }
        updateLocalTransportPhase(commandId, TransportCommandPhase.ACCEPTED, action = action)
        updateLocalTransportPhase(commandId, TransportCommandPhase.EXECUTING, action = action)
        when (playerMutations.executeTransport(ticket, block)) {
            PlayerMutationCoordinator.ExecutionResult.SUCCESS ->
                updateLocalTransportPhase(commandId, TransportCommandPhase.SETTLED, action = action)
            PlayerMutationCoordinator.ExecutionResult.FAILED ->
                updateLocalTransportPhase(
                    commandId,
                    TransportCommandPhase.REJECTED,
                    "Playback could not complete that action",
                    action = action,
                )
            PlayerMutationCoordinator.ExecutionResult.STALE ->
                updateLocalTransportPhase(
                    commandId,
                    TransportCommandPhase.SUPERSEDED,
                    "Replaced by a newer action",
                    action = action,
                )
        }
    }

    private fun canAdvanceTransportPhase(
        from: TransportCommandPhase,
        to: TransportCommandPhase,
    ): Boolean {
        if (from.isTerminal || from == to) return false
        if (to.isTerminal) return true
        fun order(phase: TransportCommandPhase): Int =
            when (phase) {
                TransportCommandPhase.SUBMITTED -> 0
                TransportCommandPhase.ACCEPTED -> 1
                TransportCommandPhase.SCHEDULED -> 2
                TransportCommandPhase.EXECUTING -> 3
                TransportCommandPhase.SETTLED,
                TransportCommandPhase.SUPERSEDED,
                TransportCommandPhase.REJECTED -> 4
            }
        return order(to) > order(from)
    }

    private suspend fun publishTransportStatus(
        requestedBy: PeerId,
        commandId: String,
        action: TransportAction,
        phase: TransportCommandPhase,
        queueItemId: QueueItemId? = null,
        requestedPositionMs: Long? = null,
        message: String? = null,
    ) {
        if (
            transportCommands.route(commandId) == null && !transportCommands.isCompleted(commandId)
        ) {
            transportCommands.remember(
                commandId,
                TransportCommandTracker.Route(
                    requestedBy = requestedBy,
                    action = action,
                    queueItemId = queueItemId,
                    requestedPositionMs = requestedPositionMs,
                ),
            )
        }
        val transition =
            transportCommands.transition(
                commandId = commandId,
                phase = phase,
                queueItemId = queueItemId,
                requestedPositionMs = requestedPositionMs,
            )
        val route = (transition as? TransportCommandTracker.Transition.Applied)?.route
        if (route == null) {
            if (
                phase == TransportCommandPhase.ACCEPTED &&
                    transition == TransportCommandTracker.Transition.Duplicate &&
                    !isTransportPreparing(commandId)
            ) {
                transportCommands.route(commandId)?.let { scheduleTransportWatchdog(commandId, it) }
            }
            return
        }
        log.i(
            TAG,
            "Transport command id=${commandId.take(8)} action=$action phase=$phase " +
                "item=${route.queueItemId?.value?.take(8) ?: "none"}" +
                (message?.let { " message=${it.take(120)}" } ?: ""),
        )
        if (requestedBy == identity.peerId) {
            updateLocalTransportPhase(
                commandId = commandId,
                phase = phase,
                message = message,
                queueItemId = route.queueItemId,
                requestedPositionMs = route.requestedPositionMs,
                action = action,
            )
        } else {
            send(
                requestedBy,
                ProtocolBody.CommandStatus(
                    commandId = commandId,
                    action = action,
                    phase = phase,
                    queueItemId = route.queueItemId,
                    requestedPositionMs = route.requestedPositionMs,
                    message = message,
                ),
            )
        }
        if (phase.isTerminal) {
            transportWatchdogJobs.remove(commandId)?.cancel()
        } else if (!isTransportPreparing(commandId)) {
            scheduleTransportWatchdog(commandId, route)
        }
    }

    private fun onScheduledCommandPhase(
        commandId: String,
        phase: TransportCommandPhase,
        message: String?,
    ) {
        val generation = sessionJobs.generation
        scope.launch {
            roomEvents.submit(
                RoomEvent.TransportCommandPhaseObserved(
                    generation = generation,
                    commandId = commandId,
                    phase = phase,
                    message = message,
                )
            )
        }
    }

    private suspend fun processTransportCommandPhaseObserved(
        event: RoomEvent.TransportCommandPhaseObserved
    ) {
        if (!sessionJobs.isCurrent(event.generation)) return
        val route = transportCommands.route(event.commandId)
        if (route == null) {
            if (transportCommands.isCompleted(event.commandId)) return
            updateLocalTransportPhase(event.commandId, event.phase, event.message)
            return
        }
        publishTransportStatus(
            requestedBy = route.requestedBy,
            commandId = event.commandId,
            action = route.action,
            phase = event.phase,
            queueItemId = route.queueItemId,
            requestedPositionMs = route.requestedPositionMs,
            message = event.message,
        )
    }

    private fun isTransportPreparing(commandId: String): Boolean =
        pendingTrackTransitions.matches(commandId) || pendingPlayCommand?.commandId == commandId

    private fun scheduleTransportWatchdog(
        commandId: String,
        route: TransportCommandTracker.Route,
        reconciliationAttempted: Boolean = false,
        delayMs: Long =
            when (route.phase) {
                TransportCommandPhase.ACCEPTED -> TRANSPORT_ACCEPTED_WATCHDOG_MS
                TransportCommandPhase.SCHEDULED,
                TransportCommandPhase.EXECUTING -> TRANSPORT_EXECUTION_WATCHDOG_MS
                else -> TRANSPORT_ACCEPTED_WATCHDOG_MS
            },
    ) {
        transportWatchdogJobs.remove(commandId)?.cancel()
        val generation = sessionJobs.generation
        transportWatchdogJobs[commandId] = launchSessionJob {
            delay(delayMs)
            roomEvents.submit(
                RoomEvent.TransportWatchdogExpired(
                    generation = generation,
                    commandId = commandId,
                    ticket = route.ticket,
                    reconciliationAttempted = reconciliationAttempted,
                )
            )
        }
    }

    private suspend fun processTransportWatchdogExpired(event: RoomEvent.TransportWatchdogExpired) {
        if (!sessionJobs.isCurrent(event.generation)) return
        val route = transportCommands.route(event.commandId, event.ticket) ?: return
        transportWatchdogJobs.remove(event.commandId)
        if (isTransportPreparing(event.commandId)) return
        if (transportAlreadyAligned(route)) {
            publishTransportStatus(
                requestedBy = route.requestedBy,
                commandId = event.commandId,
                action = route.action,
                phase = TransportCommandPhase.SETTLED,
                queueItemId = route.queueItemId,
                requestedPositionMs = route.requestedPositionMs,
                message = "ALREADY_ALIGNED",
            )
            return
        }
        if (!event.reconciliationAttempted) {
            if (
                route.phase == TransportCommandPhase.SCHEDULED ||
                    route.phase == TransportCommandPhase.EXECUTING
            ) {
                scheduler.cancelIfOwned(event.commandId, publishSuperseded = false)
            }
            reconcileTransportFromCanonical()
            if (transportAlreadyAligned(route)) {
                publishTransportStatus(
                    requestedBy = route.requestedBy,
                    commandId = event.commandId,
                    action = route.action,
                    phase = TransportCommandPhase.SETTLED,
                    queueItemId = route.queueItemId,
                    requestedPositionMs = route.requestedPositionMs,
                    message = "SETTLED_AFTER_RECONCILIATION",
                )
            } else {
                transportCommands.route(event.commandId, event.ticket)?.let { current ->
                    scheduleTransportWatchdog(
                        commandId = event.commandId,
                        route = current,
                        reconciliationAttempted = true,
                        delayMs = TRANSPORT_RECONCILIATION_GRACE_MS,
                    )
                }
            }
            return
        }

        val message = "Playback state could not be reconciled"
        publishTransportStatus(
            requestedBy = route.requestedBy,
            commandId = event.commandId,
            action = route.action,
            phase = TransportCommandPhase.REJECTED,
            queueItemId = route.queueItemId,
            requestedPositionMs = route.requestedPositionMs,
            message = message,
        )
        if (route.requestedBy == identity.peerId) {
            setIssue(
                RoomIssue(
                    code = RoomIssueCode.INTERNAL_FAILURE,
                    message = message,
                    commandId = event.commandId,
                    queueItemId = route.queueItemId,
                    deduplicationKey = "transport-reconciliation:${event.commandId}",
                )
            )
        }
    }

    private suspend fun transportAlreadyAligned(route: TransportCommandTracker.Route): Boolean {
        val snapshot = engine?.snapshot() ?: return false
        val target = route.queueItemId ?: snapshot.playback.queueItemId ?: return false
        if (snapshot.playback.queueItemId != target) return false
        val sample = player.samplePlayback()
        val expectedPositionMs =
            route.requestedPositionMs
                ?: snapshot.playback.projectedPositionMs(clockSync.coordinatorNowNs())
        val toleranceMs =
            if (snapshot.playback.isPlaying) {
                TRANSPORT_PLAYING_POSITION_TOLERANCE_MS
            } else {
                TRANSPORT_PAUSED_POSITION_TOLERANCE_MS
            }
        return sample.queueItemId == target &&
            abs(sample.positionMs - expectedPositionMs) <= toleranceMs &&
            sample.playWhenReady == snapshot.playback.isPlaying
    }

    private suspend fun reconcileTransportFromCanonical() {
        val snapshot = engine?.snapshot() ?: return
        refreshPlayerQueue(snapshot, allowDeferredRetry = false)
        val target = snapshot.playback.queueItemId ?: return
        val positionMs = snapshot.playback.projectedPositionMs(clockSync.coordinatorNowNs())
        playerMutations.synchronize {
            val local = state.value
            if (
                local.queueItemId != target ||
                    abs(local.positionMs - positionMs) > TRANSPORT_PAUSED_POSITION_TOLERANCE_MS
            ) {
                if (!seekToItem(target, positionMs)) return@synchronize
            }
            setPlaybackSpeed(1f)
            if (snapshot.playback.isPlaying) play() else pause()
        }
    }

    private suspend fun submitUserCommand(command: UserCommand) {
        val snapshot =
            engine?.snapshot()
                ?: run {
                    setError("Join or create a room first")
                    return
                }
        if (snapshot.term.coordinatorPeerId == identity.peerId) {
            applyCoordinatorCommand(command)
        } else {
            sendToCoordinator(ProtocolBody.UserCommandRequest(command))
        }
    }

    /**
     * Applies the same latest-intent debounce to commands submitted by another peer without ever
     * delaying the serialized room actor. A final actor-side [TransportIntentCoordinator.isLatest]
     * check closes the small race between the debounce completing and the event being consumed.
     */
    private fun queueRemoteTransportCommand(command: UserCommand) {
        val generation = sessionJobs.generation
        launchSessionJob {
            if (!transportIntents.awaitLatest(command)) {
                roomEvents.submit(RoomEvent.CoordinatorTransportSuperseded(generation, command))
                return@launchSessionJob
            }
            if (!sessionJobs.isCurrent(generation)) return@launchSessionJob
            val completion = CompletableDeferred<Unit>()
            roomEvents.submit(RoomEvent.CoordinatorCommandReceived(command, completion))
            completion.await()
        }
    }

    private suspend fun publishSupersededTransport(command: UserCommand) {
        val action = command.transportActionOrNull() ?: return
        if (!recentCommandIds.add(command.commandId)) return
        while (recentCommandIds.size > RECENT_COMMAND_ID_LIMIT) {
            recentCommandIds.remove(recentCommandIds.first())
        }
        publishTransportStatus(
            requestedBy = command.requestedBy,
            commandId = command.commandId,
            action = action,
            phase = TransportCommandPhase.SUPERSEDED,
            queueItemId = (command as? UserCommand.PlayQueueItem)?.queueItemId,
            requestedPositionMs = (command as? UserCommand.Seek)?.positionMs,
            message = "Replaced by a newer action",
        )
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
        while (recentCommandIds.size > RECENT_COMMAND_ID_LIMIT) {
            recentCommandIds.remove(recentCommandIds.first())
        }

        val roomEngine = engine ?: return
        val current = roomEngine.snapshot()
        when (command) {
            is UserCommand.Play -> {
                supersedePendingTrackTransition("Cancelled by Play")
                if (pendingPlayCommand?.commandId != command.commandId) {
                    supersedePendingPlayCommand("Replaced by a newer Play request")
                }
            }

            is UserCommand.Pause -> {
                supersedePendingTrackTransition("Cancelled by Pause")
                supersedePendingPlayCommand("Cancelled by Pause")
            }

            is UserCommand.Seek -> {
                supersedePendingTrackTransition("Replaced by a seek")
                supersedePendingPlayCommand("Replaced by a seek")
            }

            is UserCommand.SkipNext -> supersedePendingPlayCommand("Replaced by Next")
            is UserCommand.SkipPrevious -> supersedePendingPlayCommand("Replaced by Previous")
            is UserCommand.PlayQueueItem ->
                supersedePendingPlayCommand("Replaced by a selected song")
            is UserCommand.QueueRemove -> {
                if (pendingTrackTransitions.peek()?.queueItemId == command.queueItemId) {
                    rejectPendingTrackTransition("The selected song was removed from the queue")
                }
                if (pendingPlayQueueItemId == command.queueItemId) {
                    supersedePendingPlayCommand("The selected song was removed from the queue")
                }
            }

            is UserCommand.QueueClear -> {
                rejectPendingTrackTransition("The queue was cleared")
                supersedePendingPlayCommand("The queue was cleared")
            }

            else -> Unit
        }
        val existingRoute = transportCommands.route(command.commandId)
        val action = existingRoute?.action ?: command.transportActionOrNull()
        if (action != null && existingRoute == null) {
            transportCommands.remember(
                command.commandId,
                TransportCommandTracker.Route(
                    requestedBy = command.requestedBy,
                    action = action,
                    queueItemId = (command as? UserCommand.PlayQueueItem)?.queueItemId,
                    requestedPositionMs = (command as? UserCommand.Seek)?.positionMs,
                ),
            )
        }
        if (action != null && !transportIntents.isLatest(command)) {
            publishTransportStatus(
                requestedBy = command.requestedBy,
                commandId = command.commandId,
                action = action,
                phase = TransportCommandPhase.SUPERSEDED,
                message = "Replaced by a newer action",
            )
            return
        }
        if (command is UserCommand.PlayQueueItem) {
            val existingCommandId =
                pendingTrackTransitions.activeForTarget(command.queueItemId)?.commandId
                    ?: transportCommands
                        .activeForQueueItem(
                            command.queueItemId,
                            excludingCommandId = command.commandId,
                        )
                        ?.first
            if (existingCommandId != null) {
                publishTransportStatus(
                    requestedBy = command.requestedBy,
                    commandId = command.commandId,
                    action = action ?: TransportAction.PLAY_ITEM,
                    phase = TransportCommandPhase.SUPERSEDED,
                    queueItemId = command.queueItemId,
                    message = "Already requested by command ${existingCommandId.take(8)}",
                )
                return
            }
        }
        val priorPendingTransition = pendingTrackTransitions.peek()
        val resolution =
            TransportTargetPolicy.resolve(
                command = command,
                snapshot = current,
                coordinatorNowNs = clock.nowNs(),
                pendingTarget = pendingTrackTransitions.relativeNavigationBase(command),
            )
        if (resolution.alreadyAligned) {
            publishTransportStatus(
                requestedBy = command.requestedBy,
                commandId = command.commandId,
                action = action ?: TransportAction.PLAY_ITEM,
                phase = TransportCommandPhase.SETTLED,
                queueItemId = (command as? UserCommand.PlayQueueItem)?.queueItemId,
                message = "ALREADY_ALIGNED",
            )
            return
        }
        if (
            priorPendingTransition != null &&
                priorPendingTransition.commandId != command.commandId &&
                command.replacesPendingTrackNavigation()
        ) {
            supersedePendingTrackTransition("Replaced by a newer track change")
        }
        resolution.rejection?.let { reason ->
            log.w(TAG, "Command ${command::class.simpleName} rejected: $reason")
            if (action != null) {
                publishTransportStatus(
                    requestedBy = command.requestedBy,
                    commandId = command.commandId,
                    action = action,
                    phase = TransportCommandPhase.REJECTED,
                    message = reason,
                )
            } else if (command.requestedBy == identity.peerId) {
                setError(reason)
            } else {
                send(command.requestedBy, ProtocolBody.CommandRejected(command.commandId, reason))
            }
            return
        }

        resolution.pendingTarget?.let { targetId ->
            val target = current.queue.firstOrNull { it.queueItemId == targetId }
            if (target == null) {
                if (action != null) {
                    publishTransportStatus(
                        requestedBy = command.requestedBy,
                        commandId = command.commandId,
                        action = action,
                        phase = TransportCommandPhase.REJECTED,
                        message = "That song is no longer in the queue",
                    )
                }
                return
            }
            val originalAction = action ?: TransportAction.PLAY_ITEM
            val pending =
                PendingTrackTransition(
                    commandId = command.commandId,
                    action = originalAction,
                    requestedBy = command.requestedBy,
                    queueItemId = targetId,
                    trackId = target.track.trackId,
                    resumePlayback = resolution.pendingResumePlayback ?: true,
                )
            pendingTrackTransitions.replace(pending)?.let { previous ->
                if (previous.commandId != pending.commandId) {
                    publishTransportStatus(
                        previous.requestedBy,
                        previous.commandId,
                        previous.action,
                        TransportCommandPhase.SUPERSEDED,
                        previous.queueItemId,
                        message = "Replaced by a newer track change",
                    )
                }
            }
            publishTransportStatus(
                requestedBy = command.requestedBy,
                commandId = command.commandId,
                action = originalAction,
                phase = TransportCommandPhase.ACCEPTED,
                queueItemId = targetId,
                message = "Preparing the selected song…",
            )
            startPendingTrackPreparation(pending)
            applyCanonicalMutation(
                ProtocolBody.QueueItemPreparationRequested(
                    queueItemId = targetId,
                    commandId = command.commandId,
                )
            )
            // Existing availability may have been announced before this request existed. Reconcile
            // immediately instead of waiting for a duplicate announcement that may never arrive.
            reevaluatePreparation(target.track.trackId)
            return
        }

        val effectiveCommand = resolution.command ?: command
        val currentItem = PlaybackRequestPolicy.currentItem(current)
        if (
            effectiveCommand is UserCommand.PlayQueueItem &&
                action in setOf(TransportAction.NEXT, TransportAction.PREVIOUS) &&
                effectiveCommand.queueItemId == current.playback.queueItemId
        ) {
            publishTransportStatus(
                requestedBy = command.requestedBy,
                commandId = command.commandId,
                action = action ?: TransportAction.PLAY_ITEM,
                phase = TransportCommandPhase.SETTLED,
                queueItemId = effectiveCommand.queueItemId,
                message = "Already on that song",
            )
            return
        }
        if (
            effectiveCommand is UserCommand.Play &&
                currentItem != null &&
                PlaybackRequestPolicy.shouldDeferPlay(current)
        ) {
            setPendingPlayCommand(effectiveCommand, currentItem.queueItemId)
            log.i(
                TAG,
                "Play deferred item=${currentItem.queueItemId.value.take(8)} " +
                    "prepared=false connected=${current.members.count { it.connected }}",
            )
            publishTransportStatus(
                requestedBy = effectiveCommand.requestedBy,
                commandId = effectiveCommand.commandId,
                action = TransportAction.PLAY,
                phase = TransportCommandPhase.ACCEPTED,
                queueItemId = currentItem.queueItemId,
                message = "Preparing music…",
            )
            if (effectiveCommand.requestedBy == identity.peerId) {
                container.roomStore.update {
                    it.copy(
                        status = UserFacingStatus.PREPARING,
                        statusMessage = "Preparing music… Play will start automatically.",
                        errorMessage = null,
                        issue = null,
                    )
                }
            }
            prepareWindow(current)
            val currentTrackId =
                current.playback.queueItemId
                    ?.let { id -> current.queue.firstOrNull { it.queueItemId == id } }
                    ?.track
                    ?.trackId ?: current.queue.firstOrNull()?.track?.trackId
            if (currentTrackId != null) reevaluatePreparation(currentTrackId)
            return
        }

        val leadNs = transportLeadNs(current)
        when (
            val decision =
                roomEngine.decide(
                    effectiveCommand,
                    clock.nowNs(),
                    leadNs,
                    ::snapshotFitsProtocol,
                )
        ) {
            is RoomReducer.Decision.Rejected -> {
                log.w(
                    TAG,
                    "Command ${effectiveCommand::class.simpleName} rejected: ${decision.reason}",
                )
                if (action != null) {
                    publishTransportStatus(
                        requestedBy = command.requestedBy,
                        commandId = command.commandId,
                        action = action,
                        phase = TransportCommandPhase.REJECTED,
                        message = decision.reason,
                    )
                } else if (command.requestedBy == identity.peerId) {
                    setError(decision.reason)
                } else {
                    send(
                        command.requestedBy,
                        ProtocolBody.CommandRejected(command.commandId, decision.reason),
                    )
                }
            }

            is RoomReducer.Decision.Accepted -> {
                log.i(
                    TAG,
                    "Command ${effectiveCommand::class.simpleName} accepted mutations=${decision.mutations.size}",
                )
                if (action != null) {
                    val targetId =
                        decision.mutations
                            .asSequence()
                            .mapNotNull { mutation ->
                                when (val body = mutation.body) {
                                    is ProtocolBody.PlayScheduled -> body.queueItemId
                                    is ProtocolBody.PauseScheduled -> body.queueItemId
                                    is ProtocolBody.SeekScheduled -> body.queueItemId
                                    is ProtocolBody.CurrentItemChanged -> body.queueItemId
                                    else -> null
                                }
                            }
                            .lastOrNull()
                    publishTransportStatus(
                        requestedBy = command.requestedBy,
                        commandId = command.commandId,
                        action = action,
                        phase = TransportCommandPhase.ACCEPTED,
                        queueItemId = targetId,
                        requestedPositionMs = (effectiveCommand as? UserCommand.Seek)?.positionMs,
                    )
                }
                if (
                    effectiveCommand is UserCommand.Play ||
                        effectiveCommand is UserCommand.Pause ||
                        effectiveCommand is UserCommand.Seek ||
                        effectiveCommand is UserCommand.SkipNext ||
                        effectiveCommand is UserCommand.SkipPrevious ||
                        effectiveCommand is UserCommand.PlayQueueItem
                ) {
                    pendingAutoResumeQueueItemId = null
                    clearPendingPlayCommand()
                    pendingTrackTransitions.clearIfCommand(command.commandId)?.let {
                        cancelPendingTrackTransitionJobs()
                    }
                }
                decision.mutations.forEach { mutation ->
                    updateSnapshot(mutation.snapshot)
                    broadcastCanonical(mutation.sequence, mutation.body)
                    playbackDispatcher.submit(mutation.body, mutation.snapshot)
                }
            }
        }
    }

    private fun UserCommand.replacesPendingTrackNavigation(): Boolean =
        when (this) {
            is UserCommand.SkipNext,
            is UserCommand.SkipPrevious,
            is UserCommand.PlayQueueItem -> true
            else -> false
        }

    private fun transportLeadNs(snapshot: RoomSnapshot): Long {
        val activePeers = snapshot.members.filter { it.connected }.mapTo(hashSetOf()) { it.peerId }
        return TransportLeadTimePolicy.leadNs(
            connectedPeerCount = activePeers.size,
            clockReadyPeerCount = clockReadyPeers.count { it in activePeers },
            maxPeerRoundTripNs =
                activePeers
                    .asSequence()
                    .filter { it != identity.peerId }
                    .mapNotNull(clockRoundTripNs::get)
                    .maxOrNull() ?: 0L,
            maxPeerUncertaintyNs =
                activePeers
                    .asSequence()
                    .filter { it != identity.peerId }
                    .mapNotNull(clockUncertaintyNs::get)
                    .maxOrNull() ?: 0L,
            reconnecting =
                container.roomStore.structure.value.lifecycle == RoomLifecycleState.RECONNECTING,
        )
    }

    private suspend fun reconcileCanonicalPlayback(
        reconciliation: CanonicalPlaybackDispatcher.PlaybackReconciliation
    ) {
        val snapshot = reconciliation.snapshot
        log.i(
            TAG,
            "Reconcile playback sequence=${snapshot.sequence} revision=${reconciliation.desired.contentRevision} " +
                "triggers=${reconciliation.triggers.joinToString()}",
        )
        playerMutations.maintenance {
            setRepeatCurrentItem(snapshot.repeatMode == RepeatMode.ONE)
        }
        requestTimelineRefresh(snapshot)
    }

    private suspend fun applyExactCanonicalPlayback(body: ProtocolBody, snapshot: RoomSnapshot) {
        log.i(TAG, "Apply ${body::class.simpleName} sequence=${snapshot.sequence}")
        when (body) {
            is ProtocolBody.QueueItemsRemoved -> {
                // Removing the audible item is followed by a scheduled CurrentItemChanged
                // mutation. Keep the existing ExoPlayer timeline until that timestamp; otherwise
                // setQueue() would select the replacement immediately and create an early skip.
                if (player.state.value.queueItemId !in body.queueItemIds) {
                    requestTimelineRefresh(snapshot)
                } else {
                    prepareWindow(snapshot)
                }
            }

            ProtocolBody.QueueCleared -> {
                clearPendingPlayCommand()
                pendingAutoResumeQueueItemId = null
                scheduler.cancel("Queue cleared")
                playerMutations.invalidateTransport()
                queueRefreshJob?.cancel()
                timelineRefreshJob?.cancel()
                refreshPlayerQueue(snapshot)
            }

            is ProtocolBody.QueueItemPreparationRequested -> {
                if (snapshot.queue.none { it.queueItemId == body.queueItemId }) return
                log.i(
                    TAG,
                    "Preparation request command=${body.commandId?.take(8) ?: "none"} " +
                        "item=${body.queueItemId.value.take(8)} sequence=${snapshot.sequence}",
                )
                prepareWindow(snapshot, priorityQueueItemId = body.queueItemId)
            }

            is ProtocolBody.PlayScheduled ->
                if (canApplyScheduledCommand()) {
                    markTrackPlayed(snapshot, body.queueItemId)
                    scheduler.schedulePlay(
                        body.queueItemId,
                        body.positionMs,
                        body.executeAtCoordinatorNs,
                        body.commandId,
                    )
                }

            is ProtocolBody.PauseScheduled ->
                if (canApplyScheduledCommand()) {
                    scheduler.schedulePause(
                        body.queueItemId,
                        body.positionMs,
                        body.executeAtCoordinatorNs,
                        body.commandId,
                    )
                }

            is ProtocolBody.SeekScheduled ->
                if (canApplyScheduledCommand()) {
                    scheduler.scheduleSeek(
                        body.queueItemId,
                        body.positionMs,
                        body.resumePlayback,
                        body.executeAtCoordinatorNs,
                        body.commandId,
                    )
                }

            is ProtocolBody.CurrentItemChanged -> {
                body.queueItemId?.let { markTrackPlayed(snapshot, it) }
                // A canonical transition is published before its execution timestamp. Updating the
                // player's current item immediately would make this device skip early. Keep the
                // currently audible item selected, preload the target, execute at room time, then
                // reconcile the timeline after the transition.
                val localBefore = player.state.value
                val currentStillInQueue =
                    localBefore.queueItemId?.let { currentId ->
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
                    val expectedNow =
                        body.positionMs +
                            if (body.resumePlayback) {
                                ((clockSync.coordinatorNowNs() - body.executeAtCoordinatorNs)
                                    .coerceAtLeast(0) / 1_000_000L)
                            } else 0L
                    val local = player.state.value
                    val targetTimeReached =
                        body.executeAtCoordinatorNs <= clockSync.coordinatorNowNs()
                    val alreadyAligned =
                        local.queueItemId == target &&
                            abs(local.positionMs - expectedNow) <=
                                CURRENT_ITEM_POSITION_TOLERANCE_MS &&
                            local.playWhenReady == body.resumePlayback
                    if (targetTimeReached && alreadyAligned) {
                        body.commandId?.let { commandId ->
                            onScheduledCommandPhase(
                                commandId,
                                TransportCommandPhase.SETTLED,
                                "ALREADY_ALIGNED",
                            )
                        }
                    } else {
                        scheduler.scheduleSeek(
                            target,
                            body.positionMs,
                            body.resumePlayback,
                            body.executeAtCoordinatorNs,
                            body.commandId,
                        )
                    }
                }
                    ?: run {
                        scheduler.cancel("Queue no longer has a current item")
                        playerMutations.maintenance { pause() }
                    }
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
        val result =
            snapshotValidator.validate(
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
        if (peers.size > MAX_ROOM_MEMBERS || peers.map { it.peerId }.distinct().size != peers.size)
            return false
        val memberIds = snapshot.members.map { it.peerId }.toSet()
        return peers.all { endpoint ->
            endpoint.peerId in memberIds &&
                endpoint.displayName.length in 1..80 &&
                endpoint.displayName.none { it.isISOControl() } &&
                endpoint.hostAddress.length in 1..255 &&
                endpoint.hostAddress.none { it.isISOControl() } &&
                endpoint.port in 1..65535 &&
                endpoint.appVersion.length in 1..64 &&
                endpoint.appVersion.none { it.isISOControl() }
        }
    }

    private suspend fun processEnvelope(peerId: PeerId, envelope: Envelope) {
        lastSeenElapsedMs[peerId] = SystemClock.elapsedRealtime()
        val current = engine?.snapshot()
        if (current != null && envelope.roomId != current.roomId) return
        val coordinatorNow =
            when {
                isCoordinator() -> clock.nowNs()
                clockSync.synchronized -> clockSync.coordinatorNowNs()
                else -> null
            }
        when (
            val acceptance =
                envelopeReplayProtector.evaluate(
                    socketPeerId = peerId,
                    envelope = envelope,
                    acceptedTerm = current?.term?.number,
                    lastAppliedSequence = current?.sequence,
                    coordinatorNowNs = coordinatorNow,
                )
        ) {
            EnvelopeAcceptance.Accepted -> Unit
            EnvelopeAcceptance.Duplicate -> return
            is EnvelopeAcceptance.SequenceGap -> {
                log.w(
                    TAG,
                    "Rejected sequence gap expected=${acceptance.expected} actual=${acceptance.actual}",
                )
                if (!isCoordinator() && current != null) {
                    sendToCoordinator(ProtocolBody.SnapshotRequest(current.sequence))
                }
                return
            }
            is EnvelopeAcceptance.Rejected -> {
                log.w(
                    TAG,
                    "Rejected envelope peer=${peerId.value.take(8)} reason=${acceptance.reason}",
                )
                return
            }
        }
        when (val body = envelope.body) {
            is ProtocolBody.JoinAccepted -> {
                if (isCoordinator() || peerId != coordinatorPeerId) return
                if (
                    body.snapshot.term.number != envelope.term ||
                        !validateIncomingSnapshot(
                            snapshot = body.snapshot,
                            expectedRoomId = envelope.roomId,
                            expectedCoordinatorPeerId = peerId,
                            minimumTerm = current?.term?.number,
                            minimumSequence = current?.sequence,
                        ) ||
                        !validatePeerDirectory(body.peerDirectory, body.snapshot)
                ) {
                    throw IllegalStateException("Invalid join acceptance")
                }
                joinTimeoutJob?.cancel()
                joinTimeoutJob = null
                joinAttemptJob?.cancel()
                joinAttemptJob = null
                pendingJoin = null
                resetClockSynchronization()
                clockReadyPeers.clear()
                engine = RoomEngine(body.snapshot)
                coordinatorPeerId = body.snapshot.term.coordinatorPeerId
                body.peerDirectory.forEach { peerDirectory[it.peerId] = it }
                lastSeenElapsedMs[body.snapshot.term.coordinatorPeerId] =
                    SystemClock.elapsedRealtime()
                announcedTrackIds.clear()
                recoveryJob?.cancel()
                recoveryJob = null

                // Publish the authenticated room and start liveness work before touching local
                // files. Playback preparation is recoverable and must never hold the
                // serialized room actor or make an otherwise valid join appear to time out.
                updateSnapshot(body.snapshot, RoomLifecycleState.CONNECTED, "Connected")
                startSessionJobs()
                launchSnapshotPreparation(body.snapshot, initialJoin = true)
            }

            is ProtocolBody.UserCommandRequest ->
                if (isCoordinator()) {
                    if (body.command.requestedBy != peerId) {
                        send(
                            peerId,
                            ProtocolBody.CommandRejected(
                                body.command.commandId,
                                "Invalid command identity",
                            ),
                        )
                    } else if (body.command.transportActionOrNull() != null) {
                        queueRemoteTransportCommand(body.command)
                    } else {
                        applyCoordinatorCommand(body.command)
                    }
                }

            is ProtocolBody.CommandStatus ->
                if (peerId == coordinatorPeerId && body.commandId.isNotBlank()) {
                    if (body.commandId in localTransportCommandIds) {
                        updateLocalTransportPhase(
                            commandId = body.commandId,
                            phase = body.phase,
                            message = body.message,
                            queueItemId = body.queueItemId,
                            requestedPositionMs = body.requestedPositionMs,
                            action = body.action,
                        )
                    }
                }

            is ProtocolBody.CommandRejected ->
                if (peerId == coordinatorPeerId && body.commandId.isNotBlank()) {
                    val currentStatus = container.roomStore.structure.value.transportStatus
                    if (currentStatus?.commandId == body.commandId) {
                        updateLocalTransportPhase(
                            body.commandId,
                            TransportCommandPhase.REJECTED,
                            body.reason,
                        )
                    } else {
                        setError(body.reason)
                    }
                }

            is ProtocolBody.PeerJoined -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.PeerUpdated -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.PeerLeft -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.QueueItemsAdded -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.QueueItemsRemoved -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.QueueItemMoved -> applyCanonicalEnvelope(envelope, body)
            ProtocolBody.QueueCleared -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.QueuePreparedSetChanged -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.QueueItemPreparationRequested -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.RoomOptionsChanged -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.PlaybackModeChanged -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.PlayScheduled -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.PauseScheduled -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.SeekScheduled -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.CurrentItemChanged -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.Snapshot -> {
                val before = engine?.snapshot()
                val expectedCoordinator =
                    before?.term?.coordinatorPeerId ?: coordinatorPeerId ?: return
                if (isCoordinator() || peerId != expectedCoordinator) return
                if (
                    !validateIncomingSnapshot(
                        snapshot = body.snapshot,
                        expectedRoomId = envelope.roomId,
                        expectedCoordinatorPeerId = peerId,
                        minimumTerm = before?.term?.number,
                        minimumSequence = before?.sequence,
                    )
                )
                    return
                if (before != null && body.snapshot.term.number > before.term.number) {
                    resetClockSynchronization()
                    playbackSync.reset(preserveLearnedBaseline = true)
                    latestPlaybackStateSync = null
                }
                val replaced =
                    engine?.replace(body.snapshot) ?: body.snapshot.also { engine = RoomEngine(it) }
                updateSnapshot(replaced)
                launchSnapshotPreparation(replaced, initialJoin = false)
            }

            is ProtocolBody.SnapshotRequest ->
                if (isCoordinator())
                    send(
                        peerId,
                        ProtocolBody.Snapshot(engine?.snapshot() ?: return),
                    )

            is ProtocolBody.PeerDirectory ->
                if (!isCoordinator() && peerId == coordinatorPeerId) {
                    val snapshot = engine?.snapshot() ?: return
                    if (!validatePeerDirectory(body.peers, snapshot)) {
                        log.w(TAG, "Rejected invalid peer directory")
                        return
                    }
                    peerDirectory.keys.retainAll(body.peers.map { it.peerId }.toSet())
                    body.peers.forEach { peerDirectory[it.peerId] = it }
                }

            is ProtocolBody.EndpointAnnouncement ->
                if (isCoordinator()) {
                    updatePeerEndpoint(peerId, body.endpoint)
                }

            is ProtocolBody.Heartbeat -> {
                val snapshot = engine?.snapshot()
                if (isCoordinator()) {
                    send(peerId, ProtocolBody.AckSequence(snapshot?.sequence ?: 0))
                } else if (
                    peerId == snapshot?.term?.coordinatorPeerId &&
                        body.lastAppliedSequence > snapshot.sequence
                ) {
                    sendToCoordinator(ProtocolBody.SnapshotRequest(snapshot.sequence))
                }
            }

            is ProtocolBody.AckSequence -> {
                val snapshot = engine?.snapshot()
                if (
                    !isCoordinator() &&
                        peerId == snapshot?.term?.coordinatorPeerId &&
                        body.sequence > snapshot.sequence
                ) {
                    sendToCoordinator(ProtocolBody.SnapshotRequest(snapshot.sequence))
                }
            }

            is ProtocolBody.ClockPing ->
                if (isCoordinator()) {
                    val receive = clock.nowNs()
                    send(
                        peerId,
                        ProtocolBody.ClockPong(
                            body.pingId,
                            body.guestSendNs,
                            receive,
                            clock.nowNs(),
                        ),
                    )
                }

            is ProtocolBody.ClockPong -> {
                if (isCoordinator() || peerId != coordinatorPeerId) return
                val wasSynchronized = clockSync.synchronized
                clockSync.recordPong(
                    body.pingId,
                    body.guestSendNs,
                    body.coordinatorReceiveNs,
                    body.coordinatorSendNs,
                    clock.nowNs(),
                )
                val nowNs = clock.nowNs()
                if (
                    clockSync.synchronized &&
                        (!wasSynchronized ||
                            nowNs - lastClockQualityReportNs >= CLOCK_QUALITY_REPORT_INTERVAL_NS)
                ) {
                    lastClockQualityReportNs = nowNs
                    sendToCoordinator(
                        ProtocolBody.ClockReady(
                            roundTripNs = clockSync.roundTripNs.takeIf { it != Long.MAX_VALUE },
                            uncertaintyNs = clockSync.uncertaintyNs,
                        )
                    )
                }
                if (!wasSynchronized && clockSync.synchronized) {
                    container.roomStore.update {
                        it.copy(status = UserFacingStatus.READY, statusMessage = "Ready")
                    }
                }
            }

            is ProtocolBody.ClockReady ->
                if (isCoordinator() && body.synchronized) {
                    clockReadyPeers.add(peerId)
                    body.roundTripNs
                        ?.takeIf { it in 0..MAX_REPORTED_CLOCK_RTT_NS }
                        ?.let {
                            clockRoundTripNs[peerId] = it
                        }
                    body.uncertaintyNs
                        ?.takeIf { it in 0..MAX_REPORTED_CLOCK_UNCERTAINTY_NS }
                        ?.let {
                            clockUncertaintyNs[peerId] = it
                        }
                    reevaluateAllPreparation()
                    val snapshot = engine?.snapshot()
                    if (snapshot != null) {
                        val now = clock.nowNs()
                        send(
                            peerId,
                            ProtocolBody.PlaybackStateSync(snapshot.playback.forStateSync(now)),
                        )
                    }
                }

            is ProtocolBody.PlaybackStateSync ->
                if (peerId == coordinatorPeerId) applyPlaybackSync(body.playback)
            is ProtocolBody.PlaybackStatusReport ->
                if (isCoordinator()) updateMemberPlayback(peerId, body)
            is ProtocolBody.MemberPlaybackStatus ->
                if (!isCoordinator() && peerId == coordinatorPeerId) {
                    applyEphemeralMemberPlayback(body)
                }

            is ProtocolBody.TrackHave -> if (isCoordinator()) onTrackHave(peerId, body.trackId)
            is ProtocolBody.TrackNeed -> if (isCoordinator()) onTrackNeed(peerId, body.trackId)
            is ProtocolBody.TrackSourceAssigned ->
                if (!isCoordinator() && peerId == coordinatorPeerId) onTrackSourceAssigned(body)

            is ProtocolBody.TrackSourceAuthorized ->
                if (isCoordinator()) onTrackSourceAuthorized(peerId, body)
            is ProtocolBody.TrackReady -> if (isCoordinator()) onTrackHave(peerId, body.trackId)
            is ProtocolBody.TrackFailed -> if (isCoordinator()) onTrackFailed(peerId, body)
            is ProtocolBody.LeaveRoom -> connections[peerId]?.close()
            is ProtocolBody.RejoinRequest ->
                if (isCoordinator()) {
                    send(peerId, ProtocolBody.Snapshot(engine?.snapshot() ?: return))
                    onTracksHaveInActor(peerId, body.cachedTrackIds.take(MAX_REJOIN_CACHE_IDS))
                }

            is ProtocolBody.TrackDescriptorMessage ->
                if (!isCoordinator() && peerId == coordinatorPeerId) {
                    announceLocalAvailability(body.descriptor)
                }

            is ProtocolBody.TransferCancelled -> Unit
        }
    }

    private suspend fun applyCanonicalEnvelope(envelope: Envelope, body: ProtocolBody) {
        val snapshot = engine?.snapshot() ?: return
        if (
            envelope.senderPeerId != snapshot.term.coordinatorPeerId ||
                envelope.term != snapshot.term.number
        )
            return
        val sequence = envelope.sequence ?: return
        if (sequence <= snapshot.sequence) return
        if (sequence != snapshot.sequence + 1) {
            // TCP preserves order within one connection, so a gap means this peer reconnected or
            // replaced state without receiving every mutation. Never build on a partial history.
            sendToCoordinator(ProtocolBody.SnapshotRequest(snapshot.sequence))
            return
        }
        val updated =
            engine?.applyValidated(sequence, body, ::snapshotFitsProtocol)
                ?: run {
                    log.w(
                        TAG,
                        "Rejected canonical mutation ${body::class.simpleName}: invalid resulting snapshot",
                    )
                    sendToCoordinator(ProtocolBody.SnapshotRequest(snapshot.sequence))
                    return
                }
        updateSnapshot(updated)
        playbackDispatcher.submit(body, updated)
    }

    private suspend fun announceLocalAvailability(track: TrackDescriptor) {
        if (!announcedTrackIds.add(track.trackId)) return
        val hasFile =
            suspendResult { container.trackRepository.requireReadableFile(track.trackId) != null }
                .onFailure { error ->
                    log.w(TAG, "Could not read ${track.trackId.value.take(8)}", error)
                }
                .getOrDefault(false)
        if (isCoordinator()) {
            if (hasFile) onTrackHave(identity.peerId, track.trackId)
            else onTrackNeed(identity.peerId, track.trackId)
        } else {
            sendToCoordinator(
                if (hasFile) ProtocolBody.TrackHave(track.trackId)
                else ProtocolBody.TrackNeed(track.trackId)
            )
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
        recordTrackAvailability(peerId, trackId)
        assignWaiting(trackId)
        reevaluatePreparation(trackId)
    }

    /** Applies reconnect cache knowledge in one actor turn and one preparation reconciliation. */
    private suspend fun onTracksHaveInActor(peerId: PeerId, trackIds: List<TrackId>) {
        val changedTrackIds =
            trackIds
                .asSequence()
                .distinct()
                .filter { trackId ->
                    recordTrackAvailability(peerId, trackId)
                }
                .toList()
        changedTrackIds.forEach { assignWaiting(it) }
        if (changedTrackIds.isNotEmpty()) reevaluateAllPreparation()
    }

    private fun recordTrackAvailability(peerId: PeerId, trackId: TrackId): Boolean {
        val changed =
            availability.computeIfAbsent(trackId) { ConcurrentHashMap.newKeySet() }.add(peerId)
        transferFailureCounts.keys.removeAll {
            it.startsWith("${trackId.value}:$peerId:") || it.endsWith(":$peerId")
        }
        waitingForSource[trackId]?.remove(peerId)
        return changed
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
        val destinations = waitingForSource[trackId]?.takeIf { it.isNotEmpty() }?.toList() ?: return
        val snapshot = engine?.snapshot() ?: return
        val descriptor =
            snapshot.queue.firstOrNull { it.track.trackId == trackId }?.track
                ?: container.trackRepository.get(trackId)
                ?: return
        val sources = availability[trackId].orEmpty()
        destinations.forEach { destination ->
            if (
                pendingTransferAssignments.values.any {
                    it.track.trackId == trackId && it.destinationPeerId == destination
                }
            )
                return@forEach
            val sourceId =
                sources.firstOrNull {
                    it != destination && (it == identity.peerId || connections.containsKey(it))
                } ?: return@forEach
            val source =
                if (sourceId == identity.peerId) localEndpoint()
                else peerDirectory[sourceId] ?: return@forEach
            val token = Crypto.randomBase64(24)
            val assignment =
                ProtocolBody.TrackSourceAssigned(descriptor, source, destination, token)
            val expiresAt = SystemClock.elapsedRealtime() + TRANSFER_TOKEN_LIFETIME_MS

            if (sourceId == identity.peerId) {
                transferManager?.authorize(snapshot.roomId, trackId, destination, token, expiresAt)
                deliverTransferAssignment(assignment)
            } else {
                pendingTransferAssignments[token] = assignment
                send(sourceId, assignment)
                launchSessionJob {
                    delay(SOURCE_AUTHORIZATION_TIMEOUT_MS)
                    val expired =
                        pendingTransferAssignments.remove(token) ?: return@launchSessionJob
                    waitingForSource
                        .computeIfAbsent(expired.track.trackId) { ConcurrentHashMap.newKeySet() }
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

    private suspend fun onTrackSourceAuthorized(
        peerId: PeerId,
        authorized: ProtocolBody.TrackSourceAuthorized,
    ) {
        val assignment = pendingTransferAssignments[authorized.authorizationToken] ?: return
        if (
            assignment.source.peerId != peerId ||
                assignment.track.trackId != authorized.trackId ||
                assignment.destinationPeerId != authorized.destinationPeerId
        )
            return
        if (!pendingTransferAssignments.remove(authorized.authorizationToken, assignment)) return
        deliverTransferAssignment(assignment)
    }

    private fun onTrackSourceAssigned(assignment: ProtocolBody.TrackSourceAssigned) {
        when (identity.peerId) {
            assignment.source.peerId -> {
                transferManager?.authorize(
                    container.roomStore.structure.value.snapshot?.roomId ?: return,
                    assignment.track.trackId,
                    assignment.destinationPeerId,
                    assignment.authorizationToken,
                    SystemClock.elapsedRealtime() + TRANSFER_TOKEN_LIFETIME_MS,
                )
                launchSessionJob {
                    sendToCoordinator(
                        ProtocolBody.TrackSourceAuthorized(
                            assignment.track.trackId,
                            assignment.destinationPeerId,
                            assignment.authorizationToken,
                        )
                    )
                }
            }

            assignment.destinationPeerId ->
                transferManager?.download(
                    roomId = container.roomStore.structure.value.snapshot?.roomId ?: return,
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
        waitingForSource
            .computeIfAbsent(failure.trackId) { ConcurrentHashMap.newKeySet() }
            .add(peerId)
        reevaluatePreparation(failure.trackId)
        launchSessionJob {
            delay(TRANSFER_RETRY_DELAY_MS)
            assignWaiting(failure.trackId)
        }
    }

    private fun canApplyScheduledCommand(): Boolean =
        roleEngine().canApplyScheduledCommand(clockSync.estimate(clock.nowNs()))

    private suspend fun onLocalTrackReady(descriptor: TrackDescriptor) {
        announcedTrackIds.add(descriptor.trackId)
        requestTimelineRefresh(engine?.snapshot() ?: return)
        if (isCoordinator()) onTrackHave(identity.peerId, descriptor.trackId)
        else sendToCoordinator(ProtocolBody.TrackReady(descriptor.trackId))
    }

    private fun launchSnapshotPreparation(snapshot: RoomSnapshot, initialJoin: Boolean) {
        timelineRefreshJob?.cancel()
        timelineRefreshJob = launchSessionJob {
            try {
                reconcileSnapshotQueue(snapshot)
                prepareWindow(snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val phase = if (initialJoin) "Initial" else "Snapshot"
                log.w(TAG, "$phase playback preparation failed", error)
                setError(
                    if (initialJoin) "Connected; some music may need to be prepared again"
                    else "Room restored; some music may need to be prepared again"
                )
            }
        }
    }

    private suspend fun reconcileSnapshotQueue(snapshot: RoomSnapshot) {
        playerMutations.maintenance { setRepeatCurrentItem(snapshot.repeatMode == RepeatMode.ONE) }
        val local = player.state.value
        val canonicalItem = snapshot.playback.queueItemId
        // A full snapshot is commonly received after reconnect. If this phone is still playing a
        // different item, keep that timeline until clock synchronization supplies an authoritative
        // PlaybackStateSync. This avoids both early future skips and abrupt unsynchronized jumps.
        if (
            local.queueItemId != null && canonicalItem != null && local.queueItemId != canonicalItem
        )
            return
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
        allowDeferredRetry: Boolean = true,
    ) {
        val windowQueue =
            PlaybackQueuePolicy.playerWindow(
                snapshot = snapshot,
                historyCount = PLAYER_HISTORY_ITEMS,
                upcomingCount = maxOf(snapshot.options.preloadCount + 2, PLAYER_UPCOMING_ITEMS),
            )
        val readable =
            withContext(Dispatchers.IO) {
                windowQueue.associate { item ->
                    val file =
                        suspendResult {
                                container.trackRepository.requireReadableFile(item.track.trackId)
                            }
                            .onFailure { error ->
                                log.w(
                                    TAG,
                                    "Could not load ${item.track.trackId.value.take(8)}",
                                    error,
                                )
                            }
                            .getOrNull()
                    item.track.trackId to file
                }
            }
        val allowedByRoom =
            PlaybackQueuePolicy.playableItems(
                snapshot = snapshot.copy(queue = windowQueue),
                readableTrackIds = readable.filterValues { it != null }.keys,
            )
        val playable = allowedByRoom.mapNotNull { item ->
            readable[item.track.trackId]?.let { audioFile ->
                LocalPlayableItem(
                    queueItemId = item.queueItemId,
                    track = item.track,
                    file = audioFile,
                )
            }
        }
        val localBeforeApply = player.state.value
        // Canonical playback changes are published before their synchronized execution time. A
        // preparation or availability refresh during that window may preload the target, but it
        // must not select it early. Protect the currently audible item until the scheduled
        // transport operation settles, then the timestamp-bound refresh selects canonical state.
        val protectedCurrentQueueItemId =
            preferredCurrentQueueItemId
                ?: localBeforeApply.queueItemId.takeIf { playerMutations.hasPendingTransport }
        val protectedPositionMs =
            preferredPositionMs
                ?: localBeforeApply.positionMs.takeIf { playerMutations.hasPendingTransport }
        val current =
            protectedCurrentQueueItemId?.takeIf { id -> playable.any { it.queueItemId == id } }
                ?: snapshot.playback.queueItemId?.takeIf { id ->
                    playable.any { it.queueItemId == id }
                }
                ?: playable.firstOrNull()?.queueItemId
        val currentPosition =
            when {
                current == protectedCurrentQueueItemId && protectedPositionMs != null ->
                    protectedPositionMs
                player.state.value.queueItemId == current -> player.state.value.positionMs
                else -> snapshot.playback.projectedPositionMs(clockSync.coordinatorNowNs())
            }
        val applied = playerMutations.maintenanceIfTransportIdle {
            setQueue(playable, current, currentPosition)
        }
        if (!applied && allowDeferredRetry) schedulePlayerMaintenanceRetry()
    }

    private fun schedulePlayerMaintenanceRetry() {
        if (playerMaintenanceRetryJob?.isActive == true) return
        val generation = sessionJobs.generation
        playerMaintenanceRetryJob = launchSessionJob {
            try {
                while (
                    isActive &&
                        sessionJobs.isCurrent(generation) &&
                        playerMutations.hasPendingTransport
                ) {
                    delay(PLAYER_MAINTENANCE_RETRY_INTERVAL_MS)
                }
                if (!sessionJobs.isCurrent(generation)) return@launchSessionJob
                engine?.snapshot()?.let { snapshot ->
                    refreshPlayerQueue(snapshot, allowDeferredRetry = false)
                }
            } finally {
                playerMaintenanceRetryJob = null
            }
        }
    }

    private suspend fun markTrackPlayed(snapshot: RoomSnapshot, queueItemId: QueueItemId) {
        snapshot.queue
            .firstOrNull { it.queueItemId == queueItemId }
            ?.track
            ?.trackId
            ?.let { trackId ->
                suspendResult { container.trackRepository.markPlayed(trackId) }
                    .onFailure { error -> log.w(TAG, "Could not update recent music", error) }
            }
    }

    /**
     * Coalesces queue/preparation bursts so adding a large playlist does one Media3 rebuild, not
     * one database/file pass per canonical mutation. Transport-timestamp reconciliation uses the
     * separate [scheduleQueueRefresh] path and is never delayed by this debounce.
     */
    private fun requestTimelineRefresh(fallbackSnapshot: RoomSnapshot) {
        timelineRefreshJob?.cancel()
        timelineRefreshJob = launchSessionJob {
            delay(TIMELINE_REFRESH_DEBOUNCE_MS)
            val snapshot =
                engine?.snapshot()?.takeIf { it.roomId == fallbackSnapshot.roomId }
                    ?: fallbackSnapshot
            refreshPlayerQueue(snapshot)
            prepareWindow(snapshot)
        }
    }

    private fun scheduleQueueRefresh(executeAtCoordinatorNs: Long) {
        queueRefreshJob?.cancel()
        queueRefreshJob = launchSessionJob {
            while (isActive) {
                if (!isCoordinator() && !clockSync.synchronized) {
                    delay(CLOCK_MAPPING_RECHECK_INTERVAL_MS)
                    continue
                }
                val targetLocalNs =
                    if (isCoordinator()) {
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

    private suspend fun prepareWindow(
        snapshot: RoomSnapshot,
        priorityQueueItemId: QueueItemId? = null,
    ) {
        val desiredItems =
            TrackPrefetchPolicy.prioritizedDesiredItems(
                snapshot = snapshot,
                priorityQueueItemId = priorityQueueItemId,
                upcomingCount =
                    snapshot.options.preloadCount.coerceAtMost(
                        TrackPrefetchPolicy.DEFAULT_UPCOMING_COUNT
                    ),
            )
        val nextDesired = desiredItems.mapTo(linkedSetOf()) { it.track.trackId }
        val progress = container.roomStore.transfers.value.transfers
        TrackPrefetchPolicy.cancellableObsoleteTracks(
                previousDesired = desiredPrefetchTrackIds,
                nextDesired = nextDesired,
                progressByTrack = progress,
            )
            .forEach { obsolete -> transferManager?.cancel(obsolete) }
        desiredPrefetchTrackIds = nextDesired
        desiredItems.forEach { item ->
            container.trackRepository.touchTemporary(item.track.trackId)
            announceLocalAvailability(item.track)
        }
    }

    private suspend fun reevaluatePreparation(trackId: TrackId) {
        if (!isCoordinator()) return
        val snapshot = engine?.snapshot() ?: return
        val activePeers =
            snapshot.members.filter { it.connected }.mapTo(mutableSetOf()) { it.peerId }
        val readyPeers = availability[trackId].orEmpty()
        val shouldPrepare =
            activePeers.isNotEmpty() &&
                readyPeers.containsAll(activePeers) &&
                clockReadyPeers.containsAll(activePeers)
        log.i(
            TAG,
            "Preparation track=${trackId.value.take(8)} ready=${readyPeers.size}/${activePeers.size} " +
                "clocks=${clockReadyPeers.count { it in activePeers }}/${activePeers.size} prepared=$shouldPrepare",
        )
        val affectedItems = snapshot.queue.filter { it.track.trackId == trackId }
        if (affectedItems.isEmpty()) return
        val affectedIds = affectedItems.mapTo(hashSetOf()) { it.queueItemId }
        val desiredPrepared =
            if (shouldPrepare) {
                snapshot.preparedQueueItemIds + affectedIds
            } else {
                snapshot.preparedQueueItemIds - affectedIds
            }
        if (desiredPrepared != snapshot.preparedQueueItemIds) {
            emitCanonical(ProtocolBody.QueuePreparedSetChanged(desiredPrepared))
        }
        affectedItems.forEach { item ->
            val coordinatorCanContinue =
                !snapshot.options.waitAtTrackBoundary && identity.peerId in readyPeers
            if (
                (shouldPrepare || coordinatorCanContinue) &&
                    pendingAutoResumeQueueItemId == item.queueItemId
            ) {
                val latest = engine?.snapshot() ?: return@forEach
                if (latest.playback.queueItemId == item.queueItemId && !latest.playback.isPlaying) {
                    pendingAutoResumeQueueItemId = null
                    emitCanonical(
                        ProtocolBody.CurrentItemChanged(
                            queueItemId = item.queueItemId,
                            positionMs = 0,
                            executeAtCoordinatorNs = clock.nowNs() + transportLeadNs(latest),
                            resumePlayback = true,
                        )
                    )
                }
            }
        }
        resumePendingTrackTransitionIfReady()
        resumePendingPlayIfReady()
        rejectPendingTrackTransitionIfUnavailable(snapshot, activePeers, trackId)
    }

    private suspend fun rejectPendingTrackTransitionIfUnavailable(
        snapshot: RoomSnapshot,
        activePeers: Set<PeerId>,
        trackId: TrackId,
    ) {
        val pending = pendingTrackTransitions.peek() ?: return
        val target =
            snapshot.queue.firstOrNull { it.queueItemId == pending.queueItemId }
                ?: run {
                    rejectPendingTrackTransition("The selected song is no longer in the queue")
                    return
                }
        if (target.track.trackId != trackId || activePeers.isEmpty()) return
        val availablePeers =
            availability[trackId].orEmpty().filterTo(hashSetOf()) { it in activePeers }
        if (availablePeers.isNotEmpty()) return
        val waitingPeers =
            waitingForSource[trackId].orEmpty().filterTo(hashSetOf()) { it in activePeers }
        if (!waitingPeers.containsAll(activePeers)) return
        rejectPendingTrackTransition("No connected phone has this song")
    }

    private suspend fun rejectPendingTrackTransition(message: String) {
        finishPendingTrackTransition(TransportCommandPhase.REJECTED, message)
    }

    private suspend fun supersedePendingTrackTransition(message: String) {
        finishPendingTrackTransition(TransportCommandPhase.SUPERSEDED, message)
    }

    private suspend fun finishPendingTrackTransition(
        phase: TransportCommandPhase,
        message: String,
    ): PendingTrackTransition? {
        val pending = pendingTrackTransitions.clear() ?: return null
        cancelPendingTrackTransitionJobs()
        publishTransportStatus(
            requestedBy = pending.requestedBy,
            commandId = pending.commandId,
            action = pending.action,
            phase = phase,
            queueItemId = pending.queueItemId,
            message = message,
        )
        return pending
    }

    private fun startPendingTrackPreparation(pending: PendingTrackTransition) {
        cancelPendingTrackTransitionJobs()
        val generation = sessionJobs.generation
        pendingTrackTransitionTimeoutJob = launchSessionJob {
            delay(PENDING_TRACK_PREPARATION_TIMEOUT_MS)
            roomEvents.submit(
                RoomEvent.PendingTrackTransitionTimedOut(
                    generation = generation,
                    commandId = pending.commandId,
                )
            )
        }
        pendingTrackAvailabilityProbeJob = launchSessionJob {
            val available =
                withContext(Dispatchers.IO) {
                    suspendResult {
                            container.trackRepository.requireReadableFile(pending.trackId) != null
                        }
                        .onFailure { error ->
                            log.w(
                                TAG,
                                "Could not probe ${pending.trackId.value.take(8)} for pending navigation",
                                error,
                            )
                        }
                        .getOrDefault(false)
                }
            roomEvents.submit(
                RoomEvent.PendingTrackAvailabilityProbed(
                    generation = generation,
                    commandId = pending.commandId,
                    queueItemId = pending.queueItemId,
                    trackId = pending.trackId,
                    available = available,
                )
            )
        }
    }

    private fun cancelPendingTrackTransitionJobs() {
        pendingTrackTransitionTimeoutJob?.cancel()
        pendingTrackTransitionTimeoutJob = null
        pendingTrackAvailabilityProbeJob?.cancel()
        pendingTrackAvailabilityProbeJob = null
    }

    private suspend fun processPendingTrackAvailabilityProbed(
        event: RoomEvent.PendingTrackAvailabilityProbed
    ) {
        if (!sessionJobs.isCurrent(event.generation)) return
        if (!pendingTrackTransitions.matches(event.commandId, event.queueItemId)) return
        pendingTrackAvailabilityProbeJob = null
        if (event.available) {
            onTrackHaveInActor(identity.peerId, event.trackId)
        } else {
            onTrackNeedInActor(identity.peerId, event.trackId)
        }
    }

    private suspend fun processPendingTrackTransitionTimedOut(
        event: RoomEvent.PendingTrackTransitionTimedOut
    ) {
        if (!sessionJobs.isCurrent(event.generation)) return
        val pending =
            pendingTrackTransitions.peek()?.takeIf { it.commandId == event.commandId } ?: return
        finishPendingTrackTransition(
            TransportCommandPhase.REJECTED,
            "Song preparation timed out. Try again or choose another song.",
        )
        if (pending.requestedBy == identity.peerId) {
            setIssue(
                RoomIssue(
                    code = RoomIssueCode.TRACK_PREPARATION_TIMED_OUT,
                    message = "This song took too long to prepare",
                    severity = RoomIssueSeverity.WARNING,
                    recoveryAction = RoomRecoveryAction.RETRY,
                    commandId = pending.commandId,
                    queueItemId = pending.queueItemId,
                    deduplicationKey = "track-preparation-timeout:${pending.queueItemId.value}",
                )
            )
        }
    }

    private suspend fun resumePendingTrackTransitionIfReady() {
        val pending = pendingTrackTransitions.peek() ?: return
        val snapshot = engine?.snapshot() ?: return
        if (pending.queueItemId !in snapshot.preparedQueueItemIds) return
        if (
            pending.requestedBy != identity.peerId &&
                snapshot.members.none { it.peerId == pending.requestedBy && it.connected }
        ) {
            finishPendingTrackTransition(
                TransportCommandPhase.SUPERSEDED,
                "The requesting phone left the room",
            )
            return
        }
        pendingTrackTransitions.clearIfCommand(pending.commandId)
        cancelPendingTrackTransitionJobs()
        recentCommandIds.remove(pending.commandId)
        log.i(
            TAG,
            "Prepared pending transition command=${pending.commandId.take(8)} " +
                "item=${pending.queueItemId.value.take(8)}",
        )
        applyCoordinatorCommand(
            UserCommand.PlayQueueItem(
                commandId = pending.commandId,
                requestedBy = pending.requestedBy,
                queueItemId = pending.queueItemId,
                resumePlayback = pending.resumePlayback,
            )
        )
    }

    private suspend fun setPendingPlayCommand(command: UserCommand.Play, queueItemId: QueueItemId) {
        val previous = pendingPlayCommand
        val previousQueueItemId = pendingPlayQueueItemId
        clearPendingPlayCommand()
        if (previous != null && previous.commandId != command.commandId) {
            publishTransportStatus(
                requestedBy = previous.requestedBy,
                commandId = previous.commandId,
                action = TransportAction.PLAY,
                phase = TransportCommandPhase.SUPERSEDED,
                queueItemId = previousQueueItemId,
                message = "Replaced by a newer Play request",
            )
        }
        pendingPlayCommand = command
        pendingPlayQueueItemId = queueItemId
        val generation = sessionJobs.generation
        pendingPlayTimeoutJob = launchSessionJob {
            delay(PENDING_TRACK_PREPARATION_TIMEOUT_MS)
            roomEvents.submit(RoomEvent.PendingPlayTimedOut(generation, command.commandId))
        }
    }

    private fun clearPendingPlayCommand(): UserCommand.Play? {
        val pending = pendingPlayCommand
        pendingPlayCommand = null
        pendingPlayQueueItemId = null
        pendingPlayTimeoutJob?.cancel()
        pendingPlayTimeoutJob = null
        return pending
    }

    private suspend fun supersedePendingPlayCommand(message: String) {
        val queueItemId = pendingPlayQueueItemId
        val pending = clearPendingPlayCommand() ?: return
        publishTransportStatus(
            requestedBy = pending.requestedBy,
            commandId = pending.commandId,
            action = TransportAction.PLAY,
            phase = TransportCommandPhase.SUPERSEDED,
            queueItemId = queueItemId,
            message = message,
        )
    }

    private suspend fun processPendingPlayTimedOut(event: RoomEvent.PendingPlayTimedOut) {
        if (!sessionJobs.isCurrent(event.generation)) return
        val pending = pendingPlayCommand?.takeIf { it.commandId == event.commandId } ?: return
        val queueItemId = pendingPlayQueueItemId
        clearPendingPlayCommand()
        publishTransportStatus(
            requestedBy = pending.requestedBy,
            commandId = pending.commandId,
            action = TransportAction.PLAY,
            phase = TransportCommandPhase.REJECTED,
            queueItemId = queueItemId,
            message = "Song preparation timed out. Try again or choose another song.",
        )
        if (pending.requestedBy == identity.peerId) {
            setIssue(
                RoomIssue(
                    code = RoomIssueCode.TRACK_PREPARATION_TIMED_OUT,
                    message = "This song took too long to prepare",
                    severity = RoomIssueSeverity.WARNING,
                    recoveryAction = RoomRecoveryAction.RETRY,
                    commandId = pending.commandId,
                    queueItemId = queueItemId,
                    deduplicationKey =
                        "play-preparation-timeout:${queueItemId?.value ?: "current"}",
                )
            )
        }
    }

    private suspend fun resumePendingPlayIfReady() {
        val pending = pendingPlayCommand ?: return
        val requester = pending.requestedBy
        val snapshot = engine?.snapshot() ?: return
        if (
            requester != identity.peerId &&
                snapshot.members.none { it.peerId == requester && it.connected }
        ) {
            val queueItemId = pendingPlayQueueItemId
            clearPendingPlayCommand()
            publishTransportStatus(
                requestedBy = pending.requestedBy,
                commandId = pending.commandId,
                action = TransportAction.PLAY,
                phase = TransportCommandPhase.SUPERSEDED,
                queueItemId = queueItemId,
                message = "The requesting phone left the room",
            )
            return
        }
        val current =
            snapshot.playback.queueItemId?.let { id ->
                snapshot.queue.firstOrNull { it.queueItemId == id }
            } ?: snapshot.queue.firstOrNull() ?: return
        if (
            snapshot.options.waitAtTrackBoundary &&
                current.queueItemId !in snapshot.preparedQueueItemIds
        )
            return
        clearPendingPlayCommand()
        log.i(
            TAG,
            "Preparation complete; retrying deferred Play command=${pending.commandId.take(8)} " +
                "item=${current.queueItemId.value.take(8)}",
        )
        recentCommandIds.remove(pending.commandId)
        applyCoordinatorCommand(pending)
    }

    private suspend fun reevaluateAllPreparation() {
        val snapshot = engine?.snapshot() ?: return
        if (!isCoordinator()) return
        val activePeers = snapshot.members.filter { it.connected }.mapTo(hashSetOf()) { it.peerId }
        val clocksReady = activePeers.isNotEmpty() && clockReadyPeers.containsAll(activePeers)
        val desired =
            if (!clocksReady) {
                emptySet()
            } else {
                snapshot.queue
                    .asSequence()
                    .filter { item ->
                        availability[item.track.trackId].orEmpty().containsAll(activePeers)
                    }
                    .mapTo(linkedSetOf()) { it.queueItemId }
            }
        if (desired != snapshot.preparedQueueItemIds) {
            emitCanonical(ProtocolBody.QueuePreparedSetChanged(desired))
        }
        pendingAutoResumeQueueItemId?.let { pendingId ->
            val latest = engine?.snapshot() ?: return@let
            if (
                pendingId in latest.preparedQueueItemIds &&
                    latest.playback.queueItemId == pendingId &&
                    !latest.playback.isPlaying
            ) {
                pendingAutoResumeQueueItemId = null
                emitCanonical(
                    ProtocolBody.CurrentItemChanged(
                        queueItemId = pendingId,
                        positionMs = 0,
                        executeAtCoordinatorNs = clock.nowNs() + transportLeadNs(latest),
                        resumePlayback = true,
                    )
                )
            }
        }
        resumePendingTrackTransitionIfReady()
        resumePendingPlayIfReady()
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
        val updated =
            engine?.applyValidated(sequence, body, ::snapshotFitsProtocol)
                ?: run {
                    log.w(
                        TAG,
                        "Rejected local mutation ${body::class.simpleName}: invalid resulting snapshot",
                    )
                    return
                }
        updateSnapshot(updated)
        broadcastCanonical(sequence, body)
        playbackDispatcher.submit(body, updated)
    }

    private suspend fun recoverFromPlayerTransitionLoop() {
        log.e(TAG, "Automatic item-transition circuit breaker tripped")
        scheduler.cancel("Unstable automatic track switching")
        playerMutations.maintenance {
            pause()
            setPlaybackSpeed(1f)
        }
        val snapshot = engine?.snapshot()
        val currentItem = snapshot?.playback?.queueItemId
        if (snapshot != null && currentItem != null && snapshot.playback.isPlaying) {
            val nowNs = clock.nowNs()
            emitCanonical(
                ProtocolBody.PauseScheduled(
                    queueItemId = currentItem,
                    positionMs = snapshot.playback.projectedPositionMs(nowNs),
                    executeAtCoordinatorNs = nowNs,
                )
            )
        }
        setIssue(
            RoomIssue(
                code = RoomIssueCode.PLAYBACK_UNSTABLE,
                message = "Playback was paused after unstable track switching",
                severity = RoomIssueSeverity.WARNING,
                recoveryAction = RoomRecoveryAction.RETRY,
                deduplicationKey = "playback-transition-loop",
            )
        )
    }

    private suspend fun recordNaturalTrackTransition(queueItemId: QueueItemId, positionMs: Long) {
        val snapshot = engine?.snapshot() ?: return
        if (!snapshot.playback.isPlaying || snapshot.playback.queueItemId == queueItemId) return
        if (snapshot.queue.none { it.queueItemId == queueItemId }) return
        val startTime = clock.nowNs() - positionMs.coerceAtLeast(0) * 1_000_000L
        emitCanonical(ProtocolBody.CurrentItemChanged(queueItemId, 0, startTime, true))
    }

    private suspend fun recordNaturalRepeatTransition(queueItemId: QueueItemId, positionMs: Long) {
        val mutation =
            PlaybackQueuePolicy.planRepeatTransition(
                snapshot = engine?.snapshot() ?: return,
                repeatedQueueItemId = queueItemId,
                positionMs = positionMs,
                coordinatorNowNs = clock.nowNs(),
            ) ?: return
        emitCanonical(mutation)
    }

    private suspend fun recordNaturalPlaybackEnded(
        queueItemId: QueueItemId,
        positionMs: Long,
        durationMs: Long,
    ) {
        val plan =
            PlaybackQueuePolicy.planNaturalEnd(
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
        latestPlaybackStateSync = canonical
        if (!clockSync.synchronized) return
        val queueItem = canonical.queueItemId ?: return
        val coordinatorNow = clockSync.coordinatorNowNs()
        val scheduledForFuture =
            canonical.coordinatorTimestampNs > coordinatorNow + FUTURE_COMMAND_TOLERANCE_NS
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
        when (
            PlaybackIntentReconciliationPolicy.decide(
                canonicalPlaying = canonical.isPlaying,
                localPlayWhenReady = localState.playWhenReady,
                locallySuppressed = localState.locallySuppressed,
            )
        ) {
            PlaybackIntentReconciliationPolicy.Action.PLAY -> playerMutations.synchronize { play() }
            PlaybackIntentReconciliationPolicy.Action.PAUSE ->
                playerMutations.synchronize { pause() }
            PlaybackIntentReconciliationPolicy.Action.NONE -> Unit
        }
    }

    private suspend fun runPlaybackSynchronizationTick(
        snapshot: RoomSnapshot,
        coordinator: Boolean,
    ) {
        val sample = player.samplePlayback()
        // The canonical room timeline is independent of every physical player. Coordinator and
        // participants run the same correction controller against that timeline; role only
        // determines which monotonic clock maps the canonical timestamp.
        val canonical =
            if (coordinator) snapshot.playback else latestPlaybackStateSync ?: snapshot.playback
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
            clockEstimate =
                ClockEstimate(
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

        val futureCommand =
            canonical.coordinatorTimestampNs > sampleCoordinatorNs + FUTURE_COMMAND_TOLERANCE_NS
        val decision =
            if (futureCommand) {
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
                        // Every device corrects its audible output to the same canonical timeline.
                        // Route latency therefore applies equally to the coordinator and
                        // participants.
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
            canonicalPositionMs =
                if (futureCommand) null else canonical.projectedPositionMs(sampleCoordinatorNs),
            clockEstimate = clockEstimate,
            decision = decision,
        )

        if (
            sample.sampledAtLocalNs - lastPlaybackStatusReportNs >=
                PLAYBACK_STATUS_REPORT_INTERVAL_NS
        ) {
            lastPlaybackStatusReportNs = sample.sampledAtLocalNs
            val report =
                ProtocolBody.PlaybackStatusReport(
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
        if (!isCoordinator()) resetClockSynchronization()
        playbackSync.reset(preserveLearnedBaseline = false)
        playbackSpeedGate.reset()
        syncDiagnostics.clear()
        latestPlaybackStateSync = if (isCoordinator()) engine?.snapshot()?.playback else null
        lastPlaybackReferenceBroadcastNs = 0L
        lastPlaybackStatusReportNs = 0L
        container.roomStore.updatePlayback { it.copy(localDriftMs = null) }
        val actualSpeed = player.state.value.playbackSpeed
        if (abs(actualSpeed - 1f) > PLAYBACK_SPEED_EPSILON) {
            playerMutations.synchronize { setPlaybackSpeed(1f) }
        }
        log.i(TAG, "Synchronization reacquisition required reason=$reason")
    }

    private suspend fun applyPlaybackSyncDecision(
        decision: PlaybackSyncDecision,
        actualSpeed: Float,
    ) {
        when (val action = decision.action) {
            is SyncAction.SetSpeed -> applyPlaybackSpeedTarget(action.speed, actualSpeed)
            is SyncAction.Seek -> {
                applyPlaybackSpeedTarget(decision.baselineSpeed, actualSpeed)
                playerMutations.synchronize { seekTo(action.positionMs) }
            }
            is SyncAction.Hold -> applyPlaybackSpeedTarget(action.baselineSpeed, actualSpeed)
        }
    }

    private suspend fun applyPlaybackSpeedTarget(targetSpeed: Float, actualSpeed: Float) {
        playbackSpeedGate
            .select(
                requestedSpeed = targetSpeed,
                actualSpeed = actualSpeed,
                nowNs = clock.nowNs(),
            )
            ?.let { selected -> playerMutations.synchronize { setPlaybackSpeed(selected) } }
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
        val actionName =
            when (decision.action) {
                is SyncAction.SetSpeed -> "SET_SPEED"
                is SyncAction.Seek -> "SEEK"
                is SyncAction.Hold -> "HOLD"
            }
        syncDiagnostics.record(
            SynchronizationEvent(
                timestampLocalNs = sampleAtLocalNs,
                timestampCoordinatorNs = sampleCoordinatorNs,
                deviceId = identity.peerId.value.take(12),
                deviceModel = diagnosticDeviceModel,
                androidVersion = diagnosticAndroidVersion,
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
                clockUncertaintyMs =
                    clockEstimate.uncertaintyNs.takeIf { it != Long.MAX_VALUE }?.div(1_000_000.0),
                clockState = clockEstimate.state.name,
                playbackSyncState = decision.state.name,
                action = actionName,
                actionReason = decision.reason,
                hardSeekCount = decision.hardSeekCount,
                buffering = buffering,
            )
        )
    }

    private suspend fun updateMemberPlayback(
        peerId: PeerId,
        report: ProtocolBody.PlaybackStatusReport,
    ) {
        val snapshot = engine?.snapshot() ?: return
        if (snapshot.members.none { it.peerId == peerId }) return
        val status =
            ProtocolBody.MemberPlaybackStatus(
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
                memberPlayback =
                    state.memberPlayback +
                        (status.peerId to
                            com.darius.unison.model.MemberPlaybackTelemetry(
                                positionMs = status.positionMs,
                                driftMs = status.driftMs,
                            ))
            )
        }
    }

    private fun refreshPowerLocks() {
        val structure = container.roomStore.structure.value
        val demand = RoomPowerPolicy.evaluate(sessionActive = structure.sessionActive)
        if (demand.wifi) wifiLocks.acquireWifi() else wifiLocks.releaseWifi()
        wifiLocks.setCpuRequired(demand.cpu)
        wifiLocks.refresh()
    }

    private fun startSessionJobs() {
        val generation = sessionJobs.generation
        heartbeatLiveness.reset()
        refreshPowerLocks()
        heartbeatJob?.cancel()
        clockSyncJob?.cancel()
        syncJob?.cancel()
        retentionRefreshJob?.cancel()
        heartbeatJob = launchSessionJob {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                submitSessionEvent(generation, RoomEvent.HeartbeatTick)
            }
        }
        // Converge the guest clock before the first user command, then keep it fresh.
        clockSyncJob = launchSessionJob {
            while (isActive) {
                // The coordinator already owns room time. Do not flood the serialized actor with
                // no-op clock requests; promotion/demotion is observed dynamically each cycle.
                if (!isCoordinator()) submitSessionEvent(generation, RoomEvent.ClockSyncTick)
                delay(
                    if (isCoordinator() || clockSync.synchronized) {
                        CLOCK_SYNC_STEADY_INTERVAL_MS
                    } else {
                        CLOCK_SYNC_WARMUP_INTERVAL_MS
                    }
                )
            }
        }
        syncJob = launchSessionJob {
            while (isActive) {
                val snapshot = engine?.snapshot()
                val playerState = player.state.value
                val intervalMs =
                    PlaybackSyncCadencePolicy.intervalMs(
                        queueItemPresent = snapshot?.playback?.queueItemId != null,
                        canonicalPlaying = snapshot?.playback?.isPlaying == true,
                        scheduledCommandPresent = playerMutations.hasPendingTransport,
                        localBuffering = playerState.buffering,
                        syncState = playbackSync.state,
                    )
                if (intervalMs == null) {
                    // Deliberately paused and empty rooms have no synchronization work. Reset the
                    // discontinuity baseline so an intentional suspension cannot be diagnosed as
                    // scheduler starvation when playback later resumes.
                    lastPlaybackSyncTickLocalNs = 0L
                    delay(PlaybackSyncCadencePolicy.SUSPENDED_RECHECK_INTERVAL_MS)
                    continue
                }
                delay(intervalMs)
                submitSessionEvent(generation, RoomEvent.PlaybackSyncTick)
            }
        }
        retentionRefreshJob = launchSessionJob {
            while (isActive) {
                delay(TEMPORARY_RETENTION_REFRESH_INTERVAL_MS)
                refreshTemporaryRetention()
            }
        }
    }

    private suspend fun processHeartbeatTick() {
        val snapshot = engine?.snapshot() ?: return
        val nowMs = SystemClock.elapsedRealtime()
        refreshPowerLocks()
        val enforceTimeouts = heartbeatLiveness.onTick(nowMs)
        if (isCoordinator()) {
            broadcast(ProtocolBody.Heartbeat(snapshot.sequence))
            if (enforceTimeouts) {
                val cutoff = nowMs - PEER_TIMEOUT_MS
                connections.keys
                    .filter { (lastSeenElapsedMs[it] ?: 0L) < cutoff }
                    .forEach {
                        connections[it]?.close(IllegalStateException("Peer heartbeat timed out"))
                    }
            }
        } else {
            sendToCoordinator(ProtocolBody.Heartbeat(snapshot.sequence))
            val coordinator = coordinatorPeerId
            if (
                enforceTimeouts &&
                    coordinator != null &&
                    (lastSeenElapsedMs[coordinator] ?: nowMs) < nowMs - PEER_TIMEOUT_MS
            ) {
                coordinatorConnection?.close(
                    IllegalStateException("Coordinator heartbeat timed out")
                )
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
        val snapshot = engine?.snapshot() ?: return
        val coordinator = isCoordinator()
        val now = clock.nowNs()
        val previousTick = lastPlaybackSyncTickLocalNs
        lastPlaybackSyncTickLocalNs = now
        if (previousTick > 0L && now - previousTick > LIFECYCLE_DISCONTINUITY_NS) {
            resetSynchronizationAfterDiscontinuity("scheduler_delay")
            return
        }
        runPlaybackSynchronizationTick(snapshot, coordinator)
        if (
            coordinator &&
                now - lastPlaybackReferenceBroadcastNs >=
                    playbackSync.config.referenceIntervalMs * 1_000_000L
        ) {
            lastPlaybackReferenceBroadcastNs = now
            val reference = snapshot.playback.forStateSync(now)
            broadcast(ProtocolBody.PlaybackStateSync(reference))
        }
    }

    private suspend fun refreshTemporaryRetention() {
        val snapshot = engine?.snapshot() ?: return
        TrackPrefetchPolicy.desiredItems(snapshot).forEach { item ->
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
        val member =
            MemberSnapshot(
                connection.peerId,
                connection.endpoint.displayName,
                connection.endpoint,
            )
        val snapshot = engine?.snapshot() ?: return
        val sequence = snapshot.sequence + 1
        val joined = ProtocolBody.PeerJoined(member)
        val updated =
            engine?.applyValidated(sequence, joined, ::snapshotFitsProtocol)
                ?: run {
                    peerDirectory.remove(connection.peerId)
                    connection.close(IllegalStateException("Room state capacity exceeded"))
                    return
                }
        updateSnapshot(updated)
        val joinAccepted =
            envelope(
                ProtocolBody.JoinAccepted(updated, peerDirectory.values.toList()),
                sequence = null,
            )
        if (!connection.trySend(joinAccepted)) {
            connection.close(IllegalStateException("Guaranteed control queue is full"))
            return
        }
        broadcastCanonical(sequence, joined, except = connection.peerId)
        reevaluateAllPreparation()
        broadcast(ProtocolBody.PeerDirectory(peerDirectory.values.toList()))
        TrackPrefetchPolicy.desiredItems(updated).forEach { track ->
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
            resetClockSynchronization()
            playbackSync.reset(preserveLearnedBaseline = true)
            latestPlaybackStateSync = null
            if (engine == null) {
                joinTimeoutJob?.cancel()
                joinTimeoutJob = null
                connections.remove(peerId, connection)
                coordinatorPeerId = null
                roomSecret?.fill(0)
                roomSecret = null
                roomPin = null
                val pending = pendingJoin
                val failure = cause ?: IllegalStateException("Connection closed before joining")
                if (pending != null) {
                    processInitialJoinFailed(
                        RoomEvent.InitialJoinFailed(
                            generation = pending.generation,
                            attempt = pending.attemptsStarted,
                            error = failure,
                        )
                    )
                } else {
                    setFailure(userFacingJoinFailure(failure))
                }
                return
            }
            container.roomStore.update {
                it.copy(
                    lifecycle = RoomLifecycleState.RECONNECTING,
                    status = UserFacingStatus.RECONNECTING,
                    statusMessage = "Reconnecting…",
                )
            }
            refreshPowerLocks()
            recoveryJob?.cancel()
            recoveryJob = launchSessionJob { attemptReconnectThenRecover(peerId) }
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
        val generation = sessionJobs.generation
        val snapshot = engine?.snapshot() ?: return
        val endpoint = peerDirectory[lostCoordinator]
        val secret = roomSecret?.copyOf()
        if (endpoint == null || secret == null) {
            roomEvents.submit(RoomEvent.ReconnectExhausted(generation, lostCoordinator))
            return
        }
        try {
            val networkDeadline =
                SystemClock.elapsedRealtime() + RoomReconnectPolicy.NETWORK_GRACE_MS
            var waitingMessageShown = false
            while (
                sessionJobs.isCurrent(generation) &&
                    selectedLocalAddress() == null &&
                    SystemClock.elapsedRealtime() < networkDeadline
            ) {
                if (!waitingMessageShown) {
                    waitingMessageShown = true
                    container.roomStore.update {
                        it.copy(
                            lifecycle = RoomLifecycleState.RECONNECTING,
                            status = UserFacingStatus.RECONNECTING,
                            statusMessage = "Waiting for Wi-Fi…",
                        )
                    }
                }
                delay(RoomReconnectPolicy.NETWORK_POLL_MS)
            }
            if (!sessionJobs.isCurrent(generation)) return

            for (attempt in 1..RoomReconnectPolicy.MAX_ATTEMPTS) {
                delay(RoomReconnectPolicy.delayBeforeAttemptMs(attempt))
                if (!sessionJobs.isCurrent(generation)) return
                container.roomStore.update {
                    it.copy(
                        lifecycle = RoomLifecycleState.RECONNECTING,
                        status = UserFacingStatus.RECONNECTING,
                        statusMessage =
                            "Reconnecting ($attempt/${RoomReconnectPolicy.MAX_ATTEMPTS})…",
                    )
                }
                val attemptStartedAtMs = SystemClock.elapsedRealtime()
                val reconnectResult = suspendResult {
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
                }
                val result = reconnectResult.getOrNull()
                if (result == null) {
                    val error =
                        reconnectResult.exceptionOrNull()
                            ?: IllegalStateException("Reconnect failed without an error")
                    log.w(
                        TAG,
                        "Reconnect failed attempt=$attempt " +
                            "durationMs=${SystemClock.elapsedRealtime() - attemptStartedAtMs} " +
                            "type=${error::class.simpleName}",
                        error,
                    )
                    continue
                }
                log.i(
                    TAG,
                    "Reconnect socket restored attempt=$attempt " +
                        "durationMs=${SystemClock.elapsedRealtime() - attemptStartedAtMs}",
                )

                val cached = buildList {
                    for (trackId in snapshot.queue.map { it.track.trackId }.distinct()) {
                        if (container.trackRepository.hasVerifiedSource(trackId)) add(trackId)
                        if (size == MAX_REJOIN_CACHE_IDS) break
                    }
                }
                roomEvents.submit(
                    RoomEvent.ReconnectSucceeded(
                        generation = generation,
                        expectedCoordinatorPeerId = lostCoordinator,
                        connected = result,
                        lastSequence = snapshot.sequence,
                        cachedTrackIds = cached,
                    )
                )
                return
            }
            roomEvents.submit(RoomEvent.ReconnectExhausted(generation, lostCoordinator))
        } finally {
            secret.fill(0)
        }
    }

    private suspend fun processReconnectSucceeded(event: RoomEvent.ReconnectSucceeded) {
        recoveryJob = null
        if (!sessionJobs.isCurrent(event.generation) || engine == null) {
            event.connected.connection.closeSilently()
            event.connected.roomSecret.fill(0)
            return
        }
        if (event.connected.coordinatorPeerId != event.expectedCoordinatorPeerId) {
            event.connected.connection.closeSilently()
            event.connected.roomSecret.fill(0)
            recoveryJob = launchSessionJob { beginCoordinatorRecovery() }
            return
        }
        val previousSecret = roomSecret
        coordinatorPeerId = event.connected.coordinatorPeerId
        coordinatorConnection = event.connected.connection
        roomSecret = event.connected.roomSecret
        if (previousSecret !== event.connected.roomSecret) previousSecret?.fill(0)
        connections[event.connected.coordinatorPeerId] = event.connected.connection
        event.connected.connection.start()
        lastSeenElapsedMs[event.connected.coordinatorPeerId] = SystemClock.elapsedRealtime()
        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.RECONNECTING,
                status = UserFacingStatus.RECONNECTING,
                statusMessage = "Restoring room state…",
                errorMessage = null,
                issue = null,
            )
        }
        refreshPowerLocks()
        val request =
            envelope(
                ProtocolBody.RejoinRequest(
                    lastAppliedSequence = event.lastSequence,
                    cachedTrackIds = event.cachedTrackIds,
                    listeningPort = server.port,
                )
            )
        if (!event.connected.connection.trySend(request)) {
            event.connected.connection.close(
                IllegalStateException("Guaranteed control queue is full")
            )
        }
    }

    /**
     * Best-effort deterministic election. Everyone has equal rights; lowest peer ID is only a
     * tie-breaker.
     */
    private suspend fun beginCoordinatorRecovery() {
        delay(ELECTION_DELAY_MS)
        val snapshot = engine?.snapshot() ?: return
        val lostCoordinator = coordinatorPeerId
        val connectedCandidates =
            snapshot.members
                .filter {
                    (it.connected || it.peerId == identity.peerId) && it.peerId != lostCoordinator
                }
                .map { it.peerId }
        val winner =
            (connectedCandidates + identity.peerId).distinct().minByOrNull { it.value }
                ?: identity.peerId
        if (winner == identity.peerId) {
            val local = localEndpoint()
            val promoted =
                snapshot.copy(
                    term = CoordinatorTerm(snapshot.term.number + 1, identity.peerId),
                    // Old guest-to-coordinator sockets are gone. Mark every remote peer
                    // disconnected
                    // until it actually reconnects to the elected coordinator; otherwise
                    // preparation
                    // waits forever for peers that are not connected to this coordinator yet.
                    members =
                        snapshot.members.map {
                            if (it.peerId == identity.peerId)
                                it.copy(connected = true, endpoint = local)
                            else it.copy(connected = false)
                        },
                )
            if (!snapshotFitsProtocol(promoted)) {
                setFailure("Recovered room state was invalid")
                return
            }
            val replacementPin = Crypto.randomFourDigitPin()
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
                onError = ::setError,
            )
            updateSnapshot(promoted, RoomLifecycleState.CONNECTED, "Connection restored")
            // Promotion changes the monotonic-clock mapping, not the canonical timeline model.
            // Clear speed/baseline state learned against the previous coordinator before resuming.
            resetSynchronizationAfterDiscontinuity("coordinator_promotion")
            // Existing guest-to-old-coordinator sockets are gone. NSD/direct reconnect brings peers
            // back.
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
                val connected =
                    suspendResult {
                            controlClient.reconnectWithRoomSecret(
                                identity,
                                snapshot.roomId,
                                endpoint.hostAddress,
                                endpoint.port,
                                server.port,
                                secret,
                                BuildConfig.VERSION_NAME,
                                ::enqueueEnvelope,
                                ::enqueueControlClosed,
                            )
                        }
                        .getOrNull()
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

    private fun launchSessionJob(block: suspend CoroutineScope.() -> Unit): Job =
        sessionJobs.launch {
            block()
        }

    private suspend fun submitSessionEvent(generation: Long, event: RoomEvent) {
        if (sessionJobs.isCurrent(generation)) roomEvents.submit(event)
    }

    private suspend fun leaveRoom() {
        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.ENDING,
                status = UserFacingStatus.PREPARING,
                statusMessage = "Ending room…",
                errorMessage = null,
                issue = null,
            )
        }
        suspendResult { sendToCoordinator(ProtocolBody.LeaveRoom("left")) }
        resetSession(keepDiscovery = false)
        hotspot.stop()
        playerMutations.invalidateTransport()
        playerMutations.maintenance {
            pause()
            setRepeatCurrentItem(false)
            setQueue(emptyList(), null, 0)
        }
        container.roomStore.reset(preserveHotspot = false)
    }

    private suspend fun resetSession(keepDiscovery: Boolean) {
        // No command may disappear merely because the session is ending. Publish terminal phases
        // before sockets and actor-owned preparation jobs are torn down.
        supersedePendingTrackTransition("Room session ended")
        supersedePendingPlayCommand("Room session ended")
        supersedeAllTransportCommands("Room session ended")
        val startedAtMs = SystemClock.elapsedRealtime()
        val closingTransferManager = transferManager
        val transferCountBeforeShutdown = closingTransferManager?.activeTransferCount ?: 0
        val playbackMetrics = playbackDispatcher.metrics()
        val closingConnections =
            (connections.values + listOfNotNull(coordinatorConnection)).distinct()
        closingConnections.forEach(ControlConnection::closeSilently)
        closingTransferManager?.cancelAll()
        sessionJobs.advanceAndCancel(SESSION_SHUTDOWN_TIMEOUT_MS)
        withTimeoutOrNull(SESSION_SHUTDOWN_TIMEOUT_MS) {
            closingTransferManager?.cancelAllAndJoin(SESSION_SHUTDOWN_TIMEOUT_MS)
            closingConnections.forEach { it.closeAndJoin(notifyClosed = false) }
        }
        resetSessionState(keepDiscovery)
        log.i(
            TAG,
            "Session shutdown durationMs=${SystemClock.elapsedRealtime() - startedAtMs} " +
                "connections=${closingConnections.size} transfers=$transferCountBeforeShutdown " +
                "remainingJobs=${sessionJobs.activeJobCount} logBacklog=${log.pendingLineCount} " +
                "droppedLogs=${log.droppedLineCount} playbackExact=${playbackMetrics.exactApplied}/" +
                "${playbackMetrics.exactSubmitted} playbackReconcile=${playbackMetrics.reconciliationApplied}/" +
                "${playbackMetrics.reconciliationSubmitted} collapsed=${playbackMetrics.reconciliationCollapsed} " +
                "skipped=${playbackMetrics.reconciliationSkipped} playbackFailures=${playbackMetrics.failures}",
        )
        playbackDispatcher.resetMetrics()
    }

    private suspend fun supersedeAllTransportCommands(message: String) {
        transportCommands.activeRoutes().forEach { (commandId, route) ->
            publishTransportStatus(
                requestedBy = route.requestedBy,
                commandId = commandId,
                action = route.action,
                phase = TransportCommandPhase.SUPERSEDED,
                queueItemId = route.queueItemId,
                requestedPositionMs = route.requestedPositionMs,
                message = message,
            )
        }
        transportWatchdogJobs.values.forEach(Job::cancel)
        transportWatchdogJobs.clear()
    }

    private fun resetSessionNow(keepDiscovery: Boolean) {
        transportWatchdogJobs.values.forEach(Job::cancel)
        transportWatchdogJobs.clear()
        if (::identity.isInitialized) {
            transportCommands.activeRoutes().forEach { (commandId, route) ->
                if (route.requestedBy == identity.peerId) {
                    updateLocalTransportPhase(
                        commandId = commandId,
                        phase = TransportCommandPhase.SUPERSEDED,
                        message = "Service shut down",
                        queueItemId = route.queueItemId,
                        requestedPositionMs = route.requestedPositionMs,
                        action = route.action,
                    )
                }
            }
        }
        transportCommands.drain()
        sessionJobs.advanceAndCancelNow()
        (connections.values + listOfNotNull(coordinatorConnection))
            .distinct()
            .forEach(ControlConnection::closeSilently)
        resetSessionState(keepDiscovery)
        playbackDispatcher.resetMetrics()
    }

    private fun resetSessionState(keepDiscovery: Boolean) {
        transportWatchdogJobs.values.forEach(Job::cancel)
        transportWatchdogJobs.clear()
        scheduler.cancel()
        heartbeatJob = null
        clockSyncJob = null
        syncJob = null
        retentionRefreshJob = null
        queueRefreshJob = null
        timelineRefreshJob = null
        playerMaintenanceRetryJob = null
        recoveryJob = null
        joinTimeoutJob = null
        joinAttemptJob = null
        pendingJoin = null
        heartbeatLiveness.reset()
        transferManager?.cancelAll()
        transferManager = null
        peers.clearSession(ControlConnection::closeSilently)
        coordinatorConnection = null
        coordinatorPeerId = null
        roomQueueLeases.values.forEach(ManagedFileLease::close)
        roomQueueLeases.clear()
        desiredPrefetchTrackIds = emptySet()
        admission.reset()
        recentCommandIds.clear()
        envelopeReplayProtector.reset()
        playerEventInterpreter.reset(player.state.value)
        latestPlaybackStateSync = null
        lastPlaybackReferenceBroadcastNs = 0L
        lastPlaybackStatusReportNs = 0L
        lastPlaybackSyncTickLocalNs = 0L
        lastObservedOutputRoute = null
        playbackSync.reset(preserveLearnedBaseline = false)
        playbackSpeedGate.reset()
        syncDiagnostics.clear()
        pendingAutoResumeQueueItemId = null
        clearPendingPlayCommand()
        pendingTrackTransitions.clear()
        cancelPendingTrackTransitionJobs()
        localTransportCommandIds.clear()
        completedLocalTransportCommandIds.clear()
        transportCommands.clear()
        transportIntents.invalidateAll()
        transportStatusClearJob?.cancel()
        transportStatusClearJob = null
        playerMutations.invalidateTransport()
        container.roomStore.updateStructure { it.copy(transportStatus = null) }
        engine = null
        roomSecret?.fill(0)
        roomSecret = null
        roomPin = null
        container.roomStore.update { it.copy(localRoomPin = null) }
        resetClockSynchronization()
        discovery.stopAdvertising()
        wifiLocks.setCpuRequired(false)
        wifiLocks.releaseWifi()
        if (!keepDiscovery) stopDiscovery()
    }

    private fun isCoordinator(): Boolean =
        ::identity.isInitialized && coordinatorPeerId == identity.peerId && engine != null

    private fun roleEngine(): RoomRoleEngine =
        if (isCoordinator()) CoordinatorEngine else ParticipantEngine

    private fun ControlConnection.asSendTarget(): RoomSendTarget =
        RoomSendTarget(
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

    private suspend fun sendToCoordinator(body: ProtocolBody) =
        messageRouter.sendToCoordinator(body)

    private suspend fun send(peerId: PeerId, body: ProtocolBody) = messageRouter.send(peerId, body)

    private suspend fun broadcast(body: ProtocolBody, except: PeerId? = null) =
        messageRouter.broadcast(body, except)

    private suspend fun broadcastCanonical(
        sequence: Long,
        body: ProtocolBody,
        except: PeerId? = null,
    ) = messageRouter.broadcastCanonical(sequence, body, except)

    private suspend fun envelope(body: ProtocolBody, sequence: Long? = null): Envelope {
        val snapshot =
            engine?.snapshot() ?: throw IllegalStateException("Room session is not established")
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
        val allowedAddress =
            NetworkAddressPolicy.parseAllowedAddress(announced.hostAddress) ?: return
        val normalized =
            announced.copy(
                displayName = announced.displayName.trim().take(40).ifBlank { "Friend" },
                hostAddress = allowedAddress.hostAddress ?: return,
                lastSeenElapsedMs = SystemClock.elapsedRealtime(),
            )
        peerDirectory[peerId] = normalized
        val member = engine?.snapshot()?.members?.firstOrNull { it.peerId == peerId } ?: return
        if (
            member.displayName != normalized.displayName ||
                member.endpoint != normalized ||
                !member.connected
        ) {
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
        if (
            member != null &&
                (member.displayName != identity.displayName || member.endpoint != endpoint)
        ) {
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

    private fun localEndpoint(): PeerEndpoint =
        PeerEndpoint(
            peerId = identity.peerId,
            displayName = identity.displayName,
            hostAddress = selectedLocalAddress() ?: "127.0.0.1",
            port = server.port,
            appVersion = BuildConfig.VERSION_NAME,
            lastSeenElapsedMs = SystemClock.elapsedRealtime(),
        )

    private fun selectedLocalAddress(): String? =
        NetworkAddressPolicy.bestLocalAddress(preferHotspot = hotspot.state.value != null)
            ?.hostAddress

    private fun updateSnapshot(
        snapshot: RoomSnapshot,
        lifecycle: RoomLifecycleState = RoomLifecycleState.CONNECTED,
        message: String? = null,
    ) {
        refreshRoomQueueLeases(snapshot)
        latestPlaybackStateSync = snapshot.playback
        container.roomStore.update {
            it.copy(
                lifecycle = lifecycle,
                snapshot = snapshot,
                isCoordinator = snapshot.term.coordinatorPeerId == identity.peerId,
                status = UserFacingStatus.READY,
                statusMessage = message,
                errorMessage = null,
                issue = null,
                roomAddress = selectedLocalAddress(),
                roomPort = server.port,
            )
        }
        refreshPowerLocks()
    }

    private fun refreshRoomQueueLeases(snapshot: RoomSnapshot) {
        val required = snapshot.queue.mapTo(linkedSetOf()) { it.track.trackId }
        roomQueueLeases.keys
            .filter { it !in required }
            .forEach { trackId ->
                roomQueueLeases.remove(trackId)?.close()
            }
        required.filterNot(roomQueueLeases::containsKey).forEach { trackId ->
            roomQueueLeases[trackId] =
                container.fileStore.acquireLease(
                    trackId,
                    ManagedFileLeaseReason.ROOM_QUEUE,
                )
        }
    }

    private suspend fun <T> suspendResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun userFacingJoinFailure(error: Throwable): String =
        when ((error as? ProtocolException)?.rejectionCode) {
            HandshakeRejectionCode.AUTHENTICATION_FAILED -> "The room invite or PIN is incorrect"
            HandshakeRejectionCode.RATE_LIMITED -> "Too many attempts. Try again shortly"
            HandshakeRejectionCode.PROTOCOL_MISMATCH -> "This room uses a different Unison version"
            HandshakeRejectionCode.ROOM_FULL -> "This room is full"
            HandshakeRejectionCode.IDENTITY_COLLISION -> "This phone is already in the room"
            HandshakeRejectionCode.COORDINATOR_MOVED -> "The room host changed. Find the room again"
            HandshakeRejectionCode.ROOM_INACTIVE,
            HandshakeRejectionCode.WRONG_ROOM -> "This room is no longer available"
            HandshakeRejectionCode.INVALID_REQUEST,
            HandshakeRejectionCode.UNKNOWN,
            null -> "Could not connect to this room"
        }

    private fun onPlaybackFailure(failure: PlaybackFailure) {
        val issue =
            when (failure) {
                is PlaybackFailure.TrackUnavailable ->
                    RoomIssue(
                        code = RoomIssueCode.PLAYBACK_TRACK_UNAVAILABLE,
                        message = "This song is not ready yet",
                        recoveryAction = RoomRecoveryAction.READD_TRACK,
                        commandId = failure.commandId,
                        queueItemId = failure.queueItemId,
                    )

                is PlaybackFailure.ClockUnavailable ->
                    RoomIssue(
                        code = RoomIssueCode.PLAYBACK_CLOCK_UNAVAILABLE,
                        message = "Playback is waiting for the room clock to reconnect",
                        severity = RoomIssueSeverity.WARNING,
                        recoveryAction = RoomRecoveryAction.RECONNECT,
                        commandId = failure.commandId,
                        deduplicationKey = "playback-clock:${failure.commandId ?: "room"}",
                    )

                is PlaybackFailure.ActionFailed ->
                    RoomIssue(
                        code = RoomIssueCode.PLAYBACK_ACTION_FAILED,
                        message = "Playback could not complete that action",
                        recoveryAction = RoomRecoveryAction.RETRY,
                        commandId = failure.commandId,
                        deduplicationKey = "playback-action:${failure.commandId ?: failure.action}",
                    )
            }
        setIssue(issue)
    }

    private fun setIssue(issue: RoomIssue) {
        val current = container.roomStore.structure.value.issue
        if (current?.deduplicationKey == issue.deduplicationKey) return
        container.roomStore.update {
            it.copy(issue = issue, errorMessage = issue.message, statusMessage = null)
        }
    }

    private fun setError(message: String) = setIssue(RoomIssue.internalFailure(message))

    private fun resetClockSynchronization() {
        clockSync.reset()
        lastClockQualityReportNs = 0L
    }

    private suspend fun setFailure(message: String) {
        // A terminal failure must not leave a stale snapshot, sockets, or audio running behind the
        // lobby. Avoid cancelling the coroutine currently reporting recovery failure, then perform
        // the same deterministic teardown as Leave room before publishing the error.
        if (recoveryJob === currentCoroutineContext()[Job]) recoveryJob = null
        resetSession(keepDiscovery = false)
        hotspot.stop()
        playerMutations.invalidateTransport()
        suspendResult {
            playerMutations.maintenance {
                pause()
                setRepeatCurrentItem(false)
                setQueue(emptyList(), null, 0)
            }
        }
        container.roomStore.reset(preserveHotspot = false)
        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.FAILED,
                status = UserFacingStatus.UNAVAILABLE,
                errorMessage = message,
                issue =
                    RoomIssue(
                        code = RoomIssueCode.CONNECTION_FAILED,
                        message = message,
                        recoveryAction = RoomRecoveryAction.RECONNECT,
                        deduplicationKey = "terminal:$message",
                    ),
                statusMessage = null,
            )
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        addressMonitorJob?.cancel()
        addressMonitorJob = null
        hotspotStateJob?.cancel()
        hotspotStateJob = null
        playerStateJob?.cancel()
        playerStateJob = null
        resetSessionNow(keepDiscovery = false)
        playbackDispatcher.close()
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
        private const val SESSION_SHUTDOWN_TIMEOUT_MS = 2_500L
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val CLOCK_SYNC_WARMUP_INTERVAL_MS = 250L
        private const val CLOCK_SYNC_STEADY_INTERVAL_MS = 1_000L
        private const val CLOCK_QUALITY_REPORT_INTERVAL_NS = 15_000_000_000L

        private const val PLAYBACK_SPEED_EPSILON = 0.00005f
        private const val TRANSPORT_RESULT_VISIBLE_MS = 900L
        private const val TRANSPORT_REJECTION_VISIBLE_MS = 3_000L
        private const val TRANSPORT_ACCEPTED_WATCHDOG_MS = 2_000L
        private const val TRANSPORT_EXECUTION_WATCHDOG_MS = 12_000L
        private const val TRANSPORT_RECONCILIATION_GRACE_MS = 2_000L
        private const val TRANSPORT_PAUSED_POSITION_TOLERANCE_MS = 250L
        private const val TRANSPORT_PLAYING_POSITION_TOLERANCE_MS = 750L
        private const val PLAYBACK_STATUS_REPORT_INTERVAL_NS = 1_000_000_000L
        private const val LIFECYCLE_DISCONTINUITY_NS = 3_000_000_000L
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
        private const val PLAYER_MAINTENANCE_RETRY_INTERVAL_MS = 40L
        private const val CURRENT_ITEM_POSITION_TOLERANCE_MS = 250L
        private const val RECENT_COMMAND_ID_LIMIT = 256
        private const val PLAYER_HISTORY_ITEMS = 2
        private const val PLAYER_UPCOMING_ITEMS = 12
        private const val FUTURE_COMMAND_TOLERANCE_NS = 5_000_000L
        private const val ELECTION_DELAY_MS = 3_000L
        private const val PEER_TIMEOUT_MS = 30_000L
        private const val INITIAL_JOIN_TIMEOUT_MS = 12_000L
        private const val PENDING_TRACK_PREPARATION_TIMEOUT_MS = 10_000L
        private const val MAX_REPORTED_CLOCK_RTT_NS = 2_000_000_000L
        private const val MAX_REPORTED_CLOCK_UNCERTAINTY_NS = 1_000_000_000L
    }
}
