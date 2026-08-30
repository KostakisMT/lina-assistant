# Nächste Schritte – Stand 2026-08-30

> Entstanden an einem Tag mit Gerätetests und einem Klientenbesuch. Alles hier
> ist am Lenovo-Testtablet beobachtet, nicht hergeleitet. Details in
> CHANGELOG.md (Einträge vom 2026-08-30), Begründungen in DECISIONS.md
> (ADR-035), vollständige Liste in TODO.md.
>
> Branch: `fix/whisper-geistereingaben`, 12 Commits, **nicht gepusht**.

## Für die nächste Claude-Session zuerst lesen

Zwei Fehler, die an diesem Tag gemacht wurden – bitte nicht wiederholen:

1. **Vor dem „Reparieren" nach einem Test suchen, der die Absicht festhält.**
   Der fehlende News-Intent sah nach einer versehentlichen Regression aus und
   wurde beinahe zurückgebaut. Der Test `News gehen komplett an Ebene 2` hält
   die Entscheidung ausdrücklich fest. Ein Commit-Diff allein sagt nicht, ob
   etwas verloren ging oder bewusst entfernt wurde.

   **Vorsicht mit dem Wort „Nachrichten" – es meint im Deutschen dreierlei:**
   - **News** („was gibt es Neues?", RSS-Quellen, `ResolvedIntent.ReadNews`) –
     laufen bewusst komplett über Claude+Websuche.
   - **SMS** („lies meine Nachrichten", `ResolvedIntent.ReadSms`) – bleiben
     lokal und sind davon **nicht** berührt.
   - **Benachrichtigungen** (Android-Notifications, `LinaAccessibilityService`)
     – wieder etwas anderes; nur Anruf- und SMS-Notifications überleben den
     Filter, alles übrige fällt still weg. Siehe eigenen Abschnitt in TODO.md.

   In `LocalCommandResolverTest` standen beide Bedeutungen bis 2026-08-30 unter
   demselben Wort direkt untereinander. Die Tests heißen jetzt `SMS vorlesen`,
   `SMS schlagen Dokument` bzw. `News gehen komplett an Ebene 2`. Wer hier
   durcheinanderkommt, baut entweder die News-Entscheidung zurück oder
   zerschießt das SMS-Vorlesen.
2. **Der Debug-Broadcast ist kein Ersatz für das Mikrofon.**
   `am broadcast -a dev.lina.DEBUG_INPUT` speist Text in `processDebugInput()`
   ein und **umgeht damit alle Bestätigungsfenster**, die über
   `stt.startListening` hören. Was so getestet wurde, ist über den echten
   Sprachweg nicht bewiesen. Mehrere Befunde des Tages waren genau das.

---

## 1. Sicherheitsfunktion zu Ende beweisen

- [ ] **Gesprochenes „ja" beim Anruf-Schutz verifizieren.** `PhoneNumberRisk`
      hält Premium-/Kurzwahlnummern an, das ist am Gerät belegt – aber nur die
      *sicheren* Richtungen (Timeout, leeres Transkript). Der Ja-Pfad, der
      tatsächlich wählt, ist ungeprüft, weil der Debug-Broadcast das
      Bestätigungsfenster umgeht.
      **Vorgehen:** dem freigegebenen Testkontakt (der freigegebene Testkontakt,
      01555 5501234) testweise eine Kurzwahl als Zweitnummer geben, dann per
      Mikrofon „ja" sagen. **Real anrufen ausschließlich diesen Kontakt.**
- [ ] **Notruf gegenprüfen.** 110/112 dürfen nie in die Rückfrage laufen.
      Unit-getestet, am Gerät nicht – und hier ist ein Fehler nicht tolerierbar.
      Ohne echten Anruf prüfbar, indem ein Kontakt mit der Nummer 112 angelegt
      und im Log beobachtet wird, ob `Sondernummer erkannt` ausbleibt.

## 2. Der eigentliche Blocker: Spracherkennung auf Raumdistanz

Im Onboarding kamen **6 von 6** Antworten falsch an. „Oldenburg" → „Albenburg",
und dieser Satz war vollständig, nicht abgeschnitten. Die Antworten gehen
ungefiltert in Claudes System-Prompt – Lina las danach Nachrichten aus dem
falschen Landkreis vor. Weitere Belege: „Tolstoi" → „Teustol", „LibriVox" →
„Privaks", „Onleihe" → „Interleihte", „Hörbuch" → „führbuch".

- [ ] **Whisper `small` statt `base` messen.** Etwa 3× so groß, spürbar
      langsamer (statt ~2 s eher ~5 s pro Befehl). Ob das auf dem Tablet
      tragbar ist, muss gemessen werden – Latenz, RAM, Wärme.
- [ ] **Mikrofonabstand als Variable behandeln.** Vor der Modellfrage klären,
      wie viel schon durch Aufstellung oder ein externes Mikrofon zu holen ist.
      Billigster Hebel, bisher nicht systematisch geprüft.
- [ ] **`answers.json` nach dem Onboarding gemeinsam durchgehen.** Solange die
      Erkennung so ist, darf die Einrichtung nicht unbeaufsichtigt laufen. Der
      Nutzer kann die Fehler nicht bemerken.

## 3. Offene Fehler aus den Gerätetests

- [ ] **`speak()` beendet keine laufende Aufnahme.** Ist das Mikrofon im
      Folgefenster offen, kann Lina hineinsprechen. `waitForSilenceThenRun()`
      sichert nur die Gegenrichtung. Ausgelöst im Betrieb durch eine fällige
      `ReminderScheduler`-Ansage, den Reader oder jede `INTERRUPT`-Ausgabe.
      Der invasivste der offenen Punkte – bewusst vor der Übergabe
      zurückgestellt.
- [ ] **Stille Abbrüche in den Folgefenster-Öffnern.** Das Muster
      `if (onboarding != null) return` verschluckt Nutzerabsichten spurlos.
      Bei `openRiskyCallConfirm()` behoben, offen in `openSimImportFollowUp()`,
      `openDocFollowUp()`, `openLibrivoxSuggestionFollowUp()`.
      Bei der SIM-Nachfrage besonders heikel: `recordSeen()` läuft vorher, die
      Karte gilt danach als bekannt, **die Frage kommt nie wieder**.
- [ ] **Falsche Erfolgsmeldung beim Anruf.** Ohne SIM/Netz sagt Lina „Ich rufe
      … an", der Dialer kommt nie hoch, es passiert nichts. Teil des offenen
      P1 „Fehler-/Offline-Pfade akustisch abdecken".
- [ ] **Whisper halluziniert zusammenhängenden englischen Text** auf
      Raumrauschen (am Gerät: „3.7, expect is you attack quick…"). Der
      Artefaktfilter erkennt Untertitel-Notation, keinen plausibel klingenden
      Fließtext. Denkbar: Sprache prüfen und Nicht-Deutsches im Befehlspfad
      verwerfen.

## 4. Architektur – ADR-035, in dieser Reihenfolge

Der Befund dahinter: `ClaudeConversation.TOOLS` kannte 10 Werkzeuge,
`ResolvedIntent` hat 46 Intents. **Und `training/llm/prompts/system_prompt_router_de.txt`
listet exakt dieselben 10** – der lokale Llama-Router aus ADR-032 erbt die
Lücke unverändert.

- [ ] **Werkzeug-Registry: eine Deklaration, zwei Verbraucher.** Daraus werden
      sowohl die Claude-Tools als auch der Router-Prompt erzeugt.
- [ ] **Drift-Test.** Schlägt fehl, sobald ein `ResolvedIntent` weder ein
      Werkzeug hat noch auf einer Ausnahmeliste steht (nur interne
      Folgefenster-Intents). **Das ist der Kern der ADR** – die 46-gegen-10-
      Lücke konnte nur entstehen, weil zwei handgepflegte Listen ohne
      Verbindung nebeneinanderliefen und nichts es gemerkt hat.
- [ ] **Erst danach das nächste Router-Training.** Sonst zweimal trainieren.
      **Die 88,5 % aus ADR-032 sind kein übertragbarer Ausgangswert** – sie
      messen gegen 10 Klassen; mit dem vollen Umfang sinkt die Genauigkeit
      zunächst. Dazu die dort dokumentierte Lauf-zu-Lauf-Varianz (zweiter Lauf:
      59,6 %).
- [ ] **Regex auf Reflexe zurückschneiden.** Nur was sofort und offline nötig
      ist: Stopp, Pause, Weiter, lauter/leiser, Anrufsteuerung, Schlafmodus.
      Belegt: „Suche nach Hörbischan in der Kategorie Politik" erreichte Claude
      **gar nicht**, weil `such\s+(.+)` vorher zugriff und den verstümmelten
      Satz als LibriVox-Suchbegriff abfeuerte.
- [ ] **Offline-Ansage.** Ohne Netz und ohne lokalen Router bleiben nur die
      Reflexe – Lina muss das sagen. Stilles Scheitern ist für einen blinden
      Nutzer nicht von einem defekten Gerät zu unterscheiden.

## 5. Kontakt-Import

Eine echte Vodafone-SIM lieferte 21 Einträge, **keinen persönlichen**, 13 mit
199ct/Min. Testdatensatz liegt in `tablet-data/testdaten/vodafone-sim.vcf`.

- [ ] **SIM-Import filtern statt abschaffen.** Kurzwahlen, Namen mit „ct/Min",
      Anbieter-Präfixe. **Nicht abschaffen:** die Zielgruppe – ältere Menschen
      mit altem Tastenhandy – hat ihre Kontakte oft tatsächlich auf der SIM.
- [ ] **Gebündelt nachfragen** statt still importieren. Widerspricht ADR-029
      nicht; dort wurde die Einzelbestätigung *pro Kontakt* verworfen.
- [ ] **vCard-Trichter ausbauen.** Vorhanden: `ipad-import.sh`,
      `ipad_contacts_to_vcard.py`, `merge-vcards.py`. Offen: Google-Export,
      Android-zu-Android.
- [ ] **`ipad-import.sh` zeigt keinen Fortschritt** – Ausgabe läuft durch
      `tail -3`, der Nutzer sitzt bei einem ~20-GB-Backup vor einem scheinbar
      eingefrorenen Terminal.

## 6. Betrieb und Auslieferung

- [ ] **Fernwartung ist nie getestet worden.** Tailscale fehlt auf Mac und
      Tablet (`brew install --cask tailscale-app` braucht das Nutzerpasswort).
      Im Entwickler-WLAN verhinderte Client-Isolation jede direkte Verbindung –
      beim Nutzer kann das genauso sein. Ohne Tailscale kein Fernzugriff.
- [ ] **Be My Eyes installieren** – sonst öffnet „ruf einen Helfer an" nur die
      Play-Store-Seite (ADR-033).
- [ ] Release-Keystore + signiertes `assembleRelease`.
- [ ] Dauerbetrieb über Nacht verifizieren (Lenovo/ZUI Battery-Killer).

## 7. Widerspruch zwischen Code und Doku

- [ ] **Nachrichten entscheiden.** `LocalCommandResolverTest` hält fest, dass
      Nachrichten bewusst komplett an Claude/Websuche gehen. CLAUDE.md
      beschreibt weiterhin RSS-Feeds und Vertrauensquellen (Junge Welt, unsere
      zeit, Spektrum, Yacht) als Priorität 3, und `NewsSyncWorker` synchronisiert
      im Hintergrund Feeds, die per Sprache unerreichbar sind. Entweder den
      RSS-Weg wiederbeleben oder `feature/news/` ausbauen und CLAUDE.md
      angleichen.

---

## Betriebsfallen, die an einem Tag zweimal zugeschlagen haben

- **Jedes `adb install -r` und jedes `am force-stop` schaltet den
  AccessibilityService ab** – still, ohne Hinweis. Das ist die Kernkomponente
  für eingehende Anrufe. Nach jedem Deploy neu setzen:
  ```
  adb shell settings put secure enabled_accessibility_services dev.lina/dev.lina.core.accessibility.LinaAccessibilityService
  adb shell settings put secure accessibility_enabled 1
  ```
  Kontrolle: `adb shell dumpsys accessibility | grep 'label=Lina'`
- **Fehlt `shared_prefs/lina.xml`, startet die Einrichtung bei JEDEM App-Start
  neu** – und blockiert dabei Weckwort und alle Bestätigungsfenster. Genau das
  hat den Anruf-Schutz stundenlang unsichtbar gemacht.
- **`adb tcpip 5555` wirft das Gerät vom USB.** Mit `adb usb` zurück.
- Builds brauchen `JAVA_HOME` auf `openjdk@17`.
