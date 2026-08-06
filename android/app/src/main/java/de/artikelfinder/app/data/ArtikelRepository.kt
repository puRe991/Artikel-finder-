package de.artikelfinder.app.data

import de.artikelfinder.app.data.local.ArtikelCacheDao
import de.artikelfinder.app.data.remote.ArtikelAendernDto
import de.artikelfinder.app.data.remote.ArtikelAnlegenDto
import de.artikelfinder.app.data.remote.ArtikelApi
import de.artikelfinder.app.data.remote.PreisErfassenDto
import de.artikelfinder.app.data.remote.ProblemDetailsDto
import de.artikelfinder.app.data.remote.StandortErfassenDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis eines Aufrufs, der auch ohne Netz eine brauchbare Antwort liefern soll. */
sealed interface Abruf<out T> {
    data class Erfolg<T>(val wert: T, val ausCache: Boolean = false) : Abruf<T>
    data class Fehler(val meldung: String, val offline: Boolean = false) : Abruf<Nothing>
}

@Singleton
class ArtikelRepository @Inject constructor(
    private val api: ArtikelApi,
    private val cache: ArtikelCacheDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Zuletzt gesehene Artikel aus dem Cache — die Startseite hat dadurch sofort Inhalt. */
    fun zuletztGesehen(): Flow<List<Artikel>> =
        cache.zuletztGesehen().map { liste -> liste.map { it.zuModell() } }

    suspend fun suchen(
        suchbegriff: String?,
        kategorieId: Int? = null,
        nurMitWerbepreis: Boolean = false,
        seite: Int = 1,
    ): Abruf<List<Artikel>> = try {
        val antwort = api.suchen(
            suchbegriff = suchbegriff?.takeIf { it.isNotBlank() },
            kategorieId = kategorieId,
            nurMitWerbepreis = nurMitWerbepreis,
            seite = seite,
        )
        val artikel = antwort.eintraege.map { it.zuModell() }
        merken(artikel)
        Abruf.Erfolg(artikel)
    } catch (fehler: IOException) {
        // Kein Netz: der Cache ist besser als ein leerer Bildschirm, muss aber als
        // möglicherweise veraltet gekennzeichnet sein.
        val treffer = cache.suchen(suchbegriff.orEmpty()).map { it.zuModell() }
        if (treffer.isEmpty()) offlineFehler(fehler) else Abruf.Erfolg(treffer, ausCache = true)
    } catch (fehler: HttpException) {
        Abruf.Fehler(fehlermeldung(fehler))
    }

    suspend fun holen(id: String): Abruf<ArtikelDetail> = try {
        val detail = api.holen(id).zuModell()
        merken(listOf(detail.artikel))
        Abruf.Erfolg(detail)
    } catch (fehler: IOException) {
        cache.holen(id)?.let { Abruf.Erfolg(ArtikelDetail(it.zuModell()), ausCache = true) }
            ?: offlineFehler(fehler)
    } catch (fehler: HttpException) {
        Abruf.Fehler(fehlermeldung(fehler))
    }

    /**
     * Barcode-Lookup. `null` im Erfolgsfall heißt "Artikel unbekannt" — dann bietet die
     * App das Anlegen an, statt einen Fehler zu zeigen.
     */
    suspend fun perEan(ean: String): Abruf<ArtikelDetail?> = try {
        val antwort = api.perEan(ean)
        when {
            antwort.isSuccessful -> {
                val detail = antwort.body()?.zuModell()
                detail?.let { merken(listOf(it.artikel)) }
                Abruf.Erfolg(detail)
            }
            antwort.code() == 404 -> Abruf.Erfolg(null)
            else -> Abruf.Fehler("Die Suche nach $ean ist fehlgeschlagen (${antwort.code()}).")
        }
    } catch (fehler: IOException) {
        cache.perEan(ean)?.let { Abruf.Erfolg(ArtikelDetail(it.zuModell()), ausCache = true) }
            ?: offlineFehler(fehler)
    } catch (fehler: HttpException) {
        Abruf.Fehler(fehlermeldung(fehler))
    }

    suspend fun anlegen(eingabe: ArtikelAnlegenDto): Abruf<ArtikelDetail> =
        schreibend { api.anlegen(eingabe).zuModell().also { merken(listOf(it.artikel)) } }

    suspend fun aendern(id: String, eingabe: ArtikelAendernDto, geaendertVon: String?): Abruf<ArtikelDetail> =
        schreibend { api.aendern(id, eingabe, geaendertVon).zuModell().also { merken(listOf(it.artikel)) } }

    suspend fun preisErfassen(artikelId: String, eingabe: PreisErfassenDto): Abruf<Preis> =
        schreibend { api.preisErfassen(artikelId, eingabe).zuModell() }

    suspend fun standortErfassen(artikelId: String, eingabe: StandortErfassenDto): Abruf<Standort> =
        schreibend { api.standortErfassen(artikelId, eingabe).zuModell() }

    suspend fun verlauf(artikelId: String): Abruf<List<Verlaufseintrag>> =
        lesend { api.verlauf(artikelId).map { it.zuModell() } }

    suspend fun kategorien(): Abruf<List<Kategorie>> =
        lesend { api.kategorien().map { it.zuModell() } }

    suspend fun gaenge(marktId: Int): Abruf<List<Gang>> =
        lesend { api.gaenge(marktId).map { it.zuModell() } }

    suspend fun artikelImGang(marktId: Int, gang: String): Abruf<List<Artikel>> =
        lesend { api.artikelImGang(marktId, gang).map { it.zuModell() }.also { merken(it) } }

    suspend fun standardMarkt(): Abruf<Markt> = lesend { api.standardMarkt().zuModell() }

    private suspend fun <T> lesend(block: suspend () -> T): Abruf<T> = try {
        Abruf.Erfolg(block())
    } catch (fehler: IOException) {
        offlineFehler(fehler)
    } catch (fehler: HttpException) {
        Abruf.Fehler(fehlermeldung(fehler))
    }

    /**
     * Schreibvorgänge brauchen Netz. Ein stiller lokaler Fallback wäre hier falsch: der
     * Nutzer muss wissen, ob sein Preis angekommen ist.
     */
    private suspend fun <T> schreibend(block: suspend () -> T): Abruf<T> = try {
        Abruf.Erfolg(block())
    } catch (fehler: IOException) {
        Abruf.Fehler(
            "Keine Verbindung zum Server — die Eingabe wurde nicht gespeichert.",
            offline = true,
        )
    } catch (fehler: HttpException) {
        Abruf.Fehler(fehlermeldung(fehler))
    }

    private suspend fun merken(artikel: List<Artikel>) {
        if (artikel.isEmpty()) return

        val jetzt = System.currentTimeMillis()
        cache.merken(artikel.map { it.zuCacheEintrag(jetzt) })
        cache.aufraeumen()
    }

    private fun offlineFehler(fehler: IOException): Abruf.Fehler = Abruf.Fehler(
        meldung = "Keine Verbindung zum Server (${fehler.message ?: "Netzwerkfehler"}).",
        offline = true,
    )

    /**
     * Holt die Klartextmeldung aus den Problem Details der API. Die sind auf Deutsch und
     * erklären dem Nutzer den Fall genauer als ein Statuscode ("EAN gehört bereits zu
     * einem anderen Artikel").
     */
    private fun fehlermeldung(fehler: HttpException): String {
        val rumpf = runCatching { fehler.response()?.errorBody()?.string() }.getOrNull()
        val detail = rumpf
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<ProblemDetailsDto>(it) }.getOrNull() }
            ?.detail

        return detail ?: "Die Anfrage ist fehlgeschlagen (HTTP ${fehler.code()})."
    }
}
