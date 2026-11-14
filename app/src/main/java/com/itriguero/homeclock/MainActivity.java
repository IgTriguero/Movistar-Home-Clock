package com.itriguero.homeclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private static final long WEATHER_INTERVAL_MS = 15 * 60 * 1000L;
    private static final long MATCHES_IDLE_INTERVAL_MS = 5 * 60 * 1000L;
    private static final long BROWSE_TIMEOUT_MS = 60 * 1000L;
    private static final long UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L;
    private static final long UPDATE_SNOOZE_MS = 24 * 60 * 60 * 1000L;

    private TextView clock;
    private TextView date;
    private TextView updated;

    private WeatherPager weatherPager;
    private PageDotsView weatherDots;
    private CityList cityList;
    private WeatherRepository weatherRepository;
    private WeatherSnapshot weatherSnapshot;
    private String lastCitySignature = "";

    private LinearLayout matchPanel;
    private LinearLayout matchList;
    private TextView matchTitle;
    private TextView matchSubtitle;
    private TextView matchPrevDay;
    private TextView matchNextDay;
    private TextView panelToggle;
    private TextView matchSources;

    private MatchRepository matchRepository;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService matchesExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService logoExecutor = Executors.newFixedThreadPool(3);
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("es", "ES"));
    private final SimpleDateFormat kickoffFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    private final List<Match> matches = new ArrayList<>();
    private final List<MatchRow> matchRows = new ArrayList<>();
    private final AtomicBoolean fetchingMatches = new AtomicBoolean(false);
    private final AtomicBoolean fetchingUpdate = new AtomicBoolean(false);
    private final KickoffGuard kickoffGuard = new KickoffGuard();

    private boolean running = true;
    private boolean showMatches = true;
    private boolean panelHidden;
    private String hiddenSignature = "";
    private String syncMessage = "";
    private MatchesSnapshot lastSnapshot;
    private LocalDate browseDate;
    private long browseDeadline;
    private String testDateOverride;
    private long lastWeatherFetch = 0;
    private long lastMatchesFetch = 0;
    private LocalDate lastFetchedDate = LocalDate.now();

    private Prefs prefs;
    private boolean suppressPageChange;
    private boolean lastShowMatches;
    private String lastTestDate = "";
    private boolean nightEnabled;
    private LocalTime nightStart = LocalTime.of(23, 0);
    private LocalTime nightEnd = LocalTime.of(7, 0);
    private boolean updatePromptShown;
    private View screenRoot;
    private long shiftAnchorMillis;
    private int lastShiftStep = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            Date now = new Date();
            clock.setText(timeFormat.format(now));
            date.setText(capitalize(dateFormat.format(now)));
            updateCountdowns();
            updateBrowseSubtitle();
            applyNightMode();
            applyPixelShift();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable browseTimeout = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            browseDate = null;
            browseDeadline = 0;
            updateBrowseSubtitle();
            refreshMatches();
        }
    };

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            refreshWeather();
            maybeCheckForUpdates();
            handler.postDelayed(this, WEATHER_INTERVAL_MS);
        }
    };

    private final Runnable matchesTick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            refreshMatches();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();

        prefs = new Prefs(this);
        testDateOverride = BuildConfig.DEBUG ? getIntent().getStringExtra("test_date") : null;
        cityList = prefs.loadCities();
        weatherRepository = new WeatherRepository(WeatherClient::fetch,
                new WeatherRepository.Store() {
                    @Override
                    public String read() {
                        return prefs.loadWeatherCache();
                    }

                    @Override
                    public void write(String json) {
                        prefs.saveWeatherCacheV2(json);
                    }
                });

        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        updated = findViewById(R.id.updated);
        weatherPager = findViewById(R.id.weatherPager);
        weatherDots = findViewById(R.id.weatherDots);
        screenRoot = findViewById(R.id.screenRoot);
        shiftAnchorMillis = System.currentTimeMillis();
        matchPanel = findViewById(R.id.matchPanel);
        matchList = findViewById(R.id.matchList);
        matchTitle = findViewById(R.id.matchTitle);
        matchSubtitle = findViewById(R.id.matchSubtitle);
        matchPrevDay = findViewById(R.id.matchPrevDay);
        matchNextDay = findViewById(R.id.matchNextDay);
        panelToggle = findViewById(R.id.panelToggle);
        matchSources = findViewById(R.id.matchSources);

        matchRepository = new MatchRepository(getApplicationContext());

        showMatches = prefs.showMatches();
        lastShowMatches = showMatches;
        lastTestDate = prefs.testDate();
        matchPanel.setVisibility(View.GONE);

        weatherPager.setBinder((page, index) -> {
            if (index < 0 || index >= cityList.size()) return;
            City city = cityList.get(index);
            WeatherCache.Entry entry = weatherSnapshot == null
                    ? null : weatherSnapshot.entryFor(city.key());
            page.bind(cityList.labelFor(city.key()),
                    entry == null ? null : entry.weather);
        });
        weatherPager.setOnPageChangedListener(index -> {
            if (suppressPageChange) return;
            if (index < 0 || index >= cityList.size()) return;
            cityList.select(cityList.get(index).key());
            prefs.saveSelection(cityList);
            lastCitySignature = citySignature();
            updateWeatherChrome();
        });

        findViewById(R.id.settingsButton).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        panelToggle.setOnClickListener(v -> toggleMatchPanel());
        matchPrevDay.setOnClickListener(v -> browseByDays(-1));
        matchNextDay.setOnClickListener(v -> browseByDays(1));
        panelToggle.setVisibility(View.GONE);

        handler.post(tick);
        handler.postDelayed(weatherTick, WEATHER_INTERVAL_MS);
        scheduleMatchesTick(MATCHES_IDLE_INTERVAL_MS);

        lastCitySignature = citySignature();
        updateWeatherChrome();
        refreshWeather();
        refreshMatches();
        maybeCheckForUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        hideSystemBars();
        loadNightConfig();

        long now = System.currentTimeMillis();
        String previousListSignature = cityList.signature();
        cityList = prefs.loadCities();
        String signature = citySignature();
        boolean listChanged = !cityList.signature().equals(previousListSignature);
        boolean cityChanged = !signature.equals(lastCitySignature);
        boolean show = prefs.showMatches();
        String testDate = prefs.testDate();
        boolean forceRefresh = prefs.consumeRefreshRequest();

        if (forceRefresh) {
            lastCitySignature = signature;
            lastShowMatches = show;
            lastTestDate = testDate;
            showMatches = show;
            testDateOverride = null;
            panelHidden = false;
            hiddenSignature = "";
            browseDate = null;
            browseDeadline = 0;
            handler.removeCallbacks(browseTimeout);
            updatePanelToggle();
            updateWeatherChrome();
            refreshWeather();
            refreshMatches();
        } else {
            boolean weatherDue = now - lastWeatherFetch > WEATHER_INTERVAL_MS;
            if (cityChanged) {
                lastCitySignature = signature;
                updateWeatherChrome();
                // Cambiar de ciudad sin tocar la lista no necesita red mientras
                // los datos sigan frescos: el snapshot ya trae todas las
                // ciudades consultadas.
                if (listChanged || weatherDue) refreshWeather();
            } else if (weatherDue) {
                refreshWeather();
            }

            if (show != lastShowMatches || !testDate.equals(lastTestDate)) {
                lastShowMatches = show;
                lastTestDate = testDate;
                showMatches = show;
                testDateOverride = null;
                panelHidden = false;
                hiddenSignature = "";
                browseDate = null;
                browseDeadline = 0;
                handler.removeCallbacks(browseTimeout);
                updatePanelToggle();
                refreshMatches();
            } else if (show && now - lastMatchesFetch > MATCHES_IDLE_INTERVAL_MS) {
                refreshMatches();
            }
        }

        shiftAnchorMillis = System.currentTimeMillis();
        lastShiftStep = -1;
        applyPixelShift();
        applyNightMode();
        maybeCheckForUpdates();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(matchesTick);
        handler.removeCallbacks(browseTimeout);
        executor.shutdownNow();
        matchesExecutor.shutdownNow();
        updateExecutor.shutdownNow();
        if (matchRepository != null) matchRepository.close();
        logoExecutor.shutdownNow();
        super.onDestroy();
    }

    /**
     * Comprueba una vez al día si hay release nueva, la descarga y, si el
     * SHA-256 coincide, pide confirmación para instalar. En debug no se
     * ejecuta para no mezclar la APK de desarrollo con la release.
     */
    private void maybeCheckForUpdates() {
        if (BuildConfig.DEBUG) return;

        long now = System.currentTimeMillis();
        if (now - prefs.lastUpdateCheck() < UPDATE_CHECK_INTERVAL_MS) return;
        if (!fetchingUpdate.compareAndSet(false, true)) return;

        prefs.saveLastUpdateCheck(now);
        updateExecutor.execute(() -> {
            try {
                UpdateClient.UpdateInfo info = UpdateClient.fetch();
                if (!info.isNewerThan(BuildConfig.VERSION_CODE)) return;
                if (prefs.updateSnoozeVersion() == info.versionCode
                        && prefs.updateSnoozeUntil() > System.currentTimeMillis()) {
                    return;
                }

                File apk = new File(getCacheDir(), "homeclock-update.apk");
                UpdateDownloader.download(info.apkUrl, apk);
                if (!UpdateDownloader.verify(apk, info.sha256)) {
                    apk.delete();
                    return;
                }
                runOnUiThread(() -> showUpdateDialog(info, apk));
            } catch (Exception ignored) {
                // Sin conexión o manifiesto inválido: se reintenta mañana.
            } finally {
                fetchingUpdate.set(false);
            }
        });
    }

    private void showUpdateDialog(UpdateClient.UpdateInfo info, File apk) {
        if (!running || isFinishing() || updatePromptShown) return;
        updatePromptShown = true;

        String message = info.notes.isEmpty()
                ? getString(R.string.update_message)
                : info.notes;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_title, info.versionName))
                .setMessage(message)
                .setPositiveButton(R.string.update_install,
                        (dialog, which) -> startUpdateInstall(apk))
                .setNegativeButton(R.string.update_later, (dialog, which) -> {
                    prefs.saveUpdateSnooze(info.versionCode,
                            System.currentTimeMillis() + UPDATE_SNOOZE_MS);
                    updatePromptShown = false;
                })
                .setOnCancelListener(dialog -> updatePromptShown = false)
                .show();
    }

    private void startUpdateInstall(File apk) {
        updatePromptShown = false;
        if (!UpdateInstaller.canInstall(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.update_permission_title)
                    .setMessage(R.string.update_permission_message)
                    .setPositiveButton(R.string.update_open_settings,
                            (dialog, which) ->
                                    UpdateInstaller.openUnknownSourcesSettings(this))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        try {
            UpdateInstaller.install(this, apk);
        } catch (Exception e) {
            Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void hideSystemBars() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    // ---------------------------------------------------------------------
    // Tiempo
    // ---------------------------------------------------------------------

    private void refreshWeather() {
        if (!running) return;

        lastWeatherFetch = System.currentTimeMillis();

        final List<City> cities = cityList.cities();
        final String signature = citySignature();

        // Mientras no haya datos se mantiene el aviso de actualización; con
        // datos se conserva la marca anterior para no parpadear en cada tick.
        if (weatherSnapshot == null || weatherSnapshot.isEmpty()) {
            updated.setText(R.string.weather_updating);
        }

        executor.execute(() -> {
            WeatherSnapshot snapshot = weatherRepository.refreshBlocking(cities);
            runOnUiThread(() -> {
                if (!running) return;
                weatherSnapshot = snapshot;
                if (snapshot.fetchedAt > 0) {
                    Diagnostics.get().recordWeather(snapshot.fetchedAt);
                }
                updateWeatherChrome();

                // La lista cambió mientras se consultaba la red: se pide un
                // único refresco más para no quedarse con datos de una lista
                // vieja. Si la firma no cambió, no se encadena nada.
                if (!signature.equals(citySignature())) {
                    handler.post(this::refreshWeather);
                }
            });
        });
    }

    /**
     * Repinta las páginas, los puntos, la marca temporal y la descripción
     * accesible sin mover la selección guardada.
     */
    private void updateWeatherChrome() {
        int selected = cityList.selectedIndex();

        suppressPageChange = true;
        try {
            weatherPager.setPageCount(cityList.size());
            weatherPager.setCurrentItem(selected, false);
        } finally {
            suppressPageChange = false;
        }

        weatherPager.notifyPagesChanged();
        weatherDots.setDots(cityList.size(), selected);
        updated.setText(weatherUpdatedLabel());
        weatherPager.setPageDescription(weatherPageDescription());
    }

    private CharSequence weatherUpdatedLabel() {
        WeatherSnapshot snapshot = weatherSnapshot;
        if (snapshot == null || snapshot.isEmpty() || snapshot.fetchedAt <= 0) {
            return getString(R.string.weather_empty);
        }

        if (snapshot.state == WeatherSnapshot.State.FRESH) {
            String label = getString(R.string.weather_updated,
                    timeFormat.format(new Date(snapshot.fetchedAt)));
            return snapshot.partial
                    ? getString(R.string.weather_partial, label)
                    : label;
        }

        String age = DateUtils.getRelativeTimeSpanString(
                snapshot.fetchedAt, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS).toString();
        return "offline".equals(snapshot.error)
                ? getString(R.string.weather_offline, age)
                : getString(R.string.weather_cached_age, age);
    }

    /** Texto para TalkBack: ciudad visible, tiempo y posición en la lista. */
    private CharSequence weatherPageDescription() {
        int size = cityList.size();
        int index = cityList.selectedIndex();
        City city = cityList.get(index);
        String label = cityList.labelFor(city.key());

        Weather weather = weatherSnapshot == null
                ? null : weatherSnapshot.weatherFor(city.key());
        if (weather == null) {
            return getString(R.string.weather_page_a11y_empty,
                    label, index + 1, size);
        }

        return getString(R.string.weather_page_a11y,
                label,
                String.format(Locale.getDefault(), "%.0f°", weather.temp),
                WeatherText.description(weather.weatherCode),
                getString(R.string.weather_page_details,
                        weather.feels, weather.humidity, weather.wind),
                index + 1, size);
    }

    private String citySignature() {
        return cityList.signature() + "|" + cityList.selectedKey();
    }

    // ---------------------------------------------------------------------
    // Partidos
    // ---------------------------------------------------------------------

    private void refreshMatches() {
        if (!showMatches) {
            handler.removeCallbacks(matchesTick);
            matchPanel.setVisibility(View.GONE);
            updatePanelToggle();
            return;
        }
        if (!fetchingMatches.compareAndSet(false, true)) return;

        LocalDate fetchDate = resolveFetchDate();

        final LocalDate date = fetchDate;
        lastFetchedDate = date;
        lastMatchesFetch = System.currentTimeMillis();

        if (matches.isEmpty() && !panelHidden) {
            showLoadingPanel();
        }

        matchesExecutor.execute(() -> {
            long started = System.currentTimeMillis();
            try {
                MatchesSnapshot snapshot = matchRepository.fetchBlocking(date);
                Diagnostics.get().recordMatches(snapshot,
                        System.currentTimeMillis() - started);
                runOnUiThread(() -> {
                    fetchingMatches.set(false);
                    if (!running) return;
                    if (!resolveFetchDate().equals(date)) {
                        refreshMatches();
                        return;
                    }
                    applySnapshot(snapshot);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    fetchingMatches.set(false);
                    if (!running) return;
                    if (!resolveFetchDate().equals(date)) {
                        refreshMatches();
                        return;
                    }
                    MatchesSnapshot error = errorSnapshot(date);
                    Diagnostics.get().recordMatches(error,
                            System.currentTimeMillis() - started);
                    applySnapshot(error);
                });
            }
        });
    }

    /**
     * Snapshot vacío con una fuente de error para que la UI muestre el panel
     * de reintento si falla el propio repositorio.
     */
    private MatchesSnapshot errorSnapshot(LocalDate date) {
        Map<String, SourceStatus> sources = new LinkedHashMap<>();
        sources.put("repository", new SourceStatus(
                "repository", SourceState.ERROR,
                getString(R.string.matches_error_title),
                System.currentTimeMillis(), 0));
        return new MatchesSnapshot(date, Collections.emptyList(), sources,
                System.currentTimeMillis(), false);
    }

    private void applySnapshot(MatchesSnapshot snapshot) {
        if (snapshot == null) {
            scheduleMatchesTick(RefreshPolicy.nextDelay(null, System.currentTimeMillis()));
            return;
        }
        lastSnapshot = snapshot;
        if (!showMatches) {
            handler.removeCallbacks(matchesTick);
            matchPanel.setVisibility(View.GONE);
            updatePanelToggle();
            return;
        }

        if (snapshot.stale) {
            syncMessage = getString(R.string.matches_stale,
                    timeFormat.format(new Date(snapshot.fetchedAt)));
        } else if (snapshot.hasErrors()) {
            syncMessage = getString(R.string.matches_partial,
                    errorSourceNames(snapshot));
        } else if (hasCachedSources(snapshot)) {
            syncMessage = getString(R.string.matches_cached);
        } else {
            syncMessage = "";
        }

        if (!snapshot.matches.isEmpty()) {
            renderMatches(snapshot.matches);
        } else {
            matches.clear();
            if (snapshot.hasErrors()) {
                showErrorPanel(snapshot);
            } else if (browseDate != null && !panelHidden) {
                showEmptyBrowsePanel();
            } else {
                matchRows.clear();
                matchList.removeAllViews();
                matchPanel.setVisibility(View.GONE);
                updatePanelToggle();
            }
        }

        updateSourceFooter(snapshot);
        updateBrowseSubtitle();
        scheduleMatchesTick(RefreshPolicy.nextDelay(snapshot, System.currentTimeMillis()));
    }

    private void showLoadingPanel() {
        if (panelHidden) return;

        matchRows.clear();
        matchList.removeAllViews();
        matchPanel.setVisibility(View.VISIBLE);
        matchTitle.setText(matchTitlePrefix(resolveFetchDate()));

        TextView loading = new TextView(this);
        loading.setText(R.string.matches_loading);
        loading.setTextColor(0xFF808080);
        loading.setTextSize(18);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(24), 0, 0);
        matchList.addView(loading);

        syncMessage = "";
        updateSourceFooter(null);
        updatePanelToggle();
        updateBrowseSubtitle();
    }

    private void showErrorPanel(MatchesSnapshot snapshot) {
        if (panelHidden) {
            matchPanel.setVisibility(View.GONE);
            updatePanelToggle();
            return;
        }

        matchRows.clear();
        matchList.removeAllViews();
        matchPanel.setVisibility(View.VISIBLE);
        matchTitle.setText(matchTitlePrefix(snapshot.date != null
                ? snapshot.date : resolveFetchDate()));

        LinearLayout retry = new LinearLayout(this);
        retry.setOrientation(LinearLayout.VERTICAL);
        retry.setGravity(Gravity.CENTER_HORIZONTAL);
        retry.setPadding(0, dp(24), 0, dp(24));
        retry.setOnClickListener(v -> refreshMatches());

        TextView title = new TextView(this);
        title.setText(R.string.matches_error_title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        retry.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.matches_retry);
        subtitle.setTextColor(0xFFE0A85A);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, 0);
        retry.addView(subtitle);

        matchList.addView(retry, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        updatePanelToggle();
        updateSourceFooter(snapshot);
        updateBrowseSubtitle();
    }

    private void updateSourceFooter(MatchesSnapshot snapshot) {
        if (matchSources == null) return;

        if (snapshot == null) {
            matchSources.setText(R.string.sources_short);
            matchSources.setTextColor(0xFF808080);
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append(sourceFooterLabel(snapshot, Scores365Source.ID, "365Scores"))
                .append(" · ")
                .append(sourceFooterLabel(snapshot, RfefSource.ID, "RFEF"))
                .append(" · ")
                .append(sourceFooterLabel(snapshot, CeltaSource.ID, "Celta"));

        matchSources.setText(text.toString());
        matchSources.setTextColor(snapshot.hasErrors() || snapshot.stale
                || hasCachedSources(snapshot) ? 0xFFE0A85A : 0xFF808080);
    }

    private String sourceFooterLabel(MatchesSnapshot snapshot,
                                     String sourceId, String label) {
        SourceStatus status = snapshot.sources.get(sourceId);
        if (status == null) {
            return snapshot.hasErrors() ? label + " ⚠" : label;
        }
        boolean ok = status.state == SourceState.OK || status.state == SourceState.EMPTY;
        if (status.fromCache) return label + " · caché";
        return ok ? label : label + " ⚠";
    }

    private static boolean hasCachedSources(MatchesSnapshot snapshot) {
        for (SourceStatus status : snapshot.sources.values()) {
            if (status != null && status.fromCache) return true;
        }
        return false;
    }

    private String errorSourceNames(MatchesSnapshot snapshot) {
        StringBuilder names = new StringBuilder();
        for (SourceStatus status : snapshot.sources.values()) {
            if (status == null) continue;
            if (status.state == SourceState.OK || status.state == SourceState.EMPTY) {
                continue;
            }
            if (names.length() > 0) names.append(", ");
            names.append(sourceDisplayName(status.sourceId));
        }
        return names.length() == 0 ? getString(R.string.diag_source_error) : names.toString();
    }

    private static String sourceDisplayName(String sourceId) {
        if (Scores365Source.ID.equals(sourceId)) return "365Scores";
        if (RfefSource.ID.equals(sourceId)) return "RFEF";
        if (CeltaSource.ID.equals(sourceId)) return "Celta";
        return sourceId == null ? "?" : sourceId;
    }

    private void renderMatches(List<Match> list) {
        String signature = matchSignature(list);

        if (list.isEmpty() && browseDate == null) {
            panelHidden = false;
            hiddenSignature = "";
        } else if (!list.isEmpty() && panelHidden && !signature.equals(hiddenSignature)) {
            // Si aparece un partido nuevo, el panel oculto se vuelve a mostrar.
            panelHidden = false;
        }

        matches.clear();
        matches.addAll(list);
        matchRows.clear();
        matchList.removeAllViews();

        if (matches.isEmpty()) {
            if (browseDate != null && !panelHidden) {
                showEmptyBrowsePanel();
                return;
            }
            matchPanel.setVisibility(View.GONE);
            updatePanelToggle();
            return;
        }

        matchPanel.setVisibility(panelHidden ? View.GONE : View.VISIBLE);
        updatePanelToggle();
        String prefix = matchTitlePrefix(lastFetchedDate);
        matchTitle.setText(getResources().getQuantityString(
                R.plurals.matches_count, matches.size(), prefix, matches.size()));

        for (int i = 0; i < matches.size(); i++) {
            addMatchRow(matches.get(i));
            if (i < matches.size() - 1) addSeparator();
        }
        updateCountdowns();
        updateBrowseSubtitle();
    }

    private void showEmptyBrowsePanel() {
        matchRows.clear();
        matchList.removeAllViews();
        matchPanel.setVisibility(View.VISIBLE);
        matchTitle.setText(matchTitlePrefix(lastFetchedDate));

        TextView empty = new TextView(this);
        empty.setText(R.string.matches_empty_day);
        empty.setTextColor(0xFF808080);
        empty.setTextSize(18);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(24), 0, 0);
        matchList.addView(empty);

        updatePanelToggle();
        updateBrowseSubtitle();
    }

    private void browseByDays(int delta) {
        LocalDate base = browseDate != null
                ? browseDate
                : (lastFetchedDate != null ? lastFetchedDate : defaultFetchDate());
        browseDate = base.plusDays(delta);
        startBrowseTimer();
        matchTitle.setText(getString(R.string.matches_title_more, matchTitlePrefix(browseDate)));
        refreshMatches();
    }

    private void startBrowseTimer() {
        browseDeadline = System.currentTimeMillis() + BROWSE_TIMEOUT_MS;
        handler.removeCallbacks(browseTimeout);
        handler.postDelayed(browseTimeout, BROWSE_TIMEOUT_MS);
        updateBrowseSubtitle();
    }

    private void toggleMatchPanel() {
        boolean visible = matchPanel.getVisibility() == View.VISIBLE;

        if (visible) {
            panelHidden = true;
            hiddenSignature = matchSignature(matches);
            matchPanel.setVisibility(View.GONE);
        } else {
            panelHidden = false;
            if (matches.isEmpty() && browseDate == null) {
                if (lastSnapshot != null && lastSnapshot.matches.isEmpty()
                        && lastSnapshot.hasErrors()) {
                    showErrorPanel(lastSnapshot);
                } else {
                    browseDate = lastFetchedDate != null
                            ? lastFetchedDate : defaultFetchDate();
                    startBrowseTimer();
                    showEmptyBrowsePanel();
                }
            }
            matchPanel.setVisibility(View.VISIBLE);
            updateCountdowns();
        }
        updatePanelToggle();
    }

    private void updatePanelToggle() {
        if (panelToggle == null) return;
        if (!showMatches) {
            panelToggle.setVisibility(View.GONE);
            return;
        }
        panelToggle.setVisibility(View.VISIBLE);
        boolean visible = matchPanel.getVisibility() == View.VISIBLE;
        panelToggle.setText(visible ? R.string.panel_hide : R.string.panel_show);
    }

    private void updateBrowseSubtitle() {
        if (matchSubtitle == null) return;
        if (browseDate == null) {
            matchSubtitle.setText(syncMessage.isEmpty()
                    ? getString(R.string.matches_subtitle) : syncMessage);
            return;
        }
        long remaining = Math.max(0, browseDeadline - System.currentTimeMillis());
        long seconds = (remaining + 999) / 1000;
        matchSubtitle.setText(getString(R.string.browse_return, seconds));
    }

    private String matchTitlePrefix(LocalDate date) {
        LocalDate base = defaultFetchDate();
        if (date.equals(base)) return getString(R.string.today);
        if (date.equals(base.plusDays(1))) return getString(R.string.tomorrow);
        if (date.equals(base.minusDays(1))) return getString(R.string.yesterday);
        String pattern = date.getYear() == base.getYear() ? "d MMM" : "d MMM yy";
        String text = date.format(
                DateTimeFormatter.ofPattern(pattern, new Locale("es", "ES")));
        return text.toUpperCase(new Locale("es", "ES"));
    }

    private LocalDate resolveFetchDate() {
        return browseDate != null ? browseDate : defaultFetchDate();
    }

    private LocalDate defaultFetchDate() {
        if (!BuildConfig.DEBUG) return LocalDate.now();
        String test = testDateOverride;
        if (test == null) {
            test = prefs.testDate();
        }
        if (test != null && !test.trim().isEmpty()) {
            try {
                return LocalDate.parse(test.trim());
            } catch (Exception ignored) {
                // Si la fecha de prueba no es válida, se usa hoy.
            }
        }
        return LocalDate.now();
    }

    private static String matchSignature(List<Match> list) {
        List<Long> ids = new ArrayList<>();
        for (Match m : list) ids.add(m.id);
        Collections.sort(ids);
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) sb.append(id).append(',');
        return sb.toString();
    }

    private void addMatchRow(Match m) {
        addCrestNameRow(m);
    }

    private void addCrestNameRow(Match m) {
        int crest = 80;
        int outer = crest + 8;
        String contextText = contextLabel(m);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        block.setPadding(0, dp(2), 0, dp(2));

        TextView context = new TextView(this);
        context.setText(contextText);
        context.setTextColor(0xFF808080);
        context.setTextSize(11);
        context.setGravity(Gravity.CENTER);
        context.setSingleLine(true);
        context.setEllipsize(TextUtils.TruncateAt.END);
        context.setPadding(0, 0, 0, dp(2));
        block.addView(context, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        row.setContentDescription(m.homeName + " contra " + m.awayName
                + (contextText.isEmpty() ? "" : ", " + contextText));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        left.addView(crestView(m.homeLogoUrl, crest, m.homeName),
                new LinearLayout.LayoutParams(dp(outer), dp(outer)));
        TextView homeName = teamNameView(m, true, 16);
        homeName.setGravity(Gravity.CENTER);
        left.addView(homeName);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        center.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView status = new TextView(this);
        TextView second = new TextView(this);
        bindStatus(m, status, second, 20, 16);
        center.addView(status);
        center.addView(second);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        right.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        right.addView(crestView(m.awayLogoUrl, crest, m.awayName),
                new LinearLayout.LayoutParams(dp(outer), dp(outer)));
        TextView awayName = teamNameView(m, false, 16);
        awayName.setGravity(Gravity.CENTER);
        right.addView(awayName);

        row.addView(left);
        row.addView(center);
        row.addView(right);
        block.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(128)));
        matchList.addView(block);
    }

    private TextView teamNameView(Match m, boolean home, float sizeSp) {
        TextView view = new TextView(this);
        view.setText(displayTeamName(m, home));
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(sizeSp);
        boolean tracked = home == m.trackedIsHome;
        view.setTypeface(null, tracked ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(home ? Gravity.START : Gravity.END);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private FrameLayout crestView(String url, int sizeDp, String teamName) {
        FrameLayout frame = new FrameLayout(this);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(teamName);
        image.setImageResource(R.drawable.ic_crest_placeholder);
        frame.addView(image, new FrameLayout.LayoutParams(
                dp(sizeDp), dp(sizeDp), Gravity.CENTER));
        loadLogo(url, image);
        return frame;
    }

    private void loadLogo(String url, ImageView view) {
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
        Context app = getApplicationContext();
        logoExecutor.execute(() -> {
            Bitmap bitmap = LogoLoader.load(app, url);
            if (bitmap == null) return;
            runOnUiThread(() -> {
                if (url.equals(view.getTag())) view.setImageBitmap(bitmap);
            });
        });
    }

    private void bindStatus(Match m, TextView status, TextView second,
                            float statusSp, float secondSp) {
        status.setTextSize(statusSp);
        status.setTypeface(null, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        second.setTextSize(secondSp);
        second.setGravity(Gravity.CENTER);

        if (m.isScheduled()) {
            status.setTextSize(24);
            second.setTextSize(18);
            if (m.timeTbd) {
                status.setText(R.string.dash);
                status.setTextColor(0xFFFFFFFF);
                second.setText(R.string.matches_hour_tbd);
                second.setTextColor(0xFFE0A85A);
            } else {
                status.setText(kickoffFormat.format(new Date(m.startMillis)));
                status.setTextColor(0xFFFFFFFF);
                second.setTextColor(0xFFE0A85A);
                MatchRow rowRef = new MatchRow();
                rowRef.match = m;
                rowRef.countdown = second;
                matchRows.add(rowRef);
            }
        } else if (m.isPostponed()) {
            status.setText(R.string.matches_postponed);
            status.setTextColor(0xFFFFFFFF);
            second.setText(R.string.dash);
            second.setTextColor(0xFF808080);
        } else if (m.isCancelled()) {
            status.setText(R.string.matches_cancelled);
            status.setTextColor(0xFFFFFFFF);
            second.setText(R.string.dash);
            second.setTextColor(0xFF808080);
        } else if (m.isInPlay()) {
            status.setTextSize(22);
            second.setTextSize(30);
            status.setText(m.isLiveApprox()
                    ? getString(R.string.matches_in_play) : liveLabel(m));
            status.setTextColor(0xFFFFD166);
            second.setText(scoreLabel(m));
            second.setTextColor(0xFFFFFFFF);
        } else if (m.isFinished()) {
            status.setTextSize(20);
            second.setTextSize(30);
            status.setText(finishedLabel(m));
            status.setTextColor(0xFF808080);
            second.setText(scoreLabel(m));
            second.setTextColor(0xFFFFFFFF);
        } else {
            status.setText(R.string.dash);
            status.setTextColor(0xFF808080);
            second.setText(R.string.dash);
            second.setTextColor(0xFF808080);
        }
    }

    private String finishedLabel(Match m) {
        String text = m.statusText == null ? "" : m.statusText.toLowerCase(Locale.ROOT);
        if (text.contains("tiempo extra") || text.contains("prórroga")
                || text.contains("overtime")) {
            return getString(R.string.match_final_ot);
        }
        if (text.contains("penaltis") || text.contains("penalties")) {
            return getString(R.string.match_penalties);
        }
        return getString("Baloncesto".equals(m.sport)
                ? R.string.match_final : R.string.match_ft);
    }

    private String displayTeamName(Match m, boolean home) {
        boolean tracked = home == m.trackedIsHome;
        if (tracked) return shortTeamName(m);
        return compactName(home ? m.homeName : m.awayName);
    }

    private static String compactName(String name) {
        if (name == null) return "";
        return name
                .replace("Republica Checa", "Rep. Checa")
                .replace("República Checa", "Rep. Checa")
                .replace("Real Racing Club de Santander", "Racing Santander")
                .replace("Fundación Rayo Vallecano", "Rayo Vallecano")
                .replace("Manchester United", "Man. United")
                .replace("Joventut de Badalona", "Joventut")
                .replace("Real Avilés Industrial", "Real Avilés")
                .replace("Club Atlético Osasuna", "Osasuna")
                .replace("Real Betis Balompié", "Real Betis")
                .replace("RC Deportivo de A Coruña", "Deportivo")
                .replace("Real Club Celta de Vigo", "Celta");
    }

    private static String shortCompetition(String competition) {
        if (competition == null) return "";
        String lower = competition.toLowerCase(Locale.ROOT);
        if (lower.contains("naciones")) return "Liga Naciones";
        if (lower.contains("copa del rey")) return "Copa del Rey";
        if (lower.contains("copa de la liga")) return "Copa de la Liga";
        if (lower.contains("super cup")) return "Supercopa Euroliga";
        if (lower.contains("euroliga") || lower.contains("euroleague")) return "Euroliga";
        if (lower.contains("liga endesa") || lower.contains("acb")) return "Liga Endesa";
        if (lower.contains("federaci")) return "2ª Federación";
        if (lower.contains("champions")) return "Champions";
        if (lower.contains("europa")) return "Europa League";
        if (lower.contains("amistoso")) return "Amistoso";
        return competition.trim();
    }

    private static String contextLabel(Match m) {
        String competition = shortCompetition(m.competition);
        String sport = m.sport == null ? "" : m.sport.trim();
        if (competition.isEmpty()) return sport;
        if (sport.isEmpty()) return competition;
        return competition + " · " + sport;
    }

    private String scoreLabel(Match m) {
        if (!m.scoreKnown) return getString(R.string.dash);
        return ((int) Math.round(m.homeScore)) + " - " + ((int) Math.round(m.awayScore));
    }

    private void addSeparator() {
        View sep = new View(this);
        sep.setBackgroundColor(0xFF222222);
        matchList.addView(sep, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void updateCountdowns() {
        if (matchRows.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (MatchRow row : matchRows) {
            long remaining = row.match.startMillis - now;
            if (remaining <= 0) {
                row.countdown.setText(R.string.matches_in_play);
                if (!fetchingMatches.get() && kickoffGuard.shouldRefresh(now, 60_000L)) {
                    refreshMatches();
                }
            } else {
                row.countdown.setText(countdownText(remaining));
            }
        }
    }

    private String countdownText(long remainingMs) {
        long s = remainingMs / 1000;
        if (s >= 86400) {
            return getString(R.string.countdown_days, s / 86400, (s / 3600) % 24);
        }
        if (s >= 3600) {
            return getString(R.string.countdown_hours, s / 3600, (s / 60) % 60);
        }
        if (s >= 60) {
            return getString(R.string.countdown_minutes, s / 60, s % 60);
        }
        return getString(R.string.countdown_seconds, s);
    }

    private String liveLabel(Match m) {
        String status = m.statusText == null ? "" : m.statusText;
        String statusLower = status.toLowerCase(Locale.ROOT);
        if (statusLower.contains("descanso") || statusLower.contains("half")) {
            return getString(R.string.matches_break);
        }

        if ("Baloncesto".equals(m.sport)) {
            String label = status;
            if (m.gameTimeDisplay != null && !m.gameTimeDisplay.isEmpty()) {
                if (label.isEmpty() || label.equalsIgnoreCase("Live")) {
                    label = m.gameTimeDisplay;
                } else {
                    label = label + " " + m.gameTimeDisplay;
                }
            }
            if (label.isEmpty()) label = getString(R.string.matches_live);
            return label;
        }

        // Fútbol: se muestra exactamente el minuto que da la API. No hay
        // reloj local ni segundos inventados.
        if (m.gameTimeDisplay != null && !m.gameTimeDisplay.isEmpty()) {
            return m.gameTimeDisplay;
        }
        if (m.gameTime > 0) {
            return Math.round(m.gameTime) + "'";
        }
        if (!status.isEmpty()) {
            return status;
        }
        return getString(R.string.matches_live);
    }

    private String shortTeamName(Match m) {
        long id = m.trackedIsHome ? m.homeId : m.awayId;
        switch ((int) id) {
            case 131: return "REAL MADRID";
            case 174: return "RAYO";
            case 108: return "LIVERPOOL";
            case 5050: return "ESPAÑA";
            case 204: return "R. MADRID BAL.";
            default: return m.trackedName.toUpperCase(Locale.getDefault());
        }
    }

    private void scheduleMatchesTick(long delay) {
        handler.removeCallbacks(matchesTick);
        handler.postDelayed(matchesTick, delay);
    }

    // ---------------------------------------------------------------------
    // Ajustes
    // ---------------------------------------------------------------------

    private void loadNightConfig() {
        nightEnabled = prefs.nightEnabled();
        nightStart = NightSchedule.parseOrDefault(
                prefs.nightStart(), Prefs.DEFAULT_NIGHT_START);
        nightEnd = NightSchedule.parseOrDefault(
                prefs.nightEnd(), Prefs.DEFAULT_NIGHT_END);
    }

    /**
     * Atenúa la ventana dentro de la franja nocturna. Se aplica sobre el
     * brillo de esta Activity, sin permisos ni cambios globales del sistema.
     */
    private void applyNightMode() {
        float brightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        if (nightEnabled && NightSchedule.isNight(LocalTime.now(), nightStart, nightEnd)) {
            brightness = 0.1f;
        }

        WindowManager.LayoutParams params = getWindow().getAttributes();
        if (params.screenBrightness != brightness) {
            params.screenBrightness = brightness;
            getWindow().setAttributes(params);
        }
    }

    /** Mueve el contenido unos dp cada 5 min para evitar retención de pantalla. */
    private void applyPixelShift() {
        if (screenRoot == null) return;
        int step = PixelShift.stepForElapsed(
                System.currentTimeMillis() - shiftAnchorMillis);
        if (step == lastShiftStep) return;
        lastShiftStep = step;
        int[] offset = PixelShift.offsetForStep(step);
        screenRoot.setTranslationX(dp(offset[0]));
        screenRoot.setTranslationY(dp(offset[1]));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static class MatchRow {
        Match match;
        TextView countdown;
    }
}
