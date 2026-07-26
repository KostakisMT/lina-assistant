package dev.lina.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDedupTest {

    private fun contact(id: Long, name: String, number: String) = Contact(id, name, number)

    @Test
    fun `neue Kontakte ohne Ueberlappung`() {
        val existing = listOf(contact(1, "Boris Hartmann", "0151 1111111"))
        val incoming = listOf(contact(0, "Ulla Winter", "0160 2222222"))

        val result = ContactDedup.partition(existing, incoming)

        assertEquals(1, result.new.size)
        assertEquals("Ulla Winter", result.new.first().displayName)
        assertEquals(0, result.duplicateCount)
    }

    @Test
    fun `Duplikate werden per Telefonnummer erkannt`() {
        val existing = listOf(contact(1, "Boris Hartmann", "+49 151 1111111"))
        val incoming = listOf(contact(0, "Boris", "0151 1111111"))

        val result = ContactDedup.partition(existing, incoming)

        assertTrue(result.new.isEmpty())
        assertEquals(1, result.duplicateCount)
    }

    @Test
    fun `gemischte Liste trennt neue und doppelte korrekt`() {
        val existing = listOf(contact(1, "Boris Hartmann", "0151 1111111"))
        val incoming = listOf(
            contact(0, "Boris", "0151 1111111"),
            contact(0, "Ulla Winter", "0160 2222222"),
        )

        val result = ContactDedup.partition(existing, incoming)

        assertEquals(1, result.new.size)
        assertEquals("Ulla Winter", result.new.first().displayName)
        assertEquals(1, result.duplicateCount)
    }

    @Test
    fun `leere Listen liefern leeres Ergebnis`() {
        assertEquals(0, ContactDedup.partition(emptyList(), emptyList()).new.size)
        assertEquals(0, ContactDedup.partition(emptyList(), emptyList()).duplicateCount)
    }
}
