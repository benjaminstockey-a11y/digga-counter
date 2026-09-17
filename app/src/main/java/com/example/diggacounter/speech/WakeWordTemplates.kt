package com.example.diggacounter.speech

import android.content.Context
import java.io.File

/**
 * Stores several recordings of one person saying "Digga" as feature-vector sequences
 * ("templates"). Each template is a list of [AudioFeatures.BAND_COUNT]-sized frame
 * vectors. Simple text format: one line per template, values comma-separated, a "|"
 * separates frames within a template.
 */
object WakeWordTemplates {

    fun file(context: Context, personId: Long): File =
        File(context.filesDir, "wakeword_$personId.txt")

    fun save(file: File, templates: List<List<FloatArray>>) {
        file.writeText(
            templates.joinToString("\n") { template ->
                template.joinToString("|") { frame -> frame.joinToString(",") }
            }
        )
    }

    fun load(file: File): List<List<FloatArray>> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readText()
                .lines()
                .filter { it.isNotBlank() }
                .map { line ->
                    line.split("|").map { frameStr ->
                        frameStr.split(",").map { it.toFloat() }.toFloatArray()
                    }
                }
        }.getOrElse { emptyList() }
    }
}
