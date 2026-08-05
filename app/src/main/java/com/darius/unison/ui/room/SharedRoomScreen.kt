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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.RoomUiState
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransportAction
import com.darius.unison.room.QueueDragPolicy
import com.darius.unison.storage.PlaylistSummary
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The complete in-room experience. Room identity, code, participants and the canonical player are
 * the first viewport; the same vertical surface becomes the queue as the user scrolls.
 */
@Composable
internal fun SharedRoomScreen(
    room: RoomUiState,
    playlists: List<PlaylistSummary>,
    libraryTotalCount: Int,
    temporaryTrackIds: Set<TrackId>,
    retentionPolicy: RetentionPolicy,
    playbackPositionFlow: StateFlow<Long>,
    pickerTracksFlow: Flow<PagingData<TrackDescriptor>>,
    pickerQueryState: StateFlow<String>,
    onPickerQueryChange: (String) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlayQueueItem: (QueueItemId) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: (RepeatMode) -> Unit,
    onChooseFiles: () -> Unit,
    onImportM3u: () -> Unit,
    onSelectAllTracks: (String, (Set<TrackId>) -> Unit) -> Unit,
    onAddLibrarySelectionToRoom: (Boolean, List<String>, List<TrackId>) -> Unit,
    onRemoveQueueItem: (QueueItemId) -> Unit,
    onMoveQueueItem: (QueueItemId, Int) -> Unit,
    onMoveQueueItemNext: (QueueItemId) -> Unit,
    onKeepTrack: (TrackId) -> Unit,
    onUpdateOptions: (RoomOptions) -> Unit,
    onSetRetentionPolicy: (RetentionPolicy) -> Unit,
    onSaveQueue: (String) -> Unit,
    onClearPlayed: () -> Unit,
    onClearQueue: () -> Unit,
    onLeave: () -> Unit,
    onRetryIssue: () -> Unit,
    onDismissIssue: (String) -> Unit,
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
    val transportControls =
        remember(
            nowPlaying?.queueItemId,
            hasSeekableDuration,
            snapshot.playback.isPlaying,
            transportStatus,
        ) {
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = nowPlaying != null,
                hasSeekableDuration = hasSeekableDuration,
                localIsPlaying = snapshot.playback.isPlaying,
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
        if (!enabled || optimisticAction != null) return
        optimisticAction = action
        optimisticQueueItemId = queueItemId
        command()
    }

    val requestPlayPause = {
        val action = if (displayedPlaying) TransportAction.PAUSE else TransportAction.PLAY
        submitTransport(action, transportControls.canPlayPause) {
            if (displayedPlaying) onPause() else onPlay()
        }
    }
    val requestPrevious = {
        submitTransport(
            action = TransportAction.PREVIOUS,
            enabled = transportControls.canNavigate,
            command = onPrevious,
        )
    }
    val requestNext = {
        submitTransport(
            action = TransportAction.NEXT,
            enabled = transportControls.canNavigate,
            command = onNext,
        )
    }
    val requestQueueItem: (QueueItemId) -> Unit = { itemId ->
        submitTransport(
            action = TransportAction.PLAY_ITEM,
            enabled = transportControls.canSelectItem,
            queueItemId = itemId,
        ) {
            onPlayQueueItem(itemId)
        }
    }

    val activeTransfers =
        remember(room.transfers) {
            room.transfers.values
                .filter {
                    it.state == MemberTrackState.RECEIVING ||
                        it.state == MemberTrackState.VERIFYING ||
                        it.state == MemberTrackState.CANCELLED ||
                        it.state == MemberTrackState.FAILED
                }
                .sortedBy { it.trackId.value }
        }

    val listState = rememberLazyListState()
    val compactPlayerVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 &&
                listState.layoutInfo.visibleItemsInfo.none { it.key == "room-player" }
        }
    }
    val localRoomCode = room.localRoomPin
    var roomMenu by remember { mutableStateOf(false) }
    var showRoomCode by remember { mutableStateOf(false) }
    var showListeners by remember { mutableStateOf(false) }
    var showAddMusic by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var saveQueueOpen by remember { mutableStateOf(false) }
    var confirmClearQueue by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var queueQuery by rememberSaveable(snapshot.roomId) { mutableStateOf("") }
    val visibleQueue =
        remember(snapshot.queue, queueQuery) {
            val query = queueQuery.trim()
            if (query.isEmpty()) snapshot.queue.withIndex().toList()
            else
                snapshot.queue.withIndex().filter { indexed ->
                    val track = indexed.value.track
                    track.displayTitle.contains(query, ignoreCase = true) ||
                        track.artist?.contains(query, ignoreCase = true) == true ||
                        track.album?.contains(query, ignoreCase = true) == true
                }
        }

    fun openAddMusic() {
        onPickerQueryChange("")
        showAddMusic = true
    }

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "room-header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            snapshot.roomName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${snapshot.members.count { it.connected }} listening",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { roomMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Room actions")
                        }
                        DropdownMenu(expanded = roomMenu, onDismissRequest = { roomMenu = false }) {
                            if (localRoomCode != null) {
                                DropdownMenuItem(
                                    text = { Text("Show room code") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                    onClick = {
                                        roomMenu = false
                                        showRoomCode = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Listeners") },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                onClick = {
                                    roomMenu = false
                                    showListeners = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save queue as playlist") },
                                enabled = snapshot.queue.isNotEmpty(),
                                onClick = {
                                    roomMenu = false
                                    saveQueueOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear played songs") },
                                enabled =
                                    snapshot.queue.indexOfFirst {
                                        it.queueItemId == snapshot.playback.queueItemId
                                    } > 0,
                                onClick = {
                                    roomMenu = false
                                    onClearPlayed()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Room settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = {
                                    roomMenu = false
                                    showOptions = true
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Leave room") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                                onClick = {
                                    roomMenu = false
                                    confirmLeave = true
                                },
                            )
                        }
                    }
                }
            }

            item(key = "participants") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(snapshot.members, key = { it.peerId.value }) { member ->
                        ParticipantStatus(
                            member.displayName,
                            member.connected,
                            member.currentTrackState,
                        )
                    }
                }
            }

            room.issue?.let { issue ->
                item(key = "room-issue") {
                    PersistentRoomIssueCard(
                        issue = issue,
                        transportStatus = room.transportStatus,
                        onDismiss = { onDismissIssue(issue.message) },
                        onRetryTransport = onRetryIssue,
                        onChooseFiles = onChooseFiles,
                        onLeaveRoom = { confirmLeave = true },
                    )
                }
            }

            item(key = "room-player") {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(
                            nowPlaying?.track?.displayTitle ?: "Nothing playing",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            nowPlaying?.track?.artist?.takeIf(String::isNotBlank)
                                ?: if (snapshot.queue.isEmpty()) "Add music from the queue below"
                                else "Choose a song from the queue",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (nowPlaying != null) {
                            RoomSeekSlider(
                                playbackPositionFlow = playbackPositionFlow,
                                durationMs = duration,
                                enabled = transportControls.canSeek,
                                transportStatus = transportStatus,
                                onSeek = onSeek,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                TransportControlButton(
                                    active = feedbackAction == TransportAction.PREVIOUS,
                                    enabled =
                                        transportControls.canNavigate && optimisticAction == null,
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
                                    enabled =
                                        transportControls.canPlayPause && optimisticAction == null,
                                    onClick = requestPlayPause,
                                )
                                TransportControlButton(
                                    active = feedbackAction == TransportAction.NEXT,
                                    enabled =
                                        transportControls.canNavigate && optimisticAction == null,
                                    onClick = requestNext,
                                    contentDescription = "Next",
                                ) {
                                    Icon(Icons.Default.SkipNext, null, Modifier.size(32.dp))
                                }
                            }
                        } else if (snapshot.queue.isNotEmpty()) {
                            TextButton(
                                onClick = { requestQueueItem(snapshot.queue.first().queueItemId) }
                            ) {
                                Text("Play first song")
                            }
                        }
                    }
                }
            }

            item(key = "queue-toolbar") {
                RoomQueueToolbar(
                    query = queueQuery,
                    shuffleEnabled = snapshot.shuffleEnabled,
                    repeatMode = snapshot.repeatMode,
                    queueEnabled = snapshot.queue.isNotEmpty(),
                    onQueryChange = { queueQuery = it },
                    onShuffle = onShuffle,
                    onRepeat = { onRepeat(snapshot.repeatMode.next()) },
                    onClear = { confirmClearQueue = true },
                )
            }

            if (room.lifecycle == RoomLifecycleState.RECONNECTING) {
                item(key = "room-reconnecting") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            "Reconnecting…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (activeTransfers.isNotEmpty()) {
                item(key = "room-transfers") {
                    TransferStatusCard(
                        transfers = activeTransfers,
                        titles =
                            snapshot.queue.associate { it.track.trackId to it.track.displayTitle },
                    )
                }
            }

            item(key = "queue-title") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Up next",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (queueQuery.isBlank()) {
                                "${snapshot.queue.size} ${if (snapshot.queue.size == 1) "song" else "songs"}"
                            } else {
                                "${visibleQueue.size} matching"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { openAddMusic() }) {
                        Icon(Icons.Default.Add, "Add music")
                    }
                }
            }

            when {
                snapshot.queue.isEmpty() ->
                    item(key = "queue-empty") {
                        Text(
                            "Nothing queued",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                visibleQueue.isEmpty() ->
                    item(key = "queue-no-results") {
                        Text(
                            "No queue matches",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                else ->
                    items(visibleQueue, key = { "queue:${it.value.queueItemId.value}" }) { indexed
                        ->
                        val index = indexed.index
                        val item = indexed.value
                        QueueRow(
                            index = index,
                            lastIndex = snapshot.queue.lastIndex,
                            track = item.track,
                            current = item.queueItemId == displayedQueueItemId,
                            playing = item.queueItemId == displayedQueueItemId && displayedPlaying,
                            temporary = item.track.trackId in temporaryTrackIds,
                            pending = item.queueItemId == feedbackQueueItemId,
                            canReorder = !snapshot.shuffleEnabled && queueQuery.isBlank(),
                            draggedIndex = draggedQueueIndex,
                            dragTargetIndex = dragTargetIndex,
                            dragOffsetPx = if (draggedQueueIndex == index) dragOffsetPx else 0f,
                            onDragStart = { startQueueDrag(index) },
                            onDragDelta = ::updateQueueDrag,
                            onDragCancel = ::resetQueueDrag,
                            onDragEnd = {
                                val target = dragTargetIndex ?: index
                                resetQueueDrag()
                                if (target != index) onMoveQueueItem(item.queueItemId, target)
                            },
                            onMove = { onMoveQueueItem(item.queueItemId, it) },
                            playEnabled =
                                transportControls.canSelectItem && optimisticAction == null,
                            onPlay = { requestQueueItem(item.queueItemId) },
                            onMoveNext = { onMoveQueueItemNext(item.queueItemId) },
                            onRemove = { onRemoveQueueItem(item.queueItemId) },
                            onKeep = { onKeepTrack(item.track.trackId) },
                        )
                    }
            }
        }

        if (compactPlayerVisible && nowPlaying != null) {
            SharedCompactPlayer(
                track = nowPlaying.track,
                isPlaying = displayedPlaying,
                pendingAction = feedbackAction,
                onPrevious = requestPrevious,
                onPlayPause = requestPlayPause,
                onNext = requestNext,
                navigationEnabled = transportControls.canNavigate && optimisticAction == null,
                playPauseEnabled = transportControls.canPlayPause && optimisticAction == null,
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .zIndex(2f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    if (showAddMusic) {
        RoomAddMusicSheet(
            pickerTracksFlow = pickerTracksFlow,
            pickerQueryState = pickerQueryState,
            playlists = playlists,
            libraryTotalCount = libraryTotalCount,
            onPickerQueryChange = onPickerQueryChange,
            onChooseFiles = {
                showAddMusic = false
                onChooseFiles()
            },
            onImportM3u = {
                showAddMusic = false
                onImportM3u()
            },
            onSelectAllTracks = onSelectAllTracks,
            onAddSelection = { includeAllMusic, playlistIds, trackIds ->
                showAddMusic = false
                onAddLibrarySelectionToRoom(includeAllMusic, playlistIds, trackIds)
            },
            onDismiss = { showAddMusic = false },
        )
    }

    if (showRoomCode && localRoomCode != null) {
        RoomCodeDialog(roomCode = localRoomCode, onDismiss = { showRoomCode = false })
    }

    if (showListeners) {
        RoomListenersDialog(
            members = snapshot.members,
            onDismiss = { showListeners = false },
        )
    }

    if (saveQueueOpen) {
        SaveQueueDialog(
            initialName = "${snapshot.roomName} queue",
            onSave = { name ->
                saveQueueOpen = false
                onSaveQueue(name)
            },
            onDismiss = { saveQueueOpen = false },
        )
    }

    if (showOptions) {
        RoomOptionsDialog(
            initialOptions = snapshot.options,
            initialRetention = retentionPolicy,
            onSave = { options, retention ->
                showOptions = false
                onUpdateOptions(options)
                onSetRetentionPolicy(retention)
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
                onClearQueue()
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
                onLeave()
            },
            onDismiss = { confirmLeave = false },
        )
    }
}
