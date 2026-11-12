package com.xianzhun.tuner.core

import kotlin.math.abs

enum class SignalState { WAITING, TOO_QUIET, UNSTABLE, VALID, CLIPPED }

data class TuningReading(
    val target: GuitarString,
    val frequency: Double,
    val cents: Double,
    val inTune: Boolean,
)

data class TuningResult(val reading: TuningReading?, val signal: SignalState, val level: Double)

/** Pure state machine: callers supply a monotonic time, enabling deterministic tests. */
class TuningEngine {
    private val frequencies = ArrayDeque<Double>()
    private var target: GuitarString? = null
    private var candidate: GuitarString? = null
    private var candidateSince = 0L
    private var stableSince: Long? = null
    private var isInTune = false
    private var lastValidAt: Long? = null
    private var lastReading: TuningReading? = null

    fun reset() {
        frequencies.clear()
        target = null
        candidate = null
        stableSince = null
        isInTune = false
        lastValidAt = null
        lastReading = null
    }

    fun process(analysis: PitchAnalysis, manualTarget: GuitarString?, nowMs: Long): TuningResult {
        val frequency = analysis.frequency
        if (frequency == null || !frequency.isFinite() || frequency <= 0.0 ||
            analysis.confidence < 0.85 || analysis.clipped
        ) {
            stableSince = null
            isInTune = false
            val signal = when {
                analysis.clipped -> SignalState.CLIPPED
                analysis.rms < 0.002 -> SignalState.TOO_QUIET
                else -> SignalState.UNSTABLE
            }
            val stale = lastValidAt?.let { nowMs - it >= 250 } ?: true
            if (stale) {
                frequencies.clear()
                lastReading = null
            }
            return TuningResult(lastReading?.copy(inTune = false), signal, analysis.rms)
        }

        if (lastValidAt?.let { nowMs - it > 250 } == true) {
            frequencies.clear()
            stableSince = null
            isInTune = false
        }
        lastValidAt = nowMs
        if (frequencies.isNotEmpty() && abs(centsBetween(frequency, frequencies.last())) > 60) {
            frequencies.clear()
            stableSince = null
            isInTune = false
        }
        frequencies.addLast(frequency)
        if (frequencies.size > 3) frequencies.removeFirst()
        val smoothed = frequencies.sorted()[frequencies.size / 2]
        val closest = manualTarget ?: GuitarString.closest(smoothed)
        val current = target

        if (manualTarget != null || current == null) {
            if (target != closest) {
                stableSince = null
                isInTune = false
            }
            target = closest
            candidate = null
        } else if (closest != current &&
            abs(centsBetween(smoothed, closest.frequency)) + 12 < abs(centsBetween(smoothed, current.frequency))
        ) {
            if (candidate != closest) {
                candidate = closest
                candidateSince = nowMs
            } else if (nowMs - candidateSince >= 120) {
                target = closest
                candidate = null
                stableSince = null
                isInTune = false
            }
        } else {
            candidate = null
        }

        val selected = checkNotNull(target)
        val cents = centsBetween(smoothed, selected.frequency)
        val rawCents = centsBetween(frequency, selected.frequency)
        // A fresh out-of-range sample must not inherit the previous median's green state.
        if (abs(cents) <= 3.0 && abs(rawCents) <= 3.0) {
            if (stableSince == null) stableSince = nowMs
            if (nowMs - checkNotNull(stableSince) >= 300) isInTune = true
        } else {
            stableSince = null
            if (abs(cents) > 5.0 || abs(rawCents) > 5.0) isInTune = false
        }
        val reading = TuningReading(selected, smoothed, cents, isInTune)
        lastReading = reading
        return TuningResult(reading, SignalState.VALID, analysis.rms)
    }
}
