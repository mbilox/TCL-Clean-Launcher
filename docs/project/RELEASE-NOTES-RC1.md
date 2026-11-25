# TCL Clean Launcher v3.0-rc1 (BeyondTV2 AR05)

A Home launcher for one TV: a TCL BeyondTV2 (RTD2851, Android TV 11, AR05, 2 GB, 1080p), forked from
[CleanYourTV](https://github.com/Gathin23/cleanyourtv) v3.0 (MIT). This is the first release candidate
of the fork and it is **not published yet** — see the gate at the end.

*Draft release body. Numbers and paths are the ones committed in this tree; the authoritative numbers were measured on the unit itself (heapprofd A/B runs).*

## What's new vs upstream v3.0

- **Own identity.** `ar.TCL.cleanLauncher` (versionCode 9, versionName `3.0`), label
  **"TCL Clean Launcher"** in `en`/`es`/`pt`, signed with the fork's own keystore kept outside the
  repo (`launcher/keystore.properties` is gitignored; without it the release build falls back to the
  debug key, as upstream does). Locales trimmed to `en`/`es`/`pt`, which the build file records as
  ~165 KB of APK and ~160 KB of `resources.arsc`.
- **Modo (Mode) row in launcher settings**, applied from the remote with no PC: **Normal**
  (effects, wallpapers, skins) or **Maximum optimization** (no animations, effects, cinematic, glow
  or wallpaper, names always shown, banners in RGB_565 at 240 dpi). Both presets ship inside the APK
  as `res/raw/tcl_normal.json` / `tcl_max.json` (readable copies in `presets/`), are written over the
  app's `config.json` overlay, and the choice survives a force-close
  (`SharedPreferences` `launcher_settings`, key `mode`).
- **Banners row, independent of the mode**: **Full** / **Memory saver** / **Icons only**
  (`banner_mode` = `full` / `lean` / `icons`). `lean` forces `banner_565=true` +
  `banner_density=240`; `icons` sets `uniform_tiles=true`. It is an absolute choice that always wins over the preset and `config.xml`, and a mode
  change does not reset it (the Máx preset no longer carries banner keys).
- **`banner_565` and `banner_density` in `config.xml`**, both off by default so upstream behaviour is
  unchanged; with both off, `AppAdapter.decodeBanner()` takes the untouched upstream path. Measured
  Native Heap **18,396 KB → 10,340 KB (−8,056 KB, −44%)**, banner rubric ~7,734 KB → ~3,367 KB, total
  PSS flat (no PSS claim). Attribution by heapprofd, method and traps in `FINDINGS.md` §14, A/B in
  (A/B measured on the unit).
- **Launcher settings reachable with the remote:** `launcher_settings_button` defaults to **on** in
  the fork (upstream keeps it off), because the Modo/Banners rows live in launcher settings.
- **The voice pill (`btnVoice`)** so the TCL microphone chain keeps working: the remote's mic button
  is turned by `TclWinInjector` into a synthetic click on the Home's own voice button, which fires
  `Intent.ACTION_ASSIST`. A Home without that view loses the remote's mic on this board
  (`FINDINGS.md` §5, §16).
- **Renamed to "TCL Clean Launcher"** in the shipped locales, so the TV's Home picker no longer shows
  three indistinguishable "Inicio" rows (`FINDINGS.md` §17c).
- **Typeface subset to the `wght` axis in use** (vendored `tools/subset_font.py` from upstream PR
  #3156, every codepoint kept): **13.5 KB back in-APK**, 377,759 B → 364,463 B.
- **Upstream tree untouched** apart from `launcher/`, so `git merge --no-ff upstream/main` stays
  clean; the fork's own material lives in `docs/project/`, `devices/` and `presets/`.

## Single-pass grid layout adapted from upstream PR #446: 24 fewer views at cold start, first frame p95 8-9 ms, Native Heap lower in Max mode (9,828 KB), cold start Normal 745 ms p50 / Max 1,076 ms. Look verified bit-identical on the unit.

Measured on the unit

Same day, same protocol (`tools/bench-launcher.sh`), Home in the foreground
(the fork's internal release budget). Eddy 0.6.0 and upstream v3.0 were
measured on the same TV as reference points.

| Metric | Fork — Max | Fork — Normal | Eddy 0.6.0 | Upstream v3.0 |
| --- | ---: | ---: | ---: | ---: |
| APK | 364,463 B | 364,463 B | 516.9 KB | 366.6 KB |
| Cold start (5 runs) | ~1,200 ms | 829–836 ms | **458–489 ms** | 957–1,037 ms |
| Jank (20 D-pad pairs) | 0.00% (43 frames) | 0.18% (1,662 frames) | 0.00% | 0.00% |
| p50/p90/p95 frames | 7/10/11 ms | 7/9/10 ms | 7/11/12 ms | 6/9/9 ms |
| PSS + SwapPSS | **27.7 MB** | 37.7 MB | **17.9 MB** | 37.5–39.8 MB |
| Idle, 30 s | 0 frames / 0% CPU | 3 frames / 0.8% | 2 frames / 0% | 0 frames |

- The Max-mode PSS budget (≤ 30 MB) is met at 27.7 MB, and Normal-mode jank/p95/idle are inside
  their columns. **Max-mode p95 frames read 11 ms against a 9 ms budget**, so this candidate does not
  clear the whole gate; the grid work that later measured 6/8/9 ms in both modes is a separate build.
- **The 600 ms cold-start budget is met by no launcher of this family on this firmware**, and this
  release does not meet it either: Max is ~1.2 s and Normal 829–836 ms. Eddy's 458–489 ms comes from
  a smaller launcher (no app library, no settings panel). The grid single-pass work adapted from
  upstream PR #446 later measured Normal 745 ms / Max 1,076 ms on a separate build
  the internal release budget; it is not part of this candidate.
- Native Heap is reported, not gated: `banner_565` + `banner_density:240` plus the lean keys take it
  to ~10.3 MB. PSS did not move with the banner prune, and no PSS claim is made from it.

## Install

```sh
adb install -r app-release.apk
adb shell cmd package compile -m speed ar.TCL.cleanLauncher   # AOT immediately, no JIT wait
```

Install with the TV idle: installing a 27-locale APK on 2 GB with the TV in use once killed the
`system_server` during `dexopt` (it recovered by itself; `FINDINGS.md` §9). After **installing or
updating the package that owns Home**, reboot once before judging the voice button — the mic chain
stays broken until then (`FINDINGS.md` §6).

**To make it the boot Home** (the only no-root path found on AR05, `FINDINGS.md` §17d):

1. `adb shell pm disable-user --user 0 com.tcl.overseasappshow`
   Until this is off, its `BootReceiver` re-applies the vendor's Home at every boot and any choice
   is lost — `set-home-activity` and the TV's own picker both lost across four reboots (§17c).
2. Open the TV's Home picker and choose **"TCL Clean Launcher"**:
   `adb shell am start -a android.settings.HOME_SETTINGS`, then the remote.
   In this unit's picker the rows are: 1 = this fork, 2 = Eddy, 3 = upstream `com.gathin.tvhome`,
   4 = `KeyCustomFunctionService` (§17b). Confirm with
   `adb shell dumpsys activity activities | grep mResumedActivity`: the *activity namespace* in
   `resolve-activity` is `com.gathin.tvhome` for every build of this family, so it cannot be used to
   tell them apart.
3. **Two full reboots.** Then Home comes up on
   `ar.TCL.cleanLauncher/com.gathin.tvhome.MainActivity` (2/2 reboots, §17d).

Keep `com.tcl.guard` enabled through all of this (see below), and re-check the debloat after every
reboot: `TV_HOST=<tv ip> tools/check.py devices/telemetry-7.txt ar.TCL.cleanLauncher`.

## Requirements & caveats

- **`com.tcl.guard` must stay enabled.** It owns the Bluetooth remote's `virtual-search` input
  channel; with it disabled the mic button reaches `katniss` with no audio stream and the
  `AudioRecord` fallback never runs, which no reboot repairs (`FINDINGS.md` §16). This is why the fork
  carries an exception list instead of a general telemetry prune.
- **`com.tcl.tv` must stay enabled**: its `GlobalKeyService` answers the remote's short power press
  (`devices/README.md`).
- **Cast is untested on this unit** (upstream #453 is the record of Chromecast breakage after large
  debloats). `overseasappshow` was disabled for the boot-Home fix with that risk acknowledged, and the
  cast test is still on the owner's list before publication.
- **Accessibility services are off by default** here (`enabled_accessibility_services` is `null`), so
  `HomeKeyService`/`InputKeyService` are not bound: `app_keys`, `settings_key_keycodes` and the other
  remote-key remaps are inactive until the owner binds them (`FINDINGS.md` §17d, IDEAS #2). The mic is
  unaffected — that chain does not use accessibility (§16).
- **Android TV 11 / API 30 on AR05 only.** `minSdk` 26, `targetSdk` 34, 32-bit `armeabi-v7a`
  userspace. Nothing here was tested on another TV, and the vendor-allowlist Home behaviour is
  firmware-specific.
- The TV's address is DHCP and changes between sessions; no script takes a fixed address
  (`FINDINGS.md` §13).

## Build

| | |
| --- | --- |
| APK | 376,259 B |
| SHA-256 | `d4ff3885f29dba6847e9c10495c9be3b9397fdf5734ec279cb46402831b1f367` |

This is the candidate installed on the unit on 2026-11-24 19:39 (home verified after install). It
carries the gate-day fixes: the watch row reveals fully in both directions with column-preserving
navigation, the row's captions never fade, the sideways row's poster repaint no longer depends on
holder recycling, and the dev-only probe is unexported. The internal plan and budget still record the previous frozen candidate (364,463 B / `605fc14f…`); both are
refreshed when the release is recorded. Verify the APK, not the build: `aapt2 dump badging` for
package and locales, `apksigner verify --print-certs` for the certificate.

## Known behaviour that is not a launcher defect

- **First mic press after a long idle can fail silently.** The voice chain is firmware
  (`com.tcl.guard` → `com.tcl.assistant` → katniss); the launcher ships the `btnVoice` pill so the
  chain works, but Android parks those processes on idle and the first press can reach katniss
  before `com.tcl.assistant` is up again (measured: 12 s late on a 7-day-old boot under load 20) —
  the intent then arrives without an audio stream and katniss rejects it. The second press works;
  the owner confirmed normal tap-to-talk afterwards. A reboot clears it; keeping those processes
  alive would cost ~200 MB of RAM on a 2 GB board.
- The sleep path of this firmware shows a TCL "switching to standby" dialog instead of the dream
  when `sleep_timeout` expires; the dream (Google's backdrop) starts on the power/sleep path
  (`screensaver_activate_on_sleep=1`).

## Not yet published

Publication is **gated on the owner's own tests**: cast with the owner's phone (upstream #453) and
anything else the owner wants before it goes out. Already verified by the owner on 2026-11-24: the
physical remote's mic on this build chain (with the cold-start race documented above) and the
launcher options over ADB. The decision to publish is the owner's, explicitly (PLAN §10 status,
the internal release budget). Until then this document is a draft and no release exists.
