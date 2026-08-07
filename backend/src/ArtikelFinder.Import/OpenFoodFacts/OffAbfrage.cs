namespace ArtikelFinder.Import.OpenFoodFacts;

/// <summary>
/// Eine Suchanfrage an die Open-Food-Facts-API: welche Datenbank, welcher Filter.
///
/// Die API filtert über gleichnamige Felder (<c>categories_tags</c>, <c>brands_tags</c>),
/// deshalb reicht ein Feldname plus Wert. Die Alternative — je Filterart eine eigene
/// Client-Methode — hätte den Wiederholungs- und Ausfallmechanismus doppelt gebraucht.
/// </summary>
/// <param name="Datenbank">Welche Datenbank der Familie gefragt wird.</param>
/// <param name="Feld">Name des Filterfelds in der API.</param>
/// <param name="Wert">Tag, nach dem gefiltert wird.</param>
/// <param name="Land">Ländertag; leer bedeutet: alle Länder.</param>
public sealed record OffAbfrage(OffDatenbank Datenbank, string Feld, string Wert, string Land)
{
    public static OffAbfrage NachKategorie(string kategorieTag, string land) =>
        new(OffDatenbank.Lebensmittel, "categories_tags", kategorieTag, land);

    public static OffAbfrage NachMarke(OffDatenbank datenbank, string markenTag, string land) =>
        new(datenbank, "brands_tags", markenTag, land);

    public override string ToString() =>
        Datenbank == OffDatenbank.Lebensmittel ? Wert : $"{Wert} @ {Datenbank.Name}";
}
