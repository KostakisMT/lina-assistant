#!/usr/bin/env bash
# Vollbackup des Lina-Testtablets, bevor ein neuer Build installiert wird.
#
# Sichert nach tablet-data/backup-<zeitstempel>/:
#   base.apk            – der aktuell INSTALLIERTE Build (nicht der lokal gebaute)
#   app-data.tar        – interne App-Daten (nur bei debuggable-Build, via run-as)
#   external-files/     – Aufnahmen, Onboarding-Sessions, Dokumentfotos
#   contacts.csv        – System-Kontakte VOR einem SIM-/vCard-Import (ADR-029)
#   meta.txt            – Paket-, Geräte- und Berechtigungsstand
#
# Wichtig: app-data.tar enthält EncryptedSharedPreferences. Deren Schlüssel
# liegen im Android-Keystore des Geräts und werden bei Deinstallation gelöscht –
# ein Restore auf ein FRESHES Gerät/nach Deinstallation kann diese Dateien nicht
# entschlüsseln. Das Backup ist eine Sicherung zur Einsicht und für den Fall
# "Update in place ging schief", kein vollwertiges Migrationsformat.
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v adb >/dev/null 2>&1; then
  SDK_DIR="$(grep '^sdk.dir=' local.properties 2>/dev/null | cut -d= -f2 || true)"
  [ -n "${SDK_DIR:-}" ] && export PATH="$PATH:$SDK_DIR/platform-tools"
fi
command -v adb >/dev/null 2>&1 || { echo "adb nicht gefunden" >&2; exit 1; }

PKG="dev.lina"
STAMP="$(date +%Y-%m-%d_%H%M)"
OUT="tablet-data/backup-$STAMP"
EXT="/sdcard/Android/data/$PKG/files"

adb get-state >/dev/null 2>&1 || { echo "Kein Gerät verbunden (adb devices)" >&2; exit 1; }
mkdir -p "$OUT"
echo "→ $OUT"

echo "[1/5] Metadaten"
{
  echo "# Lina-Gerätebackup $STAMP"
  echo
  echo "## Gerät"
  for p in ro.product.model ro.product.manufacturer ro.build.version.release ro.build.version.sdk ro.build.display.id ro.serialno; do
    echo "$p = $(adb shell getprop $p | tr -d '\r')"
  done
  echo
  echo "## Paket $PKG"
  adb shell dumpsys package "$PKG" | sed -n '/^Packages:/,/^$/p' | tr -d '\r'
  echo
  echo "## Erteilte Berechtigungen"
  adb shell dumpsys package "$PKG" | grep -E 'granted=true' | tr -d '\r' | sort -u
  echo
  echo "## Accessibility / Battery"
  echo "enabled_accessibility_services = $(adb shell settings get secure enabled_accessibility_services | tr -d '\r')"
  echo "accessibility_enabled = $(adb shell settings get secure accessibility_enabled | tr -d '\r')"
  adb shell dumpsys deviceidle whitelist 2>/dev/null | grep -i lina | tr -d '\r' || echo "(nicht in Battery-Whitelist)"
} > "$OUT/meta.txt" 2>&1

echo "[2/5] Installierte APK"
APK_PATHS="$(adb shell pm path "$PKG" | tr -d '\r' | sed 's/^package://')"
i=0
for p in $APK_PATHS; do
  name="base.apk"; [ $i -gt 0 ] && name="split-$i.apk"
  adb pull "$p" "$OUT/$name" >/dev/null && echo "    $name"
  i=$((i+1))
done

echo "[3/5] Externe Dateien (Aufnahmen, Onboarding, Fotos)"
if adb shell "[ -d $EXT ]" 2>/dev/null; then
  adb pull "$EXT" "$OUT/external-files" >/dev/null 2>&1 && echo "    ok" || echo "    (leer/nicht lesbar)"
else
  echo "    (nicht vorhanden)"
fi

echo "[4/5] Interne App-Daten (run-as, nur debuggable)"
if adb shell run-as "$PKG" id >/dev/null 2>&1; then
  # Modelle (piper/whisper/vosk) werden beim Start aus den Assets entpackt und
  # sind reproduzierbar – sie würden das Backup um ~600 MB aufblähen.
  adb exec-out run-as "$PKG" tar cf - \
      --exclude=./cache --exclude=./code_cache \
      --exclude=./files/piper --exclude=./files/whisper --exclude=./files/vosk-model-small-de \
      . > "$OUT/app-data.tar" 2>/dev/null \
    && echo "    $(du -h "$OUT/app-data.tar" | cut -f1)" \
    || echo "    FEHLER beim tar"
else
  echo "    übersprungen (Build nicht debuggable)"
fi

echo "[5/5] System-Kontakte (Stand VOR einem Import)"
adb shell content query --uri content://com.android.contacts/data \
  --projection display_name:mimetype:data1 2>/dev/null | tr -d '\r' > "$OUT/contacts.csv" || true
echo "    $(wc -l < "$OUT/contacts.csv" | tr -d ' ') Zeilen"

echo
echo "Fertig: $OUT"
du -sh "$OUT"
