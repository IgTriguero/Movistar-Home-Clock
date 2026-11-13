#!/usr/bin/env python3
"""Recalcula el resumen de un CSV generado por tools/soak.sh.

Uso:
    python3 tools/soak_summary.py soak-72h.csv
    python3 tools/soak_summary.py soak-72h.csv --json

No usa dependencias externas: solo la biblioteca estándar.
"""

import argparse
import csv
import json
import sys
from pathlib import Path

PSS_GROWTH_LIMIT_KB = 50 * 1024
FOREGROUND_MIN_PERCENT = 95.0


def to_int(value):
    """Convierte celdas del CSV a entero; devuelve None si no es numérica."""
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        return int(text)
    except ValueError:
        try:
            return int(float(text))
        except (ValueError, OverflowError):
            return None


def percent(part, total):
    if total <= 0:
        return 0.0
    return (part * 100.0) / total


def read_rows(path):
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        fieldnames = reader.fieldnames or []
        rows = list(reader)

    if not rows:
        raise ValueError("el CSV no contiene muestras")

    required = {
        "timestamp_iso",
        "epoch_s",
        "pid",
        "alive",
        "pss_total_kb",
        "app_in_foreground",
        "main_in_foreground",
        "crashes_total",
        "anrs_total",
        "pid_changed",
        "notes",
    }
    missing = required.difference(fieldnames)
    if missing:
        raise ValueError(
            "faltan columnas obligatorias en el CSV: " + ", ".join(sorted(missing))
        )
    return rows


def summarize(path):
    rows = read_rows(path)

    samples = len(rows)
    alive_count = sum(1 for row in rows if to_int(row.get("alive")) == 1)
    app_fg_count = sum(1 for row in rows if to_int(row.get("app_in_foreground")) == 1)
    main_fg_count = sum(1 for row in rows if to_int(row.get("main_in_foreground")) == 1)
    pid_changes = sum(1 for row in rows if to_int(row.get("pid_changed")) == 1)
    notes_count = sum(1 for row in rows if str(row.get("notes") or "").strip())
    crash_buffer_samples = sum(
        1 for row in rows if (to_int(row.get("crash_buffer_hits")) or 0) > 0
    )
    logcat_hit_samples = sum(
        1 for row in rows if (to_int(row.get("logcat_hits")) or 0) > 0
    )
    pss_missing_samples = 0
    for row in rows:
        if to_int(row.get("alive")) == 1:
            pss_value = to_int(row.get("pss_total_kb"))
            if pss_value is None or pss_value <= 0:
                pss_missing_samples += 1

    last_crashes = 0
    last_anrs = 0
    for row in reversed(rows):
        value = to_int(row.get("crashes_total"))
        if value is not None:
            last_crashes = value
            break
    for row in reversed(rows):
        value = to_int(row.get("anrs_total"))
        if value is not None:
            last_anrs = value
            break

    pss_values = []
    for row in rows:
        value = to_int(row.get("pss_total_kb"))
        if value is not None and value > 0:
            pss_values.append(value)

    pss_first = pss_values[0] if pss_values else None
    pss_last = pss_values[-1] if pss_values else None
    pss_min = min(pss_values) if pss_values else None
    pss_max = max(pss_values) if pss_values else None
    growth_kb = None
    growth_percent = None
    if pss_first is not None and pss_last is not None:
        growth_kb = pss_last - pss_first
        if pss_first > 0:
            growth_percent = (growth_kb * 100.0) / pss_first

    epochs = []
    for row in rows:
        value = to_int(row.get("epoch_s"))
        if value is not None:
            epochs.append(value)
    duration_s = (max(epochs) - min(epochs)) if len(epochs) >= 2 else 0

    events_ok = last_crashes == 0 and last_anrs == 0
    alive_ok = samples > 0 and alive_count == samples and pid_changes == 0
    memory_ok = None
    if growth_kb is not None:
        memory_ok = growth_kb < PSS_GROWTH_LIMIT_KB or (
            growth_percent is not None and growth_percent < 20.0
        )
    foreground_ok = percent(app_fg_count, samples) >= FOREGROUND_MIN_PERCENT
    overall_pass = bool(events_ok and alive_ok and memory_ok and foreground_ok)

    return {
        "csv": str(path),
        "samples": samples,
        "start_iso": rows[0].get("timestamp_iso"),
        "end_iso": rows[-1].get("timestamp_iso"),
        "duration_s": duration_s,
        "duration_h": round(duration_s / 3600.0, 2),
        "alive_count": alive_count,
        "alive_percent": round(percent(alive_count, samples), 1),
        "app_foreground_count": app_fg_count,
        "app_foreground_percent": round(percent(app_fg_count, samples), 1),
        "main_foreground_count": main_fg_count,
        "main_foreground_percent": round(percent(main_fg_count, samples), 1),
        "crashes": last_crashes,
        "anrs": last_anrs,
        "pid_changes": pid_changes,
        "notes_samples": notes_count,
        "pss_missing_samples": pss_missing_samples,
        "crash_buffer_samples": crash_buffer_samples,
        "logcat_hit_samples": logcat_hit_samples,
        "pss": {
            "samples": len(pss_values),
            "min_kb": pss_min,
            "max_kb": pss_max,
            "first_kb": pss_first,
            "last_kb": pss_last,
            "growth_kb": growth_kb,
            "growth_percent": round(growth_percent, 1)
            if growth_percent is not None
            else None,
        },
        "criteria": {
            "events": "OK" if events_ok else "FAIL",
            "alive": "OK" if alive_ok else "FAIL",
            "memory": "N/A" if memory_ok is None else ("OK" if memory_ok else "FAIL"),
            "foreground": "OK" if foreground_ok else "FAIL",
        },
        "overall_pass": overall_pass,
        "result": "PASS" if overall_pass else "FAIL",
    }


def print_report(data):
    pss = data["pss"]
    print("===== Resumen soak HomeClock (recalculado) =====")
    print(f"CSV         : {data['csv']}")
    print(
        f"Duración    : {data['duration_h']:.2f} h ({data['duration_s']} s), "
        f"{data['samples']} muestras"
    )
    print(
        f"Proceso vivo: {data['alive_count']}/{data['samples']} "
        f"({data['alive_percent']:.1f} %)"
    )
    print(
        f"MainActivity: {data['main_foreground_count']}/{data['samples']} "
        f"({data['main_foreground_percent']:.1f} %) | "
        f"HomeClock en primer plano: {data['app_foreground_count']}/{data['samples']} "
        f"({data['app_foreground_percent']:.1f} %)"
    )
    print(f"Crashes     : {data['crashes']} | ANRs: {data['anrs']}")
    print(f"Reinicios   : {data['pid_changes']} cambio(s) de PID")

    if pss["first_kb"] is None:
        print("PSS total   : sin datos")
        print("Crecimiento : n/a")
    else:
        print(
            f"PSS total   : min {pss['min_kb']} KB | max {pss['max_kb']} KB | "
            f"primera {pss['first_kb']} KB | última {pss['last_kb']} KB "
            f"({pss['samples']} muestras)"
        )
        print(
            f"Crecimiento : {pss['growth_kb']} KB "
            f"({pss['growth_kb'] / 1024.0:.1f} MB, {pss['growth_percent']:.1f} %)"
        )

    print(
        f"Incidencias : {data['notes_samples']} con notas; "
        f"{data['pss_missing_samples']} vivo sin PSS; "
        f"{data['crash_buffer_samples']} con líneas en crash buffer; "
        f"{data['logcat_hit_samples']} con hits FATAL/ANR"
    )
    criteria = data["criteria"]
    print(
        f"Criterios   : eventos={criteria['events']} vivo={criteria['alive']} "
        f"memoria={criteria['memory']} primer_plano={criteria['foreground']}"
    )
    print(f"Resultado   : {data['result']}")


def main():
    parser = argparse.ArgumentParser(
        description="Recalcula el resumen de un CSV de tools/soak.sh."
    )
    parser.add_argument("csv", type=Path, help="CSV generado por tools/soak.sh")
    parser.add_argument(
        "--json",
        action="store_true",
        help="Imprime el resumen como JSON en vez de texto.",
    )
    args = parser.parse_args()

    try:
        data = summarize(args.csv)
    except (OSError, ValueError, csv.Error) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    if args.json:
        print(json.dumps(data, ensure_ascii=False, indent=2))
    else:
        print_report(data)

    return 0 if data["overall_pass"] else 1


if __name__ == "__main__":
    sys.exit(main())
