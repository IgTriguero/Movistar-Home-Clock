package com.itriguero.homeclock;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Caché diaria de snapshots en disco, un archivo por fecha.
 *
 * El guardado es atómico (tmp + rename). La lectura nunca lanza: devuelve
 * null si el archivo no existe, la versión no coincide o el JSON está roto.
 */
public final class MatchCache {

    static final int VERSION = 1;

    private static final String PREFIX = "matches_";
    private static final String SUFFIX = ".json";

    private final File dir;

    public MatchCache(Context context) {
        this(context.getFilesDir());
    }

    public MatchCache(File dir) {
        this.dir = dir;
    }

    public void save(MatchesSnapshot snapshot) {
        if (snapshot == null || snapshot.date == null || dir == null) return;

        File file = fileFor(snapshot.date);
        File tmp = new File(dir, file.getName() + ".tmp." + System.nanoTime());
        try {
            if (!dir.exists() && !dir.mkdirs()) return;

            JSONObject root = toJson(snapshot);
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }

            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // La caché es best-effort: sin ella se vuelve a consultar.
        } finally {
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    public MatchesSnapshot load(LocalDate date) {
        if (date == null || dir == null) return null;

        File file = fileFor(date);
        if (!file.isFile() || file.length() <= 0) return null;

        try {
            String raw = new String(
                    Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(raw);
            if (root.optInt("version", -1) != VERSION) return null;

            String storedDate = root.optString("date", "");
            if (!storedDate.isEmpty() && !date.toString().equals(storedDate)) {
                return null;
            }
            long fetchedAt = root.optLong("fetchedAt", 0);
            if (fetchedAt <= 0) return null;

            List<Match> matches = new ArrayList<>();
            JSONArray matchArray = root.optJSONArray("matches");
            if (matchArray != null) {
                for (int i = 0; i < matchArray.length(); i++) {
                    Match match = matchFromJson(matchArray.optJSONObject(i));
                    if (match != null) matches.add(match);
                }
            }

            Map<String, SourceStatus> sources = new LinkedHashMap<>();
            JSONArray sourceArray = root.optJSONArray("sources");
            if (sourceArray != null) {
                for (int i = 0; i < sourceArray.length(); i++) {
                    SourceStatus status = sourceFromJson(sourceArray.optJSONObject(i));
                    if (status != null) sources.put(status.sourceId, status);
                }
            }

            return new MatchesSnapshot(
                    date, matches, sources,
                    fetchedAt,
                    root.optBoolean("stale", false));
        } catch (Exception e) {
            return null;
        }
    }

    private File fileFor(LocalDate date) {
        return new File(dir, PREFIX
                + date.format(DateTimeFormatter.ISO_LOCAL_DATE) + SUFFIX);
    }

    private static JSONObject toJson(MatchesSnapshot snapshot) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        root.put("date", snapshot.date.toString());
        root.put("fetchedAt", snapshot.fetchedAt);
        root.put("stale", snapshot.stale);

        JSONArray matches = new JSONArray();
        for (Match match : snapshot.matches) {
            if (match != null) matches.put(matchToJson(match));
        }
        root.put("matches", matches);

        JSONArray sources = new JSONArray();
        for (SourceStatus status : snapshot.sources.values()) {
            if (status != null) sources.put(sourceToJson(status));
        }
        root.put("sources", sources);
        return root;
    }

    private static JSONObject matchToJson(Match match) throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", match.id);
        json.put("sourceId", safe(match.sourceId));
        json.put("sport", safe(match.sport));
        json.put("competition", safe(match.competition));
        json.put("startMillis", match.startMillis);
        json.put("status", (match.status == null ? MatchStatus.UNKNOWN : match.status).name());
        json.put("statusText", safe(match.statusText));
        json.put("gameTimeDisplay", safe(match.gameTimeDisplay));
        json.put("gameTime", finite(match.gameTime, -1));
        json.put("timeTbd", match.timeTbd);
        json.put("scoreKnown", match.scoreKnown);
        json.put("homeId", match.homeId);
        json.put("awayId", match.awayId);
        json.put("homeName", safe(match.homeName));
        json.put("awayName", safe(match.awayName));
        putLogo(json, "homeLogoUrl", match.homeLogoUrl);
        putLogo(json, "awayLogoUrl", match.awayLogoUrl);
        json.put("homeScore", finite(match.homeScore, -1));
        json.put("awayScore", finite(match.awayScore, -1));
        json.put("trackedIsHome", match.trackedIsHome);
        json.put("trackedName", safe(match.trackedName));
        json.put("opponentName", safe(match.opponentName));
        return json;
    }

    private static Match matchFromJson(JSONObject json) {
        if (json == null) return null;

        Match match = new Match();
        match.id = json.optLong("id", 0);
        match.sourceId = json.optString("sourceId", "");
        match.sport = json.optString("sport", "");
        match.competition = json.optString("competition", "");
        match.startMillis = json.optLong("startMillis", 0);
        match.status = parseStatus(json.optString("status", ""));
        match.statusText = json.optString("statusText", "");
        match.gameTimeDisplay = json.optString("gameTimeDisplay", "");
        match.gameTime = json.optDouble("gameTime", -1);
        match.timeTbd = json.optBoolean("timeTbd", false);
        match.scoreKnown = json.optBoolean("scoreKnown", false);
        match.homeId = json.optLong("homeId", 0);
        match.awayId = json.optLong("awayId", 0);
        match.homeName = json.optString("homeName", "");
        match.awayName = json.optString("awayName", "");
        match.homeLogoUrl = nullable(json, "homeLogoUrl");
        match.awayLogoUrl = nullable(json, "awayLogoUrl");
        match.homeScore = json.optDouble("homeScore", 0);
        match.awayScore = json.optDouble("awayScore", 0);
        match.trackedIsHome = json.optBoolean("trackedIsHome", false);
        match.trackedName = json.optString("trackedName", "");
        match.opponentName = json.optString("opponentName", "");
        return match;
    }

    private static JSONObject sourceToJson(SourceStatus status) throws Exception {
        JSONObject json = new JSONObject();
        json.put("sourceId", safe(status.sourceId));
        json.put("state", status.state.name());
        if (status.error != null && !status.error.isEmpty()) {
            json.put("error", status.error);
        }
        json.put("fetchedAt", status.fetchedAt);
        json.put("durationMs", status.durationMs);
        json.put("fromCache", status.fromCache);
        json.put("matchCount", status.matchCount);
        return json;
    }

    private static SourceStatus sourceFromJson(JSONObject json) {
        if (json == null) return null;

        String sourceId = json.optString("sourceId", "");
        if (sourceId.isEmpty()) return null;

        SourceState state;
        try {
            state = SourceState.valueOf(json.optString("state", ""));
        } catch (IllegalArgumentException e) {
            state = SourceState.ERROR;
        }
        return new SourceStatus(
                sourceId,
                state,
                nullable(json, "error"),
                json.optLong("fetchedAt", 0),
                json.optLong("durationMs", 0),
                json.optBoolean("fromCache", false),
                json.optInt("matchCount", 0));
    }

    private static MatchStatus parseStatus(String name) {
        try {
            return MatchStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            return MatchStatus.UNKNOWN;
        }
    }

    private static void putLogo(JSONObject json, String key, String url) throws Exception {
        if (url == null || url.isEmpty()) {
            json.remove(key);
        } else {
            json.put(key, url);
        }
    }

    private static String nullable(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) return null;
        String value = json.optString(key, "");
        return value.isEmpty() ? null : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static double finite(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
        return value;
    }
}
