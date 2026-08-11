package de.artikelfinder.app.data

/**
 * Modelle für die UI. Zeitpunkte sind Millisekunden seit 1970 — die Daten kommen aus der
 * lokalen Datenbank, es gibt keine ISO-Zeitstempel eines Servers mehr zu zerlegen.
 */
data class Artikel(
    val id: String,
    val name: String,
    val marke: String? = null,
    val ean: String? = null,
    val artikelnummer: String? = null,
    val kategorieId: Int? = null,
    val kategorieName: String? = null,
    val bildUrl: String? = null,
    val preis: Preis? = null,
    val standort: Standort? = null,
)

data class ArtikelDetail(
    val artikel: Artikel,
    val preise: List<Preis> = emptyList(),
    val standorte: List<Standort> = emptyList(),
    val erstelltVon: String = "Nutzer",
)

data class Preis(
    val id: String = "",
    val preis: Double,
    val werbepreis: Double? = null,
    val werbepreisAktiv: Boolean = false,
    val werbepreisGueltigVon: Long? = null,
    val werbepreisGueltigBis: Long? = null,
    val erfasstAm: Long = 0,
    val erfasstVon: String? = null,
) {
    /** Was der Kunde heute zahlt. */
    val gueltigerPreis: Double get() = if (werbepreisAktiv && werbepreis != null) werbepreis else preis
}

data class Standort(
    val id: String = "",
    val gang: String,
    val regalBeschreibung: String? = null,
    val kartenX: Float? = null,
    val kartenY: Float? = null,
    val erfasstAm: Long = 0,
    val erfasstVon: String? = null,
)

data class Kategorie(
    val id: Int,
    val name: String,
    val pfad: String,
)

data class Markt(
    val id: Int,
    val name: String,
    /** Handelskette, z.B. "Kaufland" oder "OBI". */
    val kette: String,
    /** Abschnitt der Marktauswahl, siehe [Marktkatalog]. */
    val kategorie: String,
    val ort: String? = null,
)

/** Ein Abschnitt der Marktauswahl: eine Kategorie und die Märkte, die zu ihr gehören. */
data class Marktgruppe(
    val kategorie: String,
    val maerkte: List<Markt>,
)

data class Gang(
    val gang: String,
    val anzahlArtikel: Int,
)

data class Verlaufseintrag(
    val id: Long,
    val entitaet: String,
    val beschreibung: String,
    val geaendertVon: String?,
    val geaendertAm: Long,
)
