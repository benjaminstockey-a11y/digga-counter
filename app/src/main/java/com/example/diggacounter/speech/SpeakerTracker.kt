package com.example.diggacounter.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*

/**
 * Records the microphone continuously on its own (independent of whatever
 * android.speech.SpeechRecognizer is doing) and keeps a rolling tally of who was most
 * likely speaking over the last few seconds.
 *
 * This exists because [android.speech.RecognitionListener.onBufferReceived] - which would
 * have let us reuse the recognizer's own audio - is documented as optional and doesn't
 * fire on every device/recognizer implementation. Recording separately works regardless,
 * at the cost of two concurrent microphone captures; most devices since Android 10 allow
 * that (the mic isn't exclusively locked to one app), but it's not guaranteed everywhere.
 */
class SpeakerTracker(
    private val identifier: SpeakerIdentifier,
    private val scope: CoroutineScope
) {
    companion object {
        private const val SAMPLE_RATE = 16000
        // ~3.2s rolling window at 64ms/frame - long enough to cover "...Digga" being said,
        // short enough that an old speaker's frames don't linger and skew a later match.
        private const val WINDOW_SIZE = 50
    }

    private val recentSpeakers = ArrayDeque<Long>()
    private var job: Job? = null
    private var audioRecord: AudioRecord? = null

    /** How many audio frames have been successfully read since [start] - for diagnostics. */
    @Volatile var framesCaptured: Int = 0
        private set

    @SuppressLint("MissingPermission")
    fun start() {
        val frameSize = identifier.frameLength
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, frameSize * 4)
        )
        audioRecord = record

        job = scope.launch(Dispatchers.IO) {
            try {
                record.startRecording()
                val frame = ShortArray(frameSize)
                while (isActive) {
                    var offset = 0
                    while (offset < frame.size) {
                        val read = record.read(frame, offset, frame.size - offset)
                        if (read <= 0) break
                        offset += read
                    }
                    if (offset == frame.size) {
                        framesCaptured++
                        identifier.identifyFrame(frame)?.let { personId ->
                            synchronized(recentSpeakers) {
                                recentSpeakers.addLast(personId)
                                while (recentSpeakers.size > WINDOW_SIZE) recentSpeakers.removeFirst()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Recording failed (e.g. mic unavailable) - mostLikelySpeaker() will just
                // keep returning null, which the caller already handles.
            }
        }
    }

    /** Most frequently identified speaker in the recent rolling window, or null. */
    fun mostLikelySpeaker(): Long? = synchronized(recentSpeakers) {
        recentSpeakers.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    fun stop() {
        job?.cancel()
        job = null
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // Already stopped/never started successfully.
        }
        audioRecord?.release()
        audioRecord = null
    }
}
