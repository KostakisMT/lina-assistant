"""Phase A: Mac-Spike gegen ein handgeschriebenes deutsches Eval-Set.

Laedt ein Basis-Gemma-3n-Checkpoint (kein Finetuning) ueber mlx-lm, schickt
jeden Prompt aus prompts/eval_handwritten.jsonl durch (mit dem Lina-
System-Prompt aus prompts/system_prompt_de.txt) und vergleicht die Antwort
grob gegen das erwartete Verhalten (Tool-Aufruf / freier Text / Schweigen).

Kein Android, kein Zielgeraet noetig - beantwortet nur, ob die
Konversationsqualitaet ueberhaupt vielversprechend ist, bevor Zeit in
Trainingsdaten und Finetuning investiert wird.

Aufruf:
    venv/bin/python spike_check.py [--model REPO_ID] [--adapter-path PFAD]
"""

import argparse
import json
import re
from pathlib import Path

from mlx_lm import generate, load

HERE = Path(__file__).parent
DEFAULT_MODEL = "mlx-community/gemma-3n-E2B-it-lm-4bit"

TOOL_NAMES = {
    "anrufen",
    "sms_senden",
    "sms_vorlesen",
    "hoerbuch_abspielen",
    "erinnerung_anlegen",
    "erinnerungen_vorlesen",
    "termin_anlegen",
    "dokument_vorlesen",
    "stopp",
    "gespraech_beenden",
}

TOOL_CALL_RE = re.compile(r"^\s*([a-z_]+)\(([^)]*)\)\s*$")


def classify(output: str) -> tuple[str, str | None]:
    """Grobe Klassifikation: (typ, werkzeugname) mit typ in tool/silence/say."""
    match = TOOL_CALL_RE.match(output.strip())
    if match and match.group(1) in TOOL_NAMES:
        tool = match.group(1)
        if tool == "gespraech_beenden":
            return "silence", tool
        return "tool", tool
    return "say", None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--adapter-path", default=None)
    parser.add_argument("--max-tokens", type=int, default=80)
    args = parser.parse_args()

    system_prompt = (HERE / "prompts" / "system_prompt_de.txt").read_text()
    eval_path = HERE / "prompts" / "eval_handwritten.jsonl"
    cases = [json.loads(line) for line in eval_path.read_text().splitlines() if line.strip()]

    print(f"Lade {args.model} (adapter={args.adapter_path or '-'}) ...")
    model, tokenizer = load(args.model, adapter_path=args.adapter_path)

    correct = 0
    results = []
    for case in cases:
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": case["input"]},
        ]
        prompt = tokenizer.apply_chat_template(messages, add_generation_prompt=True)
        output = generate(model, tokenizer, prompt=prompt, max_tokens=args.max_tokens)
        got_type, got_tool = classify(output)

        expected_type = case["expected_type"]
        expected_tool = case.get("expected_tool")
        ok = got_type == expected_type and (
            expected_type != "tool" or got_tool == expected_tool
        )
        correct += ok

        results.append(
            {
                "input": case["input"],
                "expected": f"{expected_type}" + (f"/{expected_tool}" if expected_tool else ""),
                "got": f"{got_type}" + (f"/{got_tool}" if got_tool else ""),
                "ok": ok,
                "output": output.strip(),
            }
        )

    print()
    for r in results:
        mark = "OK  " if r["ok"] else "FAIL"
        print(f"[{mark}] {r['input']!r}")
        print(f"       erwartet={r['expected']}  erhalten={r['got']}")
        if not r["ok"]:
            print(f"       Rohausgabe: {r['output']!r}")
    print()
    print(f"Ergebnis: {correct}/{len(cases)} korrekt")


if __name__ == "__main__":
    main()
