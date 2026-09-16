package com.example.diggacounter.listening

import android.app.*
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.*

/**
 * Foreground service that listens continuously using Android's built-in SpeechRecognizer
 * (free, no Google Cloud account needed - prefers fully offline on-device recognition
 * where the device has an offline language pack installed, otherwise falls back to
 * Google's standard free voice recognition).
 *
 * SpeechRecognizer also hands us the raw 16-bit PCM audio it's listening to via
 * [RecognitionListener.onBufferReceived] - we feed that same audio into our own
 * [SpeakerIdentifier] to figure out *who* is speaking, without needing a second
 * microphone recording session. When a recognized transcript contains "Digga" and a
 * speaker was confidently identified, that person is booked 50 cents.
 */
class ListeningService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var repository: PersonRepository
    private var speechRecognizer: SpeechRecognizer? = null
    private var speakerIdentifier: SpeakerIdentifier? = null

    private val pendingPcm = ArrayDeque<Short>()
    private val speakerCounts = HashMap<Long, Int>()

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
                            speakerIdentifier = SpeakerIdentifier(applicationContext, profileFiles)
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
        serviceScope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        speakerIdentifier = null
    }

    private fun startRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }
        listenOnce()
    }

    private fun listenOnce() {
        pendingPcm.clear()
        speakerCounts.clear()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        speechRecognizer?.startListening(intent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onBufferReceived(buffer: ByteArray?) {
            buffer ?: return
            feedSpeakerIdentifier(bytesToShorts(buffer))
        }

        override fun onError(error: Int) {
            // Recognizer stops listening on error (including plain silence timeouts) -
            // just restart so the service keeps listening continuously.
            mainHandler.postDelayed({ listenOnce() }, 250)
        }

        override fun onResults(results: Bundle?) {
            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.lowercase()

            if (transcript != null && transcript.contains(Config.TRIGGER_WORD)) {
                speakerCounts.maxByOrNull { it.value }?.key?.let { speakerId ->
                    serviceScope.launch { repository.registerDigga(speakerId) }
                }
            }
            listenOnce()
        }
    }

    private fun feedSpeakerIdentifier(newSamples: ShortArray) {
        val identifier = speakerIdentifier ?: return
        val frameLength = identifier.frameLength

        try {
            pendingPcm.addAll(newSamples.toList())
            while (pendingPcm.size >= frameLength) {
                val frame = ShortArray(frameLength) { pendingPcm.removeFirst() }
                identifier.identifyFrame(frame)?.let { id ->
                    speakerCounts[id] = (speakerCounts[id] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            // Don't let a transient error take the whole listening service down - this
            // chunk's speaker just won't be identified.
        }
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        for (i in shorts.indices) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt()
            shorts[i] = ((hi shl 8) or lo).toShort()
        }
        return shorts
    }

    private fun buildNotification(): Notification {
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
            .setContentText("Hört zu und zählt 'Digga' pro Person")
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
