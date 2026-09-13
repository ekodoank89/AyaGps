package com.aya.module.data.repository

import android.content.Context
import com.aya.module.domain.model.LocationData
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

    private fun readPoint(latKey: String, lngKey: String): LocationData? {
        val lat = prefs.getString(latKey, null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString(lngKey, null)?.toDoubleOrNull() ?: return null
        return LocationData(latitude = lat, longitude = lng)
    }

    private companion object {
        const val PREFS_NAME = "aya_map_state"
        const val KEY_ACTIVE_A = "active_a"
        const val KEY_LAT_A = "lat_a"
        const val KEY_LNG_A = "lng_a"
        const val KEY_ACTIVE_B = "active_b"
        const val KEY_LAT_B = "lat_b"
        const val KEY_LNG_B = "lng_b"
    }
}
