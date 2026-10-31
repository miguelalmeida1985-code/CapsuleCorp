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

    /** The app's own padlock. Off by default: the dashboard has its own child lock card. */
    var childLock: Boolean
        get() = sp.getBoolean("childLock", false)
        set(v) = sp.edit().putBoolean("childLock", v).apply()

    /** Kept across restarts, so a reboot doesn't unlock the screen. */
    var locked: Boolean
        get() = sp.getBoolean("locked", false)
        set(v) = sp.edit().putBoolean("locked", v).apply()

    /** Wake the screen when the front camera sees someone (tablet only). */
    var motionWake: Boolean
        get() = sp.getBoolean("motionWake", true)
        set(v) = sp.edit().putBoolean("motionWake", v).apply()

    /** Seconds without movement or touch before the screen goes dark; 0 = never. */
    var sleepAfter: Int
        get() = sp.getInt("sleepAfter", 120)
        set(v) = sp.edit().putInt("sleepAfter", v.coerceAtLeast(0)).apply()

    var kiosk: Boolean
        get() = sp.getBoolean("kiosk", true)
        set(v) = sp.edit().putBoolean("kiosk", v).apply()

    val isTv: Boolean
        get() = isTv(mode)

    /** Whether [m] ("auto", "tablet" or "tv") means a TV on this screen. */
    fun isTv(m: String): Boolean = when (m) {
        "tv" -> true
        "tablet" -> false
        else -> detectTv(context)
    }

    /**
     * Open the dashboard when the screen boots. Until it is changed it follows the mode:
     * on for a tablet, off for a TV, where CapsuleOS only opens when it is picked.
     */
    var openOnBoot: Boolean
        get() = if (sp.contains("openOnBoot")) sp.getBoolean("openOnBoot", true) else !isTv
        set(v) = sp.edit().putBoolean("openOnBoot", v).apply()

    /** Back to following the mode. */
    fun resetOpenOnBoot() = sp.edit().remove("openOnBoot").apply()

    val openOnBootIsSet: Boolean get() = sp.contains("openOnBoot")

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
