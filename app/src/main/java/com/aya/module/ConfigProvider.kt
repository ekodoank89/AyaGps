package com.aya.module

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
        context?.let {
            prefs = it.getSharedPreferences("location_config", Context.MODE_PRIVATE)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("active", "latitude", "longitude"))
        val active = prefs.getBoolean("active", false)
        val lat = prefs.getFloat("latitude", 0.0f)
        val lng = prefs.getFloat("longitude", 0.0f)

        cursor.addRow(arrayOf(if (active) 1 else 0, lat, lng))
        return cursor
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.dir/vnd.com.aya.module.config"

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        values?.let {
            val editor = prefs.edit()
            if (it.containsKey("active")) editor.putBoolean("active", it.getAsBoolean("active"))
            if (it.containsKey("latitude")) editor.putFloat("latitude", it.getAsFloat("latitude"))
            if (it.containsKey("longitude")) editor.putFloat("longitude", it.getAsFloat("longitude"))
            editor.apply()
        }
        return uri
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        insert(uri, values)
        return 1
    }
}
