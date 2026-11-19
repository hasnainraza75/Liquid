package com.example.data

enum class ControlCapabilityTier(val badgeLabel: String, val explanation: String) {
    DIRECT_API(
        badgeLabel = "Direct API",
        explanation = "Controlled directly in real time via standard Android 15 SDK APIs."
    ),
    SPECIAL_PERMISSION(
        badgeLabel = "Special Access",
        explanation = "Controlled directly once the user grants Android special access (Write Settings, DND Policy, Accessibility, or MediaProjection)."
    ),
    SYSTEM_PANEL(
        badgeLabel = "System Panel",
        explanation = "Reads live device state directly; Android 10–15 restricts third-party toggling, so tapping launches Android's native quick panel or settings screen."
    ),
    PRIVILEGED_OR_PANEL(
        badgeLabel = "ADB / Panel",
        explanation = "Reads live device state directly; toggles directly only if WRITE_SECURE_SETTINGS is granted via ADB or privileged SystemUI install, otherwise opens Android Settings."
    )
}

enum class LuxTileId(
    val key: String,
    val title: String,
    val capabilityTier: ControlCapabilityTier,
    val defaultVisibleInGrid: Boolean,
    val androidApiSummary: String
) {
    FLASHLIGHT(
        key = "flashlight",
        title = "Torch",
        capabilityTier = ControlCapabilityTier.DIRECT_API,
        defaultVisibleInGrid = true,
        androidApiSummary = "CameraManager.setTorchMode + TorchCallback"
    ),
    ROTATION(
        key = "rotation",
        title = "Auto-Rotate",
        capabilityTier = ControlCapabilityTier.SPECIAL_PERMISSION,
        defaultVisibleInGrid = true,
        androidApiSummary = "Settings.System.ACCELEROMETER_ROTATION (WRITE_SETTINGS)"
    ),
    DND(
        key = "dnd",
        title = "Focus DND",
        capabilityTier = ControlCapabilityTier.SPECIAL_PERMISSION,
        defaultVisibleInGrid = true,
        androidApiSummary = "NotificationManager.setInterruptionFilter (Policy Access)"
    ),
    SCREEN_RECORD(
        key = "screen_record",
        title = "Record",
        capabilityTier = ControlCapabilityTier.SPECIAL_PERMISSION,
        defaultVisibleInGrid = true,
        androidApiSummary = "MediaProjectionManager + FGS(mediaProjection) + MediaRecorder"
    ),
    SCREENSHOT(
        key = "screenshot",
        title = "Capture",
        capabilityTier = ControlCapabilityTier.SPECIAL_PERMISSION,
        defaultVisibleInGrid = true,
        androidApiSummary = "AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT (API 28+)"
    ),
    BATTERY_SAVER(
        key = "battery_saver",
        title = "Battery Saver",
        capabilityTier = ControlCapabilityTier.PRIVILEGED_OR_PANEL,
        defaultVisibleInGrid = true,
        androidApiSummary = "PowerManager.isPowerSaveMode + ACTION_BATTERY_SAVER_SETTINGS"
    ),
    LOCATION(
        key = "location",
        title = "Location",
        capabilityTier = ControlCapabilityTier.SYSTEM_PANEL,
        defaultVisibleInGrid = true,
        androidApiSummary = "LocationManager.isLocationEnabled + ACTION_LOCATION_SOURCE_SETTINGS"
    ),
    HOTSPOT(
        key = "hotspot",
        title = "Hotspot",
        capabilityTier = ControlCapabilityTier.SYSTEM_PANEL,
        defaultVisibleInGrid = true,
        androidApiSummary = "Tethering state probe + android.settings.TETHER_SETTINGS"
    ),
    CAST(
        key = "cast",
        title = "Cast",
        capabilityTier = ControlCapabilityTier.SYSTEM_PANEL,
        defaultVisibleInGrid = true,
        androidApiSummary = "DisplayManager / MediaRouter + ACTION_CAST_SETTINGS"
    ),
    AIRPLANE(
        key = "airplane",
        title = "Airplane",
        capabilityTier = ControlCapabilityTier.PRIVILEGED_OR_PANEL,
        defaultVisibleInGrid = false,
        androidApiSummary = "Settings.Global.AIRPLANE_MODE_ON + ACTION_AIRPLANE_MODE_SETTINGS"
    ),
    WIFI(
        key = "wifi",
        title = "Wi-Fi",
        capabilityTier = ControlCapabilityTier.SYSTEM_PANEL,
        defaultVisibleInGrid = false,
        androidApiSummary = "WifiManager / ConnectivityManager + Settings.Panel.ACTION_INTERNET_CONNECTIVITY"
    ),
    BLUETOOTH(
        key = "bluetooth",
        title = "Bluetooth",
        capabilityTier = ControlCapabilityTier.SYSTEM_PANEL,
        defaultVisibleInGrid = false,
        androidApiSummary = "BluetoothManager + ACTION_REQUEST_ENABLE / ACTION_BLUETOOTH_SETTINGS"
    ),
    MOBILE_DATA(
        key = "mobile_data",
        title = "Mobile",
        capabilityTier = ControlCapabilityTier.SYSTEM_PANEL,
        defaultVisibleInGrid = false,
        androidApiSummary = "TelephonyManager + ConnectivityManager + Internet Panel"
    );

    companion object {
        val defaultGridOrder: List<LuxTileId> = entries.filter { it.defaultVisibleInGrid }

        fun fromKey(key: String): LuxTileId? = entries.firstOrNull { it.key == key }
    }
}

enum class LuxThemeMode(val key: String, val label: String) {
    DARK("dark", "Liquid Dark"),
    LIGHT("light", "Liquid Light"),
    SYSTEM("system", "Follow System");

    companion object {
        fun fromKey(key: String?): LuxThemeMode =
            entries.firstOrNull { it.key == key } ?: DARK
    }
}

data class LuxUserPreferences(
    val themeMode: LuxThemeMode = LuxThemeMode.DARK,
    val blurRadiusDp: Float = 28f,
    val glassOpacity: Float = 0.24f,
    val edgeTriggerEnabled: Boolean = false,
    val edgeTriggerWidthPercent: Float = 0.34f,
    val edgeTriggerHeightDp: Int = 44,
    val showEdgeIndicatorHint: Boolean = true,
    val recordWithMicrophone: Boolean = false,
    val orderedTileKeys: List<String> = LuxTileId.entries.map { it.key },
    val enabledTileKeys: Set<String> = LuxTileId.defaultGridOrder.map { it.key }.toSet()
) {
    val activeGridTiles: List<LuxTileId>
        get() {
            val ordered = orderedTileKeys.mapNotNull { LuxTileId.fromKey(it) }
            val missing = LuxTileId.entries.filter { it !in ordered }
            return (ordered + missing).filter { it.key in enabledTileKeys }
        }

    val allOrderedTiles: List<LuxTileId>
        get() {
            val ordered = orderedTileKeys.mapNotNull { LuxTileId.fromKey(it) }
            val missing = LuxTileId.entries.filter { it !in ordered }
            return ordered + missing
        }
}
