package de.artikelfinder.app.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import de.artikelfinder.app.data.local.ArtikelCacheDao
import de.artikelfinder.app.data.local.ArtikelCacheEintrag
import de.artikelfinder.app.data.remote.ArtikelApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ArtikelRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ArtikelRepository
    private lateinit var cache: CacheAttrappe

    @Before
    fun aufbauen() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ArtikelApi::class.java)

        cache = CacheAttrappe()
        repository = ArtikelRepository(api, cache)
    }

    @After
    fun abbauen() {
        server.shutdown()
    }

    @Test
    fun `EAN-Lookup liefert den Artikel und legt ihn in den Cache`() = runTest {
        server.enqueue(jsonAntwort(ARTIKEL_JSON))

        val ergebnis = repository.perEan("4008400202990")

        assertTrue(ergebnis is Abruf.Erfolg)
        val detail = (ergebnis as Abruf.Erfolg).wert
        assertEquals("Bio Vollmilch", detail?.artikel?.name)
        assertEquals(1.29, detail?.artikel?.preis?.gueltigerPreis!!, 0.0001)

        // Der gescannte Artikel muss offline verfügbar bleiben.
        assertEquals(1, cache.gemerkt.size)
        assertEquals("Bio Vollmilch", cache.gemerkt.single().name)
    }

    @Test
    fun `Unbekannte EAN ist Erfolg mit null, kein Fehler`() = runTest {
        // 404 ist beim Scannen der Auslöser für "Artikel anlegen", nicht für eine Fehlermeldung.
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"unbekannt"}"""))

        val ergebnis = repository.perEan("4000000000000")

        assertTrue(ergebnis is Abruf.Erfolg)
        assertNull((ergebnis as Abruf.Erfolg).wert)
    }

    @Test
    fun `Konflikt beim Anlegen zeigt die Klartextmeldung der API`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"title":"Conflict","detail":"Zur EAN 4008400202990 existiert bereits ein Artikel.","status":409}""")
        )

        val ergebnis = repository.anlegen(
            de.artikelfinder.app.data.remote.ArtikelAnlegenDto(name = "Dublette", ean = "4008400202990")
        )

        assertTrue(ergebnis is Abruf.Fehler)
        assertEquals(
            "Zur EAN 4008400202990 existiert bereits ein Artikel.",
            (ergebnis as Abruf.Fehler).meldung,
        )
    }

    @Test
    fun `Suche faellt bei Netzfehler auf den Cache zurueck`() = runTest {
        server.shutdown() // simuliert "kein Netz"
        cache.eintraege += cacheEintrag("Vollmilch")

        val ergebnis = repository.suchen("voll")

        assertTrue(ergebnis is Abruf.Erfolg)
        val erfolg = ergebnis as Abruf.Erfolg
        assertTrue("Cache-Treffer müssen als solche gekennzeichnet sein", erfolg.ausCache)
        assertEquals("Vollmilch", erfolg.wert.single().name)
    }

    @Test
    fun `Leerer Cache bei Netzfehler meldet offline`() = runTest {
        server.shutdown()

        val ergebnis = repository.suchen("voll")

        assertTrue(ergebnis is Abruf.Fehler)
        assertTrue((ergebnis as Abruf.Fehler).offline)
    }

    @Test
    fun `Schreibvorgang ohne Netz wird nicht still verschluckt`() = runTest {
        server.shutdown()

        val ergebnis = repository.preisErfassen(
            "1",
            de.artikelfinder.app.data.remote.PreisErfassenDto(preis = 1.49),
        )

        // Der Nutzer muss erfahren, dass der Preis nicht angekommen ist.
        assertTrue(ergebnis is Abruf.Fehler)
        assertTrue((ergebnis as Abruf.Fehler).offline)
        assertTrue(ergebnis.meldung.contains("nicht gespeichert"))
    }

    private fun jsonAntwort(rumpf: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(rumpf)

    private fun cacheEintrag(name: String) = ArtikelCacheEintrag(
        id = "1", name = name, marke = null, ean = null, kategorieName = null, bildUrl = null,
        preis = 1.49, werbepreis = null, werbepreisAktiv = false, gang = "7",
        regalBeschreibung = null, zuletztGesehen = 0,
    )

    /** Einfache Attrappe statt Room — hier wird das Repository getestet, nicht die Datenbank. */
    private class CacheAttrappe : ArtikelCacheDao {
        val eintraege = mutableListOf<ArtikelCacheEintrag>()
        val gemerkt = mutableListOf<ArtikelCacheEintrag>()

        override fun zuletztGesehen(limit: Int): Flow<List<ArtikelCacheEintrag>> = flowOf(eintraege)

        override suspend fun suchen(begriff: String, limit: Int): List<ArtikelCacheEintrag> =
            eintraege.filter { it.name.contains(begriff, ignoreCase = true) }

        override suspend fun holen(id: String): ArtikelCacheEintrag? = eintraege.find { it.id == id }

        override suspend fun perEan(ean: String): ArtikelCacheEintrag? = eintraege.find { it.ean == ean }

        override suspend fun merken(eintraege: List<ArtikelCacheEintrag>) {
            gemerkt += eintraege
        }

        override suspend fun aufraeumen(behalten: Int) = Unit
    }

    private companion object {
        const val ARTIKEL_JSON = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Bio Vollmilch",
          "marke": "Müller",
          "ean": "4008400202990",
          "erstelltVon": "Nutzer",
          "erstelltAm": "2026-08-06T10:00:00+00:00",
          "preise": [{
            "id": "22222222-2222-2222-2222-222222222222",
            "artikelId": "11111111-1111-1111-1111-111111111111",
            "marktId": 1,
            "preis": 1.49,
            "werbepreis": 1.29,
            "erfasstAm": "2026-08-06T10:00:00+00:00",
            "werbepreisAktiv": true,
            "gueltigerPreis": 1.29
          }],
          "standorte": [{
            "id": "33333333-3333-3333-3333-333333333333",
            "artikelId": "11111111-1111-1111-1111-111111111111",
            "marktId": 1,
            "gang": "7",
            "erfasstAm": "2026-08-06T10:00:00+00:00"
          }]
        }
        """
    }
}
