package com.aya.module.xposed

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.net.Uri
import android.os.Build
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import java.util.Random
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Sumber koordinat untuk SATU kategori ("a" = Gojek, "b" = Grab).
 * Prioritas: push broadcast → ContentProvider → XSharedPreferences.
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
    private var loggedRemoteFail = false

    private var pushActive: Boolean? = null
    private var receiverRegistered = false

    fun latitude(): Double? = jittered()?.first
    fun longitude(): Double? = jittered()?.second

    private fun jittered(): Pair<Double, Double>? {
        ensurePushReceiver()
        if (pushActive != null) return jitter.value(baseLat, baseLng)
        refresh(System.currentTimeMillis())
        if (!active || baseLat.isNaN() || baseLng.isNaN()) return null
        return jitter.value(baseLat, baseLng)
    }

    private fun refresh(now: Long) {
        if (now - lastReload < RELOAD_INTERVAL_MS) return
        lastReload = now
        if (readRemote()) return
        readXsp()
    }

    // ===== Jalur 1: push broadcast (real-time, prioritas tertinggi) =====
    private fun ensurePushReceiver() {
        if (receiverRegistered) return
        val app = currentApplication() ?: return
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    if (i?.getStringExtra("cat") != cat) return
                    val a = i.getBooleanExtra("active", false)
                    val la = i.getDoubleExtra("lat", Double.NaN)
                    val ln = i.getDoubleExtra("lng", Double.NaN)
                    val st = i.getFloatExtra("step", 3f)
                    val wn = i.getIntExtra("win", 5)
                    val rd = i.getFloatExtra("radius", 4f)
                    var bl = i.getDoubleExtra("blat", Double.NaN)
                    var bln = i.getDoubleExtra("blng", Double.NaN)
                    if (bl.isNaN()) bl = la
                    if (bln.isNaN()) bln = ln
                    applyJitter(st, wn, rd)
                    pushActive = a
                    XposedBridge.log("AYAGPS [$cat]: push diterima → active=$a, $bl, $bln")
                    applyState(a, bl, bln)
                }
            }
            val filter = IntentFilter(ACTION_PUSH)
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                app.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
            XposedBridge.log("AYAGPS [$cat]: push receiver terpasang")
        } catch (t: Throwable) {
            XposedBridge.log("AYAGPS [$cat]: gagal daftar push receiver: $t")
        }
    }

    // ===== Jalur 2: ContentProvider =====
    private fun readRemote(): Boolean {
        return try {
            val app = currentApplication() ?: return false
            val b = app.contentResolver.call(
                Uri.parse("content://$AUTHORITY"), "getState", cat, null
            ) ?: return false
            applyJitter(
                b.getFloat("jit_step", 3f),
                b.getInt("jit_win", 5),
                b.getFloat("jit_radius", 4f)
            )
            applyState(
                b.getBoolean("active", false),
                b.getDouble("base_lat", Double.NaN),
                b.getDouble("base_lng", Double.NaN)
            )
            true
        } catch (t: Throwable) {
            if (!loggedRemoteFail) {
                loggedRemoteFail = true
                XposedBridge.log("AYAGPS [$cat]: jalur remote gagal → fallback XSP: $t")
            }
            false
        }
    }

    // ===== Jalur 3: XSharedPreferences =====
    private fun readXsp(): Boolean {
        return try {
            xsp.reload()
            val a = xsp.getBoolean("active_$cat", false)
            val la = xsp.getString("jitter_base_lat_$cat", null)?.toDoubleOrNull()
                ?: xsp.getString("lat_$cat", null)?.toDoubleOrNull() ?: Double.NaN
            val ln = xsp.getString("jitter_base_lng_$cat", null)?.toDoubleOrNull()
                ?: xsp.getString("lng_$cat", null)?.toDoubleOrNull() ?: Double.NaN
            XposedBridge.log("AYAGPS [$cat]: XSP → active=$a lat=$la lng=$ln")
            applyJitter(
                xsp.getFloat("jitter_step_$cat", 3f),
                xsp.getInt("jitter_interval_$cat", 5),
                xsp.getFloat("jitter_radius_$cat", 4f)
            )
            applyState(a, la, ln)
            true
        } catch (t: Throwable) {
            XposedBridge.log("AYAGPS [$cat]: XSP fallback gagal: $t")
            false
        }
    }

    private fun applyJitter(step: Float, win: Int, radius: Float) {
        jStep = step
        jWin = win
        jRadius = radius
    }

    private fun applyState(a: Boolean, la: Double, ln: Double) {
        val changed = a != active || la != baseLat || ln != baseLng
        active = a
        if (!la.isNaN()) baseLat = la
        if (!ln.isNaN()) baseLng = ln
        if (changed) jitter.reset()
        if (a != loggedActive) {
            loggedActive = a
            if (a) {
                XposedBridge.log(
                    "AYAGPS: spoof AKTIF (${cat.uppercase()}) → $baseLat, $baseLng | " +
                        "jitter: $jStep m / $jWin dtk / R$jRadius m"
                )
            } else {
                XposedBridge.log("AYAGPS: spoof dimatikan ($cat)")
            }
        }
    }

    private fun currentApplication(): Application? = try {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Application
    } catch (t: Throwable) {
        null
    }

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

    /** Random-walk GPS + clamp vektor (lingkaran) */
    private inner class Jitter {
        private val rnd = Random()
        private var oLat = 0.0
        private var oLng = 0.0
        private var windowStart = 0L

        fun reset() {
            oLat = 0.0
            oLng = 0.0
            windowStart = 0L
        }

        fun value(baseLat: Double, baseLng: Double): Pair<Double, Double> {
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
        private const val AUTHORITY = "com.aya.module.config"
        private const val ACTION_PUSH = "com.aya.module.PUSH"
        private const val RELOAD_INTERVAL_MS = 1000L
    }
}
