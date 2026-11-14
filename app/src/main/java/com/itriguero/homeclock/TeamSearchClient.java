package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Búsqueda de equipos en el API público (no oficial) de 365Scores.
 *
 * Solo fútbol y baloncesto: son los deportes que muestra HomeClock. Las
 * competiciones se piden en una segunda llamada best-effort, porque la
 * búsqueda no las incluye y sin ellas la fila de Ajustes quedaría coja.
 *
 * Igual que {@link MatchesClient}, no usa Android para poder validarse en la
 * JVM con fixtures y en el contrato diario de fuentes.
 */
final class TeamSearchClient {

    private static final String SEARCH_BASE = "https://webws.365scores.com/web/search/";
    private static final String COMPETITIONS_BASE =
            "https://webws.365scores.com/web/competitions/";
    private static final String COMPETITORS_BASE =
            "https://webws.365scores.com/web/competitors/";
    private static final String COMMON_QUERY = "?appTypeId=5&langId=14";
    private static final String USER_AGENT = "HomeClock/1.0";
    private static final String ACCEPT_LANGUAGE = "es-ES,es;q=0.9";

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final int COMPETITIONS_TIMEOUT_MS = 4_000;
    private static final int MAX_RESPONSE_CHARS = 2 * 1024 * 1024;
    private static final int MAX_RESULTS = 12;
    private static final String SELECTION_COMPETITION = "Selección";

    /**
     * As Celtas no existe en 365Scores; se ofrece como entrada curada local
     * para que el buscador no parezca incompleto.
     */
    private static final Set<String> CURATED_AS_CELTAS = new HashSet<>();

    static {
        Collections.addAll(CURATED_AS_CELTAS,
                "celta", "celtas", "as celtas", "celta femenino",
                "as celtas femenino");
    }

    private TeamSearchClient() {
    }

    /**
     * Busca equipos por nombre. Menos de dos caracteres se rechaza antes de
     * tocar la red: una consulta vacía devuelve el catálogo completo.
     */
    static List<TrackedTeam> search(String query) throws TeamSearchException {
        String normalized = normalizeQuery(query);
        if (normalized.codePointCount(0, normalized.length()) < 2) {
            throw new TeamSearchException(TeamSearchException.Kind.INVALID_QUERY, 0,
                    "La búsqueda necesita al menos dos caracteres");
        }

        String body = httpGet(buildUrl(normalized),
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        List<Candidate> candidates = parseCandidates(body);

        // La competición de cada club es información secundaria: si la segunda
        // petición falla se devuelven los equipos con la competición vacía.
        TreeSet<Long> competitionIds = new TreeSet<>();
        for (Candidate candidate : candidates) {
            if (candidate.type == 1 && candidate.mainCompetitionId > 0) {
                competitionIds.add(candidate.mainCompetitionId);
            }
        }

        Map<Long, String> competitionNames = Collections.emptyMap();
        if (!competitionIds.isEmpty()) {
            try {
                String competitionsJson = httpGet(
                        buildCompetitionsUrl(competitionIds),
                        COMPETITIONS_TIMEOUT_MS, COMPETITIONS_TIMEOUT_MS);
                competitionNames = parseCompetitions(competitionsJson);
            } catch (TeamSearchException ignored) {
                // Best-effort: la búsqueda sigue sin nombres de competición.
            }
        }

        return toTeams(candidates, competitionNames);
    }

    /**
     * As Celtas no está en 365Scores. Se reconoce por nombre, sin red, y solo
     * para las variantes que el usuario puede escribir en el buscador.
     */
    static List<TrackedTeam> curated(String query) {
        String normalized = stripAccents(normalizeQuery(query).toLowerCase(Locale.ROOT));
        if (normalized.isEmpty()) return Collections.emptyList();

        boolean matches = CURATED_AS_CELTAS.contains(normalized)
                || normalized.startsWith("as celtas ");
        if (!matches) return Collections.emptyList();
        return Collections.singletonList(TrackedTeam.asCeltas());
    }

    /** Lookup directo por ID, usado al restaurar o validar un equipo guardado. */
    static TrackedTeam fetchById(long id) throws TeamSearchException {
        if (id <= 0) {
            throw new TeamSearchException(TeamSearchException.Kind.INVALID_QUERY, 0,
                    "El identificador debe ser positivo");
        }
        String body = httpGet(buildCompetitorUrl(id),
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        return parseCompetitor(body, id);
    }

    static String buildUrl(String query) {
        return SEARCH_BASE + COMMON_QUERY + "&userCountryId=225&query="
                + encode(normalizeQuery(query));
    }

    /** IDs positivos, sin duplicados y ordenados; la coma se codifica. */
    static String buildCompetitionsUrl(Collection<Long> ids) {
        TreeSet<Long> sorted = new TreeSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && id > 0) sorted.add(id);
            }
        }
        StringBuilder csv = new StringBuilder();
        for (Long id : sorted) {
            if (csv.length() > 0) csv.append(',');
            csv.append(id);
        }
        return COMPETITIONS_BASE + COMMON_QUERY + "&competitions=" + encode(csv.toString());
    }

    static String buildCompetitorUrl(long id) {
        return COMPETITORS_BASE + COMMON_QUERY + "&competitors=" + id;
    }

    static List<TrackedTeam> parseSearch(String json) throws TeamSearchException {
        return parseSearch(json, Collections.emptyMap());
    }

    static List<TrackedTeam> parseSearch(String json,
                                         Map<Long, String> competitionNames)
            throws TeamSearchException {
        return toTeams(parseCandidates(json),
                competitionNames == null ? Collections.emptyMap() : competitionNames);
    }

    /**
     * Parsea el array de competiciones. A diferencia de la búsqueda, aquí un
     * JSON roto no debe romper el flujo: se devuelve un mapa vacío.
     */
    static Map<Long, String> parseCompetitions(String json) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) return result;

        try {
            JSONObject root = new JSONObject(json);
            JSONArray competitions = root.optJSONArray("competitions");
            if (competitions == null) return result;

            for (int i = 0; i < competitions.length(); i++) {
                JSONObject item = competitions.optJSONObject(i);
                if (item == null) continue;
                long id = item.optLong("id", 0);
                String name = item.optString("name", "").trim();
                if (id > 0 && !name.isEmpty()) result.put(id, name);
            }
        } catch (JSONException e) {
            return new LinkedHashMap<>();
        }
        return result;
    }

    /**
     * Lookup por ID: la respuesta trae el competidor, su país y todas sus
     * competiciones, así que no hace falta una segunda petición.
     */
    static TrackedTeam parseCompetitor(String json, long id) throws TeamSearchException {
        if (id <= 0) {
            throw new TeamSearchException(TeamSearchException.Kind.INVALID_QUERY, 0,
                    "El identificador debe ser positivo");
        }
        if (json == null || json.trim().isEmpty()) {
            throw new TeamSearchException(TeamSearchException.Kind.NOT_FOUND, 0,
                    "365Scores no devolvió datos para el competidor " + id);
        }

        try {
            JSONObject root = new JSONObject(json);
            Map<Integer, String> countries = idNameMap(root.optJSONArray("countries"));
            Map<Long, String> competitions = competitionMap(root.optJSONArray("competitions"));

            JSONObject match = null;
            JSONArray competitors = root.optJSONArray("competitors");
            if (competitors != null) {
                for (int i = 0; i < competitors.length(); i++) {
                    JSONObject item = competitors.optJSONObject(i);
                    if (item != null && item.optLong("id", 0) == id) {
                        match = item;
                        break;
                    }
                }
            }
            if (match == null) {
                throw new TeamSearchException(TeamSearchException.Kind.NOT_FOUND, 0,
                        "365Scores no conoce el competidor " + id);
            }

            int sportId = match.optInt("sportId", 0);
            int type = match.optInt("type", 0);
            String name = match.optString("name", "").trim();
            if (!isSupportedSport(sportId) || !isSupportedType(type) || name.isEmpty()) {
                throw new TeamSearchException(TeamSearchException.Kind.NOT_FOUND, 0,
                        "El competidor " + id + " no es un equipo de fútbol o baloncesto");
            }

            String country = countryName(countries, match.optInt("countryId", 0));
            String competition = competitionName(match, type, competitions);
            String shortName = TrackedTeam.autoShortName(
                    name, match.optString("shortName", ""));

            return new TrackedTeam(TrackedTeam.PROVIDER_365, id, sportId, name,
                    shortName, country, competition, TrackedTeam.logoUrl(id), true);
        } catch (JSONException e) {
            throw new TeamSearchException(TeamSearchException.Kind.PARSE, 0,
                    "Respuesta JSON inválida de 365Scores", e);
        }
    }

    private static List<Candidate> parseCandidates(String json) throws TeamSearchException {
        List<Candidate> candidates = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return candidates;

        try {
            JSONObject root = new JSONObject(json);
            // Los deportes válidos son fijos (1 y 2); de sports solo se
            // necesitaría el nombre, que TrackedTeam ya deriva del sportId.
            Map<Integer, String> countryNames = idNameMap(root.optJSONArray("countries"));

            JSONArray competitors = root.optJSONArray("competitors");
            if (competitors == null) {
                if (root.has("competitors") && !root.isNull("competitors")) {
                    throw new TeamSearchException(TeamSearchException.Kind.PARSE, 0,
                            "El campo competitors no es una lista");
                }
                return candidates;
            }

            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < competitors.length(); i++) {
                JSONObject item = competitors.optJSONObject(i);
                if (item == null) continue;

                long id = item.optLong("id", 0);
                int sportId = item.optInt("sportId", 0);
                int type = item.optInt("type", 0);
                String name = item.optString("name", "").trim();

                if (id <= 0 || name.isEmpty()) continue;
                if (item.optBoolean("hideOnSearch", false)) continue;
                if (!isSupportedSport(sportId) || !isSupportedType(type)) continue;
                if (!seen.add(id)) continue;

                candidates.add(new Candidate(
                        id,
                        sportId,
                        type,
                        name,
                        TrackedTeam.autoShortName(name, item.optString("shortName", "")),
                        countryName(countryNames, item.optInt("countryId", 0)),
                        item.optLong("popularityRank", 0),
                        item.optLong("mainCompetitionId", 0)));
            }

            return candidates;
        } catch (JSONException e) {
            throw new TeamSearchException(TeamSearchException.Kind.PARSE, 0,
                    "Respuesta JSON inválida de 365Scores", e);
        }
    }

    private static List<TrackedTeam> toTeams(List<Candidate> candidates,
                                             Map<Long, String> competitionNames) {
        if (candidates.isEmpty()) return new ArrayList<>();

        Set<String> footballNames = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (candidate.sportId == TrackedTeam.SPORT_FOOTBALL) {
                footballNames.add(nameKey(candidate.name));
            }
        }

        List<Candidate> sorted = new ArrayList<>(candidates);
        for (Candidate candidate : sorted) {
            if (candidate.sportId == TrackedTeam.SPORT_BASKETBALL
                    && footballNames.contains(nameKey(candidate.name))) {
                candidate.shortName = TrackedTeam.withBasketballSuffix(
                        candidate.shortName);
            }
        }

        Collections.sort(sorted, (a, b) -> {
            int sportA = a.sportId == TrackedTeam.SPORT_FOOTBALL ? 0 : 1;
            int sportB = b.sportId == TrackedTeam.SPORT_FOOTBALL ? 0 : 1;
            if (sportA != sportB) return sportA - sportB;
            return Long.compare(b.popularityRank, a.popularityRank);
        });

        List<TrackedTeam> teams = new ArrayList<>();
        for (Candidate candidate : sorted) {
            if (teams.size() >= MAX_RESULTS) break;

            String competition = candidate.type == 2
                    ? SELECTION_COMPETITION
                    : candidate.mainCompetitionId > 0
                            ? valueOrEmpty(competitionNames.get(candidate.mainCompetitionId))
                            : "";

            teams.add(new TrackedTeam(TrackedTeam.PROVIDER_365, candidate.id,
                    candidate.sportId, candidate.name, candidate.shortName,
                    candidate.country, competition,
                    TrackedTeam.logoUrl(candidate.id), true));
        }
        return teams;
    }

    private static boolean isSupportedSport(int sportId) {
        return sportId == TrackedTeam.SPORT_FOOTBALL
                || sportId == TrackedTeam.SPORT_BASKETBALL;
    }

    /** 1 club, 2 selección, 0/ausente desconocido. 16 leyendas queda fuera. */
    private static boolean isSupportedType(int type) {
        return type >= 0 && type <= 2;
    }

    private static Map<Integer, String> idNameMap(JSONArray array) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (array == null) return result;

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            int id = item.optInt("id", 0);
            String name = item.optString("name", "").trim();
            if (id != 0 && !name.isEmpty()) result.put(id, name);
        }
        return result;
    }

    private static Map<Long, String> competitionMap(JSONArray array) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (array == null) return result;

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            long id = item.optLong("id", 0);
            String name = item.optString("name", "").trim();
            if (id > 0 && !name.isEmpty()) result.put(id, name);
        }
        return result;
    }

    private static String countryName(Map<Integer, String> countries, int countryId) {
        if (countries == null) return "";
        return valueOrEmpty(countries.get(countryId));
    }

    private static String competitionName(JSONObject competitor, int type,
                                          Map<Long, String> competitions) {
        if (type == 2) return SELECTION_COMPETITION;
        long competitionId = competitor.optLong("mainCompetitionId", 0);
        if (competitionId <= 0 || competitions == null) return "";
        return valueOrEmpty(competitions.get(competitionId));
    }

    private static String nameKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeQuery(String query) {
        return query == null ? "" : query.trim().replaceAll("\\s+", " ");
    }

    private static String stripAccents(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 no disponible", e);
        }
    }

    private static String httpGet(String url, int connectTimeoutMs, int readTimeoutMs)
            throws TeamSearchException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);

            int code = conn.getResponseCode();
            if (code != 200) {
                throw new TeamSearchException(TeamSearchException.Kind.HTTP, code,
                        "HTTP " + code + " en " + url);
            }
            return readBody(conn);
        } catch (SocketTimeoutException e) {
            throw new TeamSearchException(TeamSearchException.Kind.TIMEOUT, 0,
                    "365Scores tardó demasiado en responder", e);
        } catch (IOException e) {
            // UnknownHostException, ConnectException y SSLException heredan de
            // IOException: desde el punto de vista del usuario es estar offline.
            throw new TeamSearchException(TeamSearchException.Kind.OFFLINE, 0,
                    "No se pudo conectar con 365Scores", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Límite duro para no cargar en memoria el catálogo completo del API. */
    private static String readBody(HttpURLConnection conn)
            throws IOException, TeamSearchException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[8_192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                body.append(buffer, 0, read);
                if (body.length() > MAX_RESPONSE_CHARS) {
                    throw new TeamSearchException(TeamSearchException.Kind.PARSE, 0,
                            "La respuesta de 365Scores supera el límite de 2 MB");
                }
            }
        }
        return body.toString();
    }

    static final class TeamSearchException extends Exception {
        enum Kind { INVALID_QUERY, NOT_FOUND, OFFLINE, TIMEOUT, HTTP, PARSE }

        final Kind kind;
        final int httpStatus;

        TeamSearchException(Kind kind, int httpStatus, String message) {
            super(message);
            this.kind = kind;
            this.httpStatus = httpStatus;
        }

        TeamSearchException(Kind kind, int httpStatus, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
            this.httpStatus = httpStatus;
        }
    }

    private static final class Candidate {
        final long id;
        final int sportId;
        final int type;
        final String name;
        String shortName;
        final String country;
        final long popularityRank;
        final long mainCompetitionId;

        Candidate(long id, int sportId, int type, String name, String shortName,
                  String country, long popularityRank, long mainCompetitionId) {
            this.id = id;
            this.sportId = sportId;
            this.type = type;
            this.name = name;
            this.shortName = shortName;
            this.country = country;
            this.popularityRank = popularityRank;
            this.mainCompetitionId = mainCompetitionId;
        }
    }
}
