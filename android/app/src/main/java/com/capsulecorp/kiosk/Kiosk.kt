package com.capsulecorp.kiosk

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

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
            setHome(activity, true)
            dpm.addPersistentPreferredActivity(admin, home, homeAlias(activity))
            alwaysOn(dpm, admin, activity)
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

    /**
     * The dashboard is the only thing this screen shows: no Android lock screen, the
     * screen stays on while charging even if the app isn't running, and the camera
     * for waking the screen is granted without asking.
     */
    private fun alwaysOn(dpm: DevicePolicyManager, admin: ComponentName, context: Context) {
        try {
            dpm.setKeyguardDisabled(admin, true)
        } catch (_: Exception) {
            // Refused while a PIN or pattern lock is set on the tablet.
        }
        try {
            // AC, USB and wireless charging.
            dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "7")
        } catch (_: Exception) {
        }
        try {
            dpm.setPermissionGrantState(
                admin, context.packageName, Manifest.permission.CAMERA,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
            )
        } catch (_: Exception) {
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
        setHome(activity, false)
    }

    private fun homeAlias(context: Context) = ComponentName(context.packageName, "${context.packageName}.Home")

    /** Offers the app as a home screen only while it's a kiosk; otherwise the screen keeps its own launcher. */
    private fun setHome(context: Context, on: Boolean) {
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val pm = context.packageManager
        if (pm.getComponentEnabledSetting(homeAlias(context)) != state) {
            pm.setComponentEnabledSetting(homeAlias(context), state, PackageManager.DONT_KILL_APP)
        }
    }

    /** Gives up Device Owner, so the tablet can be set up normally again. */
    @Suppress("DEPRECATION")
    fun removeOwner(activity: Activity) {
        if (!isOwner(activity)) return
        stop(activity)
        val dpm = dpm(activity)
        dpm.setLockTaskPackages(admin(activity), emptyArray())
        try {
            dpm.setKeyguardDisabled(admin(activity), false)
        } catch (_: Exception) {
        }
        dpm.clearDeviceOwnerApp(activity.packageName)
    }
}
