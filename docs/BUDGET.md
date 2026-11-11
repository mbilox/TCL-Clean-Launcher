# Release budget

The performance gate for the TCL Clean Launcher fork on the real unit (TCL BeyondTV2, RTD2851,
AR05, Android TV 11, 2 GB — profile in [`devices/README.md`](../devices/README.md)). A release ships
only with the table below measured on the unit and recorded with its APK size and SHA-256. If a
number does not fit, the build does not ship; there is no averaging it away.

## The budget

| Metric | Max optimisation mode | Normal mode | How it is read |
| --- | ---: | ---: | --- |
| Cold start, p50 | **≤ 600 ms** | ≤ 900 ms | `Displayed … +NNNms` from logcat |
| Jank (40 keys) | **0.00%** | ≤ 0.5% | `dumpsys gfxinfo`, 20 DOWN/UP pairs |
| p95 frame | **≤ 9 ms** | ≤ 12 ms | `dumpsys gfxinfo` |
| PSS + SwapPSS | **≤ 30 MB** | ≤ 45 MB | `dumpsys meminfo`, foreground steady |
| Idle, 30 s | **≤ 1 frame** | ≤ 3 frames | `dumpsys gfxinfo` |
| APK (release) | **≤ 540 KB** | ≤ 540 KB | size on disk |
| Native Heap (reported, not gated) | ~10.3 MB with `banner_565` + `banner_density:240` and the lean keys | same | `dumpsys meminfo`; heapprofd for attribution |

The budget comes from `docs/project/PLAN.md` §4. Native Heap is not a gate: the banner prune took it
from 18,396 KB to 10,340 KB steady and PSS did not move, so it is recorded here so a release cannot
regress it silently (`docs/project/AUDIT.md` §5). "Max" mode does not exist in the app yet (F3); until
it does, the Max column is the target for the lean preset and the Normal column for the full look.

## Measurement protocol

1. **Reboot** the TV, wait for `sys.boot_completed=1`, then let it settle about 3 minutes (installer
   dexopt and the post-boot scan otherwise inflate the first run).
2. **Judge idle by CPU, never by `loadavg`.** About a dozen vendor kernel threads sit in `D` state
   permanently and hold load average at 19–31 with the CPU 92% idle (`docs/project/FINDINGS.md` §2).
3. **Cold start:** `tools/bench-launcher.sh <pkg> <component> <label>`, five runs. When the package
   under test owns Home, `am start -W` reports `UNKNOWN` and a 7–20 ms `WaitTime` that measures
   nothing; the honest number is the system's `Displayed <pkg>/<activity>: +NNNms` line
   (`FINDINGS.md` §3).
4. **Jank and frame times:** the same script, `dumpsys gfxinfo reset`, 20 DOWN/UP pairs at 0.7 s,
   read 3 s later. A smaller sample makes the jank percentage meaningless.
5. **PSS + SwapPSS, foreground steady:** read `dumpsys meminfo` with the launcher in the foreground
   after the settle step, and compare two builds in the same state. PSS alone lies: the same process
   read earlier showed 24,000 KB PSS / 1 KB swap and later 15,540 KB / 8,357 KB, because pages had
   moved to swap (`FINDINGS.md` §4). A foreground reading is not comparable with a background one.
6. **Native Heap:** `dumpsys meminfo` for the number; heapprofd for the attribution, on a
   `debuggable=true` build that is reverted afterwards. Method and its traps are in `FINDINGS.md` §14;
   the runner is `tools/native-heap-audit.sh`, the A/B runner is `tools/banner-ab.sh` (not committed in
   this tree).
7. **Compare against the table.** Compare each mode against its own column, then record the result
   with the release.

The TV's address is DHCP and changes between sessions (`FINDINGS.md` §13); scripts take it from the
environment (`TV_HOST`, `SERIAL`) and no address is stored in a result.

## Evidence per build

| Build | APK | SHA-256 | Cold start | Jank | p50/p90/p95 | PSS+swap | Idle 30 s | Native Heap | Evidence |
| --- | ---: | --- | ---: | ---: | --- | ---: | ---: | ---: | --- |
| Eddy 0.6.0 (`br.eddy.tvhome`, reference Home) | 516.9 KB | — | 405 ms | 0.00% | 6/8/9 ms | 23.3 MB | 0 frames | — | `docs/project/FINDINGS.md` §12 |
| Upstream v3.0 lean | 532.2 KB | — | ~850 ms | 0.00% | 6/9/9 ms | 37.5–39.8 MB | 0 frames | 17,612 KB | `FINDINGS.md` §12, `AUDIT.md` §5 |
| Fork `ar.TCL.cleanLauncher`, versionCode 9 | 373,881 B | `4aae86c2453a5a3b297c5ef2ab4542de2db78b0dbf34715068d59d17f40fcbc5` | not measured | — | — | — | — | — | `docs/project/LOG.md` (2026-10-26), `NEXT-SESSION.md` |
| Fork banner-lean A/B (debuggable measurement build) | — | — | — | — | — | 43,217 / 44,163 KB | — | 18,396 → 10,340 KB | `docs/project/AUDIT.md` §5, `LOG.md` (2026-11-11) |

The 373,881 B APK is inside the size gate; it was never installed on the unit, so it has no runtime
row yet. The banner A/B numbers are from a `debuggable=true` build and are not release numbers. A
second build size, 374,269 B, appears in the task notes but has no committed build log or SHA-256 in
this tree: add its row only when the log lands.

## Recording a release

1. Build, then verify the APK, not the build: `aapt2 dump badging` (package, locales) and
   `apksigner verify --print-certs`. A green `assembleRelease` proves nothing.
2. Check the size: `stat -c %s` (Linux) or `stat -f %z` (macOS) ≤ 540 KB.
3. Run the protocol above on the unit for the mode(s) the release ships. Install with the TV idle,
   `adb install -r`, then reboot before measuring or judging the voice button (`FINDINGS.md` §6, §9).
4. Add a row here with the size, the SHA-256 (`sha256sum`), the measured numbers and the evidence
   paths. One row per published build; no row, no release.

## Comparativa triple del primer release (2026-11-11, mismo día y protocolo)

Todos medidos con `tools/bench-launcher.sh` en esta unidad (BeyondTV2 AR05), mismos minutos,
fork = release candidate 1 (APK 364.219 B, SHA `55030ab8...`, modo Normal/Máx según la fila).
Crudo: `bench-results/bench-*-{fork-release-normal,fork-release-max2,compare-eddy,compare-upstream-v30}-20261111-*`.

| Métrica | Fork — Máx | Fork — Normal | Eddy 0.6.0 | Upstream v3.0 |
|---|---:|---:|---:|---:|
| APK | 364.219 B | 364.219 B | 516,9 KB | 366,6 KB |
| Cold start (5 corridas) | ~1.200 ms¹ | 829-836 ms | **458-489 ms** | 957-1.037 ms |
| Jank (20 pares D-pad) | 0,00% (43 frames) | **0,18% (1.662 frames)** | 0,00% (42 frames) | 0,00%² |
| p50/p90/p95 frames | 7/10/11 ms | 7/9/10 ms | 7/11/12 ms | 6/9/9 ms² |
| PSS + swap | **27,7 MB** ✓ | 37,7 MB | **17,9 MB** | 37,5-39,8 MB² |
| Idle 30 s | 0 frames / 0% CPU | 3 frames / 0,8% | 2 frames / 0% | 0 frames² |
| Presupuesto PSS ≤ 30 MB (Máx) | **cumple** | n/a | — | — |
| Presupuesto cold ≤ 600 ms | **no cumple** (~1,2 s) | no | cumple | no |

¹ El presupuesto de cold ≤ 600 ms no lo cumple ningún launcher de la familia gathin en esta
  firmware (upstream 957+ ms; el bench del 26/10 dio ~850 ms). El de Eddy es más chico por código
  (v0.6.0 sin la biblioteca de apps ni este panel de Ajustes).
² PSS/jank/idle del upstream de la línea base 2026-10-26 (mismo build v3.0, mismo protocolo); en
  el bench de hoy el proceso perdió el Home a mitad de corrida — TCL re-afirma su Home preferido
  (Eddy) cuando el proceso del Home activo muere, incluso con `overseasappshow` deshabilitado.

**Lectura honesta**: Eddy gana en liviandad y arranque (es un launcher más chico, sin la biblioteca
de apps ni este panel). El fork entrega el paquete completo (Modo/Banners desde el control,
banners podados −8 MB Native, mic verificado, boot-Home resuelto) a un costo de PSS que en Máx
queda bajo el presupuesto. El upstream sin podar es el más pesado en Native y arranca más lento
que Eddy y que el fork en Normal.
