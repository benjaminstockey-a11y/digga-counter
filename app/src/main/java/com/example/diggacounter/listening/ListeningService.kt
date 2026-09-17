package com.example.diggacounter.listening

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.diggacounter.R
import com.example.diggacounter.data.AppDatabase
import com.example.diggacounter.data.PersonRepository
import com.example.diggacounter.speech.WakeWordDetector
import com.example.diggacounter.speech.WakeWordTemplates
import kotlinx.coroutines.*
import java.io.File

/**
 * Foreground service that listens continuously for the trigger word using our own
 * [WakeWordDetector] - no Google/Picovoice account, no cloud costs, and no conflicts with
 * any other recognizer since it's the sole owner of one microphone recording session.
 *
 * A template match already tells us *who* said the word (the template was recorded by
 * that specific person), so there's no separate speaker-ID step - a confident match books
 * 50 cents directly to that person.
 */
class ListeningService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var repository: PersonRepository
    private var detector: WakeWordDetector? = null
    private var personNamesById: Map<Long, String> = emptyMap()

    override fun onCreate() {
        super.onCreate()
        repository = PersonRepository(AppDatabase.get(this).personDao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (detector == null) {
            serviceScope.launch {
                val persons = repository.getAll()
                personNamesById = persons.associate { it.id to it.name }
                val templateFiles: Map<Long, File> = persons
                    .map { it.id to WakeWordTemplates.file(applicationContext, it.id) }
                    .filter { (_, file) -> file.exists() }
                    .toMap()

                withContext(Dispatchers.Main) {
                    if (templateFiles.isEmpty()) {
                        android.widget.Toast.makeText(
                            this@ListeningService,
                            "Für niemanden ist \"Digga\" trainiert - bitte erst über das Mikrofon-Icon trainieren.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        stopSelf()
                        return@withContext
                    }
                    detector = WakeWordDetector(
                        context = applicationContext,
                        enrolledPersons = templateFiles,
                        scope = serviceScope,
                        onMatch = { match ->
                            serviceScope.launch { repository.registerDigga(match.personId) }
                            val name = personNamesById[match.personId] ?: "?"
                            mainNotify("\"Digga\" erkannt -> $name gebucht! (Distanz ${"%.2f".format(match.distance)})")
                        },
                        onUtteranceAnalyzed = { bestDistance, frameCount ->
                            mainNotify(
                                "Hört zu - letzte Äußerung: $frameCount Frames, " +
                                    "beste Distanz ${"%.2f".format(bestDistance)} (Schwelle ${WakeWordDetector.MATCH_THRESHOLD})"
                            )
                        }
                    ).apply { start() }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        detector?.stop()
        detector = null
        serviceScope.cancel()
    }

    private fun mainNotify(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(contentText: String = "Hört zu und zählt 'Digga' pro Person"): Notification {
        val channelId = "digga_listening"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "Digga Counter", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Digga Counter läuft")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, ListeningService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListeningService::class.java))
        }
    }
}
