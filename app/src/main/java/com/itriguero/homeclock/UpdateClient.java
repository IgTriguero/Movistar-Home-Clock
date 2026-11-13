package com.itriguero.homeclock;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Comprueba la última release publicada en GitHub. El manifiesto
 * `update.json` es un asset del release y contiene versión, URL del APK,
 * SHA-256 y notas. Sin Android: el parser se prueba con fixtures.
 */
final class UpdateClient {

    private static final int TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "HomeClock/" + BuildConfig.VERSION_NAME;

    private UpdateClient() {
    }

    static UpdateInfo fetch() throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(BuildConfig.UPDATE_MANIFEST_URL).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Cache-Control", "no-cache");

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

    static UpdateInfo parse(String json) throws JSONException {
        if (json == null || json.trim().isEmpty()) {
            throw new JSONException("Manifiesto vacío");
        }

        JSONObject root = new JSONObject(json);
        int versionCode = root.optInt("versionCode", -1);
        String versionName = root.optString("versionName", "").trim();
        String apkUrl = root.optString("apkUrl", "").trim();
        String sha256 = root.optString("sha256", "").trim().toLowerCase(Locale.ROOT);
        String notes = root.optString("notes", "").trim();

        if (versionCode <= 0) throw new JSONException("versionCode inválido");
        if (versionName.isEmpty()) throw new JSONException("versionName vacío");
        if (!apkUrl.startsWith("https://")) throw new JSONException("apkUrl inválida");
        if (!sha256.matches("[0-9a-f]{64}")) throw new JSONException("sha256 inválido");

        return new UpdateInfo(versionCode, versionName, apkUrl, sha256, notes);
    }

    static final class UpdateInfo {
        final int versionCode;
        final String versionName;
        final String apkUrl;
        final String sha256;
        final String notes;

        UpdateInfo(int versionCode, String versionName, String apkUrl, String sha256, String notes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.notes = notes;
        }

        boolean isNewerThan(int currentVersionCode) {
            return versionCode > currentVersionCode;
        }
    }
}
