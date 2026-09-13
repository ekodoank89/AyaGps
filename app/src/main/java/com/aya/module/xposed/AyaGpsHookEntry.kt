package com.aya.module.xposed

import android.location.Location
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Random
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Entry point Xposed (Legacy API).
 * TIDAK berjalan di aplikasi AyaGps — di-load LSPosed ke proses aplikasi target.
 *
 * Membaca state titik A/B + setelan jitter dari prefs "aya_map_state"
 * milik aplikasi AyaGps (com.aya.module), lalu menyuntikkan koordinat ke
 * seluruh jalur lokasi di aplikasi target.
 */
class AyaGpsHookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == MODULE_PACKAGE) return

        XposedBridge.log("AYAGPS: hook terpasang di '${lpparam.packageName}'")

        val config = PointConfig()

        hookLocationGetters(lpparam.classLoader, config)
        hookLastKnownLocation(lpparam.classLoader, config)
        hookGmsLocationResult(lpparam.classLoader, config)
    }

    // ===== Getter: mengganti hasil pembacaan koordinat =====
    private fun hookLocationGetters(classLoader: ClassLoader, config: PointConfig) {
        val loggedFirst = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            XposedHelpers.findAndHookMethod("android.location.Location", classLoader, "getLatitude",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        config.latitude()?.let {
                            if (loggedFirst.compareAndSet(false, true)) {
                                XposedBridge.log("AYAGPS: getLatitude terpanggil — kirim koordinat fake")
                            }
                            param.result = it
                        }
                    }
                })
            XposedHelpers.findAndHookMethod("android.location.Location", classLoader, "getLongitude",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        config.longitude()?.let { param.result = it }
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("AYAGPS: getter Location gagal di-hook: $t")
        }
    }

    // ===== getLastKnownLocation: tulis fake ke FIELD (konsistensi penuh) =====
    private fun hookLastKnownLocation(classLoader: ClassLoader, config: PointConfig) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager", classLoader,
                "getLastKnownLocation", String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.result as? Location)?.let { config.rewriteFields(it, "getLastKnownLocation") }
                    }
                })
        } catch (t: Throwable) {
            XposedBridge.log("AYAGPS: getLastKnownLocation gagal di-hook: $t")
        }
    }

    // ===== GMS LocationResult: jalur FusedLocation app modern =====
    private fun hookGmsLocationResult(classLoader: ClassLoader, config: PointConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(GMS_LOCATION_RESULT, classLoader)
            if (cls == null) {
                XposedBridge.log("AYAGPS: GMS LocationResult tidak ditemukan (app tidak pakai GMS location?)")
                return
            }
            XposedBridge.hookAllMethods(cls, "getLastLocation", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    (param.result as? Location)?.let { config.rewriteFields(it, "GMS.getLastLocation") }
                }
            })
            XposedBridge.hookAllMethods(cls, "getLocations", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    @Suppress("UNCHECKED_CAST")
                    (param.result as? List<Location>)?.forEach { config.rewriteFields(it, "GMS.getLocations") }
                }
            })
            XposedBridge.log("AYAGPS: GMS LocationResult di-hook")
        } catch (t: Throwable) {
            XposedBridge.log("AYAGPS: GMS LocationResult gagal: $t")
        }
    }

    companion object {
        private const val MODULE_PACKAGE = "com.aya.module"
        private const val GMS_LOCATION_RESULT = "com.google.android.gms.location.LocationResult"
    }
}

/**
 * Satu sumber kebenaran koordinat: titik A dulu, jika tidak aktif pakai B.
 * Reload prefs maksimal 1x/detik (pola yang sama dengan modul AYA).
 */
private class PointConfig {

    private val xsp by lazy { XSharedPreferences(MODULE_PACKAGE, PREFS_NAME) }

    private var lastReload = 0L
    private var active = false
    private var baseLat = Double.NaN
    private var baseLng = Double.NaN

    // Jitter (random walk) — dihitung di proses target, config dari app
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
        XposedBridge.log("AYAGPS: debug baca prefs → a=${sp.getBoolean("active_a", false)} b=${sp.getBoolean("active_b", false)}")
            return
        }

        // Titik A dulu; kalau tidak aktif, pakai B
        val a = sp.getBoolean("active_a", false)
        val b = sp.getBoolean("active_b", false)
        val lat = sp.getString(if (a) "lat_a" else "lat_b", null)?.toDoubleOrNull() ?: Double.NaN
        val lng = sp.getString(if (a) "lng_a" else "lng_b", null)?.toDoubleOrNull() ?: Double.NaN
        val baseLatKey = if (a) "jitter_base_lat_a" else "jitter_base_lat_b"
        val baseLngKey = if (a) "jitter_base_lng_a" else "jitter_base_lng_b"
        val cat = if (a) "a" else "b"

        jStep = sp.getString("jitter_step_$cat", null)?.toFloatOrNull() ?: 3f
        jWin = sp.getString("jitter_interval_$cat", null)?.toIntOrNull() ?: 5
        jRadius = sp.getString("jitter_radius_$cat", null)?.toFloatOrNull() ?: 4f

        // Basis jitter: pakai yang tersimpan; fallback ke titik itu sendiri
        var bl = sp.getString(baseLatKey, null)?.toDoubleOrNull() ?: Double.NaN
        var blng = sp.getString(baseLngKey, null)?.toDoubleOrNull() ?: Double.NaN
        if (bl.isNaN() || blng.isNaN()) { bl = lat; blng = lng }

        val changed = (a || b) != active || bl != baseLat || blng != baseLng
        active = a || b
        if (!lat.isNaN()) baseLat = bl else if (changed) baseLat = Double.NaN
        if (!lng.isNaN()) baseLng = blng else if (changed) baseLng = Double.NaN
        if (changed) jitter.onBaseChanged(baseLat, baseLng)

        if (active != loggedActive) {
            loggedActive = active
            if (active) {
                XposedBridge.log(
                    "AYAGPS: spoof AKTIF (${if (a) "A" else "B"}) → $bl, $blng | " +
                            "jitter: $jStep m / $jWin dtk / R$jRadius m"
                )
            } else {
                XposedBridge.log("AYAGPS: spoof dimatikan")
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

    /** Random-walk GPS + clamp vektor (lingkaran), identik pola modul AYA */
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
