# Lokaler Gemma-3n-Pfad – Trainings-/Eval-Pipeline (ADR-032)

Parallel zur Wake-Word-Trainingspipeline (`training/`), aber eigenes venv
(MLX-Abhängigkeiten kollidieren mit dem torch/onnxruntime-Stack dort).

## Ablauf (reproduzierbar)

```bash
# 1. Umgebung
cd training/llm
uv venv --python 3.12 venv
uv pip install --python venv/bin/python mlx-vlm anthropic

# 2. Phase A0/A: Tooling-Check + Mac-Spike (kein Finetuning, Basis-Checkpoint)
venv/bin/python spike_check.py

# 3. Phase B: synthetische Trainingsdaten (braucht CLAUDE_API_KEY in local.properties)
venv/bin/python gen_dialogue.py --per-tool 4 --negatives 8 --persona 8
venv/bin/python build_dataset.py

# 4. Phase C: LoRA-Finetuning + Eval gegen den gehaltenen Testsatz
venv/bin/python -m mlx_lm lora --model mlx-community/gemma-3n-E2B-it-lm-4bit \
  --train --data data --config lora_config.yaml \
  --iters 200 --batch-size 2 --num-layers 8 --learning-rate 1e-4 \
  --adapter-path adapters/v1
venv/bin/python eval.py --adapter-path adapters/v1
venv/bin/python eval.py   # Baseline ohne Adapter, zum Vergleich
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

**Fazit:** Die Pipeline funktioniert mechanisch durchgängig (Training →
Speichern → Laden → Anwenden), aber dieser erste, bewusst kleine Versuch
(39 Beispiele) reicht nicht für ein messbar wirksames Finetuning. Nächster
Schritt vor einem weiteren Versuch: deutlich größerer Datensatz (mehrere
hundert statt 52 Rohbeispiele – `gen_dialogue.py` unterstützt das direkt
über `--per-tool`/`--negatives`/`--persona`), niedrigere Lernrate (z.B. 2e-5
statt 1e-4) mit mehr Iterationen statt weniger Iterationen mit hoher Rate,
und eine Nachverfolgung der `q_proj`-Gradienten-Frage (ggf. als Upstream-
Issue bei `ml-explore/mlx-lm` melden – reproduzierbar, siehe oben).
