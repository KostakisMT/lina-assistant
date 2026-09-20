package dev.lina.feature.audiobook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Der LibriVox-RSS-Feed kommt über das Netz von einer fremden Quelle. Neben
 * dem normalen Kapitel-Parsing wird hier festgehalten, dass ein präparierter
 * Feed keine lokalen Dateien auslesen kann (XXE) – siehe SecureXmlTest für
 * die Härtung selbst.
 */
class LibrivoxRssChapterTest {

    private val repository = LibrivoxRepository()

    @Test
    fun `Kapitel aus einem normalen Feed`() {
        val rss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <item>
                  <title>01 - Vorwort</title>
                  <enclosure url="https://example.org/01.mp3" type="audio/mpeg"/>
                  <itunes:duration>00:12:30</itunes:duration>
                </item>
                <item>
                  <title>02 - Erstes Kapitel</title>
                  <enclosure url="https://example.org/02.mp3" type="audio/mpeg"/>
                  <itunes:duration>01:00:05</itunes:duration>
                </item>
                <item>
                  <title>Ohne Audio</title>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val chapters = repository.parseRssChapters(rss.byteInputStream())

        assertEquals(2, chapters.size)
        assertEquals("01 - Vorwort", chapters[0].title)
        assertEquals("https://example.org/01.mp3", chapters[0].url)
        assertEquals(12 * 60 + 30, chapters[0].durationSecs)
        assertEquals(3600 + 5, chapters[1].durationSecs)
    }

    @Test
    fun `praeparierter Feed liest keine lokale Datei aus`() {
        val secret = File.createTempFile("lina-xxe-rss", ".txt").apply {
            writeText("GEHEIMNIS-AUS-DATEI")
            deleteOnExit()
        }
        val payload = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [ <!ENTITY xxe SYSTEM "file://${secret.absolutePath}"> ]>
            <rss><channel><item>
              <title>&xxe;</title>
              <enclosure url="https://example.org/01.mp3"/>
            </item></channel></rss>
        """.trimIndent()

        // Blockiertes DOCTYPE wirft – in fetchChapters landet das im
        // bestehenden catch und liefert eine leere Kapitelliste.
        val chapters = runCatching {
            repository.parseRssChapters(payload.byteInputStream())
        }.getOrDefault(emptyList())

        assertFalse(
            "Dateiinhalt über externe Entity ausgelesen: $chapters",
            chapters.any { it.title.contains("GEHEIMNIS-AUS-DATEI") },
        )
    }
}
