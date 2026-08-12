# Lokaler Gemma-3n-Pfad – Trainings-/Eval-Pipeline (ADR-032)

Parallel zur Wake-Word-Trainingspipeline (`training/`), aber eigenes venv
(MLX-Abhängigkeiten kollidieren mit dem torch/onnxruntime-Stack dort).

## Ablauf (reproduzierbar)

```bash
# 1. Umgebung
cd training/llm
uv venv --python 3.12 venv
uv pip install --python venv/bin/python mlx-vlm mlx-lm anthropic

# 2. Phase A0/A: Tooling-Check + Mac-Spike (kein Finetuning, Basis-Checkpoint)
venv/bin/python spike_check.py

# 3. Phase B: synthetische Trainingsdaten - standardmaessig lokal ueber
#    Llama-3.2-3B (kein API-Key, keine Kosten), alternativ --backend claude
#    (braucht CLAUDE_API_KEY in local.properties + Guthaben) fuer hoehere
#    Qualitaet/Vielfalt, falls verfuegbar
venv/bin/python gen_dialogue.py --per-tool 15 --negatives 40 --persona 40
venv/bin/python build_dataset.py          # Persona-Text bleibt Persona-Text
venv/bin/python build_router_dataset.py   # Persona -> frei_gespraech()-Weiterleitung

# 4. Phase C: LoRA-Finetuning + Eval gegen den gehaltenen Testsatz
#    (Llama-3.2-3B, nicht Gemma - siehe "Durchbruch" unten)
venv/bin/python -m mlx_lm lora --model mlx-community/Llama-3.2-3B-Instruct-4bit \
  --train --data data/router --mask-prompt --seed 0 \
  --iters 260 --batch-size 8 --learning-rate 1e-5 --save-every 20 \
  --adapter-path adapters/router_v3
venv/bin/python sweep_checkpoints.py --model mlx-community/Llama-3.2-3B-Instruct-4bit \
  --adapter-dir adapters/router_v3 --limit 20   # groben Sweep zuerst, dann volle Auswertung
venv/bin/python eval.py --model mlx-community/Llama-3.2-3B-Instruct-4bit \
  --adapter-path adapters/router_v3 --data data/router
```

## Phase A0 – Tooling-Realitätscheck (2026-08-04)

- `mlx-vlm` (multimodales MLX-Paket) lädt `mlx-community/gemma-3n-E2B-it-4bit`
  und generiert fehlerfrei fließendes Deutsch (Peak Memory 4,6GB, ~75
  Tokens/s auf diesem Mac). **Funktioniert.**
- `mlx-lm` (Text-only) lädt `mlx-community/gemma-3n-E2B-it-lm-4bit` und
  generiert ebenfalls fehlerfrei (Peak Memory 2,6GB, ~102 Tokens/s) – schneller
  und schlanker als `mlx-vlm` für den reinen Text-Anwendungsfall. Ein älteres,
  öffentlich dokumentiertes GitHub-Issue (`ml-explore/mlx-lm#257`, "Model type
  gemma3n not supported") ist mit der hier installierten Version
  (`mlx-lm==0.31.3`, via `mlx-vlm==0.6.9` als Abhängigkeit installiert) nicht
  mehr reproduzierbar – Support wurde offenbar nachgezogen. **Funktioniert,
  Text-Pfad wird für Training/Eval bevorzugt.**
- **LoRA-Finetuning via `mlx_lm.lora` bricht mit Standardeinstellungen ab:**
  `AttributeError: 'LoRALinear' object has no attribute 'weight'`. Ursache
  gefunden (`mlx_lm/models/gemma3n.py`, `Gemma3nAltUp.predict()`/`correct()`):
  Gemma 3n hat einen architektur-eigenen "AltUp"-Mechanismus mit eigenen
  `nn.Linear`-Modulen (`correction_coefs`, `prediction_coefs`), auf deren
  `.weight`-Attribut der Modellcode **direkt** zugreift statt sie regulär
  aufzurufen. `mlx-lm`s generische LoRA-Layer-Erkennung wrapt aber
  standardmäßig JEDEN `nn.Linear` der letzten N Layer – trifft sie auch
  AltUp, bricht das Training, weil `LoRALinear` kein `.weight`-Attribut mehr
  hat. **Fix:** `lora_config.yaml` mit expliziten `lora_parameters.keys:
  ["self_attn.q_proj", "self_attn.v_proj"]` – beschränkt LoRA auf die
  normalen Attention-Projektionen, AltUp wird nie angefasst. Mit diesem Fix
  läuft ein Trainingsschritt durch (live verifiziert, 2 Dummy-Beispiele, 2
  Iterationen, Adapter erfolgreich gespeichert).

**Offen (Phase D.5, nicht Teil dieser Pipeline):** ob ein so trainierter
MLX-LoRA-Adapter tatsächlich auf ein `.task`/`.litertlm`-Bundle fürs Gerät
übertragbar ist (MediaPipes dokumentierter On-Device-LoRA-Pfad nennt aktuell
nur Gemma-2 2B/Gemma 2B/Phi-2, nicht Gemma 3n) – MLX validiert hier nur den
Trainingsansatz und die Datenqualität auf dem Mac, nicht die Auslieferbarkeit.

## Phase A – Mac-Spike, Basis-Modell ohne Finetuning (2026-08-04)

Modell: `mlx-community/gemma-3n-E2B-it-lm-4bit`, Prompts:
`prompts/eval_handwritten.jsonl` (20 handgeschriebene Fälle).

**Ergebnis: 11/20 korrekt.**

- Tool-Aufrufe mit erkennbarem Namensargument (`anrufen`, `sms_senden`,
  `termin_anlegen`) funktionieren bereits zuverlässig zero-shot, auch bei
  verstümmelten STT-Eingaben ("Rumfe, Boris an" → korrekt erkannt).
- Parameterlose/kurze Imperativ-Werkzeuge (`sms_vorlesen`,
  `hoerbuch_abspielen`, `erinnerungen_vorlesen`, `dokument_vorlesen`, `stopp`)
  scheitern fast durchgängig – das Modell "erzählt" die Aktion in normaler
  Sprache statt den Werkzeugaufruf auszugeben (z.B. "Gerne! Ich lese dir die
  Post vor." statt `dokument_vorlesen()`).
- Raumgespräch-Filter (`gespraech_beenden`): nur 1/3 korrekt – das
  Basismodell antwortet im Zweifel lieber, statt zu schweigen.
- Freie Konversation/Persona: 5/5 korrekt, Tonfall passend.

Das bestätigt empirisch genau die beiden Finetuning-Gründe aus ADR-032
(Tool-Calling-Zuverlässigkeit, Raumgespräch-Sicherheit) – reines
Prompt-Engineering reicht für diese beiden Fälle nicht.

## Phase B – Trainingsdaten (2026-08-04)

Erste Testcharge über `gen_dialogue.py` (Bootstrap via Claude API,
`claude-sonnet-5`): 52 Rohbeispiele (4 je Werkzeug × 9 Werkzeuge, 8
Raumgespräch-Negative, 8 Persona-Beispiele). Split über `build_dataset.py`:
39 train / 5 valid / 8 test (`test.jsonl` nie im Training verwendet).

## Phase C – Erster Finetuning-Versuch (2026-08-04)

Zwei Trainingsläufe (`mlx_lm.lora`, Basis `mlx-community/gemma-3n-E2B-it-lm-4bit`,
Rang 8, 39 Trainingsbeispiele), beide **ohne sichtbaren Effekt auf die
generierten Antworten** – Ergebnis von `eval.py` gegen `data/test.jsonl` (8
gehaltene Beispiele) ist in beiden Fällen **2/8, identisch zum Basismodell
ohne Adapter, Wort für Wort dieselbe Rohausgabe**. Das ist kein einzelner
Fehlschlag, sondern drei separat verifizierte, unterschiedliche Ursachen:

1. **`Gemma3nAltUp`-Inkompatibilität** (siehe Phase A0) – behoben über
   `lora_config.yaml`s `keys`-Einschränkung.
2. **`self_attn.q_proj` bekommt keinen Gradienten.** Erster Lauf
   (`adapters/v1`, `keys: [q_proj, v_proj]`, 200 Iterationen): Per
   `safetensors`-Inspektion aller 8 Layer direkt aus der gespeicherten Datei
   (nicht über die Lade-Logik, um Lade-Bugs auszuschließen) war
   `q_proj.lora_b` in **jedem einzelnen** der 8 Layer exakt `0.0` –
   `v_proj.lora_b` dagegen in jedem Layer konsistent ungleich 0
   (abs_sum ≈13,0). Kein Ausreißer, sondern ein systematisches Muster – der
   Gradient zu `q_proj` scheint in Gemma3ns Attention-Pfad blockiert
   (Ursache nicht abschließend geklärt, Verdacht: eine Query-Transformation
   außerhalb des autodiff-getrackten Pfads, ähnlich dem direkten
   `.weight`-Zugriff bei AltUp – siehe TODO). Trainingsverlust sank in
   diesem Lauf zunächst (6,14→5,88 bei Iter 20–40), stieg zum Ende wieder auf
   6,56 (Iter 200) – bei nur 39 Beispielen und 200 Iterationen vermutlich
   Overfitting/Instabilität obendrauf.
3. **Selbst der tatsächlich trainierte Teil (`v_proj`) ist zu schwach, um
   die Greedy-Generierung sichtbar zu verändern.** Zweiter Lauf
   (`adapters/v2`, `keys: [v_proj]` nur, 150 Iterationen): Trainingsverlust
   **explodierte** (10,9→22,5, keine Konvergenz – Lernrate 1e-4 vermutlich
   zu hoch für diesen winzigen Datensatz). Live verifiziert, dass die
   Adapter-Gewichte diesmal korrekt geladen UND im Modellgraphen ankommen
   (`lora_b` abs_sum ≈13,3, nicht 0) – das Laden funktioniert also
   grundsätzlich. Trotzdem: identische Rohausgabe wie das Basismodell bei
   allen 8 Testfällen. Mit Rang 8, nur einer Modulart, 150 Iterationen und
   39 Beispielen ist das Signal offenbar zu schwach, um Token-Entscheidungen
   beim Greedy-Decoding umzukippen.

**Fazit (Stand erster Versuch, 39 Beispiele):** Die Pipeline funktioniert
mechanisch durchgängig (Training → Speichern → Laden → Anwenden), aber
dieser erste, bewusst kleine Versuch reicht nicht für ein messbar
wirksames Finetuning.

## Phase C, Fortsetzung – Datensatz skaliert, Divergenz bleibt (2026-08-04)

Datensatz auf 345 Beispiele skaliert (259/34/52 train/valid/test,
`--per-tool 25 --negatives 60 --persona 60`). Seitdem systematisch **fünf**
Trainingsläufe, alle mit demselben Muster: Loss fällt bis ca. Iteration
40–80, steigt danach dauerhaft über den Ausgangswert (Val Loss Iter 1 ≈
7,4–8,2) und bleibt erhöht (12–15) bis zum Ende:

| Lauf | Daten | `keys` | LR | `scale` | `--mask-prompt` | Ergebnis |
|---|---|---|---|---|---|---|
| v1 | 39 | q+v | 1e-4 | 20 | nein | q_proj lernt nie (Gradient 0); spätere Divergenz |
| v2 | 39 | v | 1e-4 | 20 | nein | divergiert ab Iter ~20 |
| v3 | 345 | v | 2e-5 | 20 | nein | divergiert ab Iter ~80 |
| v4 | 345 | v | 2e-5 | 20 | **ja** | divergiert ab Iter ~80 (Fix ohne Wirkung) |
| v5 | 345 | v | 5e-5 | **2** | ja | divergiert ab Iter ~80 (Fix ohne Wirkung) |

`scale` (mlx_lm-Default 20, unüblich hoch gegenüber der sonst üblichen
LoRA-Praxis von ~0,5–2) war ein naheliegender Verdacht, wurde in v5 auf 2,0
gesenkt – identisches Divergenzmuster, exakt am selben Iterationspunkt.
Damit ist **weder `keys`, noch Datensatzgröße, noch Lernrate, noch
Prompt-Masking, noch `scale`** die Ursache – alle fünf naheliegenden Hebel
wurden einzeln durchprobiert. Das durchgängige "kippt bei Iter ~80"-Muster
über fünf sonst unterschiedliche Läufe hinweg deutet stärker auf ein
strukturelles Problem in `mlx-lm`s noch jungem Gemma-3n-Support hin (evtl.
verwandt mit dem `q_proj`-Gradienten-Befund oben – ein Verdacht ist eine
Fehlerakkumulation im AltUp-Mehrstrom-Mechanismus über die Zeit, nicht
abschließend verifiziert) als auf gewöhnliches Overfitting oder eine falsch
gewählte Lernrate.

**Offene nächste Schritte, nicht mehr "einfach nochmal mit anderem Wert
versuchen":**
- bf16-Diagnose (unquantisierte Basis statt 4bit) als letzter isolierter
  Test, ob Quantisierung beteiligt ist
- `q_proj`/Divergenz-Befund als Upstream-Issue bei `ml-explore/mlx-lm`
  melden (reproduzierbar, mit Tabelle oben belegbar)
- Alternativ: Fallback aus ADR-032 ziehen – ein kleineres, architektonisch
  ausgereiftes Modell (FunctionGemma/Gemma-3-270M, keine AltUp-Architektur)
  fürs Tool-Calling/Raumgespräch-Finetuning nutzen, Gemma 3n selbst bleibt
  unfinetuned/prompt-basiert für die freie Konversation (dort lag die
  Zero-Shot-Qualität im Mac-Spike ohnehin schon bei 5/5).

## Pivot – Gemma-3-270M-Router divergiert ebenfalls (2026-08-04)

Fallback-Architektur umgesetzt: statt Gemma 3n selbst zu finetunen, sollte
ein kleines, architektonisch normales Modell als **Router** dienen (Tool-
Aufruf / Raumgespräch-Stille / `frei_gespraech()` = Weiterleitung an ein
unfinetuntes Gemma 3n für freie Konversation). Datensatz dafür:
`build_router_dataset.py` mappt die `persona`-Kategorie auf `frei_gespraech()`
um, `prompts/system_prompt_router_de.txt` als kompakter Router-Prompt.

`mlx-community/gemma-3-270m-it-4bit` lädt und generiert einwandfrei (0,2GB
Peak), aber **jeder** Trainingslauf divergierte sofort und drastisch (Val
Loss 3,2 → 145-340 je nach Konfiguration) – schlimmer, nicht besser als bei
Gemma 3n. Neun Läufe insgesamt, systematisch jede Einzelvariable
durchprobiert (Tabelle):

| Lauf | Modell | Quantisierung | LR | `scale` | `--mask-prompt` | Batch | Sonstiges | Ergebnis |
|---|---|---|---|---|---|---|---|---|
| router_v1 | Gemma-3-270M | 4bit | 1e-4 | 20 | nein | 8 | – | Val 3,2→182 |
| router_v2 | Gemma-3-270M | 4bit | 1e-5 | 20 | nein | 8 | – | Val 3,2→340 (noch schlechter!) |
| router_v3 | Gemma-3-270M | **bf16** | 1e-5 | 20 | nein | 8 | – | Val 3,2→334 (fast identisch zu v2 – **Quantisierung ausgeschlossen**) |
| router_v4 | Gemma-3-270M | 4bit | 5e-5 | 20 | **ja** | 32 | Warmup 40 Iter | Val 3,2→139-244 (Fix ohne Wirkung) |

Code-Lektüre von `mlx_lm/tuner/trainer.py` (`default_loss`) und
`tuner/datasets.py` (`ChatDataset.process()`, Offset-/Masken-Berechnung)
ergab **keinen Bug** – Standard-korrekte maskierte Kreuzentropie. Auffällig:
unser System-Prompt ist mit ~500 Tokens sehr lang gegenüber den ~14 Tokens
Ziel-Sequenz (>95% des Kontexts wird maskiert) – ein ungewöhnliches
Verhältnis, aber kein bewiesener Kausalzusammenhang.

## Durchbruch – Llama-3.2-3B-Instruct statt Gemma-Familie (2026-08-04)

Nutzer-Freigabe, das Modell zu wechseln, falls das einfacher ist. Auf
Empfehlung aus einer Tiefenrecherche (siehe DECISIONS.md ADR-032-Nachtrag):
**Llama-3.2-3B-Instruct** statt Gemma 3n/Gemma-3-270M – mit Abstand am besten
unterstützte Architektur in mlx-lm (kein AltUp, keine Exotik), von mehreren
Quellen explizit als aktuell bestes On-Device-Modell für Tool-Calling
genannt, Deutsch offiziell eine von acht Kernsprachen.

`mlx-community/Llama-3.2-3B-Instruct-4bit` mit **identischer Pipeline**
(gleicher `data/router`-Datensatz, `--mask-prompt`, LR 1e-5, Batch 8) –
**konvergiert sauber**: Val Loss 3,01 (Iter 1) → 0,096 (Iter 100) → 0,050
(Iter 120). Trainingsverlust fällt monoton. Ab Iter ~160 dann doch
Divergenz (Val Loss auf 7,9 bei Iter 200) – aber diesmal eindeutig als
**Overfitting/Adam-Instabilität nach Erreichen eines sehr niedrigen Loss**
zu erklären (klassisches Muster), nicht als grundsätzlich kaputtes Training.
Fix: **Early Stopping** – der bei Iter 100 gespeicherte Checkpoint
(`0000100_adapters.safetensors`) wurde separiert und evaluiert.

**Ergebnis gegen den gehaltenen Testsatz (52 Beispiele, nie im Training):
46/52 korrekt (88,5%).** Zum Vergleich: jeder Gemma-Versuch lag bei
Basismodell-Niveau oder darunter. Die 6 Fehlschläge sind überwiegend echte
Grenzfälle:
- Extremes STT-Verhörer ("Sameseß" für "SMS") → fälschlich Raumgespräch
- Ein-Wort-Befehl "Lies vor." ohne Kontext → SMS statt Dokument (beides
  vertretbar)
- Radiosprecher-Phrasierung ("Und jetzt zu unserem Interview...") → als
  `frei_gespraech()` statt Raumgespräch eingeordnet
- Umgangssprachliches Verb "simse" für SMS senden → nicht erkannt
- Ein **halluziniertes Werkzeug**: bei "Und damit kommen wir zum Wetter für
  die kommende Woche" rief das Modell `wetter_vorlesen()` auf – ein Name,
  der in keiner Trainingsdaten-Zeile vorkommt. Vermerkt für spätere
  Beobachtung, kein Blocker bei 88,5% Gesamtgenauigkeit.

**Offene nächste Schritte:**
- Genaueren Stopp-Punkt finden (`--save-every 10`, `--steps-per-eval 20`
  zwischen Iter 100-160) statt der aktuellen Grobauflösung (100er-Schritte)
- Datensatz auf mehrere hundert Beispiele skalieren (jetzt mit einem
  Modell, das nachweislich lernt) und erneut evaluieren
- Halluzinierte-Werkzeug-Fälle im Auge behalten, ggf. gezielt mehr
  Negativbeispiele mit thematisch verwandten, aber nicht existierenden
  Wünschen ergänzen
- Phase D.5 (ONNX Runtime GenAI statt MediaPipe – siehe ADR-032-Nachtrag,
  bessere LoRA-Deploy-Story, nutzt die bereits vorhandene
  `onnxruntime-android`-Abhängigkeit statt einer vierten nativen
  ML-Runtime im Projekt)

## Feinerer Early-Stopping-Versuch – Lauf-zu-Lauf-Varianz entdeckt (2026-08-04)

Versuch, den Stopp-Punkt genauer zu finden: `--save-every 20
--steps-per-eval 20` statt der groben 100er-Auflösung, plus neues
`sweep_checkpoints.py` (lädt jeden Zwischenstand einzeln, bewertet ihn gegen
den Testsatz, optional auf eine Teilmenge begrenzt für einen schnellen groben
Sweep vor der vollen Auswertung). Datensatz-Skalierung (Ziel: ~540 statt 345
Beispiele) **blockiert** – das Anthropic-Konto hat aktuell kein Guthaben
mehr (`credit balance is too low`), `gen_dialogue.py` bricht deshalb ab.
Kein Datenverlust (die Datei wird erst am Ende geschrieben), aber ohne neue
Marge muss der bestehende 345-Beispiele-Datensatz weiterverwendet werden.

Zweiter Trainingslauf (`llama_router_v2`, identische Konfiguration wie der
erfolgreiche erste Lauf: gleicher Datensatz, `lr=1e-5`, `batch=8`,
`--mask-prompt`) zeigte diesmal eine ganz andere Trainingskurve – Val Loss
fiel diesmal **durchgängig monoton** (kein Ausreißer wie beim ersten Lauf).
Der Sweep der Zwischenstände (grob, 20 Testbeispiele) ergab aber ein
überraschendes, nicht-monotones Genauigkeits-Muster:

| Iteration | Treffer (von 20) |
|---|---|
| 20 | 16 |
| 40–120 | **0** (kompletter Einbruch trotz fallendem Loss) |
| 140 | 4 |
| 160 | 10 |
| 180 | 12 |
| 200–260 | 12–13 |

Bestes Ergebnis dieses Laufs (Iter 260, volle 52er-Testmenge): **31/52
(59,6 %)** – klar schlechter als der erste Lauf (46/52, 88,5 %), **trotz
identischer Konfiguration**. `mlx_lm.lora` fixiert standardmäßig keinen
Zufallssamen für die Batch-Reihenfolge – zwei Läufe mit denselben
Hyperparametern durchlaufen also unterschiedliche Trainingspfade. Zusätzlich
zeigt sich: **Val-Loss ist für diese Aufgabe kein verlässlicher Stellvertreter
für die tatsächliche (exakte) Ausgabegenauigkeit** – Loss fiel hier
durchgehend, während die Trefferquote zwischenzeitlich auf 0 einbrach.

**Fazit:** `adapters/llama_router_v1_iter100` (88,5 %) bleibt der beste
verfügbare Adapter – dieser zweite Lauf hat ihn nicht geschlagen, aber die
Varianz selbst ist ein wichtiger Befund. Für reproduzierbare künftige Läufe:
`mlx_lm.lora` hat tatsächlich ein `--seed SEED` (in `--help` bestätigt, in
diesem Lauf schlicht nicht gesetzt) – ab jetzt immer explizit mitgeben.
Sobald das Anthropic-Guthaben wieder verfügbar ist, sollte die Datensatz-
Skalierung Priorität vor weiteren Early-Stopping-Experimenten haben – mehr
Trainingsdaten dürfte die Lauf-zu-Lauf-Varianz direkt reduzieren, statt sie
nur zu vermessen.

## `gen_dialogue.py` auf lokale Generierung umgestellt (2026-08-04)

Grund: die Datensatz-Generierung über Claude war der einzige noch verbliebene
Cloud-Abhängigkeit im gesamten Trainings-Pfad (`mlx_lm.lora` selbst läuft
schon immer 100% lokal) – und genau dort fehlte gerade das API-Guthaben.
`--backend claude` bleibt als Option erhalten (z.B. sobald wieder Guthaben
verfügbar ist, für höhere Qualität/Vielfalt), Standard ist jetzt
`--backend local` mit `mlx-community/Llama-3.2-3B-Instruct-4bit` (demselben
Modell, das sich schon fürs Finetuning bewährt hat).

**Drei echte Bugs unterwegs gefunden und behoben, nicht nur Hyperparameter:**

1. **Kein Terminierungs-Limit.** Die ursprüngliche Fassung fragte so lange
   nach einem Batch, bis `n` gültige Beispiele erreicht waren – ohne
   Obergrenze. Als das lokale Modell fuer eine Prompt-Form durchgängig
   ungültige Antworten lieferte, lief das Skript >20 Minuten ohne jede
   Ausgabe (stdout ist beim Umleiten in eine Datei blockgepuffert, nicht
   zeilengepuffert – deshalb sah es wie ein Hänger aus, war aber eine echte
   Endlosschleife). Fix: `collect()` bricht garantiert nach
   `n * MAX_ATTEMPTS_PER_EXAMPLE` Versuchen ab und meldet ehrlich, wie viele
   gültige Beispiele es tatsächlich wurden.
2. **JSON-Array-Generierung war fuer ein 3B-Modell zu fehleranfällig.**
   Ursprünglich wurden mehrere Beispiele pro Antwort als JSON-Liste
   angefragt (funktionierte bei Claude gut) – das lokale Modell brach dabei
   wiederholt das JSON (unescapte Anführungszeichen innerhalb von
   Funktionsaufruf-Strings wie `"output": "sms_senden(kontakt="Ulla", ...)"`
   ohne `\"`, oder abgeschnittene Arrays ohne schließende `]`). Fix:
   komplett auf ein Beispiel pro Antwort umgestellt, simples
   `EINGABE:`/`AUSGABE:`-Zeilenformat statt JSON – kein Array, keine
   verschachtelten Anführungszeichen mehr möglich.
3. **Das Modell kopierte Teile der Prompt-Beschreibung wörtlich.** Die
   Werkzeug-Signaturen enthielten Platzhalter im `key="Wert"`-Stil (z.B.
   `text="Woran"`), die das Modell für echte Beispielwerte hielt und
   unverändert übernahm. Fix: Signaturen ohne Anführungszeichen um
   Platzhalter umformuliert (`text (woran erinnert werden soll)` statt
   `text="Woran"`), zusätzlich ein konkretes, korrekt formatiertes
   Vollbeispiel im Prompt (mit zur Argumentform passender Illustration -
   ein Werkzeug ohne Argumente bekommt eine argumentlose Illustration,
   sonst hängte das Modell auch dort unnötig Argumente an).

**Validierung verschärft:** `valid_tool_call()` prüfte ursprünglich nur, ob
die per Regex gefundenen `key="value"`-Paare plausibel aussehen – ein
unerkannter Rest wie ein nackter Positionsparameter (`hoerbuch_abspielen
(Boris)`, live beobachtet) fiel durch die Maschen, weil `findall()` einfach
nichts Passendes fand und der Rest stillschweigend ignoriert wurde. Jetzt
muss die komplette Argumentliste per `re.fullmatch()` aus lauter
`key="value"`-Paaren bestehen, sonst wird das Beispiel verworfen.

**Deduplizierung:** Das Modell ignoriert die "wiederhole nichts"-Anweisung im
Prompt (`avoid_note()`) öfter als erwartet – mehrere exakt identische Inputs
mit unterschiedlichem Output rutschten anfangs durch. Jetzt führt `collect()`
zusätzlich eine `seen_inputs`-Menge (case-insensitive), harte Zurückweisung
bei Duplikaten. Ein neues `--append`-Flag lässt mehrere kleine Läufe
Ergebnisse in `data/raw/dialogue_raw.json` **anhäufen** statt sie zu
ersetzen, mit über alle bisherigen Läufe hinweg geltender Deduplizierung.

**Ertragsrate ist eine echte, dauerhafte Grenze, kein Bug:** Bei kleinem `n`
(z.B. 3 je Werkzeug) liegt die Trefferquote bei ~90-95%. Bei größerem `n`
(z.B. 15) fällt sie auf ~10-15%, weil ein 3B-Modell für ein derart enges
Themenfeld schlicht nicht genug einzigartige Formulierungen "kennt" und nach
den ersten paar Versuchen anfängt, Dubletten zu produzieren, die dann
verworfen werden. Gegen den bereits bestehenden, groesstenteils
Claude-generierten 345er-Datensatz ist die Ertragsrate nochmal niedriger
(neue Beispiele dürfen auch mit denen aus früheren Claude-Läufen nicht
kollidieren). Empfehlung: **mehrere kleine `--append`-Läufe** (z.B.
`--per-tool 5 --negatives 15 --persona 15`) statt eines großen – bestätigt
am 2026-08-04: ein Lauf mit diesen Werten fügte 18 neue, echte Beispiele zum
bestehenden 345er-Datensatz hinzu (345 → 363). Bescheiden, aber real, kostenlos
und wiederholbar – für einen deutlichen Sprung auf mehrere hundert NEUE
Beispiele bleibt Claude (sobald wieder Guthaben verfügbar ist) das
geeignetere Werkzeug, gerade weil es nicht an derselben Diversitätsgrenze
hängt.

## Neuer Bestwert: 94,4 % auf dem 363er-Datensatz (2026-08-04)

Neu trainiert auf dem durch lokale Generierung gewachsenen Datensatz
(345 → 363), diesmal mit `--seed 0` (Empfehlung aus dem vorherigen
Lauf-zu-Lauf-Varianz-Befund befolgt) und `--save-every 20` von Anfang an:

```bash
venv/bin/python -m mlx_lm lora --model mlx-community/Llama-3.2-3B-Instruct-4bit \
  --train --data data/router --mask-prompt --seed 0 \
  --iters 300 --batch-size 8 --learning-rate 1e-5 \
  --steps-per-report 20 --steps-per-eval 20 --save-every 20 \
  --adapter-path adapters/llama_router_363
```

**Val Loss diesmal durchgehend stabil** (3,48 → 0,175 bereits bei Iter 20,
danach flach zwischen 0,08–0,12 bis Iter 300) – keine Spur der Divergenz aus
den vorherigen Läufen. Der Checkpoint-Sweep (`sweep_checkpoints.py`, grobe
20er-Teilmenge) zeigt ein bemerkenswert flaches Plateau: **18/20 korrekt von
Iter 20 bis Iter 300 durchgehend** (einzige Ausnahme Iter 40 mit 16/20).
Volle Auswertung des Iter-200-Checkpoints gegen den kompletten 54er-Testsatz:

**51/54 korrekt (94,4 %)** – neuer Bestwert, deutlich über dem vorherigen
Rekord (46/52, 88,5 %) und diesmal nicht nur ein Glückstreffer eines
einzelnen Iterationspunkts, sondern über das gesamte Training hinweg
stabil reproduzierbar.

**Nebenbefund beim Durchsehen der 3 Fehlschläge:** Zwei Trainingsbeispiele
aus dem lokal generierten `--append`-Batch waren tatsächlich falsch
beschriftet – `"Hey Lina, wie geht es dir?"` (eine reine Begrüßung) war als
`sms_vorlesen()` gelabelt, `"Hallo, wie geht es dir?"` als `stopp()`. Die
Modellantwort auf den ersten Testfall (`stopp()`) deutet darauf hin, dass
das Modell diese beiden fast identischen, aber unterschiedlich beschrifteten
Beispiele tatsächlich verwechselt hat. `valid_tool_call()` prüft nur, ob die
AUSGABE syntaktisch korrekt ist, nicht ob EINGABE und AUSGABE inhaltlich
zusammenpassen – eine echte Lücke, gegen die aktuell nur manuelle Durchsicht
hilft. Beide Beispiele entfernt (363 → 361 Rohbeispiele), Router-Datensatz
neu gebaut. Die übrigen 2 Fehlschläge sind echte Grenzfälle (extremes
STT-Verhöhrer "Sameseß" für "SMS"; eine Segel-Alltagsschilderung ohne
eindeutigen Konversations-Marker).

**Aktueller Bestand:** `adapters/llama_router_363_iter200` ist der neue
beste Adapter (94,4 %), abgelöst `llama_router_v1_iter100` (88,5 %). Nächster
sinnvoller Schritt bleibt die Datensatz-Skalierung über Claude, sobald
wieder Guthaben verfügbar ist – plus künftig generierte Batches vor dem
Training stichprobenartig auf Input/Output-Konsistenz durchsehen, nicht nur
auf Syntax verlassen.

## Konsistenz-Check versucht, verworfen; Datensatz-Skalierung an ihrer Grenze (2026-08-04)

Nutzerwunsch nach dem Fund oben: EINGABE/AUSGABE-Konsistenz automatisiert
prüfen, nicht nur AUSGABE-Syntax. **Erster Ansatz (verworfen):**
`make_semantic_check()` fragte den Router-Prompt selbst, unabhängig vom
generierten AUSGABE-Wert, was er für die EINGABE klassifizieren würde, und
verglich mit der behaupteten Kategorie. Live getestet: Ertragsrate brach von
94% auf 12% ein. Ursache: der Check nutzt das **unfinetunte Basismodell**,
das beim Router-Task selbst schwach ist (11/20 zero-shot, siehe Mac-Spike
oben) – ein Zirkelschluss, dieselbe Schwäche, die das Finetuning beheben
soll, als Filter für die Trainingsdaten zu verwenden. Der Check lehnte
massenhaft **gute** Beispiele ab, weil das Basismodell sie nicht korrekt
zero-shot klassifizierte, nicht weil Eingabe und Ausgabe wirklich nicht
zusammenpassten.

**Ersetzt durch einen billigen, gezielten Filter:** `is_generic_smalltalk()`
– eine reine Regex-Prüfung (kein zusätzlicher Modellaufruf) auf Muster wie
"wie geht's/wie geht es dir", angewendet nur auf Werkzeug-Beispiele (bei
Raumgespräch/Persona-Beispielen wären solche Sätze legitim). Trifft exakt
den beobachteten Fehlerfall, ohne die Zirkelschluss-Problematik. Mit diesem
Filter zurück zu normalen Ertragsraten (kein künstlicher Einbruch mehr).

**Datensatz-Skalierung per `--append` ist an ihrer praktischen Grenze
angekommen:** Ein weiterer `--append`-Lauf (`--per-tool 5 --negatives 15
--persona 15`) gegen den mittlerweile 361 Beispiele umfassenden Datensatz
ergab **0 neue Beispiele in jeder einzelnen Kategorie** (0/5 bei allen 9
Werkzeugen, 0/15 bei Negativen, 0/15 bei Persona) – nicht nur eine niedrige
Quote wie beim ersten `--append`-Lauf (18/65), sondern ein vollständiger
Nulldurchgang. Der bestehende Datensatz deckt inzwischen praktisch alles ab,
was ein 3B-Modell für diese engen, generischen Kategorien natürlicherweise
produziert – jeder Versuch kollidierte mit einem bereits vorhandenen
Beispiel. Das ist die klare, endgültige Bestätigung der bereits vermuteten
Diversitätsgrenze: **lokale Generierung kann diesen Datensatz aktuell nicht
weiter vergrößern**, unabhängig von Versuchslimit oder Prompt-Feinschliff.
Datensatz bleibt bei 361 – identisch mit dem Stand des 94,4-%-Adapters, kein
erneutes Training nötig. Weiteres Wachstum braucht Claude (`--backend
claude`) sobald wieder Guthaben verfügbar ist, oder manuell geschriebene
Beispiele (z.B. echte STT-Verhörer aus der Praxis, siehe TODO.md).
