namespace ArtikelFinder.Shared.Dtos;

/// <summary>Seitenweises Ergebnis. Der Katalog wird nach dem Open-Food-Facts-Import
/// sechsstellig, deshalb ist Paging von Anfang an Pflicht.</summary>
public sealed record SeitenErgebnis<T>
{
    public required IReadOnlyList<T> Eintraege { get; init; }
    public required int Seite { get; init; }
    public required int Seitengroesse { get; init; }
    public required int GesamtAnzahl { get; init; }

    public int Seitenanzahl => Seitengroesse <= 0 ? 0 : (int)Math.Ceiling(GesamtAnzahl / (double)Seitengroesse);
    public bool HatWeitere => Seite < Seitenanzahl;

    public static SeitenErgebnis<T> Leer(int seite, int seitengroesse) => new()
    {
        Eintraege = [],
        Seite = seite,
        Seitengroesse = seitengroesse,
        GesamtAnzahl = 0,
    };
}
