package de.artikelfinder.app.data.sicherung

import de.artikelfinder.app.data.Abruf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das Format ist reiner Text und braucht kein Android. Geprüft wird vor allem, dass ein
 * Umlauf nichts verliert: eine Sicherung, die beim Einlesen anders aussieht als beim
 * Schreiben, merkt man erst, wenn man sie braucht.
 */
class SicherungsformatTest {

    @Test
    fun `Umlauf erhaelt alle Felder`() {
        val original = beispiel()

        val gelesen = Sicherungsformat.lesen(Sicherungsformat.schreiben(original)).erfolg()

        assertEquals(original, gelesen)
    }

    @Test
    fun `Tabulatoren und Zeilenumbrueche im Freitext sprengen das Format nicht`() {
        val original = Sicherung(
            erstelltAm = 1_700_000_000_000,
            artikel = listOf(
                GesicherterArtikel(
                    id = "a1",
                    ean = null,
                    name = "Wurst\tmit Tabulator\nund Umbruch",
                    marke = "Pfad\\Marke",
                    artikelnummer = null,
                    kategorie = null,
                )
            ),
        )

        val text = Sicherungsformat.schreiben(original)
        val gelesen = Sicherungsformat.lesen(text).erfolg()

        // Eine Datensatzzeile je Artikel — der Umbruch im Namen darf keine zweite erzeugen.
        assertEquals(1, text.lines().count { it.startsWith("A\t") })
        assertEquals(original.artikel, gelesen.artikel)
    }

    @Test
    fun `Preise werden unabhaengig von der Spracheinstellung geschrieben`() {
        val text = Sicherungsformat.schreiben(beispiel())

        // Deutsches Dezimalkomma in der Datei würde beim Einlesen zu zwei Feldern führen.
        assertTrue("Preise gehören mit Punkt in die Datei", text.contains("1.49"))
        assertTrue(text.contains("1.29"))
    }

    @Test
    fun `Katalogartikel haengen an der EAN selbst angelegte an ihrer Id`() {
        val text = Sicherungsformat.schreiben(beispiel())

        assertTrue("Der Bezug über die EAN fehlt", text.contains("e:4337256123456"))
        assertTrue("Der Bezug über die Id fehlt", text.contains("i:a2"))
    }

    @Test
    fun `Eine fremde Datei wird abgelehnt`() {
        val ergebnis = Sicherungsformat.lesen("ean\tname\tmarke\n4337256123456\tMilch\tK-Classic")

        assertTrue("Eine Katalogdatei ist keine Sicherung", ergebnis is Abruf.Fehler)
    }

    @Test
    fun `Eine Datei aus einer neueren App wird abgelehnt statt halb gelesen`() {
        val text = Sicherungsformat.schreiben(beispiel())
            .replace("format\t1", "format\t99")

        val ergebnis = Sicherungsformat.lesen(text)

        assertTrue(ergebnis is Abruf.Fehler)
        assertTrue((ergebnis as Abruf.Fehler).meldung.contains("neueren Version"))
    }

    @Test
    fun `Beschaedigte Zeilen werden uebersprungen der Rest bleibt lesbar`() {
        val text = Sicherungsformat.schreiben(beispiel())
            .lines()
            .toMutableList()
            .apply { add(4, "P\tkaputt\tohne-weitere-felder") }
            .joinToString("\n")

        val gelesen = Sicherungsformat.lesen(text).erfolg()

        // Ein verstümmelter Datensatz darf nicht die ganze Sicherung wertlos machen.
        assertEquals(beispiel().preise, gelesen.preise)
        assertEquals(beispiel().standorte, gelesen.standorte)
    }

    @Test
    fun `Eine leere Sicherung bleibt eine leere Sicherung`() {
        val leer = Sicherung(erstelltAm = 1_700_000_000_000)

        val gelesen = Sicherungsformat.lesen(Sicherungsformat.schreiben(leer)).erfolg()

        assertTrue(gelesen.istLeer)
        assertEquals(leer.erstelltAm, gelesen.erstelltAm)
    }

    private fun beispiel() = Sicherung(
        erstelltAm = 1_700_000_000_000,
        artikel = listOf(
            GesicherterArtikel(
                id = "a2", ean = null, name = "Lose Ware vom Markt",
                marke = null, artikelnummer = "4711", kategorie = "Obst",
            )
        ),
        preise = listOf(
            GesicherterPreis(
                id = "p1", bezug = Artikelbezug.PerEan("4337256123456"), wert = 1.49,
                werbepreis = 1.29, werbepreisVon = null, werbepreisBis = 1_900_000_000_000,
                erfasstAm = 1_700_000_000_000, erfasstVon = "tobias",
            ),
            GesicherterPreis(
                id = "p2", bezug = Artikelbezug.PerId("a2"), wert = 2.99,
                werbepreis = null, werbepreisVon = null, werbepreisBis = null,
                erfasstAm = 1_700_000_001_000, erfasstVon = null,
            ),
        ),
        standorte = listOf(
            GesicherterStandort(
                id = "s1", bezug = Artikelbezug.PerEan("4337256123456"), gang = "7",
                regalBeschreibung = "unten links", kartenX = null, kartenY = null,
                erfasstAm = 1_700_000_000_000, erfasstVon = "tobias",
            )
        ),
        verlauf = listOf(
            GesicherterVerlauf(
                bezug = Artikelbezug.PerEan("4337256123456"), entitaet = "Preis",
                aenderungsart = "Angelegt", beschreibung = "Preis 1,49 -> 1,29 EUR",
                geaendertVon = "tobias", geaendertAm = 1_700_000_000_000,
            )
        ),
        merkposten = listOf(GesicherterMerkposten("aktionsende", "1900000000000")),
    )

    private fun <T> Abruf<T>.erfolg(): T = when (this) {
        is Abruf.Erfolg -> wert
        is Abruf.Fehler -> throw AssertionError("Erwartet wurde ein Erfolg, war: $meldung")
    }
}
