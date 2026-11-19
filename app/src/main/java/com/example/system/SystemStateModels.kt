package com.example.system

import android.graphics.Bitmap

data class WifiSystemState(
    val isEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val networkLabel: String = "Off"
)

data class BluetoothSystemState(
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false,
    val hasPermission: Boolean = false,
    val statusLabel: String = "Unavailable"
)

data class MobileNetworkSystemState(
    val isSupported: Boolean = false,
    val isCellularActive: Boolean = false,
    val isMobileDataEnabled: Boolean = false,
    val carrierName: String = "No SIM",
    val networkGeneration: String = "—",
    val signalLevel: Int = 0, // 0..4
    val hasPhoneStatePermission: Boolean = false
)

data class FlashlightSystemState(
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false,
    val cameraId: String? = null,
    val statusNote: String = "Checking…"
)

data class BrightnessSystemState(
    val level: Int = 128, // 0..255
    val isAutomatic: Boolean = false,
    val canWriteSystemSettings: Boolean = false
) {
    val fraction: Float
        get() = (level.coerceIn(0, 255) / 255f)

    val percent: Int
        get() = ((fraction * 100f).toInt()).coerceIn(0, 100)
}

data class VolumeStreamInfo(
    val streamType: Int,
    val label: String,
    val current: Int,
    val max: Int
) {
    val fraction: Float
        get() = if (max <= 0) 0f else (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)

    val percent: Int
        get() = (fraction * 100f).toInt().coerceIn(0, 100)
}

data class VolumeSystemState(
    val media: VolumeStreamInfo = VolumeStreamInfo(3, "Media", 0, 15),
    val ring: VolumeStreamInfo = VolumeStreamInfo(2, "Ring", 0, 7),
    val alarm: VolumeStreamInfo = VolumeStreamInfo(4, "Alarm", 0, 7),
    val notification: VolumeStreamInfo = VolumeStreamInfo(5, "Notify", 0, 7),
    val ringerModeLabel: String = "Normal"
)

data class BatterySystemState(
    val percent: Int = 0,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false
)

data class DoNotDisturbSystemState(
    val isEnabled: Boolean = false,
    val filterLabel: String = "Off",
    val hasPolicyAccess: Boolean = false
)

data class MediaPlaybackState(
    val hasNotificationAccess: Boolean = false,
    val isPlaying: Boolean = false,
    val hasActiveSession: Boolean = false,
    val title: String = "No Active Media",
    val artist: String = "Tap play or open a media app",
    val sourcePackage: String? = null,
    val albumArt: Bitmap? = null
)

data class CastSystemState(
    val isCasting: Boolean = false,
    val activeRouteName: String = "Not Connected"
)

data class HotspotSystemState(
    val isEnabled: Boolean? = null, // null when OEM blocks unprivileged AP state probe
    val statusLabel: String = "System Settings"
)

sealed interface ScreenRecordingState {
    data object Idle : ScreenRecordingState
    data class Recording(val elapsedSeconds: Long, val withMicrophone: Boolean) : ScreenRecordingState
    data class Saved(val displayName: String) : ScreenRecordingState
    data class Error(val message: String) : ScreenRecordingState
}

data class PermissionHealthSnapshot(
    val canDrawOverlays: Boolean = false,
    val isAccessibilityServiceRunning: Boolean = false,
    val canWriteSettings: Boolean = false,
    val hasDndPolicyAccess: Boolean = false,
    val hasNotificationListenerAccess: Boolean = false,
    val hasBluetoothConnectPermission: Boolean = false,
    val hasReadPhoneStatePermission: Boolean = false,
    val hasPostNotificationsPermission: Boolean = false,
    val hasRecordAudioPermission: Boolean = false,
    val hasWriteSecureSettingsGrant: Boolean = false,
    val isCrossWindowBlurSupported: Boolean = false,
    val isEdgeGestureServiceRunning: Boolean = false
) {
    val coreGrantedCount: Int
        get() = listOf(
            canDrawOverlays,
            isAccessibilityServiceRunning,
            canWriteSettings,
            hasDndPolicyAccess,
            hasNotificationListenerAccess
        ).count { it }

    val coreTotalCount: Int = 5
}

data class DeviceSystemSnapshot(
    val formattedTime: String = "--:--",
    val formattedDate: String = "",
    val wifi: WifiSystemState = WifiSystemState(),
    val bluetooth: BluetoothSystemState = BluetoothSystemState(),
    val mobile: MobileNetworkSystemState = MobileNetworkSystemState(),
    val isAirplaneModeOn: Boolean = false,
    val flashlight: FlashlightSystemState = FlashlightSystemState(),
    val brightness: BrightnessSystemState = BrightnessSystemState(),
    val volume: VolumeSystemState = VolumeSystemState(),
    val isLocationEnabled: Boolean = false,
    val isAutoRotateEnabled: Boolean = false,
    val battery: BatterySystemState = BatterySystemState(),
    val dnd: DoNotDisturbSystemState = DoNotDisturbSystemState(),
    val media: MediaPlaybackState = MediaPlaybackState(),
    val cast: CastSystemState = CastSystemState(),
    val hotspot: HotspotSystemState = HotspotSystemState(),
    val permissions: PermissionHealthSnapshot = PermissionHealthSnapshot()
)

sealed interface SystemActionResult {
    data class Success(val message: String) : SystemActionResult
    data class OpenedSystemPanel(val message: String) : SystemActionResult
    data class PermissionRequired(val permissionName: String, val message: String) : SystemActionResult
    data class UnsupportedHardware(val message: String) : SystemActionResult
    data class Error(val message: String) : SystemActionResult
}
