# CLAUDE.md – Lina · VoiceFirst Assistant

> **Pflichtlektüre für jede Claude Code Instanz.**
> Lies diese Datei vollständig bevor du irgendeine Zeile Code schreibst.
> Nach jeder abgeschlossenen Task: CHANGELOG.md und TODO.md aktualisieren (Repo-Root, nicht docs/).
> Architekturentscheidungen immer in DECISIONS.md festhalten.

---

## Projekt

**Name:** Lina – VoiceFirst Assistant
**Weckwort:** "Hey Lina"
**Art:** Open-Source Android-App (GitHub)
**Träger:** Vorerst privat vom Entwickler getragen – kein kommerzieller Vertrieb.
Eine spätere gemeinnützige Trägerschaft ist offen; konkrete Kandidaten werden
nicht öffentlich benannt (ADR-023).
**Lizenz:** Apache 2.0 (Code). Modelle können abweichende Lizenzen haben –
NC-Lizenzen (z.B. CC BY-NC-SA bei OVOS-Piper-Stimmen) sind nutzbar, weil Lina
ein rein nicht-kommerzielles, quelloffenes Projekt ohne kommerziellen Vertrieb
ist (NonCommercial bezieht sich auf die Art der Nutzung, nicht auf die
Rechtsform); pro Modell in DECISIONS.md zu dokumentieren.
**Ziel:** Blinden und sehbehinderten Menschen die selbstständige Teilhabe am Alltag ermöglichen – gesteuert per Sprache durch eine KI-Assistentin namens Lina.

---

## Primärer Nutzer (anonymisierte Persona)

> Persönliche Details (echte Kontakte, Gesundheits-/Lebensumstände) stehen in
> `NUTZERPROFIL.md` – die Datei ist **lokal und gitignored**, nie committen.

| Eigenschaft | Detail |
|---|---|
| Sehvermögen | Fortschreitende Erblindung, Farbenblind, Brille hilft nicht mehr |
| Körper | Sitzt viel, Hände sollen frei bleiben |
| Sprache | Deutsch (de-DE) |
| Gerät | Lenovo Idea Tab (TB336ZU), Wohnzimmer, stationär, WLAN |
| Referenz | Kannte Siri von Apple – zu limitiert |

### Wichtige Kontakte (Fuzzy-Matching Pflicht – fiktive Beispielnamen)
Arundhati Brandt, Boris Hartmann, Ulla Winter, Annika Berger,
Sabine Dreyer, Dirk Eßfeld, Hannah Schäfer, Gudrun Sommer

> ⚠️ Namen wie "Arundhati" und "Eßfeld" sind spracherkennungs-kritisch.
> Phonetische Varianten und Fuzzy-Matching müssen explizit hinterlegt werden.

### Nachrichteninteressen
- **Themen:** Politik, Wirtschaft, evidenzbasierte Wissenschaft, Nautik/Seefahrt/Segeln, Marxismus & politische Theorie
- **Vertrauensquellen:** Junge Welt (primär), unsere zeit/uz.de, Spektrum der Wissenschaft, Yacht/Segeln-Magazin
- **Modus:** Nur auf Abruf – KEIN automatisches Briefing
- **Format:** Kurze Zusammenfassung zuerst, auf Nachfrage vollständiger Artikel

### Hörbuch-Profil
- Keine Vorerfahrung mit Hörbüchern → sanftes Onboarding nötig
- **Interessen:** Kulturhistorisch, klassische Literatur, politische Sachbücher
- **Starttitel:** Aitmatow – Djamilah, Tolstoi, Gorki, Brecht
- **Ziel:** Zugang zu einer Bibliothek die vorgelesen wird (Onleihe + Librivox)

### Kommunikation
- **Anrufe:** Höchste Priorität
- **SMS:** Selten aktuell, aber motiviert wenn es zuverlässig funktioniert
- **WhatsApp:** Nutzt er nicht
- **Sprachmuster:** Formelle Vornamen bevorzugt. Bei Mehrdeutigkeit (z.B. "Boris" ohne Nachname, aber mehrere Boris im Telefonbuch) muss Lina rückfragen: "Welchen Boris meinst du?" + Auswahl vorlesen

---

## Lina – die Assistentin

- **Weckwort:** "Hey Lina"
- **Stimme:** Weiblich, Deutsch (de-DE)
- **TTS Rate:** 0.9f (leicht verlangsamt, deutlich)
- **Charakter:** Nicht limitiert wie Siri – kontextbewusst, erweiterbar, offline-fähig
- **Feedback-Stil:** Knapp und klar – kein unnötiges Gerede

---

## Leitprinzipien

1. **Voice-First, nicht Voice-Only** – Touch ist optionaler Fallback
2. **Offline where possible** – was lokal laufen kann, läuft lokal
3. **Kein Login-Zwang im MVP**
4. **Barrierefrei by default** – kein nachträgliches Patching
5. **Alle Kernkomponenten hinter Interfaces** – STT, TTS, Intent müssen austauschbar sein
6. **Kein Feature ohne TTS-Feedback** – jede Aktion bestätigt Lina akustisch
7. **Kein Feature ohne Doku** – jede Änderung landet in CHANGELOG.md

---

## Zielplattform

| Eigenschaft | Wert |
|---|---|
| OS | Android 13+ |
| Primärgerät | Lenovo Idea Tab (TB336ZU) |
| Formfaktor | Tablet, stationär, Ständer |
| Konnektivität | WLAN (DSL) |
| Min SDK | API 33 |

---

## Tech Stack

### Speech-to-Text (STT)

**Aktiv:** Whisper base int8 (multilingual, language=de) über **sherpa-onnx**
- `WhisperSttEngine`: nicht-streamend, nimmt bis ~1.2s Stille nach Sprachbeginn auf (max 10s)
- Erkennt natürliche Sprache und schwierige Namen ("Arundhati") zuverlässig
- ~2s Transkription für ~5s Audio auf dem Lenovo-Tablet

**Fallback:** Vosk (`vosk-model-small-de`) – `VoskSttEngine`, wird geladen wenn Whisper-Init fehlschlägt

```kotlin
interface SttEngine {
    fun startListening(onResult: (String) -> Unit)
    fun stopListening()
    fun destroy()
}
```

### Intent-Parsing (Hybrid – KEIN reiner when-Block)

**Ebene 1 – Lokal, <50ms:**
- Regex + Keyword-Matching
- Finite Intent-Liste: anrufen, SMS, Nachrichten, Pause, Stopp, Hörbuch …
- Kontakt-Spitznamen aus Nutzerprofil vorbelegt

**Ebene 2 – Lokales LLM (wenn Ebene 1 kein Match):**
- Quantisiertes Modell on-device (Phi-3 mini / Gemma 2B GGUF)
- Rephrasing erkennen, Slot Extraction, Kontext halten
- Implementierung Phase 2

```kotlin
interface IntentResolver {
    fun resolve(input: String): ResolvedIntent?
}
// Implementierungen: LocalCommandResolver, LlmIntentResolver
```

### Text-to-Speech (TTS)

**Aktiv:** Piper TTS über **sherpa-onnx** – `PiperTtsEngine`
- Stimme: `de_DE-dii-high` (OpenVoiceOS, 22kHz, hohe Qualität), Rate 0.9f
  – Lizenz CC BY-NC-SA 4.0, nutzbar da rein nicht-kommerzielles Projekt (ADR-016, ADR-023)
- Synthese ~0.5–1s pro Satz auf dem Lenovo-Tablet, komplett offline
- Stimmwechsel: Modell in `assets/piper/` + `AVAILABLE_VOICES` ergänzen;
  zur Laufzeit per Befehl "Stimme <n>" / "nächste Stimme" (Debug-Feature)

**Fallback:** Android System TTS (`AndroidTtsEngine`), wenn Piper-Init fehlschlägt

> Lina spricht stundenlang. TTS-Qualität ist Kernfunktion, keine Kosmetik.

```kotlin
interface TtsEngine {
    fun speak(text: String, priority: TtsPriority = TtsPriority.NORMAL)
    fun stop()
    fun setRate(rate: Float)
    fun isSpeaking(): Boolean          // treibt die Statuskugel (LinaOrb): Speaking-Zustand
    fun shutdown()
}

enum class TtsPriority { LOW, NORMAL, HIGH, INTERRUPT }
```

### Wake Word
- **Engine:** OpenWakeWord (ONNX Runtime, Apache 2.0)
- **Weckwort:** "Hey Lina" – eigenes Modell `hey_lina_v1.onnx`, trainiert mit
  synthetischen TTS-Daten + echten Nutzeraufnahmen (Ablauf: `training/README.md`;
  für neue Nutzer: Debug-Befehl "Aufnahme" → Nachtraining)
- **Modelle:** `melspectrogram.onnx` + `embedding_model.onnx` + Classifier in `assets/openwakeword/`
- **Download:** `scripts/download-models.sh` ausführen nach Repo-Clone
- **Kein API-Key nötig**

```kotlin
interface WakeWordEngine {
    fun start(onDetected: () -> Unit)
    fun stop()
    fun destroy()
}
```

**OEM-Stabilitätsstrategie (aggressive App-Killer, gilt für Lenovo wie zuvor Samsung):**
- ForegroundService mit sichtbarer Notification (Pflicht ab Android 12)
- Battery-Whitelist Onboarding (Nutzer wird geführt)
- `PARTIAL_WAKE_LOCK` für stationären Betrieb
- Watchdog: prüft Service, startet neu falls gekillt
- `RECEIVE_BOOT_COMPLETED` für Autostart

### AccessibilityService (KERNKOMPONENTE)

Ohne AccessibilityService kein zuverlässiger eingehender Anruf per Sprache.

**Fähigkeiten:**
- Notifications in Echtzeit lesen
- UI-Trees fremder Apps analysieren und bedienen
- Eingehende Anrufe/SMS erkennen
- Später: Wolt, Rewe, Picnic per Sprache bedienen

```xml
<service android:name=".core.accessibility.LinaAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService"/>
    </intent-filter>
    <meta-data android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config"/>
</service>
```

`accessibility_service_config.xml`:
```xml
<accessibility-service
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackSpoken"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"/>
```

---

## Architektur – Grundfluss

```
[Boot]
  → [LinaService startet als ForegroundService]
  → [OpenWakeWord hört dauerhaft auf Weckwort]
  → [LinaAccessibilityService läuft parallel]

[Nutzer]: "Hey Lina, ruf Boris an"
  → [STT: Befehl aufnehmen]
  → [IntentResolver Ebene 1: "anrufen" + Slot "Boris"]
  → [Kontakt Boris Hartmann gefunden]
  → [Anruf starten]
  → [Lina: "Ich rufe Boris Hartmann an"]

[Nutzer]: "Hey Lina, was gibt es Neues?"
  → [NewsFeature: RSS-Cache laden]
  → [Lina: "Drei Meldungen. Erstens: ..."]
  → [Nutzer]: "Mehr zur zweiten"
  → [Lina: vollständiger Artikel]
```

---

## Modulstruktur

> Seit ADR-034 gibt es zwei Gradle-Build-Flavors (`standard`/`ngo`, ADR-032).
> Alles unten liegt in `src/main/` und ist geteilter Code für beide Flavors.
> Die Claude-API-Anbindung ist die einzige Ausnahme: `ClaudeConversation.kt`
> liegt in `app/src/standard/kotlin/dev/lina/core/llm/` (nicht `src/main/`),
> daneben in `app/src/ngo/kotlin/dev/lina/core/llm/` ein
> `ConversationEngineProvider`-Gegenstück ohne Cloud-Anbindung. Ein künftiges
> `GemmaConversation` (ADR-032, noch nicht gebaut) gehört ebenfalls unter
> `src/ngo/`.

```
app/src/main/kotlin/dev/lina/
├── core/
│   ├── wakeword/
│   │   ├── WakeWordEngine.kt        # Interface
│   │   ├── OpenWakeWordEngine.kt    # ONNX-basierte Implementierung
│   │   ├── WakeWordService.kt       # ForegroundService
│   │   └── WakeWordWatchdog.kt      # OEM-Killschutz
│   ├── stt/
│   │   ├── SttEngine.kt             # Interface
│   │   ├── WhisperSttEngine.kt      # Aktiv (sherpa-onnx, Whisper base int8)
│   │   └── VoskSttEngine.kt         # Fallback
│   ├── tts/
│   │   ├── TtsEngine.kt             # Interface + TtsPriority
│   │   ├── PiperTtsEngine.kt        # Aktiv (sherpa-onnx, de_DE-ramona)
│   │   └── AndroidTtsEngine.kt      # Fallback
│   ├── intent/
│   │   ├── IntentResolver.kt        # Interface
│   │   ├── ResolvedIntent.kt        # Datenklassen
│   │   ├── LocalCommandResolver.kt  # Ebene 1: Regex/Keywords
│   │   └── LlmIntentResolver.kt     # Ebene 2: Stub für Phase 2
│   ├── contacts/
│   │   ├── ContactRepository.kt     # ContactsContract Wrapper (Lesen)
│   │   ├── FuzzyContactMatcher.kt   # Phonetisches Matching
│   │   ├── SimContactSource.kt      # SIM-ADN-Kontakte lesen (content://icc/adn)
│   │   ├── ContactWriter.kt         # Batch-Insert neuer Kontakte
│   │   ├── ContactDedup.kt          # + PhoneNumberNormalizer.kt: Duplikat-Erkennung
│   │   └── VCardParser.kt           # vCard 2.1/3.0, pure/unit-testbar
│   ├── xml/
│   │   └── SecureXml.kt             # Einziger erlaubter DocumentBuilder (XXE-Härtung, ADR-036)
│   ├── sim/
│   │   ├── SimIdentity.kt           # Best-Effort-Fingerabdruck (ADR-029)
│   │   ├── SimIdentityReader.kt     # SubscriptionManager/TelephonyManager
│   │   └── SimChangeDetector.kt     # NoSim/FirstSeen/Unchanged/Changed
│   └── accessibility/
│       └── LinaAccessibilityService.kt
├── feature/
│   ├── calls/
│   │   ├── CallHandler.kt
│   │   └── IncomingCallReceiver.kt
│   ├── sms/
│   │   ├── SmsReader.kt
│   │   └── SmsSender.kt
│   ├── news/
│   │   ├── RssFeedRepository.kt
│   │   ├── NewsCache.kt             # Lokaler Cache
│   │   ├── NewsSyncWorker.kt        # WorkManager
│   │   └── NewsReader.kt            # TTS-Steuerung
│   ├── audiobook/
│   │   ├── AudiobookPlayer.kt       # ExoPlayer Wrapper (Kapitel-Playlist)
│   │   ├── Chapter.kt               # Kapitel-Modell (opt. Zeitbereich)
│   │   ├── DaisyParser.kt           # DAISY 2.02: ncc.html + SMIL
│   │   ├── DaisyRepository.kt       # Buchordner erkennen und auflösen
│   │   ├── LibrivoxRepository.kt    # LibriVox-API (Titel/Autor/Genre, deutschsprachig gefiltert)
│   │   ├── LibrivoxGenres.kt        # Feste Genre-Taxonomie + deutsche Synonyme (ADR-030)
│   │   └── PlaybackStateStore.kt    # Fortschritt persistent (inkl. Kapitel)
│   ├── document/
│   │   └── DocumentCamera.kt        # CameraX Rückkamera, eigener Lifecycle
│   ├── helper/
│   │   └── HelperCallLauncher.kt    # Öffnet Be My Eyes (App-Handoff, ADR-033)
│   ├── contactimport/
│   │   ├── ContactImportManager.kt  # Orchestriert SIM-/vCard-Import (Dedup+Write)
│   │   └── ContactImportStore.kt    # EncryptedSharedPreferences: SIM-Fingerabdruck
│   ├── reminder/
│   │   ├── Reminder.kt              # Datenmodell + spokenTime()
│   │   ├── ReminderStore.kt         # EncryptedSharedPreferences
│   │   ├── ReminderScheduler.kt     # AlarmManager (setAlarmClock, Doze-fest)
│   │   ├── ReminderManager.kt       # Anlegen/Ansagen/Löschen
│   │   └── GermanTimeParser.kt      # Relative Zeiten/Uhrzeiten (kein Datum)
│   ├── calendar/
│   │   ├── CalendarEvent.kt         # Termin-Datenmodell (Datum/Uhrzeit/Quelle)
│   │   ├── CalendarStore.kt         # EncryptedSharedPreferences
│   │   ├── GermanDateParser.kt      # Datumsphrasen: relativ/Wochentag/explizit
│   │   └── CalendarManager.kt       # Legt bei jedem Termin eine verknüpfte Reminder an (ADR-031)
│   └── onboarding/
│       ├── PermissionsGuide.kt
│       ├── VoiceOnboarding.kt       # Gesprochene Ersteinrichtung
│       └── BatteryWhitelistGuide.kt
└── ui/
    ├── launcher/
    │   ├── LauncherActivity.kt
    │   └── LinaActivity.kt          # Zustandsmodell (Idle/Listening/Thinking/Speaking/Error)
    └── components/
        ├── LinaTheme.kt             # Hochkontrast-Theme
        ├── LinaOrb.kt               # Statuskugel für Angehörige/Besucher (dekorativ)
        ├── AudiobookPlayerPanel.kt  # Sichtbare Hörbuch-Steuerung für Angehörige
        └── CalendarPanel.kt         # Wochenansicht für Angehörige/Pflegepersonal
```

---

## Phase 1 – MVP Features

### Anrufe (Priorität 1)
| Befehl | Aktion |
|---|---|
| "Ruf Boris an" | Kontakt suchen → anrufen |
| "Ruf Mama an" | Spitznamen-Mapping → anrufen |
| "Annehmen" | Eingehenden Anruf annehmen |
| "Ablehnen" | Eingehenden Anruf ablehnen |
| "Auflegen" | Aktiven Anruf beenden |

### SMS (Priorität 2)
| Befehl | Aktion |
|---|---|
| "Schreib Boris: [Text]" | SMS senden, Lina bestätigt |
| "Lies meine Nachrichten" | Neueste SMS vorlesen |
| "Antwort: [Text]" | Auf letzte SMS antworten |

### Nachrichten (Priorität 3)
| Befehl | Aktion |
|---|---|
| "Was gibt es Neues?" | Zusammenfassungen vorlesen |
| "Mehr dazu" | Vollständigen Artikel vorlesen |
| "Nächste Meldung" | Weiter |
| "Stopp" | Vorlesen beenden |

**RSS-Feeds (vorkonfiguriert):**
- `https://www.jungewelt.de/rss.php` (Junge Welt)
- `https://www.unsere-zeit.de/feed/` (unsere zeit)
- `https://www.spektrum.de/rss/news` (Spektrum der Wissenschaft)
- `https://www.yacht.de/feed/` (Yacht / Segeln)

### Hörbücher (Priorität 4)
| Befehl | Aktion |
|---|---|
| "Spiel Hörbuch ab" | Letztes/erstes Hörbuch starten |
| "Pause" / "Weiter" | Wiedergabe steuern |
| "30 Sekunden zurück" | Zurückspulen |
| "Was höre ich gerade?" | Titel und Kapitel ansagen |
| "Nächstes Kapitel" / "Ein Kapitel zurück" | Kapitel wechseln |
| "Kapitel drei" | Zu Kapitel springen (Zahl oder Zahlwort) |
| "Welche Kapitel gibt es?" | Kapitel auflisten (max. 10, dann Hinweis) |
| "Welche Hörbücher habe ich?" | Lokale Bibliothek auflisten + LibriVox-Hinweis; bei ≤2 Büchern proaktive Ja/Nein-Frage |
| "Hörbücher zum Thema Politik" | LibriVox-Genre-Suche (ADR-030) – feste Taxonomie + Stichwort-Fallback |
| "Suche Tolstoi" | Titel-/Autorensuche (lokal, sonst LibriVox) |

**Formate:** lokale MP3/M4B, LibriVox-Streaming und **DAISY 2.02** – das Format
der Blindenhörbüchereien (Buchordner mit `ncc.html`, ADR-019). Kapitel sind
durchgängiges Konzept: der Player spielt immer eine Playlist, nie eine
Einzeldatei.

**LibriVox-Genre-Suche (ADR-030):** LibriVox hat keine freie Themensuche, nur
eine feste, englischsprachige Genre-Taxonomie (`LibrivoxGenres.kt`, live von
librivox.org gescrapt – kein API-Endpunkt dafür). Ein ungültiger Genre-Name
liefert HTTP 500, nie eine leere Liste – deshalb wird jeder Nutzer-Rohtext
erst gegen die Taxonomie validiert. Ohne Treffer fällt die Suche auf normale
Stichwortsuche zurück; Lina sagt an, welcher der beiden Wege es war.

### Dokument-Vorlesen (Priorität 5, ADR-018)
Dokument liegt im festen Rahmen vor dem Tablet (Rückkamera).

| Befehl | Aktion |
|---|---|
| "Lies mir die Post vor" | Foto → Vision → Relevantes vorlesen |
| "Was steht da?" | Dito |
| "Ja" / "alles" (nach der Nachfrage) | Vollständigen Text vorlesen |
| "Nochmal" | Neues Foto (z.B. nach Umblättern) |
| "Testfoto" (Debug) | Foto speichern, um den Rahmen auszurichten |

> Das Bild verlässt das Gerät (Cloud-Vision) und wird **nicht** gespeichert –
> nur transient im RAM. Einwilligung siehe WARTUNG.md.

### Helfer-Anruf per Be My Eyes (ADR-033)
Ergänzt das Dokument-Vorlesen um alles, was ein einzelnes Foto nicht abdeckt
(Objekte, Umgebung, Rückfragen in Echtzeit) – per Live-Videoanruf zu einem
sehenden Freiwilligen.

| Befehl | Aktion |
|---|---|
| "Ruf einen Helfer an" / "Be My Eyes" / "Hilfe beim Sehen" | Öffnet die App Be My Eyes |

> Be My Eyes hat **keine offene API**, um einen Anruf ins Freiwilligennetzwerk
> auszulösen (nur das umgekehrte "Specialized Help"-Programm für
> Unternehmen). Lina öffnet daher nur die App (App-Handoff) – der letzte Tap
> auf "Call a Volunteer" bleibt bei der Nutzer:in, was zu Leitprinzip 1 passt
> (Be My Eyes ist selbst TalkBack-optimiert). Fehlt die App, öffnet Lina
> stattdessen die Play-Store-Seite statt selbst zu installieren. Externe
> Abhängigkeit – muss vor der Übergabe separat installiert sein. Einwilligung
> (Live-Video an eine anonyme Person) siehe WARTUNG.md.

### Schlafmodus
| Befehl | Aktion |
|---|---|
| "Schlafmodus" / "Gute Nacht" / "Schlafenszeit" | Bildschirm dimmen (Fenster-Helligkeit 0.04) + Lautstärke auf 30% |
| "Schlafmodus aus" / "Wach auf" / "Licht an" | Automatische Helligkeitssteuerung wiederherstellen |

Wirkt sofort, auch mitten in einem laufenden Gespräch (wie "Stopp" lokal
priorisiert, nicht Claude überlassen) – siehe ADR-028.

### SIM-Erkennung + Kontakt-Import (ADR-029)
| Befehl | Aktion |
|---|---|
| (automatisch bei neuer/anderer SIM, auch beim allerersten Start) | Nachfrage: "Soll ich die Kontakte übernehmen?" |
| "Kontakte von der SIM importieren" | SIM-Kontakte (`content://icc/adn`) lesen, entduplizieren, schreiben |
| "Kontakte aus einer Datei importieren" | System-Dateipicker für eine vCard-Datei (.vcf) öffnen |

Realistischer Ersatz für einen "Migrationsassistenten" – ein echtes
Geräte-Pairing (Googles Quick Switch) ist für eine Drittanbieter-App nicht
zugänglich. Google-Konto-Sync während der Android-Ersteinrichtung braucht
keinen Lina-Code, `ContactRepository` liest diese Kontakte ohnehin mit.

### Kalender (ADR-031)
| Befehl | Aktion |
|---|---|
| "Welches Datum haben wir heute?" / "Welcher Tag ist heute?" | Datum ansagen |
| "Trage einen Termin ein für nächsten Montag: Zahnarzt" | Termin anlegen (Doppelpunkt trennt Datum von Titel) + automatische Erinnerung |
| "Zeig mir den Kalender" / "Was sind meine nächsten Termine?" | Wochenansicht einblenden + Termine ansagen |
| "Verstecke den Kalender" | Wochenansicht ausblenden |
| "Lösche meine Termine" | Alle Termine + verknüpfte Erinnerungen löschen |
| (automatisch beim Dokument-Vorlesen) | Erkennt Claude einen konkreten Termin/eine Frist im Foto, bietet an, ihn einzutragen |

Jeder Termin legt automatisch eine ganz normale `Reminder` an – kein zweites
Scheduling-System. `GermanDateParser` versteht relative Tage, Wochentag-relativ
("nächsten Montag") und explizite Daten ("am 15. März", "15.3."), aber keine
Wiederholung und keinen konfigurierbaren Erinnerungs-Vorlauf (bewusst einfach
gehalten). Die Dokument-Erkennung läuft über ein isoliertes Claude-Tool, das
ausschließlich beim Vorlesen registriert wird – nie im freien Gesprächspfad.

### Ambiente-UI für Angehörige/Besucher
Der primäre Nutzer ist blind – die visuelle Oberfläche ist ausdrücklich für
sehende Angehörige/Besucher gedacht, die sehen wollen, was Lina gerade tut,
oder ein laufendes Hörbuch bedienen bzw. den Kalender einsehen möchten. Audio
bleibt für den Nutzer selbst die einzige Schnittstelle.
- **LinaOrb:** rein dekorative animierte Statuskugel, unterscheidet
  Idle/Listening/Thinking/Speaking/Error über Bewegungscharakter statt Farbe
- **AudiobookPlayerPanel:** sichtbare Steuerung (Titel/Kapitel/Fortschritt,
  Zurück/-30s/Pause/Vor, Lautstärke) – erscheint nur bei geladenem Buch
- **CalendarPanel:** Wochenansicht (heute..+6 Tage), große Schrift – teilt
  sich den rechten Panel-Slot mit dem AudiobookPlayerPanel (Kalender hat
  Vorrang, solange er per Sprachbefehl angefordert wurde)
- **Querformat:** `screenOrientation="sensorLandscape"`, da das Tablet fast
  immer liegend im Ständer steht; Kugel+Status links, Player/Kalender rechts

---

## Nächster Schritt

- Anrufe/SMS am echten Gerät testen (bisher nur Kernlogik, kein SIM im Testgerät)
- Release-Keystore anlegen + signiertes `assembleRelease`
- Dauerbetrieb über mehrere Stunden/über Nacht verifizieren (Lenovo/ZUI Battery-Killer)
- STT-Robustheit bei Raumdistanz verbessern (Whisper-Verhörer bei Befehlen)
- Dokument-Termin-Erkennung mit einem echten Dokument (Datum/Frist) am Gerät verifizieren – bisher nur der unveränderte Negativ-Pfad bestätigt
- Helfer-Anruf (ADR-033) am Gerät verifizieren: Be My Eyes installieren, "ruf einen Helfer an" testen, Fallback-Pfad ohne installierte App prüfen

## Vision (Nordstern – bestimmt die Priorisierung von Phase 2+)

Lina soll kein Kommando-Empfänger sein, sondern eine freundliche Gesprächspartnerin:
jemand, der zuhört, mit dem man sich unterhalten kann wie mit modernen LLM-Assistenten,
und der nebenbei das Gerät bedient und im Alltag unterstützt.

- **Natürliche Stimme:** Piper TTS (warme deutsche Frauenstimme) statt System-TTS
- **Echte Konversation:** Hybrid – Kernbefehle lokal/offline, freie Gespräche über Cloud-LLM
  (Claude API) mit Lina-Persönlichkeit und Dialoggedächtnis
- **Briefe vorlesen:** Tablet-Kamera + Vision-Modell ("Halte den Brief vor das Tablet")
- **Zeitung erzählen:** RSS + LLM → "Erzähl mir, was heute wichtig ist" mit Rückfragen
- **Fernziel:** Smart-Glasses-Anbindung (Meta Ray-Ban hat aktuell KEINE offene API –
  beobachten, nicht darauf bauen)

## Phase 2 – Geplant (jetzt nicht implementieren)

- ~~STT-Migration zu Whisper (Sherpa-ONNX)~~ ✅ erledigt 2026-07-02
- ~~TTS-Upgrade auf Piper (weibliche deutsche Stimme)~~ ✅ erledigt 2026-07-02
- ~~LLM-Anbindung für freie Konversation (Claude API Hybrid, ADR-017)~~ ✅ erledigt 2026-07-16
- Onleihe-Integration (Bibliotheksausweis → Hörbücher)
- Podcast-Streaming (gPodder-Backend)
- Sprach-Einkauf: Wolt, Rewe Express, Picnic via AccessibilityService
- WhatsApp via AccessibilityService

---

## UI-Richtlinien

- Hintergrund: `#000000`
- Text: `#FFFFFF` oder `#FFD700`
- Keine Farbe als einziger Informationsträger (Farbenblindheit!)
- Schriftgröße: mindestens 24sp
- Touch-Targets: mindestens 72dp × 72dp
- Primäres Feedback immer akustisch – visuell ist sekundär
- Keine tiefen Menüs, kein Pflicht-Scrollen

---

## Berechtigungen (AndroidManifest)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.READ_CONTACTS"/>
<uses-permission android:name="android.permission.WRITE_CONTACTS"/> <!-- SIM-/vCard-Import, ADR-029 -->
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.READ_CALL_LOG"/>
<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS"/>
<uses-permission android:name="android.permission.READ_PHONE_STATE"/>
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.READ_SMS"/>
<uses-permission android:name="android.permission.RECEIVE_SMS"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO"/> <!-- Music/Audiobooks, ADR-025 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE"/>
```

> Quelle der Wahrheit ist `AndroidManifest.xml` – diese Liste hier ist ein
> Spiegel für den schnellen Überblick, bei Abweichung gilt das Manifest.

---

## Testnutzer-Netzwerk

- 2 Personen im engeren Umfeld des Entwicklers
- Blindentennis-Verein
- Olympiakader Blindensport

---

## Docs-Regeln für Claude Code

**Tests:** Reine JVM-Tests liegen in `app/src/test/` und laufen mit
`./gradlew testStandardDebugUnitTest testNgoDebugUnitTest` – seit den
Build-Flavors (ADR-034) gibt es kein flavor-loses `testDebugUnitTest` mehr,
die CI führt beide bei jedem PR aus. Parser und Intent-Erkennung gehören
dorthin: Sie entscheiden, was Lina tut, und die Muster-Reihenfolge im
`LocalCommandResolver` ist regressionsanfällig. Alles, was `Context` braucht,
bleibt vorerst ungetestet (kein Robolectric im Projekt). `./gradlew
assembleDebug` bleibt dagegen gültig (Aggregat-Task für beide Flavors).

**Nach JEDER abgeschlossenen Task** (alle drei liegen im Repo-Root, nicht in `docs/`):
1. `CHANGELOG.md` → Was wurde gebaut/geändert?
2. `TODO.md` → Task auf `[x]` setzen, nächste priorisieren
3. `DECISIONS.md` → Falls Architekturentscheidung getroffen: ADR anlegen

**Niemals:**
- Vosk direkt aufrufen – immer über `SttEngine`
- Android TTS direkt aufrufen – immer über `TtsEngine`
- ONNX-Modelle ins Git committen – immer per `scripts/download-models.sh` laden
- `DocumentBuilderFactory.newInstance()` direkt aufrufen – immer über
  `SecureXml.newDocumentBuilder()` (XXE-Härtung, ADR-036)
- `when`-Block als vollständigen Intent-Parser verwenden
- Feature bauen ohne TTS-Feedback von Lina
- `git clean -x`/`-xd`/`-xdf` im Repo-Root ausführen, ohne vorher
  `.git/info/exclude` zu prüfen (`cat .git/info/exclude`) – dort können
  unversionierte Verzeichnisse mit eigener Git-Historie eingetragen sein,
  die `-x` mitlöschen würde (anders als reine `.gitignore`-Einträge). Falls
  nötig, gezielt mit `-e <verzeichnis>` ausschließen oder ganz auf `-x`
  verzichten.

---

## .gitignore

```
local.properties
*.jks
*.gguf
*.onnx
app/src/main/assets/vosk-model*/
```
