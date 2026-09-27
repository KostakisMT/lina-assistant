package dev.lina.core.intent

/**
 * Gesprochene Anleitung auf "Was kannst du?" / "Womit kannst du mir helfen?".
 *
 * Ein blinder Nutzer kann kein Handbuch lesen und keine Menüs durchsehen –
 * was Lina kann, erfährt er nur, indem er fragt. Deshalb ein kurzes Gespräch
 * statt eines Monologs: erst ein Überblick in einem Atemzug, dann fragt Lina,
 * worüber er mehr wissen will, und erklärt genau dieses Thema mit Sätzen, die
 * er wörtlich so sagen kann.
 *
 * Regeln für die Texte (Leitprinzip "knapp und klar", TTS-Rate 0.9):
 * - Pro Thema höchstens drei bis vier Beispielsätze, keine Aufzählung aller
 *   Varianten – er soll sie sich merken können.
 * - Nur Beispiele, die tatsächlich funktionieren: [Thema.beispiele] wird im
 *   Test gegen den [LocalCommandResolver] geprüft. Themen, die nur mit
 *   Cloud-Gespräch gehen (Nachrichten, freie Fragen), fehlen ohne Cloud.
 * - Keine Sonderzeichen, die Piper falsch vorliest.
 *
 * Bewusst lokal und ohne Claude: Die Hilfe muss auch funktionieren, wenn das
 * Netz weg ist oder der Sprachdienst streikt – gerade dann fragt man sie.
 */
class HelpGuide(private val mitCloud: Boolean) {

    enum class Thema(
        val titel: String,
        /** Wörter, an denen die Antwort im Hilfe-Gespräch erkannt wird. */
        val schluessel: List<String>,
        val text: String,
        /** Sätze aus [text], die Lina versteht – vom Test gegen den Resolver geprüft. */
        val beispiele: List<String>,
        val brauchtCloud: Boolean = false,
    ) {
        ANRUFE(
            "Anrufe",
            listOf("anruf", "telefon", "anrufen", "klingel"),
            "Zum Telefonieren sag: Ruf, dann den Namen, und an. Zum Beispiel: Ruf Anna an. " +
                "Wenn es klingelt, sage ich dir, wer anruft. Dann sagst du: annehmen, oder: ablehnen. " +
                "Zum Beenden sagst du: auflegen.",
            listOf("ruf anna an", "annehmen", "ablehnen", "auflegen"),
        ),
        SMS(
            "SMS",
            listOf("sms", "schreib", "kurznachricht"),
            "Für eine SMS sag: Schreib, den Namen, und dann deinen Text. " +
                "Zum Beispiel: Schreib Anna, ich komme morgen. " +
                "Neue SMS lese ich dir vor, wenn du sagst: Lies meine Nachrichten.",
            listOf("schreib anna: ich komme morgen", "lies meine nachrichten"),
        ),
        NACHRICHTEN(
            "Nachrichten",
            listOf("nachrichten", "neuigkeiten", "zeitung", "neues", "news", "meldung"),
            "Frag einfach: Was gibt es Neues? Dann lese ich dir kurz die Meldungen aus deinen Zeitungen vor. " +
                "Nach jeder Meldung kannst du sagen: mehr, dann lese ich den ganzen Artikel. " +
                "Oder: nächste. Oder: stopp.",
            emptyList(), // läuft über das Gespräch mit dem Sprachdienst
            brauchtCloud = true,
        ),
        HOERBUECHER(
            "Hörbücher",
            listOf("hörbuch", "hoerbuch", "hörbücher", "hoerbuecher", "buch", "bücher", "buecher", "vorlesen lassen"),
            "Sag: Spiel Hörbuch ab. Mit: Pause, und: Weiter, hältst du an und machst weiter. " +
                "Du kannst auch sagen: nächstes Kapitel, oder: 30 Sekunden zurück. " +
                "Neue Bücher findest du mit: Suche Tolstoi, oder: Hörbücher zum Thema Geschichte.",
            listOf(
                "spiel hörbuch ab", "pause", "weiter", "nächstes kapitel",
                "30 sekunden zurück", "suche tolstoi", "hörbücher zum thema geschichte",
            ),
        ),
        POST(
            "Post vorlesen",
            listOf("post", "brief", "vorles", "dokument", "papier", "kamera", "foto"),
            "Leg den Brief in den Rahmen vor dem Tablet und sag: Lies mir die Post vor. " +
                "Ich sage dir zuerst kurz, worum es geht. Sag dann: alles, und ich lese ihn ganz vor. " +
                "Nach dem Umblättern sagst du: nochmal.",
            listOf("lies mir die post vor"),
        ),
        TERMINE(
            "Erinnerungen und Termine",
            listOf("erinner", "termin", "kalender", "datum", "uhr", "wecker", "zeit"),
            "Sag zum Beispiel: Erinnere mich in 20 Minuten an den Tee. " +
                "Oder: Trag einen Termin ein für nächsten Montag, Zahnarzt. Ich erinnere dich dann rechtzeitig. " +
                "Du kannst auch fragen: Was sind meine nächsten Termine? Oder: Wie spät ist es?",
            listOf("erinnere mich in 20 minuten an den tee", "was sind meine nächsten termine", "wie spät ist es"),
        ),
        BEDIENUNG(
            "Lautstärke und Nachtruhe",
            listOf("laut", "leise", "lautstärke", "nacht", "schlaf", "ruhe"),
            "Sag: lauter, oder: leiser. Wenn ich still sein soll, sag: stopp. " +
                "Abends sagst du: Gute Nacht, dann werde ich leise und dunkel. Morgens sagst du: Wach auf.",
            listOf("lauter", "leiser", "stopp", "gute nacht", "wach auf"),
        ),
        GESPRAECH(
            "Unterhalten",
            listOf("unterhalt", "gespräch", "gespraech", "fragen", "reden", "plaudern"),
            "Du kannst mich auch einfach etwas fragen, so wie einen Menschen. " +
                "Zum Beispiel, wie das Wetter wird, was ein Wort bedeutet, oder wer ein Buch geschrieben hat. " +
                "Nach meiner Antwort höre ich kurz weiter zu, dann musst du nicht wieder Hey Lina sagen.",
            emptyList(),
            brauchtCloud = true,
        ),
    }

    /** Was Lina nach einer Äußerung im Hilfe-Gespräch sagt und ob sie weiter zuhört. */
    data class Antwort(val text: String, val weiterZuhoeren: Boolean)

    val themen: List<Thema> = Thema.entries.filter { mitCloud || !it.brauchtCloud }

    private var fehlversuche = 0

    /** Einstieg: ein bestimmtes Thema direkt, sonst der Überblick. */
    fun start(thema: Thema? = null): Antwort {
        fehlversuche = 0
        if (thema != null && thema in themen) return erklaere(thema)
        return Antwort(
            "Ich kann dir bei vielem helfen: " + aufzaehlung(themen.map { ueberblicksName(it) }) + ". " +
                "Worüber möchtest du mehr wissen?",
            weiterZuhoeren = true,
        )
    }

    /** Reaktion auf das, was der Nutzer im Hilfe-Gespräch sagt. */
    fun antwort(gesagt: String): Antwort {
        val t = gesagt.trim().lowercase()
        themaIn(t)?.let { return erklaere(it) }
        if (ENDE.containsMatchIn(t)) return Antwort(ABSCHIED, weiterZuhoeren = false)
        if (JA.containsMatchIn(t)) {
            return Antwort("Worüber? " + aufzaehlung(themen.map { it.titel }, "oder") + "?", weiterZuhoeren = true)
        }
        if (ALLES.containsMatchIn(t)) {
            // Alle Themen am Stück wäre ein Monolog von Minuten – lieber führen
            return erklaere(themen.first())
        }
        fehlversuche++
        if (fehlversuche >= 2 || t.isBlank()) return Antwort(ABSCHIED, weiterZuhoeren = false)
        return Antwort(
            "Das habe ich nicht verstanden. Sag zum Beispiel: " +
                aufzaehlung(themen.take(3).map { it.titel }, "oder") + ".",
            weiterZuhoeren = true,
        )
    }

    /** Thema, das in [text] genannt wird – auch für "Was kannst du mit Hörbüchern?". */
    fun themaIn(text: String): Thema? {
        val t = text.lowercase()
        return themen.firstOrNull { thema -> thema.schluessel.any { t.contains(it) } }
    }

    private fun erklaere(thema: Thema): Antwort {
        fehlversuche = 0
        val naechstes = themen.getOrNull(themen.indexOf(thema) + 1)
        val frage = if (naechstes != null) {
            "Möchtest du noch etwas anderes wissen? Zum Beispiel über ${naechstes.titel}?"
        } else {
            "Möchtest du noch etwas anderes wissen?"
        }
        return Antwort(thema.text + " " + frage, weiterZuhoeren = true)
    }

    private fun ueberblicksName(thema: Thema): String = when (thema) {
        Thema.ANRUFE -> "telefonieren"
        Thema.SMS -> "SMS schreiben und vorlesen"
        Thema.NACHRICHTEN -> "Nachrichten aus deinen Zeitungen"
        Thema.HOERBUECHER -> "Hörbücher"
        Thema.POST -> "deine Post vorlesen"
        Thema.TERMINE -> "Erinnerungen und Termine"
        Thema.BEDIENUNG -> "Lautstärke und Nachtruhe"
        Thema.GESPRAECH -> "und du kannst dich einfach mit mir unterhalten"
    }

    private fun aufzaehlung(teile: List<String>, und: String = "und"): String = when {
        teile.size <= 1 -> teile.joinToString()
        teile.last().startsWith("und ") -> teile.joinToString(", ")
        else -> teile.dropLast(1).joinToString(", ") + " $und " + teile.last()
    }

    companion object {
        private const val ABSCHIED =
            "Gut. Wenn du etwas vergisst, frag mich einfach: Was kannst du?"

        // Wortgrenzen am Anfang: "nein" darf nicht in "keinen" o.ä. greifen
        private val ENDE = Regex(
            """\b(?:nein|nee|ne|nichts|danke|reicht|genug|fertig|passt|später|spaeter|stopp|stop|tschüss|tschuess)\b"""
        )
        private val JA = Regex("""\b(?:ja|gerne|gern|klar|okay|ok|doch)\b""")
        private val ALLES = Regex("""\b(?:alles|alle|der reihe nach)\b""")

        /** Fragen nach Linas Fähigkeiten – eng gefasst, damit "kannst du Boris anrufen" ein Anruf bleibt. */
        private val FRAGE_UEBERBLICK = listOf(
            Regex("""\bwas\s+kannst\s+du\b(?:\s+(?:denn|eigentlich|alles|so|mir|überhaupt|ueberhaupt|noch|sonst|""" +
                """für mich|fuer mich|machen|tun))*[\s.,!?]*$"""),
            Regex("""\b(?:womit|wobei|wie)\s+kannst\s+du\s+mir\s+helfen\b"""),
            Regex("""\bwas\s+kann\s+ich\s+(?:dich\s+)?(?:alles\s+)?(?:fragen|sagen)\b"""),
            Regex("""\bwas\s+kann\s+ich\s+(?:alles\s+)?mit\s+dir\s+machen\b"""),
            Regex("""\bwas\s+(?:sind|hast\s+du\s+für)\s+(?:deine\s+)?funktionen\b"""),
            Regex("""\bwelche\s+befehle\b"""),
            Regex("""\b(?:anleitung|tutorial|bedienungsanleitung)\b"""),
            Regex("""\bwie\s+funktionierst\s+du\b"""),
        )

        /** "Was kannst du mit Hörbüchern?" / "Wie funktioniert das mit den Terminen?" */
        private val FRAGE_THEMA = listOf(
            Regex("""\bwas\s+kannst\s+du\s+(?:alles\s+)?(?:mit|bei|zu|für|fuer)\b"""),
            Regex("""\bwie\s+(?:funktioniert|geht)\s+das\s+mit\b"""),
        )

        /**
         * Hilfe-Frage in [input] (kleingeschrieben)? Liefert das gefragte
         * Thema (oder null = Überblick) – oder gar nichts, wenn es keine
         * Hilfe-Frage ist. Themenerkennung ohne Cloud-Einschränkung: welche
         * Themen es gibt, entscheidet erst [start].
         */
        fun erkenne(input: String): ResolvedIntent.Help? {
            val t = input.trim().lowercase()
            if (FRAGE_THEMA.any { it.containsMatchIn(t) }) {
                // "Was kannst du für mich tun?" nennt kein Thema → Überblick unten
                HelpGuide(mitCloud = true).themaIn(t)?.let { return ResolvedIntent.Help(it) }
            }
            if (FRAGE_UEBERBLICK.any { it.containsMatchIn(t) }) return ResolvedIntent.Help(null)
            return null
        }
    }
}
