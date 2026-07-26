package dev.lina.core.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimIdentityTest {

    @Test
    fun `composite ist deterministisch fuer gleiche Eingaben`() {
        val a = SimIdentity.composite(1, "Telekom", "de", "123456")
        val b = SimIdentity.composite(1, "Telekom", "de", "123456")
        assertEquals(a, b)
    }

    @Test
    fun `composite unterscheidet sich bei unterschiedlichem Carrier`() {
        val a = SimIdentity.composite(1, "Telekom", "de", "123456")
        val b = SimIdentity.composite(1, "Vodafone", "de", "123456")
        assertFalse(a == b)
    }

    @Test
    fun `hasChanged ist false wenn previous null ist (erster Start)`() {
        assertFalse(SimIdentity.hasChanged(null, "irgendein-fingerabdruck"))
    }

    @Test
    fun `hasChanged ist false bei identischem Fingerabdruck`() {
        val current = SimIdentity.composite(1, "Telekom", "de", "123456")
        assertFalse(SimIdentity.hasChanged(current, current))
    }

    @Test
    fun `hasChanged ist true bei unterschiedlichem Fingerabdruck`() {
        val previous = SimIdentity.composite(1, "Telekom", "de", "123456")
        val current = SimIdentity.composite(2, "Vodafone", "de", "654321")
        assertTrue(SimIdentity.hasChanged(previous, current))
    }

    @Test
    fun `composite kommt mit fehlenden Feldern klar`() {
        val fingerprint = SimIdentity.composite(0, null, null, null)
        assertEquals("0|||", fingerprint)
    }
}
