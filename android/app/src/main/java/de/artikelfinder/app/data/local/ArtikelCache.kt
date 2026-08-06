package de.artikelfinder.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Lokaler Zwischenspeicher der zuletzt gesehenen Artikel.
 *
 * Bewusst eine flache Tabelle statt einer Spiegelung des Servermodells: im Markt zählt,
 * dass Name, Preis und Gang auch ohne Empfang angezeigt werden — und Kaufland-Hallen sind
 * genau dort schlecht versorgt, wo man die Information braucht. Erfassungen laufen weiter
 * online; eine Offline-Warteschlange wäre erst mit dem Mehrbenutzerbetrieb sinnvoll,
 * weil dann Konflikte aufgelöst werden müssen.
 */
@Entity(tableName = "artikel_cache")
data class ArtikelCacheEintrag(
    @PrimaryKey val id: String,
    val name: String,
    val marke: String?,
    val ean: String?,
    val kategorieName: String?,
    val bildUrl: String?,
    val preis: Double?,
    val werbepreis: Double?,
    @ColumnInfo(name = "werbepreis_aktiv") val werbepreisAktiv: Boolean,
    val gang: String?,
    @ColumnInfo(name = "regal_beschreibung") val regalBeschreibung: String?,
    @ColumnInfo(name = "zuletzt_gesehen") val zuletztGesehen: Long,
)

@Dao
interface ArtikelCacheDao {

    /** Zuletzt gesehene Artikel, neueste zuerst. Speist die Startseite ohne Netz. */
    @Query("SELECT * FROM artikel_cache ORDER BY zuletzt_gesehen DESC LIMIT :limit")
    fun zuletztGesehen(limit: Int = 50): Flow<List<ArtikelCacheEintrag>>

    @Query(
        """
        SELECT * FROM artikel_cache
        WHERE name LIKE '%' || :begriff || '%' COLLATE NOCASE
           OR marke LIKE '%' || :begriff || '%' COLLATE NOCASE
           OR ean = :begriff
        ORDER BY zuletzt_gesehen DESC
        LIMIT :limit
        """
    )
    suspend fun suchen(begriff: String, limit: Int = 50): List<ArtikelCacheEintrag>

    @Query("SELECT * FROM artikel_cache WHERE id = :id")
    suspend fun holen(id: String): ArtikelCacheEintrag?

    @Query("SELECT * FROM artikel_cache WHERE ean = :ean LIMIT 1")
    suspend fun perEan(ean: String): ArtikelCacheEintrag?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun merken(eintraege: List<ArtikelCacheEintrag>)

    /** Hält den Cache klein — mehr als ein paar hundert Artikel sieht niemand wieder an. */
    @Query(
        """
        DELETE FROM artikel_cache WHERE id NOT IN (
            SELECT id FROM artikel_cache ORDER BY zuletzt_gesehen DESC LIMIT :behalten
        )
        """
    )
    suspend fun aufraeumen(behalten: Int = 500)
}

@Database(entities = [ArtikelCacheEintrag::class], version = 1, exportSchema = false)
abstract class ArtikelDatenbank : RoomDatabase() {
    abstract fun artikelCacheDao(): ArtikelCacheDao
}
