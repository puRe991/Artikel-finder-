package de.artikelfinder.app.data

import java.util.Locale

/**
 * Die bekannten Handelsketten, nach Kategorie geordnet — die Vorlage der Marktauswahl.
 *
 * Die Kategorie hängt an der Kette und nicht an der Datenbank: sie ist feststehendes Wissen
 * über den Handel, kein selbst erfasster Wert. Dadurch bleibt die Tabelle `markt` unverändert,
 * und ein Update, das eine Kette nachträgt, ordnet auch bereits angelegte Märkte neu ein.
 */
object Marktkatalog {

    const val SUPERMAERKTE = "Supermärkte & Discounter"
    const val BAUMAERKTE = "Baumärkte"
    const val ELEKTROMAERKTE = "Elektrofachmärkte"

    /** Auffangkategorie für selbst angelegte Märkte, deren Kette der Katalog nicht kennt. */
    const val WEITERE = "Weitere Märkte"

    /** Reihenfolge der Abschnitte in der Marktauswahl. */
    val REIHENFOLGE = listOf(SUPERMAERKTE, BAUMAERKTE, ELEKTROMAERKTE, WEITERE)

    /** Eine Handelskette und die Kategorie, unter der sie in der Auswahl steht. */
    data class Kette(val name: String, val kategorie: String)

    private val BAUMARKTKETTEN = listOf(
        "OBI",
        "Bauhaus",
        "Hornbach",
        "toom Baumarkt",
        "hagebaumarkt",
        "Hellweg",
        "Globus Baumarkt",
        "Sonderpreis Baumarkt",
        "Bauking",
        "Hammer",
        "Holzland",
        "Werkers Welt",
        "Raiffeisen-Markt",
        "BayWa Bau- & Gartenmarkt",
    )

    private val ELEKTROKETTEN = listOf(
        "MediaMarkt",
        "Saturn",
        "expert",
        "expert Klein",
        "Euronics",
        "EP: ElectronicPartner",
        "MEDIMAX",
        "Berlet",
        "Conrad Electronic",
        "Alternate",
        "Cyberport",
        "notebooksbilliger.de",
        "Mindfactory",
        "computeruniverse",
        "Reichelt Elektronik",
        "Pollin Electronic",
        "Coolblue",
    )

    /**
     * Ketten, die nur zur Einordnung bekannt sind. Sie werden nicht angelegt — der eigene
     * Lebensmittelmarkt steht bereits in der Datenbank und soll dort nicht doppelt erscheinen.
     */
    private val SUPERMARKTKETTEN = listOf(
        "Kaufland",
        "Lidl",
        "Aldi",
        "Aldi Süd",
        "Aldi Nord",
        "Edeka",
        "Rewe",
        "Penny",
        "Netto",
        "Netto Marken-Discount",
        "Norma",
        "Globus",
        "Famila",
        "tegut",
        "Marktkauf",
        "HIT",
        "nahkauf",
        "Combi",
        "Wasgau",
        "V-Markt",
        "real",
    )

    /**
     * Ketten, die die App beim Start anlegt, damit sie in der Auswahl auftauchen. Ein Markt
     * daraus bekommt erst dann Inhalt, wenn dort Preise oder Gänge erfasst werden.
     */
    val VORGESCHLAGENE_KETTEN: List<Kette> =
        BAUMARKTKETTEN.map { Kette(it, BAUMAERKTE) } + ELEKTROKETTEN.map { Kette(it, ELEKTROMAERKTE) }

    private val NACH_KETTE: Map<String, String> = buildMap {
        SUPERMARKTKETTEN.forEach { put(schluessel(it), SUPERMAERKTE) }
        VORGESCHLAGENE_KETTEN.forEach { put(schluessel(it.name), it.kategorie) }
    }

    /**
     * Die Kategorie einer Kette. Unbekannte Ketten landen unter [WEITERE]; "Kaufland Gießen"
     * zählt als Kaufland, damit ein selbst benannter Markt nicht aus seiner Kategorie fällt.
     */
    fun kategorieFuer(kette: String): String {
        val gesucht = schluessel(kette)

        return NACH_KETTE[gesucht]
            ?: NACH_KETTE.entries.firstOrNull { (name, _) -> gesucht.startsWith("$name ") }?.value
            ?: WEITERE
    }

    private fun schluessel(kette: String) = kette.trim().lowercase(Locale.GERMANY)
}
