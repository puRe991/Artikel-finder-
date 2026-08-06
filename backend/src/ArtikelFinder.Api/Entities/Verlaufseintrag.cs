using ArtikelFinder.Shared.Enums;

namespace ArtikelFinder.Api.Entities;

/// <summary>
/// Aenderungsverlauf pro Artikel: wer hat wann welchen Preis/Standort erfasst. Ab Phase 3
/// die Grundlage fuer Beitragsverlauf und Moderation.
/// </summary>
public class Verlaufseintrag
{
    public long Id { get; set; }

    public Guid ArtikelId { get; set; }
    public Artikel Artikel { get; set; } = null!;

    /// <summary>"Artikel", "Preis" oder "Standort".</summary>
    public required string Entitaet { get; set; }

    public Aenderungsart Aenderungsart { get; set; }

    /// <summary>Menschenlesbare Zusammenfassung, z.B. "Preis 1,49 -&gt; 1,29 EUR".</summary>
    public required string Beschreibung { get; set; }

    public string? GeaendertVon { get; set; }

    public DateTimeOffset GeaendertAm { get; set; }
}
