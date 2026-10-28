package com.capsulecorp.kiosk

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView

/** The dashboard's address, this screen's mode, the PIN, the child lock and kiosk mode. */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        val url = findViewById<EditText>(R.id.url)
        val mode = findViewById<RadioGroup>(R.id.mode)
        val extra = findViewById<EditText>(R.id.extra)
        val pin = findViewById<EditText>(R.id.pin)
        val childLock = findViewById<CheckBox>(R.id.child_lock)
        val kiosk = findViewById<CheckBox>(R.id.kiosk)
        val owner = Kiosk.isOwner(this)

        findViewById<TextView>(R.id.version).text = getString(R.string.version_line, BuildConfig.VERSION_NAME)
        url.setText(prefs.url)
        mode.check(
            when (prefs.mode) {
                "tablet" -> R.id.mode_tablet
                "tv" -> R.id.mode_tv
                else -> R.id.mode_auto
            },
        )
        extra.setText(prefs.extra)
        pin.setText(prefs.pin)
        childLock.isChecked = prefs.childLock
        kiosk.isChecked = prefs.kiosk
        findViewById<TextView>(R.id.kiosk_help).setText(if (owner) R.string.kiosk_help_owner else R.string.kiosk_help_pinning)

        findViewById<Button>(R.id.save).setOnClickListener {
            prefs.url = url.text.toString()
            prefs.mode = when (mode.checkedRadioButtonId) {
                R.id.mode_tablet -> "tablet"
                R.id.mode_tv -> "tv"
                else -> "auto"
            }
            prefs.extra = extra.text.toString()
            prefs.pin = pin.text.toString()
            prefs.childLock = childLock.isChecked
            if (!childLock.isChecked) prefs.locked = false
            prefs.kiosk = kiosk.isChecked
            finish()
        }

        // Leaves the app, e.g. to reach Android's settings. Kiosk mode comes back when it opens again.
        findViewById<Button>(R.id.exit).setOnClickListener {
            Kiosk.stop(this)
            finishAffinity()
        }

        findViewById<Button>(R.id.remove_owner).apply {
            visibility = if (owner) View.VISIBLE else View.GONE
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setMessage(R.string.remove_owner_confirm)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        Kiosk.removeOwner(this@SettingsActivity)
                        prefs.kiosk = false
                        finishAffinity()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }
}
