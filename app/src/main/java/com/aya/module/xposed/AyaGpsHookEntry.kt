package com.aya.module.xposed

import android.location.Location
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Entry point Xposed (Legacy API).
 * Mapping target:
 *   A → com.gojek.partner (Gojek Partner)
 *   B → com.grabtaxi.driver2 (Grab Driver)
 */
class AyaGpsHookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val category = CATEGORY_BY_TARGET[lpparam.packageName] ?: return

        XposedBridge.log("AYAGPS: hook terpasang di '${lpparam.packageName}' (kategori: $category)")

        val config = PointConfig(category)

        hookLocationGetters(lpparam.classLoader, config)
        hookLastKnownLocation(lpparam.classLoader, config)
        hookGmsLocationResult(lpparam.classLoader, config)
    }

    // ===== Getter: mengganti hasil pembacaan koordinat =====
    private fun hookLocationGetters(classLoader: ClassLoader, config: PointConfig) {
        val loggedFirst = AtomicBoolean(false)
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location", classLoader, "getLatitude",
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
            XposedHelpers.findAndHookMethod(
                "android.location.Location", classLoader, "getLongitude",
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
                        (param.result as? Location)
                            ?.let { config.rewriteFields(it, "getLastKnownLocation") }
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
                XposedBridge.log("AYAGPS: GMS LocationResult tidak ditemukan")
                return
            }
            XposedBridge.hookAllMethods(cls, "getLastLocation", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    (param.result as? Location)
                        ?.let { config.rewriteFields(it, "GMS.getLastLocation") }
                }
            })
            XposedBridge.hookAllMethods(cls, "getLocations", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    @Suppress("UNCHECKED_CAST")
                    (param.result as? List<Location>)
                        ?.forEach { config.rewriteFields(it, "GMS.getLocations") }
                }
            })
            XposedBridge.log("AYAGPS: GMS LocationResult di-hook")
        } catch (t: Throwable) {
            XposedBridge.log("AYAGPS: GMS LocationResult gagal: $t")
        }
    }

    companion object {
        private const val GMS_LOCATION_RESULT = "com.google.android.gms.location.LocationResult"

        /** Mapping package target → kategori titik (a/b) */
        private val CATEGORY_BY_TARGET = mapOf(
            "com.gojek.partner" to "a",
            "com.grabtaxi.driver2" to "b"
        )
    }
}
