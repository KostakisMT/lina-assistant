"""Zerlegt die Nutzer-Aufnahmen in Äußerungen und sortiert sie per Whisper:
Segmente mit "lina" -> data/user_positive/, Rest -> data/user_command/.
"""

import glob
import os
import numpy as np
import soundfile as sf
import sherpa_onnx

SR = 16000
WHISPER = "../app/src/main/assets/whisper/sherpa-onnx-whisper-base"

rec = sherpa_onnx.OfflineRecognizer.from_whisper(
    encoder=f"{WHISPER}/base-encoder.int8.onnx",
    decoder=f"{WHISPER}/base-decoder.int8.onnx",
    tokens=f"{WHISPER}/base-tokens.txt",
    language="de",
    num_threads=4,
)


def segments(audio, thresh_ratio=4.0, min_len=0.35, pad=0.25):
    """Energie-basierte Segmentierung (Frame 50ms)."""
    frame = SR // 20
    n = len(audio) // frame
    rms = np.array([
        np.sqrt(np.mean(audio[i * frame:(i + 1) * frame] ** 2)) for i in range(n)
    ])
    floor = np.percentile(rms, 20) + 1e-6
    active = rms > floor * thresh_ratio
    segs = []
    start = None
    silence = 0
    for i, a in enumerate(active):
        if a:
            if start is None:
                start = i
            silence = 0
        elif start is not None:
            silence += 1
            if silence > 8:  # 400ms Stille beendet Segment
                segs.append((start, i - silence))
                start, silence = None, 0
    if start is not None:
        segs.append((start, n))
    out = []
    for s, e in segs:
        if (e - s) * frame / SR < min_len:
            continue
        a = max(0, int(s * frame - pad * SR))
        b = min(len(audio), int(e * frame + pad * SR))
        out.append(audio[a:b])
    return out


def transcribe(seg):
    s = rec.create_stream()
    s.accept_waveform(SR, seg)
    rec.decode_stream(s)
    return s.result.text.strip().lower()


def main():
    os.makedirs("data/user_positive", exist_ok=True)
    os.makedirs("data/user_command", exist_ok=True)
    n_pos = n_cmd = 0
    for path in sorted(glob.glob("data/user_recordings/*.wav")):
        audio, sr = sf.read(path, dtype="float32")
        assert sr == SR, sr
        for i, seg in enumerate(segments(audio)):
            text = transcribe(seg)
            is_wake = any(k in text for k in ("lina", "lena", "leena", "linna"))
            kind = "user_positive" if is_wake else "user_command"
            name = os.path.basename(path).replace(".wav", f"_{i:02d}.wav")
            sf.write(f"data/{kind}/{name}", seg, SR)
            print(f"{kind[5:]:9s} {len(seg)/SR:4.1f}s  \"{text}\"  -> {name}")
            if is_wake:
                n_pos += 1
            else:
                n_cmd += 1
    print(f"\nwake calls: {n_pos}, commands: {n_cmd}")


if __name__ == "__main__":
    main()
