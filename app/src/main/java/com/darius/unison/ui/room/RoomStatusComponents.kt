package com.darius.unison.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.darius.unison.R
import com.darius.unison.model.RoomIssue
import com.darius.unison.model.RoomIssueSeverity
import com.darius.unison.model.TransportCommandStatus

@Composable
internal fun PersistentRoomIssueCard(
    issue: RoomIssue,
    transportStatus: TransportCommandStatus?,
    onDismiss: () -> Unit,
    onRetryTransport: () -> Unit,
    onChooseFiles: () -> Unit,
    onLeaveRoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = RoomPlaybackUiPolicy.issueAction(issue, transportStatus)
    val presentation = RoomPlaybackUiPolicy.issuePresentation(issue)
    val icon =
        when (issue.severity) {
            RoomIssueSeverity.INFO -> Icons.Default.Info
            RoomIssueSeverity.WARNING -> Icons.Default.Warning
            RoomIssueSeverity.ERROR -> Icons.Default.ErrorOutline
        }
    val colors =
        when (issue.severity) {
            RoomIssueSeverity.INFO ->
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )

            RoomIssueSeverity.WARNING ->
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )

            RoomIssueSeverity.ERROR ->
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
        }

    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
        colors = colors,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(presentation.message, style = MaterialTheme.typography.bodyMedium)
                when (action) {
                    RoomPlaybackUiPolicy.IssueAction.RETRY_TRANSPORT ->
                        TextButton(onClick = onRetryTransport) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(stringResource(R.string.action_try_again))
                        }

                    RoomPlaybackUiPolicy.IssueAction.CHOOSE_FILES ->
                        TextButton(onClick = onChooseFiles) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(stringResource(R.string.action_choose_files))
                        }

                    RoomPlaybackUiPolicy.IssueAction.LEAVE_ROOM ->
                        TextButton(onClick = onLeaveRoom) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(stringResource(R.string.action_leave_room))
                        }

                    null -> Unit
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, stringResource(R.string.action_dismiss))
            }
        }
    }
}
