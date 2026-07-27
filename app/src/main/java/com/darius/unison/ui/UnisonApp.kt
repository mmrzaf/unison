package com.darius.unison.ui

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darius.unison.library.PlaylistDetail
import com.darius.unison.model.AppCommand
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.HotspotInfo
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import java.util.Locale

private enum class Destination(val label: String) { HOME("Home"), LIBRARY("Library"), ROOM("Room") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnisonApp(viewModel: MainViewModel) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var importToRoom by remember { mutableStateOf(false) }
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importMusic(uris, importToRoom)
    }
    val m3uLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importM3u(it, importToRoom) }
    }
    val m3uFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.resolvePendingM3u(uri)
    }
    var exportPlaylistId by remember { mutableStateOf<String?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
            val id = exportPlaylistId
            if (uri != null && id != null) viewModel.exportPlaylist(id, uri)
            exportPlaylistId = null
        }
    val startPlaylistExport: (String, String) -> Unit = { id, name ->
        exportPlaylistId = id
        exportLauncher.launch("${name.safeFileName()}.m3u8")
    }

    var startHotspotAfterPermission by remember { mutableStateOf(false) }
    val hotspotPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants.values.all { it }
            if (granted && startHotspotAfterPermission) {
                viewModel.command(AppCommand.CreateOfflineNetwork)
            } else if (startHotspotAfterPermission) {
                viewModel.showMessage("Nearby Wi-Fi permission is required to create an offline network")
            }
            startHotspotAfterPermission = false
        }

    LaunchedEffect(ui.message, ui.room.errorMessage) {
        val value = ui.message ?: ui.room.errorMessage
        if (!value.isNullOrBlank()) {
            snackbar.showSnackbar(value)
            viewModel.clearNotice()
        }
    }
    LaunchedEffect(ui.room.snapshot) {
        if (ui.room.snapshot != null) destination = Destination.ROOM
    }

    if (!ui.settingsLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!ui.onboardingComplete) {
        NameDialog(onSave = viewModel::saveName)
    }

    BackHandler(enabled = ui.selectedPlaylist != null) {
        viewModel.closePlaylist()
    }

    ui.pendingM3uResolution?.let { pending ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.FolderOpen, null) },
            title = { Text("Find playlist music") },
            text = {
                Text(
                    "${pending.unresolvedCount} ${if (pending.unresolvedCount == 1) "song was" else "songs were"} not found. " +
                        "Choose the folder containing the music, or continue without them."
                )
            },
            confirmButton = {
                Button(onClick = { m3uFolderLauncher.launch(null) }) { Text("Choose folder") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::finishPendingM3uWithoutFolder) { Text("Use available") }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Unison") },
                actions = {
                    ui.room.snapshot?.let {
                        IconButton(onClick = { destination = Destination.ROOM }) {
                            Icon(Icons.Default.Headphones, contentDescription = "Open room")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {
                            Icon(
                                when (item) {
                                    Destination.HOME -> Icons.Default.Home
                                    Destination.LIBRARY -> Icons.Default.LibraryMusic
                                    Destination.ROOM -> Icons.Default.Groups
                                },
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                        enabled = item != Destination.ROOM || ui.room.snapshot != null,
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier
            .padding(padding)
            .fillMaxSize()) {
            when (destination) {
                Destination.HOME -> HomeScreen(
                    state = ui,
                    onCreate = { viewModel.command(AppCommand.CreateRoom(it)) },
                    onDiscover = { viewModel.command(AppCommand.StartDiscovery) },
                    onJoin = { room, pin -> viewModel.command(AppCommand.JoinRoom(room, pin)) },
                    onOfflineNetwork = {
                        val permissions = PermissionController.offlineNetworkPermissions()
                        val missing = permissions.filter {
                            ContextCompat.checkSelfPermission(
                                context,
                                it
                            ) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) viewModel.command(AppCommand.CreateOfflineNetwork)
                        else {
                            startHotspotAfterPermission = true
                            hotspotPermissionLauncher.launch(missing.toTypedArray())
                        }
                    },
                    onStopOfflineNetwork = { viewModel.command(AppCommand.StopOfflineNetwork) },
                )

                Destination.LIBRARY -> LibraryScreen(
                    state = ui,
                    onChooseFiles = {
                        importToRoom = false
                        filesLauncher.launch(arrayOf("audio/*"))
                    },
                    onImportM3u = {
                        importToRoom = false
                        m3uLauncher.launch(
                            arrayOf(
                                "audio/x-mpegurl",
                                "application/vnd.apple.mpegurl",
                                "application/x-mpegurl",
                                "text/plain"
                            )
                        )
                    },
                    onAddTrack = { viewModel.command(AppCommand.AddTracks(listOf(it))) },
                    onKeepTrack = viewModel::keepTrack,
                    onRemoveTemporaryTrack = viewModel::removeTemporaryTrack,
                    onClearTemporaryMusic = viewModel::clearTemporaryMusic,
                    onCreatePlaylist = viewModel::createPlaylist,
                    onOpenPlaylist = viewModel::openPlaylist,
                    onClosePlaylist = viewModel::closePlaylist,
                    onRenamePlaylist = viewModel::renamePlaylist,
                    onUpdatePlaylistTracks = viewModel::updatePlaylistTracks,
                    onAddTracksToPlaylist = viewModel::addTracksToPlaylist,
                    onDeletePlaylist = viewModel::deletePlaylist,
                    onAddPlaylist = viewModel::addPlaylistToRoom,
                    onExportPlaylist = startPlaylistExport,
                )

                Destination.ROOM -> RoomScreen(
                    state = ui,
                    joinLink = viewModel.joinLink(),
                    onPlay = { viewModel.command(AppCommand.Play) },
                    onPause = { viewModel.command(AppCommand.Pause) },
                    onSeek = { viewModel.command(AppCommand.Seek(it)) },
                    onNext = { viewModel.command(AppCommand.SkipNext) },
                    onPrevious = { viewModel.command(AppCommand.SkipPrevious) },
                    onChooseFiles = {
                        importToRoom = true
                        filesLauncher.launch(arrayOf("audio/*"))
                    },
                    onImportM3u = {
                        importToRoom = true
                        m3uLauncher.launch(
                            arrayOf(
                                "audio/x-mpegurl",
                                "application/vnd.apple.mpegurl",
                                "application/x-mpegurl",
                                "text/plain"
                            )
                        )
                    },
                    onRemoveQueueItem = { viewModel.command(AppCommand.RemoveQueueItem(it)) },
                    onMoveQueueItem = { item, index -> viewModel.command(AppCommand.MoveQueueItem(item, index)) },
                    onUpdateOptions = { viewModel.command(AppCommand.UpdateRoomOptions(it)) },
                    onSetRetentionPolicy = viewModel::setRetentionPolicy,
                    onLeave = {
                        viewModel.command(AppCommand.LeaveRoom)
                        destination = Destination.HOME
                    },
                )
            }
            if (ui.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun NameDialog(onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.GraphicEq, null) },
        title = { Text("Welcome to Unison") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose the name your friends will see in nearby rooms.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Your name") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Continue") }
        },
    )
}

@Composable
private fun HomeScreen(
    state: MainUiState,
    onCreate: (String?) -> Unit,
    onDiscover: () -> Unit,
    onJoin: (DiscoveredRoom, String) -> Unit,
    onOfflineNetwork: () -> Unit,
    onStopOfflineNetwork: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var showHotspotQr by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf<DiscoveredRoom?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Listen together", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Your music, together, without internet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Create room")
                }
                OutlinedButton(onClick = onDiscover, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Find rooms")
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Offline network", style = MaterialTheme.typography.titleMedium)
                    if (state.room.hotspot == null) {
                        Text(
                            "Use this only when no shared Wi-Fi is available.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onOfflineNetwork) { Text("Create local hotspot") }
                    } else {
                        Text("Network: ${state.room.hotspot.ssid}")
                        state.room.hotspot.passphrase?.let { Text("Password: $it") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showHotspotQr = true }) { Text("Show Wi-Fi QR") }
                            TextButton(onClick = onStopOfflineNetwork) { Text("Stop hotspot") }
                        }
                    }
                }
            }
        }
        if (state.room.discoveredRooms.isNotEmpty()) {
            item { Text("Nearby rooms", style = MaterialTheme.typography.titleLarge) }
            items(state.room.discoveredRooms, key = { it.roomId }) { room ->
                ListItem(
                    headlineContent = { Text(room.roomName) },
                    supportingContent = { Text("Nearby") },
                    leadingContent = { Icon(Icons.Default.Groups, null) },
                    trailingContent = { FilledTonalButton(onClick = { joining = room }) { Text("Join") } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (state.room.lifecycle == RoomLifecycleState.DISCOVERING) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Looking for rooms on this network…")
                }
            }
        }
        item {
            Text("How it works", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Create a room, add songs, and listen together. Unison gets upcoming songs ready automatically.")
        }
    }

    if (showHotspotQr && state.room.hotspot != null) {
        val hotspot = state.room.hotspot
        AlertDialog(
            onDismissRequest = { showHotspotQr = false },
            title = { Text("Connect to offline network") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(QrCode.create(hotspot.wifiQrPayload()), "Wi-Fi QR", Modifier.size(260.dp))
                    Text(hotspot.ssid, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Scan with the phone camera, then return to Unison and find the room.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showHotspotQr = false }) { Text("Done") } },
        )
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create room") },
            text = {
                OutlinedTextField(
                    name,
                    { name = it.take(60) },
                    label = { Text("Room name (optional)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    showCreate = false; onCreate(name.ifBlank { null })
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }
    joining?.let { room ->
        var pin by remember(room.roomId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { joining = null },
            title = { Text("Join ${room.roomName}") },
            text = {
                OutlinedTextField(
                    pin,
                    { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text("6-digit room PIN") },
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
    onChooseFiles: () -> Unit,
    onImportM3u: () -> Unit,
    onAddTrack: (TrackId) -> Unit,
    onKeepTrack: (TrackId) -> Unit,
    onRemoveTemporaryTrack: (TrackId) -> Unit,
    onClearTemporaryMusic: () -> Unit,
    onCreatePlaylist: (String, List<TrackId>) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onClosePlaylist: () -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onUpdatePlaylistTracks: (String, List<TrackId>) -> Unit,
    onAddTracksToPlaylist: (String, List<TrackId>) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddPlaylist: (String) -> Unit,
    onExportPlaylist: (String, String) -> Unit,
) {
    state.selectedPlaylist?.let { playlist ->
        PlaylistDetailScreen(
            playlist = playlist,
            libraryTracks = state.tracks,
            roomActive = state.room.snapshot != null,
            onBack = onClosePlaylist,
            onRename = { onRenamePlaylist(playlist.playlistId, it) },
            onReplaceTracks = { onUpdatePlaylistTracks(playlist.playlistId, it) },
            onAddTracks = { onAddTracksToPlaylist(playlist.playlistId, it) },
            onDelete = { onDeletePlaylist(playlist.playlistId) },
            onAddToRoom = { onAddPlaylist(playlist.playlistId) },
            onExport = { onExportPlaylist(playlist.playlistId, playlist.name) },
        )
        return
    }

    var selected by remember { mutableStateOf(setOf<TrackId>()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showClearTemporaryDialog by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChooseFiles) {
                    Icon(
                        Icons.Default.AudioFile,
                        null
                    ); Spacer(Modifier.width(6.dp)); Text("Choose files")
                }
                OutlinedButton(onClick = onImportM3u) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null); Spacer(
                    Modifier.width(6.dp)
                ); Text("Import M3U")
                }
            }
        }
        if (state.storageSummary.totalBytes > 0L) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, null)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Music stored by Unison", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    formatBytes(state.storageSummary.totalBytes),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (state.storageSummary.keptBytes > 0L) {
                            Text(
                                "Kept ${formatBytes(state.storageSummary.keptBytes)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.storageSummary.temporaryBytes > 0L) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Temporary ${formatBytes(state.storageSummary.temporaryBytes)}",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { showClearTemporaryDialog = true }) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.playlists.isNotEmpty()) {
            item {
                Text(
                    "Playlists",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            items(state.playlists, key = { it.playlistId }) { playlist ->
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    supportingContent = { Text("Tap to view and edit") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                onExportPlaylist(
                                    playlist.playlistId,
                                    playlist.name
                                )
                            }) { Icon(Icons.Default.UploadFile, "Export") }
                            IconButton(
                                onClick = { onAddPlaylist(playlist.playlistId) },
                                enabled = state.room.snapshot != null
                            ) {
                                Icon(Icons.Default.AddCircle, "Add to room")
                            }
                        }
                    },
                    modifier = Modifier.clickable { onOpenPlaylist(playlist.playlistId) },
                )
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { showPlaylistDialog = true }) { Text("New playlist (${selected.size})") }
                }
            }
        }
        if (state.tracks.isEmpty()) {
            item {
                EmptyState(
                    "No music yet",
                    "Choose audio files or share them to Unison.",
                    Icons.Default.LibraryMusic
                )
            }
        } else {
            items(state.tracks, key = { it.trackId.value }) { track ->
                val checked = track.trackId in selected
                ListItem(
                    headlineContent = { Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val detail = track.artist ?: track.originalFileName ?: formatBytes(track.sizeBytes)
                        Text(
                            if (track.trackId in state.temporaryTrackIds) "$detail • Temporary" else detail,
                            maxLines = 1
                        )
                    },
                    leadingContent = {
                        Checkbox(
                            checked,
                            onCheckedChange = {
                                selected = if (it) selected + track.trackId else selected - track.trackId
                            })
                    },
                    trailingContent = {
                        Row {
                            if (track.trackId in state.temporaryTrackIds) {
                                IconButton(onClick = { onKeepTrack(track.trackId) }) {
                                    Icon(
                                        Icons.Default.BookmarkAdd,
                                        "Keep"
                                    )
                                }
                                IconButton(onClick = { onRemoveTemporaryTrack(track.trackId) }) {
                                    Icon(Icons.Default.DeleteOutline, "Remove")
                                }
                            }
                            IconButton(onClick = { onAddTrack(track.trackId) }, enabled = state.room.snapshot != null) {
                                Icon(Icons.Default.AddCircle, "Add to room")
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        selected = if (checked) selected - track.trackId else selected + track.trackId
                    },
                )
            }
        }
    }
    if (showClearTemporaryDialog) {
        AlertDialog(
            onDismissRequest = { showClearTemporaryDialog = false },
            title = { Text("Clear temporary music?") },
            text = { Text("Music currently used by the active room will stay available. Other temporary copies will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearTemporaryDialog = false
                        onClearTemporaryMusic()
                    }
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearTemporaryDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showPlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(name, { name = it.take(80) }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    onCreatePlaylist(name, selected.toList())
                    selected = emptySet()
                    showPlaylistDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showPlaylistDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlist: PlaylistDetail,
    libraryTracks: List<TrackDescriptor>,
    roomActive: Boolean,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onReplaceTracks: (List<TrackId>) -> Unit,
    onAddTracks: (List<TrackId>) -> Unit,
    onDelete: () -> Unit,
    onAddToRoom: () -> Unit,
    onExport: () -> Unit,
) {
    var showRename by remember(playlist.playlistId) { mutableStateOf(false) }
    var showAddSongs by remember(playlist.playlistId) { mutableStateOf(false) }
    var showDelete by remember(playlist.playlistId) { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${playlist.tracks.size} ${if (playlist.tracks.size == 1) "song" else "songs"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showRename = true }) { Icon(Icons.Default.Edit, "Rename") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddToRoom, enabled = roomActive && playlist.tracks.isNotEmpty()) {
                    Icon(Icons.Default.AddCircle, null); Spacer(Modifier.width(6.dp)); Text("Add to room")
                }
                OutlinedButton(onClick = { showAddSongs = true }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null); Spacer(Modifier.width(6.dp)); Text("Add songs")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onExport) {
                    Icon(
                        Icons.Default.UploadFile,
                        null
                    ); Spacer(Modifier.width(6.dp)); Text("Export M3U")
                }
                TextButton(onClick = { showDelete = true }) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        null
                    ); Spacer(Modifier.width(6.dp)); Text("Delete")
                }
            }
        }
        if (playlist.tracks.isEmpty()) {
            item { EmptyState("Empty playlist", "Add songs from your library.", Icons.AutoMirrored.Filled.QueueMusic) }
        } else {
            itemsIndexed(playlist.tracks, key = { index, track -> "${track.trackId.value}:$index" }) { index, track ->
                ListItem(
                    headlineContent = { Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(track.artist ?: formatDuration(track.durationMs), maxLines = 1) },
                    leadingContent = { Text("${index + 1}") },
                    trailingContent = {
                        var menuOpen by remember(playlist.playlistId, track.trackId, index) { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, "Song options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Move up") },
                                    leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) },
                                    enabled = index > 0,
                                    onClick = {
                                        menuOpen = false
                                        val updated = playlist.tracks.map { it.trackId }.toMutableList()
                                        val value = updated.removeAt(index)
                                        updated.add(index - 1, value)
                                        onReplaceTracks(updated)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Move down") },
                                    leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) },
                                    enabled = index < playlist.tracks.lastIndex,
                                    onClick = {
                                        menuOpen = false
                                        val updated = playlist.tracks.map { it.trackId }.toMutableList()
                                        val value = updated.removeAt(index)
                                        updated.add(index + 1, value)
                                        onReplaceTracks(updated)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove") },
                                    leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) },
                                    onClick = {
                                        menuOpen = false
                                        val updated = playlist.tracks.map { it.trackId }.toMutableList()
                                            .also { it.removeAt(index) }
                                        onReplaceTracks(updated)
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    if (showRename) {
        var name by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename playlist") },
            text = { OutlinedTextField(name, { name = it.take(80) }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { Button(onClick = { onRename(name); showRename = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } },
        )
    }
    if (showAddSongs) {
        var selected by remember { mutableStateOf(setOf<TrackId>()) }
        val existing = playlist.tracks.mapTo(mutableSetOf()) { it.trackId }
        val choices = libraryTracks.filterNot { it.trackId in existing }
        AlertDialog(
            onDismissRequest = { showAddSongs = false },
            title = { Text("Add songs") },
            text = {
                if (choices.isEmpty()) Text("Every song in your library is already here.")
                else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(choices, key = { it.trackId.value }) { track ->
                        val checked = track.trackId in selected
                        ListItem(
                            headlineContent = {
                                Text(
                                    track.displayTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Checkbox(
                                    checked,
                                    { selected = if (it) selected + track.trackId else selected - track.trackId })
                            },
                            modifier = Modifier.clickable {
                                selected = if (checked) selected - track.trackId else selected + track.trackId
                            },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAddTracks(selected.toList()); showAddSongs = false },
                    enabled = selected.isNotEmpty()
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddSongs = false }) { Text("Cancel") } },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("The songs stay in your library.") },
            confirmButton = { Button(onClick = { onDelete(); showDelete = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RoomScreen(
    state: MainUiState,
    joinLink: String?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onChooseFiles: () -> Unit,
    onImportM3u: () -> Unit,
    onRemoveQueueItem: (QueueItemId) -> Unit,
    onMoveQueueItem: (QueueItemId, Int) -> Unit,
    onUpdateOptions: (RoomOptions) -> Unit,
    onSetRetentionPolicy: (RetentionPolicy) -> Unit,
    onLeave: () -> Unit,
) {
    val snapshot = state.room.snapshot
    if (snapshot == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("No active room", "Create or join a room from Home.", Icons.Default.Groups)
        }
        return
    }
    var showQr by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    val nowPlaying = snapshot.queue.firstOrNull { it.queueItemId == snapshot.playback.queueItemId }
    val duration = nowPlaying?.track?.durationMs?.coerceAtLeast(1) ?: 1L
    val currentPrepared = nowPlaying?.queueItemId?.let { it in snapshot.preparedQueueItemIds } == true
    var seekPreview by remember(state.room.localPlaybackPositionMs, snapshot.playback.queueItemId) {
        mutableFloatStateOf(state.room.localPlaybackPositionMs.coerceIn(0, duration).toFloat())
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        snapshot.roomName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val connectedCount = snapshot.members.count { it.connected }
                    Text(
                        "$connectedCount ${if (connectedCount == 1) "listener" else "listeners"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (joinLink != null) IconButton(onClick = { showQr = true }) { Icon(Icons.Default.QrCode2, "Invite") }
                IconButton(onClick = { showOptions = true }) { Icon(Icons.Default.Settings, "Room options") }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.GraphicEq, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        nowPlaying?.track?.displayTitle ?: "Nothing queued",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    nowPlaying?.track?.artist?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Slider(
                        value = seekPreview,
                        onValueChange = { seekPreview = it },
                        onValueChangeFinished = { onSeek(seekPreview.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        enabled = nowPlaying != null,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(seekPreview.toLong()))
                        Text(formatDuration(duration))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = onPrevious,
                            enabled = nowPlaying != null
                        ) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(34.dp)) }
                        FilledIconButton(
                            onClick = if (snapshot.playback.isPlaying) onPause else onPlay,
                            modifier = Modifier.size(64.dp),
                            enabled = nowPlaying != null,
                        ) {
                            Icon(
                                if (snapshot.playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                "Play or pause",
                                Modifier.size(36.dp)
                            )
                        }
                        IconButton(onClick = onNext, enabled = nowPlaying != null) {
                            Icon(
                                Icons.Default.SkipNext,
                                "Next",
                                Modifier.size(34.dp)
                            )
                        }
                    }
                    if (nowPlaying != null && !currentPrepared) {
                        Text(
                            "Getting this song ready…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChooseFiles) {
                    Icon(
                        Icons.Default.Add,
                        null
                    ); Spacer(Modifier.width(6.dp)); Text("Add music")
                }
                OutlinedButton(onClick = onImportM3u) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null); Spacer(
                    Modifier.width(6.dp)
                ); Text("Add M3U")
                }
            }
        }
        item { Text("Queue", style = MaterialTheme.typography.titleLarge) }
        if (snapshot.queue.isEmpty()) {
            item { Text("Add songs to start listening.") }
        } else {
            itemsIndexed(snapshot.queue, key = { _, item -> item.queueItemId.value }) { index, item ->
                val isCurrent = item.queueItemId == snapshot.playback.queueItemId
                val isPrepared = item.queueItemId in snapshot.preparedQueueItemIds
                ListItem(
                    headlineContent = { Text(item.track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            when {
                                isCurrent && snapshot.playback.isPlaying -> "Playing • ${formatDuration(item.track.durationMs)}"
                                isCurrent && isPrepared -> "Paused • ${formatDuration(item.track.durationMs)}"
                                isCurrent -> "Getting ready • ${formatDuration(item.track.durationMs)}"
                                isPrepared -> "Up next • ${formatDuration(item.track.durationMs)}"
                                else -> "Getting ready • ${formatDuration(item.track.durationMs)}"
                            }
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (isCurrent) Icons.Default.Equalizer else Icons.Default.MusicNote,
                            null
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onMoveQueueItem(item.queueItemId, index - 1) },
                                enabled = index > 0,
                            ) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                            IconButton(
                                onClick = { onMoveQueueItem(item.queueItemId, index + 1) },
                                enabled = index < snapshot.queue.lastIndex,
                            ) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                            IconButton(onClick = { onRemoveQueueItem(item.queueItemId) }) {
                                Icon(Icons.Default.Close, "Remove")
                            }
                        }
                    },
                )
            }
        }
        item { Text("Listeners", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
        items(snapshot.members, key = { it.peerId.value }) { member ->
            ListItem(
                headlineContent = { Text(member.displayName) },
                supportingContent = {
                    Text(
                        if (member.connected) "Connected" else "Offline"
                    )
                },
                leadingContent = { Icon(Icons.Default.Person, null) },
                trailingContent = {
                    val color =
                        if (member.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    Icon(Icons.Default.Circle, null, Modifier.size(10.dp), tint = color)
                },
            )
        }
        item {
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text("Leave room")
            }
        }
    }

    if (showQr && joinLink != null) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Join this room") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(QrCode.create(joinLink), "Room QR", Modifier.size(260.dp))
                    Text(
                        "PIN ${snapshot.roomPin ?: "—"}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Friends on the same Wi-Fi can scan this with their camera.",
                        style = MaterialTheme.typography.bodySmall
                    )
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
            title = { Text("Room & storage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Shared room", style = MaterialTheme.typography.titleSmall)
                    SwitchRow("Wait for everyone before each song", options.waitAtTrackBoundary) {
                        options = options.copy(waitAtTrackBoundary = it)
                    }
                    Text("Keep the next ${options.preloadCount} songs ready")
                    Slider(
                        value = options.preloadCount.toFloat(),
                        onValueChange = { options = options.copy(preloadCount = it.toInt().coerceIn(1, 10)) },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                    HorizontalDivider()
                    Text("On this phone", style = MaterialTheme.typography.titleSmall)
                    Text("Received music", style = MaterialTheme.typography.labelLarge)
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
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun EmptyState(title: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    return "%d:%02d".format(Locale.US, total / 60, total % 60)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(Locale.US, bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(Locale.US, bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(Locale.US, bytes / 1_000.0)
    else -> "$bytes B"
}

private fun HotspotInfo.wifiQrPayload(): String {
    fun String.escapeWifiQr(): String = replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace(":", "\\:")
        .replace("\"", "\\\"")

    val security = if (passphrase.isNullOrEmpty()) "nopass" else "WPA"
    return "WIFI:T:$security;S:${ssid.escapeWifiQr()};P:${passphrase.orEmpty().escapeWifiQr()};;"
}

private fun String.safeFileName(): String = lowercase(Locale.US)
    .replace(Regex("[^a-z0-9._-]+"), "-")
    .trim('-')
    .ifBlank { "unison-playlist" }
