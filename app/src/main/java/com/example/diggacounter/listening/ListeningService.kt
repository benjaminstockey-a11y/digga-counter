package com.example.diggacounter.listening

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.example.diggacounter.Config
import com.example.diggacounter.R
import com.example.diggacounter.data.AppDatabase
import com.example.diggacounter.data.Person
import com.example.diggacounter.data.PersonRepository
import kotlinx.coroutines.*

/**
 * Foreground service that listens continuously using Android's built-in SpeechRecognizer
 * (free, no Google Cloud account needed - prefers fully offline on-device recognition
 * where the device has an offline language pack installed, otherwise falls back to
 * Google's standard free voice recognition).
 *
 * Automatic *speaker* identification turned out to be unreliable across devices (the
 * recognizer's own audio buffer callback is optional and doesn't fire everywhere, and
 * recording the mic ourselves at the same time starves the recognizer of audio on devices
 * that don't support concurrent capture). So instead: when "Digga" is heard, if there's
 * exactly one enrolled person they're credited automatically; with several people, a
 * notification with one tap-to-confirm button per person is shown - reliable on every
 * device since it doesn't depend on voice matching at all.
 */
class ListeningService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var repository: PersonRepository
    private var speechRecognizer: SpeechRecognizer? = null
    private var knownPersons: List<Person> = emptyList()
    private val audioManager: AudioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var attributionNotificationId = ATTRIBUTION_NOTIFICATION_ID_BASE

    override fun onCreate() {
        super.onCreate()
        repository = PersonRepository(AppDatabase.get(this).personDao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (speechRecognizer == null) {
            serviceScope.launch {
                knownPersons = repository.getAll()
                withContext(Dispatchers.Main) {
                    try {
                        startRecognizer()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            this@ListeningService,
                            "Zuhören konnte nicht gestartet werden: ${e.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        setBeepMuted(false)
    }

    private fun startRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }
        listenOnce()
    }

    private fun listenOnce() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            // Prefer online recognition: it has a much bigger vocabulary than the offline
            // model and recognizes slang words like "Digga" more reliably.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        // Mute the beep Android plays when recognition starts - we restart listening
        // continuously, so without this it beeps constantly and sounds like it's
        // crashing/restarting. Unmuted again once recognition has actually started.
        setBeepMuted(true)
        speechRecognizer?.startListening(intent)
    }

    /** Best-effort mute/unmute of the stream the system recognition start/end beep plays
     * on - varies a bit by device/OEM, so we cover several candidates. Muting
     * STREAM_SYSTEM/STREAM_RING (where the beep usually actually lives) only works once
     * the user has granted "Do Not Disturb" access - without it Android silently ignores
     * the request instead of throwing, so there's nothing else to detect/react to here. */
    private fun setBeepMuted(muted: Boolean) {
        val direction = if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        val streams = mutableListOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION)
        if (hasDndAccess()) {
            streams += AudioManager.STREAM_SYSTEM
            streams += AudioManager.STREAM_RING
        }
        for (stream in streams) {
            try {
                audioManager.adjustStreamVolume(stream, direction, 0)
            } catch (e: Exception) {
                // Some devices/streams refuse programmatic mute - not worth crashing over.
            }
        }
    }

    private fun hasDndAccess(): Boolean {
        val manager = getSystemService(NotificationManager::class.java) ?: return false
        return manager.isNotificationPolicyAccessGranted
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            // Start beep has played (or was suppressed) by now - unmute so the person's
            // own environment sounds normal while they're actually talking.
            setBeepMuted(false)
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onEndOfSpeech() {
            // The end-of-speech beep plays right around here, before onResults/onError -
            // mute again so that one is suppressed too.
            setBeepMuted(true)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onError(error: Int) {
            setBeepMuted(false)
            // Recognizer stops listening on error (including plain silence timeouts) -
            // just restart so the service keeps listening continuously.
            mainHandler.postDelayed({ listenOnce() }, 250)
        }

        override fun onResults(results: Bundle?) {
            setBeepMuted(false)
            val alternatives = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.map { it.lowercase() }
                ?: emptyList()

            val heard = alternatives.firstOrNull()
            if (alternatives.any { containsTriggerWord(it) }) {
                handleTrigger()
                updateNotification(heard)
            } else {
                updateNotification(heard)
            }
            listenOnce()
        }
    }

    private fun handleTrigger() {
        when (knownPersons.size) {
            0 -> return // nobody to credit
            1 -> serviceScope.launch { repository.registerDigga(knownPersons.first().id) }
            else -> showAttributionPrompt()
        }
    }

    /** Notification with one tap-to-confirm action button per enrolled person - the
     * reliable fallback since automatic voice matching didn't hold up across devices. */
    private fun showAttributionPrompt() {
        val notificationId = attributionNotificationId++
        val manager = getSystemService(NotificationManager::class.java) ?: return
        ensureAttributionChannel(manager)

        val builder = NotificationCompat.Builder(this, ATTRIBUTION_CHANNEL_ID)
            .setContentTitle("Wer hat \"Digga\" gesagt?")
            .setContentText("Antippen um 50 Cent zu buchen")
            .setSmallIcon(R.drawable.ic_mic)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(30_000)

        // Notifications reliably render at most a handful of action buttons.
        for (person in knownPersons.take(4)) {
            val intent = Intent(this, DiggaAttributionReceiver::class.java).apply {
                putExtra(DiggaAttributionReceiver.EXTRA_PERSON_ID, person.id)
                putExtra(DiggaAttributionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                (notificationId * 100 + person.id).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "War ${person.name}", pendingIntent)
        }

        manager.notify(notificationId, builder.build())
    }

    private fun ensureAttributionChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(ATTRIBUTION_CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ATTRIBUTION_CHANNEL_ID,
                    "Digga-Zuordnung",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    /**
     * Android's speech recognizer often mishears slang like "Digga" - check the configured
     * trigger word plus a few common misheard spellings, not just an exact substring match.
     */
    private fun containsTriggerWord(transcript: String): Boolean {
        val variants = listOf(
            Config.TRIGGER_WORD, "digger", "dicka", "ticka", "diggah", "diggar",
            "dicker", "ticker", "diggeh", "diga", "digge"
        )
        return variants.any { transcript.contains(it) }
    }

    /** Shows the last thing Android's recognizer actually understood - handy for checking
     * whether "Digga" is being heard at all and roughly how it gets transcribed. */
    private fun updateNotification(lastHeard: String?) {
        val text = if (lastHeard.isNullOrBlank()) {
            "Hört zu und zählt 'Digga' pro Person"
        } else {
            "Zuletzt verstanden: \"$lastHeard\""
        }
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
        private const val ATTRIBUTION_NOTIFICATION_ID_BASE = 1000
        private const val ATTRIBUTION_CHANNEL_ID = "digga_attribution"

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
