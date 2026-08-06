package de.artikelfinder.app.data

/**
 * Modelle für die UI. Getrennt von den Netzwerk-DTOs, damit ein Bildschirm nicht merkt,
 * ob seine Daten vom Server oder aus dem Offline-Cache kommen.
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
    /** true, wenn die Daten aus dem lokalen Cache stammen und veraltet sein können. */
    val ausCache: Boolean = false,
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
    val werbepreisGueltigVon: String? = null,
    val werbepreisGueltigBis: String? = null,
    val erfasstAm: String = "",
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
    val erfasstAm: String = "",
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
    val ort: String? = null,
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
    val geaendertAm: String,
)
