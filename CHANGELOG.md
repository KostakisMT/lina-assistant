# CHANGELOG.md – Lina · VoiceFirst Assistant

> Nach JEDER abgeschlossenen Task hier eintragen.
> Format: Datum | Was | Warum | Dateien | Offen

---

## Format

```
## [YYYY-MM-DD] Kurztitel
**Was:** Was wurde implementiert oder geändert
**Warum:** Begründung / Kontext
**Dateien:** Welche Dateien angelegt oder geändert
**Offen:** Was ist noch nicht fertig oder bekannt problematisch
```

---

## [2026-07-18] Öffentlicher Launch: Repo + GitHub Pages live

**Was:** Frisches öffentliches Repo `KostakisMT/lina-assistant` (ein Initial-Commit, keine Alt-History) erstellt und gepusht; bisheriges privates Repo als Archiv umbenannt zu `lina-assistant-private` (volle History bleibt dort erhalten). GitHub Pages aktiviert (main /docs) → Landingpage live: https://kostakismt.github.io/lina-assistant/ – Repo-Beschreibung, Homepage-URL und Topics gesetzt. Lokales Arbeitsverzeichnis: `origin` = öffentliches Repo, `archive` = privates Archiv, Branch `old-main-archiv` sichert die alte History zusätzlich lokal.

**Warum:** Open-Source-Launch; die alte Git-History enthielt personenbezogene Daten und durfte nie öffentlich werden – daher History-freier Neustart.

**Dateien:** (Repo-Struktur; TODO.md, CHANGELOG.md)

**Offen:** Release-APK + F-Droid (Phase 2), Kurzvorstellung in Accessibility-Communities.

---

## [2026-07-16] Open-Source-Launch-Paket: LICENSE, NOTICE, README, Landingpage

**Was:** `LICENSE` (Apache 2.0, kanonischer Text) und `NOTICE.md` (Attribution aller Modelle/Bibliotheken, insb. Piper-Stimme de_DE-dii-high CC BY-NC-SA mit Gemeinnützigkeits-Begründung nach ADR-016). README publikumstauglich umgeschrieben (Features, Technik-Tabelle, Build-Anleitung, Träger), `CONTRIBUTING.md` mit Grundregeln. Barrierefreie Hochkontrast-Landingpage `docs/index.html` für GitHub Pages (main /docs).

**Warum:** Voraussetzungen für die öffentliche GitHub-Veröffentlichung samt Landingpage.

**Dateien:** LICENSE, NOTICE.md, README.md, CONTRIBUTING.md, docs/index.html, TODO.md

**Offen:** Frisches öffentliches Repo (History enthält persönliche Daten), GitHub Pages aktivieren, Repo-Beschreibung/Topics.

---

## [2026-07-16] Datenschutz-Sweep für Open-Source-Veröffentlichung

**Was:** Alle personenbezogenen Daten aus dem getrackten Repo entfernt: `NUTZERPROFIL.md` ist jetzt gitignored (bleibt lokal), echte Kontaktnamen in Docs/Kommentaren durch fiktive Beispielnamen ersetzt, Versicherungs-/Gesundheitsdetails aus CLAUDE.md reduziert. `ClaudeConversation` bekommt Kontaktnamen jetzt zur Laufzeit vom Gerät (`ContactRepository.loadAll()`) statt hartkodiert im System-Prompt – funktioniert damit für jeden Nutzer.

**Warum:** Vorbereitung der öffentlichen GitHub-Veröffentlichung. Die Git-History enthält die alten Daten weiterhin → öffentliches Repo muss als frisches Repo ohne History starten.

**Dateien:** .gitignore, CLAUDE.md, ONBOARDING.md, TODO.md, CHANGELOG.md, DECISIONS.md, ClaudeConversation.kt, LauncherActivity.kt, FuzzyContactMatcher.kt

**Offen:** Frisches öffentliches Repo aus diesem Stand erzeugen (History-frei); LICENSE/NOTICE.

---

## [2026-07-16] Claude-API-Anbindung: freie Konversation (Ebene 2)

**Was:** Neue Klasse `ClaudeConversation` (core/llm): freie Konversation über die Claude API (anthropic-java SDK, Modell `claude-sonnet-5`) mit Lina-Persona, Dialoggedächtnis (max. 20 Nachrichten) und Prompt-Caching für den System-Prompt. Gerätebefehle, die Ebene 1 nicht versteht (z.B. Whisper-Verhörer), erkennt Claude über Tool-Definitionen (anrufen, sms_senden, sms_vorlesen, nachrichten_vorlesen, hoerbuch_abspielen, stopp) und reicht sie als `ResolvedIntent` zur lokalen Ausführung zurück. In `LauncherActivity` verdrahtet: unbekannte Eingaben gehen an Claude (Hintergrund-Thread), Antwort wird vorgelesen. API-Key kommt aus `local.properties` (`CLAUDE_API_KEY`); ohne Key bleibt Ebene 2 aus und die App verhält sich wie bisher. Thinking explizit deaktiviert (Latenz; Thinking-Tokens zählen gegen maxTokens). Build-Fixes: `Properties`-Import in build.gradle.kts, META-INF-Excludes für HttpClient5-Jars des SDK.

**Warum:** Vision "Gesprächspartnerin statt Kommando-Empfänger" (CLAUDE.md Nordstern); gleichzeitig Robustheit gegen STT-Verhörer bei Befehlen.

**Dateien:** core/llm/ClaudeConversation.kt (neu), LauncherActivity.kt, app/build.gradle.kts

**Offen:** Test auf dem Gerät mit echtem API-Key. STT-Wartezeit + Claude-Latenz akustisch überbrücken (Bestätigungston). Dialoggedächtnis ist nur im RAM (Neustart = vergessen).

---

## [2026-07-04] Custom-Weckwort "Hey Lina" trainiert und verifiziert

**Was:** Eigenes OpenWakeWord-Modell `hey_lina_v1.onnx` ersetzt den hey_jarvis-Platzhalter. Training unter `training/`: ~1000 synthetische "Hey Lina"-Aussprachen (904 LibriTTS-Sprecher + 6 deutsche Piper-Stimmen), MUSAN-Negative (12h), Hard Negatives ("Hey Nina", "Helena", …), Augmentierung (Hall, Rauschen, Tonhöhe/Tempo). Runde 2 zusätzlich mit 12 echten Weckrufen des Entwicklers (per neuem Debug-Befehl "Aufnahme" direkt am Tablet aufgenommen, 40-fach übergewichtet). Feature-Pipeline (`oww_features.py`) repliziert exakt die App-Pipeline. Echo-Unterdrückung: Weckwort-Erkennungen werden ignoriert, während/kurz nachdem Lina selbst spricht.

**Warum:** "Hey Jarvis" war Platzhalter; Runde 1 (rein synthetisch) erkannte die echte Nutzerstimme nur 2/5 – mit echten Aufnahmen 5/5.

**Dateien:** training/*.py, training/README.md, OpenWakeWordEngine.kt, LauncherActivity.kt, PiperTtsEngine.kt, download-models.sh

**Offen:** Für den Ziel-Testnutzer denselben Aufnahme+Nachtrainings-Ablauf wiederholen. Whisper versteht Befehle aus Raumdistanz unzuverlässig (Verhörer wie "Rumfe, Boris an") – nächster Verbesserungspunkt.

---

## [2026-07-04] Stimmen-A/B-Test → neue Standard-Stimme de_DE-dii-high

**Was:** Laufzeit-Stimmwechsler in `PiperTtsEngine` (`AVAILABLE_VOICES`, `switchVoice()`, thread-sicher via engineLock) + Sprach-/Debug-Befehl "Stimme <n>" / "nächste Stimme" in `LauncherActivity`. Sechs Stimmen auf dem Gerät verglichen (ramona/kerstin/eva_k low, thorsten medium, dii/miro high). Bugfix: AudioTrack-Puffergröße auf Framegröße (4 Bytes) gerundet – 22.05kHz-Modelle crashten sonst mit "Invalid audio buffer size". Gewählt: **de_DE-dii-high** (OpenVoiceOS) als Default, übrige Modelle aus Assets entfernt. Projektträger Inklusiv Aktiv e.V. (gemeinnützig) in CLAUDE.md dokumentiert – macht die CC-BY-NC-SA-Lizenz der Stimme nutzbar (ADR-016).

**Warum:** Nutzerfeedback: ramona-low klanglich unzureichend; alle freien deutschen Piper-Frauenstimmen sind nur "low"-Qualität.

**Dateien:** PiperTtsEngine.kt, LauncherActivity.kt, download-models.sh, CLAUDE.md, DECISIONS.md

**Offen:** Attribution der Stimme (OpenVoiceOS) in eine LICENSES/NOTICE-Datei aufnehmen. Vosk-Assets entfernen, wenn Whisper sich bewährt (−96MB APK). Langfristig: eigene freie deutsche Frauenstimme trainieren.

---

## [2026-07-02] Phase-2-Upgrade: Piper TTS + Whisper STT (sherpa-onnx)

**Was:** `PiperTtsEngine` (de_DE-ramona-low, weiblich, natürlich, offline; AudioTrack-Wiedergabe, Prioritäts-Queue, INTERRUPT) und `WhisperSttEngine` (Whisper base int8 multilingual, language=de, eigene Stille-Endpunkterkennung) über sherpa-onnx-AAR integriert. Beide hinter den bestehenden Interfaces mit Fallback auf AndroidTts/Vosk. Download-Script um AAR + Modelle erweitert. Auf dem Gerät verifiziert: Piper-Synthese ~0.3–0.5s/Satz, Whisper transkribiert "Ruf Arundhati an" fehlerfrei in 2.1s (Vosk verstand "ruf aroma an").

**Warum:** Vision "freundliche Stimme + natürliches Verstehen" (siehe CLAUDE.md Nordstern); ADR-015.

**Dateien:** PiperTtsEngine.kt, WhisperSttEngine.kt, LauncherActivity.kt, build.gradle.kts, download-models.sh, .gitignore, CLAUDE.md, DECISIONS.md

**Offen:** Stimme ggf. gegen kerstin/eva_k tauschen (Nutzer-Feedback). STT-Latenz (~2s) dem Nutzer akustisch überbrücken. Vosk-Assets könnten raus, wenn Whisper sich bewährt (−45MB APK).

---

## [2026-07-02] Gerätetest Runde 2: Broadcast-Fix, Hintergrund-Crash, Fuzzy-Matcher

**Was:** (1) Weckwort-Broadcast kam nie an: `sendBroadcast` ohne `setPackage` wird ab Android 14 nicht an RECEIVER_NOT_EXPORTED zugestellt – gefixt. (2) Crash-Schleife: Mikrofon-FGS darf aus dem Hintergrund nicht starten (z.B. nach Anruf-Intent, wenn die Dialer-UI übernimmt) – `startForeground` jetzt mit try/catch + `stopSelf`, LauncherActivity startet den Service bei `onResume` neu. (3) FuzzyContactMatcher: Phonetik-Stufe vergleicht jetzt per Levenshtein auf Kölner-Codes je Namensteil, kombiniert mit Buchstaben-Ähnlichkeit (0.6/0.4) – STT-Verhörer wie "aroma"→Arundhati Brandt werden aufgelöst, Code-Kollisionen zwischen verschiedenen Namen nicht mehr falsch gematcht. (4) Diagnose-Logging: OWW-Scores/Pegel, Vosk-Ergebnisse, Intent-Verarbeitung. (5) Wake-Neustart nach Antwort um 2s verzögert (Selbst-Trigger durch eigene TTS). (6) Schwellwert 0.5→0.3, Patience 3→2 (vorläufig, auf Gerät kalibrieren).

**Warum:** Zweiter Testlauf auf dem Lenovo – Weckwort wurde erkannt, aber nichts reagierte; danach Crash-Schleife nach Anrufversuch.

**Dateien:** WakeWordService.kt, LauncherActivity.kt, VoskSttEngine.kt, OpenWakeWordEngine.kt, FuzzyContactMatcher.kt

**Offen:** Wake-Schwellwert mit echter Stimme kalibrieren. Vosk-Qualität bei natürlicher Sprache prüfen. Echte Nummer im Testkontakt Boris Hartmann ggf. durch Beispielnummer ersetzen.

---

## [2026-07-02] Sprach-Pipeline verdrahtet + OpenWakeWord-Fixes (erster Gerätetest)

**Was:** Die Sprach-Pipeline war nie verdrahtet – LauncherActivity startete nur die Debug-UI, `WakeWordService`/`LinaOrchestrator` wurden nirgends aufgerufen. Jetzt: Nach Mikrofon-Berechtigung lädt Vosk, `WakeWordService` startet, bei Weckwort-Erkennung stoppt der Service (Mikrofon-Freigabe), Lina fragt "Ja?", Vosk nimmt den Befehl auf und gibt ihn in die bestehende Intent-Verarbeitung; danach startet die Weckwort-Erkennung wieder (inkl. 10s-Timeout). Drei Bugs gefixt: (1) `WakeWordService.start()` prüft jetzt RECORD_AUDIO – FGS-Typ "microphone" crashte sonst vor Berechtigungserteilung (Android 14+). (2) `OpenWakeWordEngine`: Melspectrogram-Ausgabeform ist `[1,1,frames,32]`, nicht `[1,frames,32]` (ArrayIndexOutOfBounds), plus fehlende OpenWakeWord-Normalisierung (x/10+2). (3) Classifier-Eingabe braucht Form `[1,16,96]` statt flach. Listen-Loop crasht bei Engine-Fehlern nicht mehr die App.

**Warum:** Erster Test auf dem realen Zielgerät – App zeigte nur Debug-Modus, reagierte nicht auf das Weckwort.

**Dateien:** LauncherActivity.kt, WakeWordService.kt, OpenWakeWordEngine.kt

**Offen:** Weckwort ist weiterhin Platzhalter "Hey Jarvis". Erkennungsqualität/Threshold auf dem Gerät validieren. `LinaOrchestrator` ist jetzt redundant (Logik liegt in LauncherActivity) – konsolidieren.

---

## [2026-07-02] Zielgerät-Wechsel: Lenovo Idea Tab (TB336ZU)

**Was:** Neues Zielgerät Lenovo Idea Tab TB336ZU (ersetzt Samsung Galaxy Tab A9+). `com.google.android.dialer` als Dialer-Paket im `LinaAccessibilityService` ergänzt, da Lenovo Stock-Android mit Google-Telefon-App nutzt. Alle Doku auf das neue Gerät aktualisiert (CLAUDE.md, README, NUTZERPROFIL, ONBOARDING, TODO). ADR-014 angelegt.

**Warum:** Das Lenovo-Tablet liegt physisch vor und wird das Gerät des Primärnutzers.

**Dateien:** LinaAccessibilityService.kt, CLAUDE.md, README.md, NUTZERPROFIL.md, ONBOARDING.md, TODO.md, DECISIONS.md

**Offen:** Erstinstallation + Gerätetest stehen aus (USB-Debugging wird gerade aktiviert). Lenovo-Battery-Optimierung im Dauerbetrieb prüfen.

---

## [2026-06-20] Migration: Porcupine → OpenWakeWord

**Was:** Wake Word Detection komplett von Picovoice Porcupine auf OpenWakeWord (Open Source, Apache 2.0) migriert. Neues `WakeWordEngine` Interface für Austauschbarkeit. `OpenWakeWordEngine` implementiert mit ONNX Runtime: 3-Stufen-Pipeline (Melspectrogram → Embedding → Classifier) mit Patience-basierter Erkennung. `WakeWordService` auf neue Engine umgestellt. Porcupine-Dependency und BuildConfig-Key entfernt. Download-Script für ONNX-Modelle erstellt. Git + GitHub-Repo aufgesetzt.

**Warum:** Picovoice erfordert seit 2026 manuellen Approval-Prozess für API-Keys – nicht akzeptabel für ein Open-Source-Projekt. OpenWakeWord ist komplett frei, kein Key nötig, und die Modelle sind mit 3.5MB deutlich kleiner.

**Dateien:**
- `app/src/main/kotlin/dev/lina/core/wakeword/WakeWordEngine.kt` – Neues Interface
- `app/src/main/kotlin/dev/lina/core/wakeword/OpenWakeWordEngine.kt` – ONNX-basierte Implementierung
- `app/src/main/kotlin/dev/lina/core/wakeword/WakeWordService.kt` – Porcupine → OpenWakeWordEngine
- `app/build.gradle.kts` – `porcupine-android` → `onnxruntime-android`
- `scripts/download-models.sh` – Lädt ONNX-Modelle herunter
- `.gitignore` – `openwakeword/` Assets ausschließen

**Offen:** Aktuell "hey_jarvis" als Platzhalter-Weckwort. Custom-Modell "Hey Lina" muss noch trainiert werden (synthetische TTS-Daten).

---

## [2026-06-20] Git + GitHub Repo initialisiert

**Was:** Git initialisiert, initialer Commit mit allen 55 Dateien (4182 Zeilen). GitHub-Repo `KostakisMT/lina-assistant` (privat) angelegt und gepusht.

**Warum:** Versionskontrolle und Remote-Backup vor Tablet-Auslieferung an Testnutzer.

**Dateien:** Alle Projektdateien.

**Offen:** SSH-Key nicht konfiguriert, Push läuft über HTTPS.

---

## [2026-05-30] Phase 1g – Feature: Hörbücher (MVP-Hook)

**Was:** Komplettes Hörbuch-Feature als zentrales Alleinstellungsmerkmal implementiert. 6 Komponenten:
- `AudiobookPlayer` – ExoPlayer (Media3) Wrapper für Wiedergabe, Seek, Volume-Kontrolle
- `PlaybackStateStore` – Persistenter Fortschritt (SharedPreferences), überlebt App-Neustart
- `AudiobookLibrary` – Kuratierte Startliste (Djamilah, Tolstoi, Gorki, Brecht) + lokale Datei-Erkennung + Librivox-Integration
- `LibrivoxRepository` – Librivox-API Suche nach Titel/Autor, RSS-Chapter-Parsing, Streaming-URLs
- `SleepTimer` – Countdown mit 30s Lautstärke-Fade-Out
- `AudiobookManager` – Orchestriert alles: Sprachbefehle → Player + Library + Timer + TTS

Neue Intents: `ListAudiobooks`, `SearchAudiobook(query)`, `SleepTimer(minutes)` mit entsprechenden Regex-Patterns im LocalCommandResolver.

**Warum:** Kein anderer Sprachassistent bietet blinden Nutzern diese Kombination: komplett sprachgesteuerte Hörbuch-Wiedergabe mit kuratiertem Onboarding, Librivox-Suche, persistentem Fortschritt und Schlaf-Timer. Das ist der MVP-Hook.

**Dateien:**
- `app/src/main/kotlin/dev/lina/feature/audiobook/AudiobookPlayer.kt`
- `app/src/main/kotlin/dev/lina/feature/audiobook/PlaybackStateStore.kt`
- `app/src/main/kotlin/dev/lina/feature/audiobook/AudiobookLibrary.kt`
- `app/src/main/kotlin/dev/lina/feature/audiobook/LibrivoxRepository.kt`
- `app/src/main/kotlin/dev/lina/feature/audiobook/SleepTimer.kt`
- `app/src/main/kotlin/dev/lina/feature/audiobook/AudiobookManager.kt`
- `app/src/main/kotlin/dev/lina/core/intent/ResolvedIntent.kt` – 3 neue Intents
- `app/src/main/kotlin/dev/lina/core/intent/LocalCommandResolver.kt` – Schlaf-Timer + Suche Patterns
- `app/src/main/kotlin/dev/lina/ui/launcher/LauncherActivity.kt` – AudiobookManager integriert
- `app/build.gradle.kts` – Media3 ExoPlayer Dependency

**Sprachbefehle:** "Spiel Hörbuch ab", "Pause", "Weiter", "30 Sekunden zurück", "Was höre ich?", "Welche Hörbücher habe ich?", "Suche Tolstoi", "Stopp in 30 Minuten"

---

## [2026-05-30] Phase 1e – Feature: SMS

**Was:** SMS-Feature komplett implementiert. `SmsReader` liest Posteingang via ContentResolver (neueste zuerst), löst Absender-Nummern über `PhoneLookup` zu Kontaktnamen auf und liest per TTS vor. `SmsSender` sendet SMS via `SmsManager` mit FuzzyContactMatcher-Auflösung, unterstützt Multipart für lange Texte und Antworten auf die letzte empfangene SMS. LauncherActivity aktualisiert: alle SMS-Dummy-Responses durch echte Feature-Aufrufe ersetzt.

**Warum:** SMS ist Priorität 2 im MVP. Nutzer möchte Nachrichten per Sprache lesen, senden und beantworten.

**Dateien:**
- `app/src/main/kotlin/dev/lina/feature/sms/SmsReader.kt` – Posteingang lesen + TTS vorlesen + Kontaktname-Auflösung
- `app/src/main/kotlin/dev/lina/feature/sms/SmsSender.kt` – Senden + Antworten + SmsResult sealed class
- `app/src/main/kotlin/dev/lina/ui/launcher/LauncherActivity.kt` – SmsReader + SmsSender integriert

**Sprachbefehle:** "Schreib Boris: Bin gleich da", "Lies meine Nachrichten", "Antwort: Danke" – alle funktional.

---

## [2026-05-30] Phase 1d – Feature: Anrufe

**Was:** `CallHandler` implementiert mit TelecomManager-Integration. Ausgehende Anrufe via `Intent.ACTION_CALL` mit Kontakt-Auflösung über FuzzyContactMatcher. Eingehenden Anruf annehmen via `TelecomManager.acceptRingingCall()`, ablehnen/auflegen via `TelecomManager.endCall()`. TTS-Feedback für alle Anruf-States. LauncherActivity aktualisiert: Dummy-Responses durch echte CallHandler-Aufrufe ersetzt. Berechtigungen `ANSWER_PHONE_CALLS` und `READ_PHONE_STATE` im Manifest ergänzt.

**Warum:** Anrufe haben höchste Priorität im MVP. Nutzer muss per Sprache Kontakte anrufen und eingehende Anrufe steuern können.

**Dateien:**
- `app/src/main/kotlin/dev/lina/feature/calls/CallHandler.kt` – Anruf-Steuerung + CallResult sealed class
- `app/src/main/kotlin/dev/lina/ui/launcher/LauncherActivity.kt` – CallHandler integriert
- `app/src/main/AndroidManifest.xml` – ANSWER_PHONE_CALLS + READ_PHONE_STATE

**Sprachbefehle:** "Ruf Boris an", "Annehmen", "Ablehnen", "Auflegen" – alle funktional.

---

## [2026-05-30] Phase 1f – Feature: Nachrichten

**Was:** RSS-Nachrichten-Feature komplett implementiert. `RssFeedRepository` mit nativen `XmlPullParser` (keine externe Dependency). Vier vorkonfigurierte Feeds (Junge Welt, unsere zeit, Spektrum, Yacht). `NewsCache` als JSON-Datei in `filesDir`. `NewsSyncWorker` via WorkManager (stündlicher Sync). `NewsReader` mit Zusammenfassung-Modus (Titel + erster Satz) und Detail-Modus (vollständige Beschreibung auf Nachfrage). TTS-Feedback für alle Nachrichten-States. LauncherActivity aktualisiert.

**Warum:** Nachrichten sind Priorität 3 im MVP. Nutzer möchte Nachrichten auf Abruf per Sprache vorgelesen bekommen.

**Dateien:**
- `app/src/main/kotlin/dev/lina/feature/news/RssFeedRepository.kt` – RSS-Parser + 4 Feeds
- `app/src/main/kotlin/dev/lina/feature/news/NewsCache.kt` – JSON-basierter lokaler Cache
- `app/src/main/kotlin/dev/lina/feature/news/NewsSyncWorker.kt` – WorkManager stündlicher Sync
- `app/src/main/kotlin/dev/lina/feature/news/NewsReader.kt` – TTS-Steuerung mit Zusammenfassung/Detail
- `app/src/main/kotlin/dev/lina/ui/launcher/LauncherActivity.kt` – NewsReader + NewsSyncWorker integriert

**Sprachbefehle:** "Was gibt es Neues?", "Mehr dazu", "Nächste Meldung", "Stopp" – alle funktional.

---

## [2026-05-30] Phase 1b+1c – AccessibilityService & Kontakte

**Was:** LinaAccessibilityService mit Notification-Erkennung für eingehende Anrufe/SMS implementiert. ContactRepository für ContactsContract-Zugriff. FuzzyContactMatcher mit Kölner Phonetik, Levenshtein-Distanz und konfigurierbarem Spitznamen-Mapping. AccessibilityGuide für Onboarding. LauncherActivity mit Kontakt-Auflösung und Accessibility-Event-Empfang.

**Warum:** Kontakte müssen per Vorname/Spitzname gefunden werden ("boris" → Boris Hartmann, "mama" → Gudrun Sommer). AccessibilityService ist Kernkomponente für Anruf/SMS-Erkennung.

**Dateien:**
- `app/src/main/kotlin/dev/lina/core/accessibility/LinaAccessibilityService.kt` – Notification-basierte Anruf/SMS-Erkennung
- `app/src/main/kotlin/dev/lina/core/contacts/ContactRepository.kt` – ContactsContract Wrapper
- `app/src/main/kotlin/dev/lina/core/contacts/FuzzyContactMatcher.kt` – Phonetik + Levenshtein + Spitznamen
- `app/src/main/kotlin/dev/lina/feature/onboarding/AccessibilityGuide.kt` – Service-Status + Settings-Intent
- `app/src/main/kotlin/dev/lina/ui/launcher/LauncherActivity.kt` – Kontakt-Matching integriert

**Getestet:** boris→Boris Hartmann, mama→Gudrun Sommer, ulla→Ulla Winter, annika→Annika Berger. Alles korrekt aufgelöst.

---

## [2026-05-29] Emulator-Testumgebung + Debug-Modus

**Was:** Android Emulator als Galaxy Tab A9+ Profil (1200x1920, API 33, ARM64 nativ auf M2 Max) eingerichtet. Mikrofon-Passthrough funktioniert auf Apple Silicon Emulator v36.5.11 nicht (bekannter Bug: `microphoneEnabledChanged` Signal fehlt im Build). Workaround: Debug-Modus mit Texteingabe und ADB-Broadcast-Receiver für die gesamte Intent-Pipeline. Test-Skripte erstellt.

**Warum:** Visuelles Feedback beim Entwickeln nötig. Echte Mikrofon-Tests erst auf physischem Tablet möglich.

**Dateien:**
- `scripts/run-emulator.sh` – Emulator starten
- `scripts/deploy.sh` – Build + Install + App starten
- `scripts/say.sh` – Sprachbefehle per ADB simulieren (`./scripts/say.sh "ruf boris an"`)
- `app/src/main/kotlin/dev/lina/ui/launcher/LauncherActivity.kt` – Debug-Modus mit Textfeld, ADB-Broadcast-Receiver, Intent-Log

**Offen:** Mikrofon-Passthrough auf Emulator nicht möglich. STT (Vosk) kann nur auf physischem Gerät getestet werden.

---

## [2026-05-29] Phase 1a – Kern-Infrastruktur

**Was:** Komplette Sprachpipeline implementiert: VoskSttEngine (offline Deutsch), WakeWordService (Porcupine ForegroundService), WakeWordWatchdog (Samsung-Killschutz), BootReceiver (Autostart), LinaOrchestrator (Wake Word → STT → Intent → TTS), LocalCommandResolver (Regex-basiert, alle MVP-Befehle), LlmIntentResolver (Stub), PermissionsGuide, BatteryWhitelistGuide. LauncherActivity mit Onboarding-Flow. Vosk-Modell vosk-model-small-de heruntergeladen.

**Warum:** Grundvoraussetzung für alle Features. Ohne STT/TTS/WakeWord kein sprachgesteuerter Assistent.

**Dateien:**
- `app/src/main/kotlin/dev/lina/core/stt/VoskSttEngine.kt` – Vosk Wrapper mit Asset-Kopie
- `app/src/main/kotlin/dev/lina/core/wakeword/WakeWordService.kt` – ForegroundService + PARTIAL_WAKE_LOCK
- `app/src/main/kotlin/dev/lina/core/wakeword/WakeWordWatchdog.kt` – 30s Service-Prüfung
- `app/src/main/kotlin/dev/lina/core/wakeword/BootReceiver.kt` – BOOT_COMPLETED Autostart
- `app/src/main/kotlin/dev/lina/core/LinaOrchestrator.kt` – Verbindet Wake Word → STT → Intent → TTS
- `app/src/main/kotlin/dev/lina/core/intent/LocalCommandResolver.kt` – Regex Ebene 1
- `app/src/main/kotlin/dev/lina/core/intent/LlmIntentResolver.kt` – Phase 2 Stub
- `app/src/main/kotlin/dev/lina/feature/onboarding/PermissionsGuide.kt`
- `app/src/main/kotlin/dev/lina/feature/onboarding/BatteryWhitelistGuide.kt`
- `app/src/main/kotlin/dev/lina/ui/launcher/LauncherActivity.kt` – Erweitert mit Onboarding
- `app/build.gradle.kts` – Porcupine, Vosk, WorkManager Dependencies
- `app/src/main/AndroidManifest.xml` – WakeWordService + BootReceiver registriert

**Offen:** Phase 1b (AccessibilityService Logik), Phase 1c (ContactRepository, FuzzyContactMatcher), Phase 1d–g (Features).

---

## [2026-05-29] Phase 0 – Android-Projekt aufgesetzt

**Was:** Vollständiges Android-Projekt mit Kotlin, Jetpack Compose, Gradle KTS angelegt. Alle Interfaces (SttEngine, TtsEngine, IntentResolver), ResolvedIntent sealed class, AndroidTtsEngine, LinaTheme, LauncherActivity, LinaAccessibilityService und AndroidManifest mit allen Berechtigungen erstellt. Build erfolgreich.

**Warum:** Projektgrundgerüst für Phase 1 nötig. Alle Kernkomponenten hinter Interfaces gemäß ADR-001/002/005.

**Dateien:**
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` – Gradle-Konfiguration
- `app/build.gradle.kts` – App-Modul (compileSdk 35, minSdk 33, Compose BOM)
- `app/src/main/AndroidManifest.xml` – Alle Berechtigungen + Launcher + AccessibilityService
- `app/src/main/res/xml/accessibility_service_config.xml`
- `app/src/main/kotlin/dev/lina/core/tts/TtsEngine.kt` – Interface + TtsPriority
- `app/src/main/kotlin/dev/lina/core/tts/AndroidTtsEngine.kt` – MVP-Implementierung
- `app/src/main/kotlin/dev/lina/core/stt/SttEngine.kt` – Interface
- `app/src/main/kotlin/dev/lina/core/intent/IntentResolver.kt` – Interface
- `app/src/main/kotlin/dev/lina/core/intent/ResolvedIntent.kt` – Sealed class mit allen MVP-Intents
- `app/src/main/kotlin/dev/lina/core/accessibility/LinaAccessibilityService.kt` – Stub
- `app/src/main/kotlin/dev/lina/ui/components/LinaTheme.kt` – Hochkontrast (#000/#FFF/#FFD700)
- `app/src/main/kotlin/dev/lina/ui/launcher/LauncherActivity.kt` – Home-App
- `app/proguard-rules.pro`, `.gitignore`, `local.properties`

**Offen:** Vosk-Modell noch nicht heruntergeladen. GitHub Repo noch nicht erstellt. Git noch nicht initialisiert.

---

## [2026-05-29] Projektdokumentation initialisiert

**Was:** Vollständige Projektdokumentation erstellt: CLAUDE.md, TODO.md, DECISIONS.md, CHANGELOG.md, ONBOARDING.md, NUTZERPROFIL.md

**Warum:** Grundlage für kollaboratives Vibe-Coding mit mehreren Claude Code Instanzen. Alle Entscheidungen, Nutzerprofil und Architektur dokumentiert bevor Code geschrieben wird.

**Dateien:**
- `CLAUDE.md` (Root) – Hauptkontext für Claude Code
- `docs/TODO.md` – Taskboard
- `docs/DECISIONS.md` – 10 ADRs
- `docs/CHANGELOG.md` – Diese Datei
- `docs/ONBOARDING.md` – Für neue Devs
- `docs/NUTZERPROFIL.md` – Detailliertes Nutzerprofil

**Offen:** Android-Projekt noch nicht angelegt. GitHub Repo noch nicht erstellt.
