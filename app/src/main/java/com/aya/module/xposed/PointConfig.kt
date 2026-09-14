package com.aya.module.xposed

import android.location.Location
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import java.util.Random
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Sumber koordinat untuk SATU kategori titik ("a" = Gojek, "b" = Grab).
 * Membaca prefs "aya_map_state" milik aplikasi AYA HOTSPOT (com.aya.module).
 */
class PointConfig(private val cat: String) {

    private val xsp by lazy { XSharedPreferences(MODULE_PACKAGE, PREFS_NAME) }

    private var lastReload = 0L
    private var active = false
    private var baseLat = Double.NaN
    private var baseLng = Double.NaN

    private var jStep = 3f
    private var jWin = 5
    private var jRadius = 4f
    private val jitter = Jitter()

    private var loggedActive = false

    fun latitude(): Double? = jittered()?.first
    fun longitude(): Double? = jittered()?.second

    private fun jittered(): Pair<Double, Double>? {
        refresh(System.currentTimeMillis())
        if (!active || baseLat.isNaN() || baseLng.isNaN()) return null
        return jitter.applyTo(baseLat, baseLng)
    }

    private fun refresh(now: Long) {
        if (now - lastReload < RELOAD_INTERVAL_MS) return
        lastReload = now

        val sp = try {
            xsp.reload()
            xsp
        } catch (t: Throwable) {
            XposedBridge.log("AYAGPS: XSharedPreferences gagal: $t")
            return
        }

        // Debug isi prefs — untuk verifikasi pembacaan dari proses target
        val a = sp.getBoolean("active_a", false)
        val b = sp.getBoolean("active_b", false)
        XposedBridge.log("AYAGPS: debug prefs [$cat] → active_a=$a active_b=$b")

        val lat = sp.getString("lat_$cat", null)?.toDoubleOrNull() ?: Double.NaN
        val lng = sp.getString("lng_$cat", null)?.toDoubleOrNull() ?: Double.NaN

        // Config jitter kategori ini (disimpan app sebagai float/int)
        jStep = sp.getFloat("jitter_step_$cat", 3f)
        jWin = sp.getInt("jitter_interval_$cat", 5)
        jRadius = sp.getFloat("jitter_radius_$cat", 4f)

        var bl = sp.getString("jitter_base_lat_$cat", null)?.toDoubleOrNull() ?: Double.NaN
        var blng = sp.getString("jitter_base_lng_$cat", null)?.toDoubleOrNull() ?: Double.NaN
        if (bl.isNaN() || blng.isNaN()) {
            bl = lat
            blng = lng
        }

        val newActive = sp.getBoolean("active_$cat", false)
        val changed = newActive != active || bl != baseLat || blng != baseLng
        active = newActive
        baseLat = bl
        baseLng = blng
        if (changed) jitter.onBaseChanged(bl, blng)

        if (active != loggedActive) {
            loggedActive = active
            if (active) {
                XposedBridge.log(
                    "AYAGPS: spoof AKTIF (${cat.uppercase()}) → $bl, $blng | " +
                        "jitter: $jStep m / $jWin dtk / R$jRadius m"
                )
            } else {
                XposedBridge.log("AYAGPS: spoof dimatikan ($cat)")
            }
        }
    }

    /** Tulis fake langsung ke field — distanceTo, toString ikut konsisten */
    fun rewriteFields(loc: Location, source: String) {
        val la = latitude() ?: return
        val ln = longitude() ?: return
        try {
            loc.latitude = la
            loc.longitude = ln
        } catch (t: Throwable) {
            XposedBridge.log("AYAGPS: gagal menulis field via $source: $t")
        }
    }

    /** Random-walk GPS + clamp vektor (lingkaran), pola sama dengan modul AYA */
    private inner class Jitter {
        private val rnd = Random()
        private var oLat = 0.0
        private var oLng = 0.0
        private var windowStart = 0L
        private var baseRefLat = Double.NaN
        private var baseRefLng = Double.NaN

        fun onBaseChanged(la: Double, ln: Double) {
            if (la != baseRefLat || ln != baseRefLng) {
                oLat = 0.0
                oLng = 0.0
                baseRefLat = la
                baseRefLng = ln
                windowStart = 0L
            }
        }

        fun applyTo(baseLat: Double, baseLng: Double): Pair<Double, Double> {
            val now = System.currentTimeMillis()
            if (now - windowStart >= jWin * 1000L) {
                windowStart = now
                val mLat = 111320.0
                val mLng = 111320.0 * cos(Math.toRadians(baseLat))
                oLat += ((rnd.nextDouble() - 0.5) * jStep) / mLat
                oLng += ((rnd.nextDouble() - 0.5) * jStep) / mLng

                val dLatM = oLat * mLat
                val dLngM = oLng * mLng
                val dist = sqrt(dLatM * dLatM + dLngM * dLngM)
                if (dist > jRadius) {
                    val scale = jRadius / dist
                    oLat = (dLatM * scale) / mLat
                    oLng = (dLngM * scale) / mLng
                }
            }
            return (baseLat + oLat) to (baseLng + oLng)
        }
    }

    companion object {
        private const val MODULE_PACKAGE = "com.aya.module"
        private const val PREFS_NAME = "aya_map_state"
        private const val RELOAD_INTERVAL_MS = 1000L
    }
}
