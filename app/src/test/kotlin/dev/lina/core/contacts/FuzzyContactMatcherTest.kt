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
        // Realistische Mobilnummern: alles unter 7 Ziffern gilt als Kurzwahl
        // (PhoneNumberRisk) und wird vom Raten ausgeschlossen – ein zu kurzer
        // Platzhalter würde hier stillschweigend jeden Kontakt herausfiltern.
        val contacts = names.mapIndexed { i, name -> Contact(i.toLong(), name, "017012345$i") }
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
    // --- Diensteinträge des Anbieters (2026-09-20) -------------------------
    //
    // Fixture aus der echten Vodafone-SIM des Testnutzers (Namen und Nummern
    // 1:1 aus content://icc/adn ausgelesen). Genau diese Einträge standen am
    // 2026-09-20 im Telefonbuch, als der SIM-Spiegel auftauchte.

    private fun telefonbuchMitDiensteintraegen() = FuzzyContactMatcher(
        object : ContactSource {
            override fun loadAll() = listOf(
                Contact(1, "Arundhati Brandt", "0170123451"),
                Contact(2, "Boris Hartmann", "0170123452"),
                Contact(3, "Kartenlegen 199ct/Min Tarot", "22377"),
                Contact(4, "Horoskop 199ct/Min", "22335"),
                Contact(5, "Auskunft 11880 199ct/Min", "118802899"),
                Contact(6, "ADAC Pannenhilfe 30ct/Min", "222222"),
                Contact(7, "Mailbox", "5500"),
            )
        },
    )

    @Test
    fun `verhoerter Name landet nicht auf einer Premium-Nummer`() {
        // "Tolstoi" → "Teustol" ist der am Gerät belegte Whisper-Verhörer.
        // Vorher konnte das über Phonetik/Levenshtein auf "Tarot" fallen.
        val result = telefonbuchMitDiensteintraegen().findMatches("teustol")
        val getroffen = when (result) {
            is ContactMatchResult.SingleMatch -> listOf(result.contact)
            is ContactMatchResult.MultipleMatches -> result.contacts
            is ContactMatchResult.NoMatch -> emptyList()
        }
        assertTrue(
            "Diensteintrag geraten: ${getroffen.map { it.displayName }}",
            getroffen.none { PhoneNumberRisk.isServiceEntry(it.displayName, it.phoneNumber) },
        )
    }

    @Test
    fun `Kurzwahl wird nicht erraten`() {
        val result = telefonbuchMitDiensteintraegen().findBestMatch("mailbix")
        assertTrue(
            "Kurzwahl 5500 wurde erraten: ${result?.displayName}",
            result == null || result.phoneNumber != "5500",
        )
    }

    @Test
    fun `bewusst genannter Dienst bleibt erreichbar`() {
        // Wer "Horoskop" klar ausspricht, soll ihn bekommen – gefiltert wird
        // nur das Raten, nicht der exakte Namensvergleich.
        val result = telefonbuchMitDiensteintraegen().findMatches("horoskop")
        assertTrue(result is ContactMatchResult.SingleMatch)
        assertEquals("22335", (result as ContactMatchResult.SingleMatch).contact.phoneNumber)
    }

    @Test
    fun `echte Kontakte bleiben trotz Filter erratbar`() {
        val result = telefonbuchMitDiensteintraegen().findBestMatch("arundati")
        assertEquals("Arundhati Brandt", result?.displayName)
    }

    @Test
    fun `Diensteintrag auf normaler Nummer wird am Namen erkannt`() {
        // 0800/118xx-Nummern sind keine Kurzwahl – hier trägt der Tarif-Marker.
        assertTrue(PhoneNumberRisk.isServiceEntry("ADAC Pannenhilfe 30ct/Min", "222222"))
        assertTrue(PhoneNumberRisk.isServiceEntry("Auskunft 11880 199ct/Min", "118802899"))
        assertTrue(!PhoneNumberRisk.isServiceEntry("Boris Hartmann", "0170123452"))
    }

}
