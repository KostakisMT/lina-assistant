"""Findet den besten Early-Stopping-Punkt: laedt jeden waehrend des Trainings
gespeicherten Zwischen-Checkpoint (0000NNN_adapters.safetensors) und
evaluiert ihn gegen den gehaltenen Testsatz, genau wie eval.py fuer den
finalen Adapter. Braucht --save-every beim Training, damit genug
Zwischenstaende existieren.

Aufruf:
    venv/bin/python sweep_checkpoints.py --adapter-dir adapters/llama_router_v2 \
        [--model REPO_ID] [--data data/router]
"""

import argparse
import json
import shutil
import tempfile
from pathlib import Path

from mlx_lm import generate, load

from spike_check import DEFAULT_MODEL, classify

HERE = Path(__file__).parent


def score(model, tokenizer, rows, max_tokens: int) -> tuple[int, int]:
    correct = 0
    for row in rows:
        system_msg, user_msg, gold_msg = row["messages"]
        prompt = tokenizer.apply_chat_template(
            [system_msg, user_msg], add_generation_prompt=True
        )
        output = generate(model, tokenizer, prompt=prompt, max_tokens=max_tokens)
        exp_type, exp_tool = classify(gold_msg["content"])
        got_type, got_tool = classify(output)
        ok = exp_type == got_type and (exp_type != "tool" or exp_tool == got_tool)
        correct += ok
    return correct, len(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--adapter-dir", required=True)
    parser.add_argument("--data", default="data/router")
    parser.add_argument("--max-tokens", type=int, default=80)
    parser.add_argument(
        "--limit", type=int, default=None,
        help="Nur die ersten N Testbeispiele nutzen (schneller grober Sweep)",
    )
    args = parser.parse_args()

    adapter_dir = HERE / args.adapter_dir
    test_path = HERE / args.data / "test.jsonl"
    rows = [json.loads(line) for line in test_path.read_text().splitlines() if line.strip()]
    if args.limit:
        rows = rows[: args.limit]
    config_path = adapter_dir / "adapter_config.json"

    checkpoints = sorted(adapter_dir.glob("[0-9]*_adapters.safetensors"))
    if not checkpoints:
        raise SystemExit(
            f"Keine nummerierten Checkpoints in {adapter_dir} gefunden - "
            "Training mit --save-every laufen lassen"
        )

    results = []
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        shutil.copy(config_path, tmp_path / "adapter_config.json")
        for ckpt in checkpoints:
            shutil.copy(ckpt, tmp_path / "adapters.safetensors")
            model, tokenizer = load(args.model, adapter_path=str(tmp_path))
            correct, total = score(model, tokenizer, rows, args.max_tokens)
            iter_num = ckpt.stem.split("_")[0].lstrip("0") or "0"
            results.append((int(iter_num), correct, total))
            print(f"Iter {iter_num}: {correct}/{total} korrekt")

    print()
    print("Zusammenfassung:")
    for iter_num, correct, total in results:
        bar = "#" * correct
        print(f"  Iter {iter_num:>5}: {correct:>2}/{total}  {bar}")
    best = max(results, key=lambda r: r[1])
    print(f"\nBeste Iteration: {best[0]} mit {best[1]}/{best[2]} korrekt")


if __name__ == "__main__":
    main()
