package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Caché en disco del detalle de partidos finalizados, un JSON por partido.
 *
 * Solo se guardan los datos que necesita la UI; {@code members}, apuestas y
 * {@code topPerformers} se descartan. La escritura es atómica y la lectura es
 * tolerante: un archivo roto, de otra versión o con una clave distinta se
 * borra y se devuelve {@code null}.
 */
final class MatchDetailCache {

    static final int VERSION = 1;
    static final int MAX_FILES = 60;
    static final long MAX_BYTES = 12L * 1024L * 1024L;

    private static final String SUFFIX = ".json";

    private final File dir;

    MatchDetailCache(File dir) {
        this.dir = dir;
    }

    synchronized MatchDetail load(Match match) {
        if (match == null || !match.isFinished() || dir == null) return null;

        File file = fileFor(match);
        if (file == null || !file.isFile() || file.length() <= 0) return null;

        try {
            String raw = new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(raw);

            if (root.optInt("version", -1) != VERSION
                    || root.optLong("gameId", -1) != match.id
                    || !sameSource(root.optString("sourceId", ""), match.sourceId)
                    || !root.optBoolean("final", false)) {
                delete(file);
                return null;
            }

            MatchDetail detail = fromJson(root);
            if (detail == null || !detail.isFinished()) {
                delete(file);
                return null;
            }

            // LRU: leer un partido lo rejuvenece.
            //noinspection ResultOfMethodCallIgnored
            file.setLastModified(System.currentTimeMillis());
            return detail.withFetch(detail.fetchedAt, true);
        } catch (Exception e) {
            delete(file);
            return null;
        }
    }

    synchronized void save(MatchDetail detail) {
        if (detail == null || !detail.isFinished() || dir == null) return;

        File tmp = null;
        try {
            if (!dir.exists() && !dir.mkdirs()) return;
            prune();

            File file = fileFor(detail);
            if (file == null) return;
            tmp = new File(dir, file.getName() + ".tmp." + System.nanoTime());

            try (OutputStream output = new FileOutputStream(tmp)) {
                output.write(toJson(detail).toString().getBytes(StandardCharsets.UTF_8));
            }

            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException | UnsupportedOperationException e) {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // La caché es best-effort: si falla se vuelve a pedir al servidor.
        } finally {
            if (tmp != null && tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            prune();
        }
    }

    synchronized void clear() {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return;
        for (File file : files) delete(file);
    }

    synchronized int size() {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File file : files) {
            if (isCacheFile(file)) count++;
        }
        return count;
    }

    private void prune() {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return;

        List<File> cacheFiles = new ArrayList<>();
        for (File file : files) {
            if (isCacheFile(file)) cacheFiles.add(file);
        }
        if (cacheFiles.size() <= MAX_FILES && totalBytes(cacheFiles) <= MAX_BYTES) {
            return;
        }

        cacheFiles.sort(Comparator.comparingLong(File::lastModified));
        long total = totalBytes(cacheFiles);
        int count = cacheFiles.size();
        for (File file : cacheFiles) {
            if (count <= MAX_FILES && total <= MAX_BYTES) break;
            long length = file.length();
            if (delete(file)) {
                count--;
                total -= length;
            }
        }
    }

    private static long totalBytes(List<File> files) {
        long total = 0;
        for (File file : files) total += Math.max(0L, file.length());
        return total;
    }

    private static boolean isCacheFile(File file) {
        return file != null && file.isFile() && file.getName().endsWith(SUFFIX);
    }

    private File fileFor(Match match) {
        if (match == null) return null;
        return new File(dir, key(match.sourceId, match.id) + SUFFIX);
    }

    private File fileFor(MatchDetail detail) {
        if (detail == null) return null;
        return new File(dir, key(detail.sourceId, detail.gameId) + SUFFIX);
    }

    private static String key(String sourceId, long gameId) {
        return safeKey(sourceId) + "_" + gameId;
    }

    private static String safeKey(String sourceId) {
        String raw = sourceId == null ? "" : sourceId.trim();
        return raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static boolean sameSource(String stored, String current) {
        return safeKey(stored).equals(safeKey(current));
    }

    private static boolean delete(File file) {
        return file != null && file.exists() && file.delete();
    }

    private static JSONObject toJson(MatchDetail detail) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        root.put("final", detail.isFinished());
        root.put("gameId", detail.gameId);
        root.put("sourceId", safe(detail.sourceId));
        root.put("competition", safe(detail.competition));
        root.put("sport", safe(detail.sport));
        root.put("startMillis", detail.startMillis);
        root.put("status", detail.status.name());
        root.put("statusText", safe(detail.statusText));
        root.put("gameTimeDisplay", safe(detail.gameTimeDisplay));
        root.put("scoreKnown", detail.scoreKnown);
        root.put("homeScore", detail.homeScore);
        root.put("awayScore", detail.awayScore);
        root.put("homeName", safe(detail.homeName));
        root.put("awayName", safe(detail.awayName));
        root.put("homeId", detail.homeId);
        root.put("awayId", detail.awayId);
        root.put("fetchedAt", detail.fetchedAt);
        root.put("hasStats", detail.hasStats);
        root.put("statsError", safe(detail.statsError));

        if (detail.venue != null) root.put("venue", venueToJson(detail.venue));
        if (!detail.officials.isEmpty()) {
            root.put("officials", officialsToJson(detail.officials));
        }
        if (!detail.events.isEmpty()) {
            root.put("events", eventsToJson(detail.events));
        }
        if (detail.homeLineup != null) {
            root.put("homeLineup", lineupToJson(detail.homeLineup));
        }
        if (detail.awayLineup != null) {
            root.put("awayLineup", lineupToJson(detail.awayLineup));
        }
        if (!detail.statCategories.isEmpty()) {
            root.put("statCategories", statsToJson(detail.statCategories));
        }
        if (!detail.tvNetworks.isEmpty()) {
            root.put("tvNetworks", tvNetworksToJson(detail.tvNetworks));
        }
        return root;
    }

    private static JSONObject venueToJson(Venue venue) throws Exception {
        JSONObject json = new JSONObject();
        json.put("name", safe(venue.name));
        json.put("capacity", venue.capacity);
        json.put("attendance", venue.attendance);
        return json;
    }

    private static JSONArray officialsToJson(List<Official> officials) throws Exception {
        JSONArray array = new JSONArray();
        for (Official official : officials) {
            JSONObject json = new JSONObject();
            json.put("name", safe(official.name));
            json.put("role", safe(official.role));
            array.put(json);
        }
        return array;
    }

    private static JSONArray eventsToJson(List<MatchEvent> events) throws Exception {
        JSONArray array = new JSONArray();
        for (MatchEvent event : events) {
            JSONObject json = new JSONObject();
            json.put("order", event.order);
            json.put("minute", event.minute);
            json.put("addedTime", event.addedTime);
            json.put("display", safe(event.display));
            json.put("type", event.type.name());
            json.put("side", event.side.name());
            json.put("playerName", safe(event.playerName));
            json.put("playerNumber", event.playerNumber);
            json.put("secondaryName", safe(event.secondaryName));
            json.put("major", event.major);
            array.put(json);
        }
        return array;
    }

    private static JSONObject lineupToJson(TeamLineup lineup) throws Exception {
        JSONObject json = new JSONObject();
        json.put("teamId", lineup.teamId);
        json.put("teamName", safe(lineup.teamName));
        json.put("formation", safe(lineup.formation));
        json.put("status", safe(lineup.status));
        json.put("coach", safe(lineup.coach));
        json.put("starters", playersToJson(lineup.starters));
        json.put("substitutes", playersToJson(lineup.substitutes));
        json.put("missing", playersToJson(lineup.missing));
        return json;
    }

    private static JSONArray playersToJson(List<LineupPlayer> players) throws Exception {
        JSONArray array = new JSONArray();
        for (LineupPlayer player : players) {
            JSONObject json = new JSONObject();
            json.put("playerId", player.playerId);
            json.put("name", safe(player.name));
            json.put("number", player.number);
            json.put("position", safe(player.position));
            json.put("positionShort", safe(player.positionShort));
            json.put("rating", finite(player.rating, -1));
            json.put("starter", player.starter);
            array.put(json);
        }
        return array;
    }

    private static JSONArray statsToJson(List<StatCategory> categories) throws Exception {
        JSONArray array = new JSONArray();
        for (StatCategory category : categories) {
            JSONObject json = new JSONObject();
            json.put("name", safe(category.name));
            json.put("order", category.order);

            JSONArray rows = new JSONArray();
            for (StatRow row : category.rows) {
                JSONObject rowJson = new JSONObject();
                rowJson.put("id", row.id);
                rowJson.put("name", safe(row.name));
                rowJson.put("homeValue", safe(row.homeValue));
                rowJson.put("awayValue", safe(row.awayValue));
                rowJson.put("homePercent", finite(row.homePercent, -1));
                rowJson.put("awayPercent", finite(row.awayPercent, -1));
                rowJson.put("major", row.major);
                rows.put(rowJson);
            }
            json.put("rows", rows);
            array.put(json);
        }
        return array;
    }

    private static JSONArray tvNetworksToJson(List<String> networks) {
        JSONArray array = new JSONArray();
        for (String network : networks) array.put(safe(network));
        return array;
    }

    private static MatchDetail fromJson(JSONObject root) {
        if (root == null) return null;

        MatchStatus status = parseStatus(root.optString("status", ""));
        Venue venue = venueFromJson(root.optJSONObject("venue"));
        List<Official> officials = officialsFromJson(root.optJSONArray("officials"));
        List<MatchEvent> events = eventsFromJson(root.optJSONArray("events"));
        TeamLineup homeLineup = lineupFromJson(root.optJSONObject("homeLineup"));
        TeamLineup awayLineup = lineupFromJson(root.optJSONObject("awayLineup"));
        List<StatCategory> categories = statsFromJson(root.optJSONArray("statCategories"));
        List<String> networks = tvNetworksFromJson(root.optJSONArray("tvNetworks"));

        return new MatchDetail(
                root.optLong("gameId", 0L),
                root.optString("sourceId", ""),
                root.optString("competition", ""),
                root.optString("sport", ""),
                root.optLong("startMillis", 0L),
                status,
                root.optString("statusText", ""),
                root.optString("gameTimeDisplay", ""),
                root.optBoolean("scoreKnown", false),
                root.optInt("homeScore", -1),
                root.optInt("awayScore", -1),
                root.optString("homeName", ""),
                root.optString("awayName", ""),
                root.optLong("homeId", 0L),
                root.optLong("awayId", 0L),
                venue,
                officials,
                events,
                homeLineup,
                awayLineup,
                categories,
                root.optBoolean("hasStats", false),
                root.optString("statsError", ""),
                networks,
                root.optLong("fetchedAt", 0L),
                false);
    }

    private static Venue venueFromJson(JSONObject json) {
        if (json == null) return null;
        return new Venue(
                json.optString("name", ""),
                json.optInt("capacity", 0),
                json.optInt("attendance", 0));
    }

    private static List<Official> officialsFromJson(JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();

        List<Official> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;
            String name = json.optString("name", "");
            if (name.isEmpty()) continue;
            result.add(new Official(name, json.optString("role", "")));
        }
        return result;
    }

    private static List<MatchEvent> eventsFromJson(JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();

        List<MatchEvent> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;

            result.add(new MatchEvent(
                    json.optInt("order", i + 1),
                    json.optInt("minute", 0),
                    json.optInt("addedTime", 0),
                    json.optString("display", ""),
                    parseEventType(json.optString("type", "")),
                    parseEventSide(json.optString("side", "")),
                    json.optString("playerName", ""),
                    json.optInt("playerNumber", 0),
                    json.optString("secondaryName", ""),
                    json.optBoolean("major", false)));
        }
        return result;
    }

    private static TeamLineup lineupFromJson(JSONObject json) {
        if (json == null) return null;
        return new TeamLineup(
                json.optLong("teamId", 0L),
                json.optString("teamName", ""),
                json.optString("formation", ""),
                json.optString("status", ""),
                json.optString("coach", ""),
                playersFromJson(json.optJSONArray("starters")),
                playersFromJson(json.optJSONArray("substitutes")),
                playersFromJson(json.optJSONArray("missing")));
    }

    private static List<LineupPlayer> playersFromJson(JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();

        List<LineupPlayer> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;

            result.add(new LineupPlayer(
                    json.optLong("playerId", 0L),
                    json.optString("name", ""),
                    json.optInt("number", 0),
                    json.optString("position", ""),
                    json.optString("positionShort", ""),
                    json.optDouble("rating", -1),
                    json.optBoolean("starter", false)));
        }
        return result;
    }

    private static List<StatCategory> statsFromJson(JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();

        List<StatCategory> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;

            List<StatRow> rows = new ArrayList<>();
            JSONArray rowsJson = json.optJSONArray("rows");
            if (rowsJson != null) {
                for (int j = 0; j < rowsJson.length(); j++) {
                    JSONObject row = rowsJson.optJSONObject(j);
                    if (row == null) continue;
                    rows.add(new StatRow(
                            row.optInt("id", 0),
                            row.optString("name", ""),
                            row.optString("homeValue", ""),
                            row.optString("awayValue", ""),
                            row.optDouble("homePercent", -1),
                            row.optDouble("awayPercent", -1),
                            row.optBoolean("major", false)));
                }
            }
            result.add(new StatCategory(
                    json.optString("name", ""),
                    json.optInt("order", 0),
                    rows));
        }
        return result;
    }

    private static List<String> tvNetworksFromJson(JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();

        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String name = array.optString(i, "");
            if (name != null && !name.trim().isEmpty()) result.add(name.trim());
        }
        return result;
    }

    private static MatchStatus parseStatus(String name) {
        try {
            return MatchStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            return MatchStatus.UNKNOWN;
        }
    }

    private static MatchEvent.Type parseEventType(String name) {
        try {
            return MatchEvent.Type.valueOf(name);
        } catch (IllegalArgumentException e) {
            return MatchEvent.Type.OTHER;
        }
    }

    private static MatchEvent.Side parseEventSide(String name) {
        try {
            return MatchEvent.Side.valueOf(name);
        } catch (IllegalArgumentException e) {
            return MatchEvent.Side.NEUTRAL;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static double finite(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
        return value;
    }
}
