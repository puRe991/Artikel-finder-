using ArtikelFinder.Shared.Enums;

namespace ArtikelFinder.Api.Entities;

public class Artikel
{
    public Guid Id { get; set; }

    public required string Name { get; set; }

    /// <summary>
    /// Normalisierte Fassung von Name + Marke fuer die Volltextsuche.
    /// Wird ausschliesslich im DbContext gepflegt, nie von aussen gesetzt.
    /// </summary>
    public string SuchText { get; set; } = string.Empty;

    public string? Marke { get; set; }

    /// <summary>EAN-8/13 oder UPC, nur Ziffern. Eindeutig, sofern gesetzt.</summary>
    public string? Ean { get; set; }

    /// <summary>Interne Artikelnummer des Marktes, falls auf dem Regaletikett ablesbar.</summary>
    public string? Artikelnummer { get; set; }

    public int? KategorieId { get; set; }
    public Kategorie? Kategorie { get; set; }

    public string? BildUrl { get; set; }

    public Erstellerquelle ErstelltVon { get; set; } = Erstellerquelle.Nutzer;

    public DateTimeOffset ErstelltAm { get; set; }

    public DateTimeOffset? GeaendertAm { get; set; }

    public ICollection<Preis> Preise { get; set; } = [];
    public ICollection<Standort> Standorte { get; set; } = [];
    public ICollection<Verlaufseintrag> Verlauf { get; set; } = [];
}
