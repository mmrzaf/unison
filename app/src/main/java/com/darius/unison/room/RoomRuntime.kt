package com.darius.unison.room

import android.content.Context
import android.os.SystemClock
import com.darius.unison.BuildConfig
import com.darius.unison.app.AppContainer
import com.darius.unison.model.AppCommand
import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.LocalIdentity
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
import com.darius.unison.network.LocalHotspotController
import com.darius.unison.network.NetworkAddressPolicy
import com.darius.unison.network.NsdDiscoveryEvent
import com.darius.unison.network.NsdRoomDiscovery
import com.darius.unison.network.PeerServer
import com.darius.unison.network.WifiLocks
import com.darius.unison.playback.LocalPlayableItem
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.ScheduledPlaybackController
import com.darius.unison.protocol.Crypto
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.protocol.ProtocolJson
import com.darius.unison.storage.RoomSnapshotEntity
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.sync.PlaybackSyncEngine
import com.darius.unison.transfer.TransferManager
import com.darius.unison.util.AndroidMonotonicClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.Socket
import java.util.Base64
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
    private val playbackSync = PlaybackSyncEngine()
    private val wifiLocks = WifiLocks(appContext)
    private val discovery = NsdRoomDiscovery(appContext, wifiLocks, log)
    private val hotspot = LocalHotspotController(appContext, log)
    private val controlClient = ControlClient(scope, log)
    private val server = PeerServer(scope, log, this)
    private val scheduler =
        ScheduledPlaybackController(player, clock, clockSync, scope, log) { message -> setError(message) }

    private lateinit var identity: LocalIdentity
    private var engine: RoomEngine? = null
    private var roomSecret: ByteArray? = null
    private var roomPin: String? = null
    private var coordinatorPeerId: PeerId? = null
    private var coordinatorConnection: ControlConnection? = null
    private val connections = ConcurrentHashMap<PeerId, ControlConnection>()
    private val peerDirectory = ConcurrentHashMap<PeerId, PeerEndpoint>()
    private val availability = ConcurrentHashMap<TrackId, MutableSet<PeerId>>()
    private val waitingForSource = ConcurrentHashMap<TrackId, MutableSet<PeerId>>()
    private val recentCommandIds = LinkedHashSet<String>()
    private val lastSeenElapsedMs = ConcurrentHashMap<PeerId, Long>()
    private val announcedTrackIds = ConcurrentHashMap.newKeySet<TrackId>()
    private val clockReadyPeers = ConcurrentHashMap.newKeySet<PeerId>()
    private val transferFailureCounts = ConcurrentHashMap<String, Int>()
    private val pendingTransferAssignments = ConcurrentHashMap<String, ProtocolBody.TrackSourceAssigned>()
    private val pinAttempts = ConcurrentHashMap<String, PinAttemptState>()
    private val initializationMutex = Mutex()
    private val canonicalMutationMutex = Mutex()
    private val canonicalSideEffects = Channel<Pair<ProtocolBody, RoomSnapshot>>(capacity = 128)

    private var transferManager: TransferManager? = null
    private var discoveryJob: Job? = null
    private var heartbeatJob: Job? = null
    private var clockSyncJob: Job? = null
    private var syncJob: Job? = null
    private var persistenceJob: Job? = null
    private var addressMonitorJob: Job? = null
    private var speedResetJob: Job? = null
    private var queueRefreshJob: Job? = null
    private var timelineRefreshJob: Job? = null
    private var recoveryJob: Job? = null
    private var lastObservedPlayerItem: QueueItemId? = null
    private var lastHandledEndedItem: QueueItemId? = null
    private var lastObservedRepeatTransitionRevision = 0L
    private var pendingAutoResumeQueueItemId: QueueItemId? = null
    private var pendingPlayRequestedBy: PeerId? = null
    private var closed = false

    private data class PinAttemptState(val failures: Int, val blockedUntilElapsedMs: Long)

    init {
        scope.launch {
            for ((body, snapshot) in canonicalSideEffects) {
                try {
                    applyCanonicalSideEffects(body, snapshot)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
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
                    container.roomStore.update { it.copy(roomAddress = address) }
                    if (address != null && ::identity.isInitialized && engine != null) {
                        if (isCoordinator()) refreshLocalCoordinatorEndpoint()
                        else sendToCoordinator(ProtocolBody.EndpointAnnouncement(localEndpoint()))
                    }
                }
                delay(LOCAL_ADDRESS_POLL_INTERVAL_MS)
            }
        }
        scope.launch {
            hotspot.state.collectLatest { value ->
                // LocalOnlyHotspot reports success before the network interface is always visible.
                // Give Android a brief moment to publish the address, then update and re-advertise
                // an active room without making the user recreate it.
                if (value != null) delay(HOTSPOT_INTERFACE_SETTLE_MS)
                val address = selectedLocalAddress()
                container.roomStore.update { it.copy(hotspot = value, roomAddress = address) }
                if (value != null && ::identity.isInitialized && isCoordinator()) {
                    refreshLocalCoordinatorEndpoint()
                }
            }
        }
        scope.launch {
            player.state.collect { value ->
                container.roomStore.update {
                    it.copy(
                        localPlaybackPositionMs = value.positionMs,
                        localPlaybackQueueItemId = value.queueItemId,
                        localIsPlaying = value.isPlaying,
                        localSeekRevision = value.seekRevision,
                        errorMessage = value.error ?: it.errorMessage,
                    )
                }
                val previous = lastObservedPlayerItem
                lastObservedPlayerItem = value.queueItemId
                val repeatedCurrentItem =
                    value.repeatTransitionRevision > lastObservedRepeatTransitionRevision
                lastObservedRepeatTransitionRevision = value.repeatTransitionRevision
                if (previous != value.queueItemId || !value.ended) lastHandledEndedItem = null
                if (repeatedCurrentItem && value.queueItemId != null && isCoordinator()) {
                    recordNaturalRepeatTransition(value.queueItemId, value.positionMs)
                } else if (previous != null && value.queueItemId != null &&
                    previous != value.queueItemId && value.isPlaying && isCoordinator()
                ) {
                    pendingAutoResumeQueueItemId = null
                    recordNaturalTrackTransition(value.queueItemId, value.positionMs)
                } else if (value.ended && value.queueItemId != null && lastHandledEndedItem != value.queueItemId && isCoordinator()) {
                    lastHandledEndedItem = value.queueItemId
                    recordNaturalPlaybackEnded(value.queueItemId, value.positionMs, value.durationMs)
                }
            }
        }
    }

    suspend fun handle(command: AppCommand) {
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
                    container.roomStore.update { state -> state.copy(transfers = state.transfers + (progress.trackId to progress)) }
                },
                onCompleted = { descriptor -> onLocalTrackReady(descriptor) },
                onFailed = { trackId, sourcePeerId, reason ->
                    sendToCoordinator(ProtocolBody.TrackFailed(trackId, reason, sourcePeerId))
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
            roomPin = pin,
            members = listOf(MemberSnapshot(identity.peerId, identity.displayName, endpoint)),
        )
        engine = RoomEngine(initial)
        discovery.advertise(id, name, server.port, 1, onError = ::setError)
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
        try {
            val connected = controlClient.connect(
                identity = identity,
                roomId = room.roomId,
                host = room.hostAddress,
                port = room.port,
                listeningPort = server.port,
                pin = pin,
                appVersion = BuildConfig.VERSION_NAME,
                onEnvelope = ::onEnvelope,
                onClosed = ::onControlClosed,
            )
            roomSecret = connected.roomSecret
            roomPin = pin
            coordinatorPeerId = connected.coordinatorPeerId
            coordinatorConnection = connected.connection
            connections[connected.coordinatorPeerId] = connected.connection
            connected.connection.start()
            container.roomStore.update {
                it.copy(lifecycle = RoomLifecycleState.JOINING, statusMessage = "Joining room…")
            }
            startSessionJobs()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            log.w(TAG, "Could not join room", error)
            setFailure(userFacingJoinFailure(error))
        }
    }

    private fun startDiscovery() {
        discoveryJob?.cancel()
        container.roomStore.update {
            it.copy(
                lifecycle = if (it.snapshot == null) RoomLifecycleState.DISCOVERING else it.lifecycle,
                discoveredRooms = emptyList(),
                errorMessage = null
            )
        }
        discoveryJob = scope.launch {
            discovery.discover()
                .catch { setError("Could not find nearby rooms") }
                .collect { event ->
                    container.roomStore.update { state ->
                        when (event) {
                            is NsdDiscoveryEvent.Found -> state.copy(
                                discoveredRooms = (state.discoveredRooms
                                    .filterNot { it.roomId == event.room.roomId || it.serviceName == event.room.serviceName } + event.room)
                                    .sortedBy { it.roomName }
                            )

                            is NsdDiscoveryEvent.Lost -> state.copy(
                                discoveredRooms = state.discoveredRooms.filterNot { it.serviceName == event.serviceName }
                            )
                        }
                    }
                }
        }
    }

    private fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        discovery.stopDiscovery()
        container.roomStore.update { state ->
            state.copy(lifecycle = if (state.snapshot == null) RoomLifecycleState.IDLE else state.lifecycle)
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
                runCatching { container.trackRepository.requireReadableFile(descriptor.trackId) != null }
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
            runCatching { container.trackRepository.requireReadableFile(item.track.trackId) != null }
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
        var prepareAfterDecision: RoomSnapshot? = null
        canonicalMutationMutex.withLock {
            if (!recentCommandIds.add(command.commandId)) return@withLock
            while (recentCommandIds.size > 256) recentCommandIds.remove(recentCommandIds.first())
            val roomEngine = engine ?: return@withLock
            val current = roomEngine.snapshot()
            val currentItem = PlaybackRequestPolicy.currentItem(current)
            if (command is UserCommand.Play && currentItem != null && PlaybackRequestPolicy.shouldDeferPlay(current)) {
                pendingPlayRequestedBy = command.requestedBy
                prepareAfterDecision = current
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
                return@withLock
            }
            when (val decision = roomEngine.decide(command, clock.nowNs())) {
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
                        canonicalSideEffects.send(mutation.body to mutation.snapshot)
                    }
                }
            }
        }
        prepareAfterDecision?.let { snapshot ->
            prepareWindow(snapshot)
            val currentTrackId = snapshot.playback.queueItemId
                ?.let { id -> snapshot.queue.firstOrNull { it.queueItemId == id } }
                ?.track?.trackId
                ?: snapshot.queue.firstOrNull()?.track?.trackId
            if (currentTrackId != null) reevaluatePreparation(currentTrackId)
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

    private suspend fun onEnvelope(peerId: PeerId, envelope: Envelope) {
        lastSeenElapsedMs[peerId] = SystemClock.elapsedRealtime()
        val current = engine?.snapshot()
        if (current != null && envelope.roomId != current.roomId) return
        // Frame authentication proves possession of this socket's session key, not the JSON
        // sender field. Bind every envelope to the peer that owns the connection so a guest
        // cannot impersonate the coordinator or another room member inside an authenticated frame.
        if (envelope.senderPeerId != peerId) {
            log.w(
                TAG,
                "Rejected sender mismatch socket=${peerId.value.take(8)} envelope=${envelope.senderPeerId.value.take(8)}"
            )
            return
        }
        when (val body = envelope.body) {
            is ProtocolBody.JoinAccepted -> {
                if (isCoordinator() || peerId != coordinatorPeerId) return
                if (body.snapshot.roomId != envelope.roomId || body.snapshot.term.coordinatorPeerId != peerId) return
                clockSync.reset()
                clockReadyPeers.clear()
                engine = RoomEngine(body.snapshot)
                coordinatorPeerId = body.snapshot.term.coordinatorPeerId
                body.peerDirectory.forEach { peerDirectory[it.peerId] = it }
                lastSeenElapsedMs[body.snapshot.term.coordinatorPeerId] = SystemClock.elapsedRealtime()
                announcedTrackIds.clear()
                recoveryJob?.cancel(); recoveryJob = null
                updateSnapshot(body.snapshot, RoomLifecycleState.CONNECTED, "Connected")
                reconcileSnapshotQueue(body.snapshot)
                prepareWindow(body.snapshot)
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
                val currentCoordinator = engine?.snapshot()?.term?.coordinatorPeerId
                if (isCoordinator() || (currentCoordinator != null && peerId != currentCoordinator)) return
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
        val updated = engine?.apply(sequence, body) ?: return
        updateSnapshot(updated)
        canonicalSideEffects.send(body to updated)
    }

    private suspend fun announceLocalAvailability(track: TrackDescriptor) {
        if (!announcedTrackIds.add(track.trackId)) return
        val hasFile = runCatching { container.trackRepository.requireReadableFile(track.trackId) != null }
            .onFailure { error -> log.w(TAG, "Could not read ${track.trackId.value.take(8)}", error) }
            .getOrDefault(false)
        if (isCoordinator()) {
            if (hasFile) onTrackHave(identity.peerId, track.trackId) else onTrackNeed(identity.peerId, track.trackId)
        } else {
            sendToCoordinator(if (hasFile) ProtocolBody.TrackHave(track.trackId) else ProtocolBody.TrackNeed(track.trackId))
        }
    }

    private suspend fun onTrackHave(peerId: PeerId, trackId: TrackId) {
        availability.computeIfAbsent(trackId) { ConcurrentHashMap.newKeySet() }.add(peerId)
        transferFailureCounts.keys.removeAll { it.startsWith("${trackId.value}:$peerId:") || it.endsWith(":$peerId") }
        waitingForSource[trackId]?.remove(peerId)
        assignWaiting(trackId)
        reevaluatePreparation(trackId)
    }

    private suspend fun onTrackNeed(peerId: PeerId, trackId: TrackId) {
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

    private fun canApplyScheduledCommand(): Boolean = isCoordinator() || clockSync.synchronized

    private suspend fun onLocalTrackReady(descriptor: TrackDescriptor) {
        announcedTrackIds.add(descriptor.trackId)
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
                val file = runCatching { container.trackRepository.requireReadableFile(item.track.trackId) }
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
        val artworkFile = allowedByRoom
            .firstOrNull { it.queueItemId == artworkQueueItemId }
            ?.let { item ->
                readable[item.track.trackId]?.let { audioFile ->
                    runCatching { container.artworkStore.fileFor(item.track.trackId, audioFile) }
                        .onFailure { error -> log.w(TAG, "Could not load artwork", error) }
                        .getOrNull()
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
    }

    private suspend fun markTrackPlayed(snapshot: RoomSnapshot, queueItemId: QueueItemId) {
        snapshot.queue.firstOrNull { it.queueItemId == queueItemId }?.track?.trackId?.let { trackId ->
            runCatching { container.trackRepository.markPlayed(trackId) }
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
            val targetLocalNs = clockSync.toLocalTime(executeAtCoordinatorNs)
            val remainingNs = targetLocalNs - clock.nowNs()
            if (remainingNs > 0) delay((remainingNs + 999_999L) / 1_000_000L)
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
        if (!isCoordinator()) return
        canonicalMutationMutex.withLock {
            val snapshot = engine?.snapshot() ?: return@withLock
            val sequence = snapshot.sequence + 1
            val updated = engine?.apply(sequence, body) ?: return@withLock
            updateSnapshot(updated)
            broadcastCanonical(sequence, body)
            canonicalSideEffects.send(body to updated)
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
        if (isCoordinator() || !clockSync.synchronized) return
        val queueItem = canonical.queueItemId ?: return
        val coordinatorNow = clockSync.coordinatorNowNs()
        val scheduledForFuture = canonical.coordinatorTimestampNs > coordinatorNow + FUTURE_COMMAND_TOLERANCE_NS
        if (scheduledForFuture) {
            val local = player.state.value
            // When a future skip/removal is pending, the current item may no longer exist in the
            // canonical queue. Do not rebuild the timeline while it is still audible. The target
            // was already preloaded in the previous timeline; a post-execution refresh removes the
            // obsolete item. A fresh/rejoining player has no current item and must be prepared now.
            if (local.queueItemId == null) refreshPlayerQueue(engine?.snapshot() ?: return)
            // Periodic state is the recovery path for a lost canonical command. Preserve the
            // future execution timestamp instead of reconciling transport state immediately.
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
        val expected = canonical.projectedPositionMs(coordinatorNow)
        val localState = player.state.value
        val actual = localState.positionMs
        val drift = expected - actual
        container.roomStore.update { it.copy(localDriftMs = drift) }
        when (val correction = playbackSync.correction(expected, actual)) {
            PlaybackSyncEngine.Correction.None -> if (abs(player.state.value.playbackSpeed - 1f) > 0.001f) player.setPlaybackSpeed(
                1f
            )

            is PlaybackSyncEngine.Correction.Seek -> player.seekTo(correction.positionMs)
            is PlaybackSyncEngine.Correction.AdjustSpeed -> {
                player.setPlaybackSpeed(correction.speed)
                speedResetJob?.cancel()
                speedResetJob = scope.launch {
                    delay(correction.durationMs)
                    player.setPlaybackSpeed(1f)
                }
            }
        }
        // Periodic state is also the recovery path when a scheduled command was lost during a
        // transient reconnect. Reconcile transport state only after position correction.
        if (canonical.isPlaying && !player.state.value.isPlaying) player.play()
        else if (!canonical.isPlaying && player.state.value.isPlaying) player.pause()
        sendToCoordinator(
            ProtocolBody.PlaybackStatusReport(
                queueItem,
                player.state.value.positionMs,
                player.state.value.isPlaying,
                drift
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
        container.roomStore.update { state ->
            val snapshot = state.snapshot ?: return@update state
            state.copy(snapshot = snapshot.copy(members = snapshot.members.map { member ->
                if (member.peerId == status.peerId) {
                    member.copy(playbackPositionMs = status.positionMs, driftMs = status.driftMs)
                } else member
            }))
        }
    }

    private fun startSessionJobs() {
        wifiLocks.acquireWifi()
        heartbeatJob?.cancel()
        clockSyncJob?.cancel()
        syncJob?.cancel()
        persistenceJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val snapshot = engine?.snapshot() ?: continue
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
        }
        // Converge the guest clock before the first user command, then keep it fresh. A five-second
        // ping cadence would make a newly joined room visibly slow to become synchronization-ready.
        clockSyncJob = scope.launch {
            while (isActive) {
                if (!isCoordinator() && coordinatorConnection != null) {
                    val ping = clockSync.createPing()
                    sendToCoordinator(ProtocolBody.ClockPing(ping.pingId, ping.localSendNs))
                }
                delay(if (clockSync.synchronized) CLOCK_SYNC_STEADY_INTERVAL_MS else CLOCK_SYNC_WARMUP_INTERVAL_MS)
            }
        }
        syncJob = scope.launch {
            while (isActive) {
                delay(PLAYBACK_SYNC_INTERVAL_MS)
                if (isCoordinator()) {
                    val snapshot = engine?.snapshot() ?: continue
                    val now = clock.nowNs()
                    broadcast(ProtocolBody.PlaybackStateSync(snapshot.playback.forStateSync(now)))
                }
            }
        }
        persistenceJob = scope.launch {
            while (isActive) {
                delay(SNAPSHOT_PERSIST_INTERVAL_MS)
                persistSnapshot()
            }
        }
    }

    private suspend fun persistSnapshot() {
        val snapshot = engine?.snapshot() ?: return
        try {
            val currentIndex = snapshot.queue.indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
                .let { if (it < 0) 0 else it }
            snapshot.queue.drop(currentIndex).take(snapshot.options.preloadCount + 1).forEach { item ->
                container.trackRepository.touchTemporary(item.track.trackId)
            }
            container.database.roomSnapshotDao().upsert(
                RoomSnapshotEntity(
                    snapshot.roomId,
                    ProtocolJson.encodeToString(snapshot.copy(roomPin = null)),
                    System.currentTimeMillis(),
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            log.w(TAG, "Snapshot persistence failed", error)
        }
    }

    override suspend fun admitControl(
        hello: HandshakeMessage.ClientHello,
        remoteAddress: String,
    ): PeerServer.ControlAdmission {
        val snapshot = engine?.snapshot() ?: return PeerServer.ControlAdmission.Rejected("Room is not active")
        if (!isCoordinator()) return PeerServer.ControlAdmission.Rejected("Coordinator moved")
        if (hello.roomId != snapshot.roomId) return PeerServer.ControlAdmission.Rejected("Wrong room")
        if (PROTOCOL_VERSION !in hello.protocolVersions) return PeerServer.ControlAdmission.Rejected("App versions are incompatible")
        if (hello.peerId == identity.peerId) return PeerServer.ControlAdmission.Rejected("Cannot join yourself")
        if (hello.peerId.value.length !in 16..128 || !HELLO_TOKEN_PATTERN.matches(hello.peerId.value)) {
            return PeerServer.ControlAdmission.Rejected("Invalid peer identity")
        }
        if (hello.appVersion.length !in 1..64 || hello.displayName.length > 160) {
            return PeerServer.ControlAdmission.Rejected("Invalid client metadata")
        }
        if (hello.clientNonce.length !in 16..128 || !HELLO_TOKEN_PATTERN.matches(hello.clientNonce)) {
            return PeerServer.ControlAdmission.Rejected("Invalid connection request")
        }
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val attemptState = pinAttempts[remoteAddress]
        if (attemptState != null && attemptState.blockedUntilElapsedMs > nowElapsedMs) {
            return PeerServer.ControlAdmission.Rejected("Too many PIN attempts; try again shortly")
        }
        val expectedProof = roomPin?.let { Crypto.pinProof(snapshot.roomId, it, hello.clientNonce) }
        val suppliedProof = hello.pinProof
        if (expectedProof == null || suppliedProof == null ||
            !Crypto.constantTimeEquals(expectedProof.encodeToByteArray(), suppliedProof.encodeToByteArray())
        ) {
            pinAttempts.compute(remoteAddress) { _, previous ->
                val failures = if (previous == null || previous.blockedUntilElapsedMs <= nowElapsedMs) {
                    (previous?.failures ?: 0) + 1
                } else previous.failures
                val blockedUntil = if (failures >= MAX_PIN_FAILURES) nowElapsedMs + PIN_BACKOFF_MS else 0L
                PinAttemptState(if (blockedUntil > 0) 0 else failures, blockedUntil)
            }
            return PeerServer.ControlAdmission.Rejected("Incorrect room PIN")
        }
        pinAttempts.remove(remoteAddress)
        if (hello.listeningPort !in 1..65535) return PeerServer.ControlAdmission.Rejected("Invalid peer port")
        val isKnownPeer = snapshot.members.any { it.peerId == hello.peerId }
        if (!isKnownPeer && snapshot.members.count { it.connected } >= MAX_ROOM_MEMBERS) {
            return PeerServer.ControlAdmission.Rejected("Room is full")
        }

        val secret = roomSecret ?: return PeerServer.ControlAdmission.Rejected("Room is restarting")
        val serverNonce = Crypto.randomBase64(18)
        val endpoint = PeerEndpoint(
            peerId = hello.peerId,
            displayName = hello.displayName.trim().take(40).ifBlank { "Friend" },
            hostAddress = remoteAddress,
            port = hello.listeningPort,
            appVersion = hello.appVersion,
            lastSeenElapsedMs = SystemClock.elapsedRealtime(),
        )
        val encryptedSecret = Crypto.encryptAesGcm(
            key = Crypto.derivePinKey(
                snapshot.roomId,
                roomPin ?: return PeerServer.ControlAdmission.Rejected("Room PIN unavailable"),
                hello.clientNonce
            ),
            plaintext = secret,
            associatedData = "${snapshot.roomId}:${hello.peerId.value}".encodeToByteArray(),
        )
        val response = HandshakeMessage.CoordinatorHello(
            acceptedVersion = PROTOCOL_VERSION,
            term = snapshot.term.number,
            coordinatorPeerId = identity.peerId,
            serverNonce = serverNonce,
            encryptedRoomSecretBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(encryptedSecret.ciphertext),
            roomSecretIvBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(encryptedSecret.iv),
            snapshotSequence = snapshot.sequence,
        )
        return PeerServer.ControlAdmission.Accepted(
            response = response,
            sessionKey = Crypto.deriveSessionKey(secret, hello.clientNonce, serverNonce),
            endpoint = endpoint,
            roomId = snapshot.roomId,
            onEnvelope = ::onEnvelope,
            onClosed = ::onControlClosed,
        )
    }

    override suspend fun onControlConnected(connection: ControlConnection) {
        connections.put(connection.peerId, connection)?.close()
        lastSeenElapsedMs[connection.peerId] = SystemClock.elapsedRealtime()
        if (!isCoordinator()) return
        peerDirectory[connection.peerId] = connection.endpoint
        val member = MemberSnapshot(
            connection.peerId,
            connection.endpoint.displayName,
            connection.endpoint,
        )
        val updated = canonicalMutationMutex.withLock {
            val snapshot = engine?.snapshot() ?: return@withLock null
            val sequence = snapshot.sequence + 1
            val joined = ProtocolBody.PeerJoined(member)
            val value = engine?.apply(sequence, joined) ?: return@withLock null
            updateSnapshot(value)
            connection.send(envelope(ProtocolBody.JoinAccepted(value, peerDirectory.values.toList()), sequence = null))
            broadcastCanonical(sequence, joined, except = connection.peerId)
            value
        } ?: return
        reevaluateAllPreparation()
        broadcast(ProtocolBody.PeerDirectory(peerDirectory.values.toList()))
        updated.queue.take(updated.options.preloadCount + 1).forEach { track ->
            send(connection.peerId, ProtocolBody.TrackDescriptorMessage(track.track))
        }
    }

    override suspend fun onFileConnection(socket: Socket, hello: HandshakeMessage.ClientHello) {
        transferManager?.handleIncomingFileSocket(socket, hello) ?: socket.close()
    }

    private suspend fun onControlClosed(connection: ControlConnection, cause: Throwable?) {
        val peerId = connection.peerId
        // A reconnect may replace an older socket for the same peer. The older socket's delayed
        // close callback must never remove or mark the new connection disconnected.
        val wasCurrent = connections.remove(peerId, connection)
        if (!wasCurrent && coordinatorConnection !== connection) return
        if (coordinatorPeerId == peerId && !isCoordinator()) {
            if (coordinatorConnection === connection) coordinatorConnection = null
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
            canonicalMutationMutex.withLock {
                val snapshot = engine?.snapshot() ?: return@withLock
                val member = snapshot.members.firstOrNull { it.peerId == peerId } ?: return@withLock
                val sequence = snapshot.sequence + 1
                val body = ProtocolBody.PeerUpdated(member.copy(connected = false))
                val updated = engine?.apply(sequence, body) ?: return@withLock
                updateSnapshot(updated)
                broadcastCanonical(sequence, body)
            }
            reevaluateAllPreparation()
        }
        log.i(TAG, "Peer disconnected ${peerId.value.take(8)} ${cause?.message.orEmpty()}")
    }

    private suspend fun attemptReconnectThenRecover(lostCoordinator: PeerId) {
        val snapshot = engine?.snapshot() ?: return
        val endpoint = peerDirectory[lostCoordinator]
        val pin = roomPin
        if (endpoint != null && pin != null) {
            repeat(3) { attempt ->
                delay(1_200L + attempt * 600L)
                val result = runCatching {
                    controlClient.connect(
                        identity = identity,
                        roomId = snapshot.roomId,
                        host = endpoint.hostAddress,
                        port = endpoint.port,
                        listeningPort = server.port,
                        pin = pin,
                        appVersion = BuildConfig.VERSION_NAME,
                        onEnvelope = ::onEnvelope,
                        onClosed = ::onControlClosed,
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
                    result.connection.send(envelope(ProtocolBody.RejoinRequest(snapshot.sequence, cached, server.port)))
                    return
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
                roomPin = roomPin,
            )
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
            val pin = roomPin
            if (endpoint == null || pin == null) {
                setFailure("Room connection was lost")
                return
            }
            var restored = false
            repeat(5) { attempt ->
                if (restored) return@repeat
                delay(350L + attempt * 450L)
                val connected = runCatching {
                    controlClient.connect(
                        identity, snapshot.roomId, endpoint.hostAddress, endpoint.port, server.port,
                        pin, BuildConfig.VERSION_NAME, ::onEnvelope, ::onControlClosed,
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
        runCatching { sendToCoordinator(ProtocolBody.LeaveRoom("left")) }
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
        persistenceJob?.cancel(); persistenceJob = null
        speedResetJob?.cancel(); speedResetJob = null
        queueRefreshJob?.cancel(); queueRefreshJob = null
        timelineRefreshJob?.cancel(); timelineRefreshJob = null
        recoveryJob?.cancel(); recoveryJob = null
        transferManager?.cancelAll()
        connections.values.forEach { it.close() }
        connections.clear()
        coordinatorConnection = null
        coordinatorPeerId = null
        peerDirectory.clear()
        availability.clear()
        waitingForSource.clear()
        lastSeenElapsedMs.clear()
        announcedTrackIds.clear()
        clockReadyPeers.clear()
        transferFailureCounts.clear()
        pendingTransferAssignments.clear()
        pinAttempts.clear()
        recentCommandIds.clear()
        lastObservedPlayerItem = null
        lastHandledEndedItem = null
        lastObservedRepeatTransitionRevision = player.state.value.repeatTransitionRevision
        pendingAutoResumeQueueItemId = null
        pendingPlayRequestedBy = null
        engine = null
        roomSecret = null
        roomPin = null
        clockSync.reset()
        discovery.stopAdvertising()
        wifiLocks.releaseWifi()
        if (!keepDiscovery) stopDiscovery()
    }

    private fun isCoordinator(): Boolean =
        ::identity.isInitialized && coordinatorPeerId == identity.peerId && engine != null

    private suspend fun sendToCoordinator(body: ProtocolBody) {
        if (isCoordinator()) {
            when (body) {
                is ProtocolBody.TrackHave -> onTrackHave(identity.peerId, body.trackId)
                is ProtocolBody.TrackNeed -> onTrackNeed(identity.peerId, body.trackId)
                is ProtocolBody.TrackFailed -> onTrackFailed(identity.peerId, body)
                is ProtocolBody.UserCommandRequest -> applyCoordinatorCommand(body.command)
                else -> Unit
            }
            return
        }
        coordinatorConnection?.send(envelope(body)) ?: setError("Room connection is unavailable")
    }

    private suspend fun send(peerId: PeerId, body: ProtocolBody) {
        if (peerId == identity.peerId) {
            onEnvelope(identity.peerId, envelope(body))
            return
        }
        connections[peerId]?.send(envelope(body))
    }

    private suspend fun broadcast(body: ProtocolBody, except: PeerId? = null) {
        val value = envelope(body)
        for ((peer, connection) in connections.entries) {
            if (peer != except && !connection.trySend(value)) {
                connection.close(IllegalStateException("Control queue is full"))
            }
        }
    }

    private suspend fun broadcastCanonical(sequence: Long, body: ProtocolBody, except: PeerId? = null) {
        val value = envelope(body, sequence)
        for ((peer, connection) in connections.entries) {
            if (peer != except && !connection.trySend(value)) {
                // A peer that cannot accept ordered canonical updates must reconnect and obtain a
                // full snapshot. Never let one dead socket delay commands for every listener.
                connection.close(IllegalStateException("Canonical control queue is full"))
            }
        }
    }

    private suspend fun envelope(body: ProtocolBody, sequence: Long? = null): Envelope {
        val snapshot = engine?.snapshot()
        return Envelope(
            roomId = snapshot?.roomId ?: container.roomStore.state.value.snapshot?.roomId.orEmpty(),
            term = snapshot?.term?.number ?: 0,
            coordinatorPeerId = snapshot?.term?.coordinatorPeerId,
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

    private fun userFacingJoinFailure(error: Throwable): String {
        val detail = error.message.orEmpty()
        return when {
            detail.contains("Incorrect room PIN", ignoreCase = true) -> "The room PIN is incorrect"
            detail.contains("too many PIN", ignoreCase = true) -> "Too many attempts. Try again shortly"
            detail.contains("incompatible", ignoreCase = true) ||
                detail.contains("protocol", ignoreCase = true) -> "This room uses a different Unison version"

            detail.contains("room is full", ignoreCase = true) -> "This room is full"
            detail.contains("cannot join yourself", ignoreCase = true) -> "This phone is already in the room"
            else -> "Could not connect to this room"
        }
    }

    private fun setError(message: String) {
        container.roomStore.update { it.copy(errorMessage = message, statusMessage = null) }
    }

    private fun setFailure(message: String) {
        container.roomStore.update {
            it.copy(
                lifecycle = RoomLifecycleState.FAILED,
                status = UserFacingStatus.UNAVAILABLE,
                errorMessage = message,
                statusMessage = null
            )
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        addressMonitorJob?.cancel()
        addressMonitorJob = null
        resetSession(keepDiscovery = false)
        canonicalSideEffects.close()
        hotspot.stop()
        server.stop()
        wifiLocks.close()
    }

    companion object {
        private const val TAG = "RoomRuntime"
        private const val MAX_ROOM_MEMBERS = 8
        private const val HEARTBEAT_INTERVAL_MS = 5_000L
        private const val CLOCK_SYNC_WARMUP_INTERVAL_MS = 250L
        private const val CLOCK_SYNC_STEADY_INTERVAL_MS = 5_000L
        private const val PLAYBACK_SYNC_INTERVAL_MS = 2_000L
        private const val SNAPSHOT_PERSIST_INTERVAL_MS = 30_000L
        private const val TRANSFER_TOKEN_LIFETIME_MS = 60_000L
        private const val TRANSFER_RETRY_DELAY_MS = 1_500L
        private const val SOURCE_AUTHORIZATION_TIMEOUT_MS = 2_500L
        private const val MAX_SOURCE_FAILURES = 2
        private const val MAX_REJOIN_CACHE_IDS = 1_000
        private const val HOTSPOT_INTERFACE_SETTLE_MS = 800L
        private const val LOCAL_ADDRESS_POLL_INTERVAL_MS = 2_000L
        private const val TRANSITION_RECONCILE_DELAY_MS = 80L
        private const val TIMELINE_REFRESH_DEBOUNCE_MS = 60L
        private const val PLAYER_HISTORY_ITEMS = 2
        private const val PLAYER_UPCOMING_ITEMS = 12
        private const val FUTURE_COMMAND_TOLERANCE_NS = 5_000_000L
        private const val ELECTION_DELAY_MS = 3_000L
        private const val PEER_TIMEOUT_MS = 18_000L
        private const val MAX_PIN_FAILURES = 5
        private const val PIN_BACKOFF_MS = 30_000L
        private val HELLO_TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]+$")
    }
}
