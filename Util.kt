package com.example.battery_float

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Registers a receiver for protected system broadcasts (battery, screen on/off) and returns the
 * sticky intent if there is one.
 *
 * Android 13+ wants an explicit exported flag. The Android documentation says a receiver that
 * listens to broadcasts sent by the system must use RECEIVER_EXPORTED (RECEIVER_NOT_EXPORTED can
 * miss broadcasts sent by privileged apps). This is safe here: ACTION_BATTERY_CHANGED,
 * ACTION_SCREEN_ON and ACTION_SCREEN_OFF are protected broadcasts, which only the system itself
 * is allowed to send, so no other app can spoof them.
 */
internal fun Context.registerSystemReceiver(
    receiver: BroadcastReceiver,
    filter: IntentFilter
): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    } else {
        registerReceiver(receiver, filter)
    }

/** Unregisters a receiver and ignores the error Android throws if it was not registered. */
internal fun Context.unregisterQuietly(receiver: BroadcastReceiver) {
    try {
        unregisterReceiver(receiver)
    } catch (e: IllegalArgumentException) {
        // Already unregistered; nothing to do.
    }
}
