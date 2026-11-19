package com.example.service

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.LuxControlViewModel
import com.example.ui.LuxUiEvent
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.screens.ControlCenterScreen
import com.example.ui.theme.LocalLiquidGlassTokens
import com.example.ui.theme.LuxControlTheme
import kotlinx.coroutines.flow.collectLatest

/**
 * Translucent Blur-Behind Shade Activity launched instantaneously when the user
 * swipes down from the top-right corner gesture zone. Uses real Android 12-15
 * WindowManager.LayoutParams.FLAG_BLUR_BEHIND and Window.setBackgroundBlurRadius()
 * to blur the live running app behind the Liquid Glass panels.
 */
class LuxOverlayShadeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyRealCrossWindowBlur(radiusPx = 64)

        setContent {
            val viewModel: LuxControlViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(uiState.preferences.blurRadiusDp) {
                val px = (uiState.preferences.blurRadiusDp * resources.displayMetrics.density * 1.4f)
                    .toInt()
                    .coerceIn(0, 120)
                applyRealCrossWindowBlur(px)
            }

            val mediaProjectionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    viewModel.onScreenCaptureConsentResult(result.resultCode, result.data)
                    finishWithoutAnimation()
                } else {
                    viewModel.onScreenCaptureConsentResult(Activity.RESULT_CANCELED, null)
                }
            }

            LaunchedEffect(viewModel) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        is LuxUiEvent.ShowFeedback -> {
                            snackbarHostState.showSnackbar(
                                message = event.message,
                                duration = SnackbarDuration.Short
                            )
                        }
                        LuxUiEvent.RequestScreenCaptureConsent -> {
                            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                            if (mpm != null) {
                                runCatching {
                                    mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                                }
                            }
                        }
                        LuxUiEvent.RequestRuntimePermissions -> Unit
                    }
                }
            }

            LuxControlTheme(
                themeMode = uiState.preferences.themeMode,
                blurRadiusDp = uiState.preferences.blurRadiusDp,
                glassOpacity = uiState.preferences.glassOpacity
            ) {
                var cumulativeSwipeUp by remember { mutableFloatStateOf(0f) }

                LiquidGlassBackdrop(
                    transparentWindow = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (cumulativeSwipeUp < -120f) {
                                        finishWithoutAnimation()
                                    }
                                    cumulativeSwipeUp = 0f
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount < 0) cumulativeSwipeUp += dragAmount
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        ControlCenterScreen(
                            uiState = uiState,
                            isOverlayMode = true,
                            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                            onTileClick = viewModel::onTileClicked,
                            onBrightnessChange = viewModel::onBrightnessChanged,
                            onToggleAutoBrightness = viewModel::onToggleAutoBrightness,
                            onStreamVolumeChange = viewModel::onVolumeStreamChanged,
                            onOpenAudioMixer = viewModel::setAudioMixerOpen,
                            onMediaPlayPause = viewModel::onMediaPlayPause,
                            onMediaNext = viewModel::onMediaNext,
                            onMediaPrevious = viewModel::onMediaPrevious,
                            onGrantMediaAccess = viewModel::openNotificationListenerSettings,
                            onToggleTheme = viewModel::cycleThemeQuickly,
                            onPrimaryHeaderAction = { finishWithoutAnimation() },
                            onNavigateCustomize = { finishWithoutAnimation() },
                            onNavigateSystemAudit = { finishWithoutAnimation() }
                        )

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        ) { data ->
                            val glass = LocalLiquidGlassTokens.current
                            Snackbar(
                                snackbarData = data,
                                containerColor = if (glass.isDark) Color(0xFF10203A) else Color(0xFFF6FBFF),
                                contentColor = glass.primaryInk
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyRealCrossWindowBlur(radiusPx: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs = window.attributes
                attrs.blurBehindRadius = radiusPx
                window.attributes = attrs
                window.setBackgroundBlurRadius(radiusPx)
            }
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
