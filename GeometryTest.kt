package com.example.battery_float

import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {

    @Test
    fun fractionMapsOntoRange() {
        assertEquals(100, Geometry.fractionToPosition(0f, 100, 900))
        assertEquals(500, Geometry.fractionToPosition(0.5f, 100, 900))
        assertEquals(900, Geometry.fractionToPosition(1f, 100, 900))
    }

    @Test
    fun fractionIsClampedToValidRange() {
        assertEquals(100, Geometry.fractionToPosition(-3f, 100, 900))
        assertEquals(900, Geometry.fractionToPosition(7f, 100, 900))
    }

    @Test
    fun emptyOrInvertedRangeFallsBackToMinimum() {
        assertEquals(40, Geometry.fractionToPosition(0.7f, 40, 40))
        assertEquals(40, Geometry.fractionToPosition(0.7f, 40, 10))
        assertEquals(0f, Geometry.positionToFraction(55, 40, 40), 0f)
        assertEquals(40, Geometry.clamp(500, 40, 10))
        assertEquals(40, Geometry.nearestEdge(500, 40, 10))
    }

    @Test
    fun positionAndFractionRoundTrip() {
        for (position in 100..900 step 50) {
            val fraction = Geometry.positionToFraction(position, 100, 900)
            assertEquals(position, Geometry.fractionToPosition(fraction, 100, 900))
        }
    }

    @Test
    fun positionToFractionStaysInsideZeroToOne() {
        assertEquals(0f, Geometry.positionToFraction(-50, 100, 900), 0f)
        assertEquals(1f, Geometry.positionToFraction(5000, 100, 900), 0f)
    }

    @Test
    fun clampKeepsBubbleOnScreen() {
        assertEquals(100, Geometry.clamp(-20, 100, 900))
        assertEquals(900, Geometry.clamp(1200, 100, 900))
        assertEquals(450, Geometry.clamp(450, 100, 900))
    }

    @Test
    fun snapPicksTheNearerEdge() {
        assertEquals(100, Geometry.nearestEdge(300, 100, 900))
        assertEquals(900, Geometry.nearestEdge(700, 100, 900))
    }

    @Test
    fun snapTieGoesToTheStartEdge() {
        assertEquals(100, Geometry.nearestEdge(500, 100, 900))
    }
}
