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
4. Crea o actualiza `docs/release-notes/<versión>.md` con **todos** los cambios visibles desde el tag anterior. Usa `git log --oneline <último-tag>..HEAD` y el diff para no olvidar nada. Incluye nuevas funciones, mejoras y fixes; no es un volcado de commits.
5. Ejecuta `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`. Si falla, para y reporta.
6. Actualiza `HOMECLOCK_VERSION_NAME` y `HOMECLOCK_VERSION_CODE` (siempre +1) en `gradle.properties`.
7. Haz commit `release: HomeClock X.Y` o `release: HomeClock X.Y.Z`, incluyendo el changelog.
8. Crea el tag `vX.Y` o `vX.Y.Z`.
9. Ejecuta `git push origin main --tags`.
10. Verifica el workflow **Android CI** y que la release tenga APK y `update.json`.
11. Comprueba que el cuerpo de la release de GitHub y las notas de `update.json` coinciden con el changelog. El CI los genera desde `docs/release-notes/<versión>.md`; no los edites a mano.
12. No afirmes que la release está creada hasta que el workflow haya terminado correctamente.

El changelog es obligatorio en toda release; no esperes a que el usuario lo pida.

Si el usuario no confirma la versión, no continúes.
