package com.aya.module.xposed

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModulePackageInfo
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Entry point Xposed (Modern API).
 *
 * Kelas ini TIDAK berjalan di aplikasi AyaGps sendiri — dia di-load oleh
 * framework LSPosed ke dalam proses aplikasi target yang dipilih di scope.
 */
class AyaGpsModule(
    base: XposedInterface,
    info: ModulePackageInfo
) : XposedModule(base, info) {

    init {
        log("AyaGps: module loaded (Modern API)")
    }

    /** Dipanggil saat proses aplikasi target selesai load package-nya */
    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        log("AyaGps: injected into ${param.packageName}")
    }
}
