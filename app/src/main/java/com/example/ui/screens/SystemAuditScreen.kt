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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PermDeviceInformation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.ui.LuxUiState
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.LiquidGlassPillButton
import com.example.ui.theme.LocalLiquidGlassTokens
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusGranted
import com.example.ui.theme.StatusWarning

@Composable
fun SystemAuditScreen(
    uiState: LuxUiState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenDndPolicy: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onCopiedAdbCommand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlassTokens.current
    val perms = uiState.snapshot.permissions
    val probe = uiState.deviceProbe
    val report = uiState.architectureReport
    val clipboard = LocalClipboardManager.current

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 14.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("system_audit_screen_list")
        ) {
            item(key = "audit_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "System Integration",
                            style = MaterialTheme.typography.headlineMedium,
                            color = glass.primaryInk
                        )
                        Text(
                            text = "Android 15 permissions & itel City 200 (C681L) architecture report",
                            style = MaterialTheme.typography.bodySmall,
                            color = glass.secondaryInk
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("audit_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh live system audit",
                            tint = glass.primaryInk
                        )
                    }
                }
            }

            // 1. Live Special Access & Runtime Permission Manager
            item(key = "permissions_hub") {
                LiquidGlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permissions_checklist_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Android 15 Special Access & Services",
                            style = MaterialTheme.typography.titleMedium,
                            color = glass.primaryInk
                        )

                        PermissionRowItem(
                            icon = Icons.Rounded.Layers,
                            title = "Display Over Other Apps",
                            detail = "Required for top-right swipe handle & floating shade",
                            granted = perms.canDrawOverlays,
                            testTag = "perm_row_overlay",
                            onClick = onOpenOverlayPermission
                        )
                        PermissionRowItem(
                            icon = Icons.Rounded.AccessibilityNew,
                            title = "Accessibility Service",
                            detail = "Elevated top-right edge strip + native OS Screenshot",
                            granted = perms.isAccessibilityServiceRunning,
                            testTag = "perm_row_accessibility",
                            onClick = onOpenAccessibilitySettings
                        )
                        PermissionRowItem(
                            icon = Icons.Rounded.SettingsApplications,
                            title = "Modify System Settings",
                            detail = "Direct real Brightness & Auto-Rotate control",
                            granted = perms.canWriteSettings,
                            testTag = "perm_row_write_settings",
                            onClick = onOpenWriteSettings
                        )
                        PermissionRowItem(
                            icon = Icons.Rounded.DoNotDisturbOn,
                            title = "Do Not Disturb Policy Access",
                            detail = "Direct real interruption filter toggling",
                            granted = perms.hasDndPolicyAccess,
                            testTag = "perm_row_dnd",
                            onClick = onOpenDndPolicy
                        )
                        PermissionRowItem(
                            icon = Icons.Rounded.MusicNote,
                            title = "Notification Access (Media Bridge)",
                            detail = "Live Now Playing title, artist, artwork & controls",
                            granted = perms.hasNotificationListenerAccess,
                            testTag = "perm_row_media",
                            onClick = onOpenNotificationListener
                        )

                        HorizontalDivider(color = glass.specularBorderMid)

                        val runtimeAllGranted = perms.hasBluetoothConnectPermission &&
                            perms.hasReadPhoneStatePermission &&
                            perms.hasPostNotificationsPermission
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Runtime Telemetry Permissions",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = glass.primaryInk
                                )
                                Text(
                                    text = "BT: ${yn(perms.hasBluetoothConnectPermission)}  •  Phone: ${yn(perms.hasReadPhoneStatePermission)}  •  Notify: ${yn(perms.hasPostNotificationsPermission)}  •  Mic: ${yn(perms.hasRecordAudioPermission)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.secondaryInk
                                )
                            }
                            if (!runtimeAllGranted) {
                                LiquidGlassPillButton(
                                    text = "Grant",
                                    icon = Icons.Rounded.PermDeviceInformation,
                                    testTag = "request_runtime_permissions_button",
                                    highlighted = true,
                                    onClick = onRequestRuntimePermissions
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "All runtime permissions granted",
                                    tint = StatusGranted
                                )
                            }
                        }
                    }
                }
            }

            // 2. Live Runtime Hardware Probe
            if (probe != null) {
                item(key = "live_device_probe") {
                    LiquidGlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_hardware_probe_card")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.DeveloperBoard,
                                    contentDescription = "Runtime device probe",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Current Runtime Device Probe",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = glass.primaryInk
                                )
                            }
                            KeyValueRow("Device", "${probe.manufacturer} ${probe.model} (${probe.deviceCode})")
                            KeyValueRow("OS / API", "Android ${probe.androidRelease} (SDK ${probe.sdkInt})")
                            KeyValueRow("Hardware / SoC", probe.hardware)
                            KeyValueRow(
                                "Cross-Window Blur",
                                if (probe.supportsCrossWindowBlur) "Enabled (FLAG_BLUR_BEHIND)" else "Disabled / Fallback"
                            )
                            KeyValueRow(
                                "Privilege Level",
                                if (probe.isRunningAsSystemApp) "System Priv-App" else "Standard User APK"
                            )
                            KeyValueRow(
                                "ADB WRITE_SECURE",
                                if (perms.hasWriteSecureSettingsGrant) "Granted" else "Not Granted"
                            )
                            KeyValueRow(
                                "Hardware Probes",
                                "Flash=${yn(probe.hasFlashHardware)}  Cellular=${yn(probe.hasTelephonyHardware)}  BT=${yn(probe.hasBluetoothHardware)}"
                            )
                        }
                    }
                }
            }

            // 3. Honest itel City 200 (C681L) OEM SystemUI Limitation & AOSP Architecture Report
            if (report != null) {
                item(key = "itel_architecture_report") {
                    LiquidGlassPanel(
                        accentGlow = StatusWarning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("itel_c681l_audit_card")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = "SystemUI Limitation Disclosure",
                                    tint = StatusWarning
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "itel City 200 (C681L) SystemUI Audit",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = glass.primaryInk
                                )
                            }

                            KeyValueRow("Target", report.targetDeviceName)
                            KeyValueRow("SoC / GPU", report.chipsetInfo)
                            KeyValueRow("OEM ROM", report.oemFirmwareInfo)

                            Text(
                                text = "Exact OEM SystemUI Replacement Limitation",
                                style = MaterialTheme.typography.titleSmall,
                                color = StatusBlocked
                            )
                            Text(
                                text = report.systemUiLimitationSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = glass.primaryInk
                            )

                            Text(
                                text = "Bootloader & Firmware Compatibility",
                                style = MaterialTheme.typography.titleSmall,
                                color = StatusWarning
                            )
                            Text(
                                text = report.bootloaderAssessment,
                                style = MaterialTheme.typography.bodySmall,
                                color = glass.secondaryInk
                            )

                            Text(
                                text = "Deployment Paths",
                                style = MaterialTheme.typography.titleSmall,
                                color = glass.primaryInk
                            )
                            report.recommendedDeploymentModes.forEach { mode ->
                                Text(
                                    text = "• $mode",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = glass.secondaryInk
                                )
                            }

                            // Copyable ADB Elevation Command
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(glass.sliderTrackFill)
                                    .border(1.dp, glass.specularBorderMid, RoundedCornerShape(14.dp))
                                    .clickable {
                                        clipboard.setText(AnnotatedString(report.adbSecureGrantCommand))
                                        onCopiedAdbCommand()
                                    }
                                    .testTag("copy_adb_command_box")
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = report.adbSecureGrantCommand,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.primaryInk,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy ADB grant command",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                item(key = "matrix_header") {
                    Text(
                        text = "16-Control Android 15 Capability Matrix",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.primaryInk
                    )
                }

                items(report.controlMatrix, key = { it.controlName }) { row ->
                    LiquidGlassPanel(
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = row.controlName,
                                style = MaterialTheme.typography.titleSmall,
                                color = glass.primaryInk
                            )
                            Text(
                                text = "State: ${row.stateSource}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "APK Action: ${row.unprivilegedAction}",
                                style = MaterialTheme.typography.bodySmall,
                                color = glass.primaryInk
                            )
                            Text(
                                text = "Platform Note: ${row.systemUiRequirement}",
                                style = MaterialTheme.typography.bodySmall,
                                color = glass.secondaryInk
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRowItem(
    icon: ImageVector,
    title: String,
    detail: String,
    granted: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val glass = LocalLiquidGlassTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(glass.tileInactiveFill.copy(alpha = glass.tileInactiveFill.alpha * 0.72f))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (granted) StatusGranted else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = glass.primaryInk
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.secondaryInk
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (granted) "READY" else "SETUP",
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) StatusGranted else StatusWarning,
            modifier = Modifier
                .clip(CircleShape)
                .background((if (granted) StatusGranted else StatusWarning).copy(alpha = 0.16f))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    val glass = LocalLiquidGlassTokens.current
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = glass.secondaryInk,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = glass.primaryInk
        )
    }
}

private fun yn(value: Boolean): String = if (value) "Yes" else "No"
