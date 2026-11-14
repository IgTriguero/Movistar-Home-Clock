# HomeClock — Diseño técnico

Documento de arquitectura y decisiones de la versión **1.1.1** (14/11/2026). Para instalar, compilar y ver las capturas, consulta el [README](README.md).

## 1. Objetivo y alcance

HomeClock es una app Android para un dispositivo **Movistar Home RG3205W** usado en horizontal como reloj de sobremesa. Sustituye el launcher por una pantalla propia que se abre a mano y muestra:

- Hora y fecha.
- Tiempo actual de una ciudad elegida.
- Partidos de los equipos seguidos, con marcador y estado.

La app es **standalone**: no hay backend propio, cuenta de usuario ni analítica. Todo el trabajo se hace en el dispositivo contra APIs y páginas públicas.

**Decisiones de producto:**

| Decisión | Motivo |
|---|---|
| App normal, no launcher | El dispositivo se sigue usando con Home 2.0; la app se abre cuando se quiere. |
| Sin cuentas ni backend | Menos operación y menos datos personales. |
| Equipos configurables desde la app | Cualquier equipo de fútbol o baloncesto de 365Scores se puede añadir, pausar o quitar sin tocar código; As Celtas se mantiene como entrada curada. |
| Solo horizontal | El Movistar Home es un panel fijo en horizontal. |
| Sin AndroidX ni dependencias externas | APK pequeño y build reproducible. |

## 2. Arquitectura

```text
HomeClockApp
  ├── DiagnosticsStore (sesiones, PSS, salidas, debounce)
  └── listener de Diagnostics

MainActivity
  ├── reloj/fecha (tick de 1 s)
  ├── detección de ciudad por IP (al arrancar + reintentos)
  ├── tiempo (WeatherRepository multi-ciudad, cada 15 min)
  ├── pager de ciudades (swipe + dots)
  ├── partidos (MatchRepository, refresco adaptativo)
  ├── modo noche (brillo de ventana)
  ├── pixel shift (protección de pantalla)
  └── actualizaciones (UpdateClient/Downloader/Installer)

SettingsActivity
  ├── ciudades (lista editable y Open-Meteo Geocoding)
  ├── equipos (lista editable, búsqueda 365Scores y alta por ID)
  ├── partidos y modo noche
  └── diagnóstico (Diagnostics + MatchCache + Prefs)
```

| Componente | Responsabilidad |
|---|---|
| `MainActivity` | Pantalla principal, ciclo de refresco, render del panel y avisos. |
| `SettingsActivity` | Ajustes, listas de ciudades y equipos, y diagnóstico. |
| `City` / `CityList` / `CityListCodec` | Modelo, orden, selección y JSON de ciudades (`cities_v2`). |
| `TrackedTeam` | Equipo seguido inmutable: proveedor, ID, deporte, nombres, país, competición, logo y estado. |
| `TrackedTeamList` / `TrackedTeamCodec` | Lista ordenada (máximo 10, lista vacía válida), firma de selección y JSON `teams_v1`. |
| `TeamSelection` / `TeamSelectionHolder` | Selección inmutable de equipos activos compartida con las fuentes. |
| `TeamSearchClient` | Búsqueda de equipos en 365Scores, lookup por ID y sugerencia curada de As Celtas. |
| `IpLocationClient` | Detección de ciudad por IP con `ipwho.is`, normalización y errores tipados. |
| `WeatherRepository` | Fetch multi-ciudad, merge de caché v2 y fallback offline. |
| `WeatherPager` / `PageDotsView` | Paginador táctil del tiempo y puntos de posición. |
| `MatchDetail*` / `MatchDetailDialog` | Detalle de 365Scores, caché de finalizados y modal por pestañas. |
| `BroadcastCatalog` | Guía curada de TV por competición y búsqueda de respaldo. |
| `MatchRepository` | Ejecuta las fuentes en paralelo, fusiona, deduplica y aplica caché. |
| `Scores365Source` | Fútbol y baloncesto vía 365Scores. |
| `RfefSource` | As Celtas en Liga (RFEF). |
| `CeltaSource` | As Celtas en Copa de la Reina (web del Celta). |
| `MatchCache` | Un JSON por fecha (`version: 2`), firma de equipos, escritura atómica y lectura tolerante. |
| `MatchesSnapshot` | Partidos + estado por fuente + marca de stale. |
| `Diagnostics` | Estado vivo: último resultado, salud por fuente y del tiempo. |
| `DiagnosticsStore` | Persiste el resumen en `no_backup/diagnostics.json`, PSS, salidas, sesiones y debounce. |
| `SourceHealth` / `SourceHealthPolicy` | Rachas de fallos y caída de fuente (3 fallos + 15 min). |
| `ExitInfoReader` / `ExitReasonMapper` | Motivos de salida de `ApplicationExitInfo` (API 30+) y su clasificación. |
| `HomeClockApp` | Arranca el store antes de las Activities y recibe `onTrimMemory`. |
| `LogoLoader` | Escudos en memoria y disco, con caché negativa. |
| `UpdateClient` / `UpdateDownloader` / `UpdateInstaller` | Comprobación, descarga, SHA-256 e instalación. |
| `PixelShift` | Patrón de desplazamiento anti-retención. |

## 3. Fuentes de datos

| Fuente | Uso | Formato |
|---|---|---|
| 365Scores `webws.365scores.com` | Fútbol y baloncesto de los equipos seguidos | JSON |
| RFEF `rfef.es` | Calendario y resultados de As Celtas | HTML con `<select>` de jornadas y tablas |
| Celta `rccelta.es` | Copa de la Reina de As Celtas | JSON de WordPress (`admin-ajax.php`) |
| Open-Meteo `api.open-meteo.com` | Tiempo actual | JSON |
| Open-Meteo Geocoding | Buscador de ciudades | JSON |
| ipwho.is | Ciudad estimada por IP (primera ciudad / botón manual) | JSON |

- 365Scores se consulta por `competitors` con los IDs de los equipos activos, `langId=14` y zona `Europe/Madrid`. La búsqueda de equipos usa `GET /web/search/?query=...` y el lookup por ID `GET /web/competitors/?competitors=...`.
- As Celtas no existe en 365Scores: es una entrada curada (`as-celtas`, id `-1`) que solo activa RFEF y Celta. Si no está en la lista, esas fuentes no se consultan.
- RFEF y Celta guardan un calendario compacto en el dispositivo para no repetir peticiones.
- No se usan claves de API. Las fuentes son públicas pero no oficiales; pueden cambiar sin aviso.

## 4. Modelo y normalización

`Match` contiene equipos, competición, deporte, hora, estado, marcador, escudos y el equipo seguido (`trackedId`, `trackedIsHome`, `homeTracked`, `awayTracked`). `MatchStatus` normaliza los estados:

```text
UNKNOWN · SCHEDULED · LIVE · LIVE_APPROX · FINISHED · POSTPONED · CANCELLED
```

Reglas relevantes:

- **`LIVE`**: la fuente da un reloj fiable (365Scores).
- **`LIVE_APPROX`**: la fuente sabe que se juega pero no da minuto (RFEF/Celta).
- **`timeTbd`**: la fuente no da hora. Se muestra “hora por confirmar”; no se inventa una hora.
- **Sin marcador**: se muestra `—`, nunca `-1`.
- Los partidos sin hora usan la medianoche local solo como valor interno de ordenación.

### Deduplicación

Dos partidos son el mismo si coincide la pareja de equipos (en cualquier orden) y:

- ambos tienen hora y están a **≤ 2 horas**, o
- alguno es `timeTbd` y caen en el **mismo día local**.

Sin nombres se compara `id + fuente`. Si ambos partidos traen deporte y no coincide, no se fusionan (fútbol y baloncesto pueden compartir nombres y hora). En caso de duplicado se prioriza el estado en vivo, el marcador conocido y la fuente (`365Scores > Celta > RFEF`). Los escudos que falten se completan desde el duplicado y los flags de equipo seguido se traducen si los nombres vienen invertidos.

### Reloj en vivo

- **Fútbol**: se muestra exactamente el minuto de la API (`34'`, `90+4'`). No hay reloj local ni segundos inventados.
- **Baloncesto**: se muestra el `mm:ss` de la API (`4C 00:03`). No se extrapola.
- **Descanso**: se muestra como texto.
- **Refresco**: 15 s en baloncesto; 5 s en los últimos 2 minutos de cuarto/prórroga; 60 s en fútbol; 5 min en `LIVE_APPROX`.

## 5. Repositorio y caché

`MatchRepository` ejecuta las fuentes en un pool de 3 hilos con un presupuesto global de **12 s**.

- Si una fuente falla, se conservan los partidos de las demás.
- Solo se ejecutan las fuentes habilitadas por la selección: 365Scores si hay IDs activos y RFEF/Celta si As Celtas está activo. Sin fuentes activas se devuelve un snapshot vacío sin leer caché ni tocar la red.
- Si una fuente falla pero hay caché de esa fuente, se reutilizan sus partidos y se marca `fromCache`.
- Si fallan todas, se devuelve el último snapshot con `stale=true`.
- Solo se sobrescribe la caché cuando el ciclo termina sin errores.
- `SourceStatus` guarda estado, error, latencia, `fetchedAt`, `fromCache` y número de partidos.
- `Diagnostics` guarda en memoria el último snapshot, la salud por fuente/tiempo y notifica a `DiagnosticsStore`.
- `DiagnosticsStore` escribe un resumen JSON versionado en `no_backup/diagnostics.json` con debounce (20 s, máximo 60 s), PSS (`Debug.MemoryInfo`), motivo de la última salida (`ApplicationExitInfo` en API 30+) y contadores. Ajustes lo usa antes de caer a `MatchCache`/`Prefs`.

La caché de partidos es un JSON por fecha en `filesDir` (`matches_YYYY-MM-DD.json`) con escritura atómica (`.tmp` + rename). La versión 2 guarda la firma de equipos activos (`teams`): si la lista cambia, la caché de esa fecha se descarta para no resucitar partidos de equipos borrados. La lectura sin firma (diagnóstico y tests) sigue aceptando cachés antiguas. La caché de tiempo está en `SharedPreferences` y se invalida al cambiar de ciudad.

## 6. Interfaz

### Pantalla principal

- Reloj grande y fecha centrados.
- Bloque de tiempo abajo: icono, temperatura, ciudad a la derecha, descripción y detalles.
- El bloque es un paginador: swipe izquierda/derecha cambia de ciudad, con puntos de posición y resistencia en los extremos.
- Panel de partidos a la derecha (40 % del ancho) con cabecera de día, lista y footer de fuentes.
- El panel se puede ocultar con **OCULTAR / PARTIDOS**.
- Las flechas ◀ ▶ navegan por días; tras 60 s vuelve a hoy, sin contador visible. Si el usuario oculta el panel con partidos, reaparece solo tras 60 s. Ajustes se cierra solo tras 60 s sin interacción para dejar el reloj a la vista.
- Si no hay partidos, el panel se oculta y el footer sigue disponible.

### Detalle de partido

Al tocar una fila se abre un `Dialog` de plataforma con cabecera fija, cuatro
pestañas (Resumen, Alineaciones, Estadísticas y Dónde ver) y scroll por pestaña.
365Scores aporta eventos, alineaciones, estadísticas, estadio, árbitro y
`tvNetworks`; RFEF y Celta solo muestran el resumen básico y un enlace a su
fuente. Los partidos finalizados se cachean en `filesDir/match_details`; los
vivos se refrescan cada 20 s (fútbol) o 15 s (baloncesto).

### Fila de partido

Una sola fila, sin estilos alternativos:

- Escudo de 80 dp con placeholder mientras carga.
- Nombre del equipo seguido en negrita.
- Contexto “competición · deporte” encima.
- Hora y cuenta atrás, o estado y marcador.

### Modo noche

`NightSchedule` decide si la hora actual está dentro de la franja (admite cruce de medianoche y fin exclusivo). La ventana baja a brillo `0.1`; fuera se usa `BRIGHTNESS_OVERRIDE_NONE`. No requiere permisos.

### Pixel shift

Cada 5 minutos se aplica un desplazamiento de hasta ±4 dp siguiendo un anillo de 9 posiciones (centro + 8 direcciones). El contenido no se queda fijo en el mismo píxel y vuelve al centro cada 45 minutos.

## 7. Ajustes

`SettingsActivity` incluye:

- **Ciudades**: lista editable (máximo 8) con buscador de Open-Meteo. Se añaden, seleccionan, reordenan y eliminan; la primera es la ciudad actual y se actualiza al abrir la app. **Usar mi ubicación** lanza la detección por IP. La selección y la lista se guardan al instante. La búsqueda corre en background y descarta respuestas obsoletas.
- **Equipos**: lista editable (máximo 10) con logo, deporte, competición y país. Se añaden con el buscador de 365Scores (debounce de 350 ms, agrupados por deporte, sugerencia local de As Celtas) o por ID. El checkbox **Seguir** activa/desactiva sin borrar; ✕ elimina con confirmación. **Añadir equipos por defecto** recupera solo los ausentes. La lista vacía es válida: oculta el panel y no consulta fuentes. Los cambios se guardan al instante en `teams_v1`.
- **Partidos**: visibilidad del panel.
- **Noche**: activación y franja `HH:mm` con validación.
- **Diagnóstico**: última actualización, latencia, estado y salud por fuente, caché, contadores de escudos, resumen persistido (sesión, PSS, última salida, fuentes caídas), **Actualizar ahora** y **Borrar caché de escudos**.
- **Acerca de**: versión, fuentes y comprobación manual de actualizaciones.
- **Fecha de prueba**: solo en builds debug.

## 8. Actualizaciones y release

- La app consulta `update.json` en GitHub Releases una vez al día y desde Ajustes.
- Descarga el APK, verifica el SHA-256 y lanza `PackageInstaller`.
- Android muestra la confirmación final; sin *device owner* o root no se puede evitar.
- Tras instalar, Android 15 puede devolver al launcher y bloquear el arranque de la activity desde el callback del instalador (BAL). La app no se reabre sola.
- La firma release es estable y se guarda fuera del repo (`keys/`). Sin ella no se pueden publicar actualizaciones que instalen encima.
- CI: `.github/workflows/android.yml` (tests + lint + APK debug; release al tag `v*`) y `.github/workflows/contract.yml` (contrato diario).
- Versión en `gradle.properties`; tag `vX.Y`; secretos de firma en GitHub.

## 9. Calidad

- **587 tests** unitarios: modelo, mapper, tiempo, temporada, kickoff, refresco, caché, repositorio, `SourceResult`, diagnóstico, salud de fuentes, codec/estado persistido, PSS/salidas, pixel shift, caché de escudos, ciudades, caché v2, pager, detección IP, detalle de partido, guía de TV, equipos seguidos, búsqueda de equipos y contrato.
- **Contrato diario** con datos reales de 365Scores, Open-Meteo, RFEF y Celta.
- **Soak test**: `tools/soak.sh` muestrea PID, PSS, actividad en primer plano y crashes/ANRs durante 72 h.
- **Lint**: 0 errores; quedan avisos menores de rendimiento/estilo.
- **Fixtures**: 365Scores, Celta, jornadas RFEF, geocoding de Vigo, Open-Meteo multi-ciudad e ipwho.is.

## 10. Limitaciones

- La búsqueda de equipos usa el endpoint público no oficial de 365Scores; si cambia, se puede seguir añadiendo por ID y As Celtas sigue disponible desde la entrada local.
- As Celtas no existe en 365Scores: depende de RFEF y de la web del Celta. Si ambas fallan, se conserva el último calendario cacheado.
- La detección por IP es aproximada a nivel de ciudad/proveedor y puede fallar con VPN, CGNAT, IP móvil o redes corporativas. La ciudad se puede cambiar a mano.
- En Android anterior a 11 no hay `ApplicationExitInfo`: el motivo de la última salida puede quedar como “desconocida”. El PSS es una tendencia, no una medida exacta.
- La app es solo horizontal y no arranca sola tras reinicio o corte de luz.
- No hay kiosco ni launcher.
- Las fuentes no son APIs oficiales; un cambio de HTML/JSON puede requerir actualizar un parser.
- El updater necesita una confirmación del sistema; el modo silencioso requeriría *device owner*.
- La prueba de 72 h se ejecuta aparte; el harness está incluido, pero no forma parte del CI.

## 11. Próximos pasos

El detalle vivo, con notas de viabilidad, está en [`docs/BACKLOG.md`](docs/BACKLOG.md). Resumen:

1. ✅ Varias ciudades guardadas con swipe en la zona del tiempo.
2. ✅ Primera ciudad detectada por IP, con botón manual “Usar mi ubicación”.
3. ✅ Detalle de partido en un modal (goles, tarjetas, cambios, alineaciones y estadísticas).
4. ✅ Dónde ver cada partido en España, con guía por competición y búsqueda.
5. ✅ Configuración de equipos desde la app: búsqueda en 365Scores, alta por ID, pausa/borrado y As Celtas curada.
6. Fallback de fútbol si 365Scores falla.
7. ✅ Diagnóstico persistente entre procesos y aviso de fuente caída.
8. Prueba de 72 h completa y ajuste de consumo si aparece deriva.
9. Valorar *device owner* solo si se quiere actualización totalmente silenciosa.
