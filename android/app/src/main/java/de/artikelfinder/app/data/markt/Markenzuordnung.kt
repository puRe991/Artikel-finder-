package de.artikelfinder.app.data.markt

import de.artikelfinder.app.data.Suchtext
import de.artikelfinder.app.data.local.ArtikelDatenbank
import de.artikelfinder.app.data.local.Merkposten
import de.artikelfinder.app.data.local.MerkpostenEintrag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trägt zu jedem Artikel die Kette nach, in der es ihn exklusiv gibt.
 *
 * Läuft beim Start und merkt sich, mit welcher Fassung der Markenliste sie zuletzt dran
 * war. Wächst die Liste in [Ketten], genügt eine höhere `ZUORDNUNG_VERSION` — die App
 * trägt beim nächsten Start nach, ohne dass sich das Schema ändert.
 *
 * Die Zuordnung setzt an der normalisierten Marke an. Im Katalog steht dieselbe Eigenmarke
 * in bis zu sechs Schreibweisen; welche zusammengehören, weiß erst `Suchtext`, und SQLite
 * kann das nicht. Deshalb werden die Schreibweisen hier gelesen, in Kotlin gruppiert und
 * je Kette in einem Rutsch gesetzt.
 */
@Singleton
class Markenzuordnung @Inject constructor(private val datenbank: ArtikelDatenbank) {

    suspend fun nachtragenWennNoetig() {
        val merkposten = datenbank.merkpostenDao()
        val erledigt = merkposten.lesen(Merkposten.MARKENZUORDNUNG)?.toIntOrNull() ?: 0
        if (erledigt >= Ketten.ZUORDNUNG_VERSION) return

        nachtragen()
        merkposten.schreiben(MerkpostenEintrag(Merkposten.MARKENZUORDNUNG, Ketten.ZUORDNUNG_VERSION.toString()))
    }

    /** Setzt die Zuordnung neu — auch dort, wo bisher eine falsche stand. */
    suspend fun nachtragen(): Int {
        val artikelDao = datenbank.artikelDao()

        val nachKette = artikelDao.alleMarken()
            .groupBy { Ketten.ketteFuerMarke(it) }
            .filterKeys { it != null }

        artikelDao.eigenmarkenLeeren()

        for ((kette, schreibweisen) in nachKette) {
            schreibweisen.chunked(SQL_PARAMETERGRENZE).forEach {
                artikelDao.eigenmarkeSetzen(kette!!, it)
            }
        }

        return nachKette.values.sumOf { it.size }
    }

    private companion object {
        /** SQLite nimmt nicht beliebig viele Platzhalter in einem IN(...) entgegen. */
        const val SQL_PARAMETERGRENZE = 500
    }
}

/** Nur für Tests und Diagnose: die Schreibweisen, die auf dieselbe Marke fallen. */
internal fun schreibweisenGruppieren(marken: List<String>): Map<String, List<String>> =
    marken.groupBy { Suchtext.normalisieren(it) }
