"""Phase B: synthetischer Trainingsdaten-Generator (Bootstrap ueber Claude).

Nutzt den bestehenden CLAUDE_API_KEY aus local.properties, um rohe deutsche
Dialogbeispiele fuer das lokale Gemma-3n-Finetuning zu erzeugen: mehrere
Formulierungsvarianten je Werkzeug (inkl. Whisper-Verhoerer), Raumgespraech-
Negativbeispiele und freie Konversation fuer Tonfall/Persona.

Schreibt Rohdaten nach data/raw/dialogue_raw.json (gitignored). build_dataset.py
macht daraus anschliessend das MLX-Chat-JSONL-Trainingsformat.

Aufruf:
    venv/bin/python gen_dialogue.py [--per-tool N] [--negatives N] [--persona N] [--batch-size N]

Grosse N werden in mehreren Anfragen a --batch-size Beispielen erzeugt
(bessere Vielfalt als ein einzelner Riesen-Prompt, robuster gegen
abgeschnittene Antworten). Jede Anfrage bekommt eine Stichprobe der bereits
erzeugten Eingaben, um Wiederholungen zu vermeiden.
"""

import argparse
import json
import re
from pathlib import Path

from anthropic import Anthropic

HERE = Path(__file__).parent
REPO_ROOT = HERE.parent.parent

MODEL = "claude-sonnet-5"

# Fiktive Namen aus CLAUDE.md (Nutzerprofil ist gitignored, hier bewusst nur
# die fiktiven Beispielnamen verwenden)
CONTACT_NAMES = [
    "Boris Hartmann", "Ulla Winter", "Annika Berger", "Sabine Dreyer",
    "Dirk Eßfeld", "Hannah Schäfer", "Gudrun Sommer", "Arundhati Brandt",
]

TOOLS = {
    "anrufen": "kontakt=\"Name\" - ruft einen Kontakt an",
    "sms_senden": "kontakt=\"Name\", text=\"Nachricht\" - sendet eine SMS",
    "sms_vorlesen": "(keine Parameter) - liest die neuesten SMS vor",
    "hoerbuch_abspielen": "(keine Parameter) - spielt das aktuelle Hoerbuch ab",
    "erinnerung_anlegen": (
        "text=\"Woran\", zeitpunkt=\"ISO 8601\", taeglich=\"true|false\" - "
        "legt eine Erinnerung an"
    ),
    "erinnerungen_vorlesen": "(keine Parameter) - liest anstehende Erinnerungen vor",
    "termin_anlegen": (
        "titel=\"Worum es geht\", datum=\"ISO 8601\", zeit=\"HH:MM oder leer\" - "
        "legt einen Kalendertermin an"
    ),
    "dokument_vorlesen": "(keine Parameter) - liest ein vor dem Tablet liegendes Dokument vor",
    "stopp": "(keine Parameter) - stoppt Vorlesen oder Wiedergabe",
}

JSON_ARRAY_RE = re.compile(r"\[.*\]", re.DOTALL)


def read_api_key() -> str:
    props = (REPO_ROOT / "local.properties").read_text()
    for line in props.splitlines():
        if line.strip().startswith("CLAUDE_API_KEY"):
            return line.split("=", 1)[1].strip()
    raise RuntimeError("CLAUDE_API_KEY nicht in local.properties gefunden")


def ask_for_json(client: Anthropic, prompt: str, retries: int = 2) -> list[dict]:
    last_error: Exception | None = None
    for attempt in range(retries + 1):
        response = client.messages.create(
            model=MODEL,
            max_tokens=4096,
            messages=[{"role": "user", "content": prompt}],
        )
        text = "".join(b.text for b in response.content if b.type == "text")
        match = JSON_ARRAY_RE.search(text)
        if match:
            return json.loads(match.group(0))
        last_error = RuntimeError(
            f"Keine JSON-Liste in der Antwort gefunden (Versuch {attempt + 1}): {text[:200]!r}"
        )
    raise last_error


def avoid_note(existing: list[dict]) -> str:
    if not existing:
        return ""
    sample = [ex["input"] for ex in existing[-15:]]
    joined = "\n".join(f"- {s}" for s in sample)
    return (
        "\n\nDiese Formulierungen wurden bereits benutzt - erzeuge KEINE "
        f"Wiederholungen oder Varianten davon, sondern spuerbar andere:\n{joined}"
    )


def gen_tool_examples(client: Anthropic, tool: str, signature: str, n: int, batch_size: int) -> list[dict]:
    names = ", ".join(CONTACT_NAMES)
    examples: list[dict] = []
    while len(examples) < n:
        batch_n = min(batch_size, n - len(examples))
        prompt = f"""Du hilfst, Trainingsdaten fuer einen deutschen Sprachassistenten
(Lina, fuer einen blinden Nutzer) zu erzeugen. Erzeuge {batch_n} verschiedene,
realistische deutsche Spracheingaben, die alle das Werkzeug `{tool}` mit der
Signatur {signature} ausloesen sollten.

Variiere bewusst: hoeflich vs. direkt/Imperativ, kurz vs. umstaendlich, und
bei mindestens einer Variante ein realistisches Spracherkennungs-Verhoerer
(Whisper macht manchmal Fehler wie "Rumfe" statt "Ruf" oder vertauschte
Silben) - aber der Sinn muss erkennbar bleiben. Falls die Signatur einen
Kontakt braucht, nutze NUR Namen aus dieser Liste (Vorname reicht meist):
{names}.{avoid_note(examples)}

Antworte NUR mit einer JSON-Liste, keine Erklaerung, Format:
[{{"input": "<Spracheingabe>", "output": "{tool}(<passende Argumente>)"}}]"""
        examples.extend(ask_for_json(client, prompt))
    return examples[:n]


def gen_negative_examples(client: Anthropic, n: int, batch_size: int) -> list[dict]:
    examples: list[dict] = []
    while len(examples) < n:
        batch_n = min(batch_size, n - len(examples))
        prompt = f"""Du hilfst, Trainingsdaten fuer einen deutschen Sprachassistenten
(Lina) zu erzeugen, der nach dem Weckwort "Hey Lina" weiter zuhoert. Erzeuge
{batch_n} realistische deutsche Saetze, die das Mikrofon aufschnappen KOENNTE,
die aber NICHT an Lina gerichtet sind: Gespraeche zwischen Personen im Raum,
Antworten an jemand anderen ("ja mach ich"), Fernsehton, Selbstgespraeche.
Keine Frage/Bitte, die wie an einen Assistenten gerichtet klingt.{avoid_note(examples)}

Antworte NUR mit einer JSON-Liste, Format:
[{{"input": "<Satz>", "output": "gespraech_beenden()"}}]"""
        examples.extend(ask_for_json(client, prompt))
    return examples[:n]


def gen_persona_examples(client: Anthropic, n: int, batch_size: int) -> list[dict]:
    examples: list[dict] = []
    while len(examples) < n:
        batch_n = min(batch_size, n - len(examples))
        prompt = f"""Du hilfst, Trainingsdaten fuer den deutschen Sprachassistenten Lina
zu erzeugen (fuer einen blinden Nutzer, der sich fuer Politik, Wirtschaft,
Wissenschaft, Segeln und Marxismus/politische Theorie interessiert). Erzeuge
{batch_n} Beispielpaare aus (a) einer freien Frage/Aussage an Lina, die KEIN
Geraetebefehl ist, und (b) Linas passender Antwort: warm, freundlich, kurz
(1-3 Saetze), kein Markdown, keine Emojis, wie eine gute Bekannte - nicht wie
ein Callcenter oder eine foermliche KI.{avoid_note(examples)}

Antworte NUR mit einer JSON-Liste, Format:
[{{"input": "<Frage/Aussage>", "output": "<Linas Antwort als reiner Text>"}}]"""
        examples.extend(ask_for_json(client, prompt))
    return examples[:n]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--per-tool", type=int, default=4)
    parser.add_argument("--negatives", type=int, default=8)
    parser.add_argument("--persona", type=int, default=8)
    parser.add_argument("--batch-size", type=int, default=8)
    args = parser.parse_args()

    client = Anthropic(api_key=read_api_key())

    examples = []
    for tool, signature in TOOLS.items():
        print(f"Generiere {args.per_tool} Beispiele fuer {tool} ...")
        batch = gen_tool_examples(client, tool, signature, args.per_tool, args.batch_size)
        for ex in batch:
            ex["category"] = f"tool:{tool}"
        examples.extend(batch)

    print(f"Generiere {args.negatives} Raumgespraech-Negativbeispiele ...")
    negatives = gen_negative_examples(client, args.negatives, args.batch_size)
    for ex in negatives:
        ex["category"] = "silence"
    examples.extend(negatives)

    print(f"Generiere {args.persona} Persona-Beispiele ...")
    persona = gen_persona_examples(client, args.persona, args.batch_size)
    for ex in persona:
        ex["category"] = "persona"
    examples.extend(persona)

    out_dir = HERE / "data" / "raw"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "dialogue_raw.json"
    out_path.write_text(json.dumps(examples, ensure_ascii=False, indent=2))
    print(f"\n{len(examples)} Rohbeispiele geschrieben nach {out_path}")


if __name__ == "__main__":
    main()
