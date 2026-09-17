package com.example.diggacounter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.diggacounter.speech.WakeWordTrainer
import kotlinx.coroutines.launch

/** Records the person saying "Digga" a few times in isolation, building templates the
 * live detector later matches against - this both recognizes the word and who said it. */
@Composable
fun WakeWordTrainingDialog(
    personId: Long,
    personName: String,
    onDone: (path: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recorded by remember { mutableStateOf(0) }
    var started by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\"Digga\" von $personName trainieren") },
        text = {
            Column {
                Text(
                    "Nach \"Aufnahme starten\" soll $personName einfach nur \"Digga\" sagen, " +
                        "dabei kurz pausieren, dann nochmal - insgesamt ${WakeWordTrainer.TEMPLATE_COUNT} mal. " +
                        "Immer normal und deutlich, wie später auch im Alltag."
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = recorded / WakeWordTrainer.TEMPLATE_COUNT.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("$recorded / ${WakeWordTrainer.TEMPLATE_COUNT} aufgenommen")
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
                            val file = WakeWordTrainer.train(context, personId) { count -> recorded = count }
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
