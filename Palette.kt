package com.example.battery_float

/**
 * Colours used by the app screen and the bubble. The theme in res/values/colors.xml mirrors the
 * background and accent values so the window never flashes a different colour while starting.
 *
 * The UI is dark only: near-black surfaces switch off most pixels on OLED screens, which is the
 * cheapest way for an app screen to save power.
 */
object Palette {
    val BACKGROUND = argb(0xFF0B0E11)
    val CARD = argb(0xFF14181D)
    val CARD_STROKE = argb(0xFF232A31)
    val FIELD = argb(0xFF1C2228)

    val TEXT_PRIMARY = argb(0xFFEAF0F4)
    val TEXT_SECONDARY = argb(0xFF98A4AF)

    val GREEN = argb(0xFF34D399)
    val GREEN_TONAL = argb(0xFF0F3A2C)
    val GREEN_ON = argb(0xFF04130D)

    val YELLOW = argb(0xFFFACC15)
    val ORANGE = argb(0xFFFB923C)

    val RED = argb(0xFFF87171)
    val RED_ON = argb(0xFF1C0606)

    val AMBER = argb(0xFFFBBF24)
    val AMBER_TONAL = argb(0xFF2E2406)
    val AMBER_STROKE = argb(0x66FBBF24)
    val AMBER_ON = argb(0xFF1F1500)

    val OVERLAY_DISC = argb(0xE6101418)
    val TRACK = argb(0x33FFFFFF)
    val RIPPLE = argb(0x33FFFFFF)
    val WHITE = argb(0xFFFFFFFF)
    val GRAY = argb(0xFF6B7782)

    /** Ring colour for a battery level. A full battery is always green. */
    fun forLevel(percent: Int, full: Boolean): Int = when {
        percent < 0 -> GRAY
        full -> GREEN
        percent > 50 -> GREEN
        percent > 30 -> YELLOW
        percent > 15 -> ORANGE
        else -> RED
    }

    /** Colour literals such as 0xFF0B0E11 are Longs in Kotlin; this wraps them into an ARGB Int. */
    private fun argb(value: Long): Int = value.toInt()
}
