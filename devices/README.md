# TCL BeyondTV2 (RTD2851, AR05) — device profile

One real TCL BeyondTV2 running the TCL Clean Launcher fork: Android TV 11, 2 GB of RAM, and the
telemetry debloat of record applied on 2026-10-26. It is written to be publishable as-is, so every
claim points at a committed evidence file.

The raw `baseline/` captures named by `docs/project/AUDIT.md` are **not** in this tree. Where a
number comes from one, this profile cites the committed summary (`docs/project/AUDIT.md`,
`docs/project/LOG.md`) and says the raw capture is not here.

## Identity

| Field | Value |
| --- | --- |
| Model / product | `BeyondTV2` |
| Device | `BeyondTV` |
| Board | `RTD2851` (`ro.board.platform` `rtd285o`, `ro.hardware` `rtd285o`) |
| Brand | TCL |
| Firmware | V8-R851T02-LF1V660 (AR05) |
| Build | `PPR1.180610.011`, incremental `AR05`, built 2022-05-26 (`user`, `release-keys`) |
| Android | 11 / API 30, security patch 2021-12-05 |
| Fingerprint | `TCL/BeyondTV2/BeyondTV:11/PPR1.180610.011/AR05:user/release-keys` |
| CPU ABI | `armeabi-v7a`, `armeabi` (32-bit userspace only) |
| RAM | 2 GB |
| Remote | `TCL_RC802VEU2` (Bluetooth) |

Source: `evidence/getprop-pre.txt` (a curated `getprop`); the firmware name, RAM and remote are from
the header of `docs/project/FINDINGS.md`. The serial, `android_id`, `boot_id`, MAC addresses and the
DHCP address of the unit are redacted everywhere: the address is `<TV_IP>` and the raw `getprop`
stays outside the repo.

## Debloat applied

Method: `pm disable-user --user 0 <package>` only. No uninstall, no root, no `pm clear`, no
settings changes, no package re-enabled, **no reboot during the run**.

Applied 2026-10-26 04:06:58–04:07:14 UTC in two batches (5 then 2), from
`evidence/batch1-disable.txt` and `evidence/batch2-disable.txt`. The disabled set went from 21 to
28 packages (`backup-packages-2026-10-26.txt` → `evidence/pm-disabled-post.txt`).

| # | Package | What it is | Pre-state evidence |
| --- | --- | --- | --- |
| 1 | `com.tcl.bi` | TCL analytics / telemetry (`BIService`, `ParamProvider`) | `evidence/dumpsys-com.tcl.bi-pre.txt` |
| 2 | `com.tcl.tv.tclhome_passive` | TCL content pushed onto Home at boot | `evidence/dumpsys-com.tcl.tv.tclhome_passive-pre.txt` |
| 3 | `com.tcl.waterfall.overseas` | TCL web content feed / recommendation waterfall | `evidence/dumpsys-com.tcl.waterfall.overseas-pre.txt` |
| 4 | `com.tcl.messagebox` | Pushed messages and promos | `evidence/dumpsys-com.tcl.messagebox-pre.txt` |
| 5 | `com.google.android.partnersetup` | Partner attribution / installs | `evidence/dumpsys-com.google.android.partnersetup-pre.txt` |
| 6 | `com.tcl.guard` | TGuard: device-use tracking, "health info", memory cleaner | `evidence/dumpsys-com.tcl.guard-pre.txt` |
| 7 | `com.google.android.feedback` | "Send feedback" diagnostics upload | `evidence/dumpsys-com.google.android.feedback-pre.txt` |

Each package was present and enabled (`enabled=0`, i.e. DEFAULT) before its `disable-user`, which
answered `new state: disabled-user`. After each batch the crash buffer was empty and Home still
resolved to `br.eddy.tvhome` / `com.gathin.tvhome.MainActivity`
(`evidence/batch1-postcheck.txt`, `evidence/batch2-postcheck.txt`).

**Correction to the earlier handoff.** `docs/project/FINDINGS.md` §1 says that only `com.tcl.bi` and
`com.tcl.tv.tclhome_passive` were debloated. At the start of this run both were **enabled**: they
appear in the enabled set (`backup-packages-2026-10-26-enabled.txt`), not in the 21-package disabled
set, and their dumps print `enabled=0` (`evidence/dumpsys-com.tcl.bi-pre.txt:125`,
`evidence/dumpsys-com.tcl.tv.tclhome_passive-pre.txt:116`). The 7 packages above are the debloat of
record; the `FINDINGS.md` §1 sentence describes an earlier attempt that was no longer in effect.

**Verified at apply time, not across a reboot.** Nothing was rebooted in this run, so persistence of
the 7 is still unverified (see below). The 21 packages that were already disabled at the factory
(`uk.co.freeview.*` × 15 and others) were left untouched and are not part of this debloat.

What was deliberately left on: `com.google.android.gms` still carries the advertising identifier on
this Android 11 build (there is no separate AdServices package here: both plausible names answer
`Unknown package`, `evidence/adservices-attempt.txt`), and the OTA app stays enabled. Streaming
apps, the voice stack, Cast and the TCL live-TV/HDMI/remote packages were not touched
(`evidence/protected-check.txt`).

## The debloat comes back after a reboot (drift)

`docs/project/FINDINGS.md` §11 records the finding on this unit:

1. The pair `com.tcl.bi` + `com.tcl.tv.tclhome_passive` was disabled and verified on 2026-10-25
   20:35, with Home, Cast and the inputs still working.
2. After a reboot at 00:13, at 01:06 **both were enabled again** (`enabled=0` = DEFAULT = enabled).
3. The next run found the same pair enabled and disabled them as part of the 7 above.

Committed evidence for the reverted state: both are in the enabled list, both dumps print `enabled=0`,
and neither appears in the 21-package disabled list (paths above). The DEBLOAT-REPORT is dated
2026-10-26 and labels its header "unit local 01:07 -03" while its batch timestamps are UTC; the
FINDINGS timestamps do not say which clock they use, so treat 01:06 and 04:06 UTC as possibly the
same check labelled differently rather than as two separate events.

Suspected re-enabler: `com.tcl.overseasappshow`, whose `BootReceiver` runs on `BOOT_COMPLETED`,
`STR_RESUME_COMPLETED` and `com.tcl.countrychange` and is documented upstream as re-enabling its
family at every boot (`examples/tcl-beyondtv2-android-11/debloat.txt`, header; DEBLOAT-REPORT §5).
It is still **enabled** on this unit: disabling it is stage 3, deferred by the owner until the fork
runs as a stable Home, because it also carries Cast risk (upstream #453). `com.tcl.overseasappshow`
has **not** been proven to be the cause here; a competing explanation (`appstatecontroller`) is
recorded in `docs/project/IDEAS.md`. The practical rule is the one that matters: verify and re-apply
after every reboot.

## Do not disable `com.tcl.tv`

> **`com.tcl.tv` is load-bearing: disabling it kills the remote's short power press.** On this TCL
> firmware `TclPhoneWindowManager` dispatches `KEYCODE_POWER` over IPC to
> `com.tcl.tv.service.GlobalKeyService`; with `com.tcl.tv` disabled the short press does nothing and
> only the long press opens Android's shutdown dialog. Reported as upstream issue #1608 and fixed
> there by the docs PR #1828, shipped in v3.1.0. The upstream profile
> `examples/tcl-beyondtv-rt2851/debloat.txt` therefore lists `com.tcl.tv` under "DO NOT DISABLE", and
> `docs/project/IDEAS.md` records the same warning for this fork.

Also keep on this unit: `com.tcl.tvinput` (tuner/HDMI), `com.tcl.keycustomfunctionservice` (remote
keys and fallback Home), `com.tcl.partnercustomizer` (carries the remote's TKey activity), the voice
stack, Cast, and the settings/OTA apps. `evidence/protected-check.txt` is the 17-package check that
nothing in that set changed.

## Benchmarks

Launcher comparison on this unit, from `docs/project/FINDINGS.md` §12 (raw captures
`baseline/F4-benchmark-eddy-at11.md` and `baseline/F5-benchmark-upstream-v3-at11.md` are not in this
tree):

| | Stock factory Home | Eddy 0.6.0 | Our build¹ | Upstream v3.0 lean |
| --- | ---: | ---: | ---: | ---: |
| Jank (40 keys) | 14.38% | 0.00% | **0.00%** | 0.00% |
| p50 / p90 / p95 | 9 / 23 / 36 ms | 7 / 9 / 12 ms | **6 / 8 / 9 ms** | 6 / 9 / 9 ms |
| Cold start | — | 471–528 ms | **405 ms** | ~850 ms |
| PSS + SwapPSS | ~78 MB | ~29 MB | **23.3 MB** | 37.5–39.8 MB |
| Idle, 30 s | ~3.8 fps | 2 frames | **0 frames** | 0 frames |
| APK | — | 516.9 KB | **516.9 KB** | 532.2 KB |

¹ "Our build" there is the Eddy 0.6.0 code plus two local edits, not the fork APK. The fork release
build (`ar.TCL.cleanLauncher`, versionCode 9, 373,881 B) has **not been installed or measured** on
the unit yet (`docs/project/NEXT-SESSION.md`), so it has no runtime row. Its size is well inside the
APK budget in `docs/BUDGET.md`.

Native Heap attribution and the banner prune, from `docs/project/AUDIT.md` §5 and
`docs/project/LOG.md` (2026-11-11; heapprofd, 95 s sessions, 16 KB sampling; raw captures
`baseline/banner-prune/` are not in this tree):

| Rubric | A — upstream defaults | B — `banner_565:true`, `banner_density:240` (+ lean keys) | Delta |
| --- | ---: | ---: | ---: |
| Native Heap, steady | 18,396 KB | **10,340 KB** | **−8,056 KB (−44%)** |
| Banner (`loadBanner` → `allocateHeapBitmap`) | ~7,734 KB | ~3,367 KB | ~−56% |
| Ambient (`setAmbient` / `showHero`) | ~675 KB | not re-measured | no data |
| PSS total | 43,217 KB | 44,163 KB | ≈0 — **no PSS claim** |

The ~11 MB of unaccounted Native Heap was this banner pipeline (`AppAdapter.loadBanner` ≈ 10.4 MB
live), not the wallpaper, the font or AndroidX. `banner_565` and `banner_density` default to off, so
upstream behaviour is unchanged. The PSS stayed flat because Code/System dominate it; the measured
win is Native Heap, not total PSS.

## Verify and re-apply after a reboot

The repo already ships the verify → re-apply tool: `tools/check.py` (upstream, merged in PR #794).
It reads a debloat list, compares it with the TV and exits **1** on drift, **0** in sync, **2** when
the list or the TV cannot be read.

There is no committed list file for this set yet, so the seven names below are the list.

```sh
# write the seven names to a local list (comments and `# notes` are allowed)
cat > /tmp/tcl-ar05-debloat.txt <<'EOF'
com.tcl.bi
com.tcl.tv.tclhome_passive
com.tcl.waterfall.overseas
com.tcl.messagebox
com.google.android.partnersetup
com.tcl.guard
com.google.android.feedback
EOF

TV_HOST=<tv ip> tools/check.py /tmp/tcl-ar05-debloat.txt br.eddy.tvhome        # report
TV_HOST=<tv ip> tools/check.py --fix /tmp/tcl-ar05-debloat.txt br.eddy.tvhome  # re-disable drift
```

What `check.py` covers: for every listed package it reads the TV's **user 0** enabled/disabled state
(`pm list packages --user 0` / `pm list packages -d --user 0`), reports the packages a firmware
update or boot turned back on, and reports Home when it drifted onto a listed package or away from
the launcher you name. `--fix` re-runs the same safety checks as `tools/disable.sh` (setupwraith
owning the saved Wi-Fi network, packages that drive the HDMI/AV inputs, handlers that open Settings),
re-applies `pm disable-user --user 0` to what came back, and reads the TV back to confirm it.
It never uninstalls anything.

Pass the package that owns Home as the second argument, always. With two arguments omitted,
`check.py` expects this repo's `com.gathin.tvhome` (installed but inactive on this unit), so it would
report Home drift and `--fix` would hand Home to it. Today that package is `br.eddy.tvhome`; after
the fork is installed it is `ar.TCL.cleanLauncher`.

**Gap:** `check.py` has no `--restore`/rollback flag, and its Home check only knows the package you
pass. Rollback stays a separate, deliberate step (below).

## Rollback

Re-enable exactly the 7 packages, nothing else. No uninstall is needed or wanted:

```sh
for p in com.tcl.bi com.tcl.tv.tclhome_passive com.tcl.waterfall.overseas com.tcl.messagebox \
         com.google.android.partnersetup com.tcl.guard com.google.android.feedback; do
    adb -s <TV_IP>:5555 shell pm enable --user 0 "$p"
done
```

Confirm with `pm list packages -d` (the count should drop from 28 to 21) and leave the 21
factory-disabled packages alone.

Do **not** reach for `tools/restore.sh` here. This debloat was applied by hand, so no recovery record
exists for it; with no record, `restore.sh` enables **every** package in the list, enables the stock
launcher (`com.google.android.tvlauncher` by default), hands Home to it and resets the animation
scales — the opposite of keeping the current launcher. Use the `pm enable` block above. If you
deliberately want the stock Home back, that is a separate decision; after re-enabling `com.tcl.tv`
verify one short power press (see the warning above).

## Evidence index

| Path | Contents |
| --- | --- |
| `devices/DEBLOAT-REPORT.md` | full report of the 7-package pass, exclusions and stage 3 |
| `devices/backup-packages-2026-10-26.txt` | 21 packages disabled before the pass |
| `devices/backup-packages-2026-10-26-enabled.txt` | 126 enabled packages before the pass |
| `devices/evidence/getprop-pre.txt` | curated `getprop`: model/board/build identity only |
| `devices/evidence/dumpsys-<pkg>-pre.txt` | per-package pre-state (the 7 plus packages left on) |
| `devices/evidence/batch1-disable.txt`, `batch2-disable.txt` | `pm disable-user` transcripts |
| `devices/evidence/batch1-postcheck.txt`, `batch2-postcheck.txt` | crash buffer + Home after each batch |
| `devices/evidence/pm-disabled-post.txt`, `pm-enabled-post.txt` | post-change state (28 disabled) |
| `devices/evidence/protected-check.txt` | the 17 packages that must stay enabled |
| `devices/evidence/adservices-attempt.txt` | the two `Unknown package` errors for ad services |
| `docs/project/FINDINGS.md` §11, §12 | drift finding, benchmarks, measurement traps |
| `docs/project/AUDIT.md` §5 | Native Heap attribution and the banner A/B |
| `docs/BUDGET.md` | the gate these numbers are judged against |

## Open items

- Persistence of the 7 across a reboot is **unverified**; run `tools/check.py` after the next reboot.
- Stage 3 (`com.tcl.overseasappshow` and its family) is deferred by the owner; the drift is not yet
  attributed.
- The advertising identifier remains via `com.google.android.gms`.
- The fork release has no runtime measurement yet, and the raw `baseline/` captures cited by the
  audit are not committed.
