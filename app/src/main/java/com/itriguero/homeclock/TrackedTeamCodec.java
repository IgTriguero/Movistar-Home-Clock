package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Codec puro del JSON de equipos ({@code teams_v1}). No conoce Android: se
 * puede probar en la JVM y reutilizar desde cualquier otra capa.
 *
 * {@link #decode(String)} nunca falla: ante datos ausentes, rotos o
 * incoherentes devuelve una lista válida y marca la reparación para que Prefs
 * pueda reescribir el valor una sola vez.
 */
final class TrackedTeamCodec {

    static final int VERSION = 1;

    private static final String KEY_VERSION = "version";
    private static final String KEY_TEAMS = "teams";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_ID = "id";
    private static final String KEY_SPORT_ID = "sportId";
    private static final String KEY_NAME = "name";
    private static final String KEY_SHORT_NAME = "shortName";
    private static final String KEY_COUNTRY = "country";
    private static final String KEY_COMPETITION = "competition";
    private static final String KEY_LOGO = "logo";
    private static final String KEY_ENABLED = "enabled";

    private static final long NO_ID = Long.MIN_VALUE;

    private TrackedTeamCodec() {
    }

    static Decoded decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaults(true, false, false);
        }

        JSONObject root;
        try {
            root = new JSONObject(raw);
        } catch (Exception e) {
            // JSON roto o que no es un objeto: se arranca con los defaults.
            return defaults(false, true, false);
        }

        boolean repaired = false;
        int version = root.optInt(KEY_VERSION, VERSION);
        if (version < VERSION) repaired = true;
        boolean readOnly = version > VERSION;

        JSONArray array = root.optJSONArray(KEY_TEAMS);
        if (array == null) {
            // Sin array no hay nada que conservar: defaults reparados.
            return defaults(false, true, readOnly);
        }

        List<TrackedTeam> parsed = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            TrackedTeam team = teamFromJson(array.optJSONObject(i));
            if (team == null || !team.isValid()) {
                repaired = true;
                continue;
            }
            if (!keys.add(team.key())) {
                // Duplicado: se conserva el primero.
                repaired = true;
                continue;
            }
            if (parsed.size() >= TrackedTeamList.MAX_TEAMS) {
                repaired = true;
                continue;
            }
            parsed.add(team);
        }

        if (parsed.isEmpty() && array.length() > 0) {
            // Había entradas pero ninguna válida: antes de dejar la lista vacía
            // se recuperan los equipos por defecto.
            return defaults(false, true, readOnly);
        }

        Decoded decoded = new Decoded();
        decoded.list = new TrackedTeamList(parsed, readOnly);
        decoded.migrated = false;
        decoded.repaired = repaired;
        decoded.readOnly = readOnly;
        return decoded;
    }

    /**
     * Serializa siempre con la forma actual. Es determinista para el mismo
     * contenido. Una lista vacía es válida; solo un {@code null} devuelve "".
     */
    static String encode(TrackedTeamList list) {
        if (list == null) return "";

        try {
            JSONObject root = new JSONObject();
            root.put(KEY_VERSION, VERSION);

            JSONArray array = new JSONArray();
            for (TrackedTeam team : list.teams()) {
                JSONObject item = new JSONObject();
                item.put(KEY_PROVIDER, team.provider);
                item.put(KEY_ID, team.competitorId);
                item.put(KEY_SPORT_ID, team.sportId);
                item.put(KEY_NAME, team.name);
                item.put(KEY_SHORT_NAME, team.shortName);
                item.put(KEY_COUNTRY, team.country);
                item.put(KEY_COMPETITION, team.competition);
                item.put(KEY_LOGO, team.logoUrl);
                item.put(KEY_ENABLED, team.enabled);
                array.put(item);
            }
            root.put(KEY_TEAMS, array);
            return root.toString();
        } catch (Exception e) {
            // Solo podría fallar con valores no serializables, que el modelo no
            // permite: todos los campos son texto, números o booleanos.
            return "";
        }
    }

    private static Decoded defaults(boolean migrated, boolean repaired, boolean readOnly) {
        Decoded decoded = new Decoded();
        decoded.list = new TrackedTeamList(TrackedTeam.defaults(), readOnly);
        decoded.migrated = migrated;
        decoded.repaired = repaired;
        decoded.readOnly = readOnly;
        return decoded;
    }

    private static TrackedTeam teamFromJson(JSONObject json) {
        if (json == null) return null;

        long id = json.optLong(KEY_ID, NO_ID);
        if (id == NO_ID) return null;

        String provider = string(json, KEY_PROVIDER);
        if (provider.isEmpty()) {
            // Compatibilidad con entradas antiguas o escritas a mano: el signo
            // del identificador distingue As Celtas de 365Scores.
            provider = id < 0
                    ? TrackedTeam.PROVIDER_AS_CELTAS
                    : TrackedTeam.PROVIDER_365;
        }

        return new TrackedTeam(
                provider,
                id,
                json.optInt(KEY_SPORT_ID, 0),
                string(json, KEY_NAME),
                string(json, KEY_SHORT_NAME),
                string(json, KEY_COUNTRY),
                string(json, KEY_COMPETITION),
                string(json, KEY_LOGO),
                json.optBoolean(KEY_ENABLED, true));
    }

    private static String string(JSONObject json, String key) {
        Object value = json.opt(key);
        return value instanceof String ? ((String) value).trim() : "";
    }

    static final class Decoded {
        TrackedTeamList list;
        boolean migrated;
        boolean repaired;
        boolean readOnly;
    }
}
