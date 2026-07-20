<!--
Danke für deinen Beitrag zu Lina!
English is welcome – feel free to fill this in in English.
-->

## Was ändert sich?

<!-- Kurz und konkret. Was kann Lina danach, was vorher nicht ging? -->

## Warum?

<!-- Welches Problem löst das? Bei Feedback aus dem Feldtest: gerne zitieren. -->

## Wie getestet?

<!--
Am wertvollsten ist ein Test auf einem echten Gerät – Lina wird gesprochen
bedient, vieles zeigt sich erst dort (Timing, Mikrofon-Konflikte, Verhörer).
Welche Sprachbefehle hast du ausprobiert, was kam zurück?
-->

---

## Checkliste

- [ ] **Gesprochene Rückmeldung**: Jede neue Aktion bestätigt Lina hörbar – kein Feature ohne TTS-Feedback
- [ ] **Ohne Sehen bedienbar**: Der Ablauf funktioniert allein über Sprache und Ton, ohne auf den Bildschirm zu schauen
- [ ] **Interfaces beachtet**: STT/TTS/Wake-Word nur über `SttEngine`/`TtsEngine`/`WakeWordEngine` genutzt, nicht direkt
- [ ] **Offline geprüft**: Funktioniert das Feature ohne Internet – und wenn nicht, sagt Lina das verständlich?
- [ ] **`CHANGELOG.md`** ergänzt (Was / Warum / Dateien / Offen)
- [ ] **`DECISIONS.md`**: Bei einer Architekturentscheidung ein ADR angelegt
- [ ] **Keine personenbezogenen Daten**: keine echten Namen, Adressen, Kontakte, Sprachaufnahmen, Fotos oder API-Keys – auch nicht in Commit-Messages, Logs oder Testdaten
- [ ] **Build läuft**: `./gradlew assembleDebug`

<!--
Zu den letzten beiden Punkten:
- Lina läuft bei echten Menschen im Wohnzimmer. Testdaten bitte anonymisieren.
- Modelle und Aufnahmen gehören nicht ins Repo (siehe .gitignore).
-->
