package com.example.ui.screens

import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.SwipeDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.LuxTileId
import com.example.system.ScreenRecordingState
import com.example.ui.LuxUiState
import com.example.ui.components.AudioMixerModalSheet
import com.example.ui.components.BrightnessAndVolumeSlidersRow
import com.example.ui.components.ConnectivityGlassPod
import com.example.ui.components.LiquidGlassCircleTile
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.LuxStatusHeader
import com.example.ui.components.MediaPlaybackGlassPod
import com.example.ui.components.resolveTilePresentation
import com.example.ui.theme.GlowRecord
import com.example.ui.theme.LocalLiquidGlassTokens
import com.example.ui.theme.StatusGranted
import com.example.ui.theme.StatusWarning

@Composable
fun ControlCenterScreen(
    uiState: LuxUiState,
    isOverlayMode: Boolean,
    contentPadding: PaddingValues,
    onTileClick: (LuxTileId) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onToggleAutoBrightness: () -> Unit,
    onStreamVolumeChange: (Int, Float) -> Unit,
    onOpenAudioMixer: (Boolean) -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onGrantMediaAccess: () -> Unit,
    onToggleTheme: () -> Unit,
    onPrimaryHeaderAction: () -> Unit,
    onNavigateCustomize: () -> Unit,
    onNavigateSystemAudit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlassTokens.current
    val snapshot = uiState.snapshot
    val activeGridTiles = uiState.preferences.activeGridTiles

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("control_center_scroll_container")
        ) {
            // 1. Live Clock, Battery, Carrier, Theme Toggle & Shade Launch
            item(key = "header") {
                LuxStatusHeader(
                    snapshot = snapshot,
                    isOverlayMode = isOverlayMode,
                    onToggleTheme = onToggleTheme,
                    onPrimaryHeaderAction = onPrimaryHeaderAction
                )
            }

            // Active Recording Banner (Real live timer + immediate Stop button)
            item(key = "recording_banner") {
                val rec = uiState.recordingState
                AnimatedVisibility(visible = rec is ScreenRecordingState.Recording) {
                    if (rec is ScreenRecordingState.Recording) {
                        val m = rec.elapsedSeconds / 60
                        val s = rec.elapsedSeconds % 60
                        LiquidGlassPanel(
                            accentGlow = GlowRecord,
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = 12.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTileClick(LuxTileId.SCREEN_RECORD) }
                                .testTag("active_recording_banner")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.FiberManualRecord,
                                        contentDescription = "Recording active",
                                        tint = GlowRecord,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Recording Screen  ${String.format("%02d:%02d", m, s)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = glass.primaryInk
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.StopCircle,
                                        contentDescription = "Stop recording",
                                        tint = GlowRecord,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "STOP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GlowRecord
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Top Bento Pair: 2x2 Connectivity Glass Pod + Live Media Playback Glass Pod
            item(key = "top_bento_pair") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ConnectivityGlassPod(
                        snapshot = snapshot,
                        onWifiClick = { onTileClick(LuxTileId.WIFI) },
                        onBluetoothClick = { onTileClick(LuxTileId.BLUETOOTH) },
                        onMobileClick = { onTileClick(LuxTileId.MOBILE_DATA) },
                        onAirplaneClick = { onTileClick(LuxTileId.AIRPLANE) },
                        modifier = Modifier.weight(1f)
                    )
                    MediaPlaybackGlassPod(
                        media = snapshot.media,
                        onPrevious = onMediaPrevious,
                        onPlayPause = onMediaPlayPause,
                        onNext = onMediaNext,
                        onGrantNotificationAccess = onGrantMediaAccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Mid Bento Row: 2x2 Core Hardware Quick Pod + Dual Liquid Glass Vertical Sliders
            item(key = "mid_bento_sliders") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Left Hero Utility Pod (Torch, Focus DND, Auto-Rotate, Battery Saver)
                    LiquidGlassPanel(
                        contentPadding = 12.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(214.dp)
                            .testTag("hero_utility_pod")
                    ) {
                        val heroTiles = listOf(
                            LuxTileId.FLASHLIGHT,
                            LuxTileId.DND,
                            LuxTileId.ROTATION,
                            LuxTileId.BATTERY_SAVER
                        )
                        Column(
                            verticalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            heroTiles.chunked(2).forEach { pair ->
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    pair.forEach { tileId ->
                                        val p = resolveTilePresentation(
                                            tileId = tileId,
                                            snapshot = snapshot,
                                            recordingState = uiState.recordingState
                                        )
                                        LiquidGlassCircleTile(
                                            icon = p.icon,
                                            label = p.label,
                                            subtitle = p.subtitle,
                                            isActive = p.isActive,
                                            activeColor = p.activeColor,
                                            isUnavailable = p.isUnavailable,
                                            circleSize = 48.dp,
                                            testTag = "hero_tile_${tileId.key}",
                                            onClick = { onTileClick(tileId) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right Dual Dynamic Vertical Sliders (Brightness + Media Volume)
                    BrightnessAndVolumeSlidersRow(
                        brightnessFraction = snapshot.brightness.fraction,
                        brightnessPercent = snapshot.brightness.percent,
                        isAutoBrightness = snapshot.brightness.isAutomatic,
                        volumeState = snapshot.volume,
                        onBrightnessChange = onBrightnessChange,
                        onToggleAutoBrightness = onToggleAutoBrightness,
                        onMediaVolumeChange = { frac ->
                            onStreamVolumeChange(AudioManager.STREAM_MUSIC, frac)
                        },
                        onOpenAudioMixer = { onOpenAudioMixer(true) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. Customizable Circular Liquid Glass Tiles Dock
            item(key = "custom_tiles_dock") {
                LiquidGlassPanel(
                    contentPadding = 14.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_circular_tiles_panel")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "System Controls",
                                style = MaterialTheme.typography.titleSmall,
                                color = glass.primaryInk
                            )
                            if (!isOverlayMode) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(glass.tileInactiveFill)
                                        .clickable(onClick = onNavigateCustomize)
                                        .testTag("customize_tiles_shortcut")
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Customize circular tiles",
                                        tint = glass.primaryInk,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Customize",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = glass.primaryInk
                                    )
                                }
                            }
                        }

                        if (activeGridTiles.isEmpty()) {
                            Text(
                                text = "No circular tiles enabled. Open Studio to add system controls.",
                                style = MaterialTheme.typography.bodySmall,
                                color = glass.secondaryInk,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            activeGridTiles.chunked(4).forEach { rowTiles ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowTiles.forEach { tileId ->
                                        val p = resolveTilePresentation(
                                            tileId = tileId,
                                            snapshot = snapshot,
                                            recordingState = uiState.recordingState
                                        )
                                        LiquidGlassCircleTile(
                                            icon = p.icon,
                                            label = p.label,
                                            subtitle = p.subtitle,
                                            isActive = p.isActive,
                                            activeColor = p.activeColor,
                                            isUnavailable = p.isUnavailable,
                                            circleSize = 54.dp,
                                            testTag = "grid_tile_${tileId.key}",
                                            onClick = { onTileClick(tileId) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(4 - rowTiles.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Honest Real-System Integration & Top-Right Gesture Readiness Strip
            if (!isOverlayMode) {
                item(key = "system_readiness_strip") {
                    val perms = snapshot.permissions
                    val gestureActive = uiState.preferences.edgeTriggerEnabled &&
                        (perms.isEdgeGestureServiceRunning || perms.isAccessibilityServiceRunning)
                    LiquidGlassPanel(
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = 14.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateSystemAudit)
                            .testTag("system_readiness_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            (if (gestureActive) StatusGranted else StatusWarning)
                                                .copy(alpha = 0.2f)
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (gestureActive) {
                                            Icons.Rounded.SwipeDown
                                        } else {
                                            Icons.Rounded.Security
                                        },
                                        contentDescription = "System readiness status",
                                        tint = if (gestureActive) StatusGranted else StatusWarning,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (gestureActive) {
                                            "Top-Right Swipe Active"
                                        } else {
                                            "System Access: ${perms.coreGrantedCount}/${perms.coreTotalCount} Ready"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = glass.primaryInk
                                    )
                                    Text(
                                        text = "Blur: ${if (perms.isCrossWindowBlurSupported) "HW Supported" else "GPU Fallback"}  •  Tap for itel C681L Audit",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = glass.secondaryInk,
                                        maxLines = 1
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Open System Audit",
                                tint = if (perms.coreGrantedCount == perms.coreTotalCount) {
                                    StatusGranted
                                } else {
                                    glass.mutedInk
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.isAudioMixerOpen) {
        AudioMixerModalSheet(
            volumeState = snapshot.volume,
            onStreamChange = onStreamVolumeChange,
            onDismiss = { onOpenAudioMixer(false) }
        )
    }
}
