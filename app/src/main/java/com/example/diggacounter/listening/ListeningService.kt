package com.example.diggacounter.listening

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.diggacounter.Config
import com.example.diggacounter.R
import com.example.diggacounter.audio.AudioChunkRecorder
import com.example.diggacounter.audio.toPcm16Bytes
import com.example.diggacounter.data.AppDatabase
import com.example.diggacounter.data.PersonRepository
import com.example.diggacounter.speech.EagleSpeakerId
import com.example.diggacounter.speech.GoogleSpeechClient
import kotlinx.coroutines.*

/**
 * Foreground service that listens continuously: records short audio chunks, runs each
 * chunk through Google Speech-to-Text (does it contain "Digga"?) and through Picovoice
 * Eagle (whose enrolled voice does it best match?), and books 50 cents to that person
 * when both agree.
 */
class ListeningService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private lateinit var repository: PersonRepository
    private val recorder = AudioChunkRecorder()
    private val speechClient = GoogleSpeechClient()
    private var recognizer: EagleSpeakerId.Recognizer? = null

    override fun onCreate() {
        super.onCreate()
        repository = PersonRepository(AppDatabase.get(this).personDao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (job?.isActive != true) {
            job = serviceScope.launch { listenLoop() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        recorder.stop()
        recognizer?.close()
    }

    private suspend fun listenLoop() {
        val persons = repository.getAll()
        val profileFiles = persons
            .filter { it.voiceProfilePath != null }
            .associate { it.id to java.io.File(it.voiceProfilePath!!) }

        recognizer = EagleSpeakerId.Recognizer(applicationContext, profileFiles)
        recorder.start()

        try {
            while (currentCoroutineContext().isActive) {
                val chunk = recorder.readChunk()

                // 1) Who spoke in this chunk? Split into Eagle-sized frames and take the
                //    speaker identified most often across those frames.
                val speakerId = identifySpeaker(chunk)

                // 2) What was said? Send the same chunk to Google Speech-to-Text.
                val transcript = speechClient.recognize(chunk.toPcm16Bytes(), recorder.sampleRateUsed())

                if (speakerId != null && transcript != null &&
                    transcript.contains(Config.TRIGGER_WORD, ignoreCase = true)
                ) {
                    repository.registerDigga(speakerId)
                }
            }
        } finally {
            recorder.stop()
            recognizer?.close()
        }
    }

    private fun identifySpeaker(chunk: ShortArray): Long? {
        val eagle = recognizer ?: return null
        val frameLength = eagle.frameLength
        if (frameLength <= 0) return null

        val counts = HashMap<Long, Int>()
        var offset = 0
        while (offset + frameLength <= chunk.size) {
            val frame = chunk.copyOfRange(offset, offset + frameLength)
            eagle.identifyFrame(frame)?.let { id ->
                counts[id] = (counts[id] ?: 0) + 1
            }
            offset += frameLength
        }
        return counts.maxByOrNull { it.value }?.key
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
