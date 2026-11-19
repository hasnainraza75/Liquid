package com.example.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwipeDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.LuxThemeMode
import com.example.data.LuxTileId
import com.example.ui.LuxUiState
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.theme.GlacialCyan
import com.example.ui.theme.LocalLiquidGlassTokens
import com.example.ui.theme.StatusGranted

@Composable
fun CustomizeScreen(
    uiState: LuxUiState,
    contentPadding: PaddingValues,
    onSelectTheme: (LuxThemeMode) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
    onGlassOpacityChange: (Float) -> Unit,
    onEdgeTriggerToggle: (Boolean) -> Unit,
    onEdgeGeometryChange: (Float, Int) -> Unit,
    onEdgeHintToggle: (Boolean) -> Unit,
    onRecordMicToggle: (Boolean) -> Unit,
    onToggleTile: (LuxTileId, Boolean) -> Unit,
    onMoveTile: (LuxTileId, Boolean) -> Unit,
    onResetTiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlassTokens.current
    val prefs = uiState.preferences
    val allTiles = prefs.allOrderedTiles

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 14.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("customize_screen_list")
        ) {
            item(key = "studio_header") {
                Column {
                    Text(
                        text = "Liquid Glass Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        color = glass.primaryInk
                    )
                    Text(
                        text = "Calibrate optical frost, top-right swipe trigger, and circular tile order.",
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.secondaryInk
                    )
                }
            }

            // 1. Top-Right Corner Swipe Gesture Configurator
            item(key = "edge_gesture_card") {
                LiquidGlassPanel(
                    accentGlow = if (prefs.edgeTriggerEnabled) StatusGranted else GlacialCyan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwipeDown,
                                    contentDescription = "Top-right swipe trigger",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Top-Right Corner Swipe Overlay",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = glass.primaryInk
                                    )
                                    Text(
                                        text = "Swipe down from top-right edge above any app",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = glass.secondaryInk
                                    )
                                }
                            }
                            Switch(
                                checked = prefs.edgeTriggerEnabled,
                                onCheckedChange = onEdgeTriggerToggle,
                                modifier = Modifier.testTag("edge_trigger_switch")
                            )
                        }

                        // Live Corner Zone Diagram Preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(glass.sliderTrackFill)
                                .border(1.dp, glass.specularBorderTop.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        ) {
                            Text(
                                text = "Screen Top Edge",
                                style = MaterialTheme.typography.labelSmall,
                                color = glass.mutedInk,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 14.dp)
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .fillMaxWidth(prefs.edgeTriggerWidthPercent)
                                    .height((prefs.edgeTriggerHeightDp * 0.72f).dp.coerceIn(22.dp, 54.dp))
                                    .clip(RoundedCornerShape(bottomStart = 14.dp, topEnd = 18.dp))
                                    .background(GlacialCyan.copy(alpha = 0.34f))
                                    .border(
                                        1.dp,
                                        GlacialCyan,
                                        RoundedCornerShape(bottomStart = 14.dp, topEnd = 18.dp)
                                    )
                            ) {
                                Text(
                                    text = "SWIPE ZONE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.primaryInk
                                )
                            }
                        }

                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Top-Right Zone Width",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = glass.secondaryInk
                                )
                                Text(
                                    text = "${(prefs.edgeTriggerWidthPercent * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.primaryInk
                                )
                            }
                            Slider(
                                value = prefs.edgeTriggerWidthPercent,
                                onValueChange = { onEdgeGeometryChange(it, prefs.edgeTriggerHeightDp) },
                                valueRange = 0.20f..0.50f,
                                modifier = Modifier.testTag("edge_width_slider")
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Show subtle crystalline corner handle",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.primaryInk,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = prefs.showEdgeIndicatorHint,
                                onCheckedChange = onEdgeHintToggle,
                                modifier = Modifier.testTag("edge_hint_switch")
                            )
                        }
                    }
                }
            }

            // 2. Theme & Liquid Glass Optical Material Card
            item(key = "optical_material_card") {
                LiquidGlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = "Theme & Glass Material",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Theme & Optical Blur",
                                style = MaterialTheme.typography.titleMedium,
                                color = glass.primaryInk
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LuxThemeMode.entries.forEach { mode ->
                                val selected = prefs.themeMode == mode
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else glass.tileInactiveFill
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) Color.White.copy(alpha = 0.6f) else glass.specularBorderTop.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .clickable { onSelectTheme(mode) }
                                        .testTag("theme_mode_${mode.key}")
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = mode.label.replace("Liquid ", "").replace("Follow ", ""),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else glass.primaryInk
                                    )
                                }
                            }
                        }

                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.BlurOn,
                                        contentDescription = "Blur radius",
                                        tint = glass.secondaryInk,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Backdrop Blur Radius",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = glass.secondaryInk
                                    )
                                }
                                Text(
                                    text = "${prefs.blurRadiusDp.toInt()} dp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.primaryInk
                                )
                            }
                            Slider(
                                value = prefs.blurRadiusDp,
                                onValueChange = onBlurRadiusChange,
                                valueRange = 0f..48f,
                                modifier = Modifier.testTag("blur_radius_slider")
                            )
                        }

                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Glass Frost Opacity",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = glass.secondaryInk
                                )
                                Text(
                                    text = "${(prefs.glassOpacity * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.primaryInk
                                )
                            }
                            Slider(
                                value = prefs.glassOpacity,
                                onValueChange = onGlassOpacityChange,
                                valueRange = 0.14f..0.42f,
                                modifier = Modifier.testTag("glass_opacity_slider")
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Record microphone",
                                    tint = glass.secondaryInk,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Include microphone audio in Screen Recording",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = glass.primaryInk
                                )
                            }
                            Switch(
                                checked = prefs.recordWithMicrophone,
                                onCheckedChange = onRecordMicToggle,
                                modifier = Modifier.testTag("record_mic_switch")
                            )
                        }
                    }
                }
            }

            // 3. Customizable Circular Tiles Order & Visibility
            item(key = "tiles_section_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Circular Tiles Order",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.primaryInk
                    )
                    TextButton(
                        onClick = onResetTiles,
                        modifier = Modifier.testTag("reset_tiles_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset tile order",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }

            itemsIndexed(allTiles, key = { _, item -> item.key }) { index, tile ->
                val isEnabled = tile.key in prefs.enabledTileKeys
                LiquidGlassPanel(
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tile_editor_row_${tile.key}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tile.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = glass.primaryInk
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tile.capabilityTier.badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = tile.androidApiSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = glass.secondaryInk,
                                maxLines = 1
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onMoveTile(tile, true) },
                                enabled = index > 0,
                                modifier = Modifier.testTag("tile_move_up_${tile.key}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Move ${tile.title} up",
                                    tint = if (index > 0) glass.primaryInk else glass.mutedInk.copy(alpha = 0.35f)
                                )
                            }
                            IconButton(
                                onClick = { onMoveTile(tile, false) },
                                enabled = index < allTiles.lastIndex,
                                modifier = Modifier.testTag("tile_move_down_${tile.key}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "Move ${tile.title} down",
                                    tint = if (index < allTiles.lastIndex) glass.primaryInk else glass.mutedInk.copy(alpha = 0.35f)
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { onToggleTile(tile, it) },
                                modifier = Modifier.testTag("tile_toggle_${tile.key}")
                            )
                        }
                    }
                }
            }
        }
    }
}
