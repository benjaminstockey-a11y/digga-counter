package com.example.diggacounter

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.diggacounter.data.Person
import com.example.diggacounter.listening.ListeningService
import com.example.diggacounter.ui.DiggaViewModel
import com.example.diggacounter.ui.PersonListScreen
import com.example.diggacounter.ui.UpdateAvailableDialog
import com.example.diggacounter.ui.VoiceTrainingDialog
import com.example.diggacounter.update.ApkInstaller
import com.example.diggacounter.update.UpdateChecker
import com.example.diggacounter.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: DiggaViewModel by viewModels()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handled by hasRequiredPermissions() check before starting */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        requestPermissions.launch(needed.toTypedArray())

        setContent {
            var isListening by remember { mutableStateOf(false) }
            var trainingPerson by remember { mutableStateOf<Person?>(null) }
            var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
            val persons by viewModel.persons.collectAsState()
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                availableUpdate = withContext(Dispatchers.IO) {
                    runCatching { UpdateChecker.checkForUpdate() }.getOrNull()
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier) {
                    PersonListScreen(
                        persons = persons,
                        isListening = isListening,
                        onAddPerson = viewModel::addPerson,
                        onDeletePerson = viewModel::deletePerson,
                        onAdjust = { person, steps -> viewModel.adjustBalance(person.id, steps) },
                        onTrainVoice = { person -> trainingPerson = person },
                        onToggleListening = {
                            if (!hasRequiredPermissions()) {
                                requestPermissions.launch(
                                    arrayOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                )
                                return@PersonListScreen
                            }
                            isListening = !isListening
                            if (isListening) {
                                ListeningService.start(this)
                                // Jump straight to the "Do Not Disturb access" settings screen
                                // if not granted yet - that's what lets the app silence the
                                // recognizer's start/stop beep.
                                if (!hasDndAccess()) openDndAccessSettings()
                            } else {
                                ListeningService.stop(this)
                            }
                        }
                    )

                    trainingPerson?.let { person ->
                        VoiceTrainingDialog(
                            personId = person.id,
                            personName = person.name,
                            onDone = { path ->
                                viewModel.setVoiceProfilePath(person.id, path)
                                trainingPerson = null
                            },
                            onDismiss = { trainingPerson = null }
                        )
                    }

                    availableUpdate?.let { update ->
                        UpdateAvailableDialog(
                            update = update,
                            onInstall = {
                                scope.launch(Dispatchers.IO) {
                                    UpdateChecker.downloadAndInstall(this@MainActivity, update)
                                }
                                availableUpdate = null
                            },
                            onDismiss = { availableUpdate = null }
                        )
                    }
                }
            }
        }
    }

    /** Call once the APK finishes downloading (e.g. from a notification tap) to launch the installer. */
    fun installPendingUpdate() = ApkInstaller.installDownloaded(this)

    private fun hasRequiredPermissions(): Boolean {
        val recordOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val notifOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        return recordOk && notifOk
    }

    private fun hasDndAccess(): Boolean {
        val manager = getSystemService(NotificationManager::class.java) ?: return false
        return manager.isNotificationPolicyAccessGranted
    }

    /** Android has no public API to deep-link straight to one app's own DND-access toggle -
     * only this general list of every app that has requested it. So we tell people exactly
     * what to look for right before handing them off to it. */
    private fun openDndAccessSettings() {
        android.widget.Toast.makeText(
            this,
            "Gleich öffnet sich eine Liste - \"Digga Counter\" suchen und den Schalter aktivieren.",
            android.widget.Toast.LENGTH_LONG
        ).show()
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }
}
