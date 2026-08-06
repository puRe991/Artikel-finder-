namespace ArtikelFinder.Shared.Enums;

/// <summary>
/// Woher ein Datensatz stammt. Wichtig fuer die Moderation ab Phase 3: importierte
/// Daten sind unverifiziert, Nutzereintraege sind zurechenbar.
/// </summary>
public enum Erstellerquelle
{
    /// <summary>Vom System angelegt (Seed-Daten, Migrationen).</summary>
    System = 0,

    /// <summary>Aus einer externen Quelle importiert, z.B. Open Food Facts.</summary>
    Import = 1,

    /// <summary>Von einem Nutzer in der App erfasst.</summary>
    Nutzer = 2,
}
