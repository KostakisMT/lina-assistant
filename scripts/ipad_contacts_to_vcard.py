#!/usr/bin/env python3
"""AddressBook.sqlitedb aus einem iOS-Backup -> vCard 3.0.

Aufgerufen von ipad-import.sh. Das Ausgabeformat ist bewusst schlicht
gehalten, weil Linas VCardParser nur FN, N und TEL auswertet und
QUOTED-PRINTABLE ausdruecklich nicht dekodiert (siehe VCardParser.kt) --
deshalb wird hier reines UTF-8 ohne Encoding-Parameter geschrieben.

Nur Kontakte MIT Telefonnummer landen in der Datei: Lina kann mit einem
Namen ohne Nummer nichts anfangen, und jeder ueberfluessige Eintrag
vergroessert nur den Suchraum fuers Fuzzy-Matching und damit die Gefahr
einer Fehlzuordnung bei verhoerten Namen.
"""
import re
import sqlite3
import sys

# ABMultiValue.property == 3 sind Telefonnummern
PHONE_PROPERTY = 3


def escape(value: str) -> str:
    """vCard-Sonderzeichen maskieren (RFC 6350)."""
    return (
        value.replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
    )


def main() -> int:
    if len(sys.argv) != 3:
        print("Nutzung: ipad_contacts_to_vcard.py <AddressBook.sqlitedb> <ausgabe.vcf>", file=sys.stderr)
        return 1
    db_path, out_path = sys.argv[1], sys.argv[2]

    con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    con.text_factory = lambda b: b.decode("utf-8", "replace")
    rows = con.execute(
        """
        SELECT p.ROWID, p.First, p.Last, p.Organization, mv.value
        FROM ABPerson p
        JOIN ABMultiValue mv
          ON mv.record_id = p.ROWID AND mv.property = ?
        WHERE mv.value IS NOT NULL AND trim(mv.value) <> ''
        ORDER BY p.Last, p.First
        """,
        (PHONE_PROPERTY,),
    ).fetchall()
    con.close()

    people = {}
    for rowid, first, last, org, number in rows:
        first = (first or "").strip()
        last = (last or "").strip()
        org = (org or "").strip()
        name = " ".join(p for p in (first, last) if p) or org
        if not name:
            continue
        entry = people.setdefault(rowid, {"first": first, "last": last, "name": name, "numbers": []})
        # Leerzeichen und Bindestriche raus - Lina normalisiert ohnehin,
        # aber so bleibt die Datei auch fuer Menschen lesbar.
        cleaned = re.sub(r"[^\d+]", "", number)
        if cleaned and cleaned not in entry["numbers"]:
            entry["numbers"].append(cleaned)

    with open(out_path, "w", encoding="utf-8") as fh:
        for entry in people.values():
            fh.write("BEGIN:VCARD\r\n")
            fh.write("VERSION:3.0\r\n")
            fh.write(f"N:{escape(entry['last'])};{escape(entry['first'])};;;\r\n")
            fh.write(f"FN:{escape(entry['name'])}\r\n")
            for number in entry["numbers"]:
                fh.write(f"TEL;TYPE=CELL:{number}\r\n")
            fh.write("END:VCARD\r\n")

    total_numbers = sum(len(e["numbers"]) for e in people.values())
    print(f"    {len(people)} Kontakte mit {total_numbers} Nummern -> {out_path}")
    if not people:
        print("    ACHTUNG: keine Kontakte gefunden. Liegen sie evtl. nur in iCloud "
              "und nicht auf dem Geraet? Dann den iCloud-Weg nehmen.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
