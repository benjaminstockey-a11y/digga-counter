package com.example.diggacounter.speech

import android.content.Context
import java.io.File

/** Matches live audio frames against every enrolled person's saved voiceprint. */
class SpeakerIdentifier(context: Context, enrolledPersons: Map<Long, File>) {

    /** Minimum cosine similarity (0.0-1.0) to accept a match instead of "unknown".
     * Kept fairly low because this simple hand-rolled feature vector doesn't reproduce
     * as consistently between enrollment and real-world runtime audio (different mic
     * gain/environment/background noise) as a trained embedding model would - a strict
     * threshold like 0.9 essentially never matches anyone, so "Digga" gets recognized as
     * a word but never attributed to a person. */
    private val matchThreshold = 0.45f

    val frameLength: Int = AudioFeatures.FRAME_SIZE
    private val sampleRate = 16000 // matches what Android's SpeechRecognizer streams via onBufferReceived

    /** Highest similarity score seen across all identifyFrame() calls so far, regardless of
     * whether it cleared [matchThreshold] - purely for on-device diagnostics. */
    var lastBestScore: Float = -1f
        private set

    private val personIds: List<Long>
    private val profiles: List<FloatArray>

    init {
        val entries = enrolledPersons
            .mapNotNull { (id, file) -> VoiceProfileStore.load(file)?.let { id to it } }
        personIds = entries.map { it.first }
        profiles = entries.map { it.second }
    }

    /** Feed one [frameLength]-sample frame. Returns the best-matching personId, or null. */
    fun identifyFrame(frame: ShortArray): Long? {
        if (profiles.isEmpty()) return null
        if (AudioFeatures.rms(frame) < AudioFeatures.SILENCE_RMS_THRESHOLD) return null

        val features = AudioFeatures.extract(frame, sampleRate)
        var bestIndex = -1
        var bestScore = -1f
        for (i in profiles.indices) {
            val score = AudioFeatures.cosineSimilarity(features, profiles[i])
            if (score > bestScore) {
                bestScore = score
                bestIndex = i
            }
        }
        if (bestScore > lastBestScore) lastBestScore = bestScore
        return if (bestIndex >= 0 && bestScore >= matchThreshold) personIds[bestIndex] else null
    }
}
