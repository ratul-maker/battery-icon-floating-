package com.example.battery_float

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings the bubble back after the phone restarts or after the app is updated (installing an
 * update stops the running service). It only acts if the bubble was on before, the user left
 * "Restore after restart" enabled, and the overlay permission is still granted.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val settings = AppSettings(context)
        if (!settings.bubbleWanted) return
        if (settings.restoreAfterReboot && FloatingBatteryService.canDrawOverlays(context)) {
            if (!FloatingBatteryService.start(context)) settings.bubbleWanted = false
        } else {
            settings.bubbleWanted = false
        }
    }
}
