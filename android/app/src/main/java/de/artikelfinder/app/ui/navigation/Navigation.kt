package de.artikelfinder.app.ui.navigation

import android.net.Uri

/**
 * Ziele der App. Die Routen sind bewusst als Konstanten mit Hilfsfunktionen gebündelt,
 * damit die Argumente an genau einer Stelle kodiert werden.
 */
object Ziele {
    const val SUCHE = "suche"
    const val SCAN = "scan"
    const val GAENGE = "gaenge"
    const val ANGEBOTE = "angebote"
    const val SICHERUNG = "sicherung"
    const val MAERKTE = "maerkte"

    const val DETAIL = "artikel/{artikelId}"
    fun detail(artikelId: String) = "artikel/$artikelId"

    const val VERLAUF = "artikel/{artikelId}/verlauf"
    fun verlauf(artikelId: String) = "artikel/$artikelId/verlauf"

    /** Bearbeitungsformular. Ohne `artikelId` legt es einen neuen Artikel an. */
    const val BEARBEITEN = "bearbeiten?artikelId={artikelId}&ean={ean}"
    fun bearbeiten(artikelId: String? = null, ean: String? = null): String {
        val parameter = buildList {
            artikelId?.let { add("artikelId=${Uri.encode(it)}") }
            ean?.let { add("ean=${Uri.encode(it)}") }
        }
        return if (parameter.isEmpty()) "bearbeiten" else "bearbeiten?${parameter.joinToString("&")}"
    }

    const val GANG_ARTIKEL = "gaenge/{gang}"
    fun gangArtikel(gang: String) = "gaenge/${Uri.encode(gang)}"

    const val ARG_ARTIKEL_ID = "artikelId"
    const val ARG_EAN = "ean"
    const val ARG_GANG = "gang"
}
