namespace ArtikelFinder.Api.Entities;

/// <summary>
/// Wo der Artikel in einem bestimmten Markt liegt. Wie beim Preis gilt: neue Erfassung =
/// neue Zeile, der juengste Eintrag pro (Artikel, Markt) ist der aktuelle. Umraeumaktionen
/// bleiben so nachvollziehbar.
/// </summary>
public class Standort
{
    public Guid Id { get; set; }

    public Guid ArtikelId { get; set; }
    public Artikel Artikel { get; set; } = null!;

    public int MarktId { get; set; }
    public Markt Markt { get; set; } = null!;

    /// <summary>Gangbezeichnung wie ausgeschildert, z.B. "7" oder "Obst".</summary>
    public required string Gang { get; set; }

    /// <summary>Freitext, z.B. "links, mittleres Fach".</summary>
    public string? RegalBeschreibung { get; set; }

    /// <summary>Relative X-Koordinate (0..1) auf dem Grundriss. Phase 2.</summary>
    public float? KartenX { get; set; }

    /// <summary>Relative Y-Koordinate (0..1) auf dem Grundriss. Phase 2.</summary>
    public float? KartenY { get; set; }

    public DateTimeOffset ErfasstAm { get; set; }

    public string? ErfasstVon { get; set; }
}
