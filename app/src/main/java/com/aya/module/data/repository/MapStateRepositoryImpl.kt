package com.aya.module.data.repository

import android.content.Context
import com.aya.module.domain.model.LocationData
import com.aya.module.domain.model.PanelOffset
import com.aya.module.domain.model.SavedCameraState
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
            prefs.edit()
                .putBoolean(KEY_ACTIVE_A, state.isActiveA)
                .putString(KEY_LAT_A, state.pointA?.latitude?.toString())
                .putString(KEY_LNG_A, state.pointA?.longitude?.toString())
                .putBoolean(KEY_ACTIVE_B, state.isActiveB)
                .putString(KEY_LAT_B, state.pointB?.latitude?.toString())
                .putString(KEY_LNG_B, state.pointB?.longitude?.toString())
                .commit()
        }
    }

    override suspend fun load(): SavedPointsState = withContext(Dispatchers.IO) {
        SavedPointsState(
            isActiveA = prefs.getBoolean(KEY_ACTIVE_A, false),
            pointA = readPoint(KEY_LAT_A, KEY_LNG_A),
            isActiveB = prefs.getBoolean(KEY_ACTIVE_B, false),
            pointB = readPoint(KEY_LAT_B, KEY_LNG_B)
        )
    }

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
    }
}
