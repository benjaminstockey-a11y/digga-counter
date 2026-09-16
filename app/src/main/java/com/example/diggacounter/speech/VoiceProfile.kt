package com.example.diggacounter.speech

import android.content.Context
import java.io.File

/** A saved voiceprint: the average feature vector across a person's enrollment recording. */
object VoiceProfileStore {

    fun profileFile(context: Context, personId: Long): File =
        File(context.filesDir, "voice_profile_$personId.txt")

    fun save(file: File, vector: FloatArray) {
        file.writeText(vector.joinToString(","))
    }

    fun load(file: File): FloatArray? {
        if (!file.exists()) return null
        return runCatching {
            file.readText().split(",").map { it.toFloat() }.toFloatArray()
        }.getOrNull()
    }
}
