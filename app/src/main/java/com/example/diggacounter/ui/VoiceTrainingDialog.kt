package com.example.diggacounter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.diggacounter.speech.VoiceTrainer
import kotlinx.coroutines.launch

/** Records a few seconds of the given person's voice and saves an Eagle voice profile for them. */
@Composable
fun VoiceTrainingDialog(
    personId: Long,
    personName: String,
    onDone: (path: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0f) }
    var started by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stimme von $personName trainieren") },
        text = {
            Column {
                Text("Lass $personName ein paar Sätze sprechen, bis der Balken voll ist.")
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("${progress.toInt()} %")
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !started,
                onClick = {
                    started = true
                    scope.launch {
                        try {
                            val file = VoiceTrainer.train(context, personId) { p -> progress = p }
                            onDone(file.absolutePath)
                        } catch (e: Exception) {
                            error = e.message ?: "Training fehlgeschlagen"
                            started = false
                        }
                    }
                }
            ) { Text("Aufnahme starten") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
