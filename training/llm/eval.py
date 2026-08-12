"""Phase C: Eval gegen den gehaltenen Testsatz (data/test.jsonl).

data/test.jsonl wird NIE fuers Training benutzt (siehe build_dataset.py).
Laedt Basis-Modell (+ optional LoRA-Adapter), generiert fuer jede
Test-Zeile eine Antwort und vergleicht sie mit der ueber die gleiche
classify()-Heuristik wie spike_check.py klassifizierten Zielantwort.

Aufruf:
    venv/bin/python eval.py [--adapter-path adapters/v1] [--model REPO_ID] [--data data]
"""

import argparse
import json
from pathlib import Path

from mlx_lm import generate, load

from spike_check import DEFAULT_MODEL, classify

HERE = Path(__file__).parent


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--adapter-path", default=None)
    parser.add_argument("--max-tokens", type=int, default=80)
    parser.add_argument("--data", default="data", help="Verzeichnis mit test.jsonl")
    args = parser.parse_args()

    test_path = HERE / args.data / "test.jsonl"
    rows = [json.loads(line) for line in test_path.read_text().splitlines() if line.strip()]

    print(f"Lade {args.model} (adapter={args.adapter_path or '-'}) ...")
    model, tokenizer = load(args.model, adapter_path=args.adapter_path)

    correct = 0
    for row in rows:
        messages = row["messages"]
        system_msg, user_msg, gold_msg = messages[0], messages[1], messages[2]
        prompt = tokenizer.apply_chat_template(
            [system_msg, user_msg], add_generation_prompt=True
        )
        output = generate(model, tokenizer, prompt=prompt, max_tokens=args.max_tokens)

        exp_type, exp_tool = classify(gold_msg["content"])
        got_type, got_tool = classify(output)
        ok = exp_type == got_type and (exp_type != "tool" or exp_tool == got_tool)
        correct += ok

        mark = "OK  " if ok else "FAIL"
        print(f"[{mark}] {user_msg['content']!r}")
        print(f"       erwartet={exp_type}/{exp_tool}  erhalten={got_type}/{got_tool}")
        if not ok:
            print(f"       Rohausgabe: {output.strip()!r}")

    print()
    print(f"Ergebnis: {correct}/{len(rows)} korrekt (adapter={args.adapter_path or 'kein Adapter/Basismodell'})")


if __name__ == "__main__":
    main()
