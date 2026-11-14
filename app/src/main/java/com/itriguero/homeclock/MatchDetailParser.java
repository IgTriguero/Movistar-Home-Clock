package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parser puro del detalle de un partido de 365Scores.
 *
 * No hace red ni usa Android: recibe el JSON de {@code /web/game/} y, si está
 * disponible, el de {@code /web/game/stats/}. Es tolerante con campos
 * ausentes: nunca lanza por un dato que falte, y si no hay objeto {@code game}
 * devuelve {@code null}.
 */
final class MatchDetailParser {

    private MatchDetailParser() {
    }

    static MatchDetail parse(JSONObject gameRoot, JSONObject statsRoot, Match fallback) {
        if (gameRoot == null) return null;

        JSONObject game = gameRoot.optJSONObject("game");
        // También se acepta directamente el objeto game (útil para tests y
        // para reutilizar el parser con respuestas ya extraídas).
        if (game == null && gameRoot.has("homeCompetitor")) game = gameRoot;
        if (game == null) return null;

        JSONObject home = game.optJSONObject("homeCompetitor");
        JSONObject away = game.optJSONObject("awayCompetitor");

        long homeId = longValue(home, "id", fallback == null ? 0 : fallback.homeId);
        long awayId = longValue(away, "id", fallback == null ? 0 : fallback.awayId);
        String homeName = text(home, "name", fallback == null ? "" : fallback.homeName);
        String awayName = text(away, "name", fallback == null ? "" : fallback.awayName);

        String statusText = text(game, "statusText",
                fallback == null ? "" : fallback.statusText);
        MatchStatus status = status(game, statusText, fallback);

        int homeScore = -1;
        int awayScore = -1;
        boolean scoreKnown = false;
        if (home != null && away != null
                && !home.isNull("score") && !away.isNull("score")) {
            homeScore = (int) Math.round(decimal(home, "score", -1));
            awayScore = (int) Math.round(decimal(away, "score", -1));
            scoreKnown = homeScore >= 0 && awayScore >= 0;
        }
        if (status.isPostponed() || status.isCancelled()) {
            scoreKnown = false;
            homeScore = -1;
            awayScore = -1;
        }

        Map<Long, JSONObject> members = members(game);
        List<MatchEvent> events = events(game, members, homeId, awayId);
        TeamLineup homeLineup = lineup(home, members, homeId, homeName);
        TeamLineup awayLineup = lineup(away, members, awayId, awayName);

        Stats stats = stats(statsRoot, homeId, awayId);

        return new MatchDetail(
                longValue(game, "id", fallback == null ? 0 : fallback.id),
                Scores365Source.ID,
                text(game, "competitionDisplayName",
                        fallback == null ? "" : fallback.competition),
                sport(game, fallback),
                startMillis(game, fallback),
                status,
                statusText,
                text(game, "gameTimeDisplay",
                        fallback == null ? "" : fallback.gameTimeDisplay),
                scoreKnown,
                homeScore,
                awayScore,
                homeName,
                awayName,
                homeId,
                awayId,
                venue(game.optJSONObject("venue")),
                officials(game.optJSONArray("officials")),
                events,
                homeLineup,
                awayLineup,
                stats.categories,
                !stats.categories.isEmpty(),
                stats.error,
                tvNetworks(game, gameRoot),
                0L,
                false);
    }

    private static MatchStatus status(JSONObject game, String statusText, Match fallback) {
        int group = intValue(game, "statusGroup", -1);
        if (group >= 0) {
            MatchStatus status = MatchStatusMapper.from365(group, statusText);
            if (status != MatchStatus.UNKNOWN) return status;
        }
        return fallback == null || fallback.status == null
                ? MatchStatus.UNKNOWN : fallback.status;
    }

    private static String sport(JSONObject game, Match fallback) {
        int sportId = intValue(game, "sportId", 0);
        if (sportId == 1) return "Fútbol";
        if (sportId == 2) return "Baloncesto";
        if (fallback != null && fallback.sport != null && !fallback.sport.isEmpty()) {
            return fallback.sport;
        }
        return "";
    }

    private static long startMillis(JSONObject game, Match fallback) {
        String raw = text(game, "startTime", "");
        if (!raw.isEmpty()) {
            try {
                return OffsetDateTime.parse(raw).toInstant().toEpochMilli();
            } catch (Exception ignored) {
                // Se usa el fallback: el estado del partido sigue siendo válido.
            }
        }
        return fallback == null ? 0L : fallback.startMillis;
    }

    private static Venue venue(JSONObject venue) {
        if (venue == null) return null;
        return new Venue(
                text(venue, "name", ""),
                intValue(venue, "capacity", 0),
                intValue(venue, "attendance", 0));
    }

    private static List<Official> officials(JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();

        List<Official> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject official = array.optJSONObject(i);
            if (official == null) continue;

            String name = text(official, "name", "");
            if (name.isEmpty()) continue;

            String role = text(official, "role", text(official, "roleName", ""));
            result.add(new Official(name, role));
        }
        return result;
    }

    /**
     * Emisoras del partido. 365Scores las publica en {@code game.tvNetworks};
     * en algunas respuestas cuelgan de la raíz, así que se aceptan ambas.
     */
    private static List<String> tvNetworks(JSONObject game, JSONObject gameRoot) {
        JSONArray array = game.optJSONArray("tvNetworks");
        if (array == null && gameRoot != null && gameRoot != game) {
            array = gameRoot.optJSONArray("tvNetworks");
        }
        if (array == null || array.length() == 0) return Collections.emptyList();

        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject network = array.optJSONObject(i);
            if (network == null) continue;

            String name = text(network, "name", "");
            if (!name.isEmpty()) result.add(name);
        }
        return result;
    }

    private static Map<Long, JSONObject> members(JSONObject game) {
        Map<Long, JSONObject> result = new HashMap<>();
        JSONArray array = game.optJSONArray("members");
        if (array == null) return result;

        for (int i = 0; i < array.length(); i++) {
            JSONObject member = array.optJSONObject(i);
            if (member == null) continue;

            long id = longValue(member, "id", 0);
            if (id > 0) result.put(id, member);
        }
        return result;
    }

    private static List<MatchEvent> events(JSONObject game,
                                           Map<Long, JSONObject> members,
                                           long homeId, long awayId) {
        JSONArray array = game.optJSONArray("events");
        if (array == null || array.length() == 0) return Collections.emptyList();

        List<MatchEvent> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject event = array.optJSONObject(i);
            if (event == null) continue;

            int minute = intValue(event, "gameTime", 0);
            int addedTime = intValue(event, "addedTime", 0);
            long playerId = longValue(event, "playerId", 0);

            String playerName = playerName(members, playerId);
            int playerNumber = playerNumber(members, playerId);
            String secondaryName = secondaryName(event, members);

            result.add(new MatchEvent(
                    intValue(event, "order", i + 1),
                    minute,
                    addedTime,
                    display(event, minute, addedTime),
                    type(event.optJSONObject("eventType")),
                    side(event, homeId, awayId),
                    playerName,
                    playerNumber,
                    secondaryName,
                    event.optBoolean("isMajor", false)));
        }

        result.sort(Comparator.comparingInt((MatchEvent event) -> event.order));
        return result;
    }

    private static String display(JSONObject event, int minute, int addedTime) {
        if (addedTime > 0) return minute + "+" + addedTime + "'";

        String display = text(event, "gameTimeDisplay", "");
        if (!display.isEmpty()) return display;
        return minute > 0 ? minute + "'" : "";
    }

    private static MatchEvent.Side side(JSONObject event, long homeId, long awayId) {
        long competitorId = longValue(event, "competitorId", 0);
        if (competitorId <= 0) return MatchEvent.Side.NEUTRAL;
        if (homeId > 0 && competitorId == homeId) return MatchEvent.Side.HOME;
        if (awayId > 0 && competitorId == awayId) return MatchEvent.Side.AWAY;
        return MatchEvent.Side.NEUTRAL;
    }

    private static String playerName(Map<Long, JSONObject> members, long playerId) {
        if (playerId <= 0) return "";
        JSONObject member = members.get(playerId);
        String name = text(member, "name", "");
        return name.isEmpty() ? "Jugador" : name;
    }

    private static int playerNumber(Map<Long, JSONObject> members, long playerId) {
        if (playerId <= 0) return 0;
        return jerseyNumber(members.get(playerId));
    }

    private static int jerseyNumber(JSONObject member) {
        if (member == null) return 0;
        int number = intValue(member, "jerseyNumber", Integer.MIN_VALUE);
        if (number != Integer.MIN_VALUE) return number;
        return intValue(member, "number", 0);
    }

    private static String secondaryName(JSONObject event, Map<Long, JSONObject> members) {
        JSONArray extra = event.optJSONArray("extraPlayers");
        if (extra == null || extra.length() == 0) return "";

        long id = extra.optLong(0, 0);
        return playerName(members, id);
    }

    private static MatchEvent.Type type(JSONObject eventType) {
        if (eventType == null) return MatchEvent.Type.OTHER;

        int id = intValue(eventType, "id", -1);
        String name = (text(eventType, "name", "") + " "
                + text(eventType, "subTypeName", "")).toLowerCase(Locale.ROOT);

        if (id == 1000 || contains(name, "substitution") || contains(name, "sustituci")
                || contains(name, "cambio")) {
            return MatchEvent.Type.SUBSTITUTION;
        }
        if (id == 11 || contains(name, "anulado") || contains(name, "disallowed")) {
            return MatchEvent.Type.DISALLOWED_GOAL;
        }
        if (contains(name, "var") || contains(name, "videoarbitro")
                || contains(name, "video arbitro")) {
            return MatchEvent.Type.VAR;
        }
        if (id == 3 || contains(name, "roja")) {
            return MatchEvent.Type.RED_CARD;
        }
        if (id == 2 || contains(name, "amarilla")) {
            if (contains(name, "segunda") || contains(name, "second yellow")) {
                return MatchEvent.Type.SECOND_YELLOW;
            }
            return MatchEvent.Type.YELLOW_CARD;
        }
        if (contains(name, "propia") || contains(name, "own goal")
                || contains(name, "autogol")) {
            return MatchEvent.Type.OWN_GOAL;
        }
        boolean missedPenalty = contains(name, "fallado") || contains(name, "missed");
        if (!missedPenalty && (contains(name, "penalt") || contains(name, "penalty"))) {
            return MatchEvent.Type.PENALTY_GOAL;
        }
        if (id == 1 || contains(name, "gol")) {
            return MatchEvent.Type.GOAL;
        }
        return MatchEvent.Type.OTHER;
    }

    private static boolean contains(String text, String needle) {
        return text.contains(needle);
    }

    private static TeamLineup lineup(JSONObject competitor,
                                     Map<Long, JSONObject> members,
                                     long teamIdFallback, String teamNameFallback) {
        if (competitor == null) return null;

        JSONObject lineups = competitor.optJSONObject("lineups");
        if (lineups == null) return null;

        List<LineupPlayer> starters = new ArrayList<>();
        List<LineupPlayer> substitutes = new ArrayList<>();
        List<LineupPlayer> missing = new ArrayList<>();
        String coach = "";

        JSONArray array = lineups.optJSONArray("members");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                int status = intValue(item, "status", 0);
                long playerId = longValue(item, "id", 0);
                JSONObject member = members.get(playerId);

                String rawName = text(member, "name", text(item, "name", ""));
                String name = rawName.isEmpty() ? "Jugador" : rawName;
                int number = member != null
                        ? jerseyNumber(member)
                        : jerseyNumber(item);

                if (status == 4) {
                    if (coach.isEmpty() && !rawName.isEmpty()) coach = rawName;
                    continue;
                }
                if (status < 1 || status > 3) continue;

                JSONObject position = item.optJSONObject("position");
                String positionName = position != null
                        ? text(position, "name", "")
                        : text(item, "position", "");
                String positionShort = text(position, "shortName", "");
                double rating = decimal(item, "ranking", -1);

                LineupPlayer player = new LineupPlayer(playerId, name, number,
                        positionName, positionShort, rating, status == 1);
                if (status == 1) {
                    starters.add(player);
                } else if (status == 2) {
                    substitutes.add(player);
                } else {
                    missing.add(player);
                }
            }
        }

        if (coach.isEmpty()) {
            JSONObject coachJson = lineups.optJSONObject("coach");
            coach = text(coachJson, "name", "");
        }

        return new TeamLineup(
                longValue(competitor, "id", teamIdFallback),
                text(competitor, "name", teamNameFallback),
                text(lineups, "formation", ""),
                text(lineups, "status", ""),
                coach,
                starters,
                substitutes,
                missing);
    }

    private static Stats stats(JSONObject statsRoot, long homeId, long awayId) {
        if (statsRoot == null) return Stats.EMPTY;
        if (!statsRoot.has("statistics") || statsRoot.isNull("statistics")) {
            return Stats.EMPTY;
        }

        JSONArray array = statsRoot.optJSONArray("statistics");
        if (array == null) {
            return new Stats(Collections.emptyList(), "Estadísticas no disponibles");
        }
        if (array.length() == 0) return Stats.EMPTY;

        try {
            Stats parsed = parseStats(array, homeId, awayId);
            if (parsed.categories.isEmpty()) {
                return new Stats(Collections.emptyList(), "Estadísticas no disponibles");
            }
            return parsed;
        } catch (Exception e) {
            return new Stats(Collections.emptyList(), "Estadísticas no disponibles");
        }
    }

    private static Stats parseStats(JSONArray array, long homeId, long awayId) {
        Map<String, CategoryBuilder> categories = new LinkedHashMap<>();
        int sequence = 0;

        for (int i = 0; i < array.length(); i++) {
            JSONObject stat = array.optJSONObject(i);
            if (stat == null) continue;

            long competitorId = longValue(stat, "competitorId", 0);
            boolean home = homeId > 0 && competitorId == homeId;
            boolean away = awayId > 0 && competitorId == awayId;
            if (!home && !away) continue;

            String categoryName = text(stat, "categoryName", "");
            if (categoryName.isEmpty()) categoryName = "General";

            CategoryBuilder category = categories.get(categoryName);
            if (category == null) {
                category = new CategoryBuilder(categoryName,
                        intValue(stat, "categoryOrder", 0));
                categories.put(categoryName, category);
            }

            int id = intValue(stat, "id", 0);
            String key = id > 0 ? "id:" + id : "seq:" + (sequence++);
            RowBuilder row = category.rows.get(key);
            if (row == null) {
                row = new RowBuilder(id, text(stat, "name", ""),
                        intValue(stat, "order", 0));
                category.rows.put(key, row);
            }
            if (row.name.isEmpty()) row.name = text(stat, "name", "");
            if (row.order == 0) row.order = intValue(stat, "order", 0);

            String value = text(stat, "value", "");
            double percent = decimal(stat, "valuePercentage", -1);
            if (home) {
                row.homeValue = value;
                row.homePercent = percent;
            } else {
                row.awayValue = value;
                row.awayPercent = percent;
            }
            row.major = row.major || stat.optBoolean("isMajor", false);
        }

        List<StatCategory> result = new ArrayList<>();
        for (CategoryBuilder category : categories.values()) {
            List<RowBuilder> builders = new ArrayList<>(category.rows.values());
            builders.sort(Comparator.comparingInt((RowBuilder row) -> row.order)
                    .thenComparing(row -> row.name));

            List<StatRow> rows = new ArrayList<>();
            for (RowBuilder row : builders) {
                rows.add(new StatRow(row.id, row.name, row.homeValue, row.awayValue,
                        row.homePercent, row.awayPercent, row.major));
            }
            result.add(new StatCategory(category.name, category.order, rows));
        }

        result.sort(Comparator.comparingInt((StatCategory category) -> category.order)
                .thenComparing(category -> category.name));
        return new Stats(result, "");
    }

    private static String text(JSONObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.isNull(key)) return fallback;
        String value = object.optString(key, fallback);
        if (value == null) return fallback;
        return value.trim();
    }

    private static int intValue(JSONObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.isNull(key)) return fallback;
        return object.optInt(key, fallback);
    }

    private static long longValue(JSONObject object, String key, long fallback) {
        if (object == null || !object.has(key) || object.isNull(key)) return fallback;
        return object.optLong(key, fallback);
    }

    private static double decimal(JSONObject object, String key, double fallback) {
        if (object == null || !object.has(key) || object.isNull(key)) return fallback;
        double value = object.optDouble(key, fallback);
        if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
        return value;
    }

    private static final class Stats {
        static final Stats EMPTY = new Stats(Collections.emptyList(), "");

        final List<StatCategory> categories;
        final String error;

        Stats(List<StatCategory> categories, String error) {
            this.categories = categories;
            this.error = error;
        }
    }

    private static final class CategoryBuilder {
        final String name;
        final int order;
        final Map<String, RowBuilder> rows = new LinkedHashMap<>();

        CategoryBuilder(String name, int order) {
            this.name = name;
            this.order = order;
        }
    }

    private static final class RowBuilder {
        final int id;
        String name;
        int order;
        String homeValue = "";
        String awayValue = "";
        double homePercent = -1;
        double awayPercent = -1;
        boolean major;

        RowBuilder(int id, String name, int order) {
            this.id = id;
            this.name = name;
            this.order = order;
        }
    }
}
