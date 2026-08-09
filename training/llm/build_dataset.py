"""Phase B: rohe Dialogbeispiele -> MLX-Chat-JSONL (train/valid/test).

Liest data/raw/*.json (Listen von {"input", "output", "category"}), baut je
Beispiel eine mlx_lm-Chat-Zeile mit dem gemeinsamen Lina-System-Prompt aus
prompts/system_prompt_de.txt, mischt und splittet in
data/{train,valid,test}.jsonl. test.jsonl geht NIE ins Training (siehe
eval.py) - nur train/valid werden mlx_lm.lora als --data-Verzeichnis
uebergeben.

Aufruf:
    venv/bin/python build_dataset.py [--valid-frac 0.1] [--test-frac 0.1] [--seed 0]
"""

import argparse
import json
import random
from pathlib import Path

HERE = Path(__file__).parent


def load_raw() -> list[dict]:
    raw_dir = HERE / "data" / "raw"
    examples = []
    for path in sorted(raw_dir.glob("*.json")):
        examples.extend(json.loads(path.read_text()))
    return examples


def to_chat_line(system_prompt: str, example: dict) -> dict:
    return {
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": example["input"]},
            {"role": "assistant", "content": example["output"]},
        ]
    }


def write_jsonl(path: Path, rows: list[dict]) -> None:
    with path.open("w") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--valid-frac", type=float, default=0.1)
    parser.add_argument("--test-frac", type=float, default=0.15)
    parser.add_argument("--seed", type=int, default=0)
    args = parser.parse_args()

    system_prompt = (HERE / "prompts" / "system_prompt_de.txt").read_text()
    examples = load_raw()
    if not examples:
        raise SystemExit("Keine Rohdaten in data/raw/ gefunden - erst gen_dialogue.py laufen lassen")

    rng = random.Random(args.seed)
    rng.shuffle(examples)

    n = len(examples)
    n_test = max(1, round(n * args.test_frac))
    n_valid = max(1, round(n * args.valid_frac))
    n_train = n - n_test - n_valid
    if n_train < 1:
        raise SystemExit(f"Zu wenige Beispiele ({n}) fuer diesen Split")

    test_ex = examples[:n_test]
    valid_ex = examples[n_test:n_test + n_valid]
    train_ex = examples[n_test + n_valid:]

    data_dir = HERE / "data"
    write_jsonl(data_dir / "train.jsonl", [to_chat_line(system_prompt, e) for e in train_ex])
    write_jsonl(data_dir / "valid.jsonl", [to_chat_line(system_prompt, e) for e in valid_ex])
    write_jsonl(data_dir / "test.jsonl", [to_chat_line(system_prompt, e) for e in test_ex])

    print(f"train={len(train_ex)} valid={len(valid_ex)} test={len(test_ex)} (gesamt {n})")
    print("test.jsonl wird NIE an mlx_lm.lora --data uebergeben (nur eval.py liest es).")


if __name__ == "__main__":
    main()
