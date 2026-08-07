package de.artikelfinder.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * Läuft gegen eine echte SQLite-Datenbank im Speicher. Die interessanten Fehler stecken in
 * den Abfragen selbst — "jüngster Preis je Markt", LIKE-Verhalten, Kategoriezweige — und
 * die zeigen sich nur beim echten Datenbanktreiber.
 */
@RunWith(RobolectricTestRunner::class)
class ArtikelRepositoryTest {

    private lateinit var datenbank: ArtikelDatenbank
    private lateinit var repository: ArtikelRepository

    @Before
    fun aufbauen() = runTest {
        datenbank = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ArtikelDatenbank::class.java,
        ).allowMainThreadQueries().build()

        repository = ArtikelRepository(datenbank)

        // Stammdaten wie beim echten Start anlegen.
        val aufbau = katalogaufbau()
        aufbau.sicherstellen()
        val zustand = aufbau.zustand.value
        check(zustand is Aufbauzustand.Fertig) { "Katalogaufbau fehlgeschlagen: " + zustand }
    }

    @After
    fun abbauen() = datenbank.close()

    @Test
    fun `Katalog wird beim ersten Start aus den Assets eingelesen`() = runTest {
        // Prüft die echte mitgelieferte Datei, nicht eine Attrappe.
        val anzahl = datenbank.artikelDao().anzahl()
        assertTrue("Erwartet wurden ueber 15.000 Artikel, waren $anzahl", anzahl > 15_000)
    }

    @Test
    fun `Suche findet Katalogartikel ohne Umlaute`() = runTest {
        val mitUmlaut = repository.suchen("käse").erfolg()
        val ohneUmlaut = repository.suchen("kase").erfolg()
        val ausgeschrieben = repository.suchen("kaese").erfolg()

        assertTrue(mitUmlaut.isNotEmpty())
        assertEquals(mitUmlaut.size, ohneUmlaut.size)
        assertEquals(mitUmlaut.size, ausgeschrieben.size)
    }

    @Test
    fun `Suche verknuepft mehrere Begriffe mit UND`() = runTest {
        val einer = repository.suchen("bio").erfolg()
        val zwei = repository.suchen("bio milch").erfolg()

        assertTrue("Zwei Begriffe duerfen nicht mehr treffen als einer", zwei.size <= einer.size)
        assertTrue(zwei.all { treffer ->
            val text = Suchtext.normalisieren("${treffer.name} ${treffer.marke.orEmpty()}")
            text.contains("bio") && text.contains("milch")
        })
    }

    @Test
    fun `Beobachtete Suche zeigt einen nachtraeglich erfassten Preis`() = runTest {
        // Der Fall aus der Hauptansicht: gesucht wird vor der Erfassung, angezeigt danach.
        val artikel = repository.anlegen(name = "Preisnachtrag", ean = FREIE_EAN).erfolg()
        assertNull(repository.suchenLive("Preisnachtrag").first().single().preis)

        repository.preisErfassen(artikel.artikel.id, 2.49)

        val beobachtet = repository.suchenLive("Preisnachtrag").first()
        assertEquals(2.49, beobachtet.single().preis!!.preis, 0.001)
        // Beide Wege müssen dasselbe liefern, sonst zeigt die Liste je nach Aufruf anderes an.
        assertEquals(repository.suchen("Preisnachtrag").erfolg().map { it.id }, beobachtet.map { it.id })
    }

    @Test
    fun `Ein erfasster Preis haelt den Katalogartikel unter den zuletzt bearbeiteten`() = runTest {
        val katalogartikel = datenbank.artikelDao()
            .suchen(0, "%", "%", "%", emptyList(), 0, 0, 0, 0, 1, 1, 0)
            .first().artikel

        // Alle Katalogartikel teilen sich denselben Importzeitpunkt, und der liegt vor jeder
        // Neuanlage. Nach ihm allein sortiert rutschte ein eben erfasster Artikel hinter die
        // 50 Neuanlagen — also aus der Liste heraus.
        repeat(60) { nummer -> repository.anlegen(name = "Neuanlage $nummer") }
        repository.preisErfassen(katalogartikel.id, 1.09)

        val liste = repository.zuletztBearbeitet().first()

        assertEquals(50, liste.size)
        assertTrue(
            "Der eben erfasste Artikel fehlt in der Liste",
            liste.any { it.id == katalogartikel.id },
        )
        assertEquals(1.09, liste.first { it.id == katalogartikel.id }.preis!!.preis, 0.001)
    }

    @Test
    fun `Anlegen mit Preis und Standort erzeugt alles in einem Schritt`() = runTest {
        val detail = repository.anlegen(
            name = "Testartikel",
            ean = FREIE_EAN,
            preis = 1.49,
            gang = "7",
            erfasstVon = "tobias",
        ).erfolg()

        assertEquals(1, detail.preise.size)
        assertEquals(1, detail.standorte.size)
        assertEquals("7", detail.standorte.first().gang)
        assertEquals("Nutzer", detail.erstelltVon)
    }

    @Test
    fun `Doppelte EAN wird abgelehnt`() = runTest {
        repository.anlegen(name = "Erster", ean = FREIE_EAN)
        val zweiter = repository.anlegen(name = "Zweiter", ean = FREIE_EAN)

        assertTrue(zweiter is Abruf.Fehler)
    }

    @Test
    fun `EAN eines Katalogartikels kollidiert ebenfalls`() = runTest {
        // Der Katalog bringt bereits 15.000 EANs mit — eine davon darf nicht doppelt gehen.
        val vorhandene = datenbank.artikelDao()
            .suchen(0, "%", "%", "%", emptyList(), 0, 0, 0, 0, 1, 1, 0)
            .first().artikel.ean!!

        val ergebnis = repository.anlegen(name = "Dublette", ean = vorhandene)
        assertTrue(ergebnis is Abruf.Fehler)
    }

    @Test
    fun `Juengster Preis gewinnt`() = runTest {
        val artikel = repository.anlegen(name = "Vollmilch", preis = 1.49).erfolg()
        repository.preisErfassen(artikel.artikel.id, 1.59)

        val neu = repository.holen(artikel.artikel.id).erfolg()

        assertEquals(1.59, neu.artikel.preis!!.preis, 0.001)
        assertEquals("Historie bleibt erhalten", 2, neu.preise.size)
    }

    @Test
    fun `Werbepreis gilt nur bis zum Ablaufdatum`() = runTest {
        val artikel = repository.anlegen(name = "Angebotsartikel").erfolg()

        repository.preisErfassen(
            artikel.artikel.id, preis = 1.49, werbepreis = 1.29,
            werbepreisBis = System.currentTimeMillis() + 86_400_000,
        )
        assertEquals(1.29, repository.holen(artikel.artikel.id).erfolg().artikel.preis!!.gueltigerPreis, 0.001)

        repository.preisErfassen(
            artikel.artikel.id, preis = 1.49, werbepreis = 1.29,
            werbepreisBis = System.currentTimeMillis() - 1,
        )
        assertEquals(1.49, repository.holen(artikel.artikel.id).erfolg().artikel.preis!!.gueltigerPreis, 0.001)
    }

    @Test
    fun `Werbepreis ueber Normalpreis wird abgelehnt`() = runTest {
        val artikel = repository.anlegen(name = "Artikel").erfolg()
        val ergebnis = repository.preisErfassen(artikel.artikel.id, preis = 1.00, werbepreis = 2.00)

        assertTrue(ergebnis is Abruf.Fehler)
    }

    @Test
    fun `Umraeumen entfernt den Artikel aus dem alten Gang`() = runTest {
        val artikel = repository.anlegen(name = "Vollmilch", gang = "7").erfolg()
        repository.standortErfassen(artikel.artikel.id, gang = "3")

        assertTrue(repository.artikelImGang("7").erfolg().isEmpty())
        assertEquals(1, repository.artikelImGang("3").erfolg().size)
        assertEquals(listOf("3"), repository.gaenge().erfolg().map { it.gang })
    }

    @Test
    fun `Gaenge werden numerisch sortiert`() = runTest {
        repository.anlegen(name = "A", gang = "2")
        repository.anlegen(name = "B", gang = "10")
        repository.anlegen(name = "C", gang = "1")

        assertEquals(listOf("1", "2", "10"), repository.gaenge().erfolg().map { it.gang })
    }

    @Test
    fun `Verlauf protokolliert wer was wann geaendert hat`() = runTest {
        val artikel = repository.anlegen(name = "Vollmilch", preis = 1.49, erfasstVon = "tobias").erfolg()
        repository.preisErfassen(artikel.artikel.id, 1.59, erfasstVon = "tobias")

        val verlauf = repository.verlauf(artikel.artikel.id).erfolg()

        assertEquals(3, verlauf.size) // Artikel + Erstpreis + neuer Preis
        assertEquals("Preis 1,49 -> 1,59 EUR", verlauf.first().beschreibung)
        assertTrue(verlauf.all { it.geaendertVon == "tobias" })
    }

    @Test
    fun `Angebotsliste enthaelt nur laufende Aktionen`() = runTest {
        val laufend = repository.anlegen(name = "Laufendes Angebot").erfolg()
        val abgelaufen = repository.anlegen(name = "Abgelaufenes Angebot").erfolg()
        val ohneAktion = repository.anlegen(name = "Normalpreis").erfolg()

        repository.preisErfassen(
            laufend.artikel.id, 2.00, werbepreis = 1.50,
            werbepreisBis = System.currentTimeMillis() + 2 * 86_400_000,
        )
        repository.preisErfassen(
            abgelaufen.artikel.id, 2.00, werbepreis = 1.50,
            werbepreisBis = System.currentTimeMillis() - 1,
        )
        repository.preisErfassen(ohneAktion.artikel.id, 2.00)

        val angebote = repository.aktiveAngebote().first()

        assertEquals(listOf("Laufendes Angebot"), angebote.map { it.name })
    }

    @Test
    fun `Angebote werden nach Ablauf sortiert`() = runTest {
        val spaet = repository.anlegen(name = "Laeuft spaet ab").erfolg()
        val frueh = repository.anlegen(name = "Laeuft frueh ab").erfolg()

        repository.preisErfassen(
            spaet.artikel.id, 2.00, werbepreis = 1.50,
            werbepreisBis = System.currentTimeMillis() + 5 * 86_400_000,
        )
        repository.preisErfassen(
            frueh.artikel.id, 2.00, werbepreis = 1.50,
            werbepreisBis = System.currentTimeMillis() + 86_400_000,
        )

        // Was zuerst ablaeuft, gehoert nach oben — danach richtet sich der Einkauf.
        assertEquals(
            listOf("Laeuft frueh ab", "Laeuft spaet ab"),
            repository.aktiveAngebote().first().map { it.name },
        )
    }

    @Test
    fun `Aktionsende wird fuer die naechste Eingabe gemerkt`() = runTest {
        val artikel = repository.anlegen(name = "Prospektartikel").erfolg()
        val ende = System.currentTimeMillis() + 3 * 86_400_000

        repository.preisErfassen(artikel.artikel.id, 2.00, werbepreis = 1.50, werbepreisBis = ende)

        // Beim naechsten Angebot desselben Prospekts soll das Datum vorbelegt sein.
        assertEquals(ende, repository.letztesAktionsende())
    }

    @Test
    fun `Preis ohne Werbepreis ueberschreibt das gemerkte Aktionsende nicht`() = runTest {
        val a = repository.anlegen(name = "Mit Aktion").erfolg()
        val ende = System.currentTimeMillis() + 3 * 86_400_000
        repository.preisErfassen(a.artikel.id, 2.00, werbepreis = 1.50, werbepreisBis = ende)

        val b = repository.anlegen(name = "Ohne Aktion").erfolg()
        repository.preisErfassen(b.artikel.id, 3.00)

        assertEquals(ende, repository.letztesAktionsende())
    }

    @Test
    fun `Kategoriefilter schliesst Unterkategorien ein`() = runTest {
        val kategorien = repository.kategorien().erfolg()
        val molkerei = kategorien.first { it.name == "Molkereiprodukte" }
        val milch = kategorien.first { it.name == "Milch" }

        repository.anlegen(name = "Testmilch", kategorieId = milch.id)

        val treffer = repository.suchen("testmilch", kategorieId = molkerei.id).erfolg()
        assertEquals(1, treffer.size)
    }

    @Test
    fun `EAN-Lookup findet Artikel unabhaengig von der Schreibweise`() = runTest {
        repository.anlegen(name = "Vollmilch", ean = FREIE_EAN)

        assertNotNull(repository.perEan("9999-999-999994").erfolg())
        assertNull(repository.perEan("4000000000000").erfolg())
    }

    @Test
    fun `Loeschen raeumt Preise Standorte und Verlauf mit ab`() = runTest {
        val artikel = repository.anlegen(name = "Weg damit", preis = 1.49, gang = "7").erfolg()
        val id = artikel.artikel.id

        repository.loeschen(id)

        assertTrue(repository.holen(id) is Abruf.Fehler)
        assertTrue(datenbank.preisDao().fuerArtikel(id).isEmpty())
        assertTrue(datenbank.standortDao().fuerArtikel(id).isEmpty())
        assertTrue(datenbank.verlaufDao().fuerArtikel(id).isEmpty())
    }

    @Test
    fun `Zweiter Start liest den Katalog nicht erneut ein`() = runTest {
        val vorher = datenbank.artikelDao().anzahl()
        katalogaufbau().sicherstellen()

        assertEquals(vorher, datenbank.artikelDao().anzahl())
    }

    private companion object {
        /** Nicht im ausgelieferten Katalog enthalten — sonst kollidieren die Tests mit echten Daten. */
        const val FREIE_EAN = "9999999999994"
    }

    /**
     * Bewusst ueber den echten Asset-Manager statt ueber den Quellbaum: der Build-Prozess
     * veraendert Assets (er entpackt .gz und schneidet die Endung ab), und genau dieser
     * Unterschied hat die App schon einmal beim ersten Start scheitern lassen.
     */
    private fun katalogaufbau() =
        Katalogaufbau(ApplicationProvider.getApplicationContext(), datenbank)

    private fun <T> Abruf<T>.erfolg(): T = when (this) {
        is Abruf.Erfolg -> wert
        is Abruf.Fehler -> throw AssertionError("Erwartet wurde ein Erfolg, war: $meldung")
    }
}
