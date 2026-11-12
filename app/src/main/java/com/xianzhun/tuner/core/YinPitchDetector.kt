package com.xianzhun.tuner.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PitchAnalysis(
    val frequency: Double?,
    val confidence: Double,
    val rms: Double,
    val clipped: Boolean,
)

/** Single-note YIN estimator. Instances are confined to one analysis coroutine. */
class YinPitchDetector(
    private val sampleRate: Int,
    private val minFrequency: Double = 65.0,
    private val maxFrequency: Double = 450.0,
) {
    private var difference = DoubleArray(0)

    fun analyze(samples: FloatArray): PitchAnalysis {
        require(sampleRate > 0)
        val maxLag = min(samples.size / 2 - 1, (sampleRate / minFrequency).toInt())
        // Search above the accepted output range as well: otherwise a high tone
        // could be mistaken for an in-range subharmonic merely by skipping its first dip.
        val minLag = max(2, (sampleRate / (maxFrequency * 3)).toInt())
        require(maxLag > minLag) { "Analysis window is too short" }

        var sum = 0.0
        var sumSquares = 0.0
        var clippedCount = 0
        for (sample in samples) {
            sum += sample
            sumSquares += sample.toDouble() * sample
            if (sample >= 0.985f || sample <= -0.985f) clippedCount++
        }
        val mean = sum / samples.size
        val rms = sqrt(max(0.0, sumSquares / samples.size - mean * mean))
        val clipped = clippedCount > samples.size / 100
        if (!rms.isFinite() || rms < 0.002) return PitchAnalysis(null, 0.0, rms, clipped)

        if (difference.size != maxLag + 1) difference = DoubleArray(maxLag + 1)
        val comparisonLength = samples.size - maxLag
        var runningSum = 0.0
        difference[0] = 1.0
        for (lag in 1..maxLag) {
            var squaredDifference = 0.0
            for (i in 0 until comparisonLength) {
                val delta = (samples[i] - samples[i + lag]).toDouble()
                squaredDifference += delta * delta
            }
            runningSum += squaredDifference
            difference[lag] = if (runningSum > 0.0) squaredDifference * lag / runningSum else 1.0
        }

        var chosen = -1
        var lag = minLag
        while (lag < maxLag) {
            if (difference[lag] < 0.12) {
                while (lag + 1 <= maxLag && difference[lag + 1] < difference[lag]) lag++
                chosen = lag
                break
            }
            lag++
        }
        if (chosen < 0) return PitchAnalysis(null, 0.0, rms, clipped)

        // Strong harmonics can create an early dip. Prefer a substantially better
        // doubled/tripled period only when the first dip has meaningful residual error.
        if (difference[chosen] > 0.025) {
            val original = chosen
            for (multiple in 2..3) {
                val center = original * multiple
                if (center + 2 >= maxLag) break
                val candidate = (center - 2..center + 2).minBy { difference[it] }
                if (difference[candidate] < difference[chosen] * 0.2 &&
                    difference[chosen] - difference[candidate] > 0.02
                ) chosen = candidate
            }
        }

        var period = chosen.toDouble()
        if (chosen > 1 && chosen < maxLag) {
            val left = difference[chosen - 1]
            val middle = difference[chosen]
            val right = difference[chosen + 1]
            val denominator = left - 2.0 * middle + right
            if (denominator != 0.0) period += (0.5 * (left - right) / denominator).coerceIn(-1.0, 1.0)
        }
        val frequency = sampleRate / period
        val confidence = (1.0 - difference[chosen]).coerceIn(0.0, 1.0)
        return PitchAnalysis(
            frequency.takeIf { it.isFinite() && it in minFrequency..maxFrequency },
            confidence, rms, clipped,
        )
    }
}
