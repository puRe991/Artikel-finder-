using System.ComponentModel.DataAnnotations;
using ArtikelFinder.Shared.Enums;

namespace ArtikelFinder.Shared.Dtos;

public sealed record KategorieDto
{
    public required int Id { get; init; }
    public required string Name { get; init; }
    public int? ParentKategorieId { get; init; }

    /// <summary>Voller Pfad, z.B. "Lebensmittel &gt; Molkereiprodukte &gt; Joghurt".</summary>
    public required string Pfad { get; init; }
}

public sealed record KategorieAnlegenDto
{
    [Required, StringLength(150, MinimumLength = 2)]
    public required string Name { get; init; }

    public int? ParentKategorieId { get; init; }
}

public sealed record MarktDto
{
    public required int Id { get; init; }
    public required string Name { get; init; }
    public required string Kette { get; init; }
    public string? Ort { get; init; }
    public string? Strasse { get; init; }

    /// <summary>Pfad/URL zum Grundriss-SVG. Phase 2.</summary>
    public string? GrundrissUrl { get; init; }
}

/// <summary>Ein Gang im Markt mit der Zahl der dort erfassten Artikel.</summary>
public sealed record GangDto
{
    public required string Gang { get; init; }
    public required int AnzahlArtikel { get; init; }
}

/// <summary>Ein Eintrag im Aenderungsverlauf.</summary>
public sealed record VerlaufEintragDto
{
    public required long Id { get; init; }
    public required Guid ArtikelId { get; init; }

    /// <summary>Betroffene Entitaet: "Artikel", "Preis" oder "Standort".</summary>
    public required string Entitaet { get; init; }

    public required Aenderungsart Aenderungsart { get; init; }

    /// <summary>Kurzbeschreibung der Aenderung, z.B. "Preis 1,49 -&gt; 1,29".</summary>
    public required string Beschreibung { get; init; }

    public string? GeaendertVon { get; init; }
    public required DateTimeOffset GeaendertAm { get; init; }
}
