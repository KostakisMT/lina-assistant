# SICHERHEIT.md – Sicherheits- und Datenschutzkonzept

> Stand: 2026-07-26. Beschreibt den **tatsächlichen** Zustand, nicht den
> angestrebten. Wo etwas noch fehlt, steht das ausdrücklich dabei – ein
> Konzept, das nur Stärken aufzählt, ist als Prüfgrundlage wertlos.

## Warum der Schutzbedarf hier über dem Üblichen liegt

Lina ist für blinde und stark sehbehinderte Menschen gebaut. Daraus folgen
drei Dinge, die andere Apps nicht in dieser Schärfe haben:

1. **Die Nutzer:innen können Fehler nicht sehen.** Wer nicht erkennt, dass
   ein falscher Name im Display steht oder eine Warnung eingeblendet wird,
   kann auch nicht gegensteuern. Sicherheit muss deshalb im Verhalten
   stecken, nicht in Hinweisen auf dem Bildschirm.
2. **Die Daten sind besonders sensibel.** Lina liest Post vor – Behörden-,
   Arzt- und Bankbriefe eingeschlossen. Sie kennt das Telefonbuch, liest SMS
   und verwaltet Erinnerungen, die oft gesundheitsbezogen sind
   („Tabletten nehmen").
3. **Die Nutzer:innen sind auf Hilfe angewiesen.** Einrichtung und Wartung
   übernehmen Angehörige oder Betreuer:innen. Jede Schutzmaßnahme,
   die eine sehende Person voraussetzt, ist zugleich eine Abhängigkeit.

## Verantwortlichkeit

Datenschutzrechtlich verantwortlich ist derzeit der Entwickler als Privatperson,
der das Projekt vorerst privat trägt (ADR-023). Anthropic ist Auftragsverarbeiter
für die Cloud-Funktionen; ein AVV ist Voraussetzung für den Regelbetrieb und
steht noch aus. In der jetzigen Übergangsphase laufen die Cloud-Funktionen direkt
zum KI-Dienst über einen eigenen API-Key des Entwicklers – der Proxy aus
[ADR-020](DECISIONS.md) mit einer weiteren verarbeitenden Stelle ist ein späterer
Schritt und mit ihm eine mögliche gemeinnützige Trägerschaft. Die Einwilligung in
[WARTUNG.md](WARTUNG.md) beschreibt den aktuellen Weg.

---

## Welche Daten entstehen – und wo sie liegen

| Daten | Ort | Dauer | Verlässt das Gerät? |
|---|---|---|---|
| Kontakte (lesend) | nur im Arbeitsspeicher, bei Bedarf aus `ContactsContract` gelesen | flüchtig | **Namen ja** – siehe unten |
| Kontakte (schreibend, SIM-/vCard-Import) | dauerhaft in `ContactsContract` geschrieben (`WRITE_CONTACTS`, seit 2026-07-26) | **dauerhaft**, wie jeder andere Kontakt am Gerät | nein (Import selbst) |
| SIM-Fingerabdruck (Erkennung neuer Karten) | `EncryptedSharedPreferences` (`ContactImportStore`) | dauerhaft | nein |
| SMS-Inhalte | nur im Arbeitsspeicher | flüchtig | nein |
| Dialoggedächtnis | Arbeitsspeicher, max. 20 Nachrichten | bis Neustart | ja (Konversation) |
| Dokumentfotos (normal) | Arbeitsspeicher | bis Vorlesen endet | ja (Auswertung), nicht gespeichert |
| Dokumentfotos (`testfoto`) | `getExternalFilesDir/docphotos/` | max. 7 Tage (automatische Löschung seit 2026-07-25) | nein |
| Sprachaufnahmen Ersteinrichtung | `getExternalFilesDir/onboarding/` | max. 7 Tage (automatische Löschung seit 2026-07-25) | nein (außer Fernwartung) |
| Nutzerprofil (Anrede, Interessen, Region) | `SharedPreferences` unverschlüsselt | dauerhaft | ja (Teil des System-Prompts) |
| Erinnerungen | `EncryptedSharedPreferences` (AES256-GCM/SIV, seit 2026-07-25) | dauerhaft | nur bei Cloud-Erkennung |
| Hörbuch-Fortschritt | `SharedPreferences` unverschlüsselt | dauerhaft | nein |
| Nachrichten-Cache | `filesDir/news_cache.json` | bis Aktualisierung | nein |

### Was tatsächlich das Gerät verlässt

Nur drei Stellen im Code öffnen Netzwerkverbindungen:

- **`ClaudeConversation`** → Anthropic: der Gesprächstext, Dokumentfotos und –
  als Teil des System-Prompts – **die Namen aus dem Telefonbuch** sowie
  Interessen und Wohnregion. Die Kontaktnamen werden mitgeschickt, damit
  Lina verstümmelte Spracherkennung zuordnen kann („Rumfe Boris an"). Das ist
  eine bewusste Abwägung zugunsten der Erkennungsqualität und gehört
  ausdrücklich in die Aufklärung – Telefonnummern werden **nicht** übertragen.
- **`RssFeedRepository`** → die vier vorkonfigurierten Nachrichtenquellen.
- **`LibrivoxRepository`** → LibriVox, beim Suchen und Streamen von Hörbüchern.

Alles Übrige – Weckwort, Spracherkennung, Sprachausgabe, Kontakte, SMS,
Anrufe, Erinnerungen, lokale Hörbücher – läuft ohne Netz.

### Die Trennlinie

Ohne Internet entfallen genau zwei Funktionen: freie Konversation und
Dokument-Vorlesen. Alle Kernbefehle laufen weiter. Das ist keine Nebenfolge,
sondern Entwurfsprinzip ([ADR-017](DECISIONS.md)) – und zugleich eine
Datenschutzeigenschaft: Wer die Cloud-Funktionen nicht will, verliert nicht
das Gerät.

---

## Technische Maßnahmen

### Vorhanden

- Weckwort, Spracherkennung (Whisper) und Sprachausgabe (Piper) laufen
  vollständig auf dem Gerät. Es gibt keinen Dauer-Upload von Audio.
- Dokumentfotos werden im Regelfall nicht persistiert.
- Der Dialogverlauf ist auf 20 Nachrichten begrenzt und wird nicht gespeichert.
- Kein Nutzerkonto, keine Registrierung, keine Werbe- oder Analyse-SDKs.
- Externe XML-Entities sind in **allen** XML-Parsern abgeschaltet
  (XXE-Schutz), weil sowohl DAISY-Bücher als auch LibriVox-RSS-Feeds aus
  fremden Quellen stammen. Zentral in `core/xml/SecureXml.kt`
  (ADR-035) – `DocumentBuilderFactory` wird nirgends mehr direkt
  aufgerufen. Bis 2026-08-23 galt das nur für den DAISY-Parser; der
  LibriVox-Feed-Parser war offen (behoben, siehe CHANGELOG.md).
- Der Anthropic-Schlüssel liegt nicht im Repository (`local.properties`,
  gitignored).

### Bekannte Lücken

Offen benannt, weil sie den realen Schutz bestimmen:

1. **Der API-Schlüssel wird in die App kompiliert.** Wer die APK
   auseinandernimmt, hat ihn. Für ein einzelnes Testgerät hinnehmbar, für
   Verteilung nicht – deshalb der Proxy in [ADR-020](DECISIONS.md).
   *Wichtigste offene Maßnahme.*
2. **`SharedPreferences` teilweise noch unverschlüsselt.** Erinnerungen
   (seit 2026-07-25) und der SIM-Fingerabdruck (seit 2026-07-26) laufen über
   `EncryptedSharedPreferences`. Nutzerprofil (Anrede/Interessen/Region) und
   Hörbuch-Fortschritt liegen weiterhin im Klartext – geringeres Risiko
   (keine Gesundheits-/Terminbezüge), aber noch offen.
3. ~~**Sprachaufnahmen der Ersteinrichtung bleiben liegen.**~~ Behoben
   2026-07-25/26: `cleanupOldDebugFiles()` löscht `onboarding/`- und
   `docphotos/`-Einträge automatisch nach 7 Tagen bei jedem App-Start.
4. ~~**`testfoto`-Aufnahmen werden nie automatisch gelöscht.**~~ Siehe Punkt 3
   – dieselbe automatische Löschung deckt beide Ordner ab.
5. **Keine Wiederanmeldung.** Wer am Gerät ist, kann alles.
6. **Kontakt-Import kennt keine Widerspruchsprüfung gegen die sprechende
   Person.** Wie bei Anrufen/SMS (siehe „Grenzen" unten) prüft Lina nicht,
   *wer* die SIM-Import-Nachfrage mit "Ja" beantwortet oder den
   Datei-Import-Befehl auslöst – jede Person im Raum kann dauerhaft neue
   Kontakte in die System-Kontakte schreiben (`WRITE_CONTACTS`, seit
   2026-07-26). Entschärfung: nur additiv (kein Löschen/Überschreiben
   bestehender Kontakte), Dedup verhindert Duplikate, aber kein Undo für
   fälschlich importierte Einträge außer manuellem Löschen in der
   Kontakte-App.

---

## Organisatorische Maßnahmen

- **Einwilligung vor Übergabe**, in einfacher Sprache besprochen und
  festgehalten ([WARTUNG.md](WARTUNG.md)). Sie benennt Fernwartung,
  Cloud-Konversation, Sprachaufnahmen und Dokument-Vorlesen einzeln.
- **Fernwartung nur zu Wartungszwecken**, über ein privates Tailnet, nie
  heimliches Mithören. Betreuer:innen sehen dabei den Bildschirm.
- **Quelloffenheit** (Apache 2.0): Jede Verarbeitung ist nachlesbar statt
  zugesichert. Das ist die belastbarste Zusage, die das Projekt machen kann.
- **Architekturentscheidungen dokumentiert** in [DECISIONS.md](DECISIONS.md),
  einschließlich der Datenschutzfolgen jeder Cloud-Nutzung.

## Bei Verlust oder Diebstahl eines Tablets

Heute: Der einkompilierte API-Schlüssel gilt weiter und muss für **alle**
Geräte gewechselt werden. Auf dem Gerät liegen Nutzerprofil, Erinnerungen und
Sprachaufnahmen unverschlüsselt.

Mit dem Proxy: Das Gerätetoken wird einzeln gesperrt, alle anderen Geräte
laufen unverändert weiter. Das ist der zweite große Gewinn von ADR-020, neben
dem Schlüsselschutz.

## Sicherheitsmeldungen

Wer eine Schwachstelle findet, soll sie melden können, ohne sie öffentlich zu
machen. Meldeweg über die im Repository hinterlegte Kontaktadresse; wir
antworten, bevor wir Details veröffentlichen. Die Spezifikation des Proxys
([PROXY-SPEC.md](PROXY-SPEC.md)) ist bewusst öffentlich, damit sie geprüft
werden kann.

---

## Grenzen – wogegen Lina nicht schützt

Ohne diesen Abschnitt wäre das Konzept unehrlich.

- **Die Stimme ist kein Ausweis.** Lina prüft nicht, *wer* spricht. Jede
  Person im Raum kann Anrufe auslösen, SMS verschicken lassen oder
  Nachrichten vorlesen lassen. Das ist die Kehrseite der Bedienung ohne
  Bildschirm und Passwort – und für die Zielgruppe die richtige Abwägung,
  aber sie muss benannt sein.
- **Körperlicher Zugriff bedeutet vollen Zugriff.** Lina läuft als
  Startbildschirm-App auf einem stationären Tablet ohne Sperre.
- **Gegen Täuschung des Nutzers hilft keine Technik.** Wer am Telefon oder
  per Brief betrügt, wird durch Lina nicht aufgehalten – sie liest den Brief
  nur vor.
- **Die Cloud-Anbieter sehen, was sie sehen.** Was an Anthropic geht, ist
  dort verarbeitet. Wir können das begrenzen und offenlegen, nicht aufheben.
- **Fernwartung ist mächtig.** Betreuer:innen können den Bildschirm sehen und
  Dateien abholen. Das schützt Vertrauen, nicht Technik.

---

## Offene Punkte

- [ ] AVV mit Anthropic abschließen
- [ ] `EncryptedSharedPreferences` für Nutzerprofil und Hörbuch-Fortschritt (Rest von Lücke 2 – Erinnerungen und SIM-Fingerabdruck sind bereits umgestellt)
- [x] ~~Automatische Löschung für Einrichtungs-Aufnahmen und `testfoto`-Bilder~~ (Lücken 3/4) – erledigt 2026-07-25/26
- [ ] Proxy umsetzen, Schlüssel aus der APK entfernen (Lücke 1)
- [ ] Kontaktadresse für Sicherheitsmeldungen im Repository hinterlegen
- [ ] Aufklärung ergänzen: Kontaktnamen werden Teil des System-Prompts
- [ ] Aufklärung ergänzen: SIM-/vCard-Kontakt-Import schreibt dauerhaft neue Kontakte (Lücke 6) – siehe WARTUNG.md-Einwilligung
