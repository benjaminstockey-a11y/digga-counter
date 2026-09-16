package com.example.diggacounter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diggacounter.speech.VoiceTrainer
import kotlinx.coroutines.launch

/** Sentences to read aloud during training - deliberately include "Digga" several times so
 * the voiceprint captures how this person actually says the trigger word, plus a spread of
 * other sounds/vowels for a more general profile. */
private val TRAINING_PROMPTS = listOf(
    "Was geht, Digga?",
    "Digga, das ist doch mal eine geile App.",
    "Alter Digga, hör mal kurz zu.",
    "Der schnelle braune Fuchs springt über den faulen Hund.",
    "Digga, ich glaub das klappt richtig gut.",
    "Heute ist ein ziemlich schöner Tag, oder Digga?",
    "Digga, sag mal ehrlich, was denkst du gerade?",
    "Eins, zwei, drei, vier, fünf, Digga."
)

/** Records a few seconds of the given person's voice and saves a voice profile for them. */
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

    // Advance to the next prompt sentence roughly every 12% of progress.
    val promptIndex = (progress / 12f).toInt().coerceIn(0, TRAINING_PROMPTS.size - 1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stimme von $personName trainieren") },
        text = {
            Column {
                Text("Lass $personName folgende Sätze der Reihe nach vorlesen, ganz normal und deutlich, bis der Balken voll ist:")
                Spacer(Modifier.height(12.dp))
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (started) TRAINING_PROMPTS[promptIndex] else TRAINING_PROMPTS[0],
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
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
