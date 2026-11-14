package com.itriguero.homeclock;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Process;
import android.provider.Settings;

/**
 * Medidas del proceso actual tomadas del sistema Android.
 *
 * Todo es best-effort: si una lectura falla se devuelve 0 y el diagnóstico
 * sigue funcionando. No hay dependencias externas.
 */
final class ProcessMetrics {

    private ProcessMetrics() {
    }

    static int pid() {
        return Process.myPid();
    }

    /** Arranque del proceso en tiempo de {@code elapsedRealtime}, API 24. */
    static long startElapsedRealtime() {
        return Process.getStartElapsedRealtime();
    }

    /**
     * Número de arranques del dispositivo ({@code Settings.Global.BOOT_COUNT})
     * o -1 si no se puede leer.
     */
    static int bootCount(Context context) {
        if (context == null) return -1;
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.BOOT_COUNT);
        } catch (Exception e) {
            return -1;
        }
    }

    /** PSS y desglose de memoria del proceso actual. */
    static MemorySample sampleMemory() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        try {
            Debug.getMemoryInfo(info);
        } catch (RuntimeException e) {
            return new MemorySample(0, 0, 0, 0, 0, 0, 0);
        }

        return new MemorySample(
                Math.max(0, info.getTotalPss()),
                memoryStat(info, "summary.java-heap"),
                memoryStat(info, "summary.native-heap"),
                memoryStat(info, "summary.code"),
                memoryStat(info, "summary.graphics"),
                Math.max(0, info.getTotalPrivateDirty()),
                importance());
    }

    private static int memoryStat(Debug.MemoryInfo info, String key) {
        try {
            String value = info.getMemoryStat(key);
            if (value == null) return 0;
            int parsed = Integer.parseInt(value.trim());
            return Math.max(0, parsed);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int importance() {
        try {
            ActivityManager.RunningAppProcessInfo info =
                    new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(info);
            return Math.max(0, info.importance);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Muestra puntual de memoria; sin fecha para que la ponga el store. */
    static final class MemorySample {

        final int totalPssKb;
        final int javaKb;
        final int nativeKb;
        final int codeKb;
        final int graphicsKb;
        final int privateDirtyKb;
        final int importance;

        MemorySample(int totalPssKb, int javaKb, int nativeKb, int codeKb,
                     int graphicsKb, int privateDirtyKb, int importance) {
            this.totalPssKb = totalPssKb;
            this.javaKb = javaKb;
            this.nativeKb = nativeKb;
            this.codeKb = codeKb;
            this.graphicsKb = graphicsKb;
            this.privateDirtyKb = privateDirtyKb;
            this.importance = importance;
        }
    }
}
