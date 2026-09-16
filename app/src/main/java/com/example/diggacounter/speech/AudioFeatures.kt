package com.example.diggacounter.speech

import kotlin.math.*

/**
 * Turns a short chunk of 16-bit PCM audio into a small "voiceprint" vector describing its
 * spectral shape - the main thing that differs between two people's voices. No external
 * service or model file needed, just an FFT and some log-energy band sums.
 *
 * This is a lightweight, self-contained substitute for a proper speaker-embedding model.
 * It works reasonably well for telling a handful of specific, enrolled household members
 * apart, but is not as accurate as a trained neural speaker-ID model.
 */
object AudioFeatures {

    const val FRAME_SIZE = 1024 // ~64ms at 16kHz; must be a power of two for the FFT
    private const val BAND_COUNT = 24
    private const val MIN_HZ = 80.0
    private const val MAX_HZ = 4000.0

    /** RMS energy below this (on a 0-1 normalized scale) is treated as silence and skipped. */
    const val SILENCE_RMS_THRESHOLD = 0.01f

    fun rms(frame: ShortArray): Float {
        var sum = 0.0
        for (s in frame) {
            val v = s / 32768.0
            sum += v * v
        }
        return sqrt(sum / frame.size).toFloat()
    }

    /** Returns an L2-normalized log-band-energy vector describing this frame's timbre. */
    fun extract(frame: ShortArray, sampleRate: Int): FloatArray {
        require(frame.size == FRAME_SIZE) { "frame must be $FRAME_SIZE samples" }

        val re = FloatArray(FRAME_SIZE)
        val im = FloatArray(FRAME_SIZE)
        for (i in 0 until FRAME_SIZE) {
            // Hamming window to reduce spectral leakage at frame edges.
            val window = 0.54f - 0.46f * cos(2.0 * PI * i / (FRAME_SIZE - 1)).toFloat()
            re[i] = (frame[i] / 32768f) * window
        }
        fft(re, im)

        val magnitude = FloatArray(FRAME_SIZE / 2)
        for (i in magnitude.indices) {
            magnitude[i] = sqrt(re[i] * re[i] + im[i] * im[i])
        }

        val bands = FloatArray(BAND_COUNT)
        val binHz = sampleRate.toDouble() / FRAME_SIZE
        val logMin = ln(MIN_HZ)
        val logMax = ln(MAX_HZ)
        for (b in 0 until BAND_COUNT) {
            val loHz = exp(logMin + (logMax - logMin) * b / BAND_COUNT)
            val hiHz = exp(logMin + (logMax - logMin) * (b + 1) / BAND_COUNT)
            val loBin = (loHz / binHz).toInt().coerceIn(0, magnitude.size - 1)
            val hiBin = (hiHz / binHz).toInt().coerceIn(loBin + 1, magnitude.size)
            var energy = 0.0
            for (i in loBin until hiBin) energy += magnitude[i]
            bands[b] = ln(1.0 + energy).toFloat()
        }

        // L2-normalize so loudness doesn't affect the comparison, only spectral shape.
        var norm = 0f
        for (v in bands) norm += v * v
        norm = sqrt(norm)
        if (norm > 1e-6f) {
            for (i in bands.indices) bands[i] /= norm
        }
        return bands
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot // both vectors are already L2-normalized, so dot product == cosine similarity
    }

    /** In-place iterative radix-2 Cooley-Tukey FFT. [re].size must be a power of two. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wReal = cos(ang).toFloat()
            val wImag = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curReal = 1f
                var curImag = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curReal - im[i + k + len / 2] * curImag
                    val vIm = re[i + k + len / 2] * curImag + im[i + k + len / 2] * curReal
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextReal = curReal * wReal - curImag * wImag
                    val nextImag = curReal * wImag + curImag * wReal
                    curReal = nextReal
                    curImag = nextImag
                }
                i += len
            }
            len = len shl 1
        }
    }
}
