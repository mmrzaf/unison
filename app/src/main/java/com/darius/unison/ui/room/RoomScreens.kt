package com.darius.unison.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.darius.unison.app.unisonContainer
import com.darius.unison.library.LibrarySort
import com.darius.unison.library.PlaylistDetail
import com.darius.unison.model.AppCommand
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private enum class TransportRequest { PLAY, PAUSE, SEEK, NEXT, PREVIOUS, PLAY_ITEM }

@Composable
internal fun RoomLobbyScreen(
    state: MainUiState,
    onCreate: (String?) -> Unit,
    onDiscover: () -> Unit,
    onJoin: (DiscoveredRoom, String) -> Unit,
    onOfflineNetwork: () -> Unit,
    onStopOfflineNetwork: () -> Unit,
    onEditName: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var roomName by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf<DiscoveredRoom?>(null) }
    var pin by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    val connecting = state.room.lifecycle == RoomLifecycleState.CONNECTING ||
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDiscover,
                            modifier = Modifier.weight(1f),
                            enabled = !connecting &&
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
                            IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                DropdownMenuItem(
                                    text = { Text("Change your name") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { more = false; onEditName() },
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
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Offline Wi-Fi ready", fontWeight = FontWeight.SemiBold)
                        Text(hotspot.ssid)
                        hotspot.passphrase?.let { Text("Password: $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        if (connecting) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(state.room.statusMessage ?: "Connecting to room…")
                }
            }
        } else if (state.room.discoveredRooms.isNotEmpty()) {
            item { SectionTitle("Nearby rooms") }
            items(state.room.discoveredRooms, key = { "${it.roomId}:${it.hostAddress}:${it.port}" }) { room ->
                ListItem(
                    headlineContent = { Text(room.roomName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("Tap to join") },
                    leadingContent = { Icon(Icons.Default.Groups, null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { joining = room; pin = "" },
                    trailingContent = {
                        Text(
                            "Join",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                )
                HorizontalDivider()
            }
        } else if (state.room.lifecycle == RoomLifecycleState.DISCOVERING) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                    text = "Make sure everyone is on the same Wi-Fi, then tap Find rooms to search again.",
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
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it.take(40) },
                    label = { Text("Room name (optional)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = { showCreate = false; onCreate(roomName.trim().takeIf(String::isNotEmpty)) }) {
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
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text("6-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = { joining = null; onJoin(room, pin) }, enabled = pin.length == 6) { Text("Join") }
            },
            dismissButton = { TextButton(onClick = { joining = null }) { Text("Cancel") } },
        )
    }
}
@Composable
internal fun RoomScreen(
    state: MainUiState,
    playbackPositionMs: Long,
    joinLink: String?,
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
    onKeepTrack: (TrackId) -> Unit,
    onUpdateOptions: (RoomOptions) -> Unit,
    onSetRetentionPolicy: (RetentionPolicy) -> Unit,
    onSaveQueue: (String) -> Unit,
    onClearPlayed: () -> Unit,
    onLeave: () -> Unit,
) {
    val snapshot = state.room.snapshot
    if (snapshot == null) {
        EmptyState("No active room", "Create a room or join someone nearby.", Icons.Default.Groups)
        return
    }
    val displayedQueueItemId = state.room.localPlaybackQueueItemId ?: snapshot.playback.queueItemId
    val nowPlaying = displayedQueueItemId?.let { id -> snapshot.queue.firstOrNull { it.queueItemId == id } }
    val hasSeekableDuration = (nowPlaying?.track?.durationMs ?: 0L) > 0L
    val duration = nowPlaying?.track?.durationMs?.coerceAtLeast(1L) ?: 1L
    val activeTransfers = state.room.transfers.values
        .filter {
            it.state == MemberTrackState.RECEIVING ||
                it.state == MemberTrackState.VERIFYING ||
                it.state == MemberTrackState.CANCELLED ||
                it.state == MemberTrackState.FAILED
        }
        .sortedBy { it.trackId.value }
    var seekPreview by remember { mutableFloatStateOf(playbackPositionMs.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showListeners by remember { mutableStateOf(false) }
    var showAddFromLibrary by remember { mutableStateOf(false) }
    var roomMenu by remember { mutableStateOf(false) }
    var saveQueueDialog by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var transportRequest by remember { mutableStateOf<TransportRequest?>(null) }
    var pendingSeekPositionMs by remember { mutableStateOf<Long?>(null) }
    var transportStartItem by remember { mutableStateOf(state.room.localPlaybackQueueItemId) }
    var transportStartSeekRevision by remember { mutableLongStateOf(state.room.localSeekRevision) }
    var requestedQueueItemId by remember { mutableStateOf<QueueItemId?>(null) }
    var previousRestartsCurrent by remember { mutableStateOf(false) }
    var draggedQueueIndex by remember { mutableStateOf<Int?>(null) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var queuePlaylistName by remember(snapshot.roomName) { mutableStateOf("${snapshot.roomName} queue") }
    val roomListState = rememberLazyListState()
    val compactPlayerVisible by remember {
        derivedStateOf {
            roomListState.firstVisibleItemIndex > 1 &&
                roomListState.layoutInfo.visibleItemsInfo.none { it.key == "full-player" }
        }
    }
    val requestPlayPause = {
        if (transportRequest == null && nowPlaying != null) {
            if (state.room.localIsPlaying) {
                transportRequest = TransportRequest.PAUSE
                onPause()
            } else {
                transportRequest = TransportRequest.PLAY
                onPlay()
            }
        }
    }

    LaunchedEffect(playbackPositionMs, duration, dragging, pendingSeekPositionMs) {
        if (!dragging && pendingSeekPositionMs == null) {
            seekPreview = playbackPositionMs.coerceIn(0, duration).toFloat()
        }
    }
    LaunchedEffect(state.room.localSeekRevision) {
        if (pendingSeekPositionMs != null &&
            state.room.localSeekRevision > transportStartSeekRevision
        ) {
            pendingSeekPositionMs = null
            if (transportRequest == TransportRequest.SEEK) transportRequest = null
            seekPreview = playbackPositionMs.coerceIn(0, duration).toFloat()
        }
    }
    LaunchedEffect(pendingSeekPositionMs) {
        if (pendingSeekPositionMs != null) {
            delay(6_000)
            pendingSeekPositionMs = null
            if (transportRequest == TransportRequest.SEEK) transportRequest = null
            seekPreview = playbackPositionMs.coerceIn(0, duration).toFloat()
        }
    }
    LaunchedEffect(state.room.errorMessage) {
        if (!state.room.errorMessage.isNullOrBlank()) {
            pendingSeekPositionMs = null
            transportRequest = null
            requestedQueueItemId = null
        }
    }
    LaunchedEffect(transportRequest) {
        if (transportRequest != null) {
            delay(6_000)
            transportRequest = null
            requestedQueueItemId = null
        }
    }
    LaunchedEffect(
        state.room.localIsPlaying,
        state.room.localPlaybackQueueItemId,
        state.room.localSeekRevision,
        playbackPositionMs,
    ) {
        val completed = when (transportRequest) {
            TransportRequest.PLAY -> state.room.localIsPlaying
            TransportRequest.PAUSE -> !state.room.localIsPlaying
            TransportRequest.SEEK -> false
            TransportRequest.NEXT,
                -> state.room.localPlaybackQueueItemId != transportStartItem

            TransportRequest.PREVIOUS -> {
                state.room.localPlaybackQueueItemId != transportStartItem ||
                    (previousRestartsCurrent && playbackPositionMs <= 1_000L)
            }

            TransportRequest.PLAY_ITEM ->
                state.room.localPlaybackQueueItemId == requestedQueueItemId &&
                    state.room.localIsPlaying &&
                    state.room.localSeekRevision > transportStartSeekRevision

            null -> false
        }
        if (completed) {
            transportRequest = null
            requestedQueueItemId = null
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
                    IconButton(onClick = { showQr = true }, enabled = joinLink != null) {
                        Icon(
                            Icons.Default.QrCode2,
                            "Invite"
                        )
                    }
                    Box {
                        IconButton(onClick = { roomMenu = true }) { Icon(Icons.Default.MoreVert, "Room actions") }
                        DropdownMenu(expanded = roomMenu, onDismissRequest = { roomMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Save queue as playlist") },
                                enabled = snapshot.queue.isNotEmpty(),
                                onClick = { roomMenu = false; saveQueueDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear played songs") },
                                enabled = snapshot.queue.indexOfFirst { it.queueItemId == snapshot.playback.queueItemId } > 0,
                                onClick = { roomMenu = false; onClearPlayed() },
                            )
                            DropdownMenuItem(
                                text = { Text("Listeners (${snapshot.members.count { it.connected }})") },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                onClick = { roomMenu = false; showListeners = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Import M3U playlist") },
                                leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                                onClick = { roomMenu = false; onImportM3u() },
                            )
                            DropdownMenuItem(
                                text = { Text("Room settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { roomMenu = false; showOptions = true },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Leave room") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                                onClick = { roomMenu = false; confirmLeave = true },
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
                            TrackArtwork(
                                track = nowPlaying.track,
                                size = 148.dp,
                                reloadKey = state.room.transfers[nowPlaying.track.trackId]?.state == MemberTrackState.READY,
                            )
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
                            Slider(
                                value = seekPreview.coerceIn(0f, duration.toFloat()),
                                onValueChange = { dragging = true; seekPreview = it },
                                onValueChangeFinished = {
                                    dragging = false
                                    pendingSeekPositionMs = seekPreview.toLong()
                                    transportStartSeekRevision = state.room.localSeekRevision
                                    transportRequest = TransportRequest.SEEK
                                    onSeek(pendingSeekPositionMs ?: 0L)
                                },
                                valueRange = 0f..duration.toFloat(),
                                enabled = hasSeekableDuration,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatDuration(seekPreview.toLong()), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    if (hasSeekableDuration) formatDuration(duration) else "—:—",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                IconButton(
                                    onClick = {
                                        previousRestartsCurrent = playbackPositionMs > 4_000L
                                        transportStartItem = state.room.localPlaybackQueueItemId
                                        transportRequest = TransportRequest.PREVIOUS
                                        onPrevious()
                                    },
                                    enabled = transportRequest == null,
                                ) {
                                    Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(32.dp))
                                }
                                FilledIconButton(
                                    onClick = requestPlayPause,
                                    modifier = Modifier.size(58.dp),
                                    enabled = transportRequest == null,
                                ) {
                                    if (transportRequest != null) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(26.dp),
                                            strokeWidth = 2.5.dp,
                                        )
                                    } else {
                                        Icon(
                                            if (state.room.localIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            if (state.room.localIsPlaying) "Pause" else "Play",
                                            Modifier.size(32.dp),
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        transportStartItem = state.room.localPlaybackQueueItemId
                                        transportRequest = TransportRequest.NEXT
                                        onNext()
                                    },
                                    enabled = transportRequest == null,
                                ) {
                                    Icon(Icons.Default.SkipNext, "Next", Modifier.size(32.dp))
                                }
                            }
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
                            "${snapshot.queue.size} / 1,000",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(
                            onClick = onShuffle,
                            enabled = snapshot.queue.size > 1,
                        ) {
                            Icon(Icons.Default.Shuffle, "Shuffle queue")
                        }
                        val repeatDescription = when (snapshot.repeatMode) {
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAddFromLibrary = true }, modifier = Modifier.weight(1f)) {
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
                }
            }
            state.room.statusMessage
                ?.takeIf { it.isNotBlank() && it != "Ready" }
                ?.let { status ->
                    item(key = "room-status") {
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
            if (activeTransfers.isNotEmpty()) {
                item(key = "transfers") {
                    TransferStatusCard(
                        transfers = activeTransfers,
                        titles = snapshot.queue.associate { it.track.trackId to it.track.displayTitle },
                    )
                }
            }
            if (snapshot.queue.isEmpty()) {
                item(key = "empty-queue") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
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
            } else {
                itemsIndexed(snapshot.queue, key = { _, item -> item.queueItemId.value }) { index, item ->
                    QueueRow(
                        index = index,
                        lastIndex = snapshot.queue.lastIndex,
                        track = item.track,
                        artworkReloadKey = state.room.transfers[item.track.trackId]?.state,
                        current = item.queueItemId == displayedQueueItemId,
                        playing = item.queueItemId == state.room.localPlaybackQueueItemId && state.room.localIsPlaying,
                        temporary = item.track.trackId in state.temporaryTrackIds,
                        canReorder = !snapshot.shuffleEnabled,
                        draggedIndex = draggedQueueIndex,
                        dragTargetIndex = dragTargetIndex,
                        onDragStateChange = { dragged, target ->
                            draggedQueueIndex = dragged
                            dragTargetIndex = target
                        },
                        onMove = { onMoveQueueItem(item.queueItemId, it) },
                        onPlay = {
                            if (transportRequest == null) {
                                requestedQueueItemId = item.queueItemId
                                transportStartSeekRevision = state.room.localSeekRevision
                                transportRequest = TransportRequest.PLAY_ITEM
                                onPlayQueueItem(item.queueItemId)
                            }
                        },
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
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f),
        ) {
            CompactRoomPlayer(
                track = nowPlaying?.track,
                artworkReloadKey = nowPlaying?.track?.trackId?.let { state.room.transfers[it]?.state },
                isPlaying = state.room.localIsPlaying,
                pending = transportRequest != null,
                onPlayPause = requestPlayPause,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                    Text("${state.libraryTotalCount} ${if (state.libraryTotalCount == 1) "song" else "songs"}")
                                },
                                leadingContent = { Icon(Icons.Default.LibraryMusic, null) },
                                modifier = Modifier.clickable {
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
                                    Text("${playlist.trackCount} ${if (playlist.trackCount == 1) "song" else "songs"}")
                                },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                                modifier = Modifier.clickable {
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
                    Button(onClick = {
                        showAddFromLibrary = false
                        onChooseFiles()
                    }) { Text("Add files") }
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
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saveQueueDialog = false }) { Text("Cancel") } },
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
                            supportingContent = { Text(if (member.connected) "Listening" else "Offline") },
                            leadingContent = { Icon(Icons.Default.Person, null) },
                            trailingContent = {
                                Icon(
                                    Icons.Default.Circle,
                                    null,
                                    Modifier.size(9.dp),
                                    tint = if (member.connected) {
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

    if (showQr && joinLink != null) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Invite friends") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncQrCode(joinLink, Modifier.size(252.dp))
                    Text(
                        "PIN ${state.room.localRoomPin ?: "—"}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Scan while connected to the same Wi-Fi.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Done") } },
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
                    Text("New music shared with this phone", style = MaterialTheme.typography.labelLarge)
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
                Button(onClick = {
                    onUpdateOptions(options)
                    onSetRetentionPolicy(retention)
                    showOptions = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showOptions = false }) { Text("Cancel") } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave this room?") },
            text = { Text("Playback and transfers on this phone will stop.") },
            confirmButton = {
                Button(onClick = {
                    confirmLeave = false
                    onLeave()
                }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Stay") }
            },
        )
    }
}

@Composable
internal fun CompactRoomPlayer(
    track: TrackDescriptor?,
    artworkReloadKey: Any?,
    isPlaying: Boolean,
    pending: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (track != null) {
                TrackArtwork(track = track, size = 40.dp, reloadKey = artworkReloadKey)
            } else {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track?.displayTitle ?: "Nothing playing",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.artist?.takeIf(String::isNotBlank)
                        ?: if (isPlaying) "Playing" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = track != null && !pending,
                modifier = Modifier.size(44.dp),
            ) {
                if (pending) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                    )
                }
            }
        }
    }
}

@Composable
internal fun TransferStatusCard(
    transfers: List<TransferProgress>,
    titles: Map<TrackId, String>,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            transfers.take(3).forEach { transfer ->
                val title = titles[transfer.trackId] ?: "Music"
                val status = when (transfer.state) {
                    MemberTrackState.RECEIVING -> "Receiving $title"
                    MemberTrackState.VERIFYING -> "Verifying $title"
                    MemberTrackState.CANCELLED -> "Cancelled $title"
                    MemberTrackState.FAILED -> "Could not receive $title"
                    else -> title
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (transfer.state == MemberTrackState.RECEIVING) {
                            Text(
                                "${(transfer.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (transfer.state == MemberTrackState.CANCELLED || transfer.state == MemberTrackState.FAILED) {
                        Text(
                            "Check the Wi-Fi connection. Unison can try another source.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { transfer.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (transfers.size > 3) {
                Text(
                    "${transfers.size - 3} more transfer${if (transfers.size == 4) "" else "s"} in progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun QueueRow(
    index: Int,
    lastIndex: Int,
    track: TrackDescriptor,
    artworkReloadKey: Any?,
    current: Boolean,
    playing: Boolean,
    temporary: Boolean,
    canReorder: Boolean,
    draggedIndex: Int?,
    dragTargetIndex: Int?,
    onDragStateChange: (Int?, Int?) -> Unit,
    onMove: (Int) -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val estimatedRowHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
    val displacedOffsetPx = when {
        draggedIndex == null || dragTargetIndex == null || draggedIndex == index -> 0f
        draggedIndex < dragTargetIndex && index in (draggedIndex + 1)..dragTargetIndex ->
            -estimatedRowHeightPx

        draggedIndex > dragTargetIndex && index in dragTargetIndex until draggedIndex ->
            estimatedRowHeightPx

        else -> 0f
    }
    val animatedDisplacementPx by animateFloatAsState(
        targetValue = displacedOffsetPx,
        label = "Queue drop position",
    )
    val finishDrag = {
        val targetIndex = (index + (dragOffsetPx / estimatedRowHeightPx).roundToInt())
            .coerceIn(0, lastIndex)
        dragOffsetPx = 0f
        onDragStateChange(null, null)
        if (targetIndex != index) onMove(targetIndex)
    }
    ListItem(
        modifier = Modifier
            .graphicsLayer {
                translationY = if (draggedIndex == index) dragOffsetPx else animatedDisplacementPx
                shadowElevation = if (dragOffsetPx == 0f) 0f else 8.dp.toPx()
            }
            .clickable(enabled = draggedIndex == null, onClick = onPlay),
        headlineContent = { Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(
                    when {
                        playing -> "Playing"
                        current -> "Paused"
                        else -> track.artist?.takeIf(String::isNotBlank)
                    },
                    "Temporary".takeIf { temporary },
                    formatDuration(track.durationMs),
                ).joinToString(" • "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            TrackArtwork(track = track, size = 40.dp, reloadKey = artworkReloadKey)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canReorder) {
                    Icon(
                        Icons.Default.DragHandle,
                        "Hold and drag to reorder",
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                            .pointerInput(index, lastIndex) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragOffsetPx = 0f
                                        onDragStateChange(index, index)
                                    },
                                    onDragCancel = {
                                        dragOffsetPx = 0f
                                        onDragStateChange(null, null)
                                    },
                                    onDragEnd = finishDrag,
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetPx = (dragOffsetPx + dragAmount.y).coerceIn(
                                            minimumValue = -index * estimatedRowHeightPx,
                                            maximumValue = (lastIndex - index) * estimatedRowHeightPx,
                                        )
                                        val targetIndex = (
                                            index + (dragOffsetPx / estimatedRowHeightPx).roundToInt()
                                            ).coerceIn(0, lastIndex)
                                        onDragStateChange(index, targetIndex)
                                    },
                                )
                            },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Queue actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (temporary) {
                            DropdownMenuItem(
                                text = { Text("Keep on this phone") },
                                onClick = { menu = false; onKeep() },
                            )
                        }
                        if (canReorder) {
                            DropdownMenuItem(
                                text = { Text("Move up") },
                                enabled = index > 0,
                                onClick = { menu = false; onMove(index - 1) },
                            )
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                enabled = index < lastIndex,
                                onClick = { menu = false; onMove(index + 1) },
                            )
                        }
                        DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { menu = false; onRemove() })
                    }
                }
            }
        },
    )
}
