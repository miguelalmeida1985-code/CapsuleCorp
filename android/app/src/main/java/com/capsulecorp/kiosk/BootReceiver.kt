package com.capsulecorp.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Opens the dashboard when the screen boots, if "Abrir ao ligar" is on (a tablet's
 * default). A TV leaves it off, so CapsuleOS only opens when it is picked there.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(context).openOnBoot) return
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // Android may block starting from the background; the home-app role still opens it.
        }
    }
}
