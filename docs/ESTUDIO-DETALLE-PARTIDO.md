# Estudio de legibilidad, UX y UI — Detalle de partido

**Fecha:** 15/11/2026  
**Dispositivo:** Movistar Home RG3205W (8", 1280×800)  
**Alcance:** modal de detalle, pestaña **RESUMEN**, cabecera y pestañas. Se revisan de pasada alineaciones, estadísticas y TV.  
**Referencia visual:** captura de la app móvil de 365Scores (720×1600), adjunta por el usuario.

> Este documento es un estudio y una propuesta. No se ha modificado código ni versión.

---

## 0. Resumen ejecutivo

La pantalla actual no está mal compuesta: el marcador está centrado, la cabecera queda fija y las pestañas son claras. El problema es de **escala**: está dimensionada para leerse a 0,3–0,9 m, no a 1,5 m.

- Casi todo el contenido está entre **11sp y 20sp**. A 1,5 m eso son entre **2,4′ y 4,4′** de altura de mayúscula, por debajo del umbral práctico de lectura.
- El marcador, a **40sp**, llega a **8,9′** a 1,5 m: se puede leer, pero no es “de un vistazo”. Para vistazo hacen falta **20′** (≈96sp).
- La captura del móvil no se puede copiar tal cual: un móvil de 6,1" a 30 cm abarca unos **12,4°**; el Home a 1,5 m abarca **6,6°**. Pero el factor importante no es el ancho, sino el tamaño angular del texto: 14sp en el móvil a 30 cm ocupan unos **20′** de mayúscula; en el Home a 1,5 m, para ocupar lo mismo harían falta unos **90sp**. El marcador de 96sp no es un capricho: es el tamaño al que se ve el texto normal del móvil.
- La propuesta es una jerarquía de dos capas:
  - **Vistazo (1,5–2 m):** marcador 96sp, equipos y estado 40sp, goles 36sp, eventos 48sp.
  - **Detalle (0,8–1 m):** minutos y asistencias 28sp, etiquetas 28sp, ficha 24–28sp, pie 18sp.
- La cronología pasa a ser **a dos lados**: local a la izquierda, visitante a la derecha, minuto en el eje central e iconos en la columna interior.
- El “resumen” deja de ser una lista uniforme: la cabecera resume **goles y goleadores de cada equipo**; debajo, la cronología con **goles, amarillas, rojas, segundas amarillas, cambios y VAR**.
- El marcador queda **fijo** arriba. La pestaña RESUMEN no necesita ser la única fuente de verdad: el vistazo está siempre visible.

### Las tres cifras que explican todo

| Pregunta | Respuesta |
|---|---|
| Pantalla | 8", 1280×800, 189 ppi, 172×108 mm, 1dp ≈ 1px |
| Ángulo a 1,5 m | 6,6° × 4,1°; 1px = 0,31′; 1 mm = 2,29′ |
| Texto mínimo / cómodo / vistazo a 1,5 m | 45sp / 54–67sp / 90sp |
| Lo que hay ahora | 11–20sp en el contenido; 40sp el marcador |
| Lo que se propone | 96sp marcador, 40sp equipos, 48sp eventos, 28sp minutos |

---

## 1. Referencia física y método

### 1.1. El panel

El RG3205W tiene una pantalla de **8 pulgadas y 1280×800** en horizontal. Las capturas del proyecto (`docs/capturas/01-principal.png`, etc.) son de 1280×800 y el reloj principal usa 210sp con escala 1,2; la escala es coherente con **1dp ≈ 1px** (densidad mdpi). Si el equipo se hubiera reconfigurado, conviene comprobarlo con:

```bash
adb shell wm size
adb shell wm density
```

| Dato | Valor |
|---|---|
| Diagonal | 8" (203,2 mm) |
| Resolución | 1280 × 800 px (16:10) |
| Tamaño físico | 172,3 × 107,7 mm |
| Densidad | 188,7 ppi |
| Pitch de píxel | 0,1346 mm |
| Densidad lógica | 1,0 (1dp = 1px) |
| Ángulo a 1,5 m | 6,56° × 4,11° |
| Ángulo de 1px a 1,5 m | 0,3085′ |

### 1.2. Cómo se mide la legibilidad

La altura de mayúscula de Roboto es aproximadamente **0,72 em**. A 1,5 m:

- 1px = **0,3085′** de ángulo.
- 1 mm = **2,29′**.
- Una letra de altura de mayúscula `C` px ocupa `0,3085 × C` minutos de arco.
- Para un tamaño `S` en sp: `C = 0,72 × S`, luego **ángulo ≈ 0,222 × S arcmin**.

De ahí sale la regla de bolsillo:

> **S (sp) ≈ 4,5 × ángulo_deseado (arcmin)** a 1,5 m.  
> O, según la distancia: **S ≈ 30 × D(m)** para el mínimo, **36 × D(m)** para lectura y **45–60 × D(m)** para comodidad/vistazo.

### 1.3. Umbrales prácticos

| Objetivo | Altura de mayúscula | Tamaño a 1,5 m | Uso |
|---|---|---:|---|
| Umbral de agudeza (20/20) | 5′ | 22sp | No usar para contenido |
| Mínimo práctico | 10′ | 45sp | Texto que “se puede” leer |
| Lectura cómoda | 12–15′ | 54–67sp | Contenido principal |
| Vistazo a distancia | 20–25′ | 90–112sp | Marcador, título |

### 1.4. La tabla que resuelve la duda

| Distancia | Ancho angular | 1px | 10′ (mínimo) | 15′ (cómodo) | 20′ (vistazo) |
|---:|---:|---:|---:|---:|---:|
| 0,5 m | 19,5° | 0,93′ | 15sp | 22sp | 30sp |
| 1,0 m | 9,8° | 0,46′ | 30sp | 45sp | 60sp |
| **1,5 m** | **6,6°** | **0,31′** | **45sp** | **67sp** | **90sp** |
| 2,0 m | 4,9° | 0,23′ | 60sp | 90sp | 120sp |

Conclusión: a 1,5 m, **por debajo de 45sp no se llega ni al mínimo práctico**; para comodidad hacen falta **54–67sp**. La pantalla actual usa 11–20sp para el contenido, así que está calibrada para unos **25–45 cm**. El marcador de 40sp está calibrado para unos **90 cm**.

---

## 2. Auditoría de la pantalla actual

### 2.1. Medidas actuales

| Elemento | Ahora | Altura de mayúscula | Ángulo a 1,5 m | Distancia cómoda (15′) | Diagnóstico |
|---|---:|---:|---:|---:|---|
| Marcador | 40sp | 3,88 mm | 8,9′ | 0,89 m | Legible, no vistazo |
| Nombre de equipo | 20sp (auto 11–20) | 1,94 mm | 4,4′ | 0,44 m | Bajo el umbral |
| Estado / minuto | 15sp | 1,45 mm | 3,3′ | 0,33 m | Ilegible |
| Pestañas | 14sp / 48dp | 1,36 mm | 3,1′ | 0,31 m | Ilegible |
| Jugador de evento | 15sp | 1,45 mm | 3,3′ | 0,33 m | Ilegible |
| Etiqueta de evento | 11sp | 1,07 mm | 2,4′ | 0,24 m | Ilegible |
| Equipo del evento | 12sp | 1,16 mm | 2,7′ | 0,27 m | Ilegible |
| Asistencia / “sale” | 12sp | 1,16 mm | 2,7′ | 0,27 m | Ilegible |
| Cabecera de sección | 12sp | 1,16 mm | 2,7′ | 0,27 m | Ilegible |
| Mensaje vacío / error | 15sp | 1,45 mm | 3,3′ | 0,33 m | Ilegible |
| Pie (fuente / actualizado) | 12sp | 1,16 mm | 2,7′ | 0,27 m | Ilegible |
| Alineaciones | 14sp / 17sp | 1,36 / 1,65 mm | 3,1 / 3,8′ | 0,31 / 0,38 m | Ilegible |
| Estadísticas | 13–14sp | 1,26–1,36 mm | 2,9–3,1′ | 0,29–0,31 m | Ilegible |
| TV | 17sp | 1,65 mm | 3,8′ | 0,38 m | Ilegible |

La única excepción es el marcador: **8,9′** a 1,5 m. Un adulto con buena vista lo lee, pero no entra en el rango de “vistazo” (20′). El resto está directamente por debajo del mínimo práctico.

El panel principal de partidos tiene el mismo problema de escala (22sp para la hora, 12sp para la competición). Este estudio se centra en el modal de detalle; el panel principal merece un estudio aparte.

### 2.2. Problemas de UX/UI

1. **No hay resumen, hay una lista.** `renderSummary()` pinta los eventos en orden y una ficha de estadio. No hay “2–1, goles de X e Y, última amarilla a Z”. Para saber qué ha pasado hay que recorrer la lista entera.
2. **No hay lado.** `eventRow()` usa una sola columna: minuto a la izquierda, cuerpo a la derecha, todo alineado a la izquierda. El equipo solo se distingue por una línea de 12sp en gris. A 1,5 m, esa línea no existe.
3. **No hay iconos.** “Amarilla”, “Cambio”, “Gol” son texto de 11sp. Sin formas, el ojo tiene que leer cada fila para saber qué es. La captura del móvil acierta justo en esto: iconos de tarjeta y flechas de cambio.
4. **Jerarquía invertida.** El marcador (40sp) solo es 2,7 veces el texto del evento (15sp). Las pestañas (14sp) compiten con el contenido. No hay un punto de entrada visual claro.
5. **Cabecera pequeña.** Cresta de 56dp (7,5 mm, 17′ a 1,5 m), nombre a 20sp, marcador a 40sp. La cresta se ve, pero el nombre cae a 4,4′. El autosize permite bajar hasta **11sp** (2,4′): la app resuelve el desbordamiento haciendo el texto ilegible. La propuesta es cresta de 80dp (10,8 mm, 24′).
6. **Pestañas pequeñas y solo por color.** 14sp / 48dp, selección únicamente por color (`detail_tab_text.xml`). A 1,5 m, 3,1′; a 0,5 m el toque es aceptable, pero la señal de selección es débil.
7. **Densidad de lista.** Filas con 7dp de padding y divisores de 1px. A 1,5 m, las filas se funden: un divisor de 1px es 0,13 mm (0,3′); incluso uno de 2px es 0,27 mm (2′). No hay descanso, prórroga ni agrupación.
8. **Tarjetas invisibles.** La superficie `#1C1A18` sobre el fondo `#121212` tiene **1,08:1**; el borde de 1px `#2A2520`, **1,23:1**. A 1,5 m no se ve la tarjeta; la ficha del estadio parece texto suelto. Si la tarjeta es la única agrupación, no cumple su función.
9. **Estados vacíos diminutos.** “Cargando…”, “Sin eventos”, “No se pudo cargar” a 15sp, centrados. A 1,5 m es una pantalla vacía sin explicación.
10. **Pie y metadatos invisibles.** Fuente, hora de actualización, fecha, estadio y árbitro a 12–13sp. El usuario no puede saber si los datos son frescos ni de dónde vienen.
11. **Sin señal de scroll.** `scrollbars="none"` en los cuatro `ScrollView`. Con contenido largo, nada indica que hay más abajo.
12. **Color como única señal.** Pestaña seleccionada por color; flechas de cambio verde/rojo en la referencia; tarjetas amarilla/roja por color. Para daltonismo, la forma y el texto deben hacer el trabajo.
13. **El último evento queda abajo.** Orden cronológico ascendente; en directo, el evento más reciente está al final. Al refrescar no se sigue el final; el usuario que está arriba no ve lo nuevo.
14. **Etiquetas de fútbol para baloncesto.** “Gol”, “Amarilla”, “Cambio” se usan también en baloncesto, donde no hay tarjetas y cada canasta es un evento. El modelo `MatchEvent` es común; la UI debería adaptar iconos y etiquetas al deporte.
15. **Actualización con reconstrucción completa.** `renderSummary()` hace `removeAllViews()` en cada refresco. Con textos grandes, un evento nuevo puede provocar un salto visual y pérdida de contexto.

### 2.3. Lo que ya está bien y conviene conservar

- **Fondo oscuro y contraste alto:** blanco sobre `#121212` = 18,7:1; secundario `#B0B0B0` = 8,6:1; acento `#E0A85A` = 8,9:1.
- **Marcador centrado con crestas a los lados:** es la composición correcta; solo hay que escalarla.
- **Cabecera fija:** el marcador no se va al hacer scroll. Mantener.
- **Cuatro pestañas y etiquetas claras:** RESUMEN, ALINEACIONES, ESTADÍSTICAS, DÓNDE VER. Mantener, con más tamaño.
- **Refresco en vivo, caché y pie de fuente:** comportamiento correcto; solo falta que se lea.
- **`contentDescription` en marcador, equipos, estado y filas:** buena base de accesibilidad.
- **Conservación de scroll al refrescar:** ya está resuelta; hay que mejorarla para el caso “estoy al final”.
- **Sin AndroidX ni dependencias:** se mantiene.

---

## 3. Lectura de la captura del móvil

La captura adjunta es de la app móvil de 365Scores, no del modal de HomeClock. Se usa como referencia de **patrón**, no de densidad. Para la comparación se supone un móvil de 6,1" a 30 cm, que es un orden de magnitud habitual; si el móvil es más grande o se mira a 40 cm, las conclusiones no cambian, solo se reducen un poco.

### 3.1. Por qué no se puede copiar la escala

| | Móvil de 6,1" a 30 cm | Home de 8" a 1,5 m |
|---|---:|---:|
| Ancho angular | ≈ 12,4° | ≈ 6,6° |
| Alto angular | ≈ 26,4° | ≈ 4,1° |
| Texto de 14sp (mayúscula) | ≈ 20′ | ≈ 3,1′ |
| Para igualar el texto del móvil | — | ≈ 90sp |

El móvil, a 30 cm, tiene casi **el doble de ancho angular** que el Home a 1,5 m, pero el texto de 14sp del móvil ocupa **seis veces más** altura angular que el mismo 14sp en el Home. No es solo que el móvil sea más ancho: está mucho más cerca. Copiar 14sp en el Home es reducirlo a **una sexta parte** de su tamaño visual.

Por eso la propuesta no intenta “meter la app del móvil” en el Home. Elige qué se queda en la capa de vistazo y qué se lee de cerca.

### 3.2. Qué copiar, qué adaptar y qué descartar

| Patrón de la captura | Decisión | Motivo |
|---|---|---|
| Local a la izquierda, visitante a la derecha | **Copiar** | Es la lectura natural y resuelve la ambigüedad de lado. |
| Iconos de tarjeta y flechas de cambio | **Copiar** | Permiten escanear sin leer. |
| Minuto junto al evento | **Adaptar** | Centrarlo en un eje; el minuto es el ancla temporal. |
| Marcador central con crestas | **Copiar y escalar** | Composición correcta; 96sp en vez de 40sp. |
| Barra de posesión | **Copiar en ESTADÍSTICAS** | Útil, pero no debe robar altura al resumen. |
| Tarjetas redondeadas con mucho aire | **Descartar** | A 1,5 m el borde no se ve; mejor secciones planas y separación. |
| Dos eventos distintos en la misma fila (21′ y 15′) | **Descartar** | Mezcla minutos y confunde la cronología. |
| Gráfico de inercia | **Descartar** | A 1,5 m es abstracto e ilegible; necesita 200px y ejes grandes. |
| Pestañas con scroll horizontal | **Descartar** | Las cuatro caben fijas en 1280px. |
| Texto de 12–14sp | **Descartar** | Es densidad de móvil, no de panel a distancia. |
| Verde/rojo como única señal de cambio | **Descartar** | Daltonismo; usar flechas ↑/↓ y texto. |
| Barra de navegación inferior | **No aplica** | Es un patrón de móvil. |

---

## 4. Propuesta de diseño

### 4.1. Principios

1. **Dos capas, no una.** Vistazo (1,5–2 m) y detalle (0,8–1 m). No todo puede ser de 1,5 m: a esa distancia solo caben 6–7 líneas legibles. Si se exige que *todo* el detalle sea de 1,5 m, hay que paginar y renunciar a alineaciones y estadísticas; la propuesta prioriza lo que se mira a distancia y deja el resto para acercarse.
2. **El marcador manda.** 96sp, fijo, con las crestas y los nombres alrededor.
3. **El resumen está en la cabecera.** Goles y goleadores por equipo, sin tener que abrir nada.
4. **Cronología a dos lados.** Local izquierda, visitante derecha, minuto en el centro. El lado se ve por posición, no por color.
5. **Icono + texto redundante.** El icono acelera el escaneo; el texto y el `contentDescription` garantizan accesibilidad.
6. **Menos cajas, más separación.** A 1,5 m, un borde de 1px no existe. Usar espacio, títulos y cambios de fondo con contraste real.
7. **Contraste y tamaño antes que decoración.** Sin sombras, degradados ni animaciones que no aporten información.
8. **El pie no es contenido de vistazo.** Fuente y hora pueden ser 18sp; el marcador y los eventos, no.

### 4.2. Composición propuesta (1280×800)

```
┌────────────────────────────────────────────────────────────────────────────┐
│ AMISTOSO INTERNACIONAL                                            [ ✕ ]    │ 24sp
│                                                                            │
│  [cresta]  España            1 - 2            Inglaterra  [cresta]         │ 96sp
│            ⚽ 1 · Porro      83:12 · EN VIVO   Kane, Bellingham ⚽           │ 36 / 40sp
│                                                                            │
├────────────────────────────────────────────────────────────────────────────┤
│  RESUMEN    │   ALINEACIONES   │   ESTADÍSTICAS   │   DÓNDE VER           │ 30sp · 72px
├────────────────────────────────────────────────────────────────────────────┤
│  [cresta]              CRONOLOGÍA              [cresta]                    │ 28sp
│                                                                            │
│                 Declan Rice  [🟨] Amarilla  │ 15' │                         │ 48 / 28sp
│                 Pedro Porro  [⚽] · Morata   │ 21' │                         │ 48 / 28sp
│                             DESCANSO                                       │ 28sp
│                                    │ 45+2' │ [⚽] Harry Kane · Bellingham  │ 48 / 28sp
│                 Morata ↑ / Pedri ↓ [⇅] │ 60' │                             │ 36sp
│                                    │ 70' │ [⚽] Bellingham · Kane          │ 48 / 28sp
│                                    │ 76' │ [🟥] Declan Rice · 2ª amarilla  │ 48 / 28sp
│                                    │ 82' │ [VAR] Revisión de gol          │ 32sp
├────────────────────────────────────────────────────────────────────────────┤
│ Fuente: 365Scores · Actualizado 21:28                                      │ 18sp
└────────────────────────────────────────────────────────────────────────────┘
```

La cronología muestra **6 eventos + descanso completos** en los ~500px de contenido: las filas son de una sola línea y la asistencia/etiqueta va inline a 28sp. El VAR puede necesitar un scroll mínimo. En un partido con más eventos, el scroll sigue disponible.

Medidas:

| Zona | Alto | Notas |
|---|---:|---|
| Línea de competición + cierre | 38px | Cierre flotante de 64dp; no gasta una fila extra. |
| Marcador (cresta 80dp + nombre 40sp + goles 36sp) | 90–190px | Crece si el nombre o los goles ocupan dos líneas. |
| Pestañas | 72px | 30sp, subrayado de 6px en la activa. |
| Contenido | 460–540px | 6 filas de 48sp con `minHeight` 64dp (3–4dp de padding, métricas de Android) + descanso; el VAR puede quedar a un scroll mínimo. |
| Pie | 24px | Fuente y actualización. |
| Márgenes | 16px | Sin tarjeta contenedora ni esquinas redondeadas. |

La cabecera ocupa unos **160–240px** y deja **460–540px** para la cronología. Es el equilibrio entre un marcador de vistazo y una lista que se puede recorrer.

Anchos: centro del marcador **320dp**; bloques de equipo **1:1**; eje de la cronología **112dp**; columnas local/visitante **1:1**. En 1280px, cada bloque de equipo queda en unos 464px y cada columna de evento en unos 560px.

**Mockups:** [detalle actual](mockups/detalle-partido-actual-1280x800.html) ([captura](mockups/detalle-partido-actual-1280x800.png)) · [propuesta](mockups/detalle-partido-1280x800.html) ([captura](mockups/detalle-partido-1280x800.png)) · [comparativa tipográfica](mockups/comparativa-tipografia-1280x800.png). El HTML se abre a 1280×800; la propuesta muestra 6 eventos + descanso completos (el VAR queda a un scroll mínimo en la app). Las banderas circulares son marcadores de posición: en la app se usan los escudos reales de `LogoLoader`.

### 4.3. Escala tipográfica

| Token | Uso | Tamaño | Peso | Color | Mayúscula | Ángulo 1,5 m | Veredicto |
|---|---|---:|---|---|---:|---:|---|
| `score` | Marcador | 96sp (auto 64–96) | Bold | `#FFFFFF` | 9,30 mm | 21,3′ | Vistazo |
| `team` | Nombre de equipo | 40sp (auto 30–40) | Medium | `#FFFFFF` | 3,88 mm | 8,9′ | Legible |
| `status` | Estado / minuto | 40sp | Bold | live `#FFD166` / acento | 3,88 mm | 8,9′ | Legible |
| `goalLine` | Goles y goleadores | 36sp | Regular | `#FFFFFF` / `#CFCFCF` | 3,49 mm | 8,0′ | Legible |
| `eventMain` | Jugador del evento | 48sp | Medium/Bold | `#FFFFFF` | 4,65 mm | 10,7′ | Legible |
| `eventSub` | Nombres de cambio | 36sp | Medium | `#FFFFFF` | 3,49 mm | 8,0′ | Legible |
| `minute` | Minuto en el eje | 28sp | Bold | `#E0A85A` | 2,71 mm | 6,2′ | Legible justo |
| `assist` | Asistencia | 28sp | Regular | `#CFCFCF` | 2,71 mm | 6,2′ | Legible justo |
| `tag` | “Penalti”, “Propia”, “VAR” | 28sp | Bold | `#E0A85A` | 2,71 mm | 6,2′ | Legible justo |
| `section` | “CRONOLOGÍA”, “DESCANSO” | 28sp | Bold | `#E0A85A` | 2,71 mm | 6,2′ | Legible justo |
| `tab` | Pestañas | 30sp | Bold | activo `#E0A85A` | 2,90 mm | 6,6′ | Interacción |
| `message` | Vacío / error / carga | 32sp | Regular | `#CFCFCF` | 3,10 mm | 7,1′ | Legible |
| `button` | Reintentar / abrir fuente | 30sp | Bold | `#121212` sobre acento | 2,90 mm | 6,6′ | Interacción |
| `meta` | Fecha, estadio, árbitro | 24sp | Regular | `#CFCFCF` | 2,71 mm | 6,2′ | Secundario |
| `footer` | Fuente / actualizado | 18sp | Regular | `#A0A0A0` | 1,74 mm | 4,0′ | Solo cerca |

Reglas:

- **Contenido de vistazo:** ≥28sp; **contenido principal:** ≥40sp (los nombres de cambio, 36sp, son la excepción); **eventos:** 48sp; **marcador:** 96sp.
- **Ningún contenido de vistazo por debajo de 28sp.** `meta` y `footer` son la excepción explícita: no son información de vistazo, son procedencia.
- **No usar autosize en cada fila.** Solo en marcador, nombres de equipo y cambios (32–36sp). En eventos, 48sp fijos en una línea; la asistencia/etiqueta va inline y se elide si no cabe; el nombre del jugador no se encoge.
- **No usar `letterSpacing` en texto grande.** Solo en etiquetas cortas en mayúsculas (0,02–0,04). El 0,08–0,1 actual a 12sp separa las letras y empeora la lectura.
- **No usar todo mayúsculas en nombres de jugador.** Ralentiza la lectura; reservarlo para secciones y estados.
- **Cifras tabulares** (`fontFeatureSettings="tnum"`) en marcador y minutos para que no bailen al actualizar.
- **Marcadores largos:** el fútbol se queda en 96sp; un marcador de baloncesto de tres cifras (“102 - 98”) baja por autosize a 64–72sp para caber en los 320dp centrales. A 64sp sigue siendo cómodo (14,2′ a 1,5 m).

### 4.4. Cabecera: el resumen de un vistazo

La cabecera deja de ser solo “cresta + nombre + marcador” y añade una línea de goles y goleadores por equipo:

```
[cresta]  ESPAÑA                          INGLATERRA  [cresta]
          ⚽ 2 · Morata, Oyarzabal          Kane, Bellingham ⚽
                        0 - 0
                    39:44 · EN VIVO
```

- **Local:** cresta 80dp a la izquierda, nombre 40sp, línea de goles 36sp alineada a la izquierda.
- **Visitante:** espejo: línea de goles alineada a la derecha, nombre, cresta a la derecha.
- **Centro:** marcador 96sp, estado 40sp debajo.
- **Línea de goles:** `⚽ <número> · <goleadores>`, hasta dos nombres y `+N` si hay más; sin minutos, que ya están en la cronología. Si no hay goles: “Sin goles” en `#CFCFCF`. Si el partido no ha empezado: se omite y el estado muestra la fecha/hora.
- **Número de goles:** usar el marcador oficial (`homeScore`/`awayScore`), no el tamaño de la lista de eventos. La fuente puede no publicar todos los eventos.
- **Goleadores:** los eventos de gol válidos de ese lado, en orden. En propia puerta, el gol cuenta para el equipo contrario y el jugador se etiqueta “Propia”. Si faltan eventos, se muestran los disponibles; el conteo oficial manda.
- **Sin chips de ranking FIFA** ni datos que no existan en HomeClock. La captura del móvil los tiene; aquí no aportan y roban espacio.
- **Estado:** “EN VIVO · 39:44” en `#FFD166`, “DESCANSO”, “FINAL” o “MAÑANA 21:00” en acento. El estado nunca depende solo del color: el texto lo dice.

La línea de goles es la única concesión a 36sp (8,0′). Si se quiere leer también sin esfuerzo a 1,5 m, se puede subir a 40sp y aceptar dos líneas; el marcador sigue siendo el protagonista.

### 4.5. Cronología a dos lados

Anatomía de una fila:

```
 LOCAL (derecha hacia el centro)        EJE        VISITANTE (izquierda hacia el centro)
┌──────────────────────────────────┬──────────┬──────────────────────────────────┐
│ Pedro Porro  [⚽] · Morata        │   21'    │                                  │
├──────────────────────────────────┼──────────┼──────────────────────────────────┤
│                                  │   15'    │ [🟨]  Declan Rice · Amarilla     │
├──────────────────────────────────┼──────────┼──────────────────────────────────┤
│ Morata ↑ / Pedri ↓  [⇅]          │   60'    │                                  │
├──────────────────────────────────┼──────────┼──────────────────────────────────┤
│                                  │   70'    │ [⚽]  Bellingham · Kane          │
└──────────────────────────────────┴──────────┴──────────────────────────────────┘
```

Reglas:

- **Columna local:** `layout_weight=1`, `gravity=end`. El bloque de texto se alinea a la derecha; el icono va en el borde interior, pegado al minuto.
- **Columna visitante:** `layout_weight=1`, `gravity=start`. Icono en el borde interior, texto a la izquierda.
- **Eje central:** 112dp de ancho, minuto 28sp centrado, cifras tabulares. “45+2′” y “90+12′” caben.
- **Iconos:** 48dp; 40×48dp para tarjetas. En la columna interior, formando dos columnas visuales que enmarcan el minuto.
- **Fila de una línea:** nombre + asistencia/etiqueta inline + icono + minuto. Altura objetivo: 3–4dp de padding vertical y `minHeight` 64dp (métricas de Android; el line-height del mockup es de CSS). La asistencia se elide; el nombre del jugador nunca se recorta por debajo de 40sp. Si con 6 eventos + descanso + VAR no cabe con seguridad, se mantienen 6 + descanso y el VAR queda a un scroll mínimo.
- **Separación:** 8dp vertical por fila, 24dp entre grupos. Sin divisores de 1px. Si se usa una línea, al menos 2px y un color visible (`#5A5A5A` o más claro), y solo entre grupos; mejor el espacio.
- **Gol:** nombre 48sp bold; asistencia 28sp inline separada por `·`; icono de balón 48dp. Sin la palabra “Gol” (el balón y el marcador ya lo dicen), salvo en penalti/propia/anulado. Ejemplo: `Pedro Porro · Morata`.
- **Amarilla / roja:** nombre 48sp; icono de tarjeta 40×48dp; etiqueta “Amarilla” / “Roja” a 28sp inline. El color no es la única señal: la etiqueta y el `contentDescription` lo explican; el borde blanco de la roja es un refuerzo, no la señal principal.
- **Segunda amarilla:** dos tarjetas solapadas (amarilla detrás, roja delante) 48dp; etiqueta “2ª amarilla” a 28sp inline. Es distinta de la roja directa.
- **Cambio:** una línea: `Morata ↑ / Pedri ↓` a 36sp, con flechas 40dp (↑ entra, ↓ sale). Si los dos nombres no caben, 32sp en una línea o nombres cortos; nunca dos líneas. No usar solo verde/rojo: las flechas y el orden “entra/sale” son la señal.
- **VAR / neutral:** fila centrada a todo el ancho, icono 48dp y etiqueta “VAR · Revisión” a 32sp; minuto 28sp a la derecha. No pertenece a ningún lado.
- **Descanso:** fila divisoria con “DESCANSO” 28sp y una línea de 2px; 2dp de padding vertical. Ayuda a orientarse en partidos largos.
- **Final:** fila “FINAL” 28sp al terminar.
- **Orden:** cronológico ascendente. En directo, hacer **auto-scroll al último evento** al abrir y si el usuario ya estaba al final; si está leyendo arriba, conservar la posición. En un partido terminado, empezar arriba (el relato desde el inicio).
- **Nombres largos:** la fila es de una línea; si el nombre + detalle no cabe, se elide el detalle (asistencia/etiqueta), no el goleador. Si el nombre solo no cabe, se usa el nombre corto; la elipsis del nombre queda como último recurso. Nunca se encoge a 11sp.

### 4.6. Iconografía

Todos los iconos son **VectorDrawable** locales, sin emojis ni dependencias. Tamaño base 48dp; las tarjetas, 40×48dp dentro de un contenedor de 48dp. Los símbolos `⚽` y `🟨` de los wireframes son solo ilustrativos; la implementación usa vectores.

| Evento | Icono | Tamaño | Color | Etiqueta de apoyo | `contentDescription` |
|---|---|---:|---|---|---|
| Gol | Balón | 48dp | Blanco | — | “Gol de {equipo}. {jugador}, minuto {minuto}.” |
| Penalti | Balón + “P” | 48dp | Blanco/acento | “Penalti” 28sp | “Gol de penalti de {equipo}. {jugador}, minuto {minuto}.” |
| Gol en propia | Balón + flecha atrás | 48dp | Blanco | “Propia” 28sp | “Gol en propia puerta de {jugador}, {equipo}. Minuto {minuto}.” |
| Gol anulado | Balón + barra | 48dp | Blanco | “Anulado” 28sp | “Gol anulado a {equipo}. {jugador}, minuto {minuto}.” |
| Amarilla | Rectángulo | 40×48dp | `#FFC400` | “Amarilla” 28sp | “Tarjeta amarilla para {equipo}. {jugador}, minuto {minuto}.” |
| Roja | Rectángulo | 40×48dp | `#E53935` + borde blanco 2px | “Roja” 28sp | “Tarjeta roja para {equipo}. {jugador}, minuto {minuto}.” |
| Segunda amarilla | Dos rectángulos | 48dp | Amarillo + rojo | “2ª” 24sp | “Segunda amarilla, expulsión de {equipo}. {jugador}, minuto {minuto}.” |
| Cambio | Flechas ↑↓ | 48dp | Verde `#43A047` / rojo `#E53935` | “Entra / sale” 24sp si hace falta | “Cambio en {equipo}. Entra {jugador}, sale {secundario}. Minuto {minuto}.” |
| VAR | Marco “VAR” | 48×40dp | Blanco | “VAR” 28sp | “Revisión VAR, minuto {minuto}.” |
| Otra incidencia | Punto / info | 40dp | `#CFCFCF` | Etiqueta 28sp | “{etiqueta} de {equipo}. {jugador}, minuto {minuto}.” |

Notas:

- **No usar emoji** (`⚽`, `🟨`): la fuente de emoji cambia entre versiones de Android, el color no se controla y la alineación a 48sp es impredecible.
- **No usar iconos solos para penalti, propia, anulado o VAR.** A 1,5 m la diferencia entre balón, balón con “P” y balón con barra es demasiado sutil; la etiqueta de 28sp resuelve.
- **Las tarjetas llevan etiqueta corta:** “Amarilla” / “Roja” a 28sp. El color solo no basta para daltonismo; el rectángulo amarillo/rojo es el icono, la etiqueta es la garantía. La segunda amarilla se distingue por las dos tarjetas y la etiqueta “2ª”.
- **El icono es decorativo para accesibilidad:** `contentDescription=null` e `importantForAccessibility=no`; la fila completa lleva la descripción. Así TalkBack lee una frase, no “imagen, imagen, texto”. La fila es el nodo accesible (`focusable=true`, `contentDescription` completo) y los hijos quedan como no importantes.
- **Colores de tarjeta:** `#FFC400` (11,7:1) y `#E53935` (4,4:1). La roja lleva borde blanco de 2px como refuerzo; la etiqueta de 28sp es la que garantiza la lectura sin depender del color. El texto de la fila es blanco, no rojo.

### 4.7. Color y contraste

| Token | Hex | Fondo | Ratio | Uso | Nota |
|---|---|---|---:|---|---|
| Texto primario | `#FFFFFF` | `#121212` | 18,7:1 | Marcador, nombres | AAA |
| Texto secundario | `#CFCFCF` | `#121212` | 12,0:1 | Minutos, asistencia | Mejor que el `#B0B0B0` actual (8,6:1) |
| Acento | `#E0A85A` | `#121212` | 8,9:1 | Secciones, minuto | AAA |
| Directo | `#FFD166` | `#121212` | 13,0:1 | “EN VIVO” | AAA |
| Amarilla | `#FFC400` | `#121212` | 11,7:1 | Icono | AAA |
| Roja | `#E53935` | `#121212` | 4,4:1 | Icono | ≥3:1 para gráficos; etiqueta “Roja” y borde |
| Roja texto | `#FF6B6B` | `#121212` | 6,8:1 | Si hace falta texto | AA |
| Cambio entra | `#43A047` | `#121212` | 5,7:1 | Flecha | No usar como única señal |
| Visitante | `#4FC3F7` | `#121212` | 9,4:1 | Barra de estadística | Alternativa al gris actual |
| Superficie | `#1C1A18` | `#121212` | 1,08:1 | Tarjetas | **No separa** |
| Borde actual | `#2A2520` | `#121212` | 1,23:1 | Tarjetas | **No separa** |
| Borde propuesto | `#5A5A5A` | `#121212` | 2,7:1 | Solo si hace falta | Aún <3:1; mejor espacio/título |
| Pie actual | `#808080` | `#121212` | 4,7:1 | Pie | Pasa texto, pero a 12sp no se lee |
| Pie propuesto | `#A0A0A0` | `#121212` | 7,2:1 | Pie | AAA |

Decisiones:

- **No usar el color para distinguir local/visitante.** La posición (izquierda/derecha), la cresta y el nombre lo hacen. En las barras de estadísticas sí hace falta color, pero con etiquetas y valores.
- **No usar rojo/verde como única señal en cambios.** Flechas ↑/↓, orden “entra/sale” y `contentDescription`.
- **No confiar en la superficie `#1C1A18`.** Si se quiere una tarjeta, el borde debe ser ≥3:1 (`#6B6B6B` o más claro) o, mejor, sustituirla por secciones planas con 24dp de separación y un título de 28sp.
- **El fondo oscuro se mantiene.** En modo noche, el brillo baja; el texto blanco y el acento siguen teniendo margen. La roja a 4,4:1 es el token más justo; la etiqueta y el borde ayudan.

### 4.8. Pestañas, cierre y scroll

- **Pestañas:** 72dp de alto, 30sp, cuatro partes iguales. Activa: texto acento + subrayado de 6dp + fondo `#2A2118`. Inactiva: `#CFCFCF`.
- **Cierre:** objetivo de 64dp, icono “✕” 36sp, arriba a la derecha. No gastar una fila solo para cerrar; va sobre la línea de competición.
- **Scroll:** `scrollbars="vertical"`, `fadeScrollbars="false"`, thumb de 8dp `#808080`. A 1,5 m, 8px = 1,1 mm = 2,5′; se ve. Añadir un degradado inferior de 24dp cuando quede contenido.
- **Foco/táctil:** 64dp mínimo para cierre, pestañas y botones. A 1,5 m no se toca; a 0,5–0,8 m, 64dp es cómodo.
- **Sin esquinas redondeadas en el modal:** el diálogo ocupa toda la pantalla; 20dp de margen y 18dp de radio solo gastan espacio. Usar full-bleed con 16dp de padding interior.

### 4.9. Estados vacíos, error y carga

| Estado | Icono | Texto principal | Apoyo | Acción |
|---|---|---|---|---|
| Cargando | Spinner 48dp | “Cargando…” 32sp | — | — |
| Previo | Calendario 64dp | “El partido aún no ha comenzado” 32sp | “Hora: {hora}” 28sp | — |
| Directo sin eventos | Silbato 64dp | “Sin acciones todavía” 32sp | “El partido acaba de empezar” 28sp | — |
| Final sin eventos | Silbato 64dp | “No hay eventos registrados” 32sp | “La fuente no publica el detalle” 28sp | — |
| Error | Triángulo 64dp | “No se pudo cargar el detalle” 32sp | El marcador de la cabecera sigue visible | “Reintentar” 30sp / 64dp |
| Fuente sin detalle | Enlace 64dp | “Esta fuente no ofrece detalle” 32sp | — | “Abrir en {fuente}” 30sp / 64dp |
| Sin estadísticas | Gráfico 64dp | “Estadísticas no disponibles” 32sp | “Se publican al empezar” 28sp | — |
| Sin alineaciones | Lista 64dp | “Alineaciones no publicadas” 32sp | “Se confirman una hora antes” 28sp | — |

Ningún estado debe dejar la pantalla en negro sin explicación. La cabecera se mantiene siempre que haya datos básicos del partido.

### 4.10. Resto de pestañas

| Elemento | Ahora | Propuesta | Motivo |
|---|---:|---:|---|
| Alineaciones: equipo | 17sp | 36sp | Lectura a 1 m |
| Alineaciones: jugador | 14sp | 32sp | 7,1′ a 1,5 m |
| Alineaciones: dorsal | 14sp | 32sp | Cifra tabular |
| Alineaciones: posición | 11sp | 24sp | Secundario |
| Alineaciones: nota | 11sp | 24sp | Secundario |
| Estadísticas: valor | 14sp | 40sp | 8,9′ |
| Estadísticas: nombre | 13sp | 28sp | 6,2′ |
| Estadísticas: categoría | 13sp | 28sp | 6,2′ |
| Estadísticas: barra | 4dp | 16dp | 2,1 mm |
| TV: emisora | 17sp | 36sp | 8,0′ |
| TV: catálogo | 17sp | 36sp | 8,0′ |
| TV: detalle | 13sp | 24sp | Secundario |
| TV: aviso | 12sp | 20sp | Solo cerca |
| Pie | 12sp | 18sp | Procedencia |
| Botones | 15sp / 48dp | 30sp / 64dp | Táctil y visible |
| Ficha (estadio, árbitro) | 13–15sp | 24–28sp | Al final del resumen |

Las pestañas de alineaciones y estadísticas son de **acercarse**, no de vistazo. 32sp es suficiente para 1 m sin convertir la lista en infinita. La cabecera y las pestañas sí son comunes y se escalan una sola vez.

---

## 5. Plan de implementación

### 5.1. Fase 1 — P0: lo que pide el usuario

1. **Escala tipográfica y filas de una línea** en `dialog_match_detail.xml` y `MatchDetailDialog.java`: marcador 96sp, equipos/estado 40sp, goles 36sp, eventos 48sp, minutos 28sp, secciones 28sp, pestañas 30sp, mensajes 32sp. Asistencia/etiqueta inline; 6 eventos + descanso visibles.
2. **Cabecera nueva:** cresta 80dp, nombres 40sp, línea de goles por equipo, marcador 96sp, estado 40sp.
3. **Cronología a dos lados:** local izquierda, visitante derecha, minuto central, iconos interiores.
4. **Iconos vectoriales:** gol, penalti, propia, anulado, amarilla, roja, segunda amarilla, cambio, VAR, otra.
5. **Resumen de goles** en la cabecera (conteo oficial + goleadores por lado).
6. **Pestañas y cierre** a 72dp/64dp con subrayado de selección.
7. **Estados vacíos/error/carga** a 32sp con icono.
8. **Scroll visible** y full-bleed sin tarjeta contenedora.
9. **`contentDescription`** de fila completa y iconos decorativos.

**Estimación:** 1,5–2 días. Es el mínimo para cumplir “leer a 1,5 m”, “local izquierda / visitante derecha” e “iconos”.

### 5.2. Fase 2 — P1: pulir el vistazo

1. **Franja de posesión** en ESTADÍSTICAS (no en el resumen): 40sp, barra de 16dp, etiquetas.
2. **Conteo de tarjetas** por equipo en una línea de 28–32sp, si no compite con los goles.
3. **Descanso y final** como divisores de la cronología.
4. **Marcador en el momento del gol** (“21′ · 1–0”) calculado desde los eventos.
5. **Auto-scroll al último evento** en directo; conservar posición si el usuario está leyendo arriba.
6. **Badge de actualización** (“Actualizado 21:28”) a 20–24sp, o integrarlo en el estado.
7. **Ajuste de fuentes a 1,15×** y nombres largos.

**Estimación:** 1 día.

### 5.3. Fase 3 — P2: extras

1. Animación suave al cambiar el marcador (sin parpadeos; 300ms).
2. Filtro “goles / tarjetas / cambios” en la cronología.
3. “Ver desde el inicio” o inversión del orden.
4. Iconos y etiquetas según deporte (baloncesto: canasta, faltas, tiempos muertos).
5. Modo “distancia” que oculte metadatos y aumente aún más el contenido.

**Estimación:** 1–2 días. No es necesario para la primera entrega.

### 5.4. Mapa de cambios

| Archivo | Cambio |
|---|---|
| `app/src/main/res/layout/dialog_match_detail.xml` | Cabecera `wrap_content`, cresta 80dp, nombres 40sp, marcador 96sp, estado 40sp, línea de goles, pestañas 72dp/30sp, padding 16dp, scrollbars. |
| `app/src/main/java/.../MatchDetailDialog.java` | Constantes de tipografía; `bindHeader()` con goles; `renderSummary()` con cronología; `eventRow()` → `timelineRow()`; iconos; estados; accesibilidad; auto-scroll. |
| `app/src/main/res/values/colors.xml` | Secundario `#CFCFCF`; barra visitante `#4FC3F7`; colores de tarjeta; borde de tarjeta si se mantiene. |
| `app/src/main/res/drawable/ic_ev_*.xml` | 10 iconos vectoriales (gol, penalti, propia, anulado, amarilla, roja, segunda, cambio, VAR, otra). |
| `app/src/main/java/.../MatchSummary.java` | Nuevo helper puro: goles por lado, goleadores, tarjetas, orden. |
| `app/src/test/java/.../MatchSummaryTest.java` | Tests de propia, penalti, anulado, segunda amarilla, neutral, vacío, conteo vs. marcador. |
| `app/src/main/res/values/strings.xml` | “Sin goles”, “Penalti”, “Propia”, “Anulado”, “2ª”, “Entra”, “Sale”, “Asiste”, “DESCANSO”, “FINAL”, “CRONOLOGÍA” y descripciones accesibles. |
| `MatchDetailParser`, `MatchDetail`, `MatchEvent` | Sin cambios de modelo; el helper consume lo que ya hay. |
| `docs/BACKLOG.md` | Enlazar este estudio cuando se priorice la fase 1 (opcional). |

### 5.5. Tests y verificación

- **Unitarios:** `MatchSummaryTest` y, si se extrae, `TimelineOrderTest`.
- **Comandos:** `./gradlew --no-daemon testDebugUnitTest`, `./gradlew --no-daemon lintDebug`, `./gradlew --no-daemon assembleDebug`.
- **Manual:** captura a 1280×800, medida de altura de texto, prueba a 1,5 m y 2 m, nombres largos, 30 eventos, refresco en vivo, modo noche, daltonismo y TalkBack.
- **No tocar** `HOMECLOCK_VERSION_CODE` ni `HOMECLOCK_VERSION_NAME` hasta una release autorizada.

---

## 6. Validación en el dispositivo

### 6.1. Cómo medir en 5 minutos

1. Abrir el detalle y hacer una captura de 1280×800.
2. Medir en la imagen la altura de mayúscula del marcador, los nombres y un evento (px).
3. Convertir: `mm = px × 0,1346`; `arcmin = px × 0,3085`.
4. Si el marcador no llega a 20′ y los eventos a 10′, no está a la altura de 1,5 m.
5. Prueba subjetiva: colocar el Home a 1,5 m y leer sin acercarse. Repetir a 2 m para el marcador.

### 6.2. Criterios de aceptación

- [ ] El marcador de fútbol se lee a **2 m** sin esfuerzo (≥15′; 96sp = 16′ a 2 m).
- [ ] Los nombres de equipo, el estado y la línea de goles se leen a **1,5 m** (≥8′).
- [ ] Los nombres de los eventos se leen a 1,5 m (≥10′; 48sp = 10,7′).
- [ ] Se ven al menos **6 eventos + descanso** sin hacer scroll.
- [ ] Los minutos y asistencias se leen a 1,5 m (≥6′; 28sp = 6,2′).
- [ ] Se distinguen local/visitante **sin color** (posición + cresta + nombre).
- [ ] Se distinguen gol, tarjeta, cambio y VAR **sin color** (forma + etiqueta).
- [ ] Contraste de texto ≥4,5:1; iconos ≥3:1.
- [ ] Objetivos táctiles ≥64dp.
- [ ] Ningún contenido de vistazo por debajo de 28sp.
- [ ] A 1,15× de escala de fuente no hay recortes ni solapamientos.
- [ ] Nombres largos y 30 eventos no rompen la composición.
- [ ] El refresco en vivo no da tirones ni pierde la posición.
- [ ] TalkBack lee una frase completa por evento.
- [ ] En modo noche (brillo 20%) se sigue leyendo el marcador y los eventos.

### 6.3. Prueba con personas

- 3–5 personas, con y sin gafas, a 1,5 m.
- Preguntas: “¿Quién va ganando?”, “¿Quién ha marcado?”, “¿Cuántas amarillas tiene cada equipo?”, “¿Qué ha pasado en el minuto 60?”.
- Si necesitan acercarse o señalar con el dedo, la respuesta es no.

---

## 7. Anexo

### 7.1. Fórmulas

```text
ppi        = sqrt(1280² + 800²) / 8 = 188,7
pitch_mm   = 25,4 / ppi = 0,1346 mm
arcmin_px  = 2 · atan(pitch / (2 · 1500)) · 60 = 0,3085′
cap_px     = 0,72 · S
arcmin_cap = 0,72 · S · 0,3085 = 0,222 · S
S          = 4,5 · arcmin_cap      (a 1,5 m)
```

### 7.2. Antes / después

| Elemento | Ahora | Propuesta | Factor |
|---|---:|---:|---:|
| Marcador | 40sp | 96sp | ×2,4 |
| Nombre de equipo | 20sp | 40sp | ×2,0 |
| Estado | 15sp | 40sp | ×2,7 |
| Línea de goles | — | 36sp | Nueva |
| Pestañas | 14sp | 30sp | ×2,1 |
| Jugador de evento | 15sp | 48sp | ×3,2 |
| Cambio | 15sp | 36sp | ×2,4 |
| Minuto | 12sp | 28sp | ×2,3 |
| Asistencia | 12sp | 28sp | ×2,3 |
| Etiqueta | 11sp | 28sp | ×2,5 |
| Sección | 12sp | 28sp | ×2,3 |
| Mensaje | 15sp | 32sp | ×2,1 |
| Pie | 12sp | 18sp | ×1,5 |
| Cresta | 56dp | 80dp | ×1,4 |
| Alto de pestaña | 48dp | 72dp | ×1,5 |

### 7.3. Decisiones descartadas

- **Aumentar todo sin jerarquía:** a 1,5 m solo caben 6–7 líneas legibles; si todo es grande, nada destaca. La jerarquía es parte de la solución.
- **Copiar la densidad del móvil:** el Home a 1,5 m tiene la mitad de ancho angular que un móvil a 30 cm, y su texto ocupa una sexta parte de la altura angular.
- **Gráfico de inercia:** necesita 200px y ejes; a 1,5 m es abstracto y compite con el marcador.
- **Emoji como iconos:** dependen de la fuente del sistema, no se controla el color y la alineación a 48sp es impredecible.
- **Solo color para local/visitante o tarjetas:** falla con daltonismo y en modo noche.
- **Tarjetas con borde de 1px:** a 1,5 m el borde no existe. Mejor secciones planas y espacio.
- **Todo el detalle a 1,5 m:** imposible sin convertir la pantalla en 8 líneas. La capa de detalle es para 0,8–1 m.

### 7.4. Referencias

- WCAG 2.2, 1.4.3 (contraste de texto) y 1.4.11 (contraste de elementos no textuales).
- Agudeza 20/20: letra de 5′ de altura; lectura continua: 10–12′; comodidad: 12–15′; vistazo: 20–25′.
- Especificaciones del Movistar Home RG3205W: 8 pulgadas, 1280×800, Android 8.1.
- Captura de referencia: app móvil de 365Scores, 720×1600, usada solo como patrón de composición.

---

**Siguiente paso recomendado:** implementar la fase 1 (tipografía, cabecera con goles, cronología a dos lados e iconos) y validar con una captura a 1280×800 y una prueba a 1,5 m antes de tocar las fases 2 y 3.
