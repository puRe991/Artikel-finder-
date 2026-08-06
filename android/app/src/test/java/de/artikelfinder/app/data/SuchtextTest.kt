package de.artikelfinder.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dieselben Beispiele wie in `SuchtextTests` auf der Backend-Seite. Weicht eine Seite ab,
 * findet die App Artikel nicht mehr, die der Katalog eigentlich enthält.
 */
class SuchtextTest {

    @Test
    fun `normalisieren liefert die kanonische Form`() {
        assertEquals("mueller kaese", Suchtext.normalisieren("Müller Käse!"))
        assertEquals("bio vollmilch 3 8", Suchtext.normalisieren("  Bio   Vollmilch 3,8%  "))
        assertEquals("creme fraiche", Suchtext.normalisieren("Crème fraîche"))
        assertEquals("strassenkaese", Suchtext.normalisieren("Straßenkäse"))
        assertEquals("", Suchtext.normalisieren(null))
        assertEquals("", Suchtext.normalisieren("   "))
    }

    @Test
    fun `Index enthaelt beide Umlaut-Schreibweisen`() {
        val index = Suchtext.fuerIndex("Bärenmarke")

        assertTrue(index.contains("baerenmarke"))
        assertTrue(index.contains("barenmarke"))
    }

    @Test
    fun `Index dupliziert nicht ohne Umlaute`() {
        assertEquals("vollmilch", Suchtext.fuerIndex("Vollmilch"))
    }

    @Test
    fun `normalisieren erzeugt keine LIKE-Platzhalter`() {
        // Aus dem normalisierten Text werden LIKE-Muster gebaut; kaemen % oder _ durch,
        // koennte eine Suche den halben Katalog zurueckgeben.
        val normalisiert = Suchtext.normalisieren("100% _Rabatt_ 50%")

        assertFalse(normalisiert.contains('%'))
        assertFalse(normalisiert.contains('_'))
    }
}

class EanTest {

    @Test
    fun `normalisieren raeumt die Eingabe auf`() {
        assertEquals("4008400202990", Ean.normalisieren("4008400202990"))
        assertEquals("4008400202990", Ean.normalisieren(" 4008-400-202990 "))
        assertEquals("0000000000000", Ean.normalisieren("0000000000000"))
        assertEquals(null, Ean.normalisieren("123"))
        assertEquals(null, Ean.normalisieren("123456789012345"))
        assertEquals(null, Ean.normalisieren(null))
    }

    @Test
    fun `Pruefziffer akzeptiert echte Barcodes`() {
        assertTrue(Ean.pruefzifferKorrekt("4008400202990"))
        assertTrue(Ean.pruefzifferKorrekt("4045317058067"))
        assertTrue(Ean.pruefzifferKorrekt("40084015"))
    }

    @Test
    fun `Pruefziffer lehnt Tippfehler ab`() {
        assertFalse(Ean.pruefzifferKorrekt("4008400202991"))
        assertFalse(Ean.pruefzifferKorrekt("4008400202900"))
        assertFalse(Ean.pruefzifferKorrekt("123456789"))
    }
}
