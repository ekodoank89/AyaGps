package com.hotspot.module.xposed

import android.location.Location
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

object LocationFrameworkHook {

    // Membaca konfigurasi aplikasi secara lintas-proses (IPC)
    private val pref = XSharedPreferences("com.hotspot.module", "hotspot_settings")

    fun applyHook(lpparam: LoadPackageParam) {
        
        val hookLocationLogic = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                pref.makeWorldReadable()
                pref.reload()

                val isActive = pref.getBoolean("is_active", false)
                if (!isActive) return // Jika fitur mati di UI, gunakan GPS asli

                val lat = pref.getFloat("spoof_lat", -6.1925f).toDouble()
                val lng = pref.getFloat("spoof_lng", 106.8227f).toDouble()

                val methodName = param.method.name
                when (methodName) {
                    "isFromMockProvider" -> param.result = false // Sembunyikan status mock
                    "getLatitude" -> param.result = lat
                    "getLongitude" -> param.result = lng
                }
            }
        }

        // 1. Hook Framework Android Location
        XposedHelpers.findAndHookMethod(Location::class.java, "isFromMockProvider", hookLocationLogic)
        XposedHelpers.findAndHookMethod(Location::class.java, "getLatitude", hookLocationLogic)
        XposedHelpers.findAndHookMethod(Location::class.java, "getLongitude", hookLocationLogic)

        // 2. Hook Fused Location Provider (Google Play Services) untuk akurasi tinggi target
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.LocationResult",
                lpparam.classLoader,
                "getLastLocation",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        pref.reload()
                        if (!pref.getBoolean("is_active", false)) return

                        val targetLocation = param.result as? Location ?: return
                        targetLocation.latitude = pref.getFloat("spoof_lat", -6.1925f).toDouble()
                        targetLocation.longitude = pref.getFloat("spoof_lng", 106.8227f).toDouble()
                        param.result = targetLocation
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("HOTSPOT: Fused Location provider hook skipped or not found.")
        }
    }
}
