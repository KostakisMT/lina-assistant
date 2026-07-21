# PROXY-SPEC.md – Lina-Proxy

> Spezifikationsentwurf zu [ADR-020](DECISIONS.md). Der Proxy existiert noch
> nicht; dieses Dokument beschreibt, was er können muss, bevor Code entsteht.
> Status: Entwurf, 2026-07-21.

## Zweck

Lina spricht heute direkt mit der Claude API, mit einem Schlüssel, der in die
App kompiliert wird. Für ein einzelnes Testgerät geht das; für Verteilung
nicht – ein Schlüssel in einer verteilten APK ist offen, sobald jemand sie
auseinandernimmt.

Der Proxy schiebt sich dazwischen. Er hält den Anthropic-Schlüssel, prüft
Gerätetoken, zählt Verbrauch und reicht Anfragen weiter.

**Nicht Aufgabe des Proxys:** Inhalte speichern, Gespräche auswerten,
Nutzerprofile führen. Er ist eine Durchreiche mit Zugangsprüfung und
Zählwerk, nichts weiter.

## Was er lösen muss

| Problem | Antwort |
|---|---|
| Schlüssel in verteilter App | Schlüssel bleibt serverseitig |
| Verlorenes Tablet | Einzelnes Gerätetoken sperren statt Org-Schlüssel rotieren |
| Unbekannte Kosten | Verbrauch pro Gerät zählen (Grundlage für ADR-021) |
| Blinder Nutzer kann keinen Schlüssel eingeben | Kopplung per gesprochenem Code |
| Missbrauch / Kostenexplosion | Kontingente und Rate-Limits pro Gerät |

---

## Datenmodell

```
Gerät
  id                  intern
  token_hash          nur der Hash, nie das Token selbst
  konto_id            welchem Konto zugeordnet (nullable bis zur Kopplung)
  bezeichnung         "Wohnzimmer Tablet" – vom Helfer vergeben
  status              aktiv | gesperrt
  erstellt_am, zuletzt_gesehen_am

Konto
  id
  inhaber             Vertrauensperson oder Nutzer:in
  psp_kunden_id       nur wenn Weitergabe greift (ADR-021), sonst leer

Verbrauch  (eine Zeile je Anfrage)
  geraet_id, zeitpunkt
  eingabe_tokens, ausgabe_tokens, cache_lese_tokens
  websuchen           Anzahl – wird getrennt abgerechnet, nicht über Tokens
  modell              welches Modell tatsächlich lief (ADR-022)
```

Der Verbrauch enthält **keine Inhalte** – keine Prompts, keine Antworten,
keine Bilder. Nur Zähler.

---

## Endpunkte

### Kopplung

```
POST /pair/start                      (vom Tablet, ohne Auth – siehe Sicherheit)
  → { code: "B7A3K2", geheimnis: "...", gueltig_bis: "..." }
```

Erzeugt einen Kopplungsvorgang. `code` wird von Lina vorgelesen
(`GermanSpelling.spellExplicit`), `geheimnis` bleibt auf dem Gerät und wird
nie ausgesprochen.

```
POST /pair/claim                      (von der Webseite, Helfer angemeldet)
  { code }
  → { geraet_bezeichnung_vorschlag, angefragt_vor_sekunden }
  danach Bestätigung → Gerät wird dem Konto zugeordnet
```

```
POST /pair/poll                       (vom Tablet)
  { code, geheimnis }
  → 202 solange offen | 200 { token } sobald zugeordnet
```

**Warum das Geheimnis:** Ohne es könnte jeder, der den gesprochenen Code
mithört, das fertige Token abholen. Das Token wird nur an den heraus­gegeben,
der auch `geheimnis` vorweist – also an genau das Gerät, das den Vorgang
gestartet hat.

**Anforderungen an den Code:**

- 6 Zeichen aus `GermanSpelling.CODE_ALPHABET` (29 Zeichen, ≈ 594 Mio. Kombinationen).
  Verwechselbare Zeichen sind dort bereits ausgeschlossen.
- Gültig 10 Minuten, danach verfällt er.
- Einmalig – nach erfolgreicher Zuordnung sofort entwertet.
- Höchstens 5 Fehlversuche je Code, dazu ein globales Limit auf `/pair/claim`.
- Dem Helfer wird vor der Bestätigung angezeigt, **wie lange die Anfrage her
  ist**. Ein untergeschobener fremder Code fällt so auf.

### Weiterleitung

```
POST /v1/messages                     (vom Tablet, Bearer-Token)
  Body wie Anthropic Messages API
  → Antwort durchgereicht
  → 429 wenn Kontingent erschöpft, mit sprechendem Grund
```

Der Proxy ergänzt den Anthropic-Schlüssel, wählt gemäß ADR-022 das Modell und
schreibt anschließend eine Verbrauchszeile.

```
GET /v1/kontingent                    (vom Tablet)
  → { verbraucht, grenze, zurueckgesetzt_am }
```

Damit kann Lina auf Nachfrage Auskunft geben, ohne dass jemand eine Webseite
öffnen muss.

### Verwaltung

```
POST /geraete/{id}/sperren            (nur Konto, dem das Gerät gehört)
```

Für verlorene oder ausgemusterte Tablets. Die Zuständigkeit muss serverseitig
aus dem Token abgeleitet werden, nicht aus der übergebenen `id` – sonst sperrt
jeder angemeldete Nutzer fremde Geräte, indem er `id` hochzählt.

---

## Sicherheitsanforderungen

Diese Spezifikation ist bewusst öffentlich. Die Sicherheit darf nicht daran
hängen, dass jemand die Endpunkte nicht kennt – sie stehen später ohnehin in
der APK und im Netzwerkverkehr. Was sie stattdessen tragen muss:

**Transport.** Ausschließlich TLS. Der Proxy lehnt Klartext ab, statt
umzuleiten – eine Weiterleitung würde das Token beim ersten Versuch schon
preisgeben.

**`/pair/start` ist unauthentifiziert und braucht deshalb harte Grenzen.**
Ohne sie kann jeder beliebig viele offene Kopplungsvorgänge erzeugen. Das ist
nicht nur ein Ressourcenproblem: Je mehr Codes gleichzeitig gültig sind, desto
wahrscheinlicher trifft ein blind geratener Code irgendeinen davon. Deshalb
Limit pro Quell-IP **und** eine absolute Obergrenze gleichzeitig offener
Vorgänge.

**Raten von Codes.** 6 Zeichen aus 29 ergeben ≈ 594 Mio. Kombinationen. Ein
gezielter Angriff auf *einen* Code ist damit aussichtslos. Der realistische
Angriff ist das Raten gegen *alle* offenen Codes gleichzeitig – dagegen hilft
nur ein **globales** Limit auf `/pair/claim`, nicht nur eines pro Code. Beides
ist Pflicht, nicht Kür. Dasselbe gilt für `/pair/poll`, wo sonst das
Geheimnis durchprobiert werden könnte.

**Gerätetoken.** Nur als Hash gespeichert. Endliche Lebensdauer mit
Erneuerung im Betrieb – ein unbegrenzt gültiges Token auf einem Gerät, das
jahrelang im Wohnzimmer steht, ist auf Dauer nicht vertretbar. Erneuerung darf
**keine** neue Kopplung erfordern, sonst steht der blinde Nutzer ohne Helfer da.

**Zuordnung fremder Geräte.** Wer einen mitgehörten Code bei sich einlöst,
bekommt ein fremdes Gerät in sein Konto – und zahlt dessen Verbrauch. Der
umgekehrte, schädlichere Fall (das eigene Gerät in ein fremdes Konto schieben,
um auf fremde Kosten zu nutzen) setzt Social Engineering voraus. Gegenmittel
in beide Richtungen: dem Helfer vor der Bestätigung anzeigen, wie alt die
Anfrage ist, und dem Konto-Inhaber jede neue Gerätezuordnung melden.

**Fehlermeldungen** verraten nicht, ob ein Code existiert, abgelaufen oder
bereits eingelöst ist – sonst wird `/pair/claim` zum Orakel.

**Was hier nicht hineingehört:** Hostnamen, konkrete Infrastruktur,
tatsächlich eingestellte Limitwerte nach dem Tuning, Zugangsdaten und
Kontaktwege für Störfälle. Das Protokoll ist öffentlich, der Betrieb nicht.

---

## Kontingente und Missbrauchsschutz

Nach ADR-021 hat jedes Gerät ein monatliches Freikontingent. **Die Höhe steht
bewusst noch nicht fest** – sie wird aus den Verbrauchsdaten des Feldtests
abgeleitet, nicht geschätzt.

Unabhängig davon braucht es harte Grenzen gegen Fehlfunktionen:

- Obergrenze Anfragen pro Minute je Gerät – fängt eine Endlosschleife in der
  App ab, bevor sie ein Monatsbudget verbrennt.
- Getrenntes, knapperes Limit für Websuchen (pro Suche abgerechnet, siehe ADR-022).
- Getrenntes Limit für Vision-Aufrufe (Dokument-Vorlesen, deutlich teurer je Aufruf).
- Warnschwelle: Bei 80 % des Kontingents eine Benachrichtigung an den
  Konto-Inhaber – **nicht** an den blinden Nutzer, der daran nichts ändern kann.

Beim Überschreiten antwortet der Proxy so, dass Lina einen brauchbaren Satz
vorlesen kann („Ich kann gerade nicht ins Internet, die Kernfunktionen gehen
weiter") – niemals mit einer nackten Fehlernummer.

---

## Datenschutz

- Inhalte werden **nicht** gespeichert. Kein Prompt-Log, keine Antworten,
  keine Dokumentfotos.
- Gespeichert werden Zähler, Zeitstempel und Gerätezuordnung.
- Zugriffslogs ohne Inhalte, kurze Aufbewahrung.
- Der Verein wird DSGVO-Verantwortlicher, Anthropic Auftragsverarbeiter – der
  AVV ist Voraussetzung für den Betrieb.
- Die Einwilligung (WARTUNG.md, Punkt 5) benennt den Zwischenschritt bereits.

---

## Betrieb

- Fällt der Proxy aus, entfallen freie Konversation und Dokument-Vorlesen.
  Alle Offline-Kernbefehle laufen weiter (ADR-017). Lina muss das sagen können,
  ohne zu behaupten, sie sei kaputt.
- Der Proxy wird betriebsnotwendige Vereinsinfrastruktur: Erreichbarkeit,
  Updates und Schlüsselrotation sind eine Dauerverpflichtung, keine
  einmalige Einrichtung.
- Schlüsselrotation muss möglich sein, ohne dass Geräte neu gekoppelt werden.

---

## Offene Fragen

1. **Streaming.** `ClaudeConversation` arbeitet heute blockierend. Bei einer
   Sprachassistentin ist Zeit bis zum ersten Wort das, was Nutzer als
   Geschwindigkeit erleben. Ob der Proxy SSE durchreicht, sollte entschieden
   werden, bevor die Schnittstelle festliegt.
2. **Wo läuft er?** Anforderung ist gering (Durchreiche), aber der Standort
   ist datenschutzrelevant – EU-Hosting ist vorzuziehen.
3. **Modellauswahl: Proxy oder App?** ADR-022 verlangt Routing. Im Proxy ist
   es zentral änderbar ohne App-Update; in der App kennt man den Kontext
   besser. Tendenz: Proxy, damit Kostensteuerung ohne Rollout möglich ist.
4. **Mehrere Geräte je Konto** – für Haushalte mit zwei Tablets oder für den
   Verein, der Testgeräte verwaltet. Das Datenmodell lässt es zu; die
   Kontingentlogik muss es abbilden.
5. **Offline-Kopplung.** Wenn beim Feldtest kein Helfer erreichbar ist: Darf
   der Verein ein Gerät vorab koppeln und fertig ausliefern? Vermutlich ja –
   dann ist der Pairing-Flow der Zweitweg, nicht der Hauptweg.
