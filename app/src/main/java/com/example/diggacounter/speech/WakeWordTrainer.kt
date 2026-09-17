package com.example.diggacounter.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records several isolated utterances of the trigger word (the person says "Digga",
 * pauses, says it again, ...) and saves each as a feature-vector template. Uses simple
 * energy-based voice activity detection to find where each utterance starts and ends -
 * no external service needed.
 */
object WakeWordTrainer {

    private const val SAMPLE_RATE = 16000
    const val TEMPLATE_COUNT = 5

    private const val MIN_UTTERANCE_FRAMES = 4   // ~128ms - ignore tiny noise blips
    private const val MAX_UTTERANCE_FRAMES = 40  // ~1.28s - cut off if someone rambles
    private const val SILENCE_HANGOVER_FRAMES = 6 // ~192ms of silence ends an utterance

    @SuppressLint("MissingPermission")
    suspend fun train(
        context: Context,
        personId: Long,
        onTemplateRecorded: (count: Int) -> Unit
    ): java.io.File = withContext(Dispatchers.IO) {
        val frameSize = AudioFeatures.FRAME_SIZE
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, frameSize * 4)
        )

        val templates = mutableListOf<List<FloatArray>>()
        val outFile = WakeWordTemplates.file(context, personId)

        try {
            audioRecord.startRecording()
            val frame = ShortArray(frameSize)
            var currentUtterance = mutableListOf<FloatArray>()
            var silenceRun = 0
            var inUtterance = false

            while (templates.size < TEMPLATE_COUNT) {
                var offset = 0
                while (offset < frame.size) {
                    val read = audioRecord.read(frame, offset, frame.size - offset)
                    if (read <= 0) break
                    offset += read
                }
                val voiced = AudioFeatures.rms(frame) >= AudioFeatures.SILENCE_RMS_THRESHOLD

                if (voiced) {
                    inUtterance = true
                    silenceRun = 0
                    currentUtterance.add(AudioFeatures.extract(frame, SAMPLE_RATE))
                    if (currentUtterance.size >= MAX_UTTERANCE_FRAMES) {
                        finishUtterance(currentUtterance, templates, onTemplateRecorded)
                        currentUtterance = mutableListOf()
                        inUtterance = false
                    }
                } else if (inUtterance) {
                    silenceRun++
                    if (silenceRun >= SILENCE_HANGOVER_FRAMES) {
                        finishUtterance(currentUtterance, templates, onTemplateRecorded)
                        currentUtterance = mutableListOf()
                        inUtterance = false
                        silenceRun = 0
                    } else {
                        currentUtterance.add(AudioFeatures.extract(frame, SAMPLE_RATE))
                    }
                }
            }

            WakeWordTemplates.save(outFile, templates)
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
        outFile
    }

    private fun finishUtterance(
        utterance: List<FloatArray>,
        templates: MutableList<List<FloatArray>>,
        onTemplateRecorded: (Int) -> Unit
    ) {
        if (utterance.size >= MIN_UTTERANCE_FRAMES) {
            templates.add(utterance)
            onTemplateRecorded(templates.size)
        }
    }
}
