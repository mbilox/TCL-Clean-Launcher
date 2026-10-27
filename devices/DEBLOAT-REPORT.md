# TCL BeyondTV2 (AR05) — telemetry / ads / ACR debloat report

**Date:** 2026-10-26 (host UTC; unit local 2026-10-26 01:07 -03)
**Unit:** TCL BeyondTV2 @ 192.168.1.15:5555 (adb over TCP, authorized)
**Firmware:** `TCL/BeyondTV2/BeyondTV:11/PPR1.180610.011/AR05:user/release-keys` — Android TV 11, `ro.build.version.sdk=30`
**Method:** `pm disable-user --user 0 <pkg>` only. No uninstall, no reboot, no `pm clear`, no settings changes, no installs, no package re-enabled.
**Reference:** `cleanyourtv/examples/tcl-beyondtv2-android-11/debloat.txt` (same model + build number, Android 11 variant)
**Source of truth for pre/post state:** `evidence/` in this folder.

---

## 1. Starting state (snapshot)

| Artifact | File |
| --- | --- |
| Disabled packages before change (21) | `backup-packages-2026-10-26.txt` |
| Enabled packages before change (126) | `backup-packages-2026-10-26-enabled.txt` |
| Full `pm list packages -f` | `evidence/pm-list-packages-f-pre.txt` |
| Full `getprop` (465 lines) | `evidence/getprop-pre.txt` |
| Disabled packages after change (28) | `evidence/pm-disabled-post.txt` |
| Crash buffer before change (empty) | `evidence/logcat-crash-pre.txt` |

Pre-change HOME resolution: `com.gathin.tvhome.MainActivity` / packageName `br.eddy.tvhome` (the locally built
launcher) — `evidence/home-resolve-pre.txt`.

### Correction to the handoff: `com.tcl.bi` and `com.tcl.tv.tclhome_passive` were NOT disabled

The task said these had already been applied by the owner. Observed evidence at task start:

```
$ adb shell pm list packages -d | grep -E 'tcl\.bi|tclhome_passive'   # -> no output
$ adb shell pm list packages -e | grep -E 'tcl\.bi|tclhome_passive'
package:com.tcl.tv.tclhome_passive
package:com.tcl.bi
$ adb shell dumpsys package com.tcl.bi | grep 'User 0:'
    User 0: ... installed=true ... enabled=0 ...     # enabled=0 == DEFAULT == enabled
```

Both were **enabled** (not in the 21-package disabled set, `enabled=0`). They are unambiguous telemetry class
and are first-class upstream entries, so they were disabled in this pass (batch 1) rather than skipped. If they
were disabled on a different unit or by a method that was reverted by a reboot, this run is what makes them
actually stay off. **They must be re-checked after the next reboot** — see §6.

The 21 packages that were genuinely disabled before this task are the factory/UK preload set
(`uk.co.freeview.*` × 15, `au.com.stan.and`, `com.tcl.gallery`, `com.google.android.tvlauncher`,
`tv.wuaki.apptv`, `com.google.android.apps.nbu.smartconnect.tv`, `com.tcl.smartalexa`) — untouched.

---

## 2. Packages disabled in this pass (7)

Every package was confirmed present and enabled with `dumpsys package <pkg>` before disabling; the per-package
dumps are in `evidence/dumpsys-<pkg>-pre.txt`.

| # | Package | Class | Evidence (one-liner) | Command | Status after |
| --- | --- | --- | --- | --- | --- |
| 1 | `com.tcl.bi` | analytics/telemetry | Upstream: "TCL analytics / telemetry service". Components: `com.tcl.ttvs.bi.core.BIService`, `com.tcl.ttvs.bi.provider.ParamProvider`; perms `INTERNET`, `ACCESS_WIFI_STATE`, `READ_PHONE_STATE`, `RECEIVE_BOOT_COMPLETED: granted=true` | `pm disable-user --user 0 com.tcl.bi` | `disabled-user` |
| 2 | `com.tcl.tv.tclhome_passive` | ads/promo (push onto home) | Upstream: "pushes TCL content onto the home screen at boot". Components: `broadcast.BootCompleteReceiver` (`RECEIVE_BOOT_COMPLETED: granted=true`), `HomePassiveActivity`; perms `INTERNET`, `ACCESS_WIFI_STATE` | `pm disable-user --user 0 com.tcl.tv.tclhome_passive` | `disabled-user` |
| 3 | `com.tcl.waterfall.overseas` | ads/promo (content feed) | Upstream: "TCL web content feed / recommendation waterfall". Components: `com.tcl.browser.portal.browse.activity.PlayWebVideoActivity`, `.ui.SchemeActivity`; perms `INTERNET: granted=true`, `ACCESS_WIFI_STATE: granted=true` | `pm disable-user --user 0 com.tcl.waterfall.overseas` | `disabled-user` |
| 4 | `com.tcl.messagebox` | ads/promo (pushed messages) | Upstream: "pushed messages and promos". Components: `.activity.MainActivity`, `.activity.ImagePlayerActivity`, `org.tcl.ttvs.LocalCompactReceiver`; perms `INTERNET: granted=true`, `RECEIVE_BOOT_COMPLETED: granted=true` | `pm disable-user --user 0 com.tcl.messagebox` | `disabled-user` |
| 5 | `com.google.android.partnersetup` | ads (partner attribution / installs) | Upstream: "partner-app attribution / installs". Components: `.RlzPingBroadcastReceiver`, `.UpdateIntentReceiver`, `.PhoneStateReceiver`; perms `QUERY_ALL_PACKAGES`, `READ_PHONE_STATE`, `RECEIVE_BOOT_COMPLETED: granted=true` | `pm disable-user --user 0 com.google.android.partnersetup` | `disabled-user` |
| 6 | `com.tcl.guard` | analytics/telemetry (device-use tracking) | Upstream: "TGuard: device-use tracking, 'health info', memory cleaner". Components: `.permissionshield.*` (permission logging), `.mainmenu.splashpage.activity.StartActivity`; perms `INTERNET: granted=true`, `READ_PHONE_STATE`, `RECEIVE_BOOT_COMPLETED: granted=true`; `pkgFlags=PERSISTENT` | `pm disable-user --user 0 com.tcl.guard` | `disabled-user` |
| 7 | `com.google.android.feedback` | analytics/telemetry (diagnostics upload) | Upstream: "'send feedback'". Components: `.FeedbackActivity`, `.BugReportService`; perms `INTERNET: granted=true`, `ACCESS_NETWORK_STATE` | `pm disable-user --user 0 com.google.android.feedback` | `disabled-user` |

**Total disabled in this pass: 7.** Disabled package count went 21 → 28 (`evidence/pm-disabled-post.txt`).

### Batches (each ≤ 5 packages) and post-batch checks

| Batch | Packages | Time (UTC) | Crash buffer after | HOME resolution after | Log |
| --- | --- | --- | --- | --- | --- |
| 1 | `com.tcl.bi`, `com.tcl.tv.tclhome_passive`, `com.tcl.waterfall.overseas`, `com.tcl.messagebox`, `com.google.android.partnersetup` | 04:06:58 | `logcat -d -b crash -t 100` → **empty**, 0 FATAL (baseline was also empty) | still `br.eddy.tvhome` / `com.gathin.tvhome.MainActivity` | `evidence/batch1-disable.txt`, `evidence/batch1-postcheck.txt` |
| 2 | `com.tcl.guard`, `com.google.android.feedback` | 04:07:14 | `logcat -d -b crash` → **empty**, 0 FATAL | still `br.eddy.tvhome` / `com.gathin.tvhome.MainActivity` | `evidence/batch2-disable.txt`, `evidence/batch2-postcheck.txt` |

Every disable returned `new state: disabled-user` (no failures, no system-protected packages refused).
No reboot was performed.

---

## 3. Google ad services / Ad ID — honest finding

**No Google ad-services package exists on this firmware.** `pm list packages | grep -Ei 'adservice|adservices|ads\.'`
returns nothing, and both plausible names are `Unknown package`:

```
$ adb shell pm disable-user --user 0 com.google.android.adservices
java.lang.IllegalArgumentException: Unknown package: com.google.android.adservices
$ adb shell pm disable-user --user 0 com.google.android.ext.services.adservices
java.lang.IllegalArgumentException: Unknown package: com.google.android.ext.services.adservices
```

Raw output: `evidence/adservices-attempt.txt`. AdServices (the standalone ad-ID/LAT module) is an Android 12+
GMS module; this is an Android 11 GMS build, so the advertising identifier lives inside
`com.google.android.gms`, which is explicitly out of scope (core GMS, disabling it breaks the device).
Consequence, stated plainly: **the advertising-identifier surface is still present via Play services and was
not removed.** What was removed are the TCL-side and Google-side collectors/attributors listed above.
The only honest remedy left would be a settings-level ad-ID reset/opt-out, which this pass is forbidden to touch.

---

## 4. Not disabled / needs review

| Package | Why not touched |
| --- | --- |
| `com.tcl.overseasappshow` | Factory promo/app-show service **and the boot-time re-enabler** of the partner packages. Disabling it is the stage-1 entry of the upstream list but this pass was explicitly scoped away from that family. See §5. |
| `com.tcl.tv.tclhome_master` | Part of the `overseasappshow` re-enable family (stage 3 decision pending). Also carries `StartUpActivity`, an alternate home; the owner's HOME must not move. |
| `com.tcl.notereminder` | Part of the `overseasappshow` re-enable family (stage 3 decision pending). |
| `com.tcl.MultiScreenInteraction_TV` (MagiConnect) | Part of the `overseasappshow` re-enable family (stage 3). Casting behaviour is also a functional-verification item. |
| `com.tcl.esticker` | Retail demo sticker, but known **not to stay disabled on this firmware** (upstream: it does stay off on the Android 11 unit, yet the handoff recorded it as non-persistent here). Excluded per task scope; if it should be retired the boot re-enabler has to go first. |
| `com.tcl.partnercustomizer` | Load-bearing: `.tkey.WithIcon` holds a `LEANBACK_LAUNCHER` activity on this unit (upstream DO-NOT-DISABLE list). |
| `com.google.android.tvrecommendations` | Recommendation row, not a telemetry collector; upstream deliberately excludes it because the stock launcher it fed is already disabled. Left for review — disabling it would also affect the assistant/ambient surfaces. |
| `android.autoinstalls.config.tcl.device` (PlayAutoInstallStub) | Partner auto-install config stub (`.DummyReceiver`, 63-line dump, no services, no permissions). Plausibly partner attribution, but it is an install-time config and not clearly telemetry; purpose not determinable from evidence here → left alone. |
| `com.google.android.gms` | Core Play services; holds GMS Ad ID/measurement. Out of scope by instruction and by necessity. |
| `com.google.android.katniss`, `com.google.android.tv.assistant`, `com.tcl.assistant`, `com.tcl.micmanager` | Voice stack. Owner's voice button must keep working (upstream disables these by choice; we must not). |
| `com.google.android.backdrop` | Google TV ambient screensaver. Out of scope. |
| `com.google.android.apps.mediashell`, `com.google.android.tv.remote.service` | Chromecast / phone-remote surfaces; touching them risks the Cast regression in §5. |
| `com.tcl.tv`, `com.tcl.tvinput`, tuner/EPG, `com.tcl.keycustomfunctionservice`, `com.tcl.autopair`, `com.tcl.inputmethod.international`, `com.tcl.settings`, `com.tcl.initsetup`, `com.tcl.factory.view`, `com.tcl.xian.StartandroidService`, `com.tcl.copydatatotv`, `com.tcl.systemserver`, `com.tcl.providers.config`, `com.tcl.usercenter`, `com.tcl.useragreement`, `com.tcl.versionUpdateApp` | Live TV / HDMI / remote / setup / account / OTA. Upstream DO-NOT-DISABLE or out of scope. OTA updater deliberately kept so security fixes still arrive. |
| `br.eddy.tvhome` (active home), `com.gathin.tvhome` (inactive) | Owner's launchers. Untouched. |
| Streaming apps (Netflix, Prime Video, YouTube, YouTube Music, Disney+, Star+, Crunchyroll, HBO Max/`com.wbd.stream`, Flow, VLC, Stremio, Jio TV, TV browser) | Out of scope — media apps are kept. |

Sanity check that nothing in the protected set changed (`evidence/protected-check.txt`, `evidence/pm-enabled-post.txt`):
`com.tcl.tv`, `com.tcl.tvinput`, `com.tcl.micmanager`, `com.google.android.katniss`, `com.google.android.gms`,
`com.google.android.apps.mediashell`, `com.google.android.backdrop`, `com.tcl.partnercustomizer`,
`com.tcl.keycustomfunctionservice`, `com.tcl.overseasappshow`, `com.tcl.tv.tclhome_master`,
`com.tcl.notereminder`, `com.tcl.MultiScreenInteraction_TV`, `com.tcl.esticker`, `br.eddy.tvhome`,
`com.gathin.tvhome`, `com.tcl.versionUpdateApp` — **all still `ENABLED`**.

Upstream entries **not present on this firmware** (verified against `pm list packages -f`): `com.tcl.smartalexa`
is present but was already disabled at the factory; there is no dedicated ACR/Nielsen/"exposure" package
installed — on this firmware the viewing-data surface is inside `com.tcl.bi` (now disabled) rather than a
separate ACR app. `com.tcl.tv.odm`, `com.tcl.acr`, `com.nielsen.*`, `com.tcl.samba.tv`-style entries do not exist here.

---

## 5. Stage 3 pending — `overseasappshow` family and the Cast risk

**Stage 3 (not done, needs an explicit owner decision):** `com.tcl.overseasappshow` is a system-uid service
whose `BootReceiver` runs at priority 1000 on `BOOT_COMPLETED`, `STR_RESUME_COMPLETED` and
`com.tcl.countrychange`, and it reacts to `PACKAGE_CHANGED` and connectivity changes. Upstream documents that
it **re-enables** `com.tcl.MultiScreenInteraction_TV`, `com.tcl.notereminder` and `com.tcl.tv.tclhome_master`
at every boot (and MagiConnect right after any `pm install`). It is the reason that family is still enabled
here. Disabling it is what would make a stage-3 pass stick — and it is also the change most likely to be
reverted or to surprise the owner, so it was deliberately left out of this pass.

**Cast regression risk:** upstream issue #453 describes Chromecast/mirroring breakage observed on other units
after large debloats. This run **did not touch any Cast package** — `com.google.android.apps.mediashell`
(Chromecast receiver), `com.google.android.gms` and `com.google.android.backdrop` are all still enabled, and
`com.tcl.miracast` / `com.tcl.MultiScreenInteraction_TV` were left alone. Casting from a phone should be
unaffected, but it is on the verification list below because it is the one failure mode a big debloat can cause
indirectly.

**Re-enable drift risk:** `com.tcl.bi` and `com.tcl.tv.tclhome_passive` were found enabled at the start of this
run despite being recorded as already disabled. Whatever reverted them (reboot, factory reset, or a
`pm install`/`PACKAGE_CHANGED` path) may do it again. Re-check with:

```
adb -s 192.168.1.15:5555 shell pm list packages -d | grep -E 'tcl\.bi|tclhome_passive|waterfall|messagebox|partnersetup|tcl\.guard|android.feedback'
```

---

## 6. Restore (re-enable everything touched by this pass)

One-liner, exactly the 7 packages disabled here — it does not touch anything else:

```
adb -s 192.168.1.15:5555 shell "pm enable com.tcl.bi; pm enable com.tcl.tv.tclhome_passive; pm enable com.tcl.waterfall.overseas; pm enable com.tcl.messagebox; pm enable com.google.android.partnersetup; pm enable com.tcl.guard; pm enable com.google.android.feedback"
```

Per-package form:

```
adb -s 192.168.1.15:5555 shell pm enable com.tcl.bi
adb -s 192.168.1.15:5555 shell pm enable com.tcl.tv.tclhome_passive
adb -s 192.168.1.15:5555 shell pm enable com.tcl.waterfall.overseas
adb -s 192.168.1.15:5555 shell pm enable com.tcl.messagebox
adb -s 192.168.1.15:5555 shell pm enable com.google.android.partnersetup
adb -s 192.168.1.15:5555 shell pm enable com.tcl.guard
adb -s 192.168.1.15:5555 shell pm enable com.google.android.feedback
```

(The 21 packages that were already disabled before this task are **not** in this block — re-enabling them is
out of scope for this report.)

---

## 7. Functional verification to run with the owner

Nothing was rebooted, so these are the checks to do at the next natural reboot and in normal use:

1. **Home still returns to the custom launcher** — `cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME` should keep resolving to `br.eddy.tvhome` / `com.gathin.tvhome.MainActivity`. Verified at both batch boundaries this run (after start, after batch 1, after batch 2); launcher process was alive (`pidof br.eddy.tvhome` → 3552) with no ANR in the events buffer.
2. **Live TV** — `com.tcl.tv` / `.TVActivity` launches and tunes channels (`com.tcl.tv`, `com.tcl.tvinput` enabled).
3. **HDMI 1–3 inputs** — input list populated and switching works (TIF path untouched).
4. **Volume / remote keys** — `com.tcl.keycustomfunctionservice` + `com.tcl.partnercustomizer` handling intact.
5. **Voice button** — mic search still opens (`com.tcl.micmanager`, `com.google.android.katniss`, `com.tcl.assistant` all still enabled; nothing mic-related touched).
6. **Cast from a phone** — Chromecast discovery + playback (`com.google.android.apps.mediashell`, GMS untouched; this is the #453 watch item).
7. **Netflix / Prime Video / YouTube** launch and play (streaming apps untouched).
8. **Home-screen promos / TCL content row** — expected to be gone or reduced now that `tclhome_passive` and `waterfall.overseas` are disabled; confirm the owner is happy with the emptier home screen.
9. **Post-reboot re-check** of the 7 disabled packages (§5 command) — the important one for `com.tcl.bi` / `tclhome_passive`, which were found re-enabled this run.
10. **Memory cleaner / "device health" UI** in `com.tcl.settings` may reference `com.tcl.guard`; if that entry is used and now misbehaves, restore only `com.tcl.guard`.

Minor observation from the post-change logcat (`evidence/logcat-main-post.txt`): `com.tcl.guard` still appears in
system_server bookkeeping (`I Icing: doRemovePackageData com.tcl.guard`,
`V GoogleTVCustomer: :---->onPackageChanged:com.tcl.guard`,
`D TGuardMemoryManagerTrimHandler: rank6 com.tcl.guard`). These are the platform reacting to the disable, not a
crash: the crash buffer stayed empty and no FATAL/ANR was logged.

---

## 8. Evidence index

| File | Contents |
| --- | --- |
| `backup-packages-2026-10-26.txt` | disabled set before change (21) |
| `backup-packages-2026-10-26-enabled.txt` | enabled set before change (126) |
| `evidence/getprop-pre.txt` | full `getprop` |
| `evidence/pm-list-packages-f-pre.txt` | full `pm list packages -f` (apk paths) |
| `evidence/pm-enabled-post.txt`, `evidence/pm-disabled-post.txt` | post-change state |
| `evidence/dumpsys-<pkg>-pre.txt` | per-package dumps used as disable evidence |
| `evidence/batch1-disable.txt`, `evidence/batch2-disable.txt` | `pm disable-user` transcripts |
| `evidence/batch1-postcheck.txt`, `evidence/batch2-postcheck.txt` | crash buffer + HOME resolution after each batch |
| `evidence/logcat-crash-pre.txt` | crash buffer baseline (empty) |
| `evidence/logcat-main-post.txt` | main log tail after batch 2 |
| `evidence/home-resolve-pre.txt` | HOME resolution before change |
| `evidence/adservices-attempt.txt` | the two `Unknown package` errors for ad services |
| `evidence/protected-check.txt` | enabled/disabled state of every must-not-break package |
