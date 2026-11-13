# Prueba de 72 h (soak) de HomeClock

Monitor de larga duración para el dispositivo Android conectado por ADB. Comprueba
que `com.itriguero.homeclock` sigue vivo, no crashea, no se queda sin memoria y
mantiene `MainActivity` en primer plano.

El harness **solo lee** del dispositivo:

- no borra datos ni cachés;
- no fuerza paradas (`am force-stop`) ni reinicia la app;
- no instala ni actualiza nada;
- no cambia ajustes del sistema.

## Requisitos

- `adb` en el `PATH` y el dispositivo autorizado (`adb devices` debe mostrar `device`).
- HomeClock instalada y abierta en `MainActivity` antes de empezar.
- El dispositivo conectado por USB o ADB inalámbrico durante toda la prueba.
- El host que ejecuta el script sin suspensión. En macOS:

  ```bash
  caffeinate -i ./tools/soak.sh --hours 72 --out soak-72h.csv
  ```

- Red disponible. La app consulta Open-Meteo, 365Scores, RFEF y Celta; una caída
  de red no debería crashear, pero invalida la parte de “sin peticiones descontroladas”.

## Lanzamiento

Desde la raíz de `HomeClock`:

```bash
chmod +x tools/soak.sh
./tools/soak.sh --serial <adb-serial> --hours 72 --interval 60 --out soak-72h.csv
```

Si hay un solo dispositivo, `--serial` se puede omitir. También se respeta la
variable `ANDROID_SERIAL`; si no hay ninguna de las dos, se usa el primer
dispositivo en estado `device`.

Para dejarlo corriendo sin ocupar la terminal:

```bash
nohup ./tools/soak.sh --serial <adb-serial> --hours 72 --interval 60 \
  --out soak-72h.csv > soak-72h.log 2>&1 &
```

Para detenerlo antes de tiempo, envía `SIGTERM` (o `Ctrl-C` si está en primer
plano). El script imprime el resumen igualmente:

```bash
kill -TERM <pid-del-soak>
```

### Smoke corto

`--hours 0.02` son 72 segundos. Con el intervalo por defecto (60 s) solo salen
2 muestras; para un smoke más útil baja el intervalo:

```bash
./tools/soak.sh --hours 0.02 --interval 10 --out "soak-smoke-$(date +%H%M%S).csv"
```

El smoke valida el harness (ADB, CSV, resumen), no la estabilidad de 72 h.

### Opciones

| Opción | Por defecto | Descripción |
|---|---|---|
| `--serial <serial>` | `$ANDROID_SERIAL` o primer `device` | Serial ADB. |
| `--hours <n>` | `72` | Duración en horas. Admite decimales (`0.02`). |
| `--interval <segundos>` | `60` | Segundos entre muestras. Entero > 0. |
| `--out <csv>` | `soak-YYYYmmdd-HHMMSS.csv` | CSV de salida. Falla si ya existe. |
| `-h`, `--help` | — | Ayuda. |

El CSV de eventos se crea al lado: `soak-72h.events.log` para `soak-72h.csv`.

## Qué hace en cada muestra

En este orden, con el serial elegido:

1. `adb shell pidof com.itriguero.homeclock` → PID y `alive`.
2. `adb shell dumpsys meminfo com.itriguero.homeclock` → `TOTAL` PSS en KB.
3. `adb shell dumpsys activity activities | grep topResumedActivity` → activity top.
4. `adb logcat -d -b crash -t 100` → líneas del paquete en el buffer de crashes.
5. `adb logcat -d -t 200 | grep -iE 'FATAL EXCEPTION|ANR in com.itriguero.homeclock'`
   → hits de crash/ANR.
6. Deduplica crashes/ANRs nuevos contra una línea base tomada al arrancar y escribe
   la fila del CSV.

La línea base evita contar crashes/ANRs anteriores al inicio. Los eventos nuevos
se guardan también en el `.events.log`: el buffer de crash cuando aparece un crash
y las últimas 200 líneas de logcat cuando aparece un ANR.

## Criterios de aceptación

La ejecución es válida solo si la app estuvo abierta y el ADB no se desconectó.
Sobre una ejecución válida de 72 h:

| Criterio | Umbral | Cómo se comprueba |
|---|---|---|
| Crashes | **0** nuevos | `crashes_total = 0` y `new_crashes = 0` en todas las filas. |
| ANRs | **0** nuevos | `anrs_total = 0` y `new_anrs = 0` en todas las filas. |
| Proceso vivo | **100 %** de las muestras | `alive = 1` en todas las filas y sin cambios de PID. |
| Memoria | crecimiento de PSS **< 20 %** o **< 50 MB** | primera vs. última muestra con PSS; además, revisar que no haya una tendencia monótona preocupante. |
| Primer plano | `MainActivity` el **100 %** (ideal) | `main_in_foreground = 1`. Se toleran `SettingsActivity` y el diálogo/instalador de actualización; el resumen considera OK si ≥95 % de las muestras tienen alguna activity de HomeClock. El instalador del sistema no es HomeClock: solo pasa por el margen del 5 %. |
| Peticiones | sin ráfagas ni bucles | revisión manual (ver abajo). |

Un solo crash, ANR, PID nuevo o muestra con `alive = 0` invalida la prueba. Si el
dispositivo se reinició o se actualizó la app durante el soak, hay que repetirlo:
el cambio de PID y la pérdida de primer plano son esperados, pero impiden atribuir
el resultado a la versión que se quería probar.

El `PASS` automático no evalúa la red: si la revisión manual detecta un bucle de
peticiones, el soak es `FAIL` aunque el script haya salido con `0`.

### Peticiones descontroladas

El harness no mide tráfico de red. La cadencia esperada (README) es:

- tiempo: cada 15 min;
- partidos sin partido: cada 6 h;
- partidos cerca del kickoff: 30 min, 10 min y 60 s;
- fútbol en vivo: 60 s;
- baloncesto en vivo: 15 s (5 s en los últimos 2 min de cada cuarto/prórroga);
- reloj local: cada segundo, sin red.

Para detectar un bucle:

```bash
# UID de la app
adb shell dumpsys package com.itriguero.homeclock | grep -m 1 userId=

# Actividad de red de ese uid (comparar antes/después o varias veces)
adb shell dumpsys netstats detail | grep -A 5 "uid=<uid>"

# Logs de error repetidos de las fuentes
adb logcat -d | grep -iE "365scores|open-meteo|rfef|rcelta|HTTP|timeout"

# Estado de la app y sus handlers
adb shell dumpsys activity processes | grep -A 20 com.itriguero.homeclock

# CPU del proceso (una foto; repetir si hay sospecha)
adb shell top -b -n 1 | grep com.itriguero.homeclock
```

Si aparecen decenas de peticiones por minuto sin un partido en vivo, o el PSS y
la CPU suben a la vez, se trata como fallo aunque no haya crash.

## Interpretar el CSV

Una fila por muestra, en orden cronológico. `timestamp_iso` y `epoch_s` son UTC.

| Columna | Significado |
|---|---|
| `timestamp_iso` | Instante de la muestra en ISO 8601 UTC. |
| `epoch_s` | El mismo instante en segundos epoch. |
| `pid` | PID de `com.itriguero.homeclock`; vacío si no está vivo. |
| `alive` | `1` si `pidof` devolvió un PID; `0` si no. |
| `pss_total_kb` | PSS total en KB del proceso; vacío si no está vivo o `dumpsys` falló. |
| `top_activity` | Línea `topResumedActivity=...` completa. |
| `app_in_foreground` | `1` si la activity top es de HomeClock (Main, Settings o UpdateResult). |
| `main_in_foreground` | `1` solo si la activity top es `MainActivity`. |
| `crash_buffer_hits` | Líneas del paquete en `logcat -b crash -t 100`. **Puede incluir crashes antiguos**; no es el contador de eventos nuevos. |
| `logcat_hits` | Líneas que casan `FATAL EXCEPTION` o `ANR in ...` en las últimas 200. `FATAL EXCEPTION` no se filtra por paquete; es un indicador bruto. |
| `new_crashes` | Crashes nuevos detectados en esta muestra (deduplicados). |
| `new_anrs` | ANRs nuevos detectados en esta muestra (deduplicados). |
| `crashes_total` | Acumulado de crashes nuevos desde el inicio. |
| `anrs_total` | Acumulado de ANRs nuevos desde el inicio. |
| `pid_changed` | `1` si el PID cambió respecto a la muestra anterior con PID. |
| `notes` | Incidencias del muestreo: `pss_unavailable`, `pss_error`, `top_error`, `top_empty`, `logcat_crash_error`, `logcat_main_error`, `adb_unreachable`. |

Lecturas típicas:

- **`alive = 0`**: el proceso desapareció. Mira `new_crashes`/`crashes_total`;
  si siguen a 0, pudo ser un kill del sistema (LMK, batería, actualización) o que
  alguien cerró la app. Revisa el playbook.
- **`pss_total_kb` vacío con `alive = 1`**: `dumpsys meminfo` no devolvió una
  línea `TOTAL` parseable. Queda anotado como `pss_unavailable`; no es un crash.
- **`pid_changed = 1`**: reinicio del proceso. Aunque `alive = 1`, la prueba no es
  limpia: el crecimiento de PSS mezcla dos procesos.
- **`crash_buffer_hits > 0` con `new_crashes = 0`**: hay líneas de un crash
  anterior en el buffer; no cuenta como crash nuevo.
- **`top_empty`**: el formato de `dumpsys activity` no trajo `topResumedActivity`.
  El resto de métricas sigue siendo válido, pero no se puede afirmar el primer plano.
- **`notes` con `*_error`**: fallo de ADB o del comando, no necesariamente de la app.

### Resumen final

Al terminar (o al recibir `Ctrl-C`/`SIGTERM`) imprime:

- número de muestras y duración real;
- % de muestras con el proceso vivo;
- % con `MainActivity` y % con cualquier activity de HomeClock en primer plano;
- crashes y ANRs nuevos;
- cambios de PID;
- PSS mínimo, máximo, primera y última muestra, y crecimiento en KB/MB/%;
- incidencias de muestreo;
- criterios y resultado `PASS`/`FAIL`.

Ejemplo (no es una ejecución real):

```text
===== Resumen soak HomeClock =====
Dispositivo : <serial>
CSV         : soak-72h.csv
Eventos     : soak-72h.events.log
Duración    : 72.00 h (259200 s), 4320 muestras cada 60 s
Proceso vivo: 4320/4320 (100.0 %)
MainActivity: 4317/4320 (99.9 %) | HomeClock en primer plano: 4320/4320 (100.0 %)
Crashes     : 0 | ANRs: 0
Reinicios   : 0 cambio(s) de PID
PSS total   : min 61200 KB | max 68900 KB | primera 62000 KB | última 65400 KB (4320 muestras)
Crecimiento : 3400 KB (3.3 MB, 5.5 %)
Incidencias : 0 con notas; 0 vivo sin PSS; 0 con líneas en crash buffer; 0 con hits FATAL/ANR
Criterios   : eventos=OK vivo=OK memoria=OK primer_plano=OK
Resultado   : PASS
```

### Códigos de salida

| Código | Significado |
|---|---|
| `0` | Soak completado y criterios `PASS`. |
| `1` | Soak completado pero criterios `FAIL`. |
| `2` | Error de uso o de preparación (adb, dispositivo, CSV existente, etc.). |
| `130` | Interrumpido con `Ctrl-C`; el resumen se imprime igualmente. |
| `143` | Interrumpido con `SIGTERM` (`kill`); el resumen se imprime igualmente. |

Un `FAIL` no significa que el CSV sea inservible: el resumen y el `.events.log`
son la evidencia para investigar.

## Recalcular el resumen

`tools/soak_summary.py` lee el CSV y recalcula los mismos datos sin depender de
bash ni de ADB. Solo usa la biblioteca estándar de Python 3.

```bash
python3 tools/soak_summary.py soak-72h.csv
python3 tools/soak_summary.py soak-72h.csv --json
```

El modo `--json` es útil para guardar el resultado en CI. Sale con `0` si `PASS`,
`1` si `FAIL` y `2` si el CSV no se puede leer.

## Qué hacer si falla

### Crash

1. Mira `soak-*.events.log`: incluye las líneas nuevas y el buffer de crash.
2. Recoge el log completo sin borrar nada:

   ```bash
   adb logcat -d -b crash > crash.log
   adb logcat -d > logcat.log
   adb bugreport bugreport.zip
   ```

3. Localiza el `timestamp` del CSV y correlaciónalo con `FATAL EXCEPTION`.
4. Reproduce con la misma versión y el mismo estado (día, partido, ajustes).
5. Si el crash ocurre al volver de segundo plano, revisa `onResume`/`onPause` y
   los `Handler`/ejecutores de `MainActivity`.

No desinstales ni borres datos: se perderían las preferencias y la caché que
forman parte del estado reproducido.

### ANR

1. `adb bugreport bugreport.zip` incluye las trazas de `/data/anr/`.
2. Busca `ANR in com.itriguero.homeclock` y el hilo principal en las trazas.
3. Revisa si coincide con una descarga de red, parseo de HTML/JSON, escritura de
   caché o reconstrucción de la UI en el hilo principal.
4. Comprueba `adb shell dumpsys activity lastanr`.

### Proceso muerto sin crash

1. Busca kills del sistema:

   ```bash
   adb logcat -d | grep -iE "am_kill|lowmemorykiller|Killing|Force stopping|ANR"
   adb shell dumpsys activity lru
   adb shell dumpsys activity processes | grep -A 20 com.itriguero.homeclock
   ```

2. Comprueba si el dispositivo se reinició (`adb shell uptime`, `adb shell cat /proc/uptime`).
3. Revisa optimización de batería, ahorro de energía y políticas de Home 2.0.
4. Si fue una actualización de la app, el PID nuevo es esperado; repite el soak
   con la versión final y sin actualizaciones pendientes.

### Crecimiento de PSS

1. Compara `dumpsys meminfo` al principio y al final:

   ```bash
   adb shell dumpsys meminfo com.itriguero.homeclock
   ```

2. Mira las secciones `Native Heap`, `Dalvik Heap`, `Views`, `Activities` y
   `Bitmap`. Un crecimiento de `Views`/`Activities` sugiere filas o listeners que
   no se liberan; uno de `Bitmap` sugiere caché de escudos.
3. Para un análisis de heap hace falta una build debug (`run-as`/`am dumpheap`);
   la release no es debuggable. No cambies la release solo para el soak.
4. Un crecimiento puntual por una actualización de partidos o escudos no es un
   leak; preocupa la tendencia sostenida en el tiempo.

### Primer plano perdido

1. Comprueba qué hay delante:

   ```bash
   adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"
   adb shell dumpsys activity activities | grep topResumedActivity
   ```

2. Causas habituales: se pulsó Home, saltó el launcher de Home 2.0, apareció el
   diálogo de actualización, se apagó la pantalla o el sistema mostró un diálogo.
3. `SettingsActivity` y `UpdateResultActivity` son de HomeClock y cuentan como
   app en primer plano, pero no como `MainActivity`. Anota la causa en el informe.

### ADB desconectado

1. `adb devices`; si el dispositivo aparece como `offline`/`unauthorized`,
   reconecta el cable o reautoriza el depurado.
2. `adb kill-server && adb start-server` si el servidor se quedó colgado.
3. Las filas afectadas tendrán `notes=adb_unreachable` y `alive=0`; no las cuentes
   como caída de la app. Repite el soak si el hueco es largo.

## Limitaciones

- El muestreo cada 60 s puede perderse un crash/ANR muy corto. El buffer de crash
  y las últimas 200 líneas de logcat ayudan, pero no son infinitos.
- La línea base ignora crashes/ANRs anteriores al inicio; si la app ya venía
  inestable, el soak no lo verá.
- `-t 100` y `-t 200` son ventanas acotadas. Un crash seguido de mucho tráfico
  puede salir de la ventana antes de la siguiente muestra.
- El PSS incluye memoria compartida y varía con el sistema; el crecimiento es una
  señal, no una prueba de leak.
- `dumpsys activity` cambia de formato entre versiones de Android. Si no aparece
  `topResumedActivity`, la fila queda con `top_empty`.
- El harness no mide red, CPU ni batería. El criterio de peticiones descontroladas
  se revisa a mano.
- El script no abre la app ni la relanza si se cierra. Si `alive=0`, no la
  reinicies a mitad del soak: invalida la serie.
- El host debe permanecer despierto y con el dispositivo conectado. Un portátil
  que se suspende produce huecos de ADB.
