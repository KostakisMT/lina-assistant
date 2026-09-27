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

# Mit gesetzter IP immer das Tablet übers Netz ansprechen – sonst scheitert
# adb mit "more than one device", sobald zusätzlich ein USB-Gerät hängt.
# ANDROID_SERIAL wird auch von say.sh geerbt.
[ -n "$IP" ] && [ "${1:-}" != connect ] && export ANDROID_SERIAL="$IP:$PORT"

A11Y="dev.lina/dev.lina.core.accessibility.LinaAccessibilityService"
enable_a11y() {
  adb shell settings put secure enabled_accessibility_services "$A11Y"
  adb shell settings put secure accessibility_enabled 1
  if adb shell dumpsys accessibility | grep -q 'label=Lina'; then
    echo "AccessibilityService aktiv."
  else
    echo "WARNUNG: AccessibilityService NICHT aktiv – eingehende Anrufe gehen nicht!" >&2
  fi
}
# Lautstärke per simulierter Taste: "cmd media_session volume --set" trifft auf
# dem Lenovo nur das Default-Device, nicht den Lautsprecher. Einzelne Tasten
# gehen verloren (v.a. während Lina spricht) – daher messen und nachregeln.
speaker_volume() {
  adb shell "dumpsys audio" 2>/dev/null | grep -m1 -A8 '^- STREAM_MUSIC' \
    | sed -n 's/.*(speaker): \([0-9]*\).*/\1/p'
}
set_volume() {
  local target="$1" cur diff key keys try i
  for try in 1 2 3 4 5; do
    cur="$(speaker_volume)"
    [ -n "$cur" ] || { echo "Lautstärke nicht lesbar" >&2; return 1; }
    [ "$cur" -eq "$target" ] && return 0
    diff=$(( target - cur )); key=24
    [ "$diff" -lt 0 ] && { diff=$(( -diff )); key=25; }
    keys=""; for i in $(seq "$diff"); do keys="$keys $key"; done
    adb shell "for k in$keys; do input keyevent \$k; sleep 0.2; done"
    sleep 1
  done
  echo "WARNUNG: Lautstärke steht bei $(speaker_volume) statt $target" >&2
}
debug_input() { adb shell "am broadcast -a dev.lina.DEBUG_INPUT --es text '$1'" > /dev/null; }

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
    # Android schaltet Accessibility-Dienste bei jeder Neuinstallation still ab
    enable_a11y
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
  stop)
    # Lina hört sofort auf zu reden/vorzulesen (wie der Sprachbefehl "Stopp")
    debug_input "stopp"
    echo "Stopp gesendet." ;;
  nacht)
    # Redet nachts dazwischen: Vorlesen stoppen, Schlafmodus, Medien stumm
    debug_input "stopp"
    debug_input "schlafmodus"
    # Schlafmodus setzt selbst 30 % – erst danach stumm schalten
    sleep 3
    set_volume 0
    echo "Lina gestoppt, Schlafmodus an, Medienlautstärke 0." ;;
  laut)
    # Nach "nacht": Lautstärke zurück (Standard 8 von 15), Schlafmodus aus
    debug_input "schlafmodus aus"
    sleep 3
    set_volume "${2:-8}"
    echo "Medienlautstärke ${2:-8}, Schlafmodus aus." ;;
  a11y)
    enable_a11y ;;
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
  restart-app       Lina neu starten (auch wenn sie hängt)
  stop              Lina hört sofort auf zu reden (wie "Stopp")
  nacht             Stopp + Schlafmodus + Medien stumm (redet nachts dazwischen)
  laut [0-15]       Lautstärke zurück (Standard 8) + Schlafmodus aus
  a11y              AccessibilityService wieder einschalten (nach jedem Install!)
  say "<text>"      Sprachbefehl simulieren (via say.sh)
EOF
    ;;
esac
