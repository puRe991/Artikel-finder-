package de.artikelfinder.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EingabeTest {

    @Test
    fun `Betrag akzeptiert Komma und Punkt`() {
        // Auf der deutschen Tastatur tippt man das Komma, die API erwartet einen Punkt.
        assertEquals(1.49, "1,49".alsBetrag()!!, 0.0001)
        assertEquals(1.49, "1.49".alsBetrag()!!, 0.0001)
        assertEquals(1.49, "  1,49  ".alsBetrag()!!, 0.0001)
    }

    @Test
    fun `Betrag lehnt Unsinn und Nullpreise ab`() {
        assertNull("".alsBetrag())
        assertNull("abc".alsBetrag())
        assertNull("0".alsBetrag())
        assertNull("-1,50".alsBetrag())
    }

    @Test
    fun `Aktionsdatum gilt bis zum Ende des Tages`() {
        val ergebnis = "31.12.2026".alsIsoDatum()

        // Ohne die Uhrzeit wäre eine Aktion am letzten Tag bereits um 00 Uhr abgelaufen.
        assertEquals("2026-12-31T23:59:59Z", ergebnis)
    }

    @Test
    fun `Aktionsdatum lehnt unvollstaendige Eingaben ab`() {
        assertNull("31.12".alsIsoDatum())
        assertNull("2026-12-31".alsIsoDatum())
        assertNull("".alsIsoDatum())
    }
}
