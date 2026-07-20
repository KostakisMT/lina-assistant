package dev.lina.feature.audiobook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DAISY-Bücher kommen von fremden Produktionsstellen – Schreibweisen und
 * Zeitformate schwanken. Der Parser muss das aushalten, sonst ist ein ganzes
 * Buch aus der Blindenhörbücherei unlesbar.
 */
class DaisyParserTest {

    private val ncc = """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
          "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
          <meta name="dc:title" content="Die Mutter" />
          <meta name="dc:creator" content="Maxim Gorki" />
          <meta name="ncc:totalTime" content="08:12:33" />
        </head>
        <body>
          <h1 class="title"><a href="kap01.smil#tx0001">Die M&uuml;tter &ndash; Vorwort</a></h1>
          <h1 class="chapter"><a href="kap01.smil#tx0002">Erstes Kapitel</a></h1>
          <h2 class="section"><a href="kap02.smil#tx0100">Abschnitt&nbsp;A</a></h2>
          <h1 class="chapter"><a href="kap03.smil#tx0200">Zweites Kapitel</a></h1>
        </body>
        </html>
    """.trimIndent()

    // ------------------------------------------------------------- ncc.html

    @Test
    fun `Metadaten aus ncc`() {
        val doc = DaisyParser.parseNcc(ncc)
        assertEquals("Die Mutter", doc.title)
        assertEquals("Maxim Gorki", doc.author)
        assertEquals(8 * 3600 + 12 * 60 + 33, doc.totalTimeSecs)
    }

    @Test
    fun `Kapitel stehen in Dokumentreihenfolge`() {
        // h1 und h2 wechseln sich ab – nach Ebene sortiert wäre die
        // Reihenfolge falsch und das Buch spränge beim Hören
        val titles = DaisyParser.parseNcc(ncc).entries.map { it.title }
        assertEquals(
            listOf("Die Mütter – Vorwort", "Erstes Kapitel", "Abschnitt A", "Zweites Kapitel"),
            titles,
        )
    }

    @Test
    fun `Sprungziele werden in Datei und Fragment zerlegt`() {
        val entries = DaisyParser.parseNcc(ncc).entries
        assertEquals("kap01.smil", entries[0].smilFile)
        assertEquals("tx0001", entries[0].fragmentId)
        assertEquals(1, entries[0].level)
        assertEquals(2, entries[2].level)
    }

    @Test
    fun `Umlaut-Entities werden aufgeloest`() {
        // ohne DTD scheitert ein XML-Parser sonst an &uuml;
        assertTrue(DaisyParser.parseNcc(ncc).entries[0].title.contains("Mütter"))
    }

    @Test
    fun `fehlende Metadaten fuehren nicht zum Absturz`() {
        val minimal = """
            <html><body><h1><a href="a.smil#x1">Kapitel eins</a></h1></body></html>
        """.trimIndent()
        val doc = DaisyParser.parseNcc(minimal)
        assertEquals("Unbekannter Titel", doc.title)
        assertEquals("Unbekannt", doc.author)
        assertEquals(1, doc.entries.size)
    }

    // ----------------------------------------------------------------- SMIL

    @Test
    fun `SMIL mit npt-Zeitangaben`() {
        val smil = """
            <?xml version="1.0"?>
            <smil>
              <body>
                <seq>
                  <par id="tx0001">
                    <audio src="audio01.mp3" clip-begin="npt=0.000s" clip-end="npt=125.500s"/>
                  </par>
                  <par id="tx0002">
                    <audio src="audio01.mp3" clip-begin="npt=125.500s" clip-end="npt=310.250s"/>
                  </par>
                </seq>
              </body>
            </smil>
        """.trimIndent()

        val audios = DaisyParser.parseSmil(smil)
        assertEquals(2, audios.size)
        // Die ID hängt am <par>, nicht am <audio>
        assertEquals("tx0002", audios[1].id)
        assertEquals("audio01.mp3", audios[1].src)
        assertEquals(125_500L, audios[1].clipBeginMs)
        assertEquals(310_250L, audios[1].clipEndMs)
    }

    @Test
    fun `SMIL in camelCase-Schreibweise`() {
        val smil = """
            <smil><body><seq><par id="a">
              <audio id="a1" src="teil2.mp3" clipBegin="00:01:05.5" clipEnd="00:02:10"/>
            </par></seq></body></smil>
        """.trimIndent()

        val audio = DaisyParser.parseSmil(smil).single()
        assertEquals("a1", audio.id)
        assertEquals(65_500L, audio.clipBeginMs)
        assertEquals(130_000L, audio.clipEndMs)
    }

    // ---------------------------------------------------------- Clock-Values

    @Test
    fun `Clock-Values in allen SMIL-Schreibweisen`() {
        assertEquals(125_500L, DaisyParser.parseClockValue("npt=125.5s"))
        assertEquals(125_500L, DaisyParser.parseClockValue("125.5s"))
        assertEquals(125_500L, DaisyParser.parseClockValue("125.5"))
        assertEquals(7_325_000L, DaisyParser.parseClockValue("02:02:05"))
        assertEquals(125_000L, DaisyParser.parseClockValue("02:05"))
        assertEquals(90_000L, DaisyParser.parseClockValue("1.5min"))
        assertEquals(7_200_000L, DaisyParser.parseClockValue("2h"))
        assertEquals(250L, DaisyParser.parseClockValue("250ms"))
    }

    @Test
    fun `leere oder kaputte Clock-Values ergeben null Millisekunden`() {
        assertEquals(0L, DaisyParser.parseClockValue(""))
        assertEquals(0L, DaisyParser.parseClockValue("   "))
        assertEquals(0L, DaisyParser.parseClockValue("keine ahnung"))
    }

    // ------------------------------------------------------------ Sanitizing

    @Test
    fun `DOCTYPE wird entfernt`() {
        val out = DaisyParser.sanitizeXhtml(
            """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0//EN" "x.dtd"><html/>"""
        )
        assertTrue(!out.contains("DOCTYPE"))
        assertTrue(out.contains("<html/>"))
    }

    @Test
    fun `XML-eigene Entities bleiben unangetastet`() {
        val out = DaisyParser.sanitizeXhtml("<p>Karl &amp; Rosa &lt;1919&gt;</p>")
        assertTrue(out.contains("&amp;"))
        assertTrue(out.contains("&lt;"))
    }
}
