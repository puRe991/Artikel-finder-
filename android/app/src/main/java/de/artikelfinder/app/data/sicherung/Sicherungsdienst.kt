package de.artikelfinder.app.data.sicherung

import androidx.room.withTransaction
import de.artikelfinder.app.data.Katalogaufbau.Companion.QUELLE_NUTZER
import de.artikelfinder.app.data.Suchtext
import de.artikelfinder.app.data.local.ArtikelDatenbank
import de.artikelfinder.app.data.local.ArtikelEintrag
import de.artikelfinder.app.data.local.MarktEintrag
import de.artikelfinder.app.data.local.MerkpostenEintrag
import de.artikelfinder.app.data.local.PreisEintrag
import de.artikelfinder.app.data.local.StandortEintrag
import de.artikelfinder.app.data.local.VerlaufEintrag
import de.artikelfinder.app.data.markt.Ketten
import de.artikelfinder.app.data.markt.Marktverwaltung
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sichert die selbst erfassten Daten und spielt sie wieder ein.
 *
 * Einspielen ergänzt und löscht nie. Wer eine Sicherung einliest, will seine Arbeit
 * zurückhaben — nicht die Arbeit ersetzen, die er seitdem gemacht hat. Datensätze bringen
 * ihre Id aus der Datei mit, deshalb ist mehrfaches Einspielen derselben Datei folgenlos.
 */
@Singleton
class Sicherungsdienst @Inject constructor(
    private val datenbank: ArtikelDatenbank,
    private val maerkte: Marktverwaltung,
) {

    suspend fun erstellen(jetzt: Long = System.currentTimeMillis()): Sicherung {
        val preise = datenbank.preisDao().alle()
        val standorte = datenbank.standortDao().alle()
        val verlauf = datenbank.verlaufDao().alle()
        val eigeneArtikel = datenbank.artikelDao().nachQuelle(QUELLE_NUTZER)

        val betroffene = buildSet {
            preise.forEach { add(it.artikelId) }
            standorte.forEach { add(it.artikelId) }
            verlauf.forEach { add(it.artikelId) }
            eigeneArtikel.forEach { add(it.id) }
        }

        val bezuege = bezuegeErmitteln(betroffene)
        val kategorienNachId = datenbank.stammdatenDao().kategorien().associate { it.id to it.name }

        // Nur Maerkte, zu denen es auch Erfassungen gibt — ein leer angelegter Markt ist
        // nichts, was man sichern muesste.
        val benutzteMaerkte = (preise.map { it.marktId } + standorte.map { it.marktId }).toSet()
        val marktSchluessel = datenbank.stammdatenDao().maerkte()
            .filter { it.id in benutzteMaerkte }
            .associate { it.id to Suchtext.normalisieren(it.name) }

        return Sicherung(
            erstelltAm = jetzt,
            maerkte = datenbank.stammdatenDao().maerkte()
                .filter { it.id in benutzteMaerkte }
                .map { GesicherterMarkt(marktSchluessel.getValue(it.id), it.kette, it.name, it.ort) },
            artikel = eigeneArtikel.map {
                GesicherterArtikel(
                    id = it.id,
                    ean = it.ean,
                    name = it.name,
                    marke = it.marke,
                    artikelnummer = it.artikelnummer,
                    kategorie = it.kategorieId?.let(kategorienNachId::get),
                )
            },
            preise = preise.mapNotNull { eintrag ->
                bezuege[eintrag.artikelId]?.let {
                    GesicherterPreis(
                        id = eintrag.id, bezug = it, wert = eintrag.wert,
                        werbepreis = eintrag.werbepreis,
                        werbepreisVon = eintrag.werbepreisVon,
                        werbepreisBis = eintrag.werbepreisBis,
                        erfasstAm = eintrag.erfasstAm, erfasstVon = eintrag.erfasstVon,
                        marktSchluessel = marktSchluessel[eintrag.marktId],
                    )
                }
            },
            standorte = standorte.mapNotNull { eintrag ->
                bezuege[eintrag.artikelId]?.let {
                    GesicherterStandort(
                        id = eintrag.id, bezug = it, gang = eintrag.gang,
                        regalBeschreibung = eintrag.regalBeschreibung,
                        kartenX = eintrag.kartenX, kartenY = eintrag.kartenY,
                        erfasstAm = eintrag.erfasstAm, erfasstVon = eintrag.erfasstVon,
                        marktSchluessel = marktSchluessel[eintrag.marktId],
                    )
                }
            },
            verlauf = verlauf.mapNotNull { eintrag ->
                bezuege[eintrag.artikelId]?.let {
                    GesicherterVerlauf(
                        bezug = it, entitaet = eintrag.entitaet,
                        aenderungsart = eintrag.aenderungsart,
                        beschreibung = eintrag.beschreibung,
                        geaendertVon = eintrag.geaendertVon, geaendertAm = eintrag.geaendertAm,
                    )
                }
            },
            merkposten = datenbank.merkpostenDao().alle()
                .map { GesicherterMerkposten(it.schluessel, it.wert) },
        )
    }

    suspend fun einspielen(sicherung: Sicherung): Sicherungsbericht =
        datenbank.withTransaction {
            val kategorienNachName = datenbank.stammdatenDao().kategorien()
                .associate { it.name to it.id }
            val marktZuordnung = maerkteAufloesen(sicherung.maerkte)
            val aufloeser = Artikelaufloeser()

            var neueArtikel = 0
            for (eintrag in sicherung.artikel) {
                val vorhanden = aufloeser.lokaleId(eintrag.ean?.let(Artikelbezug::PerEan))
                    ?: aufloeser.lokaleId(Artikelbezug.PerId(eintrag.id))

                if (vorhanden != null) {
                    aufloeser.merken(eintrag, vorhanden)
                    continue
                }

                // Die Id aus der Datei bleibt erhalten: dadurch findet ein zweiter
                // Importlauf denselben Artikel wieder, statt ihn ein weiteres Mal anzulegen.
                datenbank.artikelDao().einfuegenWennNeu(
                    ArtikelEintrag(
                        id = eintrag.id,
                        name = eintrag.name,
                        suchtext = Suchtext.fuerIndex(
                            listOfNotNull(eintrag.name, eintrag.marke).joinToString(" ")
                        ),
                        marke = eintrag.marke,
                        ean = eintrag.ean,
                        artikelnummer = eintrag.artikelnummer,
                        kategorieId = eintrag.kategorie?.let(kategorienNachName::get),
                        bildUrl = null,
                        eigenmarkeKette = Ketten.ketteFuerMarke(eintrag.marke),
                        erstelltVon = QUELLE_NUTZER,
                        erstelltAm = sicherung.erstelltAm,
                        geaendertAm = null,
                    )
                )
                aufloeser.merken(eintrag, eintrag.id)
                neueArtikel++
            }

            var ohneArtikel = 0

            val preise = sicherung.preise.mapNotNull { eintrag ->
                val id = aufloeser.lokaleId(eintrag.bezug) ?: run { ohneArtikel++; return@mapNotNull null }
                PreisEintrag(
                    id = eintrag.id, artikelId = id, marktId = marktFuer(eintrag.marktSchluessel, marktZuordnung),
                    wert = eintrag.wert, werbepreis = eintrag.werbepreis,
                    werbepreisVon = eintrag.werbepreisVon, werbepreisBis = eintrag.werbepreisBis,
                    erfasstAm = eintrag.erfasstAm, erfasstVon = eintrag.erfasstVon,
                )
            }

            val standorte = sicherung.standorte.mapNotNull { eintrag ->
                val id = aufloeser.lokaleId(eintrag.bezug) ?: run { ohneArtikel++; return@mapNotNull null }
                StandortEintrag(
                    id = eintrag.id, artikelId = id, marktId = marktFuer(eintrag.marktSchluessel, marktZuordnung),
                    gang = eintrag.gang, regalBeschreibung = eintrag.regalBeschreibung,
                    kartenX = eintrag.kartenX, kartenY = eintrag.kartenY,
                    erfasstAm = eintrag.erfasstAm, erfasstVon = eintrag.erfasstVon,
                )
            }

            val neuePreise = datenbank.preisDao().einfuegenWennNeu(preise).count { it != -1L }
            val neueStandorte = datenbank.standortDao().einfuegenWennNeu(standorte).count { it != -1L }

            // Der Verlauf bekommt seine Id von der Datenbank, kann also nicht über einen
            // Konflikt entdoppelt werden. Ein Eintrag ist dann derselbe, wenn Artikel,
            // Zeitpunkt und Beschreibung übereinstimmen.
            val bekannteEintraege = datenbank.verlaufDao().alle()
                .mapTo(mutableSetOf()) { Verlaufschluessel(it.artikelId, it.geaendertAm, it.beschreibung) }

            val neuerVerlauf = sicherung.verlauf.mapNotNull { eintrag ->
                val id = aufloeser.lokaleId(eintrag.bezug) ?: return@mapNotNull null
                val schluessel = Verlaufschluessel(id, eintrag.geaendertAm, eintrag.beschreibung)
                if (!bekannteEintraege.add(schluessel)) return@mapNotNull null

                VerlaufEintrag(
                    artikelId = id, entitaet = eintrag.entitaet,
                    aenderungsart = eintrag.aenderungsart, beschreibung = eintrag.beschreibung,
                    geaendertVon = eintrag.geaendertVon, geaendertAm = eintrag.geaendertAm,
                )
            }
            datenbank.verlaufDao().einfuegen(neuerVerlauf)

            sicherung.merkposten.forEach {
                datenbank.merkpostenDao().schreiben(MerkpostenEintrag(it.schluessel, it.wert))
            }

            Sicherungsbericht(
                neueArtikel = neueArtikel,
                neuePreise = neuePreise,
                neueStandorte = neueStandorte,
                neuerVerlauf = neuerVerlauf.size,
                bereitsVorhanden = (preise.size - neuePreise) + (standorte.size - neueStandorte),
                ohneArtikel = ohneArtikel,
            )
        }

    /**
     * Ordnet die Maerkte aus der Datei denen dieses Geraets zu — ueber den normalisierten
     * Namen, denn die Markt-Id ist wie die Artikel-Id nur lokal gueltig. Was fehlt, wird
     * angelegt: die Preise eines Markts, den es hier nicht gibt, waeren sonst verloren.
     */
    private suspend fun maerkteAufloesen(gesichert: List<GesicherterMarkt>): Map<String, Int> {
        if (gesichert.isEmpty()) return emptyMap()

        val stammdaten = datenbank.stammdatenDao()
        val vorhanden = stammdaten.maerkte().associateBy { Suchtext.normalisieren(it.name) }

        return gesichert.associate { markt ->
            val lokal = vorhanden[markt.schluessel]?.id
                ?: stammdaten.marktEinfuegen(
                    MarktEintrag(name = markt.name, kette = markt.kette, ort = markt.ort)
                ).toInt()

            markt.schluessel to lokal
        }
    }

    /**
     * Dateien aus Format 1 kennen keine Maerkte — damals gab es nur einen. Ihre Erfassungen
     * landen im gerade gewaehlten Markt, was der einzigen sinnvollen Lesart entspricht.
     */
    private fun marktFuer(schluessel: String?, zuordnung: Map<String, Int>): Int =
        schluessel?.let { zuordnung[it] } ?: maerkte.aktuelleId()

    private suspend fun bezuegeErmitteln(ids: Set<String>): Map<String, Artikelbezug> =
        ids.chunked(SQL_PARAMETERGRENZE)
            .flatMap { datenbank.artikelDao().eanZuIds(it) }
            .associate { zeile ->
                zeile.id to (zeile.ean?.let(Artikelbezug::PerEan) ?: Artikelbezug.PerId(zeile.id))
            }

    /** Merkt sich, welcher Bezug aus der Datei auf welchen Artikel dieses Geräts zeigt. */
    private inner class Artikelaufloeser {
        private val bekannt = mutableMapOf<Artikelbezug, String?>()

        suspend fun lokaleId(bezug: Artikelbezug?): String? {
            if (bezug == null) return null
            // Auch ein erfolgloser Treffer wird gemerkt: eine Sicherung enthält viele
            // Preise zu denselben Artikeln, und nicht auflösbare Bezüge sollen nicht bei
            // jeder Zeile erneut abgefragt werden.
            if (bekannt.containsKey(bezug)) return bekannt[bezug]

            val treffer = when (bezug) {
                is Artikelbezug.PerEan -> datenbank.artikelDao().idPerEan(bezug.ean)
                is Artikelbezug.PerId -> datenbank.artikelDao().roh(bezug.id)?.id
            }
            bekannt[bezug] = treffer
            return treffer
        }

        fun merken(eintrag: GesicherterArtikel, lokaleId: String) {
            bekannt[Artikelbezug.PerId(eintrag.id)] = lokaleId
            eintrag.ean?.let { bekannt[Artikelbezug.PerEan(it)] = lokaleId }
        }
    }

    private data class Verlaufschluessel(
        val artikelId: String,
        val geaendertAm: Long,
        val beschreibung: String,
    )

    private companion object {
        /** SQLite nimmt nicht beliebig viele Platzhalter in einem IN(...) entgegen. */
        const val SQL_PARAMETERGRENZE = 500
    }
}

/**
 * Was das Einspielen bewirkt hat. `bereitsVorhanden` ist der Normalfall, wenn dieselbe
 * Datei ein zweites Mal eingelesen wird; `ohneArtikel` zählt Erfassungen zu Artikeln, die
 * dieser Katalog nicht kennt.
 */
data class Sicherungsbericht(
    val neueArtikel: Int,
    val neuePreise: Int,
    val neueStandorte: Int,
    val neuerVerlauf: Int,
    val bereitsVorhanden: Int,
    val ohneArtikel: Int,
) {
    val nichtsGeaendert: Boolean
        get() = neueArtikel == 0 && neuePreise == 0 && neueStandorte == 0 && neuerVerlauf == 0
}
