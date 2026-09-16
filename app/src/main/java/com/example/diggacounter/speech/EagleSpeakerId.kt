package com.example.diggacounter.speech

import ai.picovoice.eagle.Eagle
import ai.picovoice.eagle.EagleProfile
import ai.picovoice.eagle.EagleProfiler
import android.content.Context
import com.example.diggacounter.Config
import java.io.File

/**
 * Wraps Picovoice Eagle (on-device speaker recognition) for two jobs:
 *  - [Enroller]: turn a few seconds of one person's voice into a reusable profile file.
 *  - [Recognizer]: given a live audio chunk, score it against every enrolled person and
 *    return the best match if it's confident enough.
 */
object EagleSpeakerId {

    /** Minimum similarity score (0.0-1.0) to accept a match instead of treating it as "unknown". */
    const val MATCH_THRESHOLD = 0.6f

    fun profileFile(context: Context, personId: Long): File =
        File(context.filesDir, "voice_profile_$personId.eagle")

    /** Records enrollment audio chunk by chunk until Eagle reports 100% enrolled. */
    class Enroller(context: Context) {
        private val profiler = EagleProfiler.Builder()
            .setAccessKey(Config.PICOVOICE_ACCESS_KEY)
            .build(context)

        val sampleRate: Int get() = profiler.sampleRate

        /** Minimum total samples Eagle needs per enroll() call to make progress. */
        val minEnrollSamples: Int get() = profiler.minEnrollSamples

        /** Feed a chunk of audio (at least [minEnrollSamples] long); returns progress 0-100. */
        fun enrollChunk(pcmChunk: ShortArray): Float =
            profiler.enroll(pcmChunk).percentage

        fun exportAndSave(destination: File) {
            val bytes = profiler.export().bytes
            destination.writeBytes(bytes)
        }

        fun close() = profiler.delete()
    }

    /** Matches live audio chunks against every enrolled person's saved profile. */
    class Recognizer(context: Context, enrolledPersons: Map<Long, File>) {
        private val personIds: List<Long>
        private val eagle: Eagle

        val sampleRate: Int
        val frameLength: Int

        init {
            val entries = enrolledPersons.filterValues { it.exists() }.toList()
            personIds = entries.map { it.first }
            val profiles = entries.map { (_, file) -> EagleProfile(file.readBytes()) }.toTypedArray()

            eagle = Eagle.Builder()
                .setAccessKey(Config.PICOVOICE_ACCESS_KEY)
                .setSpeakerProfiles(profiles)
                .build(context)
            sampleRate = eagle.sampleRate
            frameLength = eagle.frameLength
        }

        /**
         * Feed one frame ([frameLength] samples). Returns the personId with the highest
         * similarity score above [MATCH_THRESHOLD], or null if nobody matched confidently
         * or no one is enrolled yet.
         */
        fun identifyFrame(pcmFrame: ShortArray): Long? {
            if (personIds.isEmpty()) return null
            val scores = eagle.process(pcmFrame)
            val bestIndex = scores.indices.maxByOrNull { scores[it] } ?: return null
            return if (scores[bestIndex] >= MATCH_THRESHOLD) personIds[bestIndex] else null
        }

        fun close() = eagle.delete()
    }
}
