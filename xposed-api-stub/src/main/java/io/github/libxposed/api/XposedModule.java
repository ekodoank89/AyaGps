package io.github.libxposed.api;

/**
 * STUB kompilasi dari kelas dasar modul libxposed Modern API.
 * Signature mengikuti API asli; isi metode tidak pernah dieksekusi di sini.
 */
public abstract class XposedModule implements XposedModuleInterface {

    private final XposedInterface base;

    protected XposedModule(XposedInterface base, XposedModuleInterface.ModuleLoadedParam param) {
        this.base = base;
    }

    public final void log(String text) {
        base.log(text);
    }

    public final void log(String prefix, Throwable throwable) {
        base.log(prefix, throwable);
    }

    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
    }

    public void onSystemServerLoaded(XposedModuleInterface.SystemServerLoadedParam param) {
    }
}
