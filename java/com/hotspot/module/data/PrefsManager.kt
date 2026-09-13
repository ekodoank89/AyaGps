package com.hotspot.module.data

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "hotspot_settings"
    private const val KEY_IS_ACTIVE = "is_active"
    private const val KEY_LAT = "spoof_lat"
    private const val KEY_LNG = "spoof_lng"

    // Digunakan oleh UI Aplikasi untuk menulis data
    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveData(context: Context, active: Boolean, lat: Double, lng: Double) {
        getPreferences(context).edit().apply {
            putBoolean(KEY_IS_ACTIVE, active)
            putFloat(KEY_LAT, lat.toFloat())
            putFloat(KEY_LNG, lng.toFloat())
            apply()
        }
    }
}
