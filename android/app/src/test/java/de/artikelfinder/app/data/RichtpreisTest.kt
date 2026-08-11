package de.artikelfinder.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.artikelfinder.app.data.local.ArtikelDatenbank
import de.artikelfinder.app.data.local.MerkpostenEintrag
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
 * Der Richtpreis kommt aus der Katalogdatei. Diese Tests decken die drei Wege ab, auf denen
 * er in der App landet oder eben nicht: frischer Aufbau, Nachtragen in eine bestehende
 * Datenbank und eine alte Katalogfassung ohne Preisspalten.
 */
@RunWith(RobolectricTestRunner::class)
class RichtpreisTest {

    private lateinit var datenbank: ArtikelDatenbank
    private lateinit var repository: ArtikelRepository

    @Before
    fun aufbauen() {
        datenbank = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ArtikelDatenbank::class.java,
        ).allowMainThreadQueries().build()

        repository = ArtikelRepository(datenbank)
    }

    @After
    fun abbauen() = datenbank.close()

    @Test
    fun `Katalogaufbau liest die Preisspalten mit ein`() = runTest {
        aufbauenMit(KATALOG_MIT_PREIS)

        val artikel = repository.suchen("Vollmilch").erfolg().single()
        val richtpreis = artikel.richtpreis

        assertNotNull(richtpreis)
        assertEquals(1.19, richtpreis!!.wert, 0.001)
        assertEquals(1.09, richtpreis.niedrigster!!, 0.001)
        assertEquals(1.29, richtpreis.hoechster!!, 0.001)
        assertEquals(3, richtpreis.anzahl)
        assertEquals("2026-06-30", richtpreis.stand)
        assertTrue(richtpreis.hatSpanne)
    }

    @Test
    fun `Katalog ohne Preisspalten bleibt lesbar`() = runTest {
        // Die alte Fassung der Datei hat fuenf Spalten. Sie darf den Aufbau nicht kosten.
        aufbauenMit(KATALOG_OHNE_PREIS)

        val artikel = repository.suchen("Vollmilch").erfolg().single()

        assertNull(artikel.richtpreis)
    }

    @Test
    fun `Ohne eigenen Preis steht der Richtpreis allein, mit eigenem daneben`() = runTest {
        aufbauenMit(KATALOG_MIT_PREIS)

        val vorher = repository.suchen("Vollmilch").erfolg().single()
        assertNull("Der Richtpreis ist kein eigener Preis", vorher.preis)
        assertNotNull(vorher.richtpreis)

        repository.preisErfassen(vorher.id, preis = 0.89)

        val nachher = repository.suchen("Vollmilch").erfolg().single()
        assertEquals(0.89, nachher.preis!!.preis, 0.001)
        assertEquals(1.19, nachher.richtpreis!!.wert, 0.001)
    }

    @Test
    fun `Neue Katalogfassung traegt Richtpreise nach, ohne eigene Daten zu verlieren`() = runTest {
        // Der Fall beim App-Update: die Datenbank steht samt selbst erfasster Preise, nur
        // der Katalog ist neuer. Ein Neuaufbau wuerde die eigene Arbeit wegraeumen.
        aufbauenMit(KATALOG_OHNE_PREIS)

        val artikel = repository.suchen("Vollmilch").erfolg().single()
        repository.preisErfassen(artikel.id, preis = 0.89)
        repository.standortErfassen(artikel.id, gang = "7")

        datenbank.merkpostenDao().schreiben(
            MerkpostenEintrag(Katalogaufbau.MERKPOSTEN_KATALOGSTAND, "1")
        )

        aufbauenMit(KATALOG_MIT_PREIS)

        val nachher = repository.suchen("Vollmilch").erfolg().single()
        assertEquals("Der Artikel wurde nicht neu angelegt", artikel.id, nachher.id)
        assertEquals(1.19, nachher.richtpreis!!.wert, 0.001)
        assertEquals(0.89, nachher.preis!!.preis, 0.001)
        assertEquals("7", nachher.standort!!.gang)
        assertEquals(
            Katalogaufbau.KATALOGSTAND,
            datenbank.merkpostenDao().lesen(Katalogaufbau.MERKPOSTEN_KATALOGSTAND),
        )
    }

    @Test
    fun `Unveraenderter Katalogstand loest kein Nachtragen aus`() = runTest {
        aufbauenMit(KATALOG_MIT_PREIS)

        // Zweiter Start mit derselben Fassung: der Katalog wird nicht noch einmal gelesen,
        // der Bestand bleibt wie er ist.
        val vorher = datenbank.artikelDao().anzahl()
        aufbauenMit(KATALOG_MIT_PREIS)

        assertEquals(vorher, datenbank.artikelDao().anzahl())
    }

    private suspend fun aufbauenMit(katalog: String) {
        val aufbau = Katalogaufbau(ApplicationProvider.getApplicationContext(), datenbank)
        aufbau.katalogQuelle = { katalog.byteInputStream() }
        aufbau.sicherstellen()

        val zustand = aufbau.zustand.value
        check(zustand is Aufbauzustand.Fertig) { "Katalogaufbau fehlgeschlagen: $zustand" }
    }

    private fun <T> Abruf<T>.erfolg(): T = when (this) {
        is Abruf.Erfolg -> wert
        is Abruf.Fehler -> throw AssertionError("Erwartet wurde ein Erfolg, war: $meldung")
    }

    private companion object {
        const val KATALOG_OHNE_PREIS =
            "ean\tname\tmarke\tkategorie\tbildUrl\n" +
                "4045317058067\tVollmilch 1 l\tK-Classic\tMilch\t\n"

        const val KATALOG_MIT_PREIS =
            "ean\tname\tmarke\tkategorie\tbildUrl\tpreis\tpreisMin\tpreisMax\tpreisAnzahl\tpreisStand\n" +
                "4045317058067\tVollmilch 1 l\tK-Classic\tMilch\t\t1.19\t1.09\t1.29\t3\t2026-06-30\n"
    }
}
