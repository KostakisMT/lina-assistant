package dev.lina.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardParserTest {

    @Test
    fun `FN wird als Anzeigename genutzt`() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Boris Hartmann
            TEL;TYPE=CELL:+49 151 1234567
            END:VCARD
        """.trimIndent()

        val contacts = VCardParser.parse(vcard)

        assertEquals(1, contacts.size)
        assertEquals("Boris Hartmann", contacts.first().displayName)
        assertEquals("+49 151 1234567", contacts.first().phoneNumber)
    }

    @Test
    fun `fehlendes FN faellt auf N zurueck`() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            N:Hartmann;Boris;;;
            TEL:0151 1234567
            END:VCARD
        """.trimIndent()

        val contacts = VCardParser.parse(vcard)

        assertEquals(1, contacts.size)
        assertEquals("Boris Hartmann", contacts.first().displayName)
    }

    @Test
    fun `mehrere TEL-Zeilen ergeben mehrere Kontakte`() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ulla Winter
            TEL;TYPE=CELL:0160 1111111
            TEL;TYPE=HOME:030 2222222
            END:VCARD
        """.trimIndent()

        val contacts = VCardParser.parse(vcard)

        assertEquals(2, contacts.size)
        assertTrue(contacts.all { it.displayName == "Ulla Winter" })
        assertEquals(setOf("0160 1111111", "030 2222222"), contacts.map { it.phoneNumber }.toSet())
    }

    @Test
    fun `mehrere Kontakte in einer Datei`() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Boris Hartmann
            TEL:0151 1111111
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Ulla Winter
            TEL:0160 2222222
            END:VCARD
        """.trimIndent()

        val contacts = VCardParser.parse(vcard)

        assertEquals(2, contacts.size)
        assertEquals("Boris Hartmann", contacts[0].displayName)
        assertEquals("Ulla Winter", contacts[1].displayName)
    }

    @Test
    fun `Kontakt ohne Telefonnummer wird uebersprungen`() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ohne Nummer
            END:VCARD
        """.trimIndent()

        assertEquals(0, VCardParser.parse(vcard).size)
    }

    @Test
    fun `Kontakt ohne Namen bekommt Fallback Unbekannt`() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            TEL:0151 1234567
            END:VCARD
        """.trimIndent()

        val contacts = VCardParser.parse(vcard)

        assertEquals(1, contacts.size)
        assertEquals("Unbekannt", contacts.first().displayName)
    }

    @Test
    fun `Zeilenfortsetzung wird korrekt entfaltet`() {
        // RFC 6350: eine Fortsetzungszeile beginnt mit einem eingefügten
        // Leerzeichen, das beim Entfalten entfernt wird – das trennende
        // Leerzeichen im Namen muss also schon am Ende der ersten Zeile stehen.
        val vcard = "BEGIN:VCARD\r\n" +
            "VERSION:3.0\r\n" +
            "FN:Boris \r\n" +
            " Hartmann\r\n" +
            "TEL:0151 1234567\r\n" +
            "END:VCARD"

        val contacts = VCardParser.parse(vcard)

        assertEquals(1, contacts.size)
        assertEquals("Boris Hartmann", contacts.first().displayName)
    }

    @Test
    fun `defekter Block ohne END wird ignoriert`() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Kaputt
            TEL:0151 1234567
        """.trimIndent()

        assertEquals(0, VCardParser.parse(vcard).size)
    }

    @Test
    fun `leerer Text liefert leere Liste`() {
        assertEquals(0, VCardParser.parse("").size)
    }
    /**
     * Ende-zu-Ende-Absicherung fuer den iPad-Weg (scripts/ipad-import.sh +
     * ipad_contacts_to_vcard.py): exakt das Format, das der Konverter aus
     * einer iOS-AddressBook.sqlitedb erzeugt. Bricht dieser Test, passt der
     * Konverter nicht mehr zum Parser und der Kontakt-Import schlaegt beim
     * Nutzer fehl - gemerkt wuerde es sonst erst vor Ort.
     */
    @Test
    fun `vCard aus dem iPad-Konverter wird gelesen`() {
        val vcf = listOf(
            "BEGIN:VCARD", "VERSION:3.0", "N:Hartmann;Boris;;;", "FN:Boris Hartmann",
            "TEL;TYPE=CELL:+4915555501234", "END:VCARD",
            "BEGIN:VCARD", "VERSION:3.0", "N:Es\u00dffeld;Dirk;;;", "FN:Dirk Es\u00dffeld",
            "TEL;TYPE=CELL:+491715550103", "END:VCARD",
            "BEGIN:VCARD", "VERSION:3.0", "N:Nummern;Zwei;;;", "FN:Zwei Nummern",
            "TEL;TYPE=CELL:+491701111111", "TEL;TYPE=CELL:01702222222", "END:VCARD",
        ).joinToString("\r\n")

        val contacts = VCardParser.parse(vcf)

        // Umlaut/ss ueberleben den Weg durch den Konverter
        assertTrue(contacts.any { it.displayName == "Dirk Es\u00dffeld" })
        assertTrue(contacts.any { it.displayName == "Boris Hartmann" && it.phoneNumber == "+4915555501234" })
        // Beide Nummern eines Kontakts kommen an
        val zwei = contacts.filter { it.displayName == "Zwei Nummern" }.map { it.phoneNumber }
        assertTrue("+491701111111" in zwei)
        assertTrue("01702222222" in zwei)
    }

}
