/*
 * AOSP Android 15 SystemUI Integration Reference Hook
 * Target AOSP Path:
 *   frameworks/base/packages/SystemUI/src/com/android/systemui/shade/LuxSplitShadeInterceptPatch.kt
 *
 * Integration Point:
 *   Hook into ShadeViewController / NotificationPanelViewController touch interception
 *   (onInterceptTouchEvent / handleStatusBarTouch) on AOSP 15 builds where LuxControlCenter
 *   is installed as a platform-signed priv-app in /system_ext/priv-app/.
 *
 * Behavior:
 *   - Swipes starting in the top-left 65% of the status bar open standard Android Notifications.
 *   - Downward swipes starting in the top-right 35% of the status bar suppress AOSP Quick Settings
 *     and launch LuxOverlayShadeActivity directly at the system layer.
 */
package com.android.systemui.shade

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

class LuxSplitShadeInterceptPatch(
    private val context: Context,
    private val topRightWidthRatio: Float = 0.35f
) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var trackingTopRight = false

    /**
     * Call from NotificationShadeWindowView.onInterceptTouchEvent(ev).
     * Returns true when this handler consumes the top-right downward gesture
     * so AOSP QuickSettings does NOT expand underneath Lux Control Center.
     */
    fun maybeInterceptTopRightQuickSettings(
        ev: MotionEvent,
        displayWidthPx: Int,
        statusBarHeightPx: Int
    ): Boolean {
        val rightThresholdX = displayWidthPx * (1f - topRightWidthRatio)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                trackingTopRight = startX >= rightThresholdX && startY <= statusBarHeightPx * 1.35f
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!trackingTopRight) return false
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                if (dy > touchSlop * 1.5f && dy > abs(dx) * 1.2f) {
                    trackingTopRight = false
                    launchLuxControlShade()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                trackingTopRight = false
                return false
            }
        }
        return false
    }

    private fun launchLuxControlShade() {
        val intent = Intent().apply {
            component = ComponentName(
                "com.aistudio.luxcontrol.vqxkpm",
                "com.example.service.LuxOverlayShadeActivity"
            )
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        context.startActivityAsUser(intent, UserHandle.CURRENT)
    }
}
