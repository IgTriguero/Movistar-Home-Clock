package com.itriguero.homeclock;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ajustes de la app: búsqueda de ciudad por nombre, partidos y modo noche.
 * La ciudad se guarda al tocar un resultado; Guardar persiste el resto.
 */
public class SettingsActivity extends Activity {

    private Prefs prefs;

    private TextView currentCity;
    private EditText cityQuery;
    private Button searchButton;
    private TextView searchStatus;
    private LinearLayout searchResults;
    private ScrollView settingsScroll;
    private CheckBox showMatchesInput;
    private CheckBox nightEnabledInput;
    private EditText nightStartInput;
    private EditText nightEndInput;
    private EditText testDateInput;
    private Button checkUpdatesButton;
    private TextView updateStatus;
    private TextView diagnosticsMatches;
    private TextView diagnosticsWeather;
    private TextView diagnosticsSources;
    private TextView diagnosticsCache;
    private TextView diagnosticsLogos;
    private Button refreshNowButton;
    private Button clearLogosButton;
    private TextView diagnosticsStatus;

    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private int searchToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new Prefs(this);

        currentCity = findViewById(R.id.currentCity);
        cityQuery = findViewById(R.id.cityQuery);
        searchButton = findViewById(R.id.searchButton);
        searchStatus = findViewById(R.id.searchStatus);
        searchResults = findViewById(R.id.searchResults);
        settingsScroll = findViewById(R.id.settingsScroll);
        showMatchesInput = findViewById(R.id.showMatches);
        nightEnabledInput = findViewById(R.id.nightEnabled);
        nightStartInput = findViewById(R.id.nightStart);
        nightEndInput = findViewById(R.id.nightEnd);
        testDateInput = findViewById(R.id.testDate);
        checkUpdatesButton = findViewById(R.id.checkUpdatesButton);
        updateStatus = findViewById(R.id.updateStatus);
        diagnosticsMatches = findViewById(R.id.diagnosticsMatches);
        diagnosticsWeather = findViewById(R.id.diagnosticsWeather);
        diagnosticsSources = findViewById(R.id.diagnosticsSources);
        diagnosticsCache = findViewById(R.id.diagnosticsCache);
        diagnosticsLogos = findViewById(R.id.diagnosticsLogos);
        refreshNowButton = findViewById(R.id.refreshNowButton);
        clearLogosButton = findViewById(R.id.clearLogosButton);
        diagnosticsStatus = findViewById(R.id.diagnosticsStatus);

        currentCity.setText(prefs.city());
        showMatchesInput.setChecked(prefs.showMatches());
        nightEnabledInput.setChecked(prefs.nightEnabled());
        nightStartInput.setText(prefs.nightStart());
        nightEndInput.setText(prefs.nightEnd());

        View testDateSection = findViewById(R.id.testDateSection);
        testDateSection.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        if (BuildConfig.DEBUG) testDateInput.setText(prefs.testDate());

        ((TextView) findViewById(R.id.version))
                .setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.saveButton).setOnClickListener(v -> save());
        checkUpdatesButton.setOnClickListener(v -> checkForUpdates());
        refreshNowButton.setOnClickListener(v -> requestRefreshNow());
        clearLogosButton.setOnClickListener(v -> clearLogoCache());
        searchButton.setOnClickListener(v -> startSearch());
        cityQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startSearch();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDiagnostics();
    }

    @Override
    protected void onDestroy() {
        searchToken++;
        searchExecutor.shutdownNow();
        updateExecutor.shutdownNow();
        super.onDestroy();
    }

    private void startSearch() {
        final String query = cityQuery.getText().toString().trim();
        if (query.isEmpty()) {
            searchStatus.setText(R.string.settings_search_required);
            searchStatus.setVisibility(View.VISIBLE);
            return;
        }

        hideKeyboard();
        final int token = ++searchToken;
        searchButton.setEnabled(false);
        searchStatus.setText(R.string.settings_searching);
        searchStatus.setVisibility(View.VISIBLE);
        searchResults.removeAllViews();
        scrollToResults();

        searchExecutor.execute(() -> {
            final List<CitySearchClient.Result> found;
            try {
                found = CitySearchClient.search(query);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (token != searchToken || isFinishing()) return;
                    searchButton.setEnabled(true);
                    searchStatus.setText(R.string.settings_search_error);
                    searchStatus.setVisibility(View.VISIBLE);
                    scrollToResults();
                });
                return;
            }

            runOnUiThread(() -> {
                if (token != searchToken || isFinishing()) return;
                searchButton.setEnabled(true);
                if (found.isEmpty()) {
                    searchStatus.setText(R.string.settings_search_empty);
                    searchStatus.setVisibility(View.VISIBLE);
                    scrollToResults();
                    return;
                }
                searchStatus.setText(getString(
                        R.string.settings_search_results, found.size()));
                searchStatus.setVisibility(View.VISIBLE);
                showResults(found);
                scrollToResults();
            });
        });
    }

    private void showResults(List<CitySearchClient.Result> results) {
        searchResults.removeAllViews();
        for (CitySearchClient.Result result : results) {
            TextView row = new TextView(this);
            row.setText(resultLabel(result));
            row.setTextColor(0xFFFFFFFF);
            row.setTextSize(20);
            row.setSingleLine(true);
            row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackgroundResource(R.drawable.bg_search_result);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> selectCity(result));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(4);
            searchResults.addView(row, params);
        }
    }

    private void selectCity(CitySearchClient.Result result) {
        prefs.saveLocation(result.name, result.lat, result.lon);
        currentCity.setText(result.name);
        searchStatus.setText(getString(R.string.settings_city_saved, result.name));
        searchStatus.setVisibility(View.VISIBLE);
        searchResults.removeAllViews();
    }

    /**
     * Consulta manual de actualizaciones. Si hay una nueva, se pone la marca
     * a cero para que MainActivity la descargue al volver y muestre el aviso.
     */
    private void checkForUpdates() {
        checkUpdatesButton.setEnabled(false);
        updateStatus.setText(R.string.update_checking);
        updateStatus.setVisibility(View.VISIBLE);
        settingsScroll.post(() ->
                settingsScroll.smoothScrollTo(0, checkUpdatesButton.getTop()));

        updateExecutor.execute(() -> {
            try {
                UpdateClient.UpdateInfo info = UpdateClient.fetch();
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    checkUpdatesButton.setEnabled(true);
                    if (info.isNewerThan(BuildConfig.VERSION_CODE)) {
                        prefs.saveLastUpdateCheck(0);
                        updateStatus.setText(getString(
                                R.string.update_available, info.versionName));
                    } else {
                        updateStatus.setText(getString(
                                R.string.update_up_to_date, BuildConfig.VERSION_NAME));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    checkUpdatesButton.setEnabled(true);
                    updateStatus.setText(R.string.update_check_failed);
                });
            }
        });
    }

    private void updateDiagnostics() {
        long now = System.currentTimeMillis();
        Diagnostics.Snapshot diag = Diagnostics.get().snapshot();

        MatchesSnapshot cached = null;
        if (diag.matchesAt <= 0) {
            cached = new MatchCache(this).load(LocalDate.now());
        }

        long matchesAt = diag.matchesAt;
        long duration = diag.matchesDurationMs;
        int matchCount = diag.matchCount;
        Map<String, SourceStatus> sources = diag.sources;
        long fetchedAt = diag.fetchedAt;
        boolean stale = diag.stale;

        if (matchesAt <= 0 && cached != null) {
            matchesAt = cached.fetchedAt;
            matchCount = cached.matches.size();
            sources = cached.sources;
            fetchedAt = cached.fetchedAt;
            stale = cached.stale;
        }

        if (matchesAt > 0) {
            String line = getString(R.string.diag_matches,
                    Diagnostics.ageLabel(matchesAt, now), duration, matchCount);
            if (stale) line += getString(R.string.diag_stale_suffix);
            diagnosticsMatches.setText(line);
        } else {
            diagnosticsMatches.setText(R.string.diag_no_data);
        }

        long weatherAt = diag.weatherAt > 0
                ? diag.weatherAt : weatherSavedAt();
        if (weatherAt > 0) {
            diagnosticsWeather.setText(getString(R.string.diag_weather,
                    Diagnostics.ageLabel(weatherAt, now), timeLabel(weatherAt)));
        } else {
            diagnosticsWeather.setText(R.string.diag_no_data);
        }

        diagnosticsSources.setText(sourceLines(sources));
        if (fetchedAt > 0) {
            diagnosticsCache.setText(getString(R.string.diag_cache,
                    Diagnostics.ageLabel(fetchedAt, now), matchCount));
        } else {
            diagnosticsCache.setText(R.string.diag_no_data);
        }

        LogoLoader.Stats stats = LogoLoader.stats();
        diagnosticsLogos.setText(getString(R.string.diag_logos,
                stats.memoryHits, stats.diskHits, stats.downloads,
                stats.failures, stats.negativeHits));
    }

    private void requestRefreshNow() {
        prefs.requestRefresh();
        Toast.makeText(this, R.string.diag_refresh_requested, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void clearLogoCache() {
        LogoLoader.clearDiskCache(this);
        LogoLoader.clearNegativeCache();
        LogoLoader.clearMemoryCache();
        updateDiagnostics();
        diagnosticsStatus.setText(R.string.diag_logos_cleared);
        diagnosticsStatus.setVisibility(View.VISIBLE);
    }

    private String sourceLines(Map<String, SourceStatus> sources) {
        if (sources == null || sources.isEmpty()) {
            return getString(R.string.diag_no_data);
        }

        StringBuilder out = new StringBuilder();
        for (String id : new String[]{
                Scores365Source.ID, RfefSource.ID, CeltaSource.ID}) {
            if (out.length() > 0) out.append('\n');
            out.append(sourceLine(id, sources.get(id)));
        }
        for (Map.Entry<String, SourceStatus> entry : sources.entrySet()) {
            String id = entry.getKey();
            if (Scores365Source.ID.equals(id) || RfefSource.ID.equals(id)
                    || CeltaSource.ID.equals(id)) {
                continue;
            }
            out.append('\n').append(sourceLine(id, entry.getValue()));
        }
        return out.toString();
    }

    private String sourceLine(String id, SourceStatus status) {
        String name = sourceDisplayName(id);
        if (status == null) {
            return getString(R.string.diag_source_line, name,
                    getString(R.string.diag_source_empty), 0L, 0, "");
        }

        StringBuilder suffix = new StringBuilder();
        if (status.fromCache) suffix.append(getString(R.string.diag_source_cache));
        if (status.error != null && !status.error.isEmpty()) {
            suffix.append(getString(R.string.diag_source_error_suffix,
                    shortError(status.error)));
        }
        return getString(R.string.diag_source_line, name,
                stateLabel(status.state), status.durationMs,
                status.matchCount, suffix.toString());
    }

    private String stateLabel(SourceState state) {
        if (state == null) return getString(R.string.diag_source_error);
        switch (state) {
            case OK: return getString(R.string.diag_source_ok);
            case EMPTY: return getString(R.string.diag_source_empty);
            case TIMEOUT: return getString(R.string.diag_source_timeout);
            case HTTP_ERROR: return getString(R.string.diag_source_http);
            case PARSE_ERROR: return getString(R.string.diag_source_parse);
            case OFFLINE: return getString(R.string.diag_source_offline);
            default: return getString(R.string.diag_source_error);
        }
    }

    private static String sourceDisplayName(String id) {
        if (Scores365Source.ID.equals(id)) return "365Scores";
        if (RfefSource.ID.equals(id)) return "RFEF";
        if (CeltaSource.ID.equals(id)) return "Celta";
        if ("repository".equals(id)) return "Repositorio";
        return id == null ? "?" : id;
    }

    private long weatherSavedAt() {
        try {
            return new JSONObject(prefs.weatherCache()).optLong("savedAt", 0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String timeLabel(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(millis));
    }

    private static String shortError(String error) {
        if (error == null) return "";
        return error.length() <= 48 ? error : error.substring(0, 48) + "…";
    }

    private void save() {
        String start = nightStartInput.getText().toString().trim();
        String end = nightEndInput.getText().toString().trim();
        boolean valid = true;

        nightStartInput.setError(null);
        nightEndInput.setError(null);

        if (!NightSchedule.isValidHhMm(start)) {
            nightStartInput.setError(getString(R.string.settings_night_invalid));
            valid = false;
        }
        if (!NightSchedule.isValidHhMm(end)) {
            nightEndInput.setError(getString(R.string.settings_night_invalid));
            valid = false;
        }
        if (!valid) return;

        prefs.saveShowMatches(showMatchesInput.isChecked());
        prefs.saveNight(nightEnabledInput.isChecked(), start, end);
        prefs.saveTestDate(BuildConfig.DEBUG
                ? testDateInput.getText().toString().trim() : null);
        finish();
    }

    private static String resultLabel(CitySearchClient.Result result) {
        StringBuilder label = new StringBuilder(
                result.name == null ? "" : result.name);
        String region = result.admin1 == null ? "" : result.admin1.trim();
        String country = result.country == null ? "" : result.country.trim();

        if (!country.isEmpty()) {
            region = region.isEmpty() ? country : region + ", " + country;
        }
        if (!region.isEmpty()) {
            label.append(" · ").append(region);
        }
        return label.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(cityQuery.getWindowToken(), 0);
        }
    }

    private void scrollToResults() {
        settingsScroll.post(() ->
                settingsScroll.smoothScrollTo(0, searchStatus.getTop()));
    }
}
