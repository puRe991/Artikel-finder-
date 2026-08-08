package de.artikelfinder.app.data.sicherung

import de.artikelfinder.app.data.Abruf

/**
 * Das Dateiformat der Sicherung: eine Zeile je Datensatz, Felder durch Tabulator getrennt,
 * der erste Buchstabe sagt, worum es sich handelt.
 *
 * Gesichert wird nur, was der Nutzer selbst erfasst hat. Der Artikelkatalog bleibt außen
 * vor — er liegt in der App und ist jederzeit wiederherstellbar. Das hält die Datei klein
 * genug, um sie zu verschicken, und macht sie lesbar: wer sie öffnet, sieht seine Preise.
 *
 * Der Bezug auf einen Artikel läuft über die EAN, nicht über die Artikel-Id. Der
 * Katalogaufbau vergibt bei jeder Installation neue Ids — eine Sicherung, die daran hängt,
 * wäre auf einem zweiten Gerät wertlos. Selbst angelegte Artikel ohne EAN behalten
 * stattdessen ihre Id und werden beim Einspielen damit wieder angelegt; dadurch bleibt auch
 * das mehrfache Einspielen derselben Datei folgenlos.
 */
object Sicherungsformat {

    const val FORMATVERSION = 1

    private const val TRENNER = '\t'

    /** Erstes Feld jeder Zeile. */
    private const val ARTIKEL = "A"
    private const val PREIS = "P"
    private const val STANDORT = "S"
    private const val VERLAUF = "V"
    private const val MERKPOSTEN = "M"

    fun schreiben(sicherung: Sicherung): String = buildString {
        appendLine("# Artikel-Finder — selbst erfasste Preise, Standorte und Notizen.")
        appendLine("# Der Artikelkatalog steht in der App und wird hier nicht mitgeschrieben.")
        appendLine("# Einspielen ergänzt, es löscht nichts.")
        zeile(this, "format", FORMATVERSION.toString())
        zeile(this, "erstellt", sicherung.erstelltAm.toString())

        sicherung.artikel.forEach {
            zeile(this, ARTIKEL, it.id, it.ean, it.name, it.marke, it.artikelnummer, it.kategorie)
        }
        sicherung.preise.forEach {
            zeile(
                this, PREIS, it.id, it.bezug.kodiert(), it.wert.toString(),
                it.werbepreis?.toString(), it.werbepreisVon?.toString(),
                it.werbepreisBis?.toString(), it.erfasstAm.toString(), it.erfasstVon,
            )
        }
        sicherung.standorte.forEach {
            zeile(
                this, STANDORT, it.id, it.bezug.kodiert(), it.gang, it.regalBeschreibung,
                it.kartenX?.toString(), it.kartenY?.toString(),
                it.erfasstAm.toString(), it.erfasstVon,
            )
        }
        sicherung.verlauf.forEach {
            zeile(
                this, VERLAUF, it.bezug.kodiert(), it.entitaet, it.aenderungsart,
                it.beschreibung, it.geaendertVon, it.geaendertAm.toString(),
            )
        }
        sicherung.merkposten.forEach { zeile(this, MERKPOSTEN, it.schluessel, it.wert) }
    }

    fun lesen(inhalt: String): Abruf<Sicherung> {
        var formatversion: Int? = null
        var erstelltAm = 0L

        val artikel = mutableListOf<GesicherterArtikel>()
        val preise = mutableListOf<GesicherterPreis>()
        val standorte = mutableListOf<GesicherterStandort>()
        val verlauf = mutableListOf<GesicherterVerlauf>()
        val merkposten = mutableListOf<GesicherterMerkposten>()

        inhalt.lineSequence().forEach { rohzeile ->
            if (rohzeile.isBlank() || rohzeile.startsWith("#")) return@forEach

            val felder = rohzeile.split(TRENNER).map { it.entschaerft() }

            when (felder[0]) {
                "format" -> formatversion = felder.getOrNull(1)?.toIntOrNull()
                "erstellt" -> erstelltAm = felder.getOrNull(1)?.toLongOrNull() ?: 0L

                ARTIKEL -> {
                    felder.pruefen(7) ?: return@forEach
                    artikel += GesicherterArtikel(
                        id = felder[1], ean = felder.leerAlsNull(2),
                        name = felder[3], marke = felder.leerAlsNull(4),
                        artikelnummer = felder.leerAlsNull(5), kategorie = felder.leerAlsNull(6),
                    )
                }

                PREIS -> {
                    felder.pruefen(9) ?: return@forEach
                    val bezug = felder[2].alsBezug() ?: return@forEach
                    val wert = felder[3].toDoubleOrNull() ?: return@forEach
                    preise += GesicherterPreis(
                        id = felder[1], bezug = bezug, wert = wert,
                        werbepreis = felder.leerAlsNull(4)?.toDoubleOrNull(),
                        werbepreisVon = felder.leerAlsNull(5)?.toLongOrNull(),
                        werbepreisBis = felder.leerAlsNull(6)?.toLongOrNull(),
                        erfasstAm = felder[7].toLongOrNull() ?: 0L,
                        erfasstVon = felder.leerAlsNull(8),
                    )
                }

                STANDORT -> {
                    felder.pruefen(9) ?: return@forEach
                    val bezug = felder[2].alsBezug() ?: return@forEach
                    standorte += GesicherterStandort(
                        id = felder[1], bezug = bezug, gang = felder[3],
                        regalBeschreibung = felder.leerAlsNull(4),
                        kartenX = felder.leerAlsNull(5)?.toFloatOrNull(),
                        kartenY = felder.leerAlsNull(6)?.toFloatOrNull(),
                        erfasstAm = felder[7].toLongOrNull() ?: 0L,
                        erfasstVon = felder.leerAlsNull(8),
                    )
                }

                VERLAUF -> {
                    felder.pruefen(7) ?: return@forEach
                    val bezug = felder[1].alsBezug() ?: return@forEach
                    verlauf += GesicherterVerlauf(
                        bezug = bezug, entitaet = felder[2], aenderungsart = felder[3],
                        beschreibung = felder[4], geaendertVon = felder.leerAlsNull(5),
                        geaendertAm = felder[6].toLongOrNull() ?: 0L,
                    )
                }

                MERKPOSTEN -> {
                    felder.pruefen(3) ?: return@forEach
                    merkposten += GesicherterMerkposten(felder[1], felder[2])
                }
            }
        }

        return when {
            formatversion == null -> Abruf.Fehler(
                "Das ist keine Sicherung des Artikel-Finders — die Kopfzeile fehlt."
            )

            formatversion > FORMATVERSION -> Abruf.Fehler(
                "Die Datei stammt aus einer neueren Version der App (Format $formatversion, " +
                    "diese App kennt $FORMATVERSION). Aktualisiere die App und versuche es erneut."
            )

            else -> Abruf.Erfolg(
                Sicherung(erstelltAm, artikel, preise, standorte, verlauf, merkposten)
            )
        }
    }

    private fun zeile(ziel: StringBuilder, vararg felder: String?) {
        ziel.appendLine(felder.joinToString(TRENNER.toString()) { it.orEmpty().geschuetzt() })
    }

    /**
     * Namen und Regalbeschreibungen sind Freitext und dürfen das Format nicht sprengen.
     * Umgekehrt soll ein Backslash im Namen ein Backslash bleiben.
     */
    private fun String.geschuetzt(): String = this
        .replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun String.entschaerft(): String {
        if (!contains('\\')) return this

        val ergebnis = StringBuilder(length)
        var i = 0
        while (i < length) {
            val zeichen = this[i]
            if (zeichen != '\\' || i == lastIndex) {
                ergebnis.append(zeichen)
                i++
                continue
            }
            when (val naechstes = this[i + 1]) {
                't' -> ergebnis.append('\t')
                'n' -> ergebnis.append('\n')
                'r' -> ergebnis.append('\r')
                '\\' -> ergebnis.append('\\')
                else -> ergebnis.append(zeichen).append(naechstes)
            }
            i += 2
        }
        return ergebnis.toString()
    }

    /** Zu kurze Zeilen werden übersprungen statt die ganze Datei zu verwerfen. */
    private fun List<String>.pruefen(erwartet: Int): Unit? =
        if (size >= erwartet) Unit else null

    private fun List<String>.leerAlsNull(index: Int): String? =
        getOrNull(index)?.takeIf { it.isNotEmpty() }
}

/**
 * Worauf sich ein gesicherter Preis oder Standort bezieht. Die EAN ist über Geräte hinweg
 * stabil, die Artikel-Id nur innerhalb einer Installation — deshalb hat die EAN Vorrang.
 */
sealed interface Artikelbezug {
    data class PerEan(val ean: String) : Artikelbezug
    data class PerId(val id: String) : Artikelbezug
}

internal fun Artikelbezug.kodiert(): String = when (this) {
    is Artikelbezug.PerEan -> "e:$ean"
    is Artikelbezug.PerId -> "i:$id"
}

internal fun String.alsBezug(): Artikelbezug? = when {
    startsWith("e:") && length > 2 -> Artikelbezug.PerEan(substring(2))
    startsWith("i:") && length > 2 -> Artikelbezug.PerId(substring(2))
    else -> null
}

data class Sicherung(
    val erstelltAm: Long,
    val artikel: List<GesicherterArtikel> = emptyList(),
    val preise: List<GesicherterPreis> = emptyList(),
    val standorte: List<GesicherterStandort> = emptyList(),
    val verlauf: List<GesicherterVerlauf> = emptyList(),
    val merkposten: List<GesicherterMerkposten> = emptyList(),
) {
    val istLeer: Boolean
        get() = artikel.isEmpty() && preise.isEmpty() && standorte.isEmpty() &&
            verlauf.isEmpty() && merkposten.isEmpty()
}

/** Nur selbst angelegte Artikel — Katalogartikel stecken schon in der App. */
data class GesicherterArtikel(
    val id: String,
    val ean: String?,
    val name: String,
    val marke: String?,
    val artikelnummer: String?,
    /** Der Kategoriename, nicht die Id: Ids hängen an der Reihenfolge beim Aufbau. */
    val kategorie: String?,
)

data class GesicherterPreis(
    val id: String,
    val bezug: Artikelbezug,
    val wert: Double,
    val werbepreis: Double?,
    val werbepreisVon: Long?,
    val werbepreisBis: Long?,
    val erfasstAm: Long,
    val erfasstVon: String?,
)

data class GesicherterStandort(
    val id: String,
    val bezug: Artikelbezug,
    val gang: String,
    val regalBeschreibung: String?,
    val kartenX: Float?,
    val kartenY: Float?,
    val erfasstAm: Long,
    val erfasstVon: String?,
)

data class GesicherterVerlauf(
    val bezug: Artikelbezug,
    val entitaet: String,
    val aenderungsart: String,
    val beschreibung: String,
    val geaendertVon: String?,
    val geaendertAm: Long,
)

data class GesicherterMerkposten(val schluessel: String, val wert: String)
