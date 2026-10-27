#!/usr/bin/env bash
# Measures a Home launcher on this TV with the same protocol used in baseline/F4.
#
# Usage: ./tools/bench-launcher.sh <package> <component> <label>
#   package    applicationId, e.g. br.eddy.tvhome
#   component  activity to start cold, e.g. br.eddy.tvhome/com.gathin.tvhome.MainActivity
#   label      name for the raw-output folder, e.g. eddy-build / upstream-v30
#
# What it measures (same order every run, so two launchers are comparable):
#   1. cold start x5: am force-stop + am start -W, WaitTime of each run
#   2. jank: dumpsys gfxinfo reset, 20 D-pad keys at 0.7 s, read 3 s later
#   3. PSS from dumpsys meminfo
#   4. idle 30 s: frames rendered and CPU share while nothing is pressed
#
# It only reads from the device, except force-stop/start of the package under test
# and the D-pad events it sends. It never changes Home, accessibility or debloat.

set -uo pipefail

SERIAL="${SERIAL:?pasá SERIAL=<ip-de-la-tv>:5555 (adb connect <ip>:5555)}"
ADB="${ADB:-adb}"   # poné ADB=/ruta/a/platform-tools/adb si no está en PATH
PKG="${1:?package id required}"
COMPONENT="${2:?component required, e.g. br.eddy.tvhome/com.gathin.tvhome.MainActivity}"
LABEL="${3:?label required}"

OUT="${OUT:-bench-results/bench-$LABEL-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT"

adb() { "$ADB" -s "$SERIAL" "$@"; }

if ! adb shell "pm list packages | grep -qx 'package:$PKG'"; then
    echo "FATAL: $PKG is not installed" >&2
    exit 1
fi

LOAD=$(adb shell cat /proc/loadavg | awk '{print $1}')
echo "load average at start: $LOAD"
if [ "$(printf '%s\n5\n' "$LOAD" | sort -g | tail -1)" = "$LOAD" ] && [ "$LOAD" != "5" ]; then
    echo "WARNING: load is $LOAD; numbers will be inflated by other work on the TV" >&2
fi

echo "== 1/4 cold start x5 (Displayed de ActivityTaskManager) =="
# When the package under test owns Home, `adb shell am force-stop` does not produce a cold
# start: the system relaunches Home by itself and `am start -W` then reports LaunchState
# UNKNOWN with a 7-20 ms WaitTime that measures nothing. The honest number is the one the
# system logs for that relaunch: "Displayed <pkg>/<activity>: +NNNms".
: > "$OUT/coldstart.txt"
for i in 1 2 3 4 5; do
    LOG="$OUT/coldstart-$i.logcat.txt"
    adb logcat -c
    adb logcat -s ActivityTaskManager:I > "$LOG" 2>&1 &
    LPID=$!
    sleep 1
    adb shell "am force-stop $PKG"
    for t in $(seq 1 40); do
        grep -q "Displayed $PKG" "$LOG" 2>/dev/null && break
        sleep 0.25
    done
    kill "$LPID" 2>/dev/null
    LINE=$(grep -m1 "Displayed $PKG" "$LOG" 2>/dev/null)
    if [ -n "$LINE" ]; then
        MS=$(printf '%s' "$LINE" | sed -n 's/.*+\([0-9][0-9]*\)ms.*/\1/p')
        echo "run $i: ${MS:-?} ms (Displayed)" | tee -a "$OUT/coldstart.txt"
    else
        # Package does not own Home: fall back to the classic COLD start measurement.
        adb shell "am force-stop $PKG"
        sleep 1
        WT=$(adb shell "am start -W -n $COMPONENT" | tr -d '\r' | sed -n 's/^WaitTime: //p')
        echo "run $i: ${WT:-?} ms (WaitTime fallback, not Home)" | tee -a "$OUT/coldstart.txt"
    fi
    sleep 1
done

# F4 used 20 DOWN/UP pairs (40 keys). Performance preset draws about one frame per focus
# change, so this yields ~40 frames; a smaller sample makes the jank percentage meaningless.
pkill -f "adb logcat -s ActivityTaskManager" 2>/dev/null
echo "== 2/4 jank, 20 pares DOWN/UP at 0.7 s =="
adb shell "dumpsys gfxinfo $PKG reset" > /dev/null
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
    adb shell input keyevent 20 > /dev/null   # DPAD_DOWN
    sleep 0.7
    adb shell input keyevent 19 > /dev/null   # DPAD_UP
    sleep 0.7
done
sleep 3
adb shell "dumpsys gfxinfo $PKG" > "$OUT/gfxinfo.txt"
JANKY=$(grep -m1 -E "Janky frames" "$OUT/gfxinfo.txt" | sed 's/^ *//')
FRAMES=$(grep -m1 -E "Total frames rendered" "$OUT/gfxinfo.txt" | sed 's/^ *//')
PCT50=$(grep -m1 -E "50th percentile" "$OUT/gfxinfo.txt" | sed 's/^ *//')
PCT90=$(grep -m1 -E "90th percentile" "$OUT/gfxinfo.txt" | sed 's/^ *//')
PCT95=$(grep -m1 -E "95th percentile" "$OUT/gfxinfo.txt" | sed 's/^ *//')
echo "$FRAMES"
echo "$JANKY"
echo "$PCT50"
echo "$PCT90"
echo "$PCT95"

echo "== 3/4 PSS =="
adb shell "dumpsys meminfo $PKG" > "$OUT/meminfo.txt"
# The totals live on one line: "TOTAL PSS: 16460   TOTAL RSS: 100912   TOTAL SWAP PSS: 185".
tr -d '\r' < "$OUT/meminfo.txt" | grep -E "^ *TOTAL PSS:" | sed 's/^ *//'

echo "== 4/4 idle 30 s =="
BEFORE=$(adb shell "dumpsys gfxinfo $PKG" | grep -m1 "Total frames rendered" | grep -oE "[0-9]+")
sleep 30
AFTER=$(adb shell "dumpsys gfxinfo $PKG" | grep -m1 "Total frames rendered" | grep -oE "[0-9]+")
CPU=$(adb shell "dumpsys cpuinfo" | grep -m1 "$PKG" | sed 's/^ *//')
echo "frames in 30 s idle: $(( ${AFTER:-0} - ${BEFORE:-0} ))"
echo "cpu: ${CPU:-not sampled}"
echo "$CPU" >> "$OUT/idle.txt"
adb shell "dumpsys cpuinfo" > "$OUT/cpuinfo.txt"

echo
echo "raw output: $OUT"
