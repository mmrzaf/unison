package com.darius.unison.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
internal fun NameDialog(
    initialName: String,
    dismissible: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                label = { Text("Shown to friends") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim().ifBlank { "Friend" }) }) {
                Text(if (dismissible) "Save" else "Continue")
            }
        },
        dismissButton = {
            if (dismissible) TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
