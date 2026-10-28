package com.capsulecorp.kiosk

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** What the app remembers on this screen. */
class Prefs(private val context: Context) {
    private val sp = context.getSharedPreferences("capsule", Context.MODE_PRIVATE)

    var url: String
        get() = sp.getString("url", "") ?: ""
        set(v) = sp.edit().putString("url", v.trim()).apply()

    /** "auto", "tablet" or "tv". */
    var mode: String
        get() = sp.getString("mode", "auto") ?: "auto"
        set(v) = sp.edit().putString("mode", v).apply()

    /** Extra address options, e.g. "effects=lite&fit=screen". */
    var extra: String
        get() = sp.getString("extra", "") ?: ""
        set(v) = sp.edit().putString("extra", v.trim()).apply()

    var pin: String
        get() = sp.getString("pin", "") ?: ""
        set(v) = sp.edit().putString("pin", v.trim()).apply()

    var childLock: Boolean
        get() = sp.getBoolean("childLock", true)
        set(v) = sp.edit().putBoolean("childLock", v).apply()

    /** Kept across restarts, so a reboot doesn't unlock the screen. */
    var locked: Boolean
        get() = sp.getBoolean("locked", false)
        set(v) = sp.edit().putBoolean("locked", v).apply()

    var kiosk: Boolean
        get() = sp.getBoolean("kiosk", true)
        set(v) = sp.edit().putBoolean("kiosk", v).apply()

    val isTv: Boolean
        get() = when (mode) {
            "tv" -> true
            "tablet" -> false
            else -> detectTv(context)
        }

    /** The dashboard's address with this screen's options added. */
    fun dashboardUrl(): String = buildDashboardUrl(url, isTv, extra, BuildConfig.VERSION_NAME)

    companion object {
        fun detectTv(context: Context): Boolean {
            val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            return ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }
    }
}

/**
 * Adds CapsuleOS's screen options to [base]: ?tv on a TV (remote control, no editing),
 * ?noedit on a tablet, the user's [extra] options and ?app_version so the version badge
 * shows this app's version too. Options already in [base] are left as they are.
 */
fun buildDashboardUrl(base: String, tv: Boolean, extra: String, version: String): String {
    if (base.isBlank()) return ""
    var address = base.trim()
    if (!address.contains("://")) address = "http://$address"
    val hash = address.indexOf('#').let { if (it >= 0) address.substring(it) else "" }
    var rest = address.removeSuffix(hash)
    val query = rest.substringAfter('?', "")
    val have = query.split('&').filter { it.isNotEmpty() }.map { it.substringBefore('=') }.toMutableSet()

    val add = mutableListOf<String>()
    fun option(o: String) {
        val key = o.substringBefore('=')
        if (key.isNotEmpty() && have.add(key)) add += o
    }
    option(if (tv) "tv" else "noedit")
    extra.trim().trimStart('?', '&').split('&').filter { it.isNotBlank() }.forEach(::option)
    option("app_version=$version")

    if (add.isEmpty()) return address
    rest += if (rest.contains('?')) (if (rest.endsWith('?') || rest.endsWith('&')) "" else "&") else "?"
    return rest + add.joinToString("&") + hash
}
