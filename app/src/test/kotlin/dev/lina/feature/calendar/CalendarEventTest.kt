package dev.lina.feature.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventTest {

    @Test
    fun `JSON Rundtrip mit allen Feldern`() {
        val event = CalendarEvent(
            id = 3, title = "Zahnarzt", date = "2026-08-15", time = "14:30",
            reminderId = 7, source = "manual",
        )
        val restored = CalendarEvent.fromJson(event.toJson())
        assertEquals(event, restored)
    }

    @Test
    fun `JSON Rundtrip ohne Uhrzeit und ohne verknuepfte Erinnerung`() {
        val event = CalendarEvent(
            id = 1, title = "Frist", date = "2026-09-01", time = null,
            reminderId = null, source = "document",
        )
        val restored = CalendarEvent.fromJson(event.toJson())
        assertEquals(event, restored)
        assertNull(restored.time)
        assertNull(restored.reminderId)
    }

    @Test
    fun `Liste Rundtrip`() {
        val events = listOf(
            CalendarEvent(1, "A", "2026-08-01", null, null, "manual"),
            CalendarEvent(2, "B", "2026-08-02", "09:00", 5, "document"),
        )
        val json = CalendarEvent.listToJson(events)
        assertEquals(events, CalendarEvent.listFromJson(json))
    }

    @Test
    fun `kaputtes JSON liefert leere Liste statt Absturz`() {
        assertTrue(CalendarEvent.listFromJson("nicht valide { json").isEmpty())
    }
}
