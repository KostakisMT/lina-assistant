"""Phase B: synthetischer Trainingsdaten-Generator.

Erzeugt rohe deutsche Dialogbeispiele fuers lokale Finetuning: mehrere
Formulierungsvarianten je Werkzeug (inkl. Whisper-Verhoerer), Raumgespraech-
Negativbeispiele und freie Konversation fuer Tonfall/Persona.

Standard-Backend: lokales Llama-3.2-3B ueber mlx-lm (kein API-Key, keine
Kosten). Alternativ `--backend claude` (braucht CLAUDE_API_KEY in
local.properties und Anthropic-Guthaben) fuer hoehere Qualitaet/Vielfalt,
falls verfuegbar.

Erzeugt EIN Beispiel pro Anfrage in einem simplen Zeilenformat
(EINGABE:/AUSGABE:) statt einer JSON-Liste mehrerer Beispiele - kleinere
lokale Modelle brechen bei mehreren Beispielen pro Antwort öfter das JSON
(unescapte Anfuehrungszeichen, abgeschnittene Arrays); ein einzelnes Paar
pro Antwort ist robuster zu parsen. Jedes generierte Beispiel wird gegen die
bekannte Werkzeug-Signatur validiert (siehe valid_tool_call) - ungueltige
werden verworfen, nicht "repariert". Pro Beispiel gibt es ein festes
Versuchslimit, damit ein durchgaengig falsch antwortendes Modell nicht zu
einer Endlosschleife fuehrt (das ist in einer frueheren Version tatsaechlich
passiert - siehe CHANGELOG 2026-08-04).

Schreibt Rohdaten nach data/raw/dialogue_raw.json (gitignored). build_dataset.py
/ build_router_dataset.py machen daraus anschliessend das Trainingsformat.

Aufruf:
    venv/bin/python gen_dialogue.py [--per-tool N] [--negatives N] [--persona N]
        [--backend local|claude] [--model REPO_ID]
"""

import argparse
import json
import re
from pathlib import Path
from typing import Callable

HERE = Path(__file__).parent
REPO_ROOT = HERE.parent.parent

LOCAL_DEFAULT_MODEL = "mlx-community/Llama-3.2-3B-Instruct-4bit"
CLAUDE_MODEL = "claude-sonnet-5"
MAX_ATTEMPTS_PER_EXAMPLE = 12

# Fiktive Namen aus CLAUDE.md (Nutzerprofil ist gitignored, hier bewusst nur
# die fiktiven Beispielnamen verwenden)
CONTACT_NAMES = [
    "Boris Hartmann", "Ulla Winter", "Annika Berger", "Sabine Dreyer",
    "Dirk Eßfeld", "Hannah Schäfer", "Gudrun Sommer", "Arundhati Brandt",
]

# Bewusst OHNE Anfuehrungszeichen um die Platzhalter (kein "key=\"Wert\""-
# Muster) - kleinere lokale Modelle haben sonst diese Beschreibung selbst
# als Beispielwert kopiert (z.B. woertlich text="Woran" ausgegeben), weil es
# visuell wie ein echter Aufruf aussieht. example_call() liefert das
# konkrete, korrekt formatierte Beispiel separat.
TOOLS = {
    "anrufen": "kontakt (Name des Kontakts) - ruft einen Kontakt an",
    "sms_senden": "kontakt (Name), text (Nachrichteninhalt) - sendet eine SMS",
    "sms_vorlesen": "keine Argumente - liest die neuesten SMS vor",
    "hoerbuch_abspielen": "keine Argumente - spielt das aktuelle Hoerbuch ab",
    "erinnerung_anlegen": (
        "text (woran erinnert werden soll), zeitpunkt (Datum+Uhrzeit als "
        "ISO 8601), taeglich (true oder false) - legt eine Erinnerung an"
    ),
    "erinnerungen_vorlesen": "keine Argumente - liest anstehende Erinnerungen vor",
    "termin_anlegen": (
        "titel (worum es geht), datum (ISO 8601), zeit (HH:MM, weglassen "
        "wenn keine genannt wurde) - legt einen Kalendertermin an"
    ),
    "dokument_vorlesen": "keine Argumente - liest ein vor dem Tablet liegendes Dokument vor",
    "stopp": "keine Argumente - stoppt Vorlesen oder Wiedergabe",
}

# Pflicht-Keyword-Argumente je Werkzeug (fuer Validierung generierter
# Beispiele) - reine Namensmenge, keine Werte. Optionale Argumente (z.B.
# "zeit" bei termin_anlegen) sind absichtlich NICHT als Pflicht gelistet,
# aber auch nicht verboten - siehe ALLOWED_EXTRA_ARGS.
TOOL_REQUIRED_ARGS = {
    "anrufen": {"kontakt"},
    "sms_senden": {"kontakt", "text"},
    "sms_vorlesen": set(),
    "hoerbuch_abspielen": set(),
    "erinnerung_anlegen": {"text", "zeitpunkt"},
    "erinnerungen_vorlesen": set(),
    "termin_anlegen": {"titel", "datum"},
    "dokument_vorlesen": set(),
    "stopp": set(),
}
TOOL_ALLOWED_EXTRA_ARGS = {
    "erinnerung_anlegen": {"taeglich"},
    "termin_anlegen": {"zeit"},
}
# Platzhaltertext aus den TOOLS-Signaturbeschreibungen - kleinere Modelle
# geben das manchmal woertlich als "Wert" zurueck statt es durch echten
# Inhalt zu ersetzen. Ein generiertes Beispiel mit einem dieser Werte ist
# offensichtlich kaputt.
PLACEHOLDER_VALUES = {
    "Name", "Nachricht", "Woran", "Worum es geht", "ISO 8601",
    "HH:MM oder leer", "true|false",
}

TOOL_CALL_RE = re.compile(r"^([a-z_]+)\((.*)\)$")
KWARG_RE = re.compile(r'(\w+)\s*=\s*"((?:[^"\\]|\\.)*)"')
# Muss die GESAMTE Argumentliste abdecken (re.fullmatch) - reines findall()
# uebersieht stillschweigend nicht passenden Rest (z.B. einen unquotierten
# Positionsparameter wie "hoerbuch_abspielen(Boris)", live beobachtet).
KWARG_LIST_RE = re.compile(r'\s*(?:\w+\s*=\s*"(?:[^"\\]|\\.)*"\s*(?:,\s*)?)*\s*')
EINGABE_RE = re.compile(r"EINGABE:\s*(.+)", re.IGNORECASE)
AUSGABE_RE = re.compile(r"AUSGABE:\s*(.+)", re.IGNORECASE)

# Ein Backend ist einfach: prompt (str) -> rohe Modellantwort (str)
Backend = Callable[[str], str]


def valid_tool_call(tool: str, output: str) -> bool:
    """Prueft ein generiertes Beispiel gegen die bekannte Werkzeug-Signatur.

    Kleinere lokale Modelle halten das geforderte Format nicht immer ein
    (falsche Argumentnamen, fehlende Klammern, erfundene Parameter, oder -
    beobachtet - das woertliche Kopieren der Platzhaltertext aus der
    Prompt-Beschreibung statt eines echten Werts). Ungueltige Beispiele
    werden verworfen statt "repariert", damit im Trainingsdatensatz nur
    syntaktisch korrekte Zielausgaben landen.
    """
    match = TOOL_CALL_RE.match(output.strip())
    if not match or match.group(1) != tool:
        return False
    required = TOOL_REQUIRED_ARGS.get(tool, set())
    allowed = required | TOOL_ALLOWED_EXTRA_ARGS.get(tool, set())
    args_str = match.group(2)
    if not args_str.strip():
        return not required
    if not KWARG_LIST_RE.fullmatch(args_str):
        return False  # z.B. unquotierter/positionaler Rest wie "Boris"
    kwargs = dict(KWARG_RE.findall(args_str))
    if set(kwargs) - allowed:
        return False  # unbekanntes/erfundenes Argument
    if not required.issubset(kwargs):
        return False  # Pflichtargument fehlt
    if any(v in PLACEHOLDER_VALUES for v in kwargs.values()):
        return False  # Platzhaltertext woertlich uebernommen
    return True


def make_local_backend(model_repo: str) -> Backend:
    """Lokales Llama/Gemma ueber mlx-lm - kein API-Key, keine Kosten."""
    from mlx_lm import generate, load

    print(f"Lade lokales Modell {model_repo} ...")
    model, tokenizer = load(model_repo)

    def backend(prompt: str) -> str:
        messages = [{"role": "user", "content": prompt}]
        rendered = tokenizer.apply_chat_template(messages, add_generation_prompt=True)
        return generate(model, tokenizer, prompt=rendered, max_tokens=300)

    return backend


def make_claude_backend() -> Backend:
    """Claude API - braucht CLAUDE_API_KEY in local.properties + Guthaben."""
    from anthropic import Anthropic

    props = (REPO_ROOT / "local.properties").read_text()
    api_key = None
    for line in props.splitlines():
        if line.strip().startswith("CLAUDE_API_KEY"):
            api_key = line.split("=", 1)[1].strip()
    if not api_key:
        raise RuntimeError("CLAUDE_API_KEY nicht in local.properties gefunden")
    client = Anthropic(api_key=api_key)

    def backend(prompt: str) -> str:
        response = client.messages.create(
            model=CLAUDE_MODEL,
            max_tokens=300,
            messages=[{"role": "user", "content": prompt}],
        )
        return "".join(b.text for b in response.content if b.type == "text")

    return backend


def parse_example(text: str) -> dict | None:
    eingabe = EINGABE_RE.search(text)
    ausgabe = AUSGABE_RE.search(text)
    if not eingabe or not ausgabe:
        return None
    return {"input": eingabe.group(1).strip(), "output": ausgabe.group(1).strip()}


# Erster Versuch war ein Konsistenz-Check ueber den (unfinetunten) Router-
# Prompt selbst - live getestet, aber verworfen: das Basismodell ist beim
# Router-Task zero-shot selbst schwach (11/20 im Mac-Spike, siehe README),
# also lehnte der Check reihenweise GUTE Beispiele ab, nur weil das
# Basismodell sie nicht korrekt klassifizierte (Ertragsrate 94% -> 12% im
# Test). Zirkelschluss: dieselbe Schwaeche, die wir wegtrainieren wollen,
# als Filter fuer die Trainingsdaten zu benutzen. Stattdessen ein billiger,
# gezielter Filter fuer genau das beobachtete Fehlerbild (eine inhaltsleere
# Begruessung wurde als sms_vorlesen()/stopp() beschriftet) - kein
# zusaetzlicher Modellaufruf noetig.
GENERIC_SMALLTALK_RE = re.compile(
    r"wie geht('?s| es (dir|ihnen))|wie läuft'?s|alles klar bei dir",
    re.IGNORECASE,
)


def is_generic_smalltalk(text: str) -> bool:
    return bool(GENERIC_SMALLTALK_RE.search(text))


def avoid_note(existing: list[dict]) -> str:
    if not existing:
        return ""
    sample = [ex["input"] for ex in existing[-8:]]
    joined = "\n".join(f"- {s}" for s in sample)
    return (
        "\n\nDiese Formulierungen wurden bereits benutzt - erzeuge KEINE "
        f"Wiederholung oder Variante davon, sondern etwas spuerbar anderes:\n{joined}"
    )


def example_call(tool: str) -> str:
    """Baut EINEN konkret korrekt formatierten Beispielaufruf - hilft
    kleineren Modellen mehr als eine abstrakte Signaturbeschreibung."""
    sample_values = {
        "kontakt": "Boris", "text": "Ich komme spaeter",
        "zeitpunkt": "2026-08-05T10:00", "taeglich": "false",
        "titel": "Zahnarzt", "datum": "2026-08-10", "zeit": "14:30",
    }
    args = ", ".join(
        f'{name}="{sample_values[name]}"' for name in sorted(TOOL_REQUIRED_ARGS[tool])
    )
    return f"{tool}({args})"


def collect(
    n: int,
    build_prompt: Callable[[list[dict]], str],
    backend: Backend,
    is_valid: Callable[[str], bool],
    seed_inputs: set[str] = frozenset(),
    semantic_check: Callable[[str], bool] | None = None,
) -> list[dict]:
    """Generisches Sammel-Loop: fragt einzeln nach Beispielen, validiert,
    bricht garantiert nach n * MAX_ATTEMPTS_PER_EXAMPLE Versuchen ab (auch
    wenn n nie erreicht wird) - verhindert eine Endlosschleife, falls das
    Modell durchgaengig ungueltige Antworten liefert.

    [seed_inputs]: bereits vorhandene Eingaben (z.B. aus --append) - werden
    zusaetzlich zu neu generierten als "schon benutzt" behandelt, damit
    mehrere kleine Laeufe sich nicht gegenseitig duplizieren.

    [semantic_check]: optionaler Konsistenz-Check - prueft, ob EINGABE und
    AUSGABE tatsaechlich zusammenpassen (nicht nur, ob AUSGABE syntaktisch
    gueltig ist), z.B. is_generic_smalltalk() fuer Werkzeug-Beispiele.
    """
    examples: list[dict] = []
    seen_inputs: set[str] = set(seed_inputs)
    attempts = 0
    max_attempts = n * MAX_ATTEMPTS_PER_EXAMPLE
    while len(examples) < n and attempts < max_attempts:
        attempts += 1
        text = backend(build_prompt(examples))
        ex = parse_example(text)
        if not ex or not ex["input"] or not is_valid(ex["output"]):
            continue
        key = ex["input"].strip().lower()
        if key in seen_inputs:
            continue  # das Modell ignoriert avoid_note() gelegentlich
        if semantic_check and not semantic_check(ex["input"]):
            continue  # Eingabe und Ausgabe passen nicht zusammen
        seen_inputs.add(key)
        examples.append(ex)
    if len(examples) < n:
        print(f"  (nur {len(examples)}/{n} gueltige Beispiele nach {attempts} Versuchen)")
    return examples


def gen_tool_examples(
    backend: Backend, tool: str, signature: str, n: int,
    seed_inputs: set[str] = frozenset(),
) -> list[dict]:
    names = ", ".join(CONTACT_NAMES)
    demo = example_call(tool)
    # Illustration passend zur Arg-Form des Zielwerkzeugs waehlen (nicht
    # immer ein Beispiel MIT Argumenten zeigen) - sonst haengt das Modell
    # bei Null-Argument-Werkzeugen unnoetig Argumente an, siehe CHANGELOG.
    if TOOL_REQUIRED_ARGS[tool]:
        illustration_input = "Ruf doch mal Boris an, wenn du Zeit hast"
        illustration_output = 'anrufen(kontakt="Boris")'
    else:
        illustration_input = "Bitte stoppe das Vorlesen"
        illustration_output = "stopp()"

    def build_prompt(existing: list[dict]) -> str:
        return f"""Du hilfst, Trainingsdaten fuer einen deutschen Sprachassistenten
(Lina, fuer einen blinden Nutzer) zu erzeugen. Erzeuge EIN Beispiel: eine
realistische deutsche Spracheingabe, die das Werkzeug `{tool}` mit der
Signatur {signature} ausloesen sollte.

Variiere: hoeflich vs. direkt/Imperativ, kurz vs. umstaendlich, gelegentlich
ein realistisches Spracherkennungs-Verhoerer (Whisper macht manchmal Fehler
wie "Rumfe" statt "Ruf" oder vertauschte Silben) - der Sinn muss erkennbar
bleiben. Falls die Signatur einen Kontakt braucht, nutze NUR einen Namen aus
dieser Liste (Vorname reicht meist): {names}.

WICHTIG: AUSGABE muss IMMER exakt in diesem Format sein, mit einem echten
Wert statt eines Platzhalters, genau diesen Argumentnamen, keinen erfundenen
zusaetzlichen Argumenten. Braucht das Werkzeug keine Argumente, sind die
Klammern LEER: `{tool}()`.{avoid_note(existing)}

Antworte in GENAU diesem Format, keine Erklaerung, keine Anfuehrungszeichen
um die Werte, kein zusaetzlicher Text davor oder danach - hier ein
vollstaendiges Beispiel fuer ein ANDERES Werkzeug (Inhalt nur zur
Illustration des Formats, nicht wiederholen):
EINGABE: {illustration_input}
AUSGABE: {illustration_output}

Jetzt dein Beispiel fuer das Werkzeug `{tool}` (Aufruf-Syntax: {demo}):
EINGABE: <Spracheingabe>
AUSGABE: {tool}(<passende Argumente>)"""

    # Fuer Werkzeug-Beispiele: inhaltsleere Begruessungen ohne jeden
    # Handlungsbezug rausfiltern (siehe is_generic_smalltalk-Kommentar) -
    # bei Raumgespraech/Persona-Beispielen waeren solche Saetze dagegen
    # legitim, dort nicht angewendet.
    return collect(
        n, build_prompt, backend, lambda out: valid_tool_call(tool, out), seed_inputs,
        semantic_check=lambda inp: not is_generic_smalltalk(inp),
    )


def gen_negative_examples(backend: Backend, n: int, seed_inputs: set[str] = frozenset()) -> list[dict]:
    """AUSGABE ist hier immer dieselbe feste Zeichenkette - die wird direkt
    im Code gesetzt statt vom Modell verlangt. Frueher (siehe CHANGELOG
    2026-08-04) brach das Modell nach der EINGABE-Zeile ab, wenn es auch die
    triviale, immer gleiche AUSGABE-Zeile generieren sollte - vermutlich weil
    es nichts Neues beizutragen hatte."""

    def build_prompt(existing: list[dict]) -> str:
        return f"""Du hilfst, Trainingsdaten fuer einen deutschen Sprachassistenten
(Lina) zu erzeugen, der nach dem Weckwort "Hey Lina" weiter zuhoert. Erzeuge
EINEN realistischen deutschen Satz, den das Mikrofon aufschnappen KOENNTE,
der aber NICHT an Lina gerichtet ist: ein Gespraech zwischen Personen im
Raum, eine Antwort an jemand anderen ("ja mach ich"), Fernsehton, ein
Selbstgespraech. Keine Frage/Bitte, die wie an einen Assistenten gerichtet
klingt.{avoid_note(existing)}

Antworte NUR mit dem Satz selbst, keine Anfuehrungszeichen, keine Erklaerung,
kein Praefix wie "EINGABE:"."""

    examples: list[dict] = []
    seen_inputs: set[str] = set(seed_inputs)
    attempts = 0
    max_attempts = n * MAX_ATTEMPTS_PER_EXAMPLE
    while len(examples) < n and attempts < max_attempts:
        attempts += 1
        text = backend(build_prompt(examples)).strip().strip('"').strip()
        first_line = text.splitlines()[0].strip() if text else ""
        key = first_line.lower()
        if not first_line or TOOL_CALL_RE.match(first_line) or key in seen_inputs:
            continue
        seen_inputs.add(key)
        examples.append({"input": first_line, "output": "gespraech_beenden()"})
    if len(examples) < n:
        print(f"  (nur {len(examples)}/{n} gueltige Beispiele nach {attempts} Versuchen)")
    return examples


def gen_persona_examples(
    backend: Backend, n: int, seed_inputs: set[str] = frozenset(),
) -> list[dict]:
    def build_prompt(existing: list[dict]) -> str:
        return f"""Du hilfst, Trainingsdaten fuer den deutschen Sprachassistenten Lina
zu erzeugen (fuer einen blinden Nutzer, der sich fuer Politik, Wirtschaft,
Wissenschaft, Segeln und Marxismus/politische Theorie interessiert). Denk dir
EINE freie Frage oder Aussage an Lina aus, die KEIN Geraetebefehl ist, und
Linas passende Antwort: warm, freundlich, kurz (1-3 Saetze), kein Markdown,
keine Emojis, wie eine gute Bekannte - nicht wie ein Callcenter oder eine
foermliche KI.{avoid_note(existing)}

Antworte in GENAU diesem Format, keine Erklaerung, kein zusaetzlicher Text
davor oder danach - hier ein vollstaendiges Beispiel (Inhalt nur zur
Illustration, nicht wiederholen):
EINGABE: Was hältst du von Tolstoi?
AUSGABE: Ich mag seine Erzählungen sehr, sie sind so klar geschrieben.

Jetzt dein eigenes Beispiel:
EINGABE: <Frage/Aussage>
AUSGABE: <Linas Antwort als reiner Text>"""

    return collect(
        n, build_prompt, backend,
        lambda out: bool(out.strip()) and not TOOL_CALL_RE.match(out.strip()),
        seed_inputs,
    )


def load_existing_by_category(path: Path) -> dict[str, set[str]]:
    if not path.exists():
        return {}
    data = json.loads(path.read_text())
    by_cat: dict[str, set[str]] = {}
    for ex in data:
        by_cat.setdefault(ex["category"], set()).add(ex["input"].strip().lower())
    return by_cat


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--per-tool", type=int, default=4)
    parser.add_argument("--negatives", type=int, default=8)
    parser.add_argument("--persona", type=int, default=8)
    parser.add_argument("--backend", choices=["local", "claude"], default="local")
    parser.add_argument(
        "--model", default=LOCAL_DEFAULT_MODEL,
        help="Nur fuer --backend local: mlx-community-Repo-ID",
    )
    parser.add_argument(
        "--append", action="store_true",
        help="Neue Beispiele zu data/raw/dialogue_raw.json HINZUFUEGEN statt "
             "sie zu ersetzen - Dubletten werden ueber alle bisherigen Laeufe "
             "hinweg vermieden. Fuer lokale Generierung gedacht: mehrere "
             "kleine Laeufe (hohe Trefferquote) statt eines grossen (das "
             "Modell wiederholt sich bei hohen n schnell, siehe README).",
    )
    args = parser.parse_args()

    backend = make_local_backend(args.model) if args.backend == "local" else make_claude_backend()

    out_dir = HERE / "data" / "raw"
    out_path = out_dir / "dialogue_raw.json"
    existing = load_existing_by_category(out_path) if args.append else {}
    prior_examples = json.loads(out_path.read_text()) if args.append and out_path.exists() else []

    examples = []
    for tool, signature in TOOLS.items():
        print(f"Generiere {args.per_tool} Beispiele fuer {tool} ...")
        seeds = existing.get(f"tool:{tool}", set())
        batch = gen_tool_examples(backend, tool, signature, args.per_tool, seeds)
        for ex in batch:
            ex["category"] = f"tool:{tool}"
        examples.extend(batch)

    print(f"Generiere {args.negatives} Raumgespraech-Negativbeispiele ...")
    negatives = gen_negative_examples(backend, args.negatives, existing.get("silence", set()))
    for ex in negatives:
        ex["category"] = "silence"
    examples.extend(negatives)

    print(f"Generiere {args.persona} Persona-Beispiele ...")
    persona = gen_persona_examples(backend, args.persona, existing.get("persona", set()))
    for ex in persona:
        ex["category"] = "persona"
    examples.extend(persona)

    out_dir.mkdir(parents=True, exist_ok=True)
    all_examples = prior_examples + examples
    out_path.write_text(json.dumps(all_examples, ensure_ascii=False, indent=2))
    if args.append:
        print(f"\n{len(examples)} neue Beispiele hinzugefuegt, {len(all_examples)} insgesamt in {out_path}")
    else:
        print(f"\n{len(all_examples)} Rohbeispiele geschrieben nach {out_path}")


if __name__ == "__main__":
    main()
