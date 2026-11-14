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

## Checklist

1. Preguntar y confirmar la versión exacta.
2. Ejecutar `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`.
3. Actualizar `HOMECLOCK_VERSION_NAME` y `HOMECLOCK_VERSION_CODE` en `gradle.properties`.
4. Actualizar capturas, README o notas si el cambio lo requiere.
5. Commit `release: HomeClock X.Y` o `release: HomeClock X.Y.Z`.
6. Crear el tag `vX.Y` o `vX.Y.Z`.
7. Ejecutar `git push origin main --tags`.
8. Verificar el workflow **Android CI** y que la release publicada tenga el APK y `update.json`.
9. No anunciar la release hasta que el workflow termine correctamente.

## Qué no hacer

- No subir la versión en commits normales de features o fixes.
- No reutilizar un `versionCode`.
- No crear un tag sin haber actualizado antes `gradle.properties`.
- No empujar el tag si los tests o el lint fallan.
- No cambiar la versión por iniciativa propia: siempre la elige el usuario.
