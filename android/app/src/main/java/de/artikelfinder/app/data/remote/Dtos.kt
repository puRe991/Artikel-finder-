package de.artikelfinder.app.data.remote

import kotlinx.serialization.Serializable

/**
 * Gegenstücke zu den DTOs aus `ArtikelFinder.Shared`. Die Feldnamen entsprechen exakt der
 * JSON-Ausgabe der API (camelCase), damit keine Namenszuordnung nötig ist.
 */
@Serializable
data class SeitenErgebnisDto<T>(
    val eintraege: List<T> = emptyList(),
    val seite: Int = 1,
    val seitengroesse: Int = 25,
    val gesamtAnzahl: Int = 0,
    val seitenanzahl: Int = 0,
    val hatWeitere: Boolean = false,
)

@Serializable
data class ArtikelListeDto(
    val id: String,
    val name: String,
    val marke: String? = null,
    val ean: String? = null,
    val artikelnummer: String? = null,
    val kategorieId: Int? = null,
    val kategorieName: String? = null,
    val bildUrl: String? = null,
    val aktuellerPreis: PreisDto? = null,
    val standort: StandortDto? = null,
)

@Serializable
data class ArtikelDetailDto(
    val id: String,
    val name: String,
    val marke: String? = null,
    val ean: String? = null,
    val artikelnummer: String? = null,
    val kategorieId: Int? = null,
    val kategorieName: String? = null,
    val bildUrl: String? = null,
    val erstelltVon: String = "Nutzer",
    val erstelltAm: String,
    val geaendertAm: String? = null,
    val preise: List<PreisDto> = emptyList(),
    val standorte: List<StandortDto> = emptyList(),
)

@Serializable
data class PreisDto(
    val id: String,
    val artikelId: String,
    val marktId: Int,
    val preis: Double,
    val werbepreis: Double? = null,
    val werbepreisGueltigVon: String? = null,
    val werbepreisGueltigBis: String? = null,
    val erfasstAm: String,
    val erfasstVon: String? = null,
    val werbepreisAktiv: Boolean = false,
    val gueltigerPreis: Double = preis,
)

@Serializable
data class StandortDto(
    val id: String,
    val artikelId: String,
    val marktId: Int,
    val gang: String,
    val regalBeschreibung: String? = null,
    val kartenX: Float? = null,
    val kartenY: Float? = null,
    val erfasstAm: String,
    val erfasstVon: String? = null,
)

@Serializable
data class KategorieDto(
    val id: Int,
    val name: String,
    val parentKategorieId: Int? = null,
    val pfad: String,
)

@Serializable
data class MarktDto(
    val id: Int,
    val name: String,
    val kette: String,
    val ort: String? = null,
    val strasse: String? = null,
    val grundrissUrl: String? = null,
)

@Serializable
data class GangDto(
    val gang: String,
    val anzahlArtikel: Int,
)

@Serializable
data class VerlaufEintragDto(
    val id: Long,
    val artikelId: String,
    val entitaet: String,
    val aenderungsart: String,
    val beschreibung: String,
    val geaendertVon: String? = null,
    val geaendertAm: String,
)

// --- Anfragen ---

@Serializable
data class ArtikelAnlegenDto(
    val name: String,
    val marke: String? = null,
    val ean: String? = null,
    val artikelnummer: String? = null,
    val kategorieId: Int? = null,
    val bildUrl: String? = null,
    val preis: PreisErfassenDto? = null,
    val standort: StandortErfassenDto? = null,
)

@Serializable
data class ArtikelAendernDto(
    val name: String,
    val marke: String? = null,
    val ean: String? = null,
    val artikelnummer: String? = null,
    val kategorieId: Int? = null,
    val bildUrl: String? = null,
)

@Serializable
data class PreisErfassenDto(
    val marktId: Int? = null,
    val preis: Double,
    val werbepreis: Double? = null,
    val werbepreisGueltigVon: String? = null,
    val werbepreisGueltigBis: String? = null,
    val erfasstVon: String? = null,
)

@Serializable
data class StandortErfassenDto(
    val marktId: Int? = null,
    val gang: String,
    val regalBeschreibung: String? = null,
    val kartenX: Float? = null,
    val kartenY: Float? = null,
    val erfasstVon: String? = null,
)

/** Fehlerformat der API (RFC 9457 Problem Details). */
@Serializable
data class ProblemDetailsDto(
    val title: String? = null,
    val detail: String? = null,
    val status: Int? = null,
)
