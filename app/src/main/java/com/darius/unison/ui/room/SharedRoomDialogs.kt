package com.darius.unison.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.darius.unison.model.MemberRuntimeState
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomLifecycleState
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
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomListenersSheet(
    members: List<MemberSnapshot>,
    memberRuntime: Map<PeerId, MemberRuntimeState>,
    localPeerId: PeerId?,
    isCoordinator: Boolean,
    lifecycle: RoomLifecycleState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SheetHeader(
                title = "Listeners",
                subtitle =
                    if (members.size == 1) "1 person in this room"
                    else "${members.size} people in this room",
                onClose = onDismiss,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                items(members, key = { it.peerId.value }) { member ->
                    val isLocal = member.peerId == localPeerId
                    val directlyDisconnected =
                        isCoordinator && !isLocal && memberRuntime[member.peerId]?.connected == false
                    val status =
                        when {
                            isLocal && lifecycle == RoomLifecycleState.RECONNECTING -> "Reconnecting…"
                            directlyDisconnected -> "Reconnecting…"
                            isLocal -> "Listening · This phone"
                            else -> "Listening"
                        }
                    ListItem(
                        headlineContent = { Text(member.displayName) },
                        supportingContent = { Text(status) },
                        leadingContent = { TonalIcon(Icons.Default.Person, null) },
                    )
                }
            }
        }
    }
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
    onSave: (RoomOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var options by remember(initialOptions) { mutableStateOf(initialOptions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Room settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchRow("Wait for ready listeners before each song", options.waitAtTrackBoundary) {
                    options = options.copy(waitAtTrackBoundary = it)
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(options) }) { Text("Save") } },
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
