package io.github.libxposed.api;

/**
 * STUB kompilasi dari interface framework libxposed Modern API.
 */
public interface XposedModuleInterface {

    /** Parameter saat modul di-load ke sebuah proses */
    interface ModuleLoadedParam {
    }

    /** Parameter saat sebuah package selesai di-load di proses ini */
    interface PackageLoadedParam {
        String getPackageName();

        boolean isFirstPackage();

        ClassLoader getClassLoader();
    }

    /** Parameter saat system server selesai di-load */
    interface SystemServerLoadedParam {
        ClassLoader getClassLoader();
    }
}
