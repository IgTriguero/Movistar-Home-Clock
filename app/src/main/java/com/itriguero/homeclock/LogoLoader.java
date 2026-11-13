package com.itriguero.homeclock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cargador de escudos muy ligero: memoria + disco. Sin librerías externas.
 *
 * <p>Las descargas se serializan por URL: dos escudos distintos se descargan
 * en paralelo, pero el mismo escudo no se pide dos veces a la vez. Los fallos
 * se recuerdan en {@link LogoNegativeCache} para no reintentar una URL rota
 * durante unos minutos.
 */
public final class LogoLoader {

    private static final int MAX_MEMORY = 8 * 1024 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) HomeClock/1.0";
    private static final String DISK_DIR = "logos";

    private static final LruCache<String, Bitmap> MEMORY =
            new LruCache<String, Bitmap>(MAX_MEMORY) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    /**
     * Un lock por URL. La entrada se retira en el {@code finally} para que el
     * mapa no crezca sin límite; la comprobación de identidad evita que dos
     * hilos usen locks distintos para la misma URL durante esa limpieza.
     */
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private static final LogoNegativeCache NEGATIVE = new LogoNegativeCache();

    private static final AtomicInteger MEMORY_HITS = new AtomicInteger();
    private static final AtomicInteger DISK_HITS = new AtomicInteger();
    private static final AtomicInteger DOWNLOADS = new AtomicInteger();
    private static final AtomicInteger FAILURES = new AtomicInteger();
    private static final AtomicInteger NEGATIVE_HITS = new AtomicInteger();

    private LogoLoader() {
    }

    /**
     * Devuelve el escudo si ya está en memoria. También devuelve {@code null}
     * si la URL está en la caché negativa, aunque hubiera una imagen en
     * memoria (no debería ocurrir: solo se marca en fallo si no hay imagen).
     */
    public static Bitmap peek(String url) {
        if (url == null || url.isEmpty()) return null;

        if (NEGATIVE.isFailed(url, System.currentTimeMillis())) {
            NEGATIVE_HITS.incrementAndGet();
            return null;
        }

        Bitmap cached = MEMORY.get(url);
        if (cached != null) MEMORY_HITS.incrementAndGet();
        return cached;
    }

    /**
     * Carga el escudo de memoria, disco o red, en ese orden. Las URL distintas
     * no se bloquean entre sí. No reintenta una URL en la caché negativa hasta
     * que expire su TTL.
     */
    public static Bitmap load(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return null;

        Bitmap cached = MEMORY.get(url);
        if (cached != null) {
            MEMORY_HITS.incrementAndGet();
            return cached;
        }

        if (NEGATIVE.isFailed(url, System.currentTimeMillis())) {
            NEGATIVE_HITS.incrementAndGet();
            return null;
        }

        while (true) {
            Object lock = LOCKS.computeIfAbsent(url, key -> new Object());
            synchronized (lock) {
                // Si el lock se retiró mientras esperábamos, otro hilo pudo
                // crear uno nuevo: reintentar con el vigente evita descargar
                // la misma URL dos veces en paralelo.
                if (LOCKS.get(url) != lock) continue;

                try {
                    return loadLocked(context, url);
                } finally {
                    LOCKS.remove(url, lock);
                }
            }
        }
    }

    /** Borra la caché de escudos en disco ({@code cacheDir/logos}). */
    public static void clearDiskCache(Context context) {
        if (context == null) return;
        File cacheDir = context.getCacheDir();
        if (cacheDir == null) return;
        deleteRecursively(new File(cacheDir, DISK_DIR));
    }

    /** Olvida los fallos recordados, de modo que las URL se vuelven a probar. */
    public static void clearNegativeCache() {
        NEGATIVE.clear();
    }

    /** Vacía la caché en memoria para forzar una recarga desde disco/red. */
    public static void clearMemoryCache() {
        MEMORY.evictAll();
    }

    /** Contadores acumulados desde el arranque del proceso, para diagnóstico. */
    public static Stats stats() {
        return new Stats(
                MEMORY_HITS.get(),
                DISK_HITS.get(),
                DOWNLOADS.get(),
                FAILURES.get(),
                NEGATIVE_HITS.get());
    }

    private static Bitmap loadLocked(Context context, String url) {
        // Doble comprobación: otro hilo pudo terminar mientras esperábamos.
        Bitmap cached = MEMORY.get(url);
        if (cached != null) {
            MEMORY_HITS.incrementAndGet();
            return cached;
        }

        if (NEGATIVE.isFailed(url, System.currentTimeMillis())) {
            NEGATIVE_HITS.incrementAndGet();
            return null;
        }

        File cacheDir = context.getCacheDir();
        if (cacheDir == null) {
            fail(url);
            return null;
        }

        File dir = new File(cacheDir, DISK_DIR);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            fail(url);
            return null;
        }

        File file = new File(dir, sha1(url) + ".img");
        Bitmap bitmap = decode(file);
        if (bitmap != null) {
            MEMORY.put(url, bitmap);
            DISK_HITS.incrementAndGet();
            return bitmap;
        }

        bitmap = download(url);
        if (bitmap != null) {
            save(bitmap, file);
            MEMORY.put(url, bitmap);
            DOWNLOADS.incrementAndGet();
            return bitmap;
        }

        fail(url);
        return null;
    }

    private static void fail(String url) {
        NEGATIVE.markFailed(url, System.currentTimeMillis());
        FAILURES.incrementAndGet();
    }

    private static Bitmap decode(File file) {
        if (!file.exists()) return null;
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap download(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", USER_AGENT);

            int code = conn.getResponseCode();
            if (code != 200) return null;

            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void save(Bitmap bitmap, File file) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
            // Si no se puede cachear, se mantiene solo en memoria.
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;

        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    /** Instantánea inmutable de los contadores de {@link LogoLoader}. */
    public static final class Stats {

        public final int memoryHits;
        public final int diskHits;
        public final int downloads;
        public final int failures;
        public final int negativeHits;

        public Stats(int memoryHits, int diskHits, int downloads,
                     int failures, int negativeHits) {
            this.memoryHits = memoryHits;
            this.diskHits = diskHits;
            this.downloads = downloads;
            this.failures = failures;
            this.negativeHits = negativeHits;
        }

        @Override
        public String toString() {
            return "Stats{memoryHits=" + memoryHits
                    + ", diskHits=" + diskHits
                    + ", downloads=" + downloads
                    + ", failures=" + failures
                    + ", negativeHits=" + negativeHits
                    + '}';
        }
    }
}
