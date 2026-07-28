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

    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingNotificationAction
        pendingNotificationAction = null
        if (!granted) {
            viewModel.showMessage("Notifications are off; background playback controls may be hidden")
        }
        action?.invoke()
    }
    val withNotificationPermission: (() -> Unit) -> Unit = { action ->
        val permission = PermissionController.notificationPermission()
        if (permission == null ||
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingNotificationAction = action
            notificationPermissionLauncher.launch(permission)
        }
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
        val ambiguity = pending.ambiguous.firstOrNull()
        if (ambiguity != null) {
            AlertDialog(
                onDismissRequest = {},
                icon = { Icon(Icons.Default.LibraryMusic, null) },
                title = { Text("Choose playlist match") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            ambiguity.entry.displayTitle
                                ?: ambiguity.entry.reference.substringAfterLast('/').substringAfterLast('\\'),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Several library songs match this entry. Choose the exact song; Unison will not guess.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(ambiguity.candidates, key = { it.trackId.value }) { candidate ->
                                ListItem(
                                    modifier = Modifier.clickable {
                                        viewModel.choosePendingM3uCandidate(ambiguity.entryIndex, candidate.trackId)
                                    },
                                    headlineContent = { Text(candidate.displayTitle) },
                                    supportingContent = {
                                        Text(
                                            listOfNotNull(
                                                candidate.artist?.takeIf(String::isNotBlank),
                                                candidate.album?.takeIf(String::isNotBlank),
                                                formatDuration(candidate.durationMs),
                                            ).joinToString(" • "),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingContent = { TrackArtwork(candidate, 44.dp) },
                                )
                                HorizontalDivider()
                            }
                        }
                        Text(
                            "${pending.ambiguous.size} ambiguous • ${pending.unresolved.size} unavailable",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { viewModel.skipPendingM3uAmbiguity(ambiguity.entryIndex) }) {
                        Text("Skip this entry")
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = {},
                icon = { Icon(Icons.Default.FolderOpen, null) },
                title = { Text("Find playlist music") },
                text = {
                    Text(
                        "${pending.unresolved.size} ${if (pending.unresolved.size == 1) "song needs" else "songs need"} their music folder. Folder scanning is cancellable and unsafe paths are ignored."
                    )
                },
                confirmButton = { Button(onClick = { m3uFolderLauncher.launch(null) }) { Text("Choose folder") } },
                dismissButton = {
                    TextButton(onClick = viewModel::finishPendingM3uWithoutFolder) { Text("Skip missing") }
                },
            )
        }
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
                            onCreate = { name ->
                                withNotificationPermission { viewModel.command(AppCommand.CreateRoom(name)) }
                            },
                            onDiscover = { viewModel.command(AppCommand.StartDiscovery) },
                            onJoin = { room, pin ->
                                withNotificationPermission { viewModel.command(AppCommand.JoinRoom(room, pin)) }
                            },
                            onOfflineNetwork = {
                                withNotificationPermission {
                                    val missing = PermissionController.offlineNetworkPermissions().filter {
                                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                    }
                                    if (missing.isEmpty()) viewModel.command(AppCommand.CreateOfflineNetwork)
                                    else {
                                        startHotspotAfterPermission = true
                                        hotspotPermissionLauncher.launch(missing.toTypedArray())
                                    }
                                }
                            },
                            onStopOfflineNetwork = { viewModel.command(AppCommand.StopOfflineNetwork) },
                            onEditName = { showNameEdit = true },
                        )
                    } else RoomScreen(
                        state = ui,
                        playbackPositionMs = playbackPositionMs,
                        joinLink = viewModel.joinLink(),
                        onPlay = { withNotificationPermission { viewModel.command(AppCommand.Play) } },
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
