# "Hey Lina" – Wake-Word-Training

Trainiert den OpenWakeWord-Classifier `hey_lina_v1.onnx` mit synthetischen
TTS-Daten. Die Feature-Berechnung ([oww_features.py](oww_features.py))
repliziert exakt die Pipeline aus `OpenWakeWordEngine.kt` (gleiche
ONNX-Modelle, Normalisierung x/10+2, 76-Frame-Fenster, 16×96-Eingabe) –
damit passen Training und Laufzeit garantiert zusammen.

## Ablauf (reproduzierbar)

```bash
# 1. Umgebung (uv installiert ein eigenes Python, unabhängig von Homebrew)
uv venv --python 3.12 venv
uv pip install --python venv/bin/python torch numpy scipy soundfile onnxruntime sherpa-onnx onnx

# 2. Daten
#    - TTS-Modelle nach tts/ (sherpa-onnx tts-models Release):
#      vits-piper-en_US-libritts_r-medium (904 Sprecher) + deutsche Piper-Stimmen
#    - MUSAN nach data/musan/ (https://www.openslr.org/17, ~11GB)

# 3. Pipeline
venv/bin/python gen_samples.py       # ~1000 Positive + ~550 Hard Negatives (TTS)
venv/bin/python extract_features.py  # Augmentierung + Features (N_AUG=4, MUSAN_HOURS=12)
venv/bin/python train.py             # MLP-Training + Export hey_lina_v1.onnx

# 4. Deployment
cp hey_lina_v1.onnx ../app/src/main/assets/openwakeword/
```

## Ergebnis v1 (2026-07-04)

- Validierung: Recall ~0.90 (Schwelle 0.3), Fehlalarmrate ~0.17% pro Fenster
  auf MUSAN-Negativen (laute Musik/Sprache – Wohnzimmer-Realität ist ruhiger)
- Laufzeit-Schwelle/Patience in `OpenWakeWordEngine.kt` (threshold, patienceCount)
- Bei zu vielen Fehlalarmen: Schwelle erhöhen; bei verpassten Wecks: senken
  oder mit mehr/echten Aufnahmen nachtrainieren (Clips des Zielnutzers in
  data/positive_raw/ legen und Pipeline ab Schritt 3 wiederholen)
