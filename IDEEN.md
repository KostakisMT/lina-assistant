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

## Geplant

### DAISY-Hörbücher (Priorität hoch)
**Nutzen:** sehr hoch · **Aufwand:** mittel

Die Blindenhörbüchereien verleihen zehntausende Hörbücher **kostenlos** an
nachweislich sehbehinderte Menschen (z.B. [Norddeutsche
Hörbücherei](https://norddeutsche-hoerbuecherei.de/): ~50.000 Titel;
[WBH Münster](https://www.bsvw.org/die-westdeutsche-hoerbuecherei/): DAISY-CDs
per Post). Der Zugang setzt eine Mitgliedschaft mit Nachweis voraus.

[DAISY 2.02](https://daisy.org/activities/standards/daisy/daisy-2/daisy-format-2-02-specification/)
ist ein offener Standard: eine `ncc.html` mit den Kapiteln als Überschriften,
SMIL-Dateien für die Zuordnung Kapitel → Audiodatei/Zeitbereich, dazu MP3s.
Gut parsebar – Lina nutzt denselben `XmlPullParser` schon für RSS.

**Lücke:** Aktive quelloffene DAISY-Player für Android gibt es praktisch nicht
(vorhandene Projekte sind eingestellt oder laufen nicht auf aktuellen
Android-Versionen). Siehe [Übersicht der DAISY-Software](https://en.wikipedia.org/wiki/List_of_Digital_Accessible_Information_System_software)
und [DAISY Consortium auf GitHub](https://github.com/daisy).

### Erinnerungen & Wecker (Priorität hoch)
**Nutzen:** hoch · **Aufwand:** klein

„Erinnere mich morgen um zehn an den Arzt", Medikamenten-Timer, wiederkehrende
Ansagen. Vollständig offline über `AlarmManager` – kein externes Projekt nötig.
Erfahrungsgemäß eines der meistgenutzten Features bei älteren Nutzer:innen.

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
| Sprechtempo/Lautstärke per Sprachbefehl | mittel | klein |
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
