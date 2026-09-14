package com.aya.module

import android.content.Context
import android.database.Cursor
import android.location.Location
import android.net.Uri
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val PROVIDER_URI = "content://com.aya.module.config"
        private val TARGET_PACKAGES = setOf("com.grabtaxi.driver2", "com.gojek.partner")
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName !in TARGET_PACKAGES) return

        XposedBridge.log("AyaModule: Hooking ke paket ${lpparam.packageName}")

        // Hook fungsi getLatitude
        XposedHelpers.findAndHookMethod(
            "android.location.Location",
            lpparam.classLoader,
            "getLatitude",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHookParam) {
                    val config = fetchConfig()
                    if (config != null && config.active) {
                        param.result = config.latitude
                    }
                }
            }
        )

        // Hook fungsi getLongitude
        XposedHelpers.findAndHookMethod(
            "android.location.Location",
            lpparam.classLoader,
            "getLongitude",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHookParam) {
                    val config = fetchConfig()
                    if (config != null && config.active) {
                        param.result = config.longitude
                    }
                }
            }
        )
    }

    private fun fetchConfig(): ConfigData? {
        return try {
            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null)
            val currentApplication = XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication")
            val context = XposedHelpers.callMethod(currentApplication, "getApplicationContext") as? Context ?: return null

            val cursor: Cursor? = context.contentResolver.query(
                Uri.parse(PROVIDER_URI), null, null, null, null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val active = it.getInt(it.getColumnIndexOrThrow("active")) == 1
                    val lat = it.getFloat(it.getColumnIndexOrThrow("latitude")).toDouble()
                    val lng = it.getFloat(it.getColumnIndexOrThrow("longitude")).toDouble()
                    ConfigData(active, lat, lng)
                } else null
            }
        } catch (e: Throwable) {
            XposedBridge.log("AyaModule Error: Gagal membaca ContentProvider (${e.message})")
            null
        }
    }

    data class ConfigData(val active: Boolean, val latitude: Double, val longitude: Double)
}
