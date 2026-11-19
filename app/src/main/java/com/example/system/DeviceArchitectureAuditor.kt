package com.example.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager

data class RuntimeDeviceProbe(
    val manufacturer: String,
    val model: String,
    val deviceCode: String,
    val hardware: String,
    val androidRelease: String,
    val sdkInt: Int,
    val buildFingerprint: String,
    val hasFlashHardware: Boolean,
    val hasTelephonyHardware: Boolean,
    val hasBluetoothHardware: Boolean,
    val supportsCrossWindowBlur: Boolean,
    val isRunningAsSystemApp: Boolean,
    val isExactItelC681L: Boolean
)

data class ControlMatrixRow(
    val controlName: String,
    val stateSource: String,
    val unprivilegedAction: String,
    val systemUiRequirement: String
)

data class TargetArchitectureReport(
    val targetDeviceName: String,
    val chipsetInfo: String,
    val oemFirmwareInfo: String,
    val bootloaderAssessment: String,
    val systemUiLimitationSummary: String,
    val recommendedDeploymentModes: List<String>,
    val adbSecureGrantCommand: String,
    val controlMatrix: List<ControlMatrixRow>
)

object DeviceArchitectureAuditor {

    fun probeCurrentDevice(context: Context): RuntimeDeviceProbe {
        val pm = context.packageManager
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val blurSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { wm?.isCrossWindowBlurEnabled == true }.getOrDefault(false)
        } else {
            false
        }
        val appFlags = runCatching {
            context.applicationInfo.flags
        }.getOrDefault(0)
        val isSystemApp = (appFlags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

        val modelUpper = Build.MODEL.uppercase()
        val deviceUpper = Build.DEVICE.uppercase()
        val isItelC681L = modelUpper.contains("C681L") || deviceUpper.contains("C681L")

        return RuntimeDeviceProbe(
            manufacturer = Build.MANUFACTURER.ifBlank { "Unknown" },
            model = Build.MODEL.ifBlank { "Unknown" },
            deviceCode = Build.DEVICE.ifBlank { "Unknown" },
            hardware = Build.HARDWARE.ifBlank { "Unknown" },
            androidRelease = Build.VERSION.RELEASE.ifBlank { "Unknown" },
            sdkInt = Build.VERSION.SDK_INT,
            buildFingerprint = Build.FINGERPRINT.ifBlank { "Unknown" },
            hasFlashHardware = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
            hasTelephonyHardware = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
            hasBluetoothHardware = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
            supportsCrossWindowBlur = blurSupported,
            isRunningAsSystemApp = isSystemApp,
            isExactItelC681L = isItelC681L
        )
    }

    fun buildItelC681LReport(packageName: String): TargetArchitectureReport {
        return TargetArchitectureReport(
            targetDeviceName = "itel City 200 (Model C681L)",
            chipsetInfo = "Unisoc T7250 / Spreadtrum UMS9230E (2x Cortex-A75 + 6x Cortex-A55, Mali-G57 MP1 GPU, 720x1576 120Hz)",
            oemFirmwareInfo = "itel OS 15 (Transsion Android 15 fork; signed .pac factory firmware e.g. C681L-15.1.2.x)",
            bootloaderAssessment = "Locked by default with Android Verified Boot (AVB / vbmeta) and Unisoc factory-token fastboot unlock restrictions. No official OEM unlock program or official AOSP/LineageOS tree exists for C681L.",
            systemUiLimitationSummary = "On stock Android 15 (itel OS 15), com.android.systemui resides in /system_ext/priv-app/SystemUI and is signed with Transsion's private OEM platform key. An ordinary third-party APK CANNOT uninstall, overwrite, or intercept TYPE_STATUS_BAR inside SystemUI. Full shade replacement requires either OEM platform-key signing or an unlocked bootloader running an AOSP 15 / GSI build with our systemui-integration/ patch. On stock locked C681L devices, Lux Control operates honestly as a real Android 15 System Control Companion using Accessibility/Overlay top-right edge swipe detection + real FLAG_BLUR_BEHIND window blur.",
            recommendedDeploymentModes = listOf(
                "Mode A (Stock Locked itel C681L — Working Today): Install signed APK, grant Overlay + Accessibility + Write Settings + DND + Notification Access, and swipe down from the calibrated top-right corner zone to launch Lux Control Shade with real hardware blur.",
                "Mode B (Stock itel C681L + USB ADB Elevation — No Root): Run the ADB WRITE_SECURE_SETTINGS grant command below to unlock direct Airplane Mode & Battery Saver writes where allowed by OEM framework.",
                "Mode C (Unlocked Bootloader / AOSP 15 Custom Build): Integrate systemui-integration/aosp15/ (Android.bp, privapp-permissions, LuxSplitShadeInterceptPatch.kt) into AOSP frameworks/base/packages/SystemUI to replace the native top-right Quick Settings shade at TYPE_NOTIFICATION_SHADE."
            ),
            adbSecureGrantCommand = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
            controlMatrix = listOf(
                ControlMatrixRow("1. Wi-Fi", "ConnectivityManager + WifiManager", "Live state + Android Internet Quick Panel", "Direct toggle needs NETWORK_SETTINGS (privileged)"),
                ControlMatrixRow("2. Bluetooth", "BluetoothManager (BLUETOOTH_CONNECT)", "Live state + Request Enable / BT Settings", "Direct silent toggle deprecated in API 33+"),
                ControlMatrixRow("3. Mobile Network", "TelephonyManager + TelephonyCallback", "Live carrier, 4G/5G gen, bars + Internet Panel", "Direct data toggle needs MODIFY_PHONE_STATE"),
                ControlMatrixRow("4. Airplane Mode", "Settings.Global.AIRPLANE_MODE_ON", "Direct with ADB WRITE_SECURE_SETTINGS, else Settings", "Radio broadcast requires platform signature"),
                ControlMatrixRow("5. Flashlight", "CameraManager.TorchCallback", "Direct real torch toggle (setTorchMode)", "Works directly in standard APK"),
                ControlMatrixRow("6. Brightness", "Settings.System.SCREEN_BRIGHTNESS", "Direct real slider & Auto toggle (WRITE_SETTINGS)", "Works directly once Write Settings granted"),
                ControlMatrixRow("7. Volume", "AudioManager stream APIs + Receiver", "Direct real Media/Ring/Alarm/Notify sliders", "Works directly in standard APK"),
                ControlMatrixRow("8. Location", "LocationManager.isLocationEnabled", "Live GPS state + Location Settings", "Direct toggle requires WRITE_SECURE_SETTINGS"),
                ControlMatrixRow("9. Screen Rotation", "Settings.System.ACCELEROMETER_ROTATION", "Direct real toggle (WRITE_SETTINGS)", "Works directly once Write Settings granted"),
                ControlMatrixRow("10. Battery Saver", "PowerManager.isPowerSaveMode + Battery", "Live % & mode + Battery Saver Settings", "Direct toggle requires DEVICE_POWER / privileged"),
                ControlMatrixRow("11. Do Not Disturb", "NotificationManager interruptionFilter", "Direct real toggle (Policy Access)", "Works directly once Policy Access granted"),
                ControlMatrixRow("12. Screen Record", "MediaProjection + FGS(mediaProjection)", "Direct real .mp4 recording to MediaStore", "Works directly with system consent dialog"),
                ControlMatrixRow("13. Screenshot", "AccessibilityService (API 28+)", "Direct real OS screenshot (GLOBAL_ACTION)", "Works directly once Accessibility enabled"),
                ControlMatrixRow("14. Media Playback", "MediaSessionManager + NotificationListener", "Direct live metadata, art & TransportControls", "Works directly once Notification Access granted"),
                ControlMatrixRow("15. Cast", "DisplayManager + MediaRouter", "Live presentation display + Cast Settings", "Direct Miracast pairing needs CONFIGURE_WIFI_DISPLAY"),
                ControlMatrixRow("16. Hotspot", "Tethering probe + ConnectivityManager", "Opens native Hotspot & Tethering Settings", "Direct AP toggle needs TETHER_PRIVILEGED")
            )
        )
    }
}
