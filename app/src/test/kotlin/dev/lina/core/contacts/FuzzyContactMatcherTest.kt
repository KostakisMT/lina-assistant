package dev.lina.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das Kontakt-Matching entscheidet, **wen Lina anruft**. Ein Fehler hier ist
 * teurer als irgendwo sonst im Projekt: Der Nutzer sieht nicht, wen er am
 * Apparat hat, und merkt den Irrtum erst im Gespräch.
 *
 * Getestet wird deshalb vor allem die Reihenfolge der fünf Stufen
 * (exakt → Namensteil → enthält → Phonetik → Levenshtein) und die Frage,
 * wann Lina **nachfragen muss** statt zu raten. Die Namen stammen aus dem
 * Nutzerprofil in CLAUDE.md, wo "Arundhati" und "Eßfeld" ausdrücklich als
 * spracherkennungs-kritisch markiert sind.
 */
class FuzzyContactMatcherTest {

    private fun matcher(vararg names: String): FuzzyContactMatcher {
        val contacts = names.mapIndexed { i, name -> Contact(i.toLong(), name, "0170$i") }
        return FuzzyContactMatcher(object : ContactSource {
            override fun loadAll() = contacts
        })
    }

    /** Das Telefonbuch des Testnutzers laut CLAUDE.md. */
    private fun standardTelefonbuch() = matcher(
        "Arundhati Brandt",
        "Boris Hartmann",
        "Ulla Winter",
        "Annika Berger",
        "Sabine Dreyer",
        "Dirk Eßfeld",
        "Hannah Schäfer",
        "Gudrun Sommer",
    )

    private fun single(result: ContactMatchResult): String {
        assertTrue("Erwartet: eindeutiger Treffer, war: $result", result is ContactMatchResult.SingleMatch)
        return (result as ContactMatchResult.SingleMatch).contact.displayName
    }

    // ---------- Stufe 1 & 2: exakte Treffer

    @Test
    fun `vollständiger Name trifft eindeutig`() {
        assertEquals("Boris Hartmann", single(standardTelefonbuch().findMatches("Boris Hartmann")))
    }

    @Test
    fun `Vorname allein trifft eindeutig`() {
        assertEquals("Boris Hartmann", single(standardTelefonbuch().findMatches("Boris")))
    }

    @Test
    fun `Nachname allein trifft eindeutig`() {
        assertEquals("Ulla Winter", single(standardTelefonbuch().findMatches("Winter")))
    }

    @Test
    fun `Groß- und Kleinschreibung egal`() {
        assertEquals("Boris Hartmann", single(standardTelefonbuch().findMatches("boris hartmann")))
    }

    @Test
    fun `umgebende Leerzeichen werden ignoriert`() {
        assertEquals("Boris Hartmann", single(standardTelefonbuch().findMatches("  Boris  ")))
    }

    // ---------- Mehrdeutigkeit: Lina muss nachfragen statt zu raten

    @Test
    fun `zwei gleiche Vornamen führen zur Rückfrage`() {
        // Der in CLAUDE.md beschriebene Fall: "Welchen Boris meinst du?"
        val result = matcher("Boris Hartmann", "Boris Neumann").findMatches("Boris")
        assertTrue("Erwartet: Rückfrage, war: $result", result is ContactMatchResult.MultipleMatches)
        assertEquals(2, (result as ContactMatchResult.MultipleMatches).contacts.size)
    }

    @Test
    fun `bei Mehrdeutigkeit wird die Anfrage für die Rückfrage mitgegeben`() {
        // CallHandler baut daraus "Welchen boris meinst du?" – ohne query ginge der Satz nicht.
        val result = matcher("Boris Hartmann", "Boris Neumann").findMatches("Boris")
        assertEquals("boris", (result as ContactMatchResult.MultipleMatches).query)
    }

    @Test
    fun `Nachname grenzt zwei gleiche Vornamen wieder ab`() {
        val m = matcher("Boris Hartmann", "Boris Neumann")
        assertEquals("Boris Neumann", single(m.findMatches("Neumann")))
    }

    // ---------- Stufe 3: Teilstring

    @Test
    fun `Namensanfang genügt`() {
        assertEquals("Gudrun Sommer", single(standardTelefonbuch().findMatches("Gud")))
    }

    // ---------- Spracherkennungs-kritische Namen (CLAUDE.md)

    @Test
    fun `Arundhati wird korrekt geschrieben erkannt`() {
        assertEquals("Arundhati Brandt", single(standardTelefonbuch().findMatches("Arundhati")))
    }

    @Test
    fun `Arundhati wird trotz STT-Verhörer erkannt`() {
        // phoneticSimilarity nennt "aroma"→"Arundhati" ausdrücklich als Zielfall:
        // Whisper verhört den Namen, Lina soll trotzdem die Richtige anrufen.
        assertEquals("Arundhati Brandt", single(standardTelefonbuch().findMatches("Aromahati")))
    }

    @Test
    fun `Verhörer wählt die richtige von mehreren ähnlichen Personen`() {
        // Der gefährliche Fall: nicht "irgendwer", sondern der Nächstliegende.
        val m = matcher("Sabine Dreyer", "Sabrina Bauer", "Martin Vogel")
        assertEquals("Sabine Dreyer", single(m.findMatches("Sabina")))
    }

    @Test
    fun `Eßfeld auch ohne ß geschrieben`() {
        // Whisper transkribiert "ß" unzuverlässig – "Essfeld" muss treffen.
        assertEquals("Dirk Eßfeld", single(standardTelefonbuch().findMatches("Essfeld")))
    }

    @Test
    fun `Schäfer auch als Schaefer`() {
        assertEquals("Hannah Schäfer", single(standardTelefonbuch().findMatches("Schaefer")))
    }

    // ---------- Stufe 5: Tippfehler-Toleranz

    @Test
    fun `ein vertauschter Buchstabe trifft noch`() {
        assertEquals("Sabine Dreyer", single(standardTelefonbuch().findMatches("Sabnie")))
    }

    // ---------- Kein Treffer

    @Test
    fun `unbekannter Name ergibt NoMatch`() {
        val result = standardTelefonbuch().findMatches("Xylophon")
        assertTrue("Erwartet: NoMatch, war: $result", result is ContactMatchResult.NoMatch)
    }

    @Test
    fun `leeres Telefonbuch ergibt NoMatch`() {
        val result = matcher().findMatches("Boris")
        assertTrue("Erwartet: NoMatch, war: $result", result is ContactMatchResult.NoMatch)
    }

    @Test
    fun `findBestMatch liefert null statt zu werfen`() {
        assertEquals(null, standardTelefonbuch().findBestMatch("Xylophon"))
    }

    // ---------- Bausteine

    @Test
    fun `levenshtein zählt Einzelschritte`() {
        assertEquals(0, FuzzyContactMatcher.levenshtein("boris", "boris"))
        assertEquals(3, FuzzyContactMatcher.levenshtein("kitten", "sitting"))
        assertEquals(5, FuzzyContactMatcher.levenshtein("", "boris"))
    }

    @Test
    fun `Kölner Phonetik macht Umlaut- und ß-Schreibweisen gleich`() {
        // Trägt die beiden Tests oben – wenn das hier bricht, brechen die auch.
        assertEquals(
            FuzzyContactMatcher.koelnerPhonetik("Eßfeld"),
            FuzzyContactMatcher.koelnerPhonetik("Essfeld"),
        )
        assertEquals(
            FuzzyContactMatcher.koelnerPhonetik("Schäfer"),
            FuzzyContactMatcher.koelnerPhonetik("Schaefer"),
        )
    }

    @Test
    fun `Kölner Phonetik ignoriert Groß- und Kleinschreibung`() {
        assertEquals(
            FuzzyContactMatcher.koelnerPhonetik("Hartmann"),
            FuzzyContactMatcher.koelnerPhonetik("hartmann"),
        )
    }

    @Test
    fun `Kölner Phonetik liefert für Leerstring nichts`() {
        assertEquals("", FuzzyContactMatcher.koelnerPhonetik(""))
        assertEquals("", FuzzyContactMatcher.koelnerPhonetik("123"))
    }
}
