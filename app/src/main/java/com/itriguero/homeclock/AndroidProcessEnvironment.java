package com.itriguero.homeclock;

import android.content.Context;
import android.os.Build;

import java.util.List;

/**
 * Implementación real de {@link AndroidEnvironment}: delega en
 * {@link ProcessMetrics}, {@link ExitInfoReader} y {@link BuildConfig}.
 */
final class AndroidProcessEnvironment implements AndroidEnvironment {

    private final Context context;

    AndroidProcessEnvironment(Context context) {
        Context app = context.getApplicationContext();
        this.context = app == null ? context : app;
    }

    @Override
    public long now() {
        return System.currentTimeMillis();
    }

    @Override
    public int pid() {
        return ProcessMetrics.pid();
    }

    @Override
    public long startElapsedRealtime() {
        return ProcessMetrics.startElapsedRealtime();
    }

    @Override
    public int bootCount() {
        return ProcessMetrics.bootCount(context);
    }

    @Override
    public int appVersionCode() {
        return BuildConfig.VERSION_CODE;
    }

    @Override
    public String appVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public int sdkInt() {
        return Build.VERSION.SDK_INT;
    }

    @Override
    public ProcessMetrics.MemorySample sampleMemory() {
        return ProcessMetrics.sampleMemory();
    }

    @Override
    public ExitInfoReader.Scan scanExits(List<String> seenKeys, long now,
                                         int appVersionCode,
                                         long sessionDurationMs) {
        return ExitInfoReader.readNew(context, seenKeys, now, appVersionCode,
                sessionDurationMs);
    }
}
