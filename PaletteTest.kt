package com.example.battery_float

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteTest {

    @Test
    fun levelColoursFollowTheThresholds() {
        assertEquals(Palette.GREEN, Palette.forLevel(100, false))
        assertEquals(Palette.GREEN, Palette.forLevel(51, false))
        assertEquals(Palette.YELLOW, Palette.forLevel(50, false))
        assertEquals(Palette.YELLOW, Palette.forLevel(31, false))
        assertEquals(Palette.ORANGE, Palette.forLevel(30, false))
        assertEquals(Palette.ORANGE, Palette.forLevel(16, false))
        assertEquals(Palette.RED, Palette.forLevel(15, false))
        assertEquals(Palette.RED, Palette.forLevel(0, false))
    }

    @Test
    fun fullBatteryIsAlwaysGreen() {
        assertEquals(Palette.GREEN, Palette.forLevel(100, true))
    }

    @Test
    fun unknownLevelIsGray() {
        assertEquals(Palette.GRAY, Palette.forLevel(-1, false))
    }
}
