package dev.lina.core.intent

import dev.lina.core.intent.RoomSpeechFilter.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Filter entscheidet, ob eine Äußerung das Gerät verlassen darf. Die
 * beiden Fehlerrichtungen wiegen ungleich: eine fälschlich blockierte Frage
 * kostet eine Wiederholung mit Weckwort, eine fälschlich durchgelassene
 * Passage aus einem fremden Gespräch ist nicht zurückholbar.
 *
 * Deshalb prüfen die Tests unten beides getrennt – und die Raumgespräch-Fälle
 * sind bewusst die ausführlicheren.
 */
class RoomSpeechFilterTest {

    private fun urteil(text: String) = RoomSpeechFilter.evaluate(text).verdict

    private fun darfRaus(text: String) =
        RoomSpeechFilter.evaluate(text).verdict.mayReachCloud()

    // ── Was an Lina gerichtet ist, muss durchkommen ──────────────────────

    @Test
    fun `Fragen an Lina kommen durch`() {
        listOf(
            "Was kannst du alles?",
            "Kannst du mir etwas über Tolstoi erzählen?",
            "Wie wird das Wetter morgen?",
            "Weißt du, wann die Kieler Woche ist?",
            "Erzähl mir mehr dazu",
            "Und was war die zweite Meldung?",
            "Wer hat das Buch geschrieben?",
            "Erklär mir das nochmal genauer",
        ).forEach { assertTrue(it, darfRaus(it)) }
    }

    @Test
    fun `direkte Anrede genuegt immer`() {
        assertEquals(Verdict.ADDRESSED, urteil("Lina, mach mal lauter"))
        // Selbst wenn sonst alles nach Raumgespräch klingt
        assertEquals(Verdict.ADDRESSED, urteil("Lina, er hat gesagt bis dann"))
    }

    /**
     * Der lokale Resolver darf NICHT als Freifahrtschein dienen: "ich ruf dich
     * später an" trifft resolveCall und ist trotzdem eine Absprache unter
     * Menschen. Genau dieser Satz muss blockiert bleiben.
     */
    @Test
    fun `Regex-Treffer ist kein Freifahrtschein`() {
        assertFalse(darfRaus("ich ruf dich später an"))
        assertFalse(darfRaus("ich schreib dir dann später"))
    }

    /** Echte Gerätebefehle an Lina kommen über ihre eigenen Indizien durch. */
    @Test
    fun `echte Befehle kommen durch`() {
        listOf(
            "spiel das Hörbuch ab",
            "mach bitte lauter",
            "lies mir die Nachrichten vor",
        ).forEach { assertTrue(it, darfRaus(it)) }
    }

    // ── Was im Raum gesprochen wird, darf NICHT raus ─────────────────────

    @Test
    fun `Gespraech ueber Dritte bleibt auf dem Geraet`() {
        listOf(
            "ja er hat gesagt er kommt später",
            "sie meinte das wäre kein Problem",
            "und dann hat er einfach aufgelegt",
            "die haben gestern schon angerufen",
        ).forEach { assertFalse(it, darfRaus(it)) }
    }

    @Test
    fun `Absprachen und Verabschiedungen bleiben auf dem Geraet`() {
        listOf(
            "alles klar, bis dann",
            "ich ruf dich später an",
            "ja mach ich morgen, sag ich dir dann",
            "wir sehen uns nächste Woche",
            "tschüss, bis später",
        ).forEach { assertFalse(it, darfRaus(it)) }
    }

    @Test
    fun `Fuellwoerter bleiben auf dem Geraet`() {
        listOf("mhm", "aha", "ach so", "genau", "na ja", "okay", "hm ja")
            .forEach { assertFalse(it, darfRaus(it)) }
    }

    /** Im Gesprächsfenster ist ein blankes "ja" nicht zuzuordnen. */
    @Test
    fun `blosses Ja oder Nein reicht nicht`() {
        listOf("ja", "nein", "doch", "klar").forEach { assertFalse(it, darfRaus(it)) }
    }

    /** Genau der am Gerät beobachtete Fall: mitgehörtes Videotelefonat. */
    @Test
    fun `mitgehoertes Telefonat im Raum bleibt auf dem Geraet`() {
        val mitgehoert =
            "Das ist ja auch so ein bisschen meine Sanctuary, weißt du, wenn du " +
                "einen Freund hast der da auch mit reinkommt und dann sagt er"
        assertFalse(darfRaus(mitgehoert))
    }

    @Test
    fun `langes Erzaehlen ohne Bezug zu Lina bleibt auf dem Geraet`() {
        val erzaehlung =
            "also gestern waren wir dann noch kurz einkaufen und danach sind wir " +
                "direkt nach Hause gefahren weil es schon spät war"
        assertFalse(darfRaus(erzaehlung))
    }

    // ── Grenzfälle ───────────────────────────────────────────────────────

    @Test
    fun `Unentscheidbares wird wie Raumgespraech behandelt`() {
        val r = RoomSpeechFilter.evaluate("der Tisch")
        assertEquals(Verdict.UNCERTAIN, r.verdict)
        assertFalse("im Zweifel NICHT senden", r.verdict.mayReachCloud())
    }

    @Test
    fun `leere Eingabe faellt nicht um`() {
        assertFalse(darfRaus(""))
        assertFalse(darfRaus("   "))
        assertFalse(RoomSpeechFilter.evaluate(null).verdict.mayReachCloud())
    }

    /** Negative Indizien schlagen einzelne positive – Sicherheitsrichtung. */
    @Test
    fun `negative Indizien ueberstimmen ein schwaches positives`() {
        // "sag ich dir" ist eine Absprache, trotz Imperativ-Anmutung von "sag"
        assertFalse(darfRaus("mach ich, sag ich dir dann"))
    }

    /** Die Begründung muss im Log nachvollziehbar sein. */
    @Test
    fun `Ergebnis nennt die ausschlaggebenden Signale`() {
        val r = RoomSpeechFilter.evaluate("ja er hat gesagt bis dann")
        assertTrue(r.signals.any { it.contains("dritte Person") })
        assertTrue(r.signals.any { it.contains("Absprache") })
        assertTrue(r.score < 0)
    }
}
