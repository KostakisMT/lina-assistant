package dev.lina.core.contacts

import dev.lina.core.contacts.PhoneNumberRisk.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Testnummern stammen aus dem echten Datensatz, der beim Klientenbesuch
 * am 2026-08-30 von einer Vodafone-SIM ins Telefonbuch kam
 * (tablet-data/testdaten/vodafone-sim.vcf) – nicht ausgedacht.
 */
class PhoneNumberRiskTest {

    // ── Notruf: darf NIEMALS eine Rückfrage auslösen ─────────────────────

    @Test
    fun `Notrufnummern werden sofort gewaehlt`() {
        listOf("110", "112", "116117", "116116", "19222").forEach {
            assertEquals(it, Category.EMERGENCY, PhoneNumberRisk.classify(it))
            assertFalse(it, PhoneNumberRisk.classify(it).needsConfirmation())
        }
    }

    /** Notruf schlägt die Kurzwahl-Regel, obwohl 112 nur drei Ziffern hat. */
    @Test
    fun `Notruf schlaegt die Kurzwahl-Regel`() {
        assertEquals(Category.EMERGENCY, PhoneNumberRisk.classify("112"))
    }

    // ── Die echten SIM-Einträge ──────────────────────────────────────────

    @Test
    fun `Auskunftsdienste sind Premium`() {
        // "Auskunft 11880 199ct/Min" und "Auslandsauskunft 199ct/Min"
        assertEquals(Category.PREMIUM, PhoneNumberRisk.classify("118802899"))
        assertEquals(Category.PREMIUM, PhoneNumberRisk.classify("11890"))
    }

    @Test
    fun `Anbieter-Kurzwahlen brauchen Rueckfrage`() {
        // Tarot 22377, Horoskop 22335, PartnerschaftLiebe 22484, Mailbox 5500
        listOf("22377", "22335", "22484", "22464", "5500", "6729", "22988").forEach {
            assertEquals(it, Category.SHORT_CODE, PhoneNumberRisk.classify(it))
            assertTrue(it, PhoneNumberRisk.classify(it).needsConfirmation())
        }
    }

    @Test
    fun `Servicenummern mit Sondertarif`() {
        // "Deutsche Bahn 60ct" → 01806996633
        assertEquals(Category.SERVICE, PhoneNumberRisk.classify("01806996633"))
    }

    @Test
    fun `klassische Premium-Vorwahlen`() {
        assertEquals(Category.PREMIUM, PhoneNumberRisk.classify("09001234567"))
        assertEquals(Category.PREMIUM, PhoneNumberRisk.classify("013712345"))
    }

    // ── Echte Kontakte müssen ungehindert durchgehen ─────────────────────

    @Test
    fun `normale Rufnummern loesen keine Rueckfrage aus`() {
        listOf(
            "+49 1555 5501234",   // Boris Hartmann
            "+49 171 5550103",    // Ulla
            "0441 12345678",      // Festnetz Oldenburg
            "004917155501",
            "08008001070",        // Bestellhotline, kostenlose 0800
        ).forEach {
            assertEquals(it, Category.NORMAL, PhoneNumberRisk.classify(it))
            assertFalse(it, PhoneNumberRisk.classify(it).needsConfirmation())
        }
    }

    /** Landesvorwahl-Schreibweisen dürfen die Erkennung nicht aushebeln. */
    @Test
    fun `Premium wird auch mit Landesvorwahl erkannt`() {
        assertEquals(Category.PREMIUM, PhoneNumberRisk.classify("+499001234567"))
        assertEquals(Category.PREMIUM, PhoneNumberRisk.classify("00499001234567"))
    }

    /** Auslandsnummern lassen sich nicht beurteilen – lieber nicht nachfragen. */
    @Test
    fun `auslaendische Nummern gelten als normal`() {
        assertEquals(Category.NORMAL, PhoneNumberRisk.classify("+41791234567"))
        assertEquals(Category.NORMAL, PhoneNumberRisk.classify("+12125551234"))
    }

    @Test
    fun `leere Eingabe faellt nicht um`() {
        assertEquals(Category.NORMAL, PhoneNumberRisk.classify(null))
        assertEquals(Category.NORMAL, PhoneNumberRisk.classify(""))
        assertEquals(Category.NORMAL, PhoneNumberRisk.classify("   "))
    }

    @Test
    fun `Rueckfrage nennt den Namen und den Grund`() {
        val text = PhoneNumberRisk.confirmationPrompt("Tarot", Category.SHORT_CODE)
        assertTrue(text.contains("Tarot"))
        assertTrue(text.contains("Kurzwahl"))
        assertTrue(text.trim().endsWith("?"))
    }
}
