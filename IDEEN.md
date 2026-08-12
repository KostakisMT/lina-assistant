# Ideen & Bausteine

Feature-Ideen für Lina und freie Bausteine, die dafür in Frage kommen.
Ergänzung zu [TODO.md](TODO.md) (konkrete Aufgaben) – hier steht, *was* wir
warum bauen könnten und *womit*. Beiträge willkommen.

Bewertung: **Nutzen** = Alltagsgewinn für blinde/sehbehinderte Nutzer:innen,
**Aufwand** = grobe Einschätzung.

---

## Warum diese Liste existiert

Assistenztechnik für blinde Menschen ist ein Markt mit teuren Speziallösungen
und wenig offener Software. Bei der Recherche fiel auf: Für mehrere naheliegende
Funktionen existiert **keine gepflegte quelloffene Umsetzung für Android** –
etwa DAISY-Hörbücher oder Farberkennung. Lina kann diese Lücken schließen, und
die Bausteine sollen wiederverwendbar sein.

---

## Umgesetzt

Ehemals hier unter „Geplant" gelistet, inzwischen gebaut – Details siehe
TODO.md/CHANGELOG.md.

### ~~DAISY-Hörbücher~~ ✅ 2026-07-20 (ADR-019)
War hier mit Priorität hoch gelistet. `DaisyParser`/`DaisyRepository` lesen
`ncc.html` + SMIL wie unten beschrieben, per `XmlPullParser` wie bei RSS.
Offen bleibt nur ein Praxistest mit einem echten Hörbücherei-Buch (Struktur
variiert je Produktionsstelle) – siehe TODO.md.

### ~~Erinnerungen & Wecker~~ ✅ 2026-07-20
War hier mit Priorität hoch gelistet. Vollständig offline über `AlarmManager`,
deutsches Zeitparsing, Claude-Fallback für verstümmelte Eingaben. Kleinere
Restpunkte (Löschen einzelner Erinnerungen per Sprache) siehe TODO.md.

### ~~Helfer-Anruf (Be My Eyes)~~ ✅ 2026-08-12 (ADR-033)
Live-Videoanruf zu einem sehenden Freiwilligen für alles, was das
Dokument-Vorlesen nicht abdeckt (Objekte, Umgebung, Rückfragen in Echtzeit).
Be My Eyes hat keine offene Anruf-API – `HelperCallLauncher` öffnet
stattdessen nur die App (App-Handoff), ein Tap auf „Call a Volunteer" bleibt
bei der Nutzer:in. Restpunkt: Gerätetest, siehe TODO.md.

---

## Geplant

*(aktuell keine Einträge mit Priorität hoch – neue Ideen kommen ins Backlog
unten, bis sie konkret genug für einen eigenen Plan sind)*

---

## Backlog

### Farberkennung
**Nutzen:** hoch (bei Farbenblindheit) · **Aufwand:** klein

„Welche Farbe hat dieses Hemd?" Braucht kein fremdes Repo: Kamerabild →
Pixelbereich mitteln → RGB → Farbnamen-Tabelle (deutsch). Für Menschen mit
Farbenblindheit *und* Sehverlust doppelt relevant. Kommerzielle Apps existieren
(ColorVisor, ColorFast), eine quelloffene Entsprechung fehlt.

### Produkte per Barcode erkennen
**Nutzen:** hoch · **Aufwand:** mittel

„Was ist das für eine Dose?" – Barcode scannen, Produktnamen vorlesen.
[Open Food Facts](https://world.openfoodfacts.org/data) bietet eine freie API
(ODbL, keine Rate-Limits bei vernünftiger Nutzung) mit großem deutschem
Bestand. Barcode-Erkennung on-device über ML Kit.

### Offline-OCR als Rückfallebene
**Nutzen:** mittel · **Aufwand:** mittel

Das Dokument-Vorlesen (siehe [CLAUDE.md](CLAUDE.md)) braucht Internet. Ein
Offline-Pfad über [ML Kit Text Recognition](https://developers.google.com/ml-kit)
(on-device, kostenlos) oder [Tesseract4Android](https://github.com/tesseract-ocr/tesseract)
(Apache 2.0) liefert zwar nur rohen Text ohne Relevanzfilterung – aber besser
als „geht gerade nicht".

### Rückfragen zum Dokument
**Nutzen:** hoch · **Aufwand:** mittel

Nach dem Vorlesen weiterfragen: „Wann ist die Frist?", „Lies den zweiten
Absatz". Setzt voraus, das Bild für ein paar Folgeturns im Kontext zu halten.

### Briefe beantworten
**Nutzen:** hoch · **Aufwand:** groß

Brief vorlesen → Antwort diktieren → als E-Mail versenden oder als Brief
formatieren. Schließt den Kreis zur schriftlichen Teilhabe.

### Podcasts
**Nutzen:** mittel · **Aufwand:** klein

Abonnements über offene RSS-Feeds – dieselbe Technik wie die Nachrichten,
ohne Anbieterbindung.

### Weitere Ideen

| Idee | Nutzen | Aufwand |
|---|---|---|
| Einkaufsliste per Sprache (offline) | mittel | klein |
| Verpasste Anrufe vorlesen, Anrufbeantworter transkribieren | mittel | mittel |
| ~~Lautstärke per Sprachbefehl~~ | – | ✅ umgesetzt 2026-07-26 (Stufen, Prozent, Stummschalten) |
| Sprechtempo per Sprachbefehl | mittel | klein |
| Notruf-Schnellwahl („Ruf Hilfe") | hoch | klein |
| Displays ablesen (Waschmaschine, Herd, Waage) per Kamera | mittel | klein* |
| Geldscheine erkennen | mittel | klein* |
| Englische Version (i18n der Sprachpipeline) | hoch | mittel |

\* nutzt die bereits vorhandene Kamera- und Vision-Anbindung

---

## Bausteine im Blick

| Baustein | Lizenz | Wofür |
|---|---|---|
| [DAISY 2.02](https://daisy.org/activities/standards/daisy/daisy-2/daisy-format-2-02-specification/) | offener Standard | Hörbücher der Blindenbüchereien |
| [Open Food Facts](https://world.openfoodfacts.org/data) | ODbL (Daten) | Produkterkennung |
| [Tesseract4Android](https://github.com/tesseract-ocr/tesseract) | Apache 2.0 | Offline-OCR |
| ML Kit (Text, Barcode) | proprietär, kostenlos, on-device | OCR, Barcodes |
| [LibriVox](https://librivox.org/) | Public Domain | Hörbücher (bereits integriert) |

**Nicht empfehlenswert:** Die zahlreichen „Blind Helper"-Apps auf GitHub sind
überwiegend ungepflegte Studien- und Hackathon-Projekte ohne belastbare
Grundlage.

---

## Mitmachen

Eine Idee aufgreifen? Gerne ein Issue aufmachen. Besonders willkommen sind
Rückmeldungen von Menschen mit eigener Seheinschränkungs-Erfahrung – welche
dieser Funktionen im Alltag wirklich zählen, wissen sie am besten.
