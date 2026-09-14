package com.aya.module.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Provider config yang dibaca oleh hook di proses aplikasi target
 * (pola yang sama dengan ConfigProvider milik modul AYA).
 * Membaca langsung SharedPreferences "aya_map_state" milik aplikasi ini.
 */
class AyaConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_STATE) return null
        val cat = arg ?: return null
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return Bundle().apply {
            putBoolean("active", prefs.getBoolean("active_$cat", false))
            putDouble("lat", prefs.getString("lat_$cat", null)?.toDoubleOrNull() ?: Double.NaN)
            putDouble("lng", prefs.getString("lng_$cat", null)?.toDoubleOrNull() ?: Double.NaN)
            putFloat("jit_step", prefs.getFloat("jitter_step_$cat", 3f))
            putInt("jit_win", prefs.getInt("jitter_interval_$cat", 5))
            putFloat("jit_radius", prefs.getFloat("jitter_radius_$cat", 4f))
            putDouble(
                "base_lat",
                prefs.getString("jitter_base_lat_$cat", null)?.toDoubleOrNull() ?: Double.NaN
            )
            putDouble(
                "base_lng",
                prefs.getString("jitter_base_lng_$cat", null)?.toDoubleOrNull() ?: Double.NaN
            )
        }
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(
        uri: Uri, selection: String?, selectionArgs: Array<String>?
    ): Int = 0

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.aya.module.config"
        const val METHOD_STATE = "getState"
        const val PREFS_NAME = "aya_map_state"
    }
}
