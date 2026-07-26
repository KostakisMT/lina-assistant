# DECISIONS.md – Architekturentscheidungen

> Jede bewusste Architekturentscheidung wird hier festgehalten.
> Format: ADR (Architecture Decision Record)
> Vor einer Entscheidung immer prüfen ob ein bestehender ADR betroffen ist.

---

## ADR-001: Vosk als STT für MVP
**Datum:** 2026-05-29 | **Status:** Akzeptiert

**Kontext:** Offline-STT für Deutsch nötig. Kein Cloud-Zwang.
**Entscheidung:** Vosk (vosk-model-small-de) für Phase 1. Bewusst temporär.
**Konsequenzen:** `SttEngine` Interface ist Pflicht – Whisper.cpp / Sherpa-ONNX in Phase 2 ohne Refactoring austauschbar.

---

## ADR-002: Hybrid Intent-Parser
**Datum:** 2026-05-29 | **Status:** Akzeptiert

**Kontext:** "Ruf Boris an" und "Kannst du bitte Boris anrufen?" müssen beide funktionieren. Regellogik kollabiert bei natürlicher Sprache.
**Entscheidung:** Ebene 1: Regex/Keywords (<50ms). Ebene 2: lokales LLM (Phase 2).
**Konsequenzen:** `IntentResolver` Interface mit `LocalCommandResolver` + `LlmIntentResolver` (Stub).

---

## ADR-003: AccessibilityService als Kernkomponente
**Datum:** 2026-05-29 | **Status:** Akzeptiert

**Kontext:** Eingehende Anrufe per Sprache, Notifications vorlesen, später andere Apps bedienen.
**Entscheidung:** `LinaAccessibilityService` ist Kernarchitektur ab Phase 1, kein optionales Feature.
**Konsequenzen:** Nutzer muss AccessibilityService im Onboarding aktivieren.

---

## ADR-004: ForegroundService + Watchdog für Samsung
**Datum:** 2026-05-29 | **Status:** Akzeptiert

**Kontext:** Samsung OneUI killt Hintergrundprozesse aggressiv.
**Entscheidung:** WakeWordService als ForegroundService + PARTIAL_WAKE_LOCK + Battery-Whitelist + Watchdog.
**Konsequenzen:** Tablet läuft stationär, Bildschirm oft an. Bekannte Abwägung.

---

## ADR-005: Android TTS für MVP, Piper für Phase 2
**Datum:** 2026-05-29 | **Status:** Akzeptiert

**Kontext:** Nutzer hört stundenlang zu. Sprachqualität ist Kernfunktion.
**Entscheidung:** Android TTS (weiblich, de-DE, 0.9f) für MVP. Piper in Phase 2.
**Konsequenzen:** `TtsEngine` Interface Pflicht. Rate 0.9f als Startwert.

---

## ADR-006: Apache 2.0 Lizenz
**Datum:** 2026-05-29 | **Status:** Akzeptiert

**Kontext:** Verbände, NGOs, Krankenkassen sollen es übernehmen können.
**Entscheidung:** Apache 2.0 – offen, kommerziell nutzbar, kein Copyleft-Zwang.

---

## ADR-007: Weckwort "Hey Lina"
**Datum:** 2026-05-29 | **Status:** Akzeptiert

**Kontext:** Nutzer kannte Siri von Apple, wollte ähnlichen Namen. "Siri" ist Apple-Marke.
**Entscheidung:** Name "Lina" – kurz, weiblich, phonetisch klar, markenrechtlich frei. Weckwort: "Hey Lina".
**Konsequenzen:** Alle TTS-Ausgaben in Ich-Form von Lina. Porcupine Custom Wakeword nötig.

---

## ADR-008: RSS only – kein automatisches Briefing
**Datum:** 2026-05-29 | **Status:** Akzeptiert

**Kontext:** Nutzer möchte Nachrichten nur auf Abruf, kein automatisches Vorlesen.
**Entscheidung:** WorkManager synct stündlich im Hintergrund. Vorlesen nur auf Sprachbefehl.
**Konsequenzen:** Kein Morgen-Briefing, kein Push. Einfacher zu bauen, respektiert Autonomie.

---

## ADR-009: Fuzzy-Matching für Kontakte
**Datum:** 2026-05-29 | **Status:** Akzeptiert

**Kontext:** Kontaktnamen wie "Arundhati Brandt" und "Dirk Eßfeld" sind spracherkennungs-kritisch. Spitznamen wie "Boris" statt "Boris Hartmann" müssen matchen.
**Entscheidung:** `FuzzyContactMatcher` mit phonetischer Ähnlichkeit (Levenshtein + Kölner Phonetik) + konfigurierbares Spitznamen-Mapping.
**Konsequenzen:** Kontakt-Matching ist eigene Komponente, nicht inline im Feature-Code.

---

## ADR-010: Onleihe + Librivox für Phase 2 Bibliothek
**Datum:** 2026-05-29 | **Status:** Vorgeschlagen

**Kontext:** Nutzer möchte Zugang zu einer vorlesbaren Bibliothek. Keine Vorerfahrung mit Hörbüchern.
**Entscheidung:** Phase 2: Onleihe (kostenlos via Bibliotheksausweis) + Librivox (gemeinfreie Klassiker). Phase 1: lokale MP3/M4B Dateien.
**Konsequenzen:** `AudiobookPlayer` muss später Streaming-Quellen unterstützen. Interface von Anfang an offen halten.

---

## ADR-011: Librivox schon im MVP (nicht erst Phase 2)
**Datum:** 2026-05-30 | **Status:** Akzeptiert

**Kontext:** Hörbücher sind der MVP-Hook. Nur lokale Dateien wäre zu mager – der Nutzer hat keine Hörbuch-Erfahrung und keine Sammlung. Librivox bietet gemeinfreie Klassiker (Tolstoi, Gorki, Brecht) kostenlos per API, kein Login nötig.
**Entscheidung:** Librivox-Suche + Streaming direkt im MVP. Onleihe bleibt Phase 2 (braucht Bibliotheksausweis-Login).
**Konsequenzen:** WLAN ist Voraussetzung für Librivox-Suche (Tablet ist stationär, also gegeben). Lokale Dateien bleiben als Offline-Fallback.

---

## ADR-012: Schlaf-Timer mit Lautstärke-Fade
**Datum:** 2026-05-30 | **Status:** Akzeptiert

**Kontext:** Nutzer sitzt viel, hört möglicherweise abends lange. Abrupter Stopp ist unangenehm.
**Entscheidung:** Schlaf-Timer mit 30-Sekunden-Fade-Out (Lautstärke sinkt linear auf 0). Einfacher Sprachbefehl: "Stopp in 30 Minuten".
**Konsequenzen:** Fortschritt wird beim Fade-Ende gespeichert. Kein Wecker/Alarm nötig.

---

## ADR-013: OpenWakeWord statt Porcupine
**Datum:** 2026-06-20 | **Status:** Akzeptiert

**Kontext:** Picovoice (Porcupine) erfordert seit 2026 einen manuellen Approval-Prozess für API-Keys. Kein sofortiger Zugang möglich. Für ein Open-Source-Projekt ist eine Abhängigkeit von einem proprietären Gatekeeper nicht tragbar.
**Entscheidung:** Migration auf OpenWakeWord (Apache 2.0). Nutzt ONNX Runtime für Android. Drei-Stufen-Pipeline: Melspectrogram → Speech Embedding → Wake Word Classifier. Vorläufig "hey_jarvis" als Platzhalter bis Custom-Modell "Hey Lina" trainiert ist.
**Konsequenzen:**
- `WakeWordEngine` Interface eingeführt – Wake Word Detection ist jetzt austauschbar
- Kein API-Key mehr nötig
- ONNX-Modelle (~3.5MB) müssen per Script heruntergeladen werden (`scripts/download-models.sh`)
- Custom "Hey Lina"-Modell muss separat trainiert werden (synthetische TTS-Daten + Classifier)
- APK-Größe: +4MB gegenüber Porcupine (akzeptabel)

---

## ADR-014: Zielgerät-Wechsel auf Lenovo Idea Tab (TB336ZU)
**Datum:** 2026-07-02 | **Status:** Akzeptiert

**Kontext:** Das ursprünglich geplante Samsung Galaxy Tab A9+ wurde durch ein Lenovo Idea Tab TB336ZU ersetzt – das Gerät liegt physisch vor und wird das Gerät des Primärnutzers.
**Entscheidung:** Lenovo Idea Tab TB336ZU ist neues Primär- und Zielgerät. Die OEM-Stabilitätsstrategie (ForegroundService, Watchdog, Battery-Whitelist, Boot-Autostart) bleibt unverändert – sie war nie Samsung-spezifisch implementiert, nur so benannt.
**Konsequenzen:**
- Lenovo nutzt nahezu Stock-Android mit Google-Apps: `com.google.android.dialer` als Dialer-Paket im `LinaAccessibilityService` ergänzt (Samsung-Paketnamen bleiben als Fallback)
- Samsung-OneUI-spezifische Risiken (aggressiver App-Killer) entschärft, Lenovo-Battery-Optimierung muss aber real getestet werden
- Alle Doku-Referenzen (CLAUDE.md, README, NUTZERPROFIL, ONBOARDING, TODO) aktualisiert

---

## ADR-015: sherpa-onnx als Runtime für Piper-TTS und Whisper-STT
**Datum:** 2026-07-02 | **Status:** Akzeptiert

**Kontext:** Vision-Upgrade: natürliche Stimme + robustes Sprachverstehen. Piper (C++) und Whisper bräuchten sonst zwei getrennte native Integrationen.
**Entscheidung:** sherpa-onnx (k2-fsa, Apache 2.0) als gemeinsame Runtime – eine AAR liefert TTS (Piper/VITS) und STT (Whisper). Variante mit statisch gelinktem ONNX Runtime, um Kollision mit der Microsoft-ONNX-AAR (OpenWakeWord) zu vermeiden; x86-ABIs vom Packaging ausgeschlossen. Stimme: de_DE-ramona-low (weiblich). STT: Whisper base int8 multilingual, language=de. Beides offline.
**Konsequenzen:**
- APK wächst auf ~324MB (Whisper 153MB, Piper 65MB, Vosk bleibt als Fallback)
- AAR + Modelle via scripts/download-models.sh, nicht im Git
- Whisper ist nicht-streamend: eigene Endpunkt-Erkennung (Stille-Detektion) in WhisperSttEngine
- Alte Engines bleiben als Fallback hinter den Interfaces (SttEngine/TtsEngine)

---

## ADR-016: TTS-Stimme de_DE-dii-high (OpenVoiceOS)
**Datum:** 2026-07-04 | **Status:** Akzeptiert

> **Aktualisierung (ADR-023, 2026-07-25):** Die NC-Begründung stützt sich nicht
> mehr auf eine gemeinnützige Trägerschaft, sondern darauf, dass Lina ein rein
> nicht-kommerzielles, quelloffenes Projekt ohne kommerziellen Vertrieb ist. Die
> Stimme bleibt unverändert nutzbar.

**Kontext:** Die Standard-Piper-Frauenstimmen (ramona/kerstin/eva_k) existieren nur in "low"-Qualität und wurden vom Entwickler als unzureichend bewertet. Hochwertige deutsche Piper-Stimmen: thorsten-medium/high (männlich, freie Lizenz) sowie die OpenVoiceOS-Stimmen dii-high/miro-high (CC BY-NC-SA 4.0). A/B-Test aller 6 Kandidaten auf dem Zielgerät per Laufzeit-Stimmwechsler.
**Entscheidung:** `de_DE-dii-high` wird Linas Standard-Stimme. Die NC-Lizenz ist zulässig, weil Lina ein rein nicht-kommerzielles, quelloffenes Projekt ohne kommerziellen Vertrieb ist – NonCommercial bezieht sich auf die Art der Nutzung, nicht auf die Rechtsform des Trägers (siehe ADR-023).
**Konsequenzen:**
- CC BY-NC-SA 4.0 muss bei Weitergabe der App dokumentiert werden (Attribution: OpenVoiceOS/pipertts_de-DE_dii)
- Falls je ein kommerzieller Zweig entsteht: Stimme ersetzen (Option: eigene Piper-Stimme trainieren, freier deutscher Frauen-Datensatz)
- Laufzeit-Stimmwechsler ("Stimme <n>") bleibt als Debug-Feature erhalten
- Ungenutzte Modelle aus Assets entfernt (APK ~380MB statt ~680MB)

---

## ADR-017: Claude API (claude-sonnet-5) für freie Konversation
**Datum:** 2026-07-16 | **Status:** Akzeptiert

**Kontext:** Ebene 2 des Intent-Systems war als lokales LLM geplant (Phi-3/Gemma GGUF). Die Vision (Gesprächspartnerin mit echter Konversationsfähigkeit) übersteigt aber, was quantisierte 2-3B-Modelle auf dem Lenovo-Tablet leisten – und das Tablet steht stationär im WLAN. Modellwahl: Sprachassistenz ist latenz- und kostensensitiv (viele kurze Antworten, 1-3 Sätze), kein Long-Horizon-Reasoning nötig.
**Entscheidung:** Cloud-Hybrid: Kernbefehle bleiben lokal/offline (Ebene 1), freie Konversation läuft über die Claude API mit `claude-sonnet-5` (bestes Latenz/Qualität/Kosten-Verhältnis für kurze Dialogantworten; Thinking deaktiviert). Claude bekommt Tool-Definitionen für die Kernbefehle und kann verstümmelte STT-Transkripte als Gerätebefehle zurückreichen.
**Konsequenzen:**
- Ohne Internet oder API-Key funktionieren weiterhin alle Kernbefehle – nur die freie Konversation entfällt (Prinzip "Offline where possible" bleibt gewahrt)
- API-Key liegt in `local.properties` (nicht im Git); pro Gerät zu hinterlegen
- Datenschutz: freie Konversation verlässt das Gerät; muss im Onboarding/der Doku transparent gemacht werden
- `LlmIntentResolver` (lokales Modell) bleibt als möglicher späterer Offline-Pfad im Backlog

---

## ADR-018: Dokument-Vorlesen per Rückkamera + Cloud-Vision
**Datum:** 2026-07-20 | **Status:** Akzeptiert

**Kontext:** Post, Zeitungen und Formulare selbstständig lesen zu können ist einer der größten Alltagswünsche blinder Menschen (Förder-Meilenstein 3). Kamerabedienung ist für Blinde normalerweise das Hauptproblem: Ausrichten, Abstand, Schärfe. Der Testnutzer stellt das Tablet stationär auf einen fixierten Ständer und markiert mit Kreppband einen festen Rahmen auf dem Tisch – Dokumente werden immer an dieselbe Stelle gelegt. Damit entfällt das Ausrichtungsproblem vollständig. Optionen für die Texterkennung: (a) On-Device-OCR (z.B. ML Kit/Tesseract) – offline, aber liefert nur rohen Text ohne Struktur, Layout-Chaos bei Briefen, keine Relevanzfilterung; (b) Cloud-Vision über die bereits angebundene Claude API – versteht Layout, Absender, Anliegen und kann Belangloses weglassen.

**Entscheidung:** Rückkamera (höhere Auflösung als die Frontkamera; auf dem Testgerät auch bei schwachem Licht scharf) + `claude-sonnet-5` Vision. Die Auswertung ist ein **einmaliger, zustandsloser Aufruf** – das Bild landet nicht im Dialoggedächtnis. Standard ist die relevanzgefilterte Ausgabe (bei Briefen: erst Absender und Anliegen, dann Inhalt; Anschriften, Briefköpfe, Fußzeilen, Kleingedrucktes und Werbung werden weggelassen), auf Nachfrage der vollständige Text.

**Konsequenzen:**
- Dokumentfotos verlassen das Gerät – bei Post besonders sensibel. Muss in der Einwilligung ausdrücklich benannt werden (WARTUNG.md).
- Das Bild wird **nicht persistiert**: nur transient im RAM während des Vorlesens und des Folgefensters, danach verworfen. Ausnahme: der Debug-Befehl "testfoto" speichert bewusst eine Datei, damit die Rahmen-Ausrichtung einmalig per adb geprüft werden kann.
- Ohne Internet gibt es kein Dokument-Vorlesen (alle Offline-Kernbefehle laufen weiter). On-Device-OCR bleibt als möglicher Offline-Pfad im Backlog.
- CameraX bekommt einen **eigenen LifecycleOwner** statt dem der Activity: Das Tablet steht stationär mit meist ausgeschaltetem Bildschirm; eine gestoppte Activity würde die Kamera sofort abkoppeln ("Camera is closed").
- Kosten: ein Vision-Aufruf pro Dokument (Bild auf 2000px lange Kante herunterskaliert, JPEG-Q85 ≈ 500 kB).

---

## ADR-019: DAISY 2.02 selbst parsen, Kapitel als Kernkonzept
**Datum:** 2026-07-20 | **Status:** Akzeptiert

**Kontext:** Die Blindenhörbüchereien (Norddeutsche Hörbücherei ~50.000 Titel, WBH Münster) verleihen kostenlos an nachweislich sehbehinderte Menschen – im Format DAISY 2.02. Aktive quelloffene DAISY-Player für Android existieren praktisch nicht; die vorhandenen Projekte sind eingestellt oder laufen nicht auf aktuellen Android-Versionen. Damit ist der größte frei verfügbare Hörbuchbestand für den Nutzer bisher unerreichbar. Optionen: (a) fremde DAISY-Bibliothek einbinden – keine gepflegte für Android verfügbar; (b) DAISY 2.02 selbst lesen – der Standard ist offen und schlank: `ncc.html` (XHTML mit Metadaten und Kapiteln als Überschriften) plus SMIL-Dateien, die Kapitel auf Audiodatei und Zeitbereich abbilden.

Unabhängig davon fehlte dem Hörbuch-Feature das Konzept „Kapitel" ganz: `AudiobookPlayer` kannte nur eine einzelne Datei. Das war auch der Grund, warum LibriVox-Bücher nach dem ersten Abschnitt kommentarlos endeten.

**Entscheidung:** DAISY 2.02 wird selbst geparst (`DaisyParser`, `DaisyRepository`), und Kapitel werden als eigenes Modell (`Chapter`) im gesamten Hörbuch-Feature verankert – der Player spielt grundsätzlich eine Kapitel-Playlist. Bewusst nur JDK-XML (`DocumentBuilder`) statt Androids `XmlPullParser`, damit der Parser in reinen JVM-Unit-Tests ohne Gerät läuft. DAISY 3 / EPUB3-Audio bleibt vorerst außen vor.

**Konsequenzen:**
- Ein Format-Parser mehr in eigener Verantwortung. Fremde Produktionsstellen weichen in Schreibweisen ab, deshalb: `clip-begin` **und** `clipBegin`, Clock-Values in allen SMIL-Varianten, Dateinamen ohne Rücksicht auf Groß-/Kleinschreibung (gebrannte CDs), DOCTYPE und benannte Entities werden vor dem Parsen entschärft. Alles über Testfixtures abgedeckt.
- Externe Entities sind abgeschaltet: Das Tablet ist zeitweise offline (die DTD wäre nicht ladbar), und ein Buch aus fremder Quelle darf keinen Netzabruf auslösen (XXE).
- Mehrere DAISY-Kapitel teilen sich oft eine MP3. Der Player nutzt dafür `MediaItem.ClippingConfiguration` – die Kapitelgrenze ist damit Sache von ExoPlayer, nicht eigener Zeitlogik.
- Kapitelwechsel werden **angesagt**. Ohne Bild ist ein Kapitelsprung sonst nicht wahrnehmbar; das gilt auch beim automatischen Weiterlaufen.
- Für gestreamte Bücher wird die Kapitelliste nicht persistiert, sondern der RSS-Feed – nach einem Neustart wird sie neu geholt. Sonst wäre der LibriVox-Fehler über den Neustart hinweg zurück.
- Die Mitgliedschaft bei einer Hörbücherei setzt einen Sehbehinderungsnachweis voraus und wird von Lina nicht abgebildet: Bücher landen manuell im Audiobooks-Ordner. Ein Ausleih-Onboarding bleibt offen.

---

## ADR-020: Proxy statt API-Key pro Gerät
**Datum:** 2026-07-21 | **Status:** Akzeptiert

> **Aktualisierung (ADR-023, 2026-07-25):** Vorerst gibt es keine Träger-Organisation.
> In der Übergangsphase läuft die App über einen eigenen API-Key des Entwicklers;
> der Proxy und ein widerrufbares Gerätetoken bleiben das Ziel für die Verteilung.
> Betreiber und Verantwortlicher ist die jeweils tragende Stelle – aktuell die
> Privatperson, perspektivisch ggf. eine gemeinnützige Organisation.

**Kontext:** ADR-017 ließ offen, wie ein ausgeliefertes Gerät an Claude-Zugang kommt – der Key liegt bis heute in `local.properties` und wird per `buildConfigField` einkompiliert. Das trägt für ein Testgerät, aber nicht für Verteilung: Ein Key in einer verteilten APK ist kompromittiert, sobald jemand sie auseinandernimmt.

Die naheliegende Alternative "der Nutzer meldet sich mit seinem eigenen Claude-Konto an" ist nicht möglich: Anthropic untersagt Drittanbieter-Apps ausdrücklich, OAuth-Token aus Free-/Pro-/Max-Konten zu verwenden (Richtlinienänderung Februar 2026, verschärft April 2026). Erlaubt sind ausschließlich API-Keys aus der Console.

Bleibt "Bring your own key": Der Nutzer legt selbst ein Console-Konto an und hinterlegt seinen Key. Für die Zielgruppe ist das unmöglich. Ein Anthropic-Key ist `sk-ant-api03-` plus ~95 Zeichen Base64 – nicht diktierbar, nicht buchstabierbar, per Whisper nicht robust erfassbar. Die Registrierung selbst (Kreditkarte, Captcha, E-Mail-Bestätigung) ist für einen blinden Menschen ohnehin nicht selbstständig zu schaffen.

**Entscheidung:** Der Betreiber betreibt einen Proxy und hält dort den Anthropic-Key. Die App spricht nicht mehr direkt mit Anthropic, sondern authentifiziert sich beim Proxy mit einem **widerrufbaren Gerätetoken**. Vergeben wird das Token über einen **gesprochenen Pairing-Code**: Lina nennt einen kurzen Code phonetisch ("Berta – Sieben – Anton – Drei"), eine Vertrauensperson gibt ihn auf der Betreiber-Webseite ein, das Tablet pollt und erhält sein Token. Damit findet die sehende Arbeit außerhalb des Tablets statt und der Nutzer muss nichts sehen, tippen oder diktieren.

`BuildConfig.CLAUDE_API_KEY` bleibt als Entwicklerpfad erhalten.

**Konsequenzen:**
- Ohne erreichbaren Proxy entfällt die freie Konversation – alle Offline-Kernbefehle laufen weiter. Das Prinzip aus ADR-017 bleibt gewahrt, die Ausfallursache verschiebt sich nur von "kein Key" zu "kein Proxy".
- Der Proxy ist ein Single Point of Failure und wird betriebsnotwendige Betreiberinfrastruktur. Betrieb, Monitoring und Erreichbarkeit sind ab dann eine dauerhafte Verpflichtung, keine Nebensache.
- Der Proxy **zählt den Verbrauch pro Gerät ab Tag eins**, auch solange alles kostenlos ist. Ohne diese Daten ist jede spätere Kontingent- oder Preisentscheidung geraten (siehe ADR-021).
- Bei Verlust eines Tablets wird ein einzelnes Gerätetoken gesperrt, statt einen organisationsweiten Key rotieren zu müssen.
- Das Gerätetoken gehört in `EncryptedSharedPreferences`. Das Projekt nutzt bisher durchgängig unverschlüsselte `SharedPreferences` (`PlaybackStateStore`, `ReminderStore`, Onboarding-Profil) – für ein Zugangstoken reicht das nicht.
- Datenschutzrechtlich wird der Betreiber Verantwortlicher und Anthropic Auftragsverarbeiter; ein AVV wird nötig. Die Einwilligung (WARTUNG.md) muss den Zwischenschritt über den vorgeschalteten Server benennen.
- Ein Buchstabieralphabet für die phonetische Code-Ansage fehlt und gehört nach `core/text/` neben `GermanNumbers`.

---

## ADR-021: Freikontingent pro Gerät, Kostenweitergabe ohne Marge darüber
**Datum:** 2026-07-21 | **Status:** Teilweise überholt (ADR-023)

> **Aktualisierung (ADR-023, 2026-07-25):** Die auf Gemeinnützigkeit, Zweckbetrieb
> (§68 AO) und Spendenfinanzierung gestützte Begründung trägt nicht mehr, solange
> kein gemeinnütziger Träger existiert. Das **Prinzip bleibt** – frei für die
> normale Alltagsnutzung, keine Zahlungsdaten in der App. Die
> **Finanzierungsmechanik ist offen**: die Kosten trägt vorerst der Entwickler,
> ein tragfähiges Modell ist an einen künftigen gemeinnützigen Partner geknüpft.

**Kontext:** Mit dem Proxy (ADR-020) trägt der Betreiber die Claude-Kosten aller Nutzer. Bei geschätzt 3–12 € pro aktivem Nutzer und Monat skaliert das unangenehm: ~100 Nutzer sind 3.600–14.000 € im Jahr und aus Spenden und Förderung tragbar; 10.000 Nutzer wären 360.000–1,4 Mio. € im Jahr und für keine tragende Stelle finanzierbar. Ein unbedingtes Versprechen "für immer kostenlos" müsste bei Erfolg gebrochen werden.

Eine Gewinnmarge auf die Weitergabe wäre bei Anthropic zulässig – ein Produkt auf der API zu bauen und dafür Geld zu nehmen ist üblich, untersagt ist nur der Weiterverkauf des rohen API-Zugangs. Sie kollidiert aber mit zwei bestehenden Festlegungen: Sie begründet einen wirtschaftlichen Geschäftsbetrieb und gefährdet damit die Gemeinnützigkeit, und sie löst genau den Fall aus, den ADR-016 vorsieht – die Stimme `de_DE-dii-high` (CC BY-NC-SA 4.0) ist nur nutzbar, weil kein kommerzieller Vertrieb stattfindet.

**Entscheidung:** Gestaffeltes Modell statt Entweder-oder.

Jedes Gerät bekommt ein **monatliches Freikontingent**, bemessen so, dass normale Alltagsnutzung vollständig gedeckt ist – finanziert aus Spenden und Förderung. Erst darüber hinaus braucht es ein Nutzerkonto mit Rechnungsanschrift, und die Mehrkosten werden **1:1 ohne Gewinnaufschlag** weitergegeben. Die große Mehrheit der Nutzer braucht damit nie ein Konto.

Die Höhe des Kontingents wird **erst nach dem Feldtest** aus echten Verbrauchsdaten des Proxys festgelegt. Jede vorher genannte Zahl wäre erfunden.

Zahlungsdaten laufen ausschließlich über einen Payment-Provider auf einer Betreiber-Webseite. **Die App erfasst, speichert und überträgt keine Zahlungsdaten – insbesondere nicht per Spracheingabe.** Der Proxy kennt nur eine Kunden-ID des Providers.

**Konsequenzen:**
- ADR-016 bleibt gültig: kein kommerzieller Vertrieb, die Stimme muss nicht ersetzt werden.
- Die Zugangszusage für die Zielgruppe bleibt belastbar, ohne dass der Betreiber ein unbegrenztes Kostenrisiko trägt.
- **Vor der ersten Rechnung ist eine steuerliche Prüfung erforderlich.** Offen sind mindestens: Umsatzsteuer (auch ohne Marge liegt ein Leistungsaustausch vor; die Kleinunternehmerregelung wird bei Skalierung gesprengt; §4 Nr. 18 UStG prüfen) und die Einordnung als Zweckbetrieb nach §68 Nr. 4 AO – Einrichtungen der Blindenfürsorge sind dort ausdrücklich genannt und ein aussichtsreicher Kandidat.
- Sobald Nutzer zahlen, greifen Fernabsatz- und Widerrufsrecht sowie die Preisangabenverordnung. AGB und Widerrufsbelehrung müssen für diese Zielgruppe **barrierefrei zugänglich** sein – ein PDF genügt nicht.
- Nach außen wird nicht "für immer kostenlos" versprochen, sondern: für die normale Alltagsnutzung zahlt niemand.
- Der Bedarf an Kontingenten macht die Verbrauchszählung aus ADR-020 zur harten Anforderung, nicht zum Nice-to-have.

---

## ADR-022: Gestuftes Modell-Routing statt "alles an Sonnet 5"
**Datum:** 2026-07-21 | **Status:** Akzeptiert

**Kontext:** Die laufenden Kosten sind der limitierende Faktor für Linas Reichweite – jeder eingesparte Euro ist ein Nutzer mehr, der die App bekommen kann. Die heutige Implementierung in `ClaudeConversation` ist auf Qualität optimiert und kostenmäßig ungeprüft:

- `MODEL = "claude-sonnet-5"` für **jeden** Turn, auch für "ja bitte" oder "nächstes Kapitel"
- `WebSearchTool20260209` mit `maxUses(3)` – Websuche wird **pro Suche** abgerechnet, nicht über Tokens, und kann damit den restlichen Turn kostenmäßig übersteigen
- `MAX_HISTORY = 20`, die vollständige Historie geht bei jedem Turn erneut in den Input
- `LlmIntentResolver` ist ein toter Stub; alles, was Ebene 1 nicht versteht, geht in die Cloud

Gut gelöst ist bereits: System-Prompt mit `CacheControlEphemeral` gecacht, Thinking deaktiviert, `maxTokens` auf 500 begrenzt.

**Entscheidung:** Gestufte Ausführung nach Aufwand der Anfrage.

1. Ebene 1 (`LocalCommandResolver`) bleibt erste Instanz – kostenlos, unverändert.
2. **Ebene 2 lokal reaktivieren:** `LlmIntentResolver` mit kleinem On-Device-Modell für einfache Rückfragen und Bestätigungen. Damit ist der Eintrag im Backlog nicht mehr "ggf. obsolet durch ADR-017", sondern ein Kostenhebel.
3. **Haiku 4.5 als Standard** für kurze Turns; Eskalation auf Sonnet 5 nur bei echten Gesprächen.
4. **Websuche restriktiver:** `maxUses` senken, nur bei erkennbarem Aktualitätsbezug zulassen.
5. `MAX_HISTORY` senken und die Wirkung auf die Gesprächsqualität messen.

Zielgröße: Faktor 3–5 gegenüber heute.

**Konsequenzen:**
- Mehr Komplexität im Routing – eine zusätzliche Stelle, an der Verhalten schwer nachvollziehbar wird.
- Qualitätsrisiko: Haiku kann bei Grenzfällen schwächer sein. Das ist zu **messen, nicht anzunehmen** – die Eskalationsschwelle ist ein empirischer Wert.
- Vision und Dokument-Vorlesen bleiben bei Sonnet 5 (ADR-018). Dort zählt Genauigkeit mehr als der Preis, und die Aufrufe sind selten.
- Erster Schritt ist keine Optimierung, sondern eine **Messung**: ein realer Alltagstag mit Aufschlüsselung nach Tokens und Websuchen. Ohne diese Zahlen ist jede Priorisierung geraten – auch die Reihenfolge oben.

---

## ADR-023: Projekt läuft als Privatperson weiter, ohne Träger-Organisation
**Datum:** 2026-07-25 | **Status:** Akzeptiert

**Kontext:** Bisher war als Träger eine gemeinnützige Organisation vorgesehen. Diese Trägerschaft war an mehreren Stellen tragend: als Begründung für die NC-Lizenz der Stimme (ADR-016), als DSGVO-Verantwortlicher und Betreiber des Proxys (ADR-020) und als Kostenträger des Freikontingents (ADR-021). Die Planung ändert sich: Es gibt vorerst keine solche Organisation. Der Entwickler führt Lina als **Privatperson** weiter, bewirbt sich beim **Prototype Fund** (der ohnehin nur an Privatpersonen auszahlt, nicht an Organisationen – die neue Struktur passt hier besser) und sucht parallel Gespräche über Testausweitung und Finanzierung.

**Entscheidung:** Lina wird vorerst privat vom Entwickler getragen. Eine spätere gemeinnützige Trägerschaft bleibt eine offene Option, wird aber **nicht festgelegt und in der öffentlichen Doku nicht namentlich benannt** – weder mögliche Partner noch Fördergeber. Konkrete Kandidaten werden nur intern geführt. „Alles offen halten" ist bewusste Strategie, nicht Unfertigkeit.

**Konsequenzen:**
- **NC-Lizenz (ADR-016):** Die Stimme `de_DE-dii-high` (CC BY-NC-SA 4.0) bleibt nutzbar. „NonCommercial" bezieht sich auf die Art der Nutzung, nicht auf die Rechtsform des Nutzers – ein privates, kostenloses, quelloffenes Projekt ohne kommerziellen Vertrieb erfüllt die Bedingung ebenso wie ein gemeinnütziger Träger. Ein Stimmentausch bleibt nur für den Fall eines kommerziellen Zweigs nötig.
- **Verantwortung & Betrieb (ADR-020):** Datenschutzrechtlich verantwortlich ist jetzt der Entwickler. In der Übergangsphase laufen die Cloud-Funktionen direkt zum KI-Dienst über einen **eigenen API-Key** (der `BuildConfig.CLAUDE_API_KEY`-Pfad existiert). Der Proxy mit Gerätetoken bleibt das Ziel für die Verteilung, wird aber jetzt nicht gebaut.
- **Kostenmodell (ADR-021):** Die auf Gemeinnützigkeit gestützte Begründung entfällt. Das Prinzip – frei für die normale Alltagsnutzung, keine Zahlungsdaten in der App – bleibt. Die Finanzierung ist offen: die laufenden Kosten trägt vorerst der Entwickler; ein tragfähiges Modell (Fördermittel, Spenden) ist an einen künftigen gemeinnützigen Partner geknüpft.
- **Fördermittel:** Der Prototype Fund passt zur Privatperson-Struktur. Fördertöpfe und Programme, die eine gemeinnützige Organisation voraussetzen (z.B. Aktion Mensch, steuerabzugsfähige Spenden), sind ohne einen solchen Partner nicht zugänglich und bleiben ein offener Strang.
- Diese ADR **ändert ADR-016, ADR-020 und ADR-021**; deren ursprünglicher Text bleibt als Historie stehen, jeweils mit einer Aktualisierungsnotiz versehen.

---

## ADR-024: Nachrichten laufen über Ebene 2 (Claude + Websuche), nicht mehr über den RSS-Reader
**Datum:** 2026-07-25 | **Status:** Akzeptiert

**Kontext:** Beim ersten Bring-up-Test auf dem echten Lenovo-Tablet war der RSS-basierte `NewsReader` (ADR aus Phase 1f) in Bedienung und Klang "grausam": starres Folgefenster mit Schlüsselwörtern ("mehr", "nächste", "stopp"), rohe RSS-Zusammenfassungen ohne Relevanzfilterung, kein Eingehen auf Rückfragen. Parallel existierte für regionale/thematische Nachrichtenwünsche bereits ein funktionierender Pfad über `ClaudeConversation` mit Websuche (ADR-017) – inklusive Dialoggedächtnis und natürlichem Gesprächsfluss. Nur der allgemeine Fall ("Was gibt es Neues?") ging noch am lokalen `LocalCommandResolver` vorbei zum RSS-Reader.

**Entscheidung:** Auch der allgemeine Nachrichten-Fall läuft jetzt über Ebene 2. `LocalCommandResolver.resolveNews()` wurde entfernt – jede Nachrichtenanfrage fällt an Claude durch. Der System-Prompt weist Claude an, einen kurzen, für den Nutzer relevanten Überblick zu geben (Region + international gemischt, unter Nutzung der aus dem Onboarding bekannten Interessen und Wohnregion), gefolgt von genau einem Rückfrage-Hinweis. Das Werkzeug `nachrichten_vorlesen` wurde aus dem Tool-Set entfernt. `NewsReader`, `RssFeedRepository`, `NewsCache` und `NewsSyncWorker` bleiben im Code, werden aber nicht mehr angesprochen – Entscheidung über Entfernen vs. Offline-Fallback steht noch aus (siehe TODO.md).

**Konsequenzen:**
- Nachrichten sind jetzt zwingend online und kosten einen Claude-Turn plus ggf. Websuche (Kostenfrage siehe ADR-022) – anders als der bisher komplett offline laufende RSS-Pfad.
- Deutlich bessere Bedienung: freier Rückfrage-Dialog statt starrem Kurzwort-Fenster, Relevanzfilterung statt roher RSS-Reihenfolge.
- `resolveNews`-Tests in `LocalCommandResolverTest` wurden auf das neue Verhalten umgestellt (Nachrichten-Eingaben liefern jetzt bewusst `null`, damit sie an Ebene 2 durchfallen).
- Kein Nachrichten-Zugriff mehr möglich, wenn kein `CLAUDE_API_KEY` gesetzt ist oder das Internet fehlt – das war vorher über den RSS-Cache zumindest teilweise offline abgefangen. Bewusst in Kauf genommen, da der Offline-Fall laut Feedback ohnehin schlecht war; eine Offline-Rückfallebene wäre ein separates, künftiges Thema.

---

## ADR-025: Lokale Hörbücher als Ordner mit einer Datei je Kapitel + READ_MEDIA_AUDIO
**Datum:** 2026-07-25 | **Status:** Akzeptiert

**Kontext:** `AudiobookLibrary.scanLocalFiles()` behandelte bisher jede Audiodatei in `Audiobooks/` als eigenständiges Buch ohne Kapitelbezug – für ein einzelnes MP3 richtig, für ein mehrteiliges Hörbuch (z.B. LibriVox-Downloads mit einer Datei je Kapitel) aber falsch: ein 37-Kapitel-Roman wäre als 37 separate "Bücher" gelistet worden. Gleichzeitig zeigte sich beim Testen, dass der öffentliche `Music/Audiobooks`-Ordner unter Scoped Storage (Android 13+) ohne `READ_MEDIA_AUDIO` für die App unlesbar war (`Permission denied`, am Gerät reproduziert) – ein reiner Dateisystemzugriff reicht auf modernen Android-Versionen nicht mehr.

**Entscheidung:**
- Ein Unterordner in `Audiobooks/` mit mehreren Audiodateien wird zu einem Buch mit echter Kapitelnavigation – eine Datei je Kapitel (`Chapter.clipStartMs/clipEndMs = 0`, wie im `Chapter`-Datenmodell für dieses Muster bereits vorgesehen). Kein Umbau am Player nötig, das Muster existierte für gestreamtes LibriVox schon.
- Ordnername-Konvention `"Titel - Autor"` (Trennung am ersten `" - "`); ohne Trenner wird der ganze Ordnername zum Titel, Autor "Unbekannt".
- Ordner mit `ncc.html` bleiben `daisyBooks()` vorbehalten (nicht doppelt als Mehrkapitel-Ordner erfasst).
- `READ_MEDIA_AUDIO` als reguläre, im Onboarding abgefragte Berechtigung ergänzt (`PermissionsGuide`), da minSdk 33 sie ohnehin voraussetzt.

**Konsequenzen:**
- Lokale Hörbücher aus mehreren Dateien (curl-Downloads, Bibliotheks-Rips) sind jetzt ohne Zusatzcode direkt abspielbar, inklusive Kapitelsprung/-liste.
- Neue Pflichtberechtigung im Onboarding – ein weiterer Dialog, den Nutzer bestätigen müssen.
- Nicht getestet: reiner `adb push` in den App-eigenen externen Ordner (`getExternalFilesDir`) scheiterte ebenfalls mit `Permission denied` beim Lesezugriff der App auf von außen (adb/shell) hineingelegte Dateien – ein bekanntes Scoped-Storage-Verhalten. Für spätere Doku relevant: Dateien für lokale Hörbücher gehören in den **öffentlichen** `Music/Audiobooks`-Ordner (mit `READ_MEDIA_AUDIO`), nicht in den App-privaten Ordner, wenn sie von außerhalb der App dorthin gelangen sollen.

---

## ADR-026: WakeWordService läuft durchgehend – Pause/Resume statt Stop/Restart
**Datum:** 2026-07-26 | **Status:** Akzeptiert

**Kontext:** Der Dauerbetriebs-Check deckte einen realen Ausfall auf: `LauncherActivity` stoppte `WakeWordService` bei jedem Konversationsturn komplett (um das Mikrofon für STT freizugeben) und startete ihn danach über `startForegroundService()` neu. Android 14+/15 verbietet genau diesen Neustart-Typ für Mikrofon-Foreground-Services, wenn die App gerade nicht im Vordergrund ist (`SecurityException`). Am Gerät reproduziert: Der Service beendete sich nach einem fehlgeschlagenen Neustart selbst, `WakeWordWatchdog` scheiterte am selben Restart-Versuch – das Weckwort blieb dauerhaft aus, ohne Absturz oder Crash-Log, bis die App manuell wieder geöffnet wurde.

**Entscheidung:** `WakeWordService` wird nie mehr während des normalen Betriebs komplett gestoppt. Statt `stopService()`+`startForegroundService()` schicken `WakeWordService.pauseListening()`/`resumeListening()` ein Kommando per normalem `startService()` an die bereits laufende Instanz – das ist kein neuer Foreground-Start und daher nicht von der Android-Regel betroffen. Intern ruft das nur `OpenWakeWordEngine.stop()`/`start()` auf (Mikrofon/Thread freigeben bzw. neu starten), ohne die geladenen ONNX-Modelle zu entladen. Zwei echte Kaltstarts bleiben bestehen: `onResume()`-Fallback und der initiale Start nach dem Onboarding – beide garantiert aus einem Vordergrund-Kontext.

**Konsequenzen:**
- Behebt den Kernfall (jeder Gesprächsturn) vollständig – am Gerät verifiziert, kein `SecurityException`-Treffer mehr im Log.
- Restrisiko bleibt: ein echter Prozess-Tod (OOM-Kill) im Hintergrund bräuchte weiterhin einen vollständigen `WakeWordWatchdog`-Neustart, der theoretisch noch scheitern kann, falls die App exakt dann im Hintergrund ist. Deutlich seltener als der behobene Fall, nicht weiter verfolgt.
- Nebeneffekt: Pause/Resume ist schneller als der vorherige volle Stop/Neustart, da die ONNX-Modelle geladen bleiben.

---

## ADR-027: Ambiente-UI (Statuskugel + Hörbuch-Player) additiv statt als Umbau; Querformat als Standardausrichtung
**Datum:** 2026-07-26 | **Status:** Akzeptiert

**Kontext:** Der Bildschirm war bisher ein reiner Entwickler-Debugscreen, für den
blinden Primärnutzer ohnehin irrelevant. Angehörige/Besucher sollten stattdessen
sehen können, was Lina gerade tut, und optional ein laufendes Hörbuch bedienen
können. Es existierte kein zentraler Aktivitätszustand (nur ~18 verstreute
`statusText`-Zuweisungen) und keine Möglichkeit, "spricht gerade" abzufragen
(`TtsEngine` kannte das Konzept nicht). Außerdem zeigte sich beim Verifizieren am
Gerät, dass das Tablet praktisch immer im Querformat liegt (Ständer, Wohnzimmer),
die App aber auf `screenOrientation="portrait"` fixiert war – sichtbares
Letterboxing auf den Screenshots.

**Entscheidung:**
- Rein additives Zustandsmodell (`LinaActivity` sealed class) neben dem
  bestehenden `statusText`, statt eines Umbaus – jede der ~18 bestehenden
  Zuweisungsstellen bekommt eine zusätzliche Zeile, kein bestehendes Verhalten
  ändert sich.
- `TtsEngine.isSpeaking(): Boolean` neu im Interface, per Polling (250ms) aus der
  UI abgefragt statt eines neuen Callback-Mechanismus – kein Push-System für
  TTS-Zustand im Projekt vorhanden, ein `@Volatile`-Boolean-Read ist praktisch
  kostenlos.
- Kein ViewModel/StateFlow eingeführt – im ganzen Projekt existiert bisher keine
  solche Schicht; für zwei Screens mit bescheidenen Echtzeit-Anforderungen
  (Kugel-Phase, 1x/s Hörbuch-Fortschritt) wäre das unpassend viel neue
  Architektur. Stattdessen `LaunchedEffect`+`delay`-Polling, exakt wie an anderen
  Stellen im Projekt bereits üblich.
- Statuskugel bewusst rein dekorativ (kein Touch-Target), Stil schlicht/flach
  ohne Glow/Blur (Performance auf dem Lenovo-Tablet), Zustände nur über
  Bewegungscharakter unterschieden, nicht über neue Farben (bleibt bei
  Schwarz/Weiß/Gold).
- Debug-Eingabefeld/Log komplett aus der UI entfernt statt nur versteckt – der
  `dev.lina.DEBUG_INPUT`-Broadcast-Mechanismus bleibt unabhängig davon
  funktionsfähig, da der `BroadcastReceiver` `processDebugInput()` direkt
  aufruft.
- `android:screenOrientation` von `portrait` auf `sensorLandscape` geändert
  (statt festem `landscape`), damit beide Querformat-Rotationen erlaubt sind,
  aber nie auf Hochformat zurückgefallen wird. Layout von `Column` auf `Row`
  umgebaut: Kugel+Status und Hörbuch-Player nebeneinander statt untereinander.

**Konsequenzen:**
- Angehörige/Besucher sehen den Aktivitätszustand und können ein Hörbuch direkt
  bedienen, ohne dass sich am primären Sprachinterface etwas ändert.
- Fand nebenbei einen echten Bug: `LinaTypography.labelLarge` hatte fest Gold als
  Textfarbe hinterlegt, wodurch Button-Beschriftungen auf dem goldenen
  `Button`-Hintergrund unsichtbar waren (Gold auf Gold) – behoben, indem
  `labelLarge` keine feste Farbe mehr vorgibt und stattdessen den vom jeweiligen
  Container gesetzten `LocalContentColor` erbt.
- Jede neue Bildschirmausgabe (z.B. künftige Screens) muss diese Falle im Blick
  behalten: Text-Styles mit fest codierter Farbe sind nur für direkt auf dem
  Hintergrund sitzenden Text sicher, nicht für Text auf farbigen Containern.
- Kein Rollback-Pfad für Hochformat vorgesehen – falls das Tablet doch einmal
  hochkant genutzt wird, dreht sich die App nicht mit (`sensorLandscape` erlaubt
  nur die beiden Querformat-Rotationen).

---

## ADR-028: Schlafmodus über Fenster-Helligkeit statt Systemeinstellung
**Datum:** 2026-07-26 | **Status:** Akzeptiert

**Kontext:** Nutzerwunsch nach einem Sprachbefehl, der abends Bildschirm und
Lautstärke gemeinsam für die Nacht herunterfährt. Eine echte Änderung der
Systemhelligkeit (`Settings.System.SCREEN_BRIGHTNESS`) würde die Berechtigung
`WRITE_SETTINGS` voraussetzen, die eine Nutzerfreigabe über eine separate
Systemeinstellungsseite erfordert (kein normaler Laufzeit-Dialog) – ein hoher
Reibungsaufwand für ein Feature, das nur wirkt, während Lina ohnehin läuft.

**Entscheidung:** Der Schlafmodus setzt `Window.attributes.screenBrightness`
der eigenen `LauncherActivity` auf einen niedrigen Festwert (0.04) statt die
Systemeinstellung zu ändern. Das ist eine reine Fenster-Eigenschaft ohne
Sonderberechtigung, wirkt aber genauso auf die tatsächliche Display-Helligkeit
(am Gerät über `dumpsys display` bestätigt: `Display Brightness=0.04`), weil
Lina als Home-App dauerhaft im Vordergrund läuft. "Schlafmodus aus"/"wach auf"
setzt `screenBrightness` auf `BRIGHTNESS_OVERRIDE_NONE` zurück und übergibt die
Kontrolle wieder an die automatische Helligkeitssteuerung des Geräts. Die
Lautstärke auf 30% nutzt dieselbe bestehende Weiche (Hörbuch vs. System) wie
die übrigen Lautstärke-Befehle – keine neue Logik dafür.

**Konsequenzen:**
- Funktioniert ohne zusätzlichen Berechtigungsdialog, sofort nutzbar.
- Der Effekt gilt nur, solange `LauncherActivity` im Vordergrund ist – verlässt
  der Nutzer die App (praktisch nie, da Lina die Home-App ist), greift die
  normale Systemhelligkeit wieder. Für dieses Projekt kein Problem, da Lina
  bewusst immer im Vordergrund läuft.
- Kein Persistenzzustand: Ein Neustart der App (oder des Geräts) hebt die
  Dimmung automatisch auf, ganz ohne expliziten "wach auf"-Befehl – gewollt,
  da niemand den Schlafmodus über einen Neustart hinweg "vergessen" soll.

---

## ADR-029: Kontakt-Import auf SIM-Erkennung + vCard-Datei begrenzt statt echtem Geräte-Pairing; Best-Effort-SIM-Fingerabdruck statt echter ICCID
**Datum:** 2026-07-26 | **Status:** Akzeptiert

**Kontext:** Gewünscht war ursprünglich ein "Migrationsassistent", der aktiv
nach einem alten Gerät sucht und dessen Kontakte/Daten übernimmt – analog zu
Googles eigenem "Quick Switch". Das ist für eine Drittanbieter-App technisch
nicht umsetzbar: Es gibt keine offene Schnittstelle, über die eine normale App
ein anderes physisches Gerät finden oder dessen Daten abrufen könnte – Quick
Switch ist eine signierte Systemkomponente des Setup-Wizards.

Zusätzlich zeigte die Recherche: Die echte ICCID (eindeutige SIM-Seriennummer)
ist auf Android 10+/API 33 für Apps ohne Trägerrechte meist nicht mehr lesbar
(`SecurityException` oder geschwärzter Platzhalter) – ein einzelner
`getSimSerialNumber()`-Aufruf ist daher kein verlässliches Signal mehr.

**Entscheidung:**
- Realistischer Ersatz für den "Migrationsassistenten": (1) automatische
  SIM-Erkennung + Import (das explizit gewünschte Kernfeature) und (2) ein
  Sprachbefehl, der Androids eigenen Storage-Access-Framework-Dateipicker für
  eine vCard-Datei (.vcf) öffnet – das universelle Kontakt-Exportformat, das
  jedes alte Telefon (Android, iPhone, Feature-Phone) erzeugen kann. Für
  Google-Konto-Sync während der Android-Ersteinrichtung ist kein Lina-Code
  nötig, da `ContactRepository` diese Kontakte ohnehin automatisch mitliest.
- Der SIM-Fingerabdruck (`SimIdentity.composite()`) kombiniert Subscription-ID,
  Carrier-Name, Länderkennung und – falls lesbar – ein ICCID-Suffix zu einem
  Best-Effort-Signal statt einer garantiert eindeutigen ID. Jedes Feld wird
  einzeln gelesen (ein fehlendes Feld leert nicht den ganzen Fingerabdruck).
  Bewusster Tradeoff: lieber gelegentlich unnötig nachfragen (falscher
  Positiv-Treffer) als eine echte SIM-Änderung zu verpassen.
- Dateibasierter Import statt passivem Ordner-Beobachten (`FileObserver` auf
  Downloads): Scoped Storage (API 29+) erlaubt normalen Apps keinen
  unbeaufsichtigten Zugriff auf beliebige geteilte Dateien außerhalb kuratierter
  MediaStore-Sammlungen, und `.vcf` ist kein Medientyp mit eigener granularer
  Berechtigung. Der Dateipicker (`ACTION_OPEN_DOCUMENT`) braucht dagegen keine
  Sonderberechtigung und umgeht das Problem vollständig – die einmalige
  Dateiauswahl durch einen Helfer ersetzt gleichzeitig die sonst nötige
  Ja/Nein-Sprachbestätigung (die Auswahl selbst ist die Bestätigung).
- Neue Berechtigung `WRITE_CONTACTS` (Kontakte schreiben), da bisher nur lesend
  zugegriffen wurde.

**Konsequenzen:**
- Deckt den expliziten Hauptfall (SIM mit Kontakten bringen) und den
  realistischen Ersatzfall (Kontakte aus einer Exportdatei) ab, ohne eine
  technisch unmögliche Geräte-Suche vorzutäuschen.
- Ein Helfer muss die Datei einmal im System-Dateipicker antippen – das kann
  keine Sprachsteuerung ersetzen, da es System-UI außerhalb von Linas Kontrolle
  ist. Für den blinden Primärnutzer ändert sich dadurch nichts an der
  sonstigen Sprachbedienung.
- Der SIM-Fingerabdruck kann in seltenen Fällen falsch auslösen (z.B. wenn ein
  Mobilfunkanbieter seinen Netzbetreiber-Namen ändert) oder eine echte SIM-
  Änderung verpassen (wenn alle lesbaren Felder zufällig identisch bleiben) –
  am Testgerät (kein physischer SIM-Steckplatz belegt) nicht mit einem echten
  SIM-Wechsel überprüfbar, nur über `SimIdentityTest` unit-getestet.
