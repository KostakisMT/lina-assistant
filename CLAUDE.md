# CLAUDE.md – Lina · VoiceFirst Assistant

> **Pflichtlektüre für jede Claude Code Instanz.**
> Lies diese Datei vollständig bevor du irgendeine Zeile Code schreibst.
> Nach jeder abgeschlossenen Task: docs/CHANGELOG.md und docs/TODO.md aktualisieren.
> Architekturentscheidungen immer in docs/DECISIONS.md festhalten.

---

## Projekt

**Name:** Lina – VoiceFirst Assistant
**Weckwort:** "Hey Lina"
**Art:** Open-Source Android-App (GitHub)
**Träger:** Gemeinnütziger Verein (Name vorerst nicht öffentlich, intern dokumentiert) – kein kommerzieller Vertrieb
**Lizenz:** Apache 2.0 (Code). Modelle können abweichende Lizenzen haben –
NC-Lizenzen (z.B. CC BY-NC-SA bei OVOS-Piper-Stimmen) sind durch die
Gemeinnützigkeit nutzbar, aber pro Modell in DECISIONS.md zu dokumentieren.
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
  – Lizenz CC BY-NC-SA 4.0, nutzbar da Träger gemeinnützig (ADR-016)
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
│   │   ├── ContactRepository.kt     # ContactsContract Wrapper
│   │   └── FuzzyContactMatcher.kt   # Phonetisches Matching
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
│   │   ├── AudiobookPlayer.kt       # ExoPlayer Wrapper
│   │   └── PlaybackStateStore.kt    # Fortschritt persistent
│   └── onboarding/
│       ├── PermissionsGuide.kt
│       └── BatteryWhitelistGuide.kt
└── ui/
    ├── launcher/
    │   └── LauncherActivity.kt
    └── components/
        └── LinaTheme.kt             # Hochkontrast-Theme
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

---

## Nächster Schritt

- LLM-Anbindung (Claude API) für freie Konversation – siehe Vision
- STT-Robustheit bei Raumdistanz verbessern (Whisper-Verhörer bei Befehlen)

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
- LLM-Anbindung für freie Konversation (siehe Vision – empfohlen: Claude API Hybrid)
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
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.READ_CALL_LOG"/>
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.READ_SMS"/>
<uses-permission android:name="android.permission.RECEIVE_SMS"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE"/>
```

---

## Testnutzer-Netzwerk

- 2 Personen im engeren Umfeld des Entwicklers
- Blindentennis-Verein
- Olympiakader Blindensport

---

## Docs-Regeln für Claude Code

**Nach JEDER abgeschlossenen Task:**
1. `docs/CHANGELOG.md` → Was wurde gebaut/geändert?
2. `docs/TODO.md` → Task auf `[x]` setzen, nächste priorisieren
3. `docs/DECISIONS.md` → Falls Architekturentscheidung getroffen: ADR anlegen

**Niemals:**
- Vosk direkt aufrufen – immer über `SttEngine`
- Android TTS direkt aufrufen – immer über `TtsEngine`
- ONNX-Modelle ins Git committen – immer per `scripts/download-models.sh` laden
- `when`-Block als vollständigen Intent-Parser verwenden
- Feature bauen ohne TTS-Feedback von Lina

---

## .gitignore

```
local.properties
*.jks
*.gguf
*.onnx
app/src/main/assets/vosk-model*/
```
