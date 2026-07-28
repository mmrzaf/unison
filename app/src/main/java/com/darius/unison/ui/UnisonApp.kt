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

private enum class Destination(val label: String) { LIBRARY("Library"), ROOM("Room") }
private enum class TransportRequest { PLAY, PAUSE, SEEK, NEXT, PREVIOUS, PLAY_ITEM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnisonApp(viewModel: MainViewModel) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val pickerQuery by viewModel.pickerQuery.collectAsStateWithLifecycle()
    val libraryTracks = viewModel.libraryTracks.collectAsLazyPagingItems()
    val pickerTracks = viewModel.pickerTracks.collectAsLazyPagingItems()
    var destination by rememberSaveable { mutableStateOf(Destination.LIBRARY) }
    var allMusicOpen by rememberSaveable { mutableStateOf(false) }
    var lastOpenedRoomId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNameEdit by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var importToRoom by rememberSaveable { mutableStateOf(false) }
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importMusic(uris, importToRoom)
    }
    val m3uLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importM3u(it, importToRoom) }
    }
    val m3uFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::resolvePendingM3u)
    }
    var exportPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        val id = exportPlaylistId
        if (uri != null && id != null) viewModel.exportPlaylist(id, uri)
        exportPlaylistId = null
    }
    val startPlaylistExport: (String, String) -> Unit = { id, name ->
        exportPlaylistId = id
        exportLauncher.launch("${name.safeFileName()}.m3u8")
    }

    var startHotspotAfterPermission by rememberSaveable { mutableStateOf(false) }
    val hotspotPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it } && startHotspotAfterPermission) {
            viewModel.command(AppCommand.CreateOfflineNetwork)
        } else if (startHotspotAfterPermission) {
            viewModel.showMessage("Nearby Wi-Fi access is needed to create an offline network")
        }
        startHotspotAfterPermission = false
    }

    LaunchedEffect(ui.message) {
        val value = ui.message
        if (!value.isNullOrBlank()) {
            snackbar.showSnackbar(value)
            viewModel.clearMessage(value)
        }
    }
    LaunchedEffect(ui.room.errorMessage) {
        val value = ui.room.errorMessage
        if (!value.isNullOrBlank()) {
            snackbar.showSnackbar(value)
            viewModel.clearRoomError(value)
        }
    }
    val activeRoomId = ui.room.snapshot?.roomId
    LaunchedEffect(activeRoomId) {
        if (activeRoomId == null) {
            lastOpenedRoomId = null
        } else if (activeRoomId != lastOpenedRoomId) {
            lastOpenedRoomId = activeRoomId
            destination = Destination.ROOM
        }
    }
    LaunchedEffect(destination, activeRoomId, ui.room.lifecycle) {
        if (destination != Destination.ROOM && ui.room.lifecycle == RoomLifecycleState.DISCOVERING) {
            viewModel.command(AppCommand.StopDiscovery)
        }
        if (destination != Destination.LIBRARY) allMusicOpen = false
    }

    if (!ui.settingsLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (!ui.onboardingComplete || showNameEdit) {
        NameDialog(
            initialName = ui.room.localIdentity?.displayName.orEmpty(),
            dismissible = ui.onboardingComplete,
            onDismiss = { showNameEdit = false },
            onSave = { name ->
                viewModel.saveName(name)
                showNameEdit = false
            },
        )
    }

    BackHandler(enabled = ui.selectedPlaylist != null) { viewModel.closePlaylist() }
    BackHandler(enabled = ui.selectedPlaylist == null && destination == Destination.LIBRARY && allMusicOpen) {
        allMusicOpen = false
    }
    ui.pendingShare?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.resolvePendingShare(null) },
            title = { Text("Add shared music") },
            text = { Text("Choose where to add ${if (pending.isM3u) "this playlist" else "the selected music"}.") },
            confirmButton = {
                Button(onClick = { viewModel.resolvePendingShare(ShareDestination.ROOM) }) { Text("Room") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.resolvePendingShare(ShareDestination.LIBRARY) }) {
                        Text("Library")
                    }
                    if (!pending.isM3u) {
                        TextButton(onClick = { viewModel.resolvePendingShare(ShareDestination.BOTH) }) {
                            Text("Both")
                        }
                    }
                }
            },
        )
    }

    ui.pendingM3uResolution?.let { pending ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.FolderOpen, null) },
            title = { Text("Find playlist music") },
            text = {
                Text("${pending.unresolvedCount} ${if (pending.unresolvedCount == 1) "song needs" else "songs need"} their music folder.")
            },
            confirmButton = { Button(onClick = { m3uFolderLauncher.launch(null) }) { Text("Choose folder") } },
            dismissButton = {
                TextButton(onClick = viewModel::finishPendingM3uWithoutFolder) { Text("Skip missing") }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        ui.selectedPlaylist?.name ?: when {
                            destination == Destination.ROOM -> ui.room.snapshot?.roomName ?: "Room"
                            destination == Destination.LIBRARY && allMusicOpen -> "All music"
                            destination == Destination.LIBRARY -> "Library"
                            else -> "Unison"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (ui.selectedPlaylist != null || (destination == Destination.LIBRARY && allMusicOpen)) {
                        IconButton(onClick = {
                            if (ui.selectedPlaylist != null) viewModel.closePlaylist() else allMusicOpen = false
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                val destinations = listOf(
                    Destination.LIBRARY,
                    Destination.ROOM,
                )
                destinations.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = {
                            if (ui.selectedPlaylist != null) viewModel.closePlaylist()
                            allMusicOpen = false
                            destination = item
                        },
                        icon = {
                            Icon(
                                when (item) {
                                    Destination.LIBRARY -> Icons.Default.LibraryMusic
                                    Destination.ROOM -> {
                                        if (ui.room.snapshot == null) Icons.Default.Groups else Icons.Default.Equalizer
                                    }
                                },
                                item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier
            .padding(padding)
            .fillMaxSize()) {
            val playlist = ui.selectedPlaylist
            if (playlist != null) {
                PlaylistDetailScreen(
                    detail = playlist,
                    pickerTracks = pickerTracks,
                    pickerQuery = pickerQuery,
                    onPickerQueryChange = viewModel::setPickerQuery,
                    roomActive = ui.room.snapshot != null,
                    onRename = { viewModel.renamePlaylist(playlist.playlistId, it) },
                    onUpdateTracks = { viewModel.updatePlaylistTracks(playlist.playlistId, it) },
                    onAddTracks = { viewModel.addTracksToPlaylist(playlist.playlistId, it) },
                    onAddToRoom = { viewModel.addPlaylistToRoom(playlist.playlistId) },
                    onAddTracksToRoom = viewModel::addTracksToRoom,
                    onSelectAll = viewModel::loadTrackIds,
                    onExport = { startPlaylistExport(playlist.playlistId, playlist.name) },
                    onDelete = { viewModel.deletePlaylist(playlist.playlistId) },
                )
            } else {
                when (destination) {
                    Destination.LIBRARY -> LibraryScreen(
                        state = ui,
                        allMusicOpen = allMusicOpen,
                        onOpenAllMusic = { allMusicOpen = true },
                        tracks = libraryTracks,
                        pickerTracks = pickerTracks,
                        pickerQuery = pickerQuery,
                        onQueryChange = viewModel::setLibraryQuery,
                        onPickerQueryChange = viewModel::setPickerQuery,
                        onSortChange = viewModel::setLibrarySort,
                        onChooseFiles = {
                            importToRoom = false
                            filesLauncher.launch(arrayOf("audio/*"))
                        },
                        onImportM3u = {
                            importToRoom = false
                            m3uLauncher.launch(M3U_TYPES)
                        },
                        onEditName = { showNameEdit = true },
                        onAddTrackToRoom = { viewModel.addTracksToRoom(listOf(it)) },
                        onAddTracksToRoom = viewModel::addTracksToRoom,
                        onPlayNext = { viewModel.addTracksToRoom(listOf(it), insertAfterCurrent = true) },
                        onKeepTrack = viewModel::keepTrack,
                        onRemoveTemporaryTrack = viewModel::removeTemporaryTrack,
                        onClearTemporaryMusic = viewModel::clearTemporaryMusic,
                        onCreatePlaylist = viewModel::createPlaylist,
                        onOpenPlaylist = viewModel::openPlaylist,
                        onAddPlaylistToRoom = viewModel::addPlaylistToRoom,
                        onAddTrackToPlaylist = { playlistId, trackId ->
                            viewModel.addTracksToPlaylist(playlistId, listOf(trackId))
                        },
                        onAddTracksToPlaylist = viewModel::addTracksToPlaylist,
                        onSelectAll = viewModel::loadTrackIds,
                    )

                    Destination.ROOM -> if (ui.room.snapshot == null) {
                        RoomLobbyScreen(
                            state = ui,
                            onCreate = { viewModel.command(AppCommand.CreateRoom(it)) },
                            onDiscover = { viewModel.command(AppCommand.StartDiscovery) },
                            onJoin = { room, pin -> viewModel.command(AppCommand.JoinRoom(room, pin)) },
                            onOfflineNetwork = {
                                val missing = PermissionController.offlineNetworkPermissions().filter {
                                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                }
                                if (missing.isEmpty()) viewModel.command(AppCommand.CreateOfflineNetwork)
                                else {
                                    startHotspotAfterPermission = true
                                    hotspotPermissionLauncher.launch(missing.toTypedArray())
                                }
                            },
                            onStopOfflineNetwork = { viewModel.command(AppCommand.StopOfflineNetwork) },
                            onEditName = { showNameEdit = true },
                        )
                    } else RoomScreen(
                        state = ui,
                        playbackPositionMs = playbackPositionMs,
                        joinLink = viewModel.joinLink(),
                        onPlay = { viewModel.command(AppCommand.Play) },
                        onPause = { viewModel.command(AppCommand.Pause) },
                        onSeek = { viewModel.command(AppCommand.Seek(it)) },
                        onNext = { viewModel.command(AppCommand.SkipNext) },
                        onPrevious = { viewModel.command(AppCommand.SkipPrevious) },
                        onPlayQueueItem = { viewModel.command(AppCommand.PlayQueueItem(it)) },
                        onShuffle = { viewModel.command(AppCommand.ShuffleQueue) },
                        onRepeat = { viewModel.command(AppCommand.SetRepeat(it)) },
                        onChooseFiles = {
                            importToRoom = true
                            filesLauncher.launch(arrayOf("audio/*"))
                        },
                        onImportM3u = {
                            importToRoom = true
                            m3uLauncher.launch(M3U_TYPES)
                        },
                        onAddAllMusicToRoom = {
                            viewModel.loadTrackIds("") { all ->
                                viewModel.addTracksToRoom(all.toList())
                            }
                        },
                        onAddPlaylistToRoom = viewModel::addPlaylistToRoom,
                        onRemoveQueueItem = { viewModel.command(AppCommand.RemoveQueueItem(it)) },
                        onMoveQueueItem = { item, index -> viewModel.command(AppCommand.MoveQueueItem(item, index)) },
                        onKeepTrack = viewModel::keepTrack,
                        onUpdateOptions = { viewModel.command(AppCommand.UpdateRoomOptions(it)) },
                        onSetRetentionPolicy = viewModel::setRetentionPolicy,
                        onSaveQueue = { name ->
                            val ids = ui.room.snapshot?.queue?.map { it.track.trackId }.orEmpty()
                            if (ids.isNotEmpty()) viewModel.createPlaylist(name, ids)
                        },
                        onClearPlayed = { viewModel.command(AppCommand.ClearPlayed) },
                        onLeave = {
                            viewModel.command(AppCommand.LeaveRoom)
                        },
                    )
                }
            }
            if (ui.busy) {
                OperationBanner(
                    progress = ui.importProgress,
                    onCancel = viewModel::cancelImport,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    initialName: String,
    dismissible: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                label = { Text("Shown to friends") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim().ifBlank { "Friend" }) }) {
                Text(if (dismissible) "Save" else "Continue")
            }
        },
        dismissButton = {
            if (dismissible) TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RoomLobbyScreen(
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
private fun LibraryScreen(
    state: MainUiState,
    allMusicOpen: Boolean,
    onOpenAllMusic: () -> Unit,
    tracks: LazyPagingItems<TrackDescriptor>,
    pickerTracks: LazyPagingItems<TrackDescriptor>,
    pickerQuery: String,
    onQueryChange: (String) -> Unit,
    onPickerQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onChooseFiles: () -> Unit,
    onImportM3u: () -> Unit,
    onEditName: () -> Unit,
    onAddTrackToRoom: (TrackId) -> Unit,
    onAddTracksToRoom: (List<TrackId>) -> Unit,
    onPlayNext: (TrackId) -> Unit,
    onKeepTrack: (TrackId) -> Unit,
    onRemoveTemporaryTrack: (TrackId) -> Unit,
    onClearTemporaryMusic: () -> Unit,
    onCreatePlaylist: (String, List<TrackId>) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onAddPlaylistToRoom: (String) -> Unit,
    onAddTrackToPlaylist: (String, TrackId) -> Unit,
    onAddTracksToPlaylist: (String, List<TrackId>) -> Unit,
    onSelectAll: (String, (Set<TrackId>) -> Unit) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var storageDialog by remember { mutableStateOf(false) }
    var confirmClearTemporary by remember { mutableStateOf(false) }
    var createPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var playlistSelection by remember { mutableStateOf(setOf<TrackId>()) }
    var selectingMusic by rememberSaveable { mutableStateOf(false) }
    var musicSelection by remember { mutableStateOf(setOf<TrackId>()) }
    var playlistTarget by remember { mutableStateOf<Set<TrackId>?>(null) }
    val startPlaylistCreation = {
        createPlaylist = true
        playlistSelection = emptySet()
        playlistName = ""
        onPickerQueryChange("")
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = if (allMusicOpen) onChooseFiles else startPlaylistCreation) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(if (allMusicOpen) "Add music" else "New playlist")
            }
            Spacer(Modifier.weight(1f))
            if (!allMusicOpen) {
                IconButton(onClick = onChooseFiles) { Icon(Icons.Default.AudioFile, "Add music") }
            }
            IconButton(onClick = { storageDialog = true }) { Icon(Icons.Default.Storage, "Storage") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (allMusicOpen) {
                        DropdownMenuItem(
                            text = { Text("New playlist") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            onClick = { menu = false; startPlaylistCreation() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Import M3U") },
                        leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                        onClick = { menu = false; onImportM3u() },
                    )
                    DropdownMenuItem(
                        text = { Text("Change your name") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menu = false; onEditName() },
                    )
                }
            }
        }

        if (!allMusicOpen) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionTitle("Your playlists")
                Text(
                    "Choose a collection to manage it or add it to a room.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all-music") {
                    Card(
                        onClick = {
                            onQueryChange("")
                            selectingMusic = false
                            musicSelection = emptySet()
                            onOpenAllMusic()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.LibraryMusic,
                                null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("All music", style = MaterialTheme.typography.titleMedium)
                                val songCount = "${state.libraryTotalCount} " +
                                    if (state.libraryTotalCount == 1) "song" else "songs"
                                Text(
                                    "$songCount · Default playlist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.room.snapshot != null) {
                                IconButton(
                                    onClick = {
                                        onSelectAll("") { all -> onAddTracksToRoom(all.toList()) }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add all music to room")
                                }
                            }
                        }
                    }
                }
                items(state.playlists, key = { it.playlistId }) { playlist ->
                    Card(
                        onClick = { onOpenPlaylist(playlist.playlistId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${playlist.trackCount} ${if (playlist.trackCount == 1) "song" else "songs"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.room.snapshot != null) {
                                IconButton(onClick = { onAddPlaylistToRoom(playlist.playlistId) }) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add playlist to room")
                                }
                            }
                        }
                    }
                }
                if (state.playlists.isEmpty()) {
                    item(key = "empty-playlists") {
                        Card(onClick = startPlaylistCreation, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Create your first playlist", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Group songs before sharing them with a room.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("All music")
                Spacer(Modifier.weight(1f))
                if (state.room.snapshot != null) {
                    FilledTonalButton(
                        onClick = {
                            onSelectAll("") { all -> onAddTracksToRoom(all.toList()) }
                        },
                        enabled = state.libraryTotalCount > 0,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add all")
                    }
                }
                TextButton(
                    onClick = {
                        selectingMusic = !selectingMusic
                        if (!selectingMusic) musicSelection = emptySet()
                    },
                    enabled = tracks.itemCount > 0,
                ) { Text(if (selectingMusic) "Done" else "Select") }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.libraryQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search title, artist, or album") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.libraryQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Clear") }
                        }
                    },
                    singleLine = true,
                )
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        LibrarySort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.displayName()) },
                                trailingIcon = { if (sort == state.librarySort) Icon(Icons.Default.Check, null) },
                                onClick = { sortMenu = false; onSortChange(sort) },
                            )
                        }
                    }
                }
            }
            if (selectingMusic) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "${musicSelection.size} selected",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (musicSelection.size == state.libraryVisibleCount) {
                            musicSelection = emptySet()
                        } else {
                            onSelectAll(state.libraryQuery) { musicSelection = it }
                        }
                    }) {
                        Text(if (musicSelection.size == state.libraryVisibleCount) "Clear" else "Select all")
                    }
                    IconButton(
                        onClick = { playlistTarget = musicSelection },
                        enabled = musicSelection.isNotEmpty(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, "Add selection to playlist")
                    }
                    if (state.room.snapshot != null) {
                        FilledTonalButton(
                            onClick = {
                                onAddTracksToRoom(musicSelection.toList())
                                musicSelection = emptySet()
                                selectingMusic = false
                            },
                            enabled = musicSelection.isNotEmpty(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Room")
                        }
                    }
                }
            }
            Box(Modifier
                .fillMaxWidth()
                .weight(1f)) {
                when {
                    tracks.loadState.refresh is LoadState.Loading && tracks.itemCount == 0 -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    tracks.loadState.refresh is LoadState.Error && tracks.itemCount == 0 -> {
                        LoadError(onRetry = tracks::retry)
                    }

                    tracks.itemCount == 0 -> {
                        EmptyState(
                            title = if (state.libraryQuery.isBlank()) "No music yet" else "No matches",
                            text = if (state.libraryQuery.isBlank()) "Add local audio files to start." else "Try another search.",
                            icon = Icons.Default.AudioFile,
                            actionLabel = if (state.libraryQuery.isBlank()) "Add music" else null,
                            onAction = onChooseFiles,
                        )
                    }

                    else -> {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(
                                count = tracks.itemCount,
                                key = tracks.itemKey { it.trackId.value },
                            ) { index ->
                                tracks[index]?.let { track ->
                                    TrackRow(
                                        track = track,
                                        temporary = track.trackId in state.temporaryTrackIds,
                                        roomActive = state.room.snapshot != null,
                                        selectionMode = selectingMusic,
                                        selected = track.trackId in musicSelection,
                                        onSelectionChange = { checked ->
                                            musicSelection = if (checked) {
                                                musicSelection + track.trackId
                                            } else {
                                                musicSelection - track.trackId
                                            }
                                        },
                                        onAddToRoom = { onAddTrackToRoom(track.trackId) },
                                        onPlayNext = { onPlayNext(track.trackId) },
                                        onKeep = { onKeepTrack(track.trackId) },
                                        onRemove = { onRemoveTemporaryTrack(track.trackId) },
                                        onAddToPlaylist = { playlistTarget = setOf(track.trackId) },
                                    )
                                    HorizontalDivider(Modifier.padding(start = 56.dp))
                                }
                            }
                            when (tracks.loadState.append) {
                                is LoadState.Loading -> item {
                                    Box(Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    }
                                }

                                is LoadState.Error -> item {
                                    TextButton(onClick = tracks::retry, modifier = Modifier.fillMaxWidth()) {
                                        Text("Try loading more")
                                    }
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    playlistTarget?.let { trackIds ->
        AlertDialog(
            onDismissRequest = { playlistTarget = null },
            title = {
                Text(
                    if (trackIds.size == 1) "Add to playlist" else "Add ${trackIds.size} songs to playlist"
                )
            },
            text = {
                if (state.playlists.isEmpty()) {
                    Text("Create a playlist first.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(state.playlists, key = { it.playlistId }) { playlist ->
                            TextButton(
                                onClick = {
                                    if (trackIds.size == 1) {
                                        onAddTrackToPlaylist(playlist.playlistId, trackIds.first())
                                    } else {
                                        onAddTracksToPlaylist(playlist.playlistId, trackIds.toList())
                                    }
                                    playlistTarget = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(playlist.name, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
            },
            confirmButton = {
                if (state.playlists.isEmpty()) {
                    Button(onClick = {
                        playlistSelection = trackIds
                        playlistName = ""
                        onPickerQueryChange("")
                        playlistTarget = null
                        createPlaylist = true
                    }) { Text("Create playlist") }
                } else {
                    TextButton(onClick = { playlistTarget = null }) { Text("Cancel") }
                }
            },
        )
    }

    if (storageDialog) {
        AlertDialog(
            onDismissRequest = { storageDialog = false },
            icon = { Icon(Icons.Default.Storage, null) },
            title = { Text("Storage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageLine("Used by Unison", state.storageSummary.totalBytes)
                    StorageLine("Kept music", state.storageSummary.keptBytes)
                    StorageLine("Temporary music", state.storageSummary.temporaryBytes)
                    Text(
                        "Temporary music is removed automatically when it is no longer needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { storageDialog = false }) { Text("Done") } },
            dismissButton = {
                TextButton(
                    onClick = { storageDialog = false; confirmClearTemporary = true },
                    enabled = state.storageSummary.temporaryBytes > 0,
                ) { Text("Clear temporary") }
            },
        )
    }

    if (confirmClearTemporary) {
        AlertDialog(
            onDismissRequest = { confirmClearTemporary = false },
            title = { Text("Clear temporary music?") },
            text = { Text("Songs used by the active room will stay available.") },
            confirmButton = {
                Button(onClick = {
                    confirmClearTemporary = false
                    onClearTemporaryMusic()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearTemporary = false }) { Text("Cancel") }
            },
        )
    }

    if (createPlaylist) {
        AlertDialog(
            onDismissRequest = { createPlaylist = false },
            title = { Text("New playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it.take(60) },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = pickerQuery,
                        onValueChange = onPickerQueryChange,
                        placeholder = { Text("Search music") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${playlistSelection.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            onSelectAll(pickerQuery) { all ->
                                playlistSelection = if (playlistSelection.size == all.size) emptySet() else all
                            }
                        }) {
                            Text("Select all")
                        }
                    }
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(
                            count = pickerTracks.itemCount,
                            key = pickerTracks.itemKey { it.trackId.value },
                        ) { index ->
                            pickerTracks[index]?.let { track ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = track.trackId in playlistSelection,
                                        onCheckedChange = { checked ->
                                            playlistSelection = if (checked) {
                                                playlistSelection + track.trackId
                                            } else {
                                                playlistSelection - track.trackId
                                            }
                                        },
                                    )
                                    Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(playlistName, playlistSelection.toList())
                        createPlaylist = false
                    },
                    enabled = playlistName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createPlaylist = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TrackRow(
    track: TrackDescriptor,
    temporary: Boolean,
    roomActive: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onAddToRoom: () -> Unit,
    onPlayNext: () -> Unit,
    onKeep: () -> Unit,
    onRemove: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    ListItem(
        modifier = if (selectionMode) {
            Modifier.clickable { onSelectionChange(!selected) }
        } else {
            Modifier
        },
        headlineContent = { Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(
                    track.artist?.takeIf(String::isNotBlank),
                    formatDuration(track.durationMs)
                ).joinToString(" • "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { TrackArtwork(track, 40.dp) },
        trailingContent = {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelectionChange,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (roomActive) {
                        IconButton(onClick = onAddToRoom) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to room")
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (roomActive) {
                                DropdownMenuItem(
                                    text = { Text("Play next") },
                                    onClick = { menu = false; onPlayNext() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Add to playlist") },
                                onClick = { menu = false; onAddToPlaylist() },
                            )
                            if (temporary) {
                                DropdownMenuItem(
                                    text = { Text("Keep on this phone") },
                                    onClick = { menu = false; onKeep() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove temporary copy") },
                                    onClick = { menu = false; confirmRemove = true },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this song?") },
            text = { Text("The temporary copy will be deleted from this phone.") },
            confirmButton = {
                Button(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistDetailScreen(
    detail: PlaylistDetail,
    pickerTracks: LazyPagingItems<TrackDescriptor>,
    pickerQuery: String,
    onPickerQueryChange: (String) -> Unit,
    roomActive: Boolean,
    onRename: (String) -> Unit,
    onUpdateTracks: (List<TrackId>) -> Unit,
    onAddTracks: (List<TrackId>) -> Unit,
    onAddToRoom: () -> Unit,
    onAddTracksToRoom: (List<TrackId>) -> Unit,
    onSelectAll: (String, (Set<TrackId>) -> Unit) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var name by remember(detail.name) { mutableStateOf(detail.name) }
    var addSongs by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<TrackId>()) }
    var selectingPlaylist by rememberSaveable(detail.playlistId) { mutableStateOf(false) }
    var selectedIndices by remember(detail.playlistId) { mutableStateOf(setOf<Int>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${detail.tracks.size} ${if (detail.tracks.size == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        selectingPlaylist = !selectingPlaylist
                        if (!selectingPlaylist) selectedIndices = emptySet()
                    },
                    enabled = detail.tracks.isNotEmpty(),
                ) {
                    Text(if (selectingPlaylist) "Done" else "Select")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Playlist actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menu = false; rename = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Export M3U") },
                            leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                            onClick = { menu = false; onExport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
            if (selectingPlaylist) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${selectedIndices.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            selectedIndices = if (selectedIndices.size == detail.tracks.size) {
                                emptySet()
                            } else {
                                detail.tracks.indices.toSet()
                            }
                        }) {
                            Text(if (selectedIndices.size == detail.tracks.size) "Clear" else "Select all")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (roomActive) {
                            FilledTonalButton(
                                onClick = {
                                    onAddTracksToRoom(selectedIndices.sorted().map { detail.tracks[it].trackId })
                                    selectedIndices = emptySet()
                                    selectingPlaylist = false
                                },
                                enabled = selectedIndices.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add to room")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onUpdateTracks(
                                    detail.tracks.map { it.trackId }
                                        .filterIndexed { index, _ -> index !in selectedIndices }
                                )
                                selectedIndices = emptySet()
                                selectingPlaylist = false
                            },
                            enabled = selectedIndices.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.DeleteOutline, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Remove")
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            addSongs = true
                            selected = emptySet()
                            onPickerQueryChange("")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add songs")
                    }
                    if (roomActive) {
                        OutlinedButton(
                            onClick = onAddToRoom,
                            enabled = detail.tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add to room")
                        }
                    }
                }
            }
        }
        if (detail.tracks.isEmpty()) {
            EmptyState("Empty playlist", "Add songs from your library.", Icons.AutoMirrored.Filled.QueueMusic)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(detail.tracks, key = { index, track -> "$index:${track.trackId.value}" }) { index, track ->
                    ListItem(
                        modifier = if (selectingPlaylist) {
                            Modifier.clickable {
                                selectedIndices = if (index in selectedIndices) {
                                    selectedIndices - index
                                } else {
                                    selectedIndices + index
                                }
                            }
                        } else {
                            Modifier
                        },
                        headlineContent = { Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    track.artist?.takeIf(String::isNotBlank),
                                    formatDuration(track.durationMs),
                                ).joinToString(" • "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = {
                            if (selectingPlaylist) {
                                Checkbox(
                                    checked = index in selectedIndices,
                                    onCheckedChange = { checked ->
                                        selectedIndices = if (checked) {
                                            selectedIndices + index
                                        } else {
                                            selectedIndices - index
                                        }
                                    },
                                )
                            } else {
                                Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                            }
                        },
                        trailingContent = {
                            if (!selectingPlaylist) PlaylistTrackActions(
                                canMoveUp = index > 0,
                                canMoveDown = index < detail.tracks.lastIndex,
                                onMoveUp = {
                                    val ids = detail.tracks.map { it.trackId }.toMutableList()
                                    val moved = ids.removeAt(index)
                                    ids.add(index - 1, moved)
                                    onUpdateTracks(ids)
                                },
                                onMoveDown = {
                                    val ids = detail.tracks.map { it.trackId }.toMutableList()
                                    val moved = ids.removeAt(index)
                                    ids.add(index + 1, moved)
                                    onUpdateTracks(ids)
                                },
                                onRemove = {
                                    val ids = detail.tracks.map { it.trackId }.toMutableList()
                                    ids.removeAt(index)
                                    onUpdateTracks(ids)
                                },
                            )
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                }
            }
        }
    }

    if (rename) {
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename playlist") },
            text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true) },
            confirmButton = { Button(onClick = { onRename(name); rename = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } },
        )
    }
    if (addSongs) {
        AlertDialog(
            onDismissRequest = { addSongs = false },
            title = { Text("Add songs") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pickerQuery,
                        onValueChange = onPickerQueryChange,
                        placeholder = { Text("Search music") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${selected.size} selected",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            onSelectAll(pickerQuery) { all ->
                                selected = if (selected.size == all.size) emptySet() else all
                            }
                        }) {
                            Text("Select all")
                        }
                    }
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(
                            count = pickerTracks.itemCount,
                            key = pickerTracks.itemKey { it.trackId.value },
                        ) { index ->
                            pickerTracks[index]?.let { track ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = track.trackId in selected,
                                        onCheckedChange = { checked ->
                                            selected =
                                                if (checked) selected + track.trackId else selected - track.trackId
                                        },
                                    )
                                    Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAddTracks(selected.toList()); addSongs = false },
                    enabled = selected.isNotEmpty()
                ) {
                    Text("Add")
                }
            },
            dismissButton = { TextButton(onClick = { addSongs = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("The music files will stay in your library.") },
            confirmButton = { Button(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlaylistTrackActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Song actions") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Move up") },
                enabled = canMoveUp,
                onClick = { menu = false; onMoveUp() },
            )
            DropdownMenuItem(
                text = { Text("Move down") },
                enabled = canMoveDown,
                onClick = { menu = false; onMoveDown() },
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = { menu = false; onRemove() },
            )
        }
    }
}

@Composable
private fun RoomScreen(
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
                    Image(QrCode.create(joinLink), "Room QR", Modifier.size(252.dp))
                    Text(
                        "PIN ${snapshot.roomPin ?: "—"}",
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
private fun CompactRoomPlayer(
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
private fun TransferStatusCard(
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
                    if (transfer.state == MemberTrackState.FAILED) {
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
private fun QueueRow(
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

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TrackArtwork(
    track: TrackDescriptor,
    size: androidx.compose.ui.unit.Dp,
    reloadKey: Any? = Unit,
) {
    val container = LocalContext.current.unisonContainer
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = track.trackId, key2 = reloadKey) {
        try {
            val audioFile = withContext(Dispatchers.IO) {
                container.trackRepository.requireReadableFile(track.trackId)
            }
            if (audioFile != null) {
                value = withContext(Dispatchers.IO) {
                    container.artworkStore.bitmapFor(track.trackId, audioFile)
                }
                // Retry once only when extraction recorded a transient decoder/storage failure.
                val retryDelayMs = if (value == null) {
                    withContext(Dispatchers.IO) {
                        container.artworkStore.transientRetryDelayMs(track.trackId)
                    }
                } else {
                    null
                }
                if (retryDelayMs != null) {
                    delay(retryDelayMs + 500L)
                    value = withContext(Dispatchers.IO) {
                        container.artworkStore.bitmapFor(track.trackId, audioFile)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            container.diagnostics.w(
                "TrackArtwork",
                "Could not load artwork track=${track.trackId.value.take(8)}",
                error,
            )
        }
    }
    val shape = RoundedCornerShape(if (size >= 100.dp) 18.dp else 8.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val artwork = bitmap
        if (artwork != null && !artwork.isRecycled) {
            Image(
                bitmap = artwork.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    modifier = Modifier.size(if (size >= 100.dp) 48.dp else 22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun OperationBanner(
    progress: ImportProgress?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (progress == null) {
                Text(
                    "Working…",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Adding ${progress.completed} of ${progress.total}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Cancel")
                    }
                }
                LinearProgressIndicator(
                    progress = { progress.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LoadError(onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Music could not be loaded", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StorageLine(label: String, bytes: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(formatBytes(bytes), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyState(
    title: String,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
                Text(actionLabel)
            }
        }
    }
}

private fun RepeatMode.next(): RepeatMode = when (this) {
    RepeatMode.OFF -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.OFF
}

private fun LibrarySort.displayName(): String = when (this) {
    LibrarySort.RECENT -> "Recently added"
    LibrarySort.TITLE -> "Title"
    LibrarySort.ARTIST -> "Artist"
    LibrarySort.ALBUM -> "Album"
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.US, minutes, seconds)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(Locale.US, bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(Locale.US, bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(Locale.US, bytes / 1_000.0)
    else -> "$bytes B"
}

private fun String.safeFileName(): String = lowercase(Locale.US)
    .replace(Regex("[^a-z0-9._-]+"), "-")
    .trim('-')
    .ifBlank { "unison-playlist" }

private val M3U_TYPES = arrayOf(
    "audio/x-mpegurl",
    "application/vnd.apple.mpegurl",
    "application/x-mpegurl",
    "text/plain",
)
