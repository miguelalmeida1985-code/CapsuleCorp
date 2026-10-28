package com.capsulecorp.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Opens the dashboard when the screen boots (also covered by being the home app). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // Android may block starting from the background; the home-app role still opens it.
        }
    }
}
