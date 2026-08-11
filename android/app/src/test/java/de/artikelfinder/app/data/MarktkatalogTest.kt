package de.artikelfinder.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarktkatalogTest {

    @Test
    fun `Bekannte Ketten landen in ihrer Kategorie`() {
        assertEquals(Marktkatalog.BAUMAERKTE, Marktkatalog.kategorieFuer("OBI"))
        assertEquals(Marktkatalog.ELEKTROMAERKTE, Marktkatalog.kategorieFuer("Saturn"))
        assertEquals(Marktkatalog.SUPERMAERKTE, Marktkatalog.kategorieFuer("Kaufland"))
    }

    @Test
    fun `Gross- und Kleinschreibung spielt keine Rolle`() {
        assertEquals(Marktkatalog.ELEKTROMAERKTE, Marktkatalog.kategorieFuer("  alternate "))
        assertEquals(Marktkatalog.BAUMAERKTE, Marktkatalog.kategorieFuer("Hornbach"))
    }

    @Test
    fun `Ein Markt mit Ortszusatz behaelt die Kategorie seiner Kette`() {
        assertEquals(Marktkatalog.SUPERMAERKTE, Marktkatalog.kategorieFuer("Kaufland Gießen"))
        assertEquals(Marktkatalog.ELEKTROMAERKTE, Marktkatalog.kategorieFuer("expert Klein Koblenz"))
    }

    @Test
    fun `Unbekannte Ketten landen unter Weitere Maerkte`() {
        assertEquals(Marktkatalog.WEITERE, Marktkatalog.kategorieFuer("Hofladen Meier"))
    }

    @Test
    fun `Jede vorgeschlagene Kette kommt nur einmal vor`() {
        val namen = Marktkatalog.VORGESCHLAGENE_KETTEN.map { it.name.lowercase() }

        assertEquals(namen.size, namen.toSet().size)
        assertTrue(Marktkatalog.VORGESCHLAGENE_KETTEN.all { it.kategorie in Marktkatalog.REIHENFOLGE })
    }
}
