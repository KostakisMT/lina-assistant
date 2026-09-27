package dev.lina.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Das Protokoll wird zeilenweise ausgewertet (grep, Claude). Eine Äußerung mit
 * Zeilenumbruch darf deshalb nie auf zwei Zeilen zerfallen, und der Tageswechsel
 * muss in Ortszeit passieren – nächtliche Vorfälle sollen in der Datei des
 * Abends landen, in dem sie für den Nutzer passiert sind.
 */
class ProtokollTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun millis(jahr: Int, monat: Int, tag: Int, h: Int, m: Int, s: Int = 0, ms: Int = 0): Long =
        ZonedDateTime.of(jahr, monat, tag, h, m, s, ms * 1_000_000, berlin).toInstant().toEpochMilli()

    @Test
    fun `zeile hat Uhrzeit, Tag und Text`() {
        val z = Protokoll.zeile(millis(2026, 9, 27, 14, 5, 12, 345), "WhisperStt", "Transkription \"ja\"", berlin)
        assertEquals("14:05:12.345 [WhisperStt] Transkription \"ja\"\n", z)
    }

    @Test
    fun `zeilenumbrueche im Text bleiben eine Zeile`() {
        val z = Protokoll.zeile(millis(2026, 9, 27, 14, 0), "LinaLauncher", "erste\r\nzweite\ndritte", berlin)
        assertEquals(1, z.count { it == '\n' })
        assertFalse(z.contains('\r'))
        assertEquals("14:00:00.000 [LinaLauncher] erste ⏎ zweite ⏎ dritte\n", z)
    }

    @Test
    fun `dateiname folgt dem Ortsdatum, nicht UTC`() {
        // 00:30 Ortszeit ist in UTC noch der Vortag
        assertEquals("2026-09-28.log", Protokoll.dateiname(millis(2026, 9, 28, 0, 30), berlin))
        assertEquals("2026-09-27.log", Protokoll.dateiname(millis(2026, 9, 27, 23, 59), berlin))
    }
}
