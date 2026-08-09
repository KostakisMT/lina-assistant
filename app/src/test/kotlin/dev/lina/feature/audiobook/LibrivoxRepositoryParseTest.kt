package dev.lina.feature.audiobook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressionstest für einen echten, live gegen die LibriVox-API gefundenen
 * Bug: der frühere `fields={id,title,...}`-Parameter lieferte nicht ein
 * zusammengeführtes JSON-Objekt pro Buch, sondern mehrere aneinandergehängte
 * `{"books":[...]}`-Blöcke (einen je Feld) – `JSONObject(json)` parst nur den
 * ersten. Titel/Autor/Dauer/Sprache waren dadurch bei jeder LibriVox-Suche
 * leer bzw. "Unbekannt". Der Fix lässt `fields=` weg; die Standardantwort
 * enthält alles in einem sauberen Objekt.
 */
class LibrivoxRepositoryParseTest {

    private val repository = LibrivoxRepository()

    @Test
    fun `altes fields-Format lieferte nur die ID (Bug, jetzt nicht mehr genutzt)`() {
        // Exakt die Form, die die echte API für ?fields={id,title,...} zurückgab.
        val brokenJson = """{"books":[{"id":"855"}]}{"books":[{"title":"Faust I"}]}"""

        val books = repository.parseBooks(brokenJson)

        assertEquals(1, books.size)
        assertEquals("855", books.first().id)
        // Der Titel aus dem zweiten Block ging verloren – das war der Bug.
        assertEquals("", books.first().title)
    }

    @Test
    fun `neues Standardformat liefert alle Felder korrekt`() {
        val cleanJson = """
            {"books":[{
                "id":"855",
                "title":"Faust I",
                "description":"Klassiker",
                "language":"German",
                "totaltimesecs":15538,
                "url_rss":"https://librivox.org/rss/855",
                "authors":[{"first_name":"Johann Wolfgang von","last_name":"Goethe"}]
            }]}
        """.trimIndent()

        val books = repository.parseBooks(cleanJson)

        assertEquals(1, books.size)
        val book = books.first()
        assertEquals("855", book.id)
        assertEquals("Faust I", book.title)
        assertEquals("Johann Wolfgang von Goethe", book.author)
        assertEquals("German", book.language)
        assertEquals(15538, book.totalDurationSecs)
        assertEquals("https://librivox.org/rss/855", book.rssUrl)
    }

    @Test
    fun `fehlende Autoren ergeben Unbekannt statt Absturz`() {
        val json = """{"books":[{"id":"1","title":"Ohne Autor","language":"German"}]}"""
        val books = repository.parseBooks(json)
        assertEquals("Unbekannt", books.first().author)
    }

    @Test
    fun `kaputtes JSON liefert leere Liste statt Absturz`() {
        val books = repository.parseBooks("nicht valides json{{{")
        assertTrue(books.isEmpty())
    }
}
