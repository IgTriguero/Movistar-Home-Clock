package com.itriguero.homeclock;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder mutable para construir {@link DiagnosticsState} sin constructores
 * gigantes.
 *
 * Es puro Java (sin Android) y no valida más que la propia
 * {@link DiagnosticsState}: los límites y los valores imposibles se normalizan
 * al llamar a {@link #build()}. El store de diagnóstico lo usa para aplicar
 * cambios incrementales sobre el estado leído de disco.
 */
final class DiagnosticsStateBuilder {

    private long savedAt;
    private long firstSeenAt;
    private long appVersionCode;
    private String appVersionName = "";
    private int sdk;
    private DiagnosticsState.ProcessInfo process =
            DiagnosticsState.ProcessInfo.empty();
    private DiagnosticsState.MatchesInfo matches =
            DiagnosticsState.MatchesInfo.empty();
    private DiagnosticsState.WeatherInfo weather =
            DiagnosticsState.WeatherInfo.empty();
    private DiagnosticsState.MemoryInfo memory =
            DiagnosticsState.MemoryInfo.empty();
    private final List<DiagnosticsState.ExitEvent> exits = new ArrayList<>();
    private DiagnosticsState.Counters counters =
            DiagnosticsState.Counters.empty();
    private final List<String> exitScanKeys = new ArrayList<>();

    private DiagnosticsStateBuilder() {
    }

    /** Copia todos los campos de un estado existente; {@code null} = vacío. */
    static DiagnosticsStateBuilder from(DiagnosticsState state) {
        DiagnosticsStateBuilder builder = new DiagnosticsStateBuilder();
        if (state == null) return builder;

        builder.savedAt = state.savedAt;
        builder.firstSeenAt = state.firstSeenAt;
        builder.appVersionCode = state.appVersionCode;
        builder.appVersionName = state.appVersionName;
        builder.sdk = state.sdk;
        builder.process = state.process;
        builder.matches = state.matches;
        builder.weather = state.weather;
        builder.memory = state.memory;
        builder.exits.addAll(state.exits);
        builder.counters = state.counters;
        builder.exitScanKeys.addAll(state.exitScanKeys);
        return builder;
    }

    DiagnosticsStateBuilder savedAt(long savedAt) {
        this.savedAt = savedAt;
        return this;
    }

    DiagnosticsStateBuilder firstSeenAt(long firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
        return this;
    }

    DiagnosticsStateBuilder version(long appVersionCode, String appVersionName,
                                    int sdk) {
        this.appVersionCode = appVersionCode;
        this.appVersionName = appVersionName;
        this.sdk = sdk;
        return this;
    }

    DiagnosticsStateBuilder process(DiagnosticsState.ProcessInfo process) {
        this.process = process;
        return this;
    }

    DiagnosticsStateBuilder matches(DiagnosticsState.MatchesInfo matches) {
        this.matches = matches;
        return this;
    }

    DiagnosticsStateBuilder weather(DiagnosticsState.WeatherInfo weather) {
        this.weather = weather;
        return this;
    }

    DiagnosticsStateBuilder memory(DiagnosticsState.MemoryInfo memory) {
        this.memory = memory;
        return this;
    }

    /** Sustituye la lista completa de salidas por la indicada. */
    DiagnosticsStateBuilder exits(List<DiagnosticsState.ExitEvent> exits) {
        this.exits.clear();
        return addExits(exits);
    }

    DiagnosticsStateBuilder addExit(DiagnosticsState.ExitEvent exit) {
        if (exit != null) exits.add(exit);
        return this;
    }

    DiagnosticsStateBuilder addExits(List<DiagnosticsState.ExitEvent> exits) {
        if (exits == null) return this;
        for (DiagnosticsState.ExitEvent exit : exits) {
            if (exit != null) this.exits.add(exit);
        }
        return this;
    }

    DiagnosticsStateBuilder counters(DiagnosticsState.Counters counters) {
        this.counters = counters;
        return this;
    }

    /** Sustituye las claves de salidas ya vistas por las indicadas. */
    DiagnosticsStateBuilder exitScanKeys(List<String> keys) {
        this.exitScanKeys.clear();
        return addExitKeys(keys);
    }

    DiagnosticsStateBuilder addExitKey(String key) {
        if (key != null && !key.isEmpty()) exitScanKeys.add(key);
        return this;
    }

    DiagnosticsStateBuilder addExitKeys(List<String> keys) {
        if (keys == null) return this;
        for (String key : keys) {
            if (key != null && !key.isEmpty()) exitScanKeys.add(key);
        }
        return this;
    }

    /** Construye conservando el {@code savedAt} actual. */
    DiagnosticsState build() {
        return build(savedAt);
    }

    DiagnosticsState build(long savedAt) {
        return new DiagnosticsState(savedAt, firstSeenAt, appVersionCode,
                appVersionName, sdk, process, matches, weather, memory,
                exits, counters, exitScanKeys);
    }
}
