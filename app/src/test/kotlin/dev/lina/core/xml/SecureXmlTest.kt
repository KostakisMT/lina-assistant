package dev.lina.core.xml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.io.StringReader
import org.xml.sax.InputSource

/**
 * Lina parst XML aus fremden Quellen (DAISY-Bücher, LibriVox-RSS über das
 * Netz). Ein unkonfigurierter DOM-Parser löst externe Entities auf – damit
 * könnte ein präparierter Feed lokale Dateien auslesen (XXE) oder die App per
 * Entity-Expansion aufhängen. Diese Tests halten die Härtung fest.
 */
class SecureXmlTest {

    private fun parseOrBlocked(xml: String): String? =
        runCatching {
            SecureXml.newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
                .documentElement
                .textContent
        }.getOrNull()

    @Test
    fun `externe Entity liest keine lokale Datei`() {
        val secret = File.createTempFile("lina-xxe", ".txt").apply {
            writeText("GEHEIMNIS-AUS-DATEI")
            deleteOnExit()
        }
        val payload = """
            <?xml version="1.0"?>
            <!DOCTYPE feed [ <!ENTITY xxe SYSTEM "file://${secret.absolutePath}"> ]>
            <feed>&xxe;</feed>
        """.trimIndent()

        // Entweder der Parser lehnt das DOCTYPE ganz ab (JVM/Xerces) oder das
        // Entity wird leer aufgelöst – der Dateiinhalt darf nie auftauchen.
        val text = parseOrBlocked(payload)
        assertFalse(
            "Dateiinhalt über externe Entity ausgelesen: $text",
            text?.contains("GEHEIMNIS-AUS-DATEI") == true,
        )
    }

    @Test
    fun `Entity-Expansion haengt die App nicht auf (Billion Laughs)`() {
        val payload = buildString {
            append("<?xml version=\"1.0\"?>\n<!DOCTYPE lol [\n")
            append("<!ENTITY lol \"lol\">\n")
            for (i in 1..9) {
                val prev = if (i == 1) "lol" else "lol${i - 1}"
                append("<!ENTITY lol$i \"" + "&$prev;".repeat(10) + "\">\n")
            }
            append("]>\n<lolz>&lol9;</lolz>")
        }

        val text = parseOrBlocked(payload)
        // Blockiert (null) oder leer aufgelöst – nur nicht milliardenfach expandiert.
        assertFalse(
            "Entity-Expansion fand statt (${text?.length} Zeichen)",
            (text?.length ?: 0) > 1000,
        )
    }

    @Test
    fun `normales XML wird weiterhin geparst`() {
        val text = parseOrBlocked("<feed><item>Kapitel 1</item></feed>")
        assertEquals("Kapitel 1", text)
    }
}
