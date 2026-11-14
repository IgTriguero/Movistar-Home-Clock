# HomeClock — instrucciones para agentes

App Android Java para un Movistar Home RG3205W en horizontal: reloj, tiempo y partidos. Sin AndroidX ni dependencias de runtime.

## Comandos

- Tests: `./gradlew --no-daemon testDebugUnitTest`
- Un test: `./gradlew --no-daemon testDebugUnitTest --tests "com.itriguero.homeclock.CityTest"`
- Lint: `./gradlew --no-daemon lintDebug`
- APK debug: `./gradlew --no-daemon assembleDebug`
- APK release: `./gradlew --no-daemon assembleRelease`
- Invoca `./gradlew` directamente. No uses `bash ./gradlew`, `sh -c` ni `/bin/zsh -lc`.

## Estructura

- Código: `app/src/main/java/com/itriguero/homeclock/`
- Layouts: `app/src/main/res/layout/`
- Tests: `app/src/test/java/com/itriguero/homeclock/`
- Fixtures: `app/src/test/resources/fixtures/`
- Backlog: `docs/BACKLOG.md`
- Diseño técnico: `ESTUDIO.md`
- Release: `docs/RELEASE.md`

## Convenciones

- Java 17, minSdk 26, targetSdk 34, compileSdk 35.
- Sin AndroidX ni dependencias nuevas sin aprobación.
- Solo horizontal. JUnit 4. Comentarios y tests en español.
- No crear ramas, no hacer commit, no hacer push y no crear tags salvo que el usuario pida una release o lo autorice explícitamente.
- Antes de declarar una tarea terminada: ejecutar los tests y pegar la salida real. No afirmes que pasa si no lo has ejecutado.

## Versionado y releases

Regla obligatoria:

1. **Durante el desarrollo normal no se toca la versión.** `HOMECLOCK_VERSION_CODE` y `HOMECLOCK_VERSION_NAME` de `gradle.properties` solo se cambian al preparar una release.
2. **Antes de cambiar la versión, pregunta siempre al usuario.** No asumas ni elijas tú. La pregunta exacta es:

   > ¿La release es una versión menor `1.X` (nuevas funciones) o un fix `1.X.Y` (correcciones de la `1.X` actual)?

3. Espera la respuesta. Si el usuario no responde o no lo tiene claro, no toques la versión.
4. `HOMECLOCK_VERSION_CODE` se incrementa siempre en 1. Nunca se reutiliza.
5. El tag debe coincidir con `HOMECLOCK_VERSION_NAME`: `v1.X` o `v1.X.Y`.
6. Una release solo se considera creada cuando existe el tag y el workflow de GitHub ha publicado el APK y `update.json`.
7. Si el usuario no ha pedido una release, no subas la versión, no crees tags y no ejecutes el workflow de release.
8. Los subagentes de código (`homeclock-coder`) no pueden editar `gradle.properties`; la versión solo la cambia el flujo de release.

El procedimiento completo está en `docs/RELEASE.md`. Para preparar una release, usa el comando `/release`.
