package com.example.battery_float

import android.content.Context
import android.content.SharedPreferences

/**
 * Every user option, kept in one tiny SharedPreferences file.
 *
 * The app screen and the overlay service run in the same process, so a change made on the screen
 * reaches the running bubble through [register] without any IPC or polling.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** True while the user wants the bubble on. Used to bring it back after a reboot or an update. */
    var bubbleWanted: Boolean
        get() = prefs.getBoolean(KEY_WANTED, false)
        set(value) = prefs.edit().putBoolean(KEY_WANTED, value).apply()

    /** Bubble diameter in dp. */
    var sizeDp: Int
        get() = prefs.getInt(KEY_SIZE, DEFAULT_SIZE_DP).coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)
        set(value) = prefs.edit().putInt(KEY_SIZE, value.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)).apply()

    /** Whole-bubble opacity, MIN_OPACITY..100 percent. */
    var opacityPercent: Int
        get() = prefs.getInt(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(MIN_OPACITY, 100)
        set(value) = prefs.edit().putInt(KEY_OPACITY, value.coerceIn(MIN_OPACITY, 100)).apply()

    /** Glide to the nearest screen edge after the bubble is released. */
    var snapToEdge: Boolean
        get() = prefs.getBoolean(KEY_SNAP, true)
        set(value) = prefs.edit().putBoolean(KEY_SNAP, value).apply()

    /** Restart the bubble after a reboot or an app update (only if it was on before). */
    var restoreAfterReboot: Boolean
        get() = prefs.getBoolean(KEY_RESTORE, true)
        set(value) = prefs.edit().putBoolean(KEY_RESTORE, value).apply()

    /** Horizontal position as a fraction (0 = left edge, 1 = right edge) of the free space. */
    var positionX: Float
        get() = prefs.getFloat(KEY_POS_X, DEFAULT_POS_X).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat(KEY_POS_X, value.coerceIn(0f, 1f)).apply()

    /** Vertical position as a fraction (0 = top, 1 = bottom) of the free space. */
    var positionY: Float
        get() = prefs.getFloat(KEY_POS_Y, DEFAULT_POS_Y).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat(KEY_POS_Y, value.coerceIn(0f, 1f)).apply()

    /** Remembers that the notification permission dialog was already shown once. */
    var notificationAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, value).apply()

    /** Stores both coordinates in a single write. */
    fun savePosition(x: Float, y: Float) {
        prefs.edit()
            .putFloat(KEY_POS_X, x.coerceIn(0f, 1f))
            .putFloat(KEY_POS_Y, y.coerceIn(0f, 1f))
            .apply()
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val FILE_NAME = "battery_float_settings"

        const val KEY_WANTED = "bubble_wanted"
        const val KEY_SIZE = "size_dp"
        const val KEY_OPACITY = "opacity_percent"
        const val KEY_SNAP = "snap_to_edge"
        const val KEY_RESTORE = "restore_after_reboot"
        const val KEY_POS_X = "position_x"
        const val KEY_POS_Y = "position_y"
        const val KEY_NOTIFICATION_ASKED = "notification_asked"

        const val MIN_SIZE_DP = 48
        const val MAX_SIZE_DP = 160
        const val DEFAULT_SIZE_DP = 80

        const val MIN_OPACITY = 40
        const val DEFAULT_OPACITY = 100

        const val DEFAULT_POS_X = 1f
        const val DEFAULT_POS_Y = 0.25f
    }
}
