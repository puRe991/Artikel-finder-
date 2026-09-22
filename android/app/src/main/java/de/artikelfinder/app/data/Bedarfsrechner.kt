package de.artikelfinder.app.data

/**
 * Was für die Bedarfsrechnung von einer Bestandsbewegung übrig bleibt. Bewusst losgelöst
 * von der Room-Entität, damit die Rechnung ohne Datenbank getestet werden kann.
 */
data class Bewegung(
    val art: Bewegungsart,
    /** Vorzeichenbehaftet: Kauf ist positiv, Verbrauch negativ. */
    val menge: Int,
    val stueckpreis: Double?,
    val zeitpunkt: Long,
)

enum class Bewegungsart {
    /** Selbst gekauft und eingescannt — erhöht den Bestand. */
    KAUF,

    /** Aufgebraucht — senkt den Bestand. */
    VERBRAUCH,

    /** Bestand von Hand richtiggestellt. */
    KORREKTUR;

    companion object {
        fun ausText(text: String): Bewegungsart =
            entries.firstOrNull { it.name == text } ?: KORREKTUR
    }
}

/**
 * Das Ergebnis der Bedarfsrechnung für einen Artikel.
 *
 * Alle Bedarfsangaben sind `null`, solange zu wenig Daten vorliegen (weniger als zwei Käufe
 * oder kein zeitlicher Abstand). Lieber keine Zahl als eine erfundene.
 */
data class Bedarf(
    val aktuellerBestand: Int,
    /** Geschätzter Verbrauch pro Tag, aus dem Nachkauf-Rhythmus abgeleitet. */
    val verbrauchProTag: Double?,
    val bedarfProWoche: Double?,
    val bedarfProMonat: Double?,
    /** Wie lange der aktuelle Bestand beim bisherigen Verbrauch noch reicht (Tage). */
    val reichweiteTage: Double?,
    /** Zuletzt gezahlter bzw. bekannter Stückpreis. */
    val letzterStueckpreis: Double?,
    /** Voraussichtliche Kosten pro Monat = Monatsbedarf × Stückpreis. */
    val monatskosten: Double?,
    /** Was bisher insgesamt für den Artikel ausgegeben wurde. */
    val gesamtAusgaben: Double,
    /** Wie oft der Artikel erfasst wurde (einzelne Scans/Buchungen). */
    val anzahlKaeufe: Int,
    /** Wie viele getrennte Nachkäufe daraus wurden (Käufe desselben Einkaufs zählen als einer). */
    val anzahlNachkaeufe: Int,
    val gekaufteMenge: Int,
    val ersterKauf: Long?,
    val letzterKauf: Long?,
) {
    /** Wert des aktuellen Bestands zum letzten bekannten Stückpreis. */
    val bestandswert: Double?
        get() = letzterStueckpreis?.let { it * aktuellerBestand }

    /** Bald leer — Zeit, an den Nachkauf zu denken. */
    val nachkaufEmpfohlen: Boolean
        get() = reichweiteTage != null && reichweiteTage <= NACHKAUF_SCHWELLE_TAGE

    /** Genug Bewegungen, um überhaupt etwas über den Bedarf sagen zu können. */
    val hatBedarfsschaetzung: Boolean
        get() = verbrauchProTag != null

    companion object {
        const val NACHKAUF_SCHWELLE_TAGE = 5.0

        val leer = Bedarf(
            aktuellerBestand = 0,
            verbrauchProTag = null,
            bedarfProWoche = null,
            bedarfProMonat = null,
            reichweiteTage = null,
            letzterStueckpreis = null,
            monatskosten = null,
            gesamtAusgaben = 0.0,
            anzahlKaeufe = 0,
            anzahlNachkaeufe = 0,
            gekaufteMenge = 0,
            ersterKauf = null,
            letzterKauf = null,
        )
    }
}

/**
 * Leitet aus den erfassten Käufen ab, wie viel von einem Artikel gebraucht wird.
 *
 * Grundgedanke: Alles, was vor dem letzten Nachkauf gekauft wurde, gilt inzwischen als
 * verbraucht — sonst wäre nicht nachgekauft worden. Diese Menge verteilt auf die Zeit vom
 * ersten bis zum letzten Kauf ergibt den Verbrauch pro Tag. Der Rhythmus, in dem jemand
 * nachkauft, ist damit das Maß für den Bedarf; einzelne Verbrauchsbuchungen sind dafür
 * nicht nötig.
 */
object Bedarfsrechner {

    private const val TAG_MS = 86_400_000.0
    private const val TAGE_PRO_MONAT = 30.0

    /**
     * Käufe, die weniger als so weit auseinanderliegen, gehören zum selben Einkauf — etwa
     * zwei Packungen aus einem Einkauf oder das Einräumen der Tüte, bei dem nacheinander
     * gescannt wird. Sie zählen als ein Nachkauf, nicht als Verbrauchszyklus, sonst schnellt
     * der geschätzte Bedarf ins Absurde.
     */
    private const val EINKAUF_FENSTER_MS = 12L * 60 * 60 * 1000

    /**
     * @param bewertungspreis Preis, mit dem Bestand und Bedarf in Euro bewertet werden — der
     *   vom Nutzer gepflegte Artikelpreis. Ist er `null`, greift der zuletzt gezahlte
     *   Kaufpreis, damit die reine Logik ohne Preisquelle auskommt.
     */
    fun berechnen(
        bewegungen: List<Bewegung>,
        jetzt: Long = System.currentTimeMillis(),
        bewertungspreis: Double? = null,
    ): Bedarf {
        if (bewegungen.isEmpty() && bewertungspreis == null) return Bedarf.leer

        val aktuellerBestand = bewegungen.sumOf { it.menge }
        val kaeufe = bewegungen
            .filter { it.art == Bewegungsart.KAUF && it.menge > 0 }
            .sortedBy { it.zeitpunkt }

        val gekaufteMenge = kaeufe.sumOf { it.menge }
        val gesamtAusgaben = kaeufe.sumOf { (it.stueckpreis ?: 0.0) * it.menge }
        val letzterKaufpreis = kaeufe.lastOrNull { it.stueckpreis != null }?.stueckpreis
        // Zur Bewertung zählt der gepflegte Artikelpreis; ohne ihn der zuletzt gezahlte.
        val stueckpreis = bewertungspreis ?: letzterKaufpreis

        // Zeitnahe Käufe zu einem Nachkauf zusammenfassen — das ist das Maß für den Rhythmus.
        val nachkaeufe = zuNachkaeufen(kaeufe)
        val verbrauchProTag = verbrauchProTag(nachkaeufe)
        val bedarfProWoche = verbrauchProTag?.let { it * 7 }
        val bedarfProMonat = verbrauchProTag?.let { it * TAGE_PRO_MONAT }

        val reichweiteTage = verbrauchProTag
            ?.takeIf { it > 0 }
            ?.let { aktuellerBestand.coerceAtLeast(0) / it }

        val monatskosten = if (bedarfProMonat != null && stueckpreis != null) {
            bedarfProMonat * stueckpreis
        } else {
            null
        }

        return Bedarf(
            aktuellerBestand = aktuellerBestand,
            verbrauchProTag = verbrauchProTag,
            bedarfProWoche = bedarfProWoche,
            bedarfProMonat = bedarfProMonat,
            reichweiteTage = reichweiteTage,
            letzterStueckpreis = stueckpreis,
            monatskosten = monatskosten,
            gesamtAusgaben = gesamtAusgaben,
            anzahlKaeufe = kaeufe.size,
            anzahlNachkaeufe = nachkaeufe.size,
            gekaufteMenge = gekaufteMenge,
            ersterKauf = kaeufe.firstOrNull()?.zeitpunkt,
            letzterKauf = kaeufe.lastOrNull()?.zeitpunkt,
        )
    }

    /**
     * Verbrauch pro Tag aus dem Nachkauf-Rhythmus. Käufe desselben Einkaufs werden zuvor zu
     * einem Nachkauf zusammengefasst; es braucht mindestens zwei solche Nachkäufe mit
     * zeitlichem Abstand. Die zuletzt gekaufte Charge zählt als noch vorhanden und geht
     * deshalb nicht in den bereits verbrauchten Teil ein.
     */
    private fun verbrauchProTag(nachkaeufe: List<Nachkauf>): Double? {
        if (nachkaeufe.size < 2) return null

        val spanneTage = (nachkaeufe.last().zeitpunkt - nachkaeufe.first().zeitpunkt) / TAG_MS
        if (spanneTage <= 0) return null

        val verbraucht = (nachkaeufe.sumOf { it.menge } - nachkaeufe.last().menge).toDouble()
        if (verbraucht <= 0) return null

        return verbraucht / spanneTage
    }

    /**
     * Fasst zeitnahe Käufe zu einem Nachkauf zusammen. Ein neuer Kauf gehört zum laufenden
     * Nachkauf, solange er innerhalb des Einkaufsfensters auf den vorigen Scan folgt; sonst
     * beginnt ein neuer Nachkauf.
     */
    private fun zuNachkaeufen(kaeufe: List<Bewegung>): List<Nachkauf> {
        val nachkaeufe = mutableListOf<Nachkauf>()
        for (kauf in kaeufe) {
            val letzter = nachkaeufe.lastOrNull()
            if (letzter != null && kauf.zeitpunkt - letzter.zeitpunkt <= EINKAUF_FENSTER_MS) {
                nachkaeufe[nachkaeufe.lastIndex] = Nachkauf(kauf.zeitpunkt, letzter.menge + kauf.menge)
            } else {
                nachkaeufe += Nachkauf(kauf.zeitpunkt, kauf.menge)
            }
        }
        return nachkaeufe
    }

    /** Ein zusammengefasster Nachkauf: Zeitpunkt des letzten Scans und Gesamtmenge. */
    private data class Nachkauf(val zeitpunkt: Long, val menge: Int)
}
