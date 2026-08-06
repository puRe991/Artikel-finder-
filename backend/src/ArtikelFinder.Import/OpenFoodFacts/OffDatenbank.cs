namespace ArtikelFinder.Import.OpenFoodFacts;

/// <summary>
/// Eine Datenbank der Open-Food-Facts-Familie.
///
/// Open Food Facts deckt nur Lebensmittel ab. Kaufland-Eigenmarken gibt es aber auch als
/// Toilettenpapier, Duschgel und Katzenfutter — die stehen in den Schwesterdatenbanken, die
/// dieselbe API sprechen und nur unter einer anderen Adresse liegen. Ohne sie fehlt der
/// halbe Drogerie- und Tierbedarfsteil des Sortiments.
/// </summary>
/// <param name="Name">Klartextname für Protokoll und Zusammenfassung.</param>
/// <param name="BasisAdresse">Wurzel der API dieser Datenbank.</param>
/// <param name="StandardKategorie">
/// Kategorie für Artikel, deren Tags keine genauere Zuordnung hergeben. Bei den kleinen
/// Schwesterdatenbanken ist die Kategorisierung oft leer; die Oberkategorie ist dann immer
/// noch richtig. Für Lebensmittel gibt es keinen sinnvollen Sammelbegriff — dort bleibt die
/// Kategorie lieber leer als falsch.
/// </param>
public sealed record OffDatenbank(string Name, string BasisAdresse, string? StandardKategorie)
{
    public static readonly OffDatenbank Lebensmittel =
        new("Open Food Facts", "https://world.openfoodfacts.org/", null);

    public static readonly OffDatenbank Drogerie =
        new("Open Beauty Facts", "https://world.openbeautyfacts.org/", "Drogerie");

    public static readonly OffDatenbank Haushalt =
        new("Open Products Facts", "https://world.openproductsfacts.org/", "Haushalt & Sonstiges");

    public static readonly OffDatenbank Tierbedarf =
        new("Open Pet Food Facts", "https://world.openpetfoodfacts.org/", "Tierbedarf");

    public static readonly IReadOnlyList<OffDatenbank> Alle =
        [Lebensmittel, Drogerie, Haushalt, Tierbedarf];

    public override string ToString() => Name;
}
