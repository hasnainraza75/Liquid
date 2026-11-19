package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.luxPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lux_control_preferences"
)

class LuxPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.luxPreferencesDataStore)

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BLUR_RADIUS_DP = floatPreferencesKey("blur_radius_dp")
        val GLASS_OPACITY = floatPreferencesKey("glass_opacity")
        val EDGE_TRIGGER_ENABLED = booleanPreferencesKey("edge_trigger_enabled")
        val EDGE_WIDTH_PERCENT = floatPreferencesKey("edge_width_percent")
        val EDGE_HEIGHT_DP = intPreferencesKey("edge_height_dp")
        val SHOW_EDGE_HINT = booleanPreferencesKey("show_edge_hint")
        val RECORD_MIC = booleanPreferencesKey("record_mic")
        val ORDERED_TILES = stringPreferencesKey("ordered_tiles_csv")
        val ENABLED_TILES = stringPreferencesKey("enabled_tiles_csv")
    }

    val preferencesFlow: Flow<LuxUserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val orderedCsv = prefs[Keys.ORDERED_TILES]
            val enabledCsv = prefs[Keys.ENABLED_TILES]
            val orderedList = if (orderedCsv.isNullOrBlank()) {
                LuxTileId.entries.map { it.key }
            } else {
                orderedCsv.split(",").filter { it.isNotBlank() }
            }
            val enabledSet = if (enabledCsv == null) {
                LuxTileId.defaultGridOrder.map { it.key }.toSet()
            } else {
                enabledCsv.split(",").filter { it.isNotBlank() }.toSet()
            }

            LuxUserPreferences(
                themeMode = LuxThemeMode.fromKey(prefs[Keys.THEME_MODE]),
                blurRadiusDp = (prefs[Keys.BLUR_RADIUS_DP] ?: 28f).coerceIn(0f, 48f),
                glassOpacity = (prefs[Keys.GLASS_OPACITY] ?: 0.24f).coerceIn(0.14f, 0.42f),
                edgeTriggerEnabled = prefs[Keys.EDGE_TRIGGER_ENABLED] ?: false,
                edgeTriggerWidthPercent = (prefs[Keys.EDGE_WIDTH_PERCENT] ?: 0.34f).coerceIn(0.20f, 0.50f),
                edgeTriggerHeightDp = (prefs[Keys.EDGE_HEIGHT_DP] ?: 44).coerceIn(28, 72),
                showEdgeIndicatorHint = prefs[Keys.SHOW_EDGE_HINT] ?: true,
                recordWithMicrophone = prefs[Keys.RECORD_MIC] ?: false,
                orderedTileKeys = orderedList,
                enabledTileKeys = enabledSet
            )
        }

    suspend fun setThemeMode(mode: LuxThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setBlurRadiusDp(radiusDp: Float) {
        dataStore.edit { it[Keys.BLUR_RADIUS_DP] = radiusDp.coerceIn(0f, 48f) }
    }

    suspend fun setGlassOpacity(opacity: Float) {
        dataStore.edit { it[Keys.GLASS_OPACITY] = opacity.coerceIn(0.14f, 0.42f) }
    }

    suspend fun setEdgeTriggerEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.EDGE_TRIGGER_ENABLED] = enabled }
    }

    suspend fun setEdgeTriggerGeometry(widthPercent: Float, heightDp: Int) {
        dataStore.edit {
            it[Keys.EDGE_WIDTH_PERCENT] = widthPercent.coerceIn(0.20f, 0.50f)
            it[Keys.EDGE_HEIGHT_DP] = heightDp.coerceIn(28, 72)
        }
    }

    suspend fun setShowEdgeIndicatorHint(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_EDGE_HINT] = show }
    }

    suspend fun setRecordWithMicrophone(enabled: Boolean) {
        dataStore.edit { it[Keys.RECORD_MIC] = enabled }
    }

    suspend fun setTileEnabled(tileId: LuxTileId, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = (prefs[Keys.ENABLED_TILES]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableSet())
                ?: LuxTileId.defaultGridOrder.map { it.key }.toMutableSet()
            if (enabled) current.add(tileId.key) else current.remove(tileId.key)
            prefs[Keys.ENABLED_TILES] = current.joinToString(",")
        }
    }

    suspend fun moveTile(tileId: LuxTileId, moveUp: Boolean) {
        dataStore.edit { prefs ->
            val current = (prefs[Keys.ORDERED_TILES]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableList())
                ?: LuxTileId.entries.map { it.key }.toMutableList()

            // Ensure all valid keys are present
            LuxTileId.entries.forEach { entry ->
                if (entry.key !in current) current.add(entry.key)
            }

            val index = current.indexOf(tileId.key)
            if (index == -1) return@edit
            val targetIndex = if (moveUp) index - 1 else index + 1
            if (targetIndex in current.indices) {
                val item = current.removeAt(index)
                current.add(targetIndex, item)
                prefs[Keys.ORDERED_TILES] = current.joinToString(",")
            }
        }
    }

    suspend fun resetTileDefaults() {
        dataStore.edit { prefs ->
            prefs[Keys.ORDERED_TILES] = LuxTileId.entries.map { it.key }.joinToString(",")
            prefs[Keys.ENABLED_TILES] = LuxTileId.defaultGridOrder.map { it.key }.joinToString(",")
        }
    }
}
