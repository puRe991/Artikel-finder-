package de.artikelfinder.app.data

import de.artikelfinder.app.data.local.ArtikelCacheEintrag
import de.artikelfinder.app.data.remote.ArtikelDetailDto
import de.artikelfinder.app.data.remote.ArtikelListeDto
import de.artikelfinder.app.data.remote.GangDto
import de.artikelfinder.app.data.remote.KategorieDto
import de.artikelfinder.app.data.remote.MarktDto
import de.artikelfinder.app.data.remote.PreisDto
import de.artikelfinder.app.data.remote.StandortDto
import de.artikelfinder.app.data.remote.VerlaufEintragDto

fun PreisDto.zuModell() = Preis(
    id = id,
    preis = preis,
    werbepreis = werbepreis,
    werbepreisAktiv = werbepreisAktiv,
    werbepreisGueltigVon = werbepreisGueltigVon,
    werbepreisGueltigBis = werbepreisGueltigBis,
    erfasstAm = erfasstAm,
    erfasstVon = erfasstVon,
)

fun StandortDto.zuModell() = Standort(
    id = id,
    gang = gang,
    regalBeschreibung = regalBeschreibung,
    kartenX = kartenX,
    kartenY = kartenY,
    erfasstAm = erfasstAm,
    erfasstVon = erfasstVon,
)

fun ArtikelListeDto.zuModell() = Artikel(
    id = id,
    name = name,
    marke = marke,
    ean = ean,
    artikelnummer = artikelnummer,
    kategorieId = kategorieId,
    kategorieName = kategorieName,
    bildUrl = bildUrl,
    preis = aktuellerPreis?.zuModell(),
    standort = standort?.zuModell(),
)

fun ArtikelDetailDto.zuModell(): ArtikelDetail {
    // Die API liefert Preise und Standorte absteigend nach Erfassung — der erste Eintrag
    // ist damit der aktuelle.
    val preise = preise.map { it.zuModell() }
    val standorte = standorte.map { it.zuModell() }

    return ArtikelDetail(
        artikel = Artikel(
            id = id,
            name = name,
            marke = marke,
            ean = ean,
            artikelnummer = artikelnummer,
            kategorieId = kategorieId,
            kategorieName = kategorieName,
            bildUrl = bildUrl,
            preis = preise.firstOrNull(),
            standort = standorte.firstOrNull(),
        ),
        preise = preise,
        standorte = standorte,
        erstelltVon = erstelltVon,
    )
}

fun KategorieDto.zuModell() = Kategorie(id = id, name = name, pfad = pfad)

fun MarktDto.zuModell() = Markt(id = id, name = name, ort = ort)

fun GangDto.zuModell() = Gang(gang = gang, anzahlArtikel = anzahlArtikel)

fun VerlaufEintragDto.zuModell() = Verlaufseintrag(
    id = id,
    entitaet = entitaet,
    beschreibung = beschreibung,
    geaendertVon = geaendertVon,
    geaendertAm = geaendertAm,
)

fun Artikel.zuCacheEintrag(zeitpunkt: Long) = ArtikelCacheEintrag(
    id = id,
    name = name,
    marke = marke,
    ean = ean,
    kategorieName = kategorieName,
    bildUrl = bildUrl,
    preis = preis?.preis,
    werbepreis = preis?.werbepreis,
    werbepreisAktiv = preis?.werbepreisAktiv ?: false,
    gang = standort?.gang,
    regalBeschreibung = standort?.regalBeschreibung,
    zuletztGesehen = zeitpunkt,
)

fun ArtikelCacheEintrag.zuModell() = Artikel(
    id = id,
    name = name,
    marke = marke,
    ean = ean,
    kategorieName = kategorieName,
    bildUrl = bildUrl,
    preis = preis?.let {
        Preis(preis = it, werbepreis = werbepreis, werbepreisAktiv = werbepreisAktiv)
    },
    standort = gang?.let { Standort(gang = it, regalBeschreibung = regalBeschreibung) },
    ausCache = true,
)
