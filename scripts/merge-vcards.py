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
"""
import csv
import re
import sys
from pathlib import Path

NAME_HINTS = ("display name", "name", "vorname", "first", "nachname", "last")
PHONE_HINTS = ("phone", "telefon", "mobile", "handy", "tel")


def escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")


def from_vcf(path: Path) -> tuple[str, int]:
    text = path.read_text(encoding="utf-8", errors="replace")
    count = len(re.findall(r"^BEGIN:VCARD", text, re.MULTILINE | re.IGNORECASE))
    if not text.endswith("\n"):
        text += "\r\n"
    return text, count


def from_csv(path: Path) -> tuple[str, int]:
    out, count = [], 0
    with path.open(newline="", encoding="utf-8", errors="replace") as fh:
        reader = csv.DictReader(fh)
        if not reader.fieldnames:
            return "", 0
        lower = {f: (f or "").strip().lower() for f in reader.fieldnames}
        name_cols = [f for f, l in lower.items() if any(h in l for h in NAME_HINTS)]
        phone_cols = [f for f, l in lower.items() if any(h in l for h in PHONE_HINTS)]
        if not name_cols or not phone_cols:
            print(f"    {path.name}: keine Namens-/Telefonspalten erkannt "
                  f"(Spalten: {', '.join(reader.fieldnames)})", file=sys.stderr)
            return "", 0
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
                continue
            out.append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
            out.append(f"FN:{escape(name)}\r\n")
            for n in numbers:
                out.append(f"TEL;TYPE=CELL:{n}\r\n")
            out.append("END:VCARD\r\n")
            count += 1
    return "".join(out), count


def main() -> int:
    if len(sys.argv) < 3:
        print("Nutzung: merge-vcards.py <ausgabe.vcf> <datei1> [datei2 ...]", file=sys.stderr)
        print("         Eingaben: .vcf oder .csv (Yahoo-/Google-Export)", file=sys.stderr)
        return 1
    out_path = Path(sys.argv[1])
    parts, total = [], 0
    for arg in sys.argv[2:]:
        path = Path(arg)
        if not path.is_file():
            print(f"    übersprungen (nicht gefunden): {arg}", file=sys.stderr)
            continue
        if path.suffix.lower() == ".csv":
            text, n = from_csv(path)
        else:
            text, n = from_vcf(path)
        print(f"    {path.name}: {n} Kontakte")
        parts.append(text)
        total += n
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text("".join(parts), encoding="utf-8")
    print(f"    → {out_path}  ({total} Kontakte gesamt)")
    if total == 0:
        print("    ACHTUNG: nichts zusammengeführt.", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
