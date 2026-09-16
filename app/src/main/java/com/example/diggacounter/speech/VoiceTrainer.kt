package com.example.diggacounter.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Records a few seconds of one person's voice, extracts a spectral feature vector for
 * each voiced (non-silent) frame, and averages them into a single voiceprint saved to
 * disk - no external service or account involved.
 */
object VoiceTrainer {

    private const val SAMPLE_RATE = 16000
    private const val TARGET_VOICED_FRAMES = 80 // ~5s of actual speech at 64ms/frame

    @SuppressLint("MissingPermission")
    suspend fun train(
        context: Context,
        personId: Long,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
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

        val outFile = VoiceProfileStore.profileFile(context, personId)
        val sum = FloatArray(24) // must match AudioFeatures band count
        var voicedFrames = 0

        try {
            audioRecord.startRecording()
            val frame = ShortArray(frameSize)
            while (voicedFrames < TARGET_VOICED_FRAMES) {
                var offset = 0
                while (offset < frame.size) {
                    val read = audioRecord.read(frame, offset, frame.size - offset)
                    if (read <= 0) break
                    offset += read
                }
                if (AudioFeatures.rms(frame) >= AudioFeatures.SILENCE_RMS_THRESHOLD) {
                    val features = AudioFeatures.extract(frame, SAMPLE_RATE)
                    for (i in features.indices) sum[i] += features[i]
                    voicedFrames++
                    onProgress(voicedFrames * 100f / TARGET_VOICED_FRAMES)
                }
            }

            val average = FloatArray(sum.size) { sum[it] / voicedFrames }
            var norm = 0f
            for (v in average) norm += v * v
            norm = kotlin.math.sqrt(norm)
            if (norm > 1e-6f) for (i in average.indices) average[i] /= norm

            VoiceProfileStore.save(outFile, average)
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
        outFile
    }
}
