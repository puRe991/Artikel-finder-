package de.artikelfinder.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reiner Rechentest ohne Datenbank — die Bedarfslogik ist die eigentliche Neuerung und muss
 * ohne Android-Umgebung nachvollziehbar sein.
 */
class BedarfsrechnerTest {

    private val tag = 86_400_000L
    private val jetzt = 100L * tag

    private fun kauf(tagNr: Long, menge: Int = 1, preis: Double? = null) =
        Bewegung(Bewegungsart.KAUF, menge, preis, tagNr * tag)

    private fun kaufBei(zeitpunkt: Long, menge: Int = 1, preis: Double? = null) =
        Bewegung(Bewegungsart.KAUF, menge, preis, zeitpunkt)

    @Test
    fun `ohne Bewegungen ist alles leer`() {
        val bedarf = Bedarfsrechner.berechnen(emptyList(), jetzt)

        assertEquals(0, bedarf.aktuellerBestand)
        assertNull(bedarf.verbrauchProTag)
        assertFalse(bedarf.hatBedarfsschaetzung)
        assertEquals(0.0, bedarf.gesamtAusgaben, 0.0001)
    }

    @Test
    fun `ein einziger Kauf reicht nicht fuer eine Schaetzung`() {
        val bedarf = Bedarfsrechner.berechnen(listOf(kauf(0, menge = 2)), jetzt)

        assertEquals(2, bedarf.aktuellerBestand)
        assertNull(bedarf.verbrauchProTag)
        assertNull(bedarf.bedarfProMonat)
        assertNull(bedarf.reichweiteTage)
        assertEquals(1, bedarf.anzahlKaeufe)
    }

    @Test
    fun `regelmaessige Kaeufe ergeben Verbrauch und Monatsbedarf`() {
        // Alle 10 Tage eine Packung, vier Käufe: 3 Packungen in 30 Tagen verbraucht.
        val bewegungen = listOf(kauf(0), kauf(10), kauf(20), kauf(30))
        val bedarf = Bedarfsrechner.berechnen(bewegungen, jetzt)

        // (4 - 1) Packungen / 30 Tage = 0,1 pro Tag.
        assertEquals(0.1, bedarf.verbrauchProTag!!, 0.0001)
        assertEquals(0.7, bedarf.bedarfProWoche!!, 0.0001)
        assertEquals(3.0, bedarf.bedarfProMonat!!, 0.0001)
        assertEquals(4, bedarf.anzahlKaeufe)
        assertEquals(4, bedarf.aktuellerBestand)
    }

    @Test
    fun `Reichweite folgt aus Bestand und Verbrauch`() {
        // 0,1 pro Tag Verbrauch, Bestand 4 -> reicht 40 Tage.
        val bewegungen = listOf(kauf(0), kauf(10), kauf(20), kauf(30))
        val bedarf = Bedarfsrechner.berechnen(bewegungen, jetzt)

        assertEquals(40.0, bedarf.reichweiteTage!!, 0.0001)
        assertFalse(bedarf.nachkaufEmpfohlen)
    }

    @Test
    fun `niedriger Bestand empfiehlt den Nachkauf`() {
        // Hoher Verbrauch, danach fast alles aufgebraucht.
        val bewegungen = listOf(
            kauf(0, menge = 10),
            kauf(10, menge = 10),
            Bewegung(Bewegungsart.VERBRAUCH, -19, null, 15 * tag),
        )
        val bedarf = Bedarfsrechner.berechnen(bewegungen, jetzt)

        assertEquals(1, bedarf.aktuellerBestand)
        assertTrue(bedarf.reichweiteTage!! <= Bedarf.NACHKAUF_SCHWELLE_TAGE)
        assertTrue(bedarf.nachkaufEmpfohlen)
    }

    @Test
    fun `Kosten und Ausgaben nutzen den Stueckpreis`() {
        val bewegungen = listOf(
            kauf(0, menge = 1, preis = 1.50),
            kauf(10, menge = 1, preis = 1.50),
            kauf(20, menge = 1, preis = 1.60),
            kauf(30, menge = 1, preis = 1.60),
        )
        val bedarf = Bedarfsrechner.berechnen(bewegungen, jetzt)

        // 4 Käufe: 1,50 + 1,50 + 1,60 + 1,60 = 6,20 insgesamt.
        assertEquals(6.20, bedarf.gesamtAusgaben, 0.0001)
        assertEquals(1.60, bedarf.letzterStueckpreis!!, 0.0001)
        // Monatsbedarf 3 * letzter Stückpreis 1,60 = 4,80.
        assertEquals(4.80, bedarf.monatskosten!!, 0.0001)
        // Bestandswert: 4 Stück * 1,60.
        assertEquals(6.40, bedarf.bestandswert!!, 0.0001)
    }

    @Test
    fun `Kaeufe am selben Tag ergeben keine erfundene Rate`() {
        val bewegungen = listOf(kauf(5, menge = 2), kauf(5, menge = 3))
        val bedarf = Bedarfsrechner.berechnen(bewegungen, jetzt)

        assertEquals(5, bedarf.aktuellerBestand)
        assertNull(bedarf.verbrauchProTag)
        assertNull(bedarf.monatskosten)
    }

    @Test
    fun `zwei Scans desselben Einkaufs ergeben keine Bedarfsschaetzung`() {
        // Der reale Fehlerfall: dieselbe Packung zweimal kurz hintereinander gescannt.
        val start = 50L * tag
        val bewegungen = listOf(
            kaufBei(start, menge = 1, preis = 1.19),
            kaufBei(start + 11 * 60 * 1000, menge = 1, preis = 1.19), // 11 Minuten später
        )
        val bedarf = Bedarfsrechner.berechnen(bewegungen, jetzt)

        assertEquals(2, bedarf.aktuellerBestand)
        assertEquals(2, bedarf.anzahlKaeufe)      // zwei Scans
        assertEquals(1, bedarf.anzahlNachkaeufe)  // aber ein Einkauf
        assertNull(bedarf.verbrauchProTag)
        assertNull(bedarf.bedarfProMonat)
        assertNull(bedarf.monatskosten)
        assertNull(bedarf.reichweiteTage)
        assertFalse(bedarf.nachkaufEmpfohlen)
    }

    @Test
    fun `Batch-Scan mehrerer Packungen zaehlt als ein Nachkauf`() {
        val start = 50L * tag
        val bewegungen = listOf(
            kaufBei(start),
            kaufBei(start + 2_000),
            kaufBei(start + 5_000),
        )
        val bedarf = Bedarfsrechner.berechnen(bewegungen, jetzt)

        assertEquals(3, bedarf.aktuellerBestand)
        assertNull(bedarf.verbrauchProTag)
    }

    @Test
    fun `Nachkaeufe an verschiedenen Tagen ergeben eine sinnvolle Rate`() {
        val bewegungen = listOf(
            kaufBei(0, menge = 1),
            kaufBei(60_000, menge = 1),                 // selber Einkauf -> Nachkauf 1 = 2 Stück
            kaufBei(30 * tag, menge = 1),
            kaufBei(30 * tag + 60_000, menge = 1),      // Nachkauf 2 = 2 Stück, 30 Tage später
        )
        val bedarf = Bedarfsrechner.berechnen(bewegungen, jetzt)

        // 2 Nachkäufe: verbraucht = 4 - 2 = 2 in 30 Tagen -> rund 0,067/Tag -> 2/Monat.
        assertEquals(0.0667, bedarf.verbrauchProTag!!, 0.001)
        assertEquals(2.0, bedarf.bedarfProMonat!!, 0.01)
        assertEquals(4, bedarf.aktuellerBestand)
        assertEquals(4, bedarf.anzahlKaeufe)
        assertEquals(2, bedarf.anzahlNachkaeufe)
    }

    @Test
    fun `Verbrauchsbuchungen senken den Bestand aber nicht die gekaufte Menge`() {
        val bewegungen = listOf(
            kauf(0, menge = 5),
            kauf(20, menge = 5),
            Bewegung(Bewegungsart.VERBRAUCH, -3, null, 25 * tag),
        )
        val bedarf = Bedarfsrechner.berechnen(bewegungen, jetzt)

        assertEquals(7, bedarf.aktuellerBestand) // 5 + 5 - 3
        assertEquals(10, bedarf.gekaufteMenge)   // Verbrauch zählt nicht als Kauf
        // Verbraucht laut Nachkauf: (10 - 5) / 20 Tage = 0,25 pro Tag.
        assertEquals(0.25, bedarf.verbrauchProTag!!, 0.0001)
    }
}
