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

**Kontext:** Die Standard-Piper-Frauenstimmen (ramona/kerstin/eva_k) existieren nur in "low"-Qualität und wurden vom Entwickler als unzureichend bewertet. Hochwertige deutsche Piper-Stimmen: thorsten-medium/high (männlich, freie Lizenz) sowie die OpenVoiceOS-Stimmen dii-high/miro-high (CC BY-NC-SA 4.0). A/B-Test aller 6 Kandidaten auf dem Zielgerät per Laufzeit-Stimmwechsler.
**Entscheidung:** `de_DE-dii-high` wird Linas Standard-Stimme. Die NC-Lizenz ist zulässig, weil das Projekt von einem gemeinnützigen Verein getragen wird und kein kommerzieller Vertrieb stattfindet.
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
