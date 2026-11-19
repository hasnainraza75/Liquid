package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Lightweight native top-right edge swipe detector installed in WindowManager
 * (TYPE_ACCESSIBILITY_OVERLAY or TYPE_APPLICATION_OVERLAY).
 * Detects a downward swipe from the calibrated top-right corner zone and opens
 * LuxOverlayShadeActivity with real Android cross-window blur.
 */
class LuxEdgeGestureView(
    context: Context,
    private val showVisualHint: Boolean,
    private val onSwipeTriggered: () -> Unit
) : View(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeThresholdPx = (24f * resources.displayMetrics.density).coerceAtLeast(touchSlop * 1.4f)

    private var downX = 0f
    private var downY = 0f
    private var triggeredThisGesture = false

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(128, 173, 232, 255)
        style = Paint.Style.FILL
    }

    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1f
    }

    private val pillRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showVisualHint) return

        val density = resources.displayMetrics.density
        val pillWidth = (width * 0.48f).coerceIn(28f * density, 76f * density)
        val pillHeight = 4f * density
        val left = width - pillWidth - (14f * density)
        val top = (6f * density).coerceAtMost((height - pillHeight).coerceAtLeast(0f))
        pillRect.set(left, top, left + pillWidth, top + pillHeight)
        val radius = pillHeight / 2f
        canvas.drawRoundRect(pillRect, radius, radius, pillPaint)
        canvas.drawRoundRect(pillRect, radius, radius, pillStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                triggeredThisGesture = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!triggeredThisGesture) {
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (deltaY >= swipeThresholdPx && deltaY > abs(deltaX) * 1.15f) {
                        triggeredThisGesture = true
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onSwipeTriggered()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                triggeredThisGesture = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        fun launchShadeActivity(context: Context) {
            val intent = Intent(context, LuxOverlayShadeActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            runCatching { context.startActivity(intent) }
        }
    }
}
