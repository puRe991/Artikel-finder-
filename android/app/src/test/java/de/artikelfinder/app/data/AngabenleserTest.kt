package de.artikelfinder.app.data

import de.artikelfinder.app.data.local.ArtikelEintrag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aufbereitet wurden die Angaben schon beim Import — hier wird nur noch aufgeteilt. Der
 * interessante Fall ist der leere: „keine Angabe" darf nie wie „enthält nichts davon"
 * aussehen, und dafür muss der Unterschied bis in die Oberfläche tragen.
 */
class AngabenleserTest {

    @Test
    fun `Allergene und Spuren werden getrennt gelesen`() {
        val angaben = Angabenleser.lesen(
            artikel(allergene = "Milch, Schalenfrüchte, Soja", spuren = "Eier, Erdnüsse")
        )

        assertEquals(listOf("Milch", "Schalenfrüchte", "Soja"), angaben.allergene)
        assertEquals(listOf("Eier", "Erdnüsse"), angaben.spuren)
        assertTrue(angaben.hatAllergeninfo)
    }

    @Test
    fun `Ohne Angaben bleiben die Listen leer`() {
        val angaben = Angabenleser.lesen(artikel())

        assertTrue(angaben.allergene.isEmpty())
        assertTrue(angaben.spuren.isEmpty())
        assertFalse("Ohne Daten gibt es nichts zu behaupten", angaben.hatAllergeninfo)
        assertTrue(angaben.istLeer)
    }

    @Test
    fun `Naehrwerte kommen in der Reihenfolge der Packungstabelle`() {
        val angaben = Angabenleser.lesen(
            artikel(naehrwerte = "salz=0.2;kcal=250;fett=12.5;zucker=18.86")
        )

        assertEquals(
            listOf("Energie", "Fett", "davon Zucker", "Salz"),
            angaben.naehrwerte.map { it.bezeichnung },
        )
        assertEquals("250 kcal", angaben.naehrwerte.first().wert)
        // Deutsches Dezimalkomma erst bei der Anzeige, in der Datei steht der Punkt.
        assertEquals("12,5 g", angaben.naehrwerte[1].wert)
    }

    @Test
    fun `Unbekannte Naehrwertschluessel werden uebergangen`() {
        val angaben = Angabenleser.lesen(artikel(naehrwerte = "kcal=100;quatsch=7;kaputt;=5"))

        assertEquals(1, angaben.naehrwerte.size)
        assertEquals("100 kcal", angaben.naehrwerte.first().wert)
    }

    @Test
    fun `Nutriscore nur als Note von a bis e`() {
        assertEquals("c", Angabenleser.lesen(artikel(nutriscore = "C")).nutriscore)
        assertNull(Angabenleser.lesen(artikel(nutriscore = "unknown")).nutriscore)
        assertNull(Angabenleser.lesen(artikel(nutriscore = "z")).nutriscore)
        assertNull(Angabenleser.lesen(artikel()).nutriscore)
    }

    @Test
    fun `Doppelte Auszeichnungen erscheinen einmal`() {
        val angaben = Angabenleser.lesen(artikel(auszeichnungen = "Bio, Vegan, Bio,  , Vegan"))

        assertEquals(listOf("Bio", "Vegan"), angaben.auszeichnungen)
    }

    @Test
    fun `Ein Artikel mit nur einer Angabe gilt nicht als leer`() {
        assertFalse(Angabenleser.lesen(artikel(menge = "250 g")).istLeer)
        assertFalse(Angabenleser.lesen(artikel(zutaten = "Milch, Zucker")).istLeer)
    }

    private fun artikel(
        menge: String? = null,
        allergene: String? = null,
        spuren: String? = null,
        auszeichnungen: String? = null,
        naehrwerte: String? = null,
        nutriscore: String? = null,
        zutaten: String? = null,
    ) = ArtikelEintrag(
        id = "a1",
        name = "Testartikel",
        suchtext = "testartikel",
        marke = null,
        ean = null,
        artikelnummer = null,
        kategorieId = null,
        menge = menge,
        allergene = allergene,
        spuren = spuren,
        auszeichnungen = auszeichnungen,
        naehrwerte = naehrwerte,
        nutriscore = nutriscore,
        zutaten = zutaten,
        bildUrl = null,
        erstelltVon = "Import",
        erstelltAm = 0,
        geaendertAm = null,
    )
}
