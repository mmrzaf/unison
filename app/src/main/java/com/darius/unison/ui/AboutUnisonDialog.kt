package com.darius.unison.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.darius.unison.BuildConfig
import com.darius.unison.R
import com.darius.unison.protocol.PROTOCOL_VERSION

/** Small release/about surface shared by Home and the active room menu. */
@Composable
internal fun AboutUnisonDialog(onDismiss: () -> Unit) {
    val sourceUrl = stringResource(R.string.source_repository_url)
    val uriHandler = LocalUriHandler.current
    val buildSuffix = if (BuildConfig.DEBUG) " · ${BuildConfig.BUILD_TYPE}" else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Code, null) },
        title = { Text("Unison") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Local-first synchronized music for nearby phones.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})$buildSuffix",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Protocol $PROTOCOL_VERSION")
                    Text("Android 11+ · release-tested on 11, 13, and 16")
                }
                Row(
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            runCatching { uriHandler.openUri(sourceUrl) }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Code, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Source code", fontWeight = FontWeight.SemiBold)
                        Text(
                            "github.com/mmrzaf/unison",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
