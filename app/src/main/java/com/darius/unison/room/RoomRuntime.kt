package com.darius.unison.room

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.darius.unison.BuildConfig
import com.darius.unison.app.AppContainer
import com.darius.unison.model.AppCommand
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.HotspotInfo
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.LocalPlaybackParticipation
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
import com.darius.unison.model.TransferPriority
import com.darius.unison.model.UserCommand
import com.darius.unison.model.UserFacingStatus
import com.darius.unison.model.transportAction
import com.darius.unison.model.transportActionOrNull
import com.darius.unison.network.AndroidLocalNetworkRouter
import com.darius.unison.network.ControlClient
import com.darius.unison.network.ControlConnection
import com.darius.unison.network.DiscoveredRoomRegistry
import com.darius.unison.network.LocalHotspotController
import com.darius.unison.network.NsdDiscoveryEvent
import com.darius.unison.network.NsdDiscoveryException
import com.darius.unison.network.NsdRoomDiscovery
import com.darius.unison.network.PeerServer
import com.darius.unison.network.WifiLocks
import com.darius.unison.playback.CanonicalPlaybackDispatcher
import com.darius.unison.playback.LocalPlayableItem
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackFailure
import com.darius.unison.playback.PlaybackIntentReconciliationPolicy
import com.darius.unison.playback.PlayerEventInterpreter
import com.darius.unison.playback.PlayerExecutor
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlayerState
import com.darius.unison.playback.PlaybackPauseCause
import com.darius.unison.playback.PlayerStateEventPolicy
import com.darius.unison.protocol.Crypto
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.EnvelopeAcceptance
import com.darius.unison.protocol.EnvelopeReplayProtector
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.HandshakeRejectionCode
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.protocol.ProtocolException
import com.darius.unison.protocol.ProtocolJson
import com.darius.unison.protocol.RoomSnapshotValidator
import com.darius.unison.protocol.TransferFailureBlame
import com.darius.unison.protocol.TransferFailureCode
import com.darius.unison.protocol.SnapshotValidationContext
import com.darius.unison.protocol.SnapshotValidationResult
import com.darius.unison.storage.ManagedFileLease
import com.darius.unison.storage.ManagedFileLeaseReason
import com.darius.unison.sync.ClockEstimate
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.sync.ClockSyncState
import com.darius.unison.sync.PlaybackSyncDecision
import com.darius.unison.sync.PlaybackSyncInput
import com.darius.unison.sync.SyncAction
import com.darius.unison.sync.SynchronizationDiagnostics
import com.darius.unison.sync.SynchronizationEventFactory
import com.darius.unison.transfer.TransferCapacityPolicy
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
    private enum class PendingSuccessorReason {
        NATURAL_END,
        USER_NEXT,
    }

    private data class PendingSuccessor(
        val fromQueueItemId: QueueItemId,
        val targetQueueItemId: QueueItemId,
        val reason: PendingSuccessorReason,
        val resumeWhenReady: Boolean,
        val commandId: String? = null,
        val requestedBy: PeerId? = null,
    )
    private val appContext = context.applicationContext
    private val log = container.diagnostics
    private val diagnostics = RoomDiagnostics(log)
    private val clock = AndroidMonotonicClock
    private val clockSync = ClockSyncEngine(clock)
    private val playbackSynchronization = PlaybackSynchronizationRuntime()
    private val syncDiagnostics = SynchronizationDiagnostics(scope, log)
    private val wifiLocks = WifiLocks(appContext)
    private val localNetworkRouter = AndroidLocalNetworkRouter(appContext, log)
    private val discovery = NsdRoomDiscovery(appContext, wifiLocks, log, localNetworkRouter)
    private val discoveredRoomRegistry = DiscoveredRoomRegistry()
    private val hotspot = LocalHotspotController(appContext, log)
    private val controlClient = ControlClient(scope, log, localNetworkRouter)
    private val server = PeerServer(scope, log, this, localNetworkRouter)
    private val playerExecutor =
        PlayerExecutor(
            player = player,
            clock = clock,
            clockSync = clockSync,
            scope = scope,
            log = log,
            onError = ::onPlaybackFailure,
            onCommandPhase = ::onScheduledCommandPhase,
            usesLocalCoordinatorClock = { isCoordinator() },
        )
    private val queuePreparationFence = QueuePreparationFence()
    private val playbackSession =
        PlaybackSessionCoordinator(
            playbackStatusReportIntervalNs = PLAYBACK_STATUS_REPORT_INTERVAL_NS,
            clockQualityReportIntervalNs = CLOCK_QUALITY_REPORT_INTERVAL_NS,
        )
    private val localPlaybackSync =
        LocalPlaybackSyncController(
            player = player,
            playerExecutor = playerExecutor,
            clock = clock,
            clockSync = clockSync,
            playbackSession = playbackSession,
            synchronization = playbackSynchronization,
        )
    private lateinit var identity: LocalIdentity
    private var engine: RoomEngine? = null
    private var roomSecret: ByteArray? = null
    private var roomPin: String? = null
    private var coordinatorPeerId: PeerId? = null
    private var coordinatorConnection: ControlConnection? = null
    private val peers = PeerRegistry<ControlConnection>()
    private val peerPlaybackHealth =
        PeerPlaybackHealthRegistry(readyLeaseNs = CLOCK_READY_LEASE_NS)
    private val transferCapacityPolicy = TransferCapacityPolicy.DEFAULT
    private val transferCoordinator = TransferCoordinator(transferCapacityPolicy)
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

    private val pendingTransferAssignments
        get() = peers.pendingTransferAssignments

    private val initializationMutex = Mutex()
    private val snapshotValidator = RoomSnapshotValidator(maxMembers = MAX_ROOM_MEMBERS)
    private val envelopeReplayProtector = EnvelopeReplayProtector()
    private val admission =
        ControlAdmissionController(
            snapshot = { engine?.snapshot() },
            isCoordinator = ::isCoordinator,
            localIdentity = { identity },
            roomPin = { roomPin },
            roomSecret = { roomSecret },
            sessionGeneration = { sessionJobs.generation },
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
            handleLocalEnvelope = ::processLocalEnvelope,
            onCoordinatorUnavailable = ::setError,
        )
    private val canonicalPlayback =
        CanonicalPlaybackCoordinator(
            player = player,
            playerExecutor = playerExecutor,
            clock = clock,
            clockSync = clockSync,
            playbackSession = playbackSession,
            localPeerId = { identity.peerId },
            isCoordinator = ::isCoordinator,
            snapshotProvider = { engine?.snapshot() },
            refreshPlayerQueue = ::refreshPlayerQueue,
            isQueueItemExecutable = ::isQueueItemLocallyExecutable,
            scheduleQueueRefresh = ::scheduleQueueRefresh,
            requestSnapshot = { sequence ->
                sendToCoordinator(ProtocolBody.SnapshotRequest(sequence))
            },
            send = ::send,
            log = log,
            futureCommandToleranceNs = FUTURE_COMMAND_TOLERANCE_NS,
        )
    private val roomEvents =
        SerializedEventLoop<RoomEvent>(
            scope = scope,
            capacity = ROOM_EVENT_CAPACITY,
            handler = ::processRoomEvent,
            onFailure = { event, error ->
                val eventName =
                    if (error is CancellationException) {
                        "room.event.unexpected_handler_cancellation"
                    } else {
                        "room.event.failed"
                    }
                diagnostics.error(
                    eventName, error, "event.type" to event::class.simpleName,
                )
                event.completionOrNull()?.completeExceptionally(error)
            },
            onDropped = { event, cause ->
                event.completionOrNull()?.completeExceptionally(cause)
            },
            onHandled = { event, durationNs ->
                if (durationNs >= SLOW_ROOM_EVENT_NS) {
                    diagnostics.warn(
                        "room.event.slow",
                        null,
                        "event.type" to event::class.simpleName,
                        "operation.duration_ms" to durationNs / 1_000_000L,
                    )
                }
            },
        )
    private val transportIntents =
        TransportIntentCoordinator(scope, ::acceptTransportIntent, ::supersedeTransportIntent)
    private val localPlaybackParticipation = LocalPlaybackParticipationCoordinator(player = player, playerExecutor = playerExecutor, clock = clock, clockSync = clockSync,
        playbackSession = playbackSession, isCoordinator = ::isCoordinator,
        snapshotProvider = { engine?.snapshot() },
        isQueueItemExecutable = ::isQueueItemLocallyExecutable,
        refreshPlayerQueue = { snapshot, itemId, positionMs -> refreshPlayerQueue(snapshot, itemId, positionMs) },
        executeRejoin = { commandId, publishTransportStatus, block ->
            if (publishTransportStatus) {
                executeImmediateLocalTransport(commandId, TransportAction.PLAY, block)
            } else {
                playerExecutor.executeImmediateTransport(commandId, block)
            }
        },
        resetLocalSynchronization = {
            localPlaybackSync.resetTracking(preserveLearnedBaseline = false)
            syncDiagnostics.clear()
            container.roomStore.updatePlayback { it.copy(localDriftMs = null) }
        },
        publishStatus = { report -> publishLocalPlaybackStatus(report) },
        onCoordinatorCohortChanged = ::reevaluateAllPreparation,
        diagnostics = diagnostics,
    )
    private val playbackDispatcher =
        CanonicalPlaybackDispatcher(
            scope = scope,
            applyExact = ::applyExactCanonicalPlayback,
            reconcileLatest = ::reconcileCanonicalPlayback,
            preparedQueueItemIds = { preparedQueueItemIds },
            onFailure = { body, error ->
                diagnostics.error(
                    "playback.dispatch.failed", error,
                    "mutation.type" to (body?.let { it::class.simpleName } ?: "reconciliation"),
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
    private var addressMonitorJob: Job? = null
    private var queueRefreshJob: Job? = null
    private var timelineRefreshJob: Job? = null
    private var playerMaintenanceRetryJob: Job? = null
    private var recoveryJob: Job? = null
    private var localNetworkRecoveryJob: Job? = null
    private val peerDisconnectGraceJobs = mutableMapOf<PeerId, Job>()
    private var joinTimeoutJob: Job? = null
    private var joinAttemptJob: Job? = null
    private var pendingJoin: PendingJoin? = null
    private var lastAdvertisedClockReady: Boolean? = null
    private val heartbeatLiveness = HeartbeatLivenessPolicy(HEARTBEAT_INTERVAL_MS, PEER_TIMEOUT_MS)
    private val playerEventInterpreter = PlayerEventInterpreter()
    private val roomQueueLeases = mutableMapOf<TrackId, ManagedFileLease>()
    /** Ephemeral readiness projection for the current queue revision; never canonical/wire-ordered. */
    private var preparedQueueItemIds: Set<QueueItemId> = emptySet()
    /** Explicit user Prepare intent. Ephemeral and never canonical. */
    private var explicitPreparationQueueItemIds: Set<QueueItemId> = emptySet()
    private var playbackReadinessQueueRevision: Long = -1L
    private var desiredTransferDemands: Map<TrackId, TransferDemand> = emptyMap()
    private val localTrackAvailability = ConcurrentHashMap<TrackId, Boolean>()
    private var pendingSuccessor: PendingSuccessor? = null
    private var terminalNaturalPause: TerminalNaturalPause? = null
    private val localTransportCommandIds = LinkedHashSet<String>()
    private val completedLocalTransportCommandIds = LinkedHashSet<String>()
    private val transportCommands = TransportCommandTracker()
    private val transportWatchdogJobs = mutableMapOf<String, Job>()
    private val transferRetryDeadlines = mutableMapOf<Pair<TrackId, PeerId>, Long>()
    private var transferRetryWakeupJob: Job? = null
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
                        beginTrackPreparation(
                            event.command,
                            event.queuePreparationTicket
                                ?: error("AddTracks is missing its ingress fence"),
                            event.completion,
                        )
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
                    processRemoteEnvelope(event.sourceConnection, event.envelope)
                }

            is RoomEvent.ControlConnected ->
                completeEvent(event.completion) {
                    processControlConnected(event.connection, event.admittedSession)
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
                    setRoomEnded("The room host is unavailable")
                }
            }

            is RoomEvent.LocalNetworkGraceExpired ->
                processLocalNetworkGraceExpired(event.generation)

            is RoomEvent.PeerDisconnectGraceExpired ->
                processPeerDisconnectGraceExpired(event.generation, event.peerId)

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
                    if (event.available) {
                        onTrackHaveInActor(event.peerId, event.trackId)
                    } else {
                        onTrackNeedInActor(
                            event.peerId,
                            event.trackId,
                            event.priority,
                            event.neededByCoordinatorNs,
                        )
                    }
                }

            is RoomEvent.TracksPrepared ->
                completeEvent(event.completion) {
                    applyPreparedTracks(event)
                }

            is RoomEvent.RepositoryCommandCompleted ->
                completeEvent(event.completion) {
                    event.error?.let { throw it }
                    if (!sessionJobs.isCurrent(event.generation)) {
                        return@completeEvent
                    }
                }

            is RoomEvent.LocalAddressChanged -> processLocalAddressChanged(event.address)
            is RoomEvent.HotspotChanged -> processHotspotChanged(event.value, event.address)
            is RoomEvent.PlayerTransitionObserved -> processPlayerTransition(event.state)
            is RoomEvent.TransportCommandPhaseObserved ->
                processTransportCommandPhaseObserved(event)
            is RoomEvent.LocalTrackAvailabilityProbed ->
                processLocalTrackAvailabilityProbed(event)
            is RoomEvent.TransportWatchdogExpired -> processTransportWatchdogExpired(event)
            is RoomEvent.TransportWatchdogAlignmentObserved ->
                processTransportWatchdogAlignmentObserved(event)
            is RoomEvent.HeartbeatTick -> {
                if (sessionJobs.isCurrent(event.generation)) {
                    processHeartbeatTick()
                } else {
                    logStaleSessionEvent("HeartbeatTick", event.generation)
                }
            }
            is RoomEvent.LocalPlaybackStatusDue -> {
                if (sessionJobs.isCurrent(event.generation)) processLocalPlaybackStatusDue()
            }
            is RoomEvent.PlaybackReferenceBroadcastDue -> {
                if (sessionJobs.isCurrent(event.generation)) processPlaybackReferenceBroadcastDue()
            }
            is RoomEvent.TransferCompleted -> {
                if (sessionJobs.isCurrent(event.generation)) {
                    onLocalTrackReady(event.descriptor)
                } else {
                    logStaleSessionEvent("TransferCompleted", event.generation)
                }
            }
            is RoomEvent.TransferAuthorizationTimedOut ->
                processTransferAuthorizationTimedOut(event)
            is RoomEvent.TransferRetryDue -> {
                if (sessionJobs.isCurrent(event.generation)) {
                    transferRetryWakeupJob = null
                    val key = event.trackId to event.destinationPeerId
                    val deadline = transferRetryDeadlines[key]
                    if (deadline != null && clock.nowNs() >= deadline) {
                        transferRetryDeadlines.remove(key)
                        assignNextForDestination(event.destinationPeerId)
                    }
                    rescheduleTransferRetryWakeup()
                }
            }
            is RoomEvent.TransferFailed -> {
                if (!sessionJobs.isCurrent(event.generation)) {
                    logStaleSessionEvent("TransferFailed", event.generation)
                    return
                }
                val failure = event.failure
                sendToCoordinator(
                    ProtocolBody.TrackFailed(
                        trackId = failure.trackId,
                        sourcePeerId = failure.sourcePeerId,
                        stage = failure.stage,
                        code = failure.code,
                        blame = failure.blame,
                        retryable = failure.retryable,
                        reason = failure.message,
                    )
                )
            }
        }
    }

    private fun logStaleSessionEvent(eventType: String, eventGeneration: Long) {
        diagnostics.debug(
            "room.event.stale_session",
            "event.type" to eventType,
            "session.event_generation" to eventGeneration,
            "session.current_generation" to sessionJobs.generation,
        )
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

    private suspend fun enqueueEnvelope(connection: ControlConnection, envelope: Envelope) {
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(RoomEvent.NetworkEnvelopeReceived(connection, envelope, completion))
        completion.await()
    }

    private suspend fun enqueueControlClosed(connection: ControlConnection, cause: Throwable?) {
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(RoomEvent.ControlClosed(connection, cause, completion))
        completion.await()
    }

    private suspend fun processLocalAddressChanged(address: String?) {
        container.roomStore.update { it.copy(roomAddress = address) }
        if (!::identity.isInitialized || engine == null) return

        if (address == null) {
            beginLocalConnectivityInterruption()
            return
        }

        if (isCoordinator()) {
            val recoveringFromLocalNetwork = localNetworkRecoveryJob != null
            localNetworkRecoveryJob?.cancel()
            localNetworkRecoveryJob = null
            refreshLocalCoordinatorEndpoint()
            if (recoveringFromLocalNetwork) restoreConnectedLifecycle("Network restored")
        } else {
            // A participant whose control socket survived a brief route transition can continue on
            // that socket. If the socket closed, processControlClosed owns reconnect completion.
            if (recoveryJob == null && coordinatorConnection != null) {
                restoreConnectedLifecycle("Network restored")
                localEndpointOrNull()?.let { sendToCoordinator(ProtocolBody.EndpointAnnouncement(it)) }
            }
        }
    }

    private suspend fun processHotspotChanged(value: HotspotInfo?, address: String?) {
        val previous = container.roomStore.structure.value.hotspot
        container.roomStore.update { it.copy(hotspot = value, roomAddress = address) }
        if (!::identity.isInitialized || engine == null) return

        // A hosted LocalOnlyHotspot is the room's network, not a cosmetic setting. If Android or
        // the user removes it while this phone coordinates the room, the old room can no longer be
        // truthfully advertised as active even if this phone also happens to have another address.
        if (previous != null && value == null && isCoordinator()) {
            setRoomEnded("The room hotspot stopped")
            return
        }
        if (value != null && isCoordinator()) refreshLocalCoordinatorEndpoint()
    }

    private suspend fun beginLocalConnectivityInterruption() {
        val snapshot = engine?.snapshot() ?: return
        pauseLocalPlaybackForConnectivityLoss()
        container.roomStore.update { state ->
            state.copy(
                lifecycle = RoomLifecycleState.RECONNECTING,
                status = UserFacingStatus.RECONNECTING,
                statusMessage = "Waiting for network…",
                errorMessage = null,
                issue = null,
            )
        }
        refreshPowerLocks()

        if (!isCoordinator()) {
            // Route loss invalidates the existing coordinator socket even if Android has not yet
            // delivered a TCP close. Closing it starts the one bounded reconnect owner immediately.
            coordinatorConnection?.close(IllegalStateException("Local network unavailable"))
            return
        }
        if (localNetworkRecoveryJob?.isActive == true) return
        val generation = sessionJobs.generation
        diagnostics.warn(
            "room.network.interrupted",
            null,
            "room.id_hash" to snapshot.roomId.hashCode().toString(16),
        )
        localNetworkRecoveryJob = launchSessionJob {
            delay(RoomReconnectPolicy.LOCAL_NETWORK_GRACE_MS)
            submitSessionEvent(generation, RoomEvent.LocalNetworkGraceExpired(generation))
        }
    }

    private suspend fun pauseLocalPlaybackForConnectivityLoss() {
        playerExecutor.invalidateTransport()
        suspendResult {
            playerExecutor.maintenance {
                if (state.value.playWhenReady) pause(PlaybackPauseCause.CONNECTION_INTERRUPTION)
                setPlaybackSpeed(1f)
            }
        }.onFailure { error ->
            diagnostics.warn("playback.connection_pause.failed", error)
        }
    }

    private fun restoreConnectedLifecycle(message: String) {
        if (engine == null) return
        val current = container.roomStore.structure.value
        if (current.lifecycle != RoomLifecycleState.RECONNECTING) return
        container.roomStore.update { state ->
            state.copy(
                lifecycle = RoomLifecycleState.CONNECTED,
                status = UserFacingStatus.READY,
                statusMessage = message,
                errorMessage = null,
                issue = null,
            )
        }
        refreshPowerLocks()
        diagnostics.info("room.network.restored")
    }

    private suspend fun processLocalNetworkGraceExpired(generation: Long) {
        if (!sessionJobs.isCurrent(generation)) return
        localNetworkRecoveryJob = null
        if (engine == null || !isCoordinator()) return
        if (selectedLocalAddress() != null) {
            refreshLocalCoordinatorEndpoint()
            restoreConnectedLifecycle("Network restored")
            return
        }
        diagnostics.warn("room.network.recovery_exhausted")
        setRoomEnded("The room network is unavailable")
    }

    private fun schedulePeerDisconnectGrace(peerId: PeerId) {
        if (!isCoordinator()) return
        peerDisconnectGraceJobs.remove(peerId)?.cancel()
        val generation = sessionJobs.generation
        peerDisconnectGraceJobs[peerId] = launchSessionJob {
            delay(RoomReconnectPolicy.PEER_DISCONNECT_GRACE_MS)
            submitSessionEvent(
                generation,
                RoomEvent.PeerDisconnectGraceExpired(generation, peerId),
            )
        }
    }

    private suspend fun processPeerDisconnectGraceExpired(generation: Long, peerId: PeerId) {
        peerDisconnectGraceJobs.remove(peerId)
        if (!sessionJobs.isCurrent(generation) || !isCoordinator() || isPeerConnected(peerId)) return
        val snapshot = engine?.snapshot() ?: return
        if (snapshot.members.none { it.peerId == peerId }) return

        emitCanonical(ProtocolBody.PeerLeft(peerId))
        peers.removePeer(peerId)
        transferCoordinator.removePeer(peerId)
        cancelTransferRetriesForPeer(peerId)
        discardPendingTransferAssignmentsForPeer(peerId)
        waitingForSource.values.forEach { it.remove(peerId) }
        publishMemberRuntime()
        assignEligibleTransfers()
        reevaluateAllPreparation()
        broadcast(ProtocolBody.PeerDirectory(peerDirectory.values.toList()))
        diagnostics.info(
            "room.peer.removed_after_disconnect",
            "peer.id" to peerId.value.take(12),
        )
    }

    private suspend fun processPlayerTransition(value: PlayerState) {
        localPlaybackParticipation.observe(value, engine?.snapshot())
        if (value.participation == LocalPlaybackParticipation.OUTPUT_INHIBITED) {
            launchSessionJob { localPlaybackParticipation.tryPendingRejoin() }
        }
        value.error?.let { error ->
            container.roomStore.updateStructure { it.copy(errorMessage = error) }
        }
        when (val action = playerEventInterpreter.observe(value, isCoordinator(), clock.nowNs())) {
            PlayerEventInterpreter.Action.None -> Unit
            is PlayerEventInterpreter.Action.PlaybackEnded ->
                recordNaturalPlaybackEnded(action.queueItemId, action.positionMs, action.durationMs)
        }
    }

    init {
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
                        localPlaybackParticipation = value.participation,
                        localPlaybackInhibitionReason = value.inhibitionReason,
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
    private suspend fun acceptTransportIntent(intent: TransportIntentCoordinator.Intent) {
        when (intent) {
            is TransportIntentCoordinator.Intent.Local -> {
                // Enqueue lifecycle + command in actor order, but do not make the intent processor
                // wait for command execution. This keeps ingress responsive while the actor owns
                // deterministic command ordering and the caller's completion remains terminal.
                roomEvents.submit(RoomEvent.LocalTransportSubmitted(intent.command, CompletableDeferred()))
                roomEvents.submit(RoomEvent.AppCommandReceived(intent.command, completion = intent.completion))
            }
            is TransportIntentCoordinator.Intent.Remote ->
                if (sessionJobs.isCurrent(intent.generation)) {
                    roomEvents.submit(
                        RoomEvent.CoordinatorCommandReceived(intent.command, CompletableDeferred())
                    )
                }
        }
    }

    private suspend fun supersedeTransportIntent(intent: TransportIntentCoordinator.Intent) {
        when (intent) {
            is TransportIntentCoordinator.Intent.Local -> {
                roomEvents.submit(RoomEvent.LocalTransportSubmitted(intent.command, CompletableDeferred()))
                roomEvents.submit(RoomEvent.LocalTransportSuperseded(intent.command, intent.completion))
            }
            is TransportIntentCoordinator.Intent.Remote ->
                if (sessionJobs.isCurrent(intent.generation)) {
                    roomEvents.submit(RoomEvent.CoordinatorTransportSuperseded(intent.generation, intent.command))
                }
        }
    }
    /** Enqueues one app command and returns its terminal completion. */
    suspend fun submit(command: AppCommand): CompletableDeferred<Unit> {
        if (command is AppCommand.Transport) {
            val completion = CompletableDeferred<Unit>()
            if (!transportIntents.submit(command, completion))
                completion.completeExceptionally(IllegalStateException("Too many playback commands"))
            return completion
        }
        // Issue AddTracks tickets at ordered ingress, not when repository work starts. A later
        // ClearQueue can therefore invalidate the earlier request even if actor scheduling delays
        // the AddTracks event itself.
        val queuePreparationTicket =
            if (command is AppCommand.AddTracks) queuePreparationFence.issue() else null
        if (command == AppCommand.ClearQueue || command == AppCommand.LeaveRoom) {
            queuePreparationFence.invalidate()
            // These coordinators own their own synchronization and can invalidate immediately.
            // Room-owned pending transition state is cancelled later inside the serialized actor.
            transportIntents.invalidateAll()
            playerExecutor.invalidateTransport()
        }
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(
            RoomEvent.AppCommandReceived(
                command = command,
                queuePreparationTicket = queuePreparationTicket,
                completion = completion,
            )
        )
        return completion
    }
    suspend fun handle(command: AppCommand) {
        submit(command).await()
    }

    private suspend fun handleAppCommand(command: AppCommand) {
        ensureInitialized()
        diagnostics.info("room.command.received", "command.type" to command::class.simpleName)
        when (command) {
            is AppCommand.CreateRoom -> createRoom(command.roomName)
            is AppCommand.JoinRoom -> joinRoom(command.room, command.credential)
            AppCommand.StartDiscovery -> startDiscovery()
            AppCommand.StopDiscovery -> stopDiscovery()
            AppCommand.LeaveRoom -> leaveRoom()
            AppCommand.CreateOfflineNetwork -> hotspot.start { message -> setError(message) }
            AppCommand.StopOfflineNetwork -> {
                if (engine != null && isCoordinator() && container.roomStore.structure.value.hotspot != null) {
                    leaveRoom("Room hotspot stopped")
                } else {
                    hotspot.stop()
                }
            }
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
                    localEndpointOrNull()?.let { sendToCoordinator(ProtocolBody.EndpointAnnouncement(it)) }
                }
            }

            is AppCommand.KeepTrack,
            is AppCommand.RemoveTemporaryTrack ->
                error("Library file operations must run outside the room actor")
            is AppCommand.Play -> {
                val snapshot = engine?.snapshot()
                val local = player.state.value
                when (
                    PlaybackIntentReconciliationPolicy.decidePlayRequest(
                        canonicalPlaying = snapshot?.playback?.isPlaying == true,
                        participation = local.participation,
                    )
                ) {
                    PlaybackIntentReconciliationPolicy.PlayRequestAction.REJOIN_LIVE_ROOM -> {
                        localPlaybackParticipation.requestManualRejoin(command.commandId)
                        updateLocalTransportPhase(
                            command.commandId,
                            TransportCommandPhase.ACCEPTED,
                            "Waiting to rejoin",
                            action = TransportAction.PLAY,
                        )
                        launchSessionJob { localPlaybackParticipation.tryPendingRejoin() }
                    }
                    PlaybackIntentReconciliationPolicy.PlayRequestAction.MUTATE_CANONICAL_ROOM ->
                        submitUserCommand(
                            UserCommand.Play(
                                commandId = command.commandId,
                                requestedBy = identity.peerId,
                            )
                        )
                }
            }

            is AppCommand.Pause -> {
                submitUserCommand(
                    UserCommand.Pause(commandId = command.commandId, requestedBy = identity.peerId)
                )
            }

            is AppCommand.Seek -> {
                submitUserCommand(
                    UserCommand.Seek(
                        commandId = command.commandId,
                        requestedBy = identity.peerId,
                        positionMs = command.positionMs,
                    )
                )
            }

            is AppCommand.SkipNext -> {
                submitUserCommand(
                    UserCommand.SkipNext(
                        commandId = command.commandId,
                        requestedBy = identity.peerId,
                    )
                )
            }

            is AppCommand.SkipPrevious -> {
                submitUserCommand(
                    UserCommand.SkipPrevious(
                        commandId = command.commandId,
                        requestedBy = identity.peerId,
                    )
                )
            }

            is AppCommand.PlayQueueItem -> {
                submitUserCommand(
                    UserCommand.PlayQueueItem(
                        commandId = command.commandId,
                        requestedBy = identity.peerId,
                        queueItemId = command.queueItemId,
                    )
                )
            }

            is AppCommand.PrepareQueueItem -> requestQueueItemPreparation(
                queueItemId = command.queueItemId,
                requestId = command.requestId,
            )

            AppCommand.ShuffleQueue ->
                submitUserCommand(
                    UserCommand.QueueShuffle(
                        requestedBy = identity.peerId,
                        shuffleSeed = clock.nowNs() xor identity.peerId.value.hashCode().toLong(),
                    )
                )

            is AppCommand.SetRepeat ->
                submitUserCommand(
                    UserCommand.RepeatModeChange(
                        requestedBy = identity.peerId,
                        repeatMode = command.mode,
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
                    socketProvider = localNetworkRouter,
                    retentionPolicyProvider = { container.settings.retentionPolicy.first() },
                    onProgress = { progress ->
                        sessionJobs.runIfCurrent(managerGeneration) {
                            container.roomStore.updateTransfers { state ->
                                state.copy(
                                    transfers = state.transfers + (progress.trackId to progress)
                                )
                            }
                        }
                    },
                    onCompleted = { descriptor ->
                        if (sessionJobs.isCurrent(managerGeneration)) {
                            roomEvents.submit(
                                RoomEvent.TransferCompleted(managerGeneration, descriptor)
                            )
                        }
                    },
                    onFailed = { failure ->
                        if (sessionJobs.isCurrent(managerGeneration)) {
                            roomEvents.submit(RoomEvent.TransferFailed(managerGeneration, failure))
                        }
                    },
                    capacityPolicy = transferCapacityPolicy,
                )
        }
        container.roomStore.update { it.copy(roomPort = port) }
    }

    private suspend fun createRoom(requestedName: String?) {
        resetSession(keepDiscovery = false)
        container.roomStore.reset()
        if (selectedLocalAddress() == null) {
            setFailure("Connect to Wi-Fi or create an offline network before creating a room")
            return
        }
        ensureServerAndTransfers()
        playerExecutor.maintenance { setRepeatCurrentItem(false) }
        val id = UUID.randomUUID().toString()
        diagnostics.begin(id, role = "coordinator")
        val name =
            requestedName?.trim()?.take(60)?.ifBlank { null } ?: "${identity.displayName}'s room"
        val pin = Crypto.randomFourDigitPin()
        roomPin = pin
        roomSecret = Crypto.randomBytes(32)
        coordinatorPeerId = identity.peerId
        resetClockSynchronization()
        val endpoint =
            localEndpointOrNull() ?: run {
                setFailure("The local network became unavailable. Connect to Wi-Fi and try again")
                return
            }
        peerDirectory[identity.peerId] = endpoint
        val initial =
            RoomSnapshot(
                roomId = id,
                roomName = name,
                term = CoordinatorTerm(1, identity.peerId),
                sequence = 0,
                members = listOf(MemberSnapshot(identity.peerId, identity.displayName)),
            )
        check(snapshotFitsProtocol(initial)) { "Locally-created room snapshot is invalid" }
        engine = RoomEngine(initial)
        discovery.advertise(id, name, server.port, 1, onError = ::setError)
        container.roomStore.update { it.copy(localRoomPin = pin) }
        updateSnapshot(initial, RoomLifecycleState.CONNECTED, "Room ready")
        startSessionJobs()
        diagnostics.created(server.port)
    }

    private suspend fun joinRoom(room: DiscoveredRoom, credential: RoomJoinCredential) {
        resetSession(keepDiscovery = false)
        ensureServerAndTransfers()
        container.roomStore.reset()
        diagnostics.begin(room.roomId, role = "participant")
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
                diagnostics.joinStarted(attempt, identity.peerId.value, pending.room.port)
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
                diagnostics.joinAdmitted(attempt, SystemClock.elapsedRealtime() - startedAtMs)
                roomEvents.submit(
                    RoomEvent.InitialJoinConnected(pending.generation, attempt, connected)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                diagnostics.joinFailed(attempt, SystemClock.elapsedRealtime() - startedAtMs, error)
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
        publishMemberRuntime()
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
            diagnostics.warn("room.join.exhausted", event.error, "join.attempt" to event.attempt)
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
        diagnostics.warn(
            "room.identity.regenerated", null,
            "peer.previous_id" to previousPeerId.value.take(12),
            "peer.new_id" to identity.peerId.value.take(12),
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
            diagnostics.info("discovery.scan.already_active")
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
                        diagnostics.warn("discovery.scan.ended_early")
                    } else {
                        diagnostics.info("discovery.scan.completed")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val recoverable = (error as? NsdDiscoveryException)?.recoverable != false
                    diagnostics.warn(
                        "discovery.scan.failed", error, "discovery.recoverable" to recoverable,
                    )
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
        fenceTicket: QueuePreparationFence.Ticket,
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
                                        diagnostics.warn(
                                            "storage.track.prepare_failed", error,
                                            "track.id" to descriptor.trackId.value.take(12),
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
                    fenceTicket = fenceTicket,
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
            return
        }
        if (!queuePreparationFence.isCurrent(event.fenceTicket)) {
            diagnostics.info(
                "room.queue.add_stale_result",
                "operation.name" to event.fenceTicket.operation,
                "operation.epoch" to event.fenceTicket.epoch,
            )
            return
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
        val execution = playerExecutor.executeImmediateTransport(commandId, block)
        val superseded = execution.supersededCommandId
        if (superseded != null && superseded != commandId) {
            updateLocalTransportPhase(
                superseded,
                TransportCommandPhase.SUPERSEDED,
                "Replaced by a newer action",
            )
        }
        updateLocalTransportPhase(commandId, TransportCommandPhase.ACCEPTED, action = action)
        updateLocalTransportPhase(commandId, TransportCommandPhase.EXECUTING, action = action)
        when (execution.result) {
            PlayerExecutor.ExecutionResult.SUCCESS ->
                updateLocalTransportPhase(commandId, TransportCommandPhase.SETTLED, action = action)
            PlayerExecutor.ExecutionResult.FAILED ->
                updateLocalTransportPhase(
                    commandId,
                    TransportCommandPhase.REJECTED,
                    "Playback could not complete that action",
                    action = action,
                )
            PlayerExecutor.ExecutionResult.STALE ->
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
                    transition == TransportCommandTracker.Transition.Duplicate
            ) {
                transportCommands.route(commandId)?.let { scheduleTransportWatchdog(commandId, it) }
            }
            return
        }
        diagnostics.info(
            "room.transport.status", "command.id" to commandId.take(12), "transport.action" to action.name,
            "transport.phase" to phase.name, "queue.item_id" to route.queueItemId?.value?.take(12),
            "transport.message" to message?.take(160),
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
        } else {
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
        diagnostics.debug(
            "playback.transport_watchdog.expired",
            "command.id" to event.commandId.take(12),
            "transport.action" to route.action.name,
            "transport.phase" to route.phase.name,
        )
        val snapshot = engine?.snapshot() ?: return
        launchSessionJob {
            val aligned = transportAlreadyAligned(route, snapshot)
            roomEvents.submit(
                RoomEvent.TransportWatchdogAlignmentObserved(
                    generation = event.generation,
                    commandId = event.commandId,
                    ticket = event.ticket,
                    graceAttempted = event.reconciliationAttempted,
                    aligned = aligned,
                )
            )
        }
    }

    private suspend fun processTransportWatchdogAlignmentObserved(
        event: RoomEvent.TransportWatchdogAlignmentObserved,
    ) {
        if (!sessionJobs.isCurrent(event.generation)) return
        val route = transportCommands.route(event.commandId, event.ticket) ?: return
        if (event.aligned) {
            publishTransportStatus(
                requestedBy = route.requestedBy,
                commandId = event.commandId,
                action = route.action,
                phase = TransportCommandPhase.SETTLED,
                queueItemId = route.queueItemId,
                requestedPositionMs = route.requestedPositionMs,
                message = if (event.graceAttempted) "SETTLED_AFTER_GRACE" else "ALREADY_ALIGNED",
            )
            return
        }
        if (!event.graceAttempted) {
            scheduleTransportWatchdog(
                commandId = event.commandId,
                route = route,
                reconciliationAttempted = true,
                delayMs = TRANSPORT_RECONCILIATION_GRACE_MS,
            )
            return
        }

        // The watchdog observes command completion; it is not a second playback controller.
        // Canonical playback/sync owns repair. Cancel only this command's stale reservation.
        launchSessionJob {
            playerExecutor.cancelIfOwned(event.commandId, publishSuperseded = false)
        }
        val message = "Playback command did not settle"
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
                    deduplicationKey = "transport-watchdog:${event.commandId}",
                )
            )
        }
    }

    private fun projectedCanonicalPosition(snapshot: RoomSnapshot): Long {
        val coordinatorNowNs =
            when {
                isCoordinator() -> clock.nowNs()
                clockSync.synchronized -> clockSync.coordinatorNowNs()
                // Before clock lock, the coordinator timestamp has no relationship to this
                // device's monotonic clock. Hold the reference position instead of inventing one.
                else -> snapshot.playback.coordinatorTimestampNs
            }
        return snapshot.playback.projectedPositionMs(coordinatorNowNs)
    }

    private suspend fun transportAlreadyAligned(
        route: TransportCommandTracker.Route,
        snapshot: RoomSnapshot,
    ): Boolean {
        val target = route.queueItemId ?: snapshot.playback.queueItemId ?: return false
        if (snapshot.playback.queueItemId != target) return false
        val sample = player.samplePlayback()
        val expectedPositionMs =
            route.requestedPositionMs ?: projectedCanonicalPosition(snapshot)
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

    /** Routes remote transport through the same single-owner intent processor as local controls. */
    private suspend fun queueRemoteTransportCommand(command: UserCommand) {
        if (!transportIntents.submit(sessionJobs.generation, command)) publishSupersededTransport(command)
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
        if (command is UserCommand.PlayQueueItem) {
            val existingCommandId =
                transportCommands
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
        val resolution =
            TransportTargetPolicy.resolve(
                command = command,
                snapshot = current,
                coordinatorNowNs = clock.nowNs(),
                preparedQueueItemIds = preparedQueueItemIds,
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
        resolution.waitForPreparationQueueItemId?.let { targetQueueItemId ->
            if (command !is UserCommand.SkipNext) {
                error("Only sequential Next may wait for preparation")
            }
            val fromQueueItemId = current.playback.queueItemId
                ?: run {
                    publishTransportStatus(
                        requestedBy = command.requestedBy,
                        commandId = command.commandId,
                        action = TransportAction.NEXT,
                        phase = TransportCommandPhase.REJECTED,
                        message = "There is no current song",
                    )
                    return
                }
            beginPendingSuccessor(
                PendingSuccessor(
                    fromQueueItemId = fromQueueItemId,
                    targetQueueItemId = targetQueueItemId,
                    reason = PendingSuccessorReason.USER_NEXT,
                    resumeWhenReady = resolution.resumeWhenReady,
                    commandId = command.commandId,
                    requestedBy = command.requestedBy,
                )
            )
            return
        }
        resolution.rejection?.let { reason ->
            diagnostics.warn(
                "room.command.rejected", null, "command.type" to command::class.simpleName, "reason" to reason,
            )
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

        val resolvedCommand = resolution.command ?: command
        val effectiveCommand =
            if (resolvedCommand is UserCommand.QueueShuffle && resolvedCommand.preserveNextQueueItemId == null) {
                resolvedCommand.copy(preserveNextQueueItemId = shuffleRunwayQueueItemId(current))
            } else {
                resolvedCommand
            }
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
                PlaybackRequestPolicy.requiresPreparationForPlay(current, preparedQueueItemIds)
        ) {
            publishTransportStatus(
                requestedBy = effectiveCommand.requestedBy,
                commandId = effectiveCommand.commandId,
                action = TransportAction.PLAY,
                phase = TransportCommandPhase.REJECTED,
                queueItemId = currentItem.queueItemId,
                message = "Prepare this song before playing it",
            )
            return
        }

        val replayPositionOverrideMs =
            if (effectiveCommand is UserCommand.Play) {
                TerminalReplayPolicy.playPositionOverrideMs(current, terminalNaturalPause)
            } else {
                null
            }
        val leadNs = transportLeadNs(current)
        when (
            val decision =
                roomEngine.decide(
                    command = effectiveCommand,
                    coordinatorNowNs = clock.nowNs(),
                    leadNs = leadNs,
                    acceptsSnapshot =
                        if (effectiveCommand is UserCommand.QueueAdd) ::snapshotFitsProtocol
                        else { _: RoomSnapshot -> true },
                    preparedQueueItemIds = preparedQueueItemIds,
                    playPositionOverrideMs = replayPositionOverrideMs,
                )
        ) {
            is RoomReducer.Decision.Rejected -> {
                diagnostics.warn(
                    "room.command.rejected", null, "command.type" to effectiveCommand::class.simpleName,
                    "reason" to decision.reason,
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
                diagnostics.info(
                    "room.command.accepted", "command.type" to effectiveCommand::class.simpleName,
                    "canonical.mutation_count" to decision.mutations.size,
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
                    cancelPendingSuccessor("Replaced by a newer playback action")
                }
                decision.mutations.forEach { mutation ->
                    updateSnapshot(mutation.snapshot)
                    revalidateTerminalNaturalPause(mutation.snapshot)
                    broadcastCanonical(mutation.sequence, mutation.body)
                    playbackDispatcher.submit(mutation.body, mutation.snapshot)
                }
                if (replayPositionOverrideMs != null && effectiveCommand is UserCommand.Play) {
                    terminalNaturalPause = null
                }
            }
        }
    }

    private fun transportLeadNs(snapshot: RoomSnapshot): Long =
        RoomTransportTiming.leadNs(
            snapshot = snapshot,
            connectedPeers = connectedPeerIds(snapshot),
            peerHealth = peerPlaybackHealth,
            localCoordinatorPeerId = identity.peerId,
            localParticipation = player.state.value.participation,
            nowNs = clock.nowNs(),
            reconnecting = container.roomStore.structure.value.lifecycle == RoomLifecycleState.RECONNECTING,
        )

    private suspend fun reconcileCanonicalPlayback(
        reconciliation: CanonicalPlaybackDispatcher.PlaybackReconciliation
    ) {
        val snapshot = reconciliation.snapshot
        diagnostics.debug(
            "playback.reconcile.requested", "canonical.sequence" to snapshot.sequence,
            "playback.queue_revision" to reconciliation.key.queueRevision,
            "playback.triggers" to reconciliation.triggers.joinToString(),
        )
        // Canonical queue semantics own repeat behavior. Media3 itself never loops an item.
        playerExecutor.maintenance { setRepeatCurrentItem(false) }
        requestTimelineRefresh(snapshot)
    }

    private suspend fun applyExactCanonicalPlayback(body: ProtocolBody, snapshot: RoomSnapshot) {
        diagnostics.info(
            "room.canonical.applied", "mutation.type" to body::class.simpleName,
            "canonical.sequence" to snapshot.sequence,
        )
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
                playerExecutor.cancel("Queue cleared")
                queueRefreshJob?.cancel()
                timelineRefreshJob?.cancel()
                refreshPlayerQueue(snapshot)
            }

            is ProtocolBody.PlayScheduled ->
                if (canApplyScheduledCommand()) {
                    if (!isQueueItemLocallyExecutable(body.queueItemId)) {
                        diagnostics.debug(
                            "playback.execution.waiting_for_media",
                            "queue.item_id" to body.queueItemId.value.take(12),
                            "command.id" to body.commandId?.take(12),
                        )
                        prepareWindow(snapshot, priorityQueueItemId = body.queueItemId)
                    } else {
                        val local = player.state.value
                        refreshPlayerQueue(
                            snapshot = snapshot,
                            preferredCurrentQueueItemId = local.queueItemId,
                            preferredPositionMs = local.positionMs,
                        )
                        markTrackPlayed(snapshot, body.queueItemId)
                        playerExecutor.schedulePlay(
                            body.queueItemId,
                            body.positionMs,
                            body.executeAtCoordinatorNs,
                            body.commandId,
                        )
                    }
                }

            is ProtocolBody.PauseScheduled ->
                if (canApplyScheduledCommand()) {
                    playerExecutor.schedulePause(
                        body.queueItemId,
                        body.positionMs,
                        body.executeAtCoordinatorNs,
                        body.commandId,
                    )
                }

            is ProtocolBody.SeekScheduled ->
                if (canApplyScheduledCommand()) {
                    if (!isQueueItemLocallyExecutable(body.queueItemId)) {
                        diagnostics.debug(
                            "playback.execution.waiting_for_media",
                            "queue.item_id" to body.queueItemId.value.take(12),
                            "command.id" to body.commandId?.take(12),
                        )
                        prepareWindow(snapshot, priorityQueueItemId = body.queueItemId)
                    } else {
                        val local = player.state.value
                        refreshPlayerQueue(
                            snapshot = snapshot,
                            preferredCurrentQueueItemId = local.queueItemId,
                            preferredPositionMs = local.positionMs,
                        )
                        playerExecutor.scheduleSeek(
                            body.queueItemId,
                            body.positionMs,
                            body.resumePlayback,
                            body.executeAtCoordinatorNs,
                            body.commandId,
                        )
                    }
                }

            is ProtocolBody.CurrentItemChanged -> {
                val targetQueueItemId = body.queueItemId
                if (targetQueueItemId != null && !isQueueItemLocallyExecutable(targetQueueItemId)) {
                    diagnostics.debug(
                        "playback.execution.waiting_for_media",
                        "queue.item_id" to targetQueueItemId.value.take(12),
                        "command.id" to body.commandId?.take(12),
                    )
                    prepareWindow(snapshot, priorityQueueItemId = targetQueueItemId)
                    return
                }
                targetQueueItemId?.let { markTrackPlayed(snapshot, it) }
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
                        playerExecutor.scheduleSeek(
                            target,
                            body.positionMs,
                            body.resumePlayback,
                            body.executeAtCoordinatorNs,
                            body.commandId,
                        )
                    }
                }
                    ?: run {
                        playerExecutor.cancel("Queue no longer has a current item")
                        playerExecutor.maintenance { pause(PlaybackPauseCause.CANONICAL_QUEUE_EMPTY) }
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
            diagnostics.warn("room.snapshot.rejected", null, "reason" to result.summary)
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

    private suspend fun processRemoteEnvelope(
        sourceConnection: ControlConnection,
        envelope: Envelope,
    ) {
        val peerId = sourceConnection.peerId
        if (!RoomIngressAuthority.isCurrentConnection(connections[peerId], sourceConnection)) {
            diagnostics.warn(
                "network.envelope.stale_connection",
                null,
                "peer.id" to peerId.value.take(12),
            )
            return
        }
        if (!isCoordinator() && coordinatorConnection !== sourceConnection) {
            diagnostics.warn(
                "network.envelope.stale_coordinator_connection",
                null,
                "peer.id" to peerId.value.take(12),
            )
            return
        }
        processEnvelope(
            peerId = peerId,
            envelope = envelope,
            sourceConnection = sourceConnection,
            refreshRemoteLiveness = true,
        )
    }

    private suspend fun processLocalEnvelope(envelope: Envelope) {
        processEnvelope(
            peerId = identity.peerId,
            envelope = envelope,
            sourceConnection = null,
            refreshRemoteLiveness = false,
        )
    }

    private suspend fun processEnvelope(
        peerId: PeerId,
        envelope: Envelope,
        sourceConnection: ControlConnection?,
        refreshRemoteLiveness: Boolean,
    ) {
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
                diagnostics.warn(
                    "network.envelope.sequence_gap", null, "sequence.expected" to acceptance.expected,
                    "sequence.actual" to acceptance.actual, "peer.id" to peerId.value.take(12),
                )
                if (!isCoordinator() && current != null) {
                    sendToCoordinator(ProtocolBody.SnapshotRequest(current.sequence))
                }
                return
            }
            is EnvelopeAcceptance.Rejected -> {
                diagnostics.warn(
                    "network.envelope.rejected", null, "peer.id" to peerId.value.take(12),
                    "reason" to acceptance.reason,
                )
                return
            }
        }
        if (refreshRemoteLiveness) {
            lastSeenElapsedMs[peerId] = SystemClock.elapsedRealtime()
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
                engine = RoomEngine(body.snapshot)
                coordinatorPeerId = body.snapshot.term.coordinatorPeerId
                body.peerDirectory.forEach { peerDirectory[it.peerId] = it }
                lastSeenElapsedMs[body.snapshot.term.coordinatorPeerId] =
                    SystemClock.elapsedRealtime()
                announcedTrackIds.clear()
                preparedQueueItemIds = emptySet()
                explicitPreparationQueueItemIds = emptySet()
                playbackReadinessQueueRevision = -1L
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
            is ProtocolBody.PlaybackReadinessChanged -> {
                if (!isCoordinator() && peerId == coordinatorPeerId) {
                    applyPlaybackReadiness(body)
                }
            }
            is ProtocolBody.QueueItemPreparationRequested -> {
                when {
                    isCoordinator() -> {
                        val snapshot = engine?.snapshot() ?: return
                        if (snapshot.members.any { it.peerId == peerId }) {
                            requestQueueItemPreparation(
                                queueItemId = body.queueItemId,
                                requestId = body.commandId,
                            )
                        }
                    }
                    peerId == coordinatorPeerId -> {
                        val snapshot = engine?.snapshot() ?: return
                        if (snapshot.queue.any { it.queueItemId == body.queueItemId }) {
                            explicitPreparationQueueItemIds += body.queueItemId
                            publishMediaReadiness(snapshot)
                            prepareWindow(snapshot, priorityQueueItemId = body.queueItemId)
                        }
                    }
                }
            }
            is ProtocolBody.RoomOptionsChanged -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.QueueShuffled -> applyCanonicalEnvelope(envelope, body)
            is ProtocolBody.RepeatModeChanged -> applyCanonicalEnvelope(envelope, body)
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
                    localPlaybackSync.resetRuntime(preserveLearnedBaseline = true)
                    playbackSession.clearCanonical()
                }
                val replaced =
                    engine?.replace(body.snapshot) ?: body.snapshot.also { engine = RoomEngine(it) }
                preparedQueueItemIds = emptySet()
                explicitPreparationQueueItemIds = emptySet()
                playbackReadinessQueueRevision = -1L
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
                        diagnostics.warn("room.peer_directory.rejected")
                        return
                    }
                    peerDirectory.keys.retainAll(body.peers.map { it.peerId }.toSet())
                    body.peers.forEach { peerDirectory[it.peerId] = it }
                }

            is ProtocolBody.EndpointAnnouncement ->
                if (isCoordinator() && sourceConnection != null) {
                    updatePeerEndpoint(sourceConnection, body.endpoint)
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
                reportLocalClockReadiness()
                if (!wasSynchronized && clockSync.synchronized) {
                    container.roomStore.update {
                        it.copy(status = UserFacingStatus.READY, statusMessage = "Ready")
                    }
                }
            }

            is ProtocolBody.ClockReady -> {
                if (!isCoordinator()) return
                val roundTrip =
                    body.roundTripNs?.takeIf { it in 0..MAX_REPORTED_CLOCK_RTT_NS }
                val uncertainty =
                    body.uncertaintyNs?.takeIf { it in 0..MAX_REPORTED_CLOCK_UNCERTAINTY_NS }
                val validClockQuality = roundTrip != null && uncertainty != null
                val ready = body.synchronized && validClockQuality
                if (body.synchronized && !validClockQuality) {
                    diagnostics.warn(
                        "sync.clock_quality.rejected", null, "peer.id" to peerId.value.take(12),
                    )
                }
                val nowNs = clock.nowNs()
                val wasClockReady = peerPlaybackHealth.isClockReady(peerId, nowNs)
                val readinessChanged =
                    peerPlaybackHealth.updateClock(
                        peerId = peerId,
                        synchronized = ready,
                        roundTripNs = roundTrip.takeIf { ready },
                        uncertaintyNs = uncertainty.takeIf { ready },
                        nowNs = nowNs,
                    )
                if (readinessChanged) reevaluateAllPreparation()
                if (ready && !wasClockReady) {
                    val snapshot = engine?.snapshot()
                    if (snapshot != null) {
                        send(
                            peerId,
                            playbackSession.playbackStateSync(snapshot, nowNs, recovery = true),
                        )
                    }
                }
            }

            is ProtocolBody.PlaybackStateSync ->
                if (peerId == coordinatorPeerId) {
                    val generation = sessionJobs.generation
                    launchSessionJob {
                        if (sessionJobs.isCurrent(generation)) canonicalPlayback.applyRemoteSync(body)
                    }
                }
            is ProtocolBody.PlaybackStatusReport ->
                if (isCoordinator()) {
                    val readinessChanged =
                        peerPlaybackHealth.updateParticipation(
                            peerId = peerId,
                            participation = body.participation,
                            nowNs = clock.nowNs(),
                        )
                    if (readinessChanged) reevaluateAllPreparation()
                    val generation = sessionJobs.generation
                    launchSessionJob {
                        if (sessionJobs.isCurrent(generation)) {
                            canonicalPlayback.handleStatusReport(
                                peerId,
                                body,
                                playbackExecutable = peerPlaybackHealth.isContentReady(peerId),
                            )
                        }
                    }
                }
            is ProtocolBody.TrackHave -> if (isCoordinator()) onTrackHave(peerId, body.trackId)
            is ProtocolBody.TrackNeed ->
                if (isCoordinator()) {
                    onTrackNeed(
                        peerId = peerId,
                        trackId = body.trackId,
                        priority = body.priority,
                        neededByCoordinatorNs = body.neededByCoordinatorNs,
                    )
                }
            is ProtocolBody.TrackSourceAssigned ->
                if (!isCoordinator() && peerId == coordinatorPeerId) onTrackSourceAssigned(body)

            is ProtocolBody.TrackSourceAuthorized ->
                if (isCoordinator()) onTrackSourceAuthorized(peerId, body)
            is ProtocolBody.TrackReady -> if (isCoordinator()) onTrackHave(peerId, body.trackId)
            is ProtocolBody.TrackFailed -> if (isCoordinator()) onTrackFailed(peerId, body)
            is ProtocolBody.LeaveRoom -> {
                if (!isCoordinator() && peerId == coordinatorPeerId) {
                    setRoomEnded(
                        body.reason?.takeIf { it.isNotBlank() }
                            ?: "The room host ended the room"
                    )
                    return
                }
                if (isCoordinator()) {
                    peerDisconnectGraceJobs.remove(peerId)?.cancel()
                    val snapshot = engine?.snapshot()
                    if (snapshot?.members?.any { it.peerId == peerId } == true) {
                        emitCanonical(ProtocolBody.PeerLeft(peerId))
                    }
                    peers.removePeer(peerId)
                    transferCoordinator.removePeer(peerId)
                    cancelTransferRetriesForPeer(peerId)
                    assignEligibleTransfers()
                    publishMemberRuntime()
                    broadcast(ProtocolBody.PeerDirectory(peerDirectory.values.toList()))
                }
                connections[peerId]?.close()
            }
            is ProtocolBody.RejoinRequest ->
                if (isCoordinator()) {
                    val snapshot = engine?.snapshot() ?: return
                    send(peerId, ProtocolBody.Snapshot(snapshot))
                    onTracksHaveInActor(peerId, body.cachedTrackIds.take(MAX_REJOIN_CACHE_IDS))
                    val latest = engine?.snapshot() ?: return
                    send(
                        peerId,
                        ProtocolBody.PlaybackReadinessChanged(
                            queueRevision = latest.queueRevision,
                            preparedQueueItemIds = preparedQueueItemIds,
                        ),
                    )
                    explicitPreparationQueueItemIds.forEach { queueItemId ->
                        send(peerId, ProtocolBody.QueueItemPreparationRequested(queueItemId))
                    }
                }

            is ProtocolBody.TrackDescriptorMessage ->
                if (!isCoordinator() && peerId == coordinatorPeerId) {
                    scheduleLocalAvailabilityProbe(body.descriptor.trackId)
                }

            is ProtocolBody.TransferCancelled -> {
                if (isCoordinator()) {
                    transferCoordinator.removeDemand(body.trackId, peerId)
                    cancelTransferRetry(body.trackId, peerId)
                    waitingForSource[body.trackId]?.remove(peerId)
                    assignNextForDestination(peerId)
                } else if (peerId == coordinatorPeerId) {
                    // Coordinator cancellation is reserved for lifecycle/explicit teardown. Normal
                    // demand-window changes no longer send this message in 1.2.0.
                    transferManager?.cancel(body.trackId, body.reason ?: "Coordinator cancelled transfer")
                }
            }
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
                    diagnostics.warn(
                        "room.canonical.rejected", null, "mutation.type" to body::class.simpleName,
                    )
                    sendToCoordinator(ProtocolBody.SnapshotRequest(snapshot.sequence))
                    return
                }
        updateSnapshot(updated)
        applyCanonicalRuntimeBookkeeping(body, updated)
        playbackDispatcher.submit(body, updated)
    }

    private fun scheduleLocalAvailabilityProbe(trackId: TrackId) {
        if (!announcedTrackIds.add(trackId)) return
        val generation = sessionJobs.generation
        launchSessionJob {
            val hasFile =
                withContext(Dispatchers.IO) {
                    suspendResult { container.trackRepository.requireReadableFile(trackId) != null }
                        .onFailure { error ->
                            diagnostics.warn(
                                "storage.track.read_failed", error,
                                "track.id" to trackId.value.take(12),
                            )
                        }
                        .getOrDefault(false)
                }
            roomEvents.submit(
                RoomEvent.LocalTrackAvailabilityProbed(
                    generation = generation,
                    trackId = trackId,
                    available = hasFile,
                )
            )
        }
    }

    private suspend fun processLocalTrackAvailabilityProbed(
        event: RoomEvent.LocalTrackAvailabilityProbed,
    ) {
        if (!sessionJobs.isCurrent(event.generation) || engine == null) return
        localTrackAvailability[event.trackId] = event.available
        val snapshot = engine?.snapshot()
        if (snapshot != null) publishMediaReadiness(snapshot)
        if (isCoordinator()) {
            if (event.available) {
                onTrackHaveInActor(identity.peerId, event.trackId)
            } else {
                val demand = desiredTransferDemands[event.trackId]
                onTrackNeedInActor(
                    peerId = identity.peerId,
                    trackId = event.trackId,
                    priority = demand?.priority ?: TransferPriority.BACKGROUND,
                    neededByCoordinatorNs = demand?.neededByCoordinatorNs,
                )
            }
        } else {
            val demand = desiredTransferDemands[event.trackId]
            sendToCoordinator(
                if (event.available) {
                    ProtocolBody.TrackHave(event.trackId)
                } else {
                    ProtocolBody.TrackNeed(
                        trackId = event.trackId,
                        priority = demand?.priority ?: TransferPriority.BACKGROUND,
                        neededByCoordinatorNs = demand?.neededByCoordinatorNs,
                    )
                }
            )
        }
        if (event.available && snapshot != null) {
            requestLocalPlaybackRecoveryIfCurrent(event.trackId, snapshot)
        }
    }

    private suspend fun onTrackHave(peerId: PeerId, trackId: TrackId) {
        if (roomEvents.isCurrentContext()) {
            onTrackHaveInActor(peerId, trackId)
            return
        }
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(
            RoomEvent.TrackAvailabilityObserved(
                peerId = peerId,
                trackId = trackId,
                available = true,
                completion = completion,
            )
        )
        completion.await()
    }

    private suspend fun onTrackHaveInActor(peerId: PeerId, trackId: TrackId) {
        recordTrackAvailability(peerId, trackId)
        transferCoordinator.finish(trackId, peerId)
        discardPendingTransferAssignments(trackId, peerId)
        cancelTransferRetry(trackId, peerId)
        assignWaiting(trackId)
        assignEligibleTransfers()
        reevaluatePreparation(trackId)
    }

    /** Applies reconnect cache knowledge in one actor turn and one preparation reconciliation. */
    private suspend fun onTracksHaveInActor(peerId: PeerId, trackIds: List<TrackId>) {
        val changedTrackIds =
            trackIds
                .asSequence()
                .distinct()
                .filter { trackId -> recordTrackAvailability(peerId, trackId) }
                .toList()
        trackIds.forEach {
            transferCoordinator.finish(it, peerId)
            cancelTransferRetry(it, peerId)
        }
        changedTrackIds.forEach { assignWaiting(it) }
        assignEligibleTransfers()
        if (changedTrackIds.isNotEmpty()) reevaluateAllPreparation()
    }

    private fun recordTrackAvailability(peerId: PeerId, trackId: TrackId): Boolean {
        val changed =
            availability.computeIfAbsent(trackId) { ConcurrentHashMap.newKeySet() }.add(peerId)
        waitingForSource[trackId]?.remove(peerId)
        return changed
    }

    private fun discardPendingTransferAssignments(trackId: TrackId, destinationPeerId: PeerId) {
        pendingTransferAssignments.entries
            .asSequence()
            .filter { (_, assignment) ->
                assignment.track.trackId == trackId &&
                    assignment.destinationPeerId == destinationPeerId
            }
            .map { it.key }
            .toList()
            .forEach { token -> pendingTransferAssignments.remove(token) }
    }

    private fun discardPendingTransferAssignmentsForPeer(peerId: PeerId) {
        pendingTransferAssignments.entries
            .asSequence()
            .filter { (_, assignment) ->
                assignment.source.peerId == peerId || assignment.destinationPeerId == peerId
            }
            .map { it.key }
            .toList()
            .forEach { token -> pendingTransferAssignments.remove(token) }
    }

    private suspend fun onTrackNeed(
        peerId: PeerId,
        trackId: TrackId,
        priority: TransferPriority = TransferPriority.BACKGROUND,
        neededByCoordinatorNs: Long? = null,
    ) {
        if (roomEvents.isCurrentContext()) {
            onTrackNeedInActor(peerId, trackId, priority, neededByCoordinatorNs)
            return
        }
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(
            RoomEvent.TrackAvailabilityObserved(
                peerId = peerId,
                trackId = trackId,
                available = false,
                priority = priority,
                neededByCoordinatorNs = neededByCoordinatorNs,
                completion = completion,
            )
        )
        completion.await()
    }

    private suspend fun onTrackNeedInActor(
        peerId: PeerId,
        trackId: TrackId,
        priority: TransferPriority = TransferPriority.BACKGROUND,
        neededByCoordinatorNs: Long? = null,
    ) {
        availability[trackId]?.remove(peerId)
        waitingForSource.computeIfAbsent(trackId) { ConcurrentHashMap.newKeySet() }.add(peerId)
        val nowNs = clock.nowNs()
        val sanitizedDeadline =
            neededByCoordinatorNs?.takeIf { it in nowNs..(nowNs + MAX_TRANSFER_DEADLINE_NS) }
        val demand =
            TransferDemand(
                trackId = trackId,
                destinationPeerId = peerId,
                priority = priority,
                neededByCoordinatorNs = sanitizedDeadline,
                requestedAtCoordinatorNs = nowNs,
            )
        transferCoordinator.upsert(demand)
        // Priorities decide what starts next. Once a route has been admitted, ordinary playback
        // demand changes do not tear down useful transfer work.
        reevaluatePreparation(trackId)
        assignNextForDestination(peerId)
    }

    /** A newly available source may unblock this track for several destinations. */
    private suspend fun assignWaiting(trackId: TrackId) {
        if (!isCoordinator()) return
        waitingForSource[trackId]
            ?.toList()
            ?.forEach { destination -> assignNextForDestination(destination) }
    }

    /**
     * Fills this destination's bounded transfer slots from highest playback consequence downward.
     * A demand without a usable source does not block a lower-priority demand that can make progress.
     */
    private suspend fun assignNextForDestination(destination: PeerId) {
        if (!isCoordinator()) return
        val snapshot = engine?.snapshot() ?: return
        while (true) {
            val candidate =
                transferCoordinator.pendingDemands(destination).firstNotNullOfOrNull { demand ->
                    val descriptor =
                        snapshot.queue.firstOrNull { it.track.trackId == demand.trackId }?.track
                            ?: return@firstNotNullOfOrNull null
                    val sourceId =
                        transferCoordinator.chooseSource(
                            demand = demand,
                            availableSources = availability[demand.trackId].orEmpty(),
                            nowCoordinatorNs = clock.nowNs(),
                            isUsable = { source ->
                                source == identity.peerId || connections.containsKey(source)
                            },
                        ) ?: return@firstNotNullOfOrNull null
                    Triple(demand, descriptor, sourceId)
                } ?: return
            val (demand, descriptor, sourceId) = candidate
            val source =
                if (sourceId == identity.peerId) localEndpointOrNull() ?: return
                else peerDirectory[sourceId] ?: return
            val token = Crypto.randomBase64(24)
            val assignment =
                ProtocolBody.TrackSourceAssigned(descriptor, source, destination, token)
            val route = TransferRouteKey(demand.trackId, sourceId, destination)
            transferCoordinator.markActive(route)
            val assignmentId = Crypto.fileTransferAuthorizationId(token).take(16)
            diagnostics.info(
                "transfer.assignment.created",
                "transfer.assignment_id" to assignmentId,
                "track.id" to demand.trackId.value.take(12),
                "transfer.source_peer_id" to sourceId.value.take(12),
                "transfer.destination_peer_id" to destination.value.take(12),
                "transfer.priority" to demand.priority.name,
                "transfer.needed_by_coordinator_ns" to demand.neededByCoordinatorNs,
            )
            val expiresAt = SystemClock.elapsedRealtime() + TRANSFER_TOKEN_LIFETIME_MS

            if (sourceId == identity.peerId) {
                transferManager?.authorize(
                    snapshot.roomId,
                    demand.trackId,
                    destination,
                    token,
                    expiresAt,
                )
                deliverTransferAssignment(assignment)
            } else {
                pendingTransferAssignments[token] = assignment
                send(sourceId, assignment)
                val generation = sessionJobs.generation
                launchSessionJob {
                    delay(SOURCE_AUTHORIZATION_TIMEOUT_MS)
                    roomEvents.submit(
                        RoomEvent.TransferAuthorizationTimedOut(
                            generation = generation,
                            authorizationToken = token,
                        )
                    )
                }
            }
        }
    }

    private suspend fun processTransferAuthorizationTimedOut(
        event: RoomEvent.TransferAuthorizationTimedOut,
    ) {
        if (!sessionJobs.isCurrent(event.generation)) return
        val expired = pendingTransferAssignments.remove(event.authorizationToken) ?: return
        val route =
            TransferRouteKey(
                trackId = expired.track.trackId,
                sourcePeerId = expired.source.peerId,
                destinationPeerId = expired.destinationPeerId,
            )
        diagnostics.warn(
            "transfer.assignment.authorization_timeout",
            null,
            "transfer.assignment_id" to Crypto.fileTransferAuthorizationId(event.authorizationToken).take(16),
            "track.id" to expired.track.trackId.value.take(12),
            "transfer.source_peer_id" to expired.source.peerId.value.take(12),
            "transfer.destination_peer_id" to expired.destinationPeerId.value.take(12),
        )
        val retryAt = transferCoordinator.recordRouteFailure(route, clock.nowNs())
        waitingForSource
            .computeIfAbsent(expired.track.trackId) { ConcurrentHashMap.newKeySet() }
            .add(expired.destinationPeerId)
        scheduleTransferRetry(expired.track.trackId, expired.destinationPeerId, retryAt)
        assignEligibleTransfers()
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
        diagnostics.warn(
            "transfer.route.failed",
            null,
            "track.id" to failure.trackId.value.take(12),
            "transfer.source_peer_id" to failure.sourcePeerId?.value?.take(12),
            "transfer.destination_peer_id" to peerId.value.take(12),
            "transfer.phase" to failure.stage.name,
            "transfer.failure_code" to failure.code.name,
            "transfer.failure_blame" to failure.blame.name,
            "transfer.retryable" to failure.retryable,
        )
        availability[failure.trackId]?.remove(peerId)
        val sourcePeerId = failure.sourcePeerId
        val nowCoordinatorNs = clock.nowNs()
        val retryAtCoordinatorNs =
            if (sourcePeerId != null) {
                val route = TransferRouteKey(failure.trackId, sourcePeerId, peerId)
                when (failure.blame) {
                    TransferFailureBlame.ROUTE ->
                        transferCoordinator.recordRouteFailure(route, nowCoordinatorNs)

                    TransferFailureBlame.SOURCE -> {
                        transferCoordinator.markTerminal(failure.trackId, peerId)
                        if (failure.code == TransferFailureCode.SOURCE_UNAVAILABLE) {
                            availability[failure.trackId]?.remove(sourcePeerId)
                        }
                        nowCoordinatorNs
                    }

                    TransferFailureBlame.DESTINATION,
                    TransferFailureBlame.UNKNOWN -> {
                        // A local validation/state problem is not evidence that the network route
                        // is unhealthy. Release the assignment without poisoning route health.
                        transferCoordinator.markTerminal(failure.trackId, peerId)
                        nowCoordinatorNs + BASE_TRANSFER_RETRY_DELAY_MS * 1_000_000L
                    }
                }
            } else {
                transferCoordinator.markTerminal(failure.trackId, peerId)
                nowCoordinatorNs + BASE_TRANSFER_RETRY_DELAY_MS * 1_000_000L
            }

        val demandStillWanted = transferCoordinator.demandFor(failure.trackId, peerId) != null
        if (!failure.retryable && failure.blame == TransferFailureBlame.DESTINATION) {
            transferCoordinator.finish(failure.trackId, peerId)
            cancelTransferRetry(failure.trackId, peerId)
            discardPendingTransferAssignments(failure.trackId, peerId)
            waitingForSource[failure.trackId]?.remove(peerId)
            if (peerId == identity.peerId) {
                val snapshot = engine?.snapshot()
                pendingSuccessor
                    ?.takeIf { pending ->
                        snapshot?.queue
                            ?.firstOrNull { it.queueItemId == pending.targetQueueItemId }
                            ?.track
                            ?.trackId == failure.trackId
                    }
                    ?.let {
                        cancelPendingSuccessor(
                            message = "Next song could not be prepared",
                            phase = TransportCommandPhase.REJECTED,
                        )
                    }
                setIssue(
                    RoomIssue(
                        code = RoomIssueCode.PLAYBACK_TRACK_UNAVAILABLE,
                        message = failure.reason,
                        severity = RoomIssueSeverity.WARNING,
                        recoveryAction = RoomRecoveryAction.NONE,
                        deduplicationKey = "transfer-destination:${failure.trackId.value}",
                    )
                )
            }
        } else if (demandStillWanted) {
            waitingForSource
                .computeIfAbsent(failure.trackId) { ConcurrentHashMap.newKeySet() }
                .add(peerId)
        } else {
            waitingForSource[failure.trackId]?.remove(peerId)
            cancelTransferRetry(failure.trackId, peerId)
        }
        reevaluatePreparation(failure.trackId)
        if (failure.retryable && demandStillWanted) {
            scheduleTransferRetry(failure.trackId, peerId, retryAtCoordinatorNs)
        }
        assignEligibleTransfers()
    }

    private fun cancelTransferRetry(trackId: TrackId, destinationPeerId: PeerId) {
        if (transferRetryDeadlines.remove(trackId to destinationPeerId) != null) {
            rescheduleTransferRetryWakeup()
        }
    }

    private fun cancelTransferRetriesForPeer(peerId: PeerId) {
        val changed = transferRetryDeadlines.keys.removeAll { (_, destination) -> destination == peerId }
        if (changed) rescheduleTransferRetryWakeup()
    }

    private fun clearTransferRetries() {
        transferRetryWakeupJob?.cancel()
        transferRetryWakeupJob = null
        transferRetryDeadlines.clear()
    }

    private fun scheduleTransferRetry(
        trackId: TrackId,
        destinationPeerId: PeerId,
        retryAtCoordinatorNs: Long,
    ) {
        val key = trackId to destinationPeerId
        transferRetryDeadlines[key] = retryAtCoordinatorNs
        val delayMs =
            ((retryAtCoordinatorNs - clock.nowNs()).coerceAtLeast(0L) / 1_000_000L)
                .coerceAtLeast(1L)
        diagnostics.debug(
            "transfer.retry.scheduled",
            "track.id" to trackId.value.take(12),
            "transfer.destination_peer_id" to destinationPeerId.value.take(12),
            "transfer.retry_delay_ms" to delayMs,
        )
        rescheduleTransferRetryWakeup()
    }

    /** One session timer wakes the actor for the earliest retry; the coordinator owns all deadlines. */
    private fun rescheduleTransferRetryWakeup() {
        transferRetryWakeupJob?.cancel()
        transferRetryWakeupJob = null
        val (key, retryAtCoordinatorNs) =
            transferRetryDeadlines.minByOrNull { it.value } ?: return
        val generation = sessionJobs.generation
        val delayMs =
            ((retryAtCoordinatorNs - clock.nowNs()).coerceAtLeast(0L) / 1_000_000L)
                .coerceAtLeast(1L)
        transferRetryWakeupJob =
            launchSessionJob {
                delay(delayMs)
                roomEvents.submit(RoomEvent.TransferRetryDue(generation, key.first, key.second))
            }
    }

    /** Reconsiders every waiting destination when source capacity is released. */
    private suspend fun assignEligibleTransfers() {
        transferCoordinator.pendingDestinations().forEach { assignNextForDestination(it) }
    }

    private fun shuffleRunwayQueueItemId(snapshot: RoomSnapshot): QueueItemId? {
        val currentIndex = snapshot.queue.indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
        if (currentIndex < 0) return null
        val current = snapshot.queue[currentIndex]
        val next = snapshot.queue.getOrNull(currentIndex + 1) ?: return null
        if (next.queueItemId !in preparedQueueItemIds) return null
        val durationMs = current.track.durationMs.takeIf { it > 0L } ?: return next.queueItemId
        val remainingMs =
            (durationMs - snapshot.playback.projectedPositionMs(clock.nowNs())).coerceAtLeast(0L)
        return next.queueItemId.takeIf { remainingMs <= SHUFFLE_RUNWAY_PRESERVE_THRESHOLD_MS }
    }

    private fun canApplyScheduledCommand(): Boolean =
        roleEngine().canApplyScheduledCommand(clockSync.estimate(clock.nowNs()))

    private suspend fun onLocalTrackReady(descriptor: TrackDescriptor) {
        announcedTrackIds.add(descriptor.trackId)
        localTrackAvailability[descriptor.trackId] = true
        val snapshot = engine?.snapshot() ?: return
        publishMediaReadiness(snapshot)
        requestTimelineRefresh(snapshot)
        if (isCoordinator()) onTrackHave(identity.peerId, descriptor.trackId)
        else sendToCoordinator(ProtocolBody.TrackReady(descriptor.trackId))
        requestLocalPlaybackRecoveryIfCurrent(descriptor.trackId, snapshot)
        launchSessionJob { localPlaybackParticipation.tryPendingRejoin() }
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
                diagnostics.warn(
                    "playback.preparation.failed", error, "preparation.phase" to phase.lowercase(),
                )
                setError(
                    if (initialJoin) "Connected; some music may need to be prepared again"
                    else "Room restored; some music may need to be prepared again"
                )
            }
        }
    }

    private suspend fun reconcileSnapshotQueue(snapshot: RoomSnapshot) {
        playerExecutor.maintenance { setRepeatCurrentItem(false) }
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
                windowQueue
                    .asSequence()
                    .map { it.track.trackId }
                    .distinct()
                    .associateWith { trackId ->
                        suspendResult { container.trackRepository.requireReadableFile(trackId) }
                            .onFailure { error ->
                                diagnostics.warn(
                                    "storage.track.load_failed", error,
                                    "track.id" to trackId.value.take(12),
                                )
                            }
                            .getOrNull()
                    }
            }
        val allowedByRoom =
            PlaybackQueuePolicy.playableItems(
                snapshot = snapshot.copy(queue = windowQueue),
                readableTrackIds = readable.filterValues { it != null }.keys,
                preparedQueueItemIds = preparedQueueItemIds,
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
                ?: localBeforeApply.queueItemId.takeIf { playerExecutor.hasPendingTransport }
        val protectedPositionMs =
            preferredPositionMs
                ?: localBeforeApply.positionMs.takeIf { playerExecutor.hasPendingTransport }
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
                else -> projectedCanonicalPosition(snapshot)
            }
        val applied = playerExecutor.maintenanceIfTransportIdle {
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
                        playerExecutor.hasPendingTransport
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
                    .onFailure { error -> diagnostics.warn("storage.recent_update.failed", error) }
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

    private suspend fun requestQueueItemPreparation(
        queueItemId: QueueItemId,
        requestId: String? = null,
    ) {
        val snapshot = engine?.snapshot()
            ?: run {
                setError("Join or create a room first")
                return
            }
        val item = snapshot.queue.firstOrNull { it.queueItemId == queueItemId }
            ?: run {
                setError("That song is no longer in the queue")
                return
            }
        val newlyRequested = queueItemId !in explicitPreparationQueueItemIds
        explicitPreparationQueueItemIds += queueItemId
        publishMediaReadiness(snapshot)
        prepareWindow(snapshot, priorityQueueItemId = queueItemId)
        if (!newlyRequested) {
            if (isCoordinator()) reevaluatePreparation(item.track.trackId)
            return
        }
        if (isCoordinator()) {
            broadcast(
                ProtocolBody.QueueItemPreparationRequested(
                    queueItemId = queueItemId,
                    commandId = requestId,
                )
            )
            reevaluatePreparation(item.track.trackId)
        } else {
            sendToCoordinator(
                ProtocolBody.QueueItemPreparationRequested(
                    queueItemId = queueItemId,
                    commandId = requestId,
                )
            )
        }
        diagnostics.info(
            "room.media.prepare_requested",
            "queue.item_id" to queueItemId.value.take(12),
            "track.id" to item.track.trackId.value.take(12),
        )
    }

    private fun publishMediaReadiness(snapshot: RoomSnapshot) {
        val validIds = snapshot.queue.mapTo(hashSetOf()) { it.queueItemId }
        explicitPreparationQueueItemIds = explicitPreparationQueueItemIds.intersect(validIds)
        val locallyAvailable =
            localTrackAvailability.asSequence()
                .filter { it.value }
                .mapTo(hashSetOf()) { it.key }
        val readiness =
            RoomMediaReadinessPolicy.derive(
                snapshot = snapshot,
                roomReadyQueueItemIds = preparedQueueItemIds,
                explicitPreparationQueueItemIds = explicitPreparationQueueItemIds,
                locallyAvailableTrackIds = locallyAvailable,
            )
        container.roomStore.updateStructure { state ->
            if (state.mediaReadiness == readiness) state else state.copy(mediaReadiness = readiness)
        }
    }

    private suspend fun isQueueItemLocallyExecutable(queueItemId: QueueItemId): Boolean {
        val snapshot = engine?.snapshot() ?: return false
        val trackId = snapshot.queue.firstOrNull { it.queueItemId == queueItemId }?.track?.trackId
            ?: return false
        return localTrackAvailability[trackId] == true
    }

    private suspend fun isCanonicalCurrentLocallyExecutable(): Boolean {
        val queueItemId = engine?.snapshot()?.playback?.queueItemId ?: return true
        return isQueueItemLocallyExecutable(queueItemId)
    }

    private fun requestLocalPlaybackRecoveryIfCurrent(trackId: TrackId, snapshot: RoomSnapshot) {
        val currentTrackId = snapshot.playback.queueItemId
            ?.let { currentId -> snapshot.queue.firstOrNull { it.queueItemId == currentId } }
            ?.track
            ?.trackId
            ?: return
        if (currentTrackId != trackId) return
        val generation = sessionJobs.generation
        launchSessionJob {
            if (sessionJobs.isCurrent(generation)) {
                canonicalPlayback.reconcileLocalExecution("MEDIA_READY")
            }
        }
    }

    private suspend fun prepareWindow(
        snapshot: RoomSnapshot,
        priorityQueueItemId: QueueItemId? = null,
    ) {
        val hasCoordinatorClock = isCoordinator() || clockSync.synchronized
        val nowNs =
            when {
                isCoordinator() -> clock.nowNs()
                clockSync.synchronized -> clockSync.coordinatorNowNs()
                else -> snapshot.playback.coordinatorTimestampNs.coerceAtLeast(0L)
            }
        val demands =
            TrackPrefetchPolicy.transferDemands(
                snapshot = snapshot,
                destinationPeerId = identity.peerId,
                coordinatorNowNs = nowNs,
                priorityQueueItemId = priorityQueueItemId,
                upcomingCount =
                    snapshot.options.preloadCount.coerceAtMost(
                        TrackPrefetchPolicy.DEFAULT_UPCOMING_COUNT
                    ),
            ).map { demand ->
                if (hasCoordinatorClock) demand else demand.copy(neededByCoordinatorNs = null)
            }
        val nextDemandMap = demands.associateBy { it.trackId }
        val obsoleteTrackIds = desiredTransferDemands.keys - nextDemandMap.keys
        obsoleteTrackIds.forEach { obsolete ->
            if (isCoordinator()) {
                transferCoordinator.removeDemand(obsolete, identity.peerId)
                cancelTransferRetry(obsolete, identity.peerId)
                waitingForSource[obsolete]?.remove(identity.peerId)
                assignNextForDestination(identity.peerId)
            } else {
                sendToCoordinator(
                    ProtocolBody.TransferCancelled(
                        obsolete,
                        "No longer in the playback demand window",
                    )
                )
            }
        }
        desiredTransferDemands = nextDemandMap
        demands.forEach { demand ->
            when (localTrackAvailability[demand.trackId]) {
                true -> Unit
                false -> {
                    if (isCoordinator()) {
                        onTrackNeedInActor(
                            identity.peerId,
                            demand.trackId,
                            demand.priority,
                            demand.neededByCoordinatorNs,
                        )
                    } else {
                        sendToCoordinator(
                            ProtocolBody.TrackNeed(
                                demand.trackId,
                                demand.priority,
                                demand.neededByCoordinatorNs,
                            )
                        )
                    }
                }
                null -> scheduleLocalAvailabilityProbe(demand.trackId)
            }
        }
        launchSessionJob {
            withContext(Dispatchers.IO) {
                demands.map { it.trackId }.distinct().forEach { trackId ->
                    suspendResult { container.trackRepository.touchTemporary(trackId) }
                        .onFailure { error ->
                            diagnostics.warn(
                                "storage.retention_touch.failed", error,
                                "track.id" to trackId.value.take(12),
                            )
                        }
                }
            }
        }
    }

    private suspend fun reevaluatePreparation(trackId: TrackId) {
        if (!isCoordinator()) return
        val snapshot = engine?.snapshot() ?: return
        val connectedPeers = connectedPeerIds(snapshot).toMutableSet()
        val nowNs = clock.nowNs()
        refreshPlaybackAdmissions(snapshot, connectedPeers, nowNs)
        val audibleCohort =
            peerPlaybackHealth.readyPeers(
                connectedPeers, identity.peerId, player.state.value.participation, nowNs)
        val readinessCohort =
            peerPlaybackHealth.contentReadinessPeers(connectedPeers, identity.peerId, nowNs)
        val readyPeers = availability[trackId].orEmpty()
        val shouldPrepare =
            readinessCohort.isNotEmpty() && readyPeers.containsAll(readinessCohort)
        diagnostics.debug(
            "playback.preparation.status", "track.id" to trackId.value.take(12),
            "playback.ready_members" to readyPeers.count { it in readinessCohort },
            "playback.readiness_members" to readinessCohort.size,
            "playback.cohort_members" to audibleCohort.size,
            "room.connected_members" to connectedPeers.size,
            "playback.prepared" to shouldPrepare,
        )
        val affectedItems = snapshot.queue.filter { it.track.trackId == trackId }
        if (affectedItems.isEmpty()) return
        val affectedIds = affectedItems.mapTo(hashSetOf()) { it.queueItemId }
        val desiredPrepared =
            if (shouldPrepare) preparedQueueItemIds + affectedIds
            else preparedQueueItemIds - affectedIds
        updatePlaybackReadiness(snapshot, desiredPrepared)
        if (shouldPrepare && affectedIds.any { it in preparedQueueItemIds }) {
            completePendingSuccessorIfReady()
        }
    }

    /** A peer joins the playback cohort only after it has the current item and one item of runway. */
    private fun refreshPlaybackAdmissions(
        snapshot: RoomSnapshot,
        connectedPeers: Set<PeerId>,
        nowNs: Long,
    ) {
        val currentIndex =
            snapshot.queue.indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
                .let { if (it >= 0) it else 0 }
        val requiredTrackIds =
            buildList {
                snapshot.queue.getOrNull(currentIndex)?.track?.trackId?.let(::add)
                snapshot.queue.getOrNull(currentIndex + 1)?.track?.trackId?.let { next ->
                    if (next !in this) add(next)
                }
            }
        connectedPeers.asSequence()
            .filter { it != identity.peerId }
            .forEach { peerId ->
                val contentReady =
                    requiredTrackIds.isEmpty() ||
                        requiredTrackIds.all { trackId -> peerId in availability[trackId].orEmpty() }
                peerPlaybackHealth.updateContentReady(peerId, contentReady, nowNs)
            }
    }

    private suspend fun reevaluateAllPreparation() {
        val snapshot = engine?.snapshot() ?: return
        if (!isCoordinator()) return
        val connectedPeers = connectedPeerIds(snapshot).toHashSet()
        val nowNs = clock.nowNs()
        refreshPlaybackAdmissions(snapshot, connectedPeers, nowNs)
        val readinessCohort =
            peerPlaybackHealth.contentReadinessPeers(connectedPeers, identity.peerId, nowNs)
        val desired =
            if (readinessCohort.isEmpty()) emptySet()
            else
                snapshot.queue
                    .asSequence()
                    .filter { item ->
                        availability[item.track.trackId].orEmpty().containsAll(readinessCohort)
                    }
                    .mapTo(linkedSetOf()) { it.queueItemId }
        updatePlaybackReadiness(snapshot, desired)
        completePendingSuccessorIfReady()
    }

    private suspend fun updatePlaybackReadiness(
        snapshot: RoomSnapshot,
        desired: Set<QueueItemId>,
    ) {
        val valid = desired.intersect(snapshot.queue.mapTo(hashSetOf()) { it.queueItemId })
        if (
            valid == preparedQueueItemIds &&
                playbackReadinessQueueRevision == snapshot.queueRevision
        ) return
        preparedQueueItemIds = valid
        playbackReadinessQueueRevision = snapshot.queueRevision
        publishMediaReadiness(snapshot)
        playbackDispatcher.reconcile(
            snapshot,
            CanonicalPlaybackDispatcher.Trigger.PREPARATION_CHANGED,
        )
        if (isCoordinator()) {
            broadcast(
                ProtocolBody.PlaybackReadinessChanged(
                    queueRevision = snapshot.queueRevision,
                    preparedQueueItemIds = valid,
                )
            )
        }
    }

    private suspend fun applyPlaybackReadiness(body: ProtocolBody.PlaybackReadinessChanged) {
        val snapshot = engine?.snapshot() ?: return
        if (body.queueRevision != snapshot.queueRevision) return
        val valid = body.preparedQueueItemIds.intersect(
            snapshot.queue.mapTo(hashSetOf()) { it.queueItemId }
        )
        if (
            valid == preparedQueueItemIds &&
                playbackReadinessQueueRevision == body.queueRevision
        ) return
        preparedQueueItemIds = valid
        playbackReadinessQueueRevision = body.queueRevision
        publishMediaReadiness(snapshot)
        playbackDispatcher.reconcile(
            snapshot,
            CanonicalPlaybackDispatcher.Trigger.PREPARATION_CHANGED,
        )
    }

    private fun ProtocolBody.changesPlaybackReadinessInputs(): Boolean =
        when (this) {
            is ProtocolBody.PeerJoined,
            is ProtocolBody.PeerLeft,
            is ProtocolBody.QueueItemsAdded,
            is ProtocolBody.QueueItemsRemoved,
            is ProtocolBody.QueueItemMoved,
            ProtocolBody.QueueCleared,
            is ProtocolBody.QueueShuffled -> true
            else -> false
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

    private suspend fun cancelPendingSuccessor(
        message: String,
        phase: TransportCommandPhase = TransportCommandPhase.SUPERSEDED,
    ) {
        val pending = pendingSuccessor ?: return
        pendingSuccessor = null
        container.roomStore.updateStructure { it.copy(pendingSuccessorQueueItemId = null) }
        val commandId = pending.commandId
        val requestedBy = pending.requestedBy
        if (commandId != null && requestedBy != null) {
            publishTransportStatus(
                requestedBy = requestedBy,
                commandId = commandId,
                action = TransportAction.NEXT,
                phase = phase,
                queueItemId = pending.targetQueueItemId,
                message = message,
            )
        }
        diagnostics.info(
            "playback.successor.cancelled",
            "queue.item_id" to pending.targetQueueItemId.value.take(12),
            "playback.successor_reason" to pending.reason.name,
            "transport.message" to message.take(160),
        )
    }

    private suspend fun replacePendingSuccessor(next: PendingSuccessor) {
        val previous = pendingSuccessor
        val previousCommandId = previous?.commandId
        val previousRequestedBy = previous?.requestedBy
        if (
            previousCommandId != null &&
                previousCommandId != next.commandId &&
                previousRequestedBy != null
        ) {
            publishTransportStatus(
                requestedBy = previousRequestedBy,
                commandId = previousCommandId,
                action = TransportAction.NEXT,
                phase = TransportCommandPhase.SUPERSEDED,
                queueItemId = previous.targetQueueItemId,
                message = "Replaced by a newer Next request",
            )
        }
        pendingSuccessor = next
        container.roomStore.updateStructure {
            it.copy(pendingSuccessorQueueItemId = next.targetQueueItemId)
        }
        diagnostics.info(
            "playback.successor.pending",
            "queue.item_id" to next.targetQueueItemId.value.take(12),
            "playback.successor_reason" to next.reason.name,
            "playback.resume_when_ready" to next.resumeWhenReady,
        )
    }

    private fun pendingSuccessorStillValid(
        snapshot: RoomSnapshot,
        pending: PendingSuccessor,
    ): Boolean {
        if (snapshot.playback.queueItemId != pending.fromQueueItemId) return false
        val expectedTarget =
            when (pending.reason) {
                PendingSuccessorReason.NATURAL_END ->
                    PlaybackQueuePolicy.naturalSuccessorQueueItemId(
                        snapshot, pending.fromQueueItemId
                    )
                PendingSuccessorReason.USER_NEXT ->
                    PlaybackQueuePolicy.immediateNextQueueItemId(
                        snapshot, pending.fromQueueItemId
                    )
            }
        return expectedTarget == pending.targetQueueItemId
    }

    private suspend fun revalidatePendingSuccessor(snapshot: RoomSnapshot) {
        val pending = pendingSuccessor ?: return
        if (!pendingSuccessorStillValid(snapshot, pending)) {
            cancelPendingSuccessor("Queue changed before the next song was ready")
        }
    }

    private suspend fun beginPendingSuccessor(pending: PendingSuccessor) {
        replacePendingSuccessor(pending)
        val commandId = pending.commandId
        val requestedBy = pending.requestedBy
        if (commandId != null && requestedBy != null) {
            publishTransportStatus(
                requestedBy = requestedBy,
                commandId = commandId,
                action = TransportAction.NEXT,
                phase = TransportCommandPhase.ACCEPTED,
                queueItemId = pending.targetQueueItemId,
                message = "Preparing next song",
            )
        }
        requestQueueItemPreparation(
            queueItemId = pending.targetQueueItemId,
            requestId = pending.commandId,
        )
        completePendingSuccessorIfReady()
    }

    private suspend fun completePendingSuccessorIfReady() {
        val pending = pendingSuccessor ?: return
        val latest = engine?.snapshot() ?: return
        if (!pendingSuccessorStillValid(latest, pending)) {
            cancelPendingSuccessor("Queue changed before the next song was ready")
            return
        }
        if (pending.targetQueueItemId !in preparedQueueItemIds) return

        pendingSuccessor = null
        container.roomStore.updateStructure { it.copy(pendingSuccessorQueueItemId = null) }
        val commandId = pending.commandId
        val requestedBy = pending.requestedBy
        if (commandId != null && requestedBy != null) {
            publishTransportStatus(
                requestedBy = requestedBy,
                commandId = commandId,
                action = TransportAction.NEXT,
                phase = TransportCommandPhase.ACCEPTED,
                queueItemId = pending.targetQueueItemId,
                message = "Next song is ready",
            )
        }
        diagnostics.info(
            "playback.successor.ready",
            "queue.item_id" to pending.targetQueueItemId.value.take(12),
            "playback.successor_reason" to pending.reason.name,
        )
        emitCanonical(
            ProtocolBody.CurrentItemChanged(
                queueItemId = pending.targetQueueItemId,
                positionMs = 0,
                executeAtCoordinatorNs = clock.nowNs() + transportLeadNs(latest),
                resumePlayback = pending.resumeWhenReady,
                commandId = commandId,
            )
        )
    }

    private suspend fun applyCanonicalRuntimeBookkeeping(
        body: ProtocolBody,
        updated: RoomSnapshot,
    ) {
        revalidateTerminalNaturalPause(updated)
        if (body == ProtocolBody.QueueCleared) {
            cancelPendingSuccessor("Queue was cleared")
            return
        }
        revalidatePendingSuccessor(updated)
    }

    private fun revalidateTerminalNaturalPause(snapshot: RoomSnapshot) {
        val marker = terminalNaturalPause ?: return
        if (!TerminalReplayPolicy.isStillValid(snapshot, marker)) terminalNaturalPause = null
    }

    private suspend fun applyCanonicalMutation(body: ProtocolBody) {
        if (!isCoordinator()) return
        val snapshot = engine?.snapshot() ?: return
        val sequence = snapshot.sequence + 1
        val updated =
            engine?.applyValidated(sequence, body, ::snapshotFitsProtocol)
                ?: run {
                    diagnostics.warn(
                        "room.canonical.local_rejected", null, "mutation.type" to body::class.simpleName,
                    )
                    return
                }
        updateSnapshot(updated)
        applyCanonicalRuntimeBookkeeping(body, updated)
        broadcastCanonical(sequence, body)
        playbackDispatcher.submit(body, updated)
        if (body.changesPlaybackReadinessInputs()) reevaluateAllPreparation()
    }

    private suspend fun recordNaturalPlaybackEnded(
        queueItemId: QueueItemId,
        positionMs: Long,
        durationMs: Long,
    ) {
        val snapshot = engine?.snapshot() ?: return
        val plan =
            PlaybackQueuePolicy.planNaturalEnd(
                snapshot = snapshot,
                preparedQueueItemIds = preparedQueueItemIds,
                endedQueueItemId = queueItemId,
                positionMs = positionMs,
                durationMs = durationMs,
                coordinatorNowNs = clock.nowNs(),
                leadNs = transportLeadNs(snapshot),
            ) ?: return

        val waitTarget = plan.waitForQueueItemId
        if (waitTarget != null) {
            val existing = pendingSuccessor
            if (
                existing == null ||
                    existing.fromQueueItemId != queueItemId ||
                    existing.targetQueueItemId != waitTarget
            ) {
                replacePendingSuccessor(
                    PendingSuccessor(
                        fromQueueItemId = queueItemId,
                        targetQueueItemId = waitTarget,
                        reason = PendingSuccessorReason.NATURAL_END,
                        resumeWhenReady = true,
                    )
                )
            }
            // Canonical time must stop at the physical boundary before preparation can complete.
            emitCanonical(plan.mutation)
            val pending = pendingSuccessor
            if (pending != null && pending.targetQueueItemId == waitTarget) {
                requestQueueItemPreparation(waitTarget, pending.commandId)
                completePendingSuccessorIfReady()
            }
            return
        }

        val immediateTarget =
            (plan.mutation as? ProtocolBody.CurrentItemChanged)?.queueItemId
        val pending = pendingSuccessor
        if (
            immediateTarget != null &&
                pending != null &&
                pending.fromQueueItemId == queueItemId &&
                pending.targetQueueItemId == immediateTarget
        ) {
            // A user Next request may have become ready at the same instant as the natural end.
            completePendingSuccessorIfReady()
            if (pendingSuccessor == null) return
        }
        cancelPendingSuccessor("Natural playback moved to a different successor")
        emitCanonical(plan.mutation)
        if (plan.mutation is ProtocolBody.PauseScheduled && immediateTarget == null) {
            val pausedSnapshot = engine?.snapshot() ?: return
            terminalNaturalPause = TerminalReplayPolicy.capture(pausedSnapshot, queueItemId)
        }
    }

    private fun hasOtherActivePlaybackListener(
        snapshot: RoomSnapshot,
        localPeerId: PeerId,
        nowNs: Long,
    ): Boolean =
        connectedPeerIds(snapshot).asSequence()
            .filter { it != localPeerId }
            .any { peerPlaybackHealth.isSynchronizationParticipant(it, nowNs) }

    private suspend fun runPlaybackSynchronizationTick(
        snapshot: RoomSnapshot,
        coordinator: Boolean,
        connected: Boolean,
        generation: Long,
        localPeerId: PeerId,
    ) {
        localPlaybackParticipation.tryPendingRejoin()
        val soloCoordinator =
            coordinator && !hasOtherActivePlaybackListener(snapshot, localPeerId, clock.nowNs())
        val soloModeChanged =
            localPlaybackSync.setSoloCoordinatorMode(
                enabled = soloCoordinator,
                canonical = snapshot.playback.takeIf { coordinator },
            )
        if (soloModeChanged) {
            syncDiagnostics.clear()
            container.roomStore.updatePlayback { it.copy(localDriftMs = null) }
            diagnostics.info(
                if (soloCoordinator) "sync.solo_coordinator.quiet"
                else "sync.solo_coordinator.reacquire",
                "room.member_count" to snapshot.members.size,
            )
        }
        if (soloCoordinator) return

        when (val result = localPlaybackSync.tick(snapshot, coordinator, connected)) {
            is LocalPlaybackSyncController.TickResult.Reacquiring -> {
                syncDiagnostics.clear()
                container.roomStore.updatePlayback { it.copy(localDriftMs = null) }
                diagnostics.info("sync.reacquire.required", "reason" to result.reason)
                return
            }

            is LocalPlaybackSyncController.TickResult.Evaluated -> {
                container.roomStore.updatePlayback { state ->
                    state.copy(localDriftMs = result.decision.rawDriftMs)
                }
                syncDiagnostics.record(
                    SynchronizationEventFactory.create(
                        snapshot = snapshot,
                        localPeerId = localPeerId,
                        deviceModel = diagnosticDeviceModel,
                        androidVersion = diagnosticAndroidVersion,
                        sampleCoordinatorNs = result.sampleCoordinatorNs,
                        samplePositionMs = result.sample.positionMs,
                        sampleAtLocalNs = result.sample.sampledAtLocalNs,
                        observedAtLocalNs = clock.nowNs(),
                        outputRoute = result.sample.outputRoute,
                        buffering =
                            result.sample.activityState == PlaybackActivityState.BUFFERING,
                        canonicalPositionMs = result.canonicalPositionMs,
                        clockEstimate = result.clockEstimate,
                        decision = result.decision,
                    )
                )
                if (localPlaybackSync.shouldReportPlaybackStatus(result.sample.sampledAtLocalNs)) {
                    roomEvents.submit(RoomEvent.LocalPlaybackStatusDue(generation))
                }
                if (
                    coordinator &&
                        localPlaybackSync.shouldBroadcastPlaybackReference(clock.nowNs())
                ) {
                    roomEvents.submit(RoomEvent.PlaybackReferenceBroadcastDue(generation))
                }
            }
        }
    }

    /**
     * Low-frequency convergence receipts stay actor-owned. The independent local sync loop only
     * requests a check; the actor constructs the receipt from the latest player/canonical state so
     * actor delay cannot turn an old sample into a false repair.
     */
    private suspend fun processLocalPlaybackStatusDue() {
        val snapshot = engine?.snapshot() ?: return
        val value = player.state.value
        val report = localPlaybackParticipation.statusReport(value, snapshot)
        if (isCoordinator()) {
            val generation = sessionJobs.generation
            launchSessionJob {
                if (sessionJobs.isCurrent(generation)) {
                    canonicalPlayback.handleStatusReport(
                        identity.peerId,
                        report,
                        playbackExecutable = isCanonicalCurrentLocallyExecutable(),
                    )
                }
            }
        } else {
            sendToCoordinator(report)
        }
    }

    private suspend fun processPlaybackReferenceBroadcastDue() {
        val snapshot = engine?.snapshot() ?: return
        if (snapshot.term.coordinatorPeerId != identity.peerId) return
        broadcast(playbackSession.playbackStateSync(snapshot, clock.nowNs()))
    }

    private suspend fun publishLocalPlaybackStatus(report: ProtocolBody.PlaybackStatusReport) {
        if (isCoordinator()) {
            val generation = sessionJobs.generation
            launchSessionJob {
                if (sessionJobs.isCurrent(generation)) {
                    canonicalPlayback.handleStatusReport(
                        identity.peerId,
                        report,
                        playbackExecutable = isCanonicalCurrentLocallyExecutable(),
                    )
                }
            }
        } else if (coordinatorConnection != null) {
            sendToCoordinator(report)
        }
    }

    private suspend fun resetPlaybackSynchronizationAfterRoleChange(reason: String) {
        resetClockSynchronization()
        val canonical = engine?.snapshot()?.playback.takeIf { isCoordinator() }
        localPlaybackSync.resetPlaybackConvergence(
            canonical = canonical,
            preserveLearnedBaseline = false,
        )
        syncDiagnostics.clear()
        container.roomStore.updatePlayback { it.copy(localDriftMs = null) }
        diagnostics.info("sync.reacquire.required", "reason" to reason)
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
        val sessionEngine = engine ?: return
        val localPeerId = identity.peerId
        heartbeatLiveness.reset()
        refreshPowerLocks()
        heartbeatJob?.cancel()
        clockSyncJob?.cancel()
        syncJob?.cancel()
        heartbeatJob = launchSessionJob {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                submitSessionEvent(generation, RoomEvent.HeartbeatTick(generation))
            }
        }
        // Clock estimation has its own synchronized local owner. The room actor receives only
        // low-frequency readiness consequences; timer cadence is never coupled to actor latency.
        clockSyncJob = launchSessionJob {
            while (isActive) {
                val coordinator =
                    sessionEngine.snapshot().term.coordinatorPeerId == localPeerId
                if (!coordinator && sessionJobs.isCurrent(generation)) {
                    reportLocalClockReadiness()
                    val ping = clockSync.createPing()
                    sendToCoordinator(ProtocolBody.ClockPing(ping.pingId, ping.localSendNs))
                }
                delay(
                    if (coordinator || clockSync.synchronized) {
                        CLOCK_SYNC_STEADY_INTERVAL_MS
                    } else {
                        CLOCK_SYNC_WARMUP_INTERVAL_MS
                    }
                )
            }
        }
        // Local drift sampling/correction intentionally bypasses room actor scheduling. Only
        // low-frequency status/reference publication is returned to the actor as a due event.
        syncJob = launchSessionJob {
            while (isActive) {
                val snapshot = sessionEngine.snapshot()
                val snapshotCoordinator = snapshot.term.coordinatorPeerId == localPeerId
                val soloCoordinator =
                    snapshotCoordinator &&
                        !hasOtherActivePlaybackListener(snapshot, localPeerId, clock.nowNs())
                val intervalMs =
                    if (soloCoordinator) {
                        localPlaybackSync.tuning.suspendedRecheckIntervalMs
                    } else {
                        localPlaybackSync.intervalMs(snapshot, player.state.value)
                            ?: localPlaybackSync.tuning.suspendedRecheckIntervalMs
                    }
                delay(intervalMs)
                if (!sessionJobs.isCurrent(generation)) continue
                val current = sessionEngine.snapshot()
                val coordinator = current.term.coordinatorPeerId == localPeerId
                val connected =
                    container.roomStore.structure.value.lifecycle == RoomLifecycleState.CONNECTED
                runPlaybackSynchronizationTick(
                    snapshot = current,
                    coordinator = coordinator,
                    connected = connected,
                    generation = generation,
                    localPeerId = localPeerId,
                )
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
            if (peerPlaybackHealth.expireReadyLeases(clock.nowNs())) {
                reevaluateAllPreparation()
            }
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


    private suspend fun reportLocalClockReadiness() {
        if (isCoordinator() || coordinatorConnection == null) return
        val nowNs = clock.nowNs()
        val synchronized = clockSync.synchronized
        val changed = lastAdvertisedClockReady != synchronized
        val periodicQualityDue =
            synchronized &&
                playbackSession.shouldReportClockQuality(
                    nowLocalNs = nowNs,
                    newlySynchronized = changed && synchronized,
                )
        if (!changed && !periodicQualityDue) return
        sendToCoordinator(
            ProtocolBody.ClockReady(
                synchronized = synchronized,
                roundTripNs =
                    clockSync.roundTripNs.takeIf { synchronized && it != Long.MAX_VALUE },
                uncertaintyNs = clockSync.uncertaintyNs.takeIf { synchronized },
            )
        )
        lastAdvertisedClockReady = synchronized
    }

    override suspend fun admitControl(
        hello: HandshakeMessage.ControlHello,
        remoteAddress: String,
    ): PeerServer.ControlAdmission = admission.admit(hello, remoteAddress)

    override suspend fun onControlConnected(
        connection: ControlConnection,
        admittedRoomId: String,
        admittedSessionGeneration: Long,
    ) {
        val completion = CompletableDeferred<Unit>()
        roomEvents.submit(
            RoomEvent.ControlConnected(
                connection = connection,
                admittedSession =
                    RoomSessionProvenance(
                        roomId = admittedRoomId,
                        generation = admittedSessionGeneration,
                    ),
                completion = completion,
            )
        )
        completion.await()
    }

    private suspend fun processControlConnected(
        connection: ControlConnection,
        admittedSession: RoomSessionProvenance,
    ) {
        val current = engine?.snapshot()
        if (
            !RoomIngressAuthority.acceptsSession(
                provenance = admittedSession,
                currentRoomId = current?.roomId,
                currentGeneration = sessionJobs.generation,
                coordinatorIsAuthoritative = isCoordinator(),
            )
        ) {
            diagnostics.warn(
                "network.control.stale_admission",
                null,
                "peer.id" to connection.peerId.value.take(12),
                "session.admitted_generation" to admittedSession.generation,
                "session.current_generation" to sessionJobs.generation,
            )
            connection.closeSilently()
            return
        }
        peerDisconnectGraceJobs.remove(connection.peerId)?.cancel()
        connections.put(connection.peerId, connection)?.close()
        lastSeenElapsedMs[connection.peerId] = SystemClock.elapsedRealtime()
        publishMemberRuntime()
        if (!isCoordinator()) return
        peerDirectory[connection.peerId] = connection.endpoint
        val member = MemberSnapshot(connection.peerId, connection.endpoint.displayName)
        val snapshot = engine?.snapshot() ?: return
        val existing = snapshot.members.firstOrNull { it.peerId == connection.peerId }
        val canonicalMutation: Pair<Long, ProtocolBody>? =
            when {
                existing == null -> snapshot.sequence + 1 to ProtocolBody.PeerJoined(member)
                existing.displayName != member.displayName ->
                    snapshot.sequence + 1 to ProtocolBody.PeerUpdated(member)
                else -> null
            }
        val updated =
            if (canonicalMutation != null) {
                val (sequence, body) = canonicalMutation
                engine?.applyValidated(sequence, body, ::snapshotFitsProtocol)
                    ?: run {
                        peerDirectory.remove(connection.peerId)
                        connection.close(IllegalStateException("Room state capacity exceeded"))
                        return
                    }
            } else {
                snapshot
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
        canonicalMutation?.let { (sequence, body) ->
            broadcastCanonical(sequence, body, except = connection.peerId)
        }
        reevaluateAllPreparation()
        send(
            connection.peerId,
            ProtocolBody.PlaybackReadinessChanged(
                queueRevision = updated.queueRevision,
                preparedQueueItemIds = preparedQueueItemIds,
            ),
        )
        explicitPreparationQueueItemIds.forEach { queueItemId ->
            send(connection.peerId, ProtocolBody.QueueItemPreparationRequested(queueItemId))
        }
        broadcast(ProtocolBody.PeerDirectory(peerDirectory.values.toList()))
        TrackPrefetchPolicy.desiredItems(updated).forEach { track ->
            send(connection.peerId, ProtocolBody.TrackDescriptorMessage(track.track))
        }
    }

    override suspend fun onFileConnection(socket: Socket, hello: HandshakeMessage.FileClientHello) {
        transferManager?.handleIncomingFileSocket(socket, hello) ?: socket.close()
    }

    private suspend fun processControlClosed(connection: ControlConnection, cause: Throwable?) {
        val peerId = connection.peerId
        // A reconnect may replace an older socket for the same peer. The older socket's delayed
        // close callback must never remove or mark the new connection disconnected.
        val wasCurrent = connections.remove(peerId, connection)
        if (!wasCurrent && coordinatorConnection !== connection) return
        publishMemberRuntime()
        if (coordinatorPeerId == peerId && !isCoordinator()) {
            if (coordinatorConnection === connection) coordinatorConnection = null
            envelopeReplayProtector.resetPeer(peerId)
            // Stop local audio as soon as the room authority is unreachable. The canonical room
            // may recover, but continuing audible playback while control is absent falsely implies
            // synchronized listening. Keep the local timeline so successful reconnect can resume at
            // the newest canonical position.
            pauseLocalPlaybackForConnectivityLoss()
            // Stop all guest correction and scheduled execution immediately. The previous affine
            // clock mapping and playback-reference stream are no longer trustworthy after a
            // coordinator socket closes; both will reacquire only after reconnect to that host.
            resetClockSynchronization()
            localPlaybackSync.resetRuntime(preserveLearnedBaseline = true)
            playbackSession.clearCanonical()
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
            playbackSession.forgetPeer(peerId)
            peerPlaybackHealth.remove(peerId)
            transferCoordinator.removePeer(peerId)
            cancelTransferRetriesForPeer(peerId)
            discardPendingTransferAssignmentsForPeer(peerId)
            waitingForSource.values.forEach { it.remove(peerId) }
            envelopeReplayProtector.resetPeer(peerId)
            publishMemberRuntime()
            engine?.snapshot()?.members
                ?.asSequence()
                ?.map { it.peerId }
                ?.filter { it != peerId && isPeerConnected(it) }
                ?.forEach { assignNextForDestination(it) }
            reevaluateAllPreparation()
            if (engine?.snapshot()?.members?.any { it.peerId == peerId } == true) {
                schedulePeerDisconnectGrace(peerId)
            }
        }
        diagnostics.info(
            "network.peer.disconnected", "peer.id" to peerId.value.take(12),
            "disconnect.cause" to cause?.javaClass?.simpleName,
        )
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
                    sessionJobs.runIfCurrent(generation) {
                        container.roomStore.update {
                            it.copy(
                                lifecycle = RoomLifecycleState.RECONNECTING,
                                status = UserFacingStatus.RECONNECTING,
                                statusMessage = "Waiting for Wi-Fi…",
                            )
                        }
                    }
                }
                delay(RoomReconnectPolicy.NETWORK_POLL_MS)
            }
            if (!sessionJobs.isCurrent(generation)) return

            for (attempt in 1..RoomReconnectPolicy.MAX_ATTEMPTS) {
                delay(RoomReconnectPolicy.delayBeforeAttemptMs(attempt))
                if (!sessionJobs.isCurrent(generation)) return
                if (
                    !sessionJobs.runIfCurrent(generation) {
                        container.roomStore.update {
                            it.copy(
                                lifecycle = RoomLifecycleState.RECONNECTING,
                                status = UserFacingStatus.RECONNECTING,
                                statusMessage =
                                    "Reconnecting ($attempt/${RoomReconnectPolicy.MAX_ATTEMPTS})…",
                            )
                        }
                    }
                ) return
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
                    diagnostics.warn(
                        "network.reconnect.failed", error, "reconnect.attempt" to attempt,
                        "operation.duration_ms" to SystemClock.elapsedRealtime() - attemptStartedAtMs,
                    )
                    continue
                }
                diagnostics.info(
                    "network.reconnect.restored", "reconnect.attempt" to attempt,
                    "operation.duration_ms" to SystemClock.elapsedRealtime() - attemptStartedAtMs,
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
            setFailure("The room host changed while reconnecting")
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


    private fun launchSessionJob(block: suspend CoroutineScope.() -> Unit): Job =
        sessionJobs.launch {
            block()
        }

    private suspend fun submitSessionEvent(generation: Long, event: RoomEvent) {
        if (sessionJobs.isCurrent(generation)) roomEvents.submit(event)
    }

    private suspend fun leaveRoom(reason: String = "The room host ended the room") {
        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.ENDING,
                status = UserFacingStatus.PREPARING,
                statusMessage = "Ending room…",
                errorMessage = null,
                issue = null,
            )
        }
        if (isCoordinator()) {
            // Protocol 2 already has LeaveRoom. From the coordinator it is a terminal room signal,
            // so healthy participants do not waste a reconnect window after an intentional exit.
            suspendResult { broadcast(ProtocolBody.LeaveRoom(reason)) }
        } else {
            suspendResult { sendToCoordinator(ProtocolBody.LeaveRoom(reason)) }
        }
        hotspot.stop()
        playerExecutor.invalidateTransport()
        playerExecutor.maintenance {
            pause(PlaybackPauseCause.SESSION_END)
            setRepeatCurrentItem(false)
            setQueue(emptyList(), null, 0)
        }
        resetSession(keepDiscovery = false)
        container.roomStore.reset(preserveHotspot = false)
    }

    private suspend fun resetSession(keepDiscovery: Boolean) {
        val closingDiagnosticSessionId = diagnostics.currentSessionId()
        // No command may disappear merely because the session is ending. Publish terminal phases
        // before sockets and actor-owned preparation jobs are torn down.
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
        localPlaybackParticipation.resetForSessionBoundary()
        diagnostics.end(
            closingDiagnosticSessionId, SystemClock.elapsedRealtime() - startedAtMs,
            closingConnections.size, transferCountBeforeShutdown, sessionJobs.activeJobCount, playbackMetrics,
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
        clearTransferRetries()
    }

    private fun resetSessionNow(keepDiscovery: Boolean) {
        val closingDiagnosticSessionId = diagnostics.currentSessionId()
        transportWatchdogJobs.values.forEach(Job::cancel)
        transportWatchdogJobs.clear()
        clearTransferRetries()
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
        diagnostics.endNow(closingDiagnosticSessionId)
        playbackDispatcher.resetMetrics()
    }

    private fun resetSessionState(keepDiscovery: Boolean) {
        transportWatchdogJobs.values.forEach(Job::cancel)
        transportWatchdogJobs.clear()
        clearTransferRetries()
        playerExecutor.cancel()
        heartbeatJob = null
        clockSyncJob = null
        syncJob = null
        queueRefreshJob = null
        timelineRefreshJob = null
        playerMaintenanceRetryJob = null
        recoveryJob = null
        localNetworkRecoveryJob?.cancel()
        localNetworkRecoveryJob = null
        peerDisconnectGraceJobs.values.forEach(Job::cancel)
        peerDisconnectGraceJobs.clear()
        joinTimeoutJob = null
        joinAttemptJob = null
        pendingJoin = null
        heartbeatLiveness.reset()
        transferManager?.cancelAll()
        transferManager = null
        localNetworkRouter.resetSession()
        peers.clearSession(ControlConnection::closeSilently)
        transferCoordinator.clear()
        peerPlaybackHealth.clear()
        playbackSession.resetSession()
        queuePreparationFence.invalidate()
        coordinatorConnection = null
        coordinatorPeerId = null
        roomQueueLeases.values.forEach(ManagedFileLease::close)
        roomQueueLeases.clear()
        preparedQueueItemIds = emptySet()
        explicitPreparationQueueItemIds = emptySet()
        playbackReadinessQueueRevision = -1L
        desiredTransferDemands = emptyMap()
        localTrackAvailability.clear()
        admission.reset()
        recentCommandIds.clear()
        envelopeReplayProtector.reset()
        playerEventInterpreter.reset(player.state.value)
        localPlaybackSync.resetRuntime(preserveLearnedBaseline = false)
        syncDiagnostics.clear()
        pendingSuccessor = null
        terminalNaturalPause = null
        localTransportCommandIds.clear()
        completedLocalTransportCommandIds.clear()
        transportCommands.clear()
        transportIntents.invalidateAll()
        transportStatusClearJob?.cancel()
        transportStatusClearJob = null
        playerExecutor.invalidateTransport()
        container.roomStore.updateStructure {
            it.copy(transportStatus = null, pendingSuccessorQueueItemId = null)
        }
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

    private fun isPeerConnected(peerId: PeerId): Boolean =
        peerId == identity.peerId || connections.containsKey(peerId)

    private fun connectedPeerIds(snapshot: RoomSnapshot): Set<PeerId> {
        val members = snapshot.members.mapTo(hashSetOf()) { it.peerId }
        return buildSet {
            if (identity.peerId in members) add(identity.peerId)
            connections.keys.filterTo(this) { it in members }
        }
    }

    private fun publishMemberRuntime(snapshot: RoomSnapshot? = container.roomStore.structure.value.snapshot) {
        val room = snapshot ?: return
        val runtime =
            room.members.associate { member ->
                member.peerId to
                    com.darius.unison.model.MemberRuntimeState(
                        connected = isPeerConnected(member.peerId),
                    )
            }
        container.roomStore.updateStructure { it.copy(memberRuntime = runtime) }
    }

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
            is ProtocolBody.TrackNeed ->
                onTrackNeed(
                    identity.peerId,
                    body.trackId,
                    body.priority,
                    body.neededByCoordinatorNs,
                )
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
            protocolVersion = PROTOCOL_VERSION,
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

    private suspend fun updatePeerEndpoint(
        sourceConnection: ControlConnection,
        announced: PeerEndpoint,
    ) {
        val peerId = sourceConnection.peerId
        if (!RoomIngressAuthority.isCurrentConnection(connections[peerId], sourceConnection)) return
        val update =
            PeerEndpointAuthority.normalizeAnnouncement(
                peerId = peerId,
                authenticatedHostAddress = sourceConnection.authenticatedRemoteHostAddress,
                announced = announced,
                lastSeenElapsedMs = SystemClock.elapsedRealtime(),
            ) ?: return
        if (!update.announcedHostMatchesAuthenticatedHost) {
            diagnostics.warn(
                "network.endpoint.host_mismatch",
                null,
                "peer.id" to peerId.value.take(12),
            )
        }
        val normalized = update.endpoint
        peerDirectory[peerId] = normalized
        val member = engine?.snapshot()?.members?.firstOrNull { it.peerId == peerId } ?: return
        if (member.displayName != normalized.displayName) {
            emitCanonical(ProtocolBody.PeerUpdated(member.copy(displayName = normalized.displayName)))
        }
        publishMemberRuntime()
        broadcast(ProtocolBody.PeerDirectory(peerDirectory.values.toList()))
    }

    private suspend fun refreshLocalCoordinatorEndpoint() {
        if (!isCoordinator()) return
        val snapshot = engine?.snapshot() ?: return
        val endpoint = localEndpointOrNull() ?: return
        peerDirectory[identity.peerId] = endpoint
        val member = snapshot.members.firstOrNull { it.peerId == identity.peerId }
        if (member != null && member.displayName != identity.displayName) {
            emitCanonical(ProtocolBody.PeerUpdated(member.copy(displayName = identity.displayName)))
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

    private fun localEndpointOrNull(): PeerEndpoint? {
        val hostAddress = selectedLocalAddress() ?: return null
        return PeerEndpoint(
            peerId = identity.peerId,
            displayName = identity.displayName,
            hostAddress = hostAddress,
            port = server.port,
            appVersion = BuildConfig.VERSION_NAME,
            lastSeenElapsedMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun selectedLocalAddress(): String? =
        localNetworkRouter.preferredLocalAddress(preferHotspot = hotspot.state.value != null)
            ?.hostAddress

    private fun updateSnapshot(
        snapshot: RoomSnapshot,
        lifecycle: RoomLifecycleState = RoomLifecycleState.CONNECTED,
        message: String? = null,
    ) {
        val previousQueueRevision = container.roomStore.structure.value.snapshot?.queueRevision
        if (previousQueueRevision != null && previousQueueRevision != snapshot.queueRevision) {
            val validIds = snapshot.queue.mapTo(hashSetOf()) { it.queueItemId }
            preparedQueueItemIds =
                if (snapshot.term.coordinatorPeerId == identity.peerId) {
                    preparedQueueItemIds.intersect(validIds)
                } else {
                    emptySet()
                }
            explicitPreparationQueueItemIds = explicitPreparationQueueItemIds.intersect(validIds)
            playbackReadinessQueueRevision = -1L
        }
        if (previousQueueRevision == null || previousQueueRevision != snapshot.queueRevision) {
            refreshRoomQueueLeases(snapshot)
        }
        playbackSession.seedCanonical(snapshot.playback)
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
                memberRuntime =
                    snapshot.members.associate { member ->
                        member.peerId to
                            com.darius.unison.model.MemberRuntimeState(
                                connected = isPeerConnected(member.peerId),
                            )
                    },
            )
        }
        publishMediaReadiness(snapshot)
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
            HandshakeRejectionCode.AUTHENTICATION_FAILED -> "The room code is incorrect"
            HandshakeRejectionCode.RATE_LIMITED -> "Too many attempts. Try again shortly"
            HandshakeRejectionCode.PROTOCOL_MISMATCH -> "This room uses a different Unison version"
            HandshakeRejectionCode.ROOM_FULL -> "This room is full"
            HandshakeRejectionCode.IDENTITY_COLLISION -> "This phone is already in the room"
            HandshakeRejectionCode.COORDINATOR_MOVED ->
                "The room connection changed. Find the room again"
            HandshakeRejectionCode.ROOM_INACTIVE,
            HandshakeRejectionCode.WRONG_ROOM -> "This room is no longer available"
            HandshakeRejectionCode.INVALID_REQUEST,
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
                        recoveryAction = RoomRecoveryAction.NONE,
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
        playbackSession.resetClockQuality()
        lastAdvertisedClockReady = null
    }

    private suspend fun teardownTerminalSession() {
        // A terminal transition must not leave stale canonical state, sockets, transfers, or audio
        // behind the lobby. Avoid cancelling the coroutine currently reporting reconnect failure.
        if (recoveryJob === currentCoroutineContext()[Job]) recoveryJob = null
        localNetworkRecoveryJob?.cancel()
        localNetworkRecoveryJob = null
        hotspot.stop()
        playerExecutor.invalidateTransport()
        suspendResult {
            playerExecutor.maintenance {
                pause(PlaybackPauseCause.FAILURE_TEARDOWN)
                setRepeatCurrentItem(false)
                setQueue(emptyList(), null, 0)
            }
        }
        resetSession(keepDiscovery = false)
        container.roomStore.reset(preserveHotspot = false)
    }

    private suspend fun setRoomEnded(message: String) {
        diagnostics.warn("room.ended", null, "reason" to message)
        teardownTerminalSession()
        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.FAILED,
                status = UserFacingStatus.UNAVAILABLE,
                errorMessage = message,
                issue =
                    RoomIssue(
                        code = RoomIssueCode.ROOM_ENDED,
                        message = message,
                        recoveryAction = RoomRecoveryAction.NONE,
                        deduplicationKey = "room-ended:$message",
                    ),
                statusMessage = null,
            )
        }
    }

    private suspend fun setFailure(message: String) {
        teardownTerminalSession()
        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.FAILED,
                status = UserFacingStatus.UNAVAILABLE,
                errorMessage = message,
                issue =
                    RoomIssue(
                        code = RoomIssueCode.CONNECTION_FAILED,
                        message = message,
                        recoveryAction = RoomRecoveryAction.NONE,
                        deduplicationKey = "terminal:$message",
                    ),
                statusMessage = null,
            )
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // The service is allowed to stop after a failed precondition. Retain that terminal error
        // so the activity can explain what happened instead of silently returning to the lobby.
        val retainedFailure =
            container.roomStore.structure.value.takeIf {
                it.lifecycle == RoomLifecycleState.FAILED && it.issue != null
            }
        addressMonitorJob?.cancel()
        addressMonitorJob = null
        hotspotStateJob?.cancel()
        hotspotStateJob = null
        playerStateJob?.cancel()
        playerStateJob = null
        resetSessionNow(keepDiscovery = false)
        container.roomStore.reset(preserveHotspot = false)
        retainedFailure?.let { failure ->
            container.roomStore.updateStructure { state ->
                state.copy(
                    lifecycle = failure.lifecycle,
                    status = failure.status,
                    statusMessage = failure.statusMessage,
                    errorMessage = failure.errorMessage,
                    issue = failure.issue,
                )
            }
        }
        transportIntents.close()
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
        private const val SLOW_ROOM_EVENT_NS = 16_000_000L
        private const val SESSION_SHUTDOWN_TIMEOUT_MS = 2_500L
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val CLOCK_SYNC_WARMUP_INTERVAL_MS = 250L
        private const val CLOCK_SYNC_STEADY_INTERVAL_MS = 1_000L
        private const val CLOCK_QUALITY_REPORT_INTERVAL_NS = 15_000_000_000L
        private const val CLOCK_READY_LEASE_NS =
            CLOCK_QUALITY_REPORT_INTERVAL_NS * 2 + HEARTBEAT_INTERVAL_MS * 1_000_000L

        private const val PLAYBACK_SPEED_EPSILON = 0.00005f
        private const val TRANSPORT_RESULT_VISIBLE_MS = 900L
        private const val TRANSPORT_REJECTION_VISIBLE_MS = 3_000L
        private const val TRANSPORT_ACCEPTED_WATCHDOG_MS = 2_000L
        private const val TRANSPORT_EXECUTION_WATCHDOG_MS = 12_000L
        private const val TRANSPORT_RECONCILIATION_GRACE_MS = 2_000L
        private const val TRANSPORT_PAUSED_POSITION_TOLERANCE_MS = 250L
        private const val TRANSPORT_PLAYING_POSITION_TOLERANCE_MS = 750L
        private const val PLAYBACK_STATUS_REPORT_INTERVAL_NS = 1_000_000_000L
        private const val MANUAL_DISCOVERY_WINDOW_MS = 8_000L
        private const val TRANSFER_TOKEN_LIFETIME_MS = 60_000L
        private const val BASE_TRANSFER_RETRY_DELAY_MS = 500L
        private const val SHUFFLE_RUNWAY_PRESERVE_THRESHOLD_MS = 45_000L
        private const val MAX_TRANSFER_DEADLINE_NS = 6L * 60L * 60L * 1_000_000_000L
        private const val SOURCE_AUTHORIZATION_TIMEOUT_MS = 2_500L
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
        private const val PEER_TIMEOUT_MS = 30_000L
        private const val INITIAL_JOIN_TIMEOUT_MS = 12_000L
        private const val MAX_REPORTED_CLOCK_RTT_NS = 2_000_000_000L
        private const val MAX_REPORTED_CLOCK_UNCERTAINTY_NS = 1_000_000_000L
    }
}
