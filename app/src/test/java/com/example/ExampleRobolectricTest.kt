package com.example

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.LuxControlAccessibilityService
import com.example.service.LuxEdgeGestureService
import com.example.service.LuxMediaNotificationListener
import com.example.service.LuxOverlayShadeActivity
import com.example.service.ScreenRecordingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app label matches Lux Control`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Lux Control", appName)
    }

    @Test
    fun `manifest declares all system services and translucent shade activity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val pkg = context.packageName

        assertNotNull(pm.getActivityInfo(ComponentName(pkg, LuxOverlayShadeActivity::class.java.name), 0))
        assertNotNull(pm.getServiceInfo(ComponentName(pkg, LuxEdgeGestureService::class.java.name), 0))
        assertNotNull(pm.getServiceInfo(ComponentName(pkg, ScreenRecordingService::class.java.name), 0))
        assertNotNull(pm.getServiceInfo(ComponentName(pkg, LuxControlAccessibilityService::class.java.name), 0))
        assertNotNull(pm.getServiceInfo(ComponentName(pkg, LuxMediaNotificationListener::class.java.name), 0))
    }
}
