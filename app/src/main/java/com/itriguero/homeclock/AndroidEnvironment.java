package com.itriguero.homeclock;

import java.util.List;

/**
 * Fuente de datos del sistema que necesita {@link DiagnosticsStore}.
 *
 * Permite probar el store en la JVM con un fake: producción usa
 * {@link AndroidProcessEnvironment} y los tests inyectan valores mutables.
 */
interface AndroidEnvironment {

    long now();

    int pid();

    long startElapsedRealtime();

    int bootCount();

    int appVersionCode();

    String appVersionName();

    int sdkInt();

    ProcessMetrics.MemorySample sampleMemory();

    ExitInfoReader.Scan scanExits(List<String> seenKeys, long now,
                                  int appVersionCode, long sessionDurationMs);
}
