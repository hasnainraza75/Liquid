package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.screens.ControlCenterScreen
import com.example.ui.screens.CustomizeScreen
import com.example.ui.screens.SystemAuditScreen
import com.example.ui.theme.LocalLiquidGlassTokens
import com.example.ui.theme.LuxControlTheme
import kotlinx.coroutines.flow.collectLatest

const val ROUTE_CONTROL_CENTER = "control_center"
const val ROUTE_CUSTOMIZE = "customize_studio"
const val ROUTE_SYSTEM_AUDIT = "system_audit"

private data class LuxNavDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

private val luxDestinations = listOf(
    LuxNavDestination(
        route = ROUTE_CONTROL_CENTER,
        labelRes = R.string.nav_control_center,
        icon = Icons.Rounded.Tune,
        testTag = "nav_tab_control"
    ),
    LuxNavDestination(
        route = ROUTE_CUSTOMIZE,
        labelRes = R.string.nav_customize,
        icon = Icons.Rounded.DashboardCustomize,
        testTag = "nav_tab_customize"
    ),
    LuxNavDestination(
        route = ROUTE_SYSTEM_AUDIT,
        labelRes = R.string.nav_system,
        icon = Icons.Rounded.Security,
        testTag = "nav_tab_system"
    )
)

@Composable
fun LuxAppRoot(
    viewModel: LuxControlViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_CONTROL_CENTER

    // Refresh real system telemetry immediately whenever user returns from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.onScreenCaptureConsentResult(result.resultCode, result.data)
        } else {
            viewModel.onScreenCaptureConsentResult(Activity.RESULT_CANCELED, null)
        }
    }

    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshSystemState()
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
                    val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                    if (mpm != null) {
                        runCatching {
                            mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                        }.onFailure { err ->
                            snackbarHostState.showSnackbar(
                                "Screen capture dialog error: ${err.localizedMessage}"
                            )
                        }
                    } else {
                        snackbarHostState.showSnackbar("MediaProjectionManager unavailable on this device.")
                    }
                }
                LuxUiEvent.RequestRuntimePermissions -> {
                    val requested = buildList {
                        add(Manifest.permission.READ_PHONE_STATE)
                        add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            add(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()
                    runtimePermissionsLauncher.launch(requested)
                }
            }
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LuxControlTheme(
        themeMode = uiState.preferences.themeMode,
        blurRadiusDp = uiState.preferences.blurRadiusDp,
        glassOpacity = uiState.preferences.glassOpacity
    ) {
        LiquidGlassBackdrop(transparentWindow = false) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            val glass = LocalLiquidGlassTokens.current
                            Snackbar(
                                snackbarData = data,
                                containerColor = if (glass.isDark) Color(0xFF10203A) else Color(0xFFF6FBFF),
                                contentColor = glass.primaryInk,
                                modifier = Modifier.testTag("lux_feedback_snackbar")
                            )
                        }
                    },
                    bottomBar = {
                        if (!useNavRail) {
                            LuxFloatingGlassNavBar(
                                currentRoute = currentRoute,
                                onSelectRoute = ::navigateTo
                            )
                        }
                    }
                ) { innerPadding ->
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (useNavRail) {
                            val glass = LocalLiquidGlassTokens.current
                            NavigationRail(
                                containerColor = glass.panelBottomFill.copy(alpha = 0.72f),
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                            ) {
                                luxDestinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTo(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = dest.icon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(dest.labelRes)) },
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_CONTROL_CENTER,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(ROUTE_CONTROL_CENTER) {
                                ControlCenterScreen(
                                    uiState = uiState,
                                    isOverlayMode = false,
                                    contentPadding = innerPadding,
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
                                    onPrimaryHeaderAction = viewModel::launchLiveOverlayShade,
                                    onNavigateCustomize = { navigateTo(ROUTE_CUSTOMIZE) },
                                    onNavigateSystemAudit = { navigateTo(ROUTE_SYSTEM_AUDIT) }
                                )
                            }
                            composable(ROUTE_CUSTOMIZE) {
                                CustomizeScreen(
                                    uiState = uiState,
                                    contentPadding = innerPadding,
                                    onSelectTheme = viewModel::setThemeMode,
                                    onBlurRadiusChange = viewModel::setBlurRadiusDp,
                                    onGlassOpacityChange = viewModel::setGlassOpacity,
                                    onEdgeTriggerToggle = viewModel::setEdgeTriggerEnabled,
                                    onEdgeGeometryChange = viewModel::setEdgeGeometry,
                                    onEdgeHintToggle = viewModel::setShowEdgeIndicatorHint,
                                    onRecordMicToggle = viewModel::setRecordWithMicrophone,
                                    onToggleTile = viewModel::setTileEnabled,
                                    onMoveTile = viewModel::moveTile,
                                    onResetTiles = viewModel::resetTileDefaults
                                )
                            }
                            composable(ROUTE_SYSTEM_AUDIT) {
                                SystemAuditScreen(
                                    uiState = uiState,
                                    contentPadding = innerPadding,
                                    onRefresh = viewModel::refreshSystemState,
                                    onOpenOverlayPermission = viewModel::openOverlayPermission,
                                    onOpenAccessibilitySettings = viewModel::openAccessibilitySettings,
                                    onOpenWriteSettings = viewModel::openWriteSettingsPermission,
                                    onOpenDndPolicy = viewModel::openDndPolicyPermission,
                                    onOpenNotificationListener = viewModel::openNotificationListenerSettings,
                                    onRequestRuntimePermissions = viewModel::requestRuntimePermissions,
                                    onCopiedAdbCommand = {
                                        viewModel.refreshSystemState()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LuxFloatingGlassNavBar(
    currentRoute: String,
    onSelectRoute: (String) -> Unit
) {
    val glass = LocalLiquidGlassTokens.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(glass.panelTopFill, glass.panelBottomFill)
                    )
                )
                .border(1.1.dp, glass.specularBorderTop.copy(alpha = 0.65f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            luxDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { onSelectRoute(dest.route) }
                        .testTag(dest.testTag)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = stringResource(dest.labelRes),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else glass.primaryInk,
                        modifier = Modifier.size(19.dp)
                    )
                    if (selected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(dest.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
