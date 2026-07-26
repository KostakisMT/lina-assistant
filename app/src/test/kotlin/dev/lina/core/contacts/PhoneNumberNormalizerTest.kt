package dev.lina.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun `normalize entfernt alles ausser Ziffern`() {
        assertEquals("491511234567", PhoneNumberNormalizer.normalize("+49 151 1234567"))
        assertEquals("01511234567", PhoneNumberNormalizer.normalize("0151-1234567"))
    }

    @Test
    fun `matches erkennt gleiche Nummer trotz Landesvorwahl-Schreibweise`() {
        assertTrue(PhoneNumberNormalizer.matches("+49 151 1234567", "0151 1234567"))
        assertTrue(PhoneNumberNormalizer.matches("0151-123-4567", "(0151) 1234567"))
    }

    @Test
    fun `matches erkennt unterschiedliche Nummern korrekt als verschieden`() {
        assertFalse(PhoneNumberNormalizer.matches("0151 1234567", "0160 7654321"))
    }

    @Test
    fun `matches schuetzt Kurznummern vor Fehltreffern`() {
        assertFalse(PhoneNumberNormalizer.matches("112", "49151234567112"))
        assertTrue(PhoneNumberNormalizer.matches("112", "112"))
    }

    @Test
    fun `matches ist false bei leeren Nummern`() {
        assertFalse(PhoneNumberNormalizer.matches("", "0151 1234567"))
        assertFalse(PhoneNumberNormalizer.matches("abc", "0151 1234567"))
    }
}
