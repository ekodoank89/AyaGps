package com.aya.module.xposed

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Entry point Xposed (Modern API).
 * TIDAK berjalan di aplikasi AyaGps — di-load LSPosed ke proses aplikasi target.
 */
class AyaGpsModule(
    base: XposedInterface,
    param: XposedModuleInterface.ModuleLoadedParam
) : XposedModule(base, param) {

    init {
        log("AyaGps: module loaded (Modern API)")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        log("AyaGps: injected into ${param.packageName}")
    }
}
