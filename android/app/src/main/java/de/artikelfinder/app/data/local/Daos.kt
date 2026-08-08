package de.artikelfinder.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Die Abfragen bilden dieselbe Regel ab wie zuvor der Server: je Artikel und Markt zählt
 * die jüngste Preis- bzw. Standorterfassung. Umgeräumte Artikel tauchen dadurch nicht mehr
 * im alten Gang auf, und die Historie bleibt trotzdem erhalten.
 */
private const val AKTUELLER_STAND = """
    SELECT a.*,
           k.name AS kategorie_name,
           p.wert AS preis_wert,
           p.werbepreis AS preis_werbepreis,
           p.werbepreis_von AS preis_werbepreis_von,
           p.werbepreis_bis AS preis_werbepreis_bis,
           s.gang AS standort_gang,
           s.regal_beschreibung AS standort_regal
    FROM artikel a
    LEFT JOIN kategorie k ON k.id = a.kategorie_id
    LEFT JOIN preis p ON p.id = (
        SELECT id FROM preis
        WHERE artikel_id = a.id AND markt_id = :marktId
        ORDER BY erfasst_am DESC LIMIT 1
    )
    LEFT JOIN standort s ON s.id = (
        SELECT id FROM standort
        WHERE artikel_id = a.id AND markt_id = :marktId
        ORDER BY erfasst_am DESC LIMIT 1
    )
"""

/**
 * Eigenmarken stehen nur in den Läden ihrer Kette. Ein Artikel ist sichtbar, wenn er keiner
 * Kette gehört (Herstellermarken stehen überall) oder zur Kette des gewählten Markts.
 *
 * Ist kein Markt gewählt oder die Zuordnung abgeschaltet, hebt `:fremdeZeigen` die
 * Bedingung auf — lieber ein fremder Artikel zu viel als ein eigener zu wenig.
 */
private const val OHNE_FREMDE_EIGENMARKEN = """
    :fremdeZeigen = 1
    OR a.eigenmarke_kette IS NULL
    OR a.eigenmarke_kette = :kette
"""

@Dao
interface ArtikelDao {

    @Query("SELECT COUNT(*) FROM artikel")
    suspend fun anzahl(): Int

    @Query("$AKTUELLER_STAND WHERE a.id = :id")
    suspend fun holen(id: String, marktId: Int): ArtikelMitStand?

    @Query("$AKTUELLER_STAND WHERE a.ean = :ean LIMIT 1")
    suspend fun perEan(ean: String, marktId: Int): ArtikelMitStand?

    @Query("SELECT id FROM artikel WHERE ean = :ean LIMIT 1")
    suspend fun idPerEan(ean: String): String?

    /**
     * Volltextsuche über den normalisierten Suchtext. Bis zu drei Begriffe werden
     * UND-verknüpft; leere Begriffe sind als '%' neutral, damit eine einzige Abfrage für
     * alle Fälle reicht.
     */
    @Query(
        """
        $AKTUELLER_STAND
        WHERE (:hatSuche = 0 OR (a.suchtext LIKE :t1 AND a.suchtext LIKE :t2 AND a.suchtext LIKE :t3))
          AND (:kategorieAnzahl = 0 OR a.kategorie_id IN (:kategorieIds))
          AND (:nurMitStandort = 0 OR s.id IS NOT NULL)
          AND (:nurMitWerbepreis = 0 OR (
                p.werbepreis IS NOT NULL
                AND (p.werbepreis_von IS NULL OR p.werbepreis_von <= :jetzt)
                AND (p.werbepreis_bis IS NULL OR p.werbepreis_bis >= :jetzt)))
          AND ($OHNE_FREMDE_EIGENMARKEN)
        ORDER BY a.name COLLATE NOCASE
        LIMIT :grenze OFFSET :versatz
        """
    )
    suspend fun suchen(
        hatSuche: Int,
        t1: String,
        t2: String,
        t3: String,
        kategorieIds: List<Int>,
        kategorieAnzahl: Int,
        nurMitStandort: Int,
        nurMitWerbepreis: Int,
        fremdeZeigen: Int,
        kette: String?,
        jetzt: Long,
        marktId: Int,
        grenze: Int,
        versatz: Int,
    ): List<ArtikelMitStand>

    @Query(
        """
        SELECT COUNT(*) FROM artikel a
        LEFT JOIN preis p ON p.id = (
            SELECT id FROM preis WHERE artikel_id = a.id AND markt_id = :marktId
            ORDER BY erfasst_am DESC LIMIT 1)
        LEFT JOIN standort s ON s.id = (
            SELECT id FROM standort WHERE artikel_id = a.id AND markt_id = :marktId
            ORDER BY erfasst_am DESC LIMIT 1)
        WHERE (:hatSuche = 0 OR (a.suchtext LIKE :t1 AND a.suchtext LIKE :t2 AND a.suchtext LIKE :t3))
          AND (:kategorieAnzahl = 0 OR a.kategorie_id IN (:kategorieIds))
          AND (:nurMitStandort = 0 OR s.id IS NOT NULL)
          AND (:nurMitWerbepreis = 0 OR (
                p.werbepreis IS NOT NULL
                AND (p.werbepreis_von IS NULL OR p.werbepreis_von <= :jetzt)
                AND (p.werbepreis_bis IS NULL OR p.werbepreis_bis >= :jetzt)))
          AND ($OHNE_FREMDE_EIGENMARKEN)
        """
    )
    suspend fun anzahlTreffer(
        hatSuche: Int,
        t1: String,
        t2: String,
        t3: String,
        kategorieIds: List<Int>,
        kategorieAnzahl: Int,
        nurMitStandort: Int,
        nurMitWerbepreis: Int,
        fremdeZeigen: Int,
        kette: String?,
        jetzt: Long,
        marktId: Int,
    ): Int

    /** Zuletzt bearbeitete oder angelegte Artikel — der Einstieg ohne Suchbegriff. */
    @Query(
        """
        $AKTUELLER_STAND
        WHERE a.erstellt_von = 'Nutzer' OR a.geaendert_am IS NOT NULL
           OR p.id IS NOT NULL OR s.id IS NOT NULL
        ORDER BY COALESCE(a.geaendert_am, a.erstellt_am) DESC
        LIMIT :grenze
        """
    )
    fun zuletztBearbeitet(marktId: Int, grenze: Int = 50): Flow<List<ArtikelMitStand>>

    @Query("$AKTUELLER_STAND WHERE s.gang = :gang ORDER BY a.name COLLATE NOCASE")
    suspend fun imGang(gang: String, marktId: Int): List<ArtikelMitStand>

    @Query(
        """
        SELECT s.gang AS gang, COUNT(*) AS anzahl FROM standort s
        WHERE s.markt_id = :marktId AND s.id = (
            SELECT id FROM standort WHERE artikel_id = s.artikel_id AND markt_id = :marktId
            ORDER BY erfasst_am DESC LIMIT 1)
        GROUP BY s.gang
        """
    )
    suspend fun gaenge(marktId: Int): List<GangZeile>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun einfuegen(artikel: ArtikelEintrag)

    @Update
    suspend fun aktualisieren(artikel: ArtikelEintrag)

    @Query("DELETE FROM artikel WHERE id = :id")
    suspend fun loeschen(id: String)

    @Query("SELECT * FROM artikel WHERE id = :id")
    suspend fun roh(id: String): ArtikelEintrag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun stapelEinfuegen(artikel: List<ArtikelEintrag>)

    /** Selbst angelegte Artikel — nur die gehören in eine Sicherung. */
    @Query("SELECT * FROM artikel WHERE erstellt_von = :quelle ORDER BY erstellt_am")
    suspend fun nachQuelle(quelle: String): List<ArtikelEintrag>

    /**
     * Die EAN zu einer Reihe von Ids. Sie ist der einzige über Geräte hinweg stabile
     * Schlüssel: die Artikel-Id vergibt der Katalogaufbau bei jeder Installation neu.
     */
    @Query("SELECT id, ean FROM artikel WHERE id IN (:ids)")
    suspend fun eanZuIds(ids: List<String>): List<IdUndEan>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun einfuegenWennNeu(artikel: ArtikelEintrag): Long

    // --- Markenzuordnung ---

    /**
     * Die Schreibweisen, wie sie im Katalog stehen. „K-Classic" kommt in sechs Fassungen
     * vor; welche davon zur selben Marke gehören, entscheidet erst die Normalisierung in
     * Kotlin — SQLite kann sie nicht.
     */
    @Query("SELECT DISTINCT marke FROM artikel WHERE marke IS NOT NULL AND marke != ''")
    suspend fun alleMarken(): List<String>

    @Query("UPDATE artikel SET eigenmarke_kette = :kette WHERE marke IN (:marken)")
    suspend fun eigenmarkeSetzen(kette: String, marken: List<String>)

    @Query("UPDATE artikel SET eigenmarke_kette = NULL")
    suspend fun eigenmarkenLeeren()

    @Query("SELECT COUNT(*) FROM artikel WHERE eigenmarke_kette IS NOT NULL AND eigenmarke_kette != :kette")
    suspend fun anzahlFremderEigenmarken(kette: String): Int

    /**
     * Laufende Angebote, das am schnellsten ablaufende zuerst. Angebote ohne Enddatum
     * stehen hinten — sie laufen bis auf Weiteres.
     */
    @Query(
        """
        $AKTUELLER_STAND
        WHERE p.werbepreis IS NOT NULL
          AND (p.werbepreis_von IS NULL OR p.werbepreis_von <= :jetzt)
          AND (p.werbepreis_bis IS NULL OR p.werbepreis_bis >= :jetzt)
        ORDER BY COALESCE(p.werbepreis_bis, 9223372036854775807), a.name COLLATE NOCASE
        """
    )
    fun aktiveAngebote(marktId: Int, jetzt: Long): Flow<List<ArtikelMitStand>>
}

data class GangZeile(val gang: String, val anzahl: Int)

data class IdUndEan(val id: String, val ean: String?)

@Dao
interface PreisDao {
    @Insert
    suspend fun einfuegen(preis: PreisEintrag)

    @Query("SELECT * FROM preis WHERE artikel_id = :artikelId ORDER BY erfasst_am DESC")
    suspend fun fuerArtikel(artikelId: String): List<PreisEintrag>

    @Query(
        """
        SELECT * FROM preis WHERE artikel_id = :artikelId AND markt_id = :marktId
        ORDER BY erfasst_am DESC LIMIT 1
        """
    )
    suspend fun aktuellster(artikelId: String, marktId: Int): PreisEintrag?

    @Query("SELECT * FROM preis ORDER BY erfasst_am")
    suspend fun alle(): List<PreisEintrag>

    /**
     * Beim Einspielen einer Sicherung. Die Id kommt aus der Datei: derselbe Datensatz
     * zweimal einzuspielen soll nichts verändern, deshalb IGNORE statt REPLACE. Das
     * Ergebnis sagt je Zeile, ob sie neu war (Zeilennummer) oder schon dastand (-1).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun einfuegenWennNeu(preise: List<PreisEintrag>): List<Long>
}

@Dao
interface StandortDao {
    @Insert
    suspend fun einfuegen(standort: StandortEintrag)

    @Query("SELECT * FROM standort WHERE artikel_id = :artikelId ORDER BY erfasst_am DESC")
    suspend fun fuerArtikel(artikelId: String): List<StandortEintrag>

    @Query("SELECT * FROM standort ORDER BY erfasst_am")
    suspend fun alle(): List<StandortEintrag>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun einfuegenWennNeu(standorte: List<StandortEintrag>): List<Long>
}

@Dao
interface VerlaufDao {
    @Insert
    suspend fun einfuegen(eintrag: VerlaufEintrag)

    @Query("SELECT * FROM verlauf WHERE artikel_id = :artikelId ORDER BY geaendert_am DESC, id DESC")
    suspend fun fuerArtikel(artikelId: String): List<VerlaufEintrag>

    @Query("SELECT * FROM verlauf ORDER BY geaendert_am")
    suspend fun alle(): List<VerlaufEintrag>

    /** Die Id vergibt die Datenbank; doppelte Einträge fängt der Aufrufer selbst ab. */
    @Insert
    suspend fun einfuegen(eintraege: List<VerlaufEintrag>)
}

@Dao
interface MerkpostenDao {
    @Query("SELECT wert FROM merkposten WHERE schluessel = :schluessel")
    suspend fun lesen(schluessel: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun schreiben(eintrag: MerkpostenEintrag)

    @Query("SELECT * FROM merkposten")
    suspend fun alle(): List<MerkpostenEintrag>
}

@Dao
interface StammdatenDao {
    @Query("SELECT * FROM kategorie ORDER BY name")
    suspend fun kategorien(): List<KategorieEintrag>

    @Query("SELECT COUNT(*) FROM kategorie")
    suspend fun anzahlKategorien(): Int

    @Insert
    suspend fun kategorieEinfuegen(kategorie: KategorieEintrag): Long

    @Query("SELECT * FROM markt ORDER BY name")
    suspend fun maerkte(): List<MarktEintrag>

    @Query("SELECT * FROM markt ORDER BY name")
    fun maerkteStrom(): Flow<List<MarktEintrag>>

    @Query("SELECT * FROM markt WHERE id = :id")
    suspend fun markt(id: Int): MarktEintrag?

    @Query("SELECT COUNT(*) FROM markt")
    suspend fun anzahlMaerkte(): Int

    @Insert
    suspend fun marktEinfuegen(markt: MarktEintrag): Long

    @Update
    suspend fun marktAktualisieren(markt: MarktEintrag)

    @Query("DELETE FROM markt WHERE id = :id")
    suspend fun marktLoeschen(id: Int)

    @Transaction
    suspend fun kategorienAnlegen(eintraege: List<KategorieEintrag>): List<Long> =
        eintraege.map { kategorieEinfuegen(it) }
}
