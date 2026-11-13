package com.itriguero.homeclock;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UpdateClientTest {

    private static final String SHA =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Test
    public void parsesValidManifest() throws Exception {
        UpdateClient.UpdateInfo info = UpdateClient.parse(manifest(
                3, "2.1", "https://github.com/u/r/releases/download/v2.1/app.apk", SHA, "Notas"));

        assertEquals(3, info.versionCode);
        assertEquals("2.1", info.versionName);
        assertEquals(SHA, info.sha256);
        assertEquals("Notas", info.notes);
        assertTrue(info.isNewerThan(2));
    }

    @Test
    public void sameOrOlderVersionIsNotNewer() throws Exception {
        UpdateClient.UpdateInfo info = UpdateClient.parse(
                manifest(2, "2.0", "https://example.com/app.apk", SHA, ""));

        assertFalse(info.isNewerThan(2));
        assertFalse(info.isNewerThan(3));
    }

    @Test
    public void notesAreOptional() throws Exception {
        String json = "{\"versionCode\":2,\"versionName\":\"2.0\","
                + "\"apkUrl\":\"https://example.com/app.apk\",\"sha256\":\"" + SHA + "\"}";

        assertEquals("", UpdateClient.parse(json).notes);
    }

    @Test
    public void shaIsNormalizedToLowercase() throws Exception {
        UpdateClient.UpdateInfo info = UpdateClient.parse(
                manifest(2, "2.0", "https://example.com/app.apk", SHA.toUpperCase(), ""));

        assertEquals(SHA, info.sha256);
    }

    @Test
    public void rejectsInvalidVersionCode() {
        assertInvalid(manifest(0, "2.0", "https://example.com/app.apk", SHA, ""));
    }

    @Test
    public void rejectsEmptyVersionName() {
        assertInvalid(manifest(2, "", "https://example.com/app.apk", SHA, ""));
    }

    @Test
    public void rejectsNonHttpsUrl() {
        assertInvalid(manifest(2, "2.0", "http://example.com/app.apk", SHA, ""));
    }

    @Test
    public void rejectsInvalidSha() {
        assertInvalid(manifest(2, "2.0", "https://example.com/app.apk", "abc", ""));
    }

    @Test
    public void rejectsMalformedJson() {
        assertInvalid("{no es json");
    }

    @Test
    public void rejectsEmptyManifest() {
        assertInvalid("");
        assertInvalid(null);
    }

    private static void assertInvalid(String json) {
        try {
            UpdateClient.parse(json);
            fail("Debía rechazar el manifiesto");
        } catch (JSONException expected) {
            // correcto
        }
    }

    private static String manifest(int versionCode, String versionName,
                                   String apkUrl, String sha, String notes) {
        return "{\"versionCode\":" + versionCode
                + ",\"versionName\":\"" + versionName
                + "\",\"apkUrl\":\"" + apkUrl
                + "\",\"sha256\":\"" + sha
                + "\",\"notes\":\"" + notes + "\"}";
    }
}
