package com.darius.unison.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.darius.unison.library.LibrarySort
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.sync.PlaybackSyncProfile

/** Out-of-room surface: rooms first, then a playlist-only music shelf. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    state: MainUiState,
    tracks: LazyPagingItems<TrackDescriptor>,
    onCreateRoom: (String?) -> Unit,
    onStartDiscovery: () -> Unit,
    onJoinRoom: (DiscoveredRoom, String) -> Unit,
    onCancelConnection: () -> Unit,
    onChooseFiles: () -> Unit,
    onImportM3u: () -> Unit,
    onEditName: () -> Unit,
    onSetPlaybackSyncProfile: (PlaybackSyncProfile) -> Unit,
    onCreateOfflineNetwork: () -> Unit,
    onStopOfflineNetwork: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: (String, List<TrackId>) -> Unit,
    onAddTracksToPlaylist: (String, List<TrackId>) -> Unit,
    onKeepTracks: (Set<TrackId>) -> Unit,
    onRemoveTemporaryTracks: (Set<TrackId>) -> Unit,
    onSelectAllTracks: (String, (Set<TrackId>) -> Unit) -> Unit,
    onClearTemporaryMusic: () -> Unit,
) {
    val listState = rememberLazyListState()
    var createRoomOpen by rememberSaveable { mutableStateOf(false) }
    var roomName by rememberSaveable { mutableStateOf("") }
    var joiningRoom by remember { mutableStateOf<DiscoveredRoom?>(null) }
    var joinPin by rememberSaveable { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }
    var musicMenuOpen by remember { mutableStateOf(false) }
    var storageOpen by remember { mutableStateOf(false) }
    var synchronizationOpen by remember { mutableStateOf(false) }
    var connectionHelpOpen by remember { mutableStateOf(false) }
    var confirmClearTemporary by remember { mutableStateOf(false) }
    var createPlaylistOpen by remember { mutableStateOf(false) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var allMusicOpen by rememberSaveable { mutableStateOf(false) }
    var discoveryRequestedOnEntry by rememberSaveable { mutableStateOf(false) }
    val connecting =
        state.room.lifecycle == RoomLifecycleState.PREPARING ||
            state.room.lifecycle == RoomLifecycleState.CONNECTING ||
            state.room.lifecycle == RoomLifecycleState.JOINING

    LaunchedEffect(state.room.lifecycle, discoveryRequestedOnEntry) {
        if (!discoveryRequestedOnEntry && state.room.lifecycle == RoomLifecycleState.IDLE) {
            discoveryRequestedOnEntry = true
            onStartDiscovery()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        stickyHeader(key = "app-bar") {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Unison",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                        DropdownMenu(
                            expanded = settingsOpen,
                            onDismissRequest = { settingsOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Change your name") },
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                onClick = {
                                    settingsOpen = false
                                    onEditName()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Playback synchronization") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = {
                                    settingsOpen = false
                                    synchronizationOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Connection help") },
                                leadingIcon = { Icon(Icons.Default.WifiTethering, null) },
                                onClick = {
                                    settingsOpen = false
                                    connectionHelpOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Storage") },
                                leadingIcon = { Icon(Icons.Default.Storage, null) },
                                onClick = {
                                    settingsOpen = false
                                    storageOpen = true
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "room-shelf-title") {
            Text(
                "Listen together",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        item(key = "create-room") {
            Button(
                onClick = { createRoomOpen = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                enabled = !connecting,
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(10.dp))
                Text("Create room", style = MaterialTheme.typography.titleMedium)
            }
        }

        item(key = "nearby-title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Nearby",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (state.room.lifecycle == RoomLifecycleState.DISCOVERING) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onStartDiscovery, enabled = !connecting) {
                        Icon(Icons.Default.Refresh, "Refresh nearby rooms")
                    }
                }
            }
        }

        if (connecting) {
            item(key = "connecting") {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Joining room…", modifier = Modifier.weight(1f))
                        TextButton(onClick = onCancelConnection) { Text("Cancel") }
                    }
                }
            }
        } else if (state.room.discoveredRooms.isEmpty()) {
            item(key = "nearby-empty") {
                Text(
                    if (state.room.lifecycle == RoomLifecycleState.DISCOVERING) "Looking nearby…"
                    else "No nearby rooms",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        } else {
            items(state.room.discoveredRooms, key = { "room:${it.roomId}" }) { room ->
                ListItem(
                    headlineContent = {
                        Text(
                            room.roomName,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = { Text("Nearby") },
                    leadingContent = { Icon(Icons.Default.Groups, null) },
                    trailingContent = {
                        FilledTonalButton(
                            onClick = {
                                joiningRoom = room
                                joinPin = ""
                            }
                        ) {
                            Text("Join")
                        }
                    },
                )
            }
        }

        state.room.hotspot?.let { hotspot ->
            item(key = "active-local-network") {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.WifiTethering,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Local connection active", fontWeight = FontWeight.SemiBold)
                            Text(hotspot.ssid, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = onStopOfflineNetwork) { Text("Stop") }
                    }
                }
            }
        }

        item(key = "music-divider") {
            Column(
                Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Your music",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Playlists and saved music",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            playlistName = ""
                            createPlaylistOpen = true
                        }
                    ) {
                        Icon(Icons.Default.Add, "New playlist")
                    }
                    Box {
                        IconButton(onClick = { musicMenuOpen = true }) {
                            Icon(Icons.Default.MoreVert, "Music actions")
                        }
                        DropdownMenu(
                            expanded = musicMenuOpen,
                            onDismissRequest = { musicMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add audio files") },
                                leadingIcon = { Icon(Icons.Default.AudioFile, null) },
                                onClick = {
                                    musicMenuOpen = false
                                    onChooseFiles()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Import playlist") },
                                leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                                onClick = {
                                    musicMenuOpen = false
                                    onImportM3u()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Storage") },
                                leadingIcon = { Icon(Icons.Default.Storage, null) },
                                onClick = {
                                    musicMenuOpen = false
                                    storageOpen = true
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "all-music") {
            PlaylistShelfRow(
                name = "All Music",
                count = state.libraryTotalCount,
                onClick = { allMusicOpen = true },
            )
        }

        items(state.playlists, key = { "playlist:${it.playlistId}" }) { playlist ->
            PlaylistShelfRow(
                name = playlist.name,
                count = playlist.trackCount,
                onClick = { onOpenPlaylist(playlist.playlistId) },
            )
        }

        if (state.playlists.isEmpty()) {
            item(key = "playlist-empty") {
                Text(
                    "Create playlists to organize your music.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
    }

    if (allMusicOpen) {
        AllMusicSheet(
            tracks = tracks,
            query = state.libraryQuery,
            sort = state.librarySort,
            totalCount = state.libraryTotalCount,
            temporaryTrackIds = state.temporaryTrackIds,
            playlists = state.playlists,
            onQueryChange = onQueryChange,
            onSortChange = onSortChange,
            onChooseFiles = onChooseFiles,
            onAddTracksToPlaylist = onAddTracksToPlaylist,
            onKeepTracks = onKeepTracks,
            onRemoveTemporaryTracks = onRemoveTemporaryTracks,
            onSelectAll = onSelectAllTracks,
            onDismiss = {
                onQueryChange("")
                allMusicOpen = false
            },
        )
    }

    if (createRoomOpen) {
        AlertDialog(
            onDismissRequest = { createRoomOpen = false },
            title = { Text("Create room") },
            text = {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it.take(60) },
                    label = { Text("Room name") },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        createRoomOpen = false
                        onCreateRoom(roomName.trim().takeIf(String::isNotEmpty))
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton(onClick = { createRoomOpen = false }) { Text("Cancel") } },
        )
    }

    joiningRoom?.let { room ->
        AlertDialog(
            onDismissRequest = { joiningRoom = null },
            title = { Text(room.roomName) },
            text = {
                OutlinedTextField(
                    value = joinPin,
                    onValueChange = { value -> joinPin = value.filter(Char::isDigit).take(4) },
                    label = { Text("4-digit room code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        joiningRoom = null
                        onJoinRoom(room, joinPin)
                    },
                    enabled = joinPin.length == 4,
                ) {
                    Text("Join")
                }
            },
            dismissButton = { TextButton(onClick = { joiningRoom = null }) { Text("Cancel") } },
        )
    }

    if (createPlaylistOpen) {
        AlertDialog(
            onDismissRequest = { createPlaylistOpen = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it.take(60) },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(playlistName.trim(), emptyList())
                        createPlaylistOpen = false
                    },
                    enabled = playlistName.isNotBlank(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { createPlaylistOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (synchronizationOpen) {
        PlaybackSynchronizationDialog(
            initialProfile = state.playbackSyncProfile,
            onSave = { profile ->
                synchronizationOpen = false
                onSetPlaybackSyncProfile(profile)
            },
            onDismiss = { synchronizationOpen = false },
        )
    }

    if (connectionHelpOpen) {
        AlertDialog(
            onDismissRequest = { connectionHelpOpen = false },
            icon = { Icon(Icons.Default.WifiTethering, null) },
            title = { Text("Connect nearby phones") },
            text = {
                Text(
                    "Unison normally uses the Wi-Fi network you are already connected to. If there is no router, this phone can create a private local connection for the room."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        connectionHelpOpen = false
                        if (state.room.hotspot == null) onCreateOfflineNetwork()
                        else onStopOfflineNetwork()
                    }
                ) {
                    Text(
                        if (state.room.hotspot == null) "Create local connection"
                        else "Stop local connection"
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { connectionHelpOpen = false }) { Text("Done") }
            },
        )
    }

    if (storageOpen) {
        AlertDialog(
            onDismissRequest = { storageOpen = false },
            icon = { Icon(Icons.Default.Storage, null) },
            title = { Text("Storage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageLine("Used by Unison", state.storageSummary.totalBytes)
                    StorageLine("Kept music", state.storageSummary.keptBytes)
                    StorageLine("Temporary music", state.storageSummary.temporaryBytes)
                }
            },
            confirmButton = { TextButton(onClick = { storageOpen = false }) { Text("Done") } },
            dismissButton = {
                TextButton(
                    onClick = {
                        storageOpen = false
                        confirmClearTemporary = true
                    },
                    enabled = state.storageSummary.temporaryBytes > 0,
                ) {
                    Text("Clear temporary")
                }
            },
        )
    }

    if (confirmClearTemporary) {
        AlertDialog(
            onDismissRequest = { confirmClearTemporary = false },
            title = { Text("Clear temporary music?") },
            text = { Text("Songs used by an active room are kept available.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearTemporary = false
                        onClearTemporaryMusic()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearTemporary = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistShelfRow(name: String, count: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("$count ${if (count == 1) "song" else "songs"}") },
        leadingContent = { Icon(Icons.Default.LibraryMusic, null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(Modifier.padding(start = 56.dp))
}
