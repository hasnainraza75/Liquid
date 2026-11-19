package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.system.VolumeStreamInfo
import com.example.system.VolumeSystemState
import com.example.ui.theme.GlowBrightness
import com.example.ui.theme.GlowVolume
import com.example.ui.theme.LocalLiquidGlassTokens
import com.example.ui.theme.ObsidianVoid

/**
 * Dynamic vertical Liquid Glass capsule slider with animated fluid fill column,
 * luminous top meniscus rim, real drag & tap interaction, and adaptive icon morphing.
 */
@Composable
fun LiquidVerticalSlider(
    valueFraction: Float,
    percentText: String,
    label: String,
    icon: ImageVector,
    liquidAccent: Color,
    testTag: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    badgeIcon: ImageVector? = null,
    onBadgeClick: (() -> Unit)? = null,
    iconRotationDegrees: Float = 0f
) {
    val glass = LocalLiquidGlassTokens.current
    var sliderHeightPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember(valueFraction) { mutableFloatStateOf(valueFraction.coerceIn(0f, 1f)) }

    val displayedFraction = if (isDragging) dragFraction else valueFraction.coerceIn(0f, 1f)
    val animatedFill by animateFloatAsState(
        targetValue = displayedFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (isDragging) Spring.StiffnessHigh else Spring.StiffnessMedium
        ),
        label = "liquidSliderFill"
    )

    val capsuleShape = RoundedCornerShape(30.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(178.dp)
                .clip(capsuleShape)
                .background(glass.sliderTrackFill, capsuleShape)
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            glass.specularBorderTop,
                            glass.specularBorderMid,
                            glass.specularBorderBottom
                        )
                    ),
                    shape = capsuleShape
                )
                .onSizeChanged { sliderHeightPx = it.height.coerceAtLeast(1).toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val next = (1f - (offset.y / sliderHeightPx)).coerceIn(0f, 1f)
                        dragFraction = next
                        onValueChange(next)
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val next = (1f - (offset.y / sliderHeightPx)).coerceIn(0f, 1f)
                            dragFraction = next
                            onValueChange(next)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val next = (1f - (change.position.y / sliderHeightPx)).coerceIn(0f, 1f)
                            dragFraction = next
                            onValueChange(next)
                        }
                    )
                }
                .testTag(testTag)
        ) {
            // Animated Liquid Fill Column
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(animatedFill.coerceAtLeast(0.02f))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                liquidAccent.copy(alpha = 0.96f),
                                glass.sliderLiquidTop,
                                glass.sliderLiquidBottom
                            )
                        )
                    )
            ) {
                // Specular Meniscus Shimmer Line at the top edge of the liquid
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(Color.White.copy(alpha = 0.85f))
                )
            }

            // Top Percentage Readout
            Text(
                text = percentText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (animatedFill > 0.80f) ObsidianVoid else glass.primaryInk,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            )

            // Bottom Dynamic Morphing Icon
            Icon(
                imageVector = icon,
                contentDescription = "$label $percentText",
                tint = if (animatedFill > 0.18f) ObsidianVoid else glass.primaryInk,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .size(26.dp)
                    .rotate(iconRotationDegrees)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (badgeText != null && onBadgeClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(glass.tileInactiveFill)
                    .border(0.8.dp, glass.specularBorderTop.copy(alpha = 0.5f), CircleShape)
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onBadgeClick)
                    .testTag("${testTag}_badge")
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                if (badgeIcon != null) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = badgeText,
                        tint = glass.primaryInk,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.primaryInk,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = glass.secondaryInk
            )
        }
    }
}

fun brightnessIconFor(fraction: Float): ImageVector = when {
    fraction < 0.30f -> Icons.Rounded.BrightnessLow
    fraction < 0.72f -> Icons.Rounded.BrightnessMedium
    else -> Icons.Rounded.BrightnessHigh
}

fun volumeIconFor(fraction: Float): ImageVector = when {
    fraction <= 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
    fraction < 0.52f -> Icons.AutoMirrored.Rounded.VolumeDown
    else -> Icons.AutoMirrored.Rounded.VolumeUp
}

@Composable
fun BrightnessAndVolumeSlidersRow(
    brightnessFraction: Float,
    brightnessPercent: Int,
    isAutoBrightness: Boolean,
    volumeState: VolumeSystemState,
    onBrightnessChange: (Float) -> Unit,
    onToggleAutoBrightness: () -> Unit,
    onMediaVolumeChange: (Float) -> Unit,
    onOpenAudioMixer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        LiquidVerticalSlider(
            valueFraction = brightnessFraction,
            percentText = "$brightnessPercent%",
            label = "Brightness",
            icon = brightnessIconFor(brightnessFraction),
            liquidAccent = GlowBrightness,
            testTag = "slider_brightness",
            onValueChange = onBrightnessChange,
            badgeText = if (isAutoBrightness) "Auto ON" else "Manual",
            badgeIcon = Icons.Rounded.AutoAwesome,
            onBadgeClick = onToggleAutoBrightness,
            iconRotationDegrees = brightnessFraction * 120f,
            modifier = Modifier.weight(1f)
        )
        LiquidVerticalSlider(
            valueFraction = volumeState.media.fraction,
            percentText = "${volumeState.media.percent}%",
            label = "Volume",
            icon = volumeIconFor(volumeState.media.fraction),
            liquidAccent = GlowVolume,
            testTag = "slider_volume",
            onValueChange = onMediaVolumeChange,
            badgeText = "Mixer",
            badgeIcon = Icons.Rounded.Tune,
            onBadgeClick = onOpenAudioMixer,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioMixerModalSheet(
    volumeState: VolumeSystemState,
    onStreamChange: (streamType: Int, fraction: Float) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalLiquidGlassTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (glass.isDark) Color(0xFF0E1A30) else Color(0xFFF5FAFF),
        contentColor = glass.primaryInk
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Live Audio Mixer",
                        style = MaterialTheme.typography.titleLarge,
                        color = glass.primaryInk
                    )
                    Text(
                        text = "Ringer Mode: ${volumeState.ringerModeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.secondaryInk
                    )
                }
            }

            AudioStreamRow(
                info = volumeState.media,
                icon = Icons.Rounded.MusicNote,
                testTag = "mixer_slider_media",
                onChange = { onStreamChange(volumeState.media.streamType, it) }
            )
            AudioStreamRow(
                info = volumeState.ring,
                icon = Icons.Rounded.RingVolume,
                testTag = "mixer_slider_ring",
                onChange = { onStreamChange(volumeState.ring.streamType, it) }
            )
            AudioStreamRow(
                info = volumeState.alarm,
                icon = Icons.Rounded.Alarm,
                testTag = "mixer_slider_alarm",
                onChange = { onStreamChange(volumeState.alarm.streamType, it) }
            )
            AudioStreamRow(
                info = volumeState.notification,
                icon = Icons.Rounded.Notifications,
                testTag = "mixer_slider_notification",
                onChange = { onStreamChange(volumeState.notification.streamType, it) }
            )
        }
    }
}

@Composable
private fun AudioStreamRow(
    info: VolumeStreamInfo,
    icon: ImageVector,
    testTag: String,
    onChange: (Float) -> Unit
) {
    val glass = LocalLiquidGlassTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = info.label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = info.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = glass.primaryInk
                )
            }
            Text(
                text = "${info.current}/${info.max} (${info.percent}%)",
                style = MaterialTheme.typography.labelSmall,
                color = glass.secondaryInk
            )
        }
        Slider(
            value = info.fraction,
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
