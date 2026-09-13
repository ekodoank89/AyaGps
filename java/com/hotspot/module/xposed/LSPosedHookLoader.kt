package com.hotspot.module.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class LSPosedHookLoader : IXposedHookLoadPackage {

    private val driverPackages = setOf(
        "com.gojek.partner",
        "com.grabtaxi.driver2"
    )

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (!driverPackages.contains(lpparam.packageName)) return

        // Eksekusi manipulasi lokasi pada framework internal aplikasi target
        LocationFrameworkHook.applyHook(lpparam)
    }
}
