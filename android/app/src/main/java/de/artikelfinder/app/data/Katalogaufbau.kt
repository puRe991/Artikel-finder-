package de.artikelfinder.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.artikelfinder.app.data.local.ArtikelDatenbank
import de.artikelfinder.app.data.local.ArtikelEintrag
import de.artikelfinder.app.data.local.KategorieEintrag
import de.artikelfinder.app.data.local.MarktEintrag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface Aufbauzustand {
    data object Pruefen : Aufbauzustand
    data class Laeuft(val erledigt: Int, val gesamt: Int) : Aufbauzustand
    data object Fertig : Aufbauzustand
    data class Fehlgeschlagen(val meldung: String) : Aufbauzustand
}

/**
 * Baut beim ersten Start die Datenbank aus der mitgelieferten Katalogdatei auf.
 *
 * Der Katalog liegt als gepacktes TSV in den Assets (rund 520 KB für gut 19.000 Artikel).
 * Ihn beim ersten Start einzulesen ist deutlich sparsamer, als eine fertige
 * SQLite-Datenbank von mehreren Megabyte in die APK zu legen.
 */
@Singleton
class Katalogaufbau @Inject constructor(
    @ApplicationContext private val context: Context,
    private val datenbank: ArtikelDatenbank,
) {
    /** Woher die Katalogdatei kommt. Ueberschreibbar, damit Tests eigene Daten einspeisen. */
    var katalogQuelle: () -> InputStream = { context.assets.open(KATALOG_DATEI) }

    private val _zustand = MutableStateFlow<Aufbauzustand>(Aufbauzustand.Pruefen)
    val zustand: StateFlow<Aufbauzustand> = _zustand.asStateFlow()

    suspend fun sicherstellen() = withContext(Dispatchers.IO) {
        try {
            stammdatenAnlegen()

            if (datenbank.artikelDao().anzahl() > 0) {
                _zustand.value = Aufbauzustand.Fertig
                return@withContext
            }

            katalogEinlesen()
            _zustand.value = Aufbauzustand.Fertig
        } catch (fehler: Exception) {
            _zustand.value = Aufbauzustand.Fehlgeschlagen(
                fehler.message ?: "Der Katalog konnte nicht aufgebaut werden."
            )
        }
    }

    private suspend fun stammdatenAnlegen() {
        val stammdaten = datenbank.stammdatenDao()

        if (stammdaten.anzahlMaerkte() == 0) {
            stammdaten.marktEinfuegen(
                MarktEintrag(id = STANDARD_MARKT, name = "Kaufland Gießen", kette = "Kaufland", ort = "Gießen")
            )
        }

        if (stammdaten.anzahlKategorien() == 0) {
            for ((oberkategorie, unterkategorien) in KATEGORIERASTER) {
                val elternId = stammdaten.kategorieEinfuegen(
                    KategorieEintrag(name = oberkategorie, parentId = null)
                ).toInt()

                for (name in unterkategorien) {
                    stammdaten.kategorieEinfuegen(KategorieEintrag(name = name, parentId = elternId))
                }
            }
        }
    }

    private suspend fun katalogEinlesen() {
        val kategorienNachName = datenbank.stammdatenDao().kategorien().associate { it.name to it.id }
        val jetzt = System.currentTimeMillis()

        val zeilen = katalogQuelle().use { roh -> entpacktLesen(roh).readLines() }

        // Erste Zeile ist die Kopfzeile des TSV.
        val gesamt = (zeilen.size - 1).coerceAtLeast(0)
        _zustand.value = Aufbauzustand.Laeuft(0, gesamt)

        val stapel = ArrayList<ArtikelEintrag>(STAPELGROESSE)
        var erledigt = 0

        for (zeile in zeilen.drop(1)) {
            if (zeile.isBlank()) continue

            val felder = zeile.split('\t')
            if (felder.size < 5) continue

            val ean = Ean.normalisieren(felder[0]) ?: continue
            val name = felder[1].trim().ifEmpty { null } ?: continue
            val marke = felder[2].trim().ifEmpty { null }

            stapel += ArtikelEintrag(
                id = UUID.randomUUID().toString(),
                name = name,
                suchtext = Suchtext.fuerIndex(listOfNotNull(name, marke).joinToString(" ")),
                marke = marke,
                ean = ean,
                artikelnummer = null,
                kategorieId = kategorienNachName[felder[3].trim()],
                bildUrl = felder[4].trim().ifEmpty { null },
                erstelltVon = QUELLE_IMPORT,
                erstelltAm = jetzt,
                geaendertAm = null,
            )

            if (stapel.size >= STAPELGROESSE) {
                // IGNORE bei Konflikten: doppelte EANs im Katalog sollen den Aufbau nicht
                // abbrechen, der erste Treffer gewinnt.
                datenbank.artikelDao().stapelEinfuegen(stapel)
                erledigt += stapel.size
                stapel.clear()
                _zustand.value = Aufbauzustand.Laeuft(erledigt, gesamt)
            }
        }

        if (stapel.isNotEmpty()) {
            datenbank.artikelDao().stapelEinfuegen(stapel)
            erledigt += stapel.size
        }

        _zustand.value = Aufbauzustand.Laeuft(erledigt, gesamt)
    }

    /**
     * Liest die Katalogdatei, gepackt oder nicht.
     *
     * Der Build-Prozess entpackt .gz-Dateien in den Assets selbsttaetig und legt sie ohne
     * Endung ab — die Datei ist auf dem Geraet also Klartext, im Quellbaum eventuell nicht.
     * Statt sich auf eine Variante zu verlassen, wird am Dateikopf erkannt, was vorliegt.
     */
    private fun entpacktLesen(roh: InputStream): BufferedReader {
        val gepuffert = roh.buffered()
        gepuffert.mark(2)
        val ersteBytes = ByteArray(2)
        val gelesen = gepuffert.read(ersteBytes)
        gepuffert.reset()

        val istGzip = gelesen == 2 &&
            ersteBytes[0] == 0x1f.toByte() &&
            ersteBytes[1] == 0x8b.toByte()

        return if (istGzip) GZIPInputStream(gepuffert).bufferedReader() else gepuffert.bufferedReader()
    }

    companion object {
        const val STANDARD_MARKT = 1
        const val QUELLE_IMPORT = "Import"
        const val QUELLE_NUTZER = "Nutzer"

        private const val KATALOG_DATEI = "katalog-seed.tsv"
        private const val STAPELGROESSE = 1000

        /** Muss zum Raster passen, mit dem der Katalog erzeugt wurde. */
        private val KATEGORIERASTER = listOf(
            "Obst & Gemüse" to listOf("Obst", "Gemüse", "Salate & Kräuter"),
            "Molkereiprodukte" to listOf("Milch", "Joghurt & Quark", "Käse", "Butter & Margarine"),
            "Fleisch & Wurst" to listOf("Frischfleisch", "Wurstwaren", "Geflügel"),
            "Brot & Backwaren" to listOf("Brot", "Brötchen", "Kuchen & Gebäck"),
            "Tiefkühl" to listOf("Tiefkühlgemüse", "Pizza & Fertiggerichte", "Eis"),
            "Getränke" to listOf("Wasser", "Säfte", "Limonaden", "Kaffee & Tee", "Bier & Wein"),
            "Grundnahrungsmittel" to listOf(
                "Nudeln & Reis", "Konserven", "Öl & Essig", "Gewürze", "Mehl & Backzutaten"
            ),
            "Süßwaren & Snacks" to listOf("Schokolade", "Kekse", "Chips & Salziges"),
            "Drogerie" to listOf("Körperpflege", "Waschmittel", "Reinigung", "Papierwaren"),
            "Tierbedarf" to listOf("Hundefutter", "Katzenfutter", "Kleintier & Vogel", "Zubehör"),
            "Haushalt & Sonstiges" to emptyList(),
        )
    }
}
