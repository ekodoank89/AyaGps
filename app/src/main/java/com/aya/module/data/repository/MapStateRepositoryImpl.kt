package com.aya.module.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.aya.module.core.ConfigPusher
import com.aya.module.domain.model.FavoritePoint
import com.aya.module.domain.model.JitterConfig
import com.aya.module.domain.model.LocationData
import com.aya.module.domain.model.PanelOffset
import com.aya.module.domain.model.SavedCameraState
import com.aya.module.domain.model.SavedJitterState
import com.aya.module.domain.model.SavedPanelLocks
import com.aya.module.domain.model.SavedPanelOffsets
import com.aya.module.domain.model.SavedPointsState
import com.aya.module.domain.repository.MapStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MapStateRepositoryImpl(context: Context) : MapStateRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun save(state: SavedPointsState) {
        // commit() (bukan apply()) + Dispatchers.IO agar benar-benar tersimpan ke disk
        // meskipun aplikasi langsung di-force stop setelah tombol ditekan
        withContext(Dispatchers.IO) {
            val editor = prefs.edit()
            editor.putBoolean(KEY_ACTIVE_A, state.isActiveA)
            editor.putString(KEY_LAT_A, state.pointA?.latitude?.toString())
            editor.putString(KEY_LNG_A, state.pointA?.longitude?.toString())
            editor.putBoolean(KEY_ACTIVE_B, state.isActiveB)
            editor.putString(KEY_LAT_B, state.pointB?.latitude?.toString())
            editor.putString(KEY_LNG_B, state.pointB?.longitude?.toString())
            putFavorites(editor, PREFIX_FAV_A, state.favoritesA)
            putFavorites(editor, PREFIX_FAV_B, state.favoritesB)
            editor.putBoolean(KEY_PIN_CHIP_VISIBLE, state.isPinChipVisible)
            editor.commit()
            ConfigPusher.pushAll(context)
        }
    }

    override suspend fun load(): SavedPointsState = withContext(Dispatchers.IO) {
        SavedPointsState(
            isActiveA = prefs.getBoolean(KEY_ACTIVE_A, false),
            pointA = readPoint(KEY_LAT_A, KEY_LNG_A),
            isActiveB = prefs.getBoolean(KEY_ACTIVE_B, false),
            pointB = readPoint(KEY_LAT_B, KEY_LNG_B),
            favoritesA = readFavorites(PREFIX_FAV_A),
            favoritesB = readFavorites(PREFIX_FAV_B),
            isPinChipVisible = prefs.getBoolean(KEY_PIN_CHIP_VISIBLE, true)
        )
    }

    // ===== Serialisasi daftar favorit (index keys) =====

    private fun putFavorites(
        editor: SharedPreferences.Editor,
        prefix: String,
        list: List<FavoritePoint>
    ) {
        val oldCount = prefs.getInt("${prefix}_count", 0)
        for (i in 0 until oldCount) editor.remove("${prefix}_$i")
        editor.putInt("${prefix}_count", list.size)
        list.forEachIndexed { i, fav ->
            editor.putString("${prefix}_${i}_name", fav.name)
            editor.putString("${prefix}_${i}_lat", fav.location.latitude.toString())
            editor.putString("${prefix}_${i}_lng", fav.location.longitude.toString())
        }
    }

    private fun readFavorites(prefix: String): List<FavoritePoint> {
        val count = prefs.getInt("${prefix}_count", 0)
        return (0 until count).mapNotNull { i ->
            val name = prefs.getString("${prefix}_${i}_name", null) ?: return@mapNotNull null
            val loc = readPoint("${prefix}_${i}_lat", "${prefix}_${i}_lng")
                ?: return@mapNotNull null
            FavoritePoint(name = name, location = loc)
        }
    }

    // ===== Lock panel =====

    override suspend fun savePanelLocks(state: SavedPanelLocks) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putBoolean(KEY_TRACK_LOCKED, state.trackLocked)
                .putBoolean(KEY_ZOOM_LOCKED, state.zoomLocked)
                .commit()
        }
    }

    override suspend fun loadPanelLocks(): SavedPanelLocks = withContext(Dispatchers.IO) {
        SavedPanelLocks(
            trackLocked = prefs.getBoolean(KEY_TRACK_LOCKED, false),
            zoomLocked = prefs.getBoolean(KEY_ZOOM_LOCKED, false)
        )
    }

    // ===== Posisi drag panel =====

    override suspend fun savePanelOffsets(state: SavedPanelOffsets) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putFloat(KEY_TRACK_OFF_X, state.track?.x ?: 0f)
                .putFloat(KEY_TRACK_OFF_Y, state.track?.y ?: 0f)
                .putFloat(KEY_ZOOM_OFF_X, state.zoom?.x ?: 0f)
                .putFloat(KEY_ZOOM_OFF_Y, state.zoom?.y ?: 0f)
                .putBoolean(KEY_HAS_PANEL_OFFSETS, state.track != null || state.zoom != null)
                .commit()
        }
    }

    override suspend fun loadPanelOffsets(): SavedPanelOffsets = withContext(Dispatchers.IO) {
        if (!prefs.getBoolean(KEY_HAS_PANEL_OFFSETS, false)) {
            SavedPanelOffsets()
        } else {
            SavedPanelOffsets(
                track = readOffset(KEY_TRACK_OFF_X, KEY_TRACK_OFF_Y),
                zoom = readOffset(KEY_ZOOM_OFF_X, KEY_ZOOM_OFF_Y)
            )
        }
    }

    // ===== Posisi kamera/pin =====

    override suspend fun saveCameraState(state: SavedCameraState) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_CAM_LAT, state.latitude.toString())
                .putString(KEY_CAM_LNG, state.longitude.toString())
                .putFloat(KEY_CAM_ZOOM, state.zoom)
                .commit()
        }
    }

    override suspend fun loadCameraState(): SavedCameraState? = withContext(Dispatchers.IO) {
        val lat = prefs.getString(KEY_CAM_LAT, null)?.toDoubleOrNull()
            ?: return@withContext null
        val lng = prefs.getString(KEY_CAM_LNG, null)?.toDoubleOrNull()
            ?: return@withContext null
        SavedCameraState(
            latitude = lat,
            longitude = lng,
            zoom = prefs.getFloat(KEY_CAM_ZOOM, 16f)
        )
    }

    // ===== Jitter =====

    override suspend fun saveJitter(state: SavedJitterState) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putFloat(KEY_JITTER_STEP_A, state.configA.stepMeters)
                .putInt(KEY_JITTER_INTERVAL_A, state.configA.intervalSeconds)
                .putFloat(KEY_JITTER_RADIUS_A, state.configA.radiusMeters)
                .putFloat(KEY_JITTER_STEP_B, state.configB.stepMeters)
                .putInt(KEY_JITTER_INTERVAL_B, state.configB.intervalSeconds)
                .putFloat(KEY_JITTER_RADIUS_B, state.configB.radiusMeters)
                .putBoolean(KEY_JITTER_ACTIVE_A, state.isActiveA)
                .putString(KEY_JITTER_BASE_LAT_A, state.baseA?.latitude?.toString())
                .putString(KEY_JITTER_BASE_LNG_A, state.baseA?.longitude?.toString())
                .putBoolean(KEY_JITTER_ACTIVE_B, state.isActiveB)
                .putString(KEY_JITTER_BASE_LAT_B, state.baseB?.latitude?.toString())
                .putString(KEY_JITTER_BASE_LNG_B, state.baseB?.longitude?.toString())
                .commit()
                ConfigPusher.pushAll(context)
        }
    }

    override suspend fun loadJitter(): SavedJitterState = withContext(Dispatchers.IO) {
        SavedJitterState(
            configA = JitterConfig(
                stepMeters = prefs.getFloat(KEY_JITTER_STEP_A, 3f),
                intervalSeconds = prefs.getInt(KEY_JITTER_INTERVAL_A, 5),
                radiusMeters = prefs.getFloat(KEY_JITTER_RADIUS_A, 4f)
            ),
            configB = JitterConfig(
                stepMeters = prefs.getFloat(KEY_JITTER_STEP_B, 3f),
                intervalSeconds = prefs.getInt(KEY_JITTER_INTERVAL_B, 5),
                radiusMeters = prefs.getFloat(KEY_JITTER_RADIUS_B, 4f)
            ),
            isActiveA = prefs.getBoolean(KEY_JITTER_ACTIVE_A, false),
            baseA = readPoint(KEY_JITTER_BASE_LAT_A, KEY_JITTER_BASE_LNG_A),
            isActiveB = prefs.getBoolean(KEY_JITTER_ACTIVE_B, false),
            baseB = readPoint(KEY_JITTER_BASE_LAT_B, KEY_JITTER_BASE_LNG_B)
        )
    }

    // ===== Helper =====

    private fun readPoint(latKey: String, lngKey: String): LocationData? {
        val lat = prefs.getString(latKey, null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString(lngKey, null)?.toDoubleOrNull() ?: return null
        return LocationData(latitude = lat, longitude = lng)
    }

    private fun readOffset(xKey: String, yKey: String): PanelOffset = PanelOffset(
        x = prefs.getFloat(xKey, 0f),
        y = prefs.getFloat(yKey, 0f)
    )

    private companion object {
        const val PREFS_NAME = "aya_map_state"
        const val KEY_ACTIVE_A = "active_a"
        const val KEY_LAT_A = "lat_a"
        const val KEY_LNG_A = "lng_a"
        const val KEY_ACTIVE_B = "active_b"
        const val KEY_LAT_B = "lat_b"
        const val KEY_LNG_B = "lng_b"
        const val PREFIX_FAV_A = "fav_a"
        const val PREFIX_FAV_B = "fav_b"
        const val KEY_PIN_CHIP_VISIBLE = "pin_chip_visible"
        const val KEY_TRACK_LOCKED = "track_panel_locked"
        const val KEY_ZOOM_LOCKED = "zoom_panel_locked"
        const val KEY_TRACK_OFF_X = "track_off_x"
        const val KEY_TRACK_OFF_Y = "track_off_y"
        const val KEY_ZOOM_OFF_X = "zoom_off_x"
        const val KEY_ZOOM_OFF_Y = "zoom_off_y"
        const val KEY_HAS_PANEL_OFFSETS = "has_panel_offsets"
        const val KEY_CAM_LAT = "cam_lat"
        const val KEY_CAM_LNG = "cam_lng"
        const val KEY_CAM_ZOOM = "cam_zoom"
        const val KEY_JITTER_STEP_A = "jitter_step_a"
        const val KEY_JITTER_INTERVAL_A = "jitter_interval_a"
        const val KEY_JITTER_RADIUS_A = "jitter_radius_a"
        const val KEY_JITTER_STEP_B = "jitter_step_b"
        const val KEY_JITTER_INTERVAL_B = "jitter_interval_b"
        const val KEY_JITTER_RADIUS_B = "jitter_radius_b"
        const val KEY_JITTER_ACTIVE_A = "jitter_active_a"
        const val KEY_JITTER_BASE_LAT_A = "jitter_base_lat_a"
        const val KEY_JITTER_BASE_LNG_A = "jitter_base_lng_a"
        const val KEY_JITTER_ACTIVE_B = "jitter_active_b"
        const val KEY_JITTER_BASE_LAT_B = "jitter_base_lat_b"
        const val KEY_JITTER_BASE_LNG_B = "jitter_base_lng_b"
    }
}
