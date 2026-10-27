# TCL Clean Launcher

Launcher de Home para **TCL BeyondTV2 (RTD2851, Android TV 11 / AR05)** derivado de
[CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT), con dos modos de uso pensados para una
TV de 2 GB y un control remoto, y con un presupuesto de rendimiento medido en la unidad real.

| | |
|---|---|
| Base | `Gathin23/cleanyourtv` v3.0 (launcher Java, MIT) |
| Target | TCL BeyondTV2 `BeyondTV2` / `BeyondTV`, board `rtd285o`, firmware `V8-R851T02-LF1V660` (AR05) |
| applicationId | `ar.TCL.cleanLauncher` |
| Modos | **Normal** (look completo: efectos, wallpapers, skins) y **Máxima optimización** |
| Estado | F1 cerrado en local (repo + identidad + firma). Próximo: F5 paso 1 o F3 |

## Qué agrega este proyecto

1. **Dos modos elegibles desde el control remoto** (sin PC): presets empaquetados en el APK, aplicados
   desde Ajustes. Hoy en el upstream los temas solo se aplican por `adb`.
2. **Perfil de device propio**: lista de debloat verificada, benchmark reproducible y las trampas de
   método de esta placa documentadas.
3. **Presupuesto de rendimiento bloqueante** por modo: si un cambio lo rompe, no se publica.

## Documentación

| Documento | Contenido |
|---|---|
| [`PLAN.md`](PLAN.md) | Plan maestro: arquitectura de los modos, fases, tareas, presupuesto, decisiones |
| [`AUDIT.md`](AUDIT.md) | Auditoría de peso del APK y del runtime, con lo que se puede podar y lo que no |
| [`FINDINGS.md`](FINDINGS.md) | Todo lo aprendido de esta TV: firmware, método de medición, micrófono, trampas |
| [`UPSTREAM.md`](UPSTREAM.md) | Relación con el upstream y con el fork de Eddy: qué se publicó y qué es aportable |
| [`LOG.md`](LOG.md) | Bitácora cronológica de decisiones y evidencia (manda sobre el resto si hay contradicción) |
| [`TASKS.md`](TASKS.md) | Checklist de trabajo con estado |
| [`APP-MAP.md`](APP-MAP.md) | Mapa de paquetes de la unidad: qué hace cada uno y si es seguro apagarlo |
| [`NEXT-SESSION.md`](NEXT-SESSION.md) | Prompt de arranque para retomar el proyecto en otra sesión |

## Layout del repositorio

Este repo es un **fork completo** del upstream: el árbol de arriba (`launcher/`, `examples/`,
`tools/`, `docs/`, `themes/`, `addons/`) es upstream y se mantiene sin editar para que
`git merge --no-ff upstream/main` siga siendo limpio. Lo propio del proyecto vive en:

| Ruta | Qué es |
|---|---|
| `launcher/` | El launcher (único lugar donde tocamos código de la app) |
| `docs/project/` | Documentación de este fork (este índice) |
| `devices/tcl-beyondtv2-ar05/` | Evidencia de la unidad real: debloat, backups, reportes |
| `presets/` | Presets de los modos Normal / Máxima optimización |

Comandos de build (toolchain local, no entra al repo):

```bash
cd launcher
export JAVA_HOME=C:/repositorios/TCL-Tv/tools/jdk/jdk-17.0.20.1+1
# local.properties: sdk.dir=C:/repositorios/TCL-Tv/tools/sdk
./gradlew assembleRelease
```

La firma se toma de `launcher/keystore.properties` si existe; el keystore vive en
`C:/repositorios/TCL-Tv/keys/`, fuera de este repo.

## Licencia

El launcher hereda la licencia del upstream (MIT, ver [`LICENSE`](../../LICENSE)).
Los documentos de este repositorio son del proyecto.
