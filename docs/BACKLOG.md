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

## 2. Primera ciudad detectada por IP 🔜 S

**Depende de 1.** El listado y la migración ya están preparados para una ciudad
`source: DETECTED`; falta la detección.

- **Vía recomendada: geolocalización por IP.** En el primer arranque se hace una
  petición HTTPS a un servicio sin clave (por ejemplo `https://ipwho.is/`,
  probado en ~120 ms) que devuelve ciudad, latitud y longitud. No requiere
  permisos ni cambiar ajustes del sistema. Se guarda como primera ciudad y no se
  vuelve a consultar salvo que el usuario pulse **Usar mi ubicación** en Ajustes.
- **Por qué no la ubicación de Android:** el RG3205W no declara
  `android.hardware.location`, no tiene proveedor de red y la ubicación del
  sistema está desactivada (verificado por ADB). Habría que pedir
  `ACCESS_COARSE_LOCATION`, activar la ubicación y aun así depender de servicios
  que el dispositivo no ofrece.
- **Precisión:** a nivel de ciudad/proveedor, suficiente para el tiempo. Si el
  servicio falla o no hay red, se usa la ciudad por defecto actual (Tres Cantos)
  y el usuario puede buscar la suya.
- **Privacidad:** una única petición a un tercero en el primer arranque; hay que
  documentarlo en la sección de privacidad del README.
- La ciudad detectada se puede editar o eliminar como cualquier otra. Si se
  elimina la primera, la siguiente pasa a ser la inicial.
- El proveedor debe quedar detrás de una clase pequeña (`IpLocationClient`) para
  poder cambiarlo sin tocar la UI.
- En upgrades, no sustituir automáticamente una ciudad manual: ofrecer el botón
  **Usar mi ubicación** y aplicarlo solo si la lista es la ciudad por defecto.

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
