package com.example.battery_float

import android.content.Intent
import android.os.BatteryManager
import kotlin.math.roundToInt

/** A snapshot of the sticky ACTION_BATTERY_CHANGED broadcast. */
data class BatteryStatus(
    /** 0..100, or -1 when the level cannot be read (or there is no battery). */
    val percent: Int,
    val charging: Boolean,
    val full: Boolean,
    /** BatteryManager.BATTERY_PLUGGED_* bit mask, 0 when running on battery. */
    val plugged: Int,
    /** BatteryManager.BATTERY_HEALTH_* value. */
    val health: Int,
    /** Temperature in tenths of a degree Celsius, or [NO_VALUE]. */
    val temperatureTenthsC: Int,
    /** Voltage in millivolts, or [NO_VALUE]. */
    val voltageMv: Int,
) {
    val known: Boolean get() = percent >= 0

    val pluggedIn: Boolean get() = plugged != 0

    /** Only trust values in a physically sensible range; some devices report 0 or garbage. */
    val temperatureCelsius: Float?
        get() = if (temperatureTenthsC != NO_VALUE && temperatureTenthsC != 0 &&
            temperatureTenthsC in -400..1500
        ) temperatureTenthsC / 10f else null

    val voltageVolts: Float?
        get() = if (voltageMv in 2000..10000) voltageMv / 1000f else null

    companion object {
        const val NO_VALUE = Int.MIN_VALUE

        /** BatteryManager.BATTERY_PLUGGED_DOCK exists only from API 33, so the value is kept here. */
        const val PLUGGED_DOCK = 8

        val UNKNOWN = BatteryStatus(
            percent = -1,
            charging = false,
            full = false,
            plugged = 0,
            health = BatteryManager.BATTERY_HEALTH_UNKNOWN,
            temperatureTenthsC = NO_VALUE,
            voltageMv = NO_VALUE,
        )

        fun from(intent: Intent?): BatteryStatus {
            if (intent == null) return UNKNOWN
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
            val percent = if (present && level >= 0 && scale > 0) {
                (level * 100f / scale).roundToInt().coerceIn(0, 100)
            } else {
                -1
            }
            val status = intent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
            return BatteryStatus(
                percent = percent,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING,
                full = status == BatteryManager.BATTERY_STATUS_FULL,
                plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
                health = intent.getIntExtra(
                    BatteryManager.EXTRA_HEALTH,
                    BatteryManager.BATTERY_HEALTH_UNKNOWN
                ),
                temperatureTenthsC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, NO_VALUE),
                voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, NO_VALUE),
            )
        }
    }
}
