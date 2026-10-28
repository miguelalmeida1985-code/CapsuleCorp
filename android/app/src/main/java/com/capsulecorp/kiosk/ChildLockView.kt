package com.capsulecorp.kiosk

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Sits on top of the dashboard. A small padlock at the top: tap it to lock, and while
 * locked every touch is swallowed (the dashboard keeps updating). Unlocking takes
 * holding the padlock for [HOLD_MS], with a ring filling up around it.
 *
 * It also hides the way into the settings: five quick taps in the top-right corner.
 */
@SuppressLint("ViewConstructor")
class ChildLockView(
    context: Context,
    private val onLockChange: (Boolean) -> Unit,
    private val onSettings: () -> Unit,
) : View(context) {

    var locked = false
        set(v) {
            field = v
            invalidate()
        }

    /** Off on a TV or when the lock is turned off in the settings: only the corner taps remain. */
    var showLock = true
        set(v) {
            field = v
            invalidate()
        }

    private val dp = resources.displayMetrics.density
    private val radius = 20 * dp
    private val offset = 10 * dp
    private val corner = 64 * dp

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 11, 15, 23) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * dp
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * dp
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.accent)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14 * dp
        textAlign = Paint.Align.CENTER
    }
    private val hintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 11, 15, 23) }
    private val shackle = Path()
    private val oval = RectF()

    private var progress = 0f
    private var holding: ValueAnimator? = null
    private var tapDown = false
    private var hintUntil = 0L
    private val cornerTaps = ArrayDeque<Long>()

    private fun cx() = width / 2f
    private fun cy() = offset + radius

    private fun onLock(x: Float, y: Float): Boolean {
        val dx = x - cx()
        val dy = y - cy()
        val r = radius + 12 * dp // a little bigger than it looks, for fingers
        return dx * dx + dy * dy <= r * r
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val onButton = showLock && onLock(e.x, e.y)
        if (!locked) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (onButton) {
                        tapDown = true
                        return true
                    }
                    // Not ours: let the touch through to the dashboard.
                    if (e.x > width - corner && e.y < corner) countCornerTap()
                    return false
                }
                MotionEvent.ACTION_UP -> {
                    if (tapDown && onButton) lock()
                    tapDown = false
                }
                MotionEvent.ACTION_CANCEL -> tapDown = false
            }
            return tapDown
        }
        // Locked: every touch stops here.
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (onButton) startHold() else showHint()
            MotionEvent.ACTION_MOVE -> if (holding != null && !onButton) cancelHold()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelHold()
        }
        return true
    }

    private fun countCornerTap() {
        val now = SystemClock.uptimeMillis()
        cornerTaps.addLast(now)
        while (cornerTaps.isNotEmpty() && now - cornerTaps.first() > 3000) cornerTaps.removeFirst()
        if (cornerTaps.size >= 5) {
            cornerTaps.clear()
            onSettings()
        }
    }

    private fun lock() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        locked = true
        onLockChange(true)
    }

    private fun startHold() {
        cancelHold()
        holding = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = HOLD_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
                if (progress >= 1f && holding === this) {
                    holding = null
                    progress = 0f
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    locked = false
                    onLockChange(false)
                }
            }
            start()
        }
    }

    private fun cancelHold() {
        holding?.let {
            holding = null
            it.cancel()
        }
        progress = 0f
        invalidate()
    }

    private fun showHint() {
        hintUntil = SystemClock.uptimeMillis() + 2500
        invalidate()
        postInvalidateDelayed(2600)
    }

    override fun onDraw(canvas: Canvas) {
        if (!showLock) return
        val cx = cx()
        val cy = cy()
        val alpha = if (locked) 235 else 120
        bg.alpha = if (locked) 190 else 110
        stroke.alpha = alpha
        fill.alpha = alpha
        canvas.drawCircle(cx, cy, radius, bg)

        // Padlock: a body and a shackle, lifted on one side when unlocked.
        val w = radius * 0.9f
        val h = radius * 0.7f
        val bodyTop = cy - h * 0.15f
        oval.set(cx - w / 2, bodyTop, cx + w / 2, bodyTop + h)
        canvas.drawRoundRect(oval, 2.5f * dp, 2.5f * dp, fill)
        val sw = w * 0.62f
        val lift = if (locked) 0f else radius * 0.28f
        shackle.reset()
        shackle.moveTo(cx - sw / 2, bodyTop)
        shackle.lineTo(cx - sw / 2, bodyTop - radius * 0.32f - lift)
        oval.set(cx - sw / 2, bodyTop - radius * 0.32f - sw / 2 - lift, cx + sw / 2, bodyTop - radius * 0.32f + sw / 2 - lift)
        shackle.arcTo(oval, 180f, 180f)
        shackle.lineTo(cx + sw / 2, bodyTop - (if (locked) 0f else radius * 0.05f) - lift * 0.4f)
        canvas.drawPath(shackle, stroke)

        if (progress > 0f) {
            oval.set(cx - radius - 3 * dp, cy - radius - 3 * dp, cx + radius + 3 * dp, cy + radius + 3 * dp)
            canvas.drawArc(oval, -90f, 360f * progress, false, ring)
        }

        if (locked && SystemClock.uptimeMillis() < hintUntil) {
            val text = context.getString(R.string.lock_hint)
            val tw = hintPaint.measureText(text)
            val ty = cy + radius + 30 * dp
            oval.set(cx - tw / 2 - 14 * dp, ty - 22 * dp, cx + tw / 2 + 14 * dp, ty + 10 * dp)
            canvas.drawRoundRect(oval, 16 * dp, 16 * dp, hintBg)
            canvas.drawText(text, cx, ty, hintPaint)
        }
    }

    companion object {
        const val HOLD_MS = 3000L
    }
}
