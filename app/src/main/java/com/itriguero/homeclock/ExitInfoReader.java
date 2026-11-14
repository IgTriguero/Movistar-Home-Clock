package com.itriguero.homeclock;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lee las salidas recientes del proceso desde {@code ApplicationExitInfo}
 * (API 30) y las convierte en eventos del diagnóstico.
 *
 * Las claves ya vistas se pasan desde el store para no duplicar eventos entre
 * arranques. La consulta es best-effort: cualquier fallo se traduce en un
 * {@link Scan} vacío. No se lee el trace de las salidas.
 */
final class ExitInfoReader {

    static final int MAX_QUERY = 16;

    private static final int MAX_DESCRIPTION_CHARS =
            DiagnosticsState.MAX_DESCRIPTION_CHARS;

    private ExitInfoReader() {
    }

    static boolean isSupported(int sdkInt) {
        return sdkInt >= 30;
    }

    static Scan readNew(Context context, List<String> seenKeys, long now,
                        int appVersionCode, long sessionDurationMs) {
        if (context == null || !isSupported(Build.VERSION.SDK_INT)) {
            return Scan.empty();
        }
        try {
            return Api30.read(context, seenKeys, now, appVersionCode,
                    sessionDurationMs);
        } catch (RuntimeException e) {
            return Scan.empty();
        }
    }

    /** Clave estable de una salida, usada para deduplicar entre arranques. */
    static String key(long timestamp, int pid, int reason, int status) {
        return timestamp + ":" + pid + ":" + reason + ":" + status;
    }

    /** Acceso a la API 30 aislado para que el resto pueda cargarse antes. */
    @TargetApi(30)
    static final class Api30 {

        private Api30() {
        }

        static Scan read(Context context, List<String> seenKeys, long now,
                         int appVersionCode, long sessionDurationMs) {
            ActivityManager manager = (ActivityManager) context.getSystemService(
                    Context.ACTIVITY_SERVICE);
            if (manager == null) return Scan.empty();

            String packageName = context.getPackageName();
            List<ApplicationExitInfo> history = manager
                    .getHistoricalProcessExitReasons(packageName, 0, MAX_QUERY);

            Set<String> seen = new LinkedHashSet<>();
            if (seenKeys != null) seen.addAll(seenKeys);

            Set<String> keys = new LinkedHashSet<>();
            Set<String> emitted = new LinkedHashSet<>();
            List<DiagnosticsState.ExitEvent> events = new ArrayList<>();

            if (history != null) {
                for (ApplicationExitInfo info : history) {
                    if (info == null) continue;
                    if (!packageName.equals(info.getProcessName())) continue;

                    int pid = info.getPid();
                    int reason = info.getReason();
                    int status = info.getStatus();
                    String key = key(info.getTimestamp(), pid, reason, status);
                    keys.add(key);

                    if (seen.contains(key) || !emitted.add(key)) continue;

                    events.add(new DiagnosticsState.ExitEvent(
                            info.getTimestamp(),
                            now,
                            ExitReasonMapper.cause(reason, status),
                            reason,
                            status,
                            pid,
                            info.getImportance(),
                            truncate(info.getDescription()),
                            ExitReasonMapper.confidence(reason),
                            appVersionCode,
                            sessionDurationMs));
                }
            }

            return new Scan(events, new ArrayList<>(keys));
        }

        private static String truncate(String value) {
            if (value == null) return "";
            if (value.length() <= MAX_DESCRIPTION_CHARS) return value;
            return value.substring(0, MAX_DESCRIPTION_CHARS);
        }
    }

    /** Salidas nuevas junto a todas las claves observadas en esta pasada. */
    static final class Scan {

        final List<DiagnosticsState.ExitEvent> events;
        final List<String> keys;

        Scan(List<DiagnosticsState.ExitEvent> events, List<String> keys) {
            this.events = Collections.unmodifiableList(new ArrayList<>(
                    events == null ? Collections.emptyList() : events));
            this.keys = Collections.unmodifiableList(new ArrayList<>(
                    keys == null ? Collections.emptyList() : keys));
        }

        static Scan empty() {
            return new Scan(null, null);
        }
    }
}
