package com.itriguero.homeclock;

import com.itriguero.homeclock.TeamSearchClient.TeamSearchException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

/**
 * Ajustes de la app: lista editable de ciudades, búsqueda, partidos y modo
 * noche. Las operaciones sobre la lista se guardan al instante; Guardar
 * persiste el resto.
 */
public class SettingsActivity extends Activity {

    private static final String[] KNOWN_SOURCES = {
            Scores365Source.ID, RfefSource.ID, CeltaSource.ID};

    private static final long TEAM_SEARCH_DEBOUNCE_MS = 350L;

    private Prefs prefs;

    private CityList cityList;
    private LinearLayout cityListContainer;
    private TextView cityListCount;
    private TextView cityListStatus;
    private EditText cityQuery;
    private Button searchButton;
    private TextView searchStatus;
    private LinearLayout searchResults;

    private TrackedTeamList teamList;
    private LinearLayout teamListContainer;
    private TextView teamListCount;
    private TextView teamListHint;
    private TextView teamListStatus;
    private TextView teamsReadOnly;
    private Button addDefaultTeamsButton;
    private EditText teamQuery;
    private Button teamSearchButton;
    private TextView teamSearchStatus;
    private LinearLayout teamSearchResults;
    private Button addTeamByIdButton;
    private List<TrackedTeam> currentTeamResults = new ArrayList<>();

    private Button useLocationButton;
    private TextView useLocationStatus;
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
    private TextView diagnosticsPersisted;
    private TextView diagnosticsMemory;
    private TextView diagnosticsExit;
    private TextView diagnosticsHealth;
    private Button refreshNowButton;
    private Button clearLogosButton;
    private TextView diagnosticsStatus;

    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService locationExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService teamSearchExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService teamLogoExecutor = Executors.newSingleThreadExecutor();
    private final Handler teamDebounceHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean detectingLocation = new AtomicBoolean(false);
    private Runnable pendingTeamSearch;
    private int searchToken;
    private int locationToken;
    private int teamSearchToken;

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
        teamListContainer = findViewById(R.id.teamList);
        teamListCount = findViewById(R.id.teamListCount);
        teamListHint = findViewById(R.id.teamListHint);
        teamListStatus = findViewById(R.id.teamListStatus);
        teamsReadOnly = findViewById(R.id.teamsReadOnly);
        addDefaultTeamsButton = findViewById(R.id.addDefaultTeamsButton);
        teamQuery = findViewById(R.id.teamQuery);
        teamSearchButton = findViewById(R.id.teamSearchButton);
        teamSearchStatus = findViewById(R.id.teamSearchStatus);
        teamSearchResults = findViewById(R.id.teamSearchResults);
        addTeamByIdButton = findViewById(R.id.addTeamByIdButton);
        useLocationButton = findViewById(R.id.useLocationButton);
        useLocationStatus = findViewById(R.id.useLocationStatus);
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
        diagnosticsPersisted = findViewById(R.id.diagnosticsPersisted);
        diagnosticsMemory = findViewById(R.id.diagnosticsMemory);
        diagnosticsExit = findViewById(R.id.diagnosticsExit);
        diagnosticsHealth = findViewById(R.id.diagnosticsHealth);
        refreshNowButton = findViewById(R.id.refreshNowButton);
        clearLogosButton = findViewById(R.id.clearLogosButton);
        diagnosticsStatus = findViewById(R.id.diagnosticsStatus);

        cityList = prefs.loadCities();
        renderCityList();

        teamList = prefs.loadTeams();
        renderTeamList();
        applyTeamsReadOnlyControls();

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
        useLocationButton.setOnClickListener(v -> startLocationDetection());
        cityQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startSearch();
                return true;
            }
            return false;
        });

        teamSearchButton.setOnClickListener(v -> {
            cancelPendingTeamSearch();
            startTeamSearch(true);
        });
        addDefaultTeamsButton.setOnClickListener(v -> addDefaultTeams());
        addTeamByIdButton.setOnClickListener(v -> showAddTeamByIdDialog());
        teamQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                cancelPendingTeamSearch();
                startTeamSearch(true);
                return true;
            }
            return false;
        });
        teamQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onTeamQueryChanged(s.toString());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // MainActivity puede haber cambiado la selección deslizando el reloj.
        cityList = prefs.loadCities();
        renderCityList();
        teamList = prefs.loadTeams();
        renderTeamList();
        applyTeamsReadOnlyControls();
        updateDiagnostics();
    }

    @Override
    protected void onDestroy() {
        searchToken++;
        locationToken++;
        teamSearchToken++;
        teamDebounceHandler.removeCallbacksAndMessages(null);
        searchExecutor.shutdownNow();
        updateExecutor.shutdownNow();
        locationExecutor.shutdownNow();
        teamSearchExecutor.shutdownNow();
        teamLogoExecutor.shutdownNow();
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

    private void startLocationDetection() {
        if (detectingLocation.get()) return;
        if (cityList.isReadOnly()) {
            showLocationStatus(R.string.settings_city_detect_readonly);
            return;
        }

        final int token = ++locationToken;
        detectingLocation.set(true);
        useLocationButton.setEnabled(false);
        useLocationButton.setText(R.string.settings_city_detecting);
        useLocationStatus.setText(R.string.settings_city_detecting_status);
        useLocationStatus.setVisibility(View.VISIBLE);

        locationExecutor.execute(() -> {
            try {
                IpLocationClient.Result result = IpLocationClient.fetch();
                runOnUiThread(() -> applyLocationResult(token, result));
            } catch (IpLocationClient.IpLocationException e) {
                runOnUiThread(() -> showLocationError(token, e));
            }
        });
    }

    /**
     * Aplica la ciudad detectada con la misma semántica que el arranque:
     * se reutiliza la entrada existente o se inserta al principio, sin borrar
     * ninguna ciudad guardada.
     */
    private void applyLocationResult(int token, IpLocationClient.Result result) {
        if (token != locationToken || isFinishing()) return;
        detectingLocation.set(false);
        resetLocationButton();

        cityList = prefs.loadCities();
        City detected = City.fromDetected(result);
        CityList.DetectedResult promoted = cityList.promoteDetected(detected);

        switch (promoted) {
            case ADDED_FIRST:
            case PROMOTED_EXISTING:
                prefs.saveCities(cityList);
                renderCityList();
                showLocationStatus(getString(
                        R.string.settings_city_detected, cityList.get(0).name));
                break;
            case ALREADY_FIRST:
                prefs.saveCities(cityList);
                renderCityList();
                showLocationStatus(getString(
                        R.string.settings_city_detected_existing, cityList.get(0).name));
                break;
            case FULL:
                showLocationStatus(getString(R.string.settings_city_detect_full,
                        detected == null ? "" : detected.name));
                break;
            case READ_ONLY:
                showLocationStatus(R.string.settings_city_detect_readonly);
                break;
            default:
                showLocationStatus(R.string.settings_city_detect_error);
                break;
        }
    }

    private void showLocationError(int token, IpLocationClient.IpLocationException error) {
        if (token != locationToken || isFinishing()) return;
        detectingLocation.set(false);
        resetLocationButton();

        if (error.kind == IpLocationClient.IpLocationException.Kind.OFFLINE
                || error.kind == IpLocationClient.IpLocationException.Kind.TIMEOUT) {
            showLocationStatus(R.string.settings_city_detect_offline);
        } else if (error.kind == IpLocationClient.IpLocationException.Kind.HTTP
                && error.httpStatus == 429) {
            showLocationStatus(R.string.settings_city_detect_unavailable);
        } else {
            showLocationStatus(R.string.settings_city_detect_error);
        }
    }

    private void resetLocationButton() {
        useLocationButton.setEnabled(true);
        useLocationButton.setText(R.string.settings_city_detect);
    }

    private void showLocationStatus(int stringRes) {
        showLocationStatus(getString(stringRes));
    }

    private void showLocationStatus(CharSequence text) {
        useLocationStatus.setText(text);
        useLocationStatus.setVisibility(View.VISIBLE);
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
            if (i == selectedIndex) {
                badges.add(getString(R.string.settings_city_badge_current));
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

    // ------------------------------------------------------------------
    // Equipos
    // ------------------------------------------------------------------

    private void onTeamQueryChanged(String rawQuery) {
        if (teamList == null || teamList.isReadOnly()) return;
        cancelPendingTeamSearch();

        String query = rawQuery == null ? "" : rawQuery.trim();
        teamSearchToken++;
        teamSearchButton.setEnabled(true);

        if (query.length() < 2) {
            currentTeamResults = new ArrayList<>();
            teamSearchResults.removeAllViews();
            teamSearchStatus.setText("");
            teamSearchStatus.setVisibility(View.GONE);
            return;
        }

        pendingTeamSearch = () -> {
            pendingTeamSearch = null;
            startTeamSearch(false);
        };
        teamDebounceHandler.postDelayed(pendingTeamSearch, TEAM_SEARCH_DEBOUNCE_MS);
    }

    private void cancelPendingTeamSearch() {
        if (pendingTeamSearch == null) return;
        teamDebounceHandler.removeCallbacks(pendingTeamSearch);
        pendingTeamSearch = null;
    }

    /** Invalida la búsqueda en curso y limpia resultados y estado. */
    private void clearTeamSearch() {
        teamSearchToken++;
        teamSearchButton.setEnabled(teamList == null || !teamList.isReadOnly());
        currentTeamResults = new ArrayList<>();
        teamSearchResults.removeAllViews();
        teamSearchStatus.setText("");
        teamSearchStatus.setVisibility(View.GONE);
    }

    private void startTeamSearch(boolean fromButton) {
        final String query = teamQuery.getText().toString().trim();
        if (fromButton) hideKeyboard(teamQuery);

        if (query.length() < 2) {
            clearTeamSearch();
            teamSearchStatus.setText(R.string.settings_team_search_required);
            teamSearchStatus.setVisibility(View.VISIBLE);
            return;
        }

        final int token = ++teamSearchToken;
        teamSearchButton.setEnabled(false);
        teamSearchStatus.setText(R.string.settings_team_searching);
        teamSearchStatus.setVisibility(View.VISIBLE);
        teamSearchResults.removeAllViews();
        currentTeamResults = new ArrayList<>();
        scrollToTeamResults();

        teamSearchExecutor.execute(() -> {
            final List<TrackedTeam> curated = TeamSearchClient.curated(query);
            List<TrackedTeam> remote = Collections.emptyList();
            TeamSearchException error = null;
            try {
                remote = TeamSearchClient.search(query);
            } catch (TeamSearchException e) {
                error = e;
            }
            final List<TrackedTeam> remoteResult = remote;
            final TeamSearchException searchError = error;
            runOnUiThread(() -> applyTeamSearchResult(
                    token, curated, remoteResult, searchError));
        });
    }

    private void applyTeamSearchResult(int token, List<TrackedTeam> curated,
                                       List<TrackedTeam> remote,
                                       TeamSearchException error) {
        if (token != teamSearchToken || isFinishing() || isDestroyed()) return;

        teamSearchButton.setEnabled(!teamList.isReadOnly());

        // La sugerencia local va primero y los remotos no la duplican.
        LinkedHashMap<String, TrackedTeam> merged = new LinkedHashMap<>();
        for (TrackedTeam team : curated) merged.put(team.key(), team);
        for (TrackedTeam team : remote) {
            if (!merged.containsKey(team.key())) merged.put(team.key(), team);
        }
        currentTeamResults = new ArrayList<>(merged.values());

        if (currentTeamResults.isEmpty()) {
            teamSearchStatus.setText(error == null
                    ? getString(R.string.settings_team_search_empty)
                    : errorMessage(error, false));
            teamSearchStatus.setVisibility(View.VISIBLE);
            scrollToTeamResults();
            return;
        }

        showTeamSearchResults(currentTeamResults);
        teamSearchStatus.setText(error == null
                ? getString(R.string.settings_team_search_results,
                        currentTeamResults.size())
                : errorMessage(error, !curated.isEmpty()));
        teamSearchStatus.setVisibility(View.VISIBLE);
        scrollToTeamResults();
    }

    private String errorMessage(TeamSearchException error, boolean hasCurated) {
        switch (error.kind) {
            case OFFLINE:
                return getString(hasCurated
                        ? R.string.settings_team_search_offline
                        : R.string.settings_team_search_error);
            case TIMEOUT:
                return getString(R.string.settings_team_search_timeout);
            case HTTP:
                return getString(error.httpStatus == 429
                        ? R.string.settings_team_search_rate
                        : R.string.settings_team_search_http);
            case PARSE:
                return getString(R.string.settings_team_search_parse);
            case NOT_FOUND:
                return getString(R.string.settings_team_search_not_found);
            case INVALID_QUERY:
                return getString(R.string.settings_team_search_required);
            default:
                return getString(R.string.settings_team_search_error);
        }
    }

    private void showTeamSearchResults(List<TrackedTeam> results) {
        teamSearchResults.removeAllViews();
        if (results == null || results.isEmpty()) return;

        int[] sports = {TrackedTeam.SPORT_FOOTBALL, TrackedTeam.SPORT_BASKETBALL};
        int[] headers = {R.string.settings_team_group_football,
                R.string.settings_team_group_basketball};

        for (int group = 0; group < sports.length; group++) {
            List<TrackedTeam> groupTeams = new ArrayList<>();
            for (TrackedTeam team : results) {
                if (team.sportId == sports[group]) groupTeams.add(team);
            }
            if (groupTeams.isEmpty()) continue;

            TextView header = new TextView(this);
            header.setText(headers[group]);
            header.setTextColor(0xFF808080);
            header.setTextSize(14);
            header.setPadding(dp(4), dp(8), dp(4), dp(4));
            teamSearchResults.addView(header);

            for (TrackedTeam team : groupTeams) {
                View row = getLayoutInflater().inflate(
                        R.layout.view_team_result_row, teamSearchResults, false);

                loadTeamLogo(team.logoUrl, row.findViewById(R.id.teamResultLogo));
                ((TextView) row.findViewById(R.id.teamResultName))
                        .setText(team.name);

                String detail = teamResultDetail(team);
                TextView detailView = row.findViewById(R.id.teamResultDetail);
                detailView.setText(detail);
                detailView.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);

                TrackedTeam existing = teamList.find(team.key());
                int actionRes;
                int actionColor;
                if (existing == null) {
                    actionRes = R.string.settings_team_result_add;
                    actionColor = 0xFFE0A85A;
                } else if (existing.enabled) {
                    actionRes = R.string.settings_team_result_added;
                    actionColor = 0xFF808080;
                } else {
                    actionRes = R.string.settings_team_result_paused;
                    actionColor = 0xFFE0A85A;
                }

                TextView action = row.findViewById(R.id.teamResultAction);
                action.setText(actionRes);
                action.setTextColor(actionColor);
                if (teamList.isReadOnly()) {
                    action.setAlpha(0.5f);
                    row.setClickable(false);
                } else {
                    row.setOnClickListener(v -> addTeam(team));
                }

                List<String> desc = new ArrayList<>();
                desc.add(team.name);
                if (!detail.isEmpty()) desc.add(detail);
                desc.add(getString(actionRes));
                if (TrackedTeam.PROVIDER_AS_CELTAS.equals(team.provider)) {
                    desc.add(getString(R.string.settings_team_curated));
                }
                row.setContentDescription(TextUtils.join(" · ", desc));

                LinearLayout.LayoutParams params =
                        (LinearLayout.LayoutParams) row.getLayoutParams();
                if (params != null) params.topMargin = dp(4);
                teamSearchResults.addView(row);
            }
        }
    }

    private static String teamResultDetail(TrackedTeam team) {
        List<String> parts = new ArrayList<>(3);
        if (!team.competition.isEmpty()) parts.add(team.competition);
        String sport = team.sport();
        if (!sport.isEmpty()) parts.add(sport);
        if (!team.country.isEmpty()) parts.add(team.country);
        return TextUtils.join(" · ", parts);
    }

    /** Reescribe las filas ya visibles para reflejar altas, bajas y pausas. */
    private void refreshTeamSearchResults() {
        if (currentTeamResults == null || currentTeamResults.isEmpty()) return;
        if (isFinishing() || isDestroyed()) return;
        showTeamSearchResults(currentTeamResults);
    }

    private void renderTeamList() {
        int scrollY = settingsScroll.getScrollY();
        teamListContainer.removeAllViews();

        boolean readOnly = teamList.isReadOnly();
        int size = teamList.size();
        int active = teamList.activeCount();

        teamsReadOnly.setVisibility(readOnly ? View.VISIBLE : View.GONE);
        teamListCount.setText(getString(R.string.settings_teams_count,
                size, TrackedTeamList.MAX_TEAMS, active));

        if (size == 0) {
            teamListHint.setText(R.string.settings_teams_empty);
            teamListHint.setVisibility(View.VISIBLE);
        } else if (active == 0) {
            teamListHint.setText(R.string.settings_teams_all_paused);
            teamListHint.setVisibility(View.VISIBLE);
        } else {
            teamListHint.setText("");
            teamListHint.setVisibility(View.GONE);
        }

        addDefaultTeamsButton.setVisibility(
                !readOnly && hasMissingDefaults() ? View.VISIBLE : View.GONE);

        for (int i = 0; i < size; i++) {
            TrackedTeam team = teamList.get(i);
            View row = getLayoutInflater().inflate(
                    R.layout.view_team_row, teamListContainer, false);

            loadTeamLogo(team.logoUrl, row.findViewById(R.id.teamLogo));

            TextView name = row.findViewById(R.id.teamName);
            name.setText(team.name);

            String detail = team.settingsDetail();
            TextView detailView = row.findViewById(R.id.teamDetail);
            detailView.setText(detail);
            detailView.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);

            String badge = team.badge();
            List<String> badges = new ArrayList<>();
            if (!badge.isEmpty()) badges.add(badge);
            if (!team.enabled) badges.add(getString(R.string.settings_team_paused));
            TextView badgesView = row.findViewById(R.id.teamBadges);
            badgesView.setText(TextUtils.join(" · ", badges));
            badgesView.setVisibility(badges.isEmpty() ? View.GONE : View.VISIBLE);

            // Los equipos en pausa se atenúan, salvo el control de seguir.
            float alpha = team.enabled ? 1f : 0.5f;
            name.setAlpha(alpha);
            detailView.setAlpha(alpha);
            badgesView.setAlpha(alpha);

            CheckBox follow = row.findViewById(R.id.teamEnabled);
            follow.setOnCheckedChangeListener(null);
            follow.setChecked(team.enabled);
            follow.setContentDescription(getString(
                    R.string.settings_team_follow_desc, team.name));
            follow.setEnabled(!readOnly);
            follow.setOnCheckedChangeListener((button, checked) ->
                    setTeamEnabled(team, checked));

            TextView delete = row.findViewById(R.id.teamDelete);
            delete.setContentDescription(getString(
                    R.string.settings_team_delete_desc, team.name));
            delete.setEnabled(!readOnly);
            delete.setAlpha(readOnly ? 0.4f : 1f);
            if (!readOnly) delete.setOnClickListener(v -> confirmRemoveTeam(team));

            List<String> desc = new ArrayList<>();
            desc.add(team.name);
            if (!detail.isEmpty()) desc.add(detail);
            desc.add(getString(team.enabled
                    ? R.string.settings_team_state_active
                    : R.string.settings_team_state_paused));
            if (!badge.isEmpty()) desc.add(badge);
            row.setContentDescription(TextUtils.join(" · ", desc));
            if (readOnly) {
                row.setClickable(false);
            } else {
                // Toda la fila alterna el seguimiento, como el checkbox.
                row.setOnClickListener(v -> setTeamEnabled(team, !team.enabled));
            }

            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) row.getLayoutParams();
            if (params != null) params.topMargin = i == 0 ? 0 : dp(4);
            teamListContainer.addView(row);
        }

        refreshTeamSearchResults();
        settingsScroll.post(() -> settingsScroll.scrollTo(0, scrollY));
    }

    private boolean hasMissingDefaults() {
        for (TrackedTeam team : TrackedTeam.defaults()) {
            if (teamList.indexOf(team.key()) < 0) return true;
        }
        return false;
    }

    private void setTeamEnabled(TrackedTeam team, boolean enabled) {
        if (teamList.isReadOnly()) {
            showTeamStatus(R.string.settings_teams_readonly);
            return;
        }
        if (!teamList.setEnabled(team.key(), enabled)) return;

        prefs.saveTeams(teamList);
        renderTeamList();
        showTeamStatus(getString(enabled
                ? R.string.settings_team_enabled
                : R.string.settings_team_paused_status, team.name));
    }

    private void confirmRemoveTeam(TrackedTeam team) {
        if (teamList.isReadOnly()) {
            showTeamStatus(R.string.settings_teams_readonly);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_team_remove_title)
                .setMessage(getString(
                        R.string.settings_team_remove_message, team.name))
                .setPositiveButton(R.string.settings_team_remove_confirm,
                        (dialog, which) -> removeTeam(team))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void removeTeam(TrackedTeam team) {
        TrackedTeamList.RemoveResult result = teamList.remove(team.key());
        if (result == TrackedTeamList.RemoveResult.READ_ONLY) {
            showTeamStatus(R.string.settings_teams_readonly);
            return;
        }
        if (result != TrackedTeamList.RemoveResult.REMOVED) return;

        prefs.saveTeams(teamList);
        renderTeamList();
        showTeamStatus(getString(R.string.settings_team_removed, team.name));
    }

    private void addTeam(TrackedTeam team) {
        if (teamList.isReadOnly()) {
            showTeamStatus(R.string.settings_teams_readonly);
            return;
        }

        TrackedTeam existing = teamList.find(team.key());
        if (existing != null) {
            if (existing.enabled) {
                showTeamStatus(getString(R.string.settings_team_exists, team.name));
                return;
            }
            if (!teamList.setEnabled(team.key(), true)) return;
            prefs.saveTeams(teamList);
            renderTeamList();
            showTeamStatus(getString(R.string.settings_team_reactivated, team.name));
            return;
        }

        TrackedTeamList.AddResult result = teamList.add(team);
        switch (result) {
            case ADDED:
                prefs.saveTeams(teamList);
                renderTeamList();
                showTeamStatus(getString(R.string.settings_team_added, team.name));
                break;
            case DUPLICATE:
                showTeamStatus(getString(R.string.settings_team_exists, team.name));
                break;
            case FULL:
                showTeamStatus(R.string.settings_teams_full);
                break;
            case READ_ONLY:
                showTeamStatus(R.string.settings_teams_readonly);
                break;
            default:
                showTeamStatus(R.string.settings_team_invalid);
                break;
        }
        refreshTeamSearchResults();
    }

    private void addDefaultTeams() {
        if (teamList.isReadOnly()) {
            showTeamStatus(R.string.settings_teams_readonly);
            return;
        }

        int added = 0;
        for (TrackedTeam team : TrackedTeam.defaults()) {
            if (teamList.indexOf(team.key()) >= 0) continue;
            TrackedTeamList.AddResult result = teamList.add(team);
            if (result == TrackedTeamList.AddResult.ADDED) {
                added++;
            } else if (result == TrackedTeamList.AddResult.FULL) {
                break;
            } else if (result == TrackedTeamList.AddResult.READ_ONLY) {
                showTeamStatus(R.string.settings_teams_readonly);
                return;
            }
        }

        if (added > 0) {
            prefs.saveTeams(teamList);
            renderTeamList();
            showTeamStatus(getString(R.string.settings_teams_defaults_added, added));
        } else if (hasMissingDefaults()
                && teamList.size() >= TrackedTeamList.MAX_TEAMS) {
            showTeamStatus(R.string.settings_teams_full);
        } else {
            showTeamStatus(R.string.settings_teams_defaults_none);
        }
        refreshTeamSearchResults();
    }

    private void showAddTeamByIdDialog() {
        if (teamList.isReadOnly()) {
            showTeamStatus(R.string.settings_teams_readonly);
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(24);
        box.setPadding(horizontal, dp(8), horizontal, 0);

        final EditText input = new EditText(this);
        input.setHint(R.string.settings_team_by_id_hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF808080);
        input.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(12)});
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final TextView status = new TextView(this);
        status.setTextColor(0xFFE0A85A);
        status.setTextSize(14);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        box.addView(status, statusParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_team_by_id_title)
                .setView(box)
                .setPositiveButton(R.string.settings_team_result_add, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String raw = input.getText().toString().trim();
                            long id;
                            try {
                                id = Long.parseLong(raw);
                            } catch (NumberFormatException e) {
                                id = 0L;
                            }
                            if (id <= 0L) {
                                status.setText(R.string.settings_team_by_id_invalid);
                                status.setVisibility(View.VISIBLE);
                                return;
                            }

                            final long teamId = id;
                            input.setEnabled(false);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(false);
                            status.setText(R.string.settings_team_by_id_working);
                            status.setVisibility(View.VISIBLE);

                            teamSearchExecutor.execute(() -> {
                                TrackedTeam found = null;
                                TeamSearchException error = null;
                                try {
                                    found = TeamSearchClient.fetchById(teamId);
                                } catch (TeamSearchException e) {
                                    error = e;
                                }
                                final TrackedTeam result = found;
                                final TeamSearchException failure = error;
                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    // Si se cerró el diálogo, no se añade nada.
                                    if (!dialog.isShowing()) return;
                                    if (result != null) {
                                        dialog.dismiss();
                                        addTeam(result);
                                        return;
                                    }
                                    input.setEnabled(true);
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                            .setEnabled(true);
                                    if (failure != null && failure.kind
                                            == TeamSearchException.Kind.NOT_FOUND) {
                                        status.setText(
                                                R.string.settings_team_by_id_not_found);
                                    } else {
                                        status.setText(failure == null
                                                ? getString(
                                                        R.string.settings_team_search_error)
                                                : errorMessage(failure, false));
                                    }
                                    status.setVisibility(View.VISIBLE);
                                });
                            });
                        }));
        dialog.show();
    }

    private void applyTeamsReadOnlyControls() {
        boolean readOnly = teamList.isReadOnly();
        teamsReadOnly.setVisibility(readOnly ? View.VISIBLE : View.GONE);
        teamQuery.setEnabled(!readOnly);
        teamSearchButton.setEnabled(!readOnly);
        addTeamByIdButton.setEnabled(!readOnly);
        addDefaultTeamsButton.setVisibility(
                !readOnly && hasMissingDefaults() ? View.VISIBLE : View.GONE);
    }

    private void showTeamStatus(int stringRes) {
        showTeamStatus(getString(stringRes));
    }

    private void showTeamStatus(CharSequence text) {
        teamListStatus.setText(text);
        teamListStatus.setVisibility(View.VISIBLE);
    }

    private void scrollToTeamResults() {
        settingsScroll.post(() ->
                settingsScroll.smoothScrollTo(0, teamSearchStatus.getTop()));
    }

    private void loadTeamLogo(String url, ImageView view) {
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

        Context app = getApplicationContext();
        teamLogoExecutor.execute(() -> {
            Bitmap bitmap = LogoLoader.load(app, url);
            if (bitmap == null) return;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (url.equals(view.getTag())) view.setImageBitmap(bitmap);
            });
        });
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
        DiagnosticsState persisted = DiagnosticsStore.get(this).state();
        DiagnosticsState.MatchesInfo savedMatches = persisted.matches;

        boolean savedMatchesAvailable = savedMatches.finishedAt > 0L;
        MatchesSnapshot cached = null;
        if (diag.matchesAt <= 0L && !savedMatchesAvailable) {
            cached = new MatchCache(this).load(LocalDate.now());
        }

        long matchesAt = diag.matchesAt;
        String matchesDate = diag.matchesDate;
        long duration = diag.matchesDurationMs;
        int matchCount = diag.matchCount;
        long fetchedAt = diag.fetchedAt;
        boolean stale = diag.stale;
        boolean partial = false;

        if (matchesAt <= 0L && savedMatchesAvailable) {
            matchesAt = savedMatches.finishedAt;
            matchesDate = savedMatches.date;
            duration = savedMatches.durationMs;
            matchCount = savedMatches.matchCount;
            fetchedAt = savedMatches.fetchedAt;
            stale = savedMatches.stale;
            partial = savedMatches.partial;
        }

        if (matchesAt <= 0L && cached != null) {
            matchesAt = cached.fetchedAt;
            matchesDate = cached.date == null ? "" : cached.date.toString();
            matchCount = cached.matches.size();
            fetchedAt = cached.fetchedAt;
            stale = cached.stale;
        }

        if (matchesAt > 0L) {
            StringBuilder suffix = new StringBuilder();
            if (stale) suffix.append(getString(R.string.diag_stale_suffix));
            if (partial) {
                suffix.append(getString(R.string.diag_matches_partial_suffix));
            }

            String date = dateLabel(matchesDate, matchesAt);
            String line;
            if (!date.isEmpty()) {
                line = getString(R.string.diag_matches_dated,
                        date, Diagnostics.ageLabel(matchesAt, now), duration,
                        matchCount, suffix.toString());
            } else {
                StringBuilder plain = new StringBuilder(getString(
                        R.string.diag_matches, Diagnostics.ageLabel(matchesAt, now),
                        duration, matchCount));
                plain.append(suffix);
                line = plain.toString();
            }
            diagnosticsMatches.setText(line);
        } else {
            diagnosticsMatches.setText(R.string.diag_no_data);
        }

        boolean liveWeather = diag.weatherAt > 0L
                || (diag.weatherState != null && !diag.weatherState.isEmpty());
        DiagnosticsState.WeatherInfo savedWeather = persisted.weather;
        boolean savedWeatherAvailable = savedWeather.finishedAt > 0L
                || savedWeather.fetchedAt > 0L
                || savedWeather.cityCount > 0
                || savedWeather.consecutiveFailures > 0
                || savedWeather.lastOkAt > 0L
                || savedWeather.lastErrorAt > 0L;

        long savedWeatherAt = 0L;
        if (!liveWeather && !savedWeatherAvailable) {
            // Compatibilidad: caché antigua cuando aún no hay estado persistido.
            savedWeatherAt = weatherSavedAt();
        }

        if (liveWeather || savedWeatherAvailable) {
            long weatherAt = liveWeather
                    ? (diag.weatherAt > 0L ? diag.weatherAt : diag.weatherAttemptAt)
                    : (savedWeather.fetchedAt > 0L
                            ? savedWeather.fetchedAt : savedWeather.finishedAt);
            String state = liveWeather ? diag.weatherState : savedWeather.state;
            String error = liveWeather ? diag.weatherError : savedWeather.error;
            int cities = liveWeather
                    ? diag.weatherCityCount : savedWeather.cityCount;

            String detail;
            if (error != null && !error.isEmpty()) {
                detail = shortError(error);
            } else {
                detail = getString(R.string.diag_weather_cities, cities);
            }
            diagnosticsWeather.setText(getString(R.string.diag_weather_state,
                    Diagnostics.ageLabel(weatherAt, now),
                    weatherStateLabel(state), detail));
        } else if (savedWeatherAt > 0L) {
            diagnosticsWeather.setText(getString(R.string.diag_weather,
                    Diagnostics.ageLabel(savedWeatherAt, now),
                    timeLabel(savedWeatherAt)));
        } else {
            diagnosticsWeather.setText(R.string.diag_weather_none);
        }

        Map<String, SourceHealth> health = diag.sourceHealth;
        if (health == null || health.isEmpty()) health = savedMatches.sources;
        if (health == null) health = Collections.emptyMap();

        if (!diag.sources.isEmpty()) {
            diagnosticsSources.setText(sourceLines(diag.sources, health, now));
        } else if (!savedMatches.sources.isEmpty()) {
            diagnosticsSources.setText(sourceLines(
                    Collections.emptyMap(), savedMatches.sources, now));
        } else if (cached != null && !cached.sources.isEmpty()) {
            diagnosticsSources.setText(sourceLines(cached.sources, health, now));
        } else if (!health.isEmpty()) {
            diagnosticsSources.setText(sourceLines(
                    Collections.emptyMap(), health, now));
        } else {
            diagnosticsSources.setText(R.string.diag_no_data);
        }

        if (fetchedAt > 0L) {
            diagnosticsCache.setText(getString(R.string.diag_cache,
                    Diagnostics.ageLabel(fetchedAt, now), matchCount));
        } else {
            diagnosticsCache.setText(R.string.diag_no_data);
        }

        LogoLoader.Stats stats = LogoLoader.stats();
        diagnosticsLogos.setText(getString(R.string.diag_logos,
                stats.memoryHits, stats.diskHits, stats.downloads,
                stats.failures, stats.negativeHits));

        diagnosticsPersisted.setText(getString(R.string.diag_persisted,
                Diagnostics.ageLabel(persisted.savedAt, now),
                persisted.process.sessionCount,
                Diagnostics.ageLabel(persisted.firstSeenAt, now)));

        DiagnosticsState.MemoryInfo memory = persisted.memory;
        if (memory.samples > 0) {
            diagnosticsMemory.setText(getString(R.string.diag_memory,
                    formatMb(memory.totalPssKb), formatMb(memory.maxPssKb),
                    formatMb(memory.javaKb), formatMb(memory.nativeKb),
                    formatMb(memory.codeKb)));
        } else {
            diagnosticsMemory.setText(R.string.diag_memory_none);
        }

        if (!persisted.exits.isEmpty()) {
            DiagnosticsState.ExitEvent exit = persisted.exits.get(0);
            diagnosticsExit.setText(getString(R.string.diag_exit,
                    exitCauseLabel(exit.cause),
                    Diagnostics.ageLabel(exit.at, now),
                    persisted.counters.anrs, persisted.counters.lmk,
                    persisted.counters.unknownExits));
        } else {
            diagnosticsExit.setText(R.string.diag_exit_none);
        }

        Map<String, SourceHealth> healthForDown = diag.sourceHealth == null
                || diag.sourceHealth.isEmpty()
                ? savedMatches.sources : diag.sourceHealth;
        List<String> down = new ArrayList<>();
        for (Map.Entry<String, SourceHealth> entry : healthForDown.entrySet()) {
            SourceHealth sourceHealth = entry.getValue();
            if (sourceHealth == null || !sourceHealth.isDown(now)) continue;
            String id = entry.getKey();
            if (id == null || id.isEmpty()) id = sourceHealth.sourceId;
            down.add(getString(R.string.diag_source_down,
                    sourceDisplayName(id),
                    Diagnostics.ageLabel(sourceHealth.downSince, now),
                    sourceHealth.consecutiveFailures));
        }
        if (down.isEmpty()) {
            diagnosticsHealth.setText(R.string.diag_health_none);
        } else {
            diagnosticsHealth.setText(getString(R.string.diag_health,
                    TextUtils.join("\n", down)));
        }
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

    private String sourceLines(Map<String, SourceStatus> sources,
                               Map<String, SourceHealth> health, long now) {
        Map<String, SourceStatus> statusMap = sources == null
                ? Collections.<String, SourceStatus>emptyMap() : sources;
        Map<String, SourceHealth> healthMap = health == null
                ? Collections.<String, SourceHealth>emptyMap() : health;
        if (statusMap.isEmpty() && healthMap.isEmpty()) {
            return getString(R.string.diag_no_data);
        }

        StringBuilder out = new StringBuilder();
        if (!statusMap.isEmpty()) {
            for (String id : KNOWN_SOURCES) {
                appendSourceLine(out, id, statusMap.get(id), healthMap, now);
            }
        }
        for (Map.Entry<String, SourceStatus> entry : statusMap.entrySet()) {
            if (isKnownSource(entry.getKey())) continue;
            appendSourceLine(out, entry.getKey(), entry.getValue(), healthMap, now);
        }
        for (Map.Entry<String, SourceHealth> entry : healthMap.entrySet()) {
            String id = entry.getKey();
            if (statusMap.containsKey(id)) continue;
            if (!statusMap.isEmpty() && isKnownSource(id)) continue;
            appendSourceLine(out, id, null, healthMap, now);
        }
        return out.toString();
    }

    private void appendSourceLine(StringBuilder out, String id, SourceStatus status,
                                  Map<String, SourceHealth> health, long now) {
        SourceHealth sourceHealth = health.get(id);
        String line;
        if (status != null) {
            line = sourceLine(id, status) + healthSuffix(sourceHealth, now);
        } else if (sourceHealth != null) {
            line = healthLine(id, sourceHealth, now);
        } else {
            line = sourceLine(id, null);
        }
        if (out.length() > 0) out.append('\n');
        out.append(line);
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

    /** Línea construida solo con la salud persistida de una fuente. */
    private String healthLine(String id, SourceHealth health, long now) {
        StringBuilder suffix = new StringBuilder();
        if (health.fromCache) suffix.append(getString(R.string.diag_source_cache));
        if (health.error != null && !health.error.isEmpty()) {
            suffix.append(getString(R.string.diag_source_error_suffix,
                    shortError(health.error)));
        }
        suffix.append(healthSuffix(health, now));
        return getString(R.string.diag_source_line, sourceDisplayName(id),
                stateLabel(health.state), health.durationMs, health.matchCount,
                suffix.toString());
    }

    private String healthSuffix(SourceHealth health, long now) {
        if (health == null) return "";
        StringBuilder suffix = new StringBuilder();
        if (health.consecutiveFailures > 0) {
            suffix.append(getString(R.string.diag_health_failures,
                    health.consecutiveFailures));
        }
        if (health.isDown(now)) {
            suffix.append(getString(R.string.diag_health_down));
        }
        return suffix.toString();
    }

    private static boolean isKnownSource(String id) {
        for (String known : KNOWN_SOURCES) {
            if (known.equals(id)) return true;
        }
        return false;
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

    private static String dateLabel(long millis) {
        return new SimpleDateFormat("dd/MM", Locale.getDefault())
                .format(new Date(millis));
    }

    /** Fecha ISO del snapshot (2026-11-14) a dd/MM; si no encaja, se deja tal cual. */
    private static String dateLabel(String isoDate, long fallbackMillis) {
        if (isoDate != null && !isoDate.isEmpty()) {
            try {
                return LocalDate.parse(isoDate).format(
                        DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault()));
            } catch (DateTimeParseException e) {
                return isoDate;
            }
        }
        return fallbackMillis > 0L ? dateLabel(fallbackMillis) : "";
    }

    private static String formatMb(int kb) {
        return String.format(Locale.getDefault(), "%.1f MB", kb / 1024f);
    }

    private String exitCauseLabel(String cause) {
        if (cause == null) return getString(R.string.diag_exit_unknown);
        switch (cause) {
            case "crash":
            case "crash_native":
                return getString(R.string.diag_exit_crash);
            case "anr":
                return getString(R.string.diag_exit_anr);
            case "lmk":
                return getString(R.string.diag_exit_lmk);
            case "signal":
                return getString(R.string.diag_exit_signal);
            case "user":
                return getString(R.string.diag_exit_user);
            case "update":
                return getString(R.string.diag_exit_update);
            case "reboot":
                return getString(R.string.diag_exit_reboot);
            default:
                return getString(R.string.diag_exit_unknown);
        }
    }

    private String weatherStateLabel(String state) {
        if ("FRESH".equals(state)) {
            return getString(R.string.diag_weather_fresh);
        }
        if ("CACHED".equals(state)) {
            return getString(R.string.diag_weather_cached);
        }
        return getString(R.string.diag_weather_empty);
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
        hideKeyboard(cityQuery);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void scrollToResults() {
        settingsScroll.post(() ->
                settingsScroll.smoothScrollTo(0, searchStatus.getTop()));
    }
}
