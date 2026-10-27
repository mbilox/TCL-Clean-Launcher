#!/usr/bin/env bash
# Attributes the launcher's Native Heap inventory of the upstream v3.0 build:
# set it as Home, let every lazy loader finish, then capture `dumpsys meminfo -a`
# objects view. Home restored at the end.
#
# Read-only vs. the TV except: home set/relaunch of com.gathin.tvhome and restoring
# br.eddy.tvhome after.

set -uo pipefail
SERIAL="${SERIAL:-192.168.1.15:5555}"
ADB="${ADB:-adb}"   # poné ADB=/ruta/a/platform-tools/adb si no está en PATH
PKG="${PKG:-com.gathin.tvhome}"
COMP="$PKG/com.gathin.tvhome.MainActivity"
OUT="${OUT:-bench-results/native-heap-probe-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT"
export MSYS_NO_PATHCONV=1
adb() { "$ADB" -s "$SERIAL" "$@"; }

echo "== Home = $PKG =="
adb shell "cmd package set-home-activity $COMP" 2>&1 | tr -d '\r' | tail -1
adb shell input keyevent 224 >/dev/null 2>&1   # wake
sleep 1
adb shell "am force-stop $PKG" >/dev/null 2>&1
sleep 2
adb shell "am start -a android.intent.action.MAIN -c android.intent.category.HOME" >/dev/null 2>&1
sleep 25   # lazy loaders (AppAdapter banners, LauncherLocale, UiFont) need to settle

echo "== meminfo -a (inventario de objetos) =="
adb shell "dumpsys meminfo -a $PKG" | tr -d '\r' > "$OUT/meminfo-full.txt"
adb shell "dumpsys meminfo $PKG"    | tr -d '\r' > "$OUT/meminfo-plain.txt"
echo "-- resumen --"
grep -E "^ +(Native Heap|TOTAL PSS|TOTAL SWAP PSS)" "$OUT/meminfo-full.txt" | head -3

echo "== restauración =="
adb shell "cmd package set-home-activity ${RESTORE_HOME:-br.eddy.tvhome}/com.gathin.tvhome.MainActivity" 2>&1 | tr -d '\r' | tail -1
adb shell "am force-stop $PKG; am start -a android.intent.action.MAIN -c android.intent.category.HOME" >/dev/null 2>&1
sleep 4
adb shell "cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME" | tr -d '\r' | grep packageName | head -1
echo "evidencia: $OUT"
