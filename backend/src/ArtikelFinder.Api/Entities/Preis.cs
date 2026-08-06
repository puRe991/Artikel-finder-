namespace ArtikelFinder.Api.Entities;

/// <summary>
/// Eine Preiserfassung. Preise werden nie ueberschrieben, sondern als neue Zeile
/// angehaengt — der juengste Eintrag pro (Artikel, Markt) ist der aktuelle. Damit ist
/// die Preishistorie ohne Zusatztabelle vorhanden.
/// </summary>
public class Preis
{
    public Guid Id { get; set; }

    public Guid ArtikelId { get; set; }
    public Artikel Artikel { get; set; } = null!;

    public int MarktId { get; set; }
    public Markt Markt { get; set; } = null!;

    /// <summary>Normalpreis in Euro.</summary>
    public decimal Wert { get; set; }

    /// <summary>Aktionspreis in Euro, falls beworben.</summary>
    public decimal? Werbepreis { get; set; }

    public DateTimeOffset? WerbepreisGueltigVon { get; set; }

    public DateTimeOffset? WerbepreisGueltigBis { get; set; }

    public DateTimeOffset ErfasstAm { get; set; }

    /// <summary>Freitext im MVP, ab Phase 3 die Nutzer-Id.</summary>
    public string? ErfasstVon { get; set; }

    /// <summary>Laeuft zum angegebenen Zeitpunkt eine Werbeaktion?
    /// Offene Grenzen zaehlen als "ab sofort" bzw. "bis auf Weiteres".</summary>
    public bool IstWerbepreisAktiv(DateTimeOffset zeitpunkt) =>
        Werbepreis.HasValue
        && (!WerbepreisGueltigVon.HasValue || WerbepreisGueltigVon.Value <= zeitpunkt)
        && (!WerbepreisGueltigBis.HasValue || WerbepreisGueltigBis.Value >= zeitpunkt);
}
