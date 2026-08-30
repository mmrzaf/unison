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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.TrackId

/** Out-of-room surface: rooms first, then a playlist-only music shelf. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: MainUiState,
    onCreateRoom: (String?) -> Unit,
    onStartDiscovery: () -> Unit,
    onJoinRoom: (DiscoveredRoom, String) -> Unit,
    onCancelConnection: () -> Unit,
    onChooseFiles: () -> Unit,
    onImportM3u: () -> Unit,
    onEditName: () -> Unit,
    onShowAbout: () -> Unit,
    onSetRetentionPolicy: (RetentionPolicy) -> Unit,
    onCreateOfflineNetwork: () -> Unit,
    onStopOfflineNetwork: () -> Unit,
    onOpenAllMusic: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: (String, List<TrackId>) -> Unit,
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
    var connectionHelpOpen by remember { mutableStateOf(false) }
    var confirmClearTemporary by remember { mutableStateOf(false) }
    var createPlaylistOpen by remember { mutableStateOf(false) }
    var playlistName by rememberSaveable { mutableStateOf("") }
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
            ScreenTopBar(title = "Unison") {
                IconButton(onClick = { settingsOpen = true }) {
                    Icon(Icons.Default.Settings, "Settings")
                }
            }
        }

        item(key = "room-shelf-title") {
            SectionHeader(
                title = "Listen together",
                subtitle = "Start a synchronized room or join someone nearby.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item(key = "create-room") {
            Surface(
                modifier =
                    Modifier.fillMaxWidth().clickable(enabled = !connecting) {
                        createRoomOpen = true
                    },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TonalIcon(Icons.Default.Groups, null)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Create a room",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Play music together on nearby phones.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
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
            SectionHeader(
                title = "Your music",
                subtitle =
                    "${state.libraryTotalCount} ${if (state.libraryTotalCount == 1) "song" else "songs"}",
                modifier = Modifier.padding(top = 22.dp),
                action = {
                    TextButton(onClick = onChooseFiles) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add music")
                    }
                    Box {
                        IconButton(onClick = { musicMenuOpen = true }) {
                            Icon(Icons.Default.MoreVert, "More music actions")
                        }
                        DropdownMenu(
                            expanded = musicMenuOpen,
                            onDismissRequest = { musicMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import playlist") },
                                leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                                onClick = {
                                    musicMenuOpen = false
                                    onImportM3u()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("New playlist") },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = {
                                    musicMenuOpen = false
                                    playlistName = ""
                                    createPlaylistOpen = true
                                },
                            )
                        }
                    }
                },
            )
        }

        item(key = "all-music") {
            PlaylistShelfRow(
                name = "All Music",
                count = state.libraryTotalCount,
                onClick = onOpenAllMusic,
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Create a playlist to organize music for later.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                playlistName = ""
                                createPlaylistOpen = true
                            }
                        ) {
                            Text("New playlist")
                        }
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Your name") },
                supportingContent = { Text(state.room.localIdentity?.displayName ?: "Friend") },
                leadingContent = { Icon(Icons.Default.Person, null) },
                modifier =
                    Modifier.clickable {
                        settingsOpen = false
                        onEditName()
                    },
            )
            ListItem(
                headlineContent = { Text("Storage") },
                supportingContent = { Text(formatBytes(state.storageSummary.totalBytes)) },
                leadingContent = { Icon(Icons.Default.Storage, null) },
                modifier =
                    Modifier.clickable {
                        settingsOpen = false
                        storageOpen = true
                    },
            )
            ListItem(
                headlineContent = { Text("Connection help") },
                leadingContent = { Icon(Icons.Default.WifiTethering, null) },
                modifier =
                    Modifier.clickable {
                        settingsOpen = false
                        connectionHelpOpen = true
                    },
            )
            ListItem(
                headlineContent = { Text("About Unison") },
                leadingContent = { Icon(Icons.Default.Code, null) },
                modifier =
                    Modifier.clickable {
                        settingsOpen = false
                        onShowAbout()
                    },
            )
            Spacer(Modifier.size(24.dp))
        }
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

    if (connectionHelpOpen) {
        AlertDialog(
            onDismissRequest = { connectionHelpOpen = false },
            icon = { Icon(Icons.Default.WifiTethering, null) },
            title = { Text("Connect nearby phones") },
            text = {
                Text(
                    "Unison normally uses the Wi-Fi network you are already connected to. On Android 13+ allow Nearby devices when prompted. If there is no router, this phone can create a private local connection for the room."
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StorageLine("Used by Unison", state.storageSummary.totalBytes)
                    StorageLine("Kept music", state.storageSummary.keptBytes)
                    StorageLine("Temporary music", state.storageSummary.temporaryBytes)
                    Text(
                        "Music received in rooms",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.retentionPolicy == RetentionPolicy.TEMPORARY_24_HOURS,
                            onClick = { onSetRetentionPolicy(RetentionPolicy.TEMPORARY_24_HOURS) },
                            label = { Text("Temporary") },
                        )
                        FilterChip(
                            selected = state.retentionPolicy == RetentionPolicy.KEEP_IN_LIBRARY,
                            onClick = { onSetRetentionPolicy(RetentionPolicy.KEEP_IN_LIBRARY) },
                            label = { Text("Keep in library") },
                        )
                    }
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
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TonalIcon(Icons.Default.LibraryMusic, null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$count ${if (count == 1) "song" else "songs"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
