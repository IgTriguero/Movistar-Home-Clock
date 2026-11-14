package com.itriguero.homeclock;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Catálogo curado de emisoras por competición en España.
 *
 * <p>Objeto puro (sin Android): traduce la competición de 365Scores a una
 * etiqueta orientativa de la temporada {@link #SEASON} y a una URL de búsqueda
 * como respaldo. Los derechos cambian por jornada y edición, así que el
 * catálogo se revisa al inicio de cada temporada.
 *
 * <p>Las reglas se evalúan en orden para que las competiciones con nombre
 * parecido no caigan en la categoría equivocada ("Copa de la Reina" no es
 * "Copa del Rey", "Championship" no es "Champions", "LaLiga 2" no es LaLiga).
 */
final class BroadcastCatalog {

    static final String SEASON = "2026/27";
    static final String SEARCH_KEY = "search";

    private static final String GOOGLE_SEARCH = "https://www.google.com/search?q=";
    private static final Locale ES = new Locale("es", "ES");

    private BroadcastCatalog() {
    }

    /** Emisoras y búsqueda de respaldo para un partido. Nunca devuelve null. */
    static BroadcastInfo forMatch(Match match) {
        String competition = match == null ? "" : normalize(match.competition);
        String tracked = match == null ? "" : normalize(match.trackedName);
        // Si la fuente no publica competición, el nombre del equipo seguido es
        // la única pista disponible.
        String subject = competition.isEmpty() ? tracked : competition;
        String searchUrl = searchUrl(match);

        if (subject.contains("euroliga") || subject.contains("euroleague")) {
            return curated("euroliga", "Movistar Plus+",
                    "Exclusiva en España.", searchUrl);
        }

        if (subject.contains("copa de la reina")) {
            return curated("copa-de-la-reina", "RTVE Play / Teledeporte · RFEF TV",
                    "Emisión variable; consulta la guía.", searchUrl);
        }

        if (subject.contains("liga endesa") || subject.contains("acb")) {
            return curated("acb", "DAZN · Movistar Plus+ · Orange TV",
                    "DAZN es la plataforma principal; autonómicas emiten parte.",
                    searchUrl);
        }

        if (subject.contains("champions") && !subject.contains("championship")) {
            return curated("champions", "Movistar Plus+ · Orange TV",
                    "Competición completa en Movistar Plus+.", searchUrl);
        }

        if (subject.contains("premier")) {
            return curated("premier", "DAZN · Orange TV · Movistar Plus+",
                    "DAZN emite la Premier; Movistar Plus+ un partido destacado.",
                    searchUrl);
        }

        if (subject.contains("copa del rey")) {
            return curated("copa-del-rey", "Movistar Plus+ · RTVE · Orange TV",
                    "RTVE emite una selección en abierto.", searchUrl);
        }

        if (subject.contains("laliga")
                && !subject.contains("laliga 2")
                && !subject.contains("hypermotion")
                && !subject.contains("segunda")) {
            return curated("laliga", "Movistar Plus+ · DAZN · Orange TV",
                    "Reparto por jornada; hay un partido en abierto.", searchUrl);
        }

        if (isSegundaFederacion(subject)
                && (subject.contains("femenina")
                || tracked.contains("femenina")
                || tracked.contains("as celtas"))) {
            return curated("segunda-federacion-femenina", "YouTube: RFEF / RC Celta",
                    "Emisión seleccionada en canales oficiales.", searchUrl);
        }

        if (subject.contains("naciones")
                || subject.contains("nations league")
                || subject.contains("seleccion")) {
            return curated("seleccion", "La 1 · RTVE Play · DAZN",
                    "Partidos oficiales en abierto en RTVE.", searchUrl);
        }

        if (subject.contains("supercopa")) {
            return curated("supercopa", "Movistar Plus+ · Orange TV",
                    "Puede cambiar según la edición.", searchUrl);
        }

        if (subject.contains("europa league") || subject.contains("europa liga")) {
            return curated("europa-league", "Movistar Plus+ · Orange TV",
                    "Si el equipo cae de Champions.", searchUrl);
        }

        return search(searchUrl);
    }

    /**
     * Limpia el sufijo de dial que añade 365Scores: "DAZN LaLiga (M55 O113)"
     * se muestra como "DAZN LaLiga". Si al limpiar no queda texto, conserva el
     * original recortado.
     */
    static String cleanNetworkName(String raw) {
        if (raw == null) return "";
        String original = raw.trim();
        if (original.isEmpty()) return "";

        String cleaned = original.replaceAll("\\s+", " ").trim();
        String withoutSuffix = cleaned
                .replaceAll("(?:\\s*\\([^()]*\\))+\\s*$", "")
                .trim();
        return withoutSuffix.isEmpty() ? original : withoutSuffix;
    }

    private static BroadcastInfo curated(String key, String label, String detail,
                                         String searchUrl) {
        return new BroadcastInfo(key, label, detail, searchUrl, SEASON,
                BroadcastInfo.Source.CURATED);
    }

    /**
     * La RFEF publica "2ª Federación Femenina" y 365Scores puede escribir
     * "Segunda Federación"; se aceptan las dos formas.
     */
    private static boolean isSegundaFederacion(String subject) {
        return subject.contains("segunda federacion")
                || subject.contains("2 federacion")
                || subject.contains("2ª federacion");
    }

    private static BroadcastInfo search(String searchUrl) {
        return new BroadcastInfo(SEARCH_KEY, "Buscar dónde ver",
                "No hay una emisora fija para esta competición.",
                searchUrl, SEASON, BroadcastInfo.Source.SEARCH);
    }

    private static String searchUrl(Match match) {
        StringBuilder query = new StringBuilder("dónde ver");

        String home = match == null ? "" : trim(match.homeName);
        String away = match == null ? "" : trim(match.awayName);
        if (home.isEmpty() && away.isEmpty()) {
            home = match == null ? "" : trim(match.trackedName);
            if (home.isEmpty()) home = match == null ? "" : trim(match.competition);
        }
        append(query, home);
        append(query, away);

        if (match != null && match.startMillis > 0) {
            SimpleDateFormat format = new SimpleDateFormat("d MMM yyyy", ES);
            append(query, format.format(new Date(match.startMillis)));
        }

        append(query, "TV España");
        return GOOGLE_SEARCH + encode(query.toString());
    }

    private static void append(StringBuilder text, String part) {
        String value = part == null ? "" : part.trim();
        if (value.isEmpty()) return;
        text.append(' ').append(value);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 siempre está disponible en la JVM y en Android.
            return value;
        }
    }

    /**
     * Minúsculas, sin tildes, sin signos y con espacios colapsados para que
     * "Copa de la Reina", "COPA DE LA REINA" y "copa  de la reina" coincidan.
     */
    static String normalize(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String lower = decomposed.toLowerCase(Locale.ROOT);
        return lower.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
