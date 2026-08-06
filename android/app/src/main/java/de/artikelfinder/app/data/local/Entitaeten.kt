package de.artikelfinder.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Das Datenmodell liegt vollständig auf dem Gerät — es gibt keinen Server.
 *
 * Die Struktur entspricht bewusst weiterhin dem Entwurf aus dem Backend: `marktId` an
 * Preis und Standort, Erfassungen werden angehängt statt überschrieben. Damit bleibt der
 * Weg zu mehreren Filialen und später zu einem Abgleich zwischen Geräten offen, ohne die
 * Datenbank umbauen zu müssen.
 */

@Entity(
    tableName = "artikel",
    indices = [
        Index(value = ["ean"], unique = true),
        Index(value = ["suchtext"]),
        Index(value = ["kategorie_id"]),
    ],
)
data class ArtikelEintrag(
    @PrimaryKey val id: String,
    val name: String,
    /** Normalisierte Fassung von Name und Marke, siehe `Suchtext.fuerIndex`. */
    val suchtext: String,
    val marke: String?,
    val ean: String?,
    val artikelnummer: String?,
    @ColumnInfo(name = "kategorie_id") val kategorieId: Int?,
    @ColumnInfo(name = "bild_url") val bildUrl: String?,
    /** "Import" für Katalogartikel, "Nutzer" für selbst angelegte. */
    @ColumnInfo(name = "erstellt_von") val erstelltVon: String,
    @ColumnInfo(name = "erstellt_am") val erstelltAm: Long,
    @ColumnInfo(name = "geaendert_am") val geaendertAm: Long?,
)

@Entity(tableName = "kategorie", indices = [Index(value = ["parent_id"])])
data class KategorieEintrag(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "parent_id") val parentId: Int?,
)

@Entity(tableName = "markt")
data class MarktEintrag(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val kette: String,
    val ort: String?,
)

@Entity(
    tableName = "preis",
    foreignKeys = [ForeignKey(
        entity = ArtikelEintrag::class,
        parentColumns = ["id"],
        childColumns = ["artikel_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["artikel_id", "markt_id", "erfasst_am"])],
)
data class PreisEintrag(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "artikel_id") val artikelId: String,
    @ColumnInfo(name = "markt_id") val marktId: Int,
    val wert: Double,
    val werbepreis: Double?,
    @ColumnInfo(name = "werbepreis_von") val werbepreisVon: Long?,
    @ColumnInfo(name = "werbepreis_bis") val werbepreisBis: Long?,
    @ColumnInfo(name = "erfasst_am") val erfasstAm: Long,
    @ColumnInfo(name = "erfasst_von") val erfasstVon: String?,
)

@Entity(
    tableName = "standort",
    foreignKeys = [ForeignKey(
        entity = ArtikelEintrag::class,
        parentColumns = ["id"],
        childColumns = ["artikel_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["artikel_id", "markt_id", "erfasst_am"]),
        Index(value = ["markt_id", "gang"]),
    ],
)
data class StandortEintrag(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "artikel_id") val artikelId: String,
    @ColumnInfo(name = "markt_id") val marktId: Int,
    val gang: String,
    @ColumnInfo(name = "regal_beschreibung") val regalBeschreibung: String?,
    @ColumnInfo(name = "karten_x") val kartenX: Float?,
    @ColumnInfo(name = "karten_y") val kartenY: Float?,
    @ColumnInfo(name = "erfasst_am") val erfasstAm: Long,
    @ColumnInfo(name = "erfasst_von") val erfasstVon: String?,
)

@Entity(
    tableName = "verlauf",
    foreignKeys = [ForeignKey(
        entity = ArtikelEintrag::class,
        parentColumns = ["id"],
        childColumns = ["artikel_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["artikel_id", "geaendert_am"])],
)
data class VerlaufEintrag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "artikel_id") val artikelId: String,
    val entitaet: String,
    val aenderungsart: String,
    val beschreibung: String,
    @ColumnInfo(name = "geaendert_von") val geaendertVon: String?,
    @ColumnInfo(name = "geaendert_am") val geaendertAm: Long,
)

/**
 * Merkposten der App, etwa der zuletzt eingetippte Aktionszeitraum. Beim Abtippen eines
 * Prospekts gilt derselbe Zeitraum fuer alle Angebote — ihn jedes Mal neu einzugeben waere
 * die eigentliche Tipparbeit.
 */
@Entity(tableName = "merkposten")
data class MerkpostenEintrag(
    @PrimaryKey val schluessel: String,
    val wert: String,
)

/** Artikel samt aktuellem Preis und Standort — das Ergebnis der Trefferliste. */
data class ArtikelMitStand(
    @androidx.room.Embedded val artikel: ArtikelEintrag,
    @ColumnInfo(name = "kategorie_name") val kategorieName: String?,
    @ColumnInfo(name = "preis_wert") val preisWert: Double?,
    @ColumnInfo(name = "preis_werbepreis") val werbepreis: Double?,
    @ColumnInfo(name = "preis_werbepreis_von") val werbepreisVon: Long?,
    @ColumnInfo(name = "preis_werbepreis_bis") val werbepreisBis: Long?,
    @ColumnInfo(name = "standort_gang") val gang: String?,
    @ColumnInfo(name = "standort_regal") val regalBeschreibung: String?,
)
