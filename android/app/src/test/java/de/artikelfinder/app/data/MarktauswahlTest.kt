package de.artikelfinder.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.artikelfinder.app.data.local.ArtikelDatenbank
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Die Marktauswahl gegen echtes SQLite: welche Märkte die App mitbringt, wie sie gruppiert
 * sind — und vor allem, dass Preise und Gänge am gewählten Markt hängen und nicht
 * durcheinanderlaufen.
 */
@RunWith(RobolectricTestRunner::class)
class MarktauswahlTest {

    private lateinit var datenbank: ArtikelDatenbank
    private lateinit var repository: ArtikelRepository

    @Before
    fun aufbauen() = runTest {
        datenbank = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ArtikelDatenbank::class.java,
        ).allowMainThreadQueries().build()

        repository = ArtikelRepository(datenbank)
        katalogaufbau().sicherstellen()
    }

    @After
    fun abbauen() = datenbank.close()

    @Test
    fun `Die Auswahl bringt die bekannten Baumaerkte mit`() = runTest {
        val baumaerkte = maerkteDerKategorie(Marktkatalog.BAUMAERKTE)

        assertTrue(
            "Fehlende Baumärkte: $baumaerkte",
            baumaerkte.containsAll(listOf("OBI", "Bauhaus", "Hornbach", "toom Baumarkt", "hagebaumarkt")),
        )
    }

    @Test
    fun `Die Auswahl bringt die bekannten Elektrofachmaerkte mit`() = runTest {
        val elektro = maerkteDerKategorie(Marktkatalog.ELEKTROMAERKTE)

        assertTrue(
            "Fehlende Elektrofachmärkte: $elektro",
            elektro.containsAll(listOf("Saturn", "MediaMarkt", "expert", "expert Klein", "Alternate")),
        )
    }

    @Test
    fun `Die Abschnitte stehen in der Reihenfolge des Katalogs`() = runTest {
        assertEquals(
            listOf(Marktkatalog.SUPERMAERKTE, Marktkatalog.BAUMAERKTE, Marktkatalog.ELEKTROMAERKTE),
            repository.maerkte().erfolg().map { it.kategorie },
        )
    }

    @Test
    fun `Der eigene Markt wird ueber seine Kette eingeordnet`() = runTest {
        // "Kaufland Gießen" heißt anders als die Kette — trotzdem gehört er zu den Supermärkten.
        assertEquals(listOf("Kaufland Gießen"), maerkteDerKategorie(Marktkatalog.SUPERMAERKTE))
    }

    @Test
    fun `Ein zweiter Start legt die Ketten nicht doppelt an`() = runTest {
        val vorher = datenbank.stammdatenDao().anzahlMaerkte()

        katalogaufbau().sicherstellen()

        assertEquals(vorher, datenbank.stammdatenDao().anzahlMaerkte())
    }

    @Test
    fun `Preise und Gaenge gelten je Markt`() = runTest {
        val artikel = repository.anlegen(name = "Akkuschrauber", preis = 89.90, gang = "12").erfolg()
        val id = artikel.artikel.id

        repository.marktWaehlen(markt("OBI").id)

        assertNull("Der Preis des Supermarkts gilt im Baumarkt nicht", preisIm(id))
        assertTrue(repository.gaenge().erfolg().isEmpty())

        repository.preisErfassen(id, 79.90)
        assertEquals(79.90, preisIm(id)!!, 0.001)

        repository.marktWaehlen(Katalogaufbau.STANDARD_MARKT)

        assertEquals(89.90, preisIm(id)!!, 0.001)
        assertEquals(listOf("12"), repository.gaenge().erfolg().map { it.gang })
    }

    @Test
    fun `Der gewaehlte Markt ueberlebt den Neustart`() = runTest {
        val saturn = markt("Saturn")
        repository.marktWaehlen(saturn.id)

        // Ein frisches Repository steht für den nächsten Start der App.
        val nachNeustart = ArtikelRepository(datenbank).markt().erfolg()

        assertEquals(saturn.id, nachNeustart.id)
        assertEquals(Marktkatalog.ELEKTROMAERKTE, nachNeustart.kategorie)
    }

    @Test
    fun `Ein unbekannter Markt aendert die Wahl nicht`() = runTest {
        repository.marktWaehlen(markt("Saturn").id)

        val unbekannt = repository.marktWaehlen(999_999)

        assertTrue(unbekannt is Abruf.Fehler)
        assertEquals("Saturn", repository.markt().erfolg().name)
    }

    private suspend fun maerkteDerKategorie(kategorie: String): List<String> =
        repository.maerkte().erfolg()
            .firstOrNull { it.kategorie == kategorie }
            ?.maerkte
            ?.map { it.name }
            .orEmpty()

    private suspend fun markt(name: String): Markt =
        repository.maerkte().erfolg().flatMap { it.maerkte }.first { it.name == name }

    private suspend fun preisIm(artikelId: String): Double? =
        repository.holen(artikelId).erfolg().artikel.preis?.preis

    /**
     * Der echte Katalog mit 19.000 Artikeln spielt hier keine Rolle — eine leere Katalogdatei
     * hält die Tests schnell. Die Stammdaten legt der Aufbau trotzdem an.
     */
    private fun katalogaufbau() =
        Katalogaufbau(ApplicationProvider.getApplicationContext(), datenbank).apply {
            katalogQuelle = { "ean\tname\tmarke\tkategorie\tbild\n".byteInputStream() }
        }

    private fun <T> Abruf<T>.erfolg(): T = when (this) {
        is Abruf.Erfolg -> wert
        is Abruf.Fehler -> throw AssertionError("Erwartet wurde ein Erfolg, war: $meldung")
    }
}
