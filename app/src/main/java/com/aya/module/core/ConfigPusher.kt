package com.aya.module.core

import android.content.Context
import android.content.Intent

/**
 * Mengirim state A/B ke aplikasi target via broadcast eksplisit.
 * Receiver terdaftar di proses target oleh hook (RECEIVER_EXPORTED).
 */
object ConfigPusher {
    const val ACTION = "com.aya.module.PUSH"

    private val TARGETS = listOf(
        "com.gojek.partner",
        "com.grabtaxi.driver2"
    )

    fun pushAll(context: Context) {
        val prefs = context.getSharedPreferences("aya_map_state", Context.MODE_PRIVATE)
        for (cat in listOf("a", "b")) {
            val lat = prefs.getString("lat_$cat", null)?.toDoubleOrNull() ?: Double.NaN
            val lng = prefs.getString("lng_$cat", null)?.toDoubleOrNull() ?: Double.NaN
            val blat = prefs.getString("jitter_base_lat_$cat", null)
                ?.toDoubleOrNull()?.takeUnless { it.isNaN() } ?: lat
            val blng = prefs.getString("jitter_base_lng_$cat", null)
                ?.toDoubleOrNull()?.takeUnless { it.isNaN() } ?: lng

            for (target in TARGETS) {
                val i = Intent(ACTION).setPackage(target)
                i.putExtra("cat", cat)
                i.putExtra("active", prefs.getBoolean("active_$cat", false))
                i.putExtra("lat", lat)
                i.putExtra("lng", lng)
                i.putExtra("step", prefs.getFloat("jitter_step_$cat", 3f))
                i.putExtra("win", prefs.getInt("jitter_interval_$cat", 5))
                i.putExtra("radius", prefs.getFloat("jitter_radius_$cat", 4f))
                i.putExtra("blat", blat)
                i.putExtra("blng", blng)
                try {
                    context.sendBroadcast(i)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
