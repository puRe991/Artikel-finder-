package de.artikelfinder.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.artikelfinder.app.data.local.ArtikelDatenbank
import de.artikelfinder.app.data.markt.Ketten
import de.artikelfinder.app.data.markt.Marktverwaltung
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Prüft die Kette von der Katalogdatei bis ins Anzeigemodell.
 *
 * Die Angaben werden im Importer aufbereitet und in der App nur noch gelesen — zwischen
 * beiden liegen sieben Spalten einer Textdatei. Genau dort geht so etwas verloren, und
 * gemerkt hätte man es erst vor dem Regal.
 */
@RunWith(RobolectricTestRunner::class)
class KatalogangabenTest {

    private lateinit var datenbank: ArtikelDatenbank
    private lateinit var repository: ArtikelRepository

    @Before
    fun aufbauen() = runTest {
        datenbank = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ArtikelDatenbank::class.java,
        ).allowMainThreadQueries().build()

        val maerkte = Marktverwaltung(datenbank)
        repository = ArtikelRepository(datenbank, maerkte)

        val aufbau = Katalogaufbau(ApplicationProvider.getApplicationContext(), datenbank)
        aufbau.katalogQuelle = { KATALOG.byteInputStream() }
        aufbau.sicherstellen()
        check(aufbau.zustand.value is Aufbauzustand.Fertig)

        maerkte.anlegen(Ketten.KAUFLAND)
    }

    @After
    fun abbauen() = datenbank.close()

    @Test
    fun `Die Angaben aus der Katalogdatei kommen im Anzeigemodell an`() = runTest {
        val angaben = repository.perEan(SCHOKOLADE).erfolg()!!.artikel.angaben

        assertEquals("100 g", angaben.menge)
        assertEquals(listOf("Milch", "Schalenfrüchte", "Soja"), angaben.allergene)
        assertEquals(listOf("Eier", "Erdnüsse"), angaben.spuren)
        assertEquals(listOf("Bio", "Fairtrade"), angaben.auszeichnungen)
        assertEquals("e", angaben.nutriscore)
        assertTrue(angaben.zutaten!!.startsWith("Kakaomasse"))
        assertEquals("534 kcal", angaben.naehrwerte.first().wert)
    }

    @Test
    fun `Ein Artikel ohne Angaben behauptet nichts`() = runTest {
        val angaben = repository.perEan(WASSER).erfolg()!!.artikel.angaben

        assertTrue(angaben.allergene.isEmpty())
        assertFalse(
            "Ohne Daten darf die App keine Allergenauskunft vortäuschen",
            angaben.hatAllergeninfo,
        )
        assertNull(angaben.zutaten)
        assertEquals("1,5 l", angaben.menge)
    }

    @Test
    fun `Auszeichnungen sind ueber die Suche zu finden`() = runTest {
        // „Bio" steht weder im Namen noch in der Marke — nur im Siegel.
        val treffer = repository.suchen("bio schokolade").erfolg()

        assertEquals(1, treffer.size)
        assertEquals(SCHOKOLADE, treffer.first().ean)
    }

    @Test
    fun `Eine Katalogdatei ohne die neuen Spalten bleibt lesbar`() = runTest {
        // So sah der Katalog vor der Anreicherung aus: fünf Spalten.
        val alteDatenbank = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ArtikelDatenbank::class.java,
        ).allowMainThreadQueries().build()

        try {
            val aufbau = Katalogaufbau(ApplicationProvider.getApplicationContext(), alteDatenbank)
            aufbau.katalogQuelle = { ALTER_KATALOG.byteInputStream() }
            aufbau.sicherstellen()

            check(aufbau.zustand.value is Aufbauzustand.Fertig) {
                "Ein älterer Katalog darf den Aufbau nicht scheitern lassen"
            }
            assertEquals(1, alteDatenbank.artikelDao().anzahl())

            val eintrag = alteDatenbank.artikelDao().roh(
                alteDatenbank.artikelDao().idPerEan(WASSER)!!
            )!!
            assertNull(eintrag.allergene)
            assertNull(eintrag.menge)
        } finally {
            alteDatenbank.close()
        }
    }

    private companion object {
        const val SCHOKOLADE = "4337256111111"
        const val WASSER = "4337256222222"

        private const val KOPF = "ean\tname\tmarke\tkategorie\tbildUrl\tmenge\tallergene" +
            "\tspuren\tauszeichnungen\tnaehrwerte\tnutriscore\tzutaten"

        val KATALOG = listOf(
            KOPF,
            listOf(
                SCHOKOLADE, "Zartbitter Schokolade", "K-Classic", "Schokolade", "",
                "100 g", "Milch, Schalenfrüchte, Soja", "Eier, Erdnüsse", "Bio, Fairtrade",
                "kcal=534;fett=29.7;zucker=47.5;salz=0.02", "e",
                "Kakaomasse, Zucker, Kakaobutter, Emulgator (Sojalecithin)",
            ).joinToString("\t"),
            listOf(
                WASSER, "Natürliches Mineralwasser", "K-Classic", "Wasser", "",
                "1,5 l", "", "", "", "", "", "",
            ).joinToString("\t"),
        ).joinToString("\n")

        val ALTER_KATALOG = listOf(
            "ean\tname\tmarke\tkategorie\tbildUrl",
            listOf(WASSER, "Natürliches Mineralwasser", "K-Classic", "Wasser", "").joinToString("\t"),
        ).joinToString("\n")
    }

    private fun <T> Abruf<T>.erfolg(): T = when (this) {
        is Abruf.Erfolg -> wert
        is Abruf.Fehler -> throw AssertionError("Erwartet wurde ein Erfolg, war: $meldung")
    }
}
