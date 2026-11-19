package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.LuxPreferencesRepository
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
 * Foreground service (specialUse) hosting the top-right swipe gesture zone using
 * TYPE_APPLICATION_OVERLAY when the AccessibilityService overlay is not active.
 */
class LuxEdgeGestureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var windowManager: WindowManager? = null
    private var edgeView: LuxEdgeGestureView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            _isRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }

        ensureNotificationChannel()
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _isRunning.value = true
        observePreferences()
        return START_STICKY
    }

    private fun observePreferences() {
        collectJob?.cancel()
        val repo = LuxPreferencesRepository(applicationContext)
        collectJob = serviceScope.launch {
            repo.preferencesFlow.collectLatest { prefs ->
                if (!Settings.canDrawOverlays(this@LuxEdgeGestureService)) {
                    stopSelf()
                    return@collectLatest
                }
                // Avoid duplicate edge strip if AccessibilityService is already hosting an elevated overlay
                if (LuxControlAccessibilityService.isRunning.value) {
                    removeEdgeView()
                } else {
                    attachOrUpdateEdgeView(
                        widthPercent = prefs.edgeTriggerWidthPercent,
                        heightDp = prefs.edgeTriggerHeightDp,
                        showHint = prefs.showEdgeIndicatorHint
                    )
                }
            }
        }
    }

    private fun attachOrUpdateEdgeView(widthPercent: Float, heightDp: Int, showHint: Boolean) {
        removeEdgeView()
        val wm = windowManager ?: return
        val metrics = resources.displayMetrics
        val stripWidth = (metrics.widthPixels * widthPercent).toInt().coerceAtLeast(120)
        val stripHeight = (heightDp * metrics.density).toInt().coerceAtLeast(72)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            stripWidth,
            stripHeight,
            overlayType,
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

    private fun removeEdgeView() {
        val view = edgeView ?: return
        runCatching { windowManager?.removeView(view) }
        edgeView = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_overlay_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_overlay_desc)
                setShowBadge(false)
            }
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openShadeIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, LuxOverlayShadeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openMainIntent = PendingIntent.getActivity(
            this,
            102,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            103,
            Intent(this, LuxEdgeGestureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_body))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openMainIntent)
            .addAction(0, getString(R.string.action_launch_overlay), openShadeIntent)
            .addAction(0, "Disable", stopIntent)
            .build()
    }

    override fun onDestroy() {
        removeEdgeView()
        collectJob?.cancel()
        serviceScope.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "lux_edge_gesture_channel"
        private const val NOTIFICATION_ID = 4101
        const val ACTION_STOP = "com.example.action.STOP_EDGE_GESTURE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            val intent = Intent(context, LuxEdgeGestureService::class.java)
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, LuxEdgeGestureService::class.java))
            }
        }
    }
}
