package com.darius.unison.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.darius.unison.R
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransportAction
import com.darius.unison.room.QueueDragPolicy
import com.darius.unison.room.QueueSearchIndex
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
internal fun RoomLobbyScreen(
    state: MainUiState,
    onCreate: (String?) -> Unit,
    onDiscover: () -> Unit,
    onJoin: (DiscoveredRoom, String) -> Unit,
    onCancelConnection: () -> Unit,
    onOfflineNetwork: () -> Unit,
    onStopOfflineNetwork: () -> Unit,
    onEditName: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var roomName by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf<DiscoveredRoom?>(null) }
    var pin by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    val connecting =
        state.room.lifecycle == RoomLifecycleState.CONNECTING ||
            state.room.lifecycle == RoomLifecycleState.JOINING ||
            state.room.lifecycle == RoomLifecycleState.PREPARING

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Listen together",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Create a room or search this Wi-Fi when you want to join someone nearby.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { showCreate = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !connecting,
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create room")
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDiscover,
                            modifier = Modifier.weight(1f),
                            enabled =
                                !connecting &&
                                    state.room.lifecycle != RoomLifecycleState.DISCOVERING,
                        ) {
                            if (state.room.lifecycle == RoomLifecycleState.DISCOVERING) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, null)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.room.lifecycle == RoomLifecycleState.DISCOVERING) {
                                    "Searching…"
                                } else {
                                    "Find rooms"
                                }
                            )
                        }
                        Box {
                            IconButton(onClick = { more = true }) {
                                Icon(Icons.Default.MoreVert, "More")
                            }
                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                DropdownMenuItem(
                                    text = { Text("Change your name") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = {
                                        more = false
                                        onEditName()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.room.hotspot == null) {
                                                "Create offline Wi-Fi"
                                            } else {
                                                "Stop offline Wi-Fi"
                                            }
                                        )
                                    },
                                    onClick = {
                                        more = false
                                        if (state.room.hotspot == null) {
                                            onOfflineNetwork()
                                        } else {
                                            onStopOfflineNetwork()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        state.room.hotspot?.let { hotspot ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Offline Wi-Fi ready", fontWeight = FontWeight.SemiBold)
                        Text(hotspot.ssid)
                        hotspot.passphrase?.let {
                            Text("Password: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (connecting) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Column(Modifier.weight(1f)) {
                                Text("Joining room", fontWeight = FontWeight.SemiBold)
                                Text(
                                    state.room.statusMessage ?: "Connecting…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(
                            onClick = onCancelConnection,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        } else if (state.room.discoveredRooms.isNotEmpty()) {
            item { SectionTitle("Nearby rooms") }
            items(
                state.room.discoveredRooms,
                key = { "${it.roomId}:${it.hostAddress}:${it.port}" },
            ) { room ->
                ListItem(
                    headlineContent = {
                        Text(room.roomName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text("Tap to join") },
                    leadingContent = { Icon(Icons.Default.Groups, null) },
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            joining = room
                            pin = ""
                        },
                    trailingContent = {
                        Text(
                            "Join",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
                HorizontalDivider()
            }
        } else if (state.room.lifecycle == RoomLifecycleState.DISCOVERING) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Looking for nearby rooms…")
                }
            }
        } else if (state.room.discoveryCompleted) {
            item {
                EmptyState(
                    title = "No rooms found",
                    text =
                        "Make sure everyone is on the same Wi-Fi, then tap Find rooms to search again.",
                    icon = Icons.Default.SearchOff,
                )
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create room") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = { roomName = it.take(40) },
                        label = { Text("Room name (optional)") },
                        singleLine = true,
                    )
                    Text(
                        stringResource(R.string.room_create_code_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreate = false
                        onCreate(roomName.trim().takeIf(String::isNotEmpty))
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }
    joining?.let { room ->
        AlertDialog(
            onDismissRequest = { joining = null },
            title = { Text("Join ${room.roomName}") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.room_join_code_field)) },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                if (pin.length == 4) {
                                    joining = null
                                    onJoin(room, pin)
                                }
                            }
                        ),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        joining = null
                        onJoin(room, pin)
                    },
                    enabled = pin.length == 4,
                ) {
                    Text("Join")
                }
            },
            dismissButton = { TextButton(onClick = { joining = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun RoomScreen(
    state: MainUiState,
    playbackPositionState: State<Long>,
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
    onAddAllMusicToRoom: () -> Unit,
    onAddPlaylistToRoom: (String) -> Unit,
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
) {
    val snapshot = state.room.snapshot
    if (snapshot == null) {
        EmptyState("No active room", "Create a room or join someone nearby.", Icons.Default.Groups)
        return
    }
    val displayedQueueItemId = state.room.localPlaybackQueueItemId ?: snapshot.playback.queueItemId
    val nowPlaying = displayedQueueItemId?.let { id ->
        snapshot.queue.firstOrNull { it.queueItemId == id }
    }
    val hasSeekableDuration = (nowPlaying?.track?.durationMs ?: 0L) > 0L
    val duration = nowPlaying?.track?.durationMs?.coerceAtLeast(1L) ?: 1L
    val activeTransfers =
        state.room.transfers.values
            .filter {
                it.state == MemberTrackState.RECEIVING ||
                    it.state == MemberTrackState.VERIFYING ||
                    it.state == MemberTrackState.CANCELLED ||
                    it.state == MemberTrackState.FAILED
            }
            .sortedBy { it.trackId.value }
    var showOptions by remember { mutableStateOf(false) }
    var showListeners by remember { mutableStateOf(false) }
    var showAddFromLibrary by remember { mutableStateOf(false) }
    var roomMenu by remember { mutableStateOf(false) }
    var saveQueueDialog by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmClearQueue by remember { mutableStateOf(false) }
    val localRoomCode = state.room.localRoomPin
    var roomCodeVisible by rememberSaveable(snapshot.roomId) { mutableStateOf(false) }
    var draggedQueueIndex by remember { mutableStateOf<Int?>(null) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragPointerCenterPx by remember { mutableStateOf<Float?>(null) }
    var dragAutoScrollPx by remember { mutableFloatStateOf(0f) }
    var queuePlaylistName by
        remember(snapshot.roomName) { mutableStateOf("${snapshot.roomName} queue") }
    var queueSearchQuery by rememberSaveable(snapshot.roomId) { mutableStateOf("") }
    var appliedQueueSearchQuery by remember(snapshot.roomId) { mutableStateOf("") }
    val queueSearchIndex = remember(snapshot.queue) { QueueSearchIndex(snapshot.queue) }
    val queueSearchResults =
        remember(queueSearchIndex, appliedQueueSearchQuery) {
            queueSearchIndex.search(appliedQueueSearchQuery)
        }
    val queueSearchActive = queueSearchQuery.isNotBlank()
    val roomListState = rememberLazyListState()
    val queueIndexByLazyKey =
        remember(snapshot.queue) {
            snapshot.queue
                .mapIndexed { index, item -> "queue:${item.queueItemId.value}" to index }
                .toMap()
        }
    val dragEdgePx = with(LocalDensity.current) { 72.dp.toPx() }
    val maxDragScrollPx = with(LocalDensity.current) { 28.dp.toPx() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) roomCodeVisible = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun recalculateQueueDrag() {
        val origin = draggedQueueIndex ?: return
        val pointerCenter = dragPointerCenterPx ?: return
        val layout = roomListState.layoutInfo
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
        val visibleItem = roomListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
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
    val compactPlayerVisible by remember {
        derivedStateOf {
            roomListState.firstVisibleItemIndex > 1 &&
                roomListState.layoutInfo.visibleItemsInfo.none { it.key == "full-player" }
        }
    }
    val transportStatus = state.room.transportStatus
    val transportControls =
        remember(
            nowPlaying?.queueItemId,
            hasSeekableDuration,
            state.room.localIsPlaying,
            transportStatus,
        ) {
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = nowPlaying != null,
                hasSeekableDuration = hasSeekableDuration,
                localIsPlaying = state.room.localIsPlaying,
                status = transportStatus,
            )
        }
    val displayedPlaying = transportControls.displayedPlaying
    val requestPlayPause = {
        if (transportControls.canPlayPause) {
            if (displayedPlaying) onPause() else onPlay()
        }
    }

    LaunchedEffect(snapshot.roomId, snapshot.queue.isEmpty()) {
        if (snapshot.queue.isEmpty()) {
            queueSearchQuery = ""
            appliedQueueSearchQuery = ""
        }
    }
    LaunchedEffect(queueSearchQuery) {
        if (queueSearchQuery.isBlank()) {
            appliedQueueSearchQuery = ""
        } else {
            delay(150)
            appliedQueueSearchQuery = queueSearchQuery
        }
    }
    LaunchedEffect(queueSearchActive) {
        if (queueSearchActive) resetQueueDrag()
    }

    LaunchedEffect(draggedQueueIndex) {
        while (draggedQueueIndex != null) {
            withFrameNanos {}
            val requested = dragAutoScrollPx
            if (abs(requested) < 0.5f) continue
            val consumed = roomListState.scrollBy(requested)
            if (abs(consumed) < 0.5f) {
                dragAutoScrollPx = 0f
                continue
            }
            // Scrolling moves the row's layout position opposite the content direction. Preserve
            // the dragged row under the pointer while the list advances beneath it.
            dragOffsetPx += consumed
            recalculateQueueDrag()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = roomListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "room-header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${snapshot.members.count { it.connected }} listening",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { roomMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Room actions")
                        }
                        DropdownMenu(expanded = roomMenu, onDismissRequest = { roomMenu = false }) {
                            if (localRoomCode != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.room_code_action)) },
                                    onClick = {
                                        roomMenu = false
                                        roomCodeVisible = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Save queue as playlist") },
                                enabled = snapshot.queue.isNotEmpty(),
                                onClick = {
                                    roomMenu = false
                                    saveQueueDialog = true
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
                                text = {
                                    Text("Listeners (${snapshot.members.count { it.connected }})")
                                },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                onClick = {
                                    roomMenu = false
                                    showListeners = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Import M3U playlist") },
                                leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                                onClick = {
                                    roomMenu = false
                                    onImportM3u()
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
            if (nowPlaying != null) {
                item(key = "full-player") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                nowPlaying.track.displayTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                nowPlaying.track.artist?.takeIf(String::isNotBlank) ?: " ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            RoomSeekSlider(
                                playbackPositionState = playbackPositionState,
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
                                    active =
                                        transportStatus?.active == true &&
                                            transportStatus.action == TransportAction.PREVIOUS,
                                    enabled = transportControls.canNavigate,
                                    onClick = onPrevious,
                                    contentDescription = "Previous",
                                ) {
                                    Icon(Icons.Default.SkipPrevious, null, Modifier.size(32.dp))
                                }
                                TransportPlayPauseButton(
                                    isPlaying = displayedPlaying,
                                    pending = transportControls.playPausePending,
                                    enabled = transportControls.canPlayPause,
                                    onClick = requestPlayPause,
                                )
                                TransportControlButton(
                                    active =
                                        transportStatus?.active == true &&
                                            transportStatus.action == TransportAction.NEXT,
                                    enabled = transportControls.canNavigate,
                                    onClick = onNext,
                                    contentDescription = "Next",
                                ) {
                                    Icon(Icons.Default.SkipNext, null, Modifier.size(32.dp))
                                }
                            }
                            TransportStatusLine(
                                status = transportStatus,
                                queue = snapshot.queue,
                            )
                        }
                    }
                }
            }
            item(key = "queue-controls") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Queue")
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (queueSearchActive) {
                                "${queueSearchResults.size} of ${snapshot.queue.size}"
                            } else {
                                "${snapshot.queue.size} / 1,000"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { confirmClearQueue = true },
                            enabled = snapshot.queue.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear")
                        }
                        if (snapshot.shuffleEnabled) {
                            FilledTonalIconButton(
                                onClick = onShuffle,
                                enabled = snapshot.queue.size > 1,
                            ) {
                                Icon(Icons.Default.Shuffle, "Turn shuffle off")
                            }
                        } else {
                            IconButton(
                                onClick = onShuffle,
                                enabled = snapshot.queue.size > 1,
                            ) {
                                Icon(Icons.Default.Shuffle, "Turn shuffle on")
                            }
                        }
                        val repeatDescription =
                            when (snapshot.repeatMode) {
                                RepeatMode.OFF -> "Repeat is off"
                                RepeatMode.ALL -> "Repeat queue"
                                RepeatMode.ONE -> "Repeat current song"
                            }
                        if (snapshot.repeatMode == RepeatMode.OFF) {
                            IconButton(
                                onClick = { onRepeat(RepeatMode.ALL) },
                                enabled = snapshot.queue.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Repeat, repeatDescription)
                            }
                        } else {
                            FilledTonalIconButton(
                                onClick = { onRepeat(snapshot.repeatMode.next()) },
                                enabled = snapshot.queue.isNotEmpty(),
                            ) {
                                Icon(
                                    if (snapshot.repeatMode == RepeatMode.ONE) {
                                        Icons.Default.RepeatOne
                                    } else {
                                        Icons.Default.Repeat
                                    },
                                    repeatDescription,
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { showAddFromLibrary = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add from library")
                        }
                        OutlinedButton(onClick = onChooseFiles) {
                            Icon(Icons.Default.AudioFile, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Files")
                        }
                    }
                    if (snapshot.queue.isNotEmpty()) {
                        CompactQueueSearchField(
                            value = queueSearchQuery,
                            onValueChange = { queueSearchQuery = it.take(120) },
                            onClear = { queueSearchQuery = "" },
                        )
                    }
                }
            }
            state.room.statusMessage
                ?.takeIf { it.isNotBlank() && it != "Ready" }
                ?.let { status ->
                    item(key = "room-status") {
                        if (state.room.lifecycle == RoomLifecycleState.RECONNECTING) {
                            Card(
                                modifier =
                                    Modifier.fillMaxWidth().semantics {
                                        liveRegion = LiveRegionMode.Polite
                                    }
                            ) {
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(
                                        Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Connection interrupted",
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            status,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(onClick = { confirmLeave = true }) {
                                        Text("Leave")
                                    }
                                }
                            }
                        } else {
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            if (activeTransfers.isNotEmpty()) {
                item(key = "transfers") {
                    TransferStatusCard(
                        transfers = activeTransfers,
                        titles =
                            snapshot.queue.associate { it.track.trackId to it.track.displayTitle },
                    )
                }
            }
            if (snapshot.queue.isEmpty()) {
                item(key = "empty-queue") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                null,
                                modifier = Modifier.size(42.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Queue is empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Add a playlist or choose audio files to start listening together.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else if (queueSearchActive && queueSearchResults.isEmpty()) {
                item(key = "empty-queue-search") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "No songs found in the queue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(onClick = { queueSearchQuery = "" }) {
                                Text("Clear search")
                            }
                        }
                    }
                }
            } else {
                items(queueSearchResults, key = { "queue:${it.item.queueItemId.value}" }) { match ->
                    val item = match.item
                    val index = match.originalIndex
                    QueueRow(
                        index = index,
                        lastIndex = snapshot.queue.lastIndex,
                        track = item.track,
                        current = item.queueItemId == displayedQueueItemId,
                        playing =
                            item.queueItemId == state.room.localPlaybackQueueItemId &&
                                state.room.localIsPlaying,
                        temporary = item.track.trackId in state.temporaryTrackIds,
                        canReorder = !snapshot.shuffleEnabled && !queueSearchActive,
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
                        playEnabled = transportControls.canNavigate,
                        onPlay = { onPlayQueueItem(item.queueItemId) },
                        onMoveNext = { onMoveQueueItemNext(item.queueItemId) },
                        onRemove = { onRemoveQueueItem(item.queueItemId) },
                        onKeep = { onKeepTrack(item.track.trackId) },
                    )
                    HorizontalDivider(Modifier.padding(start = 48.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = compactPlayerVisible && nowPlaying != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).zIndex(2f),
        ) {
            CompactRoomPlayer(
                track = nowPlaying?.track,
                queue = snapshot.queue,
                isPlaying = displayedPlaying,
                transportStatus = transportStatus,
                onPlayPause = requestPlayPause,
                playPauseEnabled = transportControls.canPlayPause,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    if (showAddFromLibrary) {
        AlertDialog(
            onDismissRequest = { showAddFromLibrary = false },
            icon = { Icon(Icons.Default.LibraryMusic, null) },
            title = { Text("Add from library") },
            text = {
                if (state.libraryTotalCount == 0) {
                    Text("Your library is empty. Add audio files to this phone first.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        item(key = "all-music") {
                            ListItem(
                                headlineContent = { Text("All music") },
                                supportingContent = {
                                    Text(
                                        "${state.libraryTotalCount} ${if (state.libraryTotalCount == 1) "song" else "songs"}"
                                    )
                                },
                                leadingContent = { Icon(Icons.Default.LibraryMusic, null) },
                                modifier =
                                    Modifier.clickable {
                                        showAddFromLibrary = false
                                        onAddAllMusicToRoom()
                                    },
                            )
                        }
                        items(state.playlists, key = { it.playlistId }) { playlist ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        playlist.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        "${playlist.trackCount} ${if (playlist.trackCount == 1) "song" else "songs"}"
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null)
                                },
                                modifier =
                                    Modifier.clickable {
                                        showAddFromLibrary = false
                                        onAddPlaylistToRoom(playlist.playlistId)
                                    },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (state.libraryTotalCount == 0) {
                    Button(
                        onClick = {
                            showAddFromLibrary = false
                            onChooseFiles()
                        }
                    ) {
                        Text("Add files")
                    }
                } else {
                    TextButton(onClick = { showAddFromLibrary = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (saveQueueDialog) {
        AlertDialog(
            onDismissRequest = { saveQueueDialog = false },
            title = { Text("Save queue") },
            text = {
                OutlinedTextField(
                    value = queuePlaylistName,
                    onValueChange = { queuePlaylistName = it.take(60) },
                    label = { Text("Playlist name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveQueue(queuePlaylistName.trim())
                        saveQueueDialog = false
                    },
                    enabled = queuePlaylistName.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { saveQueueDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showListeners) {
        AlertDialog(
            onDismissRequest = { showListeners = false },
            icon = { Icon(Icons.Default.Person, null) },
            title = { Text("Listeners") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(snapshot.members, key = { it.peerId.value }) { member ->
                        ListItem(
                            headlineContent = { Text(member.displayName) },
                            supportingContent = {
                                Text(if (member.connected) "Listening" else "Offline")
                            },
                            leadingContent = { Icon(Icons.Default.Person, null) },
                            trailingContent = {
                                Icon(
                                    Icons.Default.Circle,
                                    null,
                                    Modifier.size(9.dp),
                                    tint =
                                        if (member.connected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                )
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showListeners = false }) { Text("Done") }
            },
        )
    }

    if (showOptions) {
        var options by remember(snapshot.options) { mutableStateOf(snapshot.options) }
        var retention by remember(state.retentionPolicy) { mutableStateOf(state.retentionPolicy) }
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text("Room settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SwitchRow("Wait for everyone before each song", options.waitAtTrackBoundary) {
                        options = options.copy(waitAtTrackBoundary = it)
                    }
                    Text(
                        "Useful when the Wi-Fi connection is slow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    Text(
                        "New music shared with this phone",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = retention == RetentionPolicy.TEMPORARY_24_HOURS,
                            onClick = { retention = RetentionPolicy.TEMPORARY_24_HOURS },
                            label = { Text("Temporary") },
                        )
                        FilterChip(
                            selected = retention == RetentionPolicy.KEEP_IN_LIBRARY,
                            onClick = { retention = RetentionPolicy.KEEP_IN_LIBRARY },
                            label = { Text("Keep") },
                        )
                    }
                    Text(
                        "Temporary copies are removed after 24 hours. Keep individual songs from their queue menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateOptions(options)
                        onSetRetentionPolicy(retention)
                        showOptions = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { showOptions = false }) { Text("Cancel") } },
        )
    }
    if (confirmClearQueue) {
        AlertDialog(
            onDismissRequest = { confirmClearQueue = false },
            icon = { Icon(Icons.Default.DeleteOutline, null) },
            title = { Text("Clear the entire queue?") },
            text = {
                Text("Playback will stop for everyone and all queued songs will be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearQueue = false
                        onClearQueue()
                    }
                ) {
                    Text("Clear queue")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearQueue = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this room?") },
            text = { Text("Playback and transfers on this phone will stop.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmLeave = false
                        onLeave()
                    }
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Stay") }
            },
        )
    }
    if (roomCodeVisible && localRoomCode != null) {
        RoomCodeDialog(
            roomCode = localRoomCode,
            onDismiss = { roomCodeVisible = false },
        )
    }
}
