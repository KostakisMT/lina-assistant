"""Fallback-Architektur (Pivot 2026-08-04, siehe README): Gemma 3n selbst
bleibt unfinetuned/prompt-basiert fuer freie Konversation. Ein kleines,
architektonisch normales Modell (Gemma-3-270M-it, KEIN "3n" - keine AltUp-
Probleme) wird stattdessen als Router finegetuned: trifft die Eingabe ein
Werkzeug, ist sie Raumgespraech, oder ist sie eine echte Frage/Bitte AN Lina
(dann frei_gespraech() -> Weitergabe an Gemma 3n)?

Baut aus den bestehenden Rohdaten (data/raw/dialogue_raw.json) einen
Router-Datensatz: tool:*/silence-Kategorien bleiben wie sie sind, die
persona-Kategorie wird auf frei_gespraech() umgemappt (die Eingabe bleibt
wertvoll als Trainingsbeispiel fuers Erkennen von "das ist eine echte Frage",
nur die Zielausgabe aendert sich - der Router soll nicht selbst antworten).

Aufruf:
    venv/bin/python build_router_dataset.py [--valid-frac 0.1] [--test-frac 0.15]
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


def to_router_example(ex: dict) -> dict:
    if ex["category"] == "persona":
        return {"input": ex["input"], "output": "frei_gespraech()"}
    return {"input": ex["input"], "output": ex["output"]}


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

    system_prompt = (HERE / "prompts" / "system_prompt_router_de.txt").read_text()
    raw = load_raw()
    if not raw:
        raise SystemExit("Keine Rohdaten in data/raw/ gefunden - erst gen_dialogue.py laufen lassen")
    examples = [to_router_example(ex) for ex in raw]

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

    out_dir = HERE / "data" / "router"
    out_dir.mkdir(parents=True, exist_ok=True)
    write_jsonl(out_dir / "train.jsonl", [to_chat_line(system_prompt, e) for e in train_ex])
    write_jsonl(out_dir / "valid.jsonl", [to_chat_line(system_prompt, e) for e in valid_ex])
    write_jsonl(out_dir / "test.jsonl", [to_chat_line(system_prompt, e) for e in test_ex])

    print(f"train={len(train_ex)} valid={len(valid_ex)} test={len(test_ex)} (gesamt {n})")
    print("test.jsonl wird NIE an mlx_lm.lora --data uebergeben (nur eval.py liest es).")


if __name__ == "__main__":
    main()
