package de.artikelfinder.app.data

import de.artikelfinder.app.data.Katalogaufbau.Companion.QUELLE_NUTZER
import de.artikelfinder.app.data.local.ArtikelDatenbank
import de.artikelfinder.app.data.local.ArtikelEintrag
import de.artikelfinder.app.data.local.ArtikelMitStand
import de.artikelfinder.app.data.local.Merkposten
import de.artikelfinder.app.data.local.MerkpostenEintrag
import de.artikelfinder.app.data.local.PreisEintrag
import de.artikelfinder.app.data.local.StandortEintrag
import de.artikelfinder.app.data.local.VerlaufEintrag
import de.artikelfinder.app.data.markt.Ketten
import de.artikelfinder.app.data.markt.Marktverwaltung
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis einer Operation. Fehler sind hier fachlich (EAN doppelt), nicht technisch. */
sealed interface Abruf<out T> {
    data class Erfolg<T>(val wert: T) : Abruf<T>
    data class Fehler(val meldung: String) : Abruf<Nothing>
}

/**
 * Alle Daten liegen auf dem Gerät — es gibt keinen Server und keine Netzabhängigkeit.
 *
 * Die Regeln aus dem ursprünglichen Backend gelten weiter: Preise und Standorte werden
 * angehängt statt überschrieben, der jüngste Eintrag je Markt ist der aktuelle, und jede
 * Änderung landet im Verlauf.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ArtikelRepository @Inject constructor(
    private val datenbank: ArtikelDatenbank,
    private val maerkte: Marktverwaltung,
) {

    private val artikelDao get() = datenbank.artikelDao()
    private val preisDao get() = datenbank.preisDao()
    private val standortDao get() = datenbank.standortDao()
    private val verlaufDao get() = datenbank.verlaufDao()
    private val stammdatenDao get() = datenbank.stammdatenDao()

    /** Laufende Angebote, das am schnellsten ablaufende zuerst. */
    fun aktiveAngebote(): Flow<List<Artikel>> =
        // Ein Marktwechsel muss die Liste neu ziehen — die Angebote gelten je Markt.
        maerkte.aktuell
            .flatMapLatest { markt ->
                artikelDao.aktiveAngebote(markt?.id ?: KEIN_MARKT, System.currentTimeMillis())
            }
            .map { liste -> liste.map { it.zuModell() } }

    /**
     * Der zuletzt eingetippte Aktionszeitraum. Beim Abtippen eines Prospekts gilt derselbe
     * Zeitraum fuer jedes Angebot; ihn 40-mal einzugeben waere die eigentliche Arbeit.
     */
    suspend fun letztesAktionsende(): Long? =
        datenbank.merkpostenDao().lesen(Merkposten.AKTIONSENDE)?.toLongOrNull()

    suspend fun aktionsendeMerken(zeitpunkt: Long) =
        datenbank.merkpostenDao().schreiben(
            MerkpostenEintrag(Merkposten.AKTIONSENDE, zeitpunkt.toString())
        )

    fun zuletztBearbeitet(): Flow<List<Artikel>> =
        maerkte.aktuell
            .flatMapLatest { markt -> artikelDao.zuletztBearbeitet(markt?.id ?: KEIN_MARKT) }
            .map { liste -> liste.map { it.zuModell() } }

    suspend fun suchen(
        suchbegriff: String?,
        kategorieId: Int? = null,
        nurMitWerbepreis: Boolean = false,
        fremdeEigenmarken: Boolean = false,
        seite: Int = 1,
        seitengroesse: Int = 50,
    ): Abruf<List<Artikel>> {
        val tokens = Suchtext.normalisieren(suchbegriff)
            .split(' ')
            .filter { it.isNotBlank() }
            .take(3)

        // Die Abfrage erwartet immer drei Muster; nicht belegte sind als '%' neutral.
        val muster = List(3) { i -> tokens.getOrNull(i)?.let { "%$it%" } ?: "%" }
        val kategorien = kategorieId?.let { zweigIds(it) } ?: emptyList()

        val treffer = artikelDao.suchen(
            hatSuche = if (tokens.isEmpty()) 0 else 1,
            t1 = muster[0], t2 = muster[1], t3 = muster[2],
            kategorieIds = kategorien,
            kategorieAnzahl = kategorien.size,
            nurMitStandort = 0,
            nurMitWerbepreis = if (nurMitWerbepreis) 1 else 0,
            fremdeZeigen = fremdeZeigen(fremdeEigenmarken),
            kette = maerkte.aktuelleKette(),
            jetzt = System.currentTimeMillis(),
            marktId = marktId(),
            grenze = seitengroesse,
            versatz = (seite - 1) * seitengroesse,
        )

        return Abruf.Erfolg(treffer.map { it.zuModell() })
    }

    suspend fun anzahlTreffer(
        suchbegriff: String?,
        kategorieId: Int? = null,
        nurMitWerbepreis: Boolean = false,
        fremdeEigenmarken: Boolean = false,
    ): Int {
        val tokens = Suchtext.normalisieren(suchbegriff).split(' ').filter { it.isNotBlank() }.take(3)
        val muster = List(3) { i -> tokens.getOrNull(i)?.let { "%$it%" } ?: "%" }
        val kategorien = kategorieId?.let { zweigIds(it) } ?: emptyList()

        return artikelDao.anzahlTreffer(
            hatSuche = if (tokens.isEmpty()) 0 else 1,
            t1 = muster[0], t2 = muster[1], t3 = muster[2],
            kategorieIds = kategorien,
            kategorieAnzahl = kategorien.size,
            nurMitStandort = 0,
            nurMitWerbepreis = if (nurMitWerbepreis) 1 else 0,
            fremdeZeigen = fremdeZeigen(fremdeEigenmarken),
            kette = maerkte.aktuelleKette(),
            jetzt = System.currentTimeMillis(),
            marktId = marktId(),
        )
    }

    /**
     * Ohne bekannte Kette bleibt der Filter aus. Sonst verschwänden bei einem Markt ohne
     * Zuordnung („Anderer Markt") sämtliche Eigenmarken auf einmal — und das ist mit
     * Sicherheit falscher als ein fremder Treffer zu viel.
     */
    private fun fremdeZeigen(gewuenscht: Boolean): Int =
        if (gewuenscht || maerkte.aktuelleKette().let { it == null || it == Ketten.SONSTIGE }) 1 else 0

    suspend fun holen(id: String): Abruf<ArtikelDetail> {
        val artikel = artikelDao.holen(id, marktId())
            ?: return Abruf.Fehler("Der Artikel wurde nicht gefunden.")

        return Abruf.Erfolg(
            ArtikelDetail(
                artikel = artikel.zuModell(),
                preise = preisDao.fuerArtikel(id).map { it.zuModell() },
                standorte = standortDao.fuerArtikel(id).map { it.zuModell() },
                erstelltVon = artikel.artikel.erstelltVon,
            )
        )
    }

    /** Barcode-Lookup. `null` heißt "unbekannt" — die App bietet dann das Anlegen an. */
    suspend fun perEan(ean: String): Abruf<ArtikelDetail?> {
        val normalisiert = Ean.normalisieren(ean) ?: return Abruf.Erfolg(null)
        val treffer = artikelDao.perEan(normalisiert, marktId()) ?: return Abruf.Erfolg(null)
        return holen(treffer.artikel.id) as Abruf<ArtikelDetail?>
    }

    suspend fun anlegen(
        name: String,
        marke: String? = null,
        ean: String? = null,
        artikelnummer: String? = null,
        kategorieId: Int? = null,
        preis: Double? = null,
        werbepreis: Double? = null,
        gang: String? = null,
        regalBeschreibung: String? = null,
        erfasstVon: String? = null,
    ): Abruf<ArtikelDetail> {
        val normalisierteEan = Ean.normalisieren(ean)

        if (normalisierteEan != null && artikelDao.idPerEan(normalisierteEan) != null) {
            return Abruf.Fehler(
                "Zur EAN $normalisierteEan gibt es bereits einen Artikel. Ergänze ihn, " +
                    "statt einen zweiten anzulegen."
            )
        }

        val jetzt = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()

        artikelDao.einfuegen(
            ArtikelEintrag(
                id = id,
                name = name.trim(),
                suchtext = Suchtext.fuerIndex(listOfNotNull(name.trim(), marke?.trim()).joinToString(" ")),
                marke = marke.leerAlsNull(),
                ean = normalisierteEan,
                artikelnummer = artikelnummer.leerAlsNull(),
                kategorieId = kategorieId,
                bildUrl = null,
                eigenmarkeKette = Ketten.ketteFuerMarke(marke),
                erstelltVon = QUELLE_NUTZER,
                erstelltAm = jetzt,
                geaendertAm = null,
            )
        )

        protokollieren(id, "Artikel", "Angelegt", "Artikel \"${name.trim()}\" angelegt.", erfasstVon, jetzt)

        preis?.let { preisErfassenIntern(id, it, werbepreis, null, erfasstVon, jetzt) }
        gang.leerAlsNull()?.let { standortErfassenIntern(id, it, regalBeschreibung, erfasstVon, jetzt) }

        return holen(id)
    }

    suspend fun aendern(
        id: String,
        name: String,
        marke: String? = null,
        ean: String? = null,
        artikelnummer: String? = null,
        kategorieId: Int? = null,
        geaendertVon: String? = null,
    ): Abruf<ArtikelDetail> {
        val vorhanden = artikelDao.roh(id) ?: return Abruf.Fehler("Der Artikel wurde nicht gefunden.")
        val normalisierteEan = Ean.normalisieren(ean)

        if (normalisierteEan != null) {
            val andere = artikelDao.idPerEan(normalisierteEan)
            if (andere != null && andere != id) {
                return Abruf.Fehler("Die EAN $normalisierteEan gehört bereits zu einem anderen Artikel.")
            }
        }

        val aenderungen = buildList {
            vergleichen(this, "Name", vorhanden.name, name.trim())
            vergleichen(this, "Marke", vorhanden.marke, marke.leerAlsNull())
            vergleichen(this, "EAN", vorhanden.ean, normalisierteEan)
            vergleichen(this, "Artikelnummer", vorhanden.artikelnummer, artikelnummer.leerAlsNull())
            vergleichen(this, "Kategorie", vorhanden.kategorieId?.toString(), kategorieId?.toString())
        }

        if (aenderungen.isEmpty()) return holen(id)

        val jetzt = System.currentTimeMillis()
        artikelDao.aktualisieren(
            vorhanden.copy(
                name = name.trim(),
                suchtext = Suchtext.fuerIndex(listOfNotNull(name.trim(), marke?.trim()).joinToString(" ")),
                marke = marke.leerAlsNull(),
                ean = normalisierteEan,
                artikelnummer = artikelnummer.leerAlsNull(),
                kategorieId = kategorieId,
                eigenmarkeKette = Ketten.ketteFuerMarke(marke),
                geaendertAm = jetzt,
            )
        )

        protokollieren(id, "Artikel", "Geaendert", aenderungen.joinToString("; "), geaendertVon, jetzt)
        return holen(id)
    }

    suspend fun loeschen(id: String): Abruf<Unit> {
        artikelDao.roh(id) ?: return Abruf.Fehler("Der Artikel wurde nicht gefunden.")
        artikelDao.loeschen(id)
        return Abruf.Erfolg(Unit)
    }

    suspend fun preisErfassen(
        artikelId: String,
        preis: Double,
        werbepreis: Double? = null,
        werbepreisBis: Long? = null,
        erfasstVon: String? = null,
    ): Abruf<Preis> {
        artikelDao.roh(artikelId) ?: return Abruf.Fehler("Der Artikel wurde nicht gefunden.")

        if (werbepreis != null && werbepreis > preis) {
            return Abruf.Fehler("Der Werbepreis darf nicht über dem Normalpreis liegen.")
        }

        if (werbepreis != null && werbepreisBis != null) {
            aktionsendeMerken(werbepreisBis)
        }

        val eintrag = preisErfassenIntern(
            artikelId, preis, werbepreis, werbepreisBis, erfasstVon, System.currentTimeMillis()
        )
        return Abruf.Erfolg(eintrag.zuModell())
    }

    suspend fun standortErfassen(
        artikelId: String,
        gang: String,
        regalBeschreibung: String? = null,
        erfasstVon: String? = null,
    ): Abruf<Standort> {
        artikelDao.roh(artikelId) ?: return Abruf.Fehler("Der Artikel wurde nicht gefunden.")

        val eintrag = standortErfassenIntern(
            artikelId, gang, regalBeschreibung, erfasstVon, System.currentTimeMillis()
        )
        return Abruf.Erfolg(eintrag.zuModell())
    }

    suspend fun verlauf(artikelId: String): Abruf<List<Verlaufseintrag>> =
        Abruf.Erfolg(
            verlaufDao.fuerArtikel(artikelId).map {
                Verlaufseintrag(it.id, it.entitaet, it.beschreibung, it.geaendertVon, it.geaendertAm)
            }
        )

    suspend fun kategorien(): Abruf<List<Kategorie>> {
        val alle = stammdatenDao.kategorien()
        val nachId = alle.associateBy { it.id }

        return Abruf.Erfolg(
            alle.map { k ->
                val pfad = generateSequence(k) { nachId[it.parentId] }
                    .map { it.name }
                    .toList()
                    .reversed()
                    .joinToString(" > ")

                Kategorie(k.id, k.name, pfad)
            }.sortedBy { it.pfad.lowercase(Locale.GERMANY) }
        )
    }

    suspend fun gaenge(): Abruf<List<Gang>> {
        val zeilen = artikelDao.gaenge(marktId())

        return Abruf.Erfolg(
            zeilen
                .map { Gang(it.gang, it.anzahl) }
                // "2" vor "10": numerische Gänge nicht alphabetisch sortieren.
                .sortedWith(compareBy({ it.gang.toIntOrNull() ?: Int.MAX_VALUE }, { it.gang }))
        )
    }

    suspend fun artikelImGang(gang: String): Abruf<List<Artikel>> =
        Abruf.Erfolg(artikelDao.imGang(gang, marktId()).map { it.zuModell() })

    /** Der gewählte Markt, nicht irgendeiner — es können mehrere angelegt sein. */
    suspend fun markt(): Abruf<Markt> {
        val markt = maerkte.aktuell.value
            ?: return Abruf.Fehler("Es ist kein Markt gewählt.")

        return Abruf.Erfolg(Markt(markt.id, markt.name, markt.ort))
    }

    private suspend fun preisErfassenIntern(
        artikelId: String,
        preis: Double,
        werbepreis: Double?,
        werbepreisBis: Long?,
        erfasstVon: String?,
        jetzt: Long,
    ): PreisEintrag {
        val vorheriger = preisDao.aktuellster(artikelId, marktId())

        val eintrag = PreisEintrag(
            id = UUID.randomUUID().toString(),
            artikelId = artikelId,
            marktId = marktId(),
            wert = preis,
            werbepreis = werbepreis,
            werbepreisVon = null,
            werbepreisBis = werbepreisBis,
            erfasstAm = jetzt,
            erfasstVon = erfasstVon.leerAlsNull(),
        )

        preisDao.einfuegen(eintrag)

        val beschreibung = buildString {
            if (vorheriger == null) {
                append(String.format(Locale.GERMANY, "Preis %.2f EUR erfasst", preis))
            } else {
                append(String.format(Locale.GERMANY, "Preis %.2f -> %.2f EUR", vorheriger.wert, preis))
            }
            werbepreis?.let { append(String.format(Locale.GERMANY, ", Werbepreis %.2f EUR", it)) }
        }

        protokollieren(artikelId, "Preis", "Angelegt", beschreibung, erfasstVon, jetzt)
        return eintrag
    }

    private suspend fun standortErfassenIntern(
        artikelId: String,
        gang: String,
        regalBeschreibung: String?,
        erfasstVon: String?,
        jetzt: Long,
    ): StandortEintrag {
        val eintrag = StandortEintrag(
            id = UUID.randomUUID().toString(),
            artikelId = artikelId,
            marktId = marktId(),
            gang = gang.trim(),
            regalBeschreibung = regalBeschreibung.leerAlsNull(),
            kartenX = null,
            kartenY = null,
            erfasstAm = jetzt,
            erfasstVon = erfasstVon.leerAlsNull(),
        )

        standortDao.einfuegen(eintrag)

        val beschreibung = regalBeschreibung.leerAlsNull()
            ?.let { "Standort Gang ${gang.trim()} ($it)" }
            ?: "Standort Gang ${gang.trim()}"

        protokollieren(artikelId, "Standort", "Angelegt", beschreibung, erfasstVon, jetzt)
        return eintrag
    }

    private suspend fun protokollieren(
        artikelId: String,
        entitaet: String,
        art: String,
        beschreibung: String,
        von: String?,
        jetzt: Long,
    ) = verlaufDao.einfuegen(
        VerlaufEintrag(
            artikelId = artikelId,
            entitaet = entitaet,
            aenderungsart = art,
            beschreibung = beschreibung.take(500),
            geaendertVon = von.leerAlsNull(),
            geaendertAm = jetzt,
        )
    )

    /** Die Kategorie selbst plus alle Unterkategorien. */
    private suspend fun zweigIds(wurzelId: Int): List<Int> {
        val alle = stammdatenDao.kategorien()
        val kinder = alle.filter { it.parentId != null }.groupBy({ it.parentId!! }, { it.id })

        val ergebnis = mutableListOf<Int>()
        val offen = ArrayDeque(listOf(wurzelId))
        val gesehen = mutableSetOf<Int>()

        while (offen.isNotEmpty()) {
            val aktuell = offen.removeFirst()
            if (!gesehen.add(aktuell)) continue
            ergebnis += aktuell
            kinder[aktuell]?.let { offen.addAll(it) }
        }

        return ergebnis
    }

    private fun vergleichen(ziel: MutableList<String>, feld: String, alt: String?, neu: String?) {
        if (alt != neu) ziel += "$feld: ${alt ?: "—"} -> ${neu ?: "—"}"
    }

    private fun String?.leerAlsNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    /** Der gerade gewaehlte Markt. Preise und Gaenge haengen daran. */
    private fun marktId(): Int = maerkte.aktuelleId()

    private companion object {
        /** Trifft keinen Markt — solange keiner gewaehlt ist, bleiben die Listen leer. */
        const val KEIN_MARKT = 0
    }
}

// --- Zuordnung Datenbank -> UI-Modell ---

private fun ArtikelMitStand.zuModell(): Artikel {
    val jetzt = System.currentTimeMillis()
    val aktiv = werbepreis != null &&
        (werbepreisVon == null || werbepreisVon <= jetzt) &&
        (werbepreisBis == null || werbepreisBis >= jetzt)

    return Artikel(
        id = artikel.id,
        name = artikel.name,
        marke = artikel.marke,
        ean = artikel.ean,
        artikelnummer = artikel.artikelnummer,
        kategorieId = artikel.kategorieId,
        kategorieName = kategorieName,
        bildUrl = artikel.bildUrl,
        preis = preisWert?.let {
            Preis(preis = it, werbepreis = werbepreis, werbepreisAktiv = aktiv, werbepreisGueltigBis = werbepreisBis)
        },
        standort = gang?.let { Standort(gang = it, regalBeschreibung = regalBeschreibung) },
    )
}

private fun PreisEintrag.zuModell(): Preis {
    val jetzt = System.currentTimeMillis()
    val aktiv = werbepreis != null &&
        (werbepreisVon == null || werbepreisVon <= jetzt) &&
        (werbepreisBis == null || werbepreisBis >= jetzt)

    return Preis(
        id = id,
        preis = wert,
        werbepreis = werbepreis,
        werbepreisAktiv = aktiv,
        werbepreisGueltigVon = werbepreisVon,
        werbepreisGueltigBis = werbepreisBis,
        erfasstAm = erfasstAm,
        erfasstVon = erfasstVon,
    )
}

private fun StandortEintrag.zuModell() = Standort(
    id = id,
    gang = gang,
    regalBeschreibung = regalBeschreibung,
    kartenX = kartenX,
    kartenY = kartenY,
    erfasstAm = erfasstAm,
    erfasstVon = erfasstVon,
)
