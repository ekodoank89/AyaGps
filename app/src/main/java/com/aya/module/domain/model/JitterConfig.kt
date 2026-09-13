package com.aya.module.domain.model

import kotlin.math.cos

/** Setelan gerak acak (jitter) untuk satu titik */
data class JitterConfig(
    val stepMeters: Float = 3f,      // jarak maksimum per langkah
    val intervalSeconds: Int = 5,    // jendela waktu antar langkah
    val radiusMeters: Float = 4f     // batas radius dari titik asal
) {
    companion object {
        val DEFAULT = JitterConfig()
    }
}

/** Kondisi jitter A/B yang dipersistenkan ke disk */
data class SavedJitterState(
    val configA: JitterConfig = JitterConfig(),
    val configB: JitterConfig = JitterConfig(),
    val isActiveA: Boolean = false,
    val baseA: LocationData? = null,
    val isActiveB: Boolean = false,
    val baseB: LocationData? = null
)

/** Geser titik sejauh (dx, dy) meter dari posisinya */
fun LocationData.offsetByMeters(dxMeters: Double, dyMeters: Double): LocationData {
    val dLat = dyMeters / 111_320.0
    val dLng = dxMeters / (111_320.0 * cos(Math.toRadians(latitude)))
    return copy(latitude = latitude + dLat, longitude = longitude + dLng)
}
