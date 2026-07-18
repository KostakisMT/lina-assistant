"""Berechnet Trainings-Features aus den Roh-Clips.

Positive: pro Clip mehrere augmentierte Varianten (Gain, Rauschen aus MUSAN,
synthetischer Hall, zufällige Stille davor/danach) -> letztes Fenster [16,96]
(Weckwort endet am Clip-Ende, wie zur Laufzeit beim Trigger).

Negative: Hard Negatives (TTS) + MUSAN-Ausschnitte (Sprache/Musik/Geräusche)
-> ALLE Fenster.

Ausgabe: data/features_{pos,neg}.npy und Validierungs-Splits.
"""

import os
import glob
import numpy as np
import soundfile as sf
from scipy.signal import fftconvolve, resample_poly
from oww_features import FeaturePipeline, WAKE_FRAMES

SR = 16000
rng = np.random.default_rng(7)
fp = FeaturePipeline()

MUSAN = sorted(glob.glob("data/musan/*/*/*.wav"))
print(f"musan files: {len(MUSAN)}")


def load_wav(path):
    audio, sr = sf.read(path, dtype="float32", always_2d=False)
    if audio.ndim > 1:
        audio = audio.mean(axis=1)
    if sr != SR:
        audio = resample_poly(audio, SR, sr).astype(np.float32)
    return audio


def random_noise(n):
    if not MUSAN:
        return np.zeros(n, dtype=np.float32)
    for _ in range(10):
        path = MUSAN[rng.integers(len(MUSAN))]
        try:
            audio = load_wav(path)
        except Exception:
            continue
        if len(audio) >= n:
            start = rng.integers(0, len(audio) - n + 1)
            return audio[start:start + n]
    return np.zeros(n, dtype=np.float32)


def synthetic_rir():
    """Einfacher synthetischer Raumhall (exponentiell abklingendes Rauschen)."""
    t60 = rng.uniform(0.1, 0.5)
    n = int(t60 * SR)
    rir = rng.standard_normal(n).astype(np.float32) * np.exp(
        -6.9 * np.arange(n) / n
    ).astype(np.float32)
    rir[0] = 1.0
    return rir / np.abs(rir).max()


def augment(clip):
    out = clip.copy()
    if rng.random() < 0.7:  # Tonhöhe/Tempo (±10% ≈ künstliche Sprecher)
        num = int(rng.uniform(90, 110))
        out = resample_poly(out, 100, num).astype(np.float32)
    if rng.random() < 0.5:  # Hall
        out = fftconvolve(out, synthetic_rir())[: len(out) + SR // 4].astype(np.float32)
    # Kontext davor, kurze Stille danach (Weckwort soll am Ende liegen).
    # Pipeline braucht >= 31 Chunks (~2.5s), bevor 16 Embeddings existieren.
    tail = np.zeros(int(rng.uniform(0.0, 0.15) * SR), dtype=np.float32)
    min_total = int(2.7 * SR)
    front_len = max(
        int(rng.uniform(1.2, 2.5) * SR),
        min_total - len(out) - len(tail),
    )
    out = np.concatenate([np.zeros(front_len, dtype=np.float32), out, tail])
    gain = rng.uniform(0.15, 1.0)
    out = out * gain
    if rng.random() < 0.75:  # Hintergrundgeräusch
        snr_db = rng.uniform(3, 25)
        noise = random_noise(len(out))
        sig_p = np.mean(out**2) + 1e-9
        noi_p = np.mean(noise**2) + 1e-9
        noise = noise * np.sqrt(sig_p / noi_p / (10 ** (snr_db / 10)))
        out = out + noise
    peak = np.abs(out).max()
    if peak > 1.0:
        out = out / peak
    return out


def main():
    n_aug = int(os.environ.get("N_AUG", "4"))

    # --- Positive ---
    pos_windows = []
    pos_files = sorted(glob.glob("data/positive_raw/*.wav"))
    print(f"positive clips: {len(pos_files)}")
    for i, f in enumerate(pos_files):
        clip = load_wav(f)
        for _ in range(n_aug):
            w = fp.final_window(augment(clip))
            if w is not None:
                pos_windows.append(w)
        if i % 200 == 0:
            print(f"  pos {i}/{len(pos_files)}")
    # --- Echte Nutzer-Weckrufe: stark überabgetastet (Zielstimme!) ---
    user_files = sorted(glob.glob("data/user_positive/*.wav"))
    print(f"user positive clips: {len(user_files)}")
    user_windows = []
    for f in user_files:
        clip = load_wav(f)
        for _ in range(40):
            w = fp.final_window(augment(clip))
            if w is not None:
                user_windows.append(w)
    # 2 Clips für Validierung reservieren (letzte 80 Fenster)
    pos_windows.extend(user_windows)
    pos = np.array(pos_windows, dtype=np.float32)

    # --- Hard Negatives (TTS-Phrasen + echte Nutzer-Befehle, augmentiert) ---
    neg_windows = []
    for f in sorted(glob.glob("data/user_command/*.wav")):
        clip = load_wav(f)
        for _ in range(20):
            neg_windows.extend(fp.windows(augment(clip)))
    hn_files = sorted(glob.glob("data/hard_negative_raw/*.wav"))
    print(f"hard negative clips: {len(hn_files)}")
    for f in hn_files:
        clip = load_wav(f)
        for _ in range(2):
            ws = fp.windows(augment(clip))
            neg_windows.extend(ws)

    # --- MUSAN-Negative (echte Sprache/Musik/Geräusche) ---
    hours = float(os.environ.get("MUSAN_HOURS", "12"))
    seg = 10 * SR  # 10s-Segmente
    n_segments = int(hours * 3600 / 10)
    print(f"musan negatives: {n_segments} x 10s")
    for i in range(n_segments):
        ws = fp.windows(random_noise(seg))
        # ausdünnen: jedes 2. Fenster reicht (stark überlappend)
        neg_windows.extend(ws[::2])
        if i % 200 == 0:
            print(f"  musan {i}/{n_segments}, windows so far: {len(neg_windows)}")
    neg = np.array(neg_windows, dtype=np.float32)

    print(f"features: pos {pos.shape}, neg {neg.shape}")
    rng.shuffle(pos)
    rng.shuffle(neg)
    n_pv = max(200, len(pos) // 10)
    n_nv = max(2000, len(neg) // 10)
    np.save("data/features_pos_val.npy", pos[:n_pv])
    np.save("data/features_pos.npy", pos[n_pv:])
    np.save("data/features_neg_val.npy", neg[:n_nv])
    np.save("data/features_neg.npy", neg[n_nv:])
    print("saved.")


if __name__ == "__main__":
    main()
