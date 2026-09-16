package com.example.diggacounter.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.diggacounter.update.UpdateInfo

@Composable
fun UpdateAvailableDialog(
    update: UpdateInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update verfügbar: ${update.versionName}") },
        text = { Text(update.notes.ifBlank { "Eine neue Version ist verfügbar." }) },
        confirmButton = {
            TextButton(onClick = onInstall) { Text("Herunterladen & installieren") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Später") }
        }
    )
}
