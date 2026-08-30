package dev.lina.core.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Filter darf lieber ein Artefakt durchlassen als eine echte Äußerung
 * verschlucken – ein verschluckter Befehl ist für einen blinden Nutzer
 * schlimmer als eine überflüssige Rückfrage. Deshalb hier vor allem die
 * Gegenprobe: alltägliche Befehle müssen passieren.
 */
class TranscriptPlausibilityTest {

    /** Genau der Fall vom Testtablet, 2026-08-30. */
    @Test
    fun `gemessenes Whisper-Artefakt wird verworfen`() {
        assertFalse(TranscriptPlausibility.isPlausible("* Erinnungsvolle Musik *"))
    }

    @Test
    fun `Untertitel-Notation in allen Klammerformen wird verworfen`() {
        listOf("[Musik]", "(Applaus)", "* Musik *", "♪♪♪", "<Geräusch>", "[ Musik ]")
            .forEach { assertFalse(it, TranscriptPlausibility.isPlausible(it)) }
    }

    @Test
    fun `Abspann-Floskeln werden verworfen`() {
        listOf(
            "Untertitel der Amara.org-Community",
            "Untertitelung des ZDF, 2020",
            "Vielen Dank fürs Zuschauen!",
            "Abonniert diesen Kanal",
        ).forEach { assertFalse(it, TranscriptPlausibility.isPlausible(it)) }
    }

    @Test
    fun `leer und reine Satzzeichen sind nicht plausibel`() {
        listOf("", "   ", null, "...", "?!", "123 456")
            .forEach { assertFalse(String(charArrayOf()) + it, TranscriptPlausibility.isPlausible(it)) }
    }

    /** Die wichtigere Richtung: echte Befehle dürfen nicht hängenbleiben. */
    @Test
    fun `echte Befehle passieren den Filter`() {
        listOf(
            "ruf Boris an",
            "was gibt es Neues",
            "Ja",
            "Nein",
            "Stopp",
            "spiel Hörbuch ab",
            "trage einen Termin ein für nächsten Montag: Zahnarzt",
            "schreib Ulla: bin gleich da",
            "lies mir die Post vor",
            "Kontakte von der SIM importieren",
            "erzähl mir etwas über das Segeln",
            "Hörbücher zum Thema Politik",
        ).forEach { assertTrue(it, TranscriptPlausibility.isPlausible(it)) }
    }

    /** Ein Sternchen mitten im Satz ist kein Artefakt. */
    @Test
    fun `Sonderzeichen im Satz machen ihn nicht unplausibel`() {
        assertTrue(TranscriptPlausibility.isPlausible("spiel Kapitel 3 * ab"))
    }

    @Test
    fun `filter liefert getrimmten Text oder leer`() {
        assertEquals("ruf Boris an", TranscriptPlausibility.filter("  ruf Boris an  "))
        assertEquals("", TranscriptPlausibility.filter("[Musik]"))
        assertEquals("", TranscriptPlausibility.filter(null))
    }
}
