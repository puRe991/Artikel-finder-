using System.ComponentModel.DataAnnotations;
using ArtikelFinder.Shared.Enums;

namespace ArtikelFinder.Shared.Dtos;

/// <summary>Kompakte Artikeldarstellung fuer Trefferlisten.</summary>
public sealed record ArtikelListeDto
{
    public required Guid Id { get; init; }
    public required string Name { get; init; }
    public string? Marke { get; init; }
    public string? Ean { get; init; }
    public string? Artikelnummer { get; init; }
    public int? KategorieId { get; init; }
    public string? KategorieName { get; init; }
    public string? BildUrl { get; init; }

    /// <summary>Aktuellster Preis fuer den angefragten Markt, falls erfasst.</summary>
    public PreisDto? AktuellerPreis { get; init; }

    /// <summary>Zuletzt erfasster Standort im angefragten Markt, falls bekannt.</summary>
    public StandortDto? Standort { get; init; }
}

/// <summary>Vollstaendiger Artikel inklusive Preis- und Standorthistorie.</summary>
public sealed record ArtikelDetailDto
{
    public required Guid Id { get; init; }
    public required string Name { get; init; }
    public string? Marke { get; init; }
    public string? Ean { get; init; }
    public string? Artikelnummer { get; init; }
    public int? KategorieId { get; init; }
    public string? KategorieName { get; init; }
    public string? BildUrl { get; init; }
    public required Erstellerquelle ErstelltVon { get; init; }
    public required DateTimeOffset ErstelltAm { get; init; }
    public DateTimeOffset? GeaendertAm { get; init; }

    public IReadOnlyList<PreisDto> Preise { get; init; } = [];
    public IReadOnlyList<StandortDto> Standorte { get; init; } = [];
}

/// <summary>Anlage eines Artikels. Preis und Standort sind optional mitlieferbar.</summary>
public sealed record ArtikelAnlegenDto
{
    [Required, StringLength(300, MinimumLength = 2)]
    public required string Name { get; init; }

    [StringLength(120)]
    public string? Marke { get; init; }

    /// <summary>EAN-8 / EAN-13 / UPC, nur Ziffern.</summary>
    [RegularExpression(@"^\d{8,14}$", ErrorMessage = "EAN muss aus 8 bis 14 Ziffern bestehen.")]
    public string? Ean { get; init; }

    [StringLength(60)]
    public string? Artikelnummer { get; init; }

    public int? KategorieId { get; init; }

    [StringLength(1000)]
    public string? BildUrl { get; init; }

    /// <summary>Optionaler Erstpreis, damit Scannen + Erfassen ein Aufruf bleibt.</summary>
    public PreisErfassenDto? Preis { get; init; }

    /// <summary>Optionaler Erststandort.</summary>
    public StandortErfassenDto? Standort { get; init; }
}

/// <summary>Aenderung der Artikelstammdaten. Preise/Standorte laufen ueber eigene Endpunkte.</summary>
public sealed record ArtikelAendernDto
{
    [Required, StringLength(300, MinimumLength = 2)]
    public required string Name { get; init; }

    [StringLength(120)]
    public string? Marke { get; init; }

    [RegularExpression(@"^\d{8,14}$", ErrorMessage = "EAN muss aus 8 bis 14 Ziffern bestehen.")]
    public string? Ean { get; init; }

    [StringLength(60)]
    public string? Artikelnummer { get; init; }

    public int? KategorieId { get; init; }

    [StringLength(1000)]
    public string? BildUrl { get; init; }
}
