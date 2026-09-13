package io.github.libxposed.api;

/**
 * STUB kompilasi — implementasi asli disediakan framework LSPosed saat runtime.
 * Stub TIDAK ikut ter-bundle ke APK (compileOnly).
 */
public interface XposedInterface {

    void log(String text);

    void log(String prefix, Throwable throwable);
}
