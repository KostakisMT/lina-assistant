package dev.lina.feature.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * Bezugszeitpunkt aller Tests: Montag, 20.07.2026, 09:00 Ortszeit (gleicher
 * Bezugspunkt wie GermanTimeParserTest, zur Vergleichbarkeit).
 */
class GermanDateParserTest {

    private val now = at(2026, 7, 20, 9, 0)

    @Test
    fun `heute morgen uebermorgen`() {
        assertEquals("2026-07-20", GermanDateParser.parse("heute", now)!!.date)
        assertEquals("2026-07-21", GermanDateParser.parse("morgen", now)!!.date)
        assertEquals("2026-07-22", GermanDateParser.parse("übermorgen", now)!!.date)
    }

    @Test
    fun `in N Tagen`() {
        assertEquals("2026-07-23", GermanDateParser.parse("in 3 tagen", now)!!.date)
        assertEquals("2026-07-25", GermanDateParser.parse("in fünf tagen", now)!!.date)
    }

    @Test
    fun `in einer Woche`() {
        assertEquals("2026-07-27", GermanDateParser.parse("in einer woche", now)!!.date)
    }

    @Test
    fun `naechsten Montag ueberspringt heute`() {
        // "now" ist bereits ein Montag – "nächsten Montag" darf nicht heute meinen.
        assertEquals("2026-07-27", GermanDateParser.parse("nächsten montag", now)!!.date)
    }

    @Test
    fun `diesen Freitag`() {
        assertEquals("2026-07-24", GermanDateParser.parse("diesen freitag", now)!!.date)
    }

    @Test
    fun `explizites Datum mit Monatsname in der Zukunft`() {
        assertEquals("2026-08-15", GermanDateParser.parse("am 15. august", now)!!.date)
    }

    @Test
    fun `explizites Datum mit Monatsname in der Vergangenheit rutscht ins naechste Jahr`() {
        assertEquals("2027-03-15", GermanDateParser.parse("am 15. märz", now)!!.date)
    }

    @Test
    fun `numerisches Datum ohne Jahr`() {
        assertEquals("2026-08-15", GermanDateParser.parse("15.8.", now)!!.date)
    }

    @Test
    fun `numerisches Datum mit Jahr`() {
        assertEquals("2027-03-15", GermanDateParser.parse("15.03.2027", now)!!.date)
    }

    @Test
    fun `optionale Uhrzeit wird miterkannt`() {
        val r = GermanDateParser.parse("übermorgen um 10 Uhr", now)!!
        assertEquals("2026-07-22", r.date)
        assertEquals("10:00", r.time)
    }

    @Test
    fun `ohne Uhrzeit ist time null`() {
        assertNull(GermanDateParser.parse("morgen", now)!!.time)
    }

    @Test
    fun `Muell liefert null`() {
        assertNull(GermanDateParser.parse("irgendwann mal vielleicht", now))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
