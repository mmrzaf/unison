package com.darius.unison.ui

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.darius.unison.model.AppCommand
import com.darius.unison.model.RoomJoinCredential

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnisonApp(viewModel: MainViewModel) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var showNameEdit by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var importToRoom by rememberSaveable { mutableStateOf(false) }
    val filesLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) viewModel.importMusic(uris, importToRoom)
        }
    val m3uLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.importM3u(it, importToRoom) }
        }
    val m3uFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(viewModel::resolvePendingM3u)
        }
    var exportPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(
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
    val hotspotPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            grants ->
            if (grants.values.all { it } && startHotspotAfterPermission) {
                viewModel.command(AppCommand.CreateOfflineNetwork)
            } else if (startHotspotAfterPermission) {
                viewModel.showMessage("Nearby Wi-Fi access is needed to create an offline network")
            }
            startHotspotAfterPermission = false
        }
    val createOfflineNetwork = {
        val missing =
            PermissionController.offlineNetworkPermissions().filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
        if (missing.isEmpty()) {
            viewModel.command(AppCommand.CreateOfflineNetwork)
        } else {
            startHotspotAfterPermission = true
            hotspotPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(ui.message) {
        val value = ui.message
        if (!value.isNullOrBlank()) {
            snackbar.showSnackbar(value)
            viewModel.clearMessage(value)
        }
    }
    LaunchedEffect(ui.room.errorMessage, ui.room.issue) {
        val value = ui.room.errorMessage
        if (ui.room.issue == null && !value.isNullOrBlank()) {
            snackbar.showSnackbar(value)
            viewModel.clearRoomError(value)
        }
    }

    if (!ui.settingsLoaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
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

    ui.pendingShare?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.resolvePendingShare(null) },
            title = { Text("Add shared music") },
            text = {
                Text(
                    "Choose where to add ${if (pending.isM3u) "this playlist" else "the selected music"}."
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.resolvePendingShare(ShareDestination.ROOM) }) {
                    Text("Room")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { viewModel.resolvePendingShare(ShareDestination.LIBRARY) }
                    ) {
                        Text("Library")
                    }
                    if (!pending.isM3u) {
                        TextButton(
                            onClick = { viewModel.resolvePendingShare(ShareDestination.BOTH) }
                        ) {
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
                                ?: ambiguity.entry.reference
                                    .substringAfterLast('/')
                                    .substringAfterLast('\\'),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Several library songs match this entry. Choose the exact song.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(ambiguity.candidates, key = { it.trackId.value }) { candidate ->
                                ListItem(
                                    modifier =
                                        Modifier.clickable {
                                            viewModel.choosePendingM3uCandidate(
                                                ambiguity.entryIndex,
                                                candidate.trackId,
                                            )
                                        },
                                    headlineContent = { Text(candidate.displayTitle) },
                                    supportingContent = {
                                        Text(
                                            listOfNotNull(
                                                    candidate.artist?.takeIf(String::isNotBlank),
                                                    candidate.album?.takeIf(String::isNotBlank),
                                                    formatDuration(candidate.durationMs),
                                                )
                                                .joinToString(" • "),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.skipPendingM3uAmbiguity(ambiguity.entryIndex) }
                    ) {
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
                        "${pending.unresolved.size} ${if (pending.unresolved.size == 1) "song needs" else "songs need"} their music folder."
                    )
                },
                confirmButton = {
                    Button(onClick = { m3uFolderLauncher.launch(null) }) { Text("Choose folder") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::finishPendingM3uWithoutFolder) {
                        Text("Skip missing")
                    }
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (ui.room.snapshot == null) {
                val libraryTracks = viewModel.libraryTracks.collectAsLazyPagingItems()
                HomeScreen(
                    state = ui,
                    tracks = libraryTracks,
                    onCreateRoom = { viewModel.command(AppCommand.CreateRoom(it)) },
                    onStartDiscovery = {
                        viewModel.command(AppCommand.StartDiscovery, feedback = null)
                    },
                    onJoinRoom = { room, pin ->
                        viewModel.command(
                            AppCommand.JoinRoom(room, RoomJoinCredential.Pin(pin)),
                            feedback = null,
                        )
                    },
                    onCancelConnection = {
                        viewModel.command(AppCommand.LeaveRoom, feedback = null)
                    },
                    onChooseFiles = {
                        importToRoom = false
                        filesLauncher.launch(arrayOf("audio/*"))
                    },
                    onImportM3u = {
                        importToRoom = false
                        m3uLauncher.launch(M3U_TYPES)
                    },
                    onEditName = { showNameEdit = true },
                    onSetPlaybackSyncProfile = viewModel::setPlaybackSyncProfile,
                    onCreateOfflineNetwork = createOfflineNetwork,
                    onStopOfflineNetwork = { viewModel.command(AppCommand.StopOfflineNetwork) },
                    onQueryChange = viewModel::setLibraryQuery,
                    onSortChange = viewModel::setLibrarySort,
                    onOpenPlaylist = viewModel::openPlaylist,
                    onCreatePlaylist = viewModel::createPlaylist,
                    onAddTracksToPlaylist = viewModel::addTracksToPlaylist,
                    onKeepTracks = viewModel::keepTracks,
                    onRemoveTemporaryTracks = viewModel::removeTemporaryTracks,
                    onSelectAllTracks = viewModel::loadTrackIds,
                    onClearTemporaryMusic = viewModel::clearTemporaryMusic,
                )
            } else {
                SharedRoomScreen(
                    room = ui.room,
                    playlists = ui.playlists,
                    libraryTotalCount = ui.libraryTotalCount,
                    temporaryTrackIds = ui.temporaryTrackIds,
                    retentionPolicy = ui.retentionPolicy,
                    playbackSyncProfile = ui.playbackSyncProfile,
                    playbackPositionFlow = viewModel.playbackPositionMs,
                    pickerTracksFlow = viewModel.pickerTracks,
                    pickerQueryState = viewModel.pickerQuery,
                    diagnosticRevision = viewModel.diagnosticRevision,
                    actions =
                        remember(viewModel, filesLauncher, m3uLauncher) {
                            SharedRoomActions(
                                loadRoomLogs = viewModel::roomLogEvents,
                                onPickerQueryChange = viewModel::setPickerQuery,
                                onPlay = { viewModel.command(AppCommand.Play()) },
                                onPause = { viewModel.command(AppCommand.Pause()) },
                                onSeek = { viewModel.command(AppCommand.Seek(it)) },
                                onNext = { viewModel.command(AppCommand.SkipNext()) },
                                onPrevious = { viewModel.command(AppCommand.SkipPrevious()) },
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
                                onSelectAllTracks = viewModel::loadRoomTrackIds,
                                onAddLibrarySelectionToRoom = viewModel::addLibrarySelectionToRoom,
                                onRemoveQueueItem = { viewModel.command(AppCommand.RemoveQueueItem(it)) },
                                onMoveQueueItem = { item, index ->
                                    viewModel.command(AppCommand.MoveQueueItem(item, index))
                                },
                                onMoveQueueItemNext = {
                                    viewModel.command(AppCommand.MoveQueueItemNext(it))
                                },
                                onKeepTrack = viewModel::keepTrack,
                                onUpdateOptions = { viewModel.command(AppCommand.UpdateRoomOptions(it)) },
                                onSetRetentionPolicy = viewModel::setRetentionPolicy,
                                onSetPlaybackSyncProfile = viewModel::setPlaybackSyncProfile,
                                onSaveQueue = { name, ids ->
                                    if (ids.isNotEmpty()) viewModel.createPlaylist(name, ids)
                                },
                                onClearPlayed = { viewModel.command(AppCommand.ClearPlayed) },
                                onClearQueue = { viewModel.command(AppCommand.ClearQueue) },
                                onLeave = { viewModel.command(AppCommand.LeaveRoom) },
                                onRetryIssue = viewModel::retryRoomIssue,
                                onDismissIssue = viewModel::clearRoomError,
                            )
                        },
                )
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

    ui.selectedPlaylist?.let { playlist ->
        val pickerQuery by viewModel.pickerQuery.collectAsStateWithLifecycle()
        val pickerTracks = viewModel.pickerTracks.collectAsLazyPagingItems()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closePlaylist,
            sheetState = sheetState,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::closePlaylist) {
                        Icon(Icons.Default.Close, "Close playlist")
                    }
                }
                Box(Modifier.weight(1f)) {
                    PlaylistDetailScreen(
                        detail = playlist,
                        pickerTracks = pickerTracks,
                        pickerQuery = pickerQuery,
                        onPickerQueryChange = viewModel::setPickerQuery,
                        roomActive = ui.room.snapshot != null,
                        onRename = { viewModel.renamePlaylist(playlist.playlistId, it) },
                        onMoveTrack = { from, to ->
                            viewModel.movePlaylistTrack(playlist.playlistId, from, to)
                        },
                        onRemoveTracks = { indices ->
                            viewModel.removePlaylistTracks(playlist.playlistId, indices)
                        },
                        onAddTracks = { viewModel.addTracksToPlaylist(playlist.playlistId, it) },
                        onAddToRoom = { viewModel.addPlaylistToRoom(playlist.playlistId) },
                        onAddTracksToRoom = viewModel::addTracksToRoom,
                        onSelectAll = viewModel::loadTrackIds,
                        onExport = { startPlaylistExport(playlist.playlistId, playlist.name) },
                        onDelete = { viewModel.deletePlaylist(playlist.playlistId) },
                    )
                }
            }
        }
    }
}
