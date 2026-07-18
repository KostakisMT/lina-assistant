# ONBOARDING.md – Für neue Devs und Claude Code Instanzen

> Lies dieses Dokument zuerst. Dann CLAUDE.md. Dann TODO.md.
> Danach kannst du sofort produktiv mitarbeiten.

---

## Was ist Lina?

Lina ist eine Open-Source Android KI-Assistentin für blinde und sehbehinderte Menschen.
Gesteuert per Sprache. Weckwort: "Hey Lina".
Kein Screen-Reader-Patch – von Grund auf Voice-First gebaut.

**Primärer Nutzer:** Ein konkreter Mensch mit fortschreitender Erblindung, Lenovo Idea Tab (TB336ZU), Wohnzimmer, Deutsch. Er kannte Siri – aber sie war zu limitiert. Lina soll besser sein.

---

## Docs auf einen Blick

| Datei | Inhalt | Wann lesen |
|---|---|---|
| `CLAUDE.md` (Root) | Vollständiger Projektkontext, Tech Stack, Architektur | Immer zuerst |
| `docs/TODO.md` | Taskboard | Vor jeder Task |
| `docs/CHANGELOG.md` | Was wurde wann gebaut | Bei Fragen zu Code |
| `DECISIONS.md` | 13 ADRs – Architekturentscheidungen | Bei Designfragen |
| `NUTZERPROFIL.md` | Detailliertes Nutzerprofil (**lokal, gitignored** – enthält persönliche Daten) | Bei Feature-Fragen |
| `ONBOARDING.md` | Diese Datei | Einmal beim Start |

---

## Kollaborations-Regeln

Mehrere Personen und Claude Code Instanzen arbeiten gleichzeitig.

1. **Vor dem Start:** Task in `TODO.md` mit `[~] @name` claimen + committen
2. **Nach Abschluss:** `[x]` setzen + `CHANGELOG.md` Eintrag anlegen
3. **Bei Architekturentscheidungen:** `DECISIONS.md` prüfen, ggf. neuen ADR anlegen
4. **Niemals:** ADR-001 bis ADR-013 ohne neuen ADR rückgängig machen

---

## Lokales Setup

```bash
# 1. Repo klonen
git clone https://github.com/KostakisMT/lina-assistant.git
cd lina-assistant

# 2. Vosk-Modell laden
# https://alphacephei.com/vosk/models → vosk-model-small-de
# Entpacken nach: app/src/main/assets/vosk-model-small-de/

# 3. OpenWakeWord-Modelle laden
./scripts/download-models.sh

# 4. Build (JAVA_HOME muss auf JDK 17 zeigen)
./gradlew assembleDebug

# 5. In Android Studio öffnen oder Claude Code starten
claude
```

---

## Die wichtigsten Interface-Kontrakte

```kotlin
// STT – IMMER so, nie direkt Vosk aufrufen
interface SttEngine {
    fun startListening(onResult: (String) -> Unit)
    fun stopListening()
    fun destroy()
}

// TTS – IMMER so, nie direkt Android TTS aufrufen
interface TtsEngine {
    fun speak(text: String, priority: TtsPriority = TtsPriority.NORMAL)
    fun stop()
    fun setRate(rate: Float)
    fun shutdown()
}

// Intent
interface IntentResolver {
    fun resolve(input: String): ResolvedIntent?
}
```

---

## Was Lina niemals tun darf

- Kein Feature ohne TTS-Bestätigung ("Ich rufe Boris an")
- Keine Farbe als einziger Informationsträger
- Kein automatisches Nachrichten-Briefing (nur auf Abruf)
- Kein Hardcoden von API-Keys
- Keinen `when`-Block als vollständigen Intent-Parser verwenden

---

## Design (niemals brechen)

```
Hintergrund: #000000
Text:        #FFFFFF oder #FFD700
Schrift:     min. 24sp
Touch:       min. 72dp × 72dp
Feedback:    akustisch zuerst – visuell ist sekundär
```

---

## Fragen?

Erst `DECISIONS.md` – steht wahrscheinlich schon drin.
Sonst neuen ADR anlegen und entscheiden.
