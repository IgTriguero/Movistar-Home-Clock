# HomeClock — Backlog

Ideas y trabajo pendiente, en orden de interés. No es un compromiso de entrega:
sirve para no perder el contexto técnico de cada propuesta.

Estados: 💡 idea · 🔜 siguiente · 🚧 en curso · ✅ hecho
Tamaño orientativo: S (1 día) · M (2-3 días) · L (4-5 días)

Última revisión: 14/11/2026 (versión 1.0).

## 1. Varias ciudades guardadas con swipe de tiempo ✅ M

**Implementado:** lista de hasta 8 ciudades, paginador táctil en el bloque de
tiempo, puntos de posición, selección persistida y una sola petición a Open-Meteo
para todas las ciudades.

**Cómo quedó:**

- Ajustes → **Ciudades**: añadir con el buscador de `CitySearchClient`,
  seleccionar tocando la fila, reordenar con ↑/↓ y eliminar con confirmación.
  La primera es la ciudad por defecto.
- `cities_v2` guarda la lista y la clave seleccionada; `CityList`/`CityListCodec`
  migran desde `city/lat/lon` y mantienen el espejo para downgrade.
- `weather_cache_v2` guarda una entrada por ciudad con su timestamp; el merge
  conserva las ausentes y no escribe en fallo.
- `WeatherPager` usa `GestureDetector` + `ValueAnimator`, sin AndroidX, con
  resistencia en los extremos y tres páginas reutilizadas.
- Tests: modelo, codec, migración, cliente multi-ciudad, caché, repositorio,
  pager y `Prefs` con un `SharedPreferences` en memoria.

## 2. Primera ciudad detectada por IP ✅ S

**Implementado:** `IpLocationClient` consulta `ipwho.is` al abrir la app y
promueve la ciudad actual al primer puesto sin borrar ninguna. Si la ciudad ya
está guardada (misma clave o mismo nombre a ≤25 km) se reutiliza; si es nueva se
inserta delante. La lista sigue limitada a 8: si está llena se avisa y no se
añade. En Ajustes hay un botón **Usar mi ubicación** con el mismo comportamiento.

- Sin permisos ni ubicación de Android: el RG3205W no tiene proveedor de red ni
  `android.hardware.location`, así que se usa la IP pública.
- Hasta 3 intentos por arranque (0, 15 min, 2 h) solo con errores recuperables.
  No hay flag vitalicio: el siguiente arranque vuelve a intentarlo.
- Privacidad: el proveedor ve la IP pública y el `User-Agent`; no se envía la
  ubicación del dispositivo, ni la lista de ciudades, ni identificadores.
  Documentado en README.
- La ciudad detectada se puede editar, reordenar o borrar. Si se borra, no
  reaparece automáticamente hasta el siguiente arranque.
- En Ajustes solo se muestra la insignia **Actual**; “Por defecto” y
  “Detectada” se retiraron.

## 3. Detalle de partido en un modal 💡 L

**Objetivo:** tocar una fila de partido y abrir un modal con goles, tarjetas,
cambios, alineaciones, estadísticas, estadio y árbitro.

**Viabilidad (verificada contra la API real):**

- `Match.id` ya es el `gameId` de 365Scores y `sourceId` identifica la fuente.
- `GET https://webws.365scores.com/web/game/?gameId=<id>&langId=14&...` devuelve:
  - `events`: goles, tarjetas y sustituciones con minuto, jugador y tipo
    (`eventType.id` / `name`, ya en español: `Gol`, `Tarjeta amarilla`,
    `Substitution`...).
  - `members`: nombres y dorsales para resolver los `playerId` de los eventos.
  - `lineups` por equipo, `officials` (árbitro), `venue` y `topPerformers`.
- `GET .../web/game/stats/?games=<id>` devuelve estadísticas comparadas.
- Los partidos de RFEF y Celta no tienen este detalle: para ellos el modal
  mostraría lo básico (equipos, hora, marcador, competición) y, si existe, el
  enlace a la fuente.

**Comportamiento propuesto:**

- Fila clickable (feedback visual al pulsar).
- `android.app.Dialog` con layout oscuro propio, sin Material:
  cabecera con equipos, marcador y estado; línea temporal de eventos; secciones
  de alineaciones y estadísticas; pie con estadio y árbitro.
- Carga bajo demanda con estado de carga y error. Si el detalle falla, se sigue
  mostrando el resumen que ya tiene la app.
- Partido en vivo: refrescar el modal cada 20-30 s mientras esté abierto.
  Partido finalizado: cachear el detalle (los eventos ya no cambian).
- Ampliar el contrato diario y los fixtures con una respuesta de `/web/game/`.

**Riesgos:** API no oficial y sin documentación; parsear de forma tolerante y
no bloquear la pantalla principal si el detalle no responde.

## 4. Dónde ver cada partido en España 💡 S/M

**Objetivo:** indicar en el detalle del partido (o en la fila) por dónde se
puede ver en España.

**Opciones valoradas:**

| Opción | Pros | Contras |
|---|---|---|
| Tabla propia por competición | Sin red, cero dependencias, control total | Los derechos cambian cada temporada y por jornada |
| Scrapear una guía (p. ej. futbolenlatv, BeSoccer) | Más preciso por partido | Sin API oficial, HTML frágil y posible bloqueo |
| Enlace a búsqueda (“dónde ver X vs Y”) | Siempre actualizado, cero mantenimiento | Menos integrado en la UI |

**Recomendación:** empezar por una tabla curada por competición (LaLiga,
Champions, Copa, ACB, Euroliga, selección) con la etiqueta “según competición”,
más un enlace de búsqueda como respaldo. 365Scores no incluye emisoras en el
detalle del partido. Revisar la tabla al inicio de cada temporada.

## 5. Configuración de equipos desde la app 🔜 M/L

Hoy los equipos están fijados en código (`MatchesClient.TRACKED_IDS_CSV` y
`MainActivity.shortTeamName`). Falta una pantalla para añadir/quitar equipos,
persistir los IDs en `Prefs` y hacer dinámicos nombres y escudos. Requiere
resolver la búsqueda de equipos contra 365Scores.

## 6. Fallback de fútbol si 365Scores falla 🔜 M

`MatchRepository` reutiliza caché, pero no hay una segunda fuente para Real
Madrid, Rayo, España o Liverpool. RFEF y Celta solo cubren a As Celtas. Habría
que elegir una fuente alternativa y normalizarla al modelo `Match`.

## 7. Diagnóstico persistente y aviso de fuente caída 🔜 S/M

`Diagnostics` es un singleton en memoria: tras morir el proceso, Ajustes cae a
`MatchCache`/`Prefs`. Falta persistir el último estado. El aviso visual de
fuente caída ya existe parcialmente (footer con `⚠` y mensajes de *stale* y
*parcial*); valorar si hace falta algo más visible.

## 8. Soak test de 72 h 🔜

El 13/11/2026 se hizo un smoke de 7 h: 0 crashes, 0 ANRs, proceso vivo el
100 % y PSS estabilizada (~45 MB). No sustituye a la prueba completa. Hacer las
72 h sobre el build candidato de la próxima versión; revisar deriva de PSS,
crashes, ANRs, actividad en primer plano y consumo.

## 9. Device owner para actualización silenciosa 💡

Solo si se quiere evitar la confirmación final del instalador de Android.
Implica provisionar el dispositivo como *device owner*; no es necesario para el
uso actual.

## Descartado por ahora

- Launcher o modo kiosco: la app se abre a mano sobre Home 2.0.
- Autoarranque tras reinicio o corte de luz.
- Cuentas, backend y analítica.
- AndroidX y dependencias externas: se mantiene el APK pequeño y el build
  reproducible.
