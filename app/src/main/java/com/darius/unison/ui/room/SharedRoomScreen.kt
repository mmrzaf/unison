package com.darius.unison.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.paging.PagingData
import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomMediaReadiness
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.RoomUiState
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransportAction
import com.darius.unison.room.QueueDragPolicy
import com.darius.unison.room.QueueShufflePolicy
import com.darius.unison.storage.PlaylistSummary
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The complete music-first in-room experience. Healthy networking and transfer machinery stay out
 * of the primary surface; the player, queue and user intent dominate the room.
 */
@Composable
internal fun SharedRoomScreen(
    room: RoomUiState,
    playlists: List<PlaylistSummary>,
    libraryTotalCount: Int,
    temporaryTrackIds: Set<TrackId>,
    playbackPositionFlow: StateFlow<Long>,
    pickerTracksFlow: Flow<PagingData<TrackDescriptor>>,
    pickerQueryState: StateFlow<String>,
    diagnosticRevision: StateFlow<Long>,
    actions: SharedRoomActions,
) {
    val snapshot = room.snapshot
    if (snapshot == null) {
        EmptyState("No active room", "Create a room or join someone nearby.", Icons.Default.Groups)
        return
    }

    // The visible player always follows canonical room intent. Local Media3 state is health
    // telemetry only and must never redefine the room's song or play/pause icon.
    val displayedQueueItemId = snapshot.playback.queueItemId
    val nowPlaying = displayedQueueItemId?.let { id ->
        snapshot.queue.firstOrNull { it.queueItemId == id }
    }
    val hasSeekableDuration = (nowPlaying?.track?.durationMs ?: 0L) > 0L
    val duration = nowPlaying?.track?.durationMs?.coerceAtLeast(1L) ?: 1L
    val transportStatus = room.transportStatus
    val localOutputInhibited =
        room.localPlaybackParticipation == LocalPlaybackParticipation.OUTPUT_INHIBITED
    val transportControls =
        remember(
            nowPlaying?.queueItemId,
            hasSeekableDuration,
            snapshot.playback.isPlaying,
            localOutputInhibited,
            transportStatus,
        ) {
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = nowPlaying != null,
                hasSeekableDuration = hasSeekableDuration,
                localIsPlaying =
                    snapshot.playback.isPlaying && !localOutputInhibited,
                status = transportStatus,
            )
        }
    val displayedPlaying = transportControls.displayedPlaying
    var optimisticAction by
        remember(snapshot.roomId) {
            mutableStateOf<TransportAction?>(null)
        }
    var optimisticQueueItemId by
        remember(snapshot.roomId) {
            mutableStateOf<QueueItemId?>(null)
        }
    val canonicalActiveStatus = transportStatus?.takeIf { it.active }
    val feedbackAction = canonicalActiveStatus?.action ?: optimisticAction
    val feedbackQueueItemId = canonicalActiveStatus?.queueItemId ?: optimisticQueueItemId

    LaunchedEffect(transportStatus?.commandId, transportStatus?.phase) {
        if (transportStatus?.active == true || transportStatus?.phase?.isTerminal == true) {
            optimisticAction = null
            optimisticQueueItemId = null
        }
    }
    LaunchedEffect(optimisticAction) {
        val pending = optimisticAction ?: return@LaunchedEffect
        delay(1_500)
        if (optimisticAction == pending) {
            optimisticAction = null
            optimisticQueueItemId = null
        }
    }

    fun submitTransport(
        action: TransportAction,
        enabled: Boolean,
        queueItemId: QueueItemId? = null,
        command: () -> Unit,
    ) {
        if (!enabled) return
        optimisticAction = action
        optimisticQueueItemId = queueItemId
        command()
    }

    val requestPlayPause = {
        val action =
            if (localOutputInhibited) TransportAction.PLAY
            else if (displayedPlaying) TransportAction.PAUSE
            else TransportAction.PLAY
        submitTransport(action, transportControls.canPlayPause) {
            if (displayedPlaying) actions.playback.pause() else actions.playback.play()
        }
    }
    val requestPrevious = {
        submitTransport(
            action = TransportAction.PREVIOUS,
            enabled = transportControls.canNavigate,
            command = actions.playback.previous,
        )
    }
    val requestNext = {
        submitTransport(
            action = TransportAction.NEXT,
            enabled = transportControls.canNavigate,
            command = actions.playback.next,
        )
    }
    val requestQueueItem: (QueueItemId) -> Unit = { itemId ->
        if (room.mediaReadiness[itemId] == RoomMediaReadiness.READY) {
            submitTransport(
                action = TransportAction.PLAY_ITEM,
                enabled = transportControls.canSelectItem,
                queueItemId = itemId,
            ) {
                actions.playback.playQueueItem(itemId)
            }
        } else if (transportControls.canSelectItem) {
            actions.playback.prepareQueueItem(itemId)
        }
    }

    val transitionPresentation =
        remember(snapshot, room.lifecycle, transportStatus, room.transfers) {
            RoomPlaybackUiPolicy.transition(
                snapshot = snapshot,
                lifecycle = room.lifecycle,
                status = transportStatus,
                transfers = room.transfers,
            )
        }
    val currentIndex =
        remember(snapshot.queueRevision, displayedQueueItemId) {
            snapshot.queue.indexOfFirst { it.queueItemId == displayedQueueItemId }
        }
    val immediateNext =
        remember(snapshot.queueRevision, currentIndex) {
            snapshot.queue.getOrNull(currentIndex + 1)
        }

    val listState = rememberLazyListState()
    val compactPlayerVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 &&
                listState.layoutInfo.visibleItemsInfo.none { it.key == "room-player" }
        }
    }
    val localRoomCode = room.localRoomPin
    var showListeners by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showAddMusic by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var saveQueueOpen by remember { mutableStateOf(false) }
    var confirmClearQueue by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var queueQuery by rememberSaveable(snapshot.roomId) { mutableStateOf("") }
    val filteredQueue =
        remember(snapshot.queue, queueQuery) {
            val query = queueQuery.trim()
            if (query.isEmpty()) emptyList()
            else
                snapshot.queue.filter { item ->
                    val track = item.track
                    track.displayTitle.contains(query, ignoreCase = true) ||
                        track.artist?.contains(query, ignoreCase = true) == true ||
                        track.album?.contains(query, ignoreCase = true) == true
                }
        }

    fun openAddMusic() {
        actions.queue.pickerQueryChange("")
        showAddMusic = true
    }

    var reorderMode by rememberSaveable(snapshot.roomId) { mutableStateOf(false) }
    var draggedQueueIndex by remember { mutableStateOf<Int?>(null) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragPointerCenterPx by remember { mutableStateOf<Float?>(null) }
    var dragAutoScrollPx by remember { mutableFloatStateOf(0f) }
    val queueIndexByLazyKey =
        remember(snapshot.queue) {
            snapshot.queue
                .mapIndexed { index, item -> "queue:${item.queueItemId.value}" to index }
                .toMap()
        }
    val dragEdgePx = with(LocalDensity.current) { 72.dp.toPx() }
    val maxDragScrollPx = with(LocalDensity.current) { 28.dp.toPx() }

    fun recalculateQueueDrag() {
        if (snapshot.queue.isEmpty()) return
        val origin = draggedQueueIndex ?: return
        val pointerCenter = dragPointerCenterPx ?: return
        val layout = listState.layoutInfo
        val visibleQueueItems =
            layout.visibleItemsInfo.mapNotNull { item ->
                val queueIndex = queueIndexByLazyKey[item.key] ?: return@mapNotNull null
                QueueDragPolicy.VisibleItem(
                    queueIndex = queueIndex,
                    offsetPx = item.offset.toFloat(),
                    sizePx = item.size.toFloat(),
                )
            }
        dragTargetIndex =
            QueueDragPolicy.targetIndex(pointerCenter, visibleQueueItems, origin)
                .coerceIn(0, snapshot.queue.lastIndex)
        dragAutoScrollPx =
            QueueDragPolicy.autoScrollPerFrame(
                pointerCenterPx = pointerCenter,
                viewportStartPx = layout.viewportStartOffset.toFloat(),
                viewportEndPx = layout.viewportEndOffset.toFloat(),
                edgeSizePx = dragEdgePx,
                maxScrollPx = maxDragScrollPx,
            )
    }

    fun startQueueDrag(index: Int) {
        val key = "queue:${snapshot.queue[index].queueItemId.value}"
        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        draggedQueueIndex = index
        dragTargetIndex = index
        dragOffsetPx = 0f
        dragPointerCenterPx = visibleItem?.let { it.offset + it.size / 2f }
        dragAutoScrollPx = 0f
        recalculateQueueDrag()
    }

    fun updateQueueDrag(deltaY: Float) {
        dragOffsetPx += deltaY
        dragPointerCenterPx = (dragPointerCenterPx ?: 0f) + deltaY
        recalculateQueueDrag()
    }

    fun resetQueueDrag() {
        draggedQueueIndex = null
        dragTargetIndex = null
        dragOffsetPx = 0f
        dragPointerCenterPx = null
        dragAutoScrollPx = 0f
    }

    LaunchedEffect(snapshot.queueRevision) {
        if (draggedQueueIndex != null) resetQueueDrag()
    }
    LaunchedEffect(queueQuery) {
        if (queueQuery.isNotBlank()) {
            resetQueueDrag()
            reorderMode = false
        }
    }
    LaunchedEffect(draggedQueueIndex) {
        while (draggedQueueIndex != null) {
            withFrameNanos {}
            val requested = dragAutoScrollPx
            if (abs(requested) < 0.5f) continue
            val consumed = listState.scrollBy(requested)
            if (abs(consumed) < 0.5f) {
                dragAutoScrollPx = 0f
                continue
            }
            dragOffsetPx += consumed
            recalculateQueueDrag()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "room-header") {
                RoomHeader(
                    roomName = snapshot.roomName,
                    roomCode = localRoomCode,
                    connectedListeners = snapshot.members.size,
                    lifecycle = room.lifecycle,
                    onShowListeners = { showListeners = true },
                    onShowLogs = { showLogs = true },
                    onShowSettings = { showOptions = true },
                    onShowAbout = actions.session.showAbout,
                    onLeave = { confirmLeave = true },
                )
            }

            item(key = "room-player") {
                Column(
                    Modifier.fillMaxWidth().padding(start = 6.dp, top = 24.dp, end = 6.dp, bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (nowPlaying != null) {
                        Text(
                            "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        nowPlaying?.track?.displayTitle ?: "Nothing playing",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        nowPlaying?.track?.artist?.takeIf(String::isNotBlank)
                            ?: if (snapshot.queue.isEmpty()) "Add music to start listening together"
                            else "Choose a song from the queue",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    transitionPresentation?.let { PlaybackTransitionStatus(it) }
                    immediateNext?.let { next ->
                        if (transitionPresentation?.queueItemId != next.queueItemId) {
                            UpNextStatus(
                                track = next.track,
                                transfer = room.transfers[next.track.trackId],
                                readiness =
                                    room.mediaReadiness[next.queueItemId]
                                        ?: RoomMediaReadiness.NEEDS_PREPARATION,
                            )
                        }
                    }
                    if (localOutputInhibited && snapshot.playback.isPlaying) {
                        Text(
                            when (room.localPlaybackInhibitionReason) {
                                LocalPlaybackInhibitionReason.BECOMING_NOISY ->
                                    "Your audio output disconnected · The room kept playing"
                                LocalPlaybackInhibitionReason.AUDIO_FOCUS ->
                                    "Your audio was interrupted · The room kept playing"
                                LocalPlaybackInhibitionReason.UNSUITABLE_OUTPUT ->
                                    "Your audio output is unavailable · The room kept playing"
                                LocalPlaybackInhibitionReason.SYSTEM_POLICY ->
                                    "Your phone paused audio · The room kept playing"
                                null -> "Audio is paused on this phone · The room kept playing"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (nowPlaying != null) {
                        RoomSeekSlider(
                            playbackPositionFlow = playbackPositionFlow,
                            durationMs = duration,
                            enabled = transportControls.canSeek,
                            transportStatus = transportStatus,
                            onSeek = actions.playback.seek,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            TransportControlButton(
                                active = feedbackAction == TransportAction.PREVIOUS,
                                enabled = transportControls.canNavigate,
                                onClick = requestPrevious,
                                contentDescription = "Previous",
                            ) {
                                Icon(Icons.Default.SkipPrevious, null, Modifier.size(32.dp))
                            }
                            TransportPlayPauseButton(
                                isPlaying = displayedPlaying,
                                pending =
                                    feedbackAction == TransportAction.PLAY ||
                                        feedbackAction == TransportAction.PAUSE,
                                enabled = transportControls.canPlayPause,
                                onClick = requestPlayPause,
                            )
                            TransportControlButton(
                                active = feedbackAction == TransportAction.NEXT,
                                enabled = transportControls.canNavigate,
                                onClick = requestNext,
                                contentDescription = "Next",
                            ) {
                                Icon(Icons.Default.SkipNext, null, Modifier.size(32.dp))
                            }
                        }
                    } else if (snapshot.queue.isNotEmpty()) {
                        val firstItem = snapshot.queue.first()
                        val firstReadiness =
                            room.mediaReadiness[firstItem.queueItemId]
                                ?: RoomMediaReadiness.NEEDS_PREPARATION
                        val firstPreparationFailed =
                            room.transfers[firstItem.track.trackId]?.state == MemberTrackState.FAILED
                        FilledTonalButton(
                            onClick = { requestQueueItem(firstItem.queueItemId) },
                            enabled =
                                firstReadiness != RoomMediaReadiness.PREPARING ||
                                    firstPreparationFailed,
                        ) {
                            Text(
                                when {
                                    firstReadiness == RoomMediaReadiness.READY -> "Play first song"
                                    firstPreparationFailed -> "Retry preparing first song"
                                    firstReadiness == RoomMediaReadiness.PREPARING ->
                                        "Preparing first song…"
                                    else -> "Prepare first song"
                                }
                            )
                        }
                    } else {
                        FilledTonalButton(onClick = ::openAddMusic) {
                            Icon(Icons.Default.Add, null)
                            Text("Add music", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            room.issue?.let { issue ->
                item(key = "room-issue") {
                    PersistentRoomIssueCard(
                        issue = issue,
                        transportStatus = room.transportStatus,
                        onDismiss = { actions.session.dismissIssue(issue.message) },
                        onRetryTransport = actions.session.retryIssue,
                        onChooseFiles = actions.queue.chooseFiles,
                        onLeaveRoom = { confirmLeave = true },
                    )
                }
            }

            item(key = "queue-title") {
                SectionHeader(
                    title = "Queue",
                    subtitle =
                        if (queueQuery.isBlank()) {
                            RoomQueueUiPolicy.queueSummary(
                                queueSize = snapshot.queue.size,
                                readiness =
                                    snapshot.queue.map { item ->
                                        room.mediaReadiness[item.queueItemId]
                                            ?: RoomMediaReadiness.NEEDS_PREPARATION
                                    },
                            )
                        } else {
                            "${filteredQueue.size} matching"
                        },
                    modifier = Modifier.padding(top = 6.dp),
                    action = {
                        IconButton(onClick = ::openAddMusic) {
                            Icon(Icons.Default.Add, "Add music")
                        }
                    },
                )
            }

            item(key = "queue-toolbar") {
                RoomQueueToolbar(
                    query = queueQuery,
                    repeatMode = snapshot.repeatMode,
                    queueEnabled = snapshot.queue.isNotEmpty(),
                    shuffleAvailable =
                        QueueShufflePolicy.canShuffle(snapshot.queue, displayedQueueItemId),
                    canSaveQueue = snapshot.queue.isNotEmpty(),
                    canClearPlayed =
                        snapshot.queue.indexOfFirst { it.queueItemId == snapshot.playback.queueItemId } > 0,
                    reorderMode = reorderMode,
                    onQueryChange = { queueQuery = it },
                    onToggleReorder = {
                        resetQueueDrag()
                        reorderMode = !reorderMode
                    },
                    onShuffle = actions.queue.shuffle,
                    onRepeat = { actions.queue.repeat(snapshot.repeatMode.next()) },
                    onSaveQueue = { saveQueueOpen = true },
                    onClearPlayed = actions.queue.clearPlayed,
                    onClearQueue = { confirmClearQueue = true },
                )
            }

            when {
                snapshot.queue.isEmpty() ->
                    item(key = "queue-empty") {
                        EmptyState(
                            title = "Queue is empty",
                            text = "Add songs and everyone in the room will follow the same queue.",
                            icon = Icons.Default.LibraryMusic,
                            actionLabel = "Add music",
                            onAction = ::openAddMusic,
                        )
                    }
                queueQuery.isNotBlank() && filteredQueue.isEmpty() ->
                    item(key = "queue-no-results") {
                        Text(
                            "No queue matches",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                queueQuery.isBlank() ->
                    itemsIndexed(
                        items = snapshot.queue,
                        key = { _, item -> "queue:${item.queueItemId.value}" },
                        contentType = { _, _ -> "queue-row" },
                    ) { index, item ->
                        QueueRow(
                            index = index,
                            lastIndex = snapshot.queue.lastIndex,
                            track = item.track,
                            current = item.queueItemId == displayedQueueItemId,
                            playing = item.queueItemId == displayedQueueItemId && displayedPlaying,
                            temporary = item.track.trackId in temporaryTrackIds,
                            pending = item.queueItemId == feedbackQueueItemId,
                            transfer = room.transfers[item.track.trackId],
                            readiness = room.mediaReadiness[item.queueItemId]
                                ?: RoomMediaReadiness.NEEDS_PREPARATION,
                            canReorder = reorderMode,
                            draggedIndex = draggedQueueIndex,
                            dragTargetIndex = dragTargetIndex,
                            dragOffsetPx = if (draggedQueueIndex == index) dragOffsetPx else 0f,
                            onDragStart = { startQueueDrag(index) },
                            onDragDelta = ::updateQueueDrag,
                            onDragCancel = ::resetQueueDrag,
                            onDragEnd = {
                                val target = dragTargetIndex ?: index
                                resetQueueDrag()
                                if (target != index) actions.queue.moveQueueItem(item.queueItemId, target)
                            },
                            onMove = { actions.queue.moveQueueItem(item.queueItemId, it) },
                            playEnabled =
                                transportControls.canSelectItem &&
                                    (
                                        room.mediaReadiness[item.queueItemId] != RoomMediaReadiness.PREPARING ||
                                            room.transfers[item.track.trackId]?.state == MemberTrackState.FAILED
                                    ),
                            onPlay = { requestQueueItem(item.queueItemId) },
                            onMoveNext = { actions.queue.moveQueueItemNext(item.queueItemId) },
                            onRemove = { actions.queue.removeQueueItem(item.queueItemId) },
                            onKeep = { actions.queue.keepTrack(item.track.trackId) },
                        )
                    }
                else ->
                    items(
                        items = filteredQueue,
                        key = { item -> "queue:${item.queueItemId.value}" },
                        contentType = { "queue-row" },
                    ) { item ->
                        val index = queueIndexByLazyKey.getValue("queue:${item.queueItemId.value}")
                        QueueRow(
                            index = index,
                            lastIndex = snapshot.queue.lastIndex,
                            track = item.track,
                            current = item.queueItemId == displayedQueueItemId,
                            playing = item.queueItemId == displayedQueueItemId && displayedPlaying,
                            temporary = item.track.trackId in temporaryTrackIds,
                            pending = item.queueItemId == feedbackQueueItemId,
                            transfer = room.transfers[item.track.trackId],
                            readiness = room.mediaReadiness[item.queueItemId]
                                ?: RoomMediaReadiness.NEEDS_PREPARATION,
                            canReorder = false,
                            draggedIndex = null,
                            dragTargetIndex = null,
                            dragOffsetPx = 0f,
                            onDragStart = {},
                            onDragDelta = {},
                            onDragCancel = {},
                            onDragEnd = {},
                            onMove = { actions.queue.moveQueueItem(item.queueItemId, it) },
                            playEnabled =
                                transportControls.canSelectItem &&
                                    (
                                        room.mediaReadiness[item.queueItemId] != RoomMediaReadiness.PREPARING ||
                                            room.transfers[item.track.trackId]?.state == MemberTrackState.FAILED
                                    ),
                            onPlay = { requestQueueItem(item.queueItemId) },
                            onMoveNext = { actions.queue.moveQueueItemNext(item.queueItemId) },
                            onRemove = { actions.queue.removeQueueItem(item.queueItemId) },
                            onKeep = { actions.queue.keepTrack(item.track.trackId) },
                        )
                    }
            }
        }

        if (compactPlayerVisible && nowPlaying != null) {
            SharedCompactPlayer(
                track = nowPlaying.track,
                isPlaying = displayedPlaying,
                pendingAction = feedbackAction,
                statusText = transitionPresentation?.let { transition ->
                    transition.progressFraction?.let {
                        "${transition.message} · ${(it * 100).toInt()}%"
                    } ?: transition.message
                },
                onPrevious = requestPrevious,
                onPlayPause = requestPlayPause,
                onNext = requestNext,
                navigationEnabled = transportControls.canNavigate,
                playPauseEnabled = transportControls.canPlayPause,
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .zIndex(2f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }

    if (showAddMusic) {
        RoomAddMusicSheet(
            pickerTracksFlow = pickerTracksFlow,
            pickerQueryState = pickerQueryState,
            playlists = playlists,
            libraryTotalCount = libraryTotalCount,
            onPickerQueryChange = actions.queue.pickerQueryChange,
            onChooseFiles = {
                showAddMusic = false
                actions.queue.chooseFiles()
            },
            onImportM3u = {
                showAddMusic = false
                actions.queue.importM3u()
            },
            onSelectAllTracks = actions.queue.selectAllTracks,
            onAddSelection = { includeAllMusic, playlistIds, trackIds, insertAfterCurrent ->
                showAddMusic = false
                actions.queue.addLibrarySelectionToRoom(
                    includeAllMusic,
                    playlistIds,
                    trackIds,
                    insertAfterCurrent,
                )
            },
            onDismiss = { showAddMusic = false },
        )
    }

    if (showListeners) {
        RoomListenersSheet(
            members = snapshot.members,
            memberRuntime = room.memberRuntime,
            localPeerId = room.localIdentity?.peerId,
            isCoordinator = room.isCoordinator,
            lifecycle = room.lifecycle,
            onDismiss = { showListeners = false },
        )
    }

    if (showLogs) {
        RoomLogsDialog(
            revision = diagnosticRevision,
            loadEvents = actions.diagnostics.loadLogs,
            onClear = actions.diagnostics.clearLogs,
            onDismiss = { showLogs = false },
        )
    }

    if (saveQueueOpen) {
        SaveQueueDialog(
            initialName = "${snapshot.roomName} queue",
            onSave = { name ->
                saveQueueOpen = false
                actions.queue.saveQueue(name, snapshot.queue.map { it.track.trackId })
            },
            onDismiss = { saveQueueOpen = false },
        )
    }

    if (showOptions) {
        RoomOptionsDialog(
            initialOptions = snapshot.options,
            onSave = { options ->
                showOptions = false
                actions.session.updateOptions(options)
            },
            onDismiss = { showOptions = false },
        )
    }

    if (confirmClearQueue) {
        RoomConfirmationDialog(
            title = "Clear queue?",
            text = "Playback stops for everyone and all queued songs are removed.",
            confirmLabel = "Clear",
            dismissLabel = "Cancel",
            onConfirm = {
                confirmClearQueue = false
                actions.queue.clearQueue()
            },
            onDismiss = { confirmClearQueue = false },
        )
    }

    if (confirmLeave) {
        RoomConfirmationDialog(
            title = "Leave room?",
            text = "Playback and transfers on this phone will stop.",
            confirmLabel = "Leave",
            dismissLabel = "Stay",
            onConfirm = {
                confirmLeave = false
                actions.session.leave()
            },
            onDismiss = { confirmLeave = false },
        )
    }
}
