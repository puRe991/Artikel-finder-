namespace ArtikelFinder.Api.Entities;

/// <summary>
/// Eine Filiale. Preis und Standort haengen ab Tag 1 am Markt, damit der Ausbau auf
/// weitere Filialen/Ketten (Phase 3) keine Datenmigration erzwingt.
/// </summary>
public class Markt
{
    public int Id { get; set; }

    public required string Name { get; set; }

    /// <summary>Handelskette, z.B. "Kaufland".</summary>
    public required string Kette { get; set; }

    public string? Ort { get; set; }

    public string? Strasse { get; set; }

    /// <summary>Pfad/URL zum Grundriss-SVG fuer die Kartenansicht (Phase 2).</summary>
    public string? GrundrissUrl { get; set; }

    public ICollection<Preis> Preise { get; set; } = [];
    public ICollection<Standort> Standorte { get; set; } = [];
}
