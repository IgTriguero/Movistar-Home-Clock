package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Codec JSON versionado del diagnóstico persistido ({@code diagnostics.json}).
 *
 * {@link #decode(String)} nunca lanza. Devuelve {@code null} si no hay JSON,
 * está roto o la versión no se reconoce. Una versión futura se decodifica de
 * forma tolerante con {@code readOnly=true}: el store no debe sobrescribirla.
 *
 * {@link #encode(DiagnosticsState)} es determinista: las fuentes se ordenan
 * (365scores, rfef, celta y después alfabéticamente), las salidas por fecha
 * descendente y el histórico de memoria por fecha ascendente.
 */
final class DiagnosticsCodec {

    static final int VERSION = 1;

    private static final List<String> PREFERRED_SOURCES =
            Arrays.asList("365scores", "rfef", "celta");

    private static final String KEY_VERSION = "version";
    private static final String KEY_SAVED_AT = "savedAt";
    private static final String KEY_FIRST_SEEN_AT = "firstSeenAt";
    private static final String KEY_APP_VERSION_CODE = "appVersionCode";
    private static final String KEY_APP_VERSION_NAME = "appVersionName";
    private static final String KEY_SDK = "sdk";
    private static final String KEY_PROCESS = "process";
    private static final String KEY_MATCHES = "matches";
    private static final String KEY_WEATHER = "weather";
    private static final String KEY_MEMORY = "memory";
    private static final String KEY_EXITS = "exits";
    private static final String KEY_COUNTERS = "counters";
    private static final String KEY_EXIT_SCAN_KEYS = "exitScanKeys";

    private DiagnosticsCodec() {
    }

    static String encode(DiagnosticsState state) {
        if (state == null) return "";

        try {
            JSONObject root = new JSONObject();
            root.put(KEY_VERSION, VERSION);
            root.put(KEY_SAVED_AT, state.savedAt);
            root.put(KEY_FIRST_SEEN_AT, state.firstSeenAt);
            root.put(KEY_APP_VERSION_CODE, state.appVersionCode);
            root.put(KEY_APP_VERSION_NAME,
                    truncate(safe(state.appVersionName), 64));
            root.put(KEY_SDK, state.sdk);
            root.put(KEY_PROCESS, processToJson(state.process));
            root.put(KEY_MATCHES, matchesToJson(state.matches));
            root.put(KEY_WEATHER, weatherToJson(state.weather));
            root.put(KEY_MEMORY, memoryToJson(state.memory));
            root.put(KEY_EXITS, exitsToJson(state.exits));
            root.put(KEY_COUNTERS, countersToJson(state.counters));

            JSONArray keys = new JSONArray();
            int keyCount = 0;
            if (state.exitScanKeys != null) {
                for (String key : state.exitScanKeys) {
                    if (key == null || key.isEmpty()) continue;
                    if (keyCount >= DiagnosticsState.MAX_EXIT_KEYS) break;
                    keys.put(truncate(key, DiagnosticsState.MAX_ID_CHARS));
                    keyCount++;
                }
            }
            root.put(KEY_EXIT_SCAN_KEYS, keys);

            return root.toString();
        } catch (Exception e) {
            // Con los tipos usados solo fallaría por un estado corrupto.
            return "";
        }
    }

    /** Resultado tolerante: estado parseado y si el fichero es de una versión futura. */
    static Decoded decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;

        try {
            JSONObject root = new JSONObject(raw);
            long version = longValue(root, KEY_VERSION);
            if (version < 1L) return null;
            boolean readOnly = version > VERSION;

            DiagnosticsState state = new DiagnosticsState(
                    longValue(root, KEY_SAVED_AT),
                    longValue(root, KEY_FIRST_SEEN_AT),
                    longValue(root, KEY_APP_VERSION_CODE),
                    stringValue(root, KEY_APP_VERSION_NAME, 64),
                    intValue(root, KEY_SDK),
                    processFromJson(root.optJSONObject(KEY_PROCESS)),
                    matchesFromJson(root.optJSONObject(KEY_MATCHES)),
                    weatherFromJson(root.optJSONObject(KEY_WEATHER)),
                    memoryFromJson(root.optJSONObject(KEY_MEMORY)),
                    exitsFromJson(root.optJSONArray(KEY_EXITS)),
                    countersFromJson(root.optJSONObject(KEY_COUNTERS)),
                    exitKeysFromJson(root.optJSONArray(KEY_EXIT_SCAN_KEYS)));

            return new Decoded(state, readOnly);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject processToJson(DiagnosticsState.ProcessInfo process)
            throws Exception {
        DiagnosticsState.ProcessInfo safe = process == null
                ? DiagnosticsState.ProcessInfo.empty() : process;
        JSONObject json = new JSONObject();
        json.put("pid", safe.pid);
        json.put("startedAt", safe.startedAt);
        json.put("startElapsedRealtime", safe.startElapsedRealtime);
        json.put("bootCount", safe.bootCount);
        json.put("lastHeartbeatAt", safe.lastHeartbeatAt);
        json.put("lastHeartbeatElapsedRealtime", safe.lastHeartbeatElapsedRealtime);
        json.put("lastForegroundAt", safe.lastForegroundAt);
        json.put("lastBackgroundAt", safe.lastBackgroundAt);
        json.put("lastTrimLevel", safe.lastTrimLevel);
        json.put("lastTrimAt", safe.lastTrimAt);
        json.put("activityClosedAt", safe.activityClosedAt);
        json.put("sessionCount", safe.sessionCount);
        return json;
    }

    private static DiagnosticsState.ProcessInfo processFromJson(JSONObject json) {
        if (json == null) return DiagnosticsState.ProcessInfo.empty();
        return new DiagnosticsState.ProcessInfo(
                intValue(json, "pid"),
                longValue(json, "startedAt"),
                longValue(json, "startElapsedRealtime"),
                intValue(json, "bootCount"),
                longValue(json, "lastHeartbeatAt"),
                longValue(json, "lastHeartbeatElapsedRealtime"),
                longValue(json, "lastForegroundAt"),
                longValue(json, "lastBackgroundAt"),
                intValue(json, "lastTrimLevel"),
                longValue(json, "lastTrimAt"),
                longValue(json, "activityClosedAt"),
                intValue(json, "sessionCount"));
    }

    private static JSONObject matchesToJson(DiagnosticsState.MatchesInfo matches)
            throws Exception {
        DiagnosticsState.MatchesInfo safe = matches == null
                ? DiagnosticsState.MatchesInfo.empty() : matches;
        JSONObject json = new JSONObject();
        json.put("date", safe(safe.date));
        json.put("attemptStartedAt", safe.attemptStartedAt);
        json.put("finishedAt", safe.finishedAt);
        json.put("durationMs", safe.durationMs);
        json.put("fetchedAt", safe.fetchedAt);
        json.put("stale", safe.stale);
        json.put("partial", safe.partial);
        json.put("matchCount", safe.matchCount);

        List<Map.Entry<String, SourceHealth>> sources =
                new ArrayList<>(safe.sources.entrySet());
        sources.sort(Comparator
                .comparingInt((Map.Entry<String, SourceHealth> entry) ->
                        sourceRank(entry.getKey()))
                .thenComparing(Map.Entry::getKey));

        JSONArray array = new JSONArray();
        int count = 0;
        for (Map.Entry<String, SourceHealth> entry : sources) {
            String id = entry.getKey();
            SourceHealth health = entry.getValue();
            if (id == null || id.isEmpty() || health == null) continue;
            if (count >= DiagnosticsState.MAX_SOURCES) break;
            array.put(sourceToJson(id, health));
            count++;
        }
        json.put("sources", array);
        return json;
    }

    private static int sourceRank(String id) {
        if (id == null) return PREFERRED_SOURCES.size();
        int index = PREFERRED_SOURCES.indexOf(id);
        return index >= 0 ? index : PREFERRED_SOURCES.size();
    }

    private static DiagnosticsState.MatchesInfo matchesFromJson(JSONObject json) {
        if (json == null) return DiagnosticsState.MatchesInfo.empty();

        Map<String, SourceHealth> sources = new LinkedHashMap<>();
        JSONArray array = json.optJSONArray("sources");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                SourceHealth health = sourceFromJson(array.optJSONObject(i));
                if (health == null || health.sourceId.isEmpty()) continue;
                if (sources.containsKey(health.sourceId)) continue;
                if (sources.size() >= DiagnosticsState.MAX_SOURCES) break;
                sources.put(health.sourceId, health);
            }
        }

        return new DiagnosticsState.MatchesInfo(
                stringValue(json, "date", DiagnosticsState.MAX_ID_CHARS),
                longValue(json, "attemptStartedAt"),
                longValue(json, "finishedAt"),
                longValue(json, "durationMs"),
                longValue(json, "fetchedAt"),
                json.optBoolean("stale", false),
                json.optBoolean("partial", false),
                intValue(json, "matchCount"),
                sources);
    }

    private static JSONObject sourceToJson(String id, SourceHealth health)
            throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", truncate(id, DiagnosticsState.MAX_ID_CHARS));
        json.put("state", safety(health.state).name());
        json.put("error", truncate(safe(health.error),
                DiagnosticsState.MAX_ERROR_CHARS));
        json.put("lastAttemptAt", health.lastAttemptAt);
        json.put("lastOkAt", health.lastOkAt);
        json.put("lastErrorAt", health.lastErrorAt);
        json.put("durationMs", health.durationMs);
        json.put("fromCache", health.fromCache);
        json.put("matchCount", health.matchCount);
        json.put("consecutiveFailures", health.consecutiveFailures);
        json.put("downSince", health.downSince);
        json.put("attempts", health.attempts);
        json.put("failures", health.failures);
        return json;
    }

    private static SourceHealth sourceFromJson(JSONObject json) {
        if (json == null) return null;

        String id = stringValue(json, "id", DiagnosticsState.MAX_ID_CHARS);
        if (id.isEmpty()) {
            // Compatibilidad con un posible nombre alternativo.
            id = stringValue(json, "sourceId", DiagnosticsState.MAX_ID_CHARS);
        }
        if (id.isEmpty()) return null;

        String stateName = stringValue(json, "state", DiagnosticsState.MAX_ID_CHARS);
        SourceState state;
        try {
            state = SourceState.valueOf(stateName);
        } catch (IllegalArgumentException e) {
            state = SourceState.ERROR;
        }

        return new SourceHealth(
                id,
                state,
                nullableString(json, "error", DiagnosticsState.MAX_ERROR_CHARS),
                longValue(json, "lastAttemptAt"),
                longValue(json, "lastOkAt"),
                longValue(json, "lastErrorAt"),
                longValue(json, "durationMs"),
                json.optBoolean("fromCache", false),
                intValue(json, "matchCount"),
                intValue(json, "consecutiveFailures"),
                longValue(json, "downSince"),
                intValue(json, "attempts"),
                intValue(json, "failures"));
    }

    private static JSONObject weatherToJson(DiagnosticsState.WeatherInfo weather)
            throws Exception {
        DiagnosticsState.WeatherInfo safe = weather == null
                ? DiagnosticsState.WeatherInfo.empty() : weather;
        JSONObject json = new JSONObject();
        json.put("attemptStartedAt", safe.attemptStartedAt);
        json.put("finishedAt", safe.finishedAt);
        json.put("durationMs", safe.durationMs);
        json.put("fetchedAt", safe.fetchedAt);
        json.put("state", safe.state);
        json.put("partial", safe.partial);
        json.put("error", truncate(safe(safe.error),
                DiagnosticsState.MAX_ERROR_CHARS));
        json.put("cityCount", safe.cityCount);
        json.put("consecutiveFailures", safe.consecutiveFailures);
        json.put("lastOkAt", safe.lastOkAt);
        json.put("lastErrorAt", safe.lastErrorAt);
        json.put("downSince", safe.downSince);
        return json;
    }

    private static DiagnosticsState.WeatherInfo weatherFromJson(JSONObject json) {
        if (json == null) return DiagnosticsState.WeatherInfo.empty();
        return new DiagnosticsState.WeatherInfo(
                longValue(json, "attemptStartedAt"),
                longValue(json, "finishedAt"),
                longValue(json, "durationMs"),
                longValue(json, "fetchedAt"),
                stringValue(json, "state", DiagnosticsState.MAX_ID_CHARS),
                json.optBoolean("partial", false),
                stringValue(json, "error", DiagnosticsState.MAX_ERROR_CHARS),
                intValue(json, "cityCount"),
                intValue(json, "consecutiveFailures"),
                longValue(json, "lastOkAt"),
                longValue(json, "lastErrorAt"),
                longValue(json, "downSince"));
    }

    private static JSONObject memoryToJson(DiagnosticsState.MemoryInfo memory)
            throws Exception {
        DiagnosticsState.MemoryInfo safe = memory == null
                ? DiagnosticsState.MemoryInfo.empty() : memory;
        JSONObject json = new JSONObject();
        json.put("sampledAt", safe.sampledAt);
        json.put("totalPssKb", safe.totalPssKb);
        json.put("javaKb", safe.javaKb);
        json.put("nativeKb", safe.nativeKb);
        json.put("codeKb", safe.codeKb);
        json.put("graphicsKb", safe.graphicsKb);
        json.put("privateDirtyKb", safe.privateDirtyKb);
        json.put("importance", safe.importance);
        json.put("samples", safe.samples);
        json.put("minPssKb", safe.minPssKb);
        json.put("maxPssKb", safe.maxPssKb);

        List<DiagnosticsState.Sample> history = new ArrayList<>(safe.history);
        history.sort(Comparator.comparingLong((DiagnosticsState.Sample sample) -> sample.at));
        int from = Math.max(0, history.size() - DiagnosticsState.MAX_HISTORY);

        JSONArray array = new JSONArray();
        for (int i = from; i < history.size(); i++) {
            DiagnosticsState.Sample sample = history.get(i);
            if (sample == null) continue;
            JSONObject item = new JSONObject();
            item.put("at", sample.at);
            item.put("totalPssKb", sample.totalPssKb);
            array.put(item);
        }
        json.put("history", array);
        return json;
    }

    private static DiagnosticsState.MemoryInfo memoryFromJson(JSONObject json) {
        if (json == null) return DiagnosticsState.MemoryInfo.empty();

        List<DiagnosticsState.Sample> history = new ArrayList<>();
        JSONArray array = json.optJSONArray("history");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                history.add(new DiagnosticsState.Sample(
                        longValue(item, "at"),
                        intValue(item, "totalPssKb")));
            }
        }

        return new DiagnosticsState.MemoryInfo(
                longValue(json, "sampledAt"),
                intValue(json, "totalPssKb"),
                intValue(json, "javaKb"),
                intValue(json, "nativeKb"),
                intValue(json, "codeKb"),
                intValue(json, "graphicsKb"),
                intValue(json, "privateDirtyKb"),
                intValue(json, "importance"),
                intValue(json, "samples"),
                intValue(json, "minPssKb"),
                intValue(json, "maxPssKb"),
                history);
    }

    private static JSONArray exitsToJson(List<DiagnosticsState.ExitEvent> exits)
            throws Exception {
        List<DiagnosticsState.ExitEvent> sorted = new ArrayList<>();
        if (exits != null) {
            for (DiagnosticsState.ExitEvent event : exits) {
                if (event != null) sorted.add(event);
            }
        }
        sorted.sort(Comparator
                .comparingLong((DiagnosticsState.ExitEvent event) -> event.at).reversed()
                .thenComparing(Comparator.comparingLong(
                        (DiagnosticsState.ExitEvent event) -> event.observedAt).reversed())
                .thenComparingInt(event -> event.reasonCode)
                .thenComparingInt(event -> event.pid));

        JSONArray array = new JSONArray();
        int count = Math.min(sorted.size(), DiagnosticsState.MAX_EXITS);
        for (int i = 0; i < count; i++) {
            array.put(exitToJson(sorted.get(i)));
        }
        return array;
    }

    private static JSONObject exitToJson(DiagnosticsState.ExitEvent event)
            throws Exception {
        JSONObject json = new JSONObject();
        json.put("at", event.at);
        json.put("observedAt", event.observedAt);
        json.put("cause", truncate(safe(event.cause), DiagnosticsState.MAX_ID_CHARS));
        json.put("reasonCode", event.reasonCode);
        json.put("status", event.status);
        json.put("pid", event.pid);
        json.put("importance", event.importance);
        json.put("description", truncate(safe(event.description),
                DiagnosticsState.MAX_DESCRIPTION_CHARS));
        json.put("confidence", truncate(safe(event.confidence),
                DiagnosticsState.MAX_ID_CHARS));
        json.put("appVersionCode", event.appVersionCode);
        json.put("sessionDurationMs", event.sessionDurationMs);
        return json;
    }

    private static List<DiagnosticsState.ExitEvent> exitsFromJson(JSONArray array) {
        List<DiagnosticsState.ExitEvent> exits = new ArrayList<>();
        if (array == null) return exits;

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            exits.add(new DiagnosticsState.ExitEvent(
                    longValue(item, "at"),
                    longValue(item, "observedAt"),
                    stringValue(item, "cause", DiagnosticsState.MAX_ID_CHARS),
                    intValue(item, "reasonCode"),
                    intValue(item, "status"),
                    intValue(item, "pid"),
                    intValue(item, "importance"),
                    stringValue(item, "description",
                            DiagnosticsState.MAX_DESCRIPTION_CHARS),
                    stringValue(item, "confidence", DiagnosticsState.MAX_ID_CHARS),
                    longValue(item, "appVersionCode"),
                    longValue(item, "sessionDurationMs")));
        }
        return exits;
    }

    private static JSONObject countersToJson(DiagnosticsState.Counters counters)
            throws Exception {
        DiagnosticsState.Counters safe = counters == null
                ? DiagnosticsState.Counters.empty() : counters;
        JSONObject json = new JSONObject();
        json.put("sessions", safe.sessions);
        json.put("matchesAttempts", safe.matchesAttempts);
        json.put("matchesOk", safe.matchesOk);
        json.put("matchesFail", safe.matchesFail);
        json.put("weatherAttempts", safe.weatherAttempts);
        json.put("weatherOk", safe.weatherOk);
        json.put("weatherFail", safe.weatherFail);
        json.put("exits", safe.exits);
        json.put("crashes", safe.crashes);
        json.put("anrs", safe.anrs);
        json.put("lmk", safe.lmk);
        json.put("unknownExits", safe.unknownExits);
        return json;
    }

    private static DiagnosticsState.Counters countersFromJson(JSONObject json) {
        if (json == null) return DiagnosticsState.Counters.empty();
        return new DiagnosticsState.Counters(
                intValue(json, "sessions"),
                intValue(json, "matchesAttempts"),
                intValue(json, "matchesOk"),
                intValue(json, "matchesFail"),
                intValue(json, "weatherAttempts"),
                intValue(json, "weatherOk"),
                intValue(json, "weatherFail"),
                intValue(json, "exits"),
                intValue(json, "crashes"),
                intValue(json, "anrs"),
                intValue(json, "lmk"),
                intValue(json, "unknownExits"));
    }

    private static List<String> exitKeysFromJson(JSONArray array) {
        List<String> keys = new ArrayList<>();
        if (array == null) return keys;

        for (int i = 0; i < array.length(); i++) {
            if (keys.size() >= DiagnosticsState.MAX_EXIT_KEYS) break;
            Object value = array.opt(i);
            if (!(value instanceof String)) continue;
            String key = truncate((String) value, DiagnosticsState.MAX_ID_CHARS);
            if (key.isEmpty()) continue;
            keys.add(key);
        }
        return keys;
    }

    private static SourceState safety(SourceState state) {
        return state == null ? SourceState.ERROR : state;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        if (max <= 0 || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private static String stringValue(JSONObject json, String key, int max) {
        if (json == null || !json.has(key) || json.isNull(key)) return "";
        return truncate(json.optString(key, ""), max);
    }

    private static String nullableString(JSONObject json, String key, int max) {
        String value = stringValue(json, key, max);
        return value.isEmpty() ? null : value;
    }

    /**
     * Lee un entero largo tolerante: números y cadenas, con negativos y valores
     * no finitos convertidos en 0.
     */
    private static long longValue(JSONObject json, String key) {
        if (json == null || !json.has(key) || json.isNull(key)) return 0L;

        Object value = json.opt(key);
        if (value instanceof Number) {
            double asDouble = ((Number) value).doubleValue();
            if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)) return 0L;
            long asLong = ((Number) value).longValue();
            return Math.max(0L, asLong);
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) return 0L;
            try {
                return Math.max(0L, Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                try {
                    double asDouble = Double.parseDouble(text);
                    if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)) {
                        return 0L;
                    }
                    return Math.max(0L, (long) asDouble);
                } catch (NumberFormatException ignoredAgain) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private static int intValue(JSONObject json, String key) {
        long value = longValue(json, key);
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    /** Estado decodificado junto a la marca de solo lectura. */
    static final class Decoded {

        final DiagnosticsState state;
        final boolean readOnly;

        Decoded(DiagnosticsState state, boolean readOnly) {
            this.state = state;
            this.readOnly = readOnly;
        }
    }
}
