# Mitmachen bei Lina

Danke für dein Interesse! Lina ist ein gemeinnützig getragenes,
nicht-kommerzielles Projekt für blinde und sehbehinderte Menschen.

## Einstieg

1. [ONBOARDING.md](ONBOARDING.md) lesen (Projektüberblick, Docs-Struktur)
2. [CLAUDE.md](CLAUDE.md) lesen (Architektur, Leitprinzipien, Modulstruktur)
3. Offene Aufgaben stehen in [TODO.md](TODO.md)

## Grundregeln

- **Voice-First:** Kein Feature ohne akustisches Feedback (TTS)
- **Barrierefrei by default:** Hochkontrast, große Touch-Targets, keine Farbe als einziger Informationsträger
- **Offline where possible:** Was lokal laufen kann, läuft lokal
- **Interfaces respektieren:** STT/TTS/WakeWord/Intent nie direkt aufrufen, immer über die Interfaces
- **Doku-Pflicht:** Jede Änderung in `CHANGELOG.md`, Architekturentscheidungen als ADR in `DECISIONS.md`
- **Keine personenbezogenen Daten committen** – Nutzerprofile, echte Kontakte und Sprachaufnahmen bleiben lokal (gitignored)

## Pull Requests

- Kleine, fokussierte PRs mit klarer Beschreibung
- `./gradlew assembleDebug` muss durchlaufen – bei jedem PR baut das auch
  automatisch (GitHub Actions, siehe `.github/workflows/build.yml`)
- Beim Öffnen eines PRs erscheint eine Checkliste – sie spiegelt die
  Leitprinzipien (gesprochene Rückmeldung, ohne Sehen bedienbar, keine
  personenbezogenen Daten)
- Bei UI-Änderungen: gegen die UI-Richtlinien in CLAUDE.md prüfen

### Nur kompilieren, ohne 400 MB Modelle

Zum Bauen genügt die sherpa-onnx-AAR; die Sprachmodelle braucht erst die
laufende App:

```bash
./scripts/download-models.sh --libs-only
./gradlew assembleDebug
```

## Fragen

Issue aufmachen – wir freuen uns über Feedback, besonders aus der Community
blinder und sehbehinderter Nutzer:innen.
