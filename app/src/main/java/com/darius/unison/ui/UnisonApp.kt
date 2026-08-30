package com.darius.unison.ui

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.RoomJoinCredential

private sealed interface PendingNetworkPermissionAction {
    val scope: String
    val deniedMessage: String

    data class CreateRoom(val name: String?) : PendingNetworkPermissionAction {
        override val scope = "create_room"
        override val deniedMessage = "Nearby Wi-Fi access is needed to create a room"
    }

    data class JoinRoom(val room: DiscoveredRoom, val pin: String) :
        PendingNetworkPermissionAction {
        override val scope = "join_room"
        override val deniedMessage = "Nearby Wi-Fi access is needed to join a room"
    }

    data object CreateOfflineNetwork : PendingNetworkPermissionAction {
        override val scope = "create_offline_network"
        override val deniedMessage = "Nearby Wi-Fi access is needed to create an offline network"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnisonApp(viewModel: MainViewModel) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var showNameEdit by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var allMusicOpen by rememberSaveable { mutableStateOf(false) }

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

    var pendingNetworkPermissionAction by remember {
        mutableStateOf<PendingNetworkPermissionAction?>(null)
    }

    fun executeNetworkAction(action: PendingNetworkPermissionAction) {
        when (action) {
            is PendingNetworkPermissionAction.CreateRoom ->
                viewModel.command(AppCommand.CreateRoom(action.name))
            is PendingNetworkPermissionAction.JoinRoom ->
                viewModel.command(
                    AppCommand.JoinRoom(action.room, RoomJoinCredential.Pin(action.pin)),
                    feedback = null,
                )
            PendingNetworkPermissionAction.CreateOfflineNetwork ->
                viewModel.command(AppCommand.CreateOfflineNetwork)
        }
    }

    fun requiredPermissions(action: PendingNetworkPermissionAction): Array<String> =
        when (action) {
            is PendingNetworkPermissionAction.CreateRoom,
            is PendingNetworkPermissionAction.JoinRoom ->
                PermissionController.localNetworkPermissions()
            PendingNetworkPermissionAction.CreateOfflineNetwork ->
                PermissionController.offlineNetworkPermissions()
        }

    val networkPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            grants ->
            val pending = pendingNetworkPermissionAction ?: return@rememberLauncherForActivityResult
            pendingNetworkPermissionAction = null
            val granted =
                requiredPermissions(pending).all { permission ->
                    grants[permission] == true ||
                        ContextCompat.checkSelfPermission(context, permission) ==
                            PackageManager.PERMISSION_GRANTED
                }
            viewModel.reportPermissionResult(pending.scope, granted)
            if (granted) {
                executeNetworkAction(pending)
            } else {
                viewModel.showMessage(pending.deniedMessage)
            }
        }

    fun runWithNetworkPermissions(
        action: PendingNetworkPermissionAction,
        requiredPermissions: Array<String>,
    ) {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            executeNetworkAction(action)
            return
        }
        pendingNetworkPermissionAction = action
        networkPermissionLauncher.launch(missing.toTypedArray())
    }

    val createOfflineNetwork = {
        runWithNetworkPermissions(
            PendingNetworkPermissionAction.CreateOfflineNetwork,
            PermissionController.offlineNetworkPermissions(),
        )
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

    fun closePlaylistScreen() {
        viewModel.setPickerQuery("")
        viewModel.closePlaylist()
    }

    BackHandler(enabled = ui.room.snapshot == null && ui.selectedPlaylist != null) {
        closePlaylistScreen()
    }

    LaunchedEffect(ui.room.snapshot?.roomId) {
        if (ui.room.snapshot != null) {
            allMusicOpen = false
            if (ui.selectedPlaylist != null) closePlaylistScreen()
        }
    }

    ui.pendingMusicImport?.let { pending ->
        MusicDestinationSheet(
            pending = pending,
            playlists = ui.playlists,
            roomActive = ui.room.snapshot != null,
            onDismiss = { viewModel.resolvePendingImport(null) },
            onConfirm = viewModel::resolvePendingImport,
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
                when {
                    ui.selectedPlaylist != null -> {
                        val playlist = checkNotNull(ui.selectedPlaylist)
                        val pickerQuery by viewModel.pickerQuery.collectAsStateWithLifecycle()
                        val pickerTracks = viewModel.pickerTracks.collectAsLazyPagingItems()
                        Column(Modifier.fillMaxSize()) {
                            ScreenTopBar(
                                title = playlist.name,
                                subtitle =
                                    "${playlist.tracks.size} ${if (playlist.tracks.size == 1) "song" else "songs"}",
                                onBack = ::closePlaylistScreen,
                            )
                            Box(Modifier.weight(1f)) {
                                PlaylistDetailScreen(
                                    detail = playlist,
                                    playlists = ui.playlists,
                                    pickerTracks = pickerTracks,
                                    pickerQuery = pickerQuery,
                                    onPickerQueryChange = viewModel::setPickerQuery,
                                    onRename = {
                                        viewModel.renamePlaylist(playlist.playlistId, it)
                                    },
                                    onMoveTrack = { from, to ->
                                        viewModel.movePlaylistTrack(playlist.playlistId, from, to)
                                    },
                                    onRemoveTracks = { indices ->
                                        viewModel.removePlaylistTracks(playlist.playlistId, indices)
                                    },
                                    onAddTracks = {
                                        viewModel.addTracksToPlaylist(playlist.playlistId, it)
                                    },
                                    onAddTracksToPlaylists = viewModel::addTracksToPlaylists,
                                    onSelectAll = viewModel::loadTrackIds,
                                    onExport = {
                                        startPlaylistExport(playlist.playlistId, playlist.name)
                                    },
                                    onDelete = { viewModel.deletePlaylist(playlist.playlistId) },
                                )
                            }
                        }
                    }
                    allMusicOpen -> {
                        val libraryTracks = viewModel.libraryTracks.collectAsLazyPagingItems()
                        AllMusicScreen(
                            tracks = libraryTracks,
                            query = ui.libraryQuery,
                            sort = ui.librarySort,
                            totalCount = ui.libraryTotalCount,
                            temporaryTrackIds = ui.temporaryTrackIds,
                            playlists = ui.playlists,
                            onQueryChange = viewModel::setLibraryQuery,
                            onSortChange = viewModel::setLibrarySort,
                            onChooseFiles = {
                                importToRoom = false
                                filesLauncher.launch(arrayOf("audio/*"))
                            },
                            onAddTracksToPlaylists = viewModel::addTracksToPlaylists,
                            onKeepTracks = viewModel::keepTracks,
                            onRemoveTemporaryTracks = viewModel::removeTemporaryTracks,
                            onSelectAll = viewModel::loadTrackIds,
                            onBack = { allMusicOpen = false },
                        )
                    }
                    else ->
                        HomeScreen(
                            state = ui,
                            onCreateRoom = { name ->
                                runWithNetworkPermissions(
                                    PendingNetworkPermissionAction.CreateRoom(name),
                                    PermissionController.localNetworkPermissions(),
                                )
                            },
                            onDismissRoomIssue = viewModel::clearRoomError,
                            onStartDiscovery = {
                                viewModel.command(AppCommand.StartDiscovery, feedback = null)
                            },
                            onJoinRoom = { room, pin ->
                                runWithNetworkPermissions(
                                    PendingNetworkPermissionAction.JoinRoom(room, pin),
                                    PermissionController.localNetworkPermissions(),
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
                            onShowAbout = { showAbout = true },
                            onSetRetentionPolicy = viewModel::setRetentionPolicy,
                            onCreateOfflineNetwork = createOfflineNetwork,
                            onStopOfflineNetwork = {
                                viewModel.command(AppCommand.StopOfflineNetwork)
                            },
                            onOpenAllMusic = { allMusicOpen = true },
                            onOpenPlaylist = { playlistId ->
                                allMusicOpen = false
                                viewModel.openPlaylist(playlistId)
                            },
                            onCreatePlaylist = viewModel::createPlaylist,
                            onClearTemporaryMusic = viewModel::clearTemporaryMusic,
                        )
                }
            } else {
                SharedRoomScreen(
                    room = ui.room,
                    playlists = ui.playlists,
                    libraryTotalCount = ui.libraryTotalCount,
                    temporaryTrackIds = ui.temporaryTrackIds,
                    playbackPositionFlow = viewModel.playbackPositionMs,
                    pickerTracksFlow = viewModel.pickerTracks,
                    pickerQueryState = viewModel.pickerQuery,
                    diagnosticRevision = viewModel.diagnosticRevision,
                    actions =
                        remember(viewModel, filesLauncher, m3uLauncher) {
                            SharedRoomActions(
                                playback =
                                    RoomPlaybackActions(
                                        play = { viewModel.command(AppCommand.Play()) },
                                        pause = { viewModel.command(AppCommand.Pause()) },
                                        seek = { viewModel.command(AppCommand.Seek(it)) },
                                        next = { viewModel.command(AppCommand.SkipNext()) },
                                        previous = { viewModel.command(AppCommand.SkipPrevious()) },
                                        playQueueItem = {
                                            viewModel.command(AppCommand.PlayQueueItem(it))
                                        },
                                        prepareQueueItem = {
                                            viewModel.command(AppCommand.PrepareQueueItem(it))
                                        },
                                    ),
                                queue =
                                    RoomQueueActions(
                                        shuffle = { viewModel.command(AppCommand.ShuffleQueue) },
                                        repeat = { viewModel.command(AppCommand.SetRepeat(it)) },
                                        chooseFiles = {
                                            importToRoom = true
                                            filesLauncher.launch(arrayOf("audio/*"))
                                        },
                                        importM3u = {
                                            importToRoom = true
                                            m3uLauncher.launch(M3U_TYPES)
                                        },
                                        pickerQueryChange = viewModel::setPickerQuery,
                                        selectAllTracks = viewModel::loadRoomTrackIds,
                                        addLibrarySelectionToRoom =
                                            viewModel::addLibrarySelectionToRoom,
                                        removeQueueItem = {
                                            viewModel.command(AppCommand.RemoveQueueItem(it))
                                        },
                                        moveQueueItem = { item, index ->
                                            viewModel.command(AppCommand.MoveQueueItem(item, index))
                                        },
                                        moveQueueItemNext = {
                                            viewModel.command(AppCommand.MoveQueueItemNext(it))
                                        },
                                        keepTrack = viewModel::keepTrack,
                                        saveQueue = { name, ids ->
                                            if (ids.isNotEmpty())
                                                viewModel.createPlaylist(name, ids)
                                        },
                                        clearPlayed = { viewModel.command(AppCommand.ClearPlayed) },
                                        clearQueue = { viewModel.command(AppCommand.ClearQueue) },
                                    ),
                                session =
                                    RoomSessionUiActions(
                                        updateOptions = {
                                            viewModel.command(AppCommand.UpdateRoomOptions(it))
                                        },
                                        showAbout = { showAbout = true },
                                        leave = { viewModel.command(AppCommand.LeaveRoom) },
                                        retryIssue = viewModel::retryRoomIssue,
                                        dismissIssue = viewModel::clearRoomError,
                                    ),
                                diagnostics =
                                    RoomDiagnosticsActions(
                                        loadLogs = viewModel::roomLogEvents,
                                        clearLogs = viewModel::clearRoomLogs,
                                    ),
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

    if (showAbout) {
        AboutUnisonDialog(onDismiss = { showAbout = false })
    }
}
