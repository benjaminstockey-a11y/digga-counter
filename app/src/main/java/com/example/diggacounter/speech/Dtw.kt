package com.example.diggacounter.speech

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Dynamic Time Warping: compares two sequences of feature vectors (e.g. one template
 * recording of "Digga" and one live audio window) even though they're spoken at slightly
 * different speeds/lengths. Returns a distance - lower means more similar - normalized by
 * path length so it's comparable across sequences of different sizes.
 */
object Dtw {

    fun distance(a: List<FloatArray>, b: List<FloatArray>): Float {
        if (a.isEmpty() || b.isEmpty()) return Float.MAX_VALUE
        val n = a.size
        val m = b.size
        val cost = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE / 2 } }
        cost[0][0] = 0f

        for (i in 1..n) {
            for (j in 1..m) {
                val d = euclidean(a[i - 1], b[j - 1])
                cost[i][j] = d + min(cost[i - 1][j], min(cost[i][j - 1], cost[i - 1][j - 1]))
            }
        }
        // Normalize by path length (roughly n+m) so longer/shorter sequences stay comparable.
        return cost[n][m] / (n + m)
    }

    private fun euclidean(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}
