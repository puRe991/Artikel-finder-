package de.artikelfinder.app.data

import de.artikelfinder.app.data.local.ArtikelEintrag

/**
 * Liest die Angabenspalten der Datenbank in das Anzeigemodell.
 *
 * Aufbereitet wurden sie schon beim Import — Tags in deutsche Begriffe, Naehrwerte in eine
 * kompakte Zeile. Hier wird nur noch aufgeteilt. Die Trennung ist Absicht: die Regeln,
 * welche Allergene ueberhaupt genannt werden duerfen, gehoeren an eine Stelle, und das ist
 * der Importer.
 */
object Angabenleser {

    /** Reihenfolge und Beschriftung wie auf der Naehrwerttabelle der Packung. */
    private val NAEHRWERTE = listOf(
        "kcal" to ("Energie" to "kcal"),
        "fett" to ("Fett" to "g"),
        "gesfett" to ("davon gesättigte Fettsäuren" to "g"),
        "kh" to ("Kohlenhydrate" to "g"),
        "zucker" to ("davon Zucker" to "g"),
        "eiweiss" to ("Eiweiß" to "g"),
        "salz" to ("Salz" to "g"),
    )

    fun lesen(eintrag: ArtikelEintrag) = Produktangaben(
        menge = eintrag.menge?.takeIf { it.isNotBlank() },
        allergene = liste(eintrag.allergene),
        spuren = liste(eintrag.spuren),
        auszeichnungen = liste(eintrag.auszeichnungen),
        naehrwerte = naehrwerte(eintrag.naehrwerte),
        nutriscore = eintrag.nutriscore?.trim()?.lowercase()?.takeIf { it.length == 1 && it[0] in 'a'..'e' },
        zutaten = eintrag.zutaten?.takeIf { it.isNotBlank() },
    )

    private fun liste(roh: String?): List<String> =
        roh?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()

    /** `kcal=250;fett=12.5` — unbekannte Schluessel werden uebergangen. */
    private fun naehrwerte(roh: String?): List<Naehrwert> {
        if (roh.isNullOrBlank()) return emptyList()

        val werte = roh.split(';')
            .mapNotNull { teil ->
                val trenner = teil.indexOf('=')
                if (trenner <= 0) return@mapNotNull null
                teil.substring(0, trenner).trim() to teil.substring(trenner + 1).trim()
            }
            .toMap()

        return NAEHRWERTE.mapNotNull { (schluessel, beschriftung) ->
            val wert = werte[schluessel]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val (name, einheit) = beschriftung
            Naehrwert(name, "${wert.replace('.', ',')} $einheit")
        }
    }
}
