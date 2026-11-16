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
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
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

    private static final Locale ES = new Locale("es", "ES");
    /** Fondo de la barra comparativa de estadísticas. */
    private static final int STAT_BAR_TRACK = 0xFF2A2A2A;
    /** Separador gris entre el conteo de goles y los goleadores. */
    private static final int GOAL_SEPARATOR = 0xFF7A7A7A;
    /** Los suplentes publicados suelen ser 7-12; se limita la lista por seguridad. */
    private static final int MAX_SUBSTITUTES = 12;

    // Escala tipográfica del estudio de legibilidad (1280x800, 1dp ~ 1px).
    private static final float EVENT_TEXT_SP = 48f;
    private static final float EVENT_DETAIL_SP = 28f;
    private static final float EVENT_SUB_SP = 36f;
    private static final float MINUTE_SP = 28f;
    private static final int TIMELINE_AXIS_WIDTH = 112;

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
    private final View liveDot;
    private final LinearLayout homeGoalsRow;
    private final LinearLayout awayGoalsRow;
    private final TextView homeGoals;
    private final TextView awayGoals;
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
        liveDot = dialog.findViewById(R.id.detailLiveDot);
        homeGoalsRow = dialog.findViewById(R.id.detailHomeGoalsRow);
        awayGoalsRow = dialog.findViewById(R.id.detailAwayGoalsRow);
        homeGoals = dialog.findViewById(R.id.detailHomeGoals);
        awayGoals = dialog.findViewById(R.id.detailAwayGoals);
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
            renderTvNetworks(null);
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
        int homeCount;
        int awayCount;
        boolean started;
        List<MatchEvent> events;
        if (loaded != null) {
            scoreKnown = loaded.scoreKnown;
            homeCount = loaded.homeScore;
            awayCount = loaded.awayScore;
            started = loaded.status.isInPlay() || loaded.status.isFinished();
            events = loaded.events;
        } else {
            scoreKnown = match.scoreKnown && (match.isInPlay() || match.isFinished());
            homeCount = (int) Math.round(match.homeScore);
            awayCount = (int) Math.round(match.awayScore);
            started = match.isInPlay() || match.isFinished();
            events = Collections.emptyList();
        }
        scoreText = scoreKnown
                ? homeCount + " - " + awayCount
                : activity.getString(R.string.dash);
        score.setText(scoreText);
        score.setContentDescription(scoreKnown
                ? activity.getString(R.string.detail_a11y_score, scoreText)
                : activity.getString(R.string.detail_a11y_score_none));

        MatchSummary summary = MatchSummary.from(homeCount, awayCount, events);
        boolean showGoals = scoreKnown && started;
        bindGoalLine(homeGoalsRow, homeGoals, summary, true, showGoals, home);
        bindGoalLine(awayGoalsRow, awayGoals, summary, false, showGoals, away);

        String statusText = statusLine(loaded);
        status.setText(statusText);
        status.setContentDescription(activity.getString(R.string.detail_a11y_status, statusText));

        MatchStatus value = loaded != null ? loaded.status : match.status;
        boolean breakStatus = statusText.equals(
                activity.getString(R.string.detail_status_break));
        boolean live = value != null && value.isInPlay() && !breakStatus;
        liveDot.setVisibility(live ? View.VISIBLE : View.GONE);

        if (live) {
            status.setTextColor(activity.getColor(R.color.detail_live));
        } else if (value != null && (value.isScheduled()
                || value.isFinished() || breakStatus)) {
            status.setTextColor(activity.getColor(R.color.detail_accent));
        } else {
            status.setTextColor(activity.getColor(R.color.detail_text_secondary));
        }
    }

    /**
     * Pinta la línea de goles de un equipo: balón, conteo oficial y hasta dos
     * goleadores cortos. Si no hay goles, "Sin goles" a 36sp; si el partido no
     * ha empezado o no se conoce el marcador, la línea se oculta.
     */
    private void bindGoalLine(LinearLayout row, TextView view, MatchSummary summary,
                              boolean home, boolean show, String teamName) {
        if (!show) {
            row.setVisibility(View.GONE);
            return;
        }
        row.setVisibility(View.VISIBLE);

        int count = summary.count(home);
        if (count <= 0) {
            view.setTextSize(36);
            view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            view.setTextColor(activity.getColor(R.color.detail_text_secondary));
            view.setText(R.string.detail_no_goals);
            view.setContentDescription(
                    activity.getString(R.string.detail_a11y_no_goals, teamName));
            return;
        }

        String names = summary.scorerLine(home, 2, count);
        view.setTextSize(36);
        SpannableStringBuilder text = new SpannableStringBuilder(String.valueOf(count));
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(activity.getColor(R.color.detail_text_primary)),
                0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (!names.isEmpty()) {
            int separatorStart = text.length();
            text.append(" · ");
            text.setSpan(new ForegroundColorSpan(GOAL_SEPARATOR),
                    separatorStart, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            int namesStart = text.length();
            text.append(names);
            text.setSpan(new ForegroundColorSpan(
                            activity.getColor(R.color.detail_text_secondary)),
                    namesStart, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        view.setText(text);
        view.setContentDescription(names.isEmpty()
                ? activity.getString(R.string.detail_a11y_goals_count, teamName, count)
                : activity.getString(R.string.detail_a11y_goals, teamName, count, names));
    }

    private String statusLine(MatchDetail loaded) {
        MatchStatus value = loaded != null ? loaded.status : match.status;
        if (value == null) value = MatchStatus.UNKNOWN;

        String statusText = loaded != null ? loaded.statusText : safe(match.statusText);
        String gameTime = loaded != null
                ? loaded.gameTimeDisplay : safe(match.gameTimeDisplay);

        if (value.isInPlay()) {
            if (MatchTimeline.isBreakText(statusText)) {
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

    // ---------------------------------------------------------------------
    // Resumen / cronología
    // ---------------------------------------------------------------------

    private void renderSummary(MatchDetail loaded) {
        int scrollY = summaryScroll.getScrollY();
        summaryContent.removeAllViews();
        summaryContent.addView(timelineHeader());

        List<MatchEvent> events = loaded.events;
        if (events.isEmpty()) {
            summaryContent.addView(messageView(noEventsMessage(loaded.status)));
        } else {
            addTimeline(loaded, events);
        }

        addFicha(loaded);
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

    /** Cabecera de la cronología: crestas mini a cada lado y título centrado. */
    private View timelineHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        ImageView home = miniCrest();
        ImageView away = miniCrest();
        loadCrest(home, match.homeLogoUrl);
        loadCrest(away, match.awayLogoUrl);

        TextView title = textView(R.string.detail_events_title,
                28, R.color.detail_accent);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setAllCaps(true);
        title.setLetterSpacing(0.06f);
        title.setIncludeFontPadding(false);

        header.addView(home, new LinearLayout.LayoutParams(dp(28), dp(28)));
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(away, new LinearLayout.LayoutParams(dp(28), dp(28)));
        return header;
    }

    private ImageView miniCrest() {
        ImageView view = new ImageView(activity);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImageResource(R.drawable.ic_crest_placeholder);
        view.setContentDescription(null);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private void addTimeline(MatchDetail loaded, List<MatchEvent> events) {
        boolean passedHalftime = MatchTimeline.hasPassedHalftime(
                loaded.status, loaded.statusText, loaded.gameTimeDisplay);
        int halftime = MatchTimeline.halftimeIndex(events, passedHalftime);
        for (int i = 0; i < events.size(); i++) {
            if (i == halftime) summaryContent.addView(dividerRow(R.string.detail_status_break));
            summaryContent.addView(timelineRow(loaded, events.get(i)));
        }
        if (halftime == events.size()) {
            summaryContent.addView(dividerRow(R.string.detail_status_break));
        }
        if (loaded.status.isFinished()) {
            summaryContent.addView(dividerRow(R.string.detail_status_final));
        }
    }

    /**
     * Fila de una sola línea: local a la izquierda, minuto en el eje central y
     * visitante a la derecha. Los eventos neutrales van centrados.
     */
    private View timelineRow(MatchDetail loaded, MatchEvent event) {
        if (MatchTimeline.isNeutral(event)) return neutralRow(event);

        boolean home = event.side == MatchEvent.Side.HOME;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout cell = eventCell(event);
        TextView minute = minuteView(event);
        View spacer = new View(activity);

        LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams spacerParams =
                new LinearLayout.LayoutParams(0, 1, 1f);
        LinearLayout.LayoutParams minuteParams = new LinearLayout.LayoutParams(
                dp(TIMELINE_AXIS_WIDTH), LinearLayout.LayoutParams.WRAP_CONTENT);

        if (home) {
            row.addView(cell, cellParams);
            row.addView(minute, minuteParams);
            row.addView(spacer, spacerParams);
        } else {
            row.addView(spacer, spacerParams);
            row.addView(minute, minuteParams);
            row.addView(cell, cellParams);
        }

        row.setFocusable(true);
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        row.setContentDescription(eventDescription(loaded, event));
        markDescendantsDecorative(row);
        return row;
    }

    /** Icono en el borde interior y texto hacia el exterior. */
    private LinearLayout eventCell(MatchEvent event) {
        boolean home = event.side == MatchEvent.Side.HOME;
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setBaselineAligned(false);
        cell.setGravity((home ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);

        View icon = eventIcon(event);

        // El área de texto ocupa el ancho restante; dentro, el grupo nombre +
        // detalle se pega al icono. Así el nombre nunca empuja al icono fuera.
        LinearLayout textArea = new LinearLayout(activity);
        textArea.setOrientation(LinearLayout.HORIZONTAL);
        textArea.setGravity((home ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        EventTextGroup textGroup = eventTextGroup(event);
        textArea.addView(textGroup.group, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (textGroup.name != null) {
            applyNamePriority(textArea, textGroup);
        }

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));

        if (home) {
            textParams.setMarginEnd(dp(12));
            cell.addView(textArea, textParams);
            cell.addView(icon, iconParams);
        } else {
            iconParams.setMarginEnd(dp(12));
            cell.addView(icon, iconParams);
            cell.addView(textArea, textParams);
        }
        return cell;
    }

    /**
     * Nombre 48sp (bold en los goles) y, al lado, asistencia 28sp o etiqueta
     * 28sp. Son TextView separados para que la elipsis caiga en el detalle y
     * nunca en el goleador. El cambio va en una línea a 36sp con autosize a
     * 32sp si los dos nombres no caben.
     */
    private EventTextGroup eventTextGroup(MatchEvent event) {
        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);

        if (event.type == MatchEvent.Type.SUBSTITUTION) {
            TextView substitution = textView(substitutionText(event),
                    EVENT_SUB_SP, R.color.detail_text_primary);
            substitution.setSingleLine(true);
            substitution.setEllipsize(TextUtils.TruncateAt.END);
            substitution.setIncludeFontPadding(false);
            substitution.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            substitution.setAutoSizeTextTypeUniformWithConfiguration(
                    32, 36, 1, TypedValue.COMPLEX_UNIT_SP);
            group.addView(substitution);
            return new EventTextGroup(group, null, null, null, "");
        }

        String player = event.playerName.trim();
        if (player.isEmpty()) player = activity.getString(R.string.detail_player_unknown);

        TextView name = textView(player, EVENT_TEXT_SP, R.color.detail_text_primary);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setIncludeFontPadding(false);
        name.setFontFeatureSettings("tnum");
        name.setTypeface(Typeface.create("sans-serif-medium",
                isGoal(event.type) ? Typeface.BOLD : Typeface.NORMAL));
        group.addView(name);

        TextView assist = null;
        boolean goal = event.type == MatchEvent.Type.GOAL
                || event.type == MatchEvent.Type.PENALTY_GOAL;
        if (goal && !event.secondaryName.trim().isEmpty()) {
            assist = textView("· " + event.secondaryName.trim(),
                    EVENT_DETAIL_SP, R.color.detail_text_secondary);
            assist.setSingleLine(true);
            assist.setEllipsize(TextUtils.TruncateAt.END);
            assist.setIncludeFontPadding(false);
            assist.setPadding(dp(8), 0, 0, 0);
            group.addView(assist);
        }

        TextView tag = null;
        int label = eventLabel(event.type);
        if (label != 0) {
            tag = textView(label, EVENT_DETAIL_SP, R.color.detail_accent);
            tag.setTypeface(null, Typeface.BOLD);
            tag.setSingleLine(true);
            tag.setEllipsize(TextUtils.TruncateAt.END);
            tag.setIncludeFontPadding(false);
            tag.setPadding(dp(8), 0, 0, 0);
            group.addView(tag);
        }
        return new EventTextGroup(group, name, assist, tag, player);
    }

    /**
     * Prioridad del nombre: se mide su ancho real y se limita la asistencia o
     * la etiqueta al espacio que queda. Si el nombre completo no cabe, se
     * prueba el nombre corto; la elipsis del nombre es el último recurso.
     */
    private void applyNamePriority(LinearLayout textArea, EventTextGroup text) {
        final int[] appliedWidth = {0};
        textArea.addOnLayoutChangeListener((view, left, top, right, bottom,
                                            oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            if (width <= 0 || width == appliedWidth[0]) return;
            appliedWidth[0] = width;
            textArea.post(() -> applyNamePriority(width, text));
        });
    }

    private void applyNamePriority(int available, EventTextGroup text) {
        String fullName = text.fullName;
        int fullWidth = measureText(text.name, fullName);
        String shortName = MatchSummary.shortName(fullName);
        int shortWidth = measureText(text.name, shortName);
        String chosen = MatchTimeline.fitName(
                fullName, shortName, fullWidth, shortWidth, available);

        if (chosen == null) {
            // Ni el nombre corto cabe: el TextView aplica su elipsis.
            if (!shortName.isEmpty()
                    && !shortName.contentEquals(text.name.getText())) {
                text.name.setText(shortName);
            }
        } else if (!chosen.contentEquals(text.name.getText())) {
            text.name.setText(chosen);
        }

        int nameWidth = measureText(text.name, text.name.getText().toString());
        int remaining = Math.max(0, available - nameWidth);

        // La etiqueta va después de la asistencia: se reserva su ancho
        // (incluido su padding) antes de limitar la asistencia.
        if (text.tag != null) {
            text.tag.setMaxWidth(remaining);
            int tagWidth = Math.min(
                    measureText(text.tag, text.tag.getText().toString())
                            + text.tag.getTotalPaddingLeft()
                            + text.tag.getTotalPaddingRight(),
                    remaining);
            remaining = Math.max(0, remaining - tagWidth);
        }
        if (text.assist != null) {
            text.assist.setMaxWidth(remaining);
        }
    }

    private static int measureText(TextView view, String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(view.getPaint().measureText(text));
    }

    /** Vistas del bloque de texto de una fila, para ajustar el ancho del detalle. */
    private static final class EventTextGroup {
        final LinearLayout group;
        final TextView name;
        final TextView assist;
        final TextView tag;
        final String fullName;

        EventTextGroup(LinearLayout group, TextView name, TextView assist,
                       TextView tag, String fullName) {
            this.group = group;
            this.name = name;
            this.assist = assist;
            this.tag = tag;
            this.fullName = fullName;
        }
    }

    private String substitutionText(MatchEvent event) {
        String in = event.playerName.trim();
        String out = event.secondaryName.trim();
        if (!in.isEmpty() && !out.isEmpty()) {
            return activity.getString(R.string.detail_sub_line, in, out);
        }
        if (!in.isEmpty()) return activity.getString(R.string.detail_sub_in, in);
        if (!out.isEmpty()) return activity.getString(R.string.detail_sub_out, out);
        return activity.getString(R.string.detail_sub_change);
    }

    private TextView minuteView(MatchEvent event) {
        String display = event.display == null ? "" : event.display.trim();
        if (display.isEmpty()) display = activity.getString(R.string.dash);

        TextView minute = textView(display, MINUTE_SP, R.color.detail_accent);
        minute.setTypeface(null, Typeface.BOLD);
        minute.setGravity(Gravity.CENTER);
        minute.setFontFeatureSettings("tnum");
        minute.setSingleLine(true);
        minute.setIncludeFontPadding(false);
        return minute;
    }

    /** VAR y demás eventos neutrales: fila centrada, sin lado. */
    private View neutralRow(MatchEvent event) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setFocusable(true);
        // 2dp por lado: el chip mide 40dp y la fila debe caber en el alto útil.
        row.setPadding(0, dp(2), 0, dp(2));

        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);

        boolean var = event.type == MatchEvent.Type.VAR;
        if (var) {
            TextView chip = textView(R.string.detail_event_var,
                    18, R.color.detail_text_secondary);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setLetterSpacing(0.04f);
            chip.setBackgroundResource(R.drawable.ic_ev_var);
            group.addView(chip, new LinearLayout.LayoutParams(dp(48), dp(40)));
        } else {
            View icon = eventIcon(event);
            group.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }

        int labelRes = eventLabel(event.type);
        if (!var && labelRes == 0) labelRes = eventTypeName(event.type);
        TextView label = textView(var
                        ? activity.getString(R.string.detail_var_review)
                        : activity.getString(labelRes),
                32, R.color.detail_text_secondary);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(var ? 12 : 8), 0, 0, 0);
        group.addView(label);

        TextView minute = minuteView(event);
        minute.setPadding(dp(16), 0, 0, 0);
        group.addView(minute);

        row.addView(group, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        row.setContentDescription(eventDescription(null, event));
        markDescendantsDecorative(row);
        return row;
    }

    private View eventIcon(MatchEvent event) {
        int resource;
        if (event.type == null) {
            resource = R.drawable.ic_ev_other;
        } else {
            switch (event.type) {
                case YELLOW_CARD:
                    resource = R.drawable.ic_ev_yellow_card;
                    break;
                case RED_CARD:
                    resource = R.drawable.ic_ev_red_card;
                    break;
                case SECOND_YELLOW:
                    resource = R.drawable.ic_ev_second_yellow;
                    break;
                case SUBSTITUTION:
                    resource = R.drawable.ic_ev_substitution;
                    break;
                case GOAL:
                case PENALTY_GOAL:
                case OWN_GOAL:
                case DISALLOWED_GOAL:
                    resource = R.drawable.ic_ev_goal;
                    break;
                default:
                    resource = R.drawable.ic_ev_other;
                    break;
            }
        }

        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageResource(resource);
        icon.setContentDescription(null);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return icon;
    }

    /** Descripción completa de la fila para TalkBack. */
    private String eventDescription(MatchDetail loaded, MatchEvent event) {
        String minute = event.display == null
                ? "" : event.display.replace("'", "").trim();
        StringBuilder text = new StringBuilder();
        if (!minute.isEmpty()) {
            text.append(activity.getString(R.string.detail_a11y_minute, minute)).append(' ');
        }

        if (MatchTimeline.isNeutral(event)
                && event.type != MatchEvent.Type.VAR) {
            String neutralPlayer = event.playerName.trim();
            if (neutralPlayer.isEmpty()) {
                text.append(activity.getString(R.string.detail_a11y_neutral,
                        activity.getString(eventTypeName(event.type))));
            } else {
                text.append(activity.getString(R.string.detail_a11y_neutral_player,
                        activity.getString(eventTypeName(event.type)), neutralPlayer));
            }
            return text.toString().trim();
        }
        if (event.type == MatchEvent.Type.VAR) {
            text.append(activity.getString(R.string.detail_a11y_var));
            return text.toString().trim();
        }

        String player = event.playerName.trim().isEmpty()
                ? activity.getString(R.string.detail_player_unknown)
                : event.playerName.trim();
        String team = loaded == null ? "" : eventTeam(loaded, event);
        boolean hasSecondary = !event.secondaryName.trim().isEmpty();
        String secondary = event.secondaryName.trim();

        switch (event.type) {
            case GOAL:
                text.append(hasSecondary
                        ? activity.getString(R.string.detail_a11y_goal_assist,
                        team, player, secondary)
                        : activity.getString(R.string.detail_a11y_goal, team, player));
                break;
            case PENALTY_GOAL:
                text.append(hasSecondary
                        ? activity.getString(R.string.detail_a11y_penalty_assist,
                        team, player, secondary)
                        : activity.getString(R.string.detail_a11y_penalty, team, player));
                break;
            case OWN_GOAL:
                // El lado que publica la fuente para una propia no es fiable:
                // se nombra al jugador y no se atribuye el gol a un equipo.
                text.append(activity.getString(
                        R.string.detail_a11y_own_goal, player));
                break;
            case DISALLOWED_GOAL:
                text.append(activity.getString(
                        R.string.detail_a11y_disallowed, team, player));
                break;
            case YELLOW_CARD:
                text.append(activity.getString(
                        R.string.detail_a11y_yellow, team, player));
                break;
            case RED_CARD:
                text.append(activity.getString(
                        R.string.detail_a11y_red, team, player));
                break;
            case SECOND_YELLOW:
                text.append(activity.getString(
                        R.string.detail_a11y_second_yellow, team, player));
                break;
            case SUBSTITUTION:
                text.append(hasSecondary
                        ? activity.getString(R.string.detail_a11y_substitution,
                        team, player, secondary)
                        : activity.getString(R.string.detail_a11y_substitution_in,
                        team, player));
                break;
            case OTHER:
            default:
                text.append(activity.getString(
                        R.string.detail_a11y_other, team, player));
                break;
        }
        return text.toString().trim();
    }

    private String eventTeam(MatchDetail loaded, MatchEvent event) {
        if (loaded == null) return "";
        if (event.side == MatchEvent.Side.HOME) {
            return !loaded.homeName.isEmpty() ? loaded.homeName : safe(match.homeName);
        }
        if (event.side == MatchEvent.Side.AWAY) {
            return !loaded.awayName.isEmpty() ? loaded.awayName : safe(match.awayName);
        }
        return "";
    }

    private static boolean isGoal(MatchEvent.Type type) {
        return type == MatchEvent.Type.GOAL
                || type == MatchEvent.Type.PENALTY_GOAL
                || type == MatchEvent.Type.OWN_GOAL;
    }

    /** Etiqueta corta inline; 0 si la fila no lleva etiqueta. */
    private int eventLabel(MatchEvent.Type type) {
        if (type == null) return R.string.detail_event_other;
        switch (type) {
            case PENALTY_GOAL:
                return R.string.detail_event_penalty;
            case OWN_GOAL:
                return R.string.detail_event_own_goal_short;
            case DISALLOWED_GOAL:
                return R.string.detail_event_disallowed_short;
            case YELLOW_CARD:
                return R.string.detail_event_yellow;
            case RED_CARD:
                return R.string.detail_event_red;
            case SECOND_YELLOW:
                return R.string.detail_event_second_yellow_short;
            case OTHER:
                return R.string.detail_event_other;
            default:
                return 0;
        }
    }

    /** Etiqueta larga, para las descripciones accesibles y los neutrales. */
    private int eventTypeName(MatchEvent.Type type) {
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

    /** Divisor de sección ("DESCANSO", "FINAL"): dos líneas de 2dp. */
    private View dividerRow(int textRes) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));

        View left = new View(activity);
        left.setBackgroundColor(activity.getColor(R.color.detail_divider));
        row.addView(left, new LinearLayout.LayoutParams(0, dp(2), 1f));

        TextView label = textView(textRes, 28, R.color.detail_accent);
        label.setTypeface(null, Typeface.BOLD);
        label.setAllCaps(true);
        label.setLetterSpacing(0.06f);
        label.setIncludeFontPadding(false);
        label.setPadding(dp(12), 0, dp(12), 0);
        row.addView(label);

        View right = new View(activity);
        right.setBackgroundColor(activity.getColor(R.color.detail_divider));
        row.addView(right, new LinearLayout.LayoutParams(0, dp(2), 1f));
        return row;
    }

    /** Ficha plana al final del resumen: estadio, aforo, árbitro y fecha. */
    private void addFicha(MatchDetail loaded) {
        Venue venue = loaded.venue;
        boolean hasVenue = venue != null && !venue.name.isEmpty();
        boolean hasNumbers = venue != null
                && (venue.capacity > 0 || venue.attendance > 0);

        List<Official> officials = new ArrayList<>();
        if (loaded.officials != null) {
            for (Official official : loaded.officials) {
                if (official != null && !official.name.trim().isEmpty()) {
                    officials.add(official);
                }
            }
        }
        long start = loaded.startMillis > 0 ? loaded.startMillis : match.startMillis;
        if (!hasVenue && !hasNumbers && officials.isEmpty() && start <= 0) return;

        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(20), 0, dp(8));
        block.addView(sectionHeader(R.string.detail_ficha, 24));

        if (hasVenue) {
            TextView name = textView(venue.name, 28, R.color.detail_text_primary);
            name.setTypeface(null, Typeface.BOLD);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(0, dp(4), 0, 0);
            block.addView(name);
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
            TextView value = textView(numbers.toString(),
                    24, R.color.detail_text_secondary);
            value.setPadding(0, dp(2), 0, 0);
            block.addView(value);
        }

        for (Official official : officials) {
            TextView value = textView(activity.getString(
                            R.string.detail_official, official.role, official.name),
                    24, R.color.detail_text_secondary);
            value.setPadding(0, dp(2), 0, 0);
            block.addView(value);
        }

        if (start > 0) {
            TextView value = textView(metaDateFormat.format(new Date(start)),
                    24, R.color.detail_text_secondary);
            value.setPadding(0, dp(2), 0, 0);
            block.addView(value);
        }

        summaryContent.addView(block);
    }

    /** Los hijos de una fila no son nodos propios: la fila lleva la frase. */
    private static void markDescendantsDecorative(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (child instanceof ViewGroup) {
                markDescendantsDecorative((ViewGroup) child);
            }
        }
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

        TextView team = textView(teamName(loaded, lineup, home), 36, R.color.detail_text_primary);
        team.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        team.setMaxLines(2);
        team.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(team);

        String details = lineupDetails(lineup);
        if (!details.isEmpty()) {
            TextView subtitle = textView(details, 24, R.color.detail_text_secondary);
            subtitle.setMaxLines(2);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setPadding(0, dp(2), 0, dp(6));
            column.addView(subtitle);
        }

        if (!hasStarters(lineup)) {
            TextView empty = textView(R.string.detail_lineups_empty, 24,
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
                    24, R.color.detail_text_secondary);
            coach.setPadding(0, dp(8), 0, 0);
            column.addView(coach);
        }
        return column;
    }

    private void addLineupPlayers(LinearLayout column, int titleRes,
                                  List<LineupPlayer> players, int max) {
        if (players == null || players.isEmpty()) return;
        column.addView(sectionHeader(titleRes, 24));

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
        row.setPadding(0, dp(6), 0, dp(6));

        TextView number = textView(player.number > 0 ? String.valueOf(player.number) : "",
                32, R.color.detail_accent);
        number.setTypeface(null, Typeface.BOLD);
        number.setGravity(Gravity.END);
        number.setFontFeatureSettings("tnum");
        row.addView(number, new LinearLayout.LayoutParams(
                dp(56), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView name = textView(player.name, 32, R.color.detail_text_primary);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(dp(8));
        row.addView(name, nameParams);

        if (!player.positionShort.isEmpty()) {
            TextView position = textView(player.positionShort, 24,
                    R.color.detail_text_secondary);
            position.setSingleLine(true);
            position.setEllipsize(TextUtils.TruncateAt.END);
            position.setMaxWidth(dp(96));
            position.setPadding(dp(8), 0, 0, 0);
            row.addView(position);
        }

        if (player.rating >= 0) {
            TextView rating = textView(activity.getString(
                    R.string.detail_player_rating, player.rating),
                    24, R.color.detail_live);
            rating.setTypeface(null, Typeface.BOLD);
            rating.setPadding(dp(8), 0, 0, 0);
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
        TextView hint = textView(R.string.detail_stats_pending, 28,
                R.color.detail_text_secondary);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, dp(32));
        return hint;
    }

    private TextView categoryHeader(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(28);
        view.setAllCaps(true);
        view.setLetterSpacing(0.06f);
        view.setTextColor(activity.getColor(R.color.detail_accent));
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private LinearLayout.LayoutParams categoryParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        // 16dp entre categorías.
        if (!first) params.topMargin = dp(16);
        return params;
    }

    private View statRowBox(StatRow row) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout values = new LinearLayout(activity);
        values.setOrientation(LinearLayout.HORIZONTAL);
        values.setGravity(Gravity.CENTER_VERTICAL);
        values.setBaselineAligned(false);

        TextView home = textView(row.homeValue, 40, R.color.detail_text_primary);
        home.setGravity(Gravity.END);
        home.setSingleLine(true);
        home.setEllipsize(TextUtils.TruncateAt.END);
        home.setPadding(0, 0, dp(16), 0);
        if (row.major) home.setTypeface(null, Typeface.BOLD);
        values.addView(home, new LinearLayout.LayoutParams(
                dp(120), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView name = textView(row.name, 28, R.color.detail_text_secondary);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(dp(16), 0, dp(16), 0);
        values.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView away = textView(row.awayValue, 40, R.color.detail_text_primary);
        away.setGravity(Gravity.START);
        away.setSingleLine(true);
        away.setEllipsize(TextUtils.TruncateAt.END);
        away.setPadding(dp(16), 0, 0, 0);
        if (row.major) away.setTypeface(null, Typeface.BOLD);
        values.addView(away, new LinearLayout.LayoutParams(
                dp(120), LinearLayout.LayoutParams.WRAP_CONTENT));

        box.addView(values);

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(16));
        barParams.topMargin = dp(8);
        box.addView(statBar(row), barParams);

        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        boxParams.bottomMargin = dp(16);
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
        track.setCornerRadius(dp(8));
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
        float radius = dp(8);
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

        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(32), 0, 0);

        TextView title = textView(R.string.detail_error, 32, R.color.detail_text_primary);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        block.addView(title);

        TextView retry = actionButton(R.string.detail_retry, this::retry);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        block.addView(retry, params);

        summaryContent.addView(block);
    }

    private void retry() {
        if (closed) return;
        summaryContent.removeAllViews();
        summaryContent.addView(messageView(R.string.detail_loading));
        lineupsContent.removeAllViews();
        lineupsContent.addView(messageView(R.string.detail_loading));
        statsContent.removeAllViews();
        statsContent.addView(messageView(R.string.detail_loading));
        renderTv(null);
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

        BroadcastInfo info = BroadcastCatalog.forMatch(match);
        List<String> emisoras = cleanNetworks(networks);
        boolean hasNetworks = !emisoras.isEmpty();

        if (hasNetworks) {
            tvContent.addView(sectionHeader(R.string.detail_tv_title, 24));
            for (String network : emisoras) {
                TextView value = textView(network, 36, R.color.detail_text_primary);
                value.setPadding(0, dp(6), 0, dp(6));
                tvContent.addView(value);
            }
        } else if (!info.isCurated()) {
            // Competición desconocida: el catálogo no puede confirmar emisora.
            tvContent.addView(messageView(R.string.detail_tv_none));
        }

        addBroadcastCatalog(info, hasNetworks);

        TextView search = actionButton(R.string.detail_tv_search,
                () -> openTvSearch(info.searchUrl));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        tvContent.addView(search, params);

        TextView disclaimer = textView(
                R.string.detail_tv_disclaimer, 20, R.color.detail_text_secondary);
        disclaimer.setPadding(0, dp(16), 0, 0);
        disclaimer.setLineSpacing(dp(2), 1f);
        tvContent.addView(disclaimer);

        TextView reviewed = textView(activity.getString(
                R.string.detail_tv_reviewed, BroadcastCatalog.SEASON),
                20, R.color.detail_text_secondary);
        reviewed.setPadding(0, dp(4), 0, 0);
        tvContent.addView(reviewed);

        restoreScroll(tvScroll, scrollY);
    }

    /**
     * Catálogo curado por competición. En modo búsqueda solo se muestra el
     * aviso, sin repetir la etiqueta del botón.
     */
    private void addBroadcastCatalog(BroadcastInfo info, boolean hasNetworks) {
        if (info == null || !info.isCurated()) {
            if (hasNetworks && info != null && !info.detail.isEmpty()) {
                TextView detail = textView(info.detail, 24, R.color.detail_text_secondary);
                detail.setPadding(0, dp(16), 0, 0);
                detail.setLineSpacing(dp(2), 1f);
                tvContent.addView(detail);
            }
            return;
        }

        tvContent.addView(sectionHeader(R.string.detail_tv_catalog, 24));

        TextView label = textView(info.label, 36, R.color.detail_text_primary);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setPadding(0, dp(4), 0, dp(6));
        tvContent.addView(label);

        if (!info.detail.isEmpty()) {
            TextView detail = textView(info.detail, 24, R.color.detail_text_secondary);
            detail.setLineSpacing(dp(2), 1f);
            tvContent.addView(detail);
        }
    }

    /** Nombres limpiados de dial y sin duplicados, conservando el orden de la fuente. */
    private static List<String> cleanNetworks(List<String> networks) {
        if (networks == null || networks.isEmpty()) return Collections.emptyList();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String network : networks) {
            String cleaned = BroadcastCatalog.cleanNetworkName(network);
            if (!cleaned.isEmpty()) unique.add(cleaned);
        }
        return unique.isEmpty() ? Collections.emptyList() : new ArrayList<>(unique);
    }

    private void openTvSearch(String url) {
        if (url == null || url.isEmpty()) return;
        openUrl(url);
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
        view.setTextSize(32);
        view.setTextColor(activity.getColor(R.color.detail_text_secondary));
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(32), 0, dp(32));
        view.setLineSpacing(dp(4), 1f);
        return view;
    }

    private TextView sectionHeader(int textRes, float sizeSp) {
        TextView view = new TextView(activity);
        view.setText(textRes);
        view.setTextSize(sizeSp);
        view.setAllCaps(true);
        view.setLetterSpacing(0.06f);
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
        TextView button = textView(text, 30, R.color.detail_bg);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(24), dp(12), dp(24), dp(12));
        button.setMinHeight(dp(64));
        button.setMinWidth(dp(64));
        button.setBackground(buttonBackground());
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        return button;
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
