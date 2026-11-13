package com.itriguero.homeclock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Búsqueda de ciudades y pueblos con el geocoding público de Open-Meteo.
 * Sin Android ni claves: la respuesta se puede probar con fixtures.
 */
public final class CitySearchClient {

    private static final String BASE = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String USER_AGENT = "HomeClock/1.0";
    private static final int TIMEOUT_MS = 10_000;

    private CitySearchClient() {
    }

    public static List<Result> search(String query) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(buildUrl(query)).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);

            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            return parse(body.toString());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static String buildUrl(String query) {
        String value = query == null ? "" : query.trim();
        try {
            return BASE + "?name=" + URLEncoder.encode(value, "UTF-8")
                    + "&count=8&language=es&format=json";
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 no disponible", e);
        }
    }

    /**
     * Parsea la respuesta de Open-Meteo. Los resultados sin coordenadas se
     * descartan porque no permiten consultar el tiempo.
     */
    public static List<Result> parse(String json) throws JSONException {
        List<Result> results = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return results;

        JSONObject root = new JSONObject(json);
        JSONArray array = root.optJSONArray("results");
        if (array == null) return results;

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null || item.isNull("latitude") || item.isNull("longitude")) {
                continue;
            }
            double lat = item.optDouble("latitude", Double.NaN);
            double lon = item.optDouble("longitude", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

            results.add(new Result(
                    item.optString("name", "").trim(),
                    item.optString("admin1", "").trim(),
                    item.optString("country", "").trim(),
                    lat, lon));
        }
        return results;
    }

    public static final class Result {
        public final String name;
        public final String admin1;
        public final String country;
        public final double lat;
        public final double lon;

        public Result(String name, String admin1, String country, double lat, double lon) {
            this.name = name;
            this.admin1 = admin1;
            this.country = country;
            this.lat = lat;
            this.lon = lon;
        }
    }
}
