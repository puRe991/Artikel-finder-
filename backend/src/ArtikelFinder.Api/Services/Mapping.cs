using ArtikelFinder.Api.Entities;
using ArtikelFinder.Shared.Dtos;

namespace ArtikelFinder.Api.Services;

/// <summary>Handgeschriebenes Mapping — bei sechs Entitaeten kostet ein Mapper-Framework
/// mehr Verstaendnis als es spart.</summary>
public static class Mapping
{
    public static PreisDto ZuDto(this Preis preis, DateTimeOffset jetzt) => new()
    {
        Id = preis.Id,
        ArtikelId = preis.ArtikelId,
        MarktId = preis.MarktId,
        Preis = preis.Wert,
        Werbepreis = preis.Werbepreis,
        WerbepreisGueltigVon = preis.WerbepreisGueltigVon,
        WerbepreisGueltigBis = preis.WerbepreisGueltigBis,
        ErfasstAm = preis.ErfasstAm,
        ErfasstVon = preis.ErfasstVon,
        WerbepreisAktiv = preis.IstWerbepreisAktiv(jetzt),
    };

    public static StandortDto ZuDto(this Standort standort) => new()
    {
        Id = standort.Id,
        ArtikelId = standort.ArtikelId,
        MarktId = standort.MarktId,
        Gang = standort.Gang,
        RegalBeschreibung = standort.RegalBeschreibung,
        KartenX = standort.KartenX,
        KartenY = standort.KartenY,
        ErfasstAm = standort.ErfasstAm,
        ErfasstVon = standort.ErfasstVon,
    };

    public static MarktDto ZuDto(this Markt markt) => new()
    {
        Id = markt.Id,
        Name = markt.Name,
        Kette = markt.Kette,
        Ort = markt.Ort,
        Strasse = markt.Strasse,
        GrundrissUrl = markt.GrundrissUrl,
    };

    public static VerlaufEintragDto ZuDto(this Verlaufseintrag eintrag) => new()
    {
        Id = eintrag.Id,
        ArtikelId = eintrag.ArtikelId,
        Entitaet = eintrag.Entitaet,
        Aenderungsart = eintrag.Aenderungsart,
        Beschreibung = eintrag.Beschreibung,
        GeaendertVon = eintrag.GeaendertVon,
        GeaendertAm = eintrag.GeaendertAm,
    };

    public static ArtikelDetailDto ZuDetailDto(this Artikel artikel, DateTimeOffset jetzt) => new()
    {
        Id = artikel.Id,
        Name = artikel.Name,
        Marke = artikel.Marke,
        Ean = artikel.Ean,
        Artikelnummer = artikel.Artikelnummer,
        KategorieId = artikel.KategorieId,
        KategorieName = artikel.Kategorie?.Name,
        BildUrl = artikel.BildUrl,
        ErstelltVon = artikel.ErstelltVon,
        ErstelltAm = artikel.ErstelltAm,
        GeaendertAm = artikel.GeaendertAm,
        Preise = [.. artikel.Preise.OrderByDescending(p => p.ErfasstAm).Select(p => p.ZuDto(jetzt))],
        Standorte = [.. artikel.Standorte.OrderByDescending(s => s.ErfasstAm).Select(s => s.ZuDto())],
    };
}
