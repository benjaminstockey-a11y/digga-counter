package com.example.diggacounter.speech

import android.util.Base64
import com.example.diggacounter.Config
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the Google Cloud Speech-to-Text REST "speech:recognize" endpoint.
 * Each call is a short, synchronous recognition of one audio chunk (a few seconds) -
 * simpler and more robust on mobile than the gRPC streaming API, at the cost of a little
 * latency per chunk.
 */
class GoogleSpeechClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Returns the recognized transcript (lowercased) for one PCM16/16kHz mono chunk, or null. */
    fun recognize(pcm16: ByteArray, sampleRate: Int): String? {
        val audioB64 = Base64.encodeToString(pcm16, Base64.NO_WRAP)

        val body = JSONObject().apply {
            put("config", JSONObject().apply {
                put("encoding", "LINEAR16")
                put("sampleRateHertz", sampleRate)
                put("languageCode", "de-DE")
                put("model", "default")
            })
            put("audio", JSONObject().apply {
                put("content", audioB64)
            })
        }.toString()

        val request = Request.Builder()
            .url("https://speech.googleapis.com/v1/speech:recognize?key=${Config.GOOGLE_SPEECH_API_KEY}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            val results = json.optJSONArray("results") ?: return null
            val transcript = StringBuilder()
            for (i in 0 until results.length()) {
                val alt = results.getJSONObject(i)
                    .getJSONArray("alternatives")
                    .getJSONObject(0)
                transcript.append(alt.optString("transcript"))
                transcript.append(" ")
            }
            return transcript.toString().trim().lowercase()
        }
    }
}
