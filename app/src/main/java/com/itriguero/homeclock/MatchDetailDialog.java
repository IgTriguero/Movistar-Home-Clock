package com.itriguero.homeclock;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Modal con el detalle de un partido.
 *
 * <p>La cabecera, el marcador y el estado se pintan al instante desde el
 * {@link Match} de la fila; el resto se rellena cuando responde
 * {@link MatchDetailRepository}. Las pestañas de alineaciones y estadísticas se
 * reconstruyen en cada refresco conservando la posición de lectura.
 *
 * <p>El refresco en vivo se programa con {@link MatchDetailRefreshPolicy} y se
 * detiene al pausar la Activity o cerrar el diálogo. Sin AndroidX: solo
 * {@link Dialog} del framework.
 */
final class MatchDetailDialog {

    private static final String RFEF_URL =
            "https://rfef.es/es/resultados?competition=33836146&group=33836147";
    private static final String CELTA_URL =
            "https://rccelta.es/equipos/as-celtas/partidos/?par=132417";
    private static final String GOOGLE_SEARCH = "https://www.google.com/search?q=";

    private static final Locale ES = new Locale("es", "ES");
    private static final int SURFACE_STROKE = 0xFF2A2520;
    /** Fondo de la barra comparativa de estadísticas. */
    private static final int STAT_BAR_TRACK = 0xFF2A2A2A;
    /** Los suplentes publicados suelen ser 7-12; se limita la lista por seguridad. */
    private static final int MAX_SUBSTITUTES = 12;

    private final Activity activity;
    private final Match match;
    private final MatchDetailRepository repository;
    private final Dialog dialog;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ExecutorService logoExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "match-detail-logo");
        thread.setDaemon(true);
        return thread;
    });

    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat metaDateFormat =
            new SimpleDateFormat("EEE d MMM · HH:mm", ES);
    private final SimpleDateFormat searchDateFormat =
            new SimpleDateFormat("d MMM yyyy", ES);
    private final DateTimeFormatter shortDayFormat =
            DateTimeFormatter.ofPattern("d MMM", ES);
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(ES);

    private final TextView competition;
    private final TextView close;
    private final ImageView homeCrest;
    private final ImageView awayCrest;
    private final TextView homeName;
    private final TextView awayName;
    private final TextView score;
    private final TextView status;
    private final TextView meta;
    private final TextView footer;
    private final RadioGroup tabs;
    private final ScrollView summaryScroll;
    private final ScrollView lineupsScroll;
    private final ScrollView statsScroll;
    private final ScrollView tvScroll;
    private final LinearLayout summaryContent;
    private final LinearLayout lineupsContent;
    private final LinearLayout statsContent;
    private final LinearLayout tvContent;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            if (closed || paused || !dialog.isShowing()) return;
            loadDetail();
        }
    };

    private MatchDetailRepository.Request request;
    private MatchDetail detail;
    private Runnable onClosedListener;
    private int loadToken;
    private boolean paused;
    private boolean closed;

    MatchDetailDialog(Activity activity, Match match, MatchDetailRepository repository) {
        this.activity = activity;
        this.match = match == null ? new Match() : match;
        this.repository = repository;

        dialog = new Dialog(activity, R.style.Theme_HomeClock_Dialog);
        dialog.setContentView(R.layout.dialog_match_detail);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d -> onDismissed());

        competition = dialog.findViewById(R.id.detailCompetition);
        close = dialog.findViewById(R.id.detailClose);
        homeCrest = dialog.findViewById(R.id.detailHomeCrest);
        awayCrest = dialog.findViewById(R.id.detailAwayCrest);
        homeName = dialog.findViewById(R.id.detailHomeName);
        awayName = dialog.findViewById(R.id.detailAwayName);
        score = dialog.findViewById(R.id.detailScore);
        status = dialog.findViewById(R.id.detailStatus);
        meta = dialog.findViewById(R.id.detailMeta);
        footer = dialog.findViewById(R.id.detailFooter);
        tabs = dialog.findViewById(R.id.detailTabs);
        summaryScroll = dialog.findViewById(R.id.summaryScroll);
        lineupsScroll = dialog.findViewById(R.id.lineupsScroll);
        statsScroll = dialog.findViewById(R.id.statsScroll);
        tvScroll = dialog.findViewById(R.id.tvScroll);
        summaryContent = dialog.findViewById(R.id.summaryContent);
        lineupsContent = dialog.findViewById(R.id.lineupsContent);
        statsContent = dialog.findViewById(R.id.statsContent);
        tvContent = dialog.findViewById(R.id.tvContent);

        close.setOnClickListener(v -> dismiss());
        tabs.setOnCheckedChangeListener((group, checkedId) -> showTab(checkedId));

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.6f;
            params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            window.setAttributes(params);
        }

        prepareStaticContent();
    }

    void setOnClosedListener(Runnable listener) {
        onClosedListener = listener;
    }

    boolean isShowing() {
        return dialog != null && dialog.isShowing() && !closed;
    }

    void show() {
        if (dialog.isShowing()) return;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        closed = false;
        paused = false;
        bindHeader(null);

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        applyWindowFlags();

        if (isDetailSource()) {
            loadDetail();
        } else {
            renderUnsupportedSource();
        }
    }

    void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        } else {
            onDismissed();
        }
    }

    /** Detiene el refresco y descarta la petición en vuelo al pausar la Activity. */
    void onActivityPaused() {
        paused = true;
        handler.removeCallbacks(refreshTask);
        cancelRequest();
    }

    /** Reanuda el refresco con una carga inmediata al volver a primer plano. */
    void onActivityResumed() {
        if (closed) return;
        paused = false;
        if (!dialog.isShowing()) return;
        applyWindowFlags();
        loadDetail();
    }

    private void onDismissed() {
        if (closed) return;
        closed = true;
        cancelRequest();
        handler.removeCallbacks(refreshTask);
        logoExecutor.shutdownNow();

        Runnable listener = onClosedListener;
        onClosedListener = null;
        if (listener != null) listener.run();
    }

    /**
     * Copia el brillo de la ventana de la Activity (modo noche) y mantiene la
     * pantalla encendida si la Activity ya lo hacía.
     */
    private void applyWindowFlags() {
        Window window = dialog.getWindow();
        if (window == null) return;

        Window host = activity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        if (host != null) {
            WindowManager.LayoutParams hostParams = host.getAttributes();
            params.screenBrightness = hostParams.screenBrightness;
            if ((hostParams.flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0) {
                params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            }
            copyImmersiveMode(window, host);
        }
        params.dimAmount = 0.6f;
        params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(params);
    }

    /**
     * La Activity oculta las barras del sistema; el diálogo es otra ventana y
     * debe ocultarlas también para no romper el modo kiosco.
     */
    private static void copyImmersiveMode(Window target, Window host) {
        if (Build.VERSION.SDK_INT >= 30) {
            target.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = target.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            target.getDecorView().setSystemUiVisibility(
                    host.getDecorView().getSystemUiVisibility());
        }
    }

    // ---------------------------------------------------------------------
    // Carga
    // ---------------------------------------------------------------------

    private void prepareStaticContent() {
        tabs.check(R.id.tabSummary);
        showTab(R.id.tabSummary);

        summaryContent.removeAllViews();
        summaryContent.addView(messageView(R.string.detail_loading));
        lineupsContent.removeAllViews();
        lineupsContent.addView(messageView(R.string.detail_loading));
        statsContent.removeAllViews();
        statsContent.addView(messageView(R.string.detail_loading));
        tvContent.removeAllViews();
        tvContent.addView(messageView(R.string.detail_loading));
    }

    private void loadDetail() {
        if (closed || paused || repository == null) return;
        if (!isDetailSource()) return;
        if (!dialog.isShowing()) return;

        cancelRequest();
        final int token = ++loadToken;
        request = repository.load(match, true, result ->
                activity.runOnUiThread(() -> {
                    if (token != loadToken) return;
                    onLoaded(result);
                }));
    }

    private void cancelRequest() {
        loadToken++;
        MatchDetailRepository.Request current = request;
        request = null;
        if (current != null) current.cancel();
    }

    private void onLoaded(SourceResult<MatchDetail> result) {
        request = null;
        if (closed || !dialog.isShowing()) return;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        if (result == null) {
            renderError();
            renderLineups(null);
            renderStats(null);
            scheduleRefresh();
            return;
        }

        if (result.state == SourceState.OK && result.data != null) {
            detail = result.data;
            bindHeader(detail);
            renderSummary(detail);
            renderLineups(detail);
            renderStats(detail);
            renderTv(detail);
            renderFooter(detail, result, false);
            scheduleRefresh();
            return;
        }

        if (result.state == SourceState.EMPTY) {
            detail = null;
            bindHeader(null);
            renderNoDetailSource();
            renderLineups(null);
            renderStats(null);
            renderTvNetworks(null);
            renderFooter(null, result, false);
            scheduleRefresh();
            return;
        }

        detail = null;
        renderError();
        renderLineups(null);
        renderStats(null);
        renderTvNetworks(null);
        renderFooter(null, result, true);
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        handler.removeCallbacks(refreshTask);
        if (closed || paused || !dialog.isShowing()) return;

        long delay = MatchDetailRefreshPolicy.nextDelay(
                match, detail, System.currentTimeMillis());
        if (delay == Long.MAX_VALUE) return;
        if (delay < 1000L) delay = 1000L;
        handler.postDelayed(refreshTask, delay);
    }

    private boolean isDetailSource() {
        return match != null
                && Scores365Source.ID.equals(match.sourceId)
                && match.id > 0;
    }

    // ---------------------------------------------------------------------
    // Cabecera
    // ---------------------------------------------------------------------

    private void bindHeader(MatchDetail loaded) {
        String home = loaded != null && !loaded.homeName.isEmpty()
                ? loaded.homeName : safe(match.homeName);
        String away = loaded != null && !loaded.awayName.isEmpty()
                ? loaded.awayName : safe(match.awayName);

        String competitionText = loaded != null && !loaded.competition.isEmpty()
                ? loaded.competition : safe(match.competition);
        if (competitionText.isEmpty()) competitionText = safe(match.sport);
        competition.setText(competitionText);

        homeName.setText(home);
        awayName.setText(away);
        homeName.setContentDescription(activity.getString(R.string.detail_a11y_home, home));
        awayName.setContentDescription(activity.getString(R.string.detail_a11y_away, away));

        loadCrest(homeCrest, match.homeLogoUrl);
        loadCrest(awayCrest, match.awayLogoUrl);

        boolean scoreKnown;
        String scoreText;
        if (loaded != null) {
            scoreKnown = loaded.scoreKnown;
            scoreText = scoreKnown
                    ? loaded.homeScore + " - " + loaded.awayScore
                    : activity.getString(R.string.dash);
        } else {
            scoreKnown = match.scoreKnown && (match.isInPlay() || match.isFinished());
            scoreText = scoreKnown
                    ? Math.round(match.homeScore) + " - " + Math.round(match.awayScore)
                    : activity.getString(R.string.dash);
        }
        score.setText(scoreText);
        score.setContentDescription(scoreKnown
                ? activity.getString(R.string.detail_a11y_score, scoreText)
                : activity.getString(R.string.detail_a11y_score_none));

        String statusText = statusLine(loaded);
        status.setText(statusText);
        status.setContentDescription(activity.getString(R.string.detail_a11y_status, statusText));
        MatchStatus value = loaded != null ? loaded.status : match.status;
        if (value != null && value.isInPlay()) {
            status.setTextColor(activity.getColor(R.color.detail_live));
        } else if (value != null && value.isScheduled()) {
            status.setTextColor(activity.getColor(R.color.detail_accent));
        } else {
            status.setTextColor(activity.getColor(R.color.detail_text_secondary));
        }

        String metaText = metaLine(loaded);
        meta.setText(metaText);
        meta.setVisibility(metaText.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String statusLine(MatchDetail loaded) {
        MatchStatus value = loaded != null ? loaded.status : match.status;
        if (value == null) value = MatchStatus.UNKNOWN;

        String statusText = loaded != null ? loaded.statusText : safe(match.statusText);
        String gameTime = loaded != null
                ? loaded.gameTimeDisplay : safe(match.gameTimeDisplay);

        if (value.isInPlay()) {
            String lower = statusText.toLowerCase(ES);
            if (lower.contains("descanso") || lower.contains("half")) {
                return activity.getString(R.string.detail_status_break);
            }
            if (!gameTime.isEmpty() && !"live".equalsIgnoreCase(gameTime)) {
                return activity.getString(R.string.detail_status_live) + " · " + gameTime;
            }
            double elapsed = loaded != null ? 0 : match.gameTime;
            if (elapsed > 0) {
                return activity.getString(R.string.detail_status_live)
                        + " · " + Math.round(elapsed) + "'";
            }
            return activity.getString(R.string.detail_status_live);
        }

        if (value.isFinished()) {
            return activity.getString(R.string.detail_status_final);
        }

        if (value.isScheduled()) {
            if (match.timeTbd) return activity.getString(R.string.matches_hour_tbd);
            long start = loaded != null && loaded.startMillis > 0
                    ? loaded.startMillis : match.startMillis;
            if (start > 0) {
                return activity.getString(R.string.detail_scheduled,
                        scheduledDay(start), timeFormat.format(new Date(start)));
            }
            return activity.getString(R.string.matches_hour_tbd);
        }

        if (value.isPostponed()) {
            return activity.getString(R.string.matches_postponed).toUpperCase(ES);
        }
        if (value.isCancelled()) {
            return activity.getString(R.string.matches_cancelled).toUpperCase(ES);
        }

        return statusText.isEmpty() ? activity.getString(R.string.dash) : statusText;
    }

    private String scheduledDay(long startMillis) {
        LocalDate date = Instant.ofEpochMilli(startMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return activity.getString(R.string.today);
        if (date.equals(today.plusDays(1))) return activity.getString(R.string.tomorrow);
        if (date.equals(today.minusDays(1))) return activity.getString(R.string.yesterday);
        return date.format(shortDayFormat).toUpperCase(ES);
    }

    private String metaLine(MatchDetail loaded) {
        StringBuilder text = new StringBuilder();

        long start = loaded != null && loaded.startMillis > 0
                ? loaded.startMillis : match.startMillis;
        if (start > 0) {
            text.append(metaDateFormat.format(new Date(start)));
        }

        if (loaded != null && loaded.venue != null && !loaded.venue.name.isEmpty()) {
            appendPart(text, loaded.venue.name);
        }

        Official referee = firstOfficial(loaded == null ? null : loaded.officials);
        if (referee != null) {
            appendPart(text, activity.getString(
                    R.string.detail_official, referee.role, referee.name));
        }
        return text.toString();
    }

    // ---------------------------------------------------------------------
    // Resumen
    // ---------------------------------------------------------------------

    private void renderSummary(MatchDetail loaded) {
        int scrollY = summaryScroll.getScrollY();
        summaryContent.removeAllViews();

        List<MatchEvent> events = loaded.events;
        if (events == null || events.isEmpty()) {
            summaryContent.addView(messageView(noEventsMessage(loaded.status)));
        } else {
            summaryContent.addView(sectionHeader(R.string.detail_events_title));
            for (int i = 0; i < events.size(); i++) {
                summaryContent.addView(eventRow(loaded, events.get(i)));
                if (i < events.size() - 1) summaryContent.addView(divider());
            }
        }

        addVenueBlock(loaded);
        restoreScroll(summaryScroll, scrollY);
    }

    /** Mantiene la posición de lectura tras un refresco en vivo. */
    private void restoreScroll(ScrollView scrollView, int scrollY) {
        if (scrollY <= 0) return;
        scrollView.post(() -> scrollView.scrollTo(0, scrollY));
    }

    private int noEventsMessage(MatchStatus value) {
        if (value != null && value.isScheduled()) {
            return R.string.detail_no_events_scheduled;
        }
        if (value != null && value.isInPlay()) {
            return R.string.detail_no_events_live;
        }
        if (value != null && value.isFinished()) {
            return R.string.detail_no_events_finished;
        }
        return R.string.detail_no_events;
    }

    private View eventRow(MatchDetail loaded, MatchEvent event) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView minute = textView(event.display, 12, R.color.detail_accent);
        minute.setTypeface(null, Typeface.BOLD);
        minute.setGravity(Gravity.CENTER);
        row.addView(minute, new LinearLayout.LayoutParams(
                dp(52), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView label = textView(eventLabel(event.type), 11, R.color.detail_text_secondary);
        label.setAllCaps(true);
        label.setLetterSpacing(0.08f);
        body.addView(label);

        String player = event.playerName.trim().isEmpty()
                ? activity.getString(R.string.detail_player_unknown)
                : event.playerName;
        TextView playerView = textView(player, 15, R.color.detail_text_primary);
        if (event.major) playerView.setTypeface(null, Typeface.BOLD);
        body.addView(playerView);

        String team = eventTeam(loaded, event);
        if (!team.isEmpty()) {
            body.addView(textView(team, 12, R.color.detail_text_secondary));
        }

        if (event.type == MatchEvent.Type.SUBSTITUTION
                && !event.secondaryName.trim().isEmpty()) {
            body.addView(textView(activity.getString(
                    R.string.detail_event_secondary, event.secondaryName),
                    12, R.color.detail_text_secondary));
        }

        row.addView(body);
        return row;
    }

    private String eventTeam(MatchDetail loaded, MatchEvent event) {
        if (event.side == MatchEvent.Side.HOME) {
            return !loaded.homeName.isEmpty() ? loaded.homeName : safe(match.homeName);
        }
        if (event.side == MatchEvent.Side.AWAY) {
            return !loaded.awayName.isEmpty() ? loaded.awayName : safe(match.awayName);
        }
        return "";
    }

    private int eventLabel(MatchEvent.Type type) {
        if (type == null) return R.string.detail_event_other;
        switch (type) {
            case GOAL:
                return R.string.detail_event_goal;
            case PENALTY_GOAL:
                return R.string.detail_event_penalty;
            case OWN_GOAL:
                return R.string.detail_event_own_goal;
            case DISALLOWED_GOAL:
                return R.string.detail_event_disallowed;
            case YELLOW_CARD:
                return R.string.detail_event_yellow;
            case RED_CARD:
                return R.string.detail_event_red;
            case SECOND_YELLOW:
                return R.string.detail_event_second_yellow;
            case SUBSTITUTION:
                return R.string.detail_event_substitution;
            case VAR:
                return R.string.detail_event_var;
            default:
                return R.string.detail_event_other;
        }
    }

    private void addVenueBlock(MatchDetail loaded) {
        Venue venue = loaded.venue;
        boolean hasVenue = venue != null && !venue.name.isEmpty();
        boolean hasNumbers = venue != null
                && (venue.capacity > 0 || venue.attendance > 0);
        boolean hasOfficials = false;
        if (loaded.officials != null) {
            for (Official official : loaded.officials) {
                if (official != null && !official.name.isEmpty()) {
                    hasOfficials = true;
                    break;
                }
            }
        }
        if (!hasVenue && !hasNumbers && !hasOfficials) return;

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground());
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        if (hasVenue) {
            TextView name = textView(venue.name, 15, R.color.detail_text_primary);
            name.setTypeface(null, Typeface.BOLD);
            card.addView(name);
        }

        if (hasNumbers) {
            StringBuilder numbers = new StringBuilder();
            if (venue.capacity > 0) {
                numbers.append(activity.getString(
                        R.string.detail_venue_capacity, number(venue.capacity)));
            }
            if (venue.attendance > 0) {
                if (numbers.length() > 0) numbers.append(" · ");
                numbers.append(activity.getString(
                        R.string.detail_venue_attendance, number(venue.attendance)));
            }
            TextView value = textView(numbers.toString(), 13, R.color.detail_text_secondary);
            value.setPadding(0, dp(4), 0, 0);
            card.addView(value);
        }

        if (hasOfficials) {
            for (Official official : loaded.officials) {
                if (official == null || official.name.isEmpty()) continue;
                TextView value = textView(activity.getString(
                                R.string.detail_official, official.role, official.name),
                        13, R.color.detail_text_secondary);
                value.setPadding(0, dp(4), 0, 0);
                card.addView(value);
            }
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        summaryContent.addView(card, params);
    }

    // ---------------------------------------------------------------------
    // Alineaciones
    // ---------------------------------------------------------------------

    /**
     * Pinta las dos alineaciones en columnas dentro del ScrollView existente.
     * No hay scroll anidado: cada columna crece en vertical y el modal entero
     * se desplaza.
     */
    private void renderLineups(MatchDetail loaded) {
        int scrollY = lineupsScroll.getScrollY();
        lineupsContent.removeAllViews();

        if (loaded == null
                || (!hasStarters(loaded.homeLineup) && !hasStarters(loaded.awayLineup))) {
            lineupsContent.addView(messageView(R.string.detail_lineups_unavailable));
            restoreScroll(lineupsScroll, scrollY);
            return;
        }

        LinearLayout columns = new LinearLayout(activity);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setBaselineAligned(false);

        LinearLayout.LayoutParams homeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        homeParams.setMarginEnd(dp(8));
        LinearLayout.LayoutParams awayParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        awayParams.setMarginStart(dp(8));

        columns.addView(lineupColumn(loaded, loaded.homeLineup, true), homeParams);
        columns.addView(lineupColumn(loaded, loaded.awayLineup, false), awayParams);

        lineupsContent.addView(columns);
        restoreScroll(lineupsScroll, scrollY);
    }

    private static boolean hasStarters(TeamLineup lineup) {
        return lineup != null && !lineup.starters.isEmpty();
    }

    private LinearLayout lineupColumn(MatchDetail loaded, TeamLineup lineup, boolean home) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);

        TextView team = textView(teamName(loaded, lineup, home), 17, R.color.detail_text_primary);
        team.setTypeface(null, Typeface.BOLD);
        team.setMaxLines(2);
        team.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(team);

        String details = lineupDetails(lineup);
        if (!details.isEmpty()) {
            TextView subtitle = textView(details, 12, R.color.detail_text_secondary);
            subtitle.setMaxLines(2);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setPadding(0, dp(2), 0, dp(4));
            column.addView(subtitle);
        }

        if (!hasStarters(lineup)) {
            TextView empty = textView(R.string.detail_lineups_empty, 13,
                    R.color.detail_text_secondary);
            empty.setPadding(0, dp(8), 0, 0);
            column.addView(empty);
            return column;
        }

        addLineupPlayers(column, R.string.detail_lineups_starters,
                lineup.starters, Integer.MAX_VALUE);
        addLineupPlayers(column, R.string.detail_lineups_substitutes,
                lineup.substitutes, MAX_SUBSTITUTES);
        addLineupPlayers(column, R.string.detail_lineups_missing,
                lineup.missing, Integer.MAX_VALUE);

        if (!lineup.coach.isEmpty()) {
            TextView coach = textView(activity.getString(
                    R.string.detail_lineups_coach, lineup.coach),
                    12, R.color.detail_text_secondary);
            coach.setPadding(0, dp(8), 0, 0);
            column.addView(coach);
        }
        return column;
    }

    private void addLineupPlayers(LinearLayout column, int titleRes,
                                  List<LineupPlayer> players, int max) {
        if (players == null || players.isEmpty()) return;
        column.addView(sectionHeader(titleRes));

        int count = Math.min(players.size(), max);
        for (int i = 0; i < count; i++) {
            LineupPlayer player = players.get(i);
            if (player != null) column.addView(playerRow(player));
        }
    }

    /**
     * Fila compacta: dorsal, nombre a una línea con ellipsis, posición corta y
     * nota si la fuente la publica.
     */
    private View playerRow(LineupPlayer player) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));

        TextView number = textView(player.number > 0 ? String.valueOf(player.number) : "",
                14, R.color.detail_accent);
        number.setTypeface(null, Typeface.BOLD);
        number.setGravity(Gravity.END);
        row.addView(number, new LinearLayout.LayoutParams(
                dp(28), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView name = textView(player.name, 14, R.color.detail_text_primary);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(dp(6));
        row.addView(name, nameParams);

        if (!player.positionShort.isEmpty()) {
            TextView position = textView(player.positionShort, 11,
                    R.color.detail_text_secondary);
            position.setSingleLine(true);
            position.setEllipsize(TextUtils.TruncateAt.END);
            position.setMaxWidth(dp(64));
            position.setPadding(dp(6), 0, 0, 0);
            row.addView(position);
        }

        if (player.rating >= 0) {
            TextView rating = textView(activity.getString(
                    R.string.detail_player_rating, player.rating),
                    11, R.color.detail_live);
            rating.setTypeface(null, Typeface.BOLD);
            rating.setPadding(dp(6), 0, 0, 0);
            row.addView(rating);
        }
        return row;
    }

    private String teamName(MatchDetail loaded, TeamLineup lineup, boolean home) {
        if (lineup != null && !lineup.teamName.isEmpty()) return lineup.teamName;
        String name = home ? loaded.homeName : loaded.awayName;
        if (!name.isEmpty()) return name;
        return safe(home ? match.homeName : match.awayName);
    }

    /** "4-2-3-1 · Confirmado" o la parte que exista. */
    private static String lineupDetails(TeamLineup lineup) {
        if (lineup == null) return "";
        StringBuilder text = new StringBuilder();
        appendPart(text, lineup.formation);
        appendPart(text, lineup.status);
        return text.toString();
    }

    // ---------------------------------------------------------------------
    // Estadísticas
    // ---------------------------------------------------------------------

    private void renderStats(MatchDetail loaded) {
        int scrollY = statsScroll.getScrollY();
        statsContent.removeAllViews();

        if (loaded == null || !loaded.hasStats || loaded.statCategories.isEmpty()) {
            addStatsEmpty(loaded);
            restoreScroll(statsScroll, scrollY);
            return;
        }

        boolean firstCategory = true;
        for (StatCategory category : loaded.statCategories) {
            if (category == null) continue;

            boolean categoryStarted = false;
            for (StatRow row : category.rows) {
                if (row == null || row.name.trim().isEmpty()) continue;

                if (!categoryStarted) {
                    statsContent.addView(categoryHeader(category.name),
                            categoryParams(firstCategory));
                    firstCategory = false;
                    categoryStarted = true;
                }
                statsContent.addView(statRowBox(row));
            }
        }

        if (firstCategory) {
            // Todas las categorías venían sin filas utilizables.
            statsContent.addView(messageView(R.string.detail_stats_unavailable));
        }
        restoreScroll(statsScroll, scrollY);
    }

    private void addStatsEmpty(MatchDetail loaded) {
        if (loaded != null && !loaded.statsError.isEmpty()) {
            statsContent.addView(messageView(loaded.statsError));
            return;
        }

        TextView message = messageView(R.string.detail_stats_unavailable);
        if (loaded != null && loaded.status.isScheduled()) {
            // Antes del inicio la fuente aún no publica estadísticas.
            message.setPadding(0, message.getPaddingTop(), 0, dp(4));
            statsContent.addView(message);
            statsContent.addView(statsPendingHint());
        } else {
            statsContent.addView(message);
        }
    }

    private TextView statsPendingHint() {
        TextView hint = textView(R.string.detail_stats_pending, 12,
                R.color.detail_text_secondary);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, dp(32));
        return hint;
    }

    private TextView categoryHeader(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(13);
        view.setAllCaps(true);
        view.setLetterSpacing(0.08f);
        view.setTextColor(activity.getColor(R.color.detail_accent));
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private LinearLayout.LayoutParams categoryParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        // 8dp de margen del bloque anterior + 8dp aquí = 16dp entre categorías.
        if (!first) params.topMargin = dp(8);
        return params;
    }

    private View statRowBox(StatRow row) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout values = new LinearLayout(activity);
        values.setOrientation(LinearLayout.HORIZONTAL);
        values.setGravity(Gravity.CENTER_VERTICAL);
        values.setBaselineAligned(false);

        TextView home = textView(row.homeValue, 14, R.color.detail_text_primary);
        home.setGravity(Gravity.END);
        home.setSingleLine(true);
        home.setEllipsize(TextUtils.TruncateAt.END);
        if (row.major) home.setTypeface(null, Typeface.BOLD);
        values.addView(home, new LinearLayout.LayoutParams(
                dp(70), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView name = textView(row.name, 13, R.color.detail_text_secondary);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        values.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView away = textView(row.awayValue, 14, R.color.detail_text_primary);
        away.setGravity(Gravity.START);
        away.setSingleLine(true);
        away.setEllipsize(TextUtils.TruncateAt.END);
        if (row.major) away.setTypeface(null, Typeface.BOLD);
        values.addView(away, new LinearLayout.LayoutParams(
                dp(70), LinearLayout.LayoutParams.WRAP_CONTENT));

        box.addView(values);

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
        barParams.topMargin = dp(4);
        box.addView(statBar(row), barParams);

        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        boxParams.bottomMargin = dp(8);
        box.setLayoutParams(boxParams);
        return box;
    }

    private View statBar(StatRow row) {
        double home = row.homePercent;
        double away = row.awayPercent;
        if (home < 0 || away < 0 || (home <= 0 && away <= 0)) {
            home = 0.5;
            away = 0.5;
        }

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        GradientDrawable track = new GradientDrawable();
        track.setColor(STAT_BAR_TRACK);
        track.setCornerRadius(dp(2));
        bar.setBackground(track);

        View homeSegment = new View(activity);
        homeSegment.setBackground(barSegment(activity.getColor(R.color.detail_bar_home), true));
        View awaySegment = new View(activity);
        awaySegment.setBackground(barSegment(activity.getColor(R.color.detail_bar_away), false));

        bar.addView(homeSegment, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, (float) home));
        bar.addView(awaySegment, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, (float) away));
        return bar;
    }

    /** Segmento redondeado solo en el extremo exterior de la barra. */
    private GradientDrawable barSegment(int color, boolean left) {
        float radius = dp(2);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadii(left
                ? new float[]{radius, radius, 0, 0, 0, 0, radius, radius}
                : new float[]{0, 0, radius, radius, radius, radius, 0, 0});
        return drawable;
    }

    // ---------------------------------------------------------------------
    // Sin detalle, error y TV
    // ---------------------------------------------------------------------

    private void renderNoDetailSource() {
        summaryContent.removeAllViews();
        summaryContent.addView(messageView(R.string.detail_source_no_detail));

        String source = sourceLabel(match.sourceId);
        final String url = sourceUrl(match.sourceId);
        if (!url.isEmpty()) {
            TextView open = actionButton(activity.getString(
                    R.string.detail_open_source, source), () -> openUrl(url));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(12);
            params.gravity = Gravity.CENTER_HORIZONTAL;
            summaryContent.addView(open, params);
        }
    }

    /** Fuente sin detalle propio (RFEF, Celta): cabecera básica y enlace. */
    private void renderUnsupportedSource() {
        bindHeader(null);
        renderNoDetailSource();
        renderLineups(null);
        renderStats(null);
        renderTvNetworks(null);
        footer.setText(activity.getString(
                R.string.detail_source, sourceLabel(match.sourceId)));
    }

    private void renderError() {
        summaryContent.removeAllViews();

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cardBackground());
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = textView(R.string.detail_error, 16, R.color.detail_text_primary);
        title.setTypeface(null, Typeface.BOLD);
        card.addView(title);

        TextView retry = actionButton(R.string.detail_retry, this::retry);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        card.addView(retry, params);

        summaryContent.addView(card);
    }

    private void retry() {
        if (closed) return;
        summaryContent.removeAllViews();
        summaryContent.addView(messageView(R.string.detail_loading));
        lineupsContent.removeAllViews();
        lineupsContent.addView(messageView(R.string.detail_loading));
        statsContent.removeAllViews();
        statsContent.addView(messageView(R.string.detail_loading));
        loadDetail();
    }

    private void renderTv(MatchDetail loaded) {
        if (loaded == null) {
            tvContent.removeAllViews();
            tvContent.addView(messageView(R.string.detail_loading));
            return;
        }
        renderTvNetworks(loaded.tvNetworks);
    }

    private void renderTvNetworks(List<String> networks) {
        int scrollY = tvScroll.getScrollY();
        tvContent.removeAllViews();

        if (networks != null && !networks.isEmpty()) {
            tvContent.addView(sectionHeader(R.string.detail_tv_title));
            for (String network : networks) {
                if (network == null || network.trim().isEmpty()) continue;
                TextView value = textView(network.trim(), 17, R.color.detail_text_primary);
                value.setPadding(0, dp(4), 0, dp(4));
                tvContent.addView(value);
            }
        } else {
            tvContent.addView(messageView(R.string.detail_tv_none));
        }

        TextView search = actionButton(R.string.detail_tv_search, this::openTvSearch);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        tvContent.addView(search, params);

        TextView disclaimer = textView(
                R.string.detail_tv_disclaimer, 12, R.color.detail_text_secondary);
        disclaimer.setPadding(0, dp(12), 0, 0);
        disclaimer.setLineSpacing(dp(2), 1f);
        tvContent.addView(disclaimer);
        restoreScroll(tvScroll, scrollY);
    }

    private void openTvSearch() {
        long start = match.startMillis;
        String query;
        if (start > 0) {
            query = activity.getString(R.string.detail_tv_search_query,
                    safe(match.homeName), safe(match.awayName),
                    searchDateFormat.format(new Date(start)));
        } else {
            query = activity.getString(R.string.detail_tv_search_query_no_date,
                    safe(match.homeName), safe(match.awayName));
        }

        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(GOOGLE_SEARCH + Uri.encode(query)));
        openIntent(intent);
    }

    private void openUrl(String url) {
        openIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void openIntent(Intent intent) {
        if (intent.resolveActivity(activity.getPackageManager()) == null) {
            Toast.makeText(activity, R.string.detail_tv_no_browser, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.detail_tv_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private void renderFooter(MatchDetail loaded, SourceResult<MatchDetail> result,
                              boolean failed) {
        String source = sourceLabel(match.sourceId);
        StringBuilder text = new StringBuilder(
                activity.getString(R.string.detail_source, source));

        if (failed) {
            text.append(" · ").append(activity.getString(R.string.detail_error_short));
        } else {
            long fetchedAt = loaded != null && loaded.fetchedAt > 0
                    ? loaded.fetchedAt : (result == null ? 0L : result.fetchedAt);
            boolean cached = (loaded != null && loaded.fromCache)
                    || (result != null && result.fromCache);
            if (fetchedAt > 0) {
                text.append(" · ");
                text.append(cached
                        ? activity.getString(R.string.detail_cached)
                        : activity.getString(R.string.detail_updated,
                        timeFormat.format(new Date(fetchedAt))));
            }
        }
        footer.setText(text.toString());
    }

    private void showTab(int checkedId) {
        summaryScroll.setVisibility(
                checkedId == R.id.tabSummary ? View.VISIBLE : View.GONE);
        lineupsScroll.setVisibility(
                checkedId == R.id.tabLineups ? View.VISIBLE : View.GONE);
        statsScroll.setVisibility(
                checkedId == R.id.tabStats ? View.VISIBLE : View.GONE);
        tvScroll.setVisibility(
                checkedId == R.id.tabTv ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------------
    // Escudos
    // ---------------------------------------------------------------------

    private void loadCrest(ImageView view, String url) {
        view.setImageResource(R.drawable.ic_crest_placeholder);
        if (url == null || url.isEmpty()) {
            view.setTag(null);
            return;
        }
        view.setTag(url);

        Bitmap cached = LogoLoader.peek(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        Context app = activity.getApplicationContext();
        try {
            logoExecutor.execute(() -> {
                Bitmap bitmap = LogoLoader.load(app, url);
                if (bitmap == null) return;
                activity.runOnUiThread(() -> {
                    if (closed) return;
                    if (url.equals(view.getTag())) view.setImageBitmap(bitmap);
                });
            });
        } catch (RejectedExecutionException ignored) {
            // El diálogo se está cerrando; el placeholder es suficiente.
        }
    }

    // ---------------------------------------------------------------------
    // Utilidades de vista
    // ---------------------------------------------------------------------

    private TextView messageView(int textRes) {
        return messageView(activity.getString(textRes));
    }

    private TextView messageView(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(activity.getColor(R.color.detail_text_secondary));
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(32), 0, dp(32));
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    private TextView sectionHeader(int textRes) {
        TextView view = new TextView(activity);
        view.setText(textRes);
        view.setTextSize(12);
        view.setAllCaps(true);
        view.setLetterSpacing(0.1f);
        view.setTextColor(activity.getColor(R.color.detail_accent));
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private TextView textView(int textRes, float sizeSp, int colorRes) {
        return textView(activity.getString(textRes), sizeSp, colorRes);
    }

    private TextView textView(String text, float sizeSp, int colorRes) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(activity.getColor(colorRes));
        return view;
    }

    private TextView actionButton(int textRes, Runnable action) {
        return actionButton(activity.getString(textRes), action);
    }

    private TextView actionButton(String text, Runnable action) {
        TextView button = textView(text, 15, R.color.detail_bg);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(20), dp(12), dp(20), dp(12));
        button.setMinHeight(dp(48));
        button.setBackground(buttonBackground());
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private View divider() {
        View view = new View(activity);
        view.setBackgroundColor(SURFACE_STROKE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(2);
        params.bottomMargin = dp(2);
        view.setLayoutParams(params);
        return view;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(activity.getColor(R.color.detail_surface));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), SURFACE_STROKE);
        return background;
    }

    private GradientDrawable buttonBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(activity.getColor(R.color.detail_accent));
        background.setCornerRadius(dp(12));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private String number(int value) {
        return numberFormat.format(value);
    }

    private static Official firstOfficial(List<Official> officials) {
        if (officials == null) return null;
        for (Official official : officials) {
            if (official != null && !official.name.isEmpty()) return official;
        }
        return null;
    }

    private static void appendPart(StringBuilder text, String part) {
        if (part == null || part.trim().isEmpty()) return;
        if (text.length() > 0) text.append(" · ");
        text.append(part.trim());
    }

    private static String sourceLabel(String sourceId) {
        if (Scores365Source.ID.equals(sourceId)) return "365Scores";
        if (RfefSource.ID.equals(sourceId)) return "RFEF";
        if (CeltaSource.ID.equals(sourceId)) return "Celta";
        return sourceId == null ? "" : sourceId;
    }

    private static String sourceUrl(String sourceId) {
        if (RfefSource.ID.equals(sourceId)) return RFEF_URL;
        if (CeltaSource.ID.equals(sourceId)) return CELTA_URL;
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
