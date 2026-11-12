# TCL BeyondTV2 (RTD2851, AR05) — device profile

One real TCL BeyondTV2 running the TCL Clean Launcher fork: Android TV 11, 2 GB of RAM, and the
telemetry debloat of record applied on 2026-10-26 and revised on 2026-11-11. It is written to be
publishable as-is, so every claim points at a committed evidence file.

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
`evidence/dumpsys-com.tcl.tv.tclhome_passive-pre.txt:116`). The 7 packages above were the debloat of
record on 2026-10-26; the list of record changed on 2026-11-11 (`devices/telemetry-7.txt`:
`com.tcl.guard` back on, `com.tcl.overseasappshow` off — see “Current state” below). The
`FINDINGS.md` §1 sentence describes an earlier attempt that was no longer in effect.

**Verified at apply time, not across a reboot.** Nothing was rebooted in this run, so persistence of
the 7 was unverified when that pass ended (it was verified on 2026-11-11 — see “Current state”
below). The 21 packages that were already disabled at the factory
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

### Current state (2026-11-11): stage 3 done, `com.tcl.guard` back on

- `com.tcl.overseasappshow` is **disabled** (stage 3, owner-approved for this step). It was the
  boot-Home re-applier: with it off and Home chosen in the TV's own picker, the fork survived two
  reboots 2/2 (`docs/project/FINDINGS.md` §17d). `tools/check.py` reports 7/7 with no drift against
  `devices/telemetry-7.txt` after the reboot.
- `com.tcl.guard` is **enabled again**, and stays that way. It owns the Bluetooth remote's
  `virtual-search` input channel; disabled, the mic button reaches `katniss` with no audio stream and
  the `AudioRecord` fallback never runs — a dead remote microphone that a reboot does not repair
  (`FINDINGS.md` §16). The 2026-10-26 seven-package pass above included it; the list of record is now
  the six telemetry packages plus `overseasappshow`.
- The unit now boots into `ar.TCL.cleanLauncher`. `enabled_accessibility_services` is still `null`,
  so the launcher's key services are not bound (see the keycode table above).

Suspected re-enabler: `com.tcl.overseasappshow`, whose `BootReceiver` runs on `BOOT_COMPLETED`,
`STR_RESUME_COMPLETED` and `com.tcl.countrychange` and is documented upstream as re-enabling its
family at every boot (`examples/tcl-beyondtv2-android-11/debloat.txt`, header; DEBLOAT-REPORT §5).
It was still **enabled** on 2026-10-26, because disabling it was stage 3, deferred by the owner until
the fork ran as a stable Home, since it also carries Cast risk (upstream #453); that stage ran on
2026-11-11 and confirmed it as the boot-Home re-applier (`docs/project/FINDINGS.md` §17d). A competing
explanation for the telemetry drift (`appstatecontroller`) is recorded in `docs/project/IDEAS.md` and
was not tested. The practical rule is the one that matters: verify and re-apply after every reboot.

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
nothing in that set changed. `com.tcl.guard` belongs on that list too, for the reason in
`docs/project/FINDINGS.md` §16 (below, “Current state”).

## Remote keycodes (`TCL_RC802VEU2`)

What each button sends, so `app_keys` can be configured without guessing. Only the microphone numbers
have been observed on this unit; the four vendor keycodes are the ones the upstream project documents
for sibling TCL boards **in this repo**, and none of them has been confirmed on this remote yet.

| Button | Number | Where in this repo | Confirmed on this unit? |
| --- | --- | --- | --- |
| Mic (voice) | framework key **219** | `docs/project/FINDINGS.md` §16 (getevent + logcat, 2026-11-11) | **yes** |
| Mic (same button, lower layer) | scancode `KEY_SEARCH` **84**, HID usage `0xC0221`, on the HID device named `virtual-search` | `docs/project/FINDINGS.md` §5; `examples/tcl-beyondtv2-android-11/README.md` | yes |
| Netflix | **4062** | `examples/tcl-43c645/README.md` and its `config.json` (`app_keys`); `docs/devices/tcl-55c728-g05-4k-gb.md` | no |
| YouTube | **4065** | `examples/tcl-43c645/README.md` and its `config.json` (`app_keys`); `docs/devices/tcl-55p71b-g09-key-repeat.md` | no |
| Prime Video | **4099** | `examples/tcl-43c645/README.md` (read with `KeyEvent.keyCodeFromString`, not guessed); `docs/devices/tcl-55c728-g05-4k-gb.md` | no |
| Settings (gear) | **4077** (`KEYCODE_TCL_SET`) | `examples/tcl-43c645/README.md` and its `config.json` (`settings_key_keycodes`); `examples/tcl-55c725/README.md` | no |

What the table implies for this unit:

- **The mic button is not a keycode to remap.** `TclPhoneWindowManager` always dispatches it as key 219
  on its way to the vendor chain, and what decides whether the assistant gets audio is the input device
  the event arrives on, i.e. whether `com.tcl.guard` is enabled (`FINDINGS.md` §16). Consuming 219 or 84
  in an accessibility service is the documented way to break the mic (§18 anti-patterns).
- **Keycodes differ between TCL firmwares.** The gear is 4077 on the boards above and 83 on others
  (`examples/tcl-55c725/README.md`), so read the number on the TV rather than assuming: `adb shell logcat
  -d -v time | grep -E 'interceptKeyTq|handleKeyBeforeQueueing'` right after one press, or `getevent -pl`
  for the device and its declared codes (`docs/TROUBLESHOOTING.md`, “Which keycode a dead button
  sends”).
- **`app_keys` does nothing until a key service is bound**, and on this unit
  `enabled_accessibility_services` is `null`, so `HomeKeyService`/`InputKeyService` are not bound
  (`docs/project/FINDINGS.md` §17d). Map a button and it still goes to whatever answers it today until
  that changes.
- The `tcl-43c645` table also lists `KEYCODE_TCL_QUICK_MENU` **4020** (TCL button), `4140` (Guard) and
  `4057` (Media), with the Linux scancodes behind all of them. On the RTD2851 firmware of
  `docs/devices/tcl-beyondtv-rt2851.md`, 4062 and 4020 never reach an accessibility key filter at all:
  they are handled by `com.tcl.keycustomfunctionservice`, and an injected `input keyevent 4020` does not
  reproduce them — only a real press does.

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

¹ "Our build" there is the Eddy 0.6.0 code plus two local edits, not the fork APK. That table is the
pre-fork baseline from `docs/project/FINDINGS.md` §12. The fork itself was measured later and on the
same day for both modes: the three-way comparison (fork Normal/Max, Eddy, upstream) with APK sizes,
SHA-256 and cold starts is in [`docs/BUDGET.md`](../docs/BUDGET.md). Against the budget of record:
Max-mode PSS is met (27.7 MB of 30 MB) and Normal-mode jank, p95 and idle are inside their columns;
Max-mode p95 reads 11 ms against a 9 ms budget, and the 600 ms cold start is met by no launcher of
this family on this firmware.

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

The list file is committed: **`devices/telemetry-7.txt`** (6 telemetry packages + `com.tcl.overseasappshow`,
with `com.tcl.guard` left out and the reason in its header). Use it instead of typing names by hand.

```sh
TV_HOST=<tv ip> tools/check.py devices/telemetry-7.txt ar.TCL.cleanLauncher        # report
TV_HOST=<tv ip> tools/check.py --fix devices/telemetry-7.txt ar.TCL.cleanLauncher  # re-disable drift
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
report Home drift and `--fix` would hand Home to it. As of 2026-11-11 that package is
`ar.TCL.cleanLauncher` (see “Current state” above); before it was `br.eddy.tvhome`.

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

- Persistence across a reboot **is** verified for this set: `tools/check.py` 7/7 after the
  2026-11-11 reboots. Keep re-running it, since the drift mechanism is a habit, not an event
  (`FINDINGS.md` §11).
- `com.tcl.overseasappshow` is off and its family (`MagiConnect`, `notereminder`,
  `MultiScreenInteraction_TV`) was not touched; the Cast regression in upstream #453 stays untested on
  this unit.
- The advertising identifier remains via `com.google.android.gms`.
- No keycode of this remote except the mic (219 / scancode 84) has been confirmed on this unit; the
  `app_keys` candidates in the table above need a real-remote press to be trusted.
- The raw `baseline/` captures cited by the audit are not committed.
