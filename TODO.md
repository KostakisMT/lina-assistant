# TODO.md – Lina · Taskboard

> Quelle der Wahrheit für alle Tasks.
> Vor dem Start: Task mit `[~] @name` claimen um Konflikte zu vermeiden.
> Nach Abschluss: `[x]` setzen + CHANGELOG.md Eintrag.

## Legende
```
[ ]  offen
[~]  in Arbeit – @name dahinter schreiben
[x]  erledigt
[!]  blockiert – Grund angeben
```

---

## 🔴 Phase 0 – Projektsetup

- [x] Git initialisiert + initialer Commit (2026-06-20)
- [x] GitHub Repo angelegt + Push (2026-06-20, privat, `KostakisMT/lina-assistant`)
- [x] Android-Projekt anlegen (Kotlin, Jetpack Compose, API 33+, Gradle KTS)
- [x] Paketname festlegen: `dev.lina`
- [x] Modulstruktur anlegen (core/, feature/, ui/ laut CLAUDE.md)
- [x] `local.properties` anlegen
- [x] `.gitignore` konfigurieren
- [x] Vosk-Modell `vosk-model-small-de` herunterladen → `assets/`
- [x] `LinaTheme.kt` anlegen (Hochkontrast: #000000 / #FFFFFF / #FFD700)
- [x] `LauncherActivity` als Home-App registrieren (AndroidManifest)

---

## 🔴 Phase 1a – Kern-Infrastruktur

- [x] `TtsEngine` Interface + `TtsPriority` enum definieren
- [x] `AndroidTtsEngine` implementieren (weiblich, de-DE, Rate 0.9f, Warteschlange)
- [x] `SttEngine` Interface definieren
- [x] `VoskSttEngine` implementieren (Vosk Wrapper, Coroutine-Scope)
- [x] `WakeWordService` als ForegroundService (OpenWakeWord, ONNX Runtime)
- [x] `WakeWordWatchdog` implementieren (Samsung-Killschutz)
- [x] Autostart nach Boot (`RECEIVE_BOOT_COMPLETED`)
- [x] `PARTIAL_WAKE_LOCK` für stationären Betrieb
- [x] Battery-Whitelist Onboarding-Screen

---

## 🔴 Phase 1b – AccessibilityService

- [x] `LinaAccessibilityService` anlegen
- [x] `accessibility_service_config.xml` konfigurieren
- [x] Eingehende Anrufe erkennen → TTS: "Anruf von [Name]"
- [x] Eingehende SMS erkennen → TTS: "Neue Nachricht von [Name]"
- [x] Onboarding: Nutzer zu Accessibility-Einstellungen führen

---

## 🔴 Phase 1c – Kontakte & Intent

- [x] `ContactRepository` – Kontakte aus ContactsContract laden
- [x] `FuzzyContactMatcher` – phonetisches Matching (Arundhati, Eßfeld etc.)
- [x] Spitznamen-Mapping vorbereiten (konfigurierbar)
- [x] `IntentResolver` Interface + `ResolvedIntent` Datenklassen
- [x] `LocalCommandResolver` – Regex/Keywords Ebene 1
- [x] Intent-Definitionen für alle MVP-Befehle
- [x] `LlmIntentResolver` – Stub (Phase 2 Vorbereitung)

---

## ✅ Phase 1d – Feature: Anrufe

- [x] Anruf starten via Intent.ACTION_CALL + Kontakt-Auflösung
- [x] Eingehenden Anruf annehmen/ablehnen (via TelecomManager)
- [x] Aktiven Anruf beenden
- [x] TTS-Feedback für alle Anruf-States
- [x] Sprachbefehle: "Ruf Boris an", "Annehmen", "Ablehnen", "Auflegen"

---

## ✅ Phase 1e – Feature: SMS

- [x] SMS-Posteingang lesen (ContentResolver, neueste zuerst)
- [x] SMS vorlesen via TTS (Absender per PhoneLookup aufgelöst)
- [x] SMS senden via SmsManager (inkl. Multipart für lange Texte)
- [x] Antworten auf letzte SMS
- [x] TTS-Bestätigung nach Versand
- [x] Sprachbefehle: "Schreib Boris: [Text]", "Lies Nachrichten", "Antwort: [Text]"

---

## ✅ Phase 1f – Feature: Nachrichten

- [x] RSS-Parser implementieren (XmlPullParser, nativ)
- [x] Feeds vorkonfigurieren:
  - [x] Junge Welt (`https://www.jungewelt.de/rss.php`)
  - [x] unsere zeit (`https://www.unsere-zeit.de/feed/`)
  - [x] Spektrum der Wissenschaft (`https://www.spektrum.de/rss/news`)
  - [x] Yacht (`https://www.yacht.de/feed/`)
- [x] Lokaler Cache (JSON in filesDir)
- [x] WorkManager-Sync (stündlich)
- [x] Zusammenfassung-Modus (Titel + ein Satz)
- [x] Detail-Modus (vollständiger Artikel auf Nachfrage)
- [x] Sprachbefehle: "Was gibt es Neues?", "Mehr dazu", "Nächste", "Stopp"

---

## ✅ Phase 1g – Feature: Hörbücher (MVP-Hook)

- [x] ExoPlayer Integration (Media3)
- [x] Lokale MP3/M4B aus Storage laden (Audiobooks-Ordner)
- [x] Sanftes Onboarding (kuratierte Startliste: Djamilah, Tolstoi, Gorki, Brecht)
- [x] Fortschritt persistent speichern (SharedPreferences)
- [x] Librivox-Integration (Suche + Streaming, kein Login)
- [x] Schlaf-Timer mit Lautstärke-Fade-Out (30s)
- [x] Sprachbefehle: "Spiel Hörbuch ab", "Pause", "Weiter", "30 Sekunden zurück", "Was höre ich?", "Welche Hörbücher habe ich?", "Suche Brecht", "Stopp in 30 Minuten"
- [x] Kapitel-Infrastruktur: Playlist statt Einzeldatei, Kapitelansage, Fortschritt je Kapitel (2026-07-20)
- [x] **Bugfix:** LibriVox endete nach dem ersten Abschnitt (nur `chapters.first()` wurde gespielt) – 2026-07-20
- [x] DAISY 2.02 lesen: `ncc.html` + SMIL + Zeitbereiche (2026-07-20, ADR-019)
- [x] Kapitel-Sprachbefehle: "nächstes Kapitel", "ein Kapitel zurück", "Kapitel drei", "welche Kapitel gibt es" (2026-07-20)
- [ ] Echtes DAISY-Buch der Hörbücherei am Tablet durchspielen (Struktur variiert je Produktionsstelle)
- [ ] LibriVox-Mehrkapitel-Wiedergabe am Tablet gegenprüfen (Streaming über Kapitelgrenze)
- [ ] Onboarding: Wie kommen DAISY-Bücher aufs Tablet? (CD-Import / Ausleihe der Hörbücherei)

---

## ✅ Qualitätssicherung – Tests

- [x] Test-Sourceset + JUnit im Build, CI läuft `testDebugUnitTest` (2026-07-20)
- [x] `GermanTimeParserTest` – Zeitangaben, Wiederholungen, Sachtext (2026-07-20)
- [x] `LocalCommandResolverTest` – Intents und ihre Abgrenzungen untereinander (2026-07-20)
- [x] `DaisyParserTest` – ncc.html, SMIL, Clock-Values, Sanitizing (2026-07-20)
- [ ] `FuzzyContactMatcher` testen (Arundhati, Eßfeld – spracherkennungs-kritisch)
- [ ] `RssFeedRepository` gegen gespeicherte Feed-Beispiele testen
- [ ] Mehrdeutigkeit "weiter" (Meldung vs. Hörbuch) kontextabhängig auflösen

---

## 🔴 Auslieferung Testnutzer – KW 26 (Deadline: 2026-06-27)

### Build & Release
- [x] `assembleDebug` – sauberer Build verifiziert (2026-06-20)
- [x] Porcupine → OpenWakeWord migriert (2026-06-20, kein API-Key mehr nötig)
- [ ] Release-Keystore anlegen (`.jks`)
- [ ] `assembleRelease` – signiertes Release-APK erzeugen

### Test auf echtem Tablet (Lenovo Idea Tab TB336ZU)
- [ ] APK installieren (USB/ADB over WiFi)
- [ ] Onboarding durchspielen: Berechtigungen, Battery-Whitelist, AccessibilityService
- [x] Wake Word getestet – "Hey Lina" (Custom-Modell v2 mit Nutzeraufnahmen): 5/5 erkannt (2026-07-04)
- [ ] STT: erkennt Vosk Befehle korrekt? (Ruf Boris an, Lies Nachrichten etc.)
- [ ] Anrufe: ausgehend + eingehend annehmen/ablehnen
- [ ] SMS: senden + lesen
- [ ] Nachrichten: RSS-Sync + Vorlesen
- [ ] Hörbücher: Librivox-Suche + Wiedergabe + Schlaf-Timer
- [ ] Dauerbetrieb: Service stabil nach 1h, 4h, über Nacht?

### Tablet vorbereiten für Nutzer
- [ ] Lina als Standard-Home-App setzen
- [ ] Android Schriftgröße auf Maximum
- [ ] TalkBack-Konflikte evaluieren (parallel zu Lina?)
- [ ] WLAN konfigurieren
- [x] Kontakte eintragen (Arundhati, Boris, Ulla, Annika, Sabine, Dirk, Hannah, Gudrun) – per adb mit Beispielnummern (2026-07-02)
- [ ] Testanruf mit echtem Kontakt
- [ ] Kurzanleitung erstellen (große Schrift oder Audioformat)

### Risiken & Showstopper
- [ ] Lenovo/ZUI Battery-Optimierung – killt es den Service?
- [ ] Vosk-Erkennungsqualität bei Umgebungsgeräuschen
- [ ] TTS-Lautstärke über Tablet-Lautsprecher ausreichend?
- [ ] RSS-Feeds erreichbar? (Junge Welt Paywall?)
- [ ] JAVA_HOME muss gesetzt sein für Builds (`openjdk@17` via Homebrew)

---

## 🟡 Plan bis zum Bewerbungsfenster (Stand 2026-07-18)

### Etappe 1 – Juli: Claude-Anbindung verifizieren & Release-fähig werden
- [ ] `CLAUDE_API_KEY` in local.properties hinterlegen (Nutzer) + Tablet-Test der freien Konversation (inkl. Verhörer-Befehle wie "Rumfe mal den Boris an" → Do-Intent)
- [x] Bestätigungston/Earcon während STT-Transkription und Claude-Wartezeit (Earcons.kt; Klang auf Gerät noch validieren) – 2026-07-18
- [ ] Release-Keystore anlegen (`.jks`, lokal) + signiertes `assembleRelease`-APK
- [ ] SSH-Key für GitHub einrichten (aktuell HTTPS)

### Besuch Testnutzer #2 – nächste Woche (verschoben von 2026-07-19)
- [ ] Aufnahme-Runden Zukunftsbefehle (Wetter/Nachrichten/Brief/Podcast – Liste siehe Chat/Session)
- [ ] Wetter + Regionalnachrichten live mit Testnutzer durchspielen
- [ ] Nachtrainiertes Weckwort-Modell aufspielen und 5/5-Test wiederholen
- [ ] Battery-Whitelist + Dauerbetrieb prüfen, Abschluss Übergabe-Checkliste

### Etappe 2 – August: Robustheit & Gerätetest komplett
- [ ] Raumdistanz-Spike: GTCRN-Entrauschen vor Whisper (sherpa-onnx) prototypisch einbauen und auf dem Tablet messen (Fehlerrate vorher/nachher)
- [ ] Offene Gerätetests abarbeiten (siehe Auslieferungs-Checkliste oben: Anrufe, SMS, RSS, Hörbücher, Onboarding)
- [ ] Dauerbetrieb: Service-Stabilität 1h / 4h / über Nacht auf dem Lenovo (ZUI-Battery-Killer)
- [ ] Tablet des Zieltestnutzers vorbereiten: Wake-Word-Nachtraining mit seiner Stimme ("Aufnahme"-Befehl → training/)

### Etappe 3 – September: Sichtbarkeit & Antragsreife
- [ ] 2-Minuten-Demo-Video: Lina auf dem Tablet (Anruf, Nachrichten, Konversation) – für Landingpage und Anträge
- [ ] Demo-Video auf Landingpage einbinden (mit Transkript/Untertiteln – barrierefrei)
- [ ] Fördermittel-Unterlagen finalisieren (intern, siehe lokaler Ordner)
- [ ] Kurzanleitung für Nutzer in Audioform aufnehmen

### Etappe 4 – Oktober/November: Einreichen & erzählen
- [ ] Förderbewerbung einreichen (Fenster: 01.10.–30.11.2026)
- [ ] Lina in Accessibility-/FOSS-Communities vorstellen (Foren, Mastodon, ggf. Vortrag)

## 🟢 Open-Source-Launch

- [x] Datenschutz-Sweep: NUTZERPROFIL.md gitignored, Klarnamen durch fiktive ersetzt (2026-07-16)
- [x] LICENSE (Apache 2.0) + NOTICE.md (2026-07-16)
- [x] README + CONTRIBUTING publikumstauglich (2026-07-16)
- [x] Landingpage `docs/index.html` (barrierefrei, Hochkontrast) (2026-07-16)
- [x] Frisches öffentliches Repo ohne History: `KostakisMT/lina-assistant` (alt: `lina-assistant-private`, bleibt Archiv) – 2026-07-18
- [x] GitHub Pages aktiv: https://kostakismt.github.io/lina-assistant/ – 2026-07-18
- [x] Repo-Beschreibung, Homepage + Topics gesetzt – 2026-07-18

---

## ⏰ Erinnerungen & Wecker

- [x] AlarmManager-Infrastruktur, offline, Doze-fest (2026-07-20)
- [x] Deutsches Zeitparsing lokal (relativ, Uhrzeit, halb/viertel, täglich) (2026-07-20)
- [x] Ansage per TTS + Benachrichtigung als Rückfall (2026-07-20)
- [x] Claude-Tools als Fallback für verstümmelte Eingaben (2026-07-20)
- [x] Rescheduling nach Neustart (BootReceiver) (2026-07-20)
- [ ] Neustart-Rescheduling am Gerät verifizieren
- [ ] Einzelne Erinnerung per Sprache löschen ("lösche die Erinnerung an den Arzt")
- [ ] Exact-Alarm-Recht auf dem Zielgerät prüfen

---

## 📷 Dokument-Vorlesen (Meilenstein 3)

- [x] CameraX-Rückkamera-Aufnahme, headless mit eigenem Lifecycle (2026-07-20)
- [x] Vision-Auswertung über Claude, relevanzgefiltert + "alles vorlesen" (2026-07-20)
- [x] Intent + Folgefenster (ja/alles, wiederhole, nochmal) (2026-07-20)
- [x] Debug-Befehl "testfoto" zum Ausrichten des Rahmens (2026-07-20)
- [ ] Rahmen beim Testnutzer aufkleben und mit "testfoto" ausrichten
- [ ] Mit echter Post testen (mehrseitig, Umschlag, Behördenbrief)
- [ ] Latenz prüfen (~14s) – ggf. kleineres Bild oder Streaming
- [ ] Offline-Alternative (On-Device-OCR) evaluieren – Backlog

---

## 🔵 Phase 2 – Geplant (nicht jetzt)

- [x] STT: Whisper über sherpa-onnx integriert (base int8, de) – 2026-07-02
- [x] TTS: Piper über sherpa-onnx integriert (de_DE-ramona-low) – 2026-07-02
- [x] Piper-Stimme mit Nutzer validiert → de_DE-dii-high gewählt (2026-07-04, ADR-016)
- [x] NOTICE.md mit Stimm-Attribution (OpenVoiceOS, CC BY-NC-SA) + LICENSE (Apache 2.0) angelegt – 2026-07-16
- [ ] STT-Wartezeit (~2s) akustisch überbrücken (kurzer Bestätigungston)
- [ ] STT-Robustheit bei Raumdistanz: Entrauschen vor Whisper prüfen (sherpa-onnx Speech-Enhancement/GTCRN – gleiche Runtime; Alternativen: RNNoise, Android NoiseSuppressor)
- [x] LLM-Anbindung: Claude API für freie Konversation (`ClaudeConversation`, ADR-017) – 2026-07-16
- [ ] Claude-Anbindung auf dem Tablet testen (echter API-Key in local.properties)
- [ ] LlmIntentResolver implementieren (lokales Modell – Backlog, ggf. obsolet durch ADR-017)
- [ ] Onleihe-Integration (Bibliothek per Ausweis)
- [ ] Podcast-Streaming (gPodder-Backend)
- [ ] Sprach-Einkauf: Wolt, Rewe Express, Picnic
- [ ] WhatsApp via AccessibilityService

---

## ✅ Erledigt

<!-- Erledigte Tasks hierher verschieben mit Datum -->
<!-- [x] 2026-05-29 – Projektdokumentation erstellt -->
