package com.itriguero.homeclock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Descarga el APK de una release y comprueba su SHA-256 antes de instalarlo.
 */
final class UpdateDownloader {

    private static final int TIMEOUT_MS = 30_000;
    private static final String USER_AGENT = "HomeClock/" + BuildConfig.VERSION_NAME;

    private UpdateDownloader() {
    }

    static File download(String url, File destination) throws Exception {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("No se pudo crear " + parent);
        }
        File temp = new File(destination.getPath() + ".part");

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", USER_AGENT);

            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);

            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            if (destination.exists() && !destination.delete()) {
                throw new IOException("No se pudo reemplazar " + destination);
            }
            if (!temp.renameTo(destination)) {
                throw new IOException("No se pudo mover " + temp);
            }
            return destination;
        } finally {
            if (conn != null) conn.disconnect();
            if (temp.exists()) temp.delete();
        }
    }

    static boolean verify(File file, String expectedSha256) throws Exception {
        if (file == null || !file.isFile() || expectedSha256 == null) return false;
        String actual = sha256(file);
        return actual.equalsIgnoreCase(expectedSha256.trim());
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.US, "%02x", value));
        }
        return hex.toString();
    }
}
