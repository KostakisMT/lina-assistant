"""Trainiert den "Hey Lina"-Classifier und exportiert hey_lina.onnx.

Architektur wie die offiziellen OpenWakeWord-Modelle: kleines MLP auf
[16,96]-Embedding-Fenstern, Sigmoid-Score. Eingabeform [1,16,96] passt
direkt zu OpenWakeWordEngine.computeWakeWordScore() auf dem Tablet.
"""

import numpy as np
import torch
import torch.nn as nn

DEVICE = "cpu"
rng = np.random.default_rng(3)


class WakeModel(nn.Module):
    def __init__(self):
        super().__init__()
        self.net = nn.Sequential(
            nn.Flatten(),
            nn.Linear(16 * 96, 128),
            nn.LayerNorm(128),
            nn.ReLU(),
            nn.Linear(128, 128),
            nn.LayerNorm(128),
            nn.ReLU(),
            nn.Linear(128, 1),
            nn.Sigmoid(),
        )

    def forward(self, x):
        return self.net(x)


def load(name):
    return torch.from_numpy(np.load(f"data/{name}.npy"))


def main():
    pos, neg = load("features_pos"), load("features_neg")
    pos_val, neg_val = load("features_pos_val"), load("features_neg_val")
    print(f"train: pos {len(pos)}, neg {len(neg)} | val: pos {len(pos_val)}, neg {len(neg_val)}")

    model = WakeModel().to(DEVICE)
    opt = torch.optim.AdamW(model.parameters(), lr=1e-3, weight_decay=1e-4)
    # Falsch-Positive sind schlimmer als verpasste Wecks: Negative höher gewichten
    loss_fn = nn.BCELoss(reduction="none")

    batch = 512
    steps = 6000
    for step in range(steps):
        pi = torch.randint(0, len(pos), (batch // 4,))
        ni = torch.randint(0, len(neg), (3 * batch // 4,))
        x = torch.cat([pos[pi], neg[ni]]).to(DEVICE)
        y = torch.cat([
            torch.ones(len(pi), 1),
            torch.zeros(len(ni), 1),
        ]).to(DEVICE)
        w = torch.cat([
            torch.full((len(pi), 1), 1.0),
            torch.full((len(ni), 1), 2.0),
        ]).to(DEVICE)
        opt.zero_grad()
        out = model(x)
        loss = (loss_fn(out, y) * w).mean()
        loss.backward()
        opt.step()
        if step % 500 == 0 or step == steps - 1:
            model.eval()
            with torch.no_grad():
                ps = model(pos_val.to(DEVICE)).numpy().ravel()
                ns = model(neg_val.to(DEVICE)).numpy().ravel()
            # Kennzahlen bei Schwelle 0.5 und 0.3 (App-Threshold)
            for th in (0.5, 0.3):
                recall = float((ps >= th).mean())
                fpr = float((ns >= th).mean())
                print(f"step {step:5d} loss {loss.item():.4f} | th={th}: recall {recall:.3f}, neg-fp-rate {fpr:.5f}")
            model.train()

    model.eval()
    # Export – Eingabename/-form kompatibel zur App ([1,16,96], fixe Batchgröße)
    dummy = torch.zeros(1, 16, 96)
    torch.onnx.export(
        model, (dummy,), "hey_lina_v1.onnx",
        input_names=["oww_input"], output_names=["score"],
        opset_version=17, dynamo=False,
    )
    print("exported hey_lina_v1.onnx")

    # Verifikation mit onnxruntime
    import onnxruntime as ort
    sess = ort.InferenceSession("hey_lina_v1.onnx")
    o = sess.run(None, {"oww_input": pos_val[:1].numpy()})[0]
    with torch.no_grad():
        t = model(pos_val[:1]).item()
    print(f"onnx {float(o.ravel()[0]):.4f} vs torch {t:.4f}")


if __name__ == "__main__":
    main()
