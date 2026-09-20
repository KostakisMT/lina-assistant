#!/usr/bin/env python3
"""Mehrere Kontakt-Exporte zu EINER vCard-Datei fuer Linas Import zusammenfassen.

Hintergrund: Auf einem iPad koennen Kontakte aus mehreren Accounts kommen
(iCloud, Yahoo, Google, lokal). Der iCloud-Export enthaelt nur die
iCloud-Kontakte -- Yahoo-Kontakte liegen auf deren CardDAV-Server und fehlen
darin stillschweigend. Deshalb jeden Account einzeln exportieren und hier
zusammenfuehren.

Nimmt .vcf (einfach aneinanderhaengen) und .csv (Yahoo/Google-Export,
Spalten werden anhand ihrer Ueberschriften erraten). Ausgabe ist vCard 3.0
in UTF-8 ohne Encoding-Parameter -- passend zu Linas VCardParser, der
QUOTED-PRINTABLE ausdruecklich nicht dekodiert.

Dubletten werden hier NICHT entfernt: das macht ContactDedup auf dem Gerät
per normalisierter Telefonnummer, und zwar sowohl gegen den Bestand als auch
innerhalb des Imports.

Kontakte ohne Telefonnummer werden aus CSV nicht uebernommen (Lina braucht die
Nummer zum Anrufen) und in .vcf nur gemeldet, nicht entfernt. Beides wird
gezaehlt und angezeigt -- stilles Wegwerfen ist hier der teure Fehler.

Rueckgabewerte:
  0  alles gut
  2  gar nichts zusammengefuehrt
  3  mindestens eine Eingabedatei lieferte 0 Kontakte (Export kaputt?)
  4  mindestens eine Eingabedatei wurde nicht gefunden
Die Ausgabedatei wird auch bei 2/3/4 geschrieben.
"""
import csv
import re
import sys
from pathlib import Path

NAME_HINTS = ("display name", "name", "vorname", "first", "nachname", "last")
PHONE_HINTS = ("phone", "telefon", "mobile", "handy", "tel")
# Yahoo benennt seine Telefonspalten teilweise ohne das Wort "Phone"
# (Layout: Phone,Home,Work,Pager,Fax,Mobile,Other). Beim Klienten lagen ALLE
# Nummern in "Other" -- mit blosser Stichwortsuche fiel der komplette Export
# durch. Diese Namen zaehlen deshalb bei EXAKTER Uebereinstimmung als
# Telefonspalte; exakt deshalb, damit "Home Email" und "Home Address"/
# "Other City" nicht mitgefangen werden.
PHONE_COLS_EXACT = ("home", "work", "pager", "fax", "other")


def escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")


def from_vcf(path: Path) -> tuple[str, int, int]:
    text = path.read_text(encoding="utf-8", errors="replace")
    count = len(re.findall(r"^BEGIN:VCARD", text, re.MULTILINE | re.IGNORECASE))
    # Karten ohne TEL werden mit uebernommen, sind fuer "Ruf X an" aber wertlos.
    # Nur melden, nicht verwerfen -- eine Adresse kann trotzdem gewollt sein.
    cards = re.split(r"^BEGIN:VCARD", text, flags=re.MULTILINE | re.IGNORECASE)[1:]
    # Gruppen-Praefix beachten: iOS schreibt "item1.TEL". Genau wie in
    # VCardParser.parseBlock(), sonst zaehlt man Karten als nummernlos,
    # die das Geraet sehr wohl importiert.
    ohne_tel = sum(
        1 for c in cards
        if not re.search(r"^(?:[A-Za-z0-9-]+\.)?TEL", c, re.MULTILINE | re.IGNORECASE)
    )
    if not text.endswith("\n"):
        text += "\r\n"
    return text, count, ohne_tel


def from_csv(path: Path) -> tuple[str, int, int]:
    out, count, verworfen = [], 0, 0
    with path.open(newline="", encoding="utf-8", errors="replace") as fh:
        reader = csv.DictReader(fh)
        if not reader.fieldnames:
            return "", 0, 0
        lower = {f: (f or "").strip().lower() for f in reader.fieldnames}
        name_cols = [f for f, l in lower.items() if any(h in l for h in NAME_HINTS)]
        phone_cols = [
            f for f, l in lower.items()
            if any(h in l for h in PHONE_HINTS) or l in PHONE_COLS_EXACT
        ]
        if not name_cols or not phone_cols:
            print(f"    {path.name}: keine Namens-/Telefonspalten erkannt "
                  f"(Spalten: {', '.join(reader.fieldnames)})", file=sys.stderr)
            return "", 0, 0
        for row in reader:
            name = " ".join(
                (row.get(c) or "").strip() for c in name_cols if (row.get(c) or "").strip()
            ).strip()
            numbers = []
            for c in phone_cols:
                raw = (row.get(c) or "").strip()
                cleaned = re.sub(r"[^\d+]", "", raw)
                if cleaned and cleaned not in numbers:
                    numbers.append(cleaned)
            if not name or not numbers:
                # Zeile existiert, taugt aber nicht zum Anrufen -- gezaehlt, nicht verschwiegen.
                if name or numbers:
                    verworfen += 1
                continue
            out.append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
            out.append(f"FN:{escape(name)}\r\n")
            for n in numbers:
                out.append(f"TEL;TYPE=CELL:{n}\r\n")
            out.append("END:VCARD\r\n")
            count += 1
    return "".join(out), count, verworfen


def main() -> int:
    if len(sys.argv) < 3:
        print("Nutzung: merge-vcards.py <ausgabe.vcf> <datei1> [datei2 ...]", file=sys.stderr)
        print("         Eingaben: .vcf oder .csv (Yahoo-/Google-Export)", file=sys.stderr)
        return 1
    out_path = Path(sys.argv[1])
    parts, total = [], 0
    leer: list[str] = []      # Eingaben, die 0 Kontakte beigesteuert haben
    fehlend: list[str] = []   # gar nicht erst gefunden
    for arg in sys.argv[2:]:
        path = Path(arg)
        if not path.is_file():
            print(f"    übersprungen (nicht gefunden): {arg}", file=sys.stderr)
            fehlend.append(arg)
            continue
        if path.suffix.lower() == ".csv":
            text, n, verworfen = from_csv(path)
            zusatz = f", {verworfen} Zeile(n) ohne Name/Nummer verworfen" if verworfen else ""
        else:
            text, n, ohne_tel = from_vcf(path)
            zusatz = f", davon {ohne_tel} ohne Telefonnummer" if ohne_tel else ""
        print(f"    {path.name}: {n} Kontakte{zusatz}")
        if n == 0:
            leer.append(path.name)
        parts.append(text)
        total += n
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text("".join(parts), encoding="utf-8")
    print(f"    → {out_path}  ({total} Kontakte gesamt)")

    # Eine Eingabe mit 0 Kontakten ist der teure Fehler: der Yahoo-Export lieferte
    # beim Klienten eine Datei mit blosser Kopfzeile, und ohne Nachzaehlen sah das
    # aus wie Erfolg. Deshalb hier laut werden UND mit != 0 enden, damit es auch
    # in einem Skript auffaellt -- die Ausgabedatei ist trotzdem geschrieben.
    if total == 0:
        print("    ACHTUNG: nichts zusammengeführt.", file=sys.stderr)
        return 2
    if leer:
        print(f"    ACHTUNG: {len(leer)} Eingabe(n) ohne einen einzigen Kontakt: "
              f"{', '.join(leer)}", file=sys.stderr)
        print("    Export vermutlich fehlgeschlagen – bitte erneut ziehen, "
              "bevor importiert wird.", file=sys.stderr)
        return 3
    if fehlend:
        return 4
    return 0


if __name__ == "__main__":
    sys.exit(main())
