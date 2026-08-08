package de.artikelfinder.app.data.sicherung

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.ArtikelRepository
import de.artikelfinder.app.data.Aufbauzustand
import de.artikelfinder.app.data.Katalogaufbau
import de.artikelfinder.app.data.local.ArtikelDatenbank
import de.artikelfinder.app.data.markt.Ketten
import de.artikelfinder.app.data.markt.Marktverwaltung
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Der Sinn einer Sicherung zeigt sich erst auf einem zweiten Gerät. Genau das wird hier
 * nachgestellt: zwei Installationen mit demselben Katalog, aber — wie im echten Betrieb —
 * unterschiedlichen Artikel-Ids. Eine Sicherung, die an den Ids hinge, wäre hier wertlos.
 */
@RunWith(RobolectricTestRunner::class)
class SicherungsdienstTest {

    private val geoeffnet = mutableListOf<ArtikelDatenbank>()

    @After
    fun abbauen() = geoeffnet.forEach { it.close() }

    @Test
    fun `Erfasste Preise finden auf einem zweiten Geraet ihren Artikel wieder`() = runTest {
        val altesGeraet = installation()
        val artikel = altesGeraet.repository.perEan(MILCH).erfolg()!!
        altesGeraet.repository.preisErfassen(artikel.artikel.id, preis = 1.49, werbepreis = 1.29)
        altesGeraet.repository.standortErfassen(artikel.artikel.id, gang = "7")

        val datei = Sicherungsformat.schreiben(altesGeraet.dienst.erstellen())

        val neuesGeraet = installation()
        val gleicherArtikel = neuesGeraet.repository.perEan(MILCH).erfolg()!!
        assertNotEquals(
            "Der Testaufbau taugt nur, wenn die Ids sich tatsächlich unterscheiden",
            artikel.artikel.id,
            gleicherArtikel.artikel.id,
        )

        val bericht = neuesGeraet.dienst.einspielen(Sicherungsformat.lesen(datei).erfolg())

        assertEquals(1, bericht.neuePreise)
        assertEquals(1, bericht.neueStandorte)
        assertEquals(0, bericht.ohneArtikel)

        val wiederhergestellt = neuesGeraet.repository.perEan(MILCH).erfolg()!!
        assertEquals(1.49, wiederhergestellt.artikel.preis!!.preis, 0.001)
        assertEquals(1.29, wiederhergestellt.artikel.preis!!.werbepreis!!, 0.001)
        assertEquals("7", wiederhergestellt.artikel.standort!!.gang)
    }

    @Test
    fun `Selbst angelegte Artikel kommen mit`() = runTest {
        val alt = installation()
        alt.repository.anlegen(name = "Lose Ware vom Wochenmarkt", preis = 2.99, gang = "1")

        val datei = Sicherungsformat.schreiben(alt.dienst.erstellen())

        val neu = installation()
        val bericht = neu.dienst.einspielen(Sicherungsformat.lesen(datei).erfolg())

        assertEquals(1, bericht.neueArtikel)
        assertEquals(1, bericht.neuePreise)

        val treffer = neu.repository.suchen("lose ware").erfolg()
        assertEquals(1, treffer.size)
        assertEquals(2.99, treffer.first().preis!!.preis, 0.001)
        assertEquals("1", treffer.first().standort!!.gang)
    }

    @Test
    fun `Zweimal einspielen aendert nichts`() = runTest {
        val alt = installation()
        val artikel = alt.repository.perEan(MILCH).erfolg()!!
        alt.repository.preisErfassen(artikel.artikel.id, 1.49)
        alt.repository.anlegen(name = "Eigener Artikel", preis = 3.49)

        val sicherung = Sicherungsformat.lesen(Sicherungsformat.schreiben(alt.dienst.erstellen())).erfolg()

        val neu = installation()
        neu.dienst.einspielen(sicherung)
        val zweiterLauf = neu.dienst.einspielen(sicherung)

        assertTrue(
            "Ein zweiter Lauf derselben Datei darf nichts hinzufügen",
            zweiterLauf.nichtsGeaendert,
        )
        assertEquals(1, neu.repository.suchen("eigener artikel").erfolg().size)
        assertEquals(1, neu.datenbank.preisDao().fuerArtikel(
            neu.repository.perEan(MILCH).erfolg()!!.artikel.id
        ).size)
    }

    @Test
    fun `Einspielen ergaenzt und loescht nicht`() = runTest {
        val alt = installation()
        alt.repository.preisErfassen(alt.repository.perEan(MILCH).erfolg()!!.artikel.id, 1.49)
        val datei = Sicherungsformat.schreiben(alt.dienst.erstellen())

        // Auf dem Zielgerät ist seit der Sicherung etwas anderes erfasst worden.
        val neu = installation()
        val butter = neu.repository.perEan(BUTTER).erfolg()!!
        neu.repository.preisErfassen(butter.artikel.id, 2.19)

        neu.dienst.einspielen(Sicherungsformat.lesen(datei).erfolg())

        assertEquals(2.19, neu.repository.perEan(BUTTER).erfolg()!!.artikel.preis!!.preis, 0.001)
        assertEquals(1.49, neu.repository.perEan(MILCH).erfolg()!!.artikel.preis!!.preis, 0.001)
    }

    @Test
    fun `Erfassungen zu unbekannten Artikeln werden gemeldet statt still verschluckt`() = runTest {
        val alt = installation()
        val artikel = alt.repository.perEan(MILCH).erfolg()!!
        alt.repository.preisErfassen(artikel.artikel.id, 1.49)
        val datei = Sicherungsformat.schreiben(alt.dienst.erstellen())

        // Das Zielgerät hat einen Katalog ohne diesen Artikel — etwa nach einem Katalogumbau.
        val neu = installation(katalog = KATALOG_OHNE_MILCH)
        val bericht = neu.dienst.einspielen(Sicherungsformat.lesen(datei).erfolg())

        assertEquals(0, bericht.neuePreise)
        assertEquals(1, bericht.ohneArtikel)
    }

    @Test
    fun `Der Katalog wandert nicht in die Sicherung`() = runTest {
        val geraet = installation()
        geraet.repository.preisErfassen(geraet.repository.perEan(MILCH).erfolg()!!.artikel.id, 1.49)

        val sicherung = geraet.dienst.erstellen()

        // Drei Katalogartikel stehen in der Datenbank, aber keiner davon in der Datei:
        // sie liegen ohnehin in der App.
        assertEquals(3, geraet.datenbank.artikelDao().anzahl())
        assertTrue(sicherung.artikel.isEmpty())
        assertEquals(1, sicherung.preise.size)
    }

    @Test
    fun `Der gemerkte Aktionszeitraum kommt mit`() = runTest {
        val alt = installation()
        val ende = 1_900_000_000_000
        alt.repository.preisErfassen(
            alt.repository.perEan(MILCH).erfolg()!!.artikel.id,
            preis = 2.00, werbepreis = 1.50, werbepreisBis = ende,
        )

        val datei = Sicherungsformat.schreiben(alt.dienst.erstellen())

        val neu = installation()
        neu.dienst.einspielen(Sicherungsformat.lesen(datei).erfolg())

        assertEquals(ende, neu.repository.letztesAktionsende())
    }

    @Test
    fun `Preise mehrerer Maerkte bleiben getrennt`() = runTest {
        val alt = installation()
        val artikel = alt.repository.perEan(MILCH).erfolg()!!.artikel.id
        alt.repository.preisErfassen(artikel, 1.49)
        alt.repository.standortErfassen(artikel, gang = "3")

        alt.maerkte.anlegen(Ketten.REWE, ort = "Marburg")
        alt.repository.preisErfassen(artikel, 1.79)
        alt.repository.standortErfassen(artikel, gang = "7")

        val datei = Sicherungsformat.schreiben(alt.dienst.erstellen())

        // Auf dem neuen Geraet existiert noch keiner der beiden Maerkte unter diesem Namen.
        val neu = installation(kette = Ketten.LIDL, ort = "Wetzlar")
        neu.dienst.einspielen(Sicherungsformat.lesen(datei).erfolg())

        val wieder = neu.datenbank.stammdatenDao().maerkte().associateBy { it.name }
        assertEquals(3, wieder.size)

        // Beide Erfassungen muessen wieder zu ihrem eigenen Markt gehoeren — verschmelzen
        // sie, sieht der Nutzer im Kaufland den Rewe-Preis.
        neu.maerkte.waehlen(wieder.getValue("Kaufland Gießen").id)
        assertEquals(1.49, neu.repository.perEan(MILCH).erfolg()!!.artikel.preis!!.preis, 0.001)
        assertEquals("3", neu.repository.perEan(MILCH).erfolg()!!.artikel.standort!!.gang)

        neu.maerkte.waehlen(wieder.getValue("Rewe Marburg").id)
        assertEquals(1.79, neu.repository.perEan(MILCH).erfolg()!!.artikel.preis!!.preis, 0.001)
        assertEquals("7", neu.repository.perEan(MILCH).erfolg()!!.artikel.standort!!.gang)
    }

    @Test
    fun `Ein bereits vorhandener Markt wird nicht doppelt angelegt`() = runTest {
        val alt = installation()
        alt.repository.preisErfassen(alt.repository.perEan(MILCH).erfolg()!!.artikel.id, 1.49)
        val datei = Sicherungsformat.schreiben(alt.dienst.erstellen())

        val neu = installation()
        neu.dienst.einspielen(Sicherungsformat.lesen(datei).erfolg())

        assertEquals(1, neu.datenbank.stammdatenDao().maerkte().size)
        assertEquals(1.49, neu.repository.perEan(MILCH).erfolg()!!.artikel.preis!!.preis, 0.001)
    }

    /** Eine frische Installation: eigene Datenbank, eigener Katalogaufbau, eigene Ids. */
    private suspend fun installation(
        katalog: String = KATALOG,
        kette: String = Ketten.KAUFLAND,
        ort: String? = "Gießen",
    ): Installation {
        val datenbank = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ArtikelDatenbank::class.java,
        ).allowMainThreadQueries().build()
        geoeffnet += datenbank

        val aufbau = Katalogaufbau(ApplicationProvider.getApplicationContext(), datenbank)
        aufbau.katalogQuelle = { katalog.byteInputStream() }
        aufbau.sicherstellen()
        check(aufbau.zustand.value is Aufbauzustand.Fertig) { "Katalogaufbau fehlgeschlagen" }

        val maerkte = Marktverwaltung(datenbank)
        maerkte.anlegen(kette, ort = ort)

        return Installation(
            datenbank,
            ArtikelRepository(datenbank, maerkte),
            Sicherungsdienst(datenbank, maerkte),
            maerkte,
        )
    }

    private data class Installation(
        val datenbank: ArtikelDatenbank,
        val repository: ArtikelRepository,
        val dienst: Sicherungsdienst,
        val maerkte: Marktverwaltung,
    )

    private companion object {
        const val MILCH = "4337256111111"
        const val BUTTER = "4337256222222"

        /** Die Tabulatoren stehen bewusst als Escape da — im Quelltext sind sie sonst
         *  nicht von Leerzeichen zu unterscheiden, und eine Zeile mit zu wenigen Feldern
         *  überspringt der Katalogaufbau stillschweigend. Das letzte Feld ist die Bild-URL. */
        private fun katalog(vararg zeilen: String) =
            (listOf("ean\tname\tmarke\tkategorie\tbildUrl") + zeilen).joinToString("\n")

        val KATALOG = katalog(
            "$MILCH\tFrische Vollmilch 1l\tK-Classic\tMilch\t",
            "$BUTTER\tDeutsche Markenbutter 250g\tK-Classic\tButter & Margarine\t",
            "4337256333333\tWeizenmehl Type 405\tK-Classic\tMehl & Backzutaten\t",
        )

        val KATALOG_OHNE_MILCH = katalog(
            "$BUTTER\tDeutsche Markenbutter 250g\tK-Classic\tButter & Margarine\t",
        )
    }

    private fun <T> Abruf<T>.erfolg(): T = when (this) {
        is Abruf.Erfolg -> wert
        is Abruf.Fehler -> throw AssertionError("Erwartet wurde ein Erfolg, war: $meldung")
    }
}
