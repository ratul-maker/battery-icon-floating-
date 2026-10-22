package com.example.battery_float

import kotlin.math.roundToInt

/**
 * Pure position maths for the floating bubble (no Android classes, so it is unit-tested on the JVM).
 *
 * A bubble position is the window's top-left corner. It may range from `min` (bubble touching the
 * start of the usable area) to `max` (bubble touching the end), where `max = areaEnd - bubbleSize`.
 */
object Geometry {

    /** Maps a 0..1 fraction onto [min, max]. An empty or inverted range always yields [min]. */
    fun fractionToPosition(fraction: Float, min: Int, max: Int): Int {
        if (max <= min) return min
        return min + (fraction.coerceIn(0f, 1f) * (max - min)).roundToInt()
    }

    /** Inverse of [fractionToPosition]; an empty range yields 0. */
    fun positionToFraction(position: Int, min: Int, max: Int): Float {
        if (max <= min) return 0f
        return ((position - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    }

    /** Keeps [position] inside [min, max]. An empty or inverted range always yields [min]. */
    fun clamp(position: Int, min: Int, max: Int): Int =
        if (max <= min) min else position.coerceIn(min, max)

    /** The nearer end of [min, max]; ties go to [min]. */
    fun nearestEdge(position: Int, min: Int, max: Int): Int {
        if (max <= min) return min
        return if (position - min <= max - position) min else max
    }
}
