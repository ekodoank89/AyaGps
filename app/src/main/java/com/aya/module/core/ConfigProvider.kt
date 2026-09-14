package com.aya.module.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class ConfigProvider : ContentProvider() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(): Boolean {
        prefs = context!!.getSharedPreferences("aya_gps_prefs", Context.MODE_PRIVATE)
        return true
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        // Buat cursor virtual di memori untuk mengirimkan data ke aplikasi driver
        val cursor = MatrixCursor(arrayOf("active", "lat", "lng"))
        
        val isActive = prefs.getBoolean("active", false)
        val lat = prefs.getFloat("lat", 0.0f)
        val lng = prefs.getFloat("lng", 0.0f)
        
        cursor.addRow(arrayOf(if (isActive) 1 else 0, lat, lng))
        return cursor
    }

    // Fungsi insert, delete, update bisa dikosongkan atau disesuaikan jika modul butuh mengubah data dari sisi driver
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
