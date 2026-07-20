# WARTUNG.md – Fernzugriff & Betrieb des Test-Tablets

> Für Betreuer:innen eines ausgelieferten Lina-Tablets. Das Tablet steht
> stationär mit Dauerstrom im WLAN der Nutzer:in.

## ⚠️ Einwilligung zuerst

Vor der Übergabe muss die Nutzer:in **informiert zustimmen**:

1. **Fernwartung:** Betreuer:innen können übers Internet auf das Tablet
   zugreifen (Updates einspielen, Logs und Einrichtungs-Aufnahmen abholen,
   Bildschirm sehen). Zugriff nur zu Wartungszwecken, nie heimlich mithören.
2. **Cloud-Konversation:** Freie Fragen an Lina werden an einen Internetdienst
   (Claude API) geschickt. Kernbefehle (Anrufe, SMS, Hörbücher) bleiben auf dem
   Gerät. Lina kündigt keine Cloud-Nutzung einzeln an – das muss vorab klar sein.
3. **Aufnahmen:** Bei der Ersteinrichtung entstehen Sprachaufnahmen (Weckwort,
   Befehle) für das Training der Erkennung. Sie werden nur dafür verwendet.
4. **Dokument-Vorlesen:** Sagt die Nutzer:in „lies mir die Post vor", macht Lina
   ein Foto des Dokuments im Rahmen und schickt es zur Auswertung an denselben
   Internetdienst. Das betrifft auch private Post. Das Bild wird **nicht
   gespeichert** – es liegt nur während des Vorlesens im Arbeitsspeicher.
   (Ausnahme: der Wartungsbefehl „testfoto" legt bewusst eine Datei ab, damit
   der Rahmen einmalig ausgerichtet werden kann – danach löschen.)

Am besten kurz und einfach erklären und die Zustimmung schriftlich oder als
Sprachnotiz festhalten.

## Einrichtung des Fernzugriffs (einmalig, vor Übergabe)

1. **Tailscale** auf Tablet und Betreuer-Rechner installieren, beide im selben
   Tailnet anmelden (kostenloses Konto reicht). Das Tablet bekommt eine stabile
   `100.x.y.z`-IP – erreichbar aus jedem Netz, keine Portfreigaben nötig.
2. Auf dem Tablet **WLAN-Debugging** aktivieren: Entwickleroptionen freischalten
   (7× auf Build-Nummer tippen) → „Debugging über WLAN". Einmalig per USB koppeln:
   `adb tcpip 5555` (bleibt bis zum Reboot; nach Reboot per Tailscale-adb-pairing
   oder kurzem USB-Anschluss vor Ort erneuerbar – für den stationären Betrieb
   praktisch: Tablet rebootet selten, hängt am Strom).
3. In Tailscale für das Tablet **„Disable key expiry"** setzen, sonst fällt der
   Zugang nach Ablauf des Schlüssels aus.
4. Auf dem Betreuer-Rechner: `export LINA_TABLET_IP=100.x.y.z` (z.B. in ~/.zshrc).

## Täglicher Umgang

```bash
./scripts/remote.sh connect          # verbinden
./scripts/remote.sh status           # Akku, Uptime, läuft der Wake-Service?
./scripts/remote.sh logs             # Live-Logs (STT, Claude, Onboarding)
./scripts/remote.sh deploy           # neue Version bauen + remote installieren
./scripts/remote.sh pull-onboarding  # Einrichtungs-Aufnahmen + Antworten holen
./scripts/remote.sh screen           # Bildschirm spiegeln (scrcpy)
```

„Änderungen automatisch laden" = `deploy`: baut die aktuelle lokale Version und
installiert sie übers Netz; Lina startet danach neu. Kein App-Store, keine
Wartezeit.

## Ersteinrichtung (macht die Nutzer:in selbst, komplett gesprochen)

Beim allerersten Start (nachdem Berechtigungen erteilt sind und die
Spracherkennung geladen ist) startet Lina automatisch die Einrichtung:

1. **5× „Hey Lina"** nach Signalton einsprechen → WAVs für Weckwort-Nachtraining
2. **5 Kernbefehle** nachsprechen → Referenzaufnahmen für STT-Tests
3. **5 Fragen** beantworten (Anrede, Nachrichten-Interessen, Bücher, wichtigste
   Person, Wünsche) → `answers.json`; Anrede + Interessen personalisieren die
   Claude-Persona automatisch

Dateien: `Android/data/dev.lina/files/onboarding/session_<zeit>/` → per
`pull-onboarding` abholen. Danach Weckwort-Modell nachtrainieren
(`training/README.md`) und per `deploy` zurückspielen.

Debug-Befehle (Texteingabe oder `remote.sh say`):
- `einrichtung` – Einrichtung manuell starten
- `einrichtung zurücksetzen` – beim nächsten Start läuft sie wieder automatisch

## Checkliste Übergabetag

- [ ] Einwilligung besprochen und festgehalten (s.o.)
- [ ] WLAN der Nutzer:in eingetragen, Tablet am Strom, Ständer
- [ ] Tailscale online (`remote.sh status` von unterwegs testen!)
- [ ] `CLAUDE_API_KEY` in der installierten APK enthalten (freie Konversation testen)
- [ ] Battery-Whitelist + Accessibility-Service gesetzt (App führt hin)
- [ ] Echte Kontakte eingetragen
- [ ] `einrichtung zurücksetzen` ausgeführt, damit die Einrichtung beim
      ersten „richtigen" Start frisch beginnt
- [ ] Testanruf + „Was gibt es Neues?" + freie Frage vor Ort durchspielen
