# Release de HomeClock

Para preparar una release desde OpenCode, usa el comando `/release`. Te preguntará lo primero qué tipo de versión es.

## Regla de oro

**No se sube la versión durante el desarrollo normal.** `HOMECLOCK_VERSION_CODE` y `HOMECLOCK_VERSION_NAME` de `gradle.properties` solo se cambian cuando se va a crear una release.

## Antes de tocar la versión

Preguntar siempre al usuario:

> ¿La release es una versión menor `1.X` (nuevas funciones) o un fix `1.X.Y` (correcciones de la `1.X` actual)?

No asumir la respuesta, no elegir la versión y no tocar `gradle.properties` hasta tenerla.

## Cómo calcular la versión

| Tipo | Regla | Desde `1.0` | Desde `1.1` |
|---|---|---|---|
| Menor `1.X` | Sube el minor y deja el patch en 0 | `1.1` | `1.2` |
| Fix `1.X.Y` | Sube el patch de la `1.X` actual | `1.0.1` | `1.1.1` |

- `HOMECLOCK_VERSION_CODE` siempre se incrementa en 1. Nunca se reutiliza.
- `HOMECLOCK_VERSION_NAME` es la versión elegida.
- El tag es `v` + la versión: `v1.1`, `v1.0.1`, `v1.1.1`.
- La versión del tag debe coincidir exactamente con `HOMECLOCK_VERSION_NAME`.

## Changelog obligatorio

Toda release debe incluir `docs/release-notes/<versión>.md` antes del commit. Por ejemplo: `docs/release-notes/1.2.md` o `docs/release-notes/1.1.2.md`. Puedes usar `docs/release-notes/1.1.md` y `docs/release-notes/1.1.1.md` como referencia.

- Escríbelo en español y pensado para el usuario final.
- Incluye **todo** lo nuevo y lo corregido desde la release anterior, agrupado por área.
- No es un volcado de commits: explica el cambio visible y su efecto.
- El archivo es la fuente de verdad de:
  - el cuerpo de la release de GitHub (`body_path` del workflow);
  - las notas que muestra la app en `update.json.notes`.
- Si el archivo falta o está vacío, el workflow de release falla.

Ejemplo de estructura:

````md
# HomeClock X.Y

Resumen breve de la release.

## Cambios

- ...
- ...

## Instalación

```bash
adb install -r homeclock-X.Y.apk
```
````

## Checklist

1. Preguntar y confirmar la versión exacta.
2. Crear o actualizar `docs/release-notes/<versión>.md` con todos los cambios visibles.
3. Ejecutar `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`.
4. Actualizar `HOMECLOCK_VERSION_NAME` y `HOMECLOCK_VERSION_CODE` en `gradle.properties`.
5. Actualizar capturas, README o notas si el cambio lo requiere.
6. Commit `release: HomeClock X.Y` o `release: HomeClock X.Y.Z` (incluye el changelog).
7. Crear el tag `vX.Y` o `vX.Y.Z`.
8. Ejecutar `git push origin main --tags`.
9. Verificar el workflow **Android CI** y que la release publicada tenga el APK y `update.json`.
10. Verificar que el cuerpo de la release de GitHub y las notas de `update.json` coinciden con el changelog.
11. No anunciar la release hasta que el workflow termine correctamente.

## Qué no hacer

- No subir la versión en commits normales de features o fixes.
- No reutilizar un `versionCode`.
- No crear un tag sin haber actualizado antes `gradle.properties`.
- No crear una release sin `docs/release-notes/<versión>.md`.
- No editar a mano la release de GitHub ni `update.json`: el CI los genera desde el changelog.
- No empujar el tag si los tests o el lint fallan.
- No cambiar la versión por iniciativa propia: siempre la elige el usuario.
