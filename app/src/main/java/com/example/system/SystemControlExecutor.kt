package com.example.system

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.service.LuxControlAccessibilityService
import com.example.service.LuxMediaSessionBridge

/**
 * Executes real Android 15 system actions for all 16 controls with explicit
 * permission verification and error reporting. Never pretends an unsupported API
 * succeeded and never silently swallows failures.
 */
class SystemControlExecutor(
    private val context: Context,
    private val canWriteSettingsCheck: () -> Boolean = {
        Settings.System.canWrite(context.applicationContext)
    }
) {

    private val appContext = context.applicationContext

    // 1. Wi-Fi
    fun openWifiControl(): SystemActionResult {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        return launchSettingsIntent(
            primary = Intent(action),
            fallback = Intent(Settings.ACTION_WIFI_SETTINGS),
            successLabel = "Opened Android Wi-Fi & Internet Panel"
        )
    }

    // 2. Bluetooth
    fun openBluetoothControl(isCurrentlyEnabled: Boolean, hasConnectPermission: Boolean): SystemActionResult {
        if (!isCurrentlyEnabled && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasConnectPermission)) {
            val requestEnable = launchSettingsIntent(
                primary = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                fallback = Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                successLabel = "Requested Bluetooth enable via Android system dialog"
            )
            if (requestEnable !is SystemActionResult.Error) return requestEnable
        }
        return launchSettingsIntent(
            primary = Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            fallback = Intent(Settings.ACTION_SETTINGS),
            successLabel = "Opened Android Bluetooth Settings (API 33+ restricts silent toggle)"
        )
    }

    // 3. Mobile Network Information & Panel
    fun openMobileNetworkControl(): SystemActionResult {
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        }
        return launchSettingsIntent(
            primary = primary,
            fallback = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            successLabel = "Opened Android Mobile Network & Internet Panel"
        )
    }

    // 4. Airplane Mode
    fun toggleOrOpenAirplaneMode(currentlyEnabled: Boolean): SystemActionResult {
        val hasWriteSecure = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasWriteSecure) {
            val wrote = runCatching {
                Settings.Global.putInt(
                    appContext.contentResolver,
                    Settings.Global.AIRPLANE_MODE_ON,
                    if (currentlyEnabled) 0 else 1
                )
            }.getOrDefault(false)
            if (wrote) {
                // Standard APKs cannot send protected ACTION_AIRPLANE_MODE_CHANGED radio broadcast on Android 15;
                // open Airplane settings if radio did not flip so user can confirm cleanly.
                return SystemActionResult.OpenedSystemPanel(
                    "Updated AIRPLANE_MODE_ON setting. On non-platform builds, radio switch may require System Settings confirmation."
                )
            }
        }
        return launchSettingsIntent(
            primary = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS),
            successLabel = "Opened Airplane Mode Settings (privileged radio broadcast required for silent toggle)"
        )
    }

    // 5. Flashlight (Real direct Camera2 Torch API)
    fun toggleFlashlight(state: FlashlightSystemState): SystemActionResult {
        if (!state.isSupported || state.cameraId == null) {
            return SystemActionResult.UnsupportedHardware(
                "No camera flash unit detected on this device."
            )
        }
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return SystemActionResult.Error("CameraManager service is unavailable.")
        val targetState = !state.isEnabled
        return try {
            cm.setTorchMode(state.cameraId, targetState)
            SystemActionResult.Success(if (targetState) "Flashlight turned ON" else "Flashlight turned OFF")
        } catch (e: CameraAccessException) {
            val detail = when (e.reason) {
                CameraAccessException.CAMERA_IN_USE -> "Camera is currently in use by another app."
                CameraAccessException.MAX_CAMERAS_IN_USE -> "Maximum concurrent cameras already active."
                CameraAccessException.CAMERA_DISABLED -> "Camera flash disabled by device policy."
                else -> e.localizedMessage ?: "Flash hardware unavailable."
            }
            SystemActionResult.Error("Torch error: $detail")
        } catch (e: SecurityException) {
            SystemActionResult.Error("Torch security restriction: ${e.localizedMessage}")
        } catch (t: Throwable) {
            SystemActionResult.Error("Unable to toggle flashlight: ${t.localizedMessage}")
        }
    }

    // 6. Brightness (Real Settings.System direct write)
    fun setSystemBrightness(fraction: Float): SystemActionResult {
        if (!canWriteSettingsCheck()) {
            return SystemActionResult.PermissionRequired(
                permissionName = "Modify System Settings",
                message = "Grant 'Modify system settings' to control real display brightness."
            )
        }
        val clamped = fraction.coerceIn(0.02f, 1f)
        val brightnessInt = (clamped * 255f).toInt().coerceIn(5, 255)
        return try {
            val cr = appContext.contentResolver
            Settings.System.putInt(
                cr,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, brightnessInt)
            SystemActionResult.Success("Brightness set to ${(clamped * 100).toInt()}%")
        } catch (t: Throwable) {
            SystemActionResult.Error("Failed to write brightness: ${t.localizedMessage}")
        }
    }

    fun toggleAdaptiveBrightness(currentlyAuto: Boolean): SystemActionResult {
        if (!canWriteSettingsCheck()) {
            return SystemActionResult.PermissionRequired(
                permissionName = "Modify System Settings",
                message = "Grant 'Modify system settings' to toggle Adaptive Brightness."
            )
        }
        return try {
            val nextMode = if (currentlyAuto) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            }
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                nextMode
            )
            SystemActionResult.Success(
                if (nextMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                    "Adaptive Brightness ON"
                } else {
                    "Manual Brightness"
                }
            )
        } catch (t: Throwable) {
            SystemActionResult.Error("Unable to change brightness mode: ${t.localizedMessage}")
        }
    }

    // 7. Volume (Real AudioManager direct stream control)
    fun setStreamVolumeFraction(streamType: Int, fraction: Float): SystemActionResult {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return SystemActionResult.Error("AudioManager unavailable.")
        return try {
            val max = am.getStreamMaxVolume(streamType).coerceAtLeast(1)
            val target = (fraction.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
            am.setStreamVolume(streamType, target, 0)
            SystemActionResult.Success("Volume updated (${(fraction.coerceIn(0f, 1f) * 100).toInt()}%)")
        } catch (e: SecurityException) {
            SystemActionResult.PermissionRequired(
                permissionName = "Do Not Disturb Policy Access",
                message = "Ringer volume is protected while Do Not Disturb is active."
            )
        } catch (t: Throwable) {
            SystemActionResult.Error("Volume update failed: ${t.localizedMessage}")
        }
    }

    // 8. Location
    fun openLocationSettings(): SystemActionResult {
        return launchSettingsIntent(
            primary = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            fallback = Intent(Settings.ACTION_SETTINGS),
            successLabel = "Opened Android Location Settings"
        )
    }

    // 9. Screen Rotation (Real Settings.System direct toggle)
    fun toggleAutoRotate(currentlyEnabled: Boolean): SystemActionResult {
        if (!canWriteSettingsCheck()) {
            return SystemActionResult.PermissionRequired(
                permissionName = "Modify System Settings",
                message = "Grant 'Modify system settings' to toggle Auto-Rotate directly."
            )
        }
        return try {
            val next = if (currentlyEnabled) 0 else 1
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                next
            )
            SystemActionResult.Success(if (next == 1) "Auto-Rotate ON" else "Rotation Locked")
        } catch (t: Throwable) {
            SystemActionResult.Error("Auto-Rotate toggle failed: ${t.localizedMessage}")
        }
    }

    // 10. Battery Saver
    fun openBatterySaverSettings(): SystemActionResult {
        return launchSettingsIntent(
            primary = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            fallback = Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            successLabel = "Opened Android Battery Saver Settings"
        )
    }

    // 11. Do Not Disturb (Real NotificationManager direct toggle)
    fun toggleDoNotDisturb(state: DoNotDisturbSystemState): SystemActionResult {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return SystemActionResult.Error("NotificationManager unavailable.")
        if (!nm.isNotificationPolicyAccessGranted) {
            return SystemActionResult.PermissionRequired(
                permissionName = "Do Not Disturb Access",
                message = "Grant Notification Policy Access to toggle Do Not Disturb directly."
            )
        }
        return try {
            val nextFilter = if (state.isEnabled) {
                NotificationManager.INTERRUPTION_FILTER_ALL
            } else {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            }
            nm.setInterruptionFilter(nextFilter)
            SystemActionResult.Success(
                if (nextFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    "Do Not Disturb OFF"
                } else {
                    "Do Not Disturb ON (Priority)"
                }
            )
        } catch (t: Throwable) {
            SystemActionResult.Error("Do Not Disturb toggle failed: ${t.localizedMessage}")
        }
    }

    // 13. Screenshot (Real AccessibilityService GLOBAL_ACTION_TAKE_SCREENSHOT)
    fun takeNativeScreenshot(): SystemActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return SystemActionResult.UnsupportedHardware(
                "Global OS screenshots require Android 9 (API 28) or higher."
            )
        }
        if (!LuxControlAccessibilityService.isRunning.value) {
            return SystemActionResult.PermissionRequired(
                permissionName = "Accessibility Service",
                message = "Enable 'Lux Control Edge & Screenshot' in Accessibility Settings for real OS screenshots."
            )
        }
        val triggered = LuxControlAccessibilityService.requestNativeScreenshot()
        return if (triggered) {
            SystemActionResult.Success("Native Android screenshot captured")
        } else {
            SystemActionResult.Error("Android rejected screenshot request; verify Accessibility Service is active.")
        }
    }

    // 14. Media Playback Controls (Real MediaController or real AudioManager KeyEvents)
    fun mediaPlayPause(): SystemActionResult {
        if (LuxMediaSessionBridge.dispatchPlayPause()) {
            return SystemActionResult.Success("Dispatched Play/Pause to active media session")
        }
        return dispatchMediaKeyFallback(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play/Pause")
    }

    fun mediaNext(): SystemActionResult {
        if (LuxMediaSessionBridge.dispatchNext()) {
            return SystemActionResult.Success("Skipped to next track")
        }
        return dispatchMediaKeyFallback(KeyEvent.KEYCODE_MEDIA_NEXT, "Next")
    }

    fun mediaPrevious(): SystemActionResult {
        if (LuxMediaSessionBridge.dispatchPrevious()) {
            return SystemActionResult.Success("Skipped to previous track")
        }
        return dispatchMediaKeyFallback(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous")
    }

    private fun dispatchMediaKeyFallback(keyCode: Int, label: String): SystemActionResult {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return SystemActionResult.Error("AudioManager unavailable.")
        return try {
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
            SystemActionResult.Success("Sent hardware media key: $label")
        } catch (t: Throwable) {
            SystemActionResult.Error("Media control error: ${t.localizedMessage}")
        }
    }

    // 15. Cast
    fun openCastSettings(): SystemActionResult {
        return launchSettingsIntent(
            primary = Intent(Settings.ACTION_CAST_SETTINGS),
            fallback = Intent(Settings.ACTION_DISPLAY_SETTINGS),
            successLabel = "Opened Android Cast & Wireless Display Settings"
        )
    }

    // 16. Hotspot
    fun openHotspotSettings(): SystemActionResult {
        return launchSettingsIntent(
            primary = Intent("android.settings.TETHER_SETTINGS"),
            fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS),
            successLabel = "Opened Android Hotspot & Tethering Settings"
        )
    }

    // Special Access Settings Launchers
    fun openOverlayPermissionSettings(): SystemActionResult {
        return launchSettingsIntent(
            primary = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${appContext.packageName}")
            ),
            fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            successLabel = "Opened 'Display over other apps' permission screen"
        )
    }

    fun openWriteSettingsPermission(): SystemActionResult {
        return launchSettingsIntent(
            primary = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${appContext.packageName}")
            ),
            fallback = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS),
            successLabel = "Opened 'Modify system settings' permission screen"
        )
    }

    fun openDndPolicyPermission(): SystemActionResult {
        return launchSettingsIntent(
            primary = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            fallback = Intent(Settings.ACTION_SETTINGS),
            successLabel = "Opened Do Not Disturb Access settings"
        )
    }

    fun openAccessibilitySettings(): SystemActionResult {
        return launchSettingsIntent(
            primary = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            fallback = Intent(Settings.ACTION_SETTINGS),
            successLabel = "Opened Accessibility Settings"
        )
    }

    fun openNotificationListenerSettings(): SystemActionResult {
        return launchSettingsIntent(
            primary = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            fallback = Intent(Settings.ACTION_SETTINGS),
            successLabel = "Opened Notification Access settings for Media Bridge"
        )
    }

    private fun launchSettingsIntent(
        primary: Intent,
        fallback: Intent,
        successLabel: String
    ): SystemActionResult {
        primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launchedPrimary = runCatching {
            appContext.startActivity(primary)
            true
        }.getOrDefault(false)
        if (launchedPrimary) {
            return SystemActionResult.OpenedSystemPanel(successLabel)
        }
        val launchedFallback = runCatching {
            appContext.startActivity(fallback)
            true
        }.getOrDefault(false)
        return if (launchedFallback) {
            SystemActionResult.OpenedSystemPanel(successLabel)
        } else {
            SystemActionResult.Error("No Android Settings activity available for this system panel.")
        }
    }
}
