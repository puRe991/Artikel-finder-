namespace ArtikelFinder.Api.Services;

public sealed record ArtikelSuchfilter
{
    /// <summary>Freitext ueber Name und Marke.</summary>
    public string? Suchbegriff { get; init; }

    /// <summary>Exakte EAN. Schlaegt Suchbegriff und Kategorie.</summary>
    public string? Ean { get; init; }

    /// <summary>Kategorie inklusive aller Unterkategorien.</summary>
    public int? KategorieId { get; init; }

    /// <summary>Nur Artikel, fuer die im Markt ein Standort erfasst ist.</summary>
    public bool NurMitStandort { get; init; }

    /// <summary>Nur Artikel mit aktuell laufendem Werbepreis.</summary>
    public bool NurMitWerbepreis { get; init; }

    public int MarktId { get; init; }
    public int Seite { get; init; } = 1;
    public int Seitengroesse { get; init; } = 25;
}
