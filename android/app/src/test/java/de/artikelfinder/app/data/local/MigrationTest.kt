package de.artikelfinder.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Wacht über den Weg von einer alten Datenbank zur aktuellen.
 *
 * Der Artikelkatalog ist ersetzbar — er steht in den Assets. Die selbst erfassten Preise,
 * Standorte und Gänge sind es nicht: sie entstehen nur im Laden, Artikel für Artikel. Eine
 * vergessene Migration wirft genau die weg, und zwar erst auf dem Handy des Nutzers beim
 * übernächsten Update. Deshalb wird der Aufstieg hier bei jedem Testlauf nachgespielt.
 *
 * Der Test baut die älteste Datenbank aus der eingecheckten Schemabeschreibung nach — nicht
 * aus den heutigen Entitäten. Nur so prüft er wirklich den Aufstieg und nicht bloß sich
 * selbst.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var schemaOrdner: File

    @Before
    fun schemataFinden() {
        val ordner = javaClass.classLoader?.getResource(SCHEMA_ORDNER)
        assertNotNull(
            "Im Klassenpfad liegt kein Ordner '$SCHEMA_ORDNER'. Room exportiert die " +
                "Schemata nach app/schemas — steht 'room.schemaLocation' in der " +
                "build.gradle.kts und ist der Ordner eingecheckt?",
            ordner,
        )
        schemaOrdner = File(ordner!!.toURI())
    }

    @Test
    fun `Zu jeder Schemaversion liegt eine Datei im Repository`() {
        val vorhanden = schemaOrdner.listFiles { datei -> datei.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()

        assertEquals(
            "Erwartet werden die Schemadateien 1..${ArtikelDatenbank.VERSION}. Nach einer " +
                "Versionserhöhung einmal bauen und die neue JSON-Datei mit einchecken.",
            (1..ArtikelDatenbank.VERSION).toList(),
            vorhanden,
        )
    }

    @Test
    fun `Jeder Versionsschritt hat genau eine Migration`() {
        val schritte = MIGRATIONEN.map { it.startVersion to it.endVersion }.sortedBy { it.first }
        val erwartet = (1 until ArtikelDatenbank.VERSION).map { it to it + 1 }

        // Deckt beide Richtungen ab: eine fehlende Migration zu einer erhöhten Version
        // ebenso wie eine Migration, die auf eine Version zeigt, die es nicht gibt.
        assertEquals(
            "Zu jeder Version über 1 gehört eine Migration von der Vorgängerversion. " +
                "Fehlt sie, stürzt die App beim Update mit \"A migration from … was " +
                "required but not found\" ab.",
            erwartet,
            schritte,
        )
    }

    @Test
    fun `Migrationen fuehren erfasste Daten von der aeltesten Version bis zur aktuellen`() = runTest {
        val datei = context.getDatabasePath(TESTDATENBANK)
        datei.parentFile?.mkdirs()
        datei.delete()

        alteDatenbankAnlegen(datei, schema(ALTESTE_VERSION))

        val datenbank = Room
            .databaseBuilder(context, ArtikelDatenbank::class.java, TESTDATENBANK)
            .addMigrations(*MIGRATIONEN)
            .allowMainThreadQueries()
            .build()

        try {
            // Öffnet die Datenbank und lässt Room die Migrationen fahren. Passt das Schema
            // danach nicht zu den Entitäten, bricht Room genau hier ab — dieselbe Prüfung,
            // die sonst der Nutzer beim App-Start auslöst.
            datenbank.openHelper.writableDatabase

            val artikel = datenbank.artikelDao().roh(ARTIKEL_ID)
            assertNotNull("Der erfasste Artikel hat die Migration nicht überlebt", artikel)
            assertEquals("Handerfasste Vollmilch", artikel!!.name)

            val preise = datenbank.preisDao().fuerArtikel(ARTIKEL_ID)
            assertEquals("Die Preishistorie ist unvollständig", 2, preise.size)
            assertEquals(1.59, preise.first().wert, 0.001)
            assertEquals(1.29, preise.first().werbepreis!!, 0.001)

            val standorte = datenbank.standortDao().fuerArtikel(ARTIKEL_ID)
            assertEquals(1, standorte.size)
            assertEquals("7", standorte.first().gang)

            val verlauf = datenbank.verlaufDao().fuerArtikel(ARTIKEL_ID)
            assertTrue("Der Änderungsverlauf ist verloren gegangen", verlauf.isNotEmpty())
        } finally {
            datenbank.close()
            datei.delete()
        }
    }

    /**
     * Baut die Datenbank so nach, wie sie in der angegebenen Version auf dem Gerät liegt:
     * Tabellen und Indizes aus der Schemabeschreibung, dazu Rooms eigene Verwaltungstabelle
     * mit dem Prüfwert der Version und die Versionsnummer in `PRAGMA user_version`. Genau
     * daran erkennt Room beim Öffnen, dass migriert werden muss.
     */
    private fun alteDatenbankAnlegen(datei: File, schema: JSONObject) {
        val db = SQLiteDatabase.openOrCreateDatabase(datei, null)

        try {
            val entitaeten = schema.getJSONArray("entities")
            for (i in 0 until entitaeten.length()) {
                val entitaet = entitaeten.getJSONObject(i)
                val tabelle = entitaet.getString("tableName")

                db.execSQL(entitaet.getString("createSql").fuerTabelle(tabelle))

                val indizes = entitaet.optJSONArray("indices")
                for (j in 0 until (indizes?.length() ?: 0)) {
                    db.execSQL(indizes!!.getJSONObject(j).getString("createSql").fuerTabelle(tabelle))
                }
            }

            val sichten = schema.optJSONArray("views")
            for (i in 0 until (sichten?.length() ?: 0)) {
                db.execSQL(sichten!!.getJSONObject(i).getString("createSql"))
            }

            val verwaltung = schema.getJSONArray("setupQueries")
            for (i in 0 until verwaltung.length()) db.execSQL(verwaltung.getString(i))

            erfassteDatenAnlegen(db)
            db.version = schema.getInt("version")
        } finally {
            db.close()
        }
    }

    /**
     * Was ein Nutzer im Laden zusammengetragen hat, in den Spalten der ältesten Version.
     * Bewusst als handgeschriebenes SQL und nicht über die heutigen DAOs: die Daten sollen
     * so aussehen wie damals, sonst prüft der Test den Aufstieg nicht.
     */
    private fun erfassteDatenAnlegen(db: SQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO artikel (id, name, suchtext, marke, ean, artikelnummer, kategorie_id,
                                 bild_url, erstellt_von, erstellt_am, geaendert_am)
            VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, 'Nutzer', 1700000000000, NULL)
            """.trimIndent(),
            arrayOf(ARTIKEL_ID, "Handerfasste Vollmilch", "handerfasste vollmilch", "K-Classic", "4337256123456"),
        )

        db.execSQL(
            """
            INSERT INTO preis (id, artikel_id, markt_id, wert, werbepreis, werbepreis_von,
                               werbepreis_bis, erfasst_am, erfasst_von)
            VALUES ('preis-1', ?, 1, 1.49, NULL, NULL, NULL, 1700000000000, 'tobias')
            """.trimIndent(),
            arrayOf(ARTIKEL_ID),
        )

        db.execSQL(
            """
            INSERT INTO preis (id, artikel_id, markt_id, wert, werbepreis, werbepreis_von,
                               werbepreis_bis, erfasst_am, erfasst_von)
            VALUES ('preis-2', ?, 1, 1.59, 1.29, NULL, 1900000000000, 1700000001000, 'tobias')
            """.trimIndent(),
            arrayOf(ARTIKEL_ID),
        )

        db.execSQL(
            """
            INSERT INTO standort (id, artikel_id, markt_id, gang, regal_beschreibung,
                                  karten_x, karten_y, erfasst_am, erfasst_von)
            VALUES ('standort-1', ?, 1, '7', 'unten links', NULL, NULL, 1700000000000, 'tobias')
            """.trimIndent(),
            arrayOf(ARTIKEL_ID),
        )

        db.execSQL(
            """
            INSERT INTO verlauf (artikel_id, entitaet, aenderungsart, beschreibung,
                                 geaendert_von, geaendert_am)
            VALUES (?, 'Preis', 'Angelegt', 'Preis 1,49 -> 1,59 EUR', 'tobias', 1700000001000)
            """.trimIndent(),
            arrayOf(ARTIKEL_ID),
        )
    }

    private fun schema(version: Int): JSONObject {
        val datei = File(schemaOrdner, "$version.json")
        assertTrue("Die Schemadatei ${datei.name} fehlt", datei.exists())
        return JSONObject(datei.readText()).getJSONObject("database")
    }

    private fun String.fuerTabelle(tabelle: String) = replace("\${TABLE_NAME}", tabelle)

    private companion object {
        const val SCHEMA_ORDNER = "de.artikelfinder.app.data.local.ArtikelDatenbank"
        const val TESTDATENBANK = "migrationstest.db"
        const val ALTESTE_VERSION = 1
        const val ARTIKEL_ID = "11111111-2222-3333-4444-555555555555"
    }
}
