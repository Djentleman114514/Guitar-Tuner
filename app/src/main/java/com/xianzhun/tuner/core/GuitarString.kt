package com.xianzhun.tuner.core

import kotlin.math.abs
import kotlin.math.ln

data class GuitarString(val number: Int, val note: String, val octave: Int, val frequency: Double) {
    val fullNote: String get() = "$note$octave"
    companion object {
        val standard = listOf(
            GuitarString(6, "E", 2, 82.4068892282),
            GuitarString(5, "A", 2, 110.0),
            GuitarString(4, "D", 3, 146.8323839587),
            GuitarString(3, "G", 3, 195.9977179909),
            GuitarString(2, "B", 3, 246.9416506281),
            GuitarString(1, "E", 4, 329.6275569129),
        )

        fun closest(frequency: Double): GuitarString = standard.minBy {
            abs(centsBetween(frequency, it.frequency))
        }
    }
}

fun centsBetween(frequency: Double, target: Double): Double = 1200.0 * ln(frequency / target) / ln(2.0)
