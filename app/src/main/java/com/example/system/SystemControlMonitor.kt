package com.example.system

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaRouter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.service.LuxControlAccessibilityService
import com.example.service.LuxEdgeGestureService
import com.example.service.LuxMediaNotificationListener
import com.example.service.LuxMediaSessionBridge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Observes live Android system state across all 16 control domains using real
 * framework BroadcastReceivers, ContentObservers, NetworkCallbacks, TorchCallbacks,
 * and TelephonyCallbacks. Zero simulated or placeholder hardware values.
 */
class SystemControlMonitor(private val context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _snapshot = MutableStateFlow(DeviceSystemSnapshot())
    val snapshot: StateFlow<DeviceSystemSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var isStarted = false
    @Volatile
    private var torchEnabledState = false
    @Volatile
    private var torchCameraId: String? = null
    @Volatile
    private var latestSignalLevel = 0

    private var bridgeJob: Job? = null
    private var telephonyCallback: Any? = null

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refreshAll()
        }
    }

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            refreshAll()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshAll()
        override fun onLost(network: Network) = refreshAll()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refreshAll()
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (torchCameraId == null || cameraId == torchCameraId) {
                torchCameraId = cameraId
                torchEnabledState = enabled
                refreshAll()
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) {
                torchEnabledState = false
                refreshAll()
            }
        }
    }

    fun start(scope: CoroutineScope) {
        if (isStarted) {
            refreshAll()
            return
        }
        isStarted = true
        torchCameraId = findFlashCameraId()

        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                systemReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }

        runCatching {
            appContext.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                false,
                settingsObserver
            )
            appContext.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                false,
                settingsObserver
            )
            appContext.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                false,
                settingsObserver
            )
            appContext.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
                false,
                settingsObserver
            )
        }

        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.registerDefaultNetworkCallback(networkCallback)
        }

        runCatching {
            val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager?.registerTorchCallback(torchCallback, mainHandler)
        }

        registerSignalStrengthListener()

        bridgeJob?.cancel()
        bridgeJob = scope.launch {
            combine(
                LuxMediaSessionBridge.mediaState,
                LuxControlAccessibilityService.isRunning,
                LuxEdgeGestureService.isRunning
            ) { mediaState, _, _ -> mediaState }
                .collect {
                    refreshAll()
                }
        }

        refreshAll()
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        bridgeJob?.cancel()
        bridgeJob = null

        runCatching { appContext.unregisterReceiver(systemReceiver) }
        runCatching { appContext.contentResolver.unregisterContentObserver(settingsObserver) }
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(networkCallback)
        }
        runCatching {
            val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager?.unregisterTorchCallback(torchCallback)
        }
        unregisterSignalStrengthListener()
    }

    fun refreshAll() {
        val permissions = readPermissionHealth()
        val now = Date()
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)

        LuxMediaSessionBridge.updateAccessState(permissions.hasNotificationListenerAccess)

        _snapshot.value = DeviceSystemSnapshot(
            formattedTime = timeFmt,
            formattedDate = dateFmt,
            wifi = readWifiState(),
            bluetooth = readBluetoothState(permissions.hasBluetoothConnectPermission),
            mobile = readMobileNetworkState(permissions.hasReadPhoneStatePermission),
            isAirplaneModeOn = readAirplaneMode(),
            flashlight = readFlashlightState(),
            brightness = readBrightnessState(permissions.canWriteSettings),
            volume = readVolumeState(),
            isLocationEnabled = readLocationEnabled(),
            isAutoRotateEnabled = readAutoRotateEnabled(),
            battery = readBatteryState(),
            dnd = readDoNotDisturbState(permissions.hasDndPolicyAccess),
            media = LuxMediaSessionBridge.mediaState.value.copy(
                hasNotificationAccess = permissions.hasNotificationListenerAccess
            ),
            cast = readCastState(),
            hotspot = readHotspotState(),
            permissions = permissions
        )
    }

    private fun readWifiState(): WifiSystemState {
        val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val enabled = runCatching { wm?.isWifiEnabled == true }.getOrDefault(false)
        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val label = when {
            !enabled -> "Off"
            connected -> {
                @Suppress("DEPRECATION")
                val rawSsid = runCatching { wm?.connectionInfo?.ssid }.getOrNull()
                val cleaned = rawSsid?.trim('"')?.takeIf {
                    it.isNotBlank() && !it.equals("<unknown ssid>", ignoreCase = true)
                }
                cleaned ?: "Connected"
            }
            else -> "On"
        }
        return WifiSystemState(
            isEnabled = enabled,
            isConnected = connected,
            networkLabel = label
        )
    }

    @SuppressLint("MissingPermission")
    private fun readBluetoothState(hasConnectPermission: Boolean): BluetoothSystemState {
        val hasFeature = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val bm = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter ?: return BluetoothSystemState(
            isSupported = hasFeature,
            isEnabled = false,
            hasPermission = hasConnectPermission,
            statusLabel = "No Radio"
        )
        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        val label = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasConnectPermission -> {
                if (enabled) "On" else "Grant BT"
            }
            enabled -> "On"
            else -> "Off"
        }
        return BluetoothSystemState(
            isSupported = true,
            isEnabled = enabled,
            hasPermission = hasConnectPermission,
            statusLabel = label
        )
    }

    @SuppressLint("MissingPermission")
    private fun readMobileNetworkState(hasPhonePermission: Boolean): MobileNetworkSystemState {
        val hasTelephony = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (!hasTelephony || tm == null) {
            return MobileNetworkSystemState(
                isSupported = false,
                carrierName = "No Cellular",
                networkGeneration = "Wi-Fi Only",
                hasPhoneStatePermission = hasPhonePermission
            )
        }

        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val cellularActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val carrier = runCatching {
            tm.networkOperatorName?.takeIf { it.isNotBlank() }
                ?: tm.simOperatorName?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "No Service"

        val dataEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasPhonePermission) {
            runCatching { tm.isDataEnabled }.getOrDefault(cellularActive)
        } else {
            cellularActive
        }

        val generation = if (hasPhonePermission) {
            val type = runCatching { tm.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
            mapNetworkGeneration(type)
        } else {
            if (cellularActive) "Active" else "Cellular"
        }

        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { tm.signalStrength?.level ?: latestSignalLevel }.getOrDefault(latestSignalLevel)
        } else {
            latestSignalLevel
        }.coerceIn(0, 4)

        return MobileNetworkSystemState(
            isSupported = true,
            isCellularActive = cellularActive,
            isMobileDataEnabled = dataEnabled,
            carrierName = carrier,
            networkGeneration = generation,
            signalLevel = level,
            hasPhoneStatePermission = hasPhonePermission
        )
    }

    private fun readAirplaneMode(): Boolean {
        return runCatching {
            Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        }.getOrDefault(false)
    }

    private fun readFlashlightState(): FlashlightSystemState {
        val camId = torchCameraId ?: findFlashCameraId().also { torchCameraId = it }
        val supported = camId != null
        return FlashlightSystemState(
            isSupported = supported,
            isEnabled = supported && torchEnabledState,
            cameraId = camId,
            statusNote = when {
                !supported -> "No Flash Unit"
                torchEnabledState -> "On"
                else -> "Off"
            }
        )
    }

    private fun readBrightnessState(canWrite: Boolean): BrightnessSystemState {
        val cr = appContext.contentResolver
        val level = runCatching {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        }.getOrDefault(128).coerceIn(0, 255)
        val isAuto = runCatching {
            Settings.System.getInt(
                cr,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }.getOrDefault(false)

        return BrightnessSystemState(
            level = level,
            isAutomatic = isAuto,
            canWriteSystemSettings = canWrite
        )
    }

    private fun readVolumeState(): VolumeSystemState {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return VolumeSystemState()

        fun readStream(stream: Int, label: String): VolumeStreamInfo {
            val max = runCatching { am.getStreamMaxVolume(stream) }.getOrDefault(15).coerceAtLeast(1)
            val current = runCatching { am.getStreamVolume(stream) }.getOrDefault(0).coerceIn(0, max)
            return VolumeStreamInfo(stream, label, current, max)
        }

        val ringerLabel = when (runCatching { am.ringerMode }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Normal"
        }

        return VolumeSystemState(
            media = readStream(AudioManager.STREAM_MUSIC, "Media"),
            ring = readStream(AudioManager.STREAM_RING, "Ring"),
            alarm = readStream(AudioManager.STREAM_ALARM, "Alarm"),
            notification = readStream(AudioManager.STREAM_NOTIFICATION, "Notify"),
            ringerModeLabel = ringerLabel
        )
    }

    private fun readLocationEnabled(): Boolean {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { lm?.isLocationEnabled == true }.getOrDefault(false)
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                Settings.Secure.getInt(
                    appContext.contentResolver,
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF
                ) != Settings.Secure.LOCATION_MODE_OFF
            }.getOrDefault(false)
        }
    }

    private fun readAutoRotateEnabled(): Boolean {
        return runCatching {
            Settings.System.getInt(
                appContext.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            ) == 1
        }.getOrDefault(false)
    }

    private fun readBatteryState(): BatterySystemState {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = runCatching { pm?.isPowerSaveMode == true }.getOrDefault(false)
        val batteryIntent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) ((level * 100) / scale).coerceIn(0, 100) else 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatterySystemState(
            percent = pct,
            isCharging = charging,
            isPowerSaveMode = isPowerSave
        )
    }

    private fun readDoNotDisturbState(hasPolicyAccess: Boolean): DoNotDisturbSystemState {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        val filter = runCatching {
            nm?.currentInterruptionFilter
                ?: android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)

        val enabled = filter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN

        val label = when (filter) {
            android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority"
            android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms Only"
            android.app.NotificationManager.INTERRUPTION_FILTER_NONE -> "Total Silence"
            else -> "Off"
        }

        return DoNotDisturbSystemState(
            isEnabled = enabled,
            filterLabel = label,
            hasPolicyAccess = hasPolicyAccess
        )
    }

    private fun readCastState(): CastSystemState {
        val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val presentationDisplays = runCatching {
            dm?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.toList().orEmpty()
        }.getOrDefault(emptyList())
        if (presentationDisplays.isNotEmpty()) {
            val name = presentationDisplays.first().name?.takeIf { it.isNotBlank() } ?: "Wireless Display"
            return CastSystemState(isCasting = true, activeRouteName = name)
        }

        val mr = appContext.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter
        val route = runCatching { mr?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO) }.getOrNull()
        val defaultRoute = runCatching { mr?.defaultRoute }.getOrNull()
        val isNonDefault = route != null && defaultRoute != null && route != defaultRoute
        return if (isNonDefault) {
            CastSystemState(
                isCasting = true,
                activeRouteName = route.name?.toString()?.ifBlank { "Casting" } ?: "Casting"
            )
        } else {
            CastSystemState(isCasting = false, activeRouteName = "Cast Screen")
        }
    }

    private fun readHotspotState(): HotspotSystemState {
        // Android 10-15 restricts reading Wi-Fi AP state from unprivileged apps via public SDK.
        // We never guess or simulate hotspot state; we report transparent System Settings status.
        return HotspotSystemState(
            isEnabled = null,
            statusLabel = "Tether Settings"
        )
    }

    fun readPermissionHealth(): PermissionHealthSnapshot {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

        val hasBtConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasPhoneState = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasPostNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasRecordAudio = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasWriteSecure = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotifListener = isNotificationListenerEnabled()
        val crossWindowBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { wm?.isCrossWindowBlurEnabled == true }.getOrDefault(false)
        } else {
            false
        }

        return PermissionHealthSnapshot(
            canDrawOverlays = runCatching { Settings.canDrawOverlays(appContext) }.getOrDefault(false),
            isAccessibilityServiceRunning = LuxControlAccessibilityService.isRunning.value,
            canWriteSettings = runCatching { Settings.System.canWrite(appContext) }.getOrDefault(false),
            hasDndPolicyAccess = runCatching { nm?.isNotificationPolicyAccessGranted == true }.getOrDefault(false),
            hasNotificationListenerAccess = hasNotifListener,
            hasBluetoothConnectPermission = hasBtConnect,
            hasReadPhoneStatePermission = hasPhoneState,
            hasPostNotificationsPermission = hasPostNotif,
            hasRecordAudioPermission = hasRecordAudio,
            hasWriteSecureSettingsGrant = hasWriteSecure,
            isCrossWindowBlurSupported = crossWindowBlur,
            isEdgeGestureServiceRunning = LuxEdgeGestureService.isRunning.value
        )
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledPackages = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(appContext)
        }.getOrDefault(emptySet())
        if (appContext.packageName in enabledPackages) return true

        val flat = runCatching {
            Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners")
        }.getOrNull().orEmpty()
        val componentName = ComponentName(appContext, LuxMediaNotificationListener::class.java).flattenToString()
        return flat.contains(componentName)
    }

    private fun findFlashCameraId(): String? {
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
        return runCatching {
            val ids = cm.cameraIdList
            ids.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val isBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                hasFlash && isBack
            } ?: ids.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    private fun registerSignalStrengthListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        runCatching {
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    latestSignalLevel = signalStrength.level.coerceIn(0, 4)
                    refreshAll()
                }
            }
            tm.registerTelephonyCallback(appContext.mainExecutor, callback)
            telephonyCallback = callback
        }
    }

    private fun unregisterSignalStrengthListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val cb = telephonyCallback as? TelephonyCallback ?: return
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        runCatching { tm?.unregisterTelephonyCallback(cb) }
        telephonyCallback = null
    }

    companion object {
        fun mapNetworkGeneration(networkType: Int): String {
            return when (networkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_CDMA -> "2G"
                else -> "Cellular"
            }
        }
    }
}
