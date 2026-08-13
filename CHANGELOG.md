# CHANGELOG.md – Lina · VoiceFirst Assistant

> Nach JEDER abgeschlossenen Task hier eintragen.
> Format: Datum | Was | Warum | Dateien | Offen

---

## [2026-08-13] Interne Konkurrenzanalyse, Ergebnis in Präsentation eingeflossen

**Was:** Lokale Konkurrenzanalyse durchgeführt (nicht im Repo, siehe
`.gitignore`). Kernaussage daraus in das Artifact „Lina – Präsentation für
Vereine und Förderer" übernommen: neue Folie „Die Lücke, die Lina schließt"
sowie eine ergänzte Zeile „Kosten für Nutzer:innen" in der Vergleichstabelle.

**Warum:** Positionierung für Förderer schärfen, vor dem nächsten
Förderantrags-Anlauf (`foerderantrag/`).

**Dateien:** `Lina Praesentation.html` (lokale Quelle, deckungsgleich mit
dem veröffentlichten Artifact).

---

## [2026-08-12] Phase D: Gradle-Build-Flavor-Grundgerüst standard/ngo (ADR-034)

**Was:** Neue Gradle-Flavor-Dimension `distribution` mit `standard`
(bisheriges Verhalten, `dev.lina`, Claude API aus `local.properties`) und
`ngo` (`dev.lina.ngo`, `CLAUDE_API_KEY` hart auf `""`, Anthropic-SDK nur
noch `standardImplementation`). `ClaudeConversation.kt` – die einzige Datei
mit `com.anthropic.*`-Imports – von `src/main/kotlin/` nach
`src/standard/kotlin/` verschoben. Neuer `ConversationEngineProvider`
(identische Funktionssignatur, pro Flavor eigener Rumpf in `src/standard/`
bzw. `src/ngo/`) ist jetzt der einzige Ort, an dem `LauncherActivity`
(geteilter Code) die Ebene-2-Engine erzeugt – kein direkter
`ClaudeConversation`-Zugriff mehr außerhalb des `standard`-Flavors möglich.
Reine Scaffolding: kein `GemmaConversation`, kein lokaler LLM-Code in dieser
Phase.

**Warum:** Setzt ADR-032 (Zwei-Flavor-Architektur für NGO-Partner, die eine
Anthropic-Anbindung grundsätzlich ablehnen) konkret in Gradle um. Der
bisherige `claude == null`-Pfad (leerer API-Key) reichte für "kein
Cloud-Zugriff" schon aus, aber nicht dafür, den Anthropic-Code komplett aus
dem NGO-Build fernzuhalten – ein Audit der NGO-APK sollte keine
Anthropic-Referenzen mehr finden.

**Dateien:** `app/build.gradle.kts` (Flavor-Dimension, verschobener
`buildConfigField`, `standardImplementation` für das Anthropic-SDK),
`core/llm/ClaudeConversation.kt` (verschoben nach `src/standard/`),
`core/llm/ConversationEngineProvider.kt` (neu, je einmal in `src/standard/`
und `src/ngo/`), `ui/launcher/LauncherActivity.kt` (nutzt den Provider statt
`ClaudeConversation` direkt), `.github/workflows/build.yml` (Task-Namen
`testDebugUnitTest`→`testStandardDebugUnitTest`+`testNgoDebugUnitTest`,
`lintDebug`→`lintStandardDebug`+`lintNgoDebug`, Report-Pfade angepasst),
`CLAUDE.md` (Testbefehl, Modulstruktur-Hinweis), `DECISIONS.md` (ADR-034),
`TODO.md`.

**Verifiziert:** `./gradlew testStandardDebugUnitTest testNgoDebugUnitTest`
und `./gradlew assembleDebug` (baut beide Varianten) grün.
`unzip`+dex-Grep bestätigt: `app-standard-debug.apk` enthält
`com/anthropic`-Referenzen (in 5 dex-Dateien, zwei davon ~10MB nur dafür),
`app-ngo-debug.apk` enthält keine einzige.

**Offen:** Websuche/Nachrichten (ADR-024) und Dokument-Vision (ADR-018) haben
im `ngo`-Flavor weiterhin keine Entsprechung – bewusst nicht in dieser Phase
gelöst (siehe TODO.md, war schon vorher als offene Frage in ADR-032
vermerkt). `GemmaConversation` selbst ist eine spätere Phase, abhängig vom
Stand des Llama-3.2-3B-Finetunings.

---

## [2026-08-12] Feature: Helfer-Anruf per Be My Eyes (ADR-033)

**Was:** Neuer Sprachbefehl ("ruf einen Helfer an", "Be My Eyes", "hilfe
beim sehen" u.ä.) öffnet die Be-My-Eyes-App, damit die Nutzer:in dort per
Videoanruf einen sehenden Freiwilligen erreicht. Vorher recherchiert: Be My
Eyes hat keine offene API, um einen Anruf ins Freiwilligennetzwerk
auszulösen (nur das umgekehrte "Specialized Help"-Partnerprogramm für
Unternehmen) – umgesetzt wurde deshalb ein reiner App-Handoff
(`PackageManager.getLaunchIntentForPackage`), der letzte Tap auf "Call a
Volunteer" bleibt bei der Nutzer:in. Fehlt die App, öffnet Lina stattdessen
die Play-Store-Seite statt selbst zu installieren.

**Warum:** Sinnvolle Ergänzung zum Dokument-Vorlesen (ADR-018) für alles,
was ein einzelnes Foto nicht abdeckt (Objekte, Umgebung, Rückfragen in
Echtzeit) – auf Nutzerwunsch vor Phase D (Gradle-Build-Flavor) vorgezogen.

**Dateien:** `feature/helper/HelperCallLauncher.kt` (neu),
`core/intent/ResolvedIntent.kt` (+`CallHelper`),
`core/intent/LocalCommandResolver.kt` (+`resolveHelperCall()`, steht vor
`resolveCall`), `ui/launcher/LauncherActivity.kt` (Verdrahtung),
`AndroidManifest.xml` (`<queries>` für Paketsichtbarkeit ab Android 11),
`test/kotlin/dev/lina/core/intent/LocalCommandResolverTest.kt` (neue Tests),
`DECISIONS.md` (ADR-033), `TODO.md`, `CLAUDE.md`, `WARTUNG.md`, `IDEEN.md`.

**Offen:** Am echten Gerät noch nicht getestet (weder der Erfolgs- noch der
"App fehlt"-Pfad). Stufe 2 (Deep-Link ohne Tap, über dieselbe Route wie
Google Assistants App Action) bleibt ein mögliches Upgrade, sobald jemand
die BME-APK auf ihre `shortcuts.xml` hin inspiziert hat.

---

## [2026-08-04] Neuer Bestwert 94,4% (Llama-3.2-3B auf 361-Beispiele-Datensatz); Datensatz-Skalierung an ihrer Grenze

**Was:** Neu trainiert auf dem durch lokale Generierung gewachsenen
Datensatz, diesmal mit `--seed 0` und `--save-every 20` von Anfang an: Val
Loss durchgehend stabil (3,48 → 0,175 bei Iter 20, danach flach bis Iter
300), Checkpoint-Sweep zeigt ein Plateau von 18/20 ueber fast den gesamten
Lauf. Volle Auswertung (Iter 200, 54er-Testsatz): **51/54 (94,4 %) korrekt**
– neuer Bestwert, loest den vorherigen (46/52, 88,5 %) ab, diesmal ueber das
gesamte Training hinweg reproduzierbar statt nur an einem Iterationspunkt.
Dabei zwei falsch beschriftete Trainingsbeispiele gefunden (Begruessungen
faelschlich als `sms_vorlesen()`/`stopp()` gelabelt) und entfernt (363 → 361).

Auf Nutzerwunsch einen automatisierten Input/Output-Konsistenz-Check
ergaenzt. Erster Ansatz (den Router-Prompt selbst zur unabhaengigen
Klassifikation befragen) live getestet und wieder verworfen: Zirkelschluss,
das dabei genutzte unfinetunte Basismodell ist beim Router-Task selbst
schwach (11/20 zero-shot) und lehnte deshalb massenhaft gute Beispiele ab
(Ertragsrate 94% → 12%). Ersetzt durch `is_generic_smalltalk()` – ein
billiger Regex-Filter ohne zusaetzlichen Modellaufruf, der gezielt den
beobachteten Fehlerfall trifft.

Ein weiterer `--append`-Lauf gegen den gewachsenen Datensatz ergab **0 neue
Beispiele in jeder einzelnen Kategorie** – die endgueltige Bestaetigung,
dass lokale Generierung diesen Datensatz aktuell nicht weiter vergroessern
kann, unabhaengig von Versuchslimit oder Prompt.

**Warum:** Fortsetzung des Router-Finetunings; Nutzerwunsch, den
Konsistenz-Check zu ergaenzen, nachdem die zwei Fehlbeschriftungen gefunden
wurden.

**Dateien:** `training/llm/gen_dialogue.py` (Konsistenz-Check-Versuch +
Ersatz), `training/llm/README.md`, `TODO.md`, `data/raw/dialogue_raw.json`
(363 → 361, zwei Beispiele entfernt, gitignored).

**Verifiziert:** `eval.py` gegen den vollen 54er-Testsatz (51/54).
`sweep_checkpoints.py` gegen alle 15 gespeicherten Zwischenstaende.

**Offen:** Datensatz-Skalierung braucht jetzt Claude (`--backend claude`,
sobald wieder Guthaben vorhanden) oder manuell geschriebene Beispiele –
lokale Generierung ist an dieser Stelle ausgereizt.

---

## [2026-08-04] `gen_dialogue.py` auf lokale Datengenerierung umgestellt (kein Anthropic-Guthaben mehr nötig)

**Was:** `training/llm/gen_dialogue.py` generiert Trainingsdaten jetzt
standardmäßig lokal über Llama-3.2-3B (`--backend local`, kein API-Key
nötig) statt über die Claude API. `--backend claude` bleibt als Option für
später. Dabei drei echte Bugs gefunden und behoben: (1) die
Sammel-Schleife hatte kein Terminierungs-Limit – bei einem Modell, das
durchgängig ungültig antwortet, lief das Skript endlos ohne sichtbare
Ausgabe (stdout-Blockpufferung beim Umleiten in eine Datei ließ es wie
einen Hänger aussehen); jetzt bricht `collect()` garantiert nach
`n * MAX_ATTEMPTS_PER_EXAMPLE` Versuchen ab. (2) Mehrere Beispiele als
JSON-Liste pro Antwort war für ein 3B-Modell zu fehleranfällig (unescapte
Anführungszeichen, abgeschnittene Arrays) – umgestellt auf ein Beispiel pro
Antwort im einfachen `EINGABE:`/`AUSGABE:`-Zeilenformat. (3) Das Modell
kopierte Platzhaltertext aus der Werkzeug-Signatur-Beschreibung wörtlich in
generierte Beispiele – Signaturen umformuliert (keine `key="Platzhalter"`-
Optik mehr), konkretes Vollbeispiel mit passender Argumentform ergänzt.
Validierung verschärft (`re.fullmatch` statt `findall`, damit ein
unvalidierter Rest wie ein nackter Positionsparameter nicht mehr
durchrutscht) und Dublettenprüfung ergänzt. Neues `--append`-Flag häuft
Ergebnisse über mehrere kleine Läufe an, statt sie zu ersetzen.

**Warum:** Das Anthropic-Konto hatte kein API-Guthaben mehr – die einzige
verbliebene Cloud-Abhängigkeit im sonst komplett lokalen Trainings-Pfad.
Nutzerwunsch: vollständig lokal/kostenlos.

**Dateien:** `training/llm/gen_dialogue.py` (grundlegend überarbeitet),
`training/llm/README.md`, `TODO.md`.

**Verifiziert:** Mehrere Testläufe mit steigendem `n`; bei kleinem `n`
(~3/Werkzeug) 90-95% Trefferquote, bei großem `n` (~15) fällt sie auf
10-15% – eine echte, dauerhafte Diversitätsgrenze eines 3B-Modells für ein
enges Themenfeld, kein Bug. Ein `--append`-Lauf mit moderatem `n` fügte 18
neue, echte Beispiele zum bestehenden Datensatz hinzu (345 → 363).

**Offen:** Für einen deutlichen Sprung auf mehrere hundert neue Beispiele
bleibt Claude das geeignetere Werkzeug, sobald wieder Guthaben verfügbar
ist – lokale Generierung eignet sich für kleine, wiederholte Ergänzungen,
nicht für eine große Einzelskalierung.

---

## [2026-08-04] Lauf-zu-Lauf-Varianz beim Router-Finetuning entdeckt; Datensatz-Skalierung blockiert

**Was:** Versuch, den Early-Stopping-Punkt für den Llama-3.2-3B-Router
genauer zu finden (`--save-every 20` statt 100, neues
`training/llm/sweep_checkpoints.py` zum Durchtesten aller Zwischenstände).
Datensatz-Skalierung auf ~540 Beispiele schlug fehl: das Anthropic-Konto hat
kein API-Guthaben mehr (`credit balance is too low`) – dabei nebenbei einen
echten Bug in `gen_dialogue.py`s JSON-Extraktion gefunden und behoben (der
gierige Regex `\[.*\]` griff bei manchen Antworten über das eigentliche
Array hinaus; jetzt `json.JSONDecoder.raw_decode` ab der ersten `[`, stoppt
exakt an der echten schließenden Klammer). Der zweite Trainingslauf mit dem
bestehenden 345er-Datensatz (identische Konfiguration wie der erfolgreiche
erste Lauf) konvergierte klar schlechter (bestes Ergebnis 31/52 statt 46/52)
– `mlx_lm.lora` setzt ohne `--seed` keinen festen Zufallssamen, zwei Läufe
mit identischen Hyperparametern durchlaufen unterschiedliche Trainingspfade.
Zusätzlicher Befund: Val-Loss fiel in diesem Lauf durchgehend, während die
Testgenauigkeit zwischenzeitlich auf 0/20 einbrach – Loss ist für diese
Aufgabe kein verlässlicher Stellvertreter für die tatsächliche
Ausgabegenauigkeit.

**Warum:** Fortsetzung des Llama-3.2-3B-Durchbruchs vom selben Tag –
Nutzerwunsch, Datensatz zu skalieren und den Stopp-Punkt zu verfeinern.

**Dateien:** `training/llm/sweep_checkpoints.py` (neu), `gen_dialogue.py`
(JSON-Extraktion robuster, `--limit`-Fix in Aufrufen), `README.md`,
`DECISIONS.md` (nicht geändert diesmal – Befund ist Ergänzung zum
bestehenden ADR-032-Nachtrag, nicht architekturrelevant), `TODO.md`.

**Verifiziert:** `eval.py` gegen den vollen 52er-Testsatz für beide Läufe.

**Offen:** Datensatz-Skalierung bleibt blockiert bis das Anthropic-Konto
wieder Guthaben hat. `adapters/llama_router_v1_iter100` (88,5 %) bleibt der
beste verfügbare Adapter – künftige Läufe brauchen ein festes `--seed` für
faire Vergleiche.

---

## [2026-08-04] Durchbruch: Llama-3.2-3B statt Gemma-Familie fürs lokale Tool-Routing (ADR-032)

**Was:** Neun LoRA-Finetuning-Läufe mit Gemma 3n und Gemma-3-270M (auf Mac/
MLX) divergierten alle systematisch – jede Einzelvariable durchprobiert
(LoRA-Keys, `scale`, Lernrate über vier Größenordnungen, Prompt-Masking,
Batch-Größe, 4bit vs. bf16), Code der Maskierung/des Loss gelesen (kein
Bug). Tiefenrecherche ergab: Llama-3.2-3B-Instruct ist die am besten
unterstützte Architektur in mlx-lm und aktuell das empfohlene Modell für
On-Device-Tool-Calling. Mit identischer Pipeline (gleicher Datensatz, gleiche
Parameter) konvergierte Llama-3.2-3B sauber (Val Loss 3,01→0,096 über 100
Iterationen); Early Stopping auf den Iter-100-Checkpoint ergab **46/52
(88,5 %) korrekt** gegen den gehaltenen Testsatz – gegenüber Nullwirkung bei
jedem Gemma-Versuch. Architektur damit korrigiert: Llama-3.2-3B als
finegetuntes Router-Modell (Tool-Aufruf/Raumgespräch/Weiterleitung an freie
Konversation), Gemma 3n bleibt wie geplant unfinetuned für die eigentliche
Konversation – zwei Modelle statt einem.

**Warum:** Fortsetzung von ADR-032 nach neun gescheiterten Trainingsläufen;
Nutzer lehnte bezahlte Cloud-GPU-Lösung ab (kostenlos/lokal blieb Vorgabe),
erlaubte aber ausdrücklich einen Modellwechsel.

**Dateien:** `training/llm/build_router_dataset.py` (neu, Router-Datensatz:
`persona`-Kategorie → `frei_gespraech()`-Weiterleitung statt eigener
Antwort), `training/llm/prompts/system_prompt_router_de.txt` (neu),
`training/llm/lora_config_router.yaml` (neu, Warmup-Experiment),
`training/llm/eval.py` (`--data`-Parameter ergänzt, generalisiert für
mehrere Datensätze), `training/llm/spike_check.py` (`classify()` um
`handoff`-Typ für `frei_gespraech()` ergänzt), `training/llm/README.md`
(vollständige Dokumentation aller neun gescheiterten + des erfolgreichen
Laufs), `DECISIONS.md` (ADR-032-Nachtrag „Durchbruch"), `TODO.md`.

**Verifiziert:** `eval.py` gegen 52 nie im Training gesehene Testbeispiele,
46/52 korrekt. Kein Gerätetest (weiterhin nur Mac).

**Offen:** Genauerer Early-Stopping-Punkt, größerer Datensatz, ein
beobachteter Fall von Werkzeug-Halluzination (`wetter_vorlesen()` erfunden)
– braucht serverseitige Validierung der Router-Ausgabe gegen die bekannte
Tool-Liste, bevor das produktiv läuft. Speicherbudget für 4GB-Zielgeräte mit
zwei Modellen ungeprüft. ONNX Runtime GenAI als Alternative zu MediaPipe
vermerkt, keine Entscheidung.

---

## [2026-08-09] Echte UI-Screenshots (Emulator) + README/GitHub-Page aktualisiert, Emoji aus beiden entfernt

**Was:**
- Debug-APK auf dem lokal vorhandenen Android-Emulator (`LinaTablet`, API 33,
  `/opt/homebrew/share/android-commandlinetools`) installiert und über den
  bestehenden Debug-Broadcast (`dev.lina.DEBUG_INPUT`, Extra `text`) durch
  drei UI-Zustände gesteuert, dann per `adb exec-out screencap` fotografiert
  und mit `ffmpeg` auf den reinen App-Inhalt zugeschnitten (System-Statusleiste/
  Taskbar entfernt): Grundzustand (`docs/screenshots/idle.png`),
  Kalender-Wochenansicht mit einem Testtermin (`docs/screenshots/calendar.png`)
  und Hörbuch-Player mit einem echten LibriVox-Stream
  (`docs/screenshots/audiobook-player.png`, per „Spiel Hörbuch ab" nach
  Platzieren einer Testdatei unter `/sdcard/Music/Audiobooks/`).
  Ersteinrichtung wurde für den Testlauf per direkt geschriebener
  SharedPreferences-XML übersprungen (nur Emulator-State, nicht committet).
- README.md (beide Sprachversionen) um die Screenshots ergänzt sowie um die
  bislang fehlenden Funktionen **Erinnerungen** und **Kalender** in der
  Feature-Liste (waren nur in `docs/index.html` dokumentiert, nicht im
  README). `docs/index.html` (GitHub Pages) um denselben Kalender-Punkt und
  einen neuen Abschnitt „So sieht das aus" mit derselben Screenshot-Galerie
  ergänzt (responsives Grid, passend zum Schwarz/Gold-Kontrastdesign).
- Auf Nutzerwunsch alle Emoji aus README.md entfernt (dekorative Icons vor
  den Feature-Punkten sowie die ✅/❌-Spalte der Technik-Tabelle, letztere
  durch „Yes"/„No" ersetzt). `docs/index.html` enthielt bereits keine Emoji.
  Bewusst **nicht** angefasst: CLAUDE.md/CHANGELOG.md/TODO.md/DECISIONS.md,
  dort sind ✅/❌/🟣/🟡/⚠️ funktionale Marker (Status, Priorität, Warnhinweis),
  keine Dekoration – Entscheidung explizit mit dem Nutzer abgestimmt.

**Warum:** README und GitHub-Page waren seit dem Kalender-Feature (siehe
Eintrag 2026-07-26 unten) nicht mehr synchron mit dem tatsächlichen
Funktionsumfang; beide hatten außerdem nie echte Screenshots, nur Text.

**Dateien:** `README.md`, `docs/index.html`, neu:
`docs/screenshots/{idle,calendar,audiobook-player}.png`.

**Verifiziert:** GitHub Page lokal im Browser prüft (Layout, Bilder,
Responsive-Grid). README-Bildpfade sind repo-relativ, wie von GitHubs
Markdown-Renderer erwartet.

**Offen:** Speaking-Zustand der Statuskugel weiterhin nicht per Screenshot
festgehalten (Logik aber identisch/mitgetestet über `isSpeaking()`-Polling,
siehe Ambiente-UI-Sektion unten). Screenshots stammen vom Emulator, nicht
vom echten Lenovo-Testtablet – bei Gelegenheit durch echte Gerätefotos
ersetzen oder ergänzen.

---

## [2026-08-04] Feature-Spike: Gemma-3n-Trainingspipeline (Mac) + ConversationEngine-Interface

**Was:** Erste Umsetzung von ADR-032, ausgehend vom physisch verfügbaren
Testtablet:
- Neue Pipeline `training/llm/` (eigenes venv, MLX): `spike_check.py` prüft
  ein Basis-Gemma-3n-E2B gegen 20 handgeschriebene deutsche Prompts (Tools,
  STT-Verhörer, Raumgespräch, Persona) – 11/20 korrekt, bestätigt empirisch
  die Finetuning-Gründe aus ADR-032. `gen_dialogue.py` erzeugt synthetische
  deutsche Trainingsbeispiele über die bestehende Claude-API-Anbindung,
  `build_dataset.py` baut daraus MLX-Chat-JSONL mit train/valid/test-Split
  (Test-Split nie im Training verwendet). `eval.py` scored einen Adapter
  gegen den gehaltenen Testsatz.
- Erster LoRA-Finetuning-Versuch (`mlx_lm.lora`) deckte zwei reproduzierbare
  Probleme in Gemma-3ns noch jungem `mlx-lm`-Support auf (Absturz durch
  LoRA-Wrapping von Gemma3ns AltUp-Mechanismus, behoben; `self_attn.q_proj`
  bekommt keinen Gradienten, ungelöst) – Details in `training/llm/README.md`
  und im ADR-032-Nachtrag (DECISIONS.md). Der resultierende Adapter ist mit
  nur 39 Trainingsbeispielen noch zu schwach für einen messbaren Effekt.
- `core/llm/ConversationEngine.kt` neu: `LinaReply` aus `ClaudeConversation`
  extrahiert, Interface (`ask`/`readDocument`/`reset`) definiert.
  `ClaudeConversation` implementiert es jetzt, `LauncherActivity` hält das
  Feld als Interface-Typ statt der konkreten Klasse – reiner Refactor, kein
  Verhaltensunterschied, Andockpunkt für ein künftiges `GemmaConversation`.

**Warum:** Der Nutzer hat das Testtablet jetzt vor Ort und will die nächsten
Tage aktiv testen und finetunen (Anschluss an ADR-032).

**Dateien:** Neu: `training/llm/` (README, `spike_check.py`,
`gen_dialogue.py`, `build_dataset.py`, `eval.py`, `lora_config.yaml`,
`prompts/`), `app/src/main/kotlin/dev/lina/core/llm/ConversationEngine.kt`.
Geändert: `ClaudeConversation.kt` (implementiert Interface),
`DocumentReadResult.kt` (Doc-Kommentar), `LauncherActivity.kt` (Feldtyp),
`DECISIONS.md` (ADR-032-Nachtrag), `TODO.md`, `.gitignore`.

**Verifiziert:** `./gradlew compileDebugKotlin` grün (`JAVA_HOME` musste auf
den Homebrew-`openjdk@17`-Pfad gesetzt werden, war zuvor nicht gefunden).
Trainingspipeline Ende-zu-Ende auf dem Mac gelaufen (Download, Generierung,
Training, Eval) – noch kein Gerätetest, kein Build-Flavor, kein
funktionierender Adapter.

**Offen:** Datensatz auf mehrere hundert Beispiele skalieren, Lernrate
senken (Divergenz bei 1e-4 beobachtet), `q_proj`-Gradienten-Befund klären
(ggf. Upstream-Issue), danach erst Phase D (Gradle-Flavor,
MediaPipe-Integration) und D.5 (Adapter-Formatkompatibilität fürs Gerät).

---

## [2026-08-04] Doku: ADR-032 – lokaler Gemma-3n-Pfad für NGO-Partner (Build-Flavor + Finetuning-Strategie)

**Was:** Recherche und Architekturentscheidung dokumentiert: Gemma 3n
(E2B/E4B) als eigener Build-Flavor neben der bestehenden Claude-API-Anbindung
(ADR-017) – kein Ersatz, zwei parallele Pfade. Enthält Machbarkeitsprüfung
fürs Zielgerät (TB336ZU, Dimensity 6300), eine Antwort auf die Frage, wie
Konversationsqualität ohne fähiges Android-Testgerät überhaupt verifizierbar
ist (Mac/MLX), sowie eine LoRA/QLoRA-Finetuning-Strategie analog zum
bestehenden Wake-Word-Trainingsmuster.

**Warum:** Blindenvereine/NGOs, die als mögliche zukünftige Tester in Frage
kommen, lehnen eine Anthropic-Anbindung ab und fordern ein lokales Modell.

**Dateien:** `DECISIONS.md` (neu: ADR-032), `TODO.md` (neue Sektion „Lokaler
Gemma-3n-Pfad für NGO-Partner", zwei verstreute ältere Backlog-Zeilen dorthin
konsolidiert).

**Offen:** Reine Recherche/Dokumentation – kein Code. Websuche-/Vision-Ersatz
im NGO-Flavor noch nicht entschieden, kein Mac/MLX-Spike durchgeführt.

---

## [2026-07-26] Feature: Kalender (Datum-Ansage, Termine + automatische Erinnerung, Dokument-Trigger, Familien-Wochenansicht)

**Was:** Vier zusammenhängende Fähigkeiten:
- **Datum-Ansage:** "Welches Datum haben wir heute?"/"Welcher Tag ist heute?"
  → "Heute ist Sonntag, der 26. Juli." (`ResolvedIntent.Date`, mirrort `Time`).
- **Termine mit automatischer Erinnerung:** "Trage einen Termin ein für
  nächsten Montag: Zahnarzt" (Doppelpunkt trennt Datum von Titel, wie beim
  SMS-Diktat). Neuer `GermanDateParser` versteht relative Tage
  (heute/morgen/übermorgen, "in 3 Tagen", "in einer Woche"), Wochentag-relativ
  ("nächsten Montag" – überspringt den heutigen Tag, falls heute bereits
  Montag ist) und explizite Daten ("am 15. März", "15.3.", "15.03.2027").
  Jeder Termin bekommt automatisch eine ganz normale, bestehende `Reminder`
  darunter (Standardzeit 9 Uhr, falls keine Uhrzeit genannt wurde) – kein
  zweites Scheduling-System, siehe ADR-031.
- **Dokument-Trigger:** Beim Vorlesen eines Fotos erkennt Claude über ein neues,
  ausschließlich für `readDocument()` registriertes Tool (`termin_erkannt`)
  einen im Dokument stehenden konkreten Termin/eine Frist und bietet an, ihn
  einzutragen ("Übrigens, im Dokument steht ein Termin: X am Y. Soll ich den
  eintragen?") – eigener, komplett neuer Ja/Nein-Dialog
  (`openDocCalendarFollowUp`/`handleDocCalendarFollowUp`), der die bestehende
  "alles vorlesen?"-Logik unangetastet lässt.
- **Familien-Wochenansicht:** "Zeig mir den Kalender"/"Was sind meine nächsten
  Termine" macht ein neues `CalendarPanel` sichtbar (ersetzt den
  Hörbuch-Player im selben rechten Spalten-Slot) – immer Wochenansicht
  (heute..+6 Tage), große Schrift, Schwarz/Weiß/Gold wie das übrige UI.
  "Verstecke den Kalender" blendet es wieder aus, eine Hörbuch-Aktion
  (Play/Weiter) fordert die Spalte ebenfalls zurück.

**Warum:** Nutzerwunsch – Uhrzeit funktionierte bereits gut, Datum/Termine
fehlten komplett; Angehörige/Pflegepersonal sollen anstehende Termine auf
einen Blick sehen können.

**Dateien:**
- Neu: `core/text/GermanCalendarNames.kt` (geteilter Wochentag-/Monatswortschatz,
  vorher privat in `Reminder.kt` dupliziert), `feature/calendar/CalendarEvent.kt`,
  `feature/calendar/CalendarStore.kt` (EncryptedSharedPreferences wie
  `ReminderStore`), `feature/calendar/GermanDateParser.kt` (pure, unit-testbar),
  `feature/calendar/CalendarManager.kt`, `ui/components/CalendarPanel.kt`,
  `core/llm/DocumentReadResult.kt` (+ `SuggestedCalendarEvent`)
- Geändert: `feature/reminder/Reminder.kt` (Wortschatz-Refactor, keine
  Verhaltensänderung), `core/intent/ResolvedIntent.kt`,
  `core/intent/LocalCommandResolver.kt` (`resolveCalendar()` läuft VOR
  `resolveReminder()`; dessen `ClearReminders`/`ListReminders`-Regex verlieren
  die `termine?`-Alternative, die früher fälschlich mitgriff),
  `core/llm/ClaudeConversation.kt` (`termin_anlegen` in `TOOLS`, isoliertes
  `termin_erkannt` nur in `readDocument()`, dessen Rückgabetyp jetzt
  `DocumentReadResult` statt `LinaReply`), `ui/launcher/LauncherActivity.kt`
  (Dispatch, Layout-Slot-Teilung Kalender/Player, Dokument-Folgefenster)

**Verifiziert am Gerät:** Datum-Ansage korrekt ("Heute ist Sonntag, der 26.
Juli."); Termin-Anlage legt sichtbar eine `Reminder` an (`ReminderScheduler`-Log
bestätigt "morgen um 9 Uhr" für "nächsten Montag" an einem Sonntag);
`CalendarPanel` zeigt die Wochenansicht korrekt inkl. des angelegten Termins;
"verstecke den Kalender" blendet aus; `ClearCalendarEvents`/`ClearReminders`
kollidieren nicht mehr; **unveränderter Dokument-Pfad ohne erkannten Termin
zuerst gegengetestet** (Foto ohne lesbares Dokument → normale Fehlermeldung,
`termin_erkannt=false` im Log, kein Absturz) – erst danach der Rest verifiziert.

**Offen:** Der positive Dokument-Erkennungspfad (`termin_erkannt=true`) braucht
ein reales Foto eines Dokuments mit konkretem Datum – nicht ohne physischen
Zugriff auf das Testgerät simulierbar, nur der unveränderte Negativ-Pfad wurde
live bestätigt.

---

## [2026-07-26] Feature: Hörbuch-Verfügbarkeit ansagen + LibriVox-Genre-Suche (ADR-030)

**Was:** Zwei echte, live gegen die echte LibriVox-API verifizierte Bugs im
bestehenden `LibrivoxRepository` gefunden und behoben, plus neue Genre-/
Themen-Suche:

1. **Parsing-Bug:** Der `fields={id,title,authors,...}`-Parameter lieferte
   keine zusammengeführten JSON-Objekte, sondern mehrere aneinandergehängte
   `{"books":[...]}`-Blöcke (einen je Feld) – `JSONObject(json)` parste nur
   den ersten. Titel/Autor/Dauer/RSS-URL waren dadurch bei **jeder**
   LibriVox-Suche bisher leer bzw. „Unbekannt". Fix: `fields=`-Parameter
   weggelassen, die Standardantwort liefert alles in einem sauberen Objekt.
2. **Sprachfilter-Bug:** Der `language`-Parameter wurde nie an die Such-URL
   angehängt – „deutsche Inhalte" wurden also gar nicht gefiltert. Zusätzlich:
   der Parameter wird von LibriVox serverseitig ignoriert (live geprüft:
   `language=English` liefert trotzdem deutschsprachige Treffer mit). Fix:
   client-seitiger Sprachfilter auf das von der API mitgelieferte
   `language`-Feld, mit höherem `limit` angefragt (20 statt 5), um nach dem
   Filtern noch genug Treffer übrig zu haben.

**Neu:** Sprachbefehl "Hörbücher zum Thema X" / "gibt es Hörbücher über X"
(neuer `ResolvedIntent.SearchAudiobookByGenre`, `LocalCommandResolver.
resolveAudiobookGenreSearch()` – bewusst vor der bestehenden Titel-/
Autorensuche in der Erkennungskette, sonst hätte deren Muster die Plural-Form
mitgerissen). Neue `LibrivoxGenres.kt`: feste LibriVox-Taxonomie (live von
librivox.org/search gescrapt, da die API keinen Genres-Endpunkt hat) + eine
deutsche Synonymtabelle für die bekannten Interessen des Nutzers (Marxismus →
"Political Science", Segeln → "Nautical & Marine Fiction", u.a.). Ein nicht
in der Taxonomie enthaltener Genre-Name liefert von der API **HTTP 500** statt
einer leeren Liste (live verifiziert) – deshalb ausschließlich validierte
Taxonomie-Werte, nie Nutzer-Rohtext, an `?genre=` übergeben. Findet die
Genre-Suche nichts, fällt `AudiobookLibrary.searchByTopic()` automatisch auf
die normale Stichwortsuche zurück; Lina sagt an, was tatsächlich passiert ist
("gefunden in der Kategorie X" vs. "stattdessen nach dem Stichwort gesucht").

**"Was kann ich abspielen?" erweitert:** `AudiobookManager.listBooks()` weist
jetzt immer darauf hin, dass LibriVox durchsucht werden kann; bei leerer oder
sehr kleiner Bibliothek (≤2 Bücher) fragt Lina proaktiv per Sprache, ob sie
suchen soll (Ja/Nein-Folgefenster nach dem Vorbild der SIM-Import-Nachfrage,
`LauncherActivity.openLibrivoxSuggestionFollowUp()`/`handleLibrivoxSuggestionFollowUp()`).

**Dateien:**
- Neu: `feature/audiobook/LibrivoxGenres.kt`
- Geändert: `feature/audiobook/LibrivoxRepository.kt` (Bugfixes, `searchByGenre()`,
  `language`-Feld in `LibrivoxBook`), `feature/audiobook/AudiobookLibrary.kt`
  (`searchByTopic()`), `feature/audiobook/AudiobookManager.kt` (`listBooks()`
  erweitert, neue `searchByTopic()`-Methode), `core/intent/ResolvedIntent.kt`,
  `core/intent/LocalCommandResolver.kt`, `ui/launcher/LauncherActivity.kt`
  (neuer Dispatch-Zweig + Ja/Nein-Folgefenster), `app/build.gradle.kts`
  (`org.json:json` als Test-Abhängigkeit – Android liefert dafür nur einen
  Stub, der echte JVM-Tests der Parsing-Logik unmöglich machen würde)

**Verifiziert am Gerät:** "Suche Hörbücher zum Thema Politik" → korrekt zu
`Political Science` aufgelöst → echter Treffer *Manifest der Kommunistischen
Partei* (Friedrich Engels, 4 Kapitel) mit korrekt befülltem Titel/Autor (der
Parsing-Bug hätte hier leere Felder gezeigt). Regressionscheck der normalen
Titel-/Autorensuche ("suche Tolstoi") weiterhin einwandfrei. `ListAudiobooks`
ohne Absturz, kein Folgefenster ausgelöst (Bibliothek nicht dünn genug – korrekt).

**Offen:** Der proaktive Ja/Nein-Vorschlag bei dünner Bibliothek konnte am
Testgerät nicht auslösen (Bibliothek hat mehr als 2 Bücher) – Logik folgt
1:1 dem bereits am Gerät verifizierten SIM-Import-Muster, aber nicht separat
gegengetestet.

---

## [2026-07-26] Doku: Gesamtüberholung + Prioritäten in TODO.md

**Was:** Alle Projektdokumente gegen den tatsächlichen Code-Stand geprüft und
aktualisiert (viel war seit Tagen/Wochen unverändert, während Code sich weiter
entwickelt hatte). Konkret:
- `TODO.md`: neues Prioritäts-Schema (P0–P4) in der Legende, jede Sektion
  entsprechend getaggt; stale KW-26-Deadline ehrlich als verstrichen benannt
  statt stillschweigend zu ignorieren; doppelten Release-Keystore-Eintrag auf
  eine Stelle reduziert; fälschlich als offen markierte Punkte korrigiert
  (Claude-Anbindung ist längst getestet, Hörbuch-Schlaf-Timer ist am Gerät
  geprüft) – Anrufe/SMS-Gerätetest explizit als blockiert markiert (`[!]`,
  Testtablet hat keine SIM: `gsm.sim.state=ABSENT`).
- `CLAUDE.md`: Modulstruktur um `core/sim/`, `core/contacts/` (Schreib-/Import-
  Komponenten), `feature/contactimport/`, `LinaOrb`/`AudiobookPlayerPanel`/
  `LinaActivity` ergänzt; `WRITE_CONTACTS` in der Berechtigungsliste nachgetragen
  (und die ganze Liste gegen das echte Manifest abgeglichen – mehrere fehlende
  Einträge gefunden); `TtsEngine.isSpeaking()` im Interface-Snippet ergänzt;
  Phase-1-Tabellen um Schlafmodus und SIM-/Kontakt-Import erweitert; "Nächster
  Schritt" korrigiert (LLM-Anbindung stand noch als offen da, ist seit Wochen
  fertig) und aktualisiert.
- `SICHERHEIT.md`: `EncryptedSharedPreferences`- und Auto-Löschungs-Lücken als
  (teilweise) behoben markiert; neue Datenkategorie ergänzt – Kontakt-Import
  schreibt jetzt dauerhaft in die System-Kontakte (`WRITE_CONTACTS`), vorher
  wurden Kontakte nur flüchtig gelesen.
- `WARTUNG.md`: neuer Einwilligungspunkt für den Kontakt-Import (schreibt echte,
  dauerhafte Kontakte – anders als das bisherige, nur flüchtige Vorlesen).
- `ONBOARDING.md`: ADR-Anzahl korrigiert (13 → aktuell 29), `TtsEngine`-Snippet
  um `isSpeaking()` ergänzt, `docs/`-Pfade auf die echten Root-Pfade korrigiert.
- `README.md`: irreführende Aussage "Carried by a German non-profit
  association" korrigiert (widersprach ADR-023 – aktuell privat getragen,
  gemeinnützige Trägerschaft nur eine mögliche Zukunftsoption) + neue Features
  (Schlafmodus, Kontakt-Import, Ambiente-Anzeige) in beide Sprachversionen
  ergänzt.
- `IDEEN.md`: DAISY-Hörbücher und Erinnerungen & Wecker aus "Geplant" in einen
  neuen "Umgesetzt"-Abschnitt verschoben (waren dort mit "Priorität hoch"
  gelistet, obwohl längst gebaut); Lautstärke-Zeile von der noch offenen
  Sprechtempo-Idee getrennt.

**Warum:** Nutzerwunsch – vor dem nächsten Commit sollten Doku und TODO wieder
den echten Stand widerspiegeln, nicht nur der Code. Stale Doku ist besonders
teuer in einem Projekt mit mehreren gleichzeitig arbeitenden Claude-Code-
Instanzen (siehe ONBOARDING.md), die sich auf diese Dateien verlassen.

**Offen:** Die strategische Etappen-Planung ("Plan bis zum Bewerbungsfenster")
wurde nur auf Ist-Stand geprüft, nicht inhaltlich neu zugeschnitten – das ist
eine Entscheidung der Trägerschaft, nicht etwas, das eine Doku-Aufräumrunde
eigenmächtig ändern sollte.

---

## [2026-07-26] Feature: SIM-Erkennung + Kontakt-Import (SIM & vCard-Datei)

**Was:** Lina erkennt eine neue oder andere SIM-Karte (auch beim allerersten
Start mit bereits eingelegter SIM) und fragt per Sprache, ob die SIM-Kontakte
übernommen werden sollen ("Ich habe eine neue SIM-Karte erkannt. Soll ich die
Kontakte übernehmen?"). Zusätzlich neuer Sprachbefehl "Kontakte aus einer Datei
importieren", der Androids Storage-Access-Framework-Dateipicker öffnet und eine
vCard-Datei (.vcf) einliest – das universelle Kontakt-Exportformat, das jedes
alte Handy (Android, iPhone, Feature-Phone) erzeugen kann. Beide Wege
entduplizieren gegen bestehende Kontakte per normalisierter Telefonnummer und
fassen das Ergebnis in einer gesprochenen Zusammenfassung zusammen ("Ich habe
12 neue Kontakte übernommen, 3 gab es schon."), statt bei Dutzenden Kontakten
einzeln nachzufragen.

Ursprünglich als umfassenderer "Migrationsassistent" gewünscht (aktiv nach
einem alten Gerät suchen) – technisch für eine Drittanbieter-App nicht möglich
(Googles Quick-Switch-Übertragung ist eine signierte Systemkomponente ohne
offene Schnittstelle). Nach Rücksprache auf SIM-Erkennung + Datei-Import
begrenzt; Google-Konto-Sync während der Android-Ersteinrichtung braucht keinen
Lina-Code, da `ContactRepository` das ohnehin schon automatisch mitliest.

Der SIM-Fingerabdruck ist ein Best-Effort-Signal (Kombination aus
Subscription-ID, Carrier-Name, Länderkennung und – falls lesbar – ICCID-Suffix)
statt einer garantiert eindeutigen ID, da Android 10+/API 33 die echte ICCID
für Apps ohne Trägerrechte oft schwärzt oder eine `SecurityException` wirft.

**Neue Berechtigung:** `WRITE_CONTACTS` (Manifest + `PermissionsGuide`).

**Dateien:**
- Neu: `core/sim/SimIdentity.kt`, `core/sim/SimIdentityReader.kt`,
  `core/sim/SimChangeDetector.kt`, `feature/contactimport/ContactImportStore.kt`,
  `feature/contactimport/ContactImportManager.kt`, `core/contacts/SimContactSource.kt`,
  `core/contacts/ContactWriter.kt`, `core/contacts/ContactDedup.kt`,
  `core/contacts/PhoneNumberNormalizer.kt`, `core/contacts/VCardParser.kt`
- Geändert: `ui/launcher/LauncherActivity.kt` (App-bereit-Check, Live-Erkennung
  per `BroadcastReceiver`, Sprach-Bestätigung, Dateipicker-Anbindung, neue
  Intent-Zweige), `core/intent/ResolvedIntent.kt`, `core/intent/LocalCommandResolver.kt`,
  `feature/onboarding/PermissionsGuide.kt`, `AndroidManifest.xml`

**Verifiziert am Gerät:** Sprachbefehle "Kontakte von der SIM importieren" und
"Kontakte aus einer Datei importieren" korrekt als Intent erkannt und ohne
Absturz ausgeführt (Testgerät hat keine SIM – `gsm.sim.state=ABSENT`, damit
erwartungsgemäß "keine Kontakte gefunden"). vCard-Import komplett end-to-end
verifiziert: Testdatei mit zwei Kontakten in Downloads gelegt, per Sprachbefehl
den System-Dateipicker geöffnet, Datei ausgewählt, danach beide Kontakte
tatsächlich in der echten Android-Kontakte-Datenbank gefunden (`content query`)
– Parser, Dedup und Batch-Insert funktionieren real, nicht nur in Unit-Tests.
Test-Kontakte danach wieder entfernt.

**Offen:** Ein echter SIM-Wechsel konnte am Testgerät nicht geprüft werden
(kein physischer SIM-Steckplatz belegt) – die Logik ist aber unit-getestet
(`SimIdentityTest`) und die Erkennungspfade (App-Start + Live-Broadcast)
degradieren nachweislich sauber auf "keine SIM" statt zu crashen.

---

## [2026-07-26] Feature: Schlafmodus (Bildschirm dimmen + Lautstärke 30%)

**Was:** Neuer Sprachbefehl "Schlafmodus" (auch "gute Nacht", "Schlafenszeit"):
dimmt die Bildschirmhelligkeit auf einen schwachen Rest (0.04, reine
Fenster-Helligkeit über `Window.attributes.screenBrightness` – keine
Systemeinstellung, daher keine `WRITE_SETTINGS`-Berechtigung nötig, da Lina
ohnehin dauerhaft als Home-App im Vordergrund läuft) und setzt die Lautstärke
auf 30% (Hörbuch oder System, je nachdem was gerade läuft – dieselbe Weiche wie
bei "lauter"/"leiser"/den Lautstärke-Sollwerten). Gegenstück "Schlafmodus aus"
/ "wach auf" / "licht an" stellt die automatische Helligkeitssteuerung wieder
her (`BRIGHTNESS_OVERRIDE_NONE`).

**Warum:** Nutzerwunsch – abends per Sprachbefehl Bildschirm und Lautstärke
gleichzeitig für die Nacht herunterfahren, ohne Tablet in die Hand nehmen zu
müssen.

**Dateien:**
- `core/intent/ResolvedIntent.kt` – `SleepMode`/`SleepModeOff`
- `core/intent/LocalCommandResolver.kt` – `resolveSleepMode()`, in die
  Haupt-Erkennungskette eingehängt (vor `resolveAudiobook`, da sonst unabhängig
  vom bestehenden Hörbuch-Schlaf-Timer)
- `ui/launcher/LauncherActivity.kt` – `enterSleepMode()`/`exitSleepMode()`,
  Dispatch + Debug-Log-Einträge

**Verifiziert am Gerät:** `dumpsys display` zeigt `Display Brightness=0.04`
nach "schlafmodus" (exakt der gesetzte Wert), `dumpsys audio` bestätigt den
Lautstärke-Sprung auf Stufe 5/15 (≈30%, Rundung durch die grobe 15-stufige
Skala des Geräts – dieselbe Rundung wie bei den bestehenden
Lautstärke-Befehlen). "wach auf" setzt die Helligkeit zurück auf
automatische Steuerung (Sensor-abhängig, am Gerät auf 0.727 beobachtet).

---

## [2026-07-26] Feature: Ambiente-UI für Angehörige/Besucher (Statuskugel + Hörbuch-Player) + Querformat

**Was:** Der bisherige Bildschirm war ein reiner Entwickler-Debugscreen
(Texteingabe + Log). Neu:
- `LinaActivity` (sealed class: `Loading/Idle/Listening/Thinking/Speaking/Error`)
  als additives Zustandsmodell neben dem bestehenden `statusText` – an allen ~18
  bestehenden Zuweisungsstellen in `LauncherActivity.kt` ergänzt, kein
  bestehendes Verhalten geändert.
- `TtsEngine.isSpeaking(): Boolean` neu im Interface (vorher nur in
  `PiperTtsEngine` vorhanden, nicht über die Abstraktion erreichbar);
  `AndroidTtsEngine` bekommt eine eigene `@Volatile`-Implementierung. Wird von
  der UI alle 250ms gepollt (kein Push-Mechanismus im Projekt vorhanden), hat
  Vorrang vor jedem `linaActivity`-Wert, solange `true`.
- `LinaOrb` (`ui/components/LinaOrb.kt`): rein dekorative, animierte
  Statuskugel – unterscheidet Zustände über Bewegungscharakter (Puls, Ringe,
  rotierende Bögen, Sinus-Wobble, einmaliges Wackeln bei Fehlern), bewusst
  schlicht/flach ohne Glow/Blur, bleibt bei Schwarz/Weiß/Gold.
- `AudiobookPlayerPanel` (`ui/components/AudiobookPlayerPanel.kt`): sichtbare
  Steuerung fürs Hörbuch für Angehörige – Titel/Autor/Kapitel, Fortschrittsbalken
  + mm:ss-Anzeige, Steuerzeile (Zurück/-30s/Pause-Weiter/Vor), Lautstärke
  (Leiser/Lauter). Jeder Button ruft eine bestehende `AudiobookManager`-Methode
  direkt auf. Neu dafür: `AudiobookManager.currentStatus()` liest Titel, Autor,
  Kapitel, Position/Dauer, Play-Status – reiner Lesezugriff, keine neue Logik.
  Erscheint nur, wenn tatsächlich ein Buch geladen ist (auch pausiert).
- Debug-Eingabefeld, "Senden"-Button und Log-Text komplett aus der UI entfernt
  (Nutzerentscheidung: kein Bedarf mehr, seit der `dev.lina.DEBUG_INPUT`-Broadcast
  headless funktioniert). `processDebugInput()` und der Broadcast-Empfänger
  bleiben unverändert – nur die sichtbaren Widgets sind weg.
- **Querformat:** Tablet liegt in der Praxis fast immer im Querformat (Ständer,
  Wohnzimmer). `android:screenOrientation` von `portrait` auf `sensorLandscape`
  geändert; das Compose-Layout ist jetzt ein `Row` statt `Column` – Kugel+Status
  links, Hörbuch-Player rechts (nur wenn ein Buch geladen ist), sonst zentriert
  über die volle Breite. Vorher: fixiertes Hochformat auf physisch querliegendem
  Gerät führte zu sichtbarem Letterboxing (System-Hintergrund links/rechts).

**Bugfix währenddessen gefunden:** `LinaTypography.labelLarge` (Button-Beschriftungen)
hatte fest `color = LinaGold` hinterlegt – auf dem goldenen `Button`-Hintergrund
des neuen Players war der Text dadurch komplett unsichtbar (Gold auf Gold), obwohl
`ButtonDefaults.buttonColors(contentColor = onPrimary)` korrekt gesetzt war: die
explizite Farbe im `TextStyle` hat den vom Button bereitgestellten `LocalContentColor`
überschrieben. Fix: `labelLarge` ohne feste Farbe – Button-Text erbt jetzt
`onPrimary` (Schwarz auf Gold), am Gerät bestätigt. Betraf vermutlich auch den
inzwischen entfernten Debug-"Senden"-Button.

**Warum:** Angehörige und Besucher sollen sehen können, was Lina gerade tut, und
bei Bedarf ein laufendes Hörbuch selbst bedienen können – ohne dass sich am
primären Sprachinterface für den blinden Nutzer etwas ändert (Audio bleibt
primär, die Kugel ist rein dekorativ ohne Touch-Target).

**Dateien:**
- Neu: `ui/launcher/LinaActivity.kt`, `ui/components/LinaOrb.kt`,
  `ui/components/AudiobookPlayerPanel.kt`
- Geändert: `ui/launcher/LauncherActivity.kt` (Zustandsfeld, ~18 Zuweisungen,
  Compose-Baum auf `Row` umgebaut, Debug-Panel entfernt), `ui/components/LinaTheme.kt`
  (labelLarge-Fix), `core/tts/TtsEngine.kt`, `core/tts/AndroidTtsEngine.kt`,
  `core/tts/PiperTtsEngine.kt` (`isSpeaking()`), `feature/audiobook/AudiobookManager.kt`
  (`currentStatus()`), `AndroidManifest.xml` (`screenOrientation`), `app/build.gradle.kts`
  (`androidx.compose.animation:animation` explizit)

**Verifiziert am Gerät:** Idle/Thinking/Listening-Zustände der Kugel sichtbar und
unterscheidbar; Hörbuch-Player erscheint bei geladenem Buch, alle Buttons per
Touch funktionsfähig (Pause/Weiter-Toggle bestätigt), Fortschrittsbalken und
Zeitanzeige aktualisieren sich live; Querformat füllt den Bildschirm vollständig,
kein Letterboxing mehr. Speaking-Zustand der Kugel nicht separat per Screenshot
eingefangen (kurze Antworten liefen durch, bevor der Screenshot griff) – Logik
ist aber identisch zu den anderen Zuständen und über `isSpeaking()`-Polling
unabhängig getestet.

**Offen:** Performance-Check (Kugel-Animation parallel zu Piper-Synthese unter
Dauerlast) nicht gesondert gemessen, aber während des Tests keine sichtbaren
Ruckler.

---

## [2026-07-26] Feature: Direkte Lautstärke-Sollwerte + Stummschalten

**Was:** Ergänzt die relative Lautstärkeregelung ("lauter"/"leiser") um direkte
Sollwerte: "Lautstärke eins" bis "zehn" (= 10–100% in 10er-Schritten), "Lautstärke
(auf) X Prozent" (beliebiger Wert 0–100), und explizites Stummschalten ("Ton aus",
"stumm", "Lautstärke aus", "Lautstärke 0" – alle über `SetVolume(0)`). Nutzt
dieselbe Weiche wie "lauter"/"leiser": läuft ein Hörbuch, wird dessen Lautstärke
gesetzt; sonst die Systemlautstärke (`STREAM_MUSIC`). Prozent-Muster wird vor dem
Stufen-Muster geprüft, damit z.B. "70 Prozent" nicht fälschlich als (ungültige)
Stufe 70 geparst wird. `AudiobookManager`s Ansage-Logik für "lauter"/"leiser" und
den neuen Sollwert-Befehl in einen gemeinsamen `announceVolume()`-Helfer gezogen.

**Warum:** Nutzerwunsch nach dem ersten Lautstärke-Feature – direkte Werte statt
nur schrittweiser Anpassung, plus ein expliziter Mute-Befehl.

**Dateien:** `core/intent/ResolvedIntent.kt`, `core/intent/LocalCommandResolver.kt`,
`feature/audiobook/AudiobookManager.kt`, `ui/launcher/LauncherActivity.kt`,
`test/.../LocalCommandResolverTest.kt`

**Getestet (isoliert per adb, mehrfach lauter/leiser + Stufe/Prozent/Stumm, sowohl
Hörbuch-aktiv als auch System-Pfad):** alle Kombinationen korrekt geroutet, Werte
stimmen (mit erwarteter Rundung auf ganzzahlige System-Lautstärkestufen), Mute
korrekt gesetzt und durch neuen Prozentwert wieder aufgehoben.

---

## [2026-07-26] Feature: Lautstärke "lauter"/"leiser" für Hörbücher

**Was:** Neue Sprachbefehle "lauter"/"leiser" passen die Hörbuch-Lautstärke in
10%-Schritten an (0–100%, `AudiobookPlayer.adjustVolume()`), mit gesprochener
Rückmeldung ("Lautstärke 70 Prozent.", Grenzfälle "geht nicht lauter"/"Lautstärke
aus."). Ohne geladenes Hörbuch: "Kein Hörbuch ausgewählt." `ResolvedIntent.VolumeUp`/
`VolumeDown`, lokal in `LocalCommandResolver` erkannt (Wortgrenzen `\b`, aus der
"weiter"-Lektion vom Vortag übernommen – kein reiner Teilstring-Test). Am Gerät
getestet (lauter → lauter → leiser, alle korrekt erkannt und bestätigt).

**Warum:** Nutzerwunsch – Lautstärkeregelung per Sprache fehlte bisher komplett.

**Dateien:** `feature/audiobook/AudiobookPlayer.kt`, `feature/audiobook/AudiobookManager.kt`,
`core/intent/ResolvedIntent.kt`, `core/intent/LocalCommandResolver.kt`,
`ui/launcher/LauncherActivity.kt`, `test/.../LocalCommandResolverTest.kt`

**Nachtrag (selbiger Tag):** "lauter"/"leiser" steuern jetzt zusätzlich die
**Systemlautstärke** (`STREAM_MUSIC`, über `AudioManager.adjustStreamVolume()`),
wenn gerade **kein Hörbuch läuft** (`audiobookManager?.isPlaying == true` entscheidet
die Weiche in `LauncherActivity.handleIntent()`). Gesprochene Rückmeldung wie beim
Hörbuch-Pfad, keine System-Lautstärkeanzeige (hilft einem blinden Nutzer nicht).
Am Gerät sauber verifiziert (isoliert per adb, ohne manuelles Zutun): Tablet stand
auf stumm (`Muted: true, streamVolume:0`) – nach dem Sprachbefehl "lauter"
`Muted: false, streamVolume:1`, korrekt entstummt und einen Schritt lauter, plus
gesprochene Bestätigung. Gegenprobe bei laufendem Hörbuch: "lauter" traf weiterhin
korrekt die Hörbuch-Lautstärke, nicht das System.

**Dateien (Nachtrag):** `ui/launcher/LauncherActivity.kt`

---

## [2026-07-26] Bugfix: WakeWordService überlebt Konversationsturns (Android-14-Hintergrundregel)

**Was:** Der beim Dauerbetriebs-Check gefundene Ausfall (siehe vorheriger Eintrag)
ist behoben. Root Cause war der Stop+Neustart-Zyklus: `LauncherActivity` stoppte
`WakeWordService` komplett, um das Mikrofon für STT freizugeben, und startete ihn
danach über `startForegroundService()` neu – genau dieser Neustart-Typ ist unter
Android 14+/15 aus dem Hintergrund verboten. Fix: Der Service läuft jetzt
durchgehend (nie mehr komplett gestoppt); `WakeWordService.pauseListening()`/
`resumeListening()` schicken stattdessen ein Kommando per normalem `startService()`
an die bereits laufende Instanz – das löst keinen neuen Foreground-Start aus und
ist daher von der Android-Regel nicht betroffen. `OpenWakeWordEngine.stop()`/
`start()` geben dabei nur Mikrofon/Thread frei bzw. neu, ohne die geladenen
ONNX-Modelle neu zu laden (schneller als vorher als Nebeneffekt). Alle 7
Stop-Aufrufstellen in `LauncherActivity.kt` auf `pauseListening()` umgestellt, der
zentrale Wiederaufnahme-Punkt (`wakeResumeRunnable`) auf `resumeListening()`. Die
beiden echten Kaltstart-Stellen (`onResume()`-Fallback, initialer Start nach
Onboarding) bleiben bei `WakeWordService.start()`.

**Warum:** Das Stop/Restart-Muster passierte bei jedem Gesprächsturn – ein
Kernszenario für einen Sprachassistenten, nicht ein Randfall. Am Gerät verifiziert:
exakt der Ablauf, der vorher mit `SecurityException` abstürzte (Antwort →
Folgefenster → Stille-Timeout → Rückkehr zum Weckwort), lief danach fehlerfrei durch;
kein Treffer der ursprünglichen Fehlermeldung mehr im gesamten Log-Puffer.

**Dateien:** `core/wakeword/WakeWordService.kt`, `ui/launcher/LauncherActivity.kt`

**Offen:** Echter Prozess-Tod (z.B. OOM-Kill) im Hintergrund bräuchte weiterhin
einen vollständigen Neustart über `WakeWordWatchdog`, der theoretisch noch an
derselben Regel scheitern könnte, falls die App exakt dann im Hintergrund ist –
ein deutlich selteneres Restrisiko als der behobene Fall, nicht weiter verfolgt.

---

## [2026-07-26] Dauerbetrieb-Check: WakeWordService-Restart scheitert an Android-Hintergrundregel

**Was:** Beim Dauerbetriebs-Monitoring (Plan `reicht-weiter-mit-den-ethereal-perlis.md`,
Punkt 4) reichte normale Nutzung, um einen realen Ausfall zu reproduzieren – keine
Stunde Wartezeit nötig. Ablauf am Gerät: ein Gesprächsturn läuft (Mikrofon wird für
STT kurz freigegeben, `WakeWordService` gestoppt), danach soll der Service
zurückkommen. Genau dabei schlug `startForeground()` mit
`SecurityException: ... Foreground service started from background can not have
... microphone access` fehl – eine Android-14/15-Regel, die Mikrofon-Foreground-
Services grundsätzlich nicht aus dem Hintergrund neu starten lässt. Der Service
beendet sich daraufhin selbst (kein Crash, kein Log-Fehler außer der abgefangenen
Exception). `WakeWordWatchdog` (30s-Prüfintervall) versucht zwar nachzustarten,
scheitert aber an derselben Regel, solange die App im Hintergrund ist. Der Code
kennt das Problem bereits in einem Kommentar (`WakeWordService.onCreate()`),
verlässt sich aber ausschließlich auf `LauncherActivity.onResume()` als Rettung –
das setzt voraus, dass jemand die App manuell wieder in den Vordergrund holt.

**Warum wichtig:** Das Stop/Restart-Muster passiert bei **jedem** Konversationsturn,
nicht nur selten. Verliert die App währenddessen kurz den Vordergrund-Status (z.B.
Bildschirm-Timeout auf dem stationären Tablet), bleibt das Weckwort dauerhaft aus,
bis jemand die App manuell öffnet – für ein Gerät, das unbeaufsichtigt zuhören soll,
ein potenziell gravierender Ausfall, der bisher unter "Lenovo/ZUI
Battery-Optimierung – killt es den Service?" vermutet, aber nicht auf diese
konkrete, andere Ursache zurückgeführt war.

**Dateien:** Keine Code-Änderung – reiner Befund. `TODO.md` aktualisiert
(Risiken & Showstopper).

**Offen:** Braucht eine eigene Architekturentscheidung, nicht in dieser Session
gelöst. Denkbare Richtungen: Bildschirm/Vordergrund-Status aktiv halten
(`FLAG_KEEP_SCREEN_ON` o.ä.), Restart-Strategie ändern (z.B. Mikrofon-Freigabe
seltener/kürzer, oder Service-Typ/-Architektur überdenken), oder eine
Foreground-Ausnahme beantragen, falls möglich. Sollte vor einem produktiven
Dauerbetrieb beim Testnutzer geklärt sein.

---

## [2026-07-25] Hörbuch-Nachtests: ExoPlayer-Audiofokus-Bug, "weiter"-Mehrdeutigkeit, RSS-Entscheidung

**Was:**
1. **Bugfix Audio-Überschneidung (zweite Ursache):** Der erste Überschneidungs-Fix
   (onDone-Callback in `AudiobookManager.playBook()`) behob nur den Fall "frischer
   Start". Am Gerät reproduziert: ein **pausiertes** Hörbuch lief von selbst wieder
   an, sobald Piper kurz Audio-Fokus anforderte und wieder freigab (Weckwort → "Ja?"
   → STT) – bewiesen über `AudioTrack`-Logzeilen (Hörbuch-Track lief 1+ Sekunde vor
   Beginn der Ansage-Synthese los). Ursache: `AudiobookPlayer` erstellte ExoPlayer
   ohne explizite `AudioAttributes`, wodurch Media3s **automatische
   Audiofokus-Verwaltung** aktiv war und die Wiedergabe bei Fokus-Rückgewinn eigenständig
   fortsetzte – unabhängig von jedem eigenen Play/Pause-Code. Fix:
   `ExoPlayer.Builder(...).setAudioAttributes(..., handleAudioFocus = false)`, da die
   App Ducking/Pause bereits selbst orchestriert.
2. **Bugfix "weiter"-Mehrdeutigkeit:** `LocalCommandResolver.resolveAudiobook` matchte
   `.*weiter.*` als reinen Teilstring ohne Wortgrenzen – traf dadurch auch
   zusammengesetzte Wörter wie "weiterreden" ("lass uns weiterreden" startete
   versehentlich das Hörbuch statt an Claude zu gehen, am Gerät reproduziert). Fix:
   `\b`-Wortgrenzen um die Alternation.
3. **Entscheidung RssFeedRepository: behalten** als Offline-Fallback, nicht entfernt.
   Testaufbau (`XmlPullParser` → `DocumentBuilder`) bleibt offen.
4. **Live-Tests bestätigt:** Schlaf-Timer (Fade-Out exakt 30s vor Ablauf, sauberer
   Pause-Trigger bei Lautstärke 0), LibriVox-Streaming-Mehrkapitel-Navigation über die
   Kapitelgrenze (Kapitelwechsel lädt nachweislich eine andere Remote-Datei, nicht nur
   `chapters.first()` wie der 2026-07-20-Bug) – beide fehlerfrei am Gerät.

**Warum:** Fortsetzung der Hörbuch-Testreihe laut Plan
(`reicht-weiter-mit-den-ethereal-perlis.md`). Der Audiofokus-Bug war ein Nutzerbericht
("Jules Verne hat getriggert") während eines freien Konversationstests – Root Cause
lag tiefer als der erste Fix vom selben Tag.

**Dateien:** `feature/audiobook/AudiobookPlayer.kt`, `core/intent/LocalCommandResolver.kt`,
`test/.../LocalCommandResolverTest.kt`, TODO.md

**Offen:** Sicherheits-Timeout für TTS-Wartefenster, EncryptedSharedPreferences für
Erinnerungen, Auto-Löschung Setup-Aufnahmen/Testfotos, Dauerbetrieb-Monitoring (siehe
Plan). Anrufe/SMS bleiben blockiert (kein SIM im Testtablet).

---

## [2026-07-25] Hörbücher: Echo-Schutz, lokale Mehrkapitel-Ordner, vier deutsche LibriVox-Bücher installiert

**Was:**
1. **Bugfix Echo/Weckwort während Hörbuch-Wiedergabe:** Die Weckwort-Erkennung ignorierte bisher nur Linas eigene TTS-Stimme (`piperEngine.isSpeaking()`), nicht die laufende Hörbuch-Wiedergabe. Die Erzählstimme konnte dadurch fälschlich das Weckwort auslösen; die STT fing dann Buchtext ein und schickte ihn als vermeintlichen Befehl an Claude – am Gerät reproduziert. Fix: `AudiobookManager.duckForListening()`/`resumeAfterListening()` pausieren/setzen lautlos fort; `LauncherActivity` duckt bei jedem Weckwort-Trigger und setzt zentral in `resumeWakeWordListening()` fort – außer der Nutzer wollte im Fenster tatsächlich pausieren/stoppen (neues Flag `explicitAudiobookPause`). Nebeneffekt behoben: „Stopp" pausiert jetzt auch tatsächlich ein laufendes Hörbuch (vorher trotz „Stoppt Vorlesen oder Wiedergabe"-Beschreibung wirkungslos für Audiobooks).
2. **Lokale Mehrkapitel-Bücher:** `AudiobookLibrary.localFolderBooks()` – ein Unterordner in `Audiobooks/` mit mehreren Audiodateien wird zu einem Buch mit echter Kapitelnavigation (eine Datei je Kapitel, wie beim LibriVox-Streaming). Ordnername `"Titel - Autor"` wird geparst. Vorher hätte `scanLocalFiles()` jede Datei als eigenständiges Buch ohne Kapitelbezug gelistet.
3. **Bugfix Speicherzugriff:** Der öffentliche `Music/Audiobooks`-Ordner war unter Scoped Storage (Android 13+) ohne Berechtigung nicht lesbar (`Permission denied`, am Gerät reproduziert – 4 Bücher wurden zu nur "keine Hörbücher" verarbeitet). `READ_MEDIA_AUDIO` in Manifest und `PermissionsGuide` ergänzt.
4. **Vier deutsche LibriVox-Hörbücher installiert** (gemeinfrei, öffentlich zum Download bestimmt): Tolstoi – *Herr und Knecht* (10 Kapitel), Keller – *Der Schmied seines Glückes* (6 Kapitel), Eichendorff – *Die Entführung* (5 Kapitel), Verne – *Reise um die Erde in 80 Tagen* (37 Kapitel). Insgesamt 58 Dateien/733 MB, in `/storage/emulated/0/Music/Audiobooks/<Titel> - <Autor>/` auf dem Testgerät.

**Warum:** Nutzerwunsch nach deutschsprachigem Hörbuch-Material zum Offline-Hören; der Echo-Fehler wurde beim Live-Test zufällig entdeckt („Lina hört ab und an dem Hörbuch zu und hat Eingaben/Anfragen an Claude") und ist ein reales Nutzungsproblem, kein Rand­fall.

**Dateien:** `feature/audiobook/AudiobookManager.kt`, `feature/audiobook/AudiobookLibrary.kt`, `ui/launcher/LauncherActivity.kt`, `feature/onboarding/PermissionsGuide.kt`, `AndroidManifest.xml`

**Offen:** Echo-Fix behebt nur die *Folgen* eines Fehlalarms (kein Claude-Unsinn mehr, Buch pausiert/läuft sauber weiter) – die akustische Ursache (Erzählstimme kann weiterhin das Weckwort selbst triggern) bräuchte eine echte Acoustic-Echo-Cancellation-Lösung, nicht nur Ducking. `localFolderBooks()` ist ungetestet in JVM-Unit-Tests (Android-`Context`/`Environment`-Abhängigkeit, wie der Rest von `AudiobookLibrary`).

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

## [2026-07-25] Nachrichten über Claude statt RSS + zwei Bring-up-Fixes vom Tabletttest

**Was:**
1. „Was gibt es Neues?" läuft jetzt komplett über Ebene 2 (`ClaudeConversation` + Websuche) statt über den RSS-Reader: zwei bis drei wirklich relevante Meldungen (Region + Welt gemischt), danach ein Rückfrage-Angebot, echte Konversation statt starrem „mehr/nächste/stopp"-Folgefenster. Das Werkzeug `nachrichten_vorlesen` und die lokale News-Erkennung in `LocalCommandResolver` sind entfernt; `NewsReader`/`RssFeedRepository` bleiben unangetastet im Code liegen (mögliche Offline-Reserve), werden aber nicht mehr angesprochen.
2. **Bugfix TTS-Deadlock:** `PiperTtsEngine.synthesizeAndPlay` gab lange Texte (z.B. ein 1078-Zeichen-Dokument) in einem einzigen `generate()`-Aufruf an sherpa-onnx – das blockierte minutenlang, `isBusySpeaking()` blieb dauerhaft `true`, das Dokument-Folgefenster öffnete nie und das Weckwort wurde nie reaktiviert (Voice-Deadlock, reproduziert am echten Tablet bei „Lies meine Post"). Fix: Text wird jetzt an Satzgrenzen in ≤240-Zeichen-Stücke zerlegt (`splitIntoChunks`/`hardWrap`) und nacheinander synthetisiert/abgespielt; `stopRequested`-Flag lässt `stop()`/INTERRUPT den Chunk-Lauf sofort abbrechen.
3. **Bugfix Activity-Neustart bei Kameraaufnahme:** Auf dem Lenovo-Tablet (ZUI 17.5, physisch im Querformat montiert) hat die OEM-Funktion `OvCameraRotation` beim Öffnen der Rückkamera einen Konfigurationswechsel ausgelöst. `LauncherActivity` war auf `screenOrientation="portrait"` fixiert, aber ohne `android:configChanges` – Android hat die Activity deshalb zerstört und neu aufgebaut, während der Foto→Claude→Vorlesen-Hintergrund-Thread noch lief. Der Thread sprach danach eine bereits heruntergefahrene `ttsEngine`-Instanz an; `speak()` legte den Text lautlos in eine tote Queue (kein Crash, keine Logzeile – nur Stille, zweimal exakt so reproduziert). Fix: `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize|uiMode"` im Manifest verhindert die Zerstörung; zusätzlich verwirft `PiperTtsEngine.speak()` jetzt Aufrufe nach `shutdown()` mit einer Log-Warnung statt sie stumm zu verschlucken.

**Warum:** Nutzerfeedback nach dem ersten Tablet-Bring-up: RSS-Nachrichten waren „grausam" in Bedienung und Klang; Claude kann das mit Websuche und echtem Dialog deutlich besser und personalisiert (Region Oldenburg, Interessen Politik/Natur sind aus dem Onboarding bereits gesetzt). Die beiden Bugs wurden beim direkten Testen am Gerät gefunden – der eigentliche Wert des Tablet-Bring-ups: beide waren im Code unsichtbar und sind ohne echtes Gerät (Whisper-Timing, ZUI-OEM-Verhalten, Tablet-Orientierung) praktisch nicht zu finden.

**Dateien:** `core/intent/LocalCommandResolver.kt`, `core/llm/ClaudeConversation.kt`, `core/tts/PiperTtsEngine.kt`, `AndroidManifest.xml`, `test/.../LocalCommandResolverTest.kt`

**Offen:** Alter RSS-Code (`NewsReader`, `RssFeedRepository`, zugehörige Intents) ist jetzt totes Gewicht – Entscheidung noch aus, ob er als Offline-Fallback bleibt oder entfernt wird. Sicherheits-Timeout für die TTS-Wartefenster (`openFollowUpWindow`/`openDocFollowUp`) als zusätzliche Absicherung gegen künftige Hänger ist als Folgeaufgabe vorgemerkt, aber durch die beiden Fixes hier nicht mehr akut.

---

## [2026-07-25] Trägerschaft: Projekt läuft als Privatperson

**Was:** Die Doku spiegelt jetzt, dass Lina vorerst **privat vom Entwickler** getragen wird – ohne Träger-Organisation. Neuer `ADR-023`, der ADR-016 (NC-Lizenz), ADR-020 (Proxy/Verantwortung) und ADR-021 (Kostenmodell) ändert; die drei tragen eine Aktualisierungsnotiz, ihr historischer Text bleibt. Alle Verweise auf einen gemeinnützigen Träger in CLAUDE.md, README.md, CONTRIBUTING.md, NOTICE.md, SICHERHEIT.md, WARTUNG.md, PROXY-SPEC.md und der Landingpage sind auf „nicht-kommerzielles, quelloffenes Projekt" bzw. „Entwickler als Verantwortlicher" umgestellt.

**Warum:** Planänderung – das Projekt läuft als Privatperson weiter, mit Bewerbung beim Prototype Fund (der nur an Privatpersonen auszahlt) und offenen Gesprächen über Testausweitung und Finanzierung. Strategisch werden **keine konkreten Organisationen namentlich benannt**; alles bleibt offen, Kandidaten nur intern. Wichtige Klarstellung: die CC-BY-NC-SA-Stimme bleibt nutzbar – NonCommercial hängt an der Art der Nutzung, nicht an der Rechtsform.

**Dateien:** DECISIONS.md (ADR-023 neu, ADR-016/020/021 annotiert), CLAUDE.md, README.md, CONTRIBUTING.md, NOTICE.md, SICHERHEIT.md, WARTUNG.md, PROXY-SPEC.md, docs/index.html, CHANGELOG.md

**Offen:** Tragfähiges Finanzierungsmodell (an künftigen gemeinnützigen Partner geknüpft); AVV mit Anthropic; ob/wann der Proxy kommt.

---

## [2026-07-21] Sicherheitskonzept angelegt

**Was:** `SICHERHEIT.md` – Schutzbedarf, tatsächliche Datenflüsse, technische und organisatorische Maßnahmen, Geräteverlust, Meldeweg, und ausdrücklich die Grenzen. Enthält eine offene Liste bekannter Lücken statt nur der Stärken. Einwilligung in WARTUNG.md präzisiert.

**Warum:** Der Betreiber wird mit ADR-020 datenschutzrechtlich verantwortlich – eine Dokumentation der Maßnahmen ist nach Art. 32 DSGVO ohnehin Pflicht. Zusätzlich zahlt sie auf die Prototype-Fund-Schwerpunkte Datensicherheit und Software-Infrastruktur ein, wo die Passung laut interner Notizen das größte Bewerbungsrisiko ist.

**Befund beim Schreiben:** Die Kontaktnamen aus dem Telefonbuch gehen bei **jedem** Konversationsturn an Anthropic – sie stecken im System-Prompt, damit `ClaudeConversation` verhörte Namen zuordnen kann ("Rumfe Boris an"). Die Einwilligung sprach bisher nur allgemein von "freien Fragen" und war damit unvollständig. Telefonnummern und SMS-Inhalte sind nicht betroffen. WARTUNG.md Punkt 2 benennt es jetzt ausdrücklich.

**Weitere dokumentierte Lücken:** einkompilierter API-Schlüssel (ADR-020 adressiert ihn), unverschlüsselte `SharedPreferences` inklusive gesundheitsbezogener Erinnerungen, unbegrenzt liegenbleibende Einrichtungs-Sprachaufnahmen und `testfoto`-Bilder.

**Dateien:** SICHERHEIT.md (neu), WARTUNG.md, TODO.md, CHANGELOG.md

**Offen:** AVV mit Anthropic; Kontaktadresse für Sicherheitsmeldungen fehlt noch im Repository.

---

## [2026-07-21] Kontakt-Matching getestet, Buchstabiertafel, Proxy-Entwurf

**Was:** Drei Aufgaben, die ohne Tablet machbar waren.
1. `FuzzyContactMatcher` mit 22 JVM-Tests abgesichert. Dafür ein `ContactSource`-Interface eingezogen (`ContactRepository` implementiert es) – vorher war die Klasse an `Context` gekoppelt und in reinen JVM-Tests nicht instanziierbar. Aufruferseite unverändert.
2. `GermanSpelling` in `core/text/` – Buchstabiertafel für die gesprochene Ansage des Pairing-Codes, mit `CODE_ALPHABET` ohne verwechselbare Zeichen. 11 Tests.
3. `PROXY-SPEC.md` – Spezifikationsentwurf zu ADR-020: Datenmodell, Endpunkte, Pairing mit Geräte-Geheimnis, Kontingente, Datenschutz, offene Fragen.

**Warum:** Das Matching entscheidet, wen Lina anruft – der teuerste denkbare Fehler, weil der Nutzer nicht sieht, wen er am Apparat hat, und den Irrtum erst im Gespräch merkt. Die Buchstabiertafel ist Blocker für den Kopplungsdialog aus ADR-020. Bewusst die traditionelle Tafel (Anton, Berta, Cäsar) statt DIN 5009:2022 mit Städtenamen: Linas Nutzer:innen kennen die klassischen Namen, und beim Vorlesen zählt sofortiges Verstehen mehr als die aktuelle Normfassung.

**Dateien:** `core/contacts/ContactRepository.kt` (+`ContactSource`), `core/contacts/FuzzyContactMatcher.kt` (Konstruktor nimmt Interface), `core/text/GermanSpelling.kt` (neu), `test/.../FuzzyContactMatcherTest.kt` (neu), `test/.../GermanSpellingTest.kt` (neu), `PROXY-SPEC.md` (neu), TODO.md

**Offen:** Der Test für `RssFeedRepository` sieht nach einer schnellen Ergänzung aus, ist aber keine: Die Klasse nutzt `XmlPullParser` (Android-API, in JVM-Tests nicht vorhanden) und mischt Netzabruf mit Parsing – erst denselben Umbau wie bei `DaisyParser` (ADR-019). Steht jetzt mit dieser Einschränkung im TODO. Die offenen Fragen aus PROXY-SPEC.md sind unentschieden, allen voran Streaming (Zeit bis zum ersten Wort ist bei einer Sprachassistentin das, was als Geschwindigkeit erlebt wird).

**Testlage:** 91 Tests, alle grün.

---

## [2026-07-21] Claude-Zugang, Kostenmodell und Modell-Routing entschieden

**Was:** Drei Architekturentscheidungen dokumentiert, kein Code geändert.
**ADR-020:** Proxy statt einkompiliertem API-Key – die App authentifiziert sich mit einem widerrufbaren Gerätetoken, vergeben über einen gesprochenen Pairing-Code.
**ADR-021:** Freikontingent pro Gerät aus Spenden/Förderung, 1:1-Kostenweitergabe ohne Marge erst darüber. Zahlungsdaten nie in der App.
**ADR-022:** Gestuftes Modell-Routing (Haiku für kurze Turns, Sonnet 5 nur für echte Gespräche, lokale Ebene 2 reaktivieren, Websuche restriktiver).

**Warum:** ADR-017 ließ offen, wie ein ausgeliefertes Gerät an Claude-Zugang kommt. Ein Key in einer verteilten APK ist kompromittiert; der naheliegende Ausweg „Nutzer meldet sich mit eigenem Claude-Konto an" ist von Anthropic seit Februar 2026 untersagt; und „Bring your own key" scheitert an der Zielgruppe – ein 108-Zeichen-Key ist weder diktierbar noch buchstabierbar. Dazu kam die Skalierungsrechnung: bei 3–12 €/Nutzer/Monat sind 100 Nutzer spendenfinanzierbar, 10.000 nicht.

**Dateien:** DECISIONS.md (ADR-020 bis ADR-022), TODO.md (neuer Abschnitt „Verteilung", LlmIntentResolver-Status korrigiert), WARTUNG.md (Einwilligung Punkt 5: Weg über einen vorgeschalteten Server), CHANGELOG.md

**Offen:** Die Kostenschätzung 3–12 € ist ungemessen und enthält die Websuche nicht – `WebSearchTool20260209` wird pro Suche abgerechnet, nicht über Tokens, und ist möglicherweise der größte Einzelposten. Erster Schritt ist deshalb eine Messung, keine Optimierung. Kontingentgrenze erst nach dem Feldtest festlegen. Steuerliche Prüfung (Zweckbetrieb §68 Nr. 4 AO, Umsatzsteuer) steht aus; der Proxy selbst existiert noch nicht.

---

## [2026-07-20] Projektseite auf aktuellen Funktionsstand

**Was:** `docs/index.html` (GitHub Pages) nachgezogen – die Seite hing drei Meilensteine zurück. Neu in „Was Lina kann": Erinnerungen und Wecker, Post/Briefe vorlesen, DAISY-Hörbücher mit Kapitelnavigation. Dazu zwei neue Abschnitte: **„Was offline läuft – und was nicht"** (welche Funktion braucht Internet, was verlässt das Gerät, dass ohne API-Schlüssel alles andere weiterläuft) und **„Stand der Entwicklung"** (Feldtest-Phase, DAISY am Gerät noch unbestätigt, kein fertiges APK). Eigener Abschnitt zu den Blindenhörbüchereien als Alleinstellungsmerkmal. Zwei Sprachbeispiele ergänzt, Link auf CONTRIBUTING.md.

**Warum:** Die Seite wirbt um Mitwirkende und beschreibt den Träger – sie darf weder hinter dem Stand zurückbleiben noch mehr versprechen, als der Code hält. Dass Dokumentfotos an eine Cloud gehen, gehört bei einer Zielgruppe, die Post vorlesen lässt, sichtbar auf die Seite und nicht ins Kleingedruckte.

**Dateien:** docs/index.html

**Verifiziert:** Lokal gerendert geprüft – mobil (375px) kein horizontales Scrollen, Hochkontrast-Theme und Touch-Target-Größen unverändert, Überschriftenstruktur h1 + 8×h2, `lang="de"`. Kein Release vorhanden, deshalb bewusst kein Download-Versprechen.

**Offen:** Keine englische Fassung. Sprachbeispiele sind erfunden (keine echten Kontakte).

---

## [2026-07-20] CI: GitHub-Actions auf aktuelle Major-Versionen

**Was:** `actions/checkout` v4→v7, `actions/setup-java` v4→v5, `actions/cache` v4→v6, `actions/upload-artifact` v4→v7 in `.github/workflows/build.yml`. Nur die Versionsangaben – keine Parameter geändert.

**Warum:** GitHub warnt bei jedem Lauf, dass diese Actions auf Node.js 20 zielen und bereits zwangsweise auf Node 24 laufen. Erfahrungsgemäß wird aus so einer Warnung irgendwann ein harter Fehler – dann steht die CI still, während gerade etwas anderes ansteht.

**Dateien:** .github/workflows/build.yml

**Verifiziert:** Der CI-Lauf des PRs ist der Test – lokal nicht nachstellbar. Breaking Changes der Major-Sprünge vorher geprüft: durchweg Node-24-Runtime und ESM-Migration, beides mit `ubuntu-latest` unkritisch (GitHub-hosted Runner, keine self-hosted). `checkout@v7` blockiert zusätzlich Fork-Checkouts bei `pull_request_target`/`workflow_run` – wir nutzen `pull_request`, also nicht betroffen. Alle verwendeten Parameter bleiben gültig.

**Offen:** Nichts.

---

## [2026-07-20] DAISY-Hörbücher + Kapitel-Infrastruktur (repariert LibriVox)

**Was:** Kapitel als eigenes Konzept im Hörbuch-Feature. Neu: `Chapter` (Titel, URI, optionaler Zeitbereich), `DaisyParser` (DAISY 2.02: `ncc.html` mit Metadaten und Überschriften in Dokumentreihenfolge, SMIL mit `clip-begin`/`clip-end` in allen gängigen Schreibweisen, XHTML-Sanitizing für DOCTYPE und benannte Entities), `DaisyRepository` (erkennt Buchordner an `ncc.html`, löst Kapitel → Audiodatei + Zeitbereich auf, tolerant gegenüber Groß-/Kleinschreibung wie auf gebrannten CDs). `AudiobookPlayer` spielt jetzt eine Kapitel-Playlist statt einer Einzeldatei (`MediaItem.ClippingConfiguration`, wenn sich mehrere DAISY-Kapitel eine MP3 teilen) und meldet Kapitelwechsel. `AudiobookManager` sagt Kapitel an, `PlaybackState` merkt sich Kapitelindex und LibriVox-Feed. Neue Intents `NextChapter`/`PreviousChapter`/`GoToChapter`/`ListChapters` samt Sprachbefehlen („nächstes Kapitel", „ein Kapitel zurück", „Kapitel drei", „welche Kapitel gibt es"). Zahlwörter liegen jetzt gemeinsam in `core/text/GermanNumbers.kt`.

**Warum:** DAISY ist das Format der Blindenhörbüchereien (Norddeutsche Hörbücherei ~50.000 Titel, WBH Münster) und laut IDEEN.md die Lücke mit dem höchsten Nutzen – aktive quelloffene Android-Player gibt es praktisch nicht. Ein Hörbuch ohne Kapitelnavigation ist für einen blinden Nutzer außerdem kaum bedienbar: ohne Sprung bleibt nur Spulen.

**Behobene Fehler:**
- **LibriVox brach nach dem ersten Abschnitt ab:** `resolveLibrivoxStreamUrl` gab nur `chapters.first().url` zurück, der Player kannte nur eine Datei. Ein Roman mit 30 MP3s endete kommentarlos nach Kapitel 1. Jetzt wird die volle Kapitelliste gespielt; nach einem Neustart wird sie über den gespeicherten RSS-Feed neu geholt.
- **„ruf mal Boris an" ergab den Kontakt „mal boris"** – Füllwörter landeten im Namen (von den neuen Tests gefunden).
- **`sachtext` nahm die Zeitangabe mit:** „erinnere mich an den Arzt um zehn" wurde zu „an den Arzt um zehn". Spezifisches Muster läuft jetzt vor dem allgemeinen.
- **„nächstes Kapitel" landete bei den Nachrichten** (`NextNews` greift auf „nächste"). Kapitelbefehle werden vorrangig behandelt.

**Dateien:** feature/audiobook/{Chapter,DaisyParser,DaisyRepository}.kt (neu), AudiobookPlayer.kt, AudiobookLibrary.kt, AudiobookManager.kt, PlaybackStateStore.kt, core/text/GermanNumbers.kt (neu), ResolvedIntent.kt, LocalCommandResolver.kt, GermanTimeParser.kt, LauncherActivity.kt

**Verifiziert:** `assembleDebug` und `testDebugUnitTest` grün (58 Tests). DAISY-Parsing über Testfixtures abgedeckt.

**Offen:** Noch nicht mit einem echten DAISY-Buch der Hörbücherei am Gerät getestet – Struktur variiert je Produktionsstelle. LibriVox-Mehrkapitel-Wiedergabe am Tablet gegenprüfen. Mitgliedschaft/Ausleihe der Hörbücherei ist nicht Teil dieser Änderung (Bücher müssen manuell in den Audiobooks-Ordner). DAISY 3 / EPUB3-Audio nicht unterstützt.

---

## [2026-07-20] Unit-Tests für Parser + Intent-Erkennung, CI prüft sie

**Was:** Test-Sourceset `app/src/test/` mit JUnit 4. `GermanTimeParserTest` (relative Zeiten, „halb acht", „Viertel vor/nach", Abend-Marker, vergangene Uhrzeit → morgen, tägliche Erinnerungen, Sachtext-Extraktion, Nicht-Treffer), `LocalCommandResolverTest` (Anrufe, SMS, Dokument, Nachrichten, Hörbuch, Kapitel, Erinnerungen, Stopp – Schwerpunkt auf den **Abgrenzungen** zwischen Mustern, die sich Wörter teilen: „lies meine Nachrichten" vs. Dokument, „nächstes Kapitel" vs. Meldung, „ein Kapitel zurück" vs. Zurückspulen), `DaisyParserTest` (ncc/SMIL/Clock-Values/Sanitizing). CI-Workflow läuft `testDebugUnitTest` und sichert den Bericht als Artefakt.

**Warum:** Die Intent-Erkennung ist eine Kette von Regex-Mustern, in der die Reihenfolge entscheidet – solche Regressionen fallen ohne Test erst beim blinden Nutzer auf, der dann eine falsche Aktion ausgelöst bekommt. Der offene Punkt aus dem CI-Eintrag ist damit erledigt.

**Dateien:** app/src/test/kotlin/... (3 neu), app/build.gradle.kts, .github/workflows/build.yml

**Verifiziert:** 58 Tests, alle grün. Drei echte Fehler gefunden (siehe DAISY-Eintrag).

**Offen:** Keine Tests für `AudiobookManager`/`ReminderScheduler` – die brauchen Robolectric oder Instrumentierung. Der Befehl „weiter" ist zwischen `NextNews` und `ResumeAudiobook` mehrdeutig und wird derzeit als Meldungs-Weiterschaltung aufgelöst; bewusst nicht per Test festgeschrieben, weil das kontextabhängig entschieden werden sollte.

---

## [2026-07-20] Erinnerungen & Wecker (offline) + IDEEN.md

**Was:** Neues Feature `feature/reminder/` – gesprochene Erinnerungen, komplett offline: `Reminder` (Datenmodell + gesprochene Zeitangabe „morgen um 7 Uhr 30"), `ReminderStore` (SharedPreferences/JSON), `ReminderScheduler` (AlarmManager `setAlarmClock`, wirkt auch im Doze-Modus; Fallback `setAndAllowWhileIdle` ohne Exact-Recht), `ReminderReceiver` (Ansage per Broadcast an die Activity + Benachrichtigung als hörbarer Rückfall; tägliche Erinnerungen planen sich selbst neu), `GermanTimeParser` (deutsche Zeitangaben ohne Cloud: „in zwanzig Minuten", „morgen um halb acht", „Viertel nach sieben", „jeden Tag um acht", Zahlwörter, Abend-Marker), `ReminderManager` (Ansagen, Liste, Löschen). Intents `SetReminder`/`SetReminderAt`/`ListReminders`/`ClearReminders`; Claude-Tools `erinnerung_anlegen` (mit ISO-Zeitpunkt) und `erinnerungen_vorlesen` für verstümmelte Eingaben. BootReceiver setzt Alarme nach Neustart neu. Manifest: POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM. Dazu `IDEEN.md` im Repo: Feature-Backlog mit Nutzen/Aufwand und nutzbaren freien Bausteinen (Recherche-Ergebnis).

**Warum:** Nutzerwunsch; bei älteren Nutzer:innen erfahrungsgemäß eines der meistgenutzten Features (Termine, Medikamente). Offline-Umsetzung wahrt das Leitprinzip „Offline where possible".

**Dateien:** feature/reminder/*.kt (5 neu), ResolvedIntent.kt, LocalCommandResolver.kt, ClaudeConversation.kt, LauncherActivity.kt, BootReceiver.kt, AndroidManifest.xml, IDEEN.md (neu)

**Verifiziert:** Auf dem Gerät – „in zwei Minuten" → korrekt geplant und **pünktlich ausgelöst** (Ansage durch Lina); „morgen um halb acht" → 7:30; „jeden Tag um acht" → täglich.

**Offen:** Neustart-Rescheduling am Gerät prüfen. Einzelne Erinnerung per Sprache löschen (aktuell nur alle). Exact-Alarm-Recht auf dem Zielgerät kontrollieren.

---

## [2026-07-20] CI-Build + PR-Template für Contributions

**Was:** GitHub-Actions-Workflow `.github/workflows/build.yml`: baut bei jedem PR und Push auf main das Debug-APK (JDK 17, Gradle-Cache) und läuft zusätzlich Android-Lint (nicht blockierend, Bericht als Artefakt). Damit CI nicht ~400 MB Sprachmodelle laden muss, hat `scripts/download-models.sh` jetzt den Modus `--libs-only`: lädt nur die sherpa-onnx-AAR, die als Datei-Dependency zum Kompilieren zwingend nötig ist. Verifiziert, dass der Build ohne Piper-/Whisper-Assets durchläuft. Dazu `.github/pull_request_template.md` mit Checkliste, die die Leitprinzipien einfordert (gesprochene Rückmeldung, ohne Sehen bedienbar, Interfaces beachtet, Offline-Verhalten, CHANGELOG/ADR, keine personenbezogenen Daten). CONTRIBUTING.md um den schlanken Build-Weg ergänzt.

**Warum:** Das Repo ist öffentlich und soll Beiträge bekommen – ohne CI bleibt ungeprüft, ob ein PR überhaupt baut. Die Checkliste hält die Prinzipien präsent, die bei einer Sprachassistenz für blinde Menschen leicht untergehen.

**Dateien:** .github/workflows/build.yml (neu), .github/pull_request_template.md (neu), scripts/download-models.sh, CONTRIBUTING.md

**Offen:** Keine Tests im Projekt – der Workflow prüft nur Kompilierbarkeit und Lint. Unit-Tests für Parser (GermanTimeParser, LocalCommandResolver, DAISY) wären ein lohnender nächster Schritt.


---

## [2026-07-20] Dokument-Vorlesen per Kamera + Vision (Meilenstein 3)

**Was:** Lina fotografiert auf Zuruf ein Dokument (Post, Brief, Zeitung, Magazinseite), das im festen Kreppband-Rahmen vor dem stationären Tablet liegt, und liest vor, was wichtig ist. Neue Bausteine: `feature/document/DocumentCamera.kt` (CameraX, Rückkamera, headless ohne Preview, eigener LifecycleOwner – siehe ADR-018 –, Bild auf 2000px/JPEG-Q85 herunterskaliert, EXIF-Rotation korrigiert); `ClaudeConversation.readDocument(bytes, verbatim)` (zustandsloser Vision-Aufruf mit Image+Text-Block, eigener Dokument-System-Prompt, `maxTokens` 1024, keine Tools/History); Intent `ReadDocument` (lokale Regex „lies mir die Post vor", „was steht da" u.a. – nach der SMS-Erkennung eingeordnet, damit „lies meine Nachrichten" weiter SMS trifft) plus Claude-Tool `dokument_vorlesen` für STT-Verhörer; Orchestrierung `readDocumentAloud()` + Dokument-Folgefenster („ja/alles" → ganzer Text, „wiederhole", „nochmal" → neues Foto, sonst stiller Rückzug). CAMERA-Permission + `uses-feature`, CameraX-Deps, CAMERA im Onboarding-Berechtigungssatz. Debug-Befehl „testfoto" speichert ein Bild zum einmaligen Ausrichten des Rahmens.

**Warum:** Größter Alltagswunsch (Post selbstständig lesen) und Förder-Meilenstein 3. Der fixierte Rahmen löst das Ausrichtungsproblem, das Kameranutzung für Blinde sonst unbrauchbar macht.

**Dateien:** feature/document/DocumentCamera.kt (neu), ClaudeConversation.kt, ResolvedIntent.kt, LocalCommandResolver.kt, LauncherActivity.kt, PermissionsGuide.kt, AndroidManifest.xml, app/build.gradle.kts

**Verifiziert:** Auf dem Gerät end-to-end – Testfoto scharf und formatfüllend (auch bei schwachem Licht), „lies mir die Post vor" → Foto nach ~3s, Vision-Auswertung ~10s, Ansage beginnt korrekt mit Art und Absender des Dokuments.

**Offen:** Gesamtdauer ~14s (Earcons überbrücken; ggf. Bild kleiner oder Antwort streamen). „Alles vorlesen"-Pfad und Mehrseiten-Ablauf mit echtem Nutzer testen. On-Device-OCR als Offline-Alternative im Backlog.

---

## [2026-07-19] Weckwort v3: Nachtraining mit Testnutzer-Stimme

**Was:** Die fünf Weckwort-Aufnahmen aus der Ersteinrichtung des Testnutzers ins Training aufgenommen (`user_positive`, 40-fach übergewichtet), seine fünf Befehls-Aufnahmen als Hard Negatives. Bisherige Entwickler-Clips bleiben drin (beide Stimmen sollen wecken). Pipeline unverändert (`training/`), Validierung: Recall 0,886–0,93 bei Schwelle 0,3 über beide Stimmen, Fehlalarmrate 0,27 %/Fenster. Modell auf dem Tablet deployed.

**Warum:** Weckwort v2 war nur auf die Entwicklerstimme nachtrainiert; beim Testnutzer soll die Erkennung genauso zuverlässig sein (Besuch #2 nächste Woche).

**Dateien:** training/data (lokal, gitignored), assets/openwakeword/hey_lina_v1.onnx (gitignored)

**Offen:** 5/5-Verifikation mit der Entwicklerstimme jetzt; mit der Testnutzer-Stimme erst vor Ort bei Besuch #2. Bei Fehlalarmen im Alltag: Schwelle in OpenWakeWordEngine anheben.

---

## [2026-07-19] Onboarding-Politur + Uhrzeit-Intent + pause_turn-Härtung

**Was:** (1) Onboarding: Region als sechste Frage (füllt `user_region` für Wetter/Regionalnachrichten – ersetzt den manuellen Pref-Eingriff); Whisper-Stille-Erkennung während der Fragephase 1200→1800 ms (`WhisperSttEngine.endSilenceMs`, ältere Nutzer machen Denkpausen – drei von fünf Antworten waren beim Feldtest abgeschnitten); Pause nach Ansagen 500→800 ms (Ansage-Tail nicht mehr in der Aufnahme); Debug-Aufnahme-Ansage generisch + Startton (dient jetzt auch Zukunftsbefehl-Aufnahmen). (2) Neuer Offline-Intent `Time`: „Wie spät ist es?" → lokale Uhrzeit-Ansage, ohne Cloud. (3) `pause_turn` bei Websuche-Läufen wird erkannt und ehrlich beantwortet („Die Suche dauert gerade zu lange…") statt leerer Antwort; kein voller Resume (Java-SDK-Blockrekonstruktion unverhältnismäßig).

**Warum:** Befunde aus Feldtest-Besuch #1; Uhrzeit stand auf der Zukunftsbefehl-Liste des Testnutzers.

**Dateien:** VoiceOnboarding.kt, WhisperSttEngine.kt, LauncherActivity.kt, ResolvedIntent.kt, LocalCommandResolver.kt, ClaudeConversation.kt

**Offen:** Onboarding-Durchlauf mit den neuen Fenstern am Gerät testen (vor Besuch #2); Uhrzeit-Ansage auf dem Gerät verifiziert.

---

## [2026-07-19] Websuche: Wetter + Regional-/Themen-Nachrichten über Claude

**Was:** Server-Tool `web_search_20260209` (max. 3 Suchen/Anfrage) in `ClaudeConversation` aktiviert. Neuer Persona-Parameter `region` (Pref `user_region`, wird lokal auf dem Gerät gesetzt): Wetter- und Regionalfragen ohne Ortsangabe beziehen sich darauf. Prompt: Aktuelles über Websuche beantworten, Nachrichten als 2–3 vorlesbare Meldungen ohne URLs; `nachrichten_vorlesen`-Tool nur noch für die Standard-Schlagzeilen. Resolver entschärft: qualifizierte Nachrichtenfragen („… aus X", „… zur Politik") gehen an Ebene 2 statt an den RSS-Reader; Hörbuchsuche-Muster „gibt es …" braucht jetzt Hörbuch-Bezug (fraß vorher beliebige Fragen). Debug-Logging der Antwortblöcke. Auf dem Gerät verifiziert: Wetterbericht (Temperatur, Regen, Windwarnung) und Regionalnachrichten für die Testregion sauber.

**Warum:** Nutzerwunsch vor Ort: Wetterfragen + Regionalnachrichten (RSS-Feed unbefriedigend). Websuche deckt beides ohne eigene Wetter-/News-Infrastruktur.

**Dateien:** ClaudeConversation.kt, LauncherActivity.kt, LocalCommandResolver.kt, WakeWordService.kt

**Offen:** `pause_turn` bei langen Suchläufen nicht behandelt (bisher nicht aufgetreten; maxUses 3 hält Läufe kurz). Region wird noch nicht im Onboarding erfragt (To-do). RSS-Pfad langfristig durch Claude-kuratierte Nachrichten ersetzen? (ADR wert, nach Feldtest-Erfahrung). Einzelner Claude-Fehlgriff beobachtet (stopp-Tool auf Wetterfrage, nicht reproduzierbar). Zudem Crash-Fix: FGS-Start aus Hintergrund (Bildschirm aus) wird jetzt am Aufrufer abgefangen.

---

## [2026-07-19] Raumgespräch-Schutz: Lina redet nicht mehr dazwischen

**Was:** Folgefenster reagierten auf jedes Gespräch im Raum („ja", „okay" an einen Freund → Lina antwortete oder suchte Kontakte). Drei Maßnahmen: (1) Neues Claude-Werkzeug `gespraech_beenden` + Prompt-Regel: Erkennt Claude eine nicht an Lina gerichtete Eingabe (Raumgespräch, Fernseher), beendet sie still (`LinaReply.End`), die Äußerung fliegt aus dem Dialoggedächtnis. (2) News-Fenster strikt: Nur klare Schlüsselwörter oder lokale Befehle zählen, alles andere schließt still; „ja" aus der Schlüsselwortliste entfernt. (3) Gesprächsfenster routet alles über Claude statt über die Regex-Ebene – die matchte Raumgespräche wie „ich ruf dich später an" als Anruf-Befehl; nur „Stopp" bleibt lokal (Sofortwirkung).

**Warum:** Live-Test: Lina mischte sich in ein Gespräch zwischen Nutzer und Freund ein.

**Dateien:** ClaudeConversation.kt, LauncherActivity.kt

**Offen:** Grenzfälle beobachten (Claude fragte bei „ok, schau" nach, statt zu beenden – Prompt ggf. nachschärfen). Weckwort-Fehlauslösungen im Raumgespräch separat beobachten.

---

## [2026-07-19] Nachrichten-Dialog + schnellere Folgefenster (Nutzerfeedback)

**Was:** (1) Nachrichten sind keine Sackgasse mehr: Nach jedem Teaser öffnet sich ein Zuhörfenster – „mehr" (ganzer Artikel), „nächste" (nächste Schlagzeile) oder „stopp" reichen, ohne neues Weckwort; einmaliger gesprochener Hinweis beim ersten Mal; gilt auch, wenn Claude die Nachrichten erkannt hat (Do-Intent). Redundante „Nachrichten werden geladen"-Ansage entfernt (landete in der TTS-Queue *nach* den Meldungen). (2) Neues `isBusySpeaking()` ohne den 2s-Weckwort-Echo-Nachlauf – Folgefenster (Gespräch + News) und Onboarding reagieren ~2s schneller pro Zug. (3) Echo-Fix: `playing`-Flag gilt ab Queue-Entnahme, sonst nahm das Folgefenster Linas eigene Antwort während der Synthese auf. (4) System-Prompt: Interessen/Kontakte nicht mehr ungefragt ausspielen, keine ungefragten Zusatzangebote (Live-Feedback).

**Warum:** Direkte Testrunde mit Nutzer: „zu träge", „News enden im Nichts", „Lina hört sich selbst", „Personalisierung nervt".

**Dateien:** LauncherActivity.kt, PiperTtsEngine.kt, ClaudeConversation.kt

**Offen:** „weiter" ist doppeldeutig (weiterlesen vs. nächste) – aktuell = weiterlesen; mit Testnutzer beobachten. Vereinzelter Verbindungsfehler bei Claude-Anfrage gesehen (11:09) – beobachten.

---

## [2026-07-19] Crash-Fix: FGS-Race zwischen Wake-Neustart und Gesprächsmodus

**Was:** Absturz `ForegroundServiceDidNotStartInTimeException` behoben: Im Sprachpfad wurde der Weckwort-Neustart immer geplant (2s-Timer), auch wenn die Eingabe an Claude ging – traf Claudes Antwort kurz nach dem Timer ein, rief der Gesprächsmodus `stopService` Millisekunden nach `startForegroundService` → System-Kill. Fix: `processDebugInput()` meldet jetzt zurück, ob Claude (oder das Onboarding) übernimmt – dann wird kein Wake-Neustart geplant; `wakeResumeRunnable` prüft zusätzlich auf laufendes Onboarding. Auf dem Gerät regressionsgetestet (Claude-Antwort + Gesprächsmodus, App stabil).

**Warum:** Beim Live-Test mit aktivem API-Guthaben reproduzierbar abgestürzt.

**Dateien:** LauncherActivity.kt

---

## [2026-07-19] Gesprächsmodus für freie Konversation

**Was:** Nach einer freien Claude-Antwort (`Say`) hört Lina direkt weiter zu – heller Ton als Hinweis, kein neues „Hey Lina" nötig. Folgefragen gehen mit Dialoggedächtnis weiter an Claude; Befehle (Stopp, Anrufen, …) oder ~5s Stille beenden das Gespräch und reaktivieren das Weckwort. Dazu `resumeWakeWordListening()` cancellbar gemacht (Race: Wake-Service startete per Timer neu, während Claude noch rechnete). Claude-`maxTokens` 300→500 (abgeschnittene Sätze beim Vorlesen vermeiden). Fehleransagen nach Typ differenziert (Rate-Limit/Dienst/Netz, typisierte SDK-Exceptions).

**Warum:** Freie Konversation ist laut Nutzer-Priorität der Kern – ein Gespräch, bei dem jede Nachfrage ein Weckwort braucht, ist keins.

**Dateien:** LauncherActivity.kt, ClaudeConversation.kt

**Offen:** Ende-zu-Ende-Test mit aktivem API-Guthaben; Gesprächsmodus-Timing (5s-Stille) mit echtem Nutzer kalibrieren.

---

## [2026-07-19] Zweite Stimme: Thorsten „fröhlich" (CC0)

**Was:** `de_DE-thorsten_emotional-medium` als Stimme 2 in `AVAILABLE_VOICES` (Sprechervariante „amused" = Sprecher-ID 0, passt zum bestehenden `sid = 0`). Download-Script + NOTICE.md ergänzt. Wechsel zur Laufzeit per „Stimme 2" / „nächste Stimme". Auf dem Tablet installiert und verifiziert. APK wächst auf ~545 MB.

**Warum:** Nutzerwunsch (fröhlicher Klang als Option); zudem erste komplett frei lizenzierte Stimme (CC0, Thorsten-Voice) neben der NC-belasteten dii-Stimme.

**Dateien:** PiperTtsEngine.kt, scripts/download-models.sh, NOTICE.md

**Offen:** A/B-Vergleich mit dem Testnutzer (dii vs. thorsten-fröhlich); Default bleibt vorerst dii-high.

---

## [2026-07-18] Gesprochene Ersteinrichtung + Fernwartung (Auslieferungs-Vorbereitung)

**Was:** (1) `VoiceOnboarding` (feature/onboarding): komplett gesprochener Erststart-Flow – 5× Weckwort einsprechen (WAVs für Nachtraining), 5 Kernbefehle nachsprechen, 5 Fragen (Anrede, Nachrichten-Interessen, Bücher, wichtigste Person, Wünsche) per Whisper transkribiert → `answers.json`; Anrede + Interessen fließen automatisch in die Claude-Persona (neuer `interests`-Parameter wird aus SharedPreferences gefüllt, Claude wird nach Einrichtung neu initialisiert). Startet automatisch beim allerersten Start (erst wenn STT bereit), Guards gegen Weckwort-/Mikrofon-Konflikte (onResume, onWakeWordDetected). Debug-Befehle: "einrichtung", "einrichtung zurücksetzen". (2) `WavRecorder` (core/audio) als wiederverwendbare Aufnahme-Utility, `Earcons.go()` als Sprech-Signal. (3) Fernwartung: `scripts/remote.sh` (connect/status/logs/deploy/pull-onboarding/screen via Tailscale+adb) + `WARTUNG.md` mit Einwilligungs-Hinweisen und Übergabe-Checkliste.

**Warum:** Auslieferung an den Testnutzer; Einrichtung ohne sehende Hilfe; Betreuung des Geräts aus der Ferne (Updates einspielen, Aufnahmen abholen, Status prüfen).

**Dateien:** feature/onboarding/VoiceOnboarding.kt (neu), core/audio/WavRecorder.kt (neu), Earcons.kt, LauncherActivity.kt, scripts/remote.sh (neu), WARTUNG.md (neu)

**Offen:** Gerätetest des kompletten Flows (heute); Tailscale-Einrichtung auf Tablet+Mac; Klang/Zeitverhalten der Einrichtung mit echtem Nutzer beobachten. WLAN-adb muss nach Tablet-Reboot ggf. neu aktiviert werden.

---

## [2026-07-18] Earcons: akustische Rückmeldung in Wartezeiten

**Was:** Neue Utility `Earcons` (core/audio): synthetisierte weiche Sinus-Blips mit Hüllkurve über AudioTrack (USAGE_ASSISTANT, fire-and-forget). Zwei Signale: `ack()` (einzelner Blip, 880 Hz) sobald die Sprachaufnahme steht und die ~2s-Whisper-Transkription beginnt – via neuem optionalen Callback `WhisperSttEngine.onSpeechCaptured`; `thinking()` (zwei aufsteigende Blips) beim Start einer Claude-Anfrage in `askClaude()`.

**Warum:** Zwischen Sprechen und Antwort liegen 2–5 s Stille – für blinde Nutzer nicht unterscheidbar von "nicht gehört". Der Ton bestätigt sofort: Lina arbeitet. (Plan Etappe 1)

**Dateien:** core/audio/Earcons.kt (neu), WhisperSttEngine.kt, LauncherActivity.kt

**Offen:** Klang auf dem Tablet-Lautsprecher prüfen (Lautstärke/Charakter), ggf. Frequenzen anpassen. Auf dem Gerät testen, dass der Ack-Ton nicht in eine laufende Aufnahme zurückkoppelt (Aufnahme ist beim Abspielen bereits beendet – sollte sicher sein).

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

**Was:** Laufzeit-Stimmwechsler in `PiperTtsEngine` (`AVAILABLE_VOICES`, `switchVoice()`, thread-sicher via engineLock) + Sprach-/Debug-Befehl "Stimme <n>" / "nächste Stimme" in `LauncherActivity`. Sechs Stimmen auf dem Gerät verglichen (ramona/kerstin/eva_k low, thorsten medium, dii/miro high). Bugfix: AudioTrack-Puffergröße auf Framegröße (4 Bytes) gerundet – 22.05kHz-Modelle crashten sonst mit "Invalid audio buffer size". Gewählt: **de_DE-dii-high** (OpenVoiceOS) als Default, übrige Modelle aus Assets entfernt. Gemeinnützige, nicht-kommerzielle Trägerschaft in CLAUDE.md dokumentiert – macht die CC-BY-NC-SA-Lizenz der Stimme nutzbar (ADR-016).

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
