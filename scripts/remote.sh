#!/usr/bin/env bash
# Fernwartung des Lina-Tablets über Tailscale + ADB (WLAN-Debugging).
# Voraussetzungen: siehe WARTUNG.md. Tablet-IP per Env setzen:
#   export LINA_TABLET_IP=100.x.y.z    (Tailscale-IP des Tablets)
set -euo pipefail

# adb aus dem SDK (sdk.dir in local.properties), falls nicht im PATH
if ! command -v adb >/dev/null 2>&1; then
  SDK_DIR="$(grep '^sdk.dir=' "$(dirname "$0")/../local.properties" 2>/dev/null | cut -d= -f2 || true)"
  [ -n "${SDK_DIR:-}" ] && export PATH="$PATH:$SDK_DIR/platform-tools"
fi
command -v adb >/dev/null 2>&1 || { echo "adb nicht gefunden" >&2; exit 1; }

IP="${LINA_TABLET_IP:-}"
PORT="${LINA_TABLET_PORT:-5555}"
APK="app/build/outputs/apk/debug/app-debug.apk"
FILES="/sdcard/Android/data/dev.lina/files"

die() { echo "Fehler: $*" >&2; exit 1; }
need_ip() { [ -n "$IP" ] || die "LINA_TABLET_IP nicht gesetzt (Tailscale-IP des Tablets)"; }

case "${1:-help}" in
  connect)
    need_ip
    adb connect "$IP:$PORT"
    adb devices ;;
  status)
    adb shell "uptime; echo; dumpsys battery | grep -E 'level|AC powered'; echo; \
      dumpsys activity services dev.lina | grep -E 'WakeWordService|app=' | head -5" ;;
  logs)
    adb logcat -s LinaLauncher:D WhisperStt:D ClaudeConversation:D VoiceOnboarding:D WakeWord:D ;;
  deploy)
    JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}" ./gradlew assembleDebug -q
    adb install -r "$APK"
    echo "Installiert. App neu starten:"
    adb shell am force-stop dev.lina
    adb shell monkey -p dev.lina -c android.intent.category.LAUNCHER 1 > /dev/null
    echo "Fertig." ;;
  pull-onboarding)
    mkdir -p tablet-data
    adb pull "$FILES/onboarding" tablet-data/ && echo "→ tablet-data/onboarding/" ;;
  pull-recordings)
    mkdir -p tablet-data
    adb pull "$FILES" tablet-data/files/ && echo "→ tablet-data/files/" ;;
  screen)
    command -v scrcpy >/dev/null || die "scrcpy nicht installiert (brew install scrcpy)"
    scrcpy ;;
  restart-app)
    adb shell am force-stop dev.lina
    adb shell monkey -p dev.lina -c android.intent.category.LAUNCHER 1 > /dev/null
    echo "Lina neu gestartet." ;;
  say)
    shift; [ $# -gt 0 ] || die "Text fehlt: ./scripts/remote.sh say 'was gibt es neues'"
    exec ./scripts/say.sh "$@" ;;
  help|*)
    cat <<'EOF'
Lina-Fernwartung – Befehle:
  connect           Tablet über Tailscale verbinden (LINA_TABLET_IP setzen)
  status            Uptime, Akku/Netzteil, Lina-Service-Status
  logs              Live-Logs der Lina-Komponenten
  deploy            Baut Debug-APK, installiert sie remote, startet Lina neu
  pull-onboarding   Einrichtungs-Aufnahmen + Antworten abholen
  pull-recordings   Alle App-Dateien (Aufnahmen etc.) abholen
  screen            Bildschirm spiegeln (scrcpy)
  restart-app       Lina neu starten
  say "<text>"      Sprachbefehl simulieren (via say.sh)
EOF
    ;;
esac
