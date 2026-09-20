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

---

## ADR-030: LibriVox-Genre-Suche als feste Taxonomie + Stichwort-Fallback statt freier Themensuche
**Datum:** 2026-07-26 | **Status:** Akzeptiert

**Kontext:** ADR-010/011 hatten LibriVox bewusst auf Titel-/Autorensuche
begrenzt. Der Nutzer wollte jetzt zusätzlich wissen können, was er abspielen
kann, und Lina soll LibriVox nach deutschen Inhalten nach Thema/Genre
durchsuchen können. Live gegen die echte LibriVox-API geprüft (nur lesende
`curl`-Aufrufe): LibriVox hat **keine freie Themensuche** – nur eine feste,
englischsprachige Genre-Taxonomie (`?genre=<exakter Name>`) ohne eigenen
Genres-Endpunkt (musste von `librivox.org/search`s Dropdown gescrapt werden).
Ein nicht existierender Genre-Name liefert **HTTP 500**, keine leere Liste.
Außerdem wurde dabei entdeckt, dass der bestehende `language`-Parameter von
der API ignoriert wird (serverseitig wirkungslos) und dass der bisherige
`fields=`-Parameter beim Parsen praktisch nur die Buch-ID durchließ – beides
echte, seit dem ursprünglichen MVP unbemerkte Bugs, die mit repariert wurden.

**Entscheidung:**
- Genre-Suche nutzt eine hartkodierte Taxonomie-Liste (`LibrivoxGenres.
  TAXONOMY`, Top-Level-Fiction-Genres + erste Ebene unter "*Non-fiction" –
  bewusst nicht die volle, mehrfach verschachtelte ~140-Einträge-Liste mit
  mehrdeutigen Doppel-Namen) plus eine kleine deutsche Synonymtabelle für die
  bekannten Interessen des Nutzers. Nutzereingaben werden **immer** gegen diese
  Liste validiert, bevor ein Netzwerkruf mit `?genre=` passiert – nie Rohtext.
- Findet die Genre-Suche nichts (oder wurde kein Genre erkannt), fällt
  `AudiobookLibrary.searchByTopic()` automatisch auf die normale
  Stichwortsuche zurück. Lina sagt an, was tatsächlich passiert ist
  ("gefunden in der Kategorie X" vs. "stattdessen nach dem Stichwort
  gesucht"), statt Präzision vorzutäuschen, die die API nicht liefern kann.
- Sprachfilter jetzt client-seitig (auf das von der API gelieferte
  `language`-Feld), mit höherem `limit`, um nach dem Filtern noch brauchbar
  viele Treffer übrig zu haben – der Server filtert trotz Parameter nicht.

**Konsequenzen:**
- Für Themen mit passender Taxonomie-Kategorie (Marxismus → "Political
  Science", live verifiziert: Treffer ist tatsächlich das *Manifest der
  Kommunistischen Partei*) funktioniert die Suche gut. Für Themen ohne
  passende Kategorie (z.B. "Segeln" → "Nautical & Marine Fiction", aber
  deutschsprachige Abdeckung dort dünn) bleiben die Ergebnisse mager – das ist
  eine echte, dauerhafte Grenze der LibriVox-API, keine Einschränkung, die
  später "nachgerüstet" werden könnte.
- Der `fields=`-Fix betrifft auch die bestehende Titel-/Autorensuche
  (`SearchAudiobook`) – dort waren Titel/Autor/Dauer vermutlich seit dem
  MVP-Start leer bzw. „Unbekannt", ohne dass es aufgefallen ist, weil die
  Suche trotzdem irgendein Ergebnis lieferte und abspielbar war.
- Zukünftige Erweiterungen der Genre-Liste müssen weiterhin gegen die echte
  API geprüft werden (ein falscher Name liefert HTTP 500, keinen Hinweis).

---

## ADR-031: Kalender-Termine laufen über eine verknüpfte Reminder statt eigenem Scheduling; Datum-Parser bewusst ohne Vorlauf/Wiederholung; Dokument-Erkennung als isoliertes Tool
**Datum:** 2026-07-26 | **Status:** Akzeptiert

**Kontext:** Neues Kalender-Feature (Datum-Ansage, Termine, automatische
Erinnerung, Dokument-Trigger, Familien-Wochenansicht). Die bestehende
Erinnerungs-Infrastruktur (`ReminderStore`/`ReminderScheduler`, AlarmManager,
Boot-Rescheduling) deckt "automatische Erinnerung" bereits vollständig ab;
`GermanTimeParser` kann aber nur relative Zeiten/Uhrzeiten, keine echten
Kalendertage. Die Dokument-Vorlese-Funktion (`ClaudeConversation.readDocument()`)
hatte diese Session bereits zwei ernsthafte Hänger-Bugs (beide behoben) – jede
Änderung daran birgt Regressionsrisiko.

**Entscheidungen:**
1. **Kein zweites Scheduling-System.** Ein `CalendarEvent` legt beim Anlegen
   immer eine ganz normale `Reminder` über die bestehenden `ReminderStore`/
   `ReminderScheduler` an (Text `"Termin: $title"`, Standardzeit 9 Uhr ohne
   genannte Uhrzeit) und merkt sich deren `reminderId` nur zur gekoppelten
   Löschung. Dauerbetrieb, Doze-Festigkeit und Boot-Rescheduling kommen damit
   kostenlos mit, ohne eine zweite AlarmManager-Integration zu pflegen.
2. **`GermanDateParser` bewusst ohne Vorlauf-Konfiguration und ohne
   Wiederholung.** Kein "erinnere mich einen Tag vorher", keine
   wiederkehrenden Termine (nur `Reminder.daily`, falls je gebraucht) –
   Erinnerung ist immer am Tag selbst. Ebenso bewusst: kein automatischer
   Claude-Fallback innerhalb `CalendarManager.createFromSpeech()`, wenn die
   Datumsphrase nicht lokal erkannt wird (exakt wie `ReminderManager.
   createFromSpeech()` es bereits hält) – Ebene 2 greift nur, wenn die
   gesamte Äußerung schon auf Ebene 1 keinen Treffer hatte.
3. **Termin-Erkennung im Dokument als komplett isoliertes Claude-Tool.** Das
   neue `termin_erkannt`-Tool wird ausschließlich in `readDocument()`s eigenem
   Params-Builder registriert, niemals in `buildParams()`/`TOOLS` (dem freien
   Konversationspfad `ask()`). `readDocument()`s Rückgabetyp wechselt von
   `LinaReply` zu `DocumentReadResult` (Ansagetext + optionaler
   `SuggestedCalendarEvent`), damit Text und Terminvorschlag in einer Antwort
   zurückkommen, ohne einen zweiten Rundlauf zu brauchen. Die Ja/Nein-Nachfrage
   dazu läuft über einen komplett neuen, eigenen Dialog
   (`openDocCalendarFollowUp`/`handleDocCalendarFollowUp`) statt die
   bestehende `handleDocFollowUp()`-Verzweigung ("ja"/"alles" = ganzer Text)
   um eine dritte Bedeutung zu erweitern – der unveränderte Fall (kein Termin
   erkannt) verhält sich dadurch garantiert byte-identisch zum bisherigen Code,
   was am Gerät auch so verifiziert wurde, bevor der neue Zweig getestet wurde.
4. **Bestehende Erinnerungs-Regex korrigiert.** `ClearReminders`/
   `ListReminders` reagierten vorher auch auf "Termine" – das hätte "lösche
   meine Termine" fälschlich zum Löschen ALLER Erinnerungen gemacht. Beide
   Regexe reagieren jetzt nur noch auf "Erinnerung(en)"; `resolveCalendar()`
   läuft in der Erkennungskette vor `resolveReminder()` und beansprucht den
   gesamten "Termin"-Wortschatz exklusiv.

**Konsequenzen:**
- Löschen aller Termine storniert automatisch die verknüpften Erinnerungen
  (kein verwaistes Alarm-Objekt).
- `GermanDateParser` ist absichtlich einfacher als ein vollständiger
  Kalender-Parser – deckt die in der Praxis erwarteten Formulierungen ab,
  keine Rekurrenzregeln, keine Zeitzonen-Sonderfälle.
- Ein Dokument mit einem echten, positiv erkannten Termin (`termin_erkannt=true`)
  wurde nicht am Gerät verifiziert (braucht ein reales Foto mit Datum) – nur
  der unveränderte Negativ-Pfad ist bestätigt.

---

## ADR-032: Lokaler Gemma-3n-Pfad als eigener Build-Flavor neben Claude API (nicht Ersatz); Finetuning-Strategie
**Datum:** 2026-08-04 | **Status:** Modellwahl korrigiert (Llama-3.2-3B statt Gemma-Familie), funktionierender Adapter liegt vor – kein Android-Code

**Kontext:** Blindenvereine/NGOs, die als mögliche zukünftige Tester/Partner
in Frage kommen (ADR-023 hält Kandidaten bewusst offen und unbenannt), lehnen
eine Anthropic-Anbindung ab und fordern ein lokales Modell auf dem Tablet.
Das berührt ADR-017 (Claude API für freie Konversation, Tools, Persona),
ADR-018 (Claude Vision fürs Dokument-Vorlesen) und ADR-020/021/022 (Proxy,
Kostenmodell, Modell-Routing) – alle setzen bisher eine Cloud-LLM-Anbindung
voraus.

Ausgangsannahme war, das Lenovo Idea Tab (TB336ZU) sei zu schwach, um ein
lokales Modell überhaupt zu testen. Das trifft für das konkret gemeinte
Modell **Gemma 3n** (E2B/E4B) nicht zu: Google baut diese Modellfamilie
gezielt für genau diese Geräteklasse. Recherchestand:

- **Zielgerät:** TB336ZU = MediaTek Dimensity 6300, Mali-G57 MC2 GPU, 4GB-
  oder 8GB-RAM-Varianten, Android 15.
- **RAM-Bedarf Gemma 3n (Laufzeit, int4):** E2B (5B nominell, 2B effektiv
  durch "selective parameter activation") ~2GB RAM; E4B (8B nominell, 4B
  effektiv) ~3GB RAM. Passt rechnerisch selbst auf die 4GB-Variante.
- **Bundle-/Speicherplatzbedarf** (nicht RAM): E2B-it-int4 ~3.1GB,
  E4B-it-int4 ~4.4GB als `.task`/`.litertlm`-Bundle – deutlich mehr als der
  bisherige Whisper+Piper-Fußabdruck zusammen (~220MB).
- **Runtime:** Google AI Edge SDK / MediaPipe LLM Inference API
  (`.task`/`.litertlm`), eigenständig neben sherpa-onnx (ADR-015) – eine
  weitere native Laufzeitabhängigkeit, nicht mit dem bestehenden
  Whisper/Piper-Stack teilbar.
- **Multimodalität:** Gemma 3n verarbeitet nativ Text, Bild (MobileNet-V5-
  Encoder, 256/512/768px), Audio und Video als Input, Text als Output. Bild-
  Input ist relevant für ADR-018, Audio-Input nicht – Whisper bleibt STT.
- **Deutsch:** verbesserte Mehrsprachigkeit auch für Deutsch (WMT24++ ChrF
  50,1 %), aber ausdrücklich schwache Audio-Transkription auf Deutsch – für
  Lina irrelevant, da Gemma 3n hier nur Text (und ggf. Bild) bekommt.
- **Function Calling:** unterstützt, aber im "pythonischen" Format statt der
  JSON-Schema-Tools der Anthropic-SDK (`ClaudeConversation.TOOLS`) – nicht
  1:1 übertragbar, eigene Prompt-/Parsing-Schicht nötig. Googles
  **FunctionGemma** (270M, aus Gemma 3 abgeleitet) ist ein noch kleineres,
  dediziert auf Function-Calling trainiertes Modell – als möglicher
  zusätzlicher Baustein für die Tool-Routing-Ebene vermerkt, hier nicht
  entschieden.
- **Lizenz:** Gemma Terms of Use – kein Apache 2.0, eigenes Google-
  Lizenzwerk mit Prohibited-Use-Policy, Weitergabepflicht an nachgelagerte
  Nutzer (hier: den NGO-Betreiber) und einseitigem Widerrufsrecht durch
  Google. Anderer Charakter als die NC-Klausel der Piper-Stimme (ADR-016):
  dort nur eine Nutzungsart-Einschränkung, hier ein aktives Kontrollrecht
  des Lizenzgebers.
- **Test ohne Zielgerät:** Gemma 3n läuft quantisiert (4-bit) direkt auf dem
  Entwickler-Mac via MLX (`mlx-community`-Checkpoints, `mlx_lm.generate`,
  ab ~16GB Unified Memory). Konversationsqualität, Tool-Calling-
  Treffsicherheit, Persona und Raumgespräch-Filterung sind damit auf dem Mac
  bewertbar, ganz ohne Android-Gerät. Nur Latenz, Akkulaufzeit und
  thermisches Verhalten unter Android brauchen echte Zielhardware
  (Dimensity-6300-Klasse – nicht zwingend das eigene Tablet).
- **Finetuning:** LoRA/QLoRA ist der Standardweg für Gemma. MediaPipe
  unterstützt separate LoRA-Gewichte fürs On-Device-Deployment – ein kleiner
  Adapter statt ein komplett neu exportiertes Modell. LoRA-Finetuning ist
  auch direkt mit MLX auf dem Mac möglich, ohne Cloud-GPU-Miete.

**Entscheidung:**

1. **Zwei Build-Flavors statt Laufzeit-Fallback.** Anders als
   Whisper→Vosk oder Piper→AndroidTTS ist hier kein Laufzeit-Fallback
   sinnvoll: Die Modell-Assets sind mit mehreren GB zu groß, um beide Pfade
   in einer APK auszuliefern. NGO-Flavor: kein `CLAUDE_API_KEY`, kein Proxy,
   kein Kostenkontingent – ADR-020/021/022 entfallen für diesen Flavor
   komplett (echter Vereinfachungsgewinn). Privater/Standard-Flavor: ADR-017
   unverändert.
2. **Modellwahl:** E2B als Standard (passt auf 4GB-Geräte), E4B als Option
   für 8GB-Varianten – eine Konfigurationsfrage pro NGO-Gerätepark, keine
   Codegabel.
3. **Bewusst offen gelassene Fragen** (nicht stillschweigend übergangen):
   - Websuche/Nachrichten (ADR-024) hat im NGO-Flavor keine Entsprechung –
     entweder den bereits im Code liegenden `RssFeedRepository`-Fallback
     (ADR-024-Konsequenzen) reaktivieren, oder das Feature im NGO-Flavor
     bewusst weglassen.
   - Dokument-Vision (ADR-018) mit Gemma 3n statt Sonnet 5: Bildqualität und
     Layoutverständnis bei Behördenbriefen sind ungeprüft – für eine
     Zielgruppe, die Fehler nicht selbst gegenlesen kann, ein echtes Risiko.
     "Geht technisch" heißt hier ausdrücklich nicht "ist gut genug".
   - Die Lizenz-Weitergabepflicht (Gemma Terms of Use) an den NGO-Betreiber
     muss geklärt sein, bevor ein Gerät ausgeliefert wird.
4. **Finetuning ist Teil der Entscheidung, nicht Phase-2-Kosmetik.** Ein
   Stock-Gemma-3n-Checkpoint reicht nicht:
   - Tool-Calling-Zuverlässigkeit für Linas konkrete deutsche Werkzeuge
     (`anrufen`, `sms_senden`, `hoerbuch_abspielen`, `erinnerung_anlegen`,
     `termin_anlegen`, `dokument_vorlesen`, `stopp`, `gespraech_beenden`) im
     pythonischen Gemma-Format.
   - Die sicherheitskritische Raumgespräch-Erkennung (`gespraech_beenden`)
     ist aktuell ein langer, mehrteiliger Regelblock im Claude-System-Prompt
     (`BASE_PROMPT`) – ein kleines Modell hält sich an lange System-Prompts
     erfahrungsgemäß schlechter als Sonnet 5. Finetuning auf Beispielen statt
     auf noch mehr Prompt-Text.
   - Persona/Tonfall (kurz, warm, "wie eine gute Bekannte", kein Markdown,
     1–3 Sätze) konsistent zum bestehenden Charakter halten.
   - Robustheit gegenüber verstümmelten Whisper-Transkripten (vom Nutzer
     beobachtet, im Code kommentiert, z. B. "Rumfe, Boris an").

   Methode: LoRA/QLoRA auf Gemma-3n-E2B/E4B-Basis, separate LoRA-Gewichte
   fürs On-Device-Deployment. Erste Wahl fürs Training: MLX-LoRA auf dem
   Entwickler-Mac; Hugging-Face-QLoRA auf gemieteter GPU als Fallback, falls
   die Zielqualität mit MLX nicht erreichbar ist. Trainingsdaten werden
   synthetisch generiert, methodisch analog zum bestehenden
   Wake-Word-Trainingsmuster (`training/gen_samples.py`,
   `training/README.md`) – dort synthetische TTS-Audio-Samples, hier
   synthetische Dialogbeispiele (mehrere Formulierungsvarianten +
   Whisper-Verhörer je Tool, Raumgespräch-Negativbeispiele, freie
   Konversationsbeispiele für Tonfall). Pragmatischer Bootstrap: den
   bestehenden Claude-Pfad nutzen, um diesen Korpus zu generieren, bevor der
   lokale Pfad ihn ablöst. Evaluation über einen eigenen, von den
   Trainingsdaten getrennten Testsatz, automatisiert bewertbar (richtiges
   Tool getroffen? Ton konsistent? korrekt NICHT reagiert bei
   Raumgesprächen?) – analog zur Recall-/Fehlalarm-Messung beim
   Wake-Word-Training, lauffähig auf dem Mac vor jedem Gerätetest.

**Konsequenzen:**
- App-Größe im NGO-Flavor wächst um mehrere GB (E2B ~3,1GB / E4B ~4,4GB),
  weiterhin nur per `scripts/download-models.sh`-Muster geladen, nicht ins
  Git (wie alle ONNX/GGUF-Assets laut `.gitignore`).
- Für den NGO-Flavor entfällt die Notwendigkeit von Proxy, Gerätetoken und
  Kostenkontingent (ADR-020/021/022) vollständig – ein echter
  Vereinfachungsgewinn gegenüber dem Cloud-Pfad.
- Zwei parallel zu pflegende System-Prompts/Tool-Definitionen –
  Drift-Risiko für Linas Persona zwischen den beiden Pfaden.
- Kein einmaliger Trainingsprozess: Jede künftige Erweiterung von Linas
  Tool-Set braucht danach zwei Updates (`ClaudeConversation.TOOLS` UND die
  Finetuning-Daten des lokalen Pfads) – zusätzlicher Pflegeaufwand, den es
  beim reinen Cloud-Pfad nicht gab.
- Noch keine Entscheidung zu Websuche-/Vision-Ersatz im NGO-Flavor – siehe
  offene Fragen oben; TODO.md verfolgt die konkreten nächsten Schritte
  (Mac/MLX-Spike, Trainingsdaten-Generator, LoRA-Durchlauf, Build-Flavor-
  Grundgerüst, gezielter Gerätetest).
- Keine NGO wird hier namentlich benannt (ADR-023 bleibt gültig).

**Update vom selben Tag – erste Umsetzung (`training/llm/`):**

Tooling empirisch geprüft statt nur recherchiert: `mlx-vlm`/`mlx-lm` laden
`mlx-community/gemma-3n-E2B-it-*4bit` auf dem Entwickler-Mac fehlerfrei
(bestätigt entgegen einem älteren, öffentlichen `mlx-lm`-Issue zu fehlendem
Gemma-3n-Support – mit der hier installierten Version nicht mehr
reproduzierbar). Der Mac-Spike gegen 20 handgeschriebene deutsche Prompts
(Basismodell, kein Finetuning) ergab 11/20 korrekt – bestätigt empirisch
genau die beiden oben genannten Finetuning-Gründe: Tool-Aufrufe mit
Namensargument funktionieren bereits zero-shot, parameterlose Befehle
(„Stopp", „Lies die Post vor") und die Raumgespräch-Erkennung nicht.

Der erste LoRA-Finetuning-Versuch (39 synthetische Trainingsbeispiele) deckte
zwei reproduzierbare, modellarchitektur-spezifische Probleme in `mlx-lm`s
noch jungem Gemma-3n-Support auf: (1) `mlx-lm`s generische LoRA-Layer-
Erkennung wrapt versehentlich Gemma3ns AltUp-Mechanismus, was das Training
zum Absturz bringt – behoben durch explizite Ziel-Modul-Liste. (2) Selbst
mit dem Fix bekommt `self_attn.q_proj` reproduzierbar keinen Gradienten
(über alle 8 trainierten Layer hinweg exakt 0 geblieben), nur `v_proj`
trainiert tatsächlich. Details, Zahlen und der volle Befund in
`training/llm/README.md`. Mit nur 39 Beispielen war der resultierende
Adapter am Ende zu schwach, um die Greedy-Generierung überhaupt sichtbar zu
verändern (0/8 Verbesserung gegenüber dem Basismodell) – kein Hinweis auf
einen grundsätzlich kaputten Weg, sondern ein zu kleiner erster Versuch.
Nächster Schritt: deutlich größerer Trainingsdatensatz.

Nebenbei umgesetzt, unabhängig vom Finetuning-Ausgang: `ConversationEngine`-
Interface aus `ClaudeConversation` extrahiert (`core/llm/
ConversationEngine.kt`, `LinaReply` dorthin verschoben), `LauncherActivity`
auf den Interface-Typ umgestellt – reiner Refactor, `ClaudeConversation`
bleibt unverändertes Verhalten, `./gradlew compileDebugKotlin` grün. Das ist
die Stelle, an der ein künftiges `GemmaConversation` andockt (Phase D).

Phase D.5 (Formatkompatibilität eines Adapters fürs Gerät über MediaPipe/
`ai-edge-torch`) bleibt unangetastet – ohne einen überhaupt wirksamen
Adapter aus Phase C ist diese Frage noch nicht handlungsrelevant.

**Update vom selben Tag – Modellwechsel, Durchbruch:**

Der oben beschriebene erste Finetuning-Versuch (Gemma 3n) und ein Fallback-
Versuch mit Gemma-3-270M (kleines, architektonisch normales Modell fürs
Tool-Routing, Gemma 3n selbst nur prompt-basiert für freie Konversation)
scheiterten beide: **neun** Trainingsläufe über zwei Modelle, jede
Einzelvariable systematisch durchprobiert (LoRA-Keys, `scale`, Lernrate über
vier Größenordnungen, `--mask-prompt`, Batch-Größe, Datensatzgröße, sogar
4bit- vs. bf16-Basis) – alle divergierten im selben Muster. Code-Lektüre von
`mlx_lm/tuner/trainer.py`/`datasets.py` ergab keinen Bug in der Maskierung.

Tiefenrecherche (siehe TODO.md/README.md für Details) ergab: Llama-3.2-3B-
Instruct ist die mit Abstand am breitesten unterstützte Architektur in
mlx-lm (keine Gemma-Familien-Eigenheiten wie AltUp), wird von mehreren
Quellen explizit als aktuell bestes Modell seiner Klasse für On-Device-
Tool-Calling genannt, und hat Deutsch offiziell als eine von acht
Kernsprachen trainiert (nicht nur inzidentell wie bei Gemma 3n). Ein
bezahlter Cloud-GPU-Weg (RunPod + Axolotl/Unsloth, ~1-5 USD, hätte das
Stabilitätsproblem wahrscheinlich am zuverlässigsten gelöst) wurde vom
Nutzer explizit abgelehnt – **kostenlos/lokal bleibt die Vorgabe.**

**Entscheidung (korrigiert obige Modellwahl):** Statt Gemma 3n selbst zu
finetunen, wird **Llama-3.2-3B-Instruct als Router-Modell finegetuned**
(Tool-Aufruf / Raumgespräch-Stille / `frei_gespraech()` = Weiterleitung),
mit derselben mlx-lm-Pipeline, demselben Datensatz, denselben
Trainingsparametern wie bei den gescheiterten Gemma-Versuchen – einzige
Änderung war das Modell. Ergebnis: sauberere Konvergenz (Val Loss 3,01 →
0,096 über 100 Iterationen), dann Divergenz durch Overfitting (klassisches
Adam-Muster nach sehr niedrigem Loss, nicht dasselbe Phänomen wie bei den
Gemma-Läufen) – behoben durch Early Stopping auf den Iter-100-Checkpoint.
**Ergebnis gegen den gehaltenen Testsatz: 46/52 (88,5 %) korrekt**, gegenüber
Nullwirkung (Adapter = Basismodell) bei jedem Gemma-Versuch.

Gemma 3n bleibt wie ursprünglich vorgesehen unfinetuned/prompt-basiert für
die freie Konversation (dort lag die Zero-Shot-Qualität im Mac-Spike schon
bei 5/5) – nur die Rolle "Tool-Routing/Raumgespräch" wandert zu einem
zweiten, kleineren, finegetunten Modell (Llama-3.2-3B). Das ist eine
Zwei-Modell-Architektur auf dem Gerät statt der ursprünglich geplanten
Ein-Modell-Lösung – Konsequenz unten.

Nebenbefund für Phase D/E: ONNX Runtime GenAI (unterstützt Llama-2/3, Qwen,
Gemma, Phi mit int4-Quantisierung fürs Mobile, fertige vorkonvertierte
Pakete, dokumentierter separater LoRA-Adapter-Deploy-Weg über Olive) ist ein
ernsthafter Kandidat gegenüber MediaPipe/LiteRT – Lina hat mit
`onnxruntime-android` (Wake Word) bereits eine ONNX-Runtime-Abhängigkeit;
eine Erweiterung wäre keine vierte native ML-Runtime im Projekt, anders als
MediaPipe. Noch keine Entscheidung, nur als Alternative vermerkt.

**Konsequenzen des Modellwechsels:**
- Zwei lokale Modelle statt einem: Llama-3.2-3B (finegetuned, Tool-Routing)
  + Gemma-3n-E2B/E4B (unfinetuned, freie Konversation) – mehr Speicherplatz
  auf dem Gerät als ursprünglich geplant, aber Llama-3.2-3B-4bit ist mit
  ~2GB RAM-Bedarf noch im Rahmen des 4GB-Zielgeräts, in Summe mit Gemma 3n
  E2B (~2GB) knapp für 4GB-Geräte – **Speicherbudget für 4GB-Geräte muss in
  Phase D/E genau geprüft werden**, war vorher (ein Modell) unkritischer.
- Der halluzinierte-Werkzeug-Fall (`wetter_vorlesen()` für eine
  Wetter-Erwähnung) zeigt: die Router-Ausgabe muss serverseitig/Kotlin-
  seitig gegen die bekannte Tool-Liste validiert werden, nicht blind
  ausgeführt – ein unbekannter Funktionsname muss sicher auf
  `gespraech_beenden()`-artiges Verhalten zurückfallen, nie auf einen
  Absturz oder eine Ausnahme laufen.
- Frühere ADR-032-Annahme "ein Gemma-3n-Adapter reicht" ist überholt; die
  Finetuning-Strategie oben (Trainingsdaten-Generator, Eval-Aufbau) bleibt
  inhaltlich gültig, nur das Zielmodell hat sich geändert.

---

## ADR-033: Helfer-Anruf per Be My Eyes – App-Handoff statt Anruf-API
**Datum:** 2026-08-12 | **Status:** Akzeptiert

**Kontext:** Be My Eyes verbindet blinde/sehbehinderte Menschen per
Live-Videoanruf mit sehenden Freiwilligen – eine sinnvolle Ergänzung zum
Dokument-Vorlesen (ADR-018) für alles, was ein einzelnes Foto nicht abdeckt
(Objekte, Umgebung, Rückfragen in Echtzeit). Recherche vor der Umsetzung:
Be My Eyes hat **keine offene API**, mit der eine Drittanbieter-App einen
Anruf ins Freiwilligennetzwerk auslösen kann. Das einzige öffentliche
API-Programm ("Specialized Help") läuft umgekehrt – Unternehmen wie
Microsoft/Google werden von BME-Nutzer:innen angerufen, nicht der andere
Weg. Be My Eyes bewirbt aber eine Google-Assistant-Integration ("Ok Google,
ruf einen Freiwilligen") als "no button-pressing required" – dahinter steckt
vermutlich ein Android App Action/Deep-Link, dessen genaue Intent-Struktur
aber nicht öffentlich dokumentiert und nur durch Inspektion der BME-APK zu
ermitteln wäre.

**Entscheidung:** Drei denkbare Integrationsstufen abgewogen –
(1) App-Handoff per `PackageManager.getLaunchIntentForPackage`, ein Tap der
Nutzer:in bleibt; (2) denselben Deep-Link feuern, den Assistants App Action
auslöst, sobald dessen Struktur bekannt ist; (3) `LinaAccessibilityService`
den "Call a Volunteer"-Button selbst suchen und antippen lassen, wie für
Wolt/Rewe/Picnic vorgesehen (CLAUDE.md, noch nicht gebaut). Umgesetzt wird
**Stufe 1**. Begründung: Be My Eyes ist selbst für blinde Nutzer:innen
bedienbar (TalkBack-optimiert), der verbleibende eine Tap ist explizit durch
Leitprinzip 1 gedeckt ("Voice-First, nicht Voice-Only – Touch ist optionaler
Fallback"). Ein Live-Videoanruf zu einer unbekannten Person ist zudem
folgenreicher als ein einzelnes Foto an Claude Vision – dass die Nutzer:in
den Anruf selbst noch bestätigt, ist hier ein Feature, kein Kompromiss.
Stufe 2 bleibt ein mögliches Upgrade (kein Tap mehr nötig), sobald die
Deep-Link-Struktur aus der APK bekannt ist. Stufe 3 nur falls 1/2 nicht
ausreichen – `LinaAccessibilityService` liest bislang nur Notifications,
UI-Automation fremder Apps wäre hier Neuland und würde bei jedem
BME-Update brechen können.

**Umsetzung:** `feature/helper/HelperCallLauncher.kt` – öffnet Be My Eyes
(`com.bemyeyes.bemyeyes`) per Launch-Intent, öffnet bei fehlender
Installation stattdessen die Play-Store-Seite (kein automatischer Download).
Neuer Ebene-1-Intent `ResolvedIntent.CallHelper` in `LocalCommandResolver`
(Trigger u.a. "ruf einen Helfer an", "Be My Eyes", "hilfe beim sehen") –
steht bewusst vor `resolveCall`, sonst würde "ruf einen Helfer an" als
Kontaktname-Suche fehlinterpretiert. `AndroidManifest.xml` bekommt einen
`<queries>`-Eintrag für `com.bemyeyes.bemyeyes` (ab Android 11/API 30 sonst
für `PackageManager` unsichtbar).

**Konsequenzen:**
- Be My Eyes ist eine **externe Abhängigkeit**, die – anders als
  Whisper/Piper/OpenWakeWord – nicht gebündelt werden kann. Muss vor der
  Übergabe separat installiert sein (ONBOARDING.md/WARTUNG.md).
- Kein neues Berechtigungs-/Kostenrisiko: keine neue `uses-permission`, kein
  API-Key, keine Internetkosten für Lina selbst (der Videoanruf läuft
  komplett innerhalb der BME-App).
- Datenschutzlich eigenständig zu nennen (WARTUNG.md-Einwilligung ergänzt):
  Live-Video aus der Wohnung an eine anonyme Person ist folgenreicher als
  ein einzelnes Foto (ADR-018) – Lina selbst sieht oder speichert davon
  nichts, das läuft vollständig innerhalb von Be My Eyes.
- Kostenlos für die Nutzer:in (BME ist spendenfinanziert) – passt zum
  Prinzip "dauerhaft kostenlos" ohne Zahlungsdaten in der App.
- Offen: Stufe 2 (Deep-Link ohne Tap) erfordert, die BME-APK einmal auf die
  `shortcuts.xml`/App-Actions-Definition hin zu inspizieren – keine
  Sicherheitsumgehung, nur eine öffentliche Ressourcen-Datei lesen, aber
  bisher nicht gemacht.

---

## ADR-034: Gradle-Build-Flavor-Grundgerüst für ADR-032 (Phase D)
**Datum:** 2026-08-12 | **Status:** Akzeptiert – reine Scaffolding, kein lokaler LLM-Pfad

**Kontext:** ADR-032 legt die Zwei-Flavor-Architektur konzeptionell fest
(`standard` mit Claude API, `ngo` ohne), aber ohne konkrete Gradle-Umsetzung.
Diese ADR hält die tatsächlich gewählte Struktur fest – noch **ohne**
`GemmaConversation` oder sonstigen lokalen Konversations-Code (das bleibt
eine spätere Phase, abhängig vom Stand des Llama-3.2-3B-Finetunings).

**Entscheidung:**

1. **Flavor-Dimension `"distribution"`** mit `standard` (Default-Verhalten,
   kein Suffix – bestehende `dev.lina`-Installationen bei Testnutzer:innen
   dürfen nicht brechen) und `ngo` (`applicationIdSuffix = ".ngo"`,
   `versionNameSuffix = "-ngo"` – beide Varianten können nebeneinander auf
   einem Testgerät installiert sein).
2. **`CLAUDE_API_KEY` wandert vom `defaultConfig` in die Flavor-Blöcke.**
   `standard` liest ihn wie bisher aus `local.properties`. `ngo` setzt ihn
   **hart auf `""`**, unabhängig vom Inhalt von `local.properties` – ein in
   der Dev-Umgebung für den `standard`-Flavor gesetzter Key darf niemals
   versehentlich in einen NGO-Build durchsickern.
3. **Anthropic-SDK-Dependency nur `standardImplementation`.** Damit
   `ClaudeConversation.kt` (einzige Datei mit `com.anthropic.*`-Imports) für
   den `ngo`-Flavor nicht mehr auf dem Klassenpfad ist, zieht sie aus
   `src/main/kotlin/` nach `src/standard/kotlin/` um (Kotlin-Android-Plugin
   erkennt `src/<flavorName>/kotlin/` automatisch als zusätzliches
   Source-Set, keine weitere Gradle-Konfiguration nötig).
4. **`ConversationEngineProvider` als Flavor-Seam.** `LauncherActivity`
   (geteilter Code) darf `ClaudeConversation` nicht mehr direkt referenzieren
   – das würde die `ngo`-Kompilierung brechen. Stattdessen ruft sie
   `ConversationEngineProvider.create(...)` auf; **dieselbe** Funktion
   existiert wortgleich als `object` einmal in `src/standard/` (baut
   `ClaudeConversation`) und einmal in `src/ngo/` (liefert immer `null`).
   Beim Kompilieren einer Variante wird `src/main/` mit genau einem der
   beiden Flavor-Source-Sets gemerged – der Compiler sieht pro Variante nur
   eine Implementierung. Kein gemeinsames Interface in `src/main/` nötig,
   nur identische Signatur.
5. **Kein neuer Laufzeit-Zweig.** Der `ngo`-Flavor braucht keine einzige
   Änderung an `LauncherActivity`s Verhalten: `claude == null` ist bereits
   der gehärtete Pfad für "kein API-Key konfiguriert" (z.B. beim
   Dokument-Vorlesen: "Zum Vorlesen von Dokumenten brauche ich eine
   Internetverbindung..."). Der `ngo`-Flavor erzwingt über
   `ConversationEngineProvider` lediglich strukturell denselben Zustand, statt
   sich auf eine leere `local.properties` zu verlassen.

**Verifiziert:** `./gradlew testStandardDebugUnitTest testNgoDebugUnitTest`
und `./gradlew assembleDebug` (Aggregat-Task, baut beide Varianten) grün.
APK-Vergleich bestätigt die Trennung: `app-standard-debug.apk` enthält
`com/anthropic`-Referenzen in 5 dex-Dateien (~20MB in zwei zusätzlichen
dex-Dateien), `app-ngo-debug.apk` enthält keine einzige – kein Fall von
"Key leer, Code trotzdem mitgeschleppt".

**Konsequenzen:**
- `./gradlew testDebugUnitTest` (flavor-los) existiert nicht mehr – CLAUDE.md
  und `.github/workflows/build.yml` aktualisiert auf
  `testStandardDebugUnitTest`/`testNgoDebugUnitTest`. `assembleDebug` und
  `lint<Flavor>Debug` bleiben/wurden entsprechend angepasst.
- CI baut und testet ab jetzt **beide** Flavors bei jedem PR – ein
  versehentlicher Anthropic-Import in geteiltem Code (`src/main/`) fällt
  sofort als Kompilierfehler im `ngo`-Zweig auf, nicht erst beim NGO-Release.
- **Weiterhin offen (aus ADR-032 übernommen, hier nicht entschieden):**
  Websuche/Nachrichten (ADR-024) hat im `ngo`-Flavor keine Entsprechung –
  aktuell fällt "was gibt es Neues" dort auf die generische
  "Das habe ich nicht verstanden"-Meldung zurück (kein Absturz, aber
  irreführend). Dokument-Vision hat im `ngo`-Flavor ebenfalls keine
  Entsprechung (identisch zum bereits vorhandenen "kein Internet"-Text).
  Beides bewusst nicht in dieser Phase gelöst – siehe TODO.md.
- Kein `GemmaConversation` in dieser Phase: `ConversationEngineProvider` im
  `ngo`-Flavor ist ein reiner Platzhalter (`create(...) = null`). Das ist der
  Anschlusspunkt für Phase E, sobald ein finegetunter On-Device-Pfad steht.

---

## ADR-035: Ein gemeinsamer Werkzeug-Katalog als Voraussetzung für den lokalen Router; Regex nur noch für Reflexe
**Datum:** 2026-08-30 | **Status:** Vorgeschlagen – aus einer Live-Sitzung am Testtablet, noch nicht umgesetzt

**Kontext:** Beim Durchspielen vor der Übergabe sprach der Entwickler frei mit
Lina und stellte fest: „nichts funktioniert richtig". Die Auswertung des
Mitschnitts ergab zwei ineinandergreifende Ursachen, keine davon in der
Sprachausgabe oder im Feature-Code:

1. **Whisper verstümmelt bei Raumdistanz genau die Wörter, auf die es
   ankommt.** Gemessen: „Tolstoi" → „Teustol", „LibriVox" → „Privaks",
   „Onleihe" → „Interleihte", „Hörbuch" → „führbuch". Damit verfehlt die
   Regex-Ebene, die auf exakte Formulierungen angewiesen ist.
2. **Der Auffangpfad hatte für den betroffenen Bereich keine Werkzeuge.**
   `ClaudeConversation.TOOLS` kannte 10 Werkzeuge, `ResolvedIntent` hat 46
   Intents. Beim Hörbuch konnte Claude genau eins: abspielen. Auf „welche
   Hörbücher habe ich" antwortete Claude wahrheitsgemäß „ich habe keinen
   Zugriff auf eine Liste" – und dementierte damit eine Funktion, die es
   gibt. Für den Nutzer sieht das aus, als könne Lina nichts.

Entscheidend: **`training/llm/prompts/system_prompt_router_de.txt` listet
exakt dieselben 10 Werkzeuge.** Der lokale Llama-3.2-3B-Router aus ADR-032
erbt die Lücke unverändert. Die dort gemessenen 88,5 % (46/52) beziehen sich
auf diesen schmalen Katalog, nicht auf Linas tatsächlichen Funktionsumfang.

Ursache der Lücke ist strukturell: Intent-Liste und Werkzeug-Liste sind zwei
handgepflegte Listen ohne Verbindung. Sie sind auf 46 gegen 10 auseinander-
gelaufen, und **nichts im Projekt hat das bemerkt** – gefunden hat es der
Entwickler beim freien Sprechen, drei Stunden vor einer Übergabe.

**Entscheidung:**

1. **Ein Katalog, zwei Verbraucher.** Jede Fähigkeit wird einmal deklariert
   (Name, Parameter, Beschreibung, erzeugter `ResolvedIntent`). Daraus wird
   sowohl `ClaudeConversation.TOOLS` generiert als auch der Werkzeugblock in
   `system_prompt_router_de.txt`. Cloud-Pfad und lokaler Pfad können damit
   nicht mehr auseinanderlaufen.
2. **Ein Test, der die Drift bricht.** Ein Unit-Test schlägt fehl, sobald ein
   `ResolvedIntent` weder ein Werkzeug hat noch auf einer expliziten
   Ausnahmeliste steht. Auf die Ausnahmeliste gehören nur die internen
   Folgefenster-Intents (`NextNews`, `NewsDetail`, `AskAudiobookTopic` u.ä.),
   die nie aus freier Rede entstehen. **Das ist der eigentliche Kern dieser
   ADR** – ohne ihn steht dieselbe Lücke in drei Monaten wieder da.
3. **Die Regex-Ebene schrumpft auf Reflexe.** Sie bleibt zuständig für das,
   was sie allein kann: sofort und offline. Konkret Stopp, Pause, Weiter,
   lauter/leiser, Anrufsteuerung (annehmen/ablehnen/auflegen) und
   Schlafmodus. Kriterium ist nicht Erkennungsgüte, sondern
   Sicherheitsrelevanz oder Sofort-Unterbrechung: Sagt der Nutzer „stopp",
   während Lina redet, sind Sekunden Bedenkzeit ein Defekt.
   Alles andere geht an die Routing-Ebene.
4. **Die Routing-Ebene ist langfristig lokal** (ADR-032, Llama-3.2-3B-Router
   auf dem Tablet). Claude bleibt für **Nachrichten und freie Gespräche** –
   das deckt sich mit der bereits getroffenen Entscheidung, Nachrichten
   komplett über Claude+Websuche zu fahren (festgehalten im Test
   `Nachrichten gehen komplett an Ebene 2`).
5. **Reihenfolge:** Katalog und Test zuerst, danach das nächste
   Router-Training. Andernfalls wird zweimal trainiert – einmal auf den
   schmalen Satz, dann auf den vollen.

**Konsequenzen:**

- **Die 88,5 % aus ADR-032 sind kein übertragbarer Ausgangswert.** Mehr
  Werkzeuge bedeuten mehr Klassen; die Genauigkeit wird zunächst sinken und
  muss neu erarbeitet werden. Zusammen mit der dort dokumentierten
  Lauf-zu-Lauf-Varianz (ein zweiter Lauf: 59,6 %) heißt das: der lokale Pfad
  ist weiter von der Auslieferung entfernt, als die Zahl vermuten lässt.
- **Bis der lokale Router steht, trägt Claude die Routing-Last.** Das erhöht
  die API-Kosten gegenüber heute, weil die Regex bislang die häufigen Befehle
  abfängt. Der Nutzer hat für diesen einen Testnutzer bis zu 20 EUR/Monat als
  vertretbar bezeichnet. Verlässlich schätzen lässt sich das erst mit
  Nutzungsdaten; der größte Treiber ist die Websuche für Nachrichten, nicht
  die Gerätebefehle. Berührt ADR-020 (Proxy mit Nutzungszähler).
- **Wartezeiten werden häufiger.** Deshalb ist die am selben Tag gebaute
  Vertröstung (12s „Einen Moment, ich suche noch", harte Grenze 90s) eine
  **Voraussetzung** dieser Architektur, keine Politur.
- **Offline bleibt eine Lücke.** Ohne Netz und ohne lokalen Router bleiben nur
  die Reflexe. Lina muss das dann ansagen („Ich bin gerade nicht verbunden,
  ich verstehe nur die Grundbefehle") – stilles Scheitern ist für einen
  blinden Nutzer nicht von einem defekten Gerät zu unterscheiden.

**Belege aus der Sitzung (2026-08-30):** Nach dem Nachrüsten der elf
Hörbuch-Werkzeuge wurden dieselben verstümmelten Transkripte erneut
eingespielt. „Was ihr hörbücher habe ist." → `hoerbuecher_auflisten`.
„Spiele her und knecht vom Teustol" → `hoerbuch_suchen("Herr und Knecht
Tolstoi")` – das Modell korrigiert beide verstümmelten Namen selbst, was eine
Regex prinzipiell nicht leisten kann. Gegenbeispiel für Punkt 3: „Suche nach
Hörbischan in der Kategorie Politik" erreichte die Routing-Ebene **gar
nicht**, weil die lokale Regex `such\s+(.+)` vorher zugriff und den ganzen
verstümmelten Satz als LibriVox-Suchbegriff abfeuerte. Die Regex ist dort
nicht nur unvollständig, sie zerstört aktiv Eingaben, die die nächste Ebene
richtig verstanden hätte.

---

## ADR-036: Alle DOM-Parser gehen durch einen gemeinsamen gehärteten Builder (`SecureXml`)

**Datum:** 2026-08-23 | **Status:** Akzeptiert

**Kontext:** Lina parst XML aus zwei fremden Quellen: DAISY-Bücher (lokal, aber
von fremden Produktionsstellen) und LibriVox-RSS-Feeds (über das Netz, ohne
Nutzerinteraktion). `DaisyParser` war seit ADR-019 gegen XXE gehärtet,
`LibrivoxRepository.parseRssChapters()` nicht – dort stand ein blankes
`DocumentBuilderFactory.newInstance()`. Die Lücke fiel erst auf, als jemand die
Datei in ein anderes Projekt portierte und dort reviewte. Genau das ist das
Muster: die Härtung war Wissen in *einer* Methode, nicht Struktur im Projekt.

**Entscheidung:**

1. **Ein `object SecureXml` in `core/xml/`** liefert den einzigen erlaubten
   `DocumentBuilder`. `DaisyParser` und `LibrivoxRepository` rufen beide nur
   noch `SecureXml.newDocumentBuilder()`. Direkte
   `DocumentBuilderFactory.newInstance()`-Aufrufe sind im Projekt verboten
   (Eintrag in der "Niemals"-Liste in CLAUDE.md).
2. **`disallow-doctype-decl=true` als stärkste Sperre**, zusätzlich zur
   DaisyParser-Vorlage. Ohne DOCTYPE gibt es weder externe Entities noch
   Entity-Expansion – das erschlägt XXE und "Billion Laughs" in einem Zug.
   Vertretbar, weil **keiner** der beiden Aufrufer eine DTD braucht:
   DAISY-Dokumente werden vorher per `sanitizeXhtml()` entdoctyped (das war
   schon vorher nötig, weil die DAISY-DTD offline nicht ladbar ist), und
   LibriVox-RSS enthält keine.
3. **Die übrigen Schalter bleiben trotzdem drin** (load-external-dtd,
   external-general-entities, external-parameter-entities, `isValidating`,
   `isExpandEntityReferences`, `isXIncludeAware`) und jeder `setFeature`-Aufruf
   steckt in `runCatching`. Grund: Androids Expat-basierter
   `DocumentBuilderFactory` kennt nicht alle Xerces-Feature-URIs und wirft
   sonst `ParserConfigurationException`. Kein Schalter darf allein tragend
   sein.
4. **`setEntityResolver` als letzte Instanz** – parser-unabhängig, greift auch
   dann, wenn eine Plattform keines der Features kennt: ein Entity wird leer
   aufgelöst statt aus Datei oder Netz geladen.

**Konsequenzen:**

- Die Härtung kann nicht mehr an einer Stelle veralten. Neue XML-Quellen
  (z.B. der in TODO.md offene `RssFeedRepository`-Umbau von `XmlPullParser`
  auf `DocumentBuilder`) erben sie automatisch.
- `parseRssChapters` wurde von `private` auf `internal` gezogen, damit ein
  reiner JVM-Test die Härtung ohne Netzwerk prüfen kann – dieselbe Begründung
  wie bei `parseBooks`.
- Ein DAISY-Buch, das eine DTD wirklich bräuchte, würde jetzt gar nicht mehr
  parsen statt still ohne Entities. Bewusst in Kauf genommen: `sanitizeXhtml()`
  übersetzt die real vorkommenden benannten Entities ohnehin selbst in
  numerische, und der Fehlerfall ist ein nicht lesbares Buch – nicht ein
  ausgelesenes Dateisystem.
- **Nicht** gelöst: `RssFeedRepository` nutzt weiterhin `XmlPullParser`. Der
  lädt nichts extern nach und ist damit nicht anfällig, geht aber auch nicht
  durch `SecureXml` – die Regel gilt für DOM-Parser.
