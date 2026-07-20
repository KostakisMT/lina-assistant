package dev.lina.feature.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Der Parser läuft offline auf dem Tablet – verhört er sich, geht die
 * Erinnerung still verloren. Deshalb hier die Formulierungen, die der
 * Testnutzer tatsächlich benutzt.
 *
 * Bezugszeitpunkt aller Tests: Montag, 20.07.2026, 09:00 Ortszeit.
 */
class GermanTimeParserTest {

    private val now = at(2026, 7, 20, 9, 0)

    // ------------------------------------------------------- relative Zeiten

    @Test
    fun `in zwanzig Minuten`() {
        val r = GermanTimeParser.parse("erinnere mich in zwanzig minuten an die suppe", now)!!
        assertEquals(now + 20 * 60_000L, r.triggerAtMillis)
        assertEquals("an die suppe", r.text)
        assertTrue(!r.daily)
    }

    @Test
    fun `in Ziffern geschriebene Minuten`() {
        val r = GermanTimeParser.parse("erinnere mich in 5 minuten an den ofen", now)!!
        assertEquals(now + 5 * 60_000L, r.triggerAtMillis)
    }

    @Test
    fun `in einer halben Stunde`() {
        val r = GermanTimeParser.parse("erinnere mich in einer halben stunde an den tee", now)!!
        assertEquals(now + 30 * 60_000L, r.triggerAtMillis)
    }

    @Test
    fun `in einer Stunde`() {
        val r = GermanTimeParser.parse("erinnere mich in einer stunde an die post", now)!!
        assertEquals(now + 60 * 60_000L, r.triggerAtMillis)
    }

    @Test
    fun `in zwei Stunden`() {
        val r = GermanTimeParser.parse("erinnere mich in zwei stunden an das essen", now)!!
        assertEquals(now + 2 * 60 * 60_000L, r.triggerAtMillis)
    }

    // ------------------------------------------------------------- Uhrzeiten

    @Test
    fun `morgen um zehn`() {
        val r = GermanTimeParser.parse("erinnere mich morgen um zehn an den arzt", now)!!
        assertDate(r.triggerAtMillis, day = 21, hour = 10, minute = 0)
        assertEquals("an den arzt", r.text)
    }

    @Test
    fun `uebermorgen um acht`() {
        val r = GermanTimeParser.parse("erinnere mich übermorgen um acht an den müll", now)!!
        assertDate(r.triggerAtMillis, day = 22, hour = 8, minute = 0)
    }

    @Test
    fun `halb acht meint sieben Uhr dreissig`() {
        val r = GermanTimeParser.parse("erinnere mich um halb acht an die tagesschau", now)!!
        assertDate(r.triggerAtMillis, day = 21, hour = 7, minute = 30)
    }

    @Test
    fun `viertel nach sieben`() {
        val r = GermanTimeParser.parse("erinnere mich um viertel nach sieben ans radio", now)!!
        assertDate(r.triggerAtMillis, day = 21, hour = 7, minute = 15)
    }

    @Test
    fun `viertel vor acht`() {
        val r = GermanTimeParser.parse("erinnere mich um viertel vor acht an den bus", now)!!
        assertDate(r.triggerAtMillis, day = 21, hour = 7, minute = 45)
    }

    @Test
    fun `Uhrzeit mit Minuten`() {
        val r = GermanTimeParser.parse("erinnere mich um 14 uhr 30 an den anruf", now)!!
        assertDate(r.triggerAtMillis, day = 20, hour = 14, minute = 30)
    }

    @Test
    fun `abends verschiebt in den Nachmittag`() {
        val r = GermanTimeParser.parse("erinnere mich um acht abends an die tabletten", now)!!
        assertDate(r.triggerAtMillis, day = 20, hour = 20, minute = 0)
    }

    @Test
    fun `vergangene Uhrzeit rutscht auf morgen`() {
        // 8 Uhr ist um 9 Uhr bereits vorbei
        val r = GermanTimeParser.parse("erinnere mich um acht an die zeitung", now)!!
        assertDate(r.triggerAtMillis, day = 21, hour = 8, minute = 0)
    }

    @Test
    fun `spaetere Uhrzeit bleibt heute`() {
        val r = GermanTimeParser.parse("erinnere mich um 17 uhr an das abendessen", now)!!
        assertDate(r.triggerAtMillis, day = 20, hour = 17, minute = 0)
    }

    // ---------------------------------------------------------- Wiederholung

    @Test
    fun `jeden Tag ist eine taegliche Erinnerung`() {
        val r = GermanTimeParser.parse("erinnere mich jeden tag um acht an die tabletten", now)!!
        assertTrue(r.daily)
        assertDate(r.triggerAtMillis, day = 21, hour = 8, minute = 0)
        assertEquals("an die tabletten", r.text)
    }

    @Test
    fun `taeglich ist eine taegliche Erinnerung`() {
        val r = GermanTimeParser.parse("erinnere mich täglich um 18 uhr an den hund", now)!!
        assertTrue(r.daily)
    }

    // ---------------------------------------------------------------- Sachtext

    @Test
    fun `Sachteil vor der Zeitangabe wird ohne Zeit uebernommen`() {
        val r = GermanTimeParser.parse("erinnere mich an den arzt um zehn", now)!!
        assertEquals("an den arzt", r.text)
    }

    @Test
    fun `ohne erkennbaren Sachteil bleibt ein neutraler Text`() {
        val r = GermanTimeParser.parse("erinnere mich um 16 uhr", now)!!
        assertEquals("deine Erinnerung", r.text)
    }

    // ------------------------------------------------------------ kein Treffer

    @Test
    fun `Satz ohne Zeitangabe liefert null`() {
        // null heißt: Ebene 2 (Claude) soll es versuchen
        assertNull(GermanTimeParser.parse("erinnere mich an den arzt", now))
        assertNull(GermanTimeParser.parse("wie ist das wetter", now))
    }

    @Test
    fun `unsinnige Uhrzeit liefert null`() {
        assertNull(GermanTimeParser.parse("erinnere mich um 99 uhr an etwas", now))
    }

    // ----------------------------------------------------------------- Hilfen

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun assertDate(millis: Long, day: Int, hour: Int, minute: Int) {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        assertEquals("Tag", day, c.get(Calendar.DAY_OF_MONTH))
        assertEquals("Stunde", hour, c.get(Calendar.HOUR_OF_DAY))
        assertEquals("Minute", minute, c.get(Calendar.MINUTE))
    }
}
