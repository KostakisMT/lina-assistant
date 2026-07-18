"""Erzeugt synthetische Trainingsclips für "Hey Lina".

Positive: "Hey Lina" über ~904 LibriTTS-Sprecher (en) + deutsche Piper-Stimmen.
Hard Negatives: ähnliche Phrasen + zufällige deutsche/englische Sätze
(auch in denselben Stimmen, damit der Classifier nicht die Stimme lernt).

Ausgabe: 16-kHz-Mono-WAVs in data/positive_raw/ und data/hard_negative_raw/.
"""

import os
import numpy as np
import soundfile as sf
import sherpa_onnx
from scipy.signal import resample_poly

SR = 16000
TTS_DIR = "tts"
OUT_POS = "data/positive_raw"
OUT_NEG = "data/hard_negative_raw"

POSITIVE_TEXTS = ["Hey Lina.", "Hey, Lina!", "Hey Leena."]
NEGATIVE_TEXTS = [
    "Hey Nina.", "Hey Tina.", "Hey Mina.", "Hey Lisa.", "Helena.",
    "Hallo Nina.", "Hey Leon.", "Hey Linda.", "Katharina.", "Der Liter Benzin.",
    "Wie ist das Wetter heute?", "Ich möchte ein Buch lesen.",
    "Das Abendessen ist fertig.", "Morgen scheint die Sonne.",
    "Bitte mach das Radio an.", "Die Katze schläft auf dem Sofa.",
    "What time is it right now?", "The weather is nice today.",
    "Please turn on the lights.", "I would like to read a book.",
]

GERMAN_VOICES = [
    ("vits-piper-de_DE-dii-high", "de_DE-dii-high"),
    ("vits-piper-de_DE-miro-high", "de_DE-miro-high"),
    ("vits-piper-de_DE-thorsten-medium", "de_DE-thorsten-medium"),
    ("vits-piper-de_DE-ramona-low", "de_DE-ramona-low"),
    ("vits-piper-de_DE-kerstin-low", "de_DE-kerstin-low"),
    ("vits-piper-de_DE-eva_k-x_low", "de_DE-eva_k-x_low"),
]
LIBRITTS = ("vits-piper-en_US-libritts_r-medium", "en_US-libritts_r-medium")


def make_tts(model_dir: str, model_name: str) -> sherpa_onnx.OfflineTts:
    cfg = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=f"{TTS_DIR}/{model_dir}/{model_name}.onnx",
                tokens=f"{TTS_DIR}/{model_dir}/tokens.txt",
                data_dir=f"{TTS_DIR}/{model_dir}/espeak-ng-data",
            ),
            num_threads=4,
        ),
    )
    return sherpa_onnx.OfflineTts(cfg)


def synth(tts, text, sid, speed) -> np.ndarray:
    audio = tts.generate(text, sid=sid, speed=speed)
    samples = np.asarray(audio.samples, dtype=np.float32)
    if audio.sample_rate != SR:
        samples = resample_poly(samples, SR, audio.sample_rate).astype(np.float32)
    return samples


def save(path, samples):
    peak = np.abs(samples).max()
    if peak < 1e-3:  # leere/stumme Synthese verwerfen
        return False
    sf.write(path, samples / peak * 0.85, SR)
    return True


def main():
    os.makedirs(OUT_POS, exist_ok=True)
    os.makedirs(OUT_NEG, exist_ok=True)
    rng = np.random.default_rng(42)
    n_pos = n_neg = 0

    # 1) LibriTTS multi-speaker (englisch, 904 Sprecher)
    tts = make_tts(*LIBRITTS)
    n_speakers = tts.num_speakers
    print(f"libritts speakers: {n_speakers}")
    for sid in range(n_speakers):
        text = POSITIVE_TEXTS[sid % len(POSITIVE_TEXTS)]
        speed = float(rng.uniform(0.85, 1.25))
        if save(f"{OUT_POS}/libritts_{sid:04d}.wav", synth(tts, text, sid, speed)):
            n_pos += 1
        # jeder 3. Sprecher liefert zusätzlich ein Hard Negative
        if sid % 3 == 0:
            ntext = NEGATIVE_TEXTS[(sid // 3) % len(NEGATIVE_TEXTS)]
            if save(f"{OUT_NEG}/libritts_{sid:04d}.wav", synth(tts, ntext, sid, float(rng.uniform(0.85, 1.25)))):
                n_neg += 1
    del tts

    # 2) Deutsche Stimmen: alle Texte, mehrere Geschwindigkeiten
    for model_dir, model_name in GERMAN_VOICES:
        tts = make_tts(model_dir, model_name)
        for ti, text in enumerate(POSITIVE_TEXTS):
            for si, speed in enumerate([0.8, 0.9, 1.0, 1.1, 1.25]):
                ok = save(
                    f"{OUT_POS}/{model_name}_{ti}_{si}.wav",
                    synth(tts, text, 0, speed),
                )
                n_pos += int(ok)
        for ti, text in enumerate(NEGATIVE_TEXTS):
            for si, speed in enumerate([0.9, 1.1]):
                ok = save(
                    f"{OUT_NEG}/{model_name}_{ti}_{si}.wav",
                    synth(tts, text, 0, speed),
                )
                n_neg += int(ok)
        del tts

    print(f"positive clips: {n_pos}, hard negative clips: {n_neg}")


if __name__ == "__main__":
    main()
