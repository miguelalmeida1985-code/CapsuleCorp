package com.capsulecorp.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Keeps the app on screen. As Device Owner (set once over ADB) the app is allowed to
 * lock itself in and be the home screen; without it, Android's screen pinning is used.
 */
object Kiosk {
    private fun dpm(context: Context) = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private fun admin(context: Context) = ComponentName(context, AdminReceiver::class.java)

    fun isOwner(context: Context) = dpm(context).isDeviceOwnerApp(context.packageName)

    private fun inLockTask(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /** Locks the app in when kiosk mode is on. Safe to call on every resume. */
    fun apply(activity: Activity, enabled: Boolean) {
        if (!enabled) return stop(activity)
        val owner = isOwner(activity)
        if (owner) {
            val dpm = dpm(activity)
            val admin = admin(activity)
            dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // No status bar, no home or recents buttons, no notifications.
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(admin, home, ComponentName(activity, MainActivity::class.java))
        }
        // Without Device Owner this asks once to pin the screen.
        if (!inLockTask(activity)) {
            try {
                activity.startLockTask()
            } catch (_: Exception) {
                // Not allowed here (e.g. some TVs); the app still runs full screen.
            }
        }
    }

    /** Lets the screen leave the app (until kiosk mode is applied again). */
    fun stop(activity: Activity) {
        if (inLockTask(activity)) {
            try {
                activity.stopLockTask()
            } catch (_: Exception) {
            }
        }
        if (isOwner(activity)) dpm(activity).clearPackagePersistentPreferredActivities(admin(activity), activity.packageName)
    }

    /** Gives up Device Owner, so the tablet can be set up normally again. */
    @Suppress("DEPRECATION")
    fun removeOwner(activity: Activity) {
        if (!isOwner(activity)) return
        stop(activity)
        val dpm = dpm(activity)
        dpm.setLockTaskPackages(admin(activity), emptyArray())
        dpm.clearDeviceOwnerApp(activity.packageName)
    }
}
