package com.example

import android.content.Context
import android.media.AudioManager
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.example.system.BrightnessSystemState
import com.example.system.DeviceArchitectureAuditor
import com.example.system.DoNotDisturbSystemState
import com.example.system.FlashlightSystemState
import com.example.system.SystemActionResult
import com.example.system.SystemControlExecutor
import com.example.system.SystemControlMonitor
import com.example.system.VolumeStreamInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemControlEngineTest {

    @Test
    fun `network generation mapper maps 5G LTE 3G and 2G accurately`() {
        assertEquals("5G", SystemControlMonitor.mapNetworkGeneration(TelephonyManager.NETWORK_TYPE_NR))
        assertEquals("4G LTE", SystemControlMonitor.mapNetworkGeneration(TelephonyManager.NETWORK_TYPE_LTE))
        assertEquals("3G", SystemControlMonitor.mapNetworkGeneration(TelephonyManager.NETWORK_TYPE_HSPAP))
        assertEquals("2G", SystemControlMonitor.mapNetworkGeneration(TelephonyManager.NETWORK_TYPE_EDGE))
        assertEquals("Cellular", SystemControlMonitor.mapNetworkGeneration(TelephonyManager.NETWORK_TYPE_UNKNOWN))
    }

    @Test
    fun `brightness and volume fraction math clamps cleanly`() {
        val maxBright = BrightnessSystemState(level = 255)
        assertEquals(100, maxBright.percent)
        val zeroBright = BrightnessSystemState(level = 0)
        assertEquals(0, zeroBright.percent)
        val clampedBright = BrightnessSystemState(level = 999)
        assertEquals(100, clampedBright.percent)

        val vol = VolumeStreamInfo(AudioManager.STREAM_MUSIC, "Media", current = 8, max = 16)
        assertEquals(50, vol.percent)
        val zeroMax = VolumeStreamInfo(AudioManager.STREAM_MUSIC, "Media", current = 0, max = 0)
        assertEquals(0, zeroMax.percent)
    }

    @Test
    fun `monitor lifecycle start refresh and stop execute cleanly without leaking`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = SystemControlMonitor(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            monitor.start(scope)
            monitor.refreshAll()
            val snapshot = monitor.snapshot.value
            assertTrue(snapshot.formattedTime.contains(":"))
            assertTrue(snapshot.volume.media.max >= 1)
            assertEquals("Tether Settings", snapshot.hotspot.statusLabel)
        } finally {
            monitor.stop()
            scope.cancel()
        }
    }

    @Test
    fun `executor handles missing permissions granted writes and unsupported hardware explicitly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = SystemControlExecutor(context)

        // 1. Hardware absent -> UnsupportedHardware (never fake success)
        val torchNoHw = executor.toggleFlashlight(
            FlashlightSystemState(isSupported = false, isEnabled = false, cameraId = null)
        )
        assertTrue(torchNoHw is SystemActionResult.UnsupportedHardware)

        // 2. WRITE_SETTINGS denied -> PermissionRequired
        val deniedExecutor = SystemControlExecutor(context, canWriteSettingsCheck = { false })
        assertTrue(deniedExecutor.setSystemBrightness(0.6f) is SystemActionResult.PermissionRequired)
        assertTrue(deniedExecutor.toggleAutoRotate(currentlyEnabled = false) is SystemActionResult.PermissionRequired)

        // 3. WRITE_SETTINGS granted -> real ContentResolver writes for brightness & auto-rotate
        val grantedExecutor = SystemControlExecutor(context, canWriteSettingsCheck = { true })
        assertTrue(grantedExecutor.setSystemBrightness(0.8f) is SystemActionResult.Success)
        val writtenBrightness = android.provider.Settings.System.getInt(
            context.contentResolver,
            android.provider.Settings.System.SCREEN_BRIGHTNESS,
            -1
        )
        assertEquals(204, writtenBrightness)

        assertTrue(grantedExecutor.toggleAutoRotate(currentlyEnabled = false) is SystemActionResult.Success)
        val writtenRotate = android.provider.Settings.System.getInt(
            context.contentResolver,
            android.provider.Settings.System.ACCELEROMETER_ROTATION,
            0
        )
        assertEquals(1, writtenRotate)

        // 4. DND policy access denied -> PermissionRequired
        val dndNeedsPerm = executor.toggleDoNotDisturb(
            DoNotDisturbSystemState(isEnabled = false, hasPolicyAccess = false)
        )
        assertTrue(dndNeedsPerm is SystemActionResult.PermissionRequired)

        // 5. Screenshot without AccessibilityService -> PermissionRequired
        val screenshotNeedsA11y = executor.takeNativeScreenshot()
        assertTrue(screenshotNeedsA11y is SystemActionResult.PermissionRequired)

        // 6. Real AudioManager stream update & media key dispatch
        val volumeResult = executor.setStreamVolumeFraction(AudioManager.STREAM_MUSIC, 0.5f)
        assertTrue(volumeResult is SystemActionResult.Success)

        val mediaResult = executor.mediaPlayPause()
        assertTrue(mediaResult is SystemActionResult.Success)
    }

    @Test
    fun `itel C681L architecture audit covers all 16 controls and honest OEM constraints`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val probe = DeviceArchitectureAuditor.probeCurrentDevice(context)
        assertEquals(36, probe.sdkInt)
        assertFalse(probe.isRunningAsSystemApp)

        val report = DeviceArchitectureAuditor.buildItelC681LReport("com.aistudio.luxcontrol.vqxkpm")
        assertTrue(report.targetDeviceName.contains("C681L"))
        assertTrue(report.chipsetInfo.contains("Unisoc T7250"))
        assertTrue(report.systemUiLimitationSummary.contains("com.android.systemui"))
        assertEquals(16, report.controlMatrix.size)
        assertTrue(report.adbSecureGrantCommand.contains("WRITE_SECURE_SETTINGS"))
    }
}
