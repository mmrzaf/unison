package com.darius.unison.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Music-first room identity. Healthy listener presence stays compact; details live in a sheet. */
@Composable
internal fun RoomHeader(
    roomName: String,
    connectedListeners: Int,
    canShowRoomCode: Boolean,
    onShowRoomCode: () -> Unit,
    onShowListeners: () -> Unit,
    onShowLogs: () -> Unit,
    onShowSettings: () -> Unit,
    onShowAbout: () -> Unit,
    onLeave: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    fun runAndClose(action: () -> Unit) {
        menuOpen = false
        action()
    }

    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                roomName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier =
                        Modifier.semantics { role = Role.Button }
                            .clickable(onClick = onShowListeners)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (connectedListeners == 1) "1 in room" else "$connectedListeners in room",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                    text = { Text("Diagnostics") },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { runAndClose(onShowLogs) },
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
