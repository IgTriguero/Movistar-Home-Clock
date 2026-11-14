package com.itriguero.homeclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
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

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ajustes de la app: lista editable de ciudades, búsqueda, partidos y modo
 * noche. Las operaciones sobre la lista se guardan al instante; Guardar
 * persiste el resto.
 */
public class SettingsActivity extends Activity {

    private Prefs prefs;

    private CityList cityList;
    private LinearLayout cityListContainer;
    private TextView cityListCount;
    private TextView cityListStatus;
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

        cityListContainer = findViewById(R.id.cityList);
        cityListCount = findViewById(R.id.cityListCount);
        cityListStatus = findViewById(R.id.cityListStatus);
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

        cityList = prefs.loadCities();
        renderCityList();

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
        // MainActivity puede haber cambiado la selección deslizando el reloj.
        cityList = prefs.loadCities();
        renderCityList();
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

    private void renderCityList() {
        int scrollY = settingsScroll.getScrollY();
        cityListContainer.removeAllViews();

        int size = cityList.size();
        int selectedIndex = cityList.selectedIndex();
        cityListCount.setText(getString(
                R.string.settings_cities_count, size, CityList.MAX_CITIES));

        for (int i = 0; i < size; i++) {
            final int index = i;
            final City city = cityList.get(i);
            View row = getLayoutInflater().inflate(
                    R.layout.view_city_row, cityListContainer, false);

            TextView indexView = row.findViewById(R.id.cityIndex);
            indexView.setText(String.valueOf(i + 1));

            TextView nameView = row.findViewById(R.id.cityName);
            nameView.setText(city.name);

            TextView detailView = row.findViewById(R.id.cityDetail);
            String detail = cityDetail(city);
            detailView.setText(detail);
            detailView.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);

            List<String> badges = new ArrayList<>();
            if (i == 0) badges.add(getString(R.string.settings_city_badge_default));
            if (i == selectedIndex) {
                badges.add(getString(R.string.settings_city_badge_current));
            }
            if (city.source == City.Source.DETECTED) {
                badges.add(getString(R.string.settings_city_badge_detected));
            }
            TextView badgesView = row.findViewById(R.id.cityBadges);
            badgesView.setText(TextUtils.join(" · ", badges));
            badgesView.setVisibility(badges.isEmpty() ? View.GONE : View.VISIBLE);

            row.setContentDescription(getString(
                    R.string.settings_city_row_desc, city.name, i + 1, size));
            row.setOnClickListener(v -> selectCity(city.key()));

            TextView up = row.findViewById(R.id.cityUp);
            up.setContentDescription(getString(
                    R.string.settings_city_move_up_desc, city.name));
            boolean canMoveUp = i > 0;
            up.setEnabled(canMoveUp);
            up.setAlpha(canMoveUp ? 1f : 0.3f);
            if (canMoveUp) up.setOnClickListener(v -> moveCity(index, -1));

            TextView down = row.findViewById(R.id.cityDown);
            down.setContentDescription(getString(
                    R.string.settings_city_move_down_desc, city.name));
            boolean canMoveDown = i < size - 1;
            down.setEnabled(canMoveDown);
            down.setAlpha(canMoveDown ? 1f : 0.3f);
            if (canMoveDown) down.setOnClickListener(v -> moveCity(index, 1));

            TextView delete = row.findViewById(R.id.cityDelete);
            delete.setContentDescription(getString(
                    R.string.settings_city_delete_desc, city.name));
            delete.setOnClickListener(v -> confirmRemove(city));

            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) row.getLayoutParams();
            if (params != null) params.topMargin = i == 0 ? 0 : dp(4);
            cityListContainer.addView(row);
        }

        settingsScroll.post(() -> settingsScroll.scrollTo(0, scrollY));
    }

    private void showResults(List<CitySearchClient.Result> results) {
        searchResults.removeAllViews();
        for (CitySearchClient.Result result : results) {
            boolean alreadyAdded = cityList.indexOf(
                    City.keyOf(result.lat, result.lon)) >= 0;
            TextView row = new TextView(this);
            String label = resultLabel(result);
            row.setText(alreadyAdded
                    ? getString(R.string.settings_city_result_added, label)
                    : label);
            row.setTextColor(0xFFFFFFFF);
            row.setTextSize(20);
            row.setSingleLine(true);
            row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackgroundResource(R.drawable.bg_search_result);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> addCity(result));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(4);
            searchResults.addView(row, params);
        }
    }

    private void addCity(CitySearchClient.Result result) {
        City city = City.from(result, City.Source.MANUAL);
        if (city == null) {
            showCityStatus(R.string.settings_city_invalid);
            return;
        }

        CityList.AddResult addResult = cityList.add(city);
        switch (addResult) {
            case ADDED:
            case DUPLICATE:
                cityList.select(city.key());
                break;
            case FULL:
                showCityStatus(R.string.settings_city_full);
                return;
            case INVALID:
                showCityStatus(R.string.settings_city_invalid);
                return;
        }

        prefs.saveCities(cityList);
        String name = cityList.selected().name;
        clearSearch();
        renderCityList();
        showCityStatus(getString(addResult == CityList.AddResult.ADDED
                ? R.string.settings_city_added
                : R.string.settings_city_exists, name));
    }

    private void selectCity(String key) {
        if (!cityList.select(key)) return;
        prefs.saveSelection(cityList);
        renderCityList();
        showCityStatus(getString(
                R.string.settings_city_now, cityList.selected().name));
    }

    private void moveCity(int index, int delta) {
        if (!cityList.move(index, index + delta)) return;
        prefs.saveCities(cityList);
        renderCityList();
    }

    private void confirmRemove(City city) {
        if (cityList.size() <= 1) {
            showCityStatus(R.string.settings_city_last);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_city_remove_title)
                .setMessage(getString(
                        R.string.settings_city_remove_message, city.name))
                .setPositiveButton(R.string.settings_city_remove_confirm,
                        (dialog, which) -> removeCity(city))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void removeCity(City city) {
        CityList.RemoveResult result = cityList.remove(city.key());
        if (result == CityList.RemoveResult.REMOVED) {
            prefs.saveCities(cityList);
            renderCityList();
            showCityStatus(getString(R.string.settings_city_removed, city.name));
        } else if (result == CityList.RemoveResult.LAST) {
            showCityStatus(R.string.settings_city_last);
        }
    }

    private void showCityStatus(int stringRes) {
        showCityStatus(getString(stringRes));
    }

    private void showCityStatus(CharSequence text) {
        cityListStatus.setText(text);
        cityListStatus.setVisibility(View.VISIBLE);
    }

    private void clearSearch() {
        cityQuery.setText("");
        hideKeyboard();
        searchResults.removeAllViews();
        searchStatus.setText("");
        searchStatus.setVisibility(View.GONE);
    }

    private static String cityDetail(City city) {
        if (city.region.isEmpty()) return city.country;
        if (city.country.isEmpty() || city.region.equalsIgnoreCase(city.country)) {
            return city.region;
        }
        return city.region + ", " + city.country;
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
        return WeatherCache.decode(prefs.loadWeatherCache()).fetchedAt;
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
