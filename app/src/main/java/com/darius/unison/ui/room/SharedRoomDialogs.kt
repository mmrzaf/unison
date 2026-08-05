package com.darius.unison.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.RoomOptions

@Composable
internal fun RoomCodeDialog(
    roomCode: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(roomCode) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Room code") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    roomCode,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Text(
                    "Share this code with people nearby.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Unison room code", roomCode))
                    copied = true
                }
            ) {
                Icon(if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy, null)
                Text(if (copied) "Copied" else "Copy", modifier = Modifier.padding(start = 6.dp))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
internal fun RoomListenersDialog(
    members: List<MemberSnapshot>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Listeners") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(members, key = { it.peerId.value }) { member ->
                    ListItem(
                        headlineContent = { Text(member.displayName) },
                        supportingContent = {
                            Text(memberStatusLabel(member.connected, member.currentTrackState))
                        },
                        leadingContent = { Icon(Icons.Default.Person, null) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
internal fun SaveQueueDialog(
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save queue") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Playlist name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun RoomOptionsDialog(
    initialOptions: RoomOptions,
    initialRetention: RetentionPolicy,
    onSave: (RoomOptions, RetentionPolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    var options by remember(initialOptions) { mutableStateOf(initialOptions) }
    var retention by remember(initialRetention) { mutableStateOf(initialRetention) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Room settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchRow("Wait for everyone before each song", options.waitAtTrackBoundary) {
                    options = options.copy(waitAtTrackBoundary = it)
                }
                HorizontalDivider()
                Text("Music received by this phone", style = MaterialTheme.typography.labelLarge)
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
            Button(onClick = { onSave(options, retention) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun RoomConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}
