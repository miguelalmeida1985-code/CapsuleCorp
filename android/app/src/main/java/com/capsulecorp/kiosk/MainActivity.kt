package com.capsulecorp.kiosk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * The CapsuleOS dashboard, full screen. On a tablet it adds the child lock and kiosk
 * mode; on a TV it hands the remote's keys to the page (which moves its own highlight).
 */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var lock: ChildLockView
    private lateinit var offline: TextView
    private lateinit var sleepCover: View
    private lateinit var motion: MotionDetector
    private var asleep = false
    private var askedCamera = false
    private val handler = Handler(Looper.getMainLooper())
    private var loadedUrl = ""
    private var failed = false
    private val backPresses = ArrayDeque<Long>()

    private val goToSleep = Runnable { sleep() }
    private val retry = Runnable { if (failed && loadedUrl.isNotEmpty()) web.loadUrl(loadedUrl) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.background)) }
        web = createWebView()
        offline = TextView(this).apply {
            setText(R.string.offline)
            setTextColor(getColor(R.color.text))
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(200, 11, 15, 23))
            visibility = View.GONE
        }
        lock = ChildLockView(this, onLockChange = { prefs.locked = it }, onSettings = ::askForSettings)
        root.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(offline, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        root.addView(lock, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        sleepCover = View(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        root.addView(sleepCover, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        motion = MotionDetector(this) { someoneHere() }
        setContentView(root)

        // First run: nothing to show until the dashboard's address is set.
        if (prefs.url.isBlank()) openSettings()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        val tv = prefs.isTv
        lock.showLock = !tv && prefs.childLock
        lock.locked = lock.showLock && prefs.locked
        val url = prefs.dashboardUrl()
        if (url.isNotEmpty() && url != loadedUrl) {
            loadedUrl = url
            failed = false
            web.loadUrl(url)
        }
        if (tv) web.requestFocus()
        if (prefs.url.isNotBlank()) Kiosk.apply(this, prefs.kiosk)
        startPresence()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // A screen that's off or behind another app stops running the page's scripts and animations.
    override fun onStart() {
        super.onStart()
        web.onResume()
        web.resumeTimers()
    }

    override fun onStop() {
        motion.stop()
        handler.removeCallbacks(goToSleep)
        wake(reschedule = false)
        web.onPause()
        web.pauseTimers()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        // Keep the direct-access cookie on disk, so a power cut doesn't ask for ?key= again.
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        motion.stop()
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() = WebView(this).apply {
        CookieManager.getInstance().setAcceptCookie(true)
        setBackgroundColor(getColor(R.color.background))
        isFocusable = true
        isFocusableInTouchMode = true
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // The dashboard sizes itself; ignore Android's font size and zoom.
            textZoom = 100
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = "$userAgentString CapsuleOS/${BuildConfig.VERSION_NAME}"
        }
        webViewClient = object : WebViewClient() {
            // Every link stays inside the app.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                failed = false
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!failed) offline.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                showOffline()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // The page crashed (e.g. out of memory): start over with a fresh one.
                recreate()
                return true
            }
        }
    }

    private fun showOffline() {
        failed = true
        offline.visibility = View.VISIBLE
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, RETRY_MS)
    }

    // Presence: the screen goes dark after a while with nobody around and lights up again
    // when the front camera sees movement or the screen is touched. It never really turns
    // off (that would stop the camera); the backlight goes to zero behind a black cover.

    private val presence get() = !prefs.isTv

    private fun startPresence() {
        if (!presence) {
            motion.stop()
            wake(reschedule = false)
            return
        }
        if (prefs.motionWake) {
            if (motion.hasPermission()) motion.start()
            else if (!askedCamera) {
                askedCamera = true
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
            }
        } else {
            motion.stop()
        }
        scheduleSleep()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST && motion.hasPermission() && prefs.motionWake) motion.start()
    }

    /** Someone is here: a touch, a key or movement in front of the camera. */
    private fun someoneHere() {
        if (asleep) wake() else scheduleSleep()
    }

    private fun scheduleSleep() {
        handler.removeCallbacks(goToSleep)
        val seconds = prefs.sleepAfter
        if (presence && seconds > 0) handler.postDelayed(goToSleep, seconds * 1000L)
    }

    private fun sleep() {
        if (asleep) return
        asleep = true
        sleepCover.visibility = View.VISIBLE
        // Nothing to draw while dark; the page itself keeps running and stays up to date.
        web.visibility = View.INVISIBLE
        setBrightness(0f)
    }

    private fun wake(reschedule: Boolean = true) {
        if (asleep) {
            asleep = false
            setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            web.visibility = View.VISIBLE
            sleepCover.visibility = View.GONE
        }
        if (reschedule) scheduleSleep()
    }

    private fun setBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (asleep) {
            // The touch that wakes the screen doesn't press anything.
            if (event.actionMasked == MotionEvent.ACTION_DOWN) wake()
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) scheduleSleep()
        return super.dispatchTouchEvent(event)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (asleep) {
            if (event.action == KeyEvent.ACTION_DOWN) wake()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) scheduleSleep()
        when (event.keyCode) {
            // Back goes to the page as Escape (closes a pop-up or the views menu) and never
            // leaves the app. Five quick presses open the settings, for remotes without Menu.
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    sendEscape()
                    countBack()
                }
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                if (event.action == KeyEvent.ACTION_UP) askForSettings()
                return true
            }
        }
        // While locked, a keyboard or remote can't press anything either.
        if (lock.locked) return true
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Back is handled in dispatchKeyEvent")
    override fun onBackPressed() {
        sendEscape()
    }

    private fun sendEscape() {
        if (lock.locked) return
        web.evaluateJavascript(
            "window.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',code:'Escape',keyCode:27,bubbles:true,cancelable:true}))",
            null,
        )
    }

    private fun countBack() {
        val now = SystemClock.uptimeMillis()
        backPresses.addLast(now)
        while (backPresses.isNotEmpty() && now - backPresses.first() > 2500) backPresses.removeFirst()
        if (backPresses.size >= 5) {
            backPresses.clear()
            askForSettings()
        }
    }

    /** The settings, behind the PIN when there is one. */
    private fun askForSettings() {
        val pin = prefs.pin
        if (pin.isEmpty()) return openSettings()
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            gravity = Gravity.CENTER
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.pin_title)
            .setView(field)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (field.text.toString() == pin) openSettings()
                else Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    companion object {
        private const val RETRY_MS = 10_000L
        private const val CAMERA_REQUEST = 1
    }
}
