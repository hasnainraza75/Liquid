package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.data.LuxPreferencesRepository
import com.example.data.LuxThemeMode
import com.example.data.LuxTileId
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LuxPreferencesAndUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `preferences repository persists theme blur geometry and circular tile ordering`() = runTest {
        val tempFile = File.createTempFile("lux_prefs_test_", ".preferences_pb")
        tempFile.deleteOnExit()
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { tempFile }
        val repo = LuxPreferencesRepository(dataStore)

        repo.setThemeMode(LuxThemeMode.LIGHT)
        repo.setBlurRadiusDp(99f) // must clamp to 48f
        repo.setGlassOpacity(0.01f) // must clamp to 0.14f
        repo.setEdgeTriggerGeometry(0.45f, 60)
        repo.setTileEnabled(LuxTileId.FLASHLIGHT, false)
        repo.moveTile(LuxTileId.ROTATION, moveUp = true)

        val prefs = repo.preferencesFlow.first()
        assertEquals(LuxThemeMode.LIGHT, prefs.themeMode)
        assertEquals(48f, prefs.blurRadiusDp, 0.01f)
        assertEquals(0.14f, prefs.glassOpacity, 0.01f)
        assertEquals(0.45f, prefs.edgeTriggerWidthPercent, 0.01f)
        assertEquals(60, prefs.edgeTriggerHeightDp)
        assertFalse(LuxTileId.FLASHLIGHT.key in prefs.enabledTileKeys)
        assertEquals(LuxTileId.ROTATION.key, prefs.orderedTileKeys.first())

        repo.resetTileDefaults()
        val reset = repo.preferencesFlow.first()
        assertTrue(LuxTileId.FLASHLIGHT.key in reset.enabledTileKeys)
        assertEquals(LuxTileId.FLASHLIGHT.key, reset.orderedTileKeys.first())
    }

    @Test
    fun `cuj navigates across Control Center Studio and System Audit screens`() {
        composeTestRule.waitForIdle()

        // 1. Verify primary Liquid Glass Control Center modules
        composeTestRule.onNodeWithTag("header_clock_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connectivity_glass_pod").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_playback_glass_pod").assertIsDisplayed()
        composeTestRule.onNodeWithTag("slider_brightness").assertIsDisplayed()
        composeTestRule.onNodeWithTag("slider_volume").assertIsDisplayed()

        // 2. Toggle theme right from Control Center header
        composeTestRule.onNodeWithTag("header_theme_toggle_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Navigate to Studio tab and verify Liquid Glass customization controls
        composeTestRule.onNodeWithTag("nav_tab_customize").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("customize_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edge_trigger_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_mode_dark").performClick()
        composeTestRule.waitForIdle()

        // 4. Navigate to System Audit tab and verify permissions checklist & itel C681L audit
        composeTestRule.onNodeWithTag("nav_tab_system").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("system_audit_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("permissions_checklist_card").assertIsDisplayed()

        // 5. Return to Control Center
        composeTestRule.onNodeWithTag("nav_tab_control").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("connectivity_glass_pod").assertIsDisplayed()
    }
}
