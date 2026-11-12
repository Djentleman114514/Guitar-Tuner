package com.xianzhun.tuner.core

import org.junit.Assert.*
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

class YinPitchDetectorTest {
    @Test fun `quiet guitar with broadband microphone noise remains detectable`() {
        for (rate in listOf(44_100, 48_000)) {
            for (string in GuitarTuning.DROP_A_SEVEN.strings) {
                val random = Random(123)
                val samples = FloatArray(4096) { i ->
                    val t = i.toDouble() / rate
                    val p = 2 * PI * string.frequency * t
                    ((0.06 * sin(p) + 0.04 * sin(2 * p) + 0.02 * sin(3 * p)) *
                        exp(-3 * t) + random.nextGaussian() * 0.035).toFloat()
                }
                val result = YinPitchDetector(rate).analyze(samples)
                assertNotNull("${string.fullNote} at $rate", result.frequency)
                assertTrue("${string.fullNote}: ${result.frequency}",
                    abs(centsBetween(result.frequency!!, string.frequency)) < 5.0)
                assertEquals(SignalState.VALID, TuningEngine().process(result, null, 0).signal)
            }
        }
    }

    @Test fun `all strings and detunings stay within one cent at both sample rates`() {
        val errors = mutableListOf<Double>()
        for (rate in listOf(44_100, 48_000)) {
            val detector = YinPitchDetector(rate)
            for (string in GuitarTuning.DROP_A_SEVEN.strings) {
                for (cents in listOf(-50.0, -20.0, -5.0, 0.0, 5.0, 20.0, 50.0)) {
                    val frequency = string.frequency * 2.0.pow(cents / 1200)
                    for (phase in listOf(0.0, 0.7, 2.0)) {
                        val result = detector.analyze(tone(frequency, rate, phase))
                        assertNotNull("${string.fullNote}, $rate Hz, $cents cents", result.frequency)
                        val error = abs(centsBetween(result.frequency!!, frequency))
                        assertTrue("Error: $error cents", error < 1.0)
                        assertTrue(result.confidence > 0.95)
                        errors += error
                    }
                }
            }
        }
        println("Pure-tone fixtures: ${errors.size}, max error ${errors.max()} cents")
    }

    @Test fun `guitar-like harmonics decay and noise preserve fundamental`() {
        val errors = mutableListOf<Double>()
        for (rate in listOf(44_100, 48_000)) {
            val detector = YinPitchDetector(rate)
            for (string in GuitarTuning.DROP_A_SEVEN.strings) {
                val random = Random(42)
                val samples = FloatArray(4096) { index ->
                    val t = index.toDouble() / rate
                    val phase = 2 * PI * string.frequency * t
                    ((0.22 * sin(phase) + 0.34 * sin(phase * 2) + 0.12 * sin(phase * 3)) *
                        exp(-t * 2) + random.nextGaussian() * 0.002).toFloat()
                }
                val result = detector.analyze(samples)
                assertNotNull("${string.fullNote}: no detection", result.frequency)
                val error = abs(centsBetween(result.frequency!!, string.frequency))
                assertTrue("${string.fullNote}: $error cents", error < 3.0)
                errors += error
            }
        }
        println("Harmonic fixtures: ${errors.size}, max error ${errors.max()} cents")
    }

    @Test fun `weak fundamental does not get replaced by second harmonic`() {
        val detector = YinPitchDetector(48_000)
        for (string in GuitarTuning.DROP_A_SEVEN.strings) {
            val result = detector.analyze(FloatArray(4096) { i ->
                val phase = 2 * PI * string.frequency * i / 48_000
                (0.08 * sin(phase) + 0.55 * sin(2 * phase)).toFloat()
            })
            assertNotNull(result.frequency)
            assertTrue("${string.fullNote}: ${result.frequency}",
                abs(centsBetween(result.frequency!!, string.frequency)) < 3.0)
        }
    }

    @Test fun `silence noise and dc offset do not produce confident notes`() {
        val detector = YinPitchDetector(48_000)
        assertNull(detector.analyze(FloatArray(4096)).frequency)
        assertNull(detector.analyze(FloatArray(4096) { 0.4f }).frequency)
        for (rate in listOf(44_100, 48_000)) {
            for (seed in 0L until 32L) {
                val random = Random(seed)
                assertNull("Noise seed $seed at $rate",
                    YinPitchDetector(rate).analyze(
                        FloatArray(4096) { (random.nextGaussian() * 0.1).toFloat() },
                    ).frequency)
            }
        }
    }

    @Test fun `clipping is exposed and low level is rejected`() {
        val detector = YinPitchDetector(48_000)
        assertTrue(detector.analyze(FloatArray(4096) { if (it % 20 < 10) 1f else -1f }).clipped)
        assertNull(detector.analyze(tone(110.0, 48_000).map { it * 0.001f }.toFloatArray()).frequency)
    }

    @Test fun `out of range high tones are not folded into guitar range`() {
        val detector = YinPitchDetector(48_000)
        for (frequency in listOf(659.2551, 880.0, 1000.0)) {
            assertNull("High tone $frequency Hz must not be halved", detector.analyze(tone(frequency, 48_000)).frequency)
        }
    }

    private fun tone(frequency: Double, rate: Int, phase: Double = 0.0) =
        FloatArray(4096) { (0.5 * sin(2 * PI * frequency * it / rate + phase)).toFloat() }
}
