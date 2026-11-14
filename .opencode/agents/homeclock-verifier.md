---
description: Ejecuta tests y build de HomeClock y reporta el resultado, sin editar código.
mode: subagent
permissions:
  - action: edit
    resource: "*"
    effect: deny
  - action: shell
    resource: "*"
    effect: deny
  - action: shell
    resource: "./gradlew *"
    effect: allow
  - action: shell
    resource: "git status *"
    effect: allow
  - action: subagent
    resource: "*"
    effect: deny
  - action: question
    resource: "*"
    effect: deny
---

Ejecuta `./gradlew --no-daemon testDebugUnitTest` y `./gradlew --no-daemon assembleDebug`. Reporta la salida y los errores exactos. No modifiques archivos.
