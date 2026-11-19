package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LuxPreferencesRepository
import com.example.data.LuxThemeMode
import com.example.data.LuxTileId
import com.example.data.LuxUserPreferences
import com.example.service.LuxEdgeGestureService
import com.example.service.LuxEdgeGestureView
import com.example.service.ScreenRecordingService
import com.example.system.DeviceArchitectureAuditor
import com.example.system.DeviceSystemSnapshot
import com.example.system.RuntimeDeviceProbe
import com.example.system.ScreenRecordingState
import com.example.system.SystemActionResult
import com.example.system.SystemControlExecutor
import com.example.system.SystemControlMonitor
import com.example.system.TargetArchitectureReport
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LuxUiState(
    val snapshot: DeviceSystemSnapshot = DeviceSystemSnapshot(),
    val preferences: LuxUserPreferences = LuxUserPreferences(),
    val recordingState: ScreenRecordingState = ScreenRecordingState.Idle,
    val deviceProbe: RuntimeDeviceProbe? = null,
    val architectureReport: TargetArchitectureReport? = null,
    val isAudioMixerOpen: Boolean = false
)

sealed interface LuxUiEvent {
    data class ShowFeedback(val message: String, val isError: Boolean = false) : LuxUiEvent
    data object RequestScreenCaptureConsent : LuxUiEvent
    data object RequestRuntimePermissions : LuxUiEvent
}

class LuxControlViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val monitor = SystemControlMonitor(appContext)
    private val executor = SystemControlExecutor(appContext)
    private val prefsRepo = LuxPreferencesRepository(appContext)

    private val deviceProbe = DeviceArchitectureAuditor.probeCurrentDevice(appContext)
    private val architectureReport = DeviceArchitectureAuditor.buildItelC681LReport(appContext.packageName)

    private val _audioMixerOpen = MutableStateFlow(false)
    private val _events = MutableSharedFlow<LuxUiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<LuxUiEvent> = _events.asSharedFlow()

    val uiState: StateFlow<LuxUiState> = combine(
        monitor.snapshot,
        prefsRepo.preferencesFlow,
        ScreenRecordingService.state,
        _audioMixerOpen
    ) { snapshot, prefs, recState, mixerOpen ->
        LuxUiState(
            snapshot = snapshot,
            preferences = prefs,
            recordingState = recState,
            deviceProbe = deviceProbe,
            architectureReport = architectureReport,
            isAudioMixerOpen = mixerOpen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LuxUiState(
            deviceProbe = deviceProbe,
            architectureReport = architectureReport
        )
    )

    init {
        monitor.start(viewModelScope)
    }

    fun refreshSystemState() {
        monitor.refreshAll()
    }

    override fun onCleared() {
        monitor.stop()
        super.onCleared()
    }

    fun onTileClicked(tileId: LuxTileId) {
        val snap = uiState.value.snapshot
        when (tileId) {
            LuxTileId.WIFI -> emitResult(executor.openWifiControl())
            LuxTileId.BLUETOOTH -> emitResult(
                executor.openBluetoothControl(
                    isCurrentlyEnabled = snap.bluetooth.isEnabled,
                    hasConnectPermission = snap.bluetooth.hasPermission
                )
            )
            LuxTileId.MOBILE_DATA -> emitResult(executor.openMobileNetworkControl())
            LuxTileId.AIRPLANE -> emitResult(executor.toggleOrOpenAirplaneMode(snap.isAirplaneModeOn))
            LuxTileId.FLASHLIGHT -> emitResult(executor.toggleFlashlight(snap.flashlight))
            LuxTileId.ROTATION -> {
                val res = executor.toggleAutoRotate(snap.isAutoRotateEnabled)
                emitResult(res)
                if (res is SystemActionResult.PermissionRequired) {
                    executor.openWriteSettingsPermission()
                }
            }
            LuxTileId.DND -> {
                val res = executor.toggleDoNotDisturb(snap.dnd)
                emitResult(res)
                if (res is SystemActionResult.PermissionRequired) {
                    executor.openDndPolicyPermission()
                }
            }
            LuxTileId.SCREEN_RECORD -> handleScreenRecordToggle()
            LuxTileId.SCREENSHOT -> {
                val res = executor.takeNativeScreenshot()
                emitResult(res)
                if (res is SystemActionResult.PermissionRequired) {
                    executor.openAccessibilitySettings()
                }
            }
            LuxTileId.BATTERY_SAVER -> emitResult(executor.openBatterySaverSettings())
            LuxTileId.LOCATION -> emitResult(executor.openLocationSettings())
            LuxTileId.HOTSPOT -> emitResult(executor.openHotspotSettings())
            LuxTileId.CAST -> emitResult(executor.openCastSettings())
        }
        monitor.refreshAll()
    }

    private fun handleScreenRecordToggle() {
        val current = ScreenRecordingService.state.value
        if (current is ScreenRecordingState.Recording) {
            ScreenRecordingService.stop(appContext)
            emitFeedback("Stopping screen recording and finalizing MP4…")
        } else {
            ScreenRecordingService.clearTransientResult()
            viewModelScope.launch {
                _events.emit(LuxUiEvent.RequestScreenCaptureConsent)
            }
        }
    }

    fun onScreenCaptureConsentResult(resultCode: Int, data: Intent?) {
        if (data == null) {
            emitFeedback("Screen recording cancelled by user.", isError = false)
            return
        }
        val withMic = uiState.value.preferences.recordWithMicrophone &&
            uiState.value.snapshot.permissions.hasRecordAudioPermission
        ScreenRecordingService.start(appContext, resultCode, data, withMic)
        emitFeedback("Screen recording started (saving to Movies/LuxRecordings).")
    }

    fun onBrightnessChanged(fraction: Float) {
        val res = executor.setSystemBrightness(fraction)
        if (res is SystemActionResult.PermissionRequired) {
            emitResult(res)
            executor.openWriteSettingsPermission()
        } else if (res is SystemActionResult.Error) {
            emitResult(res)
        }
        monitor.refreshAll()
    }

    fun onToggleAutoBrightness() {
        val res = executor.toggleAdaptiveBrightness(uiState.value.snapshot.brightness.isAutomatic)
        emitResult(res)
        if (res is SystemActionResult.PermissionRequired) {
            executor.openWriteSettingsPermission()
        }
        monitor.refreshAll()
    }

    fun onVolumeStreamChanged(streamType: Int, fraction: Float) {
        val res = executor.setStreamVolumeFraction(streamType, fraction)
        if (res is SystemActionResult.Error || res is SystemActionResult.PermissionRequired) {
            emitResult(res)
        }
        monitor.refreshAll()
    }

    fun setAudioMixerOpen(open: Boolean) {
        _audioMixerOpen.value = open
    }

    fun onMediaPlayPause() = emitResult(executor.mediaPlayPause())
    fun onMediaNext() = emitResult(executor.mediaNext())
    fun onMediaPrevious() = emitResult(executor.mediaPrevious())

    fun cycleThemeQuickly() {
        val current = uiState.value.preferences.themeMode
        val next = if (current == LuxThemeMode.DARK) LuxThemeMode.LIGHT else LuxThemeMode.DARK
        viewModelScope.launch { prefsRepo.setThemeMode(next) }
    }

    fun setThemeMode(mode: LuxThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setBlurRadiusDp(radius: Float) {
        viewModelScope.launch { prefsRepo.setBlurRadiusDp(radius) }
    }

    fun setGlassOpacity(opacity: Float) {
        viewModelScope.launch { prefsRepo.setGlassOpacity(opacity) }
    }

    fun setEdgeTriggerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val perms = monitor.readPermissionHealth()
                if (!perms.canDrawOverlays && !perms.isAccessibilityServiceRunning) {
                    emitFeedback(
                        "Grant 'Display over other apps' or enable Accessibility Service first for the top-right swipe trigger.",
                        isError = true
                    )
                    executor.openOverlayPermissionSettings()
                    return@launch
                }
                prefsRepo.setEdgeTriggerEnabled(true)
                if (perms.canDrawOverlays && !perms.isAccessibilityServiceRunning) {
                    val started = LuxEdgeGestureService.start(appContext)
                    emitFeedback(
                        if (started) "Top-right corner swipe trigger active!"
                        else "Unable to start foreground edge service; verify notification & overlay permissions.",
                        isError = !started
                    )
                } else {
                    emitFeedback("Top-right swipe trigger active via Accessibility Overlay!")
                }
            } else {
                prefsRepo.setEdgeTriggerEnabled(false)
                LuxEdgeGestureService.stop(appContext)
                emitFeedback("Top-right swipe trigger paused.")
            }
            monitor.refreshAll()
        }
    }

    fun setEdgeGeometry(widthPercent: Float, heightDp: Int) {
        viewModelScope.launch { prefsRepo.setEdgeTriggerGeometry(widthPercent, heightDp) }
    }

    fun setShowEdgeIndicatorHint(show: Boolean) {
        viewModelScope.launch { prefsRepo.setShowEdgeIndicatorHint(show) }
    }

    fun setRecordWithMicrophone(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setRecordWithMicrophone(enabled) }
    }

    fun setTileEnabled(tileId: LuxTileId, enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setTileEnabled(tileId, enabled) }
    }

    fun moveTile(tileId: LuxTileId, moveUp: Boolean) {
        viewModelScope.launch { prefsRepo.moveTile(tileId, moveUp) }
    }

    fun resetTileDefaults() {
        viewModelScope.launch {
            prefsRepo.resetTileDefaults()
            emitFeedback("Restored default Liquid Glass tile layout.")
        }
    }

    fun launchLiveOverlayShade() {
        LuxEdgeGestureView.launchShadeActivity(appContext)
    }

    fun openOverlayPermission() = emitResult(executor.openOverlayPermissionSettings())
    fun openAccessibilitySettings() = emitResult(executor.openAccessibilitySettings())
    fun openWriteSettingsPermission() = emitResult(executor.openWriteSettingsPermission())
    fun openDndPolicyPermission() = emitResult(executor.openDndPolicyPermission())
    fun openNotificationListenerSettings() = emitResult(executor.openNotificationListenerSettings())

    fun requestRuntimePermissions() {
        viewModelScope.launch { _events.emit(LuxUiEvent.RequestRuntimePermissions) }
    }

    private fun emitResult(result: SystemActionResult) {
        when (result) {
            is SystemActionResult.Success -> emitFeedback(result.message, isError = false)
            is SystemActionResult.OpenedSystemPanel -> emitFeedback(result.message, isError = false)
            is SystemActionResult.PermissionRequired -> emitFeedback(result.message, isError = true)
            is SystemActionResult.UnsupportedHardware -> emitFeedback(result.message, isError = true)
            is SystemActionResult.Error -> emitFeedback(result.message, isError = true)
        }
    }

    private fun emitFeedback(message: String, isError: Boolean = false) {
        viewModelScope.launch {
            _events.emit(LuxUiEvent.ShowFeedback(message, isError))
        }
    }
}
