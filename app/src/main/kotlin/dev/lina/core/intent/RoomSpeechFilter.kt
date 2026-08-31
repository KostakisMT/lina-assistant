package dev.lina.core.intent

/**
 * Entscheidet **auf dem Gerät**, ob eine Äußerung an Lina gerichtet war –
 * bevor irgendetwas an die Claude-API geht.
 *
 * ## Warum lokal
 *
 * Nach jeder Antwort hört Lina kurz weiter zu, ohne dass das Weckwort nötig
 * ist ("Folgefenster"). Bis 2026-08-30 ging alles, was in dieser Zeit
 * ankam, an die API. Die Prüfung "war das überhaupt an mich gerichtet"
 * stand zwar im Claude-Systemprompt (`gespraech_beenden`) – aber damit
 * **erst, nachdem die Äußerung das Gerät verlassen hatte**.
 *
 * Am Gerät beobachtet: Während im Zimmer ein Videotelefonat lief, nahm Lina
 * eine Passage daraus auf und schickte sie an die API. Betroffen ist auch
 * **Besuch**, der dem nie zugestimmt hat und nichts davon weiß.
 *
 * ## Warum im Zweifel blockiert wird
 *
 * Die beiden Fehlerrichtungen wiegen völlig unterschiedlich:
 *
 * - **Fälschlich blockiert:** Der Nutzer sagt "Hey Lina" und wiederholt sich.
 *   Reibung, mehr nicht – das Weckwort funktioniert unabhängig von diesem
 *   Filter und bleibt der verlässliche Weg.
 * - **Fälschlich durchgelassen:** Ein fremdes Gespräch verlässt die Wohnung.
 *   Nicht rückholbar, und niemand bemerkt es.
 *
 * Das Folgefenster ist Bequemlichkeit, das Weckwort ist die Garantie.
 * Deshalb gilt: **[Verdict.UNCERTAIN] wird behandelt wie nicht adressiert.**
 *
 * ## Wie entschieden wird
 *
 * Kein einzelnes Merkmal entscheidet. Positive und negative Indizien werden
 * gewichtet aufsummiert; die Schwellen stehen als Konstanten unten und sind
 * bewusst gut sichtbar, weil sie im Betrieb nachjustiert werden müssen.
 *
 * Eine einzige Abkürzung umgeht die Punktevergabe: die direkte Anrede
 * "Lina" ist immer adressiert.
 *
 * **Der lokale Resolver ist ausdrücklich KEIN Freifahrtschein.** Naheliegend
 * wäre, eine erkannte Regex-Übereinstimmung als "ist ein Befehl, also an Lina
 * gerichtet" zu werten. Das wäre falsch und gefährlich: "ich ruf dich später
 * an" trifft `resolveCall` – und ist die klassische Absprache unter Menschen,
 * also genau der Fall, den dieser Filter fangen soll. `LauncherActivity`
 * hält dasselbe für das Gesprächsfenster schon länger fest ("die Regex-Ebene
 * ist für Raumgespräche zu triggerfreudig").
 *
 * Der Filter ist absichtlich frei von Android-Typen und ohne Zustand, damit
 * er vollständig unit-testbar bleibt.
 */
object RoomSpeechFilter {

    enum class Verdict {
        /** Eindeutig an Lina gerichtet – darf an die API. */
        ADDRESSED,

        /** Erkennbar ein Gespräch im Raum – bleibt auf dem Gerät. */
        ROOM_CONVERSATION,

        /** Nicht entscheidbar – wird wie [ROOM_CONVERSATION] behandelt. */
        UNCERTAIN,
        ;

        /** Ob die Äußerung das Gerät verlassen darf. */
        fun mayReachCloud(): Boolean = this == ADDRESSED
    }

    /** Nachvollziehbare Begründung – fürs Log und für die Tests. */
    data class Result(
        val verdict: Verdict,
        val score: Int,
        val signals: List<String>,
    )

    // ── Direkte Anrede ───────────────────────────────────────────────────

    private val ANREDE = Regex("""\blina\b""", RegexOption.IGNORE_CASE)

    // ── Positive Indizien ────────────────────────────────────────────────

    /** Bitten/Befehle, wie man sie an eine Assistentin richtet. */
    private val IMPERATIVE = Regex(
        """\b(?:erzähl|erzaehl|sag|sage|lies|lese|spiel|spiele|such|suche|zeig|zeige|""" +
            """nenn|nenne|erklär|erklaer|wiederhol|wiederhole|mach|schreib|schreibe|""" +
            """ruf|rufe|starte|öffne|oeffne|stopp|halt|weiter)\w*\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Höfliche Modalfragen: "kannst du", "weißt du" … */
    private val MODAL_FRAGE = Regex(
        """\b(?:kannst|könntest|koenntest|würdest|wuerdest|weißt|weisst|kennst|hast|""" +
            """darfst|magst|willst)\s+du\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Fragewörter am Anfang – typisch für eine Frage an Lina. */
    private val FRAGEWORT_ANFANG = Regex(
        """^(?:und\s+)?(?:was|wer|wie|wann|wo|warum|wieso|weshalb|welche[rsnm]?|wieviel|""" +
            """wie\s+viel)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Anknüpfungen an Linas letzte Antwort. */
    private val ANKNUEPFUNG = Regex(
        """\b(?:mehr dazu|erzähl mehr|erzaehl mehr|nochmal|noch mal|die erste|die zweite|""" +
            """die dritte|das erste|das zweite|das dritte|ausführlich|ausfuehrlich|""" +
            """genauer|und weiter|und sonst)\b""",
        RegexOption.IGNORE_CASE,
    )

    // ── Negative Indizien ────────────────────────────────────────────────

    /** Erzählen ÜBER jemanden – dritte Person, nicht Lina gemeint. */
    private val DRITTE_PERSON = Regex(
        """\b(?:er|sie|der|die)\s+(?:hat|hatte|sagt|sagte|meint|meinte|will|wollte|""" +
            """ist|war|kommt|kam|macht|machte)\b|""" +
            """\b(?:hat|sagt|sagte|meinte|meint)\s+(?:er|sie|der|die)\b|""" +
            """\bhat\s+gesagt\b|\bhaben\s+(?:die|sie)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Absprachen und Verabschiedungen unter Menschen. */
    private val ABSPRACHE = Regex(
        """\b(?:bis dann|bis später|bis spaeter|bis morgen|bis gleich|tschüss|tschuess|""" +
            """ciao|wir sehen uns|man sieht sich|ich melde mich|ich ruf(?:e)? dich|""" +
            """ruf mich (?:mal )?an|sag ich dir|mach ich|schick ich dir|meld dich|""" +
            """grüß|gruess|liebe grüße|alles klar dann)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * "ich <verb> dir/dich" – erste Person wendet sich an ein Gegenüber, das
     * nicht Lina ist ("ich schreib dir", "ich sag dir", "ich ruf dich").
     * Bewusst allgemein statt als Verbliste: die Einzelfallliste in
     * [ABSPRACHE] ließ "ich schreib dir dann später" durch, weil "schreib"
     * gleichzeitig als Imperativ zählte.
     */
    private val ICH_AN_DICH = Regex(
        """\bich\s+\w+\s+(?:dir|dich)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Füllwörter und Rückmeldepartikel – tragen keine Absicht an Lina. */
    private val FUELLWORT = Regex(
        """^(?:mhm|hm+|aha|ach so|ach|na ja|naja|genau|eben|okay|ok|alles klar|""" +
            """ja ja|ne|nee|hm ja)[\s.,!?]*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Bloße Zustimmung/Ablehnung ohne Inhalt – im Gesprächsfenster mehrdeutig. */
    private val BLOSSES_JA_NEIN = Regex("""^(?:ja|nein|doch|klar|nicht)[\s.,!?]*$""", RegexOption.IGNORE_CASE)

    // ── Gewichte ─────────────────────────────────────────────────────────

    private const val W_MODAL_FRAGE = 3
    private const val W_ANKNUEPFUNG = 3
    private const val W_FRAGEWORT = 2
    private const val W_IMPERATIV = 2
    private const val W_FRAGEZEICHEN = 1

    private const val W_DRITTE_PERSON = -4
    private const val W_ABSPRACHE = -4
    private const val W_ICH_AN_DICH = -4
    private const val W_FUELLWORT = -4
    private const val W_BLOSSES_JA_NEIN = -2
    private const val W_SEHR_LANG = -2

    /** Ab hier gilt eine Äußerung als an Lina gerichtet. */
    private const val SCHWELLE_ADRESSIERT = 2

    /** Ab so vielen Wörtern ohne positives Indiz wirkt es wie Erzählen. */
    private const val WORTGRENZE_LANG = 14

    /** Prüft, ob [text] an Lina gerichtet war. */
    fun evaluate(text: String?): Result {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) {
            return Result(Verdict.ROOM_CONVERSATION, 0, listOf("leer"))
        }
        if (ANREDE.containsMatchIn(t)) {
            return Result(Verdict.ADDRESSED, 99, listOf("direkte Anrede \"Lina\""))
        }

        val signals = mutableListOf<String>()
        var score = 0
        fun add(punkte: Int, name: String) {
            score += punkte
            signals += "$name($punkte)"
        }

        var positiv = false
        if (MODAL_FRAGE.containsMatchIn(t)) { add(W_MODAL_FRAGE, "Modalfrage"); positiv = true }
        if (ANKNUEPFUNG.containsMatchIn(t)) { add(W_ANKNUEPFUNG, "Anknüpfung"); positiv = true }
        if (FRAGEWORT_ANFANG.containsMatchIn(t)) { add(W_FRAGEWORT, "Fragewort"); positiv = true }
        if (IMPERATIVE.containsMatchIn(t)) { add(W_IMPERATIV, "Imperativ"); positiv = true }
        if (t.endsWith("?")) { add(W_FRAGEZEICHEN, "Fragezeichen"); positiv = true }

        if (DRITTE_PERSON.containsMatchIn(t)) add(W_DRITTE_PERSON, "dritte Person")
        if (ABSPRACHE.containsMatchIn(t)) add(W_ABSPRACHE, "Absprache")
        if (ICH_AN_DICH.containsMatchIn(t)) add(W_ICH_AN_DICH, "ich an dich")
        if (FUELLWORT.matches(t)) add(W_FUELLWORT, "Füllwort")
        if (BLOSSES_JA_NEIN.matches(t)) add(W_BLOSSES_JA_NEIN, "bloßes Ja/Nein")

        val woerter = t.split(Regex("""\s+""")).count { it.isNotBlank() }
        if (woerter >= WORTGRENZE_LANG && !positiv) add(W_SEHR_LANG, "lang ohne Bezug")

        val verdict = when {
            score >= SCHWELLE_ADRESSIERT -> Verdict.ADDRESSED
            score < 0 -> Verdict.ROOM_CONVERSATION
            else -> Verdict.UNCERTAIN
        }
        return Result(verdict, score, signals.ifEmpty { listOf("keine Indizien") })
    }
}
