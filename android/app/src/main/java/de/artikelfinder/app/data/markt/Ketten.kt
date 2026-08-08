package de.artikelfinder.app.data.markt

import de.artikelfinder.app.data.Suchtext

/**
 * Die Handelsketten und ihre Eigenmarken.
 *
 * Eine Eigenmarke steht nur in den Läden ihrer Kette. „ja!" im Kaufland zu suchen ist
 * deshalb keine Frage der Reihenfolge, sondern eine falsche Antwort vor dem Regal — der
 * Katalog aus den offenen Datenbanken kennt diesen Unterschied nicht und wirft alles
 * zusammen.
 *
 * **Zugeordnet wird nur, was sicher exklusiv ist.** Marken wie Alnatura, Rapunzel oder
 * dennree stehen bei mehreren Händlern im Regal und bleiben deshalb ohne Kette — sie sind
 * überall zu sehen. Im Zweifel lieber ein fremder Artikel zu viel als ein eigener zu wenig:
 * ein überflüssiger Treffer kostet einen Blick, ein fehlender einen Gang durch den Laden.
 *
 * Der Schlüssel ist die **normalisierte** Marke. Im Katalog steht „K-Classic" in sechs
 * Schreibweisen (K-Classic, K Classic, K-CLASSIC, K classic, K-classic, k classic) —
 * zusammen fast 3.000 Artikel. Über `Suchtext.normalisieren` werden daraus alle „k classic".
 */
object Ketten {

    data class Kette(val schluessel: String, val name: String)

    /**
     * Aldi Nord und Süd teilen sich fast alle Eigenmarken (Tandil, Ombia, Moser Roth,
     * Choceur …); nur bei der Molkerei gehen sie auseinander — Milsani im Süden, Milfina im
     * Norden. Die Trennung wäre für die Auswahl mehr Verwirrung als Nutzen, deshalb steht
     * hier ein Aldi mit beiden Markensätzen.
     */
    val ALLE: List<Kette> = listOf(
        Kette(KAUFLAND, "Kaufland"),
        Kette(LIDL, "Lidl"),
        Kette(ALDI, "Aldi"),
        Kette(REWE, "Rewe"),
        Kette(EDEKA, "Edeka"),
        Kette(PENNY, "Penny"),
        Kette(NETTO, "Netto Marken-Discount"),
        Kette(NORMA, "Norma"),
        Kette(DM, "dm-drogerie markt"),
        Kette(ROSSMANN, "Rossmann"),
        Kette(GLOBUS, "Globus"),
        Kette(MARKTKAUF, "Marktkauf"),
        Kette(TEGUT, "tegut"),
        Kette(SONSTIGE, "Anderer Markt"),
    )

    fun kette(schluessel: String): Kette? = ALLE.firstOrNull { it.schluessel == schluessel }

    /** Die Kette, zu der eine Marke exklusiv gehört — oder `null` für alle anderen. */
    fun ketteFuerMarke(marke: String?): String? {
        val schluessel = Suchtext.normalisieren(marke)
        return if (schluessel.isEmpty()) null else eigenmarken[schluessel]
    }

    /**
     * Wird als Merkposten mitgeschrieben. Wächst die Liste unten, genügt eine Erhöhung —
     * die App trägt die Zuordnung beim nächsten Start nach, ohne Schemawechsel.
     */
    const val ZUORDNUNG_VERSION = 1

    const val KAUFLAND = "kaufland"
    const val LIDL = "lidl"
    const val ALDI = "aldi"
    const val REWE = "rewe"
    const val EDEKA = "edeka"
    const val PENNY = "penny"
    const val NETTO = "netto"
    const val NORMA = "norma"
    const val DM = "dm"
    const val ROSSMANN = "rossmann"
    const val GLOBUS = "globus"
    const val MARKTKAUF = "marktkauf"
    const val TEGUT = "tegut"
    const val SONSTIGE = "sonstige"

    private val eigenmarken: Map<String, String> = buildMap {
        fun zuordnen(kette: String, vararg marken: String) {
            marken.forEach { marke ->
                val schluessel = Suchtext.normalisieren(marke)
                if (schluessel.isNotEmpty()) put(schluessel, kette)
            }
        }

        zuordnen(
            KAUFLAND,
            "Kaufland", "K-Classic", "K-Bio", "K-take it veggie", "K-Free", "K-Favourites",
            "K-to go", "K-Purland", "Purland", "Bevola", "exquisit",
        )

        zuordnen(
            LIDL,
            "Lidl", "Milbona", "Pilos", "Combino", "Freeway", "Saskia", "Solevita", "Cien",
            "W5", "Crownfield", "Alesto", "Sondey", "Dulano", "Chef Select", "Vitasia",
            "Bellarom", "Favorina", "Kania", "Baresa", "Eridanous", "Lupilu", "Freshona",
            "Milford", "Dulcesol",
        )

        zuordnen(
            ALDI,
            "Aldi", "Milsani", "Milfina", "Gut Bio", "GutBio", "Tandil", "Ombia",
            "Rio d'Oro", "Moser Roth", "Choceur", "Almare", "Almare Seafood",
            "Meine Metzgerei", "River", "Mamia", "Sweet Valley", "Trader Joe's", "Kokett",
        )

        zuordnen(
            REWE,
            "Rewe", "REWE Bio", "REWE Beste Wahl", "REWE Feine Welt", "REWE Regional", "ja!",
        )

        zuordnen(
            EDEKA,
            "Edeka", "EDEKA Bio", "EDEKA Selection", "Gut & Günstig", "GUT&GÜNSTIG",
        )

        zuordnen(PENNY, "Penny", "San Fabio", "Naturgut", "Mibell")

        zuordnen(NETTO, "Netto", "Netto Marken-Discount", "BioBio", "Gutes Land")

        zuordnen(NORMA, "Norma")

        zuordnen(
            DM,
            "dmBio", "Balea", "alverde", "Profissimo", "Denkmit", "Dontodent", "Ebelin",
            "Mivolis", "babylove", "SauBär", "Sanft&Sicher",
        )

        zuordnen(
            ROSSMANN,
            "Rossmann", "enerBiO", "Isana", "domol", "Alterra", "Sunozon", "Ideenwelt",
            "Facelle",
        )

        zuordnen(GLOBUS, "Globus")
        zuordnen(MARKTKAUF, "Marktkauf")
        zuordnen(TEGUT, "tegut", "tegut… bio")
    }
}
