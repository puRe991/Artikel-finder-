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
    val angaben: Produktangaben = Produktangaben(),
)

/**
 * Was die App ueber einen Artikel Auskunft geben kann. Quelle ist Open Food Facts; die
 * Werte kommen fertig aufbereitet aus der Katalogdatei, damit die App nicht mit Tags und
 * Sprachvarianten hantieren muss.
 */
data class Produktangaben(
    val menge: String? = null,
    /** Kennzeichnungspflichtige Allergene, bereits auf Deutsch. */
    val allergene: List<String> = emptyList(),
    /** „Kann Spuren enthalten von …" */
    val spuren: List<String> = emptyList(),
    /** Bio, Vegan, Glutenfrei … */
    val auszeichnungen: List<String> = emptyList(),
    val naehrwerte: List<Naehrwert> = emptyList(),
    /** Nutri-Score a–e. */
    val nutriscore: String? = null,
    val zutaten: String? = null,
) {
    val hatAllergeninfo: Boolean get() = allergene.isNotEmpty() || spuren.isNotEmpty()

    val istLeer: Boolean
        get() = menge == null && allergene.isEmpty() && spuren.isEmpty() &&
            auszeichnungen.isEmpty() && naehrwerte.isEmpty() && nutriscore == null &&
            zutaten == null
}

data class Naehrwert(val bezeichnung: String, val wert: String)

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
    val geaendertAm: Long,
)
