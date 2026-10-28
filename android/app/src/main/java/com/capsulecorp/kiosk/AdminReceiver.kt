package com.capsulecorp.kiosk

import android.app.admin.DeviceAdminReceiver

/** Needed to make the app the Device Owner (adb shell dpm set-device-owner …). */
class AdminReceiver : DeviceAdminReceiver()
