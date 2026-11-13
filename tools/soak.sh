#!/usr/bin/env bash
# soak.sh — monitor de larga duración para HomeClock.
#
# Muestrea por ADB el proceso com.itriguero.homeclock y escribe un CSV con:
# PID, PSS total, activity en primer plano, crashes/ANRs y estado del proceso.
#
# Solo ejecuta comandos de lectura. No borra datos, no fuerza paradas,
# no instala nada y no reinicia la app.
#
# Compatible con bash 3.2 (macOS) y bash 4/5 (Linux).
set -euo pipefail

# Los decimales de awk/printf deben usar punto, independientemente del locale
# del host (un macOS en español usaría coma y rompería --hours 0.02).
export LC_ALL=C

PKG="com.itriguero.homeclock"
PKG_RE='com\.itriguero\.homeclock'
ADB="${ADB:-adb}"

usage() {
    cat <<'EOF'
Uso: tools/soak.sh [opciones]

Opciones:
  --serial <serial>      Serial ADB. Por defecto: $ANDROID_SERIAL o el primer
                         dispositivo en estado "device".
  --hours <n>            Duración en horas. Por defecto: 72.
                         Admite decimales para un smoke: --hours 0.02.
  --interval <segundos>  Segundos entre muestras. Por defecto: 60.
  --out <csv>            CSV de salida. Por defecto: soak-YYYYmmdd-HHMMSS.csv.
  -h, --help             Muestra esta ayuda.

Ejemplos:
  tools/soak.sh --hours 0.02 --interval 10 --out /tmp/soak-smoke.csv
  tools/soak.sh --serial emulator-5554 --hours 72 --out soak-72h.csv
EOF
}

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 2
}

# ---------------------------------------------------------------------------
# Argumentos
# ---------------------------------------------------------------------------
serial="${ANDROID_SERIAL:-}"
hours="72"
interval="60"
out=""

while [ "$#" -gt 0 ]; do
    case "$1" in
        --serial)
            if [ "$#" -lt 2 ]; then die "--serial requiere un valor"; fi
            serial="$2"
            shift 2
            ;;
        --serial=*)
            serial="${1#*=}"
            shift
            ;;
        --hours)
            if [ "$#" -lt 2 ]; then die "--hours requiere un valor"; fi
            hours="$2"
            shift 2
            ;;
        --hours=*)
            hours="${1#*=}"
            shift
            ;;
        --interval)
            if [ "$#" -lt 2 ]; then die "--interval requiere un valor"; fi
            interval="$2"
            shift 2
            ;;
        --interval=*)
            interval="${1#*=}"
            shift
            ;;
        --out)
            if [ "$#" -lt 2 ]; then die "--out requiere un valor"; fi
            out="$2"
            shift 2
            ;;
        --out=*)
            out="${1#*=}"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            die "argumento desconocido: $1 (usa --help)"
            ;;
    esac
done

command -v "$ADB" >/dev/null 2>&1 || die "no se encuentra '$ADB' en PATH"

if ! awk -v h="$hours" 'BEGIN { exit !(h ~ /^[0-9]+([.][0-9]+)?$/ && h > 0) }'; then
    die "--hours debe ser un número mayor que 0 (ej. 72 o 0.02)"
fi

case "$interval" in
    ''|*[!0-9]*)
        die "--interval debe ser un entero de segundos mayor que 0"
        ;;
esac
if [ "$interval" -le 0 ]; then
    die "--interval debe ser mayor que 0"
fi

total_seconds=$(awk -v h="$hours" 'BEGIN { printf "%.0f", h * 3600 }')
if [ "$total_seconds" -lt 1 ]; then
    die "--hours es demasiado pequeño; usa al menos 1 segundo de duración"
fi

if [ -z "$out" ]; then
    out="soak-$(date +%Y%m%d-%H%M%S).csv"
fi
if [ -e "$out" ]; then
    die "el CSV ya existe: $out (elige otro nombre con --out)"
fi

out_dir=$(dirname "$out")
if [ ! -d "$out_dir" ]; then
    if ! mkdir -p "$out_dir"; then
        die "no se pudo crear el directorio de salida: $out_dir"
    fi
fi

events_log="${out%.csv}.events.log"
if [ -e "$events_log" ]; then
    die "el log de eventos ya existe: $events_log"
fi

# ---------------------------------------------------------------------------
# Dispositivo
# ---------------------------------------------------------------------------
if [ -z "$serial" ]; then
    serial=$("$ADB" devices 2>/dev/null | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1; exit }')
fi
if [ -z "$serial" ]; then
    die "no hay dispositivos ADB en estado 'device'. Revisa 'adb devices' y autoriza el depurado"
fi

state=$("$ADB" -s "$serial" get-state 2>/dev/null || true)
if [ "$state" != "device" ]; then
    die "el dispositivo '$serial' no está disponible (estado: ${state:-desconocido})"
fi

adb_s() {
    "$ADB" -s "$serial" "$@"
}

device_model=$(adb_s shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)
device_android=$(adb_s shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || true)
device_model=${device_model:-desconocido}
device_android=${device_android:-desconocida}

if ! adb_s shell pm path "$PKG" >/dev/null 2>&1; then
    printf 'AVISO: %s no aparece instalado en %s.\n' "$PKG" "$serial" >&2
    printf '       El soak registrará alive=0 hasta que esté instalado y abierto.\n' >&2
fi

# ---------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------
csv_escape() {
    local value="$1"
    local quote='"'
    value="${value//$quote/$quote$quote}"
    value="${value//$'\n'/ }"
    value="${value//$'\r'/}"
    printf '"%s"' "$value"
}

pct() {
    awk -v n="$1" -v d="$2" 'BEGIN {
        if (d > 0) printf "%.1f", (n * 100) / d
        else printf "0.0"
    }'
}

# Imprime las líneas FATAL EXCEPTION cuyo bloque incluye el paquete. Se usan
# las 4 líneas siguientes porque el formato habitual es:
#   FATAL EXCEPTION: main
#   Process: com.itriguero.homeclock, PID: ...
extract_fatal_events() {
    awk -v pkg="$PKG" '
        /FATAL EXCEPTION/ {
            fatal = $0
            found = 0
            for (i = 0; i < 4; i++) {
                if ((getline nextline) <= 0) break
                if (index(nextline, pkg) > 0) { found = 1; break }
            }
            if (found) print fatal
        }
    '
}

# Filtra contra el fichero de eventos ya vistos y añade los nuevos.
# Se usa un fichero y grep -F porque bash 3.2 no tiene arrays asociativos.
filter_new() {
    local line
    while IFS= read -r line; do
        if [ -z "$line" ]; then
            continue
        fi
        if ! grep -Fqx "$line" "$seen_file" 2>/dev/null; then
            printf '%s\n' "$line" >> "$seen_file"
            printf '%s\n' "$line"
        fi
    done
}

collect_pid() {
    pid=""
    local raw
    raw=$(adb_s shell pidof "$PKG" 2>/dev/null || true)
    pid=$(printf '%s' "$raw" | tr -d '\r' | awk 'NF { print $1; exit }')
}

collect_pss() {
    pss=""
    pss_cmd_failed=0
    local meminfo
    if ! meminfo=$(adb_s shell dumpsys meminfo "$PKG" 2>/dev/null); then
        pss_cmd_failed=1
        return 0
    fi
    meminfo=$(printf '%s' "$meminfo" | tr -d '\r')
    pss=$(printf '%s\n' "$meminfo" | awk '
        /^[[:space:]]*TOTAL[[:space:]]+[0-9]+/ {
            for (i = 2; i <= NF; i++) {
                if ($i ~ /^[0-9]+$/) { print $i; exit }
            }
        }
        /TOTAL PSS:/ {
            for (i = 1; i <= NF; i++) {
                if ($i == "PSS:") { print $(i + 1); exit }
            }
        }
        /^[[:space:]]*TOTAL:[[:space:]]*[0-9]+/ {
            print $2
            exit
        }
    ')
    pss=$(printf '%s' "$pss" | awk 'NF { print $1; exit }')
    case "$pss" in
        ''|*[!0-9]*) pss="" ;;
    esac
}

collect_top() {
    top_activity=""
    top_cmd_failed=0
    local raw
    if ! raw=$(adb_s shell dumpsys activity activities 2>/dev/null); then
        top_cmd_failed=1
        return 0
    fi
    top_activity=$(printf '%s\n' "$raw" | tr -d '\r' | grep -m 1 'topResumedActivity=' || true)
    top_activity=$(printf '%s' "$top_activity" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
}

collect_logs() {
    crash_raw=""
    log_raw=""
    crash_log_failed=0
    main_log_failed=0
    local raw
    if ! raw=$(adb_s logcat -d -b crash -t 100 2>/dev/null); then
        crash_log_failed=1
    else
        crash_raw=$(printf '%s' "$raw" | tr -d '\r')
    fi
    if ! raw=$(adb_s logcat -d -t 200 2>/dev/null); then
        main_log_failed=1
    else
        log_raw=$(printf '%s' "$raw" | tr -d '\r')
    fi
}

crash_candidates() {
    local fatal_crash fatal_log
    fatal_crash=$(printf '%s\n' "$crash_raw" | extract_fatal_events || true)
    fatal_log=$(printf '%s\n' "$log_raw" | extract_fatal_events || true)
    printf '%s\n%s\n' "$fatal_crash" "$fatal_log" | awk 'NF' | sort -u
}

anr_candidates() {
    printf '%s\n' "$log_raw" | grep -iF "ANR in $PKG" || true
}

# ---------------------------------------------------------------------------
# Estado del resumen
# ---------------------------------------------------------------------------
start_epoch=$(date +%s)
start_iso=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
end_epoch=$((start_epoch + total_seconds))
expected_samples=$(( (total_seconds - 1) / interval + 1 ))

samples=0
alive_count=0
app_fg_count=0
main_fg_count=0
crashes_total=0
anrs_total=0
pid_changes=0
pss_valid_count=0
pss_missing=0
crash_buf_samples=0
log_hit_samples=0
notes_count=0
pss_first=""
pss_last=""
pss_min=""
pss_max=""
prev_pid=""
summary_printed=0
overall_pass=0

if ! seen_file=$(mktemp "${TMPDIR:-/tmp}/homeclock-soak.XXXXXX"); then
    die "no se pudo crear un fichero temporal para deduplicar eventos"
fi

print_summary() {
    if [ "$summary_printed" -eq 1 ]; then
        return 0
    fi
    summary_printed=1

    local now_epoch elapsed_s elapsed_h
    now_epoch=$(date +%s)
    elapsed_s=$((now_epoch - start_epoch))
    elapsed_h=$(awk -v s="$elapsed_s" 'BEGIN { printf "%.2f", s / 3600 }')

    local alive_pct main_pct app_pct
    alive_pct=$(pct "$alive_count" "$samples")
    main_pct=$(pct "$main_fg_count" "$samples")
    app_pct=$(pct "$app_fg_count" "$samples")

    local pss_min_d pss_max_d pss_first_d pss_last_d
    local growth_kb growth_txt growth_mb_txt growth_pct_txt
    pss_min_d="${pss_min:-n/a}"
    pss_max_d="${pss_max:-n/a}"
    pss_first_d="${pss_first:-n/a}"
    pss_last_d="${pss_last:-n/a}"
    growth_kb=0
    growth_txt="n/a"
    growth_mb_txt="n/a"
    growth_pct_txt="n/a"
    if [ -n "$pss_first" ] && [ -n "$pss_last" ]; then
        growth_kb=$((pss_last - pss_first))
        growth_txt="$growth_kb"
        growth_mb_txt=$(awk -v g="$growth_kb" 'BEGIN { printf "%.1f", g / 1024 }')
        growth_pct_txt=$(awk -v g="$growth_kb" -v f="$pss_first" 'BEGIN {
            if (f > 0) printf "%.1f", (g * 100) / f
            else printf "n/a"
        }')
    fi

    local events_ok alive_ok memory_ok fg_ok resultado
    events_ok="FAIL"
    if [ "$crashes_total" -eq 0 ] && [ "$anrs_total" -eq 0 ]; then
        events_ok="OK"
    fi

    alive_ok="FAIL"
    if [ "$samples" -gt 0 ] && [ "$alive_count" -eq "$samples" ] && [ "$pid_changes" -eq 0 ]; then
        alive_ok="OK"
    fi

    memory_ok="N/A"
    if [ -n "$pss_first" ] && [ -n "$pss_last" ]; then
        memory_ok="FAIL"
        if awk -v g="$growth_kb" -v f="$pss_first" 'BEGIN {
            exit !((f > 0 && (g * 100 / f) < 20) || g < 51200)
        }'; then
            memory_ok="OK"
        fi
    fi

    fg_ok="FAIL"
    if [ "$samples" -gt 0 ] && [ $((app_fg_count * 100)) -ge $((samples * 95)) ]; then
        fg_ok="OK"
    fi

    overall_pass=0
    if [ "$events_ok" = "OK" ] && [ "$alive_ok" = "OK" ] && [ "$memory_ok" = "OK" ] && [ "$fg_ok" = "OK" ]; then
        overall_pass=1
        resultado="PASS"
    else
        resultado="FAIL"
    fi

    printf '\n===== Resumen soak HomeClock =====\n'
    printf 'Dispositivo : %s\n' "$serial"
    printf 'CSV         : %s\n' "$out"
    printf 'Eventos     : %s\n' "$events_log"
    printf 'Duración    : %s h (%d s), %d muestras cada %s s\n' "$elapsed_h" "$elapsed_s" "$samples" "$interval"
    printf 'Proceso vivo: %d/%d (%s %%)\n' "$alive_count" "$samples" "$alive_pct"
    printf 'MainActivity: %d/%d (%s %%) | HomeClock en primer plano: %d/%d (%s %%)\n' \
        "$main_fg_count" "$samples" "$main_pct" "$app_fg_count" "$samples" "$app_pct"
    printf 'Crashes     : %d | ANRs: %d\n' "$crashes_total" "$anrs_total"
    printf 'Reinicios   : %d cambio(s) de PID\n' "$pid_changes"
    printf 'PSS total   : min %s KB | max %s KB | primera %s KB | última %s KB (%d muestras)\n' \
        "$pss_min_d" "$pss_max_d" "$pss_first_d" "$pss_last_d" "$pss_valid_count"
    printf 'Crecimiento : %s KB (%s MB, %s %%)\n' "$growth_txt" "$growth_mb_txt" "$growth_pct_txt"
    printf 'Incidencias : %d con notas; %d vivo sin PSS; %d con líneas en crash buffer; %d con hits FATAL/ANR\n' \
        "$notes_count" "$pss_missing" "$crash_buf_samples" "$log_hit_samples"
    printf 'Criterios   : eventos=%s vivo=%s memoria=%s primer_plano=%s\n' \
        "$events_ok" "$alive_ok" "$memory_ok" "$fg_ok"
    printf 'Resultado   : %s\n' "$resultado"
    printf '\nCriterios en docs/SOAK.md. El primer plano exige >=95 %% con una activity de HomeClock;\n'
    printf 'la memoria exige crecimiento <20 %% o <50 MB; el proceso debe estar vivo en el 100 %%.\n'
}

on_exit() {
    local code=$?
    trap - EXIT INT TERM
    if [ "$summary_printed" -eq 0 ] && [ "$samples" -gt 0 ]; then
        print_summary
    fi
    rm -f "$seen_file" || true
    exit "$code"
}

trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

printf 'timestamp_iso,epoch_s,pid,alive,pss_total_kb,top_activity,app_in_foreground,main_in_foreground,crash_buffer_hits,logcat_hits,new_crashes,new_anrs,crashes_total,anrs_total,pid_changed,notes\n' > "$out"
{
    printf '# HomeClock soak — eventos nuevos\n'
    printf '# inicio: %s\n' "$start_iso"
    printf '# dispositivo: %s (%s, Android %s)\n' "$serial" "$device_model" "$device_android"
    printf '# paquete: %s\n' "$PKG"
    printf '# formato: timestamp_iso<TAB>tipo<TAB>linea\n'
} > "$events_log"

# Línea base: los crashes/ANRs anteriores al inicio no cuentan.
collect_logs
crash_candidates | filter_new >/dev/null
anr_candidates | filter_new >/dev/null

printf 'HomeClock soak\n'
printf '  dispositivo : %s (%s, Android %s)\n' "$serial" "$device_model" "$device_android"
printf '  paquete     : %s\n' "$PKG"
printf '  duración    : %s h (%s s), una muestra cada %s s\n' "$hours" "$total_seconds" "$interval"
printf '  CSV         : %s\n' "$out"
printf '  eventos     : %s\n' "$events_log"
printf '  (Ctrl-C detiene el soak e imprime el resumen)\n\n'

# ---------------------------------------------------------------------------
# Bucle principal
# ---------------------------------------------------------------------------
sample=0
next_tick=$start_epoch

while :; do
    now_epoch=$(date +%s)
    if [ "$now_epoch" -ge "$end_epoch" ]; then
        break
    fi

    sample=$((sample + 1))
    samples=$sample
    ts_iso=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    ts_epoch=$(date +%s)

    collect_pid
    collect_pss
    collect_top
    collect_logs

    alive=0
    if [ -n "$pid" ]; then
        alive=1
        alive_count=$((alive_count + 1))
    fi

    app_fg=0
    main_fg=0
    case "$top_activity" in
        *"$PKG/"*) app_fg=1 ;;
    esac
    case "$top_activity" in
        *"$PKG/.MainActivity"*|*"$PKG/com.itriguero.homeclock.MainActivity"*) main_fg=1 ;;
    esac
    if [ "$app_fg" -eq 1 ]; then
        app_fg_count=$((app_fg_count + 1))
    fi
    if [ "$main_fg" -eq 1 ]; then
        main_fg_count=$((main_fg_count + 1))
    fi

    pid_changed=0
    if [ -n "$pid" ]; then
        if [ -n "$prev_pid" ] && [ "$pid" != "$prev_pid" ]; then
            pid_changed=1
            pid_changes=$((pid_changes + 1))
        fi
        prev_pid="$pid"
    fi

    crash_buffer_hits=0
    if [ -n "$crash_raw" ]; then
        crash_buffer_hits=$(printf '%s\n' "$crash_raw" | grep -cF "$PKG" || true)
    fi
    case "$crash_buffer_hits" in
        ''|*[!0-9]*) crash_buffer_hits=0 ;;
    esac
    if [ "$crash_buffer_hits" -gt 0 ]; then
        crash_buf_samples=$((crash_buf_samples + 1))
    fi

    logcat_hits=0
    if [ -n "$log_raw" ]; then
        logcat_hits=$(printf '%s\n' "$log_raw" | grep -icE "FATAL EXCEPTION|ANR in ${PKG_RE}" || true)
    fi
    case "$logcat_hits" in
        ''|*[!0-9]*) logcat_hits=0 ;;
    esac
    if [ "$logcat_hits" -gt 0 ]; then
        log_hit_samples=$((log_hit_samples + 1))
    fi

    new_crash_events=$(crash_candidates | filter_new)
    new_crashes=$(printf '%s\n' "$new_crash_events" | awk 'NF { n++ } END { print n + 0 }')
    crashes_total=$((crashes_total + new_crashes))

    new_anr_events=$(anr_candidates | filter_new)
    new_anrs=$(printf '%s\n' "$new_anr_events" | awk 'NF { n++ } END { print n + 0 }')
    anrs_total=$((anrs_total + new_anrs))

    if [ -n "$pss" ]; then
        pss_valid_count=$((pss_valid_count + 1))
        if [ -z "$pss_first" ]; then
            pss_first="$pss"
        fi
        pss_last="$pss"
        if [ -z "$pss_min" ] || [ "$pss" -lt "$pss_min" ]; then
            pss_min="$pss"
        fi
        if [ -z "$pss_max" ] || [ "$pss" -gt "$pss_max" ]; then
            pss_max="$pss"
        fi
    elif [ -n "$pid" ]; then
        pss_missing=$((pss_missing + 1))
    fi

    notes=""
    if [ -n "$pid" ] && [ -z "$pss" ]; then
        notes="${notes}pss_unavailable;"
    fi
    if [ "$pss_cmd_failed" -eq 1 ]; then
        notes="${notes}pss_error;"
    fi
    if [ "$top_cmd_failed" -eq 1 ]; then
        notes="${notes}top_error;"
    fi
    if [ -z "$top_activity" ]; then
        notes="${notes}top_empty;"
    fi
    if [ "$crash_log_failed" -eq 1 ]; then
        notes="${notes}logcat_crash_error;"
    fi
    if [ "$main_log_failed" -eq 1 ]; then
        notes="${notes}logcat_main_error;"
    fi
    if [ "$pss_cmd_failed" -eq 1 ] && [ "$top_cmd_failed" -eq 1 ] && [ "$crash_log_failed" -eq 1 ] && [ "$main_log_failed" -eq 1 ]; then
        notes="${notes}adb_unreachable;"
    fi
    notes="${notes%;}"
    if [ -n "$notes" ]; then
        notes_count=$((notes_count + 1))
    fi

    printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
        "$(csv_escape "$ts_iso")" \
        "$ts_epoch" \
        "$(csv_escape "$pid")" \
        "$alive" \
        "$pss" \
        "$(csv_escape "$top_activity")" \
        "$app_fg" \
        "$main_fg" \
        "$crash_buffer_hits" \
        "$logcat_hits" \
        "$new_crashes" \
        "$new_anrs" \
        "$crashes_total" \
        "$anrs_total" \
        "$pid_changed" \
        "$(csv_escape "$notes")" >> "$out"

    if [ "$new_crashes" -gt 0 ]; then
        {
            printf '\n--- %s CRASHES nuevos=%d ---\n' "$ts_iso" "$new_crashes"
            printf '%s\n' "$new_crash_events"
            printf '%s\n' '--- crash buffer (-b crash -t 100) ---'
            printf '%s\n' "$crash_raw"
            printf '%s\n' '--- fin ---'
        } >> "$events_log"
    fi
    if [ "$new_anrs" -gt 0 ]; then
        {
            printf '\n--- %s ANRs nuevos=%d ---\n' "$ts_iso" "$new_anrs"
            printf '%s\n' "$new_anr_events"
            printf '%s\n' '--- logcat -d -t 200 ---'
            printf '%s\n' "$log_raw"
            printf '%s\n' '--- fin ---'
        } >> "$events_log"
    fi

    main_txt="no"
    if [ "$main_fg" -eq 1 ]; then
        main_txt="si"
    fi
    pss_txt="${pss:-n/a}"
    pid_txt="${pid:-none}"
    printf '[%d/%d] %s pid=%s pss=%s KB main=%s alive=%s crash=%d anr=%d\n' \
        "$sample" "$expected_samples" "$ts_iso" "$pid_txt" "$pss_txt" "$main_txt" "$alive" "$new_crashes" "$new_anrs"

    if [ "$sample" -eq 1 ] && [ "$alive" -eq 0 ]; then
        printf 'AVISO: %s no está en ejecución. Abre la app para que el soak sea útil.\n' "$PKG" >&2
    elif [ "$sample" -eq 1 ] && [ "$main_fg" -eq 0 ]; then
        printf 'AVISO: MainActivity no está en primer plano al empezar (%s).\n' "${top_activity:-sin datos}" >&2
    fi

    next_tick=$((next_tick + interval))
    now_epoch=$(date +%s)
    if [ "$now_epoch" -ge "$end_epoch" ]; then
        break
    fi
    sleep_for=$((next_tick - now_epoch))
    remaining=$((end_epoch - now_epoch))
    if [ "$sleep_for" -gt "$remaining" ]; then
        sleep_for="$remaining"
    fi
    if [ "$sleep_for" -gt 0 ]; then
        sleep "$sleep_for"
    fi
done

print_summary

if [ "$overall_pass" -eq 1 ]; then
    exit 0
else
    exit 1
fi
