package com.darius.unison.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.darius.unison.model.TrackDescriptor

@Composable
internal fun TrackRow(
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
        modifier =
            if (selectionMode) {
                Modifier.clickable { onSelectionChange(!selected) }
            } else {
                Modifier
            },
        headlineContent = {
            Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                listOfNotNull(
                        track.artist?.takeIf(String::isNotBlank),
                        formatDuration(track.durationMs),
                    )
                    .joinToString(" • "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
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
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to queue")
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "More")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (roomActive) {
                                DropdownMenuItem(
                                    text = { Text("Play next") },
                                    onClick = {
                                        menu = false
                                        onPlayNext()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Add to playlist") },
                                onClick = {
                                    menu = false
                                    onAddToPlaylist()
                                },
                            )
                            if (temporary) {
                                DropdownMenuItem(
                                    text = { Text("Keep on this phone") },
                                    onClick = {
                                        menu = false
                                        onKeep()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove temporary copy") },
                                    onClick = {
                                        menu = false
                                        confirmRemove = true
                                    },
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
                Button(
                    onClick = {
                        confirmRemove = false
                        onRemove()
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            },
        )
    }
}
