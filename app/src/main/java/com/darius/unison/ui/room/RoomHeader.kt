package com.darius.unison.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/** Room identity and infrequent room-level actions, kept outside the queue's hot rendering path. */
@Composable
internal fun RoomHeader(
    roomName: String,
    connectedListeners: Int,
    canShowRoomCode: Boolean,
    canSaveQueue: Boolean,
    canClearPlayed: Boolean,
    onShowRoomCode: () -> Unit,
    onShowListeners: () -> Unit,
    onShowLogs: () -> Unit,
    onSaveQueue: () -> Unit,
    onClearPlayed: () -> Unit,
    onShowSettings: () -> Unit,
    onShowAbout: () -> Unit,
    onLeave: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    fun runAndClose(action: () -> Unit) {
        menuOpen = false
        action()
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                roomName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$connectedListeners listening",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Room actions") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (canShowRoomCode) {
                    DropdownMenuItem(
                        text = { Text("Show room code") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { runAndClose(onShowRoomCode) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Listeners") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    onClick = { runAndClose(onShowListeners) },
                )
                DropdownMenuItem(
                    text = { Text("Room logs") },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { runAndClose(onShowLogs) },
                )
                DropdownMenuItem(
                    text = { Text("Save queue as playlist") },
                    enabled = canSaveQueue,
                    onClick = { runAndClose(onSaveQueue) },
                )
                DropdownMenuItem(
                    text = { Text("Clear played songs") },
                    enabled = canClearPlayed,
                    onClick = { runAndClose(onClearPlayed) },
                )
                DropdownMenuItem(
                    text = { Text("Room settings") },
                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                    onClick = { runAndClose(onShowSettings) },
                )
                DropdownMenuItem(
                    text = { Text("About Unison") },
                    leadingIcon = { Icon(Icons.Default.Code, null) },
                    onClick = { runAndClose(onShowAbout) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Leave room") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                    onClick = { runAndClose(onLeave) },
                )
            }
        }
    }
}
