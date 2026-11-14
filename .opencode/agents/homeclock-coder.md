---
description: Implementa cambios en HomeClock y ejecuta tests/build de Gradle. Úsalo para una fase de código acotada.
mode: subagent
model: "opencode-go/deepseek-v4.1-flash#max"
permissions:
  - action: shell
    resource: "*"
    effect: deny
  - action: shell
    resource: "./gradlew *"
    effect: allow
  - action: shell
    resource: "git status *"
    effect: allow
  - action: shell
    resource: "git diff *"
    effect: allow
  - action: shell
    resource: "git log *"
    effect: allow
  - action: shell
    resource: "ls *"
    effect: allow
  - action: shell
    resource: "find *"
    effect: allow
  - action: shell
    resource: "pwd"
    effect: allow
  - action: shell
    resource: "java -version"
    effect: allow
  - action: subagent
    resource: "*"
    effect: deny
  - action: question
    resource: "*"
    effect: deny
  - action: edit
    resource: "**/*.env*"
    effect: deny
  - action: edit
    resource: "**/*.key"
    effect: deny
  - action: edit
    resource: "**/*.secret"
    effect: deny
  - action: edit
    resource: ".git/**"
    effect: deny
  - action: edit
    resource: "gradle.properties"
    effect: deny
---

Implementa exactamente la tarea que te pasa el agente padre. Trabaja en `main`, sin ramas ni commits.

Para explorar usa `glob`, `grep` y `read`; usa `shell` solo para Gradle y para `git status/diff/log`.
No delegues en otros subagentes: si necesitas algo, pídelo en tu informe.

Antes de terminar:

1. Ejecuta `./gradlew --no-daemon testDebugUnitTest`.
2. Si tocas UI, ejecuta también `./gradlew --no-daemon assembleDebug`.
3. Pega el resultado real (o el error) en tu respuesta. No afirmes que pasa si no lo has ejecutado.

Devuelve un resumen breve: archivos tocados, decisiones y verificación.
