package de.artikelfinder.app.data

import java.text.Normalizer
import java.util.Locale

/**
 * Normalisiert Suchbegriffe und Artikelnamen auf eine gemeinsame Form.
 *
 * Portiert aus `ArtikelFinder.Shared.Suchtext` — solange der Katalog auf dem Server
 * erzeugt und auf dem Gerät durchsucht wird, müssen beide Seiten identisch normalisieren.
 * Die Tests auf beiden Seiten prüfen dieselben Beispiele.
 */
object Suchtext {

    /** Schreibweise, die Deutsche beim Tippen ohne Umlaut-Taste erwarten. */
    private val umlaute = listOf(
        Triple('ä', "ae", "a"),
        Triple('ö', "oe", "o"),
        Triple('ü', "ue", "u"),
        Triple('ß', "ss", "ss"),
    )

    /** Kanonische Form eines Suchbegriffs: klein, ohne Satzzeichen, Umlaute ausgeschrieben. */
    fun normalisieren(eingabe: String?): String = aufbereiten(eingabe, kurzform = false)

    /**
     * Der Text, der in der Datenbank landet. Enthält zusätzlich die Kurzform der Umlaute,
     * damit sowohl "mueller" als auch "muller" den Artikel "Müller" findet.
     */
    fun fuerIndex(eingabe: String?): String {
        val lang = aufbereiten(eingabe, kurzform = false)
        val kurz = aufbereiten(eingabe, kurzform = true)
        return if (lang == kurz) lang else "$lang $kurz"
    }

    private fun aufbereiten(eingabe: String?, kurzform: Boolean): String {
        if (eingabe.isNullOrBlank()) return ""

        val ersetzt = StringBuilder(eingabe.length + 4)
        for (zeichen in eingabe.lowercase(Locale.GERMANY)) {
            val treffer = umlaute.firstOrNull { it.first == zeichen }
            if (treffer != null) {
                ersetzt.append(if (kurzform) treffer.third else treffer.second)
            } else {
                ersetzt.append(zeichen)
            }
        }

        // Verbleibende Akzente (é, ñ, …) auf den Basisbuchstaben zurückführen.
        val zerlegt = Normalizer.normalize(ersetzt, Normalizer.Form.NFD)
        val ergebnis = StringBuilder(zerlegt.length)
        var letzterWarTrenner = false

        for (zeichen in zerlegt) {
            when {
                Character.getType(zeichen) == Character.NON_SPACING_MARK.toInt() -> Unit

                zeichen.isLetterOrDigit() -> {
                    ergebnis.append(zeichen)
                    letzterWarTrenner = false
                }

                !letzterWarTrenner && ergebnis.isNotEmpty() -> {
                    ergebnis.append(' ')
                    letzterWarTrenner = true
                }
            }
        }

        return Normalizer.normalize(ergebnis.toString().trimEnd(), Normalizer.Form.NFC)
    }
}

/** Hilfsfunktionen für Barcodes, portiert aus `ArtikelFinder.Shared.Ean`. */
object Ean {

    /** Bringt eine EAN auf Normalform: nur Ziffern, führende Nullen bleiben erhalten. */
    fun normalisieren(eingabe: String?): String? {
        if (eingabe.isNullOrBlank()) return null
        val ziffern = eingabe.filter(Char::isDigit)
        return if (ziffern.length in 8..14) ziffern else null
    }

    /** Prüft die GTIN-Prüfziffer (EAN-8, UPC-A, EAN-13, GTIN-14). */
    fun pruefzifferKorrekt(ean: String?): Boolean {
        val normalisiert = normalisieren(ean) ?: return false
        if (normalisiert.length !in setOf(8, 12, 13, 14)) return false

        var summe = 0
        var gewicht = 3
        for (i in normalisiert.length - 2 downTo 0) {
            summe += (normalisiert[i] - '0') * gewicht
            gewicht = if (gewicht == 3) 1 else 3
        }

        return (10 - summe % 10) % 10 == normalisiert.last() - '0'
    }
}
