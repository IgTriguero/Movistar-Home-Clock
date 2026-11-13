package com.itriguero.homeclock;

import android.content.Context;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orquesta las fuentes, deduplica resultados, aplica caché y expone un
 * snapshot único para la UI.
 */
public final class MatchRepository {

    private static final long GLOBAL_TIMEOUT_SECONDS = 12L;
    private static final long DUPLICATE_WINDOW_MS = 2L * 60L * 60L * 1000L;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static final Comparator<Match> SORT = (a, b) -> {
        int ra = rank(a);
        int rb = rank(b);
        if (ra != rb) return ra - rb;
        if (isFinished(a)) return Long.compare(b.startMillis, a.startMillis);
        return Long.compare(a.startMillis, b.startMillis);
    };

    private final List<MatchSource> sources;
    private final MatchCache cache;
    private final ExecutorService sourceExecutor;
    private final Context logoContext;

    public MatchRepository(Context context) {
        this(new ArrayList<>(java.util.Arrays.asList(
                        new Scores365Source(),
                        new RfefSource(context),
                        new CeltaSource(context))),
                new MatchCache(context),
                Executors.newFixedThreadPool(3),
                context);
    }

    MatchRepository(List<MatchSource> sources, MatchCache cache,
                    ExecutorService sourceExecutor, Context logoContext) {
        this.sources = sources == null
                ? Collections.emptyList() : new ArrayList<>(sources);
        this.cache = cache;
        this.sourceExecutor = sourceExecutor;
        this.logoContext = logoContext;
    }

    public MatchesSnapshot fetchBlocking(LocalDate date) {
        MatchesSnapshot cached = cache == null ? null : cache.load(date);

        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(GLOBAL_TIMEOUT_SECONDS);
        Map<String, Future<SourceResult<List<Match>>>> futures = new LinkedHashMap<>();
        Map<String, Long> startedAt = new HashMap<>();
        Map<String, SourceStatus> statuses = new LinkedHashMap<>();

        for (MatchSource source : sources) {
            if (source == null || source.id() == null) continue;

            String id = source.id();
            long start = System.nanoTime();
            startedAt.put(id, start);
            statuses.put(id, null);

            try {
                Future<SourceResult<List<Match>>> future =
                        sourceExecutor.submit(() -> source.fetch(date));
                futures.put(id, future);
            } catch (Exception e) {
                statuses.put(id, new SourceStatus(
                        id, SourceState.ERROR, message(e),
                        System.currentTimeMillis(), elapsedMs(start)));
            }
        }

        List<Match> collected = new ArrayList<>();
        boolean anySuccess = false;

        for (Map.Entry<String, Future<SourceResult<List<Match>>>> entry
                : futures.entrySet()) {
            String id = entry.getKey();
            Future<SourceResult<List<Match>>> future = entry.getValue();
            long start = startedAt.containsKey(id) ? startedAt.get(id) : System.nanoTime();

            SourceResult<List<Match>> result;
            try {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new TimeoutException("Tiempo global agotado");
                }
                result = future.get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                statuses.put(id, new SourceStatus(
                        id, SourceState.TIMEOUT, "Timeout",
                        System.currentTimeMillis(), elapsedMs(start)));
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                statuses.put(id, new SourceStatus(
                        id, SourceState.ERROR, "Interrumpido",
                        System.currentTimeMillis(), elapsedMs(start)));
                continue;
            } catch (CancellationException e) {
                statuses.put(id, new SourceStatus(
                        id, SourceState.ERROR, "Cancelado",
                        System.currentTimeMillis(), elapsedMs(start)));
                continue;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                Exception exception = cause instanceof Exception
                        ? (Exception) cause : new RuntimeException(cause);
                result = SourceResult.fromException(exception, start);
            }

            if (result == null) {
                statuses.put(id, new SourceStatus(
                        id, SourceState.ERROR, "Sin resultado",
                        System.currentTimeMillis(), elapsedMs(start)));
                continue;
            }

            SourceState state = result.state == null
                    ? SourceState.ERROR : result.state;
            statuses.put(id, new SourceStatus(
                    id, state, result.error, result.fetchedAt, result.durationMs,
                    result.fromCache, 0));

            if (state == SourceState.OK || state == SourceState.EMPTY) {
                anySuccess = true;
            }
            if (state == SourceState.OK && result.data != null) {
                collected.addAll(result.data);
            }
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<String, SourceStatus> entry : statuses.entrySet()) {
            if (entry.getValue() == null) {
                entry.setValue(new SourceStatus(
                        entry.getKey(), SourceState.ERROR, "Sin resultado", now, 0));
            }
        }

        boolean hasErrors = hasErrors(statuses);

        if (!anySuccess) {
            if (cached != null) {
                return cached.withStale(staleStatuses(statuses, cached));
            }
            fillMatchCounts(Collections.emptyList(), statuses);
            return new MatchesSnapshot(
                    date, Collections.emptyList(), statuses, now, false);
        }

        // Si una fuente falla, no se debe perder lo que ya teníamos de ella.
        // Se reutilizan sus partidos cacheados y se marca el snapshot stale.
        boolean usedCached = false;
        List<Match> merged = merge(collected);
        Map<String, Integer> reusedBySource = new HashMap<>();
        if (cached != null && hasErrors) {
            if (merged.isEmpty() && !cached.matches.isEmpty()) {
                return cached.withStale(staleStatuses(statuses, cached));
            }

            for (Match cachedMatch : cached.matches) {
                if (cachedMatch == null || cachedMatch.sourceId == null) continue;
                SourceStatus status = statuses.get(cachedMatch.sourceId);
                if (status == null || status.state == SourceState.OK
                        || status.state == SourceState.EMPTY) {
                    continue;
                }
                // El dedupe es por cluster (±2 h / TBD mismo día), no por una
                // clave exacta: un partido cacheado puede ser el mismo que uno
                // que acaba de llegar de otra fuente con otra hora.
                if (containsCluster(merged, cachedMatch)) continue;

                merged.add(cachedMatch);
                usedCached = true;
                reusedBySource.merge(cachedMatch.sourceId, 1, Integer::sum);
            }
            if (usedCached) {
                merged = merge(merged);
                for (Map.Entry<String, Integer> entry : reusedBySource.entrySet()) {
                    SourceStatus status = statuses.get(entry.getKey());
                    if (status != null) {
                        statuses.put(entry.getKey(),
                                status.withCache(entry.getValue()));
                    }
                }
            }
        }

        if (logoContext != null) {
            fillLogosWithinBudget(merged, deadline);
        }

        fillMatchCounts(merged, statuses);

        MatchesSnapshot snapshot = new MatchesSnapshot(
                date, merged, statuses, System.currentTimeMillis(), usedCached);
        // Solo se pisa la caché con un ciclo sin errores; un fallo parcial no
        // debe convertir una lista completa anterior en una lista incompleta.
        if (cache != null && !hasErrors) cache.save(snapshot);
        return snapshot;
    }

    /**
     * Estados para un snapshot servido íntegramente desde la caché. Marca
     * {@code fromCache=true} en las fuentes con partidos cacheados y conserva
     * el error de este intento para las demás.
     */
    private static Map<String, SourceStatus> staleStatuses(
            Map<String, SourceStatus> current, MatchesSnapshot cached) {
        Map<String, SourceStatus> result = new LinkedHashMap<>();
        if (current != null) result.putAll(current);

        Map<String, Integer> counts = countBySource(cached.matches);
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String sourceId = entry.getKey();
            int count = entry.getValue();
            if (count <= 0) continue;

            SourceStatus status = result.get(sourceId);
            if (status != null) {
                result.put(sourceId, status.withCache(count));
            } else {
                result.put(sourceId, new SourceStatus(
                        sourceId, SourceState.ERROR, "Sin conexión",
                        cached.fetchedAt, 0L, true, count));
            }
        }
        return result;
    }

    /**
     * Rellena el número de partidos de cada fuente en el snapshot final,
     * conservando su estado y su marca de caché.
     */
    private static void fillMatchCounts(List<Match> matches,
                                        Map<String, SourceStatus> statuses) {
        Map<String, Integer> counts = countBySource(matches);
        for (Map.Entry<String, SourceStatus> entry : statuses.entrySet()) {
            SourceStatus status = entry.getValue();
            if (status == null) continue;
            Integer count = counts.get(entry.getKey());
            entry.setValue(status.withMatchCount(count == null ? 0 : count));
        }
    }

    private static Map<String, Integer> countBySource(List<Match> matches) {
        Map<String, Integer> counts = new HashMap<>();
        if (matches == null) return counts;
        for (Match match : matches) {
            if (match == null || match.sourceId == null
                    || match.sourceId.isEmpty()) {
                continue;
            }
            counts.merge(match.sourceId, 1, Integer::sum);
        }
        return counts;
    }

    private static boolean hasErrors(Map<String, SourceStatus> statuses) {
        for (SourceStatus status : statuses.values()) {
            if (status != null && status.state != SourceState.OK
                    && status.state != SourceState.EMPTY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Completa escudos sin romper el presupuesto global de ~12 s. La llamada
     * a CeltaClient se ejecuta en el pool y se cancela si se agota el tiempo.
     */
    private void fillLogosWithinBudget(List<Match> matches, long deadline) {
        Future<?> future;
        try {
            future = sourceExecutor.submit(() -> {
                try {
                    CeltaClient.fillLeagueLogos(logoContext, matches);
                } catch (Exception ignored) {
                    // Los escudos son un extra: nunca invalidan los partidos.
                }
            });
        } catch (Exception e) {
            return;
        }

        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                future.cancel(true);
                return;
            }
            future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
        } catch (ExecutionException | CancellationException ignored) {
            // Sin escudos: el resto del snapshot sigue siendo válido.
        }
    }

    public void close() {
        sourceExecutor.shutdownNow();
    }

    private static List<Match> merge(List<Match> matches) {
        List<Match> clusters = new ArrayList<>();
        for (Match match : matches) {
            if (match == null) continue;

            int index = clusterIndex(clusters, match);
            if (index < 0) {
                clusters.add(match);
                continue;
            }

            Match current = clusters.get(index);
            Match winner = better(match, current) ? match : current;
            Match loser = winner == match ? current : match;
            fillMissingLogos(winner, loser);
            clusters.set(index, winner);
        }

        Collections.sort(clusters, SORT);
        return clusters;
    }

    /**
     * Agrupa partidos de la misma pareja sin importar el orden local/visitante.
     * Con hora conocida se toleran ±2 h (las fuentes redondean distinto); si
     * alguna fuente no da hora, basta con que sea el mismo día local. Sin
     * nombres solo se puede comparar id + fuente.
     */
    private static boolean sameCluster(Match a, Match b) {
        String aHome = normalizeName(a.homeName);
        String aAway = normalizeName(a.awayName);
        String bHome = normalizeName(b.homeName);
        String bAway = normalizeName(b.awayName);

        boolean aNamed = !aHome.isEmpty() && !aAway.isEmpty();
        boolean bNamed = !bHome.isEmpty() && !bAway.isEmpty();
        if (!aNamed || !bNamed) {
            return a.id == b.id && sameSource(a.sourceId, b.sourceId);
        }

        boolean samePair = (aHome.equals(bHome) && aAway.equals(bAway))
                || (aHome.equals(bAway) && aAway.equals(bHome));
        if (!samePair) return false;

        if (a.timeTbd || b.timeTbd) {
            return sameLocalDate(a.startMillis, b.startMillis);
        }
        return Math.abs(a.startMillis - b.startMillis) <= DUPLICATE_WINDOW_MS;
    }

    private static int clusterIndex(List<Match> clusters, Match candidate) {
        for (int i = 0; i < clusters.size(); i++) {
            if (sameCluster(clusters.get(i), candidate)) return i;
        }
        return -1;
    }

    private static boolean containsCluster(List<Match> matches, Match candidate) {
        return clusterIndex(matches, candidate) >= 0;
    }

    private static boolean sameSource(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean sameLocalDate(long firstMillis, long secondMillis) {
        LocalDate first = Instant.ofEpochMilli(firstMillis)
                .atZone(MADRID).toLocalDate();
        LocalDate second = Instant.ofEpochMilli(secondMillis)
                .atZone(MADRID).toLocalDate();
        return first.equals(second);
    }

    private static String normalizeName(String raw) {
        if (raw == null) return "";
        String text = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        return text;
    }

    private static boolean better(Match candidate, Match current) {
        int candidateRank = rank(candidate);
        int currentRank = rank(current);
        if (candidateRank != currentRank) return candidateRank < currentRank;
        if (candidate.scoreKnown != current.scoreKnown) return candidate.scoreKnown;
        return sourcePriority(candidate.sourceId) > sourcePriority(current.sourceId);
    }

    private static void fillMissingLogos(Match winner, Match loser) {
        boolean swapped = namesSwapped(winner, loser);
        String loserHomeLogo = swapped ? loser.awayLogoUrl : loser.homeLogoUrl;
        String loserAwayLogo = swapped ? loser.homeLogoUrl : loser.awayLogoUrl;

        if (isBlank(winner.homeLogoUrl) && !isBlank(loserHomeLogo)) {
            winner.homeLogoUrl = loserHomeLogo;
        }
        if (isBlank(winner.awayLogoUrl) && !isBlank(loserAwayLogo)) {
            winner.awayLogoUrl = loserAwayLogo;
        }
    }

    /**
     * Detecta si la misma pareja viene con local/visitante invertidos, para
     * no cruzar los escudos al completarlos.
     */
    private static boolean namesSwapped(Match a, Match b) {
        String aHome = normalizeName(a.homeName);
        String aAway = normalizeName(a.awayName);
        String bHome = normalizeName(b.homeName);
        String bAway = normalizeName(b.awayName);

        if (aHome.equals(bHome) && aAway.equals(bAway)) return false;
        return aHome.equals(bAway) && aAway.equals(bHome);
    }

    private static int sourcePriority(String sourceId) {
        if (Scores365Source.ID.equals(sourceId)) return 3;
        if (CeltaSource.ID.equals(sourceId)) return 2;
        if (RfefSource.ID.equals(sourceId)) return 1;
        return 0;
    }

    private static int rank(Match match) {
        return match.status == null ? 5 : match.status.rank();
    }

    private static boolean isFinished(Match match) {
        return match.status != null && match.status.isFinished();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
