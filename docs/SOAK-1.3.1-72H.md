# Soak de 72 h — HomeClock 1.3.1

- **Fechas:** 24–27/11/2026
- **Duración:** 72 h, 4320 muestras cada 60 s
- **Resultado:** **PASS**

## Entorno

- **App:** `com.itriguero.homeclock` 1.3.1 (versionCode 6), instalada el
  24/11/2026 a las 16:15:02.
- **Dispositivo:** Movistar Home RG3205W (`lineage_home_rg3205w`), LineageOS
  con Android 15 (SDK 35), build `BP1A.250505.005`; no es la ROM original
  Android 8.1.
- **Conexión:** ADB inalámbrico, `192.168.1.44`, serial
  `adb-47aae42a-8fF6XH._adb-tls-connect._tcp`.
- **Host:** Fedora 44, `adb` 37.0.3. El soak se ejecutó en una sesión `tmux`
  bajo `systemd-inhibit --what=idle:sleep:handle-lid-switch`.
- **Inicio:** 24/11/2026 21:43:33 CET (20:43:33 UTC).
- **Fin:** 27/11/2026 21:43:33 CET (20:43:33 UTC).

## Resultado

| Criterio | Valor | Estado |
|---|---|---|
| Proceso vivo | 4320/4320 (100 %) | OK |
| `MainActivity` en primer plano | 4320/4320 (100 %) | OK |
| HomeClock en primer plano | 4320/4320 (100 %) | OK |
| Crashes nuevos | 0 | OK |
| ANRs nuevos | 0 | OK |
| Cambios de PID | 0 | OK |
| Versión | 1.3.1 (code 6), sin cambios | OK |
| PSS | 47.766 → 60.650 KB (+12.884 KB, +12,6 MB, +27,0 %) | OK* |
| Incidencias | 0 filas con `notes`; 0 hits de crash/ANR | OK |

\* El criterio es crecimiento **<20 % o <50 MB**. Se cumple por el margen
absoluto (+12,6 MB < 50 MB), no por el relativo (+27,0 % > 20 %).

El `events.log` solo contiene la cabecera: no se registró ningún crash, ANR ni
evento nuevo. El PID `13784` se mantuvo durante las 72 h.

## Deriva de PSS

El PSS no creció de forma continua. Subió en dos escalones y se mantuvo plano
después de cada uno:

| Momento | PSS |
|---|---:|
| Inicio (0 h) | 47,8 MB |
| 8 h | 54,1 MB |
| 40 h | 55,4 MB |
| 48 h | 54,1 MB |
| 52 h | 61,2 MB |
| 72 h | 60,7 MB |

La comparación orientativa entre el `meminfo` del smoke inicial y el posterior
al soak apunta a `Native Heap` y `Graphics` (con `EGL mtrack` estable),
compatible con caché de datos/escudos y buffers nativos, no con un crash ni con
un ANR.

**Lectura:** el resultado automático es PASS, pero la subida del ~27 % es la
señal a vigilar. Al ser escalonada y con mesetas, no parece una fuga continua;
aun así, conviene repetir el soak en la próxima release y comprobar si el
escalón de ~61 MB vuelve a aparecer. Si se repite, revisar `LogoLoader` y las
cachés de datos/bitmaps antes de cambiar la release.

## Reproducción

```bash
systemd-inhibit --what=idle:sleep:handle-lid-switch \
  --why="Soak HomeClock 1.3.1" \
  ./tools/soak.sh --serial <adb-serial> --hours 72 --interval 60 \
  --expect-version 1.3.1 --out soak-1.3.1-72h.csv
```

Resumen recalculado:

```bash
python3 tools/soak_summary.py soak-1.3.1-72h.csv --expect-version 1.3.1
```

Los CSV y logs (`soak-1.3.1-72h.csv`, `.events.log`, `.log`) están en la raíz
del repo pero cubiertos por `.gitignore`; no se versionan. Este informe es el
resumen permanente.
