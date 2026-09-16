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
import com.example.diggacounter.data.PersonRepository
import com.example.diggacounter.speech.SpeakerIdentifier
import com.example.diggacounter.speech.SpeakerTracker
import kotlinx.coroutines.*

/**
 * Foreground service that listens continuously using Android's built-in SpeechRecognizer
 * (free, no Google Cloud account needed - prefers fully offline on-device recognition
 * where the device has an offline language pack installed, otherwise falls back to
 * Google's standard free voice recognition).
 *
 * Speaker identification runs from a *separate*, independent microphone recording
 * ([SpeakerTracker]) rather than the recognizer's own audio: [RecognitionListener.onBufferReceived]
 * is documented as optional and doesn't fire on every device, which left speaker ID
 * completely blind on some phones. When a recognized transcript contains "Digga" and the
 * tracker's rolling window confidently points to one enrolled person, that person is
 * booked 50 cents.
 */
class ListeningService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var repository: PersonRepository
    private var speechRecognizer: SpeechRecognizer? = null
    private var speakerIdentifier: SpeakerIdentifier? = null
    private var speakerTracker: SpeakerTracker? = null
    private val audioManager: AudioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    override fun onCreate() {
        super.onCreate()
        repository = PersonRepository(AppDatabase.get(this).personDao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (speechRecognizer == null) {
            serviceScope.launch {
                val persons = repository.getAll()
                val profileFiles = persons
                    .filter { it.voiceProfilePath != null }
                    .associate { it.id to java.io.File(it.voiceProfilePath!!) }

                withContext(Dispatchers.Main) {
                    try {
                        // No enrolled voices yet -> nobody to attribute "Digga" to.
                        if (profileFiles.isNotEmpty()) {
                            val identifier = SpeakerIdentifier(applicationContext, profileFiles)
                            speakerIdentifier = identifier
                            speakerTracker = SpeakerTracker(identifier, serviceScope).apply { start() }
                        }
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
        speakerTracker?.stop()
        speakerTracker = null
        serviceScope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        speakerIdentifier = null
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
            val triggered = alternatives.any { containsTriggerWord(it) }
            if (triggered) {
                val tracker = speakerTracker
                val matchedSpeaker = tracker?.mostLikelySpeaker()
                if (matchedSpeaker != null) {
                    serviceScope.launch { repository.registerDigga(matchedSpeaker) }
                    updateNotification(heard, debugSuffix = " -> gebucht!")
                } else {
                    val identifier = speakerIdentifier
                    val reason = when {
                        identifier == null -> "keine Stimme trainiert"
                        tracker == null || tracker.framesCaptured == 0 -> "eigene Audioaufnahme liefert keine Daten"
                        else -> "beste Ähnlichkeit nur ${(identifier.lastBestScore * 100).toInt()}%"
                    }
                    updateNotification(heard, debugSuffix = " -> nicht gebucht ($reason)")
                }
            } else {
                updateNotification(heard)
            }
            listenOnce()
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
     * why "Digga" isn't being detected (wrong spelling heard, no speech captured, etc.). */
    private fun updateNotification(lastHeard: String?, debugSuffix: String = "") {
        val text = if (lastHeard.isNullOrBlank()) {
            "Hört zu und zählt 'Digga' pro Person"
        } else {
            "Zuletzt verstanden: \"$lastHeard\"$debugSuffix"
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
