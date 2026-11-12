package com.xianzhun.tuner.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class PitchAnalysis(
    val frequency: Double?,
    val confidence: Double,
    val rms: Double,
    val clipped: Boolean,
)

/** Single-note YIN estimator. Instances are confined to one analysis coroutine. */
class YinPitchDetector(
    private val sampleRate: Int,
    private val minFrequency: Double = 45.0,
    private val maxFrequency: Double = 450.0,
) {
    private var difference = DoubleArray(0)
    private var filtered = DoubleArray(0)

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

        // Suppress broadband microphone noise before measuring periodicity. Keep
        // the raw RMS/clipping checks above: filtering must not conceal overload.
        // Reset per window because successive windows overlap; skip filter startup.
        if (filtered.size != samples.size) filtered = DoubleArray(samples.size)
        val omega = 2 * PI * 1400.0 / sampleRate
        val alpha = sin(omega) / sqrt(2.0)
        val a0 = 1 + alpha
        val b0 = (1 - cos(omega)) / (2 * a0)
        val b1 = 2 * b0
        val a1 = -2 * cos(omega) / a0
        val a2 = (1 - alpha) / a0
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in samples.indices) {
            val x = samples[i] - mean
            val y = b0 * x + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2
            filtered[i] = y
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
        }

        if (difference.size != maxLag + 1) difference = DoubleArray(maxLag + 1)
        val comparisonLength = samples.size - maxLag
        val startup = min(sampleRate / 400, comparisonLength / 4)
        var runningSum = 0.0
        difference[0] = 1.0
        for (lag in 1..maxLag) {
            var squaredDifference = 0.0
            for (i in startup until comparisonLength) {
                val delta = filtered[i] - filtered[i + lag]
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
                if (center >= maxLag) break
                // A weak fundamental shifts the early harmonic dip. At low notes
                // a fixed two-sample search misses the true doubled-period minimum.
                val radius = max(2, original / 50)
                val candidate = (max(1, center - radius)..min(maxLag - 1, center + radius))
                    .minBy { difference[it] }
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
