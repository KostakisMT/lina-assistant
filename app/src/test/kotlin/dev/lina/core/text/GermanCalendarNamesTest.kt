package dev.lina.core.text

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class GermanCalendarNamesTest {

    @Test
    fun `Wochentag-Index stimmt`() {
        // Montag, 20.07.2026
        val cal = Calendar.getInstance().apply { set(2026, Calendar.JULY, 20, 9, 0, 0) }
        assertEquals("Montag", GermanCalendarNames.weekdayName(cal))
    }

    @Test
    fun `Sonntag ist der erste Eintrag`() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.JULY, 19, 9, 0, 0) }
        assertEquals("Sonntag", GermanCalendarNames.weekdayName(cal))
    }

    @Test
    fun `Monat-Index stimmt`() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.JULY, 20, 9, 0, 0) }
        assertEquals("Juli", GermanCalendarNames.monthName(cal))
        val jan = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1, 9, 0, 0) }
        assertEquals("Januar", GermanCalendarNames.monthName(jan))
    }

    @Test
    fun `beide Listen haben die richtige Laenge`() {
        assertEquals(7, GermanCalendarNames.wochentage.size)
        assertEquals(12, GermanCalendarNames.monate.size)
    }
}
