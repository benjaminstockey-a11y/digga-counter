package com.example.diggacounter.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Continuously records 16kHz mono 16-bit PCM audio and hands it out in fixed-size chunks
 * via [readChunk]. Both Google Speech-to-Text and Picovoice Eagle expect this format.
 */
class AudioChunkRecorder(
    val sampleRate: Int = 16000,
    private val chunkSeconds: Double = 3.0
) {
    val chunkSizeSamples: Int = (sampleRate * chunkSeconds).toInt()

    fun sampleRateUsed(): Int = sampleRate

    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, chunkSizeSamples * 2) * 2
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        ).also { it.startRecording() }
    }

    /** Blocks until a full chunk of [chunkSizeSamples] 16-bit PCM samples has been read. */
    fun readChunk(): ShortArray {
        val buffer = ShortArray(chunkSizeSamples)
        var offset = 0
        val record = audioRecord ?: return buffer
        while (offset < buffer.size) {
            val read = record.read(buffer, offset, buffer.size - offset)
            if (read <= 0) break
            offset += read
        }
        return buffer
    }

    fun stop() {
        audioRecord?.let {
            it.stop()
            it.release()
        }
        audioRecord = null
    }
}

fun ShortArray.toPcm16Bytes(): ByteArray {
    val bytes = ByteArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt()
        bytes[i * 2] = (v and 0xFF).toByte()
        bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
    }
    return bytes
}
