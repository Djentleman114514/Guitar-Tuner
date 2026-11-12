package com.xianzhun.tuner.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class TuningEngineTest {
    @Test fun `drop A distinguishes seventh A1 from fifth A2 and second B3`() {
        val tuning = GuitarTuning.DROP_A_SEVEN
        for (string in tuning.strings) {
            val engine = TuningEngine(tuning.strings)
            val reading = engine.process(pitch(string.frequency), null, 0).reading!!
            assertEquals(string, reading.target)
            assertEquals(0.0, reading.cents, 1e-8)
        }
        val seventh = tuning.strings.first()
        val manual = TuningEngine(tuning.strings).process(pitch(110.0), seventh, 0).reading!!
        assertEquals(7, manual.target.number)
        assertEquals(1200.0, manual.cents, 1e-8)
        assertFalse(manual.inTune)
        assertEquals(6, TuningEngine().process(pitch(55.0), null, 0).reading!!.target.number)
    }

    private val lowE = GuitarString.standard.first()
    private fun pitch(frequency: Double) = PitchAnalysis(frequency, 0.99, 0.1, false)

    @Test fun `cents calculation and distinct E strings`() {
        assertEquals(100.0, centsBetween(440 * 2.0.pow(1.0 / 12), 440.0), 1e-8)
        assertEquals(-100.0, centsBetween(440 / 2.0.pow(1.0 / 12), 440.0), 1e-8)
        assertEquals(6, GuitarString.closest(82.4).number)
        assertEquals(1, GuitarString.closest(329.6).number)
    }

    @Test fun `green state requires continuous stable pitch`() {
        val engine = TuningEngine()
        assertFalse(engine.process(pitch(lowE.frequency), null, 0).reading!!.inTune)
        assertFalse(engine.process(pitch(lowE.frequency), null, 200).reading!!.inTune)
        assertFalse(engine.process(pitch(lowE.frequency), null, 299).reading!!.inTune)
        assertTrue(engine.process(pitch(lowE.frequency), null, 300).reading!!.inTune)
    }

    @Test fun `a gap in valid observations restarts the stability timer`() {
        val engine = TuningEngine()
        engine.process(pitch(lowE.frequency), null, 0)
        engine.process(pitch(lowE.frequency), null, 200)
        assertFalse(engine.process(pitch(lowE.frequency), null, 500).reading!!.inTune)
        assertFalse(engine.process(pitch(lowE.frequency), null, 700).reading!!.inTune)
        assertTrue(engine.process(pitch(lowE.frequency), null, 800).reading!!.inTune)
    }

    @Test fun `green state enters after 300 ms and exits beyond five cents`() {
        val engine = TuningEngine()
        for (time in 0L..300L step 50) engine.process(pitch(lowE.frequency), null, time)
        assertTrue(engine.process(pitch(lowE.frequency), null, 350).reading!!.inTune)
        val fourCents = lowE.frequency * 2.0.pow(4.0 / 1200)
        assertTrue(engine.process(pitch(fourCents), null, 400).reading!!.inTune)
        val sixCents = lowE.frequency * 2.0.pow(6.0 / 1200)
        assertFalse(engine.process(pitch(sixCents), null, 450).reading!!.inTune)
    }

    @Test fun `silence immediately removes green then clears stale readings`() {
        val engine = TuningEngine()
        for (time in 0L..300L step 50) engine.process(pitch(lowE.frequency), null, time)
        val silence = PitchAnalysis(null, 0.0, 0.0, false)
        assertFalse(engine.process(silence, null, 350).reading!!.inTune)
        assertNull(engine.process(silence, null, 550).reading)
        assertFalse(engine.process(pitch(lowE.frequency), null, 600).reading!!.inTune)
    }

    @Test fun `manual target stays fixed even when another string sounds`() {
        val result = TuningEngine().process(pitch(110.0), lowE, 0)
        assertEquals(lowE, result.reading!!.target)
        assertTrue(result.reading!!.cents > 400)
        assertFalse(result.reading!!.inTune)
    }

    @Test fun `automatic target switches only after candidate persists`() {
        val engine = TuningEngine()
        engine.process(pitch(lowE.frequency), null, 0)
        assertEquals(6, engine.process(pitch(110.0), null, 50).reading!!.target.number)
        assertEquals(6, engine.process(pitch(110.0), null, 150).reading!!.target.number)
        assertEquals(5, engine.process(pitch(110.0), null, 200).reading!!.target.number)
    }

    @Test fun `clipped and unconfident input cannot show in tune`() {
        val engine = TuningEngine()
        assertEquals(SignalState.CLIPPED,
            engine.process(PitchAnalysis(110.0, 0.99, 0.8, true), null, 0).signal)
        assertNull(engine.process(PitchAnalysis(110.0, 0.5, 0.1, false), null, 100).reading)
    }

    @Test fun `reset discards previous target and stable state`() {
        val engine = TuningEngine()
        for (time in 0L..300L step 50) engine.process(pitch(lowE.frequency), null, time)
        engine.reset()
        val result = engine.process(pitch(329.6275569), null, 350)
        assertEquals(1, result.reading!!.target.number)
        assertFalse(result.reading!!.inTune)
    }
}
