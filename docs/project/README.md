# TCL Clean Launcher

A Home launcher for **one unit**: a TCL BeyondTV2 (RTD2851, Android TV 11 / AR05, 2 GB, 1080p),
derived from [CleanYourTV](https://github.com/Gathin23/cleanyourtv) v3.0 (MIT). Two selectable modes,
fork-only memory switches, the vendor voice chain kept working, and a performance budget measured on
the real TV.

| | |
|---|---|
| Base | `Gathin23/cleanyourtv` v3.0 (Java launcher, MIT) |
| Target | TCL BeyondTV2 `BeyondTV2` / `BeyondTV`, board `rtd285o`, firmware `V8-R851T02-LF1V660` (AR05) |
| Package | `ar.TCL.cleanLauncher`, versionCode 9, versionName `3.0`, label **TCL Clean Launcher** |
| Locales | `en`, `es`, `pt` (`resourceConfigurations` in `launcher/app/build.gradle`) |
| Signing | own keystore, kept outside this repo; `launcher/keystore.properties` is gitignored |
| Modes | **Normal** (full look: effects, wallpapers, skins) and **Maximum optimization** |
| Status | Release candidate 1 frozen and measured; publication gated on the owner's own tests |

## What this fork adds over upstream

1. **Modo (Mode) row in launcher settings, operated with the remote.** Two presets ship inside the
   APK — `res/raw/tcl_normal.json` and `tcl_max.json`, readable copies in [`presets/`](../../presets/) —
   and the row writes the chosen one over the app's `config.json` overlay, stores the choice in
   `SharedPreferences` (`launcher_settings`, key `mode`) and recreates Home. Upstream applies themes
   only over `adb`. See [`presets/README.md`](../../presets/README.md).
2. **Banners row, independent of the mode.** Full / Memory saver / Icons only → `banner_mode` =
   `full` / `lean` / `icons` in the same `SharedPreferences`. `lean` forces `banner_565=true` plus
   `banner_density=240` over whatever the theme says; `icons` sets `uniform_tiles=true`. When
   `banner_mode` is not `full` it wins over the preset, and changing the mode does not reset it.
3. **`banner_565` and `banner_density` in `config.xml`**, both **off by default** so upstream
   behaviour is unchanged. `banner_565` decodes an app banner in RGB_565 instead of ARGB_8888;
   `banner_density` caps the decode density (`0` keeps the upstream route). With both keys off,
   `AppAdapter.decodeBanner()` takes the untouched upstream path. Measured on this unit with
   heapprofd: **Native Heap 18,396 KB → 10,340 KB (−8,056 KB, −44%)**, banner rubric
   ~7,734 KB → ~3,367 KB, **total PSS flat — no PSS claim**. Attribution and A/B:
   [`AUDIT.md`](AUDIT.md) §5, method and traps in [`FINDINGS.md`](FINDINGS.md) §14.
4. **The voice pill (`btnVoice`)**, ported from the Eddy build: `MainActivity` keeps the `btnVoice`
   view and fires `Intent.ACTION_ASSIST`. The TCL remote's mic button never reaches
   `VoiceInteractionService`: `TclWinInjector` performs a synthetic click on **the Home's own** voice
   button. A Home without one loses the remote's microphone on this board
   ([`FINDINGS.md`](FINDINGS.md) §5, §16).
5. **Renamed to "TCL Clean Launcher"** in all three shipped locales. The Home picker used to show
   three rows all labelled "Inicio"; the label is what makes the choice unambiguous
   ([`FINDINGS.md`](FINDINGS.md) §17c).
6. **Typeface subset to the `wght` axis the launcher uses** (vendored `tools/subset_font.py` from
   upstream PR #3156, every codepoint kept): **13.5 KB back in-APK**, 377,759 B → 364,219 B.
7. **Locales trimmed to `en`/`es`/`pt`**, which the build file records as ~165 KB of APK and ~160 KB
   of `resources.arsc`.

## Boot-Home status, stated plainly

The fork is the unit's **boot Home only while `com.tcl.overseasappshow` is disabled**. With it
enabled, its `BootReceiver` re-applies the vendor's own Home on every boot: `set-home-activity` over
`adb` and the TV's Home picker both lost to it across four reboots
([`FINDINGS.md`](FINDINGS.md) §17c). With `overseasappshow` disabled, Home picked in the TV's own
picker ("TCL Clean Launcher") and **two full reboots**, the fork came up in front 2/2
([`FINDINGS.md`](FINDINGS.md) §17d). That is the only no-root path found on this firmware.

## The debloat exception list

`devices/telemetry-7.txt` is the list of record: 6 telemetry packages plus `com.tcl.overseasappshow`.
**`com.tcl.guard` stays enabled**: TGuard owns the remote's `virtual-search` input channel, and with it
disabled the mic button reaches `katniss` without an audio stream and the `AudioRecord` fallback never
runs — a dead mic that no reboot repairs ([`FINDINGS.md`](FINDINGS.md) §16). `com.tcl.tv` also stays
enabled: its `GlobalKeyService` answers the remote's short power press.
[`devices/README.md`](../../devices/README.md) has the profile, the evidence index and the keycodes.

## Performance

[`docs/BUDGET.md`](../BUDGET.md) is the gate: a table per metric and mode, the measurement protocol,
and the three-way comparison for this release candidate (fork Normal/Max vs Eddy 0.6.0 vs upstream
v3.0), each with the APK size and SHA-256 of the build it was measured on. The short version: the
PSS budget (Max ≤ 30 MB) is met at 27.7 MB, Normal mode sits inside every column, Max mode reads
p95 11 ms against a 9 ms budget, and the 600 ms cold-start budget is met by no launcher of this
family on this firmware.

## Documentation

| Document | Contents |
|---|---|
| [`PLAN.md`](PLAN.md) | Master plan: mode architecture, phases, tasks, budget, decisions, lessons |
| [`AUDIT.md`](AUDIT.md) | APK and runtime weight audit, with what can be pruned and what cannot |
| [`FINDINGS.md`](FINDINGS.md) | Everything learned about this TV: firmware, measurement method, mic, boot Home, traps |
| [`UPSTREAM.md`](UPSTREAM.md) | Relation to upstream and to Eddy's fork: what was published, what is contributable |
| [`LOG.md`](LOG.md) | Chronological log of decisions and evidence (authoritative if anything contradicts) |
| [`TASKS.md`](TASKS.md) | Work checklist with state |
| [`APP-MAP.md`](APP-MAP.md) | Package map of the unit: what each one does and whether it is safe to switch off |
| [`NEXT-SESSION.md`](NEXT-SESSION.md) | Startup prompt to resume the project in another session |
| [`RELEASE-NOTES-RC1.md`](RELEASE-NOTES-RC1.md) | Draft release body for v3.0-rc1 |
| [`../BUDGET.md`](../BUDGET.md) | Release budget, measurement protocol and the numbers per build |

## Repository layout

This repo is a **full fork** of upstream: the tree above (`launcher/`, `examples/`, `tools/`,
`docs/`, `themes/`, `addons/`) is upstream and is kept unedited so `git merge --no-ff upstream/main`
stays clean. The project's own material lives in:

| Path | What it is |
|---|---|
| `launcher/` | The launcher (the only place where app code is touched) |
| `docs/project/` | This fork's documentation (this index) |
| `devices/` | Real-unit evidence: debloat list, backups, reports, keycodes |
| `presets/` | Normal / Maximum optimization presets |

Build (local toolchain, not in the repo):

```bash
cd launcher
export JAVA_HOME=C:/repositorios/TCL-Tv/tools/jdk/jdk-17.0.20.1+1
# local.properties: sdk.dir=C:/repositorios/TCL-Tv/tools/sdk
./gradlew assembleRelease
```

Signing comes from `launcher/keystore.properties` when it exists; the keystore lives outside this
repo. Without it the release build falls back to the debug key, like upstream.

## Licence

The launcher inherits the upstream licence (MIT, see [`LICENSE`](../../LICENSE)).
The documents in this repository belong to the project.
