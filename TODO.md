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

## Prioritäten

Jede Sektion trägt jetzt eine Priorität. Bezieht sich auf die noch **offenen**
Punkte darin, nicht auf das Feature als Ganzes – eine Sektion mit 90% `[x]`
und einem offenen Polish-Punkt ist trotzdem "erledigt", nicht "P1".

| Kürzel | Bedeutung | Wann |
|---|---|---|
| **P0** | Kritisch/blockierend | Blockiert reale Nutzung oder andere Arbeit – zuerst |
| **P1** | Hoch | Nächster sinnvoller Schritt Richtung Auslieferung |
| **P2** | Mittel | Wichtig, aber ohne Termindruck |
| **P3** | Niedrig | Politur, Nice-to-have, kein Nutzerschmerz ohne es |
| **P4** | Backlog | Phase 2+, bewusst zurückgestellt |
| **✅** | Erledigt | Nichts Offenes mehr (oder nur unverbindliche Politur) |

**Aktuell P0/P1 auf einen Blick** (Details in den jeweiligen Sektionen):
- **P0:** – (nichts blockiert aktuell die Weiterarbeit)
- **P1:** Anrufe/SMS am echten Gerät testen (aktuelles Testtablet hat **keine
  SIM** – `gsm.sim.state=ABSENT`, Test braucht ein SIM-fähiges Gerät oder eine
  eingelegte Karte); Release-Keystore + signiertes `assembleRelease`;
  Dauerbetrieb über Stunden/eine Nacht verifizieren

---

## 🔴 Phase 0 – Projektsetup — ✅ Erledigt

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

## 🔴 Phase 1a – Kern-Infrastruktur — ✅ Erledigt

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

## 🔴 Phase 1b – AccessibilityService — ✅ Erledigt

- [x] `LinaAccessibilityService` anlegen
- [x] `accessibility_service_config.xml` konfigurieren
- [x] Eingehende Anrufe erkennen → TTS: "Anruf von [Name]"
- [x] Eingehende SMS erkennen → TTS: "Neue Nachricht von [Name]"
- [x] Onboarding: Nutzer zu Accessibility-Einstellungen führen

---

## 🔴 Phase 1c – Kontakte & Intent — ✅ Erledigt

- [x] `ContactRepository` – Kontakte aus ContactsContract laden
- [x] `FuzzyContactMatcher` – phonetisches Matching (Arundhati, Eßfeld etc.)
- [x] Spitznamen-Mapping vorbereiten (konfigurierbar)
- [x] `IntentResolver` Interface + `ResolvedIntent` Datenklassen
- [x] `LocalCommandResolver` – Regex/Keywords Ebene 1
- [x] Intent-Definitionen für alle MVP-Befehle
- [x] `LlmIntentResolver` – Stub (Phase 2 Vorbereitung)

---

## ✅ Phase 1d – Feature: Anrufe — ✅ Erledigt (Code); Gerätetest siehe P1 unten

- [x] Anruf starten via Intent.ACTION_CALL + Kontakt-Auflösung
- [x] Eingehenden Anruf annehmen/ablehnen (via TelecomManager)
- [x] Aktiven Anruf beenden
- [x] TTS-Feedback für alle Anruf-States
- [x] Sprachbefehle: "Ruf Boris an", "Annehmen", "Ablehnen", "Auflegen"

---

## ✅ Phase 1e – Feature: SMS — ✅ Erledigt (Code); Gerätetest siehe P1 unten

- [x] SMS-Posteingang lesen (ContentResolver, neueste zuerst)
- [x] SMS vorlesen via TTS (Absender per PhoneLookup aufgelöst)
- [x] SMS senden via SmsManager (inkl. Multipart für lange Texte)
- [x] Antworten auf letzte SMS
- [x] TTS-Bestätigung nach Versand
- [x] Sprachbefehle: "Schreib Boris: [Text]", "Lies Nachrichten", "Antwort: [Text]"

---

## ✅ Phase 1f – Feature: Nachrichten — ✅ Erledigt (durch Claude+Websuche abgelöst, s. CHANGELOG 2026-07-25)

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

## ✅ Phase 1g – Feature: Hörbücher (MVP-Hook) — P2 Mittel (Restpunkte)

- [x] ExoPlayer Integration (Media3)
- [x] Lokale MP3/M4B aus Storage laden (Audiobooks-Ordner)
- [x] Sanftes Onboarding (kuratierte Startliste: Djamilah, Tolstoi, Gorki, Brecht)
- [x] Fortschritt persistent speichern (SharedPreferences)
- [x] Librivox-Integration (Suche + Streaming, kein Login)
- [x] Schlaf-Timer mit Lautstärke-Fade-Out (30s)
- [x] Sprachbefehle: "Spiel Hörbuch ab", "Pause", "Weiter", "30 Sekunden zurück", "Was höre ich?", "Welche Hörbücher habe ich?", "Suche Brecht", "Stopp in 30 Minuten"
- [x] Lautstärke: "lauter"/"leiser" (10%-Schritte, `AudiobookPlayer.adjustVolume()`), am Gerät getestet – 2026-07-26
- [x] Lautstärke ohne laufendes Hörbuch: "lauter"/"leiser" steuert die Systemlautstärke (`STREAM_MUSIC`), am Gerät sauber verifiziert (entstummt + eine Stufe lauter) – 2026-07-26
- [x] Kapitel-Infrastruktur: Playlist statt Einzeldatei, Kapitelansage, Fortschritt je Kapitel (2026-07-20)
- [x] **Bugfix:** LibriVox endete nach dem ersten Abschnitt (nur `chapters.first()` wurde gespielt) – 2026-07-20
- [x] DAISY 2.02 lesen: `ncc.html` + SMIL + Zeitbereiche (2026-07-20, ADR-019)
- [x] Kapitel-Sprachbefehle: "nächstes Kapitel", "ein Kapitel zurück", "Kapitel drei", "welche Kapitel gibt es" (2026-07-20)
- [ ] Echtes DAISY-Buch der Hörbücherei am Tablet durchspielen (Struktur variiert je Produktionsstelle)
- [ ] LibriVox-Mehrkapitel-Wiedergabe am Tablet gegenprüfen (Streaming über Kapitelgrenze)
- [ ] Onboarding: Wie kommen DAISY-Bücher aufs Tablet? (CD-Import / Ausleihe der Hörbücherei)

---

## ✅ Qualitätssicherung – Tests — P3 Niedrig (Restpunkt)

- [x] Test-Sourceset + JUnit im Build, CI läuft `testDebugUnitTest` (2026-07-20)
- [x] `GermanTimeParserTest` – Zeitangaben, Wiederholungen, Sachtext (2026-07-20)
- [x] `LocalCommandResolverTest` – Intents und ihre Abgrenzungen untereinander (2026-07-20)
- [x] `DaisyParserTest` – ncc.html, SMIL, Clock-Values, Sanitizing (2026-07-20)
- [x] `FuzzyContactMatcher` testen (Arundhati, Eßfeld – spracherkennungs-kritisch); dafür `ContactSource`-Interface eingezogen – 2026-07-21
- [x] `GermanSpellingTest` – Buchstabiertafel für den Pairing-Code (2026-07-21)
- [x] **Bugfix:** `PiperTtsEngine` synthetisierte lange Texte (Dokument-Vorlesen, ausführliche Antworten) in einem einzigen `generate()`-Aufruf – blockierte minutenlang und fror die komplette Sprachschleife ein (kein Weckwort-Neustart, kein Fehler). Fix: Chunking an Satzgrenzen (`splitIntoChunks`, ≤240 Zeichen) + `stopRequested`-Flag für sofortigen Abbruch – 2026-07-25
- [x] Entscheidung `RssFeedRepository`: **behalten** als Offline-Fallback (2026-07-25) – totes Gewicht im Normalbetrieb (Nachrichten laufen über Claude, CHANGELOG 2026-07-25), aber für den Fall ohne Internet/API-Key aufgehoben
- [ ] `RssFeedRepository` Testaufbau: `XmlPullParser` → `DocumentBuilder`-Umbau wie bei `DaisyParser` (ADR-019), damit JVM-Tests möglich werden – weiterhin offen, kein Code seit der Behalten-Entscheidung geändert
- [x] **Bugfix:** Mehrdeutigkeit "weiter" (Hörbuch vs. freie Konversation) – `.*weiter.*` traf ohne Wortgrenzen auch "lass uns weiterreden" und startete versehentlich das Hörbuch statt an Claude zu gehen. Fix: `\b`-Wortgrenzen in `LocalCommandResolver.resolveAudiobook`, Test ergänzt, am Gerät verifiziert – 2026-07-25
- [x] Sicherheits-Timeout für die Poll-Schleifen: gemeinsamer Helfer `waitForSilenceThenRun()` in `LauncherActivity.kt`, bricht nach 45s ab statt endlos auf `isBusySpeaking()==false` zu warten; `openFollowUpWindow`/`openDocFollowUp` darauf umgestellt, am Gerät regressionsgetestet – 2026-07-25

---

## 🔴 Auslieferung Testnutzer — P1 Hoch

> Ursprüngliche Deadline (KW 26, 2026-06-27) verstrichen, ohne dass alle Punkte
> abgeschlossen sind – bewusst nicht nachträglich verschoben, sondern hier
> offen benannt: Datum war zu optimistisch, Priorität bleibt hoch. Kein neues
> Zieldatum gesetzt, bis Anrufe/SMS/Dauerbetrieb real getestet sind.

### Build & Release — P1 Hoch
- [x] `assembleDebug` – sauberer Build verifiziert (2026-06-20)
- [x] Porcupine → OpenWakeWord migriert (2026-06-20, kein API-Key mehr nötig)
- [ ] Release-Keystore anlegen (`.jks`) – **einziger Ort dafür**, siehe auch Etappe 1 unten (dort war das dupliziert)
- [ ] `assembleRelease` – signiertes Release-APK erzeugen

### Test auf echtem Tablet (Lenovo Idea Tab TB336ZU)
- [x] APK installieren (adb, Build vom aktuellen Codestand) – 2026-07-25
- [x] Onboarding durchlaufen (Interessen/Region/Name gesetzt, aus SharedPreferences bestätigt) – 2026-07-25
- [x] Wake Word getestet – "Hey Lina" (Custom-Modell v2 mit Nutzeraufnahmen): 5/5 erkannt (2026-07-04)
- [x] STT (Whisper, nicht Vosk): "wie spät ist es", "lies meine Post" korrekt erkannt (2026-07-25)
- [!] Anrufe: ausgehend + eingehend annehmen/ablehnen – **blockiert:** aktuelles Testtablet hat keine SIM (`gsm.sim.state=ABSENT`, 2026-07-26 verifiziert), braucht ein SIM-fähiges Testgerät. Intent-Pfad selbst am 2026-08-22 per Debug-Broadcast verifiziert: "Ruf Boris an" → Fuzzy-Match → `CallHandler.dialContact()` → echter `ACTION_CALL`, Dialer öffnet InCallActivity korrekt, scheitert nur an "Mobilfunknetz nicht verfügbar" (kein SIM)
- [!] SMS: senden + lesen – gleiche Blockade (kein SIM/keine Mobilfunkverbindung im Testtablet). `SendSms`/`ReadSms`-Intents am 2026-08-22 ebenfalls per Debug-Broadcast bis zum `SmsManager`-Aufruf verifiziert
- [x] ~~Nachrichten: RSS-Sync + Vorlesen~~ – Feature auf Claude+Websuche umgestellt (siehe CHANGELOG 2026-07-25), am Gerät getestet und für gut befunden
- [x] Hörbücher: LibriVox-Suche + Wiedergabe + Pause/Weiter/Zurückspulen/Kapitel getestet (2026-07-25); Schlaf-Timer (Hörbuch-Fade-Out, nicht zu verwechseln mit dem neuen Schlafmodus) am Gerät getestet (2026-07-26)
- [x] **Bugfix:** Weckwort-Erkennung ignorierte Hörbuch-Wiedergabe nicht (nur Linas eigene Stimme) – Erzählstimme konnte Weckwort auslösen und Buchtext an Claude schicken. Duck/Resume in `AudiobookManager`/`LauncherActivity` behebt die Folgen; akustische Ursache (echtes AEC) bleibt offen – 2026-07-25
- [x] **Bugfix:** `Music/Audiobooks`-Ordner war ohne `READ_MEDIA_AUDIO` nicht lesbar (Scoped Storage) – Berechtigung ergänzt (Manifest + PermissionsGuide) – 2026-07-25
- [x] Lokale Mehrkapitel-Bücher: `AudiobookLibrary.localFolderBooks()`, vier deutsche LibriVox-Hörbücher installiert (Tolstoi, Keller, Eichendorff, Verne) – 2026-07-25
- [ ] Dauerbetrieb: Service stabil nach 1h, 4h, über Nacht?

### Tablet vorbereiten für Nutzer — P2 Mittel
- [ ] Lina als Standard-Home-App setzen
- [ ] Android Schriftgröße auf Maximum
- [ ] TalkBack-Konflikte evaluieren (parallel zu Lina?)
- [ ] WLAN konfigurieren
- [x] Kontakte eintragen (Arundhati, Boris, Ulla, Annika, Sabine, Dirk, Hannah, Gudrun) – per adb mit Beispielnummern (2026-07-02)
- [ ] Testanruf mit echtem Kontakt
- [ ] Kurzanleitung erstellen (große Schrift oder Audioformat)

### Risiken & Showstopper — P1 Hoch
- [x] **Gefunden UND behoben (2026-07-26):** `WakeWordService` (Mikrofon-FGS) durfte laut Android 14+/15 nicht aus dem Hintergrund neu gestartet werden (`SecurityException`), passierte bei jedem Konversationsturn. Fix: Service läuft jetzt durchgehend; statt komplettem Stop+Neustart wird nur die Engine intern pausiert/fortgesetzt (`WakeWordService.pauseListening()`/`resumeListening()`, normaler `startService()` an einen bereits laufenden Service – kein neuer FGS-Start, daher nicht von der Android-Regel betroffen). Am Gerät verifiziert: exakt das Szenario, das vorher abstürzte (Antwort → Folgefenster → Timeout → Rückkehr zum Weckwort), läuft jetzt ohne `SecurityException` durch. Restrisiko: echter Prozess-Tod (OOM-Kill) im Hintergrund bräuchte weiterhin einen echten Neustart, der theoretisch noch scheitern könnte – seltener Fall, nicht der ursprüngliche Auslöser.
- [ ] Lenovo/ZUI Battery-Optimierung – killt es den Service zusätzlich zum obigen Android-eigenen Problem?
- [ ] Vosk-Erkennungsqualität bei Umgebungsgeräuschen
- [ ] TTS-Lautstärke über Tablet-Lautsprecher ausreichend?
- [ ] RSS-Feeds erreichbar? (Junge Welt Paywall?)
- [ ] JAVA_HOME muss gesetzt sein für Builds (`openjdk@17` via Homebrew)
- [x] **Sicherheitslücke gefunden UND behoben (2026-08-22):** `LauncherActivity` registrierte einen `BroadcastReceiver` für Action `dev.lina.DEBUG_INPUT` (`RECEIVER_EXPORTED`, kein `BuildConfig.DEBUG`-Gate, keine Permission). Jede App auf dem Gerät (oder `adb shell`) konnte darüber beliebigen Text direkt in `processDebugInput()` einspeisen – denselben Pfad wie echte STT-Ergebnisse, inkl. `CallHandler.dialContact()` (`ACTION_CALL`, kein SIM-State-Check) und `SmsSender` (`SmsManager.sendTextMessage`, kein Guard). Reproduziert mit `adb shell am broadcast -a dev.lina.DEBUG_INPUT -p dev.lina --es text '...'`. **Fix:** Registrierung in `onCreate()` hinter `if (BuildConfig.DEBUG)` gezogen (`LauncherActivity.kt:291`). Verifiziert: generierte `BuildConfig.java` für `standardRelease` hat `DEBUG = false` → Receiver registriert sich in Release-Builds gar nicht mehr; im Debug-Build (Testtablet) funktioniert der Kanal unverändert für Entwicklungszwecke.

---

## 🟠 Verteilung: Zugang, Kosten, Finanzierung (ADR-020 bis ADR-022) — P4 Backlog

> Voraussetzung dafür, dass Lina über den einzelnen Testnutzer hinauskommt.
> Der einkompilierte `CLAUDE_API_KEY` ist für Verteilung ungeeignet. Bewusst
> P4: In der privaten Übergangsphase (ADR-023) mit einem Testnutzer noch nicht
> handlungsrelevant – wird P1, sobald ein zweiter/dritter Nutzer real ansteht.

### Zuerst messen (blockiert alles andere in dieser Sektion)
- [ ] Verbrauch eines realen Alltagstages messen, aufgeschlüsselt nach Tokens **und Websuchen** – ohne diese Zahlen sind Kontingentgrenze und Optimierungsreihenfolge geraten
- [ ] Websuche-Anteil prüfen: wie oft greift `WebSearchTool20260209` wirklich, was kostet sie anteilig (wird pro Suche abgerechnet, nicht über Tokens)

### Kostensenkung (ADR-022)
- [ ] Modell-Routing Haiku 4.5 / Sonnet 5 mit Qualitätsvergleich an echten Turns
- [ ] `LlmIntentResolver` mit On-Device-Modell reaktivieren – jetzt Teil von ADR-032 (siehe Sektion „Lokaler Gemma-3n-Pfad" unten), nicht mehr eigenständig verfolgt
- [ ] `maxUses` der Websuche senken, Auslösung an Aktualitätsbezug binden
- [ ] `MAX_HISTORY` senken, Wirkung auf Gesprächsqualität messen

### Infrastruktur (ADR-020)
> Zurückgestellt seit ADR-023 (2026-07-25): In der privaten Übergangsphase läuft
> die App über den eigenen API-Key; der Proxy ist Zukunftsthema und mit ihm eine
> mögliche gemeinnützige Trägerschaft. Tasks bleiben als Ziel für die Verteilung.
- [x] Proxy-Spezifikation: Endpunkte, Pairing, Verbrauchszählung, Rate-Limits → `PROXY-SPEC.md` (Entwurf) – 2026-07-21
- [ ] Offene Fragen aus PROXY-SPEC.md entscheiden (Streaming, Hosting-Standort, Routing in Proxy oder App, Mehrgeräte-Konten, Vorab-Kopplung)
- [ ] `CredentialStore`-Interface + `EncryptedSharedPreferences` statt `BuildConfig.CLAUDE_API_KEY`
- [ ] Sprachdialog „Einrichtung" um Pairing-Code erweitern (`VoiceOnboarding`, nutzt `GermanSpelling`)
- [x] Buchstabieralphabet in `core/text/` für die phonetische Code-Ansage (`GermanSpelling`) – 2026-07-21

### Sicherheit (siehe SICHERHEIT.md, dort die vollständige Liste) — P2 Mittel
- [x] `EncryptedSharedPreferences` für Erinnerungen: `ReminderStore.kt` auf `androidx.security.crypto` (AES256-GCM/SIV) umgestellt, einmalige Migration alter Klartext-Einträge + Löschung des alten Speichers, am Gerät verifiziert (Klartext nicht mehr lesbar, Erinnerung feuert weiterhin korrekt) – 2026-07-25
- [x] Automatische Löschung für Einrichtungs-Sprachaufnahmen und `testfoto`-Bilder: `cleanupOldDebugFiles()` in `LauncherActivity.kt`, läuft im Hintergrund bei jedem App-Start, löscht `onboarding/`- und `docphotos/`-Einträge älter als 7 Tage. Logik isoliert verifiziert (Python-Äquivalent); Live-Gerätetest an adb/run-as-Rechten im externen App-Ordner gescheitert (Testinfrastruktur, nicht Code) – 2026-07-25/26
- [ ] Kontaktadresse für Sicherheitsmeldungen im Repository hinterlegen

### Rechtlich & Finanzierung (ADR-021) — P4 Backlog
- [x] Konkurrenzanalyse für Positionierung/Förderanträge – lokal, nicht im Repo – 2026-08-13
- [ ] Steuerberater: Zweckbetrieb §68 Nr. 4 AO, Umsatzsteuer bei 1:1-Weitergabe
- [ ] PSP-Auswahl; Zahlungseinrichtung barrierefrei über Vertrauensperson
- [ ] AGB und Widerrufsbelehrung barrierefrei (kein reines PDF)
- [ ] AVV mit Anthropic
- [ ] Anfrage an Anthropic wegen Nonprofit-/Förder-API-Credits (Entwurf liegt vor)

---

## 🟣 Lokaler Gemma-3n-Pfad für NGO-Partner (ADR-032) — P4 Backlog

> Der Nutzer testet/finetuned seit 2026-08-04 aktiv weiter (Tablet ist jetzt
> vor Ort), unabhängig davon, ob eine NGO schon konkret ansteht – die
> ursprüngliche Gate-Bedingung ist damit überholt, Priorität faktisch höher
> als P4. Löst ADR-020/021/022 für diesen Build-Flavor komplett ab, s. ADR-032.

- [x] Mac/MLX-Spike: `mlx-vlm`/`mlx-lm` laden Gemma-3n-E2B fehlerfrei;
  Basismodell gegen 20 handgeschriebene Prompts getestet – 11/20 korrekt,
  bestätigt die beiden Finetuning-Gründe aus ADR-032 empirisch (2026-08-04,
  Details `training/llm/README.md`)
- [x] Synthetischer Trainingsdaten-Generator (`training/llm/gen_dialogue.py`
  + `build_dataset.py`, Bootstrap über Claude API) – erste Testcharge 52
  Rohbeispiele erzeugt und in train/valid/test gesplittet (2026-08-04)
- [x] LoRA-Finetuning-Durchlauf – neun Versuche mit Gemma 3n und Gemma-3-
  270M divergierten alle (Details `training/llm/README.md`); Tiefenrecherche
  ergab Modellwechsel als Ausweg. **Llama-3.2-3B-Instruct** (statt Gemma-
  Familie) mit identischer Pipeline + Early Stopping (Iter 100): sauberer
  Konvergenz, **46/52 (88,5 %) korrekt** gegen den gehaltenen Testsatz
  (2026-08-04, ADR-032-Nachtrag „Durchbruch"). Modellwahl damit korrigiert:
  Llama-3.2-3B als Router (Tool-Aufruf/Raumgespräch/Weiterleitung), Gemma 3n
  bleibt unfinetuned für freie Konversation – Zwei-Modell-Architektur statt
  einem Modell, siehe Konsequenzen im ADR.
- [x] Feineres Early-Stopping untersucht (`--save-every 20`, neues
  `sweep_checkpoints.py`) – ergab einen wichtigeren Befund als erhofft:
  **Lauf-zu-Lauf-Varianz.** Ein zweiter Lauf mit identischer Konfiguration
  (`llama_router_v2`) konvergierte klar schlechter (bestes Ergebnis 31/52,
  59,6 %) als der erste (46/52, 88,5 %) – `mlx_lm.lora` setzt standardmäßig
  keinen `--seed`. Zusätzlich: Val-Loss fiel in v2 durchgehend monoton,
  während die Trefferquote zwischenzeitlich auf 0/20 einbrach (Iter 40-120)
  – Loss ist für diese Aufgabe kein verlässlicher Stellvertreter für
  Ausgabegenauigkeit. `adapters/llama_router_v1_iter100` bleibt der beste
  Adapter (2026-08-04, Details `training/llm/README.md`).
- [x] `gen_dialogue.py` auf lokale Generierung umgestellt (`--backend local`,
  Standard, Llama-3.2-3B, kein API-Key noetig) – `--backend claude` bleibt
  als Option. Dabei drei echte Bugs behoben (Endlosschleife ohne
  Terminierungs-Limit, JSON-Array-Format zu fehleranfaellig fuer ein
  3B-Modell → EINGABE:/AUSGABE:-Zeilenformat, woertliches Kopieren von
  Platzhaltertext aus der Prompt-Beschreibung), Validierung verschaerft
  (`re.fullmatch` statt `findall`, Dublettenprüfung), neues `--append`-Flag
  fuer inkrementelles Anhaeufen ueber mehrere Laeufe (2026-08-04, Details
  `training/llm/README.md`).
- [x] Datensatz-Skalierung per lokaler `--append`-Generierung an ihre
  praktische Grenze gefahren: erster Lauf +18 Beispiele (345 → 363), ein
  zweiter Lauf danach ergab **0 neue Beispiele in jeder einzelnen Kategorie**
  (0/5 bei allen 9 Werkzeugen, 0/15 Negative, 0/15 Persona) – der bestehende
  Datensatz deckt praktisch alles ab, was ein 3B-Modell fuer diese engen
  Kategorien natuerlicherweise produziert. Endgueltig bestaetigt: lokale
  Generierung kann diesen 361er-Datensatz nicht weiter vergroessern, egal
  wie das Versuchslimit oder der Prompt eingestellt sind (2026-08-04, Details
  `training/llm/README.md`). Fuer weiteres Wachstum: Claude
  (`--backend claude`, sobald wieder Guthaben vorhanden) oder manuell
  geschriebene Beispiele (z.B. echte STT-Verhörer aus der Praxis).
- [x] Neu trainiert auf dem 363er-Datensatz mit `--seed 0` (Empfehlung
  befolgt) + `--save-every 20`: Val Loss diesmal durchgehend stabil (keine
  Divergenz), Checkpoint-Sweep zeigt flaches Plateau 18/20 von Iter 20-300.
  Volle Auswertung (Iter 200, 54er-Testsatz): **51/54 (94,4 %) korrekt** –
  neuer Bestwert, löst `llama_router_v1_iter100` (88,5 %) ab. Dabei zwei
  falsch beschriftete Trainingsbeispiele aus dem lokalen `--append`-Batch
  gefunden und entfernt (Begrüßungen fälschlich als `sms_vorlesen()`/
  `stopp()` gelabelt – `valid_tool_call()` prüft nur AUSGABE-Syntax, nicht
  Input/Output-Konsistenz; 363 → 361 Rohbeispiele) (2026-08-04, Details
  `training/llm/README.md`).
- [x] Input/Output-Konsistenz-Check ergaenzt: erster Ansatz (Router-Prompt
  selbst zur Klassifikation befragen) verworfen – Zirkelschluss, das
  unfinetunte Basismodell ist beim Router-Task selbst schwach, Ertragsrate
  brach von 94% auf 12% ein, weil GUTE Beispiele massenhaft abgelehnt wurden.
  Ersetzt durch `is_generic_smalltalk()` – billiger Regex-Filter ohne
  zusaetzlichen Modellaufruf, trifft gezielt den beobachteten Fehlerfall
  (2026-08-04, Details `training/llm/README.md`).
- [ ] Halluzinierte-Werkzeug-Fälle (z.B. erfundenes `wetter_vorlesen()`)
  durch mehr Negativbeispiele adressieren, sobald die Datensatz-Skalierung
  entblockt ist
- [ ] Router-Ausgabe muss Kotlin-seitig gegen die bekannte Tool-Liste
  validiert werden (unbekannter Funktionsname → sicherer Fallback, nie
  Absturz) – Konsequenz aus dem Halluzinations-Fund oben
- [ ] Speicherbudget für 4GB-Zielgeräte mit zwei Modellen (Llama-3.2-3B
  ~2GB + Gemma-3n-E2B ~2GB RAM) prüfen – war mit einem Modell unkritischer
- [ ] ONNX Runtime GenAI als Alternative zu MediaPipe/LiteRT prüfen (Lina
  hat mit `onnxruntime-android` bereits eine ONNX-Runtime-Abhängigkeit;
  unterstützt Llama/Gemma/Qwen/Phi mit int4 fürs Mobile, dokumentierter
  LoRA-Adapter-Deploy-Weg über Olive) – noch keine Entscheidung
- [x] Build-Flavor-Grundgerüst (Gradle, ADR-034, 2026-08-12): Flavor-Dimension
  `distribution` mit `standard` (`dev.lina`, Claude API wie bisher) und `ngo`
  (`dev.lina.ngo`, `CLAUDE_API_KEY` hart leer, Anthropic-SDK nur
  `standardImplementation`). `ClaudeConversation.kt` nach `src/standard/`
  verschoben; `ConversationEngineProvider` (identische Funktion, pro Flavor
  eigener Rumpf) ist der einzige Andockpunkt in `LauncherActivity` – kein
  direkter `ClaudeConversation`-Zugriff mehr im geteilten Code. Kein
  `GemmaConversation` in dieser Phase, reine Scaffolding. Verifiziert:
  `testStandardDebugUnitTest`+`testNgoDebugUnitTest`+`assembleDebug` grün,
  APK-Vergleich bestätigt `com/anthropic` fehlt komplett im ngo-APK.
  CI (`.github/workflows/build.yml`) und CLAUDE.md-Testbefehl angepasst
  (`testDebugUnitTest` gibt es flavor-los nicht mehr).
- [ ] Entscheidung Websuche-/Vision-Ersatz im NGO-Flavor (RSS-Fallback
  reaktivieren vs. Feature weglassen; Gemma-3n-Vision fürs Dokument-Vorlesen
  gegen Sonnet 5 prüfen – Risiko für eine Zielgruppe, die nicht gegenlesen
  kann) – **weiterhin offen nach Phase D**: "was gibt es Neues" landet im
  ngo-Flavor aktuell auf der generischen "nicht verstanden"-Meldung statt
  einer klaren Erklärung, das war schon vor ADR-034 so und ist bewusst nicht
  mitgelöst worden
- [ ] Lizenz-Weitergabepflicht (Gemma Terms of Use) gegenüber dem
  NGO-Betreiber klären, bevor ein Gerät ausgeliefert wird
- [ ] Gezielter Gerätetest (Latenz/Akku/Thermik) auf Dimensity-6300-Klasse –
  nicht zwingend das eigene Tablet

---

## 🟡 Plan bis zum Bewerbungsfenster (Stand 2026-07-18, Etappen unten 2026-07-26 geprüft) — P2 Mittel

> Die Etappen-Einteilung selbst ist eine strategische Entscheidung der
> Trägerschaft und wird hier nicht neu zugeschnitten – nur der Ist-Stand der
> einzelnen Punkte wurde gegen den Code/die Gerätetests von heute geprüft.

### Etappe 1 – Juli: Claude-Anbindung verifizieren & Release-fähig werden
- [x] `CLAUDE_API_KEY` in local.properties hinterlegen + Tablet-Test der freien Konversation – ausführlich am Gerät verifiziert (mehrere echte Gesprächsrunden über die gesamte Session, inkl. Do-Intents aus Freitext) – 2026-07-26
- [x] Bestätigungston/Earcon während STT-Transkription und Claude-Wartezeit (Earcons.kt; Klang auf Gerät noch validieren) – 2026-07-18
- [ ] Release-Keystore anlegen (`.jks`, lokal) + signiertes `assembleRelease`-APK – **siehe "Auslieferung Testnutzer → Build & Release"**, dort der einzige Ort dafür
- [ ] SSH-Key für GitHub einrichten (aktuell HTTPS)

### Besuch Testnutzer #2 — P2 Mittel
- [ ] Aufnahme-Runden Zukunftsbefehle (Wetter/Nachrichten/Brief/Podcast – Liste siehe Chat/Session)
- [ ] Wetter + Regionalnachrichten live mit Testnutzer durchspielen
- [ ] Nachtrainiertes Weckwort-Modell aufspielen und 5/5-Test wiederholen
- [ ] Battery-Whitelist + Dauerbetrieb prüfen, Abschluss Übergabe-Checkliste

### Etappe 2 – August: Robustheit & Gerätetest komplett — P1/P2 gemischt (siehe Einzelpunkte)
- [ ] Raumdistanz-Spike: GTCRN-Entrauschen vor Whisper (sherpa-onnx) prototypisch einbauen und auf dem Tablet messen (Fehlerrate vorher/nachher) — P2
- [ ] Offene Gerätetests abarbeiten (Anrufe/SMS **blockiert ohne SIM-Testgerät**, s.o.; Hörbücher/Onboarding bereits erledigt) — P1
- [ ] Dauerbetrieb: Service-Stabilität 1h / 4h / über Nacht auf dem Lenovo (ZUI-Battery-Killer) — P1
- [ ] Tablet des Zieltestnutzers vorbereiten: Wake-Word-Nachtraining mit seiner Stimme ("Aufnahme"-Befehl → training/) — P2

### Etappe 3 – September: Sichtbarkeit & Antragsreife — P3 Niedrig (noch nicht dran)
- [ ] 2-Minuten-Demo-Video: Lina auf dem Tablet (Anruf, Nachrichten, Konversation) – für Landingpage und Anträge
- [ ] Demo-Video auf Landingpage einbinden (mit Transkript/Untertiteln – barrierefrei)
- [ ] Fördermittel-Unterlagen finalisieren (intern, siehe lokaler Ordner)
- [ ] Kurzanleitung für Nutzer in Audioform aufnehmen

### Etappe 4 – Oktober/November: Einreichen & erzählen — P3 Niedrig (noch nicht dran)
- [ ] Förderbewerbung einreichen (Fenster: 01.10.–30.11.2026)
- [ ] Lina in Accessibility-/FOSS-Communities vorstellen (Foren, Mastodon, ggf. Vortrag)

## 🟢 Open-Source-Launch — ✅ Erledigt

- [x] Datenschutz-Sweep: NUTZERPROFIL.md gitignored, Klarnamen durch fiktive ersetzt (2026-07-16)
- [x] LICENSE (Apache 2.0) + NOTICE.md (2026-07-16)
- [x] README + CONTRIBUTING publikumstauglich (2026-07-16)
- [x] Landingpage `docs/index.html` (barrierefrei, Hochkontrast) (2026-07-16)
- [x] Frisches öffentliches Repo ohne History: `KostakisMT/lina-assistant` (alt: `lina-assistant-private`, bleibt Archiv) – 2026-07-18
- [x] GitHub Pages aktiv: https://kostakismt.github.io/lina-assistant/ – 2026-07-18
- [x] Repo-Beschreibung, Homepage + Topics gesetzt – 2026-07-18

---

## ⏰ Erinnerungen & Wecker — P2 Mittel (Restpunkte)

- [x] AlarmManager-Infrastruktur, offline, Doze-fest (2026-07-20)
- [x] Deutsches Zeitparsing lokal (relativ, Uhrzeit, halb/viertel, täglich) (2026-07-20)
- [x] Ansage per TTS + Benachrichtigung als Rückfall (2026-07-20)
- [x] Claude-Tools als Fallback für verstümmelte Eingaben (2026-07-20)
- [x] Rescheduling nach Neustart (BootReceiver) (2026-07-20)
- [ ] Neustart-Rescheduling am Gerät verifizieren
- [ ] Einzelne Erinnerung per Sprache löschen ("lösche die Erinnerung an den Arzt")
- [ ] Exact-Alarm-Recht auf dem Zielgerät prüfen

---

## 📷 Dokument-Vorlesen (Meilenstein 3) — P2 Mittel (Restpunkte, Offline-OCR ist P4)

- [x] CameraX-Rückkamera-Aufnahme, headless mit eigenem Lifecycle (2026-07-20)
- [x] Vision-Auswertung über Claude, relevanzgefiltert + "alles vorlesen" (2026-07-20)
- [x] Intent + Folgefenster (ja/alles, wiederhole, nochmal) (2026-07-20)
- [x] Debug-Befehl "testfoto" zum Ausrichten des Rahmens (2026-07-20)
- [ ] Rahmen beim Testnutzer aufkleben und mit "testfoto" ausrichten
- [x] Mit echter Post getestet (einseitiger Behördenbrief), Claude liest sinnvoll relevant vor – 2026-07-25
- [x] Latenz gemessen: Foto→Vorlesen ~11–14s, bestätigt bisherige Schätzung – 2026-07-25
- [x] **Bugfix:** Kameraaufnahme löste auf dem ZUI-Tablet (physisch quer montiert) über die OEM-Funktion `OvCameraRotation` einen Konfigurationswechsel aus; ohne `android:configChanges` wurde die Activity dabei zerstört/neu gebaut, der Foto→Claude→Vorlesen-Thread hing an der toten alten Instanz fest (kompletter Stillstand der Sprachsteuerung, kein Crash-Log). Fix in AndroidManifest.xml + Absicherung in `PiperTtsEngine.speak()` – 2026-07-25
- [ ] Mehrseitige Post / Umschlag noch nicht getestet
- [ ] Offline-Alternative (On-Device-OCR) evaluieren – Backlog

---

## 🤝 Helfer-Anruf per Be My Eyes (ADR-033) — P2 Mittel (Restpunkte)

- [x] Recherche: keine offene Anruf-API bei Be My Eyes, nur umgekehrtes
  "Specialized Help"-Partnerprogramm – App-Handoff (Stufe 1) statt
  Deep-Link/AccessibilityService gewählt (2026-08-12)
- [x] `feature/helper/HelperCallLauncher.kt`: öffnet Be My Eyes per
  Launch-Intent, Play-Store-Seite als Fallback bei fehlender Installation
  (2026-08-12)
- [x] `ResolvedIntent.CallHelper` + `LocalCommandResolver.resolveHelperCall()`
  (steht vor `resolveCall`, sonst frisst dessen Kontaktname-Muster "ruf
  einen Helfer an") (2026-08-12)
- [x] `<queries>`-Eintrag in AndroidManifest.xml für Paketsichtbarkeit ab
  Android 11 (2026-08-12)
- [x] Unit-Tests (Trigger-Erkennung + Abgrenzung zu normalem Kontaktanruf),
  `./gradlew testDebugUnitTest` + `assembleDebug` grün (2026-08-12)
- [x] WARTUNG.md-Einwilligung ergänzt (Live-Video an eine anonyme Person)
  (2026-08-12)
- [ ] Am Gerät verifiziert: Be My Eyes installiert, "ruf einen Helfer an"
  öffnet die App tatsächlich; Fallback-Pfad (App fehlt) noch nicht getestet
- [ ] Stufe 2 (Deep-Link ohne Tap): BME-APK auf `shortcuts.xml`/App-Actions
  hin inspizieren, sobald Zeit ist – kein Sicherheitsthema, nur eine
  öffentliche Ressourcen-Datei lesen

---

## 🟢 Ambiente-UI für Angehörige/Besucher + Querformat — P3 Niedrig (Restpunkte)

- [x] `TtsEngine.isSpeaking()` im Interface + `AndroidTtsEngine`-Implementierung (2026-07-26)
- [x] `LinaActivity`-Zustandsmodell (Loading/Idle/Listening/Thinking/Speaking/Error) (2026-07-26)
- [x] `AudiobookManager.currentStatus()` (2026-07-26)
- [x] `LinaOrb` – dekorative Statuskugel, schlicht/flach, Schwarz/Weiß/Gold (2026-07-26)
- [x] `AudiobookPlayerPanel` – sichtbare Hörbuch-Steuerung für Angehörige (2026-07-26)
- [x] Debug-Eingabefeld/Log aus der UI entfernt (Broadcast-Mechanismus bleibt unverändert) (2026-07-26)
- [x] Querformat: `screenOrientation="sensorLandscape"` + Layout als `Row` (Kugel+Status links, Player rechts) (2026-07-26)
- [x] **Bugfix:** `labelLarge`-Textstil hatte fest Gold hinterlegt → Button-Beschriftungen auf goldenen Buttons unsichtbar (Gold auf Gold), behoben (2026-07-26)
- [x] Am Gerät verifiziert: Idle/Thinking/Listening sichtbar unterscheidbar, Player erscheint bei geladenem Buch, alle Buttons per Touch funktionsfähig, Querformat füllt Bildschirm ohne Letterboxing (2026-07-26)
- [x] Idle-, Kalender- und Hörbuch-Player-Zustand per Screenshot festgehalten (Emulator API 33) und in README.md + docs/index.html eingebaut (2026-08-09)
- [ ] Speaking-Zustand der Kugel weiterhin nicht per Screenshot festgehalten (Logik aber identisch/mitgetestet über `isSpeaking()`-Polling); Screenshots bislang vom Emulator, nicht vom echten Testtablet
- [ ] Performance-Check unter Dauerlast (Kugel-Animation + Piper-Synthese gleichzeitig) nicht gesondert gemessen

---

## 🟢 Schlafmodus — ✅ Erledigt

- [x] `ResolvedIntent.SleepMode`/`SleepModeOff` + `LocalCommandResolver.resolveSleepMode()` (2026-07-26)
- [x] `LauncherActivity.enterSleepMode()`/`exitSleepMode()`: Fenster-Helligkeit dimmen (0.04) + Lautstärke 30% (2026-07-26)
- [x] Unit-Tests für Erkennung ("schlafmodus", "gute nacht", "schlafmodus aus", "wach auf") + Abgrenzung zum bestehenden Hörbuch-Schlaf-Timer (2026-07-26)
- [x] Am Gerät verifiziert: `dumpsys display` bestätigt Helligkeit 0.04 nach "schlafmodus", Lautstärke springt auf ≈30%, "wach auf" stellt automatische Helligkeit wieder her (2026-07-26)

---

## 🟢 SIM-Erkennung + Kontakt-Import (SIM & vCard-Datei) — ✅ Erledigt (Einschränkung s.u.)

- [x] `SimIdentity`/`SimIdentityReader`/`SimChangeDetector` – Best-Effort-Fingerabdruck statt echter ICCID (ADR-029) (2026-07-26)
- [x] `ContactImportStore` (EncryptedSharedPreferences, wie `ReminderStore`) (2026-07-26)
- [x] `SimContactSource` (`content://icc/adn`), `ContactWriter` (Batch-Insert), `ContactDedup`+`PhoneNumberNormalizer` (2026-07-26)
- [x] `VCardParser` (vCard 2.1/3.0, pure/unit-testbar) + Dateipicker-Import (`ACTION_OPEN_DOCUMENT`) (2026-07-26)
- [x] Automatische Sprach-Nachfrage bei erkannter neuer/anderer SIM (auch beim allerersten Start) (2026-07-26)
- [x] Sprachbefehle "Kontakte von der SIM importieren" / "Kontakte aus einer Datei importieren" (2026-07-26)
- [x] Neue Berechtigung `WRITE_CONTACTS` (Manifest + `PermissionsGuide`) (2026-07-26)
- [x] Unit-Tests: `SimIdentityTest`, `PhoneNumberNormalizerTest`, `ContactDedupTest`, `VCardParserTest` (2026-07-26)
- [x] Am Gerät verifiziert: vCard-Import end-to-end (Dateipicker → Parser → Dedup → echte Contacts-DB, per `content query` bestätigt); SIM-Import-Befehl korrekt erkannt und ohne Absturz ausgeführt (Testgerät ohne SIM) (2026-07-26)
- [ ] Echter SIM-Wechsel am Testgerät nicht prüfbar (kein SIM-Steckplatz belegt) – nur unit-getestet

---

## 🟢 Hörbuch-Verfügbarkeit + LibriVox-Genre-Suche (ADR-030) — ✅ Erledigt (Einschränkung s.u.)

- [x] **Bugfix (P1, bestehendes Feature war lautlos kaputt):** `fields=`-Parameter entfernt – lieferte keine zusammengeführten JSON-Objekte, Titel/Autor/Dauer waren bei jeder LibriVox-Suche bisher leer bzw. „Unbekannt" (2026-07-26)
- [x] **Bugfix (P1):** `language`-Parameter wurde nie gesendet und wird von der API ohnehin ignoriert – jetzt client-seitiger Sprachfilter auf das API-Feld `language` (2026-07-26)
- [x] `LibrivoxGenres.kt` – Taxonomie (live von librivox.org/search gescrapt) + deutsche Synonymtabelle + `findGenre()` (2026-07-26)
- [x] `LibrivoxRepository.searchByGenre()`, `AudiobookLibrary.searchByTopic()` (Genre-Treffer, sonst Stichwort-Fallback) (2026-07-26)
- [x] `AudiobookManager.listBooks()` erweitert: LibriVox-Hinweis immer, proaktive Ja/Nein-Frage bei ≤2 Büchern (2026-07-26)
- [x] Neuer Sprachbefehl "Hörbücher zum Thema X" (`ResolvedIntent.SearchAudiobookByGenre`), vor der bestehenden Titel-/Autorensuche in der Erkennungskette (2026-07-26)
- [x] Unit-Tests: `LibrivoxGenresTest`, `LibrivoxRepositoryParseTest` (Regressionstest für den `fields=`-Bug), Resolver-Abgrenzungstests (2026-07-26)
- [x] `org.json:json` als Test-Abhängigkeit ergänzt (Android liefert nur einen Stub, damit war die Parsing-Logik bisher nicht JVM-testbar) (2026-07-26)
- [x] Am Gerät verifiziert: "Suche Hörbücher zum Thema Politik" → korrekt zu "Political Science" aufgelöst → echter Treffer *Manifest der Kommunistischen Partei* mit korrektem Titel/Autor; Regressionscheck normale Titel-/Autorensuche weiterhin einwandfrei (2026-07-26)
- [ ] Proaktiver Ja/Nein-Vorschlag bei dünner Bibliothek nicht separat am Gerät getestet (Testbibliothek hat >2 Bücher) – Logik folgt 1:1 dem verifizierten SIM-Import-Muster

---

## 🟢 Kalender (Datum, Termine + automatische Erinnerung, Dokument-Trigger, Wochenansicht, ADR-031) — ✅ Erledigt (Einschränkung s.u.)

- [x] `GermanCalendarNames.kt` – Wochentag-/Monatswortschatz aus `Reminder.kt` promoted, reiner Refactor (2026-07-26)
- [x] Datum-Ansage: `ResolvedIntent.Date` + `resolveDate()`, spiegelt `Time` (2026-07-26)
- [x] `CalendarEvent`/`CalendarStore` (EncryptedSharedPreferences wie `ReminderStore`) (2026-07-26)
- [x] `GermanDateParser` (P1, pure): relative Tage, Wochentag-relativ, explizite Daten, optionale Uhrzeit (2026-07-26)
- [x] `CalendarManager`: Termin-Anlage legt automatisch eine verknüpfte `Reminder` über die bestehende Infrastruktur an, kein zweites Scheduling (2026-07-26)
- [x] Neue Intents/Resolver: `SetCalendarEvent`/`SetCalendarEventAt`/`ShowCalendar`/`HideCalendar`/`ClearCalendarEvents`, vor `resolveReminder()` in der Kette (2026-07-26)
- [x] **Regex-Korrektur (P1, bestehendes Feature):** `ClearReminders`/`ListReminders` reagierten bisher auch auf "Termine" – hätte "lösche meine Termine" fälschlich alle Erinnerungen löschen lassen. Beide Regexe jetzt nur noch "Erinnerung(en)" (2026-07-26)
- [x] Claude-Tools: `termin_anlegen` (Ebene-2-Fallback, wie `erinnerung_anlegen`) + isoliertes `termin_erkannt` nur in `readDocument()` (2026-07-26)
- [x] **Dokument-Integration (P2, höheres Risiko):** `readDocument()` liefert jetzt `DocumentReadResult` (Text + optionaler Terminvorschlag); eigener neuer Ja/Nein-Dialog (`openDocCalendarFollowUp`), bestehende `handleDocFollowUp()` unangetastet (2026-07-26)
- [x] `CalendarPanel.kt`: Wochenansicht, große Schrift, Schwarz/Weiß/Gold, teilt sich den rechten Spalten-Slot mit `AudiobookPlayerPanel` (2026-07-26)
- [x] Unit-Tests: `GermanCalendarNamesTest`, `GermanDateParserTest` (12 Fälle), `CalendarEventTest` (JSON-Rundtrip), Resolver-Ergänzungen inkl. ClearReminders/ClearCalendarEvents-Abgrenzung (2026-07-26)
- [x] Am Gerät verifiziert: Datum-Ansage korrekt, Termin-Anlage plant sichtbar eine `Reminder` ("morgen um 9 Uhr" für "nächsten Montag" an einem Sonntag), `CalendarPanel` zeigt Woche inkl. Termin korrekt, Verstecken/Löschen funktioniert, **unveränderter Dokument-Pfad zuerst gegengetestet** (kein Termin erkannt → `termin_erkannt=false`, kein Absturz, Verhalten wie vorher) (2026-07-26)
- [ ] Positiver Dokument-Erkennungspfad (`termin_erkannt=true`) nicht am Gerät verifiziert – braucht ein reales Foto eines Dokuments mit konkretem Datum

---

## 🔵 Phase 2 – Geplant (nicht jetzt) — P4 Backlog

- [x] STT: Whisper über sherpa-onnx integriert (base int8, de) – 2026-07-02
- [x] TTS: Piper über sherpa-onnx integriert (de_DE-ramona-low) – 2026-07-02
- [x] Piper-Stimme mit Nutzer validiert → de_DE-dii-high gewählt (2026-07-04, ADR-016)
- [x] NOTICE.md mit Stimm-Attribution (OpenVoiceOS, CC BY-NC-SA) + LICENSE (Apache 2.0) angelegt – 2026-07-16
- [ ] STT-Wartezeit (~2s) akustisch überbrücken (kurzer Bestätigungston)
- [ ] STT-Robustheit bei Raumdistanz: Entrauschen vor Whisper prüfen (sherpa-onnx Speech-Enhancement/GTCRN – gleiche Runtime; Alternativen: RNNoise, Android NoiseSuppressor)
- [x] LLM-Anbindung: Claude API für freie Konversation (`ClaudeConversation`, ADR-017) – 2026-07-16
- [ ] Claude-Anbindung auf dem Tablet testen (echter API-Key in local.properties)
- [ ] Onleihe-Integration (Bibliothek per Ausweis)
- [ ] Podcast-Streaming (gPodder-Backend)
- [ ] Sprach-Einkauf: Wolt, Rewe Express, Picnic
- [ ] WhatsApp via AccessibilityService

---

## ✅ Erledigt

<!-- Erledigte Tasks hierher verschieben mit Datum -->
<!-- [x] 2026-05-29 – Projektdokumentation erstellt -->
