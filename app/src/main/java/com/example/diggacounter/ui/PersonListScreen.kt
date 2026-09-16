package com.example.diggacounter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.diggacounter.data.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonListScreen(
    persons: List<Person>,
    isListening: Boolean,
    onAddPerson: (String) -> Unit,
    onDeletePerson: (Person) -> Unit,
    onAdjust: (Person, Int) -> Unit,
    onToggleListening: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Digga Counter") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Person hinzufügen")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Button(
                onClick = onToggleListening,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Icon(if (isListening) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isListening) "Zuhören stoppen" else "Zuhören starten")
            }

            if (persons.size > 1) {
                Text(
                    text = "Bei mehreren Personen fragt eine Benachrichtigung nach, wer \"Digga\" gesagt hat.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(persons, key = { it.id }) { person ->
                    PersonRow(
                        person = person,
                        onAdjust = { steps -> onAdjust(person, steps) },
                        onDelete = { onDeletePerson(person) }
                    )
                    Divider()
                }
            }
        }
    }

    if (showAddDialog) {
        AddPersonDialog(
            onConfirm = { name -> onAddPerson(name); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun PersonRow(
    person: Person,
    onAdjust: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = formatEuro(person.balanceCents),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        OutlinedButton(onClick = { onAdjust(-1) }) { Text("−50¢") }
        Spacer(Modifier.width(4.dp))
        Button(onClick = { onAdjust(1) }) { Text("+50¢") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Löschen")
        }
    }
}

@Composable
private fun AddPersonDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Person") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

fun formatEuro(cents: Int): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    return "$sign${abs / 100},${(abs % 100).toString().padStart(2, '0')} €"
}
