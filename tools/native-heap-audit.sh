#!/usr/bin/env bash
# Attributes the Native Heap of the upstream v3.0 launcher on the real unit:
# one variable per run, base = lean config (animations off etc.).
#
# Matrix:
#   N0 lean                        (baseline, everything off)
#   N1 lean + wallpaper on         (adds WallpaperPlayer decode, RGB_565)
#   N2 lean + wallpaper true_color (same wallpaper in ARGB_8888)
#   N3 lean + uniform_tiles true   (icon-only tiles, no per-app banners)
#   N4 lean + cinematic_home true  (adds the shelf and its HeroArt bitmaps)
#
# Read-only against the TV except: set-home-activity com.gathin.tvhome, its
# force-stop/relaunch, config.json writes in its own files dir, and one pushed
# wallpaper file. Home is RESTORED to br.eddy.tvhome at the end.

set -uo pipefail
SERIAL="${SERIAL:-192.168.1.15:5555}"
ADB="${ADB:-adb}"   # poné ADB=/ruta/a/platform-tools/adb si no está en PATH
PKG="${PKG:-com.gathin.tvhome}"
COMP="$PKG/com.gathin.tvhome.MainActivity"
FILES="//sdcard/Android/data/$PKG/files"
OUT="${OUT:-bench-results/native-heap-audit-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT"
export MSYS_NO_PATHCONV=1

adb() { "$ADB" -s "$SERIAL" "$@"; }

lean='{"animations":false,"reduced_motion":true,"focus_overshoot":false,"cinematic_home":false,"cinematic_art":false,"cinematic_scrim":false,"cinematic_dots":false,"app_glow":false,"ambient_glow":false,"fade_rows":false,"clock_glow":false,"wallpaper":false,"sky_intensity":0,"always_show_names":true}'

measure() { # $1 label
    adb shell input keyevent 224 > /dev/null 2>&1   # wake
    sleep 1
    adb shell "am force-stop $PKG" > /dev/null 2>&1
    sleep 3
    adb shell "am start -a android.intent.action.MAIN -c android.intent.category.HOME" > /dev/null 2>&1
    sleep 12
    adb shell "dumpsys meminfo $PKG" | tr -d '\r' | grep -E "^ +(Native Heap|TOTAL PSS|TOTAL SWAP PSS)" | head -3 > "$OUT/meminfo-$1.txt"
    N=$(tr -d '\r' < "$OUT/meminfo-$1.txt" | sed -n 's/^ *Native Heap \+\([0-9]*\) .*/\1/p')
    T=$(tr -d '\r' < "$OUT/meminfo-$1.txt" | sed -n 's/^ *TOTAL PSS: \+\([0-9]*\).*/\1/p')
    echo "$1: NativeHeap=${N}KB TotalPSS=${T}KB" | tee -a "$OUT/summary.txt"
    sleep 4
}

echo "== preparación: Home = $PKG =="
adb shell "cmd package set-home-activity $COMP" 2>&1 | tr -d '\r' | tail -1
adb shell "am start -a android.intent.action.MAIN -c android.intent.category.HOME" > /dev/null 2>&1
sleep 10

echo "== N0 lean (baseline) =="
printf '%s' "$lean" > "$OUT/config-N0.json"
adb push "$OUT/config-N0.json" "$FILES/config.json" > /dev/null 2>&1
measure N0

echo "== N1 + wallpaper (RGB_565) =="
mkdir -p "$OUT/wallpapers"
cp "${WALLPAPER:-themes/aurora/wallpaper.jpg}" "$OUT/wallpapers/aurora.jpg"
adb push "$OUT/wallpapers/aurora.jpg" "$FILES/wallpapers/aurora.jpg" > /dev/null 2>&1 || adb shell "mkdir -p ${FILES}/wallpapers" && adb push "$OUT/wallpapers/aurora.jpg" "$FILES/wallpapers/aurora.jpg" > /dev/null 2>&1
printf '%s' "$lean" | sed 's/"wallpaper":false/"wallpaper":true/' > "$OUT/config-N1.json"
adb push "$OUT/config-N1.json" "$FILES/config.json" > /dev/null 2>&1
measure N1

echo "== N2 wallpaper true_color (ARGB_8888) =="
printf '%s' "$lean" | sed 's/"wallpaper":false/"wallpaper":true/' | sed '2s/$//' > /dev/null
python - "$OUT" <<'PY'
import json,sys,os
p=os.path.join(sys.argv[1],'config-N1.json')
d=json.load(open(p)); d['wallpaper_true_color']=True
json.dump(d,open(os.path.join(sys.argv[1],'config-N2.json'),'w'))
PY
adb push "$OUT/config-N2.json" "$FILES/config.json" > /dev/null 2>&1
measure N2

echo "== N3 uniform_tiles=true (icon-only, sin banners) =="
printf '%s' "$lean" > "$OUT/config-N3.json"
python - "$OUT" <<'PY'
import json,sys,os
p=os.path.join(sys.argv[1],'config-N3.json')
d=json.load(open(p)); d['uniform_tiles']=True
json.dump(d,open(os.path.join(sys.argv[1],'config-N3.json'),'w'))
PY
adb push "$OUT/config-N3.json" "$FILES/config.json" > /dev/null 2>&1
adb shell "rm ${FILES}/wallpapers/aurora.jpg" > /dev/null 2>&1   # sin wallpaper en esta corrida
measure N3

echo "== N4 cinematic_home=true (shelf + HeroArt) =="
python - "$OUT" <<'PY'
import json,sys,os
p=os.path.join(sys.argv[1],'config-N3.json')
d=json.load(open(p)); d['uniform_tiles']=False; d['cinematic_home']=True; d['cinematic_art']=True
json.dump(d,open(os.path.join(sys.argv[1],'config-N4.json'),'w'))
PY
adb push "$OUT/config-N4.json" "$FILES/config.json" > /dev/null 2>&1
measure N4

echo "== restauración =="
adb shell "cmd package set-home-activity ${RESTORE_HOME:-br.eddy.tvhome}/com.gathin.tvhome.MainActivity" 2>&1 | tr -d '\r' | tail -1
adb shell "am force-stop $PKG; am start -a android.intent.action.MAIN -c android.intent.category.HOME" > /dev/null 2>&1
sleep 5
adb shell "cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME" | tr -d '\r' | grep packageName | head -1
adb shell "rm ${FILES}/config.json" > /dev/null 2>&1   # queda sin overlay (lean se puede reaplicar desde contrib/)
echo "evidencia: $OUT"
