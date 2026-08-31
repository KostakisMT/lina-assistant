#!/usr/bin/env bash
# Holt Kontakte von einem per USB angeschlossenen iPhone/iPad und schiebt sie
# als vCard-Datei auf das Lina-Tablet. Dort werden sie mit dem Sprachbefehl
# "Kontakte aus einer Datei importieren" übernommen (ADR-029, VCardParser).
#
# WARUM DER UMWEG ÜBER EIN BACKUP: iOS gibt Kontakte über USB nicht direkt
# heraus, und die Kontakte-App auf dem Gerät kann nicht "alle exportieren".
# Der einzige zuverlässige lokale Weg ist ein Gerätebackup, aus dem gezielt
# die AddressBook-Datenbank gelesen wird.
#
# DATENSCHUTZ – bitte vorher mit der Nutzer:in besprechen:
# Ein iOS-Backup enthält ALLES (Fotos, Nachrichten, Gesundheitsdaten). Dieses
# Skript entpackt daraus ausschließlich Kontakte und LÖSCHT das Backup danach
# wieder. Trotzdem liegt es währenddessen auf dem Betreuer-Rechner. Wer das
# nicht möchte, nimmt den iCloud-Weg (siehe --hilfe).
set -euo pipefail

cd "$(dirname "$0")/.."

WORK="$(mktemp -d "${TMPDIR:-/tmp}/lina-ipad.XXXXXX")"
OUT="tablet-data/ipad-kontakte-$(date +%Y-%m-%d_%H%M).vcf"
KEEP_BACKUP=0
PUSH=1

cleanup() {
  if [ "$KEEP_BACKUP" -eq 0 ]; then
    rm -rf "$WORK"
    echo "  Backup gelöscht: $WORK"
  else
    echo "  Backup BEHALTEN: $WORK  (enthält alle Gerätedaten – selbst löschen!)"
  fi
}
trap cleanup EXIT

hilfe() {
  cat <<'EOF'
Nutzung: ./scripts/ipad-import.sh [--backup-behalten] [--kein-push]

  --backup-behalten  Backup nicht löschen (Fehlersuche; enthält ALLE Gerätedaten)
  --kein-push        vCard nur lokal erzeugen, nicht aufs Tablet schieben

Ablauf:
  1. iPad per USB an den Mac
  2. Auf dem iPad "Diesem Computer vertrauen?" mit JA bestätigen (Code eingeben)
  3. Dieses Skript starten
  4. Am Tablet sagen: "Kontakte aus einer Datei importieren"
     und im Dateidialog die abgelegte .vcf wählen

Wenn das Backup verschlüsselt ist:
  Finder → iPad → "Lokales Backup verschlüsseln" abwählen (braucht das
  bestehende Passwort). Alternativ der iCloud-Weg, ganz ohne dieses Skript:
  icloud.com/contacts → Alle auswählen → Zahnrad → "vCard exportieren".
  Die Anmeldung macht die Nutzer:in selbst.
EOF
}

for arg in "$@"; do
  case "$arg" in
    --backup-behalten) KEEP_BACKUP=1 ;;
    --kein-push) PUSH=0 ;;
    --hilfe|-h|--help) hilfe; exit 0 ;;
    *) echo "Unbekannte Option: $arg" >&2; hilfe; exit 1 ;;
  esac
done

command -v idevice_id >/dev/null || {
  echo "libimobiledevice fehlt. Installieren mit:  brew install libimobiledevice" >&2
  exit 1
}
command -v sqlite3 >/dev/null || { echo "sqlite3 fehlt" >&2; exit 1; }

echo "[1/5] Gerät suchen"
UDID="$(idevice_id -l | head -1)"
[ -n "$UDID" ] || {
  echo "Kein iOS-Gerät gefunden. USB prüfen und auf dem iPad" >&2
  echo "\"Diesem Computer vertrauen?\" bestätigen." >&2
  exit 1
}
NAME="$(ideviceinfo -u "$UDID" -k DeviceName 2>/dev/null || echo '?')"
echo "    $NAME ($UDID)"

echo "[2/5] Kopplung"
if ! idevicepair -u "$UDID" validate >/dev/null 2>&1; then
  echo "    Bitte JETZT auf dem iPad \"Vertrauen\" tippen und den Code eingeben…"
  idevicepair -u "$UDID" pair
fi

echo "[3/5] Backup ziehen (dauert je nach Gerät einige Minuten)"
idevicebackup2 -u "$UDID" backup --full "$WORK" 2>&1 | tail -3

MANIFEST="$WORK/$UDID/Manifest.db"
[ -f "$MANIFEST" ] || { echo "Manifest.db fehlt – Backup unvollständig?" >&2; exit 1; }

if ! sqlite3 "$MANIFEST" "SELECT count(*) FROM Files;" >/dev/null 2>&1; then
  echo "" >&2
  echo "Das Backup ist VERSCHLÜSSELT – die Kontakte sind so nicht lesbar." >&2
  echo "Finder → iPad → \"Lokales Backup verschlüsseln\" abwählen," >&2
  echo "oder den iCloud-Weg nehmen (./scripts/ipad-import.sh --hilfe)." >&2
  exit 2
fi

echo "[4/5] Kontakte extrahieren"
FILEID="$(sqlite3 "$MANIFEST" \
  "SELECT fileID FROM Files WHERE domain='HomeDomain' AND relativePath='Library/AddressBook/AddressBook.sqlitedb' LIMIT 1;")"
[ -n "$FILEID" ] || { echo "AddressBook nicht im Backup gefunden" >&2; exit 1; }
DB="$WORK/$UDID/${FILEID:0:2}/$FILEID"
[ -f "$DB" ] || { echo "AddressBook-Datei fehlt: $DB" >&2; exit 1; }

mkdir -p tablet-data
python3 scripts/ipad_contacts_to_vcard.py "$DB" "$OUT"

echo "[5/5] Auf das Tablet schieben"
if [ "$PUSH" -eq 1 ]; then
  if ! command -v adb >/dev/null 2>&1; then
    SDK_DIR="$(grep '^sdk.dir=' local.properties 2>/dev/null | cut -d= -f2 || true)"
    [ -n "${SDK_DIR:-}" ] && export PATH="$PATH:$SDK_DIR/platform-tools"
  fi
  if adb get-state >/dev/null 2>&1; then
    adb push "$OUT" "/sdcard/Download/$(basename "$OUT")" >/dev/null
    echo "    → /sdcard/Download/$(basename "$OUT")"
    echo ""
    echo "Jetzt am Tablet sagen: \"Kontakte aus einer Datei importieren\""
    echo "und im Dateidialog $(basename "$OUT") auswählen."
  else
    echo "    Kein Tablet verbunden – vCard liegt nur lokal: $OUT"
  fi
else
  echo "    übersprungen (--kein-push). Datei: $OUT"
fi
