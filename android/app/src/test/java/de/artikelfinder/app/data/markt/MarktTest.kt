package de.artikelfinder.app.data.markt

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.ArtikelRepository
import de.artikelfinder.app.data.Aufbauzustand
import de.artikelfinder.app.data.Katalogaufbau
import de.artikelfinder.app.data.local.ArtikelDatenbank
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Der Markt entscheidet über zwei Dinge: welche Preise und Gänge gelten, und welche
 * Eigenmarken überhaupt im Regal stehen können.
 */
@RunWith(RobolectricTestRunner::class)
class MarktTest {

    private lateinit var datenbank: ArtikelDatenbank
    private lateinit var maerkte: Marktverwaltung
    private lateinit var repository: ArtikelRepository

    @Before
    fun aufbauen() = runTest {
        datenbank = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ArtikelDatenbank::class.java,
        ).allowMainThreadQueries().build()

        maerkte = Marktverwaltung(datenbank)
        repository = ArtikelRepository(datenbank, maerkte)

        val aufbau = Katalogaufbau(ApplicationProvider.getApplicationContext(), datenbank)
        aufbau.katalogQuelle = { KATALOG.byteInputStream() }
        aufbau.sicherstellen()
        check(aufbau.zustand.value is Aufbauzustand.Fertig)
    }

    @After
    fun abbauen() = datenbank.close()

    // --- Markenzuordnung ---

    @Test
    fun `Alle Schreibweisen einer Eigenmarke landen bei derselben Kette`() {
        // Im echten Katalog stehen von K-Classic sechs Fassungen, zusammen rund 3.000
        // Artikel. Ohne Normalisierung wären fünf davon nicht zugeordnet.
        val schreibweisen = listOf(
            "K-Classic", "K Classic", "K-CLASSIC", "K classic", "K-classic", "k classic",
        )

        assertTrue(
            schreibweisen.all { Ketten.ketteFuerMarke(it) == Ketten.KAUFLAND },
            )
    }

    @Test
    fun `Herstellermarken gehoeren keiner Kette`() {
        // Die stehen überall und dürfen nie ausgeblendet werden.
        listOf("Dr. Oetker", "Milka", "Coca-Cola", "Alnatura", "Rapunzel", "Berchtesgadener Land")
            .forEach { assertNull("$it darf keiner Kette zugeordnet sein", Ketten.ketteFuerMarke(it)) }
    }

    @Test
    fun `Die Zuordnung landet in der Datenbank`() = runTest {
        val kaufland = datenbank.artikelDao().alleMarken()
            .count { Ketten.ketteFuerMarke(it) == Ketten.KAUFLAND }

        assertTrue("Der Testkatalog enthält Kaufland-Eigenmarken", kaufland > 0)
        assertEquals(2, datenbank.artikelDao().anzahlFremderEigenmarken(Ketten.KAUFLAND))
    }

    // --- Wirkung auf die Suche ---

    @Test
    fun `Im Kaufland stehen keine fremden Eigenmarken`() = runTest {
        maerkte.anlegen(Ketten.KAUFLAND)

        val namen = repository.suchen("milch").erfolg().map { it.marke }

        assertTrue("K-Classic gehört ins Kaufland", namen.contains("K-Classic"))
        assertTrue("Milbona ist Lidl", !namen.contains("Milbona"))
        assertTrue("ja! ist Rewe", !namen.contains("ja!"))
        assertTrue("Herstellermarken stehen überall", namen.contains("Weihenstephan"))
    }

    @Test
    fun `Derselbe Suchbegriff liefert im Lidl andere Eigenmarken`() = runTest {
        maerkte.anlegen(Ketten.LIDL)

        val namen = repository.suchen("milch").erfolg().map { it.marke }

        assertTrue(namen.contains("Milbona"))
        assertTrue(!namen.contains("K-Classic"))
        assertTrue(namen.contains("Weihenstephan"))
    }

    @Test
    fun `Der Schalter holt die fremden Eigenmarken zurueck`() = runTest {
        maerkte.anlegen(Ketten.KAUFLAND)

        val ohne = repository.suchen("milch").erfolg()
        val mit = repository.suchen("milch", fremdeEigenmarken = true).erfolg()

        assertTrue("Eine falsche Zuordnung darf nie einen Treffer kosten", mit.size > ohne.size)
        assertTrue(mit.map { it.marke }.contains("Milbona"))
    }

    @Test
    fun `Ohne bekannte Kette wird nichts ausgeblendet`() = runTest {
        // „Anderer Markt" hat keine Eigenmarken — dann alle auszublenden wäre absurd.
        maerkte.anlegen(Ketten.SONSTIGE)

        val namen = repository.suchen("milch").erfolg().map { it.marke }

        assertTrue(namen.contains("K-Classic"))
        assertTrue(namen.contains("Milbona"))
    }

    // --- Preise und Gänge je Markt ---

    @Test
    fun `Preise und Gaenge gelten je Markt`() = runTest {
        val kaufland = maerkte.anlegen(Ketten.KAUFLAND, ort = "Gießen")
        val artikel = repository.suchen("weihenstephan").erfolg().first()
        repository.preisErfassen(artikel.id, preis = 1.49)
        repository.standortErfassen(artikel.id, gang = "3")

        val rewe = maerkte.anlegen(Ketten.REWE, ort = "Gießen")
        assertNull("Im anderen Markt ist noch nichts erfasst", repository.holen(artikel.id).erfolg().artikel.preis)
        repository.preisErfassen(artikel.id, preis = 1.79)
        repository.standortErfassen(artikel.id, gang = "7")

        assertEquals(1.79, repository.holen(artikel.id).erfolg().artikel.preis!!.preis, 0.001)
        assertEquals(listOf("7"), repository.gaenge().erfolg().map { it.gang })

        maerkte.waehlen(kaufland.id)
        assertEquals(1.49, repository.holen(artikel.id).erfolg().artikel.preis!!.preis, 0.001)
        assertEquals(listOf("3"), repository.gaenge().erfolg().map { it.gang })

        assertEquals("Rewe Gießen", rewe.name)
    }

    @Test
    fun `Ein Marktwechsel zieht die Angebotsliste neu`() = runTest {
        val kaufland = maerkte.anlegen(Ketten.KAUFLAND)
        val artikel = repository.suchen("weihenstephan").erfolg().first()
        repository.preisErfassen(
            artikel.id, preis = 2.00, werbepreis = 1.50,
            werbepreisBis = System.currentTimeMillis() + 86_400_000,
        )
        assertEquals(1, repository.aktiveAngebote().first().size)

        maerkte.anlegen(Ketten.REWE)
        assertEquals("Das Angebot gilt nur im Kaufland", 0, repository.aktiveAngebote().first().size)

        maerkte.waehlen(kaufland.id)
        assertEquals(1, repository.aktiveAngebote().first().size)
    }

    @Test
    fun `Der gewaehlte Markt ueberlebt einen Neustart`() = runTest {
        maerkte.anlegen(Ketten.KAUFLAND, ort = "Gießen")
        maerkte.anlegen(Ketten.REWE, ort = "Marburg")

        // Wie nach einem Kaltstart: neue Verwaltung auf derselben Datenbank.
        val nachNeustart = Marktverwaltung(datenbank)
        nachNeustart.laden()

        assertEquals("Rewe Marburg", nachNeustart.aktuell.value?.name)
    }

    @Test
    fun `Ohne angelegten Markt fragt die App danach`() = runTest {
        maerkte.laden()

        assertNull(maerkte.aktuell.value)
        assertEquals(0, maerkte.aktuelleId())
    }

    @Test
    fun `Ein entfernter Markt gibt den Platz an den naechsten ab`() = runTest {
        val kaufland = maerkte.anlegen(Ketten.KAUFLAND)
        val rewe = maerkte.anlegen(Ketten.REWE)

        maerkte.entfernen(rewe.id)

        assertEquals(kaufland.id, maerkte.aktuell.value?.id)
        assertNotNull(maerkte.aktuell.value)
    }

    private companion object {
        private fun katalog(vararg zeilen: String) =
            (listOf("ean\tname\tmarke\tkategorie\tbildUrl") + zeilen).joinToString("\n")

        val KATALOG = katalog(
            "4337256111111\tFrische Vollmilch 1l\tK-Classic\tMilch\t",
            "4337256222222\tFrische Vollmilch 1l\tMilbona\tMilch\t",
            "4337256333333\tFrische Vollmilch 1l\tja!\tMilch\t",
            "4337256444444\tFrische Vollmilch 1l\tWeihenstephan\tMilch\t",
            "4337256555555\tButter 250g\tK Classic\tButter & Margarine\t",
        )
    }

    private fun <T> Abruf<T>.erfolg(): T = when (this) {
        is Abruf.Erfolg -> wert
        is Abruf.Fehler -> throw AssertionError("Erwartet wurde ein Erfolg, war: $meldung")
    }
}
