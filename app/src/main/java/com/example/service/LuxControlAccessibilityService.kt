package com.example.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.data.LuxPreferencesRepository
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Real Android AccessibilityService delivering two system-level integrations:
 * 1. Hosts an elevated TYPE_ACCESSIBILITY_OVERLAY top-right corner swipe strip
 *    (higher z-order than TYPE_APPLICATION_OVERLAY on Android 12-15).
 * 2. Executes real native OS screenshots via GLOBAL_ACTION_TAKE_SCREENSHOT (API 28+).
 */
class LuxControlAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var prefsJob: Job? = null
    private var windowManager: WindowManager? = null
    private var edgeView: LuxEdgeGestureView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        _isRunning.value = true
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager

        val repo = LuxPreferencesRepository(applicationContext)
        prefsJob?.cancel()
        prefsJob = scope.launch {
            repo.preferencesFlow.collectLatest { prefs ->
                if (prefs.edgeTriggerEnabled) {
                    attachAccessibilityEdgeTrigger(
                        widthPercent = prefs.edgeTriggerWidthPercent,
                        heightDp = prefs.edgeTriggerHeightDp,
                        showHint = prefs.showEdgeIndicatorHint
                    )
                } else {
                    removeEdgeTrigger()
                }
            }
        }
    }

    private fun attachAccessibilityEdgeTrigger(
        widthPercent: Float,
        heightDp: Int,
        showHint: Boolean
    ) {
        removeEdgeTrigger()
        val wm = windowManager ?: return
        val metrics = resources.displayMetrics
        val stripWidth = (metrics.widthPixels * widthPercent).toInt().coerceAtLeast(120)
        val stripHeight = (heightDp * metrics.density).toInt().coerceAtLeast(72)

        val params = WindowManager.LayoutParams(
            stripWidth,
            stripHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 0
        }

        val view = LuxEdgeGestureView(this, showHint) {
            LuxEdgeGestureView.launchShadeActivity(this)
        }
        runCatching {
            wm.addView(view, params)
            edgeView = view
        }
    }

    private fun removeEdgeTrigger() {
        val view = edgeView ?: return
        runCatching { windowManager?.removeView(view) }
        edgeView = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Least-privilege: does not inspect screen text or user content.
    }

    override fun onInterrupt() {
        removeEdgeTrigger()
    }

    override fun onDestroy() {
        removeEdgeTrigger()
        prefsJob?.cancel()
        scope.cancel()
        instanceRef = null
        _isRunning.value = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instanceRef: WeakReference<LuxControlAccessibilityService>? = null

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun requestNativeScreenshot(): Boolean {
            val service = instanceRef?.get() ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return runCatching {
                service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }.getOrDefault(false)
        }
    }
}
