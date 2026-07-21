package dev.lina.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Buchstabiertafel trägt die Gerätekopplung (ADR-020): Hört die
 * Vertrauensperson den Code falsch, schlägt die Einrichtung fehl – und der
 * blinde Nutzer kann das Problem nicht selbst sehen und nicht selbst lösen.
 * Deshalb hier vor allem die Fälle, in denen Zeichen verloren gehen oder
 * still verschluckt werden könnten.
 */
class GermanSpellingTest {

    @Test
    fun `Code wird zu Ansagewörtern`() {
        assertEquals("Berta, Sieben, Anton, Drei", GermanSpelling.spell("B7A3"))
    }

    @Test
    fun `Kleinschreibung ergibt dieselbe Ansage`() {
        assertEquals(GermanSpelling.spell("B7A3"), GermanSpelling.spell("b7a3"))
    }

    @Test
    fun `Trennzeichen im Code stören nicht`() {
        // Ein Code darf lesbar formatiert übergeben werden.
        assertEquals("Berta, Sieben, Anton, Drei", GermanSpelling.spell("B7-A3"))
        assertEquals("Berta, Sieben, Anton, Drei", GermanSpelling.spell("B7 A3"))
    }

    @Test
    fun `explizite Form nennt Buchstabe und Wort`() {
        assertEquals("B wie Berta, Sieben", GermanSpelling.spellExplicit("B7"))
    }

    @Test
    fun `Umlaute haben eigene Ansagewörter`() {
        assertEquals("Ärger", GermanSpelling.spell("ä"))
        assertEquals("Ökonom", GermanSpelling.spell("ö"))
        assertEquals("Übermut", GermanSpelling.spell("ü"))
    }

    @Test
    fun `alle Ziffern sind abgedeckt`() {
        // Eine fehlende Ziffer würde im Code stillschweigend verschwinden.
        assertEquals(10, GermanSpelling.DIGITS.size)
        for (d in '0'..'9') {
            assertTrue("Ziffer $d fehlt", GermanSpelling.DIGITS.containsKey(d))
        }
    }

    @Test
    fun `alle Grundbuchstaben sind abgedeckt`() {
        for (c in 'a'..'z') {
            assertTrue("Buchstabe $c fehlt", GermanSpelling.LETTERS.containsKey(c))
        }
    }

    @Test
    fun `jedes Zeichen des Code-Alphabets ist aussprechbar`() {
        // Der eigentliche Vertrag: Was zur Code-Erzeugung freigegeben ist,
        // muss Lina auch vorlesen können – sonst entsteht ein stummer Code.
        for (c in GermanSpelling.CODE_ALPHABET) {
            assertTrue("'$c' aus CODE_ALPHABET ist nicht aussprechbar", GermanSpelling.spell(c.toString()).isNotEmpty())
        }
    }

    @Test
    fun `Code-Alphabet meidet verwechselbare Zeichen`() {
        for (c in "01IOLQY") {
            assertTrue("'$c' gehört nicht ins Code-Alphabet", !GermanSpelling.CODE_ALPHABET.contains(c))
        }
    }

    @Test
    fun `Code-Alphabet enthält keine Dopplungen`() {
        assertEquals(GermanSpelling.CODE_ALPHABET.length, GermanSpelling.CODE_ALPHABET.toSet().size)
    }

    @Test
    fun `unbekannte Zeichen werden übersprungen statt zu stören`() {
        assertEquals("Anton, Berta", GermanSpelling.spell("A#B"))
        assertEquals("", GermanSpelling.spell("#+*"))
        assertEquals("", GermanSpelling.spell(""))
    }
}
