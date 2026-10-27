#!/usr/bin/env bash
# Collects the "everything else" that a full launcher comparison needs on this TV:
# installed size, process shape, microphone posture and current audio clients.
#
# Read-only: it never installs, starts, stops or reconfigures anything. It is safe to run
# while the TV is in use.
#
# Usage: ./tools/bench-extras.sh <package> [package2 ...]

set -uo pipefail

SERIAL="${SERIAL:-192.168.1.15:5555}"
ADB="${ADB:-adb}"   # poné ADB=/ruta/a/platform-tools/adb si no está en PATH
[ "$#" -ge 1 ] || { echo "usage: $0 <package> [...]" >&2; exit 2; }

adb() { "$ADB" -s "$SERIAL" "$@"; }

for PKG in "$@"; do
    echo "=============== $PKG ==============="

    echo "-- versión y fechas"
    adb shell "dumpsys package $PKG" | tr -d '\r' | grep -E "versionName|versionCode|firstInstallTime|lastUpdateTime" | sed 's/^ *//' | head -4

    echo "-- tamaño instalado (APK en /data/app)"
    PATHONDEV=$(adb shell "pm path $PKG" | tr -d '\r' | sed 's/package://' | head -1)
    if [ -n "$PATHONDEV" ]; then
        APKB=$(adb shell "stat -c %s $PATHONDEV" | tr -d '\r')
        DIRB=$(adb shell "du -sk ${PATHONDEV%/base.apk}" | tr -d '\r' | awk '{print $1}')
        echo "base.apk: ${APKB:-?} bytes | directorio instalado: ${DIRB:-?} KB"
    else
        echo "no instalado"
    fi

    echo "-- proceso y RAM (dumpsys meminfo)"
    adb shell "dumpsys meminfo $PKG" | tr -d '\r' | grep -E "^ *TOTAL PSS:|TOTAL SWAP PSS|Java Heap|Native Heap" | sed 's/^ *//' | head -4
    PID=$(adb shell "pidof $PKG" | tr -d '\r' | awk '{print $1}')
    if [ -n "$PID" ]; then
        THREADS=$(adb shell "ls /proc/$PID/task | wc -l" | tr -d '\r')
        echo "pid: $PID | threads: ${THREADS:-?}"
    else
        echo "proceso no corriendo"
    fi

    echo "-- permisos de micrófono declarados"
    adb shell "dumpsys package $PKG" | tr -d '\r' | grep -E "RECORD_AUDIO|CAPTURE_AUDIO" | sed 's/^ *//' | head -4
    adb shell "dumpsys package $PKG" | tr -d '\r' | grep -c "RECORD_AUDIO" | sed 's/^/coincidencias RECORD_AUDIO: /'

    echo "-- clientes de captura activos ahora"
    adb shell "dumpsys media.audio_flinger" 2>/dev/null | tr -d '\r' | grep -A3 -i "Input thread\|AudioRecord" | head -8
    adb shell "dumpsys audio" 2>/dev/null | tr -d '\r' | grep -iE "recording|record clients|mic" | head -5

    echo "-- estado del mic del firmware"
    adb shell "content query --uri content://atv.hotwordmic/togglestate" 2>/dev/null | tr -d '\r' | head -2
    adb shell "settings get secure enabled_accessibility_services" | tr -d '\r' | sed 's/^/accessibility: /'

    echo "-- wakelocks y servicios del paquete"
    adb shell "dumpsys package $PKG" | tr -d '\r' | grep -E "Service|Receiver" | head -4
    echo
done
