package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoNotDisturbOff
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.SignalCellular4Bar
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.LuxTileId
import com.example.system.DeviceSystemSnapshot
import com.example.system.MediaPlaybackState
import com.example.system.ScreenRecordingState
import com.example.ui.theme.GlowAirplane
import com.example.ui.theme.GlowBatterySaver
import com.example.ui.theme.GlowBluetooth
import com.example.ui.theme.GlowCast
import com.example.ui.theme.GlowCellular
import com.example.ui.theme.GlowDnd
import com.example.ui.theme.GlowFlashlight
import com.example.ui.theme.GlowHotspot
import com.example.ui.theme.GlowLocation
import com.example.ui.theme.GlowMedia
import com.example.ui.theme.GlowRecord
import com.example.ui.theme.GlowRotation
import com.example.ui.theme.GlowScreenshot
import com.example.ui.theme.GlowWifi
import com.example.ui.theme.LocalLiquidGlassTokens
import com.example.ui.theme.StatusGranted

@Composable
fun LuxStatusHeader(
    snapshot: DeviceSystemSnapshot,
    isOverlayMode: Boolean,
    onToggleTheme: () -> Unit,
    onPrimaryHeaderAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlassTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = snapshot.formattedTime,
                    style = MaterialTheme.typography.headlineLarge,
                    color = glass.primaryInk,
                    modifier = Modifier.testTag("header_clock_text")
                )
                Spacer(modifier = Modifier.width(10.dp))
                // Real Battery Capsule
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(glass.tileInactiveFill)
                        .border(0.9.dp, glass.specularBorderTop.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("header_battery_badge")
                ) {
                    Icon(
                        imageVector = if (snapshot.battery.isCharging) {
                            Icons.Rounded.BatteryChargingFull
                        } else {
                            Icons.Rounded.BatteryStd
                        },
                        contentDescription = "Battery ${snapshot.battery.percent}%",
                        tint = when {
                            snapshot.battery.isCharging -> StatusGranted
                            snapshot.battery.isPowerSaveMode -> GlowBatterySaver
                            else -> glass.primaryInk
                        },
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${snapshot.battery.percent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.primaryInk
                    )
                }
            }
            Text(
                text = "${snapshot.formattedDate}  •  ${snapshot.mobile.carrierName}",
                style = MaterialTheme.typography.bodySmall,
                color = glass.secondaryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(glass.tileInactiveFill)
                    .border(1.dp, glass.specularBorderTop.copy(alpha = 0.55f), CircleShape)
                    .testTag("header_theme_toggle_button")
            ) {
                Icon(
                    imageVector = if (glass.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = "Switch Liquid Glass theme",
                    tint = glass.primaryInk,
                    modifier = Modifier.size(20.dp)
                )
            }

            LiquidGlassPillButton(
                text = if (isOverlayMode) "Close" else "Shade",
                icon = if (isOverlayMode) Icons.Rounded.Close else Icons.Rounded.Layers,
                testTag = if (isOverlayMode) "overlay_close_button" else "launch_overlay_shade_button",
                highlighted = !isOverlayMode && snapshot.permissions.canDrawOverlays,
                onClick = onPrimaryHeaderAction
            )
        }
    }
}

@Composable
fun ConnectivityGlassPod(
    snapshot: DeviceSystemSnapshot,
    onWifiClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onMobileClick: () -> Unit,
    onAirplaneClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassPanel(
        accentGlow = when {
            snapshot.isAirplaneModeOn -> GlowAirplane
            snapshot.wifi.isConnected -> GlowWifi
            snapshot.mobile.isCellularActive -> GlowCellular
            else -> null
        },
        contentPadding = 12.dp,
        modifier = modifier
            .height(196.dp)
            .testTag("connectivity_glass_pod")
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                LiquidGlassCircleTile(
                    icon = if (snapshot.wifi.isEnabled) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                    label = "Wi-Fi",
                    subtitle = snapshot.wifi.networkLabel,
                    isActive = snapshot.wifi.isEnabled,
                    activeColor = GlowWifi,
                    circleSize = 48.dp,
                    testTag = "tile_wifi",
                    onClick = onWifiClick,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassCircleTile(
                    icon = if (snapshot.bluetooth.isEnabled) {
                        Icons.Rounded.Bluetooth
                    } else {
                        Icons.Rounded.BluetoothDisabled
                    },
                    label = "BT",
                    subtitle = snapshot.bluetooth.statusLabel,
                    isActive = snapshot.bluetooth.isEnabled,
                    activeColor = GlowBluetooth,
                    circleSize = 48.dp,
                    isUnavailable = !snapshot.bluetooth.isSupported,
                    testTag = "tile_bluetooth",
                    onClick = onBluetoothClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                LiquidGlassCircleTile(
                    icon = if (snapshot.mobile.isCellularActive || snapshot.mobile.isMobileDataEnabled) {
                        Icons.Rounded.SignalCellular4Bar
                    } else {
                        Icons.Rounded.SignalCellularOff
                    },
                    label = "Mobile",
                    subtitle = if (snapshot.mobile.isSupported) {
                        snapshot.mobile.networkGeneration
                    } else {
                        "No SIM"
                    },
                    isActive = snapshot.mobile.isCellularActive || snapshot.mobile.isMobileDataEnabled,
                    activeColor = GlowCellular,
                    circleSize = 48.dp,
                    isUnavailable = !snapshot.mobile.isSupported,
                    testTag = "tile_mobile_data",
                    onClick = onMobileClick,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassCircleTile(
                    icon = Icons.Rounded.AirplanemodeActive,
                    label = "Plane",
                    subtitle = if (snapshot.isAirplaneModeOn) "On" else "Off",
                    isActive = snapshot.isAirplaneModeOn,
                    activeColor = GlowAirplane,
                    circleSize = 48.dp,
                    testTag = "tile_airplane",
                    onClick = onAirplaneClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MediaPlaybackGlassPod(
    media: MediaPlaybackState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlassTokens.current
    val albumBitmap = remember(media.albumArt) { media.albumArt?.asImageBitmap() }

    LiquidGlassPanel(
        accentGlow = if (media.isPlaying) GlowMedia else null,
        contentPadding = 14.dp,
        modifier = modifier
            .height(196.dp)
            .testTag("media_playback_glass_pod")
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    GlowMedia.copy(alpha = 0.45f),
                                    glass.tileInactiveFill
                                )
                            )
                        )
                        .border(1.dp, glass.specularBorderTop.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                ) {
                    if (albumBitmap != null) {
                        Image(
                            bitmap = albumBitmap,
                            contentDescription = "Active album cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = "Media artwork placeholder",
                            tint = glass.primaryInk,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (media.isPlaying) {
                    LiveEqualizerBars(color = GlowMedia)
                } else if (!media.hasNotificationAccess) {
                    Text(
                        text = "LINK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .clickable(onClick = onGrantNotificationAccess)
                            .testTag("media_link_access_chip")
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = glass.primaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("media_track_title")
                )
                Text(
                    text = media.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.secondaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(42.dp)
                        .testTag("media_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous track",
                        tint = glass.primaryInk,
                        modifier = Modifier.size(24.dp)
                    )
                }

                val playFill by animateColorAsState(
                    targetValue = if (media.isPlaying) GlowMedia else glass.tileInactiveFill,
                    label = "playButtonFill"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(playFill)
                        .border(1.dp, glass.specularBorderTop.copy(alpha = 0.6f), CircleShape)
                        .clickable(onClick = onPlayPause)
                        .testTag("media_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (media.isPlaying) "Pause media" else "Play media",
                        tint = if (media.isPlaying) Color.White else glass.primaryInk,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(42.dp)
                        .testTag("media_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next track",
                        tint = glass.primaryInk,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveEqualizerBars(color: Color) {
    val transition = rememberInfiniteTransition(label = "eq")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(370), RepeatMode.Reverse),
        label = "b3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(18.dp)
    ) {
        listOf(bar1, bar2, bar3).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight(fraction)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

data class ResolvedTilePresentation(
    val icon: ImageVector,
    val label: String,
    val subtitle: String,
    val isActive: Boolean,
    val activeColor: Color,
    val isUnavailable: Boolean = false
)

fun resolveTilePresentation(
    tileId: LuxTileId,
    snapshot: DeviceSystemSnapshot,
    recordingState: ScreenRecordingState
): ResolvedTilePresentation {
    return when (tileId) {
        LuxTileId.FLASHLIGHT -> ResolvedTilePresentation(
            icon = if (snapshot.flashlight.isEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
            label = "Torch",
            subtitle = snapshot.flashlight.statusNote,
            isActive = snapshot.flashlight.isEnabled,
            activeColor = GlowFlashlight,
            isUnavailable = !snapshot.flashlight.isSupported
        )
        LuxTileId.ROTATION -> ResolvedTilePresentation(
            icon = if (snapshot.isAutoRotateEnabled) Icons.Rounded.ScreenRotation else Icons.Rounded.ScreenLockRotation,
            label = "Rotate",
            subtitle = if (snapshot.isAutoRotateEnabled) "Auto" else "Locked",
            isActive = snapshot.isAutoRotateEnabled,
            activeColor = GlowRotation
        )
        LuxTileId.DND -> ResolvedTilePresentation(
            icon = if (snapshot.dnd.isEnabled) Icons.Rounded.DoNotDisturbOn else Icons.Rounded.DoNotDisturbOff,
            label = "Focus",
            subtitle = if (snapshot.dnd.hasPolicyAccess) snapshot.dnd.filterLabel else "Grant",
            isActive = snapshot.dnd.isEnabled,
            activeColor = GlowDnd
        )
        LuxTileId.SCREEN_RECORD -> {
            val isRec = recordingState is ScreenRecordingState.Recording
            val recLabel = when (recordingState) {
                is ScreenRecordingState.Recording -> {
                    val m = recordingState.elapsedSeconds / 60
                    val s = recordingState.elapsedSeconds % 60
                    String.format("%02d:%02d", m, s)
                }
                is ScreenRecordingState.Saved -> "Saved"
                is ScreenRecordingState.Error -> "Error"
                ScreenRecordingState.Idle -> "Ready"
            }
            ResolvedTilePresentation(
                icon = if (isRec) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                label = if (isRec) "Stop Rec" else "Record",
                subtitle = recLabel,
                isActive = isRec,
                activeColor = GlowRecord
            )
        }
        LuxTileId.SCREENSHOT -> ResolvedTilePresentation(
            icon = Icons.Rounded.Screenshot,
            label = "Capture",
            subtitle = if (snapshot.permissions.isAccessibilityServiceRunning) "OS Shot" else "Setup",
            isActive = snapshot.permissions.isAccessibilityServiceRunning,
            activeColor = GlowScreenshot
        )
        LuxTileId.BATTERY_SAVER -> ResolvedTilePresentation(
            icon = Icons.Rounded.BatterySaver,
            label = "Battery",
            subtitle = if (snapshot.battery.isPowerSaveMode) "Saver On" else "${snapshot.battery.percent}%",
            isActive = snapshot.battery.isPowerSaveMode,
            activeColor = GlowBatterySaver
        )
        LuxTileId.LOCATION -> ResolvedTilePresentation(
            icon = if (snapshot.isLocationEnabled) Icons.Rounded.LocationOn else Icons.Rounded.LocationOff,
            label = "Location",
            subtitle = if (snapshot.isLocationEnabled) "GPS On" else "Off",
            isActive = snapshot.isLocationEnabled,
            activeColor = GlowLocation
        )
        LuxTileId.HOTSPOT -> ResolvedTilePresentation(
            icon = Icons.Rounded.WifiTethering,
            label = "Hotspot",
            subtitle = snapshot.hotspot.statusLabel,
            isActive = snapshot.hotspot.isEnabled == true,
            activeColor = GlowHotspot
        )
        LuxTileId.CAST -> ResolvedTilePresentation(
            icon = if (snapshot.cast.isCasting) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
            label = "Cast",
            subtitle = if (snapshot.cast.isCasting) snapshot.cast.activeRouteName else "Ready",
            isActive = snapshot.cast.isCasting,
            activeColor = GlowCast
        )
        LuxTileId.AIRPLANE -> ResolvedTilePresentation(
            icon = Icons.Rounded.AirplanemodeActive,
            label = "Plane",
            subtitle = if (snapshot.isAirplaneModeOn) "On" else "Off",
            isActive = snapshot.isAirplaneModeOn,
            activeColor = GlowAirplane
        )
        LuxTileId.WIFI -> ResolvedTilePresentation(
            icon = if (snapshot.wifi.isEnabled) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
            label = "Wi-Fi",
            subtitle = snapshot.wifi.networkLabel,
            isActive = snapshot.wifi.isEnabled,
            activeColor = GlowWifi
        )
        LuxTileId.BLUETOOTH -> ResolvedTilePresentation(
            icon = if (snapshot.bluetooth.isEnabled) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
            label = "Bluetooth",
            subtitle = snapshot.bluetooth.statusLabel,
            isActive = snapshot.bluetooth.isEnabled,
            activeColor = GlowBluetooth,
            isUnavailable = !snapshot.bluetooth.isSupported
        )
        LuxTileId.MOBILE_DATA -> ResolvedTilePresentation(
            icon = if (snapshot.mobile.isCellularActive) Icons.Rounded.SignalCellular4Bar else Icons.Rounded.SignalCellularOff,
            label = "Mobile",
            subtitle = snapshot.mobile.networkGeneration,
            isActive = snapshot.mobile.isCellularActive,
            activeColor = GlowCellular,
            isUnavailable = !snapshot.mobile.isSupported
        )
    }
}
