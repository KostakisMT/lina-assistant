package dev.lina.feature.audiobook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrivoxGenresTest {

    @Test
    fun `Synonym-Treffer fuer bekannte Interessen`() {
        assertEquals("Political Science", LibrivoxGenres.findGenre("Marxismus"))
        assertEquals("Political Science", LibrivoxGenres.findGenre("politische Theorie"))
        assertEquals("Nautical & Marine Fiction", LibrivoxGenres.findGenre("Segeln"))
        assertEquals("Science", LibrivoxGenres.findGenre("Wissenschaft"))
        assertEquals("History", LibrivoxGenres.findGenre("Geschichte"))
    }

    @Test
    fun `Synonym-Lookup ist gross-klein-unabhaengig und trimmt`() {
        assertEquals("Political Science", LibrivoxGenres.findGenre("  MARXISMUS  "))
    }

    @Test
    fun `Teilstring-Fallback erkennt direkte Taxonomie-Namen`() {
        assertEquals("Philosophy", LibrivoxGenres.findGenre("philosophy"))
        assertEquals("Poetry", LibrivoxGenres.findGenre("poetry"))
    }

    @Test
    fun `unbekanntes Thema liefert null`() {
        assertNull(LibrivoxGenres.findGenre("Quantencomputer-Reparatur"))
        assertNull(LibrivoxGenres.findGenre(""))
    }

    @Test
    fun `alle Synonym-Werte stecken in der Taxonomie (kein Tippfehler)`() {
        val synonymValues = listOf(
            "Marxismus", "politische Theorie", "Segeln", "Wissenschaft", "Wirtschaft",
            "Geschichte", "Philosophie", "Abenteuer", "Krimi", "Gedichte", "Kinderbücher",
        )
        for (topic in synonymValues) {
            val genre = LibrivoxGenres.findGenre(topic)
            assertTrue("Kein Genre fuer '$topic' gefunden", genre != null)
            assertTrue(
                "Genre '$genre' (für '$topic') fehlt in der Taxonomie",
                LibrivoxGenres.TAXONOMY.contains(genre),
            )
        }
    }
}
