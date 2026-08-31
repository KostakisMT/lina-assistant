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
    /**
     * Regression 2026-08-30: partition() prüfte nur gegen den Bestand, nicht
     * gegen die schon akzeptierten Kandidaten. Beim Zusammenführen von
     * iCloud- und Yahoo-Export desselben iPads steht dieselbe Person in
     * beiden Dateien – vorher landeten beide Einträge im Telefonbuch.
     */
    @Test
    fun `Dubletten innerhalb des Imports werden erkannt`() {
        val incoming = listOf(
            Contact(1, "Boris Hartmann", "+4915555501234"),
            Contact(2, "Boris Hartmann", "01555 5501234"),   // gleiche Nummer, Yahoo-Schreibweise
            Contact(3, "Ulla Winter", "+491715550103"),
        )

        val result = ContactDedup.partition(existing = emptyList(), incoming = incoming)

        assertEquals(2, result.new.size)
        assertEquals(1, result.duplicateCount)
    }

    /** Bestand und Import-Dubletten zusammen. */
    @Test
    fun `Bestand und interne Dubletten werden gemeinsam gefiltert`() {
        val existing = listOf(Contact(9, "Ulla Winter", "+491715550103"))
        val incoming = listOf(
            Contact(1, "Ulla Winter", "0171 5550103"),       // schon im Bestand
            Contact(2, "Boris Hartmann", "+4915555501234"),
            Contact(3, "Boris H.", "+4915555501234"),        // Dublette im Import
        )

        val result = ContactDedup.partition(existing, incoming)

        assertEquals(1, result.new.size)
        assertEquals("Boris Hartmann", result.new.first().displayName)
        assertEquals(2, result.duplicateCount)
    }

}
