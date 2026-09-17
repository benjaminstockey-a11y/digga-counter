package com.example.diggacounter.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.io.File

data class WakeWordMatch(val personId: Long, val distance: Float)

/**
 * Owns the microphone exclusively (a single AudioRecord - no conflicts with any other
 * recognizer, unlike the earlier attempt that ran alongside android.speech.SpeechRecognizer
 * and starved it of audio on some devices). Segments incoming audio into utterances via
 * simple energy-based voice activity detection, then compares each utterance against every
 * enrolled person's "Digga" templates using [Dtw]. A close enough match identifies both
 * *that the trigger word was said* and *who said it* in one step, since the template
 * already encodes that person's specific voice and pronunciation.
 */
class WakeWordDetector(
    private val context: Context,
    enrolledPersons: Map<Long, File>,
    private val scope: CoroutineScope,
    private val onMatch: (WakeWordMatch) -> Unit,
    private val onUtteranceAnalyzed: (bestDistance: Float, frameCount: Int) -> Unit
) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val MIN_UTTERANCE_FRAMES = 4
        private const val MAX_UTTERANCE_FRAMES = 60
        private const val SILENCE_HANGOVER_FRAMES = 6
        /** DTW distance threshold - lower means stricter. Tuned empirically; adjust here
         * if real-world testing shows too many misses or false triggers. */
        const val MATCH_THRESHOLD = 0.28f
        private const val COOLDOWN_MS = 1500L
    }

    private val templatesByPerson: Map<Long, List<List<FloatArray>>> =
        enrolledPersons.mapValues { (_, file) -> WakeWordTemplates.load(file) }
            .filterValues { it.isNotEmpty() }

    private var job: Job? = null
    private var audioRecord: AudioRecord? = null
    private var lastMatchAtMs = 0L

    @SuppressLint("MissingPermission")
    fun start() {
        val frameSize = AudioFeatures.FRAME_SIZE
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
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
                var currentUtterance = mutableListOf<FloatArray>()
                var silenceRun = 0
                var inUtterance = false

                while (isActive) {
                    var offset = 0
                    while (offset < frame.size) {
                        val read = record.read(frame, offset, frame.size - offset)
                        if (read <= 0) break
                        offset += read
                    }
                    val voiced = AudioFeatures.rms(frame) >= AudioFeatures.SILENCE_RMS_THRESHOLD

                    if (voiced) {
                        inUtterance = true
                        silenceRun = 0
                        currentUtterance.add(AudioFeatures.extract(frame, SAMPLE_RATE))
                        if (currentUtterance.size >= MAX_UTTERANCE_FRAMES) {
                            handleUtterance(currentUtterance)
                            currentUtterance = mutableListOf()
                            inUtterance = false
                        }
                    } else if (inUtterance) {
                        silenceRun++
                        if (silenceRun >= SILENCE_HANGOVER_FRAMES) {
                            handleUtterance(currentUtterance)
                            currentUtterance = mutableListOf()
                            inUtterance = false
                            silenceRun = 0
                        } else {
                            currentUtterance.add(AudioFeatures.extract(frame, SAMPLE_RATE))
                        }
                    }
                }
            } catch (e: Exception) {
                // Recording failed - detector just stays silent rather than crashing the service.
            }
        }
    }

    private fun handleUtterance(utterance: List<FloatArray>) {
        if (utterance.size < MIN_UTTERANCE_FRAMES) return
        if (templatesByPerson.isEmpty()) return

        var bestPerson: Long? = null
        var bestDistance = Float.MAX_VALUE
        for ((personId, templates) in templatesByPerson) {
            for (template in templates) {
                val d = Dtw.distance(utterance, template)
                if (d < bestDistance) {
                    bestDistance = d
                    bestPerson = personId
                }
            }
        }

        onUtteranceAnalyzed(bestDistance, utterance.size)

        val now = System.currentTimeMillis()
        if (bestPerson != null && bestDistance <= MATCH_THRESHOLD && now - lastMatchAtMs > COOLDOWN_MS) {
            lastMatchAtMs = now
            onMatch(WakeWordMatch(bestPerson, bestDistance))
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try {
            audioRecord?.stop()
        } catch (e: Exception) { /* already stopped */ }
        audioRecord?.release()
        audioRecord = null
    }
}
