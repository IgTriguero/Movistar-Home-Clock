package com.itriguero.homeclock;

import android.app.Application;

/**
 * Punto de entrada del proceso: arranca la persistencia del diagnóstico y la
 * conecta al store en memoria antes de que se cree ninguna actividad.
 */
public final class HomeClockApp extends Application {

    private DiagnosticsStore diagnosticsStore;

    @Override
    public void onCreate() {
        super.onCreate();
        diagnosticsStore = DiagnosticsStore.get(this);
        diagnosticsStore.ensureStarted();
        Diagnostics.get().setListener(diagnosticsStore);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        DiagnosticsStore store = diagnosticsStore;
        if (store != null) {
            store.onTrimMemory(level, System.currentTimeMillis());
        }
    }
}
