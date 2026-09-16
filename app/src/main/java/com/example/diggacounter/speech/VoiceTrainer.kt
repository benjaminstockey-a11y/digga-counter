package com.example.diggacounter.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records live microphone audio at whatever sample rate Eagle requires and feeds it into
 * an [EagleSpeakerId.Enroller] until enrollment reaches 100%, then saves the resulting
 * voice profile for that person.
 */
object VoiceTrainer {

    @SuppressLint("MissingPermission")
    suspend fun train(
        context: Context,
        personId: Long,
        onProgress: (Float) -> Unit
    ): java.io.File = withContext(Dispatchers.IO) {
        val enroller = EagleSpeakerId.Enroller(context)
        val chunkSize = enroller.minEnrollSamples
        val minBuf = AudioRecord.getMinBufferSize(
            enroller.sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            enroller.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, chunkSize * 2)
        )

        val outFile = EagleSpeakerId.profileFile(context, personId)
        try {
            audioRecord.startRecording()
            var progress = 0f
            val chunk = ShortArray(chunkSize)
            while (progress < 100f) {
                var offset = 0
                while (offset < chunk.size) {
                    val read = audioRecord.read(chunk, offset, chunk.size - offset)
                    if (read <= 0) break
                    offset += read
                }
                progress = enroller.enrollChunk(chunk)
                onProgress(progress)
            }
            enroller.exportAndSave(outFile)
        } finally {
            audioRecord.stop()
            audioRecord.release()
            enroller.close()
        }
        outFile
    }
}
