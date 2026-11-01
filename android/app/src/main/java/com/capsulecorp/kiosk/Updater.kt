package com.capsulecorp.kiosk

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.CookieManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Updates the app from a notification. When the dashboard is newer than the app (it says
 * so through the page bridge), a notification offers the update; a tap downloads that
 * version's APK straight into Android's installer (nothing is left in Downloads) and
 * Android asks "Instalar?". The APK comes from the GitHub release, or else from the copy
 * in Home Assistant (/local/capsuleos.apk, through the dashboard's server).
 */
object Updater {
    const val ACTION_INSTALL = "com.capsulecorp.kiosk.INSTALL"
    const val EXTRA_VERSION = "version"
    private const val CHANNEL = "updates"
    private const val NOTIFY_ID = 7
    private const val RELEASES = "https://github.com/miguelalmeida1985-code/CapsuleCorp/releases/download"

    /** a > b, comparing 0.4.10 and 0.4.9 as numbers. */
    fun isNewer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** The notification for [version], once per version. */
    fun offer(context: Context, version: String) {
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        if (prefs.getString("offered", "") == version) return
        val tap = Intent(context, MainActivity::class.java)
            .setAction(ACTION_INSTALL)
            .putExtra(EXTRA_VERSION, version)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        notify(context, context.getString(R.string.update_title, version), context.getString(R.string.update_text),
            PendingIntent.getActivity(context, 0, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        prefs.edit().putString("offered", version).apply()
    }

    /** Download [version] into the installer. Asks once for "install unknown apps" first. */
    fun install(activity: Activity, version: String, dashboard: String) {
        val context = activity.applicationContext
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putString("waiting", version).apply()
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        notify(context, context.getString(R.string.update_downloading, version), "", null, ongoing = true)
        thread(name = "update") {
            try {
                val conn = open(version, dashboard)
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (conn.contentLengthLong > 0) setSize(conn.contentLengthLong)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    conn.inputStream.use { input ->
                        session.openWrite("capsuleos.apk", 0, conn.contentLengthLong).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    val done = PendingIntent.getBroadcast(context, id, Intent(context, InstallReceiver::class.java), flags)
                    session.commit(done.intentSender)
                }
                cancel(context)
            } catch (e: Exception) {
                notify(context, context.getString(R.string.update_failed), e.message ?: "", null)
                // Let the same version be offered again.
                context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().remove("offered").apply()
            }
        }
    }

    /** Back from "install unknown apps": carry on with the version that was waiting for it. */
    fun resume(activity: Activity, dashboard: String) {
        val prefs = activity.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val version = prefs.getString("waiting", "") ?: ""
        if (version.isEmpty() || !activity.packageManager.canRequestPackageInstalls()) return
        prefs.edit().remove("waiting").apply()
        install(activity, version, dashboard)
    }

    // The release first; the copy in Home Assistant if GitHub can't be reached.
    private fun open(version: String, dashboard: String): HttpURLConnection {
        val urls = mutableListOf("$RELEASES/v$version/CapsuleOS-$version.apk")
        if (dashboard.isNotBlank()) {
            val base = dashboard.substringBefore('#').substringBefore('?').let { if (it.endsWith('/')) it else "$it/" }
            urls += "${base}ha/local/capsuleos.apk"
        }
        var last: Exception = IOException("no address")
        for (u in urls) {
            try {
                val conn = URL(u).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                CookieManager.getInstance().getCookie(u)?.let { conn.setRequestProperty("Cookie", it) }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) return conn
                last = IOException("HTTP ${conn.responseCode}")
                conn.disconnect()
            } catch (e: Exception) {
                last = e
            }
        }
        throw last
    }

    private fun notify(context: Context, title: String, text: String, tap: PendingIntent?, ongoing: Boolean = false) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(if (ongoing) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .apply { if (ongoing) setProgress(0, 0, true) }
            .apply { if (tap != null) setContentIntent(tap) }
            .build()
        try { nm.notify(NOTIFY_ID, n) } catch (_: SecurityException) {}
    }

    private fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(NOTIFY_ID)

    /** The installer's answer: show its "Instalar?" screen, or say it failed. */
    class InstallReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> Unit
                else -> {
                    context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().remove("offered").apply()
                    notify(context, context.getString(R.string.update_failed), intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "", null)
                }
            }
        }
    }
}
