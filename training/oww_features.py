"""Repliziert die OpenWakeWord-Feature-Pipeline der Android-App exakt.

Muss 1:1 zu OpenWakeWordEngine.kt passen:
  - 16 kHz mono, Chunks von 1280 Samples (80 ms)
  - melspectrogram.onnx pro Chunk -> [1,1,F,32], Normalisierung x/10+2
  - Ringpuffer aus Mel-Frames; ab 76 Frames: embedding_model.onnx auf den
    LETZTEN 76 Frames -> ein Embedding [96] pro Chunk
  - Classifier-Eingabe: Fenster aus 16 aufeinanderfolgenden Embeddings [16,96]
"""

import numpy as np
import onnxruntime as ort

CHUNK = 1280
MEL_BINS = 32
EMB_WINDOW = 76
EMB_DIM = 96
WAKE_FRAMES = 16
ASSETS = "../app/src/main/assets/openwakeword"


class FeaturePipeline:
    def __init__(self, assets_dir: str = ASSETS):
        so = ort.SessionOptions()
        so.log_severity_level = 3
        self.mel = ort.InferenceSession(f"{assets_dir}/melspectrogram.onnx", so)
        self.emb = ort.InferenceSession(f"{assets_dir}/embedding_model.onnx", so)

    def embeddings(self, audio: np.ndarray) -> np.ndarray:
        """audio: float32 [-1,1], 16kHz. Liefert [N, 96] (ein Embedding pro Chunk)."""
        mel_buffer: list[np.ndarray] = []
        out = []
        n_chunks = len(audio) // CHUNK
        for i in range(n_chunks):
            chunk = audio[i * CHUNK:(i + 1) * CHUNK].astype(np.float32)
            mels = self.mel.run(None, {"input": chunk[None, :]})[0]
            frames = mels.reshape(-1, MEL_BINS) / 10.0 + 2.0  # wie in der App
            mel_buffer.extend(frames)
            if len(mel_buffer) < EMB_WINDOW:
                continue
            window = np.array(mel_buffer[-EMB_WINDOW:], dtype=np.float32)
            e = self.emb.run(None, {"input_1": window[None, :, :, None]})[0]
            out.append(e.reshape(EMB_DIM))
        return np.array(out, dtype=np.float32)

    def windows(self, audio: np.ndarray):
        """Alle Classifier-Fenster [*, 16, 96] eines Audiosignals."""
        embs = self.embeddings(audio)
        if len(embs) < WAKE_FRAMES:
            return np.zeros((0, WAKE_FRAMES, EMB_DIM), dtype=np.float32)
        return np.stack([
            embs[i:i + WAKE_FRAMES] for i in range(len(embs) - WAKE_FRAMES + 1)
        ])

    def final_window(self, audio: np.ndarray):
        """Das letzte Fenster (Weckwort endet am Clip-Ende) oder None."""
        embs = self.embeddings(audio)
        if len(embs) < WAKE_FRAMES:
            return None
        return embs[-WAKE_FRAMES:]
