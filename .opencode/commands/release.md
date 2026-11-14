---
description: Prepara una release de HomeClock. Pregunta siempre si es 1.X o 1.X.Y antes de tocar la versión.
agent: build
---

Vas a preparar una release de HomeClock. Sigue `docs/RELEASE.md`.

## Regla obligatoria

No modifiques `gradle.properties`, no hagas commit, no crees el tag y no hagas push hasta que el usuario responda y confirme la versión.

Primero pregunta exactamente:

> ¿La release es una versión menor `1.X` (nuevas funciones) o un fix `1.X.Y` (correcciones de la `1.X` actual)?

Usa la herramienta `question` si está disponible. No asumas la respuesta ni propongas una versión como si ya estuviera decidida.

## Después de la respuesta

1. Lee la versión actual de `gradle.properties` y el último tag con `git tag --sort=-v:refname | head -1`.
2. Calcula la versión propuesta:
   - Menor: sube el minor y deja el patch en 0 (`1.0` → `1.1`, `1.1` → `1.2`).
   - Fix: sube el patch de la `1.X` actual (`1.0` → `1.0.1`, `1.1` → `1.1.1`).
3. Propón la versión exacta y pide confirmación antes de editar nada.
4. Ejecuta `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`. Si falla, para y reporta.
5. Actualiza `HOMECLOCK_VERSION_NAME` y `HOMECLOCK_VERSION_CODE` (siempre +1) en `gradle.properties`.
6. Haz commit `release: HomeClock X.Y` o `release: HomeClock X.Y.Z`.
7. Crea el tag `vX.Y` o `vX.Y.Z`.
8. Ejecuta `git push origin main --tags`.
9. Verifica el workflow **Android CI** y que la release tenga APK y `update.json`.
10. No afirmes que la release está creada hasta que el workflow haya terminado correctamente.

Si el usuario no confirma la versión, no continúes.
